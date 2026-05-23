import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Bell, ChevronDown, Eye, Lock, Sparkles, TrendingDown, TrendingUp, Wallet } from "lucide-react";
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
  const [isAiSummaryOpen, setAiSummaryOpen] = useState(false);
  const [isWatchlistPopoverOpen, setWatchlistPopoverOpen] = useState(false);
  const [isAuthRequiredModalOpen, setAuthRequiredModalOpen] = useState(false);
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
      subtitle: dailyProfitLossPercent != null
        ? `${dailyProfitLossPercent >= 0 ? "+" : ""}${dailyProfitLossPercent.toFixed(2)}%`
        : t("dashboard.cards.dailyPnLSubtitle"),
      trend: formatSignedCurrency(dailyProfitLoss),
      tone: pnlTone,
      valueClassName: dailyProfitLoss > 0 ? "market-up" : dailyProfitLoss < 0 ? "market-down" : "",
      subtitleClassName: dailyProfitLossPercent > 0 ? "market-up" : dailyProfitLossPercent < 0 ? "market-down" : "",
      trendClassName: dailyProfitLoss > 0 ? "market-up" : dailyProfitLoss < 0 ? "market-down" : "",
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
      .filter((item) => Number.isFinite(Number(item.changeRate)))
      .sort((left, right) => {
        if (marketTab === "losers") {
          return toNumber(left.changeRate) - toNumber(right.changeRate);
        }

        return toNumber(right.changeRate) - toNumber(left.changeRate);
      })
      .slice(0, 5);
  }, [marketQuotes, marketTab]);

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

  return (
    <div className="dashboard-stack finance-dashboard-shell">
      {loading ? <LoadingSpinner label={t("dashboard.loading")} /> : null}

      {!loading ? (
        <>
          <section className="ticker-grid finance-dashboard-kpis">
            {orderedOverviewCardKeys.map((cardKey) => {
              if (cardKey === "alerts") {
                return isAuthenticated ? (
                  <Link key="alerts" to="/alerts" className={`summary-card summary-card-${alertTone} alerts-kpi-card`}>
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
                    <div aria-hidden="true" className={`summary-card-bar summary-card-bar--${alertTone}`} />
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
                      <div aria-hidden="true" className="summary-card-bar summary-card-bar--neutral" />
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
                      className={`summary-card summary-card-${card.tone} summary-card-interactive dashboard-watchlist-kpi-card`}
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
                      <div aria-hidden="true" className={`summary-card-bar summary-card-bar--${card.tone}`} />
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
                        className={`summary-card summary-card-${card.tone} summary-card-interactive dashboard-watchlist-kpi-card`}
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
                        <div aria-hidden="true" className={`summary-card-bar summary-card-bar--${card.tone}`} />
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
                          {t("dashboard.watchlistWidgetSeeAll")}
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
                <div key={card.key} className={`summary-card summary-card-${card.tone}`}>
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
                    <p className={`summary-card-subtitle${card.subtitleClassName ? ` ${card.subtitleClassName}` : ""}`}>{card.subtitle}</p>
                  ) : null}
                  <div aria-hidden="true" className={`summary-card-bar summary-card-bar--${card.tone}`} />
                </div>
              )
            })}
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
                          <th className="col-right">{t("dashboard.table.lastPrice")}</th>
                          <th className="col-right">{t("dashboard.table.dailyChange")}</th>
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
                            <td className="col-right">{formatCurrency(item.price, item.currency || "TRY")}</td>
                            <td className={`col-right ${toNumber(item.changeRate) >= 0 ? "market-up" : "market-down"}`}>
                              {formatMarketChange(item.changeRate)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : null}
              </section>

              <section className={`panel-surface finance-dashboard-panel dashboard-ai-summary-card${isAiSummaryOpen ? " is-open" : ""}`}>
                <button
                  type="button"
                  className="dashboard-ai-summary-toggle"
                  aria-expanded={isAiSummaryOpen}
                  onClick={() => setAiSummaryOpen((prev) => !prev)}
                >
                  <div className="dashboard-ai-summary-toggle-copy">
                    <div className="dashboard-ai-summary-toggle-title-row">
                      <span className="dashboard-ai-summary-toggle-icon"><Sparkles size={14} aria-hidden="true" /></span>
                      <h3>AI Piyasa Ozeti</h3>
                    </div>
                    <p>Piyasa yonunu, one cikan guclu ve zayif hareketleri kisa bir ozet halinde gosterir.</p>
                  </div>
                  <ChevronDown size={18} className={`dashboard-ai-summary-chevron${isAiSummaryOpen ? " is-open" : ""}`} />
                </button>

                <div className={`dashboard-ai-summary-collapse${isAiSummaryOpen ? " is-open" : ""}`}>
                  {aiSummary ? (
                    <div className="dashboard-ai-summary-content">
                      <div className="dash-ai-banner">
                        <Sparkles size={13} className="dash-ai-banner-icon" aria-hidden="true" />
                        <p className="dash-ai-banner-text">
                          Piyasa gorunumu <strong>{aiSummary.mood}</strong>
                          {aiSummary.topGainer ? (
                            <>{" · "}Guclu: <strong>{aiSummary.topGainer.code ?? aiSummary.topGainer.symbol}</strong>{" "}
                            (+{Number(aiSummary.topGainer.changeRate).toFixed(2)}%)</>
                          ) : null}
                          {aiSummary.topLoser ? (
                            <>{" · "}Zayif: <strong>{aiSummary.topLoser.code ?? aiSummary.topLoser.symbol}</strong>{" "}
                            ({Number(aiSummary.topLoser.changeRate).toFixed(2)}%)</>
                          ) : null}
                        </p>
                      </div>

                      <div className="dashboard-ai-summary-metrics">
                        <div className="dashboard-ai-summary-metric">
                          <span>Genel egilim</span>
                          <strong>{aiSummary.mood}</strong>
                        </div>
                        <div className="dashboard-ai-summary-metric">
                          <span>Ortalama degisim</span>
                          <strong className={aiSummary.avg >= 0 ? "market-up" : "market-down"}>{formatMarketChange(aiSummary.avg)}</strong>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="dashboard-ai-summary-empty">Yeterli piyasa verisi olmadigi icin ozet olusturulamadi.</div>
                  )}
                </div>
              </section>
            </div>

            <aside className="finance-dashboard-side">
              <section className="panel-surface finance-dashboard-panel dash-portfolio-widget">
                <div
                  className="dash-portfolio-header"
                  role="button"
                  tabIndex={0}
                  aria-expanded={isAuthenticated ? isPortfolioWidgetExpanded : undefined}
                  onClick={() => {
                    if (!isAuthenticated) {
                      setAuthRequiredModalOpen(true);
                      return;
                    }
                    setPortfolioWidgetExpanded((prev) => !prev);
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      if (!isAuthenticated) {
                        setAuthRequiredModalOpen(true);
                        return;
                      }
                      setPortfolioWidgetExpanded((prev) => !prev);
                    }
                  }}
                >
                  <div className="dash-portfolio-header-left">
                    <div className="dash-portfolio-header-copy">
                      <span className="dash-portfolio-header-title">{t("dashboard.portfolioWidget.title")}</span>
                    </div>
                    {isAuthenticated && portfolios.length > 1 ? (
                      <select
                        className="dash-portfolio-select"
                        value={effectiveWidgetPortfolioId ?? ""}
                        onClick={(event) => event.stopPropagation()}
                        onChange={(event) => {
                          event.stopPropagation();
                          setSelectedWidgetPortfolioId(Number(event.target.value));
                        }}
                      >
                        {portfolios.map((portfolio) => (
                          <option key={portfolio.portfolioId} value={portfolio.portfolioId}>{portfolio.portfolioName}</option>
                        ))}
                      </select>
                    ) : null}
                  </div>

                  <div className="dash-portfolio-header-right">
                    {!isAuthenticated ? (
                      <span className="dash-portfolio-lock-badge" aria-hidden="true">
                        <span className="guest-lock-icon"><Lock size={13} strokeWidth={2.3} aria-hidden="true" /></span>
                      </span>
                    ) : (
                      <>
                        {selectedWidgetTotalValue > 0 ? (
                          <span className="dash-portfolio-total">{formatCurrency(selectedWidgetTotalValue)}</span>
                        ) : null}
                        <ChevronDown
                          size={16}
                          className={`dash-portfolio-chevron${isPortfolioWidgetExpanded ? " is-open" : ""}`}
                        />
                      </>
                    )}
                  </div>
                </div>

                {isAuthenticated ? (
                  <div className={`dash-portfolio-body${isPortfolioWidgetExpanded ? " is-open" : ""}`}>
                    {portfolios.length === 0 ? (
                      <div className="dash-portfolio-empty">
                        <Link to="/portfolio" className="dash-portfolio-cta">{t("dashboard.portfolioWidget.goPortfolio")}</Link>
                      </div>
                    ) : widgetHoldings.length === 0 ? (
                      <div className="dash-portfolio-empty">{t("dashboard.portfolioWidget.empty")}</div>
                    ) : (
                      <div className="dash-portfolio-holdings">
                        {widgetHoldings.map((holding, index) => {
                          const pnl = toNumber(holding.profitLoss);
                          const pnlClass = !holding.valuationAvailable ? "" : pnl > 0 ? "market-up" : pnl < 0 ? "market-down" : "";
                          return (
                            <div
                              key={holding.holdingId || `${holding.instrumentCode}-${index}`}
                              className="dash-portfolio-row"
                            >
                              <div className="dash-portfolio-symbol-col">
                                <strong>{formatInstrumentCode(holding.instrumentCode)}</strong>
                                <span>{formatNumber(holding.quantity)} {t("dashboard.portfolioWidget.units")}</span>
                              </div>
                              <div className="dash-portfolio-value-col">
                                <span>{holding.valuationAvailable ? formatCurrency(holding.currentValue) : "-"}</span>
                                <span className={pnlClass}>
                                  {holding.valuationAvailable
                                    ? `${pnl >= 0 ? "+" : ""}${formatPercent(holding.profitLossPercent)}`
                                    : "-"}
                                </span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                    <div className="dash-portfolio-footer">
                      <Link to="/portfolio" className="dash-portfolio-cta">
                        {t("dashboard.portfolioWidget.goPortfolio")}
                      </Link>
                    </div>
                  </div>
                ) : null}
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

function formatSignedCurrency(value) {
  if (!Number.isFinite(value)) {
    return "-";
  }

  return `${value >= 0 ? "+" : ""}${formatCurrency(value)}`;
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
