import { useEffect, useState } from "react";
import { getAiTechnicalAnalysis } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiCard from "./AiCard";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiTechnicalInsightCard({ symbol, availableData = {}, highRisk = false }) {
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

    async function loadAiTechnicalComment() {
      try {
        setLoading(true);
        setError("");
        const data = await getAiTechnicalAnalysis(requestSymbol);
        if (active) {
          setComment(data ?? null);
        }
      } catch {
        if (active) {
          setComment(null);
          setError("AI teknik yorum su anda uretilemedi.");
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
  }, [isGuest, requestSymbol]);

  if (isGuest) {
    return (
      <AiLockedCard
        featureName="AI Teknik Gorunum"
        description={`${symbol || "Varlik"} icin kisa AI teknik yorumunu gormek uzere giris yapin.`}
      />
    );
  }

  return (
    <AiCard
      title="AI Teknik Gorunum"
      subtitle={`${symbol || "Varlik"} icin backend destekli teknik yorum`}
      footer={comment ? <AiResponseMeta metadata={comment.metadata} /> : null}
    >
      {loading ? <p className="ai-state-message">AI teknik analiz hazirlaniyor...</p> : null}
      {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
      {!loading && !error && comment ? (
        <>
          <p>{comment.summary}</p>

          <div className="ai-metric-strip">
            <AiMiniMetric label="Risk" value={formatRisk(comment.riskLevel)} tone={riskTone(comment.riskLevel)} />
            <AiMiniMetric label="Sinyal" value={formatSignal(comment.signal)} tone={signalTone(comment.signal)} />
            <AiMiniMetric label="Sembol" value={comment.symbol || requestSymbol || "-"} />
          </div>

          <ul className="ai-insight-list">
            <li>
              <strong>Trend:</strong> {comment.trendComment}
            </li>
            <li>
              <strong>Momentum:</strong> {comment.momentumComment}
            </li>
            {highRisk ? (
              <li>
                <strong>Uyari:</strong> Vadeli islemler kaldirac nedeniyle yuksek risk tasir.
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

function formatRisk(value) {
  return {
    LOW: "Dusuk",
    MEDIUM: "Orta",
    HIGH: "Yuksek",
  }[value] ?? "-";
}

function formatSignal(value) {
  return {
    POSITIVE: "Pozitif",
    NEUTRAL: "Notr",
    NEGATIVE: "Negatif",
    RISKY: "Riskli",
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
