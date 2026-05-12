import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import { useAuth } from "../auth/AuthContext";
import useToast from "../hooks/useToast";
import { extractErrorMessage } from "../api/responseUtils";
import { useTheme } from "../theme/ThemeContext";
import { formatDateTime } from "../utils/formatters";

function buildFormState(user, themePreference) {
  return {
    fullName: user?.fullName ?? "",
    preferredLanguage: user?.preferredLanguage ?? "",
    themePreference: themePreference ?? "system",
  };
}

export default function ProfilePage() {
  const { t, i18n } = useTranslation();
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

  useEffect(() => {
    setForm(buildFormState(user, themePreference));
  }, [themePreference, user]);

  useEffect(() => {
    if (userProfile || authLoading) {
      return;
    }

    let active = true;

    async function loadProfile() {
      try {
        setLoading(true);
        setError("");
        await refreshUserProfile();
      } catch (err) {
        if (active) {
          setError(extractErrorMessage(err, t("profile.loadError")));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadProfile();

    return () => {
      active = false;
    };
  }, [authLoading, refreshUserProfile, t, userProfile]);

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      setSubmitting(true);
      setError("");
      await updateUserProfile({
        ...form,
        themePreference: form.themePreference === "system" ? "" : form.themePreference,
      });
      showToast("success", t("profile.updateSuccess"));
    } catch (err) {
      setError(extractErrorMessage(err, t("profile.updateError")));
    } finally {
      setSubmitting(false);
    }
  }

  function handleChange(event) {
    const { name, value } = event.target;
    if (name === "themePreference") {
      setThemePreference(value);
    }
    setForm((current) => ({
      ...current,
      [name]: value,
    }));
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
  const displayRole = formatRoleLabel(user?.role, t);
  const displayAuthProvider = formatAuthProviderLabel(userProfile?.authProvider, t);
  const currentLanguage = String(i18n.resolvedLanguage || i18n.language || "tr").toLowerCase();
  const isTurkish = currentLanguage.startsWith("tr");
  const profileLabels = isTurkish
    ? {
        authProvider: "Kimlik Saglayici",
        sessionState: "Oturum Kimligi",
        tokenExpiry: "Token Suresi",
        subject: "Kimlik",
        authenticated: "Kimlik Durumu",
      }
    : {
        authProvider: "Authentication Provider",
        sessionState: "Session State",
        tokenExpiry: "Token Expiry",
        subject: "Subject",
        authenticated: "Authenticated",
      };

  return (
    <div className="profile-page-stack settings-shell">
      <PageHeader
        eyebrow={t("profile.eyebrow")}
        title={t("profile.title")}
        description={t("profile.description")}
        actions={
          <div className="actions-row">
            <button type="button" className="danger-button" onClick={logout}>
              {t("profile.logout")}
            </button>
          </div>
        }
      />

      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {authLoading || loading ? <LoadingSpinner label={t("profile.loading")} /> : null}

      {!authLoading && !loading ? (
        <div className="profile-grid settings-grid">
          <section className="card profile-summary-card">
            <p className="eyebrow">{t("profile.userInfo")}</p>
            <div className="profile-summary-head">
              <div className="profile-avatar profile-avatar-large">
                {(user?.fullName || user?.email || "FP")
                  .split(" ")
                  .filter(Boolean)
                  .slice(0, 2)
                  .map((part) => part[0]?.toUpperCase() || "")
                  .join("")}
              </div>
              <div>
                <h3>{user?.fullName || t("profile.noUserName")}</h3>
                <p>{user?.email || t("profile.noEmail")}</p>
              </div>
            </div>

            <div className="profile-stat-grid">
              <div className="profile-stat">
                <span>{t("profile.role")}</span>
                <strong>{displayRole}</strong>
              </div>
              <div className="profile-stat">
                <span>{profileLabels.authProvider}</span>
                <strong>{displayAuthProvider}</strong>
              </div>
              <div className="profile-stat">
                <span>{t("profile.createdAt")}</span>
                <strong>{formatDateTime(user?.createdAt)}</strong>
              </div>
              <div className="profile-stat">
                <span>{t("profile.keycloakId")}</span>
                <strong>{user?.keycloakId || "-"}</strong>
              </div>
            </div>
          </section>

          <form className="card profile-form-card" onSubmit={handleSubmit}>
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("profile.settings")}</p>
                <h3>{t("profile.preferencesTitle")}</h3>
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

            <div className="profile-note">{t("profile.themeNote")}</div>

            <div className="profile-note">
              {t("profile.activeTheme")} <strong>{resolvedTheme === "dark" ? t("layout.themeDark") : t("layout.themeLight")}</strong>
            </div>

            <div className="actions-row">
              <button type="submit" disabled={submitting}>
                {submitting ? t("profile.saving") : t("profile.saveChanges")}
              </button>
            </div>
          </form>

          <section className="card profile-form-card settings-session-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("profile.session.eyebrow")}</p>
                <h3>{t("profile.session.title")}</h3>
              </div>
              <span className={`portfolio-status-pill ${hasAuthenticatedSession ? "is-live" : "is-unavailable"}`}>
                {sessionDetails.authenticated}
              </span>
            </div>

            <div className="profile-stat-grid">
              <div className="profile-stat">
                <span>{profileLabels.sessionState}</span>
                <strong>{sessionDetails.sessionState}</strong>
              </div>
              <div className="profile-stat">
                <span>{profileLabels.tokenExpiry}</span>
                <strong>{sessionDetails.tokenExpiry}</strong>
              </div>
              <div className="profile-stat">
                <span>{profileLabels.subject}</span>
                <strong>{sessionDetails.subject}</strong>
              </div>
              <div className="profile-stat">
                <span>{profileLabels.authenticated}</span>
                <strong>{sessionDetails.authenticated}</strong>
              </div>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}

function formatRoleLabel(value, t) {
  const normalized = String(value || "USER").toUpperCase();
  const key = normalized.startsWith("ROLE_") ? normalized.slice(5) : normalized;
  return t(`profile.values.role.${key}`, key);
}

function formatAuthProviderLabel(value, t) {
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
