import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

export async function getCurrentUserProfile() {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.users}/me`);
  return normalizeApiResponse(response).data ?? null;
}

export async function updateCurrentUserProfile(payload) {
  const response = await axiosClient.put(`${API_CONFIG.ENDPOINTS.users}/me`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function getAllUsers() {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.users}/admin`);
  return normalizeApiResponse(response).data ?? [];
}
