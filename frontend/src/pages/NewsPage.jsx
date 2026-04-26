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
import {
  formatNewsPublishedAt,
  getNewsLanguageLabel,
  getNewsProviderLabel,
  getNewsSummaryText,
} from "../components/news/newsCardUtils";
import { buildNewsQueryParams, NEWS_SORT_OPTIONS } from "./newsPageQueryUtils";

const DEFAULT_PAGE_SIZE = 20;
const KNOWN_PROVIDERS = ["FINNHUB", "BLOOMBERG_HT", "AA_RSS"];
const KNOWN_CATEGORIES = ["ECONOMY"];
const KNOWN_LANGUAGES = ["tr", "en"];
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
  const [filters, setFilters] = useState({ keyword: "", category: "", provider: "", language: "" });
  const [appliedFilters, setAppliedFilters] = useState({ keyword: "", category: "", provider: "", language: "" });
  const [sortBy, setSortBy] = useState("publishedAt");
  const [currentPage, setCurrentPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function loadNews() {
      try {
        setLoading(true);
        setError("");
        const requestParams = buildNewsQueryParams(appliedFilters, currentPage, sortBy);
        console.debug("News page query params", requestParams);
        const result = await getNews(requestParams);

        if (!active) {
          return;
        }

        setNewsPage(result);
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, "Haberler yüklenemedi."));
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
  }, [appliedFilters, currentPage, refreshKey, sortBy]);

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
      await syncNews({ provider: appliedFilters.provider || undefined });
      setSelected(null);
      setCurrentPage(0);
      setRefreshKey((prev) => prev + 1);
    } catch (err) {
      setError(extractErrorMessage(err, "Haber senkronizasyonu başarısız oldu."));
    }
  }

  const items = newsPage.content ?? [];
  const featuredItem = items[0] ?? null;
  const feedItems = featuredItem ? items.slice(1) : items;
  const selectedProviderLabel = getNewsProviderLabel(selected?.provider);
  const selectedLanguageLabel = getNewsLanguageLabel(selected?.language);

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
      ...KNOWN_CATEGORIES,
      filters.category,
      appliedFilters.category,
      ...items.map((item) => item?.category).filter(Boolean),
    ]);

    return [...values].filter(Boolean).sort((a, b) => a.localeCompare(b));
  }, [appliedFilters.category, filters.category, items]);

  const languageOptions = useMemo(() => {
    const values = new Set([
      ...KNOWN_LANGUAGES,
      filters.language,
      appliedFilters.language,
      ...items.map((item) => item?.language).filter(Boolean),
    ]);

    return [...values].filter(Boolean).sort((a, b) => a.localeCompare(b));
  }, [appliedFilters.language, filters.language, items]);

  function handleSelectFilterChange(field, value) {
    setFilters((prev) => ({ ...prev, [field]: value }));
    setAppliedFilters((prev) => ({ ...prev, [field]: value }));
    setCurrentPage(0);
    setSelected(null);
  }

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

  function handleSortChange(value) {
    setSortBy(value);
    setCurrentPage(0);
    setSelected(null);
  }

  return (
    <div className="news-page-stack">
      <PageHeader
        title="Haberler"
        description="Piyasa etkisi yüksek manşetleri, sağlayıcı akışlarını ve finans odaklı gelişmeleri tek ekranda takip edin."
        eyebrow="Gündem"
        actions={
          <div className="actions-row">
            <button onClick={handleReload} disabled={loading}>
              Yenile
            </button>
            <button onClick={handleSync} disabled={loading}>
              Haberleri Senkronize Et
            </button>
          </div>
        }
      />

      <section className="panel-surface news-toolbar">
        <div className="news-toolbar-copy">
          <p className="eyebrow">Akış</p>
          <h3>Canlı haber akışını filtreleyin</h3>
          <p className="muted">
            Anahtar kelimeyle arayın, sağlayıcı seçin veya kategori odaklı görünüm oluşturun.
          </p>
        </div>

        <div className="news-filter-grid">
          <label className="news-filter-field">
            <span>Anahtar Kelime</span>
            <input
              placeholder="Enflasyon, bilanço, faiz..."
              value={filters.keyword}
              onChange={(e) => setFilters((p) => ({ ...p, keyword: e.target.value }))}
            />
          </label>
          <label className="news-filter-field">
            <span>Kategori</span>
            <select
              value={filters.category}
              onChange={(e) => handleSelectFilterChange("category", e.target.value)}
            >
              <option value="">Tüm kategoriler</option>
              {categoryOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label className="news-filter-field">
            <span>Sağlayıcı</span>
            <select
              value={filters.provider}
              onChange={(e) => handleSelectFilterChange("provider", e.target.value)}
            >
              <option value="">Tüm sağlayıcılar</option>
              {providerOptions.map((option) => (
                <option key={option} value={option}>
                  {getNewsProviderLabel(option)}
                </option>
              ))}
            </select>
          </label>
          <label className="news-filter-field">
            <span>Dil</span>
            <select
              value={filters.language}
              onChange={(e) => handleSelectFilterChange("language", e.target.value)}
            >
              <option value="">Tüm diller</option>
              {languageOptions.map((option) => (
                <option key={option} value={option}>
                  {option.toUpperCase()}
                </option>
              ))}
            </select>
          </label>
          <label className="news-filter-field">
            <span>Sıralama</span>
            <select
              value={sortBy}
              onChange={(e) => handleSortChange(e.target.value)}
            >
              {NEWS_SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="news-toolbar-actions">
          <button onClick={handleApplyFilters} disabled={loading}>
            Filtreleri Uygula
          </button>
          <span className="news-toolbar-note">
            {newsPage.totalElements} haber listeleniyor
          </span>
        </div>
      </section>

      {!loading && !error && items.length > 0 ? (
        <section className="news-feed-header">
          <div className="news-feed-copy">
            <p className="eyebrow">Akış</p>
            <h3>Güncel finans haberleri</h3>
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
        <EmptyState title="Haber bulunamadı" description="Filtreleri değiştirin veya sağlayıcılardan yeniden senkronize edin." />
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
          <div className="news-detail-topbar">
            <div className="news-detail-meta">
              <span className="news-card-badge category">{selected.category || "Gündem"}</span>
              <span className="news-card-badge provider">{selectedProviderLabel}</span>
              {selectedLanguageLabel ? <span className="news-meta-badge">{selectedLanguageLabel}</span> : null}
              <span className="muted">{formatNewsPublishedAt(selected.publishedAt)}</span>
            </div>
            <button type="button" className="secondary-button news-detail-back" onClick={() => setSelected(null)}>
              Haberlere dön
            </button>
          </div>

          <h3 className="news-detail-title">{selected.title || "Başlık bulunmuyor"}</h3>
          <p className={`news-detail-summary${selected.summary ? "" : " is-fallback"}`}>
            {getNewsSummaryText(selected.summary, "Bu haber için kaynak tarafından özet sağlanmadı.")}
          </p>

          <div className="news-detail-actions">
            {selected.url ? (
              <a className="secondary-button news-detail-link" href={selected.url} target="_blank" rel="noreferrer">
                Kaynakta aç
              </a>
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}
