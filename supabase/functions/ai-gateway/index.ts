import { endpoint, authenticate, json, ApiError } from "../_shared/http.ts";
import { stripJpeg } from "../_shared/jpeg.ts";
import { classifyPhoto, failureMessages, GeminiFailure } from "../_shared/gemini.ts";

Deno.serve(endpoint(async req => {
  const { admin, user } = await authenticate(req);
  const key = Deno.env.get("GEMINI_API_KEY"), model = Deno.env.get("GEMINI_MODEL");
  if (!key || !model) throw new ApiError(503, failureMessages.GEMINI_NOT_CONFIGURED, "GEMINI_NOT_CONFIGURED");
  if (Number(req.headers.get("content-length") ?? 0) > 2900000) throw new ApiError(413, "Foto demasiado grande");
  const { task, payload } = await req.json();
  if (task !== "photo" || !payload || !["PLOGGING", "REPORT"].includes(payload.kind) ||
      typeof payload.jpegBase64 !== "string" || payload.jpegBase64.length > 2800000 ||
      Object.keys(payload).some(k => !["kind", "jpegBase64"].includes(k)))
    throw new ApiError(400, "Solo clasificación de fotos; no envíes identidad, ubicación ni salud");
  let bytes: Uint8Array;
  try { bytes = stripJpeg(Uint8Array.from(atob(payload.jpegBase64), c => c.charCodeAt(0))); }
  catch { throw new ApiError(400, "JPEG no válido"); }
  const quota = await admin.rpc("reserve_ai_call", { p_user: user.id, p_task: task });
  if (quota.error) throw new ApiError(503, failureMessages.AI_QUOTA_UNAVAILABLE, "AI_QUOTA_UNAVAILABLE");
  if (!quota.data) throw new ApiError(429, failureMessages.AI_USER_QUOTA_EXHAUSTED, "AI_USER_QUOTA_EXHAUSTED");
  let base64 = "";
  for (let i = 0; i < bytes.length; i += 8192) base64 += String.fromCharCode(...bytes.subarray(i, i + 8192));
  try {
    return json({ ok: true, data: await classifyPhoto(key, model, payload.kind, btoa(base64)) });
  } catch (error) {
    if (!(error instanceof GeminiFailure)) throw error;
    console.warn(JSON.stringify(error.diagnostic)); // Fixed identifiers only; never image/text/key/JWT.
    throw new ApiError(error.status, error.message, error.code);
  }
}));
