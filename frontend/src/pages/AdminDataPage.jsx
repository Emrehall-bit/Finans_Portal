import { createElement, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Activity,
  AlertTriangle,
  Calculator,
  CheckCircle2,
  Database,
  FilePlus2,
  FileSpreadsheet,
  Hash,
  Info,
  Loader2,
  PencilLine,
  RefreshCw,
  RotateCw,
  UploadCloud,
  XCircle,
} from "lucide-react";
import {
  getBinanceHistoryFetchStatus,
  getStockFetchStatus,
  getStockHistoryBackfillStatus,
  getTefasFundBackfillStatus,
  getTefasFundFetchStatus,
  getTcmbHistoryBackfillStatus,
  getTcmbSyncStatus,
  triggerBinanceHistoryFetch,
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
  importCompanyFinancialCsv,
  seedMockDerivatives,
} from "../api/adminApi";
import { repairNewsCategories, syncNews } from "../api/newsApi";
import { extractErrorMessage } from "../api/responseUtils";
import { getNewsProviderLabel } from "../components/news/newsCardUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";

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
  const [categoryRepairLimit, setCategoryRepairLimit] = useState("500");
  const [jobProgress, setJobProgress] = useState(null);
  const [completedJobKey, setCompletedJobKey] = useState(null);
  const [financialImportFile, setFinancialImportFile] = useState(null);
  const [financialImportDryRun, setFinancialImportDryRun] = useState(true);
  const [financialImportReplace, setFinancialImportReplace] = useState(true);
  const [financialImportRecalc, setFinancialImportRecalc] = useState(true);
  const [financialImportResult, setFinancialImportResult] = useState(null);
  const pollingRef = useRef(null);
  const actionsLockedRef = useRef(false);

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
      {
        key: "mock-derivatives-seed",
        group: "live",
        eyebrow: "Mock Data",
        title: "VİOP & Tahvil Mock Seed",
        description:
          "23 vadeli işlem ve 22 tahvil/faiz enstrümanı ile 3 yıllık geçmiş veri üretir. İdempotent — tekrar çalışınca duplicate oluşturmaz.",
        actionLabel: "Seed Çalıştır",
        onClick: () => runAction("mock-derivatives-seed", seedMockDerivatives),
      },
    ],
    [
      binanceDays,
      categoryRepairLimit,
      commodityHistoryDays,
      indexHistoryDays,
      tefasFundCode,
      tefasPeriod,
      t,
    ],
  );

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

    return <pre className="admin-console-result-box">{JSON.stringify(result, null, 2)}</pre>;
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
            <span>{t("admin.status.lastOperationTime")}</span>
            <strong>-</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.status.lastOperationStatus")}</span>
            <strong>{operationStatusLabel}</strong>
          </div>
        </div>
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

        <div className="admin-financial-import-grid">
          <article className="admin-financial-card admin-financial-upload-card">
            <div className="admin-financial-card-head">
              <div>
                <p className="eyebrow">Dosya Yükleme</p>
                <h4>KAP XLSX veya Manual CSV</h4>
              </div>
              <span className="admin-financial-icon"><FileSpreadsheet size={18} /></span>
            </div>
            <label className={`admin-upload-dropzone${controlsDisabled ? " disabled" : ""}`} htmlFor="financial-import-file">
              <input
                id="financial-import-file"
                type="file"
                accept=".xlsx,.xls,.csv"
                onChange={(e) => setFinancialImportFile(e.target.files?.[0] ?? null)}
                disabled={controlsDisabled}
              />
              <span className="admin-upload-icon"><UploadCloud size={28} /></span>
              <span className="admin-upload-title">
                {financialImportFile ? financialImportFile.name : "Dosya seçin veya buraya sürükleyin"}
              </span>
              <span className="admin-upload-meta">Desteklenen formatlar: XLSX, CSV</span>
            </label>
          </article>

          <article className="admin-financial-card">
            <div className="admin-financial-card-head">
              <div>
                <p className="eyebrow">Import Ayarları</p>
                <h4>Çalıştırma modu</h4>
              </div>
              <span className="admin-financial-icon"><Database size={18} /></span>
            </div>
            <div className="admin-import-options">
              <ImportOptionCard
                icon={RotateCw}
                title="Dry-run"
                description="Kaydetmeden önizleme ve sayım yapar."
                checked={financialImportDryRun}
                disabled={controlsDisabled}
                onChange={setFinancialImportDryRun}
              />
              <ImportOptionCard
                icon={RefreshCw}
                title="Overwrite"
                description="Mevcut finansal değerleri günceller."
                checked={financialImportReplace}
                disabled={controlsDisabled}
                onChange={setFinancialImportReplace}
              />
              <ImportOptionCard
                icon={Calculator}
                title="Oran hesaplama"
                description="Import sonrası temel analiz oranlarını yeniler."
                checked={financialImportRecalc}
                disabled={controlsDisabled}
                onChange={setFinancialImportRecalc}
              />
            </div>
            <button
              type="button"
              className="admin-console-button admin-financial-import-button"
              disabled={controlsDisabled || !financialImportFile}
              onClick={handleFinancialImport}
            >
              <span className="admin-console-button-glow" />
              <span className="admin-import-button-content">
                {isBusy("financial-import") ? <Loader2 className="admin-button-spinner" size={18} /> : <UploadCloud size={18} />}
                {isBusy("financial-import")
                  ? t("admin.running")
                  : financialImportDryRun
                  ? "Dry-run Başlat"
                  : "Import Et"}
              </span>
            </button>
          </article>

          <article className="admin-financial-card admin-financial-summary-card">
            <div className="admin-financial-card-head">
              <div>
                <p className="eyebrow">İşlem Özeti</p>
                <h4>DRY-RUN metrikleri</h4>
              </div>
              {financialImportResult?.dryRun ? <span className="summary-chip">DRY-RUN</span> : null}
            </div>
            {financialImportResult ? (
              <div className="admin-financial-metrics">
                <FinancialMetric icon={FilePlus2} label="Yeni Rapor" value={financialImportResult.createdReports ?? 0} />
                <FinancialMetric icon={PencilLine} label="Güncellenen Rapor" value={financialImportResult.updatedReports ?? 0} />
                <FinancialMetric icon={Hash} label="Yeni Değer" value={financialImportResult.createdValues ?? 0} />
                <FinancialMetric icon={Activity} label="Güncellenen Değer" value={financialImportResult.updatedValues ?? 0} />
                <FinancialMetric icon={Calculator} label="Oran Hesaplanan" value={financialImportResult.recalculatedTickers?.length ?? 0} />
                <FinancialMetric icon={XCircle} label="Hata" value={financialImportResult.validationErrors?.length ?? 0} tone="danger" />
              </div>
            ) : (
              <StatusPanel
                tone="info"
                icon={Info}
                title="Henüz işlem sonucu yok"
                description="Dosya seçip dry-run veya import başlattığınızda metrikler burada görünür."
              />
            )}
          </article>

          <article className="admin-financial-card admin-financial-response-card">
            <div className="admin-financial-card-head">
              <div>
                <p className="eyebrow">Backend Cevabı</p>
                <h4>Sonuç durumu</h4>
              </div>
            </div>
            {financialImportResult ? (
              <StatusPanel
                tone={(financialImportResult.validationErrors?.length ?? 0) > 0 ? "error" : "success"}
                icon={(financialImportResult.validationErrors?.length ?? 0) > 0 ? AlertTriangle : CheckCircle2}
                title={(financialImportResult.validationErrors?.length ?? 0) > 0 ? "Kontrol gereken satırlar var" : "Import cevabı başarılı"}
                description={financialImportResult.dryRun ? "DRY-RUN modunda veri kaydedilmedi." : "Backend işlemi tamamladı."}
              />
            ) : (
              <StatusPanel
                tone="info"
                icon={Info}
                title="Cevap bekleniyor"
                description="Import sonucunda backend mesajları, uyarılar ve validasyon hataları burada listelenir."
              />
            )}
            {financialImportResult && (
              <div className="admin-financial-response-details">
                {Array.isArray(financialImportResult.recalculatedTickers) && financialImportResult.recalculatedTickers.length > 0 && (
                  <p className="admin-console-copy">
                    Oran hesaplanan: {financialImportResult.recalculatedTickers.join(", ")}
                  </p>
                )}
                {Array.isArray(financialImportResult.missingShareCountWarnings) && financialImportResult.missingShareCountWarnings.length > 0 && (
                  <div className="admin-financial-warning">
                    <AlertTriangle size={16} />
                    <p>
                      Hisse sayısı eksik (oranlar eksik kalabilir):{" "}
                      {financialImportResult.missingShareCountWarnings.join(", ")}
                    </p>
                  </div>
                )}
                {Array.isArray(financialImportResult.validationErrors) && financialImportResult.validationErrors.length > 0 && (
                  <ul className="admin-audit-reasons admin-financial-error-list">
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

          <article className="admin-financial-legacy-hidden" aria-hidden="true">
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

function ImportOptionCard({ icon, title, description, checked, disabled, onChange }) {
  const optionIcon = createElement(icon, { size: 18 });

  return (
    <label className={`admin-import-option-card${checked ? " active" : ""}${disabled ? " disabled" : ""}`}>
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        disabled={disabled}
      />
      <span className="admin-import-option-icon">{optionIcon}</span>
      <span className="admin-import-option-copy">
        <strong>{title}</strong>
        <span>{description}</span>
      </span>
      <span className="admin-import-option-toggle" aria-hidden="true">
        <span />
      </span>
    </label>
  );
}

function FinancialMetric({ icon, label, value, tone = "default" }) {
  const metricIcon = createElement(icon, { size: 17 });

  return (
    <div className={`admin-financial-metric-card ${tone}`}>
      <span className="admin-financial-metric-icon">{metricIcon}</span>
      <span className="admin-financial-metric-label">{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StatusPanel({ icon, title, description, tone }) {
  const statusIcon = createElement(icon, { size: 20 });

  return (
    <div className={`admin-financial-status-panel ${tone}`}>
      <span className="admin-financial-status-icon">{statusIcon}</span>
      <div>
        <strong>{title}</strong>
        <p>{description}</p>
      </div>
    </div>
  );
}
