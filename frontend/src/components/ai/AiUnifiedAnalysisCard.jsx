import { useEffect, useState } from "react";
import { Sparkles, TrendingUp, AlertTriangle, Info } from "lucide-react";
import { useTranslation } from "react-i18next";
import { getAiUnifiedAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiUnifiedAnalysisCard({ symbol, instrumentType = "STOCK" }) {
  const { isAuthenticated, isPremium } = useAuth();
  const { t, i18n } = useTranslation();
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
        const result = await getAiUnifiedAnalysis(symbol, instrumentType, i18n.language);
        if (active) setData(result ?? null);
      } catch {
        if (active) {
          setData(null);
          setError(t("aiCards.unified.error"));
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => { active = false; };
  }, [instrumentType, isPremium, symbol, i18n.language, t]);

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName={t("aiCards.unified.title")}
        description={t("aiCards.unified.guestDescription")}
      />
    );
  }

  if (!isPremium) {
    return (
      <AiLockedCard
        featureName={t("aiCards.unified.title")}
        description={t("aiCards.unified.premiumDescription")}
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
            <h3>{t("aiCards.unified.title")}</h3>
            <p>{t("aiCards.unified.subtitle", { symbol: symbol || t("aiCards.asset") })}</p>
          </div>
        </div>
        <span className="ai-card-badge ai-unified-badge">PRO</span>
      </header>

      <div className="ai-card-body">
        {loading ? <UnifiedSkeleton t={t} /> : null}
        {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
        {!loading && !error && data && hasContent(data) ? <UnifiedContent data={data} t={t} /> : null}
        {!loading && !error && data && !hasContent(data) ? (
          <p className="ai-state-message">{t("aiCards.unified.noData")}</p>
        ) : null}
      </div>

      {data && hasContent(data) ? (
        <div className="ai-card-footer"><AiResponseMeta metadata={data.metadata} /></div>
      ) : null}

      <p className="ai-disclaimer">
        <Info size={13} aria-hidden="true" />
        <span>{t("aiCards.disclaimer")}</span>
      </p>
    </section>
  );
}

function UnifiedContent({ data, t }) {
  const hasHighlights = Array.isArray(data.highlights) && data.highlights.length > 0;
  const hasRisks = Array.isArray(data.risks) && data.risks.length > 0;

  return (
    <>
      {data.summary ? <p className="ai-unified-summary">{data.summary}</p> : null}

      {hasHighlights ? (
        <div className="ai-unified-section">
          <div className="ai-unified-section-head">
            <TrendingUp size={14} aria-hidden="true" />
            <strong>{t("aiCards.highlights")}</strong>
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
            <strong>{t("aiCards.risksList")}</strong>
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

function UnifiedSkeleton({ t }) {
  return (
    <div className="ai-unified-skeleton" aria-busy="true" aria-label={t("aiCards.unified.loading")}>
      <div className="ai-skeleton-line ai-skeleton-line--wide" />
      <div className="ai-skeleton-line" />
      <div className="ai-skeleton-line ai-skeleton-line--narrow" />
      <div className="ai-skeleton-line ai-skeleton-line--wide" style={{ marginTop: "0.75rem" }} />
      <div className="ai-skeleton-line" />
    </div>
  );
}
