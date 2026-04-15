export default function SummaryCard({ title, value, subtitle }) {
  return (
    <div className="card summary-card">
      <p className="muted">{title}</p>
      <h3>{value}</h3>
      {subtitle ? <p className="muted">{subtitle}</p> : null}
    </div>
  );
}
