import { useEffect, useState } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getMarketData } from "../api/marketApi";
import { getNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { formatDateTime, formatNumber } from "../utils/formatters";

function toChartData(items) {
  return items.slice(0, 7).map((item, index) => ({
    name: item.symbol || `FX-${index + 1}`,
    value: Number(item.price) || 0,
  }));
}

export default function HomePage() {
  const [market, setMarket] = useState([]);
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");

        const [marketData, newsData] = await Promise.all([getMarketData(), getNews()]);

        if (!active) {
          return;
        }

        setMarket(marketData);
        setNews(newsData.slice(0, 4));
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

  const featuredMarket = market.slice(0, 5);
  const chartData = toChartData(market);

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow="Public Landing"
        title="Finans Portali"
        description="Haber akisiniz, piyasa snapshot'i ve grafikler herkese acik ilk ekranda."
      />

      {loading ? <LoadingSpinner label="Ana sayfa yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="hero-grid">
            <div className="hero-card panel-surface">
              <div className="hero-copy">
                <p className="eyebrow">Market Pulse</p>
                <h2>Tek ekranda piyasa ozeti, haberler ve finansal ritim.</h2>
                <p className="page-description">
                  Public ziyaretciler piyasayi izler, uye kullanicilar dashboard ve portfoy modullerine devam eder.
                </p>
              </div>

              <div className="hero-chart">
                {chartData.length === 0 ? (
                  <EmptyState title="Grafik verisi yok" description="Piyasa grafigi olusturulamadi." />
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartData}>
                      <defs>
                        <linearGradient id="heroArea" x1="0" x2="0" y1="0" y2="1">
                          <stop offset="0%" stopColor="#7ca7ff" stopOpacity="0.75" />
                          <stop offset="100%" stopColor="#7ca7ff" stopOpacity="0.08" />
                        </linearGradient>
                      </defs>
                      <CartesianGrid stroke="#e8eefb" vertical={false} />
                      <XAxis dataKey="name" axisLine={false} tickLine={false} />
                      <YAxis axisLine={false} tickLine={false} />
                      <Tooltip />
                      <Area type="monotone" dataKey="value" stroke="#4c7fff" strokeWidth={3} fill="url(#heroArea)" />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
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
            {featuredMarket.length === 0 ? (
              <EmptyState title="Piyasa verisi yok" description="Su anda gosterilecek piyasa verisi bulunamadi." />
            ) : (
              featuredMarket.map((item, idx) => (
                <SummaryCard
                  key={`${item.symbol}-${idx}`}
                  title={item.symbol || item.name || "FX"}
                  value={formatNumber(item.price)}
                  subtitle={[item.name, item.instrumentType, item.source].filter(Boolean).join(" | ") || "-"}
                  trend={`+0.${idx + 2}%`}
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
