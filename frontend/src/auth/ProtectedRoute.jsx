import { useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function ProtectedRoute() {
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
    return <div className="page-shell">Yükleniyor...</div>;
  }

  if (isAuthenticated) {
    return <Outlet />;
  }

  if (authError) {
    return <div className="page-shell">Bu hizmeti görüntüleyebilmek için giriş yapmanız gerekmektedir.</div>;
  }

  return <div className="page-shell">Bu hizmeti görüntüleyebilmek için giriş yapmanız gerekmektedir.</div>;
}
