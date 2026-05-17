import { useEffect, useState } from "react";
import { AlertTriangle, Info, Newspaper, TrendingUp, TrendingDown, Minus } from "lucide-react";
import { getAiNewsImpactAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiNewsImpactCard({ newsId }) {
  const { isAuthenticated, isPremium } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!newsId || !isPremium) {
      setData(null);
      setError("");
      setLoading(false);
      return;
    }

    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");
        const result = await getAiNewsImpactAnalysis(newsId);
        if (active) setData(result ?? null);
      } catch {
        if (active) {
          setData(null);
          setError("AI etki analizi su anda uretilemedi.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [isPremium, newsId]);

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName="AI Etki Analizi"
        description="Bu haberin finansal ve sektorel AI yorumunu gormek icin giris yapin."
      />
    );
  }

  if (!isPremium) {
    return (
      <AiLockedCard
        featureName="AI Etki Analizi"
        description="Haberin finansal, sektorel ve piyasa etkisini yorumlayan gelismis AI analizi premium uyelere ozeldir."
        requiresPremium
      />
    );
  }

  return (
    <section className="ai-card ai-news-impact-card">
      <div className="ai-card-glow ai-news-impact-glow" aria-hidden="true" />

      <header className="ai-card-head">
        <div className="ai-card-title-row">
          <span className="ai-card-icon ai-news-impact-icon" aria-hidden="true">
            <Newspaper size={17} />
          </span>
          <div>
            <h3>AI Etki Analizi</h3>
            <p>Bu haberin finansal ve sektorel yorumu</p>
          </div>
        </div>
        <span className="ai-card-badge ai-news-impact-badge">HABER</span>
      </header>

      <div className="ai-card-body">
        {loading ? <ImpactSkeleton /> : null}
        {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
        {!loading && !error && data ? <ImpactContent data={data} /> : null}
      </div>

      {data ? <div className="ai-card-footer"><AiResponseMeta metadata={data.metadata} /></div> : null}

      <p className="ai-disclaimer">
        <Info size={13} aria-hidden="true" />
        <span>Bu yorum yatirim tavsiyesi degildir; yalnizca mevcut verilerin otomatik analizidir.</span>
      </p>
    </section>
  );
}

function ImpactContent({ data }) {
  const hasHighlights = Array.isArray(data.highlights) && data.highlights.length > 0;
  const hasSectors = Array.isArray(data.affectedSectors) && data.affectedSectors.length > 0;
  const hasMarketImpact = data.marketImpact && data.marketImpact.trim().length > 0;

  return (
    <>
      <div className="ai-impact-meta">
        <SentimentBadge sentiment={data.sentiment} />
        <RiskBadge riskLevel={data.riskLevel} />
      </div>

      {data.summary ? <p className="ai-impact-summary">{data.summary}</p> : null}

      {hasMarketImpact ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <TrendingUp size={14} aria-hidden="true" />
            <strong>Piyasa Etkisi</strong>
          </div>
          <p className="ai-impact-text">{data.marketImpact}</p>
        </div>
      ) : null}

      {hasSectors ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <AlertTriangle size={14} aria-hidden="true" />
            <strong>Etkilenebilecek Sektorler</strong>
          </div>
          <div className="ai-sector-chips">
            {data.affectedSectors.map((sector, i) => (
              <span key={i} className="ai-sector-chip">{sector}</span>
            ))}
          </div>
        </div>
      ) : null}

      {hasHighlights ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <Newspaper size={14} aria-hidden="true" />
            <strong>One Cikanlar</strong>
          </div>
          <ul className="ai-insight-list">
            {data.highlights.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}
    </>
  );
}

function SentimentBadge({ sentiment }) {
  const map = {
    POSITIVE: { label: "Olumlu", cls: "positive" },
    NEGATIVE: { label: "Olumsuz", cls: "negative" },
    MIXED: { label: "Karma", cls: "mixed" },
    NEUTRAL: { label: "Notr", cls: "neutral" },
  };
  const { label, cls } = map[sentiment] ?? { label: sentiment, cls: "neutral" };
  const Icon = sentiment === "POSITIVE" ? TrendingUp
    : sentiment === "NEGATIVE" ? TrendingDown
      : Minus;
  return (
    <span className={`ai-sentiment-badge ai-sentiment-badge--${cls}`}>
      <Icon size={11} aria-hidden="true" />
      {label}
    </span>
  );
}

function RiskBadge({ riskLevel }) {
  const map = {
    LOW: { label: "Dusuk Risk", cls: "low" },
    MEDIUM: { label: "Orta Risk", cls: "medium" },
    HIGH: { label: "Yuksek Risk", cls: "high" },
  };
  const { label, cls } = map[riskLevel] ?? { label: riskLevel, cls: "medium" };
  return (
    <span className={`ai-risk-badge ai-risk-badge--${cls}`}>
      {label}
    </span>
  );
}

function ImpactSkeleton() {
  return (
    <div className="ai-unified-skeleton" aria-busy="true" aria-label="AI etki analizi hazirlaniyor">
      <div className="ai-skeleton-line ai-skeleton-line--narrow" style={{ width: "40%", marginBottom: "0.75rem" }} />
      <div className="ai-skeleton-line ai-skeleton-line--wide" />
      <div className="ai-skeleton-line" />
      <div className="ai-skeleton-line ai-skeleton-line--wide" style={{ marginTop: "0.75rem" }} />
      <div className="ai-skeleton-line ai-skeleton-line--narrow" />
    </div>
  );
}
