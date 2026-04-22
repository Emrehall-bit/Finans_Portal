import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: "http://localhost:8081",
  realm: "finance-portal",
  clientId: "finance-portal-backend",
});

let initPromise;
let initialized = false;

export function initKeycloak(options = {}) {
  if (initialized) {
    return Promise.resolve(Boolean(keycloak.authenticated));
  }

  if (!initPromise) {
    initPromise = keycloak.init({
      pkceMethod: "S256",
      checkLoginIframe: false,
      ...options,
    });
  }

  return initPromise
    .then((authenticated) => {
      initialized = true;
      return authenticated;
    })
    .catch((error) => {
      initPromise = undefined;
      throw error;
    });
}

export async function getValidAccessToken() {
  if (!keycloak.authenticated) {
    return null;
  }

  try {
    await keycloak.updateToken(30);
  } catch (error) {
    await logout();
    return null;
  }

  return keycloak.token ?? null;
}

export function isAuthenticated() {
  return Boolean(keycloak.authenticated);
}

export function isCallbackUrl() {
  const query = new URLSearchParams(window.location.search);
  return query.has("code") && query.has("state");
}

export function login(options = {}) {
  return keycloak.login({
    redirectUri: window.location.href,
    ...options,
  });
}

export function logout() {
  return keycloak.logout({
    redirectUri: window.location.origin,
  });
}

export default keycloak;
