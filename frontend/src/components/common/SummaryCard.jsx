export default function SummaryCard({ title, value, subtitle, trend, tone = "neutral" }) {
  return (
    <div className={`summary-card summary-card-${tone}`}>
      <div className="summary-card-top">
        <p className="summary-card-title">{title}</p>
        {trend ? <span className="summary-chip">{trend}</span> : null}
      </div>
      <h3>{value}</h3>
      {subtitle ? <p className="summary-card-subtitle">{subtitle}</p> : null}
      <div className="summary-sparkline" aria-hidden="true">
        <span />
        <span />
        <span />
        <span />
        <span />
      </div>
    </div>
  );
}
