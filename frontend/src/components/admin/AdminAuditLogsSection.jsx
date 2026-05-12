import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { getAdminAuditLogs } from "../../api/adminApi";
import { extractErrorMessage } from "../../api/responseUtils";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatDateTime } from "../../utils/formatters";

export default function AdminAuditLogsSection() {
  const { t } = useTranslation();
  const [auditPage, setAuditPage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionFilter, setActionFilter] = useState("");

  const loadAuditLogs = useCallback(async (action = "") => {
    setLoading(true);
    setError("");

    try {
      const page = await getAdminAuditLogs({
        action: action || null,
        page: 0,
        size: 20,
      });
      setAuditPage(page);
    } catch (loadError) {
      setAuditPage(null);
      setError(extractErrorMessage(loadError, t("admin.auditLogs.loadError")));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadAuditLogs(actionFilter);
  }, [actionFilter, loadAuditLogs]);

  const rows = useMemo(() => auditPage?.content ?? [], [auditPage]);

  return (
    <section className="admin-audit-section panel-surface">
      <div className="admin-audit-header">
        <div className="admin-audit-copy">
          <p className="eyebrow">{t("admin.auditLogs.eyebrow")}</p>
          <h3>{t("admin.auditLogs.title")}</h3>
          <p>{t("admin.auditLogs.description")}</p>
        </div>

        <div className="admin-audit-toolbar">
          <select value={actionFilter} onChange={(event) => setActionFilter(event.target.value)} className="admin-console-input">
            <option value="">{t("admin.auditLogs.filters.allActions")}</option>
            {[
              "USER_ACTIVATED",
              "USER_DEACTIVATED",
              "USER_ROLE_CHANGED",
              "USER_TEMP_BLOCKED",
              "USER_PERM_BLOCKED",
              "USER_UNBLOCKED",
              "NOTIFICATION_SENT_TO_USER",
              "NOTIFICATION_BROADCAST_SENT",
            ].map((action) => (
              <option key={action} value={action}>
                {t(`admin.auditLogs.actions.${action}`)}
              </option>
            ))}
          </select>

          <button type="button" className="admin-console-button admin-users-refresh" onClick={() => loadAuditLogs(actionFilter)}>
            <span className="admin-console-button-glow" />
            <span>{t("common.refresh")}</span>
          </button>
        </div>
      </div>

      {error ? <ErrorMessage message={error} /> : null}

      {loading ? (
        <LoadingSpinner label={t("admin.auditLogs.loading")} />
      ) : rows.length === 0 ? (
        <EmptyState
          title={t("admin.auditLogs.emptyTitle")}
          description={t("admin.auditLogs.emptyDescription")}
        />
      ) : (
        <div className="admin-users-table-shell">
          <table className="admin-users-table admin-audit-table">
            <thead>
              <tr>
                <th>{t("admin.auditLogs.columns.createdAt")}</th>
                <th>{t("admin.auditLogs.columns.actorUserId")}</th>
                <th>{t("admin.auditLogs.columns.targetUserId")}</th>
                <th>{t("admin.auditLogs.columns.action")}</th>
                <th>{t("admin.auditLogs.columns.description")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{formatDateTime(row.createdAt)}</td>
                  <td>{row.actorUserId ?? "-"}</td>
                  <td>{row.targetUserId ?? "-"}</td>
                  <td>{t(`admin.auditLogs.actions.${row.action}`)}</td>
                  <td>{row.description || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
