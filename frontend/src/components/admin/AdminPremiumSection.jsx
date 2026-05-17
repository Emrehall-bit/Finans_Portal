import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  adminApprovePremium,
  adminRejectPremium,
  getAdminPendingPremium,
} from "../../api/premiumApi";
import { extractErrorMessage } from "../../api/responseUtils";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatDateTime } from "../../utils/formatters";

const STATUS_LABELS = {
  PREMIUM_REQUESTED: "premium.status.PREMIUM_REQUESTED",
  PAYMENT_PENDING: "premium.status.PAYMENT_PENDING",
  ACTIVE: "premium.status.ACTIVE",
  PAYMENT_FAILED: "premium.status.PAYMENT_FAILED",
  CANCELLED: "premium.status.CANCELLED",
};

export default function AdminPremiumSection() {
  const { t } = useTranslation();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [processingId, setProcessingId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getAdminPendingPremium();
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(extractErrorMessage(err, t("admin.premium.loadError")));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { load(); }, [load]);

  async function handleApprove(subscriptionId) {
    setProcessingId(subscriptionId);
    try {
      await adminApprovePremium(subscriptionId);
      await load();
    } catch (err) {
      setError(extractErrorMessage(err, t("admin.premium.approveError")));
    } finally {
      setProcessingId(null);
    }
  }

  async function handleReject(subscriptionId) {
    setProcessingId(subscriptionId);
    try {
      await adminRejectPremium(subscriptionId);
      await load();
    } catch (err) {
      setError(extractErrorMessage(err, t("admin.premium.rejectError")));
    } finally {
      setProcessingId(null);
    }
  }

  return (
    <section className="admin-audit-section panel-surface">
      <div className="admin-audit-header">
        <div className="admin-audit-copy">
          <p className="eyebrow">{t("admin.premium.eyebrow")}</p>
          <h3>{t("admin.premium.title")}</h3>
          <p>{t("admin.premium.description")}</p>
        </div>
        <div className="admin-audit-toolbar">
          <button
            type="button"
            className="admin-console-button admin-users-refresh"
            onClick={load}
            disabled={loading}
          >
            <span className="admin-console-button-glow" />
            <span>{t("common.refresh")}</span>
          </button>
        </div>
      </div>

      {error ? <ErrorMessage message={error} /> : null}

      {loading ? (
        <LoadingSpinner label={t("admin.premium.loading")} />
      ) : items.length === 0 ? (
        <EmptyState
          title={t("admin.premium.emptyTitle")}
          description={t("admin.premium.emptyDescription")}
        />
      ) : (
        <div className="admin-users-table-shell admin-audit-table-shell">
          <table className="admin-users-table admin-audit-table">
            <thead>
              <tr>
                <th>{t("admin.premium.columns.user")}</th>
                <th>{t("admin.premium.columns.requestedAt")}</th>
                <th>{t("admin.premium.columns.status")}</th>
                <th>{t("admin.premium.columns.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const busy = processingId === item.subscriptionId;
                return (
                  <tr key={item.subscriptionId}>
                    <td>
                      <div>{item.userFullName || "-"}</div>
                      <div style={{ fontSize: "0.75rem", color: "var(--text-soft)" }}>
                        #{item.userId}
                      </div>
                    </td>
                    <td>{formatDateTime(item.requestedAt)}</td>
                    <td>
                      <span className={`admin-premium-status-badge admin-premium-status-${item.status}`}>
                        {t(STATUS_LABELS[item.status] ?? item.status)}
                      </span>
                    </td>
                    <td>
                      <div className="admin-premium-actions">
                        <button
                          type="button"
                          className="admin-users-unblock-button"
                          disabled={busy}
                          onClick={() => handleApprove(item.subscriptionId)}
                        >
                          {busy ? "..." : t("admin.premium.approve")}
                        </button>
                        <button
                          type="button"
                          className="admin-users-perm-block-button"
                          disabled={busy}
                          onClick={() => handleReject(item.subscriptionId)}
                        >
                          {busy ? "..." : t("admin.premium.reject")}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
