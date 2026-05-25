import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { getMarketQuotes } from "../api/marketApi";
import { getNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { formatNewsDate } from "../utils/dateUtils";
import { buildNewsPlaceholderLabel, getNewsDateValue, getNewsProviderLabel, getProviderBadgeColor } from "../components/news/newsCardUtils";

function ensureArray(value) {
  return Array.isArray(value) ? value : [];
}

export default function HomePage() {
  const { t } = useTranslation();
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

        const [newsData, quoteData] = await Promise.all([getNews({ isKapDisclosure: false }), getMarketQuotes()]);

        if (!active) {
          return;
        }

        setNews(ensureArray(newsData?.content).slice(0, 4));
        setQuotes(ensureArray(quoteData).slice(0, 5));
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, t("home.loadError")));
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
  }, [t]);

  const featuredNews = ensureArray(news).slice(0, 5);

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow={t("home.eyebrow")}
        title={t("home.title")}
        description={t("home.description")}
      />

      {loading ? <LoadingSpinner label={t("home.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="hero-grid">
            <div className="hero-card panel-surface">
              <div className="hero-copy">
                <p className="eyebrow">{t("home.heroEyebrow")}</p>
                <h2>{t("home.heroTitle")}</h2>
                <p className="page-description">{t("home.heroDescription")}</p>
              </div>

              <div className="hero-chart">
                {quotes.length === 0 ? (
                  <EmptyState title={t("home.marketChartEmptyTitle")} description={t("home.marketChartEmptyDescription")} />
                ) : (
                  <div className="market-board compact">
                    {quotes.map((item) => (
                      <article key={item.symbol} className="market-board-row">
                        <div>
                          <strong>{item.code || item.symbol}</strong>
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
                  <p className="eyebrow">{t("home.newsEyebrow")}</p>
                  <h3>{t("home.newsTitle")}</h3>
                </div>
                <span className="pill">{t("common.live")}</span>
              </div>

              {news.length === 0 ? (
                <EmptyState title={t("home.newsEmptyTitle")} description={t("home.newsEmptyDescription")} />
              ) : (
                <div className="news-rail-list">
                  {news.map((item) => (
                    <article key={item.id} className="news-rail-item">
                      <div
                        className="news-rail-badge"
                        style={{ background: getProviderBadgeColor(item.provider || item.source) }}
                      >
                        {buildNewsPlaceholderLabel(item)}
                      </div>
                      <div>
                        <strong>{item.title || t("home.untitledNews")}</strong>
                        <p>{getNewsProviderLabel(item.provider || item.source)}</p>
                        <span>{formatNewsDate(getNewsDateValue(item))}</span>
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
                  subtitle={`${item.code || item.symbol} | ${item.source || "-"}`}
                  trend={formatMarketChange(item.changeRate)}
                  tone={Number(item.changeRate) >= 0 ? "cool" : "warm"}
                />
              ))
            ) : featuredNews.length === 0 ? (
              <EmptyState title={t("home.summaryEmptyTitle")} description={t("home.summaryEmptyDescription")} />
            ) : (
              featuredNews.map((item, idx) => (
                <SummaryCard
                  key={`${item.id}-${idx}`}
                  title={(item.title || t("home.newsValue")).slice(0, 48)}
                  value={t("home.newsValue")}
                  subtitle={[item.provider, item.source].filter(Boolean).join(" | ") || "-"}
                  trend={formatNewsDate(getNewsDateValue(item))}
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
