import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { getAiTechnicalAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiCard from "./AiCard";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiTechnicalInsightCard({ symbol, availableData = {}, highRisk = false }) {
  const { isAuthenticated } = useAuth();
  const { t, i18n } = useTranslation();
  const requestSymbol = availableData.quote?.symbol || availableData.quote?.code || symbol;
  const [comment, setComment] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const isGuest = !isAuthenticated;

  useEffect(() => {
    if (!requestSymbol || isGuest) {
      setComment(null);
      setError("");
      setLoading(false);
      return;
    }

    let active = true;

    async function loadAiTechnicalComment() {
      try {
        setLoading(true);
        setError("");
        const data = await getAiTechnicalAnalysis(requestSymbol, i18n.language);
        if (active) {
          setComment(data ?? null);
        }
      } catch {
        if (active) {
          setComment(null);
          setError(t("aiCards.technical.error"));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadAiTechnicalComment();

    return () => {
      active = false;
    };
  }, [isGuest, requestSymbol, i18n.language, t]);

  if (isGuest) {
    return (
      <AiLockedCard
        featureName={t("aiCards.technical.title")}
        description={t("aiCards.technical.guestDescription", { symbol: symbol || t("aiCards.asset") })}
      />
    );
  }

  return (
    <AiCard
      title={t("aiCards.technical.title")}
      subtitle={t("aiCards.technical.subtitle", { symbol: symbol || t("aiCards.asset") })}
      footer={comment ? <AiResponseMeta metadata={comment.metadata} /> : null}
    >
      {loading ? <p className="ai-state-message">{t("aiCards.technical.loading")}</p> : null}
      {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
      {!loading && !error && comment ? (
        <>
          {comment.keyObservation ? (
            <div className="ai-key-observation">
              <small>{t("aiCards.keyObservation")}</small>
              <p>{comment.keyObservation}</p>
            </div>
          ) : null}

          <p>{comment.summary}</p>

          <div className="ai-metric-strip">
            <AiMiniMetric label={t("aiCards.risk")} value={formatRisk(comment.riskLevel, t)} tone={riskTone(comment.riskLevel)} />
            <AiMiniMetric label={t("aiCards.signal")} value={formatSignal(comment.signal, t)} tone={signalTone(comment.signal)} />
            <AiMiniMetric label={t("aiCards.symbol")} value={comment.symbol || requestSymbol || "-"} />
          </div>

          <ul className="ai-insight-list">
            <li>
              <strong>{t("aiCards.trend")}:</strong> {comment.trendComment}
            </li>
            <li>
              <strong>{t("aiCards.momentum")}:</strong> {comment.momentumComment}
            </li>
            {highRisk ? (
              <li>
                <strong>{t("aiCards.warning")}:</strong> {t("aiCards.technical.futuresWarning")}
              </li>
            ) : null}
          </ul>
        </>
      ) : null}
    </AiCard>
  );
}

function AiMiniMetric({ label, value, tone = "" }) {
  return (
    <span className={`ai-mini-metric ${tone}`}>
      <small>{label}</small>
      <strong>{value}</strong>
    </span>
  );
}

function formatRisk(value, t) {
  return { LOW: t("aiCards.riskLow"), MEDIUM: t("aiCards.riskMedium"), HIGH: t("aiCards.riskHigh") }[value] ?? "-";
}

function formatSignal(value, t) {
  return {
    POSITIVE: t("aiCards.signalPositive"),
    NEUTRAL:  t("aiCards.signalNeutral"),
    NEGATIVE: t("aiCards.signalNegative"),
    RISKY:    t("aiCards.signalRisky"),
  }[value] ?? "-";
}

function riskTone(value) {
  return value === "HIGH" ? "is-risky" : value === "LOW" ? "is-positive" : "is-neutral";
}

function signalTone(value) {
  if (value === "POSITIVE") return "is-positive";
  if (value === "NEGATIVE" || value === "RISKY") return "is-risky";
  return "is-neutral";
}
