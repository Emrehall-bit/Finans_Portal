import { formatDateTime } from "../../utils/formatters";

function formatPublishedAtLabel(value) {
  return value ? formatDateTime(value) : "Kaynak tarihi alinmadi";
}

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

export default function FeaturedNewsHero({ item, onOpen }) {
  if (!item) {
    return null;
  }

  const thumbnail = resolveThumbnail(item);

  return (
    <button type="button" className="panel-surface news-featured-hero" onClick={() => onOpen(item)}>
      <div className="news-featured-copy">
        <div className="news-detail-meta">
          <span className="news-card-badge">{item.category || item.provider || "Featured"}</span>
          <span className="muted">
            {item.provider || "-"} | {item.source || "-"} | {(item.language || "-").toUpperCase()} | {formatPublishedAtLabel(item.publishedAt)}
          </span>
        </div>
        <p className="eyebrow">Featured Story</p>
        <h2>{item.title || "Untitled"}</h2>
        <p>{item.summary || "Summary is not available for this story yet."}</p>
        <span className="news-card-link">Open detail</span>
      </div>

      <div className="news-featured-media">
        {thumbnail ? (
          <img src={thumbnail} alt={item?.title || "Featured news"} loading="lazy" />
        ) : (
          <div className="news-featured-placeholder" aria-hidden="true">
            <span>{(item.provider || "News").slice(0, 2).toUpperCase()}</span>
          </div>
        )}
      </div>
    </button>
  );
}
