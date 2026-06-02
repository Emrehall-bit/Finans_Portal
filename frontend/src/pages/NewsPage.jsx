import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { SlidersHorizontal } from "lucide-react";
import { extractErrorMessage } from "../api/responseUtils";
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
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(() => searchParams.get("q") || "");
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);

  const feedType = searchParams.get("tab") || "news";
  const currentPage = Number(searchParams.get("page") || "0");
  const sortBy = searchParams.get("sort") || "publishedAt";
  const selectedCategory = searchParams.get("category") || "";
  const selectedProvider = searchParams.get("provider") || "";
  const selectedLanguage = searchParams.get("language") || "";
  const selectedCategories = selectedCategory ? [selectedCategory] : [];
  const selectedProviders = selectedProvider ? [selectedProvider] : [];
  const selectedLanguages = selectedLanguage ? [selectedLanguage] : [];

  function updateParams(updater) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        updater(next);
        return next;
      },
      { replace: true },
    );
  }

  const newsQueryParams = useMemo(
    () =>
      buildNewsQueryParams(
        {
          keyword: searchParams.get("q") || "",
          category: selectedCategory,
          provider: selectedProvider,
          language: selectedLanguage,
        },
        currentPage,
        sortBy,
        feedType,
      ),
    [searchParams, selectedCategory, selectedProvider, selectedLanguage, currentPage, sortBy, feedType],
  );

  const {
    data: newsPage = INITIAL_NEWS_PAGE,
    isLoading: loading,
    error: newsError,
  } = useNewsList(newsQueryParams);

  const error = newsError ? extractErrorMessage(newsError, t("news.loadError")) : "";

  function handleOpen(item) {
    if (!item?.id) return;
    navigate(`/news/${item.id}`, { state: { newsListSearch: location.search } });
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

  const visibleLanguageOptions = useMemo(
    () => languageOptions.filter((option) => option.value).map((option) => ({ value: option.value, label: option.label })),
    [languageOptions],
  );

  const activeFilters = useMemo(() => {
    const nextFilters = [];
    if (selectedCategory) {
      nextFilters.push({ type: "category", value: selectedCategory, label: getNewsCategoryFilterLabel(selectedCategory, t) });
    }
    if (selectedProvider) {
      nextFilters.push({ type: "provider", value: selectedProvider, label: getNewsProviderFilterLabel(selectedProvider, t) });
    }
    if (selectedLanguage === "tr") nextFilters.push({ type: "language", value: "tr", label: t("common.turkish") });
    if (selectedLanguage === "en") nextFilters.push({ type: "language", value: "en", label: t("common.english") });
    return nextFilters.filter((f) => f.label);
  }, [selectedCategory, selectedProvider, selectedLanguage, t]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      updateParams((params) => {
        if (keyword) {
          params.set("q", keyword);
        } else {
          params.delete("q");
        }
        params.set("page", "0");
      });
    }, 250);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  function handleToggleCategory(value) {
    updateParams((params) => {
      if (value === ALL_CATEGORY_OPTION_VALUE || params.get("category") === value) {
        params.delete("category");
      } else {
        params.set("category", value);
      }
      params.set("page", "0");
    });
  }

  function handleToggleProvider(value) {
    updateParams((params) => {
      if (params.get("provider") === value) {
        params.delete("provider");
      } else {
        params.set("provider", value);
      }
      params.set("page", "0");
    });
  }

  function handleToggleLanguage(value) {
    updateParams((params) => {
      if (params.get("language") === value) {
        params.delete("language");
      } else {
        params.set("language", value);
      }
      params.set("page", "0");
    });
  }

  function handleResetFilters() {
    setKeyword("");
    updateParams((params) => {
      params.delete("category");
      params.delete("provider");
      params.delete("language");
      params.delete("q");
      params.set("page", "0");
    });
  }

  function handleSortChange(value) {
    updateParams((params) => {
      params.set("sort", value);
      params.set("page", "0");
    });
  }

  function handleFeedTypeChange(nextFeedType) {
    if (nextFeedType === feedType) return;
    updateParams((params) => {
      params.set("tab", nextFeedType);
      params.set("page", "0");
      params.delete("category");
      params.delete("provider");
    });
  }

  function handleRemoveActiveFilter(filter) {
    if (!filter?.type) return;
    updateParams((params) => {
      if (filter.type === "category") params.delete("category");
      else if (filter.type === "provider") params.delete("provider");
      else if (filter.type === "language") params.delete("language");
      params.set("page", "0");
    });
  }

  function handlePageChange(page) {
    if (loading || page === currentPage || page < 0 || page >= newsPage.totalPages) return;
    updateParams((params) => params.set("page", String(page)));
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
        <div className="news-page-header-main">
          <div className="news-page-header-left">
            <div className="news-page-header-copy">
              <h1 className="news-page-h1">{t("news.title")}</h1>
            </div>

            <div className="news-page-header-tabs" role="tablist" aria-label={t("news.feedTabs")}>
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

          <div className="news-page-header-right">
            <label className="news-search-field news-search-field-plain" htmlFor="news-search-input">
              <input
                id="news-search-input"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder={t("news.searchPlaceholder")}
              />
            </label>

            <select
              className="news-compact-sort"
              value={sortBy}
              onChange={(event) => handleSortChange(event.target.value)}
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
              <span>{t("common.filters")}</span>
              {activeFilters.length > 0 ? <span className="news-filter-badge">{activeFilters.length}</span> : null}
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
            languageOptions={visibleLanguageOptions}
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
          {!error && newsPage.totalPages > 0 ? (
            <PaginationControls
              className="news-pagination-card-top"
              variant="news-top"
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

          {!loading && !error && items.length > 0 ? (
            <div className="news-feed-section-label">
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
              variant="news-bottom"
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
        languageOptions={visibleLanguageOptions}
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
