import { Crown, Lock } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../auth/AuthContext";

export default function AiLockedCard({
  featureName,
  description = "",
  requiresPremium = false,
}) {
  const navigate = useNavigate();
  const { isAuthenticated, login } = useAuth();
  const { t } = useTranslation();

  const isGuest = !isAuthenticated;
  const message = isGuest
    ? t("aiCards.locked.guestMessage")
    : t("aiCards.locked.premiumMessage");
  const ctaLabel = isGuest ? t("aiCards.locked.loginCta") : t("aiCards.locked.upgradeCta");

  function handleAction() {
    if (isGuest) {
      login();
      return;
    }
    navigate("/profile");
  }

  return (
    <section className="ai-card ai-premium-gate-card" aria-label={t("aiCards.locked.ariaLabel", { name: featureName })}>
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
              {requiresPremium ? t("aiCards.locked.premiumBadge") : t("aiCards.locked.loginBadge")}
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
