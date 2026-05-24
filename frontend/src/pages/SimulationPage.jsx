import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  ArrowDownRight,
  ArrowUpRight,
  CalendarDays,
  FlaskConical,
  History,
  Layers3,
  Search,
  TrendingUp,
} from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getMarketHistory, getMarketQuote } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { useInstrumentSearch, useMarketBySymbol } from "../hooks/useMarketQueries";
import { usePortfolioSummary, useUserPortfolios } from "../hooks/usePortfolioQueries";
import { formatCurrency, formatNumber, formatPercent } from "../utils/formatters";

const FUTURE_PERCENT_PRESETS = [-20, -10, -5, 5, 10, 25];

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
  const [pastAttempted, setPastAttempted] = useState(false);
  const [pastForm, setPastForm] = useState({
    instrumentCode: "",
    instrumentLabel: "",
  });
  const [pastLoading, setPastLoading] = useState(false);
  const [pastError, setPastError] = useState("");

  const handlePastInstrumentChange = useCallback((option) => {
    setPastForm({
      instrumentCode: option?.value ?? "",
      instrumentLabel: option?.label ?? "",
    });
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
        selectedLabel: pastForm.instrumentLabel || instrumentCode,
        scenarioLabel: simulationDate,
        primaryLabel: t("simulation.ui.metrics.currentValue"),
        primaryValue: todayValue,
        totalReturn: profitLoss,
        percentReturn,
        profitLoss,
        secondaryValue: amount,
        secondaryLabel: t("simulation.ui.metrics.initialInvestment"),
        metaLine: t("simulation.ui.quantityValue", { value: formatNumber(quantity, 4) }),
        supportLine: historicalPoint?.priceDate || simulationDate,
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

        <div className={`simulation-lab-inline-feedback${pastAttempted ? " is-visible" : ""}`}>
          {pastLoading ? <LoadingSpinner label={t("simulation.past.submitting")} /> : null}
          {!pastLoading && pastError ? <ErrorMessage message={pastError} /> : null}
        </div>
      </div>

      <InlineSimulationResult
        result={result}
        emptyTitle={t("simulation.ui.emptyTitle")}
        emptyCopy={t("simulation.ui.pastEmptyCopy")}
      />
    </section>
  );
});

const FutureSimulationPanel = memo(function FutureSimulationPanel({ onResult, result }) {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const futurePercentInputRef = useRef(null);
  const [selectedPercentPreset, setSelectedPercentPreset] = useState(null);
  const [futureAttempted, setFutureAttempted] = useState(false);
  const [futureLoading, setFutureLoading] = useState(false);
  const [futureForm, setFutureForm] = useState({
    mode: "instrument",
    instrumentCode: "",
    instrumentLabel: "",
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
    }));
  }, []);

  const handleFuturePortfolioChange = useCallback((value) => {
    setFutureForm((current) => ({ ...current, portfolioId: value }));
  }, []);

  const handleFuturePercentPreset = useCallback((value) => {
    if (futurePercentInputRef.current) {
      futurePercentInputRef.current.value = value;
    }
    setSelectedPercentPreset(Number(value));
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
      if (!Number.isFinite(basePrice)) {
        setFutureLoading(false);
        return;
      }

      const projectedValue = basePrice * (1 + ratio);
      onResult({
        id: `future-instrument-${futureForm.instrumentCode}-${percentChange}`,
        kind: "future",
        heading: t("simulation.ui.futurePanelTitle"),
        sourceLabel: t("simulation.ui.futureInstrumentSourceLabel"),
        selectedLabel: futureForm.instrumentLabel || futureForm.instrumentCode,
        scenarioLabel: `${percentChange > 0 ? "+" : ""}${percentChange}%`,
        primaryLabel: t("simulation.ui.metrics.projectedResult"),
        primaryValue: projectedValue,
        totalReturn: projectedValue - basePrice,
        percentReturn: percentChange,
        profitLoss: projectedValue - basePrice,
        secondaryValue: basePrice,
        secondaryLabel: t("simulation.ui.metrics.currentPrice"),
        metaLine: selectedInstrumentQuote?.source || t("simulation.ui.liveMarketData"),
        supportLine: t("simulation.ui.manualScenario"),
        sourceDetail: futureForm.mode === "instrument" ? t("simulation.ui.instrumentBasedCalculation") : t("simulation.ui.portfolioBasedCalculation"),
        chartData: [
          { label: t("simulation.ui.chartLabels.today"), value: basePrice },
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

            <div className="simulation-lab-field">
              <span>{t("simulation.ui.percentLabel")}</span>
              <div className="simulation-lab-input-wrap simulation-lab-input-wrap--with-toggle">
                <input
                  ref={futurePercentInputRef}
                  type="number"
                  step="any"
                  defaultValue=""
                  onChange={() => setSelectedPercentPreset(null)}
                  placeholder={t("simulation.future.percentPlaceholder")}
                />
                <CurrencyToggle className="simulation-lab-inline-currency-toggle" />
              </div>
              <div className="simulation-lab-preset-row" aria-label={t("simulation.future.percentChange")}>
                {FUTURE_PERCENT_PRESETS.map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    className={selectedPercentPreset === preset ? "simulation-lab-preset-chip active" : "simulation-lab-preset-chip"}
                    onClick={() => handleFuturePercentPreset(String(preset))}
                  >
                    {preset > 0 ? `+${preset}%` : `${preset}%`}
                  </button>
                ))}
              </div>
            </div>

            <button type="submit" className="simulation-lab-primary-button" disabled={futureLoading || instrumentQuoteLoading}>
              {futureLoading ? t("simulation.future.submitting") : t("simulation.future.submit")}
            </button>
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
                <span className="simulation-lab-result-eyebrow">{result.sourceLabel}</span>
                <strong>{result.selectedLabel}</strong>
                <p>{result.scenarioLabel}</p>
              </div>
              <div className={positive ? "simulation-lab-result-badge is-positive" : "simulation-lab-result-badge is-negative"}>
                {positive ? <ArrowUpRight size={14} /> : <ArrowDownRight size={14} />}
                <span>{formatSignedPercent(result.percentReturn)}</span>
              </div>
            </div>

            <div className="simulation-lab-result-summary">
              <div className="simulation-lab-result-value">
                <span>{result.primaryLabel}</span>
                <h3>{formatAmount(result.primaryValue)}</h3>
                <p>{result.secondaryLabel}: {formatAmount(result.secondaryValue)}</p>
              </div>

              <div className="simulation-lab-chart-card">
                <MiniScenarioChart data={result.chartData} positive={positive} />
              </div>
            </div>

            <div className="simulation-lab-metric-grid">
              <ResultMetric label={t("simulation.ui.metrics.totalReturn")} value={formatAmount(result.totalReturn)} tone={result.totalReturn >= 0 ? "positive" : "negative"} />
              <ResultMetric label={t("simulation.ui.metrics.percentChange")} value={formatSignedPercent(result.percentReturn)} tone={result.percentReturn >= 0 ? "positive" : "negative"} />
              <ResultMetric label={t("simulation.ui.metrics.profitLoss")} value={formatAmount(result.profitLoss)} tone={result.profitLoss >= 0 ? "positive" : "negative"} />
              <ResultMetric label={t("simulation.ui.metrics.selectedAsset")} value={result.selectedLabel} />
            </div>

            <div className="simulation-lab-result-meta">
              <div>
                <span>{t("simulation.ui.meta.scenarioSummary")}</span>
                <strong>{result.supportLine}</strong>
              </div>
              <div>
                <span>{t("simulation.ui.meta.detail")}</span>
                <strong>{result.metaLine}</strong>
              </div>
              <div>
                <span>{t("simulation.ui.meta.dateOrSource")}</span>
                <strong>{result.sourceDetail}</strong>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
});

const MiniScenarioChart = memo(function MiniScenarioChart({ data, positive }) {
  if (!Array.isArray(data) || data.length === 0) {
    return null;
  }

  const stroke = positive ? "#16835e" : "#c24a4a";
  const fill = positive ? "rgba(22, 131, 94, 0.14)" : "rgba(194, 74, 74, 0.14)";

  return (
    <ResponsiveContainer width="100%" height={124}>
      <AreaChart data={data} margin={{ top: 8, right: 0, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id={`simulation-chart-fill-${positive ? "up" : "down"}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={stroke} stopOpacity={0.28} />
            <stop offset="100%" stopColor={stroke} stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} stroke="rgba(148, 163, 184, 0.18)" />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: "#64748b", fontSize: 11 }} />
        <YAxis hide domain={["dataMin - 1", "dataMax + 1"]} />
        <Tooltip
          formatter={(value) => formatCurrency(Number(value))}
          labelStyle={{ color: "#203252" }}
          contentStyle={{
            borderRadius: 14,
            border: "1px solid rgba(203, 213, 225, 0.9)",
            boxShadow: "0 12px 28px rgba(15, 23, 42, 0.08)",
          }}
        />
        <Area
          type="monotone"
          dataKey="value"
          stroke={stroke}
          strokeWidth={2.5}
          fill={fill}
          fillOpacity={1}
          activeDot={{ r: 4 }}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
});

const SimulationHistoryPanel = memo(function SimulationHistoryPanel({ results, formatAmount }) {
  const { t } = useTranslation();
  return (
    <section className="panel-surface simulation-lab-history-card">
      <div className="simulation-lab-history-head">
        <div>
          <h2>{t("simulation.ui.recentTitle")}</h2>
          <p>{t("simulation.ui.recentDescription")}</p>
        </div>
      </div>

      {!results.length ? (
        <div className="simulation-lab-empty-history">
          <strong>{t("simulation.ui.recentEmptyTitle")}</strong>
          <p>{t("simulation.ui.recentEmptyDescription")}</p>
        </div>
      ) : (
        <div className="simulation-lab-history-scroller">
          {results.map((item) => (
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
          ))}
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
  const debouncedSearch = useDebouncedValue(search, 180);
  const { data: results = [] } = useInstrumentSearch(debouncedSearch, { staleTime: 60_000 });
  const showResults = isFocused && debouncedSearch.trim().length >= 2;

  const filteredOptions = useMemo(
    () => results.slice(0, 24).map((item) => ({
      key: `${item.code}-${item.type}`,
      value: item.code,
      label: `${item.code}${item.name ? ` - ${item.name}` : ""}`,
    })),
    [results],
  );

  return (
    <div className="simulation-lab-search">
      <div className="simulation-lab-input-wrap simulation-lab-input-wrap--search">
        <Search size={16} aria-hidden="true" />
        <input
          type="text"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onFocus={() => setIsFocused(true)}
          onBlur={() => window.setTimeout(() => setIsFocused(false), 120)}
          placeholder={searchPlaceholder}
        />
      </div>

      <div className="simulation-lab-search-selection">
        <span>{selectedLabel || value || placeholder}</span>
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

const ResultMetric = memo(function ResultMetric({ label, value, tone = "neutral" }) {
  return (
    <div className={`simulation-lab-metric-card simulation-lab-metric-card--${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
});

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
