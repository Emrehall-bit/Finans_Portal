import { useTranslation } from "react-i18next";
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function AdminRoute() {
  const { t } = useTranslation();
  const { initialized, isAdmin } = useAuth();

  if (!initialized) {
    return <div className="page-shell">{t("route.adminCheck")}</div>;
  }

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
}
