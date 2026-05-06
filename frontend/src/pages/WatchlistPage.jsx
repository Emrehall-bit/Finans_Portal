import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { extractErrorMessage } from "../api/responseUtils";
import { addWatchlistItem, getUserWatchlist, removeWatchlistItem } from "../api/watchlistApi";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import WatchlistTable from "../components/watchlist/WatchlistTable";
import useToast from "../hooks/useToast";

export default function WatchlistPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
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
      setError(extractErrorMessage(err, t("watchlist.loadError")));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (userId) {
      loadData();
    }
  }, [userId]);

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      await addWatchlistItem(userId, { instrumentCode });
      setInstrumentCode("");
      showToast("success", t("watchlist.addSuccess"));
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, t("watchlist.addError")));
    }
  }

  async function handleRemove(id) {
    try {
      await removeWatchlistItem(id);
      showToast("success", t("watchlist.removeSuccess"));
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, t("watchlist.removeError")));
    }
  }

  return (
    <div>
      <PageHeader eyebrow={t("watchlist.eyebrow")} title={t("watchlist.title")} description={t("watchlist.description", { userId })} />
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      <form className="card form-inline" onSubmit={handleSubmit}>
        <input required value={instrumentCode} onChange={(event) => setInstrumentCode(event.target.value)} placeholder={t("watchlist.instrumentCode")} />
        <button type="submit">{t("watchlist.add")}</button>
      </form>
      {loading ? <LoadingSpinner label={t("watchlist.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && rows.length === 0 ? (
        <EmptyState title={t("watchlist.emptyTitle")} description={t("watchlist.emptyDescription")} />
      ) : null}
      {!loading && !error && rows.length > 0 ? <WatchlistTable rows={rows} onRemove={handleRemove} /> : null}
    </div>
  );
}
