import { formatDateTime } from "../../utils/formatters.js";

const PROVIDER_LABELS = {
  AA_RSS: "Anadolu Ajansı",
  CNBC_RSS: "CNBC",
  FINNHUB: "Finnhub",
  INVESTING_RSS: "Investing.com",
  KAP: "KAP",
  REUTERS_RSS: "Reuters",
};

const PROVIDER_INITIALS = {
  AA_RSS: "AA",
  CNBC_RSS: "CNBC",
  FINNHUB: "FH",
  INVESTING_RSS: "IN",
  KAP: "KAP",
  REUTERS_RSS: "RT",
};

const PROVIDER_DOMAINS = {
  AA_RSS: "aa.com.tr",
  CNBC_RSS: "cnbc.com",
  FINNHUB: "finnhub.io",
  INVESTING_RSS: "investing.com",
  KAP: "kap.org.tr",
  REUTERS_RSS: "reuters.com",
};

const QUALITY_LABELS = {
  FULL_CONTENT: "Tam metin",
  SUMMARY_ONLY: "Özet",
  SOURCE_LINK_ONLY: "Kaynak linki",
  KAP_DISCLOSURE: "KAP bildirimi",
};

const DISCLOSURE_TYPE_LABELS = {
  FINANCIAL: "Finansal rapor",
  RIGHTS: "Hak kullanımı",
  SPECIAL: "Özel durum",
  GENERAL: "Genel bildirim",
};

export function getNewsProviderLabel(provider) {
  const normalized = provider?.toUpperCase?.() || "";
  return PROVIDER_LABELS[normalized] || provider || "Bilinmeyen kaynak";
}

export function isKapDisclosure(item) {
  return Boolean(item?.isKapDisclosure) || String(item?.provider || "").toUpperCase() === "KAP";
}

export function getNewsSourceName(item) {
  return item?.sourceName?.trim() || getNewsProviderLabel(item?.provider || item?.source);
}

export function getNewsLanguageLabel(language) {
  return language ? language.toUpperCase() : null;
}

export function getNewsSummaryText(summary, fallback = "Özet bilgisi bulunmuyor.") {
  return summary?.trim() || fallback;
}

export function getNewsPreviewText(item, fallback = "İçerik önizlemesi bulunmuyor.") {
  const preview = item?.contentPreview?.trim();
  if (preview) {
    return preview;
  }

  const summary = item?.summary?.trim();
  if (summary) {
    return summary;
  }

  return fallback;
}

export function formatNewsPublishedAt(value, emptyLabel = "Tarih bilgisi alınamadı") {
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

export function getNewsQualityStatusLabel(status) {
  const normalized = status?.toUpperCase?.() || "";
  return QUALITY_LABELS[normalized] || QUALITY_LABELS.SUMMARY_ONLY;
}

export function getNewsDisclosureTypeLabel(type) {
  const normalized = type?.toUpperCase?.() || "";
  return DISCLOSURE_TYPE_LABELS[normalized] || DISCLOSURE_TYPE_LABELS.GENERAL;
}

export function buildNewsPlaceholderLabel(item) {
  const provider = item?.provider?.toUpperCase?.() || "";
  if (PROVIDER_INITIALS[provider]) {
    return PROVIDER_INITIALS[provider];
  }

  const base = getNewsProviderLabel(item?.provider);
  return (
    base
      .split(/[\s_-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join("") || "N"
  );
}

export function getNewsFallbackLogoUrl(item) {
  const provider = item?.provider?.toUpperCase?.() || "";
  const providerDomain = PROVIDER_DOMAINS[provider];
  const articleUrl = item?.sourceUrl || item?.url || "";

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

export function getNewsSourceUrl(item) {
  return item?.sourceUrl || item?.url || null;
}

export function shouldShowSourceCta(item) {
  const status = String(item?.qualityStatus || "").toUpperCase();
  return status === "SOURCE_LINK_ONLY" || status === "SUMMARY_ONLY" || isKapDisclosure(item);
}

export function getNewsPrimaryActionLabel(item, fallback = "Haberi aç") {
  return shouldShowSourceCta(item) ? "Kaynakta oku" : fallback;
}

export function getImportanceTone(score) {
  const numeric = Number(score);
  if (!Number.isFinite(numeric)) {
    return "neutral";
  }

  if (numeric >= 8) {
    return "high";
  }

  if (numeric >= 5) {
    return "medium";
  }

  return "low";
}

export function getImportanceLevelKey(score) {
  const tone = getImportanceTone(score);
  if (tone === "high") {
    return "high";
  }

  if (tone === "medium") {
    return "medium";
  }

  return "low";
}
