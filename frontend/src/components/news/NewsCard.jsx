import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  buildNewsPlaceholderLabel,
  formatNewsCategoryLabel,
  formatNewsPublishedAt,
  getImportanceLevelKey,
  getImportanceTone,
  getNewsDisclosureTypeLabel,
  getNewsFallbackLogoUrl,
  getNewsLanguageLabel,
  getNewsPreviewText,
  getNewsPrimaryActionLabel,
  getNewsProviderLabel,
  getNewsQualityStatusLabel,
  getNewsSourceName,
  getNewsSourceUrl,
  isKapDisclosure,
  shouldShowSourceCta,
} from "./newsCardUtils";

function resolveThumbnail(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

function resolveAccentLabel(item) {
  return formatNewsCategoryLabel(item?.category) || getNewsProviderLabel(item?.provider) || "Haber";
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
        <small className="news-card-placeholder-note">Finans veri akisi</small>
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
  const languageLabel = getNewsLanguageLabel(item?.language);
  const publishedAtLabel = formatNewsPublishedAt(item?.publishedAt);
  const previewText = getNewsPreviewText(item, t("news.previewMissing"));
  const sourceUrl = getNewsSourceUrl(item);
  const importanceTone = getImportanceTone(item?.importanceScore);
  const qualityLabel = getNewsQualityStatusLabel(item?.qualityStatus);
  const primaryActionLabel = getNewsPrimaryActionLabel(item, t("news.openNews"));
  const disclosureTypeLabel = getNewsDisclosureTypeLabel(item?.disclosureType);
  const importanceLevelLabel = t(`news.importanceLevels.${getImportanceLevelKey(item?.importanceScore)}`);

  useEffect(() => {
    setImageFailed(false);
    setLogoFailed(false);
  }, [thumbnail, item?.provider, item?.sourceUrl, item?.url]);

  const shellClassName = `news-card news-card-shell${kapDisclosure ? " kap" : ""}`;

  if (kapDisclosure) {
    return (
      <article className={shellClassName}>
        <button className="news-card-main" onClick={() => onClick(item)} type="button">
          <div className="news-card-body news-card-body-kap">
            <div className="news-card-meta">
              <span className="news-card-badge provider">{providerLabel}</span>
              <span className="news-card-badge neutral">{disclosureTypeLabel}</span>
              <span className={`news-card-badge importance ${importanceTone}`}>
                {importanceLevelLabel}
              </span>
            </div>

            <h3 className="news-card-title">{item?.title || t("news.titleMissing")}</h3>
            <p className="news-card-summary">{previewText}</p>

            <div className="news-kap-meta-grid">
              <div>
                <span className="news-kap-meta-label">{t("news.kapFields.companyCode")}</span>
                <strong>{item?.relatedSymbol || "-"}</strong>
              </div>
              <div>
                <span className="news-kap-meta-label">{t("news.kapFields.type")}</span>
                <strong>{disclosureTypeLabel}</strong>
              </div>
              <div>
                <span className="news-kap-meta-label">{t("news.kapFields.date")}</span>
                <strong>{publishedAtLabel}</strong>
              </div>
              <div>
                <span className="news-kap-meta-label">{t("news.kapFields.source")}</span>
                <strong>{sourceName}</strong>
              </div>
            </div>

            <div className="news-card-footer">
              <span className="news-card-quality">{qualityLabel}</span>
              <span className="news-card-link">{t("news.openDisclosure")}</span>
            </div>
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

  return (
    <article className={shellClassName}>
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
          <div className="news-card-overlay" />
          <div className="news-card-badges">
            <span className="news-card-badge category">{accentLabel}</span>
            <span className="news-card-badge provider">{providerLabel}</span>
          </div>
        </div>

        <div className="news-card-body">
          <div className="news-card-meta">
            <span className="news-card-provider">{sourceName}</span>
            {languageLabel ? <span className="news-card-dot" /> : null}
            {languageLabel ? <span className="news-meta-badge">{languageLabel}</span> : null}
            <span className="news-card-dot" />
            <time dateTime={item?.publishedAt || ""}>{publishedAtLabel}</time>
          </div>

          <h3 className="news-card-title">{item?.title || t("news.titleMissing")}</h3>
          <p className={`news-card-summary${item?.summary || item?.contentPreview ? "" : " is-fallback"}`}>{previewText}</p>

          <div className="news-card-footer">
            <div className="news-card-footer-meta">
              <span className="news-card-quality">{qualityLabel}</span>
            </div>
            <span className="news-card-link">{primaryActionLabel}</span>
          </div>
        </div>
      </button>

      {shouldShowSourceCta(item) && sourceUrl ? (
        <div className="news-card-secondary-action">
          <a href={sourceUrl} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>
            {t("news.readAtSource")}
          </a>
        </div>
      ) : null}
    </article>
  );
}
