import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getNewsDetail } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import { formatDateTime } from "../utils/formatters";
import { getStoredNewsLanguage } from "./newsLanguagePreference";
import { buildNewsPlaceholderLabel, getNewsFallbackLogoUrl, getNewsProviderLabel } from "../components/news/newsCardUtils";

export default function NewsDetailPage() {
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
          setError(extractErrorMessage(err, "Haber detayi yuklenemedi."));
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
      setError("Haber numarasi bulunamadi.");
    }

    return () => {
      active = false;
    };
  }, [id]);

  return (
    <div className="news-page-stack">
      <PageHeader
        eyebrow="Haber Detayi"
        title={item?.title || "Haber"}
        description={item?.provider || item?.source || "Finans gundemi"}
        actions={
          <Link className="secondary-button" to="/news">
            Haberlere don
          </Link>
        }
      />

      {loading ? <LoadingSpinner label="Haber detayi yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error && !item ? (
        <section className="panel-surface">
          <EmptyState title="Haber bulunamadi" description="Kayit silinmis veya erisilemiyor olabilir." />
        </section>
      ) : null}

      {!loading && !error && item && item.language && item.language !== selectedLanguage ? (
        <section className="panel-surface">
          <EmptyState
            title="Haber secili dilde goruntulenemiyor"
            description="Bu kayit farkli dilde tutuluyor. Haber listesine donup dil filtresini degistirebilirsin."
          />
        </section>
      ) : null}

      {!loading && !error && item && (!item.language || item.language === selectedLanguage) ? (
        <section className="panel-surface news-detail-card">
          {item.imageUrl && !imageFailed ? (
            <div className="news-detail-media">
              <img
                className="news-detail-image"
                src={item.imageUrl}
                alt={item.title || "Haber gorseli"}
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
                <small className="news-card-placeholder-note">Kaynak logosu</small>
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
              <span className="news-card-badge category">{item.category || "Gundem"}</span>
              <span className="news-card-badge provider">{getNewsProviderLabel(item.provider || item.source || "-")}</span>
              <span className="muted">{formatDateTime(item.publishedAt)}</span>
            </div>
          </div>

          <h3 className="news-detail-title">{item.title || "Baslik bulunamiyor"}</h3>
          <p className={`news-detail-summary${item.summary ? "" : " is-fallback"}`}>
            {item.summary || "Bu haber icin kaynak tarafi ozet bilgisi saglamadi."}
          </p>

          {item.url ? (
            <div className="news-detail-actions">
              <a className="secondary-button news-detail-link" href={item.url} target="_blank" rel="noreferrer">
                Kaynakta ac
              </a>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
