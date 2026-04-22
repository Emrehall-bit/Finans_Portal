import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { getCurrentUserProfile } from "../api/userApi";
import keycloak, { initKeycloak, isAuthenticated as hasAuthenticatedSession, isCallbackUrl, login, logout } from "./keycloak";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(true);
  const [authLoading, setAuthLoading] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [userProfile, setUserProfile] = useState(null);
  const [authError, setAuthError] = useState("");
  const bootstrapRef = useRef(null);

  const loadUserProfile = useCallback(async () => {
    const profile = await getCurrentUserProfile();
    setUserProfile(profile);
    return profile;
  }, []);

  const ensureAuthenticated = useCallback(async () => {
    if (bootstrapRef.current) {
      return bootstrapRef.current;
    }

    const bootstrap = async () => {
      setInitialized(false);
      setAuthLoading(true);
      setAuthError("");

      try {
        const authenticated = await initKeycloak({
          onLoad: "login-required",
        });

        setIsAuthenticated(Boolean(authenticated));

        if (authenticated) {
          await loadUserProfile();
        } else {
          setUserProfile(null);
        }

        return Boolean(authenticated);
      } catch (error) {
        setIsAuthenticated(false);
        setUserProfile(null);
        setAuthError(error?.message || "Authentication could not be initialized.");
        throw error;
      } finally {
        setInitialized(true);
        setAuthLoading(false);
        bootstrapRef.current = null;
      }
    };

    bootstrapRef.current = bootstrap();
    return bootstrapRef.current;
  }, [loadUserProfile]);

  const handleLogin = useCallback(async () => {
    setAuthError("");

    if (!keycloak.authenticated) {
      await initKeycloak({
        onLoad: "check-sso",
      });
    }

    return login();
  }, []);
  const handleLogout = useCallback(async () => {
    setIsAuthenticated(false);
    setUserProfile(null);
    setAuthError("");
    await logout();
  }, []);

  useEffect(() => {
    let active = true;

    async function restoreSessionFromCallback() {
      if (!isCallbackUrl()) {
        return;
      }

      try {
        setInitialized(false);
        setAuthLoading(true);
        setAuthError("");

        const authenticated = await initKeycloak({
          onLoad: "login-required",
        });

        if (!active) {
          return;
        }

        setIsAuthenticated(Boolean(authenticated));

        if (authenticated) {
          const profile = await getCurrentUserProfile();
          if (!active) {
            return;
          }
          setUserProfile(profile);
        } else {
          setUserProfile(null);
        }
      } catch (error) {
        if (!active) {
          return;
        }
        setIsAuthenticated(false);
        setUserProfile(null);
        setAuthError(error?.message || "Authentication could not be initialized.");
      } finally {
        if (active) {
          setInitialized(true);
          setAuthLoading(false);
        }
      }
    }

    restoreSessionFromCallback();

    return () => {
      active = false;
    };
  }, []);

  const value = {
    initialized,
    authLoading,
    isAuthenticated,
    authError,
    keycloak,
    token: keycloak.token ?? null,
    login: handleLogin,
    logout: handleLogout,
    ensureAuthenticated,
    userProfile,
    user: userProfile?.user ?? null,
    userId: userProfile?.user?.id ?? null,
    hasAuthenticatedSession: isAuthenticated || hasAuthenticatedSession(),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}
