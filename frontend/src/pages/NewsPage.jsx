import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Check, SlidersHorizontal, Star, X } from "lucide-react";
import { addNewsFavorite, removeNewsFavorite } from "../api/newsApi";
import { newsKeys } from "../api/queryKeys";
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
import useToast from "../hooks/useToast";
import { useNewsFavorites, useNewsList } from "../hooks/useNewsQueries";
import { NEWS_PAGE_SIZE, buildNewsQueryParams } from "./newsPageQueryUtils";

const DEFAULT_PAGE_SIZE = NEWS_PAGE_SIZE;
const REGULAR_PROVIDERS = ["AA_RSS", "CNBC_RSS", "GUARDIAN"];
const KAP_PROVIDERS = ["KAP"];
const MULTI_FILTER_SEPARATOR = ",";
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

function parseMultiFilter(value) {
  return String(value || "")
    .split(MULTI_FILTER_SEPARATOR)
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function writeMultiFilter(params, key, values) {
  const normalized = [...new Set(values.filter(Boolean))];
  if (normalized.length > 0) {
    params.set(key, normalized.join(MULTI_FILTER_SEPARATOR));
  } else {
    params.delete(key);
  }
}

function toggleMultiValue(currentValues, value) {
  return currentValues.includes(value)
    ? currentValues.filter((entry) => entry !== value)
    : [...currentValues, value];
}

export default function NewsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const { isAuthenticated, login, userId } = useAuth();
  const { toast, showToast } = useToast();
  const [keyword, setKeyword] = useState(() => searchParams.get("q") || "");
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  const [favoriteBusyIds, setFavoriteBusyIds] = useState(() => new Set());

  const feedType = searchParams.get("tab") || "news";
  const currentPage = Number(searchParams.get("page") || "0");
  const sortBy = searchParams.get("sort") || "publishedAt";
  const selectedCategory = searchParams.get("category") || "";
  const selectedProvider = searchParams.get("provider") || "";
  const selectedLanguage = searchParams.get("language") || "";
  const favoritesOnly = searchParams.get("favorites") === "1";
  const dateFrom = searchParams.get("from") || "";
  const dateTo = searchParams.get("to") || "";
  const isKapFeed = feedType === "kap";
  const selectedCategories = useMemo(() => parseMultiFilter(selectedCategory), [selectedCategory]);
  const selectedProviders = useMemo(() => parseMultiFilter(selectedProvider), [selectedProvider]);
  const selectedLanguages = useMemo(() => parseMultiFilter(selectedLanguage), [selectedLanguage]);

  const updateParams = useCallback((updater) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        updater(next);
        return next;
      },
      { replace: true },
    );
  }, [setSearchParams]);

  // Ref: effect'in yalnızca keyword değişince çalışması için updateParams'ı
  // dep olarak almadan her render'da güncel tutuyoruz.
  const updateParamsRef = useRef(updateParams);
  updateParamsRef.current = updateParams;

  const newsQueryParams = useMemo(
    () =>
      buildNewsQueryParams(
        {
          keyword: searchParams.get("q") || "",
          category: selectedCategory,
          provider: selectedProvider,
          language: selectedLanguage,
          favoritesOnly: favoritesOnly && !isKapFeed && isAuthenticated && !!userId,
          fromDate: dateFrom || undefined,
          toDate: dateTo || undefined,
        },
        currentPage,
        sortBy,
        feedType,
      ),
    [searchParams, selectedCategory, selectedProvider, selectedLanguage, favoritesOnly, isKapFeed, isAuthenticated, userId, currentPage, sortBy, feedType, dateFrom, dateTo],
  );

  const {
    data: newsPage = INITIAL_NEWS_PAGE,
    isLoading: loading,
    error: newsError,
  } = useNewsList(newsQueryParams);

  const { data: favorites = [] } = useNewsFavorites(userId, {
    enabled: isAuthenticated && !!userId,
  });

  const error = newsError ? extractErrorMessage(newsError, t("news.loadError")) : "";
  const favoriteNewsIds = useMemo(
    () => new Set((favorites ?? []).map((favorite) => favorite.newsId).filter(Boolean)),
    [favorites],
  );

  function handleOpen(item) {
    if (!item?.id) return;
    navigate(`/news/${item.id}`, { state: { newsListSearch: location.search } });
  }

  const items = newsPage.content ?? [];
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
    selectedCategories.forEach((category) => {
      nextFilters.push({ type: "category", value: category, label: getNewsCategoryFilterLabel(category, t) });
    });
    selectedProviders.forEach((provider) => {
      nextFilters.push({ type: "provider", value: provider, label: getNewsProviderFilterLabel(provider, t) });
    });
    selectedLanguages.forEach((language) => {
      if (language === "tr") nextFilters.push({ type: "language", value: "tr", label: t("common.turkish") });
      if (language === "en") nextFilters.push({ type: "language", value: "en", label: t("common.english") });
    });
    return nextFilters.filter((f) => f.label);
  }, [selectedCategories, selectedProviders, selectedLanguages, t]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      updateParamsRef.current((params) => {
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
      if (value === ALL_CATEGORY_OPTION_VALUE) {
        params.delete("category");
      } else {
        writeMultiFilter(params, "category", toggleMultiValue(parseMultiFilter(params.get("category")), value));
      }
      params.set("page", "0");
    });
  }

  function handleToggleProvider(value) {
    updateParams((params) => {
      writeMultiFilter(params, "provider", toggleMultiValue(parseMultiFilter(params.get("provider")), value));
      params.set("page", "0");
    });
  }

  function handleToggleLanguage(value) {
    updateParams((params) => {
      writeMultiFilter(params, "language", toggleMultiValue(parseMultiFilter(params.get("language")), value));
      params.set("page", "0");
    });
  }

  function handleDateFromChange(value) {
    updateParams((params) => {
      if (value) params.set("from", value);
      else params.delete("from");
      params.set("page", "0");
    });
  }

  function handleDateToChange(value) {
    updateParams((params) => {
      if (value) params.set("to", value);
      else params.delete("to");
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
      params.delete("from");
      params.delete("to");
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
      params.delete("favorites");
    });
  }

  function handleRemoveActiveFilter(filter) {
    if (!filter?.type) return;
    updateParams((params) => {
      if (filter.type === "category") {
        writeMultiFilter(params, "category", parseMultiFilter(params.get("category")).filter((value) => value !== filter.value));
      } else if (filter.type === "provider") {
        writeMultiFilter(params, "provider", parseMultiFilter(params.get("provider")).filter((value) => value !== filter.value));
      } else if (filter.type === "language") {
        writeMultiFilter(params, "language", parseMultiFilter(params.get("language")).filter((value) => value !== filter.value));
      }
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

  function handleToggleFavoritesFilter() {
    if (!isAuthenticated || !userId) {
      showToast("error", t("news.favoriteLoginRequired"));
      login?.();
      return;
    }
    updateParams((params) => {
      if (params.get("favorites") === "1") {
        params.delete("favorites");
      } else {
        params.set("favorites", "1");
      }
      params.set("page", "0");
    });
  }

  async function handleFavoriteToggle(item) {
    if (!item?.id) return;
    if (!isAuthenticated || !userId) {
      showToast("error", t("news.favoriteLoginRequired"));
      login?.();
      return;
    }
    if (favoriteBusyIds.has(item.id)) return;

    const wasFavorite = favoriteNewsIds.has(item.id);
    setFavoriteBusyIds((prev) => new Set(prev).add(item.id));
    try {
      if (wasFavorite) {
        await removeNewsFavorite(item.id);
        showToast("success", t("news.favoriteRemoved"));
      } else {
        await addNewsFavorite(item.id);
        showToast("success", t("news.favoriteAdded"));
      }
      await queryClient.invalidateQueries({ queryKey: newsKeys.favorites(userId) });
      if (favoritesOnly) {
        await queryClient.invalidateQueries({ queryKey: newsKeys.all });
      }
    } catch (favoriteError) {
      showToast("error", extractErrorMessage(favoriteError, t("news.favoriteError")));
    } finally {
      setFavoriteBusyIds((prev) => {
        const next = new Set(prev);
        next.delete(item.id);
        return next;
      });
    }
  }

  const favoritesFilterButton = !isKapFeed ? (
    <button
      type="button"
      className={`news-favorites-filter-btn${favoritesOnly ? " active" : ""}`}
      onClick={handleToggleFavoritesFilter}
      aria-pressed={favoritesOnly}
    >
      <Star size={15} strokeWidth={2.2} fill={favoritesOnly ? "currentColor" : "none"} aria-hidden="true" />
      <span>{t("news.favoritesFilter")}</span>
    </button>
  ) : null;

  return (
    <div className="news-page-stack">
      {toast ? (
        <div key={toast.id} className={`toast-notify ${toast.type}`}>
          {toast.type === "success" ? (
            <Check size={15} strokeWidth={2.5} className="toast-notify-icon" />
          ) : (
            <X size={15} strokeWidth={2.5} className="toast-notify-icon" />
          )}
          <span>{toast.message}</span>
        </div>
      ) : null}

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
            dateFrom={dateFrom}
            dateTo={dateTo}
            onToggleCategory={handleToggleCategory}
            onToggleProvider={handleToggleProvider}
            onToggleLanguage={handleToggleLanguage}
            onDateFromChange={handleDateFromChange}
            onDateToChange={handleDateToChange}
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
              beforeActions={favoritesFilterButton}
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
                <NewsCard
                  key={item.id || item.externalId || item.url}
                  item={item}
                  onClick={handleOpen}
                  isFavorite={favoriteNewsIds.has(item.id)}
                  favoriteBusy={favoriteBusyIds.has(item.id)}
                  onFavoriteToggle={handleFavoriteToggle}
                />
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
        dateFrom={dateFrom}
        dateTo={dateTo}
        onToggleCategory={handleToggleCategory}
        onToggleProvider={handleToggleProvider}
        onToggleLanguage={handleToggleLanguage}
        onDateFromChange={handleDateFromChange}
        onDateToChange={handleDateToChange}
        onReset={handleResetFilters}
        loading={loading}
      />
    </div>
  );
}
