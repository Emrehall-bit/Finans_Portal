import AiCard from "./AiCard";
import { formatNumber } from "../../utils/formatters";

export default function AiRiskSummaryCard({ instrumentType, symbol, availableData = {}, highRisk = false }) {
  const quote = availableData.quote ?? {};
  const changeRate = Number(quote.changeRate);
  const absChange = Number.isFinite(changeRate) ? Math.abs(changeRate) : null;
  const riskTone = highRisk
    ? "yüksek"
    : absChange === null
      ? "belirsiz"
      : absChange >= 5
        ? "yüksek"
        : absChange >= 2
          ? "orta"
          : "düşük-orta";

  return (
    <AiCard title="Risk Sinyali" subtitle={`${symbol || "Varlık"} için kısa risk görünümü`} badge="Sinyal">
      <p>
        {formatInstrumentType(instrumentType)} için mevcut risk görünümü <strong>{riskTone}</strong>. Günlük değişim{" "}
        <strong>{absChange === null ? "-" : `${formatNumber(changeRate, 2)}%`}</strong> seviyesinde.
      </p>

      <ul className="ai-insight-list">
        <li>
          <strong>Likidite:</strong> Fiyat güncelliği ve işlem hacmi pozisyon kararlarında dikkate alınmalı.
        </li>
        <li>
          <strong>Oynaklık:</strong> Kısa vadeli sert hareketler stop ve pozisyon büyüklüğü yönetimi gerektirir.
        </li>
        {highRisk ? (
          <li>
            <strong>Yüksek risk uyarısı:</strong> Vadeli işlemler kaldıraç, teminat ve vade riski içerir.
          </li>
        ) : (
          <li>
            <strong>Takip:</strong> Haber akışı ve piyasa geneli risk iştahı birlikte izlenmeli.
          </li>
        )}
      </ul>
    </AiCard>
  );
}

function formatInstrumentType(type) {
  return {
    STOCK: "Hisse",
    CRYPTO: "Kripto",
    FUTURES: "Vadeli işlem",
  }[type] ?? "Varlık";
}
