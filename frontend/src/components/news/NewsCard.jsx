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

function resolveAccentLabel(item) {
  return item?.category || getNewsProviderLabel(item?.provider) || "Haber";
}

export default function NewsCard({ item, onClick }) {
  const thumbnail = resolveThumbnail(item);
  const accentLabel = resolveAccentLabel(item);
  const providerLabel = getNewsProviderLabel(item?.provider);
  const languageLabel = getNewsLanguageLabel(item?.language);
  const publishedAtLabel = formatNewsPublishedAt(item?.publishedAt);
  const summaryText = getNewsSummaryText(item?.summary);

  return (
    <button className="news-card news-card-shell" onClick={() => onClick(item)} type="button">
      <div className="news-card-media">
        {thumbnail ? (
          <img
            className="news-card-image"
            src={thumbnail}
            alt={item?.title || "Haber gorseli"}
            loading="lazy"
          />
        ) : (
          <div className="news-card-placeholder" aria-hidden="true">
            <span>{buildNewsPlaceholderLabel(item)}</span>
          </div>
        )}
        <div className="news-card-overlay" />
        <div className="news-card-badges">
          <span className="news-card-badge category">{accentLabel}</span>
          <span className="news-card-badge provider">{providerLabel}</span>
        </div>
      </div>

      <div className="news-card-body">
        <div className="news-card-meta">
          <span className="news-card-provider">{providerLabel}</span>
          {languageLabel ? <span className="news-card-dot" /> : null}
          {languageLabel ? <span className="news-meta-badge">{languageLabel}</span> : null}
          <span className="news-card-dot" />
          <time dateTime={item?.publishedAt || ""}>{publishedAtLabel}</time>
        </div>

        <h3 className="news-card-title">{item?.title || "Başlık bulunmuyor"}</h3>

        <p className={`news-card-summary${item?.summary ? "" : " is-fallback"}`}>
          {summaryText}
        </p>

        <div className="news-card-footer">
          <span className="news-card-link">Detayı görüntüle</span>
        </div>
      </div>
    </button>
  );
}
