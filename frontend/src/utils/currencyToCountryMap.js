export const currencyToCountryMap = {
  TRY: "TR",
  USD: "US",
  EUR: "EU",
  AUD: "AU",
  GBP: "GB",
  JPY: "JP",
  CHF: "CH",
  CAD: "CA",
  CNY: "CN",
  AZN: "AZ",
  KRW: "KR",
  KWD: "KW",
  QAR: "QA",
  RUB: "RU",
};

function extractFxCurrencyCode(item) {
  const rawCode = item?.code;
  if (rawCode != null && String(rawCode).trim() !== "") {
    const upper = String(rawCode).trim().toUpperCase();
    if (/^[A-Z]{3}$/.test(upper)) {
      return upper;
    }
  }

  const rawSymbol = item?.symbol;
  if (rawSymbol == null || String(rawSymbol).trim() === "") {
    return null;
  }

  const sym = String(rawSymbol).trim().toUpperCase();
  if (sym.startsWith("TCMB:")) {
    const match = sym.match(/^TCMB:([A-Z]{3}):/);
    if (match?.[1]) {
      return match[1];
    }
  }

  return null;
}

export function getCountryCodeForInstrument(item, categoryFilter) {
  if (categoryFilter !== "FX") {
    return null;
  }

  const currencyCode = extractFxCurrencyCode(item);
  if (!currencyCode) {
    return null;
  }

  return currencyToCountryMap[currencyCode] ?? null;
}
