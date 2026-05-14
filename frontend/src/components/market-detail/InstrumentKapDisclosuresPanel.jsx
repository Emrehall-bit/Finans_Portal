import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import PaginationControls from "../common/PaginationControls";

const DISCLOSURE_TYPE_LABELS = {
  FINANCIAL: "Finansal",
  SPECIAL: "Özel Durum",
  GENERAL: "Genel",
};

function formatDate(value) {
  if (!value) return "-";
  try {
    return new Date(value).toLocaleDateString("tr-TR", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return String(value);
  }
}

export default function InstrumentKapDisclosuresPanel({
  loading,
  error,
  page,
  onPageChange,
}) {
  const { t } = useTranslation();

  const items = page?.content ?? [];
  const totalElements = page?.totalElements ?? 0;
  const totalPages = page?.totalPages ?? 0;
  const currentPage = page?.number ?? 0;
  const isFirst = page?.first ?? true;
  const isLast = page?.last ?? true;

  return (
    <section className="panel-surface instrument-kap-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("instrumentDetail.kapDisclosures.eyebrow")}</p>
          <h3>{t("instrumentDetail.kapDisclosures.title")}</h3>
        </div>
      </div>

      {loading && <LoadingSpinner label={t("instrumentDetail.kapDisclosures.loading")} />}
      {!loading && error && <ErrorMessage message={error} />}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          title={t("instrumentDetail.kapDisclosures.emptyTitle")}
          description={t("instrumentDetail.kapDisclosures.emptyDescription")}
        />
      )}

      {!loading && !error && items.length > 0 && (
        <>
          <ul className="kap-disclosure-list" style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {items.map((item, idx) => (
              <li key={item.id ?? idx} className="kap-disclosure-item">
                <div className="kap-disclosure-meta">
                  <span className="signal-pill" style={{ fontSize: "0.7rem" }}>
                    {DISCLOSURE_TYPE_LABELS[item.disclosureType] ?? item.disclosureType ?? "-"}
                  </span>
                  <span className="muted" style={{ fontSize: "0.8rem" }}>
                    {formatDate(item.publishedAt)}
                  </span>
                </div>

                <p className="kap-disclosure-title">
                  {item.kapUrl ? (
                    <a
                      href={item.kapUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="kap-disclosure-link"
                    >
                      {item.title ?? t("instrumentDetail.kapDisclosures.noTitle")}
                    </a>
                  ) : (
                    item.title ?? t("instrumentDetail.kapDisclosures.noTitle")
                  )}
                </p>

                {item.summary && (
                  <p className="kap-disclosure-summary muted">{item.summary}</p>
                )}
              </li>
            ))}
          </ul>

          {totalPages > 1 && (
            <PaginationControls
              currentPage={currentPage}
              totalPages={totalPages}
              totalElements={totalElements}
              loading={loading}
              isFirstPage={isFirst}
              isLastPage={isLast}
              onPrevious={() => onPageChange(currentPage - 1)}
              onNext={() => onPageChange(currentPage + 1)}
              onPageChange={onPageChange}
            />
          )}
        </>
      )}
    </section>
  );
}
