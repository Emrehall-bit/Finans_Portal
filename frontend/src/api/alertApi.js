import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

export async function getUserAlerts(userId) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.alerts}/user/${userId}`);
  return normalizeApiResponse(response).data ?? [];
}

export async function createAlert(userId, payload) {
  const response = await axiosClient.post(`${API_CONFIG.ENDPOINTS.alerts}/${userId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function cancelAlert(userId, alertId) {
  const response = await axiosClient.patch(`${API_CONFIG.ENDPOINTS.alerts}/${userId}/${alertId}/cancel`);
  return normalizeApiResponse(response).success;
}
