import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { getCurrentUserProfile, updateCurrentUserProfile } from "../api/userApi";
import keycloak, {
  clearBrowserCallbackParams,
  getAuthSnapshot,
  initKeycloak,
  isAuthenticated as hasAuthenticatedSession,
  login,
  logout,
  register,
  subscribeToAuthEvents,
} from "./keycloak";

const AuthContext = createContext(null);

function normalizeRole(role) {
  return String(role || "").trim().toUpperCase();
}

function resolveTokenRoles() {
  const realmRoles = keycloak.tokenParsed?.realm_access?.roles ?? [];
  const clientRoles = Object.values(keycloak.tokenParsed?.resource_access ?? {}).flatMap((entry) => entry?.roles ?? []);
  return [...realmRoles, ...clientRoles]
    .map(normalizeRole)
    .filter(Boolean)
    .map((role) => (role.startsWith("ROLE_") ? role.slice(5) : role));
}

function resolvePrimaryRole(userProfileRole) {
  const rolePriority = ["ADMIN", "SYSTEM_ENGINEER", "USER_PREMIUM", "USER", "GUEST"];
  const tokenRoles = resolveTokenRoles();
  const explicitRole = normalizeRole(userProfileRole);
  const merged = explicitRole ? [explicitRole, ...tokenRoles] : tokenRoles;

  return rolePriority.find((role) => merged.includes(role)) ?? (merged[0] || "GUEST");
}

function buildRoleHelpers(userProfileRole) {
  const primaryRole = resolvePrimaryRole(userProfileRole);
  const knownRoles = new Set([primaryRole, ...resolveTokenRoles()]);

  const hasRole = (role) => knownRoles.has(normalizeRole(role));
  const hasAnyRole = (roles) => roles.some((role) => hasRole(role));

  return {
    roles: Array.from(knownRoles),
    primaryRole,
    hasRole,
    hasAnyRole,
    isAdmin: hasRole("ADMIN"),
    isPremium: hasAnyRole(["USER_PREMIUM", "ADMIN"]),
    isSystemEngineer: hasRole("SYSTEM_ENGINEER"),
  };
}

export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(false);
  const [authLoading, setAuthLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [userProfile, setUserProfile] = useState(null);
  const [authError, setAuthError] = useState("");
  const [authSnapshot, setAuthSnapshot] = useState(() => getAuthSnapshot());
  const bootstrapRef = useRef(null);

  const loadUserProfile = useCallback(async () => {
    const profile = await getCurrentUserProfile();
    setUserProfile(profile);
    return profile;
  }, []);

  const saveUserProfile = useCallback(async (payload) => {
    const profile = await updateCurrentUserProfile(payload);
    setUserProfile(profile);
    return profile;
  }, []);

  const bootstrapAuth = useCallback(async () => {
    if (bootstrapRef.current) {
      return bootstrapRef.current;
    }

    bootstrapRef.current = (async () => {
      setAuthLoading(true);
      setAuthError("");

      try {
        await initKeycloak();
        const snapshot = getAuthSnapshot();
        setAuthSnapshot(snapshot);
        setIsAuthenticated(snapshot.authenticated);

        if (snapshot.authenticated) {
          try {
            await loadUserProfile();
          } catch (error) {
            setUserProfile(null);
            setAuthError(error?.message || "Authenticated, but the user profile could not be loaded.");
          }
        } else {
          setUserProfile(null);
        }

        return snapshot.authenticated;
      } catch (error) {
        setIsAuthenticated(false);
        setUserProfile(null);
        setAuthError(error?.message || "Authentication could not be initialized.");
        return false;
      } finally {
        setInitialized(true);
        setAuthLoading(false);
        bootstrapRef.current = null;
      }
    })();

    return bootstrapRef.current;
  }, [loadUserProfile]);

  useEffect(() => {
    bootstrapAuth();
  }, [bootstrapAuth]);

  useEffect(() => {
    return subscribeToAuthEvents(async (snapshot, event) => {
      setAuthSnapshot(snapshot);
      setIsAuthenticated(snapshot.authenticated);

      if (event === "auth-logout") {
        clearBrowserCallbackParams();
        setAuthSnapshot(getAuthSnapshot());
        setUserProfile(null);
        setAuthError("");
        setInitialized(true);
        setAuthLoading(false);
        setIsAuthenticated(false);
        return;
      }

      if (event === "token-expired") {
        try {
          await keycloak.updateToken(30);
          const refreshedSnapshot = getAuthSnapshot();
          setAuthSnapshot(refreshedSnapshot);
          setIsAuthenticated(refreshedSnapshot.authenticated);
        } catch {
          setIsAuthenticated(false);
          setUserProfile(null);
          setAuthError("");
          await logout();
        }
        return;
      }

      if (!snapshot.authenticated) {
        setUserProfile(null);
        setAuthSnapshot(snapshot);
        clearBrowserCallbackParams();
        setAuthLoading(false);
        return;
      }

      setAuthError("");

      try {
        await loadUserProfile();
      } catch (error) {
        setUserProfile(null);
        setAuthError(error?.message || "Authenticated, but the user profile could not be loaded.");
      } finally {
        setInitialized(true);
        setAuthLoading(false);
      }
    });
  }, [loadUserProfile]);

  const ensureAuthenticated = useCallback(
    async (options = {}) => {
      const authenticated = initialized ? isAuthenticated : await bootstrapAuth();
      if (authenticated) {
        return true;
      }

      setAuthLoading(true);
      setAuthError("");
      await login(options);
      return false;
    },
    [bootstrapAuth, initialized, isAuthenticated],
  );

  const handleLogin = useCallback(async () => {
    setAuthError("");
    return ensureAuthenticated();
  }, [ensureAuthenticated]);

  const handleRegister = useCallback(() => {
    setAuthError("");
    return register({
      redirectUri: window.location.origin,
    });
  }, []);

  const handleLogout = useCallback(async () => {
    clearBrowserCallbackParams();
    setAuthLoading(false);
    setInitialized(true);
    setIsAuthenticated(false);
    setUserProfile(null);
    setAuthError("");
    setAuthSnapshot({
      ...getAuthSnapshot(),
      authenticated: false,
      token: null,
      idToken: null,
      user: null,
    });

    try {
      await logout({
        redirectUri: window.location.origin,
      });
    } catch {
      setAuthLoading(false);
    }
  }, []);

  const value = useMemo(
    () => {
      const roleHelpers = buildRoleHelpers(userProfile?.user?.role ?? authSnapshot.user?.role);

      return {
      initialized,
      authLoading,
      isAuthenticated,
      authError,
      keycloak,
      token: authSnapshot.token,
      idToken: authSnapshot.idToken,
      login: handleLogin,
      logout: handleLogout,
      register: handleRegister,
      ensureAuthenticated,
      refreshUserProfile: loadUserProfile,
      updateUserProfile: saveUserProfile,
      userProfile,
      user: isAuthenticated ? userProfile?.user ?? authSnapshot.user ?? null : null,
      userId: userProfile?.user?.id ?? null,
      role: roleHelpers.primaryRole,
      roles: roleHelpers.roles,
      hasRole: roleHelpers.hasRole,
      hasAnyRole: roleHelpers.hasAnyRole,
      isAdmin: roleHelpers.isAdmin,
      isPremium: roleHelpers.isPremium,
      isSystemEngineer: roleHelpers.isSystemEngineer,
      hasAuthenticatedSession: isAuthenticated || hasAuthenticatedSession(),
    };
    },
    [
      initialized,
      authLoading,
      isAuthenticated,
      authError,
      authSnapshot,
      handleLogin,
      handleLogout,
      handleRegister,
      ensureAuthenticated,
      loadUserProfile,
      saveUserProfile,
      userProfile?.user?.role,
      userProfile,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}
