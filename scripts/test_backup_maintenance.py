from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("maintenance", Path(__file__).with_name("backup-maintenance.py"))
maintenance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maintenance)
from test_cloud_backup import snapshot


class MaintenanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="pacefit-maintenance-unit-")
        self.root = Path(self.temp.name)
        self.archives = self.root / "archives"
        self.state = self.root / "state"
        self.clock = datetime.now(timezone.utc)

    def tearDown(self):
        self.temp.cleanup()

    def run_maintenance(self, **kwargs):
        return maintenance.maintain(self.archives, self.state, clock=self.clock, **kwargs)

    def test_first_run_creates_and_verifies_actual_dpapi_archive(self):
        value = snapshot()
        with patch.object(maintenance.backup, "capture", return_value=value), \
                patch.object(maintenance.backup, "Cloud", return_value=object()):
            result = self.run_maintenance(scheduled=True)
        self.assertTrue(result["backup_created"])
        self.assertTrue(result["backup_completed"])
        self.assertTrue(result["retention_completed"])
        self.assertTrue(result["invoked_by_registered_task"])
        self.assertFalse(result["provider_or_auth_backup"])
        self.assertEqual(maintenance.read_status(self.state)["run_id"], result["run_id"])
        self.assertEqual(maintenance.backup.load(next(self.archives.glob("*.pfbk"))), value)

    def test_recent_archive_is_decrypted_not_just_trusted_by_name(self):
        value = snapshot()
        path = maintenance.backup.save(value, self.archives)
        provenance = {"project": maintenance.PROJECT, **maintenance.archive_metadata(path, value)}
        maintenance.write_status(provenance, self.state)
        with patch.object(maintenance.backup, "Cloud") as cloud:
            result = self.run_maintenance()
        cloud.assert_not_called()
        self.assertFalse(result["backup_created"])
        self.assertTrue(result["recent_archive_decrypted_and_validated"])

    def test_recent_subset_fixture_cannot_prevent_a_full_capture(self):
        # A rehearsal archive has the right project and TTL, but is not a complete backup.
        maintenance.backup.save(snapshot(), self.archives)
        with patch.object(maintenance.backup, "capture", return_value=snapshot()) as capture, \
                patch.object(maintenance.backup, "Cloud", return_value=object()):
            result = self.run_maintenance()
        capture.assert_called_once()
        self.assertTrue(result["backup_created"])
        self.assertEqual(result["capture_scope"], "all_active_application_accounts")

    def test_corrupt_recent_archive_does_not_suppress_capture(self):
        value = snapshot()
        valid = maintenance.backup.save(value, self.archives)
        provenance = {"project": maintenance.PROJECT, **maintenance.archive_metadata(valid, value)}
        maintenance.write_status(provenance, self.state)
        valid.write_bytes(maintenance.backup.MAGIC + b"corrupt ciphertext")
        with patch.object(maintenance.backup, "capture", return_value=snapshot()) as capture, \
                patch.object(maintenance.backup, "Cloud", return_value=object()):
            result = self.run_maintenance()
        capture.assert_called_once()
        self.assertTrue(result["backup_created"])

    def test_replaced_valid_archive_with_same_name_cannot_reuse_provenance(self):
        value = snapshot()
        path = maintenance.backup.save(value, self.archives)
        provenance = {"project": maintenance.PROJECT, **maintenance.archive_metadata(path, value)}
        maintenance.write_status(provenance, self.state)
        other = maintenance.backup.save(snapshot(), self.archives)
        path.write_bytes(other.read_bytes())
        self.assertIsNone(maintenance.fresh_backup(self.archives, self.clock, provenance))

    def test_malformed_previous_status_does_not_suppress_capture(self):
        self.state.mkdir()
        (self.state / "status.json").write_text("[]")
        with patch.object(maintenance.backup, "capture", return_value=snapshot()) as capture, \
                patch.object(maintenance.backup, "Cloud", return_value=object()):
            result = self.run_maintenance()
        capture.assert_called_once()
        self.assertTrue(result["backup_completed"])

    def test_pruning_still_completes_when_cloud_credentials_or_network_fail(self):
        value = snapshot()
        value["created_at"] = (self.clock - timedelta(days=30)).isoformat()
        # Produce an expired owned fixture by saving with a private test clock.
        with patch.object(maintenance.backup, "now", return_value=self.clock - timedelta(days=30)):
            expired = maintenance.backup.save(value, self.archives)
        unrelated = self.archives / "user-document.txt"
        unrelated.write_text("user work")
        with patch.object(maintenance.backup, "Cloud", side_effect=RuntimeError("private diagnostic")):
            with self.assertRaises(maintenance.MaintenanceFailed):
                self.run_maintenance()
        result = maintenance.read_status(self.state)
        self.assertTrue(result["retention_completed"])
        self.assertEqual(result["managed_expired_files_removed"], 1)
        self.assertFalse(result["backup_completed"])
        self.assertEqual(result["failure_phase"], "cloud_capture")
        self.assertFalse(expired.exists())
        self.assertEqual(unrelated.read_text(), "user work")
        self.assertNotIn("private diagnostic", (self.state / "status.json").read_text())

    def test_older_than_24_hours_valid_archive_requires_fresh_capture(self):
        old = snapshot()
        old["created_at"] = (self.clock - timedelta(hours=25)).isoformat()
        maintenance.backup.save(old, self.archives)
        with patch.object(maintenance.backup, "capture", return_value=snapshot()) as capture, \
                patch.object(maintenance.backup, "Cloud", return_value=object()):
            result = self.run_maintenance()
        capture.assert_called_once()
        self.assertTrue(result["backup_created"])

    def test_lock_prevents_parallel_runs_without_changing_status(self):
        with maintenance.exclusive(self.state):
            with self.assertRaises(maintenance.MaintenanceBusy):
                self.run_maintenance()
        self.assertFalse((self.state / "status.json").exists())
        with maintenance.exclusive(self.state):
            pass  # Release never removes the lock path or prevents the next owner.

    def test_failure_to_prune_never_captures_or_reports_retention_success(self):
        with patch.object(maintenance.backup, "prune", side_effect=OSError("private path")), \
                patch.object(maintenance.backup, "Cloud") as cloud:
            with self.assertRaises(maintenance.MaintenanceFailed):
                self.run_maintenance()
        cloud.assert_not_called()
        result = maintenance.read_status(self.state)
        self.assertFalse(result["retention_completed"])
        self.assertEqual(result["failure_phase"], "retention")

    def test_filenames_without_valid_ciphertext_and_foreign_status_cannot_forge_success(self):
        self.archives.mkdir()
        name = "pacefit-" + self.clock.strftime("%Y%m%dT%H%M%SZ") + "-" + "a" * 32 + ".pfbk"
        (self.archives / name).write_text("not a backup")
        self.assertIsNone(maintenance.fresh_backup(self.archives, self.clock))
        self.state.mkdir()
        (self.state / "status.json").write_text(json.dumps({"project": "unrelated"}))
        with self.assertRaises(RuntimeError):
            maintenance.read_status(self.state)

    def test_status_does_not_print_unexpected_private_fields(self):
        self.state.mkdir()
        (self.state / "status.json").write_text(json.dumps({
            "project": maintenance.PROJECT, "backup_completed": True,
            "private_photo": "do not print", "password": "do not print",
        }))
        result = maintenance.read_status(self.state)
        self.assertNotIn("private_photo", result)
        self.assertNotIn("password", result)


if __name__ == "__main__":
    unittest.main()
