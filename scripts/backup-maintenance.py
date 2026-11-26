"""Daily application-backup maintenance, for the current Windows user only.

Pruning runs before any network access. No password, key, private row or photo is
written to the operational status. This is not an Auth/provider/PITR backup.
"""
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import importlib.util
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import uuid

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("pacefit_backup",
                                            Path(__file__).with_name("cloud-backup.py"))
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)

STATE_DIRECTORY = backup.ROOT / "supabase/.temp/backup-maintenance"
PROJECT = "ymfypllkcrqwfwihqzpf"
INTERVAL = timedelta(hours=24)


class MaintenanceBusy(RuntimeError):
    pass


class MaintenanceFailed(RuntimeError):
    pass


@contextmanager
def exclusive(directory):
    """Hold one Windows byte lock; do not delete/recreate the lock on release."""
    import msvcrt
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / "maintenance.lock").open("a+b") as lock:
        if lock.seek(0, os.SEEK_END) == 0:
            lock.write(b"\0")
            lock.flush()
        lock.seek(0)
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            raise MaintenanceBusy("Another application-backup maintenance is active.") from None
        try:
            yield
        finally:
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)


def write_status(value, directory):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    temporary = directory / ("status-" + uuid.uuid4().hex + ".tmp")
    try:
        with temporary.open("x", encoding="utf-8") as output:
            json.dump(value, output, indent=2, allow_nan=False)
            output.flush()
            os.fsync(output.fileno())
        temporary.replace(directory / "status.json")
    finally:
        temporary.unlink(missing_ok=True)


def archive_metadata(path, value):
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(chunk)
    return {"archive_name": path.name, "archive_sha256": hasher.hexdigest(),
            "backup_created_at": value["created_at"],
            "capture_scope": "all_active_application_accounts"}


def fresh_backup(directory, clock, provenance=None):
    """Only an attested FULL capture can suppress a new one; fixtures cannot."""
    directory = Path(directory)
    provenance = provenance or {}
    name = provenance.get("archive_name", "")
    digest = provenance.get("archive_sha256", "")
    if (provenance.get("project") != PROJECT
            or provenance.get("capture_scope") != "all_active_application_accounts"
            or not isinstance(name, str) or not backup.NAME.fullmatch(name)
            or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest)
            or not directory.exists()):
        return None
    root = directory.resolve()
    path = directory / name
    if path.is_symlink() or not path.is_file() or path.resolve().parent != root:
        return None
    try:
        value = backup.load(path, clock=clock)
        metadata = archive_metadata(path, value)
        age = clock - datetime.fromisoformat(value["created_at"])
        if metadata["archive_sha256"] == digest and -timedelta(minutes=5) <= age < INTERVAL:
            return metadata
    except (OSError, ValueError, TypeError, KeyError, RuntimeError):
        # Corruption, a subset fixture or a replacement cannot suppress a full capture.
        pass
    return None


def maintain(directory=None, state_directory=None, clock=None, cloud_factory=None, scheduled=False):
    directory = Path(directory) if directory is not None else backup.DIRECTORY
    state_directory = Path(state_directory) if state_directory is not None else STATE_DIRECTORY
    clock = clock or datetime.now(timezone.utc)
    result = {
        "project": PROJECT, "run_id": uuid.uuid4().hex,
        "started_at": clock.isoformat(), "completed_at": None,
        "invoked_by_registered_task": bool(scheduled),
        "retention_maximum_age_days": 29, "retention_completed": False,
        "managed_expired_files_removed": 0, "backup_completed": False,
        "backup_created": False, "recent_archive_decrypted_and_validated": False,
        "provider_or_auth_backup": False, "failure_phase": None,
    }
    with exclusive(state_directory):
        try:
            previous = json.loads((state_directory / "status.json").read_text(encoding="utf-8"))
        except (OSError, ValueError):
            previous = {}
        if not isinstance(previous, dict):
            previous = {}
        phase = "retention"
        try:
            # Even expired cloud credentials or an offline network cannot skip local pruning.
            result["managed_expired_files_removed"] = backup.prune(directory, clock=clock)
            result["retention_completed"] = True
            phase = "archive_validation"
            metadata = fresh_backup(directory, clock, previous)
            if metadata is not None:
                result["recent_archive_decrypted_and_validated"] = True
            else:
                phase = "cloud_capture"
                cloud = (cloud_factory or backup.Cloud)()
                value = backup.capture(cloud)
                phase = "encrypted_write"
                path = backup.save(value, directory)
                phase = "encrypted_verification"
                if backup.load(path) != value:
                    raise RuntimeError("Encrypted backup verification failed.")
                metadata = archive_metadata(path, value)
                result["backup_created"] = True
            result.update(metadata)
            result["backup_completed"] = True
        except Exception:
            result["failure_phase"] = phase
            result["completed_at"] = datetime.now(timezone.utc).isoformat()
            write_status(result, state_directory)
            raise MaintenanceFailed("Application-backup maintenance failed; inspect private status.") from None
        result["completed_at"] = datetime.now(timezone.utc).isoformat()
        write_status(result, state_directory)
    return result


def read_status(directory=STATE_DIRECTORY):
    path = Path(directory) / "status.json"
    if not path.exists():
        return {"project": PROJECT, "maintenance_recorded": False}
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("project") != PROJECT:
        raise RuntimeError("Application-backup status project mismatch.")
    # Do not print arbitrary properties from a file, even in diagnostic mode.
    fields = (
        "project", "run_id", "started_at", "completed_at", "invoked_by_registered_task",
        "retention_maximum_age_days", "retention_completed", "managed_expired_files_removed",
        "backup_completed", "backup_created", "recent_archive_decrypted_and_validated",
        "provider_or_auth_backup", "failure_phase", "backup_created_at",
        "archive_name", "archive_sha256", "capture_scope",
    )
    return {key: value[key] for key in fields if key in value}


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("maintain", "status"))
    parser.add_argument("--scheduled", action="store_true")
    args = parser.parse_args()
    try:
        result = read_status() if args.action == "status" else maintain(scheduled=args.scheduled)
        print(json.dumps(result, indent=2))
    except MaintenanceBusy:
        print('{"maintenance_skipped": "another_owned_run_is_active"}')
    except Exception:
        print("Application-backup maintenance failed. No private values were printed.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
