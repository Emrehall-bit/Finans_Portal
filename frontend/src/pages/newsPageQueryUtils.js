export const NEWS_PAGE_SIZE = 9;
export const KAP_PAGE_SIZE = 5;

export const NEWS_SORT_OPTIONS = [
  { value: "publishedAt", label: "En yeni" },
  { value: "importanceScore", label: "Onemli haberler" },
];

export function buildNewsQueryParams(filters, page, sortBy = "publishedAt", feedType = "news") {
  const pageSize = feedType === "kap" ? KAP_PAGE_SIZE : NEWS_PAGE_SIZE;

  return Object.fromEntries(
    Object.entries({
      keyword: filters.keyword?.trim() || undefined,
      category: filters.category || undefined,
      provider: filters.provider || undefined,
      language: filters.language || undefined,
      favoritesOnly: filters.favoritesOnly ? true : undefined,
      favoriteUserId: filters.favoriteUserId || undefined,
      isKapDisclosure: feedType === "kap" ? true : false,
      fromDate: filters.fromDate || undefined,
      toDate: filters.toDate || undefined,
      page,
      size: pageSize,
      sortBy,
      sortDirection: "desc",
    }).filter(([, value]) => value !== undefined)
  );
}
