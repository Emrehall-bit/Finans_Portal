import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { useTheme } from "../../theme/ThemeContext";
import { buildComparisonData, formatAxisNumber } from "./analysisUtils";

const COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2"];

const MACRO_PRESETS = [
  { code: "CPI_TR", type: "MACRO", labelKey: "analysis.benchmark.presets.tufe" },
];

const BENCHMARK_TYPES = ["MACRO", "INDEX", "INSTRUMENT", "SECTOR"];

// Display modes available per reference type
const TYPE_MODES = {
  MACRO:      [{ key: "benchmark", labelKey: "analysis.comparison.benchmark" }, { key: "normalized", labelKey: "analysis.comparison.normalized" }],
  INDEX:      [{ key: "normalized", labelKey: "analysis.comparison.normalized" }, { key: "benchmark", labelKey: "analysis.comparison.benchmark" }],
  INSTRUMENT: [{ key: "normalized", labelKey: "analysis.comparison.normalized" }, { key: "price", labelKey: "analysis.comparison.price" }, { key: "benchmark", labelKey: "analysis.comparison.benchmark" }],
  SECTOR:     [],
};

export default function AnalysisComparisonPanel({
  benchmarkData = null,
  benchmarkLoading = false,
  benchmarkError = "",
  benchmarkCode = "CPI_TR",
  benchmarkType = "MACRO",
  onBenchmarkChange,
  comparison = null,
  loading = false,
  error = "",
  mode = "benchmark",
  onModeChange,
  primarySymbol = "",
  primaryQuote = null,
  quotes = [],
}) {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const [searchTerm, setSearchTerm] = useState("");

  const isPrimaryStock = String(primaryQuote?.instrumentType || "").toUpperCase() === "STOCK";
  const isSector = benchmarkType === "SECTOR";
  const isMacro = benchmarkType === "MACRO";

  // In price mode + INSTRUMENT type, use comparison (multi-symbol) data for chart
  const useInstrumentComparison = mode === "price" && benchmarkType === "INSTRUMENT" && (comparison?.series?.length ?? 0) >= 2;
  const activeSeries = useInstrumentComparison ? (comparison?.series ?? []) : (benchmarkData?.series ?? []);
  const chartData = useMemo(
    () => buildComparisonData(activeSeries, useInstrumentComparison ? "price" : "normalized"),
    [activeSeries, useInstrumentComparison],
  );

  const isChartLoading = useInstrumentComparison ? loading : benchmarkLoading;
  const chartError = useInstrumentComparison ? error : benchmarkError;
  const hasChart = !isChartLoading && !chartError && chartData.length >= 2;

  const effectiveReturn = isMacro ? benchmarkData?.realReturn : benchmarkData?.relativeReturn;
  const status = benchmarkData?.status ?? null;

  const instrumentOptions = useMemo(
    () => buildInstrumentOptions(quotes, primarySymbol, searchTerm),
    [quotes, primarySymbol, searchTerm],
  );
  const indexOptions = useMemo(() => buildIndexOptions(quotes), [quotes]);

  const summaryTitle = useMemo(
    () => buildComparisonTitle(primarySymbol, benchmarkType, benchmarkCode, quotes, t),
    [primarySymbol, benchmarkType, benchmarkCode, quotes, t],
  );

  const displayModes = TYPE_MODES[benchmarkType] ?? [];
  // Ensure active mode is valid for current type
  const activeMode = displayModes.some((dm) => dm.key === mode) ? mode : (displayModes[0]?.key ?? "normalized");

  function handleTypeChange(type) {
    if (type === "SECTOR" && !isPrimaryStock) return;
    const next = resolveDefaultBenchmark(type, indexOptions);
    onBenchmarkChange?.(next.code, next.type);
  }

  return (
    <div className="comparison-workspace">
      {/* Controls */}
      <div className="comparison-workspace-controls">
        <div className="comparison-control-section">
          <span className="comparison-control-label">{t("analysis.benchmark.referenceType")}</span>
          <div className="comparison-control-chips">
            {BENCHMARK_TYPES.map((type) => {
              const disabled = type === "SECTOR" && !isPrimaryStock;
              return (
                <button
                  key={type}
                  type="button"
                  disabled={disabled}
                  className={`analysis-benchmark-chip${benchmarkType === type ? " active" : ""}${disabled ? " comparison-chip-disabled" : ""}`}
                  onClick={() => handleTypeChange(type)}
                  title={disabled ? t("analysis.benchmark.sectorOnly") : undefined}
                >
                  {t(`analysis.benchmark.types.${type}`)}
                </button>
              );
            })}
          </div>
          {isSector && !isPrimaryStock ? (
            <p className="comparison-type-note">{t("analysis.benchmark.sectorOnly")}</p>
          ) : null}
        </div>

        {!isSector ? (
          <div className="comparison-control-section">
            <span className="comparison-control-label">{t("analysis.benchmark.reference")}</span>
            {isMacro ? (
              <div className="comparison-control-chips">
                {MACRO_PRESETS.map((preset) => (
                  <button
                    key={preset.code}
                    type="button"
                    className={`analysis-benchmark-chip${benchmarkCode === preset.code ? " active" : ""}`}
                    onClick={() => onBenchmarkChange?.(preset.code, preset.type)}
                  >
                    {t(preset.labelKey)}
                  </button>
                ))}
              </div>
            ) : null}
            {benchmarkType === "INDEX" ? (
              <div className="comparison-control-chips">
                {indexOptions.map((preset) => (
                  <button
                    key={preset.code}
                    type="button"
                    className={`analysis-benchmark-chip${benchmarkCode === preset.code ? " active" : ""}`}
                    onClick={() => onBenchmarkChange?.(preset.code, preset.type)}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            ) : null}
            {benchmarkType === "INSTRUMENT" ? (
              <div className="analysis-benchmark-search comparison-instrument-search">
                <input
                  type="search"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder={t("analysis.benchmark.searchPlaceholder")}
                />
                <div className="analysis-benchmark-search-results">
                  {instrumentOptions.map((item) => (
                    <button
                      key={item.symbol}
                      type="button"
                      className={`analysis-benchmark-option${benchmarkCode === item.symbol ? " active" : ""}`}
                      onClick={() => onBenchmarkChange?.(item.symbol, "INSTRUMENT")}
                    >
                      <strong>{item.code}</strong>
                      <span>{item.name}</span>
                    </button>
                  ))}
                  {instrumentOptions.length === 0 ? (
                    <span className="analysis-benchmark-empty-inline">{t("analysis.benchmark.noReferenceResults")}</span>
                  ) : null}
                </div>
              </div>
            ) : null}
          </div>
        ) : null}

        {displayModes.length > 0 ? (
          <div className="comparison-control-section">
            <span className="comparison-control-label">{t("analysis.comparison.modeLabel")}</span>
            <div className="comparison-control-chips">
              {displayModes.map((dm) => (
                <button
                  key={dm.key}
                  type="button"
                  className={`analysis-benchmark-chip${activeMode === dm.key ? " active" : ""}`}
                  onClick={() => onModeChange?.(dm.key)}
                >
                  {t(dm.labelKey)}
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </div>

      {/* Body */}
      {isSector ? (
        <div className="comparison-sector-gate">
          <p className="comparison-sector-gate-text">
            {isPrimaryStock ? t("analysis.benchmark.sectorEmpty") : t("analysis.benchmark.sectorOnly")}
          </p>
        </div>
      ) : (
        <div className="comparison-workspace-body">
          <div className="comparison-workspace-chart">
            {isChartLoading ? <LoadingSpinner label={t("analysis.benchmark.loading")} /> : null}
            {!isChartLoading && chartError ? <ErrorMessage message={chartError} /> : null}
            {!isChartLoading && !chartError && !benchmarkData && !benchmarkLoading ? (
              <p className="analysis-benchmark-empty">{t("analysis.benchmark.selectInstrument")}</p>
            ) : null}
            {!isChartLoading && !chartError && benchmarkData && chartData.length < 2 ? (
              <p className="analysis-benchmark-empty">{t("analysis.comparison.noData")}</p>
            ) : null}
            {hasChart ? (
              <div className="analysis-chart-shell terminal-chart-shell market-detail-chart">
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={chartData} margin={{ top: 18, right: 16, left: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} />
                    <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} />
                    <YAxis
                      stroke={chartTheme.axis}
                      tickLine={false}
                      axisLine={false}
                      width={72}
                      tickFormatter={(v) => formatAxisNumber(v)}
                    />
                    <Tooltip content={<ComparisonTooltip chartTheme={chartTheme} />} />
                    <Legend wrapperStyle={{ color: chartTheme.legendText }} />
                    {activeSeries.map((item, i) => (
                      <Line
                        key={item.symbol}
                        type="monotone"
                        dataKey={item.symbol}
                        name={resolveSeriesLabel(item.symbol, quotes)}
                        stroke={COLORS[i % COLORS.length]}
                        strokeWidth={2.2}
                        dot={false}
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            ) : null}
          </div>

          <div className="comparison-workspace-summary">
            <p className="comparison-summary-title">{summaryTitle}</p>
            {benchmarkLoading && !benchmarkData ? (
              <LoadingSpinner label={t("analysis.benchmark.loading")} />
            ) : null}
            {!benchmarkData && !benchmarkLoading ? (
              <p className="comparison-summary-empty">{t("analysis.benchmark.selectInstrument")}</p>
            ) : null}
            {benchmarkData ? (
              <>
                <div className="comparison-summary-metrics">
                  <ComparisonMetric
                    label={t("analysis.benchmark.baseReturn")}
                    value={formatReturnPct(benchmarkData.baseReturn)}
                  />
                  <ComparisonMetric
                    label={isMacro ? t("analysis.benchmark.inflationReturn") : t("analysis.benchmark.benchmarkReturn")}
                    value={formatReturnPct(benchmarkData.benchmarkReturn)}
                  />
                  <ComparisonMetric
                    label={isMacro ? t("analysis.benchmark.realReturn") : t("analysis.benchmark.relativeReturn")}
                    value={isMacro ? formatReturnPct(effectiveReturn) : formatPointReturn(effectiveReturn)}
                    highlight
                    positive={effectiveReturn != null && Number(effectiveReturn) >= 0}
                  />
                  {status ? (
                    <div className="comparison-metric">
                      <span className="comparison-metric-label">{t("analysis.benchmark.status")}</span>
                      <span className={`analysis-benchmark-status${status.startsWith("ABOVE") ? " above" : " below"}`}>
                        {t(`analysis.benchmark.statuses.${status}`)}
                      </span>
                    </div>
                  ) : null}
                </div>
                <p className="comparison-summary-description">
                  {isMacro ? t("analysis.benchmark.macroDescription") : t("analysis.benchmark.relativeDescription")}
                </p>
              </>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Sub-components ────────────────────────────────────────────────────────

function ComparisonMetric({ label, value, highlight = false, positive }) {
  const valueClass = [
    "comparison-metric-value",
    highlight && positive === true ? "comparison-metric-value--positive" : "",
    highlight && positive === false ? "comparison-metric-value--negative" : "",
  ].filter(Boolean).join(" ");

  return (
    <div className="comparison-metric">
      <span className="comparison-metric-label">{label}</span>
      <span className={valueClass}>{value}</span>
    </div>
  );
}

function ComparisonTooltip({ active, payload, label, chartTheme }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
      <strong>{label}</strong>
      {payload.map((item) => (
        <div key={item.dataKey} className="chart-tooltip-row">
          <span>{item.name}</span>
          <strong>{formatAxisNumber(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}

// ─── Helpers ───────────────────────────────────────────────────────────────

function buildComparisonTitle(primarySymbol, benchmarkType, benchmarkCode, quotes, t) {
  const primaryLabel = resolveCodeLabel(primarySymbol, quotes);
  let refLabel;
  if (benchmarkType === "MACRO") {
    refLabel = t("analysis.benchmark.presets.tufe");
  } else if (benchmarkType === "SECTOR") {
    refLabel = t("analysis.benchmark.sectorLabel");
  } else {
    refLabel = resolveCodeLabel(benchmarkCode, quotes);
  }
  if (!primaryLabel || !refLabel) return t("analysis.comparisonWorkspace.summaryTitle");
  return `${primaryLabel} vs ${refLabel}`;
}

function resolveCodeLabel(code, quotes) {
  if (!code) return "";
  const quote = quotes.find((q) => q.symbol === code || q.code === code);
  if (quote) return quote.code || normalizeTcmb(code) || code;
  return normalizeTcmb(code) || code;
}

function resolveSeriesLabel(symbol, quotes) {
  const quote = quotes.find((q) => q.symbol === symbol || q.code === symbol);
  if (quote) return quote.code || normalizeTcmb(symbol);
  return normalizeTcmb(symbol) || symbol;
}

function normalizeTcmb(symbol) {
  const s = String(symbol || "").trim();
  const match = s.toUpperCase().match(/^TCMB:([A-Z0-9]+):(BUY|SELL)$/);
  return match?.[1] || s || "-";
}

function buildInstrumentOptions(quotes, primarySymbol, searchTerm) {
  const normPrimary = normalizeKey(primarySymbol);
  const normSearch = String(searchTerm || "").trim().toUpperCase();
  return (Array.isArray(quotes) ? quotes : [])
    .map((item) => ({
      symbol: item.symbol || item.code,
      code: item.code || normalizeTcmb(item.symbol),
      name: item.displayName || item.name || item.code || item.symbol,
      instrumentType: String(item.instrumentType || "").toUpperCase(),
    }))
    .filter((item) => item.symbol && normalizeKey(item.symbol) !== normPrimary)
    .filter((item) => item.instrumentType !== "INDEX")
    .filter((item) => {
      if (!normSearch) return true;
      return `${item.symbol} ${item.code} ${item.name}`.toUpperCase().includes(normSearch);
    })
    .slice(0, 8);
}

function buildIndexOptions(quotes) {
  const fallback = [
    { code: "XU100", type: "INDEX", label: "BIST100" },
    { code: "XU050", type: "INDEX", label: "BIST50" },
    { code: "XU030", type: "INDEX", label: "BIST30" },
  ];
  const fromQuotes = (Array.isArray(quotes) ? quotes : [])
    .filter((item) => String(item.instrumentType || "").toUpperCase() === "INDEX")
    .map((item) => ({
      code: item.symbol || item.code,
      type: "INDEX",
      label: item.displayName || item.code || item.symbol,
    }))
    .filter((item) => item.code);

  const merged = [...fromQuotes, ...fallback];
  const seen = new Set();
  return merged.filter((item) => {
    const key = normalizeKey(item.code);
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).slice(0, 6);
}

function resolveDefaultBenchmark(type, indexOptions) {
  if (type === "MACRO") return { code: "CPI_TR", type: "MACRO" };
  if (type === "INDEX") return indexOptions[0] ?? { code: "XU100", type: "INDEX" };
  if (type === "SECTOR") return { code: "SECTOR", type: "SECTOR" };
  return { code: "", type: "INSTRUMENT" };
}

function normalizeKey(value) {
  return String(value || "").replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function formatReturnPct(value) {
  if (value == null) return "-";
  const pct = Number(value) * 100;
  return `${pct >= 0 ? "+" : ""}${pct.toFixed(2)}%`;
}

// Relative return between two assets: shown as "puan" difference
function formatPointReturn(value) {
  if (value == null) return "-";
  const pct = Number(value) * 100;
  return `${pct >= 0 ? "+" : ""}${pct.toFixed(2)} puan`;
}
