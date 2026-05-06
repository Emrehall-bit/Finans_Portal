import { formatDateTime } from "../../utils/formatters.js";

const PROVIDER_LABELS = {
  AA_RSS: "Anadolu Ajans\u0131",
  FINNHUB: "Finnhub",
  INVESTING_RSS: "Investing.com",
};

const PROVIDER_INITIALS = {
  AA_RSS: "AA",
  FINNHUB: "FH",
  INVESTING_RSS: "IN",
};

const PROVIDER_DOMAINS = {
  AA_RSS: "aa.com.tr",
  FINNHUB: "finnhub.io",
  INVESTING_RSS: "investing.com",
};

export function getNewsProviderLabel(provider) {
  const normalized = provider?.toUpperCase?.() || "";
  return PROVIDER_LABELS[normalized] || provider || "Bilinmeyen kaynak";
}

export function getNewsLanguageLabel(language) {
  return language ? language.toUpperCase() : null;
}

export function getNewsSummaryText(summary, fallback = "\u00d6zet bilgisi bulunmuyor.") {
  return summary?.trim() || fallback;
}

export function formatNewsPublishedAt(value, emptyLabel = "Tarih bilgisi al\u0131namad\u0131") {
  return value ? formatDateTime(value) : emptyLabel;
}

export function formatNewsCategoryLabel(category) {
  if (!category) {
    return null;
  }

  return category
    .replace(/[_-]+/g, " ")
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
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

export function getNewsFallbackLogoUrl(item) {
  const provider = item?.provider?.toUpperCase?.() || "";
  const providerDomain = PROVIDER_DOMAINS[provider];
  const articleUrl = item?.url || "";

  let domain = providerDomain;

  if (!domain && articleUrl) {
    try {
      domain = new URL(articleUrl).hostname;
    } catch {
      domain = "";
    }
  }

  if (!domain) {
    return null;
  }

  return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=128`;
}
