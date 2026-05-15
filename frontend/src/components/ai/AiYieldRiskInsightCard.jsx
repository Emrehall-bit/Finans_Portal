import AiCard from "./AiCard";
import { formatDateTime, formatNumber } from "../../utils/formatters";

export default function AiYieldRiskInsightCard({ symbol, availableData = {} }) {
  const quote = availableData.quote ?? {};
  const yieldValue = quote.price ?? quote.interestRate ?? quote.yield;
  const maturity = quote.maturityDate ?? quote.vade ?? quote.maturity ?? null;
  const creditRisk = quote.creditRisk ?? quote.rating ?? null;

  return (
    <AiCard title="AI Getiri ve Risk Yorumu" subtitle={`${symbol || "Tahvil"} için faiz riski özeti`}>
      <p>
        Tahvil/bono getirisi <strong>{formatYield(yieldValue)}</strong>. Vade bilgisi{" "}
        <strong>{maturity ? formatDateTime(maturity) : "belirtilmedi"}</strong>.
      </p>

      <div className="ai-metric-strip">
        <AiMiniMetric label="Getiri" value={formatYield(yieldValue)} />
        <AiMiniMetric label="Vade" value={maturity ? formatDateTime(maturity) : "-"} />
        <AiMiniMetric label="Kredi riski" value={creditRisk || "-"} />
      </div>

      <ul className="ai-insight-list">
        <li>
          <strong>Faiz riski:</strong> Piyasa faizleri yükselirse tahvil fiyatı baskılanabilir.
        </li>
        <li>
          <strong>Vade:</strong> Uzun vade, faiz değişimlerine duyarlılığı artırır.
        </li>
        <li>
          <strong>Kredi riski:</strong> İhraççı kalitesi ve likidite koşulları ayrıca izlenmelidir.
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

function formatYield(value) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  return `${formatNumber(numeric, 2)}%`;
}
