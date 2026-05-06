import { useEffect, useState } from "react";
import {
  buildNewsPlaceholderLabel,
  formatNewsCategoryLabel,
  formatNewsPublishedAt,
  getNewsFallbackLogoUrl,
  getNewsLanguageLabel,
  getNewsProviderLabel,
  getNewsSummaryText,
} from "./newsCardUtils";

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

function resolveAccentLabel(item) {
  return formatNewsCategoryLabel(item?.category) || getNewsProviderLabel(item?.provider) || "Haber";
}

export default function NewsCard({ item, onClick }) {
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);
  const thumbnail = resolveThumbnail(item);
  const hasImage = Boolean(thumbnail) && !imageFailed;
  const accentLabel = resolveAccentLabel(item);
  const providerLabel = getNewsProviderLabel(item?.provider);
  const logoUrl = getNewsFallbackLogoUrl(item);
  const languageLabel = getNewsLanguageLabel(item?.language);
  const publishedAtLabel = formatNewsPublishedAt(item?.publishedAt);
  const summaryText = getNewsSummaryText(item?.summary);

  useEffect(() => {
    setImageFailed(false);
    setLogoFailed(false);
  }, [thumbnail, item?.provider, item?.url]);

  return (
    <button className="news-card news-card-shell" onClick={() => onClick(item)} type="button">
      <div className="news-card-media">
        {hasImage ? (
          <img
            className="news-card-image"
            src={thumbnail}
            alt={item?.title || "Haber gorseli"}
            loading="lazy"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <div className="news-card-placeholder" aria-hidden="true">
            <div className="news-card-placeholder-inner">
              {logoUrl && !logoFailed ? (
                <img
                  className="news-card-placeholder-logo"
                  src={logoUrl}
                  alt={providerLabel}
                  loading="lazy"
                  onError={() => setLogoFailed(true)}
                />
              ) : (
                <span className="news-card-placeholder-mark">{buildNewsPlaceholderLabel(item)}</span>
              )}
              <strong className="news-card-placeholder-provider">{providerLabel}</strong>
              <small className="news-card-placeholder-note">Kaynak logosu</small>
            </div>
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

        <h3 className="news-card-title">{item?.title || "Baslik bulunmuyor"}</h3>
        <p className={`news-card-summary${item?.summary ? "" : " is-fallback"}`}>{summaryText}</p>

        <div className="news-card-footer">
          <span className="news-card-link">Haberi ac</span>
        </div>
      </div>
    </button>
  );
}
