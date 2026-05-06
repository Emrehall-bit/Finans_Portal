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
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { useTheme } from "../../theme/ThemeContext";
import { buildComparisonData, formatAxisNumber } from "./analysisUtils";

const COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2"];

export default function AnalysisComparisonPanel({ loading, error, comparison }) {
  const { chartTheme } = useTheme();
  const series = comparison?.series ?? [];
  const chartData = buildComparisonData(series);

  return (
    <section className="panel-surface analysis-lab-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Karsilastirma</p>
          <h3>Normalize performans</h3>
        </div>
      </div>

      {loading ? <LoadingSpinner label="Karsilastirma yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && series.length < 2 ? (
        <EmptyState title="Karsilastirma verisi bulunamadi" description="En az iki enstruman secildiginde karsilastirma olusur." />
      ) : null}

      {!loading && !error && series.length >= 2 ? (
        <div className="analysis-chart-shell terminal-chart-shell market-detail-chart">
          <ResponsiveContainer width="100%" height={380}>
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
                  name={item.symbol}
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

function ComparisonTooltip({ active, payload, label, chartTheme }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div
      className="chart-tooltip terminal-tooltip"
      style={{
        backgroundColor: chartTheme.tooltipBg,
        borderColor: chartTheme.tooltipBorder,
        color: chartTheme.tooltipText,
      }}
    >
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
