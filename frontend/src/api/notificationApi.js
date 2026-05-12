import axiosClient from "./axiosClient";
import { normalizeApiResponse } from "./responseUtils";

export async function sendNotificationToUser(userId, payload) {
  const response = await axiosClient.post(`/api/v1/admin/notifications/user/${userId}`, payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function sendBroadcastNotification(payload) {
  const response = await axiosClient.post("/api/v1/admin/notifications/broadcast", payload);
  return normalizeApiResponse(response).data ?? null;
}

export async function getNotifications() {
  const response = await axiosClient.get("/api/v1/notifications");
  return normalizeApiResponse(response).data ?? [];
}

export async function getUnreadNotificationCount() {
  const response = await axiosClient.get("/api/v1/notifications/unread-count");
  return normalizeApiResponse(response).data?.count ?? 0;
}

export async function markNotificationAsRead(notificationId) {
  const response = await axiosClient.patch(`/api/v1/notifications/${notificationId}/read`);
  return normalizeApiResponse(response).data ?? null;
}

export async function markAllNotificationsAsRead() {
  const response = await axiosClient.patch("/api/v1/notifications/read-all");
  return normalizeApiResponse(response);
}
