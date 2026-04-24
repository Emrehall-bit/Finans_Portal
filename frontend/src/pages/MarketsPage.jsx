import { useEffect, useMemo, useState } from "react";
import { getMarketQuotes } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";

export default function MarketsPage() {
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [sourceFilter, setSourceFilter] = useState("ALL");

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");
        const data = await getMarketQuotes();
        if (!active) {
          return;
        }
        setQuotes(data ?? []);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, "Piyasa verileri yuklenemedi."));
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
  }, []);

  const sources = useMemo(() => {
    return ["ALL", ...new Set(quotes.map((item) => item.source).filter(Boolean))];
  }, [quotes]);

  const filteredQuotes = useMemo(() => {
    const query = search.trim().toLowerCase();

    return quotes.filter((item) => {
      const matchesSource = sourceFilter === "ALL" || item.source === sourceFilter;
      const matchesQuery =
        query.length === 0 ||
        item.symbol?.toLowerCase().includes(query) ||
        item.displayName?.toLowerCase().includes(query);

      return matchesSource && matchesQuery;
    });
  }, [quotes, search, sourceFilter]);

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow="Market Data"
        title="Piyasa Verileri"
        description="Backend market modulu tarafindan toplanan tum sembolleri tek ekranda izleyin."
      />

      <section className="panel-surface markets-toolbar">
        <div className="market-filter-field">
          <span>Sembol veya isim ara</span>
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="THYAO, USDTRY, BTCUSDT..." />
        </div>

        <div className="market-filter-field">
          <span>Kaynak</span>
          <select value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value)}>
            {sources.map((source) => (
              <option key={source} value={source}>
                {source}
              </option>
            ))}
          </select>
        </div>

        <div className="market-toolbar-stat">
          <span>Gorunen veri</span>
          <strong>{filteredQuotes.length}</strong>
        </div>
      </section>

      {loading ? <LoadingSpinner label="Piyasa verileri yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        filteredQuotes.length === 0 ? (
          <EmptyState title="Piyasa verisi bulunamadi" description="Filtreleri degistirip tekrar deneyin." />
        ) : (
          <section className="panel-surface market-table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Sembol</th>
                    <th>Ad</th>
                    <th>Tip</th>
                    <th>Kaynak</th>
                    <th>Fiyat</th>
                    <th>Degisim</th>
                    <th>Para Birimi</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredQuotes.map((item) => (
                    <tr key={`${item.symbol}-${item.source}`}>
                      <td>{item.symbol}</td>
                      <td>{item.displayName || "-"}</td>
                      <td>{item.instrumentType || "-"}</td>
                      <td>{item.source || "-"}</td>
                      <td>{item.price ?? "-"}</td>
                      <td>
                        <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                          {formatMarketChange(item.changeRate)}
                        </span>
                      </td>
                      <td>{item.currency || "-"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )
      ) : null}
    </div>
  );
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
