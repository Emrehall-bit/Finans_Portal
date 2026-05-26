import { useMemo, useState } from "react";
import { Download, Upload } from "lucide-react";
import { importCompanyFinancialCsv, importCompanyShareCounts } from "../api/adminApi";
import { extractErrorMessage } from "../api/responseUtils";

const TEMPLATE_CSV = [
  "ticker_code,period_year,report_type,published_at,source_url,item_key,raw_label,value,currency,unit_multiplier,shares_outstanding",
  "TUPRS,2024,Q1,2024-05-02,manual://csv,HASILAT,Hasilat,275000000000,TRY,1,275256514",
  "TUPRS,2024,Q1,2024-05-02,manual://csv,BRUT_KAR,Brut Kar,42000000000,TRY,1,275256514",
  "TUPRS,2024,Q1,2024-05-02,manual://csv,NET_DONEM_KARI,Net Donem Kari,12300000000,TRY,1,275256514",
  "TUPRS,2024,Q1,2024-05-02,manual://csv,OZKAYNAKLAR,Ozkaynaklar,198000000000,TRY,1,275256514",
  "ASELS,2024,Q1,2024-05-03,manual://csv,HASILAT,Hasilat,22100000000,TRY,1,4560000000",
  "ASELS,2024,Q1,2024-05-03,manual://csv,BRUT_KAR,Brut Kar,6400000000,TRY,1,4560000000",
  "ASELS,2024,Q1,2024-05-03,manual://csv,NET_DONEM_KARI,Net Donem Kari,5400000000,TRY,1,4560000000",
  "ASELS,2024,Q1,2024-05-03,manual://csv,OZKAYNAKLAR,Ozkaynaklar,84500000000,TRY,1,4560000000",
  "BIMAS,2025,ANNUAL,2025-12-31,manual://csv,BRUT_KAR,Brut Kar,0,TRY,1,",
].join("\n");

const SHARE_COUNT_TEMPLATE_CSV = [
  "ticker_code,shares_outstanding",
  "AKSA,3885000000",
  "AKSEN,1226338236",
  "ARCLK,675728205",
].join("\n");

const SUPPORTED_ITEM_KEYS = [
  "HASILAT",
  "BRUT_KAR",
  "NET_DONEM_KARI",
  "OZKAYNAKLAR",
  "TOPLAM_VARLIKLAR",
  "TOPLAM_KAYNAKLAR",
  "TOPLAM_YUKUMLULUKLER",
  "KISA_VADELI_YUKUMLULUKLER",
  "UZUN_VADELI_YUKUMLULUKLER",
];

function mapValidationErrors(result) {
  if (!Array.isArray(result?.validationErrors)) {
    return [];
  }

  return result.validationErrors.map((item, index) => ({
    key: `${item?.lineNumber ?? "na"}-${item?.tickerCode ?? "na"}-${index}`,
    rowNumber: item?.lineNumber ?? "-",
    ticker: item?.tickerCode ?? "-",
    message: item?.message ?? "Validation error",
  }));
}

function renderTickerChips(items) {
  if (!Array.isArray(items) || items.length === 0) {
    return <p className="admin-console-copy">-</p>;
  }

  return (
    <div className="admin-import-chip-row">
      {items.map((ticker) => (
        <span key={ticker} className="summary-chip">{ticker}</span>
      ))}
    </div>
  );
}

export default function AdminFinancialImportPage() {
  const [csvFile, setCsvFile] = useState(null);
  const [shareCountFile, setShareCountFile] = useState(null);
  const [dryRun, setDryRun] = useState(false);
  const [replaceExisting, setReplaceExisting] = useState(true);
  const [recalculateRatios, setRecalculateRatios] = useState(true);
  const [overwriteShareCount, setOverwriteShareCount] = useState(false);
  const [loading, setLoading] = useState(false);
  const [shareCountLoading, setShareCountLoading] = useState(false);
  const [error, setError] = useState("");
  const [shareCountError, setShareCountError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [shareCountSuccessMessage, setShareCountSuccessMessage] = useState("");
  const [result, setResult] = useState(null);
  const [shareCountResult, setShareCountResult] = useState(null);

  const validationErrors = useMemo(() => mapValidationErrors(result), [result]);
  const shareCountInvalidRows = useMemo(() => {
    if (!Array.isArray(shareCountResult?.invalidRows)) {
      return [];
    }
    return shareCountResult.invalidRows.map((item, index) => ({
      key: `${item?.rowNumber ?? "na"}-${item?.ticker ?? "na"}-${index}`,
      rowNumber: item?.rowNumber ?? "-",
      ticker: item?.ticker ?? "-",
      message: item?.message ?? "Validation error",
    }));
  }, [shareCountResult]);

  async function runImport(importFn, file, successText, errorText) {
    if (!file) {
      setError(errorText);
      setSuccessMessage("");
      return;
    }

    setLoading(true);
    setError("");
    setSuccessMessage("");

    try {
      const response = await importFn({
        file,
        dryRun,
        replaceExisting,
        recalculateRatios,
        overwriteShareCount,
      });

      setResult(response.data ?? null);
      setSuccessMessage(dryRun ? "Dry run tamamlandi." : successText);
    } catch (requestError) {
      setError(extractErrorMessage(requestError, errorText));
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  async function handleCsvSubmit(event) {
    event.preventDefault();
    await runImport(importCompanyFinancialCsv, csvFile, "CSV import basariyla tamamlandi.", "CSV import sirasinda hata olustu.");
  }

  async function handleShareCountSubmit(event) {
    event.preventDefault();
    if (!shareCountFile) {
      setShareCountError("Share count import dosyasi secilmedi.");
      setShareCountSuccessMessage("");
      return;
    }

    setShareCountLoading(true);
    setShareCountError("");
    setShareCountSuccessMessage("");

    try {
      const response = await importCompanyShareCounts({
        file: shareCountFile,
        overwrite: overwriteShareCount,
      });
      setShareCountResult(response.data ?? null);
      setShareCountSuccessMessage("Share count import basariyla tamamlandi.");
    } catch (requestError) {
      setShareCountError(extractErrorMessage(requestError, "Share count import sirasinda hata olustu."));
      setShareCountResult(null);
    } finally {
      setShareCountLoading(false);
    }
  }

  function handleTemplateDownload() {
    const blob = new Blob([TEMPLATE_CSV], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "manual-financial-import-template.csv";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  function handleShareCountTemplateDownload() {
    const blob = new Blob([SHARE_COUNT_TEMPLATE_CSV], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "share-count-import-template.csv";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  return (
    <div className="dashboard-stack admin-console-shell admin-panel-page">
      {error ? <div className="status-box error">{error}</div> : null}
      {successMessage ? <div className="status-box success">{successMessage}</div> : null}
      {shareCountError ? <div className="status-box error">{shareCountError}</div> : null}
      {shareCountSuccessMessage ? <div className="status-box success">{shareCountSuccessMessage}</div> : null}

      <section className="admin-section admin-console-form panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Admin Import</p>
            <h3>Manual Financial Template Import</h3>
            <p className="admin-console-copy">Bu alan sadece manual financial template CSV dosyasi icindir.</p>
            <p className="admin-console-copy">BRUT_KAR opsiyoneldir. Brut Marj icin HASILAT + BRUT_KAR gerekir.</p>
            <p className="admin-console-copy">ANNUAL, Q2 ve Q3 donemlerinde saglikli brut marj icin onceki donemler de import edilmelidir.</p>
          </div>
        </div>

        <form className="admin-console-form-grid admin-import-form" onSubmit={handleCsvSubmit}>
          <div className="admin-import-field">
            <label htmlFor="financial-import-file" className="admin-console-copy">CSV yukle</label>
            <input
              id="financial-import-file"
              type="file"
              accept=".csv,text/csv"
              className="admin-console-input"
              onChange={(event) => setCsvFile(event.target.files?.[0] ?? null)}
              disabled={loading}
            />
          </div>

          <label className="admin-import-checkbox">
            <input
              type="checkbox"
              checked={dryRun}
              onChange={(event) => setDryRun(event.target.checked)}
              disabled={loading}
            />
            <span>Dry run</span>
          </label>

          <label className="admin-import-checkbox">
            <input
              type="checkbox"
              checked={replaceExisting}
              onChange={(event) => setReplaceExisting(event.target.checked)}
              disabled={loading}
            />
            <span>Replace existing</span>
          </label>

          <label className="admin-import-checkbox">
            <input
              type="checkbox"
              checked={recalculateRatios}
              onChange={(event) => setRecalculateRatios(event.target.checked)}
              disabled={loading}
            />
            <span>Recalculate ratios</span>
          </label>

          <div className="admin-console-actions">
            <button
              type="button"
              className="admin-console-button admin-console-button-secondary"
              onClick={handleTemplateDownload}
              disabled={loading}
            >
              <span className="admin-console-button-glow" />
              <span className="admin-import-button-content"><Download size={16} /> Template indir</span>
            </button>

            <button
              type="submit"
              className="admin-console-button"
              disabled={loading || !csvFile}
            >
              <span className="admin-console-button-glow" />
              <span className="admin-import-button-content"><Upload size={16} /> {loading ? "Import calisiyor..." : "CSV import baslat"}</span>
            </button>
          </div>
        </form>

        <div className="admin-import-tickers">
          <span className="admin-console-copy">Desteklenen item_key degerleri</span>
          <div className="admin-import-chip-row">
            {SUPPORTED_ITEM_KEYS.map((itemKey) => (
              <span key={itemKey} className="summary-chip">{itemKey}</span>
            ))}
          </div>
        </div>
      </section>

      <section className="admin-section admin-console-form panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Share Count Import</p>
            <h3>Share Count Import</h3>
            <p className="admin-console-copy">CSV veya XLSX dosyasindan ticker_code + shares_outstanding import eder.</p>
          </div>
        </div>

        <form className="admin-console-form-grid admin-import-form" onSubmit={handleShareCountSubmit}>
          <div className="admin-import-field">
            <label htmlFor="share-count-import-file" className="admin-console-copy">CSV veya XLSX yukle</label>
            <input
              id="share-count-import-file"
              type="file"
              accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              className="admin-console-input"
              onChange={(event) => setShareCountFile(event.target.files?.[0] ?? null)}
              disabled={shareCountLoading}
            />
          </div>

          <label className="admin-import-checkbox">
            <input
              type="checkbox"
              checked={overwriteShareCount}
              onChange={(event) => setOverwriteShareCount(event.target.checked)}
              disabled={shareCountLoading || loading}
            />
            <span>Overwrite share count</span>
          </label>

          <div className="admin-console-actions">
            <button
              type="button"
              className="admin-console-button admin-console-button-secondary"
              onClick={handleShareCountTemplateDownload}
              disabled={shareCountLoading}
            >
              <span className="admin-console-button-glow" />
              <span className="admin-import-button-content"><Download size={16} /> Template indir</span>
            </button>

            <button
              type="submit"
              className="admin-console-button"
              disabled={shareCountLoading || !shareCountFile}
            >
              <span className="admin-console-button-glow" />
              <span className="admin-import-button-content"><Upload size={16} /> {shareCountLoading ? "Import calisiyor..." : "Share Count Import"}</span>
            </button>
          </div>
        </form>
      </section>

      <section className="admin-section admin-console-result panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Import Result</p>
            <h3>Sonuc</h3>
          </div>
        </div>

        {!result ? (
          <div className="status-box empty">
            <strong>Henuz sonuc yok.</strong>
            <p>CSV import calistirildiginda sayaclar ve validation error listesi burada gorunur.</p>
          </div>
        ) : (
          <div className="admin-import-result-stack">
            <div className="admin-import-metrics">
              <div className="admin-console-metric-card">
                <span>Created Reports</span>
                <strong>{result.createdReports ?? 0}</strong>
              </div>
              <div className="admin-console-metric-card">
                <span>Updated Reports</span>
                <strong>{result.updatedReports ?? 0}</strong>
              </div>
              <div className="admin-console-metric-card">
                <span>Created Values</span>
                <strong>{result.createdValues ?? 0}</strong>
              </div>
              <div className="admin-console-metric-card">
                <span>Updated Values</span>
                <strong>{result.updatedValues ?? 0}</strong>
              </div>
              <div className="admin-console-metric-card">
                <span>Deleted Stale Values</span>
                <strong>{result.deletedStaleValues ?? 0}</strong>
              </div>
              <div className="admin-console-metric-card">
                <span>Updated Share Counts</span>
                <strong>{Array.isArray(result.updatedShareCounts) ? result.updatedShareCounts.length : 0}</strong>
              </div>
              <div className="admin-console-metric-card">
                <span>Skipped Share Counts</span>
                <strong>{Array.isArray(result.skippedShareCounts) ? result.skippedShareCounts.length : 0}</strong>
              </div>
            </div>

            <div className="admin-import-tickers">
              <span className="admin-console-copy">Recalculated Tickers</span>
              {renderTickerChips(result.recalculatedTickers)}
            </div>

            <div className="admin-import-tickers">
              <span className="admin-console-copy">Preserved Real Ratios</span>
              {renderTickerChips(result.preservedRealRatios)}
            </div>

            <div className="admin-import-tickers">
              <span className="admin-console-copy">Created Mock Ratios</span>
              {renderTickerChips(result.createdMockRatios)}
            </div>

            <div className="admin-import-tickers">
              <span className="admin-console-copy">Skipped Existing</span>
              {renderTickerChips(result.skippedExisting)}
            </div>

            <div className="admin-import-tickers">
              <span className="admin-console-copy">Updated Share Counts</span>
              {renderTickerChips(result.updatedShareCounts)}
            </div>

            <div className="admin-import-tickers">
              <span className="admin-console-copy">Skipped Share Counts</span>
              {renderTickerChips(result.skippedShareCounts)}
            </div>

            <div className="admin-import-errors">
              <div className="admin-section-head">
                <div>
                  <p className="eyebrow">Validation</p>
                  <h3>Validation Errors</h3>
                </div>
              </div>

              {validationErrors.length === 0 ? (
                <div className="status-box success">Validation error yok.</div>
              ) : (
                <div className="admin-import-table-wrap">
                  <table className="admin-import-table">
                    <thead>
                      <tr>
                        <th>Row</th>
                        <th>Ticker</th>
                        <th>Message</th>
                      </tr>
                    </thead>
                    <tbody>
                      {validationErrors.map((item) => (
                        <tr key={item.key}>
                          <td>{item.rowNumber}</td>
                          <td>{item.ticker}</td>
                          <td>{item.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </section>

      <section className="admin-section admin-console-result panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Share Count Result</p>
            <h3>Share Count Sonuc</h3>
          </div>
        </div>

        {!shareCountResult ? (
          <div className="status-box empty">
            <strong>Henuz sonuc yok.</strong>
            <p>Share count import calistirildiginda sonuc burada gorunur.</p>
          </div>
        ) : (
          <div className="admin-import-result-stack">
            <div className="admin-import-tickers">
              <span className="admin-console-copy">Updated Share Counts</span>
              {renderTickerChips(shareCountResult.updatedShareCounts)}
            </div>
            <div className="admin-import-tickers">
              <span className="admin-console-copy">Skipped Existing</span>
              {renderTickerChips(shareCountResult.skippedExisting)}
            </div>
            <div className="admin-import-tickers">
              <span className="admin-console-copy">Not Found Tickers</span>
              {renderTickerChips(shareCountResult.notFoundTickers)}
            </div>
            <div className="admin-import-tickers">
              <span className="admin-console-copy">Recalculated Tickers</span>
              {renderTickerChips(shareCountResult.recalculatedTickers)}
            </div>

            <div className="admin-import-errors">
              <div className="admin-section-head">
                <div>
                  <p className="eyebrow">Validation</p>
                  <h3>Invalid Rows</h3>
                </div>
              </div>

              {shareCountInvalidRows.length === 0 ? (
                <div className="status-box success">Invalid row yok.</div>
              ) : (
                <div className="admin-import-table-wrap">
                  <table className="admin-import-table">
                    <thead>
                      <tr>
                        <th>Row</th>
                        <th>Ticker</th>
                        <th>Message</th>
                      </tr>
                    </thead>
                    <tbody>
                      {shareCountInvalidRows.map((item) => (
                        <tr key={item.key}>
                          <td>{item.rowNumber}</td>
                          <td>{item.ticker}</td>
                          <td>{item.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
