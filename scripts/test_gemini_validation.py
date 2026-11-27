"""Offline diagnostics tests. Fake keys/errors/network only; no Google calls."""
import io
import json
import ssl
import subprocess
import sys
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import MagicMock

import gemini_validation as validation

FAKE_KEY = "unit-test-private-value-not-a-real-key"


class GeminiValidationTest(unittest.TestCase):
    def response(self, payload):
        response = MagicMock()
        response.__enter__.return_value = response
        response.read.return_value = json.dumps(payload).encode()
        return MagicMock(return_value=response)

    def error(self, payload, status=403):
        return MagicMock(side_effect=urllib.error.HTTPError(
            validation.ENDPOINT, status, "private provider description", {},
            io.BytesIO(json.dumps(payload).encode())))

    def test_header_only_verified_tls_and_numeric_stable_model_order(self):
        opener = self.response({"models": [
            {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
            {"name": "models/gemini-10-flash", "supportedGenerationMethods": ["generateContent"]},
            {"name": "models/gemini-2.5-flash-preview", "supportedGenerationMethods": ["generateContent"]},
            {"name": "models/gemini-3-flash", "supportedGenerationMethods": ["embedContent"]},
        ]})
        result = validation.inspect_models(FAKE_KEY, opener)
        self.assertTrue(result["ok"])
        self.assertEqual(["gemini-10-flash", "gemini-2.5-flash"], result["models"])
        request = opener.call_args.args[0]
        self.assertNotIn(FAKE_KEY, request.full_url)
        self.assertEqual(FAKE_KEY, request.get_header("X-goog-api-key"))
        context = opener.call_args.kwargs["context"]
        self.assertTrue(context.check_hostname)
        self.assertEqual(ssl.CERT_REQUIRED, context.verify_mode)
        self.assertNotIn(FAKE_KEY, json.dumps(result))

    def test_invalid_format_never_contacts_google(self):
        opener = MagicMock()
        self.assertEqual("INVALID_KEY_FORMAT", validation.inspect_models("not a key", opener)["code"])
        opener.assert_not_called()

    def test_new_opaque_formats_do_not_require_legacy_prefix(self):
        opener = self.response({"models": [
            {"name": "models/gemini-3-flash", "supportedGenerationMethods": ["generateContent"]}]})
        self.assertTrue(validation.inspect_models(FAKE_KEY, opener)["ok"])

    def test_only_whitelisted_error_identifiers_escape_not_message_or_metadata(self):
        payload = {"error": {"status": "PERMISSION_DENIED", "message": FAKE_KEY,
                             "details": [{"reason": "SERVICE_DISABLED",
                                          "metadata": {"credential": FAKE_KEY}}]}}
        result = validation.inspect_models(FAKE_KEY, self.error(payload))
        self.assertEqual({"ok": False, "code": "SERVICE_DISABLED", "httpStatus": 403,
                          "googleStatus": "PERMISSION_DENIED"}, result)
        self.assertNotIn(FAKE_KEY, json.dumps(result))

    def test_unknown_reasons_statuses_and_provider_text_are_discarded(self):
        payload = {"error": {"status": FAKE_KEY, "message": FAKE_KEY,
                             "details": [{"reason": FAKE_KEY}]}}
        self.assertEqual({"ok": False, "code": "HTTP_ERROR", "httpStatus": 403},
                         validation.inspect_models(FAKE_KEY, self.error(payload)))

    def test_leak_warning_is_classified_without_echo(self):
        payload = {"error": {"message": "Key " + FAKE_KEY + " was reported as leaked",
                             "status": "PERMISSION_DENIED"}}
        result = validation.inspect_models(FAKE_KEY, self.error(payload))
        self.assertEqual("KEY_REPORTED_LEAKED", result["code"])
        self.assertNotIn(FAKE_KEY, json.dumps(result))

    def test_invalid_key_diagnostic_keeps_actual_http_status(self):
        payload = {"error": {"details": [{"reason": "API_KEY_INVALID"}]}}
        result = validation.inspect_models(FAKE_KEY, self.error(payload, 400))
        self.assertEqual("API_KEY_INVALID", result["code"])
        self.assertEqual(400, result["httpStatus"])

    def test_transport_error_is_not_reported_as_google_rejecting_key(self):
        opener = MagicMock(side_effect=urllib.error.URLError(FAKE_KEY))
        result = validation.inspect_models(FAKE_KEY, opener)
        self.assertEqual({"ok": False, "code": "NETWORK_TLS_OR_TIMEOUT"}, result)

    def test_local_failure_is_not_reported_as_google_rejecting_key(self):
        opener = MagicMock(side_effect=RuntimeError(FAKE_KEY))
        self.assertEqual({"ok": False, "code": "LOCAL_VALIDATION_ERROR"},
                         validation.inspect_models(FAKE_KEY, opener))

    def test_incompatible_models_are_separate_from_auth_failure(self):
        result = validation.inspect_models(FAKE_KEY, self.response({"models": []}))
        self.assertEqual({"ok": False, "code": "NO_COMPATIBLE_FLASH_MODEL", "httpStatus": 200}, result)

    def test_malformed_response_never_appears_in_output(self):
        self.assertEqual("INVALID_PROVIDER_RESPONSE", validation.inspect_models(
            FAKE_KEY, self.response({"models": FAKE_KEY}))["code"])
        self.assertEqual("HTTP_ERROR", validation.provider_failure(403, b"not-json-private-text")["code"])

    def test_private_stdin_cli_rejects_extra_fields_without_network_or_echo(self):
        result = subprocess.run([sys.executable, str(Path(validation.__file__))],
                                input=json.dumps({"apiKey": FAKE_KEY, "unexpected": True}),
                                text=True, capture_output=True, check=True)
        self.assertEqual({"ok": False, "code": "INVALID_PRIVATE_INPUT"}, json.loads(result.stdout))
        self.assertEqual("", result.stderr)
        self.assertNotIn(FAKE_KEY, result.stdout)


if __name__ == "__main__":
    unittest.main()
