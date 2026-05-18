import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { GripVertical, LayoutGrid, RotateCcw } from "lucide-react";
import { Pie, PieChart, Cell, ResponsiveContainer, Tooltip } from "recharts";
import { Responsive, WidthProvider } from "react-grid-layout/legacy";
import {
  createPortfolio,
  createPortfolioHolding,
  deletePortfolioHolding,
  getPortfolioDetails,
  getUserPortfolios,
  updatePortfolioHolding,
} from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import { getPriceOnDate, searchInstruments } from "../api/marketApi";
import { useAuth } from "../auth/AuthContext";
import AiPortfolioCommentaryCard from "../components/ai/AiPortfolioCommentaryCard";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import SummaryCard from "../components/common/SummaryCard";
import useToast from "../hooks/useToast";
import { useTheme } from "../theme/ThemeContext";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";
import "react-grid-layout/css/styles.css";
import "react-resizable/css/styles.css";

const CHART_COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];
const EMPTY_HOLDINGS = [];
const ResponsiveGridLayout = WidthProvider(Responsive);
const PORTFOLIO_WIDGET_LAYOUTS_STORAGE_KEY = "fp:portfolio:widget-layouts:v1";
const PORTFOLIO_WIDGET_LAYOUTS_DEFAULT = {
  lg: [
    { i: "kpi", x: 0, y: 0, w: 12, h: 4, minW: 6, minH: 4 },
    { i: "performance", x: 0, y: 4, w: 7, h: 5, minW: 4, minH: 4 },
    { i: "allocation", x: 7, y: 4, w: 5, h: 5, minW: 4, minH: 4 },
    { i: "ai", x: 0, y: 9, w: 12, h: 4, minW: 5, minH: 4 },
    { i: "holdings", x: 0, y: 13, w: 12, h: 8, minW: 7, minH: 6 },
  ],
  md: [
    { i: "kpi", x: 0, y: 0, w: 10, h: 4, minW: 6, minH: 4 },
    { i: "performance", x: 0, y: 4, w: 10, h: 5, minW: 5, minH: 4 },
    { i: "allocation", x: 0, y: 9, w: 10, h: 5, minW: 5, minH: 4 },
    { i: "ai", x: 0, y: 14, w: 10, h: 4, minW: 5, minH: 4 },
    { i: "holdings", x: 0, y: 18, w: 10, h: 8, minW: 6, minH: 6 },
  ],
  sm: [
    { i: "kpi", x: 0, y: 0, w: 1, h: 4, minH: 4 },
    { i: "performance", x: 0, y: 4, w: 1, h: 5, minH: 4 },
    { i: "allocation", x: 0, y: 9, w: 1, h: 5, minH: 4 },
    { i: "ai", x: 0, y: 14, w: 1, h: 4, minH: 4 },
    { i: "holdings", x: 0, y: 18, w: 1, h: 8, minH: 6 },
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
  const [newPortfolio, setNewPortfolio] = useState({ portfolioName: "", visibilityStatus: "PRIVATE" });
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
      await loadPortfolios(created?.portfolioId ?? null);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.createError")));
    }
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
    setInstrumentSearch(holding.instrumentCode || "");
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
        const results = await searchInstruments(value);
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
    setInstrumentSearch(instrument.code);
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
  const plAmount = toNumber(summary?.profitLoss ?? summary?.totalProfitLoss);
  const plPct = toNumber(summary?.profitLossPercent);
  const holdings = selectedPortfolio?.holdings ?? EMPTY_HOLDINGS;
  const allocationData = useMemo(() => {
    const totalValue = holdings.reduce((sum, holding) => sum + toNumber(holding.currentValue), 0);
    if (totalValue <= 0) {
      return [];
    }

    return holdings.map((holding) => ({
      instrumentCode: holding.instrumentCode,
      value: toNumber(holding.currentValue),
      percentage: totalValue > 0 ? (toNumber(holding.currentValue) / totalValue) * 100 : 0,
    }));
  }, [holdings]);
  const widgetTitles = useMemo(() => ({
    kpi: t("portfolio.summary.totalCost"),
    performance: t("portfolio.historyTitle"),
    allocation: t("portfolio.allocationTitle"),
    ai: "AI Portfoy Yorumu",
    holdings: t("portfolio.holdingsTitle"),
  }), [t]);

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
    <div className="portfolio-management-shell portfolio-page-v2">
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}

      <section className="portfolio-management-grid">
        <div className="portfolio-list-sticky-wrap">
          <aside className="panel-surface portfolio-management-sidebar portfolio-list-rail">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("portfolio.listEyebrow")}</p>
                <h3>{t("portfolio.listTitle")}</h3>
              </div>
              <span className="summary-chip">{t("common.records", { count: portfolios.length })}</span>
            </div>

            <form className="portfolio-create-panel portfolio-create-panel--compact" onSubmit={handleCreatePortfolio}>
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
              <button type="submit">{t("portfolio.create")}</button>
            </form>

            {loadingList ? <LoadingSpinner label={t("portfolio.loadingList")} /> : null}
            {!loadingList && portfolios.length === 0 ? (
              <EmptyState title={t("portfolio.emptyListTitle")} description={t("portfolio.emptyListDescription")} />
            ) : null}

            {!loadingList && portfolios.length > 0 ? (
              <div className="portfolio-selector-list">
                {portfolios.map((portfolio) => (
                  <button
                    key={portfolio.portfolioId}
                    type="button"
                    className={`portfolio-selector-card${portfolio.portfolioId === selectedPortfolioId ? " active" : ""}`}
                    onClick={() => setSelectedPortfolioId(portfolio.portfolioId)}
                  >
                    <strong>{portfolio.portfolioName}</strong>
                    <span>{formatVisibilityStatus(portfolio.visibilityStatus, t)}</span>
                    <small>{formatDateTime(portfolio.createdAt)}</small>
                  </button>
                ))}
              </div>
            ) : null}
          </aside>
        </div>

        <div className={`portfolio-management-main${!loadingDetail && selectedPortfolio ? " portfolio-detail-stack" : ""}`}>
          {loadingDetail ? <LoadingSpinner label={t("portfolio.loadingDetail")} /> : null}

          {!loadingDetail && !selectedPortfolio ? (
            <section className="panel-surface portfolio-management-panel">
              <EmptyState title={t("portfolio.emptySelectionTitle")} description={t("portfolio.emptySelectionDescription")} />
            </section>
          ) : null}

          {!loadingDetail && selectedPortfolio ? (
            <>
              <header className="portfolio-detail-header">
                <div className="portfolio-detail-header-main">
                  <p className="eyebrow">{t("portfolio.selectedEyebrow")}</p>
                  <h2 className="portfolio-detail-title">{selectedPortfolio.portfolioName || "-"}</h2>
                  <p className="portfolio-detail-meta">
                    {formatVisibilityStatus(selectedPortfolio.visibilityStatus || "PRIVATE", t)} · {t("portfolio.createdAt", { value: formatDateTime(selectedPortfolio.createdAt) })}
                  </p>
                </div>
                <div className="actions-row portfolio-detail-header-actions">
                  {canEditWidgetLayout ? (
                    <>
                      <button
                        type="button"
                        className={`secondary-button portfolio-widget-action${isWidgetEditMode ? " active" : ""}`}
                        onClick={() => setWidgetEditMode((current) => !current)}
                        title="Widget duzenini duzenle"
                        aria-label="Widget duzenini duzenle"
                      >
                        <LayoutGrid size={16} />
                      </button>
                      {isWidgetEditMode ? (
                        <button
                          type="button"
                          className="secondary-button portfolio-widget-action"
                          onClick={handleResetWidgetLayouts}
                          title="Widget duzenini sifirla"
                          aria-label="Widget duzenini sifirla"
                        >
                          <RotateCcw size={16} />
                        </button>
                      ) : null}
                    </>
                  ) : null}
                  <button type="button" onClick={openAddHoldingModal}>
                    {t("portfolio.addAsset")}
                  </button>
                </div>
              </header>

              <ResponsiveGridLayout
                className={`portfolio-widget-grid${isWidgetEditMode ? " is-editing" : ""}`}
                layouts={widgetLayouts}
                breakpoints={{ lg: 1400, md: 1040, sm: 0 }}
                cols={{ lg: 12, md: 10, sm: 1 }}
                rowHeight={76}
                margin={[18, 18]}
                containerPadding={[0, 0]}
                compactType="vertical"
                isDraggable={isWidgetEditMode && canEditWidgetLayout}
                isResizable={isWidgetEditMode && canEditWidgetLayout}
                draggableHandle=".portfolio-widget-drag-handle"
                onLayoutChange={handleWidgetLayoutChange}
                onBreakpointChange={setWidgetBreakpoint}
              >
                <div key="kpi" className="portfolio-widget-slot">
                  <div className="portfolio-widget-shell portfolio-widget-shell--kpi">
                    {isWidgetEditMode && canEditWidgetLayout ? (
                      <div className="portfolio-widget-toolbar">
                        <span className="portfolio-widget-drag-handle"><GripVertical size={15} /></span>
                        <span className="portfolio-widget-toolbar-label">{widgetTitles.kpi}</span>
                      </div>
                    ) : null}
                    <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-kpi-panel">
                      <div className="portfolio-kpi-grid">
                        <div className="portfolio-kpi-wrap portfolio-kpi-wrap--stat">
                          <SummaryCard title={t("portfolio.summary.totalCost")} value={formatCurrency(summary?.totalCost)} subtitle={t("portfolio.summary.totalCostSubtitle")} tone="cool" />
                        </div>
                        <div className="portfolio-kpi-wrap portfolio-kpi-wrap--stat">
                          <SummaryCard title={t("portfolio.summary.currentValue")} value={formatCurrency(summary?.currentValue ?? summary?.totalCurrentValue)} subtitle={t("portfolio.summary.currentValueSubtitle")} tone="cool" />
                        </div>
                        <div className={getSummaryPnLWrapClass(plAmount)}>
                          <SummaryCard title={t("portfolio.summary.profitLossAmount")} value={formatCurrency(summary?.profitLoss ?? summary?.totalProfitLoss)} subtitle={t("portfolio.summary.profitLossAmountSubtitle")} tone={plAmount >= 0 ? "cool" : "warm"} />
                        </div>
                        <div className={getSummaryPnLWrapClass(plPct)}>
                          <SummaryCard title={t("portfolio.summary.profitLossPercent")} value={formatPercent(summary?.profitLossPercent)} subtitle={t("portfolio.summary.profitLossPercentSubtitle")} tone={plPct >= 0 ? "cool" : "warm"} />
                        </div>
                      </div>
                    </section>
                  </div>
                </div>

                <div key="performance" className="portfolio-widget-slot">
                  <div className="portfolio-widget-shell portfolio-widget-shell--performance">
                    {isWidgetEditMode && canEditWidgetLayout ? (
                      <div className="portfolio-widget-toolbar">
                        <span className="portfolio-widget-drag-handle"><GripVertical size={15} /></span>
                        <span className="portfolio-widget-toolbar-label">{widgetTitles.performance}</span>
                      </div>
                    ) : null}
                    <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-performance-panel">
                      <div className="panel-head portfolio-analytics-panel-head">
                        <div>
                          <p className="eyebrow">{t("portfolio.historyEyebrow")}</p>
                          <h3>{t("portfolio.historyTitle")}</h3>
                        </div>
                      </div>
                      <div className="portfolio-performance-body">
                        <div className="portfolio-history-empty-card portfolio-history-empty-card--fill">
                          <EmptyState title={t("portfolio.historyEmptyTitle")} description={t("portfolio.historyEmptyDescription")} />
                        </div>
                      </div>
                    </section>
                  </div>
                </div>

                <div key="allocation" className="portfolio-widget-slot">
                  <div className="portfolio-widget-shell portfolio-widget-shell--allocation">
                    {isWidgetEditMode && canEditWidgetLayout ? (
                      <div className="portfolio-widget-toolbar">
                        <span className="portfolio-widget-drag-handle"><GripVertical size={15} /></span>
                        <span className="portfolio-widget-toolbar-label">{widgetTitles.allocation}</span>
                      </div>
                    ) : null}
                    <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-allocation-panel">
                      <div className="panel-head portfolio-analytics-panel-head">
                        <div>
                          <p className="eyebrow">{t("portfolio.allocationEyebrow")}</p>
                          <h3>{t("portfolio.allocationTitle")}</h3>
                        </div>
                      </div>
                      {allocationData.length === 0 ? (
                        <EmptyState title={t("portfolio.allocationEmptyTitle")} description={t("portfolio.allocationEmptyDescription")} />
                      ) : (
                        <div className="portfolio-allocation-shell">
                          <div className="portfolio-allocation-chart-wrap">
                            <ResponsiveContainer width="100%" height="100%">
                              <PieChart>
                                <Pie data={allocationData} dataKey="value" nameKey="instrumentCode" outerRadius={58} innerRadius={34}>
                                  {allocationData.map((entry, index) => (
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
                          <div className="portfolio-allocation-legend">
                            {allocationData.map((entry, index) => (
                              <div key={`${entry.instrumentCode}-${index}`} className="portfolio-allocation-item portfolio-allocation-item--v2">
                                <div className="portfolio-allocation-label">
                                  <span className="portfolio-color-dot" style={{ backgroundColor: CHART_COLORS[index % CHART_COLORS.length] }} />
                                  <strong className="portfolio-allocation-code">{entry.instrumentCode}</strong>
                                </div>
                                <div className="portfolio-allocation-metrics" aria-label={`${entry.instrumentCode} allocation`}>
                                  <span className="portfolio-allocation-pct">{formatNumber(entry.percentage, 2)}%</span>
                                  <span className="portfolio-allocation-amt">{formatCurrency(entry.value)}</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </section>
                  </div>
                </div>

                <div key="ai" className="portfolio-widget-slot">
                  <div className="portfolio-widget-shell portfolio-widget-shell--ai">
                    {isWidgetEditMode && canEditWidgetLayout ? (
                      <div className="portfolio-widget-toolbar">
                        <span className="portfolio-widget-drag-handle"><GripVertical size={15} /></span>
                        <span className="portfolio-widget-toolbar-label">{widgetTitles.ai}</span>
                      </div>
                    ) : null}
                    <AiPortfolioCommentaryCard portfolio={selectedPortfolio} />
                  </div>
                </div>

                <div key="holdings" className="portfolio-widget-slot">
                  <div className="portfolio-widget-shell portfolio-widget-shell--holdings">
                    {isWidgetEditMode && canEditWidgetLayout ? (
                      <div className="portfolio-widget-toolbar">
                        <span className="portfolio-widget-drag-handle"><GripVertical size={15} /></span>
                        <span className="portfolio-widget-toolbar-label">{widgetTitles.holdings}</span>
                      </div>
                    ) : null}
                    <section className="panel-surface portfolio-management-panel portfolio-table-card portfolio-holdings-section portfolio-detail-panel portfolio-holdings-panel">
                      <div className="panel-head">
                        <div>
                          <p className="eyebrow">{t("portfolio.holdingsEyebrow")}</p>
                          <h3>{t("portfolio.holdingsTitle")}</h3>
                        </div>
                        <span className="summary-chip">{t("common.rows", { count: formatNumber(holdings.length, 0) })}</span>
                      </div>

                      {holdings.length === 0 ? (
                        <EmptyState title={t("portfolio.emptyHoldingsTitle")} description={t("portfolio.emptyHoldingsDescription")} />
                      ) : (
                        <div className="portfolio-holdings-scroll">
                          <table className="portfolio-holdings-table">
                            <thead>
                              <tr>
                                <th>{t("portfolio.table.asset")}</th>
                                <th>{t("portfolio.table.quantity")}</th>
                                <th>{t("portfolio.table.cost")}</th>
                                <th>{t("portfolio.table.currentPrice")}</th>
                                <th>{t("portfolio.table.currentValue")}</th>
                                <th>{t("portfolio.table.profitLoss")}</th>
                                <th>{t("portfolio.table.status")}</th>
                                <th>{t("portfolio.table.updatedAt")}</th>
                                <th>{t("portfolio.table.action")}</th>
                              </tr>
                            </thead>
                            <tbody>
                              {holdings.map((holding, index) => (
                                <tr key={`${holding.holdingId || holding.instrumentCode}-${index}`}>
                                  <td>
                                    <div className="portfolio-cell-stack">
                                      <strong>{holding.instrumentCode || "-"}</strong>
                                      <span className="muted">{t("portfolio.assetNumber", { index: index + 1 })}</span>
                                    </div>
                                  </td>
                                  <td>{formatNumber(holding.quantity)}</td>
                                  <td>{formatCurrency(holding.buyPrice)}</td>
                                  <td>{holding.valuationAvailable ? formatCurrency(holding.currentPrice) : t("portfolio.priceMissing")}</td>
                                  <td>{holding.valuationAvailable ? formatCurrency(holding.currentValue) : t("portfolio.missingData")}</td>
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
                                  <td>
                                    <span className={`portfolio-status-pill ${getPriceStatusClass(holding.priceStatus)}`}>
                                      {formatPriceStatus(holding.priceStatus, t)}
                                    </span>
                                  </td>
                                  <td>{formatDateTime(holding.lastPriceUpdateTime)}</td>
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
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </section>
                  </div>
                </div>
              </ResponsiveGridLayout>
            </>
          ) : null}
        </div>
      </section>

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
                  <input disabled value={holdingForm.instrumentCode} />
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
                    {holdingForm.instrumentCode && (
                      <span className="instrument-selected-badge">{holdingForm.instrumentCode}</span>
                    )}
                    {instrumentSearchOpen && (
                      <ul className="instrument-search-dropdown">
                        {instrumentResults.map((item) => (
                          <li
                            key={item.code}
                            className="instrument-search-item"
                            onMouseDown={() => selectInstrument(item)}
                          >
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
    </div>
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

  return rawValue.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function toNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
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
