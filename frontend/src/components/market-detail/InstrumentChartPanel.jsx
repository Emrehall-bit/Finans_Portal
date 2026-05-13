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
import { DEFAULT_INDICATORS, RANGE_PRESETS, formatAxisNumber } from "./marketDetailUtils";

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
  presets = RANGE_PRESETS,
  emptyTitle,
  emptyDescription,
}) {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const showRsi = selectedIndicators.has("RSI14");
  const hasData = chartData.length > 0;
  const resolvedEmptyTitle = emptyTitle || t("instrumentDetail.chartEmptyTitle");
  const resolvedEmptyDescription = emptyDescription || t("instrumentDetail.chartEmptyDescription");

  return (
    <section className="panel-surface instrument-chart-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("instrumentDetail.chartEyebrow")}</p>
          <h3>{t("instrumentDetail.chartTitle")}</h3>
        </div>
      </div>

      <div className="market-chart-topbar instrument-detail-range-bar">
        <div className="preset-chip-row">
          {presets.map((preset) => (
            <button key={preset.key} type="button" className={`table-chip-button ${activeRange === preset.key ? "active" : ""}`} onClick={() => onRangeChange(preset)}>
              {t(`instrumentDetail.ranges.${preset.key}`)}
            </button>
          ))}
        </div>

        <div className="analysis-date-grid">
          <label className="market-filter-field">
            <span>{t("instrumentDetail.startDate")}</span>
            <input type="date" value={dateRange.from} onChange={(event) => onDateRangeChange("from", event.target.value)} />
          </label>
          <label className="market-filter-field">
            <span>{t("instrumentDetail.endDate")}</span>
            <input type="date" value={dateRange.to} onChange={(event) => onDateRangeChange("to", event.target.value)} />
          </label>
        </div>
      </div>

      <div className="indicator-chip-row">
        {DEFAULT_INDICATORS.map((indicator) => (
          <button key={indicator} type="button" className={`table-chip-button ${selectedIndicators.has(indicator) ? "active" : ""}`} onClick={() => onToggleIndicator(indicator)}>
            {indicator}
          </button>
        ))}
      </div>

      {loading ? <LoadingSpinner label={t("instrumentDetail.chartLoading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && !hasData ? <EmptyState title={resolvedEmptyTitle} description={resolvedEmptyDescription} /> : null}

      {!loading && !error && hasData ? (
        <>
          <div className="analysis-chart-shell terminal-chart-shell market-detail-chart">
            <ResponsiveContainer width="100%" height={430}>
              <LineChart data={chartData} margin={{ top: 18, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} />
                <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} />
                <YAxis stroke={chartTheme.axis} tickLine={false} axisLine={false} width={72} tickFormatter={(value) => formatAxisNumber(value)} />
                <Tooltip content={<ChartTooltip chartTheme={chartTheme} />} />
                <Legend wrapperStyle={{ color: chartTheme.legendText }} />
                <Line type="monotone" dataKey="close" name={t("instrumentDetail.price")} stroke={chartTheme.priceLine} strokeWidth={2.8} dot={false} />
                {selectedIndicators.has("SMA7") ? <Line type="monotone" dataKey="sma7" name="MA 7" stroke={chartTheme.sma7Line} strokeWidth={2} dot={false} /> : null}
                {selectedIndicators.has("SMA20") ? <Line type="monotone" dataKey="sma20" name="MA 20" stroke={chartTheme.sma20Line} strokeWidth={2} dot={false} /> : null}
                {selectedIndicators.has("SMA50") ? <Line type="monotone" dataKey="sma50" name="MA 50" stroke={chartTheme.sma50Line} strokeWidth={2} dot={false} /> : null}
              </LineChart>
            </ResponsiveContainer>
          </div>

          {showRsi ? (
            <div className="analysis-chart-shell terminal-chart-shell market-detail-rsi">
              <div className="panel-head compact">
                <div>
                  <h3>RSI 14</h3>
                </div>
              </div>
              <ResponsiveContainer width="100%" height={210}>
                <LineChart data={chartData} margin={{ top: 12, right: 16, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} />
                  <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} />
                  <YAxis domain={[0, 100]} stroke={chartTheme.axis} tickLine={false} axisLine={false} width={44} />
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

function ChartTooltip({ active, payload, label, chartTheme }) {
  if (!active || !payload?.length) {
    return null;
  }

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
