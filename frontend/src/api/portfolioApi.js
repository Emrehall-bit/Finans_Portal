import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

export async function getUserPortfolios(userId) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.portfolios}/user/${userId}`);
  return normalizeApiResponse(response).data ?? [];
}

export async function createPortfolio(userId, payload) {
  const response = await axiosClient.post(`${API_CONFIG.ENDPOINTS.portfolios}/${userId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function getPortfolioById(portfolioId) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.portfolios}/${portfolioId}`);
  return normalizeApiResponse(response).data ?? null;
}

export async function updatePortfolio(portfolioId, payload) {
  const response = await axiosClient.put(`${API_CONFIG.ENDPOINTS.portfolios}/${portfolioId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function getPortfolioSummary(portfolioId) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.portfolios}/${portfolioId}/summary`);
  return normalizeApiResponse(response).data ?? null;
}

export async function getPortfolioDetails(portfolioId) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.portfolios}/${portfolioId}/details`);
  return normalizeApiResponse(response).data ?? null;
}

// Holding management now maps directly to the backend's portfolio holding endpoints.
export async function createPortfolioHolding(portfolioId, payload) {
  const response = await axiosClient.post(`${API_CONFIG.ENDPOINTS.portfolioHoldings}/${portfolioId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function updatePortfolioHolding(portfolioId, holdingId, payload) {
  const response = await axiosClient.put(`${API_CONFIG.ENDPOINTS.portfolioHoldings}/${portfolioId}/${holdingId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function deletePortfolioHolding(portfolioId, holdingId) {
  const response = await axiosClient.delete(`${API_CONFIG.ENDPOINTS.portfolioHoldings}/${portfolioId}/${holdingId}`);
  return normalizeApiResponse(response).data ?? null;
}

export async function getPortfolioHoldings(portfolioId) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.portfolioHoldings}/portfolio/${portfolioId}`);
  return normalizeApiResponse(response).data ?? [];
}
