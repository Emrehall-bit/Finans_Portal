import { useEffect, useState } from "react";
import { getNews, getNewsDetail, syncNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import NewsCard from "../components/news/NewsCard";
import { formatDateTime } from "../utils/formatters";

export default function NewsPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [filters, setFilters] = useState({ keyword: "", category: "", provider: "" });

  async function loadNews(nextFilters = filters) {
    try {
      setLoading(true);
      setError("");
      setItems(await getNews(nextFilters));
    } catch (err) {
      setError(extractErrorMessage(err, "News could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadNews();
  }, []);

  async function handleOpen(item) {
    if (!item?.id) {
      setSelected(item);
      return;
    }
    try {
      const detail = await getNewsDetail(item.id);
      setSelected(detail || item);
    } catch {
      setSelected(item);
    }
  }

  async function handleSync() {
    try {
      await syncNews({ provider: filters.provider || undefined, scope: "GLOBAL" });
      await loadNews();
    } catch (err) {
      setError(extractErrorMessage(err, "News sync failed."));
    }
  }

  return (
    <div>
      <PageHeader
        title="News"
        description="Financial news with backend-supported filtering and sync."
        actions={
          <div className="actions-row">
            <button onClick={() => loadNews()} disabled={loading}>
              Reload
            </button>
            <button onClick={handleSync} disabled={loading}>
              Sync News
            </button>
          </div>
        }
      />
      <div className="card filters-row">
        <input
          placeholder="Keyword"
          value={filters.keyword}
          onChange={(e) => setFilters((p) => ({ ...p, keyword: e.target.value }))}
        />
        <input
          placeholder="Category"
          value={filters.category}
          onChange={(e) => setFilters((p) => ({ ...p, category: e.target.value }))}
        />
        <input
          placeholder="Provider"
          value={filters.provider}
          onChange={(e) => setFilters((p) => ({ ...p, provider: e.target.value }))}
        />
        <button onClick={() => loadNews()}>Apply Filters</button>
      </div>

      {loading ? <LoadingSpinner label="Loading news..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && items.length === 0 ? (
        <EmptyState title="No news found" description="Adjust filters or sync from providers." />
      ) : null}

      <div className="news-grid">
        {items.map((item) => (
          <NewsCard key={item.id || item.externalId || item.url} item={item} onClick={handleOpen} />
        ))}
      </div>

      {selected ? (
        <div className="card">
          <h3>News Detail</h3>
          <p>
            <strong>{selected.title || "Untitled"}</strong>
          </p>
          <p>{selected.summary || "No summary provided."}</p>
          <p className="muted">
            {selected.provider || selected.source || "-"} | {formatDateTime(selected.publishedAt)}
          </p>
          {selected.url ? (
            <a href={selected.url} target="_blank" rel="noreferrer">
              Open source link
            </a>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
