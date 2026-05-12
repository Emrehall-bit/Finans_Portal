import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { sendNotificationToUser } from "../../api/notificationApi";
import { extractErrorMessage } from "../../api/responseUtils";
import ErrorMessage from "../common/ErrorMessage";

const DEFAULT_FORM = {
  title: "",
  message: "",
  type: "ADMIN_MESSAGE",
};

export default function SendUserNotificationModal({ isOpen, onClose, user, onSuccess }) {
  const { t } = useTranslation();
  const [form, setForm] = useState(DEFAULT_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  useEffect(() => {
    if (!isOpen) {
      setForm(DEFAULT_FORM);
      setSubmitting(false);
      setError("");
      setSuccessMessage("");
    }
  }, [isOpen]);

  if (!isOpen || !user) {
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setSuccessMessage("");

    try {
      const notification = await sendNotificationToUser(user.id, form);
      setSuccessMessage(t("admin.users.notification.success"));
      onSuccess?.(notification);
    } catch (submitError) {
      setError(extractErrorMessage(submitError, t("admin.users.notification.error")));
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
            <p className="eyebrow">{t("admin.users.notification.eyebrow")}</p>
            <h3>{user.fullName || user.email}</h3>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            {t("common.close")}
          </button>
        </div>

        {error ? <ErrorMessage message={error} /> : null}
        {successMessage ? <div className="status-box success">{successMessage}</div> : null}

        <form className="instrument-action-form" onSubmit={handleSubmit}>
          <label className="portfolio-field">
            <span>{t("admin.users.notification.titleLabel")}</span>
            <input
              type="text"
              required
              value={form.title}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            />
          </label>

          <label className="portfolio-field">
            <span>{t("admin.users.notification.messageLabel")}</span>
            <textarea
              required
              rows="5"
              value={form.message}
              onChange={(event) => setForm((current) => ({ ...current, message: event.target.value }))}
              className="admin-users-textarea"
            />
          </label>

          <label className="portfolio-field">
            <span>{t("admin.users.notification.typeLabel")}</span>
            <select
              value={form.type}
              onChange={(event) => setForm((current) => ({ ...current, type: event.target.value }))}
            >
              <option value="ADMIN_MESSAGE">ADMIN_MESSAGE</option>
              <option value="SECURITY">SECURITY</option>
              <option value="ANNOUNCEMENT">ANNOUNCEMENT</option>
            </select>
          </label>

          <div className="instrument-action-footer">
            <button type="submit" disabled={submitting}>
              {submitting ? t("admin.users.notification.submitting") : t("admin.users.notification.submit")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
