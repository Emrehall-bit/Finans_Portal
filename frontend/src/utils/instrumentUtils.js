const FX_CODE_LABELS_BY_LOCALE = {
  tr: {
    USD: "Amerikan Doları",
    EUR: "Euro",
    GBP: "İngiliz Sterlini",
    AUD: "Avustralya Doları",
    CAD: "Kanada Doları",
    CHF: "İsviçre Frangı",
    JPY: "Japon Yeni",
    SAR: "Suudi Riyali",
    RUB: "Rus Rublesi",
    AZN: "Azerbaycan Manatı",
    CNY: "Çin Yuanı",
    QAR: "Katar Riyali",
    KWD: "Kuveyt Dinarı",
    KRW: "Güney Kore Wonu",
  },
  en: {
    USD: "US Dollar",
    EUR: "Euro",
    GBP: "British Pound",
    AUD: "Australian Dollar",
    CAD: "Canadian Dollar",
    CHF: "Swiss Franc",
    JPY: "Japanese Yen",
    SAR: "Saudi Riyal",
    RUB: "Russian Ruble",
    AZN: "Azerbaijani Manat",
    CNY: "Chinese Yuan",
    QAR: "Qatari Riyal",
    KWD: "Kuwaiti Dinar",
    KRW: "South Korean Won",
  },
};

const INTERNAL_CASH_CODE = "TRY";
const INTERNAL_CASH_LABEL = "Nakit";

function extractFxCurrency(code) {
  if (!code) return null;
  const s = String(code);
  const colonMatch = s.match(/^[A-Z_]+:([A-Z]{2,4}):(BUY|SELL)$/i);
  if (colonMatch) return colonMatch[1].toUpperCase();

  const packedMatch = s.match(/^[A-Z_]+([A-Z]{2,4})(BUY|SELL)$/i);
  if (packedMatch) return packedMatch[1].toUpperCase();

  return null;
}

function normalizeLocale(locale) {
  return String(locale || "tr").toLowerCase().startsWith("en") ? "en" : "tr";
}

export function formatInstrumentCode(code) {
  if (!code) return "—";
  const currency = extractFxCurrency(code);
  if (currency) return currency;
  if (String(code).toUpperCase() === INTERNAL_CASH_CODE) return INTERNAL_CASH_LABEL;
  return String(code);
}

export function getFxCodeLabel(code, locale = "tr") {
  const normalizedCode = String(code || "").trim().toUpperCase();
  const normalizedLocale = normalizeLocale(locale);
  return FX_CODE_LABELS_BY_LOCALE[normalizedLocale]?.[normalizedCode] || normalizedCode;
}

export function formatInstrumentLabel(code, locale = "tr") {
  if (!code) return "-";
  const currency = extractFxCurrency(code);
  if (currency) {
    const label = getFxCodeLabel(currency, locale);
    return `${currency} / ${label}`;
  }
  if (String(code).toUpperCase() === INTERNAL_CASH_CODE) return INTERNAL_CASH_LABEL;
  return String(code);
}

export function resolveSellPrice(quote) {
  if (!quote) return null;
  return quote.sellRate ?? quote.price ?? null;
}
