import { formatDateTime, formatNumber } from "../../utils/formatters";

export const RANGE_PRESETS = [
  { key: "1D", label: "1G", days: 1 },
  { key: "1W", label: "1H", days: 7 },
  { key: "1M", label: "1A", days: 30 },
  { key: "3M", label: "3A", days: 90 },
  { key: "1Y", label: "1Y", days: 365 },
  { key: "MAX", label: "MAX", days: 3650 },
];

export const DEFAULT_INDICATORS = ["SMA7", "SMA20", "SMA50", "RSI14"];

export function buildPresetRange(days) {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - days);

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

export function formatTrendLabel(value) {
  if (!value) {
    return "-";
  }

  return {
    UPTREND: "Yukselis",
    DOWNTREND: "Dusus",
    SIDEWAYS: "Yatay",
  }[value] ?? value;
}

export function formatSignalLabel(value) {
  return {
    PRICE_ABOVE_SMA20: "Fiyat > SMA20",
    PRICE_BELOW_SMA20: "Fiyat < SMA20",
    SMA7_ABOVE_SMA20: "MA7 > MA20",
    SMA7_BELOW_SMA20: "MA7 < MA20",
    RSI_OVERBOUGHT: "RSI asiri alim",
    RSI_OVERSOLD: "RSI asiri satim",
    RSI_NEUTRAL: "RSI dengeli",
  }[value] ?? value;
}

export function buildChartData(points = [], history = []) {
  const analysisPoints = Array.isArray(points) ? points : [];
  const historyPoints = Array.isArray(history) ? history : [];

  if (historyPoints.length > 0) {
    const indicatorsByDate = new Map(
      analysisPoints
        .filter((point) => point?.date)
        .map((point) => [
          String(point.date),
          {
            sma7: toNumeric(point?.sma7),
            sma20: toNumeric(point?.sma20),
            sma50: toNumeric(point?.sma50),
            rsi14: toNumeric(point?.rsi14),
          },
        ]),
    );

    return historyPoints
      .map((point) => {
        const rawDate = point?.priceDate ? String(point.priceDate) : null;
        const indicatorValues = rawDate ? indicatorsByDate.get(rawDate) : null;
        return {
          date: formatChartDate(rawDate),
          close: toNumeric(point?.closePrice),
          sma7: indicatorValues?.sma7 ?? null,
          sma20: indicatorValues?.sma20 ?? null,
          sma50: indicatorValues?.sma50 ?? null,
          rsi14: indicatorValues?.rsi14 ?? null,
        };
      })
      .filter((point) => point.close !== null);
  }

  return analysisPoints
    .map((point) => ({
      date: formatChartDate(point?.date),
      close: toNumeric(point?.close),
      sma7: toNumeric(point?.sma7),
      sma20: toNumeric(point?.sma20),
      sma50: toNumeric(point?.sma50),
      rsi14: toNumeric(point?.rsi14),
    }))
    .filter((point) => point.close !== null);
}

export function buildStats(quote, annualHistory = []) {
  const priceTime = quote?.priceTime ?? quote?.fetchedAt ?? null;
  const annualRange = computeAnnualRange(annualHistory);

  // TODO: expose open, volume and 52-week summary fields from backend market quote endpoint.
  return [
    { label: "Son fiyat", value: formatNumber(quote?.price), tone: "neutral" },
    { label: "Gunluk degisim", value: formatMarketChange(quote?.changeRate), tone: changeTone(quote?.changeRate) },
    { label: "Acilis", value: "-", tone: "muted" },
    { label: "Hacim", value: "-", tone: "muted" },
    { label: "52 hafta araligi", value: annualRange, tone: "neutral" },
    { label: "Veri kaynagi", value: quote?.source || "-", tone: "neutral" },
    { label: "Son guncelleme", value: formatDateTime(priceTime), tone: "neutral" },
  ];
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
