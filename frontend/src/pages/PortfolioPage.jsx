import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ChartPie, GripVertical, LayoutGrid, List, RotateCcw } from "lucide-react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { Responsive, WidthProvider } from "react-grid-layout/legacy";
import {
  createPortfolio,
  createPortfolioHolding,
  deletePortfolioHolding,
  getPortfolioDetails,
  getUserPortfolios,
  updatePortfolioHolding,
} from "../api/portfolioApi";

import { getPriceOnDate, searchInstruments } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import { getUserWatchlist } from "../api/watchlistApi";
import { useAuth } from "../auth/AuthContext";
import AiPortfolioCommentaryCard from "../components/ai/AiPortfolioCommentaryCard";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PortfolioHeatmap from "../components/portfolio/PortfolioHeatmap";
import useToast from "../hooks/useToast";
import { useTheme } from "../theme/ThemeContext";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";
import "react-grid-layout/css/styles.css";
import "react-resizable/css/styles.css";

const CHART_COLORS = ["#2563eb", "#0f9d58", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];
const EMPTY_HOLDINGS = [];
const INTERNAL_CASH_CODE = "TRY";
const INTERNAL_CASH_LABEL = "Nakit";
const ResponsiveGridLayout = WidthProvider(Responsive);
const PORTFOLIO_WIDGET_LAYOUTS_STORAGE_KEY = "fp:portfolio:widget-layouts:v2";
const PORTFOLIO_WIDGET_LAYOUTS_DEFAULT = {
  lg: [
    { i: "performance", x: 0, y: 0, w: 8, h: 4, minW: 6, minH: 3 },
    { i: "allocation", x: 8, y: 0, w: 4, h: 4, minW: 4, minH: 3 },
    { i: "holdings", x: 0, y: 4, w: 8, h: 4, minW: 7, minH: 4 },
    { i: "heatmap", x: 8, y: 4, w: 4, h: 2, minW: 4, minH: 2 },
    { i: "ai", x: 8, y: 6, w: 4, h: 2, minW: 4, minH: 2 },
    { i: "watchlist", x: 8, y: 8, w: 4, h: 3, minW: 4, minH: 3 },
  ],
  md: [
    { i: "performance", x: 0, y: 0, w: 6, h: 4, minW: 5, minH: 3 },
    { i: "allocation", x: 6, y: 0, w: 4, h: 4, minW: 4, minH: 3 },
    { i: "holdings", x: 0, y: 4, w: 6, h: 4, minW: 6, minH: 4 },
    { i: "heatmap", x: 6, y: 4, w: 4, h: 2, minW: 4, minH: 2 },
    { i: "ai", x: 6, y: 6, w: 4, h: 2, minW: 4, minH: 2 },
    { i: "watchlist", x: 6, y: 8, w: 4, h: 3, minW: 4, minH: 3 },
  ],
  sm: [
    { i: "allocation", x: 0, y: 0, w: 1, h: 5, minH: 4 },
    { i: "performance", x: 0, y: 5, w: 1, h: 4, minH: 3 },
    { i: "heatmap", x: 0, y: 9, w: 1, h: 3, minH: 2 },
    { i: "ai", x: 0, y: 12, w: 1, h: 3, minH: 2 },
    { i: "watchlist", x: 0, y: 15, w: 1, h: 4, minH: 3 },
    { i: "holdings", x: 0, y: 19, w: 1, h: 6, minH: 5 },
  ],
};

function createDefaultWidgetLayouts() {
  return Object.fromEntries(
    Object.entries(PORTFOLIO_WIDGET_LAYOUTS_DEFAULT).map(([breakpoint, layouts]) => [
      breakpoint,
      layouts.map((item) => ({ ...item })),
    ]),
  );
}

function mergeWidgetLayouts(savedLayouts = {}) {
  const defaults = createDefaultWidgetLayouts();
  return Object.fromEntries(
    Object.entries(defaults).map(([breakpoint, layouts]) => {
      const savedById = new Map(
        Array.isArray(savedLayouts?.[breakpoint])
          ? savedLayouts[breakpoint]
              .filter((item) => item && typeof item.i === "string")
              .map((item) => [item.i, item])
          : [],
      );
      return [
        breakpoint,
        layouts.map((item) => ({
          ...item,
          ...(savedById.get(item.i) ?? {}),
        })),
      ];
    }),
  );
}

function loadPortfolioWidgetLayouts() {
  if (typeof window === "undefined") {
    return createDefaultWidgetLayouts();
  }
  try {
    const rawValue = window.localStorage.getItem(PORTFOLIO_WIDGET_LAYOUTS_STORAGE_KEY);
    if (!rawValue) {
      return createDefaultWidgetLayouts();
    }
    return mergeWidgetLayouts(JSON.parse(rawValue));
  } catch {
    return createDefaultWidgetLayouts();
  }
}

function persistPortfolioWidgetLayouts(layouts) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(PORTFOLIO_WIDGET_LAYOUTS_STORAGE_KEY, JSON.stringify(layouts));
}

export default function PortfolioPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const { chartTheme } = useTheme();
  const { toast, showToast } = useToast();
  const [portfolios, setPortfolios] = useState([]);
  const [selectedPortfolioId, setSelectedPortfolioId] = useState(null);
  const [selectedPortfolio, setSelectedPortfolio] = useState(null);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState("");
  const [watchlist, setWatchlist] = useState([]);
  const [newPortfolio, setNewPortfolio] = useState({ portfolioName: "", visibilityStatus: "PRIVATE" });
  const [isCreatePortfolioModalOpen, setCreatePortfolioModalOpen] = useState(false);
  const [isHoldingModalOpen, setHoldingModalOpen] = useState(false);
  const [editingHolding, setEditingHolding] = useState(null);
  const [holdingForm, setHoldingForm] = useState({
    instrumentCode: "",
    quantity: "",
    buyPrice: "",
    purchaseDate: "",
  });
  const [savingHolding, setSavingHolding] = useState(false);
  const [instrumentSearch, setInstrumentSearch] = useState("");
  const [instrumentResults, setInstrumentResults] = useState([]);
  const [instrumentSearchOpen, setInstrumentSearchOpen] = useState(false);
  const [fetchingPrice, setFetchingPrice] = useState(false);
  const [priceAutoFetched, setPriceAutoFetched] = useState(false);
  const [priceFetchError, setPriceFetchError] = useState("");
  const [widgetLayouts, setWidgetLayouts] = useState(loadPortfolioWidgetLayouts);
  const [isWidgetEditMode, setWidgetEditMode] = useState(false);
  const [widgetBreakpoint, setWidgetBreakpoint] = useState("lg");
  const [allocationView, setAllocationView] = useState("chart");
  const [isActivityModalOpen, setActivityModalOpen] = useState(false);
  const instrumentSearchRef = useRef(null);
  const searchDebounceRef = useRef(null);
  const canEditWidgetLayout = widgetBreakpoint === "lg" || widgetBreakpoint === "md";

  useEffect(() => {
    if (!canEditWidgetLayout && isWidgetEditMode) {
      setWidgetEditMode(false);
    }
  }, [canEditWidgetLayout, isWidgetEditMode]);

  useEffect(() => {
    if (userId) {
      loadPortfolios();
      loadWatchlist();
    }
  }, [userId]);

  useEffect(() => {
    if (selectedPortfolioId) {
      loadPortfolioDetails(selectedPortfolioId);
    } else {
      setSelectedPortfolio(null);
    }
  }, [selectedPortfolioId]);

  async function loadPortfolios(preferredId = null) {
    try {
      setLoadingList(true);
      setError("");
      const list = await getUserPortfolios(userId);
      setPortfolios(list);

      if (list.length === 0) {
        setSelectedPortfolioId(null);
        return;
      }

      const resolvedId =
        preferredId && list.some((item) => item.portfolioId === preferredId)
          ? preferredId
          : selectedPortfolioId && list.some((item) => item.portfolioId === selectedPortfolioId)
            ? selectedPortfolioId
            : list[0].portfolioId;

      setSelectedPortfolioId(resolvedId);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.loadListError")));
    } finally {
      setLoadingList(false);
    }
  }

  async function loadWatchlist() {
    try {
      const rows = await getUserWatchlist(userId);
      setWatchlist(Array.isArray(rows) ? rows : []);
    } catch {
      setWatchlist([]);
    }
  }

  async function loadPortfolioDetails(portfolioId) {
    try {
      setLoadingDetail(true);
      setError("");
      const details = await getPortfolioDetails(portfolioId);
      setSelectedPortfolio(details);
    } catch (err) {
      setSelectedPortfolio(null);
      setError(extractErrorMessage(err, t("portfolio.loadDetailError")));
    } finally {
      setLoadingDetail(false);
    }
  }

  async function handleCreatePortfolio(event) {
    event.preventDefault();
    try {
      setError("");
      const created = await createPortfolio(userId, newPortfolio);
      showToast("success", t("portfolio.createSuccess"));
      setNewPortfolio({ portfolioName: "", visibilityStatus: "PRIVATE" });
      setCreatePortfolioModalOpen(false);
      await loadPortfolios(created?.portfolioId ?? null);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.createError")));
    }
  }

  function openCreatePortfolioModal() {
    setCreatePortfolioModalOpen(true);
  }

  function closeCreatePortfolioModal() {
    setCreatePortfolioModalOpen(false);
    setNewPortfolio({ portfolioName: "", visibilityStatus: "PRIVATE" });
  }

  function openAddHoldingModal() {
    setEditingHolding(null);
    setHoldingForm({ instrumentCode: "", quantity: "", buyPrice: "", purchaseDate: "" });
    setInstrumentSearch("");
    setInstrumentResults([]);
    setInstrumentSearchOpen(false);
    setPriceAutoFetched(false);
    setPriceFetchError("");
    setHoldingModalOpen(true);
  }

  function openEditHoldingModal(holding) {
    setEditingHolding(holding);
    setHoldingForm({
      instrumentCode: holding.instrumentCode || "",
      quantity: holding.quantity ?? "",
      buyPrice: holding.buyPrice ?? "",
      purchaseDate: holding.purchaseDate ?? "",
    });
    setInstrumentSearch(formatInstrumentLabel(holding.instrumentCode));
    setInstrumentResults([]);
    setInstrumentSearchOpen(false);
    setPriceAutoFetched(false);
    setPriceFetchError("");
    setHoldingModalOpen(true);
  }

  function closeHoldingModal() {
    setHoldingModalOpen(false);
    setEditingHolding(null);
    setHoldingForm({ instrumentCode: "", quantity: "", buyPrice: "", purchaseDate: "" });
    setInstrumentSearch("");
    setInstrumentResults([]);
    setInstrumentSearchOpen(false);
    setPriceAutoFetched(false);
    setPriceFetchError("");
  }

  const handleInstrumentSearchChange = useCallback((value) => {
    setInstrumentSearch(value);
    setHoldingForm((current) => ({ ...current, instrumentCode: "" }));
    setPriceAutoFetched(false);
    setPriceFetchError("");

    if (searchDebounceRef.current) {
      clearTimeout(searchDebounceRef.current);
    }

    if (value.length < 2) {
      setInstrumentResults([]);
      setInstrumentSearchOpen(false);
      return;
    }

    searchDebounceRef.current = setTimeout(async () => {
      try {
        const results = await searchInstruments(value, 20, true);
        setInstrumentResults(results);
        setInstrumentSearchOpen(results.length > 0);
      } catch {
        setInstrumentResults([]);
        setInstrumentSearchOpen(false);
      }
    }, 300);
  }, []);

  function selectInstrument(instrument) {
    setHoldingForm((current) => ({ ...current, instrumentCode: instrument.code, buyPrice: "", purchaseDate: "" }));
    setInstrumentSearch(formatInstrumentSearchValue(instrument));
    setInstrumentResults([]);
    setInstrumentSearchOpen(false);
    setPriceAutoFetched(false);
    setPriceFetchError("");
  }

  async function handlePurchaseDateChange(dateValue) {
    setHoldingForm((current) => ({ ...current, purchaseDate: dateValue, buyPrice: "" }));
    setPriceAutoFetched(false);
    setPriceFetchError("");

    const code = holdingForm.instrumentCode;
    if (!code || !dateValue) {
      return;
    }

    try {
      setFetchingPrice(true);
      const result = await getPriceOnDate(code, dateValue);
      if (result && result.price != null) {
        setHoldingForm((current) => ({ ...current, buyPrice: String(result.price) }));
        setPriceAutoFetched(true);
      } else {
        setPriceFetchError(t("portfolio.priceNotFoundForDate"));
      }
    } catch {
      setPriceFetchError(t("portfolio.priceNotFoundForDate"));
    } finally {
      setFetchingPrice(false);
    }
  }

  async function handleSaveHolding(event) {
    event.preventDefault();
    if (!selectedPortfolio?.portfolioId) {
      return;
    }

    try {
      setSavingHolding(true);
      setError("");
      const payload = {
        instrumentCode: normalizeCode(holdingForm.instrumentCode),
        quantity: Number(holdingForm.quantity),
        buyPrice: Number(holdingForm.buyPrice),
        purchaseDate: holdingForm.purchaseDate || null,
      };

      if (editingHolding?.holdingId) {
        await updatePortfolioHolding(selectedPortfolio.portfolioId, editingHolding.holdingId, {
          quantity: payload.quantity,
          buyPrice: payload.buyPrice,
        });
        showToast("success", t("portfolio.holdingUpdateSuccess"));
      } else {
        await createPortfolioHolding(selectedPortfolio.portfolioId, payload);
        showToast("success", t("portfolio.holdingAddSuccess"));
      }

      closeHoldingModal();
      await loadPortfolioDetails(selectedPortfolio.portfolioId);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.holdingSaveError")));
    } finally {
      setSavingHolding(false);
    }
  }

  async function handleDeleteHolding(holdingId) {
    if (!selectedPortfolio?.portfolioId) {
      return;
    }

    try {
      setError("");
      await deletePortfolioHolding(selectedPortfolio.portfolioId, holdingId);
      showToast("success", t("portfolio.holdingDeleteSuccess"));
      await loadPortfolioDetails(selectedPortfolio.portfolioId);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.holdingDeleteError")));
    }
  }

  const summary = selectedPortfolio?.summary ?? null;
  const holdings = selectedPortfolio?.holdings ?? EMPTY_HOLDINGS;
  const totalCost = toNumber(summary?.totalCost);
  const totalValue = toNumber(summary?.currentValue ?? summary?.totalCurrentValue);
  const totalProfitLoss = toNumber(summary?.profitLoss ?? summary?.totalProfitLoss);
  const totalProfitLossPercent = toMaybeNumber(summary?.profitLossPercent);
  const hasDailyPerformance = holdings.some((holding) => Number.isFinite(Number(holding.dailyProfitLoss)));
  const dailyProfitLoss = hasDailyPerformance
    ? holdings.reduce((sum, holding) => sum + toNumber(holding.dailyProfitLoss), 0)
    : null;
  const dailyProfitLossPercent = hasDailyPerformance
    ? holdings.reduce((sum, holding) => sum + toNumber(holding.dailyChangePercent), 0) / Math.max(holdings.length, 1)
    : null;

  const allocationData = useMemo(() => {
    const items = holdings
      .map((holding) => {
        const currentValue = resolveHoldingValue(holding);
        const weight = totalValue > 0 ? (currentValue / totalValue) * 100 : 0;
        return {
          instrumentCode: holding.instrumentCode,
          displayName: formatInstrumentLabel(holding.instrumentCode),
          currentValue,
          weight,
          profitLoss: toMaybeNumber(holding.profitLoss),
          profitLossPercent: toMaybeNumber(holding.profitLossPercent),
        };
      })
      .filter((holding) => holding.currentValue > 0)
      .sort((a, b) => b.currentValue - a.currentValue);
    return items;
  }, [holdings, totalValue]);

  const topHolding = allocationData[0] ?? null;
  const cashValue = allocationData
    .filter((item) => normalizeCode(item.instrumentCode) === INTERNAL_CASH_CODE)
    .reduce((sum, item) => sum + item.currentValue, 0);
  const cashRatio = totalValue > 0 ? (cashValue / totalValue) * 100 : null;

  const assetCategorySummary = useMemo(() => buildAssetCategorySummary(holdings, totalValue), [holdings, totalValue]);
  const assetTypeBreakdown = useMemo(
    () => assetCategorySummary.filter((item) => item.value > 0).sort((a, b) => b.value - a.value),
    [assetCategorySummary],
  );

  const heatmapItems = useMemo(
    () =>
      allocationData.slice(0, 12).map((item) => ({
        symbol: item.displayName,
        weight: item.weight,
        changePercent: item.profitLossPercent ?? 0,
        value: item.currentValue,
      })),
    [allocationData],
  );

  const activityItems = useMemo(() => {
    const rows = [...holdings]
      .sort((a, b) => getHoldingActivityTimestamp(b) - getHoldingActivityTimestamp(a))
      .slice(0, 5)
      .map((holding) => ({
        title: `${formatInstrumentLabel(holding.instrumentCode)} pozisyonu gÃ¼ncellendi`,
        timestamp: holding.updatedAt || holding.createdAt || selectedPortfolio?.updatedAt || selectedPortfolio?.createdAt,
        detail: `${formatNumber(holding.quantity)} adet Â· ${formatCurrency(holding.buyPrice)}`,
        tone: toNumber(holding.profitLoss) >= 0 ? "positive" : "negative",
      }));

    if (selectedPortfolio && rows.length < 5) {
      rows.push({
        title: "PortfÃ¶y deÄŸeri hesaplandÄ±",
        timestamp: selectedPortfolio.updatedAt || selectedPortfolio.createdAt,
        detail: `${formatCurrency(totalValue || totalCost)} Â· ${formatNumber(holdings.length, 0)} varlÄ±k`,
        tone: "neutral",
      });
    }

    return rows;
  }, [holdings, selectedPortfolio, totalCost, totalValue]);

  const watchlistItems = useMemo(() => {
    if (Array.isArray(watchlist) && watchlist.length > 0) {
      return watchlist.slice(0, 5).map((item) => {
        const linkedHolding = holdings.find((holding) => normalizeCode(holding.instrumentCode) === normalizeCode(item.instrumentCode));
        return {
          symbol: formatInstrumentLabel(item.instrumentCode),
          price: toMaybeNumber(item.currentPrice),
          changePercent: toMaybeNumber(linkedHolding?.profitLossPercent),
          status: linkedHolding?.valuationAvailable ? "CanlÄ±" : "Takipte",
        };
      });
    }

    return allocationData.slice(0, 5).map((item) => ({
      symbol: item.displayName,
      price: item.currentValue > 0 ? item.currentValue : null,
      changePercent: item.profitLossPercent,
      status: item.weight > 20 ? "Ã–ne Ã§Ä±kan" : "Ä°zleniyor",
    }));
  }, [allocationData, holdings, watchlist]);

  const widgetTitles = useMemo(
    () => ({
      kpi: "PortfÃ¶y Ã¶zeti",
      performance: t("portfolio.historyTitle"),
      summary: "VarlÄ±k Ã¶zeti",
      holdings: t("portfolio.holdingsTitle"),
      allocation: t("portfolio.allocationTitle"),
      heatmap: "PortfÃ¶y Ä±sÄ± haritasÄ±",
      ai: "AI PortfÃ¶y Yorumu",
      watchlist: "Takip listesi",
    }),
    [t],
  );

  const handleWidgetLayoutChange = useCallback((_, allLayouts) => {
    const nextLayouts = mergeWidgetLayouts(allLayouts);
    setWidgetLayouts(nextLayouts);
    persistPortfolioWidgetLayouts(nextLayouts);
  }, []);

  function handleResetWidgetLayouts() {
    const nextLayouts = createDefaultWidgetLayouts();
    setWidgetLayouts(nextLayouts);
    persistPortfolioWidgetLayouts(nextLayouts);
  }

  return (
    <div className="portfolio-management-shell portfolio-page-v3 portfolio-page-v4">
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {loadingList ? <LoadingSpinner label={t("portfolio.loadingList")} /> : null}

      {!loadingList && portfolios.length === 0 ? (
        <section className="portfolio-empty-state-shell">
          <div className="panel-surface portfolio-empty-state-card">
            <div className="portfolio-empty-state-icon" aria-hidden="true">
              <ChartPie size={36} />
            </div>
            <div className="portfolio-empty-state-copy">
              <p className="eyebrow">Portfoy</p>
              <h2>Henüz bir portföyünüz bulunmuyor.</h2>
              <p>İlk portföyünüzü oluşturarak yatırımlarınızı takip etmeye başlayın.</p>
            </div>
            <button type="button" onClick={openCreatePortfolioModal}>
              + İlk Portföyünü Oluştur
            </button>
          </div>
        </section>
      ) : null}

      {!loadingList && portfolios.length > 0 ? (
        <section className="portfolio-dashboard">
          <header className="panel-surface portfolio-page-top">
            <div className="portfolio-page-titlebar">
              <p className="eyebrow">Seçili Portföy</p>
              <div className="portfolio-page-selector-row">
                <h2 className="portfolio-detail-title">{selectedPortfolio?.portfolioName || "Portföy seçin"}</h2>
                <label className="portfolio-header-select-wrap">
                  <span className="sr-only">Portföy seç</span>
                  <select
                    className="portfolio-header-select"
                    value={selectedPortfolioId ?? ""}
                    onChange={(event) => setSelectedPortfolioId(Number(event.target.value))}
                  >
                    {portfolios.map((portfolio) => (
                      <option key={portfolio.portfolioId} value={portfolio.portfolioId}>
                        {portfolio.portfolioName}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <p className="portfolio-detail-meta portfolio-detail-meta--topbar">
                {formatVisibilityStatus(selectedPortfolio?.visibilityStatus || "PRIVATE", t)} · {t("portfolio.createdAt", { value: formatDateTime(selectedPortfolio?.createdAt) })}
              </p>
            </div>
            <div className="actions-row portfolio-top-actions">
              <button type="button" className="secondary-button" onClick={openCreatePortfolioModal}>
                + Yeni Portföy Oluştur
              </button>
              <button type="button" onClick={openAddHoldingModal} disabled={!selectedPortfolio?.portfolioId}>
                + Varlık Ekle
              </button>
            </div>
          </header>

          {loadingDetail ? <LoadingSpinner label={t("portfolio.loadingDetail")} /> : null}

          {!loadingDetail && !selectedPortfolio ? (
            <section className="panel-surface portfolio-management-panel">
              <EmptyState title={t("portfolio.emptySelectionTitle")} description={t("portfolio.emptySelectionDescription")} />
            </section>
          ) : null}

          {!loadingDetail && selectedPortfolio ? (
            <>
              <section className="panel-surface portfolio-summary-strip portfolio-summary-strip--standalone">
                <div className="portfolio-summary-grid">
                  <PortfolioMetricCard label={t("portfolio.summary.totalCost")} value={formatCurrency(totalCost)} tone="neutral" />
                  <PortfolioMetricCard label={t("portfolio.summary.currentValue")} value={formatCurrency(totalValue)} tone="accent" />
                  <PortfolioMetricCard
                    label="Günlük K/Z"
                    value={dailyProfitLoss == null ? "Henüz yok" : formatCurrency(dailyProfitLoss)}
                    subvalue={dailyProfitLossPercent == null ? null : formatPercent(dailyProfitLossPercent)}
                    tone={resolveTone(dailyProfitLoss)}
                  />
                  <PortfolioMetricCard
                    label="Toplam K/Z"
                    value={formatCurrency(totalProfitLoss)}
                    subvalue={totalProfitLossPercent == null ? null : formatPercent(totalProfitLossPercent)}
                    tone={resolveTone(totalProfitLoss)}
                  />
                </div>
              </section>

              <section className="portfolio-grid-row portfolio-grid-row--analytics">
                <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-allocation-panel portfolio-allocation-panel--dense">
                  <div className="panel-head portfolio-analytics-panel-head">
                    <div>
                      <p className="eyebrow">Dağılım</p>
                      <h3>Varlık Özeti ve Dağılımı</h3>
                    </div>
                    <div className="portfolio-view-toggle" role="tablist" aria-label="Dagilim gorunumu">
                      <button
                        type="button"
                        className={`portfolio-view-toggle-button${allocationView === "chart" ? " active" : ""}`}
                        onClick={() => setAllocationView("chart")}
                        aria-label="Pasta grafik görünümü"
                        title="Pasta grafik görünümü"
                      >
                        <ChartPie size={19} />
                      </button>
                      <button
                        type="button"
                        className={`portfolio-view-toggle-button${allocationView === "summary" ? " active" : ""}`}
                        onClick={() => setAllocationView("summary")}
                        aria-label="Liste görünümü"
                        title="Liste görünümü"
                      >
                        <List size={19} />
                      </button>
                    </div>
                  </div>
                  {allocationData.length === 0 && assetTypeBreakdown.length === 0 ? (
                    <EmptyState title={t("portfolio.allocationEmptyTitle")} description={t("portfolio.allocationEmptyDescription")} />
                  ) : allocationView === "summary" ? (
                    <div className="portfolio-allocation-summary-view portfolio-allocation-summary-view--scrollable">
                      <div className="portfolio-holding-type-list">
                        {holdings.map((holding, index) => (
                          <div key={`${holding.holdingId || holding.instrumentCode}-${index}`} className="portfolio-holding-type-row">
                            <div>
                              <strong>{formatInstrumentLabel(holding.instrumentCode)}</strong>
                              <span>{getAssetCategoryLabel(getAssetCategoryKey(holding.instrumentCode))}</span>
                            </div>
                            <strong>{formatCurrency(resolveHoldingValue(holding))}</strong>
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : (
                    <div className="portfolio-allocation-shell portfolio-allocation-shell--pro">
                      <div className="portfolio-allocation-chart-wrap">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie data={allocationData.slice(0, 6)} dataKey="currentValue" nameKey="displayName" outerRadius={76} innerRadius={48}>
                              {allocationData.slice(0, 6).map((entry, index) => (
                                <Cell key={`${entry.instrumentCode}-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
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
                      <div className="portfolio-allocation-legend portfolio-allocation-legend--rich">
                        {allocationData.slice(0, 5).map((entry, index) => (
                          <div key={`${entry.instrumentCode}-${index}`} className="portfolio-allocation-item portfolio-allocation-item--v3">
                            <div className="portfolio-allocation-label">
                              <span className="portfolio-color-dot" style={{ backgroundColor: CHART_COLORS[index % CHART_COLORS.length] }} />
                              <div className="portfolio-allocation-label-copy">
                                <strong className="portfolio-allocation-code">{entry.displayName}</strong>
                                <span>{formatCurrency(entry.currentValue)}</span>
                              </div>
                            </div>
                            <div className="portfolio-allocation-metrics">
                              <span className="portfolio-allocation-pct">{formatNumber(entry.weight, 1)}%</span>
                              <span className={getPnLTextClass(entry.profitLoss)}>
                                {entry.profitLoss == null ? "-" : formatCurrency(entry.profitLoss)}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </section>

                <section className="portfolio-heatmap-column">
                  <PortfolioHeatmap items={heatmapItems} />
                </section>
              </section>

              <section className="portfolio-grid-row portfolio-grid-row--performance">
                <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-performance-panel portfolio-performance-panel--dense">
                  <div className="panel-head portfolio-analytics-panel-head">
                    <div>
                      <p className="eyebrow">Performans</p>
                      <h3>{t("portfolio.historyTitle")}</h3>
                    </div>
                    <span className="summary-chip">Yakında veri serisi</span>
                  </div>
                  <div className="portfolio-performance-compact-empty">
                    <div className="portfolio-performance-ghost-chart" aria-hidden="true">
                      <span />
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                    <div className="portfolio-performance-empty-copy">
                      <strong>{t("portfolio.historyEmptyTitle")}</strong>
                      <p>{t("portfolio.historyEmptyDescription")}</p>
                    </div>
                  </div>
                </section>

                <div className="portfolio-ai-column">
                  <AiPortfolioCommentaryCard portfolio={selectedPortfolio} compact />
                </div>
              </section>

              <section className="portfolio-grid-row portfolio-grid-row--lists">
                <section className="panel-surface portfolio-side-card portfolio-watchlist-card-v4">
                  <div className="panel-head portfolio-side-card-head">
                    <div>
                      <p className="eyebrow">Takip</p>
                      <h3>Takip Listesi</h3>
                    </div>
                    <span className="summary-chip">{watchlistItems.length}</span>
                  </div>
                  <div className="portfolio-side-scroll">
                    <div className="portfolio-watchlist-mini">
                      {watchlistItems.length === 0 ? (
                        <div className="portfolio-inline-empty">Henüz takip verisi yok.</div>
                      ) : (
                        watchlistItems.map((item, index) => (
                          <div key={`${item.symbol}-${index}`} className="portfolio-watchlist-row">
                            <div>
                              <strong>{item.symbol}</strong>
                              <span>{item.price == null ? "Fiyat yok" : formatCurrency(item.price)}</span>
                            </div>
                            <div className="portfolio-watchlist-meta">
                              <span className={getPnLTextClass(item.changePercent)}>
                                {item.changePercent == null ? "-" : formatPercent(item.changePercent)}
                              </span>
                              <small>{item.status}</small>
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </section>

                <section className="panel-surface portfolio-management-panel portfolio-table-card portfolio-holdings-section portfolio-detail-panel portfolio-holdings-panel portfolio-holdings-panel--pro">
                  <div className="panel-head">
                    <div>
                      <p className="eyebrow">Varlıklar</p>
                      <h3>{t("portfolio.holdingsTitle")}</h3>
                    </div>
                    <span className="summary-chip">{t("common.rows", { count: formatNumber(holdings.length, 0) })}</span>
                  </div>

                  {holdings.length === 0 ? (
                    <EmptyState title={t("portfolio.emptyHoldingsTitle")} description={t("portfolio.emptyHoldingsDescription")} />
                  ) : (
                    <div className="portfolio-holdings-scroll portfolio-holdings-scroll--bounded">
                      <table className="portfolio-holdings-table portfolio-holdings-table--v3">
                        <thead>
                          <tr>
                            <th>{t("portfolio.table.asset")}</th>
                            <th>{t("portfolio.table.quantity")}</th>
                            <th>Ort. maliyet</th>
                            <th>{t("portfolio.table.currentPrice")}</th>
                            <th>{t("portfolio.table.currentValue")}</th>
                            <th>Günlük %</th>
                            <th>Toplam K/Z</th>
                            <th>Ağırlık</th>
                            <th>{t("portfolio.table.status")}</th>
                            <th>{t("portfolio.table.action")}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {holdings.map((holding, index) => {
                            const currentValue = resolveHoldingValue(holding);
                            const weight = totalValue > 0 ? (currentValue / totalValue) * 100 : null;
                            const typeLabel = getAssetCategoryLabel(getAssetCategoryKey(holding.instrumentCode));
                            const dailyPct = toMaybeNumber(holding.dailyChangePercent);
                            return (
                              <tr key={`${holding.holdingId || holding.instrumentCode}-${index}`}>
                                <td>
                                  <div className="portfolio-cell-stack">
                                    <strong>{formatInstrumentLabel(holding.instrumentCode)}</strong>
                                    <span className="muted">{typeLabel}</span>
                                  </div>
                                </td>
                                <td>{formatNumber(holding.quantity)}</td>
                                <td>{formatCurrency(holding.buyPrice)}</td>
                                <td>{holding.valuationAvailable ? formatCurrency(holding.currentPrice) : t("portfolio.priceMissing")}</td>
                                <td>{holding.valuationAvailable ? formatCurrency(holding.currentValue) : formatCurrency(currentValue)}</td>
                                <td className={dailyPct == null ? "" : getPnLCellClass(dailyPct)}>
                                  {dailyPct == null ? <span className="muted">-</span> : formatPercent(dailyPct)}
                                </td>
                                <td className={holding.valuationAvailable ? getPnLCellClass(holding.profitLoss) : undefined}>
                                  {holding.valuationAvailable ? (
                                    <div className="portfolio-cell-stack">
                                      <strong>{formatCurrency(holding.profitLoss)}</strong>
                                      <span className="muted">{formatPercent(holding.profitLossPercent)}</span>
                                    </div>
                                  ) : (
                                    <span className="muted">{t("portfolio.missingData")}</span>
                                  )}
                                </td>
                                <td>{weight == null ? <span className="muted">-</span> : `${formatNumber(weight, 1)}%`}</td>
                                <td>
                                  <span className={`portfolio-status-pill ${getPriceStatusClass(holding.priceStatus)}`}>
                                    {formatPriceStatus(holding.priceStatus, t)}
                                  </span>
                                </td>
                                <td>
                                  <div className="actions-row portfolio-holdings-actions">
                                    <button type="button" className="secondary-button" onClick={() => openEditHoldingModal(holding)}>
                                      {t("portfolio.update")}
                                    </button>
                                    <button type="button" className="danger-button" onClick={() => handleDeleteHolding(holding.holdingId)}>
                                      {t("portfolio.delete")}
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              </section>

              <div className="portfolio-activity-linkbar">
                <button type="button" className="portfolio-activity-link" onClick={() => setActivityModalOpen(true)}>
                  Son hareketler
                </button>
              </div>
            </>
          ) : null}
        </section>
      ) : null}

      {isCreatePortfolioModalOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={closeCreatePortfolioModal}>
          <div className="auth-modal portfolio-action-modal portfolio-create-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="portfolio-action-modal-head">
              <div>
                <p className="eyebrow">Portföy</p>
                <h3>Yeni Portföy Oluştur</h3>
              </div>
              <button type="button" className="secondary-button" onClick={closeCreatePortfolioModal}>
                {t("common.close")}
              </button>
            </div>

            <form className="instrument-action-form" onSubmit={handleCreatePortfolio}>
              <label className="portfolio-field">
                <span>{t("portfolio.name")}</span>
                <input
                  required
                  value={newPortfolio.portfolioName}
                  onChange={(event) => setNewPortfolio((current) => ({ ...current, portfolioName: event.target.value }))}
                  placeholder={t("portfolio.namePlaceholder")}
                />
              </label>
              <label className="portfolio-field">
                <span>{t("portfolio.visibility")}</span>
                <select
                  value={newPortfolio.visibilityStatus}
                  onChange={(event) => setNewPortfolio((current) => ({ ...current, visibilityStatus: event.target.value }))}
                >
                  <option value="PRIVATE">{t("portfolio.visibilityOptions.PRIVATE")}</option>
                  <option value="PUBLIC">{t("portfolio.visibilityOptions.PUBLIC")}</option>
                </select>
              </label>
              <div className="instrument-action-footer">
                <button type="submit">+ Yeni Portföy Oluştur</button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {isHoldingModalOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={closeHoldingModal}>
          <div className="auth-modal portfolio-action-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="portfolio-action-modal-head">
              <div>
                <p className="eyebrow">{t("portfolio.modalEyebrow")}</p>
                <h3>{editingHolding ? t("portfolio.modalEditTitle") : t("portfolio.modalAddTitle")}</h3>
              </div>
              <button type="button" className="secondary-button" onClick={closeHoldingModal}>
                {t("common.close")}
              </button>
            </div>

            <form className="instrument-action-form" onSubmit={handleSaveHolding}>
              <div className="portfolio-field" ref={instrumentSearchRef}>
                <span>{t("portfolio.instrumentCode")}</span>
                {editingHolding ? (
                  <input disabled value={formatInstrumentLabel(holdingForm.instrumentCode)} />
                ) : (
                  <div className="instrument-search-wrap">
                    <input
                      required={!holdingForm.instrumentCode}
                      value={instrumentSearch}
                      onChange={(event) => handleInstrumentSearchChange(event.target.value)}
                      onFocus={() => instrumentResults.length > 0 && setInstrumentSearchOpen(true)}
                      onBlur={() => setTimeout(() => setInstrumentSearchOpen(false), 150)}
                      placeholder={t("portfolio.instrumentSearchPlaceholder")}
                      autoComplete="off"
                    />
                    {holdingForm.instrumentCode && <span className="instrument-selected-badge">{formatInstrumentLabel(holdingForm.instrumentCode)}</span>}
                    {instrumentSearchOpen && (
                      <ul className="instrument-search-dropdown">
                        {instrumentResults.map((item) => (
                          <li key={item.code} className="instrument-search-item" onMouseDown={() => selectInstrument(item)}>
                            <strong>{item.code}</strong>
                            <span className="instrument-search-name">{item.name}</span>
                            <span className="instrument-search-type">{item.type}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </div>

              <div className="instrument-action-grid">
                <label className="portfolio-field">
                  <span>{t("portfolio.quantity")}</span>
                  <input
                    required
                    type="number"
                    step="any"
                    min="0.0001"
                    value={holdingForm.quantity}
                    onChange={(event) => setHoldingForm((current) => ({ ...current, quantity: event.target.value }))}
                  />
                </label>
                <label className="portfolio-field">
                  <span>{t("portfolio.purchaseDate")}</span>
                  <input
                    type="date"
                    value={holdingForm.purchaseDate}
                    max={new Date().toISOString().split("T")[0]}
                    onChange={(event) => handlePurchaseDateChange(event.target.value)}
                    disabled={!holdingForm.instrumentCode}
                  />
                </label>
              </div>

              <label className="portfolio-field">
                <span>
                  {t("portfolio.buyPrice")}
                  {priceAutoFetched && <span className="portfolio-price-auto-badge">{t("portfolio.priceAutoFetched")}</span>}
                  {fetchingPrice && <span className="portfolio-price-auto-badge">{t("portfolio.fetchingPrice")}</span>}
                </span>
                <input
                  required
                  type="number"
                  step="any"
                  min="0.0001"
                  value={holdingForm.buyPrice}
                  readOnly={priceAutoFetched}
                  onChange={(event) => {
                    if (!priceAutoFetched) {
                      setHoldingForm((current) => ({ ...current, buyPrice: event.target.value }));
                    }
                  }}
                  placeholder={fetchingPrice ? t("portfolio.fetchingPrice") : ""}
                />
                {priceFetchError && <span className="portfolio-price-fetch-error">{priceFetchError}</span>}
              </label>

              <div className="instrument-action-footer">
                <button type="submit" disabled={savingHolding || !holdingForm.instrumentCode}>
                  {savingHolding ? t("portfolio.saving") : editingHolding ? t("portfolio.update") : t("portfolio.addAsset")}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {isActivityModalOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setActivityModalOpen(false)}>
          <div className="auth-modal portfolio-activity-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="portfolio-action-modal-head">
              <div>
                <p className="eyebrow">AkÄ±ÅŸ</p>
                <h3>Son Hareketler</h3>
              </div>
              <button type="button" className="secondary-button" onClick={() => setActivityModalOpen(false)}>
                {t("common.close")}
              </button>
            </div>

            <div className="portfolio-activity-modal-body">
              {activityItems.length === 0 ? (
                <div className="portfolio-inline-empty">HenÃ¼z hareket yok.</div>
              ) : (
                activityItems.map((item, index) => (
                  <div key={`${item.title}-${index}`} className={`portfolio-activity-row is-${item.tone}`}>
                    <div>
                      <strong>{item.title}</strong>
                      <span>{item.detail}</span>
                    </div>
                    <small>{formatDateTime(item.timestamp)}</small>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function PortfolioWidgetFrame({ children, editing, title }) {
  return (
    <div className="portfolio-widget-shell">
      {editing ? (
        <div className="portfolio-widget-toolbar">
          <span className="portfolio-widget-drag-handle"><GripVertical size={15} /></span>
          <span className="portfolio-widget-toolbar-label">{title}</span>
        </div>
      ) : null}
      {children}
    </div>
  );
}

function PortfolioMetricCard({ label, value, subvalue = null, tone = "neutral" }) {
  return (
    <article className={`portfolio-metric-card is-${tone}`}>
      <span className="portfolio-metric-label">{label}</span>
      <strong className="portfolio-metric-value">{value}</strong>
      {subvalue ? <span className="portfolio-metric-subvalue">{subvalue}</span> : <span className="portfolio-metric-subvalue">&nbsp;</span>}
    </article>
  );
}

function normalizeCode(value) {
  if (value == null) {
    return "";
  }

  const rawValue = String(value).trim();
  if (rawValue.toUpperCase().startsWith("TCMB:")) {
    return rawValue.toUpperCase();
  }

  return rawValue.replace(/[^A-Za-z0-9:]/g, "").toUpperCase();
}

function formatInstrumentLabel(value) {
  return normalizeCode(value) === INTERNAL_CASH_CODE ? INTERNAL_CASH_LABEL : value || "-";
}

function formatInstrumentSearchValue(instrument) {
  if (!instrument) {
    return "";
  }
  if (normalizeCode(instrument.code) === INTERNAL_CASH_CODE) {
    return instrument.name || INTERNAL_CASH_LABEL;
  }
  return instrument.code || instrument.name || "";
}

function toNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function toMaybeNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function getSummaryPnLWrapClass(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "portfolio-kpi-wrap portfolio-kpi-wrap--flat";
  }
  return numeric > 0 ? "portfolio-kpi-wrap portfolio-kpi-wrap--up" : "portfolio-kpi-wrap portfolio-kpi-wrap--down";
}

function getPnLCellClass(profitLoss) {
  const numeric = Number(profitLoss);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "portfolio-pl-cell is-flat";
  }
  return numeric > 0 ? "portfolio-pl-cell is-up" : "portfolio-pl-cell is-down";
}

function getPnLTextClass(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "portfolio-tonal-text is-flat";
  }
  return numeric > 0 ? "portfolio-tonal-text is-up" : "portfolio-tonal-text is-down";
}

function getPriceStatusClass(priceStatus) {
  switch (priceStatus) {
    case "LIVE":
      return "is-live";
    case "CACHED":
      return "is-cached";
    case "STALE":
      return "is-stale";
    default:
      return "is-unavailable";
  }
}

function formatPriceStatus(value, t) {
  return {
    LIVE: t("portfolio.status.LIVE"),
    CACHED: t("portfolio.status.CACHED"),
    STALE: t("portfolio.status.STALE"),
    UNAVAILABLE: t("portfolio.status.UNAVAILABLE"),
  }[value] ?? t("portfolio.status.UNAVAILABLE");
}

function formatVisibilityStatus(value, t) {
  return {
    PRIVATE: t("portfolio.visibilityOptions.PRIVATE"),
    PUBLIC: t("portfolio.visibilityOptions.PUBLIC"),
  }[value] ?? (value || "-");
}

function getHoldingActivityTimestamp(holding) {
  const timestamp = holding?.updatedAt || holding?.createdAt;
  return timestamp ? new Date(timestamp).getTime() : 0;
}

function resolveHoldingValue(holding) {
  if (holding?.valuationAvailable && Number.isFinite(Number(holding.currentValue))) {
    return Number(holding.currentValue);
  }
  return toNumber(holding?.buyPrice) * toNumber(holding?.quantity);
}

function resolveTone(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "neutral";
  }
  return numeric > 0 ? "positive" : "negative";
}

function getAssetCategoryKey(instrumentCode) {
  const code = normalizeCode(instrumentCode);
  if (!code) {
    return "OTHER";
  }
  if (code === INTERNAL_CASH_CODE) {
    return "CASH";
  }
  if (code.startsWith("TCMB:") || (code.endsWith("TRY") && code.length >= 6)) {
    return "FX";
  }
  if (code.includes("XAU") || code.includes("GOLD") || code.includes("ALTIN") || code.includes("ONS") || code.includes("GUMUS")) {
    return "COMMODITY";
  }
  if (code.endsWith("USDT") || code.endsWith("USD") || code.endsWith("BTC") || code.endsWith("ETH")) {
    return "CRYPTO";
  }
  if (/^[A-Z]{5}$/.test(code)) {
    return "STOCK";
  }
  if (/^[A-Z]{3,4}$/.test(code)) {
    return "FUND";
  }
  return "OTHER";
}

function getAssetCategoryLabel(key) {
  return {
    CASH: "Nakit",
    STOCK: "Hisse",
    FUND: "Fon",
    CRYPTO: "Kripto",
    FX: "DÃ¶viz",
    COMMODITY: "AltÄ±n / Emtia",
    OTHER: "DiÄŸer",
  }[key] ?? "DiÄŸer";
}

function buildAssetCategorySummary(holdings, totalValue) {
  const groups = new Map([
    ["CASH", { key: "CASH", label: "Nakit", count: 0, value: 0 }],
    ["STOCK", { key: "STOCK", label: "Hisse", count: 0, value: 0 }],
    ["FUND", { key: "FUND", label: "Fon", count: 0, value: 0 }],
    ["CRYPTO", { key: "CRYPTO", label: "Kripto", count: 0, value: 0 }],
    ["FX", { key: "FX", label: "DÃ¶viz", count: 0, value: 0 }],
    ["COMMODITY", { key: "COMMODITY", label: "AltÄ±n / Emtia", count: 0, value: 0 }],
    ["OTHER", { key: "OTHER", label: "DiÄŸer", count: 0, value: 0 }],
  ]);

  holdings.forEach((holding) => {
    const key = getAssetCategoryKey(holding.instrumentCode);
    const bucket = groups.get(key) ?? groups.get("OTHER");
    bucket.count += 1;
    bucket.value += resolveHoldingValue(holding);
  });

  return [...groups.values()].map((item) => ({
    ...item,
    weight: totalValue > 0 ? (item.value / totalValue) * 100 : 0,
  }));
}





