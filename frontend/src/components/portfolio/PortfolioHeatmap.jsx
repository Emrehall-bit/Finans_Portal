import EmptyState from "../common/EmptyState";
import { formatCurrency, formatNumber, formatPercent } from "../../utils/formatters";

export default function PortfolioHeatmap({ items = [] }) {
  return (
    <section className="panel-surface portfolio-side-card portfolio-heatmap-card">
      <div className="panel-head portfolio-side-card-head">
        <div>
          <p className="eyebrow">Risk Yoğunluğu</p>
          <h3>Portföy Isı Haritası</h3>
        </div>
      </div>

      {items.length === 0 ? (
        <div className="portfolio-heatmap-empty">
          <EmptyState title="Pozisyon verisi yok" description="Değerlenebilen pozisyonlar oluştuğunda ısı haritası burada görünür." />
        </div>
      ) : (
        <div className="portfolio-heatmap-grid">
          {items.map((item, index) => (
            <article key={`${item.symbol}-${index}`} className={`portfolio-heatmap-tile ${resolveHeatToneClass(item.changePercent)}`}>
              <div className="portfolio-heatmap-head">
                <strong>{item.symbol}</strong>
                <span>{formatNumber(item.weight, 1)}%</span>
              </div>
              <div className="portfolio-heatmap-body">
                <span>{formatCurrency(item.value)}</span>
                <strong>{formatPercent(item.changePercent)}</strong>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function resolveHeatToneClass(changePercent) {
  const numeric = Number(changePercent);
  if (!Number.isFinite(numeric) || numeric === 0) {
    return "is-neutral";
  }
  if (numeric > 4) {
    return "is-strong-up";
  }
  if (numeric > 0) {
    return "is-up";
  }
  if (numeric < -4) {
    return "is-strong-down";
  }
  return "is-down";
}
