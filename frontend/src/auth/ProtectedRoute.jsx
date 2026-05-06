import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function ProtectedRoute() {
  const { t } = useTranslation();
  const { authError, authLoading, ensureAuthenticated, initialized, isAuthenticated } = useAuth();
  const location = useLocation();

  useEffect(() => {
    if (!initialized || authLoading || isAuthenticated) {
      return;
    }

    ensureAuthenticated({
      redirectUri: window.location.origin + location.pathname + location.search + location.hash,
    }).catch(() => {});
  }, [authLoading, ensureAuthenticated, initialized, isAuthenticated, location.hash, location.pathname, location.search]);

  if (!initialized || authLoading) {
    return <div className="page-shell">{t("route.loading")}</div>;
  }

  if (isAuthenticated) {
    return <Outlet />;
  }

  if (authError) {
    return <div className="page-shell">{t("route.authRequired")}</div>;
  }

  return <div className="page-shell">{t("route.authRequired")}</div>;
}
