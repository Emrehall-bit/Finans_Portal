import { createContext, useContext, useEffect, useMemo, useState } from "react";

const THEME_STORAGE_KEY = "finance-portal-theme";
const ThemeContext = createContext(null);

const CHART_THEME_BY_MODE = {
  light: {
    axis: "#475569",
    grid: "rgba(100, 116, 139, 0.24)",
    legendText: "#1e293b",
    priceLine: "#1d4ed8",
    sma7Line: "#059669",
    sma20Line: "#2563eb",
    sma50Line: "#d97706",
    rsiLine: "#7c3aed",
    rsiUpperLine: "#dc2626",
    rsiLowerLine: "#059669",
    tooltipBg: "rgba(255, 255, 255, 0.98)",
    tooltipBorder: "rgba(100, 116, 139, 0.22)",
    tooltipText: "#14213f",
    tooltipContentStyle: {
      backgroundColor: "rgba(255, 255, 255, 0.98)",
      border: "1px solid rgba(148, 163, 184, 0.28)",
      borderRadius: "16px",
      color: "#14213f",
      boxShadow: "0 18px 40px rgba(15, 23, 42, 0.12)",
    },
    tooltipItemStyle: {
      color: "#14213f",
    },
    tooltipLabelStyle: {
      color: "#14213f",
      fontWeight: 700,
    },
  },
  dark: {
    axis: "#cbd5e1",
    grid: "rgba(148, 163, 184, 0.18)",
    legendText: "#e2e8f0",
    priceLine: "#f8fafc",
    sma7Line: "#34d399",
    sma20Line: "#60a5fa",
    sma50Line: "#f59e0b",
    rsiLine: "#a78bfa",
    rsiUpperLine: "#f87171",
    rsiLowerLine: "#34d399",
    tooltipBg: "rgba(15, 23, 42, 0.96)",
    tooltipBorder: "rgba(96, 165, 250, 0.18)",
    tooltipText: "#e2e8f0",
    tooltipContentStyle: {
      backgroundColor: "rgba(15, 23, 42, 0.96)",
      border: "1px solid rgba(96, 165, 250, 0.18)",
      borderRadius: "16px",
      color: "#e2e8f0",
      boxShadow: "0 18px 40px rgba(2, 6, 23, 0.42)",
    },
    tooltipItemStyle: {
      color: "#e2e8f0",
    },
    tooltipLabelStyle: {
      color: "#f8fafc",
      fontWeight: 700,
    },
  },
};

function getStoredThemePreference() {
  if (typeof window === "undefined") {
    return "system";
  }

  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  return stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
}

function getSystemTheme() {
  if (typeof window === "undefined") {
    return "light";
  }

  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function resolveTheme(themePreference, systemTheme) {
  return themePreference === "system" ? systemTheme : themePreference;
}

export function ThemeProvider({ children }) {
  const [themePreference, setThemePreferenceState] = useState(getStoredThemePreference);
  const [systemTheme, setSystemTheme] = useState(getSystemTheme);

  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");

    function handleChange(event) {
      setSystemTheme(event.matches ? "dark" : "light");
    }

    setSystemTheme(mediaQuery.matches ? "dark" : "light");

    if (typeof mediaQuery.addEventListener === "function") {
      mediaQuery.addEventListener("change", handleChange);
      return () => mediaQuery.removeEventListener("change", handleChange);
    }

    mediaQuery.addListener(handleChange);
    return () => mediaQuery.removeListener(handleChange);
  }, []);

  const resolvedTheme = resolveTheme(themePreference, systemTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme;
    document.documentElement.dataset.themePreference = themePreference;
    document.documentElement.style.colorScheme = resolvedTheme;
    window.localStorage.setItem(THEME_STORAGE_KEY, themePreference);
  }, [resolvedTheme, themePreference]);

  const value = useMemo(
    () => ({
      resolvedTheme,
      themePreference,
      setThemePreference(nextTheme) {
        const normalized = nextTheme === "light" || nextTheme === "dark" || nextTheme === "system" ? nextTheme : "system";
        setThemePreferenceState(normalized);
      },
      chartTheme: CHART_THEME_BY_MODE[resolvedTheme] ?? CHART_THEME_BY_MODE.light,
    }),
    [resolvedTheme, themePreference],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);

  if (!context) {
    throw new Error("useTheme must be used within ThemeProvider.");
  }

  return context;
}
