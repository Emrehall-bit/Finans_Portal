import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Pie, PieChart, Cell, ResponsiveContainer, Tooltip } from "recharts";
import { getMarketData } from "../api/marketApi";
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
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import SummaryCard from "../components/common/SummaryCard";
import useToast from "../hooks/useToast";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";

const CHART_COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];

export default function PortfolioDetailPage() {
  const { portfolioId } = useParams();
  const [portfolioInfo, setPortfolioInfo] = useState(null);
  const [summary, setSummary] = useState(null);
  const [holdings, setHoldings] = useState([]);
  const [marketInstruments, setMarketInstruments] = useState([]);
  const [settingsForm, setSettingsForm] = useState({ portfolioName: "", visibilityStatus: "PRIVATE" });
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
  const { userId } = useAuth();

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
          visibilityStatus: details.visibilityStatus,
          createdAt: details.createdAt,
        });
        setSettingsForm({
          portfolioName: details.portfolioName || "",
          visibilityStatus: details.visibilityStatus || "PRIVATE",
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
        visibilityStatus: portfolio?.visibilityStatus || "PRIVATE",
      });
      setSummary(summaryData);
      setHoldings(holdingsData);
    } catch (err) {
      setError(extractErrorMessage(err, "Portfolio detail could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  async function loadMarketInstruments() {
    try {
      const data = await getMarketData();
      setMarketInstruments(data || []);
    } catch (err) {
      setError(extractErrorMessage(err, "Market instruments could not be loaded."));
    }
  }

  useEffect(() => {
    loadPortfolioData();
    loadMarketInstruments();
  }, [portfolioId]);

  const allocationData = useMemo(() => {
    const totalValue = holdings.reduce((sum, holding) => sum + Number(holding.currentValue || 0), 0);
    if (totalValue <= 0) return [];

    return holdings.map((holding) => ({
      instrumentCode: holding.instrumentCode,
      value: Number(holding.currentValue || 0),
      percentage: (Number(holding.currentValue || 0) / totalValue) * 100,
    }));
  }, [holdings]);

  const filteredInstruments = useMemo(() => {
    const query = holdingForm.instrumentSearch.trim().toLowerCase();
    if (!query) {
      return marketInstruments.slice(0, 8);
    }

    return marketInstruments
      .filter((instrument) =>
        [instrument.symbol, instrument.name, instrument.instrumentType, instrument.source]
          .filter(Boolean)
          .some((value) => value.toLowerCase().includes(query)),
      )
      .slice(0, 8);
  }, [holdingForm.instrumentSearch, marketInstruments]);

  const selectedInstrument = useMemo(
    () => marketInstruments.find((instrument) => instrument.symbol === holdingForm.instrumentCode) ?? null,
    [holdingForm.instrumentCode, marketInstruments],
  );

  const valuationReadyCount = holdings.filter((holding) => holding.valuationAvailable).length;

  async function handleSaveSettings(e) {
    e.preventDefault();
    const visibilityChanged = portfolioInfo?.visibilityStatus && portfolioInfo.visibilityStatus !== settingsForm.visibilityStatus;

    if (visibilityChanged) {
      const confirmed = window.confirm(
        settingsForm.visibilityStatus === "PUBLIC"
          ? "Bu portfoyu PUBLIC yaparsan ileride diger kullanicilar tarafindan gorunebilir hale gelecek. Devam etmek istiyor musun?"
          : "Bu portfoyu PRIVATE yaparsan sadece sen gorebileceksin. Devam etmek istiyor musun?",
      );

      if (!confirmed) {
        return;
      }
    }

    try {
      const payload = {
        portfolioName: settingsForm.portfolioName,
        visibilityStatus: settingsForm.visibilityStatus,
      };
      const updated = await updatePortfolio(portfolioId, payload);
      setPortfolioInfo(updated);
      setSettingsForm({
        portfolioName: updated?.portfolioName || settingsForm.portfolioName,
        visibilityStatus: updated?.visibilityStatus || settingsForm.visibilityStatus,
      });
      setIsSettingsOpen(false);
      showToast("success", "Portfolio settings saved.");
    } catch (err) {
      setError(extractErrorMessage(err, "Portfolio settings update failed."));
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
      instrumentSearch: [instrument.symbol, instrument.name].filter(Boolean).join(" - "),
    }));
    setIsInstrumentMenuOpen(false);
    setHighlightedInstrumentIndex(0);
  }

  function handleEditHolding(holding) {
    setEditingHoldingId(holding.holdingId);
    setHoldingForm({
      instrumentSearch: holding.instrumentCode || "",
      instrumentCode: holding.instrumentCode || "",
      quantity: holding.quantity ?? "",
      buyPrice: holding.buyPrice ?? "",
    });
    setIsInstrumentMenuOpen(false);
    setHighlightedInstrumentIndex(0);
  }

  async function handleSaveHolding(e) {
    e.preventDefault();
    const numericQuantity = Number(holdingForm.quantity);
    const numericBuyPrice = Number(holdingForm.buyPrice);

    if (
      !holdingForm.instrumentCode ||
      !Number.isFinite(numericQuantity) ||
      numericQuantity <= 0 ||
      !Number.isFinite(numericBuyPrice) ||
      numericBuyPrice <= 0
    ) {
      setError("Please choose an instrument and enter valid quantity and buy price values.");
      return;
    }

    try {
      setError("");
      if (editingHoldingId) {
        await updatePortfolioHolding(portfolioId, editingHoldingId, {
          quantity: numericQuantity,
          buyPrice: numericBuyPrice,
        });
        showToast("success", "Holding updated.");
      } else {
        await createPortfolioHolding(portfolioId, {
          instrumentCode: holdingForm.instrumentCode,
          quantity: numericQuantity,
          buyPrice: numericBuyPrice,
        });
        showToast("success", "Holding added to portfolio.");
      }

      resetHoldingForm();
      await loadPortfolioData();
    } catch (err) {
      setError(extractErrorMessage(err, "Holding could not be saved."));
    }
  }

  async function handleDeleteHolding(holdingId) {
    try {
      setError("");
      await deletePortfolioHolding(portfolioId, holdingId);
      if (editingHoldingId === holdingId) {
        resetHoldingForm();
      }
      showToast("success", "Holding removed.");
      await loadPortfolioData();
    } catch (err) {
      setError(extractErrorMessage(err, "Holding could not be deleted."));
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
      {loading ? <LoadingSpinner label="Loading portfolio details..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <section className="portfolio-hero-grid">
            <div className="card portfolio-hero-card">
              <div className="portfolio-hero-header">
                <div className="portfolio-hero-copy">
                  <p className="eyebrow">Portfolio Snapshot</p>
                  <h2>{portfolioInfo?.portfolioName || "-"}</h2>
                  <p className="page-description">
                    {summary?.summaryStatus === "UNAVAILABLE"
                      ? "Valuation data is currently unavailable, but your holdings are still editable and safe."
                      : "Use this workspace to maintain your current positions with quantity and purchase price."}
                  </p>
                </div>
                <div className="portfolio-hero-actions">
                  <Link className="secondary-button portfolio-back-link" to="/portfolio">
                    Back to Portfolio List
                  </Link>
                  <button
                    type="button"
                    className="secondary-button portfolio-settings-toggle"
                    onClick={() => setIsSettingsOpen((prev) => !prev)}
                  >
                    {isSettingsOpen ? "Close Settings" : "Settings"}
                  </button>
                </div>
              </div>
              {isSettingsOpen ? (
                <form className="portfolio-inline-settings" onSubmit={handleSaveSettings}>
                  <div className="portfolio-inline-settings-header">
                    <strong>Portfolio Settings</strong>
                    <span className="summary-chip">{formatDateTime(portfolioInfo?.createdAt)}</span>
                  </div>
                  <div className="portfolio-inline-settings-grid">
                    <label className="portfolio-field">
                      <span>Name</span>
                      <input
                        required
                        value={settingsForm.portfolioName}
                        onChange={(e) => setSettingsForm((prev) => ({ ...prev, portfolioName: e.target.value }))}
                        placeholder="Portfolio name"
                      />
                    </label>
                    <label className="portfolio-field">
                      <span>Visibility</span>
                      <select
                        required
                        value={settingsForm.visibilityStatus}
                        onChange={(e) => setSettingsForm((prev) => ({ ...prev, visibilityStatus: e.target.value }))}
                      >
                        <option value="PRIVATE">PRIVATE</option>
                        <option value="PUBLIC">PUBLIC</option>
                      </select>
                    </label>
                  </div>
                  <div className="portfolio-inline-settings-note">
                    Visibility degisikligi ileride portfoyun gorunurlugunu etkiler. Kaydetmeden once onay istenir.
                  </div>
                  <div className="actions-row">
                    <button type="submit">Save Settings</button>
                  </div>
                </form>
              ) : null}
              <div className="portfolio-hero-stats">
                <div className="portfolio-kpi">
                  <span>Total Holdings</span>
                  <strong>{formatNumber(holdings.length, 0)}</strong>
                </div>
                <div className="portfolio-kpi">
                  <span>Valuation Ready</span>
                  <strong>{formatNumber(valuationReadyCount, 0)}</strong>
                </div>
                <div className="portfolio-kpi">
                  <span>Missing Prices</span>
                  <strong>{formatNumber(summary?.missingPriceCount, 0)}</strong>
                </div>
                <div className="portfolio-kpi">
                  <span>Visibility</span>
                  <strong>{portfolioInfo?.visibilityStatus || "-"}</strong>
                </div>
              </div>
            </div>
          </section>

          <section className="portfolio-summary-stack">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Valuation Overview</p>
                <h3>Summary</h3>
              </div>
              <span className={`portfolio-status-pill ${getPriceStatusClass(summary?.summaryStatus === "COMPLETE" ? "LIVE" : summary?.summaryStatus === "PARTIAL" ? "CACHED" : "UNAVAILABLE")}`}>
                {summary?.summaryStatus || "UNKNOWN"}
              </span>
            </div>
            <div className="cards-grid compact">
              <SummaryCard title="Total Cost" value={formatCurrency(summary?.totalCost)} subtitle="Recorded purchase basis" tone="cool" />
              <SummaryCard title="Current Value" value={formatCurrency(summary?.currentValue ?? summary?.totalCurrentValue)} subtitle="Best-effort market valuation" tone={getSummaryTone()} />
              <SummaryCard title="Profit / Loss" value={formatCurrency(summary?.profitLoss ?? summary?.totalProfitLoss)} subtitle="Based on available prices" tone={getSummaryTone()} />
              <SummaryCard title="Profit / Loss %" value={formatPercent(summary?.profitLossPercent)} subtitle="Weighted by valued holdings" tone="cool" />
              <SummaryCard title="Priced Holdings" value={`${formatNumber(valuationReadyCount, 0)} / ${formatNumber(holdings.length, 0)}`} subtitle="Holdings with valuation data" tone="cool" />
              <SummaryCard title="Missing Price Count" value={formatNumber(summary?.missingPriceCount, 0)} subtitle="Holdings without current valuation" tone="warm" />
            </div>
          </section>

          <section className="portfolio-workbench-grid">
            <form className="card portfolio-holding-form" onSubmit={handleSaveHolding}>
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Position Entry</p>
                  <h3>{editingHoldingId ? "Update Holding" : "Add Holding"}</h3>
                </div>
                {editingHoldingId ? (
                  <button type="button" className="secondary-button" onClick={resetHoldingForm}>
                    Cancel Edit
                  </button>
                ) : null}
              </div>

              <div className="portfolio-workbench-columns compact-search-layout">
                <div className="portfolio-search-column">
                  <label className="portfolio-field">
                    <span>Instrument Search</span>
                    <div className="instrument-search-shell">
                      <input
                        type="text"
                        required
                        value={holdingForm.instrumentSearch}
                        onFocus={() => setIsInstrumentMenuOpen(true)}
                        onBlur={() => setTimeout(() => setIsInstrumentMenuOpen(false), 120)}
                        onKeyDown={handleInstrumentSearchKeyDown}
                        onChange={(e) => {
                          setIsInstrumentMenuOpen(true);
                          setHighlightedInstrumentIndex(0);
                          setHoldingForm((prev) => ({
                            ...prev,
                            instrumentSearch: e.target.value,
                            instrumentCode: e.target.value === prev.instrumentCode ? prev.instrumentCode : "",
                          }));
                        }}
                        placeholder="Search by symbol, name, type or source"
                      />
                      {isInstrumentMenuOpen ? (
                        <div className="instrument-picker compact">
                          {filteredInstruments.length === 0 ? (
                            <div className="instrument-picker-empty">No instrument matched your search.</div>
                          ) : (
                            filteredInstruments.map((instrument, idx) => {
                              const isActive = instrument.symbol === holdingForm.instrumentCode;
                              const isHighlighted = idx === highlightedInstrumentIndex;
                              return (
                                <button
                                  key={`${instrument.symbol || "instrument"}-${idx}`}
                                  type="button"
                                  className={`instrument-option compact${isActive ? " active" : ""}${isHighlighted ? " highlighted" : ""}`}
                                  onMouseDown={(event) => event.preventDefault()}
                                  onMouseEnter={() => setHighlightedInstrumentIndex(idx)}
                                  onClick={() => handleInstrumentSelect(instrument)}
                                >
                                  <div className="instrument-option-top">
                                    <strong>{instrument.symbol || "N/A"}</strong>
                                    <span className="portfolio-status-pill is-cached">{instrument.instrumentType || "UNKNOWN"}</span>
                                  </div>
                                  <span>{instrument.name || "Unnamed instrument"}</span>
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
                    <p className="eyebrow">Selected Instrument</p>
                    <strong>{selectedInstrument ? selectedInstrument.symbol : "No instrument selected"}</strong>
                    <p>{selectedInstrument ? selectedInstrument.name || "Unnamed instrument" : "Choose a result from the search list to continue."}</p>
                    <div className="selected-instrument-meta">
                      <span>{selectedInstrument?.instrumentType || "-"}</span>
                      <span>{selectedInstrument?.source || "-"}</span>
                      <span>{selectedInstrument?.currency || "TRY"}</span>
                    </div>
                  </div>

                  <div className="portfolio-entry-grid">
                    <label className="portfolio-field">
                      <span>Quantity</span>
                      <input
                        type="number"
                        step="any"
                        required
                        value={holdingForm.quantity}
                        onChange={(e) => setHoldingForm((prev) => ({ ...prev, quantity: e.target.value }))}
                        placeholder="0.00"
                      />
                    </label>
                    <label className="portfolio-field">
                      <span>Buy Price</span>
                      <input
                        type="number"
                        step="any"
                        required
                        value={holdingForm.buyPrice}
                        onChange={(e) => setHoldingForm((prev) => ({ ...prev, buyPrice: e.target.value }))}
                        placeholder="0.00"
                      />
                    </label>
                  </div>

                  <div className="actions-row">
                    <button
                      type="submit"
                      disabled={!holdingForm.instrumentCode || Number(holdingForm.quantity) <= 0 || Number(holdingForm.buyPrice) <= 0}
                    >
                      {editingHoldingId ? "Update Holding" : "Add Holding"}
                    </button>
                  </div>
                </div>
              </div>
            </form>

            <div className="card portfolio-chart-card">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Allocation</p>
                  <h3>Holdings Chart</h3>
                </div>
              </div>
              {allocationData.length === 0 ? (
                <EmptyState title="No chart data" description="Add holdings with quantity and buy price to build allocation." />
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
                        <Tooltip formatter={(value) => formatCurrency(value)} />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                  <div className="portfolio-allocation-list">
                    {allocationData.map((entry, index) => (
                      <div key={entry.instrumentCode} className="portfolio-allocation-item">
                        <div className="portfolio-allocation-label">
                          <span className="portfolio-color-dot" style={{ backgroundColor: CHART_COLORS[index % CHART_COLORS.length] }} />
                          <strong>{entry.instrumentCode}</strong>
                        </div>
                        <span className="muted">
                          {formatCurrency(entry.value)} ({formatNumber(entry.percentage, 2)}%)
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
                <p className="eyebrow">Recorded Positions</p>
                <h3>Holdings</h3>
              </div>
              <span className="summary-chip">{formatNumber(holdings.length, 0)} items</span>
            </div>
            {holdings.length === 0 ? (
              <EmptyState title="No holdings" description="No holdings yet for this portfolio." />
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Instrument</th>
                    <th>Quantity</th>
                    <th>Buy Price</th>
                    <th>Current Price</th>
                    <th>Current Value</th>
                    <th>Profit / Loss</th>
                    <th>Status</th>
                    <th>Last Update</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {holdings.map((holding, idx) => (
                    <tr key={`${holding.instrumentCode}-${idx}`}>
                      <td>
                        <div className="portfolio-cell-stack">
                          <strong>{holding.instrumentCode}</strong>
                          <span className="muted">Holding #{idx + 1}</span>
                        </div>
                      </td>
                      <td>{formatNumber(holding.quantity)}</td>
                      <td>{formatCurrency(holding.buyPrice)}</td>
                      <td>{formatCurrency(holding.currentPrice)}</td>
                      <td>{formatCurrency(holding.currentValue)}</td>
                      <td>
                        <div className="portfolio-cell-stack">
                          <strong>{formatCurrency(holding.profitLoss)}</strong>
                          <span className="muted">{formatPercent(holding.profitLossPercent)}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`portfolio-status-pill ${getPriceStatusClass(holding.priceStatus)}`}>
                          {holding.priceStatus || "UNAVAILABLE"}
                        </span>
                      </td>
                      <td>{formatDateTime(holding.lastPriceUpdateTime)}</td>
                      <td>
                        <div className="actions-row">
                          <button type="button" className="secondary-button" onClick={() => handleEditHolding(holding)}>
                            Edit
                          </button>
                          <button type="button" className="danger-button" onClick={() => handleDeleteHolding(holding.holdingId)}>
                            Delete
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
