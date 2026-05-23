import { useTranslation } from "react-i18next";

export default function AuthRequiredModal({ isOpen, onClose, onConfirm }) {
  const { t } = useTranslation();

  if (!isOpen) {
    return null;
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="auth-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-required-title"
        onClick={(event) => event.stopPropagation()}
      >
        <p className="eyebrow">{t("layout.authRequired")}</p>
        <h3 id="auth-required-title">{t("layout.authRequiredTitle")}</h3>
        <p className="auth-modal-copy">{t("layout.authRequiredDescription")}</p>
        <div className="actions-row">
          <button type="button" className="secondary-button" onClick={onClose}>
            {t("common.cancel")}
          </button>
          <button type="button" onClick={onConfirm}>
            {t("layout.login")}
          </button>
        </div>
      </div>
    </div>
  );
}
