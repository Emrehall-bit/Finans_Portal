import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { sendBroadcastNotification } from "../../api/notificationApi";
import { extractErrorMessage } from "../../api/responseUtils";

const NOTIFICATION_TYPES = ["ANNOUNCEMENT", "SYSTEM", "SECURITY"];

const INITIAL_FORM = {
  title: "",
  message: "",
  type: "ANNOUNCEMENT",
};

export default function BroadcastNotificationCard() {
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

  function handleChange(field, value) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }));
  }

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
    <section className="admin-broadcast-card panel-surface">
      <div className="admin-broadcast-copy">
        <p className="eyebrow">{t("admin.broadcast.eyebrow")}</p>
        <h3>{t("admin.broadcast.title")}</h3>
        <p>{t("admin.broadcast.description")}</p>
      </div>

      <form className="admin-broadcast-form" onSubmit={handleSubmit}>
        <label className="modal-form-field">
          <span>{t("admin.broadcast.titleLabel")}</span>
          <input
            type="text"
            value={form.title}
            maxLength={255}
            onChange={(event) => handleChange("title", event.target.value)}
            placeholder={t("admin.broadcast.titlePlaceholder")}
            disabled={submitting}
          />
        </label>

        <label className="modal-form-field">
          <span>{t("admin.broadcast.messageLabel")}</span>
          <textarea
            rows="4"
            value={form.message}
            onChange={(event) => handleChange("message", event.target.value)}
            placeholder={t("admin.broadcast.messagePlaceholder")}
            disabled={submitting}
          />
        </label>

        <label className="modal-form-field">
          <span>{t("admin.broadcast.typeLabel")}</span>
          <select value={form.type} onChange={(event) => handleChange("type", event.target.value)} disabled={submitting}>
            {typeOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        {error ? <p className="form-feedback error">{error}</p> : null}
        {success ? <p className="form-feedback success">{success}</p> : null}

        <div className="admin-broadcast-actions">
          <button type="submit" className="admin-console-button" disabled={submitting}>
            <span className="admin-console-button-glow" />
            <span>{submitting ? t("admin.broadcast.submitting") : t("admin.broadcast.submit")}</span>
          </button>
        </div>
      </form>
    </section>
  );
}
