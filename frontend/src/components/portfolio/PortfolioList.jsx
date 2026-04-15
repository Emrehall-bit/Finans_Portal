import { useNavigate } from "react-router-dom";
import EmptyState from "../common/EmptyState";
import { formatDateTime } from "../../utils/formatters";

export default function PortfolioList({ portfolios = [] }) {
  const navigate = useNavigate();

  return (
    <div className="card">
      <h3>Portfolios</h3>
      {portfolios.length === 0 ? (
        <EmptyState title="No portfolios" description="Create a portfolio to get started." />
      ) : null}
      <div className="list">
        {portfolios.map((portfolio) => (
          <button
            key={portfolio.portfolioId}
            className="list-item"
            onClick={() => navigate(`/portfolio/${portfolio.portfolioId}`)}
          >
            {/* İsim rengi tekrar lacivert (navy) yapıldı */}
            <strong style={{ color: "navy" }}>{portfolio.portfolioName}</strong>
            <span className="muted">{portfolio.visibilityStatus || "N/A"}</span>
            <span className="muted">{formatDateTime(portfolio.createdAt)}</span>
          </button>
        ))}
      </div>
    </div>
  );
}