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
