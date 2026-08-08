import assert from "node:assert/strict";
import test from "node:test";
import { handleRequest } from "../src/index.js";

const endpoint = "https://proxy.example/v1/responses";

test("health endpoint does not require a secret", async () => {
  const response = await handleRequest(
    new Request("https://proxy.example/health"), {}, failFetch);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: "ok" });
});

test("proxy fixes model, instructions, schema, storage, and output limit", async () => {
  let capturedUrl;
  let capturedOptions;
  const response = await handleRequest(request({ model: "expensive-model" }), {
    OPENAI_API_KEY: "server-only-test-key",
    OPENAI_MODEL: "gpt-5.4-mini",
    GEORGE_RATE_LIMITER: { limit: async () => ({ success: true }) },
  }, async (url, options) => {
    capturedUrl = url;
    capturedOptions = options;
    return new Response(JSON.stringify({ status: "completed", output: [] }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  });

  assert.equal(response.status, 200);
  assert.equal(capturedUrl, "https://api.openai.com/v1/responses");
  assert.equal(capturedOptions.headers.Authorization, "Bearer server-only-test-key");
  const body = JSON.parse(capturedOptions.body);
  assert.equal(body.model, "gpt-5.4-mini");
  assert.equal(body.store, false);
  assert.equal(body.max_output_tokens, 300);
  assert.match(body.instructions, /only activity_id/);
  assert.deepEqual(
    body.text.format.schema.properties.activity_ids.items.enum,
    ["museum"]);
});

test("proxy rejects malformed, oversized, and rate-limited requests", async () => {
  const malformed = await handleRequest(new Request(endpoint, {
    method: "POST",
    body: JSON.stringify({ input: "{}" }),
  }), { OPENAI_API_KEY: "test" }, failFetch);
  assert.equal(malformed.status, 400);

  const oversized = await handleRequest(new Request(endpoint, {
    method: "POST",
    body: "x".repeat(64 * 1024 + 1),
  }), { OPENAI_API_KEY: "test" }, failFetch);
  assert.equal(oversized.status, 413);

  const limited = await handleRequest(request(), {
    OPENAI_API_KEY: "test",
    GEORGE_RATE_LIMITER: { limit: async () => ({ success: false }) },
  }, failFetch);
  assert.equal(limited.status, 429);
});

test("proxy does not expose upstream account errors", async () => {
  const response = await handleRequest(request(), {
    OPENAI_API_KEY: "test",
  }, async () => new Response(JSON.stringify({
    error: { message: "private billing detail", code: "credit_balance_exhausted" },
  }), {
    status: 429,
    headers: { "Content-Type": "application/json" },
  }));

  assert.equal(response.status, 429);
  assert.deepEqual(await response.json(), { error: "George is temporarily unavailable" });
});

function request(overrides = {}) {
  const context = {
    destination: "Toronto",
    trip_date: "2026-08-20",
    trip_start: "09:00",
    trip_end: "18:00",
    transportation_mode: "TRANSIT",
    available_activities: [{
      activity_id: "museum",
      name: "Actual Museum",
      category: "MUSEUM",
      rating: 4.8,
      duration_minutes: 90,
      opening_time: "09:00",
      closing_time: "18:00",
      setting: "INDOOR",
      address: "Museum address",
    }],
    bookmarked_activity_ids: ["museum"],
    day_plan: [],
    weather: [],
    history: [],
    question: "What should I do if it rains?",
  };
  return new Request(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "client-model",
      input: JSON.stringify(context),
      ...overrides,
    }),
  });
}

async function failFetch() {
  throw new Error("Unexpected upstream request");
}
