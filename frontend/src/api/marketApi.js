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

export async function getTechnicalAnalysis(symbol, params) {
  const { data } = await axiosClient.get(
    `${API_CONFIG.ENDPOINTS.technicalAnalysis}/${encodeURIComponent(symbol)}`,
    { params },
  );
  return data;
}

export async function compareTechnicalAnalysis(params) {
  const { data } = await axiosClient.get(`${API_CONFIG.ENDPOINTS.technicalAnalysis}/compare`, {
    params,
  });
  return data;
}
