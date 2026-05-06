const NEWS_LANGUAGE_STORAGE_KEY = "finance-portal-news-language";

export function normalizeNewsLanguage(value) {
  if (value === "en") {
    return "en";
  }
  if (value === "") {
    return "";
  }
  return "tr";
}

export function getStoredNewsLanguage() {
  if (typeof window === "undefined") {
    return "tr";
  }

  const storedValue = window.localStorage.getItem(NEWS_LANGUAGE_STORAGE_KEY);
  if (storedValue === null) {
    return "tr";
  }

  return normalizeNewsLanguage(storedValue);
}

export function persistNewsLanguage(value) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(NEWS_LANGUAGE_STORAGE_KEY, normalizeNewsLanguage(value));
}
