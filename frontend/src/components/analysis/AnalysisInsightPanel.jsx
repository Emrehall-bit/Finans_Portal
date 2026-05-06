import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatNumber } from "../../utils/formatters";
import { formatSignalLabel, formatTrendLabel } from "./analysisUtils";

export default function AnalysisInsightPanel({ analysis, loading, error }) {
  return (
    <aside className="panel-surface analysis-lab-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Indikatorler</p>
          <h3>Trend ve sinyaller</h3>
        </div>
      </div>

      {loading ? <LoadingSpinner label="Analiz ozetleri yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && !analysis ? (
        <EmptyState title="Teknik analiz verisi bulunamadi" description="Secili enstruman icin analiz sonucu gelmedi." />
      ) : null}

      {!loading && !error && analysis ? (
        <>
          <div className="instrument-overview-summary">
            <div className="instrument-overview-metric">
              <span>Trend</span>
              <strong>{formatTrendLabel(analysis.trendDirection)}</strong>
            </div>
            <div className="instrument-overview-metric">
              <span>Son fiyat</span>
              <strong>{formatNumber(analysis.latestPrice)}</strong>
            </div>
            <div className="instrument-overview-metric">
              <span>Veri noktasi</span>
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
              <span className="signal-pill neutral">Belirgin sinyal yok</span>
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
              <EmptyState title="Indikator bulunamadi" description="Secili aralik icin indikator degerleri olusmadi." />
            )}
          </div>
        </>
      ) : null}
    </aside>
  );
}
