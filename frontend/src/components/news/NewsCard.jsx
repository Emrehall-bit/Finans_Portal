import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsDisplayTags,
  getNewsFallbackLogoUrl,
  getNewsPreviewText,
  getNewsProviderLabel,
  getNewsSourceName,
  getNewsSourceUrl,
  isKapDisclosure,
  resolveKapDisclosureGroup,
  normalizeKapText,
  extractKapSubtitle,
} from "./newsCardUtils";

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
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
        <small className="news-card-placeholder-note">Finans haber akisi</small>
      </div>
    </div>
  );
}

function NewsCardContent({ item, onClick, assetKey }) {
  const { t } = useTranslation();
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);

  const thumbnail = resolveThumbnail(item);
  const kapDisclosure = isKapDisclosure(item);
  const hasImage = Boolean(thumbnail) && !imageFailed && !kapDisclosure;
  const providerLabel = getNewsProviderLabel(item?.provider);
  const sourceName = getNewsSourceName(item);
  const logoUrl = getNewsFallbackLogoUrl(item);
  const publishedAtLabel = formatNewsPublishedAt(item);
  const previewText = getNewsPreviewText(item, t("news.previewMissing"));
  const sourceUrl = getNewsSourceUrl(item);
  const kapGroup = resolveKapDisclosureGroup(item?.title);
  const displayTags = getNewsDisplayTags(item, t, 2);

  const shellClassName = `news-card news-card-shell${kapDisclosure ? " kap" : ""}`;

  if (kapDisclosure) {
    const subtitle = extractKapSubtitle(item?.title, item?.relatedSymbol);
    const normSubtitle = normalizeKapText(subtitle);
    const normBadge = normalizeKapText(kapGroup.label);
    const normSummary = normalizeKapText(item?.summary || item?.contentPreview || "");

    const showSubtitle = kapGroup.key !== "diger" && subtitle && normSubtitle !== normBadge;
    const showSummary =
      !showSubtitle &&
      Boolean(item?.summary?.trim() || item?.contentPreview?.trim()) &&
      normSummary !== normBadge;

    return (
      <article className={shellClassName} key={assetKey}>
        <div className="news-kap-card-inner">
          <div className="news-kap-header">
            <div className="news-kap-badge-row">
              <span className="news-card-badge provider">{providerLabel}</span>
              <span className={`kap-type-badge ${kapGroup.key}`}>{kapGroup.label}</span>
            </div>
            <time className="news-kap-date" dateTime={item?.publishedAt || item?.createdAt || item?.updatedAt || ""}>
              {publishedAtLabel}
            </time>
          </div>

          {item?.relatedSymbol ? (
            <strong className="news-kap-symbol">{item.relatedSymbol}</strong>
          ) : null}

          {showSubtitle ? (
            <p className="news-kap-subtitle">{subtitle}</p>
          ) : null}

          {showSummary ? (
            <p className="news-card-summary">{previewText}</p>
          ) : null}

          {sourceUrl ? (
            <a className="news-kap-cta-link" href={sourceUrl} target="_blank" rel="noreferrer">
              KAP&apos;ta Görüntüle ↗
            </a>
          ) : null}
        </div>
      </article>
    );
  }

  return (
    <article className={shellClassName} key={assetKey}>
      <button className="news-card-main" onClick={() => onClick(item)} type="button">
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
          <div className="news-card-meta">
            <span className="news-card-provider">{sourceName}</span>
            <span className="news-card-dot" />
            <time dateTime={item?.publishedAt || item?.createdAt || item?.updatedAt || ""}>{publishedAtLabel}</time>
          </div>

          {displayTags.length > 0 ? (
            <div className="news-tag-chip-row" aria-label={t("news.tagsLabel")}>
              {displayTags.map((tag) => (
                <span key={tag.key} className="news-tag-chip">
                  {tag.label}
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
            {t("news.readAtSource")} →
          </a>
        </div>
      ) : null}
    </article>
  );
}

export default function NewsCard({ item, onClick }) {
  const assetKey = [
    item?.id,
    item?.thumbnailUrl,
    item?.imageUrl,
    item?.image,
    item?.provider,
    item?.sourceUrl,
    item?.url,
  ].join("|");

  return <NewsCardContent key={assetKey} item={item} onClick={onClick} assetKey={assetKey} />;
}
