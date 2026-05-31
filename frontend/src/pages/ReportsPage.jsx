import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { FileSpreadsheet, FileText } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { useMarketQuotes } from "../hooks/useMarketQueries";
import { useUserPortfolios } from "../hooks/usePortfolioQueries";
import { formatDateTime, formatNumber } from "../utils/formatters";

export default function ReportsPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const { data: quotes = [], isLoading: quotesLoading } = useMarketQuotes({ enabled: !!userId });
  const { data: portfolios = [], isLoading: portfoliosLoading } = useUserPortfolios(userId);
  const loading = !!userId && (quotesLoading || portfoliosLoading);
  const error = "";

  const marketSummary = useMemo(() => {
    const positiveCount = quotes.filter((item) => Number(item.changeRate) >= 0).length;
    const negativeCount = quotes.filter((item) => Number(item.changeRate) < 0).length;
    return {
      total: quotes.length,
      positiveCount,
      negativeCount,
      updatedAt: new Date().toISOString(),
    };
  }, [quotes]);

  return (
    <div className="dashboard-stack reports-shell reports-page">
      <PageHeader
        eyebrow={t("reports.eyebrow")}
        title={t("reports.title")}
        description={t("reports.description")}
        actions={
          <div className="reports-range-chips" aria-label={t("reports.rangeLabel")}>
            <span>{t("reports.ranges.today")}</span>
            <span>{t("reports.ranges.week")}</span>
            <span>{t("reports.ranges.month")}</span>
            <span>{t("reports.ranges.ytd")}</span>
          </div>
        }
      />

      {loading ? <LoadingSpinner label={t("reports.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="reports-grid">
            <article className="panel-surface reports-card reports-card-portfolio">
              <div className="panel-head reports-card-head">
                <div>
                  <p className="eyebrow">{t("reports.portfolioEyebrow")}</p>
                  <h3>{t("reports.portfolioTitle")}</h3>
                </div>
                <span className="summary-chip">{t("reports.portfolioCount", { count: portfolios.length })}</span>
              </div>

              {portfolios.length === 0 ? (
                <EmptyState title={t("reports.portfolioEmptyTitle")} description={t("reports.portfolioEmptyDescription")} />
              ) : (
                <>
                  <div className="cards-grid compact reports-summary-grid reports-summary-grid-two">
                    <SummaryCard title={t("reports.cards.portfolioCount")} value={formatNumber(portfolios.length, 0)} subtitle={t("reports.cards.portfolioCountSubtitle")} tone="cool" />
                    <SummaryCard title={t("reports.cards.latestPortfolio")} value={portfolios[0]?.portfolioName || "-"} subtitle={formatDateTime(portfolios[0]?.createdAt)} tone="neutral" />
                  </div>
                  <div className="reports-info-line">
                    <span>{t("reports.reportablePortfolios")}</span>
                    <strong>{portfolios[0]?.portfolioName || t("reports.noRecentPortfolio")}</strong>
                  </div>
                </>
              )}

              <div className="reports-export-actions">
                <button type="button" className="reports-export-button" disabled title={t("reports.pdfHint")}>
                  <FileText size={18} strokeWidth={1.9} aria-hidden />
                  <span>{t("reports.pdf")}</span>
                </button>
                <button type="button" className="reports-export-button" disabled title={t("reports.excelHint")}>
                  <FileSpreadsheet size={18} strokeWidth={1.9} aria-hidden />
                  <span>{t("reports.excel")}</span>
                </button>
              </div>
            </article>

            <article className="panel-surface reports-card reports-card-market">
              <div className="panel-head reports-card-head">
                <div>
                  <p className="eyebrow">{t("reports.marketEyebrow")}</p>
                  <h3>{t("reports.marketTitle")}</h3>
                </div>
                <span className="summary-chip">{t("reports.symbolCount", { count: marketSummary.total })}</span>
              </div>

              {quotes.length === 0 ? (
                <EmptyState title={t("reports.marketEmptyTitle")} description={t("reports.marketEmptyDescription")} />
              ) : (
                <div className="cards-grid compact reports-summary-grid reports-market-grid">
                  <SummaryCard title={t("reports.cards.totalSymbols")} value={formatNumber(marketSummary.total, 0)} subtitle={t("reports.cards.totalSymbolsSubtitle")} tone="cool" />
                  <SummaryCard title={t("reports.cards.positiveSymbols")} value={formatNumber(marketSummary.positiveCount, 0)} subtitle={t("reports.cards.positiveSymbolsSubtitle")} tone="cool" />
                  <SummaryCard title={t("reports.cards.negativeSymbols")} value={formatNumber(marketSummary.negativeCount, 0)} subtitle={t("reports.cards.negativeSymbolsSubtitle")} tone="warm" />
                  <SummaryCard title={t("reports.cards.reportTime")} value={formatDateTime(marketSummary.updatedAt)} subtitle={t("reports.cards.reportTimeSubtitle")} tone="neutral" />
                </div>
              )}

              <div className="reports-export-actions">
                <button type="button" className="reports-export-button" disabled title={t("reports.pdfHint")}>
                  <FileText size={18} strokeWidth={1.9} aria-hidden />
                  <span>{t("reports.pdf")}</span>
                </button>
                <button type="button" className="reports-export-button" disabled title={t("reports.excelHint")}>
                  <FileSpreadsheet size={18} strokeWidth={1.9} aria-hidden />
                  <span>{t("reports.excel")}</span>
                </button>
              </div>
            </article>
          </section>

          <section className="panel-surface reports-empty-history">
            <EmptyState title={t("reports.historyEmptyTitle")} description={t("reports.historyEmptyDescription")} />
          </section>
        </>
      ) : null}
    </div>
  );
}
