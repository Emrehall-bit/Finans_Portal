import { Lock } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../auth/AuthContext";

export default function GuestLockOverlay({
  children,
  className = "",
  compact = false,
  badgeClassName = "",
  onRequestAuth,
}) {
  const { isAuthenticated, login } = useAuth();
  const { t } = useTranslation();

  if (isAuthenticated) {
    return children;
  }

  return (
    <div className={`guest-lock-wrap ${className}`.trim()}>
      <div className="guest-lock-content" aria-hidden="true" inert="">
        {children}
      </div>
      <button
        type="button"
        className={`guest-lock-badge${compact ? " guest-lock-badge--compact" : ""}${badgeClassName ? ` ${badgeClassName}` : ""}`}
        onClick={() => (typeof onRequestAuth === "function" ? onRequestAuth() : login())}
        aria-label={t("layout.authRequired")}
      >
        <span className="guest-lock-icon">
          <Lock size={compact ? 12 : 11} strokeWidth={2.5} aria-hidden="true" />
        </span>
        {!compact ? <span className="guest-lock-hint">{t("layout.login")}</span> : null}
      </button>
    </div>
  );
}
