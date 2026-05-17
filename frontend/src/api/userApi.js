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
  const response = await axiosClient.get(`/api/v1/admin/users`);
  const data = normalizeApiResponse(response).data;
  return data ?? { content: [], totalElements: 0, totalPages: 0, size: 0, number: 0 };
}

export async function getUserById(userId) {
  const response = await axiosClient.get(`/api/v1/admin/users/${userId}`);
  return normalizeApiResponse(response).data ?? null;
}

export async function activateUser(userId) {
  const response = await axiosClient.patch(`/api/v1/admin/users/${userId}/activate`);
  return normalizeApiResponse(response).data ?? null;
}

export async function deactivateUser(userId) {
  const response = await axiosClient.patch(`/api/v1/admin/users/${userId}/deactivate`);
  return normalizeApiResponse(response).data ?? null;
}

export async function updateUserRole(userId, role) {
  const response = await axiosClient.patch(`/api/v1/admin/users/${userId}/role`, { role });
  return normalizeApiResponse(response).data ?? null;
}

export async function getUserModeration(userId) {
  const response = await axiosClient.get(`/api/v1/admin/users/${userId}/moderation`);
  return normalizeApiResponse(response).data ?? null;
}

export async function tempBlockUser(userId, payload) {
  const response = await axiosClient.patch(`/api/v1/admin/users/${userId}/temp-block`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function permBlockUser(userId, payload) {
  const response = await axiosClient.patch(`/api/v1/admin/users/${userId}/perm-block`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function unblockUser(userId, payload) {
  const response = await axiosClient.patch(`/api/v1/admin/users/${userId}/unblock`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function deleteCurrentUserAccount() {
  const response = await axiosClient.delete(`${API_CONFIG.ENDPOINTS.users}/me`);
  return normalizeApiResponse(response);
}
