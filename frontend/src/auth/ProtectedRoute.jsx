import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function ProtectedRoute() {
  const { authError, authLoading, ensureAuthenticated, initialized, isAuthenticated, userId } = useAuth();
  const [requested, setRequested] = useState(false);
  const location = useLocation();

  useEffect(() => {
    if (isAuthenticated && userId) {
      return;
    }

    let active = true;

    async function activateProtection() {
      try {
        await ensureAuthenticated();
      } catch {
        // Keycloak redirects on unauthenticated access.
      } finally {
        if (active) {
          setRequested(true);
        }
      }
    }

    activateProtection();

    return () => {
      active = false;
    };
  }, [ensureAuthenticated, isAuthenticated, location.pathname, userId]);

  if (isAuthenticated && userId) {
    return <Outlet />;
  }

  if (!initialized || authLoading || !requested) {
    return <div className="page-shell">Authenticating...</div>;
  }

  if (authError) {
    return <div className="page-shell">{authError}</div>;
  }

  return <div className="page-shell">Redirecting to login...</div>;
}
