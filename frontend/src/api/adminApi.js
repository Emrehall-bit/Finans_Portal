import axiosClient from "./axiosClient";
import { getMarketsByType } from "./marketApi";
import { normalizeApiResponse } from "./responseUtils";

export async function triggerStockFetch() {
  const response = await axiosClient.post("/api/v1/admin/stocks/fetch");
  return normalizeApiResponse(response);
}

export async function getStockFetchStatus() {
  const response = await axiosClient.get("/api/v1/admin/stocks/fetch/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function triggerStockHistoryBackfill(payload) {
  const response = await axiosClient.post("/api/v1/admin/stocks/history/backfill", payload);
  return normalizeApiResponse(response);
}

export async function getStockHistoryBackfillStatus() {
  const response = await axiosClient.get("/api/v1/admin/stocks/history/backfill/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function triggerBinanceHistoryFetch(days = 1825) {
  const response = await axiosClient.post(`/api/v1/admin/binance/history/fetch?days=${days}`);
  return normalizeApiResponse(response);
}

export async function getBinanceHistoryFetchStatus() {
  const response = await axiosClient.get("/api/v1/admin/binance/history/fetch/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function triggerTcmbSync() {
  const response = await axiosClient.post("/api/v1/admin/binance/tcmb/sync");
  return normalizeApiResponse(response);
}

export async function getTcmbSyncStatus() {
  const response = await axiosClient.get("/api/v1/admin/binance/tcmb/sync/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function triggerTcmbHistoryBackfill() {
  const response = await axiosClient.post("/api/v1/admin/markets/fx/tcmb/history/backfill");
  return normalizeApiResponse(response);
}

export async function getTcmbHistoryBackfillStatus() {
  const response = await axiosClient.get("/api/v1/admin/markets/fx/tcmb/history/backfill/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function triggerTefasFundBackfill(payload) {
  const response = await axiosClient.post("/api/v1/admin/funds/backfill", payload);
  return normalizeApiResponse(response);
}

export async function triggerTefasFundFetch() {
  const response = await axiosClient.post("/api/v1/admin/funds/fetch");
  return normalizeApiResponse(response);
}

export async function getTefasFundFetchStatus() {
  const response = await axiosClient.get("/api/v1/admin/funds/fetch/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function getTefasFundBackfillStatus() {
  const response = await axiosClient.get("/api/v1/admin/funds/backfill/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function testTefasFundConnection() {
  const response = await axiosClient.get("/api/v1/admin/funds/test-connection");
  return normalizeApiResponse(response).data ?? null;
}

export async function getMarketTapeConfig() {
  const response = await axiosClient.get("/api/v1/markets/tape/config");
  return normalizeApiResponse(response).data?.symbols ?? [];
}

export async function updateMarketTapeConfig(symbols) {
  const response = await axiosClient.put("/api/v1/admin/markets/tape/config", { symbols });
  return normalizeApiResponse(response).data ?? null;
}

export async function getMarketTapeCandidates() {
  const [fx, crypto, stocks, funds] = await Promise.all([
    getMarketsByType("FX").catch(() => []),
    getMarketsByType("CRYPTO").catch(() => []),
    getMarketsByType("STOCK").catch(() => []),
    getMarketsByType("FUND").catch(() => []),
  ]);

  return [...fx, ...crypto, ...stocks, ...funds];
}

export async function syncCompanyDisclosures(ticker) {
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/disclosures/sync`);
  return normalizeApiResponse(response);
}

export async function backfillCompanyDisclosures(ticker, days = 1825) {
  const params = new URLSearchParams();
  params.set("days", String(days));
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/disclosures/backfill?${params.toString()}`);
  return normalizeApiResponse(response);
}

export async function syncCompanyFinancialReports(ticker) {
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/financial-reports/sync`);
  return normalizeApiResponse(response);
}

export async function parsePendingReports(ticker, options = {}) {
  const params = new URLSearchParams();
  if (options.includeFailed) {
    params.set("includeFailed", "true");
  }
  if (options.forceReparse) {
    params.set("forceReparse", "true");
  }
  const query = params.toString();
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/financial-reports/parse-pending${query ? `?${query}` : ""}`);
  return normalizeApiResponse(response);
}

export async function debugFetchCompanyFinancialTable(ticker, year = "2025", period = "1") {
  const params = new URLSearchParams();
  params.set("year", String(year));
  params.set("period", String(period));
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/financial-table/debug-fetch?${params.toString()}`);
  return normalizeApiResponse(response);
}

export async function backfillCompanyFinancials(ticker, payload) {
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/financials/backfill`, payload);
  return normalizeApiResponse(response);
}

export async function calculateCompanyRatios(ticker) {
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/ratios/calculate`);
  return normalizeApiResponse(response);
}

export async function syncAllCompanyDisclosures() {
  const response = await axiosClient.post(`/api/v1/admin/companies/disclosures/sync-all`);
  return normalizeApiResponse(response);
}

export async function getAdminAuditLogs(params = {}) {
  const searchParams = new URLSearchParams();

  if (params.action) {
    searchParams.set("action", params.action);
  }
  if (params.targetUserId) {
    searchParams.set("targetUserId", params.targetUserId);
  }
  if (params.actorUserId) {
    searchParams.set("actorUserId", params.actorUserId);
  }
  searchParams.set("page", String(params.page ?? 0));
  searchParams.set("size", String(params.size ?? 20));

  const response = await axiosClient.get(`/api/v1/admin/audit-logs?${searchParams.toString()}`);
  const data = normalizeApiResponse(response).data;
  return data ?? { content: [], totalElements: 0, totalPages: 0, size: 0, number: 0 };
}
