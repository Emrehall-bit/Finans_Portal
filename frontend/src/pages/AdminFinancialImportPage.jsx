import { useMemo, useState } from "react";
import { Download, Upload } from "lucide-react";
import { importCompanyFinancialCsv } from "../api/adminApi";
import { extractErrorMessage } from "../api/responseUtils";

const TEMPLATE_CSV = [
  "ticker_code,period_year,report_type,published_at,source_url,item_key,raw_label,value,currency,unit_multiplier",
  "TUPRS,2024,Q1,2024-05-02,manual://csv,HASILAT,Hasılat,275000000000,TRY,1",
  "TUPRS,2024,Q1,2024-05-02,manual://csv,NET_DONEM_KARI,Net Dönem Karı,12300000000,TRY,1",
  "TUPRS,2024,Q1,2024-05-02,manual://csv,OZKAYNAKLAR,Özkaynaklar,198000000000,TRY,1",
  "ASELS,2024,Q1,2024-05-03,manual://csv,HASILAT,Hasılat,22100000000,TRY,1",
  "ASELS,2024,Q1,2024-05-03,manual://csv,NET_DONEM_KARI,Net Dönem Karı,5400000000,TRY,1",
  "ASELS,2024,Q1,2024-05-03,manual://csv,OZKAYNAKLAR,Özkaynaklar,84500000000,TRY,1",
].join("\n");

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

export default function AdminFinancialImportPage() {
  const [file, setFile] = useState(null);
  const [dryRun, setDryRun] = useState(false);
  const [replaceExisting, setReplaceExisting] = useState(true);
  const [recalculateRatios, setRecalculateRatios] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [result, setResult] = useState(null);

  const validationErrors = useMemo(() => mapValidationErrors(result), [result]);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!file) {
      setError("CSV dosyası seçilmedi.");
      setSuccessMessage("");
      return;
    }

    setLoading(true);
    setError("");
    setSuccessMessage("");

    try {
      const response = await importCompanyFinancialCsv({
        file,
        dryRun,
        replaceExisting,
        recalculateRatios,
      });

      setResult(response.data ?? null);
      setSuccessMessage(dryRun ? "Dry run tamamlandı." : "CSV import başarıyla tamamlandı.");
    } catch (requestError) {
      setError(extractErrorMessage(requestError, "CSV import sırasında hata oluştu."));
      setResult(null);
    } finally {
      setLoading(false);
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

  return (
    <div className="dashboard-stack admin-console-shell admin-panel-page">
      {error ? <div className="status-box error">{error}</div> : null}
      {successMessage ? <div className="status-box success">{successMessage}</div> : null}

      <section className="admin-section admin-console-form panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Admin Import</p>
            <h3>Manuel Finansal CSV Import</h3>
            <p className="admin-console-copy">CSV dosyası yükleyip finansal rapor importunu başlatır.</p>
          </div>
        </div>

        <form className="admin-console-form-grid admin-import-form" onSubmit={handleSubmit}>
          <div className="admin-import-field">
            <label htmlFor="financial-import-file" className="admin-console-copy">CSV dosyası</label>
            <input
              id="financial-import-file"
              type="file"
              accept=".csv,text/csv"
              className="admin-console-input"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
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
              disabled={loading || !file}
            >
              <span className="admin-console-button-glow" />
              <span className="admin-import-button-content"><Upload size={16} /> {loading ? "Import çalışıyor..." : "Import başlat"}</span>
            </button>
          </div>
        </form>
      </section>

      <section className="admin-section admin-console-result panel-surface">
        <div className="admin-section-head">
          <div>
            <p className="eyebrow">Import Result</p>
            <h3>Sonuç</h3>
          </div>
        </div>

        {!result ? (
          <div className="status-box empty">
            <strong>Henüz sonuç yok.</strong>
            <p>CSV import çalıştırıldığında sayaçlar ve validation error listesi burada görünür.</p>
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
            </div>

            <div className="admin-import-tickers">
              <span className="admin-console-copy">Recalculated Tickers</span>
              {Array.isArray(result.recalculatedTickers) && result.recalculatedTickers.length > 0 ? (
                <div className="admin-import-chip-row">
                  {result.recalculatedTickers.map((ticker) => (
                    <span key={ticker} className="summary-chip">{ticker}</span>
                  ))}
                </div>
              ) : (
                <p className="admin-console-copy">-</p>
              )}
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
    </div>
  );
}
