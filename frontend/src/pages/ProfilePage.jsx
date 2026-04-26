import { useEffect, useState } from "react";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import PageHeader from "../components/common/PageHeader";
import { useAuth } from "../auth/AuthContext";
import useToast from "../hooks/useToast";
import { extractErrorMessage } from "../api/responseUtils";
import { formatDateTime } from "../utils/formatters";

function buildFormState(user) {
  return {
    fullName: user?.fullName ?? "",
    preferredLanguage: user?.preferredLanguage ?? "",
    themePreference: user?.themePreference ?? "",
  };
}

export default function ProfilePage() {
  const { userProfile, user, authLoading, updateUserProfile, refreshUserProfile } = useAuth();
  const [form, setForm] = useState(buildFormState(user));
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const { toast, showToast } = useToast();

  useEffect(() => {
    setForm(buildFormState(user));
  }, [user]);

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
          setError(extractErrorMessage(err, "Profil bilgileri yüklenemedi."));
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
      await updateUserProfile(form);
      showToast("success", "Profil bilgileri güncellendi.");
    } catch (err) {
      setError(extractErrorMessage(err, "Profil güncellenemedi."));
    } finally {
      setSubmitting(false);
    }
  }

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((current) => ({
      ...current,
      [name]: value,
    }));
  }

  return (
    <div className="profile-page-stack">
      <PageHeader
        eyebrow="Profile"
        title="Hesap Ayarları"
        description="Kendi profil bilgilerinizi görüntüleyin, adınızı ve portal tercihlerinizi güncelleyin."
      />

      {toast ? <div className={`status-box ${toast.type}`}>{toast.message}</div> : null}
      {error ? <ErrorMessage message={error} /> : null}
      {authLoading || loading ? <LoadingSpinner label="Profil yükleniyor..." /> : null}

      {!authLoading && !loading && user ? (
        <div className="profile-grid">
          <section className="card profile-summary-card">
            <p className="eyebrow">Kimlik</p>
            <div className="profile-summary-head">
              <div className="profile-avatar profile-avatar-large">
                {(user.fullName || user.email || "FP")
                  .split(" ")
                  .filter(Boolean)
                  .slice(0, 2)
                  .map((part) => part[0]?.toUpperCase() || "")
                  .join("")}
              </div>
              <div>
                <h3>{user.fullName || "Adsız kullanıcı"}</h3>
                <p>{user.email}</p>
              </div>
            </div>

            <div className="profile-stat-grid">
              <div className="profile-stat">
                <span>Rol</span>
                <strong>{user.role || "USER"}</strong>
              </div>
              <div className="profile-stat">
                <span>Auth Provider</span>
                <strong>{userProfile?.authProvider || "-"}</strong>
              </div>
              <div className="profile-stat">
                <span>Hesap Oluşturma</span>
                <strong>{formatDateTime(user.createdAt)}</strong>
              </div>
              <div className="profile-stat">
                <span>Keycloak ID</span>
                <strong>{user.keycloakId || "-"}</strong>
              </div>
            </div>
          </section>

          <form className="card profile-form-card" onSubmit={handleSubmit}>
            <div className="panel-head">
              <div>
                <p className="eyebrow">Düzenle</p>
                <h3>Profil Bilgileri</h3>
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
                <input value={user.email || ""} disabled readOnly />
              </label>

              <label className="profile-field">
                <span>Tercih Edilen Dil</span>
                <select name="preferredLanguage" value={form.preferredLanguage} onChange={handleChange}>
                  <option value="">Seçiniz</option>
                  <option value="tr">Türkçe</option>
                  <option value="en">English</option>
                </select>
              </label>

              <label className="profile-field">
                <span>Tema Tercihi</span>
                <select name="themePreference" value={form.themePreference} onChange={handleChange}>
                  <option value="">Sistem Varsayılanı</option>
                  <option value="light">Light</option>
                  <option value="dark">Dark</option>
                </select>
              </label>
            </div>

            <div className="profile-note">
              E-posta ve rol Keycloak ile senkronize edilir. Buradan yalnızca portal içi görünüm ve profil tercihleri güncellenir.
            </div>

            <div className="actions-row">
              <button type="submit" disabled={submitting}>
                {submitting ? "Kaydediliyor..." : "Değişiklikleri Kaydet"}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  );
}
