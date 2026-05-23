const FX_CODE_LABELS = {
  USD: "Dolar",
  EUR: "Euro",
  GBP: "Sterlin",
  AUD: "Avustralya Doları",
  CAD: "Kanada Doları",
  CHF: "Frank",
  JPY: "Yen",
  SAR: "Riyal",
  RUB: "Ruble",
  AZN: "Manat",
  CNY: "Yuan",
  QAR: "Riyal",
  KWD: "Dinar",
  KRW: "Won",
};

const INTERNAL_CASH_CODE = "TRY";
const INTERNAL_CASH_LABEL = "Nakit";

function extractFxCurrency(code) {
  if (!code) return null;
  const s = String(code);
  // "TCMB:USD:SELL" or "TCMB:USD:BUY"
  const colonMatch = s.match(/^TCMB:([A-Z]{2,4}):(BUY|SELL)$/i);
  if (colonMatch) return colonMatch[1].toUpperCase();
  // "TCMBUSDSELL" or "TCMBUSDBUYD" (packed, no separators)
  const packedMatch = s.match(/^TCMB([A-Z]{2,4})(BUY|SELL)$/i);
  if (packedMatch) return packedMatch[1].toUpperCase();
  return null;
}

/**
 * Returns the clean display symbol for an instrument code.
 * "TCMB:USD:SELL" → "USD"
 * "TCMBUSDSELL"   → "USD"
 * "TRY"           → "Nakit"
 * "THYAO"         → "THYAO"
 */
export function formatInstrumentCode(code) {
  if (!code) return "—";
  const currency = extractFxCurrency(code);
  if (currency) return currency;
  if (String(code).toUpperCase() === INTERNAL_CASH_CODE) return INTERNAL_CASH_LABEL;
  return String(code);
}

/**
 * Returns a rich label including the currency name for FX instruments.
 * "TCMB:USD:SELL" → "USD / Dolar"
 * "TCMBUSDSELL"   → "USD / Dolar"
 * "TRY"           → "Nakit"
 * "THYAO"         → "THYAO"
 */
export function formatInstrumentLabel(code) {
  if (!code) return "-";
  const currency = extractFxCurrency(code);
  if (currency) {
    const label = FX_CODE_LABELS[currency] || currency;
    return `${currency} / ${label}`;
  }
  if (String(code).toUpperCase() === INTERNAL_CASH_CODE) return INTERNAL_CASH_LABEL;
  return String(code);
}

/**
 * Resolves the sell/ask price from a market quote object.
 * Prefers sellRate, falls back to price.
 */
export function resolveSellPrice(quote) {
  if (!quote) return null;
  return quote.sellRate ?? quote.price ?? null;
}
