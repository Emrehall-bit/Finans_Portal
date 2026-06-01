import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";
import ReactCountryFlag from "react-country-flag";
import { LayoutGrid, Star, TrendingDown, TrendingUp } from "lucide-react";
import { buildMarketDetailPath } from "../api/marketApi";
import { addWatchlistItem, getUserWatchlist, removeWatchlistItem } from "../api/watchlistApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import useToast from "../hooks/useToast";
import { useMarketQuotes, useMarketScreen } from "../hooks/useMarketQueries";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { formatNumber } from "../utils/formatters";
import { formatInstrumentCode } from "../utils/instrumentUtils";
import { getCountryCodeForInstrument } from "../utils/currencyToCountryMap";

const CATEGORY_OPTIONS = ["FX", "CRYPTO", "STOCK", "FUND", "FUTURES", "BOND", "INDEX", "COMMODITY", "FAVORITES"];
const CURRENCY_CONVERTIBLE_CATEGORIES = new Set(["STOCK", "FUND", "COMMODITY", "INDEX", "FUTURES"]);
const STOCK_SCREEN_SORT_OPTIONS = [
  { value: "name", label: "Ada göre" },
  { value: "price_desc", label: "Fiyat yüksekten" },
  { value: "price_asc", label: "Fiyat düşükten" },
  { value: "change_desc", label: "Günlük değişim yüksekten" },
  { value: "change_asc", label: "Günlük değişim düşükten" },
  { value: "pe_asc", label: "F/K düşükten" },
  { value: "pe_desc", label: "F/K yüksekten" },
  { value: "pb_asc", label: "PD/DD düşükten" },
  { value: "pb_desc", label: "PD/DD yüksekten" },
  { value: "roe_desc", label: "ROE yüksekten" },
];
const DEFAULT_SORT_OPTIONS = [
  { value: "name", label: "Ada göre" },
  { value: "price_desc", label: "Fiyat yüksekten" },
  { value: "price_asc", label: "Fiyat düşükten" },
  { value: "change_desc", label: "Günlük değişim yüksekten" },
  { value: "change_asc", label: "Günlük değişim düşükten" },
];
const STOCK_FILTER_PRESETS = [
  { key: "cheap", label: "Ucuz Hisseler", values: { maxPe: "10" } },
  { key: "low-pb", label: "Düşük PD/DD", values: { maxPb: "1.5" } },
  { key: "high-roe", label: "Yüksek ROE", values: { minRoe: "15" } },
  { key: "low-debt", label: "Düşük Borçluluk", values: { maxDebtToEquity: "1" } },
  { key: "growth", label: "Büyüyen Şirketler", values: { minRevenueGrowth: "20" } },
  { key: "profitable-growth", label: "Kârlı Büyüme", values: { maxPe: "15", minRoe: "15", minRevenueGrowth: "10" } },
];
const STOCK_RANK_SORT_OPTIONS = [
  { value: "change_desc", label: "En Çok Yükselen" },
  { value: "change_asc", label: "En Çok Düşen" },
  { value: "pe_asc", label: "En Düşük F/K" },
  { value: "pb_asc", label: "En Düşük PD/DD" },
  { value: "roe_desc", label: "En Yüksek ROE" },
  { value: "roa_desc", label: "En Yüksek ROA" },
  { value: "revenue_growth_desc", label: "En Hızlı Büyüyen" },
  { value: "debt_to_equity", label: "En Düşük Borçluluk" },
  { value: "market_cap_desc", label: "En Büyük Piyasa Değeri" },
  { value: "name", label: "Ada göre" },
  { value: "price_desc", label: "Fiyat yüksekten" },
  { value: "price_asc", label: "Fiyat düşükten" },
];
const SEARCH_DEBOUNCE_MS = 275;
const FX_SOURCE_MODES = [
  { value: "TCMB", label: "TCMB" },
  { value: "AKBANK", label: "AKBANK" },
  { value: "COMPARE", label: "Karşılaştır" },
];
const AKBANK_VISIBLE_CODES = new Set(["AUD", "AZN", "CAD", "CHF", "CNY", "EUR", "GBP", "JPY", "KRW", "KWD", "QAR", "RUB", "USD"]);
const FX_PROVIDER_SYMBOL_PATTERN = /^[A-Z_]+:[A-Z]{2,4}:(BUY|SELL)$/;

export default function MarketsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { userId, login } = useAuth();
  const { toast, showToast } = useToast();
  const { formatAmount } = useCurrency();

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState(() => location.state?.category ?? "FX");
  const [sourceFilter, setSourceFilter] = useState("");
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
  const [watchlistItems, setWatchlistItems] = useState([]);
  const [favoriteBusyKey, setFavoriteBusyKey] = useState("");
  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  const isFavoritesTab = categoryFilter === "FAVORITES";
  const isStockTab = categoryFilter === "STOCK";
  const isFxTab = categoryFilter === "FX";

  const { data: allQuotes = [], isLoading: loadingAll } = useMarketQuotes({ enabled: isFavoritesTab });

  const screenParams = useMemo(() => ({
    type: categoryFilter,
    source: isFxTab ? (fxSourceMode !== "COMPARE" ? fxSourceMode : undefined) : sourceFilter || undefined,
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
    page: 0,
    size: 500,
    sort: normalizeScreenSort(sortBy),
  }), [
    categoryFilter,
    fxSourceMode,
    isFxTab,
    sourceFilter,
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

  const loading = isFavoritesTab ? loadingAll : loadingScreen;
  const error = isFavoritesTab
    ? ""
    : (screenError ? extractErrorMessage(screenError, t("markets.loadError")) : "");

  useEffect(() => {
    if (!userId) {
      setWatchlistItems([]);
      return;
    }

    let active = true;
    async function loadWatchlist() {
      try {
        const rows = await getUserWatchlist(userId);
        if (active) {
          setWatchlistItems(Array.isArray(rows) ? rows : []);
        }
      } catch {
        if (active) {
          setWatchlistItems([]);
        }
      }
    }

    loadWatchlist();
    return () => {
      active = false;
    };
  }, [userId]);

  const formatRateConverted = useCallback((value) => {
    if (value === null || value === undefined || value === "") {
      return "-";
    }
    if (CURRENCY_CONVERTIBLE_CATEGORIES.has(categoryFilter)) {
      return formatAmount(Number(value));
    }
    return formatRate(value);
  }, [categoryFilter, formatAmount]);

  const visibleQuotes = useMemo(() => {
    const query = debouncedSearch.trim().toLowerCase();

    if (isFavoritesTab) {
      const codes = new Set(watchlistItems.map((row) => normalizeCode(row.instrumentCode)));
      return [...allQuotes]
        .filter((item) => item && typeof item === "object")
        .map((item) => ({ ...item, marketCategory: classifyCategory(item, item.instrumentType || "OTHER") }))
        .filter((item) => {
          const onWatchlist = codes.has(normalizeCode(item.symbol)) || codes.has(normalizeCode(item.code));
          const matchesQuery = query.length === 0
            || item.symbol?.toLowerCase().includes(query)
            || item.code?.toLowerCase().includes(query)
            || item.displayName?.toLowerCase().includes(query);
          const matchesSource = !sourceFilter || String(item.source ?? "").toUpperCase() === sourceFilter.toUpperCase();
          return onWatchlist && matchesQuery && matchesSource;
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
    categoryFilter,
    debouncedSearch,
    isFavoritesTab,
    screenedQuotes,
    sourceFilter,
    sortBy,
    watchlistItems,
    fxSourceMode,
    isFxTab,
  ]);

  const fxComparisonRows = useMemo(
    () => buildFxComparisonRows(screenedQuotes, debouncedSearch),
    [screenedQuotes, debouncedSearch],
  );

  const displayedCount = isFxTab && fxSourceMode === "COMPARE" ? fxComparisonRows.length : visibleQuotes.length;
  const marketPulse = useMemo(() => ({
    visible: displayedCount,
    positive: visibleQuotes.filter((item) => Number(item.changeRate) >= 0).length,
    negative: visibleQuotes.filter((item) => Number(item.changeRate) < 0).length,
  }), [displayedCount, visibleQuotes]);

  const sourceOptions = useMemo(() => {
    const sourceItems = isFavoritesTab ? allQuotes : screenedQuotes;
    const sourceSet = new Set(
      sourceItems
        .map((item) => item.source)
        .filter(Boolean),
    );
    return [...sourceSet].sort();
  }, [allQuotes, isFavoritesTab, screenedQuotes]);

  const sectorOptions = useMemo(() => {
    const sectorSet = new Set(screenedQuotes.map((item) => item.sector).filter(Boolean));
    return [...sectorSet].sort((left, right) => left.localeCompare(right, "tr"));
  }, [screenedQuotes]);

  const marketOptions = useMemo(() => {
    const marketSet = new Set(screenedQuotes.map((item) => item.market).filter(Boolean));
    return [...marketSet].sort((left, right) => left.localeCompare(right, "tr"));
  }, [screenedQuotes]);

  const activeAdvancedFilterChips = useMemo(() => {
    if (!isStockTab) {
      return [];
    }

    const chips = [];
    if (minPe) chips.push({ key: "minPe", label: `F/K > ${minPe}` });
    if (maxPe) chips.push({ key: "maxPe", label: `F/K < ${maxPe}` });
    if (minPb) chips.push({ key: "minPb", label: `PD/DD > ${minPb}` });
    if (maxPb) chips.push({ key: "maxPb", label: `PD/DD < ${maxPb}` });
    if (minRoe) chips.push({ key: "minRoe", label: `ROE > ${minRoe}` });
    if (minRoa) chips.push({ key: "minRoa", label: `ROA > ${minRoa}` });
    if (maxDebtToEquity) chips.push({ key: "maxDebtToEquity", label: `Borç/Özkaynak < ${maxDebtToEquity}` });
    if (minRevenueGrowth) chips.push({ key: "minRevenueGrowth", label: `Hasılat büyümesi > ${minRevenueGrowth}` });
    if (minNetProfitGrowth) chips.push({ key: "minNetProfitGrowth", label: `Net kâr büyümesi > ${minNetProfitGrowth}` });
    if (sectorFilter) chips.push({ key: "sector", label: `Sektör: ${sectorFilter}` });
    if (marketFilter) chips.push({ key: "market", label: `Pazar: ${marketFilter}` });
    if (onlyWithFundamentals) chips.push({ key: "onlyWithFundamentals", label: "Temel analiz var" });
    return chips;
  }, [
    isStockTab,
    marketFilter,
    maxDebtToEquity,
    maxPb,
    maxPe,
    minNetProfitGrowth,
    minPb,
    minPe,
    minRevenueGrowth,
    minRoa,
    minRoe,
    onlyWithFundamentals,
    sectorFilter,
  ]);

  const hasAdvancedFilters = activeAdvancedFilterChips.length > 0;
  const activeSortLabel = useMemo(() => {
    const options = isStockTab ? STOCK_RANK_SORT_OPTIONS : DEFAULT_SORT_OPTIONS;
    return options.find((option) => option.value === sortBy)?.label ?? "Ada göre";
  }, [isStockTab, sortBy]);

  function applyStockPreset(values) {
    setMinPe(values.minPe ?? "");
    setMaxPe(values.maxPe ?? "");
    setMinPb(values.minPb ?? "");
    setMaxPb(values.maxPb ?? "");
    setMinRoe(values.minRoe ?? "");
    setMinRoa(values.minRoa ?? "");
    setMaxDebtToEquity(values.maxDebtToEquity ?? "");
    setMinRevenueGrowth(values.minRevenueGrowth ?? "");
    setMinNetProfitGrowth(values.minNetProfitGrowth ?? "");
    setSectorFilter(values.sector ?? "");
    setMarketFilter(values.market ?? "");
    setOnlyWithFundamentals(values.onlyWithFundamentals ?? false);
  }

  function clearAllAdvancedFilters() {
    applyStockPreset({});
  }

  function clearAdvancedFilter(key) {
    switch (key) {
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
      setWatchlistItems(await getUserWatchlist(userId));
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("instrumentDetail.favoriteError")));
    } finally {
      setFavoriteBusyKey("");
    }
  }

  return (
    <div className="dashboard-stack market-terminal-page market-terminal-page-v2">
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}

      <section className="market-page-hero panel-surface">
        <p className="eyebrow">{t("markets.eyebrow")}</p>
        <h1>{t("markets.title")}</h1>
        <p className="page-description">{t("markets.description")}</p>

        <div className="market-category-pills" role="tablist" aria-label={t("markets.category")}>
          {CATEGORY_OPTIONS.map((category) => {
            const isActive = categoryFilter === category;
            return (
              <button
                key={category}
                type="button"
                role="tab"
                aria-selected={isActive}
                className={`market-tab ${isActive ? "active" : ""}`}
                onClick={() => setCategoryFilter(category)}
              >
                {formatCategoryLabel(category, t)}
              </button>
            );
          })}
        </div>
      </section>

      <section className="market-toolbar panel-surface">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Filtreler</p>
            <h3>Basit Filtreler</h3>
          </div>
        </div>

        <div className="market-toolbar-grid">
          <label className="market-filter-field">
            <span>{t("markets.searchLabel")}</span>
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t("markets.searchPlaceholder")} />
          </label>

          {!isFxTab ? <label className="market-filter-field">
            <span>Enstrüman tipi</span>
            <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
              {CATEGORY_OPTIONS.filter((item) => item !== "FAVORITES").map((category) => (
                <option key={category} value={category}>{formatCategoryLabel(category, t)}</option>
              ))}
            </select>
          </label> : null}

          {isFxTab ? (
            <label className="market-filter-field">
              <span>{t("markets.columns.source")}</span>
              <select value={fxSourceMode} onChange={(event) => setFxSourceMode(event.target.value)}>
                {FX_SOURCE_MODES.map((mode) => (
                  <option key={mode.value} value={mode.value}>{mode.label}</option>
                ))}
              </select>
            </label>
          ) : null}

          {!isFxTab ? <label className="market-filter-field">
            <span>{t("markets.columns.source")}</span>
            <select value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value)}>
              <option value="">Tümü</option>
              {sourceOptions.map((source) => (
                <option key={source} value={source}>{source}</option>
              ))}
            </select>
          </label> : null}

          <label className="market-filter-field">
            <span>{isStockTab ? "Sırala" : t("markets.sorting")}</span>
            <select value={sortBy} onChange={(event) => setSortBy(event.target.value)}>
              {(isStockTab ? STOCK_RANK_SORT_OPTIONS : DEFAULT_SORT_OPTIONS).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>

          <label className="market-filter-field">
            <span>Fiyat min</span>
            <input value={minPrice} onChange={(event) => setMinPrice(event.target.value)} inputMode="decimal" />
          </label>

          <label className="market-filter-field">
            <span>Fiyat max</span>
            <input value={maxPrice} onChange={(event) => setMaxPrice(event.target.value)} inputMode="decimal" />
          </label>

          <label className="market-filter-field">
            <span>Günlük değişim min %</span>
            <input value={minChangePercent} onChange={(event) => setMinChangePercent(event.target.value)} inputMode="decimal" />
          </label>

          <label className="market-filter-field">
            <span>Günlük değişim max %</span>
            <input value={maxChangePercent} onChange={(event) => setMaxChangePercent(event.target.value)} inputMode="decimal" />
          </label>

          {CURRENCY_CONVERTIBLE_CATEGORIES.has(categoryFilter) ? (
            <div className="market-filter-field market-currency-toggle-wrap">
              <span>Para Birimi</span>
              <CurrencyToggle className="analysis-currency-toggle" />
            </div>
          ) : null}
        </div>

        {isStockTab ? (
          <>
            <div className="panel-head" style={{ marginTop: 16 }}>
              <div>
                <p className="eyebrow">Filtreler</p>
                <h3>İleri Filtreler</h3>
              </div>
              <div className="market-inline-actions">
                <button type="button" className="market-inline-button" onClick={clearAllAdvancedFilters}>
                  Tüm filtreleri temizle
                </button>
              </div>
            </div>
            <div className="admin-import-field">
              <span>Hazır Filtreler</span>
            </div>
            <div className="market-preset-row">
              {STOCK_FILTER_PRESETS.map((preset) => (
                <button
                  key={preset.key}
                  type="button"
                  className="terminal-badge muted market-preset-chip"
                  onClick={() => applyStockPreset(preset.values)}
                >
                  {preset.label}
                </button>
              ))}
            </div>
            <div className="market-toolbar-grid">
              <label className="market-filter-field">
                <span>F/K min</span>
                <input value={minPe} onChange={(event) => setMinPe(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>F/K max</span>
                <input value={maxPe} onChange={(event) => setMaxPe(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>PD/DD min</span>
                <input value={minPb} onChange={(event) => setMinPb(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>PD/DD max</span>
                <input value={maxPb} onChange={(event) => setMaxPb(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>ROE min</span>
                <input value={minRoe} onChange={(event) => setMinRoe(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>ROA min</span>
                <input value={minRoa} onChange={(event) => setMinRoa(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>Borç/Özkaynak max</span>
                <input value={maxDebtToEquity} onChange={(event) => setMaxDebtToEquity(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>Hasılat büyümesi min</span>
                <input value={minRevenueGrowth} onChange={(event) => setMinRevenueGrowth(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>Net kâr büyümesi min</span>
                <input value={minNetProfitGrowth} onChange={(event) => setMinNetProfitGrowth(event.target.value)} inputMode="decimal" />
              </label>
              <label className="market-filter-field">
                <span>Sektör</span>
                <select value={sectorFilter} onChange={(event) => setSectorFilter(event.target.value)}>
                  <option value="">Tümü</option>
                  {sectorOptions.map((sector) => (
                    <option key={sector} value={sector}>{sector}</option>
                  ))}
                </select>
              </label>
              <label className="market-filter-field">
                <span>Pazar</span>
                <select value={marketFilter} onChange={(event) => setMarketFilter(event.target.value)}>
                  <option value="">Tümü</option>
                  {marketOptions.map((market) => (
                    <option key={market} value={market}>{market}</option>
                  ))}
                </select>
              </label>
              <label className="market-filter-field market-filter-field-checkbox">
                <span>Sadece temel analiz verisi olanlar</span>
                <input
                  type="checkbox"
                  checked={onlyWithFundamentals}
                  onChange={(event) => setOnlyWithFundamentals(event.target.checked)}
                />
              </label>
            </div>
            {hasAdvancedFilters ? (
              <div className="market-active-filter-row">
                {activeAdvancedFilterChips.map((chip) => (
                  <button
                    key={chip.key}
                    type="button"
                    className="market-active-filter-chip"
                    onClick={() => clearAdvancedFilter(chip.key)}
                  >
                    <span>{chip.label}</span>
                    <span aria-hidden>x</span>
                  </button>
                ))}
              </div>
            ) : null}
          </>
        ) : null}
      </section>

      <div className="market-pulse-row market-pulse-row-v2">
        <div className="market-pulse-card">
          <div className="market-pulse-card-head">
            <span>{t("markets.visibleData")}</span>
            <LayoutGrid className="market-pulse-icon neutral" size={18} strokeWidth={1.75} aria-hidden />
          </div>
          <strong>{marketPulse.visible}</strong>
        </div>
        <div className="market-pulse-card up">
          <div className="market-pulse-card-head">
            <span>{t("markets.positive")}</span>
            <TrendingUp className="market-pulse-icon up" size={18} strokeWidth={1.75} aria-hidden />
          </div>
          <strong>{marketPulse.positive}</strong>
        </div>
        <div className="market-pulse-card down">
          <div className="market-pulse-card-head">
            <span>{t("markets.negative")}</span>
            <TrendingDown className="market-pulse-icon down" size={18} strokeWidth={1.75} aria-hidden />
          </div>
          <strong>{marketPulse.negative}</strong>
        </div>
      </div>

      <section className="panel-surface market-watchlist-column market-list-main">
        <div className="panel-head market-instrument-panel-head">
          <div className="market-instrument-panel-head-title">
            <p className="eyebrow">{t("markets.listEyebrow")}</p>
            <h3>{formatCategoryLabel(categoryFilter, t)}</h3>
            <div className="market-result-meta">
              <span>{displayedCount} sonuç bulundu</span>
              {isStockTab && hasAdvancedFilters ? <span className="terminal-badge muted">İleri filtre aktif</span> : null}
              {isStockTab ? <span className="terminal-badge muted">Sıralama: {activeSortLabel}</span> : null}
            </div>
          </div>
          <div className="markets-view-controls">
            <span className="terminal-badge muted">{t("common.records", { count: displayedCount })}</span>
            <div className="markets-view-toggle" role="tablist" aria-label={t("markets.listTitle")}>
              <button type="button" role="tab" aria-selected={viewMode === "table"} className={`market-segmented-tab ${viewMode === "table" ? "active" : ""}`} onClick={() => setViewMode("table")}>
                {t("markets.tableView")}
              </button>
              <button type="button" role="tab" aria-selected={viewMode === "cards"} className={`market-segmented-tab ${viewMode === "cards" ? "active" : ""}`} onClick={() => setViewMode("cards")}>
                {t("markets.cardView")}
              </button>
            </div>
          </div>
        </div>

        {loading ? <LoadingSpinner label={t("markets.loading")} /> : null}
        {error ? <ErrorMessage message={error} /> : null}

        {!loading && !error && displayedCount === 0 ? (
          <EmptyState
            title={isFavoritesTab && userId ? t("markets.favoritesEmptyTitle") : t("markets.emptyTitle")}
            description={isFavoritesTab && userId ? t("markets.favoritesEmptyDescription") : t("markets.emptyDescription")}
          />
        ) : null}

        {!loading && !error && isFxTab && fxSourceMode === "COMPARE" && fxComparisonRows.length > 0 ? (
          <div className="table-wrap finance-market-table-wrap finance-market-scroll-wrap">
            <table className="finance-market-table">
              <thead>
                <tr>
                  <th>Döviz</th>
                  <th>TCMB Alış</th>
                  <th>TCMB Satış</th>
                  <th>AKBANK Alış</th>
                  <th>AKBANK Satış</th>
                  <th>Satış Farkı %</th>
                </tr>
              </thead>
              <tbody>
                {fxComparisonRows.map((row) => (
                  <tr key={row.code}>
                    <td>
                      <button type="button" className="finance-table-row-button" onClick={() => navigate(buildMarketDetailPath(row.detailSymbol, "FX"))}>
                        <strong>{row.code}</strong>
                      </button>
                    </td>
                    <td>{formatRateConverted(row.tcmb?.buyRate)}</td>
                    <td>{formatRateConverted(row.tcmb?.sellRate ?? row.tcmb?.price)}</td>
                    <td>{formatRateConverted(row.akbank?.buyRate)}</td>
                    <td>{formatRateConverted(row.akbank?.sellRate ?? row.akbank?.price)}</td>
                    <td className={getChangeToneClass(row.sellDiffPct)}>{formatMarketChange(row.sellDiffPct)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {!loading && !error && visibleQuotes.length > 0 && viewMode === "table" && !(isFxTab && fxSourceMode === "COMPARE") ? (
          <div className="table-wrap finance-market-table-wrap finance-market-scroll-wrap">
            <table className="finance-market-table">
              <thead>
                <tr>
                  <th className="col-favorite" scope="col">
                    <span className="sr-only">{t("markets.columns.favorite")}</span>
                  </th>
                  <th>{t("markets.columns.instrument")}</th>
                  {isFxTab ? <th>Alis</th> : <th>{t("markets.columns.lastPrice")}</th>}
                  {isFxTab ? <th>Satis</th> : <th>{t("markets.columns.change")}</th>}
                  {isFxTab ? <th>{t("markets.columns.change")}</th> : null}
                  <th>{t("markets.columns.source")}</th>
                  {isStockTab ? <th>Oran Özeti</th> : null}
                </tr>
              </thead>
              <tbody>
                {visibleQuotes.map((item) => {
                  const sym = getMarketActionSymbol(item);
                  const busyKey = `${sym}-${item.source ?? ""}`;
                  return (
                    <tr key={`${item.symbol}-${item.source}-${item.marketCategory}`}>
                      <td className="col-favorite">
                        <FavoriteStarButton
                          active={isOnWatchlist(watchlistItems, sym)}
                          disabled={!sym || favoriteBusyKey === busyKey}
                          onToggle={() => handleFavoriteToggle(item)}
                        />
                      </td>
                      <td>
                        <button type="button" className="finance-table-row-button" onClick={() => navigate(buildMarketDetailPath(getMarketActionSymbol(item), item.instrumentType))}>
                          <MarketInstrumentLabel item={item} categoryFilter={item.marketCategory} variant="table" />
                        </button>
                      </td>
                      {isFxTab ? <td>{formatRateConverted(item.buyRate)}</td> : <td>{formatRateConverted(item.buyRate ?? item.sellRate ?? item.price)}</td>}
                      {isFxTab ? <td>{formatRateConverted(item.sellRate ?? item.price)}</td> : <td className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</td>}
                      {isFxTab ? <td className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</td> : null}
                      <td>{item.source || "-"}</td>
                      {isStockTab ? <td>{renderFundamentalSummary(item)}</td> : null}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}

        {!loading && !error && visibleQuotes.length > 0 && viewMode === "cards" && !(isFxTab && fxSourceMode === "COMPARE") ? (
          <div className="market-card-grid markets-card-scroll-wrap">
            {visibleQuotes.map((item) => {
              const sym = getMarketActionSymbol(item);
              const busyKey = `${sym}-${item.source ?? ""}`;
              return (
                <div key={`${item.symbol}-${item.source}-${item.marketCategory}-card`} className="market-quote-card-wrap">
                  <button type="button" className="market-quote-card" onClick={() => navigate(buildMarketDetailPath(getMarketActionSymbol(item), item.instrumentType))}>
                    <div className="market-quote-card-top">
                      <MarketInstrumentLabel item={item} categoryFilter={item.marketCategory} variant="card" />
                      <span className="terminal-badge muted">{item.source || "-"}</span>
                    </div>

                    <div className="market-quote-card-body">
                      {isFxTab ? (
                        <>
                          <div>
                            <span className="eyebrow">Alis</span>
                            <strong>{formatRateConverted(item.buyRate)}</strong>
                          </div>
                          <div>
                            <span className="eyebrow">Satis</span>
                            <strong>{formatRateConverted(item.sellRate ?? item.price)}</strong>
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
                            <strong>{formatRateConverted(item.buyRate ?? item.sellRate ?? item.price)}</strong>
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
                        {renderFundamentalSummary(item)}
                      </div>
                    ) : (
                      <div className="market-quote-card-foot">
                        <span>{formatCategoryLabel(item.marketCategory, t)}</span>
                        <strong className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</strong>
                      </div>
                    )}
                  </button>

                  <div className="market-quote-card-floating-star">
                    <FavoriteStarButton
                      active={isOnWatchlist(watchlistItems, sym)}
                      disabled={!sym || favoriteBusyKey === busyKey}
                      onToggle={() => handleFavoriteToggle(item)}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        ) : null}
      </section>
    </div>
  );
}

function MarketInstrumentLabel({ item, categoryFilter, variant = "table" }) {
  const countryCode = getCountryCodeForInstrument(item, categoryFilter);
  const codeLabel = item.code || formatInstrumentCode(item.symbol) || "-";
  const nameLabel = item.displayName || item.instrumentType || "-";
  const flagStyle = {
    width: "1.25em",
    height: "1.25em",
    borderRadius: "2px",
    display: "block",
    flexShrink: 0,
  };

  if (variant === "card") {
    return (
      <div className="market-instrument-card-root">
        <div className="market-instrument-flag-row">
          {countryCode ? <ReactCountryFlag countryCode={countryCode} svg style={flagStyle} title={countryCode} alt="" /> : null}
          <div className="market-instrument-card-text">
            <strong>{codeLabel}</strong>
            <p>{nameLabel}</p>
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
          <strong>{codeLabel}</strong>
          <span>{nameLabel}</span>
        </span>
      </span>
    </span>
  );
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

function renderFundamentalSummary(item) {
  if (!hasFundamentals(item)) {
    return <span className="terminal-badge muted">Temel analiz yok</span>;
  }

  return (
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
      <span className="terminal-badge muted">F/K {formatRatioValue(item.peRatio)}</span>
      <span className="terminal-badge muted">PD/DD {formatRatioValue(item.pbRatio)}</span>
      <span className="terminal-badge muted">ROE {formatPercentValue(item.roe)}</span>
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
  if (sortBy === "roe_desc") return toSortableNumber(right.roe) - toSortableNumber(left.roe);

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

function formatCategoryLabel(value, t) {
  return t(`markets.categories.${value}`, { defaultValue: value ?? "-" });
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

function isOnWatchlist(rows, symbol) {
  return rows.some((row) => normalizeCode(row.instrumentCode) === normalizeCode(symbol));
}

function resolveWatchlistId(rows, symbol) {
  return rows.find((row) => normalizeCode(row.instrumentCode) === normalizeCode(symbol))?.id;
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
  const symbol = isFxItem ? buildFxSymbol(source || "TCMB", fxCode) : item.symbol;

  return {
    symbol,
    code: isFxItem ? fxCode : item.symbol,
    displayName: item.name ?? (isFxItem && fxCode ? `${fxCode}/TRY` : item.symbol),
    buyRate: item.buyPrice ?? null,
    sellRate: item.sellPrice ?? null,
    price: item.lastPrice,
    changeRate: item.sellChangePercent ?? item.changePercent,
    source,
    instrumentType: normalizedType || item.type,
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
    calculatedAt: item.calculatedAt,
    dataTimestamp: item.dataTimestamp,
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
