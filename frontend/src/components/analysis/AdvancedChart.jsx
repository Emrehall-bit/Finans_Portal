import { useCallback, useEffect, useRef, useState } from "react";
import { createChart, LineSeries } from "lightweight-charts";
import { getAdvancedTechnical } from "../../api/analysisApi";
import { useTheme } from "../../theme/ThemeContext";
import { formatNumber } from "../../utils/formatters";

// ── Sabitler ──────────────────────────────────────────────────────────────────

const TIMEFRAMES = [
  { label: "1S", value: "1h" },
  { label: "4S", value: "4h" },
  { label: "1G", value: "1d" },
  { label: "1Hft", value: "1w" },
];

const INDICATOR_COLORS = {
  ma20: "#3b82f6",
  ma50: "#f59e0b",
  ema20: "#a855f7",
  bollinger_upper: "#64748b",
  bollinger_middle: "#94a3b8",
  bollinger_lower: "#64748b",
};

const INDICATOR_BUTTONS = [
  { key: "ma20",      label: "MA 20",     color: "#3b82f6" },
  { key: "ma50",      label: "MA 50",     color: "#f59e0b" },
  { key: "ema20",     label: "EMA 20",    color: "#a855f7" },
  { key: "bollinger", label: "Bollinger", color: "#64748b",
    keys: ["bollinger_upper", "bollinger_middle", "bollinger_lower"] },
];

const DRAW_TOOLS = [
  { key: "cursor",     icon: "↖", label: "İmleç" },
  { key: "horizontal", icon: "—", label: "Yatay Çizgi" },
  { key: "trend",      icon: "╱", label: "Trend Çizgisi" },
  { key: "stopLoss",   icon: "🔴", label: "Stop-Loss" },
  { key: "takeProfit", icon: "🟢", label: "Take-Profit" },
];

// ── Bileşen ───────────────────────────────────────────────────────────────────

export default function AdvancedChart({
  instrumentCode,
  initialTimeframe = "1d",
  initialHighlightTool = null,
  presetPrice = null,
  quote = null,
}) {
  const { chartTheme } = useTheme();

  // ── Refs ──────────────────────────────────────────────────────────────────
  const containerRef         = useRef(null);
  const chartRef             = useRef(null);
  const priceSeriesRef       = useRef(null);
  const indicatorRefs        = useRef({});
  const indicatorDataRef     = useRef({});
  const activeIndicatorsRef  = useRef(null);

  // Çizim aracı refs — Effect 1'in click handler'ı stale closure olmadan okur
  const activeToolRef        = useRef("cursor");
  const drawingsRef          = useRef({ stopLoss: null, takeProfit: null, horizontalLines: [], trendLines: [] });
  const trendStartRef        = useRef(null);
  const prevInstrumentRef    = useRef(null);

  // ── State ─────────────────────────────────────────────────────────────────
  const [timeframe, setTimeframe]       = useState(initialTimeframe);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState(null);
  const [activeIndicators, setActiveIndicators] = useState(
    () => new Set(["ma20", "ma50", "bollinger_upper", "bollinger_middle", "bollinger_lower"]),
  );

  const [activeTool, setActiveTool]     = useState("cursor");
  const [drawings, setDrawings]         = useState({
    stopLoss: null,
    takeProfit: null,
    horizontalLines: [],
    trendLines: [],
  });
  const [trendStart, setTrendStart]     = useState(null); // { time, price } — ilk tıklama

  // Render'da refs'i güncelle
  activeIndicatorsRef.current = activeIndicators;
  activeToolRef.current       = activeTool;
  drawingsRef.current         = drawings;
  trendStartRef.current       = trendStart;

  // ── Effect 1: Chart'ı BİR KEZ oluştur + click handler ───────────────────
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      width: containerRef.current.clientWidth,
      height: 420,
      layout: {
        background: { color: "transparent" },
        textColor: chartTheme.axis,
      },
      grid: {
        vertLines: { color: chartTheme.grid },
        horzLines: { color: chartTheme.grid },
      },
      crosshair: { mode: 1 },
      rightPriceScale: { borderColor: chartTheme.grid, autoScale: true },
      timeScale: {
        borderColor: chartTheme.grid,
        timeVisible: true,
        secondsVisible: false,
      },
    });

    const priceSeries = chart.addSeries(LineSeries, {
      color:            "#4c7fff",
      lineWidth:        2,
      priceLineVisible: false,
      lastValueVisible: true,
    });

    chartRef.current       = chart;
    priceSeriesRef.current = priceSeries;

    // ── Çizim tıklama handler'ı ──────────────────────────────────────────
    chart.subscribeClick((param) => {
      const tool = activeToolRef.current;
      if (tool === "cursor" || !param.point) return;

      const price = priceSeries.coordinateToPrice(param.point.y);
      if (price == null) return;

      if (tool === "horizontal") {
        const priceLine = priceSeries.createPriceLine({
          price,
          color:            "#6b7280",
          lineWidth:        1,
          lineStyle:        2,   // dashed
          axisLabelVisible: true,
        });
        setDrawings((p) => ({
          ...p,
          horizontalLines: [...p.horizontalLines, { id: Date.now(), price, priceLine }],
        }));

      } else if (tool === "stopLoss") {
        const cur = drawingsRef.current;
        if (cur.stopLoss) {
          try { priceSeries.removePriceLine(cur.stopLoss.priceLine); } catch { /* */ }
        }
        const priceLine = priceSeries.createPriceLine({
          price,
          color:            "#ef4444",
          lineWidth:        2,
          lineStyle:        0,   // solid
          axisLabelVisible: true,
          title:            "🔴 Stop-Loss",
        });
        setDrawings((p) => ({ ...p, stopLoss: { price, priceLine } }));

      } else if (tool === "takeProfit") {
        const cur = drawingsRef.current;
        if (cur.takeProfit) {
          try { priceSeries.removePriceLine(cur.takeProfit.priceLine); } catch { /* */ }
        }
        const priceLine = priceSeries.createPriceLine({
          price,
          color:            "#22c55e",
          lineWidth:        2,
          lineStyle:        0,
          axisLabelVisible: true,
          title:            "🟢 Take-Profit",
        });
        setDrawings((p) => ({ ...p, takeProfit: { price, priceLine } }));

      } else if (tool === "trend") {
        const start = trendStartRef.current;
        if (!start) {
          // İlk tıklama — başlangıç noktası
          if (param.time == null) return;
          setTrendStart({ time: param.time, price });
        } else {
          // İkinci tıklama — trend çizgisini çiz
          if (param.time == null || param.time === start.time) {
            setTrendStart(null);
            return;
          }
          const data = [
            { time: start.time, value: start.price },
            { time: param.time, value: price },
          ].sort((a, b) => a.time - b.time);

          const trendSeries = chart.addSeries(LineSeries, {
            color:            "#8b5cf6",
            lineWidth:        1,
            priceLineVisible: false,
            lastValueVisible: false,
          });
          trendSeries.setData(data);
          setDrawings((p) => ({
            ...p,
            trendLines: [...p.trendLines, { id: Date.now(), series: trendSeries }],
          }));
          setTrendStart(null);
        }
      }
    });

    // ── Resize ────────────────────────────────────────────────────────────
    const onResize = () => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: containerRef.current.clientWidth });
      }
    };
    window.addEventListener("resize", onResize);

    return () => {
      window.removeEventListener("resize", onResize);
      chart.remove();
      chartRef.current       = null;
      priceSeriesRef.current = null;
      indicatorRefs.current  = {};
      indicatorDataRef.current = {};
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Effect 2: Tema renk güncelle ─────────────────────────────────────────
  useEffect(() => {
    if (!chartRef.current) return;
    chartRef.current.applyOptions({
      layout:          { textColor: chartTheme.axis },
      grid:            { vertLines: { color: chartTheme.grid }, horzLines: { color: chartTheme.grid } },
      rightPriceScale: { borderColor: chartTheme.grid, autoScale: true },
      timeScale:       { borderColor: chartTheme.grid },
    });
  }, [chartTheme]);

  // ── Yardımcı: indicator görünürlüğünü senkronize et ──────────────────────
  const applyIndicatorVisibility = useCallback(() => {
    if (!chartRef.current) return;
    Object.keys(indicatorDataRef.current).forEach((key) => {
      const data     = indicatorDataRef.current[key];
      const existing = indicatorRefs.current[key];
      const show     = activeIndicatorsRef.current.has(key);

      if (show && !existing) {
        const series = chartRef.current.addSeries(LineSeries, {
          color:            INDICATOR_COLORS[key] ?? "#94a3b8",
          lineWidth:        key.startsWith("bollinger") ? 1 : 2,
          priceLineVisible: false,
          lastValueVisible: false,
        });
        series.setData(data);
        indicatorRefs.current[key] = series;
      } else if (!show && existing) {
        try { chartRef.current.removeSeries(existing); } catch { /* */ }
        indicatorRefs.current[key] = null;
      }
    });
  }, []);

  // ── Effect 3: Veri çek ────────────────────────────────────────────────────
  useEffect(() => {
    if (!instrumentCode || !priceSeriesRef.current) return;

    // Enstrüman değişince çizimleri temizle
    if (prevInstrumentRef.current && prevInstrumentRef.current !== instrumentCode) {
      const d = drawingsRef.current;
      if (d.stopLoss)   try { priceSeriesRef.current.removePriceLine(d.stopLoss.priceLine); }   catch { /* */ }
      if (d.takeProfit) try { priceSeriesRef.current.removePriceLine(d.takeProfit.priceLine); } catch { /* */ }
      d.horizontalLines.forEach((l) => { try { priceSeriesRef.current.removePriceLine(l.priceLine); } catch { /* */ } });
      d.trendLines.forEach((t) => { try { chartRef.current?.removeSeries(t.series); } catch { /* */ } });
      setDrawings({ stopLoss: null, takeProfit: null, horizontalLines: [], trendLines: [] });
      setTrendStart(null);
    }
    prevInstrumentRef.current = instrumentCode;

    let cancelled = false;

    async function fetchData() {
      setLoading(true);
      setError(null);

      try {
        const res = await getAdvancedTechnical(instrumentCode, { timeframe });
        if (cancelled) return;

        const prices = res?.prices;
        if (!prices || prices.length === 0) {
          setError("Bu enstrüman için veri bulunamadı.");
          setLoading(false);
          return;
        }

        const lineData = prices.map((p) => ({
          time:  p.time,
          value: p.value ?? p.close ?? p.close_price ?? p.price_value ?? 0,
        }));

        priceSeriesRef.current.setData(lineData);
        chartRef.current.timeScale().fitContent();

        Object.values(indicatorRefs.current).forEach((s) => {
          if (s) { try { chartRef.current.removeSeries(s); } catch { /* */ } }
        });
        indicatorRefs.current    = {};
        indicatorDataRef.current = {};

        const times = prices.map((p) => p.time);
        Object.entries(res?.indicators ?? {}).forEach(([key, values]) => {
          if (!Array.isArray(values) || values.length === 0) return;
          const chartData = values
            .map((v, i) => (v != null && times[i] != null ? { time: times[i], value: v } : null))
            .filter(Boolean);
          if (chartData.length > 0) indicatorDataRef.current[key] = chartData;
        });

        applyIndicatorVisibility();

      } catch {
        if (!cancelled) setError("Veri yüklenirken hata oluştu. Tekrar deneyin.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    fetchData();
    return () => { cancelled = true; };
  }, [instrumentCode, timeframe, applyIndicatorVisibility]);

  // ── Effect 4: Indicator toggle ────────────────────────────────────────────
  useEffect(() => {
    applyIndicatorVisibility();
  }, [activeIndicators, applyIndicatorVisibility]);

  // ── Effect 5: Grafik dışına tıklayınca imlece dön ───────────────────────
  useEffect(() => {
    if (activeTool === "cursor") return;
    const handleOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setActiveTool("cursor");
        setTrendStart(null);
      }
    };
    document.addEventListener("mousedown", handleOutside);
    return () => document.removeEventListener("mousedown", handleOutside);
  }, [activeTool]);

  // ── Çizim silme fonksiyonları ─────────────────────────────────────────────
  function removeStopLoss() {
    if (drawings.stopLoss) {
      try { priceSeriesRef.current?.removePriceLine(drawings.stopLoss.priceLine); } catch { /* */ }
    }
    setDrawings((p) => ({ ...p, stopLoss: null }));
  }

  function removeTakeProfit() {
    if (drawings.takeProfit) {
      try { priceSeriesRef.current?.removePriceLine(drawings.takeProfit.priceLine); } catch { /* */ }
    }
    setDrawings((p) => ({ ...p, takeProfit: null }));
  }

  function removeHorizontalLine(id) {
    const line = drawings.horizontalLines.find((l) => l.id === id);
    if (line) try { priceSeriesRef.current?.removePriceLine(line.priceLine); } catch { /* */ }
    setDrawings((p) => ({ ...p, horizontalLines: p.horizontalLines.filter((l) => l.id !== id) }));
  }

  function removeTrendLine(id) {
    const tl = drawings.trendLines.find((t) => t.id === id);
    if (tl) try { chartRef.current?.removeSeries(tl.series); } catch { /* */ }
    setDrawings((p) => ({ ...p, trendLines: p.trendLines.filter((t) => t.id !== id) }));
  }

  // ── Indicator toggle ──────────────────────────────────────────────────────
  function toggleGroup(btn) {
    const keys = btn.keys ?? [btn.key];
    setActiveIndicators((prev) => {
      const next = new Set(prev);
      const anyActive = keys.some((k) => next.has(k));
      if (anyActive) keys.forEach((k) => next.delete(k));
      else           keys.forEach((k) => next.add(k));
      return next;
    });
  }

  function isGroupActive(btn) {
    const keys = btn.keys ?? [btn.key];
    return keys.some((k) => activeIndicators.has(k));
  }

  // ── Hesaplamalar ──────────────────────────────────────────────────────────
  const changeRate  = quote?.changeRate;
  const isPositive  = changeRate != null && changeRate >= 0;
  const isDrawing   = activeTool !== "cursor";
  const hasDrawings = !!(
    drawings.stopLoss ||
    drawings.takeProfit ||
    drawings.horizontalLines.length ||
    drawings.trendLines.length
  );

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <section className="panel-surface advanced-chart-card">

      {/* ── Başlık ── */}
      <div className="advanced-chart-header">
        <div className="advanced-chart-header-left">
          <span className="advanced-chart-symbol">{instrumentCode?.toUpperCase() ?? "—"}</span>
          {quote?.displayName && quote.displayName !== instrumentCode?.toUpperCase() && (
            <span className="advanced-chart-name">{quote.displayName}</span>
          )}
          {quote?.price != null && (
            <span className="advanced-chart-price">{formatNumber(quote.price, 2)}</span>
          )}
          {changeRate != null && (
            <span className={`advanced-chart-change ${isPositive ? "positive" : "negative"}`}>
              {isPositive ? "+" : ""}{changeRate.toFixed(2)}%
            </span>
          )}
        </div>

        <div className="chart-timeframes">
          {TIMEFRAMES.map((tf) => (
            <button
              key={tf.value}
              className={`chart-tf-btn${timeframe === tf.value ? " active" : ""}`}
              onClick={() => setTimeframe(tf.value)}
            >
              {tf.label}
            </button>
          ))}
        </div>
      </div>

      {/* ── İndikatör + Çizim toolbar satırı ── */}
      <div className="advanced-chart-indicators">
        {/* Mevcut indikatör toggle'ları */}
        {INDICATOR_BUTTONS.map((btn) => (
          <button
            key={btn.key}
            className={`chart-indicator-btn${isGroupActive(btn) ? " active" : ""}`}
            style={{ "--indicator-color": btn.color }}
            onClick={() => toggleGroup(btn)}
          >
            <span className="chart-indicator-dot" />
            {btn.label}
          </button>
        ))}

        {/* Ayırıcı */}
        <span className="chart-toolbar-sep" aria-hidden="true" />

        {/* Çizim araçları */}
        {DRAW_TOOLS.map((tool) => (
          <button
            key={tool.key}
            title={tool.label}
            className={`draw-tool-btn${activeTool === tool.key ? " active" : ""}`}
            onClick={() => {
              setActiveTool(tool.key);
              if (tool.key !== "trend") setTrendStart(null);
            }}
          >
            {tool.icon}
          </button>
        ))}
      </div>

      {/* ── Trend çizgisi ipucu ── */}
      {trendStart && (
        <div className="chart-trend-hint">
          Bitiş noktasını seçin — başlangıç: <strong>{formatNumber(trendStart.price, 2)}</strong>
        </div>
      )}

      {/* ── Aktif çizimler bar'ı ── */}
      {hasDrawings && (
        <div className="advanced-chart-drawings-bar">
          {drawings.stopLoss && (
            <span className="drawing-chip drawing-chip--sl">
              🔴 Stop: {formatNumber(drawings.stopLoss.price, 2)}
              <button className="drawing-chip-del" onClick={removeStopLoss} title="Sil">×</button>
            </span>
          )}
          {drawings.takeProfit && (
            <span className="drawing-chip drawing-chip--tp">
              🟢 TP: {formatNumber(drawings.takeProfit.price, 2)}
              <button className="drawing-chip-del" onClick={removeTakeProfit} title="Sil">×</button>
            </span>
          )}
          {drawings.horizontalLines.map((line) => (
            <span key={line.id} className="drawing-chip drawing-chip--hline">
              — {formatNumber(line.price, 2)}
              <button className="drawing-chip-del" onClick={() => removeHorizontalLine(line.id)} title="Sil">×</button>
            </span>
          ))}
          {drawings.trendLines.map((tl) => (
            <span key={tl.id} className="drawing-chip drawing-chip--trend">
              ╱ Trend
              <button className="drawing-chip-del" onClick={() => removeTrendLine(tl.id)} title="Sil">×</button>
            </span>
          ))}
        </div>
      )}

      {/* ── Grafik canvas ── */}
      <div className={`advanced-chart-body${isDrawing ? " drawing-mode" : ""}`}>
        {loading && (
          <div className="advanced-chart-overlay">
            <span>Yükleniyor…</span>
          </div>
        )}
        {!loading && error && (
          <div className="advanced-chart-overlay advanced-chart-overlay--error">
            <span>{error}</span>
            <button
              className="chart-retry-btn"
              onClick={() => setTimeframe((t) => t)}
            >
              Tekrar Dene
            </button>
          </div>
        )}
        <div ref={containerRef} className="advanced-chart-canvas" />
      </div>

    </section>
  );
}
