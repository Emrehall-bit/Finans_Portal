import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatCompactFinancialValue, formatFinancialEquation, formatTurkishNumber } from "../../utils/formatters";

const DISPLAY_ITEMS = [
  "HASILAT",
  "NET_DONEM_KARI",
  "OZKAYNAKLAR",
  "TOPLAM_VARLIKLAR",
  "TOPLAM_KAYNAKLAR",
];

const ITEM_LABELS = {
  HASILAT: "Hasılat",
  NET_DONEM_KARI: "Net Dönem Karı",
  OZKAYNAKLAR: "Özkaynaklar",
  TOPLAM_VARLIKLAR: "Toplam Varlıklar",
  TOPLAM_KAYNAKLAR: "Toplam Kaynaklar",
};

const SUMMARY_ITEMS = [
  { key: "HASILAT", label: "Son Hasılat" },
  { key: "NET_DONEM_KARI", label: "Son Net Kar" },
  { key: "OZKAYNAKLAR", label: "Son Özkaynak" },
  { key: "TOPLAM_VARLIKLAR", label: "Son Varlıklar" },
];

export default function InstrumentFinancialsPanel({ loading, error, reports }) {
  const { t } = useTranslation();
  const rows = useMemo(() => sortReports(Array.isArray(reports) ? reports : []), [reports]);
  const reportModels = useMemo(() => buildReportModels(rows), [rows]);
  const summary = useMemo(() => buildSummary(reportModels), [reportModels]);

  if (loading) {
    return <LoadingSpinner label={t("instrumentDetail.financialsLoading", "Finansallar yükleniyor...")} />;
  }

  if (error) {
    return <ErrorMessage message={error} />;
  }

  return (
    <section className="panel-surface instrument-financials-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">{t("instrumentDetail.financialsEyebrow")}</p>
          <h3>{t("instrumentDetail.financialsTitle")}</h3>
        </div>
      </div>

      {rows.length === 0 ? (
        <FinancialEmptyState
          title={t("instrumentDetail.financialsEmptyTitle")}
          description={t("instrumentDetail.financialsEmptyDescription")}
        />
      ) : (
        <>
          <div className="financial-summary-grid">
            {summary.map((item) => (
              <div key={item.key} className={`financial-summary-card ${item.change?.className ?? "value-neutral"}`}>
                <span>{item.label}</span>
                <strong>{item.display}</strong>
                <small>
                  {item.period}
                  {item.change ? <ChangeBadge change={item.change} /> : null}
                </small>
              </div>
            ))}
          </div>

          <div className="financial-period-stack">
            {reportModels.map((reportModel) => (
              <FinancialPeriod
                key={reportModel.report.reportId ?? `${reportModel.report.periodYear}-${reportModel.report.periodQuarter}`}
                reportModel={reportModel}
              />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function FinancialPeriod({ reportModel }) {
  const { report, values } = reportModel;

  return (
    <section className="financial-period-block">
      <div className="financial-period-head">
        <h4>{formatPeriod(report)}</h4>
        {report?.parseStatus ? <span className={`status-badge ${String(report.parseStatus).toLowerCase()}`}>{report.parseStatus}</span> : null}
      </div>

      {values.length === 0 ? (
        <FinancialEmptyState title="Finansal kalem yok" description="Bu dönem için finansal kalem bulunamadı." compact />
      ) : (
        <div className="financial-values-table-wrap">
          <table className="financial-values-table">
            <thead>
              <tr>
                <th>Kalem</th>
                <th>Değer</th>
                <th>Değişim</th>
                <th>Para Birimi</th>
                <th>Çarpan</th>
                <th>Ham Etiket</th>
              </tr>
            </thead>
            <tbody>
              {values.map((item) => (
                <tr key={item.itemKey}>
                  <td>{ITEM_LABELS[item.itemKey] ?? item.itemKey ?? "-"}</td>
                  <td className={item.change?.className ?? ""}>{formatMultipliedValue(item)}</td>
                  <td>{item.change ? <ChangeBadge change={item.change} /> : <span className="financial-change-empty">-</span>}</td>
                  <td>{item.currency ?? "-"}</td>
                  <td>{formatTurkishNumber(item.unitMultiplier ?? 1, 0)}</td>
                  <td>{item.rawLabel ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function buildDisplayValues(values) {
  if (!Array.isArray(values) || values.length === 0) {
    return [];
  }

  const byKey = new Map(values.map((item) => [item.itemKey, item]));
  const preferred = DISPLAY_ITEMS.map((key) => byKey.get(key)).filter(Boolean);
  const rest = values.filter((item) => item?.itemKey && !DISPLAY_ITEMS.includes(item.itemKey));
  return [...preferred, ...rest];
}

function buildReportModels(reports) {
  return reports.map((report, index) => {
    const previous = reports[index + 1];
    const previousValues = new Map((previous?.values ?? []).map((item) => [item.itemKey, item]));
    const values = buildDisplayValues(report?.values).map((item) => {
      const currentTotal = getFinancialTotal(item);
      const previousTotal = getFinancialTotal(previousValues.get(item.itemKey));
      return {
        ...item,
        total: currentTotal,
        change: buildChange(currentTotal, previousTotal),
      };
    });
    return { report, values };
  });
}

function buildSummary(reportModels) {
  const latest = reportModels.find((model) => model.values.length > 0);
  const values = new Map((latest?.values ?? []).map((item) => [item.itemKey, item]));
  return SUMMARY_ITEMS.map((summaryItem) => {
    const value = values.get(summaryItem.key);
    const equation = value
      ? formatFinancialEquation(value.value, value.unitMultiplier ?? 1, value.currency ?? "TRY")
      : { total: null };
    return {
      ...summaryItem,
      total: equation.total,
      display: equation.total === null ? "-" : formatCompactFinancialValue(equation.total, value?.currency ?? "TRY"),
      period: latest ? formatPeriod(latest.report) : "-",
      change: value?.change ?? null,
    };
  });
}

function sortReports(reports) {
  return [...reports].sort((a, b) => {
    const yearDiff = Number(b?.periodYear ?? 0) - Number(a?.periodYear ?? 0);
    if (yearDiff !== 0) return yearDiff;
    return periodRank(b) - periodRank(a);
  });
}

function periodRank(report) {
  if (report?.reportType === "ANNUAL") return 4;
  return Number(report?.periodQuarter ?? 0);
}

function formatPeriod(report) {
  if (!report) return "-";
  const year = report.periodYear ?? "-";
  if (report.reportType === "ANNUAL") return `${year}/Annual`;
  if (report.reportType) return `${year}/${report.reportType}`;
  if (Number(report.periodQuarter) === 4) return `${year}/Annual`;
  return report.periodQuarter ? `${year}/Q${report.periodQuarter}` : String(year);
}

function formatMultipliedValue(item) {
  return formatFinancialEquation(item?.value, item?.unitMultiplier ?? 1, item?.currency ?? "TRY").label;
}

function getFinancialTotal(item) {
  if (!item) return null;
  return formatFinancialEquation(item?.value, item?.unitMultiplier ?? 1, item?.currency ?? "TRY").total;
}

function buildChange(currentTotal, previousTotal) {
  const current = Number(currentTotal);
  const previous = Number(previousTotal);
  if (!Number.isFinite(current) || !Number.isFinite(previous)) return null;
  const diff = current - previous;
  if (diff === 0) {
    return { className: "value-neutral", arrow: "•", label: "0,0%" };
  }
  if (previous === 0) {
    return { className: diff > 0 ? "value-positive" : "value-negative", arrow: diff > 0 ? "▲" : "▼", label: "" };
  }
  const percent = (diff / Math.abs(previous)) * 100;
  return {
    className: diff > 0 ? "value-positive" : "value-negative",
    arrow: diff > 0 ? "▲" : "▼",
    label: `${diff > 0 ? "+" : ""}${percent.toLocaleString("tr-TR", {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    })}%`,
  };
}

function ChangeBadge({ change }) {
  return (
    <span className={`financial-change-badge ${change.className}`}>
      <span aria-hidden="true">{change.arrow}</span>
      {change.label ? <span>{change.label}</span> : null}
    </span>
  );
}

function FinancialEmptyState({ title, description, compact = false }) {
  return (
    <div className={`financial-empty-state ${compact ? "compact" : ""}`}>
      <span aria-hidden="true">▦</span>
      <EmptyState title={title} description={description} />
    </div>
  );
}
