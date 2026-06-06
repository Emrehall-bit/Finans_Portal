import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams, useSearchParams } from "react-router-dom";
import { getMarketHistory, getMarketBySymbol, getTechnicalAnalysis } from "../../api/marketApi";
import { getNews } from "../../api/newsApi";
import { getCompanyFundamentals, getCompanyFinancials } from "../../api/companyApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useQueryClient } from "@tanstack/react-query";
import { addWatchlistItem, removeWatchlistItem } from "../../api/watchlistApi";
import { watchlistKeys } from "../../api/queryKeys";
import { useUserWatchlist } from "../../hooks/useWatchlistQueries";
import { useAuth } from "../../auth/AuthContext";
import { useCurrency } from "../../currency/CurrencyContext";
import AiCompanyComparisonCard from "../ai/AiCompanyComparisonCard";
import AiFundamentalInsightCard from "../ai/AiFundamentalInsightCard";
import AiTechnicalInsightCard from "../ai/AiTechnicalInsightCard";
import AiUnifiedAnalysisCard from "../ai/AiUnifiedAnalysisCard";
import AuthRequiredModal from "../common/AuthRequiredModal";
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
import FundamentalAnalysis from "../analysis/FundamentalAnalysis";
import InstrumentHeader from "./InstrumentHeader";
import InstrumentKapNewsList from "./InstrumentKapNewsList";
import InstrumentStatsPanel from "./InstrumentStatsPanel";
import InstrumentTabs from "./InstrumentTabs";
import { formatInstrumentCode, formatInstrumentLabel } from "../../utils/instrumentUtils";
import {
  buildChartData,
  buildPresetRange,
  buildStats,
  DEFAULT_INDICATORS,
  formatTrendLabel,
  resolveInstrumentSymbols,
  resolveTrendDirection,
} from "./marketDetailUtils";

export default function InstrumentDetailPage() {
  const { t } = useTranslation();
  const { symbol = "" } = useParams();
  const [searchParams] = useSearchParams();
  const normalizedSymbol = decodeURIComponent(symbol);
  const instrumentType = (searchParams.get("type") || "").trim().toUpperCase();
  const { userId, login } = useAuth();
  const { convertAmount, currency } = useCurrency();
  const { toast, showToast } = useToast();
  const queryClient = useQueryClient();
  const { data: watchlistItems = [] } = useUserWatchlist(userId);

  const [quote, setQuote] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [annualHistory, setAnnualHistory] = useState([]);
  const [yearStatsHistory, setYearStatsHistory] = useState([]);
  const [quoteLoading, setQuoteLoading] = useState(true);
  const [analysisLoading, setAnalysisLoading] = useState(true);
  const [, setHistoryLoading] = useState(true);
  const [favoriteBusy, setFavoriteBusy] = useState(false);
  const [quoteError, setQuoteError] = useState("");
  const [analysisError, setAnalysisError] = useState("");
  const [activeTab, setActiveTab] = useState("overview");
  const [activeRange, setActiveRange] = useState("3M");
  const [dateRange, setDateRange] = useState(() => buildPresetRange({ months: 3 }));
  const [selectedIndicators, setSelectedIndicators] = useState(() => new Set(DEFAULT_INDICATORS));
  const [isPortfolioModalOpen, setPortfolioModalOpen] = useState(false);
  const [isAlertModalOpen, setAlertModalOpen] = useState(false);
  const [isAuthRequiredModalOpen, setAuthRequiredModalOpen] = useState(false);

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
  const displayChartData = useMemo(() => {
    if (currency === "TRY") return chartData;
    return chartData.map((point) => ({
      ...point,
      close: convertAmount(point.close),
      sma7: point.sma7 != null ? convertAmount(point.sma7) : null,
      sma20: point.sma20 != null ? convertAmount(point.sma20) : null,
      sma50: point.sma50 != null ? convertAmount(point.sma50) : null,
    }));
  }, [chartData, currency, convertAmount]);
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
                {resolvedInstrumentType === "STOCK" ? (
                  <AiUnifiedAnalysisCard
                    symbol={displaySymbol}
                    instrumentType="STOCK"
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
                  chartData={displayChartData}
                  currency={currency}
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
