import AiCard from "./AiCard";
import { formatCurrency, formatNumber } from "../../utils/formatters";

export default function AiMacroInsightCard({ symbol, availableData = {} }) {
  const quote = availableData.quote ?? {};
  const changeRate = Number(quote.changeRate);
  const volatility = Number.isFinite(changeRate)
    ? Math.abs(changeRate) > 1
      ? "yüksek"
      : Math.abs(changeRate) > 0.3
        ? "orta"
        : "düşük"
    : "belirsiz";

  return (
    <AiCard title="AI Makro Kur Yorumu" subtitle={`${symbol || "Döviz"} için makro görünüm`}>
      <p>
        Kısa vadeli kur görünümü <strong>{availableData.trendLabel || "nötr"}</strong>. Güncel seviye{" "}
        <strong>{formatCurrency(quote.price ?? quote.sellRate ?? quote.buyRate, quote.currency || "TRY")}</strong>,
        oynaklık <strong>{volatility}</strong>.
      </p>

      <div className="ai-metric-strip">
        <AiMiniMetric label="Alış" value={formatCurrency(quote.buyRate, quote.currency || "TRY")} />
        <AiMiniMetric label="Satış" value={formatCurrency(quote.sellRate ?? quote.price, quote.currency || "TRY")} />
        <AiMiniMetric label="Günlük" value={Number.isFinite(changeRate) ? `${formatNumber(changeRate, 2)}%` : "-"} />
      </div>

      <ul className="ai-insight-list">
        <li>
          <strong>Makro etki:</strong> TCMB, Fed ve enflasyon verileri kur yönünde belirleyici olabilir.
        </li>
        <li>
          <strong>Teknik trend:</strong> Teknik yorum sınırlı tutulmalı; haber akışı kurda ani hareket yaratabilir.
        </li>
        <li>
          <strong>Risk:</strong> Veri saati ve merkez bankası açıklamalarında spread ve oynaklık artabilir.
        </li>
      </ul>
    </AiCard>
  );
}

function AiMiniMetric({ label, value }) {
  return (
    <span className="ai-mini-metric">
      <small>{label}</small>
      <strong>{value}</strong>
    </span>
  );
}
