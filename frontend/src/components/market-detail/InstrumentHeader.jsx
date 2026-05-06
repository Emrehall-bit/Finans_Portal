import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
  const changeNumeric = Number(changeRate);
  const isPositive = Number.isFinite(changeNumeric) ? changeNumeric >= 0 : false;

  return (
    <section className="panel-surface instrument-detail-hero">
      <div className="instrument-detail-hero-main">
        <Link to="/markets" className="secondary-button market-detail-back">
          {t("instrumentDetail.back")}
        </Link>
        <div className="instrument-detail-heading">
          <div>
            <p className="eyebrow">{t("instrumentDetail.eyebrow")}</p>
            <h1>{symbol || "-"}</h1>
            <p className="instrument-detail-subtitle">{displayName || t("instrumentDetail.loadingSubtitle")}</p>
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
            {favoriteBusy ? t("instrumentDetail.processing") : isFavorite ? t("instrumentDetail.removeFavorite") : t("instrumentDetail.addFavorite")}
          </button>
          <button type="button" className="secondary-button" onClick={onOpenAlert}>
            {t("instrumentDetail.createAlert")}
          </button>
          <button type="button" onClick={onOpenPortfolio}>
            {t("instrumentDetail.addToPortfolio")}
          </button>
        </div>

        <div className="instrument-detail-hero-meta">
          <div className="terminal-price-card">
            <span>{t("instrumentDetail.source")}</span>
            <strong>{source || "-"}</strong>
          </div>
          <div className="terminal-price-card">
            <span>{t("instrumentDetail.type")}</span>
            <strong>{instrumentType || "-"}</strong>
          </div>
          <div className="terminal-price-card">
            <span>{t("instrumentDetail.currency")}</span>
            <strong>{currency || "-"}</strong>
          </div>
        </div>
      </div>
    </section>
  );
}
