import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { getMacroHistory, getMarketsByType } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { formatNumber } from "../utils/formatters";

const CATEGORY_OPTIONS = ["FX", "CRYPTO", "STOCK", "FUND", "FUTURES", "BOND"];
const MACRO_FROM_DATE = "2024-01-01";

export default function MarketsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [sourceFilter, setSourceFilter] = useState("ALL");
  const [categoryFilter, setCategoryFilter] = useState("FX");
  const [viewMode, setViewMode] = useState("table");
  const [sortBy, setSortBy] = useState("name");
  const [macroPanelOpen, setMacroPanelOpen] = useState(true);
  const [selectedMacroSymbol, setSelectedMacroSymbol] = useState("");
  const [macroHistory, setMacroHistory] = useState([]);
  const [macroHistoryLoading, setMacroHistoryLoading] = useState(false);
  const [macroHistoryError, setMacroHistoryError] = useState("");
  const deferredSearch = useDeferredValue(search);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");
        const data = await getMarketsByType(categoryFilter);
        if (!active) {
          return;
        }

        const normalizedQuotes = Array.isArray(data) ? data : [];
        setQuotes(
          normalizedQuotes
            .filter((item) => item && typeof item === "object")
            .map((item) => ({ ...item, marketCategory: classifyCategory(item, categoryFilter) })),
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

  const macroInstruments = useMemo(() => [], []);

  useEffect(() => {
    if (macroInstruments.length === 0) {
      if (selectedMacroSymbol) {
        setSelectedMacroSymbol("");
      }
      return;
    }

    const selectedExists = macroInstruments.some((item) => normalizeText(item.symbol) === normalizeText(selectedMacroSymbol));
    if (!selectedExists) {
      setSelectedMacroSymbol(macroInstruments[0].symbol);
    }
  }, [macroInstruments, selectedMacroSymbol]);

  useEffect(() => {
    let active = true;

    async function loadMacroHistory() {
      if (!macroPanelOpen || !selectedMacroSymbol) {
        if (active) {
          setMacroHistory([]);
          setMacroHistoryError("");
          setMacroHistoryLoading(false);
        }
        return;
      }

      try {
        setMacroHistoryLoading(true);
        setMacroHistoryError("");
        const data = await getMacroHistory(selectedMacroSymbol, {
          from: MACRO_FROM_DATE,
          to: buildTodayIsoDate(),
        });
        if (!active) {
          return;
        }
        const normalizedHistory = Array.isArray(data) ? data : [];
        setMacroHistory(normalizedHistory.filter((item) => item && typeof item === "object"));
      } catch (err) {
        if (!active) {
          return;
        }
        setMacroHistoryError(extractErrorMessage(err, t("markets.macro.historyError")));
      } finally {
        if (active) {
          setMacroHistoryLoading(false);
        }
      }
    }

    loadMacroHistory();

    return () => {
      active = false;
    };
  }, [macroPanelOpen, selectedMacroSymbol, t]);

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
        const matchesQuery =
          query.length === 0 ||
          item.symbol?.toLowerCase().includes(query) ||
          item.displayName?.toLowerCase().includes(query);

        return matchesSource && matchesQuery;
      })
      .sort((left, right) => sortQuotes(left, right, sortBy));
  }, [quotes, deferredSearch, sourceFilter, sortBy]);

  const marketPulse = useMemo(() => {
    const positive = filteredQuotes.filter((item) => Number(item.changeRate) >= 0).length;
    const negative = filteredQuotes.filter((item) => Number(item.changeRate) < 0).length;

    return {
      visible: filteredQuotes.length,
      positive,
      negative,
    };
  }, [filteredQuotes]);

  const selectedMacro = useMemo(
    () => macroInstruments.find((item) => normalizeText(item.symbol) === normalizeText(selectedMacroSymbol)) ?? null,
    [macroInstruments, selectedMacroSymbol],
  );

  const isFxTable = categoryFilter === "FX";

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
        </div>
      </section>

      <section className="panel-surface market-watchlist-column">
        <div className="panel-head">
          <div>
            <p className="eyebrow">{t("markets.listEyebrow")}</p>
            <h3>{formatCategoryLabel(categoryFilter, t)}</h3>
          </div>

          <div className="markets-view-controls">
            <span className="terminal-badge muted">{t("common.records", { count: filteredQuotes.length })}</span>
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

        <div className="markets-console-grid">
          <aside className="markets-category-sidebar">
            <p className="eyebrow">{t("markets.category")}</p>
            <div className="markets-category-nav" role="tablist" aria-label={t("markets.category")}>
              {CATEGORY_OPTIONS.map((category) => {
                const isActive = categoryFilter === category;
                return (
                  <button
                    key={category}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    className={`markets-category-button ${isActive ? "active" : ""}`}
                    onClick={() => setCategoryFilter(category)}
                  >
                    {formatCategoryLabel(category, t)}
                  </button>
                );
              })}
            </div>
          </aside>

          <div className="markets-category-content">
            {!loading && !error && filteredQuotes.length === 0 ? (
              <EmptyState title={t("markets.emptyTitle")} description={t("markets.emptyDescription")} />
            ) : null}

            {!loading && !error && filteredQuotes.length > 0 && viewMode === "table" ? (
              <div className="table-wrap finance-market-table-wrap finance-market-scroll-wrap">
                <table className="finance-market-table">
                  <thead>
                    <tr>
                      <th>{t("markets.columns.instrument")}</th>
                      {isFxTable ? <th>{t("markets.columns.buy", { defaultValue: "Alış" })}</th> : null}
                      {isFxTable ? <th>{t("markets.columns.sell", { defaultValue: "Satış" })}</th> : null}
                      {!isFxTable ? <th>{t("markets.columns.lastPrice", { defaultValue: "Son Fiyat" })}</th> : null}
                      <th>{t("markets.columns.change")}</th>
                      <th>{t("markets.columns.source")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredQuotes.map((item) => (
                      <tr key={`${item.symbol}-${item.source}-${item.marketCategory}`}>
                        <td>
                          <button
                            type="button"
                            className="finance-table-row-button"
                            onClick={() => navigate(`/markets/${encodeURIComponent(item.symbol)}`)}
                          >
                            <span className="finance-table-symbol">
                              <strong>{item.symbol || "-"}</strong>
                              <span>{item.displayName || item.instrumentType || "-"}</span>
                            </span>
                          </button>
                        </td>
                        {isFxTable ? <td>{formatRate(item.buyRate)}</td> : null}
                        {isFxTable ? <td>{formatRate(item.sellRate)}</td> : null}
                        {!isFxTable ? <td>{formatRate(item.price)}</td> : null}
                        <td className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</td>
                        <td>{item.source || "-"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}

            {!loading && !error && filteredQuotes.length > 0 && viewMode === "cards" ? (
              <div className="market-card-grid markets-card-scroll-wrap">
                {filteredQuotes.map((item) => (
                  <button
                    key={`${item.symbol}-${item.source}-${item.marketCategory}-card`}
                    type="button"
                    className="market-quote-card"
                    onClick={() => navigate(`/markets/${encodeURIComponent(item.symbol)}`)}
                  >
                    <div className="market-quote-card-top">
                      <div>
                        <strong>{item.symbol || "-"}</strong>
                        <p>{item.displayName || item.instrumentType || "-"}</p>
                      </div>
                      <span className="terminal-badge muted">{item.source || "-"}</span>
                    </div>

                    <div className="market-quote-card-body">
                      <div>
                        <span className="eyebrow">
                          {isFxTable ? t("markets.columns.buy", { defaultValue: "Alış" }) : t("markets.columns.lastPrice", { defaultValue: "Son Fiyat" })}
                        </span>
                        <strong>{formatRate(isFxTable ? item.buyRate : item.price)}</strong>
                      </div>
                      {isFxTable ? (
                        <div>
                          <span className="eyebrow">{t("markets.columns.sell", { defaultValue: "Satış" })}</span>
                          <strong>{formatRate(item.sellRate)}</strong>
                        </div>
                      ) : null}
                    </div>

                    <div className="market-quote-card-foot">
                      <span>{formatCategoryLabel(item.marketCategory, t)}</span>
                      <strong className={getChangeToneClass(item.changeRate)}>{formatMarketChange(item.changeRate)}</strong>
                    </div>
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        </div>
      </section>

      <section className="panel-surface market-macro-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">{t("markets.macro.eyebrow")}</p>
            <h3>{t("markets.macro.title")}</h3>
          </div>
          <button
            type="button"
            className={`table-chip-button ${macroPanelOpen ? "active" : ""}`}
            onClick={() => setMacroPanelOpen((current) => !current)}
          >
            {macroPanelOpen ? t("markets.macro.hide") : t("markets.macro.show")}
          </button>
        </div>

        {macroPanelOpen ? (
          <div className="market-macro-grid">
            <div className="market-macro-list">
              <div className="market-macro-list-head">
                <span className="terminal-badge muted">{t("common.records", { count: macroInstruments.length })}</span>
              </div>

              {macroInstruments.length === 0 ? (
                <EmptyState title={t("markets.macro.emptyTitle")} description={t("markets.macro.emptyDescription")} />
              ) : null}
            </div>

            <div className="market-macro-history">
              <div className="market-macro-history-head">
                <div>
                  <p className="eyebrow">{t("markets.macro.historyEyebrow")}</p>
                  <h4>{selectedMacro?.displayName || t("markets.macro.historyTitle")}</h4>
                </div>
                {selectedMacro ? <span className="terminal-badge muted">{selectedMacro.symbol}</span> : null}
              </div>

              {macroHistoryLoading ? <LoadingSpinner label={t("markets.macro.historyLoading")} /> : null}
              {macroHistoryError ? <ErrorMessage message={macroHistoryError} /> : null}

              {!macroHistoryLoading && !macroHistoryError && selectedMacro && macroHistory.length === 0 ? (
                <EmptyState title={t("markets.macro.historyEmptyTitle")} description={t("markets.macro.historyEmptyDescription")} />
              ) : null}

              {!macroHistoryLoading && !macroHistoryError && macroHistory.length > 0 ? (
                <div className="table-wrap market-macro-history-table">
                  <table>
                    <thead>
                      <tr>
                        <th>{t("markets.macro.columns.date")}</th>
                        <th>{t("markets.macro.columns.value")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {macroHistory.map((item) => (
                        <tr key={`${selectedMacroSymbol}-${item.priceDate}`}>
                          <td>{formatMacroMonth(item.priceDate, i18n.language)}</td>
                          <td>{formatNumber(item.closePrice)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </div>
          </div>
        ) : null}
      </section>
    </div>
  );
}

function classifyCategory(item, fallbackCategory) {
  const type = normalizeText(item?.instrumentType);
  if (type === "FOREX" || type === "CURRENCY") {
    return "FX";
  }
  return ["CRYPTO", "FX", "STOCK", "FUND", "FUTURES", "BOND"].includes(type) ? type : fallbackCategory;
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

function formatRate(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  return formatNumber(value);
}

function formatMacroMonth(value, language) {
  if (!value) {
    return "-";
  }

  const [year, month] = String(value).split("-");
  if (!year || !month) {
    return String(value);
  }

  if (language?.startsWith("en")) {
    return `${year}-${month.padStart(2, "0")}`;
  }

  return `${year}-${month.padStart(2, "0")}`;
}

function toSortableNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : -Infinity;
}

function normalizeText(value) {
  return value == null ? "" : String(value).trim().toUpperCase();
}

function buildTodayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

function getChangeToneClass(value) {
  if (value === null || value === undefined || value === "") {
    return "tone-muted";
  }

  return Number(value) >= 0 ? "tone-positive" : "tone-negative";
}
