import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { getMarketBySymbol, getMarketHistory } from "../api/marketApi";
import { formatCurrency } from "../utils/formatters";

const CURRENCY_STORAGE_KEY = "fp:currency:v1";
const CurrencyContext = createContext(null);

export function CurrencyProvider({ children }) {
  const [currency, setCurrencyState] = useState(() => {
    try {
      return localStorage.getItem(CURRENCY_STORAGE_KEY) === "USD" ? "USD" : "TRY";
    } catch {
      return "TRY";
    }
  });
  const [usdRate, setUsdRate] = useState(null);

  useEffect(() => {
    let active = true;

    async function loadUsdRate() {
      // 1. Canlı quote dene (Redis'ten gelir)
      for (const sym of ["TCMB:USD:SELL", "USDTRY"]) {
        try {
          const data = await getMarketBySymbol(sym);
          const rate = Number(data?.price ?? data?.sellRate);
          if (Number.isFinite(rate) && rate > 1) {
            if (active) setUsdRate(rate);
            return;
          }
        } catch {
          // sonraki adıma geç
        }
      }

      // 2. DB'deki son kapanış fiyatına bak
      const today = new Date().toISOString().slice(0, 10);
      const monthAgo = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
      for (const sym of ["TCMB:USD:SELL", "USDTRY"]) {
        try {
          const history = await getMarketHistory(sym, { from: monthAgo, to: today });
          const lastPoint = Array.isArray(history) && history.length > 0 ? history[history.length - 1] : null;
          const rate = Number(lastPoint?.closePrice ?? lastPoint?.close ?? lastPoint?.price);
          if (Number.isFinite(rate) && rate > 1) {
            if (active) setUsdRate(rate);
            return;
          }
        } catch {
          // sonraki adıma geç
        }
      }
    }

    loadUsdRate();
    return () => {
      active = false;
    };
  }, []);

  const setCurrency = useCallback((next) => {
    const normalized = next === "USD" ? "USD" : "TRY";
    try {
      localStorage.setItem(CURRENCY_STORAGE_KEY, normalized);
    } catch {
      // ignore
    }
    setCurrencyState(normalized);
  }, []);

  const convertAmount = useCallback(
    (tryValue) => {
      const n = Number(tryValue);
      if (!Number.isFinite(n)) return null;
      return currency === "USD" && usdRate ? n / usdRate : n;
    },
    [currency, usdRate],
  );

  const formatAmount = useCallback(
    (tryValue) => {
      const converted = convertAmount(tryValue);
      return converted === null ? "-" : formatCurrency(converted, currency);
    },
    [convertAmount, currency],
  );

  const value = useMemo(
    () => ({ currency, usdRate, setCurrency, convertAmount, formatAmount }),
    [currency, usdRate, setCurrency, convertAmount, formatAmount],
  );

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency() {
  const context = useContext(CurrencyContext);
  if (!context) {
    throw new Error("useCurrency must be used within CurrencyProvider");
  }
  return context;
}

export function CurrencyToggle({ className }) {
  const { currency, setCurrency, usdRate } = useCurrency();
  return (
    <div className={`currency-toggle${className ? ` ${className}` : ""}`}>
      <button
        type="button"
        className={`currency-toggle-btn${currency === "TRY" ? " is-active" : ""}`}
        onClick={() => setCurrency("TRY")}
      >
        ₺ TRY
      </button>
      <button
        type="button"
        className={`currency-toggle-btn${currency === "USD" ? " is-active" : ""}`}
        onClick={() => setCurrency("USD")}
        disabled={!usdRate}
        title={usdRate ? `1 USD ≈ ${usdRate.toFixed(2)} TRY` : "Kur verisi yükleniyor..."}
      >
        $ USD
      </button>
    </div>
  );
}
