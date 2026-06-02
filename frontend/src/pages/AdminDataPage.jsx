import { useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  getBinanceHistoryFetchStatus,
  getMarketTapeCandidates,
  getMarketTapeConfig,
  getStockFetchStatus,
  getStockHistoryBackfillStatus,
  getTefasFundBackfillStatus,
  getTefasFundFetchStatus,
  getTcmbHistoryBackfillStatus,
  getTcmbSyncStatus,
  triggerBinanceHistoryFetch,
  triggerMacroSyncAll,
  triggerStockFetch,
  triggerStockHistoryBackfill,
  triggerTefasFundFetch,
  triggerTefasFundBackfill,
  triggerTcmbHistoryBackfill,
  triggerTcmbSync,
  triggerIndexFetch,
  triggerCommodityDerive,
  triggerInternalCommodityHistoryBackfill,
  triggerCommodityHistoryBackfill,
  triggerIndexHistoryBackfill,
  updateMarketTapeConfig,
  seedMockRatios,
} from "../api/adminApi";
import { auditAffectedInstruments, backfillFilterTags, syncNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import { getNewsProviderLabel } from "../components/news/newsCardUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";

const DEFAULT_MARKET_TAPE_SYMBOLS = [
  "XU100",
  "BIST100",
  "BTCUSDT",
  "BTCTRY",
  "BTC",
  "USDTRY",
  "EURTRY",
  "XAUTRY",
  "GRAMALTIN",
  "ETHUSDT",
  "ETHTRY",
  "ETH",
];

function buildPayload(symbol, startDate, endDate) {
  return {
    symbol: symbol.trim() || null,
    startDate: startDate || null,
    endDate: endDate || null,
  };
}

export default function AdminDataPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [busyKey, setBusyKey] = useState(null);
  const [actionsLocked, setActionsLocked] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [stockSymbol, setStockSymbol] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [binanceDays, setBinanceDays] = useState("1825");
  const [commodityHistoryDays, setCommodityHistoryDays] = useState("365");
  const [indexHistoryDays, setIndexHistoryDays] = useState("365");
  const [tefasFundCode, setTefasFundCode] = useState("");
  const [tefasPeriod, setTefasPeriod] = useState("");
  const [affectedAuditLimit, setAffectedAuditLimit] = useState("100");
  const [marketTapeSymbols, setMarketTapeSymbols] = useState([]);
  const [marketTapeCatalog, setMarketTapeCatalog] = useState([]);
  const [marketTapeSearch, setMarketTapeSearch] = useState("");
  const [marketTapeLoaded, setMarketTapeLoaded] = useState(false);
  const [jobProgress, setJobProgress] = useState(null);
  const [completedJobKey, setCompletedJobKey] = useState(null);
  const [showMockRatioConfirm, setShowMockRatioConfirm] = useState(false);
  const [seedMockRatiosResult, setSeedMockRatiosResult] = useState(null);
  const pollingRef = useRef(null);
  const actionsLockedRef = useRef(false);
  const dragStateRef = useRef(null);

  const statusLoaders = useMemo(
    () => ({
      "stock-fetch": getStockFetchStatus,
      "stock-history": getStockHistoryBackfillStatus,
      "tcmb-sync": getTcmbSyncStatus,
      "tcmb-history": getTcmbHistoryBackfillStatus,
      "binance-history": getBinanceHistoryFetchStatus,
      "tefas-fund-fetch": getTefasFundFetchStatus,
      "tefas-fund-backfill": getTefasFundBackfillStatus,
    }),
    [],
  );

  const stopJobPolling = () => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  };

  const lockActions = () => {
    if (actionsLockedRef.current) {
      return false;
    }
    actionsLockedRef.current = true;
    setActionsLocked(true);
    return true;
  };

  const unlockActions = () => {
    actionsLockedRef.current = false;
    setActionsLocked(false);
  };

  useEffect(() => () => stopJobPolling(), []);

  useEffect(() => {
    let active = true;

    async function loadMarketTape() {
      const [configResult, quotesResult] = await Promise.allSettled([
        getMarketTapeConfig(),
        getMarketTapeCandidates(),
      ]);

      if (!active) {
        return;
      }

      const resolvedSymbols =
        configResult.status === "fulfilled" && Array.isArray(configResult.value) && configResult.value.length > 0
          ? configResult.value
          : DEFAULT_MARKET_TAPE_SYMBOLS;

      const resolvedQuotes =
        quotesResult.status === "fulfilled" && Array.isArray(quotesResult.value)
          ? quotesResult.value
          : [];

      setMarketTapeSymbols(resolvedSymbols);
      setMarketTapeCatalog(resolvedQuotes);
      setMarketTapeLoaded(true);

      if (configResult.status === "rejected") {
        setResult({
          marketTapeConfigWarning: extractErrorMessage(configResult.reason, t("admin.marketTape.loadError")),
        });
      }
    }

    loadMarketTape();

    return () => {
      active = false;
    };
  }, [t]);

  const pollJobStatus = async (key) => {
    const statusLoader = statusLoaders[key];
    if (!statusLoader) {
      stopJobPolling();
      setBusyKey((current) => (current === key ? null : current));
      unlockActions();
      return;
    }

    try {
      const status = await statusLoader();
      if (!status) {
        return;
      }

      setJobProgress({ key, ...status });

      if (!status.running && isJobCompleted(key, status)) {
        stopJobPolling();
        setBusyKey((current) => (current === key ? null : current));
        setCompletedJobKey(key);
        unlockActions();
      }
    } catch (err) {
      stopJobPolling();
      setBusyKey((current) => (current === key ? null : current));
      setJobProgress(null);
      setError(extractErrorMessage(err, t("admin.actionError")));
      unlockActions();
    }
  };

  const startPolling = (key) => {
    stopJobPolling();
    window.setTimeout(() => {
      pollJobStatus(key);
    }, 500);
    pollingRef.current = window.setInterval(() => {
      pollJobStatus(key);
    }, 3000);
  };

  const startJob = async (key, executor) => {
    if (!lockActions()) {
      return;
    }

    setBusyKey(key);
    setError("");
    setResult(null);
    setCompletedJobKey(null);
    setJobProgress({ key, running: true, processed: 0, total: 0 });

    try {
      const response = await executor();
      setResult(response ?? null);
      startPolling(key);
    } catch (err) {
      stopJobPolling();
      setBusyKey(null);
      setJobProgress(null);
      setCompletedJobKey(null);
      setError(extractErrorMessage(err, t("admin.actionError")));
      unlockActions();
    }
  };

  const runAction = async (key, executor) => {
    if (!lockActions()) {
      return;
    }

    setBusyKey(key);
    setError("");
    setResult(null);
    setCompletedJobKey(null);

    try {
      const response = await executor();
      setResult(response ?? null);
    } catch (err) {
      setError(extractErrorMessage(err, t("admin.actionError")));
    } finally {
      setBusyKey((current) => (current === key ? null : current));
      unlockActions();
    }
  };

  const actionCards = useMemo(
    () => [
      {
        key: "stock-fetch",
        group: "live",
        eyebrow: t("admin.cards.stockFetch.eyebrow"),
        title: t("admin.cards.stockFetch.title"),
        description: t("admin.cards.stockFetch.description"),
        actionLabel: t("admin.cards.stockFetch.action"),
        onClick: () => startJob("stock-fetch", triggerStockFetch),
      },
      {
        key: "news-sync-aa",
        group: "live",
        eyebrow: "News Sync",
        title: "AA RSS",
        description: "Anadolu Ajansı ekonomi akışını senkronize eder.",
        actionLabel: `${getNewsProviderLabel("AA_RSS")} sync`,
        onClick: () => runAction("news-sync-aa", () => syncNews({ provider: "AA_RSS" })),
      },
      {
        key: "news-sync-cnbc",
        group: "live",
        eyebrow: "News Sync",
        title: "CNBC RSS",
        description: "CNBC RSS keşfini çalıştırır, article page fetch ile full content dener.",
        actionLabel: `${getNewsProviderLabel("CNBC_RSS")} sync`,
        onClick: () => runAction("news-sync-cnbc", () => syncNews({ provider: "CNBC_RSS" })),
      },
      {
        key: "news-sync-kap",
        group: "live",
        eyebrow: "News Sync",
        title: "KAP",
        description: "KAP bildirim akışını senkronize eder.",
        actionLabel: `${getNewsProviderLabel("KAP")} sync`,
        onClick: () => runAction("news-sync-kap", () => syncNews({ provider: "KAP" })),
      },
      {
        key: "news-backfill-tags",
        group: "live",
        eyebrow: "News Backfill",
        title: "Filter Tags Yenile",
        description: "Mevcut haberlerin filterTags alanını yeniden hesaplar (KAP bildirimleri atlanır). Limit: 5000 haber.",
        actionLabel: "Filter Tags Backfill",
        onClick: () => runAction("news-backfill-tags", () => backfillFilterTags({ limit: 5000, dryRun: false })),
      },
      {
        key: "news-affected-audit",
        group: "live",
        eyebrow: t("admin.cards.newsAffectedAudit.eyebrow"),
        title: t("admin.cards.newsAffectedAudit.title"),
        description: t("admin.cards.newsAffectedAudit.description"),
        actionLabel: t("admin.cards.newsAffectedAudit.action"),
        input: (
          <input
            type="number"
            min="1"
            max="500"
            step="1"
            value={affectedAuditLimit}
            onChange={(event) => setAffectedAuditLimit(event.target.value)}
            className="admin-console-input"
            placeholder={t("admin.cards.newsAffectedAudit.limitPlaceholder")}
          />
        ),
        onClick: () =>
          runAction("news-affected-audit", () =>
            auditAffectedInstruments({ limit: Number.parseInt(affectedAuditLimit, 10) || 100 }),
          ),
      },
      {
        key: "tcmb-sync",
        group: "live",
        eyebrow: t("admin.cards.tcmbSync.eyebrow"),
        title: t("admin.cards.tcmbSync.title"),
        description: t("admin.cards.tcmbSync.description"),
        actionLabel: t("admin.cards.tcmbSync.action"),
        onClick: () => startJob("tcmb-sync", triggerTcmbSync),
      },
      {
        key: "tcmb-history",
        group: "history",
        warning: true,
        eyebrow: t("admin.cards.tcmbHistory.eyebrow"),
        title: t("admin.cards.tcmbHistory.title"),
        description: t("admin.cards.tcmbHistory.description"),
        actionLabel: t("admin.cards.tcmbHistory.action"),
        onClick: () => startJob("tcmb-history", triggerTcmbHistoryBackfill),
      },
      {
        key: "binance-history",
        group: "history",
        warning: true,
        eyebrow: t("admin.cards.binanceHistory.eyebrow"),
        title: t("admin.cards.binanceHistory.title"),
        description: t("admin.cards.binanceHistory.description"),
        actionLabel: t("admin.cards.binanceHistory.action"),
        input: (
          <input
            type="number"
            min="1"
            step="1"
            value={binanceDays}
            onChange={(event) => setBinanceDays(event.target.value)}
            className="admin-console-input"
            placeholder="1825"
          />
        ),
        onClick: () =>
          startJob("binance-history", () =>
            triggerBinanceHistoryFetch(Number.parseInt(binanceDays, 10) || 1825),
          ),
      },
      {
        key: "index-fetch",
        group: "live",
        eyebrow: t("admin.cards.indexFetch.eyebrow"),
        title: t("admin.cards.indexFetch.title"),
        description: t("admin.cards.indexFetch.description"),
        actionLabel: t("admin.cards.indexFetch.action"),
        onClick: () => runAction("index-fetch", triggerIndexFetch),
      },
      {
        key: "commodity-derive",
        group: "live",
        eyebrow: t("admin.cards.commodityDerive.eyebrow"),
        title: t("admin.cards.commodityDerive.title"),
        description: t("admin.cards.commodityDerive.description"),
        actionLabel: t("admin.cards.commodityDerive.action"),
        onClick: () => runAction("commodity-derive", triggerCommodityDerive),
      },
      {
        key: "internal-commodity-history",
        group: "history",
        warning: true,
        eyebrow: t("admin.cards.internalCommodityHistory.eyebrow"),
        title: t("admin.cards.internalCommodityHistory.title"),
        description: t("admin.cards.internalCommodityHistory.description"),
        actionLabel: t("admin.cards.internalCommodityHistory.action"),
        input: (
          <input
            type="number"
            min="1"
            step="1"
            value={commodityHistoryDays}
            onChange={(event) => setCommodityHistoryDays(event.target.value)}
            className="admin-console-input"
            placeholder={t("admin.cards.internalCommodityHistory.daysPlaceholder")}
          />
        ),
        onClick: () =>
          runAction("internal-commodity-history", () =>
            triggerInternalCommodityHistoryBackfill(Number.parseInt(commodityHistoryDays, 10) || 365),
          ),
      },
      {
        key: "commodity-history",
        group: "history",
        warning: true,
        eyebrow: t("admin.cards.commodityHistory.eyebrow"),
        title: t("admin.cards.commodityHistory.title"),
        description: t("admin.cards.commodityHistory.description"),
        actionLabel: t("admin.cards.commodityHistory.action"),
        input: (
          <input
            type="number"
            min="1"
            step="1"
            value={commodityHistoryDays}
            onChange={(event) => setCommodityHistoryDays(event.target.value)}
            className="admin-console-input"
            placeholder={t("admin.cards.commodityHistory.daysPlaceholder")}
          />
        ),
        onClick: () =>
          runAction("commodity-history", () =>
            triggerCommodityHistoryBackfill(Number.parseInt(commodityHistoryDays, 10) || 365),
          ),
      },
      {
        key: "index-history",
        group: "history",
        warning: true,
        eyebrow: t("admin.cards.indexHistory.eyebrow"),
        title: t("admin.cards.indexHistory.title"),
        description: t("admin.cards.indexHistory.description"),
        actionLabel: t("admin.cards.indexHistory.action"),
        input: (
          <input
            type="number"
            min="1"
            step="1"
            value={indexHistoryDays}
            onChange={(event) => setIndexHistoryDays(event.target.value)}
            className="admin-console-input"
            placeholder={t("admin.cards.indexHistory.daysPlaceholder")}
          />
        ),
        onClick: () =>
          runAction("index-history", () =>
            triggerIndexHistoryBackfill(Number.parseInt(indexHistoryDays, 10) || 365),
          ),
      },
      {
        key: "tefas-fund-fetch",
        group: "live",
        eyebrow: t("admin.cards.tefasFundFetch.eyebrow"),
        title: t("admin.cards.tefasFundFetch.title"),
        description: t("admin.cards.tefasFundFetch.description"),
        actionLabel: t("admin.cards.tefasFundFetch.action"),
        onClick: () => startJob("tefas-fund-fetch", triggerTefasFundFetch),
      },
      {
        key: "tefas-fund-backfill",
        group: "history",
        warning: true,
        eyebrow: t("admin.cards.tefasFundBackfill.eyebrow"),
        title: t("admin.cards.tefasFundBackfill.title"),
        description: t("admin.cards.tefasFundBackfill.description"),
        actionLabel: t("admin.cards.tefasFundBackfill.action"),
        input: (
          <div className="admin-console-form-grid">
            <input
              type="text"
              value={tefasFundCode}
              onChange={(event) => setTefasFundCode(event.target.value.toUpperCase())}
              className="admin-console-input"
              placeholder={t("admin.cards.tefasFundBackfill.fundCodePlaceholder")}
            />
            <input
              type="number"
              min="1"
              max="60"
              step="1"
              value={tefasPeriod}
              onChange={(event) => setTefasPeriod(event.target.value)}
              className="admin-console-input"
              placeholder={t("admin.cards.tefasFundBackfill.periodPlaceholder")}
            />
          </div>
        ),
        onClick: () =>
          startJob("tefas-fund-backfill", () =>
            triggerTefasFundBackfill({
              fundCode: tefasFundCode.trim() || null,
              periyod: tefasPeriod.trim() ? Number.parseInt(tefasPeriod, 10) || null : null,
            }),
          ),
      },
    ],
    [affectedAuditLimit, binanceDays, commodityHistoryDays, indexHistoryDays, tefasFundCode, tefasPeriod, t],
  );

  const runMacroSyncAll = async () => {
    if (!lockActions()) return;
    setBusyKey("macro-all");
    setError("");
    setResult(null);
    setCompletedJobKey(null);
    try {
      const response = await triggerMacroSyncAll();
      setResult(response ?? null);
    } catch (err) {
      setError(extractErrorMessage(err, t("admin.actionError")));
    } finally {
      setBusyKey((c) => (c === "macro-all" ? null : c));
      unlockActions();
    }
  };

  const handleSeedMockRatios = async () => {
    setShowMockRatioConfirm(false);
    setSeedMockRatiosResult(null);
    await runAction("seed-mock-ratios", async () => {
      const response = await seedMockRatios();
      const data = response?.data ?? null;
      setSeedMockRatiosResult(data);
      queryClient.invalidateQueries({ queryKey: ["markets", "screen"] });
      queryClient.invalidateQueries({ queryKey: ["markets", "symbol"] });
      return response;
    });
  };

  const isBusy = useMemo(() => (key) => busyKey === key, [busyKey]);
  const controlsDisabled = actionsLocked || busyKey !== null || jobProgress?.running === true;
  const liveOperationCards = useMemo(() => actionCards.filter((card) => card.group === "live"), [actionCards]);
  const historyOperationCards = useMemo(() => actionCards.filter((card) => card.group === "history"), [actionCards]);
  const operationStatusLabel = error
    ? t("admin.metrics.status.error")
    : jobProgress?.running
      ? t("admin.metrics.status.running")
      : completedJobKey
        ? t("admin.metrics.status.completed")
        : "-";

  const availableMarketTapeSymbols = useMemo(() => {
    const selected = new Set(marketTapeSymbols.map((item) => String(item).toUpperCase()));
    return [...new Set(
      marketTapeCatalog
        .map((item) => item?.symbol)
        .filter(Boolean)
        .map((item) => String(item).toUpperCase()),
    )]
      .filter((symbol) => !selected.has(symbol))
      .sort((left, right) => left.localeCompare(right));
  }, [marketTapeCatalog, marketTapeSymbols]);

  const filteredAvailableMarketTapeSymbols = useMemo(() => {
    const query = marketTapeSearch.trim().toUpperCase();
    if (!query) {
      return availableMarketTapeSymbols;
    }
    return availableMarketTapeSymbols.filter((symbol) => symbol.includes(query));
  }, [availableMarketTapeSymbols, marketTapeSearch]);

  const handleSaveMarketTape = async () => {
    await runAction("market-tape-save", async () => {
      const response = await updateMarketTapeConfig(marketTapeSymbols);
      setMarketTapeSymbols(response?.symbols ?? marketTapeSymbols);
      window.dispatchEvent(new CustomEvent("market-tape-config-updated"));
      return response;
    });
  };

  const handleAddMarketTapeSymbol = (symbol) => {
    if (!symbol) return;
    setMarketTapeSymbols((current) => {
      const normalized = String(symbol).toUpperCase();
      if (current.includes(normalized)) return current;
      return [...current, normalized];
    });
  };

  const handleRemoveMarketTapeSymbol = (symbol) => {
    setMarketTapeSymbols((current) => current.filter((item) => item !== symbol));
  };

  const handleSelectedDragStart = (symbol, index) => {
    dragStateRef.current = { source: "selected", symbol, index };
  };

  const handleAvailableDragStart = (symbol) => {
    dragStateRef.current = { source: "available", symbol, index: -1 };
  };

  const moveSelectedSymbol = (fromIndex, toIndex) => {
    if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0) return;
    setMarketTapeSymbols((current) => {
      if (fromIndex >= current.length || toIndex >= current.length) return current;
      const next = [...current];
      const [moved] = next.splice(fromIndex, 1);
      next.splice(toIndex, 0, moved);
      return next;
    });
  };

  const insertAvailableIntoSelected = (symbol, targetIndex = null) => {
    if (!symbol) return;
    setMarketTapeSymbols((current) => {
      const normalized = String(symbol).toUpperCase();
      if (current.includes(normalized)) return current;
      const next = [...current];
      if (targetIndex == null || targetIndex < 0 || targetIndex > next.length) {
        next.push(normalized);
      } else {
        next.splice(targetIndex, 0, normalized);
      }
      return next;
    });
  };

  const handleSelectedDrop = (targetIndex) => {
    const dragState = dragStateRef.current;
    if (!dragState) return;
    if (dragState.source === "selected") {
      moveSelectedSymbol(dragState.index, targetIndex);
    } else {
      insertAvailableIntoSelected(dragState.symbol, targetIndex);
    }
    dragStateRef.current = null;
  };

  const handleSelectedAppendDrop = () => {
    const dragState = dragStateRef.current;
    if (!dragState) return;
    if (dragState.source === "selected") {
      setMarketTapeSymbols((current) => {
        if (dragState.index < 0 || dragState.index >= current.length) return current;
        const next = [...current];
        const [moved] = next.splice(dragState.index, 1);
        next.push(moved);
        return next;
      });
    } else {
      insertAvailableIntoSelected(dragState.symbol);
    }
    dragStateRef.current = null;
  };

  const renderJobProgress = (key) => {
    if (jobProgress?.key === key) {
      if (jobProgress.running) {
        if (key === "tefas-fund-fetch") {
          return (
            <div className="admin-console-job-status">
              <LoadingSpinner label={t("admin.fundFetch.running")} />
            </div>
          );
        }
        return (
          <p className="admin-console-copy">
            {t("admin.progress", {
              processed: jobProgress.processed,
              total: jobProgress.total,
            })}
          </p>
        );
      }

      if (completedJobKey === key) {
        if (key === "tefas-fund-fetch") {
          return (
            <p className="admin-console-copy">
              {t("admin.fundFetch.completed", {
                processedFunds: jobProgress.processedFunds ?? 0,
                savedFunds: jobProgress.savedFunds ?? 0,
              })}
            </p>
          );
        }
        return <p className="admin-console-copy">{t("admin.completed")}</p>;
      }
    }

    if (completedJobKey === key) {
      return <p className="admin-console-copy">{t("admin.completed")}</p>;
    }

    return null;
  };

  const renderOperationCard = (card) => (
    <article key={card.key} className="admin-console-card admin-operation-card panel-surface">
      <div className="admin-console-card-copy">
        <div className="admin-operation-card-head">
          <p className="eyebrow">{card.eyebrow}</p>
          {card.warning ? <span className="admin-warning-badge">{t("admin.longRunningWarning")}</span> : null}
        </div>
        <h3>{card.title}</h3>
        <p>{card.description}</p>
      </div>
      {card.input ?? null}
      <button
        type="button"
        className="admin-console-button"
        disabled={controlsDisabled}
        onClick={card.onClick}
      >
        <span className="admin-console-button-glow" />
        <span>{isBusy(card.key) ? t("admin.running") : card.actionLabel}</span>
      </button>
      {renderJobProgress(card.key)}
    </article>
  );

  const isAffectedInstrumentAuditResult =
    result &&
    typeof result === "object" &&
    typeof result.checkedCount === "number" &&
    Array.isArray(result.suspiciousItems);

  const renderResultPanel = () => {
    if (!result) {
      return (
        <EmptyState
          title={t("admin.result.emptyTitle")}
          description={t("admin.result.emptyDescription")}
        />
      );
    }

    if (!isAffectedInstrumentAuditResult) {
      return <pre className="admin-console-result-box">{JSON.stringify(result, null, 2)}</pre>;
    }

    const suspiciousItems = result.suspiciousItems ?? [];

    return (
      <div className="admin-audit-result">
        <div className="admin-audit-metrics">
          <div className="admin-console-metric-card">
            <span>{t("admin.audit.checkedCount")}</span>
            <strong>{result.checkedCount ?? 0}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.audit.suspiciousCount")}</span>
            <strong>{result.suspiciousCount ?? 0}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.audit.emptyCount")}</span>
            <strong>{result.emptyCount ?? 0}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.audit.highConfidenceCount")}</span>
            <strong>{result.highConfidenceCount ?? 0}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.audit.mediumConfidenceCount")}</span>
            <strong>{result.mediumConfidenceCount ?? 0}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.audit.lowConfidenceCount")}</span>
            <strong>{result.lowConfidenceCount ?? 0}</strong>
          </div>
        </div>

        {suspiciousItems.length === 0 ? (
          <EmptyState
            title={t("admin.audit.emptyTitle")}
            description={t("admin.audit.emptyDescription")}
          />
        ) : (
          <div className="admin-audit-list">
            {suspiciousItems.map((item) => (
              <article key={item.newsId} className="admin-audit-item">
                <div className="admin-audit-item-head">
                  <strong>{item.title}</strong>
                  <span className="summary-chip">#{item.newsId}</span>
                </div>
                <p className="admin-console-copy">{item.suspiciousReason}</p>
                <div className="admin-audit-meta">
                  <span>{t("admin.audit.category")}: {item.category || "-"}</span>
                  <span>
                    {t("admin.audit.tags")}: {Array.isArray(item.filterTags) && item.filterTags.length ? item.filterTags.join(", ") : "-"}
                  </span>
                  <span>
                    {t("admin.audit.symbols")}: {Array.isArray(item.affectedSymbols) && item.affectedSymbols.length ? item.affectedSymbols.join(", ") : "-"}
                  </span>
                </div>
                {Array.isArray(item.reasons) && item.reasons.length ? (
                  <ul className="admin-audit-reasons">
                    {item.reasons.map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                ) : null}
              </article>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="dashboard-stack admin-console-shell admin-panel-page">
      {showMockRatioConfirm && (
        <div
          className="modal-backdrop"
          role="presentation"
          onClick={() => setShowMockRatioConfirm(false)}
        >
          <div
            className="auth-modal instrument-action-modal"
            role="dialog"
            aria-modal="true"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="instrument-action-modal-head">
              <div>
                <p className="eyebrow">Şirket Verileri</p>
                <h3>Mock Temel Analiz Verisi Oluştur</h3>
              </div>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setShowMockRatioConfirm(false)}
              >
                {t("common.close")}
              </button>
            </div>
            <p className="admin-console-copy">
              Mevcut <strong>company_ratios</strong>, <strong>financial_reports</strong> ve{" "}
              <strong>financial_values</strong> verileri silinmez veya ezilmez.
              Sadece oran kaydı olmayan şirketler için deterministik demo/mock temel analiz verisi
              oluşturulur. Aynı ticker için her çalıştırmada aynı değerler üretilir.
            </p>
            <p className="admin-console-copy">Devam etmek istiyor musunuz?</p>
            <div className="instrument-action-footer">
              <button type="button" onClick={handleSeedMockRatios}>
                Evet, Oluştur
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setShowMockRatioConfirm(false)}
              >
                {t("common.cancel")}
              </button>
            </div>
          </div>
        </div>
      )}

      {error ? <ErrorMessage message={error} /> : null}

      <section className="admin-section admin-status-section panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">{t("admin.status.eyebrow")}</p>
            <h3>{t("admin.status.title")}</h3>
          </div>
          <span className="summary-chip">{t("admin.title")}</span>
        </div>

        <div className="admin-status-grid">
          <div className="admin-console-metric-card">
            <span>{t("admin.status.totalSymbols")}</span>
            <strong>{marketTapeLoaded ? marketTapeCatalog.length || "-" : "-"}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.status.selectedTapeSymbols")}</span>
            <strong>{marketTapeLoaded ? marketTapeSymbols.length || "-" : "-"}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.status.lastOperationTime")}</span>
            <strong>-</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.status.lastOperationStatus")}</span>
            <strong>{operationStatusLabel}</strong>
          </div>
        </div>
      </section>

      <section className="admin-section admin-console-form admin-market-tape-section panel-surface">
        <div className="admin-section-head">
          <div>
          <p className="eyebrow">{t("admin.marketTape.eyebrow")}</p>
          <h3>{t("admin.marketTape.title")}</h3>
          <p className="admin-console-copy">{t("admin.marketTape.description")}</p>
          </div>
        </div>

        {!marketTapeLoaded ? (
          <LoadingSpinner label={t("admin.marketTape.loading")} />
        ) : (
          <>
            <div className="market-tape-admin-grid">
              <section className="market-tape-admin-panel">
                <div className="market-tape-admin-head">
                  <span className="admin-console-label">{t("admin.marketTape.selectedLabel")}</span>
                  <strong>{marketTapeSymbols.length}</strong>
                </div>

                {marketTapeSymbols.length === 0 ? (
                  <EmptyState
                    title={t("admin.marketTape.emptySelectedTitle")}
                    description={t("admin.marketTape.emptySelectedDescription")}
                  />
                ) : (
                  <div
                    className="market-tape-admin-list"
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={handleSelectedAppendDrop}
                  >
                    {marketTapeSymbols.map((symbol, index) => (
                      <div
                        key={symbol}
                        className="market-tape-chip selected"
                        draggable={!controlsDisabled}
                        onDragStart={() => handleSelectedDragStart(symbol, index)}
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={() => handleSelectedDrop(index)}
                      >
                        <span className="market-tape-chip-handle">::</span>
                        <strong>{symbol}</strong>
                        <button
                          type="button"
                          className="market-tape-chip-remove"
                          disabled={controlsDisabled}
                          onClick={() => handleRemoveMarketTapeSymbol(symbol)}
                        >
                          {t("common.remove")}
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              <section className="market-tape-admin-panel">
                <div className="market-tape-admin-head">
                  <span className="admin-console-label">{t("admin.marketTape.availableLabel")}</span>
                  <strong>{availableMarketTapeSymbols.length}</strong>
                </div>
                <input
                  type="text"
                  className="admin-console-input"
                  value={marketTapeSearch}
                  onChange={(event) => setMarketTapeSearch(event.target.value.toUpperCase())}
                  placeholder={t("admin.marketTape.searchPlaceholder")}
                  disabled={controlsDisabled}
                />
                <div className="market-tape-admin-list compact">
                  {availableMarketTapeSymbols.length === 0 ? (
                    <EmptyState
                      title={t("admin.marketTape.emptyAvailableTitle")}
                      description={t("admin.marketTape.emptyAvailableDescription")}
                    />
                  ) : filteredAvailableMarketTapeSymbols.length === 0 ? (
                    <EmptyState
                      title={t("admin.marketTape.emptySearchTitle")}
                      description={t("admin.marketTape.emptySearchDescription")}
                    />
                  ) : (
                    filteredAvailableMarketTapeSymbols.map((symbol) => (
                      <div
                        key={symbol}
                        className="market-tape-chip available"
                        draggable={!controlsDisabled}
                        onDragStart={() => handleAvailableDragStart(symbol)}
                      >
                        <strong>{symbol}</strong>
                        <button
                          type="button"
                          className="market-tape-chip-add"
                          disabled={controlsDisabled}
                          onClick={() => handleAddMarketTapeSymbol(symbol)}
                        >
                          {t("common.add")}
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </section>
            </div>
            <p className="admin-console-copy">{t("admin.marketTape.hint")}</p>
            <div className="admin-console-actions">
              <button
                type="button"
                className="admin-console-button"
                disabled={controlsDisabled}
                onClick={handleSaveMarketTape}
              >
                <span className="admin-console-button-glow" />
                <span>{isBusy("market-tape-save") ? t("admin.running") : t("admin.marketTape.save")}</span>
              </button>
            </div>
          </>
        )}
      </section>

      <section className="admin-section">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">{t("admin.sections.liveEyebrow")}</p>
            <h3>{t("admin.sections.liveTitle")}</h3>
          </div>
        </div>
        <div className="admin-console-grid admin-grid">
          {liveOperationCards.map(renderOperationCard)}
        </div>
      </section>

      <section className="admin-section">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">{t("admin.sections.historyEyebrow")}</p>
            <h3>{t("admin.sections.historyTitle")}</h3>
          </div>
        </div>

        <div className="admin-console-grid admin-grid">
          {historyOperationCards.map(renderOperationCard)}

          <article className="admin-console-form admin-operation-card panel-surface">
            <div className="admin-console-card-copy">
              <div className="admin-operation-card-head">
                <p className="eyebrow">{t("admin.stockHistory.eyebrow")}</p>
                <span className="admin-warning-badge">{t("admin.longRunningWarning")}</span>
              </div>
              <h3>{t("admin.stockHistory.title")}</h3>
              <p className="admin-console-copy">{t("admin.stockHistory.description")}</p>
            </div>

            <div className="admin-console-form-grid">
              <input
                type="text"
                value={stockSymbol}
                onChange={(event) => setStockSymbol(event.target.value.toUpperCase())}
                className="admin-console-input"
                placeholder={t("admin.stockHistory.symbolPlaceholder")}
              />
              <input
                type="text"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
                className="admin-console-input"
                placeholder={t("admin.stockHistory.startDatePlaceholder")}
              />
              <input
                type="text"
                value={endDate}
                onChange={(event) => setEndDate(event.target.value)}
                className="admin-console-input"
                placeholder={t("admin.stockHistory.endDatePlaceholder")}
              />
            </div>

            <div className="admin-console-actions">
              <button
                type="button"
                className="admin-console-button"
                disabled={controlsDisabled}
                onClick={() =>
                  startJob("stock-history", () =>
                    triggerStockHistoryBackfill(buildPayload(stockSymbol, startDate, endDate)),
                  )
                }
              >
                <span className="admin-console-button-glow" />
                <span>{isBusy("stock-history") ? t("admin.running") : t("admin.stockHistory.action")}</span>
              </button>
              <button
                type="button"
                className="admin-console-button admin-console-button-secondary"
                disabled={controlsDisabled}
                onClick={() => {
                  setStockSymbol("");
                  setStartDate("");
                  setEndDate("");
                }}
              >
                <span className="admin-console-button-glow" />
                <span>{t("common.clear")}</span>
              </button>
            </div>

            {renderJobProgress("stock-history")}
          </article>
        </div>
      </section>
      <section className="admin-section">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">{t("admin.sections.macroEyebrow")}</p>
            <h3>{t("admin.sections.macroTitle")}</h3>
          </div>
        </div>
        <div className="admin-console-grid admin-grid">
          <article className="admin-console-card admin-operation-card panel-surface">
            <div className="admin-console-card-copy">
              <div className="admin-operation-card-head">
                <p className="eyebrow">{t("admin.cards.macroSyncAll.eyebrow")}</p>
              </div>
              <h3>{t("admin.cards.macroSyncAll.title")}</h3>
              <p>{t("admin.cards.macroSyncAll.description")}</p>
            </div>
            <button
              type="button"
              className="admin-console-button"
              disabled={controlsDisabled}
              onClick={runMacroSyncAll}
            >
              <span className="admin-console-button-glow" />
              <span>{isBusy("macro-all") ? t("admin.running") : t("admin.cards.macroSyncAll.action")}</span>
            </button>
          </article>
        </div>
      </section>

      <section className="admin-section">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Şirket Verileri</p>
            <h3>Şirket Veri Yönetimi</h3>
          </div>
        </div>
        <div className="admin-console-grid admin-grid">
          <article className="admin-console-card admin-operation-card panel-surface">
            <div className="admin-console-card-copy">
              <div className="admin-operation-card-head">
                <p className="eyebrow">Temel Analiz</p>
              </div>
              <h3>Mock Temel Analiz Verisi Oluştur</h3>
              <p>
                Mevcut gerçek oran verilerini ezmez, sadece oran kaydı olmayan şirketlere
                demo/mock temel analiz verisi oluşturur.
              </p>
            </div>
            <button
              type="button"
              className="admin-console-button"
              disabled={controlsDisabled}
              onClick={() => setShowMockRatioConfirm(true)}
            >
              <span className="admin-console-button-glow" />
              <span>{isBusy("seed-mock-ratios") ? t("admin.running") : "Mock Veri Oluştur"}</span>
            </button>
            {seedMockRatiosResult && (
              <div className="admin-console-job-status">
                <p className="admin-console-copy">
                  <strong>Yeni Profil:</strong> {seedMockRatiosResult.autoCreatedProfiles ?? 0}
                  {" · "}
                  <strong>Oluşturulan Oran:</strong> {seedMockRatiosResult.createdMockRatios ?? 0}
                  {" · "}
                  <strong>Atlanan:</strong> {seedMockRatiosResult.skippedExistingRatios ?? 0}
                  {" · "}
                  <strong>Hata:</strong> {seedMockRatiosResult.errors?.length ?? 0}
                </p>
                {seedMockRatiosResult.errors?.length > 0 && (
                  <ul className="admin-audit-reasons">
                    {seedMockRatiosResult.errors.map((err, index) => (
                      <li key={index}>{err}</li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </article>
        </div>
      </section>

      <section className="admin-section admin-console-result admin-response-panel panel-surface">
        <div className="admin-section-head">
          <div>
          <p className="eyebrow">{t("admin.result.eyebrow")}</p>
          <h3>{t("admin.result.title")}</h3>
          <p className="admin-console-copy">{t("admin.result.description")}</p>
          </div>
        </div>

        {renderResultPanel()}

        {completedJobKey ? <p className="admin-console-copy">{t("admin.completed")}</p> : null}
      </section>
    </div>
  );
}

function isJobCompleted(key, status) {
  if (!status) {
    return false;
  }

  if (key === "tefas-fund-fetch") {
    return (
      Number.isFinite(Number(status.processedFunds)) ||
      Number.isFinite(Number(status.savedFunds)) ||
      Boolean(status.lastError)
    );
  }

  return Number(status.total) > 0 || Boolean(status.lastError);
}
