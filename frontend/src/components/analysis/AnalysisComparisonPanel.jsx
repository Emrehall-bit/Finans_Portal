import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Check, Plus, X } from "lucide-react";
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

export default function AnalysisComparisonPanel({
  loading,
  error,
  comparison,
  mode = "normalized",
  onModeChange,
  suggestions = [],
  selectedSymbols = [],
  primarySymbol = "",
  quotes = [],
  onToggleSymbol,
}) {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const series = comparison?.series ?? [];
  const chartData = useMemo(() => buildComparisonData(series, mode), [series, mode]);
  const isActive = !loading && !error && series.length >= 2;
  const compactEmpty = !loading && !error && series.length < 2;
  const activeComparisonSymbols = useMemo(
    () => selectedSymbols.filter((symbol) => symbol && symbol !== primarySymbol),
    [selectedSymbols, primarySymbol],
  );

  return (
    <section
      className={`panel-surface analysis-lab-panel analysis-comparison-section${compactEmpty ? " analysis-lab-panel--compact-empty" : ""}${isActive ? " is-active" : ""}`}
    >
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("analysis.comparison.eyebrow")}</p>
          <h3>{mode === "price" ? t("analysis.comparison.priceTitle") : t("analysis.comparison.title")}</h3>
        </div>
        {isActive ? (
          <span className="analysis-comparison-live-badge">{t("analysis.comparison.activeCount", { count: series.length })}</span>
        ) : null}
        <div className="market-segmented-tabs" role="tablist" aria-label={t("analysis.comparison.modeLabel")}>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "normalized"}
            className={`market-segmented-tab ${mode === "normalized" ? "active" : ""}`}
            onClick={() => onModeChange?.("normalized")}
          >
            {t("analysis.comparison.normalized")}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "price"}
            className={`market-segmented-tab ${mode === "price" ? "active" : ""}`}
            onClick={() => onModeChange?.("price")}
          >
            {t("analysis.comparison.price")}
          </button>
        </div>
      </div>

      {activeComparisonSymbols.length > 0 ? (
        <div className="analysis-comparison-active-row">
          {activeComparisonSymbols.map((symbol) => (
            <button
              key={symbol}
              type="button"
              className="analysis-comparison-active-chip"
              onClick={() => onToggleSymbol?.(symbol)}
              title={t("common.remove")}
            >
              <span>{formatSelectedComparisonSymbol(symbol, quotes)}</span>
              <X size={12} strokeWidth={2.4} />
            </button>
          ))}
        </div>
      ) : null}

      {loading ? <LoadingSpinner label={t("analysis.comparison.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && series.length < 2 ? (
        <ComparisonEmptyState
          suggestions={suggestions}
          selectedSymbols={selectedSymbols}
          onToggleSymbol={onToggleSymbol}
        />
      ) : null}

      {!loading && !error && series.length >= 2 ? (
        <div className="analysis-chart-shell terminal-chart-shell market-detail-chart">
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData} margin={{ top: 18, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} />
              <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} />
              <YAxis stroke={chartTheme.axis} tickLine={false} axisLine={false} width={72} tickFormatter={(value) => formatAxisNumber(value)} />
              <Tooltip content={<ComparisonTooltip chartTheme={chartTheme} />} />
              <Legend wrapperStyle={{ color: chartTheme.legendText }} />
              {series.map((item, index) => (
                <Line
                  key={item.symbol}
                  type="monotone"
                  dataKey={item.symbol}
                  name={formatComparisonSymbol(item.symbol)}
                  stroke={COLORS[index % COLORS.length]}
                  strokeWidth={2.2}
                  dot={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : null}
    </section>
  );
}

function formatComparisonSymbol(symbol) {
  const rawSymbol = String(symbol || "").trim();
  const tcmbMatch = rawSymbol.toUpperCase().match(/^TCMB:([A-Z0-9]+):(BUY|SELL)$/);
  return tcmbMatch?.[1] || rawSymbol || "-";
}

function formatSelectedComparisonSymbol(symbol, quotes) {
  const match = quotes.find((item) => item.symbol === symbol || item.code === symbol);
  return match?.code || formatComparisonSymbol(symbol);
}

function ComparisonTooltip({ active, payload, label, chartTheme }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
      <strong>{label}</strong>
      {payload.map((item) => (
        <div key={item.dataKey} className="chart-tooltip-row">
          <span>{item.name}</span>
          <strong>{formatComparisonValue(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}

function formatComparisonValue(value) {
  return formatAxisNumber(value);
}

function ComparisonEmptyState({ suggestions = [], selectedSymbols = [], onToggleSymbol }) {
  const { t } = useTranslation();
  const interactive = typeof onToggleSymbol === "function" && suggestions.length > 0;
  const items = suggestions.length > 0
    ? suggestions
    : ["BTC", "ETH", "XAUUSD", "THYAO"].map((symbol) => ({ symbol, label: symbol }));

  return (
    <div className="comparison-empty-compact">
      <p>{t("analysis.comparisonEmpty.description")}</p>
      <div
        className="analysis-comparison-suggestion-row"
        role={interactive ? "group" : undefined}
        aria-label={interactive ? t("analysis.comparisonEmpty.ariaLabel") : undefined}
      >
        {items.map(({ symbol, label }) => {
          const active = selectedSymbols.includes(symbol);
          if (!interactive) {
            return <span key={symbol} className="analysis-comparison-suggestion-chip">{label}</span>;
          }
          return (
            <button
              key={symbol}
              type="button"
              className={`analysis-comparison-suggestion-chip is-interactive${active ? " active" : ""}`}
              onClick={() => onToggleSymbol(symbol)}
              aria-pressed={active}
            >
              {active ? <Check size={13} strokeWidth={2.4} /> : <Plus size={13} strokeWidth={2.4} />}
              <span>{label}</span>
            </button>
          );
        })}
      </div>
      {interactive ? <span className="comparison-empty-hint">{t("analysis.comparisonEmpty.suggestionHint")}</span> : null}
    </div>
  );
}
