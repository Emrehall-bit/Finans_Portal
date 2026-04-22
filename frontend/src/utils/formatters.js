export function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString();
}

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
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(numeric);
}
