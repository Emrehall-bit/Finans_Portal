import AiCard from "./AiCard";
import { formatNumber } from "../../utils/formatters";

export default function AiFundInsightCard({ symbol, availableData = {} }) {
  const quote = availableData.quote ?? {};
  const fundType = quote.fonTurAciklama || quote.fundType || "Fon türü belirtilmedi";
  const riskLevel = quote.riskDegeri ?? quote.riskLevel ?? "-";
  const recentReturn = quote.getiri1a ?? quote.return1m ?? quote.changeRate;
  const managementFee = quote.managementFee ?? quote.yonetimUcreti ?? null;
  const allocation = quote.portfolioDistribution || quote.allocation || null;

  return (
    <AiCard title="AI Fon Yorumu" subtitle={`${symbol || "Fon"} için fon verisi özeti`}>
      <p>
        Fon türü <strong>{fundType}</strong>. Risk seviyesi <strong>{riskLevel}</strong>, son getiri göstergesi{" "}
        <strong>{formatPercentLike(recentReturn)}</strong> olarak izleniyor.
      </p>

      <div className="ai-metric-strip">
        <AiMiniMetric label="Son getiri" value={formatPercentLike(recentReturn)} />
        <AiMiniMetric label="Risk" value={String(riskLevel)} />
        <AiMiniMetric label="Yönetim ücreti" value={managementFee == null ? "-" : formatPercentLike(managementFee)} />
      </div>

      <ul className="ai-insight-list">
        <li>
          <strong>Fon türü:</strong> {fundType}
        </li>
        <li>
          <strong>Dağılım:</strong> {allocation ? formatAllocation(allocation) : "Portföy dağılımı verisi bu ekranda yok"}
        </li>
        <li>
          <strong>Takip:</strong> Risk seviyesi, fon türü ve dönemsel getiriler birlikte değerlendirilmelidir.
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

function formatPercentLike(value) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  return `${formatNumber(numeric, 2)}%`;
}

function formatAllocation(allocation) {
  if (typeof allocation === "string") return allocation;
  if (!allocation || typeof allocation !== "object") return "-";
  return Object.entries(allocation)
    .slice(0, 3)
    .map(([key, value]) => `${key}: ${formatPercentLike(value)}`)
    .join(", ");
}
