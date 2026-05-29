import Keycloak from "keycloak-js";
import { keycloakConfig } from "../config/keycloak-config";

const keycloak = new Keycloak({
  url: keycloakConfig.url,
  realm: keycloakConfig.realm,
  clientId: keycloakConfig.clientId,
});

let initPromise;
let initialized = false;
let handlersBound = false;
const authListeners = new Set();

function cleanupCallbackStorage() {
  if (typeof window === "undefined") {
    return;
  }

  const keysToRemove = [];
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (key?.startsWith("kc-callback-")) {
      keysToRemove.push(key);
    }
  }

  keysToRemove.forEach((key) => window.localStorage.removeItem(key));
}

function stripAuthParams(urlValue) {
  const url = new URL(urlValue);
  const searchParamsToRemove = ["code", "state", "session_state", "iss", "error", "error_description"];

  searchParamsToRemove.forEach((param) => url.searchParams.delete(param));

  if (url.hash.startsWith("#")) {
    const hashParams = new URLSearchParams(url.hash.slice(1));
    let mutated = false;

    searchParamsToRemove.forEach((param) => {
      if (hashParams.has(param)) {
        hashParams.delete(param);
        mutated = true;
      }
    });

    if (mutated) {
      const nextHash = hashParams.toString();
      url.hash = nextHash ? `#${nextHash}` : "";
    }
  }

  return url.toString();
}

function resolveAppRedirectUri(redirectUri) {
  if (typeof window === "undefined") {
    return redirectUri || keycloakConfig.appUrl;
  }

  if (redirectUri) {
    return stripAuthParams(redirectUri);
  }

  return stripAuthParams(window.location.href);
}

function resolveSilentCheckSsoRedirectUri() {
  const appUrl = resolveAppRedirectUri(keycloakConfig.appUrl || window.location.origin);
  return new URL("/silent-check-sso.html", appUrl).toString();
}

function cleanupBrowserCallbackUrl() {
  if (typeof window === "undefined") {
    return;
  }

  const cleanedUrl = stripAuthParams(window.location.href);
  if (cleanedUrl !== window.location.href) {
    window.history.replaceState(window.history.state, document.title, cleanedUrl);
  }
}

export function clearBrowserCallbackParams() {
  cleanupBrowserCallbackUrl();
}

function buildUserFromToken() {
  const claims = keycloak.idTokenParsed ?? keycloak.tokenParsed ?? {};
  const fullName =
    claims.name ||
    [claims.given_name, claims.family_name].filter(Boolean).join(" ").trim() ||
    claims.preferred_username ||
    claims.email ||
    "";

  if (!fullName && !claims.email && !claims.sub) {
    return null;
  }

  return {
    fullName: fullName || null,
    email: claims.email || null,
    keycloakId: claims.sub || null,
    role: null,
  };
}

export function getAuthSnapshot() {
  const hasToken = Boolean(keycloak.token);
  return {
    initialized,
    authenticated: hasToken,
    token: keycloak.token ?? null,
    idToken: keycloak.idToken ?? null,
    tokenParsed: keycloak.tokenParsed ?? null,
    idTokenParsed: keycloak.idTokenParsed ?? null,
    user: buildUserFromToken(),
  };
}

function notifyAuthListeners(event) {
  const snapshot = getAuthSnapshot();
  authListeners.forEach((listener) => listener(snapshot, event));
}

function bindKeycloakHandlers() {
  if (handlersBound) {
    return;
  }

  keycloak.onReady = () => {
    cleanupCallbackStorage();
    cleanupBrowserCallbackUrl();
    notifyAuthListeners("ready");
  };

  keycloak.onAuthSuccess = () => {
    cleanupCallbackStorage();
    cleanupBrowserCallbackUrl();
    notifyAuthListeners("auth-success");
  };

  keycloak.onAuthLogout = () => {
    cleanupCallbackStorage();
    notifyAuthListeners("auth-logout");
  };

  keycloak.onAuthRefreshSuccess = () => {
    notifyAuthListeners("auth-refresh-success");
  };

  keycloak.onAuthRefreshError = () => {
    notifyAuthListeners("auth-refresh-error");
  };

  keycloak.onTokenExpired = () => {
    notifyAuthListeners("token-expired");
  };

  handlersBound = true;
}

export function subscribeToAuthEvents(listener) {
  authListeners.add(listener);
  return () => {
    authListeners.delete(listener);
  };
}

export function initKeycloak(options = {}) {
  bindKeycloakHandlers();

  if (initialized) {
    return Promise.resolve(Boolean(keycloak.authenticated));
  }

  if (!initPromise) {
    initPromise = keycloak.init({
      onLoad: "check-sso",
      flow: "standard",
      scope: "openid profile email",
      pkceMethod: "S256",
      checkLoginIframe: false,
      responseMode: "query",
      silentCheckSsoRedirectUri: resolveSilentCheckSsoRedirectUri(),
      redirectUri: resolveAppRedirectUri(),
      ...options,
    });
  }

  return initPromise
    .then(() => {
      initialized = true;
      cleanupCallbackStorage();
      return getAuthSnapshot().authenticated;
    })
    .catch((error) => {
      initPromise = undefined;
      initialized = false;
      throw error instanceof Error ? error : new Error("Keycloak initialization failed");
    });
}

export async function getValidAccessToken() {
  if (!keycloak.token) {
    return null;
  }

  if (!keycloak.isTokenExpired(30)) {
    return keycloak.token;
  }

  try {
    await keycloak.updateToken(30);
    return keycloak.token ?? null;
  } catch {
    return keycloak.token ?? null;
  }
}

export function isAuthenticated() {
  return Boolean(keycloak.token);
}

export function login(options = {}) {
  return keycloak.login({
    redirectUri: resolveAppRedirectUri(options.redirectUri),
    ...options,
  });
}

export function register(options = {}) {
  return keycloak.login({
    action: "register",
    redirectUri: resolveAppRedirectUri(options.redirectUri),
    ...options,
  });
}

export function logout(options = {}) {
  cleanupCallbackStorage();
  cleanupBrowserCallbackUrl();
  return keycloak.logout({
    redirectUri:
      typeof window !== "undefined"
        ? window.location.origin
        : resolveAppRedirectUri(options.redirectUri || keycloakConfig.appUrl),
    ...options,
  });
}

export function logoutToUrl(redirectUrl) {
  return logout({
    redirectUri: redirectUrl || resolveAppRedirectUri(),
  });
}

export default keycloak;
