import axiosClient from "./axiosClient";
import { normalizeApiResponse } from "./responseUtils";

export async function getPremiumStatus() {
  const response = await axiosClient.get("/api/v1/premium/status");
  return normalizeApiResponse(response).data ?? null;
}

export async function requestPremiumUpgrade() {
  const response = await axiosClient.post("/api/v1/premium/upgrade");
  return normalizeApiResponse(response).data ?? null;
}

export async function cancelPremiumUpgrade() {
  const response = await axiosClient.post("/api/v1/premium/cancel");
  return normalizeApiResponse(response).data ?? null;
}

export async function getAdminPendingPremium() {
  const response = await axiosClient.get("/api/v1/admin/premium/pending");
  return normalizeApiResponse(response).data ?? [];
}

export async function adminApprovePremium(subscriptionId) {
  const response = await axiosClient.post(`/api/v1/admin/premium/${subscriptionId}/payment-success`);
  return normalizeApiResponse(response).data ?? null;
}

export async function adminRejectPremium(subscriptionId) {
  const response = await axiosClient.post(`/api/v1/admin/premium/${subscriptionId}/payment-fail`);
  return normalizeApiResponse(response).data ?? null;
}
