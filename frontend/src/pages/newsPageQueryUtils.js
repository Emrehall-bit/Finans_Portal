export const NEWS_SORT_OPTIONS = [
  { value: "publishedAt", label: "En yeni" },
  { value: "importanceScore", label: "Önemli haberler" },
];

export function buildNewsQueryParams(filters, page, sortBy = "publishedAt") {
  return Object.fromEntries(
    Object.entries({
      keyword: filters.keyword?.trim() || undefined,
      category: filters.category || undefined,
      provider: filters.provider || undefined,
      language: filters.language || undefined,
      page,
      size: 20,
      sortBy,
      sortDirection: "desc",
    }).filter(([, value]) => value !== undefined)
  );
}
