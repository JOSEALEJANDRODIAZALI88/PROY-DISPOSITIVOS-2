"""Read-only review of a PRIVATE SQLite copy; never emits health values or GPS coordinates."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sqlite3
import sys
import uuid


def review(connection, session_id):
    session = connection.execute(
        "SELECT state, durationMillis, endedAt FROM activity_sessions WHERE id=?", (session_id,)).fetchone()
    if session is None:
        raise RuntimeError("Session not found in this private copy.")
    # Do not select latitude/longitude or any health-profile columns.
    times = [row[0] for row in connection.execute(
        "SELECT timeMillis FROM route_points WHERE sessionId=? ORDER BY id", (session_id,))]
    deltas = [b - a for a, b in zip(times, times[1:])]
    ordered = all(value > 0 for value in deltas)
    span = times[-1] - times[0] if len(times) >= 2 and ordered else 0
    max_gap = max(deltas, default=0) if ordered else None
    result = {
        "point_count": len(times), "recorded_duration_ms": session[1],
        "gps_span_ms": span, "timestamps_strictly_increasing": ordered,
        "maximum_gap_ms": max_gap, "gaps_over_30_seconds": sum(value > 30000 for value in deltas),
        "finished": session[0] == "FINISHED" and session[2] is not None,
        "continuous_20min_gps_criterion": (
            len(times) >= 2 and ordered and span >= 1200000 and max_gap <= 30000
            and session[1] >= 1200000 and session[0] == "FINISHED" and session[2] is not None),
        "physical_device_proven": False, "screen_off_proven": False,
        "classification_and_distance_accuracy_proven": False,
        "notes": "Pauses count as gaps. This measurement alone does not prove a real phone, screen-off operation or field accuracy.",
    }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path, help="Consistent private SQLite copy including committed WAL data")
    parser.add_argument("--session", required=True, help="Session UUID visible in the private SQLite viewer")
    args = parser.parse_args()
    session_id = str(uuid.UUID(args.session))
    path = args.database.resolve(strict=True)
    with sqlite3.connect(path.as_uri() + "?mode=ro") as connection:
        result = review(connection, session_id)
    result["recorded_at"] = datetime.now(timezone.utc).isoformat()
    folder = Path(__file__).resolve().parent.parent / "app/build/verification/field"
    folder.mkdir(parents=True, exist_ok=True)
    destination = folder / f"review-{uuid.uuid4().hex}.json"
    destination.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    print("Redacted report saved under app/build/verification/field/. Source was not changed.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Private SQLite review failed. Check the copy/schema/session; no source data was printed.", file=sys.stderr)
        sys.exit(1)
