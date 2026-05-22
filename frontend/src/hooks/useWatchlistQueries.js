import { useQuery } from "@tanstack/react-query";
import { getUserWatchlist } from "../api/watchlistApi";
import { watchlistKeys } from "../api/queryKeys";

export function useUserWatchlist(userId, options = {}) {
  return useQuery({
    queryKey: watchlistKeys.byUser(userId),
    queryFn: () => getUserWatchlist(userId),
    staleTime: 30_000,
    enabled: !!userId,
    ...options,
  });
}
