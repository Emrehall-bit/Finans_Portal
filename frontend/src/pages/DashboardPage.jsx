import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ArrowDown, ArrowUp, Bell, Eye, TrendingDown, TrendingUp, Wallet } from "lucide-react";
import { Link } from "react-router-dom";
import { useQueries } from "@tanstack/react-query";
import { buildMarketDetailPath } from "../api/marketApi";
import { getPortfolioDetails } from "../api/portfolioApi";
import { portfolioKeys } from "../api/queryKeys";
import { useAuth } from "../auth/AuthContext";
import AiMarketSummaryCard from "../components/ai/AiMarketSummaryCard";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { useUserAlerts } from "../hooks/useAlertQueries";
import { useMarketQuotes } from "../hooks/useMarketQueries";
import { useNewsList } from "../hooks/useNewsQueries";
import { useUserPortfolios } from "../hooks/usePortfolioQueries";
import { useUserWatchlist } from "../hooks/useWatchlistQueries";
import { formatCurrency, formatDateTime, formatNumber } from "../utils/formatters";
export default function DashboardPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();

  const { data: marketQuotes = [], isLoading: quotesLoading, error: quotesError } = useMarketQuotes();
  const { data: newsPage, isLoading: newsLoading, error: newsError } = useNewsList(
    { size: 6, isKapDisclosure: false },
    { staleTime: 2 * 60_000 },
  );
  const { data: watchlistItems = [], error: watchlistError } = useUserWatchlist(userId);
  const { data: alerts = [], error: alertsError } = useUserAlerts(userId);
  const { data: portfolios = [], isLoading: portfoliosLoading, error: portfoliosError } = useUserPortfolios(userId);

  const portfolioDetailQueries = useQueries({
    queries: portfolios.map((p) => ({
      queryKey: portfolioKeys.details(p.portfolioId),
      queryFn: () => getPortfolioDetails(p.portfolioId),
      staleTime: 30_000,
    })),
  });

  const loading = quotesLoading || newsLoading || portfoliosLoading;
  const newsItems = (newsPage?.content ?? []).slice(0, 4);
  const portfolioSnapshots = portfolioDetailQueries
    .filter((q) => q.status === "success" && q.data)
    .map((q) => q.data);

  const sectionErrors = {
    market: quotesError ? t("dashboard.marketError") : null,
    news: newsError ? t("dashboard.newsError") : null,
    watchlist: watchlistError && userId ? t("dashboard.watchlistError") : null,
    alerts: alertsError && userId ? t("dashboard.alertsError") : null,
    portfolios: portfoliosError && userId ? t("dashboard.portfoliosError") : null,
  };

  const overviewCards = useMemo(() => {
    const portfolioValue = portfolioSnapshots.reduce(
      (sum, item) => sum + toNumber(item?.summary?.currentValue ?? item?.summary?.totalCurrentValue),
      0,
    );
    const hasPortfolio = portfolioSnapshots.length > 0 && portfolioValue > 0;

    const activeAlertCount = alerts.filter((item) => String(item?.status || "").toUpperCase() === "ACTIVE").length || alerts.length;
    const dailyProfitLoss = portfolioSnapshots.reduce(
      (sum, snapshot) => sum + toNumber(snapshot?.summary?.dailyProfitLoss),
      0,
    );
    const pnlTone = dailyProfitLoss > 0 ? "positive" : dailyProfitLoss < 0 ? "negative" : "neutral";

    return [
      {
        title: t("dashboard.cards.portfolioValueTitle"),
        value: formatCurrency(portfolioValue),
        subtitle: hasPortfolio ? t("dashboard.cards.portfolioValueSubtitle", { count: portfolioSnapshots.length }) : null,
        trend: hasPortfolio ? t("dashboard.cards.portfolioValueTrend") : null,
        tone: "cool",
        icon: <Wallet size={18} />,
        isEmpty: !hasPortfolio,
      },
      {
        title: t("dashboard.cards.dailyPnLTitle"),
        value: formatCurrency(dailyProfitLoss),
        subtitle: t("dashboard.cards.dailyPnLSubtitle"),
        trend: formatSignedCurrency(dailyProfitLoss),
        tone: pnlTone,
        icon: dailyProfitLoss < 0 ? <TrendingDown size={18} /> : <TrendingUp size={18} />,
      },
      {
        title: t("dashboard.cards.watchlistTitle"),
        value: formatNumber(watchlistItems.length, 0),
        subtitle: t("dashboard.cards.watchlistSubtitle"),
        trend: watchlistItems.length ? t("dashboard.cards.watchlistTrend") : null,
        tone: "neutral",
        icon: <Eye size={18} />,
      },
      {
        title: t("dashboard.cards.alertsTitle"),
        value: formatNumber(activeAlertCount, 0),
        subtitle: t("dashboard.cards.alertsSubtitle"),
        trend: alerts.some(isTriggeredAlert) ? t("dashboard.cards.alertsTrend") : null,
        tone: alerts.some(isTriggeredAlert) ? "warm" : "neutral",
        icon: <Bell size={18} />,
      },
    ];
  }, [alerts, portfolioSnapshots, t, watchlistItems.length]);

  const watchlistRows = useMemo(() => {
    if (!watchlistItems.length) return [];
    // Backend normalizeSymbol strips all non-alphanumeric chars before storing.
    // e.g. "TCMB:USD:SELL" is stored as "TCMBUSDSELL".
    // We must apply the same transform to quote symbols when building the lookup map.
    const norm = (s) => (s ?? "").replace(/[^A-Za-z0-9]/g, "").toUpperCase();
    const quoteMap = new Map();
    for (const q of marketQuotes) {
      quoteMap.set(norm(q.symbol), q);
      if (q.code) quoteMap.set(norm(q.code), q);
    }
    return watchlistItems.slice(0, 7).map((item) => {
      const quote = quoteMap.get(item.instrumentCode);
      const shortCode = quote?.code ?? item.instrumentCode;
      const rawName = quote?.displayName ?? "";
      const isDuplicateName = !rawName || rawName === shortCode || rawName === item.instrumentCode;
      const typeLabel = INSTRUMENT_TYPE_LABELS[quote?.instrumentType] ?? null;
      return {
        id: item.id,
        code: shortCode,
        secondLine: isDuplicateName ? typeLabel : rawName,
        price: quote?.sellRate ?? quote?.price ?? null,
        changeRate: quote?.changeRate ?? null,
        instrumentType: quote?.instrumentType ?? null,
        symbol: quote?.symbol ?? item.instrumentCode,
      };
    });
  }, [watchlistItems, marketQuotes]);

  const [marketTab, setMarketTab] = useState("gainers");
  const marketRows = useMemo(() => {
    return [...marketQuotes]
      .filter((item) => Number.isFinite(Number(item.changeRate)))
      .sort((left, right) => {
        if (marketTab === "losers") {
          return toNumber(left.changeRate) - toNumber(right.changeRate);
        }

        return toNumber(right.changeRate) - toNumber(left.changeRate);
      })
      .slice(0, 5);
  }, [marketQuotes, marketTab]);





  return (
    <div className="dashboard-stack finance-dashboard-shell">
      {loading ? <LoadingSpinner label={t("dashboard.loading")} /> : null}

      {!loading ? (
        <>
          <section className="ticker-grid finance-dashboard-kpis">
            {overviewCards.map((card) => (
              <div key={card.title} className={`summary-card summary-card-${card.tone}`}>
                <div className="summary-card-top">
                  <div className="summary-card-title-row">
                    {card.icon ? <span className={`summary-card-icon summary-card-icon--${card.tone}`}>{card.icon}</span> : null}
                    <p className="summary-card-title">{card.title}</p>
                  </div>
                  {card.trend ? <span className="summary-chip">{card.trend}</span> : null}
                </div>
                <h3>{card.value}</h3>
                {card.isEmpty ? (
                  <Link to="/portfolio" className="summary-card-cta">{t("dashboard.cards.addPortfolio")}</Link>
                ) : card.subtitle ? (
                  <p className="summary-card-subtitle">{card.subtitle}</p>
                ) : null}
                <div aria-hidden="true" className={`summary-card-bar summary-card-bar--${card.tone}`} />
              </div>
            ))}
          </section>

          <section className="finance-dashboard-grid">
            <div className="finance-dashboard-main">
              <AiMarketSummaryCard marketQuotes={marketQuotes} newsItems={newsItems} />

              {userId ? (
                <section className="panel-surface finance-dashboard-panel dash-watchlist-panel">
                  <div className="panel-head">
                    <h3 className="dashboard-section-title">
                      {t("dashboard.watchlistWidgetTitle")}
                      {watchlistRows.length > 0 ? <span className="dash-watchlist-count">({watchlistRows.length})</span> : null}
                    </h3>
                    <Link to="/markets" state={{ category: "FAVORITES" }} className="panel-text-link">{t("dashboard.watchlistWidgetSeeAll")}</Link>
                  </div>
                  {watchlistRows.length === 0 ? (
                    <EmptyState
                      title={t("dashboard.watchlistWidgetEmptyTitle")}
                      description={t("dashboard.watchlistWidgetEmptyDesc")}
                    />
                  ) : (
                    <div className="dash-watchlist-list">
                      {watchlistRows.map((row) => {
                        const cr = toNumber(row.changeRate);
                        const changeClass = row.changeRate == null ? "" : cr >= 0 ? "market-up" : "market-down";
                        const ChangeIcon = row.changeRate != null && cr < 0 ? ArrowDown : ArrowUp;
                        return (
                          <Link
                            key={row.id}
                            to={buildMarketDetailPath(row.symbol, row.instrumentType)}
                            className="dash-watchlist-row"
                          >
                            <div className="dash-watchlist-symbol-col">
                              <strong className="dash-watchlist-code">{row.code}</strong>
                              {row.secondLine ? <span className="dash-watchlist-name">{row.secondLine}</span> : null}
                            </div>
                            <div className="dash-watchlist-price-col">
                              <span className="dash-watchlist-price">{formatCurrency(row.price)}</span>
                              <span className={`dash-watchlist-change ${changeClass}`}>
                                {row.changeRate != null ? <ChangeIcon size={11} className="dash-watchlist-arrow" /> : null}
                                {formatMarketChange(row.changeRate)}
                              </span>
                            </div>
                          </Link>
                        );
                      })}
                    </div>
                  )}
                </section>
              ) : null}

              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <div>
                    <h3 className="dashboard-section-title">{t("dashboard.marketSummaryTitle")}</h3>

                    <div className="market-tabs">
                      <button
                        type="button"
                        className={marketTab === "gainers" ? "market-tab active" : "market-tab"}
                        onClick={() => setMarketTab("gainers")}
                      >
                        {t("dashboard.marketTabs.gainers")}
                      </button>

                      <button
                        type="button"
                        className={marketTab === "losers" ? "market-tab active" : "market-tab"}
                        onClick={() => setMarketTab("losers")}
                      >
                        {t("dashboard.marketTabs.losers")}
                      </button>
                    </div>
                  </div>
                  <Link to="/markets" className="panel-text-link">
                    {t("dashboard.allMarkets")}
                  </Link>
                </div>

                {sectionErrors.market && marketRows.length === 0 ? <ErrorMessage message={sectionErrors.market} /> : null}
                {!sectionErrors.market && marketRows.length === 0 ? (
                  <EmptyState title={t("dashboard.marketEmptyTitle")} description={t("dashboard.marketEmptyDescription")} />
                ) : null}
                {marketRows.length > 0 ? (
                  <div className="finance-market-table-wrap">
                    <table className="finance-market-table">
                      <thead>
                        <tr>
                          <th>{t("dashboard.table.instrument")}</th>
                          <th>{t("dashboard.table.source")}</th>
                          <th>{t("dashboard.table.lastPrice")}</th>
                          <th>{t("dashboard.table.dailyChange")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {marketRows.map((item) => (
                          <tr key={item.symbol}>
                            <td>
                              <Link to={buildMarketDetailPath(item.symbol, item.instrumentType)} className="finance-table-symbol">
                                <strong>{item.code || item.symbol}</strong>
                                <span>{item.displayName || item.code || item.symbol}</span>
                              </Link>
                            </td>
                            <td>{item.source || "-"}</td>
                            <td>{formatCurrency(item.price, item.currency || "TRY")}</td>
                            <td className={toNumber(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                              {formatMarketChange(item.changeRate)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : null}
              </section>


            </div>

            <aside className="finance-dashboard-side">
              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <div>
                    <h3>{t("dashboard.newsTitle")}</h3>
                  </div>
                  <Link to="/news" className="panel-text-link">
                    {t("dashboard.allNews")}
                  </Link>
                </div>

                {sectionErrors.news && newsItems.length === 0 ? <ErrorMessage message={sectionErrors.news} /> : null}
                {!sectionErrors.news && newsItems.length === 0 ? (
                  <EmptyState title={t("dashboard.newsEmptyTitle")} description={t("dashboard.newsEmptyDescription")} />
                ) : null}
                {newsItems.length > 0 ? (
                  <div className="news-rail-list compact dashboard-news-list">
                    {newsItems.map((item) => (
                      <Link key={item.id || item.externalId} to={`/news/${item.id}`} className="news-rail-item finance-news-link">
                        <div className="news-rail-badge">{(item.provider || item.source || "N").slice(0, 1)}</div>
                        <div>
                          <strong>{item.title || t("dashboard.untitledNews")}</strong>
                          <p>{item.provider || item.source || "-"}</p>
                          <span>{formatDateTime(item.publishedAt)}</span>
                        </div>
                      </Link>
                    ))}
                  </div>
                ) : null}
              </section>



            </aside>
          </section>
        </>
      ) : null}
    </div>
  );
}



function isTriggeredAlert(item) {
  return String(item?.status || "").toUpperCase() === "TRIGGERED" || Boolean(item?.triggeredAt);
}


function toNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function formatMarketChange(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }

  return `${numeric >= 0 ? "+" : ""}${numeric.toFixed(2)}%`;
}

function formatSignedCurrency(value) {
  if (!Number.isFinite(value)) {
    return "-";
  }

  return `${value >= 0 ? "+" : ""}${formatCurrency(value)}`;
}

const INSTRUMENT_TYPE_LABELS = {
  STOCK: "Hisse",
  FX: "Döviz",
  FUND: "Fon",
  CRYPTO: "Kripto",
  FUTURES: "Vadeli",
  BOND: "Tahvil",
  INDEX: "Endeks",
  COMMODITY: "Emtia",
};
