import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { getNews, syncNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import PageHeader from "../components/common/PageHeader";
import NewsCard from "../components/news/NewsCard";
import NewsFilterDrawer from "../components/news/NewsFilterDrawer";
import NewsFeedSkeleton from "../components/news/NewsFeedSkeleton";
import NewsSidebarFilters from "../components/news/NewsSidebarFilters";
import NewsTopBar from "../components/news/NewsTopBar";
import { formatNewsCategoryLabel, getNewsProviderLabel } from "../components/news/newsCardUtils";
import { getStoredNewsLanguage, persistNewsLanguage } from "./newsLanguagePreference";
import { buildNewsQueryParams } from "./newsPageQueryUtils";

const DEFAULT_PAGE_SIZE = 20;
const KNOWN_PROVIDERS = ["FINNHUB", "AA_RSS", "INVESTING_RSS"];
const KNOWN_CATEGORIES = ["business", "ECONOMY", "top news", "general", "company"];
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
  const defaultLanguage = getStoredNewsLanguage();
  const [newsPage, setNewsPage] = useState(INITIAL_NEWS_PAGE);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [filters, setFilters] = useState({ keyword: "", category: "", provider: "", language: defaultLanguage });
  const [appliedFilters, setAppliedFilters] = useState({ keyword: "", category: "", provider: "", language: defaultLanguage });
  const [sortBy, setSortBy] = useState("publishedAt");
  const [appliedSortBy, setAppliedSortBy] = useState("publishedAt");
  const [currentPage, setCurrentPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  const [selectedCategories, setSelectedCategories] = useState(filters.category ? [filters.category] : []);
  const [selectedProviders, setSelectedProviders] = useState(filters.provider ? [filters.provider] : []);
  const [selectedLanguages, setSelectedLanguages] = useState(filters.language ? [filters.language] : []);

  useEffect(() => {
    let active = true;

    async function loadNews() {
      try {
        if (currentPage === 0) {
          setLoading(true);
        } else {
          setLoadingMore(true);
        }
        setError("");
        const requestParams = buildNewsQueryParams(appliedFilters, currentPage, appliedSortBy);
        const result = await getNews(requestParams);

        if (!active) {
          return;
        }

        setNewsPage((previous) => {
          if (currentPage === 0) {
            return result;
          }

          const mergedContent = [...(previous.content ?? []), ...(result.content ?? [])];
          const dedupedContent = [];
          const seen = new Set();

          mergedContent.forEach((item) => {
            const key = item?.id || item?.externalId || item?.url;
            if (!key || seen.has(key)) {
              return;
            }
            seen.add(key);
            dedupedContent.push(item);
          });

          return {
            ...result,
            content: dedupedContent,
          };
        });
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, t("news.loadError")));
        if (currentPage === 0) {
          setNewsPage(INITIAL_NEWS_PAGE);
        }
      } finally {
        if (active) {
          setLoading(false);
          setLoadingMore(false);
        }
      }
    }

    loadNews();

    return () => {
      active = false;
    };
  }, [appliedFilters, appliedSortBy, currentPage, refreshKey, t]);

  async function handleSync() {
    try {
      await syncNews({ provider: appliedFilters.provider || undefined });
      setCurrentPage(0);
      setRefreshKey((prev) => prev + 1);
    } catch (err) {
      setError(extractErrorMessage(err, t("news.syncError")));
    }
  }

  function handleOpen(item) {
    if (!item?.id) {
      return;
    }

    navigate(`/news/${item.id}`);
  }

  const items = newsPage.content ?? [];

  const providerOptions = useMemo(() => {
    const values = new Set(KNOWN_PROVIDERS);

    selectedProviders.forEach((provider) => {
      if (provider) {
        values.add(provider);
      }
    });

    return [...values].filter(Boolean).sort((a, b) => a.localeCompare(b));
  }, [selectedProviders]);

  const categoryOptions = useMemo(() => {
    const values = new Map();

    [...KNOWN_CATEGORIES, ...selectedCategories, ...items.map((item) => item?.category)]
      .filter(Boolean)
      .forEach((category) => {
        const key = category.trim().toLowerCase();
        if (!values.has(key)) {
          values.set(key, category);
        }
      });

    return [...values.values()].sort((a, b) =>
      (formatNewsCategoryLabel(a) || a).localeCompare(formatNewsCategoryLabel(b) || b),
    );
  }, [items, selectedCategories]);

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
      language: selectedLanguages[0] || "",
    }),
    [filters.keyword, selectedCategories, selectedLanguages, selectedProviders],
  );

  const activeFilters = useMemo(() => {
    const nextFilters = [];

    if (draftFilters.category) {
      nextFilters.push({
        type: "category",
        value: draftFilters.category,
        label: formatNewsCategoryLabel(draftFilters.category),
      });
    }
    if (draftFilters.provider) {
      nextFilters.push({
        type: "provider",
        value: draftFilters.provider,
        label: getNewsProviderLabel(draftFilters.provider),
      });
    }
    if (draftFilters.language === "tr") {
      nextFilters.push({
        type: "language",
        value: "tr",
        label: t("common.turkish"),
      });
    }
    if (draftFilters.language === "en") {
      nextFilters.push({
        type: "language",
        value: "en",
        label: t("common.english"),
      });
    }

    return nextFilters.filter((filter) => filter.label);
  }, [draftFilters, t]);

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

  function toggleSelection(value, selectedValues, setter) {
    setter(
      selectedValues.includes(value)
        ? selectedValues.filter((item) => item !== value)
        : [...selectedValues, value]
    );
  }

  function handleToggleCategory(value) {
    toggleSelection(value, selectedCategories, setSelectedCategories);
  }

  function handleToggleProvider(value) {
    toggleSelection(value, selectedProviders, setSelectedProviders);
  }

  function handleToggleLanguage(value) {
    const nextSelection = selectedLanguages.includes(value) ? [] : [value];
    setSelectedLanguages(nextSelection);
    persistNewsLanguage(nextSelection[0] || "");
  }

  function handleResetFilters() {
    const resetLanguage = getStoredNewsLanguage();
    setSelectedCategories([]);
    setSelectedProviders([]);
    setSelectedLanguages(resetLanguage ? [resetLanguage] : []);
    setFilters((prev) => ({
      ...prev,
      category: "",
      provider: "",
      language: resetLanguage,
    }));
  }

  function handleReload() {
    setRefreshKey((prev) => prev + 1);
  }

  function handleSortChange(value) {
    setSortBy(value);
    setAppliedSortBy(value);
    setCurrentPage(0);
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
      setSelectedLanguages([]);
      persistNewsLanguage("");
    }
  }

  function handleLoadMore() {
    if (loading || loadingMore || newsPage.last || !newsPage.hasNext) {
      return;
    }
    setCurrentPage((prev) => prev + 1);
  }

  return (
    <div className="news-page-stack">
      <PageHeader
        title={t("news.title")}
        description={t("news.description")}
        eyebrow={t("news.eyebrow")}
        actions={isAdmin ? (
          <div className="actions-row">
            <button onClick={handleReload} disabled={loading}>
              {t("news.refresh")}
            </button>
            <button onClick={handleSync} disabled={loading}>
              {t("news.sync")}
            </button>
          </div>
        ) : null}
      />

      <NewsTopBar
        keyword={filters.keyword}
        onKeywordChange={(value) => setFilters((current) => ({ ...current, keyword: value }))}
        sortBy={sortBy}
        onSortChange={handleSortChange}
        resultCount={newsPage.totalElements}
        activeFilterCount={activeFilters.length}
        activeFilters={activeFilters}
        onRemoveActiveFilter={handleRemoveActiveFilter}
        onOpenFilters={() => setMobileFiltersOpen(true)}
      />

      <div className="news-layout-grid">
        <aside className="news-sidebar-column">
          <NewsSidebarFilters
            categoryOptions={categoryOptions.map((option) => ({ value: option, label: formatNewsCategoryLabel(option) }))}
            providerOptions={providerOptions.map((option) => ({ value: option, label: getNewsProviderLabel(option) }))}
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
            <section className="news-feed-header">
              <div className="news-feed-copy">
                <p className="eyebrow">{t("news.publishFlowEyebrow")}</p>
                <h3>{t("news.publishFlowTitle")}</h3>
              </div>
            </section>
          ) : null}

          {loading && items.length === 0 ? <NewsFeedSkeleton /> : null}
          {error ? <ErrorMessage message={error} /> : null}
          {!loading && !error && items.length === 0 ? (
            <EmptyState title={t("news.emptyTitle")} description={t("news.emptyDescription")} />
          ) : null}

          {!loading && !error && items.length > 0 ? (
            <section className="news-grid news-grid-portal">
              {items.map((item) => (
                <NewsCard key={item.id || item.externalId || item.url} item={item} onClick={handleOpen} />
              ))}
            </section>
          ) : null}

          {!loading && !error && items.length > 0 && newsPage.hasNext ? (
            <div className="news-load-more-wrap">
              <button
                type="button"
                className="news-load-more-button"
                onClick={handleLoadMore}
                disabled={loadingMore}
              >
                {loadingMore ? t("news.loadingMore") : t("news.loadMore")}
              </button>
            </div>
          ) : null}
        </div>
      </div>

      <NewsFilterDrawer
        open={mobileFiltersOpen}
        onClose={() => setMobileFiltersOpen(false)}
        categoryOptions={categoryOptions.map((option) => ({ value: option, label: formatNewsCategoryLabel(option) }))}
        providerOptions={providerOptions.map((option) => ({ value: option, label: getNewsProviderLabel(option) }))}
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
