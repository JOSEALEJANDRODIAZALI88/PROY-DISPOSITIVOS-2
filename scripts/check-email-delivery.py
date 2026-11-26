"""Opt-in inbox confirmation check, using only a unique alias of the configured Gmail.

No inbox access, Gmail password, admin-generated OTP, secret in arguments/logs or
changes to an existing account. The user must receive and privately enter the code.
"""
import datetime
import importlib.util
import json
from pathlib import Path
import queue
import re
import secrets
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
import uuid
import webbrowser

ROOT = Path(__file__).resolve().parent.parent


class DeliveryCheck:
    def __init__(self):
        sys.dont_write_bytecode = True
        spec = importlib.util.spec_from_file_location("pacefit_cloud_admin",
                                                     ROOT / "scripts/cloud-admin.py")
        self.admin = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.admin)
        self.run = uuid.uuid4().hex
        self.email = self.password = self.anon = self.service = None
        self.user_id = None
        self.attempted = False

    def prepare(self):
        self.admin.assert_target()
        config = self.admin.management("GET", f"/v1/projects/{self.admin.REF}/config/auth")
        sender = config.get("smtp_admin_email", "").strip()
        if (config.get("mailer_autoconfirm", True) or config.get("smtp_host") != "smtp.gmail.com"
                or config.get("smtp_user", "").strip().casefold() != sender.casefold()
                or "{{ .Token }}" not in config.get("mailer_templates_confirmation_content", "")):
            raise RuntimeError("Revisa Gmail, remitente, plantilla OTP y confirmacion antes de enviar.")
        match = re.fullmatch(r"([a-zA-Z0-9.]+)(?:\+[^@\s]+)?@(gmail\.com|googlemail\.com)", sender, re.I)
        if not match:
            raise RuntimeError("Este asistente solo admite el Gmail propio ya configurado.")
        self.email = f"{match.group(1)}+pacefit.{self.run[:16]}@{match.group(2)}"
        if len(self.email.split("@")[0]) > 64:
            raise RuntimeError("El alias supera el limite de correo; no se envio nada.")
        self.password = secrets.token_urlsafe(36) + "Aa9!"
        self.anon, self.service = self.admin.keys()

    def request(self, path, body=None, token=None, method="POST", admin=False):
        raw = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(
            self.admin.BASE + path, data=raw, method=method,
            headers={"apikey": self.anon, "Content-Type": "application/json",
                     "Authorization": "Bearer " + (self.service if admin else token or self.anon)},
        )
        try:
            with urllib.request.urlopen(req, timeout=50) as response:
                data = response.read()
                return json.loads(data) if data.strip() else None
        except urllib.error.HTTPError as error:
            if admin and error.code == 404:
                return None
            if error.code == 429:
                raise RuntimeError("Limite de intentos: espera antes de volver a solicitar correo.") from None
            if path == "/auth/v1/verify" and error.code in (400, 401, 403):
                raise RuntimeError("Codigo incorrecto o caducado. No se confirmo el correo.") from None
            raise RuntimeError(f"No se completo la solicitud (HTTP {error.code}).") from None
        except urllib.error.URLError:
            raise RuntimeError("No hay conexion segura; no se afirma que la operacion termino.") from None

    def send(self):
        if self.attempted or not self.email:
            raise RuntimeError("No se repite automaticamente una solicitud de correo.")
        self.attempted = True
        result = self.request("/auth/v1/signup", {
            "email": self.email, "password": self.password,
            "data": {"pacefit_delivery_run": self.run},
        })
        # GoTrue's raw signup response can be the user itself, while SDK-style
        # responses wrap it in "user". Neither shape may bypass confirmation.
        user = result.get("user", result) if isinstance(result, dict) else {}
        candidate = user.get("id") if isinstance(user, dict) else None
        if (not isinstance(candidate, str)
                or not re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", candidate)
                or result.get("access_token") or result.get("session")
                or user.get("email_confirmed_at")
                or user.get("email", "").casefold() != self.email.casefold()
                or (user.get("user_metadata") or {}).get("pacefit_delivery_run") != self.run):
            raise RuntimeError("No se obtuvo un registro pendiente de confirmacion; revisa el servidor.")
        # Set the ID only after validating ownership; ambiguous failures use the
        # exact alias/run cleanup query rather than trusting another account ID.
        self.user_id = candidate

    def confirm(self, code):
        if not re.fullmatch(r"(?:[0-9]{6}|[0-9]{8})", code):
            raise RuntimeError("Introduce el codigo completo recibido, sin enviarlo al chat.")
        session = self.request("/auth/v1/verify", {"email": self.email, "token": code, "type": "email"})
        if session.get("user", {}).get("id") != self.user_id:
            raise RuntimeError("La confirmacion no corresponde a esta cuenta de prueba.")
        # Real session renewal after real inbox verification, not an admin confirmation bypass.
        renewed = self.request("/auth/v1/token?grant_type=refresh_token",
                               {"refresh_token": session["refresh_token"]})
        if renewed.get("user", {}).get("id") != self.user_id:
            raise RuntimeError("No se verifico la renovacion de la sesion.")
        self.request("/auth/v1/logout?scope=global", {}, token=renewed["access_token"])
        self.cleanup()
        evidence = {
            "project": self.admin.REF, "email_requested": True,
            "inbox_code_entered_by_user": True, "otp_verified_via_public_auth_api": True,
            "admin_generated_otp": False, "session_refresh_and_logout": True,
            "disposable_auth_account_deleted_verified": True,
            "client": "Private desktop HTTP helper; not Android UI or a physical phone.",
            "recorded_at": datetime.datetime.now().astimezone().isoformat(),
        }
        folder = ROOT / "app/build/verification/email-delivery"
        folder.mkdir(parents=True, exist_ok=True)
        target = folder / f"result-{self.run}.json"
        target.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")

    def cleanup(self):
        if not self.attempted:
            return
        # If signup committed but its response was lost, find ONLY this exact random alias/run.
        # The address is generated from a strictly validated Gmail name, never arbitrary SQL.
        if not self.user_id:
            rows = self.admin.management("POST", f"/v1/projects/{self.admin.REF}/database/query", {
                "query": "select id::text from auth.users where email = '" + self.email
                         + "' and raw_user_meta_data->>'pacefit_delivery_run' = '" + self.run + "';",
            })
            if len(rows) > 1:
                raise RuntimeError("Cuenta de prueba ambigua; no se borro ninguna cuenta.")
            if rows:
                self.user_id = rows[0]["id"]
        if self.user_id:
            if not re.fullmatch(r"[0-9a-f-]{36}", self.user_id):
                raise RuntimeError("Identificador de prueba invalido; no se borro.")
            current = self.request(f"/auth/v1/admin/users/{self.user_id}", method="GET", admin=True)
            if current:
                if (current.get("email", "").casefold() != self.email.casefold()
                        or current.get("user_metadata", {}).get("pacefit_delivery_run") != self.run):
                    raise RuntimeError("La cuenta no pertenece a esta prueba; no se modifico.")
                self.request(f"/auth/v1/admin/users/{self.user_id}", method="DELETE", admin=True)
                if self.request(f"/auth/v1/admin/users/{self.user_id}", method="GET", admin=True) is not None:
                    raise RuntimeError("Falta comprobar la eliminacion de la cuenta de prueba.")
        self.attempted = False
        self.password = None


def gui(check_ui=False):
    import tkinter as tk
    from tkinter import ttk

    root = tk.Tk()
    root.title("PaceFit - Comprobar llegada real del correo")
    root.geometry("730x530")
    root.minsize(660, 500)
    if check_ui:
        root.withdraw()
    panel = ttk.Frame(root, padding=20)
    panel.pack(fill="both", expand=True)
    ttk.Label(panel, text="Confirmacion real de Gmail", font=("Segoe UI", 15, "bold")).pack(anchor="w")
    ttk.Label(panel, wraplength=680, text=(
        "Se enviara un correo a un alias nuevo de TU Gmail configurado. No se lee tu buzon "
        "ni se pide tu contrasena. Revisa Recibidos/Spam/Todos y pega aqui el codigo. Solo se borrara "
        "la cuenta temporal creada por esta prueba, nunca tu cuenta Google ni cuentas existentes."
    )).pack(anchor="w", pady=12)
    recipient = tk.StringVar(value="Preparando destino privado...")
    ttk.Label(panel, textvariable=recipient, wraplength=680).pack(anchor="w")
    consent = tk.BooleanVar(value=False)
    status = tk.StringVar(value="No se ha enviado correo.")
    otp = tk.StringVar()
    agree = ttk.Checkbutton(panel, text="Autorizo este unico envio y el borrado de su cuenta de prueba.",
                            variable=consent)
    agree.pack(anchor="w", pady=10)
    send = ttk.Button(panel, text="Enviar correo de prueba", state="disabled")
    send.pack(anchor="w")
    ttk.Button(panel, text="Buscar PaceFit en Gmail (incluye Spam)", command=lambda: webbrowser.open(
        "https://mail.google.com/mail/u/0/#search/" +
        urllib.parse.quote('in:anywhere "PaceFit AI" newer_than:1d', safe="")
    )).pack(anchor="w", pady=(5, 0))
    ttk.Label(panel, text="Codigo recibido (solo aqui; nunca en el chat):").pack(anchor="w", pady=(16, 3))
    code = ttk.Entry(panel, textvariable=otp, show="*", width=24, state="disabled")
    code.pack(anchor="w")
    verify = ttk.Button(panel, text="Confirmar y limpiar cuenta temporal", state="disabled")
    verify.pack(anchor="w", pady=8)
    ttk.Label(panel, textvariable=status, wraplength=680).pack(anchor="w", pady=6)
    state = {"busy": False, "prepared": False, "sent": False, "done": False, "session": None}
    results = queue.Queue()

    def controls(*_):
        idle = not state["busy"] and not state["done"]
        send.configure(state="normal" if idle and state["prepared"] and consent.get()
                       and not state["session"].attempted else "disabled")
        verify.configure(state="normal" if idle and state["sent"]
                         and re.fullmatch(r"(?:[0-9]{6}|[0-9]{8})", otp.get()) else "disabled")

    def work(action, function):
        state["busy"] = True
        controls()
        status.set("Procesando por HTTPS. No cierres esta ventana...")

        def run():
            try:
                function()
                results.put((action, None))
            except Exception as error:
                # All expected messages are controlled above; never show raw provider replies.
                message = str(error) if type(error) is RuntimeError else "No se completo; revisa la conexion."
                results.put((action, message))

        threading.Thread(target=run, daemon=True).start()
        root.after(150, receive)

    def receive():
        try:
            action, error = results.get_nowait()
        except queue.Empty:
            root.after(150, receive)
            return
        state["busy"] = False
        otp.set("")
        if error:
            status.set(error + " Puedes cerrar para limpiar solo esta prueba.")
        elif action == "prepare":
            state["prepared"] = True
            recipient.set("Destino: " + state["session"].email)
            status.set("Verifica que es tu Gmail. Marca la autorizacion para enviar.")
        elif action == "send":
            state["sent"] = True
            code.configure(state="normal")
            code.focus_set()
            status.set("Solicitud aceptada; busca en el Gmail indicado, incluido Spam/Todos. "
                       "Aun NO se afirma entrega.")
        elif action == "verify":
            state["done"] = True
            code.configure(state="disabled")
            status.set("APROBADO: correo confirmado, sesion renovada/cerrada y cuenta temporal borrada. Puedes cerrar.")
        elif action == "close":
            root.destroy()
            return
        controls()

    def close():
        if state["busy"]:
            status.set("Espera a terminar la operacion; no se interrumpe una limpieza.")
        elif state["session"] is not None and state["session"].attempted:
            work("close", state["session"].cleanup)
        else:
            otp.set("")
            root.destroy()

    def prepare():
        def initialize():
            state["session"] = DeliveryCheck()
            state["session"].prepare()
        work("prepare", initialize)

    consent.trace_add("write", controls)
    otp.trace_add("write", controls)
    send.configure(command=lambda: work("send", state["session"].send))
    # Capture the code at click time, not when the form is created.
    def confirm_clicked():
        value = otp.get()
        work("verify", lambda: state["session"].confirm(value))
    verify.configure(command=confirm_clicked)
    root.protocol("WM_DELETE_WINDOW", close)
    if check_ui:
        assert code.cget("show") == "*" and str(send.cget("state")) == "disabled"
        assert str(verify.cget("state")) == "disabled"
        root.destroy()
        print("Private email form controls OK; no clipboard, credentials, email or network used.")
    else:
        root.after(0, prepare)
        root.mainloop()


if __name__ == "__main__":
    try:
        if sys.argv[1:] not in ([], ["--check-ui"]):
            raise RuntimeError("Usage: python scripts/check-email-delivery.py [--check-ui]")
        gui(check_ui=bool(sys.argv[1:]))
    except Exception:
        print("Email check could not open; no credentials were printed.", file=sys.stderr)
        sys.exit(1)
