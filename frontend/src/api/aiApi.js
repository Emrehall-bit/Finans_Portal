import axiosClient from "./axiosClient";

const inFlight = new Map();

function deduplicate(key, fn) {
  if (inFlight.has(key)) return inFlight.get(key);
  const promise = fn().finally(() => inFlight.delete(key));
  inFlight.set(key, promise);
  return promise;
}

export function getAiTechnicalAnalysis(symbol) {
  return deduplicate(
    `technical:${symbol}`,
    () => axiosClient.get(`/api/v1/ai/technical/${encodeURIComponent(symbol)}`).then((r) => r.data)
  );
}

export function getAiFundamentalAnalysis(symbol) {
  return deduplicate(
    `fundamental:${symbol}`,
    () => axiosClient.get(`/api/v1/ai/fundamental/${encodeURIComponent(symbol)}`).then((r) => r.data)
  );
}

/**
 * Chat is not cached — every call hits the AI provider directly.
 * Uses a 30-second timeout to accommodate AI response latency.
 */
export function postAiChat(message, context = null) {
  return axiosClient
    .post("/api/v1/ai/chat", { message, context }, { timeout: 30000 })
    .then((r) => r.data);
}
