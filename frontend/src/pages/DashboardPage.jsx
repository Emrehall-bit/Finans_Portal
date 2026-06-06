import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, ArrowLeftRight, BarChart2, Bell, Bitcoin, Eye, Globe, LineChart, Package, ShieldAlert, Sparkles, TrendingDown, TrendingUp, Wallet } from "lucide-react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import CreateAlertModal from "../components/market-detail/CreateAlertModal";
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
import AiLockedCard from "../components/ai/AiLockedCard";
import { getDashboardAiAnalysis } from "../api/aiApi";

const DASHBOARD_CHART_COLORS = ["#2563eb", "#0f9d58", "#f59e0b", "#dc2626", "#7c3aed"];

function PortfolioDonutChart({ data, totalValue }) {
  const [hovering, setHovering] = useState(false);

  return (
    <div className="dash-portfolio-dist-chart">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="label"
            outerRadius={70}
            innerRadius={42}
            onMouseEnter={() => setHovering(true)}
            onMouseLeave={() => setHovering(false)}
          >
            {data.map((entry) => (
              <Cell key={entry.key} fill={entry.color} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value, name) => [formatCurrency(value), name]}
            contentStyle={{ fontSize: "0.78rem", borderRadius: "8px", border: "1px solid rgba(214,222,241,0.7)", boxShadow: "0 4px 12px rgba(15,23,42,0.10)" }}
            itemStyle={{ color: "#1e293b" }}
            labelStyle={{ fontWeight: 700, color: "#0f172a", marginBottom: 2 }}
          />
        </PieChart>
      </ResponsiveContainer>
      {!hovering && (
        <div className="dash-portfolio-dist-center">
          <span>Toplam</span>
          <strong>{formatCurrency(totalValue)}</strong>
        </div>
      )}
    </div>
  );
}

export default function DashboardPage() {
  const { t, i18n } = useTranslation();
  const { userId, isAuthenticated, isPremium, login } = useAuth();

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
  const [aiAnalysisData, setAiAnalysisData] = useState(null);
  const [aiAnalysisLoading, setAiAnalysisLoading] = useState(false);
  const [aiAnalysisError, setAiAnalysisError] = useState("");
  const [aiAnalysisRetryKey, setAiAnalysisRetryKey] = useState(0);
  const [isWatchlistPopoverOpen, setWatchlistPopoverOpen] = useState(false);
  const [isAuthRequiredModalOpen, setAuthRequiredModalOpen] = useState(false);
  const [alertModal, setAlertModal] = useState({ isOpen: false, symbol: null, displaySymbol: null, currentPrice: null });
  const watchlistPopoverRef = useRef(null);

  const bestSnapshotId = portfolioSnapshots.length
    ? portfolioSnapshots.reduce((best, curr) =>
        toNumber(curr?.summary?.currentValue ?? curr?.summary?.totalCurrentValue) >
        toNumber(best?.summary?.currentValue ?? best?.summary?.totalCurrentValue) ? curr : best
      )?.portfolioId
    : null;
  const effectiveWidgetPortfolioId = selectedWidgetPortfolioId ?? bestSnapshotId ?? portfolios[0]?.portfolioId ?? null;
  const selectedWidgetSnapshot = portfolioSnapshots.find((s) => s.portfolioId === effectiveWidgetPortfolioId) ?? null;
  const selectedWidgetTotalValue = toNumber(
    selectedWidgetSnapshot?.summary?.currentValue ?? selectedWidgetSnapshot?.summary?.totalCurrentValue,
  );
  const widgetHoldings = selectedWidgetSnapshot?.holdings ?? [];
  const widgetPortfolioName = portfolios.find((p) => p.portfolioId === effectiveWidgetPortfolioId)?.portfolioName ?? null;

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
      const qCode = norm(q.code || q.symbol || "");
      const qType = String(q.instrumentType || "").toUpperCase();
      if (qCode.length <= 4 && (qType === "FX" || qType === "FOREX" || qType === "CURRENCY")) {
        const packedKey = `TCMB${qCode}SELL`;
        if (!quoteMap.has(packedKey)) quoteMap.set(packedKey, q);
      }
    }

    let nearest = null;
    let bestGap = Infinity;

    for (const alert of activeAlerts) {
      const quote = quoteMap.get(norm(alert.instrumentCode))
        ?? quoteMap.get(norm(formatInstrumentCode(alert.instrumentCode)));
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

  const portfolioDistributionData = useMemo(() => {
    const holdings = widgetHoldings?.length ? widgetHoldings : (portfolioSnapshots?.[0]?.holdings ?? []);
    const items = holdings
      .map((h) => ({ h, val: toNumber(h?.currentValue ?? h?.marketValue) }))
      .filter((x) => x.val > 0)
      .slice(0, 5);
    const total = items.reduce((s, x) => s + x.val, 0);
    return items.map(({ h, val }, idx) => ({
      key: h?.instrumentCode ?? String(idx),
      label: getDashboardPortfolioHoldingLabel(h),
      value: val,
      weight: total > 0 ? (val / total) * 100 : 0,
      color: DASHBOARD_CHART_COLORS[idx % DASHBOARD_CHART_COLORS.length],
    }));
  }, [widgetHoldings, portfolioSnapshots]);

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
      value: formatDashboardPnL(dailyProfitLoss),
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
      // Legacy packed FX key (e.g. "TCMBAUDSELL") for old DB entries
      const qCode = norm(q.code || q.symbol || "");
      const qType = String(q.instrumentType || "").toUpperCase();
      if (qCode.length <= 4 && (qType === "FX" || qType === "FOREX" || qType === "CURRENCY")) {
        const packedKey = `TCMB${qCode}SELL`;
        if (!quoteMap.has(packedKey)) quoteMap.set(packedKey, q);
      }
    }

    return watchlistItems.slice(0, 8).map((item) => {
      const displayCode = formatInstrumentCode(item.instrumentCode);
      const quote = quoteMap.get(norm(item.instrumentCode))
        ?? quoteMap.get(norm(displayCode));
      const shortCode = quote?.code ?? displayCode;
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

  const MARKET_CATEGORY_GROUPS = [
    { type: "STOCK",     label: "Hisse"  },
    { type: "CRYPTO",    label: "Kripto" },
    { type: "FUND",      label: "Fon"    },
    { type: "FX",        label: "Döviz"  },
    { type: "INDEX",     label: "Endeks" },
    { type: "COMMODITY", label: "Emtia"  },
  ];

  const marketCategorySummary = useMemo(() => {
    return MARKET_CATEGORY_GROUPS.map(({ type, label }) => {
      const items = marketQuotes.filter(
        (q) => normalizeDashboardInstrumentType(q?.instrumentType) === type && Number.isFinite(Number(q?.changeRate))
      );
      const up   = items.filter((q) => toNumber(q.changeRate) >= 0).length;
      const down = items.filter((q) => toNumber(q.changeRate) <  0).length;
      return { type, label, up, down, total: items.length };
    }).filter((c) => c.total > 0);
  }, [marketQuotes]);

  const marketParams = useMemo(() => {
    const changedRows = marketQuotes.filter((item) => Number.isFinite(Number(item?.changeRate)));
    if (!changedRows.length) return { avgMarketChange: 0, gainerCount: 0, loserCount: 0, totalQuotes: 0 };
    const avg = changedRows.reduce((sum, item) => sum + Number(item.changeRate), 0) / changedRows.length;
    return {
      avgMarketChange: Math.round(avg * 100) / 100,
      gainerCount: changedRows.filter((item) => Number(item.changeRate) > 0).length,
      loserCount: changedRows.filter((item) => Number(item.changeRate) < 0).length,
      totalQuotes: changedRows.length,
    };
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

  const dashboardAiLanguage = i18n.language?.toLowerCase().startsWith("en") ? "en" : "tr";
  const dashboardAiErrorFallback = dashboardAiLanguage === "en"
    ? "Analysis could not be loaded."
    : "Analiz getirilemedi.";

  useEffect(() => {
    if (!isAiDrawerOpen || !isAuthenticated || !isPremium || aiAnalysisData) return undefined;
    let cancelled = false;
    async function loadAnalysis() {
      try {
        setAiAnalysisLoading(true);
        setAiAnalysisError("");
        const data = await getDashboardAiAnalysis({
          language: dashboardAiLanguage,
          ...marketParams,
        });
        if (!cancelled) setAiAnalysisData(normalizeDashboardAiAnalysis(data));
      } catch (err) {
        if (!cancelled) {
          setAiAnalysisData(null);
          setAiAnalysisError(err?.response?.data?.message || err?.message || dashboardAiErrorFallback);
        }
      } finally {
        if (!cancelled) setAiAnalysisLoading(false);
      }
    }
    loadAnalysis();
    return () => { cancelled = true; };
  }, [isAiDrawerOpen, isAuthenticated, isPremium, aiAnalysisData, marketParams, dashboardAiLanguage, dashboardAiErrorFallback, aiAnalysisRetryKey]);

  useEffect(() => {
    if (!isAiDrawerOpen && (aiAnalysisLoading || aiAnalysisError || aiAnalysisData)) {
      setAiAnalysisData(null);
      setAiAnalysisError("");
      setAiAnalysisLoading(false);
    }
  }, [isAiDrawerOpen, aiAnalysisLoading, aiAnalysisError, aiAnalysisData]);

  return (
    <div className="dashboard-stack finance-dashboard-shell dashboard-page">
      {loading ? <LoadingSpinner label={t("dashboard.loading")} /> : null}

      {!loading ? (
        <>
          <section className="ticker-grid finance-dashboard-kpis">
            {orderedOverviewCardKeys.map((cardKey) => {
              if (cardKey === "alerts") {
                return isAuthenticated ? (
                  <Link key="alerts" to="/alerts" className={`summary-card summary-card-${alertTone} summary-card-alerts alerts-kpi-card summary-card--secondary`}>
                    <div className="kpi-card-header">
                      <div className="kpi-card-title-row">
                        <button
                          type="button"
                          className={`summary-card-icon summary-card-icon--${alertTone} dash-kpi-bell-btn`}
                          title="Hızlı alarm kur"
                          aria-label="Hızlı alarm kur"
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            setAlertModal({ isOpen: true, symbol: null, displaySymbol: null, currentPrice: null });
                          }}
                        >
                          <Bell size={18} />
                        </button>
                        <p className="summary-card-title">{t("dashboard.cards.alertsTitle")}</p>
                      </div>
                      <div className="kpi-card-badge-slot">
                        {hasTriggeredAlert ? <span className="summary-chip">{t("dashboard.cards.alertsTrend")}</span> : null}
                      </div>
                    </div>
                    <div className="kpi-card-content">
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
                    </div>
                  </Link>
                ) : (
                  <GuestLockOverlay
                    key="alerts"
                    compact
                    className="dashboard-kpi-guest-lock"
                    badgeClassName="dashboard-kpi-lock-badge"
                    onRequestAuth={() => setAuthRequiredModalOpen(true)}
                  >
                    <div className="summary-card summary-card-neutral summary-card--secondary">
                      <div className="kpi-card-header">
                        <div className="kpi-card-title-row">
                          <span className="summary-card-icon summary-card-icon--neutral"><Bell size={18} /></span>
                          <p className="summary-card-title">{t("dashboard.cards.alertsTitle")}</p>
                        </div>
                        <div className="kpi-card-badge-slot" />
                      </div>
                      <div className="kpi-card-content">
                        <h3>-</h3>
                        <p className="summary-card-subtitle">{t("dashboard.cards.alertsSubtitle")}</p>
                      </div>
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
                      className={`summary-card summary-card-${card.tone} summary-card-${card.key} summary-card-interactive dashboard-watchlist-kpi-card summary-card--secondary`}
                      aria-haspopup="dialog"
                      aria-expanded={isAuthenticated ? isWatchlistPopoverOpen : undefined}
                      onClick={handleWatchlistCardClick}
                    >
                      <div className="kpi-card-header">
                        <div className="kpi-card-title-row">
                          {card.icon ? <span className={`summary-card-icon summary-card-icon--${card.tone}`}>{card.icon}</span> : null}
                          <p className="summary-card-title">{card.title}</p>
                        </div>
                        <div className="kpi-card-badge-slot">
                          {card.trend ? <span className="summary-chip">{card.trend}</span> : null}
                        </div>
                      </div>
                      <div className="kpi-card-content">
                        <h3>{card.value}</h3>
                        {card.subtitle ? <p className="summary-card-subtitle">{card.subtitle}</p> : null}
                      </div>
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
                        className={`summary-card summary-card-${card.tone} summary-card-${card.key} summary-card-interactive dashboard-watchlist-kpi-card summary-card--secondary`}
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
                  <div className="kpi-card-header">
                    <div className="kpi-card-title-row">
                      {card.icon ? <span className={`summary-card-icon summary-card-icon--${card.tone}`}>{card.icon}</span> : null}
                      <p className="summary-card-title">{card.title}</p>
                    </div>
                    <div className="kpi-card-badge-slot">
                      {card.trend ? (
                        <span className={`summary-chip${card.trendClassName ? ` ${card.trendClassName}` : ""}`}>{card.trend}</span>
                      ) : null}
                    </div>
                  </div>
                  <div className="kpi-card-content">
                    <h3 className={card.valueClassName}>{card.value}</h3>
                    {card.isEmpty ? (
                      <Link to="/portfolio" className="summary-card-cta">{t("dashboard.cards.addPortfolio")}</Link>
                    ) : card.subtitle ? (
                      <p className="summary-card-subtitle">{card.subtitle}</p>
                    ) : null}
                  </div>
                </div>
              )
            })}
          </section>

          {/* AI trigger removed from main area — moved to right column */}

          <section className="finance-dashboard-grid">
            <div className="finance-dashboard-main">
              <section className="panel-surface finance-dashboard-panel">
                <div className="panel-head">
                  <h3 className="dashboard-section-title">Piyasalarda Bugün</h3>
                  <Link to="/markets" className="panel-text-link">{t("dashboard.allMarkets")} →</Link>
                </div>

                {sectionErrors.market && marketQuotes.length === 0 ? <ErrorMessage message={sectionErrors.market} /> : null}
                {!sectionErrors.market && marketQuotes.length === 0 ? (
                  <EmptyState title={t("dashboard.marketEmptyTitle")} description={t("dashboard.marketEmptyDescription")} />
                ) : null}

                {marketQuotes.length > 0 && (() => {
                  const totalUp   = marketCategorySummary.reduce((s, c) => s + c.up,   0);
                  const totalDown = marketCategorySummary.reduce((s, c) => s + c.down, 0);
                  const overview  = generateMarketBreadthOverview(marketCategorySummary, totalUp, totalDown);
                  return (
                    <>
                      {overview ? (
                        <div className="dash-breadth-overview">
                          <span className="dash-breadth-overview-label">Genel Görünüm</span>
                          <p className="dash-breadth-overview-text">{overview.line1}</p>
                          {overview.line2 ? <p className="dash-breadth-overview-text">{overview.line2}</p> : null}
                        </div>
                      ) : null}

                      <div className="dash-breadth-kpi-row">
                        <div className="dash-breadth-kpi dash-breadth-kpi--up">
                          <span className="dash-breadth-kpi-num">{totalUp}</span>
                          <span className="dash-breadth-kpi-label">Yükselen</span>
                        </div>
                        <div className="dash-breadth-kpi dash-breadth-kpi--down">
                          <span className="dash-breadth-kpi-num">{totalDown}</span>
                          <span className="dash-breadth-kpi-label">Düşen</span>
                        </div>
                      </div>

                      <div className="dash-breadth-pulse-list">
                        {marketCategorySummary.map((cat) => {
                          const status = getMarketBreadthStatus(cat.up, cat.total);
                          const Icon = MARKET_CATEGORY_ICONS[cat.type];
                          return (
                            <div key={cat.type} className={`dash-breadth-pulse-row dash-breadth-pulse-row--${status.tone}`}>
                              <span className="dash-breadth-pulse-cat">{cat.label}</span>
                              <div className="dash-breadth-pulse-right">
                                <span className={`dash-breadth-pulse-status dash-breadth-pulse-status--${status.tone}`}>
                                  {status.icon} {status.label}
                                </span>
                                <span className="dash-breadth-inline-up"><span className="dash-breadth-dot-up" aria-hidden="true">•</span>{cat.up} yükselen</span>
                                <span className="dash-breadth-inline-dn"><span className="dash-breadth-dot-dn" aria-hidden="true">•</span>{cat.down} düşen</span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </>
                  );
                })()}
              </section>

              {/* Portfolio panel: separate card with holdings */}
              <GuestLockOverlay
                className="dashboard-portfolio-guest-lock"
                badgeClassName="dash-portfolio-lock-badge"
                onRequestAuth={() => setAuthRequiredModalOpen(true)}
              >
                <section className="panel-surface finance-dashboard-panel dashboard-portfolio-panel">
                  <div className="panel-head">
                    <div>
                      <h3 className="dashboard-section-title">Portföy Özeti</h3>
                      {widgetPortfolioName ? (
                        <span className="dash-portfolio-panel-name">{widgetPortfolioName}</span>
                      ) : null}
                    </div>
                    <Link to="/portfolio" state={{ portfolioId: effectiveWidgetPortfolioId }} className="panel-text-link">{t("Portföyünü görüntüle") ?? "Tümünü Gör"}</Link>
                  </div>

                  {hasPortfolio && portfolioDistributionData.length > 0 ? (
                    <div className="dash-portfolio-dist-layout">
                      <PortfolioDonutChart data={portfolioDistributionData} totalValue={selectedWidgetTotalValue} />
                      <div className="dash-portfolio-dist-list">
                        {portfolioDistributionData.map((entry) => (
                          <div key={entry.key} className="dash-portfolio-dist-item">
                            <div className="dash-portfolio-dist-label">
                              <span className="portfolio-color-dot" style={{ backgroundColor: entry.color }} />
                              <span>{entry.label}</span>
                            </div>
                            <div className="dash-portfolio-dist-metrics">
                              <strong>{formatCurrency(entry.value)}</strong>
                              <span>{formatNumber(entry.weight, 1)}%</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : portfolioSnapshots.length === 0 ? (
                    <div className="dash-portfolio-empty">
                      <p className="dash-portfolio-empty-title">Takip edecek bir portföyünüz bulunmuyor.</p>
                      <p className="dash-portfolio-empty-desc">Borsa, kripto ve döviz yatırımlarınızı ayrı klasörlerde takip edebilmek için ilk portföyünüzü oluşturun.</p>
                      <Link to="/portfolio" className="summary-card-cta">{t("dashboard.cards.addPortfolio")}</Link>
                    </div>
                  ) : (
                    <div className="dash-portfolio-empty">
                      <p className="dash-portfolio-empty-title">Bu portföyde henüz bir varlık bulunmuyor.</p>
                      <p className="dash-portfolio-empty-desc">Portföyünüze hisse senedi, kripto para veya nakit ekleyerek anlık değerini izleyin.</p>
                      <Link to="/portfolio" className="summary-card-cta">{t("dashboard.cards.addPortfolio")}</Link>
                    </div>
                  )}
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
                    {newsItems.map((item) => {
                      const thumb = item?.thumbnailUrl || item?.imageUrl || item?.image || null;
                      return (
                        <Link key={item.id || item.externalId} to={`/news/${item.id}`} className={`dash-news-item${thumb ? " has-image" : ""}`}>
                          {thumb && (
                            <img
                              className="dash-news-thumb"
                              src={thumb}
                              alt=""
                              loading="lazy"
                              onError={e => {
                                e.currentTarget.style.display = "none";
                                e.currentTarget.closest(".dash-news-item")?.classList.remove("has-image");
                              }}
                            />
                          )}
                          <div className="dash-news-content">
                            <div className="dash-news-item-header">
                              <span className={`dash-news-source-badge ${getDashboardNewsProviderBadgeClass(item.provider)}`.trim()}>
                                {buildNewsPlaceholderLabel(item)}
                              </span>
                              <span className="dash-news-date">{formatDateTime(item.publishedAt)}</span>
                            </div>
                            <strong className="dash-news-title">{item.title || t("dashboard.untitledNews")}</strong>
                          </div>
                        </Link>
                      );
                    })}
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
          <div className="ai-drawer-panel" role="dialog" aria-modal="true" aria-label={t("dashboard.aiDrawer.title")} onClick={(e) => e.stopPropagation()}>
            <div className="ai-drawer-header">
              <div className="ai-drawer-title-group">
                <h3 className="ai-drawer-title">{t("dashboard.aiDrawer.title")}</h3>
                <div className="ai-drawer-sub">{t("dashboard.aiDrawer.subtitle")}</div>
                <div className="ai-drawer-pills" aria-hidden="true">
                  <span className="ai-pill">AI ANALİZİ</span>
                  <span className="ai-pill">GÜNCEL</span>
                  <span className="ai-pill">PREMİUM</span>
                </div>
              </div>
              <button type="button" className="ai-drawer-close" aria-label="Kapat" onClick={() => setAiDrawerOpen(false)}>✕</button>
            </div>
            <div className="ai-drawer-body">
              {!isAuthenticated ? (
                <AiLockedCard
                  featureName={t("dashboard.aiDrawer.title")}
                  description={t("dashboard.aiDrawer.loginRequired")}
                />
              ) : !isPremium ? (
                <AiLockedCard
                  featureName={t("dashboard.aiDrawer.title")}
                  description={t("dashboard.aiDrawer.premiumRequired")}
                  requiresPremium
                />
              ) : aiAnalysisLoading ? (
                <div className="portfolio-ai-sections">
                  <div className="portfolio-ai-state-card portfolio-ai-state-card--loading">
                    <strong>{t("dashboard.aiDrawer.loading")}</strong>
                    <p>{t("dashboard.aiDrawer.loadingDesc")}</p>
                  </div>
                  {[0, 1, 2].map((i) => (
                    <div key={i} className="portfolio-ai-section-card portfolio-ai-section-card--skeleton" aria-hidden="true">
                      <span className="portfolio-ai-skeleton-line portfolio-ai-skeleton-line--title" />
                      <span className="portfolio-ai-skeleton-line" />
                      <span className="portfolio-ai-skeleton-line portfolio-ai-skeleton-line--short" />
                    </div>
                  ))}
                </div>
              ) : aiAnalysisError ? (
                <div className="portfolio-ai-sections">
                  <div className="portfolio-ai-state-card portfolio-ai-state-card--error">
                    <div className="portfolio-ai-state-head">
                      <AlertTriangle size={16} />
                      <strong>{t("dashboard.aiDrawer.error")}</strong>
                    </div>
                    <p>{aiAnalysisError}</p>
                    <button
                      type="button"
                      className="secondary-button"
                      style={{ marginTop: 12 }}
                      onClick={() => {
                        setAiAnalysisData(null);
                        setAiAnalysisError("");
                        setAiAnalysisLoading(false);
                        setAiAnalysisRetryKey((value) => value + 1);
                      }}
                    >
                      {t("dashboard.aiDrawer.retry")}
                    </button>
                  </div>
                </div>
              ) : !aiAnalysisData ? (
                <div className="portfolio-ai-sections">
                  <div className="portfolio-ai-state-card">
                    <strong>{t("dashboard.aiDrawer.noData")}</strong>
                    <p>{t("dashboard.aiDrawer.noDataDesc")}</p>
                  </div>
                </div>
              ) : (
                <div className="portfolio-ai-sections">
                  {aiAnalysisData.marketContext ? (
                    <article className="portfolio-ai-section-card is-primary">
                      <div className="portfolio-ai-section-head">
                        <div className="portfolio-ai-section-icon"><Sparkles size={15} /></div>
                        <div>
                          <h4>{t("dashboard.aiDrawer.marketContext")}</h4>
                        </div>
                      </div>
                      <div className="portfolio-ai-section-copy">
                        <p>{aiAnalysisData.marketContext}</p>
                      </div>
                    </article>
                  ) : null}

                  {aiAnalysisData.newsContext ? (
                    <article className="portfolio-ai-section-card is-neutral">
                      <div className="portfolio-ai-section-head">
                        <div className="portfolio-ai-section-icon"><Sparkles size={15} /></div>
                        <div>
                          <h4>{t("dashboard.aiDrawer.newsContext")}</h4>
                        </div>
                      </div>
                      <div className="portfolio-ai-section-copy">
                        <p>{aiAnalysisData.newsContext}</p>
                      </div>
                    </article>
                  ) : null}

                  {aiAnalysisData.riskSignals?.length > 0 ? (
                    <article className="portfolio-ai-section-card is-risk">
                      <div className="portfolio-ai-section-head">
                        <div className="portfolio-ai-section-icon"><ShieldAlert size={15} /></div>
                        <div>
                          <h4>{t("dashboard.aiDrawer.riskSignals")}</h4>
                        </div>
                      </div>
                      <ul className="portfolio-ai-section-list">
                        {aiAnalysisData.riskSignals.map((s) => <li key={s}>{s}</li>)}
                      </ul>
                    </article>
                  ) : null}

                  {aiAnalysisData.watchPoints?.length > 0 ? (
                    <article className="portfolio-ai-section-card is-warning">
                      <div className="portfolio-ai-section-head">
                        <div className="portfolio-ai-section-icon"><AlertTriangle size={15} /></div>
                        <div>
                          <h4>{t("dashboard.aiDrawer.watchPoints")}</h4>
                        </div>
                      </div>
                      <ul className="portfolio-ai-section-list">
                        {aiAnalysisData.watchPoints.map((w) => <li key={w}>{w}</li>)}
                      </ul>
                    </article>
                  ) : null}

                  {aiAnalysisData.finalComment ? (
                    <article className="portfolio-ai-section-card is-neutral">
                      <div className="portfolio-ai-section-head">
                        <div className="portfolio-ai-section-icon"><Sparkles size={15} /></div>
                        <div>
                          <h4>{t("dashboard.aiDrawer.finalComment")}</h4>
                        </div>
                      </div>
                      <div className="portfolio-ai-section-copy">
                        <p>{aiAnalysisData.finalComment}</p>
                      </div>
                    </article>
                  ) : null}

                  {!aiAnalysisData.marketContext && !aiAnalysisData.newsContext
                    && !aiAnalysisData.riskSignals?.length && !aiAnalysisData.watchPoints?.length
                    && !aiAnalysisData.finalComment ? (
                    <div className="portfolio-ai-state-card">
                      <strong>{t("dashboard.aiDrawer.noContent")}</strong>
                      <p>{t("dashboard.aiDrawer.noContentDesc")}</p>
                    </div>
                  ) : null}
                </div>
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
      <CreateAlertModal
        isOpen={alertModal.isOpen}
        onClose={() => setAlertModal((s) => ({ ...s, isOpen: false }))}
        symbol={alertModal.symbol}
        displaySymbol={alertModal.displaySymbol}
        currentPrice={alertModal.currentPrice}
        userId={userId}
      />
    </div>
  );
}

function normalizeDashboardAiAnalysis(data) {
  if (!data || typeof data !== "object") {
    return {};
  }

  return {
    marketContext: typeof data.marketContext === "string" ? data.marketContext.trim() : "",
    newsContext: typeof data.newsContext === "string" ? data.newsContext.trim() : "",
    riskSignals: Array.isArray(data.riskSignals) ? data.riskSignals.filter(Boolean) : [],
    watchPoints: Array.isArray(data.watchPoints) ? data.watchPoints.filter(Boolean) : [],
    finalComment: typeof data.finalComment === "string" ? data.finalComment.trim() : "",
    marketTone: data.marketTone,
    fallbackUsed: data.fallbackUsed,
    metadata: data.metadata,
  };
}

function isTriggeredAlert(item) {
  return String(item?.status || "").toUpperCase() === "TRIGGERED" || Boolean(item?.triggeredAt);
}

function toNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function formatDashboardPnL(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric >= 0) return formatCurrency(value);
  const abs = formatCurrency(-numeric);
  return abs.replace(/^([^0-9\s,.‒-―]+)/, "$1-");
}

function formatMarketPrice(price, currency = "TRY") {
  const numeric = Number(price);
  if (!Number.isFinite(numeric)) return formatCurrency(price, currency);
  const fractionDigits = Math.abs(numeric) > 0 && Math.abs(numeric) < 1 ? 4 : 2;
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: currency || "TRY",
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(numeric);
  } catch {
    return formatCurrency(price, currency);
  }
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
  if (midpoint < 3) {
    return value;
  }

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

function getMarketBreadthStatus(up, total) {
  if (!total) return { label: "Veri Yok", icon: "—", tone: "neutral" };
  const r = up / total;
  if (r >= 0.65) return { label: "Güçlü",     icon: "↑",  tone: "up"      };
  if (r >= 0.55) return { label: "Pozitif",   icon: "↗",  tone: "up"      };
  if (r >= 0.45) return { label: "Dengeli",   icon: "↔",  tone: "neutral" };
  if (r >= 0.35) return { label: "Zayıf",     icon: "↘",  tone: "down"    };
  return             { label: "Çok Zayıf", icon: "↓",  tone: "down"    };
}

function generateMarketBreadthOverview(categorySummary, totalUp, totalDown) {
  const total = totalUp + totalDown;
  if (!total) return null;

  const upRatio = totalUp / total;

  let line1;
  if (upRatio >= 0.62)      line1 = "Kategorilerin büyük bölümünde yükselenler önde.";
  else if (upRatio >= 0.54) line1 = "Piyasada genel olarak alıcılı görünüm hakim.";
  else if (upRatio >= 0.46) line1 = "Piyasa görünümü dengeli seyrediyor.";
  else if (upRatio >= 0.38) line1 = "Piyasada hafif satış baskısı gözlemleniyor.";
  else                       line1 = "Piyasalarda satış baskısı belirgin.";

  const cats = categorySummary
    .filter((c) => c.total > 0)
    .map((c) => ({ ...c, ratio: c.up / c.total }))
    .sort((a, b) => b.ratio - a.ratio);

  if (cats.length < 2) return { line1, line2: null };

  const best  = cats[0];
  const worst = cats[cats.length - 1];
  const spread = best.ratio - worst.ratio;

  let line2 = null;
  if (best.ratio >= 0.65 && worst.ratio <= 0.35) {
    line2 = `${best.label} öne çıkarken ${worst.label} baskı altında.`;
  } else if (best.ratio >= 0.65) {
    line2 = `${best.label} kategorisi pozitif ayrışıyor.`;
  } else if (worst.ratio <= 0.35) {
    line2 = `${worst.label} kategorisinde belirgin satış baskısı var.`;
  } else if (spread >= 0.20) {
    line2 = `${best.label} öne çıkarken ${worst.label} geride kalıyor.`;
  }

  return { line1, line2 };
}

const MARKET_CATEGORY_ICONS = {
  STOCK:     BarChart2,
  CRYPTO:    Bitcoin,
  FUND:      LineChart,
  FX:        ArrowLeftRight,
  INDEX:     Globe,
  COMMODITY: Package,
};

function getDashboardMarketCategoryLabel(item) {
  switch (normalizeDashboardInstrumentType(item?.instrumentType)) {
    case "STOCK": return "BIST";
    case "CRYPTO": return "Kripto";
    case "FX": return "Döviz";
    case "FUND": return "Fon";
    case "INDEX": return "Endeks";
    case "COMMODITY": return "Emtia";
    case "FUTURES": return "Vadeli";
    default: return "";
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

function normalizeDashboardInstrumentType(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "FOREX" || normalized === "CURRENCY") {
    return "FX";
  }
  return normalized;
}
