import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import { ArrowRight, ChevronDown } from "lucide-react";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { extractErrorMessage } from "../api/responseUtils";
import { useComparisonAnalysis, useMarketHistory, useMarketQuotes, useTechnicalAnalysis } from "../hooks/useMarketQueries";
import AnalysisComparisonPanel from "../components/analysis/AnalysisComparisonPanel";
import AnalysisSymbolPicker from "../components/analysis/AnalysisSymbolPicker";
import { ANALYSIS_RANGE_PRESETS, buildChartData, buildPresetRange, DEFAULT_INDICATORS } from "../components/analysis/analysisUtils";
import AdvancedChart from "../components/analysis/AdvancedChart";
import FundamentalAnalysis from "../components/analysis/FundamentalAnalysis";
import SimpleAnalysisChart from "../components/analysis/SimpleAnalysisChart";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { formatInstrumentCode, getFxCodeLabel } from "../utils/instrumentUtils";

export default function AnalysisPage() {
  const { t, i18n } = useTranslation();
  const { convertAmount, currency } = useCurrency();
  const [searchParams] = useSearchParams();
  const routeInstrumentType = String(searchParams.get("type") || "").trim().toUpperCase();
  const [primarySymbol, setPrimarySymbol] = useState(() => searchParams.get("symbol") || "");
  const [selectedSymbols, setSelectedSymbols] = useState(() => {
    const sym = searchParams.get("symbol");
    return sym ? [sym] : [];
  });
  const [activeRange, setActiveRange] = useState("3M");
  const [dateRange, setDateRange] = useState(() => buildPresetRange(90));
  const [selectedIndicators, setSelectedIndicators] = useState(() => new Set(DEFAULT_INDICATORS));
  const [comparisonMode, setComparisonMode] = useState("normalized");
  const [chartMode, setChartMode] = useState(() => (searchParams.get("tool") ? "advanced" : "simple"));
  const [fundamentalsOpen, setFundamentalsOpen] = useState(false);
  const initialHighlightTool = searchParams.get("tool") || null;
  const presetPrice = searchParams.get("preset") ? Number(searchParams.get("preset")) : null;

  const { data: rawQuotes = [], isLoading: quotesLoading, error: quotesQueryError } = useMarketQuotes();
  const quotes = useMemo(() => (Array.isArray(rawQuotes) ? rawQuotes : []), [rawQuotes]);
  const primaryQuote = useMemo(
    () => quotes.find((q) => q.symbol === primarySymbol || q.code === primarySymbol) ?? null,
    [quotes, primarySymbol],
  );
  const primaryApiSymbol = useMemo(
    () => resolveApiSymbol(primarySymbol, primaryQuote, routeInstrumentType),
    [primarySymbol, primaryQuote, routeInstrumentType],
  );
  const primaryContext = useMemo(
    () => buildInstrumentContext(primarySymbol, primaryQuote, i18n.resolvedLanguage),
    [primarySymbol, primaryQuote, i18n.resolvedLanguage],
  );
  const quotesError = quotesQueryError ? extractErrorMessage(quotesQueryError, t("analysis.quotesError")) : "";

  useEffect(() => {
    if (quotes.length > 0 && !primarySymbol) {
      setPrimarySymbol(quotes[0]?.symbol || "");
      setSelectedSymbols((current) => (current.length > 0 ? current : quotes[0]?.symbol ? [quotes[0].symbol] : []));
    }
  }, [quotes, primarySymbol]);

  const analysisParams = useMemo(
    () => ({
      from: dateRange.from,
      to: dateRange.to,
      indicators: Array.from(selectedIndicators).join(","),
    }),
    [dateRange, selectedIndicators],
  );

  const { data: analysis = null, isLoading: analysisLoading, error: analysisQueryError } = useTechnicalAnalysis(
    primaryApiSymbol,
    analysisParams,
    { enabled: !!(primaryApiSymbol && dateRange.from && dateRange.to) },
  );
  const analysisError = analysisQueryError ? resolveAnalysisErrorMessage(analysisQueryError, t) : "";
  const historyParams = useMemo(
    () => ({
      from: dateRange.from,
      to: dateRange.to,
      source: primaryQuote?.source,
      type: primaryQuote?.instrumentType,
    }),
    [dateRange, primaryQuote],
  );
  const { data: history = [], isLoading: historyLoading } = useMarketHistory(
    primaryApiSymbol,
    historyParams,
    { enabled: !!(primaryApiSymbol && dateRange.from && dateRange.to) },
  );

  const comparisonParams = useMemo(
    () =>
      chartMode === "advanced" && selectedSymbols.length >= 2 && dateRange.from && dateRange.to
        ? {
            symbols: selectedSymbols
              .map((symbol) => resolveApiSymbol(
                symbol,
                quotes.find((q) => q.symbol === symbol || q.code === symbol),
                symbol === primarySymbol ? routeInstrumentType : "",
              ))
              .join(","),
            from: dateRange.from,
            to: dateRange.to,
          }
        : null,
    [chartMode, selectedSymbols, dateRange, quotes, primarySymbol, routeInstrumentType],
  );

  const { data: comparison = null, isLoading: comparisonLoading, error: comparisonQueryError } = useComparisonAnalysis(
    comparisonParams,
    { enabled: !!comparisonParams },
  );
  const comparisonError = comparisonQueryError ? resolveComparisonErrorMessage(comparisonQueryError, t) : "";

  const chartData = useMemo(
    () => buildChartData(Array.isArray(analysis?.points) ? analysis.points : [], history),
    [analysis, history],
  );
  const hasChartData = chartData.length > 0;
  const simpleChartLoading = analysisLoading || historyLoading;
  const simpleChartError = !hasChartData ? analysisError : "";
  const displayChartData = useMemo(() => {
    if (currency === "TRY") return chartData;
    return chartData.map((point) => ({
      ...point,
      close: convertAmount(point.close),
      sma7: point.sma7 != null ? convertAmount(point.sma7) : null,
      sma20: point.sma20 != null ? convertAmount(point.sma20) : null,
      sma50: point.sma50 != null ? convertAmount(point.sma50) : null,
      rsi14: point.rsi14,
    }));
  }, [chartData, currency, convertAmount]);

  function handlePrimaryChange(symbol) {
    setPrimarySymbol(symbol);
    setSelectedSymbols((current) => {
      const next = current.filter((item) => item !== symbol);
      return symbol ? [symbol, ...next].slice(0, 5) : next;
    });
  }

  function handleToggleComparisonSymbol(symbol) {
    setSelectedSymbols((current) => {
      if (current.includes(symbol)) {
        const next = current.filter((item) => item !== symbol);
        if (primarySymbol === symbol) {
          setPrimarySymbol(next[0] || "");
        }
        return next;
      }

      const next = [...current, symbol].slice(0, 5);
      if (!primarySymbol) {
        setPrimarySymbol(symbol);
      }
      return next;
    });
  }

  function handleRangeChange(preset) {
    setActiveRange(preset.key);
    setDateRange(buildPresetRange(preset.days));
  }

  return (
    <div className="dashboard-stack analysis-lab-shell">
      {quotesLoading ? <LoadingSpinner label={t("analysis.quotesLoading")} /> : null}
      {quotesError ? <ErrorMessage message={quotesError} /> : null}

      {!quotesLoading && !quotesError && quotes.length === 0 ? (
        <section className="panel-surface">
          <EmptyState title={t("analysis.emptyTitle")} description={t("analysis.emptyDescription")} />
        </section>
      ) : null}

      {!quotesLoading && !quotesError && quotes.length > 0 ? (
        <section className="analysis-lab-grid">
          <div className="analysis-lab-main">
            <section className="panel-surface analysis-terminal-shell">
              {primarySymbol ? (
                <div className="analysis-chart-stack-shell">
                  <AnalysisSymbolPicker
                    quotes={quotes}
                    primarySymbol={primarySymbol}
                    selectedSymbols={selectedSymbols}
                    primaryContext={primaryContext}
                    primaryQuote={primaryQuote}
                    currencyToggle={<CurrencyToggle className="analysis-currency-toggle" />}
                    chartMode={chartMode}
                    showComparison
                    showInlineComparisonChips={chartMode === "advanced"}
                    onChartModeChange={setChartMode}
                    onPrimaryChange={handlePrimaryChange}
                    onToggleComparisonSymbol={handleToggleComparisonSymbol}
                  />

                  <div className="analysis-terminal-body">
                    {chartMode === "simple" ? (
                        <SimpleAnalysisChart
                          activeRange={activeRange}
                          onRangeChange={handleRangeChange}
                          loading={simpleChartLoading}
                          error={simpleChartError}
                          chartData={displayChartData}
                          presets={ANALYSIS_RANGE_PRESETS}
                          quote={primaryQuote}
                          analysis={analysis}
                          primaryContext={primaryContext}
                          onOpenAdvanced={() => setChartMode("advanced")}
                          quotes={quotes}
                          primarySymbol={primarySymbol}
                          selectedSymbols={selectedSymbols}
                          onToggleComparisonSymbol={handleToggleComparisonSymbol}
                        />
                    ) : (
                      <AdvancedChart
                        instrumentCode={primaryApiSymbol}
                        initialHighlightTool={initialHighlightTool}
                        presetPrice={presetPrice}
                        quote={primaryQuote}
                        technicalAnalysis={analysis}
                      />
                    )}
                  </div>
                </div>
              ) : (
                <section className="analysis-lab-panel analysis-terminal-empty">
                  <EmptyState title={t("analysis.primaryEmptyTitle")} description={t("analysis.primaryEmptyDescription")} />
                </section>
              )}
            </section>

            {primarySymbol && ["STOCK", "FUND"].includes(primaryQuote?.instrumentType) ? (
              <section className="panel-surface analysis-fundamentals-shell">
                <button
                  type="button"
                  className={`analysis-fundamentals-toggle${fundamentalsOpen ? " is-open" : ""}`}
                  onClick={() => setFundamentalsOpen((current) => !current)}
                  aria-expanded={fundamentalsOpen}
                >
                  <div>
                    <strong>{t("fundamental.title")}</strong>
                    <span>{fundamentalsOpen ? "Gizle" : "Göster"}</span>
                  </div>
                  <ChevronDown size={18} strokeWidth={2.2} className="analysis-fundamentals-chevron" />
                </button>

                {fundamentalsOpen ? (
                  <div className="analysis-fundamentals-collapse is-open">
                    <FundamentalAnalysis instrumentCode={primarySymbol} />
                  </div>
                ) : null}
              </section>
            ) : null}

            {chartMode === "advanced" ? (
              <AnalysisComparisonPanel
                loading={comparisonLoading}
                error={comparisonError}
                comparison={comparison}
                mode={comparisonMode}
                onModeChange={setComparisonMode}
              />
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}

function buildInstrumentContext(primarySymbol, primaryQuote, locale) {
  const normalizedType = String(primaryQuote?.instrumentType || "").trim().toUpperCase() || "MARKET";
  const normalizedSource = primaryQuote?.source || "-";
  const displayCode = primaryQuote?.code || formatInstrumentCode(primarySymbol) || "-";
  const symbolLine = normalizedType === "FX" ? `${displayCode}/TRY` : displayCode;

  return {
    symbolLine,
    title: resolveContextTitle(primaryQuote, displayCode, normalizedType, locale),
    metaLine: [normalizedType, normalizedSource].filter(Boolean).join(" \u2022 "),
  };
}

function resolveContextTitle(primaryQuote, displayCode, normalizedType, locale) {
  if (normalizedType === "FX") {
    return getFxCodeLabel(displayCode, locale);
  }

  const displayName = String(primaryQuote?.displayName || "").trim();
  if (displayName && displayName !== displayCode) {
    return displayName;
  }

  return displayCode;
}

function SimpleComparisonStrip({ quotes, primarySymbol, selectedSymbols, onToggleComparisonSymbol }) {
  const { t } = useTranslation();
  const comparisonSymbols = selectedSymbols.filter((symbol) => symbol !== primarySymbol);
  const suggestions = useMemo(
    () => deriveComparisonSuggestions(quotes, primarySymbol),
    [quotes, primarySymbol],
  );

  if (suggestions.length === 0) return null;

  return (
    <section className="simple-compare-strip">
      <div className="simple-compare-strip-head">
        <div>
          <strong>{t("analysis.comparison.eyebrow")}</strong>
          <p>{t("analysis.comparison.simpleStripHint")}</p>
        </div>
        {comparisonSymbols.length > 0 ? (
          <button type="button" className="simple-compare-strip-link">
            <span>{t("analysis.comparison.activeCount", { count: comparisonSymbols.length })}</span>
            <ArrowRight size={15} strokeWidth={2.2} />
          </button>
        ) : null}
      </div>

      <div className="simple-compare-chip-row">
        {suggestions.map((symbol) => {
          const active = selectedSymbols.includes(symbol);
          return (
            <button
              key={symbol}
              type="button"
              className={`simple-compare-chip${active ? " active" : ""}`}
              onClick={() => onToggleComparisonSymbol(symbol)}
            >
              <span className={`simple-compare-chip-icon simple-compare-chip-icon--${symbol.toLowerCase()}`}>
                {symbol.slice(0, 2)}
              </span>
              <span>{resolveChipLabel(symbol, quotes)}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function deriveComparisonSuggestions(quotes, primarySymbol, limit = 4) {
  const seenTypes = new Set();
  const result = [];

  for (const quote of quotes) {
    const sym = quote.symbol || quote.code;
    if (!sym || sym === primarySymbol) continue;
    const type = String(quote.instrumentType || "").toUpperCase();
    if (!seenTypes.has(type)) {
      result.push(sym);
      seenTypes.add(type);
    }
    if (result.length >= limit) break;
  }

  if (result.length < limit) {
    for (const quote of quotes) {
      const sym = quote.symbol || quote.code;
      if (!sym || sym === primarySymbol || result.includes(sym)) continue;
      result.push(sym);
      if (result.length >= limit) break;
    }
  }

  return result;
}

function resolveChipLabel(symbol, quotes) {
  return quotes.find((item) => item.symbol === symbol)?.code || formatInstrumentCode(symbol) || symbol;
}

function resolveAnalysisErrorMessage(error, t) {
  const status = Number(error?.response?.status);
  if ([400, 404, 422, 500].includes(status)) {
    return t("analysis.rangeUnavailable");
  }
  return extractErrorMessage(error, t("analysis.analysisError"));
}

function resolveComparisonErrorMessage(error, t) {
  const status = Number(error?.response?.status);
  if ([400, 404, 422, 500].includes(status)) {
    return t("analysis.rangeUnavailable");
  }
  return extractErrorMessage(error, t("analysis.comparisonError"));
}

function resolveApiSymbol(symbol, quote, fallbackInstrumentType = "") {
  const rawSymbol = String(symbol || "").trim();
  if (!rawSymbol) {
    return "";
  }

  const fullSymbol = String(quote?.symbol || "").trim();
  if (fullSymbol) {
    return fullSymbol;
  }

  const normalizedType = String(quote?.instrumentType || fallbackInstrumentType).trim().toUpperCase();
  const upperSymbol = rawSymbol.toUpperCase();
  if (normalizedType === "FX" && !upperSymbol.startsWith("TCMB:") && /^[A-Z]{3}$/.test(upperSymbol)) {
    return `TCMB:${upperSymbol}:SELL`;
  }

  return rawSymbol;
}
