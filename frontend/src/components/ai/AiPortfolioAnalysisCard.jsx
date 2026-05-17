import { AlertTriangle, ShieldAlert, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getPortfolioAiAnalysis } from "../../api/aiApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useAuth } from "../../auth/AuthContext";
import AiCard from "./AiCard";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiPortfolioAnalysisCard({ portfolioId, portfolioName, holdingsCount = 0 }) {
  const { isAuthenticated, isPremium } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const canUse = isPremium;
  const hasPortfolio = Boolean(portfolioId);
  const isEmptyPortfolio = Number(holdingsCount || 0) === 0;
  const title = useMemo(
    () => (portfolioName ? `${portfolioName} icin premium AI portfoy yorumu` : "Premium AI portfoy yorumu"),
    [portfolioName],
  );

  useEffect(() => {
    setData(null);
    setLoading(false);
    setError("");
  }, [portfolioId]);

  async function handleAnalyze() {
    if (!canUse || !hasPortfolio || loading) {
      return;
    }

    try {
      setLoading(true);
      setError("");
      const response = await getPortfolioAiAnalysis(portfolioId);
      setData(response ?? null);
    } catch (requestError) {
      setData(null);
      setError(extractErrorMessage(requestError, "AI portfoy analizi su anda uretilemedi."));
    } finally {
      setLoading(false);
    }
  }

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName="AI Portfoy Analizi"
        description="Portfoy dagilimi, risk gorunumu ve cesitlilik yorumunu gormek icin giris yapin."
      />
    );
  }

  if (!canUse) {
    return (
      <AiLockedCard
        featureName="AI Portfoy Analizi"
        description="Bu ozellik premium kullanicilar icin gecerlidir."
        requiresPremium
      />
    );
  }

  return (
    <AiCard
      title="AI Portfoy Analizi"
      subtitle={title}
      badge="PRO"
      className="ai-portfolio-analysis-card"
      footer={data ? <AiResponseMeta metadata={data.metadata} /> : null}
    >
      <div className="ai-portfolio-analysis-toolbar">
        <div className="ai-portfolio-analysis-intro">
          <span className="summary-chip">Pozisyon: {Number(holdingsCount || 0)}</span>
          <p>Analiz sadece butona bastiginizda calisir. Sayfa acilisinda otomatik istek yapilmaz.</p>
        </div>
        <button type="button" className="ai-comparison-action" onClick={handleAnalyze} disabled={!hasPortfolio || loading}>
          {loading ? "Analiz uretiliyor..." : "Portfoyumu AI ile Analiz Et"}
        </button>
      </div>

      {error ? <p className="ai-state-message error">{error}</p> : null}

      {!loading && !error && !data ? (
        <div className="ai-portfolio-analysis-placeholder">
          <Sparkles size={16} aria-hidden="true" />
          <p>
            {isEmptyPortfolio
              ? "Portfoy su anda bos gorunuyor. Isterseniz butona basarak bos portfoy icin kontrollu AI yorumunu alabilirsiniz."
              : "Dagilim, risk sinyalleri ve cesitlilik yorumu butona bastiktan sonra burada gosterilir."}
          </p>
        </div>
      ) : null}

      {!loading && !error && data ? (
        <div className="ai-portfolio-analysis-content">
          {data.dataQuality !== "COMPLETE" ? (
            <div className="ai-comparison-warning">
              <AlertTriangle size={14} aria-hidden="true" />
              <span>Veri kalitesi {formatDataQuality(data.dataQuality)}. Yorum bazi alanlarda sinirli veriyle uretildi.</span>
            </div>
          ) : null}

          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title="Ozet" content={data.summary} />
          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title="Dagilim yorumu" content={data.allocationComment} />
          <AnalysisBlock icon={<ShieldAlert size={14} aria-hidden="true" />} title="Risk gorunumu" content={data.riskComment} />
          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title="Cesitlilik" content={data.diversificationComment} />

          <div className="ai-comparison-grid">
            <ListBlock title="Guclu pozisyonlar" items={data.strongestPositions} tone="positive" />
            <ListBlock title="Zayif pozisyonlar" items={data.weakestPositions} tone="risk" />
          </div>

          {Array.isArray(data.riskSignals) && data.riskSignals.length > 0 ? (
            <div className="ai-portfolio-analysis-signals">
              <div className="ai-unified-section-head">
                <ShieldAlert size={14} aria-hidden="true" />
                <strong>Risk sinyalleri</strong>
              </div>
              <ul className="ai-insight-list ai-portfolio-signal-list">
                {data.riskSignals.map((item, index) => (
                  <li key={`risk-${index}`}>{item}</li>
                ))}
              </ul>
            </div>
          ) : null}

          {Array.isArray(data.suggestions) && data.suggestions.length > 0 ? (
            <div className="ai-portfolio-analysis-suggestions">
              <div className="ai-unified-section-head">
                <Sparkles size={14} aria-hidden="true" />
                <strong>Kontrol edilebilir noktalar</strong>
              </div>
              <ul className="ai-insight-list">
                {data.suggestions.map((item, index) => (
                  <li key={`suggestion-${index}`}>{item}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title="Son yorum" content={data.finalComment} />
        </div>
      ) : null}
    </AiCard>
  );
}

function AnalysisBlock({ icon, title, content }) {
  if (!content) {
    return null;
  }

  return (
    <div className="ai-unified-section">
      <div className="ai-unified-section-head">
        {icon}
        <strong>{title}</strong>
      </div>
      <p>{content}</p>
    </div>
  );
}

function ListBlock({ title, items, tone = "neutral" }) {
  const rows = Array.isArray(items) ? items.filter(Boolean) : [];
  if (rows.length === 0) {
    return null;
  }

  return (
    <div className={`ai-fundamental-list ai-comparison-list ai-comparison-list--${tone}`}>
      <strong>{title}</strong>
      <ul>
        {rows.map((item, index) => (
          <li key={`${title}-${index}`}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function formatDataQuality(value) {
  switch (String(value || "").toUpperCase()) {
    case "COMPLETE":
      return "yuksek";
    case "PARTIAL":
      return "orta";
    case "LIMITED":
    case "LOW":
    case "INSUFFICIENT":
      return "dusuk";
    default:
      return "bilinmiyor";
  }
}
