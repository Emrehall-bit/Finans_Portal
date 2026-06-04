import { AlertTriangle, ShieldAlert, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { getPortfolioAiAnalysis } from "../../api/aiApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useAuth } from "../../auth/AuthContext";
import AiCard from "./AiCard";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiPortfolioAnalysisCard({ portfolioId, portfolioName, holdingsCount = 0 }) {
  const { isAuthenticated, isPremium } = useAuth();
  const { t, i18n } = useTranslation();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const canUse = isPremium;
  const hasPortfolio = Boolean(portfolioId);
  const isEmptyPortfolio = Number(holdingsCount || 0) === 0;
  const title = useMemo(
    () => portfolioName
      ? t("aiCards.portfolio.subtitleNamed", { name: portfolioName })
      : t("aiCards.portfolio.subtitle"),
    [portfolioName, t],
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
      const response = await getPortfolioAiAnalysis(portfolioId, i18n.language);
      setData(response ?? null);
    } catch (requestError) {
      setData(null);
      setError(extractErrorMessage(requestError, t("aiCards.portfolio.error")));
    } finally {
      setLoading(false);
    }
  }

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName={t("aiCards.portfolio.title")}
        description={t("aiCards.portfolio.guestDescription")}
      />
    );
  }

  if (!canUse) {
    return (
      <AiLockedCard
        featureName={t("aiCards.portfolio.title")}
        description={t("aiCards.portfolio.premiumDescription")}
        requiresPremium
      />
    );
  }

  return (
    <AiCard
      title={t("aiCards.portfolio.title")}
      subtitle={title}
      badge="PRO"
      className="ai-portfolio-analysis-card"
      footer={data ? <AiResponseMeta metadata={data.metadata} /> : null}
    >
      <div className="ai-portfolio-analysis-toolbar">
        <div className="ai-portfolio-analysis-intro">
          <span className="summary-chip">Pozisyon: {Number(holdingsCount || 0)}</span>
          <p>{t("aiCards.portfolio.onDemandNote")}</p>
        </div>
        <button type="button" className="ai-comparison-action" onClick={handleAnalyze} disabled={!hasPortfolio || loading}>
          {loading ? t("aiCards.portfolio.analyzing") : t("aiCards.portfolio.analyzeButton")}
        </button>
      </div>

      {error ? <p className="ai-state-message error">{error}</p> : null}

      {!loading && !error && !data ? (
        <div className="ai-portfolio-analysis-placeholder">
          <Sparkles size={16} aria-hidden="true" />
          <p>
            {isEmptyPortfolio
              ? t("aiCards.portfolio.emptyPlaceholder")
              : t("aiCards.portfolio.readyPlaceholder")}
          </p>
        </div>
      ) : null}

      {!loading && !error && data ? (
        <div className="ai-portfolio-analysis-content">
          {data.dataQuality !== "COMPLETE" ? (
            <div className="ai-comparison-warning">
              <AlertTriangle size={14} aria-hidden="true" />
              <span>{t("aiCards.portfolio.dataQualityWarning", { quality: formatDataQuality(data.dataQuality, t) })}</span>
            </div>
          ) : null}

          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title={t("aiCards.portfolio.summary")} content={data.summary} />
          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title={t("aiCards.portfolio.allocation")} content={data.allocationComment} />
          <AnalysisBlock icon={<ShieldAlert size={14} aria-hidden="true" />} title={t("aiCards.portfolio.riskView")} content={data.riskComment} />
          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title={t("aiCards.portfolio.diversification")} content={data.diversificationComment} />

          <div className="ai-comparison-grid">
            <ListBlock title={t("aiCards.portfolio.strongPositions")} items={data.strongestPositions} tone="positive" />
            <ListBlock title={t("aiCards.portfolio.weakPositions")} items={data.weakestPositions} tone="risk" />
          </div>

          {Array.isArray(data.riskSignals) && data.riskSignals.length > 0 ? (
            <div className="ai-portfolio-analysis-signals">
              <div className="ai-unified-section-head">
                <ShieldAlert size={14} aria-hidden="true" />
                <strong>{t("aiCards.portfolio.riskSignals")}</strong>
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
                <strong>{t("aiCards.portfolio.suggestions")}</strong>
              </div>
              <ul className="ai-insight-list">
                {data.suggestions.map((item, index) => (
                  <li key={`suggestion-${index}`}>{item}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <AnalysisBlock icon={<Sparkles size={14} aria-hidden="true" />} title={t("aiCards.portfolio.finalComment")} content={data.finalComment} />
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

function formatDataQuality(value, t) {
  switch (String(value || "").toUpperCase()) {
    case "COMPLETE":     return t("aiCards.portfolio.qualityHigh");
    case "PARTIAL":      return t("aiCards.portfolio.qualityMedium");
    case "LIMITED":
    case "LOW":
    case "INSUFFICIENT": return t("aiCards.portfolio.qualityLow");
    default:             return t("aiCards.portfolio.qualityUnknown");
  }
}
