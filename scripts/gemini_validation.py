"""Private Google model-list validation. Never return keys or provider error text."""
import json
import re
import socket
import ssl
import sys
import urllib.error
import urllib.request

ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
KEY_PATTERN = re.compile(r"[A-Za-z0-9_.-]{20,512}")
MODEL_PATTERN = re.compile(r"models/gemini-(\d+)(?:\.(\d+))?-flash")
SAFE_REASONS = {
    "API_KEY_INVALID", "API_KEY_EXPIRED", "API_KEY_SERVICE_BLOCKED",
    "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED",
    "SERVICE_DISABLED", "CONSUMER_INVALID", "BILLING_DISABLED",
    "ACCESS_TOKEN_SCOPE_INSUFFICIENT", "IAM_PERMISSION_DENIED",
}
SAFE_STATUSES = {
    "INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED",
    "RESOURCE_EXHAUSTED", "FAILED_PRECONDITION", "UNAVAILABLE",
    "INTERNAL", "DEADLINE_EXCEEDED", "NOT_FOUND",
}


def failure(code, http_status=None, google_status=None):
    result = {"ok": False, "code": code}
    if isinstance(http_status, int) and 100 <= http_status <= 599:
        result["httpStatus"] = http_status
    if google_status in SAFE_STATUSES:
        result["googleStatus"] = google_status
    return result


def provider_failure(status, payload):
    """Only predefined identifiers leave this function, never free-form messages."""
    try:
        error = json.loads(payload).get("error", {})
        if not isinstance(error, dict):
            return failure("HTTP_ERROR", status)
        google_status = error.get("status")
        # Classify an explicit leak warning without exposing its original text.
        if "leaked" in str(error.get("message", "")).lower():
            return failure("KEY_REPORTED_LEAKED", status, google_status)
        details = error.get("details", [])
        if isinstance(details, list):
            for item in details:
                if isinstance(item, dict) and item.get("reason") in SAFE_REASONS:
                    return failure(item["reason"], status, google_status)
        return failure("HTTP_ERROR", status, google_status)
    except (ValueError, TypeError, AttributeError):
        return failure("HTTP_ERROR", status)


def inspect_models(key, opener=None):
    if not isinstance(key, str) or not KEY_PATTERN.fullmatch(key):
        return failure("INVALID_KEY_FORMAT")
    request = urllib.request.Request(ENDPOINT, headers={"x-goog-api-key": key})
    try:
        open_request = opener or urllib.request.urlopen
        with open_request(request, timeout=30, context=ssl.create_default_context()) as response:
            payload = response.read(2 * 1024 * 1024 + 1)
        if len(payload) > 2 * 1024 * 1024:
            return failure("INVALID_PROVIDER_RESPONSE", 200)
        data = json.loads(payload)
        rows = data.get("models") if isinstance(data, dict) else None
        if not isinstance(rows, list):
            return failure("INVALID_PROVIDER_RESPONSE", 200)
        models = set()
        for row in rows:
            if not isinstance(row, dict):
                continue
            name = row.get("name")
            methods = row.get("supportedGenerationMethods")
            if (isinstance(name, str) and MODEL_PATTERN.fullmatch(name)
                    and isinstance(methods, list) and "generateContent" in methods):
                models.add(name.removeprefix("models/"))
        models = sorted(models, key=lambda name: tuple(
            int(part or 0) for part in MODEL_PATTERN.fullmatch("models/" + name).groups()),
            reverse=True)[:20]
        if not models:
            return failure("NO_COMPATIBLE_FLASH_MODEL", 200)
        return {"ok": True, "httpStatus": 200, "models": models}
    except urllib.error.HTTPError as error:
        try:
            return provider_failure(error.code, error.read(65536))
        except Exception:
            return failure("HTTP_ERROR", error.code)
        finally:
            error.close()
    except (ssl.SSLError, socket.timeout, TimeoutError):
        return failure("NETWORK_TLS_OR_TIMEOUT")
    except urllib.error.URLError:
        return failure("NETWORK_TLS_OR_TIMEOUT")
    except (ValueError, TypeError, AttributeError):
        return failure("INVALID_PROVIDER_RESPONSE")
    except Exception:
        # Do not leak exception messages, request objects, headers or credentials.
        return failure("LOCAL_VALIDATION_ERROR")


def main():
    try:
        value = json.loads(sys.stdin.read(4097))
        result = (inspect_models(value["apiKey"]) if isinstance(value, dict)
                  and set(value) == {"apiKey"} else failure("INVALID_PRIVATE_INPUT"))
    except Exception:
        result = failure("INVALID_PRIVATE_INPUT")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
