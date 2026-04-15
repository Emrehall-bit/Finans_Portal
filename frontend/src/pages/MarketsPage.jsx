import { useEffect, useMemo, useState } from "react";
import { getTcmbMarketData } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import MarketTable from "../components/markets/MarketTable";

export default function MarketsPage() {
  const [rows, setRows] = useState([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function loadData() {
    try {
      setLoading(true);
      setError("");
      setRows(await getTcmbMarketData());
    } catch (err) {
      setError(extractErrorMessage(err, "Market data could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((item) =>
      [item.symbol, item.name, item.source].some((value) => String(value || "").toLowerCase().includes(q)),
    );
  }, [rows, search]);

  return (
    <div>
      <PageHeader
        title="Market Data"
        description="Current market records from /api/v1/markets/tcmb."
        actions={
          <button onClick={loadData} disabled={loading}>
            Refresh
          </button>
        }
      />
      <div className="card">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search symbol, name, source"
        />
      </div>
      {loading ? <LoadingSpinner label="Loading market data..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && filtered.length === 0 ? (
        <EmptyState title="No market records" description="Try refreshing or changing search filter." />
      ) : null}
      {!loading && !error && filtered.length > 0 ? <MarketTable rows={filtered} /> : null}
    </div>
  );
}
