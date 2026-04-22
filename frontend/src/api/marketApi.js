import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

function normalizeMarketItem(item) {
  if (!item || typeof item !== "object") {
    return null;
  }

  return {
    ...item,
    symbol: item.symbol ?? "",
    name: item.name ?? "",
    instrumentType: item.instrumentType ?? "UNKNOWN",
    source: item.source ?? "",
    currency: item.currency ?? "TRY",
    price: item.price ?? null,
    changeAmount: item.changeAmount ?? null,
    changePercent: item.changePercent ?? null,
    freshness: item.freshness ?? "UNKNOWN",
    priceTime: item.priceTime ?? null,
    fetchedAt: item.fetchedAt ?? null,
    lastUpdated: item.priceTime ?? item.fetchedAt ?? null,
  };
}

function normalizeMarketItems(items) {
  if (!Array.isArray(items)) {
    return [];
  }

  return items
    .map(normalizeMarketItem)
    .filter(Boolean)
    .sort((left, right) => String(left.symbol || "").localeCompare(String(right.symbol || "")));
}

export async function getMarketData(params = {}) {
  const response = await axiosClient.get(API_CONFIG.ENDPOINTS.markets, { params });
  return normalizeMarketItems(normalizeApiResponse(response).data);
}

export async function getTcmbMarketData() {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/tcmb`);
  return normalizeMarketItems(normalizeApiResponse(response).data);
}
