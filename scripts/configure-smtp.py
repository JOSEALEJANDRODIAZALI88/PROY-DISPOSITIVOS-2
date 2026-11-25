"""Interactive SMTP setup: verified TLS, no password files, authorized Free project only."""
import getpass
import importlib.util
from pathlib import Path
import queue
import re
import smtplib
import ssl
import sys
import threading
import webbrowser


def cloud_admin():
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("pacefit_cloud_admin",
                                                Path(__file__).with_name("cloud-admin.py"))
    admin = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(admin)
    return admin


def configure_smtp(host, port_text, sender, username, password, admin=None):
    if (not re.fullmatch(r"[A-Za-z0-9.-]+\.[A-Za-z]{2,}", host)
            or port_text not in ("465", "587")
            or not re.fullmatch(r"[^@\s<>]+@[^@\s<>]+\.[^@\s<>]+", sender)
            or not username or len(username) > 500 or not password):
        raise ValueError("Invalid SMTP configuration; no server settings changed.")
    admin = admin or cloud_admin()
    admin.assert_target()
    current = admin.management("GET", f"/v1/projects/{admin.REF}/config/auth")
    if current.get("smtp_host"):
        return "existing"
    context = ssl.create_default_context()
    port = int(port_text)
    if port == 465:
        connection = smtplib.SMTP_SSL(host, port, timeout=30, context=context)
    else:
        connection = smtplib.SMTP(host, port, timeout=30)
    with connection as smtp:
        smtp.ehlo()
        if port == 587:
            smtp.starttls(context=context)
            smtp.ehlo()
        smtp.login(username, password)
    # Authentication was real and certificate-verified; no email is sent by this helper.
    admin.management("PATCH", f"/v1/projects/{admin.REF}/config/auth", {
        "smtp_host": host, "smtp_port": str(port), "smtp_user": username,
        "smtp_pass": password, "smtp_admin_email": sender, "smtp_sender_name": "PaceFit AI",
        "mailer_autoconfirm": False, "password_min_length": 10,
    })
    configured = admin.management("GET", f"/v1/projects/{admin.REF}/config/auth")
    if configured.get("smtp_host") != host or configured.get("mailer_autoconfirm", True):
        raise RuntimeError("Server configuration requires review; confirmation must remain enabled.")
    return "configured"


def interactive():
    if not sys.stdin.isatty():
        raise RuntimeError("Open this helper in a private interactive terminal, not agent output.")
    print("PaceFit - Configuracion privada de correo")
    print("Usa un servicio SMTP gratuito ya autorizado. Este asistente no crea cuentas ni activa pagos.")
    print("Si no tienes SMTP, cancela con Ctrl+C. Nunca pegues estos datos en el chat.")
    if input("Comprueba tu plan gratuito y escribe GRATIS: ").strip() != "GRATIS":
        return
    host = input("Servidor SMTP (el indicado por tu proveedor): ").strip()
    port_text = input("Puerto TLS: 587 (STARTTLS) o 465 (TLS) [587]: ").strip() or "587"
    sender = input("Correo remitente autorizado por tu proveedor: ").strip()
    username = input("Usuario SMTP: ").strip()
    print("Con Gmail usa una contrasena de aplicacion, NO tu contrasena normal de Google.")
    password = getpass.getpass("Contrasena o clave SMTP (oculta): ")
    if not password:
        raise RuntimeError("Empty password; no settings changed.")
    try:
        result = configure_smtp(host, port_text, sender, username, password)
        if result == "existing":
            print("SMTP ya configurado. No se sobrescribieron datos privados.")
        else:
            print("SMTP guardado en Supabase con confirmacion activa. No se guardo la clave en archivos.")
            print("Falta comprobar entrega real, verificacion y retorno desde el correo.")
    finally:
        password = None


def gmail_gui(check_ui=False):
    import tkinter as tk
    from tkinter import ttk
    root = tk.Tk()
    root.title("PaceFit - Gmail privado para la beta")
    root.geometry("690x440")
    root.resizable(False, False)
    panel = ttk.Frame(root, padding=20)
    panel.pack(fill="both", expand=True)
    panel.columnconfigure(0, weight=1)
    ttk.Label(panel, text="Conectar Gmail sin mostrar contrasenas", font=("Segoe UI", 14, "bold")).grid(
        row=0, column=0, sticky="w", pady=(0, 10))
    ttk.Label(panel, text="1. Activa la verificacion en dos pasos en Google.\n"
              "2. Crea una contrasena de aplicacion llamada PaceFit.\n"
              "3. Introducela aqui. NO uses la contrasena normal ni la envies al chat.",
              wraplength=640).grid(row=1, column=0, sticky="w")
    ttk.Button(panel, text="Abrir contrasenas de aplicacion de Google",
               command=lambda: webbrowser.open("https://myaccount.google.com/apppasswords")).grid(
                   row=2, column=0, sticky="w", pady=10)
    ttk.Label(panel, text="Tu correo Gmail:").grid(row=3, column=0, sticky="w")
    email = tk.StringVar()
    email_field = ttk.Entry(panel, textvariable=email)
    email_field.grid(row=4, column=0, sticky="ew", pady=(3, 10))
    ttk.Label(panel, text="Contrasena de aplicacion de 16 caracteres (Ctrl+V; veras puntos):").grid(
        row=5, column=0, sticky="w")
    password = tk.StringVar()
    password_field = ttk.Entry(panel, textvariable=password, show="*")
    password_field.grid(row=6, column=0, sticky="ew", pady=(3, 10))
    consent = tk.BooleanVar()
    consent_field = ttk.Checkbutton(panel, variable=consent,
        text="Autorizo usar mi Gmail existente para esta beta, sin contratar planes de pago.")
    consent_field.grid(row=7, column=0, sticky="w")
    notice = tk.StringVar(value="Solo se guarda en Supabase, no en archivos, Android ni Git. No se enviara correo aun.")
    ttk.Label(panel, textvariable=notice, wraplength=640).grid(row=9, column=0, sticky="w", pady=12)
    busy = False
    completed = False
    results = queue.Queue()

    def enable(*_):
        usable = (consent.get() and bool(re.fullmatch(r"[^@\s]+@(gmail|googlemail)\.com",
                  email.get().strip(), re.IGNORECASE))
                  and bool(re.fullmatch(r"[A-Za-z0-9]{16}", re.sub(r"\s", "", password.get()))))
        connect.configure(state="normal" if usable and not busy and not completed else "disabled")

    def receive_result():
        nonlocal busy, completed
        try:
            message, success = results.get_nowait()
        except queue.Empty:
            root.after(150, receive_result)
            return
        busy = False
        completed = success
        notice.set(message)
        password.set("")
        email_field.configure(state="disabled" if success else "normal")
        password_field.configure(state="disabled" if success else "normal")
        consent_field.configure(state="disabled" if success else "normal")
        enable()

    def submit():
        nonlocal busy
        if busy or completed:
            return
        address = email.get().strip()
        secret = re.sub(r"\s", "", password.get())
        if (not consent.get() or not re.fullmatch(r"[^@\s]+@(gmail|googlemail)\.com", address, re.IGNORECASE)
                or not re.fullmatch(r"[A-Za-z0-9]{16}", secret)):
            notice.set("Comprueba Gmail, la contrasena de aplicacion y tu autorizacion.")
            return
        busy = True
        connect.configure(state="disabled")
        email_field.configure(state="disabled")
        password_field.configure(state="disabled")
        consent_field.configure(state="disabled")
        notice.set("Comprobando TLS, acceso Gmail y configuracion Free. Espera sin cerrar...")

        def work():
            nonlocal secret
            try:
                result = configure_smtp("smtp.gmail.com", "587", address, address, secret)
                if result == "existing":
                    results.put(("Ya habia SMTP configurado. No se sobrescribio. Cierra y avisa en el chat.", True))
                else:
                    results.put(("Gmail configurado con confirmacion activa. Falta probar entrega real. "
                                 "Cierra y avisa LISTO en el chat.", True))
            except smtplib.SMTPAuthenticationError:
                results.put(("Google rechazo el acceso. Usa una contrasena de aplicacion nueva; "
                             "no la contrasena normal.", False))
            except Exception:
                results.put(("No se completo la conexion. Revisa red, Google o Supabase. "
                             "No envies contrasenas al chat.", False))
            finally:
                secret = None
        threading.Thread(target=work, daemon=True).start()
        root.after(150, receive_result)

    connect = ttk.Button(panel, text="Comprobar y conectar Gmail", command=submit, state="disabled")
    connect.grid(row=8, column=0, sticky="w", pady=(12, 0))
    for value in (email, password, consent):
        value.trace_add("write", enable)

    def close():
        if busy:
            notice.set("La operacion sigue en curso. Espera al resultado antes de cerrar.")
        else:
            password.set("")
            root.destroy()
    root.protocol("WM_DELETE_WINDOW", close)
    if check_ui:
        root.withdraw()
        assert password_field.cget("show") == "*"
        assert "disabled" in connect.state()
        root.destroy()
        print("Private Gmail form controls OK; no clipboard access, credential or network call.")
        return
    email_field.focus_set()
    root.mainloop()


if __name__ == "__main__":
    try:
        if sys.argv[1:] in (["--gmail-gui"], ["--gmail-gui", "--check-ui"]):
            gmail_gui(check_ui="--check-ui" in sys.argv)
        elif not sys.argv[1:]:
            interactive()
        else:
            raise ValueError("Use --gmail-gui or open the interactive SMTP helper.")
    except KeyboardInterrupt:
        print("\nCancelado. No se mostro ninguna clave.")
    except Exception as error:
        # SMTP/provider responses may contain sensitive account information.
        print(f"No se completo SMTP ({type(error).__name__}). No se mostraron credenciales.")
        sys.exit(1)
