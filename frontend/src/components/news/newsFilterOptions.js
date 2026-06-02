export const ALL_CATEGORY_OPTION_VALUE = "__ALL__";

const FILTER_CATEGORY_CONFIG = [
  { value: "GENERAL_ECONOMY", labelKey: "news.filterCategories.generalEconomy" },
  { value: "STOCKS", labelKey: "news.filterCategories.stocks" },
  { value: "COMPANY", labelKey: "news.filterCategories.company" },
  { value: "FX", labelKey: "news.filterCategories.fx" },
  { value: "GOLD_COMMODITY", labelKey: "news.filterCategories.goldCommodities" },
  { value: "INTEREST_BONDS", labelKey: "news.filterCategories.interestBonds" },
  { value: "BANKING", labelKey: "news.filterCategories.banking" },
  { value: "CRYPTO", labelKey: "news.filterCategories.crypto" },
  { value: "GLOBAL_MARKETS", labelKey: "news.filterCategories.globalMarkets" },
];

const DISPLAY_CATEGORY_GROUPS = {
  GENERAL_ECONOMY: "GENERAL_ECONOMY",
  ECONOMY: "GENERAL_ECONOMY",
  GENERAL: "GENERAL_ECONOMY",
  BUSINESS: "GENERAL_ECONOMY",
  STOCKS: "STOCKS",
  STOCK: "STOCKS",
  SHARES: "STOCKS",
  EQUITY: "STOCKS",
  COMPANY: "COMPANY",
  FX: "FX",
  FOREX: "FX",
  CURRENCY: "FX",
  DOVIZ: "FX",
  GOLD_COMMODITY: "GOLD_COMMODITY",
  GOLD: "GOLD_COMMODITY",
  COMMODITY: "GOLD_COMMODITY",
  EMTIA: "GOLD_COMMODITY",
  ALTIN: "GOLD_COMMODITY",
  ENERGY: "GOLD_COMMODITY",
  INTEREST_BONDS: "INTEREST_BONDS",
  INTEREST_RATE: "INTEREST_BONDS",
  BOND: "INTEREST_BONDS",
  TAHVIL: "INTEREST_BONDS",
  FAIZ: "INTEREST_BONDS",
  BANKING: "BANKING",
  BANK: "BANKING",
  CRYPTO: "CRYPTO",
  CRYPTOCURRENCY: "CRYPTO",
  GLOBAL_MARKETS: "GLOBAL_MARKETS",
  MARKETS: "GLOBAL_MARKETS",
  TOP_NEWS: "GLOBAL_MARKETS",
  GEOPOLITICS: "GLOBAL_MARKETS",
};

const DISPLAY_CATEGORY_LABEL_KEYS = {
  GENERAL_ECONOMY: "news.filterCategories.generalEconomy",
  STOCKS: "news.filterCategories.stocks",
  COMPANY: "news.filterCategories.company",
  FX: "news.filterCategories.fx",
  GOLD_COMMODITY: "news.filterCategories.goldCommodities",
  INTEREST_BONDS: "news.filterCategories.interestBonds",
  BANKING: "news.filterCategories.banking",
  CRYPTO: "news.filterCategories.crypto",
  GLOBAL_MARKETS: "news.filterCategories.globalMarkets",
};

const PROVIDER_LABEL_KEYS = {
  AA_RSS: "news.filterProviders.aa",
  CNBC_RSS: "news.filterProviders.cnbc",
  GUARDIAN: "news.filterProviders.guardian",
  KAP: "news.filterProviders.kap",
};

export function getNewsCategoryFilterOptions(t) {
  return [
    { value: ALL_CATEGORY_OPTION_VALUE, label: t("news.filterCategories.all"), isAll: true },
    ...FILTER_CATEGORY_CONFIG.map((item) => ({
      value: item.value,
      label: t(item.labelKey),
    })),
  ];
}

export function getNewsCategoryFilterLabel(category, t) {
  const normalized = normalizeCategoryKey(category);
  const displayCategory = DISPLAY_CATEGORY_GROUPS[normalized];
  if (!displayCategory) {
    return null;
  }
  return t(DISPLAY_CATEGORY_LABEL_KEYS[displayCategory]);
}

export function getNewsProviderFilterOptions(providers, t) {
  return providers
    .filter(Boolean)
    .map((provider) => ({
      value: provider,
      label: t(PROVIDER_LABEL_KEYS[provider] || "news.filterProviders.unknown"),
    }));
}

export function getNewsProviderFilterLabel(provider, t) {
  if (!provider) {
    return null;
  }
  return t(PROVIDER_LABEL_KEYS[provider] || "news.filterProviders.unknown");
}

function normalizeCategoryKey(category) {
  return String(category || "")
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "_");
}
