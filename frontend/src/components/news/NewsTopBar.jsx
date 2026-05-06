import { useTranslation } from "react-i18next";

export default function NewsTopBar({
  keyword,
  onKeywordChange,
  sortBy,
  onSortChange,
  resultCount,
  activeFilterCount,
  activeFilters,
  onRemoveActiveFilter,
  onOpenFilters,
}) {
  const { t } = useTranslation();

  return (
    <section className="panel-surface news-topbar-shell">
      <div className="news-topbar-main">
        <label className="news-search-field" htmlFor="news-search-input">
          <span className="news-search-icon" aria-hidden="true">
            +
          </span>
          <input
            id="news-search-input"
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            placeholder={t("news.searchPlaceholder")}
          />
        </label>

        <div className="news-topbar-controls">
          <label className="news-sort-field">
            <span>{t("news.sort")}</span>
            <select value={sortBy} onChange={(event) => onSortChange(event.target.value)}>
              <option value="publishedAt">{t("news.sortNewest")}</option>
              <option value="importanceScore">{t("news.sortImportance")}</option>
            </select>
          </label>

          <button type="button" className="secondary-button news-mobile-filter-trigger" onClick={onOpenFilters}>
            {t("common.filters")}
          </button>
        </div>
      </div>

      <div className="news-topbar-summary">
        <span className="news-toolbar-note">{t("news.listedCount", { count: resultCount })}</span>
        {activeFilterCount > 0 ? (
          <span className="news-active-filter-count">{t("news.activeFilterCount", { count: activeFilterCount })}</span>
        ) : null}
      </div>

      {activeFilters?.length ? (
        <div className="news-active-filter-row" aria-label={t("news.activeFilters")}>
          {activeFilters.map((filter) => (
            <button
              key={`${filter.type}:${filter.value}`}
              type="button"
              className="news-active-filter-chip"
              onClick={() => onRemoveActiveFilter?.(filter)}
            >
              <span>{filter.label}</span>
              <span className="news-active-filter-chip-close" aria-hidden="true">
                x
              </span>
            </button>
          ))}
        </div>
      ) : null}
    </section>
  );
}
