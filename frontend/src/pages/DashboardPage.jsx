import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { getUserAlerts } from "../api/alertApi";
import { getMarketQuotes } from "../api/marketApi";
import { getNews } from "../api/newsApi";
import { getPortfolioDetails, getUserPortfolios } from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import { getUserWatchlist } from "../api/watchlistApi";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { useTheme } from "../theme/ThemeContext";
import { formatCurrency, formatDateTime, formatNumber } from "../utils/formatters";

const CHART_COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];

export default function DashboardPage() {
  const { t } = useTranslation();
  const { user, userId } = useAuth();
  const { chartTheme } = useTheme();
  const [loading, setLoading] = useState(true);
  const [marketQuotes, setMarketQuotes] = useState([]);
  const [newsItems, setNewsItems] = useState([]);
  const [watchlistItems, setWatchlistItems] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [portfolioSnapshots, setPortfolioSnapshots] = useState([]);
  const [sectionErrors, setSectionErrors] = useState({});

  useEffect(() => {
    if (!userId) {
      setLoading(false);
      return;
    }

    let active = true;

    async function loadDashboard() {
      try {
        setLoading(true);
        setSectionErrors({});

        const [marketResult, newsResult, watchlistResult, alertsResult, portfoliosResult] = await Promise.allSettled([
          getMarketQuotes(),
          getNews({ size: 6 }),
          getUserWatchlist(userId),
          getUserAlerts(userId),
          getUserPortfolios(userId),
        ]);

        if (!active) {
          return;
        }

        const nextErrors = {};

        const resolvedMarketQuotes = marketResult.status === "fulfilled" ? marketResult.value ?? [] : [];
        const resolvedNews = newsResult.status === "fulfilled" ? newsResult.value?.content ?? [] : [];
        const resolvedWatchlist = watchlistResult.status === "fulfilled" ? watchlistResult.value ?? [] : [];
        const resolvedAlerts = alertsResult.status === "fulfilled" ? alertsResult.value ?? [] : [];
        const portfolios = portfoliosResult.status === "fulfilled" ? portfoliosResult.value ?? [] : [];

        if (marketResult.status === "rejected") {
          nextErrors.market = extractErrorMessage(marketResult.reason, t("dashboard.marketError"));
        }
        if (newsResult.status === "rejected") {
          nextErrors.news = extractErrorMessage(newsResult.reason, t("dashboard.newsError"));
        }
        if (watchlistResult.status === "rejected") {
          nextErrors.watchlist = extractErrorMessage(watchlistResult.reason, t("dashboard.watchlistError"));
        }
        if (alertsResult.status === "rejected") {
          nextErrors.alerts = extractErrorMessage(alertsResult.reason, t("dashboard.alertsError"));
        }
        if (portfoliosResult.status === "rejected") {
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

  const marketRows = useMemo(() => {
    return [...marketQuotes]
      .sort((left, right) => Math.abs(toNumber(right.changeRate)) - Math.abs(toNumber(left.changeRate)))
      .slice(0, 8);
  }, [marketQuotes]);

  const favoriteRows = useMemo(() => {
    return watchlistItems.slice(0, 6).map((item) => {
      const marketQuote = quoteBySymbol.get(normalizeCode(item.instrumentCode));
      return {
        symbol: item.instrumentCode,
        price: marketQuote?.price ?? item.currentPrice,
        changeRate: marketQuote?.changeRate,
        source: marketQuote?.source ?? item.source,
        updatedAt: marketQuote?.fetchedAt ?? item.lastUpdated,
      };
    });
  }, [quoteBySymbol, watchlistItems]);

  const allocationData = useMemo(() => {
    const totals = new Map();

    portfolioSnapshots.forEach((snapshot) => {
      (snapshot?.holdings ?? []).forEach((holding) => {
        const key = holding?.instrumentCode || "N/A";
        totals.set(key, (totals.get(key) || 0) + toNumber(holding?.currentValue));
      });
    });

    return [...totals.entries()]
      .map(([instrumentCode, value]) => ({ instrumentCode, value }))
      .filter((item) => item.value > 0)
      .sort((left, right) => right.value - left.value)
      .slice(0, 8);
  }, [portfolioSnapshots]);

  const riskWarnings = useMemo(() => {
    const items = [];
    const missingPriceCount = portfolioSnapshots.reduce((sum, item) => sum + Number(item?.summary?.missingPriceCount || 0), 0);
    if (missingPriceCount > 0) {
      items.push({
        title: t("dashboard.risk.missingPriceTitle"),
        description: t("dashboard.risk.missingPriceDescription", { count: missingPriceCount }),
      });
    }

    const sharpMovers = favoriteRows.filter((item) => toNumber(item.changeRate) <= -3).slice(0, 2);
    sharpMovers.forEach((item) => {
      items.push({
        title: t("dashboard.risk.fastPullbackTitle", { symbol: item.symbol }),
        description: t("dashboard.risk.fastPullbackDescription", { change: formatMarketChange(item.changeRate) }),
      });
    });

    const nearAlerts = alerts
      .filter((item) => !isTriggeredAlert(item) && isAlertNearTrigger(item))
      .slice(0, 2);
    nearAlerts.forEach((item) => {
      items.push({
        title: t("dashboard.risk.nearAlertTitle", { symbol: item.instrumentCode }),
        description: t("dashboard.risk.nearAlertDescription", {
          target: formatCurrency(item.targetPrice),
          price: formatCurrency(item.currentPrice),
        }),
      });
    });

    return items.slice(0, 4);
  }, [alerts, favoriteRows, portfolioSnapshots, t]);

  const notifications = useMemo(() => {
    return alerts
      .filter(isTriggeredAlert)
      .slice(0, 4)
      .map((item) => ({
        id: item.id,
        title: t("dashboard.triggeredAlertTitle", { symbol: item.instrumentCode }),
        description: t("dashboard.triggeredAlertDescription", {
          condition: item.conditionType,
          target: formatCurrency(item.targetPrice),
        }),
        timestamp: item.triggeredAt ?? item.lastUpdated,
      }));
  }, [alerts, t]);

  return (
    <div className="dashboard-stack finance-dashboard-shell">
      <PageHeader
        eyebrow={t("dashboard.eyebrow")}
        title={t("dashboard.title", { suffix: user?.fullName ? `, ${user.fullName}` : "" })}
        description={t("dashboard.description")}
      />

      {loading ? <LoadingSpinner label={t("dashboard.loading")} /> : null}

      {!loading ? (
        <>
          <section className="ticker-grid finance-dashboard-kpis">
            {overviewCards.map((card) => (
              <SummaryCard
                key={card.title}
                title={card.title}
                value={card.value}
                subtitle={card.subtitle}
                trend={card.trend}
                tone={card.tone}
              />
            ))}
          </section>

          <section className="finance-dashboard-grid">
            <div className="finance-dashboard-main">
              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("dashboard.marketSummaryEyebrow")}</p>
                    <h3>{t("dashboard.marketSummaryTitle")}</h3>
                  </div>
                  <Link to="/markets" className="secondary-button">
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
                              <Link to={`/markets/${encodeURIComponent(item.symbol)}`} className="finance-table-symbol">
                                <strong>{item.symbol}</strong>
                                <span>{item.displayName || item.symbol}</span>
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

              <div className="finance-dashboard-subgrid">
                <section className="panel-surface finance-dashboard-panel">
                  <div className="panel-head">
                    <div>
                      <p className="eyebrow">{t("dashboard.favoritesEyebrow")}</p>
                      <h3>{t("dashboard.favoritesTitle")}</h3>
                    </div>
                    <Link to="/markets" className="secondary-button">
                      {t("dashboard.goMarkets")}
                    </Link>
                  </div>

                  {sectionErrors.watchlist && favoriteRows.length === 0 ? <ErrorMessage message={sectionErrors.watchlist} /> : null}
                  {!sectionErrors.watchlist && favoriteRows.length === 0 ? (
                    <EmptyState
                      title={t("dashboard.favoritesEmptyTitle")}
                      description={t("dashboard.favoritesEmptyDescription")}
                    />
                  ) : null}
                  {favoriteRows.length > 0 ? (
                    <div className="market-list">
                      {favoriteRows.map((item) => (
                        <div key={item.symbol} className="market-list-item">
                          <div className="market-list-main">
                            <strong>{item.symbol}</strong>
                            <p>{item.source || t("dashboard.waitingSource")}</p>
                          </div>
                          <div className="market-list-side">
                            <strong>{formatNumber(item.price)}</strong>
                            <span className={toNumber(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                              {formatMarketChange(item.changeRate)}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </section>

                <section className="panel-surface finance-dashboard-panel">
                  <div className="panel-head">
                    <div>
                      <p className="eyebrow">{t("dashboard.allocationEyebrow")}</p>
                      <h3>{t("dashboard.allocationTitle")}</h3>
                    </div>
                    <Link to="/portfolio" className="secondary-button">
                      {t("dashboard.portfolios")}
                    </Link>
                  </div>

                  {(sectionErrors.portfolios || sectionErrors.portfolioDetails) && allocationData.length === 0 ? (
                    <ErrorMessage message={sectionErrors.portfolios || sectionErrors.portfolioDetails} />
                  ) : null}
                  {allocationData.length === 0 ? (
                    <EmptyState title={t("dashboard.allocationEmptyTitle")} description={t("dashboard.allocationEmptyDescription")} />
                  ) : null}
                  {allocationData.length > 0 ? (
                    <div className="finance-allocation-shell">
                      <div className="finance-allocation-chart">
                        <ResponsiveContainer>
                          <PieChart>
                            <Pie data={allocationData} dataKey="value" nameKey="instrumentCode" outerRadius={92} innerRadius={48}>
                              {allocationData.map((entry, index) => (
                                <Cell key={entry.instrumentCode} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                              ))}
                            </Pie>
                            <Tooltip
                              formatter={(value) => formatCurrency(value)}
                              contentStyle={chartTheme.tooltipContentStyle}
                              itemStyle={chartTheme.tooltipItemStyle}
                              labelStyle={chartTheme.tooltipLabelStyle}
                            />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                      <div className="portfolio-allocation-list">
                        {allocationData.map((entry, index) => (
                          <div key={entry.instrumentCode} className="portfolio-allocation-item">
                            <div className="portfolio-allocation-label">
                              <span className="portfolio-color-dot" style={{ backgroundColor: CHART_COLORS[index % CHART_COLORS.length] }} />
                              <strong>{entry.instrumentCode}</strong>
                            </div>
                            <span className="muted">{formatCurrency(entry.value)}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </section>
              </div>
            </div>

            <aside className="finance-dashboard-side">
              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <div>
                      <p className="eyebrow">{t("dashboard.newsEyebrow")}</p>
                      <h3>{t("dashboard.newsTitle")}</h3>
                  </div>
                  <Link to="/news" className="secondary-button">
                    {t("dashboard.allNews")}
                  </Link>
                </div>

                {sectionErrors.news && newsItems.length === 0 ? <ErrorMessage message={sectionErrors.news} /> : null}
                {!sectionErrors.news && newsItems.length === 0 ? (
                  <EmptyState title={t("dashboard.newsEmptyTitle")} description={t("dashboard.newsEmptyDescription")} />
                ) : null}
                {newsItems.length > 0 ? (
                  <div className="news-rail-list compact">
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

              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("dashboard.riskEyebrow")}</p>
                    <h3>{t("dashboard.riskTitle")}</h3>
                  </div>
                </div>

                {riskWarnings.length === 0 ? (
                  <EmptyState title={t("dashboard.riskEmptyTitle")} description={t("dashboard.riskEmptyDescription")} />
                ) : (
                  <div className="finance-warning-list">
                    {riskWarnings.map((item, index) => (
                      <article key={`${item.title}-${index}`} className="finance-warning-card">
                        <strong>{item.title}</strong>
                        <p>{item.description}</p>
                      </article>
                    ))}
                  </div>
                )}
              </section>

              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("dashboard.notificationsEyebrow")}</p>
                    <h3>{t("dashboard.notificationsTitle")}</h3>
                  </div>
                  <Link to="/alerts" className="secondary-button">
                    {t("dashboard.notificationsLink")}
                  </Link>
                </div>

                {sectionErrors.alerts && notifications.length === 0 ? <ErrorMessage message={sectionErrors.alerts} /> : null}
                {!sectionErrors.alerts && notifications.length === 0 ? (
                  <EmptyState title={t("dashboard.notificationsEmptyTitle")} description={t("dashboard.notificationsEmptyDescription")} />
                ) : null}
                {notifications.length > 0 ? (
                  <div className="finance-notification-list">
                    {notifications.map((item) => (
                      <article key={item.id} className="finance-notification-card">
                        <strong>{item.title}</strong>
                        <p>{item.description}</p>
                        <span>{formatDateTime(item.timestamp)}</span>
                      </article>
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

function isAlertNearTrigger(item) {
  const currentPrice = toNumber(item?.currentPrice);
  const targetPrice = toNumber(item?.targetPrice);
  if (!Number.isFinite(currentPrice) || !Number.isFinite(targetPrice) || targetPrice <= 0) {
    return false;
  }

  return Math.abs(currentPrice - targetPrice) / targetPrice <= 0.02;
}

function isTriggeredAlert(item) {
  return String(item?.status || "").toUpperCase() === "TRIGGERED" || Boolean(item?.triggeredAt);
}

function normalizeCode(value) {
  return value == null ? "" : String(value).replace(/[^A-Za-z0-9]/g, "").toUpperCase();
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
