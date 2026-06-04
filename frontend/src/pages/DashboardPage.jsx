import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Bell, Eye, Sparkles, TrendingDown, TrendingUp, Wallet } from "lucide-react";
import { Link } from "react-router-dom";
import { useQueries } from "@tanstack/react-query";
import { buildMarketDetailPath } from "../api/marketApi";
import { getPortfolioDetails } from "../api/portfolioApi";
import { portfolioKeys } from "../api/queryKeys";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import AuthRequiredModal from "../components/common/AuthRequiredModal";
import { useUserAlerts } from "../hooks/useAlertQueries";
import { useMarketQuotes } from "../hooks/useMarketQueries";
import { useNewsList } from "../hooks/useNewsQueries";
import { useUserPortfolios } from "../hooks/usePortfolioQueries";
import { useUserWatchlist } from "../hooks/useWatchlistQueries";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";
import { formatInstrumentCode } from "../utils/instrumentUtils";
import { buildNewsPlaceholderLabel } from "../components/news/newsCardUtils";
import GuestLockOverlay from "../components/common/GuestLockOverlay";

export default function DashboardPage() {
  const { t } = useTranslation();
  const { userId, isAuthenticated, login } = useAuth();

  const { data: marketQuotes = [], isLoading: quotesLoading, error: quotesError } = useMarketQuotes();
  const { data: newsPage, isLoading: newsLoading, error: newsError } = useNewsList(
    { size: 8, isKapDisclosure: false },
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
  const newsItems = (newsPage?.content ?? []).slice(0, 8);
  const portfolioSnapshots = portfolioDetailQueries
    .filter((q) => q.status === "success" && q.data)
    .map((q) => q.data);

  const [isPortfolioWidgetExpanded, setPortfolioWidgetExpanded] = useState(false);
  const [selectedWidgetPortfolioId, setSelectedWidgetPortfolioId] = useState(null);
  const [isAiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [isWatchlistPopoverOpen, setWatchlistPopoverOpen] = useState(false);
  const [isAuthRequiredModalOpen, setAuthRequiredModalOpen] = useState(false);
  const [marketCategoryFilter, setMarketCategoryFilter] = useState("all");
  const watchlistPopoverRef = useRef(null);

  const effectiveWidgetPortfolioId = selectedWidgetPortfolioId ?? portfolios[0]?.portfolioId ?? null;
  const selectedWidgetSnapshot = portfolioSnapshots.find((s) => s.portfolioId === effectiveWidgetPortfolioId) ?? null;
  const selectedWidgetTotalValue = toNumber(
    selectedWidgetSnapshot?.summary?.currentValue ?? selectedWidgetSnapshot?.summary?.totalCurrentValue,
  );
  const widgetHoldings = selectedWidgetSnapshot?.holdings ?? [];

  const sectionErrors = {
    market: quotesError ? t("dashboard.marketError") : null,
    news: newsError ? t("dashboard.newsError") : null,
    watchlist: watchlistError && userId ? t("dashboard.watchlistError") : null,
    alerts: alertsError && userId ? t("dashboard.alertsError") : null,
    portfolios: portfoliosError && userId ? t("dashboard.portfoliosError") : null,
  };

  const activeAlerts = useMemo(
    () => alerts.filter((a) => String(a?.status || "").toUpperCase() === "ACTIVE"),
    [alerts],
  );
  const activeAlertCount = activeAlerts.length || alerts.length;
  const hasTriggeredAlert = useMemo(() => alerts.some(isTriggeredAlert), [alerts]);
  const alertTone = hasTriggeredAlert ? "warm" : "neutral";

  const nearestAlert = useMemo(() => {
    if (!activeAlerts.length) return null;
    const norm = (s) => (s ?? "").replace(/[^A-Za-z0-9]/g, "").toUpperCase();
    const quoteMap = new Map();

    for (const q of marketQuotes) {
      quoteMap.set(norm(q.symbol), q);
      if (q.code) quoteMap.set(norm(q.code), q);
    }

    let nearest = null;
    let bestGap = Infinity;

    for (const alert of activeAlerts) {
      const quote = quoteMap.get(norm(alert.instrumentCode));
      const cur = toNumber(quote?.sellRate ?? quote?.price ?? alert.currentPrice ?? 0);
      const target = toNumber(alert.targetPrice);

      if (cur > 0 && target > 0) {
        const gap = alert.conditionType === "ABOVE" ? target - cur : cur - target;
        if (gap >= 0 && gap < bestGap) {
          bestGap = gap;
          nearest = alert;
        }
      }

      if (!nearest) nearest = alert;
    }

    return nearest;
  }, [activeAlerts, marketQuotes]);

  const portfolioValue = portfolioSnapshots.reduce(
    (sum, item) => sum + toNumber(item?.summary?.currentValue ?? item?.summary?.totalCurrentValue),
    0,
  );
  const hasPortfolio = portfolioSnapshots.length > 0 && portfolioValue > 0;
  const dailyProfitLoss = portfolioSnapshots.reduce(
    (sum, snapshot) => sum + toNumber(snapshot?.summary?.dailyProfitLoss),
    0,
  );
  const previousValue = portfolioValue - dailyProfitLoss;
  const dailyProfitLossPercent = previousValue > 0 ? (dailyProfitLoss / previousValue) * 100 : null;
  const dailyProfitLossPercentLabel = dailyProfitLossPercent != null
    ? `${dailyProfitLossPercent >= 0 ? "+" : ""}${dailyProfitLossPercent.toFixed(2)}%`
    : null;
  const pnlTone = dailyProfitLoss > 0 ? "positive" : dailyProfitLoss < 0 ? "negative" : "neutral";

  const overviewCards = [
    {
      key: "portfolio-value",
      title: t("dashboard.cards.portfolioValueTitle"),
      value: formatCurrency(portfolioValue),
      subtitle: hasPortfolio ? t("dashboard.cards.portfolioValueSubtitle", { count: portfolioSnapshots.length }) : null,
      trend: hasPortfolio ? t("dashboard.cards.portfolioValueTrend") : null,
      tone: "cool",
      icon: <Wallet size={18} />,
      isEmpty: !hasPortfolio,
    },
    {
      key: "daily-pnl",
      title: t("dashboard.cards.dailyPnLTitle"),
      value: formatCurrency(dailyProfitLoss),
      subtitle: dailyProfitLossPercentLabel ? null : t("dashboard.cards.dailyPnLSubtitle"),
      trend: dailyProfitLossPercentLabel,
      tone: pnlTone,
      valueClassName: dailyProfitLoss > 0 ? "market-up" : dailyProfitLoss < 0 ? "market-down" : "",
      trendClassName: dailyProfitLossPercent > 0 ? "market-up" : dailyProfitLossPercent < 0 ? "market-down" : "",
      icon: dailyProfitLoss < 0 ? <TrendingDown size={18} /> : <TrendingUp size={18} />,
    },
    {
      key: "watchlist",
      title: t("dashboard.cards.watchlistTitle"),
      value: formatNumber(watchlistItems.length, 0),
      subtitle: t("dashboard.cards.watchlistSubtitle"),
      trend: watchlistItems.length ? t("dashboard.cards.watchlistTrend") : null,
      tone: "neutral",
      icon: <Eye size={18} />,
    },
  ];

  const overviewCardMap = new Map(overviewCards.map((card) => [card.key, card]));
  const orderedOverviewCardKeys = ["watchlist", "alerts", "portfolio-value", "daily-pnl"];

  const watchlistRows = (() => {
    if (!watchlistItems.length) return [];
    const norm = (s) => (s ?? "").replace(/[^A-Za-z0-9]/g, "").toUpperCase();
    const quoteMap = new Map();

    for (const q of marketQuotes) {
      quoteMap.set(norm(q.symbol), q);
      if (q.code) quoteMap.set(norm(q.code), q);
    }

    return watchlistItems.slice(0, 8).map((item) => {
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
  })();

  const [marketTab, setMarketTab] = useState("gainers");
  const marketRows = useMemo(() => {
    return [...marketQuotes]
      .filter((item) => matchesDashboardMarketCategory(item, marketCategoryFilter))
      .filter((item) => Number.isFinite(Number(item.changeRate)))
      .sort((left, right) => {
        if (marketTab === "losers") {
          return toNumber(left.changeRate) - toNumber(right.changeRate);
        }

        return toNumber(right.changeRate) - toNumber(left.changeRate);
      })
      .slice(0, 5);
  }, [marketQuotes, marketCategoryFilter, marketTab]);

  const aiSummary = useMemo(() => {
    const changedRows = marketQuotes.filter((item) => Number.isFinite(Number(item?.changeRate)));
    if (!changedRows.length) return null;
    const avg = changedRows.reduce((sum, item) => sum + Number(item.changeRate), 0) / changedRows.length;
    const mood = avg > 0.4 ? "pozitif" : avg < -0.4 ? "zayif" : "dengeli";
    const topGainer = [...changedRows].sort((a, b) => Number(b.changeRate) - Number(a.changeRate))[0];
    const topLoser = [...changedRows].sort((a, b) => Number(a.changeRate) - Number(b.changeRate))[0];
    return { mood, avg, topGainer, topLoser };
  }, [marketQuotes]);

  useEffect(() => {
    if (!isWatchlistPopoverOpen) return undefined;

    function handlePointerDown(event) {
      if (watchlistPopoverRef.current && !watchlistPopoverRef.current.contains(event.target)) {
        setWatchlistPopoverOpen(false);
      }
    }

    function handleEscape(event) {
      if (event.key === "Escape") {
        setWatchlistPopoverOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("touchstart", handlePointerDown);
    document.addEventListener("keydown", handleEscape);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("touchstart", handlePointerDown);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [isWatchlistPopoverOpen]);

  function isTouchLayout() {
    if (typeof window === "undefined" || !window.matchMedia) return false;
    return window.matchMedia("(hover: none), (pointer: coarse), (max-width: 960px)").matches;
  }

  function handleWatchlistCardClick(event) {
    if (!isTouchLayout()) return;
    if (!isAuthenticated) {
      event.preventDefault();
      login();
      return;
    }
    event.preventDefault();
    setWatchlistPopoverOpen((prev) => !prev);
  }

  function handleWatchlistMouseEnter() {
    if (!isAuthenticated || isTouchLayout()) return;
    setWatchlistPopoverOpen(true);
  }

  function handleWatchlistMouseLeave() {
    if (isTouchLayout()) return;
    setWatchlistPopoverOpen(false);
  }

  useEffect(() => {
    if (!isAiDrawerOpen) return undefined;
    function handleKey(event) {
      if (event.key === "Escape") {
        setAiDrawerOpen(false);
      }
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [isAiDrawerOpen]);

  return (
    <div className="dashboard-stack finance-dashboard-shell dashboard-page">
      {loading ? <LoadingSpinner label={t("dashboard.loading")} /> : null}

      {!loading ? (
        <>
          <section className="ticker-grid finance-dashboard-kpis">
            {orderedOverviewCardKeys.map((cardKey) => {
              if (cardKey === "alerts") {
                return isAuthenticated ? (
                  <Link key="alerts" to="/alerts" className={`summary-card summary-card-${alertTone} summary-card-alerts alerts-kpi-card`}>
                    <div className="summary-card-top">
                      <div className="summary-card-title-row">
                        <span className={`summary-card-icon summary-card-icon--${alertTone}`}><Bell size={18} /></span>
                        <p className="summary-card-title">{t("dashboard.cards.alertsTitle")}</p>
                      </div>
                      {hasTriggeredAlert ? <span className="summary-chip">{t("dashboard.cards.alertsTrend")}</span> : null}
                    </div>
                    <h3>{formatNumber(activeAlertCount, 0)}</h3>
                    {activeAlertCount === 0 ? (
                      <span className="summary-card-cta">{t("dashboard.cards.addAlert")}</span>
                    ) : nearestAlert ? (
                      <p className="summary-card-subtitle alerts-kpi-nearest">
                        {formatInstrumentCode(nearestAlert.instrumentCode)}
                        {" "}{nearestAlert.conditionType === "ABOVE" ? ">" : "<"}
                        {" "}{formatCurrency(nearestAlert.targetPrice)}
                      </p>
                    ) : (
                      <p className="summary-card-subtitle">{t("dashboard.cards.alertsSubtitle")}</p>
                    )}
                  </Link>
                ) : (
                  <GuestLockOverlay
                    key="alerts"
                    compact
                    className="dashboard-kpi-guest-lock"
                    badgeClassName="dashboard-kpi-lock-badge"
                    onRequestAuth={() => setAuthRequiredModalOpen(true)}
                  >
                    <div className="summary-card summary-card-neutral">
                      <div className="summary-card-top">
                        <div className="summary-card-title-row">
                          <span className="summary-card-icon summary-card-icon--neutral"><Bell size={18} /></span>
                          <p className="summary-card-title">{t("dashboard.cards.alertsTitle")}</p>
                        </div>
                      </div>
                      <h3>-</h3>
                      <p className="summary-card-subtitle">{t("dashboard.cards.alertsSubtitle")}</p>
                    </div>
                  </GuestLockOverlay>
                );
              }

              const card = overviewCardMap.get(cardKey);
              if (!card) {
                return null;
              }

              return card.key === "watchlist" ? (
                <div
                  key={card.key}
                  ref={watchlistPopoverRef}
                  className={`dashboard-kpi-popover-shell${isWatchlistPopoverOpen ? " is-open" : ""}`}
                  onMouseEnter={handleWatchlistMouseEnter}
                  onMouseLeave={handleWatchlistMouseLeave}
                >
                  {isAuthenticated ? (
                    <button
                      type="button"
                      className={`summary-card summary-card-${card.tone} summary-card-${card.key} summary-card-interactive dashboard-watchlist-kpi-card`}
                      aria-haspopup="dialog"
                      aria-expanded={isAuthenticated ? isWatchlistPopoverOpen : undefined}
                      onClick={handleWatchlistCardClick}
                    >
                      <div className="summary-card-top">
                        <div className="summary-card-title-row">
                          {card.icon ? <span className={`summary-card-icon summary-card-icon--${card.tone}`}>{card.icon}</span> : null}
                          <p className="summary-card-title">{card.title}</p>
                        </div>
                        {card.trend ? <span className="summary-chip">{card.trend}</span> : null}
                      </div>
                      <h3>{card.value}</h3>
                      {card.subtitle ? <p className="summary-card-subtitle">{card.subtitle}</p> : null}
                    </button>
                  ) : (
                    <GuestLockOverlay
                      compact
                      className="dashboard-kpi-guest-lock"
                      badgeClassName="dashboard-kpi-lock-badge"
                      onRequestAuth={() => setAuthRequiredModalOpen(true)}
                    >
                      <button
                        type="button"
                        className={`summary-card summary-card-${card.tone} summary-card-${card.key} summary-card-interactive dashboard-watchlist-kpi-card`}
                        aria-hidden="true"
                        tabIndex={-1}
                      >
                        <div className="summary-card-top">
                          <div className="summary-card-title-row">
                            {card.icon ? <span className={`summary-card-icon summary-card-icon--${card.tone}`}>{card.icon}</span> : null}
                            <p className="summary-card-title">{card.title}</p>
                          </div>
                          {card.trend ? <span className="summary-chip">{card.trend}</span> : null}
                        </div>
                        <h3>{card.value}</h3>
                        {card.subtitle ? <p className="summary-card-subtitle">{card.subtitle}</p> : null}
                      </button>
                    </GuestLockOverlay>
                  )}

                  {isAuthenticated && isWatchlistPopoverOpen ? (
                    <div className="dashboard-watchlist-popover" role="dialog" aria-label={t("dashboard.watchlistWidgetTitle")}>
                      <div className="dashboard-watchlist-popover-head">
                        <span>{t("dashboard.watchlistWidgetTitle")}</span>
                        <Link
                          to="/markets"
                          state={{ category: "FAVORITES" }}
                          className="dashboard-watchlist-popover-link"
                          onClick={() => setWatchlistPopoverOpen(false)}
                        >
                          {t("İzleme listenizi görüntüleyin")}
                        </Link>
                      </div>

                      {sectionErrors.watchlist ? <ErrorMessage message={sectionErrors.watchlist} /> : null}

                      {watchlistRows.length === 0 && !sectionErrors.watchlist ? (
                        <div className="dashboard-watchlist-popover-empty">{t("dashboard.watchlistWidgetEmptyTitle")}</div>
                      ) : null}

                      {watchlistRows.length > 0 ? (
                        <div className="dashboard-watchlist-popover-list">
                          {watchlistRows.map((row) => {
                            const changeClass = row.changeRate == null
                              ? ""
                              : toNumber(row.changeRate) >= 0
                                ? "market-up"
                                : "market-down";

                            return (
                              <Link
                                key={row.id}
                                to={buildMarketDetailPath(row.symbol, row.instrumentType)}
                                className="dashboard-watchlist-popover-row"
                                onClick={() => setWatchlistPopoverOpen(false)}
                              >
                                <div className="dashboard-watchlist-popover-symbol">
                                  <strong>{row.code}</strong>
                                  {row.secondLine ? <span>{row.secondLine}</span> : null}
                                </div>
                                <div className="dashboard-watchlist-popover-metrics">
                                  <span>{formatCurrency(row.price)}</span>
                                  <span className={changeClass}>{formatMarketChange(row.changeRate)}</span>
                                </div>
                              </Link>
                            );
                          })}
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              ) : (
                <div key={card.key} className={`summary-card summary-card-${card.tone} summary-card-${card.key} ${card.key === "portfolio-value" || card.key === "daily-pnl" ? "summary-card--primary" : "summary-card--secondary"}`}>
                  <div className="summary-card-top">
                    <div className="summary-card-title-row">
                      {card.icon ? <span className={`summary-card-icon summary-card-icon--${card.tone}`}>{card.icon}</span> : null}
                      <p className="summary-card-title">{card.title}</p>
                    </div>
                    {card.trend ? (
                      <span className={`summary-chip${card.trendClassName ? ` ${card.trendClassName}` : ""}`}>{card.trend}</span>
                    ) : null}
                  </div>
                  <h3 className={card.valueClassName}>{card.value}</h3>
                  {card.isEmpty ? (
                    <Link to="/portfolio" className="summary-card-cta">{t("dashboard.cards.addPortfolio")}</Link>
                  ) : card.subtitle ? (
                    <p className="summary-card-subtitle">{card.subtitle}</p>
                  ) : null}
                </div>
              )
            })}
          </section>

          {/* AI trigger removed from main area — moved to right column */}

          <section className="finance-dashboard-grid">
            <div className="finance-dashboard-main">
              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <div className="dashboard-market-heading">
                    <h3 className="dashboard-section-title">{t("dashboard.marketSummaryTitle")}</h3>
                    <div className="market-tabs">
                      <button
                        type="button"
                        className={`market-tab dashboard-market-tab ${marketTab === "gainers" ? "active" : ""}`}
                        onClick={() => setMarketTab("gainers")}
                      >
                        {t("dashboard.marketTabs.gainers")}
                      </button>
                      <button
                        type="button"
                        className={`market-tab dashboard-market-tab ${marketTab === "losers" ? "active" : ""}`}
                        onClick={() => setMarketTab("losers")}
                      >
                        {t("dashboard.marketTabs.losers")}
                      </button>
                    </div>
                    <div className="dashboard-market-filter-row" role="tablist" aria-label={t("dashboard.marketCategoryAria")}>
                      {DASHBOARD_MARKET_CATEGORY_FILTERS.map((filter) => (
                        <button
                          key={filter.key}
                          type="button"
                          role="tab"
                          aria-selected={marketCategoryFilter === filter.key}
                          className={marketCategoryFilter === filter.key ? "dashboard-market-filter active" : "dashboard-market-filter"}
                          onClick={() => setMarketCategoryFilter(filter.key)}
                        >
                          {t(filter.labelKey)}
                        </button>
                      ))}
                    </div>
                  </div>
                  <Link to="/markets" className="panel-text-link">
                    {t("dashboard.allMarkets")}
                  </Link>
                </div>

                {sectionErrors.market && marketRows.length === 0 ? <ErrorMessage message={sectionErrors.market} /> : null}
                {!sectionErrors.market && marketRows.length === 0 && marketQuotes.length === 0 ? (
                  <EmptyState title={t("dashboard.marketEmptyTitle")} description={t("dashboard.marketEmptyDescription")} />
                ) : null}
                {!sectionErrors.market && marketRows.length === 0 && marketQuotes.length > 0 ? (
                  <EmptyState title={t("dashboard.marketFilteredEmptyTitle")} description={t("dashboard.marketFilteredEmptyDescription")} />
                ) : null}
                {marketRows.length > 0 && (
                  <>
                    {/* Mover cards removed — display table only */}

                    {/** portfolio summary moved out to its own panel below the market panel **/}

                    <div className="finance-market-table-wrap">
                    <table className="finance-market-table">
                      <thead>
                        <tr>
                          <th>{t("dashboard.table.instrument")}</th>
                          <th className="col-right">{t("dashboard.table.lastPrice")}</th>
                          <th className="col-right">{t("dashboard.table.dailyChange")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {marketRows.map((item) => {
                          const label = getDashboardInstrumentLabel(item);
                          const subLabel = getDashboardInstrumentSubLabel(item);

                          return (
                            <tr key={item.symbol}>
                              <td>
                                <Link to={buildMarketDetailPath(item.symbol, item.instrumentType)} className="finance-table-symbol">
                                  <strong>{label}</strong>
                                  {subLabel ? <span>{subLabel}</span> : null}
                                </Link>
                              </td>
                              <td className="col-right">{formatCurrency(item.price, item.currency || "TRY")}</td>
                              <td className={`col-right ${toNumber(item.changeRate) >= 0 ? "market-up" : "market-down"}`}>
                                {formatMarketChange(item.changeRate)}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                  </>
                )}
              </section>

              {/* Portfolio panel: separate card with holdings */}
              <GuestLockOverlay
                compact
                className="dashboard-portfolio-guest-lock"
                badgeClassName="dash-portfolio-lock-badge"
                onRequestAuth={() => setAuthRequiredModalOpen(true)}
              >
                <section className="panel-surface finance-dashboard-panel dashboard-portfolio-panel">
                  <div className="panel-head">
                    <div>
                      <h3 className="dashboard-section-title">Portföy Özeti</h3>
                    </div>
                    <Link to="/portfolio" className="panel-text-link">{t("Portföyünü görüntüle") ?? "Tümünü Gör"}</Link>
                  </div>

                  <div className="panel-body" style={{ padding: '12px 16px' }}>
                    {hasPortfolio ? (
                      <div className="portfolio-holdings-list">
                        {(widgetHoldings && widgetHoldings.length ? widgetHoldings : (portfolioSnapshots[0]?.holdings || [])).slice(0,5).map((h, idx) => {
                          const label = getDashboardPortfolioHoldingLabel(h);
                          const subLabel = getDashboardPortfolioHoldingSubLabel(h, label);

                          return (
                            <div key={idx} className="portfolio-holding-row" style={{display:'flex',justifyContent:'space-between',gap:12,padding:'8px 0',borderBottom: idx < 4 ? '1px solid var(--panel-border)' : 'none'}}>
                              <div style={{minWidth:0}}>
                                <strong style={{display:'block',whiteSpace:'nowrap',overflow:'hidden',textOverflow:'ellipsis'}}>{label}</strong>
                                {subLabel ? <span style={{fontSize:'0.8rem',color:'var(--text-soft)'}}>{subLabel}</span> : null}
                              </div>
                              <div style={{textAlign:'right'}}>
                                <div style={{fontWeight:700}}>{formatCurrency(h?.currentValue ?? h?.marketValue ?? 0)}</div>
                                <div className={toNumber(h?.changeRate) >= 0 ? 'market-up' : 'market-down'} style={{fontSize:'0.85rem'}}>{formatMarketChange(h?.changeRate)}</div>
                              </div>
                            </div>
                          );
                        })}
                        {(!widgetHoldings || widgetHoldings.length === 0) && !(portfolioSnapshots[0]?.holdings && portfolioSnapshots[0].holdings.length) ? (
                          <div className="portfolio-empty" style={{padding:'8px 0'}}>{t('dashboard.portfolioEmpty') ?? 'Portföyünüz boş'}</div>
                        ) : null}
                      </div>
                    ) : (
                      <div style={{padding:'8px 0'}}>{t('dashboard.cards.addPortfolio')}</div>
                    )}
                  </div>
                </section>
              </GuestLockOverlay>
            </div>

            <aside className="finance-dashboard-side">
              {/* AI mini-card placed above news panel (replaces duplicate portfolio widget) */}
              <section className="panel-surface finance-dashboard-panel dash-ai-mini-card">
                <div
                  className="dash-ai-mini-header"
                  role="button"
                  tabIndex={0}
                  onClick={() => setAiDrawerOpen(true)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      setAiDrawerOpen(true);
                    }
                  }}
                >
                  <div className="dash-ai-mini-copy">
                    <span className="dash-ai-mini-icon"><Sparkles size={16} aria-hidden="true" /></span>
                    <div className="dash-ai-mini-text">
                      <span className="dash-ai-mini-title">AI Piyasa Özeti</span>
                      <span className="dash-ai-mini-sub">Günün piyasa görünümünü incele</span>
                    </div>
                  </div>

                  <div className="dash-ai-mini-action">
                    <button
                      type="button"
                      className="dashboard-ai-open-btn"
                      onClick={(e) => {
                        e.stopPropagation();
                        setAiDrawerOpen(true);
                      }}
                    >
                      Detayı Aç
                    </button>
                  </div>
                </div>
              </section>

              <section className="panel-surface finance-dashboard-panel finance-dashboard-news-panel">
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
                  <div className="dashboard-news-list">
                    {newsItems.map((item) => (
                      <Link key={item.id || item.externalId} to={`/news/${item.id}`} className="dash-news-item">
                        <div className="dash-news-item-header">
                          <span className={`dash-news-source-badge ${getDashboardNewsProviderBadgeClass(item.provider)}`.trim()}>
                            {buildNewsPlaceholderLabel(item)}
                          </span>
                          <span className="dash-news-date">{formatDateTime(item.publishedAt)}</span>
                        </div>
                        <strong className="dash-news-title">{item.title || t("dashboard.untitledNews")}</strong>
                      </Link>
                    ))}
                  </div>
                ) : null}
              </section>
            </aside>
          </section>
        </>
      ) : null}
      {/* AI Drawer overlay/panel */}
      {isAiDrawerOpen ? (
        <div className="ai-drawer-overlay" onClick={() => setAiDrawerOpen(false)}>
          <div className="ai-drawer-panel" role="dialog" aria-modal="true" aria-label="AI Piyasa Özeti" onClick={(e) => e.stopPropagation()}>
            <div className="ai-drawer-header">
              <div className="ai-drawer-title-group">
                <h3 className="ai-drawer-title">AI Piyasa Özeti</h3>
                <div className="ai-drawer-sub">Günün piyasa görünümü</div>
                <div className="ai-drawer-pills" aria-hidden="true">
                  <span className="ai-pill">PİYASA ÖZETİ</span>
                  <span className="ai-pill">GÜNCEL</span>
                  <span className="ai-pill">AI DESTEKLİ</span>
                </div>
              </div>
              <button type="button" className="ai-drawer-close" aria-label="Kapat" onClick={() => setAiDrawerOpen(false)}>✕</button>
            </div>
            <div className="ai-drawer-body">
              {aiSummary ? (
                <div className="ai-cards">
                  <div className="ai-card ai-summary-card">
                    <div className="dash-ai-banner">
                      <Sparkles size={14} className="dash-ai-banner-icon" aria-hidden="true" />
                      <p className="dash-ai-banner-text">
                        Piyasa görünümu <strong>{aiSummary.mood}</strong>
                        {aiSummary.topGainer ? (
                          <> {" · "} Güçlü: <strong>{aiSummary.topGainer.code ?? aiSummary.topGainer.symbol}</strong> (+{Number(aiSummary.topGainer.changeRate).toFixed(2)}%)</>
                        ) : null}
                        {aiSummary.topLoser ? (
                          <> {" · "} Zayıf: <strong>{aiSummary.topLoser.code ?? aiSummary.topLoser.symbol}</strong> ({Number(aiSummary.topLoser.changeRate).toFixed(2)}%)</>
                        ) : null}
                      </p>
                    </div>
                  </div>

                  <div className="ai-metrics-row">
                    <div className="ai-card ai-metric-card">
                      <span className="ai-metric-label">Genel eğilim</span>
                      <strong className="ai-metric-value">{aiSummary.mood}</strong>
                    </div>
                    <div className="ai-card ai-metric-card">
                      <span className="ai-metric-label">Ortalama değişim</span>
                      <strong className={`ai-metric-value ${aiSummary.avg >= 0 ? 'market-up' : 'market-down'}`}>{formatMarketChange(aiSummary.avg)}</strong>
                    </div>
                  </div>

                  {(aiSummary.topGainer || aiSummary.topLoser) ? (
                    <div className="ai-card ai-strong-weak-card">
                      {aiSummary.topGainer ? (
                        <div className="ai-strong">
                          <strong>Güçlü: </strong>{aiSummary.topGainer.code ?? aiSummary.topGainer.symbol} <span className="market-up">(+{Number(aiSummary.topGainer.changeRate).toFixed(2)}%)</span>
                        </div>
                      ) : null}
                      {aiSummary.topLoser ? (
                        <div className="ai-weak">
                          <strong>Zayıf: </strong>{aiSummary.topLoser.code ?? aiSummary.topLoser.symbol} <span className="market-down">({Number(aiSummary.topLoser.changeRate).toFixed(2)}%)</span>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              ) : (
                <div className="ai-card ai-empty-card">Henüz AI piyasa özeti oluşturulmadı.</div>
              )}
            </div>
          </div>
        </div>
      ) : null}

      <AuthRequiredModal
        isOpen={isAuthRequiredModalOpen}
        onClose={() => setAuthRequiredModalOpen(false)}
        onConfirm={login}
      />
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

function getDashboardInstrumentLabel(item) {
  const type = normalizeDashboardInstrumentType(item?.instrumentType);
  const pairLabel = getDashboardPairLabel(item);

  if (pairLabel) {
    return pairLabel;
  }

  if (type === "CRYPTO") {
    const baseCode = getDashboardCryptoBaseCode(item);
    return baseCode ? `${baseCode}/TRY` : getDashboardFallbackInstrumentLabel(item);
  }

  if (type === "STOCK") {
    return cleanDashboardStockCode(item?.code || item?.symbol || item?.displayName) || getDashboardFallbackInstrumentLabel(item);
  }

  return getDashboardFallbackInstrumentLabel(item);
}

function getDashboardInstrumentSubLabel(item) {
  const label = getDashboardInstrumentLabel(item);
  const displayName = String(item?.displayName || "").trim();

  if (!displayName || isDashboardInstrumentDuplicateText(displayName, label, item)) {
    return null;
  }

  return displayName;
}

function getDashboardFallbackInstrumentLabel(item) {
  const rawValue = item?.code || item?.displayName || item?.symbol || "-";
  const text = String(rawValue).trim();
  const formattedCode = formatInstrumentCode(text);

  return formattedCode && formattedCode !== "—" ? formattedCode : cleanDashboardStockCode(text) || text || "-";
}

function getDashboardPairLabel(item) {
  const candidates = [item?.displayName, item?.symbol];

  for (const candidate of candidates) {
    const label = formatDashboardTryPairLabel(candidate);
    if (label) {
      return label;
    }
  }

  return "";
}

function getDashboardCryptoBaseCode(item) {
  const pairCandidates = [item?.displayName, item?.symbol, item?.code];

  for (const candidate of pairCandidates) {
    const baseCode = extractDashboardCryptoPairBaseCode(candidate);
    if (baseCode) {
      return baseCode;
    }
  }

  const codeCandidates = [item?.code, item?.symbol];
  for (const candidate of codeCandidates) {
    const baseCode = extractDashboardPlainCryptoCode(candidate);
    if (baseCode) {
      return baseCode;
    }
  }

  return "";
}

function formatDashboardTryPairLabel(value) {
  const normalized = normalizeDashboardInstrumentText(value);
  if (!normalized.endsWith("TRY") || normalized.length <= 3) {
    return "";
  }

  const baseCode = collapseRepeatedDashboardCode(normalized.slice(0, -3));
  return baseCode ? `${baseCode}/TRY` : "";
}

function extractDashboardCryptoPairBaseCode(value) {
  const normalized = normalizeDashboardInstrumentText(value);
  if (!normalized) {
    return "";
  }

  const baseCode = normalized
    .replace(/(TRY)+$/u, "")
    .replace(/(USDT)+$/u, "")
    .replace(/(USD)+$/u, "");

  return baseCode === normalized ? "" : collapseRepeatedDashboardCode(baseCode);
}

function extractDashboardPlainCryptoCode(value) {
  const normalized = collapseRepeatedDashboardCode(normalizeDashboardInstrumentText(value));
  return /^[A-Z0-9]{2,12}$/u.test(normalized) ? normalized : "";
}

function collapseRepeatedDashboardCode(value) {
  if (!value || value.length % 2 !== 0) {
    return value;
  }

  const midpoint = value.length / 2;
  const firstHalf = value.slice(0, midpoint);

  return firstHalf === value.slice(midpoint) ? firstHalf : value;
}

function cleanDashboardStockCode(value) {
  return String(value || "")
    .trim()
    .replace(/\.IS$/iu, "")
    .toUpperCase();
}

function isDashboardInstrumentDuplicateText(text, label, item) {
  const normalizedText = normalizeDashboardInstrumentText(text);
  const normalizedLabel = normalizeDashboardInstrumentText(label);
  const normalizedCode = normalizeDashboardInstrumentText(item?.code);
  const normalizedSymbol = normalizeDashboardInstrumentText(item?.symbol);

  if (!normalizedText) {
    return true;
  }

  const equivalents = new Set([
    normalizedLabel,
    normalizedCode,
    normalizedSymbol,
    normalizedCode ? `${normalizedCode}TRY` : "",
    normalizedCode ? `${normalizedCode}USDT` : "",
    normalizedCode ? `${normalizedCode}USD` : "",
  ].filter(Boolean));

  if (equivalents.has(normalizedText)) {
    return true;
  }

  const textWithoutTry = normalizedText.replace(/(TRY)+$/u, "");
  const labelWithoutTry = normalizedLabel.replace(/(TRY)+$/u, "");
  const codeWithoutTry = normalizedCode.replace(/(TRY)+$/u, "");

  return Boolean(
    textWithoutTry
      && (
        textWithoutTry === labelWithoutTry
        || textWithoutTry === codeWithoutTry
        || normalizedText === `${codeWithoutTry}${normalizedLabel}`
        || normalizedText === `${codeWithoutTry}${labelWithoutTry}TRY`
      ),
  );
}

function normalizeDashboardInstrumentText(value) {
  return String(value || "")
    .toUpperCase()
    .replace(/[\s/:_-]+/gu, "");
}

function getDashboardPortfolioHoldingLabel(item) {
  return getDashboardInstrumentLabel({
    code: item?.instrumentCode || item?.symbol || item?.name,
    symbol: item?.symbol || item?.instrumentCode,
    displayName: item?.name,
    instrumentType: item?.instrumentType,
  });
}

function getDashboardPortfolioHoldingSubLabel(item, label) {
  const name = String(item?.name || "").trim();
  if (!name || isDashboardInstrumentDuplicateText(name, label, {
    code: item?.instrumentCode,
    symbol: item?.symbol,
  })) {
    return null;
  }

  return name;
}

function getDashboardNewsProviderBadgeClass(provider) {
  switch (String(provider || "").toUpperCase()) {
    case "AA_RSS":
      return "dash-news-source-badge--aa";
    case "GUARDIAN":
      return "dash-news-source-badge--guardian";
    case "CNBC_RSS":
      return "dash-news-source-badge--cnbc";
    default:
      return "";
  }
}

const INSTRUMENT_TYPE_LABELS = {
  STOCK: "Hisse",
  FX: "Doviz",
  FUND: "Fon",
  CRYPTO: "Kripto",
  FUTURES: "Vadeli",
  BOND: "Tahvil",
  INDEX: "Endeks",
  COMMODITY: "Emtia",
};

const DASHBOARD_MARKET_CATEGORY_FILTERS = [
  { key: "all", labelKey: "dashboard.marketCategoryFilters.all" },
  { key: "bist", labelKey: "dashboard.marketCategoryFilters.bist" },
  { key: "crypto", labelKey: "dashboard.marketCategoryFilters.crypto" },
  { key: "fund", labelKey: "dashboard.marketCategoryFilters.fund" },
  { key: "index", labelKey: "dashboard.marketCategoryFilters.index" },
  { key: "fx-commodities", labelKey: "dashboard.marketCategoryFilters.fxCommodities" },
];

function matchesDashboardMarketCategory(item, filterKey) {
  if (filterKey === "all") {
    return true;
  }

  const type = normalizeDashboardInstrumentType(item?.instrumentType);
  if (filterKey === "bist") {
    return type === "STOCK";
  }

  if (filterKey === "crypto") {
    return type === "CRYPTO";
  }

  if (filterKey === "fund") {
    return type === "FUND";
  }

  if (filterKey === "index") {
    return type === "INDEX";
  }

  if (filterKey === "fx-commodities") {
    return type === "FX" || type === "COMMODITY";
  }

  return true;
}

function normalizeDashboardInstrumentType(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "FOREX" || normalized === "CURRENCY") {
    return "FX";
  }
  return normalized;
}
