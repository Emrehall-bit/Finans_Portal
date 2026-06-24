import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate } from "react-router-dom";
import ReactCountryFlag from "react-country-flag";
import { Check, Star, X } from "lucide-react";
import { buildMarketDetailPath } from "../api/marketApi";
import { addWatchlistItem, removeWatchlistItem } from "../api/watchlistApi";
import { watchlistKeys } from "../api/queryKeys";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import useToast from "../hooks/useToast";
import { useMarketQuotes, useMarketScreen } from "../hooks/useMarketQueries";
import { useUserWatchlist } from "../hooks/useWatchlistQueries";
import { formatInstrumentValue, formatNumber } from "../utils/formatters";
import { formatInstrumentCode, getFxCodeLabel } from "../utils/instrumentUtils";
import { getCountryCodeForInstrument } from "../utils/currencyToCountryMap";

const CATEGORY_OPTIONS = ["FX", "CRYPTO", "STOCK", "FUND", "FUTURES", "BOND", "INDEX", "COMMODITY", "FAVORITES"];
const CURRENCY_CONVERTIBLE_CATEGORIES = new Set(["STOCK", "FUND", "COMMODITY", "FUTURES"]);
const DEFAULT_SORT_OPTIONS = [
  { value: "name", labelKey: "markets.filters.sortOptions.name" },
  { value: "price_desc", labelKey: "markets.filters.sortOptions.priceDesc" },
  { value: "price_asc", labelKey: "markets.filters.sortOptions.priceAsc" },
  { value: "change_desc", labelKey: "markets.filters.sortOptions.changeDesc" },
  { value: "change_asc", labelKey: "markets.filters.sortOptions.changeAsc" },
];
const BIST_TIER_OPTIONS = [
  { value: "", labelKey: "markets.filters.all" },
  { value: "BIST30", label: "BIST30" },
  { value: "BIST50", label: "BIST50" },
  { value: "BIST100", label: "BIST100" },
];

const STOCK_SECTOR_OPTIONS = [
  { value: "", labelKey: "markets.filters.all" },
  { value: "BANKING", labelKey: "markets.filters.stockSectors.BANKING" },
  { value: "FINANCIALS", labelKey: "markets.filters.stockSectors.FINANCIALS" },
  { value: "TECHNOLOGY_DEFENSE", labelKey: "markets.filters.stockSectors.TECHNOLOGY_DEFENSE" },
  { value: "TELECOM", labelKey: "markets.filters.stockSectors.TELECOM" },
  { value: "TRANSPORT_TOURISM", labelKey: "markets.filters.stockSectors.TRANSPORT_TOURISM" },
  { value: "HEALTH_SPORTS", labelKey: "markets.filters.stockSectors.HEALTH_SPORTS" },
  { value: "ENERGY", labelKey: "markets.filters.stockSectors.ENERGY" },
  { value: "CHEMICALS_PETROLEUM", labelKey: "markets.filters.stockSectors.CHEMICALS_PETROLEUM" },
  { value: "INDUSTRIAL", labelKey: "markets.filters.stockSectors.INDUSTRIAL" },
  { value: "FOOD_RETAIL", labelKey: "markets.filters.stockSectors.FOOD_RETAIL" },
];

const STOCK_RANK_SORT_OPTIONS = [
  { value: "change_desc", labelKey: "markets.filters.sortOptions.topGainers" },
  { value: "change_asc", labelKey: "markets.filters.sortOptions.topLosers" },
  { value: "pe_asc", labelKey: "markets.filters.sortOptions.lowestPe" },
  { value: "pb_asc", labelKey: "markets.filters.sortOptions.lowestPb" },
  { value: "name", labelKey: "markets.filters.sortOptions.name" },
  { value: "price_desc", labelKey: "markets.filters.sortOptions.priceDesc" },
  { value: "price_asc", labelKey: "markets.filters.sortOptions.priceAsc" },
];
const REMOVED_STOCK_SORTS = new Set(["roe_desc", "roa_desc", "revenue_growth_desc", "debt_to_equity", "market_cap_desc"]);
const STOCK_BASIC_SORT_OPTIONS = [
  { value: "name", labelKey: "markets.filters.sortOptions.name" },
  { value: "change_desc", labelKey: "markets.filters.sortOptions.topGainers" },
  { value: "change_asc", labelKey: "markets.filters.sortOptions.topLosers" },
];
const SEARCH_DEBOUNCE_MS = 275;
const INSTRUMENT_TYPE_CATEGORY_KEYS = {
  FOREX: "FX",
  CURRENCY: "FX",
};
const FX_SOURCE_MODES = [
  { value: "TCMB", label: "TCMB" },
  { value: "AKBANK", label: "AKBANK" },
  { value: "COMPARE", labelKey: "markets.filters.compare" },
];
const AKBANK_VISIBLE_CODES = new Set(["AUD", "AZN", "CAD", "CHF", "CNY", "EUR", "GBP", "JPY", "KRW", "KWD", "QAR", "RUB", "USD"]);
const FX_PROVIDER_SYMBOL_PATTERN = /^[A-Z_]+:[A-Z]{2,4}:(BUY|SELL)$/;

export default function MarketsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { userId, login } = useAuth();
  const { toast, showToast } = useToast();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState(() => location.state?.category ?? "FX");
  const [fxSourceMode, setFxSourceMode] = useState("TCMB");
  const [viewMode, setViewMode] = useState("table");
  const [sortBy, setSortBy] = useState("name");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [minChangePercent, setMinChangePercent] = useState("");
  const [maxChangePercent, setMaxChangePercent] = useState("");
  const [sectorFilter, setSectorFilter] = useState("");
  const [marketFilter, setMarketFilter] = useState("");
  const [minPe, setMinPe] = useState("");
  const [maxPe, setMaxPe] = useState("");
  const [minPb, setMinPb] = useState("");
  const [maxPb, setMaxPb] = useState("");
  const [minRoe, setMinRoe] = useState("");
  const [minRoa, setMinRoa] = useState("");
  const [maxDebtToEquity, setMaxDebtToEquity] = useState("");
  const [minRevenueGrowth, setMinRevenueGrowth] = useState("");
  const [minNetProfitGrowth, setMinNetProfitGrowth] = useState("");
  const [onlyWithFundamentals, setOnlyWithFundamentals] = useState(false);
  const [bistTierFilter, setBistTierFilter] = useState("");
  const [stockSectorFilter, setStockSectorFilter] = useState("");
  const { data: watchlistItems = [], isLoading: watchlistLoading } = useUserWatchlist(userId);
  const [favoriteBusyKey, setFavoriteBusyKey] = useState("");
  const [filterMode, setFilterMode] = useState("basic");
  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  const isFavoritesTab = categoryFilter === "FAVORITES";
  const isStockTab = categoryFilter === "STOCK";
  const isFxTab = categoryFilter === "FX";
  const isFundTab = categoryFilter === "FUND";
  const isBondTab = categoryFilter === "BOND";


  useEffect(() => {
    if (!isStockTab) {
      setBistTierFilter("");
      setStockSectorFilter("");
    }
    setFilterMode("basic");
  }, [categoryFilter, isStockTab]);

  useEffect(() => {
    if (isStockTab && REMOVED_STOCK_SORTS.has(sortBy)) {
      setSortBy("name");
    }
  }, [isStockTab, sortBy]);

  const { data: allQuotes = [], isLoading: loadingAll } = useMarketQuotes({ enabled: isFavoritesTab });

  const screenParams = useMemo(() => ({
    type: categoryFilter,
    source: isFxTab && fxSourceMode !== "COMPARE" ? fxSourceMode : undefined,
    q: debouncedSearch.trim() || undefined,
    minPrice: toFilterNumber(minPrice),
    maxPrice: toFilterNumber(maxPrice),
    minChangePercent: toFilterNumber(minChangePercent),
    maxChangePercent: toFilterNumber(maxChangePercent),
    sector: isStockTab ? (sectorFilter || undefined) : undefined,
    market: isStockTab ? (marketFilter || undefined) : undefined,
    minPe: isStockTab ? toFilterNumber(minPe) : undefined,
    maxPe: isStockTab ? toFilterNumber(maxPe) : undefined,
    minPb: isStockTab ? toFilterNumber(minPb) : undefined,
    maxPb: isStockTab ? toFilterNumber(maxPb) : undefined,
    minRoe: isStockTab ? toFilterNumber(minRoe) : undefined,
    minRoa: isStockTab ? toFilterNumber(minRoa) : undefined,
    maxDebtToEquity: isStockTab ? toFilterNumber(maxDebtToEquity) : undefined,
    minRevenueGrowth: isStockTab ? toFilterNumber(minRevenueGrowth) : undefined,
    minNetProfitGrowth: isStockTab ? toFilterNumber(minNetProfitGrowth) : undefined,
    onlyWithFundamentals: isStockTab ? onlyWithFundamentals : false,
    bistTier: isStockTab ? (bistTierFilter || undefined) : undefined,
    stockSector: isStockTab ? (stockSectorFilter || undefined) : undefined,
    page: 0,
    size: 500,
    sort: normalizeScreenSort(sortBy),
  }), [
    categoryFilter,
    fxSourceMode,
    isFxTab,
    debouncedSearch,
    minPrice,
    maxPrice,
    minChangePercent,
    maxChangePercent,
    sectorFilter,
    marketFilter,
    minPe,
    maxPe,
    minPb,
    maxPb,
    minRoe,
    minRoa,
    maxDebtToEquity,
    minRevenueGrowth,
    minNetProfitGrowth,
    onlyWithFundamentals,
    bistTierFilter,
    stockSectorFilter,
    isStockTab,
    sortBy,
  ]);

  const { data: screenResponse, isLoading: loadingScreen, error: screenError } = useMarketScreen(screenParams, {
    enabled: !isFavoritesTab,
  });

  const screenedQuotes = useMemo(
    () => (screenResponse?.content ?? []).map((item) => mapScreenItem(item)),
    [screenResponse],
  );

  const loading = isFavoritesTab ? (loadingAll || watchlistLoading) : loadingScreen;
  const error = isFavoritesTab
    ? ""
    : (screenError ? extractErrorMessage(screenError, t("markets.loadError")) : "");

  const formatRateConverted = useCallback((value, item = null) => {
    if (value === null || value === undefined || value === "") {
      return "-";
    }
    if (item && String(item.instrumentType || item.marketCategory || "").toUpperCase() === "INDEX") {
      return formatInstrumentValue(value, {
        instrumentType: "INDEX",
        displayUnit: item.displayUnit,
        includeUnit: true,
        locale: i18n.resolvedLanguage,
      });
    }
    if (CURRENCY_CONVERTIBLE_CATEGORIES.has(categoryFilter)) {
      return formatInstrumentValue(value, {
        instrumentType: item?.instrumentType || categoryFilter,
        currency: "TRY",
        locale: i18n.resolvedLanguage,
      });
    }
    return formatRate(value);
  }, [categoryFilter, i18n.resolvedLanguage]);

  const visibleQuotes = useMemo(() => {
    const query = debouncedSearch.trim().toLowerCase();

    if (isFavoritesTab) {
      const quoteMap = new Map();
      for (const q of [...allQuotes, ...screenedQuotes]) {
        if (!q || typeof q !== "object") continue;
        const sym = normalizeCode(q.symbol);
        const cod = normalizeCode(q.code);
        if (sym) quoteMap.set(sym, q);
        if (cod && !quoteMap.has(cod)) quoteMap.set(cod, q);
        // Legacy packed key (e.g. "TCMBAUDSELL") for old DB entries stored before colon format was used
        if (cod && cod.length <= 4 && classifyCategory(q, q.marketCategory || "") === "FX") {
          const packedKey = `TCMB${cod}SELL`;
          if (!quoteMap.has(packedKey)) quoteMap.set(packedKey, q);
        }
      }

      return watchlistItems
        .map((row) => {
          const code = normalizeCode(row.instrumentCode);
          const quote = quoteMap.get(code);
          if (quote) {
            return { ...quote, marketCategory: classifyCategory(quote, quote.instrumentType || "OTHER") };
          }
          const displayCode = formatInstrumentCode(row.instrumentCode);
          const isFxInstrument = displayCode !== row.instrumentCode;
          return {
            symbol: row.instrumentCode,
            code: displayCode,
            displayName: displayCode,
            price: null,
            changeRate: null,
            instrumentType: isFxInstrument ? "FX" : "OTHER",
            marketCategory: isFxInstrument ? "FX" : "OTHER",
            source: null,
          };
        })
        .filter((item) => {
          if (query.length === 0) return true;
          return (
            item.symbol?.toLowerCase().includes(query) ||
            item.code?.toLowerCase().includes(query) ||
            item.displayName?.toLowerCase().includes(query)
          );
        })
        .sort((left, right) => sortQuotes(left, right, sortBy));
    }

    if (isStockTab) {
      return [...screenedQuotes].sort((left, right) => sortQuotes(left, right, sortBy));
    }

    const nextQuotes = [...screenedQuotes]
      .filter((item) => !isFxTab || fxSourceMode !== "AKBANK" || AKBANK_VISIBLE_CODES.has(extractFxCode(item.code || item.symbol)))
      .sort((left, right) => sortQuotes(left, right, sortBy));
    return nextQuotes;
  }, [
    allQuotes,
    debouncedSearch,
    isFavoritesTab,
    screenedQuotes,
    sortBy,
    watchlistItems,
    fxSourceMode,
    isFxTab,
    isStockTab,
  ]);

  const fxComparisonRows = useMemo(
    () => buildFxComparisonRows(screenedQuotes, debouncedSearch),
    [screenedQuotes, debouncedSearch],
  );

  const displayedCount = isFxTab && fxSourceMode === "COMPARE" ? fxComparisonRows.length : visibleQuotes.length;
  const activeAdvancedFilterChips = useMemo(() => {
    if (!isStockTab) {
      return [];
    }

    const chips = [];
    if (minPrice) chips.push({ key: "minPrice", label: t("markets.filters.chips.priceGreater", { value: minPrice }) });
    if (maxPrice) chips.push({ key: "maxPrice", label: t("markets.filters.chips.priceLess", { value: maxPrice }) });
    if (minChangePercent) chips.push({ key: "minChangePercent", label: t("markets.filters.chips.dailyGreater", { value: minChangePercent }) });
    if (maxChangePercent) chips.push({ key: "maxChangePercent", label: t("markets.filters.chips.dailyLess", { value: maxChangePercent }) });
    if (minPe) chips.push({ key: "minPe", label: "F/K > " + minPe });
    if (maxPe) chips.push({ key: "maxPe", label: "F/K < " + maxPe });
    if (minPb) chips.push({ key: "minPb", label: "PD/DD > " + minPb });
    if (maxPb) chips.push({ key: "maxPb", label: "PD/DD < " + maxPb });
    if (minRoe) chips.push({ key: "minRoe", label: "ROE > " + minRoe });
    if (minRoa) chips.push({ key: "minRoa", label: "ROA > " + minRoa });
    if (maxDebtToEquity) chips.push({ key: "maxDebtToEquity", label: t("markets.filters.chips.debtLess", { value: maxDebtToEquity }) });
    if (minRevenueGrowth) chips.push({ key: "minRevenueGrowth", label: t("markets.filters.chips.revenueGrowthGreater", { value: minRevenueGrowth }) });
    if (minNetProfitGrowth) chips.push({ key: "minNetProfitGrowth", label: t("markets.filters.chips.netProfitGrowthGreater", { value: minNetProfitGrowth }) });
    if (sectorFilter) chips.push({ key: "sector", label: t("markets.filters.chips.sector", { value: sectorFilter }) });
    if (marketFilter) chips.push({ key: "market", label: t("markets.filters.chips.market", { value: marketFilter }) });
    if (onlyWithFundamentals) chips.push({ key: "onlyWithFundamentals", label: t("markets.filters.chips.withFundamentals") });
    if (bistTierFilter) chips.push({ key: "bistTier", label: t("markets.filters.chips.index", { value: bistTierFilter }) });
    if (stockSectorFilter) {
      const sectorOption = STOCK_SECTOR_OPTIONS.find((o) => o.value === stockSectorFilter);
      const sectorLabel = sectorOption?.labelKey ? t(sectorOption.labelKey) : (sectorOption?.label ?? stockSectorFilter);
      chips.push({ key: "stockSector", label: t("markets.filters.chips.sector", { value: sectorLabel }) });
    }
    return chips;
  }, [
    isStockTab,
    bistTierFilter,
    maxChangePercent,
    maxDebtToEquity,
    maxPb,
    maxPe,
    maxPrice,
    minChangePercent,
    minNetProfitGrowth,
    minPb,
    minPe,
    minPrice,
    minRevenueGrowth,
    minRoa,
    minRoe,
    onlyWithFundamentals,
    sectorFilter,
    stockSectorFilter,
    marketFilter,
    t,
  ]);

  const hasAdvancedFilters = activeAdvancedFilterChips.length > 0;
  function clearAllAdvancedFilters() {
    setMinPrice("");
    setMaxPrice("");
    setMinChangePercent("");
    setMaxChangePercent("");
    setBistTierFilter("");
    setStockSectorFilter("");
    setMinPe("");
    setMaxPe("");
    setMinPb("");
    setMaxPb("");
    setMinRoe("");
    setMinRoa("");
    setMaxDebtToEquity("");
    setMinRevenueGrowth("");
    setMinNetProfitGrowth("");
    setSectorFilter("");
    setMarketFilter("");
    setOnlyWithFundamentals(false);
  }

  function clearAllMarketFilters() {
    setSearch("");
    setFxSourceMode("TCMB");
    setSortBy("name");
    clearAllAdvancedFilters();
  }

  function clearAdvancedFilter(key) {
    switch (key) {
      case "minPrice": setMinPrice(""); break;
      case "maxPrice": setMaxPrice(""); break;
      case "minChangePercent": setMinChangePercent(""); break;
      case "maxChangePercent": setMaxChangePercent(""); break;
      case "minPe": setMinPe(""); break;
      case "maxPe": setMaxPe(""); break;
      case "minPb": setMinPb(""); break;
      case "maxPb": setMaxPb(""); break;
      case "minRoe": setMinRoe(""); break;
      case "minRoa": setMinRoa(""); break;
      case "maxDebtToEquity": setMaxDebtToEquity(""); break;
      case "minRevenueGrowth": setMinRevenueGrowth(""); break;
      case "minNetProfitGrowth": setMinNetProfitGrowth(""); break;
      case "sector": setSectorFilter(""); break;
      case "market": setMarketFilter(""); break;
      case "onlyWithFundamentals": setOnlyWithFundamentals(false); break;
      case "bistTier": setBistTierFilter(""); break;
      case "stockSector": setStockSectorFilter(""); break;
      default: break;
    }
  }

  async function handleFavoriteToggle(item) {
    const symbolKey = getMarketActionSymbol(item);
    if (!symbolKey) return;
    if (!userId) {
      await login();
      return;
    }

    const busyKey = `${symbolKey}-${item.source ?? ""}`;
    const inList = isOnWatchlist(watchlistItems, symbolKey);
    const listId = resolveWatchlistId(watchlistItems, symbolKey);

    try {
      setFavoriteBusyKey(busyKey);
      if (inList && listId) {
        await removeWatchlistItem(listId);
        showToast("success", t("instrumentDetail.favoriteRemoved"));
      } else {
        await addWatchlistItem(userId, { instrumentCode: symbolKey });
        showToast("success", t("instrumentDetail.favoriteAdded"));
      }
      queryClient.invalidateQueries({ queryKey: watchlistKeys.byUser(userId) });
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("instrumentDetail.favoriteError")));
    } finally {
      setFavoriteBusyKey("");
    }
  }

  return (
    <div className="dashboard-stack market-terminal-page market-dashboard-page market-dashboard-page-v2">
      {toast ? (
        <div key={toast.id} className={`toast-notify ${toast.type}`}>
          {toast.type === "success" ? <Check size={15} strokeWidth={2.5} className="toast-notify-icon" /> : <X size={15} strokeWidth={2.5} className="toast-notify-icon" />}
          <span>{toast.message}</span>
        </div>
      ) : null}

      <div className="market-dashboard-layout">
        <section className="market-main-panel panel-surface">
          <div className="market-panel-head">
            <div className="market-dashboard-title">
              <h1>PİYASA VERİLERİ</h1>
              <p>Gerçek zamanlı piyasa verileri ve fiyatlamalar</p>
            </div>
            <div className="market-dashboard-actions">
              <div className="markets-view-toggle" role="tablist" aria-label={t("markets.listTitle")}>
                <button type="button" role="tab" aria-selected={viewMode === "table"} className={"market-segmented-tab " + (viewMode === "table" ? "active" : "")} onClick={() => setViewMode("table")}>{t("markets.tableView")}</button>
                <button type="button" role="tab" aria-selected={viewMode === "cards"} className={"market-segmented-tab " + (viewMode === "cards" ? "active" : "")} onClick={() => setViewMode("cards")}>{t("markets.cardView")}</button>
              </div>
            </div>
          </div>

          <div className="market-top-filter-row">
            <div className="market-category-cluster">
              <div className="market-category-tabs" role="tablist" aria-label={t("markets.category")}>
                {CATEGORY_OPTIONS.map((category) => {
                  const isActive = categoryFilter === category;
                  return (
                    <button key={category} type="button" role="tab" aria-selected={isActive} className={"market-tab " + (isActive ? "active" : "")} onClick={() => setCategoryFilter(category)}>
                      {formatCategoryLabel(category, t)}
                    </button>
                  );
                })}
              </div>
            </div>
            <label className="market-search-box"><span className="sr-only">{t("markets.searchLabel")}</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t("markets.searchPlaceholder")} /></label>
          </div>
        </section>

        <div className="market-results-layout">
          <aside className="market-filter-card" aria-label={t("markets.filters.title")}>
            <div className="market-filter-card-head">
              <strong>{t("markets.filters.title")}</strong>
            </div>
            {isStockTab ? (
              <div className="market-filter-mode-toggle">
                <button type="button" className={"market-filter-mode-btn" + (filterMode === "basic" ? " is-active" : "")} onClick={() => setFilterMode("basic")}>{t("markets.filters.basic")}</button>
                <button type="button" className={"market-filter-mode-btn" + (filterMode === "advanced" ? " is-active" : "")} onClick={() => setFilterMode("advanced")}>{t("markets.filters.advanced")}</button>
              </div>
            ) : null}
            <div className="market-advanced-shell">
              <div className="market-advanced-grid">
                {isStockTab ? (
                  <div className="market-filter-section">
                    <div className="market-filter-section-head"><h4>{t("markets.filters.index")}</h4></div>
                    <div className="market-filter-option-list" role="list">
                      {BIST_TIER_OPTIONS.map((option) => (
                        <button key={option.value} type="button" className={"market-filter-option " + (bistTierFilter === option.value ? "is-selected" : "")} onClick={() => setBistTierFilter(option.value)}>
                          <span className="market-filter-option-indicator" aria-hidden="true" />
                          <span>{option.labelKey ? t(option.labelKey) : option.label}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
                {isStockTab && filterMode === "advanced" ? (
                  <div className="market-filter-section">
                    <div className="market-filter-section-head"><h4>{t("markets.filters.sector")}</h4></div>
                    <div className="market-filter-option-list" role="list">
                      {STOCK_SECTOR_OPTIONS.map((option) => (
                        <button key={option.value} type="button" className={"market-filter-option " + (stockSectorFilter === option.value ? "is-selected" : "")} onClick={() => setStockSectorFilter(option.value)}>
                          <span className="market-filter-option-indicator" aria-hidden="true" />
                          <span>{option.labelKey ? t(option.labelKey) : option.label}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
                {isFxTab ? (
                  <div className="market-filter-section">
                    <div className="market-filter-section-head"><h4>{t("markets.columns.source")}</h4></div>
                    <div className="market-filter-option-list" role="list">
                      {FX_SOURCE_MODES.map((option) => (
                        <button key={option.value} type="button" className={"market-filter-option " + (fxSourceMode === option.value ? "is-selected" : "")} onClick={() => setFxSourceMode(option.value)}>
                          <span className="market-filter-option-indicator" aria-hidden="true" />
                          <span>{option.labelKey ? t(option.labelKey) : option.label}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
                <div className="market-filter-section">
                  <div className="market-filter-section-head"><h4>{t("markets.filters.sorting")}</h4></div>
                  <div className="market-filter-option-list" role="list">
                    {(isStockTab ? (filterMode === "advanced" ? STOCK_RANK_SORT_OPTIONS : STOCK_BASIC_SORT_OPTIONS) : DEFAULT_SORT_OPTIONS).map((option) => (
                      <button key={option.value} type="button" className={"market-filter-option " + (sortBy === option.value ? "is-selected" : "")} onClick={() => setSortBy(option.value)}>
                        <span className="market-filter-option-indicator" aria-hidden="true" />
                        <span>{option.labelKey ? t(option.labelKey) : option.label}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
            <button type="button" className="market-filter-clear" onClick={clearAllMarketFilters}>{t("markets.filters.clear")}</button>
            {hasAdvancedFilters ? <div className="market-active-filter-row market-active-filter-row--inline">{activeAdvancedFilterChips.map((chip) => (<button key={chip.key} type="button" className="market-active-filter-chip" onClick={() => clearAdvancedFilter(chip.key)}><span>{chip.label}</span><span aria-hidden>x</span></button>))}</div> : null}
          </aside>

          <div className="market-table-region">
            {loading ? (viewMode === "cards" ? <MarketCardSkeleton /> : <MarketTableSkeleton />) : null}
            {error ? <ErrorMessage message={error} /> : null}
            {!loading && !error && displayedCount === 0 ? <div className="market-contained-empty"><EmptyState title={isFavoritesTab ? (userId ? t("markets.favoritesEmptyTitle") : t("markets.favoritesLoginHint")) : t("markets.emptyTitle")} description={isFavoritesTab && userId ? t("markets.favoritesEmptyDescription") : t("markets.emptyDescription")} /></div> : null}
            {!loading && !error && isFxTab && fxSourceMode === "COMPARE" && fxComparisonRows.length > 0 ? <MarketFxComparisonTable rows={fxComparisonRows} navigate={navigate} formatRateConverted={formatRateConverted} t={t} /> : null}
            {!loading && !error && visibleQuotes.length > 0 && viewMode === "table" && !(isFxTab && fxSourceMode === "COMPARE") ? <MarketQuoteTable quotes={visibleQuotes} isFxTab={isFxTab} isFavoritesTab={isFavoritesTab} isStockTab={isStockTab} isFundTab={isFundTab} isBondTab={isBondTab} sortBy={sortBy} watchlistItems={watchlistItems} favoriteBusyKey={favoriteBusyKey} onFavoriteToggle={handleFavoriteToggle} navigate={navigate} formatRateConverted={formatRateConverted} t={t} locale={i18n.resolvedLanguage} /> : null}
            {!loading && !error && visibleQuotes.length > 0 && viewMode === "cards" && !(isFxTab && fxSourceMode === "COMPARE") ? <MarketQuoteCards quotes={visibleQuotes} isFxTab={isFxTab} isStockTab={isStockTab} isFundTab={isFundTab} watchlistItems={watchlistItems} favoriteBusyKey={favoriteBusyKey} onFavoriteToggle={handleFavoriteToggle} navigate={navigate} formatRateConverted={formatRateConverted} t={t} locale={i18n.resolvedLanguage} /> : null}
          </div>
          </div>
      </div>
    </div>
  );
}

function MarketTableSkeleton() {
  return (
    <div className="market-table-skeleton">
      {Array.from({ length: 8 }).map((_, index) => <div key={index} className="market-skeleton-row"><span /><span /><span /><span /><span /></div>)}
    </div>
  );
}

function MarketCardSkeleton() {
  return (
    <div className="market-card-grid">
      {Array.from({ length: 6 }).map((_, index) => <div key={index} className="market-quote-card market-card-skeleton"><span /><span /><span /></div>)}
    </div>
  );
}

function MarketFxComparisonTable({ rows, navigate, formatRateConverted, t }) {
  return (
    <div className="table-wrap finance-market-table-wrap finance-market-scroll-wrap">
      <table className="finance-market-table fx-compare-table">
        <thead>
          <tr>
            <th>{t("markets.categories.FX")}</th>
            <th>TCMB</th>
            <th>AKBANK</th>
            <th>{t("markets.filters.differencePercent")}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.code}>
              <td>
                <button type="button" className="finance-table-row-button" onClick={() => navigate(buildMarketDetailPath(row.detailSymbol, "FX"))}>
                  <span className="finance-table-symbol finance-table-symbol--with-flag">
                    <span className="market-instrument-flag-row">
                      {(() => { const cc = getCountryCodeForInstrument({ code: row.code }, "FX"); return cc ? <ReactCountryFlag countryCode={cc} svg style={{ width: "2em", height: "2em", borderRadius: "2px", display: "block", flexShrink: 0 }} title={cc} alt="" /> : null; })()}
                      <strong className="fx-compare-code">{row.code}</strong>
                    </span>
                  </span>
                </button>
              </td>
              <td>
                <div className="fx-compare-cell">
                  <div className="fx-compare-row">
                    <span className="fx-compare-label">{t("markets.columns.buy")}</span>
                    <span className="fx-compare-value">{formatRateConverted(row.tcmb?.buyRate)}</span>
                  </div>
                  <div className="fx-compare-row">
                    <span className="fx-compare-label">{t("markets.columns.sell")}</span>
                    <span className="fx-compare-value">{formatRateConverted(row.tcmb?.sellRate ?? row.tcmb?.price)}</span>
                  </div>
                </div>
              </td>
              <td>
                <div className="fx-compare-cell">
                  <div className="fx-compare-row">
                    <span className="fx-compare-label">{t("markets.columns.buy")}</span>
                    <span className="fx-compare-value">{formatRateConverted(row.akbank?.buyRate)}</span>
                  </div>
                  <div className="fx-compare-row">
                    <span className="fx-compare-label">{t("markets.columns.sell")}</span>
                    <span className="fx-compare-value">{formatRateConverted(row.akbank?.sellRate ?? row.akbank?.price)}</span>
                  </div>
                </div>
              </td>
              <td>
                {row.sellDiffPct === null ? (
                  <span className="tone-muted">-</span>
                ) : (
                  <div className="fx-diff-cell">
                    <div className="fx-diff-row">
                      <span className="fx-diff-label">En ucuz:</span>
                      <span className="fx-diff-cheap">{row.sellDiffPct > 0 ? "TCMB" : "AKBANK"}</span>
                    </div>
                    <div className="fx-diff-row">
                      <span className="fx-diff-label">En pahalı:</span>
                      <span className="fx-diff-expensive">{row.sellDiffPct > 0 ? "AKBANK" : "TCMB"}</span>
                    </div>
                    <div className="fx-diff-row">
                      <span className="fx-diff-label">Fark:</span>
                      <span className="fx-diff-pct">{formatMarketChange(Math.abs(row.sellDiffPct))}</span>
                    </div>
                  </div>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MarketQuoteTable({ quotes, isFxTab, isFavoritesTab, isStockTab, isFundTab, isBondTab, sortBy, watchlistItems, favoriteBusyKey, onFavoriteToggle, navigate, formatRateConverted, t, locale }) {
  const metricCol = resolveStockMetricCol(isStockTab, sortBy, t);
  const tableClass = isFxTab ? "market-data-table--fx" : isFundTab ? "market-data-table--fund" : isFavoritesTab ? "market-data-table--favorites" : metricCol ? "market-data-table--compact market-data-table--with-metric" : "market-data-table--compact";
  return (
    <div className="table-wrap finance-market-table-wrap market-data-table-wrap">
      <table className={"finance-market-table market-data-table " + tableClass}>
        <thead>
          <tr>
            <th className="col-favorite" scope="col"><span className="sr-only">{t("markets.columns.favorite")}</span></th>
            <th>{t("markets.columns.instrument")}</th>
            {isFavoritesTab ? <th>{t("markets.columns.type")}</th> : null}
            <th>{isBondTab ? t("markets.columns.yieldRate") : t("markets.columns.lastPrice")}</th>
            <th>{t("markets.columns.change")}</th>
            {isFundTab ? <th>{t("markets.fundColumns.fundType")}</th> : null}
            {isFundTab ? <th className="market-number-cell">{t("markets.fundColumns.risk")}</th> : null}
            {isFundTab ? <th className="market-number-cell">{t("markets.fundColumns.getiri1y")}</th> : null}
            {metricCol ? <th className="market-number-cell">{metricCol.label}</th> : null}
            {isFxTab ? <th>{t("markets.columns.buy")}</th> : null}
            {isFxTab ? <th>{t("markets.columns.sell")}</th> : null}
          </tr>
        </thead>
        <tbody>
          {quotes.map((item) => {
            const sym = getMarketActionSymbol(item);
            const busyKey = `${sym}-${item.source ?? ""}`;
            return (
              <tr key={`${item.symbol}-${item.source}-${item.marketCategory}`}>
                <td className="col-favorite">
                  <FavoriteStarButton active={isOnWatchlist(watchlistItems, sym)} disabled={!sym || favoriteBusyKey === busyKey} onToggle={() => onFavoriteToggle(item)} />
                </td>
                <td>
                  <button type="button" className="finance-table-row-button" onClick={() => navigate(buildMarketDetailPath(getMarketActionSymbol(item), item.instrumentType))}>
                    <MarketInstrumentLabel item={item} categoryFilter={item.marketCategory} variant="table" locale={locale} />
                  </button>
                </td>
                {isFavoritesTab ? (
                  <td><span className="market-type-badge">{formatInstrumentTypeLabel(item.marketCategory || item.instrumentType, t)}</span></td>
                ) : null}
                <td className="market-number-cell">{formatRateConverted(item.sellRate ?? resolveLastPrice(item), item)}</td>
                <td className={"market-change-cell " + getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</td>
                {isFundTab ? <td><span className="market-fund-type-chip">{formatFundType(item.fundType)}</span></td> : null}
                {isFundTab ? <td className="market-number-cell">{formatFundRisk(item.riskDegeri)}</td> : null}
                {isFundTab ? <td className={"market-number-cell " + getChangeToneClass(item.getiri1y)}>{formatFundReturn(item.getiri1y)}</td> : null}
                {metricCol ? <td className="market-number-cell">{metricCol.getValue(item)}</td> : null}
                {isFxTab ? <td className="market-number-cell">{formatRateConverted(item.buyRate, item)}</td> : null}
                {isFxTab ? <td className="market-number-cell">{formatRateConverted(item.sellRate, item)}</td> : null}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function MarketQuoteCards({ quotes, isFxTab, isStockTab, isFundTab, watchlistItems, favoriteBusyKey, onFavoriteToggle, navigate, formatRateConverted, t, locale }) {
  return (
    <div className="market-card-grid markets-card-scroll-wrap">
      {quotes.map((item) => {
        const sym = getMarketActionSymbol(item);
        const busyKey = `${sym}-${item.source ?? ""}`;
        return (
          <div key={`${item.symbol}-${item.source}-${item.marketCategory}-card`} className="market-quote-card-wrap">
            <button type="button" className="market-quote-card" onClick={() => navigate(buildMarketDetailPath(getMarketActionSymbol(item), item.instrumentType))}>
              <div className="market-quote-card-top">
                <MarketInstrumentLabel item={item} categoryFilter={item.marketCategory} variant="card" locale={locale} />
              </div>

              <div className="market-quote-card-body">
                {isFxTab ? (
                  <>
                    <div>
                      <span className="eyebrow">{t("markets.columns.buy")}</span>
                      <strong>{formatRateConverted(item.buyRate, item)}</strong>
                    </div>
                    <div>
                      <span className="eyebrow">{t("markets.columns.sell")}</span>
                      <strong>{formatRateConverted(item.sellRate ?? item.price, item)}</strong>
                    </div>
                    <div>
                      <span className="eyebrow">{t("markets.columns.change")}</span>
                      <strong className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</strong>
                    </div>
                  </>
                ) : (
                  <>
                    <div>
                      <span className="eyebrow">{t("markets.columns.lastPrice")}</span>
                      <strong>{formatRateConverted(item.buyRate ?? item.sellRate ?? item.price, item)}</strong>
                    </div>
                    <div>
                      <span className="eyebrow">{t("markets.columns.change")}</span>
                      <strong className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</strong>
                    </div>
                  </>
                )}
              </div>

              {isStockTab ? (
                <div className="market-quote-card-foot">
                  {renderFundamentalSummary(item, t)}
                </div>
              ) : isFundTab ? (
                <div className="market-quote-card-foot market-fund-card-foot">
                  {renderFundSummary(item, t)}
                </div>
              ) : null}
            </button>

            <div className="market-quote-card-floating-star">
              <FavoriteStarButton
                active={isOnWatchlist(watchlistItems, sym)}
                disabled={!sym || favoriteBusyKey === busyKey}
                onToggle={() => onFavoriteToggle(item)}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function MarketInstrumentLabel({ item, categoryFilter, variant = "table", locale = "tr" }) {
  const countryCode = getCountryCodeForInstrument(item, categoryFilter);
  const codeLabel = item.code || formatInstrumentCode(item.symbol) || "-";
  const rawName = item.displayName || item.instrumentType || "-";
  const normalizedCategory = String(categoryFilter || item.instrumentType || "").toUpperCase();
  const normalizedCode = String(codeLabel || "").toUpperCase();
  const parsedNameLabel = parseInstrumentNameLabel(rawName);
  const isFxDuplicateName = normalizedCategory === "FX"
    && [normalizedCode, `${normalizedCode}/TRY`].includes(String(parsedNameLabel).toUpperCase());
  const fxCodeNameLabel = normalizedCategory === "FX" ? getFxCodeLabel(codeLabel, locale) : "";
  const nameLabel = isFxDuplicateName && fxCodeNameLabel !== normalizedCode ? fxCodeNameLabel : parsedNameLabel;
  const showNameLabel = nameLabel && nameLabel !== "-" && String(nameLabel).toUpperCase() !== normalizedCode;
  const flagSize = variant === "card" ? "2.05em" : "1.5em";
  const flagStyle = {
    width: flagSize,
    height: flagSize,
    borderRadius: "2px",
    display: "block",
    flexShrink: 0,
  };

  if (variant === "card" || variant === "watch") {
    return (
      <div className={"market-instrument-card-root " + (variant === "watch" ? "market-instrument-card-root--watch" : "")}>
        <div className="market-instrument-flag-row">
          {countryCode ? <ReactCountryFlag countryCode={countryCode} svg style={flagStyle} title={countryCode} alt="" /> : null}
          <div className={"market-instrument-card-text " + (normalizedCategory === "FX" ? "market-instrument-card-text--inline" : "")}>
            <strong>{codeLabel}</strong>
            {showNameLabel ? <p>{nameLabel}</p> : null}
          </div>
        </div>
      </div>
    );
  }

  return (
    <span className="finance-table-symbol finance-table-symbol--with-flag">
      <span className="market-instrument-flag-row">
        {countryCode ? <ReactCountryFlag countryCode={countryCode} svg style={flagStyle} title={countryCode} alt="" /> : null}
        <span className="market-instrument-text-stack">
          <strong title={nameLabel}>{codeLabel}</strong>
          {showNameLabel ? <span title={nameLabel}>{nameLabel}</span> : null}
        </span>
      </span>
    </span>
  );
}

function parseInstrumentNameLabel(rawName) {
  const text = String(rawName || "").trim();
  if (!text) return "-";
  if (text.includes("||TYPE||")) {
    return text.split("||TYPE||")[0]?.trim() || text;
  }
  return text.includes("||")
    ? text.split("||").filter((s) => s && s !== "TYPE").join(" / ")
    : text;
}

function FavoriteStarButton({ active, disabled, onToggle }) {
  return (
    <button
      type="button"
      className={`market-favorite-star ${active ? "active" : ""}`}
      disabled={disabled}
      aria-pressed={active}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onToggle();
      }}
    >
      <Star size={18} strokeWidth={active ? 0 : 1.75} fill={active ? "currentColor" : "none"} aria-hidden />
    </button>
  );
}

function renderFundamentalSummary(item, t) {
  if (!hasFundamentals(item)) {
    return <span className="market-fundamental-empty">{t("markets.filters.noFundamentals")}</span>;
  }

  return (
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
      <span className="terminal-badge muted">F/K {formatRatioValue(item.peRatio)}</span>
      <span className="terminal-badge muted">PD/DD {formatRatioValue(item.pbRatio)}</span>
      <span className="terminal-badge muted">ROE {formatPercentValue(item.roe)}</span>
    </div>
  );
}

function renderFundSummary(item, t) {
  const badges = [
    item.fundType ? { key: "type", label: formatFundType(item.fundType) } : null,
    item.riskDegeri ? { key: "risk", label: `${t("markets.fundColumns.risk")} ${formatFundRisk(item.riskDegeri)}` } : null,
    item.getiri1y != null ? { key: "return1y", label: `${t("markets.fundColumns.getiri1y")} ${formatFundReturn(item.getiri1y)}`, tone: getChangeToneClass(item.getiri1y) } : null,
  ].filter(Boolean);

  if (badges.length === 0) {
    return <span className="market-fundamental-empty">{t("markets.fundColumns.noMetadata")}</span>;
  }

  return (
    <div className="market-fund-badge-row">
      {badges.map((badge) => (
        <span key={badge.key} className={"market-fund-badge " + (badge.tone || "")}>{badge.label}</span>
      ))}
    </div>
  );
}


function hasFundamentals(item) {
  return item?.peRatio != null || item?.pbRatio != null || item?.roe != null;
}

function classifyCategory(item, fallbackCategory) {
  const type = normalizeText(item?.instrumentType);
  if (type === "FOREX" || type === "CURRENCY") return "FX";
  return ["CRYPTO", "FX", "STOCK", "FUND", "FUTURES", "BOND", "INDEX", "COMMODITY"].includes(type) ? type : fallbackCategory;
}

function sortQuotes(left, right, sortBy) {
  if (sortBy === "price_desc") return toSortableNumber(right.price) - toSortableNumber(left.price);
  if (sortBy === "price_asc") return toSortableNumber(left.price) - toSortableNumber(right.price);
  if (sortBy === "change_desc") return toSortableNumber(right.changeRate) - toSortableNumber(left.changeRate);
  if (sortBy === "change_asc") return toSortableNumber(left.changeRate) - toSortableNumber(right.changeRate);
  if (sortBy === "pe_asc") return comparePositivePe(left.peRatio, right.peRatio, true);
  if (sortBy === "pe_desc") return comparePositivePe(left.peRatio, right.peRatio, false);
  if (sortBy === "pb_asc") return toSortableNumber(left.pbRatio) - toSortableNumber(right.pbRatio);
  if (sortBy === "pb_desc") return toSortableNumber(right.pbRatio) - toSortableNumber(left.pbRatio);

  const leftLabel = `${left.displayName || ""} ${left.symbol || ""}`.trim();
  const rightLabel = `${right.displayName || ""} ${right.symbol || ""}`.trim();
  return leftLabel.localeCompare(rightLabel, "tr");
}

function comparePositivePe(leftValue, rightValue, ascending) {
  const leftGroup = peSortGroup(leftValue);
  const rightGroup = peSortGroup(rightValue);
  if (leftGroup !== rightGroup) return leftGroup - rightGroup;

  if (leftGroup === 0) {
    const leftNumeric = Number(leftValue);
    const rightNumeric = Number(rightValue);
    return ascending ? leftNumeric - rightNumeric : rightNumeric - leftNumeric;
  }

  return 0;
}

function peSortGroup(value) {
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) return 0;
  if (Number.isFinite(numeric)) return 1;
  return 2;
}

function resolveLastPrice(item) {
  return item?.price ?? item?.lastPrice ?? item?.buyRate ?? item?.sellRate ?? null;
}

function formatCategoryLabel(value, t) {
  return t(`markets.categories.${value}`, { defaultValue: value ?? "-" });
}

function formatInstrumentTypeLabel(type, t) {
  const normalizedType = String(type || "").toUpperCase();
  const categoryKey = INSTRUMENT_TYPE_CATEGORY_KEYS[normalizedType] ?? normalizedType;
  return t(`markets.categories.${categoryKey}`, { defaultValue: type || "-" });
}

function formatMarketChange(value) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return String(value);
  return `%${numeric.toFixed(2)}`;
}

function formatRate(value) {
  if (value === null || value === undefined || value === "") return "-";
  return formatNumber(value);
}

function formatRatioValue(value) {
  if (value === null || value === undefined) return "-";
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric.toFixed(2) : "-";
}

function formatPercentValue(value) {
  if (value === null || value === undefined) return "-";
  const numeric = Number(value);
  return Number.isFinite(numeric) ? `%${(numeric * 100).toFixed(2)}` : "-";
}

function formatFundType(value) {
  if (!value) return "-";
  return String(value).replace(/\s+/g, " ").trim();
}

function formatFundRisk(value) {
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function formatFundReturn(value) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  return `%${numeric.toFixed(2)}`;
}

function resolveStockMetricCol(isStockTab, sortBy) {
  if (!isStockTab) return null;
  switch (sortBy) {
    case "pe_asc":              return { label: "F/K",        getValue: (item) => formatRatioValue(item.peRatio) };
    case "pb_asc":              return { label: "PD/DD",      getValue: (item) => formatRatioValue(item.pbRatio) };
    default: return null;
  }
}

function toSortableNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : -Infinity;
}

function useDebouncedValue(value, delayMs) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setDebouncedValue(value);
    }, delayMs);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [value, delayMs]);

  return debouncedValue;
}

function toComparableNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function normalizeText(value) {
  return value == null ? "" : String(value).trim().toUpperCase();
}

function getChangeToneClass(value) {
  if (value === null || value === undefined || value === "") return "tone-muted";
  return Number(value) >= 0 ? "tone-positive" : "tone-negative";
}

function normalizeCode(value) {
  if (value == null) return "";
  const rawValue = String(value).trim().toUpperCase();
  if (FX_PROVIDER_SYMBOL_PATTERN.test(rawValue)) {
    return rawValue;
  }
  return rawValue.replace(/[^A-Z0-9]/g, "");
}

function codesMatch(a, b) {
  const strip = (s) => String(s ?? "").replace(/[^A-Z0-9]/gi, "").toUpperCase();
  return normalizeCode(a) === normalizeCode(b) || strip(a) === strip(b);
}

function isOnWatchlist(rows, symbol) {
  return rows.some((row) => codesMatch(row.instrumentCode, symbol));
}

function resolveWatchlistId(rows, symbol) {
  return rows.find((row) => codesMatch(row.instrumentCode, symbol))?.id;
}

function toFilterNumber(value) {
  if (value === null || value === undefined || value === "") {
    return undefined;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : undefined;
}

function normalizeScreenSort(value) {
  return value || "name";
}

function mapScreenItem(item) {
  const normalizedType = String(item.type || "").toUpperCase();
  const isFxItem = normalizedType === "FX";
  const fxCode = isFxItem ? extractFxCode(item.symbol) : "";
  const source = item.source ? String(item.source).toUpperCase() : "";
  const symbol = isFxItem ? fxCode : item.symbol;

  return {
    symbol,
    code: isFxItem ? fxCode : item.symbol,
    providerSymbol: isFxItem && fxCode ? buildFxSymbol(source || "TCMB", fxCode) : null,
    displayName: item.name ?? (isFxItem && fxCode ? fxCode : item.symbol),
    buyRate: item.buyPrice ?? null,
    sellRate: item.sellPrice ?? null,
    price: item.lastPrice,
    changeRate: item.sellChangePercent ?? item.changePercent,
    source,
    instrumentType: normalizedType || item.type,
    displayUnit: item.displayUnit ?? (normalizedType === "INDEX" ? "POINT" : null),
    currency: item.currency ?? (normalizedType === "INDEX" ? null : "TRY"),
    marketCategory: normalizedType || item.type,
    sector: item.sector,
    market: item.market,
    volume: item.volume,
    marketCap: item.marketCap,
    peRatio: item.peRatio,
    pbRatio: item.pbRatio,
    roe: item.roe,
    roa: item.roa,
    netMargin: item.netMargin,
    debtToEquity: item.debtToEquity,
    revenueGrowth: item.revenueGrowth,
    netProfitGrowth: item.netProfitGrowth,
    fundType: item.fundType ?? null,
    riskDegeri: item.riskDegeri ?? null,
    getiri1a: item.getiri1a ?? null,
    getiri3a: item.getiri3a ?? null,
    getiri6a: item.getiri6a ?? null,
    getiriYb: item.getiriYb ?? null,
    getiri1y: item.getiri1y ?? null,
    getiri3y: item.getiri3y ?? null,
    getiri5y: item.getiri5y ?? null,
    calculatedAt: item.calculatedAt,
    dataTimestamp: item.dataTimestamp,
    bistTier: item.bistTier ?? null,
    stockSector: item.stockSector ?? null,
  };
}

function buildFxComparisonRows(quotes, queryValue) {
  const query = String(queryValue || "").trim().toLowerCase();
  const rowsByCode = new Map();

  for (const item of Array.isArray(quotes) ? quotes : []) {
    if (String(item?.instrumentType || "").toUpperCase() !== "FX") {
      continue;
    }

    const code = extractFxCode(item.code || item.symbol);
    const source = String(item.source || "").toUpperCase();
    if (!code || !AKBANK_VISIBLE_CODES.has(code) || (source !== "TCMB" && source !== "AKBANK")) {
      continue;
    }

    const searchable = `${code} ${item.displayName || ""}`.toLowerCase();
    if (query && !searchable.includes(query)) {
      continue;
    }

    const row = rowsByCode.get(code) || { code, tcmb: null, akbank: null, detailSymbol: buildFxSymbol("TCMB", code) };
    if (source === "TCMB") {
      row.tcmb = item;
    } else if (source === "AKBANK") {
      row.akbank = item;
    }
    rowsByCode.set(code, row);
  }

  return [...rowsByCode.values()]
    .filter((row) => row.tcmb && row.akbank)
    .map((row) => {
      const tcmbSell = toComparableNumber(row.tcmb?.sellRate ?? row.tcmb?.price);
      const akbankSell = toComparableNumber(row.akbank?.sellRate ?? row.akbank?.price);
      const sellDiffPct = tcmbSell !== null && akbankSell !== null && tcmbSell !== 0
        ? ((akbankSell - tcmbSell) / Math.abs(tcmbSell)) * 100
        : null;
      return { ...row, sellDiffPct };
    })
    .sort((left, right) => left.code.localeCompare(right.code, "tr"));
}

function extractFxCode(value) {
  if (value == null) {
    return "";
  }

  const rawValue = String(value).trim().toUpperCase();
  if (FX_PROVIDER_SYMBOL_PATTERN.test(rawValue)) {
    return rawValue.split(":")[1] || "";
  }

  return rawValue.replace(/[^A-Z0-9]/g, "");
}

function buildFxSymbol(source, code, side = "SELL") {
  const normalizedSource = String(source || "TCMB").trim().toUpperCase();
  const normalizedCode = extractFxCode(code);
  const normalizedSide = String(side || "SELL").trim().toUpperCase() === "BUY" ? "BUY" : "SELL";
  return normalizedCode ? `${normalizedSource}:${normalizedCode}:${normalizedSide}` : "";
}

function getMarketActionSymbol(item) {
  if (String(item?.instrumentType || item?.marketCategory || "").toUpperCase() === "FX") {
    return buildFxSymbol("TCMB", item?.code || item?.symbol);
  }

  return item?.symbol || item?.code || "";
}
