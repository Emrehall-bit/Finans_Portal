import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Pie, PieChart, Cell, ResponsiveContainer, Tooltip } from "recharts";
import {
  createPortfolio,
  createPortfolioHolding,
  deletePortfolioHolding,
  getPortfolioDetails,
  getUserPortfolios,
  updatePortfolioHolding,
} from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import SummaryCard from "../components/common/SummaryCard";
import useToast from "../hooks/useToast";
import { useTheme } from "../theme/ThemeContext";
import { formatCurrency, formatDateTime, formatNumber, formatPercent } from "../utils/formatters";

const CHART_COLORS = ["#2563eb", "#059669", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#4f46e5"];

export default function PortfolioPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const { chartTheme } = useTheme();
  const { toast, showToast } = useToast();
  const [portfolios, setPortfolios] = useState([]);
  const [selectedPortfolioId, setSelectedPortfolioId] = useState(null);
  const [selectedPortfolio, setSelectedPortfolio] = useState(null);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState("");
  const [newPortfolio, setNewPortfolio] = useState({ portfolioName: "", visibilityStatus: "PRIVATE" });
  const [isHoldingModalOpen, setHoldingModalOpen] = useState(false);
  const [editingHolding, setEditingHolding] = useState(null);
  const [holdingForm, setHoldingForm] = useState({
    instrumentCode: "",
    quantity: "",
    buyPrice: "",
  });
  const [savingHolding, setSavingHolding] = useState(false);

  useEffect(() => {
    if (userId) {
      loadPortfolios();
    }
  }, [userId]);

  useEffect(() => {
    if (selectedPortfolioId) {
      loadPortfolioDetails(selectedPortfolioId);
    } else {
      setSelectedPortfolio(null);
    }
  }, [selectedPortfolioId]);

  async function loadPortfolios(preferredId = null) {
    try {
      setLoadingList(true);
      setError("");
      const list = await getUserPortfolios(userId);
      setPortfolios(list);

      if (list.length === 0) {
        setSelectedPortfolioId(null);
        return;
      }

      const resolvedId =
        preferredId && list.some((item) => item.portfolioId === preferredId)
          ? preferredId
          : selectedPortfolioId && list.some((item) => item.portfolioId === selectedPortfolioId)
            ? selectedPortfolioId
            : list[0].portfolioId;

      setSelectedPortfolioId(resolvedId);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.loadListError")));
    } finally {
      setLoadingList(false);
    }
  }

  async function loadPortfolioDetails(portfolioId) {
    try {
      setLoadingDetail(true);
      setError("");
      const details = await getPortfolioDetails(portfolioId);
      setSelectedPortfolio(details);
    } catch (err) {
      setSelectedPortfolio(null);
      setError(extractErrorMessage(err, t("portfolio.loadDetailError")));
    } finally {
      setLoadingDetail(false);
    }
  }

  async function handleCreatePortfolio(event) {
    event.preventDefault();
    try {
      setError("");
      const created = await createPortfolio(userId, newPortfolio);
      showToast("success", t("portfolio.createSuccess"));
      setNewPortfolio({ portfolioName: "", visibilityStatus: "PRIVATE" });
      await loadPortfolios(created?.portfolioId ?? null);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.createError")));
    }
  }

  function openAddHoldingModal() {
    setEditingHolding(null);
    setHoldingForm({ instrumentCode: "", quantity: "", buyPrice: "" });
    setHoldingModalOpen(true);
  }

  function openEditHoldingModal(holding) {
    setEditingHolding(holding);
    setHoldingForm({
      instrumentCode: holding.instrumentCode || "",
      quantity: holding.quantity ?? "",
      buyPrice: holding.buyPrice ?? "",
    });
    setHoldingModalOpen(true);
  }

  function closeHoldingModal() {
    setHoldingModalOpen(false);
    setEditingHolding(null);
    setHoldingForm({ instrumentCode: "", quantity: "", buyPrice: "" });
  }

  async function handleSaveHolding(event) {
    event.preventDefault();
    if (!selectedPortfolio?.portfolioId) {
      return;
    }

    try {
      setSavingHolding(true);
      setError("");
      const payload = {
        instrumentCode: normalizeCode(holdingForm.instrumentCode),
        quantity: Number(holdingForm.quantity),
        buyPrice: Number(holdingForm.buyPrice),
      };

      if (editingHolding?.holdingId) {
        await updatePortfolioHolding(selectedPortfolio.portfolioId, editingHolding.holdingId, {
          quantity: payload.quantity,
          buyPrice: payload.buyPrice,
        });
        showToast("success", t("portfolio.holdingUpdateSuccess"));
      } else {
        await createPortfolioHolding(selectedPortfolio.portfolioId, payload);
        showToast("success", t("portfolio.holdingAddSuccess"));
      }

      closeHoldingModal();
      await loadPortfolioDetails(selectedPortfolio.portfolioId);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.holdingSaveError")));
    } finally {
      setSavingHolding(false);
    }
  }

  async function handleDeleteHolding(holdingId) {
    if (!selectedPortfolio?.portfolioId) {
      return;
    }

    try {
      setError("");
      await deletePortfolioHolding(selectedPortfolio.portfolioId, holdingId);
      showToast("success", t("portfolio.holdingDeleteSuccess"));
      await loadPortfolioDetails(selectedPortfolio.portfolioId);
    } catch (err) {
      setError(extractErrorMessage(err, t("portfolio.holdingDeleteError")));
    }
  }

  const summary = selectedPortfolio?.summary ?? null;
  const holdings = selectedPortfolio?.holdings ?? [];
  const allocationData = useMemo(() => {
    const totalValue = holdings.reduce((sum, holding) => sum + toNumber(holding.currentValue), 0);
    if (totalValue <= 0) {
      return [];
    }

    return holdings.map((holding) => ({
      instrumentCode: holding.instrumentCode,
      value: toNumber(holding.currentValue),
      percentage: totalValue > 0 ? (toNumber(holding.currentValue) / totalValue) * 100 : 0,
    }));
  }, [holdings]);

  return (
    <div className="portfolio-management-shell">
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}

      <section className="portfolio-management-grid">
        <aside className="panel-surface portfolio-management-sidebar">
          <div className="panel-head">
            <div>
              <p className="eyebrow">{t("portfolio.listEyebrow")}</p>
              <h3>{t("portfolio.listTitle")}</h3>
            </div>
            <span className="summary-chip">{t("common.records", { count: portfolios.length })}</span>
          </div>

          <form className="portfolio-create-panel" onSubmit={handleCreatePortfolio}>
            <label className="portfolio-field">
              <span>{t("portfolio.name")}</span>
              <input
                required
                value={newPortfolio.portfolioName}
                onChange={(event) => setNewPortfolio((current) => ({ ...current, portfolioName: event.target.value }))}
                placeholder={t("portfolio.namePlaceholder")}
              />
            </label>
            <label className="portfolio-field">
              <span>{t("portfolio.visibility")}</span>
              <select
                value={newPortfolio.visibilityStatus}
                onChange={(event) => setNewPortfolio((current) => ({ ...current, visibilityStatus: event.target.value }))}
              >
                <option value="PRIVATE">{t("portfolio.visibilityOptions.PRIVATE")}</option>
                <option value="PUBLIC">{t("portfolio.visibilityOptions.PUBLIC")}</option>
              </select>
            </label>
            <button type="submit">{t("portfolio.create")}</button>
          </form>

          {loadingList ? <LoadingSpinner label={t("portfolio.loadingList")} /> : null}
          {!loadingList && portfolios.length === 0 ? (
            <EmptyState title={t("portfolio.emptyListTitle")} description={t("portfolio.emptyListDescription")} />
          ) : null}

          {!loadingList && portfolios.length > 0 ? (
            <div className="portfolio-selector-list">
              {portfolios.map((portfolio) => (
                <button
                  key={portfolio.portfolioId}
                  type="button"
                  className={`portfolio-selector-card${portfolio.portfolioId === selectedPortfolioId ? " active" : ""}`}
                  onClick={() => setSelectedPortfolioId(portfolio.portfolioId)}
                >
                  <strong>{portfolio.portfolioName}</strong>
                  <span>{formatVisibilityStatus(portfolio.visibilityStatus, t)}</span>
                  <small>{formatDateTime(portfolio.createdAt)}</small>
                </button>
              ))}
            </div>
          ) : null}
        </aside>

        <div className="portfolio-management-main">
          {loadingDetail ? <LoadingSpinner label={t("portfolio.loadingDetail")} /> : null}

          {!loadingDetail && !selectedPortfolio ? (
            <section className="panel-surface portfolio-management-panel">
              <EmptyState title={t("portfolio.emptySelectionTitle")} description={t("portfolio.emptySelectionDescription")} />
            </section>
          ) : null}

          {!loadingDetail && selectedPortfolio ? (
            <>
              <section className="panel-surface portfolio-management-panel portfolio-management-hero">
                <div className="portfolio-management-hero-head">
                  <div>
                    <p className="eyebrow">{t("portfolio.selectedEyebrow")}</p>
                    <h2>{selectedPortfolio.portfolioName || "-"}</h2>
                    <p className="page-description">
                      {formatVisibilityStatus(selectedPortfolio.visibilityStatus || "PRIVATE", t)} · {t("portfolio.createdAt", { value: formatDateTime(selectedPortfolio.createdAt) })}
                    </p>
                  </div>
                  <div className="actions-row">
                    <button type="button" onClick={openAddHoldingModal}>
                      {t("portfolio.addAsset")}
                    </button>
                  </div>
                </div>

                <div className="cards-grid compact">
                  <SummaryCard title={t("portfolio.summary.totalCost")} value={formatCurrency(summary?.totalCost)} subtitle={t("portfolio.summary.totalCostSubtitle")} tone="cool" />
                  <SummaryCard title={t("portfolio.summary.currentValue")} value={formatCurrency(summary?.currentValue ?? summary?.totalCurrentValue)} subtitle={t("portfolio.summary.currentValueSubtitle")} tone="cool" />
                  <SummaryCard title={t("portfolio.summary.profitLossAmount")} value={formatCurrency(summary?.profitLoss ?? summary?.totalProfitLoss)} subtitle={t("portfolio.summary.profitLossAmountSubtitle")} tone={toNumber(summary?.profitLoss ?? summary?.totalProfitLoss) >= 0 ? "cool" : "warm"} />
                  <SummaryCard title={t("portfolio.summary.profitLossPercent")} value={formatPercent(summary?.profitLossPercent)} subtitle={t("portfolio.summary.profitLossPercentSubtitle")} tone={toNumber(summary?.profitLossPercent) >= 0 ? "cool" : "warm"} />
                </div>

                <div className="portfolio-hero-allocation">
                  <div className="panel-head">
                    <div>
                      <p className="eyebrow">{t("portfolio.allocationEyebrow")}</p>
                      <h3>{t("portfolio.allocationTitle")}</h3>
                    </div>
                  </div>

                  {allocationData.length === 0 ? (
                    <EmptyState title={t("portfolio.allocationEmptyTitle")} description={t("portfolio.allocationEmptyDescription")} />
                  ) : (
                    <div className="portfolio-hero-allocation-shell">
                      <div className="portfolio-hero-allocation-chart">
                        <ResponsiveContainer>
                          <PieChart>
                            <Pie data={allocationData} dataKey="value" nameKey="instrumentCode" outerRadius={96} innerRadius={48}>
                              {allocationData.map((entry, index) => (
                                <Cell key={`${entry.instrumentCode}-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                              ))}
                            </Pie>
                            <Tooltip
                              formatter={(value) => formatCurrency(value)}
                              contentStyle={chartTheme.tooltipContentStyle}
                              itemStyle={chartTheme.tooltipItemStyle}
                              labelStyle={chartTheme.tooltipLabelStyle}
                            />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                      <div className="portfolio-hero-allocation-list">
                        {allocationData.map((entry, index) => (
                          <div key={`${entry.instrumentCode}-${index}`} className="portfolio-allocation-item">
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
                    </div>
                  )}
                </div>
              </section>

              <section className="portfolio-management-content-grid">
                <section className="panel-surface portfolio-management-panel">
                  <div className="panel-head">
                    <div>
                      <p className="eyebrow">{t("portfolio.historyEyebrow")}</p>
                      <h3>{t("portfolio.historyTitle")}</h3>
                    </div>
                  </div>
                  {/* TODO: backend tarafinda portfoy performans gecmisi endpointi eklendiginde bu alan gercek veriyle doldurulacak. */}
                  <EmptyState title={t("portfolio.historyEmptyTitle")} description={t("portfolio.historyEmptyDescription")} />
                </section>
              </section>

              <section className="panel-surface portfolio-management-panel table-wrap portfolio-table-card">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("portfolio.holdingsEyebrow")}</p>
                    <h3>{t("portfolio.holdingsTitle")}</h3>
                  </div>
                  <span className="summary-chip">{t("common.rows", { count: formatNumber(holdings.length, 0) })}</span>
                </div>

                {holdings.length === 0 ? (
                  <EmptyState title={t("portfolio.emptyHoldingsTitle")} description={t("portfolio.emptyHoldingsDescription")} />
                ) : (
                  <table>
                    <thead>
                      <tr>
                        <th>{t("portfolio.table.asset")}</th>
                        <th>{t("portfolio.table.quantity")}</th>
                        <th>{t("portfolio.table.cost")}</th>
                        <th>{t("portfolio.table.currentPrice")}</th>
                        <th>{t("portfolio.table.currentValue")}</th>
                        <th>{t("portfolio.table.profitLoss")}</th>
                        <th>{t("portfolio.table.status")}</th>
                        <th>{t("portfolio.table.updatedAt")}</th>
                        <th>{t("portfolio.table.action")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {holdings.map((holding, index) => (
                        <tr key={`${holding.holdingId || holding.instrumentCode}-${index}`}>
                          <td>
                            <div className="portfolio-cell-stack">
                              <strong>{holding.instrumentCode || "-"}</strong>
                              <span className="muted">{t("portfolio.assetNumber", { index: index + 1 })}</span>
                            </div>
                          </td>
                          <td>{formatNumber(holding.quantity)}</td>
                          <td>{formatCurrency(holding.buyPrice)}</td>
                          <td>{holding.valuationAvailable ? formatCurrency(holding.currentPrice) : t("portfolio.priceMissing")}</td>
                          <td>{holding.valuationAvailable ? formatCurrency(holding.currentValue) : t("portfolio.missingData")}</td>
                          <td>
                            {holding.valuationAvailable ? (
                              <div className="portfolio-cell-stack">
                                <strong>{formatCurrency(holding.profitLoss)}</strong>
                                <span className="muted">{formatPercent(holding.profitLossPercent)}</span>
                              </div>
                            ) : (
                              <span className="muted">{t("portfolio.missingData")}</span>
                            )}
                          </td>
                          <td>
                            <span className={`portfolio-status-pill ${getPriceStatusClass(holding.priceStatus)}`}>
                              {formatPriceStatus(holding.priceStatus, t)}
                            </span>
                          </td>
                          <td>{formatDateTime(holding.lastPriceUpdateTime)}</td>
                          <td>
                            <div className="actions-row">
                              <button type="button" className="secondary-button" onClick={() => openEditHoldingModal(holding)}>
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
      </section>

      {isHoldingModalOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={closeHoldingModal}>
          <div className="auth-modal portfolio-action-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="portfolio-action-modal-head">
              <div>
                <p className="eyebrow">{t("portfolio.modalEyebrow")}</p>
                <h3>{editingHolding ? t("portfolio.modalEditTitle") : t("portfolio.modalAddTitle")}</h3>
              </div>
              <button type="button" className="secondary-button" onClick={closeHoldingModal}>
                {t("common.close")}
              </button>
            </div>

            <form className="instrument-action-form" onSubmit={handleSaveHolding}>
              <label className="portfolio-field">
                <span>{t("portfolio.instrumentCode")}</span>
                <input
                  required
                  disabled={Boolean(editingHolding)}
                  value={holdingForm.instrumentCode}
                  onChange={(event) => setHoldingForm((current) => ({ ...current, instrumentCode: event.target.value }))}
                  placeholder={t("portfolio.instrumentCodePlaceholder")}
                />
              </label>

              <div className="instrument-action-grid">
                <label className="portfolio-field">
                  <span>{t("portfolio.quantity")}</span>
                  <input
                    required
                    type="number"
                    step="any"
                    min="0.0001"
                    value={holdingForm.quantity}
                    onChange={(event) => setHoldingForm((current) => ({ ...current, quantity: event.target.value }))}
                  />
                </label>
                <label className="portfolio-field">
                  <span>{t("portfolio.buyPrice")}</span>
                  <input
                    required
                    type="number"
                    step="any"
                    min="0.0001"
                    value={holdingForm.buyPrice}
                    onChange={(event) => setHoldingForm((current) => ({ ...current, buyPrice: event.target.value }))}
                  />
                </label>
              </div>

              <div className="instrument-action-footer">
                <button type="submit" disabled={savingHolding}>
                  {savingHolding ? t("portfolio.saving") : editingHolding ? t("portfolio.update") : t("portfolio.addAsset")}
                </button>
              </div>
            </form>
          </div>
        </div>
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

function toNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
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

function formatPriceStatus(value, t) {
  return {
    LIVE: t("portfolio.status.LIVE"),
    CACHED: t("portfolio.status.CACHED"),
    STALE: t("portfolio.status.STALE"),
    UNAVAILABLE: t("portfolio.status.UNAVAILABLE"),
  }[value] ?? t("portfolio.status.UNAVAILABLE");
}

function formatVisibilityStatus(value, t) {
  return {
    PRIVATE: t("portfolio.visibilityOptions.PRIVATE"),
    PUBLIC: t("portfolio.visibilityOptions.PUBLIC"),
  }[value] ?? (value || "-");
}

