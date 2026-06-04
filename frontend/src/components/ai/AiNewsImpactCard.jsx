import { useState } from "react";
import {
  AlertTriangle,
  Brain,
  ChevronDown,
  Info,
  Minus,
  Newspaper,
  Sparkles,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { getAiNewsImpactAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiNewsImpactCard({ newsId }) {
  const { isAuthenticated, isPremium } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [expanded, setExpanded] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);

  async function handleToggle() {
    const nextExpanded = !expanded;
    setExpanded(nextExpanded);

    if (!nextExpanded || !newsId || !isPremium || hasLoaded || loading) {
      return;
    }

    try {
      setLoading(true);
      setError("");
      const result = await getAiNewsImpactAnalysis(newsId);
      setData(result ?? null);
      setHasLoaded(true);
    } catch {
      setData(null);
      setError("AI etki analizi şu anda üretilemedi.");
    } finally {
      setLoading(false);
    }
  }

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName="AI Etki Analizi"
        description="Bu haberin finansal ve sektörel AI yorumunu görmek için giriş yapın."
      />
    );
  }

  if (!isPremium) {
    return (
      <AiLockedCard
        featureName="AI Etki Analizi"
        description="Haberin finansal, sektörel ve piyasa etkisini yorumlayan gelişmiş AI analizi premium üyelere özeldir."
        requiresPremium
      />
    );
  }

  return (
    <section className={`ai-card ai-news-impact-card ai-news-impact-card--toggle${expanded ? " is-expanded" : ""}`}>
      <div className="ai-card-glow ai-news-impact-glow" aria-hidden="true" />

      <button type="button" className="ai-news-impact-trigger" onClick={handleToggle}>
        <div className="ai-news-impact-trigger-main">
          <span className="ai-card-icon ai-news-impact-icon" aria-hidden="true">
            {hasLoaded ? <Brain size={17} /> : <Sparkles size={17} />}
          </span>
          <div>
            <h3>{hasLoaded ? "AI Etki Analizi" : "Yapay Zeka Etki Analizini Başlat"}</h3>
            <p>{hasLoaded ? "Analiz hazır, detayları görüntüleyebilirsiniz." : "AI ile analiz et"}</p>
          </div>
        </div>

        <div className="ai-news-impact-trigger-actions">
          <span className="ai-card-badge ai-news-impact-badge">{hasLoaded ? "HAZIR" : "AI"}</span>
          <ChevronDown
            size={18}
            className={`ai-news-impact-chevron${expanded ? " is-expanded" : ""}`}
            aria-hidden="true"
          />
        </div>
      </button>

      <div className={`ai-news-impact-collapsible${expanded ? " is-expanded" : ""}`}>
        <div className="ai-card-body ai-news-impact-collapsible-inner">
          {loading ? <ImpactSkeleton /> : null}
          {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
          {!loading && !error && data ? <ImpactContent data={data} /> : null}
        </div>

        {data ? (
          <div className="ai-card-footer">
            <AiResponseMeta metadata={data.metadata} />
          </div>
        ) : null}

        <p className="ai-disclaimer">
          <Info size={13} aria-hidden="true" />
          <span>Bu yorum yatırım tavsiyesi değildir; yalnızca mevcut verilerin otomatik analizidir.</span>
        </p>
      </div>
    </section>
  );
}

function ImpactContent({ data }) {
  // Prefer new fields; fall back to legacy fields for backward compat
  const financialContext = data.financialContext || data.summary;
  const shortTermImpact  = data.shortTermImpact  || data.marketImpact;
  const mediumTermImpact = data.mediumTermImpact;
  const uncertainty      = data.uncertainty;

  // Prefer LLM-generated affected assets; fall back to rule-based sectors
  const assetsToShow =
    Array.isArray(data.affectedAssets) && data.affectedAssets.length > 0
      ? data.affectedAssets
      : Array.isArray(data.affectedSectors) && data.affectedSectors.length > 0
      ? data.affectedSectors
      : [];

  const hasAssets      = assetsToShow.length > 0;
  const hasHighlights  = Array.isArray(data.highlights) && data.highlights.length > 0;
  const hasUncertainty = uncertainty && uncertainty.trim().length > 0;

  return (
    <>
      <div className="ai-impact-meta">
        <SentimentBadge sentiment={data.sentiment} />
        <RiskBadge riskLevel={data.riskLevel} />
      </div>

      {financialContext ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <Info size={14} aria-hidden="true" />
            <strong>Finansal Bağlam</strong>
          </div>
          <p className="ai-impact-text">{financialContext}</p>
        </div>
      ) : null}

      {shortTermImpact ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <TrendingUp size={14} aria-hidden="true" />
            <strong>Kısa Vadeli Etki</strong>
          </div>
          <p className="ai-impact-text">{shortTermImpact}</p>
        </div>
      ) : null}

      {mediumTermImpact ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <TrendingUp size={14} aria-hidden="true" />
            <strong>Orta Vadeli Etki</strong>
          </div>
          <p className="ai-impact-text">{mediumTermImpact}</p>
        </div>
      ) : null}

      {hasAssets ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <AlertTriangle size={14} aria-hidden="true" />
            <strong>Etkilenebilecek Varlıklar</strong>
          </div>
          <div className="ai-sector-chips">
            {assetsToShow.map((item, i) => (
              <span key={i} className="ai-sector-chip">{item}</span>
            ))}
          </div>
        </div>
      ) : null}

      {hasHighlights ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <Newspaper size={14} aria-hidden="true" />
            <strong>Öne Çıkanlar</strong>
          </div>
          <ul className="ai-insight-list">
            {data.highlights.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {hasUncertainty ? (
        <div className="ai-impact-section">
          <div className="ai-impact-section-head">
            <Info size={14} aria-hidden="true" />
            <strong>Belirsizlikler</strong>
          </div>
          <p className="ai-impact-text">{uncertainty}</p>
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
    NEUTRAL: { label: "Nötr", cls: "neutral" },
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
    LOW: { label: "Düşük Risk", cls: "low" },
    MEDIUM: { label: "Orta Risk", cls: "medium" },
    HIGH: { label: "Yüksek Risk", cls: "high" },
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
    <div className="ai-unified-skeleton" aria-busy="true" aria-label="AI etki analizi hazırlanıyor">
      <div className="ai-skeleton-line ai-skeleton-line--narrow" style={{ width: "40%", marginBottom: "0.75rem" }} />
      <div className="ai-skeleton-line ai-skeleton-line--wide" />
      <div className="ai-skeleton-line" />
      <div className="ai-skeleton-line ai-skeleton-line--wide" style={{ marginTop: "0.75rem" }} />
      <div className="ai-skeleton-line ai-skeleton-line--narrow" />
    </div>
  );
}
