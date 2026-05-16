import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import ReactCountryFlag from "react-country-flag";
import { LayoutGrid, Star, TrendingDown, TrendingUp } from "lucide-react";
import { buildMarketDetailPath, getMarketsByType } from "../api/marketApi";
import { addWatchlistItem, getUserWatchlist, removeWatchlistItem } from "../api/watchlistApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import useToast from "../hooks/useToast";
import { formatNumber } from "../utils/formatters";
import { getCountryCodeForInstrument } from "../utils/currencyToCountryMap";

const CATEGORY_OPTIONS = ["FX", "CRYPTO", "STOCK", "FUND", "FUTURES", "BOND"];


const FUND_RETURN_COLUMNS = [
  { key: "getiri1a", labelKey: "markets.fundColumns.getiri1a", defaultLabel: "1 Ay" },
  { key: "getiri3a", labelKey: "markets.fundColumns.getiri3a", defaultLabel: "3 Ay" },
  { key: "getiri6a", labelKey: "markets.fundColumns.getiri6a", defaultLabel: "6 Ay" },
  { key: "getiriYb", labelKey: "markets.fundColumns.getiriYb", defaultLabel: "Yılbaşı" },
  { key: "getiri1y", labelKey: "markets.fundColumns.getiri1y", defaultLabel: "1 Yıl" },
  { key: "getiri3y", labelKey: "markets.fundColumns.getiri3y", defaultLabel: "3 Yıl" },
  { key: "getiri5y", labelKey: "markets.fundColumns.getiri5y", defaultLabel: "5 Yıl" },
];

export default function MarketsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { userId, login } = useAuth();
  const { toast, showToast } = useToast();

  // Market list state
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [movementFilter, setMovementFilter] = useState("all");
  const [categoryFilter, setCategoryFilter] = useState("FX");
  const [viewMode, setViewMode] = useState("table");
  const [sortBy, setSortBy] = useState("name");
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [watchlistItems, setWatchlistItems] = useState([]);
  const [favoriteBusyKey, setFavoriteBusyKey] = useState("");
  const deferredSearch = useDeferredValue(search);


  // ── Market list data ────────────────────────────────────────────────────────
  useEffect(() => {
    if (categoryFilter === "MACRO") return;

    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");
        const data = await getMarketsByType(categoryFilter);
        if (!active) return;

        const normalizedQuotes = Array.isArray(data) ? data : [];
        setQuotes(
          normalizedQuotes
            .filter((item) => item && typeof item === "object")
            .map((item) => ({ ...item, marketCategory: classifyCategory(item, categoryFilter) })),
        );
      } catch (err) {
        if (!active) return;
        setError(extractErrorMessage(err, t("markets.loadError")));
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => { active = false; };
  }, [categoryFilter, t]);

  useEffect(() => {
    if (!userId) {
      setWatchlistItems([]);
      setFavoritesOnly(false);
      return;
    }

    let active = true;

    async function loadWatchlist() {
      try {
        const rows = await getUserWatchlist(userId);
        if (active) setWatchlistItems(Array.isArray(rows) ? rows : []);
      } catch {
        if (active) setWatchlistItems([]);
      }
    }

    loadWatchlist();
    return () => { active = false; };
  }, [userId]);

  // ── Derived market list data ────────────────────────────────────────────────
  const sortOptions = useMemo(
    () => [
      { value: "name",        label: t("markets.sort.name") },
      { value: "price_desc",  label: t("markets.sort.priceDesc") },
      { value: "price_asc",   label: t("markets.sort.priceAsc") },
      { value: "change_desc", label: t("markets.sort.changeDesc") },
      { value: "change_asc",  label: t("markets.sort.changeAsc") },
    ],
    [t],
  );

  const movementOptions = useMemo(
    () => [
      { value: "all",         label: t("markets.movement.all") },
      { value: "top_gainers", label: t("markets.movement.topGainers") },
      { value: "top_losers",  label: t("markets.movement.topLosers") },
    ],
    [t],
  );

  const effectiveSortBy = useMemo(() => {
    if (movementFilter === "top_gainers") return "change_desc";
    if (movementFilter === "top_losers")  return "change_asc";
    return sortBy;
  }, [movementFilter, sortBy]);

  const filteredQuotes = useMemo(() => {
    const query = deferredSearch.trim().toLowerCase();
    return [...quotes]
      .filter((item) => {
        const matchesQuery =
          query.length === 0 ||
          item.symbol?.toLowerCase().includes(query) ||
          item.code?.toLowerCase().includes(query) ||
          item.displayName?.toLowerCase().includes(query);
        return matchesQuery;
      })
      .sort((left, right) => sortQuotes(left, right, effectiveSortBy));
  }, [quotes, deferredSearch, effectiveSortBy]);

  const visibleQuotes = useMemo(() => {
    if (!favoritesOnly || !userId) return filteredQuotes;
    const codes = new Set(watchlistItems.map((row) => normalizeCode(row.instrumentCode)));
    return filteredQuotes.filter((item) => codes.has(normalizeCode(item.symbol)));
  }, [filteredQuotes, favoritesOnly, userId, watchlistItems]);

  const marketPulse = useMemo(() => ({
    visible: visibleQuotes.length,
    positive: visibleQuotes.filter((item) => Number(item.changeRate) >= 0).length,
    negative: visibleQuotes.filter((item) => Number(item.changeRate) < 0).length,
  }), [visibleQuotes]);

  const isFxTable   = categoryFilter === "FX";
  const isFundTable = categoryFilter === "FUND";

  // ── Handlers ───────────────────────────────────────────────────────────────
  async function handleFavoritesOnlyChange(enabled) {
    if (enabled && !userId) { await login(); return; }
    setFavoritesOnly(enabled);
  }

  async function handleFavoriteToggle(item) {
    const symbolKey = item.symbol || item.code || "";
    if (!symbolKey) return;
    if (!userId) { await login(); return; }

    const busyKey = `${symbolKey}-${item.source ?? ""}`;
    const inList  = isOnWatchlist(watchlistItems, symbolKey);
    const listId  = resolveWatchlistId(watchlistItems, symbolKey);

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

  // ── Render ─────────────────────────────────────────────────────────────────
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

      <>
          <section className="market-toolbar panel-surface">
            <div className="market-toolbar-grid">
              <label className="market-filter-field">
                <span>{t("markets.searchLabel")}</span>
                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={t("markets.searchPlaceholder")}
                />
              </label>

              <label className="market-filter-field">
                <span>{t("markets.movement.label")}</span>
                <select value={movementFilter} onChange={(event) => setMovementFilter(event.target.value)}>
                  {movementOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>

              <label className="market-filter-field">
                <span>{t("markets.sorting")}</span>
                <select
                  value={sortBy}
                  disabled={movementFilter !== "all"}
                  title={movementFilter !== "all" ? t("markets.sortDisabledHint") : undefined}
                  onChange={(event) => setSortBy(event.target.value)}
                >
                  {sortOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>

              <div className="market-favorites-toggle-wrap">
                <span className="market-favorites-label">{t("markets.favoritesOnly")}</span>
                <button
                  type="button"
                  role="switch"
                  aria-checked={favoritesOnly}
                  className={`market-favorites-switch ${favoritesOnly ? "on" : ""}`}
                  onClick={() => handleFavoritesOnlyChange(!favoritesOnly)}
                  title={!userId ? t("markets.favoritesLoginHint") : undefined}
                >
                  <span className="market-favorites-knob" />
                </button>
              </div>
            </div>
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
              </div>

              <div className="markets-view-controls">
                <span className="terminal-badge muted">{t("common.records", { count: visibleQuotes.length })}</span>
                <div className="markets-view-toggle" role="tablist" aria-label={t("markets.listTitle")}>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={viewMode === "table"}
                    className={`market-segmented-tab ${viewMode === "table" ? "active" : ""}`}
                    onClick={() => setViewMode("table")}
                  >
                    {t("markets.tableView")}
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={viewMode === "cards"}
                    className={`market-segmented-tab ${viewMode === "cards" ? "active" : ""}`}
                    onClick={() => setViewMode("cards")}
                  >
                    {t("markets.cardView")}
                  </button>
                </div>
              </div>
            </div>

            {loading ? <LoadingSpinner label={t("markets.loading")} /> : null}
            {error ? <ErrorMessage message={error} /> : null}

            <div className="markets-list-body">
              {!loading && !error && visibleQuotes.length === 0 ? (
                <EmptyState
                  title={favoritesOnly && userId ? t("markets.favoritesEmptyTitle") : t("markets.emptyTitle")}
                  description={favoritesOnly && userId ? t("markets.favoritesEmptyDescription") : t("markets.emptyDescription")}
                />
              ) : null}

              {!loading && !error && visibleQuotes.length > 0 && viewMode === "table" && isFundTable ? (
                <div className="table-wrap finance-market-table-wrap finance-market-scroll-wrap fund-market-table-wrap">
                  <table className="finance-market-table fund-market-table">
                    <thead>
                      <tr>
                        <th className="col-favorite" scope="col">
                          <span className="sr-only">{t("markets.columns.favorite")}</span>
                        </th>
                        <th>{t("markets.columns.instrument")}</th>
                        <th>{t("markets.fundColumns.umbrellaType", { defaultValue: "Şemsiye Fon Türü" })}</th>
                        <th>{t("markets.fundColumns.risk", { defaultValue: "Risk" })}</th>
                        <th>{t("markets.columns.lastPrice")}</th>
                        {FUND_RETURN_COLUMNS.map((column) => (
                          <th key={column.key}>{t(column.labelKey, { defaultValue: column.defaultLabel })}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {visibleQuotes.map((item) => {
                        const sym = item.symbol || item.code || "";
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
                              <button
                                type="button"
                                className="finance-table-row-button"
                                onClick={() => navigate(buildMarketDetailPath(item.symbol, item.instrumentType))}
                              >
                                <MarketInstrumentLabel
                                  item={item}
                                  categoryFilter={categoryFilter}
                                  variant="table"
                                  stackClass="fund-table-symbol"
                                />
                              </button>
                            </td>
                            <td>
                              <div className="fund-table-type-cell">
                                <strong>{item.fonTurAciklama || item.fundType || "-"}</strong>
                                <span>{item.source || "TEFAS"}</span>
                              </div>
                            </td>
                            <td>
                              <span className={`fund-risk-badge ${getRiskToneClass(item.riskDegeri)}`}>{formatFundRisk(item.riskDegeri)}</span>
                            </td>
                            <td className="fund-price-cell">
                              <strong>{formatRate(item.price)}</strong>
                              <span className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</span>
                            </td>
                            {FUND_RETURN_COLUMNS.map((column) => (
                              <td key={`${item.symbol}-${column.key}`} className={getChangeToneClass(item[column.key])}>
                                {formatFundReturn(item[column.key])}
                              </td>
                            ))}
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : null}

              {!loading && !error && visibleQuotes.length > 0 && viewMode === "table" && !isFundTable ? (
                <div className="table-wrap finance-market-table-wrap finance-market-scroll-wrap">
                  <table className="finance-market-table">
                    <thead>
                      <tr>
                        <th className="col-favorite" scope="col">
                          <span className="sr-only">{t("markets.columns.favorite")}</span>
                        </th>
                        <th>{t("markets.columns.instrument")}</th>
                        {isFxTable ? <th>{t("markets.columns.buy")}</th> : null}
                        {isFxTable ? <th>{t("markets.columns.sell")}</th> : null}
                        {!isFxTable ? <th>{t("markets.columns.lastPrice")}</th> : null}
                        <th>{t("markets.columns.change")}</th>
                        <th>{t("markets.columns.source")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {visibleQuotes.map((item) => {
                        const sym = item.symbol || item.code || "";
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
                              <button
                                type="button"
                                className="finance-table-row-button"
                                onClick={() => navigate(buildMarketDetailPath(item.symbol, item.instrumentType))}
                              >
                                <MarketInstrumentLabel item={item} categoryFilter={categoryFilter} variant="table" />
                              </button>
                            </td>
                            {isFxTable ? <td>{formatRate(item.buyRate)}</td> : null}
                            {isFxTable ? <td>{formatRate(item.sellRate)}</td> : null}
                            {!isFxTable ? <td>{formatRate(item.price)}</td> : null}
                            <td className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</td>
                            <td>{item.source || "-"}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : null}

              {!loading && !error && visibleQuotes.length > 0 && viewMode === "cards" && isFundTable ? (
                <div className="market-card-grid markets-card-scroll-wrap fund-card-grid">
                  {visibleQuotes.map((item) => {
                    const sym = item.symbol || item.code || "";
                    const busyKey = `${sym}-${item.source ?? ""}`;
                    return (
                      <div key={`${item.symbol}-${item.source}-${item.marketCategory}-card`} className="market-quote-card-wrap market-quote-card-wrap--fund">
                        <button
                          type="button"
                          className="market-quote-card fund-quote-card"
                          onClick={() => navigate(buildMarketDetailPath(item.symbol, item.instrumentType))}
                        >
                          <div className="market-quote-card-top fund-quote-card-top">
                            <MarketInstrumentLabel item={item} categoryFilter={categoryFilter} variant="card" />
                            <span className={`fund-risk-badge ${getRiskToneClass(item.riskDegeri)}`}>{formatFundRisk(item.riskDegeri)}</span>
                          </div>

                          <div className="fund-quote-card-meta">
                            <span>{t("markets.fundColumns.umbrellaType", { defaultValue: "Şemsiye Fon Türü" })}</span>
                            <strong>{item.fonTurAciklama || item.fundType || "-"}</strong>
                          </div>

                          <div className="market-quote-card-body fund-quote-card-body">
                            <div>
                              <span className="eyebrow">{t("markets.columns.lastPrice")}</span>
                              <strong>{formatRate(item.price)}</strong>
                            </div>
                            <div>
                              <span className="eyebrow">{t("markets.columns.change")}</span>
                              <strong className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</strong>
                            </div>
                          </div>

                          <div className="fund-return-grid">
                            {FUND_RETURN_COLUMNS.map((column) => (
                              <div key={`${item.symbol}-${column.key}`} className="fund-return-grid-item">
                                <span>{t(column.labelKey, { defaultValue: column.defaultLabel })}</span>
                                <strong className={getChangeToneClass(item[column.key])}>{formatFundReturn(item[column.key])}</strong>
                              </div>
                            ))}
                          </div>
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

              {!loading && !error && visibleQuotes.length > 0 && viewMode === "cards" && !isFundTable ? (
                <div className="market-card-grid markets-card-scroll-wrap">
                  {visibleQuotes.map((item) => {
                    const sym = item.symbol || item.code || "";
                    const busyKey = `${sym}-${item.source ?? ""}`;
                    return (
                      <div key={`${item.symbol}-${item.source}-${item.marketCategory}-card`} className="market-quote-card-wrap">
                        <button
                          type="button"
                          className="market-quote-card"
                          onClick={() => navigate(buildMarketDetailPath(item.symbol, item.instrumentType))}
                        >
                          <div className="market-quote-card-top">
                            <MarketInstrumentLabel item={item} categoryFilter={categoryFilter} variant="card" />
                            <span className="terminal-badge muted">{item.source || "-"}</span>
                          </div>

                          <div className="market-quote-card-body">
                            <div>
                              <span className="eyebrow">
                                {isFxTable ? t("markets.columns.buy") : t("markets.columns.lastPrice")}
                              </span>
                              <strong>{formatRate(isFxTable ? item.buyRate : item.price)}</strong>
                            </div>
                            {isFxTable ? (
                              <div>
                                <span className="eyebrow">{t("markets.columns.sell")}</span>
                                <strong>{formatRate(item.sellRate)}</strong>
                              </div>
                            ) : null}
                          </div>

                          <div className="market-quote-card-foot">
                            <span>{formatCategoryLabel(item.marketCategory, t)}</span>
                            <strong className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</strong>
                          </div>
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
            </div>
          </section>
      </>
    </div>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

function MarketInstrumentLabel({ item, categoryFilter, variant = "table", stackClass = "" }) {
  const countryCode = getCountryCodeForInstrument(item, categoryFilter);
  const codeLabel   = item.code || item.symbol || "-";
  const nameLabel   = item.displayName || item.instrumentType || "-";

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
          {countryCode ? (
            <ReactCountryFlag countryCode={countryCode} svg style={flagStyle} title={countryCode} alt="" />
          ) : null}
          <div className="market-instrument-card-text">
            <strong>{codeLabel}</strong>
            <p>{nameLabel}</p>
          </div>
        </div>
      </div>
    );
  }

  const symbolClass = ["finance-table-symbol", "finance-table-symbol--with-flag", stackClass].filter(Boolean).join(" ");

  return (
    <span className={symbolClass}>
      <span className="market-instrument-flag-row">
        {countryCode ? (
          <ReactCountryFlag countryCode={countryCode} svg style={flagStyle} title={countryCode} alt="" />
        ) : null}
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

// ── Pure helpers ──────────────────────────────────────────────────────────────

function classifyCategory(item, fallbackCategory) {
  const type = normalizeText(item?.instrumentType);
  if (type === "FOREX" || type === "CURRENCY") return "FX";
  return ["CRYPTO", "FX", "STOCK", "FUND", "FUTURES", "BOND"].includes(type) ? type : fallbackCategory;
}

function sortQuotes(left, right, sortBy) {
  if (sortBy === "price_desc")  return toSortableNumber(right.price)      - toSortableNumber(left.price);
  if (sortBy === "price_asc")   return toSortableNumber(left.price)       - toSortableNumber(right.price);
  if (sortBy === "change_desc") return toSortableNumber(right.changeRate)  - toSortableNumber(left.changeRate);
  if (sortBy === "change_asc")  return toSortableNumber(left.changeRate)   - toSortableNumber(right.changeRate);

  const leftLabel  = `${left.displayName  || ""} ${left.symbol  || ""}`.trim();
  const rightLabel = `${right.displayName || ""} ${right.symbol || ""}`.trim();
  return leftLabel.localeCompare(rightLabel, "tr");
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

function formatFundReturn(value) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return String(value);
  return `%${numeric.toFixed(2)}`;
}

function formatFundRisk(value) {
  if (value === null || value === undefined || value === "") return "-";
  return `${value}/7`;
}

function formatRate(value) {
  if (value === null || value === undefined || value === "") return "-";
  return formatNumber(value);
}

function toSortableNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : -Infinity;
}

function normalizeText(value) {
  return value == null ? "" : String(value).trim().toUpperCase();
}

function getChangeToneClass(value) {
  if (value === null || value === undefined || value === "") return "tone-muted";
  return Number(value) >= 0 ? "tone-positive" : "tone-negative";
}

function getRiskToneClass(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return "neutral";
  if (numeric >= 6) return "high";
  if (numeric >= 4) return "medium";
  return "low";
}

function normalizeCode(value) {
  if (value == null) return "";
  const rawValue = String(value).trim();
  if (rawValue.toUpperCase().startsWith("TCMB:")) return rawValue.toUpperCase();
  return rawValue.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function isOnWatchlist(rows, symbol) {
  return rows.some((row) => normalizeCode(row.instrumentCode) === normalizeCode(symbol));
}

function resolveWatchlistId(rows, symbol) {
  return rows.find((row) => normalizeCode(row.instrumentCode) === normalizeCode(symbol))?.id;
}
