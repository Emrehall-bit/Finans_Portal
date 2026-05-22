import { useQuery } from "@tanstack/react-query";
import { getUserAlerts } from "../api/alertApi";
import { alertKeys } from "../api/queryKeys";

export function useUserAlerts(userId, options = {}) {
  return useQuery({
    queryKey: alertKeys.byUser(userId),
    queryFn: () => getUserAlerts(userId),
    staleTime: 30_000,
    enabled: !!userId,
    ...options,
  });
}
