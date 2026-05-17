import { useEffect, useState } from "react";
import { getAiFundamentalAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiCard from "./AiCard";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiFundamentalInsightCard({ symbol, availableData = {} }) {
  const { isAuthenticated } = useAuth();
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
        const data = await getAiFundamentalAnalysis(requestSymbol);
        if (active) {
          setComment(data ?? null);
        }
      } catch {
        if (active) {
          setComment(null);
          setError("AI finansal degerlendirme su anda uretilemedi.");
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
  }, [isGuest, requestSymbol]);

  if (isGuest) {
    return (
      <AiLockedCard
        featureName="AI Finansal Degerlendirme"
        description={`${symbol || "Sirket"} icin temel AI yorumunu gormek uzere giris yapin.`}
      />
    );
  }

  return (
    <AiCard
      title="AI Finansal Degerlendirme"
      subtitle={`${symbol || "Sirket"} icin backend destekli finansal yorum`}
      footer={comment ? <AiResponseMeta metadata={comment.metadata} /> : null}
    >
      {loading ? <p className="ai-state-message">AI finansal degerlendirme hazirlaniyor...</p> : null}
      {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
      {!loading && !error && comment ? (
        <>
          <p>{comment.summary}</p>

          <div className="ai-metric-strip">
            <AiMiniMetric label="Saglik" value={formatHealth(comment.financialHealth)} tone={healthTone(comment.financialHealth)} />
            <AiMiniMetric label="Guclu Yan" value={String(comment.strengths?.length ?? 0)} />
            <AiMiniMetric label="Risk" value={String(comment.risks?.length ?? 0)} tone={(comment.risks?.length ?? 0) > 1 ? "is-risky" : ""} />
          </div>

          <div className="ai-fundamental-grid">
            <AiList title="Guclu Yanlar" items={comment.strengths} />
            <AiList title="Zayif Yanlar" items={comment.weaknesses} />
            <AiList title="Riskler" items={comment.risks} />
          </div>

          <p>
            <strong>Buyume:</strong> {comment.growthComment || "Yeterli buyume verisi yok."}
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

function AiList({ title, items }) {
  const rows = Array.isArray(items) && items.length ? items : ["Veri yok"];
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

function formatHealth(value) {
  return {
    STRONG: "Guclu",
    STABLE: "Dengeli",
    WATCH: "Izle",
    RISKY: "Riskli",
  }[value] ?? "-";
}

function healthTone(value) {
  if (value === "STRONG") return "is-positive";
  if (value === "RISKY") return "is-risky";
  return "is-neutral";
}
