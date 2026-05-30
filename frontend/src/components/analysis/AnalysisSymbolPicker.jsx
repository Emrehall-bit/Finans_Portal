import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Plus, Search, Star, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { formatNumber } from "../../utils/formatters";
import { formatInstrumentCode, getFxCodeLabel } from "../../utils/instrumentUtils";

export default function AnalysisSymbolPicker({
  quotes,
  primarySymbol,
  selectedSymbols,
  primaryContext,
  primaryQuote = null,
  currencyToggle = null,
  chartMode,
  showComparison = true,
  onChartModeChange,
  onPrimaryChange,
  onToggleComparisonSymbol,
  showInlineComparisonChips = false,
  isFavorite = false,
  favoriteBusy = false,
  onFavoriteToggle,
}) {
  const { t, i18n } = useTranslation();
  const [search, setSearch] = useState("");
  const [compareOpen, setCompareOpen] = useState(false);
  const popoverRef = useRef(null);

  const filteredQuotes = useMemo(() => {
    const query = search.trim().toLowerCase();
    const pool = quotes.filter((item) => item.symbol !== primarySymbol);

    if (!query) {
      // İlk açılışta tek tip (FX) yerine türler arası dengeli öneri göster
      return buildBalancedSuggestions(pool, 10);
    }

    // Arama yapıldığında tüm enstrüman havuzunda ara (mevcut davranış)
    return pool
      .filter((item) =>
        item.symbol?.toLowerCase().includes(query) || item.displayName?.toLowerCase().includes(query))
      .slice(0, 10);
  }, [quotes, search, primarySymbol]);

  const comparisonSymbols = useMemo(
    () => selectedSymbols.filter((symbol) => symbol !== primarySymbol),
    [selectedSymbols, primarySymbol],
  );
  const advancedHeaderPrice = primaryQuote?.sellRate ?? primaryQuote?.price ?? null;
  const advancedHeaderChange = primaryQuote?.changeRate ?? null;

  useEffect(() => {
    if (!compareOpen) return undefined;

    function handleOutside(event) {
      if (popoverRef.current && !popoverRef.current.contains(event.target)) {
        setCompareOpen(false);
      }
    }

    function handleKeyDown(event) {
      if (event.key === "Escape") setCompareOpen(false);
    }

    document.addEventListener("mousedown", handleOutside);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleOutside);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [compareOpen]);

  return (
    <div className="analysis-hero-shell">
      <div className="analysis-hero-grid">
        <div className="analysis-hero-context">
          <div className="analysis-hero-symbol-row">
            <h1>{primaryContext?.symbolLine || "-"}</h1>
            {advancedHeaderPrice != null ? (
              <div className="analysis-hero-price-block">
                <strong>{formatNumber(advancedHeaderPrice, 2)}</strong>
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
              <Star size={19} strokeWidth={2} fill={isFavorite ? "#c3a45d" : "none"} color="#c3a45d" />
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
              </div>
            </div>
          ) : null}

          {showComparison ? (
            <div className="analysis-compare-actions" ref={popoverRef}>
              <button
                type="button"
                className={`analysis-header-compare-btn${compareOpen ? " active" : ""}`}
                onClick={() => setCompareOpen((current) => !current)}
              >
                <Plus size={17} strokeWidth={2.1} />
                <span>{t("analysis.symbolPicker.searchComparison")}</span>
              </button>

              {compareOpen ? (
                <div className="analysis-compare-popover" role="dialog" aria-label={t("analysis.symbolPicker.searchComparison")}>
                  <label className="analysis-compare-search">
                    <Search size={14} strokeWidth={2} />
                    <input
                      autoFocus
                      value={search}
                      onChange={(event) => setSearch(event.target.value)}
                      placeholder={t("analysis.symbolPicker.searchPlaceholder")}
                    />
                  </label>

                  <div className="analysis-compare-results">
                    {filteredQuotes.length === 0 ? (
                      <div className="analysis-compare-empty">{t("analysis.symbolPicker.emptySelection")}</div>
                    ) : filteredQuotes.map((item) => {
                      const active = selectedSymbols.includes(item.symbol);
                      return (
                        <button
                          key={`${item.symbol}-${item.source}`}
                          type="button"
                          className={`analysis-compare-option${active ? " active" : ""}`}
                          onClick={() => onToggleComparisonSymbol(item.symbol)}
                        >
                          <strong>{formatInstrumentLabel(item)}</strong>
                          <span>{resolveInstrumentTitle(item, i18n.resolvedLanguage) || item.instrumentType || "-"}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>

      {showInlineComparisonChips && comparisonSymbols.length > 0 ? (
        <div className="analysis-inline-compare-row">
          {comparisonSymbols.map((symbol) => (
            <button
              key={symbol}
              type="button"
              className="analysis-compare-chip"
              onClick={() => onToggleComparisonSymbol(symbol)}
              title={t("common.remove")}
            >
              <span className="analysis-chip-avatar">{symbol.slice(0, 2)}</span>
              <span>{formatSelectedSymbol(symbol, quotes)}</span>
              <X size={12} strokeWidth={2.2} />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

const SUGGESTION_TYPE_ORDER = ["FX", "CRYPTO", "STOCK", "FUND"];

// Boş aramada türler arası dengeli (round-robin) öneri listesi üretir.
// Veri olmayan türler sessizce atlanır.
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
];

const CATEGORY_LABEL_KEYS = {
  FX: "analysis.symbolPicker.categories.fx",
  CRYPTO: "analysis.symbolPicker.categories.crypto",
  STOCK: "analysis.symbolPicker.categories.stock",
  BIST: "analysis.symbolPicker.categories.stock",
  FUND: "analysis.symbolPicker.categories.fund",
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
    : quotes.filter((item) => normalizeInstrumentType(item) === category);

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

function resolveTypeLabel(item, t) {
  const type = normalizeInstrumentType(item);
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

function formatSelectedSymbol(symbol, quotes) {
  return quotes.find((item) => item.symbol === symbol)?.code || formatInstrumentCode(symbol);
}
