import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { LogOut } from "lucide-react";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
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
  const displayAuthProvider = formatAuthProviderLabel(user?.authProvider ?? userProfile?.authProvider, t);
  const profileBio = userProfile?.bio || user?.bio || "-";
  const profileBadges = Array.isArray(userProfile?.badges) ? userProfile.badges : [];

  return (
    <div className="profile-page-stack profile-settings-page">
      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {authLoading || loading ? <LoadingSpinner label={t("profile.loading")} /> : null}

      {!authLoading && !loading ? (
        <>
          <section className="panel-surface profile-hero-card">
            <div className="profile-hero-main">
              <div className="profile-avatar profile-avatar-large">
                {getInitials(user?.fullName || user?.email)}
              </div>
              <div className="profile-hero-copy">
                <p className="eyebrow">{t("profile.eyebrow")}</p>
                <h1>{user?.fullName || t("profile.noUserName")}</h1>
                <p className="profile-email">{user?.email || t("profile.noEmail")}</p>
              </div>
            </div>

            <div className="profile-hero-meta">
              <div>
                <span>{t("profile.bio")}</span>
                <strong>{profileBio}</strong>
              </div>
              <div>
                <span>{t("profile.badges")}</span>
                {profileBadges.length > 0 ? (
                  <div className="profile-badge-row">
                    {profileBadges.map((badge) => (
                      <span key={badge} className="summary-chip">{badge}</span>
                    ))}
                  </div>
                ) : (
                  <strong>-</strong>
                )}
              </div>
            </div>

            <button type="button" className="danger-button profile-logout-button" onClick={logout}>
              <LogOut size={18} strokeWidth={1.9} aria-hidden />
              <span>{t("profile.logout")}</span>
            </button>
          </section>

          <div className="profile-grid">
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
            </form>

            <div className="profile-side-stack">
              <section className="panel-surface profile-community-card">
                <div className="panel-head">
                  <div>
                    <p className="eyebrow">{t("profile.community.eyebrow")}</p>
                    <h3>{t("profile.community.title")}</h3>
                  </div>
                </div>

                <div className="profile-community-grid">
                  <div className="profile-stat">
                    <span>{t("profile.community.posts")}</span>
                    <strong>-</strong>
                  </div>
                  <div className="profile-stat">
                    <span>{t("profile.community.comments")}</span>
                    <strong>-</strong>
                  </div>
                  <div className="profile-stat">
                    <span>{t("profile.community.likes")}</span>
                    <strong>-</strong>
                  </div>
                  <div className="profile-stat">
                    <span>{t("profile.community.followers")}</span>
                    <strong>-</strong>
                  </div>
                </div>
              </section>

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
            </div>
          </div>

          <section className="panel-surface profile-interactions-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">{t("profile.interactions.eyebrow")}</p>
                <h3>{t("profile.interactions.title")}</h3>
              </div>
            </div>
            <EmptyState title={t("profile.interactions.emptyTitle")} description={t("profile.interactions.emptyDescription")} />
          </section>
        </>
      ) : null}
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
