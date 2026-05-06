export function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString();
}

const ISO_CURRENCY_CODES = new Set(["TRY", "USD", "EUR", "GBP"]);

export function formatNumber(value, maxFractionDigits = 4) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return String(value);
  return numeric.toLocaleString(undefined, { maximumFractionDigits: maxFractionDigits });
}

export function formatPercent(value, maxFractionDigits = 2) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return String(value);
  return `${numeric.toLocaleString(undefined, { maximumFractionDigits: maxFractionDigits })}%`;
}

export function formatCurrency(value, currency = "TRY") {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return String(value);

  const normalizedCurrency = typeof currency === "string" ? currency.trim().toUpperCase() : "";
  if (!ISO_CURRENCY_CODES.has(normalizedCurrency)) {
    const formattedNumber = numeric.toLocaleString(undefined, { maximumFractionDigits: 2 });
    return normalizedCurrency ? `${formattedNumber} ${normalizedCurrency}` : formattedNumber;
  }

  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency: normalizedCurrency }).format(numeric);
  } catch {
    return `${numeric.toLocaleString(undefined, { maximumFractionDigits: 2 })} ${normalizedCurrency}`;
  }
}
