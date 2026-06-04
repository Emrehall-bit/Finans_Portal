import axiosClient from "./axiosClient";

const inFlight = new Map();

function deduplicate(key, fn) {
  if (inFlight.has(key)) return inFlight.get(key);
  const promise = fn().finally(() => inFlight.delete(key));
  inFlight.set(key, promise);
  return promise;
}

export function getAiTechnicalAnalysis(symbol, language = "tr") {
  return deduplicate(
    `technical:${symbol}:${language}`,
    () => axiosClient
      .get(`/api/v1/ai/technical/${encodeURIComponent(symbol)}`, { params: { language } })
      .then((r) => r.data)
  );
}

export function getAiFundamentalAnalysis(symbol, language = "tr") {
  return deduplicate(
    `fundamental:${symbol}:${language}`,
    () => axiosClient
      .get(`/api/v1/ai/fundamental/${encodeURIComponent(symbol)}`, { params: { language } })
      .then((r) => r.data)
  );
}

export function getAiUnifiedAnalysis(symbol, type, language = "tr") {
  return deduplicate(
    `unified:${symbol}:${language}`,
    () => axiosClient
      .get(`/api/v1/ai/unified/${encodeURIComponent(symbol)}`, { params: { type, language } })
      .then((r) => r.data)
  );
}

export function getAiNewsImpactAnalysis(newsId, language = "tr") {
  return deduplicate(
    `news-impact:${newsId}:${language}`,
    () => axiosClient
      .get(`/api/v1/ai/news-impact/${encodeURIComponent(newsId)}`, { params: { language } })
      .then((r) => r.data)
  );
}

export function getAiComparisonAnalysis(left, right, language = "tr") {
  const pair = [left, right]
    .map((value) => String(value || "").trim().toUpperCase())
    .sort()
    .join("-");

  return deduplicate(
    `compare-analysis:${pair}:${language}`,
    () => axiosClient
      .get("/api/v1/ai/compare-analysis", { params: { left, right, language } })
      .then((r) => r.data)
  );
}

/**
 * Chat is not cached — every call hits the AI provider directly.
 * Uses a 30-second timeout to accommodate AI response latency.
 */
export function getPortfolioAiAnalysis(portfolioId, language = "tr") {
  return deduplicate(
    `portfolio-analysis:${portfolioId}:${language}`,
    () => axiosClient
      .get(`/api/v1/ai/portfolio-analysis/${encodeURIComponent(portfolioId)}`, { params: { language } })
      .then((r) => r.data)
  );
}

export function getDashboardAiAnalysis({ language = "tr", avgMarketChange = 0, gainerCount = 0, loserCount = 0, totalQuotes = 0 } = {}) {
  const key = `dashboard-analysis:${language}:${avgMarketChange}:${gainerCount}:${loserCount}:${totalQuotes}`;
  return deduplicate(
    key,
    () => axiosClient
      .get("/api/v1/ai/dashboard", {
        params: { language, avgMarketChange, gainerCount, loserCount, totalQuotes },
        timeout: 30000,
      })
      .then((r) => r.data)
  );
}

export function postAiChat(message, context = null, language = "tr") {
  return axiosClient
    .post("/api/v1/ai/chat", { message, context, language }, { timeout: 30000 })
    .then((r) => r.data);
}
