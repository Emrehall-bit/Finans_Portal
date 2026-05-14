import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";

const NO_DATA = "Veri Yok";

function fmtRatio(value, decimals = 2) {
  if (value === null || value === undefined) return NO_DATA;
  const n = Number(value);
  if (!Number.isFinite(n)) return NO_DATA;
  return n.toLocaleString(undefined, { maximumFractionDigits: decimals, minimumFractionDigits: decimals });
}

function fmtPercent(value, decimals = 2) {
  if (value === null || value === undefined) return NO_DATA;
  const n = Number(value);
  if (!Number.isFinite(n)) return NO_DATA;
  const pct = n * 100;
  return `${pct.toLocaleString(undefined, { maximumFractionDigits: decimals, minimumFractionDigits: decimals })}%`;
}

function fmtDate(value) {
  if (!value) return NO_DATA;
  try {
    return new Date(value).toLocaleString();
  } catch {
    return String(value);
  }
}

export default function InstrumentFundamentalsPanel({ loading, error, data }) {
  const { t } = useTranslation();

  if (loading) return <LoadingSpinner label={t("instrumentDetail.fundamentals.loading")} />;
  if (error) return <ErrorMessage message={error} />;

  const noRatio = !data || data.message;

  return (
    <section className="panel-surface instrument-fundamentals-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("instrumentDetail.fundamentals.eyebrow")}</p>
          <h3>{t("instrumentDetail.fundamentals.title")}</h3>
        </div>
      </div>

      {data && (
        <div className="instrument-overview-summary" style={{ marginBottom: "1rem" }}>
          {data.companyName && (
            <div className="instrument-overview-metric">
              <span>{t("instrumentDetail.fundamentals.companyName")}</span>
              <strong>{data.companyName}</strong>
            </div>
          )}
          {data.latestReportPeriod && (
            <div className="instrument-overview-metric">
              <span>{t("instrumentDetail.fundamentals.period")}</span>
              <strong>{data.latestReportPeriod}</strong>
            </div>
          )}
          {data.parseStatus && (
            <div className="instrument-overview-metric">
              <span>{t("instrumentDetail.fundamentals.parseStatus")}</span>
              <strong>{data.parseStatus}</strong>
            </div>
          )}
        </div>
      )}

      {noRatio ? (
        <EmptyState
          title={t("instrumentDetail.fundamentals.emptyTitle")}
          description={data?.message ?? t("instrumentDetail.fundamentals.emptyDescription")}
        />
      ) : (
        <>
          <div className="indicator-value-grid terminal-indicator-grid">
            <RatioCard label={t("instrumentDetail.fundamentals.peRatio")} value={fmtRatio(data.peRatio)} />
            <RatioCard label={t("instrumentDetail.fundamentals.pbRatio")} value={fmtRatio(data.pbRatio)} />
            <RatioCard label={t("instrumentDetail.fundamentals.debtToEquity")} value={fmtRatio(data.debtToEquity)} />
            <RatioCard label={t("instrumentDetail.fundamentals.grossMargin")} value={fmtPercent(data.grossMargin)} />
            <RatioCard label={t("instrumentDetail.fundamentals.netMargin")} value={fmtPercent(data.netMargin)} />
            <RatioCard label={t("instrumentDetail.fundamentals.roe")} value={fmtPercent(data.roe)} />
            <RatioCard label={t("instrumentDetail.fundamentals.roa")} value={fmtPercent(data.roa)} />
            <RatioCard label={t("instrumentDetail.fundamentals.revenueGrowth")} value={fmtPercent(data.revenueGrowth)} />
            <RatioCard label={t("instrumentDetail.fundamentals.netProfitGrowth")} value={fmtPercent(data.netProfitGrowth)} />
            <RatioCard label={t("instrumentDetail.fundamentals.assetGrowth")} value={fmtPercent(data.assetGrowth)} />
          </div>

          {data.healthLabel && (
            <div style={{ marginTop: "1rem" }}>
              <span className="signal-pill">{data.healthLabel}</span>
            </div>
          )}

          <div className="instrument-overview-summary" style={{ marginTop: "1rem" }}>
            {data.priceAtCalc != null && (
              <div className="instrument-overview-metric">
                <span>{t("instrumentDetail.fundamentals.priceAtCalc")}</span>
                <strong>{fmtRatio(data.priceAtCalc, 4)}</strong>
              </div>
            )}
            {data.calculatedAt && (
              <div className="instrument-overview-metric">
                <span>{t("instrumentDetail.fundamentals.calculatedAt")}</span>
                <strong>{fmtDate(data.calculatedAt)}</strong>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  );
}

function RatioCard({ label, value }) {
  const isNoData = value === NO_DATA;
  return (
    <div className="indicator-value-card">
      <span>{label}</span>
      <strong style={isNoData ? { opacity: 0.45, fontStyle: "italic" } : undefined}>{value}</strong>
    </div>
  );
}
