// Real local Auth/PostgREST/Storage/Edge integration, no mocks, no cloud secrets.
// Requires `supabase start` and `supabase functions serve --env-file .env.example`.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { randomUUID, randomBytes } from "node:crypto";
import assert from "node:assert/strict";
const stack = JSON.parse(execFileSync(process.platform === "win32" ? "npx.cmd" : "npx",
  ["--yes", "supabase", "status", "-o", "json"], { encoding: "utf8", shell: process.platform === "win32",
    stdio: ["ignore", "pipe", "pipe"] }));
const base = stack.API_URL;
assert.match(base, /^http:\/\/(127\.0\.0\.1|localhost):/); // Never test/mutate a production server.
const anonymous = stack.ANON_KEY, admin = stack.SERVICE_ROLE_KEY;
let checks = 0;
const pass = message => { checks++; console.log(`ok ${checks} - ${message}`); };
async function call(path, { token = anonymous, method = "GET", body, headers = {} } = {}) {
  const multipart = body instanceof FormData;
  const response = await fetch(base + path, { method, headers: { apikey: anonymous,
    Authorization: `Bearer ${token}`, ...(!multipart && body !== undefined ? { "Content-Type": "application/json" } : {}), ...headers },
    body: body === undefined ? undefined : multipart ? body : JSON.stringify(body), signal: AbortSignal.timeout(55000) });
  const text = await response.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: response.status, data };
}
function success(result) { assert.ok(result.status >= 200 && result.status < 300, `HTTP ${result.status}`); return result.data; }
function denied(result) { assert.ok(result.status >= 400 && result.status < 500, `Expected denial; got ${result.status}`); }
const photo = Buffer.from(readFileSync(new URL("../supabase/tests/photo.base64", import.meta.url), "utf8").trim(), "base64");
const metadata = Buffer.from("Exif\0\0GPSLatitude=secret-test;CameraSerial=secret-test", "utf8");
const marker = Buffer.from([255, 225, (metadata.length + 2) >> 8, (metadata.length + 2) & 255]);
const source = Buffer.concat([photo.subarray(0, 2), marker, metadata, photo.subarray(2)]);
function form(id, kind = "PLOGGING", label = "MUCH", consent = "true") {
  const data = new FormData();
  for (const [key, value] of Object.entries({ id, kind, label, description: "Prueba automatizada", latitude: "-16.5", longitude: "-68.15", consent, points: "99999" })) data.set(key, value);
  data.set("photo", new Blob([source], { type: "image/jpeg" }), "photo.jpg");
  return data;
}
const users = [];
try {
  for (let i = 0; i < 3; i++) {
    const email = `pacefit-e2e-${randomUUID()}@example.test`;
    const auth = success(await call("/auth/v1/signup", { method: "POST", body: { email, password: randomBytes(24).toString("hex") } }));
    assert.ok(auth.access_token, "Local Auth must disable email confirmation ONLY for tests.");
    users.push({ id: auth.user.id, token: auth.access_token, refresh: auth.refresh_token });
    if (i < 2) success(await call("/rest/v1/rpc/accept_social_profile", { token: auth.access_token, method: "POST", body: { p_display_name: `Prueba ${i}` } }));
  }
  pass("two real authenticated users accept public profile");
  const [a, b] = users, id = randomUUID(), reportId = randomUUID();
  const quota = await Promise.all(Array.from({ length: 45 }, () =>
    call("/rest/v1/rpc/reserve_ai_call", { token: admin, method: "POST", body: { p_user: a.id, p_task: "photo" } }).then(success)));
  assert.equal(quota.filter(Boolean).length, 40);
  assert.equal(quota.filter(value => value === false).length, 5);
  const usage = success(await call(`/rest/v1/ai_usage?select=id&user_id=eq.${a.id}`, { token: admin }));
  assert.equal(usage.length, 40);
  pass("45 concurrent quota requests admit exactly 40; database audit agrees");
  denied(await call("/rest/v1/city_entries")); pass("anonymous cannot read map");
  denied(await call("/functions/v1/city-submit", { method: "POST", body: form(id) })); pass("anonymous cannot publish");
  denied(await call("/functions/v1/city-submit", { token: a.token, method: "POST", body: form(id, "PLOGGING", "MUCH", "false") })); pass("no publication without explicit consent");
  const entry = success(await call("/functions/v1/city-submit", { token: a.token, method: "POST", body: form(id) })).data;
  assert.equal(entry.points, 20); assert.equal(entry.owner_id, a.id); pass("server ignores forged 99999 points and assigns 20");
  const retry = success(await call("/functions/v1/city-submit", { token: a.token, method: "POST", body: form(id) })).data;
  assert.equal(retry.id, id); pass("retry is idempotent, same publication");
  denied(await call("/functions/v1/city-submit", { token: b.token, method: "POST", body: form(id) })); pass("other account cannot reuse publication id");
  const report = success(await call("/functions/v1/city-submit", { token: b.token, method: "POST", body: form(reportId, "REPORT", "POTHOLE") })).data;
  assert.equal(report.points, 0); pass("report awards no plogging points");
  const map = success(await call("/rest/v1/city_entries?select=*", { token: b.token }));
  assert.ok(map.some(row => row.id === id)); pass("authenticated shared map includes other user's confirmed publication");
  const profiles = success(await call("/rest/v1/social_profiles?select=*", { token: a.token }));
  assert.equal(profiles.length, 1); assert.equal(profiles[0].user_id, a.id); pass("private account profile not exposed cross-user");
  denied(await call(`/rest/v1/city_entries?id=eq.${id}`, { token: b.token, method: "PATCH", body: { owner_id: b.id, points: 999 } }));
  assert.equal(success(await call(`/rest/v1/city_entries?id=eq.${id}`, { token: b.token }))[0].points, 20);
  pass("direct forged update denied; real points unchanged");
  denied(await call("/functions/v1/city-delete", { token: b.token, method: "POST", body: { id } })); pass("other account cannot delete publication or photo");
  denied(await call("/rest/v1/rpc/set_report_resolved", { token: a.token, method: "POST", body: { p_id: reportId, p_resolved: true } }));
  assert.equal(success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: a.token }))[0].resolved, false);
  pass("other account cannot resolve report; target unchanged");
  success(await call("/rest/v1/rpc/set_report_resolved", { token: b.token, method: "POST", body: { p_id: reportId, p_resolved: true } }));
  assert.equal(success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: a.token }))[0].resolved, true);
  pass("owner resolves own report with real RPC");
  const path = `${a.id}/${id}.jpg`;
  denied(await call(`/storage/v1/object/city-photos/${path}`)); pass("private Storage denies anonymous read");
  const signed = success(await call(`/storage/v1/object/sign/city-photos/${path}`, { token: b.token, method: "POST", body: { expiresIn: 60 } }));
  const downloaded = await fetch(`${base}/storage/v1${signed.signedURL}`);
  assert.equal(downloaded.status, 200);
  const bytes = Buffer.from(await downloaded.arrayBuffer());
  assert.equal(bytes.includes(metadata), false); assert.equal(bytes[0], 255);
  pass("authenticated photo readable; server strips injected EXIF/GPS metadata");
  const ai = await call("/functions/v1/ai-gateway", { token: a.token, method: "POST",
    body: { task: "photo", payload: { kind: "PLOGGING", jpegBase64: source.toString("base64") } } });
  assert.equal(ai.status, 503); assert.equal(ai.data.ok, false); pass("unconfigured Gemini reports unavailable, never fabricates classification");
  const refreshed = success(await call("/auth/v1/token?grant_type=refresh_token", { method: "POST", body: { refresh_token: b.refresh } }));
  b.token = refreshed.access_token; pass("real refresh token restores session");
  const receipt = randomBytes(32).toString("hex");
  denied(await call("/functions/v1/account-delete", { token: a.token, method: "POST", body: { confirmation: "NO", requestId: receipt } }));
  pass("account deletion requires exact confirmation");
  success(await call("/functions/v1/account-delete", { token: a.token, method: "POST", body: { confirmation: "ELIMINAR", requestId: receipt } }));
  denied(await call(`/auth/v1/admin/users/${a.id}`, { token: admin }));
  assert.deepEqual(success(await call(`/rest/v1/city_entries?id=eq.${id}`, { token: b.token })), []);
  const objects = success(await call("/storage/v1/object/list/city-photos", { token: admin, method: "POST", body: { prefix: a.id, limit: 100 } }));
  assert.equal(objects.length, 0); pass("account cascade deletes Auth, publications and private photos");
  success(await call("/functions/v1/account-delete", { method: "POST", body: { confirmation: "ELIMINAR", requestId: receipt } }));
  pass("lost-response retry succeeds after user/JWT deletion using opaque receipt");
  const preserved = success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: b.token }));
  assert.equal(preserved.length, 1); assert.equal(preserved[0].resolved, true); pass("deleting account A preserves B's data");
  success(await call("/functions/v1/city-delete", { token: b.token, method: "POST", body: { id: reportId } }));
  assert.deepEqual(success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: b.token })), []);
  pass("owner deletes own publication and photo");
  success(await call("/auth/v1/logout?scope=global", { token: b.token, method: "POST" }));
  denied(await call("/auth/v1/token?grant_type=refresh_token", { method: "POST", body: { refresh_token: refreshed.refresh_token } }));
  pass("global logout revokes renewal token");
  const c = users[2], receiptWithoutProfile = randomBytes(32).toString("hex");
  success(await call("/functions/v1/account-delete", { token: c.token, method: "POST",
    body: { confirmation: "ELIMINAR", requestId: receiptWithoutProfile } }));
  denied(await call(`/auth/v1/admin/users/${c.id}`, { token: admin }));
  pass("account deletion also works before accepting a public profile");
  success(await call("/functions/v1/account-delete", { method: "POST",
    body: { confirmation: "ELIMINAR", requestId: receiptWithoutProfile } }));
  pass("deletion without profile also has a durable lost-response receipt");
  console.log(`PASS: ${checks} integration checks, real local stack; no Gemini inference claimed.`);
} finally {
  // Only UUIDs created by THIS run, never enumerate/delete unrelated users.
  for (const user of users) {
    const list = await call("/storage/v1/object/list/city-photos", { token: admin, method: "POST", body: { prefix: user.id, limit: 100 } });
    if (Array.isArray(list.data) && list.data.length) await call("/storage/v1/object/city-photos", {
      token: admin, method: "DELETE", body: { prefixes: list.data.map(row => `${user.id}/${row.name}`) } });
    await call(`/auth/v1/admin/users/${user.id}`, { token: admin, method: "DELETE" });
  }
}
