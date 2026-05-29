import { useCallback } from "react";
import { useTranslation } from "react-i18next";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { useTheme } from "../../theme/ThemeContext";
import { DEFAULT_INDICATORS, RANGE_PRESETS } from "./marketDetailUtils";

export default function InstrumentChartPanel({
  activeRange,
  onRangeChange,
  dateRange,
  onDateRangeChange,
  selectedIndicators,
  onToggleIndicator,
  loading,
  error,
  chartData,
  currency = "TRY",
  presets = RANGE_PRESETS,
  emptyTitle,
  emptyDescription,
  hidePanelHeader = false,
  secondaryToolbar = null,
}) {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const showRsi = selectedIndicators.has("RSI14");
  const hasData = chartData.length > 0;
  const resolvedEmptyTitle = emptyTitle || t("instrumentDetail.chartEmptyTitle");
  const resolvedEmptyDescription = emptyDescription || t("instrumentDetail.chartEmptyDescription");

  const formatYAxis = useCallback((value) => {
    if (value == null || Number.isNaN(Number(value))) return "";
    const prefix = currency === "USD" ? "$" : "₺";
    const num = Math.abs(Number(value));
    if (num >= 1_000_000) return `${prefix}${(Number(value) / 1_000_000).toFixed(1)}M`;
    if (num >= 1_000) return `${prefix}${(Number(value) / 1_000).toFixed(1)}K`;
    return `${prefix}${Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
  }, [currency]);

  return (
    <section className="panel-surface instrument-chart-panel">
      {!hidePanelHeader ? (
        <div className="panel-head">
          <div>
            <p className="eyebrow">{t("instrumentDetail.chartEyebrow")}</p>
            <h3>{t("instrumentDetail.chartTitle")}</h3>
          </div>
        </div>
      ) : null}

      <div className="icp-topbar">
        <div className="chart-timeframes">
          {presets.map((preset) => (
            <button
              key={preset.key}
              type="button"
              className={`chart-tf-btn${activeRange === preset.key ? " active" : ""}`}
              onClick={() => onRangeChange(preset)}
            >
              {t(`instrumentDetail.ranges.${preset.key}`)}
            </button>
          ))}
        </div>

        <div className="icp-date-row">
          <label className="icp-date-field">
            <span>{t("instrumentDetail.startDate")}</span>
            <input type="date" value={dateRange.from} onChange={(e) => onDateRangeChange("from", e.target.value)} />
          </label>
          <label className="icp-date-field">
            <span>{t("instrumentDetail.endDate")}</span>
            <input type="date" value={dateRange.to} onChange={(e) => onDateRangeChange("to", e.target.value)} />
          </label>
        </div>

        <div className="icp-indicator-row">
          {DEFAULT_INDICATORS.map((indicator) => (
            <button
              key={indicator}
              type="button"
              className={`chart-tf-btn${selectedIndicators.has(indicator) ? " active" : ""}`}
              onClick={() => onToggleIndicator(indicator)}
            >
              {indicator}
            </button>
          ))}
        </div>
      </div>

      {secondaryToolbar ? <div className="analysis-chart-secondary-toolbar">{secondaryToolbar}</div> : null}

      {loading ? <LoadingSpinner label={t("instrumentDetail.chartLoading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && !hasData ? <EmptyState title={resolvedEmptyTitle} description={resolvedEmptyDescription} /> : null}

      {!loading && !error && hasData ? (
        <>
          <div className="icp-chart-wrap terminal-chart-shell market-detail-chart">
            <ResponsiveContainer width="100%" height={520}>
              <LineChart data={chartData} margin={{ top: 20, right: 20, left: 8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="4 4" stroke={chartTheme.grid} strokeOpacity={0.55} />
                <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} tick={{ fontSize: 11 }} />
                <YAxis
                  stroke={chartTheme.axis}
                  tickLine={false}
                  axisLine={false}
                  width={82}
                  tickFormatter={formatYAxis}
                  tick={{ fontSize: 11 }}
                />
                <Tooltip content={<ChartTooltip chartTheme={chartTheme} formatValue={formatYAxis} />} />
                <Legend wrapperStyle={{ color: chartTheme.legendText, fontSize: 12 }} />
                <Line type="monotone" dataKey="close" name={t("instrumentDetail.price")} stroke={chartTheme.priceLine} strokeWidth={2.8} dot={false} activeDot={{ r: 4 }} />
                {selectedIndicators.has("SMA7") ? <Line type="monotone" dataKey="sma7" name="MA 7" stroke={chartTheme.sma7Line} strokeWidth={1.8} dot={false} /> : null}
                {selectedIndicators.has("SMA20") ? <Line type="monotone" dataKey="sma20" name="MA 20" stroke={chartTheme.sma20Line} strokeWidth={1.8} dot={false} /> : null}
                {selectedIndicators.has("SMA50") ? <Line type="monotone" dataKey="sma50" name="MA 50" stroke={chartTheme.sma50Line} strokeWidth={1.8} dot={false} /> : null}
              </LineChart>
            </ResponsiveContainer>
          </div>

          {showRsi ? (
            <div className="icp-chart-wrap terminal-chart-shell market-detail-rsi">
              <div className="panel-head compact">
                <div><h3>RSI 14</h3></div>
              </div>
              <ResponsiveContainer width="100%" height={210}>
                <LineChart data={chartData} margin={{ top: 12, right: 20, left: 8, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="4 4" stroke={chartTheme.grid} strokeOpacity={0.55} />
                  <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} tick={{ fontSize: 11 }} />
                  <YAxis domain={[0, 100]} stroke={chartTheme.axis} tickLine={false} axisLine={false} width={44} tick={{ fontSize: 11 }} />
                  <Tooltip content={<ChartTooltip chartTheme={chartTheme} />} />
                  <ReferenceLine y={70} stroke={chartTheme.rsiUpperLine} strokeDasharray="6 6" />
                  <ReferenceLine y={30} stroke={chartTheme.rsiLowerLine} strokeDasharray="6 6" />
                  <Line type="monotone" dataKey="rsi14" name="RSI 14" stroke={chartTheme.rsiLine} strokeWidth={2.4} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function ChartTooltip({ active, payload, label, chartTheme, formatValue }) {
  if (!active || !payload?.length) return null;

  const fmt = formatValue ?? ((v) => v?.toLocaleString(undefined, { maximumFractionDigits: 2 }) ?? "-");

  return (
    <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
      <strong>{label}</strong>
      {payload.map((item) => (
        <div key={item.dataKey} className="chart-tooltip-row">
          <span>{item.name}</span>
          <strong>{fmt(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}
