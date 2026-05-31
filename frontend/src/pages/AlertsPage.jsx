import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { cancelAlert, createAlert, getUserAlerts } from "../api/alertApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import { useMarketQuotes } from "../hooks/useMarketQueries";
import useToast from "../hooks/useToast";
import { formatCurrency, formatDateTime, formatNumber } from "../utils/formatters";

export default function AlertsPage() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [isModalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [symbolSearch, setSymbolSearch] = useState("");
  const [form, setForm] = useState({
    instrumentCode: "",
    conditionType: "ABOVE",
    targetPrice: "",
  });
  const { toast, showToast } = useToast();
  const { data: quotes = [], isLoading: quotesLoading } = useMarketQuotes({ enabled: !!userId });

  useEffect(() => {
    if (userId) {
      loadData();
    }
  }, [userId]);

  async function loadData() {
    try {
      setLoading(true);
      setError("");
      const alerts = await getUserAlerts(userId);
      setRows(alerts ?? []);
    } catch (err) {
      setError(extractErrorMessage(err, t("alerts.loadError")));
    } finally {
      setLoading(false);
    }
  }

  const activeAlerts = useMemo(() => rows.filter((item) => item.status === "ACTIVE"), [rows]);
  const passiveAlerts = useMemo(() => rows.filter((item) => item.status === "CANCELLED"), [rows]);
  const triggeredAlerts = useMemo(() => rows.filter((item) => item.status === "TRIGGERED" || item.triggeredAt), [rows]);

  const matchingQuotes = useMemo(() => {
    const query = symbolSearch.trim().toLowerCase();
    if (!query) {
      return quotes.slice(0, 8);
    }

    return quotes.filter((item) => {
      return (
        item.symbol?.toLowerCase().includes(query) ||
        item.displayName?.toLowerCase().includes(query)
      );
    }).slice(0, 8);
  }, [quotes, symbolSearch]);

  const selectedQuote = useMemo(
    () => quotes.find((item) => normalizeCode(item.symbol) === normalizeCode(form.instrumentCode)) || null,
    [quotes, form.instrumentCode],
  );

  function openCreateModal() {
    setSymbolSearch("");
    setForm({ instrumentCode: "", conditionType: "ABOVE", targetPrice: "" });
    setModalOpen(true);
  }

  function closeCreateModal() {
    setModalOpen(false);
    setSymbolSearch("");
    setForm({ instrumentCode: "", conditionType: "ABOVE", targetPrice: "" });
  }

  function selectInstrument(item) {
    setForm((current) => ({
      ...current,
      instrumentCode: item.symbol || "",
      targetPrice: current.targetPrice || item.price || "",
    }));
    setSymbolSearch(item.symbol || "");
  }

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      setSaving(true);
      setError("");
      await createAlert(userId, {
        instrumentCode: form.instrumentCode,
        conditionType: form.conditionType,
        targetPrice: Number(form.targetPrice),
      });
      showToast("success", t("alerts.createSuccess"));
      closeCreateModal();
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, t("alerts.createError")));
    } finally {
      setSaving(false);
    }
  }

  async function handleCancel(alertId) {
    try {
      await cancelAlert(userId, alertId);
      showToast("success", t("alerts.cancelSuccess"));
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, t("alerts.cancelError")));
    }
  }

  return (
    <div className="dashboard-stack alerts-management-shell">
      <PageHeader
        title={t("alerts.title")}
        description={t("alerts.description")}
        eyebrow={t("alerts.eyebrow")}
        actions={
          <div className="actions-row">
            <button type="button" onClick={openCreateModal}>
              {t("alerts.create")}
            </button>
          </div>
        }
      />

      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {loading || quotesLoading ? <LoadingSpinner label={t("alerts.loading")} /> : null}

      {!loading && !quotesLoading ? (
        <>
          <section className="ticker-grid alerts-kpi-grid">
            <SummaryCard title={t("alerts.cards.activeTitle")} value={formatNumber(activeAlerts.length, 0)} subtitle={t("alerts.cards.activeSubtitle")} tone="cool" />
            <SummaryCard title={t("alerts.cards.triggeredTitle")} value={formatNumber(triggeredAlerts.length, 0)} subtitle={t("alerts.cards.triggeredSubtitle")} tone="warm" />
            <SummaryCard title={t("alerts.cards.passiveTitle")} value={formatNumber(passiveAlerts.length, 0)} subtitle={t("alerts.cards.passiveSubtitle")} tone="neutral" />
            <SummaryCard title={t("alerts.cards.symbolsTitle")} value={formatNumber(uniqueSymbolCount(rows), 0)} subtitle={t("alerts.cards.symbolsSubtitle")} tone="cool" />
          </section>

          <section className="alerts-layout-grid">
            <section className="panel-surface alerts-panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">{t("alerts.activeEyebrow")}</p>
                  <h3>{t("alerts.activeTitle")}</h3>
                </div>
                <span className="summary-chip">{t("alerts.activeCount", { count: activeAlerts.length })}</span>
              </div>

              {activeAlerts.length === 0 ? (
                <EmptyState title={t("alerts.emptyActiveTitle")} description={t("alerts.emptyActiveDescription")} />
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>{t("alerts.table.instrument")}</th>
                        <th>{t("alerts.table.condition")}</th>
                        <th>{t("alerts.table.threshold")}</th>
                        <th>{t("alerts.table.lastPrice")}</th>
                        <th>{t("alerts.table.status")}</th>
                        <th>{t("alerts.table.action")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {activeAlerts.map((item) => (
                        <tr key={item.id}>
                          <td>
                            <div className="portfolio-cell-stack">
                              <strong>{item.instrumentCode || "-"}</strong>
                              <span className="muted">{item.source || t("alerts.noSource")}</span>
                            </div>
                          </td>
                          <td>{formatCondition(item.conditionType, t)}</td>
                          <td>{formatCurrency(item.targetPrice)}</td>
                          <td>{formatCurrency(item.currentPrice)}</td>
                          <td>
                            <span className="portfolio-status-pill is-live">{t("alerts.status.active")}</span>
                          </td>
                          <td>
                            <button type="button" className="danger-button" onClick={() => handleCancel(item.id)}>
                              {t("alerts.deactivate")}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <aside className="alerts-side-stack">
              <section className="panel-surface alerts-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("alerts.notificationsEyebrow")}</p>
                    <h3>{t("alerts.notificationsTitle")}</h3>
                  </div>
                </div>

                {triggeredAlerts.length === 0 ? (
                  <EmptyState title={t("alerts.emptyTriggeredTitle")} description={t("alerts.emptyTriggeredDescription")} />
                ) : (
                  <div className="finance-notification-list">
                    {triggeredAlerts.map((item) => (
                      <article key={item.id} className="finance-notification-card">
                        <strong>{t("alerts.triggeredLabel", { symbol: item.instrumentCode })}</strong>
                        <p>
                          {formatCondition(item.conditionType, t)} {formatCurrency(item.targetPrice)}
                        </p>
                        <span>{formatDateTime(item.triggeredAt || item.lastUpdated)}</span>
                      </article>
                    ))}
                  </div>
                )}
              </section>

              <section className="panel-surface alerts-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("alerts.passiveEyebrow")}</p>
                    <h3>{t("alerts.passiveTitle")}</h3>
                  </div>
                </div>

                {passiveAlerts.length === 0 ? (
                  <EmptyState title={t("alerts.emptyPassiveTitle")} description={t("alerts.emptyPassiveDescription")} />
                ) : (
                  <div className="alerts-archive-list">
                    {passiveAlerts.map((item) => (
                      <div key={item.id} className="alerts-archive-card">
                        <strong>{item.instrumentCode}</strong>
                        <p>
                          {formatCondition(item.conditionType, t)} {formatCurrency(item.targetPrice)}
                        </p>
                        <span>{formatDateTime(item.createdAt)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </aside>
          </section>
        </>
      ) : null}

      {isModalOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={closeCreateModal}>
          <div className="auth-modal alerts-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="portfolio-action-modal-head">
              <div>
                <p className="eyebrow">{t("alerts.modalEyebrow")}</p>
                <h3>{t("alerts.modalTitle")}</h3>
              </div>
              <button type="button" className="secondary-button" onClick={closeCreateModal}>
                {t("common.close")}
              </button>
            </div>

            <form className="instrument-action-form" onSubmit={handleSubmit}>
              <label className="portfolio-field">
                <span>{t("alerts.searchInstrument")}</span>
                <input
                  value={symbolSearch}
                  onChange={(event) => {
                    const value = event.target.value;
                    setSymbolSearch(value);
                    setForm((current) => ({ ...current, instrumentCode: normalizeCode(value) }));
                  }}
                  placeholder={t("alerts.searchPlaceholder")}
                />
              </label>

              <div className="alerts-picker-grid">
                {matchingQuotes.map((item) => (
                  <button
                    key={`${item.symbol}-${item.source}`}
                    type="button"
                    className={`analysis-picker-card${normalizeCode(form.instrumentCode) === normalizeCode(item.symbol) ? " active" : ""}`}
                    onClick={() => selectInstrument(item)}
                  >
                    <strong>{item.symbol}</strong>
                    <span>{item.displayName || item.source || "-"}</span>
                  </button>
                ))}
              </div>

              <div className="instrument-action-grid">
                <label className="portfolio-field">
                  <span>{t("alerts.table.condition")}</span>
                  <select
                    value={form.conditionType}
                    onChange={(event) => setForm((current) => ({ ...current, conditionType: event.target.value }))}
                  >
                    <option value="ABOVE">{t("alerts.conditions.above")}</option>
                    <option value="BELOW">{t("alerts.conditions.below")}</option>
                  </select>
                </label>
                <label className="portfolio-field">
                  <span>{t("alerts.thresholdValue")}</span>
                  <input
                    required
                    type="number"
                    step="any"
                    min="0.0001"
                    value={form.targetPrice}
                    onChange={(event) => setForm((current) => ({ ...current, targetPrice: event.target.value }))}
                  />
                </label>
              </div>

              <div className="alerts-selected-quote">
                <span>{t("alerts.selectedSymbol")}</span>
                <strong>{form.instrumentCode || "-"}</strong>
                <p>{t("alerts.lastPrice")} {selectedQuote ? formatCurrency(selectedQuote.price, selectedQuote.currency || "TRY") : t("alerts.noData")}</p>
              </div>

              <div className="instrument-action-footer">
                <button type="submit" disabled={saving || !form.instrumentCode}>
                  {saving ? t("alerts.creating") : t("alerts.create")}
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

function formatCondition(value, t) {
  return {
    ABOVE: t("alerts.conditions.above"),
    BELOW: t("alerts.conditions.below"),
  }[value] ?? value ?? "-";
}

function uniqueSymbolCount(rows) {
  return new Set(rows.map((item) => item.instrumentCode).filter(Boolean)).size;
}

