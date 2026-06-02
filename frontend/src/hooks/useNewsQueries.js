import { useQuery } from "@tanstack/react-query";
import { getNews, getNewsDetail, getNewsFavorites, getNewsRelated } from "../api/newsApi";
import { newsKeys } from "../api/queryKeys";

export function useNewsList(params = {}, options = {}) {
  return useQuery({
    queryKey: newsKeys.list(params),
    queryFn: () => getNews(params),
    staleTime: 2 * 60_000,
    ...options,
  });
}

export function useNewsDetail(id, options = {}) {
  return useQuery({
    queryKey: newsKeys.detail(id),
    queryFn: () => getNewsDetail(id),
    staleTime: 5 * 60_000,
    enabled: !!id,
    ...options,
  });
}

export function useNewsRelated(id, options = {}) {
  return useQuery({
    queryKey: newsKeys.related(id),
    queryFn: () => getNewsRelated(id),
    staleTime: 5 * 60_000,
    enabled: !!id,
    ...options,
  });
}

export function useNewsFavorites(userId, options = {}) {
  return useQuery({
    queryKey: newsKeys.favorites(userId),
    queryFn: () => getNewsFavorites(),
    staleTime: 2 * 60_000,
    enabled: !!userId,
    ...options,
  });
}
