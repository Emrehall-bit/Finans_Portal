import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  getBinanceHistoryFetchStatus,
  getStockFetchStatus,
  getStockHistoryBackfillStatus,
  getTcmbHistoryBackfillStatus,
  getTcmbSyncStatus,
  triggerBinanceHistoryFetch,
  triggerStockFetch,
  triggerStockHistoryBackfill,
  triggerTcmbHistoryBackfill,
  triggerTcmbSync,
} from "../api/adminApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import PageHeader from "../components/common/PageHeader";

function buildPayload(symbol, startDate, endDate) {
  return {
    symbol: symbol.trim() || null,
    startDate: startDate || null,
    endDate: endDate || null,
  };
}

export default function AdminPage() {
  const { t } = useTranslation();
  const [busyKey, setBusyKey] = useState(null);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [stockSymbol, setStockSymbol] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [binanceDays, setBinanceDays] = useState("1825");
  const [jobProgress, setJobProgress] = useState(null);
  const [completedJobKey, setCompletedJobKey] = useState(null);
  const pollingRef = useRef(null);

  const statusLoaders = useMemo(
    () => ({
      "stock-fetch": getStockFetchStatus,
      "stock-history": getStockHistoryBackfillStatus,
      "tcmb-sync": getTcmbSyncStatus,
      "tcmb-history": getTcmbHistoryBackfillStatus,
      "binance-history": getBinanceHistoryFetchStatus,
    }),
    [],
  );

  const stopJobPolling = () => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  };

  useEffect(() => () => stopJobPolling(), []);

  const pollJobStatus = async (key) => {
    const statusLoader = statusLoaders[key];
    if (!statusLoader) {
      stopJobPolling();
      setBusyKey((current) => (current === key ? null : current));
      return;
    }

    try {
      const status = await statusLoader();
      if (!status) {
        return;
      }

      setJobProgress({ key, ...status });

      if (!status.running && status.total > 0) {
        stopJobPolling();
        setBusyKey((current) => (current === key ? null : current));
        setCompletedJobKey(key);
      }
    } catch (err) {
      stopJobPolling();
      setBusyKey((current) => (current === key ? null : current));
      setJobProgress(null);
      setError(extractErrorMessage(err, t("admin.actionError")));
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
    }
  };

  const actionCards = useMemo(
    () => [
      {
        key: "stock-fetch",
        eyebrow: t("admin.cards.stockFetch.eyebrow"),
        title: t("admin.cards.stockFetch.title"),
        description: t("admin.cards.stockFetch.description"),
        actionLabel: t("admin.cards.stockFetch.action"),
        onClick: () => startJob("stock-fetch", triggerStockFetch),
      },
      {
        key: "tcmb-sync",
        eyebrow: t("admin.cards.tcmbSync.eyebrow"),
        title: t("admin.cards.tcmbSync.title"),
        description: t("admin.cards.tcmbSync.description"),
        actionLabel: t("admin.cards.tcmbSync.action"),
        onClick: () => startJob("tcmb-sync", triggerTcmbSync),
      },
      {
        key: "tcmb-history",
        eyebrow: t("admin.cards.tcmbHistory.eyebrow"),
        title: t("admin.cards.tcmbHistory.title"),
        description: t("admin.cards.tcmbHistory.description"),
        actionLabel: t("admin.cards.tcmbHistory.action"),
        onClick: () => startJob("tcmb-history", triggerTcmbHistoryBackfill),
      },
      {
        key: "binance-history",
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
    ],
    [binanceDays, t],
  );

  const isBusy = useMemo(() => (key) => busyKey === key, [busyKey]);

  const renderJobProgress = (key) => {
    if (jobProgress?.key === key) {
      if (jobProgress.running) {
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
        return <p className="admin-console-copy">{t("admin.completed")}</p>;
      }
    }

    if (completedJobKey === key) {
      return <p className="admin-console-copy">{t("admin.completed")}</p>;
    }

    return null;
  };

  return (
    <div className="dashboard-stack admin-console-shell">
      <PageHeader
        eyebrow={t("admin.eyebrow")}
        title={t("admin.title")}
        description={t("admin.description")}
      />

      {error ? <ErrorMessage message={error} /> : null}

      <section className="admin-console-hero panel-surface">
        <div className="admin-console-hero-copy">
          <p className="eyebrow">{t("admin.hero.eyebrow")}</p>
          <h2>{t("admin.hero.title")}</h2>
          <p>{t("admin.hero.description")}</p>
        </div>
        <div className="admin-console-hero-metrics">
          <div className="admin-console-metric-card">
            <span>{t("admin.metrics.operationType.label")}</span>
            <strong>{t("admin.metrics.operationType.value")}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.metrics.auth.label")}</span>
            <strong>{t("admin.metrics.auth.value")}</strong>
          </div>
          <div className="admin-console-metric-card">
            <span>{t("admin.metrics.status.label")}</span>
            <strong>{busyKey ? t("admin.metrics.status.running") : t("admin.metrics.status.ready")}</strong>
          </div>
        </div>
      </section>

      <section className="admin-console-grid">
        {actionCards.map((card) => (
          <article key={card.key} className="admin-console-card panel-surface">
            <div className="admin-console-card-copy">
              <p className="eyebrow">{card.eyebrow}</p>
              <h3>{card.title}</h3>
              <p>{card.description}</p>
            </div>
            {card.input ?? null}
            <button
              type="button"
              className="admin-console-button"
              disabled={busyKey !== null}
              onClick={card.onClick}
            >
              <span className="admin-console-button-glow" />
              <span>{isBusy(card.key) ? t("admin.running") : card.actionLabel}</span>
            </button>
            {renderJobProgress(card.key)}
          </article>
        ))}
      </section>

      <section className="admin-console-form panel-surface">
        <div>
          <p className="eyebrow">{t("admin.stockHistory.eyebrow")}</p>
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
            disabled={isBusy("stock-history")}
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
            disabled={isBusy("stock-history")}
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
      </section>

      <section className="admin-console-result panel-surface">
        <div>
          <p className="eyebrow">{t("admin.result.eyebrow")}</p>
          <h3>{t("admin.result.title")}</h3>
          <p className="admin-console-copy">{t("admin.result.description")}</p>
        </div>

        {!result ? (
          <EmptyState
            title={t("admin.result.emptyTitle")}
            description={t("admin.result.emptyDescription")}
          />
        ) : (
          <pre className="admin-console-result-box">{JSON.stringify(result, null, 2)}</pre>
        )}

        {completedJobKey ? <p className="admin-console-copy">{t("admin.completed")}</p> : null}
      </section>
    </div>
  );
}
