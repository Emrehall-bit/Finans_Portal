import { useMemo, useState } from "react";

export default function AnalysisSymbolPicker({
  quotes,
  primarySymbol,
  selectedSymbols,
  onPrimaryChange,
  onToggleComparisonSymbol,
}) {
  const [search, setSearch] = useState("");

  const filteredQuotes = useMemo(() => {
    const query = search.trim().toLowerCase();
    return quotes
      .filter((item) => {
        if (!query) {
          return true;
        }

        return (
          item.symbol?.toLowerCase().includes(query) ||
          item.displayName?.toLowerCase().includes(query)
        );
      })
      .slice(0, 10);
  }, [quotes, search]);

  return (
    <section className="panel-surface analysis-lab-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Enstruman Secimi</p>
          <h3>Analiz sepeti</h3>
        </div>
      </div>

      <div className="analysis-lab-toolbar">
        <label className="market-filter-field">
          <span>Ana enstruman</span>
          <select value={primarySymbol} onChange={(event) => onPrimaryChange(event.target.value)}>
            <option value="">Enstruman sec</option>
            {quotes.map((item) => (
              <option key={`${item.symbol}-${item.source}`} value={item.symbol}>
                {item.symbol} {item.displayName ? `- ${item.displayName}` : ""}
              </option>
            ))}
          </select>
        </label>

        <label className="market-filter-field">
          <span>Karsilastirma ara</span>
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="BTCUSDT, THYAO, USDTRY..."
          />
        </label>
      </div>

      <div className="analysis-selected-strip">
        {selectedSymbols.length === 0 ? <span className="muted">Karsilastirma icin en az bir enstruman sec.</span> : null}
        {selectedSymbols.map((symbol) => (
          <button
            key={symbol}
            type="button"
            className="table-chip-button active"
            onClick={() => onToggleComparisonSymbol(symbol)}
          >
            {symbol}
          </button>
        ))}
      </div>

      <div className="analysis-picker-grid">
        {filteredQuotes.map((item) => {
          const active = selectedSymbols.includes(item.symbol);
          return (
            <button
              key={`${item.symbol}-${item.source}`}
              type="button"
              className={`analysis-picker-card${active ? " active" : ""}`}
              onClick={() => onToggleComparisonSymbol(item.symbol)}
            >
              <strong>{item.symbol}</strong>
              <span>{item.displayName || item.instrumentType || "-"}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
