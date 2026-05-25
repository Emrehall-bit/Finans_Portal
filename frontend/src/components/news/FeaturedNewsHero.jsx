import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsFallbackLogoUrl,
  getNewsLanguageLabel,
  getNewsProviderLabel,
  getNewsSummaryText,
} from "./newsCardUtils";

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

export default function FeaturedNewsHero({ item, onOpen }) {
  const { t } = useTranslation();
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);

  useEffect(() => {
    setImageFailed(false);
    setLogoFailed(false);
  }, [item?.imageUrl, item?.thumbnailUrl, item?.image, item?.provider, item?.url]);

  if (!item) {
    return null;
  }

  const thumbnail = resolveThumbnail(item);
  const hasImage = Boolean(thumbnail) && !imageFailed;
  const providerLabel = getNewsProviderLabel(item.provider);
  const logoUrl = getNewsFallbackLogoUrl(item);
  const languageLabel = getNewsLanguageLabel(item.language);
  const summaryText = getNewsSummaryText(item.summary);

  return (
    <button type="button" className="panel-surface news-featured-hero" onClick={() => onOpen(item)}>
      <div className="news-featured-copy">
        <div className="news-detail-meta">
          <span className="news-card-badge category">{item.category || "Gundem"}</span>
          <span className="news-card-badge provider">{providerLabel}</span>
        </div>
        <div className="news-card-meta featured">
          <span className="news-card-provider">{providerLabel}</span>
          {languageLabel ? <span className="news-card-dot" /> : null}
          {languageLabel ? <span className="news-meta-badge">{languageLabel}</span> : null}
          <span className="news-card-dot" />
          <span>{formatNewsPublishedAt(item)}</span>
        </div>
        <p className="eyebrow">One Cikan Haber</p>
        <h2>{item.title || "Baslik bulunmuyor"}</h2>
        <p className={!item.summary ? "is-fallback" : undefined}>{summaryText}</p>
        <span className="news-card-link">{t("news.openNews")}</span>
      </div>

      <div className="news-featured-media">
        {hasImage ? (
          <img
            src={thumbnail}
            alt={item?.title || "One cikan haber"}
            loading="lazy"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <div className="news-featured-placeholder" aria-hidden="true">
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
      </div>
    </button>
  );
}
