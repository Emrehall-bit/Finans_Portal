const I18N_LANGUAGE_STORAGE_KEY = "i18nextLng";

export function normalizeNewsLanguage(value) {
  if (value && value.startsWith("en")) return "en";
  return "tr";
}

export function getStoredNewsLanguage() {
  if (typeof window === "undefined") return "tr";
  const appLang = window.localStorage.getItem(I18N_LANGUAGE_STORAGE_KEY) || "tr";
  return normalizeNewsLanguage(appLang);
}

// no-op: language preference is now derived from the app language setting
export function persistNewsLanguage() {}
