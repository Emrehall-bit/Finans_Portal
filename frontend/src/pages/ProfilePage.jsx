import { useEffect, useMemo, useState } from "react";
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
          setError(extractErrorMessage(err, "Profil bilgileri yuklenemedi."));
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
  }, [authLoading, refreshUserProfile, userProfile]);

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      setSubmitting(true);
      setError("");
      await updateUserProfile({
        ...form,
        themePreference: form.themePreference === "system" ? "" : form.themePreference,
      });
      showToast("success", "Profil bilgileri guncellendi.");
    } catch (err) {
      setError(extractErrorMessage(err, "Profil guncellenemedi."));
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
      authenticated: hasAuthenticatedSession ? "Aktif" : "Pasif",
      subject: parsed?.sub || "-",
      sessionState: keycloak?.sessionId || keycloak?.subject || "-",
      tokenExpiry: parsed?.exp ? formatDateTime(new Date(parsed.exp * 1000)) : "-",
    };
  }, [hasAuthenticatedSession, keycloak]);

  return (
    <div className="profile-page-stack settings-shell">
      <PageHeader
        eyebrow="Profil / Ayarlar"
        title="Hesap Ayarlari"
        description="Kullanici bilgilerini, tercihlerini ve Keycloak oturum durumunu yonet."
        actions={
          <div className="actions-row">
            <button type="button" className="danger-button" onClick={logout}>
              Cikis yap
            </button>
          </div>
        }
      />

      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {authLoading || loading ? <LoadingSpinner label="Profil yukleniyor..." /> : null}

      {!authLoading && !loading ? (
        <div className="profile-grid settings-grid">
          <section className="card profile-summary-card">
            <p className="eyebrow">Kullanici Bilgileri</p>
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
                <h3>{user?.fullName || "Kullanici bilgisi yok"}</h3>
                <p>{user?.email || "E-posta bilgisi yok"}</p>
              </div>
            </div>

            <div className="profile-stat-grid">
              <div className="profile-stat">
                <span>Rol</span>
                <strong>{user?.role || "USER"}</strong>
              </div>
              <div className="profile-stat">
                <span>Auth Provider</span>
                <strong>{userProfile?.authProvider || "Keycloak"}</strong>
              </div>
              <div className="profile-stat">
                <span>Hesap Olusturma</span>
                <strong>{formatDateTime(user?.createdAt)}</strong>
              </div>
              <div className="profile-stat">
                <span>Keycloak ID</span>
                <strong>{user?.keycloakId || "-"}</strong>
              </div>
            </div>
          </section>

          <form className="card profile-form-card" onSubmit={handleSubmit}>
            <div className="panel-head">
              <div>
                <p className="eyebrow">Ayarlar</p>
                <h3>Profil ve tercihler</h3>
              </div>
              <span className="pill">Self-service</span>
            </div>

            <div className="profile-form-grid">
              <label className="profile-field">
                <span>Ad Soyad</span>
                <input
                  name="fullName"
                  value={form.fullName}
                  onChange={handleChange}
                  placeholder="Ad soyad"
                  maxLength={255}
                />
              </label>

              <label className="profile-field">
                <span>E-posta</span>
                <input value={user?.email || ""} disabled readOnly />
              </label>

              <label className="profile-field">
                <span>Dil secimi</span>
                <select name="preferredLanguage" value={form.preferredLanguage} onChange={handleChange}>
                  <option value="">Sistem varsayilani</option>
                  <option value="tr">Turkce</option>
                  <option value="en">English</option>
                </select>
              </label>

              <label className="profile-field">
                <span>Tema secimi</span>
                <select name="themePreference" value={form.themePreference} onChange={handleChange}>
                  <option value="system">Sistem varsayilani</option>
                  <option value="light">Light</option>
                  <option value="dark">Dark</option>
                </select>
              </label>
            </div>

            <div className="profile-note">
              Tema secimi aninda uygulanir ve cihazda saklanir. Profil tercihi daha sonra backend preference akisina baglanabilir.
            </div>

            <div className="profile-note">
              Aktif tema: <strong>{resolvedTheme === "dark" ? "Dark" : "Light"}</strong>
            </div>

            <div className="actions-row">
              <button type="submit" disabled={submitting}>
                {submitting ? "Kaydediliyor..." : "Degisiklikleri kaydet"}
              </button>
            </div>
          </form>

          <section className="card profile-form-card settings-session-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Keycloak Oturumu</p>
                <h3>Oturum bilgileri</h3>
              </div>
              <span className={`portfolio-status-pill ${hasAuthenticatedSession ? "is-live" : "is-unavailable"}`}>
                {sessionDetails.authenticated}
              </span>
            </div>

            <div className="profile-stat-grid">
              <div className="profile-stat">
                <span>Session State</span>
                <strong>{sessionDetails.sessionState}</strong>
              </div>
              <div className="profile-stat">
                <span>Token expiry</span>
                <strong>{sessionDetails.tokenExpiry}</strong>
              </div>
              <div className="profile-stat">
                <span>Subject</span>
                <strong>{sessionDetails.subject}</strong>
              </div>
              <div className="profile-stat">
                <span>Authenticated</span>
                <strong>{sessionDetails.authenticated}</strong>
              </div>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
