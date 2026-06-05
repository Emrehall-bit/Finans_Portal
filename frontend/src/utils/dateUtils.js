const NEWS_DATE_FORMATTER = new Intl.DateTimeFormat("tr-TR", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const BACKEND_DATE_WITH_TIMEZONE_RE = /(?:Z|[+-]\d{2}:\d{2})$/i;
const BACKEND_DATE_NEEDS_T_RE = /^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?$/;
const BACKEND_DATE_WITHOUT_TIMEZONE_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/;

export function parseBackendDate(value) {
  if (!value) {
    return null;
  }

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : new Date(value.getTime());
  }

  if (typeof value === "number") {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  if (typeof value !== "string") {
    return null;
  }

  const raw = value.trim();
  if (!raw) {
    return null;
  }

  let normalized = raw;
  if (BACKEND_DATE_NEEDS_T_RE.test(normalized)) {
    normalized = normalized.replace(/\s+/, "T");
  }

  if (BACKEND_DATE_WITHOUT_TIMEZONE_RE.test(normalized) && !BACKEND_DATE_WITH_TIMEZONE_RE.test(normalized)) {
    normalized = `${normalized}Z`;
  }

  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function formatNewsDate(value) {
  const parsed = parseBackendDate(value);
  if (!parsed) {
    return "";
  }
  return NEWS_DATE_FORMATTER.format(parsed);
}

export function formatNewsLocalDate(value) {
  if (!value) {
    return "";
  }

  if (value instanceof Date || typeof value === "number") {
    return formatNewsDate(value);
  }

  if (typeof value !== "string") {
    return "";
  }

  const raw = value.trim();
  if (!raw || BACKEND_DATE_WITH_TIMEZONE_RE.test(raw)) {
    return formatNewsDate(raw);
  }

  const normalized = raw.replace(/\s+/, "T");
  const match = normalized.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$/);
  if (!match) {
    return formatNewsDate(raw);
  }

  const [, year, month, day, hour, minute] = match;
  return `${day}.${month}.${year} ${hour}:${minute}`;
}

export function formatRelativeNewsTime(value, now = new Date()) {
  const parsed = parseBackendDate(value);
  const reference = parseBackendDate(now) ?? now;
  if (!parsed || !(reference instanceof Date) || Number.isNaN(reference.getTime())) {
    return "";
  }

  const diffMs = parsed.getTime() - reference.getTime();
  const diffMinutes = Math.round(diffMs / 60000);
  const diffHours = Math.round(diffMs / 3600000);
  const diffDays = Math.round(diffMs / 86400000);
  const rtf = new Intl.RelativeTimeFormat("tr-TR", { numeric: "auto" });

  if (Math.abs(diffMinutes) < 60) {
    return rtf.format(diffMinutes, "minute");
  }

  if (Math.abs(diffHours) < 24) {
    return rtf.format(diffHours, "hour");
  }

  return rtf.format(diffDays, "day");
}
