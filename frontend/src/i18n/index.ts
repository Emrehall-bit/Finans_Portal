import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "../locales/en.json";
import tr from "../locales/tr.json";

const LANGUAGE_STORAGE_KEY = "financePortal.language";
const SUPPORTED_LANGUAGES = new Set(["tr", "en"]);

function getInitialLanguage() {
  if (typeof window === "undefined") {
    return "tr";
  }

  const storedLanguage = window.localStorage.getItem(LANGUAGE_STORAGE_KEY);
  return SUPPORTED_LANGUAGES.has(storedLanguage || "") ? storedLanguage : "tr";
}

if (!i18n.isInitialized) {
  i18n
    .use(initReactI18next)
    .init({
      resources: {
        tr: { translation: tr },
        en: { translation: en },
      },
      lng: getInitialLanguage(),
      fallbackLng: "tr",
      supportedLngs: ["tr", "en"],
      defaultNS: "translation",
      interpolation: {
        escapeValue: false,
      },
      returnNull: false,
      returnEmptyString: false,
      missingKeyHandler: false,
    });

  if (typeof window !== "undefined") {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, i18n.language || "tr");
    i18n.on("languageChanged", (language) => {
      if (SUPPORTED_LANGUAGES.has(language)) {
        window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
      }
    });
  }
}

export { LANGUAGE_STORAGE_KEY };
export default i18n;
