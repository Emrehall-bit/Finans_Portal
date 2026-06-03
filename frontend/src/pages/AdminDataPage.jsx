import { useEffect, useMemo, useRef, useState } from "react";
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
  importCompanyFinancialCsv,
} from "../api/adminApi";
import { auditAffectedInstruments, repairNewsCategories, syncNews } from "../api/newsApi";
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
  const [categoryRepairLimit, setCategoryRepairLimit] = useState("500");
  const [marketTapeSymbols, setMarketTapeSymbols] = useState([]);
  const [marketTapeCatalog, setMarketTapeCatalog] = useState([]);
  const [marketTapeSearch, setMarketTapeSearch] = useState("");
  const [marketTapeLoaded, setMarketTapeLoaded] = useState(false);
  const [jobProgress, setJobProgress] = useState(null);
  const [completedJobKey, setCompletedJobKey] = useState(null);
  const [financialImportFile, setFinancialImportFile] = useState(null);
  const [financialImportDryRun, setFinancialImportDryRun] = useState(true);
  const [financialImportReplace, setFinancialImportReplace] = useState(true);
  const [financialImportRecalc, setFinancialImportRecalc] = useState(true);
  const [financialImportResult, setFinancialImportResult] = useState(null);
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
        key: "news-category-repair",
        group: "live",
        eyebrow: "News Repair",
        title: "DB Category Repair",
        description:
          "KAP dışı haberlerde DB'deki eski category değerlerini mevcut classifier sonucuyla karşılaştırır. Önce dry-run çalıştır.",
        input: (
          <input
            type="number"
            min="1"
            max="5000"
            step="1"
            value={categoryRepairLimit}
            onChange={(event) => setCategoryRepairLimit(event.target.value)}
            className="admin-console-input"
            placeholder="500"
          />
        ),
        actions: [
          {
            key: "news-category-repair-dry-run",
            label: "Dry-run",
            onClick: () =>
              runAction("news-category-repair-dry-run", () =>
                repairNewsCategories({
                  limit: Number.parseInt(categoryRepairLimit, 10) || 500,
                  dryRun: true,
                }),
              ),
          },
          {
            key: "news-category-repair-apply",
            label: "DB'ye uygula",
            secondary: true,
            onClick: () => {
              const confirmed = window.confirm(
                "Category repair dryRun=false çalışacak ve DB'deki category değerlerini güncelleyecek. Devam edilsin mi?",
              );
              if (!confirmed) {
                return;
              }
              return runAction("news-category-repair-apply", () =>
                repairNewsCategories({
                  limit: Number.parseInt(categoryRepairLimit, 10) || 500,
                  dryRun: false,
                }),
              );
            },
          },
        ],
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
    [
      affectedAuditLimit,
      binanceDays,
      categoryRepairLimit,
      commodityHistoryDays,
      indexHistoryDays,
      tefasFundCode,
      tefasPeriod,
      t,
    ],
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

  const handleFinancialImport = async () => {
    if (!financialImportFile || !lockActions()) return;
    setBusyKey("financial-import");
    setError("");
    setFinancialImportResult(null);
    try {
      const response = await importCompanyFinancialCsv({
        file: financialImportFile,
        dryRun: financialImportDryRun,
        replaceExisting: financialImportReplace,
        recalculateRatios: financialImportRecalc,
        overwriteShareCount: false,
      });
      setFinancialImportResult(response?.data ?? null);
    } catch (err) {
      setError(extractErrorMessage(err, "Finansal import hatası."));
    } finally {
      setBusyKey((c) => (c === "financial-import" ? null : c));
      unlockActions();
    }
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
      {Array.isArray(card.actions) && card.actions.length > 0 ? (
        <div className="admin-console-actions admin-operation-actions">
          {card.actions.map((action) => (
            <button
              key={action.key}
              type="button"
              className={`admin-console-button${action.secondary ? " admin-console-button-secondary" : ""}`}
              disabled={controlsDisabled}
              onClick={action.onClick}
            >
              <span className="admin-console-button-glow" />
              <span>{isBusy(action.key) ? t("admin.running") : action.label}</span>
            </button>
          ))}
        </div>
      ) : (
        <button
          type="button"
          className="admin-console-button"
          disabled={controlsDisabled}
          onClick={card.onClick}
        >
          <span className="admin-console-button-glow" />
          <span>{isBusy(card.key) ? t("admin.running") : card.actionLabel}</span>
        </button>
      )}
      {renderJobProgress(card.key)}
    </article>
  );

  const isAffectedInstrumentAuditResult =
    result &&
    typeof result === "object" &&
    typeof result.checkedCount === "number" &&
    Array.isArray(result.suspiciousItems);

  const isCategoryRepairResult =
    result &&
    typeof result === "object" &&
    typeof result.processedCount === "number" &&
    typeof result.changedCategoryCount === "number" &&
    Array.isArray(result.sampleChanges);

  const renderResultPanel = () => {
    if (!result) {
      return (
        <EmptyState
          title={t("admin.result.emptyTitle")}
          description={t("admin.result.emptyDescription")}
        />
      );
    }

    if (isCategoryRepairResult) {
      const sampleChanges = result.sampleChanges ?? [];

      return (
        <div className="admin-audit-result">
          <div className="admin-audit-metrics">
            <div className="admin-console-metric-card">
              <span>Processed</span>
              <strong>{result.processedCount ?? 0}</strong>
            </div>
            <div className="admin-console-metric-card">
              <span>Changed</span>
              <strong>{result.changedCategoryCount ?? 0}</strong>
            </div>
            <div className="admin-console-metric-card">
              <span>Unchanged</span>
              <strong>{result.unchangedCount ?? 0}</strong>
            </div>
            <div className="admin-console-metric-card">
              <span>Skipped KAP</span>
              <strong>{result.skippedKapCount ?? 0}</strong>
            </div>
          </div>

          {sampleChanges.length === 0 ? (
            <EmptyState
              title="Category repair değişikliği yok"
              description="Mevcut limit içinde classifier sonucuna göre category değişimi bulunmadı."
            />
          ) : (
            <div className="admin-audit-list">
              {sampleChanges.map((item) => (
                <article key={item.id} className="admin-audit-item">
                  <div className="admin-audit-item-head">
                    <strong>{item.title}</strong>
                    <span className="summary-chip">#{item.id}</span>
                  </div>
                  <div className="admin-audit-meta">
                    <span>Old: {item.oldCategory || "-"}</span>
                    <span>New: {item.newCategory || "-"}</span>
                  </div>
                  {item.reason ? <p className="admin-console-copy">{item.reason}</p> : null}
                </article>
              ))}
            </div>
          )}
        </div>
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

      <section className="admin-section panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Şirket Finansalları</p>
            <h3>Finansal Veri İmport</h3>
            <p className="admin-console-copy">
              KAP "Finansal Tablo Kalem Sorgulama" XLSX veya manuel CSV dosyasını yükleyin.
              Format otomatik algılanır. Önce <strong>Dry-run</strong> ile sonuçları önizleyin.
            </p>
          </div>
        </div>

        <div className="admin-console-grid admin-grid">
          <article className="admin-console-card admin-operation-card panel-surface">
            <div className="admin-console-card-copy">
              <div className="admin-operation-card-head">
                <p className="eyebrow">XLSX / CSV</p>
              </div>
              <h3>Finansal Tablo İmport</h3>
              <p>
                KAP XLSX veya manual CSV. Import sonrası <em>recalculateRatios=true</em> ise
                gerçek oranlar hesaplanır ve Temel Analiz sekmesi güncellenir.
              </p>
            </div>

            <div className="admin-console-form-grid">
              <input
                type="file"
                accept=".xlsx,.xls,.csv"
                className="admin-console-input"
                onChange={(e) => setFinancialImportFile(e.target.files?.[0] ?? null)}
                disabled={controlsDisabled}
              />
              <label className="admin-console-label" style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <input
                  type="checkbox"
                  checked={financialImportDryRun}
                  onChange={(e) => setFinancialImportDryRun(e.target.checked)}
                  disabled={controlsDisabled}
                />
                Dry-run (kaydetme, sadece say)
              </label>
              <label className="admin-console-label" style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <input
                  type="checkbox"
                  checked={financialImportReplace}
                  onChange={(e) => setFinancialImportReplace(e.target.checked)}
                  disabled={controlsDisabled}
                />
                Mevcut değerleri güncelle
              </label>
              <label className="admin-console-label" style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <input
                  type="checkbox"
                  checked={financialImportRecalc}
                  onChange={(e) => setFinancialImportRecalc(e.target.checked)}
                  disabled={controlsDisabled}
                />
                Import sonrası oranları hesapla
              </label>
            </div>

            <button
              type="button"
              className="admin-console-button"
              disabled={controlsDisabled || !financialImportFile}
              onClick={handleFinancialImport}
            >
              <span className="admin-console-button-glow" />
              <span>
                {isBusy("financial-import")
                  ? t("admin.running")
                  : financialImportDryRun
                  ? "Dry-run Başlat"
                  : "Import Et"}
              </span>
            </button>

            {financialImportResult && (
              <div className="admin-console-job-status">
                {financialImportResult.dryRun && (
                  <p className="admin-console-copy"><strong>DRY-RUN — veri kaydedilmedi</strong></p>
                )}
                <div className="admin-audit-metrics" style={{ flexWrap: "wrap" }}>
                  <div className="admin-console-metric-card">
                    <span>Yeni Rapor</span>
                    <strong>{financialImportResult.createdReports ?? 0}</strong>
                  </div>
                  <div className="admin-console-metric-card">
                    <span>Güncellenen Rapor</span>
                    <strong>{financialImportResult.updatedReports ?? 0}</strong>
                  </div>
                  <div className="admin-console-metric-card">
                    <span>Yeni Değer</span>
                    <strong>{financialImportResult.createdValues ?? 0}</strong>
                  </div>
                  <div className="admin-console-metric-card">
                    <span>Güncellenen Değer</span>
                    <strong>{financialImportResult.updatedValues ?? 0}</strong>
                  </div>
                  <div className="admin-console-metric-card">
                    <span>Oran Hesaplanan</span>
                    <strong>{financialImportResult.recalculatedTickers?.length ?? 0}</strong>
                  </div>
                  <div className="admin-console-metric-card">
                    <span>Hata</span>
                    <strong>{financialImportResult.validationErrors?.length ?? 0}</strong>
                  </div>
                </div>
                {Array.isArray(financialImportResult.recalculatedTickers) && financialImportResult.recalculatedTickers.length > 0 && (
                  <p className="admin-console-copy">
                    Oran hesaplanan: {financialImportResult.recalculatedTickers.join(", ")}
                  </p>
                )}
                {Array.isArray(financialImportResult.missingShareCountWarnings) && financialImportResult.missingShareCountWarnings.length > 0 && (
                  <p className="admin-console-copy" style={{ color: "var(--color-warning, #f59e0b)" }}>
                    ⚠ Hisse sayısı eksik (oranlar eksik kalabilir):{" "}
                    {financialImportResult.missingShareCountWarnings.join(", ")}
                  </p>
                )}
                {Array.isArray(financialImportResult.validationErrors) && financialImportResult.validationErrors.length > 0 && (
                  <ul className="admin-audit-reasons">
                    {financialImportResult.validationErrors.slice(0, 10).map((err, i) => (
                      <li key={i}>
                        {err.lineNumber ? `Satır ${err.lineNumber}: ` : ""}
                        {err.tickerCode ? `[${err.tickerCode}] ` : ""}
                        {err.message}
                      </li>
                    ))}
                    {financialImportResult.validationErrors.length > 10 && (
                      <li>... ve {financialImportResult.validationErrors.length - 10} hata daha</li>
                    )}
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
