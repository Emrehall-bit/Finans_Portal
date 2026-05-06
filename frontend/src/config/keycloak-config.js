/**
 * Keycloak configuration
 */

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8081";
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || "finance-portal";
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "finance-portal-frontend";
const APP_URL = import.meta.env.VITE_APP_URL || "http://localhost:3000";

export const keycloakConfig = {
  url: KEYCLOAK_URL,
  realm: KEYCLOAK_REALM,
  clientId: KEYCLOAK_CLIENT_ID,
  appUrl: APP_URL,
};

export function getLoginUrl() {
  const redirectUri = encodeURIComponent(`${APP_URL}`);
  return `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth?client_id=${KEYCLOAK_CLIENT_ID}&response_type=code&scope=openid+profile+email&redirect_uri=${redirectUri}`;
}

export function getLogoutUrl() {
  const redirectUri = encodeURIComponent(`${APP_URL}`);
  return `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout?redirect_uri=${redirectUri}`;
}

export function getAccountUrl() {
  return `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/account`;
}
