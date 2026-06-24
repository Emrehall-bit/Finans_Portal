import { useTranslation } from "react-i18next";
import { Star } from "lucide-react";
import { useCurrency } from "../../currency/CurrencyContext";
import { formatInstrumentValue } from "../../utils/formatters";
import { formatInstrumentCode } from "../../utils/instrumentUtils";
import InstrumentSelectorPopover from "./InstrumentSelectorPopover";

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

  const triggerLabel = primaryContext?.symbolLine
    || primaryQuote?.code
    || (primarySymbol ? formatInstrumentCode(primarySymbol) : t("analysis.symbolPicker.selectInstrument"));

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
          {/* Primary picker is hidden in comparison mode — it lives inside the comparison panel there */}
          {chartMode !== "comparison" ? (
            <InstrumentSelectorPopover
              items={quotes}
              selectedSymbol={primarySymbol}
              onSelect={onPrimaryChange}
              triggerLabel={triggerLabel}
              placeholder={t("analysis.symbolPicker.searchPlaceholder")}
            />
          ) : null}

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
