import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  ArrowDownRight,
  ArrowUpRight,
  CalendarDays,
  FlaskConical,
  History,
  Search,
  TrendingUp,
  X,
} from "lucide-react";
import { getMarketQuote, getPriceOnDate } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { useInstrumentSearch, useMarketBySymbol } from "../hooks/useMarketQueries";
import { usePortfolioSummary, useUserPortfolios } from "../hooks/usePortfolioQueries";
import { formatNumber, formatPercent } from "../utils/formatters";

const MAX_COMPARE_INSTRUMENTS = 5;

export default function SimulationPage() {
  const { t } = useTranslation();
  const { formatAmount } = useCurrency();
  const [pastResult, setPastResult] = useState(null);
  const [futureResult, setFutureResult] = useState(null);
  const [recentSimulations, setRecentSimulations] = useState([]);

  const handleSimulationResult = useCallback((result) => {
    if (!result) {
      return;
    }

    if (result.kind === "past") {
      setPastResult(result);
    } else if (result.kind === "future") {
      setFutureResult(result);
    }

    setRecentSimulations((current) => [result, ...current.filter((item) => item.id !== result.id)].slice(0, 6));
  }, []);

  return (
    <div className="dashboard-stack simulation-lab-shell">
      <section className="panel-surface simulation-lab-hero">
        <div className="simulation-lab-hero-main">
          <div className="simulation-lab-hero-copy">
            <div className="simulation-lab-badge">
              <FlaskConical size={14} aria-hidden="true" />
              <span>{t("simulation.ui.badge")}</span>
            </div>
            <div>
              <h1>{t("simulation.ui.heroTitle")}</h1>
              <p>{t("simulation.ui.heroDescription")}</p>
            </div>
          </div>
        </div>
      </section>

      <section className="simulation-lab-main-grid">
        <PastSimulationPanel onResult={handleSimulationResult} result={pastResult} />
        <FutureSimulationPanel onResult={handleSimulationResult} result={futureResult} />
      </section>

      <SimulationHistoryPanel results={recentSimulations} formatAmount={formatAmount} />
    </div>
  );
}

const PastSimulationPanel = memo(function PastSimulationPanel({ onResult, result }) {
  const { t } = useTranslation();
  const pastDateRef = useRef(null);
  const pastAmountRef = useRef(null);
  const compareDateRef = useRef(null);
  const compareAmountRef = useRef(null);

  const [pastMode, setPastMode] = useState("single");
  const [pastAttempted, setPastAttempted] = useState(false);
  const [pastForm, setPastForm] = useState({ instrumentCode: "", instrumentLabel: "", instrumentType: "" });
  const [pastLoading, setPastLoading] = useState(false);
  const [pastError, setPastError] = useState("");

  const [compareInstruments, setCompareInstruments] = useState([]);
  const [compareLoading, setCompareLoading] = useState(false);
  const [compareError, setCompareError] = useState("");
  const [compareResult, setCompareResult] = useState(null);
  const [compareAttempted, setCompareAttempted] = useState(false);

  const handlePastInstrumentChange = useCallback((option) => {
    setPastForm({
      instrumentCode: option?.value ?? "",
      instrumentLabel: option?.label ?? "",
      instrumentType: option?.type ?? "",
    });
  }, []);

  const handleAddCompareInstrument = useCallback((option) => {
    if (!option?.value) return;
    setCompareInstruments((prev) => {
      if (prev.length >= MAX_COMPARE_INSTRUMENTS) return prev;
      if (prev.some((i) => i.code === option.value)) return prev;
      return [...prev, { code: option.value, label: option.label, type: option.type }];
    });
  }, []);

  const handleRemoveCompareInstrument = useCallback((code) => {
    setCompareInstruments((prev) => prev.filter((i) => i.code !== code));
  }, []);

  async function handlePastSimulation(event) {
    event.preventDefault();
    try {
      setPastAttempted(true);
      setPastLoading(true);
      setPastError("");

      const simulationDate = pastDateRef.current?.value ?? "";
      const amount = Number(pastAmountRef.current?.value ?? "");
      const instrumentCode = normalizeCode(pastForm.instrumentCode);

      const [historicalPoint, currentQuote] = await Promise.all([
        getPriceOnDate(instrumentCode, simulationDate),
        getMarketQuote(instrumentCode),
      ]);

      const historicalPrice = Number(historicalPoint?.price);
      const currentPrice = Number(currentQuote?.price);

      if (!Number.isFinite(historicalPrice) || !Number.isFinite(currentPrice) || !Number.isFinite(amount) || amount <= 0) {
        setPastError(t("simulation.past.insufficientData"));
        return;
      }

      const quantity = amount / historicalPrice;
      const todayValue = quantity * currentPrice;
      const profitLoss = todayValue - amount;
      const percentReturn = amount > 0 ? (profitLoss / amount) * 100 : null;

      onResult({
        id: `past-${instrumentCode}-${simulationDate}-${amount}`,
        kind: "past",
        heading: t("simulation.ui.pastPanelTitle"),
        sourceLabel: t("simulation.ui.pastSourceLabel"),
        selectedLabel: getInstrumentResultLabel(pastForm.instrumentLabel || instrumentCode, instrumentCode),
        instrumentType: pastForm.instrumentType || currentQuote?.instrumentType,
        scenarioLabel: simulationDate,
        primaryLabel: t("simulation.ui.metrics.currentValue"),
        primaryValue: todayValue,
        totalReturn: profitLoss,
        percentReturn,
        profitLoss,
        secondaryValue: amount,
        secondaryLabel: t("simulation.ui.metrics.initialInvestment"),
        metaLine: t("simulation.ui.quantityValue", { value: formatNumber(Math.trunc(quantity), 0) }),
        supportLine: historicalPoint?.date || simulationDate,
        sourceDetail: currentQuote?.source || historicalPoint?.source || "-",
        chartData: [
          { label: t("simulation.ui.chartLabels.buy"), value: amount },
          { label: t("simulation.ui.chartLabels.today"), value: todayValue },
        ],
        createdAt: new Date().toISOString(),
      });
    } catch (err) {
      setPastError(extractErrorMessage(err, t("simulation.past.calculateError")));
    } finally {
      setPastLoading(false);
    }
  }

  async function handleCompareSimulation(event) {
    event.preventDefault();
    setCompareAttempted(true);
    setCompareLoading(true);
    setCompareError("");

    try {
      const date = compareDateRef.current?.value ?? "";
      const amount = Number(compareAmountRef.current?.value ?? "");

      if (!date || !amount || amount <= 0 || compareInstruments.length < 2) {
        setCompareError(t("simulation.past.compareMinInstruments", "En az 2 enstrüman seçin."));
        setCompareLoading(false);
        return;
      }

      const rows = await Promise.all(
        compareInstruments.map(async (inst) => {
          try {
            const code = normalizeCode(inst.code);
            const [historicalPoint, currentQuote] = await Promise.all([
              getPriceOnDate(code, date),
              getMarketQuote(code),
            ]);

            const historicalPrice = Number(historicalPoint?.price);
            const currentPrice = Number(currentQuote?.price);

            if (!Number.isFinite(historicalPrice) || !Number.isFinite(currentPrice)) {
              return null;
            }

            const quantity = amount / historicalPrice;
            const todayValue = quantity * currentPrice;
            const profitLoss = todayValue - amount;
            const percentReturn = (profitLoss / amount) * 100;

            return {
              code: inst.code,
              label: inst.label.split(" - ")[0],
              historicalPrice,
              currentPrice,
              quantity,
              todayValue,
              profitLoss,
              percentReturn,
            };
          } catch {
            return null;
          }
        }),
      );

      const validRows = rows.filter(Boolean).sort((a, b) => b.percentReturn - a.percentReturn);

      if (!validRows.length) {
        setCompareError(t("simulation.past.insufficientData"));
        setCompareLoading(false);
        return;
      }

      const resultObj = {
        id: `compare-${date}-${amount}-${compareInstruments.map((i) => i.code).join(",")}`,
        kind: "compare",
        heading: t("simulation.past.compareMode", "Karşılaştırmalı"),
        sourceLabel: t("simulation.past.compareMode", "Karşılaştırmalı"),
        selectedLabel: `${validRows.length} enstrüman`,
        scenarioLabel: date,
        date,
        amount,
        rows: validRows,
        profitLoss: validRows[0]?.profitLoss ?? 0,
        percentReturn: validRows[0]?.percentReturn ?? 0,
        primaryValue: validRows[0]?.todayValue ?? 0,
        createdAt: new Date().toISOString(),
      };

      setCompareResult(resultObj);
      onResult(resultObj);
    } catch (err) {
      setCompareError(extractErrorMessage(err, t("simulation.past.calculateError")));
    } finally {
      setCompareLoading(false);
    }
  }

  return (
    <section className="panel-surface simulation-lab-card simulation-lab-card--panel">
      <div className="simulation-lab-card-top">
        <div className="simulation-lab-card-head">
          <div className="simulation-lab-card-head-icon">
            <History size={16} aria-hidden="true" />
          </div>
          <div>
            <h2>{t("simulation.ui.pastPanelTitle")}</h2>
            <p>{t("simulation.ui.pastPanelDescription")}</p>
          </div>
        </div>

        <div className="simulation-lab-mode-switch">
          <button
            type="button"
            className={pastMode === "single" ? "simulation-lab-mode-chip active" : "simulation-lab-mode-chip"}
            onClick={() => setPastMode("single")}
          >
            {t("simulation.past.singleMode", "Tek Enstrüman")}
          </button>
          <button
            type="button"
            className={pastMode === "compare" ? "simulation-lab-mode-chip active" : "simulation-lab-mode-chip"}
            onClick={() => setPastMode("compare")}
          >
            {t("simulation.past.compareMode", "Karşılaştırmalı")}
          </button>
        </div>

        {pastMode === "single" ? (
          <form className="simulation-lab-form" onSubmit={handlePastSimulation}>
            <div className="simulation-lab-field">
              <SearchableInstrumentField
                required
                value={pastForm.instrumentCode}
                selectedLabel={pastForm.instrumentLabel}
                placeholder={t("simulation.past.instrumentPlaceholder")}
                searchPlaceholder={t("simulation.past.instrumentPlaceholder")}
                onChange={handlePastInstrumentChange}
              />
            </div>

            <div className="simulation-lab-inline-fields">
              <label className="simulation-lab-field">
                <span>{t("simulation.ui.dateLabel")}</span>
                <div className="simulation-lab-input-wrap">
                  <CalendarDays size={16} aria-hidden="true" />
                  <input ref={pastDateRef} required type="date" defaultValue="" />
                </div>
              </label>

              <label className="simulation-lab-field">
                <span>{t("simulation.ui.amountLabel")}</span>
                <div className="simulation-lab-input-wrap simulation-lab-input-wrap--with-toggle">
                  <input
                    ref={pastAmountRef}
                    required
                    type="number"
                    step="any"
                    min="0.01"
                    defaultValue=""
                    placeholder={t("simulation.ui.amountPlaceholder")}
                  />
                  <CurrencyToggle className="simulation-lab-inline-currency-toggle" />
                </div>
              </label>
            </div>

            <button type="submit" className="simulation-lab-primary-button" disabled={pastLoading}>
              {pastLoading ? t("simulation.past.submitting") : t("simulation.ui.pastSubmit")}
            </button>
          </form>
        ) : (
          <form className="simulation-lab-form" onSubmit={handleCompareSimulation}>
            <div className="simulation-lab-inline-fields">
              <label className="simulation-lab-field">
                <span>{t("simulation.ui.amountLabel")}</span>
                <div className="simulation-lab-input-wrap simulation-lab-input-wrap--with-toggle">
                  <input
                    ref={compareAmountRef}
                    required
                    type="number"
                    step="any"
                    min="0.01"
                    defaultValue=""
                    placeholder={t("simulation.ui.amountPlaceholder")}
                  />
                  <CurrencyToggle className="simulation-lab-inline-currency-toggle" />
                </div>
              </label>

              <label className="simulation-lab-field">
                <span>{t("simulation.ui.dateLabel")}</span>
                <div className="simulation-lab-input-wrap">
                  <CalendarDays size={16} aria-hidden="true" />
                  <input ref={compareDateRef} required type="date" defaultValue="" />
                </div>
              </label>
            </div>

            <div className="simulation-lab-field">
              <span>
                {t("simulation.past.compareInstrumentsLabel", "Enstrümanlar")}
                {" "}
                <span className="simulation-lab-compare-count">
                  ({compareInstruments.length}/{MAX_COMPARE_INSTRUMENTS})
                </span>
              </span>
              {compareInstruments.length < MAX_COMPARE_INSTRUMENTS && (
                <SearchableInstrumentField
                  value=""
                  selectedLabel=""
                  placeholder={t("simulation.past.compareAddPlaceholder", "Enstrüman ekle")}
                  searchPlaceholder={t("simulation.past.compareAddPlaceholder", "Enstrüman ekle")}
                  onChange={handleAddCompareInstrument}
                />
              )}
              {compareInstruments.length > 0 && (
                <div className="simulation-lab-compare-chips">
                  {compareInstruments.map((inst) => (
                    <span key={inst.code} className="simulation-lab-compare-chip">
                      {inst.label.split(" - ")[0]}
                      <button
                        type="button"
                        className="simulation-lab-compare-chip-remove"
                        onClick={() => handleRemoveCompareInstrument(inst.code)}
                        aria-label={`${inst.code} kaldır`}
                      >
                        <X size={11} strokeWidth={2.5} />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <button
              type="submit"
              className="simulation-lab-primary-button"
              disabled={compareLoading || compareInstruments.length < 2}
            >
              {compareLoading ? t("simulation.past.submitting") : t("simulation.ui.pastSubmit")}
            </button>
          </form>
        )}

        <div className={`simulation-lab-inline-feedback${(pastMode === "single" ? pastAttempted : compareAttempted) ? " is-visible" : ""}`}>
          {pastMode === "single" ? (
            <>
              {pastLoading ? <LoadingSpinner label={t("simulation.past.submitting")} /> : null}
              {!pastLoading && pastError ? <ErrorMessage message={pastError} /> : null}
            </>
          ) : (
            <>
              {compareLoading ? <LoadingSpinner label={t("simulation.past.submitting")} /> : null}
              {!compareLoading && compareError ? <ErrorMessage message={compareError} /> : null}
            </>
          )}
        </div>
      </div>

      {pastMode === "single" ? (
        <InlineSimulationResult
          result={result}
          emptyTitle={t("simulation.ui.emptyTitle")}
          emptyCopy={t("simulation.ui.pastEmptyCopy")}
        />
      ) : (
        <CompareResultSection
          result={compareResult}
          emptyTitle={t("simulation.ui.emptyTitle")}
          emptyCopy={t("simulation.past.compareEmptyCopy", "Enstrümanları seçip hesapla butonuna basın.")}
        />
      )}
    </section>
  );
});

const CompareResultSection = memo(function CompareResultSection({ result, emptyTitle, emptyCopy }) {
  const { formatAmount } = useCurrency();

  return (
    <div className={result ? "simulation-lab-result-section is-ready" : "simulation-lab-result-section"}>
      <div className="simulation-lab-result-section-inner">
        {!result ? (
          <div className="simulation-lab-empty-result">
            <strong>{emptyTitle}</strong>
            <p>{emptyCopy}</p>
          </div>
        ) : (
          <div className="simulation-lab-compare-stack">
            <div className="simulation-lab-compare-table-wrap">
              <table className="simulation-lab-compare-table">
                <thead>
                  <tr>
                    <th>Enstrüman</th>
                    <th>Bugünkü Değer</th>
                    <th>Getiri</th>
                    <th>Getiri %</th>
                  </tr>
                </thead>
                <tbody>
                  {result.rows.map((row, idx) => (
                    <tr key={row.code} className={idx === 0 ? "simulation-lab-compare-row--best" : ""}>
                      <td><strong>{row.label}</strong></td>
                      <td>{formatAmount(row.todayValue)}</td>
                      <td className={row.profitLoss >= 0 ? "tone-positive" : "tone-negative"}>
                        {row.profitLoss >= 0 ? "+" : ""}{formatAmount(row.profitLoss)}
                      </td>
                      <td className={row.percentReturn >= 0 ? "tone-positive" : "tone-negative"}>
                        <strong>{formatSignedPercent(row.percentReturn)}</strong>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="simulation-lab-compare-summary">
              {buildCompareSummary(result.rows, result.amount, result.date, formatAmount)}
            </p>
          </div>
        )}
      </div>
    </div>
  );
});

const FutureSimulationPanel = memo(function FutureSimulationPanel({ onResult, result }) {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const futurePercentInputRef = useRef(null);
  const futureQuantityInputRef = useRef(null);
  const [futureAttempted, setFutureAttempted] = useState(false);
  const [futureLoading, setFutureLoading] = useState(false);
  const [futureForm, setFutureForm] = useState({
    mode: "instrument",
    instrumentCode: "",
    instrumentLabel: "",
    instrumentType: "",
    portfolioId: "",
  });

  const { data: portfolios = [], isLoading: portfoliosLoading, error: portfoliosError } = useUserPortfolios(userId);
  const { data: selectedPortfolioSummary } = usePortfolioSummary(
    futureForm.mode === "portfolio" ? futureForm.portfolioId : null,
  );
  const {
    data: selectedInstrumentQuote,
    isLoading: instrumentQuoteLoading,
    error: instrumentQuoteError,
  } = useMarketBySymbol(
    futureForm.mode === "instrument" ? futureForm.instrumentCode : null,
    { enabled: futureForm.mode === "instrument" && !!futureForm.instrumentCode },
  );

  useEffect(() => {
    if (portfolios.length > 0 && !futureForm.portfolioId) {
      setFutureForm((current) => ({ ...current, portfolioId: current.portfolioId || portfolios[0]?.portfolioId || "" }));
    }
  }, [portfolios, futureForm.portfolioId]);

  const portfolioOptions = useMemo(
    () => portfolios.map((item) => ({
      key: item.portfolioId,
      value: item.portfolioId,
      label: item.portfolioName,
    })),
    [portfolios],
  );

  const selectedPortfolioLabel = useMemo(
    () => portfolioOptions.find((item) => String(item.value) === String(futureForm.portfolioId))?.label || futureForm.portfolioId,
    [futureForm.portfolioId, portfolioOptions],
  );

  const handleFutureModeChange = useCallback((mode) => {
    setFutureForm((current) => ({ ...current, mode }));
  }, []);

  const handleFutureInstrumentChange = useCallback((option) => {
    setFutureForm((current) => ({
      ...current,
      instrumentCode: option?.value ?? "",
      instrumentLabel: option?.label ?? "",
      instrumentType: option?.type ?? "",
    }));
  }, []);

  const handleFuturePortfolioChange = useCallback((value) => {
    setFutureForm((current) => ({ ...current, portfolioId: value }));
  }, []);

  const error = portfoliosError || instrumentQuoteError
    ? extractErrorMessage(portfoliosError || instrumentQuoteError, t("simulation.loadError"))
    : "";

  const handleFutureSimulation = useCallback(async (event) => {
    event.preventDefault();
    setFutureAttempted(true);
    setFutureLoading(true);

    await new Promise((resolve) => window.requestAnimationFrame(resolve));

    const percentChange = Number(futurePercentInputRef.current?.value ?? "");
    if (!Number.isFinite(percentChange)) {
      setFutureLoading(false);
      return;
    }

    const ratio = percentChange / 100;

    if (futureForm.mode === "instrument") {
      const basePrice = Number(selectedInstrumentQuote?.price ?? selectedInstrumentQuote?.sellRate ?? selectedInstrumentQuote?.buyRate);
      const quantityInput = futureQuantityInputRef.current?.value ?? "";
      const quantity = quantityInput === "" ? 1 : Number(quantityInput);
      if (!Number.isFinite(basePrice)) {
        setFutureLoading(false);
        return;
      }
      if (!Number.isFinite(quantity) || quantity <= 0) {
        setFutureLoading(false);
        return;
      }

      const currentValue = basePrice * quantity;
      const projectedValue = currentValue * (1 + ratio);
      onResult({
        id: `future-instrument-${futureForm.instrumentCode}-${percentChange}-${quantity}`,
        kind: "future",
        heading: t("simulation.ui.futurePanelTitle"),
        sourceLabel: t("simulation.ui.futureInstrumentSourceLabel"),
        selectedLabel: getInstrumentResultLabel(futureForm.instrumentLabel || futureForm.instrumentCode, futureForm.instrumentCode),
        instrumentType: futureForm.instrumentType || selectedInstrumentQuote?.instrumentType,
        scenarioLabel: `${percentChange > 0 ? "+" : ""}${percentChange}%`,
        primaryLabel: t("simulation.ui.metrics.projectedResult"),
        primaryValue: projectedValue,
        totalReturn: projectedValue - currentValue,
        percentReturn: percentChange,
        profitLoss: projectedValue - currentValue,
        secondaryValue: currentValue,
        secondaryLabel: t("simulation.ui.metrics.currentValue"),
        metaLine: t("simulation.ui.quantityValue", { value: formatNumber(Math.trunc(quantity), 0) }),
        supportLine: t("simulation.ui.manualScenario"),
        sourceDetail: futureForm.mode === "instrument" ? t("simulation.ui.instrumentBasedCalculation") : t("simulation.ui.portfolioBasedCalculation"),
        chartData: [
          { label: t("simulation.ui.chartLabels.today"), value: currentValue },
          { label: t("simulation.ui.chartLabels.scenario"), value: projectedValue },
        ],
        createdAt: new Date().toISOString(),
      });
      setFutureLoading(false);
      return;
    }

    const portfolioValue = Number(
      selectedPortfolioSummary?.currentValue ?? selectedPortfolioSummary?.totalCurrentValue,
    );
    if (!Number.isFinite(portfolioValue)) {
      setFutureLoading(false);
      return;
    }

    const projectedValue = portfolioValue * (1 + ratio);
    onResult({
      id: `future-portfolio-${futureForm.portfolioId}-${percentChange}`,
      kind: "future",
      heading: t("simulation.ui.futurePanelTitle"),
      sourceLabel: t("simulation.ui.futurePortfolioSourceLabel"),
      selectedLabel: selectedPortfolioLabel,
      instrumentType: "PORTFOLIO",
      scenarioLabel: `${percentChange > 0 ? "+" : ""}${percentChange}%`,
      primaryLabel: t("simulation.ui.metrics.projectedResult"),
      primaryValue: projectedValue,
      totalReturn: projectedValue - portfolioValue,
      percentReturn: percentChange,
      profitLoss: projectedValue - portfolioValue,
      secondaryValue: portfolioValue,
      secondaryLabel: t("simulation.ui.metrics.currentPortfolioValue"),
      metaLine: t("simulation.ui.portfolioCalculatedOnTotal"),
      supportLine: t("simulation.ui.manualScenario"),
      sourceDetail: t("simulation.ui.portfolioBasedCalculation"),
      chartData: [
        { label: t("simulation.ui.chartLabels.today"), value: portfolioValue },
        { label: t("simulation.ui.chartLabels.scenario"), value: projectedValue },
      ],
      createdAt: new Date().toISOString(),
    });
    setFutureLoading(false);
  }, [futureForm, onResult, selectedInstrumentQuote, selectedPortfolioLabel, selectedPortfolioSummary]);

  return (
    <section className="panel-surface simulation-lab-card simulation-lab-card--panel">
      <div className="simulation-lab-card-top">
        <div className="simulation-lab-card-head">
          <div className="simulation-lab-card-head-icon">
            <TrendingUp size={16} aria-hidden="true" />
          </div>
          <div>
            <h2>{t("simulation.ui.futurePanelTitle")}</h2>
            <p>{t("simulation.ui.futurePanelDescription")}</p>
          </div>
        </div>

        {portfoliosLoading ? <LoadingSpinner label={t("simulation.loading")} /> : null}
        {!portfoliosLoading && error ? <ErrorMessage message={error} /> : null}

        {!portfoliosLoading && !error ? (
          <form className="simulation-lab-form" onSubmit={handleFutureSimulation}>
            <div className="simulation-lab-mode-switch">
              <button
                type="button"
                className={futureForm.mode === "instrument" ? "simulation-lab-mode-chip active" : "simulation-lab-mode-chip"}
                onClick={() => handleFutureModeChange("instrument")}
              >
                {t("simulation.future.instrumentMode")}
              </button>
              <button
                type="button"
                className={futureForm.mode === "portfolio" ? "simulation-lab-mode-chip active" : "simulation-lab-mode-chip"}
                onClick={() => handleFutureModeChange("portfolio")}
              >
                {t("simulation.future.portfolioMode")}
              </button>
            </div>

            {futureForm.mode === "instrument" ? (
              <div className="simulation-lab-field">
                <SearchableInstrumentField
                  value={futureForm.instrumentCode}
                  selectedLabel={futureForm.instrumentLabel}
                  placeholder={t("simulation.past.instrumentPlaceholder")}
                  searchPlaceholder={t("simulation.past.instrumentPlaceholder")}
                  onChange={handleFutureInstrumentChange}
                />
              </div>
            ) : (
              <label className="simulation-lab-field">
                <span>{t("simulation.future.portfolio")}</span>
                <PortfolioOptionsSelect
                  value={futureForm.portfolioId}
                  placeholder={t("simulation.future.portfolioPlaceholder")}
                  options={portfolioOptions}
                  onChange={handleFuturePortfolioChange}
                />
              </label>
            )}

            <div className="future-scenario-percent-panel">
              <div className="future-scenario-percent-layout">
                <div className="future-scenario-input-row">
                  <label className="future-scenario-mini-field" htmlFor="future-percent-change">
                    <span className="future-scenario-percent-label">{t("simulation.ui.percentLabel")}</span>
                    <div className="simulation-lab-input-wrap simulation-lab-input-wrap--with-toggle future-scenario-percent-input">
                      <input
                        id="future-percent-change"
                        ref={futurePercentInputRef}
                        type="number"
                        step="any"
                        defaultValue=""
                        placeholder={t("simulation.future.percentPlaceholder")}
                      />
                      <CurrencyToggle className="simulation-lab-inline-currency-toggle" />
                    </div>
                  </label>

                  {futureForm.mode === "instrument" ? (
                    <label className="future-scenario-mini-field">
                      <span className="future-scenario-percent-label">Miktar</span>
                      <div className="simulation-lab-input-wrap future-scenario-quantity-input">
                        <input
                          ref={futureQuantityInputRef}
                          type="number"
                          step="any"
                          min="0.0001"
                          defaultValue=""
                          placeholder="Adet / lot"
                          aria-label="Adet veya lot sayısı"
                        />
                      </div>
                    </label>
                  ) : null}
                </div>

                <div className="future-scenario-percent-actions">
                  <button type="submit" className="simulation-lab-primary-button future-scenario-submit" disabled={futureLoading || instrumentQuoteLoading}>
                    {futureLoading ? t("simulation.future.submitting") : t("simulation.future.submit")}
                  </button>
                </div>
              </div>
            </div>
          </form>
        ) : null}

        <div className={`simulation-lab-inline-feedback${futureAttempted ? " is-visible" : ""}`}>
          {futureAttempted && futureLoading ? <LoadingSpinner label={t("simulation.future.submitting")} /> : null}
        </div>
      </div>

      <InlineSimulationResult
        result={result}
        emptyTitle={t("simulation.ui.emptyTitle")}
        emptyCopy={t("simulation.ui.futureEmptyCopy")}
      />
    </section>
  );
});

const InlineSimulationResult = memo(function InlineSimulationResult({ result, emptyTitle, emptyCopy }) {
  const { t } = useTranslation();
  const { formatAmount } = useCurrency();
  const positive = Number(result?.profitLoss) >= 0;

  return (
    <div className={result ? "simulation-lab-result-section is-ready" : "simulation-lab-result-section"}>
      <div className="simulation-lab-result-section-inner">
        {!result ? (
          <div className="simulation-lab-empty-result">
            <strong>{emptyTitle}</strong>
            <p>{emptyCopy}</p>
          </div>
        ) : (
          <div className="simulation-lab-result-stack">
            <div className="simulation-lab-result-hero">
              <div className="simulation-lab-result-copy">
                <div className="simulation-lab-result-title-row">
                  <strong>{result.selectedLabel}</strong>
                  {result.instrumentType ? (
                    <span className="simulation-lab-result-type-badge">
                      {t(`markets.categories.${result.instrumentType}`, formatInstrumentTypeLabel(result.instrumentType))}
                    </span>
                  ) : null}
                </div>
                <p>{result.secondaryLabel}: {formatAmount(result.secondaryValue)} · {result.supportLine}</p>
              </div>
              <div className={positive ? "simulation-lab-result-badge is-positive" : "simulation-lab-result-badge is-negative"}>
                {positive ? <ArrowUpRight size={14} /> : <ArrowDownRight size={14} />}
                <span>{formatSignedPercent(result.percentReturn)}</span>
              </div>
            </div>

            <div className="simulation-lab-result-value">
              <span>{result.primaryLabel}</span>
              <h3>{formatAmount(result.primaryValue)}</h3>
            </div>

            <div className="simulation-lab-result-foot">
              <span className="simulation-lab-result-return">
                <span className="simulation-lab-result-return-label">Toplam Getiri:</span>
                <span className={result.totalReturn >= 0 ? "simulation-lab-result-return-value is-positive" : "simulation-lab-result-return-value is-negative"}>
                  {result.totalReturn >= 0 ? "+" : ""}{formatAmount(result.totalReturn)}
                </span>
              </span>
              <small>{result.metaLine}</small>
            </div>
          </div>
        )}
      </div>
    </div>
  );
});

const SimulationHistoryPanel = memo(function SimulationHistoryPanel({ results, formatAmount }) {
  const { t } = useTranslation();
  return (
    <section className="panel-surface simulation-lab-history-card">
      <div className="simulation-lab-history-head">
        <h2>{t("simulation.ui.recentTitle")}</h2>
      </div>

      {!results.length ? (
        <p className="simulation-lab-history-empty-inline">{t("simulation.ui.recentEmptyTitle")}</p>
      ) : (
        <div className="simulation-lab-history-scroller">
          {results.map((item) =>
            item.kind === "compare" ? (
              <article key={item.id} className="simulation-lab-history-tile">
                <div className="simulation-lab-history-tile-top">
                  <span className="simulation-lab-history-tile-tag">{t("simulation.past.compareMode", "Karşılaştırmalı")}</span>
                  <strong className={item.rows?.[0]?.percentReturn >= 0 ? "is-positive" : "is-negative"}>
                    {formatSignedPercent(item.rows?.[0]?.percentReturn)}
                  </strong>
                </div>
                <strong className="simulation-lab-history-tile-title">
                  {formatAmount(item.amount)} · {item.rows?.length} enstrüman
                </strong>
                <p>{t("simulation.past.compareBest", "En yüksek")}: {item.rows?.[0]?.label}</p>
                <div className="simulation-lab-history-tile-foot">
                  <span>{item.date}</span>
                  <small>{t("simulation.past.compareMode", "Karşılaştırmalı")}</small>
                </div>
              </article>
            ) : (
              <article key={item.id} className="simulation-lab-history-tile">
                <div className="simulation-lab-history-tile-top">
                  <span className="simulation-lab-history-tile-tag">{item.sourceLabel}</span>
                  <strong className={item.profitLoss >= 0 ? "is-positive" : "is-negative"}>
                    {formatSignedPercent(item.percentReturn)}
                  </strong>
                </div>
                <strong className="simulation-lab-history-tile-title">{item.selectedLabel}</strong>
                <p>{item.scenarioLabel}</p>
                <div className="simulation-lab-history-tile-foot">
                  <span>{formatAmount(item.primaryValue)}</span>
                  <small>{item.heading}</small>
                </div>
              </article>
            ),
          )}
        </div>
      )}
    </section>
  );
});

const SearchableInstrumentField = memo(function SearchableInstrumentField({
  required = false,
  value,
  selectedLabel,
  placeholder,
  searchPlaceholder,
  onChange,
}) {
  const [search, setSearch] = useState("");
  const [isFocused, setIsFocused] = useState(false);
  const searchInputRef = useRef(null);
  const debouncedSearch = useDebouncedValue(search, 180);
  const { data: results = [] } = useInstrumentSearch(debouncedSearch, { staleTime: 60_000 });
  const showResults = isFocused && debouncedSearch.trim().length >= 2;
  const hasSelection = Boolean(value);
  const selectionText = hasSelection ? getInstrumentChipText(selectedLabel || value) : placeholder;

  const filteredOptions = useMemo(
    () => results.slice(0, 24).map((item) => ({
      key: `${item.code}-${item.type}`,
      value: item.code,
      label: `${item.code}${item.name ? ` - ${item.name}` : ""}`,
      type: item.type,
    })),
    [results],
  );

  return (
    <div className="simulation-lab-search">
      <div className="simulation-lab-input-wrap simulation-lab-input-wrap--search">
        <Search size={16} aria-hidden="true" />
        <input
          ref={searchInputRef}
          type="text"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setIsFocused(true);
          }}
          onFocus={() => setIsFocused(true)}
          onBlur={() => window.setTimeout(() => setIsFocused(false), 120)}
          placeholder={searchPlaceholder}
        />
      </div>

      <div className={hasSelection ? "simulation-lab-search-selection has-selection" : "simulation-lab-search-selection"}>
        {hasSelection ? (
          <span className="simulation-lab-selected-chip">
            <strong>{selectionText}</strong>
            <button
              type="button"
              className="simulation-lab-selected-chip-remove"
              onClick={() => onChange(null)}
              aria-label={`${selectionText} kaldır`}
            >
              <X size={12} strokeWidth={2.5} />
            </button>
          </span>
        ) : (
          <span>{selectionText}</span>
        )}
        {required && !value ? <input type="hidden" required value="" readOnly /> : null}
      </div>

      {showResults ? (
        <div className="simulation-lab-search-results" role="listbox" aria-label={placeholder}>
          {filteredOptions.map((item) => (
            <button
              key={item.key}
              type="button"
              className={item.value === value ? "simulation-lab-search-option active" : "simulation-lab-search-option"}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => {
                onChange(item);
                setSearch("");
                setIsFocused(false);
                searchInputRef.current?.blur();
              }}
            >
              {item.label}
            </button>
          ))}
          {!filteredOptions.length ? (
            <div className="simulation-lab-search-empty">{placeholder}</div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
});

const PortfolioOptionsSelect = memo(function PortfolioOptionsSelect({
  value,
  placeholder,
  options,
  onChange,
}) {
  return (
    <div className="simulation-lab-input-wrap">
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">{placeholder}</option>
        {options.map((item) => (
          <option key={item.key} value={item.value}>
            {item.label}
          </option>
        ))}
      </select>
    </div>
  );
});

function buildCompareSummary(rows, amount, date, formatAmount) {
  if (!rows?.length) return "";
  const best = rows[0];
  if (best.percentReturn >= 0) {
    return `${date} tarihinde ${formatAmount(amount)} ile ${best.label} alındığında, bugün ${formatAmount(best.todayValue)} değerine ulaşılırdı — seçilen enstrümanlar arasında en yüksek getiri.`;
  }
  return `${date} tarihinde seçilen enstrümanların tümü değer kaybetti. ${best.label} (${formatSignedPercent(best.percentReturn)}) bu seçenekler arasında en az kaybettiren oldu.`;
}

function getInstrumentResultLabel(label, fallbackCode) {
  return getInstrumentChipText(label) || normalizeCode(fallbackCode);
}

function formatInstrumentTypeLabel(type) {
  const normalized = String(type || "").trim().toUpperCase();
  const labels = {
    STOCK: "Hisse",
    FUND: "Fon",
    CRYPTO: "Kripto",
    FX: "Döviz",
    COMMODITY: "Emtia",
    INDEX: "Endeks",
    FUTURES: "Vadeli",
    BOND: "Tahvil",
    PORTFOLIO: "Portföy",
  };

  return labels[normalized] || normalized || "Enstrüman";
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

function getInstrumentChipText(label) {
  return String(label || "").split(" - ")[0].trim();
}

function formatSignedPercent(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "-";
  }
  return `${numeric >= 0 ? "+" : ""}${formatPercent(numeric)}`;
}

function useDebouncedValue(value, delayMs = 180) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timeoutId);
  }, [value, delayMs]);

  return debounced;
}
