import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";

const NO_DATA = "Veri Yok";
const INSUFFICIENT_DATA = "Yeterli veri yok";

function fmtRatio(value, decimals = 2) {
  if (value === null || value === undefined) return NO_DATA;
  const n = Number(value);
  if (!Number.isFinite(n)) return NO_DATA;
  return n.toLocaleString("tr-TR", { maximumFractionDigits: decimals, minimumFractionDigits: decimals });
}

function fmtPercent(value, decimals = 2, options = {}) {
  if (value === null || value === undefined) return NO_DATA;
  const n = Number(value);
  if (!Number.isFinite(n)) return NO_DATA;
  const pct = n * 100;
  const absPct = Math.abs(pct);
  if (options.floorTiny && absPct > 0 && absPct < 0.01) {
    return "0,01% altı";
  }
  const resolvedDecimals = absPct > 0 && absPct < 1 ? Math.max(decimals, 4) : decimals;
  return `${pct.toLocaleString("tr-TR", {
    maximumFractionDigits: resolvedDecimals,
    minimumFractionDigits: resolvedDecimals,
  })}%`;
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
        <div className="fundamentals-meta-grid">
          {data.companyName && (
            <div className="fundamentals-meta-card">
              <span>{t("instrumentDetail.fundamentals.companyName")}</span>
              <strong>{data.companyName}</strong>
            </div>
          )}
          {data.latestReportPeriod && (
            <div className="fundamentals-meta-card">
              <span>{t("instrumentDetail.fundamentals.period")}</span>
              <strong>{data.latestReportPeriod}</strong>
            </div>
          )}
          {data.parseStatus && (
            <div className="fundamentals-meta-card">
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
            <RatioCard label={t("instrumentDetail.fundamentals.peRatio")} value={fmtRatio(data.peRatio)} rawValue={data.peRatio} />
            <RatioCard label={t("instrumentDetail.fundamentals.pbRatio")} value={fmtRatio(data.pbRatio)} rawValue={data.pbRatio} />
            <RatioCard label={t("instrumentDetail.fundamentals.debtToEquity")} value={fmtRatio(data.debtToEquity)} rawValue={data.debtToEquity} />
            <RatioCard label={t("instrumentDetail.fundamentals.grossMargin")} value={fmtPercent(data.grossMargin)} rawValue={data.grossMargin} />
            <RatioCard label={t("instrumentDetail.fundamentals.netMargin")} value={fmtPercent(data.netMargin)} rawValue={data.netMargin} />
            <RatioCard label={t("instrumentDetail.fundamentals.roe")} value={fmtPercent(data.roe)} rawValue={data.roe} />
            <RatioCard label={t("instrumentDetail.fundamentals.roa")} value={fmtPercent(data.roa, 2, { floorTiny: true })} rawValue={data.roa} />
            <RatioCard
              label={t("instrumentDetail.fundamentals.revenueGrowth")}
              value={fmtGrowth(data.revenueGrowth, data.revenueGrowthLabel)}
              rawValue={data.revenueGrowth}
              tooltip={t("instrumentDetail.fundamentals.growthTooltip")}
            />
            <RatioCard
              label={t("instrumentDetail.fundamentals.netProfitGrowth")}
              value={fmtGrowth(data.netProfitGrowth, data.netProfitGrowthLabel)}
              rawValue={data.netProfitGrowth}
              tooltip={t("instrumentDetail.fundamentals.growthTooltip")}
            />
            <RatioCard
              label={t("instrumentDetail.fundamentals.assetGrowth")}
              value={fmtGrowth(data.assetGrowth, data.assetGrowthLabel)}
              rawValue={data.assetGrowth}
              tooltip={t("instrumentDetail.fundamentals.growthTooltip")}
            />
          </div>

          {data.healthLabel && (
            <div className="fundamentals-health-row">
              <span className="signal-pill fundamentals-health-pill">{data.healthLabel}</span>
            </div>
          )}

          <div className="fundamentals-footer-grid">
            {data.priceAtCalc != null && (
              <div className="fundamentals-meta-card">
                <span>{t("instrumentDetail.fundamentals.priceAtCalc")}</span>
                <strong>{fmtRatio(data.priceAtCalc, 4)}</strong>
              </div>
            )}
            {data.calculatedAt && (
              <div className="fundamentals-meta-card">
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

function RatioCard({ label, value, rawValue, tooltip }) {
  const isNoData = value === NO_DATA || value === INSUFFICIENT_DATA;
  const sentiment = resolveSentiment(rawValue);
  return (
    <div className={`indicator-value-card fundamentals-ratio-card ${sentiment}`} title={tooltip}>
      <span className="fundamentals-ratio-label">{label}</span>
      <strong className={`fundamentals-ratio-value ${isNoData ? "muted-value" : ""}`}>{value}</strong>
    </div>
  );
}

function fmtGrowth(value, label) {
  if (value === null || value === undefined) return label || INSUFFICIENT_DATA;
  return fmtPercent(value);
}

function resolveSentiment(value) {
  if (value === null || value === undefined || value === "") return "ratio-neutral";
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric === 0) return "ratio-neutral";
  return numeric > 0 ? "ratio-positive" : "ratio-negative";
}
