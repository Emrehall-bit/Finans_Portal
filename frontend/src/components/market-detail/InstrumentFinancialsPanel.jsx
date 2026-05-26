import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatCompactFinancialValue, formatFinancialEquation } from "../../utils/formatters";

const INCOME_KEYS = ["HASILAT", "BRUT_KAR", "ESAS_FAALIYET_KARI", "FAVOK", "NET_DONEM_KARI"];
const BALANCE_KEYS = [
  "TOPLAM_VARLIKLAR",
  "OZKAYNAKLAR",
  "TOPLAM_KAYNAKLAR",
  "TOPLAM_YUKUMLULUKLER",
  "KISA_VADELI_YUKUMLULUKLER",
  "UZUN_VADELI_YUKUMLULUKLER",
  "ODENMIS_SERMAYE",
];
const DISPLAY_ITEMS = [...INCOME_KEYS, ...BALANCE_KEYS];

const ITEM_LABELS = {
  HASILAT: "Hasılat",
  BRUT_KAR: "Brüt Kar",
  ESAS_FAALIYET_KARI: "Esas Faaliyet Karı",
  FAVOK: "FAVÖK",
  NET_DONEM_KARI: "Net Dönem Karı",
  TOPLAM_VARLIKLAR: "Toplam Varlıklar",
  OZKAYNAKLAR: "Özkaynaklar",
  TOPLAM_KAYNAKLAR: "Toplam Kaynaklar",
  TOPLAM_YUKUMLULUKLER: "Toplam Yükümlülükler",
  KISA_VADELI_YUKUMLULUKLER: "Kısa Vadeli Yükümlülükler",
  UZUN_VADELI_YUKUMLULUKLER: "Uzun Vadeli Yükümlülükler",
  ODENMIS_SERMAYE: "Ödenmiş Sermaye",
};

const SUMMARY_ITEMS = [
  { key: "HASILAT", label: "Hasılat" },
  { key: "NET_DONEM_KARI", label: "Net Dönem Karı" },
  { key: "OZKAYNAKLAR", label: "Özkaynaklar" },
  { key: "TOPLAM_VARLIKLAR", label: "Toplam Varlıklar" },
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
          <section className="financial-overview-section" aria-label="Finansal Özet">
            <div className="financial-section-title">
              <h4>Finansal Özet</h4>
              <span>{summary.period}</span>
            </div>
            <div className="financial-summary-grid">
              {summary.items.map((item) => (
                <div key={item.key} className="financial-summary-card">
                  <span>{item.label}</span>
                  <strong className={item.isNegative ? "value-negative" : ""}>{item.display}</strong>
                </div>
              ))}
            </div>
          </section>

          <div className="financial-period-stack">
            {reportModels.map((reportModel, index) => (
              <FinancialPeriod
                key={reportModel.report.reportId ?? `${reportModel.report.periodYear}-${reportModel.report.periodQuarter}`}
                reportModel={reportModel}
                defaultOpen={index === 0}
              />
            ))}
          </div>

          <p className="financial-footnote">
            Finansal veriler dönemler arası karşılaştırma için gerektiğinde normalize edilmiştir.
          </p>
        </>
      )}
    </section>
  );
}

function FinancialPeriod({ reportModel, defaultOpen }) {
  const { report, values } = reportModel;
  const groups = useMemo(() => groupFinancialValues(values), [values]);

  return (
    <details className="financial-period-block" open={defaultOpen}>
      <summary className="financial-period-head">
        <div>
          <h4>{formatPeriod(report)}</h4>
          <span>{values.length} kalem</span>
        </div>
        <span className="financial-period-source">
          <SourceLink sourceUrl={report?.sourceUrl} />
        </span>
      </summary>

      {values.length === 0 ? (
        <FinancialEmptyState title="Finansal kalem yok" description="Bu dönem için finansal kalem bulunamadı." compact />
      ) : (
        <div className="financial-group-grid">
          {groups.map((group) => (
            <FinancialGroup key={group.title} group={group} report={report} />
          ))}
        </div>
      )}
    </details>
  );
}

function FinancialGroup({ group, report }) {
  if (group.items.length === 0) {
    return null;
  }

  return (
    <section className="financial-group-card">
      <h5>{group.title}</h5>
      <div className="financial-values-table-wrap">
        <table className="financial-values-table">
          <thead>
            <tr>
              <th>Kalem</th>
              <th>Değer</th>
              <th>Para Birimi</th>
              <th>Kaynak</th>
            </tr>
          </thead>
          <tbody>
            {group.items.map((item) => (
              <tr key={item.itemKey} title={buildValueTooltip(item)}>
                <td>{ITEM_LABELS[item.itemKey] ?? item.itemKey ?? "-"}</td>
                <td className={`financial-value-final ${isNegativeValue(item.total) ? "value-negative" : ""}`}>
                  {formatFinalValue(item)}
                </td>
                <td>{item.currency ?? "-"}</td>
                <td>
                  <SourceLink sourceUrl={report?.sourceUrl} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
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
  return reports.map((report) => {
    const values = buildDisplayValues(report?.values).map((item) => ({
      ...item,
      total: getFinancialTotal(item),
    }));
    return { report, values };
  });
}

function buildSummary(reportModels) {
  const latest = reportModels.find((model) => model.values.length > 0);
  const latestValues = new Map((latest?.values ?? []).map((item) => [item.itemKey, item]));

  return {
    period: latest ? formatPeriod(latest.report) : "-",
    items: SUMMARY_ITEMS.map((summaryItem) => {
      const value = latestValues.get(summaryItem.key);
      const hasValue = value?.total !== null && value?.total !== undefined;
      return {
        ...summaryItem,
        display: hasValue ? formatCompactFinancialValue(value.total, value?.currency ?? "TRY") : "Veri Yok",
        isNegative: hasValue ? isNegativeValue(value.total) : false,
      };
    }),
  };
}

function groupFinancialValues(values) {
  const income = [];
  const balance = [];
  const other = [];

  values.forEach((item) => {
    if (INCOME_KEYS.includes(item.itemKey)) {
      income.push(item);
    } else if (BALANCE_KEYS.includes(item.itemKey)) {
      balance.push(item);
    } else {
      other.push(item);
    }
  });

  return [
    { title: "Gelir Tablosu", items: income },
    { title: "Bilanço", items: balance },
    { title: "Diğer", items: other },
  ].filter((group) => group.items.length > 0);
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
  if (report.reportType === "ANNUAL") return `${year}/Yıllık`;
  if (report.reportType) return `${year}/${report.reportType}`;
  if (Number(report.periodQuarter) === 4) return `${year}/Yıllık`;
  return report.periodQuarter ? `${year}/Q${report.periodQuarter}` : String(year);
}

function formatFinalValue(item) {
  if (item?.total === null || item?.total === undefined) {
    return "-";
  }
  return formatCompactFinancialValue(item.total, item?.currency ?? "TRY");
}

function getFinancialTotal(item) {
  if (!item) return null;
  return formatFinancialEquation(item?.value, item?.unitMultiplier ?? 1, item?.currency ?? "TRY").total;
}

function isNegativeValue(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric < 0;
}

function buildValueTooltip(item) {
  const rawValue = item?.value ?? "-";
  const unitMultiplier = item?.unitMultiplier ?? 1;
  const rawLabel = item?.rawLabel ?? "-";
  return `Ham değer: ${rawValue} | Çarpan: ${unitMultiplier} | Ham etiket: ${rawLabel}`;
}

function SourceLink({ sourceUrl }) {
  if (!sourceUrl) {
    return <span className="financial-source-empty">-</span>;
  }

  return (
    <a className="financial-source-link" href={sourceUrl} target="_blank" rel="noreferrer">
      Kaynak
    </a>
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
