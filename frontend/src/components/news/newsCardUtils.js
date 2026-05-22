import { formatDateTime } from "../../utils/formatters.js";

const PROVIDER_LABELS = {
  AA_RSS: "Anadolu Ajansı",
  CNBC_RSS: "CNBC",
  GUARDIAN: "The Guardian",
  KAP: "KAP",
};

const PROVIDER_INITIALS = {
  AA_RSS: "AA",
  CNBC_RSS: "CNBC",
  GUARDIAN: "G",
  KAP: "KAP",
};

const PROVIDER_DOMAINS = {
  AA_RSS: "aa.com.tr",
  CNBC_RSS: "cnbc.com",
  GUARDIAN: "theguardian.com",
  KAP: "kap.org.tr",
};

const QUALITY_LABELS = {
  FULL_CONTENT: "Tam metin",
  SUMMARY_ONLY: "Ozet",
  SOURCE_LINK_ONLY: "Kaynak linki",
  KAP_DISCLOSURE: "KAP bildirimi",
};

const DISCLOSURE_TYPE_LABELS = {
  FINANCIAL: "Finansal rapor",
  RIGHTS: "Hak kullanimi",
  SPECIAL: "Ozel durum",
  GENERAL: "Genel bildirim",
};

const CATEGORY_LABELS = {
  SPECIAL_DISCLOSURE: "Özel Durum",
  FINANCIAL_REPORT: "Finansal Rapor",
  DISCLOSURE: "KAP Bildirimi",
  GENERAL_MEETING: "Genel Kurul",
  ECONOMY: "Ekonomi",
  DIVIDEND: "Kar Payı",
  BUYBACK: "Geri Alım",
  CAPITAL_INCREASE: "Sermaye Artırımı",
};

const TAG_FALLBACK_MAP = {
  FX: "FX",
  GOLD_COMMODITY: "GOLD_COMMODITY",
  INTEREST_BONDS: "INTEREST_BONDS",
  STOCKS: "STOCKS",
  BANKING: "BANKING",
  CRYPTO: "CRYPTO",
  GLOBAL_MARKETS: "GLOBAL_MARKETS",
  ENERGY: "ENERGY",
  COMPANY: "STOCKS",
  GEOPOLITICS: "GLOBAL_MARKETS",
  GENERAL_ECONOMY: "GENERAL_ECONOMY",
  ECONOMY: "GENERAL_ECONOMY",
  GENERAL: "GENERAL_ECONOMY",
  BUSINESS: "GENERAL_ECONOMY",
  TOP_NEWS: "GLOBAL_MARKETS",
  MARKETS: "GLOBAL_MARKETS",
  FOREX: "FX",
  CURRENCY: "FX",
  DOVIZ: "FX",
  STOCK: "STOCKS",
  SHARES: "STOCKS",
  EQUITY: "STOCKS",
  INTEREST_RATE: "INTEREST_BONDS",
  BOND: "INTEREST_BONDS",
  TAHVIL: "INTEREST_BONDS",
  FAIZ: "INTEREST_BONDS",
  GOLD: "GOLD_COMMODITY",
  COMMODITY: "GOLD_COMMODITY",
  EMTIA: "GOLD_COMMODITY",
  ALTIN: "GOLD_COMMODITY",
  OIL: "ENERGY",
  PETROL: "ENERGY",
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

export function getNewsSummaryText(summary, fallback = "Ozet bilgisi bulunmuyor.") {
  return summary?.trim() || fallback;
}

export function getNewsPreviewText(item, fallback = "Icerik onizlemesi bulunmuyor.") {
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

export function formatNewsPublishedAt(value, emptyLabel = "Tarih bilgisi alinamadi") {
  return value ? formatDateTime(value) : emptyLabel;
}

export function getNewsCategoryLabel(category) {
  const normalized = category?.toUpperCase?.()?.replace(/[-\s]+/g, "_") || "";
  return CATEGORY_LABELS[normalized] || formatNewsCategoryLabel(category) || null;
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

export function getNewsPrimaryActionLabel(item, fallback = "Haberi ac") {
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

function normalizeNewsTagValue(value) {
  return value?.toUpperCase?.()?.replace(/[-\s]+/g, "_") || "";
}

function mapNewsTagValue(value) {
  const normalized = normalizeNewsTagValue(value);
  return TAG_FALLBACK_MAP[normalized] || null;
}

function resolveRawNewsTags(item) {
  if (Array.isArray(item?.tags) && item.tags.length > 0) {
    return item.tags;
  }
  if (item?.category) {
    return [item.category];
  }
  return [];
}

export function getNewsDisplayTags(item, t, limit = Infinity) {
  const seen = new Set();
  const resolved = [];

  for (const rawTag of resolveRawNewsTags(item)) {
    const canonicalTag = mapNewsTagValue(rawTag);
    if (!canonicalTag || seen.has(canonicalTag)) {
      continue;
    }
    seen.add(canonicalTag);
    resolved.push({
      key: canonicalTag,
      label: t(`news.tagLabels.${canonicalTag}`, { defaultValue: canonicalTag }),
    });
    if (resolved.length >= limit) {
      break;
    }
  }

  return resolved;
}
