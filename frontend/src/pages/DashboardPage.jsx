import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { getUserAlerts } from "../api/alertApi";
import { buildMarketDetailPath, getMarketQuotes } from "../api/marketApi";
import { getNews } from "../api/newsApi";
import { getPortfolioDetails, getUserPortfolios } from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import { getUserWatchlist } from "../api/watchlistApi";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { formatCurrency, formatDateTime, formatNumber } from "../utils/formatters";
export default function DashboardPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const [loading, setLoading] = useState(true);
  const [marketQuotes, setMarketQuotes] = useState([]);
  const [newsItems, setNewsItems] = useState([]);
  const [watchlistItems, setWatchlistItems] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [portfolioSnapshots, setPortfolioSnapshots] = useState([]);
  const [sectionErrors, setSectionErrors] = useState({});

  useEffect(() => {
    let active = true;

    async function loadDashboard() {
      try {
        setLoading(true);
        setSectionErrors({});

        // Public veriler her zaman yüklenir (giriş gerektirmez)
        const publicRequests = [getMarketQuotes(), getNews({ size: 6 })];

        // Kullanıcıya özel veriler yalnızca giriş yapılmışsa yüklenir
        const userRequests = userId
          ? [getUserWatchlist(userId), getUserAlerts(userId), getUserPortfolios(userId)]
          : [Promise.resolve([]), Promise.resolve([]), Promise.resolve([])];

        const [marketResult, newsResult, watchlistResult, alertsResult, portfoliosResult] =
          await Promise.allSettled([...publicRequests, ...userRequests]);

        if (!active) {
          return;
        }

        const nextErrors = {};

        const resolvedMarketQuotes = marketResult.status === "fulfilled" ? marketResult.value ?? [] : [];
        const resolvedNews = newsResult.status === "fulfilled"
          ? (newsResult.value?.content ?? []).slice(0, 4)
          : [];
        const resolvedWatchlist = watchlistResult.status === "fulfilled" ? watchlistResult.value ?? [] : [];
        const resolvedAlerts = alertsResult.status === "fulfilled" ? alertsResult.value ?? [] : [];
        const portfolios = portfoliosResult.status === "fulfilled" ? portfoliosResult.value ?? [] : [];

        if (marketResult.status === "rejected") {
          nextErrors.market = extractErrorMessage(marketResult.reason, t("dashboard.marketError"));
        }
        if (newsResult.status === "rejected") {
          nextErrors.news = extractErrorMessage(newsResult.reason, t("dashboard.newsError"));
        }
        if (userId && watchlistResult.status === "rejected") {
          nextErrors.watchlist = extractErrorMessage(watchlistResult.reason, t("dashboard.watchlistError"));
        }
        if (userId && alertsResult.status === "rejected") {
          nextErrors.alerts = extractErrorMessage(alertsResult.reason, t("dashboard.alertsError"));
        }
        if (userId && portfoliosResult.status === "rejected") {
          nextErrors.portfolios = extractErrorMessage(portfoliosResult.reason, t("dashboard.portfoliosError"));
        }

        const detailResults = await Promise.allSettled(
          portfolios.map((portfolio) => getPortfolioDetails(portfolio.portfolioId)),
        );

        if (!active) {
          return;
        }

        const resolvedSnapshots = detailResults
          .filter((result) => result.status === "fulfilled" && result.value)
          .map((result) => result.value);

        if (detailResults.some((result) => result.status === "rejected")) {
          nextErrors.portfolioDetails = t("dashboard.portfolioDetailsError");
        }

        setMarketQuotes(resolvedMarketQuotes);
        setNewsItems(resolvedNews);
        setWatchlistItems(resolvedWatchlist);
        setAlerts(resolvedAlerts);
        setPortfolioSnapshots(resolvedSnapshots);
        setSectionErrors(nextErrors);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadDashboard();

    return () => {
      active = false;
    };
  }, [t, userId]);

  const quoteBySymbol = useMemo(() => {
    return new Map(
      marketQuotes
        .filter((quote) => quote?.symbol)
        .map((quote) => [normalizeCode(quote.symbol), quote]),
    );
  }, [marketQuotes]);

  const overviewCards = useMemo(() => {
    const portfolioValue = portfolioSnapshots.reduce(
      (sum, item) => sum + toNumber(item?.summary?.currentValue ?? item?.summary?.totalCurrentValue),
      0,
    );

    const activeAlertCount = alerts.filter((item) => String(item?.status || "").toUpperCase() === "ACTIVE").length || alerts.length;
    const dailyProfitLoss = computeDailyProfitLoss(portfolioSnapshots, quoteBySymbol);

    return [
      {
        title: t("dashboard.cards.portfolioValueTitle"),
        value: formatCurrency(portfolioValue),
        subtitle: t("dashboard.cards.portfolioValueSubtitle", { count: portfolioSnapshots.length }),
        trend: portfolioSnapshots.length ? t("dashboard.cards.portfolioValueTrend") : null,
        tone: "cool",
      },
      {
        title: t("dashboard.cards.dailyPnLTitle"),
        value: formatCurrency(dailyProfitLoss),
        subtitle: t("dashboard.cards.dailyPnLSubtitle"),
        trend: formatSignedCurrency(dailyProfitLoss),
        tone: dailyProfitLoss >= 0 ? "cool" : "warm",
      },
      {
        title: t("dashboard.cards.watchlistTitle"),
        value: formatNumber(watchlistItems.length, 0),
        subtitle: t("dashboard.cards.watchlistSubtitle"),
        trend: watchlistItems.length ? t("dashboard.cards.watchlistTrend") : null,
        tone: "neutral",
      },
      {
        title: t("dashboard.cards.alertsTitle"),
        value: formatNumber(activeAlertCount, 0),
        subtitle: t("dashboard.cards.alertsSubtitle"),
        trend: alerts.some(isTriggeredAlert) ? t("dashboard.cards.alertsTrend") : null,
        tone: alerts.some(isTriggeredAlert) ? "warm" : "neutral",
      },
    ];
  }, [alerts, portfolioSnapshots, quoteBySymbol, t, watchlistItems.length]);

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
                  <p className="summary-card-title">{card.title}</p>
                  {card.trend ? <span className="summary-chip">{card.trend}</span> : null}
                </div>
                <h3>{card.value}</h3>
                {card.subtitle ? <p className="summary-card-subtitle">{card.subtitle}</p> : null}
                <div
                  aria-hidden="true"
                  style={{
                    width: "100%",
                    height: 8,
                    marginTop: 28,
                    borderRadius: 999,
                    background:
                      "linear-gradient(90deg, rgba(37, 99, 235, 0.10), rgba(37, 99, 235, 0.34), rgba(37, 99, 235, 0.10))",
                  }}
                />
              </div>
            ))}
          </section>

          <section className="finance-dashboard-grid">
            <div className="finance-dashboard-main">
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

function computeDailyProfitLoss(portfolioSnapshots, quoteBySymbol) {
  return portfolioSnapshots.reduce((portfolioSum, snapshot) => {
    return portfolioSum + (snapshot?.holdings ?? []).reduce((holdingSum, holding) => {
      const symbol = normalizeCode(holding?.instrumentCode);
      const quote = quoteBySymbol.get(symbol);
      const changeRate = toNumber(quote?.changeRate);
      const currentValue = toNumber(holding?.currentValue);

      if (!Number.isFinite(changeRate) || !Number.isFinite(currentValue) || changeRate <= -100) {
        return holdingSum;
      }

      const ratio = changeRate / 100;
      const previousValue = currentValue / (1 + ratio);
      if (!Number.isFinite(previousValue)) {
        return holdingSum;
      }

      return holdingSum + (currentValue - previousValue);
    }, 0);
  }, 0);
}


function isTriggeredAlert(item) {
  return String(item?.status || "").toUpperCase() === "TRIGGERED" || Boolean(item?.triggeredAt);
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

