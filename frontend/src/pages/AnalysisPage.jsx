import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { compareTechnicalAnalysis, getMarkets, getTechnicalAnalysis } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import AnalysisComparisonPanel from "../components/analysis/AnalysisComparisonPanel";
import AnalysisInsightPanel from "../components/analysis/AnalysisInsightPanel";
import AnalysisSymbolPicker from "../components/analysis/AnalysisSymbolPicker";
import { ANALYSIS_RANGE_PRESETS, buildChartData, buildPresetRange, DEFAULT_INDICATORS } from "../components/analysis/analysisUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import InstrumentChartPanel from "../components/market-detail/InstrumentChartPanel";

export default function AnalysisPage() {
  const { t } = useTranslation();
  const [quotes, setQuotes] = useState([]);
  const [quotesLoading, setQuotesLoading] = useState(true);
  const [quotesError, setQuotesError] = useState("");
  const [primarySymbol, setPrimarySymbol] = useState("");
  const [selectedSymbols, setSelectedSymbols] = useState([]);
  const [activeRange, setActiveRange] = useState("3M");
  const [dateRange, setDateRange] = useState(() => buildPresetRange(90));
  const [selectedIndicators, setSelectedIndicators] = useState(() => new Set(DEFAULT_INDICATORS));
  const [analysis, setAnalysis] = useState(null);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [analysisError, setAnalysisError] = useState("");
  const [comparison, setComparison] = useState(null);
  const [comparisonLoading, setComparisonLoading] = useState(false);
  const [comparisonError, setComparisonError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadQuotes() {
      try {
        setQuotesLoading(true);
        setQuotesError("");
        const data = await getMarkets();
        if (!active) {
          return;
        }
        const normalizedData = Array.isArray(data) ? data : [];
        setQuotes(normalizedData);
        if (normalizedData.length > 0) {
          setPrimarySymbol((current) => current || normalizedData[0].symbol || "");
          setSelectedSymbols((current) => (current.length > 0 ? current : normalizedData[0]?.symbol ? [normalizedData[0].symbol] : []));
        }
      } catch (err) {
        if (active) {
          setQuotesError(extractErrorMessage(err, t("analysis.quotesError")));
          setQuotes([]);
        }
      } finally {
        if (active) {
          setQuotesLoading(false);
        }
      }
    }

    loadQuotes();
    return () => {
      active = false;
    };
  }, [t]);

  useEffect(() => {
    if (!primarySymbol || !dateRange.from || !dateRange.to) {
      setAnalysis(null);
      return;
    }

    let active = true;

    async function loadAnalysis() {
      try {
        setAnalysisLoading(true);
        setAnalysisError("");
        const data = await getTechnicalAnalysis(
          primarySymbol,
          dateRange.from,
          dateRange.to,
          Array.from(selectedIndicators).join(","),
        );
        if (active) {
          setAnalysis(data ?? null);
        }
      } catch (err) {
        if (active) {
          setAnalysis(null);
          setAnalysisError(extractErrorMessage(err, t("analysis.analysisError")));
        }
      } finally {
        if (active) {
          setAnalysisLoading(false);
        }
      }
    }

    loadAnalysis();
    return () => {
      active = false;
    };
  }, [dateRange, primarySymbol, selectedIndicators, t]);

  useEffect(() => {
    if (selectedSymbols.length < 2 || !dateRange.from || !dateRange.to) {
      setComparison(null);
      setComparisonError("");
      return;
    }

    let active = true;

    async function loadComparison() {
      try {
        setComparisonLoading(true);
        setComparisonError("");
        const data = await compareTechnicalAnalysis({
          symbols: selectedSymbols.join(","),
          from: dateRange.from,
          to: dateRange.to,
        });
        if (active) {
          setComparison(data);
        }
      } catch (err) {
        if (active) {
          setComparison(null);
          setComparisonError(extractErrorMessage(err, t("analysis.comparisonError")));
        }
      } finally {
        if (active) {
          setComparisonLoading(false);
        }
      }
    }

    loadComparison();
    return () => {
      active = false;
    };
  }, [dateRange, selectedSymbols, t]);

  const chartData = useMemo(() => buildChartData(Array.isArray(analysis?.points) ? analysis.points : []), [analysis]);

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
      <PageHeader
        eyebrow={t("analysis.eyebrow")}
        title={t("analysis.title")}
        description={t("analysis.description")}
      />

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
                <InstrumentChartPanel
                  activeRange={activeRange}
                  onRangeChange={handleRangeChange}
                  dateRange={dateRange}
                  onDateRangeChange={handleDateRangeChange}
                  selectedIndicators={selectedIndicators}
                  onToggleIndicator={toggleIndicator}
                  loading={analysisLoading}
                  error={analysisError}
                  chartData={chartData}
                  presets={ANALYSIS_RANGE_PRESETS}
                />
              ) : (
                <section className="panel-surface analysis-lab-panel">
                  <EmptyState title={t("analysis.primaryEmptyTitle")} description={t("analysis.primaryEmptyDescription")} />
                </section>
              )}

              <AnalysisComparisonPanel
                loading={comparisonLoading}
                error={comparisonError}
                comparison={comparison}
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
