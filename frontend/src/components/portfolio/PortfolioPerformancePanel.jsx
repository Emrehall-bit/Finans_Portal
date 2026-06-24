import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueries, useQuery } from "@tanstack/react-query";
import { CartesianGrid, Line, LineChart, ReferenceArea, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { X } from "lucide-react";
import { getMarketHistory } from "../../api/marketApi";
import { getMacroObservations } from "../../api/macroApi";
import { marketKeys } from "../../api/queryKeys";
import { usePortfolioPerformance } from "../../hooks/usePortfolioQueries";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatPercent } from "../../utils/formatters";
import { formatInstrumentLabel } from "../../utils/instrumentUtils";


const MAX_SERIES = 5;
const TUFE_CODE = "CPI_TR";
const SERIES_COLORS = ["#2563eb", "#0f9d58", "#f59e0b", "#7c3aed", "#0891b2", "#db2777"];
const RANGE_PRESET_KEYS = ["1M", "3M", "6M", "1Y", "5Y", "MAX"];
const RANGE_PRESET_MONTHS = { "1M": 1, "3M": 3, "6M": 6, "1Y": 12, "5Y": 60, MAX: null };

export default function PortfolioPerformancePanel({ portfolioId, holdings = [], chartTheme, currency = "TRY", formatAmount }) {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState("PERFORMANCE");
  const [scopeMode, setScopeMode] = useState("ALL");
  const [selectedCodes, setSelectedCodes] = useState([]);
  const [rangeKey, setRangeKey] = useState("3M");
  const rangePresets = useMemo(() => RANGE_PRESET_KEYS.map((key) => ({ key, label: t(`portfolio.perf.range.${key}`), months: RANGE_PRESET_MONTHS[key] })), [t]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [realMode, setRealMode] = useState(false);
  const pickerRef = useRef(null);

  useEffect(() => {
    if (!pickerOpen) return undefined;
    function onOutside(e) {
      if (pickerRef.current && !pickerRef.current.contains(e.target)) setPickerOpen(false);
    }
    document.addEventListener("mousedown", onOutside);
    return () => document.removeEventListener("mousedown", onOutside);
  }, [pickerOpen]);

  useEffect(() => {
    if (activeTab !== "INFLATION") setRealMode(false);
  }, [activeTab]);

  useEffect(() => {
    if (selectedCodes.length > 1) setRealMode(false);
  }, [selectedCodes.length]);

  const { from, to } = useMemo(() => buildRangeParams(rangeKey), [rangeKey]);

  const { data: perfHistory, isLoading: loadingPerf, error: perfError } = usePortfolioPerformance(portfolioId, useMemo(() => ({ from, to }), [from, to]));
  const perfPoints = perfHistory?.points ?? [];

  const instrumentQueries = useQueries({
    queries: (scopeMode === "ASSET" && selectedCodes.length > 0)
      ? selectedCodes.map((code) => ({
          queryKey: marketKeys.history(code, { from, to }),
          queryFn: () => getMarketHistory(code, { from, to }),
          staleTime: 5 * 60_000,
        }))
      : [],
  });

  const { data: macroObs = [], isLoading: loadingMacro } = useQuery({
    queryKey: [...marketKeys.all, "macro-obs", TUFE_CODE],
    queryFn: () => getMacroObservations(TUFE_CODE, "MONTHLY_CHANGE"),
    staleTime: 10 * 60_000,
    enabled: activeTab === "INFLATION",
  });

  const availableHoldings = useMemo(
    () => holdings.filter((h) => h.instrumentCode && h.instrumentCode !== "TRY"),
    [holdings],
  );
  const maxSelectable = activeTab === "INFLATION" ? MAX_SERIES - 1 : MAX_SERIES;
  const canAddMore = selectedCodes.length < maxSelectable;
  const addableHoldings = useMemo(
    () => availableHoldings.filter((h) => !selectedCodes.includes(h.instrumentCode)),
    [availableHoldings, selectedCodes],
  );

  function addAsset(code) {
    if (!canAddMore) return;
    setSelectedCodes((prev) => [...prev, code]);
    setPickerOpen(false);
  }
  function removeAsset(code) {
    setSelectedCodes((prev) => prev.filter((c) => c !== code));
  }
  function handleScopeChange(mode) {
    setScopeMode(mode);
    if (mode === "ALL") { setSelectedCodes([]); setRealMode(false); }
  }
  function handleTabChange(tab) {
    setActiveTab(tab);
    setSelectedCodes([]);
    setScopeMode("ALL");
    setRealMode(false);
  }

  // ── Effective-start / gap computation ───────────────────────────────────────

  // Backend resolves the performance window to max(requestedFrom, firstHoldingDate),
  // so fromDate IS the portfolio's effective start date within the selected range.
  const portfolioEffectiveStart = perfHistory?.fromDate ?? null;

  const selectedHoldingStarts = useMemo(() => {
    const map = new Map();
    if (scopeMode !== "ASSET") return map;
    selectedCodes.forEach((code) => {
      const holding = availableHoldings.find((h) => h.instrumentCode === code);
      const start = resolveHoldingStartDate(holding);
      if (start) map.set(code, start);
    });
    return map;
  }, [scopeMode, selectedCodes, availableHoldings]);

  const assetsCommonStart = useMemo(() => {
    const starts = [...selectedHoldingStarts.values()];
    return starts.length ? starts.reduce((min, d) => (d < min ? d : min)) : null;
  }, [selectedHoldingStarts]);

  const effectiveStart = scopeMode === "ASSET" ? assetsCommonStart : portfolioEffectiveStart;

  // "Tümü" should span exactly the portfolio's own history — no artificial pre-history gap.
  const axisFrom = rangeKey === "MAX" && effectiveStart ? effectiveStart : from;
  const axisTo = to;

  const hasGap = !!effectiveStart && !!axisFrom && effectiveStart > axisFrom && effectiveStart <= axisTo;
  const entirelyBeforeStart = !!effectiveStart && !!axisTo && effectiveStart > axisTo;

  // ── Series computation ──────────────────────────────────────────────────────

  const instrumentHistories = useMemo(() => {
    if (scopeMode !== "ASSET") return new Map();
    const result = new Map();
    selectedCodes.forEach((code, idx) => {
      const q = instrumentQueries[idx];
      if (!q || q.isLoading || q.isError) return;
      const raw = Array.isArray(q.data) ? q.data : [];
      const holdingStart = selectedHoldingStarts.get(code);
      const sliceFrom = holdingStart && holdingStart > from ? holdingStart : null;
      const rows = sliceFrom ? raw.filter((p) => {
        const d = p.priceTimestamp ? String(p.priceTimestamp).slice(0, 10) : (p.date ?? null);
        return d && d >= sliceFrom;
      }) : raw;
      const norm = normalizeHistoryTo100(rows);
      if (norm.length >= 2) result.set(code, norm);
    });
    return result;
  }, [scopeMode, selectedCodes, instrumentQueries, selectedHoldingStarts, from]);

  const portfolioNormalized = useMemo(() => {
    const pts = perfPoints.filter((p) => p.totalValue != null);
    if (pts.length < 2) return [];
    const base = toN(pts[0].totalValue);
    if (!base) return [];
    return pts.map((p) => ({ date: p.date, value: (toN(p.totalValue) / base) * 100 }));
  }, [perfPoints]);

  const tüfeNormalized = useMemo(() => {
    const s = buildTüfeSeries(macroObs, from, to);
    return s.length >= 2 ? s : [];
  }, [macroObs, from, to]);

  const portfolioNominalReturn = useMemo(() => {
    if (portfolioNormalized.length < 2) return null;
    const lastNorm = portfolioNormalized[portfolioNormalized.length - 1].value;
    return Number.isFinite(lastNorm) ? (lastNorm - 100) / 100 : null;
  }, [portfolioNormalized]);

  const periodReturn = useMemo(() => {
    if (perfPoints.length < 2) return null;
    const first = toN(perfPoints[0].totalValue);
    const last = toN(perfPoints[perfPoints.length - 1].totalValue);
    if (!first) return null;
    return ((last - first) / first) * 100;
  }, [perfPoints]);

  const tüfeReturn = useMemo(() => {
    if (tüfeNormalized.length < 2) return null;
    return (tüfeNormalized[tüfeNormalized.length - 1].value - 100) / 100;
  }, [tüfeNormalized]);

  const chartData = useMemo(() => {
    if (entirelyBeforeStart) return [];
    let rows = [];
    if (activeTab === "PERFORMANCE" && scopeMode === "ALL") {
      rows = perfPoints.map((p) => ({ date: p.date, totalValue: toN(p.totalValue), totalCost: toN(p.totalCost) }));
    } else if (activeTab === "PERFORMANCE" && scopeMode === "ASSET") {
      rows = instrumentHistories.size > 0 ? mergeSeriesMap(instrumentHistories) : [];
    } else if (activeTab === "INFLATION") {
      if (realMode) {
        const assetSeries = scopeMode === "ALL" ? portfolioNormalized : (instrumentHistories.get(selectedCodes[0]) ?? []);
        rows = (assetSeries.length >= 2 && tüfeNormalized.length >= 2)
          ? buildRealReturnSeries(assetSeries, tüfeNormalized)
          : [];
      } else {
        const seriesMap = new Map();
        if (scopeMode === "ALL") {
          if (portfolioNormalized.length >= 2) seriesMap.set("PORTFOLIO", portfolioNormalized);
        } else {
          instrumentHistories.forEach((pts, code) => seriesMap.set(code, pts));
        }
        if (tüfeNormalized.length >= 2) seriesMap.set(TUFE_CODE, tüfeNormalized);
        rows = seriesMap.size > 0 ? mergeSeriesMap(seriesMap) : [];
      }
    }
    if (!rows.length) return [];
    // Keep the selected period's full x-axis span: anchor placeholder rows across the
    // "no holdings yet" gap so the axis (and tooltip) cover the whole range, not just the data.
    return finalizeChartRows(rows, axisFrom, hasGap ? effectiveStart : null);
  }, [
    activeTab, scopeMode, perfPoints, instrumentHistories, portfolioNormalized, tüfeNormalized,
    realMode, selectedCodes, axisFrom, hasGap, effectiveStart, entirelyBeforeStart,
  ]);

  const chartLineKeys = useMemo(() => {
    if (realMode) return [{ code: "REAL", label: t("portfolio.benchmark.realReturn"), color: "#2563eb" }];
    if (activeTab === "PERFORMANCE" && scopeMode === "ALL") {
      return [
        { code: "totalValue", label: t("portfolio.perf.lastValue"), color: "#2563eb" },
        { code: "totalCost", label: t("portfolio.summary.totalCost"), color: "#94a3b8", dashed: true },
      ];
    }
    if (activeTab === "PERFORMANCE" && scopeMode === "ASSET") {
      return selectedCodes.map((code, i) => ({
        code, label: formatInstrumentLabel(code), color: SERIES_COLORS[i % SERIES_COLORS.length],
      }));
    }
    if (activeTab === "INFLATION") {
      const codes = scopeMode === "ALL" ? ["PORTFOLIO"] : selectedCodes;
      const lines = codes.map((code, i) => ({
        code,
        label: code === "PORTFOLIO" ? t("portfolio.eyebrow") : formatInstrumentLabel(code),
        color: SERIES_COLORS[i % SERIES_COLORS.length],
      }));
      if (tüfeNormalized.length >= 2) lines.push({ code: TUFE_CODE, label: "TÜFE", color: "#dc2626", dashed: true });
      return lines;
    }
    return [];
  }, [activeTab, scopeMode, selectedCodes, tüfeNormalized, realMode]);

  // ── State flags ─────────────────────────────────────────────────────────────

  const isLoadingAny =
    (scopeMode === "ALL" && loadingPerf) ||
    (activeTab === "INFLATION" && loadingMacro) ||
    (scopeMode === "ASSET" && selectedCodes.length > 0 && instrumentQueries.some((q) => q.isLoading));

  const hasError = scopeMode === "ALL" && !!perfError;
  const showEmptyAsset = scopeMode === "ASSET" && selectedCodes.length === 0;
  const showNoData = !isLoadingAny && !hasError && !showEmptyAsset && chartData.length < 2;

  const noDataCodes = useMemo(() => {
    if (scopeMode !== "ASSET" || isLoadingAny) return [];
    return selectedCodes.filter((code) => !instrumentHistories.has(code));
  }, [scopeMode, selectedCodes, instrumentHistories, isLoadingAny]);

  // ── Metrics ─────────────────────────────────────────────────────────────────

  const lastPoint = perfPoints[perfPoints.length - 1] ?? null;
  const firstPoint = perfPoints[0] ?? null;
  const valueDelta = lastPoint && firstPoint ? toN(lastPoint.totalValue) - toN(firstPoint.totalValue) : null;

  const assetReturns = useMemo(() => {
    if (scopeMode !== "ASSET") return [];
    return selectedCodes.map((code, idx) => {
      const q = instrumentQueries[idx];
      if (!q || q.isLoading) return { code, label: formatInstrumentLabel(code), returnPct: null, loading: true };
      if (q.isError || !Array.isArray(q.data) || q.data.length < 2) {
        return { code, label: formatInstrumentLabel(code), returnPct: null, loading: false };
      }
      const history = q.data;
      const first = Number(history[0]?.closePrice ?? history[0]?.close ?? history[0]?.price ?? 0);
      const last = Number(history[history.length - 1]?.closePrice ?? history[history.length - 1]?.close ?? history[history.length - 1]?.price ?? 0);
      return { code, label: formatInstrumentLabel(code), returnPct: first ? ((last - first) / first) * 100 : null, loading: false };
    });
  }, [scopeMode, selectedCodes, instrumentQueries]);

  const bestPerformer = useMemo(() => {
    const valid = assetReturns.filter((a) => a.returnPct != null);
    return valid.length >= 2 ? valid.reduce((b, c) => c.returnPct > b.returnPct ? c : b) : null;
  }, [assetReturns]);

  const inflationAllMetrics = useMemo(() => {
    if (activeTab !== "INFLATION" || scopeMode !== "ALL") return null;
    if (portfolioNominalReturn == null || tüfeReturn == null) return null;
    const real = ((1 + portfolioNominalReturn) / (1 + tüfeReturn)) - 1;
    return {
      portfolioPct: portfolioNominalReturn * 100,
      tüfePct: tüfeReturn * 100,
      realPct: real * 100,
      isAbove: real >= 0,
    };
  }, [activeTab, scopeMode, portfolioNominalReturn, tüfeReturn]);

  const inflationAssetMetrics = useMemo(() => {
    if (activeTab !== "INFLATION" || scopeMode !== "ASSET") return [];
    return assetReturns.map((asset) => {
      if (asset.returnPct == null || tüfeReturn == null) return { ...asset, tüfePct: null, realReturnPct: null, isAbove: null };
      const nominal = asset.returnPct / 100;
      const real = ((1 + nominal) / (1 + tüfeReturn)) - 1;
      return { ...asset, tüfePct: tüfeReturn * 100, realReturnPct: real * 100, isAbove: real >= 0 };
    });
  }, [activeTab, scopeMode, assetReturns, tüfeReturn]);

  const canShowRealToggle =
    activeTab === "INFLATION" &&
    !showEmptyAsset &&
    tüfeNormalized.length >= 2 &&
    (scopeMode === "ALL" || (scopeMode === "ASSET" && selectedCodes.length === 1));

  const showChart = !isLoadingAny && !hasError && !showEmptyAsset && !showNoData && chartData.length >= 2;

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-perf-panel">
      {/* Tab bar */}
      <div className="portfolio-perf-tabs">
        <button type="button" className={`portfolio-perf-tab${activeTab === "PERFORMANCE" ? " active" : ""}`} onClick={() => handleTabChange("PERFORMANCE")}>
          {t("portfolio.perf.portfolioPerformance")}
        </button>
        <button type="button" className={`portfolio-perf-tab${activeTab === "INFLATION" ? " active" : ""}`} onClick={() => handleTabChange("INFLATION")}>
          {t("portfolio.perf.vsInflation")}
        </button>
      </div>

      {/* Controls */}
      <div className="portfolio-perf-controls">
        <div className="portfolio-perf-scope-group" role="group">
          <button type="button" className={`portfolio-perf-scope-btn${scopeMode === "ALL" ? " active" : ""}`} onClick={() => handleScopeChange("ALL")}>
            {t("portfolio.perf.allPortfolio")}
          </button>
          <button type="button" className={`portfolio-perf-scope-btn${scopeMode === "ASSET" ? " active" : ""}`} onClick={() => handleScopeChange("ASSET")}>
            {t("portfolio.perf.selectAsset")}
          </button>
        </div>
        <div className="portfolio-perf-range-group" role="group">
          {rangePresets.map((preset) => (
            <button key={preset.key} type="button" className={`portfolio-perf-range-btn${rangeKey === preset.key ? " active" : ""}`} onClick={() => setRangeKey(preset.key)}>
              {preset.label}
            </button>
          ))}
        </div>
      </div>

      {/* Asset chips (ASSET scope) */}
      {scopeMode === "ASSET" ? (
        <div className="portfolio-perf-chips-row">
          {activeTab === "INFLATION" ? (
            <span className="portfolio-perf-chip portfolio-perf-chip--fixed">TÜFE</span>
          ) : null}
          {selectedCodes.map((code) => (
            <span key={code} className="portfolio-perf-chip">
              {formatInstrumentLabel(code)}
              <button type="button" className="portfolio-perf-chip-remove" onClick={() => removeAsset(code)} aria-label={`${formatInstrumentLabel(code)} ${t("common.remove").toLowerCase()}`}>
                <X size={11} />
              </button>
            </span>
          ))}
          {canAddMore && addableHoldings.length > 0 ? (
            <div className="portfolio-perf-picker-wrap" ref={pickerRef}>
              <button type="button" className="portfolio-perf-add-btn" onClick={() => setPickerOpen((p) => !p)}>
                {t("portfolio.perf.addAsset")}
              </button>
              {pickerOpen ? (
                <ul className="portfolio-perf-picker-list">
                  {addableHoldings.map((h) => (
                    <li key={h.instrumentCode}>
                      <button type="button" onMouseDown={() => addAsset(h.instrumentCode)}>
                        {formatInstrumentLabel(h.instrumentCode)}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : null}
          {!canAddMore ? (
            <span className="portfolio-perf-series-limit">{t("portfolio.perf.maxAssets", { count: maxSelectable })}</span>
          ) : null}
        </div>
      ) : null}

      {/* States */}
      {isLoadingAny ? <LoadingSpinner label={t("common.loading")} /> : null}
      {!isLoadingAny && hasError ? <ErrorMessage message={t("portfolio.perf.loadError")} /> : null}
      {!isLoadingAny && !hasError && showEmptyAsset ? (
        <div className="portfolio-perf-empty">
          <p>{t("portfolio.perf.selectAssetHint")}</p>
        </div>
      ) : null}
      {!isLoadingAny && !hasError && !showEmptyAsset && showNoData ? (
        <div className="portfolio-perf-empty">
          {scopeMode === "ASSET" && noDataCodes.length > 0
            ? noDataCodes.map((code) => (
                <p key={code}>{t("portfolio.perf.noHistoryForCode", { code: formatInstrumentLabel(code) })}</p>
              ))
            : <p>{entirelyBeforeStart ? t("portfolio.perf.noHoldingsInPeriod") : t("portfolio.perf.noDataForPeriod")}</p>}
        </div>
      ) : null}

      {/* Content */}
      {showChart ? (
        <>
          {/* Metrics — PERFORMANCE ALL */}
          {activeTab === "PERFORMANCE" && scopeMode === "ALL" ? (
            <div className="portfolio-perf-metrics">
              <div className="portfolio-perf-metric">
                <small>{t("portfolio.perf.lastValue")}</small>
                <strong>{lastPoint ? formatAmount(toN(lastPoint.totalValue)) : "-"}</strong>
              </div>
              <div className="portfolio-perf-metric">
                <small>{t("portfolio.perf.periodChange")}</small>
                <strong className={pnlClass(valueDelta)}>{valueDelta != null ? formatAmount(valueDelta) : "-"}</strong>
              </div>
              <div className="portfolio-perf-metric">
                <small>{t("portfolio.perf.periodReturn")}</small>
                <strong className={pnlClass(periodReturn)}>{periodReturn != null ? formatPercent(periodReturn) : "-"}</strong>
              </div>
            </div>
          ) : null}

          {/* Metrics — PERFORMANCE ASSET */}
          {activeTab === "PERFORMANCE" && scopeMode === "ASSET" ? (
            <div className="portfolio-perf-asset-returns">
              {assetReturns.map((a) => (
                <div key={a.code} className="portfolio-perf-asset-return-row">
                  <span>
                    {a.label}
                    {bestPerformer?.code === a.code ? <span className="portfolio-perf-best-tag">{t("portfolio.perf.best")}</span> : null}
                  </span>
                  <strong className={pnlClass(a.returnPct)}>
                    {a.loading ? "…" : a.returnPct != null ? formatPercent(a.returnPct) : "-"}
                  </strong>
                </div>
              ))}
            </div>
          ) : null}

          {/* Metrics — INFLATION ALL */}
          {activeTab === "INFLATION" && scopeMode === "ALL" && inflationAllMetrics ? (
            <div className="portfolio-perf-metrics">
              <div className="portfolio-perf-metric">
                <small>{t("portfolio.benchmark.portfolioReturn")}</small>
                <strong className={pnlClass(inflationAllMetrics.portfolioPct)}>{formatPercent(inflationAllMetrics.portfolioPct)}</strong>
              </div>
              <div className="portfolio-perf-metric">
                <small>{t("portfolio.benchmark.inflation")}</small>
                <strong>{formatPercent(inflationAllMetrics.tüfePct)}</strong>
              </div>
              <div className="portfolio-perf-metric">
                <small>{t("portfolio.benchmark.realReturn")}</small>
                <strong className={pnlClass(inflationAllMetrics.realPct)}>{formatPercent(inflationAllMetrics.realPct)}</strong>
              </div>
              <div className="portfolio-perf-metric">
                <small>{t("portfolio.benchmark.status")}</small>
                <strong className={inflationAllMetrics.isAbove ? "portfolio-perf-status--above" : "portfolio-perf-status--below"}>
                  {inflationAllMetrics.isAbove ? t("portfolio.benchmark.aboveInflation") : t("portfolio.benchmark.belowInflation")}
                </strong>
              </div>
            </div>
          ) : null}

          {/* Metrics — INFLATION ASSET */}
          {activeTab === "INFLATION" && scopeMode === "ASSET" && inflationAssetMetrics.length > 0 ? (
            <div className="portfolio-perf-inflation-asset-list">
              {inflationAssetMetrics.map((m) => (
                <div key={m.code} className={`portfolio-perf-inflation-asset-row${m.isAbove === true ? " is-above" : m.isAbove === false ? " is-below" : ""}`}>
                  <div className="portfolio-perf-inflation-asset-label">
                    <span>{m.label}</span>
                    <span className={m.isAbove === true ? "portfolio-perf-status--above" : m.isAbove === false ? "portfolio-perf-status--below" : ""}>
                      {m.isAbove === true ? t("portfolio.benchmark.aboveInflation") : m.isAbove === false ? t("portfolio.benchmark.belowInflation") : t("common.noData")}
                    </span>
                  </div>
                  <div className="portfolio-perf-inflation-asset-vals">
                    <span><small>{t("portfolio.perf.nominal")}</small><strong className={pnlClass(m.returnPct)}>{m.returnPct != null ? formatPercent(m.returnPct) : "-"}</strong></span>
                    <span><small>{t("portfolio.perf.real")}</small><strong className={pnlClass(m.realReturnPct)}>{m.realReturnPct != null ? formatPercent(m.realReturnPct) : "-"}</strong></span>
                  </div>
                </div>
              ))}
            </div>
          ) : null}

          {/* Real mode toggle */}
          {canShowRealToggle ? (
            <div className="portfolio-perf-real-row">
              <button type="button" className={`portfolio-perf-real-btn${realMode ? " active" : ""}`} onClick={() => setRealMode((p) => !p)}>
                {realMode ? t("portfolio.perf.nominalView") : t("portfolio.perf.realView")}
              </button>
              {scopeMode === "ASSET" && selectedCodes.length > 1 ? (
                <span className="portfolio-perf-real-note">{t("portfolio.perf.realModeNote")}</span>
              ) : null}
            </div>
          ) : null}

          {/* Gap notice */}
          {hasGap ? (
            <p className="portfolio-perf-footnote portfolio-perf-gap-note">
              {scopeMode === "ASSET" ? t("portfolio.perf.assetStart") : t("portfolio.perf.portfolioStart")}
              {" "}<strong>{formatPerfDate(effectiveStart)}</strong>
              {" — "}{activeTab === "INFLATION" ? t("portfolio.perf.realReturnFromDate") : t("portfolio.perf.returnFromDate")}
            </p>
          ) : null}

          {/* Chart */}
          <div className="portfolio-perf-chart-shell">
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={chartData} margin={{ top: 10, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} strokeOpacity={0.4} />
                <XAxis
                  dataKey="date"
                  stroke={chartTheme.axis}
                  tickLine={false}
                  axisLine={false}
                  tick={{ fontSize: 11 }}
                  tickMargin={8}
                  tickFormatter={(v) => formatPerfAxisTick(v, rangeKey)}
                  minTickGap={42}
                />
                <YAxis
                  stroke={chartTheme.axis}
                  tickLine={false}
                  axisLine={false}
                  width={activeTab === "PERFORMANCE" && scopeMode === "ALL" ? 68 : 44}
                  tickFormatter={(v) => activeTab === "PERFORMANCE" && scopeMode === "ALL" ? formatCompact(v) : Number(v).toFixed(0)}
                />
                <Tooltip content={<PerfTooltip chartTheme={chartTheme} lineKeys={chartLineKeys} rawMode={activeTab === "PERFORMANCE" && scopeMode === "ALL"} formatAmount={formatAmount} currency={currency} hasGap={hasGap} gapEnd={effectiveStart} />} />
                {hasGap ? (
                  <ReferenceArea
                    x1={axisFrom}
                    x2={effectiveStart}
                    fill="rgba(239, 68, 68, 0.06)"
                    stroke="rgba(239, 68, 68, 0.18)"
                    strokeWidth={1}
                    strokeDasharray="4 3"
                  />
                ) : null}
                {chartLineKeys.map((line) => (
                  <Line
                    key={line.code}
                    type="monotone"
                    dataKey={line.code}
                    name={line.label}
                    stroke={line.color}
                    strokeWidth={line.dashed ? 1.8 : 2.4}
                    dot={false}
                    strokeDasharray={line.dashed ? "5 4" : undefined}
                    connectNulls
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>

          {activeTab === "INFLATION" && !realMode ? (
            <p className="portfolio-perf-footnote">{t("portfolio.perf.tufeNote")}</p>
          ) : null}
          {activeTab === "PERFORMANCE" ? (
            <p className="portfolio-perf-footnote">{t("portfolio.perf.performanceNote")}</p>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

// ── Tooltip ─────────────────────────────────────────────────────────────────

function PerfTooltip({ active, payload, label, chartTheme, lineKeys, rawMode, formatAmount, hasGap, gapEnd }) {
  const { t } = useTranslation();
  if (!active) return null;

  const isGapDate = hasGap && gapEnd && label && label < gapEnd;
  if (isGapDate) {
    return (
      <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
        <strong>{formatPerfDate(label)}</strong>
        <p className="chart-tooltip-gap-note">{t("portfolio.perf.gapNote")}</p>
      </div>
    );
  }

  if (!payload?.length) return null;
  const labelMap = Object.fromEntries(lineKeys.map((l) => [l.code, { label: l.label, color: l.color }]));

  return (
    <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
      <strong>{formatPerfDate(label)}</strong>
      {payload.filter((item, i, arr) => arr.findIndex((x) => x.dataKey === item.dataKey) === i).map((item) => {
        const meta = labelMap[item.dataKey];
        return (
          <div key={item.dataKey} className="chart-tooltip-row">
            <span style={{ color: meta?.color ?? item.stroke }}>{meta?.label ?? item.name}</span>
            <strong>
              {rawMode ? formatAmount(item.value) : `${Number(item.value ?? 0).toFixed(2)}`}
            </strong>
          </div>
        );
      })}
    </div>
  );
}

// ── Pure helpers ─────────────────────────────────────────────────────────────

function buildRangeParams(rangeKey) {
  const months = RANGE_PRESET_MONTHS[rangeKey] ?? RANGE_PRESET_MONTHS["3M"];
  if (months == null) return { from: "1970-01-01", to: toIso(new Date()) };
  const to = new Date();
  const from = new Date(to);
  from.setMonth(from.getMonth() - months);
  return { from: toIso(from), to: toIso(to) };
}

function toIso(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function normalizeHistoryTo100(history) {
  if (!Array.isArray(history) || history.length < 2) return [];
  const base = Number(history[0]?.closePrice ?? history[0]?.close ?? history[0]?.price ?? 0);
  if (!base) return [];
  return history
    .map((p) => {
      const date = p.priceTimestamp ? String(p.priceTimestamp).slice(0, 10) : (p.date ?? null);
      const val = Number(p.closePrice ?? p.close ?? p.price);
      return { date, value: (val / base) * 100 };
    })
    .filter((p) => p.date && Number.isFinite(p.value));
}

function buildTüfeSeries(observations, from, to) {
  if (!Array.isArray(observations) || !observations.length) return [];
  const fromMs = new Date(from).getTime();
  const toMs = new Date(to).getTime();
  const filtered = observations
    .filter((o) => { const d = new Date(o.observationDate).getTime(); return d >= fromMs && d <= toMs; })
    .sort((a, b) => new Date(a.observationDate).getTime() - new Date(b.observationDate).getTime());
  if (!filtered.length) return [];
  const points = [{ date: from, value: 100 }];
  let cumulative = 100;
  for (const obs of filtered) {
    cumulative *= 1 + Number(obs.value) / 100;
    points.push({ date: obs.observationDate, value: Number(cumulative.toFixed(4)) });
  }
  return points;
}

function mergeSeriesMap(seriesMap) {
  const grouped = new Map();
  seriesMap.forEach((points, code) => {
    points.forEach(({ date, value }) => {
      if (!grouped.has(date)) grouped.set(date, { date });
      grouped.get(date)[code] = value;
    });
  });
  return [...grouped.values()].sort((a, b) => a.date.localeCompare(b.date));
}

function buildRealReturnSeries(assetSeries, tüfeSeries) {
  const tüfeMap = new Map(tüfeSeries.map((p) => [p.date, p.value]));
  const tüfeDates = [...tüfeMap.keys()].sort();

  function getTüfeAt(date) {
    let latest = 100;
    for (const d of tüfeDates) {
      if (d <= date) latest = tüfeMap.get(d) ?? latest;
      else break;
    }
    return latest;
  }

  return assetSeries
    .map(({ date, value }) => ({ date, REAL: Number(((value / getTüfeAt(date)) * 100).toFixed(4)) }))
    .filter((p) => Number.isFinite(p.REAL));
}

function formatPerfAxisTick(dateStr, rangeKey) {
  const d = new Date(`${dateStr}T00:00:00`);
  if (Number.isNaN(d.getTime())) return dateStr ?? "";
  if (rangeKey === "1M") return new Intl.DateTimeFormat("tr-TR", { day: "2-digit", month: "short" }).format(d);
  return new Intl.DateTimeFormat("tr-TR", { month: "short", year: "2-digit" }).format(d);
}

function formatPerfDate(dateStr) {
  const d = new Date(`${dateStr}T00:00:00`);
  if (Number.isNaN(d.getTime())) return dateStr ?? "-";
  return new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "long", year: "numeric" }).format(d);
}

function formatCompact(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  return new Intl.NumberFormat("tr-TR", { notation: "compact", maximumFractionDigits: 1 }).format(n);
}

function toN(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function pnlClass(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n === 0) return "portfolio-tonal-text is-flat";
  return n > 0 ? "portfolio-tonal-text is-up" : "portfolio-tonal-text is-down";
}

function resolveHoldingStartDate(holding) {
  if (!holding) return null;
  if (holding.purchaseDate) return String(holding.purchaseDate).slice(0, 10);
  if (holding.createdAt) return String(holding.createdAt).slice(0, 10);
  return null;
}

function finalizeChartRows(rows, axisFrom, gapEnd) {
  if (!gapEnd || !axisFrom || gapEnd <= axisFrom) return rows;
  const realDates = new Set(rows.map((r) => r.date));
  const gapRows = [];
  let cur = new Date(`${axisFrom}T00:00:00`);
  const gapEndMs = new Date(`${gapEnd}T00:00:00`).getTime();
  while (cur.getTime() < gapEndMs) {
    const d = toIso(cur);
    if (!realDates.has(d)) gapRows.push({ date: d });
    cur.setMonth(cur.getMonth() + 1);
  }
  if (!gapRows.some((r) => r.date === axisFrom) && !realDates.has(axisFrom)) {
    gapRows.push({ date: axisFrom });
  }
  return [...gapRows, ...rows].sort((a, b) => a.date.localeCompare(b.date));
}
