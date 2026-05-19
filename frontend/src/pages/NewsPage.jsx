import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { getNews, syncNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import PageHeader from "../components/common/PageHeader";
import PaginationControls from "../components/common/PaginationControls";
import NewsCard from "../components/news/NewsCard";
import NewsFilterDrawer from "../components/news/NewsFilterDrawer";
import NewsFeedSkeleton from "../components/news/NewsFeedSkeleton";
import NewsSidebarFilters from "../components/news/NewsSidebarFilters";
import NewsTopBar from "../components/news/NewsTopBar";
import { formatNewsCategoryLabel, getNewsProviderLabel } from "../components/news/newsCardUtils";
import { NEWS_PAGE_SIZE, buildNewsQueryParams } from "./newsPageQueryUtils";

const DEFAULT_PAGE_SIZE = NEWS_PAGE_SIZE;
const REGULAR_PROVIDERS = ["AA_RSS", "CNBC_RSS", "REUTERS_RSS"];
const KAP_PROVIDERS = ["KAP"];
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
  const [newsPage, setNewsPage] = useState(INITIAL_NEWS_PAGE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [filters, setFilters] = useState({ keyword: "", category: "", provider: "" });
  const [appliedFilters, setAppliedFilters] = useState({ keyword: "", category: "", provider: "", language: "" });
  const [sortBy, setSortBy] = useState("publishedAt");
  const [appliedSortBy, setAppliedSortBy] = useState("publishedAt");
  const [currentPage, setCurrentPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  const [selectedCategories, setSelectedCategories] = useState([]);
  const [selectedProviders, setSelectedProviders] = useState([]);
  const [selectedLanguages, setSelectedLanguages] = useState([]);
  const [feedType, setFeedType] = useState("news");
  const [syncingProvider, setSyncingProvider] = useState("");
  const [syncMessage, setSyncMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function loadNews() {
      try {
        setLoading(true);
        setError("");
        const requestParams = buildNewsQueryParams(appliedFilters, currentPage, appliedSortBy, feedType);
        const result = await getNews(requestParams);

        if (!active) {
          return;
        }

        setNewsPage(result);
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
        }
      }
    }

    loadNews();

    return () => {
      active = false;
    };
  }, [appliedFilters, appliedSortBy, currentPage, feedType, refreshKey, t]);

  async function handleSync() {
    try {
      const provider = appliedFilters.provider || (feedType === "kap" ? "KAP" : undefined);
      setSyncingProvider(provider || "ALL");
      setSyncMessage("");
      const response = await syncNews({ provider });
      setCurrentPage(0);
      setRefreshKey((prev) => prev + 1);
      setSyncMessage(buildSyncSummary(response, t));
    } catch (err) {
      setError(extractErrorMessage(err, t("news.syncError")));
    } finally {
      setSyncingProvider("");
    }
  }

  async function handleProviderSync(provider) {
    try {
      setSyncingProvider(provider);
      setSyncMessage("");
      const response = await syncNews({ provider });
      setCurrentPage(0);
      setRefreshKey((prev) => prev + 1);
      setSyncMessage(buildSyncSummary(response, t));
    } catch (err) {
      setError(extractErrorMessage(err, t("news.syncError")));
    } finally {
      setSyncingProvider("");
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

  const providerOptions = useMemo(() => {
    const values = new Set(providerSeed);

    selectedProviders.forEach((provider) => {
      if (provider) {
        values.add(provider);
      }
    });

    return [...values].filter(Boolean).sort((a, b) => a.localeCompare(b));
  }, [providerSeed, selectedProviders]);

  const categoryOptions = useMemo(() => {
    const values = new Map();

    [...(isKapFeed ? [] : KNOWN_CATEGORIES), ...selectedCategories, ...items.map((item) => item?.category)]
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
  }, [isKapFeed, items, selectedCategories]);

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
    setSelectedLanguages((prev) =>
      prev.includes(value) ? prev.filter((l) => l !== value) : [...prev, value]
    );
  }

  function handleResetFilters() {
    setSelectedCategories([]);
    setSelectedProviders([]);
    setSelectedLanguages([]);
    setFilters((prev) => ({ ...prev, category: "", provider: "" }));
  }

  function handleReload() {
    setRefreshKey((prev) => prev + 1);
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

      {isAdmin ? (
        <section className="panel-surface news-admin-sync-bar">
          <div className="news-admin-sync-copy">
            <p className="eyebrow">{t("news.adminSyncEyebrow")}</p>
            <h3>{t("news.adminSyncTitle")}</h3>
            <p>{t("news.adminSyncDescription")}</p>
          </div>
          <div className="actions-row news-admin-sync-actions">
            {["AA_RSS", "CNBC_RSS", "REUTERS_RSS", "KAP"].map((provider) => (
              <button
                key={provider}
                type="button"
                className="secondary-button"
                disabled={loading || syncingProvider === provider}
                onClick={() => handleProviderSync(provider)}
              >
                {syncingProvider === provider
                  ? t("news.syncingProvider", { provider: getNewsProviderLabel(provider) })
                  : t("news.syncProvider", { provider: getNewsProviderLabel(provider) })}
              </button>
            ))}
          </div>
          {syncMessage ? <p className="news-admin-sync-result">{syncMessage}</p> : null}
        </section>
      ) : null}

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

      <section className="news-feed-tabs" aria-label={t("news.feedTabs")}>
        <button
          type="button"
          className={feedType === "news" ? "news-feed-tab active" : "news-feed-tab"}
          onClick={() => handleFeedTypeChange("news")}
        >
          {t("news.tabs.news")}
        </button>
        <button
          type="button"
          className={feedType === "kap" ? "news-feed-tab active" : "news-feed-tab"}
          onClick={() => handleFeedTypeChange("kap")}
        >
          {t("news.tabs.kap")}
        </button>
      </section>

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
                <h3>{isKapFeed ? t("news.kapFlowTitle") : t("news.publishFlowTitle")}</h3>
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

function buildSyncSummary(response, t) {
  if (!response || typeof response !== "object") {
    return t("news.syncCompletedGeneric");
  }

  return t("news.syncCompletedSummary", {
    provider: getNewsProviderLabel(response.provider || "NEWS"),
    fetched: response.fetchedCount ?? 0,
    saved: response.savedCount ?? 0,
    existing: response.existingCount ?? 0,
    invalid: response.invalidCount ?? 0,
  });
}
