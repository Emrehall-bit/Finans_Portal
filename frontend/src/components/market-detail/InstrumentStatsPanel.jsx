export default function InstrumentStatsPanel({ stats }) {
  return (
    <aside className="panel-surface instrument-stats-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Bilgi Paneli</p>
          <h3>Piyasa ozeti</h3>
        </div>
      </div>

      <div className="instrument-stats-list">
        {stats.map((item) => (
          <div key={item.label} className="instrument-stats-row">
            <span>{item.label}</span>
            <strong className={`tone-${item.tone || "neutral"}`}>{item.value}</strong>
          </div>
        ))}
      </div>
    </aside>
  );
}
