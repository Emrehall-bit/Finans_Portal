import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

export async function getUserWatchlist(userId) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.watchlist}/user/${userId}`);
  return normalizeApiResponse(response).data ?? [];
}

export async function addWatchlistItem(userId, payload) {
  const response = await axiosClient.post(`${API_CONFIG.ENDPOINTS.watchlist}/${userId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function removeWatchlistItem(itemId) {
  const response = await axiosClient.delete(`${API_CONFIG.ENDPOINTS.watchlist}/${itemId}`);
  return normalizeApiResponse(response).success;
}
