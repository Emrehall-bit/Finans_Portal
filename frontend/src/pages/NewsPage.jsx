import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { RefreshCw, RotateCcw, Search, SlidersHorizontal } from "lucide-react";
import { syncNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import PaginationControls from "../components/common/PaginationControls";
import NewsCard from "../components/news/NewsCard";
import NewsFilterDrawer from "../components/news/NewsFilterDrawer";
import NewsFeedSkeleton from "../components/news/NewsFeedSkeleton";
import {
  ALL_CATEGORY_OPTION_VALUE,
  getNewsCategoryFilterLabel,
  getNewsCategoryFilterOptions,
  getNewsProviderFilterLabel,
  getNewsProviderFilterOptions,
} from "../components/news/newsFilterOptions";
import NewsSidebarFilters from "../components/news/NewsSidebarFilters";
import { useNewsList } from "../hooks/useNewsQueries";
import { NEWS_PAGE_SIZE, buildNewsQueryParams } from "./newsPageQueryUtils";

const DEFAULT_PAGE_SIZE = NEWS_PAGE_SIZE;
const REGULAR_PROVIDERS = ["AA_RSS", "CNBC_RSS", "GUARDIAN"];
const KAP_PROVIDERS = ["KAP"];
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
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const [filters, setFilters] = useState({ keyword: "", category: "", provider: "" });
  const [appliedFilters, setAppliedFilters] = useState({ keyword: "", category: "", provider: "", language: "" });
  const [sortBy, setSortBy] = useState("publishedAt");
  const [appliedSortBy, setAppliedSortBy] = useState("publishedAt");
  const [currentPage, setCurrentPage] = useState(0);
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  const [selectedCategories, setSelectedCategories] = useState([]);
  const [selectedProviders, setSelectedProviders] = useState([]);
  const [selectedLanguages, setSelectedLanguages] = useState([]);
  const [feedType, setFeedType] = useState("news");

  const newsQueryParams = useMemo(
    () => buildNewsQueryParams(appliedFilters, currentPage, appliedSortBy, feedType),
    [appliedFilters, currentPage, appliedSortBy, feedType],
  );

  const {
    data: newsPage = INITIAL_NEWS_PAGE,
    isLoading: loading,
    error: newsError,
    refetch,
  } = useNewsList(newsQueryParams);

  const error = newsError ? extractErrorMessage(newsError, t("news.loadError")) : "";

  async function handleSync() {
    try {
      const provider = appliedFilters.provider || (feedType === "kap" ? "KAP" : undefined);
      await syncNews({ provider });
      setCurrentPage(0);
      refetch();
    } catch (err) {
      // sync errors don't need state — user sees the old data
    }
  }

  function handleOpen(item) {
    if (!item?.id) {
      return;
    }

    navigate(`/news/${item.id}`);
  }

  const items = newsPage.content ?? [];
  const isKapFeed = feedType === "kap";
  const providerSeed = isKapFeed ? KAP_PROVIDERS : REGULAR_PROVIDERS;

  const providerOptions = useMemo(
    () => getNewsProviderFilterOptions(providerSeed, t),
    [providerSeed, t],
  );

  const categoryOptions = useMemo(
    () => (isKapFeed ? [] : getNewsCategoryFilterOptions(t)),
    [isKapFeed, t],
  );

  const languageOptions = useMemo(
    () => [
      { value: "", label: t("news.all") },
      { value: "tr", label: t("common.turkish") },
      { value: "en", label: t("common.english") },
    ],
    [t],
  );

  const draftFilters = useMemo(
    () => ({
      keyword: filters.keyword,
      category: selectedCategories[0] || "",
      provider: selectedProviders[0] || "",
      language: selectedLanguages.length === 1 ? selectedLanguages[0] : "",
    }),
    [filters.keyword, selectedCategories, selectedLanguages, selectedProviders],
  );

  const activeFilters = useMemo(() => {
    const nextFilters = [];

    if (draftFilters.category) {
      nextFilters.push({
        type: "category",
        value: draftFilters.category,
        label: getNewsCategoryFilterLabel(draftFilters.category, t),
      });
    }
    if (draftFilters.provider) {
      nextFilters.push({
        type: "provider",
        value: draftFilters.provider,
        label: getNewsProviderFilterLabel(draftFilters.provider, t),
      });
    }
    selectedLanguages.forEach((lang) => {
      if (lang === "tr") {
        nextFilters.push({ type: "language", value: "tr", label: t("common.turkish") });
      } else if (lang === "en") {
        nextFilters.push({ type: "language", value: "en", label: t("common.english") });
      }
    });

    return nextFilters.filter((filter) => filter.label);
  }, [draftFilters, selectedLanguages, t]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setAppliedFilters((current) => {
        const unchanged =
          current.keyword === draftFilters.keyword &&
          current.category === draftFilters.category &&
          current.provider === draftFilters.provider &&
          current.language === draftFilters.language;

        if (unchanged) {
          return current;
        }

        setCurrentPage(0);
        return draftFilters;
      });
    }, 250);

    return () => window.clearTimeout(timer);
  }, [draftFilters]);

  function handleToggleCategory(value) {
    if (value === ALL_CATEGORY_OPTION_VALUE) {
      setSelectedCategories([]);
      return;
    }
    setSelectedCategories((current) => (current.includes(value) ? [] : [value]));
  }

  function handleToggleProvider(value) {
    setSelectedProviders((current) => (current.includes(value) ? [] : [value]));
  }

  function handleToggleLanguage(value) {
    setSelectedLanguages((current) => (current.includes(value) ? [] : [value]));
  }

  function handleResetFilters() {
    setSelectedCategories([]);
    setSelectedProviders([]);
    setSelectedLanguages([]);
    setFilters((prev) => ({ ...prev, category: "", provider: "" }));
  }

  function handleReload() {
    refetch();
  }

  function handleSortChange(value) {
    setSortBy(value);
    setAppliedSortBy(value);
    setCurrentPage(0);
  }

  function handleFeedTypeChange(nextFeedType) {
    if (nextFeedType === feedType) {
      return;
    }

    setFeedType(nextFeedType);
    setCurrentPage(0);
    setSelectedCategories([]);
    setSelectedProviders([]);
  }

  function handleRemoveActiveFilter(filter) {
    if (!filter?.type) {
      return;
    }

    if (filter.type === "category") {
      setSelectedCategories((current) => current.filter((item) => item !== filter.value));
      return;
    }

    if (filter.type === "provider") {
      setSelectedProviders((current) => current.filter((item) => item !== filter.value));
      return;
    }

    if (filter.type === "language") {
      setSelectedLanguages((current) => current.filter((l) => l !== filter.value));
    }
  }

  function handlePageChange(page) {
    if (loading || page === currentPage || page < 0 || page >= newsPage.totalPages) {
      return;
    }
    setCurrentPage(page);
  }

  function handlePreviousPage() {
    handlePageChange(currentPage - 1);
  }

  function handleNextPage() {
    handlePageChange(currentPage + 1);
  }

  return (
    <div className="news-page-stack">
      <div className="news-page-header panel-surface">
        <div className="news-page-header-title-row">
          <div className="news-page-header-copy">
            <p className="eyebrow">{t("news.eyebrow")}</p>
            <h1 className="news-page-h1">{t("news.title")}</h1>
          </div>
          {isAdmin ? (
            <div className="news-header-actions">
              <button
                type="button"
                className="news-header-icon-btn"
                onClick={handleReload}
                disabled={loading}
                title={t("news.refresh")}
              >
                <RotateCcw size={15} />
              </button>
              <button
                type="button"
                className="news-header-icon-btn"
                onClick={handleSync}
                disabled={loading}
                title={t("news.sync")}
              >
                <RefreshCw size={15} />
              </button>
            </div>
          ) : null}
        </div>

        <div className="news-page-header-search-row">
          <label className="news-search-field" htmlFor="news-search-input">
            <Search size={16} className="news-search-icon" aria-hidden="true" />
            <input
              id="news-search-input"
              value={filters.keyword}
              onChange={(e) => setFilters((current) => ({ ...current, keyword: e.target.value }))}
              placeholder={t("news.searchPlaceholder")}
            />
          </label>
          <select
            className="news-compact-sort"
            value={sortBy}
            onChange={(e) => handleSortChange(e.target.value)}
            aria-label={t("news.sort")}
          >
            <option value="publishedAt">{t("news.sortNewest")}</option>
            <option value="importanceScore">{t("news.sortImportance")}</option>
          </select>
          <button
            type="button"
            className="news-header-icon-btn news-header-filter-btn"
            onClick={() => setMobileFiltersOpen(true)}
          >
            <SlidersHorizontal size={16} />
            {activeFilters.length > 0 ? (
              <span className="news-filter-badge">{activeFilters.length}</span>
            ) : null}
          </button>
        </div>

        <div className="news-page-header-bottom-row">
          <div className="news-feed-segmented" role="tablist" aria-label={t("news.feedTabs")}>
            <button
              type="button"
              role="tab"
              className={`news-feed-seg-btn${feedType === "news" ? " active" : ""}`}
              onClick={() => handleFeedTypeChange("news")}
            >
              {t("news.tabs.news")}
            </button>
            <button
              type="button"
              role="tab"
              className={`news-feed-seg-btn${feedType === "kap" ? " active" : ""}`}
              onClick={() => handleFeedTypeChange("kap")}
            >
              {t("news.tabs.kap")}
            </button>
          </div>
        </div>

        {activeFilters.length > 0 ? (
          <div className="news-active-filter-row" aria-label={t("news.activeFilters")}>
            {activeFilters.map((filter) => (
              <button
                key={`${filter.type}:${filter.value}`}
                type="button"
                className="news-active-filter-chip"
                onClick={() => handleRemoveActiveFilter(filter)}
              >
                <span>{filter.label}</span>
                <span className="news-active-filter-chip-close" aria-hidden="true">x</span>
              </button>
            ))}
          </div>
        ) : null}
      </div>

      <div className="news-layout-grid">
        <aside className="news-sidebar-column">
          <NewsSidebarFilters
            categoryOptions={categoryOptions}
            providerOptions={providerOptions}
            languageOptions={languageOptions.filter((option) => option.value).map((option) => ({ value: option.value, label: option.label }))}
            selectedCategories={selectedCategories}
            selectedProviders={selectedProviders}
            selectedLanguages={selectedLanguages}
            onToggleCategory={handleToggleCategory}
            onToggleProvider={handleToggleProvider}
            onToggleLanguage={handleToggleLanguage}
            onReset={handleResetFilters}
            loading={loading}
          />
        </aside>

        <div className="news-content-column">
          {!loading && !error && items.length > 0 ? (
            <div className="news-feed-section-label">
              <span className="eyebrow">{t("news.publishFlowEyebrow")}</span>
              <h3>{isKapFeed ? t("news.kapFlowTitle") : t("news.publishFlowTitle")}</h3>
            </div>
          ) : null}

          {loading && items.length === 0 ? <NewsFeedSkeleton /> : null}
          {error ? <ErrorMessage message={error} /> : null}
          {!loading && !error && items.length === 0 ? (
            <EmptyState title={t("news.emptyTitle")} description={t("news.emptyDescription")} />
          ) : null}

          {!loading && !error && items.length > 0 ? (
            <section className={`news-grid news-grid-portal${isKapFeed ? " news-grid-kap" : ""}`}>
              {items.map((item) => (
                <NewsCard key={item.id || item.externalId || item.url} item={item} onClick={handleOpen} />
              ))}
            </section>
          ) : null}

          {!error && newsPage.totalPages > 1 ? (
            <PaginationControls
              className="news-pagination-card-bottom"
              currentPage={currentPage}
              totalPages={newsPage.totalPages}
              totalElements={newsPage.totalElements}
              loading={loading}
              isFirstPage={newsPage.first || currentPage === 0}
              isLastPage={newsPage.last || currentPage >= newsPage.totalPages - 1}
              onPrevious={handlePreviousPage}
              onNext={handleNextPage}
              onPageChange={handlePageChange}
            />
          ) : null}
        </div>
      </div>

      <NewsFilterDrawer
        open={mobileFiltersOpen}
        onClose={() => setMobileFiltersOpen(false)}
        categoryOptions={categoryOptions}
        providerOptions={providerOptions}
        languageOptions={languageOptions.filter((option) => option.value).map((option) => ({ value: option.value, label: option.label }))}
        selectedCategories={selectedCategories}
        selectedProviders={selectedProviders}
        selectedLanguages={selectedLanguages}
        onToggleCategory={handleToggleCategory}
        onToggleProvider={handleToggleProvider}
        onToggleLanguage={handleToggleLanguage}
        onReset={handleResetFilters}
        loading={loading}
      />
    </div>
  );
}
