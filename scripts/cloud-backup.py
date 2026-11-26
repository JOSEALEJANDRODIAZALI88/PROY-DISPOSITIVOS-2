"""Encrypted APPLICATION-DATA backups; never Auth passwords, sessions, health or routes.

Windows DPAPI/current-user protection. Restore rehearsal uses an isolated disposable
PostgreSQL container, NEVER the cloud database. This is not a provider/PITR backup.
"""
import argparse
import base64
import ctypes
from ctypes import wintypes
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import uuid

from acceptance_common import Cloud, DisposableAccount, ROOT, publish_fixture

DIRECTORY = ROOT / "supabase/.temp/application-backups"
MAGIC = b"PACEFIT-APP-BACKUP-1\n"
MAX_AGE = timedelta(days=29)  # Daily pruning leaves margin before a 30-day deadline.
MAX_BYTES = 64 * 1024 * 1024
NAME = re.compile(r"pacefit-(\d{8}T\d{6}Z)-[0-9a-f]{32}\.pfbk")
ENTRY_FIELDS = ("id", "owner_id", "kind", "label", "description", "latitude", "longitude",
                "photo_path", "resolved", "created_at")


def now():
    return datetime.now(timezone.utc)


def protect(value, decrypt=False):
    """DPAPI authenticated encryption, user-scoped; no key in files or arguments."""
    class Blob(ctypes.Structure):
        _fields_ = [("size", wintypes.DWORD), ("data", ctypes.POINTER(ctypes.c_ubyte))]
    buffer = (ctypes.c_ubyte * len(value)).from_buffer_copy(value)
    source = Blob(len(value), buffer)
    output = Blob()
    api = ctypes.WinDLL("crypt32", use_last_error=True)
    function = api.CryptUnprotectData if decrypt else api.CryptProtectData
    function.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                         ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    function.restype = wintypes.BOOL
    if not function(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(output)):
        raise RuntimeError("Private backup could not be encrypted/decrypted for this Windows user.")
    local_free = ctypes.WinDLL("kernel32").LocalFree
    local_free.argtypes = [ctypes.c_void_p]
    local_free.restype = ctypes.c_void_p
    try:
        return ctypes.string_at(output.data, output.size)
    finally:
        local_free(output.data)


def validate_snapshot(value, clock=None):
    if not isinstance(value, dict) or value.get("format") != 1 or value.get("project") != "ymfypllkcrqwfwihqzpf":
        raise RuntimeError("Backup format/project mismatch.")
    created = datetime.fromisoformat(value["created_at"])
    if created.tzinfo is None or created > (clock or now()) + timedelta(minutes=5):
        raise RuntimeError("Invalid backup timestamp.")
    if (clock or now()) - created >= MAX_AGE:
        raise RuntimeError("Backup expired; restoration is forbidden.")
    expected = {"format", "project", "created_at", "profiles", "entries", "usage", "quota", "photos"}
    if set(value) != expected:
        raise RuntimeError("Unexpected backup fields; no data restored.")
    for table in ("profiles", "entries", "usage", "quota"):
        if not isinstance(value[table], list):
            raise RuntimeError("Invalid backup rows.")
    owners = {str(uuid.UUID(row["user_id"])) for row in value["profiles"]}
    if len(owners) != len(value["profiles"]):
        raise RuntimeError("Duplicate backup owner.")
    if any(row.get("deletion_requested_at") is not None for row in value["profiles"]):
        raise RuntimeError("Deletion-pending accounts cannot be backed up.")
    photo_names = set()
    for entry in value["entries"]:
        owner, entry_id = str(uuid.UUID(entry["owner_id"])), str(uuid.UUID(entry["id"]))
        path = f"{owner}/{entry_id}.jpg"
        if owner not in owners or entry["photo_path"] != path:
            raise RuntimeError("Invalid photo ownership in backup.")
        if entry["kind"] not in ("PLOGGING", "REPORT"):
            raise RuntimeError("Invalid entry kind.")
        allowed = ("LITTLE", "MEDIUM", "MUCH") if entry["kind"] == "PLOGGING" else ("POTHOLE", "WASTE", "LIGHT", "OTHER")
        if entry["label"] not in allowed:
            raise RuntimeError("Invalid entry label.")
        photo_names.add(path)
    if not isinstance(value["photos"], dict) or set(value["photos"]) != photo_names:
        raise RuntimeError("Missing or unexpected backup photographs.")
    for photo in value["photos"].values():
        data = base64.b64decode(photo["jpeg_base64"], validate=True)
        if len(data) > 2097152 or hashlib.sha256(data).hexdigest() != photo["sha256"]:
            raise RuntimeError("Backup photo integrity check failed.")
    if any(row["user_id"] not in owners for table in ("usage", "quota") for row in value[table]):
        raise RuntimeError("Unexpected audit owner.")
    return value


def capture(cloud, owners=None):
    condition = ""
    if owners is not None:
        ids = [str(uuid.UUID(value)) for value in owners]
        if not ids:
            raise RuntimeError("No fixture owners supplied.")
        condition = " AND p.user_id IN (" + ",".join("'" + value + "'::uuid" for value in ids) + ")"
    rows = cloud.query("""
        WITH active AS (
          SELECT p.* FROM public.social_profiles p JOIN auth.users u ON u.id=p.user_id
          WHERE p.deletion_requested_at IS NULL """ + condition + """
        )
        SELECT jsonb_build_object(
          'profiles',(SELECT coalesce(jsonb_agg(to_jsonb(p)),'[]'::jsonb) FROM active p),
          'entries',(SELECT coalesce(jsonb_agg(to_jsonb(e)),'[]'::jsonb) FROM public.city_entries e
                     WHERE e.owner_id IN (SELECT user_id FROM active)),
          'usage',(SELECT coalesce(jsonb_agg(to_jsonb(a)),'[]'::jsonb) FROM public.ai_usage a
                   WHERE a.user_id IN (SELECT user_id FROM active) AND a.created_at>now()-interval '29 days'),
          'quota',(SELECT coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) FROM private.ai_quota q
                   WHERE q.user_id IN (SELECT user_id FROM active) AND q.hour>now()-interval '29 days')
        ) AS snapshot;
    """)
    result = rows[0]["snapshot"]
    if isinstance(result, str):
        result = json.loads(result)
    if len(result["entries"]) > 1000:
        raise RuntimeError("Backup exceeds the safety limit; use a reviewed large-dataset procedure.")
    result.update(format=1, project=cloud.admin.REF, created_at=now().isoformat(), photos={})
    total = 0
    for entry in result["entries"]:
        expected = str(uuid.UUID(entry["owner_id"])) + "/" + str(uuid.UUID(entry["id"])) + ".jpg"
        if entry["photo_path"] != expected:
            raise RuntimeError("Photo path does not match its owner.")
        data = cloud.request("/storage/v1/object/authenticated/city-photos/" + expected,
                             server=True, binary=True)
        total += len(data)
        if total > MAX_BYTES or len(data) > 2097152:
            raise RuntimeError("Private backup exceeds its memory/size limit.")
        result["photos"][expected] = {"sha256": hashlib.sha256(data).hexdigest(),
                                     "jpeg_base64": base64.b64encode(data).decode("ascii")}
    validate_snapshot(result)
    return result


def save(snapshot, directory=DIRECTORY):
    validate_snapshot(snapshot)
    raw = json.dumps(snapshot, separators=(",", ":"), allow_nan=False).encode()
    if len(raw) > MAX_BYTES * 2:
        raise RuntimeError("Private backup exceeds its size limit.")
    encrypted = protect(raw)
    directory.mkdir(parents=True, exist_ok=True)
    stamp = datetime.fromisoformat(snapshot["created_at"]).strftime("%Y%m%dT%H%M%SZ")
    path = directory / f"pacefit-{stamp}-{uuid.uuid4().hex}.pfbk"
    with path.open("xb") as output:
        output.write(MAGIC + encrypted)
        output.flush()
        import os
        os.fsync(output.fileno())
    return path


def load(path, clock=None):
    path = Path(path)
    if path.stat().st_size > MAX_BYTES * 2:
        raise RuntimeError("Backup exceeds its size limit.")
    data = path.read_bytes()
    if not data.startswith(MAGIC):
        raise RuntimeError("Not a PaceFit encrypted backup.")
    return validate_snapshot(json.loads(protect(data[len(MAGIC):], decrypt=True)), clock)


def prune(directory=DIRECTORY, clock=None):
    """Only explicitly managed files, never recursive and never followed symlinks."""
    removed = 0
    if not directory.exists():
        return removed
    root = directory.resolve()
    for path in directory.iterdir():
        match = NAME.fullmatch(path.name)
        if not match or path.is_symlink() or not path.is_file() or path.resolve().parent != root:
            continue
        created = datetime.strptime(match[1], "%Y%m%dT%H%M%SZ").replace(tzinfo=timezone.utc)
        if (clock or now()) - created >= MAX_AGE:
            with path.open("rb") as source:
                if source.read(len(MAGIC)) != MAGIC:
                    continue
            path.unlink()
            removed += 1
    return removed


def restore_plan(snapshot, live_owners):
    validate_snapshot(snapshot)
    active = set(live_owners)
    kept = {row["user_id"] for row in snapshot["profiles"]} & active
    return {
        "profiles": [row for row in snapshot["profiles"] if row["user_id"] in kept],
        "entries": [{field: row[field] for field in ENTRY_FIELDS}
                    for row in snapshot["entries"] if row["owner_id"] in kept],
        "usage": [row for row in snapshot["usage"] if row["user_id"] in kept],
        "quota": [row for row in snapshot["quota"] if row["user_id"] in kept],
        "photos": {row["photo_path"]: snapshot["photos"][row["photo_path"]]
                   for row in snapshot["entries"] if row["owner_id"] in kept},
        "excluded_deleted_or_inactive_owners": len(snapshot["profiles"]) - len(kept),
    }


def live_owners(cloud, snapshot):
    result = set()
    for profile in snapshot["profiles"]:
        owner = str(uuid.UUID(profile["user_id"]))
        rows = cloud.query("SELECT p.user_id FROM public.social_profiles p JOIN auth.users u ON u.id=p.user_id "
            f"WHERE p.user_id='{owner}'::uuid AND p.deletion_requested_at IS NULL")
        if rows:
            result.add(owner)
    return result


def rehearse_postgres(plan):
    """Disposable network-isolated PostgreSQL; no host port, cloud writes or volume restore."""
    payload = json.dumps(plan, separators=(",", ":"), allow_nan=False)
    tag = "$pacefit_" + uuid.uuid4().hex + "$"
    if tag in payload:
        raise RuntimeError("Unexpected SQL delimiter.")
    sql = """
      CREATE TABLE profiles(user_id uuid PRIMARY KEY, display_name text, consent_at timestamptz);
      CREATE TABLE entries(id uuid PRIMARY KEY, owner_id uuid REFERENCES profiles(user_id),
        kind text, label text, description text, latitude double precision, longitude double precision,
        photo_path text UNIQUE, resolved boolean, created_at timestamptz,
        points integer GENERATED ALWAYS AS (CASE WHEN kind='PLOGGING' THEN
          CASE label WHEN 'LITTLE' THEN 5 WHEN 'MEDIUM' THEN 10 WHEN 'MUCH' THEN 20 ELSE 0 END ELSE 0 END) STORED);
      CREATE TABLE usage(user_id uuid REFERENCES profiles(user_id), task text, created_at timestamptz);
      CREATE TABLE quota(user_id uuid REFERENCES profiles(user_id), hour timestamptz, used integer CHECK(used BETWEEN 1 AND 40));
      CREATE TEMP TABLE source(value jsonb);
      INSERT INTO source VALUES(""" + tag + payload + tag + """::jsonb);
      INSERT INTO profiles SELECT p.user_id,p.display_name,p.consent_at FROM source,
        jsonb_to_recordset(value->'profiles') p(user_id uuid,display_name text,consent_at timestamptz);
      INSERT INTO entries(id,owner_id,kind,label,description,latitude,longitude,photo_path,resolved,created_at)
        SELECT e.* FROM source, jsonb_to_recordset(value->'entries') e(id uuid,owner_id uuid,
          kind text,label text,description text,latitude double precision,longitude double precision,
          photo_path text,resolved boolean,created_at timestamptz);
      INSERT INTO usage SELECT a.user_id,a.task,a.created_at FROM source,
        jsonb_to_recordset(value->'usage') a(user_id uuid,task text,created_at timestamptz);
      INSERT INTO quota SELECT q.user_id,q.hour,q.used FROM source,
        jsonb_to_recordset(value->'quota') q(user_id uuid,hour timestamptz,used integer);
      SELECT json_build_object('profiles',(SELECT count(*) FROM profiles),
        'entries',(SELECT count(*) FROM entries),'points',(SELECT coalesce(sum(points),0) FROM entries),
        'usage',(SELECT count(*) FROM usage),'quota',(SELECT count(*) FROM quota));
    """
    name = "pacefit-restore-" + uuid.uuid4().hex
    command = ["docker", "run", "--rm", "-i", "--name", name, "--network", "none",
        "--memory", "384m", "--cpus", "1", "--read-only", "--user", "postgres",
        "--tmpfs", "/tmp:rw,nosuid,size=160m,mode=1777",
        "--tmpfs", "/var/run/postgresql:rw,nosuid,size=8m,mode=1777",
        "--entrypoint", "sh", "postgres:17-alpine", "-eu", "-c",
        "initdb -D /tmp/pgdata -A trust --no-locale --encoding=UTF8 >/dev/null\n"
        "pg_ctl -D /tmp/pgdata -l /tmp/postgres.log -w start >/dev/null\n"
        "exec psql -U postgres -X -q -v ON_ERROR_STOP=1 -d postgres -At"]
    try:
        result = subprocess.run(command, input=sql, text=True, encoding="utf-8",
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=240)
        if result.returncode:
            raise RuntimeError("Isolated PostgreSQL restore failed; no cloud restore was attempted.")
        return json.loads(result.stdout.strip().splitlines()[-1])
    finally:
        # No other container, image or volume is removed.
        subprocess.run(["docker", "rm", "-f", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def rehearse_photos(plan, directory):
    """Restore only validated relative UUID paths into an owned disposable directory."""
    count = 0
    for name, value in plan["photos"].items():
        if not re.fullmatch(r"[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg", name):
            raise RuntimeError("Invalid isolated photo path.")
        owner, filename = name.split("/")
        if str(uuid.UUID(owner)) != owner or str(uuid.UUID(filename[:-4])) != filename[:-4]:
            raise RuntimeError("Invalid isolated photo identifier.")
        destination = Path(directory) / owner / filename
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.parent.resolve().parent != Path(directory).resolve():
            raise RuntimeError("Isolated photo escaped its directory.")
        data = base64.b64decode(value["jpeg_base64"], validate=True)
        if len(data) > 2097152 or hashlib.sha256(data).hexdigest() != value["sha256"]:
            raise RuntimeError("Isolated photo integrity failed.")
        with destination.open("xb") as output:
            output.write(data)
        if hashlib.sha256(destination.read_bytes()).hexdigest() != value["sha256"]:
            raise RuntimeError("Restored photo integrity failed.")
        count += 1
    return count


def rehearsal(cloud):
    import secrets
    engine = subprocess.run(["docker", "version", "--format", "{{.Server.Version}}"],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=40)
    if engine.returncode or not engine.stdout.strip():
        raise RuntimeError("Docker must be ready before a restore rehearsal.")
    fixture = base64.b64decode((ROOT / "supabase/tests/photo.base64").read_text().strip())
    with DisposableAccount(cloud) as first, DisposableAccount(cloud) as second:
        publish_fixture(first, fixture, "MUCH")
        publish_fixture(second, fixture, "MEDIUM")
        # Exercise nonempty audit/quota restoration without invoking Gemini.
        reserved = cloud.request("/rest/v1/rpc/reserve_ai_call", "POST",
            {"p_user": second.user_id, "p_task": "photo"}, server=True)
        if reserved is not True:
            raise RuntimeError("Fixture quota reservation failed.")
        snapshot = capture(cloud, [first.user_id, second.user_id])
        path = save(snapshot)
        restored = load(path)
        if restored != snapshot or b'"profiles"' in path.read_bytes():
            raise RuntimeError("Encrypted backup did not round trip.")
        cloud.request("/functions/v1/account-delete", "POST",
            {"confirmation": "ELIMINAR", "requestId": secrets.token_hex(32)}, token=first.token)
        if cloud.request(f"/auth/v1/admin/users/{first.user_id}", server=True, missing_ok=True) is not None:
            raise RuntimeError("Fixture deletion was not confirmed.")
        plan = restore_plan(restored, live_owners(cloud, restored))
        counts = rehearse_postgres(plan)
        if (plan["excluded_deleted_or_inactive_owners"] != 1 or len(plan["photos"]) != 1
                or counts != {"profiles": 1, "entries": 1, "points": 10, "usage": 1, "quota": 1}):
            raise RuntimeError("Restore exclusion/integrity check failed.")
        # Expiry and scoped purge are measured against a future clock, only in an owned test folder.
        import tempfile
        with tempfile.TemporaryDirectory(prefix="pacefit-retention-") as temp:
            directory = Path(temp)
            if rehearse_photos(plan, directory / "restored-photos") != 1:
                raise RuntimeError("Isolated photograph restore failed.")
            copy = directory / path.name
            copy.write_bytes(path.read_bytes())
            future = now() + timedelta(days=30)
            try:
                load(copy, clock=future)
            except RuntimeError as error:
                if "Backup expired" not in str(error):
                    raise
            else:
                raise RuntimeError("An expired backup was accepted.")
            if prune(directory, clock=future) != 1 or copy.exists():
                raise RuntimeError("Scoped backup retention failed.")
        result = {"project": cloud.admin.REF, "encrypted_round_trip": True,
            "real_cloud_fixture_deletion_verified": True, "deleted_owner_excluded_from_restore": True,
            "remaining_owner_restored_to_isolated_postgres": True, "photos_hash_verified": True,
            "generated_points": counts["points"], "expired_restore_denied": True,
            "scoped_retention_prune": True, "provider_backup_or_auth_restore_tested": False,
            "maximum_local_archive_age_days": 29, "recorded_at": now().isoformat()}
    # Never emit success evidence before both contexts have verified Auth cleanup.
    path.unlink()  # Only the encrypted archive created by this fixture rehearsal.
    result.update(disposable_accounts_cleanup_verified=True, fixture_archive_removed=True,
                  nonempty_usage_and_quota_restored=True, private_photo_restored_and_hash_verified=True)
    folder = ROOT / "app/build/verification/application-backup"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / ("result-" + uuid.uuid4().hex + ".json")).write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
    print("Both disposable accounts cleaned. No cloud restoration or real-user deletion was performed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("create", "prune", "rehearse"))
    args = parser.parse_args()
    removed = prune()
    if args.action == "prune":
        print(json.dumps({"managed_expired_files_removed": removed}))
        return
    cloud = Cloud()
    if args.action == "create":
        path = save(capture(cloud))
        print("Encrypted application-data backup created in supabase/.temp/application-backups/.")
        print("Current Windows user required; no Auth/password/session/disaster-recovery backup.")
    else:
        rehearsal(cloud)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Application backup operation failed; no credentials or private rows were printed.", file=sys.stderr)
        sys.exit(1)
