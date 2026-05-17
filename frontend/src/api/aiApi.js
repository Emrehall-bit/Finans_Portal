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

export function getAiUnifiedAnalysis(symbol, type = "STOCK") {
  return deduplicate(
    `unified:${symbol}`,
    () => axiosClient
      .get(`/api/v1/ai/unified/${encodeURIComponent(symbol)}`, { params: { type } })
      .then((r) => r.data)
  );
}

export function getAiNewsImpactAnalysis(newsId) {
  return deduplicate(
    `news-impact:${newsId}`,
    () => axiosClient
      .get(`/api/v1/ai/news-impact/${encodeURIComponent(newsId)}`)
      .then((r) => r.data)
  );
}

export function getAiComparisonAnalysis(left, right) {
  const pair = [left, right]
    .map((value) => String(value || "").trim().toUpperCase())
    .sort()
    .join("-");

  return deduplicate(
    `compare-analysis:${pair}`,
    () => axiosClient
      .get("/api/v1/ai/compare-analysis", { params: { left, right } })
      .then((r) => r.data)
  );
}

/**
 * Chat is not cached — every call hits the AI provider directly.
 * Uses a 30-second timeout to accommodate AI response latency.
 */
export function getPortfolioAiAnalysis(portfolioId) {
  return deduplicate(
    `portfolio-analysis:${portfolioId}`,
    () => axiosClient
      .get(`/api/v1/ai/portfolio-analysis/${encodeURIComponent(portfolioId)}`)
      .then((r) => r.data)
  );
}

export function postAiChat(message, context = null) {
  return axiosClient
    .post("/api/v1/ai/chat", { message, context }, { timeout: 30000 })
    .then((r) => r.data);
}
