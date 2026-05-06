import { useEffect, useState } from "react";
import { createAlert } from "../../api/alertApi";
import { extractErrorMessage } from "../../api/responseUtils";
import ErrorMessage from "../common/ErrorMessage";

export default function CreateAlertModal({ isOpen, onClose, symbol, currentPrice, userId, onSuccess }) {
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
      setError(extractErrorMessage(err, "Alarm olusturulamadi."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div className="auth-modal instrument-action-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <div className="instrument-action-modal-head">
          <div>
            <p className="eyebrow">Alarm olustur</p>
            <h3>{symbol}</h3>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            Kapat
          </button>
        </div>

        {error ? <ErrorMessage message={error} /> : null}

        <form className="instrument-action-form" onSubmit={handleSubmit}>
          <label className="portfolio-field">
            <span>Kosul</span>
            <select
              value={form.conditionType}
              onChange={(event) => setForm((current) => ({ ...current, conditionType: event.target.value }))}
            >
              <option value="GREATER_THAN">Fiyat bunun ustune cikinca</option>
              <option value="LESS_THAN">Fiyat bunun altina inince</option>
            </select>
          </label>

          <label className="portfolio-field">
            <span>Hedef fiyat</span>
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
              {submitting ? "Olusturuluyor..." : "Alarm olustur"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
