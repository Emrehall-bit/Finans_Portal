import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatNumber } from "../../utils/formatters";
import { buildChartData, formatSignalLabel, formatTrendLabel, resolveTrendDirection } from "./analysisUtils";

export default function AnalysisInsightPanel({ analysis, loading, error }) {
  const { t } = useTranslation();
  const displayTrendDirection = resolveTrendDirection(analysis?.trendDirection, buildChartData(analysis?.points));

  return (
    <aside className="panel-surface analysis-lab-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("analysis.insight.eyebrow")}</p>
          <h3>{t("analysis.insight.title")}</h3>
        </div>
      </div>

      {loading ? <LoadingSpinner label={t("analysis.insight.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && !analysis ? (
        <EmptyState title={t("analysis.insight.emptyTitle")} description={t("analysis.insight.emptyDescription")} />
      ) : null}

      {!loading && !error && analysis ? (
        <>
          <div className="instrument-overview-summary">
            <div className="instrument-overview-metric">
              <span>{t("instrumentDetail.trend")}</span>
              <strong>{formatTrendLabel(displayTrendDirection)}</strong>
            </div>
            <div className="instrument-overview-metric">
              <span>{t("instrumentDetail.latestPrice")}</span>
              <strong>{formatNumber(analysis.latestPrice)}</strong>
            </div>
            <div className="instrument-overview-metric">
              <span>{t("instrumentDetail.dataPoints")}</span>
              <strong>{analysis.points?.length ?? 0}</strong>
            </div>
          </div>

          <div className="signal-chip-row">
            {(analysis.signals ?? []).length > 0 ? (
              analysis.signals.map((signal) => (
                <span key={signal} className="signal-pill">
                  {formatSignalLabel(signal)}
                </span>
              ))
            ) : (
              <span className="signal-pill neutral">{t("instrumentDetail.noSignal")}</span>
            )}
          </div>

          <div className="indicator-value-grid terminal-indicator-grid">
            {(analysis.indicatorValues ?? []).length > 0 ? (
              analysis.indicatorValues.map((item) => (
                <div key={item.indicator} className="indicator-value-card">
                  <span>{item.indicator}</span>
                  <strong>{formatNumber(item.value)}</strong>
                </div>
              ))
            ) : (
              <EmptyState title={t("instrumentDetail.indicatorsEmptyTitle")} description={t("instrumentDetail.indicatorsEmptyDescription")} />
            )}
          </div>
        </>
      ) : null}
    </aside>
  );
}
