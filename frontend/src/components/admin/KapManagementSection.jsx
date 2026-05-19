import { useState } from "react";
import {
  backfillCompanyDisclosures,
  backfillCompanyFinancials,
  calculateCompanyRatios,
  debugFetchCompanyFinancialTable,
  parsePendingReports,
  syncAllCompanyDisclosures,
  syncCompanyDisclosures,
  syncCompanyFinancialReports,
} from "../../api/adminApi";
import { extractErrorMessage } from "../../api/responseUtils";
import ErrorMessage from "../common/ErrorMessage";

const PRESET_TICKERS = ["THYAO", "SISE", "TUPRS", "KCHOL", "BIMAS"];

function kapErrorMessage(err) {
  const status = err?.response?.status;
  if (status === 401) return "Oturum süresi dolmuş veya giriş yapılmamış.";
  if (status === 403) return "Bu işlem için ADMIN yetkisi gerekiyor.";
  return extractErrorMessage(err, "Beklenmedik bir hata oluştu.");
}

export default function KapManagementSection() {
  const [ticker, setTicker] = useState("THYAO");
  const [backfillDays, setBackfillDays] = useState("1825");
  const [financialBackfillStartYear, setFinancialBackfillStartYear] = useState("2021");
  const [financialBackfillEndYear, setFinancialBackfillEndYear] = useState("2026");
  const [debugYear, setDebugYear] = useState("2025");
  const [debugPeriod, setDebugPeriod] = useState("1");
  const [busyKey, setBusyKey] = useState(null);
  const [kapResult, setKapResult] = useState(null);
  const [kapError, setKapError] = useState("");

  const run = async (key, executor) => {
    if (busyKey !== null) return;
    setBusyKey(key);
    setKapError("");
    setKapResult(null);
    try {
      const result = await executor();
      setKapResult(result ?? null);
    } catch (err) {
      setKapError(kapErrorMessage(err));
    } finally {
      setBusyKey(null);
    }
  };

  const t = ticker.trim().toUpperCase();
  const isDisabled = busyKey !== null;
  const isTickerRequired = busyKey !== null || !ticker.trim();

  const actions = [
    {
      key: "kap-disclosures-sync",
      label: "KAP Bildirimlerini Senkronize Et",
      requiresTicker: true,
      onClick: () => run("kap-disclosures-sync", () => syncCompanyDisclosures(t)),
    },
    {
      key: "kap-disclosures-backfill",
      label: "KAP Geçmiş Bildirim Backfill",
      requiresTicker: true,
      onClick: () => run("kap-disclosures-backfill", () => backfillCompanyDisclosures(t, backfillDays)),
    },
    {
      key: "kap-financial-reports-sync",
      label: "Finansal Raporları Senkronize Et",
      requiresTicker: true,
      onClick: () => run("kap-financial-reports-sync", () => syncCompanyFinancialReports(t)),
    },
    {
      key: "kap-financials-backfill",
      label: "KAP Finansal Backfill",
      requiresTicker: true,
      onClick: () => run("kap-financials-backfill", () => backfillCompanyFinancials(t, {
        startYear: Number(financialBackfillStartYear),
        endYear: Number(financialBackfillEndYear),
      })),
    },
    {
      key: "kap-parse-pending",
      label: "Bekleyen Raporları Ayrıştır",
      requiresTicker: true,
      onClick: () => run("kap-parse-pending", () => parsePendingReports(t, { includeFailed: true, forceReparse: true })),
    },
    {
      key: "kap-ratios-calculate",
      label: "Temel Oranları Hesapla",
      requiresTicker: true,
      onClick: () => run("kap-ratios-calculate", () => calculateCompanyRatios(t)),
    },
    {
      key: "kap-financial-table-debug",
      label: "KAP Tablo Debug Fetch",
      requiresTicker: true,
      onClick: () => run("kap-financial-table-debug", () => debugFetchCompanyFinancialTable(t, debugYear, debugPeriod)),
    },
    {
      key: "kap-sync-all",
      label: "Tüm Şirketler — KAP Bildirim Sync",
      requiresTicker: false,
      onClick: () => run("kap-sync-all", () => syncAllCompanyDisclosures()),
    },
  ];

  return (
    <section className="admin-console-form panel-surface">
      <div>
        <p className="eyebrow">Şirket Yönetimi</p>
        <h3>KAP / Temel Analiz Yönetimi</h3>
        <p className="admin-console-copy">
          Seçili şirket için KAP bildirimlerini ve finansal verileri senkronize edin, raporları ayrıştırın ve temel oranları hesaplayın.
        </p>
      </div>

      <div className="kap-ticker-row">
        <input
          type="text"
          className="admin-console-input kap-ticker-input"
          value={ticker}
          onChange={(e) => setTicker(e.target.value.toUpperCase())}
          placeholder="Ticker (örn. THYAO)"
          disabled={isDisabled}
          maxLength={10}
        />
        <div className="kap-preset-chips">
          {PRESET_TICKERS.map((preset) => (
            <button
              key={preset}
              type="button"
              className={`kap-preset-chip${ticker.trim().toUpperCase() === preset ? " active" : ""}`}
              onClick={() => setTicker(preset)}
              disabled={isDisabled}
            >
              {preset}
            </button>
          ))}
        </div>
      </div>

      <div className="kap-debug-row">
        <input
          type="number"
          className="admin-console-input kap-debug-input"
          value={backfillDays}
          onChange={(e) => setBackfillDays(e.target.value)}
          placeholder="Backfill gün"
          disabled={isDisabled}
          min="1"
          max="3650"
        />
      </div>

      <div className="kap-debug-row">
        <input
          type="number"
          className="admin-console-input kap-debug-input"
          value={financialBackfillStartYear}
          onChange={(e) => setFinancialBackfillStartYear(e.target.value)}
          placeholder="Başlangıç yıl"
          disabled={isDisabled}
          min="2000"
          max="2100"
        />
        <input
          type="number"
          className="admin-console-input kap-debug-input"
          value={financialBackfillEndYear}
          onChange={(e) => setFinancialBackfillEndYear(e.target.value)}
          placeholder="Bitiş yıl"
          disabled={isDisabled}
          min="2000"
          max="2100"
        />
      </div>

      <div className="kap-debug-row">
        <input
          type="number"
          className="admin-console-input kap-debug-input"
          value={debugYear}
          onChange={(e) => setDebugYear(e.target.value)}
          placeholder="Yıl"
          disabled={isDisabled}
          min="2000"
          max="2100"
        />
        <select
          className="admin-console-input kap-debug-input"
          value={debugPeriod}
          onChange={(e) => setDebugPeriod(e.target.value)}
          disabled={isDisabled}
        >
          <option value="1">Period 1</option>
          <option value="2">Period 2</option>
          <option value="3">Period 3</option>
          <option value="4">Period 4</option>
        </select>
      </div>

      <div className="admin-console-actions kap-actions-grid">
        {actions.map((action) => (
          <button
            key={action.key}
            type="button"
            className="admin-console-button"
            disabled={isDisabled || (action.requiresTicker && !ticker.trim())}
            onClick={action.onClick}
          >
            <span className="admin-console-button-glow" />
            <span>{busyKey === action.key ? "Çalışıyor…" : action.label}</span>
          </button>
        ))}
      </div>

      {kapError ? <ErrorMessage message={kapError} /> : null}

      {kapResult?.message ? <div className="status-box info">{kapResult.message}</div> : null}

      {kapResult ? (
        <pre className="admin-console-result-box">{JSON.stringify(kapResult, null, 2)}</pre>
      ) : null}
    </section>
  );
}
