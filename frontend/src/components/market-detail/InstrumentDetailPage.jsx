import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams, useSearchParams } from "react-router-dom";
import { getMarketHistory, getMarketBySymbol, getTechnicalAnalysis } from "../../api/marketApi";
import { getNews } from "../../api/newsApi";
import { getCompanyFundamentals, getCompanyDisclosures, getCompanyFinancials } from "../../api/companyApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { addWatchlistItem, getUserWatchlist, removeWatchlistItem } from "../../api/watchlistApi";
import { useAuth } from "../../auth/AuthContext";
import AiCompanyComparisonCard from "../ai/AiCompanyComparisonCard";
import AiFundamentalInsightCard from "../ai/AiFundamentalInsightCard";
import AiTechnicalInsightCard from "../ai/AiTechnicalInsightCard";
import AiUnifiedAnalysisCard from "../ai/AiUnifiedAnalysisCard";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import useToast from "../../hooks/useToast";
import { formatNumber } from "../../utils/formatters";
import AddToPortfolioModal from "./AddToPortfolioModal";
import CreateAlertModal from "./CreateAlertModal";
import InstrumentChartPanel from "./InstrumentChartPanel";
import InstrumentFinancialsPanel from "./InstrumentFinancialsPanel";
import InstrumentFundamentalsPanel from "./InstrumentFundamentalsPanel";
import InstrumentHeader from "./InstrumentHeader";
import InstrumentKapDisclosuresPanel from "./InstrumentKapDisclosuresPanel";
import InstrumentNewsList from "./InstrumentNewsList";
import InstrumentStatsPanel from "./InstrumentStatsPanel";
import InstrumentTabs from "./InstrumentTabs";
import {
  buildChartData,
  buildPresetRange,
  buildStats,
  DEFAULT_INDICATORS,
  formatTrendLabel,
  resolveTrendDirection,
} from "./marketDetailUtils";

export default function InstrumentDetailPage() {
  const { t } = useTranslation();
  const { symbol = "" } = useParams();
  const [searchParams] = useSearchParams();
  const normalizedSymbol = decodeURIComponent(symbol);
  const instrumentType = (searchParams.get("type") || "").trim().toUpperCase();
  const { userId, login } = useAuth();
  const { toast, showToast } = useToast();

  const [quote, setQuote] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [annualHistory, setAnnualHistory] = useState([]);
  const [yearStatsHistory, setYearStatsHistory] = useState([]);
  const [newsItems, setNewsItems] = useState([]);
  const [watchlistItems, setWatchlistItems] = useState([]);
  const [quoteLoading, setQuoteLoading] = useState(true);
  const [analysisLoading, setAnalysisLoading] = useState(true);
  const [, setHistoryLoading] = useState(true);
  const [newsLoading, setNewsLoading] = useState(false);
  const [favoriteBusy, setFavoriteBusy] = useState(false);
  const [quoteError, setQuoteError] = useState("");
  const [analysisError, setAnalysisError] = useState("");
  const [newsError, setNewsError] = useState("");
  const [activeTab, setActiveTab] = useState("overview");
  const [activeRange, setActiveRange] = useState("3M");
  const [dateRange, setDateRange] = useState(() => buildPresetRange(90));
  const [selectedIndicators, setSelectedIndicators] = useState(() => new Set(DEFAULT_INDICATORS));
  const [isPortfolioModalOpen, setPortfolioModalOpen] = useState(false);
  const [isAlertModalOpen, setAlertModalOpen] = useState(false);

  const [fundamentals, setFundamentals] = useState(null);
  const [fundamentalsLoading, setFundamentalsLoading] = useState(false);
  const [fundamentalsError, setFundamentalsError] = useState("");

  const [financialReports, setFinancialReports] = useState([]);
  const [financialsLoading, setFinancialsLoading] = useState(false);
  const [financialsError, setFinancialsError] = useState("");

  const [disclosurePage, setDisclosurePage] = useState(null);
  const [disclosuresLoading, setDisclosuresLoading] = useState(false);
  const [disclosuresError, setDisclosuresError] = useState("");
  const [disclosuresPageIndex, setDisclosuresPageIndex] = useState(0);

  const isDateRangeInvalid = Boolean(
    dateRange.from && dateRange.to && new Date(dateRange.from).getTime() > new Date(dateRange.to).getTime(),
  );

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
        const data = await getMarketBySymbol(normalizedSymbol, { type: instrumentType || undefined });
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
  }, [normalizedSymbol, instrumentType, t]);

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
        const historyRequest = buildHistoryRequest(activeRange, dateRange, quote?.source, instrumentType);
        const nextHistory = await getMarketHistory(normalizedSymbol, historyRequest);
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
  }, [normalizedSymbol, activeRange, dateRange, quote?.source, instrumentType]);

  useEffect(() => {
    if (!normalizedSymbol) {
      setYearStatsHistory([]);
      return;
    }

    let active = true;

    async function loadYearStatsHistory() {
      try {
        const statsRange = buildPresetRange(365);
        const data = await getMarketHistory(normalizedSymbol, {
          ...statsRange,
          source: quote?.source,
          type: instrumentType,
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
  }, [normalizedSymbol, quote?.source, instrumentType]);

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
        const data = await getTechnicalAnalysis(
          normalizedSymbol,
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
          setAnalysisError(extractErrorMessage(err, t("instrumentDetail.analysisError")));
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
  }, [normalizedSymbol, dateRange, selectedIndicators, isDateRangeInvalid]);

  useEffect(() => {
    if (activeTab !== "news" || !normalizedSymbol) {
      return;
    }

    let active = true;

    async function loadNews() {
      try {
        setNewsLoading(true);
        setNewsError("");
        const page = await getNews({ symbol: normalizedSymbol, size: 8 });
        if (active) {
          setNewsItems(page.content ?? []);
        }
      } catch (err) {
        if (active) {
          setNewsItems([]);
          setNewsError(extractErrorMessage(err, t("instrumentDetail.newsError")));
        }
      } finally {
        if (active) {
          setNewsLoading(false);
        }
      }
    }

    loadNews();
    return () => {
      active = false;
    };
  }, [activeTab, normalizedSymbol]);

  useEffect(() => {
    if (!userId || !normalizedSymbol) {
      setWatchlistItems([]);
      return;
    }

    let active = true;

    async function loadWatchlist() {
      try {
        const rows = await getUserWatchlist(userId);
        if (active) {
          setWatchlistItems(rows);
        }
      } catch {
        if (active) {
          setWatchlistItems([]);
        }
      }
    }

    loadWatchlist();
    return () => {
      active = false;
    };
  }, [userId, normalizedSymbol]);

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

  useEffect(() => {
    if (activeTab !== "kapDisclosures" || !normalizedSymbol) {
      return;
    }

    let active = true;

    async function loadDisclosures() {
      try {
        setDisclosuresLoading(true);
        setDisclosuresError("");
        const page = await getCompanyDisclosures(normalizedSymbol, disclosuresPageIndex);
        if (active) {
          setDisclosurePage(page ?? null);
        }
      } catch (err) {
        if (active) {
          setDisclosurePage(null);
          setDisclosuresError(extractErrorMessage(err, t("instrumentDetail.kapDisclosures.error")));
        }
      } finally {
        if (active) {
          setDisclosuresLoading(false);
        }
      }
    }

    loadDisclosures();
    return () => {
      active = false;
    };
  }, [activeTab, normalizedSymbol, disclosuresPageIndex, t]);

  const chartData = useMemo(
    () => buildChartData(Array.isArray(analysis?.points) ? analysis.points : [], annualHistory),
    [analysis, annualHistory],
  );
  const displayTrendDirection = useMemo(
    () => resolveTrendDirection(analysis?.trendDirection, chartData),
    [analysis?.trendDirection, chartData],
  );
  const displaySymbol = useMemo(
    () => formatInstrumentDisplaySymbol(normalizedSymbol),
    [normalizedSymbol],
  );
  const displayTitle = useMemo(
    () => formatInstrumentDisplayTitle(normalizedSymbol, quote?.displayName),
    [normalizedSymbol, quote?.displayName],
  );
  const resolvedInstrumentType = useMemo(
    () => resolveInstrumentType(quote?.instrumentType || instrumentType, normalizedSymbol),
    [quote?.instrumentType, instrumentType, normalizedSymbol],
  );
  const stats = useMemo(() => buildStats(quote, yearStatsHistory), [quote, yearStatsHistory]);
  const latestPrice = quote?.price ?? analysis?.latestPrice ?? null;
  const aiAvailableData = useMemo(
    () => ({
      analysis,
      chartData,
      financialReports,
      fundamentals,
      newsItems,
      quote,
      trendLabel: formatTrendLabel(displayTrendDirection),
    }),
    [analysis, chartData, displayTrendDirection, financialReports, fundamentals, newsItems, quote],
  );
  const quoteUnavailable = !quoteLoading && !quoteError && (!quote || quote.price == null);
  const isFavorite = useMemo(
    () => watchlistItems.some((item) => normalizeCode(item.instrumentCode) === normalizeCode(normalizedSymbol)),
    [watchlistItems, normalizedSymbol],
  );
  const favoriteItemId = useMemo(
    () => watchlistItems.find((item) => normalizeCode(item.instrumentCode) === normalizeCode(normalizedSymbol))?.id,
    [watchlistItems, normalizedSymbol],
  );

  async function ensureSignedIn() {
    if (userId) {
      return true;
    }

    await login();
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
        await addWatchlistItem(userId, { instrumentCode: normalizedSymbol });
        showToast("success", t("instrumentDetail.favoriteAdded"));
      }

      setWatchlistItems(await getUserWatchlist(userId));
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
    setDateRange(buildPresetRange(preset.days));
  }

  function handleDateRangeChange(field, value) {
    setActiveRange("CUSTOM");
    setDateRange((current) => ({ ...current, [field]: value }));
  }

  function handleDisclosurePageChange(newPage) {
    setDisclosuresPageIndex(newPage);
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
        currency={quote?.currency}
        source={quote?.source}
        instrumentType={quote?.instrumentType}
        isFavorite={isFavorite}
        favoriteBusy={favoriteBusy}
        onFavoriteToggle={handleFavoriteToggle}
        onOpenAlert={handleOpenAlertModal}
        onOpenPortfolio={handleOpenPortfolioModal}
      />

      <InstrumentTabs activeTab={activeTab} onChange={setActiveTab} />

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
                {supportsTechnicalAi(resolvedInstrumentType) ? (
                  <AiUnifiedAnalysisCard
                    symbol={displaySymbol}
                    instrumentType={resolvedInstrumentType}
                  />
                ) : null}

                <section className="panel-surface instrument-overview-card">
                  <div className="panel-head">
                    <div>
                      <p className="eyebrow">{t("instrumentDetail.overviewEyebrow")}</p>
                      <h3>{t("instrumentDetail.overviewTitle")}</h3>
                    </div>
                  </div>

                  <div className="instrument-overview-summary">
                    <div className="instrument-overview-metric">
                      <span>{t("instrumentDetail.latestPrice")}</span>
                      <strong>{formatNumber(latestPrice)}</strong>
                    </div>
                    <div className="instrument-overview-metric">
                      <span>Günlük değişim</span>
                      <strong className={getChangeClass(quote?.changeRate)}>{formatSignedPercent(quote?.changeRate)}</strong>
                    </div>
                    <div className="instrument-overview-metric">
                      <span>{t("instrumentDetail.trend")}</span>
                      <strong>{analysisLoading ? "Hazırlanıyor" : formatTrendLabel(displayTrendDirection)}</strong>
                    </div>
                    <div className="instrument-overview-metric">
                      <span>{t("instrumentDetail.dataPoints")}</span>
                      <strong>{analysisLoading ? "-" : formatNumber(chartData.length, 0)}</strong>
                    </div>
                  </div>

                  <div className="instrument-overview-brief">
                    <strong>Kısa piyasa özeti</strong>
                    <p>{buildOverviewMarketSummary(quote, displayTrendDirection, chartData.length, analysisLoading)}</p>
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
                  chartData={chartData}
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

            {activeTab === "news" ? (
              <InstrumentNewsList loading={newsLoading} error={newsError} items={newsItems} />
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

                {supportsFundamentalAi(resolvedInstrumentType) ? (
                  <AiFundamentalInsightCard symbol={displaySymbol} availableData={aiAvailableData} />
                ) : null}
              </section>
            ) : null}

            {activeTab === "kapDisclosures" ? (
              <InstrumentKapDisclosuresPanel
                loading={disclosuresLoading}
                error={disclosuresError}
                page={disclosurePage}
                onPageChange={handleDisclosurePageChange}
              />
            ) : null}
          </div>

          <div className="instrument-detail-side-column">
            <InstrumentStatsPanel stats={stats} />
          </div>
        </section>
      ) : null}

      <AddToPortfolioModal
        isOpen={isPortfolioModalOpen}
        onClose={() => setPortfolioModalOpen(false)}
        symbol={normalizedSymbol}
        currentPrice={latestPrice}
        userId={userId}
        onSuccess={() => handleActionSuccess(t("instrumentDetail.addedToPortfolio"))}
      />

      <CreateAlertModal
        isOpen={isAlertModalOpen}
        onClose={() => setAlertModalOpen(false)}
        symbol={normalizedSymbol}
        currentPrice={latestPrice}
        userId={userId}
        onSuccess={() => handleActionSuccess(t("instrumentDetail.alertCreated"))}
      />
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

function normalizeCode(value) {
  if (value == null) {
    return "";
  }

  const rawValue = String(value).trim();
  if (rawValue.toUpperCase().startsWith("TCMB:")) {
    return rawValue.toUpperCase();
  }

  return rawValue.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function formatSignedPercent(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return String(value);
  }
  return `${numeric >= 0 ? "+" : ""}${formatNumber(numeric, 2)}%`;
}

function getChangeClass(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "";
  }
  return numeric > 0 ? "market-up" : "market-down";
}

function buildOverviewMarketSummary(quote, trendDirection, dataPointCount, loading) {
  if (loading) {
    return "Piyasa özeti hazırlanıyor; fiyat, günlük değişim ve kısa trend bilgisi yüklendikçe güncellenecek.";
  }

  const change = Number(quote?.changeRate);
  const trend = formatTrendLabel(trendDirection).toLocaleLowerCase("tr-TR");
  const priceText = formatNumber(quote?.price);
  const dataText = formatNumber(dataPointCount, 0);

  if (Number.isFinite(change) && change > 0) {
    return `Son fiyat ${priceText}; günlük hareket pozitif. Kısa trend ${trend}, analiz ${dataText} veri noktası üzerinden özetleniyor.`;
  }
  if (Number.isFinite(change) && change < 0) {
    return `Son fiyat ${priceText}; günlük hareket negatif. Kısa trend ${trend}, görünüm ${dataText} veri noktasıyla izleniyor.`;
  }
  return `Son fiyat ${priceText}; günlük hareket yatay veya sınırlı. Kısa trend ${trend}, ${dataText} veri noktasıyla özetleniyor.`;
}

function resolveInstrumentType(value, symbol) {
  const normalized = String(value || "").trim().toUpperCase();
  if (["STOCK", "CRYPTO", "FUND", "FX", "BOND", "FUTURES"].includes(normalized)) {
    return normalized;
  }

  const normalizedSymbol = String(symbol || "").trim().toUpperCase();
  if (normalizedSymbol.startsWith("TCMB:") || /^[A-Z]{3}TRY$/.test(normalizedSymbol)) {
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
  const tcmbMatch = parseTcmbFxInstrumentCode(symbol);
  return tcmbMatch?.currency || symbol || "-";
}

function formatInstrumentDisplayTitle(symbol, fallbackTitle) {
  const tcmbMatch = parseTcmbFxInstrumentCode(symbol);
  return tcmbMatch?.currency || fallbackTitle;
}

function parseTcmbFxInstrumentCode(symbol) {
  const match = String(symbol || "").trim().toUpperCase().match(/^TCMB:([A-Z0-9]+):(BUY|SELL)$/);
  if (!match) {
    return null;
  }

  return {
    currency: match[1],
    side: match[2],
  };
}
