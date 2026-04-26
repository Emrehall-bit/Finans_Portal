import { useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  compareTechnicalAnalysis,
  getMarketQuotes,
  getTechnicalAnalysis,
} from "../api/marketApi";
import { extractErrorMessage } from "../api/responseUtils";
import EmptyState from "../components/common/EmptyState";
import ErrorMessage from "../components/common/ErrorMessage";
import LoadingSpinner from "../components/common/LoadingSpinner";
import { formatNumber } from "../utils/formatters";

const INDICATOR_OPTIONS = ["SMA7", "SMA20", "SMA50"];
const RSI_INDICATOR = "RSI14";
const DEFAULT_INDICATORS = [...INDICATOR_OPTIONS, RSI_INDICATOR];
const RANGE_PRESETS = [
  { key: "1M", label: "1A", days: 30 },
  { key: "3M", label: "3A", days: 90 },
  { key: "6M", label: "6A", days: 180 },
  { key: "1Y", label: "1Y", days: 365 },
];
const COMPARISON_COLORS = ["#32d399", "#60a5fa", "#f59e0b", "#f87171", "#a78bfa", "#22d3ee"];
const SPOTLIGHT_SYMBOLS = ["XU100", "BIST100", "USDTRY", "EURTRY", "GRAMALTIN", "BTCUSDT", "BTC", "ETHUSDT", "ETH"];

export default function MarketsPage() {
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [sourceFilter, setSourceFilter] = useState("ALL");
  const [selectedSymbol, setSelectedSymbol] = useState("");
  const [selectedIndicators, setSelectedIndicators] = useState(() => new Set(DEFAULT_INDICATORS));
  const [compareSymbols, setCompareSymbols] = useState([]);
  const [activePreset, setActivePreset] = useState("3M");
  const [dateRange, setDateRange] = useState(() => buildPresetRange(90));
  const [analysis, setAnalysis] = useState(null);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [analysisError, setAnalysisError] = useState("");
  const [comparison, setComparison] = useState(null);
  const [comparisonLoading, setComparisonLoading] = useState(false);
  const [comparisonError, setComparisonError] = useState("");
  const deferredSearch = useDeferredValue(search);
  const isDateRangeInvalid = Boolean(
    dateRange.from && dateRange.to && new Date(dateRange.from).getTime() > new Date(dateRange.to).getTime(),
  );

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        setError("");
        const data = await getMarketQuotes();
        if (!active) {
          return;
        }

        const safeQuotes = data ?? [];
        setQuotes(safeQuotes);
        setSelectedSymbol((current) => current || safeQuotes[0]?.symbol || "");
      } catch (err) {
        if (!active) {
          return;
        }
        setError(extractErrorMessage(err, "Piyasa verileri yuklenemedi."));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    load();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedSymbol || !dateRange.from || !dateRange.to || isDateRangeInvalid) {
      setAnalysis(null);
      setAnalysisLoading(false);
      setAnalysisError(isDateRangeInvalid ? "Baslangic tarihi bitis tarihinden sonra olamaz." : "");
      return;
    }

    let active = true;

    async function loadAnalysis() {
      try {
        setAnalysisLoading(true);
        setAnalysisError("");
        const data = await getTechnicalAnalysis(selectedSymbol, {
          from: dateRange.from,
          to: dateRange.to,
          indicators: Array.from(selectedIndicators).join(","),
        });
        if (!active) {
          return;
        }
        setAnalysis(data);
      } catch (err) {
        if (!active) {
          return;
        }
        setAnalysis(null);
        setAnalysisError(extractErrorMessage(err, "Teknik analiz yuklenemedi."));
      } finally {
        if (active) {
          setAnalysisLoading(false);
        }
      }
    }

    loadAnalysis();

    return () => {
      active = false;
    };
  }, [selectedSymbol, selectedIndicators, dateRange, isDateRangeInvalid]);

  useEffect(() => {
    if (compareSymbols.length < 2 || !dateRange.from || !dateRange.to || isDateRangeInvalid) {
      setComparison(null);
      setComparisonError(isDateRangeInvalid ? "Baslangic tarihi bitis tarihinden sonra olamaz." : "");
      setComparisonLoading(false);
      return;
    }

    let active = true;

    async function loadComparison() {
      try {
        setComparisonLoading(true);
        setComparisonError("");
        const data = await compareTechnicalAnalysis({
          symbols: compareSymbols.join(","),
          from: dateRange.from,
          to: dateRange.to,
        });
        if (!active) {
          return;
        }
        setComparison(data);
      } catch (err) {
        if (!active) {
          return;
        }
        setComparison(null);
        setComparisonError(extractErrorMessage(err, "Karsilastirma verisi yuklenemedi."));
      } finally {
        if (active) {
          setComparisonLoading(false);
        }
      }
    }

    loadComparison();

    return () => {
      active = false;
    };
  }, [compareSymbols, dateRange, isDateRangeInvalid]);

  const sources = useMemo(() => {
    return ["ALL", ...new Set(quotes.map((item) => item.source).filter(Boolean))];
  }, [quotes]);

  const filteredQuotes = useMemo(() => {
    const query = deferredSearch.trim().toLowerCase();

    return quotes.filter((item) => {
      const matchesSource = sourceFilter === "ALL" || item.source === sourceFilter;
      const matchesQuery =
        query.length === 0 ||
        item.symbol?.toLowerCase().includes(query) ||
        item.displayName?.toLowerCase().includes(query);

      return matchesSource && matchesQuery;
    });
  }, [quotes, deferredSearch, sourceFilter]);

  const selectedQuote = useMemo(() => {
    return quotes.find((item) => item.symbol === selectedSymbol) || null;
  }, [quotes, selectedSymbol]);

  const spotlightQuotes = useMemo(() => {
    const priority = SPOTLIGHT_SYMBOLS.map((symbol) =>
      filteredQuotes.find((item) => item.symbol?.toUpperCase() === symbol),
    ).filter(Boolean);

    const movers = [...filteredQuotes]
      .sort((left, right) => Math.abs(Number(right.changeRate) || 0) - Math.abs(Number(left.changeRate) || 0))
      .slice(0, 5);

    return uniqueBySymbol([...priority, ...movers]).slice(0, 5);
  }, [filteredQuotes]);

  const marketPulse = useMemo(() => {
    const positive = filteredQuotes.filter((item) => Number(item.changeRate) >= 0).length;
    const negative = filteredQuotes.filter((item) => Number(item.changeRate) < 0).length;

    return {
      visible: filteredQuotes.length,
      positive,
      negative,
    };
  }, [filteredQuotes]);

  const chartData = useMemo(() => {
    return (analysis?.points ?? []).map((point) => ({
      date: formatChartDate(point.date),
      close: toNumeric(point.close),
      sma7: toNumeric(point.sma7),
      sma20: toNumeric(point.sma20),
      sma50: toNumeric(point.sma50),
      rsi14: toNumeric(point.rsi14),
    }));
  }, [analysis]);

  const showRsi = selectedIndicators.has(RSI_INDICATOR);

  const comparisonData = useMemo(() => {
    if (!comparison?.series?.length) {
      return [];
    }

    const mergedByDate = new Map();

    comparison.series.forEach((series) => {
      series.points?.forEach((point) => {
        const key = point.date;
        const current = mergedByDate.get(key) ?? { date: formatChartDate(point.date) };
        current[series.symbol] = toNumeric(point.normalizedValue);
        mergedByDate.set(key, current);
      });
    });

    return Array.from(mergedByDate.entries())
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([, value]) => value);
  }, [comparison]);

  const selectedInstrumentMeta = analysis?.indicatorValues ?? [];

  return (
    <div className="dashboard-stack market-terminal-page">
      <section className="market-terminal-hero panel-surface">
        <div className="market-terminal-copy">
          <p className="eyebrow">Market Terminal</p>
          <h1>Piyasa ekranini tek bakista oku.</h1>
          <p className="page-description">
            Referans platformlardaki gibi once akisi, sonra grafigi, ardindan teknik sinyali gor.
            Alim satim yok; odak tamamen piyasa takibi, gecmis fiyat ve karsilastirma uzerinde.
          </p>

          <div className="market-terminal-search">
            <label className="market-filter-field">
              <span>Sembol veya isim ara</span>
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="THYAO, USDTRY, BTCUSDT..."
              />
            </label>

            <label className="market-filter-field">
              <span>Kaynak</span>
              <select value={sourceFilter} onChange={(event) => setSourceFilter(event.target.value)}>
                {sources.map((source) => (
                  <option key={source} value={source}>
                    {source}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="market-pulse-row">
            <div className="market-pulse-card">
              <span>Gorunen veri</span>
              <strong>{marketPulse.visible}</strong>
            </div>
            <div className="market-pulse-card up">
              <span>Yukselen</span>
              <strong>{marketPulse.positive}</strong>
            </div>
            <div className="market-pulse-card down">
              <span>Dusen</span>
              <strong>{marketPulse.negative}</strong>
            </div>
          </div>
        </div>

        <div className="market-terminal-spotlight">
          <div className="panel-head">
            <div>
              <p className="eyebrow">One Cikanlar</p>
              <h3>Hizli piyasa ozeti</h3>
            </div>
            <span className="terminal-badge">Live</span>
          </div>

          <div className="terminal-spotlight-grid">
            {spotlightQuotes.map((item) => (
              <button
                key={`${item.symbol}-${item.source}`}
                type="button"
                className={`spotlight-quote-card ${item.symbol === selectedSymbol ? "active" : ""}`}
                onClick={() => setSelectedSymbol(item.symbol)}
              >
                <div>
                  <strong>{item.symbol}</strong>
                  <span>{item.displayName || item.source || "Market feed"}</span>
                </div>
                <div className="spotlight-quote-metric">
                  <strong>{formatNumber(item.price)}</strong>
                  <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                    {formatMarketChange(item.changeRate)}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="market-workbench-grid">
        <section className="market-watchlist-column panel-surface">
          <div className="panel-head">
            <div>
              <p className="eyebrow">Market Board</p>
              <h3>Izleme listesi</h3>
            </div>
            <span className="terminal-badge muted">{filteredQuotes.length} satir</span>
          </div>

          {loading ? <LoadingSpinner label="Piyasa verileri yukleniyor..." /> : null}
          {error ? <ErrorMessage message={error} /> : null}

          {!loading && !error && filteredQuotes.length === 0 ? (
            <EmptyState title="Piyasa verisi bulunamadi" description="Filtreleri degistirip tekrar deneyin." />
          ) : null}

          {!loading && !error && filteredQuotes.length > 0 ? (
            <div className="market-watchlist-table table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Sembol</th>
                    <th>Son</th>
                    <th>Degisim</th>
                    <th>Kiyas</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredQuotes.map((item) => {
                    const selected = item.symbol === selectedSymbol;
                    const compared = compareSymbols.includes(item.symbol);

                    return (
                      <tr
                        key={`${item.symbol}-${item.source}`}
                        className={selected ? "market-row-active" : ""}
                        onClick={() => setSelectedSymbol(item.symbol)}
                      >
                        <td>
                          <div className="watchlist-symbol-cell">
                            <strong>{item.symbol}</strong>
                            <span>{item.displayName || item.source || "-"}</span>
                          </div>
                        </td>
                        <td>{formatNumber(item.price)}</td>
                        <td>
                          <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                            {formatMarketChange(item.changeRate)}
                          </span>
                        </td>
                        <td>
                          <input
                            type="checkbox"
                            checked={compared}
                            onChange={(event) => {
                              event.stopPropagation();
                              toggleCompareSymbol(item.symbol, setCompareSymbols);
                            }}
                            aria-label={`${item.symbol} karsilastirmaya ekle`}
                          />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : null}
        </section>

        <section className="market-chart-column panel-surface">
          <div className="market-chart-topbar">
            <div>
              <p className="eyebrow">Secili Enstruman</p>
              <div className="market-chart-title-row">
                <h2>{selectedSymbol || "Sembol secin"}</h2>
                {selectedQuote ? (
                  <span className={Number(selectedQuote.changeRate) >= 0 ? "terminal-pill positive" : "terminal-pill negative"}>
                    {formatMarketChange(selectedQuote.changeRate)}
                  </span>
                ) : null}
              </div>
              <p className="muted">
                {selectedQuote?.displayName || "Market akisi"} {selectedQuote?.currency ? `• ${selectedQuote.currency}` : ""}
              </p>
            </div>

            <div className="market-chart-topbar-actions">
              <div className="preset-chip-row">
                {RANGE_PRESETS.map((preset) => (
                  <button
                    key={preset.key}
                    type="button"
                    className={`table-chip-button ${activePreset === preset.key ? "active" : ""}`}
                    onClick={() => {
                      setActivePreset(preset.key);
                      setDateRange(buildPresetRange(preset.days));
                    }}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>

              <button
                type="button"
                className={`table-chip-button ${compareSymbols.includes(selectedSymbol) ? "active" : ""}`}
                onClick={() => selectedSymbol && toggleCompareSymbol(selectedSymbol, setCompareSymbols)}
                disabled={!selectedSymbol}
              >
                Kiyasa ekle
              </button>
            </div>
          </div>

          <div className="market-chart-toolbar">
            <div className="analysis-date-grid">
              <label className="market-filter-field">
                <span>Baslangic</span>
                <input
                  type="date"
                  value={dateRange.from}
                  onChange={(event) => {
                    setActivePreset("CUSTOM");
                    setDateRange((current) => ({ ...current, from: event.target.value }));
                  }}
                />
              </label>
              <label className="market-filter-field">
                <span>Bitis</span>
                <input
                  type="date"
                  value={dateRange.to}
                  onChange={(event) => {
                    setActivePreset("CUSTOM");
                    setDateRange((current) => ({ ...current, to: event.target.value }));
                  }}
                />
              </label>
            </div>

            <div className="indicator-chip-row">
              {DEFAULT_INDICATORS.map((indicator) => (
                <button
                  key={indicator}
                  type="button"
                  className={`table-chip-button ${selectedIndicators.has(indicator) ? "active" : ""}`}
                  onClick={() => toggleIndicator(indicator, setSelectedIndicators)}
                >
                  {indicator}
                </button>
              ))}
            </div>
          </div>

          {analysisLoading ? <LoadingSpinner label="Teknik analiz hesaplaniyor..." /> : null}
          {analysisError ? <ErrorMessage message={analysisError} /> : null}

          {!analysisLoading && !analysisError && !analysis ? (
            <EmptyState
              title="Analiz bekleniyor"
              description={isDateRangeInvalid
                ? "Gecerli bir tarih araligi secin."
                : "Soldaki listeden sembol secerek grafik alanini doldurun."}
            />
          ) : null}

          {!analysisLoading && !analysisError && analysis ? (
            <>
              <div className="terminal-price-strip">
                <div className="terminal-price-card">
                  <span>Son fiyat</span>
                  <strong>{formatNumber(analysis.latestPrice)}</strong>
                </div>
                <div className="terminal-price-card">
                  <span>Trend</span>
                  <strong>{formatTrendLabel(analysis.trendDirection)}</strong>
                </div>
                <div className="terminal-price-card">
                  <span>Secili indikator</span>
                  <strong>{Array.from(selectedIndicators).join(" / ")}</strong>
                </div>
              </div>

              <div className="analysis-chart-shell terminal-chart-shell">
                <ResponsiveContainer width="100%" height={420}>
                  <LineChart data={chartData} margin={{ top: 18, right: 16, left: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(120, 140, 182, 0.14)" />
                    <XAxis dataKey="date" stroke="#94a3b8" tickLine={false} axisLine={false} />
                    <YAxis
                      stroke="#94a3b8"
                      tickLine={false}
                      axisLine={false}
                      width={72}
                      tickFormatter={(value) => formatAxisNumber(value)}
                    />
                    <Tooltip content={<AnalysisTooltip />} />
                    <Legend />
                    <Line type="monotone" dataKey="close" name="Fiyat" stroke="#f8fafc" strokeWidth={2.8} dot={false} />
                    <Line type="monotone" dataKey="sma7" name="SMA 7" stroke="#34d399" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="sma20" name="SMA 20" stroke="#60a5fa" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="sma50" name="SMA 50" stroke="#f59e0b" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>

              {showRsi ? (
                <div className="analysis-chart-shell rsi-chart-shell terminal-chart-shell">
                  <div className="panel-head compact">
                    <div>
                      <p className="eyebrow">Momentum</p>
                      <h3>RSI 14</h3>
                    </div>
                  </div>
                  <ResponsiveContainer width="100%" height={220}>
                    <LineChart data={chartData} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(120, 140, 182, 0.14)" />
                      <XAxis dataKey="date" stroke="#94a3b8" tickLine={false} axisLine={false} />
                      <YAxis domain={[0, 100]} stroke="#94a3b8" tickLine={false} axisLine={false} width={44} />
                      <Tooltip content={<AnalysisTooltip />} />
                      <ReferenceLine y={70} stroke="#f87171" strokeDasharray="6 6" />
                      <ReferenceLine y={30} stroke="#34d399" strokeDasharray="6 6" />
                      <Line type="monotone" dataKey="rsi14" name="RSI 14" stroke="#a78bfa" strokeWidth={2.4} dot={false} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              ) : null}
            </>
          ) : null}
        </section>

        <aside className="market-insight-column">
          <section className="panel-surface market-insight-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Signal Stack</p>
                <h3>Teknik gorunum</h3>
              </div>
              <span className="terminal-badge">TA</span>
            </div>

            <div className="signal-chip-row">
              {(analysis?.signals ?? []).length > 0 ? (
                analysis.signals.map((signal) => (
                  <span key={signal} className="signal-pill">
                    {formatSignalLabel(signal)}
                  </span>
                ))
              ) : (
                <span className="signal-pill neutral">Belirgin sinyal yok</span>
              )}
            </div>

            <div className="indicator-value-grid terminal-indicator-grid">
              {selectedInstrumentMeta.length > 0 ? (
                selectedInstrumentMeta.map((item) => (
                  <div key={item.indicator} className="indicator-value-card">
                    <span>{item.indicator}</span>
                    <strong>{formatNumber(item.value)}</strong>
                  </div>
                ))
              ) : (
                <EmptyState title="Indikator bekleniyor" description="Teknik analiz geldikten sonra bu kutu dolacak." />
              )}
            </div>
          </section>

          <section className="panel-surface market-insight-card">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Comparison</p>
                <h3>Normalize performans</h3>
              </div>
              <div className="market-analysis-price">
                <span>Secili semboller</span>
                <strong>{compareSymbols.length}</strong>
              </div>
            </div>

            <div className="compare-symbol-list">
              {compareSymbols.length > 0 ? compareSymbols.map((symbol) => (
                <span key={symbol} className="signal-pill neutral">
                  {symbol}
                </span>
              )) : (
                <p className="muted">En az iki sembol secin.</p>
              )}
            </div>

            {compareSymbols.length < 2 ? (
              <EmptyState
                title="Kiyas listesi hazir degil"
                description="Ayni grafikte gorebilmek icin en az iki sembol secin."
              />
            ) : null}

            {comparisonLoading ? <LoadingSpinner label="Kiyas grafigi yukleniyor..." /> : null}
            {comparisonError ? <ErrorMessage message={comparisonError} /> : null}

            {!comparisonLoading && !comparisonError && compareSymbols.length >= 2 && comparisonData.length === 0 ? (
              <EmptyState
                title="Kiyas verisi bulunamadi"
                description={isDateRangeInvalid
                  ? "Gecerli bir tarih araligi secin."
                  : "Secili semboller icin bu aralikta tarihsel veri bulunamadi."}
              />
            ) : null}

            {!comparisonLoading && !comparisonError && comparisonData.length > 0 ? (
              <div className="analysis-chart-shell compare-chart-shell terminal-chart-shell">
                <ResponsiveContainer width="100%" height={320}>
                  <LineChart data={comparisonData} margin={{ top: 18, right: 12, left: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(120, 140, 182, 0.14)" />
                    <XAxis dataKey="date" stroke="#94a3b8" tickLine={false} axisLine={false} />
                    <YAxis
                      stroke="#94a3b8"
                      tickLine={false}
                      axisLine={false}
                      width={70}
                      tickFormatter={(value) => formatAxisNumber(value)}
                    />
                    <Tooltip content={<ComparisonTooltip />} />
                    <Legend />
                    <ReferenceLine y={100} stroke="rgba(148, 163, 184, 0.5)" strokeDasharray="5 5" />
                    {(comparison.series ?? []).map((series, index) => (
                      <Line
                        key={series.symbol}
                        type="monotone"
                        dataKey={series.symbol}
                        name={series.symbol}
                        stroke={COMPARISON_COLORS[index % COMPARISON_COLORS.length]}
                        strokeWidth={2.2}
                        dot={false}
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            ) : null}
          </section>
        </aside>
      </section>
    </div>
  );
}

function toggleIndicator(indicator, setSelectedIndicators) {
  setSelectedIndicators((current) => {
    const next = new Set(current);
    if (next.has(indicator) && next.size > 1) {
      next.delete(indicator);
      return next;
    }

    next.add(indicator);
    return next;
  });
}

function toggleCompareSymbol(symbol, setCompareSymbols) {
  setCompareSymbols((current) => {
    if (current.includes(symbol)) {
      return current.filter((item) => item !== symbol);
    }

    return [...current, symbol];
  });
}

function buildPresetRange(days) {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - days);

  return {
    from: toIsoDate(from),
    to: toIsoDate(to),
  };
}

function toIsoDate(value) {
  return value.toISOString().slice(0, 10);
}

function formatMarketChange(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }

  return `${numeric >= 0 ? "+" : ""}${numeric.toFixed(2)}%`;
}

function formatChartDate(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function formatAxisNumber(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "-";
  }

  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function formatTrendLabel(value) {
  if (!value) {
    return "-";
  }

  return {
    UPTREND: "Yukselis",
    DOWNTREND: "Dusus",
    SIDEWAYS: "Yatay",
  }[value] ?? value;
}

function formatSignalLabel(value) {
  return {
    PRICE_ABOVE_SMA20: "Fiyat > SMA20",
    PRICE_BELOW_SMA20: "Fiyat < SMA20",
    SMA7_ABOVE_SMA20: "SMA7 > SMA20",
    SMA7_BELOW_SMA20: "SMA7 < SMA20",
    RSI_OVERBOUGHT: "RSI asiri alim",
    RSI_OVERSOLD: "RSI asiri satim",
    RSI_NEUTRAL: "RSI dengeli",
  }[value] ?? value;
}

function toNumeric(value) {
  if (value === null || value === undefined || value === "") {
    return null;
  }

  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : numeric;
}

function uniqueBySymbol(items) {
  const unique = [];
  const seen = new Set();

  items.forEach((item) => {
    if (!item?.symbol || seen.has(item.symbol)) {
      return;
    }

    seen.add(item.symbol);
    unique.push(item);
  });

  return unique;
}

function AnalysisTooltip({ active, payload, label }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div className="chart-tooltip terminal-tooltip">
      <strong>{label}</strong>
      {payload.map((item) => (
        <div key={item.dataKey} className="chart-tooltip-row">
          <span>{item.name}</span>
          <strong>{formatAxisNumber(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}

function ComparisonTooltip({ active, payload, label }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div className="chart-tooltip terminal-tooltip">
      <strong>{label}</strong>
      {payload.map((item) => (
        <div key={item.dataKey} className="chart-tooltip-row">
          <span>{item.name}</span>
          <strong>{formatAxisNumber(item.value)}</strong>
        </div>
      ))}
    </div>
  );
}
