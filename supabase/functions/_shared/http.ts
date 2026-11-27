import { createClient } from "npm:@supabase/supabase-js@2.49.8";

export const headers = { "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS", "Content-Type": "application/json" };
export const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers });
export class ApiError extends Error {
  constructor(public status: number, message: string, public code?: string) { super(message); }
}
export function endpoint(handler: (req: Request) => Promise<Response>) {
  return async (req: Request) => {
    if (req.method === "OPTIONS") return new Response("", { headers });
    if (req.method !== "POST") return json({ ok: false, error: "Método no permitido" }, 405);
    try { return await handler(req); }
    catch (e) {
      // Never log JWTs, emails, photos, coordinates or request bodies.
      return json({ ok: false, error: e instanceof ApiError ? e.message : "No se pudo completar la operación",
        ...(e instanceof ApiError && e.code && /^[A-Z][A-Z0-9_]{0,63}$/.test(e.code) ? { code: e.code } : {}) },
        e instanceof ApiError ? e.status : 500);
    }
  };
}
export async function authenticate(req: Request, allowDeleting = false) {
  const token = req.headers.get("Authorization")?.replace(/^Bearer /i, "");
  if (!token) throw new ApiError(401, "Inicia sesión");
  const admin = adminClient();
  // Validates with Auth, not a locally decoded/unverified JWT.
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) throw new ApiError(401, "Sesión vencida");
  const profile = await admin.from("social_profiles").select("*").eq("user_id", data.user.id).maybeSingle();
  if (profile.error) throw new ApiError(503, "Cuenta no disponible");
  if (!allowDeleting && (!profile.data || profile.data.deletion_requested_at))
    throw new ApiError(403, "Acepta el perfil público o completa la eliminación pendiente");
  return { admin, user: data.user };
}
export function adminClient() {
  return createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false, autoRefreshToken: false } });
}
export const uuid = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
