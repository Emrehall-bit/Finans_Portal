import { useEffect, useState } from "react";
import { createPortfolio, getUserPortfolios } from "../api/portfolioApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import PortfolioList from "../components/portfolio/PortfolioList";
import useToast from "../hooks/useToast";

export default function PortfolioPage() {
  const { userId } = useAuth();
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast, showToast } = useToast();
  const [newPortfolio, setNewPortfolio] = useState({ portfolioName: "", visibilityStatus: "PRIVATE" });

  async function loadPortfolios() {
    try {
      setLoading(true);
      setError("");
      const list = await getUserPortfolios(userId);
      setPortfolios(list);
    } catch (err) {
      setError(extractErrorMessage(err, "Portfolios could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (userId) {
      loadPortfolios();
    }
  }, [userId]);

  async function handleCreatePortfolio(e) {
    e.preventDefault();
    try {
      await createPortfolio(userId, newPortfolio);
      showToast("success", "Portfolio created successfully.");
      setNewPortfolio({ portfolioName: "", visibilityStatus: "PRIVATE" });
      await loadPortfolios();
    } catch (err) {
      setError(extractErrorMessage(err, "Portfolio create failed."));
    }
  }

  return (
    <div>
      <PageHeader title="Portfolio" description={`Portfolio list for user id: ${userId}`} />
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {loading ? <LoadingSpinner label="Loading portfolios..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      <div className="split-grid">
        <div>
          <PortfolioList portfolios={portfolios} />
        </div>
        <div>
          <form className="card form-grid" onSubmit={handleCreatePortfolio}>
            <h3>Create Portfolio</h3>
            <input
              required
              placeholder="Portfolio name"
              value={newPortfolio.portfolioName}
              onChange={(e) => setNewPortfolio((p) => ({ ...p, portfolioName: e.target.value }))}
            />
            <select
              required
              value={newPortfolio.visibilityStatus}
              onChange={(e) => setNewPortfolio((p) => ({ ...p, visibilityStatus: e.target.value }))}
            >
              <option value="PRIVATE">PRIVATE</option>
              <option value="PUBLIC">PUBLIC</option>
            </select>
            <button type="submit">Create</button>
          </form>
        </div>
      </div>
    </div>
  );
}
