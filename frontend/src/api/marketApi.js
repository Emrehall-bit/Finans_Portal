import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";

const DEFAULT_INDICATORS = "SMA7,SMA20,SMA50,RSI14";

function normalizeArrayPayload(payload) {
  if (Array.isArray(payload)) {
    return payload;
  }

  if (Array.isArray(payload?.data)) {
    return payload.data;
  }

  if (Array.isArray(payload?.data?.data)) {
    return payload.data.data;
  }

  if (Array.isArray(payload?.quotes)) {
    return payload.quotes;
  }

  return [];
}

function normalizeObjectPayload(payload) {
  if (payload == null) {
    return null;
  }

  if (payload?.data && !Array.isArray(payload.data)) {
    return payload.data;
  }

  return payload;
}

function toIsoDate(value) {
  return value.toISOString().slice(0, 10);
}

function buildDefaultAnalysisRange() {
  const to = new Date();
  const from = new Date(to);
  from.setMonth(from.getMonth() - 3);

  return {
    from: toIsoDate(from),
    to: toIsoDate(to),
  };
}

export async function getMarkets(params = {}) {
  const { data } = await axiosClient.get(API_CONFIG.ENDPOINTS.markets, { params });
  return normalizeArrayPayload(data);
}

export async function getMarketQuotes() {
  return getMarkets();
}

export async function getMarketBySymbol(symbol) {
  const { data } = await axiosClient.get(
    `${API_CONFIG.ENDPOINTS.markets}/symbol/${encodeURIComponent(symbol)}`,
  );
  return normalizeObjectPayload(data);
}

export async function getMarketQuote(symbol) {
  return getMarketBySymbol(symbol);
}

export async function getMarketsByType(type) {
  const { data } = await axiosClient.get(
    `${API_CONFIG.ENDPOINTS.markets}/type/${encodeURIComponent(type)}`,
  );
  return normalizeArrayPayload(data);
}

export async function getMarketHistory(symbol, paramsOrRange) {
  const params =
    typeof paramsOrRange === "string"
      ? { range: paramsOrRange }
      : {
          range: paramsOrRange?.range,
          startDate: paramsOrRange?.from,
          endDate: paramsOrRange?.to,
          source: paramsOrRange?.source,
        };

  const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/${encodeURIComponent(symbol)}/history`, {
    params,
  });
  return normalizeArrayPayload(data);
}

export async function getMacroHistory(symbol, params = {}) {
  const { data } = await axiosClient.get(
    `${API_CONFIG.ENDPOINTS.markets}/history/${encodeURIComponent(symbol)}`,
    { params },
  );
  return normalizeArrayPayload(data);
}

export async function getTechnicalAnalysis(symbol, from, to, indicators = DEFAULT_INDICATORS) {
  const defaultRange = buildDefaultAnalysisRange();
  const params =
    typeof from === "object" && from !== null
      ? {
          from: from.from ?? defaultRange.from,
          to: from.to ?? defaultRange.to,
          indicators: from.indicators ?? DEFAULT_INDICATORS,
        }
      : {
          from: from ?? defaultRange.from,
          to: to ?? defaultRange.to,
          indicators: indicators ?? DEFAULT_INDICATORS,
        };

  const { data } = await axiosClient.get(
    `${API_CONFIG.ENDPOINTS.technicalAnalysis}/${encodeURIComponent(symbol)}`,
    { params },
  );
  return normalizeObjectPayload(data);
}

export async function compareTechnicalAnalysis(params) {
  const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.technicalAnalysis}/compare`, {
    params,
  });
  return data;
}
