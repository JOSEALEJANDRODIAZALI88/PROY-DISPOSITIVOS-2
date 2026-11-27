// Offline Node 22+ tests. No network, secrets or real inference.
import test from "node:test";
import assert from "node:assert/strict";
import { photoRequest, parsePhotoResponse, classifyPhoto, providerFailure, GeminiFailure }
  from "../supabase/functions/_shared/gemini.ts";

const result = (text, finishReason = "STOP") => ({ candidates: [
  { finishReason, content: { parts: [{ text }] } },
] });
const suggestion = { label: "MEDIUM", reason: "Cantidad intermedia visible.", unsafe: false };
const valid = () => result(JSON.stringify(suggestion));

test("3.8 Flash uses LOW, schema and sufficient bounded output, without requesting thoughts", () => {
  const body = photoRequest("PLOGGING", "private-image", "gemini-3.8-flash");
  assert.equal(body.generationConfig.thinkingConfig.thinkingLevel, "LOW");
  assert.equal(body.generationConfig.maxOutputTokens, 2048);
  assert.deepEqual(body.generationConfig.responseSchema.properties.label.enum, ["LITTLE", "MEDIUM", "MUCH"]);
  assert.equal(body.generationConfig.thinkingConfig.includeThoughts, undefined);
  assert.equal(JSON.stringify(body).includes("latitude"), false);
});
test("older Flash models do not receive unsupported thinking-level parameters", () => {
  assert.equal(photoRequest("REPORT", "image", "gemini-2.5-flash").generationConfig.thinkingConfig.thinkingBudget, 0);
  assert.equal(photoRequest("REPORT", "image", "gemini-2.0-flash").generationConfig.thinkingConfig, undefined);
});
test("successful response retains labels and unsafe Boolean; reason length bounded", () => {
  assert.deepEqual(parsePhotoResponse(valid(), "PLOGGING"), suggestion);
  const value = parsePhotoResponse(result(JSON.stringify({ ...suggestion, reason: "x".repeat(400), unsafe: true })), "PLOGGING");
  assert.equal(value.reason.length, 300);
  assert.equal(value.unsafe, true);
});
test("thoughts are never parsed as classification output", () => {
  const value = valid();
  value.candidates[0].content.parts.unshift({ text: "private internal thoughts", thought: true });
  assert.deepEqual(parsePhotoResponse(value, "PLOGGING"), suggestion);
});
test("truncated output is not accepted even if a partial JSON looks valid", () => {
  assert.throws(() => parsePhotoResponse(result(JSON.stringify(suggestion), "MAX_TOKENS"), "PLOGGING"),
    error => error.code === "GEMINI_RESPONSE_TRUNCATED");
});
test("safety refusals are not reported as model downtime", () => {
  assert.throws(() => parsePhotoResponse({ promptFeedback: { blockReason: "SAFETY" } }, "REPORT"),
    error => error.code === "GEMINI_SAFETY_BLOCKED");
  assert.throws(() => parsePhotoResponse(result("", "SPII"), "REPORT"),
    error => error.code === "GEMINI_SAFETY_BLOCKED");
});
test("malformed, missing, wrong-label and wrong-type outputs never produce a fabricated suggestion", () => {
  for (const value of [null, {}, result("invalid"), result("null"),
    result(JSON.stringify({ ...suggestion, label: "POTHOLE" })),
    result(JSON.stringify({ ...suggestion, unsafe: "false" })),
    result(JSON.stringify({ ...suggestion, reason: { secret: "private" } }))])
    assert.throws(() => parsePhotoResponse(value, "PLOGGING"),
      error => error.code === "GEMINI_INVALID_RESPONSE");
});
test("Google errors expose only whitelisted identifiers, never provider text or metadata", () => {
  const error = providerFailure(429, { error: { status: "RESOURCE_EXHAUSTED",
    message: "private key/contents", details: [{ secret: "private" }] } });
  assert.equal(error.code, "GEMINI_QUOTA_EXHAUSTED");
  assert.deepEqual(error.diagnostic, { event: "pacefit_ai_failure", code: "GEMINI_QUOTA_EXHAUSTED",
    upstreamHttpStatus: 429, providerStatus: "RESOURCE_EXHAUSTED" });
  assert.equal(JSON.stringify(error).includes("private"), false);
  assert.equal(providerFailure(403, null).code, "GEMINI_ACCESS_DENIED");
  assert.equal(providerFailure(400, null).code, "GEMINI_REQUEST_REJECTED");
  assert.equal(providerFailure(503, { error: { status: "private" } }).diagnostic.providerStatus, undefined);
});
test("real request adapter uses key only in a header and performs exactly one mocked request", async () => {
  let count = 0;
  const actual = await classifyPhoto("private-test-value", "gemini-3.8-flash", "PLOGGING", "fixture", async (url, init) => {
    count++;
    assert.equal(url.includes("private-test-value"), false);
    assert.equal(init.headers["x-goog-api-key"], "private-test-value");
    return Response.json(valid());
  });
  assert.deepEqual(actual, suggestion);
  assert.equal(count, 1);
});
test("429 never triggers a retry or a paid-model fallback", async () => {
  let count = 0;
  await assert.rejects(() => classifyPhoto("dummy", "gemini-3.8-flash", "REPORT", "fixture", async () => {
    count++;
    return Response.json({ error: { status: "RESOURCE_EXHAUSTED", message: "private" } }, { status: 429 });
  }), error => error.code === "GEMINI_QUOTA_EXHAUSTED");
  assert.equal(count, 1);
});
test("timeout and network failures do not leak exception text", async () => {
  for (const [name, code] of [["TimeoutError", "GEMINI_TIMEOUT"], ["TypeError", "GEMINI_UNAVAILABLE"]]) {
    await assert.rejects(() => classifyPhoto("dummy", "gemini-3.8-flash", "REPORT", "fixture", async () => {
      const error = new Error("private request contents"); error.name = name; throw error;
    }), error => error instanceof GeminiFailure && error.code === code && !error.message.includes("private"));
  }
});
test("non-JSON provider errors and non-JSON success responses remain safe failures", async () => {
  for (const [status, code] of [[503, "GEMINI_UNAVAILABLE"], [200, "GEMINI_INVALID_RESPONSE"]])
    await assert.rejects(() => classifyPhoto("dummy", "gemini-3.8-flash", "REPORT", "fixture",
      async () => new Response("private unexpected HTML", { status })),
    error => error.code === code && !error.message.includes("private"));
});
