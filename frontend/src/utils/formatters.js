export function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString();
}

const ISO_CURRENCY_CODES = new Set(["TRY", "USD", "EUR", "GBP"]);
const INDEX_CODES = new Set(["BIST100", "BIST30", "BIST50", "XU100", "XU030", "XU050"]);

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

export function isPointBasedInstrument(instrumentOrType) {
  const instrumentType = typeof instrumentOrType === "string"
    ? instrumentOrType
    : instrumentOrType?.instrumentType ?? instrumentOrType?.type;
  const displayUnit = typeof instrumentOrType === "object" ? instrumentOrType?.displayUnit : null;
  const code = typeof instrumentOrType === "object"
    ? instrumentOrType?.symbol ?? instrumentOrType?.code ?? instrumentOrType?.instrumentCode
    : null;
  return String(displayUnit || "").trim().toUpperCase() === "POINT"
    || String(instrumentType || "").trim().toUpperCase() === "INDEX"
    || INDEX_CODES.has(String(code || "").trim().toUpperCase());
}

export function formatInstrumentValue(value, options = {}) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  const safeOptions = options ?? {};

  if (isPointBasedInstrument(safeOptions)) {
    const formatted = formatNumber(numeric, safeOptions.maximumFractionDigits ?? 2);
    if (safeOptions.includeUnit === false) {
      return formatted;
    }
    const locale = String(safeOptions.locale || "").toLowerCase();
    const unit = locale.startsWith("en") ? "pts" : "puan";
    return `${formatted} ${unit}`;
  }

  const currency = safeOptions.currency || "TRY";
  if (typeof safeOptions.currencyFormatter === "function") {
    return safeOptions.currencyFormatter(numeric);
  }
  return formatCurrency(numeric, currency);
}

export function parseFinancialNumber(value, unitMultiplier = 1) {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value === "string") {
    const normalized = value.trim();
    if (!normalized) return null;
    const negative = normalized.startsWith("(") && normalized.endsWith(")");
    const unsigned = normalized.replace(/[()]/g, "").trim();
    const trNumber = unsigned.includes(",")
      ? unsigned.replace(/\./g, "").replace(",", ".")
      : unsigned.replace(/\.(?=\d{3}(?:\.|$))/g, "");
    const parsed = Number(trNumber);
    return Number.isFinite(parsed) ? (negative ? -parsed : parsed) : null;
  }

  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return null;

  const multiplier = Number(unitMultiplier ?? 1);
  const rawText = String(value);
  const fractional = rawText.includes(".") ? rawText.split(".")[1] : "";
  if (Number.isFinite(multiplier) && multiplier >= 1000000 && fractional.length === 3) {
    return parsed * 1000;
  }

  return parsed;
}

export function formatTurkishNumber(value, maximumFractionDigits = 2) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  return numeric.toLocaleString("tr-TR", {
    maximumFractionDigits,
    minimumFractionDigits: 0,
  });
}

export function formatCompactFinancialValue(value, currency = "TRY") {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return "-";
  const abs = Math.abs(numeric);
  const units = [
    { threshold: 1000000000000, label: "Trilyon" },
    { threshold: 1000000000, label: "Milyar" },
    { threshold: 1000000, label: "Milyon" },
    { threshold: 1000, label: "Bin" },
  ];
  const unit = units.find((item) => abs >= item.threshold);
  const formatted = unit
    ? `${formatTurkishNumber(numeric / unit.threshold, 2)} ${unit.label}`
    : formatTurkishNumber(numeric, 2);
  return `${formatted} ${currency || ""}`.trim();
}

export function formatFinancialEquation(value, unitMultiplier = 1, currency = "TRY") {
  const baseValue = parseFinancialNumber(value, unitMultiplier);
  const multiplier = parseFinancialNumber(unitMultiplier);
  if (baseValue === null || multiplier === null) return { total: null, label: "-" };

  const total = baseValue * multiplier;
  return {
    total,
    label: `${formatTurkishNumber(baseValue, 3)} × ${formatTurkishNumber(multiplier, 0)} = ${formatCompactFinancialValue(total, currency)}`,
    fullLabel: `${formatTurkishNumber(total, 0)} ${currency || ""}`.trim(),
  };
}
