import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

export async function getTcmbMarketData() {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/tcmb`);
  return normalizeApiResponse(response).data ?? [];
}
