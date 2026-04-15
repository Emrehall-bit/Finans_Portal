import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { getTcmbMarketData } from "../api/marketApi";
import { getNews } from "../api/newsApi";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { extractErrorMessage } from "../api/responseUtils";
import { formatDateTime, formatNumber } from "../utils/formatters";

const links = [
  { title: "Market Data", to: "/markets" },
  { title: "News", to: "/news" },
  { title: "Portfolio", to: "/portfolio" },
  { title: "Watchlist", to: "/watchlist" },
  { title: "Alerts", to: "/alerts" },
];

export default function DashboardPage() {
  const [market, setMarket] = useState([]);
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError("");
        const [marketData, newsData] = await Promise.all([getTcmbMarketData(), getNews()]);
        setMarket(marketData.slice(0, 4));
        setNews(newsData.slice(0, 3));
      } catch (err) {
        setError(extractErrorMessage(err, "Dashboard data could not be loaded."));
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div>
      <PageHeader title="Finans Portalı öngösterim" description="Ön gösterim" />
      <div className="cards-grid">
        {links.map((item) => (
          <Link key={item.to} to={item.to} className="card link-card">
            <h3>{item.title}</h3>
            <p className="muted">Open {item.title} module</p>
          </Link>
        ))}
      </div>

      {loading ? <LoadingSpinner label="Loading dashboard..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <div className="split-grid">
          <section className="card">
            <h3>Latest Market Snapshot</h3>
            {market.length === 0 ? (
              <EmptyState title="No market data" description="No recent TCMB market records were returned." />
            ) : (
              <div className="list">
                {market.map((item, idx) => (
                  <SummaryCard
                    key={`${item.symbol}-${idx}`}
                    title={item.name || item.symbol || "Unknown Instrument"}
                    value={formatNumber(item.price)}
                    subtitle={`${item.source || "-"} | ${formatDateTime(item.lastUpdated)}`}
                  />
                ))}
              </div>
            )}
          </section>
          <section className="card">
            <h3>Latest News</h3>
            {news.length === 0 ? (
              <EmptyState title="No news available" description="No news records returned by backend." />
            ) : (
              <div className="list">
                {news.map((item) => (
                  <div key={item.id} className="list-item">
                    <strong>{item.title || "Untitled"}</strong>
                    <span className="muted">{item.provider || item.source || "-"}</span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}
