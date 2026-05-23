import { useTranslation } from "react-i18next";

function buildVisiblePages(currentPage, totalPages) {
  if (totalPages <= 0) return [];

  const pages = new Set([0, totalPages - 1, currentPage - 1, currentPage, currentPage + 1]);
  const normalizedPages = [...pages]
    .filter((page) => page >= 0 && page < totalPages)
    .sort((a, b) => a - b);

  const result = [];

  normalizedPages.forEach((page, index) => {
    if (index > 0 && page - normalizedPages[index - 1] > 1) {
      result.push(`ellipsis-${page}`);
    }
    result.push(page);
  });

  return result;
}

function PageButtons({ visiblePages, currentPage, loading, onPageChange, t }) {
  if (visiblePages.length === 0) return null;

  return (
    <div className="news-pagination-pages" aria-label={t("pagination.pagesAria")}>
      {visiblePages.map((entry) => {
        if (typeof entry !== "number") {
          return (
            <span key={entry} className="news-pagination-ellipsis" aria-hidden="true">
              ...
            </span>
          );
        }

        const isActive = entry === currentPage;

        return (
          <button
            key={entry}
            type="button"
            className={["news-page-button", isActive ? "active" : ""].filter(Boolean).join(" ")}
            onClick={() => onPageChange?.(entry)}
            disabled={loading}
            aria-current={isActive ? "page" : undefined}
          >
            {entry + 1}
          </button>
        );
      })}
    </div>
  );
}

export default function PaginationControls({
  currentPage,
  totalPages,
  totalElements,
  loading = false,
  isFirstPage = false,
  isLastPage = false,
  onPrevious,
  onNext,
  onPageChange,
  className = "",
  variant = "default",
}) {
  const { t } = useTranslation();
  const classes = ["news-pagination-card", `is-${variant}`, className].filter(Boolean).join(" ");
  const visiblePages = buildVisiblePages(currentPage, totalPages);
  const pageSummary = `${t("pagination.page")} ${currentPage + 1}${totalPages > 0 ? ` / ${totalPages}` : ""}`;
  const totalSummary = t("pagination.totalNews", { count: totalElements });

  if (variant === "news-top") {
    return (
      <div className={classes}>
        <div className="news-pagination-top-inline-copy">
          <span>{totalSummary}</span>
          <span aria-hidden="true">•</span>
          <span>{pageSummary}</span>
        </div>

        <div className="news-pagination-top-actions">
          <button
            type="button"
            className="news-pagination-nav-btn"
            onClick={onPrevious}
            disabled={loading || isFirstPage}
          >
            ← {t("pagination.previous")}
          </button>
          <button
            type="button"
            className="news-pagination-nav-btn"
            onClick={onNext}
            disabled={loading || isLastPage}
          >
            {t("pagination.next")} →
          </button>
        </div>
      </div>
    );
  }

  if (variant === "news-bottom") {
    return (
      <div className={classes}>
        <div className="news-pagination-main">
          <button
            type="button"
            className="news-pagination-nav-btn"
            onClick={onPrevious}
            disabled={loading || isFirstPage}
          >
            ← {t("pagination.previous")}
          </button>

          <PageButtons
            visiblePages={visiblePages}
            currentPage={currentPage}
            loading={loading}
            onPageChange={onPageChange}
            t={t}
          />

          <button
            type="button"
            className="news-pagination-nav-btn"
            onClick={onNext}
            disabled={loading || isLastPage}
          >
            {t("pagination.next")} →
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={classes}>
      <div className="news-pagination-summary">
        <strong>{pageSummary}</strong>
        <p className="muted">{totalSummary}</p>
      </div>
      <div className="news-pagination-main">
        <button
          type="button"
          className="news-pagination-nav-btn"
          onClick={onPrevious}
          disabled={loading || isFirstPage}
        >
          {t("pagination.previous")}
        </button>

        <PageButtons
          visiblePages={visiblePages}
          currentPage={currentPage}
          loading={loading}
          onPageChange={onPageChange}
          t={t}
        />

        <button
          type="button"
          className="news-pagination-nav-btn"
          onClick={onNext}
          disabled={loading || isLastPage}
        >
          {t("pagination.next")}
        </button>
      </div>

      <div className="news-pagination-mobile-actions">
        <button
          type="button"
          className="news-pagination-nav-btn"
          onClick={onPrevious}
          disabled={loading || isFirstPage}
        >
          {t("pagination.previous")}
        </button>
        <button
          type="button"
          className="news-pagination-nav-btn"
          onClick={onNext}
          disabled={loading || isLastPage}
        >
          {t("pagination.next")}
        </button>
      </div>
    </div>
  );
}
