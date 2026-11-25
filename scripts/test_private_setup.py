"""Offline safety tests: fake SMTP/management only, no real credentials or network."""
import importlib.util
from pathlib import Path
import smtplib
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("pacefit_smtp", Path(__file__).with_name("configure-smtp.py"))
smtp_setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smtp_setup)


class FakeAdmin:
    REF = "unit-test-only"

    def __init__(self, existing=False):
        self.config = {"smtp_host": "existing.test" if existing else "", "mailer_autoconfirm": False}
        self.writes = []
        self.checked = False

    def assert_target(self):
        self.checked = True

    def management(self, method, path, body=None):
        if method == "PATCH":
            self.writes.append(body)
            self.config.update(body)
        return self.config


class PrivateSetupTest(unittest.TestCase):
    def setUp(self):
        self.admin = FakeAdmin()
        self.server = MagicMock()
        self.server.__enter__.return_value = self.server

    def configure(self, port="587", **extra):
        arguments = dict(host="smtp.example.test", port_text=port, sender="sender@example.test",
                         username="sender@example.test", password="unit-test-only", admin=self.admin)
        arguments.update(extra)
        return smtp_setup.configure_smtp(**arguments)

    @patch.object(smtp_setup.smtplib, "SMTP")
    def test_starttls_precedes_login_and_confirmations_remain_enabled(self, smtp):
        smtp.return_value = self.server
        self.assertEqual("configured", self.configure())
        names = [call[0] for call in self.server.method_calls]
        self.assertLess(names.index("starttls"), names.index("login"))
        context = self.server.starttls.call_args.kwargs["context"]
        self.assertTrue(context.check_hostname)
        self.assertTrue(self.admin.checked)
        self.assertFalse(self.admin.writes[0]["mailer_autoconfirm"])
        self.assertEqual(10, self.admin.writes[0]["password_min_length"])

    @patch.object(smtp_setup.smtplib, "SMTP_SSL")
    def test_tls_465_uses_verified_context(self, smtp):
        smtp.return_value = self.server
        self.assertEqual("configured", self.configure("465"))
        self.assertTrue(smtp.call_args.kwargs["context"].check_hostname)
        self.server.starttls.assert_not_called()
        self.server.login.assert_called_once()

    @patch.object(smtp_setup.smtplib, "SMTP")
    def test_auth_failure_does_not_write_server_settings(self, smtp):
        smtp.return_value = self.server
        self.server.login.side_effect = smtplib.SMTPAuthenticationError(535, b"unit test rejection")
        with self.assertRaises(smtplib.SMTPAuthenticationError):
            self.configure()
        self.assertEqual([], self.admin.writes)

    @patch.object(smtp_setup.smtplib, "SMTP")
    def test_starttls_failure_does_not_send_password(self, smtp):
        smtp.return_value = self.server
        self.server.starttls.side_effect = smtplib.SMTPNotSupportedError("unit test rejection")
        with self.assertRaises(smtplib.SMTPNotSupportedError):
            self.configure()
        self.server.login.assert_not_called()
        self.assertEqual([], self.admin.writes)

    @patch.object(smtp_setup.smtplib, "SMTP")
    def test_existing_settings_are_not_overwritten(self, smtp):
        self.admin = FakeAdmin(existing=True)
        self.assertEqual("existing", self.configure())
        smtp.assert_not_called()
        self.assertEqual([], self.admin.writes)

    @patch.object(smtp_setup.smtplib, "SMTP")
    def test_invalid_host_is_rejected_before_network(self, smtp):
        with self.assertRaises(ValueError):
            self.configure(host="https://smtp.example.test")
        smtp.assert_not_called()
        self.assertFalse(self.admin.checked)

    @patch.object(smtp_setup.smtplib, "SMTP")
    def test_cleartext_port_is_rejected_before_network(self, smtp):
        with self.assertRaises(ValueError):
            self.configure("25")
        smtp.assert_not_called()
        self.assertFalse(self.admin.checked)

    @patch.object(smtp_setup.smtplib, "SMTP")
    def test_missing_password_is_rejected_before_network(self, smtp):
        with self.assertRaises(ValueError):
            self.configure(password="")
        smtp.assert_not_called()
        self.assertFalse(self.admin.checked)


if __name__ == "__main__":
    unittest.main()
