import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { createAlert } from "../../api/alertApi";
import { extractErrorMessage } from "../../api/responseUtils";
import ErrorMessage from "../common/ErrorMessage";

export default function CreateAlertModal({ isOpen, onClose, symbol, currentPrice, userId, onSuccess }) {
  const { t } = useTranslation();
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    conditionType: "GREATER_THAN",
    targetPrice: currentPrice ?? "",
  });

  useEffect(() => {
    if (!isOpen) {
      setError("");
      setSubmitting(false);
      setForm({
        conditionType: "GREATER_THAN",
        targetPrice: currentPrice ?? "",
      });
    }
  }, [isOpen, currentPrice]);

  if (!isOpen) {
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      setSubmitting(true);
      setError("");
      await createAlert(userId, {
        instrumentCode: symbol,
        conditionType: form.conditionType,
        targetPrice: Number(form.targetPrice),
      });
      onSuccess?.();
      onClose();
    } catch (err) {
      setError(extractErrorMessage(err, t("marketDetail.createAlert.error")));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div className="auth-modal instrument-action-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <div className="instrument-action-modal-head">
          <div>
            <p className="eyebrow">{t("marketDetail.createAlert.eyebrow")}</p>
            <h3>{symbol}</h3>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            {t("common.close")}
          </button>
        </div>

        {error ? <ErrorMessage message={error} /> : null}

        <form className="instrument-action-form" onSubmit={handleSubmit}>
          <label className="portfolio-field">
            <span>{t("marketDetail.createAlert.condition")}</span>
            <select
              value={form.conditionType}
              onChange={(event) => setForm((current) => ({ ...current, conditionType: event.target.value }))}
            >
              <option value="GREATER_THAN">{t("marketDetail.createAlert.above")}</option>
              <option value="LESS_THAN">{t("marketDetail.createAlert.below")}</option>
            </select>
          </label>

          <label className="portfolio-field">
            <span>{t("marketDetail.createAlert.targetPrice")}</span>
            <input
              type="number"
              step="any"
              min="0.0001"
              required
              value={form.targetPrice}
              onChange={(event) => setForm((current) => ({ ...current, targetPrice: event.target.value }))}
            />
          </label>

          <div className="instrument-action-footer">
            <button type="submit" disabled={submitting}>
              {submitting ? t("marketDetail.createAlert.submitting") : t("marketDetail.createAlert.submit")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
