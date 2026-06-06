import { formatDateTime, formatNumber } from "../../utils/formatters";
import i18n from "../../i18n";

export const RANGE_PRESETS = [
  { key: "1M", months: 1 },
  { key: "3M", months: 3 },
  { key: "6M", months: 6 },
  { key: "1Y", years: 1 },
  { key: "MAX", years: 10 },
];

export const DEFAULT_INDICATORS = ["SMA7", "SMA20", "SMA50", "RSI14"];

const FX_PROVIDER_SYMBOL_PATTERN = /^[A-Z_]+:[A-Z]{2,4}:(BUY|SELL)$/;

export function resolveInstrumentSymbols(symbol, instrumentType) {
  const rawSymbol = String(symbol || "").trim();
  const normalizedType = String(instrumentType || "").trim().toUpperCase();

  if (!rawSymbol) {
    return {
      displaySymbol: "",
      apiSymbol: "",
    };
  }

  if (normalizedType !== "FX") {
    return {
      displaySymbol: rawSymbol,
      apiSymbol: rawSymbol,
    };
  }

  const upperSymbol = rawSymbol.toUpperCase();
  if (FX_PROVIDER_SYMBOL_PATTERN.test(upperSymbol)) {
    return {
      displaySymbol: upperSymbol.split(":")[1] || rawSymbol,
      apiSymbol: upperSymbol,
    };
  }

  return {
    displaySymbol: upperSymbol,
    apiSymbol: `TCMB:${upperSymbol}:SELL`,
  };
}

export function buildPresetRange(preset) {
  const to = new Date();
  const from = new Date(to);

  if (typeof preset === "number") {
    from.setDate(from.getDate() - preset);
  } else if (preset?.months) {
    from.setMonth(from.getMonth() - preset.months);
  } else if (preset?.years) {
    from.setFullYear(from.getFullYear() - preset.years);
  } else if (preset?.days) {
    from.setDate(from.getDate() - preset.days);
  } else {
    from.setMonth(from.getMonth() - 3);
  }

  return {
    from: toIsoDate(from),
    to: toIsoDate(to),
  };
}

export function toIsoDate(value) {
  return value.toISOString().slice(0, 10);
}

export function toNumeric(value) {
  if (value === null || value === undefined || value === "") {
    return null;
  }

  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : numeric;
}

export function formatMarketChange(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }

  return `${numeric >= 0 ? "+" : ""}${numeric.toFixed(2)}%`;
}

export function formatAxisNumber(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "-";
  }

  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

export function formatChartDate(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export function formatFullChartDate(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return date.toLocaleDateString(i18n.resolvedLanguage || i18n.language || undefined, {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

function toChartDateKey(value) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return toIsoDate(date);
}

export function formatTrendLabel(value) {
  if (!value) {
    return "-";
  }

  return {
    UPTREND: i18n.t("trend.uptrend"),
    DOWNTREND: i18n.t("trend.downtrend"),
    SIDEWAYS: i18n.t("trend.sideways"),
  }[value] ?? value;
}

export function resolveTrendDirection(fallbackTrend, chartData = []) {
  const prices = (Array.isArray(chartData) ? chartData : [])
    .map((point) => toNumeric(point?.close))
    .filter((value) => value !== null);

  if (prices.length < 2 || prices[0] === 0) {
    return fallbackTrend;
  }

  const firstPrice = prices[0];
  const lastPrice = prices[prices.length - 1];
  const changePercent = ((lastPrice - firstPrice) / Math.abs(firstPrice)) * 100;

  if (changePercent > 1) {
    return "UPTREND";
  }

  if (changePercent < -1) {
    return "DOWNTREND";
  }

  return "SIDEWAYS";
}

export function formatSignalLabel(value) {
  return {
    PRICE_ABOVE_SMA20: i18n.t("signals.PRICE_ABOVE_SMA20"),
    PRICE_BELOW_SMA20: i18n.t("signals.PRICE_BELOW_SMA20"),
    SMA7_ABOVE_SMA20: i18n.t("signals.SMA7_ABOVE_SMA20"),
    SMA7_BELOW_SMA20: i18n.t("signals.SMA7_BELOW_SMA20"),
    RSI_OVERBOUGHT: i18n.t("signals.RSI_OVERBOUGHT"),
    RSI_OVERSOLD: i18n.t("signals.RSI_OVERSOLD"),
    RSI_NEUTRAL: i18n.t("signals.RSI_NEUTRAL"),
  }[value] ?? value;
}

export function buildChartData(points = [], history = []) {
  const analysisPoints = Array.isArray(points) ? points : [];
  const historyPoints = Array.isArray(history) ? history : [];
  const historyByDate = new Map(historyPoints.map((point) => [
    toChartDateKey(point?.priceTimestamp ? String(point.priceTimestamp) : null),
    point,
  ]));

  if (analysisPoints.length > 0) {
    return analysisPoints
      .map((point) => {
        const dateKey = toChartDateKey(point?.date);
        const historyPoint = historyByDate.get(dateKey);
        return {
          date: formatChartDate(point?.date),
          dateKey,
          fullDate: formatFullChartDate(point?.date),
          open: toNumeric(historyPoint?.openPrice),
          high: toNumeric(historyPoint?.highPrice),
          low: toNumeric(historyPoint?.lowPrice),
          close: toNumeric(point?.close),
          sma7: toNumeric(point?.sma7),
          sma20: toNumeric(point?.sma20),
          sma50: toNumeric(point?.sma50),
          rsi14: toNumeric(point?.rsi14),
        };
      })
      .filter((point) => point.close !== null);
  }

  if (historyPoints.length > 0) {
    return historyPoints
      .map((point) => {
        const rawDate = point?.priceTimestamp ? String(point.priceTimestamp) : null;
        return {
          date: formatChartDate(rawDate),
          dateKey: toChartDateKey(rawDate),
          fullDate: formatFullChartDate(rawDate),
          open: toNumeric(point?.openPrice),
          high: toNumeric(point?.highPrice),
          low: toNumeric(point?.lowPrice),
          close: toNumeric(point?.closePrice),
          sma7: null,
          sma20: null,
          sma50: null,
          rsi14: null,
        };
      })
      .filter((point) => point.close !== null);
  }

  return [];
}

export function buildStats(quote, annualHistory = []) {
  const priceTime = quote?.priceTime ?? quote?.fetchedAt ?? null;
  const annualRange = computeAnnualRange(annualHistory);
  const latestHistoryPoint = getLatestHistoryPoint(annualHistory);
  const openPrice = firstNumeric(
    quote?.openPrice,
    quote?.open,
    quote?.openingPrice,
    latestHistoryPoint?.openPrice,
  );
  const volume = firstNumeric(
    quote?.volume,
    quote?.totalVolume,
    latestHistoryPoint?.volume,
  );

  return [
    { label: i18n.t("stats.lastPrice"), value: formatNumber(quote?.price), tone: "neutral" },
    { label: i18n.t("stats.dailyChange"), value: formatMarketChange(quote?.changeRate), tone: changeTone(quote?.changeRate) },
    { label: i18n.t("stats.open"), value: openPrice === null ? "-" : formatNumber(openPrice), tone: openPrice === null ? "muted" : "neutral" },
    { label: i18n.t("stats.volume"), value: volume === null ? "-" : formatNumber(volume, 0), tone: volume === null ? "muted" : "neutral" },
    { label: i18n.t("stats.yearRange"), value: annualRange, tone: "neutral" },
    { label: i18n.t("stats.lastUpdate"), value: formatDateTime(priceTime), tone: "neutral" },
  ];
}

function firstNumeric(...values) {
  for (const value of values) {
    const numeric = toNumeric(value);
    if (numeric !== null) {
      return numeric;
    }
  }

  return null;
}

function getLatestHistoryPoint(history = []) {
  if (!Array.isArray(history) || history.length === 0) {
    return null;
  }

  return [...history]
    .filter((item) => item?.priceTimestamp)
    .sort((left, right) => new Date(right.priceTimestamp).getTime() - new Date(left.priceTimestamp).getTime())[0] ?? history.at(-1);
}

function computeAnnualRange(history = []) {
  if (!Array.isArray(history) || history.length === 0) {
    return "-";
  }

  const prices = history
    .map((item) => toNumeric(item.closePrice))
    .filter((value) => value !== null);

  if (prices.length === 0) {
    return "-";
  }

  return `${formatAxisNumber(Math.min(...prices))} - ${formatAxisNumber(Math.max(...prices))}`;
}

function changeTone(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "muted";
  }

  if (numeric > 0) {
    return "positive";
  }

  if (numeric < 0) {
    return "negative";
  }

  return "neutral";
}
