import axios from "axios";
import { API_CONFIG } from "./config";
import { getValidAccessToken, isAuthenticated, logout } from "../auth/keycloak";

const axiosClient = axios.create({
  baseURL: API_CONFIG.BASE_URL,
  timeout: 10000,
});

axiosClient.interceptors.request.use(async (config) => {
  const nextConfig = { ...config };
  const token = await getValidAccessToken();

  if (token) {
    nextConfig.headers = {
      ...nextConfig.headers,
      Authorization: `Bearer ${token}`,
    };
  }

  return nextConfig;
});

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error?.response?.status === 401 && isAuthenticated()) {
      await logout();
    }

    return Promise.reject(error);
  },
);

export default axiosClient;
