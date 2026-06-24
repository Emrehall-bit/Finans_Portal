import { useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { TrendingDown, TrendingUp, Bot, ChevronRight } from "lucide-react";
import { extractErrorMessage } from "../api/responseUtils";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import { useMarketQuotes } from "../hooks/useMarketQueries";
import { useNewsList } from "../hooks/useNewsQueries";
import { formatNewsPublishedAt, getNewsProviderLabel } from "../components/news/newsCardUtils";

const MARKET_TABS = [
  { key: "ALL",       label: "Hepsi" },
  { key: "STOCK",     label: "Hisse (BIST)" },
  { key: "CRYPTO",    label: "Kripto" },
  { key: "FUND",      label: "Fon" },
  { key: "COMMODITY", label: "Emtia" },
];

const TAB_COMMENTARY = {
  ALL:       "Piyasalarda karma bir seyir gözlemleniyor. Yükselenler genel olarak önde, ancak bazı sektörlerde satış baskısı sürüyor.",
  STOCK:     "BIST'te yükselenler çoğunlukta. Bankacılık ve savunma sanayi hisseleri öne çıkarken, enerji sektöründe kâr satışı görülüyor.",
  CRYPTO:    "Kripto piyasalarında volatilite yüksek. Bitcoin ve büyük coin'lerde karma seyir; altcoin'lerde seçici toparlanma var.",
  FUND:      "Hisse ağırlıklı fonlar pozitif ayrışırken, kısa vadeli tahvil fonları yatay seyrediyor.",
  COMMODITY: "Enerji emtiaları güçlü, altın jeopolitik belirsizlik nedeniyle talep görüyor.",
};

function getThumb(item) {
  return item?.thumbnailUrl || item?.imageUrl || item?.image || null;
}

function providerInitials(provider) {
  if (!provider) return "FP";
  const parts = String(provider).trim().split(/[\s_-]+/);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return provider.slice(0, 2).toUpperCase();
}

function fmtPct(v) {
  const n = Number(v);
  if (!Number.isFinite(n)) return "-";
  return `${n >= 0 ? "+" : ""}${n.toFixed(2)}%`;
}

export default function HomePage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState("STOCK");

  const { data: allQuotes = [], isLoading: quotesLoading, error: quotesError } = useMarketQuotes();
  const { data: newsData = null, isLoading: newsLoading, error: newsError } = useNewsList({ isKapDisclosure: false });

  const news = useMemo(
    () => (Array.isArray(newsData?.content) ? newsData.content : []).slice(0, 8),
    [newsData],
  );

  const loading = quotesLoading || newsLoading;
  const error = quotesError || newsError
    ? extractErrorMessage(quotesError || newsError, t("home.loadError"))
    : "";

  const filteredQuotes = useMemo(() => {
    if (activeTab === "ALL") return allQuotes;
    return allQuotes.filter((q) => (q.instrumentType || "").toUpperCase() === activeTab);
  }, [allQuotes, activeTab]);

  const risingCount  = useMemo(() => filteredQuotes.filter((q) => Number(q.changeRate) > 0).length, [filteredQuotes]);
  const fallingCount = useMemo(() => filteredQuotes.filter((q) => Number(q.changeRate) < 0).length, [filteredQuotes]);

  const topGainers = useMemo(
    () =>
      [...filteredQuotes]
        .filter((q) => Number(q.changeRate) > 0)
        .sort((a, b) => Number(b.changeRate) - Number(a.changeRate))
        .slice(0, 3),
    [filteredQuotes],
  );

  const topLosers = useMemo(
    () =>
      [...filteredQuotes]
        .filter((q) => Number(q.changeRate) < 0)
        .sort((a, b) => Number(a.changeRate) - Number(b.changeRate))
        .slice(0, 3),
    [filteredQuotes],
  );

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow={t("home.eyebrow")}
        title={t("home.title")}
        description={t("home.description")}
      />

      {loading ? <LoadingSpinner label={t("home.loading")} /> : null}
      {error   ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <div className="home-hero-grid">

          {/* ── Sol: Piyasalarda Bugün ─────────────────────────── */}
          <section className="panel-surface home-market-card">

            {/* Başlık + istatistik */}
            <div className="home-market-head">
              <p className="eyebrow">PİYASALARDA BUGÜN</p>
              {(risingCount > 0 || fallingCount > 0) ? (
                <div className="home-market-stats-bar">
                  <span className="home-stat-up">▲ {risingCount} yükselen</span>
                  <span className="home-stat-sep" />
                  <span className="home-stat-down">▼ {fallingCount} düşen</span>
                </div>
              ) : null}
            </div>

            {/* Kategori sekmeleri */}
            <div className="home-market-tabs" role="tablist">
              {MARKET_TABS.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  role="tab"
                  aria-selected={activeTab === tab.key}
                  className={`home-market-tab${activeTab === tab.key ? " active" : ""}`}
                  onClick={() => setActiveTab(tab.key)}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Yükselenler / Düşenler */}
            <div className="home-movers-grid">
              {/* Yükselenler */}
              <div className="home-movers-col">
                <div className="home-movers-col-head home-movers-col-head--up">
                  <TrendingUp size={13} strokeWidth={2.4} />
                  <span>Top 3 Yükselenler</span>
                </div>
                {topGainers.length === 0 ? (
                  <p className="home-movers-empty">Bu kategoride yükselen yok</p>
                ) : (
                  <div className="home-movers-list">
                    {topGainers.map((q) => (
                      <div key={q.symbol} className="home-mover-row">
                        <div className="home-mover-info">
                          <strong>{q.code || q.symbol}</strong>
                          <span>{(q.displayName || q.name || "").slice(0, 22) || "-"}</span>
                        </div>
                        <span className="home-mover-pct home-mover-pct--up">
                          {fmtPct(q.changeRate)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Düşenler */}
              <div className="home-movers-col">
                <div className="home-movers-col-head home-movers-col-head--down">
                  <TrendingDown size={13} strokeWidth={2.4} />
                  <span>Top 3 Düşenler</span>
                </div>
                {topLosers.length === 0 ? (
                  <p className="home-movers-empty">Bu kategoride düşen yok</p>
                ) : (
                  <div className="home-movers-list">
                    {topLosers.map((q) => (
                      <div key={q.symbol} className="home-mover-row">
                        <div className="home-mover-info">
                          <strong>{q.code || q.symbol}</strong>
                          <span>{(q.displayName || q.name || "").slice(0, 22) || "-"}</span>
                        </div>
                        <span className="home-mover-pct home-mover-pct--down">
                          {fmtPct(q.changeRate)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* AI yorumu */}
            <p className="home-market-commentary">
              {TAB_COMMENTARY[activeTab]}
            </p>
          </section>

          {/* ── Sağ sütun ─────────────────────────────────────── */}
          <div className="home-right-col">

            {/* AI Piyasa Özeti */}
            <section className="panel-surface home-ai-card">
              <div className="home-ai-icon-wrap">
                <Bot size={17} strokeWidth={2} />
              </div>
              <div className="home-ai-copy">
                <strong>AI Piyasa Özeti</strong>
                <p>Günün piyasa görünümünü özetle.</p>
              </div>
              <Link to="/dashboard" className="home-ai-btn">
                Özeti Gör <ChevronRight size={13} />
              </Link>
            </section>

            {/* Son Haberler */}
            <section className="panel-surface home-news-card">
              <div className="panel-head" style={{ padding: "0 0 10px" }}>
                <h3 style={{ margin: 0, fontSize: "0.9rem" }}>Son Haberler</h3>
                <span className="pill" style={{ fontSize: "0.7rem" }}>Canlı</span>
              </div>

              <div className="home-news-list">
                {news.length === 0 ? (
                  <p className="home-movers-empty">Haber bulunamadı</p>
                ) : (
                  news.map((item) => {
                    const thumb = getThumb(item);
                    return (
                      <article key={item.id} className={`home-news-item${thumb ? " has-thumb" : ""}`}>
                        {thumb ? (
                          <img src={thumb} alt="" className="home-news-thumb" loading="lazy" />
                        ) : (
                          <div
                            className="home-news-thumb home-news-thumb--placeholder"
                            aria-hidden="true"
                          >
                            {providerInitials(item.provider || item.source)}
                          </div>
                        )}
                        <div className="home-news-content">
                          <div className="home-news-meta">
                            <span className="home-news-source">
                              {getNewsProviderLabel(item.provider || item.source)}
                            </span>
                            <span className="home-news-date">
                              {formatNewsPublishedAt(item, "")}
                            </span>
                          </div>
                          <p className="home-news-title">{item.title || "Haber"}</p>
                        </div>
                      </article>
                    );
                  })
                )}
              </div>
            </section>

          </div>
        </div>
      ) : null}
    </div>
  );
}
