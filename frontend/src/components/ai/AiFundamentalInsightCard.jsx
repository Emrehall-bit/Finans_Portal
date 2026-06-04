import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { getAiFundamentalAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiCard from "./AiCard";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiFundamentalInsightCard({ symbol, availableData = {} }) {
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

    async function loadAiFundamentalComment() {
      try {
        setLoading(true);
        setError("");
        const data = await getAiFundamentalAnalysis(requestSymbol, i18n.language);
        if (active) {
          setComment(data ?? null);
        }
      } catch {
        if (active) {
          setComment(null);
          setError(t("aiCards.fundamental.error"));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadAiFundamentalComment();

    return () => {
      active = false;
    };
  }, [isGuest, requestSymbol, i18n.language, t]);

  if (isGuest) {
    return (
      <AiLockedCard
        featureName={t("aiCards.fundamental.title")}
        description={t("aiCards.fundamental.guestDescription", { symbol: symbol || t("aiCards.company") })}
      />
    );
  }

  return (
    <AiCard
      title={t("aiCards.fundamental.title")}
      subtitle={t("aiCards.fundamental.subtitle", { symbol: symbol || t("aiCards.company") })}
      footer={comment ? <AiResponseMeta metadata={comment.metadata} /> : null}
    >
      {loading ? <p className="ai-state-message">{t("aiCards.fundamental.loading")}</p> : null}
      {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
      {!loading && !error && comment ? (
        <>
          <p>{comment.summary}</p>

          <div className="ai-metric-strip">
            <AiMiniMetric label={t("aiCards.health")} value={formatHealth(comment.financialHealth, t)} tone={healthTone(comment.financialHealth)} />
            <AiMiniMetric label={t("aiCards.strengths")} value={String(comment.strengths?.length ?? 0)} />
            <AiMiniMetric label={t("aiCards.risks")} value={String(comment.risks?.length ?? 0)} tone={(comment.risks?.length ?? 0) > 1 ? "is-risky" : ""} />
          </div>

          <div className="ai-fundamental-grid">
            <AiList title={t("aiCards.strengthsList")} items={comment.strengths} t={t} />
            <AiList title={t("aiCards.weaknessesList")} items={comment.weaknesses} t={t} />
            <AiList title={t("aiCards.risksList")} items={comment.risks} t={t} />
          </div>

          <p>
            <strong>{t("aiCards.growth")}:</strong> {comment.growthComment || t("aiCards.fundamental.noGrowthData")}
          </p>
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

function AiList({ title, items, t }) {
  const rows = Array.isArray(items) && items.length ? items : [t("aiCards.noData")];
  return (
    <div className="ai-fundamental-list">
      <strong>{title}</strong>
      <ul>
        {rows.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function formatHealth(value, t) {
  return {
    STRONG: t("aiCards.healthStrong"),
    STABLE: t("aiCards.healthStable"),
    WATCH:  t("aiCards.healthWatch"),
    RISKY:  t("aiCards.healthRisky"),
  }[value] ?? "-";
}

function healthTone(value) {
  if (value === "STRONG") return "is-positive";
  if (value === "RISKY")  return "is-risky";
  return "is-neutral";
}
