import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  getAllUsers,
  getUserById,
  getUserModeration,
  updateUserRole,
} from "../../api/userApi";
import { extractErrorMessage } from "../../api/responseUtils";
import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatDateTime } from "../../utils/formatters";
import { useAuth } from "../../auth/AuthContext";
import SendUserNotificationModal from "./SendUserNotificationModal";
import ModerationActionModal from "./ModerationActionModal";
import BroadcastNotificationModal from "./BroadcastNotificationModal";

function formatRole(value) {
  return value || "-";
}

function formatPreference(value) {
  return value || "-";
}

function formatModerationStatus(value, t) {
  return t(`admin.users.moderation.statusValues.${value ?? "ACTIVE"}`);
}

export default function UserManagementSection() {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const [usersPage, setUsersPage] = useState(null);
  const [selectedUser, setSelectedUser] = useState(null);
  const [selectedModeration, setSelectedModeration] = useState(null);
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [processingRoleUserId, setProcessingRoleUserId] = useState(null);
  const [processingModerationUserId, setProcessingModerationUserId] = useState(null);
  const [notificationUser, setNotificationUser] = useState(null);
  const [moderationModal, setModerationModal] = useState({ mode: null, user: null });
  const [broadcastOpen, setBroadcastOpen] = useState(false);
  const [moderationByUserId, setModerationByUserId] = useState({});
  const [listError, setListError] = useState("");
  const [listSuccess, setListSuccess] = useState("");
  const [detailError, setDetailError] = useState("");

  const loadUsers = useCallback(async () => {
    setLoadingUsers(true);
    setListError("");
    setListSuccess("");

    try {
      const page = await getAllUsers();
      setUsersPage(page);
      const rows = page?.content ?? [];
      const moderationEntries = await Promise.all(
        rows.map(async (user) => {
          try {
            return [user.id, await getUserModeration(user.id)];
          } catch {
            return [user.id, null];
          }
        }),
      );

      setModerationByUserId(Object.fromEntries(moderationEntries));
    } catch (error) {
      setUsersPage(null);
      setListError(extractErrorMessage(error, t("admin.users.listError")));
    } finally {
      setLoadingUsers(false);
    }
  }, [t]);

  const loadUserDetail = useCallback(
    async (userId) => {
      setSelectedUserId(userId);
      setLoadingDetail(true);
      setDetailError("");

      try {
        const [user, moderation] = await Promise.all([getUserById(userId), getUserModeration(userId)]);
        setSelectedUser(user);
        setSelectedModeration(moderation);
        setModerationByUserId((current) => ({
          ...current,
          [userId]: moderation,
        }));
      } catch (error) {
        setSelectedUser(null);
        setSelectedModeration(null);
        setDetailError(extractErrorMessage(error, t("admin.users.detailError")));
      } finally {
        setLoadingDetail(false);
      }
    },
    [t],
  );

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  const users = useMemo(() => usersPage?.content ?? [], [usersPage]);

  const applyModerationUpdate = useCallback((targetUserId, moderation) => {
    setModerationByUserId((current) => ({
      ...current,
      [targetUserId]: moderation,
    }));

    if (selectedUserId === targetUserId) {
      setSelectedModeration(moderation);
    }
  }, [selectedUserId]);

  const applyUpdatedUser = useCallback((updatedUser) => {
    if (!updatedUser) {
      return;
    }

    setUsersPage((current) => {
      if (!current) {
        return current;
      }

      return {
        ...current,
        content: (current.content ?? []).map((user) => (user.id === updatedUser.id ? updatedUser : user)),
      };
    });

    setSelectedUser((current) => (current?.id === updatedUser.id ? updatedUser : current));
  }, []);

  const handleRoleToggle = useCallback(
    async (user) => {
      const nextRole = user.role === "ADMIN" ? "USER" : "ADMIN";

      setProcessingRoleUserId(user.id);
      setListError("");
      setListSuccess("");
      setDetailError("");

      try {
        const updatedUser = await updateUserRole(user.id, nextRole);
        applyUpdatedUser(updatedUser);
      } catch (error) {
        const message = extractErrorMessage(error, t("admin.users.roleUpdateError"));
        setListError(message);
        if (selectedUserId === user.id) {
          setDetailError(message);
        }
      } finally {
        setProcessingRoleUserId(null);
      }
    },
    [applyUpdatedUser, selectedUserId, t],
  );

  const handleModerationSuccess = useCallback((moderation) => {
    if (!moderation?.userId) {
      return;
    }

    applyModerationUpdate(moderation.userId, moderation);
    setModerationModal({ mode: null, user: null });
    setProcessingModerationUserId(null);
    setListError("");
    setDetailError("");

    if (moderation.status === "TEMP_BLOCKED") {
      setListSuccess(t("admin.users.moderation.tempBlockSuccess"));
    } else if (moderation.status === "PERM_BLOCKED") {
      setListSuccess(t("admin.users.moderation.permBlockSuccess"));
    } else {
      setListSuccess(t("admin.users.moderation.unblockSuccess"));
    }
  }, [applyModerationUpdate, t]);

  const openModerationModal = useCallback((mode, user) => {
    setListError("");
    setListSuccess("");
    setModerationModal({ mode, user });
    setProcessingModerationUserId(user.id);
  }, []);

  const closeModerationModal = useCallback(() => {
    setModerationModal({ mode: null, user: null });
    setProcessingModerationUserId(null);
  }, []);

  return (
    <section className="admin-users-section panel-surface">
      <div className="admin-users-header">
        <div className="admin-users-copy">
          <p className="eyebrow">{t("admin.users.eyebrow")}</p>
          <h3>{t("admin.users.title")}</h3>
          <p>{t("admin.users.description")}</p>
        </div>
        <div className="admin-users-toolbar">
          <span className="admin-users-count">
            {t("admin.users.count", {
              count: usersPage?.totalElements ?? users.length,
            })}
          </span>
          <button type="button" className="admin-console-button" onClick={() => setBroadcastOpen(true)}>
            <span className="admin-console-button-glow" />
            <span>{t("admin.broadcast.openButton")}</span>
          </button>
          <button type="button" className="admin-console-button admin-users-refresh" onClick={loadUsers}>
            <span className="admin-console-button-glow" />
            <span>{t("common.refresh")}</span>
          </button>
        </div>
      </div>

      {listError ? <ErrorMessage message={listError} /> : null}
      {listSuccess ? <div className="status-box success">{listSuccess}</div> : null}

      <div className="admin-users-grid">
        <div className="admin-users-table-wrap">
          {loadingUsers ? (
            <LoadingSpinner label={t("admin.users.loadingList")} />
          ) : users.length === 0 ? (
            <EmptyState
              title={t("admin.users.emptyTitle")}
              description={t("admin.users.emptyDescription")}
            />
          ) : (
            <div className="admin-users-table-shell">
              <table className="admin-users-table">
                <thead>
                  <tr>
                    <th>{t("admin.users.columns.email")}</th>
                    <th>{t("admin.users.columns.fullName")}</th>
                    <th>{t("admin.users.columns.lastLogin")}</th>
                    <th>{t("admin.users.columns.createdAt")}</th>
                    <th>{t("admin.users.columns.detail")}</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => {
                    const moderation = moderationByUserId[user.id];
                    const isSelf = userId === user.id;

                    return (
                    <tr key={user.id} className={selectedUserId === user.id ? "admin-users-row-active" : ""}>
                      <td>{user.email || "-"}</td>
                      <td>
                        <span className="admin-users-fullname">{user.fullName || "-"}</span>
                        <span className="admin-users-role-tag">{formatRole(user.role)}</span>
                      </td>
                      <td>{formatDateTime(user.lastLoginAt)}</td>
                      <td>{formatDateTime(user.createdAt)}</td>
                      <td>
                        <div className="admin-users-action-group">
                          <button
                            type="button"
                            className="admin-users-detail-button"
                            onClick={() => loadUserDetail(user.id)}
                          >
                            {t("common.detail")}
                          </button>
                          <button
                            type="button"
                            className="admin-users-role-button"
                            disabled={processingRoleUserId === user.id || isSelf}
                            onClick={() => handleRoleToggle(user)}
                          >
                            {processingRoleUserId === user.id
                              ? t("admin.users.roleUpdating")
                              : user.role === "ADMIN"
                                ? t("admin.users.revokeAdmin")
                                : t("admin.users.makeAdmin")}
                          </button>
                          <button
                            type="button"
                            className="admin-users-notification-button"
                            onClick={() => setNotificationUser(user)}
                          >
                            {t("admin.users.sendNotification")}
                          </button>
                          {moderation?.status !== "TEMP_BLOCKED" && moderation?.status !== "PERM_BLOCKED" && (
                            <button
                              type="button"
                              className="admin-users-temp-block-button"
                              disabled={processingModerationUserId === user.id || isSelf}
                              onClick={() => openModerationModal("temp", user)}
                            >
                              {t("admin.users.moderation.tempBlock")}
                            </button>
                          )}
                          {moderation?.status !== "TEMP_BLOCKED" && moderation?.status !== "PERM_BLOCKED" && (
                            <button
                              type="button"
                              className="admin-users-perm-block-button"
                              disabled={processingModerationUserId === user.id || isSelf}
                              onClick={() => openModerationModal("perm", user)}
                            >
                              {t("admin.users.moderation.permBlock")}
                            </button>
                          )}
                          {moderation?.status === "TEMP_BLOCKED" && (
                            <button
                              type="button"
                              className="admin-users-unblock-button"
                              disabled={processingModerationUserId === user.id || isSelf}
                              onClick={() => openModerationModal("unblock", user)}
                            >
                              {t("admin.users.moderation.removeTempBlock")}
                            </button>
                          )}
                          {moderation?.status === "PERM_BLOCKED" && (
                            <button
                              type="button"
                              className="admin-users-unblock-button"
                              disabled={processingModerationUserId === user.id || isSelf}
                              onClick={() => openModerationModal("unblock", user)}
                            >
                              {t("admin.users.moderation.removePermBlock")}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <aside className="admin-users-detail-card">
          <div className="admin-users-detail-head">
            <p className="eyebrow">{t("admin.users.detailEyebrow")}</p>
            <h4>{t("admin.users.detailTitle")}</h4>
          </div>

          {detailError ? <ErrorMessage message={detailError} /> : null}

          {loadingDetail ? (
            <LoadingSpinner label={t("admin.users.loadingDetail")} />
          ) : !selectedUser ? (
            <EmptyState
              title={t("admin.users.detailEmptyTitle")}
              description={t("admin.users.detailEmptyDescription")}
            />
          ) : (
            <dl className="admin-users-detail-list">
              <div>
                <dt>{t("admin.users.fields.id")}</dt>
                <dd>{selectedUser.id ?? "-"}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.keycloakId")}</dt>
                <dd>{selectedUser.keycloakId || "-"}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.email")}</dt>
                <dd>{selectedUser.email || "-"}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.fullName")}</dt>
                <dd>{selectedUser.fullName || "-"}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.role")}</dt>
                <dd>{formatRole(selectedUser.role)}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.preferences")}</dt>
                <dd>
                  {formatPreference(selectedUser.preferredLanguage)} /{" "}
                  {formatPreference(selectedUser.themePreference)}
                </dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.lastLoginAt")}</dt>
                <dd>{formatDateTime(selectedUser.lastLoginAt)}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.createdAt")}</dt>
                <dd>{formatDateTime(selectedUser.createdAt)}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.updatedAt")}</dt>
                <dd>{formatDateTime(selectedUser.updatedAt)}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.moderationStatus")}</dt>
                <dd>
                  <span className={`admin-users-moderation-badge ${String(selectedModeration?.status ?? "ACTIVE").toLowerCase().replace("_", "-")}`}>
                    {formatModerationStatus(selectedModeration?.status ?? "ACTIVE", t)}
                  </span>
                </dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.moderationReason")}</dt>
                <dd>{selectedModeration?.reason || "-"}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.moderationBlockedUntil")}</dt>
                <dd>{formatDateTime(selectedModeration?.blockedUntil)}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.moderationCreatedBy")}</dt>
                <dd>{selectedModeration?.createdBy ?? "-"}</dd>
              </div>
              <div>
                <dt>{t("admin.users.fields.moderationCreatedAt")}</dt>
                <dd>{formatDateTime(selectedModeration?.createdAt)}</dd>
              </div>
            </dl>
          )}
        </aside>
      </div>

      <BroadcastNotificationModal
        isOpen={broadcastOpen}
        onClose={() => setBroadcastOpen(false)}
      />

      <SendUserNotificationModal
        isOpen={Boolean(notificationUser)}
        user={notificationUser}
        onClose={() => setNotificationUser(null)}
      />

      <ModerationActionModal
        isOpen={Boolean(moderationModal.mode && moderationModal.user)}
        mode={moderationModal.mode}
        user={moderationModal.user}
        onClose={closeModerationModal}
        onSuccess={handleModerationSuccess}
      />
    </section>
  );
}
