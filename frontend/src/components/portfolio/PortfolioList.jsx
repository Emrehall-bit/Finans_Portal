import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import EmptyState from "../common/EmptyState";
import { formatDateTime } from "../../utils/formatters";

export default function PortfolioList({ portfolios = [] }) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className="card">
      <h3>{t("portfolio.listTitle")}</h3>
      {portfolios.length === 0 ? (
        <EmptyState title={t("portfolio.emptyListTitle")} description={t("portfolio.emptyListDescription")} />
      ) : null}
      <div className="list">
        {portfolios.map((portfolio) => (
          <button
            key={portfolio.portfolioId}
            className="list-item"
            onClick={() => navigate(`/portfolio/${portfolio.portfolioId}`)}
          >

            <strong style={{ color: "navy" }}>{portfolio.portfolioName}</strong>
            <span className="muted">{formatDateTime(portfolio.createdAt)}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
