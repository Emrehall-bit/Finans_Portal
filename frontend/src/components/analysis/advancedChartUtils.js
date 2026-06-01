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

export function toneFromRsi(rsi) {
  if (rsi == null) {
    return "neutral";
  }
  if (rsi >= 70 || rsi <= 30) {
    return "warning";
  }
  return "neutral";
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
