import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Search, Star } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useCurrency } from "../../currency/CurrencyContext";
import { formatInstrumentValue } from "../../utils/formatters";
import { formatInstrumentCode, getFxCodeLabel } from "../../utils/instrumentUtils";

export default function AnalysisSymbolPicker({
  quotes,
  primarySymbol,
  primaryContext,
  primaryQuote = null,
  currencyToggle = null,
  chartMode,
  onChartModeChange,
  onPrimaryChange,
  isFavorite = false,
  favoriteBusy = false,
  onFavoriteToggle,
}) {
  const { t, i18n } = useTranslation();
  const { formatAmount } = useCurrency();

  const advancedHeaderPrice = primaryQuote?.sellRate ?? primaryQuote?.price ?? null;
  const advancedHeaderChange = primaryQuote?.changeRate ?? null;

  return (
    <div className="analysis-hero-shell">
      <div className="analysis-hero-grid">
        <div className="analysis-hero-context">
          <div className="analysis-hero-symbol-row">
            <h1>{primaryContext?.symbolLine || "-"}</h1>
            {advancedHeaderPrice != null ? (
              <div className="analysis-hero-price-block">
                <strong>
                  {formatInstrumentValue(advancedHeaderPrice, {
                    instrumentType: primaryQuote?.instrumentType,
                    displayUnit: primaryQuote?.displayUnit,
                    currency: primaryQuote?.currency,
                    currencyFormatter: formatAmount,
                    locale: i18n.resolvedLanguage,
                  })}
                </strong>
                {advancedHeaderChange != null ? (
                  <span className={advancedHeaderChange >= 0 ? "is-positive" : "is-negative"}>
                    {advancedHeaderChange >= 0 ? "+" : ""}
                    {Number(advancedHeaderChange).toFixed(2)}%
                  </span>
                ) : null}
              </div>
            ) : null}
            <button
              type="button"
              className={`analysis-favorite-btn${isFavorite ? " is-active" : ""}`}
              aria-label={isFavorite ? "Favorilerden çıkar" : "Favorilere ekle"}
              disabled={favoriteBusy}
              onClick={onFavoriteToggle}
            >
              <Star size={40} strokeWidth={2} fill={isFavorite ? "#c3a45d" : "none"} color="#c3a45d" />
            </button>
          </div>
          {primaryContext?.title ? <div className="analysis-hero-meta"><span>{primaryContext.title}</span></div> : null}
        </div>

        <div className="analysis-hero-controls">
          <PrimaryInstrumentPicker
            quotes={quotes}
            primarySymbol={primarySymbol}
            primaryContext={primaryContext}
            primaryQuote={primaryQuote}
            onPrimaryChange={onPrimaryChange}
            t={t}
            locale={i18n.resolvedLanguage}
          />

          {currencyToggle ? <div className="analysis-hero-segment-slot">{currencyToggle}</div> : null}

          {onChartModeChange ? (
            <div className="analysis-mode-shell">
              <div className="analysis-mode-toggle" role="group">
                <button
                  type="button"
                  className={`analysis-mode-btn${chartMode === "simple" ? " active" : ""}`}
                  onClick={() => onChartModeChange("simple")}
                >
                  {t("analysis.simpleChart")}
                </button>
                <button
                  type="button"
                  className={`analysis-mode-btn${chartMode === "advanced" ? " active" : ""}`}
                  onClick={() => onChartModeChange("advanced")}
                >
                  {t("analysis.advancedChart")}
                </button>
                <button
                  type="button"
                  className={`analysis-mode-btn${chartMode === "comparison" ? " active" : ""}`}
                  onClick={() => onChartModeChange("comparison")}
                >
                  {t("analysis.comparisonChart")}
                </button>
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

const SUGGESTION_TYPE_ORDER = ["FX", "CRYPTO", "STOCK", "FUND", "COMMODITY", "FUTURES", "BOND", "INDEX"];

function buildBalancedSuggestions(pool, limit) {
  const byType = new Map();
  for (const item of pool) {
    const type = String(item?.instrumentType || "").trim().toUpperCase() || "OTHER";
    if (!byType.has(type)) {
      byType.set(type, []);
    }
    byType.get(type).push(item);
  }

  const orderedTypes = [
    ...SUGGESTION_TYPE_ORDER.filter((type) => byType.has(type)),
    ...[...byType.keys()].filter((type) => !SUGGESTION_TYPE_ORDER.includes(type)),
  ];

  const result = [];
  let round = 0;
  let added = true;
  while (result.length < limit && added) {
    added = false;
    for (const type of orderedTypes) {
      const bucket = byType.get(type);
      if (bucket && round < bucket.length) {
        result.push(bucket[round]);
        added = true;
        if (result.length >= limit) {
          break;
        }
      }
    }
    round += 1;
  }

  return result;
}

function formatInstrumentLabel(item) {
  return item?.code || formatInstrumentCode(item?.symbol) || "-";
}

const INSTRUMENT_CATEGORIES = [
  { key: "ALL", labelKey: "analysis.symbolPicker.categories.all" },
  { key: "FX", labelKey: "analysis.symbolPicker.categories.fx" },
  { key: "CRYPTO", labelKey: "analysis.symbolPicker.categories.crypto" },
  { key: "STOCK", labelKey: "analysis.symbolPicker.categories.stock" },
  { key: "FUND", labelKey: "analysis.symbolPicker.categories.fund" },
  { key: "COMMODITY", labelKey: "analysis.symbolPicker.categories.commodity" },
  { key: "FUTURES", labelKey: "analysis.symbolPicker.categories.futures" },
  { key: "BOND", labelKey: "analysis.symbolPicker.categories.bond" },
  { key: "INDEX", labelKey: "markets.categories.INDEX" },
];

const CATEGORY_LABEL_KEYS = {
  FX: "analysis.symbolPicker.categories.fx",
  CRYPTO: "analysis.symbolPicker.categories.crypto",
  STOCK: "analysis.symbolPicker.categories.stock",
  BIST: "analysis.symbolPicker.categories.stock",
  FUND: "analysis.symbolPicker.categories.fund",
  COMMODITY: "analysis.symbolPicker.categories.commodity",
  FUTURES: "analysis.symbolPicker.categories.futures",
  BOND: "analysis.symbolPicker.categories.bond",
  INDEX: "markets.categories.INDEX",
};

function PrimaryInstrumentPicker({ quotes, primarySymbol, primaryContext, primaryQuote, onPrimaryChange, t, locale }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("ALL");
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;

    function handleOutside(event) {
      if (rootRef.current && !rootRef.current.contains(event.target)) {
        setOpen(false);
      }
    }

    function handleKeyDown(event) {
      if (event.key === "Escape") setOpen(false);
    }

    document.addEventListener("mousedown", handleOutside);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleOutside);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const results = useMemo(
    () => resolveInstrumentResults(quotes, search, category),
    [quotes, search, category],
  );

  const triggerLabel = primaryContext?.symbolLine
    || formatInstrumentLabel(primaryQuote)
    || (primarySymbol ? formatInstrumentCode(primarySymbol) : t("analysis.symbolPicker.selectInstrument"));

  function handleSelect(symbol) {
    onPrimaryChange(symbol);
    setOpen(false);
    setSearch("");
    setCategory("ALL");
  }

  return (
    <div className="analysis-primary-picker" ref={rootRef}>
      <button
        type="button"
        className={`analysis-primary-trigger${open ? " active" : ""}`}
        onClick={() => setOpen((current) => !current)}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className="analysis-primary-trigger-label">{triggerLabel}</span>
        <ChevronDown size={15} strokeWidth={2.2} />
      </button>

      {open ? (
        <div className="analysis-instrument-popover" role="dialog" aria-label={t("analysis.symbolPicker.searchInstrument")}>
          <label className="analysis-compare-search">
            <Search size={14} strokeWidth={2} />
            <input
              autoFocus
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t("analysis.symbolPicker.searchPlaceholder")}
            />
          </label>

          <div className="analysis-instrument-categories" role="group" aria-label={t("analysis.symbolPicker.searchInstrument")}>
            {INSTRUMENT_CATEGORIES.map((cat) => (
              <button
                key={cat.key}
                type="button"
                className={`analysis-instrument-cat${category === cat.key ? " active" : ""}`}
                onClick={() => setCategory(cat.key)}
              >
                {t(cat.labelKey)}
              </button>
            ))}
          </div>

          <div className="analysis-compare-results">
            {results.length === 0 ? (
              <div className="analysis-compare-empty">{t("analysis.symbolPicker.noResults")}</div>
            ) : results.map((item) => {
              const selected = item.symbol === primarySymbol;
              return (
                <button
                  key={`${item.symbol}-${item.source}`}
                  type="button"
                  className={`analysis-compare-option analysis-instrument-option${selected ? " active" : ""}`}
                  onClick={() => handleSelect(item.symbol)}
                >
                  <span className="analysis-instrument-option-top">
                    <strong>{formatInstrumentLabel(item)}</strong>
                    <span className="analysis-instrument-option-type">{resolveTypeLabel(item, t)}</span>
                  </span>
                  <span className="analysis-instrument-option-name">{resolveInstrumentTitle(item, locale)}</span>
                </button>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function resolveInstrumentResults(quotes, search, category) {
  const query = search.trim().toLowerCase();
  const pool = category === "ALL"
    ? quotes
    : quotes.filter((item) => normalizeInstrumentCategory(item) === category);

  if (query) {
    return pool
      .filter((item) =>
        item.symbol?.toLowerCase().includes(query)
        || item.code?.toLowerCase().includes(query)
        || item.displayName?.toLowerCase().includes(query))
      .slice(0, 20);
  }

  if (category === "ALL") {
    return buildBalancedSuggestions(pool, 12);
  }

  return pool.slice(0, 12);
}

function normalizeInstrumentType(item) {
  return String(item?.instrumentType || "").trim().toUpperCase();
}

function normalizeInstrumentCategory(item) {
  const type = normalizeInstrumentType(item);
  switch (type) {
    case "FUTURE":
    case "FUTURES":
      return "FUTURES";
    case "BOND":
    case "BONDS":
    case "BILL":
    case "BILLS":
    case "BONO":
      return "BOND";
    case "COMMODITY":
    case "COMMODITIES":
      return "COMMODITY";
    default:
      return type;
  }
}

function resolveTypeLabel(item, t) {
  const type = normalizeInstrumentCategory(item);
  const labelKey = CATEGORY_LABEL_KEYS[type];
  return labelKey ? t(labelKey) : (type || "-");
}

function resolveInstrumentTitle(item, locale) {
  const instrumentType = String(item?.instrumentType || "").trim().toUpperCase();
  const displayCode = item?.code || formatInstrumentCode(item?.symbol) || "";
  if (instrumentType === "FX") {
    return getFxCodeLabel(displayCode, locale);
  }

  const displayName = String(item?.displayName || "").trim();
  if (displayName && displayName !== displayCode) {
    return displayName;
  }

  return displayCode || "-";
}
