import axiosClient from "./axiosClient";
import { API_CONFIG } from "./config";
import { normalizeApiResponse } from "./responseUtils";

function emptyNewsPage() {
  return {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
    hasNext: false,
    hasPrevious: false,
  };
}

function compactParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== "" && value !== null && value !== undefined)
  );
}

export async function getNews(params = {}) {
  const resolvedParams = compactParams(params);
  console.debug("News API request params", resolvedParams);
  const response = await axiosClient.get(API_CONFIG.ENDPOINTS.news, { params: resolvedParams });
  const data = normalizeApiResponse(response).data;

  if (Array.isArray(data)) {
    return {
      ...emptyNewsPage(),
      content: data,
      size: data.length || 20,
      totalElements: data.length,
      totalPages: data.length > 0 ? 1 : 0,
    };
  }

  if (data && typeof data === "object" && Array.isArray(data.content)) {
    const resolvedPage = Number.isFinite(data.page)
      ? data.page
      : Number.isFinite(data.number)
        ? data.number
        : 0;

    return {
      ...emptyNewsPage(),
      ...data,
      page: resolvedPage,
      hasNext: data.hasNext ?? !data.last,
      hasPrevious: data.hasPrevious ?? !data.first,
    };
  }

  return emptyNewsPage();
}

export async function getNewsDetail(id) {
  const response = await axiosClient.get(`${API_CONFIG.ENDPOINTS.news}/${id}`);
  return normalizeApiResponse(response).data ?? null;
}

export async function syncNews(params = {}) {
  const response = await axiosClient.post(`${API_CONFIG.ENDPOINTS.news}/sync`, null, { params: compactParams(params) });
  return normalizeApiResponse(response).data ?? null;
}
