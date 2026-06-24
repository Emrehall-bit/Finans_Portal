import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getMarketHistory, getMarketBySymbol, getTechnicalAnalysis } from "../api/marketApi";
import { getNews } from "../api/newsApi";
import { getCompanyFundamentals, getCompanyFinancials } from "../api/companyApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useQueryClient } from "@tanstack/react-query";
import { addWatchlistItem, removeWatchlistItem } from "../api/watchlistApi";
import { watchlistKeys } from "../api/queryKeys";
import { useUserWatchlist } from "../hooks/useWatchlistQueries";
import { useAuth } from "../auth/AuthContext";
import AiCompanyComparisonCard from "../components/ai/AiCompanyComparisonCard";
import AiFundamentalInsightCard from "../components/ai/AiFundamentalInsightCard";
import AiTechnicalInsightCard from "../components/ai/AiTechnicalInsightCard";
import AiUnifiedAnalysisCard from "../components/ai/AiUnifiedAnalysisCard";
import AuthRequiredModal from "../components/common/AuthRequiredModal";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { useTheme } from "../theme/ThemeContext";
import useToast from "../hooks/useToast";
import { isPointBasedInstrument } from "../utils/formatters";
import AddToPortfolioModal from "../components/market-detail/AddToPortfolioModal";
import CreateAlertModal from "../components/market-detail/CreateAlertModal";
import InstrumentChartPanel from "../components/market-detail/InstrumentChartPanel";
import InstrumentFinancialsPanel from "../components/market-detail/InstrumentFinancialsPanel";
import InstrumentFundamentalsPanel from "../components/market-detail/InstrumentFundamentalsPanel";
import FundamentalAnalysis from "../components/analysis/FundamentalAnalysis";
import InstrumentHeader from "../components/market-detail/InstrumentHeader";
import InstrumentKapNewsList from "../components/market-detail/InstrumentKapNewsList";
import InstrumentStatsPanel from "../components/market-detail/InstrumentStatsPanel";
import InstrumentTabs from "../components/market-detail/InstrumentTabs";
import { formatInstrumentCode, formatInstrumentLabel } from "../utils/instrumentUtils";
import {
  buildChartData,
  buildPresetRange,
  buildStats,
  DEFAULT_INDICATORS,
  formatTrendLabel,
  resolveInstrumentSymbols,
  resolveTrendDirection,
} from "../components/market-detail/marketDetailUtils";

const OVERVIEW_RANGE_PRESETS = [
  { key: "1M", months: 1 },
  { key: "3M", months: 3 },
  { key: "6M", months: 6 },
  { key: "1Y", years: 1 },
  { key: "MAX", years: 10 },
];

export default function MarketDetailPage() {
  const { t } = useTranslation();
  const { symbol = "" } = useParams();
  const [searchParams] = useSearchParams();
  const normalizedSymbol = decodeURIComponent(symbol);
  const instrumentType = (searchParams.get("type") || "").trim().toUpperCase();
  const { userId, login } = useAuth();
  const { toast, showToast } = useToast();
  const queryClient = useQueryClient();
  const { data: watchlistItems = [] } = useUserWatchlist(userId);

  const [quote, setQuote] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [annualHistory, setAnnualHistory] = useState([]);
  const [overviewHistory, setOverviewHistory] = useState([]);
  const [yearStatsHistory, setYearStatsHistory] = useState([]);
  const [quoteLoading, setQuoteLoading] = useState(true);
  const [analysisLoading, setAnalysisLoading] = useState(true);
  const [overviewHistoryLoading, setOverviewHistoryLoading] = useState(true);
  const [, setHistoryLoading] = useState(true);
  const [favoriteBusy, setFavoriteBusy] = useState(false);
  const [quoteError, setQuoteError] = useState("");
  const [analysisError, setAnalysisError] = useState("");
  const [activeTab, setActiveTab] = useState("overview");
  const [activeRange, setActiveRange] = useState("3M");
  const [overviewRange, setOverviewRange] = useState("3M");
  const [dateRange, setDateRange] = useState(() => buildPresetRange({ months: 3 }));
  const [selectedIndicators, setSelectedIndicators] = useState(() => new Set(DEFAULT_INDICATORS));
  const [isPortfolioModalOpen, setPortfolioModalOpen] = useState(false);
  const [isAlertModalOpen, setAlertModalOpen] = useState(false);
  const [isAuthRequiredModalOpen, setAuthRequiredModalOpen] = useState(false);
  const [aiAnalysisStarted, setAiAnalysisStarted] = useState(false);

  const [fundamentals, setFundamentals] = useState(null);
  const [fundamentalsLoading, setFundamentalsLoading] = useState(false);
  const [fundamentalsError, setFundamentalsError] = useState("");

  const [financialReports, setFinancialReports] = useState([]);
  const [financialsLoading, setFinancialsLoading] = useState(false);
  const [financialsError, setFinancialsError] = useState("");

  const [kapItems, setKapItems] = useState([]);
  const [kapLoading, setKapLoading] = useState(false);
  const [kapError, setKapError] = useState("");

  const isDateRangeInvalid = Boolean(
    dateRange.from && dateRange.to && new Date(dateRange.from).getTime() > new Date(dateRange.to).getTime(),
  );
  const routeInstrumentType = useMemo(
    () => resolveInstrumentType(instrumentType, normalizedSymbol),
    [instrumentType, normalizedSymbol],
  );
  const symbolPair = useMemo(
    () => resolveInstrumentSymbols(normalizedSymbol, routeInstrumentType),
    [normalizedSymbol, routeInstrumentType],
  );
  const apiSymbol = symbolPair.apiSymbol;
  const routeDisplaySymbol = symbolPair.displaySymbol;
  const resolvedInstrumentType = useMemo(
    () => resolveInstrumentType(quote?.instrumentType || instrumentType, apiSymbol || normalizedSymbol),
    [quote?.instrumentType, instrumentType, apiSymbol, normalizedSymbol],
  );
  const isPointBased = isPointBasedInstrument({ instrumentType: resolvedInstrumentType, displayUnit: quote?.displayUnit });

  useEffect(() => {
    if (!normalizedSymbol) {
      setQuote(null);
      setQuoteError(t("instrumentDetail.invalidInstrument"));
      setQuoteLoading(false);
      return;
    }

    let active = true;

    async function loadQuote() {
      try {
        setQuoteLoading(true);
        setQuoteError("");
        const data = await getMarketBySymbol(apiSymbol, { type: resolvedInstrumentType || instrumentType || undefined });
        if (active) {
          setQuote(data ?? null);
        }
      } catch (err) {
        if (active) {
          setQuote(null);
          setQuoteError(extractErrorMessage(err, t("instrumentDetail.quoteError")));
        }
      } finally {
        if (active) {
          setQuoteLoading(false);
        }
      }
    }

    loadQuote();
    return () => {
      active = false;
    };
  }, [normalizedSymbol, apiSymbol, resolvedInstrumentType, instrumentType, t]);

  useEffect(() => {
    if (!normalizedSymbol) {
      setAnnualHistory([]);
      setHistoryLoading(false);
      return;
    }

    let active = true;

    async function loadAnnualHistory() {
      try {
        setHistoryLoading(true);
        const historyRequest = buildHistoryRequest(activeRange, dateRange, quote?.source, resolvedInstrumentType);
        const nextHistory = await getMarketHistory(apiSymbol, historyRequest);
        if (active) {
          setAnnualHistory(Array.isArray(nextHistory) ? nextHistory : []);
        }
      } catch {
        if (active) {
          setAnnualHistory([]);
        }
      } finally {
        if (active) {
          setHistoryLoading(false);
        }
      }
    }

    loadAnnualHistory();
    return () => {
      active = false;
    };
  }, [normalizedSymbol, activeRange, dateRange, quote?.source, resolvedInstrumentType, apiSymbol]);

  useEffect(() => {
    if (!normalizedSymbol) {
      setOverviewHistory([]);
      setOverviewHistoryLoading(false);
      return;
    }

    let active = true;
    const preset = OVERVIEW_RANGE_PRESETS.find((item) => item.key === overviewRange) ?? OVERVIEW_RANGE_PRESETS[1];

    async function loadOverviewHistory() {
      try {
        setOverviewHistoryLoading(true);
        const nextHistory = await getMarketHistory(apiSymbol, {
          ...buildPresetRange(preset),
          source: quote?.source,
          type: resolvedInstrumentType,
        });
        if (active) {
          setOverviewHistory(Array.isArray(nextHistory) ? nextHistory : []);
        }
      } catch {
        if (active) {
          setOverviewHistory([]);
        }
      } finally {
        if (active) {
          setOverviewHistoryLoading(false);
        }
      }
    }

    loadOverviewHistory();
    return () => {
      active = false;
    };
  }, [normalizedSymbol, overviewRange, quote?.source, resolvedInstrumentType, apiSymbol]);

  useEffect(() => {
    if (!normalizedSymbol) {
      setYearStatsHistory([]);
      return;
    }

    let active = true;

    async function loadYearStatsHistory() {
      try {
        const statsRange = buildPresetRange(365);
        const data = await getMarketHistory(apiSymbol, {
          ...statsRange,
          source: quote?.source,
          type: resolvedInstrumentType,
        });
        if (active) {
          setYearStatsHistory(Array.isArray(data) ? data : []);
        }
      } catch {
        if (active) {
          setYearStatsHistory([]);
        }
      }
    }

    loadYearStatsHistory();
    return () => {
      active = false;
    };
  }, [normalizedSymbol, quote?.source, resolvedInstrumentType, apiSymbol]);

  useEffect(() => {
    if (!normalizedSymbol || !dateRange.from || !dateRange.to || isDateRangeInvalid) {
      setAnalysis(null);
      setAnalysisError(isDateRangeInvalid ? t("instrumentDetail.invalidDateRange") : "");
      setAnalysisLoading(false);
      return;
    }

    let active = true;

    async function loadAnalysis() {
      try {
        setAnalysisLoading(true);
        setAnalysisError("");
        const data = await getTechnicalAnalysis(apiSymbol, {
          from: dateRange.from,
          to: dateRange.to,
          indicators: Array.from(selectedIndicators).join(","),
          instrumentType: resolvedInstrumentType,
        });
        if (active) {
          setAnalysis(data ?? null);
        }
      } catch (err) {
        if (active) {
          setAnalysis(null);
          setAnalysisError(isNonTcmbFxSymbol(apiSymbol, resolvedInstrumentType) ? "" : extractErrorMessage(err, t("instrumentDetail.analysisError")));
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
  }, [normalizedSymbol, dateRange, selectedIndicators, isDateRangeInvalid, apiSymbol, resolvedInstrumentType, t]);


  useEffect(() => {
    if (activeTab !== "fundamentals" || !normalizedSymbol) {
      return;
    }

    let active = true;

    async function loadFundamentals() {
      try {
        setFundamentalsLoading(true);
        setFundamentalsError("");
        const data = await getCompanyFundamentals(normalizedSymbol);
        if (active) {
          setFundamentals(data ?? null);
        }
      } catch (err) {
        if (active) {
          setFundamentals(null);
          setFundamentalsError(extractErrorMessage(err, t("instrumentDetail.fundamentals.error")));
        }
      } finally {
        if (active) {
          setFundamentalsLoading(false);
        }
      }
    }

    loadFundamentals();
    return () => {
      active = false;
    };
  }, [activeTab, normalizedSymbol, t]);

  useEffect(() => {
    if (activeTab !== "kapDisclosures" || !normalizedSymbol) {
      return;
    }

    let active = true;

    async function loadKapDisclosures() {
      try {
        setKapLoading(true);
        setKapError("");
        const page = await getNews({ symbol: normalizedSymbol, isKapDisclosure: true, size: 30 });
        if (active) {
          setKapItems(page.content ?? []);
        }
      } catch (err) {
        if (active) {
          setKapItems([]);
          setKapError(extractErrorMessage(err, t("instrumentDetail.kapDisclosures.error")));
        }
      } finally {
        if (active) {
          setKapLoading(false);
        }
      }
    }

    loadKapDisclosures();
    return () => {
      active = false;
    };
  }, [activeTab, normalizedSymbol, t]);

  useEffect(() => {
    if (activeTab !== "financials" || !normalizedSymbol) {
      return;
    }

    let active = true;

    async function loadFinancials() {
      try {
        setFinancialsLoading(true);
        setFinancialsError("");
        const data = await getCompanyFinancials(normalizedSymbol);
        if (active) {
          setFinancialReports(Array.isArray(data) ? data : []);
        }
      } catch (err) {
        if (active) {
          setFinancialReports([]);
          if (err?.response?.status === 404) {
            setFinancialsError("");
          } else {
            setFinancialsError(extractErrorMessage(err, t("instrumentDetail.financialsError", "Finansallar yüklenemedi.")));
          }
        }
      } finally {
        if (active) {
          setFinancialsLoading(false);
        }
      }
    }

    loadFinancials();
    return () => {
      active = false;
    };
  }, [activeTab, normalizedSymbol, t]);

  const chartData = useMemo(
    () => buildChartData(Array.isArray(analysis?.points) ? analysis.points : [], annualHistory),
    [analysis, annualHistory],
  );
  const displayChartData = chartData;
  const overviewChartData = useMemo(() => buildChartData([], overviewHistory), [overviewHistory]);
  const displayOverviewChartData = overviewChartData;
  const displayTrendDirection = useMemo(
    () => resolveTrendDirection(analysis?.trendDirection, chartData),
    [analysis?.trendDirection, chartData],
  );
  const displaySymbol = useMemo(
    () => formatInstrumentDisplaySymbol(routeDisplaySymbol || normalizedSymbol),
    [routeDisplaySymbol, normalizedSymbol],
  );
  const displayTitle = useMemo(
    () => formatInstrumentDisplayTitle(normalizedSymbol, quote?.displayName),
    [normalizedSymbol, quote?.displayName],
  );
  const stats = useMemo(() => buildStats(quote, yearStatsHistory), [quote, yearStatsHistory]);
  const latestPrice = quote?.sellRate ?? quote?.price ?? analysis?.latestPrice ?? null;
  const aiAvailableData = useMemo(
    () => ({
      analysis,
      chartData,
      financialReports,
      fundamentals,
      quote,
      trendLabel: formatTrendLabel(displayTrendDirection),
    }),
    [analysis, chartData, displayTrendDirection, financialReports, fundamentals, quote],
  );
  const quoteUnavailable = !quoteLoading && !quoteError && (!quote || quote.price == null);
  const isFavorite = useMemo(
    () => watchlistItems.some((item) => normalizeCode(item.instrumentCode) === normalizeCode(apiSymbol)),
    [watchlistItems, apiSymbol],
  );
  const favoriteItemId = useMemo(
    () => watchlistItems.find((item) => normalizeCode(item.instrumentCode) === normalizeCode(apiSymbol))?.id,
    [watchlistItems, apiSymbol],
  );

  async function ensureSignedIn() {
    if (userId) {
      return true;
    }

    setAuthRequiredModalOpen(true);
    return false;
  }

  async function handleFavoriteToggle() {
    const ready = await ensureSignedIn();
    if (!ready) {
      return;
    }

    try {
      setFavoriteBusy(true);
      if (isFavorite && favoriteItemId) {
        await removeWatchlistItem(favoriteItemId);
        showToast("success", t("instrumentDetail.favoriteRemoved"));
      } else {
        await addWatchlistItem(userId, { instrumentCode: apiSymbol });
        showToast("success", t("instrumentDetail.favoriteAdded"));
      }

      queryClient.invalidateQueries({ queryKey: watchlistKeys.byUser(userId) });
    } catch (err) {
      setQuoteError(extractErrorMessage(err, t("instrumentDetail.favoriteError")));
    } finally {
      setFavoriteBusy(false);
    }
  }

  async function handleOpenPortfolioModal() {
    const ready = await ensureSignedIn();
    if (ready) {
      setPortfolioModalOpen(true);
    }
  }

  async function handleOpenAlertModal() {
    const ready = await ensureSignedIn();
    if (ready) {
      setAlertModalOpen(true);
    }
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

  function handleRangeChange(preset) {
    setActiveRange(preset.key);
    setDateRange(buildPresetRange(preset));
  }

  function handleDateRangeChange(field, value) {
    setActiveRange("CUSTOM");
    setDateRange((current) => ({ ...current, [field]: value }));
  }

  function handleActionSuccess(message) {
    showToast("success", message);
  }

  return (
    <div className="dashboard-stack market-detail-page instrument-detail-page-shell">
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}

      <InstrumentHeader
        symbol={displaySymbol}
        displayName={displayTitle}
        price={latestPrice}
        changeRate={quote?.changeRate}
        instrumentType={quote?.instrumentType}
        isFavorite={isFavorite}
        favoriteBusy={favoriteBusy}
        onFavoriteToggle={handleFavoriteToggle}
        onOpenAlert={handleOpenAlertModal}
        onOpenPortfolio={isPointBased ? null : handleOpenPortfolioModal}
      />

      <InstrumentTabs activeTab={activeTab} onChange={setActiveTab} instrumentType={instrumentType} />

      {quoteLoading ? <LoadingSpinner label={t("instrumentDetail.loading")} /> : null}
      {quoteError ? <ErrorMessage message={quoteError} /> : null}

      {!quoteLoading && !quoteError && quoteUnavailable ? (
        <section className="panel-surface">
          <EmptyState title={t("instrumentDetail.emptyTitle")} description={t("instrumentDetail.emptyDescription")} />
        </section>
      ) : null}

      {!quoteLoading && !quoteError && !quoteUnavailable ? (
        <section className="instrument-detail-grid">
          <div className="instrument-detail-main-column">
            {activeTab === "overview" ? (
              <section className="instrument-overview-stack">
                <section className="panel-surface instrument-overview-card instrument-overview-chart-card">
                  <div className="panel-head">
                    <div>
                      <h3>Fiyat Hareketi</h3>
                    </div>
                    <OverviewRangeSelector
                      activeRange={overviewRange}
                      onRangeChange={setOverviewRange}
                      presets={OVERVIEW_RANGE_PRESETS}
                    />
                  </div>

                  <OverviewPriceChart
                    chartData={displayOverviewChartData}
                    currency="TRY"
                    isPointBased={isPointBased}
                    loading={overviewHistoryLoading}
                  />

                  <div className="instrument-overview-chart-footer">
                    <p>Mum grafik, çizim araçları ve teknik göstergeler için Teknik Analiz ekranına geçin.</p>
                    <Link to={buildAnalysisPath(apiSymbol, resolvedInstrumentType)}>
                      Teknik Analize Git
                    </Link>
                  </div>
                </section>
              </section>
            ) : null}

            {activeTab === "chart" ? (
              <section className="instrument-overview-stack">
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
                  currency="TRY"
                  instrumentType={resolvedInstrumentType}
                />

                {supportsTechnicalAi(resolvedInstrumentType) ? (
                  <AiTechnicalInsightCard
                    symbol={displaySymbol}
                    availableData={aiAvailableData}
                    highRisk={resolvedInstrumentType === "FUTURES"}
                  />
                ) : null}

                {supportsTechnicalAi(resolvedInstrumentType) ? (
                  <AiCompanyComparisonCard
                    leftSymbol={normalizedSymbol}
                    displaySymbol={displaySymbol}
                    instrumentType={resolvedInstrumentType}
                  />
                ) : null}
              </section>
            ) : null}

            {activeTab === "kapDisclosures" ? (
              <InstrumentKapNewsList loading={kapLoading} error={kapError} items={kapItems} />
            ) : null}

            {activeTab === "financials" ? (
              <InstrumentFinancialsPanel
                loading={financialsLoading}
                error={financialsError}
                reports={financialReports}
              />
            ) : null}

            {activeTab === "fundamentals" ? (
              <section className="instrument-overview-stack">
                <InstrumentFundamentalsPanel
                  loading={fundamentalsLoading}
                  error={fundamentalsError}
                  data={fundamentals}
                />

                <FundamentalAnalysis instrumentCode={normalizedSymbol} />

                {supportsFundamentalAi(resolvedInstrumentType) ? (
                  <AiFundamentalInsightCard symbol={displaySymbol} availableData={aiAvailableData} />
                ) : null}
              </section>
            ) : null}
          </div>

          <div className="instrument-detail-side-column">
            <InstrumentStatsPanel stats={stats} />
            {activeTab === "overview" ? (
              aiAnalysisStarted ? (
                <AiUnifiedAnalysisCard
                  symbol={apiSymbol}
                  instrumentType={resolvedInstrumentType}
                />
              ) : (
                <section className="panel-surface instrument-overview-ai-card">
                  <div>
                    <h3>Yapay zekâ yorumu</h3>
                    <p>Bu enstrümanı mevcut fiyat hareketi ve veriler üzerinden yapay zekâ ile yorumlayın.</p>
                  </div>
                  <button
                    type="button"
                    className="secondary-button instrument-overview-link"
                    onClick={() => setAiAnalysisStarted(true)}
                  >
                    AI Analizini Başlat
                  </button>
                </section>
              )
            ) : null}
          </div>
        </section>
      ) : null}

      <AddToPortfolioModal
        isOpen={isPortfolioModalOpen}
        onClose={() => setPortfolioModalOpen(false)}
        symbol={apiSymbol}
        displaySymbol={displaySymbol}
        currentPrice={latestPrice}
        userId={userId}
        onSuccess={() => handleActionSuccess(t("instrumentDetail.addedToPortfolio"))}
      />

      <CreateAlertModal
        isOpen={isAlertModalOpen}
        onClose={() => setAlertModalOpen(false)}
        symbol={apiSymbol}
        displaySymbol={displaySymbol}
        currentPrice={latestPrice}
        userId={userId}
        onSuccess={() => handleActionSuccess(t("instrumentDetail.alertCreated"))}
      />
      <AuthRequiredModal
        isOpen={isAuthRequiredModalOpen}
        onClose={() => setAuthRequiredModalOpen(false)}
        onConfirm={login}
      />
    </div>
  );
}

function OverviewRangeSelector({ activeRange, onRangeChange, presets }) {
  return (
    <div className="overview-timeframes" aria-label="Fiyat hareketi zaman aralığı">
      {presets.map((preset) => (
        <button
          key={preset.key}
          type="button"
          className={`overview-timeframe-button${activeRange === preset.key ? " active" : ""}`}
          onClick={() => onRangeChange(preset.key)}
        >
          {formatOverviewRangeLabel(preset.key)}
        </button>
      ))}
    </div>
  );
}

function OverviewPriceChart({ chartData, currency, isPointBased, loading }) {
  const { chartTheme } = useTheme();
  const hasData = chartData.length > 0;

  return (
    <div className="instrument-overview-price-panel">
      {loading ? <LoadingSpinner label="Fiyat hareketi yükleniyor" /> : null}
      {!loading && !hasData ? (
        <EmptyState title="Fiyat verisi bulunamadı" description="Bu aralık için fiyat geçmişi geldiğinde grafik burada gösterilecek." />
      ) : null}

      {!loading && hasData ? (
        <div className="instrument-overview-chart-wrap terminal-chart-shell">
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={chartData} margin={{ top: 16, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="4 4" stroke={chartTheme.grid} strokeOpacity={0.45} />
              <XAxis
                dataKey="dateKey"
                stroke={chartTheme.axis}
                tickFormatter={formatCompactChartDate}
                tickLine={false}
                axisLine={false}
                tick={{ fontSize: 11 }}
              />
              <YAxis
                stroke={chartTheme.axis}
                tickFormatter={(value) => formatOverviewAxisValue(value, currency, isPointBased)}
                tickLine={false}
                axisLine={false}
                width={76}
                tick={{ fontSize: 11 }}
              />
              <Tooltip content={<OverviewChartTooltip chartTheme={chartTheme} currency={currency} isPointBased={isPointBased} />} />
              <Line
                type="monotone"
                dataKey="close"
                name="Fiyat"
                stroke={chartTheme.priceLine}
                strokeWidth={2.5}
                dot={false}
                activeDot={{ r: 4 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : null}
    </div>
  );
}

function OverviewChartTooltip({ active, payload, chartTheme, currency, isPointBased }) {
  if (!active || !payload?.length) return null;

  const point = payload[0]?.payload;
  return (
    <div className="chart-tooltip terminal-tooltip" style={{ backgroundColor: chartTheme.tooltipBg, borderColor: chartTheme.tooltipBorder, color: chartTheme.tooltipText }}>
      <strong>{point?.fullDate || point?.dateKey || "-"}</strong>
      <div className="chart-tooltip-row">
        <span>Fiyat</span>
        <strong>{formatOverviewPrice(payload[0]?.value, currency, isPointBased)}</strong>
      </div>
    </div>
  );
}

function buildHistoryRequest(activeRange, dateRange, source, type) {
  return {
    ...(source ? { source } : {}),
    ...(type ? { type } : {}),
    from: dateRange?.from,
    to: dateRange?.to,
  };
}

function formatOverviewRangeLabel(key) {
  return {
    "1M": "1A",
    "3M": "3A",
    "6M": "6A",
    "1Y": "1Y",
    MAX: "MAX",
  }[key] ?? key;
}

function formatCompactChartDate(value) {
  if (!value) return "-";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return date.toLocaleDateString("tr-TR", { month: "short", day: "numeric" });
}

function formatOverviewAxisValue(value, currency, isPointBased = false) {
  if (value == null || Number.isNaN(Number(value))) return "";

  const prefix = isPointBased ? "" : currency === "USD" ? "$" : "₺";
  const numeric = Math.abs(Number(value));
  if (numeric >= 1_000_000) return `${prefix}${(Number(value) / 1_000_000).toFixed(1)}M`;
  if (numeric >= 1_000) return `${prefix}${(Number(value) / 1_000).toFixed(1)}K`;
  return `${prefix}${Number(value).toLocaleString("tr-TR", { maximumFractionDigits: 2 })}`;
}

function formatOverviewPrice(value, currency, isPointBased = false) {
  if (value == null || Number.isNaN(Number(value))) return "-";
  const prefix = isPointBased ? "" : currency === "USD" ? "$" : "₺";
  return `${prefix}${Number(value).toLocaleString("tr-TR", { maximumFractionDigits: 2 })}`;
}

function buildAnalysisPath(symbol, instrumentType) {
  const params = new URLSearchParams();
  if (symbol) params.set("symbol", symbol);
  if (instrumentType) params.set("type", instrumentType);
  params.set("tool", "advanced");
  const query = params.toString();
  return query ? `/analysis?${query}` : "/analysis";
}

function normalizeCode(value) {
  if (value == null) {
    return "";
  }

  const rawValue = String(value).trim().toUpperCase();
  if (/^[A-Z_]+:[A-Z]{2,4}:(BUY|SELL)$/.test(rawValue)) {
    return rawValue;
  }

  return rawValue.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function isNonTcmbFxSymbol(symbol, instrumentType) {
  if (String(instrumentType || "").trim().toUpperCase() !== "FX") {
    return false;
  }

  const normalizedSymbol = String(symbol || "").trim().toUpperCase();
  const providerMatch = normalizedSymbol.match(/^([A-Z_]+):[A-Z]{2,4}:(BUY|SELL)$/);
  return Boolean(providerMatch && providerMatch[1] !== "TCMB");
}

function resolveInstrumentType(value, symbol) {
  const normalized = String(value || "").trim().toUpperCase();
  if (["STOCK", "CRYPTO", "FUND", "FX", "BOND", "FUTURES"].includes(normalized)) {
    return normalized;
  }

  const normalizedSymbol = String(symbol || "").trim().toUpperCase();
  if (/^[A-Z_]+:[A-Z]{2,4}:(BUY|SELL)$/.test(normalizedSymbol) || /^[A-Z]{3}TRY$/.test(normalizedSymbol)) {
    return "FX";
  }
  if (/(USDT|TRY)$/.test(normalizedSymbol) && /^(BTC|ETH|SOL|XRP|BNB|AVAX)/.test(normalizedSymbol)) {
    return "CRYPTO";
  }

  return "STOCK";
}

function supportsTechnicalAi(instrumentType) {
  return ["STOCK", "CRYPTO", "FUTURES"].includes(String(instrumentType || "").toUpperCase());
}

function supportsFundamentalAi(instrumentType) {
  return String(instrumentType || "").toUpperCase() === "STOCK";
}

function formatInstrumentDisplaySymbol(symbol) {
  return formatInstrumentCode(symbol);
}

function formatInstrumentDisplayTitle(symbol, fallbackTitle) {
  const formattedSymbol = formatInstrumentCode(symbol);
  const formattedLabel = formatInstrumentLabel(symbol);
  if (formattedSymbol && formattedLabel && formattedLabel !== formattedSymbol) {
    return formattedLabel;
  }
  return fallbackTitle || formattedSymbol;
}
