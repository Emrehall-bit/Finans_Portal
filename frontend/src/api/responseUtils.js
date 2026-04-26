export function normalizeApiResponse(response) {
  const payload = response?.data;

  if (Array.isArray(payload)) {
    return { success: true, data: payload, message: "" };
  }

  if (payload && typeof payload === "object" && "data" in payload) {
    return {
      success: payload.success !== false,
      data: payload.data,
      message: payload.message || "",
    };
  }

  return { success: true, data: payload, message: "" };
}

export function extractErrorMessage(error, fallback = "An unexpected error occurred.") {
  const apiMessage = error?.response?.data?.message;
  const problemDetail = error?.response?.data?.detail;
  const problemTitle = error?.response?.data?.title;
  const genericMessage = error?.message;
  return apiMessage || problemDetail || problemTitle || genericMessage || fallback;
}
