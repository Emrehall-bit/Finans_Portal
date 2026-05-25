import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatNewsDate } from "../../utils/dateUtils";
import { getNewsDateValue, getNewsProviderLabel } from "../news/newsCardUtils";

export default function InstrumentNewsList({ loading, error, items }) {
  const { t } = useTranslation();

  return (
    <section className="panel-surface instrument-news-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("instrumentDetail.newsEyebrow")}</p>
          <h3>{t("instrumentDetail.newsTitle")}</h3>
        </div>
      </div>

      {loading ? <LoadingSpinner label={t("instrumentDetail.newsLoading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error && items.length === 0 ? (
        <EmptyState title={t("instrumentDetail.newsEmptyTitle")} description={t("instrumentDetail.newsEmptyDescription")} />
      ) : null}

      {!loading && !error && items.length > 0 ? (
        <div className="instrument-news-list">
          {items.map((item) => (
            <article key={item.id ?? item.externalId ?? item.url} className="instrument-news-card">
              <div className="instrument-news-meta">
                <span>{getNewsProviderLabel(item.provider || item.source) || t("instrumentDetail.noSource")}</span>
                <span>{formatNewsDate(getNewsDateValue(item))}</span>
              </div>
              <h4>{item.title || t("instrumentDetail.noTitle")}</h4>
              <p>{item.summary || t("instrumentDetail.noSummary")}</p>
              {item.url ? (
                <a href={item.url} target="_blank" rel="noreferrer">
                  {t("instrumentDetail.openNews")}
                </a>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}
