import { useAuth } from "../../auth/AuthContext";
import { useTranslation } from "react-i18next";

/**
 * Premium içerikleri blur veya gizleyerek korur.
 * Props:
 *   children    — premium olan içerik
 *   featureName — özelliğin adı (ör: "Graham Sayısı")
 *   previewMode — "blur" (default) veya "hide"
 */
export default function PremiumGate({ children, featureName = "", previewMode = "blur" }) {
  const { isPremium, isAuthenticated, login } = useAuth();
  const { t } = useTranslation();

  if (isPremium) {
    return <>{children}</>;
  }

  if (previewMode === "hide") {
    return (
      <div className="premium-gate-hidden">
        <button
          className="premium-gate-cta"
          onClick={isAuthenticated ? undefined : login}
        >
          🔒 {featureName ? `${featureName} — ` : ""}
          {t("premium.upgrade", "Premium'a Geç")}
        </button>
      </div>
    );
  }

  // Blur mod
  return (
    <div className="premium-gate-wrapper" style={{ position: "relative" }}>
      <div
        style={{
          filter: "blur(6px)",
          pointerEvents: "none",
          userSelect: "none",
          opacity: 0.5,
        }}
      >
        {children}
      </div>

      <div className="premium-gate-overlay">
        <div className="premium-gate-card">
          <span className="premium-gate-lock">🔒</span>
          <p className="premium-gate-title">
            {featureName
              ? t("premium.featureLocked", { feature: featureName })
              : t("premium.locked", "Bu özellik Premium'a özel")}
          </p>
          <p className="premium-gate-desc">
            {t("premium.upgradeDesc", "Tüm analizlere erişmek için Premium üye olun.")}
          </p>
          <a href="/premium" className="premium-gate-btn">
            {t("premium.upgradeCta", "Premium'a Geç →")}
          </a>
        </div>
      </div>
    </div>
  );
}
