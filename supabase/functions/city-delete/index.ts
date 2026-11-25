import { endpoint, authenticate, json, ApiError, uuid } from "../_shared/http.ts";
Deno.serve(endpoint(async req => {
  const { admin, user } = await authenticate(req);
  const { id } = await req.json();
  if (typeof id !== "string" || !uuid(id)) throw new ApiError(400, "Identificador inválido");
  const row = await admin.from("city_entries").select("owner_id,photo_path").eq("id", id).maybeSingle();
  if (row.error) throw new ApiError(503, "No se pudo consultar el registro");
  if (row.data && row.data.owner_id !== user.id) throw new ApiError(403, "Solo puedes borrar tus publicaciones");
  const path = `${user.id}/${id}.jpg`;
  const removed = await admin.storage.from("city-photos").remove([path]);
  if (removed.error) throw new ApiError(503, "No se pudo borrar la foto");
  const deleted = await admin.from("city_entries").delete().eq("id", id).eq("owner_id", user.id);
  if (deleted.error) throw new ApiError(503, "El borrado no terminó; reintenta");
  return json({ ok: true });
}));
