import { useEffect, useState } from "react";
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

// ── Formatters ────────────────────────────────────────────────────────────────

function fmtLarge(num) {
  if (num == null) return "Veri Yok";
  const abs = Math.abs(Number(num));
  const n = Number(num);
  if (abs >= 1e12) return `${(n / 1e12).toFixed(2)} Trl TRY`;
  if (abs >= 1e9) return `${(n / 1e9).toFixed(2)} Myr TRY`;
  if (abs >= 1e6) return `${(n / 1e6).toFixed(2)} Mln TRY`;
  return n.toLocaleString("tr-TR");
}

function fmtDecimal(val, decimals = 2, suffix = "") {
  if (val == null) return "—";
  return `${Number(val).toFixed(decimals)}${suffix}`;
}

// ── Ratio config ──────────────────────────────────────────────────────────────

const RATIO_CONFIG = [
  {
    key: "peRatio",
    label: "F/K",
    good: (v) => v < 10,
    bad: (v) => v > 25,
    tooltip: "Fiyat / Kazanç. Düşük = ucuz görünür",
  },
  {
    key: "pbRatio",
    label: "PD/DD",
    good: (v) => v < 1,
    bad: (v) => v > 3,
    tooltip: "Piyasa Değeri / Defter Değeri. 1'in altı iskontolu",
  },
  {
    key: "grossMargin",
    label: "Brüt Marj",
    good: (v) => v > 20,
    bad: (v) => v < 5,
    tooltip: "Satışların yüzde kaçı brüt kar",
    suffix: "%",
  },
  {
    key: "netMargin",
    label: "Net Marj",
    good: (v) => v > 10,
    bad: (v) => v < 0,
    tooltip: "Satışların yüzde kaçı net kar",
    suffix: "%",
  },
  {
    key: "roe",
    label: "ROE",
    good: (v) => v > 15,
    bad: (v) => v < 5,
    tooltip: "Özsermaye karlılığı",
    suffix: "%",
  },
  {
    key: "roa",
    label: "ROA",
    good: (v) => v > 5,
    bad: (v) => v < 1,
    tooltip: "Aktif karlılığı",
    suffix: "%",
  },
  {
    key: "currentRatio",
    label: "Cari Oran",
    good: (v) => v > 1.5,
    bad: (v) => v < 1,
    tooltip: "Kısa vadeli borç ödeme gücü. 1'in üzeri iyi",
  },
  {
    key: "debtToEquity",
    label: "Borç/Özkaynak",
    good: (v) => v < 0.5,
    bad: (v) => v > 2,
    tooltip: "Finansal kaldıraç. Düşük = az borç",
  },
];

function ratioColorCls(config, value) {
  if (value == null) return "";
  if (config.good?.(value)) return "ratio-cell--good";
  if (config.bad?.(value)) return "ratio-cell--bad";
  return "ratio-cell--neutral";
}

// ── Signal badge ──────────────────────────────────────────────────────────────

function SignalBadge({ signal }) {
  if (!signal) return null;
  const map = {
    BULLISH: { label: "YÜKSELİŞÇİ", cls: "fa-signal-badge fa-signal-badge--bull" },
    NEUTRAL: { label: "NÖTR", cls: "fa-signal-badge fa-signal-badge--neutral" },
    BEARISH: { label: "DÜŞÜŞÇÜ", cls: "fa-signal-badge fa-signal-badge--bear" },
  };
  const cfg = map[signal] ?? map.NEUTRAL;
  return <span className={cfg.cls}>{cfg.label}</span>;
}

// ── Piotroski bar ─────────────────────────────────────────────────────────────

function PiotroskiBar({ score }) {
  if (score == null) return <span className="fa-premium-hint">Premium</span>;
  const pct = Math.round((score / 9) * 100);
  const cls = score >= 7 ? "pio-fill--strong" : score >= 4 ? "pio-fill--mid" : "pio-fill--weak";
  const label = score >= 7 ? "Güçlü" : score >= 4 ? "Orta" : "Zayıf";
  return (
    <div className="fa-pio-wrap">
      <div className="fa-pio-track">
        <div className={`fa-pio-fill ${cls}`} style={{ width: `${pct}%` }} />
      </div>
      <span className={`fa-pio-label ${cls}`}>
        {score}/9 — {label}
      </span>
    </div>
  );
}

// ── Altman badge ──────────────────────────────────────────────────────────────

function AltmanBadge({ score }) {
  if (score == null) return <span className="fa-premium-hint">Premium</span>;
  const v = Number(score);
  let zone, cls;
  if (v > 2.99) {
    zone = "Güvenli Bölge";
    cls = "fa-altman--safe";
  } else if (v >= 1.81) {
    zone = "Gri Bölge";
    cls = "fa-altman--grey";
  } else {
    zone = "Tehlike Bölgesi";
    cls = "fa-altman--danger";
  }
  return (
    <span className={`fa-altman-badge ${cls}`}>
      {fmtDecimal(score, 2)} — {zone}
    </span>
  );
}

// ── Growth badge ──────────────────────────────────────────────────────────────

function GrowthBadge({ value }) {
  if (value == null) return <span>—</span>;
  const pos = Number(value) >= 0;
  return (
    <span className={`fa-growth-badge ${pos ? "fa-growth-badge--pos" : "fa-growth-badge--neg"}`}>
      {pos ? "+" : ""}
      {Number(value).toFixed(1)}%
    </span>
  );
}

// ── Custom chart tooltip ──────────────────────────────────────────────────────

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

// ── Main component ────────────────────────────────────────────────────────────

export default function FundamentalAnalysis({ instrumentCode }) {
  const [ratios, setRatios] = useState(null);
  const [quarterly, setQuarterly] = useState([]);
  const [loading, setLoading] = useState(true);

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
  if (!ratios) return <FundamentalEmpty />;

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
  const grahamNum = ratios.grahamNumber != null ? Number(ratios.grahamNumber) : null;
  let grahamDiff = null;
  if (currentPrice && grahamNum && grahamNum > 0) {
    grahamDiff = Number((((currentPrice - grahamNum) / grahamNum) * 100).toFixed(1));
  }

  return (
    <section className="panel-surface fa-card">
      {/* ── Header ── */}
      <div className="fa-header">
        <h3 className="fa-title">Temel Analiz</h3>
        <div className="fa-header-right">
          {ratios.period && <span className="fa-period-chip">{ratios.period}</span>}
          <SignalBadge signal={ratios.overallSignal} />
        </div>
      </div>

      {/* ── Section 1: Financial summary cards ── */}
      <div className="fa-summary-grid">
        <SummaryCard label="Hasılat" value={fmtLarge(latest?.revenue)} />
        <SummaryCard
          label="Net Dönem Karı"
          value={fmtLarge(latest?.netIncome)}
          negative={latest?.netIncome != null && Number(latest.netIncome) < 0}
        />
        <SummaryCard label="Özkaynaklar" value={fmtLarge(latest?.totalEquity)} />
        <SummaryCard label="Toplam Varlıklar" value={fmtLarge(latest?.totalAssets)} />
      </div>

      {/* ── Section 2: Ratio grid ── */}
      <div className="fa-section-label">Finansal Oranlar</div>
      <div className="fa-ratios-grid">
        {RATIO_CONFIG.map((cfg) => {
          const raw = ratios[cfg.key];
          const val = raw != null ? Number(raw) : null;
          return (
            <div
              key={cfg.key}
              className={`fa-ratio-cell ${ratioColorCls(cfg, val)}`}
              title={cfg.tooltip}
            >
              <span className="fa-ratio-label">{cfg.label}</span>
              <span className="fa-ratio-value">
                {val != null ? fmtDecimal(val, 2, cfg.suffix ?? "") : "—"}
              </span>
            </div>
          );
        })}
      </div>

      {/* ── Section 3: Scores + Trend chart ── */}
      <div className="fa-bottom-grid">
        {/* Scores column */}
        <div className="fa-scores">
          <div className="fa-section-label">Gelişmiş Skorlar</div>

          <div className="fa-score-row">
            <span className="fa-score-key">Piotroski F-Score</span>
            <PiotroskiBar score={ratios.piotroskiScore} />
          </div>

          <div className="fa-score-row">
            <span className="fa-score-key">Altman Z-Score</span>
            <AltmanBadge score={ratios.altmanZScore} />
          </div>

          <div className="fa-score-row">
            <span className="fa-score-key">Graham Sayısı</span>
            {grahamNum != null ? (
              <span className={`fa-graham-val ${grahamDiff != null && grahamDiff > 0 ? "fa-graham--pricey" : "fa-graham--cheap"}`}>
                {fmtDecimal(grahamNum, 2)} TRY
                {grahamDiff != null && (
                  <span className="fa-graham-diff">
                    {grahamDiff > 0 ? `%${grahamDiff} primli` : `%${Math.abs(grahamDiff)} iskontolu`}
                  </span>
                )}
              </span>
            ) : (
              <span className="fa-premium-hint">Premium</span>
            )}
          </div>

          {currentPrice != null && (
            <div className="fa-score-row">
              <span className="fa-score-key">Mevcut Fiyat</span>
              <span className="fa-score-price">{fmtDecimal(currentPrice, 2)} TRY</span>
            </div>
          )}

          {/* YoY growth block */}
          {(ratios.revenueGrowthYoy != null ||
            ratios.netIncomeGrowthYoy != null ||
            ratios.assetGrowthYoy != null) && (
            <>
              <div className="fa-section-label" style={{ marginTop: 18 }}>
                YoY Büyüme
              </div>
              {ratios.revenueGrowthYoy != null && (
                <div className="fa-score-row">
                  <span className="fa-score-key">Hasılat</span>
                  <GrowthBadge value={ratios.revenueGrowthYoy} />
                </div>
              )}
              {ratios.netIncomeGrowthYoy != null && (
                <div className="fa-score-row">
                  <span className="fa-score-key">Net Kar</span>
                  <GrowthBadge value={ratios.netIncomeGrowthYoy} />
                </div>
              )}
              {ratios.assetGrowthYoy != null && (
                <div className="fa-score-row">
                  <span className="fa-score-key">Varlıklar</span>
                  <GrowthBadge value={ratios.assetGrowthYoy} />
                </div>
              )}
            </>
          )}
        </div>

        {/* Trend chart column */}
        <div className="fa-chart-col">
          <div className="fa-section-label">Çeyrek Trend</div>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={chartData} margin={{ top: 4, right: 16, bottom: 4, left: 0 }}>
                <XAxis
                  dataKey="period"
                  tick={{ fontSize: 11, fill: "#94a3b8" }}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  yAxisId="rev"
                  orientation="left"
                  tick={{ fontSize: 11, fill: "#94a3b8" }}
                  tickLine={false}
                  axisLine={false}
                  tickFormatter={(v) => `${v}M`}
                  width={46}
                />
                <YAxis
                  yAxisId="margin"
                  orientation="right"
                  tick={{ fontSize: 11, fill: "#94a3b8" }}
                  tickLine={false}
                  axisLine={false}
                  tickFormatter={(v) => `${v}%`}
                  width={42}
                />
                <ReferenceLine
                  yAxisId="margin"
                  y={0}
                  stroke="#94a3b8"
                  strokeDasharray="3 3"
                  strokeWidth={1}
                />
                <Tooltip content={<ChartTooltip />} />
                <Line
                  yAxisId="rev"
                  type="monotone"
                  dataKey="revenue"
                  name="Hasılat (Myr)"
                  stroke="#3b82f6"
                  strokeWidth={2}
                  dot={{ r: 3, fill: "#3b82f6", strokeWidth: 0 }}
                  activeDot={{ r: 5 }}
                />
                <Line
                  yAxisId="margin"
                  type="monotone"
                  dataKey="netMargin"
                  name="Net Marj (%)"
                  stroke="#22c55e"
                  strokeWidth={2}
                  dot={{ r: 3, fill: "#22c55e", strokeWidth: 0 }}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="fa-no-chart">Çeyreklik veri bulunamadı</div>
          )}
        </div>
      </div>
    </section>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

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
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="fa-skeleton-card" />
        ))}
      </div>
      <div className="fa-skeleton-ratios">
        {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
          <div key={i} className="fa-skeleton-ratio" />
        ))}
      </div>
    </section>
  );
}

function FundamentalEmpty() {
  return (
    <section className="panel-surface fa-card fa-card--empty">
      <span className="fa-empty-icon">📊</span>
      <p className="fa-empty-text">Bu enstrüman için temel analiz verisi bulunamadı</p>
    </section>
  );
}
