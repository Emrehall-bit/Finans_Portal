import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { formatInstrumentValue } from "../../utils/formatters";
import { formatMarketChange } from "./marketDetailUtils";

export default function InstrumentHeader({
  symbol,
  displayName,
  price,
  changeRate,
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
        <div className="idh-market-group">
          <div className="idh-identity">
            <div className="idh-headline">
              <h1 className="idh-symbol">{symbol || "-"}</h1>
            </div>
            <p className="idh-displayname">{displayName || t("instrumentDetail.loadingSubtitle")}</p>
          </div>

          <div className="idh-price-area">
            <strong className="idh-price">{price != null ? formatInstrumentValue(price, { instrumentType, currency: "TRY" }) : "-"}</strong>
            {changeRate != null ? (
              <span className={`idh-change-badge ${isPositive ? "positive" : "negative"}`}>
                {formatMarketChange(changeRate)}
              </span>
            ) : null}
          </div>
        </div>

        <div className="idh-controls">
          <button type="button" className="secondary-button" onClick={onFavoriteToggle} disabled={favoriteBusy}>
            {favoriteBusy ? t("instrumentDetail.processing") : isFavorite ? t("instrumentDetail.removeFavorite") : t("instrumentDetail.addFavorite")}
          </button>
          <button type="button" className="secondary-button" onClick={onOpenAlert}>
            {t("instrumentDetail.createAlert")}
          </button>
          {onOpenPortfolio ? (
            <button type="button" className="secondary-button" onClick={onOpenPortfolio}>
              {t("instrumentDetail.addToPortfolio")}
            </button>
          ) : null}
        </div>
      </div>
    </section>
  );
}
