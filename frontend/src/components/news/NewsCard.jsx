import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Bookmark } from "lucide-react";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
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

function Placeholder({ item, providerLabel, logoUrl, logoFailed, onLogoError, logoOnly = false }) {
  if (logoOnly && logoUrl && !logoFailed) {
    return (
      <div className="news-card-placeholder" aria-hidden="true">
        <img
          className="news-card-placeholder-logo news-card-placeholder-logo--large"
          src={logoUrl}
          alt={providerLabel}
          loading="lazy"
          onError={onLogoError}
        />
      </div>
    );
  }

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

function FavoriteButton({ active, busy, onToggle, t, size = 20 }) {
  if (!onToggle) return null;

  return (
    <button
      type="button"
      className={`news-card-favorite-btn${active ? " is-active" : ""}`}
      onClick={(event) => {
        event.stopPropagation();
        onToggle(event);
      }}
      disabled={busy}
      aria-pressed={active}
      aria-label={active ? t("news.removeFavorite") : t("news.addFavorite")}
      title={active ? t("news.removeFavorite") : t("news.addFavorite")}
    >
      <Bookmark
        className="news-card-favorite-icon"
        size={size}
        strokeWidth={1.9}
        fill={active ? "currentColor" : "none"}
        aria-hidden="true"
      />
    </button>
  );
}

function NewsCardContent({ item, onClick, assetKey, isFavorite, favoriteBusy, onFavoriteToggle }) {
  const { t } = useTranslation();
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);

  const thumbnail = resolveThumbnail(item);
  const kapDisclosure = isKapDisclosure(item);
  const isSystemGenerated = item?.provider?.toUpperCase() === "SYSTEM_GENERATED";
  const hasImage = Boolean(thumbnail) && !imageFailed && !kapDisclosure && !isSystemGenerated;
  const providerLabel = getNewsProviderLabel(item?.provider);
  const sourceName = getNewsSourceName(item);
  const logoUrl = getNewsFallbackLogoUrl(item);
  const publishedAtLabel = formatNewsPublishedAt(item);
  const previewText = getNewsPreviewText(item, t("news.previewMissing"));
  const sourceUrl = getNewsSourceUrl(item);
  const kapGroup = resolveKapDisclosureGroup(item?.title);

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

            <FavoriteButton
              active={isFavorite}
              busy={favoriteBusy}
              onToggle={onFavoriteToggle ? () => onFavoriteToggle(item) : null}
              t={t}
              size={17}
            />
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
              {t("news.viewOfficialKap")} &rarr;
            </a>
          ) : null}
        </div>
      </article>
    );
  }

  return (
    <article className={shellClassName} key={assetKey}>
      <FavoriteButton
        active={isFavorite}
        busy={favoriteBusy}
        onToggle={onFavoriteToggle ? () => onFavoriteToggle(item) : null}
        t={t}
        size={21}
      />
      <button className="news-card-main" onClick={() => onClick(item)} type="button">
        <div className="news-card-media">
          {hasImage ? (
            <img
              className="news-card-image"
              src={thumbnail}
              alt={item?.title || "Haber gorseli"}
              loading="lazy"
              width="640"
              height="360"
              onError={() => setImageFailed(true)}
            />
          ) : (
            <Placeholder
              item={item}
              providerLabel={providerLabel}
              logoUrl={logoUrl}
              logoFailed={logoFailed}
              onLogoError={() => setLogoFailed(true)}
              logoOnly={isSystemGenerated}
            />
          )}
        </div>

        <div className="news-card-body">
          <div className="news-card-meta">
            <span className="news-card-provider">{sourceName}</span>
            {isSystemGenerated ? (
              <span className="news-system-badge">Fake</span>
            ) : null}
            <span className="news-card-dot" />
            <time dateTime={item?.publishedAt || item?.createdAt || item?.updatedAt || ""}>{publishedAtLabel}</time>
          </div>

          <h3 className="news-card-title">{item?.title || t("news.titleMissing")}</h3>
          <p className={`news-card-summary${item?.summary || item?.contentPreview ? "" : " is-fallback"}`}>{previewText}</p>
        </div>
      </button>

      {sourceUrl ? (
        <div className="news-card-secondary-action">
          <a href={sourceUrl} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>
            {t("news.readAtSource")} &rarr;
          </a>
        </div>
      ) : null}
    </article>
  );
}

export default function NewsCard({ item, onClick, isFavorite = false, favoriteBusy = false, onFavoriteToggle }) {
  const assetKey = [
    item?.id,
    item?.thumbnailUrl,
    item?.imageUrl,
    item?.image,
    item?.provider,
    item?.sourceUrl,
    item?.url,
  ].join("|");

  return (
    <NewsCardContent
      key={assetKey}
      item={item}
      onClick={onClick}
      assetKey={assetKey}
      isFavorite={isFavorite}
      favoriteBusy={favoriteBusy}
      onFavoriteToggle={onFavoriteToggle}
    />
  );
}
