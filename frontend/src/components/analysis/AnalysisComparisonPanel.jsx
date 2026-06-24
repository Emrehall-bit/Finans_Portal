import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Plus, Search, X } from "lucide-react";
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
import InstrumentSelectorPopover, {
  INSTRUMENT_CATEGORIES_NO_INDEX,
  filterAndSearchInstruments,
  formatInstrumentLabel,
  resolveInstrumentTitle,
  resolveTypeLabel,
} from "./InstrumentSelectorPopover";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { useTheme } from "../../theme/ThemeContext";
import { buildComparisonData, formatAxisNumber } from "./analysisUtils";

// Constants

const COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2"];
const MAX_REFS = 4;
const STOCK_ONLY_TYPES = new Set(["INDEX", "SECTOR"]);
const BENCHMARK_TYPES = ["INSTRUMENT", "MACRO", "INDEX", "SECTOR"];
const DISPLAY_MODES = [
  { key: "normalized", labelKey: "analysis.comparison.normalized" },
  { key: "price", labelKey: "analysis.comparison.price" },
  { key: "benchmark", labelKey: "analysis.comparison.benchmark" },
];
const FALLBACK_INDICES = [
  { symbol: "XU100", code: "XU100", displayName: "BIST100", instrumentType: "INDEX" },
  { symbol: "XU050", code: "XU050", displayName: "BIST50", instrumentType: "INDEX" },
  { symbol: "XU030", code: "XU030", displayName: "BIST30", instrumentType: "INDEX" },
];
const POPULAR_COMPARISONS = [
  { key: "thyao-bist100", base: "THYAO", ref: "XU100", refType: "INDEX", labelKey: "thyao", refLabelKey: "bist100" },
  { key: "asels-sector", base: "ASELS", ref: "SECTOR", refType: "SECTOR", labelKey: "asels", refLabelKey: "defenseSector" },
  { key: "usdtry-tufe", base: "USD", ref: "CPI_TR", refType: "MACRO", labelKey: "usdTry", refLabelKey: "tufe" },
  { key: "gold-tufe", base: "GRAM_ALTIN", ref: "CPI_TR", refType: "MACRO", labelKey: "gramGold", refLabelKey: "tufe" },
  { key: "btc-nasdaq", base: "BTC", ref: "NASDAQ", refType: "INDEX", labelKey: "btc", refLabelKey: "nasdaq", visualOnly: true },
];

// Main component

export default function AnalysisComparisonPanel({
  references = [],
  onAddReference,
  onRemoveReference,
  refResults = [],
  mode = "normalized",
  onModeChange,
  primarySymbol = "",
  primaryQuote = null,
  quotes = [],
  onPrimaryChange,
  activeRange = "3M",
  rangePresets = [],
  onRangeChange,
}) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage;
  const { chartTheme } = useTheme();
  const [addingRefType, setAddingRefType] = useState("INSTRUMENT");

  const isPrimaryStock = String(primaryQuote?.instrumentType || "").toUpperCase() === "STOCK";
  const atLimit = references.length >= MAX_REFS;
  const alreadyHasMacro = references.some((r) => r.type === "MACRO");
  const alreadyHasSector = references.some((r) => r.type === "SECTOR");
  const isBenchmarkModeAvailable = references.length === 1;
  const effectiveMode = (mode === "benchmark" && !isBenchmarkModeAvailable) || (mode === "price" && alreadyHasMacro)
    ? "normalized"
    : mode;
  const singleReference = references.length === 1 ? references[0] : null;
  const isSingleMacroRef = singleReference?.type === "MACRO";

  // Reference item lists
  const indexRefItems = useMemo(() => {
    const fromQuotes = quotes.filter((q) => String(q.instrumentType || "").toUpperCase() === "INDEX");
    return fromQuotes.length > 0 ? fromQuotes : FALLBACK_INDICES;
  }, [quotes]);

  const instrumentRefItems = useMemo(
    () => quotes.filter((q) => {
      const type = String(q.instrumentType || "").toUpperCase();
      return type !== "INDEX" && q.symbol !== primarySymbol;
    }),
    [quotes, primarySymbol],
  );

  const addItems = addingRefType === "INDEX" ? indexRefItems : instrumentRefItems;

  // Primary trigger label
  const primaryTriggerLabel = primaryQuote?.code
    || normalizeTcmb(primarySymbol)
    || t("analysis.symbolPicker.selectInstrument");

  // Merged chart series
  const mergedSeries = useMemo(() => {
    const result = [];
    const seen = new Set();
    for (const { data } of refResults) {
      if (!data?.series) continue;
      for (const s of data.series) {
        if (!s?.symbol || seen.has(s.symbol)) continue;
        seen.add(s.symbol);
        result.push(s);
      }
    }
    return result;
  }, [refResults]);

  const isAnyLoading = refResults.some((r) => r.isLoading);
  const chartErrors = refResults.filter((r) => r.error && !r.data).map((r) => r.error);

  // "Fark/Reel" spread chart: base - reference normalized per date (single ref only)
  const spreadData = useMemo(() => {
    if (effectiveMode !== "benchmark" || mergedSeries.length < 2) return [];
    const [base, ref] = mergedSeries;
    const baseMap = new Map();
    base.points?.forEach((p) => {
      const k = String(p.date);
      const v = Number(p.normalizedValue);
      if (Number.isFinite(v)) baseMap.set(k, v);
    });
    const result = [];
    ref.points?.forEach((p) => {
      const k = String(p.date);
      const refVal = Number(p.normalizedValue);
      const baseVal = baseMap.get(k);
      if (Number.isFinite(refVal) && baseVal != null) {
        result.push({ date: k, spread: Number((baseVal - refVal).toFixed(4)) });
      }
    });
    return result;
  }, [effectiveMode, mergedSeries]);

  const macroAlignedNormalizedData = useMemo(() => {
    if (!isSingleMacroRef || mergedSeries.length < 2) return [];
    const [base, ref] = mergedSeries;
    return buildMacroAlignedData(base, ref, "normalized");
  }, [isSingleMacroRef, mergedSeries]);

  const macroRealData = useMemo(() => {
    if (effectiveMode !== "benchmark" || !isSingleMacroRef || mergedSeries.length < 2) return [];
    const [base, ref] = mergedSeries;
    return buildMacroAlignedData(base, ref, "real");
  }, [effectiveMode, isSingleMacroRef, mergedSeries]);

  const normalChartData = useMemo(
    () => {
      if (isSingleMacroRef && effectiveMode === "normalized") {
        return macroAlignedNormalizedData;
      }
      return buildComparisonData(mergedSeries, effectiveMode === "price" ? "price" : "normalized");
    },
    [mergedSeries, effectiveMode, isSingleMacroRef, macroAlignedNormalizedData],
  );

  const isBenchmarkChart = effectiveMode === "benchmark" && isBenchmarkModeAvailable;
  const activeChartData = isBenchmarkChart ? (isSingleMacroRef ? macroRealData : spreadData) : normalChartData;
  const hasChart = !isAnyLoading && activeChartData.length >= 2;

  // Mixed scales warning for price mode

  // Type chip action
  function handleTypeChipClick(type) {
    if (STOCK_ONLY_TYPES.has(type) && !isPrimaryStock) return;
    if (type === "MACRO") {
      if (!atLimit && !alreadyHasMacro) onAddReference?.("CPI_TR", "MACRO");
      return;
    }
    if (type === "SECTOR") {
      if (!atLimit && !alreadyHasSector) onAddReference?.("SECTOR", "SECTOR");
      return;
    }
    setAddingRefType(type);
  }

  function handlePickerSelect(symbol) {
    if (!atLimit) onAddReference?.(symbol, addingRefType);
  }

  const showPopupAdd = addingRefType === "INSTRUMENT" || addingRefType === "INDEX";
  const directAddDisabled = atLimit
    || (addingRefType === "MACRO" && alreadyHasMacro)
    || (addingRefType === "SECTOR" && (!isPrimaryStock || alreadyHasSector));
  const popularComparisons = useMemo(
    () => POPULAR_COMPARISONS.map((item) => ({
      ...item,
      baseSymbol: resolvePopularSymbol(item.base, quotes),
      refSymbol: item.refType === "MACRO" || item.refType === "SECTOR"
        ? item.ref
        : resolvePopularSymbol(item.ref, [...quotes, ...indexRefItems]),
    })),
    [quotes, indexRefItems],
  );

  function handlePopularComparison(item) {
    if (item.visualOnly || !item.baseSymbol || !item.refSymbol) return;
    onPrimaryChange?.(item.baseSymbol);
    onAddReference?.(item.refSymbol, item.refType);
  }

  return (
    <div className="comparison-workspace">
      <div className="comparison-workspace-controls">

        {/* Row 1: primary picker + add reference */}
        <div className="comparison-vs-row">
          <InstrumentSelectorPopover
            items={quotes}
            selectedSymbol={primarySymbol}
            onSelect={(sym) => onPrimaryChange?.(sym)}
            triggerLabel={primaryTriggerLabel}
            placeholder={t("analysis.symbolPicker.searchPlaceholder")}
          />

          {showPopupAdd ? (
            <ComparisonAddPicker
              items={addItems}
              onSelect={handlePickerSelect}
              placeholder={
                addingRefType === "INDEX"
                  ? t("analysis.benchmark.indexSearch")
                  : t("analysis.benchmark.searchPlaceholder")
              }
              categories={addingRefType === "INDEX" ? undefined : INSTRUMENT_CATEGORIES_NO_INDEX}
              showCategories={addingRefType !== "INDEX"}
              disabled={atLimit}
              disabledTitle={t("analysis.benchmark.limitReached")}
              label={t("analysis.benchmark.addReference")}
              t={t}
              locale={locale}
            />
          ) : (
            <button
              type="button"
              className={`comparison-add-ref-btn${directAddDisabled ? " comparison-chip-disabled" : ""}`}
              disabled={directAddDisabled}
              title={resolveTypeTitle(addingRefType, t, atLimit)}
              onClick={() => {
                if (addingRefType === "MACRO" && !alreadyHasMacro && !atLimit) onAddReference?.("CPI_TR", "MACRO");
                else if (addingRefType === "SECTOR" && !alreadyHasSector && !atLimit && isPrimaryStock) onAddReference?.("SECTOR", "SECTOR");
              }}
            >
              <Plus size={14} strokeWidth={2.5} />
              {t("analysis.benchmark.addReference")}
            </button>
          )}
        </div>

        {/* Selected reference chips */}
        <div className="comparison-chips-row">
          <span className="comparison-ref-chip comparison-ref-chip--primary">
            {primaryTriggerLabel}
            <span className="comparison-ref-chip-tag">{t("analysis.benchmark.primaryLabel")}</span>
          </span>
          {references.map((ref) => (
            <span key={ref.code} className="comparison-ref-chip">
              <span>{resolveRefLabel(ref, indexRefItems, quotes, t)}</span>
              <button
                type="button"
                className="comparison-ref-chip-remove"
                aria-label={`${resolveRefLabel(ref, indexRefItems, quotes, t)} kaldir`}
                onClick={() => onRemoveReference?.(ref.code)}
              >
                <X size={12} strokeWidth={2.5} />
              </button>
            </span>
          ))}
        </div>

        {/* Row 2: type chips + display mode chips */}
        <div className="comparison-bottom-controls">
          <div className="comparison-control-section">
            <span className="comparison-control-label">{t("analysis.benchmark.referenceType")}</span>
            <div className="comparison-control-chips">
              {BENCHMARK_TYPES.map((type) => {
                const isStockGated = STOCK_ONLY_TYPES.has(type) && !isPrimaryStock;
                const isAdded = type === "MACRO" ? alreadyHasMacro : type === "SECTOR" ? alreadyHasSector : false;
                const isActive = (type === "INSTRUMENT" || type === "INDEX") && addingRefType === type;
                const chipDisabled = isStockGated || (atLimit && !isActive);
                const gateKey = type === "INDEX" ? "analysis.benchmark.indexOnly" : "analysis.benchmark.sectorOnly";
                return (
                  <button
                    key={type}
                    type="button"
                    disabled={chipDisabled}
                    className={[
                      "analysis-benchmark-chip",
                      isActive ? "active" : "",
                      chipDisabled ? "comparison-chip-disabled" : "",
                      isAdded ? "comparison-chip-added" : "",
                    ].filter(Boolean).join(" ")}
                    onClick={() => handleTypeChipClick(type)}
                    title={isStockGated ? t(gateKey) : resolveTypeTitle(type, t, atLimit)}
                  >
                    {t(`analysis.benchmark.types.${type}`)}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="comparison-control-section">
            <span className="comparison-control-label">{t("analysis.comparison.modeLabel")}</span>
            <div className="comparison-control-chips">
              {DISPLAY_MODES.filter((dm) => !(dm.key === "price" && alreadyHasMacro)).map((dm) => {
                const disabled = dm.key === "benchmark" && !isBenchmarkModeAvailable;
                return (
                  <button
                    key={dm.key}
                    type="button"
                    disabled={disabled}
                    className={[
                      "analysis-benchmark-chip",
                      effectiveMode === dm.key ? "active" : "",
                      disabled ? "comparison-chip-disabled" : "",
                    ].filter(Boolean).join(" ")}
                    onClick={() => !disabled && onModeChange?.(dm.key)}
                    title={disabled ? t("analysis.benchmark.benchmarkSingleOnly") : undefined}
                  >
                    {t(dm.labelKey)}
                  </button>
                );
              })}
            </div>
            {mode === "benchmark" && !isBenchmarkModeAvailable ? (
              <p className="comparison-type-note">{t("analysis.benchmark.benchmarkSingleOnly")}</p>
            ) : null}
          </div>

          <div className="comparison-mode-help">
            <strong>{t(`analysis.benchmark.modeHelp.${effectiveMode}.title`)}</strong>
            <span>
              {isSingleMacroRef && effectiveMode === "benchmark"
                ? t("analysis.benchmark.modeHelp.benchmark.macroDescription")
                : t(`analysis.benchmark.modeHelp.${effectiveMode}.description`)}
            </span>
          </div>
        </div>

        {/* Range selector */}
        {rangePresets.length > 0 ? (
          <div className="comparison-range-row">
            <div className="chart-timeframes" role="group" aria-label={t("analysis.chart.rangeChange")}>
              {rangePresets.filter((preset) => !(alreadyHasMacro && preset.key === "1M")).map((preset) => (
                <button
                  key={preset.key}
                  type="button"
                  className={`chart-tf-btn${activeRange === preset.key ? " active" : ""}`}
                  onClick={() => onRangeChange?.(preset)}
                >
                  {t(`analysis.chart.range.${String(preset.key).toLowerCase()}`, { defaultValue: preset.key })}
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </div>

      {/* Body */}
      {references.length === 0 ? (
        <PopularComparisons
          items={popularComparisons}
          onSelect={handlePopularComparison}
          t={t}
        />
      ) : (
        <div className="comparison-workspace-body">
          {/* Chart */}
          <div className="comparison-workspace-chart">
            {isAnyLoading ? <LoadingSpinner label={t("analysis.benchmark.loading")} /> : null}
            {!isAnyLoading && chartErrors.length > 0 ? <ErrorMessage message={chartErrors[0]} /> : null}
            {!isAnyLoading && !hasChart && chartErrors.length === 0 ? (
              <p className="analysis-benchmark-empty">{t("analysis.benchmark.selectInstrument")}</p>
            ) : null}
            {hasChart ? (
              <div className="analysis-chart-shell terminal-chart-shell market-detail-chart">
                <ResponsiveContainer width="100%" height={300}>
                  {isBenchmarkChart ? (
                    <LineChart data={activeChartData} margin={{ top: 18, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} />
                      <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} tickFormatter={formatComparisonTick} />
                      <YAxis stroke={chartTheme.axis} tickLine={false} axisLine={false} width={72} tickFormatter={(v) => formatAxisNumber(v)} />
                      <ReferenceLine y={isSingleMacroRef ? 100 : 0} stroke={chartTheme.axis} strokeDasharray="4 2" />
                      <Tooltip content={<ComparisonTooltip chartTheme={chartTheme} />} />
                      <Line
                        type="monotone"
                        dataKey={isSingleMacroRef ? "realNormalized" : "spread"}
                        name={isSingleMacroRef ? t("analysis.benchmark.realPerformanceLine") : buildSpreadLabel(mergedSeries, quotes, t)}
                        stroke={COLORS[0]}
                        strokeWidth={2.2}
                        dot={false}
                      />
                    </LineChart>
                  ) : (
                    <LineChart data={normalChartData} margin={{ top: 18, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.grid} />
                      <XAxis dataKey="date" stroke={chartTheme.axis} tickLine={false} axisLine={false} tickFormatter={formatComparisonTick} />
                      <YAxis stroke={chartTheme.axis} tickLine={false} axisLine={false} width={72} tickFormatter={(v) => formatAxisNumber(v)} />
                      <Tooltip content={<ComparisonTooltip chartTheme={chartTheme} />} />
                      <Legend wrapperStyle={{ color: chartTheme.legendText }} />
                      {mergedSeries.map((item, i) => (
                        <Line
                          key={item.symbol}
                          type="monotone"
                          dataKey={item.symbol}
                          name={resolveSeriesLabel(item.symbol, quotes, t)}
                          stroke={COLORS[i % COLORS.length]}
                          strokeWidth={i === 0 ? 2.8 : 2.2}
                          dot={false}
                        />
                      ))}
                    </LineChart>
                  )}
                </ResponsiveContainer>
              </div>
            ) : null}
          </div>

          {/* Summary */}
          <div className="comparison-workspace-summary">
            {references.length === 1 ? (
              <SingleRefSummary
                refResult={refResults[0]}
                reference={references[0]}
                primarySymbol={primarySymbol}
                indexRefItems={indexRefItems}
                quotes={quotes}
                t={t}
                locale={locale}
              />
            ) : (
              <MultiRefSummary
                references={references}
                refResults={refResults}
                primarySymbol={primarySymbol}
                primaryQuote={primaryQuote}
                indexRefItems={indexRefItems}
                quotes={quotes}
                t={t}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ComparisonAddPicker
// Standalone add-comparison button with inline search popup.

function ComparisonAddPicker({ items, onSelect, placeholder, categories, showCategories, disabled, disabledTitle, label, t, locale }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("ALL");
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    function onOutside(e) {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false);
    }
    function onKey(e) { if (e.key === "Escape") setOpen(false); }
    document.addEventListener("mousedown", onOutside);
    window.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onOutside);
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const results = useMemo(
    () => filterAndSearchInstruments(items, search, category),
    [items, search, category],
  );

  function handleSelect(symbol) {
    onSelect(symbol);
    setOpen(false);
    setSearch("");
    setCategory("ALL");
  }

  return (
    <div className="comparison-add-picker" ref={rootRef}>
      <button
        type="button"
        className={`comparison-add-ref-btn${open ? " active" : ""}${disabled ? " comparison-chip-disabled" : ""}`}
        disabled={disabled}
        title={disabled ? disabledTitle : undefined}
        onClick={() => setOpen((p) => !p)}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <Plus size={14} strokeWidth={2.5} />
        {label}
      </button>

      {open && !disabled ? (
        <div className="analysis-instrument-popover comparison-add-popover" role="dialog" aria-label={placeholder}>
          <label className="analysis-compare-search">
            <Search size={14} strokeWidth={2} />
            <input
              autoFocus
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={placeholder}
            />
          </label>

          {showCategories && categories ? (
            <div className="analysis-instrument-categories" role="group">
              {categories.map((cat) => (
                <button
                  key={cat.key}
                  type="button"
                  className={`analysis-instrument-cat${category === cat.key ? " active" : ""}`}
                  onClick={() => setCategory(cat.key)}
                >
                  {t(cat.labelKey)}
                </button>
              ))}
            </div>
          ) : null}

          <div className="analysis-compare-results">
            {results.length === 0 ? (
              <div className="analysis-compare-empty">{t("analysis.symbolPicker.noResults")}</div>
            ) : results.map((item) => (
              <button
                key={`${item.symbol}-${item.source ?? ""}`}
                type="button"
                className="analysis-compare-option analysis-instrument-option"
                onClick={() => handleSelect(item.symbol)}
              >
                <span className="analysis-instrument-option-top">
                  <strong>{formatInstrumentLabel(item)}</strong>
                  <span className="analysis-instrument-option-type">{resolveTypeLabel(item, t)}</span>
                </span>
                <span className="analysis-instrument-option-name">{resolveInstrumentTitle(item, locale)}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

// Summary sub-components

function PopularComparisons({ items, onSelect, t }) {
  return (
    <div className="comparison-popular-empty">
      <div className="comparison-popular-copy">
        <span>{t("analysis.benchmark.popularEyebrow")}</span>
        <h4>{t("analysis.benchmark.popularTitle")}</h4>
        <p>{t("analysis.benchmark.popularDescription")}</p>
      </div>
      <div className="comparison-popular-chips" aria-label={t("analysis.benchmark.popularTitle")}>
        {items.map((item) => {
          const clickable = !item.visualOnly && item.baseSymbol && item.refSymbol;
          return (
            <button
              key={item.key}
              type="button"
              className={`comparison-popular-chip${clickable ? " is-clickable" : ""}`}
              disabled={!clickable}
              onClick={() => onSelect(item)}
              title={clickable ? t("analysis.benchmark.popularClickHint") : t("analysis.benchmark.popularVisualOnly")}
            >
              <strong>{t(`analysis.benchmark.popularLabels.${item.labelKey}`)}</strong>
              <span>↔</span>
              <strong>{t(`analysis.benchmark.popularLabels.${item.refLabelKey}`)}</strong>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function SingleRefSummary({ refResult, reference, primarySymbol, indexRefItems, quotes, t, locale }) {
  const { data, isLoading, error, isGated } = refResult ?? {};
  const isMacro = reference?.type === "MACRO";
  const isSector = reference?.type === "SECTOR";
  const effectiveReturn = isMacro ? data?.realReturn : data?.relativeReturn;
  const status = data?.status ?? null;
  const title = `${resolveCodeLabel(primarySymbol, quotes)} vs ${resolveRefLabel(reference, indexRefItems, quotes, t)}`;
  const benchmarkEffectiveEnd = data?.benchmarkEffectiveEnd;
  const hasStaleMacroBenchmark = isMacro && benchmarkEffectiveEnd && data?.to && benchmarkEffectiveEnd < data.to;

  if (isGated || isSector) {
    return <p className="comparison-sector-gate-text">{t("analysis.benchmark.sectorEmpty")}</p>;
  }

  return (
    <>
      <div className="comparison-summary-heading">
        <p>{t("analysis.comparisonWorkspace.summaryTitle")}</p>
        <span>{title}</span>
      </div>
      {isLoading && !data ? <LoadingSpinner label={t("analysis.benchmark.loading")} /> : null}
      {error && !data ? <ErrorMessage message={error} /> : null}
      {!data && !isLoading && !error ? (
        <p className="comparison-summary-empty">{t("analysis.benchmark.selectInstrument")}</p>
      ) : null}
      {data ? (
        <>
          <div className="comparison-summary-metrics">
            <ComparisonMetric
              label={t("analysis.benchmark.baseReturn")}
              value={formatReturnPct(data.baseReturn)}
            />
            <ComparisonMetric
              label={isMacro ? t("analysis.benchmark.inflationReturn") : t("analysis.benchmark.benchmarkReturn")}
              value={formatReturnPct(data.benchmarkReturn)}
            />
            <ComparisonMetric
              label={isMacro ? t("analysis.benchmark.realReturn") : t("analysis.benchmark.relativeReturn")}
              value={isMacro ? formatReturnPct(effectiveReturn) : formatPointReturn(effectiveReturn)}
              highlight
              positive={effectiveReturn != null && Number(effectiveReturn) >= 0}
            />
            {status ? (
              <div className="comparison-metric">
                <span className="comparison-metric-label">{t("analysis.benchmark.status")}</span>
                <span className={`analysis-benchmark-status${status.startsWith("ABOVE") ? " above" : " below"}`}>
                  {t(`analysis.benchmark.statuses.${status}`)}
                </span>
              </div>
            ) : null}
          </div>
          <p className="comparison-summary-description">
            {isMacro ? t("analysis.benchmark.macroDescription") : t("analysis.benchmark.relativeDescription")}
          </p>
          {hasStaleMacroBenchmark ? (
            <p className="comparison-summary-description comparison-summary-description--notice">
              {t("analysis.benchmark.macroDataUntil", { date: formatDateLabel(benchmarkEffectiveEnd, locale) })}
            </p>
          ) : null}
        </>
      ) : null}
    </>
  );
}

function MultiRefSummary({ references, refResults, primarySymbol, primaryQuote, indexRefItems, quotes, t }) {
  const baseReturn = refResults.find((r) => r.data?.baseReturn != null)?.data?.baseReturn ?? null;
  const primaryLabel = primaryQuote?.code || normalizeTcmb(primarySymbol) || primarySymbol;

  // Find the highest-return series (primary or references)
  const allReturns = [
    { code: primarySymbol, ret: baseReturn },
    ...references.map(({ code }, i) => {
      const rd = refResults[i];
      return { code, ret: rd?.data?.benchmarkReturn ?? null };
    }),
  ].filter((r) => r.ret != null);

  const bestCode = allReturns.length > 0
    ? allReturns.reduce((best, r) => Number(r.ret) > Number(best.ret) ? r : best, allReturns[0]).code
    : null;

  return (
    <>
      <div className="comparison-summary-heading">
        <p>{t("analysis.comparisonWorkspace.summaryTitle")}</p>
        <span>{t("analysis.benchmark.multiSummarySubtitle", { count: references.length })}</span>
      </div>

      {/* Primary row */}
      <div className={`comparison-multi-row${bestCode === primarySymbol ? " comparison-multi-row--best" : ""}`}>
        <div className="comparison-multi-row-left">
          <span className="comparison-multi-label">{primaryLabel}</span>
          <span className="comparison-ref-chip-tag">{t("analysis.benchmark.primaryLabel")}</span>
        </div>
        <span className={`comparison-multi-return ${baseReturn != null && Number(baseReturn) >= 0 ? "positive" : "negative"}`}>
          {formatReturnPct(baseReturn)}
        </span>
      </div>

      {/* Reference rows */}
      {references.map(({ code, type }, i) => {
        const rd = refResults[i];
        const refLabel = resolveRefLabel({ code, type }, indexRefItems, quotes, t);
        const refReturn = rd.data?.benchmarkReturn ?? null;
        const diff = baseReturn != null && refReturn != null ? baseReturn - refReturn : null;
        const isBest = code === bestCode;

        return (
          <div key={code} className={`comparison-multi-row${isBest ? " comparison-multi-row--best" : ""}`}>
            <div className="comparison-multi-row-left">
              <span className="comparison-multi-label">{refLabel}</span>
            </div>
            <div className="comparison-multi-row-right">
              {rd.isLoading ? <span className="comparison-multi-loading">...</span> : null}
              {!rd.isLoading && rd.error ? <span className="comparison-multi-error">!</span> : null}
              {!rd.isLoading && !rd.error && rd.isGated ? (
                <span className="comparison-multi-gated">{t("analysis.benchmark.sectorEmpty")}</span>
              ) : null}
              {!rd.isLoading && !rd.error && !rd.isGated && rd.data ? (
                <>
                  <span className={`comparison-multi-return ${refReturn != null && Number(refReturn) >= 0 ? "positive" : "negative"}`}>
                    {formatReturnPct(refReturn)}
                  </span>
                  {diff != null ? (
                    <span className={`comparison-multi-diff ${diff >= 0 ? "positive" : "negative"}`}>
                      {diff >= 0 ? "+" : ""}{(diff * 100).toFixed(2)} puan
                    </span>
                  ) : null}
                </>
              ) : null}
              {!rd.isLoading && !rd.error && !rd.isGated && !rd.data ? (
                <span className="comparison-multi-empty">-</span>
              ) : null}
            </div>
          </div>
        );
      })}
    </>
  );
}

function ComparisonMetric({ label, value, highlight = false, positive }) {
  const valueClass = [
    "comparison-metric-value",
    highlight && positive === true ? "comparison-metric-value--positive" : "",
    highlight && positive === false ? "comparison-metric-value--negative" : "",
  ].filter(Boolean).join(" ");
  return (
    <div className="comparison-metric">
      <span className="comparison-metric-label">{label}</span>
      <span className={valueClass}>{value}</span>
    </div>
  );
}

function formatComparisonTick(isoDate) {
  if (!isoDate) return "";
  const d = new Date(`${isoDate}T00:00:00`);
  if (Number.isNaN(d.getTime())) return isoDate;
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function formatComparisonLabel(isoDate) {
  if (!isoDate) return "-";
  const d = new Date(`${isoDate}T00:00:00`);
  if (Number.isNaN(d.getTime())) return isoDate;
  return d.toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" });
}

function ComparisonTooltip({ active, payload, label, chartTheme }) {
  if (!active || !payload?.length) return null;
  return (
    <div
      className="chart-tooltip terminal-tooltip"
      style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}
    >
      <strong>{formatComparisonLabel(label)}</strong>
      {payload.map((item) => (
        <div key={item.dataKey} className="chart-tooltip-row">
          <span>{item.name}</span>
          <strong>{formatAxisNumber(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}

// Helpers

function resolveRefLabel(ref, indexRefItems, quotes, t) {
  const { code, type } = ref ?? {};
  if (type === "MACRO") return resolveMacroLabel(code, t);
  if (type === "SECTOR") return t("analysis.benchmark.sectorLabel");
  if (type === "INDEX") {
    const item = indexRefItems.find((q) => q.symbol === code || q.code === code);
    return item?.displayName || item?.code || normalizeTcmb(code) || code;
  }
  const quote = quotes.find((q) => q.symbol === code || q.code === code);
  return quote?.code || normalizeTcmb(code) || code;
}

function resolveCodeLabel(code, quotes) {
  if (!code) return "";
  const quote = quotes.find((q) => q.symbol === code || q.code === code);
  return quote?.code || normalizeTcmb(code) || code;
}

function resolveSeriesLabel(symbol, quotes, t) {
  if (symbol === "CPI_TR") return resolveMacroLabel(symbol, t);
  const quote = quotes.find((q) => q.symbol === symbol || q.code === symbol);
  return quote?.code || normalizeTcmb(symbol) || symbol;
}

function buildSpreadLabel(mergedSeries, quotes, t) {
  if (mergedSeries.length < 2) return "Fark";
  const baseLabel = resolveSeriesLabel(mergedSeries[0].symbol, quotes, t);
  const refLabel = resolveSeriesLabel(mergedSeries[1].symbol, quotes, t);
  return `${baseLabel} - ${refLabel}`;
}

function resolveMacroLabel(code, t) {
  if (code === "CPI_TR") return t("analysis.benchmark.presets.tufe");
  return code;
}

function resolveTypeTitle(type, t, atLimit = false) {
  if (atLimit) return t("analysis.benchmark.limitReached");
  return t(`analysis.benchmark.typeHints.${type}`);
}

function resolvePopularSymbol(code, quotes) {
  const normalizedCode = normalizeLookupKey(code);
  const match = quotes.find((quote) => {
    const symbol = normalizeLookupKey(quote?.symbol);
    const shortCode = normalizeLookupKey(quote?.code);
    return symbol === normalizedCode || shortCode === normalizedCode || symbol.includes(normalizedCode);
  });
  return match?.symbol ?? null;
}

function normalizeLookupKey(value) {
  return String(value || "").replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function buildMacroAlignedData(baseSeries, macroSeries, mode) {
  const basePoints = [...(baseSeries?.points || [])]
    .filter((p) => p?.date && Number.isFinite(Number(p.normalizedValue)))
    .sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const macroPoints = [...(macroSeries?.points || [])]
    .filter((p) => p?.date && Number.isFinite(Number(p.normalizedValue)))
    .sort((a, b) => String(a.date).localeCompare(String(b.date)));

  if (basePoints.length === 0 || macroPoints.length === 0) return [];

  let macroIndex = 0;
  let currentMacro = Number(macroPoints[0].normalizedValue);
  const result = [];

  for (const point of basePoints) {
    while (
      macroIndex + 1 < macroPoints.length
      && String(macroPoints[macroIndex + 1].date) <= String(point.date)
    ) {
      macroIndex += 1;
      currentMacro = Number(macroPoints[macroIndex].normalizedValue);
    }

    const baseValue = Number(point.normalizedValue);
    if (!Number.isFinite(baseValue) || !Number.isFinite(currentMacro) || currentMacro === 0) {
      continue;
    }

    const date = String(point.date);
    if (mode === "real") {
      result.push({
        date,
        realNormalized: Number(((baseValue / currentMacro) * 100).toFixed(4)),
      });
    } else {
      result.push({
        date,
        [baseSeries.symbol]: Number(baseValue.toFixed(4)),
        [macroSeries.symbol]: Number(currentMacro.toFixed(4)),
      });
    }
  }

  return result;
}

function formatDateLabel(value, locale) {
  if (!value) return "-";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(locale || undefined, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

function normalizeTcmb(symbol) {
  const s = String(symbol || "").trim();
  const match = s.toUpperCase().match(/^TCMB:([A-Z0-9]+):(BUY|SELL)$/);
  return match?.[1] || s || "-";
}

function formatReturnPct(value) {
  if (value == null) return "-";
  const pct = Number(value) * 100;
  return `${pct >= 0 ? "+" : ""}${pct.toFixed(2)}%`;
}

function formatPointReturn(value) {
  if (value == null) return "-";
  const pct = Number(value) * 100;
  return `${pct >= 0 ? "+" : ""}${pct.toFixed(2)} puan`;
}

