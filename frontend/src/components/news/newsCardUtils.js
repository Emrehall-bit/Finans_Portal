import { formatDateTime } from "../../utils/formatters";

const PROVIDER_LABELS = {
  BLOOMBERG_HT: "Bloomberg HT",
  AA_RSS: "Anadolu Ajansı",
  FINNHUB: "Finnhub",
};

const PROVIDER_INITIALS = {
  BLOOMBERG_HT: "BH",
  AA_RSS: "AA",
  FINNHUB: "FH",
};

export function getNewsProviderLabel(provider) {
  const normalized = provider?.toUpperCase?.() || "";
  return PROVIDER_LABELS[normalized] || provider || "Bilinmeyen kaynak";
}

export function getNewsLanguageLabel(language) {
  return language ? language.toUpperCase() : null;
}

export function getNewsSummaryText(summary, fallback = "Özet bilgisi bulunmuyor.") {
  return summary?.trim() || fallback;
}

export function formatNewsPublishedAt(value, emptyLabel = "Tarih bilgisi alınamadı") {
  return value ? formatDateTime(value) : emptyLabel;
}

export function buildNewsPlaceholderLabel(item) {
  const provider = item?.provider?.toUpperCase?.() || "";
  if (PROVIDER_INITIALS[provider]) {
    return PROVIDER_INITIALS[provider];
  }

  const base = getNewsProviderLabel(item?.provider);
  return base
    .split(/[\s_-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "N";
}
