function buildVisiblePages(currentPage, totalPages) {
  if (totalPages <= 0) {
    return [];
  }

  const pages = new Set([0, totalPages - 1, currentPage - 1, currentPage, currentPage + 1]);
  const normalizedPages = [...pages]
    .filter((page) => page >= 0 && page < totalPages)
    .sort((a, b) => a - b);

  const result = [];

  normalizedPages.forEach((page, index) => {
    if (index > 0 && page - normalizedPages[index - 1] > 1) {
      result.push("ellipsis-" + page);
    }
    result.push(page);
  });

  return result;
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
}) {
  const classes = ["news-pagination-card", className].filter(Boolean).join(" ");
  const visiblePages = buildVisiblePages(currentPage, totalPages);

  return (
    <div className={classes}>
      <strong>
        Page {currentPage + 1}
        {totalPages > 0 ? ` / ${totalPages}` : ""}
      </strong>
      <p className="muted">{totalElements} total news items</p>
      {visiblePages.length > 0 ? (
        <div className="news-pagination-pages" aria-label="Pagination pages">
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
                disabled={loading || isActive}
                aria-current={isActive ? "page" : undefined}
              >
                {entry + 1}
              </button>
            );
          })}
        </div>
      ) : null}
      <div className="actions-row">
        <button type="button" onClick={onPrevious} disabled={loading || isFirstPage}>
          Previous
        </button>
        <button type="button" onClick={onNext} disabled={loading || isLastPage}>
          Next
        </button>
      </div>
    </div>
  );
}
