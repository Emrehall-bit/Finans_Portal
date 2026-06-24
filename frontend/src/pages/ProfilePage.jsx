import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Check, LogOut, X } from "lucide-react";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { useAuth } from "../auth/AuthContext";
import useToast from "../hooks/useToast";
import { extractErrorMessage } from "../api/responseUtils";
import { useTheme } from "../theme/ThemeContext";
import { formatDateTime } from "../utils/formatters";
import { deleteCurrentUserAccount } from "../api/userApi";
import { cancelPremiumUpgrade, getPremiumStatus, requestPremiumUpgrade } from "../api/premiumApi";
import i18n from "../i18n/index";

const DELETE_CONFIRM_PHRASE = "onaylıyorum.";

function buildFormState(user, themePreference) {
  return {
    fullName: user?.fullName ?? "",
    preferredLanguage: user?.preferredLanguage ?? "",
    themePreference: themePreference ?? "system",
  };
}

export default function ProfilePage() {
  const { t } = useTranslation();
  const {
    authLoading,
    hasAuthenticatedSession,
    keycloak,
    logout,
    refreshUserProfile,
    updateUserProfile,
    user,
    userProfile,
  } = useAuth();
  const { resolvedTheme, setThemePreference, themePreference } = useTheme();
  const [form, setForm] = useState(buildFormState(user, themePreference));
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast, showToast } = useToast();
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [premiumStatus, setPremiumStatus] = useState(null);
  const [premiumLoading, setPremiumLoading] = useState(true);
  const [premiumWorking, setPremiumWorking] = useState(false);

  useEffect(() => {
    setForm(buildFormState(user, themePreference));
  }, [themePreference, user]);

  useEffect(() => {
    if (userProfile || authLoading) return;
    let active = true;
    async function loadProfile() {
      try {
        setLoading(true);
        setError("");
        await refreshUserProfile();
      } catch (err) {
        if (active) setError(extractErrorMessage(err, t("profile.loadError")));
      } finally {
        if (active) setLoading(false);
      }
    }
    loadProfile();
    return () => { active = false; };
  }, [authLoading, refreshUserProfile, t, userProfile]);

  useEffect(() => {
    if (authLoading) return;
    let active = true;
    getPremiumStatus()
      .then((data) => { if (active) setPremiumStatus(data); })
      .catch(() => {})
      .finally(() => { if (active) setPremiumLoading(false); });
    return () => { active = false; };
  }, [authLoading]);

  async function handlePremiumUpgrade() {
    setPremiumWorking(true);
    try {
      const data = await requestPremiumUpgrade();
      setPremiumStatus(data);
      showToast("success", t("profile.premium.upgradeSuccess"));
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("profile.premium.upgradeError")));
    } finally {
      setPremiumWorking(false);
    }
  }

  async function handlePremiumCancel() {
    setPremiumWorking(true);
    try {
      const data = await cancelPremiumUpgrade();
      setPremiumStatus(data);
      showToast("success", t("profile.premium.cancelSuccess"));
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("profile.premium.cancelError")));
    } finally {
      setPremiumWorking(false);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      setSubmitting(true);
      setError("");
      await updateUserProfile({
        ...form,
        themePreference: form.themePreference === "system" ? "" : form.themePreference,
      });
      setThemePreference(form.themePreference || "system");
      const lang = form.preferredLanguage || "tr";
      if (lang !== i18n.language) {
        await i18n.changeLanguage(lang);
      }
      showToast("success", t("profile.updateSuccess"));
    } catch (err) {
      setError(extractErrorMessage(err, t("profile.updateError")));
    } finally {
      setSubmitting(false);
    }
  }

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  function openDeleteConfirm() {
    setDeleteConfirmText("");
    setDeleteConfirmOpen(true);
  }

  function closeDeleteConfirm() {
    setDeleteConfirmOpen(false);
    setDeleteConfirmText("");
  }

  async function handleDeleteAccount() {
    if (deleteConfirmText !== DELETE_CONFIRM_PHRASE || deleting) return;
    setDeleting(true);
    try {
      await deleteCurrentUserAccount();
      logout();
    } catch (err) {
      setError(extractErrorMessage(err, t("profile.danger.delete.error")));
      closeDeleteConfirm();
    } finally {
      setDeleting(false);
    }
  }

  const sessionDetails = useMemo(() => {
    const parsed = keycloak?.tokenParsed ?? {};
    return {
      authenticated: hasAuthenticatedSession ? t("profile.session.active") : t("profile.session.inactive"),
      subject: parsed?.sub || "-",
      sessionState: keycloak?.sessionId || keycloak?.subject || "-",
      tokenExpiry: parsed?.exp ? formatDateTime(new Date(parsed.exp * 1000)) : "-",
    };
  }, [hasAuthenticatedSession, keycloak, t]);

  const displayAuthProvider = formatAuthProviderLabel(user?.authProvider ?? userProfile?.authProvider);

  return (
    <div className="profile-page-stack profile-settings-page">
      {toast ? (
        <div className={`toast-notify ${toast.type}`}>
          {toast.type === "success"
            ? <Check size={15} strokeWidth={2.5} className="toast-notify-icon" />
            : <X size={15} strokeWidth={2.5} className="toast-notify-icon" />}
          <span>{toast.message}</span>
        </div>
      ) : null}
      {error ? <ErrorMessage message={error} /> : null}
      {authLoading || loading ? <LoadingSpinner label={t("profile.loading")} /> : null}

      {!authLoading && !loading ? (
        <div className="profile-dashboard-grid">

          {/* ── Sol sütun ── */}
          <div className="profile-col">

            {/* Profile Summary */}
            <section className="panel-surface profile-summary-card">
              <div className="profile-summary-identity">
                <div className="profile-avatar profile-avatar-large">
                  {getInitials(user?.fullName || user?.email)}
                </div>
                <div className="profile-summary-copy">
                  <strong className="profile-summary-name">{user?.fullName || t("profile.noUserName")}</strong>
                  <span className="profile-summary-email">{user?.email || t("profile.noEmail")}</span>
                </div>
              </div>
              <button
                type="button"
                className="secondary-button profile-logout-button"
                onClick={logout}
              >
                <LogOut size={14} strokeWidth={2} aria-hidden />
                <span>{t("profile.logout")}</span>
              </button>
            </section>

            {/* Account Settings */}
            <form className="panel-surface profile-form-card profile-account-card" onSubmit={handleSubmit}>
              <div className="panel-head">
                <div>
                  <p className="eyebrow">{t("profile.settings")}</p>
                  <h3>{t("profile.accountSettingsTitle")}</h3>
                </div>
                <span className="pill">{t("profile.selfService")}</span>
              </div>

              <div className="profile-form-grid">
                <label className="profile-field">
                  <span>{t("profile.fullName")}</span>
                  <input
                    name="fullName"
                    value={form.fullName}
                    onChange={handleChange}
                    placeholder={t("profile.fullNamePlaceholder")}
                    maxLength={255}
                  />
                </label>

                <label className="profile-field">
                  <span>{t("profile.email")}</span>
                  <input value={user?.email || ""} disabled readOnly />
                </label>

                <label className="profile-field">
                  <span>{t("profile.language")}</span>
                  <select name="preferredLanguage" value={form.preferredLanguage} onChange={handleChange}>
                    <option value="">{t("profile.systemDefault")}</option>
                    <option value="tr">{t("common.turkish")}</option>
                    <option value="en">{t("common.english")}</option>
                  </select>
                </label>

                <label className="profile-field">
                  <span>{t("profile.theme")}</span>
                  <select name="themePreference" value={form.themePreference} onChange={handleChange}>
                    <option value="system">{t("profile.systemDefault")}</option>
                    <option value="light">{t("layout.themeLight")}</option>
                    <option value="dark">{t("layout.themeDark")}</option>
                  </select>
                </label>
              </div>

              <div className="profile-note">
                {t("profile.activeTheme")} <strong>{resolvedTheme === "dark" ? t("layout.themeDark") : t("layout.themeLight")}</strong>
              </div>

              <div className="actions-row profile-form-actions">
                <button type="submit" disabled={submitting}>
                  {submitting ? t("profile.saving") : t("profile.saveChanges")}
                </button>
              </div>

              {user?.role !== "ADMIN" && (
                <p className="profile-contact-note">
                  {t("profile.contactNote")}{" "}
                  <a href="mailto:financeportal.system@gmail.com">financeportal.system@gmail.com</a>
                  {" "}{t("profile.contactNoteSuffix")}
                </p>
              )}
            </form>

          </div>

          {/* ── Sağ sütun ── */}
          <div className="profile-col">

            {/* Security */}
            <section className="panel-surface profile-security-card">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">{t("profile.security.eyebrow")}</p>
                  <h3>{t("profile.security.title")}</h3>
                </div>
                <span className={`portfolio-status-pill ${hasAuthenticatedSession ? "is-live" : "is-unavailable"}`}>
                  {sessionDetails.authenticated}
                </span>
              </div>

              <div className="profile-stat-grid">
                <div className="profile-stat">
                  <span>{t("profile.security.accountStatus")}</span>
                  <strong>{hasAuthenticatedSession ? t("profile.session.active") : "-"}</strong>
                </div>
                <div className="profile-stat">
                  <span>{t("profile.authProvider")}</span>
                  <strong>{displayAuthProvider || "-"}</strong>
                </div>
                <div className="profile-stat">
                  <span>{t("profile.createdAt")}</span>
                  <strong>{formatDateTime(user?.createdAt)}</strong>
                </div>
              </div>

              <details className="profile-technical-session">
                <summary>{t("profile.session.technicalTitle")}</summary>
                <div className="profile-technical-grid">
                  <div className="profile-stat">
                    <span>{t("profile.keycloakId")}</span>
                    <strong>{user?.keycloakId || "-"}</strong>
                  </div>
                  <div className="profile-stat">
                    <span>{t("profile.session.sessionState")}</span>
                    <strong>{sessionDetails.sessionState}</strong>
                  </div>
                  <div className="profile-stat">
                    <span>{t("profile.session.tokenExpiry")}</span>
                    <strong>{sessionDetails.tokenExpiry}</strong>
                  </div>
                  <div className="profile-stat">
                    <span>{t("profile.session.subject")}</span>
                    <strong>{sessionDetails.subject}</strong>
                  </div>
                </div>
              </details>
            </section>

            {/* Premium */}
            {!premiumLoading && (
              <section className="panel-surface profile-premium-card">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("profile.premium.eyebrow")}</p>
                    <h3>{t("profile.premium.title")}</h3>
                  </div>
                  {premiumStatus?.status && (
                    <span className={`premium-status-badge premium-status-${premiumStatus.status}`}>
                      {t(`premium.status.${premiumStatus.status}`)}
                    </span>
                  )}
                </div>

                {premiumStatus?.premiumActive ? (
                  <div className="profile-stat-grid">
                    <div className="profile-stat">
                      <span>{t("profile.premium.plan")}</span>
                      <strong>{premiumStatus.planType ?? "PREMIUM"}</strong>
                    </div>
                    <div className="profile-stat">
                      <span>{t("profile.premium.expiresAt")}</span>
                      <strong>{formatDateTime(premiumStatus.expiresAt)}</strong>
                    </div>
                  </div>
                ) : (
                  <p className="profile-premium-desc">
                    {premiumStatus?.status === "PAYMENT_PENDING" || premiumStatus?.status === "PREMIUM_REQUESTED"
                      ? t("profile.premium.pendingDesc")
                      : t("profile.premium.noSubscription")}
                  </p>
                )}

                <div className="actions-row" style={{ marginTop: 14 }}>
                  {premiumStatus?.premiumActive ? (
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={premiumWorking}
                      onClick={handlePremiumCancel}
                    >
                      {premiumWorking ? "..." : t("profile.premium.cancelActive")}
                    </button>
                  ) : (
                    premiumStatus?.status === "PAYMENT_PENDING" || premiumStatus?.status === "PREMIUM_REQUESTED"
                      ? (
                        <button
                          type="button"
                          className="secondary-button"
                          disabled={premiumWorking}
                          onClick={handlePremiumCancel}
                        >
                          {premiumWorking ? "..." : t("profile.premium.cancel")}
                        </button>
                      ) : (
                        <button
                          type="button"
                          disabled={premiumWorking}
                          onClick={handlePremiumUpgrade}
                        >
                          {premiumWorking ? "..." : t("profile.premium.upgrade")}
                        </button>
                      )
                  )}
                </div>
              </section>
            )}

            {/* Danger Zone */}
            <section className="panel-surface profile-danger-card">
              <div className="panel-head">
                <div>
                  <p className="eyebrow profile-danger-eyebrow">{t("profile.danger.eyebrow")}</p>
                  <h3>{t("profile.danger.title")}</h3>
                </div>
              </div>

              <div className="profile-danger-item">
                <div className="profile-danger-item-copy">
                  <strong>{t("profile.danger.delete.title")}</strong>
                  <p>{t("profile.danger.delete.description")}</p>
                </div>
                <button type="button" className="danger-button profile-danger-btn" onClick={openDeleteConfirm}>
                  {t("profile.danger.delete.action")}
                </button>
              </div>
            </section>

          </div>

        </div>
      ) : null}

      {/* Delete confirmation modal */}
      {deleteConfirmOpen && (
        <div className="modal-backdrop" role="presentation" onClick={closeDeleteConfirm}>
          <div
            className="auth-modal instrument-action-modal"
            role="dialog"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="instrument-action-modal-head">
              <div>
                <p className="eyebrow profile-danger-eyebrow">{t("profile.danger.eyebrow")}</p>
                <h3>{t("profile.danger.delete.confirmTitle")}</h3>
              </div>
              <button type="button" className="secondary-button" onClick={closeDeleteConfirm}>
                {t("common.close")}
              </button>
            </div>

            <p className="profile-delete-modal-desc">{t("profile.danger.delete.confirmDescription")}</p>

            <label className="profile-field profile-delete-confirm-field">
              <span>{t("profile.danger.delete.confirmLabel")}</span>
              <input
                type="text"
                value={deleteConfirmText}
                onChange={(e) => setDeleteConfirmText(e.target.value)}
                placeholder={DELETE_CONFIRM_PHRASE}
                autoComplete="off"
                autoFocus
              />
            </label>

            <div className="instrument-action-footer">
              <button
                type="button"
                className="danger-button"
                disabled={deleteConfirmText !== DELETE_CONFIRM_PHRASE || deleting}
                onClick={handleDeleteAccount}
              >
                {deleting ? t("profile.danger.delete.deleting") : t("profile.danger.delete.confirmAction")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function getInitials(value) {
  return String(value || "FP")
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("");
}

function formatAuthProviderLabel(value) {
  const normalized = String(value || "KEYCLOAK").toUpperCase();
  const currentLanguage =
    typeof window === "undefined"
      ? "tr"
      : String(window.localStorage.getItem("financePortal.language") || "tr").toLowerCase();
  const isTurkish = currentLanguage.startsWith("tr");
  return {
    KEYCLOAK: isTurkish ? "Keycloak" : "Keycloak",
    LOCAL: isTurkish ? "Yerel" : "Local",
  }[normalized] ?? normalized;
}
