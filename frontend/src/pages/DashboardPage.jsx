import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError("");
        const newsData = await getNews();
        setNews((newsData.content ?? []).slice(0, 4));
      } catch (err) {
        setError(extractErrorMessage(err, "Dashboard data could not be loaded."));
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

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
            <EmptyState
              title="Piyasa verisi yok"
              description="Canli fiyat modulu bu surumda devre disi. Haber ve portfoy modullerini kullanmaya devam edebilirsiniz."
            />
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

              <EmptyState title="Grafik verisi yok" description="Piyasa grafigi icin veri kaynagi yapilandirilmamis." />

              <div className="mini-stat-row">
                <div>
                  <span>Aktif haberler</span>
                  <strong>{news.length}</strong>
                </div>
                <div>
                  <span>Cache durumu</span>
                  <strong>Online</strong>
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
