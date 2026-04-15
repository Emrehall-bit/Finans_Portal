import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

export async function getNews(params = {}) {
  const response = await axiosClient.get(API_CONFIG.ENDPOINTS.news, { params });
  return normalizeApiResponse(response).data ?? [];
}

export async function getNewsDetail(id) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.news}/${id}`);
  return normalizeApiResponse(response).data ?? null;
}

export async function syncNews(params = {}) {
  const response = await axiosClient.post(`${API_CONFIG.ENDPOINTS.news}/sync`, null, { params });
  return normalizeApiResponse(response).data ?? null;
}
