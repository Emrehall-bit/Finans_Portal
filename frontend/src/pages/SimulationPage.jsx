import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { getMarketHistory, getMarketQuote, getMarketQuotes } from "../api/marketApi";
import { getPortfolioDetails, getUserPortfolios } from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";

export default function SimulationPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const [quotes, setQuotes] = useState([]);
  const [portfolios, setPortfolios] = useState([]);
  const [selectedPortfolioDetails, setSelectedPortfolioDetails] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [pastForm, setPastForm] = useState({
    instrumentCode: "",
    date: "",
    amount: "",
  });
  const [pastResult, setPastResult] = useState(null);
  const [pastLoading, setPastLoading] = useState(false);
  const [pastError, setPastError] = useState("");

  const [futureForm, setFutureForm] = useState({
    mode: "instrument",
    instrumentCode: "",
    portfolioId: "",
    percentChange: "",
  });

  useEffect(() => {
    if (!userId) {
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
        setPastForm((current) => ({
          ...current,
          instrumentCode: current.instrumentCode || marketQuotes?.[0]?.symbol || "",
        }));
        setFutureForm((current) => ({
          ...current,
          instrumentCode: current.instrumentCode || marketQuotes?.[0]?.symbol || "",
          portfolioId: current.portfolioId || userPortfolios?.[0]?.portfolioId || "",
        }));
      } catch (err) {
        if (active) {
          setError(extractErrorMessage(err, t("simulation.loadError")));
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

  useEffect(() => {
    if (futureForm.mode !== "portfolio" || !futureForm.portfolioId) {
      setSelectedPortfolioDetails(null);
      return;
    }

    let active = true;

    async function loadPortfolioDetails() {
      try {
        const details = await getPortfolioDetails(futureForm.portfolioId);
        if (active) {
          setSelectedPortfolioDetails(details);
        }
      } catch {
        if (active) {
          setSelectedPortfolioDetails(null);
        }
      }
    }

    loadPortfolioDetails();

    return () => {
      active = false;
    };
  }, [futureForm.mode, futureForm.portfolioId]);

  const selectedInstrumentQuote = useMemo(
    () => quotes.find((item) => normalizeCode(item.symbol) === normalizeCode(futureForm.instrumentCode)) || null,
    [quotes, futureForm.instrumentCode],
  );

  const futureScenario = useMemo(() => {
    const percentChange = Number(futureForm.percentChange);
    if (!Number.isFinite(percentChange)) {
      return null;
    }

    const ratio = percentChange / 100;

    if (futureForm.mode === "instrument") {
      const basePrice = Number(selectedInstrumentQuote?.price);
      if (!Number.isFinite(basePrice)) {
        return null;
      }

      const projectedPrice = basePrice * (1 + ratio);
      return {
        type: "instrument",
        baseValue: basePrice,
        projectedValue: projectedPrice,
        difference: projectedPrice - basePrice,
      };
    }

    const portfolioValue = Number(
      selectedPortfolioDetails?.summary?.currentValue ?? selectedPortfolioDetails?.summary?.totalCurrentValue,
    );
    if (!Number.isFinite(portfolioValue)) {
      return null;
    }

    const projectedValue = portfolioValue * (1 + ratio);
    return {
      type: "portfolio",
      baseValue: portfolioValue,
      projectedValue,
      difference: projectedValue - portfolioValue,
    };
  }, [futureForm.mode, futureForm.percentChange, selectedInstrumentQuote, selectedPortfolioDetails]);

  async function handlePastSimulation(event) {
    event.preventDefault();
    try {
      setPastLoading(true);
      setPastError("");
      setPastResult(null);

      const simulationDate = pastForm.date;
      const amount = Number(pastForm.amount);
      const instrumentCode = normalizeCode(pastForm.instrumentCode);

      const [history, currentQuote] = await Promise.all([
        getMarketHistory(instrumentCode, {
          from: simulationDate,
          to: simulationDate,
        }),
        getMarketQuote(instrumentCode),
      ]);

      const historicalPoint = history?.[0] ?? null;
      const historicalPrice = Number(historicalPoint?.closePrice);
      const currentPrice = Number(currentQuote?.price);

      if (!Number.isFinite(historicalPrice) || !Number.isFinite(currentPrice) || !Number.isFinite(amount) || amount <= 0) {
        setPastResult(null);
        setPastError(t("simulation.past.insufficientData"));
        return;
      }

      const quantity = amount / historicalPrice;
      const todayValue = quantity * currentPrice;
      const profitLoss = todayValue - amount;

      setPastResult({
        symbol: instrumentCode,
        historicalPrice,
        currentPrice,
        quantity,
        amount,
        todayValue,
        profitLoss,
        priceDate: historicalPoint?.priceDate || simulationDate,
        source: currentQuote?.source || historicalPoint?.source || "-",
      });
    } catch (err) {
      setPastError(extractErrorMessage(err, t("simulation.past.calculateError")));
    } finally {
      setPastLoading(false);
    }
  }

  return (
    <div className="dashboard-stack simulation-shell">
      <PageHeader
        eyebrow={t("simulation.eyebrow")}
        title={t("simulation.title")}
        description={t("simulation.description")}
      />

      {loading ? <LoadingSpinner label={t("simulation.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <section className="simulation-grid">
          <section className="panel-surface simulation-panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("simulation.past.eyebrow")}</p>
                <h3>{t("simulation.past.title")}</h3>
              </div>
            </div>

            <form className="simulation-form" onSubmit={handlePastSimulation}>
              <label className="portfolio-field">
                <span>{t("simulation.past.instrument")}</span>
                <select
                  required
                  value={pastForm.instrumentCode}
                  onChange={(event) => setPastForm((current) => ({ ...current, instrumentCode: event.target.value }))}
                >
                  <option value="">{t("simulation.past.instrumentPlaceholder")}</option>
                  {quotes.map((item) => (
                    <option key={`${item.symbol}-${item.source}`} value={item.symbol}>
                      {item.code || item.symbol} {item.displayName ? `- ${item.displayName}` : ""}
                    </option>
                  ))}
                </select>
              </label>

              <div className="simulation-form-grid">
                <label className="portfolio-field">
                  <span>{t("simulation.past.date")}</span>
                  <input
                    required
                    type="date"
                    value={pastForm.date}
                    onChange={(event) => setPastForm((current) => ({ ...current, date: event.target.value }))}
                  />
                </label>
                <label className="portfolio-field">
                  <span>{t("simulation.past.amount")}</span>
                  <input
                    required
                    type="number"
                    step="any"
                    min="0.01"
                    value={pastForm.amount}
                    onChange={(event) => setPastForm((current) => ({ ...current, amount: event.target.value }))}
                    placeholder="10000"
                  />
                </label>
              </div>

              <div className="instrument-action-footer">
                <button type="submit" disabled={pastLoading}>
                  {pastLoading ? t("simulation.past.submitting") : t("simulation.past.submit")}
                </button>
              </div>
            </form>

            {pastError ? <ErrorMessage message={pastError} /> : null}
            {!pastLoading && !pastError && !pastResult ? (
              <EmptyState
                title={t("simulation.past.emptyTitle")}
                description={t("simulation.past.emptyDescription")}
              />
            ) : null}

            {pastResult ? (
              <div className="cards-grid compact">
                <SummaryCard title={t("simulation.past.cards.buyPrice")} value={formatCurrency(pastResult.historicalPrice)} subtitle={pastResult.priceDate} tone="neutral" />
                <SummaryCard title={t("simulation.past.cards.currentPrice")} value={formatCurrency(pastResult.currentPrice)} subtitle={pastResult.source} tone="cool" />
                <SummaryCard title={t("simulation.past.cards.quantity")} value={formatNumber(pastResult.quantity, 6)} subtitle={t("simulation.past.cards.quantitySubtitle")} tone="neutral" />
                <SummaryCard title={t("simulation.past.cards.todayValue")} value={formatCurrency(pastResult.todayValue)} subtitle={formatSigned(pastResult.profitLoss)} tone={pastResult.profitLoss >= 0 ? "cool" : "warm"} />
              </div>
            ) : null}
          </section>

          <section className="panel-surface simulation-panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("simulation.future.eyebrow")}</p>
                <h3>{t("simulation.future.title")}</h3>
              </div>
            </div>

            {/* TODO: backend tarafinda senaryo/simulasyon endpointi yok. Bu alan mevcut quote ve portfoy currentValue uzerinden frontend hesaplama yapar. */}
            <div className="simulation-mode-switch">
              <button
                type="button"
                className={`table-chip-button ${futureForm.mode === "instrument" ? "active" : ""}`}
                onClick={() => setFutureForm((current) => ({ ...current, mode: "instrument" }))}
              >
                {t("simulation.future.instrumentMode")}
              </button>
              <button
                type="button"
                className={`table-chip-button ${futureForm.mode === "portfolio" ? "active" : ""}`}
                onClick={() => setFutureForm((current) => ({ ...current, mode: "portfolio" }))}
              >
                {t("simulation.future.portfolioMode")}
              </button>
            </div>

            <div className="simulation-form">
              {futureForm.mode === "instrument" ? (
                <label className="portfolio-field">
                  <span>{t("simulation.future.instrument")}</span>
                  <select
                    value={futureForm.instrumentCode}
                    onChange={(event) => setFutureForm((current) => ({ ...current, instrumentCode: event.target.value }))}
                  >
                    <option value="">{t("simulation.past.instrumentPlaceholder")}</option>
                    {quotes.map((item) => (
                      <option key={`${item.symbol}-${item.source}`} value={item.symbol}>
                        {item.code || item.symbol} {item.displayName ? `- ${item.displayName}` : ""}
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <label className="portfolio-field">
                  <span>{t("simulation.future.portfolio")}</span>
                  <select
                    value={futureForm.portfolioId}
                    onChange={(event) => setFutureForm((current) => ({ ...current, portfolioId: event.target.value }))}
                  >
                    <option value="">{t("simulation.future.portfolioPlaceholder")}</option>
                    {portfolios.map((item) => (
                      <option key={item.portfolioId} value={item.portfolioId}>
                        {item.portfolioName}
                      </option>
                    ))}
                  </select>
                </label>
              )}

              <label className="portfolio-field">
                <span>{t("simulation.future.percentChange")}</span>
                <input
                  type="number"
                  step="any"
                  value={futureForm.percentChange}
                  onChange={(event) => setFutureForm((current) => ({ ...current, percentChange: event.target.value }))}
                  placeholder={t("simulation.future.percentPlaceholder")}
                />
              </label>
            </div>

            {!futureScenario ? (
              <EmptyState
                title={t("simulation.future.emptyTitle")}
                description={t("simulation.future.emptyDescription")}
              />
            ) : (
              <div className="cards-grid compact">
                <SummaryCard
                  title={futureScenario.type === "instrument" ? t("simulation.future.cards.currentPrice") : t("simulation.future.cards.currentPortfolioValue")}
                  value={formatCurrency(futureScenario.baseValue)}
                  subtitle={t("simulation.future.cards.baseValue")}
                  tone="neutral"
                />
                <SummaryCard
                  title={t("simulation.future.cards.scenarioImpact")}
                  value={formatPercent(futureForm.percentChange)}
                  subtitle={t("simulation.future.cards.manualAssumption")}
                  tone={Number(futureForm.percentChange) >= 0 ? "cool" : "warm"}
                />
                <SummaryCard
                  title={t("simulation.future.cards.projectedResult")}
                  value={formatCurrency(futureScenario.projectedValue)}
                  subtitle={t("simulation.future.cards.projectedValue")}
                  tone={futureScenario.difference >= 0 ? "cool" : "warm"}
                />
                <SummaryCard
                  title={t("simulation.future.cards.netDifference")}
                  value={formatCurrency(futureScenario.difference)}
                  subtitle={formatSigned(futureScenario.difference)}
                  tone={futureScenario.difference >= 0 ? "cool" : "warm"}
                />
              </div>
            )}

            <section className="simulation-note-card">
              <strong>{t("simulation.note.title")}</strong>
              <p>
                {t("simulation.note.description")}
              </p>
              {selectedPortfolioDetails?.summary?.currentValue ? (
                <span>{t("simulation.note.portfolioValue", { value: formatCurrency(selectedPortfolioDetails.summary.currentValue) })}</span>
              ) : null}
            </section>
          </section>
        </section>
      ) : null}
    </div>
  );
}

function normalizeCode(value) {
  if (value == null) {
    return "";
  }

  const rawValue = String(value).trim();
  if (rawValue.toUpperCase().startsWith("TCMB:")) {
    return rawValue.toUpperCase();
  }

  return rawValue.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function formatSigned(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "-";
  }

  return `${numeric >= 0 ? "+" : ""}${formatCurrency(numeric)}`;
}

