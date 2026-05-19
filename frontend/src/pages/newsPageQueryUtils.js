export const NEWS_PAGE_SIZE = 12;

export const NEWS_SORT_OPTIONS = [
  { value: "publishedAt", label: "En yeni" },
  { value: "importanceScore", label: "Onemli haberler" },
];

export function buildNewsQueryParams(filters, page, sortBy = "publishedAt", feedType = "news") {
  return Object.fromEntries(
    Object.entries({
      keyword: filters.keyword?.trim() || undefined,
      category: filters.category || undefined,
      provider: filters.provider || undefined,
      language: filters.language || undefined,
      isKapDisclosure: feedType === "kap" ? true : false,
      page,
      size: NEWS_PAGE_SIZE,
      sortBy,
      sortDirection: "desc",
    }).filter(([, value]) => value !== undefined)
  );
}
