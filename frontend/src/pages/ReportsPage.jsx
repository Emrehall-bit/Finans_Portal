import { useEffect, useMemo, useState } from "react";
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
          setError(extractErrorMessage(err, "Rapor verileri yuklenemedi."));
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
        eyebrow="Raporlar"
        title="Raporlar"
        description="Portfoy ve piyasa akisina ait ozet raporlar icin hazir kartlar."
      />

      {loading ? <LoadingSpinner label="Rapor verileri yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <section className="reports-grid">
          <article className="panel-surface reports-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Portfoy Raporu</p>
                <h3>Portfoy ozet ciktilari</h3>
              </div>
              <span className="summary-chip">{formatNumber(portfolios.length, 0)} portfoy</span>
            </div>

            {portfolios.length === 0 ? (
              <EmptyState title="Portfoy raporu hazir degil" description="Kullaniciya ait portfoy bulunmuyor." />
            ) : (
              <div className="cards-grid compact">
                <SummaryCard title="Portfoy adedi" value={formatNumber(portfolios.length, 0)} subtitle="Raporlanabilir kayit" tone="cool" />
                <SummaryCard title="Son olusturulan" value={portfolios[0]?.portfolioName || "-"} subtitle={formatDateTime(portfolios[0]?.createdAt)} tone="neutral" />
              </div>
            )}

            <div className="reports-action-row">
              <button type="button" className="secondary-button" disabled title="TODO: backend PDF export endpointi gerekli">
                PDF export
              </button>
              <button type="button" className="secondary-button" disabled title="TODO: backend Excel export endpointi gerekli">
                Excel export
              </button>
            </div>
          </article>

          <article className="panel-surface reports-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Piyasa Takip Raporu</p>
                <h3>Market akis ozeti</h3>
              </div>
              <span className="summary-chip">{formatNumber(marketSummary.total, 0)} sembol</span>
            </div>

            {quotes.length === 0 ? (
              <EmptyState title="Piyasa raporu hazir degil" description="Market servisinden raporlanabilir veri gelmedi." />
            ) : (
              <div className="cards-grid compact">
                <SummaryCard title="Toplam sembol" value={formatNumber(marketSummary.total, 0)} subtitle="Gozlenen piyasa kaydi" tone="cool" />
                <SummaryCard title="Artida olanlar" value={formatNumber(marketSummary.positiveCount, 0)} subtitle="Pozitif gunluk hareket" tone="cool" />
                <SummaryCard title="Ekside olanlar" value={formatNumber(marketSummary.negativeCount, 0)} subtitle="Negatif gunluk hareket" tone="warm" />
                <SummaryCard title="Rapor zamani" value={formatDateTime(marketSummary.updatedAt)} subtitle="Anlik olusan ozet" tone="neutral" />
              </div>
            )}

            <div className="reports-action-row">
              <button type="button" className="secondary-button" disabled title="TODO: backend PDF export endpointi gerekli">
                PDF export
              </button>
              <button type="button" className="secondary-button" disabled title="TODO: backend Excel export endpointi gerekli">
                Excel export
              </button>
            </div>
          </article>
        </section>
      ) : null}
    </div>
  );
}
