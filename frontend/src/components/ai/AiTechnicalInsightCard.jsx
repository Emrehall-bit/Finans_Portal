import { useEffect, useState } from "react";
import { getAiTechnicalAnalysis } from "../../api/aiApi";
import AiCard from "./AiCard";

export default function AiTechnicalInsightCard({ symbol, availableData = {}, highRisk = false }) {
  const requestSymbol = availableData.quote?.symbol || availableData.quote?.code || symbol;
  const [comment, setComment] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!requestSymbol) {
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
          setError("AI teknik yorum şu anda üretilemedi.");
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
  }, [requestSymbol]);

  return (
    <AiCard title="AI Teknik Görünüm" subtitle={`${symbol || "Varlık"} için backend destekli teknik yorum`}>
      {loading ? <p className="ai-state-message">AI teknik analiz hazırlanıyor...</p> : null}
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
                <strong>Uyarı:</strong> Vadeli işlemler kaldıraç nedeniyle yüksek risk taşır.
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
    LOW: "Düşük",
    MEDIUM: "Orta",
    HIGH: "Yüksek",
  }[value] ?? "-";
}

function formatSignal(value) {
  return {
    POSITIVE: "Pozitif",
    NEUTRAL: "Nötr",
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
