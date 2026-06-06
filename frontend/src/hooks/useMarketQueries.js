import { useQuery } from "@tanstack/react-query";
import {
  compareTechnicalAnalysis,
  getMarketBySymbol,
  getMarketHistory,
  getMarketQuotes,
  getMarketTapeConfig,
  getMarketsByType,
  screenMarkets,
  getTechnicalAnalysis,
  searchInstruments,
} from "../api/marketApi";
import { getBenchmarkComparison } from "../api/analysisApi";
import { getMacroObservations } from "../api/macroApi";
import { marketKeys } from "../api/queryKeys";

export function useMarketQuotes(options = {}) {
  return useQuery({
    queryKey: marketKeys.quotes(),
    queryFn: getMarketQuotes,
    staleTime: 60_000,
    ...options,
  });
}

export function useMarketsByType(type, options = {}) {
  return useQuery({
    queryKey: marketKeys.byType(type),
    queryFn: () => getMarketsByType(type),
    staleTime: 60_000,
    enabled: !!type,
    ...options,
  });
}

export function useMarketScreen(params, options = {}) {
  return useQuery({
    queryKey: marketKeys.screen(params),
    queryFn: () => screenMarkets(params),
    staleTime: 60_000,
    ...options,
  });
}

export function useMarketBySymbol(symbol, options = {}) {
  return useQuery({
    queryKey: marketKeys.bySymbol(symbol),
    queryFn: () => getMarketBySymbol(symbol),
    staleTime: 30_000,
    enabled: !!symbol,
    ...options,
  });
}

export function useMarketHistory(symbol, params, options = {}) {
  return useQuery({
    queryKey: marketKeys.history(symbol, params),
    queryFn: () => getMarketHistory(symbol, params),
    staleTime: 5 * 60_000,
    enabled: !!symbol,
    ...options,
  });
}

export function useTechnicalAnalysis(symbol, params, options = {}) {
  return useQuery({
    queryKey: marketKeys.technicalAnalysis(symbol, params),
    queryFn: () => getTechnicalAnalysis(symbol, params),
    staleTime: 5 * 60_000,
    enabled: !!symbol,
    ...options,
  });
}

export function useMarketTapeConfig(options = {}) {
  return useQuery({
    queryKey: marketKeys.tapeConfig(),
    queryFn: getMarketTapeConfig,
    staleTime: 10 * 60_000,
    ...options,
  });
}

export function useInstrumentSearch(query, options = {}) {
  return useQuery({
    queryKey: marketKeys.search(query),
    queryFn: () => searchInstruments(query),
    staleTime: 2 * 60_000,
    enabled: !!query && query.length >= 2,
    ...options,
  });
}

export function useComparisonAnalysis(params, options = {}) {
  return useQuery({
    queryKey: [...marketKeys.all, "comparison", params],
    queryFn: () => compareTechnicalAnalysis(params),
    staleTime: 5 * 60_000,
    enabled: !!(params?.symbols && params?.from && params?.to),
    ...options,
  });
}

export function useBenchmarkComparison(params, options = {}) {
  return useQuery({
    queryKey: [...marketKeys.all, "benchmark", params],
    queryFn: () => getBenchmarkComparison(params),
    staleTime: 5 * 60_000,
    enabled: !!(params?.baseCode && params?.benchmarkCode && params?.from && params?.to),
    ...options,
  });
}

export function useBenchmarkReturn(code, type, from, to) {
  const isMacro = type === "MACRO";

  const macroResult = useQuery({
    queryKey: [...marketKeys.all, "benchmark-return-macro", code, from, to],
    queryFn: async () => {
      const observations = await getMacroObservations(code, "MONTHLY_CHANGE");
      return computeMacroReturn(observations, from, to);
    },
    staleTime: 10 * 60_000,
    enabled: isMacro && !!(code && from && to),
  });

  const instrumentResult = useQuery({
    queryKey: [...marketKeys.all, "benchmark-return-instrument", code, from, to],
    queryFn: async () => {
      const history = await getMarketHistory(code, { from, to });
      return computeInstrumentReturn(history);
    },
    staleTime: 5 * 60_000,
    enabled: !isMacro && !!(code && from && to),
  });

  return isMacro ? macroResult : instrumentResult;
}

function computeMacroReturn(observations, from, to) {
  if (!Array.isArray(observations) || observations.length === 0) return null;
  const fromMs = new Date(from).getTime();
  const toMs = new Date(to).getTime();
  const filtered = observations.filter((o) => {
    const d = new Date(o.observationDate).getTime();
    return d >= fromMs && d <= toMs;
  });
  if (filtered.length === 0) return null;
  let cumulative = 1;
  for (const obs of filtered) {
    cumulative *= 1 + Number(obs.value) / 100;
  }
  return cumulative - 1;
}

function computeInstrumentReturn(history) {
  if (!Array.isArray(history) || history.length < 2) return null;
  const first = Number(history[0]?.close ?? history[0]?.price ?? 0);
  const last = Number(history[history.length - 1]?.close ?? history[history.length - 1]?.price ?? 0);
  if (!first) return null;
  return (last - first) / first;
}
