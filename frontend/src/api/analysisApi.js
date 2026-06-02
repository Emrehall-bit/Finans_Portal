import axiosClient from "./axiosClient";

export async function getAdvancedTechnical(instrumentCode, params = {}) {
  try {
    const res = await axiosClient.get(`/api/v1/technical-analysis/${encodeURIComponent(instrumentCode)}`, { params });
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Teknik veri alınamadı [${instrumentCode}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function getTechnicalCandles(symbol, params = {}, options = {}) {
  try {
    const res = await axiosClient.get(`/api/v1/technical-analysis/${encodeURIComponent(symbol)}/candles`, { params });
    return res.data?.data ?? res.data;
  } catch (err) {
    if (!options.silent) {
      console.error(`Mum verisi alinamadi [${symbol}]:`, err?.response?.status ?? err.message);
    }
    throw err;
  }
}
export async function getUserIndicators(instrumentCode) {
  try {
    const res = await axiosClient.get(`/api/v1/technical-analysis/${encodeURIComponent(instrumentCode)}/indicators`);
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Gösterge konfigürasyonları alınamadı [${instrumentCode}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function saveIndicatorConfig(instrumentCode, body) {
  try {
    const res = await axiosClient.post(`/api/v1/technical-analysis/${encodeURIComponent(instrumentCode)}/indicators`, body);
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Gösterge konfigürasyonu kaydedilemedi [${instrumentCode}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function deleteIndicatorConfig(id) {
  try {
    const res = await axiosClient.delete(`/api/v1/technical-analysis/indicators/${id}`);
    return res.data;
  } catch (err) {
    console.error(`Gösterge konfigürasyonu silinemedi [id=${id}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function getDrawings(instrumentCode, timeframe = "1d") {
  try {
    const res = await axiosClient.get(`/api/v1/analysis/drawings/${encodeURIComponent(instrumentCode)}`, {
      params: { timeframe },
    });
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Çizimler alınamadı [${instrumentCode}/${timeframe}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function saveDrawing(instrumentCode, body) {
  try {
    const res = await axiosClient.post(`/api/v1/analysis/drawings/${encodeURIComponent(instrumentCode)}`, body);
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Çizim kaydedilemedi [${instrumentCode}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function updateDrawing(id, body) {
  try {
    const res = await axiosClient.patch(`/api/v1/analysis/drawings/${id}`, body);
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Çizim güncellenemedi [id=${id}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function deleteDrawing(id) {
  try {
    const res = await axiosClient.delete(`/api/v1/analysis/drawings/${id}`);
    return res.data;
  } catch (err) {
    console.error(`Çizim silinemedi [id=${id}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function linkDrawingToAlert(drawingId, alertId) {
  try {
    const res = await axiosClient.post(`/api/v1/analysis/drawings/${drawingId}/link-alert`, { alertId });
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Çizim alert'e bağlanamadı [drawing=${drawingId}, alert=${alertId}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function getFundamentalRatios(instrumentCode) {
  try {
    const res = await axiosClient.get(`/api/v1/analysis/fundamental/${encodeURIComponent(instrumentCode)}`);
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Temel analiz oranları alınamadı [${instrumentCode}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function getFundamentalHistory(instrumentCode) {
  try {
    const res = await axiosClient.get(`/api/v1/analysis/fundamental/${encodeURIComponent(instrumentCode)}/history`);
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Temel analiz geçmişi alınamadı [${instrumentCode}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}

export async function getFinancialData(instrumentCode, periodType = "ANNUAL") {
  try {
    const res = await axiosClient.get(`/api/v1/analysis/fundamental/${encodeURIComponent(instrumentCode)}/financials`, {
      params: { periodType },
    });
    return res.data?.data ?? res.data;
  } catch (err) {
    console.error(`Ham finansal veriler alınamadı [${instrumentCode}/${periodType}]:`, err?.response?.status ?? err.message);
    throw err;
  }
}


