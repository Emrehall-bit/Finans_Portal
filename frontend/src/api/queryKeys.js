function stable(params) {
  if (params == null || typeof params !== "object") return params;
  return Object.keys(params).sort().map((k) => `${k}=${params[k]}`).join("&");
}

export const marketKeys = {
  all: ["markets"],
  quotes: () => [...marketKeys.all, "quotes"],
  byType: (type) => [...marketKeys.all, "type", type],
  screen: (params) => [...marketKeys.all, "screen", stable(params)],
  bySymbol: (symbol) => [...marketKeys.all, "symbol", symbol],
  history: (symbol, params) => [...marketKeys.all, "history", symbol, stable(params)],
  technicalAnalysis: (symbol, params) => [...marketKeys.all, "technical", symbol, stable(params)],
  tapeConfig: () => [...marketKeys.all, "tape-config"],
  search: (query) => [...marketKeys.all, "search", query],
};

export const portfolioKeys = {
  all: ["portfolios"],
  byUser: (userId) => [...portfolioKeys.all, "user", userId],
  detail: (portfolioId) => [...portfolioKeys.all, portfolioId],
  summary: (portfolioId) => [...portfolioKeys.all, portfolioId, "summary"],
  details: (portfolioId) => [...portfolioKeys.all, portfolioId, "details"],
  performance: (portfolioId, params) => [...portfolioKeys.all, portfolioId, "performance", params],
  risk: (portfolioId) => [...portfolioKeys.all, portfolioId, "risk"],
  holdings: (portfolioId) => [...portfolioKeys.all, portfolioId, "holdings"],
};

export const newsKeys = {
  all: ["news"],
  list: (params) => [...newsKeys.all, "list", params],
  detail: (id) => [...newsKeys.all, id],
  related: (id) => [...newsKeys.all, id, "related"],
  favorites: (userId) => [...newsKeys.all, "favorites", userId],
};

export const watchlistKeys = {
  all: ["watchlist"],
  byUser: (userId) => [...watchlistKeys.all, "user", userId],
};

export const alertKeys = {
  all: ["alerts"],
  byUser: (userId) => [...alertKeys.all, "user", userId],
};
