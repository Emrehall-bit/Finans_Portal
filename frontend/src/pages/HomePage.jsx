import { useEffect, useState } from "react";
import { getNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { formatDateTime } from "../utils/formatters";

export default function HomePage() {
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");

        const newsData = await getNews();

        if (!active) {
          return;
        }

        setNews((newsData.content ?? []).slice(0, 4));
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, "Ana sayfa verileri yuklenemedi."));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    load();

    return () => {
      active = false;
    };
  }, []);

  const featuredNews = news.slice(0, 5);

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow="Public Landing"
        title="Finans Portali"
        description="Haber akisi ve finansal ozet herkese acik ilk ekranda."
      />

      {loading ? <LoadingSpinner label="Ana sayfa yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="hero-grid">
            <div className="hero-card panel-surface">
              <div className="hero-copy">
                <p className="eyebrow">Haber Odagi</p>
                <h2>Tek ekranda haberler ve finansal ritim.</h2>
                <p className="page-description">
                  Public ziyaretciler haberleri izler, uye kullanicilar dashboard ve portfoy modullerine devam eder.
                </p>
              </div>

              <div className="hero-chart">
                <EmptyState title="Piyasa grafigi yok" description="Canli fiyat modulu bu surumda devre disi." />
              </div>
            </div>

            <div className="news-rail panel-surface">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">News Flow</p>
                  <h3>One Cikan Basliklar</h3>
                </div>
                <span className="pill">Live</span>
              </div>

              {news.length === 0 ? (
                <EmptyState title="Haber bulunamadi" description="Backend su anda haber dondurmedi." />
              ) : (
                <div className="news-rail-list">
                  {news.map((item) => (
                    <article key={item.id} className="news-rail-item">
                      <div className="news-rail-badge">{(item.provider || item.source || "N").slice(0, 1)}</div>
                      <div>
                        <strong>{item.title || "Basliksiz haber"}</strong>
                        <p>{item.provider || item.source || "-"}</p>
                        <span>{formatDateTime(item.publishedAt)}</span>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="ticker-grid">
            {featuredNews.length === 0 ? (
              <EmptyState title="Ozet yok" description="Su anda one cikan haber bulunamadi." />
            ) : (
              featuredNews.map((item, idx) => (
                <SummaryCard
                  key={`${item.id}-${idx}`}
                  title={(item.title || "Haber").slice(0, 48)}
                  value="News"
                  subtitle={[item.provider, item.source].filter(Boolean).join(" | ") || "-"}
                  trend={formatDateTime(item.publishedAt)}
                  tone={idx % 2 === 0 ? "cool" : "warm"}
                />
              ))
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
