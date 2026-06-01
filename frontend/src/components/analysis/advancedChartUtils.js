import { formatSignalLabel } from "./analysisUtils";

// Data normalization
export function normalizeCandles(candles) {
  if (!Array.isArray(candles)) {
    return [];
  }

  const deduped = new Map();

  candles.forEach((candle) => {
    const time = Number(candle?.timestamp);
    const open = toFiniteNumber(candle?.open);
    const high = toFiniteNumber(candle?.high);
    const low = toFiniteNumber(candle?.low);
    const close = toFiniteNumber(candle?.close);
    const volume = toFiniteNumber(candle?.volume);
    const rsi14 = toFiniteNumber(candle?.rsi14);
    const prev = deduped.get(time);
    const previousClose = prev?.close ?? null;

    if (!Number.isInteger(time) || time <= 0) {
      return;
    }
    if ([open, high, low, close].some((value) => value == null)) {
      return;
    }

    deduped.set(time, {
      time,
      dateLabel: formatTooltipDate(time),
      open,
      high,
      low,
      close,
      volume,
      sma7: toPositiveOverlayNumber(candle?.sma7),
      sma20: toPositiveOverlayNumber(candle?.sma20),
      sma50: toPositiveOverlayNumber(candle?.sma50),
      rsi14,
      changePct: previousClose ? ((close - previousClose) / previousClose) * 100 : null,
    });
  });

  const sorted = Array.from(deduped.values()).sort((left, right) => left.time - right.time);
  return sorted.map((row, index) => ({
    ...row,
    changePct: index > 0 ? ((row.close - sorted[index - 1].close) / sorted[index - 1].close) * 100 : row.changePct,
  }));
}

export function normalizeLinePoints(points) {
  return points
    .filter((point) => point?.date && point?.close != null)
    .map((point, index, source) => {
      const time = toEpochSeconds(point.date);
      const previous = index > 0 ? source[index - 1] : null;
      const previousClose = previous?.close != null ? Number(previous.close) : null;
      return {
        time,
        dateLabel: point.date,
        open: null,
        high: null,
        low: null,
        close: Number(point.close),
        volume: null,
        sma7: toPositiveOverlayNumber(point.sma7),
        sma20: toPositiveOverlayNumber(point.sma20),
        sma50: toPositiveOverlayNumber(point.sma50),
        rsi14: toFiniteNumber(point.rsi14),
        changePct: previousClose ? ((Number(point.close) - previousClose) / previousClose) * 100 : null,
      };
    });
}

export function normalizeHistoryPoints(history) {
  return (Array.isArray(history) ? history : [])
    .map((point, index, source) => {
      const rawDate = point?.priceTimestamp ? String(point.priceTimestamp) : null;
      const close = toFiniteNumber(point?.closePrice);
      if (!rawDate || close == null) {
        return null;
      }

      const formattedDate = rawDate.slice(0, 10);
      const time = toEpochSeconds(formattedDate);
      const previousClose = index > 0 ? toFiniteNumber(source[index - 1]?.closePrice) : null;

      return {
        time,
        dateLabel: formattedDate,
        open: null,
        high: null,
        low: null,
        close,
        volume: null,
        sma7: null,
        sma20: null,
        sma50: null,
        rsi14: null,
        changePct: previousClose ? ((close - previousClose) / previousClose) * 100 : null,
      };
    })
    .filter(Boolean);
}

// Indicator calculations
export function computeEmaSeries(values, period) {
  const normalized = values.map((value) => toFiniteNumber(value));
  if (!normalized.length) {
    return [];
  }

  const multiplier = 2 / (period + 1);
  let previousEma = null;

  return normalized.map((value, index) => {
    if (value == null) {
      return null;
    }
    if (index < period - 1) {
      return null;
    }
    if (index === period - 1) {
      const seed = normalized.slice(0, period);
      if (seed.some((item) => item == null)) {
        return null;
      }
      previousEma = seed.reduce((sum, item) => sum + item, 0) / period;
      return previousEma;
    }

    previousEma = ((value - previousEma) * multiplier) + previousEma;
    return previousEma;
  });
}

export function computeSimpleMovingAverageSeries(values, period) {
  const normalized = values.map((v) => toFiniteNumber(v));
  const result = new Array(normalized.length).fill(null);
  let windowSum = 0;
  let validCount = 0;

  for (let i = 0; i < normalized.length; i++) {
    const val = normalized[i];
    if (val != null) { windowSum += val; validCount++; }
    if (i >= period) {
      const dropping = normalized[i - period];
      if (dropping != null) { windowSum -= dropping; validCount--; }
    }
    if (i >= period - 1 && validCount === period) {
      result[i] = windowSum / period;
    }
  }
  return result;
}

export function computeBollingerSeries(values, period, multiplier) {
  const normalized = values.map((v) => toFiniteNumber(v));
  const middle = computeSimpleMovingAverageSeries(normalized, period);
  const upper = new Array(normalized.length).fill(null);
  const lower = new Array(normalized.length).fill(null);
  let sumSq = 0;
  let validCount = 0;

  for (let i = 0; i < normalized.length; i++) {
    const val = normalized[i];
    if (val != null) { sumSq += val * val; validCount++; }
    if (i >= period) {
      const dropping = normalized[i - period];
      if (dropping != null) { sumSq -= dropping * dropping; validCount--; }
    }
    if (i >= period - 1 && validCount === period && middle[i] != null) {
      const sd = Math.sqrt(Math.max(0, sumSq / period - middle[i] * middle[i])) * multiplier;
      upper[i] = middle[i] + sd;
      lower[i] = middle[i] - sd;
    }
  }
  return { upper, middle, lower };
}

// Formatting and tone helpers
export function normalizeTrendDirection(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized || null;
}

export function resolveRsiThresholds(instrumentType) {
  return String(instrumentType || "").trim().toUpperCase() === "CRYPTO"
    ? { overbought: 75, oversold: 25 }
    : { overbought: 70, oversold: 30 };
}

export function resolveMomentumThreshold(instrumentType) {
  switch (String(instrumentType || "").trim().toUpperCase()) {
    case "FX":
    case "FUND":
      return 0.5;
    case "CRYPTO":
      return 3.0;
    default:
      return 1.0;
  }
}

export function resolveQuoteLatestPrice(quote) {
  const candidates = [
    quote?.latestPrice,
    quote?.sellRate,
    quote?.price,
    quote?.last,
    quote?.currentPrice,
  ];

  for (const value of candidates) {
    const numeric = toFiniteNumber(value);
    if (numeric != null && numeric > 0) {
      return numeric;
    }
  }
  return null;
}

const SELECTED_RANGE_TREND_THRESHOLDS = {
  STOCK: {
    "1M": [-15, -8, -3, 3, 8, 15],
    "3M": [-25, -15, -5, 5, 15, 25],
    "6M": [-35, -20, -8, 8, 20, 35],
    "1Y": [-50, -30, -10, 10, 30, 50],
    MAX: [-70, -40, -15, 15, 50, 100],
  },
  FUND: {
    "1M": [-10, -5, -2, 2, 5, 10],
    "3M": [-15, -8, -3, 3, 8, 15],
    "6M": [-20, -10, -4, 4, 12, 25],
    "1Y": [-30, -15, -5, 5, 15, 35],
    MAX: [-50, -25, -10, 10, 25, 60],
  },
  FX: {
    "1M": [-8, -4, -1.5, 1.5, 4, 8],
    "3M": [-15, -8, -3, 3, 8, 15],
    "6M": [-20, -12, -5, 5, 12, 20],
    "1Y": [-30, -18, -8, 8, 18, 30],
    MAX: [-50, -30, -10, 10, 30, 60],
  },
  CRYPTO: {
    "1M": [-25, -12, -5, 5, 12, 25],
    "3M": [-35, -18, -8, 8, 20, 35],
    "6M": [-50, -25, -10, 10, 25, 50],
    "1Y": [-65, -35, -15, 15, 35, 75],
    MAX: [-80, -50, -20, 20, 50, 150],
  },
  COMMODITY: {
    "1M": [-12, -6, -2, 2, 6, 12],
    "3M": [-20, -10, -4, 4, 10, 20],
    "6M": [-30, -15, -6, 6, 15, 30],
    "1Y": [-40, -20, -8, 8, 20, 40],
    MAX: [-60, -35, -12, 12, 35, 80],
  },
  INDEX: {
    "1M": [-10, -5, -2, 2, 6, 10],
    "3M": [-18, -10, -4, 4, 12, 18],
    "6M": [-25, -15, -6, 6, 18, 25],
    "1Y": [-35, -20, -8, 8, 25, 40],
    MAX: [-55, -30, -12, 12, 40, 80],
  },
  BOND: {
    "1M": [-5, -2.5, -1, 1, 3, 5],
    "3M": [-8, -4, -2, 2, 5, 8],
    "6M": [-12, -6, -3, 3, 8, 12],
    "1Y": [-18, -8, -4, 4, 10, 18],
    MAX: [-30, -15, -6, 6, 15, 30],
  },
};

export function resolveSelectedRangeTrendThresholds(instrumentType, activeRange) {
  const typeKey = normalizeSelectedRangeType(instrumentType);
  const rangeKey = normalizeSelectedRangeKey(activeRange);
  return SELECTED_RANGE_TREND_THRESHOLDS[typeKey]?.[rangeKey]
    ?? SELECTED_RANGE_TREND_THRESHOLDS.STOCK[rangeKey]
    ?? SELECTED_RANGE_TREND_THRESHOLDS.STOCK["1M"];
}

export function resolveSelectedRangePerformance(rows, instrumentType, activeRange) {
  const points = (Array.isArray(rows) ? rows : [])
    .map((row) => ({
      row,
      close: toFiniteNumber(row?.close ?? row),
    }))
    .filter((point) => point.close != null && point.close > 0);

  if (points.length < 2) {
    return {
      changePct: null,
      totalChangePct: null,
      stateKey: "neutral",
      tone: "neutral",
      trendDirection: "SIDEWAYS",
    };
  }

  const firstPoint = points[0];
  const lastPoint = points.at(-1);
  const first = firstPoint.close;
  const last = lastPoint.close;
  const totalChangePct = ((last - first) / Math.abs(first)) * 100;
  const thresholds = resolveSelectedRangeTrendThresholds(instrumentType, activeRange);
  const state = resolveSelectedRangeState(totalChangePct, thresholds);
  return buildSelectedRangePerformanceResult({ totalChangePct, ...state });
}

function resolveSelectedRangeState(changePct, thresholds) {
  const [strongBearishMax, bearishMax, weakBearishMax, weakBullishMin, bullishMin, strongBullishMin] = thresholds;

  if (changePct <= strongBearishMax) {
    return { stateKey: "strongBearish", tone: "negative", trendDirection: "DOWNTREND" };
  }
  if (changePct < bearishMax) {
    return { stateKey: "bearish", tone: "negative", trendDirection: "DOWNTREND" };
  }
  if (changePct < weakBearishMax) {
    return { stateKey: "weakBearish", tone: "negative", trendDirection: "DOWNTREND" };
  }
  if (changePct <= weakBullishMin) {
    return { stateKey: "neutral", tone: "neutral", trendDirection: "SIDEWAYS" };
  }
  if (changePct < bullishMin) {
    return { stateKey: "weakBullish", tone: "positive", trendDirection: "UPTREND" };
  }
  if (changePct < strongBullishMin) {
    return { stateKey: "bullish", tone: "positive", trendDirection: "UPTREND" };
  }
  return { stateKey: "strongBullish", tone: "positive", trendDirection: "UPTREND" };
}

function buildSelectedRangePerformanceResult({ totalChangePct, stateKey, tone, trendDirection }) {
  return {
    changePct: totalChangePct,
    totalChangePct,
    stateKey,
    tone,
    trendDirection,
  };
}

function normalizeSelectedRangeType(instrumentType) {
  const normalized = String(instrumentType || "").trim().toUpperCase();
  if (normalized === "FUTURES") return "STOCK";
  return SELECTED_RANGE_TREND_THRESHOLDS[normalized] ? normalized : "STOCK";
}

function normalizeSelectedRangeKey(activeRange) {
  const normalized = String(activeRange || "").trim().toUpperCase();
  return ["1M", "3M", "6M", "1Y", "MAX"].includes(normalized) ? normalized : "1M";
}

export function toneFromRsi(rsi, instrumentType) {
  if (rsi == null) {
    return "neutral";
  }
  const thresholds = resolveRsiThresholds(instrumentType);
  if (rsi >= thresholds.overbought || rsi <= thresholds.oversold) {
    return "warning";
  }
  return "neutral";
}

export function closeToCloseVolatility(closes, activeRange) {
  const returns = [];
  for (let i = 1; i < closes.length; i++) {
    const ret = derivePercentChange(closes[i - 1], closes[i]);
    if (ret != null) returns.push(ret);
  }
  if (returns.length < 3) return null;
  const n = Math.min({ "1M": 10, "3M": 20, "6M": 30, "1Y": 60, "MAX": 60, "1m": 10, "3m": 20, "6m": 30, "1y": 60, max: 60 }[activeRange] ?? 20, returns.length);
  const window = returns.slice(-n);
  const mean = window.reduce((sum, r) => sum + r, 0) / window.length;
  const variance = window.reduce((sum, r) => sum + (r - mean) ** 2, 0) / window.length;
  return Math.sqrt(variance);
}

function derivePercentChange(from, to) {
  const start = Number(from);
  const end = Number(to);
  if (!Number.isFinite(start) || !Number.isFinite(end) || start === 0) {
    return null;
  }
  return ((end - start) / Math.abs(start)) * 100;
}

export function toneFromSignal(tone) {
  return tone ?? "neutral";
}

export function normalizeSignalDescriptor(value) {
  if (!value) {
    return null;
  }
  if (typeof value === "object" && value.shortLabel && value.text) {
    return value;
  }
  const label = String(value).trim();
  if (!label) {
    return null;
  }
  const formattedLabel = formatSignalLabel(label);
  return {
    shortLabel: formattedLabel,
    text: formattedLabel,
    tone: /buy|bull|up|long/i.test(label)
      ? "positive"
      : /sell|bear|down|short/i.test(label)
        ? "negative"
        : "neutral",
  };
}

export function trendTone(direction) {
  switch (direction) {
    case "UPTREND":
      return "positive";
    case "DOWNTREND":
      return "negative";
    default:
      return "neutral";
  }
}

export function formatCompactPrice(value) {
  return Number(value).toFixed(Math.abs(value) >= 100 ? 2 : 4);
}

export function toFiniteNumber(value) {
  if (value == null) return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

export function toPositiveOverlayNumber(value) {
  const numeric = toFiniteNumber(value);
  return numeric != null && numeric > 0 ? numeric : null;
}

export function normalizeChartTime(time) {
  if (typeof time === "number") {
    return time;
  }
  if (time && typeof time === "object" && "year" in time) {
    return Math.floor(Date.UTC(time.year, time.month - 1, time.day) / 1000);
  }
  return null;
}

export function toEpochSeconds(date) {
  return Math.floor(new Date(`${date}T00:00:00Z`).getTime() / 1000);
}

export function formatTooltipDate(epochSeconds) {
  const date = new Date(epochSeconds * 1000);
  return date.toLocaleDateString("tr-TR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

const CHART_INDICATOR_LABELS = { sma7: "MA 7", sma20: "MA 20", sma50: "MA 50", rsi14: "RSI 14" };

/**
 * chartData dizisinde tüm noktaları null olan indikatörlerin display isimlerini döner.
 * Backend yeterli veri olmadığında o pozisyonları null bırakır; tüm noktalar null ise
 * seçili aralıkta o indikatör üretilememiş demektir.
 */
export function detectInsufficientChartIndicators(chartData) {
  if (!Array.isArray(chartData) || chartData.length === 0) return [];
  return Object.entries(CHART_INDICATOR_LABELS)
    .filter(([field]) => chartData.every((point) => point?.[field] == null))
    .map(([, label]) => label);
}
