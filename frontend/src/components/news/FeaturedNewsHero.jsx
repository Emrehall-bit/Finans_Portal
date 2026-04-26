import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsLanguageLabel,
  getNewsProviderLabel,
  getNewsSummaryText,
} from "./newsCardUtils";

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

export default function FeaturedNewsHero({ item, onOpen }) {
  if (!item) {
    return null;
  }

  const thumbnail = resolveThumbnail(item);
  const providerLabel = getNewsProviderLabel(item.provider);
  const languageLabel = getNewsLanguageLabel(item.language);
  const summaryText = getNewsSummaryText(item.summary);

  return (
    <button type="button" className="panel-surface news-featured-hero" onClick={() => onOpen(item)}>
      <div className="news-featured-copy">
        <div className="news-detail-meta">
          <span className="news-card-badge category">{item.category || "Gündem"}</span>
          <span className="news-card-badge provider">{providerLabel}</span>
        </div>
        <div className="news-card-meta featured">
          <span className="news-card-provider">{providerLabel}</span>
          {languageLabel ? <span className="news-card-dot" /> : null}
          {languageLabel ? <span className="news-meta-badge">{languageLabel}</span> : null}
          <span className="news-card-dot" />
          <span>{formatNewsPublishedAt(item.publishedAt)}</span>
        </div>
        <p className="eyebrow">Öne Çıkan Haber</p>
        <h2>{item.title || "Başlık bulunmuyor"}</h2>
        <p className={!item.summary ? "is-fallback" : undefined}>{summaryText}</p>
        <span className="news-card-link">Haberi aç</span>
      </div>

      <div className="news-featured-media">
        {thumbnail ? (
          <img src={thumbnail} alt={item?.title || "Öne çıkan haber"} loading="lazy" />
        ) : (
          <div className="news-featured-placeholder" aria-hidden="true">
            <span>{buildNewsPlaceholderLabel(item)}</span>
          </div>
        )}
      </div>
    </button>
  );
}
