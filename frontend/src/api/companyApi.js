import axiosClient from "./axiosClient";

function unwrap(payload) {
  if (payload?.data !== undefined && !Array.isArray(payload.data)) return payload.data;
  return payload;
}

export async function getCompanyFundamentals(ticker) {
  const { data } = await axiosClient.get(`/api/v1/companies/${encodeURIComponent(ticker)}/fundamentals`);
  return unwrap(data);
}

export async function getCompanyDisclosures(ticker, page = 0, size = 15) {
  const { data } = await axiosClient.get(`/api/v1/companies/${encodeURIComponent(ticker)}/disclosures`, {
    params: { page, size },
  });
  return unwrap(data);
}

export async function getCompanyFinancials(ticker) {
  const { data } = await axiosClient.get(`/api/v1/companies/${encodeURIComponent(ticker)}/financials`);
  return Array.isArray(data?.data) ? data.data : [];
}
