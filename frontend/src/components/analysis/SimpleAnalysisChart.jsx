import { useEffect, useMemo, useState } from "react";
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
import { AlertTriangle, ArrowRight, BookmarkPlus, Check, Gauge, Info, Minus, TrendingUp } from "lucide-react";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { useTheme } from "../../theme/ThemeContext";
import { formatCurrency, formatNumber } from "../../utils/formatters";
import { useCurrency } from "../../currency/CurrencyContext";
import { formatAxisNumber } from "./analysisUtils";
import {
  closeToCloseVolatility,
  detectInsufficientChartIndicators,
  resolveMomentumThreshold,
  resolveQuoteLatestPrice,
  resolveRsiThresholds,
  resolveSelectedRangePerformance,
  toneFromRsi,
} from "./advancedChartUtils";

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
  onAddToNotes = null,
  noteAdding = false,
}) {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const { convertAmount, currency } = useCurrency();
  const instrumentType = quote?.instrumentType;
  const hasData = chartData.length > 0;
  const latestPoint = hasData ? chartData.at(-1) : null;
  const previousPoint = chartData.length > 1 ? chartData.at(-2) : null;
  const latestRsi = useMemo(
    () => resolveLatestRsi(analysis, chartData),
    [analysis, chartData],
  );
  const rawLastPrice = firstFinite(resolveQuoteLatestPrice(quote), analysis?.latestPrice);
  const lastPrice = rawLastPrice != null ? convertAmount(rawLastPrice) : latestPoint?.close;
  const dailyChange = firstFinite(quote?.changeRate, analysis?.latestChangePct, derivePercentChange(previousPoint?.close, latestPoint?.close));
  const volumeValue = firstFinite(
    quote?.volume,
    quote?.totalVolume,
    analysis?.points?.at?.(-1)?.volume,
  );
  const sparklineData = useMemo(() => buildSparklineData(chartData), [chartData]);
  const latestMa20 = useMemo(() => resolveLatestIndicator(analysis, chartData, "sma20", "SMA20"), [analysis, chartData]);
  const latestMa50 = useMemo(() => resolveLatestIndicator(analysis, chartData, "sma50", "SMA50"), [analysis, chartData]);
  const checklist = buildTechChecklist({ lastPrice, ma20: latestMa20, ma50: latestMa50, latestRsi, instrumentType });
  const supportResistance = useMemo(() => buildSupportResistance(chartData, instrumentType), [chartData, instrumentType]);
  const yDomain = useMemo(() => buildPriceDomain(chartData), [chartData]);
  const selectedRangePerformance = useMemo(
    () => resolveSelectedRangePerformance(chartData, instrumentType, activeRange),
    [chartData, instrumentType, activeRange],
  );
  const rangeChangePct = selectedRangePerformance.totalChangePct;
  const selectedRangeTone = selectedRangePerformance.tone;
  const selectedRangeLabel = formatSelectedRangeStateLabel(selectedRangePerformance.stateKey);
  const summary = useMemo(
    () => buildSimpleSummaryModel({
      analysis,
      chartData,
      latestRsi,
      activeRange,
      instrumentType,
    }),
    [analysis, chartData, latestRsi, activeRange, instrumentType],
  );
  const insufficientIndicators = useMemo(() => detectInsufficientChartIndicators(chartData), [chartData]);
  const axisLabel = currency === "USD" ? "$" : "\u20ba";

  const [scaledDomain, setScaledDomain] = useState(null);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active) setScaledDomain(null);
    });
    return () => {
      active = false;
    };
  }, [chartData]);

  const zoomIn = () => {
    const [lo, hi] = scaledDomain ?? yDomain;
    const mid = (lo + hi) / 2;
    const half = (hi - lo) / 2 * 0.9;
    setScaledDomain([mid - half, mid + half]);
  };

  const zoomOut = () => {
    const [lo, hi] = scaledDomain ?? yDomain;
    const mid = (lo + hi) / 2;
    const half = (hi - lo) / 2 * 1.1;
    setScaledDomain([mid - half, mid + half]);
  };

  const metrics = [
    {
      label: t("instrumentDetail.latestPrice"),
      value: lastPrice != null ? formatCurrency(lastPrice, currency) : "-",
      tone: "neutral",
      sparkTone: "positive",
    },
    {
      label: t("instrumentDetail.dailyChange"),
      value: dailyChange != null ? `${dailyChange >= 0 ? "+" : ""}${Number(dailyChange).toFixed(2)}%` : "-",
      tone: percentTone(dailyChange),
      sparkTone: percentTone(dailyChange),
    },
    {
      label: t("analysis.chart.techPanel.rangeTrend"),
      value: selectedRangeLabel,
      tone: selectedRangeTone,
      sparkTone: selectedRangeTone,
      icon: <TrendingUp size={15} strokeWidth={2.3} />,
    },
    {
      label: t("analysis.chart.rangeChange"),
      value: rangeChangePct != null ? `${rangeChangePct >= 0 ? "+" : ""}${rangeChangePct.toFixed(2)}%` : "-",
      tone: percentTone(rangeChangePct),
      sparkTone: percentTone(rangeChangePct),
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
        <div className="chart-timeframes" role="group" aria-label="Range">
          {presets.map((preset) => (
            <button
              key={preset.key}
              type="button"
              className={`chart-tf-btn${activeRange === preset.key ? " active" : ""}`}
              onClick={() => onRangeChange(preset)}
            >
              {t(`analysis.chart.range.${String(preset.key).toLowerCase()}`, { defaultValue: preset.key })}
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
              <div className="simple-chart-scale-btns">
                <button type="button" className="simple-chart-scale-btn" onClick={zoomIn} title="Yakınlaştır">+</button>
                <button type="button" className="simple-chart-scale-btn" onClick={zoomOut} title="Uzaklaştır">−</button>
                {scaledDomain ? (
                  <button type="button" className="simple-chart-scale-btn simple-chart-scale-btn--reset" onClick={() => setScaledDomain(null)} title="Sıfırla">↺</button>
                ) : null}
              </div>
            </div>
            <ResponsiveContainer width="100%" height={580}>
              <LineChart data={chartData} margin={{ top: 12, right: 18, bottom: 26, left: 10 }}>
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
                  domain={scaledDomain ?? yDomain}
                  allowDataOverflow
                  tickLine={false}
                  axisLine={false}
                  width={78}
                  tick={{ fontSize: 12 }}
                  tickMargin={12}
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
            </div>

            {checklist.length > 0 ? (
              <div className="simple-tech-checklist">
                {checklist.map((item) => (
                  <div key={item.key} className={`simple-tech-check-item simple-tech-check-item--${item.tone}`}>
                    <span className="simple-tech-check-icon">
                      {item.tone === "warning" ? (
                        <AlertTriangle size={12} strokeWidth={2.4} />
                      ) : item.tone === "positive" ? (
                        <Check size={13} strokeWidth={2.6} />
                      ) : (
                        <Minus size={13} strokeWidth={2.4} />
                      )}
                    </span>
                    <span>{item.label}</span>
                  </div>
                ))}
              </div>
            ) : null}

            <div className="simple-tech-metrics-grid">
              <TechMetricCell
                label="RSI (14)"
                value={latestRsi != null ? Number(latestRsi).toFixed(1) : "-"}
                tone={toneFromRsi(latestRsi, instrumentType)}
                subLabel={summary.rsiZoneLabel}
                tooltip={t("analysis.chart.techPanel.tooltip.rsi")}
              />
              <TechMetricCell
                label={t("analysis.chart.techPanel.rangeTrend")}
                value={selectedRangeLabel}
                tone={selectedRangeTone}
                tooltip={t("analysis.chart.techPanel.tooltip.trend")}
              />
              <TechMetricCell
                label={t("analysis.chart.techPanel.momentum")}
                value={summary.momentumLabel}
                tone={summary.momentumTone}
                tooltip={t("analysis.chart.techPanel.tooltip.momentum")}
              />
              <TechMetricCell
                label={t("analysis.chart.techPanel.closingVolatility")}
                value={summary.volatilityRaw != null ? `${summary.volatilityLabel} (%${summary.volatilityRaw.toFixed(2)})` : summary.volatilityLabel}
                tone={summary.volatilityTone}
                tooltip={t("analysis.chart.techPanel.tooltip.closingVolatility")}
              />
              <TechMetricCell
                label={t("analysis.chart.techPanel.maLayout", "MA Dizilimi")}
                value={summary.maPairLabel}
                tone={summary.maTone}
                tooltip={t("analysis.chart.techPanel.tooltip.maLayout")}
              />
            </div>

            {insufficientIndicators.length > 0 ? (
              <div className="simple-tech-check-item simple-tech-check-item--neutral">
                <span className="simple-tech-check-icon"><AlertTriangle size={12} strokeWidth={2.4} /></span>
                <span>{t("analysis.chart.techPanel.insufficientData")}: {insufficientIndicators.join(", ")}</span>
              </div>
            ) : null}

            {(activeRange === "MAX" || activeRange === "1Y") && analysis?.trendContext ? (
              <div className="simple-tech-check-item simple-tech-check-item--neutral">
                <span className="simple-tech-check-icon"><Minus size={13} strokeWidth={2.4} /></span>
                <span>
                  {t("analysis.chart.techPanel.rangeTrend")}:{" "}
                  <strong>
                    {selectedRangeLabel}
                  </strong>
                </span>
              </div>
            ) : null}

            {supportResistance ? (
              <div className="simple-sr-card">
                <div className={`simple-sr-row ${supportResistance.levelMode === "closeBand" ? "simple-sr-row--band" : "simple-sr-row--resistance"}`}>
                  <span>{supportResistance.levelMode === "closeBand" ? t("analysis.chart.techPanel.rangeHigh") : t("analysis.chart.techPanel.resistance")}</span>
                  <strong>{axisLabel}{formatNumber(supportResistance.resistance, 2)}</strong>
                </div>
                <div className={`simple-sr-row ${supportResistance.levelMode === "closeBand" ? "simple-sr-row--band" : "simple-sr-row--support"}`}>
                  <span>{supportResistance.levelMode === "closeBand" ? t("analysis.chart.techPanel.rangeLow") : t("analysis.chart.techPanel.support")}</span>
                  <strong>{axisLabel}{formatNumber(supportResistance.support, 2)}</strong>
                </div>
              </div>
            ) : null}

            {onAddToNotes ? (
              <button
                type="button"
                className="simple-tech-add-note-btn"
                disabled={noteAdding || loading}
                onClick={() => onAddToNotes(buildAnalysisNoteContent({
                  primaryContext,
                  activeRange,
                  lastPrice,
                  rangeChangePct,
                  summary,
                  latestRsi,
                  supportResistance,
                  axisLabel,
                }))}
              >
                <BookmarkPlus size={15} strokeWidth={2.2} />
                <span>{noteAdding ? "Ekleniyor..." : "Notlara Ekle"}</span>
              </button>
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

function TechMetricCell({ label, value, tone = "neutral", subLabel, tooltip }) {
  return (
    <div className="simple-tech-metric-cell">
      <span className="simple-tech-metric-cell-label">
        {label}
        {tooltip ? <TooltipHint text={tooltip} /> : null}
      </span>
      <span className={`simple-tech-metric-cell-value simple-tech-summary-value--${tone}`}>{value}</span>
      {subLabel ? <span className="simple-tech-metric-cell-sub">{subLabel}</span> : null}
    </div>
  );
}

function TooltipHint({ text }) {
  const [open, setOpen] = useState(false);
  return (
    <span
      className="simple-tech-tooltip-anchor"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
      onClick={(e) => { e.stopPropagation(); setOpen((v) => !v); }}
      tabIndex={0}
      role="button"
      aria-expanded={open}
    >
      <Info size={14} strokeWidth={2} />
      {open ? (
        <span className="simple-tech-tooltip-box" role="tooltip">
          {text}
        </span>
      ) : null}
    </span>
  );
}


function SimpleTooltip({ active, payload, label, chartTheme, axisLabel }) {
  if (!active || !payload?.length) {
    return null;
  }

  const tooltipDate = payload[0]?.payload?.fullDate || label;
  const rows = Object.values(payload.reduce((acc, item) => {
    const key = item.dataKey || item.name;
    const existing = acc[key];
    if (!existing || existing.name === existing.dataKey) {
      acc[key] = item;
    }
    return acc;
  }, {}));

  return (
    <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
      <strong>{tooltipDate}</strong>
      {rows.map((item) => (
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

function buildTechChecklist({ lastPrice, ma20, ma50, latestRsi, instrumentType }) {
  const items = [];

  if (lastPrice != null && ma20 != null) {
    const above = lastPrice >= ma20;
    items.push({
      key: "price-ma20",
      tone: above ? "positive" : "negative",
      label: above ? "Fiyat MA20 üzerinde" : "Fiyat MA20 altında",
    });
  }

  if (ma20 != null && ma50 != null) {
    const bullish = ma20 >= ma50;
    items.push({
      key: "ma20-ma50",
      tone: bullish ? "positive" : "negative",
      label: bullish ? "MA20, MA50 üzerinde" : "MA20, MA50 altında",
    });
  }

  if (latestRsi != null) {
    const thresholds = resolveRsiThresholds(instrumentType);
    const label = latestRsi >= thresholds.overbought
      ? "RSI aşırı alım bölgesinde"
      : latestRsi <= thresholds.oversold
        ? "RSI aşırı satım bölgesinde"
        : "RSI nötr bölgede";
    items.push({ key: "rsi", tone: toneFromRsi(latestRsi, instrumentType), label });
  }

  return items;
}

function buildPriceDomain(chartData) {
  const values = chartData
    .map((point) => Number(point?.close))
    .filter(Number.isFinite);
  if (values.length === 0) {
    return ["auto", "auto"];
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  if (min === max) {
    const pad = Math.abs(min) * 0.02 || 1;
    return [min - pad, max + pad];
  }

  const pad = (max - min) * 0.08;
  return [min - pad, max + pad];
}

function buildSupportResistance(chartData, instrumentType) {
  const rows = (Array.isArray(chartData) ? chartData : []).filter((point) => positiveNumber(point?.close) != null);
  if (rows.length < 2) {
    return null;
  }
  const latestClose = positiveNumber(rows.at(-1)?.close);
  const canUseSwingLevels = supportsSwingLevels(instrumentType);
  const hasOhlc = canUseSwingLevels && rows.some((point) => positiveNumber(point?.high) != null && positiveNumber(point?.low) != null);
  const closes = rows.map((point) => positiveNumber(point.close)).filter((value) => value != null);
  const support = hasOhlc ? nearestSwingLevel(rows, latestClose, "low") : Math.min(...closes);
  const resistance = hasOhlc ? nearestSwingLevel(rows, latestClose, "high") : Math.max(...closes);
  if (positiveNumber(support) == null || positiveNumber(resistance) == null) {
    return null;
  }
  return { support, resistance, levelMode: hasOhlc ? "swing" : "closeBand" };
}

function supportsSwingLevels(instrumentType) {
  return String(instrumentType ?? "").trim().toUpperCase() === "CRYPTO";
}

function nearestSwingLevel(rows, latestClose, key) {
  const values = rows.map((point) => positiveNumber(point?.[key])).filter((value) => value != null);
  const swings = [];
  for (let i = 2; i < rows.length - 2; i++) {
    const value = positiveNumber(rows[i]?.[key]);
    if (value == null) continue;
    const neighbors = [rows[i - 2], rows[i - 1], rows[i + 1], rows[i + 2]].map((point) => positiveNumber(point?.[key]));
    const isSwing = key === "low"
      ? neighbors.every((neighbor) => neighbor != null && value <= neighbor)
      : neighbors.every((neighbor) => neighbor != null && value >= neighbor);
    if (isSwing) swings.push(value);
  }
  const candidates = key === "low"
    ? swings.filter((value) => value <= latestClose)
    : swings.filter((value) => value >= latestClose);
  if (candidates.length) {
    return candidates.reduce((best, value) => Math.abs(value - latestClose) < Math.abs(best - latestClose) ? value : best);
  }
  return key === "low" ? Math.min(...values) : Math.max(...values);
}

function positiveNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null;
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

function percentTone(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "neutral";
  }
  return numeric > 0 ? "positive" : "negative";
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

function resolveLatestIndicator(analysis, chartData, pointKey, indicatorKey) {
  const lastPointValue = chartData
    .map((point) => point?.[pointKey])
    .findLast((value) => value !== null && value !== undefined && value !== "");

  const analysisValue = analysis?.indicatorValues
    ?.find?.((item) => String(item?.indicator || "").trim().toUpperCase() === String(indicatorKey || "").trim().toUpperCase())
    ?.value;

  return firstFinite(lastPointValue, analysisValue);
}

function buildSimpleSummaryModel({ analysis, chartData, latestRsi, activeRange, instrumentType }) {
  const closes = chartData.map((point) => Number(point?.close)).filter(Number.isFinite);
  // MA alignment uses MA20 vs MA50 — the primary financial signal
  const ma20 = resolveLatestIndicator(analysis, chartData, "sma20", "SMA20");
  const ma50 = resolveLatestIndicator(analysis, chartData, "sma50", "SMA50");
  const hasMaPair = ma20 != null && ma50 != null;
  const maSpreadPct = hasMaPair ? ((ma20 - ma50) / ma50) * 100 : null;
  const bullishMaLayout = maSpreadPct != null && maSpreadPct > 0.20;
  const bearishMaLayout = maSpreadPct != null && maSpreadPct < -0.20;
  const momentumPct = resolveSelectedRangePerformance(chartData, instrumentType, activeRange).totalChangePct;
  const momentumThreshold = resolveMomentumThreshold(instrumentType);
  const volatilityPct = closeToCloseVolatility(closes, activeRange);

  return {
    momentumLabel: momentumPct == null ? "-" : momentumPct >= momentumThreshold ? "Pozitif" : momentumPct <= -momentumThreshold ? "Negatif" : "Nötr",
    momentumTone: momentumPct == null ? "neutral" : momentumPct >= momentumThreshold ? "positive" : momentumPct <= -momentumThreshold ? "negative" : "neutral",
    volatilityLabel: volatilityPct == null ? "Yetersiz veri" : volatilityPct >= 2.0 ? "Yüksek" : volatilityPct >= 0.75 ? "Orta" : "Düşük",
    volatilityTone: volatilityPct == null ? "neutral" : volatilityPct >= 2.0 ? "warning" : volatilityPct >= 0.75 ? "neutral" : "positive",
    maPairLabel: !hasMaPair
      ? "Yetersiz veri"
      : bullishMaLayout
        ? "Yukarı dizilim"
        : bearishMaLayout
          ? "Aşağı dizilim"
          : "Yatay",
    maTone: !hasMaPair
      ? "neutral"
      : bullishMaLayout
        ? "positive"
        : bearishMaLayout
          ? "negative"
          : "neutral",
    rsiZoneLabel: resolveRsiZoneLabel(latestRsi, instrumentType),
    volatilityRaw: volatilityPct,
  };
}

function formatSelectedRangeStateLabel(stateKey) {
  switch (stateKey) {
    case "strongBullish":
      return "Güçlü yükseliş";
    case "weakBullish":
      return "Zayıf yükseliş";
    case "bullish":
      return "Yükseliş";
    case "strongBearish":
      return "Güçlü düşüş";
    case "weakBearish":
      return "Zayıf düşüş";
    case "bearish":
      return "Düşüş";
    default:
      return "Yatay-Nötr";
  }
}

function resolveRsiZoneLabel(rsiValue, instrumentType) {
  if (rsiValue == null) return null;
  const thresholds = resolveRsiThresholds(instrumentType);
  if (rsiValue >= thresholds.overbought) return "Aşırı alım";
  if (rsiValue <= thresholds.oversold) return "Aşırı satım";
  return "Nötr";
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

function buildAnalysisNoteContent({ primaryContext, activeRange, lastPrice, rangeChangePct, summary, latestRsi, supportResistance, axisLabel }) {
  const val = (v, digits = 2) =>
    v == null || !Number.isFinite(Number(v)) ? "-" : `${axisLabel}${Number(v).toFixed(digits)}`;
  const pct = (v) =>
    v == null || !Number.isFinite(Number(v)) ? "-" : `${Number(v) >= 0 ? "+" : ""}${Number(v).toFixed(2)}%`;
  const str = (v) => (v != null && String(v).trim() ? String(v).trim() : "-");
  const date = new Date().toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" });
  const symbol = str(primaryContext?.symbolLine);
  const isCloseBand = supportResistance?.levelMode === "closeBand";

  return [
    `${symbol} - ${str(activeRange)} Teknik Analiz`,
    "",
    `Son fiyat: ${val(lastPrice)}`,
    `Aralık değişimi: ${pct(rangeChangePct)}`,
    `Teknik görünüm: ${str(summary?.scoreLabel)}`,
    `RSI: ${latestRsi != null ? Number(latestRsi).toFixed(1) : "-"} (${str(summary?.rsiZoneLabel)})`,
    `Momentum: ${str(summary?.momentumLabel)}`,
    `Volatilite: ${str(summary?.volatilityLabel)}`,
    `MA dizilimi: ${str(summary?.maPairLabel)}`,
    supportResistance ? `${isCloseBand ? "Aralık en düşük" : "Destek"}: ${val(supportResistance.support)}` : null,
    supportResistance ? `${isCloseBand ? "Aralık en yüksek" : "Direnç"}: ${val(supportResistance.resistance)}` : null,
    "",
    `Tarih: ${date}`,
  ].filter((line) => line !== null).join("\n");
}

