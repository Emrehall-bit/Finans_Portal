import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ArrowRight, Gauge, TrendingUp } from "lucide-react";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { useTheme } from "../../theme/ThemeContext";
import { formatNumber } from "../../utils/formatters";
import { useCurrency } from "../../currency/CurrencyContext";
import { formatAxisNumber, formatSignalLabel, formatTrendLabel, resolveTrendDirection } from "./analysisUtils";
import { formatInstrumentCode } from "../../utils/instrumentUtils";

export default function SimpleAnalysisChart({
  activeRange,
  onRangeChange,
  loading,
  error,
  chartData,
  presets,
  quote,
  analysis,
  primaryContext,
  onOpenAdvanced,
  quotes = [],
  primarySymbol = "",
  selectedSymbols = [],
  onToggleComparisonSymbol,
}) {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const { currency } = useCurrency();
  const hasData = chartData.length > 0;
  const trendDirection = useMemo(
    () => resolveTrendDirection(analysis?.trendDirection, chartData),
    [analysis?.trendDirection, chartData],
  );
  const latestPoint = hasData ? chartData.at(-1) : null;
  const previousPoint = chartData.length > 1 ? chartData.at(-2) : null;
  const latestRsi = useMemo(
    () => resolveLatestRsi(analysis, chartData),
    [analysis, chartData],
  );
  const lastPrice = firstFinite(quote?.sellRate, quote?.price, analysis?.latestPrice, latestPoint?.close);
  const dailyChange = firstFinite(quote?.changeRate, analysis?.latestChangePct, derivePercentChange(previousPoint?.close, latestPoint?.close));
  const volumeValue = firstFinite(
    quote?.volume,
    quote?.totalVolume,
    analysis?.points?.at?.(-1)?.volume,
  );
  const summary = useMemo(
    () => buildSimpleSummary({ analysis, chartData, quote, trendDirection, latestRsi }),
    [analysis, chartData, quote, trendDirection, latestRsi],
  );
  const sparklineData = useMemo(() => buildSparklineData(chartData), [chartData]);
  const suggestions = useMemo(() => deriveComparisonSuggestions(quotes, primarySymbol), [quotes, primarySymbol]);
  const axisLabel = currency === "USD" ? "$" : "\u20ba";

  const metrics = [
    {
      label: t("instrumentDetail.latestPrice"),
      value: lastPrice != null ? formatNumber(lastPrice, 2) : "-",
      tone: "neutral",
      sparkTone: "positive",
    },
    {
      label: t("instrumentDetail.dailyChange"),
      value: dailyChange != null ? `${dailyChange >= 0 ? "+" : ""}${Number(dailyChange).toFixed(2)}%` : "-",
      tone: dailyChange == null ? "neutral" : dailyChange >= 0 ? "positive" : "negative",
      sparkTone: dailyChange == null ? "neutral" : dailyChange >= 0 ? "positive" : "negative",
    },
    {
      label: t("instrumentDetail.trend"),
      value: trendDirection ? formatTrendLabel(trendDirection) : "-",
      tone: summary.scoreTone,
      sparkTone: summary.scoreTone,
      icon: <TrendingUp size={15} strokeWidth={2.3} />,
    },
    {
      label: "RSI (14)",
      value: latestRsi != null ? Number(latestRsi).toFixed(2) : "-",
      tone: latestRsi == null ? "neutral" : latestRsi >= 70 ? "warning" : latestRsi <= 30 ? "positive" : "neutral",
      sparkTone: "info",
    },
    {
      label: t("analysis.chart.tooltip.volume"),
      value: volumeValue != null ? formatNumber(volumeValue, 0) : "-",
      tone: "neutral",
      sparkTone: "neutral",
    },
  ];

  return (
    <section className="simple-analysis-panel">
      <div className="simple-analysis-range-row">
        <div className="simple-analysis-ranges">
          {presets.map((preset) => (
            <button
              key={preset.key}
              type="button"
              className={`simple-range-chip ${activeRange === preset.key ? "active" : ""}`}
              onClick={() => onRangeChange(preset)}
            >
              {preset.key}
            </button>
          ))}
        </div>
      </div>

      <div className="simple-analysis-summary">
        {metrics.map((metric) => (
          <SummaryMetric key={metric.label} metric={metric} sparklineData={sparklineData} />
        ))}
      </div>

      {loading ? <LoadingSpinner label={t("instrumentDetail.chartLoading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && !hasData ? (
        <EmptyState title={t("instrumentDetail.chartEmptyTitle")} description={t("instrumentDetail.chartEmptyDescription")} />
      ) : null}

      {!loading && !error && hasData ? (
        <div className="simple-analysis-workspace">
          <div className="simple-analysis-chart-card">
            <div className="simple-analysis-chart-header">
              <h3>{primaryContext?.symbolLine || "-"}</h3>
            </div>
            <ResponsiveContainer width="100%" height={580}>
              <LineChart data={chartData} margin={{ top: 20, right: 20, bottom: 32, left: 28 }} padding={{ left: 14, right: 4 }}>
                <defs>
                  <linearGradient id="simple-analysis-fill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.22} />
                    <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} strokeDasharray="2 8" stroke={chartTheme.grid} />
                <XAxis
                  dataKey="date"
                  stroke={chartTheme.axis}
                  tickLine={false}
                  axisLine={false}
                  tick={{ fontSize: 12 }}
                  tickMargin={10}
                />
                <YAxis
                  stroke={chartTheme.axis}
                  tickLine={false}
                  axisLine={false}
                  width={72}
                  tick={{ fontSize: 12 }}
                  tickMargin={10}
                  tickFormatter={(value) => `${axisLabel}${formatAxisNumber(value)}`}
                />
                <Tooltip content={<SimpleTooltip chartTheme={chartTheme} axisLabel={axisLabel} />} />
                <Area type="monotone" dataKey="close" stroke="none" fill="url(#simple-analysis-fill)" />
                <Line type="monotone" dataKey="close" name={t("instrumentDetail.price")} stroke="#2563eb" strokeWidth={2.8} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <aside className="simple-tech-summary-card">
            <div className="simple-tech-summary-head">
              <span className="simple-tech-summary-label">{t("analysis.chart.techPanel.title")}</span>
              <strong className={`simple-tech-summary-signal simple-tech-summary-signal--${summary.scoreTone}`}>
                {summary.scoreLabel}
              </strong>
            </div>

            <div className="simple-score-kpi">
              <span className={`simple-score-kpi-value simple-score-kpi-value--${summary.scoreTone}`}>
                {Math.round(summary.scorePercent ?? 0)}
                <span className="simple-score-kpi-total">/100</span>
              </span>
              <div className="simple-score-bar">
                <div
                  className={`simple-score-bar-fill simple-score-bar-fill--${summary.scoreTone}`}
                  style={{ width: `${Math.round(summary.scorePercent ?? 0)}%` }}
                />
              </div>
            </div>

            <div className="simple-tech-summary-list">
              <SummaryRow label="RSI (14)" value={latestRsi != null ? Number(latestRsi).toFixed(1) : "-"} tone={toneFromRsi(latestRsi)} />
              <SummaryRow label={t("analysis.chart.techPanel.trend")} value={trendDirection ? formatTrendLabel(trendDirection) : "-"} tone={summary.scoreTone} />
              <SummaryRow
                label={t("analysis.chart.techPanel.latestSignal")}
                value={summary.latestSignal ? formatSignalLabel(summary.latestSignal) : t("analysis.chart.signal.neutral.short")}
                tone={summary.signalTone}
              />
              <SummaryRow label={t("analysis.chart.techPanel.momentum")} value={summary.momentumLabel} tone={summary.momentumTone} />
              <SummaryRow label={t("analysis.chart.techPanel.volatility")} value={summary.volatilityLabel} tone={summary.volatilityTone} />
              <SummaryRow label="50 MA / 200 MA" value={summary.maPairLabel} tone={summary.maTone} />
            </div>

            {suggestions.length > 0 ? (
              <div className="simple-panel-compare">
                <span className="simple-panel-compare-label">{t("analysis.comparison.eyebrow")}</span>
                <div className="simple-panel-chips">
                  {suggestions.map((symbol) => (
                    <button
                      key={symbol}
                      type="button"
                      className={`simple-panel-chip${selectedSymbols.includes(symbol) ? " active" : ""}`}
                      onClick={() => onToggleComparisonSymbol?.(symbol)}
                    >
                      {resolveChipLabel(symbol, quotes)}
                    </button>
                  ))}
                </div>
              </div>
            ) : null}

            <button type="button" className="simple-tech-summary-cta" onClick={onOpenAdvanced}>
              <Gauge size={16} strokeWidth={2.2} />
              <span>{t("analysis.advancedChart")}</span>
              <ArrowRight size={16} strokeWidth={2.2} />
            </button>
          </aside>
        </div>
      ) : null}
    </section>
  );
}

function SummaryMetric({ metric, sparklineData }) {
  const quietPlaceholder = metric.value === "-";

  return (
    <div className={`simple-analysis-metric simple-analysis-metric--${metric.tone}`}>
      <div className="simple-analysis-metric-copy">
        <span>{metric.label}</span>
        <strong className={quietPlaceholder ? "is-placeholder" : ""}>{metric.value}</strong>
      </div>
      <div className={`simple-analysis-spark simple-analysis-spark--${metric.sparkTone}`}>
        {metric.icon ? <span className="simple-analysis-metric-icon">{metric.icon}</span> : null}
        <ResponsiveContainer width="100%" height={24}>
          <AreaChart data={sparklineData}>
            <Area type="monotone" dataKey="value" stroke={sparkColor(metric.sparkTone)} fill="none" strokeWidth={2} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function SummaryRow({ label, value, tone = "neutral" }) {
  return (
    <div className="simple-tech-summary-row">
      <span>{label}</span>
      <span className={`simple-summary-chip simple-tech-summary-value--${tone}`}>{value}</span>
    </div>
  );
}


function SimpleTooltip({ active, payload, label, chartTheme, axisLabel }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
      <strong>{label}</strong>
      {payload.map((item) => (
        <div key={item.dataKey} className="chart-tooltip-row">
          <span>{item.name}</span>
          <strong>{axisLabel}{formatAxisNumber(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}

function buildSparklineData(chartData) {
  return chartData.slice(-24).map((point, index) => ({
    idx: index,
    value: Number(point?.close) || 0,
  }));
}

function buildSimpleSummary({ analysis, chartData, quote, trendDirection, latestRsi }) {
  const closes = chartData.map((point) => Number(point?.close)).filter(Number.isFinite);
  const latestClose = closes.at(-1) ?? firstFinite(quote?.sellRate, quote?.price, analysis?.latestPrice);
  const ma50 = movingAverage(closes, 50);
  const ma200 = movingAverage(closes, 200);
  const momentumPct = closes.length >= 6 ? derivePercentChange(closes.at(-6), closes.at(-1)) : null;
  const volatilityPct = closes.length >= 2 ? Math.abs(derivePercentChange(closes.at(-2), closes.at(-1)) ?? 0) : null;
  const latestSignal = resolveLatestSignal(analysis?.signals?.[0]);

  let score = 50;
  if (trendDirection === "UPTREND") score += 18;
  if (trendDirection === "DOWNTREND") score -= 18;
  if (momentumPct != null) score += Math.max(-14, Math.min(14, momentumPct * 2.4));
  if (latestRsi != null) score += latestRsi >= 60 ? 10 : latestRsi <= 40 ? -10 : 0;
  if (ma50 != null && ma200 != null) score += ma50 > ma200 ? 12 : -12;

  const scorePercent = Math.max(0, Math.min(100, score));
  const scoreTone = scorePercent >= 72 ? "positive" : scorePercent >= 55 ? "info" : scorePercent <= 35 ? "negative" : "neutral";
  const scoreLabel = scorePercent >= 78
    ? "Güçlü yükseliş"
    : scorePercent >= 58
      ? "Zayıf yükseliş"
      : scorePercent <= 28
        ? "Zayıf düşüş"
        : "Nötr";

  return {
    scorePercent,
    scoreTone,
    scoreLabel,
    latestSignal,
    signalTone: inferSignalTone(latestSignal),
    momentumLabel: momentumPct == null ? "-" : momentumPct >= 1.4 ? "Pozitif" : momentumPct <= -1.4 ? "Negatif" : "Nötr",
    momentumTone: momentumPct == null ? "neutral" : momentumPct >= 1.4 ? "positive" : momentumPct <= -1.4 ? "negative" : "neutral",
    volatilityLabel: volatilityPct == null ? "-" : volatilityPct >= 4 ? "Yüksek" : volatilityPct >= 2 ? "Orta" : "Düşük",
    volatilityTone: volatilityPct == null ? "neutral" : volatilityPct >= 4 ? "warning" : volatilityPct >= 2 ? "neutral" : "positive",
    maPairLabel: ma50 == null || ma200 == null
      ? "Yetersiz veri"
      : ma50 > ma200
        ? "Yukarı kesişim"
        : ma50 < ma200
          ? "Aşağı kesişim"
          : "Denge",
    maTone: ma50 == null || ma200 == null
      ? "neutral"
      : ma50 > ma200
        ? "positive"
        : ma50 < ma200
          ? "negative"
          : "neutral",
  };
}

function movingAverage(values, period) {
  if (!Array.isArray(values) || values.length < period) {
    return null;
  }
  const window = values.slice(-period);
  if (window.some((value) => !Number.isFinite(value))) {
    return null;
  }
  return window.reduce((sum, value) => sum + value, 0) / period;
}

function derivePercentChange(from, to) {
  const start = Number(from);
  const end = Number(to);
  if (!Number.isFinite(start) || !Number.isFinite(end) || start === 0) {
    return null;
  }
  return ((end - start) / Math.abs(start)) * 100;
}

function firstFinite(...values) {
  for (const value of values) {
    if (value === null || value === undefined || value === "") {
      continue;
    }
    const numeric = Number(value);
    if (Number.isFinite(numeric)) {
      return numeric;
    }
  }
  return null;
}

function resolveLatestRsi(analysis, chartData) {
  const lastPointRsi = chartData
    .map((point) => point?.rsi14)
    .findLast((value) => value !== null && value !== undefined && value !== "");

  const analysisRsi = analysis?.indicatorValues
    ?.find?.((item) => String(item?.indicator).toUpperCase().includes("RSI"))
    ?.value;

  return firstFinite(lastPointRsi, analysisRsi);
}

function resolveLatestSignal(raw) {
  if (raw == null) return null;
  if (typeof raw === "string") return raw;
  return raw?.signalType || raw?.label || null;
}

function inferSignalTone(signal) {
  const raw = String(signal || "").toUpperCase();
  if (!raw) return "neutral";
  if (raw.includes("ABOVE") || raw.includes("OVERSOLD") || raw.includes("BUY")) return "positive";
  if (raw.includes("BELOW") || raw.includes("OVERBOUGHT") || raw.includes("SELL")) return "negative";
  return "neutral";
}

function toneFromRsi(rsi) {
  if (rsi == null) return "neutral";
  if (rsi >= 70) return "warning";
  if (rsi <= 30) return "positive";
  return "neutral";
}

function sparkColor(tone) {
  switch (tone) {
    case "positive":
      return "#22c55e";
    case "negative":
      return "#ef4444";
    case "info":
      return "#4f7cff";
    default:
      return "#94a3b8";
  }
}
