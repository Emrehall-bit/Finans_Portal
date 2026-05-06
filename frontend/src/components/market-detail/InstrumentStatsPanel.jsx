import { useTranslation } from "react-i18next";

export default function InstrumentStatsPanel({ stats }) {
  const { t } = useTranslation();

  return (
    <aside className="panel-surface instrument-stats-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("instrumentDetail.statsEyebrow")}</p>
          <h3>{t("instrumentDetail.statsTitle")}</h3>
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
