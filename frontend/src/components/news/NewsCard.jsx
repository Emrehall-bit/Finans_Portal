import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsDisclosureTypeLabel,
  getNewsFallbackLogoUrl,
  getNewsPreviewText,
  getNewsCategoryLabel,
  getNewsDisplayTags,
  getNewsProviderLabel,
  getNewsSourceName,
  getNewsSourceUrl,
  isKapDisclosure
} from "./newsCardUtils";

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

function resolveAccentLabel(item) {
  return getNewsCategoryLabel(item?.category) || getNewsProviderLabel(item?.provider) || "Haber";
}

function Placeholder({ item, providerLabel, logoUrl, logoFailed, onLogoError }) {
  return (
    <div className="news-card-placeholder" aria-hidden="true">
      <div className="news-card-placeholder-inner">
        {logoUrl && !logoFailed ? (
          <img
            className="news-card-placeholder-logo"
            src={logoUrl}
            alt={providerLabel}
            loading="lazy"
            onError={onLogoError}
          />
        ) : (
          <span className="news-card-placeholder-mark">{buildNewsPlaceholderLabel(item)}</span>
        )}
        <strong className="news-card-placeholder-provider">{providerLabel}</strong>
        <small className="news-card-placeholder-note">Finans haber akışı</small>
      </div>
    </div>
  );
}

export default function NewsCard({ item, onClick }) {
  const { t } = useTranslation();
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);
  const thumbnail = resolveThumbnail(item);
  const kapDisclosure = isKapDisclosure(item);
  const hasImage = Boolean(thumbnail) && !imageFailed && !kapDisclosure;
  const accentLabel = resolveAccentLabel(item);
  const providerLabel = getNewsProviderLabel(item?.provider);
  const sourceName = getNewsSourceName(item);
  const logoUrl = getNewsFallbackLogoUrl(item);
  const publishedAtLabel = formatNewsPublishedAt(item?.publishedAt);
  const previewText = getNewsPreviewText(item, t("news.previewMissing"));
  const sourceUrl = getNewsSourceUrl(item);
  const disclosureTypeLabel = getNewsDisclosureTypeLabel(item?.disclosureType);
  const displayTags = getNewsDisplayTags(item, t, 3);

  useEffect(() => {
    setImageFailed(false);
    setLogoFailed(false);
  }, [thumbnail, item?.provider, item?.sourceUrl, item?.url]);

  const shellClassName = `news-card news-card-shell${kapDisclosure ? " kap" : ""}`;

  if (kapDisclosure) {
    return (
      <article className={shellClassName}>
        <div className="news-kap-card-inner">
          <div className="news-card-meta">
            <span className="news-card-badge provider">{providerLabel}</span>
            <span className="news-card-badge neutral">{disclosureTypeLabel}</span>
          </div>

          <h3 className="news-card-title">{item?.title || t("news.titleMissing")}</h3>
          <p className="news-card-summary">{previewText}</p>

          <div className="news-kap-chip-row">
            {item?.relatedSymbol ? (
              <>
                <span className="news-kap-chip-symbol">{item.relatedSymbol}</span>
                <span className="news-kap-bullet">•</span>
              </>
            ) : null}
            <time dateTime={item?.publishedAt || ""}>{publishedAtLabel}</time>
            <span className="news-kap-bullet">•</span>
            <span>{sourceName}</span>
          </div>

          {sourceUrl ? (
            <div className="news-kap-cta-row">
              <a className="news-kap-cta-btn" href={sourceUrl} target="_blank" rel="noreferrer">
                {t("news.viewOfficialKap")}
              </a>
            </div>
          ) : null}
        </div>
      </article>
    );
  }

  return (
    <article className={shellClassName}>
      <button className="news-card-main" onClick={() => onClick(item)} type="button">
        <div className="news-card-media">
          {hasImage ? (
            <img
              className="news-card-image"
              src={thumbnail}
              alt={item?.title || "Haber görseli"}
              loading="lazy"
              onError={() => setImageFailed(true)}
            />
          ) : (
            <Placeholder
              item={item}
              providerLabel={providerLabel}
              logoUrl={logoUrl}
              logoFailed={logoFailed}
              onLogoError={() => setLogoFailed(true)}
            />
          )}
        </div>

        <div className="news-card-body">
          <div className="news-card-badges">
            <span className="news-card-badge category">{accentLabel}</span>
          </div>

          <div className="news-card-meta">
            <span className="news-card-provider">{sourceName}</span>
            <span className="news-card-dot" />
            <time dateTime={item?.publishedAt || ""}>{publishedAtLabel}</time>
          </div>

          {displayTags.length > 0 ? (
            <div className="news-tag-chip-row" aria-label={t("news.tagsLabel")}>
              {displayTags.map((tag) => (
                <span key={tag.key} className="news-tag-chip">
                  #{tag.label}
                </span>
              ))}
            </div>
          ) : null}

          <h3 className="news-card-title">{item?.title || t("news.titleMissing")}</h3>
          <p className={`news-card-summary${item?.summary || item?.contentPreview ? "" : " is-fallback"}`}>{previewText}</p>
        </div>
      </button>

      {sourceUrl ? (
        <div className="news-card-secondary-action">
          <a href={sourceUrl} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>
            {t("news.readAtSource")}
          </a>
        </div>
      ) : null}
    </article>
  );
}
