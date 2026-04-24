import { formatDateTime } from "../../utils/formatters";
import { buildNewsPlaceholderLabel } from "./newsCardUtils";

function formatPublishedAtLabel(value) {
  return value ? formatDateTime(value) : "Tarih yok";
}

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

function resolveAccentLabel(item) {
  return item?.category || item?.provider || item?.source || "News";
}

export default function NewsCard({ item, onClick }) {
  const thumbnail = resolveThumbnail(item);
  const accentLabel = resolveAccentLabel(item);
  const providerLabel = item?.provider || "Unknown Provider";
  const sourceLabel = item?.source || "Unknown Source";
  const languageLabel = item?.language ? item.language.toUpperCase() : null;

  return (
    <button className="news-card news-card-shell" onClick={() => onClick(item)} type="button">
      <div className="news-card-media">
        {thumbnail ? (
          <img
            className="news-card-image"
            src={thumbnail}
            alt={item?.title || "News thumbnail"}
            loading="lazy"
          />
        ) : (
          <div className="news-card-placeholder" aria-hidden="true">
            <span>{buildNewsPlaceholderLabel(item)}</span>
          </div>
        )}
        <div className="news-card-overlay" />
        <div className="news-card-badges">
          <span className="news-card-badge">{accentLabel}</span>
          {item?.regionScope ? (
            <span className="news-card-badge secondary">{item.regionScope}</span>
          ) : null}
        </div>
      </div>

      <div className="news-card-body">
        <div className="news-card-meta">
          <span className="news-card-provider">{providerLabel}</span>
          <span className="news-card-dot" />
          <span>{sourceLabel}</span>
          {languageLabel ? <span className="news-card-dot" /> : null}
          {languageLabel ? <span>{languageLabel}</span> : null}
          <span className="news-card-dot" />
          <time dateTime={item?.publishedAt || ""}>{formatPublishedAtLabel(item?.publishedAt)}</time>
        </div>

        <h3 className="news-card-title">{item?.title || "Untitled"}</h3>

        <p className="news-card-summary">
          {item?.summary || "Summary is not available for this item yet."}
        </p>

        <div className="news-card-footer">
          <span className="news-card-link">Open detail</span>
        </div>
      </div>
    </button>
  );
}
