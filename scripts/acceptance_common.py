"""Private, narrowly scoped clients shared by acceptance tools. Never log credentials."""
import importlib.util
import json
import os
from pathlib import Path
import secrets
import sys
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
JOURNALS = ROOT / "supabase/.temp/acceptance-fixtures"

AI_FAILURE_MESSAGES = {
    "GEMINI_NOT_CONFIGURED": "Gemini no está configurado en el servidor.",
    "GEMINI_QUOTA_EXHAUSTED": "Google limitó o agotó la cuota gratuita. Espera o clasifica manualmente; no actives pagos.",
    "GEMINI_ACCESS_DENIED": "Google rechazó el acceso. Revisa permisos de la clave y verificación gratuita del proyecto.",
    "GEMINI_REQUEST_REJECTED": "Google rechazó la configuración de la solicitud. No vuelvas a enviar aún.",
    "GEMINI_UNAVAILABLE": "Google no está disponible temporalmente. No hubo reintentos automáticos.",
    "GEMINI_TIMEOUT": "Google tardó demasiado. No hubo reintentos automáticos.",
    "GEMINI_RESPONSE_TRUNCATED": "Google devolvió una respuesta incompleta. Hace falta revisar el límite de salida.",
    "GEMINI_SAFETY_BLOCKED": "Google bloqueó esta imagen por seguridad. Elige fotos sin rostros ni datos privados.",
    "GEMINI_INVALID_RESPONSE": "Google no devolvió una clasificación válida. No se inventó una respuesta.",
    "AI_QUOTA_UNAVAILABLE": "El servidor no pudo comprobar la cuota local.",
    "AI_USER_QUOTA_EXHAUSTED": "Se alcanzaron 40 consultas por hora. Clasifica manualmente.",
}


class AiGatewayError(RuntimeError):
    def __init__(self, code, status):
        self.code = code
        self.http_status = status
        super().__init__(AI_FAILURE_MESSAGES[code] + f" [{code}; HTTP {status}]")


def admin_module():
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("pacefit_admin", ROOT / "scripts/cloud-admin.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.assert_target()
    return module


class Cloud:
    def __init__(self, admin=None):
        self.admin = admin or admin_module()
        self.anon, self.service = self.admin.keys()

    def request(self, path, method="GET", body=None, token=None, server=False,
                binary=False, missing_ok=False, content_type="application/json"):
        if not path.startswith("/") or path.startswith("//"):
            raise RuntimeError("Only the authorized HTTPS project is allowed.")
        data = body if isinstance(body, bytes) else (
            json.dumps(body).encode() if body is not None else None)
        request = urllib.request.Request(self.admin.BASE + path, method=method, data=data,
            headers={"apikey": self.anon, "Authorization": "Bearer " +
                (self.service if server else token or self.anon), "Content-Type": content_type})
        try:
            with urllib.request.urlopen(request, timeout=65) as response:
                value = response.read()
                return value if binary else json.loads(value) if value.strip() else None
        except urllib.error.HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            if path == "/functions/v1/ai-gateway":
                try:
                    public = json.loads(error.read(16384))
                    code = public.get("code") if isinstance(public, dict) else None
                except (ValueError, OSError):
                    code = None
                if isinstance(code, str) and code in AI_FAILURE_MESSAGES:
                    raise AiGatewayError(code, error.code) from None
            # Provider responses may contain names, emails or tokens.
            raise RuntimeError(f"Authorized cloud request failed (HTTP {error.code}).") from None

    def query(self, sql):
        return self.admin.management("POST",
            f"/v1/projects/{self.admin.REF}/database/query", {"query": sql})


class DisposableAccount:
    """Admin-confirmed fixture: never sends email and never proves inbox delivery."""
    def __init__(self, cloud):
        self.cloud = cloud
        self.run = uuid.uuid4().hex
        self.user_id = None
        self.token = None

    @property
    def journal(self):
        return JOURNALS / (self.run + ".json")

    def save_journal(self):
        JOURNALS.mkdir(parents=True, exist_ok=True)
        value = {"project": self.cloud.admin.REF, "run": self.run, "user_id": self.user_id,
                 "email": f"pacefit-acceptance-{self.run}@example.test"}
        temporary = self.journal.with_suffix(".json.tmp")
        with temporary.open("w", encoding="utf-8") as output:
            json.dump(value, output)
            output.flush()
            os.fsync(output.fileno())
        temporary.replace(self.journal)

    def remove_journal(self):
        if self.journal.exists():
            value = json.loads(self.journal.read_text())
            if value.get("project") != self.cloud.admin.REF or value.get("run") != self.run:
                raise RuntimeError("Fixture journal mismatch; it was not removed.")
            self.journal.unlink()

    def __enter__(self):
        try:
            # Durable intent precedes Auth creation: a killed process can be cleaned without
            # enumerating unrelated users or storing its password/session.
            if self.journal.exists():
                raise RuntimeError("Fixture journal already exists.")
            self.save_journal()
            email = f"pacefit-acceptance-{self.run}@example.test"
            password = secrets.token_urlsafe(36) + "Aa9!"
            user = self.cloud.request("/auth/v1/admin/users", "POST", {
                "email": email, "password": password, "email_confirm": True,
                "app_metadata": {"pacefit_acceptance_run": self.run}}, server=True)
            self.user_id = str(uuid.UUID(user["id"]))
            self.save_journal()
            session = self.cloud.request("/auth/v1/token?grant_type=password", "POST",
                                         {"email": email, "password": password})
            self.token = session["access_token"]
            self.cloud.request("/rest/v1/rpc/accept_social_profile", "POST",
                               {"p_display_name": "Prueba temporal PaceFit"}, token=self.token)
            return self
        except Exception:
            self.close()
            raise

    def close(self):
        if not self.user_id:
            if not self.journal.exists():
                return
            # Ambiguous create response: recover only the exact durable run/email pair.
            if len(self.run) != 32 or uuid.UUID(hex=self.run).hex != self.run:
                raise RuntimeError("Invalid fixture run.")
            email = f"pacefit-acceptance-{self.run}@example.test"
            rows = self.cloud.query("SELECT id FROM auth.users WHERE email='" + email +
                "' AND raw_app_meta_data->>'pacefit_acceptance_run'='" + self.run + "'")
            if not rows:
                self.remove_journal()
                return
            if len(rows) != 1:
                raise RuntimeError("Ambiguous fixture ownership; cleanup stopped.")
            self.user_id = str(uuid.UUID(rows[0]["id"]))
        path = f"/auth/v1/admin/users/{self.user_id}"
        user = self.cloud.request(path, server=True, missing_ok=True)
        if user is None:
            self.remove_journal()
            return
        if (user.get("app_metadata") or {}).get("pacefit_acceptance_run") != self.run:
            raise RuntimeError("Fixture ownership mismatch; no account was removed.")
        # Only this invocation's UUID. Account cleanup also removes any owned test photos.
        while True:
            objects = self.cloud.request("/storage/v1/object/list/city-photos", "POST",
                {"prefix": self.user_id, "limit": 100}, server=True)
            if not objects:
                break
            names = [item.get("name", "") for item in objects]
            if any(not name or "/" in name or "\\" in name for name in names):
                raise RuntimeError("Unexpected fixture object; cleanup stopped.")
            self.cloud.request("/storage/v1/object/city-photos", "DELETE",
                {"prefixes": [f"{self.user_id}/{name}" for name in names]}, server=True)
        self.cloud.request(path, "DELETE", server=True)
        if self.cloud.request(path, server=True, missing_ok=True) is not None:
            raise RuntimeError("Disposable account cleanup was not confirmed.")
        self.remove_journal()
        self.token = None

    def __exit__(self, *_):
        self.close()


def recover_fixtures(cloud):
    """Explicit recovery after interruption; do not run while another acceptance tool runs."""
    count = 0
    if not JOURNALS.exists():
        return count
    for path in JOURNALS.glob("*.json"):
        if path.is_symlink() or path.resolve().parent != JOURNALS.resolve():
            continue
        if len(path.stem) != 32 or any(char not in "0123456789abcdef" for char in path.stem):
            continue
        value = json.loads(path.read_text())
        if (value.get("project") != cloud.admin.REF or value.get("run") != path.stem
                or value.get("email") != f"pacefit-acceptance-{path.stem}@example.test"):
            raise RuntimeError("Unexpected fixture journal; no broad cleanup was attempted.")
        fixture = DisposableAccount(cloud)
        fixture.run = path.stem
        fixture.user_id = str(uuid.UUID(value["user_id"])) if value.get("user_id") else None
        fixture.close()
        count += 1
    return count


def publish_fixture(account, jpeg, label="MEDIUM"):
    """Synthetic location and image ONLY for a disposable automated fixture."""
    entry_id = str(uuid.uuid4())
    boundary = "pacefit-" + uuid.uuid4().hex
    parts = []
    for key, value in {"id": entry_id, "kind": "PLOGGING", "label": label,
                       "description": "Prueba automatizada temporal, no actividad real",
                       "latitude": "-16.5", "longitude": "-68.15", "consent": "true"}.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="photo"; filename="fixture.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'.encode() + jpeg + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    result = account.cloud.request("/functions/v1/city-submit", "POST", b"".join(parts),
        token=account.token, content_type="multipart/form-data; boundary=" + boundary)
    return result["data"]


if __name__ == "__main__":
    try:
        if sys.argv[1:] != ["recover-fixtures"]:
            raise RuntimeError("Usage: python scripts/acceptance_common.py recover-fixtures")
        print("Owned interrupted fixtures recovered:", recover_fixtures(Cloud()))
    except Exception:
        print("Fixture recovery stopped; no credentials or unrelated users were printed.", file=sys.stderr)
        sys.exit(1)
