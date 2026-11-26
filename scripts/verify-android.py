"""Run the opt-in cloud Android suite and bind its evidence to exact APK hashes.

Use only disposable emulators explicitly authorized for testing. No compilation,
Gradle uninstall, personal phone, credentials in arguments or secret output.
"""
import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(serial):
    if serial not in ("emulator-5554", "emulator-5556"):
        raise RuntimeError("Use only an explicitly authorized disposable emulator.")
    sdk = subprocess.check_output(
        ["adb", "-s", serial, "shell", "getprop", "ro.build.version.sdk"], text=True
    ).strip()
    if sdk not in ("26", "35"):
        raise RuntimeError("Only API 26/35 evidence is supported.")
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    test_apk = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    apk_hash, test_hash = sha256(apk), sha256(test_apk)
    folder = ROOT / f"app/build/verification/cloud-api{sdk}"
    metadata = folder / "artifact.json"
    if metadata.exists():
        previous = json.loads(metadata.read_text(encoding="utf-8"))
        old_hash = previous.get("apk_sha256", "")
        if old_hash != apk_hash:
            if not re.fullmatch(r"[0-9a-f]{64}", old_hash):
                raise RuntimeError("Existing evidence needs manual review; not overwritten.")
            archive = ROOT / f"app/build/verification/cloud-{old_hash[:12]}-api{sdk}"
            archive.mkdir(parents=True, exist_ok=True)
            for name in ("artifact.json", "instrumentation.txt"):
                source = folder / name
                target = archive / name
                if source.exists():
                    if target.exists() and source.read_bytes() != target.read_bytes():
                        raise RuntimeError("Conflicting historical evidence; not overwritten.")
                    if not target.exists():
                        shutil.copy2(source, target)

    subprocess.run(
        [sys.executable, str(ROOT / "scripts/cloud-admin.py"), "android-smoke", serial],
        cwd=ROOT, check=True,
    )
    if sha256(apk) != apk_hash or sha256(test_apk) != test_hash:
        raise RuntimeError("An APK changed during testing; do not attribute these results to it.")
    result = (folder / "instrumentation.txt").read_text(encoding="utf-8")
    if not re.search(r"OK \(24 tests\)", result) or "FAILURES" in result:
        raise RuntimeError("Expected 24 successful instrumented cases, including the cloud case.")
    unit = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    reports = list((ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in unit:
            unit[key] += int(suite.get(key, "0"))
    if unit != {"tests": 32, "failures": 0, "errors": 0, "skipped": 0}:
        raise RuntimeError("Unit evidence changed or is incomplete; inspect it separately.")
    lint = (ROOT / "app/build/reports/lint-results-debug.txt").read_text(encoding="utf-8")
    lint_result = re.search(r"(\d+) errors?, (\d+) warnings?", lint)
    if not lint_result:
        raise RuntimeError("Lint summary not found; evidence not certified.")
    evidence = {
        "api": int(sdk), "serial": serial, "project": "ymfypllkcrqwfwihqzpf",
        "version": "0.3.0-beta.1", "tests": 24, "failures": 0,
        "cloud_account_deleted_verified": True,
        "otp_wrong_rejected_and_valid_accepted": True, "email_delivery_tested": False,
        "apk_bytes": apk.stat().st_size, "apk_sha256": apk_hash,
        "test_apk_sha256": test_hash, "unit_tests": unit,
        "lint_errors": int(lint_result.group(1)), "lint_warnings": int(lint_result.group(2)),
        "build_invoked": False, "unit_and_lint_are_existing_saved_reports": True,
        "recorded_at": datetime.datetime.now().astimezone().isoformat(),
        "execution": "Manual am instrument via cloud-admin; no Gradle uninstall.",
    }
    temporary = folder / "artifact.json.tmp"
    temporary.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    temporary.replace(metadata)
    print(f"Evidence saved: API {sdk}, 24 cases, APK SHA-256 {apk_hash}.")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise RuntimeError("Usage: python scripts/verify-android.py <authorized-emulator-serial>")
        main(sys.argv[1])
    except Exception as error:
        print(f"Verification stopped: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
