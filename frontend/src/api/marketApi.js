import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";

const DEFAULT_INDICATORS = "SMA7,SMA20,SMA50,RSI14";

function normalizeArrayPayload(payload) {
  if (Array.isArray(payload)) {
    return payload;
  }

  if (Array.isArray(payload?.content)) {
    return payload.content;
  }

  if (Array.isArray(payload?.data)) {
    return payload.data;
  }

  if (Array.isArray(payload?.data?.content)) {
    return payload.data.content;
  }

  if (Array.isArray(payload?.data?.data)) {
    return payload.data.data;
  }

  if (Array.isArray(payload?.data?.data?.content)) {
    return payload.data.data.content;
  }

  if (Array.isArray(payload?.quotes)) {
    return payload.quotes;
  }

  return [];
}

function normalizeAggregateMarketsPayload(payload) {
  if (Array.isArray(payload)) {
    return payload;
  }

  const aggregate =
    payload?.data?.data && !Array.isArray(payload.data.data)
      ? payload.data.data
      : payload?.data && !Array.isArray(payload.data)
        ? payload.data
        : payload;

  const fxList = Array.isArray(aggregate?.fx) ? aggregate.fx : [];
  const cryptoList = Array.isArray(aggregate?.crypto) ? aggregate.crypto : [];
  const stockList = Array.isArray(aggregate?.stocks) ? aggregate.stocks : [];
  const fundList = Array.isArray(aggregate?.funds) ? aggregate.funds : [];
  const futuresList = Array.isArray(aggregate?.futures) ? aggregate.futures : [];
  const bondList = Array.isArray(aggregate?.bonds) ? aggregate.bonds : [];
  const indexList = Array.isArray(aggregate?.indexes) ? aggregate.indexes : [];
  const commodityList = Array.isArray(aggregate?.commodities) ? aggregate.commodities : [];

  return [
    ...fxList
      .filter((item) => item && typeof item === "object")
      .map(mapFxQuote),
    ...cryptoList,
    ...stockList,
    ...fundList,
    ...futuresList,
    ...bondList,
    ...indexList
      .filter((item) => item && typeof item === "object")
      .map(mapIndexQuote),
    ...commodityList
      .filter((item) => item && typeof item === "object")
      .map(mapCommodityQuote),
  ];
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
  return normalizeAggregateMarketsPayload(data);
}

export async function screenMarkets(params = {}) {
  const { data } = await axiosClient.get("/api/v1/markets/screen", { params });
  return normalizeObjectPayload(data);
}

export async function getMarketQuotes() {
  const [aggregateQuotes, indexQuotes, commodityQuotes] = await Promise.all([
    getMarkets(),
    getMarketsByType("INDEX"),
    getMarketsByType("COMMODITY"),
  ]);

  return mergeUniqueMarketQuotes(aggregateQuotes, indexQuotes, commodityQuotes);
}

export async function getMarketTapeQuotes() {
  const { data } = await axiosClient.get("/api/v1/markets/tape/quotes");
  return normalizeArrayPayload(data).map(mapMarketTapeQuote);
}

export async function getMarketTapeConfig() {
  const { data } = await axiosClient.get("/api/v1/markets/tape/config");
  return normalizeObjectPayload(data)?.symbols ?? [];
}

export async function getMarketBySymbol(symbol, options = {}) {
  const params = options?.type ? { type: options.type } : undefined;
  const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/${encodeURIComponent(symbol)}`, { params });
  return normalizeObjectPayload(data);
}

export async function getMarketQuote(symbol) {
  return getMarketBySymbol(symbol);
}

export async function getMarketsByType(type) {
  if (type === "FX") {
    const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/fx`);
    return normalizeArrayPayload(data).map(mapFxQuote);
  }

  if (type === "CRYPTO") {
    const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/crypto`);
    return normalizeArrayPayload(data).map(mapCryptoQuote);
  }

  if (type === "STOCK") {
    const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/stocks?page=0&size=500`);
    return normalizeArrayPayload(data).map(mapStockQuote);
  }

  if (type === "FUND") {
    const { data } = await axiosClient.get("/api/v1/funds");
    return normalizeArrayPayload(data).map(mapFundQuote);
  }

  if (type === "FUTURES") {
    const { data } = await axiosClient.get("/api/v1/futures");
    return normalizeArrayPayload(data).map(mapFuturesQuote);
  }

  if (type === "BOND") {
    const { data } = await axiosClient.get("/api/v1/bonds");
    return normalizeArrayPayload(data).map(mapBondQuote);
  }

  if (type === "INDEX") {
    const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/indexes`);
    return normalizeArrayPayload(data).map(mapIndexQuote);
  }

  if (type === "COMMODITY") {
    const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/commodities`);
    const COMMODITY_ORDER = [
      "GRAM_ALTIN", "CEYREK_ALTIN", "YARIM_ALTIN", "TAM_ALTIN",
      "CUMHURIYET_ALTINI", "GUMUS_GRAM", "BRENT",
    ];
    return normalizeArrayPayload(data)
      .map(mapCommodityQuote)
      .sort((a, b) => {
        const ai = COMMODITY_ORDER.indexOf(a.symbol);
        const bi = COMMODITY_ORDER.indexOf(b.symbol);
        const aOrder = ai === -1 ? 999 : ai;
        const bOrder = bi === -1 ? 999 : bi;
        return aOrder - bOrder;
      });
  }

  const { data } = await axiosClient.get(
    `${API_CONFIG.ENDPOINTS.markets}/type/${encodeURIComponent(type)}`,
  );
  return normalizeArrayPayload(data);
}

export async function getMarketHistory(symbol, paramsOrRange) {
  const params = {
    ...(typeof paramsOrRange === "object" && paramsOrRange?.type ? { type: paramsOrRange.type } : {}),
    ...(
    typeof paramsOrRange === "string"
      ? { range: paramsOrRange }
      : {
          from: paramsOrRange?.from,
          to: paramsOrRange?.to,
          period: paramsOrRange?.period,
          source: paramsOrRange?.source,
        }),
  };

  const url = `${API_CONFIG.ENDPOINTS.markets}/${encodeURIComponent(symbol)}/history`;
  const { data } = await axiosClient.get(url, {
    params,
  });
  return normalizeArrayPayload(data);
}

export function buildMarketDetailPath(symbol, instrumentType) {
  const normalizedType = normalizeInstrumentType(instrumentType);
  const query = normalizedType ? `?type=${encodeURIComponent(normalizedType)}` : "";
  return `/markets/${encodeURIComponent(symbol)}${query}`;
}

export async function getMacroHistory(symbol, params = {}) {
  const { data } = await axiosClient.get(
    `${API_CONFIG.ENDPOINTS.markets}/${encodeURIComponent(symbol)}/history`,
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

export async function searchInstruments(query, limit = 20, includeInternal = false) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/instruments/search`, {
    params: { q: query, limit, includeInternal },
  });
  return response.data?.data ?? [];
}

export async function getPriceOnDate(symbol, date) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/${encodeURIComponent(symbol)}/price-on-date`, {
    params: { date },
  });
  return response.data?.data ?? null;
}

function mapFxQuote(item) {
  const code = item.code ?? item.currencyCode ?? item.symbol;
  const source = item.source ?? item.sourceName;
  const instrumentSymbol = source === "TCMB" && code ? `TCMB:${String(code).toUpperCase()}:SELL` : code;
  const displayCode = code ? String(code).toUpperCase() : instrumentSymbol;

  return {
    symbol: instrumentSymbol,
    displayName: item.name ?? (source === "TCMB" && displayCode ? `${displayCode}/TRY` : displayCode),
    price: item.last ?? item.sellRate ?? item.sellingRate ?? item.sell ?? item.ask ?? item.buyRate ?? item.buyingRate ?? item.buy ?? item.bid,
    changeRate: item.changePercent ?? item.dailyChangePercent ?? item.changeRate ?? null,
    source,
    instrumentType: item.type || "FX",
    dataTimestamp: item.priceTimestamp ?? item.dataTimestamp,
    buyRate: item.buyRate ?? item.buyingRate ?? item.buy ?? item.alis ?? item.bid ?? null,
    sellRate: item.sellRate ?? item.sellingRate ?? item.sell ?? item.satis ?? item.ask ?? null,
    code: displayCode,
  };
}

function mapCryptoQuote(item) {
  return {
    symbol: item.symbol,
    displayName: item.displayName ?? item.symbol,
    price: item.price,
    changeRate: item.changeRate ?? item.dailyChangePercent ?? null,
    source: item.source,
    instrumentType: item.instrumentType || "CRYPTO",
    dataTimestamp: item.fetchedAt ?? item.dataTimestamp,
    buyRate: null,
    sellRate: null,
    code: item.symbol,
  };
}

function mapStockQuote(item) {
  return {
    symbol: item.symbol,
    displayName: item.companyName ?? item.symbol,
    price: item.price ?? item.currentPrice,
    changeRate: item.changePercent ?? null,
    source: item.sourceName,
    instrumentType: "STOCK",
    dataTimestamp: item.dataTimestamp,
    buyRate: null,
    sellRate: null,
    code: item.symbol,
    openPrice: item.openPrice ?? null,
  };
}

function mapFundQuote(item) {
  const currentNav = toNumericValue(item.navValue);
  const previousNav = toNumericValue(item.previousNavValue);
  const changeRate =
    currentNav !== null && previousNav !== null && previousNav !== 0
      ? ((currentNav - previousNav) / previousNav) * 100
      : null;

  return {
    symbol: item.fundCode,
    displayName: item.fundName ?? item.fundCode,
    price: item.navValue,
    changeRate,
    source: item.source ?? "TEFAS",
    instrumentType: "FUND",
    dataTimestamp: item.navDate,
    buyRate: null,
    sellRate: null,
    code: item.fundCode,
    previousNavValue: item.previousNavValue ?? null,
    fundType: item.fundType ?? null,
    fonTurAciklama: item.fonTurAciklama ?? item.fundType ?? null,
    riskDegeri: item.riskDegeri ?? null,
    getiri1a: item.getiri1a ?? null,
    getiri3a: item.getiri3a ?? null,
    getiri6a: item.getiri6a ?? null,
    getiriYb: item.getiriYb ?? null,
    getiri1y: item.getiri1y ?? null,
    getiri3y: item.getiri3y ?? null,
    getiri5y: item.getiri5y ?? null,
  };
}

function toNumericValue(value) {
  if (value === null || value === undefined || value === "") {
    return null;
  }

  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function normalizeInstrumentType(value) {
  if (value == null || value === "") {
    return "";
  }

  return String(value).trim().toUpperCase();
}

function mergeUniqueMarketQuotes(...quoteGroups) {
  const seen = new Set();
  const merged = [];

  for (const group of quoteGroups) {
    for (const item of Array.isArray(group) ? group : []) {
      if (!item || typeof item !== "object") {
        continue;
      }

      const normalizedSymbol = String(item.symbol ?? item.code ?? "").trim().toUpperCase();
      if (!normalizedSymbol) {
        continue;
      }

      const key = `${normalizeInstrumentType(item.instrumentType)}::${normalizedSymbol}`;
      if (seen.has(key)) {
        continue;
      }

      seen.add(key);
      merged.push(item);
    }
  }

  return merged;
}

function mapFuturesQuote(item) {
  return {
    symbol: item.contractCode,
    displayName: item.contractName ?? item.contractCode,
    price: item.lastPrice,
    changeRate: item.dailyChangePercent ?? null,
    source: item.source ?? "BIST",
    instrumentType: "FUTURES",
    dataTimestamp: item.dataTimestamp,
    buyRate: null,
    sellRate: null,
    code: item.contractCode,
  };
}

function mapBondQuote(item) {
  return {
    symbol: item.bondCode,
    displayName: item.bondName ?? item.bondCode,
    price: item.interestRate,
    changeRate: item.changeRate ?? null,
    source: item.source ?? "EVDS",
    instrumentType: "BOND",
    dataTimestamp: item.dataTimestamp,
    buyRate: null,
    sellRate: null,
    code: item.bondCode,
  };
}

function mapIndexQuote(item) {
  return {
    symbol: item.symbol,
    displayName: item.name ?? item.symbol,
    price: item.price,
    changeRate: item.changeRate ?? null,
    source: item.source ?? "YAHOO_FINANCE",
    instrumentType: item.instrumentType || "INDEX",
    dataTimestamp: item.updatedAt,
    buyRate: null,
    sellRate: null,
    code: item.symbol,
  };
}

function mapCommodityQuote(item) {
  return {
    symbol: item.symbol,
    displayName: item.name ?? item.symbol,
    price: item.price,
    changeRate: item.changeRate ?? null,
    source: item.source ?? "YAHOO_FINANCE",
    instrumentType: item.instrumentType || "COMMODITY",
    dataTimestamp: item.updatedAt,
    buyRate: null,
    sellRate: null,
    code: item.symbol,
  };
}

function mapMarketTapeQuote(item) {
  const symbol = item.symbol ?? item.code;
  const displayCode = buildTapeDisplayCode(item, symbol);

  return {
    symbol,
    displayName: item.displayName ?? item.name ?? symbol,
    price: item.price,
    changeRate: item.changeRate ?? item.changePercent ?? null,
    source: item.source,
    instrumentType: item.instrumentType,
    dataTimestamp: item.fetchedAt ?? item.dataTimestamp ?? item.updatedAt,
    buyRate: null,
    sellRate: null,
    code: displayCode,
  };
}

function buildTapeDisplayCode(item, symbol) {
  const type = normalizeInstrumentType(item.instrumentType);
  const value = String(symbol ?? "").toUpperCase();

  if (type === "FX" && value.includes(":")) {
    const parts = value.split(":");
    return parts.length >= 2 ? `${parts[1]}TRY` : value;
  }

  if (type === "COMMODITY" && value === "GRAM_ALTIN") {
    return "XAUTRY";
  }

  return value;
}



