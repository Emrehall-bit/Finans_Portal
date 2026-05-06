import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { getMarkets, getMarketsByType } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { formatNumber } from "../utils/formatters";

const SPOTLIGHT_SYMBOLS = ["XU100", "BIST100", "USDTRY", "EURTRY", "GRAMALTIN", "BTCUSDT", "BTC", "ETHUSDT", "ETH"];
const CATEGORY_OPTIONS = ["ALL", "CRYPTO", "FX", "STOCK", "FUND"];
export default function MarketsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [sourceFilter, setSourceFilter] = useState("ALL");
  const [categoryFilter, setCategoryFilter] = useState("ALL");
  const [sortBy, setSortBy] = useState("name");
  const [viewMode, setViewMode] = useState("table");
  const deferredSearch = useDeferredValue(search);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");
        const data =
          categoryFilter === "ALL"
            ? await getMarkets()
            : await getMarketsByType(categoryFilter);
        if (!active) {
          return;
        }

        const normalizedQuotes = Array.isArray(data) ? data : [];
        setQuotes(
          normalizedQuotes
            .filter((item) => item && typeof item === "object")
            .map((item) => ({ ...item, marketCategory: classifyCategory(item) })),
        );
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, t("markets.loadError")));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    load();

    return () => {
      active = false;
    };
  }, [categoryFilter, t]);

  const sortOptions = useMemo(
    () => [
      { value: "name", label: t("markets.sort.name") },
      { value: "price_desc", label: t("markets.sort.priceDesc") },
      { value: "price_asc", label: t("markets.sort.priceAsc") },
      { value: "change_desc", label: t("markets.sort.changeDesc") },
      { value: "change_asc", label: t("markets.sort.changeAsc") },
    ],
    [t],
  );

  const sources = useMemo(() => {
    return ["ALL", ...new Set(quotes.map((item) => item.source).filter(Boolean))];
  }, [quotes]);

  const filteredQuotes = useMemo(() => {
    const query = deferredSearch.trim().toLowerCase();

    return [...quotes]
      .filter((item) => {
        const matchesSource = sourceFilter === "ALL" || item.source === sourceFilter;
        const matchesCategory = categoryFilter === "ALL" || item.marketCategory === categoryFilter;
        const matchesQuery =
          query.length === 0 ||
          item.symbol?.toLowerCase().includes(query) ||
          item.displayName?.toLowerCase().includes(query);

        return matchesSource && matchesCategory && matchesQuery;
      })
      .sort((left, right) => sortQuotes(left, right, sortBy));
  }, [quotes, deferredSearch, sourceFilter, categoryFilter, sortBy]);

  const spotlightQuotes = useMemo(() => {
    const priority = SPOTLIGHT_SYMBOLS.map((symbol) =>
      filteredQuotes.find((item) => item.symbol?.toUpperCase() === symbol),
    ).filter(Boolean);

    const movers = [...filteredQuotes]
      .sort((left, right) => Math.abs(Number(right.changeRate) || 0) - Math.abs(Number(left.changeRate) || 0))
      .slice(0, 6);

    return uniqueBySymbol([...priority, ...movers]).slice(0, 6);
  }, [filteredQuotes]);

  const marketPulse = useMemo(() => {
    const positive = filteredQuotes.filter((item) => Number(item.changeRate) >= 0).length;
    const negative = filteredQuotes.filter((item) => Number(item.changeRate) < 0).length;

    return {
      visible: filteredQuotes.length,
      positive,
      negative,
    };
  }, [filteredQuotes]);

  const categorySummary = useMemo(() => {
    return CATEGORY_OPTIONS.filter((item) => item !== "ALL").map((category) => ({
      category,
      count: filteredQuotes.filter((quote) => quote.marketCategory === category).length,
    }));
  }, [filteredQuotes]);

  return (
    <div className="dashboard-stack market-terminal-page">
      <section className="market-terminal-hero panel-surface">
        <div className="market-terminal-copy">
          <p className="eyebrow">{t("markets.eyebrow")}</p>
          <h1>{t("markets.title")}</h1>
          <p className="page-description">{t("markets.description")}</p>

          <div className="market-terminal-search market-terminal-search-extended">
            <label className="market-filter-field">
              <span>{t("markets.searchLabel")}</span>
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={t("markets.searchPlaceholder")}
              />
            </label>

            <label className="market-filter-field">
              <span>{t("markets.category")}</span>
              <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
                {CATEGORY_OPTIONS.map((category) => (
                  <option key={category} value={category}>
                    {formatCategoryLabel(category, t)}
                  </option>
                ))}
              </select>
            </label>

            <label className="market-filter-field">
              <span>{t("markets.source")}</span>
              <select value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value)}>
                {sources.map((source) => (
                  <option key={source} value={source}>
                    {source}
                  </option>
                ))}
              </select>
            </label>

            <label className="market-filter-field">
              <span>{t("markets.sorting")}</span>
              <select value={sortBy} onChange={(event) => setSortBy(event.target.value)}>
                {sortOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="market-pulse-row">
            <div className="market-pulse-card">
              <span>{t("markets.visibleData")}</span>
              <strong>{marketPulse.visible}</strong>
            </div>
            <div className="market-pulse-card up">
              <span>{t("markets.positive")}</span>
              <strong>{marketPulse.positive}</strong>
            </div>
            <div className="market-pulse-card down">
              <span>{t("markets.negative")}</span>
              <strong>{marketPulse.negative}</strong>
            </div>
          </div>

          <div className="market-category-strip">
            {categorySummary.map((item) => (
              <div key={item.category} className="market-category-pill">
                <span>{formatCategoryLabel(item.category, t)}</span>
                <strong>{item.count}</strong>
              </div>
            ))}
          </div>
        </div>

        <div className="market-terminal-spotlight">
          <div className="panel-head">
            <div>
              <p className="eyebrow">{t("markets.spotlightEyebrow")}</p>
              <h3>{t("markets.spotlightTitle")}</h3>
            </div>
            <span className="terminal-badge">{t("common.live")}</span>
          </div>

          <div className="terminal-spotlight-grid">
            {spotlightQuotes.map((item) => (
              <button
                key={`${item.symbol}-${item.source}`}
                type="button"
                className="spotlight-quote-card"
                onClick={() => navigate(`/markets/${encodeURIComponent(item.symbol)}`)}
              >
                <div>
                  <strong>{item.symbol || "-"}</strong>
                  <span>{item.displayName || formatCategoryLabel(item.marketCategory, t) || t("markets.marketFeed")}</span>
                </div>
                <div className="spotlight-quote-metric">
                  <strong>{formatNumber(item.price)}</strong>
                  <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                    {formatMarketChange(item.changeRate)}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="panel-surface market-watchlist-column">
        <div className="panel-head">
          <div>
            <p className="eyebrow">{t("markets.listEyebrow")}</p>
            <h3>{t("markets.listTitle")}</h3>
          </div>

          <div className="markets-view-controls">
            <span className="terminal-badge muted">{t("common.records", { count: filteredQuotes.length })}</span>
            <div className="markets-view-toggle">
              <button
                type="button"
                className={`table-chip-button ${viewMode === "table" ? "active" : ""}`}
                onClick={() => setViewMode("table")}
              >
                {t("markets.tableView")}
              </button>
              <button
                type="button"
                className={`table-chip-button ${viewMode === "cards" ? "active" : ""}`}
                onClick={() => setViewMode("cards")}
              >
                {t("markets.cardView")}
              </button>
            </div>
          </div>
        </div>

        {loading ? <LoadingSpinner label={t("markets.loading")} /> : null}
        {error ? <ErrorMessage message={error} /> : null}

        {!loading && !error && filteredQuotes.length === 0 ? (
          <EmptyState title={t("markets.emptyTitle")} description={t("markets.emptyDescription")} />
        ) : null}

        {!loading && !error && filteredQuotes.length > 0 && viewMode === "table" ? (
          <div className="market-watchlist-table table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{t("markets.columns.instrument")}</th>
                  <th>{t("markets.columns.category")}</th>
                  <th>{t("markets.columns.last")}</th>
                  <th>{t("markets.columns.change")}</th>
                  <th>{t("markets.columns.source")}</th>
                  <th>{t("markets.columns.detail")}</th>
                </tr>
              </thead>
              <tbody>
                {filteredQuotes.map((item) => (
                  <tr
                    key={`${item.symbol}-${item.source}`}
                    onClick={() => navigate(`/markets/${encodeURIComponent(item.symbol)}`)}
                  >
                    <td>
                      <div className="watchlist-symbol-cell">
                        <strong>{item.symbol || "-"}</strong>
                        <span>{item.displayName || item.instrumentType || "-"}</span>
                      </div>
                    </td>
                    <td>{formatCategoryLabel(item.marketCategory, t)}</td>
                    <td>{formatNumber(item.price)}</td>
                    <td>
                      <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                        {formatMarketChange(item.changeRate)}
                      </span>
                    </td>
                    <td>{item.source || "-"}</td>
                    <td>
                      <button
                        type="button"
                        className="table-chip-button"
                        onClick={(event) => {
                          event.stopPropagation();
                          navigate(`/markets/${encodeURIComponent(item.symbol)}`);
                        }}
                      >
                        {t("markets.examine")}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {!loading && !error && filteredQuotes.length > 0 && viewMode === "cards" ? (
          <div className="market-card-grid">
            {filteredQuotes.map((item) => (
              <button
                key={`${item.symbol}-${item.source}`}
                type="button"
                className="market-quote-card"
                onClick={() => navigate(`/markets/${encodeURIComponent(item.symbol)}`)}
              >
                <div className="market-quote-card-top">
                  <div>
                    <strong>{item.symbol || "-"}</strong>
                    <p>{item.displayName || item.instrumentType || "-"}</p>
                  </div>
                  <span className="summary-chip">{formatCategoryLabel(item.marketCategory, t)}</span>
                </div>
                <div className="market-quote-card-body">
                  <strong>{formatNumber(item.price)}</strong>
                  <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                    {formatMarketChange(item.changeRate)}
                  </span>
                </div>
                <div className="market-quote-card-foot">
                  <span>{item.source || "-"}</span>
                  <span>{item.currency || "-"}</span>
                </div>
              </button>
            ))}
          </div>
        ) : null}
      </section>
    </div>
  );
}

function classifyCategory(item) {
  const type = normalizeText(item?.instrumentType);
  return ["CRYPTO", "FX", "STOCK", "FUND"].includes(type) ? type : "ALL";
}

function sortQuotes(left, right, sortBy) {
  if (sortBy === "price_desc") {
    return toSortableNumber(right.price) - toSortableNumber(left.price);
  }

  if (sortBy === "price_asc") {
    return toSortableNumber(left.price) - toSortableNumber(right.price);
  }

  if (sortBy === "change_desc") {
    return toSortableNumber(right.changeRate) - toSortableNumber(left.changeRate);
  }

  if (sortBy === "change_asc") {
    return toSortableNumber(left.changeRate) - toSortableNumber(right.changeRate);
  }

  const leftLabel = `${left.displayName || ""} ${left.symbol || ""}`.trim();
  const rightLabel = `${right.displayName || ""} ${right.symbol || ""}`.trim();
  return leftLabel.localeCompare(rightLabel, "tr");
}

function formatCategoryLabel(value, t) {
  return t(`markets.categories.${value}`, { defaultValue: value ?? "-" });
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

function uniqueBySymbol(items) {
  const unique = [];
  const seen = new Set();

  items.forEach((item) => {
    if (!item?.symbol || seen.has(item.symbol)) {
      return;
    }

    seen.add(item.symbol);
    unique.push(item);
  });

  return unique;
}

function toSortableNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : -Infinity;
}

function normalizeText(value) {
  return value == null ? "" : String(value).trim().toUpperCase();
}
