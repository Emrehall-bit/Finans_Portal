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
  { key: "CPI",    series: [{ code: "CPI_TR", valueType: "YEARLY_CHANGE" }, { code: "CPI_TR", valueType: "MONTHLY_CHANGE" }], unit: "PERCENT" },
  { key: "PPI",    series: [{ code: "PPI_TR", valueType: "YEARLY_CHANGE" }, { code: "PPI_TR", valueType: "MONTHLY_CHANGE" }], unit: "PERCENT" },
  { key: "POLICY_RATE", series: [{ code: "POLICY_RATE_TR", valueType: "POLICY_RATE" }], unit: "PERCENT" },
  { key: "LABOR",  series: [{ code: "UNEMPLOYMENT_TR", valueType: "UNEMPLOYMENT_RATE" }, { code: "LABOR_FORCE_PARTICIPATION_TR", valueType: "LABOR_FORCE_PARTICIPATION_RATE" }], unit: "PERCENT" },
  { key: "CONSUMER_CONFIDENCE", series: [{ code: "CONSUMER_CONFIDENCE_TR", valueType: "CONSUMER_CONFIDENCE_INDEX" }], unit: "INDEX" },
  { key: "CURRENT_ACCOUNT", series: [{ code: "CURRENT_ACCOUNT_TR", valueType: "CURRENT_ACCOUNT_BALANCE" }], unit: "USD" },
];

const OVERVIEW_INDICATORS = [
  { code: "CPI_TR",                 valueType: "YEARLY_CHANGE",             key: "cpi" },
  { code: "POLICY_RATE_TR",         valueType: "POLICY_RATE",               key: "policyRate" },
  { code: "UNEMPLOYMENT_TR",        valueType: "UNEMPLOYMENT_RATE",          key: "unemployment" },
  { code: "CONSUMER_CONFIDENCE_TR", valueType: "CONSUMER_CONFIDENCE_INDEX", key: "consumerConfidence" },
  { code: "CURRENT_ACCOUNT_TR",     valueType: "CURRENT_ACCOUNT_BALANCE",   key: "currentAccount" },
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

function cap(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

function generateOverview(data, lang) {
  const isTr = lang !== "en";
  const { cpi, policyRate, unemployment, consumerConfidence, currentAccount } = data;
  const hasCore = [cpi, policyRate, unemployment].some((v) => v != null);
  if (!hasCore && consumerConfidence == null && currentAccount == null) return { summary: "", impacts: [] };

  const clauses = [];
  if (cpi != null) {
    const v = Number(cpi);
    if (v > 50)      clauses.push(isTr ? "enflasyon yüksek seyrini korurken" : "while inflation remains elevated");
    else if (v > 20) clauses.push(isTr ? "enflasyon görece yüksek seyretmekte" : "while inflation is relatively high");
    else if (v > 10) clauses.push(isTr ? "enflasyon ılımlı düzeyde" : "while inflation is moderate");
    else             clauses.push(isTr ? "enflasyon kontrol altında" : "while inflation is under control");
  }
  if (policyRate != null) {
    const v = Number(policyRate);
    if (v > 35)      clauses.push(isTr ? "politika faizi sıkı seviyede devam ediyor" : "the policy rate remains restrictive");
    else if (v > 20) clauses.push(isTr ? "politika faizi yüksek konumunu sürdürüyor" : "the policy rate remains elevated");
    else if (v > 10) clauses.push(isTr ? "faiz ılımlı düzeyde seyrediyor" : "the interest rate is at a moderate level");
    else             clauses.push(isTr ? "faiz görece düşük seviyede" : "interest rates are relatively low");
  }
  if (unemployment != null) {
    const v = Number(unemployment);
    if (v > 12)     clauses.push(isTr ? "işsizlik yüksek seyrediyor" : "unemployment is elevated");
    else if (v > 8) clauses.push(isTr ? "işsizlik ılımlı düzeyde" : "unemployment is at a moderate level");
    else            clauses.push(isTr ? "işgücü piyasası görece güçlü" : "the labor market is relatively strong");
  }

  let summary = clauses.length > 0
    ? (isTr ? "Türkiye ekonomisinde " : "In the Turkish economy, ") + clauses.join(", ") + "."
    : "";

  const extra = [];
  if (consumerConfidence != null) {
    const v = Number(consumerConfidence);
    if (v < 70)      extra.push(isTr ? "tüketici güveni zayıf seyretmekte" : "consumer confidence is weak");
    else if (v < 85) extra.push(isTr ? "tüketici güveni ılımlı düzeyde" : "consumer confidence is moderate");
    else             extra.push(isTr ? "tüketici güveni güçlü seyrediyor" : "consumer confidence is strong");
  }
  if (currentAccount != null) {
    const v = Number(currentAccount);
    if (v < -5000)  extra.push(isTr ? "cari açık belirgin düzeyde seyrediyor" : "the current account deficit is significant");
    else if (v < 0) extra.push(isTr ? "cari denge açık veriyor" : "the current account is in deficit");
    else            extra.push(isTr ? "cari denge fazla veriyor" : "the current account is in surplus");
  }
  if (extra.length > 0) summary += (summary ? " " : "") + cap(extra.join(isTr ? ", " : "; ")) + ".";

  const impacts = [];
  if (cpi != null) {
    const v = Number(cpi);
    impacts.push(v > 20
      ? (isTr ? "Yüksek enflasyon reel getirileri baskılıyor; tahvil ve mevduatta değer kaybı riski artıyor." : "High inflation erodes real returns, raising value-loss risk for bonds and deposits.")
      : (isTr ? "Düşen enflasyon TL varlıkları ve sabit getirili araçları destekleyebilir." : "Declining inflation may support TL assets and fixed-income instruments."));
  }
  if (policyRate != null) {
    const v = Number(policyRate);
    if (v > 20) impacts.push(isTr ? "Yüksek faiz kredi maliyetlerini artırıyor; yatırım iştahı ve büyüme baskı altında." : "High interest rates raise borrowing costs; investment appetite and growth are under pressure.");
    else if (v > 10) impacts.push(isTr ? "Mevcut faiz seviyesi getiri araçlarını cazip kılarken kredi büyümesini yavaşlatabilir." : "Current rate levels make yield instruments attractive while potentially slowing credit growth.");
    else impacts.push(isTr ? "Düşük faiz ortamı kredi büyümesini desteklerken TL üzerinde baskı riski taşıyor." : "Low rate environment supports credit growth but carries TL depreciation risk.");
  }
  if (unemployment != null && Number(unemployment) > 10) impacts.push(isTr ? "Yüksek işsizlik iç talebi ve tüketim harcamalarını olumsuz etkiliyor." : "High unemployment weighs on domestic demand and consumer spending.");
  if (consumerConfidence != null) {
    const v = Number(consumerConfidence);
    if (v < 75) impacts.push(isTr ? "Zayıf tüketici güveni harcama eğilimini kısıtlıyor; tüketim odaklı varlıklar baskı altında kalabilir." : "Weak consumer confidence limits spending; consumer-oriented assets may remain under pressure.");
    else if (v > 85) impacts.push(isTr ? "Güçlü tüketici güveni iç talebi ve tüketime dönük sektörleri destekliyor." : "Strong consumer confidence supports domestic demand and consumer-oriented sectors.");
  }
  if (currentAccount != null) {
    const v = Number(currentAccount);
    if (v < -3000) impacts.push(isTr ? "Cari açık döviz talebini canlı tutuyor; kur oynaklığı artabilir ve TL üzerinde baskı sürebilir." : "The current account deficit keeps FX demand elevated; exchange rate volatility and TL pressure may persist.");
    else if (v < 0) impacts.push(isTr ? "Cari açık döviz piyasasında sınırlı baskı yaratıyor; döviz varlıkları cazibesini koruyabilir." : "The current account deficit creates limited FX market pressure; foreign currency assets may retain appeal.");
    else impacts.push(isTr ? "Cari fazla döviz girişini destekliyor; kur üzerindeki baskıyı hafifletiyor." : "The current account surplus supports FX inflows and eases exchange rate pressure.");
  }
  return { summary, impacts: impacts.slice(0, 3) };
}

export default function EconomyPage() {
  const { t, i18n } = useTranslation();
  const { chartTheme } = useTheme();

  const [selectedGroupKey, setSelectedGroupKey] = useState(INDICATOR_GROUPS[0].key);
  const [dateFilter, setDateFilter] = useState("24M");
  const [tableRows, setTableRows] = useState([]);
  const [tableLoading, setTableLoading] = useState(true);
  const [tableError, setTableError] = useState("");
  const [overviewData, setOverviewData] = useState({});
  const [overviewLoading, setOverviewLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function loadOverview() {
      try {
        const results = await Promise.allSettled(
          OVERVIEW_INDICATORS.map((ind) => getMacroObservations(ind.code, ind.valueType)),
        );
        if (!active) return;
        const next = {};
        OVERVIEW_INDICATORS.forEach((ind, i) => {
          const obs = results[i].status === "fulfilled" ? (results[i].value ?? []) : [];
          if (obs.length > 0) next[ind.key] = Number(obs[obs.length - 1].value);
        });
        setOverviewData(next);
      } finally {
        if (active) setOverviewLoading(false);
      }
    }
    loadOverview();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const group = INDICATOR_GROUPS.find((g) => g.key === selectedGroupKey);
    if (!group) return;
    let active = true;
    setTableLoading(true);
    setTableError("");
    // Keep old rows visible during transition — avoids scroll-to-bottom on height collapse
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
    return () => { active = false; };
  }, [selectedGroupKey, t]);

  const selectedGroup = useMemo(
    () => INDICATOR_GROUPS.find((g) => g.key === selectedGroupKey) ?? INDICATOR_GROUPS[0],
    [selectedGroupKey],
  );

  const { summary, impacts } = useMemo(
    () => generateOverview(overviewData, i18n.language),
    [overviewData, i18n.language],
  );

  const filteredRows = useMemo(() => {
    if (dateFilter === "12M") return tableRows.slice(0, 12);
    if (dateFilter === "24M") return tableRows.slice(0, 24);
    return tableRows;
  }, [tableRows, dateFilter]);

  // Last 24 months, oldest-first for chart
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
      [row.period, ...row.values.map((v) => (v != null ? Number(v).toFixed(2) : ""))].join(",")
    );
    const blob = new Blob(["﻿" + [header, ...rows].join("\n")], { type: "text/csv;charset=utf-8;" });
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

  // KPI: primary series (index 0), unfiltered
  const kpiNewest  = tableRows[0];
  const kpiPrev    = tableRows.length > 1 ? tableRows[1] : null;
  const kpiValue   = kpiNewest?.values[0] ?? null;
  const kpiPrevVal = kpiPrev?.values[0] ?? null;
  const kpiChange  = kpiValue != null && kpiPrevVal != null ? Number(kpiValue) - Number(kpiPrevVal) : null;

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow={t("economy.eyebrow")}
        title={t("economy.title")}
        description={t("economy.description")}
      />

      {/* ── Economic Overview ─────────────────────────────────────────── */}
      <section className="panel-surface economy-section">
        <div className="panel-head">
          <div>
            <p className="eyebrow">{t("economy.overview.eyebrow")}</p>
            <h3>{t("economy.overview.title")}</h3>
          </div>
          <span className="terminal-badge muted">{t("economy.table.tcmbSource")}</span>
        </div>
        {overviewLoading ? (
          <LoadingSpinner />
        ) : !summary && impacts.length === 0 ? (
          <p className="economy-overview-nodata">{t("economy.overview.noData")}</p>
        ) : (
          <div className="economy-overview-grid">
            {summary && (
              <div className="economy-overview-col">
                <p className="economy-overview-label">{t("economy.overview.summaryTitle")}</p>
                <p className="economy-overview-text">{summary}</p>
              </div>
            )}
            {impacts.length > 0 && (
              <div className="economy-overview-col">
                <p className="economy-overview-label">{t("economy.overview.impactsTitle")}</p>
                <ul className="economy-impacts-list">
                  {impacts.map((item, i) => <li key={i}>{item}</li>)}
                </ul>
              </div>
            )}
          </div>
        )}
      </section>

      {/* ── Macro data section ────────────────────────────────────────── */}
      <section className="panel-surface economy-section">
        <div className="panel-head">
          <div>
            <p className="eyebrow">{t("economy.source")}</p>
            <h3>{t(`economy.groups.${selectedGroupKey}.title`)}</h3>
          </div>
        </div>

        {/* Compact indicator tabs */}
        <div className="economy-tab-row">
          {INDICATOR_GROUPS.map((group) => (
            <button
              key={group.key}
              type="button"
              className={`economy-tab-btn${selectedGroupKey === group.key ? " active" : ""}`}
              title={t(`economy.groups.${group.key}.title`)}
              onClick={() => handleGroupChange(group.key)}
            >
              {t(`economy.groups.${group.key}.label`)}
            </button>
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
            {/* KPI cards */}
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

            {/* Chart header + legend */}
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

            {/* Trend chart – last 24 months */}
            <div className="economy-chart-wrap">
              {chartData.length < 2 ? (
                <div className="economy-chart-empty">{t("economy.chartNoData")}</div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                    <defs>
                      {selectedGroup.series.map((_, i) => (
                        <linearGradient key={i} id={`ecGrad${i}`} x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%"  stopColor={CHART_COLORS[i % CHART_COLORS.length]} stopOpacity={0.18} />
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

            {/* Filter bar */}
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

            {/* Data table */}
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
