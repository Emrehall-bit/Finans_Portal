import { useEffect, useState } from "react";
import { Sparkles, TrendingUp, AlertTriangle, Info } from "lucide-react";
import { getAiUnifiedAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiPremiumGate from "./AiPremiumGate";

export default function AiUnifiedAnalysisCard({ symbol, instrumentType = "STOCK" }) {
  const { isPremium } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!symbol) {
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
          setError("AI genel değerlendirme şu anda üretilemedi.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => { active = false; };
  }, [symbol, instrumentType]);

  if (!isPremium) {
    return (
      <AiPremiumGate
        featureName="AI Genel Değerlendirme"
        description="Teknik ve temel analizleri birleştiren gelişmiş AI yorumu premium üyelere özeldir."
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
            <h3>AI Genel Değerlendirme</h3>
            <p>{symbol || "Varlık"} için teknik + temel birleşik analiz</p>
          </div>
        </div>
        <span className="ai-card-badge ai-unified-badge">PRO</span>
      </header>

      <div className="ai-card-body">
        {loading ? <UnifiedSkeleton /> : null}

        {!loading && error ? (
          <p className="ai-state-message error">{error}</p>
        ) : null}

        {!loading && !error && data ? (
          <UnifiedContent data={data} />
        ) : null}
      </div>

      <p className="ai-disclaimer">
        <Info size={13} aria-hidden="true" />
        <span>Bu yorum yatırım tavsiyesi değildir; yalnızca mevcut verilerin otomatik analizidir.</span>
      </p>
    </section>
  );
}

function UnifiedContent({ data }) {
  const hasHighlights = Array.isArray(data.highlights) && data.highlights.length > 0;
  const hasRisks = Array.isArray(data.risks) && data.risks.length > 0;

  return (
    <>
      {data.summary ? (
        <p className="ai-unified-summary">{data.summary}</p>
      ) : null}

      {hasHighlights ? (
        <div className="ai-unified-section">
          <div className="ai-unified-section-head">
            <TrendingUp size={14} aria-hidden="true" />
            <strong>Öne Çıkanlar</strong>
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

      {data.fallbackUsed ? (
        <p className="ai-unified-fallback-note">
          Birleşik AI analizi hazırlanamadı; deterministic yorum gösteriliyor.
        </p>
      ) : null}
    </>
  );
}

function UnifiedSkeleton() {
  return (
    <div className="ai-unified-skeleton" aria-busy="true" aria-label="AI genel değerlendirme hazırlanıyor">
      <div className="ai-skeleton-line ai-skeleton-line--wide" />
      <div className="ai-skeleton-line" />
      <div className="ai-skeleton-line ai-skeleton-line--narrow" />
      <div className="ai-skeleton-line ai-skeleton-line--wide" style={{ marginTop: "0.75rem" }} />
      <div className="ai-skeleton-line" />
    </div>
  );
}
