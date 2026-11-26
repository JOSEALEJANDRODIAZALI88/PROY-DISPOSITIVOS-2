import base64
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("backup", Path(__file__).with_name("cloud-backup.py"))
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)


def snapshot():
    owner, entry = str(uuid.uuid4()), str(uuid.uuid4())
    photo = owner + "/" + entry + ".jpg"
    data = b"test-opaque-photo"
    return {"format": 1, "project": "ymfypllkcrqwfwihqzpf",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "profiles": [{"user_id": owner, "display_name": "Fixture",
                      "consent_at": "2026-11-25T00:00:00Z", "deletion_requested_at": None}],
        "entries": [{"id": entry, "owner_id": owner, "kind": "PLOGGING", "label": "MEDIUM",
                     "description": "Fixture", "latitude": 0, "longitude": 0, "photo_path": photo,
                     "resolved": False, "created_at": "2026-11-25T00:00:00Z", "points": 999999}],
        "usage": [], "quota": [],
        "photos": {photo: {"jpeg_base64": base64.b64encode(data).decode(),
                           "sha256": hashlib.sha256(data).hexdigest()}}}


class BackupTest(unittest.TestCase):
    def test_dpapi_round_trip_and_tamper_rejected(self):
        raw = b"private sample, not a real credential"
        encrypted = backup.protect(raw)
        self.assertNotIn(raw, encrypted)
        self.assertEqual(backup.protect(encrypted, decrypt=True), raw)
        damaged = bytearray(encrypted)
        damaged[-1] ^= 1
        with self.assertRaises(RuntimeError):
            backup.protect(bytes(damaged), decrypt=True)

    def test_save_and_load_no_plaintext_file(self):
        value = snapshot()
        with tempfile.TemporaryDirectory(prefix="pacefit-backup-unit-") as folder:
            path = backup.save(value, Path(folder))
            self.assertNotIn(b"Fixture", path.read_bytes())
            self.assertEqual(backup.load(path), value)
            self.assertTrue(backup.NAME.fullmatch(path.name))

    def test_expired_backup_cannot_restore(self):
        value = snapshot()
        with self.assertRaisesRegex(RuntimeError, "expired"):
            backup.validate_snapshot(value, datetime.now(timezone.utc) + timedelta(days=29))

    def test_wrong_project_or_extra_health_field_rejected(self):
        value = snapshot()
        value["project"] = "another-project"
        with self.assertRaises(RuntimeError):
            backup.validate_snapshot(value)
        value = snapshot()
        value["health_profile"] = []
        with self.assertRaises(RuntimeError):
            backup.validate_snapshot(value)

    def test_photo_owner_escape_and_hash_mismatch_rejected(self):
        value = snapshot()
        value["entries"][0]["photo_path"] = "../../photo.jpg"
        with self.assertRaises(RuntimeError):
            backup.validate_snapshot(value)
        value = snapshot()
        next(iter(value["photos"].values()))["sha256"] = "0" * 64
        with self.assertRaises(RuntimeError):
            backup.validate_snapshot(value)

    def test_deletion_pending_accounts_cannot_backup(self):
        value = snapshot()
        value["profiles"][0]["deletion_requested_at"] = datetime.now(timezone.utc).isoformat()
        with self.assertRaises(RuntimeError):
            backup.validate_snapshot(value)

    def test_current_deleted_or_inactive_owner_excluded_with_photos(self):
        value = snapshot()
        plan = backup.restore_plan(value, set())
        self.assertEqual(plan["profiles"], [])
        self.assertEqual(plan["entries"], [])
        self.assertEqual(plan["photos"], {})
        self.assertEqual(plan["excluded_deleted_or_inactive_owners"], 1)

    def test_restore_cannot_trust_client_points(self):
        value = snapshot()
        plan = backup.restore_plan(value, {value["profiles"][0]["user_id"]})
        self.assertNotIn("points", plan["entries"][0])
        self.assertEqual(len(plan["photos"]), 1)

    def test_photo_restore_writes_only_owned_temporary_path_and_verifies_hash(self):
        value = snapshot()
        plan = backup.restore_plan(value, {value["profiles"][0]["user_id"]})
        with tempfile.TemporaryDirectory(prefix="pacefit-photo-restore-unit-") as folder:
            self.assertEqual(backup.rehearse_photos(plan, Path(folder)), 1)
            photo = Path(folder) / next(iter(plan["photos"]))
            self.assertEqual(photo.read_bytes(), b"test-opaque-photo")
            with self.assertRaises(FileExistsError):
                backup.rehearse_photos(plan, Path(folder))

    def test_photo_restore_rejects_escape_without_creating_file(self):
        value = snapshot()
        plan = {"photos": {"../../unrelated.jpg": next(iter(value["photos"].values()))}}
        with tempfile.TemporaryDirectory(prefix="pacefit-photo-restore-unit-") as folder:
            with self.assertRaises(RuntimeError):
                backup.rehearse_photos(plan, Path(folder))
            self.assertEqual(list(Path(folder).iterdir()), [])

    def test_photo_restore_rejects_changed_content(self):
        value = snapshot()
        photo = next(iter(value["photos"].values()))
        photo["sha256"] = "0" * 64
        with tempfile.TemporaryDirectory(prefix="pacefit-photo-restore-unit-") as folder:
            with self.assertRaises(RuntimeError):
                backup.rehearse_photos({"photos": value["photos"]}, Path(folder))
            self.assertEqual(list(Path(folder).rglob("*.jpg")), [])

    def test_prune_only_managed_expired_files_and_no_recursive_delete(self):
        with tempfile.TemporaryDirectory(prefix="pacefit-prune-unit-") as folder:
            directory = Path(folder)
            valid = backup.save(snapshot(), directory)
            unrelated = directory / "my-old-file.txt"
            unrelated.write_text("user work")
            fake = directory / f"pacefit-20000101T000000Z-{uuid.uuid4().hex}.pfbk"
            fake.write_bytes(b"not one of our backups")
            nested = directory / "nested"
            nested.mkdir()
            nested_file = nested / valid.name
            nested_file.write_bytes(valid.read_bytes())
            future = datetime.now(timezone.utc) + timedelta(days=30)
            self.assertEqual(backup.prune(directory, future), 1)
            self.assertFalse(valid.exists())
            self.assertEqual(unrelated.read_text(), "user work")
            self.assertTrue(fake.exists())
            self.assertTrue(nested_file.exists())


if __name__ == "__main__":
    unittest.main()
