import axiosClient from "./axiosClient";

export async function getMacroIndicators() {
  const r = await axiosClient.get("/api/v1/markets/macro/indicators");
  return Array.isArray(r.data) ? r.data : [];
}

export async function getMacroObservations(code, valueType) {
  const r = await axiosClient.get(
    `/api/v1/markets/macro/indicators/${encodeURIComponent(code)}/observations`,
    { params: { valueType } },
  );
  return Array.isArray(r.data) ? r.data : [];
}
