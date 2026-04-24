import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { getMarketQuotes } from "../api/marketApi";
import { getNews } from "../api/newsApi";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { extractErrorMessage } from "../api/responseUtils";
import { formatDateTime } from "../utils/formatters";

const links = [
  { title: "News", description: "Provider bazli haber takibi", to: "/news" },
  { title: "Portfolio", description: "Pozisyonlar ve dagilim", to: "/portfolio" },
  { title: "Watchlist", description: "Izleme listesi", to: "/watchlist" },
  { title: "Alerts", description: "Fiyat tetikleyicileri", to: "/alerts" },
];

export default function DashboardPage() {
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
        setQuotes((quoteData ?? []).slice(0, 6));
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, "Dashboard data could not be loaded."));
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

  const positiveCount = quotes.filter((item) => Number(item.changeRate) > 0).length;
  const quoteLeaders = quotes.slice(0, 3);

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow="Private Dashboard"
        title="Kontrol Merkezi"
        description="Portfoy ve haber akisinin tek bakista okunabildigi korumali alan."
      />

      {loading ? <LoadingSpinner label="Loading dashboard..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="ticker-grid">
            {quotes.length === 0 ? (
              <EmptyState
                title="Piyasa verisi yok"
                description="Market modulu backend'den su anda veri dondurmuyor."
              />
            ) : (
              quotes.map((item, index) => (
                <SummaryCard
                  key={`${item.symbol}-${index}`}
                  title={item.displayName || item.symbol}
                  value={`${item.price ?? "-"} ${item.currency || ""}`.trim()}
                  subtitle={`${item.symbol} | ${item.source || "-"}`}
                  trend={formatMarketChange(item.changeRate)}
                  tone={Number(item.changeRate) >= 0 ? "cool" : "warm"}
                />
              ))
            )}
          </section>

          <section className="dashboard-grid">
            <div className="panel-surface dashboard-panel dashboard-panel-large">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Flow</p>
                  <h3>Ozet</h3>
                </div>
                <span className="pill">Today</span>
              </div>

              {quotes.length === 0 ? (
                <EmptyState title="Grafik verisi yok" description="Piyasa grafigi icin veri kaynagi yapilandirilmamis." />
              ) : (
                <div className="market-board">
                  {quoteLeaders.map((item) => (
                    <article key={item.symbol} className="market-board-row">
                      <div>
                        <strong>{item.symbol}</strong>
                        <p>{item.displayName || item.symbol}</p>
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

              <div className="mini-stat-row">
                <div>
                  <span>Aktif haberler</span>
                  <strong>{news.length}</strong>
                </div>
                <div>
                  <span>Pozitif hareket</span>
                  <strong>{positiveCount}</strong>
                </div>
                <div>
                  <span>Canli sembol</span>
                  <strong>{quotes.length}</strong>
                </div>
              </div>
            </div>

            <div className="panel-surface dashboard-panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Modules</p>
                  <h3>Hizli Erisim</h3>
                </div>
              </div>

              <div className="quick-links">
                {links.map((item) => (
                  <Link key={item.to} to={item.to} className="quick-link-card">
                    <strong>{item.title}</strong>
                    <p>{item.description}</p>
                    <span>Open module</span>
                  </Link>
                ))}
              </div>
            </div>

            <div className="panel-surface dashboard-panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Market Tape</p>
                  <h3>Market Akisi</h3>
                </div>
              </div>

              {quotes.length === 0 ? (
                <EmptyState title="Market akisi bos" description="Backend market verisi geldiginde burada listelenecek." />
              ) : (
                <div className="market-list">
                  {quotes.map((item) => (
                    <div key={item.symbol} className="market-list-item">
                      <div className="market-list-main">
                        <strong>{item.symbol}</strong>
                        <p>{item.displayName || item.symbol}</p>
                      </div>
                      <div className="market-list-side">
                        <strong>{item.price ?? "-"}</strong>
                        <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                          {formatMarketChange(item.changeRate)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="panel-surface dashboard-panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">News Flow</p>
                  <h3>Guncel Haberler</h3>
                </div>
              </div>

              {news.length === 0 ? (
                <EmptyState title="No news available" description="No news records returned by backend." />
              ) : (
                <div className="news-rail-list compact">
                  {news.map((item) => (
                    <div key={item.id} className="news-rail-item">
                      <div className="news-rail-badge">{(item.provider || "N").slice(0, 1)}</div>
                      <div>
                        <strong>{item.title || "Untitled"}</strong>
                        <p>{item.provider || item.source || "-"}</p>
                        <span>{formatDateTime(item.publishedAt)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
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
