import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getFinancialData, getFundamentalRatios } from "../../api/analysisApi";

const RATIO_CONFIG = [
  { key: "peRatio",      good: (v) => v < 10,  bad: (v) => v > 25 },
  { key: "pbRatio",      good: (v) => v < 1,   bad: (v) => v > 3  },
  { key: "grossMargin",  good: (v) => v > 20,  bad: (v) => v < 5,  suffix: "%" },
  { key: "netMargin",    good: (v) => v > 10,  bad: (v) => v < 0,  suffix: "%" },
  { key: "roe",          good: (v) => v > 15,  bad: (v) => v < 5,  suffix: "%" },
  { key: "roa",          good: (v) => v > 5,   bad: (v) => v < 1,  suffix: "%" },
  { key: "currentRatio", good: (v) => v > 1.5, bad: (v) => v < 1  },
  { key: "debtToEquity", good: (v) => v < 0.5, bad: (v) => v > 2  },
];

function fmtLarge(num, t) {
  if (num == null) return t("fundamental.noData");
  const abs = Math.abs(Number(num));
  const n = Number(num);
  if (abs >= 1e12) return `${(n / 1e12).toFixed(2)} ${t("fundamental.units.trillion")}`;
  if (abs >= 1e9)  return `${(n / 1e9).toFixed(2)} ${t("fundamental.units.billion")}`;
  if (abs >= 1e6)  return `${(n / 1e6).toFixed(2)} ${t("fundamental.units.million")}`;
  return n.toLocaleString();
}

function fmtDecimal(val, decimals = 2, suffix = "") {
  if (val == null) return "—";
  return `${Number(val).toFixed(decimals)}${suffix}`;
}

function ratioColorCls(config, value) {
  if (value == null) return "";
  if (config.good?.(value)) return "ratio-cell--good";
  if (config.bad?.(value))  return "ratio-cell--bad";
  return "ratio-cell--neutral";
}

function SignalBadge({ signal }) {
  const { t } = useTranslation();
  if (!signal) return null;
  const clsMap = {
    BULLISH: "fa-signal-badge fa-signal-badge--bull",
    NEUTRAL: "fa-signal-badge fa-signal-badge--neutral",
    BEARISH: "fa-signal-badge fa-signal-badge--bear",
  };
  const cls = clsMap[signal] ?? clsMap.NEUTRAL;
  return <span className={cls}>{t(`fundamental.signal.${signal}`, signal)}</span>;
}

function PiotroskiBar({ score }) {
  const { t } = useTranslation();
  if (score == null) return <span className="fa-premium-hint">Premium</span>;
  const pct = Math.round((score / 9) * 100);
  const strength = score >= 7 ? "strong" : score >= 4 ? "mid" : "weak";
  const cls = `pio-fill--${strength}`;
  return (
    <div className="fa-pio-wrap">
      <div className="fa-pio-track">
        <div className={`fa-pio-fill ${cls}`} style={{ width: `${pct}%` }} />
      </div>
      <span className={`fa-pio-label ${cls}`}>
        {score}/9 — {t(`fundamental.piotroski.${strength}`)}
      </span>
    </div>
  );
}

function AltmanBadge({ score }) {
  const { t } = useTranslation();
  if (score == null) return <span className="fa-premium-hint">Premium</span>;
  const v = Number(score);
  let zoneKey, cls;
  if (v > 2.99) {
    zoneKey = "safe";
    cls = "fa-altman--safe";
  } else if (v >= 1.81) {
    zoneKey = "grey";
    cls = "fa-altman--grey";
  } else {
    zoneKey = "danger";
    cls = "fa-altman--danger";
  }
  return (
    <span className={`fa-altman-badge ${cls}`}>
      {fmtDecimal(score, 2)} — {t(`fundamental.altman.${zoneKey}`)}
    </span>
  );
}

function GrowthBadge({ value }) {
  if (value == null) return <span>—</span>;
  const pos = Number(value) >= 0;
  return (
    <span className={`fa-growth-badge ${pos ? "fa-growth-badge--pos" : "fa-growth-badge--neg"}`}>
      {pos ? "+" : ""}{Number(value).toFixed(1)}%
    </span>
  );
}

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="fa-chart-tooltip">
      <div className="fa-ct-period">{label}</div>
      {payload.map((p) => (
        <div key={p.dataKey} className="fa-ct-row" style={{ color: p.color }}>
          <span>{p.name}</span>
          <strong>{p.value != null ? p.value.toFixed(2) : "—"}</strong>
        </div>
      ))}
    </div>
  );
}

export default function FundamentalAnalysis({ instrumentCode }) {
  const { t } = useTranslation();
  const [ratios, setRatios] = useState(null);
  const [quarterly, setQuarterly] = useState([]);
  const [loading, setLoading] = useState(true);

  const ratioConfig = useMemo(
    () => RATIO_CONFIG.map((cfg) => ({
      ...cfg,
      label:   t(`fundamental.ratios.${cfg.key}.label`),
      tooltip: t(`fundamental.ratios.${cfg.key}.tooltip`),
    })),
    [t],
  );

  useEffect(() => {
    if (!instrumentCode) return;
    setLoading(true);
    setRatios(null);
    setQuarterly([]);

    Promise.all([
      getFundamentalRatios(instrumentCode).catch(() => null),
      getFinancialData(instrumentCode, "QUARTERLY").catch(() => []),
    ]).then(([r, q]) => {
      setRatios(r);
      const sorted = [...(Array.isArray(q) ? q : [])].reverse();
      setQuarterly(sorted);
      setLoading(false);
    });
  }, [instrumentCode]);

  if (loading) return <FundamentalSkeleton />;
  if (!ratios)  return <FundamentalEmpty />;

  const latest = quarterly[quarterly.length - 1] ?? null;

  const chartData = quarterly.map((q) => ({
    period: q.period,
    revenue: q.revenue != null ? +(q.revenue / 1e9).toFixed(2) : null,
    netMargin:
      q.revenue != null && q.netIncome != null && Number(q.revenue) !== 0
        ? +((Number(q.netIncome) / Number(q.revenue)) * 100).toFixed(2)
        : null,
  }));

  const currentPrice = ratios.calculationPrice != null ? Number(ratios.calculationPrice) : null;
  const grahamNum    = ratios.grahamNumber     != null ? Number(ratios.grahamNumber)     : null;
  let grahamDiff = null;
  if (currentPrice && grahamNum && grahamNum > 0) {
    grahamDiff = Number((((currentPrice - grahamNum) / grahamNum) * 100).toFixed(1));
  }

  const showGrowth =
    ratios.revenueGrowthYoy  != null ||
    ratios.netIncomeGrowthYoy != null ||
    ratios.assetGrowthYoy    != null;

  return (
    <section className="panel-surface fa-card">
      <div className="fa-header">
        <h3 className="fa-title">{t("fundamental.title")}</h3>
        <div className="fa-header-right">
          {ratios.period && <span className="fa-period-chip">{ratios.period}</span>}
          <SignalBadge signal={ratios.overallSignal} />
        </div>
      </div>

      <div className="fa-summary-grid">
        <SummaryCard label={t("fundamental.summary.revenue")}     value={fmtLarge(latest?.revenue, t)} />
        <SummaryCard
          label={t("fundamental.summary.netIncome")}
          value={fmtLarge(latest?.netIncome, t)}
          negative={latest?.netIncome != null && Number(latest.netIncome) < 0}
        />
        <SummaryCard label={t("fundamental.summary.equity")}      value={fmtLarge(latest?.totalEquity, t)} />
        <SummaryCard label={t("fundamental.summary.totalAssets")} value={fmtLarge(latest?.totalAssets, t)} />
      </div>

      <div className="fa-section-label">{t("fundamental.ratios.sectionTitle")}</div>
      <div className="fa-ratios-grid">
        {ratioConfig.map((cfg) => {
          const raw = ratios[cfg.key];
          const val = raw != null ? Number(raw) : null;
          return (
            <div key={cfg.key} className={`fa-ratio-cell ${ratioColorCls(cfg, val)}`} title={cfg.tooltip}>
              <span className="fa-ratio-label">{cfg.label}</span>
              <span className="fa-ratio-value">
                {val != null ? fmtDecimal(val, 2, cfg.suffix ?? "") : "—"}
              </span>
            </div>
          );
        })}
      </div>

      <div className="fa-bottom-grid">
        <div className="fa-scores">
          <div className="fa-section-label">{t("fundamental.scores.title")}</div>

          <div className="fa-score-row">
            <span className="fa-score-key">Piotroski F-Score</span>
            <PiotroskiBar score={ratios.piotroskiScore} />
          </div>

          <div className="fa-score-row">
            <span className="fa-score-key">Altman Z-Score</span>
            <AltmanBadge score={ratios.altmanZScore} />
          </div>

          <div className="fa-score-row">
            <span className="fa-score-key">{t("fundamental.scores.grahamNumber")}</span>
            {grahamNum != null ? (
              <span className={`fa-graham-val ${grahamDiff != null && grahamDiff > 0 ? "fa-graham--pricey" : "fa-graham--cheap"}`}>
                {fmtDecimal(grahamNum, 2)} TRY
                {grahamDiff != null && (
                  <span className="fa-graham-diff">
                    {grahamDiff > 0
                      ? `%${grahamDiff} ${t("fundamental.scores.premium")}`
                      : `%${Math.abs(grahamDiff)} ${t("fundamental.scores.discount")}`}
                  </span>
                )}
              </span>
            ) : (
              <span className="fa-premium-hint">Premium</span>
            )}
          </div>

          {currentPrice != null && (
            <div className="fa-score-row">
              <span className="fa-score-key">{t("fundamental.scores.currentPrice")}</span>
              <span className="fa-score-price">{fmtDecimal(currentPrice, 2)} TRY</span>
            </div>
          )}

          {showGrowth && (
            <>
              <div className="fa-section-label" style={{ marginTop: 18 }}>
                {t("fundamental.growth.title")}
              </div>
              {ratios.revenueGrowthYoy != null && (
                <div className="fa-score-row">
                  <span className="fa-score-key">{t("fundamental.growth.revenue")}</span>
                  <GrowthBadge value={ratios.revenueGrowthYoy} />
                </div>
              )}
              {ratios.netIncomeGrowthYoy != null && (
                <div className="fa-score-row">
                  <span className="fa-score-key">{t("fundamental.growth.netIncome")}</span>
                  <GrowthBadge value={ratios.netIncomeGrowthYoy} />
                </div>
              )}
              {ratios.assetGrowthYoy != null && (
                <div className="fa-score-row">
                  <span className="fa-score-key">{t("fundamental.growth.assets")}</span>
                  <GrowthBadge value={ratios.assetGrowthYoy} />
                </div>
              )}
            </>
          )}
        </div>

        <div className="fa-chart-col">
          <div className="fa-section-label">{t("fundamental.chart.title")}</div>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={chartData} margin={{ top: 4, right: 16, bottom: 4, left: 0 }}>
                <XAxis dataKey="period" tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} />
                <YAxis yAxisId="rev" orientation="left" tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} tickFormatter={(v) => `${v}M`} width={46} />
                <YAxis yAxisId="margin" orientation="right" tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} tickFormatter={(v) => `${v}%`} width={42} />
                <ReferenceLine yAxisId="margin" y={0} stroke="#94a3b8" strokeDasharray="3 3" strokeWidth={1} />
                <Tooltip content={<ChartTooltip />} />
                <Line yAxisId="rev" type="monotone" dataKey="revenue" name={t("fundamental.chart.revenueSeries")} stroke="#3b82f6" strokeWidth={2} dot={{ r: 3, fill: "#3b82f6", strokeWidth: 0 }} activeDot={{ r: 5 }} />
                <Line yAxisId="margin" type="monotone" dataKey="netMargin" name={t("fundamental.chart.marginSeries")} stroke="#22c55e" strokeWidth={2} dot={{ r: 3, fill: "#22c55e", strokeWidth: 0 }} activeDot={{ r: 5 }} />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="fa-no-chart">{t("fundamental.chart.noData")}</div>
          )}
        </div>
      </div>
    </section>
  );
}

function SummaryCard({ label, value, negative }) {
  return (
    <div className="fa-summary-card">
      <span className="fa-summary-label">{label}</span>
      <span className={`fa-summary-value${negative ? " fa-summary-value--neg" : ""}`}>{value}</span>
    </div>
  );
}

function FundamentalSkeleton() {
  return (
    <section className="panel-surface fa-card fa-card--skeleton">
      <div className="fa-skeleton-header" />
      <div className="fa-skeleton-grid">
        {[1, 2, 3, 4].map((i) => <div key={i} className="fa-skeleton-card" />)}
      </div>
      <div className="fa-skeleton-ratios">
        {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => <div key={i} className="fa-skeleton-ratio" />)}
      </div>
    </section>
  );
}

function FundamentalEmpty() {
  const { t } = useTranslation();
  return (
    <section className="panel-surface fa-card fa-card--empty">
      <span className="fa-empty-icon">📊</span>
      <p className="fa-empty-text">{t("fundamental.emptyText")}</p>
    </section>
  );
}
