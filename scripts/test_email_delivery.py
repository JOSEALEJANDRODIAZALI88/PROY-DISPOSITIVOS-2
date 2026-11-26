"""Offline safety tests for the opt-in inbox helper; these never send email."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch
import json

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location(
    "pacefit_email_delivery", Path(__file__).with_name("check-email-delivery.py")
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EmailSafetyTest(unittest.TestCase):
    def check(self):
        instance = object.__new__(module.DeliveryCheck)
        instance.admin = MagicMock(REF="test-project", BASE="https://example.invalid")
        instance.admin.assert_target = MagicMock()
        instance.run = "a" * 32
        instance.email = "owned+pacefit.aaaaaaaaaaaaaaaa@gmail.com"
        instance.user_id = None
        instance.password, instance.anon, instance.service = "test-only", "public", "private"
        instance.attempted = False
        return instance

    def test_prepare_refuses_confirmation_disabled_before_reading_keys(self):
        check = self.check()
        check.admin.management.return_value = {"mailer_autoconfirm": True}
        with self.assertRaises(RuntimeError):
            check.prepare()
        check.admin.assert_target.assert_called_once()
        check.admin.keys.assert_not_called()

    def test_prepare_alias_only_routes_to_configured_own_gmail(self):
        check = self.check()
        check.admin.management.return_value = {
            "mailer_autoconfirm": False, "smtp_host": "smtp.gmail.com",
            "smtp_admin_email": "owned@gmail.com", "smtp_user": "owned@gmail.com",
            "mailer_templates_confirmation_content": "{{ .Token }}",
        }
        check.admin.keys.return_value = ("public", "private")
        check.prepare()
        self.assertEqual(check.email, "owned+pacefit.aaaaaaaaaaaaaaaa@gmail.com")

    def test_prepare_refuses_sender_not_matching_login(self):
        check = self.check()
        check.admin.management.return_value = {
            "mailer_autoconfirm": False, "smtp_host": "smtp.gmail.com",
            "smtp_admin_email": "other@gmail.com", "smtp_user": "owned@gmail.com",
            "mailer_templates_confirmation_content": "{{ .Token }}",
        }
        with self.assertRaises(RuntimeError):
            check.prepare()
        check.admin.keys.assert_not_called()

    def test_send_once_requires_pending_confirmation_not_admin_bypass(self):
        check = self.check()
        check.request = MagicMock(return_value={"user": {
            "id": "bbbbbbbb-0000-0000-0000-cccccccccccc", "email": check.email,
            "user_metadata": {"pacefit_delivery_run": check.run},
        }})
        check.send()
        with self.assertRaises(RuntimeError):
            check.send()
        check.request.assert_called_once()
        path, payload = check.request.call_args.args
        self.assertEqual(path, "/auth/v1/signup")
        self.assertEqual(payload["data"]["pacefit_delivery_run"], check.run)

    def test_raw_gotrue_user_response_is_accepted_without_a_session(self):
        check = self.check()
        check.request = MagicMock(return_value={
            "id": "bbbbbbbb-0000-0000-0000-cccccccccccc", "email": check.email,
            "user_metadata": {"pacefit_delivery_run": check.run},
        })
        check.send()
        self.assertEqual(check.user_id, "bbbbbbbb-0000-0000-0000-cccccccccccc")
        self.assertTrue(check.attempted)

    def test_signup_never_accepts_confirmation_bypass_or_other_account(self):
        base = {"id": "bbbbbbbb-0000-0000-0000-cccccccccccc",
                "email": "owned+pacefit.aaaaaaaaaaaaaaaa@gmail.com",
                "user_metadata": {"pacefit_delivery_run": "a" * 32}}
        for response in (
            {**base, "access_token": "test-token"},
            {"user": base, "session": {"access_token": "test-token"}},
            {**base, "email_confirmed_at": "2026-11-25T00:00:00Z"},
            {**base, "email": "other@gmail.com"},
            {**base, "user_metadata": {}},
            None,
        ):
            with self.subTest(response_kind=type(response).__name__):
                check = self.check()
                check.request = MagicMock(return_value=response)
                with self.assertRaises(RuntimeError):
                    check.send()
                self.assertIsNone(check.user_id)

    def test_incomplete_code_never_contacts_auth(self):
        check = self.check()
        check.request = MagicMock()
        for code in ("", "123", "1234567", "12a456", "１２３４５６"):
            with self.assertRaises(RuntimeError):
                check.confirm(code)
        check.request.assert_not_called()

    def test_cleanup_never_deletes_an_existing_or_different_account(self):
        check = self.check()
        check.attempted = True
        check.user_id = "bbbbbbbb-0000-0000-0000-cccccccccccc"
        check.request = MagicMock(return_value={
            "email": "other@gmail.com", "user_metadata": {"pacefit_delivery_run": check.run},
        })
        with self.assertRaises(RuntimeError):
            check.cleanup()
        self.assertEqual(check.request.call_count, 1)
        self.assertEqual(check.request.call_args.kwargs["method"], "GET")

    def test_cleanup_requires_the_exact_run_tag_even_for_same_email(self):
        check = self.check()
        check.attempted = True
        check.user_id = "bbbbbbbb-0000-0000-0000-cccccccccccc"
        check.request = MagicMock(return_value={"email": check.email, "user_metadata": {}})
        with self.assertRaises(RuntimeError):
            check.cleanup()
        self.assertEqual(check.request.call_count, 1)

    def test_cleanup_deleted_account_is_checked_again(self):
        check = self.check()
        check.attempted = True
        check.user_id = "bbbbbbbb-0000-0000-0000-cccccccccccc"
        check.request = MagicMock(side_effect=[
            {"email": check.email, "user_metadata": {"pacefit_delivery_run": check.run}}, None, None,
        ])
        check.cleanup()
        self.assertEqual([call.kwargs["method"] for call in check.request.call_args_list],
                         ["GET", "DELETE", "GET"])
        self.assertFalse(check.attempted)
        self.assertIsNone(check.password)

    def test_no_send_means_no_cleanup_or_account_enumeration(self):
        check = self.check()
        check.cleanup()
        check.admin.management.assert_not_called()

    def test_public_code_refresh_logout_and_owned_cleanup_write_only_safe_evidence(self):
        check = self.check()
        check.attempted = True
        check.user_id = "bbbbbbbb-0000-0000-0000-cccccccccccc"
        check.request = MagicMock(side_effect=[
            {"user": {"id": check.user_id}, "refresh_token": "test-refresh",
             "access_token": "test-access"},
            {"user": {"id": check.user_id}, "access_token": "test-renewed-access"},
            None,
            {"email": check.email, "user_metadata": {"pacefit_delivery_run": check.run}},
            None, None,
        ])
        with tempfile.TemporaryDirectory() as folder, patch.object(module, "ROOT", Path(folder)):
            check.confirm("12345678")
            evidence = list(Path(folder).rglob("result-*.json"))
            self.assertEqual(len(evidence), 1)
            text = evidence[0].read_text(encoding="utf-8")
            data = json.loads(text)
            self.assertTrue(data["otp_verified_via_public_auth_api"])
            self.assertFalse(data["admin_generated_otp"])
            self.assertTrue(data["disposable_auth_account_deleted_verified"])
            for secret in (check.email, "12345678", "test-refresh", "test-access", "test-only"):
                self.assertNotIn(secret, text)
        paths = [call.args[0] for call in check.request.call_args_list]
        self.assertEqual(paths[:3], ["/auth/v1/verify", "/auth/v1/token?grant_type=refresh_token",
                                     "/auth/v1/logout?scope=global"])
        self.assertFalse(check.attempted)

    def test_refresh_failure_does_not_create_delivery_approval(self):
        check = self.check()
        check.user_id = "bbbbbbbb-0000-0000-0000-cccccccccccc"
        check.request = MagicMock(side_effect=[
            {"user": {"id": check.user_id}, "refresh_token": "test-refresh"},
            RuntimeError("No se completo la renovacion."),
        ])
        with tempfile.TemporaryDirectory() as folder, patch.object(module, "ROOT", Path(folder)):
            with self.assertRaises(RuntimeError):
                check.confirm("12345678")
            self.assertEqual(list(Path(folder).rglob("result-*.json")), [])


if __name__ == "__main__":
    unittest.main()
