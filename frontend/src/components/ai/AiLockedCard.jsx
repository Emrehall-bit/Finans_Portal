import { Crown, Lock } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

export default function AiLockedCard({
  featureName,
  description = "",
  requiresPremium = false,
}) {
  const navigate = useNavigate();
  const { isAuthenticated, login } = useAuth();

  const isGuest = !isAuthenticated;
  const message = isGuest
    ? "Bu özelliği kullanmak için giriş yapmalısınız."
    : "Bu özellik premium kullanıcılar için geçerlidir.";
  const ctaLabel = isGuest ? "Giriş Yap" : "Premium'a Geç";

  function handleAction() {
    if (isGuest) {
      login();
      return;
    }
    navigate("/profile");
  }

  return (
    <section className="ai-card ai-premium-gate-card" aria-label={`${featureName} kilitli`}>
      <div className="ai-premium-gate-glow" aria-hidden="true" />

      <div className="ai-premium-gate-body">
        <span className="ai-premium-gate-lock" aria-hidden="true">
          <Lock size={20} />
        </span>

        <div className="ai-premium-gate-content">
          <div className="ai-premium-gate-top">
            <h3 className="ai-premium-gate-title">{featureName}</h3>
            <span className="ai-premium-upgrade-badge">
              {requiresPremium ? <Crown size={10} aria-hidden="true" /> : null}
              {requiresPremium ? "PREMIUM" : "GIRIS GEREKLI"}
            </span>
          </div>

          <p className="ai-premium-gate-desc">{message}</p>
          {description ? <p className="ai-premium-gate-hint">{description}</p> : null}

          <div className="ai-premium-gate-actions">
            <button type="button" className="ai-premium-gate-button" onClick={handleAction}>
              {ctaLabel}
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
