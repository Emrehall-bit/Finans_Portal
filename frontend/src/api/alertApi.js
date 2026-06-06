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

export async function updateAlert(userId, alertId, payload) {
  const response = await axiosClient.put(`${API_CONFIG.ENDPOINTS.alerts}/${userId}/${alertId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function deleteAlert(userId, alertId) {
  const response = await axiosClient.delete(`${API_CONFIG.ENDPOINTS.alerts}/${userId}/${alertId}`);
  return normalizeApiResponse(response).success;
}
