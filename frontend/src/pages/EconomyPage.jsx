import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getMacroObservations } from "../api/macroApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import { useTheme } from "../theme/ThemeContext";

const INDICATOR_GROUPS = [
  { key: "CPI", series: [{ code: "CPI_TR", valueType: "YEARLY_CHANGE" }, { code: "CPI_TR", valueType: "MONTHLY_CHANGE" }], unit: "PERCENT" },
  { key: "PPI", series: [{ code: "PPI_TR", valueType: "YEARLY_CHANGE" }, { code: "PPI_TR", valueType: "MONTHLY_CHANGE" }], unit: "PERCENT" },
  { key: "POLICY_RATE", series: [{ code: "POLICY_RATE_TR", valueType: "POLICY_RATE" }], unit: "PERCENT" },
  { key: "LABOR", series: [{ code: "UNEMPLOYMENT_TR", valueType: "UNEMPLOYMENT_RATE" }, { code: "LABOR_FORCE_PARTICIPATION_TR", valueType: "LABOR_FORCE_PARTICIPATION_RATE" }], unit: "PERCENT" },
  { key: "CONSUMER_CONFIDENCE", series: [{ code: "CONSUMER_CONFIDENCE_TR", valueType: "CONSUMER_CONFIDENCE_INDEX" }], unit: "INDEX" },
  { key: "CURRENT_ACCOUNT", series: [{ code: "CURRENT_ACCOUNT_TR", valueType: "CURRENT_ACCOUNT_BALANCE" }], unit: "USD" },
];

const CHART_COLORS = ["#4c7fff", "#22b07d", "#f4b44e"];

function formatVal(value, unit) {
  if (value == null) return "-";
  const n = Number(value);
  if (unit === "USD") return n.toLocaleString("tr-TR", { maximumFractionDigits: 1 });
  return n.toFixed(2);
}

function mergeSeries(seriesDataArray) {
  const periodMap = new Map();
  seriesDataArray.forEach((obs, idx) => {
    obs.forEach((item) => {
      if (!periodMap.has(item.periodLabel)) {
        periodMap.set(item.periodLabel, new Array(seriesDataArray.length).fill(null));
      }
      periodMap.get(item.periodLabel)[idx] = item.value;
    });
  });
  return [...periodMap.entries()].map(([period, values]) => ({ period, values })).reverse();
}

export default function EconomyPage() {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();

  const [selectedGroupKey, setSelectedGroupKey] = useState(INDICATOR_GROUPS[0].key);
  const [dateFilter, setDateFilter] = useState("24M");
  const [tableRows, setTableRows] = useState([]);
  const [tableLoading, setTableLoading] = useState(true);
  const [tableError, setTableError] = useState("");

  useEffect(() => {
    const group = INDICATOR_GROUPS.find((g) => g.key === selectedGroupKey);
    if (!group) return;
    let active = true;
    setTableLoading(true);
    setTableError("");
    async function load() {
      try {
        const results = await Promise.allSettled(
          group.series.map((s) => getMacroObservations(s.code, s.valueType)),
        );
        if (!active) return;
        setTableRows(mergeSeries(results.map((r) => (r.status === "fulfilled" ? (r.value ?? []) : []))));
      } catch (err) {
        if (active) {
          setTableError(extractErrorMessage(err, t("economy.loadError")));
          setTableRows([]);
        }
      } finally {
        if (active) setTableLoading(false);
      }
    }
    load();
    return () => {
      active = false;
    };
  }, [selectedGroupKey, t]);

  const selectedGroup = useMemo(
    () => INDICATOR_GROUPS.find((g) => g.key === selectedGroupKey) ?? INDICATOR_GROUPS[0],
    [selectedGroupKey],
  );

  const filteredRows = useMemo(() => {
    if (dateFilter === "12M") return tableRows.slice(0, 12);
    if (dateFilter === "24M") return tableRows.slice(0, 24);
    return tableRows;
  }, [tableRows, dateFilter]);

  const chartData = useMemo(
    () => [...tableRows.slice(0, 24)].reverse().map((row) => ({
      period: row.period,
      ...Object.fromEntries(row.values.map((val, i) => [`v${i}`, val != null ? Number(val) : undefined])),
    })),
    [tableRows],
  );

  const downloadCsv = useCallback(() => {
    const colLabels = selectedGroup.series.map((_, i) => t(`economy.groups.${selectedGroupKey}.col${i}`));
    const header = ["Tarih", ...colLabels].join(",");
    const rows = filteredRows.map((row) =>
      [row.period, ...row.values.map((v) => (v != null ? Number(v).toFixed(2) : ""))].join(","),
    );
    const blob = new Blob(["ï»¿" + [header, ...rows].join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${selectedGroupKey}_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }, [selectedGroup, selectedGroupKey, filteredRows, t]);

  function handleGroupChange(key) {
    setSelectedGroupKey(key);
    setDateFilter("24M");
  }

  const kpiNewest = tableRows[0];
  const kpiPrev = tableRows.length > 1 ? tableRows[1] : null;
  const kpiValue = kpiNewest?.values[0] ?? null;
  const kpiPrevVal = kpiPrev?.values[0] ?? null;
  const kpiChange = kpiValue != null && kpiPrevVal != null ? Number(kpiValue) - Number(kpiPrevVal) : null;

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow={t("economy.eyebrow")}
        title={t("economy.title")}
        description={t("economy.description")}
      />

      <section className="panel-surface economy-section">
        <div className="panel-head">
          <div>
            <p className="eyebrow">{t("economy.source")}</p>
            <h3>{t(`economy.groups.${selectedGroupKey}.title`)}</h3>
          </div>
        </div>

        <div className="economy-tab-row">
          {INDICATOR_GROUPS.map((group) => (
            <div key={group.key} className="economy-tab-item">
              <button
                type="button"
                className={`economy-tab-btn${selectedGroupKey === group.key ? " active" : ""}`}
                aria-label={t(`economy.groups.${group.key}.title`)}
                onClick={() => handleGroupChange(group.key)}
              >
                {t(`economy.groups.${group.key}.label`)}
              </button>
              <span className="economy-tab-tooltip" role="tooltip">
                {t(`economy.groups.${group.key}.title`)}
              </span>
            </div>
          ))}
        </div>

        {tableLoading && tableRows.length === 0 ? (
          <div style={{ marginTop: "1rem" }}><LoadingSpinner /></div>
        ) : tableError ? (
          <div style={{ marginTop: "1rem" }}><ErrorMessage message={tableError} /></div>
        ) : tableRows.length === 0 ? (
          <div style={{ marginTop: "1rem" }}><EmptyState title={t("economy.noData")} /></div>
        ) : (
          <div style={{ opacity: tableLoading ? 0.5 : 1, transition: "opacity 0.18s ease" }}>
            <div className="economy-kpi-row">
              <div className="economy-kpi-card">
                <span className="economy-kpi-label">{t("economy.kpi.lastValue")}</span>
                <span className="economy-kpi-value">{formatVal(kpiValue, selectedGroup.unit)}</span>
                <span className="economy-kpi-period">{kpiNewest?.period ?? "—"}</span>
              </div>
              <div className="economy-kpi-card">
                <span className="economy-kpi-label">{t("economy.kpi.prevPeriod")}</span>
                <span className="economy-kpi-value">{formatVal(kpiPrevVal, selectedGroup.unit)}</span>
                <span className="economy-kpi-period">{kpiPrev?.period ?? "—"}</span>
              </div>
              <div className="economy-kpi-card">
                <span className="economy-kpi-label">{t("economy.kpi.change")}</span>
                <span className="economy-kpi-value">
                  {kpiChange != null
                    ? <span className="economy-kpi-badge">{kpiChange >= 0 ? "+" : ""}{Number(kpiChange).toFixed(2)}p</span>
                    : "—"}
                </span>
              </div>
            </div>

            <div className="economy-chart-header">
              <span className="economy-chart-title">{t("economy.chart.trendTitle")}</span>
              {selectedGroup.series.length > 1 && (
                <div className="economy-chart-legend">
                  {selectedGroup.series.map((s, i) => (
                    <div key={i} className="economy-chart-legend-item">
                      <span className="economy-chart-legend-dot" style={{ background: CHART_COLORS[i % CHART_COLORS.length] }} />
                      <span className="economy-chart-legend-label">{t(`economy.chart.legend.${s.valueType}`)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="economy-chart-wrap">
              {chartData.length < 2 ? (
                <div className="economy-chart-empty">{t("economy.chartNoData")}</div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                    <defs>
                      {selectedGroup.series.map((_, i) => (
                        <linearGradient key={i} id={`ecGrad${i}`} x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor={CHART_COLORS[i % CHART_COLORS.length]} stopOpacity={0.18} />
                          <stop offset="95%" stopColor={CHART_COLORS[i % CHART_COLORS.length]} stopOpacity={0} />
                        </linearGradient>
                      ))}
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} />
                    <XAxis
                      dataKey="period"
                      tick={{ fill: chartTheme.axis, fontSize: 10 }}
                      tickLine={false}
                      interval="preserveStartEnd"
                    />
                    <YAxis
                      tick={{ fill: chartTheme.axis, fontSize: 10 }}
                      tickLine={false}
                      axisLine={false}
                      width={48}
                      tickFormatter={(v) =>
                        selectedGroup.unit === "USD"
                          ? Number(v).toLocaleString("en-US", { maximumFractionDigits: 0 })
                          : Number(v).toFixed(1)
                      }
                    />
                    <Tooltip
                      contentStyle={chartTheme.tooltipContentStyle}
                      itemStyle={chartTheme.tooltipItemStyle}
                      labelStyle={chartTheme.tooltipLabelStyle}
                      formatter={(value, name) => {
                        const idx = parseInt(name.replace("v", ""), 10);
                        return [`${Number(value).toFixed(2)}`, t(`economy.groups.${selectedGroupKey}.col${idx}`)];
                      }}
                    />
                    {selectedGroup.series.map((_, i) => (
                      <Area
                        key={i}
                        type="monotone"
                        dataKey={`v${i}`}
                        stroke={CHART_COLORS[i % CHART_COLORS.length]}
                        fill={`url(#ecGrad${i})`}
                        strokeWidth={2}
                        dot={false}
                        activeDot={{ r: 4, strokeWidth: 0 }}
                        connectNulls={false}
                      />
                    ))}
                  </AreaChart>
                </ResponsiveContainer>
              )}
            </div>

            <div className="economy-filter-bar">
              <div className="economy-range-group">
                {["12M", "24M", "ALL"].map((range) => (
                  <button
                    key={range}
                    type="button"
                    className={`economy-range-btn${dateFilter === range ? " active" : ""}`}
                    onClick={() => setDateFilter(range)}
                  >
                    {t(`economy.filter.range${range}`)}
                  </button>
                ))}
              </div>
              <button type="button" className="economy-csv-btn" onClick={downloadCsv}>
                {t("economy.filter.csvDownload")}
              </button>
            </div>

            <div className="economy-data-table-wrap">
              <table className="economy-data-table">
                <thead>
                  <tr>
                    <th>{t("economy.dateCol")}</th>
                    {selectedGroup.series.map((_, i) => (
                      <th key={i}>{t(`economy.groups.${selectedGroupKey}.col${i}`)}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filteredRows.map((row) => (
                    <tr key={row.period} className="economy-table-row">
                      <td className="economy-period-cell">{row.period}</td>
                      {row.values.map((val, i) => (
                        <td key={i} className="economy-value-cell">{formatVal(val, selectedGroup.unit)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
