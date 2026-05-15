import { useEffect, useState } from "react";
import { getAiFundamentalAnalysis } from "../../api/aiApi";
import AiCard from "./AiCard";

export default function AiFundamentalInsightCard({ symbol, availableData = {} }) {
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
          setError("AI finansal değerlendirme şu anda üretilemedi.");
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
  }, [requestSymbol]);

  return (
    <AiCard title="AI Finansal Değerlendirme" subtitle={`${symbol || "Şirket"} için backend destekli finansal yorum`}>
      {loading ? <p className="ai-state-message">AI finansal değerlendirme hazırlanıyor...</p> : null}
      {!loading && error ? <p className="ai-state-message error">{error}</p> : null}
      {!loading && !error && comment ? (
        <>
          <p>{comment.summary}</p>

          <div className="ai-metric-strip">
            <AiMiniMetric label="Sağlık" value={formatHealth(comment.financialHealth)} tone={healthTone(comment.financialHealth)} />
            <AiMiniMetric label="Güçlü Yan" value={String(comment.strengths?.length ?? 0)} />
            <AiMiniMetric label="Risk" value={String(comment.risks?.length ?? 0)} tone={(comment.risks?.length ?? 0) > 1 ? "is-risky" : ""} />
          </div>

          <div className="ai-fundamental-grid">
            <AiList title="Güçlü Yanlar" items={comment.strengths} />
            <AiList title="Zayıf Yanlar" items={comment.weaknesses} />
            <AiList title="Riskler" items={comment.risks} />
          </div>

          <p>
            <strong>Büyüme:</strong> {comment.growthComment || "Yeterli büyüme verisi yok."}
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
    STRONG: "Güçlü",
    STABLE: "Dengeli",
    WATCH: "İzle",
    RISKY: "Riskli",
  }[value] ?? "-";
}

function healthTone(value) {
  if (value === "STRONG") return "is-positive";
  if (value === "RISKY") return "is-risky";
  return "is-neutral";
}
