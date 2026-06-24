import { formatNewsDate, formatNewsLocalDate } from "../../utils/dateUtils.js";

const PROVIDER_LABELS = {
  AA_RSS: "Anadolu Ajansı",
  CNBC_RSS: "CNBC",
  GUARDIAN: "The Guardian",
  KAP: "KAP",
  SYSTEM_GENERATED: "Piyasa Sinyalleri (Fake)",
};

const PROVIDER_INITIALS = {
  AA_RSS: "AA",
  CNBC_RSS: "CNBC",
  GUARDIAN: "TG",
  KAP: "KP",
  SYSTEM_GENERATED: "FP",
};

const PROVIDER_BADGE_COLORS = {
  AA_RSS: "#1565c0",
  CNBC_RSS: "#c62828",
  GUARDIAN: "#005689",
  KAP: "#1a237e",
  SYSTEM_GENERATED: "#0f766e",
  REUTERS: "#ff6200",
  BBC: "#bb1919",
  BBC_RSS: "#bb1919",
  AFP: "#00529b",
  AFP_RSS: "#00529b",
  DW: "#00618f",
  DW_RSS: "#00618f",
  BLOOMBERG: "#2563a9",
  BLOOMBERG_RSS: "#2563a9",
  WORLDNEWSAPI: "#0d7377",
  WNA: "#0d7377",
};

const BADGE_COLOR_PALETTE = [
  "#2e63d8", "#0d7377", "#7b5ea7", "#c77b00",
  "#1565c0", "#c0392b", "#16a567", "#6d28d9",
];

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


export function getNewsProviderLabel(provider) {
  const normalized = provider?.toUpperCase?.() || "";
  return PROVIDER_LABELS[normalized] || formatRawProviderCode(provider) || provider || "Bilinmeyen kaynak";
}

export function getProviderBadgeColor(provider) {
  const normalized = provider?.toUpperCase?.() || "";
  if (PROVIDER_BADGE_COLORS[normalized]) return PROVIDER_BADGE_COLORS[normalized];
  if (!normalized) return BADGE_COLOR_PALETTE[0];
  let hash = 0;
  for (let i = 0; i < normalized.length; i++) hash = (hash * 31 + normalized.charCodeAt(i)) & 0xffff;
  return BADGE_COLOR_PALETTE[hash % BADGE_COLOR_PALETTE.length];
}

function formatRawProviderCode(raw) {
  if (!raw) return null;
  const cleaned = raw
    .replace(/[_-](rss\d*|api|feed|v\d+)$/i, "")
    .replace(/[_-]+/g, " ")
    .trim();
  if (!cleaned) return null;
  return cleaned
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => (w.length <= 5 ? w.toUpperCase() : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()))
    .join(" ");
}

export function isKapDisclosure(item) {
  return Boolean(item?.isKapDisclosure) || String(item?.provider || "").toUpperCase() === "KAP";
}

function usesLocalNewsTime(item) {
  const provider = String(item?.provider || item?.source || "").toUpperCase();
  return isKapDisclosure(item) || provider === "AA_RSS";
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

export function getNewsDateValue(item) {
  return item?.publishedAt || item?.createdAt || item?.updatedAt || null;
}

export function formatNewsPublishedAt(item, emptyLabel = "Tarih bilgisi alinamadi") {
  const formatter = usesLocalNewsTime(item) ? formatNewsLocalDate : formatNewsDate;
  const formatted = formatter(getNewsDateValue(item));
  return formatted || emptyLabel;
}

export function getNewsCategoryLabel(category) {
  const normalized = category?.toUpperCase?.()?.replace(/[-\s]+/g, "_") || "";
  return CATEGORY_LABELS[normalized] || formatNewsCategoryLabel(category) || null;
}

export function getNewsCategoryLabelI18n(category, t) {
  if (!category) return null;
  const normalized = category.toUpperCase().replace(/[-\s]+/g, "_");
  const translated = t(`news.newsCategories.${normalized}`, { defaultValue: "" });
  if (translated) return translated;
  return getNewsCategoryLabel(category);
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

  if (provider === "SYSTEM_GENERATED") {
    return "/finans-portali-logo.png";
  }

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

const KAP_DISCLOSURE_GROUPS = [
  {
    key: "menkul-kiymet",
    label: "Menkul Kıymet",
    test: (s) =>
      s.includes("Sermaye Piyasası Aracı") ||
      s.includes("Varant") ||
      s.includes("İhraç Tavanı"),
  },
  {
    key: "genel-kurul",
    label: "Genel Kurul",
    test: (s) => s.includes("Genel Kurul"),
  },
  {
    key: "derecelendirme",
    label: "Derecelendirme",
    test: (s) => s.includes("Derecelendirme") || s.includes("Bağımsız Denetim"),
  },
  {
    key: "kurumsal-eylem",
    label: "Kurumsal Eylem",
    test: (s) =>
      s.includes("Kar Payı") ||
      s.includes("Geri Alınma") ||
      s.includes("Sermaye Artırımı") ||
      s.includes("Kayıtlı Sermaye") ||
      s.includes("Birleşme"),
  },
];

export function normalizeKapText(text) {
  return String(text || "").trim().toLowerCase().replace(/\s+/g, " ").replace(/[.,;:!?]+$/, "");
}

export function extractKapSubtitle(title, symbol) {
  if (!title) return "";
  const prefix = symbol ? symbol + " - " : null;
  if (prefix && title.startsWith(prefix)) return title.substring(prefix.length);
  const idx = title.indexOf(" - ");
  return idx !== -1 ? title.substring(idx + 3) : title;
}

export function resolveKapDisclosureGroup(title) {
  if (!title || !title.includes(" - ")) {
    return { key: "diger", label: "Diğer" };
  }
  const subtitle = title.substring(title.indexOf(" - ") + 3).trim();
  for (const group of KAP_DISCLOSURE_GROUPS) {
    if (group.test(subtitle)) {
      return { key: group.key, label: group.label };
    }
  }
  return {
    key: "diger",
    label: subtitle.length > 38 ? subtitle.substring(0, 38) + "…" : subtitle,
  };
}
