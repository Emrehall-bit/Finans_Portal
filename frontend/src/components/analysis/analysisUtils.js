import {
  buildChartData,
  buildPresetRange,
  DEFAULT_INDICATORS,
  formatAxisNumber,
  formatChartDate,
  formatSignalLabel,
  formatTrendLabel,
} from "../market-detail/marketDetailUtils";

export const ANALYSIS_RANGE_PRESETS = [
  { key: "1M", days: 30 },
  { key: "3M", days: 90 },
  { key: "6M", days: 180 },
  { key: "1Y", days: 365 },
  { key: "MAX", days: 3650 },
];

export { buildChartData, buildPresetRange, DEFAULT_INDICATORS, formatAxisNumber, formatChartDate, formatSignalLabel, formatTrendLabel };

export function buildComparisonData(series = []) {
  const grouped = new Map();

  series.forEach((item) => {
    item?.points?.forEach((point) => {
      const key = formatChartDate(point.date);
      if (!grouped.has(key)) {
        grouped.set(key, { date: key });
      }

      grouped.get(key)[item.symbol] = toNumeric(point.normalizedValue);
    });
  });

  return [...grouped.values()];
}

function toNumeric(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}
