import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { extractErrorMessage } from "../api/responseUtils";
import { useComparisonAnalysis, useMarketQuotes, useTechnicalAnalysis } from "../hooks/useMarketQueries";
import AnalysisComparisonPanel from "../components/analysis/AnalysisComparisonPanel";
import AnalysisInsightPanel from "../components/analysis/AnalysisInsightPanel";
import AnalysisSymbolPicker from "../components/analysis/AnalysisSymbolPicker";
import { ANALYSIS_RANGE_PRESETS, buildChartData, buildPresetRange, DEFAULT_INDICATORS } from "../components/analysis/analysisUtils";
import AdvancedChart from "../components/analysis/AdvancedChart";
import FundamentalAnalysis from "../components/analysis/FundamentalAnalysis";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import InstrumentChartPanel from "../components/market-detail/InstrumentChartPanel";

export default function AnalysisPage() {
  const { t } = useTranslation();
  const { convertAmount, currency } = useCurrency();
  const [searchParams] = useSearchParams();
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
  const initialHighlightTool = searchParams.get("tool") || null;
  const presetPrice = searchParams.get("preset") ? Number(searchParams.get("preset")) : null;

  const { data: rawQuotes = [], isLoading: quotesLoading, error: quotesQueryError } = useMarketQuotes();
  const quotes = useMemo(() => (Array.isArray(rawQuotes) ? rawQuotes : []), [rawQuotes]);
  const primaryQuote = useMemo(
    () => quotes.find((q) => q.symbol === primarySymbol || q.code === primarySymbol) ?? null,
    [quotes, primarySymbol],
  );
  const quotesError = quotesQueryError ? extractErrorMessage(quotesQueryError, t("analysis.quotesError")) : "";

  useMemo(() => {
    if (quotes.length > 0 && !primarySymbol) {
      setPrimarySymbol(quotes[0]?.symbol || "");
      setSelectedSymbols((current) => (current.length > 0 ? current : quotes[0]?.symbol ? [quotes[0].symbol] : []));
    }
  }, [quotes]);

  const analysisParams = useMemo(
    () => ({
      from: dateRange.from,
      to: dateRange.to,
      indicators: Array.from(selectedIndicators).join(","),
    }),
    [dateRange, selectedIndicators],
  );

  const { data: analysis = null, isLoading: analysisLoading, error: analysisQueryError } = useTechnicalAnalysis(
    primarySymbol,
    analysisParams,
    { enabled: !!(primarySymbol && dateRange.from && dateRange.to) },
  );
  const analysisError = analysisQueryError ? extractErrorMessage(analysisQueryError, t("analysis.analysisError")) : "";

  const comparisonParams = useMemo(
    () =>
      selectedSymbols.length >= 2 && dateRange.from && dateRange.to
        ? { symbols: selectedSymbols.join(","), from: dateRange.from, to: dateRange.to }
        : null,
    [selectedSymbols, dateRange],
  );

  const { data: comparison = null, isLoading: comparisonLoading, error: comparisonQueryError } = useComparisonAnalysis(
    comparisonParams,
    { enabled: !!comparisonParams },
  );
  const comparisonError = comparisonQueryError ? extractErrorMessage(comparisonQueryError, t("analysis.comparisonError")) : "";

  const chartData = useMemo(() => buildChartData(Array.isArray(analysis?.points) ? analysis.points : []), [analysis]);
  const displayChartData = useMemo(() => {
    if (currency === "TRY") return chartData;
    return chartData.map((point) => ({
      ...point,
      close: convertAmount(point.close),
      SMA7: point.SMA7 != null ? convertAmount(point.SMA7) : null,
      SMA20: point.SMA20 != null ? convertAmount(point.SMA20) : null,
      SMA50: point.SMA50 != null ? convertAmount(point.SMA50) : null,
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

  function handleDateRangeChange(field, value) {
    setActiveRange("CUSTOM");
    setDateRange((current) => ({ ...current, [field]: value }));
  }

  function toggleIndicator(indicator) {
    setSelectedIndicators((current) => {
      const next = new Set(current);
      if (next.has(indicator) && next.size > 1) {
        next.delete(indicator);
        return next;
      }

      next.add(indicator);
      return next;
    });
  }

  return (
    <div className="dashboard-stack analysis-lab-shell">
      <div className="analysis-page-header-row">
        <PageHeader
          eyebrow={t("analysis.eyebrow")}
          title={t("analysis.title")}
          description={t("analysis.description")}
        />
        <CurrencyToggle className="analysis-currency-toggle" />
      </div>

      {quotesLoading ? <LoadingSpinner label={t("analysis.quotesLoading")} /> : null}
      {quotesError ? <ErrorMessage message={quotesError} /> : null}

      {!quotesLoading && !quotesError && quotes.length === 0 ? (
        <section className="panel-surface">
          <EmptyState title={t("analysis.emptyTitle")} description={t("analysis.emptyDescription")} />
        </section>
      ) : null}

      {!quotesLoading && !quotesError && quotes.length > 0 ? (
        <>
          <AnalysisSymbolPicker
            quotes={quotes}
            primarySymbol={primarySymbol}
            selectedSymbols={selectedSymbols}
            onPrimaryChange={handlePrimaryChange}
            onToggleComparisonSymbol={handleToggleComparisonSymbol}
          />

          <section className="analysis-lab-grid">
            <div className="analysis-lab-main">
              {primarySymbol ? (
                <>
                  <div className="analysis-chart-mode-toggle">
                    <button
                      className={`chart-mode-btn${chartMode === "simple" ? " active" : ""}`}
                      onClick={() => setChartMode("simple")}
                    >
                      {t("analysis.simpleChart", "Basit Grafik")}
                    </button>
                    <button
                      className={`chart-mode-btn${chartMode === "advanced" ? " active" : ""}`}
                      onClick={() => setChartMode("advanced")}
                    >
                      {t("analysis.advancedChart", "Gelişmiş Grafik")}
                    </button>
                  </div>

                  {chartMode === "simple" ? (
                    <InstrumentChartPanel
                      activeRange={activeRange}
                      onRangeChange={handleRangeChange}
                      dateRange={dateRange}
                      onDateRangeChange={handleDateRangeChange}
                      selectedIndicators={selectedIndicators}
                      onToggleIndicator={toggleIndicator}
                      loading={analysisLoading}
                      error={analysisError}
                      chartData={displayChartData}
                      presets={ANALYSIS_RANGE_PRESETS}
                    />
                  ) : (
                    <AdvancedChart
                      instrumentCode={primarySymbol}
                      initialHighlightTool={initialHighlightTool}
                      presetPrice={presetPrice}
                      quote={primaryQuote}
                    />
                  )}
                </>
              ) : (
                <section className="panel-surface analysis-lab-panel">
                  <EmptyState title={t("analysis.primaryEmptyTitle")} description={t("analysis.primaryEmptyDescription")} />
                </section>
              )}

              {primarySymbol && ["STOCK", "FUND"].includes(primaryQuote?.instrumentType) && (
                <FundamentalAnalysis instrumentCode={primarySymbol} />
              )}

              <AnalysisComparisonPanel
                loading={comparisonLoading}
                error={comparisonError}
                comparison={comparison}
                mode={comparisonMode}
                onModeChange={setComparisonMode}
              />
            </div>

            <AnalysisInsightPanel
              analysis={analysis}
              loading={analysisLoading}
              error={analysisError}
            />
          </section>
        </>
      ) : null}
    </div>
  );
}
