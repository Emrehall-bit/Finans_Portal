import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, ArrowUpDown, ChartPie, Check, Filter, GripVertical, ShieldAlert, Sparkles, X } from "lucide-react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import {
  createPortfolio,
  createPortfolioHolding,
  deletePortfolioHolding,
  updatePortfolioHolding,
} from "../api/portfolioApi";
import { portfolioKeys } from "../api/queryKeys";

import { getPortfolioAiAnalysis } from "../api/aiApi";
import { getMarketQuotesBySymbols, getPriceOnDate, searchInstruments } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import useToast from "../hooks/useToast";
import { usePortfolioDetails, usePortfolioRisk, useUserPortfolios } from "../hooks/usePortfolioQueries";
import PortfolioPerformancePanel from "../components/portfolio/PortfolioPerformancePanel";
import { useTheme } from "../theme/ThemeContext";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";
import { formatInstrumentLabel } from "../utils/instrumentUtils";
import "react-grid-layout/css/styles.css";
import "react-resizable/css/styles.css";

const CHART_COLORS = ["#2563eb", "#0f9d58", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];
const EMPTY_HOLDINGS = [];
const INTERNAL_CASH_CODE = "TRY";

export default function PortfolioPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const { chartTheme } = useTheme();
  const { formatAmount, currency } = useCurrency();
  const { toast, showToast } = useToast();
  const queryClient = useQueryClient();
  const location = useLocation();
  const navPortfolioId = location.state?.portfolioId ?? null;
  const [selectedPortfolioId, setSelectedPortfolioId] = useState(null);
  const [error, setError] = useState("");
  const [newPortfolio, setNewPortfolio] = useState({ portfolioName: "" });
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
  const [isActivityModalOpen, setActivityModalOpen] = useState(false);
  const [holdingsFilterKey, setHoldingsFilterKey] = useState("ALL");
  const [holdingsSortKey, setHoldingsSortKey] = useState("WEIGHT");
  const [isAiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [isAiDrawerVisible, setAiDrawerVisible] = useState(false);
  const [aiAnalysisData, setAiAnalysisData] = useState(null);
  const [aiAnalysisLoading, setAiAnalysisLoading] = useState(false);
  const [aiAnalysisError, setAiAnalysisError] = useState("");
  const instrumentSearchRef = useRef(null);
  const searchDebounceRef = useRef(null);

  // ¦¦ React Query data fetching ¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦¦
  const { data: portfolios = [], isLoading: loadingList } = useUserPortfolios(userId);
  const { data: selectedPortfolio = null, isLoading: loadingDetail } = usePortfolioDetails(selectedPortfolioId);
  const { data: riskSummary = null } = usePortfolioRisk(selectedPortfolioId);

  // Initialize selectedPortfolioId from portfolio list
  useEffect(() => {
    if (!portfolios.length) {
      setSelectedPortfolioId(null);
      return;
    }
    setSelectedPortfolioId((current) => {
      if (current && portfolios.some((p) => p.portfolioId === current)) return current;
      if (navPortfolioId && portfolios.some((p) => p.portfolioId === navPortfolioId)) return navPortfolioId;
      return portfolios[0].portfolioId;
    });
  }, [portfolios, navPortfolioId]);


  const openAiDrawer = useCallback(() => {
    setAiDrawerVisible(true);
    window.requestAnimationFrame(() => setAiDrawerOpen(true));
  }, []);

  const closeAiDrawer = useCallback(() => {
    setAiDrawerOpen(false);
  }, []);


  useEffect(() => {
    if (!isAiDrawerVisible) {
      return undefined;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    function handleKeyDown(event) {
      if (event.key === "Escape") {
        closeAiDrawer();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [closeAiDrawer, isAiDrawerVisible]);

  useEffect(() => {
    if (!isAiDrawerVisible || !selectedPortfolio?.portfolioId) {
      return;
    }

    let cancelled = false;

    async function loadAiAnalysis() {
      try {
        setAiAnalysisLoading(true);
        setAiAnalysisError("");
        const response = await getPortfolioAiAnalysis(selectedPortfolio.portfolioId);
        if (!cancelled) {
          setAiAnalysisData(response ?? null);
        }
      } catch (requestError) {
        if (!cancelled) {
          setAiAnalysisData(null);
          setAiAnalysisError(extractErrorMessage(requestError, t("portfolio.aiAnalysisError")));
        }
      } finally {
        if (!cancelled) {
          setAiAnalysisLoading(false);
        }
      }
    }

    loadAiAnalysis();
    return () => {
      cancelled = true;
    };
  }, [isAiDrawerVisible, selectedPortfolio?.portfolioId]);

  useEffect(() => {
    if (isAiDrawerOpen) {
      return undefined;
    }

    if (!isAiDrawerVisible) {
      return undefined;
    }

    const timer = window.setTimeout(() => {
      setAiDrawerVisible(false);
    }, 220);

    return () => window.clearTimeout(timer);
  }, [isAiDrawerOpen, isAiDrawerVisible]);


  async function handleCreatePortfolio(event) {
    event.preventDefault();
    try {
      setError("");
      const created = await createPortfolio(userId, newPortfolio);
      showToast("success", t("portfolio.createSuccess"));
      setNewPortfolio({ portfolioName: "" });
      setCreatePortfolioModalOpen(false);
      if (created?.portfolioId) setSelectedPortfolioId(created.portfolioId);
      queryClient.invalidateQueries({ queryKey: portfolioKeys.byUser(userId) });
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.createError")));
    }
  }

  function openCreatePortfolioModal() {
    setCreatePortfolioModalOpen(true);
  }

  function closeCreatePortfolioModal() {
    setCreatePortfolioModalOpen(false);
    setNewPortfolio({ portfolioName: "" });
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
        const investableResults = results.filter((item) => String(item?.type || item?.instrumentType || "").toUpperCase() !== "INDEX");
        setInstrumentResults(investableResults);
        setInstrumentSearchOpen(investableResults.length > 0);
      } catch {
        setInstrumentResults([]);
        setInstrumentSearchOpen(false);
      }
    }, 300);
  }, []);

  function selectInstrument(instrument) {
    if (String(instrument?.type || instrument?.instrumentType || "").toUpperCase() === "INDEX") {
      return;
    }
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
          purchaseDate: payload.purchaseDate,
        });
        if (Array.isArray(editingHolding?.sourceHoldingIds) && editingHolding.sourceHoldingIds.length > 1) {
          const redundantIds = editingHolding.sourceHoldingIds.slice(1);
          await Promise.all(redundantIds.map((holdingId) => deletePortfolioHolding(selectedPortfolio.portfolioId, holdingId)));
        }
        showToast("success", t("portfolio.holdingUpdateSuccess"));
      } else {
        await createPortfolioHolding(selectedPortfolio.portfolioId, payload);
        showToast("success", t("portfolio.holdingAddSuccess"));
      }

      closeHoldingModal();
      queryClient.invalidateQueries({ queryKey: portfolioKeys.details(selectedPortfolio.portfolioId) });
      queryClient.invalidateQueries({ queryKey: portfolioKeys.performance(selectedPortfolio.portfolioId) });
      queryClient.invalidateQueries({ queryKey: portfolioKeys.risk(selectedPortfolio.portfolioId) });
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.holdingSaveError")));
    } finally {
      setSavingHolding(false);
    }
  }

  async function handleDeleteHolding(holding) {
    if (!selectedPortfolio?.portfolioId) {
      return;
    }

    try {
      setError("");
      const ids = Array.isArray(holding?.sourceHoldingIds) && holding.sourceHoldingIds.length > 0 ? holding.sourceHoldingIds : [holding?.holdingId];
      await Promise.all(ids.filter(Boolean).map((holdingId) => deletePortfolioHolding(selectedPortfolio.portfolioId, holdingId)));
      showToast("success", t("portfolio.holdingDeleteSuccess"));
      queryClient.invalidateQueries({ queryKey: portfolioKeys.details(selectedPortfolio.portfolioId) });
      queryClient.invalidateQueries({ queryKey: portfolioKeys.performance(selectedPortfolio.portfolioId) });
      queryClient.invalidateQueries({ queryKey: portfolioKeys.risk(selectedPortfolio.portfolioId) });
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
  const aggregatedHoldings = holdings;
  const holdingQuoteSymbols = useMemo(
    () => [...new Set(
      aggregatedHoldings
        .map((holding) => normalizeCode(holding?.instrumentCode))
        .filter((code) => code && code !== INTERNAL_CASH_CODE),
    )],
    [aggregatedHoldings],
  );
  const { data: holdingQuotes = [] } = useQuery({
    queryKey: ["portfolio", "holding-quotes", selectedPortfolioId, holdingQuoteSymbols],
    queryFn: () => getMarketQuotesBySymbols(holdingQuoteSymbols),
    enabled: holdingQuoteSymbols.length > 0,
    staleTime: 30_000,
  });
  const holdingQuoteByCode = useMemo(() => buildHoldingQuoteMap(holdingQuotes), [holdingQuotes]);
  const dailyPerformance = aggregatedHoldings.reduce(
    (acc, holding) => {
      const holdingDaily = resolveHoldingDailyProfitLoss(holding, holdingQuoteByCode);
      return holdingDaily == null
        ? acc
        : { value: acc.value + holdingDaily, hasData: true };
    },
    { value: 0, hasData: false },
  );
  const dailyProfitLoss = dailyPerformance.hasData ? dailyPerformance.value : null;
  const previousValue = dailyProfitLoss != null ? totalValue - dailyProfitLoss : null;
  const dailyProfitLossPercent = previousValue > 0 ? (dailyProfitLoss / previousValue) * 100 : null;

  const allocationData = useMemo(() => {
    const items = aggregatedHoldings
      .map((holding) => {
        const currentValue = resolveHoldingValue(holding);
        const weight = totalValue > 0 ? (currentValue / totalValue) * 100 : 0;
        return {
          instrumentCode: holding.instrumentCode,
          displayName: formatInstrumentLabel(holding.instrumentCode),
          assetCategoryKey: getAssetCategoryKey(holding.instrumentCode),
          assetCategoryLabel: getAssetCategoryLabel(getAssetCategoryKey(holding.instrumentCode)),
          currentValue,
          weight,
          profitLoss: toMaybeNumber(holding.profitLoss),
          profitLossPercent: toMaybeNumber(holding.profitLossPercent),
        };
      })
      .filter((holding) => holding.currentValue > 0)
      .sort((a, b) => b.currentValue - a.currentValue);
    return items;
  }, [aggregatedHoldings, totalValue]);

  const topHolding = allocationData[0] ?? null;
  const cashValue = allocationData
    .filter((item) => normalizeCode(item.instrumentCode) === INTERNAL_CASH_CODE)
    .reduce((sum, item) => sum + item.currentValue, 0);
  const cashRatio = totalValue > 0 ? (cashValue / totalValue) * 100 : null;

  const assetCategorySummary = useMemo(() => buildAssetCategorySummary(aggregatedHoldings, totalValue), [aggregatedHoldings, totalValue]);
  const assetTypeBreakdown = useMemo(
    () => assetCategorySummary.filter((item) => item.value > 0).sort((a, b) => b.value - a.value),
    [assetCategorySummary],
  );
  const distributionData = useMemo(
    () =>
      allocationData.slice(0, 5).map((item, index) => ({
        key: item.instrumentCode,
        label: item.displayName,
        value: item.currentValue,
        weight: item.weight,
        color: CHART_COLORS[index % CHART_COLORS.length],
      })),
    [allocationData],
  );
  const dominantDistributionItem = distributionData[0] ?? null;
  const fallbackDiversificationScore = useMemo(() => {
    if (allocationData.length === 0) {
      return { label: "Nötr (0/5)", value: 0 };
    }
    const hhi = allocationData.reduce((sum, item) => sum + (item.weight / 100) ** 2, 0);
    const score = Math.max(1, Math.min(5, Math.round((1 - hhi) * 5)));
    const label = score <= 2 ? "Zayıf" : score === 3 ? "Orta" : "İyi";
    return { label: `${label} (${score}/5)`, value: (score / 5) * 100 };
  }, [allocationData]);
  const fallbackVolatilityScore = useMemo(() => {
    const cryptoWeight = assetTypeBreakdown.find((item) => item.key === "CRYPTO")?.ratio ?? 0;
    const fxWeight = assetTypeBreakdown.find((item) => item.key === "FX")?.ratio ?? 0;
    const score = Math.min(100, cryptoWeight * 1.1 + fxWeight * 0.45);
    return { label: score >= 65 ? "Yüksek" : score >= 35 ? "Orta" : "Düşük", value: score };
  }, [assetTypeBreakdown]);
  const fallbackLiquidityScore = useMemo(() => {
    const stockWeight = assetTypeBreakdown.find((item) => item.key === "STOCK")?.ratio ?? 0;
    const score = Math.min(100, (cashRatio ?? 0) * 18 + stockWeight * 0.55 + 20);
    return { label: score >= 65 ? "İyi" : score >= 40 ? "Orta" : "Zayıf", value: score };
  }, [assetTypeBreakdown, cashRatio]);
  const fallbackRiskAlert = useMemo(() => {
    if (!topHolding) {
      return {
        title: "Risk görünümü hazırlanıyor",
        message: "Portföy dağılımı oluştukça risk değerlendirmesi burada görünecek.",
        tone: "neutral",
      };
    }
    if (topHolding.weight >= 55) {
      return {
        title: "Yüksek konsantrasyon riski",
        message: `${topHolding.displayName} portföyünün %${formatNumber(topHolding.weight, 1)}'ini oluşturuyor. Çeşitlendirme önerilir.`,
        tone: "warning",
      };
    }
    if (topHolding.weight >= 35) {
      return {
        title: "Orta konsantrasyon riski",
        message: `${topHolding.displayName} portföyde baskın pozisyonda. Ağırlık dağılımı izlenmeli.`,
        tone: "neutral",
      };
    }
    return {
      title: "Dağılım dengeli görünüyor",
      message: "Portföyde baskın tek bir pozisyon görünmüyor. Mevcut çeşitlilik korunabilir.",
      tone: "positive",
    };
  }, [topHolding]);
  const diversificationScore = riskSummary?.diversification
    ? { label: riskSummary.diversification.label, value: Number(riskSummary.diversification.score ?? 0) }
    : fallbackDiversificationScore;
  const volatilityScore = riskSummary?.volatility
    ? { label: riskSummary.volatility.label, value: Number(riskSummary.volatility.score ?? 0) }
    : fallbackVolatilityScore;
  const liquidityScore = riskSummary?.liquidity
    ? { label: riskSummary.liquidity.label, value: Number(riskSummary.liquidity.score ?? 0) }
    : fallbackLiquidityScore;
  const riskAlert = riskSummary
    ? {
        title: riskSummary.alertTitle || fallbackRiskAlert.title,
        message: riskSummary.alertMessage || fallbackRiskAlert.message,
        tone: riskSummary.alertTone || fallbackRiskAlert.tone,
      }
    : fallbackRiskAlert;
  const aiRiskTeaser = useMemo(() => buildAiRiskTeaser({ topHolding, cashRatio, volatilityScore, liquidityScore }), [topHolding, cashRatio, volatilityScore, liquidityScore]);
  const aiInsightSections = useMemo(
    () =>
      buildAiInsightSections({
        aiAnalysisData,
        riskAlert,
        topHolding,
        volatilityScore,
        liquidityScore,
        diversificationScore,
        totalValue,
        totalProfitLoss,
        totalProfitLossPercent,
        formatMoney: formatAmount,
      }),
    [aiAnalysisData, riskAlert, topHolding, volatilityScore, liquidityScore, diversificationScore, totalValue, totalProfitLoss, totalProfitLossPercent, formatAmount],
  );
  const filteredAndSortedHoldings = useMemo(() => {
    let items = [...aggregatedHoldings];
    if (holdingsFilterKey === "PROFIT") {
      items = items.filter((holding) => toNumber(holding.profitLoss) > 0);
    } else if (holdingsFilterKey === "LOSS") {
      items = items.filter((holding) => toNumber(holding.profitLoss) < 0);
    } else if (holdingsFilterKey === "UNPRICED") {
      items = items.filter((holding) => !holding.valuationAvailable);
    }

    items.sort((left, right) => {
      if (holdingsSortKey === "PNL") {
        return toNumber(right.profitLoss) - toNumber(left.profitLoss);
      }
      if (holdingsSortKey === "NAME") {
        return formatInstrumentLabel(left.instrumentCode).localeCompare(formatInstrumentLabel(right.instrumentCode), "tr");
      }
      const leftWeight = totalValue > 0 ? resolveHoldingValue(left) / totalValue : 0;
      const rightWeight = totalValue > 0 ? resolveHoldingValue(right) / totalValue : 0;
      return rightWeight - leftWeight;
    });

    return items;
  }, [aggregatedHoldings, holdingsFilterKey, holdingsSortKey, totalValue]);

  const activityItems = useMemo(() => {
    const rows = [...holdings]
      .sort((a, b) => getHoldingActivityTimestamp(b) - getHoldingActivityTimestamp(a))
      .slice(0, 5)
      .map((holding) => ({
        title: t("portfolio.holdingPositionUpdated", { name: formatInstrumentLabel(holding.instrumentCode) }),
        timestamp: holding.updatedAt || holding.createdAt || selectedPortfolio?.updatedAt || selectedPortfolio?.createdAt,
        detail: `${formatNumber(holding.quantity)} adet · ${formatAmount(holding.buyPrice)}`,
        tone: toNumber(holding.profitLoss) >= 0 ? "positive" : "negative",
      }));

    if (selectedPortfolio && rows.length < 5) {
      rows.push({
        title: "Portföy değeri hesaplandı",
        timestamp: selectedPortfolio.updatedAt || selectedPortfolio.createdAt,
        detail: `${formatAmount(totalValue || totalCost)} · ${formatNumber(holdings.length, 0)} varlık`,
        tone: "neutral",
      });
    }

    return rows;
  }, [holdings, selectedPortfolio, totalCost, totalValue, formatAmount]);

  return (
    <div className="portfolio-management-shell portfolio-page-v3 portfolio-page-v4">
      {toast ? (
        <div key={toast.id} className={`toast-notify ${toast.type}`}>
          {toast.type === "success"
            ? <Check size={15} strokeWidth={2.5} className="toast-notify-icon" />
            : <X size={15} strokeWidth={2.5} className="toast-notify-icon" />}
          <span>{toast.message}</span>
        </div>
      ) : null}
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
                {t("portfolio.createdAt", { value: formatDateTime(selectedPortfolio?.createdAt) })}
              </p>
              <p className="portfolio-hero-meta">
                {formatNumber(holdings.length, 0)} varlık • Son güncelleme {formatDateTime(selectedPortfolio?.updatedAt || selectedPortfolio?.createdAt)}
              </p>
            </div>
            <div className="actions-row portfolio-top-actions">
              <CurrencyToggle className="analysis-currency-toggle" />
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
              <section className="panel-surface portfolio-summary-strip portfolio-summary-strip--standalone portfolio-summary-strip--hero">
                <div className="portfolio-summary-grid">
                  <PortfolioMetricCard label={t("portfolio.summary.totalCost")} value={formatAmount(totalCost)} tone="neutral" />
                  <PortfolioMetricCard label={t("portfolio.summary.currentValue")} value={formatAmount(totalValue)} tone="accent" />
                  <PortfolioMetricCard
                    label="Günlük K/Z"
                    value={dailyProfitLoss == null ? "Henüz yok" : formatAmount(dailyProfitLoss)}
                    subvalue={dailyProfitLossPercent == null ? null : formatPercent(dailyProfitLossPercent)}
                    tone={resolveTone(dailyProfitLoss)}
                  />
                  <PortfolioMetricCard
                    label="Toplam K/Z"
                    value={formatAmount(totalProfitLoss)}
                    subvalue={totalProfitLossPercent == null ? null : formatPercent(totalProfitLossPercent)}
                    tone={resolveTone(totalProfitLoss)}
                  />
                </div>
              </section>

              <section className="portfolio-grid-row portfolio-grid-row--analytics">
                <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-distribution-panel">
                  <div className="panel-head portfolio-analytics-panel-head portfolio-distribution-head">
                    <div>
                      <h3>Dağılım</h3>
                    </div>
                    <span className="summary-chip portfolio-distribution-count-chip">{formatNumber(distributionData.length, 0)}</span>
                  </div>

                  {distributionData.length === 0 ? (
                    <EmptyState title={t("portfolio.allocationEmptyTitle")} description={t("portfolio.allocationEmptyDescription")} />
                  ) : (
                    <div className="portfolio-distribution-layout">
                      <div className="portfolio-distribution-chart-wrap">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie data={distributionData} dataKey="value" nameKey="label" outerRadius={96} innerRadius={58}>
                              {distributionData.map((entry) => (
                                <Cell key={entry.key} fill={entry.color} />
                              ))}
                            </Pie>
                            <Tooltip
                              formatter={(value) => formatAmount(value)}
                              contentStyle={chartTheme.tooltipContentStyle}
                              itemStyle={chartTheme.tooltipItemStyle}
                              labelStyle={chartTheme.tooltipLabelStyle}
                            />
                          </PieChart>
                        </ResponsiveContainer>
                        <div className="portfolio-distribution-center">
                          <strong>{dominantDistributionItem?.label ?? "-"}</strong>
                          <span>{dominantDistributionItem ? `%${formatNumber(dominantDistributionItem.weight, 1)}` : "-"}</span>
                        </div>
                      </div>
                      <div className="portfolio-distribution-list">
                        {distributionData.map((entry) => (
                          <div key={entry.key} className="portfolio-distribution-list-item">
                            <div className="portfolio-distribution-list-label">
                              <span className="portfolio-color-dot" style={{ backgroundColor: entry.color }} />
                              <span>{entry.label}</span>
                            </div>
                            <div className="portfolio-distribution-list-metrics">
                              <strong>{formatNumber(entry.weight, 1)}%</strong>
                              <span>{formatAmount(entry.value)}</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </section>

                <section className="panel-surface portfolio-management-panel portfolio-detail-panel portfolio-risk-panel">
                  <div className="panel-head portfolio-side-card-head">
                    <div>
                      <p className="eyebrow">Risk</p>
                      <h3>Risk analizi</h3>
                    </div>
                    <button type="button" className="portfolio-ai-drawer-trigger portfolio-ai-drawer-trigger--inline" onClick={openAiDrawer}>
                      AI analizini aç ›
                    </button>
                  </div>
                  <div className={`portfolio-risk-alert is-${riskAlert.tone}`}>
                    <div className="portfolio-risk-alert-title">
                      <ShieldAlert size={18} />
                      <strong>{riskAlert.title}</strong>
                    </div>
                    <p>{riskAlert.message}</p>
                  </div>
                  <div className="portfolio-risk-metrics">
                    <RiskMetricRow label="Çeşitlilik skoru" value={diversificationScore.label} progress={diversificationScore.value} tone="warning" />
                    <RiskMetricRow label="Volatilite" value={volatilityScore.label} progress={volatilityScore.value} tone="danger" />
                    <RiskMetricRow label="Likidite" value={liquidityScore.label} progress={liquidityScore.value} tone="success" />
                  </div>
                </section>
              </section>

              <section className="portfolio-grid-row portfolio-grid-row--holdings">
                <section className="panel-surface portfolio-management-panel portfolio-table-card portfolio-holdings-section portfolio-detail-panel portfolio-holdings-panel portfolio-holdings-panel--pro">
                  <div className="panel-head portfolio-holdings-head">
                    <div>
                      <p className="eyebrow">Varlıklar</p>
                      <h3>{t("portfolio.holdingsTitle")}</h3>
                    </div>
                    <div className="portfolio-holdings-toolbar">
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => setHoldingsFilterKey((current) => (current === "ALL" ? "PROFIT" : current === "PROFIT" ? "LOSS" : current === "LOSS" ? "UNPRICED" : "ALL"))}
                      >
                        <Filter size={15} />
                        Filtrele
                      </button>
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => setHoldingsSortKey((current) => (current === "WEIGHT" ? "PNL" : current === "PNL" ? "NAME" : "WEIGHT"))}
                      >
                        <ArrowUpDown size={15} />
                        Sırala
                      </button>
                    </div>
                  </div>
                  {filteredAndSortedHoldings.length === 0 ? (
                    <EmptyState title={t("portfolio.emptyHoldingsTitle")} description={t("portfolio.emptyHoldingsDescription")} />
                  ) : (
                    <div className="portfolio-holdings-scroll portfolio-holdings-scroll--bounded">
                      <table className="portfolio-holdings-table portfolio-holdings-table--v3">
                        <thead>
                          <tr>
                            <th>{t("portfolio.table.asset")}</th>
                            <th>{t("portfolio.table.quantity")}</th>
                            <th>Maliyet</th>
                            <th>Güncel fiyat</th>
                            <th>Toplam değer</th>
                            <th>K/Z</th>
                            <th>{t("portfolio.table.action")}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredAndSortedHoldings.map((holding, index) => {
                            const currentValue = resolveHoldingValue(holding);
                            const weight = totalValue > 0 ? (currentValue / totalValue) * 100 : null;
                            const typeLabel = getAssetCategoryLabel(getAssetCategoryKey(holding.instrumentCode));
                            const color = CHART_COLORS[index % CHART_COLORS.length];
                            return (
                              <tr key={`${holding.holdingId || holding.instrumentCode}-${index}`}>
                                <td>
                                  <div className="portfolio-cell-stack">
                                    <div className="portfolio-asset-label-row">
                                      <span className="portfolio-color-dot" style={{ backgroundColor: color }} />
                                      <strong>{formatInstrumentLabel(holding.instrumentCode)}</strong>
                                    </div>
                                    <div className="portfolio-inline-meta">
                                      <span className={`portfolio-type-badge is-${getAssetCategoryKey(holding.instrumentCode).toLowerCase()}`}>{typeLabel}</span>
                                      <span className="muted">(%{formatNumber(weight, 1)})</span>
                                      {holding.entryCount > 1 ? <span className="muted">{holding.entryCount} kayıt birleşti</span> : null}
                                    </div>
                                  </div>
                                </td>
                                <td>{formatNumber(holding.quantity)}</td>
                                <td>{formatAmount(holding.buyPrice)}</td>
                                <td>{holding.valuationAvailable ? formatAmount(holding.currentPrice) : t("portfolio.priceMissing")}</td>
                                <td>{holding.valuationAvailable ? formatAmount(holding.currentValue) : formatAmount(currentValue)}</td>
                                <td className={holding.valuationAvailable ? getPnLCellClass(holding.profitLoss) : undefined}>
                                  {holding.valuationAvailable ? (
                                    <div className="portfolio-cell-stack">
                                      <strong>{formatAmount(holding.profitLoss)}</strong>
                                      <span className="muted">{formatPercent(holding.profitLossPercent)}</span>
                                    </div>
                                  ) : (
                                    <span className="muted">{t("portfolio.missingData")}</span>
                                  )}
                                </td>
                                <td>
                                  <div className="actions-row portfolio-holdings-actions">
                                    <button type="button" className="secondary-button" onClick={() => openEditHoldingModal(holding)}>
                                      Güncelle
                                    </button>
                                    <button type="button" className="danger-button" onClick={() => handleDeleteHolding(holding)}>
                                      Sil
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            );
                          })}
                          <tr className="portfolio-holdings-total-row">
                            <td colSpan={4}>Toplam ({formatNumber(filteredAndSortedHoldings.length, 0)} varlık)</td>
                            <td>{formatAmount(totalValue)}</td>
                            <td className={getPnLCellClass(totalProfitLoss)}>
                              <div className="portfolio-cell-stack">
                                <strong>{formatAmount(totalProfitLoss)}</strong>
                                <span>{formatPercent(totalProfitLossPercent)}</span>
                              </div>
                            </td>
                            <td />
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              </section>

              <section className="portfolio-grid-row portfolio-grid-row--performance">
                <PortfolioPerformancePanel
                  portfolioId={selectedPortfolioId}
                  holdings={holdings}
                  chartTheme={chartTheme}
                  currency={currency}
                  formatAmount={formatAmount}
                />
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

      {isCreatePortfolioModalOpen && typeof document !== "undefined" ? createPortal(
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
              <div className="instrument-action-footer">
                <button type="submit">+ Yeni Portföy Oluştur</button>
              </div>
            </form>
          </div>
        </div>,
        document.body,
      ) : null}

      {isHoldingModalOpen && typeof document !== "undefined" ? createPortal(
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
                            <strong>{formatInstrumentLabel(item.code)}</strong>
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
        </div>,
        document.body,
      ) : null}

      {isActivityModalOpen && typeof document !== "undefined" ? createPortal(
        <div className="modal-backdrop" role="presentation" onClick={() => setActivityModalOpen(false)}>
          <div className="auth-modal portfolio-activity-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="portfolio-action-modal-head">
              <div>
                <p className="eyebrow">Akış</p>
                <h3>Son Hareketler</h3>
              </div>
              <button type="button" className="secondary-button" onClick={() => setActivityModalOpen(false)}>
                {t("common.close")}
              </button>
            </div>

            <div className="portfolio-activity-modal-body">
              {activityItems.length === 0 ? (
                <div className="portfolio-inline-empty">Henüz hareket yok.</div>
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
        </div>,
        document.body,
      ) : null}


      {isAiDrawerVisible
        ? createPortal(
            <div className={`portfolio-ai-drawer ${isAiDrawerOpen ? "is-open" : ""}`} role="presentation">
              <div className="portfolio-ai-drawer-backdrop" aria-hidden="true" onClick={closeAiDrawer} />
              <aside className="portfolio-ai-drawer-sheet" role="dialog" aria-modal="true" aria-label="AI portföy analizi">
            <div className="portfolio-ai-drawer-head">
              <div className="portfolio-ai-drawer-head-copy">
                <p className="eyebrow">Yapay Zeka</p>
                <h3>Portföy AI Analizi</h3>
                <div className="portfolio-ai-drawer-badges">
                  <span className="portfolio-ai-badge">Risk Yorumu</span>
                  <span className="portfolio-ai-badge">Güncel</span>
                  <span className="portfolio-ai-badge">AI Destekli</span>
                </div>
              </div>
              <button type="button" className="portfolio-ai-drawer-close" aria-label="AI analiz panelini kapat" onClick={closeAiDrawer}>
                <X size={16} />
              </button>
            </div>
            <div className="portfolio-ai-drawer-brief">
              <div className="portfolio-ai-drawer-brief-icon">
                <Sparkles size={16} />
              </div>
              <div className="portfolio-ai-drawer-brief-copy">
                <span>Kısa Uyarı</span>
                <p>{aiRiskTeaser}</p>
              </div>
            </div>
            <div className="portfolio-ai-drawer-body">
              {aiAnalysisLoading ? (
                <section className="portfolio-ai-sections">
                  <div className="portfolio-ai-state-card portfolio-ai-state-card--loading">
                    <strong>Portföy analiz ediliyor...</strong>
                    <p>AI katmanı güncel dağılım, risk ve öneri alanlarını hazırlıyor.</p>
                  </div>
                  {[0, 1, 2].map((item) => (
                    <div key={`skeleton-${item}`} className="portfolio-ai-section-card portfolio-ai-section-card--skeleton" aria-hidden="true">
                      <span className="portfolio-ai-skeleton-line portfolio-ai-skeleton-line--title" />
                      <span className="portfolio-ai-skeleton-line" />
                      <span className="portfolio-ai-skeleton-line" />
                      <span className="portfolio-ai-skeleton-line portfolio-ai-skeleton-line--short" />
                    </div>
                  ))}
                </section>
              ) : aiAnalysisError ? (
                <section className="portfolio-ai-sections">
                  <div className="portfolio-ai-state-card portfolio-ai-state-card--error">
                    <div className="portfolio-ai-state-head">
                      <AlertTriangle size={16} />
                      <strong>AI analizi şu anda getirilemedi</strong>
                    </div>
                    <p>{aiAnalysisError}</p>
                  </div>
                </section>
              ) : !aiAnalysisData ? (
                <section className="portfolio-ai-sections">
                  <div className="portfolio-ai-state-card">
                    <strong>{t("portfolio.aiAnalysisError")}</strong>
                    <p>{t("portfolio.aiAnalysisEmptyNote")}</p>
                  </div>
                </section>
              ) : (
                <section className="portfolio-ai-sections">
                  {aiInsightSections.map((section) => (
                    <article key={section.title} className={`portfolio-ai-section-card is-${section.tone}`}>
                      <div className="portfolio-ai-section-head">
                        <div className="portfolio-ai-section-icon">{section.icon}</div>
                        <div>
                          <h4>{section.title}</h4>
                          {section.subtitle ? <p>{section.subtitle}</p> : null}
                        </div>
                      </div>
                      {section.paragraphs?.length ? (
                        <div className="portfolio-ai-section-copy">
                          {section.paragraphs.map((paragraph) => (
                            <p key={paragraph}>{paragraph}</p>
                          ))}
                        </div>
                      ) : null}
                      {section.items?.length ? (
                        <ul className="portfolio-ai-section-list">
                          {section.items.map((item) => (
                            <li key={item}>{item}</li>
                          ))}
                        </ul>
                      ) : null}
                    </article>
                  ))}
                </section>
              )}
            </div>
              </aside>
            </div>,
            document.body,
          )
        : null}
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

function RiskMetricRow({ label, value, progress, tone }) {
  const score = Math.max(1, Math.min(5, Math.round((Math.max(0, Math.min(100, progress)) / 100) * 4) + 1));
  return (
    <div className="portfolio-risk-metric-row">
      <div className="portfolio-risk-metric-head">
        <span>{label}</span>
        <div className="portfolio-risk-metric-value">
          <strong>{value}</strong>
          <span className={`portfolio-risk-score-badge is-${tone}`}>{score}/5</span>
        </div>
      </div>
      <div className="portfolio-risk-progress">
        <span className={`portfolio-risk-progress-bar is-${tone}`} style={{ width: `${Math.max(0, Math.min(100, progress))}%` }} />
      </div>
    </div>
  );
}

function buildAiRiskTeaser({ topHolding, cashRatio, volatilityScore, liquidityScore }) {
  if (topHolding && topHolding.weight >= 55) {
    return `${topHolding.displayName} ağırlığı portföy riskini artırıyor.`;
  }
  if ((cashRatio ?? 0) < 5 && volatilityScore.value >= 60) {
    return "Nakit tamponu düşük, yüksek volatilite kısa vadeli dalgalanmayı artırabilir.";
  }
  if (liquidityScore.value >= 65) {
    return "Likidite görünümü güçlü, portföy beklenmedik hareketlere karşı daha esnek.";
  }
  return "Portföy dengeli görünüyor, ağırlık dağılımı ve fiyat güncelliği izlenmeli.";
}

function buildAiInsightSections({
  aiAnalysisData,
  riskAlert,
  topHolding,
  volatilityScore,
  liquidityScore,
  diversificationScore,
  totalValue,
  totalProfitLoss,
  totalProfitLossPercent,
  formatMoney = formatCurrency,
}) {
  const summary = getNonEmptyText(
    aiAnalysisData?.summary,
    aiAnalysisData?.finalComment,
    aiAnalysisData?.allocationComment,
    aiAnalysisData?.riskComment,
    `Toplam portföy değeri ${formatMoney(totalValue)} seviyesinde. Toplam fark ${formatMoney(totalProfitLoss)} ve getirisi ${formatPercent(
      totalProfitLossPercent,
    )} olarak izleniyor.`,
  );

  const generalParagraphs = [
    summary,
    aiAnalysisData?.allocationComment,
    aiAnalysisData?.diversificationComment,
  ].filter(Boolean);

  const riskItems = uniqueStrings([
    ...(Array.isArray(aiAnalysisData?.riskSignals) ? aiAnalysisData.riskSignals : []),
    aiAnalysisData?.riskComment,
    riskAlert?.message,
    topHolding?.weight >= 55 ? "Baskın pozisyon riski yüksek; tek varlık etkisi toplam performansı belirgin şekilde yönlendirebilir." : null,
    volatilityScore.value >= 65 ? "Volatilite yüksek; günlük dalgalanmalar portföy değerinde sert hareketler yaratabilir." : null,
  ]);

  const strengthItems = uniqueStrings([
    ...(Array.isArray(aiAnalysisData?.strongestPositions) ? aiAnalysisData.strongestPositions : []),
    liquidityScore.value >= 65 ? "Likidite görünümü güçlü; yeniden dengeleme veya nakde dönüş kararları daha rahat uygulanabilir." : null,
    diversificationScore.value >= 60 ? "Çeşitlilik görünümü dengeli; baskın tek pozisyon etkisi kontrol altında." : null,
    totalProfitLoss > 0 ? `Portföy genelinde pozitif fark ${formatMoney(totalProfitLoss)} seviyesinde izleniyor.` : null,
  ]);

  const actionItems = uniqueStrings([
    ...(Array.isArray(aiAnalysisData?.suggestions) ? aiAnalysisData.suggestions : []),
    topHolding?.weight >= 55 ? `${topHolding.displayName} ağırlığını kademeli şekilde azaltarak dağılımı dengelemeyi değerlendirin.` : null,
    volatilityScore.value >= 65 ? "Yüksek oynaklık taşıyan pozisyonlarda hedef ağırlık ve zarar kes disiplinini netleştirin." : null,
    liquidityScore.value < 40 ? "Nakit oranını veya kolay nakde dönebilen varlık payını artırmak kısa vadeli esnekliği güçlendirebilir." : null,
  ]);

  return [
    {
      title: "Genel Değerlendirme",
      subtitle: "Portföyün genel görünümü ve ana tema",
      icon: <Sparkles size={15} />,
      tone: "primary",
      paragraphs: generalParagraphs.length ? generalParagraphs : ["Genel değerlendirme için yeterli AI çıktısı bulunamadı."],
    },
    {
      title: "Öne Çıkan Riskler",
      subtitle: "Yoğunlaşma ve oynaklık tarafında dikkat çeken başlıklar",
      icon: <ShieldAlert size={15} />,
      tone: "risk",
      items: riskItems.length ? riskItems : ["Belirgin bir risk sinyali üretilmedi."],
    },
    {
      title: "Güçlü Taraflar",
      subtitle: "Portföyün korunan veya avantajlı kalan yönleri",
      icon: <Sparkles size={15} />,
      tone: "success",
      items: strengthItems.length ? strengthItems : ["Belirgin bir güçlü taraf sinyali üretilmedi."],
    },
    {
      title: "Aksiyon Önerileri",
      subtitle: "Yakın vadede uygulanabilecek kontrollü adımlar",
      icon: <AlertTriangle size={15} />,
      tone: "warning",
      items: actionItems.length ? actionItems : ["Şu an için ek aksiyon önerisi bulunmuyor."],
    },
  ];
}

function getNonEmptyText(...values) {
  return values.map(normalizeAiText).find(Boolean) ?? "";
}

function normalizeAiText(value) {
  if (typeof value !== "string") {
    return "";
  }

  return value.trim();
}

function uniqueStrings(values) {
  const seen = new Set();
  return values
    .map(normalizeAiText)
    .filter((value) => {
      if (!value || seen.has(value)) {
        return false;
      }
      seen.add(value);
      return true;
    });
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

function formatInstrumentSearchValue(instrument) {
  if (!instrument) {
    return "";
  }
  return formatInstrumentLabel(instrument.code || instrument.name);
}

function toNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function toMaybeNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}



function getPnLCellClass(profitLoss) {
  const numeric = Number(profitLoss);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "portfolio-pl-cell is-flat";
  }
  return numeric > 0 ? "portfolio-pl-cell is-up" : "portfolio-pl-cell is-down";
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

function buildHoldingQuoteMap(quotes) {
  const quoteMap = new Map();
  for (const quote of Array.isArray(quotes) ? quotes : []) {
    const symbol = normalizeCode(quote?.symbol);
    const code = normalizeCode(quote?.code);
    if (symbol) quoteMap.set(symbol, quote);
    if (code) quoteMap.set(code, quote);
  }
  return quoteMap;
}

function resolveHoldingDailyProfitLoss(holding, quoteMap) {
  if (normalizeCode(holding?.instrumentCode) === INTERNAL_CASH_CODE) {
    return toMaybeNumber(holding?.dailyProfitLoss) ?? 0;
  }
  const explicitDaily = toMaybeNumber(holding?.dailyProfitLoss);
  const quote = quoteMap?.get(normalizeCode(holding?.instrumentCode));
  const quotePrice = toMaybeNumber(quote?.sellRate ?? quote?.price);
  const quantity = toMaybeNumber(holding?.quantity);
  const currentPrice = toMaybeNumber(holding?.currentPrice) ?? quotePrice;
  const currentValue = toMaybeNumber(holding?.currentValue)
    ?? (currentPrice != null && quantity != null ? currentPrice * quantity : null);
  const fallbackValue = currentValue ?? resolveHoldingValue(holding);
  const holdingChangeRate = toMaybeNumber(holding?.dailyChangePercent);
  const quoteChangeRate = toMaybeNumber(quote?.changeRate ?? quote?.dailyChangePercent);
  const changeRate = holdingChangeRate != null && holdingChangeRate !== 0 ? holdingChangeRate : quoteChangeRate ?? holdingChangeRate;
  if (explicitDaily != null && explicitDaily !== 0) return explicitDaily;
  if (explicitDaily === 0 && (changeRate == null || changeRate === 0)) return explicitDaily;
  if (fallbackValue == null || changeRate == null) return null;

  const divisor = 1 + changeRate / 100;
  if (!Number.isFinite(divisor) || divisor === 0) return null;
  return fallbackValue - fallbackValue / divisor;
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
  if (code.startsWith("TCMB:") || /^TCMB[A-Z]{2,4}(BUY|SELL)$/.test(code) || (code.endsWith("TRY") && code.length >= 6)) {
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
    FX: "Döviz",
    COMMODITY: "Altın / Emtia",
    OTHER: "Diğer",
  }[key] ?? "Diğer";
}

function buildAssetCategorySummary(holdings, totalValue) {
  const groups = new Map([
    ["CASH", { key: "CASH", label: "Nakit", count: 0, value: 0 }],
    ["STOCK", { key: "STOCK", label: "Hisse", count: 0, value: 0 }],
    ["FUND", { key: "FUND", label: "Fon", count: 0, value: 0 }],
    ["CRYPTO", { key: "CRYPTO", label: "Kripto", count: 0, value: 0 }],
    ["FX", { key: "FX", label: "Döviz", count: 0, value: 0 }],
    ["COMMODITY", { key: "COMMODITY", label: "Altın / Emtia", count: 0, value: 0 }],
    ["OTHER", { key: "OTHER", label: "Diğer", count: 0, value: 0 }],
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

