import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { createPortfolioHolding, getUserPortfolios } from "../../api/portfolioApi";
import { extractErrorMessage } from "../../api/responseUtils";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";

export default function AddToPortfolioModal({ isOpen, onClose, symbol, currentPrice, userId, onSuccess }) {
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    portfolioId: "",
    quantity: "",
    buyPrice: currentPrice ?? "",
  });

  useEffect(() => {
    if (!isOpen || !userId) {
      return;
    }

    let active = true;

    async function loadPortfolios() {
      try {
        setLoading(true);
        setError("");
        const rows = await getUserPortfolios(userId);
        if (!active) {
          return;
        }

        setPortfolios(rows);
        setForm((current) => ({
          ...current,
          portfolioId: current.portfolioId || rows[0]?.portfolioId || "",
          buyPrice: current.buyPrice || currentPrice || "",
        }));
      } catch (err) {
        if (active) {
          setError(extractErrorMessage(err, "Portfoyler yuklenemedi."));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadPortfolios();

    return () => {
      active = false;
    };
  }, [isOpen, userId, currentPrice]);

  useEffect(() => {
    if (!isOpen) {
      setForm({ portfolioId: "", quantity: "", buyPrice: currentPrice ?? "" });
      setError("");
      setSubmitting(false);
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
      await createPortfolioHolding(form.portfolioId, {
        instrumentCode: symbol,
        quantity: Number(form.quantity),
        buyPrice: Number(form.buyPrice),
      });
      onSuccess?.();
      onClose();
    } catch (err) {
      setError(extractErrorMessage(err, "Enstruman portfoye eklenemedi."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div className="auth-modal instrument-action-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <div className="instrument-action-modal-head">
          <div>
            <p className="eyebrow">Portfoye ekle</p>
            <h3>{symbol}</h3>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            Kapat
          </button>
        </div>

        {loading ? <LoadingSpinner label="Portfoyler yukleniyor..." /> : null}
        {error ? <ErrorMessage message={error} /> : null}

        {!loading && portfolios.length === 0 ? (
          <EmptyState
            title="Portfoy bulunamadi"
            description="Bu enstrumani eklemek icin once en az bir portfoy olustur."
          />
        ) : null}

        {!loading && portfolios.length === 0 ? (
          <Link to="/portfolio" className="secondary-button" onClick={onClose}>
            Portfoy sayfasina git
          </Link>
        ) : null}

        {!loading && portfolios.length > 0 ? (
          <form className="instrument-action-form" onSubmit={handleSubmit}>
            <label className="portfolio-field">
              <span>Portfoy</span>
              <select
                required
                value={form.portfolioId}
                onChange={(event) => setForm((current) => ({ ...current, portfolioId: event.target.value }))}
              >
                {portfolios.map((portfolio) => (
                  <option key={portfolio.portfolioId} value={portfolio.portfolioId}>
                    {portfolio.portfolioName}
                  </option>
                ))}
              </select>
            </label>

            <div className="instrument-action-grid">
              <label className="portfolio-field">
                <span>Miktar</span>
                <input
                  type="number"
                  step="any"
                  min="0.0001"
                  required
                  value={form.quantity}
                  onChange={(event) => setForm((current) => ({ ...current, quantity: event.target.value }))}
                />
              </label>
              <label className="portfolio-field">
                <span>Alis fiyati</span>
                <input
                  type="number"
                  step="any"
                  min="0.0001"
                  required
                  value={form.buyPrice}
                  onChange={(event) => setForm((current) => ({ ...current, buyPrice: event.target.value }))}
                />
              </label>
            </div>

            <div className="instrument-action-footer">
              <button type="submit" disabled={submitting}>
                {submitting ? "Ekleniyor..." : "Portfoye ekle"}
              </button>
            </div>
          </form>
        ) : null}
      </div>
    </div>
  );
}
