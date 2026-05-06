import { Link } from "react-router-dom";
import { formatNumber } from "../../utils/formatters";
import { formatMarketChange } from "./marketDetailUtils";

export default function InstrumentHeader({
  symbol,
  displayName,
  price,
  changeRate,
  currency,
  source,
  instrumentType,
  isFavorite,
  favoriteBusy,
  onFavoriteToggle,
  onOpenAlert,
  onOpenPortfolio,
}) {
  const changeNumeric = Number(changeRate);
  const isPositive = Number.isFinite(changeNumeric) ? changeNumeric >= 0 : false;

  return (
    <section className="panel-surface instrument-detail-hero">
      <div className="instrument-detail-hero-main">
        <Link to="/markets" className="secondary-button market-detail-back">
          Piyasa ekranina don
        </Link>
        <div className="instrument-detail-heading">
          <div>
            <p className="eyebrow">Enstruman Detayi</p>
            <h1>{symbol || "-"}</h1>
            <p className="instrument-detail-subtitle">{displayName || "Piyasa verisi yukleniyor"}</p>
          </div>
          <div className="instrument-detail-price-block">
            <strong>{formatNumber(price)}</strong>
            <span className={isPositive ? "terminal-pill positive" : "terminal-pill negative"}>
              {formatMarketChange(changeRate)}
            </span>
          </div>
        </div>
      </div>

      <div className="instrument-detail-hero-side">
        <div className="instrument-detail-action-row">
          <button type="button" className="secondary-button" onClick={onFavoriteToggle} disabled={favoriteBusy}>
            {favoriteBusy ? "Isleniyor..." : isFavorite ? "Favoriden cikar" : "Favoriye ekle"}
          </button>
          <button type="button" className="secondary-button" onClick={onOpenAlert}>
            Alarm olustur
          </button>
          <button type="button" onClick={onOpenPortfolio}>
            Portfoye ekle
          </button>
        </div>

        <div className="instrument-detail-hero-meta">
          <div className="terminal-price-card">
            <span>Kaynak</span>
            <strong>{source || "-"}</strong>
          </div>
          <div className="terminal-price-card">
            <span>Tip</span>
            <strong>{instrumentType || "-"}</strong>
          </div>
          <div className="terminal-price-card">
            <span>Para birimi</span>
            <strong>{currency || "-"}</strong>
          </div>
        </div>
      </div>
    </section>
  );
}
