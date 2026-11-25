// Real HTTPS smoke checks, ONLY the newly authorized PaceFit project.
// Test users are admin-created/confirmed: SMTP delivery is NOT verified here.
import { readFileSync } from "node:fs";
import { randomUUID, randomBytes } from "node:crypto";
import assert from "node:assert/strict";

const ref = "ymfypllkcrqwfwihqzpf";
const base = process.env.PACEFIT_CLOUD_URL;
assert.equal(process.env.PACEFIT_CLOUD_TEST_REF, ref, "Explicit cloud test authorization required.");
assert.equal(base, `https://${ref}.supabase.co`, "Never target unrelated projects.");
const anonymous = process.env.PACEFIT_CLOUD_ANON_KEY;
const admin = process.env.PACEFIT_CLOUD_SERVICE_KEY;
assert.ok(anonymous && admin);
let checks = 0;
const pass = text => console.log(`ok ${++checks} - ${text}`);
async function call(path, { token = anonymous, method = "GET", body } = {}) {
  const multipart = body instanceof FormData;
  const response = await fetch(base + path, {
    method, headers: { apikey: anonymous, Authorization: `Bearer ${token}`,
      ...(!multipart && body !== undefined ? { "Content-Type": "application/json" } : {}) },
    body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(60000),
  });
  const text = await response.text();
  let data; try { data = JSON.parse(text); } catch { data = null; }
  return { status: response.status, data };
}
function success(result) {
  assert.ok(result.status >= 200 && result.status < 300, `HTTP ${result.status}`);
  return result.data;
}
function denied(result) { assert.ok(result.status >= 400 && result.status < 500, `Expected denial; HTTP ${result.status}`); }
const jpeg = Buffer.from(readFileSync(new URL("../supabase/tests/photo.base64", import.meta.url), "utf8").trim(), "base64");
const metadata = Buffer.from("Exif\0\0GPSLatitude=secret-test;CameraSerial=secret-test");
const marker = Buffer.from([255, 225, (metadata.length + 2) >> 8, (metadata.length + 2) & 255]);
const source = Buffer.concat([jpeg.subarray(0, 2), marker, metadata, jpeg.subarray(2)]);
function form(id, kind = "PLOGGING", label = "MUCH", consent = "true") {
  const result = new FormData();
  for (const [key, value] of Object.entries({ id, kind, label, description: "Prueba automatizada temporal",
    latitude: "-16.5", longitude: "-68.15", consent, points: "99999" })) result.set(key, value);
  result.set("photo", new Blob([source], { type: "image/jpeg" }), "photo.jpg");
  return result;
}
const users = [];
const run = randomUUID();
try {
  for (let index = 0; index < 3; index++) {
    const email = `pacefit-cloud-${randomUUID()}@example.test`;
    const password = randomBytes(24).toString("hex");
    const created = success(await call("/auth/v1/admin/users", { token: admin, method: "POST",
      body: { email, password, email_confirm: true, app_metadata: { pacefit_smoke_run: run } } }));
    const user = { id: created.id };
    assert.match(user.id, /^[0-9a-f-]{36}$/);
    users.push(user); // Track BEFORE sign-in, so failures still clean only this user's records.
    const auth = success(await call("/auth/v1/token?grant_type=password", {
      method: "POST", body: { email, password } }));
    user.token = auth.access_token; user.refresh = auth.refresh_token;
    assert.ok(user.token);
    if (index < 2) success(await call("/rest/v1/rpc/accept_social_profile", { token: user.token,
      method: "POST", body: { p_display_name: `Prueba nube ${index}` } }));
  }
  pass("three disposable confirmed accounts sign in; two accept public profiles");
  const [a, b, c] = users, id = randomUUID(), reportId = randomUUID();
  const quota = await Promise.all(Array.from({ length: 45 }, () =>
    call("/rest/v1/rpc/reserve_ai_call", { token: admin, method: "POST",
      body: { p_user: a.id, p_task: "photo" } }).then(success)));
  assert.equal(quota.filter(Boolean).length, 40);
  assert.equal(quota.filter(value => value === false).length, 5);
  assert.equal(success(await call(`/rest/v1/ai_usage?select=id&user_id=eq.${a.id}`, { token: admin })).length, 40);
  pass("cloud atomic quota: 45 concurrent requests admit 40 and audit agrees");
  denied(await call("/rest/v1/city_entries")); pass("anonymous shared-map access denied");
  denied(await call("/functions/v1/city-submit", { method: "POST", body: form(id) })); pass("anonymous publish denied");
  denied(await call("/functions/v1/city-submit", { token: a.token, method: "POST", body: form(id, "PLOGGING", "MUCH", "false") }));
  pass("explicit publication consent enforced");
  const entry = success(await call("/functions/v1/city-submit", { token: a.token, method: "POST", body: form(id) })).data;
  assert.equal(entry.points, 20); assert.equal(entry.owner_id, a.id); pass("server derives 20 points, ignores forged 99999");
  assert.equal(success(await call("/functions/v1/city-submit", { token: a.token, method: "POST", body: form(id) })).data.id, id);
  pass("publication retry idempotent");
  denied(await call("/functions/v1/city-submit", { token: b.token, method: "POST", body: form(id) }));
  pass("cross-account publication collision denied");
  assert.equal(success(await call("/functions/v1/city-submit", { token: b.token, method: "POST",
    body: form(reportId, "REPORT", "POTHOLE") })).data.points, 0);
  pass("urban report has zero plogging points");
  assert.ok(success(await call("/rest/v1/city_entries?select=*", { token: b.token })).some(row => row.id === id));
  pass("authenticated shared map includes confirmed publication");
  const profiles = success(await call("/rest/v1/social_profiles?select=*", { token: a.token }));
  assert.equal(profiles.length, 1); assert.equal(profiles[0].user_id, a.id);
  pass("RLS account profile remains owner-only");
  denied(await call(`/rest/v1/city_entries?id=eq.${id}`, { token: b.token, method: "PATCH", body: { owner_id: b.id, points: 999 } }));
  assert.equal(success(await call(`/rest/v1/city_entries?id=eq.${id}`, { token: b.token }))[0].points, 20);
  pass("forged direct mutation denied and target unchanged");
  denied(await call("/functions/v1/city-delete", { token: b.token, method: "POST", body: { id } }));
  pass("cross-account photo/publication deletion denied");
  denied(await call("/rest/v1/rpc/set_report_resolved", { token: a.token, method: "POST",
    body: { p_id: reportId, p_resolved: true } }));
  assert.equal(success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: a.token }))[0].resolved, false);
  pass("cross-account report resolution denied and target unchanged");
  success(await call("/rest/v1/rpc/set_report_resolved", { token: b.token, method: "POST", body: { p_id: reportId, p_resolved: true } }));
  assert.equal(success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: a.token }))[0].resolved, true);
  pass("owner report resolution persisted");
  const path = `${a.id}/${id}.jpg`;
  denied(await call(`/storage/v1/object/city-photos/${path}`)); pass("anonymous private photo access denied");
  const signed = success(await call(`/storage/v1/object/sign/city-photos/${path}`, { token: b.token,
    method: "POST", body: { expiresIn: 60 } }));
  const download = await fetch(`${base}/storage/v1${signed.signedURL}`);
  assert.equal(download.status, 200);
  const bytes = Buffer.from(await download.arrayBuffer());
  assert.equal(bytes.includes(metadata), false); assert.equal(bytes[0], 255);
  pass("authenticated signed photo works; source GPS/EXIF removed");
  // No actual model inference until an authorized free-tier Gemini key is configured.
  const ai = await call("/functions/v1/ai-gateway", { token: b.token, method: "POST",
    body: { task: "photo", payload: { kind: "PLOGGING", jpegBase64: source.toString("base64") } } });
  assert.equal(ai.status, 503); assert.equal(ai.data.ok, false);
  pass("missing Gemini gives honest 503/manual fallback, not fabricated inference");
  const refreshed = success(await call("/auth/v1/token?grant_type=refresh_token", {
    method: "POST", body: { refresh_token: b.refresh } }));
  b.token = refreshed.access_token; pass("real cloud refresh token works");
  const receipt = randomBytes(32).toString("hex");
  denied(await call("/functions/v1/account-delete", { token: a.token, method: "POST",
    body: { confirmation: "NO", requestId: receipt } })); pass("exact deletion confirmation enforced");
  success(await call("/functions/v1/account-delete", { token: a.token, method: "POST",
    body: { confirmation: "ELIMINAR", requestId: receipt } }));
  denied(await call(`/auth/v1/admin/users/${a.id}`, { token: admin }));
  assert.deepEqual(success(await call(`/rest/v1/city_entries?id=eq.${id}`, { token: b.token })), []);
  assert.equal(success(await call("/storage/v1/object/list/city-photos", { token: admin, method: "POST",
    body: { prefix: a.id, limit: 100 } })).length, 0);
  pass("account deletion removes Auth, own rows and own photos");
  success(await call("/functions/v1/account-delete", { method: "POST", body: { confirmation: "ELIMINAR", requestId: receipt } }));
  pass("receipt retries succeed after Auth/JWT removal");
  assert.equal(success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: b.token })).length, 1);
  pass("account A deletion preserves account B");
  success(await call("/functions/v1/city-delete", { token: b.token, method: "POST", body: { id: reportId } }));
  assert.deepEqual(success(await call(`/rest/v1/city_entries?id=eq.${reportId}`, { token: b.token })), []);
  pass("owner publication/photo deletion persisted");
  success(await call("/auth/v1/logout?scope=global", { token: b.token, method: "POST" }));
  denied(await call("/auth/v1/token?grant_type=refresh_token", { method: "POST", body: { refresh_token: refreshed.refresh_token } }));
  pass("cloud global logout revokes renewal");
  const pending = randomBytes(32).toString("hex");
  success(await call("/functions/v1/account-delete", { token: c.token, method: "POST",
    body: { confirmation: "ELIMINAR", requestId: pending } }));
  denied(await call(`/auth/v1/admin/users/${c.id}`, { token: admin }));
  pass("account deletion works before public-profile acceptance");
  success(await call("/functions/v1/account-delete", { method: "POST",
    body: { confirmation: "ELIMINAR", requestId: pending } }));
  pass("unprofiled-account deletion receipt is durable");
  console.log(`PASS: ${checks} real cloud HTTPS checks; no SMTP delivery or Gemini inference claimed.`);
} finally {
  for (const user of users) {
    const list = await call("/storage/v1/object/list/city-photos", { token: admin, method: "POST", body: { prefix: user.id, limit: 100 } });
    if (Array.isArray(list.data) && list.data.length) await call("/storage/v1/object/city-photos", {
      token: admin, method: "DELETE", body: { prefixes: list.data.map(row => `${user.id}/${row.name}`) } });
    await call(`/auth/v1/admin/users/${user.id}`, { token: admin, method: "DELETE" });
  }
}
