import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { buildMarketDetailPath } from "../api/marketApi";
import AiNewsImpactCard from "../components/ai/AiNewsImpactCard";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import { useNewsDetail, useNewsRelated } from "../hooks/useNewsQueries";
import { formatCurrency, formatPercent } from "../utils/formatters";
import { formatNewsDate } from "../utils/dateUtils";
import {
  buildNewsPlaceholderLabel,
  getNewsDateValue,
  getNewsCategoryLabel,
  getNewsDisclosureTypeLabel,
  getNewsFallbackLogoUrl,
  getNewsPreviewText,
  getNewsProviderLabel,
  getNewsQualityStatusLabel,
  getNewsSourceName,
  getNewsSourceUrl,
  isKapDisclosure,
} from "../components/news/newsCardUtils";

const EMPTY_RELATED = { relatedInstruments: [], relatedNews: [] };

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

  const { data: item = null, isLoading: loading, error: detailError } = useNewsDetail(id);
  const { data: relatedRaw, isLoading: relatedLoading, error: relatedQueryError } = useNewsRelated(id);

  const error = !id ? t("newsDetail.missingId") : detailError ? extractErrorMessage(detailError, t("newsDetail.loadError")) : "";
  const relatedError = relatedQueryError ? extractErrorMessage(relatedQueryError, t("newsDetail.relatedLoadError")) : "";
  const relatedData = {
    relatedInstruments: Array.isArray(relatedRaw?.relatedInstruments) ? relatedRaw.relatedInstruments : [],
    relatedNews: Array.isArray(relatedRaw?.relatedNews) ? relatedRaw.relatedNews : [],
  };

  const kapDisclosure = isKapDisclosure(item);
  const sourceName = getNewsSourceName(item);
  const sourceUrl = getNewsSourceUrl(item);
  const previewText = getNewsPreviewText(item, t("newsDetail.summaryMissing"));
  const qualityLabel = getNewsQualityStatusLabel(item?.qualityStatus);
  const disclosureTypeLabel = getNewsDisclosureTypeLabel(item?.disclosureType);
  const providerLabel = getNewsProviderLabel(item?.provider || item?.source || "-");
  const publishedAtLabel = formatNewsDate(getNewsDateValue(item));

  const detailParagraphs = useMemo(() => {
    const baseContent = item?.summary?.trim() || item?.contentPreview?.trim() || "";
    return baseContent
      .split(/\n{2,}/)
      .map((part) => part.trim())
      .filter(Boolean);
  }, [item?.contentPreview, item?.summary]);

  return (
    <div className="news-page-stack">
      <PageHeader
        eyebrow={kapDisclosure ? t("newsDetail.kapEyebrow") : t("newsDetail.eyebrow")}
        title={item?.title || t("newsDetail.titleFallback")}
        description={sourceName || t("newsDetail.descriptionFallback")}
        actions={
          <Link className="news-detail-back-link" to={backTo}>
            {t("newsDetail.back")}
          </Link>
        }
      />

      {loading ? <LoadingSpinner label={t("newsDetail.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error && !item ? (
        <section className="panel-surface">
          <EmptyState title={t("newsDetail.emptyTitle")} description={t("newsDetail.emptyDescription")} />
        </section>
      ) : null}

      {!loading && !error && item ? (
        <section className="news-detail-hero-grid">
          <div className="news-detail-main-column">
            <article className={`panel-surface news-detail-card news-detail-hero-card${kapDisclosure ? " is-kap" : ""}`}>
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

              <div className="news-detail-topbar">
                <div className="news-detail-meta">
                  <span className="news-card-badge category">{getNewsCategoryLabel(item?.category) || t("newsDetail.defaultCategory")}</span>
                  <span className="news-card-badge provider">{providerLabel}</span>
                  {publishedAtLabel ? <span className="muted">{publishedAtLabel}</span> : null}
                </div>
              </div>

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
            </article>

            <section className={`panel-surface news-detail-card news-detail-content-card${kapDisclosure ? " is-kap" : ""}`}>
              <div className="news-detail-body">
                {detailParagraphs.length > 0 ? (
                  detailParagraphs.map((para, index) =>
                    para.startsWith("## ") ? (
                      <h3 key={index} className="news-detail-subheading">{para.slice(3)}</h3>
                    ) : (
                      <p key={index} className="news-detail-summary">{para}</p>
                    )
                  )
                ) : (
                  <p className="news-detail-summary is-fallback">
                    {kapDisclosure ? t("newsDetail.kapNoSummary") : previewText}
                  </p>
                )}
              </div>

              {kapDisclosure ? (
                <div className="news-detail-actions">
                  <a
                    className="primary-button news-detail-kap-cta"
                    href={item.url}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {t("newsDetail.viewOnKap")}
                  </a>
                </div>
              ) : sourceUrl ? (
                <div className="news-detail-actions">
                  <a className="primary-button news-detail-source-link" href={sourceUrl} target="_blank" rel="noreferrer">
                    {String(item?.qualityStatus || "").toUpperCase() === "SOURCE_LINK_ONLY"
                      ? t("news.readAtSource")
                      : t("newsDetail.openSource")}
                  </a>
                </div>
              ) : null}
          </section>
          </div>

          <aside className="news-detail-side-panel">
            <div className="news-detail-side-sticky">
              <section className="panel-surface news-related-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("newsDetail.relatedAssetsEyebrow")}</p>
                    <h3>{t("newsDetail.relatedAssetsTitle")}</h3>
                    <p className="news-related-panel-copy">{t("newsDetail.relatedAssetsDescription")}</p>
                  </div>
                </div>

                {relatedLoading ? <LoadingSpinner label={t("newsDetail.relatedLoading")} /> : null}
                {!relatedLoading && relatedError ? <ErrorMessage message={relatedError} /> : null}
                {!relatedLoading && !relatedError && relatedData.relatedInstruments.length === 0 ? (
                  <EmptyState
                    title={t("newsDetail.relatedAssetsEmptyTitle")}
                    description={t("newsDetail.relatedAssetsEmptyDescription")}
                  />
                ) : null}

                {!relatedLoading && !relatedError && relatedData.relatedInstruments.length > 0 ? (
                  <div className="news-related-list">
                    {relatedData.relatedInstruments.map((instrument) => {
                      const changeTone = Number(instrument?.changePercent) >= 0 ? "market-up" : "market-down";
                      const confidenceKey = String(instrument?.confidence || "low").toLowerCase();
                      const isMacroTheme =
                        String(instrument?.relationType || "").toUpperCase() === "THEME" &&
                        String(instrument?.confidence || "").toUpperCase() === "CONTEXTUAL";
                      return (
                        <Link
                          key={instrument.symbol}
                          to={buildMarketDetailPath(instrument.symbol, instrument.instrumentType)}
                          className="news-related-card"
                        >
                          <div className="news-related-card-top">
                            <strong className="news-related-symbol">{instrument.symbol}</strong>
                            <div className="news-related-badge-stack">
                              <span className="news-card-badge provider">{instrument.instrumentType || "STOCK"}</span>
                              <span className={`news-card-badge neutral ${String(instrument.relationType || "").toLowerCase()}`}>
                                {isMacroTheme
                                  ? t("newsDetail.confidence.contextual")
                                  : t(`newsDetail.relationTypes.${String(instrument.relationType || "THEME").toLowerCase()}`)}
                              </span>
                              <span className={`news-card-badge importance ${confidenceKey}`}>
                                {t(`newsDetail.confidence.${confidenceKey}`, t("newsDetail.relatedAssetsReasonFallback"))}
                              </span>
                            </div>
                          </div>
                          <p className="news-related-title" title={instrument.name || instrument.symbol}>
                            {instrument.name || instrument.symbol}
                          </p>
                          <p className="news-related-reason" title={instrument.reason || ""}>
                            {instrument.reason || t("newsDetail.relatedAssetsReasonFallback")}
                          </p>
                          {instrument.lastPrice !== null && instrument.lastPrice !== undefined ? (
                            <div className="news-related-market-line">
                              <strong>{formatCurrency(instrument.lastPrice, "TRY")}</strong>
                              <span className={changeTone}>
                                {formatPercent(instrument.changePercent ?? 0)}
                              </span>
                            </div>
                          ) : (
                            <span className="muted">{t("newsDetail.relatedStocksNoPrice")}</span>
                          )}
                        </Link>
                      );
                    })}
                  </div>
                ) : null}
              </section>

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
                  <EmptyState
                    title={t("newsDetail.relatedNewsEmptyTitle")}
                    description={t("newsDetail.relatedNewsEmptyDescription")}
                  />
                ) : null}

              {!relatedLoading && !relatedError && relatedData.relatedNews.length > 0 ? (
                  <div className="news-related-list">
                    {relatedData.relatedNews.map((relatedItem) => (
                      <button
                        key={relatedItem.id}
                        type="button"
                        className="news-related-card news-related-news-card"
                        onClick={() => navigate(`/news/${relatedItem.id}`)}
                      >
                        <div className="news-related-card-top">
                          <span className="news-card-badge category">
                            {getNewsCategoryLabel(relatedItem.category) || t("newsDetail.defaultCategory")}
                          </span>
                        </div>
                        <strong className="news-related-title" title={relatedItem.title || t("news.titleMissing")}>
                          {relatedItem.title || t("news.titleMissing")}
                        </strong>
                        <div className="news-related-meta">
                          <span title={relatedItem.sourceName || "-"}>{relatedItem.sourceName || "-"}</span>
                          <span>{formatNewsDate(getNewsDateValue(relatedItem))}</span>
                        </div>
                      </button>
                    ))}
                  </div>
                ) : null}
              </section>

              {!kapDisclosure ? <AiNewsImpactCard newsId={item.id} /> : null}
            </div>
          </aside>
        </section>
      ) : null}
    </div>
  );
}
