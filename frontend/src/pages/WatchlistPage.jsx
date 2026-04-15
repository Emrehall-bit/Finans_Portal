import { useEffect, useState } from "react";
import { API_CONFIG } from "../api/config";
import { extractErrorMessage } from "../api/responseUtils";
import { addWatchlistItem, getUserWatchlist, removeWatchlistItem } from "../api/watchlistApi";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import WatchlistTable from "../components/watchlist/WatchlistTable";
import useToast from "../hooks/useToast";

export default function WatchlistPage() {
  const userId = API_CONFIG.DEMO_USER_ID;
  const [rows, setRows] = useState([]);
  const [instrumentCode, setInstrumentCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast, showToast } = useToast();

  async function loadData() {
    try {
      setLoading(true);
      setError("");
      setRows(await getUserWatchlist(userId));
    } catch (err) {
      setError(extractErrorMessage(err, "Watchlist could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    try {
      await addWatchlistItem(userId, { instrumentCode });
      setInstrumentCode("");
      showToast("success", "Watchlist item added.");
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, "Could not add watchlist item."));
    }
  }

  async function handleRemove(id) {
    try {
      await removeWatchlistItem(id);
      showToast("success", "Watchlist item removed.");
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, "Could not remove watchlist item."));
    }
  }

  return (
    <div>
      <PageHeader title="Watchlist" description={`Demo user id: ${userId}`} />
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      <form className="card form-inline" onSubmit={handleSubmit}>
        <input
          required
          value={instrumentCode}
          onChange={(e) => setInstrumentCode(e.target.value)}
          placeholder="Instrument code"
        />
        <button type="submit">Add</button>
      </form>
      {loading ? <LoadingSpinner label="Loading watchlist..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && rows.length === 0 ? (
        <EmptyState title="No watchlist items" description="Add your first favorite instrument." />
      ) : null}
      {!loading && !error && rows.length > 0 ? <WatchlistTable rows={rows} onRemove={handleRemove} /> : null}
    </div>
  );
}
