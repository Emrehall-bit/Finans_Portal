import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import { Check, ChevronDown, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { CurrencyToggle, useCurrency } from "../currency/CurrencyContext";
import { extractErrorMessage } from "../api/responseUtils";
import { useQueryClient, useQueries } from "@tanstack/react-query";
import { addWatchlistItem, removeWatchlistItem } from "../api/watchlistApi";
import { marketKeys, watchlistKeys } from "../api/queryKeys";
import { useUserWatchlist } from "../hooks/useWatchlistQueries";
import { useMarketHistory, useMarketQuotes, useTechnicalAnalysis } from "../hooks/useMarketQueries";
import { getBenchmarkComparison } from "../api/analysisApi";
import useToast from "../hooks/useToast";
import AnalysisComparisonPanel from "../components/analysis/AnalysisComparisonPanel";
import AnalysisSymbolPicker from "../components/analysis/AnalysisSymbolPicker";
import { ANALYSIS_RANGE_PRESETS, buildChartData, buildPresetRange, DEFAULT_INDICATORS, formatChartDate } from "../components/analysis/analysisUtils";
import SimpleAnalysisChart from "../components/analysis/SimpleAnalysisChart";
import { resolveQuoteLatestPrice } from "../components/analysis/advancedChartUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { formatInstrumentCode, getFxCodeLabel } from "../utils/instrumentUtils";
import { isPointBasedInstrument } from "../utils/formatters";

const DEFAULT_ANALYSIS_INDICATORS_PARAM = DEFAULT_INDICATORS.join(",");
const AdvancedChart = lazy(() => import("../components/analysis/AdvancedChart"));
const FundamentalAnalysis = lazy(() => import("../components/analysis/FundamentalAnalysis"));

export default function AnalysisPage() {
  const { t, i18n } = useTranslation();
  const { convertAmount, currency } = useCurrency();
  const { userId, user, updateUserProfile } = useAuth();
  const { toast, showToast } = useToast();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const routeInstrumentType = String(searchParams.get("type") || "").trim().toUpperCase();
  const [primarySymbol, setPrimarySymbol] = useState(() => searchParams.get("symbol") || "");
  const [activeRange, setActiveRange] = useState("3M");
  const [dateRange, setDateRange] = useState(() => buildPresetRange(90));
  const [comparisonMode, setComparisonMode] = useState("normalized");
  const [references, setReferences] = useState([]);
  const [chartMode, setChartMode] = useState(() => (searchParams.get("tool") ? "advanced" : "simple"));
  const [fundamentalsOpen, setFundamentalsOpen] = useState(false);
  const [noteAdding, setNoteAdding] = useState(false);
  const [notePreviewOpen, setNotePreviewOpen] = useState(false);
  const [notePreviewContent, setNotePreviewContent] = useState("");
  const { data: watchlistItems = [] } = useUserWatchlist(userId);
  const [favoriteBusy, setFavoriteBusy] = useState(false);
  const initialHighlightTool = searchParams.get("tool") || null;
  const presetPrice = searchParams.get("preset") ? Number(searchParams.get("preset")) : null;
  const isSimpleChartMode = chartMode === "simple";
  const isComparisonMode = chartMode === "comparison";

  const { data: rawQuotes = [], isLoading: quotesLoading, error: quotesQueryError } = useMarketQuotes();
  const quotes = useMemo(() => (Array.isArray(rawQuotes) ? rawQuotes : []), [rawQuotes]);
  const primaryQuote = useMemo(
    () => quotes.find((q) => q.symbol === primarySymbol || q.code === primarySymbol) ?? null,
    [quotes, primarySymbol],
  );
  const primaryApiSymbol = useMemo(
    () => resolveApiSymbol(primarySymbol, primaryQuote, routeInstrumentType),
    [primarySymbol, primaryQuote, routeInstrumentType],
  );
  const primaryContext = useMemo(
    () => buildInstrumentContext(primarySymbol, primaryQuote, i18n.resolvedLanguage),
    [primarySymbol, primaryQuote, i18n.resolvedLanguage],
  );
  const primaryIsPointBased = isPointBasedInstrument(primaryQuote);
  const primaryIsStock = String(primaryQuote?.instrumentType || "").toUpperCase() === "STOCK";
  const quotesError = quotesQueryError ? extractErrorMessage(quotesQueryError, t("analysis.quotesError")) : "";

  const favoriteCandidates = useMemo(() => {
    const candidates = new Set();
    [primaryApiSymbol, primarySymbol, primaryQuote?.symbol, primaryQuote?.code]
      .map((value) => normalizeWatchlistCode(value))
      .filter(Boolean)
      .forEach((value) => candidates.add(value));
    return candidates;
  }, [primaryApiSymbol, primarySymbol, primaryQuote]);
  const favoriteItem = useMemo(
    () => watchlistItems.find((item) => favoriteCandidates.has(normalizeWatchlistCode(item.instrumentCode))),
    [watchlistItems, favoriteCandidates],
  );
  const isFavorite = !!favoriteItem;
  const favoriteItemId = favoriteItem?.id;

  useEffect(() => {
    if (quotes.length > 0 && !primarySymbol) {
      setPrimarySymbol(quotes[0]?.symbol || "");
    }
  }, [quotes, primarySymbol]);

  // Smart default: set first reference automatically when entering comparison mode.
  // Resets when primary instrument changes so each new primary gets its own default.
  const comparisonDefaultSet = useRef(false);
  useEffect(() => {
    if (!isComparisonMode) {
      comparisonDefaultSet.current = false;
      return;
    }
    if (comparisonDefaultSet.current) return;
    comparisonDefaultSet.current = true;
    if (primaryIsStock) {
      setReferences([{ code: "XU100", type: "INDEX" }]);
    } else {
      setReferences([{ code: "CPI_TR", type: "MACRO" }]);
    }
    setComparisonMode("normalized");
  }, [isComparisonMode, primaryIsStock]);

  const analysisParams = useMemo(
    () => ({
      from: dateRange.from,
      to: dateRange.to,
      indicators: DEFAULT_ANALYSIS_INDICATORS_PARAM,
      ...(primaryQuote?.instrumentType != null && { instrumentType: primaryQuote.instrumentType }),
    }),
    [dateRange, primaryQuote?.instrumentType],
  );

  const { data: analysis = null, isLoading: analysisLoading, error: analysisQueryError } = useTechnicalAnalysis(
    primaryApiSymbol,
    analysisParams,
    { enabled: !isComparisonMode && !!(primaryApiSymbol && dateRange.from && dateRange.to) },
  );
  const analysisError = analysisQueryError ? resolveAnalysisErrorMessage(analysisQueryError, t) : "";

  const historyParams = useMemo(
    () => ({
      from: dateRange.from,
      to: dateRange.to,
      source: primaryQuote?.source,
      type: primaryQuote?.instrumentType,
    }),
    [dateRange, primaryQuote?.source, primaryQuote?.instrumentType],
  );
  const { data: history = [], isLoading: historyLoading } = useMarketHistory(
    primaryApiSymbol,
    historyParams,
    { enabled: isSimpleChartMode && !!(primaryApiSymbol && dateRange.from && dateRange.to) },
  );

  // Parallel benchmark queries — one per valid reference (excludes SECTOR + gated INDEX)
  const validRefs = useMemo(() => {
    if (!isComparisonMode || !primaryApiSymbol || !dateRange.from || !dateRange.to) return [];
    return references.filter((ref) => {
      if (!ref.code) return false;
      if (ref.type === "SECTOR") return false;
      if (ref.type === "INDEX" && !primaryIsStock) return false;
      return true;
    });
  }, [isComparisonMode, references, primaryApiSymbol, dateRange, primaryIsStock]);

  const rawBenchmarkQueries = useQueries({
    queries: validRefs.map((ref) => ({
      queryKey: [...marketKeys.all, "benchmark", {
        baseCode: primaryApiSymbol,
        benchmarkCode: ref.code,
        benchmarkType: ref.type,
        from: dateRange.from,
        to: dateRange.to,
      }],
      queryFn: () => getBenchmarkComparison({
        baseCode: primaryApiSymbol,
        benchmarkCode: ref.code,
        benchmarkType: ref.type,
        from: dateRange.from,
        to: dateRange.to,
      }),
      staleTime: 5 * 60_000,
      enabled: true,
    })),
  });

  const validRefResultMap = useMemo(() => {
    const map = new Map();
    validRefs.forEach((ref, i) => {
      map.set(normalizeWatchlistCode(ref.code), rawBenchmarkQueries[i]);
    });
    return map;
  }, [validRefs, rawBenchmarkQueries]);

  const refResults = useMemo(() => references.map((ref) => {
    const isGated = ref.type === "SECTOR" || (ref.type === "INDEX" && !primaryIsStock);
    const q = validRefResultMap.get(normalizeWatchlistCode(ref.code));
    return {
      ref,
      data: q?.data ?? null,
      isLoading: (q?.isLoading || q?.isFetching) ?? false,
      error: q?.error ? extractErrorMessage(q.error, "") : "",
      isGated,
    };
  }), [references, validRefResultMap, primaryIsStock]);

  const analysisPoints = useMemo(
    () => (Array.isArray(analysis?.points) ? analysis.points : []),
    [analysis],
  );
  const chartData = useMemo(
    () => buildChartData(analysisPoints, history),
    [analysisPoints, history],
  );
  const quoteAlignedChartData = useMemo(
    () => alignLatestChartCloseWithQuote(chartData, primaryQuote),
    [chartData, primaryQuote],
  );
  const hasChartData = quoteAlignedChartData.length > 0;
  const hasAnalysisPoints = analysisPoints.length > 0;
  const simpleChartLoading = analysisLoading || (!hasAnalysisPoints && historyLoading);
  const simpleChartError = !hasChartData ? analysisError : "";
  const displayChartData = useMemo(() => {
    if (currency === "TRY" || primaryIsPointBased) return quoteAlignedChartData;
    return quoteAlignedChartData.map((point) => ({
      ...point,
      open: point.open != null ? convertAmount(point.open) : null,
      high: point.high != null ? convertAmount(point.high) : null,
      low: point.low != null ? convertAmount(point.low) : null,
      close: convertAmount(point.close),
      sma7: point.sma7 != null ? convertAmount(point.sma7) : null,
      sma20: point.sma20 != null ? convertAmount(point.sma20) : null,
      sma50: point.sma50 != null ? convertAmount(point.sma50) : null,
      rsi14: point.rsi14,
    }));
  }, [quoteAlignedChartData, currency, convertAmount, primaryIsPointBased]);

  function handlePrimaryChange(symbol) {
    const newQuote = quotes.find((q) => q.symbol === symbol || q.code === symbol);
    const newIsStock = String(newQuote?.instrumentType || "").toUpperCase() === "STOCK";
    setPrimarySymbol(symbol);
    comparisonDefaultSet.current = false;
    if (isComparisonMode) {
      setReferences((prev) => {
        const filtered = newIsStock
          ? prev
          : prev.filter((r) => r.type !== "INDEX" && r.type !== "SECTOR");
        const normNew = normalizeWatchlistCode(symbol);
        return filtered.filter((r) => normalizeWatchlistCode(r.code) !== normNew);
      });
    }
  }

  function handleAddReference(code, type) {
    if (!code || !type) return;
    const apiCode = resolveReferenceApiSymbol(code, type, quotes);
    setReferences((prev) => {
      if (prev.length >= 4) return prev;
      const normCode = normalizeWatchlistCode(apiCode);
      if (normCode === normalizeWatchlistCode(primaryApiSymbol || primarySymbol)) return prev;
      if (prev.some((r) => normalizeWatchlistCode(r.code) === normCode)) return prev;
      return [...prev, { code: apiCode, type }];
    });
  }

  function handleRemoveReference(code) {
    setReferences((prev) => prev.filter(
      (r) => normalizeWatchlistCode(r.code) !== normalizeWatchlistCode(code),
    ));
  }

  async function handleFavoriteToggle() {
    if (!primaryApiSymbol || favoriteBusy) return;
    if (!userId) {
      showToast("error", t("analysis.loginRequired"));
      return;
    }
    try {
      setFavoriteBusy(true);
      if (isFavorite && favoriteItemId) {
        await removeWatchlistItem(favoriteItemId);
        showToast("success", t("instrumentDetail.favoriteRemoved"));
      } else {
        await addWatchlistItem(userId, { instrumentCode: primaryApiSymbol });
        showToast("success", t("instrumentDetail.favoriteAdded"));
      }
      queryClient.invalidateQueries({ queryKey: watchlistKeys.byUser(userId) });
    } catch (err) {
      showToast("error", extractErrorMessage(err, t("instrumentDetail.favoriteError")));
    } finally {
      setFavoriteBusy(false);
    }
  }

  async function handleAddToNotes(content) {
    if (!userId) {
      showToast("error", "Not eklemek için giriş yapmalısınız");
      return;
    }
    setNotePreviewContent(content || "");
    setNotePreviewOpen(true);
  }

  async function handleConfirmAddToNotes() {
    if (!userId) {
      showToast("error", "Not eklemek için giriş yapmalısınız");
      return;
    }
    if (noteAdding) return;
    setNoteAdding(true);
    try {
      const currentNotes = Array.isArray(user?.notes) ? user.notes : [];
      const newNote = {
        id: crypto.randomUUID(),
        content: notePreviewContent,
        source: "technical-analysis",
        sourceLabel: "Teknik analiz notu",
        createdAt: new Date().toISOString(),
        updatedAt: null,
      };
      await updateUserProfile({
        fullName: user?.fullName ?? "",
        preferredLanguage: user?.preferredLanguage ?? null,
        themePreference: user?.themePreference ?? null,
        notes: [...currentNotes, newNote],
      });
      setNotePreviewOpen(false);
      setNotePreviewContent("");
      showToast("success", "Analiz notlara eklendi");
    } catch {
      showToast("error", "Not eklenemedi");
    } finally {
      setNoteAdding(false);
    }
  }

  function handleRangeChange(preset) {
    setActiveRange(preset.key);
    setDateRange(buildPresetRange(preset.days));
  }

  return (
    <div className="dashboard-stack analysis-lab-shell">
      {toast ? (
        <div key={toast.id} className={`toast-notify ${toast.type}`}>
          {toast.type === "success"
            ? <Check size={15} strokeWidth={2.5} className="toast-notify-icon" />
            : <X size={15} strokeWidth={2.5} className="toast-notify-icon" />}
          <span>{toast.message}</span>
        </div>
      ) : null}
      {notePreviewOpen ? (
        <div className="analysis-note-preview-backdrop" role="presentation">
          <section className="analysis-note-preview-modal" role="dialog" aria-modal="true" aria-label="Not önizleme">
            <div className="analysis-note-preview-head">
              <div>
                <span>Teknik Analiz Notu</span>
                <strong>Önizle ve düzenle</strong>
              </div>
              <button
                type="button"
                className="analysis-note-preview-close"
                onClick={() => {
                  if (noteAdding) return;
                  setNotePreviewOpen(false);
                  setNotePreviewContent("");
                }}
                aria-label="Kapat"
              >
                ×
              </button>
            </div>
            <textarea
              className="analysis-note-preview-textarea"
              value={notePreviewContent}
              onChange={(event) => setNotePreviewContent(event.target.value)}
              spellCheck={false}
            />
            <div className="analysis-note-preview-actions">
              <button
                type="button"
                className="analysis-note-preview-btn analysis-note-preview-btn--ghost"
                disabled={noteAdding}
                onClick={() => {
                  setNotePreviewOpen(false);
                  setNotePreviewContent("");
                }}
              >
                Vazgeç
              </button>
              <button
                type="button"
                className="analysis-note-preview-btn analysis-note-preview-btn--primary"
                disabled={noteAdding || !notePreviewContent.trim()}
                onClick={handleConfirmAddToNotes}
              >
                {noteAdding ? "Kaydediliyor..." : "Notlara Kaydet"}
              </button>
            </div>
          </section>
        </div>
      ) : null}
      {quotesLoading ? <LoadingSpinner label={t("analysis.quotesLoading")} /> : null}
      {quotesError ? <ErrorMessage message={quotesError} /> : null}

      {!quotesLoading && !quotesError && quotes.length === 0 ? (
        <section className="panel-surface">
          <EmptyState title={t("analysis.emptyTitle")} description={t("analysis.emptyDescription")} />
        </section>
      ) : null}

      {!quotesLoading && !quotesError && quotes.length > 0 ? (
        <section className="analysis-lab-grid">
          <div className="analysis-lab-main">
            <section className="panel-surface analysis-terminal-shell">
              {primarySymbol ? (
                <div className="analysis-chart-stack-shell">
                  <AnalysisSymbolPicker
                    quotes={quotes}
                    primarySymbol={primarySymbol}
                    primaryContext={primaryContext}
                    primaryQuote={primaryQuote}
                    currencyToggle={!primaryIsPointBased && !isComparisonMode ? <CurrencyToggle className="analysis-currency-toggle" /> : null}
                    chartMode={chartMode}
                    onChartModeChange={setChartMode}
                    onPrimaryChange={handlePrimaryChange}
                    isFavorite={isFavorite}
                    favoriteBusy={favoriteBusy}
                    onFavoriteToggle={handleFavoriteToggle}
                  />

                  <div className="analysis-terminal-body">
                    {isSimpleChartMode ? (
                      <SimpleAnalysisChart
                        activeRange={activeRange}
                        onRangeChange={handleRangeChange}
                        loading={simpleChartLoading}
                        error={simpleChartError}
                        chartData={displayChartData}
                        presets={ANALYSIS_RANGE_PRESETS}
                        quote={primaryQuote}
                        analysis={analysis}
                        primaryContext={primaryContext}
                        onOpenAdvanced={() => setChartMode("advanced")}
                        onAddToNotes={handleAddToNotes}
                        noteAdding={noteAdding}
                      />
                    ) : null}
                    {chartMode === "advanced" ? (
                      <Suspense fallback={<LoadingSpinner label={t("analysis.chartLoading")} />}>
                        <AdvancedChart
                          instrumentCode={primaryApiSymbol}
                          initialHighlightTool={initialHighlightTool}
                          presetPrice={presetPrice}
                          quote={primaryQuote}
                          technicalAnalysis={analysis}
                          dateRange={dateRange}
                          activeRange={activeRange}
                          rangePresets={ANALYSIS_RANGE_PRESETS}
                          onRangeChange={handleRangeChange}
                          onAddToNotes={handleAddToNotes}
                          noteAdding={noteAdding}
                        />
                      </Suspense>
                    ) : null}
                    {isComparisonMode ? (
                      <AnalysisComparisonPanel
                        references={references}
                        onAddReference={handleAddReference}
                        onRemoveReference={handleRemoveReference}
                        refResults={refResults}
                        mode={comparisonMode}
                        onModeChange={setComparisonMode}
                        primarySymbol={primarySymbol}
                        primaryQuote={primaryQuote}
                        quotes={quotes}
                        onPrimaryChange={handlePrimaryChange}
                        activeRange={activeRange}
                        rangePresets={ANALYSIS_RANGE_PRESETS}
                        onRangeChange={handleRangeChange}
                      />
                    ) : null}
                  </div>
                </div>
              ) : (
                <section className="analysis-lab-panel analysis-terminal-empty">
                  <EmptyState title={t("analysis.primaryEmptyTitle")} description={t("analysis.primaryEmptyDescription")} />
                </section>
              )}
            </section>

            {primarySymbol && !isComparisonMode && ["STOCK", "FUND"].includes(primaryQuote?.instrumentType) ? (
              <section className="panel-surface analysis-fundamentals-shell">
                <button
                  type="button"
                  className={`analysis-fundamentals-toggle${fundamentalsOpen ? " is-open" : ""}`}
                  onClick={() => setFundamentalsOpen((current) => !current)}
                  aria-expanded={fundamentalsOpen}
                >
                  <div>
                    <strong>{t("fundamental.title")}</strong>
                    <span>{fundamentalsOpen ? "Gizle" : "Göster"}</span>
                  </div>
                  <ChevronDown size={18} strokeWidth={2.2} className="analysis-fundamentals-chevron" />
                </button>

                {fundamentalsOpen ? (
                  <div className="analysis-fundamentals-collapse is-open">
                    <Suspense fallback={<LoadingSpinner label={t("fundamental.loading")} />}>
                      <FundamentalAnalysis instrumentCode={primarySymbol} />
                    </Suspense>
                  </div>
                ) : null}
              </section>
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}

function buildInstrumentContext(primarySymbol, primaryQuote, locale) {
  const normalizedType = String(primaryQuote?.instrumentType || "").trim().toUpperCase() || "MARKET";
  const normalizedSource = primaryQuote?.source || "-";
  const displayCode = primaryQuote?.code || formatInstrumentCode(primarySymbol) || "-";
  const symbolLine = normalizedType === "FX" ? `${displayCode}/TRY` : displayCode;

  return {
    symbolLine,
    title: resolveContextTitle(primaryQuote, displayCode, normalizedType, locale),
    metaLine: [normalizedType, normalizedSource].filter(Boolean).join(" • "),
  };
}

function resolveContextTitle(primaryQuote, displayCode, normalizedType, locale) {
  if (normalizedType === "FX") {
    return getFxCodeLabel(displayCode, locale);
  }

  const displayName = String(primaryQuote?.displayName || "").trim();
  if (displayName && displayName !== displayCode) {
    return displayName;
  }

  return displayCode;
}

function resolveAnalysisErrorMessage(error, t) {
  const status = Number(error?.response?.status);
  if ([400, 404, 422, 500].includes(status)) {
    return t("analysis.rangeUnavailable");
  }
  return extractErrorMessage(error, t("analysis.analysisError"));
}

function alignLatestChartCloseWithQuote(chartData, quote) {
  const quotePrice = resolveQuoteLatestPrice(quote);
  if (quotePrice == null || !Array.isArray(chartData) || chartData.length === 0) {
    return chartData;
  }

  const today = new Date();
  const todayKey = formatChartDate(today.toISOString());
  const lastPoint = chartData.at(-1);
  const quotePoint = {
    ...lastPoint,
    close: quotePrice,
    sma7: null,
    sma20: null,
    sma50: null,
    rsi14: null,
  };

  if (lastPoint?.date === todayKey) {
    return [...chartData.slice(0, -1), quotePoint];
  }

  return [
    ...chartData,
    {
      ...quotePoint,
      date: todayKey,
      fullDate: today.toLocaleDateString(undefined, {
        day: "numeric",
        month: "long",
        year: "numeric",
      }),
      open: null,
      high: null,
      low: null,
    },
  ];
}

function resolveApiSymbol(symbol, quote, fallbackInstrumentType = "") {
  const rawSymbol = String(symbol || "").trim();
  if (!rawSymbol) {
    return "";
  }

  const normalizedType = String(quote?.instrumentType || fallbackInstrumentType).trim().toUpperCase();
  if (normalizedType === "FX") {
    const providerSymbol = String(quote?.providerSymbol || "").trim();
    if (providerSymbol) {
      return providerSymbol;
    }

    const fullSymbol = String(quote?.symbol || "").trim();
    const fxSymbol = fullSymbol || rawSymbol;
    const upperFxSymbol = fxSymbol.toUpperCase();
    if (upperFxSymbol.startsWith("TCMB:")) {
      return fxSymbol;
    }
    if (/^[A-Z]{3}$/.test(upperFxSymbol)) {
      return `TCMB:${upperFxSymbol}:SELL`;
    }
  }

  const fullSymbol = String(quote?.symbol || "").trim();
  if (fullSymbol) {
    return fullSymbol;
  }

  return rawSymbol;
}

// Backend WatchlistService.normalizeSymbol ile birebir aynı olmalı
function resolveReferenceApiSymbol(code, type, quotes) {
  const rawCode = String(code || "").trim();
  if (!rawCode || type === "MACRO" || type === "SECTOR") {
    return rawCode;
  }

  const quote = quotes.find((q) => q.symbol === rawCode || q.code === rawCode);
  return resolveApiSymbol(rawCode, quote, quote?.instrumentType || type);
}

function normalizeWatchlistCode(value) {
  if (value == null) return "";
  return String(value).replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}
