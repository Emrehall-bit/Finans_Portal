import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";

export async function getMarketQuotes() {
  const { data } = await axiosClient.get(API_CONFIG.ENDPOINTS.markets);
  return data;
}

export async function getMarketQuote(symbol) {
  const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.markets}/${encodeURIComponent(symbol)}`);
  return data;
}
