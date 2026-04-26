import { useEffect, useState } from "react";
import { getMarketQuotes } from "../api/marketApi";
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
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");

        const [newsData, quoteData] = await Promise.all([getNews(), getMarketQuotes()]);

        if (!active) {
          return;
        }

        setNews((newsData.content ?? []).slice(0, 4));
        setQuotes((quoteData ?? []).slice(0, 5));
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, "Ana sayfa verileri yüklenemedi."));
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
        title="Finans Portalı"
        description="Haber akışı ve finansal özet herkese açık ilk ekranda."
      />

      {loading ? <LoadingSpinner label="Ana sayfa yükleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="hero-grid">
            <div className="hero-card panel-surface">
              <div className="hero-copy">
                <p className="eyebrow">Haber Odağı</p>
                <h2>Tek ekranda haberler ve finansal ritim.</h2>
                <p className="page-description">
                  Açık ziyaretçiler haberleri izler, üyeler dashboard ve portföy modüllerine devam eder.
                </p>
              </div>

              <div className="hero-chart">
                {quotes.length === 0 ? (
                  <EmptyState title="Piyasa grafiği yok" description="Canlı fiyat modülü şu anda veri döndürmüyor." />
                ) : (
                  <div className="market-board compact">
                    {quotes.map((item) => (
                      <article key={item.symbol} className="market-board-row">
                        <div>
                          <strong>{item.symbol}</strong>
                          <p>{item.source || "-"}</p>
                        </div>
                        <div className="market-board-metric">
                          <strong>{item.price ?? "-"}</strong>
                          <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                            {formatMarketChange(item.changeRate)}
                          </span>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="news-rail panel-surface">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Haber Akışı</p>
                  <h3>Öne Çıkan Başlıklar</h3>
                </div>
                <span className="pill">Live</span>
              </div>

              {news.length === 0 ? (
                <EmptyState title="Haber bulunamadı" description="Backend şu anda haber döndürmedi." />
              ) : (
                <div className="news-rail-list">
                  {news.map((item) => (
                    <article key={item.id} className="news-rail-item">
                      <div className="news-rail-badge">{(item.provider || item.source || "N").slice(0, 1)}</div>
                      <div>
                        <strong>{item.title || "Başlıksız haber"}</strong>
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
            {quotes.length > 0 ? (
              quotes.map((item, idx) => (
                <SummaryCard
                  key={`${item.symbol}-${idx}`}
                  title={item.displayName || item.symbol}
                  value={`${item.price ?? "-"} ${item.currency || ""}`.trim()}
                  subtitle={`${item.symbol} | ${item.source || "-"}`}
                  trend={formatMarketChange(item.changeRate)}
                  tone={Number(item.changeRate) >= 0 ? "cool" : "warm"}
                />
              ))
            ) : featuredNews.length === 0 ? (
              <EmptyState title="Özet yok" description="Şu anda öne çıkan haber bulunamadı." />
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

function formatMarketChange(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }

  return `${numeric >= 0 ? "+" : ""}${numeric.toFixed(2)}%`;
}
