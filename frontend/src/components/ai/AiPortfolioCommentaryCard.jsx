import AiCard from "./AiCard";
import { formatCurrency, formatNumber, formatPercent } from "../../utils/formatters";

export default function AiPortfolioCommentaryCard({ portfolio }) {
  const holdings = Array.isArray(portfolio?.holdings) ? portfolio.holdings : [];
  const summary = portfolio?.summary ?? {};
  const totalValue = toNumber(summary.currentValue ?? summary.totalCurrentValue);
  const profitLoss = toNumber(summary.profitLoss ?? summary.totalProfitLoss);
  const profitLossPercent = toNumber(summary.profitLossPercent);
  const topHolding = findTopHolding(holdings);
  const topWeight = totalValue > 0 && topHolding ? (toNumber(topHolding.currentValue) / totalValue) * 100 : null;
  const concentrationLabel = topWeight === null
    ? "Yeterli veri yok"
    : topWeight > 35
      ? "yüksek"
      : topWeight > 20
        ? "orta"
        : "dengeli";

  return (
    <AiCard title="AI Portföy Yorumu" subtitle="Pozisyon dağılımı ve risk değerlendirmesi">
      {portfolio ? (
        <>
          <p>
            Portföyde <strong>{formatNumber(holdings.length, 0)} pozisyon</strong> bulunuyor. Toplam değer{" "}
            <strong>{formatCurrency(totalValue)}</strong>; güncel kâr/zarar{" "}
            <strong className={profitLoss >= 0 ? "market-up" : "market-down"}>
              {formatCurrency(profitLoss)} ({formatPercent(profitLossPercent)})
            </strong>.
          </p>

          <div className="ai-metric-strip">
            <AiMiniMetric label="Pozisyon" value={formatNumber(holdings.length, 0)} />
            <AiMiniMetric label="En büyük pay" value={topHolding?.instrumentCode || "-"} />
            <AiMiniMetric label="Ağırlık" value={topWeight === null ? "-" : `${formatNumber(topWeight, 1)}%`} />
          </div>

          <ul className="ai-insight-list">
            <li>
              <strong>Yoğunlaşma:</strong> En büyük pozisyon payı {concentrationLabel} seviyede.
            </li>
            <li>
              <strong>Çeşitlendirme:</strong> Farklı sektör ve varlık tipleri riskin dengelenmesine yardımcı olur.
            </li>
            <li>
              <strong>Takip:</strong> Kâr/zarar kadar pozisyon ağırlıkları ve fiyat güncelliği de izlenmeli.
            </li>
          </ul>
        </>
      ) : (
        <p>Portföy seçildiğinde pozisyon dağılımı ve risk odağı için kısa AI yorumu burada görünecek.</p>
      )}
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

function findTopHolding(holdings) {
  return [...holdings].sort((a, b) => toNumber(b.currentValue) - toNumber(a.currentValue))[0] ?? null;
}

function toNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}
