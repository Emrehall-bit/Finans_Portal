import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatNewsDate } from "../../utils/dateUtils";
import { getNewsDateValue, resolveKapDisclosureGroup } from "../news/newsCardUtils";

export default function InstrumentKapNewsList({ loading, error, items }) {
  const { t } = useTranslation();

  return (
    <section className="panel-surface instrument-news-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("instrumentDetail.kapDisclosures.eyebrow")}</p>
          <h3>{t("instrumentDetail.kapDisclosures.title")}</h3>
        </div>
      </div>

      {loading ? <LoadingSpinner label={t("instrumentDetail.kapDisclosures.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error && items.length === 0 ? (
        <EmptyState
          title={t("instrumentDetail.kapDisclosures.emptyTitle")}
          description={t("instrumentDetail.kapDisclosures.emptyDescription")}
        />
      ) : null}

      {!loading && !error && items.length > 0 ? (
        <div className="instrument-news-list">
          {items.map((item) => {
            const group = resolveKapDisclosureGroup(item.title);
            return (
              <article key={item.id ?? item.externalId} className="instrument-news-card news-kap-card-inner">
                <div className="instrument-news-meta">
                  <span className="news-kap-chip-symbol">KAP</span>
                  <span className={`kap-type-badge ${group.key}`}>{group.label}</span>
                  <span>{formatNewsDate(getNewsDateValue(item))}</span>
                </div>
                <h4>{item.title || t("instrumentDetail.kapDisclosures.noTitle")}</h4>
                {item.url ? (
                  <a href={item.url} target="_blank" rel="noreferrer" className="news-kap-cta-btn">
                    KAP&apos;ta Görüntüle
                  </a>
                ) : null}
              </article>
            );
          })}
        </div>
      ) : null}
    </section>
  );
}
