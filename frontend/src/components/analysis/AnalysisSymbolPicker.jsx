import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

export default function AnalysisSymbolPicker({
  quotes,
  primarySymbol,
  selectedSymbols,
  onPrimaryChange,
  onToggleComparisonSymbol,
}) {
  const { t } = useTranslation();
  const [search, setSearch] = useState("");

  const filteredQuotes = useMemo(() => {
    const query = search.trim().toLowerCase();
    return quotes
      .filter((item) => {
        if (!query) {
          return true;
        }

        return item.symbol?.toLowerCase().includes(query) || item.displayName?.toLowerCase().includes(query);
      })
      .slice(0, 10);
  }, [quotes, search]);

  return (
    <section className="panel-surface analysis-lab-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("analysis.symbolPicker.eyebrow")}</p>
          <h3>{t("analysis.symbolPicker.title")}</h3>
        </div>
      </div>

      <div className="analysis-lab-toolbar">
        <label className="market-filter-field">
          <span>{t("analysis.symbolPicker.primaryInstrument")}</span>
          <select value={primarySymbol} onChange={(event) => onPrimaryChange(event.target.value)}>
            <option value="">{t("analysis.symbolPicker.selectInstrument")}</option>
            {quotes.map((item) => (
              <option key={`${item.symbol}-${item.source}`} value={item.symbol}>
                {formatInstrumentLabel(item)} {item.displayName ? `- ${item.displayName}` : ""}
              </option>
            ))}
          </select>
        </label>

        <label className="market-filter-field">
          <span>{t("analysis.symbolPicker.searchComparison")}</span>
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t("analysis.symbolPicker.searchPlaceholder")} />
        </label>
      </div>

      <div className="analysis-selected-strip">
        {selectedSymbols.length === 0 ? <span className="muted">{t("analysis.symbolPicker.emptySelection")}</span> : null}
        {selectedSymbols.map((symbol) => (
          <button key={symbol} type="button" className="table-chip-button active" onClick={() => onToggleComparisonSymbol(symbol)}>
            {formatSelectedSymbol(symbol, quotes)}
          </button>
        ))}
      </div>

      <div className="analysis-picker-grid">
        {filteredQuotes.map((item) => {
          const active = selectedSymbols.includes(item.symbol);
          return (
            <button key={`${item.symbol}-${item.source}`} type="button" className={`analysis-picker-card${active ? " active" : ""}`} onClick={() => onToggleComparisonSymbol(item.symbol)}>
              <strong>{formatInstrumentLabel(item)}</strong>
              <span>{item.displayName || item.instrumentType || "-"}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function formatInstrumentLabel(item) {
  return item?.code || item?.symbol || "-";
}

function formatSelectedSymbol(symbol, quotes) {
  return quotes.find((item) => item.symbol === symbol)?.code || symbol;
}
