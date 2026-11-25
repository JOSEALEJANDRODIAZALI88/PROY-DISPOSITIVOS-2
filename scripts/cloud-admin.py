"""Deployment helpers for the explicitly authorized PaceFit Supabase project.

Credentials are read from the Supabase CLI Windows keyring, never printed.
Cloud checks create/clean only their own disposable users; no database resets.
"""
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
import secrets

REF = "ymfypllkcrqwfwihqzpf"
ORG = "utwxsqlroxrpgilnoqpk"
ROOT = Path(__file__).resolve().parent.parent
BASE = f"https://{REF}.supabase.co"


def access_token():
    if os.environ.get("SUPABASE_ACCESS_TOKEN"):
        return os.environ["SUPABASE_ACCESS_TOKEN"]

    class Credential(ctypes.Structure):
        _fields_ = [
            ("flags", wintypes.DWORD), ("type", wintypes.DWORD),
            ("target", wintypes.LPWSTR), ("comment", wintypes.LPWSTR),
            ("last_written", wintypes.FILETIME), ("size", wintypes.DWORD),
            ("blob", ctypes.POINTER(ctypes.c_ubyte)), ("persist", wintypes.DWORD),
            ("count", wintypes.DWORD), ("attributes", ctypes.c_void_p),
            ("alias", wintypes.LPWSTR), ("user", wintypes.LPWSTR),
        ]
    api = ctypes.WinDLL("Advapi32.dll", use_last_error=True)
    api.CredReadW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                             ctypes.POINTER(ctypes.POINTER(Credential))]
    api.CredReadW.restype = wintypes.BOOL
    api.CredFree.argtypes = [ctypes.c_void_p]
    pointer = ctypes.POINTER(Credential)()
    if not api.CredReadW("Supabase CLI:supabase", 1, 0, ctypes.byref(pointer)):
        raise RuntimeError("Run Supabase CLI login first.")
    try:
        return ctypes.string_at(pointer.contents.blob, pointer.contents.size).decode("utf-8").strip()
    finally:
        api.CredFree(pointer)


def management(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(
        "https://api.supabase.com" + path, data=data, method=method,
        headers={"Authorization": "Bearer " + access_token(),
                 "Content-Type": "application/json", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            payload = response.read()
            return json.loads(payload) if payload.strip() else None
    except urllib.error.HTTPError as error:
        # Management responses/configuration can contain secrets: never dump them.
        raise RuntimeError(f"Supabase management rejected the request (HTTP {error.code}).") from None


def assert_target():
    state = json.loads((ROOT / "supabase/.temp/pacefit-cloud-state.json").read_text())
    if state.get("ref") != REF or state.get("organization") != ORG:
        raise RuntimeError("Cloud deployment state does not match the authorized project.")
    if management("GET", f"/v1/organizations/{ORG}").get("plan") != "free":
        raise RuntimeError("Stopped: organization is no longer on the free plan.")
    project = management("GET", f"/v1/projects/{REF}")
    if project.get("name") != "PaceFit AI" or project.get("status") != "ACTIVE_HEALTHY":
        raise RuntimeError("PaceFit project is not healthy; no changes made.")


def keys():
    result = management("GET", f"/v1/projects/{REF}/api-keys")
    anonymous = next((item["api_key"] for item in result if item.get("name") == "anon"), None)
    service = next((item["api_key"] for item in result if item.get("name") == "service_role"), None)
    if not anonymous or not service:
        raise RuntimeError("Expected legacy public/server keys are unavailable; inspect configuration.")
    return anonymous, service


def configure():
    # Keep email confirmation enabled; never relax security to bypass missing SMTP.
    management("PATCH", f"/v1/projects/{REF}/config/auth",
               {"password_min_length": 10, "mailer_autoconfirm": False})
    config = management("GET", f"/v1/projects/{REF}/config/auth")
    anonymous, _ = keys()
    properties = ROOT / "local.properties"
    lines = properties.read_text(encoding="utf-8").splitlines() if properties.exists() else []
    desired = {"SUPABASE_URL": BASE, "SUPABASE_ANON_KEY": anonymous}
    for line in lines:
        key, separator, value = line.partition("=")
        if separator and key in desired and value.strip() not in ("", desired[key]):
            raise RuntimeError("Existing local Supabase configuration differs; not overwritten.")
    lines = [line for line in lines if line.partition("=")[0] not in desired]
    lines += [key + "=" + value for key, value in desired.items()]
    properties.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({
        "url": BASE, "androidPublicConfigurationWritten": True,
        "emailConfirmationEnabled": not config.get("mailer_autoconfirm", True),
        "minimumPasswordLength": config.get("password_min_length"),
        "customSmtpConfigured": bool(config.get("smtp_host")),
        "serverSecretsWrittenToApk": False,
    }))


def smoke():
    anonymous, service = keys()
    environment = os.environ.copy()
    environment.update(PACEFIT_CLOUD_TEST_REF=REF, PACEFIT_CLOUD_URL=BASE,
                       PACEFIT_CLOUD_ANON_KEY=anonymous, PACEFIT_CLOUD_SERVICE_KEY=service)
    subprocess.run(["node", str(ROOT / "scripts/test-cloud.mjs")],
                   cwd=ROOT, env=environment, check=True)


def audit():
    result = management("POST", f"/v1/projects/{REF}/database/query", {"query": """
        select 'migrations' as item, count(*)::text as value
          from supabase_migrations.schema_migrations
        union all select 'rls_tables', count(*)::text from pg_class c
          join pg_namespace n on n.oid=c.relnamespace
          where n.nspname='public' and c.relname in
            ('social_profiles','city_entries','ai_usage') and c.relrowsecurity
        union all select 'private_bucket', count(*)::text from storage.buckets
          where id='city-photos' and public=false
        union all select 'retention_jobs', count(*)::text from cron.job
          where jobname='pacefit-ai-retention' and active=true;
    """})
    print(json.dumps({"project": REF, "audit": result}))


def status():
    config = management("GET", f"/v1/projects/{REF}/config/auth")
    names = {row.get("name") for row in management("GET", f"/v1/projects/{REF}/secrets")}
    print(json.dumps({
        "project": REF, "plan": "free",
        "emailConfirmationEnabled": not config.get("mailer_autoconfirm", True),
        "customSmtpConfigured": bool(config.get("smtp_host")),
        "geminiKeyDeployed": "GEMINI_API_KEY" in names,
        "geminiModelDeployed": "GEMINI_MODEL" in names,
        "privateGeminiFileExists": (ROOT / ".env").exists(),
        "confirmationUsesOtp": "{{ .Token }}" in config.get("mailer_templates_confirmation_content", ""),
    }))


def configure_confirmation():
    template = (ROOT / "supabase/templates/confirmation.html").read_text(encoding="utf-8")
    if "{{ .Token }}" not in template or any(value in template for value in
            ("{{ .ConfirmationURL }}", "{{ .SiteURL }}", "{{ .RedirectTo }}", "localhost")):
        raise RuntimeError("Confirmation template must use an OTP, not redirect bearer tokens.")
    management("PATCH", f"/v1/projects/{REF}/config/auth", {
        "mailer_templates_confirmation_content": template,
        "mailer_subjects_confirmation": "Confirma tu correo - PaceFit AI",
        "mailer_autoconfirm": False,
    })
    config = management("GET", f"/v1/projects/{REF}/config/auth")
    if (config.get("mailer_templates_confirmation_content") != template
            or config.get("mailer_autoconfirm", True)):
        raise RuntimeError("Confirmation configuration did not match; inspect before testing delivery.")
    print("Signup confirmation uses a one-time email code; confirmation remains required. No email sent.")


def deploy_gemini():
    # Never pass a key in CLI arguments/history or print the environment file.
    deployed = {row.get("name") for row in management("GET", f"/v1/projects/{REF}/secrets")}
    if deployed.intersection(("GEMINI_API_KEY", "GEMINI_MODEL")):
        raise RuntimeError("Existing Gemini server secrets were not overwritten; review before changing them.")
    path = ROOT / ".env"
    if not path.exists():
        raise RuntimeError("Complete the private configure-gemini.ps1 window first.")
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        key, separator, value = line.partition("=")
        if separator and key in ("GEMINI_API_KEY", "GEMINI_MODEL"):
            values[key] = value.strip()
    # New Google authorization keys need not use the legacy AIza prefix.
    if not re.fullmatch(r"[A-Za-z0-9_.-]{20,512}", values.get("GEMINI_API_KEY", "")):
        raise RuntimeError("Private Gemini key is missing or invalid; no secrets changed.")
    if not re.fullmatch(r"gemini-\d+(?:\.\d+)?-flash", values.get("GEMINI_MODEL", "")):
        raise RuntimeError("Select an available stable Flash model in the private setup.")
    # Validate opaque key formats with the intended provider, never by sending a key in a URL.
    request = urllib.request.Request("https://generativelanguage.googleapis.com/v1beta/models",
                                     headers={"x-goog-api-key": values["GEMINI_API_KEY"]})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            models = json.load(response).get("models", [])
    except Exception:
        raise RuntimeError("Google could not validate the private key/model; no secrets changed.") from None
    if not any(row.get("name") == "models/" + values["GEMINI_MODEL"]
               and "generateContent" in row.get("supportedGenerationMethods", []) for row in models):
        raise RuntimeError("Selected model is unavailable for this key; no secrets changed.")
    management("POST", f"/v1/projects/{REF}/secrets",
               [{"name": key, "value": values[key]} for key in ("GEMINI_API_KEY", "GEMINI_MODEL")])
    print("Gemini secrets deployed to the authorized Free project; no inference requested.")


def android_smoke(serial):
    if serial not in ("emulator-5554", "emulator-5556"):
        raise RuntimeError("Only the task's disposable emulator serials are allowed.")
    anonymous, service = keys()

    def request(path, method="GET", body=None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(BASE + path, data=data, method=method,
                                     headers={"apikey": anonymous, "Authorization": "Bearer " + service,
                                              "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as response:
                return json.load(response) if response.status != 204 else None
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            raise RuntimeError(f"Cloud test setup/cleanup failed (HTTP {error.code}).") from None

    # Never package service-role keys. Only this disposable account's credentials cross adb stdin.
    email = f"pacefit-android-{uuid.uuid4()}@example.test"
    password = secrets.token_urlsafe(36) + "Aa9!"
    # Generate a disposable signup OTP without sending email or bypassing client verification.
    # This verifies Auth's OTP API, NOT Gmail delivery. No code/credential is printed.
    created = request("/auth/v1/admin/generate_link", "POST",
                      {"type": "signup", "email": email, "password": password})
    user_id = created.get("user", created).get("id")
    properties = created.get("properties", created)
    otp = properties.get("email_otp", "")
    if not user_id:
        raise RuntimeError("Disposable signup account was not created as expected.")
    try:
        if not re.fullmatch(r"(?:[0-9]{6}|[0-9]{8})", otp):
            raise RuntimeError("Expected a six/eight-digit disposable signup OTP; no credentials printed.")
        adb = ["adb", "-s", serial]
        sdk = subprocess.check_output(adb + ["shell", "getprop", "ro.build.version.sdk"], text=True).strip()
        if sdk not in ("26", "35"):
            raise RuntimeError("Only the disposable API 26/35 emulators are allowed.")
        subprocess.run(adb + ["install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk")], check=True)
        subprocess.run(adb + ["install", "-r", "-t", str(ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")], check=True)
        subprocess.run(adb + ["shell", "run-as com.pacefit.ai mkdir -p no_backup"], check=True)
        fixture = json.dumps({"email": email, "password": password, "user_id": user_id, "email_otp": otp}).encode()
        subprocess.run(adb + ["shell", "run-as com.pacefit.ai sh -c 'cat > no_backup/pacefit-cloud-test-fixture.json'"],
                       input=fixture, check=True)
        result = subprocess.run(adb + ["shell", "am", "instrument", "-w",
                                      "com.pacefit.ai.test/com.pacefit.ai.PaceFitTestRunner"],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, check=True)
        folder = ROOT / f"app/build/verification/cloud-api{sdk}"
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "instrumentation.txt").write_text(result.stdout, encoding="utf-8")
        # Instrumentation itself can return exit 0 after failures; require its actual JUnit result.
        if "OK (24 tests)" not in result.stdout or "FAILURES" in result.stdout:
            raise RuntimeError(f"Cloud Android instrumentation failed; inspect {folder / 'instrumentation.txt'}.")
        print(f"PASS: 24 Android tests on API {sdk}, including real HTTPS OTP/login/photo publish/session restore/account deletion.")
        if request(f"/auth/v1/admin/users/{user_id}") is not None:
            raise RuntimeError("Android client did not actually delete its disposable Auth account.")
        print("Verified the Android-deleted account no longer exists on the server.")
    finally:
        # Only this call's UUID. Preserve all unrelated user/object records.
        objects = request("/storage/v1/object/list/city-photos", "POST", {"prefix": user_id, "limit": 100})
        if objects:
            request("/storage/v1/object/city-photos", "DELETE",
                    {"prefixes": [f"{user_id}/{item['name']}" for item in objects]})
        request(f"/auth/v1/admin/users/{user_id}", "DELETE")
        subprocess.run(["adb", "-s", serial, "shell",
                        "run-as com.pacefit.ai rm -f no_backup/pacefit-cloud-test-fixture.json"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    actions = {"configure": configure, "smoke": smoke, "audit": audit,
               "status": status, "deploy-gemini": deploy_gemini, "configure-confirmation": configure_confirmation}
    try:
        assert_target()
        if len(sys.argv) == 3 and sys.argv[1] == "android-smoke":
            android_smoke(sys.argv[2])
        else:
            action = actions.get(sys.argv[1] if len(sys.argv) == 2 else "")
            if action is None:
                raise RuntimeError("Usage: python scripts/cloud-admin.py configure|configure-confirmation|smoke|audit|status|deploy-gemini|android-smoke <serial>")
            action()
    except Exception as error:
        print(f"Cloud operation failed: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
