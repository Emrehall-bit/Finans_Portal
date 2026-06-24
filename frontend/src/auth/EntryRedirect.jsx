import { Navigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "./AuthContext";

export default function EntryRedirect() {
  const { initialized } = useAuth();
  const { t } = useTranslation();

  if (!initialized) {
    return <div className="page-shell">{t("common.loading")}</div>;
  }

  return <Navigate to="/dashboard" replace />;
}
