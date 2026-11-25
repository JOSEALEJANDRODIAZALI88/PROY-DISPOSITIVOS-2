import { endpoint, authenticate, json, ApiError, uuid } from "../_shared/http.ts";
import { stripJpeg } from "../_shared/jpeg.ts";

Deno.serve(endpoint(async req => {
  const { admin, user } = await authenticate(req);
  if (Number(req.headers.get("content-length") ?? 0) > 2200000) throw new ApiError(413, "Foto demasiado grande");
  const form = await req.formData();
  const id = String(form.get("id")), kind = String(form.get("kind")), label = String(form.get("label"));
  const description = String(form.get("description") ?? "").trim();
  const latText = String(form.get("latitude") ?? ""), lonText = String(form.get("longitude") ?? "");
  const latitude = Number(latText), longitude = Number(lonText);
  const file = form.get("photo");
  const labels = kind === "PLOGGING" ? ["LITTLE", "MEDIUM", "MUCH"] : kind === "REPORT" ? ["POTHOLE", "WASTE", "LIGHT", "OTHER"] : [];
  if (!uuid(id) || !labels.includes(label) || form.get("consent") !== "true" || description.length > 500 ||
      !latText || !lonText || !Number.isFinite(latitude) || !Number.isFinite(longitude) ||
      Math.abs(latitude) > 90 || Math.abs(longitude) > 180 || !(file instanceof File) || file.size > 2097152)
    throw new ApiError(400, "Confirma foto, clasificación, ubicación y consentimiento");
  const existing = await admin.from("city_entries").select("*").eq("id", id).maybeSingle();
  if (existing.error) throw new ApiError(503, "No se pudo consultar la publicación");
  if (existing.data) {
    if (existing.data.owner_id !== user.id) throw new ApiError(409, "Identificador no disponible");
    return json({ ok: true, data: existing.data }); // idempotent retry, never award twice
  }
  let jpeg: Uint8Array;
  try { jpeg = stripJpeg(new Uint8Array(await file.arrayBuffer())); }
  catch { throw new ApiError(400, "Se requiere una foto JPEG válida de hasta 1600 píxeles"); }
  const path = `${user.id}/${id}.jpg`;
  const upload = await admin.storage.from("city-photos").upload(path, jpeg, { contentType: "image/jpeg", upsert: false });
  // A retry must not overwrite a photo already committed by a concurrent request.
  if (upload.error && String(upload.error.statusCode) !== "409") throw new ApiError(503, "No se pudo guardar la foto");
  const insert = await admin.from("city_entries").insert({ id, owner_id: user.id, kind, label, description,
    latitude, longitude, photo_path: path }).select("*").single();
  if (insert.error) {
    // Keep an upload on ambiguous/concurrent commit; a retry uses this same id/path.
    throw new ApiError(503, "No se pudo confirmar la publicación; puedes reintentar sin duplicarla");
  }
  return json({ ok: true, data: insert.data });
}));
