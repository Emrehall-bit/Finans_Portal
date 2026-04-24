import { useEffect, useMemo, useState } from "react";
import { getNews, getNewsDetail, syncNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import PageHeader from "../components/common/PageHeader";
import PaginationControls from "../components/common/PaginationControls";
import FeaturedNewsHero from "../components/news/FeaturedNewsHero";
import NewsCard from "../components/news/NewsCard";
import NewsFeedSkeleton from "../components/news/NewsFeedSkeleton";
import { formatDateTime } from "../utils/formatters";

const DEFAULT_PAGE_SIZE = 20;
const KNOWN_PROVIDERS = ["FINNHUB", "BLOOMBERG_HT"];
const INITIAL_NEWS_PAGE = {
  content: [],
  page: 0,
  size: DEFAULT_PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

export default function NewsPage() {
  const [newsPage, setNewsPage] = useState(INITIAL_NEWS_PAGE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [filters, setFilters] = useState({ keyword: "", category: "", provider: "" });
  const [appliedFilters, setAppliedFilters] = useState({ keyword: "", category: "", provider: "" });
  const [currentPage, setCurrentPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function loadNews() {
      try {
        setLoading(true);
        setError("");
        const result = await getNews({
          ...appliedFilters,
          page: currentPage,
          size: DEFAULT_PAGE_SIZE,
          sortBy: "publishedAt",
          sortDirection: "desc",
        });

        if (!active) {
          return;
        }

        setNewsPage(result);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, "News could not be loaded."));
        setNewsPage(INITIAL_NEWS_PAGE);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadNews();

    return () => {
      active = false;
    };
  }, [appliedFilters, currentPage, refreshKey]);

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
      await syncNews({ provider: appliedFilters.provider || undefined, scope: "GLOBAL" });
      setSelected(null);
      setCurrentPage(0);
      setRefreshKey((prev) => prev + 1);
    } catch (err) {
      setError(extractErrorMessage(err, "News sync failed."));
    }
  }

  const items = newsPage.content ?? [];
  const featuredItem = items[0] ?? null;
  const feedItems = featuredItem ? items.slice(1) : items;

  const providerOptions = useMemo(() => {
    const values = new Set([
      ...KNOWN_PROVIDERS,
      filters.provider,
      appliedFilters.provider,
      ...items.map((item) => item?.provider).filter(Boolean),
    ]);

    return [...values].filter(Boolean).sort((a, b) => a.localeCompare(b));
  }, [appliedFilters.provider, filters.provider, items]);

  const categoryOptions = useMemo(() => {
    const values = new Set([
      filters.category,
      appliedFilters.category,
      ...items.map((item) => item?.category).filter(Boolean),
    ]);

    return [...values].filter(Boolean).sort((a, b) => a.localeCompare(b));
  }, [appliedFilters.category, filters.category, items]);

  function handleApplyFilters() {
    setAppliedFilters({ ...filters });
    setCurrentPage(0);
    setSelected(null);
  }

  function handleReload() {
    setSelected(null);
    setRefreshKey((prev) => prev + 1);
  }

  function handlePreviousPage() {
    setCurrentPage((prev) => Math.max(prev - 1, 0));
  }

  function handleNextPage() {
    setCurrentPage((prev) => {
      if (newsPage.last) {
        return prev;
      }
      return prev + 1;
    });
  }

  function handlePageChange(page) {
    setCurrentPage(page);
  }

  return (
    <div className="news-page-stack">
      <PageHeader
        title="News"
        description="Market-moving headlines, provider feeds and finance-focused coverage in a cleaner newsroom layout."
        actions={
          <div className="actions-row">
            <button onClick={handleReload} disabled={loading}>
              Reload
            </button>
            <button onClick={handleSync} disabled={loading}>
              Sync News
            </button>
          </div>
        }
      />

      <section className="panel-surface news-toolbar">
        <div className="news-toolbar-copy">
          <p className="eyebrow">Newsroom</p>
          <h3>Filter the live feed</h3>
          <p className="muted">
            Search by keyword, narrow by provider or focus on a category without leaving the page.
          </p>
        </div>

        <div className="news-filter-grid">
          <label className="news-filter-field">
            <span>Keyword</span>
            <input
              placeholder="Inflation, earnings, Fed..."
              value={filters.keyword}
              onChange={(e) => setFilters((p) => ({ ...p, keyword: e.target.value }))}
            />
          </label>
          <label className="news-filter-field">
            <span>Category</span>
            <select
              value={filters.category}
              onChange={(e) => setFilters((p) => ({ ...p, category: e.target.value }))}
            >
              <option value="">All categories</option>
              {categoryOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label className="news-filter-field">
            <span>Provider</span>
            <select
              value={filters.provider}
              onChange={(e) => setFilters((p) => ({ ...p, provider: e.target.value }))}
            >
              <option value="">All providers</option>
              {providerOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="news-toolbar-actions">
          <button onClick={handleApplyFilters} disabled={loading}>
            Apply Filters
          </button>
          <span className="news-toolbar-note">
            {newsPage.totalElements} stories available
          </span>
        </div>
      </section>

      {!loading && !error && items.length > 0 ? (
        <section className="news-feed-header">
          <div className="news-feed-copy">
            <p className="eyebrow">Feed</p>
            <h3>Latest financial coverage</h3>
          </div>

          <PaginationControls
            currentPage={currentPage}
            totalPages={newsPage.totalPages}
            totalElements={newsPage.totalElements}
            loading={loading}
            isFirstPage={currentPage <= 0 || newsPage.first}
            isLastPage={newsPage.last}
            onPrevious={handlePreviousPage}
            onNext={handleNextPage}
            onPageChange={handlePageChange}
          />
        </section>
      ) : null}

      {loading ? <NewsFeedSkeleton /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && items.length === 0 ? (
        <EmptyState title="No news found" description="Adjust filters or sync from providers." />
      ) : null}

      {!loading && !error && featuredItem ? (
        <FeaturedNewsHero item={featuredItem} onOpen={handleOpen} />
      ) : null}

      {!loading && !error && feedItems.length > 0 ? (
        <section className="news-grid news-grid-portal">
          {feedItems.map((item) => (
            <NewsCard key={item.id || item.externalId || item.url} item={item} onClick={handleOpen} />
          ))}
        </section>
      ) : null}

      {!loading && !error && items.length > 0 ? (
        <PaginationControls
          currentPage={currentPage}
          totalPages={newsPage.totalPages}
          totalElements={newsPage.totalElements}
          loading={loading}
          isFirstPage={currentPage <= 0 || newsPage.first}
          isLastPage={newsPage.last}
          onPrevious={handlePreviousPage}
          onNext={handleNextPage}
          onPageChange={handlePageChange}
          className="news-pagination-card-bottom"
        />
      ) : null}

      {selected ? (
        <section className="panel-surface news-detail-card">
          <div className="news-detail-meta">
            <span className="news-card-badge">{selected.category || selected.provider || "Detail"}</span>
            <span className="muted">
              {selected.provider || selected.source || "-"} | {formatDateTime(selected.publishedAt)}
            </span>
          </div>

          <h3 className="news-detail-title">{selected.title || "Untitled"}</h3>
          <p className="news-detail-summary">{selected.summary || "No summary provided."}</p>

          {selected.url ? (
            <a className="secondary-button news-detail-link" href={selected.url} target="_blank" rel="noreferrer">
              Open source link
            </a>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
