import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatNewsDate } from "../../utils/dateUtils";
import {
  getNewsDateValue,
  resolveKapDisclosureGroup,
  normalizeKapText,
  extractKapSubtitle,
} from "../news/newsCardUtils";

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
            const subtitle = extractKapSubtitle(item.title, item.relatedSymbol);
            const normSubtitle = normalizeKapText(subtitle);
            const normBadge = normalizeKapText(group.label);
            const normSummary = normalizeKapText(item?.summary || item?.contentPreview || "");

            const showSubtitle = group.key !== "diger" && subtitle && normSubtitle !== normBadge;
            const showSummary =
              !showSubtitle &&
              Boolean(item?.summary?.trim() || item?.contentPreview?.trim()) &&
              normSummary !== normBadge;
            const displayText = showSubtitle ? subtitle : showSummary ? (item?.summary?.trim() || item?.contentPreview?.trim()) : null;

            return (
              <article key={item.id ?? item.externalId} className="instrument-kap-card">
                <div className="instrument-kap-header">
                  <div className="instrument-kap-badge-row">
                    <span className="news-card-badge provider">KAP</span>
                    <span className={`kap-type-badge ${group.key}`}>{group.label}</span>
                  </div>
                  <time className="instrument-kap-date" dateTime={getNewsDateValue(item) || ""}>
                    {formatNewsDate(getNewsDateValue(item))}
                  </time>
                </div>

                {displayText ? (
                  <p className="instrument-kap-text">{displayText}</p>
                ) : null}

                {item.url ? (
                  <a href={item.url} target="_blank" rel="noreferrer" className="news-kap-cta-link">
                    KAP&apos;ta Görüntüle ↗
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
