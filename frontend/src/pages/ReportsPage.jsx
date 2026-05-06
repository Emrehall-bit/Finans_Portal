import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { getMarketQuotes } from "../api/marketApi";
import { getUserPortfolios } from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { formatDateTime, formatNumber } from "../utils/formatters";

export default function ReportsPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const [quotes, setQuotes] = useState([]);
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!userId) {
      setLoading(false);
      return;
    }

    let active = true;

    async function loadData() {
      try {
        setLoading(true);
        setError("");
        const [marketQuotes, userPortfolios] = await Promise.all([
          getMarketQuotes().catch(() => []),
          getUserPortfolios(userId).catch(() => []),
        ]);

        if (!active) {
          return;
        }

        setQuotes(marketQuotes ?? []);
        setPortfolios(userPortfolios ?? []);
      } catch (err) {
        if (active) {
          setError(extractErrorMessage(err, t("reports.loadError")));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadData();

    return () => {
      active = false;
    };
  }, [userId]);

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
    <div className="dashboard-stack reports-shell">
      <PageHeader
        eyebrow={t("reports.eyebrow")}
        title={t("reports.title")}
        description={t("reports.description")}
      />

      {loading ? <LoadingSpinner label={t("reports.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <section className="reports-grid">
          <article className="panel-surface reports-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("reports.portfolioEyebrow")}</p>
                <h3>{t("reports.portfolioTitle")}</h3>
              </div>
              <span className="summary-chip">{t("reports.portfolioCount", { count: portfolios.length })}</span>
            </div>

            {portfolios.length === 0 ? (
              <EmptyState title={t("reports.portfolioEmptyTitle")} description={t("reports.portfolioEmptyDescription")} />
            ) : (
              <div className="cards-grid compact">
                <SummaryCard title={t("reports.cards.portfolioCount")} value={formatNumber(portfolios.length, 0)} subtitle={t("reports.cards.portfolioCountSubtitle")} tone="cool" />
                <SummaryCard title={t("reports.cards.latestPortfolio")} value={portfolios[0]?.portfolioName || "-"} subtitle={formatDateTime(portfolios[0]?.createdAt)} tone="neutral" />
              </div>
            )}

            <div className="reports-action-row">
              <button type="button" className="secondary-button" disabled title={t("reports.pdfHint")}>
                {t("reports.pdf")}
              </button>
              <button type="button" className="secondary-button" disabled title={t("reports.excelHint")}>
                {t("reports.excel")}
              </button>
            </div>
          </article>

          <article className="panel-surface reports-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("reports.marketEyebrow")}</p>
                <h3>{t("reports.marketTitle")}</h3>
              </div>
              <span className="summary-chip">{t("reports.symbolCount", { count: marketSummary.total })}</span>
            </div>

            {quotes.length === 0 ? (
              <EmptyState title={t("reports.marketEmptyTitle")} description={t("reports.marketEmptyDescription")} />
            ) : (
              <div className="cards-grid compact">
                <SummaryCard title={t("reports.cards.totalSymbols")} value={formatNumber(marketSummary.total, 0)} subtitle={t("reports.cards.totalSymbolsSubtitle")} tone="cool" />
                <SummaryCard title={t("reports.cards.positiveSymbols")} value={formatNumber(marketSummary.positiveCount, 0)} subtitle={t("reports.cards.positiveSymbolsSubtitle")} tone="cool" />
                <SummaryCard title={t("reports.cards.negativeSymbols")} value={formatNumber(marketSummary.negativeCount, 0)} subtitle={t("reports.cards.negativeSymbolsSubtitle")} tone="warm" />
                <SummaryCard title={t("reports.cards.reportTime")} value={formatDateTime(marketSummary.updatedAt)} subtitle={t("reports.cards.reportTimeSubtitle")} tone="neutral" />
              </div>
            )}

            <div className="reports-action-row">
              <button type="button" className="secondary-button" disabled title={t("reports.pdfHint")}>
                {t("reports.pdf")}
              </button>
              <button type="button" className="secondary-button" disabled title={t("reports.excelHint")}>
                {t("reports.excel")}
              </button>
            </div>
          </article>
        </section>
      ) : null}
    </div>
  );
}
