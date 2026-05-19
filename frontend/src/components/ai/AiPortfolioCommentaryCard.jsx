import AiCard from "./AiCard";
import AiLockedCard from "./AiLockedCard";
import { useAuth } from "../../auth/AuthContext";
import { formatCurrency, formatNumber, formatPercent } from "../../utils/formatters";

export default function AiPortfolioCommentaryCard({ portfolio, compact = false }) {
  const { isAuthenticated, isPremium } = useAuth();
  const holdings = Array.isArray(portfolio?.holdings) ? portfolio.holdings : [];
  const summary = portfolio?.summary ?? {};
  const totalValue = toNumber(summary.currentValue ?? summary.totalCurrentValue);
  const profitLoss = toNumber(summary.profitLoss ?? summary.totalProfitLoss);
  const profitLossPercent = toNumber(summary.profitLossPercent);
  const topHolding = findTopHolding(holdings);
  const topWeight = totalValue > 0 && topHolding ? (toNumber(topHolding.currentValue) / totalValue) * 100 : null;
  const diversificationScore = holdings.length >= 6 ? "güçlü" : holdings.length >= 3 ? "orta" : "zayıf";
  const concentrationLabel = topWeight == null ? "veri yok" : topWeight > 35 ? "yüksek" : topWeight > 20 ? "orta" : "dengeli";
  const fallbackBullets = [
    `Pozisyon sayısı ${formatNumber(holdings.length, 0)} seviyesinde.`,
    `En büyük pozisyon ${topHolding?.instrumentCode || "-"}.`,
    `Toplam görünüm ${profitLoss >= 0 ? "pozitif" : "negatif"} tarafta.`,
    "Ağırlık dağılımı düzenli izlenmeli.",
  ];

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName="AI Portföy Yorumu"
        description="Portföy dağılımı ve risk yorumunu görmek için giriş yapın."
      />
    );
  }

  if (!isPremium) {
    return (
      <AiLockedCard
        featureName="AI Portföy Yorumu"
        description="Portföy dağılımı ve risk değerlendirmesi premium kullanıcılar için geçerlidir."
        requiresPremium
      />
    );
  }

  return (
    <AiCard
      title="AI Portföy Yorumu"
      subtitle={compact ? "Öne çıkan risk ve takip başlıkları" : "Pozisyon dağılımı ve risk değerlendirmesi"}
    >
      {portfolio ? (
        <div className={`ai-portfolio-commentary${compact ? " is-compact" : ""}`}>
          <div className="ai-metric-strip">
            <AiMiniMetric label="Pozisyon" value={formatNumber(holdings.length, 0)} />
            <AiMiniMetric label="En büyük" value={topHolding?.instrumentCode || "-"} />
            <AiMiniMetric label="Ağırlık" value={topWeight == null ? "-" : `${formatNumber(topWeight, 1)}%`} />
          </div>

          <ul className="ai-insight-list">
            <li>
              <strong>Yoğunlaşma riski:</strong> En büyük pozisyon payı {concentrationLabel}.
            </li>
            <li>
              <strong>En büyük pozisyon:</strong> {topHolding?.instrumentCode || "-"} {topHolding ? `ile ${formatCurrency(topHolding.currentValue)}` : "henüz oluşmadı"}.
            </li>
            <li>
              <strong>Çeşitlendirme:</strong> Portföy dağılımı {diversificationScore} seviyede.
            </li>
            <li>
              <strong>Takip önerisi:</strong> {formatCurrency(totalValue)} toplam değer içinde ağırlık ve fiyat güncelliği birlikte izlenmeli.
            </li>
            {!compact ? (
              <li>
                <strong>Getiri görünümü:</strong> Güncel toplam fark {formatCurrency(profitLoss)} ({formatPercent(profitLossPercent)}).
              </li>
            ) : null}
          </ul>
        </div>
      ) : (
        <div className={`ai-portfolio-commentary${compact ? " is-compact" : ""}`}>
          <ul className="ai-insight-list">
            {fallbackBullets.slice(0, compact ? 4 : 5).map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>
        </div>
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
