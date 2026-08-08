const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
const DEFAULT_MODEL = "gpt-5.4-mini";
const MAX_BODY_BYTES = 64 * 1024;
const MAX_ACTIVITIES = 120;
const MAX_HISTORY = 8;
const MAX_OUTPUT_TOKENS = 300;
const INSTRUCTIONS = "Role: You are George, the travel assistant inside CloseAI. "
  + "Choose up to three suitable activities for the user's current trip. Use every supplied "
  + "trip field as evidence. Select only activity_id values from available_activities; never "
  + "create a place, name, or ID. Use bookmarks, Day Plan, weather, hours, duration, date, and "
  + "transportation mode. For a why question, reuse the most recent grounded activity IDs in "
  + "history when appropriate. Return only the requested structured data.";

export default {
  fetch(request, env) {
    return handleRequest(request, env, fetch);
  },
};

export async function handleRequest(request, env, upstreamFetch) {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/health") {
    return jsonResponse(200, { status: "ok" });
  }
  if (url.pathname !== "/v1/responses") {
    return jsonResponse(404, { error: "Not found" });
  }
  if (request.method !== "POST") {
    return jsonResponse(405, { error: "Method not allowed" }, { Allow: "POST" });
  }
  if (!env.OPENAI_API_KEY) {
    return jsonResponse(503, { error: "George is not configured" });
  }

  const contentLength = Number(request.headers.get("content-length") || "0");
  if (contentLength > MAX_BODY_BYTES) {
    return jsonResponse(413, { error: "Request is too large" });
  }
  const requestText = await request.text();
  if (new TextEncoder().encode(requestText).byteLength > MAX_BODY_BYTES) {
    return jsonResponse(413, { error: "Request is too large" });
  }

  let clientBody;
  let context;
  try {
    clientBody = JSON.parse(requestText);
    context = JSON.parse(clientBody.input);
  } catch (error) {
    return jsonResponse(400, { error: "Request must contain valid JSON context" });
  }

  const validationError = validateContext(context);
  if (validationError) {
    return jsonResponse(400, { error: validationError });
  }

  if (env.GEORGE_RATE_LIMITER) {
    const rateLimit = await env.GEORGE_RATE_LIMITER.limit({ key: "george-responses" });
    if (!rateLimit.success) {
      return jsonResponse(429, { error: "George is busy. Please try again shortly." });
    }
  }

  const cleanContext = sanitizeContext(context);
  const activityIds = cleanContext.available_activities.map(activity => activity.activity_id);
  const upstreamBody = {
    model: env.OPENAI_MODEL || DEFAULT_MODEL,
    instructions: INSTRUCTIONS,
    input: JSON.stringify(cleanContext),
    store: false,
    max_output_tokens: MAX_OUTPUT_TOKENS,
    text: { format: responseFormat(activityIds) },
  };

  try {
    const upstream = await upstreamFetch(OPENAI_RESPONSES_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.OPENAI_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(upstreamBody),
    });
    if (!upstream.ok) {
      const status = upstream.status === 429 ? 429 : 502;
      return jsonResponse(status, {
        error: status === 429
          ? "George is temporarily unavailable"
          : "George could not complete the AI request",
      });
    }
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "Content-Type": upstream.headers.get("content-type") || "application/json" },
    });
  } catch (error) {
    return jsonResponse(502, { error: "George could not reach the AI service" });
  }
}

function validateContext(context) {
  if (!context || typeof context !== "object" || Array.isArray(context)) {
    return "Trip context is required";
  }
  if (!isShortString(context.destination, 200)) {
    return "A valid destination is required";
  }
  if (!isShortString(context.question, 1000)) {
    return "A valid question is required";
  }
  if (!Array.isArray(context.available_activities)
      || context.available_activities.length === 0
      || context.available_activities.length > MAX_ACTIVITIES) {
    return "Available activities are required";
  }
  const ids = new Set();
  for (const activity of context.available_activities) {
    if (!activity || !isShortString(activity.activity_id, 200)
        || !isShortString(activity.name, 300) || ids.has(activity.activity_id)) {
      return "Activities must have unique IDs and names";
    }
    ids.add(activity.activity_id);
  }
  if (context.history !== undefined
      && (!Array.isArray(context.history) || context.history.length > MAX_HISTORY)) {
    return "Chat history is too long";
  }
  return null;
}

function sanitizeContext(context) {
  const allowedIds = new Set(context.available_activities.map(value => value.activity_id));
  return {
    destination: text(context.destination, 200),
    trip_date: text(context.trip_date, 40),
    trip_start: text(context.trip_start, 20),
    trip_end: text(context.trip_end, 20),
    transportation_mode: text(context.transportation_mode, 40),
    available_activities: context.available_activities.map(activity => ({
      activity_id: text(activity.activity_id, 200),
      name: text(activity.name, 300),
      category: text(activity.category, 80),
      rating: number(activity.rating),
      duration_minutes: number(activity.duration_minutes),
      opening_time: text(activity.opening_time, 20),
      closing_time: text(activity.closing_time, 20),
      setting: text(activity.setting, 40),
      address: text(activity.address, 500),
    })),
    bookmarked_activity_ids: stringArray(context.bookmarked_activity_ids, 120)
      .filter(id => allowedIds.has(id)),
    day_plan: objectArray(context.day_plan, 120),
    weather: objectArray(context.weather, 120),
    history: objectArray(context.history, MAX_HISTORY),
    question: text(context.question, 1000),
  };
}

function responseFormat(activityIds) {
  return {
    type: "json_schema",
    name: "trip_activity_selection",
    strict: true,
    schema: {
      type: "object",
      properties: {
        intent: {
          type: "string",
          enum: ["RECOMMEND", "RAIN", "AFTERNOON", "BOOKMARKS", "EXPLAIN", "GENERAL"],
        },
        activity_ids: {
          type: "array",
          items: { type: "string", enum: activityIds },
          maxItems: 3,
        },
      },
      required: ["intent", "activity_ids"],
      additionalProperties: false,
    },
  };
}

function objectArray(value, maximum) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.slice(0, maximum).filter(item => item && typeof item === "object");
}

function stringArray(value, maximum) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.slice(0, maximum).filter(item => typeof item === "string");
}

function text(value, maximum) {
  return typeof value === "string" ? value.slice(0, maximum) : "";
}

function number(value) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function isShortString(value, maximum) {
  return typeof value === "string" && value.trim().length > 0 && value.length <= maximum;
}

function jsonResponse(status, body, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...extraHeaders },
  });
}
