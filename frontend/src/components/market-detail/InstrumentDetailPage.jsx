import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams, useSearchParams } from "react-router-dom";
import { getMarketHistory, getMarketBySymbol, getTechnicalAnalysis } from "../../api/marketApi";
import { getNews } from "../../api/newsApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { addWatchlistItem, getUserWatchlist, removeWatchlistItem } from "../../api/watchlistApi";
import { useAuth } from "../../auth/AuthContext";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import useToast from "../../hooks/useToast";
import AddToPortfolioModal from "./AddToPortfolioModal";
import CreateAlertModal from "./CreateAlertModal";
import InstrumentChartPanel from "./InstrumentChartPanel";
import InstrumentHeader from "./InstrumentHeader";
import InstrumentNewsList from "./InstrumentNewsList";
import InstrumentStatsPanel from "./InstrumentStatsPanel";
import InstrumentTabs from "./InstrumentTabs";
import {
  buildChartData,
  buildPresetRange,
  buildStats,
  DEFAULT_INDICATORS,
  formatSignalLabel,
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
  const [historyLoading, setHistoryLoading] = useState(true);
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
  const stats = useMemo(() => buildStats(quote, yearStatsHistory), [quote, yearStatsHistory]);
  const latestPrice = analysis?.latestPrice ?? quote?.price ?? null;
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
                <section className="panel-surface instrument-overview-card">
                  <div className="panel-head">
                    <div>
                      <p className="eyebrow">{t("instrumentDetail.overviewEyebrow")}</p>
                      <h3>{t("instrumentDetail.overviewTitle")}</h3>
                    </div>
                  </div>

                  {analysisLoading ? <LoadingSpinner label={t("instrumentDetail.analysisLoading")} /> : null}
                  {!analysisLoading && analysisError ? <ErrorMessage message={analysisError} /> : null}
                  {!analysisLoading && !analysisError && !analysis ? (
                    <EmptyState title={t("instrumentDetail.analysisEmptyTitle")} description={t("instrumentDetail.analysisEmptyDescription")} />
                  ) : null}

                  {!analysisLoading && !analysisError && analysis ? (
                    <>
                      <div className="instrument-overview-summary">
                        <div className="instrument-overview-metric">
                          <span>{t("instrumentDetail.trend")}</span>
                          <strong>{formatTrendLabel(displayTrendDirection)}</strong>
                        </div>
                        <div className="instrument-overview-metric">
                          <span>{t("instrumentDetail.latestPrice")}</span>
                          <strong>{latestPrice ?? "-"}</strong>
                        </div>
                        <div className="instrument-overview-metric">
                          <span>{t("instrumentDetail.dataPoints")}</span>
                          <strong>{chartData.length}</strong>
                        </div>
                      </div>

                      <div className="signal-chip-row">
                        {(analysis.signals ?? []).length > 0 ? (
                          analysis.signals.map((signal) => (
                            <span key={signal} className="signal-pill">
                              {formatSignalLabel(signal)}
                            </span>
                          ))
                        ) : (
                          <span className="signal-pill neutral">{t("instrumentDetail.noSignal")}</span>
                        )}
                      </div>

                      <div className="indicator-value-grid terminal-indicator-grid">
                        {(analysis.indicatorValues ?? []).length > 0 ? (
                          analysis.indicatorValues.map((item) => (
                            <div key={item.indicator} className="indicator-value-card">
                              <span>{item.indicator}</span>
                              <strong>{item.value ?? "-"}</strong>
                            </div>
                          ))
                        ) : (
                          <EmptyState
                            title={t("instrumentDetail.indicatorsEmptyTitle")}
                            description={t("instrumentDetail.indicatorsEmptyDescription")}
                          />
                        )}
                      </div>
                    </>
                  ) : null}
                </section>

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
              </section>
            ) : null}

            {activeTab === "chart" ? (
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
            ) : null}

            {activeTab === "news" ? (
              <InstrumentNewsList loading={newsLoading} error={newsError} items={newsItems} />
            ) : null}

            {activeTab === "financials" ? (
              <section className="panel-surface instrument-financials-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("instrumentDetail.financialsEyebrow")}</p>
                    <h3>{t("instrumentDetail.financialsTitle")}</h3>
                  </div>
                </div>
                {/* TODO: replace this fallback with a backend financial statements endpoint when available. */}
                <EmptyState
                  title={t("instrumentDetail.financialsEmptyTitle")}
                  description={t("instrumentDetail.financialsEmptyDescription")}
                />
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

