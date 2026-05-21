import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { Pie, PieChart, Cell, ResponsiveContainer, Tooltip } from "recharts";
import {
  createPortfolioHolding,
  deletePortfolioHolding,
  getPortfolioById,
  getPortfolioDetails,
  getPortfolioHoldings,
  getPortfolioSummary,
  updatePortfolio,
  updatePortfolioHolding,
} from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import AiPortfolioAnalysisCard from "../components/ai/AiPortfolioAnalysisCard";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import SummaryCard from "../components/common/SummaryCard";
import useToast from "../hooks/useToast";
import { useTheme } from "../theme/ThemeContext";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";

const CHART_COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];
const INTERNAL_CASH_CODE = "TRY";
const INTERNAL_CASH_LABEL = "Nakit";
const FX_CODE_LABELS = {
  USD: "Dolar",
  EUR: "Euro",
  GBP: "Sterlin",
  AUD: "Avustralya Doları",
  CAD: "Kanada Doları",
  CHF: "Frank",
  JPY: "Yen",
  SAR: "Riyal",
  RUB: "Ruble",
  AZN: "Manat",
  CNY: "Yuan",
  QAR: "Riyal",
  KWD: "Dinar",
  KRW: "Won",
};

export default function PortfolioDetailPage() {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const { formatAmount } = useCurrency();
  const { portfolioId } = useParams();
  const [portfolioInfo, setPortfolioInfo] = useState(null);
  const [summary, setSummary] = useState(null);
  const [holdings, setHoldings] = useState([]);
  const [settingsForm, setSettingsForm] = useState({ portfolioName: "" });
  const [holdingForm, setHoldingForm] = useState({
    instrumentSearch: "",
    instrumentCode: "",
    quantity: "",
    buyPrice: "",
  });
  const [editingHoldingId, setEditingHoldingId] = useState(null);
  const [isInstrumentMenuOpen, setIsInstrumentMenuOpen] = useState(false);
  const [highlightedInstrumentIndex, setHighlightedInstrumentIndex] = useState(0);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { toast, showToast } = useToast();

  async function loadPortfolioData() {
    if (!portfolioId) return;
    try {
      setLoading(true);
      setError("");
      const details = await getPortfolioDetails(portfolioId);
      if (details) {
        setPortfolioInfo({
          portfolioId: details.portfolioId,
          portfolioName: details.portfolioName,
          createdAt: details.createdAt,
        });
        setSettingsForm({
          portfolioName: details.portfolioName || "",
        });
        setSummary(details.summary || null);
        setHoldings(details.holdings || []);
        return;
      }

      const [portfolio, summaryData, holdingsData] = await Promise.all([
        getPortfolioById(portfolioId),
        getPortfolioSummary(portfolioId),
        getPortfolioHoldings(portfolioId),
      ]);
      setPortfolioInfo(portfolio);
      setSettingsForm({
        portfolioName: portfolio?.portfolioName || "",
      });
      setSummary(summaryData);
      setHoldings(holdingsData);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolioDetail.loadError")));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadPortfolioData();
  }, [portfolioId]);

  const allocationData = useMemo(() => {
    const totalValue = holdings.reduce((sum, holding) => sum + Number(holding.currentValue || 0), 0);
    if (totalValue <= 0) return [];

    return holdings.map((holding) => ({
      instrumentCode: holding.instrumentCode,
      displayName: formatInstrumentLabel(holding.instrumentCode),
      assetCategoryKey: getAssetCategoryKey(holding.instrumentCode),
      assetCategoryLabel: getAssetCategoryLabel(getAssetCategoryKey(holding.instrumentCode)),
      value: Number(holding.currentValue || 0),
      percentage: (Number(holding.currentValue || 0) / totalValue) * 100,
    }));
  }, [holdings]);

  const filteredInstruments = useMemo(() => [], []);

  const selectedInstrument = useMemo(() => {
    const code = (holdingForm.instrumentCode || "").trim();
    if (!code) return null;
    return {
      symbol: code,
      name: t("portfolioDetail.manualSymbol"),
      instrumentType: "MANUAL",
      source: "-",
      currency: "-",
    };
  }, [holdingForm.instrumentCode, t]);

  const valuationReadyCount = holdings.filter((holding) => holding.valuationAvailable).length;

  async function handleSaveSettings(event) {
    event.preventDefault();

    try {
      const payload = {
        portfolioName: settingsForm.portfolioName,
      };
      const updated = await updatePortfolio(portfolioId, payload);
      setPortfolioInfo(updated);
      setSettingsForm({
        portfolioName: updated?.portfolioName || settingsForm.portfolioName,
      });
      setIsSettingsOpen(false);
      showToast("success", t("portfolioDetail.settingsSaved"));
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolioDetail.settingsUpdateError")));
    }
  }

  function resetHoldingForm() {
    setHoldingForm({ instrumentSearch: "", instrumentCode: "", quantity: "", buyPrice: "" });
    setEditingHoldingId(null);
    setIsInstrumentMenuOpen(false);
    setHighlightedInstrumentIndex(0);
  }

  function handleInstrumentSelect(instrument) {
    setHoldingForm((prev) => ({
      ...prev,
      instrumentCode: instrument.symbol || "",
      instrumentSearch: formatInstrumentLabel(instrument.symbol || instrument.name),
    }));
    setIsInstrumentMenuOpen(false);
    setHighlightedInstrumentIndex(0);
  }

  function handleEditHolding(holding) {
    setEditingHoldingId(holding.holdingId);
    setHoldingForm({
      instrumentSearch: formatInstrumentLabel(holding.instrumentCode),
      instrumentCode: holding.instrumentCode || "",
      quantity: holding.quantity ?? "",
      buyPrice: holding.buyPrice ?? "",
    });
    setIsInstrumentMenuOpen(false);
    setHighlightedInstrumentIndex(0);
  }

  async function handleSaveHolding(event) {
    event.preventDefault();
    const numericQuantity = Number(holdingForm.quantity);
    const numericBuyPrice = Number(holdingForm.buyPrice);

    if (
      !holdingForm.instrumentCode ||
      !Number.isFinite(numericQuantity) ||
      numericQuantity <= 0 ||
      !Number.isFinite(numericBuyPrice) ||
      numericBuyPrice <= 0
    ) {
      setError(t("portfolioDetail.validationError"));
      return;
    }

    try {
      setError("");
      if (editingHoldingId) {
        await updatePortfolioHolding(portfolioId, editingHoldingId, {
          quantity: numericQuantity,
          buyPrice: numericBuyPrice,
        });
        showToast("success", t("portfolioDetail.holdingUpdated"));
      } else {
        await createPortfolioHolding(portfolioId, {
          instrumentCode: holdingForm.instrumentCode,
          quantity: numericQuantity,
          buyPrice: numericBuyPrice,
        });
        showToast("success", t("portfolioDetail.holdingAdded"));
      }

      resetHoldingForm();
      await loadPortfolioData();
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolioDetail.holdingSaveError")));
    }
  }

  async function handleDeleteHolding(holdingId) {
    try {
      setError("");
      await deletePortfolioHolding(portfolioId, holdingId);
      if (editingHoldingId === holdingId) {
        resetHoldingForm();
      }
      showToast("success", t("portfolioDetail.holdingRemoved"));
      await loadPortfolioData();
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolioDetail.holdingDeleteError")));
    }
  }

  function getSummaryTone() {
    if (summary?.summaryStatus === "UNAVAILABLE") return "warm";
    if (summary?.summaryStatus === "PARTIAL") return "warm";
    return "cool";
  }

  function getPriceStatusClass(priceStatus) {
    switch (priceStatus) {
      case "LIVE":
        return "is-live";
      case "CACHED":
        return "is-cached";
      case "STALE":
        return "is-stale";
      default:
        return "is-unavailable";
    }
  }

  function handleInstrumentSearchKeyDown(event) {
    if (!filteredInstruments.length) {
      return;
    }

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setIsInstrumentMenuOpen(true);
      setHighlightedInstrumentIndex((prev) => (prev + 1) % filteredInstruments.length);
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      setIsInstrumentMenuOpen(true);
      setHighlightedInstrumentIndex((prev) => (prev - 1 + filteredInstruments.length) % filteredInstruments.length);
      return;
    }

    if (event.key === "Enter" && isInstrumentMenuOpen) {
      event.preventDefault();
      handleInstrumentSelect(filteredInstruments[highlightedInstrumentIndex] || filteredInstruments[0]);
      return;
    }

    if (event.key === "Escape") {
      setIsInstrumentMenuOpen(false);
    }
  }

  return (
    <div className="portfolio-page-stack">
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {loading ? <LoadingSpinner label={t("portfolioDetail.loading")} /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="portfolio-hero-grid">
            <div className="card portfolio-hero-card">
              <div className="portfolio-hero-header">
                <div className="portfolio-hero-copy">
                  <p className="eyebrow">{t("portfolioDetail.snapshotEyebrow")}</p>
                  <h2>{portfolioInfo?.portfolioName || "-"}</h2>
                  <p className="page-description">
                    {summary?.summaryStatus === "UNAVAILABLE"
                      ? t("portfolioDetail.snapshotUnavailableDescription")
                      : t("portfolioDetail.snapshotDescription")}
                  </p>
                </div>
                <div className="portfolio-hero-actions">
                  <CurrencyToggle />
                  <Link className="secondary-button portfolio-back-link" to="/portfolio">
                    {t("portfolioDetail.back")}
                  </Link>
                  <button
                    type="button"
                    className="secondary-button portfolio-settings-toggle"
                    onClick={() => setIsSettingsOpen((prev) => !prev)}
                  >
                    {isSettingsOpen ? t("portfolioDetail.closeSettings") : t("portfolioDetail.settings")}
                  </button>
                </div>
              </div>
              {isSettingsOpen ? (
                <form className="portfolio-inline-settings" onSubmit={handleSaveSettings}>
                  <div className="portfolio-inline-settings-header">
                    <strong>{t("portfolioDetail.settingsTitle")}</strong>
                    <span className="summary-chip">{formatDateTime(portfolioInfo?.createdAt)}</span>
                  </div>
                  <div className="portfolio-inline-settings-grid">
                    <label className="portfolio-field">
                      <span>{t("portfolioDetail.name")}</span>
                      <input
                        required
                        value={settingsForm.portfolioName}
                        onChange={(event) => setSettingsForm((prev) => ({ ...prev, portfolioName: event.target.value }))}
                        placeholder={t("portfolioDetail.namePlaceholder")}
                      />
                    </label>
                  </div>
                  <div className="actions-row">
                    <button type="submit">{t("portfolioDetail.saveSettings")}</button>
                  </div>
                </form>
              ) : null}
              <div className="portfolio-hero-stats">
                <div className="portfolio-kpi">
                  <span>{t("portfolioDetail.totalHoldings")}</span>
                  <strong>{formatNumber(holdings.length, 0)}</strong>
                </div>
                <div className="portfolio-kpi">
                  <span>{t("portfolioDetail.valuationReady")}</span>
                  <strong>{formatNumber(valuationReadyCount, 0)}</strong>
                </div>
                <div className="portfolio-kpi">
                  <span>{t("portfolioDetail.missingPrices")}</span>
                  <strong>{formatNumber(summary?.missingPriceCount, 0)}</strong>
                </div>
              </div>
            </div>
          </section>

          <section className="portfolio-summary-stack">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("portfolioDetail.valuationEyebrow")}</p>
                <h3>{t("portfolioDetail.summaryTitle")}</h3>
              </div>
              <span className={`portfolio-status-pill ${getPriceStatusClass(summary?.summaryStatus === "COMPLETE" ? "LIVE" : summary?.summaryStatus === "PARTIAL" ? "CACHED" : "UNAVAILABLE")}`}>
                {formatSummaryStatus(summary?.summaryStatus, t)}
              </span>
            </div>
            <div className="cards-grid compact">
              <SummaryCard title={t("portfolioDetail.cards.totalCost")} value={formatAmount(summary?.totalCost)} subtitle={t("portfolioDetail.cards.totalCostSubtitle")} tone="cool" />
              <SummaryCard title={t("portfolioDetail.cards.currentValue")} value={formatAmount(summary?.currentValue ?? summary?.totalCurrentValue)} subtitle={t("portfolioDetail.cards.currentValueSubtitle")} tone={getSummaryTone()} />
              <SummaryCard title={t("portfolioDetail.cards.profitLoss")} value={formatAmount(summary?.profitLoss ?? summary?.totalProfitLoss)} subtitle={t("portfolioDetail.cards.profitLossSubtitle")} tone={getSummaryTone()} />
              <SummaryCard title={t("portfolioDetail.cards.profitLossPercent")} value={formatPercent(summary?.profitLossPercent)} subtitle={t("portfolioDetail.cards.profitLossPercentSubtitle")} tone="cool" />
              <SummaryCard title={t("portfolioDetail.cards.pricedHoldings")} value={`${formatNumber(valuationReadyCount, 0)} / ${formatNumber(holdings.length, 0)}`} subtitle={t("portfolioDetail.cards.pricedHoldingsSubtitle")} tone="cool" />
              <SummaryCard title={t("portfolioDetail.cards.missingPriceCount")} value={formatNumber(summary?.missingPriceCount, 0)} subtitle={t("portfolioDetail.cards.missingPriceCountSubtitle")} tone="warm" />
            </div>
            <AiPortfolioAnalysisCard
              portfolioId={portfolioInfo?.portfolioId || portfolioId}
              portfolioName={portfolioInfo?.portfolioName}
              holdingsCount={holdings.length}
            />
          </section>

          <section className="portfolio-workbench-grid">
            <form className="card portfolio-holding-form" onSubmit={handleSaveHolding}>
              <div className="panel-head">
                <div>
                  <p className="eyebrow">{t("portfolioDetail.positionEntryEyebrow")}</p>
                  <h3>{editingHoldingId ? t("portfolioDetail.updateHolding") : t("portfolioDetail.addHolding")}</h3>
                </div>
                {editingHoldingId ? (
                  <button type="button" className="secondary-button" onClick={resetHoldingForm}>
                    {t("portfolioDetail.cancelEdit")}
                  </button>
                ) : null}
              </div>

              <div className="portfolio-workbench-columns compact-search-layout">
                <div className="portfolio-search-column">
                  <label className="portfolio-field">
                    <span>{t("portfolioDetail.instrumentSearch")}</span>
                    <div className="instrument-search-shell">
                      <input
                        type="text"
                        required
                        value={holdingForm.instrumentSearch}
                        onFocus={() => setIsInstrumentMenuOpen(true)}
                        onBlur={() => setTimeout(() => setIsInstrumentMenuOpen(false), 120)}
                        onKeyDown={handleInstrumentSearchKeyDown}
                        onChange={(event) => {
                          const raw = event.target.value;
                          const normalized = raw.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
                          setIsInstrumentMenuOpen(true);
                          setHighlightedInstrumentIndex(0);
                          setHoldingForm((prev) => ({
                            ...prev,
                            instrumentSearch: raw,
                            instrumentCode: normalized,
                          }));
                        }}
                        placeholder={t("portfolioDetail.instrumentSearchPlaceholder")}
                      />
                      {isInstrumentMenuOpen ? (
                        <div className="instrument-picker compact">
                          {filteredInstruments.length === 0 ? (
                            <div className="instrument-picker-empty">{t("portfolioDetail.instrumentPickerEmpty")}</div>
                          ) : (
                            filteredInstruments.map((instrument, index) => {
                              const isActive = instrument.symbol === holdingForm.instrumentCode;
                              const isHighlighted = index === highlightedInstrumentIndex;
                              return (
                                <button
                                  key={`${instrument.symbol || "instrument"}-${index}`}
                                  type="button"
                                  className={`instrument-option compact${isActive ? " active" : ""}${isHighlighted ? " highlighted" : ""}`}
                                  onMouseDown={(event) => event.preventDefault()}
                                  onMouseEnter={() => setHighlightedInstrumentIndex(index)}
                                  onClick={() => handleInstrumentSelect(instrument)}
                                >
                                  <div className="instrument-option-top">
                                    <strong>{instrument.symbol || t("portfolioDetail.notAvailable")}</strong>
                                    <span className="portfolio-status-pill is-cached">{formatInstrumentType(instrument.instrumentType, t)}</span>
                                  </div>
                                  <span>{instrument.name || t("portfolioDetail.unnamedInstrument")}</span>
                                </button>
                              );
                            })
                          )}
                        </div>
                      ) : null}
                    </div>
                  </label>
                </div>

                <div className="portfolio-entry-column">
                  <div className="selected-instrument-card compact">
                    <p className="eyebrow">{t("portfolioDetail.selectedInstrumentEyebrow")}</p>
                    <strong>{selectedInstrument ? formatInstrumentLabel(selectedInstrument.symbol) : t("portfolioDetail.noInstrumentSelected")}</strong>
                    <p>{selectedInstrument ? selectedInstrument.name : t("portfolioDetail.selectedInstrumentDescription")}</p>
                    <div className="selected-instrument-meta">
                      {selectedInstrument ? (
                        <span className={`portfolio-type-badge is-${getAssetCategoryKey(selectedInstrument.symbol).toLowerCase()}`}>
                          {getAssetCategoryLabel(getAssetCategoryKey(selectedInstrument.symbol))}
                        </span>
                      ) : null}
                      <span>{formatInstrumentType(selectedInstrument?.instrumentType, t)}</span>
                      <span>{selectedInstrument?.source || "-"}</span>
                      <span>{selectedInstrument?.currency || "TRY"}</span>
                    </div>
                  </div>

                  <div className="portfolio-entry-grid">
                    <label className="portfolio-field">
                      <span>{t("portfolio.quantity")}</span>
                      <input
                        type="number"
                        step="any"
                        required
                        value={holdingForm.quantity}
                        onChange={(event) => setHoldingForm((prev) => ({ ...prev, quantity: event.target.value }))}
                        placeholder="0.00"
                      />
                    </label>
                    <label className="portfolio-field">
                      <span>{t("portfolio.buyPrice")}</span>
                      <input
                        type="number"
                        step="any"
                        required
                        value={holdingForm.buyPrice}
                        onChange={(event) => setHoldingForm((prev) => ({ ...prev, buyPrice: event.target.value }))}
                        placeholder="0.00"
                      />
                    </label>
                  </div>

                  <div className="actions-row">
                    <button
                      type="submit"
                      disabled={!holdingForm.instrumentCode || Number(holdingForm.quantity) <= 0 || Number(holdingForm.buyPrice) <= 0}
                    >
                      {editingHoldingId ? t("portfolioDetail.updateHolding") : t("portfolioDetail.addHolding")}
                    </button>
                  </div>
                </div>
              </div>
            </form>

            <div className="card portfolio-chart-card">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">{t("portfolioDetail.allocationEyebrow")}</p>
                  <h3>{t("portfolioDetail.allocationTitle")}</h3>
                </div>
              </div>
              {allocationData.length === 0 ? (
                <EmptyState title={t("portfolioDetail.chartEmptyTitle")} description={t("portfolioDetail.chartEmptyDescription")} />
              ) : (
                <>
                  <div className="portfolio-chart-stage">
                    <ResponsiveContainer>
                      <PieChart>
                        <Pie data={allocationData} dataKey="value" nameKey="instrumentCode" outerRadius={108} innerRadius={54}>
                          {allocationData.map((entry, index) => (
                            <Cell key={entry.instrumentCode} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip
                          formatter={(value) => formatAmount(value)}
                          contentStyle={chartTheme.tooltipContentStyle}
                          itemStyle={chartTheme.tooltipItemStyle}
                          labelStyle={chartTheme.tooltipLabelStyle}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                  <div className="portfolio-allocation-list">
                    {allocationData.map((entry, index) => (
                      <div key={entry.instrumentCode} className="portfolio-allocation-item">
                        <div className="portfolio-allocation-label">
                          <span className="portfolio-color-dot" style={{ backgroundColor: CHART_COLORS[index % CHART_COLORS.length] }} />
                          <div className="portfolio-allocation-label-copy">
                            <strong>{entry.displayName}</strong>
                            <span className={`portfolio-type-badge is-${entry.assetCategoryKey.toLowerCase()}`}>{entry.assetCategoryLabel}</span>
                          </div>
                        </div>
                        <span className="muted">
                          {formatAmount(entry.value)} ({formatNumber(entry.percentage, 2)}%)
                        </span>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          </section>

          <section className="card table-wrap portfolio-table-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("portfolioDetail.recordedPositionsEyebrow")}</p>
                <h3>{t("portfolio.holdingsTitle")}</h3>
              </div>
              <span className="summary-chip">{t("common.records", { count: holdings.length })}</span>
            </div>
            {holdings.length === 0 ? (
              <EmptyState title={t("portfolioDetail.noHoldingsTitle")} description={t("portfolioDetail.noHoldingsDescription")} />
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>{t("analysis.symbolPicker.primaryInstrument")}</th>
                    <th>{t("portfolio.table.quantity")}</th>
                    <th>{t("portfolio.buyPrice")}</th>
                    <th>{t("portfolio.table.currentPrice")}</th>
                    <th>{t("portfolio.table.currentValue")}</th>
                    <th>{t("portfolio.table.profitLoss")}</th>
                    <th>{t("portfolio.table.status")}</th>
                    <th>{t("portfolioDetail.lastUpdate")}</th>
                    <th>{t("portfolio.table.action")}</th>
                  </tr>
                </thead>
                <tbody>
                  {holdings.map((holding, index) => (
                    <tr key={`${holding.instrumentCode}-${index}`}>
                      <td>
                        <div className="portfolio-cell-stack">
                          <strong>{formatInstrumentLabel(holding.instrumentCode)}</strong>
                          <div className="portfolio-inline-meta">
                            <span className={`portfolio-type-badge is-${getAssetCategoryKey(holding.instrumentCode).toLowerCase()}`}>
                              {getAssetCategoryLabel(getAssetCategoryKey(holding.instrumentCode))}
                            </span>
                            <span className="muted">{t("portfolioDetail.holdingNumber", { index: index + 1 })}</span>
                          </div>
                        </div>
                      </td>
                      <td>{formatNumber(holding.quantity)}</td>
                      <td>{formatAmount(holding.buyPrice)}</td>
                      <td>{formatAmount(holding.currentPrice)}</td>
                      <td>{formatAmount(holding.currentValue)}</td>
                      <td>
                        <div className="portfolio-cell-stack">
                          <strong>{formatAmount(holding.profitLoss)}</strong>
                          <span className="muted">{formatPercent(holding.profitLossPercent)}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`portfolio-status-pill ${getPriceStatusClass(holding.priceStatus)}`}>
                          {formatHoldingStatus(holding.priceStatus, t)}
                        </span>
                      </td>
                      <td>{formatDateTime(holding.lastPriceUpdateTime)}</td>
                      <td>
                        <div className="actions-row">
                          <button type="button" className="secondary-button" onClick={() => handleEditHolding(holding)}>
                            {t("portfolio.update")}
                          </button>
                          <button type="button" className="danger-button" onClick={() => handleDeleteHolding(holding.holdingId)}>
                            {t("portfolio.delete")}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}

function formatSummaryStatus(value, t) {
  return {
    COMPLETE: t("portfolioDetail.summaryStatus.COMPLETE"),
    PARTIAL: t("portfolioDetail.summaryStatus.PARTIAL"),
    UNAVAILABLE: t("portfolioDetail.summaryStatus.UNAVAILABLE"),
  }[value] ?? t("portfolioDetail.summaryStatus.UNKNOWN");
}

function formatHoldingStatus(value, t) {
  return {
    LIVE: t("portfolio.status.LIVE"),
    CACHED: t("portfolio.status.CACHED"),
    STALE: t("portfolio.status.STALE"),
    UNAVAILABLE: t("portfolio.status.UNAVAILABLE"),
  }[value] ?? t("portfolio.status.UNAVAILABLE");
}

function formatInstrumentType(value, t) {
  return {
    MANUAL: t("portfolioDetail.instrumentType.MANUAL"),
  }[value] ?? (value || "-");
}

function getAssetCategoryKey(instrumentCode) {
  const code = normalizeInstrumentCode(instrumentCode);
  if (!code) {
    return "OTHER";
  }
  if (code === INTERNAL_CASH_CODE) {
    return "CASH";
  }
  if (code.startsWith("TCMB:")) {
    return "FX";
  }
  if (code.includes(":")) {
    return "CRYPTO";
  }
  if (/^[A-Z]{3}$/.test(code)) {
    return "FUND";
  }
  if (/^[A-Z0-9.]{1,10}$/.test(code)) {
    return "STOCK";
  }
  return "OTHER";
}

function getAssetCategoryLabel(key) {
  return {
    CASH: "Nakit",
    STOCK: "Hisse",
    FUND: "Fon",
    CRYPTO: "Kripto",
    FX: "Döviz",
    OTHER: "Diğer",
  }[key] ?? "Diğer";
}

function normalizeInstrumentCode(value) {
  if (value == null) {
    return "";
  }

  const rawValue = String(value).trim();
  if (rawValue.toUpperCase().startsWith("TCMB:")) {
    return rawValue.toUpperCase();
  }

  return rawValue.replace(/[^A-Za-z0-9:]/g, "").toUpperCase();
}

function formatInstrumentLabel(value) {
  const normalized = normalizeInstrumentCode(value);
  if (!normalized) {
    return "-";
  }
  if (normalized === INTERNAL_CASH_CODE) {
    return INTERNAL_CASH_LABEL;
  }

  const fxMatch = normalized.match(/^([A-Z_]+):([A-Z0-9]+):(BUY|SELL)$/);
  if (!fxMatch) {
    return value || normalized;
  }

  const [, , currencyCode] = fxMatch;
  const currencyLabel = FX_CODE_LABELS[currencyCode] || currencyCode;
  return `${currencyCode} / ${currencyLabel}`;
}
