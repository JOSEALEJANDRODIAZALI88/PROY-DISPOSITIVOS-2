"""Private five-photo acceptance helper. No inference without explicit user consent."""
import base64
from datetime import datetime, timezone
import json
from pathlib import Path
import queue
import subprocess
import sys
import threading
import uuid

from acceptance_common import AiGatewayError, Cloud, DisposableAccount, ROOT

LABELS = {"PLOGGING": ("LITTLE", "MEDIUM", "MUCH"),
          "REPORT": ("POTHOLE", "WASTE", "LIGHT", "OTHER")}


def prepare_photo(path):
    result = subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", str(ROOT / "scripts/prepare-private-photo.ps1")],
        input=json.dumps({"path": str(path)}), text=True, encoding="utf-8",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45)
    if result.returncode:
        raise RuntimeError("No se pudo preparar una foto: usa JPG/PNG de hasta 20 MB y 40 megapíxeles.")
    try:
        data = base64.b64decode(result.stdout.strip(), validate=True)
    except Exception:
        raise RuntimeError("No se obtuvo un JPEG privado utilizable.") from None
    if not data.startswith(b"\xff\xd8") or len(data) > 2097152:
        raise RuntimeError("JPEG preparado fuera de límites.")
    return data


def validate_suggestion(value, kind):
    if (not isinstance(value, dict) or kind not in LABELS or value.get("label") not in LABELS[kind]
            or not isinstance(value.get("unsafe"), bool)):
        raise RuntimeError("La IA no devolvió una clasificación utilizable; no se inventa un resultado.")
    return {"label": value["label"], "unsafe": value["unsafe"],
            "reason": str(value.get("reason", ""))[:300]}


def classify_five(photos, kinds, cloud=None):
    if len(photos) != 5 or len(kinds) != 5 or any(kind not in LABELS for kind in kinds):
        raise RuntimeError("Selecciona exactamente cinco fotos y categorías válidas.")
    cloud = cloud or Cloud()
    names = {row["name"] for row in cloud.admin.management("GET",
        f"/v1/projects/{cloud.admin.REF}/secrets")}
    if not {"GEMINI_API_KEY", "GEMINI_MODEL"}.issubset(names):
        raise RuntimeError("Falta conectar Gemini en el formulario privado. No se envió ninguna foto.")
    # All five conversions must succeed before creating the account or requesting inference.
    prepared = [prepare_photo(path) for path in photos]
    results = []
    with DisposableAccount(cloud) as account:
        for data, kind in zip(prepared, kinds):
            reply = cloud.request("/functions/v1/ai-gateway", "POST", {
                "task": "photo", "payload": {"kind": kind, "jpegBase64": base64.b64encode(data).decode("ascii")}
            }, token=account.token)
            if not isinstance(reply, dict) or reply.get("ok") is not True:
                raise RuntimeError("Gemini no produjo una respuesta real; clasificación manual disponible.")
            results.append(validate_suggestion(reply.get("data"), kind))
    return results


def receipt(kinds, results, reviewed_labels):
    if len(kinds) != 5 or len(results) != 5 or len(reviewed_labels) != 5:
        raise RuntimeError("La prueba exige cinco revisiones completas.")
    for kind, result, reviewed in zip(kinds, results, reviewed_labels):
        validate_suggestion(result, kind)
        if reviewed not in LABELS[kind] or result["unsafe"]:
            raise RuntimeError("Foto insegura o revisión incompleta: no se acredita aceptación.")
    return {
        "project": "ymfypllkcrqwfwihqzpf", "real_gateway_inferences": 5,
        "photos_selected_and_consented_by_user": True,
        "results": [{"kind": kind, "suggested_label": result["label"], "reviewed_label": reviewed,
                    "unsafe": result["unsafe"]} for kind, result, reviewed in zip(kinds, results, reviewed_labels)],
        "manual_corrections": sum(result["label"] != reviewed for result, reviewed in zip(results, reviewed_labels)),
        "source_metadata_reencoded_before_upload": True, "photos_published": False,
        "disposable_account_cleanup_verified": True,
        "android_ui_acceptance_tested": False, "client": "Private desktop five-photo helper, not Android UI.",
        "recorded_at": datetime.now(timezone.utc).isoformat(),
    }


def failure_receipt(error):
    if not isinstance(error, AiGatewayError):
        return None
    return {
        "project": "ymfypllkcrqwfwihqzpf", "acceptance_approved": False,
        "gateway_code": error.code, "http_status": error.http_status,
        "automatic_retries": False, "photos_published": False,
        "photo_content_paths_identity_or_keys_saved": False,
        "recorded_at": datetime.now(timezone.utc).isoformat(),
    }


def main(check_ui=False):
    import tkinter as tk
    from tkinter import filedialog, ttk
    root = tk.Tk()
    root.title("PaceFit - Comprobar Gemini con cinco fotos privadas")
    root.geometry("940x660")
    root.minsize(850, 600)
    frame = ttk.Frame(root, padding=18)
    frame.pack(fill="both", expand=True)
    ttk.Label(frame, text="Gemini real: cinco fotos consentidas", font=("Segoe UI", 15)).pack(anchor="w")
    ttk.Label(frame, text="No se publican. Se envían al gateway y a Google, sin EXIF/GPS de origen.\n"
        "Elige fotos propias sin rostros, matrículas, salud ni datos privados. Máximo cinco llamadas por intento.\n"
        "Requiere clave/modelo con cuota gratuita ya conectados; no activa pagos ni inventa respuestas.",
        wraplength=880).pack(anchor="w", pady=8)
    paths, results = [], []
    busy, attempted = False, False
    events = queue.Queue()
    consent = tk.BooleanVar(value=False)
    status = tk.StringVar(value="Selecciona cinco JPG/PNG. El código fuente y el APK no contienen la clave Gemini.")
    kinds, corrected, reviews, row_labels, kind_boxes = [], [], [], [], []
    selected_kinds = []
    rows = ttk.Frame(frame)
    rows.pack(fill="x", pady=8)
    for index in range(5):
        row = ttk.Frame(rows)
        row.pack(fill="x", pady=6)
        label = ttk.Label(row, text=f"Foto {index + 1}: sin seleccionar", width=40)
        label.pack(side="left")
        row_labels.append(label)
        kind = tk.StringVar(value="PLOGGING" if index < 3 else "REPORT")
        kinds.append(kind)
        kind_box = ttk.Combobox(row, textvariable=kind, values=tuple(LABELS), state="readonly", width=11)
        kind_box.pack(side="left", padx=5)
        kind_boxes.append(kind_box)
        choice = tk.StringVar(value="")
        corrected.append(choice)
        box = ttk.Combobox(row, textvariable=choice, values=(), state="disabled", width=12)
        box.pack(side="left", padx=5)
        checked = tk.BooleanVar(value=False)
        reviews.append((checked, box))
        ttk.Checkbutton(row, text="Revisada", variable=checked).pack(side="left", padx=5)
    pick = ttk.Button(frame, text="Seleccionar cinco fotos")
    pick.pack(anchor="w", pady=5)
    ttk.Checkbutton(frame, variable=consent, text="Son fotos autorizadas. Acepto enviarlas a Gemini con cuota gratuita, sin activar facturación.").pack(anchor="w", pady=8)
    run = ttk.Button(frame, text="Probar cinco inferencias reales", state="disabled")
    run.pack(anchor="w")
    ttk.Label(frame, textvariable=status, wraplength=880).pack(anchor="w", pady=12)
    record = ttk.Button(frame, text="Registrar revisión sin guardar fotos ni publicarlas", state="disabled")
    record.pack(anchor="w", pady=6)
    ttk.Button(frame, text="Cerrar", command=lambda: close()).pack(anchor="w")

    def enable(*_):
        run.configure(state="normal" if not busy and not attempted and consent.get() and len(paths) == 5 else "disabled")
        record.configure(state="normal" if not busy and len(results) == 5 and consent.get()
            and all(checked.get() and choice.get() in LABELS[kind.get()] and not result["unsafe"]
                    for (checked, _), choice, kind, result in zip(reviews, corrected, kinds, results)) else "disabled")

    def choose():
        if busy or attempted:
            return
        chosen = filedialog.askopenfilenames(parent=root, filetypes=[("Fotos", "*.jpg *.jpeg *.png")])
        if len(chosen) != 5 or len(set(chosen)) != 5:
            status.set("Selecciona exactamente cinco archivos distintos; no se ha enviado nada.")
            return
        paths[:] = [Path(path) for path in chosen]
        for index, path in enumerate(paths):
            row_labels[index].configure(text=f"{index + 1}. {path.name[:30]}")
        enable()

    def work():
        try:
            events.put(("ok", classify_five(tuple(paths), tuple(selected_kinds))))
        except RuntimeError as error:
            diagnostic = failure_receipt(error)
            if diagnostic:
                try:
                    folder = ROOT / "app/build/verification/gemini"
                    folder.mkdir(parents=True, exist_ok=True)
                    (folder / ("failure-" + uuid.uuid4().hex + ".json")).write_text(
                        json.dumps(diagnostic, indent=2), encoding="utf-8")
                except OSError:
                    pass  # Failure to save a diagnostic must not hide the original safe error.
            events.put(("error", str(error)))  # Only locally controlled/sanitized exceptions.
        except Exception:
            events.put(("error", "No se completó la prueba; no se mostraron claves ni respuestas privadas."))

    def start():
        nonlocal busy, attempted
        if busy or attempted or not consent.get() or len(paths) != 5:
            return
        busy, attempted = True, True
        selected_kinds[:] = [kind.get() for kind in kinds]
        for box in kind_boxes:
            box.configure(state="disabled")
        pick.configure(state="disabled")
        status.set("Preparando fotos y comprobando el servidor. Espera; no cierres durante la limpieza.")
        enable()
        threading.Thread(target=work, daemon=True).start()
        root.after(150, receive)

    def receive():
        nonlocal busy
        try:
            event, value = events.get_nowait()
        except queue.Empty:
            root.after(150, receive)
            return
        busy = False
        if event == "error":
            status.set(value + " Cierra antes de otro intento; no hay reintentos automáticos.")
        else:
            results[:] = value
            for index, (kind, choice, (checked, box), result) in enumerate(zip(kinds, corrected, reviews, results)):
                box.configure(values=LABELS[kind.get()], state="readonly")
                choice.set(result["label"])
                row_labels[index].configure(text=f"{index + 1}. Sugerencia: {result['label']}" +
                    (" — FOTO INSEGURA" if result["unsafe"] else ""))
            status.set("Cinco respuestas reales y cuenta temporal eliminada. Revisa cada foto y corrige si corresponde.\n"
                       "Una foto marcada insegura impide acreditar la aceptación; nunca se publica.")
        enable()

    def save_review():
        if busy or len(results) != 5 or not all(checked.get() for checked, _ in reviews):
            return
        try:
            result = receipt([kind.get() for kind in kinds], results, [value.get() for value in corrected])
            folder = ROOT / "app/build/verification/gemini"
            folder.mkdir(parents=True, exist_ok=True)
            (folder / ("result-" + uuid.uuid4().hex + ".json")).write_text(json.dumps(result, indent=2), encoding="utf-8")
            record.configure(state="disabled")
            status.set("APROBADO en asistente de escritorio. Evidencia sin fotos, rutas, nombres ni claves. Cierra y avisa.")
        except Exception:
            status.set("No se acreditó la revisión completa. No se guardaron fotos ni claves.")

    def close():
        if busy:
            status.set("Espera la respuesta y limpieza de la cuenta temporal antes de cerrar.")
            return
        root.destroy()

    pick.configure(command=choose)
    run.configure(command=start)
    record.configure(command=save_review)
    root.protocol("WM_DELETE_WINDOW", close)
    consent.trace_add("write", enable)
    for kind, choice, (checked, _) in zip(kinds, corrected, reviews):
        kind.trace_add("write", enable)
        choice.trace_add("write", enable)
        checked.trace_add("write", enable)
    if check_ui:
        root.withdraw()
        assert str(run["state"]) == "disabled" and str(record["state"]) == "disabled"
        root.destroy()
        print("Gemini review controls OK; no photos, credentials, inference or network used.")
    else:
        root.mainloop()


if __name__ == "__main__":
    try:
        if sys.argv[1:] not in ([], ["--check-ui"]):
            raise RuntimeError("Usage: python scripts/check-gemini.py [--check-ui]")
        main(sys.argv[1:] == ["--check-ui"])
    except Exception:
        print("Private Gemini check stopped; no keys, photo contents or paths printed.", file=sys.stderr)
        sys.exit(1)
