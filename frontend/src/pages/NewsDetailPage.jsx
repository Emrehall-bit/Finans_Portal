import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, ExternalLink, Sparkles } from "lucide-react";
import AiNewsImpactCard from "../components/ai/AiNewsImpactCard";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { useNewsDetail, useNewsRelated } from "../hooks/useNewsQueries";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsCategoryLabelI18n,
  getNewsDisclosureTypeLabel,
  getNewsFallbackLogoUrl,
  getNewsPreviewText,
  getNewsProviderLabel,
  getNewsQualityStatusLabel,
  getNewsSourceName,
  getNewsSourceUrl,
  isKapDisclosure,
} from "../components/news/newsCardUtils";

export default function NewsDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const backTo = location.state?.newsListSearch
    ? `/news${location.state.newsListSearch}`
    : "/news";
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);
  const [isAiDrawerOpen, setAiDrawerOpen] = useState(false);

  useEffect(() => {
    if (!isAiDrawerOpen) return undefined;
    function onKey(e) { if (e.key === "Escape") setAiDrawerOpen(false); }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [isAiDrawerOpen]);

  const { data: item = null, isLoading: loading, error: detailError } = useNewsDetail(id);
  const { data: relatedRaw, isLoading: relatedLoading, error: relatedQueryError } = useNewsRelated(id);

  const error = !id ? t("newsDetail.missingId") : detailError ? extractErrorMessage(detailError, t("newsDetail.loadError")) : "";
  const relatedError = relatedQueryError ? extractErrorMessage(relatedQueryError, t("newsDetail.relatedLoadError")) : "";
  const relatedData = {
    relatedNews: Array.isArray(relatedRaw?.relatedNews) ? relatedRaw.relatedNews : [],
  };

  const kapDisclosure = isKapDisclosure(item);
  const sourceName = getNewsSourceName(item);
  const sourceUrl = getNewsSourceUrl(item);
  const previewText = getNewsPreviewText(item, t("newsDetail.summaryMissing"));
  const qualityLabel = getNewsQualityStatusLabel(item?.qualityStatus);
  const disclosureTypeLabel = getNewsDisclosureTypeLabel(item?.disclosureType);
  const providerLabel = getNewsProviderLabel(item?.provider || item?.source || "-");
  const publishedAtLabel = formatNewsPublishedAt(item, "");

  const detailParagraphs = useMemo(() => {
    const baseContent = item?.summary?.trim() || item?.contentPreview?.trim() || "";
    return baseContent
      .split(/\n{2,}/)
      .map((part) => part.trim())
      .filter(Boolean);
  }, [item?.contentPreview, item?.summary]);

  return (
    <div className="news-page-stack">
      {loading ? <LoadingSpinner label={t("newsDetail.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error && !item ? (
        <section className="panel-surface">
          <EmptyState title={t("newsDetail.emptyTitle")} description={t("newsDetail.emptyDescription")} />
        </section>
      ) : null}

      {!loading && !error && item ? (
        <>
        <section className="news-detail-shell">
          <div className="news-detail-hero-grid">
          <div className="news-detail-main-column">
            <article className={`panel-surface news-detail-card news-detail-story-card${kapDisclosure ? " is-kap" : ""}`}>
              <div className="news-detail-article-header">
                <Link className="news-detail-back-link" to={backTo}>
                  <ArrowLeft size={14} aria-hidden="true" />
                  <span>{t("newsDetail.back")}</span>
                </Link>
                <div className="news-detail-header-copy">
                  <div className="news-detail-header-eyebrow">
                    <span className="news-detail-cat-badge">
                      {getNewsCategoryLabelI18n(item?.category, t) || (kapDisclosure ? t("newsDetail.kapEyebrow") : t("newsDetail.defaultCategory"))}
                    </span>
                    <span className="news-detail-eyebrow-sep" aria-hidden="true">•</span>
                    <span className="news-detail-eyebrow-provider">{providerLabel}</span>
                    {sourceName && sourceName !== providerLabel ? (
                      <>
                        <span className="news-detail-eyebrow-sep" aria-hidden="true">•</span>
                        <span className="news-detail-eyebrow-source">{sourceName}</span>
                      </>
                    ) : null}
                    {publishedAtLabel ? (
                      <>
                        <span className="news-detail-eyebrow-sep" aria-hidden="true">•</span>
                        <span className="news-detail-eyebrow-date">{publishedAtLabel}</span>
                      </>
                    ) : null}
                  </div>
                  <h1>{item?.title || t("newsDetail.titleFallback")}</h1>
                </div>
              </div>

              {!kapDisclosure ? (
                <div className="news-detail-media-shell">
                  {item.imageUrl && !imageFailed ? (
                    <div className="news-detail-media">
                      <img
                        className="news-detail-image"
                        src={item.imageUrl}
                        alt={item.title || t("newsDetail.imageAlt")}
                        loading="lazy"
                        onError={() => setImageFailed(true)}
                      />
                    </div>
                  ) : getNewsFallbackLogoUrl(item) && !logoFailed ? (
                    <div className="news-detail-media news-detail-media-fallback">
                      <div className="news-card-placeholder-inner">
                        <img
                          className="news-card-placeholder-logo"
                          src={getNewsFallbackLogoUrl(item)}
                          alt={providerLabel}
                          loading="lazy"
                          onError={() => setLogoFailed(true)}
                        />
                        <strong className="news-card-placeholder-provider">{providerLabel}</strong>
                        <small className="news-card-placeholder-note">{t("newsDetail.sourceLogo")}</small>
                      </div>
                    </div>
                  ) : (
                    <div className="news-detail-media news-detail-media-fallback">
                      <div className="news-card-placeholder-inner">
                        <span className="news-card-placeholder-mark">{buildNewsPlaceholderLabel(item)}</span>
                        <strong className="news-card-placeholder-provider">{providerLabel}</strong>
                        <small className="news-card-placeholder-note">{sourceName}</small>
                      </div>
                    </div>
                  )}
                </div>
              ) : null}

              {kapDisclosure ? (
                <div className="news-detail-kap-grid">
                  <div className="news-detail-fact">
                    <span>{t("news.kapFields.companyCode")}</span>
                    <strong>{item?.relatedSymbol || "-"}</strong>
                  </div>
                  <div className="news-detail-fact">
                    <span>{t("news.kapFields.type")}</span>
                    <strong>{disclosureTypeLabel}</strong>
                  </div>
                  <div className="news-detail-fact">
                    <span>{t("news.kapFields.quality")}</span>
                    <strong>{qualityLabel}</strong>
                  </div>
                  <div className="news-detail-fact">
                    <span>{t("news.kapFields.source")}</span>
                    <strong>{sourceName}</strong>
                  </div>
                </div>
              ) : null}

              <div className="news-detail-body">
                <div className="news-detail-prose">
                  {detailParagraphs.length > 0 ? (
                    detailParagraphs.map((para, index) =>
                      para.startsWith("## ") ? (
                        <h3 key={index} className="news-detail-subheading">{para.slice(3)}</h3>
                      ) : (
                        <p key={index} className={`news-detail-summary${index === 0 ? " is-lead" : ""}`}>{para}</p>
                      )
                    )
                  ) : (
                    <p className="news-detail-summary is-lead is-fallback">
                      {kapDisclosure ? t("newsDetail.kapNoSummary") : previewText}
                    </p>
                  )}
                </div>

                {(kapDisclosure && item.url) || sourceUrl ? (
                  <div className="news-detail-actions">
                    <a
                      className="news-detail-source-link"
                      href={kapDisclosure ? item.url : sourceUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      <span>
                        {kapDisclosure
                          ? t("newsDetail.viewOnKap")
                          : String(item?.qualityStatus || "").toUpperCase() === "SOURCE_LINK_ONLY"
                            ? t("news.readAtSource")
                            : t("newsDetail.openSource")}
                      </span>
                      <ExternalLink size={15} aria-hidden="true" />
                    </a>
                  </div>
                ) : null}
              </div>
            </article>
                </div>

          <aside className="news-detail-side-panel">
            <div className="news-detail-side-sticky">
              <section className="panel-surface news-related-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("newsDetail.relatedNewsEyebrow")}</p>
                    <h3>{t("newsDetail.relatedNewsTitle")}</h3>
                  </div>
                </div>

                {relatedLoading ? <LoadingSpinner label={t("newsDetail.relatedLoading")} /> : null}
                {!relatedLoading && relatedError ? <ErrorMessage message={relatedError} /> : null}
                {!relatedLoading && !relatedError && relatedData.relatedNews.length === 0 ? (
                  <div className="news-related-empty">
                    <strong>{t("newsDetail.relatedNewsEmptyTitle")}</strong>
                    <span>{t("newsDetail.relatedNewsEmptyDescription")}</span>
                  </div>
                ) : null}

              {!relatedLoading && !relatedError && relatedData.relatedNews.length > 0 ? (
                  <div className="news-related-list">
                    {relatedData.relatedNews.map((relatedItem) => (
                      <RelatedCard
                        key={relatedItem.id}
                        relatedItem={relatedItem}
                        onClick={() => navigate(`/news/${relatedItem.id}`)}
                        t={t}
                      />
                    ))}
                  </div>
                ) : null}
              </section>

              {!kapDisclosure ? (
                <button type="button" className="news-ai-drawer-trigger" onClick={() => setAiDrawerOpen(true)}>
                  <div className="news-ai-drawer-trigger-header">
                    <span className="news-ai-drawer-trigger-icon" aria-hidden="true"><Sparkles size={15} /></span>
                    <div>
                      <h3 className="news-ai-drawer-trigger-title">{t("aiCards.newsImpact.startTitle")}</h3>
                      <p className="news-ai-drawer-trigger-sub">{t("aiCards.newsImpact.startSubtitle")}</p>
                    </div>
                  </div>
                  <div className="news-ai-drawer-trigger-cta" aria-hidden="true">
                    {t("aiCards.newsImpact.startBtnLabel")}
                  </div>
                </button>
              ) : null}
            </div>
          </aside>
          </div>
        </section>

        {!kapDisclosure && isAiDrawerOpen ? (
          <div
            className="news-ai-drawer-overlay"
            role="presentation"
            onClick={() => setAiDrawerOpen(false)}
          >
            <div
              className="news-ai-drawer-panel"
              role="dialog"
              aria-modal="true"
              aria-label={t("aiCards.newsImpact.title")}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="news-ai-drawer-header">
                <div className="news-ai-drawer-title-group">
                  <h3 className="news-ai-drawer-title">{t("aiCards.newsImpact.title")}</h3>
                  <p className="news-ai-drawer-sub" title={item.title}>{item.title}</p>
                </div>
                <button
                  type="button"
                  className="news-ai-drawer-close"
                  aria-label={t("common.close")}
                  onClick={() => setAiDrawerOpen(false)}
                >
                  ✕
                </button>
              </div>
              <div className="news-ai-drawer-body">
                <AiNewsImpactCard newsId={item.id} autoLoad />
              </div>
            </div>
          </div>
        ) : null}
        </>
      ) : null}
    </div>
  );
}

function RelatedCard({ relatedItem, onClick, t }) {
  const [imgFailed, setImgFailed] = useState(false);
  const provider = getNewsProviderLabel(relatedItem?.provider || relatedItem?.source || relatedItem?.sourceName || "-");

  return (
    <button type="button" className="news-related-card" onClick={onClick}>
      <div className="news-related-thumb" aria-hidden="true">
        {relatedItem.imageUrl && !imgFailed ? (
          <img
            src={relatedItem.imageUrl}
            alt=""
            loading="lazy"
            onError={() => setImgFailed(true)}
          />
        ) : (
          <span className="news-related-thumb-label">{buildNewsPlaceholderLabel(relatedItem)}</span>
        )}
      </div>
      <div className="news-related-content">
        <div className="news-related-card-top">
          <span className="news-related-source-badge">{provider}</span>
          <span className="news-related-date">{formatNewsPublishedAt(relatedItem, "")}</span>
        </div>
        <strong className="news-related-title" title={relatedItem.title || t("news.titleMissing")}>
          {relatedItem.title || t("news.titleMissing")}
        </strong>
      </div>
    </button>
  );
}
