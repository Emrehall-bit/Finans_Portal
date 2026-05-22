import { useQuery } from "@tanstack/react-query";
import {
  getPortfolioDetails,
  getPortfolioHoldings,
  getPortfolioPerformanceHistory,
  getPortfolioRiskSummary,
  getPortfolioSummary,
  getUserPortfolios,
} from "../api/portfolioApi";
import { portfolioKeys } from "../api/queryKeys";

export function useUserPortfolios(userId, options = {}) {
  return useQuery({
    queryKey: portfolioKeys.byUser(userId),
    queryFn: () => getUserPortfolios(userId),
    staleTime: 30_000,
    enabled: !!userId,
    ...options,
  });
}

export function usePortfolioSummary(portfolioId, options = {}) {
  return useQuery({
    queryKey: portfolioKeys.summary(portfolioId),
    queryFn: () => getPortfolioSummary(portfolioId),
    staleTime: 30_000,
    enabled: !!portfolioId,
    ...options,
  });
}

export function usePortfolioDetails(portfolioId, options = {}) {
  return useQuery({
    queryKey: portfolioKeys.details(portfolioId),
    queryFn: () => getPortfolioDetails(portfolioId),
    staleTime: 30_000,
    enabled: !!portfolioId,
    ...options,
  });
}

export function usePortfolioPerformance(portfolioId, params = {}, options = {}) {
  return useQuery({
    queryKey: portfolioKeys.performance(portfolioId, params),
    queryFn: () => getPortfolioPerformanceHistory(portfolioId, params),
    staleTime: 60_000,
    enabled: !!portfolioId,
    ...options,
  });
}

export function usePortfolioRisk(portfolioId, options = {}) {
  return useQuery({
    queryKey: portfolioKeys.risk(portfolioId),
    queryFn: () => getPortfolioRiskSummary(portfolioId),
    staleTime: 2 * 60_000,
    enabled: !!portfolioId,
    ...options,
  });
}

export function usePortfolioHoldings(portfolioId, options = {}) {
  return useQuery({
    queryKey: portfolioKeys.holdings(portfolioId),
    queryFn: () => getPortfolioHoldings(portfolioId),
    staleTime: 30_000,
    enabled: !!portfolioId,
    ...options,
  });
}
