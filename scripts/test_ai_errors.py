"""Offline tests: public diagnostics must not expose a provider's raw error body."""
import io
import importlib.util
import json
import unittest
import urllib.error
from unittest.mock import patch
from acceptance_common import Cloud, AiGatewayError
from pathlib import Path


class AiErrorTest(unittest.TestCase):
    @staticmethod
    def admin():
        spec = importlib.util.spec_from_file_location("guarded_cloud_admin",
            Path(__file__).with_name("cloud-admin.py"))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_legacy_smoke_stops_before_keys_or_process_if_either_ai_secret_exists(self):
        for names in (["GEMINI_API_KEY"], ["GEMINI_MODEL"], ["GEMINI_API_KEY", "GEMINI_MODEL"]):
            module = self.admin()
            with patch.object(module, "management", return_value=[{"name": name} for name in names]), \
                    patch.object(module, "keys") as keys, patch.object(module.subprocess, "run") as run:
                with self.assertRaisesRegex(RuntimeError, "Stopped before any inference"):
                    module.smoke()
                keys.assert_not_called()
                run.assert_not_called()

    def test_legacy_smoke_runs_only_with_unconfigured_ai_and_private_child_environment(self):
        module = self.admin()
        with patch.object(module, "management", return_value=[]), \
                patch.object(module, "keys", return_value=("dummy-public", "dummy-server")), \
                patch.object(module.subprocess, "run") as run:
            module.smoke()
        args, kwargs = run.call_args
        self.assertEqual(args[0][0], "node")
        self.assertNotIn("dummy-server", " ".join(args[0]))
        self.assertEqual(kwargs["env"]["PACEFIT_CLOUD_SERVICE_KEY"], "dummy-server")
        self.assertTrue(kwargs["check"])

    def test_failure_receipt_contains_only_public_identifiers_and_never_approves_acceptance(self):
        spec = importlib.util.spec_from_file_location("gemini_receipt",
            Path(__file__).with_name("check-gemini.py"))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        value = module.failure_receipt(AiGatewayError("GEMINI_QUOTA_EXHAUSTED", 503))
        self.assertFalse(value["acceptance_approved"])
        self.assertEqual(value["gateway_code"], "GEMINI_QUOTA_EXHAUSTED")
        self.assertFalse(value["automatic_retries"])
        self.assertFalse(value["photo_content_paths_identity_or_keys_saved"])
        self.assertIsNone(module.failure_receipt(RuntimeError("private unexpected contents")))

    def test_allowlisted_gateway_code_is_useful_without_provider_message(self):
        error = urllib.error.HTTPError("https://example.test", 503, "private", {},
            io.BytesIO(json.dumps({"code": "GEMINI_QUOTA_EXHAUSTED",
                                  "error": "private credentials or image text"}).encode()))
        cloud = Cloud.__new__(Cloud)
        cloud.admin = type("Admin", (), {"BASE": "https://example.test"})
        cloud.anon, cloud.service = "dummy", "dummy"
        with patch("urllib.request.urlopen", side_effect=error):
            with self.assertRaises(AiGatewayError) as caught:
                cloud.request("/functions/v1/ai-gateway", "POST", {})
        self.assertEqual(caught.exception.code, "GEMINI_QUOTA_EXHAUSTED")
        self.assertEqual(caught.exception.http_status, 503)
        self.assertNotIn("private", str(caught.exception))

    def test_unknown_or_malformed_body_remains_generic(self):
        for payload in [b'{"code":"private-key","error":"private"}', b'["private"]', b'private HTML']:
            cloud = Cloud.__new__(Cloud)
            cloud.admin = type("Admin", (), {"BASE": "https://example.test"})
            cloud.anon, cloud.service = "dummy", "dummy"
            error = urllib.error.HTTPError("https://example.test", 503, "private", {}, io.BytesIO(payload))
            with patch("urllib.request.urlopen", side_effect=error):
                with self.assertRaises(RuntimeError) as caught:
                    cloud.request("/functions/v1/ai-gateway", "POST", {})
            self.assertNotIn("private", str(caught.exception))
            self.assertNotIsInstance(caught.exception, AiGatewayError)


if __name__ == "__main__":
    unittest.main()
