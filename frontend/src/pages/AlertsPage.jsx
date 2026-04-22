import { useEffect, useState } from "react";
import { cancelAlert, createAlert, getUserAlerts } from "../api/alertApi";
import { extractErrorMessage } from "../api/responseUtils";
import { useAuth } from "../auth/AuthContext";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import AlertsTable from "../components/alerts/AlertsTable";
import useToast from "../hooks/useToast";

export default function AlertsPage() {
  const { userId } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast, showToast } = useToast();
  const [form, setForm] = useState({
    instrumentCode: "",
    conditionType: "GREATER_THAN",
    targetPrice: "",
  });

  async function loadData() {
    try {
      setLoading(true);
      setError("");
      setRows(await getUserAlerts(userId));
    } catch (err) {
      setError(extractErrorMessage(err, "Alerts could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (userId) {
      loadData();
    }
  }, [userId]);

  async function handleSubmit(e) {
    e.preventDefault();
    try {
      await createAlert(userId, {
        instrumentCode: form.instrumentCode,
        conditionType: form.conditionType,
        targetPrice: Number(form.targetPrice),
      });
      setForm({ instrumentCode: "", conditionType: "GREATER_THAN", targetPrice: "" });
      showToast("success", "Alert created.");
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, "Could not create alert."));
    }
  }

  async function handleCancel(alertId) {
    try {
      await cancelAlert(userId, alertId);
      showToast("success", "Alert cancelled.");
      await loadData();
    } catch (err) {
      setError(extractErrorMessage(err, "Could not cancel alert."));
    }
  }

  return (
    <div>
      <PageHeader title="Alerts" description={`User id: ${userId}`} />
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      <form className="card form-inline" onSubmit={handleSubmit}>
        <input
          required
          value={form.instrumentCode}
          onChange={(e) => setForm((p) => ({ ...p, instrumentCode: e.target.value }))}
          placeholder="Instrument code"
        />
        <select
          value={form.conditionType}
          onChange={(e) => setForm((p) => ({ ...p, conditionType: e.target.value }))}
        >
          <option value="GREATER_THAN">GREATER_THAN</option>
          <option value="LESS_THAN">LESS_THAN</option>
        </select>
        <input
          required
          type="number"
          step="any"
          value={form.targetPrice}
          onChange={(e) => setForm((p) => ({ ...p, targetPrice: e.target.value }))}
          placeholder="Target price"
        />
        <button type="submit">Create</button>
      </form>
      {loading ? <LoadingSpinner label="Loading alerts..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {!loading && !error && rows.length === 0 ? (
        <EmptyState title="No alerts found" description="Create your first alert to start monitoring." />
      ) : null}
      {!loading && !error && rows.length > 0 ? <AlertsTable rows={rows} onCancel={handleCancel} /> : null}
    </div>
  );
}
