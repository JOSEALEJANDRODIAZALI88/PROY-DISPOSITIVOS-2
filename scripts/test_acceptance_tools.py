import base64
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch
import uuid

sys.dont_write_bytecode = True
import acceptance_common as common

spec = importlib.util.spec_from_file_location("gemini", Path(__file__).with_name("check-gemini.py"))
gemini = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gemini)


class FixtureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="pacefit-fixture-unit-")
        self.addCleanup(self.temp.cleanup)
        patched = patch.object(common, "JOURNALS", Path(self.temp.name))
        patched.start()
        self.addCleanup(patched.stop)
        self.cloud = MagicMock()
        self.cloud.admin.REF = "ymfypllkcrqwfwihqzpf"

    def test_ambiguous_creation_cleanup_queries_only_durable_exact_run(self):
        fixture = common.DisposableAccount(self.cloud)
        fixture.save_journal()
        self.cloud.query.return_value = []
        fixture.close()
        sql = self.cloud.query.call_args.args[0]
        self.assertIn("pacefit-acceptance-" + fixture.run + "@example.test", sql)
        self.assertIn("pacefit_acceptance_run", sql)
        self.cloud.request.assert_not_called()
        self.assertFalse(fixture.journal.exists())

    def test_foreign_metadata_prevents_any_delete(self):
        fixture = common.DisposableAccount(self.cloud)
        fixture.user_id = str(uuid.uuid4())
        fixture.save_journal()
        self.cloud.request.return_value = {"app_metadata": {"pacefit_acceptance_run": "another-run"}}
        with self.assertRaisesRegex(RuntimeError, "ownership"):
            fixture.close()
        self.assertEqual(self.cloud.request.call_count, 1)
        self.assertTrue(fixture.journal.exists())

    def test_only_owned_account_and_objects_deleted_then_journal_removed(self):
        fixture = common.DisposableAccount(self.cloud)
        fixture.user_id = str(uuid.uuid4())
        fixture.save_journal()
        self.cloud.request.side_effect = [
            {"app_metadata": {"pacefit_acceptance_run": fixture.run}},
            [{"name": "own.jpg"}], None, [], None, None]
        fixture.close()
        calls = self.cloud.request.call_args_list
        self.assertEqual(calls[2].args[2]["prefixes"], [fixture.user_id + "/own.jpg"])
        self.assertEqual(calls[4].args, ("/auth/v1/admin/users/" + fixture.user_id, "DELETE"))
        self.assertFalse(fixture.journal.exists())

    def test_recovery_rejects_journal_for_other_project(self):
        run = uuid.uuid4().hex
        (Path(self.temp.name) / (run + ".json")).write_text(json.dumps({
            "project": "other", "run": run, "email": f"pacefit-acceptance-{run}@example.test"}))
        with self.assertRaises(RuntimeError):
            common.recover_fixtures(self.cloud)
        self.cloud.request.assert_not_called()

    def test_journal_never_contains_password_or_token(self):
        fixture = common.DisposableAccount(self.cloud)
        fixture.token = "not-a-real-sensitive-session"
        fixture.save_journal()
        text = fixture.journal.read_text()
        self.assertNotIn(fixture.token, text)
        self.assertNotIn("password", text)
        self.assertEqual(set(json.loads(text)), {"project", "run", "user_id", "email"})


class GeminiTest(unittest.TestCase):
    def test_unsafe_wrong_label_or_missing_flag_not_accepted(self):
        for value in ({"label": "WRONG", "unsafe": False}, {"label": "LITTLE"},
                      {"label": "LITTLE", "unsafe": "false"}):
            with self.assertRaises(RuntimeError):
                gemini.validate_suggestion(value, "PLOGGING")
        results = [{"label": "LITTLE", "unsafe": True}] * 5
        with self.assertRaises(RuntimeError):
            gemini.receipt(["PLOGGING"] * 5, results, ["LITTLE"] * 5)

    def test_receipt_is_redacted_and_does_not_claim_android(self):
        values = [{"label": "LITTLE", "unsafe": False, "reason": "private model explanation"}] * 5
        proof = gemini.receipt(["PLOGGING"] * 5, values, ["MEDIUM"] * 5)
        self.assertEqual(proof["manual_corrections"], 5)
        self.assertFalse(proof["android_ui_acceptance_tested"])
        self.assertFalse(proof["photos_published"])
        self.assertNotIn("private model explanation", json.dumps(proof))
        self.assertNotIn("jpegBase64", json.dumps(proof))

    def test_missing_server_configuration_never_prepares_or_sends_photos(self):
        cloud = MagicMock()
        cloud.admin.management.return_value = []
        with patch.object(gemini, "prepare_photo") as prepare:
            with self.assertRaisesRegex(RuntimeError, "Falta conectar"):
                gemini.classify_five(["private"] * 5, ["PLOGGING"] * 5, cloud)
            prepare.assert_not_called()
        cloud.request.assert_not_called()

    def test_payload_contains_only_kind_and_sanitized_jpeg(self):
        cloud = MagicMock()
        cloud.admin.management.return_value = [{"name": "GEMINI_API_KEY"}, {"name": "GEMINI_MODEL"}]
        cloud.request.return_value = {"ok": True, "data": {"label": "LITTLE", "unsafe": False}}
        account = MagicMock()
        account.token = "fixture"
        with patch.object(gemini, "prepare_photo", return_value=b"\xff\xd8fake") as prepare, \
             patch.object(gemini, "DisposableAccount") as fixture:
            fixture.return_value.__enter__.return_value = account
            values = gemini.classify_five(["private"] * 5, ["PLOGGING"] * 5, cloud)
        self.assertEqual(len(values), 5)
        self.assertEqual(prepare.call_count, 5)
        self.assertEqual(cloud.request.call_count, 5)
        body = cloud.request.call_args.args[2]
        self.assertEqual(set(body), {"task", "payload"})
        self.assertEqual(set(body["payload"]), {"kind", "jpegBase64"})
        fixture.return_value.__exit__.assert_called_once()

    def test_conversion_failure_makes_no_inference_and_no_account(self):
        cloud = MagicMock()
        cloud.admin.management.return_value = [{"name": "GEMINI_API_KEY"}, {"name": "GEMINI_MODEL"}]
        with patch.object(gemini, "prepare_photo", side_effect=RuntimeError("invalid")), \
             patch.object(gemini, "DisposableAccount") as fixture:
            with self.assertRaises(RuntimeError):
                gemini.classify_five(["private"] * 5, ["PLOGGING"] * 5, cloud)
            fixture.assert_not_called()
        cloud.request.assert_not_called()

    def test_windows_photo_conversion_freshly_reencodes_metadata(self):
        original = base64.b64decode((common.ROOT / "supabase/tests/photo.base64").read_text().strip())
        marker = b"Exif\x00\x00GPSLatitude=private-test"
        length = len(marker) + 2
        source = original[:2] + b"\xff\xe1" + length.to_bytes(2, "big") + marker + original[2:]
        with tempfile.TemporaryDirectory(prefix="pacefit-photo-unit-") as folder:
            path = Path(folder) / "test.jpg"
            path.write_bytes(source)
            encoded = gemini.prepare_photo(path)
            self.assertTrue(encoded.startswith(b"\xff\xd8"))
            self.assertNotIn(marker, encoded)
            self.assertNotIn(b"Exif", encoded)
            self.assertEqual(path.read_bytes(), source)


if __name__ == "__main__":
    unittest.main()
