import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { formatNumber } from "../../utils/formatters";
import { formatMarketChange } from "./marketDetailUtils";
import { CurrencyToggle } from "../../currency/CurrencyContext";

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
      <Link to="/markets" className="idh-back">
        ← {t("instrumentDetail.back")}
      </Link>

      <div className="idh-body">
        <div className="idh-identity">
          <p className="idh-eyebrow">{t("instrumentDetail.eyebrow")}</p>
          <div className="idh-headline">
            <h1 className="idh-symbol">{symbol || "-"}</h1>
            {changeRate != null ? (
              <span className={`idh-change-badge ${isPositive ? "positive" : "negative"}`}>
                {isPositive ? "+" : ""}{formatMarketChange(changeRate)}
              </span>
            ) : null}
          </div>
          <p className="idh-displayname">{displayName || t("instrumentDetail.loadingSubtitle")}</p>
        </div>

        <div className="idh-price-area">
          <strong className="idh-price">{price != null ? formatNumber(price) : "—"}</strong>
          <div className="idh-meta-tags">
            {source ? <span className="idh-tag">{source}</span> : null}
            {instrumentType ? <span className="idh-tag">{instrumentType}</span> : null}
            {currency ? <span className="idh-tag">{currency}</span> : null}
          </div>
        </div>

        <div className="idh-controls">
          <CurrencyToggle />
          <div className="idh-actions">
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
        </div>
      </div>
    </section>
  );
}
