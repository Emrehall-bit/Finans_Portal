import axios from "axios";
import { API_CONFIG } from "./config";

const axiosClient = axios.create({
  baseURL: API_CONFIG.BASE_URL,
  timeout: 10000,
});

export default axiosClient;
