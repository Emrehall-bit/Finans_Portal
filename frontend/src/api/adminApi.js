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
