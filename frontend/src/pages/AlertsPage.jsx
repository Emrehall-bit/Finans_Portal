import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Check, Maximize2, Pencil, Pin, Plus, Trash2, X } from "lucide-react";
import { createAlert, deleteAlert, getUserAlerts, updateAlert } from "../api/alertApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { useMarketQuotes } from "../hooks/useMarketQueries";
import useToast from "../hooks/useToast";
import { formatCurrency, formatDateTime, formatNumber } from "../utils/formatters";
import { getFxCodeLabel } from "../utils/instrumentUtils";

const POPULAR_SYMBOLS = ["BTC", "THYAO", "EUR", "USD", "GBP", "TUPRS", "AKBNK"];

export default function AlertsPage() {
  const { i18n, t } = useTranslation();
  const { userId, user, updateUserProfile } = useAuth();
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
  const [editingAlert, setEditingAlert] = useState(null);
  const [editForm, setEditForm] = useState({ instrumentCode: "", conditionType: "ABOVE", targetPrice: "" });
  const [editSymbolSearch, setEditSymbolSearch] = useState("");
  const [editSaving, setEditSaving] = useState(false);
  const { toast, showToast } = useToast();
  const { data: quotes = [], isLoading: quotesLoading } = useMarketQuotes({ enabled: !!userId });

  // ── Notes state ──
  const [notes, setNotes] = useState([]);
  const sortedNotes = useMemo(
    () => [...notes].sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)),
    [notes],
  );
  const [addingNote, setAddingNote] = useState(false);
  const [newNoteText, setNewNoteText] = useState("");
  const [editingId, setEditingId] = useState(null);
  const [editingText, setEditingText] = useState("");
  const [notesSaving, setNotesSaving] = useState(false);
  const [expandedNoteId, setExpandedNoteId] = useState(null);
  const [popupEditMode, setPopupEditMode] = useState(false);
  const [popupEditText, setPopupEditText] = useState("");

  useEffect(() => {
    if (userId) {
      loadData();
    }
  }, [userId]);

  useEffect(() => {
    setNotes(Array.isArray(user?.notes) ? user.notes : []);
  }, [user]);

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
  const triggeredAlerts = useMemo(() => rows.filter((item) => item.status === "TRIGGERED" || item.triggeredAt), [rows]);

  const quotePriceMap = useMemo(
    () => new Map(quotes.map((q) => [normalizeCode(q.symbol), q.price])),
    [quotes],
  );

  const matchingQuotes = useMemo(() => {
    const query = symbolSearch.trim().toLowerCase();
    if (!query) {
      return POPULAR_SYMBOLS
        .map((kw) => quotes.find((q) => {
          const primary = formatAlertInstrument(q.symbol, q.source, i18n.resolvedLanguage).primary;
          return primary === kw || primary.startsWith(kw);
        }))
        .filter(Boolean);
    }
    return quotes.filter((item) =>
      item.symbol?.toLowerCase().includes(query) || item.displayName?.toLowerCase().includes(query),
    ).slice(0, 8);
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
    const displaySymbol = formatAlertInstrument(item.symbol, item.source, i18n.resolvedLanguage).primary;
    setForm((current) => ({
      ...current,
      instrumentCode: item.symbol || "",
      targetPrice: current.targetPrice || item.price || "",
    }));
    setSymbolSearch(displaySymbol === "-" ? item.symbol || "" : displaySymbol);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      setSaving(true);
      await createAlert(userId, {
        instrumentCode: form.instrumentCode,
        conditionType: form.conditionType,
        targetPrice: Number(form.targetPrice),
      });
      showToast("success", t("alerts.createSuccess"));
      closeCreateModal();
      await loadData();
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("alerts.createError")));
    } finally {
      setSaving(false);
    }
  }

  function openEditModal(alert) {
    setEditingAlert(alert);
    setEditForm({
      instrumentCode: alert.instrumentCode,
      conditionType: alert.conditionType,
      targetPrice: String(alert.targetPrice),
    });
    setEditSymbolSearch(formatAlertInstrument(alert.instrumentCode, null, i18n.resolvedLanguage).primary);
  }

  function closeEditModal() {
    setEditingAlert(null);
    setEditForm({ instrumentCode: "", conditionType: "ABOVE", targetPrice: "" });
    setEditSymbolSearch("");
  }

  async function handleUpdate(event) {
    event.preventDefault();
    try {
      setEditSaving(true);
      await updateAlert(userId, editingAlert.id, {
        instrumentCode: editForm.instrumentCode,
        conditionType: editForm.conditionType,
        targetPrice: Number(editForm.targetPrice),
      });
      showToast("success", t("alerts.updateSuccess"));
      closeEditModal();
      await loadData();
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("alerts.updateError")));
    } finally {
      setEditSaving(false);
    }
  }

  async function handleDelete(alertId) {
    try {
      await deleteAlert(userId, alertId);
      showToast("success", t("alerts.deleteSuccess"));
      await loadData();
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("alerts.deleteError")));
    }
  }

  // ── Notes handlers ──
  async function saveNotes(updatedNotes) {
    setNotesSaving(true);
    try {
      await updateUserProfile({
        fullName: user?.fullName ?? "",
        preferredLanguage: user?.preferredLanguage ?? null,
        themePreference: user?.themePreference ?? null,
        notes: updatedNotes,
      });
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("alerts.notes.saveError", "Not kaydedilemedi")));
      setNotes(Array.isArray(user?.notes) ? user.notes : []);
    } finally {
      setNotesSaving(false);
    }
  }

  function startAddNote() {
    setAddingNote(true);
    setNewNoteText("");
    setEditingId(null);
  }

  function cancelAdd() {
    setAddingNote(false);
    setNewNoteText("");
  }

  async function confirmAdd() {
    const trimmed = newNoteText.trim();
    if (!trimmed || notesSaving) return;
    const newNote = { id: crypto.randomUUID(), content: trimmed, createdAt: new Date().toISOString() };
    const updated = [...notes, newNote];
    setNotes(updated);
    setAddingNote(false);
    setNewNoteText("");
    await saveNotes(updated);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditingText("");
  }

  async function confirmEdit() {
    const trimmed = editingText.trim();
    if (!trimmed || notesSaving) return;
    const updated = notes.map((n) =>
      n.id === editingId ? { ...n, content: trimmed, updatedAt: new Date().toISOString() } : n,
    );
    setNotes(updated);
    setEditingId(null);
    setEditingText("");
    await saveNotes(updated);
  }

  function closePopup() {
    setExpandedNoteId(null);
    setPopupEditMode(false);
    setPopupEditText("");
  }

  function openPopupEdit(note) {
    setPopupEditText(note.content);
    setPopupEditMode(true);
  }

  async function confirmPopupEdit() {
    const trimmed = popupEditText.trim();
    if (!trimmed || notesSaving) return;
    const updated = notes.map((n) =>
      n.id === expandedNoteId ? { ...n, content: trimmed, updatedAt: new Date().toISOString() } : n,
    );
    setNotes(updated);
    closePopup();
    await saveNotes(updated);
  }

  async function deleteNote(noteId) {
    if (notesSaving) return;
    const updated = notes.filter((n) => n.id !== noteId);
    setNotes(updated);
    await saveNotes(updated);
  }

  async function togglePin(noteId) {
    if (notesSaving) return;
    const updated = notes.map((n) => (n.id === noteId ? { ...n, pinned: !n.pinned } : n));
    const isPinned = updated.find((n) => n.id === noteId)?.pinned;
    setNotes(updated);
    showToast("success", isPinned ? t("alerts.notes.pinned") : t("alerts.notes.unpinned"));
    await saveNotes(updated);
  }

  return (
    <div className="dashboard-stack alerts-management-shell">
      <section className="alerts-hero-card">
        <h2 className="alerts-hero-title">{t("alerts.eyebrow")}</h2>
        <div className="alerts-hero-actions">
          <button type="button" className="alerts-primary-action" onClick={openCreateModal}>
            <Plus size={17} strokeWidth={2.5} />
            <span>{t("alerts.create")}</span>
          </button>
          {!addingNote && !editingId && (
            <button type="button" className="alerts-primary-action alerts-primary-action--secondary" onClick={startAddNote} disabled={notesSaving}>
              <Plus size={17} strokeWidth={2.5} />
              <span>{t("alerts.notes.add", "Not Ekle")}</span>
            </button>
          )}
        </div>
      </section>

      {toast ? (
        <div key={toast.id} className={`toast-notify ${toast.type}`}>
          {toast.type === "success" ? (
            <Check size={15} strokeWidth={2.5} className="toast-notify-icon" />
          ) : (
            <X size={15} strokeWidth={2.5} className="toast-notify-icon" />
          )}
          <span>{toast.message}</span>
        </div>
      ) : null}
      {error ? <ErrorMessage message={error} /> : null}
      {loading || quotesLoading ? <LoadingSpinner label={t("alerts.loading")} /> : null}

      {!loading && !quotesLoading ? (
        <>
          <section className="alerts-layout-grid">
            <div className="alerts-left-stack">
            <section className="panel-surface alerts-panel alerts-active-panel">
              <div className="panel-head">
                <div>
                  <h3>{t("alerts.activeTitle")}</h3>
                </div>
                <span className="summary-chip">{t("alerts.activeCount", { count: activeAlerts.length })}</span>
              </div>

              {activeAlerts.length === 0 ? (
                <div className="alerts-empty-soft">
                  <strong>{t("alerts.emptyActiveTitle")}</strong>
                  <p>{t("alerts.emptyActiveDescription")}</p>
                </div>
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>{t("alerts.table.instrument")}</th>
                        <th>{t("alerts.table.condition")}</th>
                        <th>{t("alerts.table.threshold")}</th>
                        <th>{t("alerts.table.lastPrice")}</th>
                        <th>{t("alerts.table.action")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {activeAlerts.map((item) => (
                        <tr key={item.id}>
                          <td>
                            <div className="alerts-instrument-cell">
                              <strong>{formatAlertInstrument(item.instrumentCode, null, i18n.resolvedLanguage).primary}</strong>
                              {formatAlertInstrument(item.instrumentCode, null, i18n.resolvedLanguage).secondary ? (
                                <span>{formatAlertInstrument(item.instrumentCode, null, i18n.resolvedLanguage).secondary}</span>
                              ) : null}
                            </div>
                          </td>
                          <td>{formatCondition(item.conditionType, t)}</td>
                          <td className="alerts-money-cell">{formatCurrency(item.targetPrice)}</td>
                          <td className="alerts-money-cell">{formatCurrency(quotePriceMap.get(normalizeCode(item.instrumentCode)))}</td>
                          <td>
                            <div className="alerts-action-row">
                              <button
                                type="button"
                                className="icon-action-button"
                                onClick={() => openEditModal(item)}
                                title={t("alerts.editAlert")}
                              >
                                <Pencil size={14} strokeWidth={2} />
                              </button>
                              <button
                                type="button"
                                className="icon-action-button icon-action-button--danger"
                                onClick={() => handleDelete(item.id)}
                                title={t("alerts.deleteAlert")}
                              >
                                <Trash2 size={14} strokeWidth={2} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="panel-surface alerts-panel alerts-triggered-panel">
              <div className="panel-head">
                <div>
                  <h3>{t("alerts.notificationsTitle")}</h3>
                </div>
                <span className="summary-chip">{formatNumber(triggeredAlerts.length, 0)}</span>
              </div>

              {triggeredAlerts.length === 0 ? (
                <div className="alerts-empty-soft">
                  <strong>{t("alerts.emptyTriggeredTitle")}</strong>
                  <p>{t("alerts.emptyTriggeredDescription")}</p>
                </div>
              ) : (
                <div className="alerts-triggered-list">
                  {triggeredAlerts.map((item) => (
                    <article key={item.id} className="finance-notification-card">
                      <strong>{t("alerts.triggeredLabel", { symbol: formatAlertInstrument(item.instrumentCode, null, i18n.resolvedLanguage).primary })}</strong>
                      <p>{formatCondition(item.conditionType, t)} {formatCurrency(item.targetPrice)}</p>
                      <span>{formatDateTime(item.triggeredAt || item.lastUpdated)}</span>
                    </article>
                  ))}
                </div>
              )}
            </section>
            </div>

            {userId ? (
              <section className="panel-surface alerts-notes-panel">
                <div className="panel-head">
                  <div>
                    <h3>{t("alerts.notes.title", "Kişisel Notlar")}</h3>
                  </div>
                </div>

                {notes.length === 0 && !addingNote && (
                  <p className="alerts-notes-empty">
                    {t("alerts.notes.empty", "Henüz not eklenmemiş.")}
                  </p>
                )}

                {sortedNotes.length > 0 && (
                  <div className={`alerts-notes-scroll-wrap${sortedNotes.length > 3 ? " has-overflow" : ""}`}>
                    <ul className="alerts-notes-list">
                      {sortedNotes.map((note) => (
                        <li key={note.id} className={`alerts-note-item${note.pinned ? " alerts-note-item--pinned" : ""}`}>
                          {editingId === note.id ? (
                            <NoteEditForm
                              value={editingText}
                              onChange={(e) => setEditingText(e.target.value)}
                              onSave={confirmEdit}
                              onCancel={cancelEdit}
                              saving={notesSaving}
                            />
                          ) : (
                            <>
                              <div className="alerts-note-header">
                                <div className="alerts-note-header-main">
                                  {isTechnicalAnalysisNote(note) ? (
                                    <span className="alerts-note-source-badge">{formatTechnicalAnalysisNoteLabel(t)}</span>
                                  ) : null}
                                  <p className="alerts-note-content">{note.content}</p>
                                </div>
                                <div className="alerts-note-header-actions">
                                  <button
                                    type="button"
                                    className={`alerts-note-pin-btn${note.pinned ? " alerts-note-pin-btn--active" : ""}`}
                                    onClick={() => togglePin(note.id)}
                                    title={note.pinned ? t("alerts.notes.unpin") : t("alerts.notes.pin")}
                                    disabled={notesSaving}
                                  >
                                    <Pin size={12} strokeWidth={2} fill={note.pinned ? "currentColor" : "none"} />
                                  </button>
                                  <button
                                    type="button"
                                    className="alerts-note-expand-btn"
                                    onClick={() => setExpandedNoteId(note.id)}
                                    title="Görüntüle / Düzenle"
                                  >
                                    <Maximize2 size={13} strokeWidth={2} />
                                  </button>
                                </div>
                              </div>
                              <span className="alerts-note-date">
                                {formatNoteDate(note.updatedAt && note.updatedAt !== note.createdAt ? note.updatedAt : note.createdAt)}
                              </span>
                            </>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {addingNote && (
                  <NoteEditForm
                    value={newNoteText}
                    onChange={(e) => setNewNoteText(e.target.value)}
                    onSave={confirmAdd}
                    onCancel={cancelAdd}
                    saving={notesSaving}
                    placeholder={t(
                      "alerts.notes.placeholder",
                      "AKBNK bilançosunu incele\nUSD 50 olursa tekrar değerlendir\nTakip edilmesi gereken hisseler...",
                    )}
                    autoFocus
                  />
                )}
              </section>
            ) : null}
          </section>
        </>
      ) : null}

      {expandedNoteId && (() => {
        const note = notes.find((n) => n.id === expandedNoteId);
        if (!note) return null;
        return (
          <div className="modal-backdrop" role="presentation" onClick={closePopup}>
            <div className="alerts-note-popup" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>

              {/* Başlık */}
              <div className="alerts-note-popup-head">
                {popupEditMode ? (
                  <span className="alerts-note-popup-edit-label">Düzenleniyor</span>
                ) : (
                  <div className="alerts-note-popup-dates">
                    <span>{t("alerts.notes.createdAt", "Oluşturuldu")}: {formatNoteDate(note.createdAt)}</span>
                    {note.updatedAt && note.updatedAt !== note.createdAt && (
                      <span>{t("alerts.notes.updatedAt", "Güncellendi")}: {formatNoteDate(note.updatedAt)}</span>
                    )}
                  </div>
                )}
                <button type="button" className="alerts-note-popup-close" onClick={closePopup} aria-label="Kapat">
                  <X size={16} strokeWidth={2} />
                </button>
              </div>

              {/* İçerik — görüntüleme */}
              {!popupEditMode && (
                <>
                  <p className="alerts-note-popup-content">{note.content}</p>
                  <div className="alerts-note-popup-actions">
                    <button
                      type="button"
                      className={`secondary-button alerts-note-popup-btn${note.pinned ? " alerts-note-popup-btn--pinned" : ""}`}
                      disabled={notesSaving}
                      onClick={() => { togglePin(note.id); closePopup(); }}
                    >
                      <Pin size={14} strokeWidth={2} fill={note.pinned ? "currentColor" : "none"} />
                      <span>{note.pinned ? t("alerts.notes.unpin") : t("alerts.notes.pin")}</span>
                    </button>
                    <button
                      type="button"
                      className="secondary-button alerts-note-popup-btn"
                      disabled={notesSaving}
                      onClick={() => openPopupEdit(note)}
                    >
                      <Pencil size={14} strokeWidth={2} />
                      <span>Düzenle</span>
                    </button>
                    <button
                      type="button"
                      className="danger-button alerts-note-popup-btn"
                      disabled={notesSaving}
                      onClick={() => { deleteNote(note.id); closePopup(); }}
                    >
                      <Trash2 size={14} strokeWidth={2} />
                      <span>Sil</span>
                    </button>
                  </div>
                </>
              )}

              {/* İçerik — düzenleme */}
              {popupEditMode && (
                <div className="alerts-note-popup-edit-area">
                  <textarea
                    className="alerts-notes-textarea alerts-note-popup-textarea"
                    value={popupEditText}
                    onChange={(e) => setPopupEditText(e.target.value)}
                    maxLength={1000}
                    autoFocus
                  />
                  <div className="alerts-notes-form-footer">
                    <span className="alerts-notes-char-count">{popupEditText.length}/1000</span>
                    <div className="actions-row">
                      <button type="button" className="secondary-button" onClick={() => setPopupEditMode(false)} disabled={notesSaving}>
                        İptal
                      </button>
                      <button type="button" onClick={confirmPopupEdit} disabled={notesSaving || !popupEditText.trim()}>
                        {notesSaving ? "Kaydediliyor..." : "Kaydet"}
                      </button>
                    </div>
                  </div>
                </div>
              )}

            </div>
          </div>
        );
      })()}

      {isModalOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={closeCreateModal}>
          <div className="alert-create-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>

            <div className="alert-create-modal-head">
              <h3 className="alert-create-modal-title">{t("alerts.newAlarm", "Yeni Alarm")}</h3>
              <button type="button" className="alert-create-modal-close" onClick={closeCreateModal} aria-label={t("common.close")}>
                <X size={16} strokeWidth={2} />
              </button>
            </div>

            <form className="alert-create-form" onSubmit={handleSubmit}>

              <div className="alert-create-field">
                <input
                  className="alert-create-input"
                  value={symbolSearch}
                  onChange={(event) => {
                    const value = event.target.value;
                    setSymbolSearch(value);
                    setForm((current) => ({ ...current, instrumentCode: normalizeCode(value) }));
                  }}
                  placeholder={t("alerts.searchPlaceholder", "Enstrüman ara")}
                />
                {matchingQuotes.length > 0 && (
                  <div className="alert-create-suggestions">
                    <span className="alert-create-suggestions-label">{t("alerts.popular", "Popüler enstrümanlar")}</span>
                    <div className="alert-create-chips">
                      {matchingQuotes.map((item) => (
                        <button
                          key={`${item.symbol}-${item.source}`}
                          type="button"
                          className={`alert-create-chip${normalizeCode(form.instrumentCode) === normalizeCode(item.symbol) ? " active" : ""}`}
                          onClick={() => selectInstrument(item)}
                        >
                          {formatAlertInstrument(item.symbol, item.source, i18n.resolvedLanguage).primary}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              <div className="alert-create-segmented">
                <button
                  type="button"
                  className={`alert-create-segment${form.conditionType === "ABOVE" ? " active" : ""}`}
                  onClick={() => setForm((current) => ({ ...current, conditionType: "ABOVE" }))}
                >
                  {t("alerts.conditions.above", "Üstüne çıkarsa")}
                </button>
                <button
                  type="button"
                  className={`alert-create-segment${form.conditionType === "BELOW" ? " active" : ""}`}
                  onClick={() => setForm((current) => ({ ...current, conditionType: "BELOW" }))}
                >
                  {t("alerts.conditions.below", "Altına düşerse")}
                </button>
              </div>

              <div className="alert-create-price-wrap">
                <input
                  className="alert-create-input alert-create-price-input"
                  required
                  type="number"
                  step="any"
                  min="0.0001"
                  value={form.targetPrice}
                  onChange={(event) => setForm((current) => ({ ...current, targetPrice: event.target.value }))}
                  placeholder={t("alerts.thresholdValue", "Hedef fiyat")}
                />
                {selectedQuote?.currency && (
                  <span className="alert-create-currency">{selectedQuote.currency}</span>
                )}
              </div>

              <p className="alert-create-info">
                {t("alerts.lastPrice", "Son fiyat:")} {
                  !form.instrumentCode
                    ? t("alerts.noInstrumentSelected", "Henüz bir enstrüman seçmediniz")
                    : selectedQuote
                      ? formatCurrency(selectedQuote.price, selectedQuote.currency || "TRY")
                      : t("alerts.noData", "Veri yok")
                }
              </p>

              <div className="alert-create-footer">
                <button type="button" className="secondary-button" onClick={closeCreateModal}>
                  {t("common.cancel", "İptal")}
                </button>
                <button type="submit" className="alert-create-submit" disabled={saving || !form.instrumentCode}>
                  {saving ? t("alerts.creating", "Oluşturuluyor...") : t("alerts.create", "Alarm Oluştur")}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {editingAlert ? (() => {
        const editSelectedQuote = quotes.find(
          (item) => normalizeCode(item.symbol) === normalizeCode(editForm.instrumentCode),
        ) || null;
        const editMatchingQuotes = (() => {
          const query = editSymbolSearch.trim().toLowerCase();
          if (!query) return quotes.slice(0, 8);
          return quotes.filter((item) =>
            item.symbol?.toLowerCase().includes(query) || item.displayName?.toLowerCase().includes(query),
          ).slice(0, 8);
        })();

        return (
          <div className="modal-backdrop" role="presentation" onClick={closeEditModal}>
            <div className="auth-modal alerts-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
              <div className="portfolio-action-modal-head">
                <div>
                  <p className="eyebrow">{t("alerts.editModalEyebrow")}</p>
                  <h3>{t("alerts.editModalTitle")}</h3>
                </div>
                <button type="button" className="secondary-button" onClick={closeEditModal}>
                  {t("common.close")}
                </button>
              </div>

              <form className="instrument-action-form" onSubmit={handleUpdate}>
                <label className="portfolio-field">
                  <span>{t("alerts.searchInstrument")}</span>
                  <input
                    value={editSymbolSearch}
                    onChange={(event) => {
                      const value = event.target.value;
                      setEditSymbolSearch(value);
                      setEditForm((current) => ({ ...current, instrumentCode: normalizeCode(value) }));
                    }}
                    placeholder={t("alerts.searchPlaceholder")}
                  />
                </label>

                <div className="alerts-picker-grid">
                  {editMatchingQuotes.map((item) => (
                    <button
                      key={`${item.symbol}-${item.source}`}
                      type="button"
                      className={`analysis-picker-card${normalizeCode(editForm.instrumentCode) === normalizeCode(item.symbol) ? " active" : ""}`}
                      onClick={() => {
                        const displaySymbol = formatAlertInstrument(item.symbol, item.source, i18n.resolvedLanguage).primary;
                        setEditForm((current) => ({
                          ...current,
                          instrumentCode: item.symbol || "",
                          targetPrice: current.targetPrice || String(item.price) || "",
                        }));
                        setEditSymbolSearch(displaySymbol === "-" ? item.symbol || "" : displaySymbol);
                      }}
                    >
                      <strong>{formatAlertInstrument(item.symbol, item.source, i18n.resolvedLanguage).primary}</strong>
                      <span>{formatAlertInstrument(item.symbol, item.source, i18n.resolvedLanguage).secondary || item.displayName || "-"}</span>
                    </button>
                  ))}
                </div>

                <div className="instrument-action-grid">
                  <label className="portfolio-field">
                    <span>{t("alerts.table.condition")}</span>
                    <select
                      value={editForm.conditionType}
                      onChange={(event) => setEditForm((current) => ({ ...current, conditionType: event.target.value }))}
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
                      value={editForm.targetPrice}
                      onChange={(event) => setEditForm((current) => ({ ...current, targetPrice: event.target.value }))}
                    />
                  </label>
                </div>

                <div className="alerts-selected-quote">
                  <span>{t("alerts.selectedSymbol")}</span>
                  <strong>{formatAlertInstrument(editForm.instrumentCode, editSelectedQuote?.source, i18n.resolvedLanguage).primary}</strong>
                  <p>{t("alerts.lastPrice")} {editSelectedQuote ? formatCurrency(editSelectedQuote.price, editSelectedQuote.currency || "TRY") : t("alerts.noData")}</p>
                </div>

                <div className="instrument-action-footer">
                  <button type="submit" disabled={editSaving || !editForm.instrumentCode}>
                    {editSaving ? t("alerts.updating") : t("common.save")}
                  </button>
                </div>
              </form>
            </div>
          </div>
        );
      })() : null}
    </div>
  );
}

function NoteEditForm({ value, onChange, onSave, onCancel, saving, placeholder, autoFocus }) {
  return (
    <div className="alerts-note-edit-form">
      <textarea
        className="alerts-notes-textarea"
        value={value}
        onChange={onChange}
        maxLength={1000}
        rows={3}
        placeholder={placeholder ?? ""}
        autoFocus={autoFocus}
      />
      <div className="alerts-notes-form-footer">
        <span className="alerts-notes-char-count">{value.length}/1000</span>
        <div className="actions-row">
          <button type="button" className="secondary-button" onClick={onCancel} disabled={saving}>
            İptal
          </button>
          <button type="button" onClick={onSave} disabled={saving || !value.trim()}>
            {saving ? "Kaydediliyor..." : "Kaydet"}
          </button>
        </div>
      </div>
    </div>
  );
}

function formatNoteDate(isoString) {
  if (!isoString) return "";
  try {
    return new Date(isoString).toLocaleDateString("tr-TR", {
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  } catch {
    return "";
  }
}

function isTechnicalAnalysisNote(note) {
  if (note?.source === "technical-analysis") {
    return true;
  }
  return /Teknik Analiz/i.test(String(note?.content || ""));
}

function formatTechnicalAnalysisNoteLabel(t) {
  return t("alerts.notes.technicalAnalysisNote", {
    defaultValue: "Teknik analiz notu",
  });
}

function formatAlertInstrument(instrumentCode, source, locale = "tr") {
  const raw = String(instrumentCode || "").trim();
  if (!raw) {
    return { primary: "-", secondary: source || "-" };
  }

  const parts = raw.split(":").filter(Boolean);
  if (parts.length >= 2) {
    const currency = parts[1];
    return {
      primary: currency.toUpperCase(),
      secondary: formatFxSecondaryLabel(currency, locale),
    };
  }

  const compactFxMatch = raw.toUpperCase().match(/^(TCMB|AKBANK)([A-Z]{3})(BUY|SELL)$/);
  if (compactFxMatch) {
    return {
      primary: compactFxMatch[2],
      secondary: formatFxSecondaryLabel(compactFxMatch[2], locale),
    };
  }

  return {
    primary: raw.toUpperCase(),
    secondary: source || "-",
  };
}

function formatFxSecondaryLabel(currencyCode, locale) {
  const code = String(currencyCode || "").trim().toUpperCase();
  const label = getFxCodeLabel(code, locale);
  return label && label !== code ? label : "";
}
function normalizeCode(value) {
  if (value == null) return "";
  const rawValue = String(value).trim();
  if (rawValue.toUpperCase().startsWith("TCMB:")) return rawValue.toUpperCase();
  return rawValue.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function formatCondition(value, t) {
  return { ABOVE: t("alerts.conditions.above"), BELOW: t("alerts.conditions.below") }[value] ?? value ?? "-";
}
