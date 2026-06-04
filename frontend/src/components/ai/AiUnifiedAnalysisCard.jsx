import { useEffect, useState } from "react";
import { Sparkles, TrendingUp, AlertTriangle, Info } from "lucide-react";
import { getAiUnifiedAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiUnifiedAnalysisCard({ symbol, instrumentType = "STOCK" }) {
  const { isAuthenticated, isPremium } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!symbol || !isPremium) {
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
        const result = await getAiUnifiedAnalysis(symbol, instrumentType);
        if (active) setData(result ?? null);
      } catch {
        if (active) {
          setData(null);
          setError("AI genel degerlendirme su anda uretilemedi.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [instrumentType, isPremium, symbol]);

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName="AI Genel Degerlendirme"
        description="Teknik ve temel analizleri birlestiren AI yorumu gormek icin giris yapin."
      />
    );
  }

  if (!isPremium) {
    return (
      <AiLockedCard
        featureName="AI Genel Degerlendirme"
        description="Teknik ve temel analizleri birlestiren gelismis AI yorumu premium uyelere ozeldir."
        requiresPremium
      />
    );
  }

  return (
    <section className="ai-card ai-unified-card">
      <div className="ai-card-glow" aria-hidden="true" />

      <header className="ai-card-head">
        <div className="ai-card-title-row">
          <span className="ai-card-icon" aria-hidden="true">
            <Sparkles size={17} />
          </span>
          <div>
            <h3>AI Genel Degerlendirme</h3>
            <p>{symbol || "Varlik"} icin teknik + temel birlesik analiz</p>
          </div>
        </div>
        <span className="ai-card-badge ai-unified-badge">PRO</span>
      </header>

      <div className="ai-card-body">
        {loading ? <UnifiedSkeleton /> : null}
        {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
        {!loading && !error && data && hasContent(data) ? <UnifiedContent data={data} /> : null}
        {!loading && !error && data && !hasContent(data) ? (
          <p className="ai-state-message">Birlesik analiz icin yeterli temel veri bulunmuyor.</p>
        ) : null}
      </div>

      {data && hasContent(data) ? (
        <div className="ai-card-footer"><AiResponseMeta metadata={data.metadata} /></div>
      ) : null}

      <p className="ai-disclaimer">
        <Info size={13} aria-hidden="true" />
        <span>Bu yorum yatirim tavsiyesi degildir; yalnizca mevcut verilerin otomatik analizidir.</span>
      </p>
    </section>
  );
}

function UnifiedContent({ data }) {
  const hasHighlights = Array.isArray(data.highlights) && data.highlights.length > 0;
  const hasRisks = Array.isArray(data.risks) && data.risks.length > 0;

  return (
    <>
      {data.summary ? <p className="ai-unified-summary">{data.summary}</p> : null}

      {hasHighlights ? (
        <div className="ai-unified-section">
          <div className="ai-unified-section-head">
            <TrendingUp size={14} aria-hidden="true" />
            <strong>One Cikanlar</strong>
          </div>
          <ul className="ai-insight-list">
            {data.highlights.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {hasRisks ? (
        <div className="ai-unified-section">
          <div className="ai-unified-section-head ai-unified-section-head--risk">
            <AlertTriangle size={14} aria-hidden="true" />
            <strong>Riskler</strong>
          </div>
          <ul className="ai-insight-list ai-insight-list--risk">
            {data.risks.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}
    </>
  );
}

function hasContent(data) {
  return (
    Boolean(data?.summary) ||
    (Array.isArray(data?.highlights) && data.highlights.length > 0) ||
    (Array.isArray(data?.risks) && data.risks.length > 0)
  );
}

function UnifiedSkeleton() {
  return (
    <div className="ai-unified-skeleton" aria-busy="true" aria-label="AI genel degerlendirme hazirlaniyor">
      <div className="ai-skeleton-line ai-skeleton-line--wide" />
      <div className="ai-skeleton-line" />
      <div className="ai-skeleton-line ai-skeleton-line--narrow" />
      <div className="ai-skeleton-line ai-skeleton-line--wide" style={{ marginTop: "0.75rem" }} />
      <div className="ai-skeleton-line" />
    </div>
  );
}
