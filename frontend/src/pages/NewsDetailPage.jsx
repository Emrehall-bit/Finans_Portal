import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { getNewsDetail } from "../api/newsApi";
import AiNewsImpactCard from "../components/ai/AiNewsImpactCard";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import { formatDateTime } from "../utils/formatters";
import { getStoredNewsLanguage } from "./newsLanguagePreference";
import { buildNewsPlaceholderLabel, getNewsFallbackLogoUrl, getNewsProviderLabel } from "../components/news/newsCardUtils";

export default function NewsDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const selectedLanguage = getStoredNewsLanguage();
  const [item, setItem] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");
        const detail = await getNewsDetail(id);
        if (active) {
          setItem(detail);
          setImageFailed(false);
          setLogoFailed(false);
        }
      } catch (err) {
        if (active) {
          setItem(null);
          setError(extractErrorMessage(err, t("newsDetail.loadError")));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    if (id) {
      load();
    } else {
      setLoading(false);
      setError(t("newsDetail.missingId"));
    }

    return () => {
      active = false;
    };
  }, [id, t]);

  return (
    <div className="news-page-stack">
      <PageHeader
        eyebrow={t("newsDetail.eyebrow")}
        title={item?.title || t("newsDetail.titleFallback")}
        description={item?.provider || item?.source || t("newsDetail.descriptionFallback")}
        actions={
          <Link className="secondary-button" to="/news">
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

      {!loading && !error && item && item.language && item.language !== selectedLanguage ? (
        <section className="panel-surface">
          <EmptyState
            title={t("newsDetail.languageMismatchTitle")}
            description={t("newsDetail.languageMismatchDescription")}
          />
        </section>
      ) : null}

      {!loading && !error && item && (!item.language || item.language === selectedLanguage) ? (
        <AiNewsImpactCard newsId={item.id} />
      ) : null}

      {!loading && !error && item && (!item.language || item.language === selectedLanguage) ? (
        <section className="panel-surface news-detail-card">
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
                  alt={getNewsProviderLabel(item.provider || item.source || "-")}
                  loading="lazy"
                  onError={() => setLogoFailed(true)}
                />
                <strong className="news-card-placeholder-provider">
                  {getNewsProviderLabel(item.provider || item.source || "-")}
                </strong>
                <small className="news-card-placeholder-note">{t("newsDetail.sourceLogo")}</small>
              </div>
            </div>
          ) : (
            <div className="news-detail-media news-detail-media-fallback">
              <div className="news-card-placeholder-inner">
                <span className="news-card-placeholder-mark">{buildNewsPlaceholderLabel(item)}</span>
                <strong className="news-card-placeholder-provider">
                  {getNewsProviderLabel(item.provider || item.source || "-")}
                </strong>
              </div>
            </div>
          )}

          <div className="news-detail-topbar">
            <div className="news-detail-meta">
              <span className="news-card-badge category">{item.category || t("newsDetail.defaultCategory")}</span>
              <span className="news-card-badge provider">{getNewsProviderLabel(item.provider || item.source || "-")}</span>
              <span className="muted">{formatDateTime(item.publishedAt)}</span>
            </div>
          </div>

          <h3 className="news-detail-title">{item.title || t("newsDetail.titleMissing")}</h3>
          <p className={`news-detail-summary${item.summary ? "" : " is-fallback"}`}>
            {item.summary || t("newsDetail.summaryMissing")}
          </p>

          {item.url ? (
            <div className="news-detail-actions">
              <a className="secondary-button news-detail-link" href={item.url} target="_blank" rel="noreferrer">
                {t("newsDetail.openSource")}
              </a>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
