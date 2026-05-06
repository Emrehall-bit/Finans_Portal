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
  { key: "1M", label: "1A", days: 30 },
  { key: "3M", label: "3A", days: 90 },
  { key: "6M", label: "6A", days: 180 },
  { key: "1Y", label: "1Y", days: 365 },
  { key: "MAX", label: "MAX", days: 3650 },
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
