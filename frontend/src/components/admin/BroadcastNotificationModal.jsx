import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { sendBroadcastNotification } from "../../api/notificationApi";
import { extractErrorMessage } from "../../api/responseUtils";
import ErrorMessage from "../common/ErrorMessage";

const NOTIFICATION_TYPES = ["ANNOUNCEMENT", "SYSTEM", "SECURITY"];

const INITIAL_FORM = {
  title: "",
  message: "",
  type: "ANNOUNCEMENT",
};

export default function BroadcastNotificationModal({ isOpen, onClose }) {
  const { t } = useTranslation();
  const [form, setForm] = useState(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const typeOptions = useMemo(
    () =>
      NOTIFICATION_TYPES.map((type) => ({
        value: type,
        label: t(`layout.notificationTypes.${type}`),
      })),
    [t],
  );

  useEffect(() => {
    if (!isOpen) {
      setForm(INITIAL_FORM);
      setSubmitting(false);
      setError("");
      setSuccess("");
    }
  }, [isOpen]);

  if (!isOpen) return null;

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setSuccess("");

    try {
      await sendBroadcastNotification({
        title: form.title.trim(),
        message: form.message.trim(),
        type: form.type,
      });
      setSuccess(t("admin.broadcast.success"));
      setForm(INITIAL_FORM);
    } catch (submitError) {
      setError(extractErrorMessage(submitError, t("admin.broadcast.error")));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="auth-modal instrument-action-modal"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="instrument-action-modal-head">
          <div>
            <p className="eyebrow">{t("admin.broadcast.eyebrow")}</p>
            <h3>{t("admin.broadcast.title")}</h3>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            {t("common.close")}
          </button>
        </div>

        {error ? <ErrorMessage message={error} /> : null}
        {success ? <div className="status-box success">{success}</div> : null}

        <form className="instrument-action-form" onSubmit={handleSubmit}>
          <label className="portfolio-field">
            <span>{t("admin.broadcast.titleLabel")}</span>
            <input
              type="text"
              required
              maxLength={255}
              value={form.title}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
              placeholder={t("admin.broadcast.titlePlaceholder")}
              disabled={submitting}
            />
          </label>

          <label className="portfolio-field">
            <span>{t("admin.broadcast.messageLabel")}</span>
            <textarea
              required
              rows="5"
              value={form.message}
              onChange={(event) => setForm((current) => ({ ...current, message: event.target.value }))}
              placeholder={t("admin.broadcast.messagePlaceholder")}
              disabled={submitting}
              className="admin-users-textarea"
            />
          </label>

          <label className="portfolio-field">
            <span>{t("admin.broadcast.typeLabel")}</span>
            <select
              value={form.type}
              onChange={(event) => setForm((current) => ({ ...current, type: event.target.value }))}
              disabled={submitting}
            >
              {typeOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <div className="instrument-action-footer">
            <button type="submit" disabled={submitting || !form.title.trim() || !form.message.trim()}>
              {submitting ? t("admin.broadcast.submitting") : t("admin.broadcast.submit")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
