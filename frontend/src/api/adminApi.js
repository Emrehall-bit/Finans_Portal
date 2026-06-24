import axiosClient from "./axiosClient";
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

export async function calculateCompanyRatios(ticker) {
  const response = await axiosClient.post(`/api/v1/admin/companies/${encodeURIComponent(ticker)}/ratios/calculate`);
  return normalizeApiResponse(response);
}

export async function importCompanyFinancialCsv({
  file,
  dryRun = false,
  replaceExisting = true,
  recalculateRatios = true,
  overwriteShareCount = false,
}) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("dryRun", String(dryRun));
  formData.append("replaceExisting", String(replaceExisting));
  formData.append("recalculateRatios", String(recalculateRatios));
  formData.append("overwriteShareCount", String(overwriteShareCount));

  const response = await axiosClient.post("/api/v1/admin/companies/financials/import", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });

  return normalizeApiResponse(response);
}

export async function importCompanyShareCounts({
  file,
  overwrite = false,
}) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("overwrite", String(overwrite));

  const response = await axiosClient.post("/api/v1/admin/companies/import-share-counts", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });

  return normalizeApiResponse(response);
}

export async function triggerMacroCpiSync() {
  const response = await axiosClient.post("/api/v1/admin/markets/macro/tcmb/cpi/sync");
  return normalizeApiResponse(response);
}

export async function triggerMacroPpiSync() {
  const response = await axiosClient.post("/api/v1/admin/markets/macro/tcmb/ppi/sync");
  return normalizeApiResponse(response);
}

export async function triggerMacroPolicyRateSync() {
  const response = await axiosClient.post("/api/v1/admin/markets/macro/tcmb/policy-rate/sync");
  return normalizeApiResponse(response);
}

export async function triggerMacroLaborMarketSync() {
  const response = await axiosClient.post("/api/v1/admin/markets/macro/tcmb/labor-market/sync");
  return normalizeApiResponse(response);
}

export async function triggerMacroConsumerConfidenceSync() {
  const response = await axiosClient.post("/api/v1/admin/markets/macro/tcmb/consumer-confidence/sync");
  return normalizeApiResponse(response);
}

export async function triggerMacroCurrentAccountSync() {
  const response = await axiosClient.post("/api/v1/admin/markets/macro/tcmb/current-account/sync");
  return normalizeApiResponse(response);
}

export async function triggerIndexFetch() {
  const response = await axiosClient.post("/api/v1/admin/markets/indexes/fetch-now");
  return normalizeApiResponse(response);
}

export async function triggerCommodityDerive() {
  const response = await axiosClient.post("/api/v1/admin/markets/commodities/derive-now");
  return normalizeApiResponse(response);
}

export async function triggerInternalCommodityHistoryBackfill(days = 365) {
  const response = await axiosClient.post(`/api/v1/admin/markets/internal-commodities/history/backfill?days=${days}`);
  return normalizeApiResponse(response);
}

export async function triggerCommodityHistoryBackfill(days = 365) {
  const response = await axiosClient.post(`/api/v1/admin/markets/commodities/history/backfill?days=${days}`);
  return normalizeApiResponse(response);
}

export async function triggerIndexHistoryBackfill(days = 365) {
  const response = await axiosClient.post(`/api/v1/admin/markets/indexes/history/backfill?days=${days}`);
  return normalizeApiResponse(response);
}

export async function seedMockRatios() {
  const response = await axiosClient.post("/api/v1/admin/companies/seed-mock-ratios");
  return normalizeApiResponse(response);
}

export async function seedMockDerivatives() {
  const response = await axiosClient.post("/api/v1/admin/markets/mock-derivatives/seed");
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
