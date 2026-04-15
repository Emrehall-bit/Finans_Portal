import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Pie, PieChart, Cell, ResponsiveContainer, Tooltip } from "recharts";
import { API_CONFIG } from "../api/config";
import { getTcmbMarketData } from "../api/marketApi";
import {
  createPortfolioTransaction,
  getPortfolioById,
  getPortfolioDetails,
  getPortfolioHoldings,
  getPortfolioSummary,
  getPortfolioTransactions,
  updatePortfolio,
} from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import useToast from "../hooks/useToast";
import { formatCurrency, formatDateTime, formatNumber } from "../utils/formatters";

const CHART_COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];

export default function PortfolioDetailPage() {
  const { portfolioId } = useParams();
  const [portfolioInfo, setPortfolioInfo] = useState(null);
  const [summary, setSummary] = useState(null);
  const [holdings, setHoldings] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [marketInstruments, setMarketInstruments] = useState([]);
  const [settingsForm, setSettingsForm] = useState({ portfolioName: "", visibilityStatus: "PRIVATE" });
  const [transactionForm, setTransactionForm] = useState({ instrumentCode: "", quantity: "" });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { toast, showToast } = useToast();
  const userId = API_CONFIG.DEMO_USER_ID;

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
        setTransactions(details.transactions || []);
      } else {
        const [portfolio, summaryData, holdingsData, txData] = await Promise.all([
          getPortfolioById(portfolioId),
          getPortfolioSummary(portfolioId),
          getPortfolioHoldings(portfolioId),
          getPortfolioTransactions(portfolioId),
        ]);
        setPortfolioInfo(portfolio);
        setSettingsForm({
          portfolioName: portfolio?.portfolioName || "",
          visibilityStatus: portfolio?.visibilityStatus || "PRIVATE",
        });
        setSummary(summaryData);
        setHoldings(holdingsData);
        setTransactions(txData);
      }
    } catch (err) {
      setError(extractErrorMessage(err, "Portfolio detail could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  async function loadMarketInstruments() {
    try {
      const data = await getTcmbMarketData();
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
    const totalValue = holdings.reduce((sum, h) => sum + Number(h.currentValue || 0), 0);
    if (totalValue <= 0) return [];
    return holdings.map((h) => ({
      instrumentCode: h.instrumentCode,
      value: Number(h.currentValue || 0),
      percentage: (Number(h.currentValue || 0) / totalValue) * 100,
    }));
  }, [holdings]);

  const profitLossBase = summary?.profitLoss ?? summary?.totalProfitLoss ?? 0;
  const totalCostBase = Number(summary?.totalCost ?? 0);

  async function handleSaveSettings(e) {
    e.preventDefault();
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
      showToast("success", "Portfolio settings saved.");
    } catch (err) {
      setError(extractErrorMessage(err, "Portfolio settings update failed."));
    }
  }

  async function handleCreateTransaction(transactionType) {
    const numericQuantity = Number(transactionForm.quantity);
    if (!transactionForm.instrumentCode || !Number.isFinite(numericQuantity) || numericQuantity <= 0) {
      setError("Please select an instrument and enter a valid quantity.");
      return;
    }
    try {
      setError("");
      await createPortfolioTransaction(portfolioId, {
        instrumentCode: transactionForm.instrumentCode,
        transactionType,
        quantity: numericQuantity,
      });
      setTransactionForm({ instrumentCode: "", quantity: "" });
      showToast("success", `${transactionType} transaction created.`);
      await loadPortfolioData();
    } catch (err) {
      setError(extractErrorMessage(err, "Transaction failed. You may have insufficient quantity to sell."));
    }
  }

  return (
    <div>
      <PageHeader
        title="Portfolio Detail"
        description={`Demo user id: ${userId}`}
        actions={<Link to="/portfolio">Back to Portfolio List</Link>}
      />
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {loading ? <LoadingSpinner label="Loading portfolio details..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error ? (
        <>
          <div className="card">
            <h3>Portfolio Info</h3>
            <p>
              <strong>Name:</strong> {portfolioInfo?.portfolioName || "-"}
            </p>
            <p>
              <strong>Visibility:</strong> {portfolioInfo?.visibilityStatus || "-"}
            </p>
            <p>
              <strong>Created:</strong> {formatDateTime(portfolioInfo?.createdAt)}
            </p>
          </div>

          <form className="card form-grid" onSubmit={handleSaveSettings}>
            <h3>Portfolio Settings</h3>
            <input
              required
              value={settingsForm.portfolioName}
              onChange={(e) => setSettingsForm((prev) => ({ ...prev, portfolioName: e.target.value }))}
              placeholder="Portfolio name"
            />
            <select
              required
              value={settingsForm.visibilityStatus}
              onChange={(e) => setSettingsForm((prev) => ({ ...prev, visibilityStatus: e.target.value }))}
            >
              <option value="PRIVATE">PRIVATE</option>
              <option value="PUBLIC">PUBLIC</option>
            </select>
            <button type="submit">Save</button>
          </form>

          <h3>Summary</h3>
          <div className="cards-grid compact">
            <SummaryCard title="Total Cost" value={formatCurrency(summary?.totalCost)} />
            <SummaryCard title="Current Value" value={formatCurrency(summary?.currentValue ?? summary?.totalCurrentValue)} />
            <SummaryCard title="Profit / Loss" value={formatCurrency(summary?.profitLoss ?? summary?.totalProfitLoss)} />
            <SummaryCard
              title="Profit / Loss %"
              value={
                totalCostBase > 0
                  ? `${formatNumber((Number(profitLossBase) / totalCostBase) * 100, 2)}%`
                  : "-"
              }
            />
          </div>

          <div className="card">
            <h3>Create Transaction</h3>
            <div className="form-grid">
              <select
                required
                value={transactionForm.instrumentCode}
                onChange={(e) => setTransactionForm((prev) => ({ ...prev, instrumentCode: e.target.value }))}
              >
                <option value="">Select instrument</option>
                {marketInstruments.map((instrument, idx) => (
                  <option key={`${instrument.symbol || "instrument"}-${idx}`} value={instrument.symbol || ""}>
                    {[instrument.symbol, instrument.name].filter(Boolean).join(" - ") || "Unnamed instrument"}
                  </option>
                ))}
              </select>
              <input
                type="number"
                step="any"
                required
                value={transactionForm.quantity}
                onChange={(e) => setTransactionForm((prev) => ({ ...prev, quantity: e.target.value }))}
                placeholder="Quantity"
              />
              <div className="actions-row">
                <button
                  type="button"
                  disabled={!transactionForm.instrumentCode || Number(transactionForm.quantity) <= 0}
                  onClick={() => handleCreateTransaction("BUY")}
                >
                  BUY
                </button>
                <button
                  type="button"
                  disabled={!transactionForm.instrumentCode || Number(transactionForm.quantity) <= 0}
                  onClick={() => handleCreateTransaction("SELL")}
                >
                  SELL
                </button>
              </div>
            </div>
          </div>

          <div className="split-grid">
            <div className="card">
              <h3>Holdings Chart</h3>
              {allocationData.length === 0 ? (
                <EmptyState title="No chart data" description="Create transactions to build holdings allocation." />
              ) : (
                <>
                  <div style={{ width: "100%", height: 280 }}>
                    <ResponsiveContainer>
                      <PieChart>
                        <Pie data={allocationData} dataKey="value" nameKey="instrumentCode" outerRadius={100}>
                          {allocationData.map((entry, index) => (
                            <Cell key={entry.instrumentCode} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip formatter={(value) => formatCurrency(value)} />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                  <div className="list">
                    {allocationData.map((entry, index) => (
                      <div key={entry.instrumentCode} className="list-item">
                        <strong style={{ color: CHART_COLORS[index % CHART_COLORS.length] }}>{entry.instrumentCode}</strong>
                        <span className="muted">
                          {formatCurrency(entry.value)} ({formatNumber(entry.percentage, 2)}%)
                        </span>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>

            <div className="card table-wrap">
              <h3>Holdings</h3>
              {holdings.length === 0 ? (
                <EmptyState title="No holdings" description="No holdings yet for this portfolio." />
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th>Instrument</th>
                      <th>Quantity</th>
                      <th>Average Buy Price</th>
                      <th>Current Price</th>
                      <th>Current Value</th>
                      <th>Profit / Loss</th>
                    </tr>
                  </thead>
                  <tbody>
                    {holdings.map((holding, idx) => (
                      <tr key={`${holding.instrumentCode}-${idx}`}>
                        <td>{holding.instrumentCode}</td>
                        <td>{formatNumber(holding.quantity)}</td>
                        <td>{formatCurrency(holding.averageBuyPrice)}</td>
                        <td>{formatCurrency(holding.currentPrice)}</td>
                        <td>{formatCurrency(holding.currentValue)}</td>
                        <td>{formatCurrency(holding.profitLoss)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>

          <div className="card table-wrap">
            <h3>Transaction History</h3>
            {transactions.length === 0 ? (
              <EmptyState title="No transactions" description="No transactions have been created yet." />
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Instrument</th>
                    <th>Quantity</th>
                    <th>Price</th>
                    <th>Transaction Time</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((transaction) => (
                    <tr key={transaction.id}>
                      <td>{transaction.transactionType}</td>
                      <td>{transaction.instrumentCode}</td>
                      <td>{formatNumber(transaction.quantity)}</td>
                      <td>{formatCurrency(transaction.price)}</td>
                      <td>{formatDateTime(transaction.transactionTime)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}
