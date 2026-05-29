import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Info, Plus, Search, Star, X } from "lucide-react";
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
}) {
  const { t, i18n } = useTranslation();
  const [search, setSearch] = useState("");
  const [compareOpen, setCompareOpen] = useState(false);
  const popoverRef = useRef(null);

  const filteredQuotes = useMemo(() => {
    const query = search.trim().toLowerCase();
    return quotes
      .filter((item) => item.symbol !== primarySymbol)
      .filter((item) => {
        if (!query) return true;
        return item.symbol?.toLowerCase().includes(query) || item.displayName?.toLowerCase().includes(query);
      })
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
            {chartMode === "advanced" && advancedHeaderPrice != null ? (
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
            <button type="button" className="analysis-favorite-btn" aria-label="Favorite">
              <Star size={19} strokeWidth={2} />
            </button>
          </div>
          <div className="analysis-hero-meta">
            <span>{primaryContext?.title || "-"}</span>
            <span className="analysis-hero-meta-sep" aria-hidden="true" />
            <span>{primaryContext?.metaLine || "-"}</span>
            <Info size={15} strokeWidth={2} />
          </div>
        </div>

        <div className="analysis-hero-controls">
          <label className="analysis-switcher-field">
            <span className="sr-only">{t("analysis.symbolPicker.selectInstrument")}</span>
            <select value={primarySymbol} onChange={(event) => onPrimaryChange(event.target.value)}>
              <option value="">{t("analysis.symbolPicker.selectInstrument")}</option>
              {quotes.map((item) => (
              <option key={`${item.symbol}-${item.source}`} value={item.symbol}>
                  {formatInstrumentLabel(item)} {resolveOptionTitle(item, i18n.resolvedLanguage)}
              </option>
              ))}
            </select>
            <ChevronDown size={15} strokeWidth={2.2} />
          </label>

          {currencyToggle ? <div className="analysis-hero-segment-slot">{currencyToggle}</div> : null}

          {onChartModeChange ? (
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

function formatInstrumentLabel(item) {
  return item?.code || formatInstrumentCode(item?.symbol) || "-";
}

function resolveOptionTitle(item, locale) {
  const title = resolveInstrumentTitle(item, locale);
  return title ? `- ${title}` : "";
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
