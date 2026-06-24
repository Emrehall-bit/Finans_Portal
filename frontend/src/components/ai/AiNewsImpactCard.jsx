import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AlertTriangle,
  Brain,
  ChevronDown,
  Info,
  Newspaper,
  Sparkles,
  TrendingUp,
} from "lucide-react";
import { getAiNewsImpactAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiNewsImpactCard({ newsId, autoLoad = false }) {
  const { isAuthenticated, isPremium } = useAuth();
  const { t, i18n } = useTranslation();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [expanded, setExpanded] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);

  useEffect(() => {
    if (!autoLoad || !isPremium || !newsId) return;
    let cancelled = false;
    async function load() {
      try {
        setLoading(true);
        setError("");
        const result = await getAiNewsImpactAnalysis(newsId, i18n.language);
        if (!cancelled) { setData(result ?? null); setHasLoaded(true); }
      } catch {
        if (!cancelled) { setData(null); setError(t("aiCards.newsImpact.error")); }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [autoLoad, isPremium, newsId]);

  async function handleToggle() {
    const nextExpanded = !expanded;
    setExpanded(nextExpanded);

    if (!nextExpanded || !newsId || !isPremium || hasLoaded || loading) {
      return;
    }

    try {
      setLoading(true);
      setError("");
      const result = await getAiNewsImpactAnalysis(newsId, i18n.language);
      setData(result ?? null);
      setHasLoaded(true);
    } catch {
      setData(null);
      setError(t("aiCards.newsImpact.error"));
    } finally {
      setLoading(false);
    }
  }

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName={t("aiCards.newsImpact.title")}
        description={t("aiCards.newsImpact.guestDescription")}
      />
    );
  }

  if (!isPremium) {
    return (
      <AiLockedCard
        featureName={t("aiCards.newsImpact.title")}
        description={t("aiCards.newsImpact.premiumDescription")}
        requiresPremium
      />
    );
  }

  if (autoLoad) {
    const showLoading = loading || (!hasLoaded && !error);
    return (
      <div className="ai-news-impact-drawer-content">
        {showLoading ? <ImpactSkeleton t={t} /> : null}
        {!showLoading && error ? <p className="ai-state-message error">{error}</p> : null}
        {!showLoading && !error && data ? <ImpactContent data={data} t={t} /> : null}
        {data ? (
          <div className="ai-card-footer">
            <AiResponseMeta metadata={data.metadata} />
          </div>
        ) : null}
        <p className="ai-disclaimer">
          <Info size={13} aria-hidden="true" />
          <span>{t("aiCards.disclaimer")}</span>
        </p>
      </div>
    );
  }

  return (
    <section className={`ai-card ai-news-impact-card ai-news-impact-card--toggle${expanded ? " is-expanded" : ""}`}>
      <div className="ai-card-glow ai-news-impact-glow" aria-hidden="true" />

      <button
        type="button"
        className={`ai-news-impact-trigger${!hasLoaded && !expanded ? " is-cta" : ""}`}
        onClick={handleToggle}
      >
        {!hasLoaded && !expanded ? (
          <div className="ai-news-impact-cta-layout">
            <div className="ai-news-impact-cta-header">
              <span className="ai-card-icon ai-news-impact-icon" aria-hidden="true">
                <Sparkles size={16} />
              </span>
              <h3>{t("aiCards.newsImpact.startTitle")}</h3>
            </div>
            <p className="ai-news-impact-cta-desc">{t("aiCards.newsImpact.startSubtitle")}</p>
            <div className="ai-news-impact-cta-btn" aria-hidden="true">
              <span>{t("aiCards.newsImpact.startBtnLabel")}</span>
            </div>
          </div>
        ) : (
          <>
            <div className="ai-news-impact-trigger-main">
              <span className="ai-card-icon ai-news-impact-icon" aria-hidden="true">
                {hasLoaded ? <Brain size={17} /> : <Sparkles size={17} />}
              </span>
              <div>
                <h3>{hasLoaded ? t("aiCards.newsImpact.title") : t("aiCards.newsImpact.startTitle")}</h3>
                <p>{hasLoaded ? t("aiCards.newsImpact.readySubtitle") : t("aiCards.newsImpact.startSubtitle")}</p>
              </div>
            </div>
            <div className="ai-news-impact-trigger-actions">
              <span className="ai-card-badge ai-news-impact-badge">
                {hasLoaded ? t("aiCards.newsImpact.readyBadge") : t("aiCards.newsImpact.aiBadge")}
              </span>
              <ChevronDown
                size={18}
                className={`ai-news-impact-chevron${expanded ? " is-expanded" : ""}`}
                aria-hidden="true"
              />
            </div>
          </>
        )}
      </button>

      <div className={`ai-news-impact-collapsible${expanded ? " is-expanded" : ""}`}>
        <div className="ai-card-body ai-news-impact-collapsible-inner">
          {loading ? <ImpactSkeleton t={t} /> : null}
          {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
          {!loading && !error && data ? <ImpactContent data={data} t={t} /> : null}
        </div>

        {data ? (
          <div className="ai-card-footer">
            <AiResponseMeta metadata={data.metadata} />
          </div>
        ) : null}

        <p className="ai-disclaimer">
          <Info size={13} aria-hidden="true" />
          <span>{t("aiCards.disclaimer")}</span>
        </p>
      </div>
    </section>
  );
}

function ImpactContent({ data, t }) {
  const financialContext = optionalText(data.financialContext || data.summary);
  const marketImplication = optionalText(data.marketImplication || data.marketImpact);
  const shortTermImpact = optionalImpact(data.shortTermImpact);
  const mediumTermImpact = optionalImpact(data.mediumTermImpact);
  const uncertainty = optionalText(data.uncertainty);
  const affectedAssets = normalizeAssets(
    Array.isArray(data.affectedAssets) && data.affectedAssets.length > 0
      ? data.affectedAssets
      : []
  );
  const watchIndicators = normalizeList(data.watchIndicators);
  const highlights = normalizeList(data.highlights);

  return (
    <>
      {financialContext ? (
        <ImpactSection icon={<Info size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.financialContext")}>
          <p className="ai-impact-text">{financialContext}</p>
        </ImpactSection>
      ) : null}

      {marketImplication ? (
        <ImpactSection icon={<TrendingUp size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.marketImplication")}>
          <p className="ai-impact-text">{marketImplication}</p>
        </ImpactSection>
      ) : null}

      {shortTermImpact ? (
        <ImpactSection icon={<TrendingUp size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.shortTermImpact")}>
          <p className="ai-impact-text">{shortTermImpact}</p>
        </ImpactSection>
      ) : null}

      {mediumTermImpact ? (
        <ImpactSection icon={<TrendingUp size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.mediumTermImpact")}>
          <p className="ai-impact-text">{mediumTermImpact}</p>
        </ImpactSection>
      ) : null}

      {affectedAssets.length > 0 ? (
        <ImpactSection icon={<AlertTriangle size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.affectedAssets")}>
          <div className="ai-sector-chips">
            {affectedAssets.map((item, i) => (
              <span key={i} className="ai-sector-chip">{item}</span>
            ))}
          </div>
        </ImpactSection>
      ) : null}

      {watchIndicators.length > 0 ? (
        <ImpactSection icon={<Info size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.watchIndicators")}>
          <ul className="ai-insight-list">
            {watchIndicators.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </ImpactSection>
      ) : null}

      {highlights.length > 0 ? (
        <ImpactSection icon={<Newspaper size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.highlights")}>
          <ul className="ai-insight-list">
            {highlights.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </ImpactSection>
      ) : null}

      {uncertainty ? (
        <ImpactSection icon={<Info size={14} aria-hidden="true" />} title={t("aiCards.newsImpact.uncertainty")}>
          <p className="ai-impact-text">{uncertainty}</p>
        </ImpactSection>
      ) : null}
    </>
  );
}

function ImpactSection({ icon, title, children }) {
  return (
    <div className="ai-impact-section">
      <div className="ai-impact-section-head">
        {icon}
        <strong>{title}</strong>
      </div>
      {children}
    </div>
  );
}

function ImpactSkeleton({ t }) {
  return (
    <div className="ai-unified-skeleton" aria-busy="true" aria-label={t("aiCards.newsImpact.loading")}>
      <div className="ai-skeleton-line ai-skeleton-line--narrow" style={{ width: "40%", marginBottom: "0.75rem" }} />
      <div className="ai-skeleton-line ai-skeleton-line--wide" />
      <div className="ai-skeleton-line" />
      <div className="ai-skeleton-line ai-skeleton-line--wide" style={{ marginTop: "0.75rem" }} />
      <div className="ai-skeleton-line ai-skeleton-line--narrow" />
    </div>
  );
}

function normalizeList(value) {
  if (!Array.isArray(value)) return [];
  return value.map(optionalText).filter(Boolean).slice(0, 3);
}

function normalizeAssets(value) {
  if (!Array.isArray(value)) return [];
  const result = [];
  value.forEach((item) => {
    const cleaned = normalizeAssetLabel(item);
    if (cleaned && !result.includes(cleaned)) {
      result.push(cleaned);
    }
  });
  return result.slice(0, 3);
}

function normalizeAssetLabel(value) {
  let text = repairMojibake(String(value || ""));
  text = text.replace(/→|->|â†’/g, "|");
  text = text.split("|")[0].replace(/\s+/g, " ").trim();
  if (!text || text.length > 60 || hasBrokenEncoding(text)) return "";
  return text;
}

function optionalImpact(value) {
  const text = optionalText(value);
  if (!text) return "";
  const lower = text.toLocaleLowerCase("tr-TR");
  const weakPhrases = [
    "belirgin kısa vadeli etki",
    "belirgin kisa vadeli etki",
    "orta vadeli etki için ek gelişme",
    "orta vadeli etki icin ek gelisme",
    "saptanamıyor",
    "saptanamiyor",
  ];
  return weakPhrases.some((phrase) => lower.includes(phrase)) ? "" : text;
}

function optionalText(value) {
  if (value == null) return "";
  const text = repairMojibake(String(value)).replace(/\s+/g, " ").trim();
  if (!text || hasBrokenEncoding(text)) return "";
  return text;
}

function repairMojibake(value) {
  return value
    .replaceAll("FÄ°NANSAL SEKTÃ–R", "Finansal sektör")
    .replaceAll("GENEL PÄ°YASA", "Genel piyasa")
    .replaceAll("KRÄ°PTO VARLIKLARÄ±", "Kripto varlıklar")
    .replaceAll("Ä°", "İ")
    .replaceAll("Ä±", "ı")
    .replaceAll("Ã¶", "ö")
    .replaceAll("Ã¼", "ü")
    .replaceAll("Ã§", "ç")
    .replaceAll("ÅŸ", "ş")
    .replaceAll("ÄŸ", "ğ")
    .replaceAll("Ã–", "Ö")
    .replaceAll("Ãœ", "Ü")
    .replaceAll("Ã‡", "Ç")
    .replaceAll("Åž", "Ş")
    .replaceAll("Äž", "Ğ")
    .replaceAll("â€™", "'")
    .replaceAll("â€“", "-")
    .replaceAll("â€”", "-")
    .replaceAll("â†’", "→");
}

function hasBrokenEncoding(value) {
  return /Ã|Ä|Å|�/.test(value);
}
