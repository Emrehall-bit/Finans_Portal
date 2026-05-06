import { useEffect, useMemo, useState } from "react";
import { cancelAlert, createAlert, getUserAlerts } from "../api/alertApi";
import { getMarketQuotes } from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import SummaryCard from "../components/common/SummaryCard";
import useToast from "../hooks/useToast";
import { formatCurrency, formatDateTime, formatNumber } from "../utils/formatters";

export default function AlertsPage() {
  const { userId } = useAuth();
  const [rows, setRows] = useState([]);
  const [quotes, setQuotes] = useState([]);
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

  useEffect(() => {
    if (userId) {
      loadData();
    }
  }, [userId]);

  async function loadData() {
    try {
      setLoading(true);
      setError("");
      const [alerts, marketQuotes] = await Promise.all([
        getUserAlerts(userId),
        getMarketQuotes().catch(() => []),
      ]);
      setRows(alerts ?? []);
      setQuotes(marketQuotes ?? []);
    } catch (err) {
      setError(extractErrorMessage(err, "Alarmlar yuklenemedi."));
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
      showToast("success", "Alarm olusturuldu.");
      closeCreateModal();
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, "Alarm olusturulamadi."));
    } finally {
      setSaving(false);
    }
  }

  async function handleCancel(alertId) {
    try {
      await cancelAlert(userId, alertId);
      showToast("success", "Alarm pasife alindi.");
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, "Alarm iptal edilemedi."));
    }
  }

  return (
    <div className="dashboard-stack alerts-management-shell">
      <PageHeader
        title="Alarmlar"
        description="Fiyat seviyelerini izle, aktif kurallari yonet ve tetiklenen gecmisi takip et."
        eyebrow="Bildirimler"
        actions={
          <div className="actions-row">
            <button type="button" onClick={openCreateModal}>
              Alarm olustur
            </button>
          </div>
        }
      />

      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {loading ? <LoadingSpinner label="Alarmlar yukleniyor..." /> : null}

      {!loading ? (
        <>
          <section className="ticker-grid alerts-kpi-grid">
            <SummaryCard title="Aktif alarmlar" value={formatNumber(activeAlerts.length, 0)} subtitle="Tetik bekleyen kurallar" tone="cool" />
            <SummaryCard title="Tetiklenen" value={formatNumber(triggeredAlerts.length, 0)} subtitle="Gecmis bildirimler" tone="warm" />
            <SummaryCard title="Pasif" value={formatNumber(passiveAlerts.length, 0)} subtitle="Iptal edilen kurallar" tone="neutral" />
            <SummaryCard title="Izlenen sembol" value={formatNumber(uniqueSymbolCount(rows), 0)} subtitle="Alarm tanimli enstruman" tone="cool" />
          </section>

          <section className="alerts-layout-grid">
            <section className="panel-surface alerts-panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Aktif Alarmlar</p>
                  <h3>Calisan kurallar</h3>
                </div>
                <span className="summary-chip">{formatNumber(activeAlerts.length, 0)} aktif</span>
              </div>

              {activeAlerts.length === 0 ? (
                <EmptyState title="Aktif alarm yok" description="Alarm olusturarak fiyat seviyelerini takip etmeye basla." />
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Enstruman</th>
                        <th>Kosul</th>
                        <th>Esik</th>
                        <th>Son fiyat</th>
                        <th>Durum</th>
                        <th>Aksiyon</th>
                      </tr>
                    </thead>
                    <tbody>
                      {activeAlerts.map((item) => (
                        <tr key={item.id}>
                          <td>
                            <div className="portfolio-cell-stack">
                              <strong>{item.instrumentCode || "-"}</strong>
                              <span className="muted">{item.source || "Kaynak yok"}</span>
                            </div>
                          </td>
                          <td>{formatCondition(item.conditionType)}</td>
                          <td>{formatCurrency(item.targetPrice)}</td>
                          <td>{formatCurrency(item.currentPrice)}</td>
                          <td>
                            <span className="portfolio-status-pill is-live">Aktif</span>
                          </td>
                          <td>
                            <button type="button" className="danger-button" onClick={() => handleCancel(item.id)}>
                              Pasife al
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* TODO: backend tarafinda alarm yeniden aktif etme endpointi olmadigi icin pasif -> aktif toggle sunulmuyor. */}
            </section>

            <aside className="alerts-side-stack">
              <section className="panel-surface alerts-panel">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">Bildirimler</p>
                    <h3>Tetiklenen gecmis</h3>
                  </div>
                </div>

                {triggeredAlerts.length === 0 ? (
                  <EmptyState title="Tetiklenen alarm yok" description="Kosullar saglandiginda gecmis burada listelenecek." />
                ) : (
                  <div className="finance-notification-list">
                    {triggeredAlerts.map((item) => (
                      <article key={item.id} className="finance-notification-card">
                        <strong>{item.instrumentCode} alarmi tetiklendi</strong>
                        <p>
                          {formatCondition(item.conditionType)} {formatCurrency(item.targetPrice)}
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
                    <p className="eyebrow">Pasif Alarmlar</p>
                    <h3>Arsiv</h3>
                  </div>
                </div>

                {passiveAlerts.length === 0 ? (
                  <EmptyState title="Pasif alarm yok" description="Iptal edilen alarmlar burada gorunur." />
                ) : (
                  <div className="alerts-archive-list">
                    {passiveAlerts.map((item) => (
                      <div key={item.id} className="alerts-archive-card">
                        <strong>{item.instrumentCode}</strong>
                        <p>
                          {formatCondition(item.conditionType)} {formatCurrency(item.targetPrice)}
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
                <p className="eyebrow">Alarm Olustur</p>
                <h3>Yeni fiyat kurali</h3>
              </div>
              <button type="button" className="secondary-button" onClick={closeCreateModal}>
                Kapat
              </button>
            </div>

            <form className="instrument-action-form" onSubmit={handleSubmit}>
              <label className="portfolio-field">
                <span>Enstruman ara</span>
                <input
                  value={symbolSearch}
                  onChange={(event) => {
                    const value = event.target.value;
                    setSymbolSearch(value);
                    setForm((current) => ({ ...current, instrumentCode: normalizeCode(value) }));
                  }}
                  placeholder="BTCUSDT, THYAO, USDTRY..."
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
                  <span>Kosul</span>
                  <select
                    value={form.conditionType}
                    onChange={(event) => setForm((current) => ({ ...current, conditionType: event.target.value }))}
                  >
                    <option value="ABOVE">Fiyat ustune cikarsa</option>
                    <option value="BELOW">Fiyat altina dusurse</option>
                  </select>
                </label>
                <label className="portfolio-field">
                  <span>Esik deger</span>
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
                <span>Secili sembol</span>
                <strong>{form.instrumentCode || "-"}</strong>
                <p>Son fiyat: {selectedQuote ? formatCurrency(selectedQuote.price, selectedQuote.currency || "TRY") : "Veri yok"}</p>
              </div>

              <div className="instrument-action-footer">
                <button type="submit" disabled={saving || !form.instrumentCode}>
                  {saving ? "Olusturuluyor..." : "Alarm olustur"}
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
  return value == null ? "" : String(value).replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function formatCondition(value) {
  return {
    ABOVE: "Fiyat ustune cikarsa",
    BELOW: "Fiyat altina dusurse",
  }[value] ?? value ?? "-";
}

function uniqueSymbolCount(rows) {
  return new Set(rows.map((item) => item.instrumentCode).filter(Boolean)).size;
}
