import { endpoint, authenticate, adminClient, json, ApiError } from "../_shared/http.ts";
Deno.serve(endpoint(async req => {
  const { confirmation, requestId } = await req.json();
  if (confirmation !== "ELIMINAR" || !/^[0-9a-f]{64}$/.test(requestId ?? ""))
    throw new ApiError(400, "Confirma la eliminación e incluye el comprobante de reintento");
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(requestId))))
    .map(b => b.toString(16).padStart(2, "0")).join("");
  const admin = adminClient();
  const completed = await admin.rpc("deletion_completed", { p_hash: hash });
  if (completed.error) throw new ApiError(503, "No se pudo consultar el comprobante");
  if (completed.data === true) return json({ ok: true }); // Also works after JWT/user was deleted.
  const { user } = await authenticate(req, true);
  const marked = await admin.rpc("begin_account_deletion", { p_user: user.id, p_hash: hash });
  if (marked.error) throw new ApiError(503, "No se pudo bloquear la cuenta para borrado");
  // Always first page: every successful batch disappears. Includes orphan uploads, not only referenced rows.
  while (true) {
    const list = await admin.storage.from("city-photos").list(user.id, { limit: 100 });
    if (list.error) throw new ApiError(503, "No se pudieron consultar las fotos; reintenta");
    if (!list.data?.length) break;
    const deleted = await admin.storage.from("city-photos").remove(list.data.map(f => `${user.id}/${f.name}`));
    if (deleted.error) throw new ApiError(503, "No se pudieron borrar todas las fotos; reintenta");
  }
  const deleted = await admin.auth.admin.deleteUser(user.id);
  if (deleted.error) throw new ApiError(503, "No se pudo terminar el borrado de la cuenta; reintenta");
  // Auth FK cascades remove profile, city rows, ai_usage and quota. Backups: documented <=30-day policy.
  return json({ ok: true });
}));
