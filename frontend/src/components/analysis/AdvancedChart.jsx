import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createChart, CandlestickSeries, LineSeries } from "lightweight-charts";
import { Check, CircleAlert, Minus, MousePointer2, Target, TrendingUp, X } from "lucide-react";
import { createAlert } from "../../api/alertApi";
import { getAdvancedTechnical, getTechnicalCandles } from "../../api/analysisApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useAuth } from "../../auth/AuthContext";
import useToast from "../../hooks/useToast";
import { useTheme } from "../../theme/ThemeContext";
import { formatNumber } from "../../utils/formatters";

const RANGE_OPTIONS = [
  { label: "1A", value: "1m" },
  { label: "3A", value: "3m" },
  { label: "6A", value: "6m" },
  { label: "1Y", value: "1y" },
  { label: "MAX", value: "max" },
];

const INDICATOR_COLORS = {
  sma7: "#0f766e",
  sma20: "#2563eb",
  sma50: "#f59e0b",
};

const INDICATOR_BUTTONS = [
  { key: "sma7", label: "MA 7", color: "#0f766e" },
  { key: "sma20", label: "MA 20", color: "#2563eb" },
  { key: "sma50", label: "MA 50", color: "#f59e0b" },
];

const DRAW_TOOLS = [
  { key: "cursor", icon: MousePointer2, label: "Imlec" },
  { key: "horizontal", icon: Minus, label: "Yatay Cizgi" },
  { key: "trend", icon: TrendingUp, label: "Trend Cizgisi" },
  { key: "stopLoss", icon: CircleAlert, label: "Stop-Loss" },
  { key: "takeProfit", icon: Target, label: "Take-Profit" },
];

const DEFAULT_RANGE = "6m";
const DEFAULT_INDICATORS = "SMA7,SMA20,SMA50,RSI14";
const CRYPTO_CANDLE_ERROR_MESSAGE = "Yeterli mum verisi bulunamadi.";
const DEFAULT_BAR_SPACING = 12;
const MIN_BAR_SPACING = 8;
const DEFAULT_RIGHT_OFFSET = 3;
const MAX_VISIBLE_CANDLE_BARS = 180;
const MAX_VISIBLE_LINE_BARS = 260;

export default function AdvancedChart({
  instrumentCode,
  initialTimeframe = "1d",
  initialHighlightTool = null,
  presetPrice = null,
  quote = null,
}) {
  const { chartTheme } = useTheme();
  const { userId, login } = useAuth();
  const { toast, showToast } = useToast();

  const containerRef = useRef(null);
  const chartRef = useRef(null);
  const priceSeriesRef = useRef(null);
  const primarySeriesModeRef = useRef(null);
  const indicatorRefs = useRef({});
  const indicatorDataRef = useRef({});
  const activeToolRef = useRef("cursor");
  const drawingsRef = useRef({ stopLoss: null, takeProfit: null, horizontalLines: [], trendLines: [] });
  const trendStartRef = useRef(null);
  const prevInstrumentRef = useRef(null);
  const pendingAutoFitRef = useRef(true);
  const dataPointCountRef = useRef(0);
  const rangeAdjustLockRef = useRef(false);

  const [range, setRange] = useState(() => mapLegacyTimeframeToRange(initialTimeframe));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [activeIndicators, setActiveIndicators] = useState(() => new Set(["sma20", "sma50"]));
  const [activeTool, setActiveTool] = useState(() => mapInitialTool(initialHighlightTool));
  const [drawings, setDrawings] = useState({
    stopLoss: null,
    takeProfit: null,
    horizontalLines: [],
    trendLines: [],
  });
  const [trendStart, setTrendStart] = useState(null);
  const [creatingAlertKey, setCreatingAlertKey] = useState(null);

  activeToolRef.current = activeTool;
  drawingsRef.current = drawings;
  trendStartRef.current = trendStart;

  const isCrypto = String(quote?.instrumentType || "").toUpperCase() === "CRYPTO";
  const changeRate = quote?.changeRate;
  const isPositive = changeRate != null && changeRate >= 0;
  const hasDrawings = Boolean(
    drawings.stopLoss ||
    drawings.takeProfit ||
    drawings.horizontalLines.length ||
    drawings.trendLines.length,
  );

  const rangeDates = useMemo(() => buildDateRange(range), [range]);

  const clearAllDrawings = useCallback(() => {
    const current = drawingsRef.current;
    const priceSeries = priceSeriesRef.current;
    const chart = chartRef.current;

    if (priceSeries) {
      if (current.stopLoss?.priceLine) {
        try { priceSeries.removePriceLine(current.stopLoss.priceLine); } catch { /* noop */ }
      }
      if (current.takeProfit?.priceLine) {
        try { priceSeries.removePriceLine(current.takeProfit.priceLine); } catch { /* noop */ }
      }
      current.horizontalLines.forEach((line) => {
        try { priceSeries.removePriceLine(line.priceLine); } catch { /* noop */ }
      });
    }

    if (chart) {
      current.trendLines.forEach((line) => {
        try { chart.removeSeries(line.series); } catch { /* noop */ }
      });
    }

    setDrawings({ stopLoss: null, takeProfit: null, horizontalLines: [], trendLines: [] });
    setTrendStart(null);
  }, []);

  const ensurePrimarySeries = useCallback((mode) => {
    const chart = chartRef.current;
    if (!chart) {
      return null;
    }

    if (priceSeriesRef.current && primarySeriesModeRef.current === mode) {
      return priceSeriesRef.current;
    }

    clearAllDrawings();

    if (priceSeriesRef.current) {
      try { chart.removeSeries(priceSeriesRef.current); } catch { /* noop */ }
    }

    const series = mode === "candlestick"
      ? chart.addSeries(CandlestickSeries, {
          upColor: "#22c55e",
          borderUpColor: "#22c55e",
          wickUpColor: "#22c55e",
          downColor: "#ef4444",
          borderDownColor: "#ef4444",
          wickDownColor: "#ef4444",
          priceLineVisible: false,
          lastValueVisible: true,
        })
      : chart.addSeries(LineSeries, {
          color: "#4c7fff",
          lineWidth: 2,
          priceLineVisible: false,
          lastValueVisible: true,
        });

    priceSeriesRef.current = series;
    primarySeriesModeRef.current = mode;
    return series;
  }, [clearAllDrawings]);

  const syncIndicatorSeries = useCallback((indicatorData) => {
    const chart = chartRef.current;
    if (!chart) {
      return;
    }

    Object.entries(indicatorRefs.current).forEach(([key, series]) => {
      if (!activeIndicators.has(key) || !indicatorData[key]?.length) {
        try { chart.removeSeries(series); } catch { /* noop */ }
        delete indicatorRefs.current[key];
      }
    });

    Object.entries(indicatorData).forEach(([key, data]) => {
      if (!activeIndicators.has(key) || !data.length) {
        return;
      }

      if (!indicatorRefs.current[key]) {
        indicatorRefs.current[key] = chart.addSeries(LineSeries, {
          color: INDICATOR_COLORS[key] ?? "#94a3b8",
          lineWidth: 2,
          priceLineVisible: false,
          lastValueVisible: false,
        });
      }

      indicatorRefs.current[key].setData(data);
    });
  }, [activeIndicators]);

  const applyPresetLine = useCallback(() => {
    const tool = mapInitialTool(initialHighlightTool);
    if (!tool || !Number.isFinite(presetPrice) || presetPrice <= 0 || !priceSeriesRef.current) {
      return;
    }

    if (tool === "stopLoss" && !drawingsRef.current.stopLoss) {
      addPriceLine(tool, presetPrice);
    }

    if (tool === "takeProfit" && !drawingsRef.current.takeProfit) {
      addPriceLine(tool, presetPrice);
    }
  }, [initialHighlightTool, presetPrice]);

  useEffect(() => {
    if (!containerRef.current) {
      return undefined;
    }

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
        rightOffset: DEFAULT_RIGHT_OFFSET,
        barSpacing: DEFAULT_BAR_SPACING,
        minBarSpacing: MIN_BAR_SPACING,
        fixLeftEdge: true,
        lockVisibleTimeRangeOnResize: true,
      },
    });

    chartRef.current = chart;
    ensurePrimarySeries("line");
    const handleVisibleRangeChange = (logicalRange) => {
      if (!logicalRange || rangeAdjustLockRef.current) {
        return;
      }

      const maxVisibleBars = primarySeriesModeRef.current === "candlestick"
        ? Math.min(dataPointCountRef.current || MAX_VISIBLE_CANDLE_BARS, MAX_VISIBLE_CANDLE_BARS)
        : Math.min(dataPointCountRef.current || MAX_VISIBLE_LINE_BARS, MAX_VISIBLE_LINE_BARS);

      if (!maxVisibleBars) {
        return;
      }

      const visibleBars = logicalRange.to - logicalRange.from;
      if (visibleBars <= maxVisibleBars) {
        return;
      }

      const center = (logicalRange.from + logicalRange.to) / 2;
      rangeAdjustLockRef.current = true;
      chart.timeScale().setVisibleLogicalRange({
        from: center - (maxVisibleBars / 2),
        to: center + (maxVisibleBars / 2),
      });
      requestAnimationFrame(() => {
        rangeAdjustLockRef.current = false;
      });
    };
    chart.timeScale().subscribeVisibleLogicalRangeChange(handleVisibleRangeChange);

    chart.subscribeClick((param) => {
      const priceSeries = priceSeriesRef.current;
      const tool = activeToolRef.current;

      if (!priceSeries || tool === "cursor" || !param.point) {
        return;
      }

      const price = priceSeries.coordinateToPrice(param.point.y);
      if (price == null) {
        return;
      }

      if (tool === "horizontal") {
        const priceLine = priceSeries.createPriceLine({
          price,
          color: "#6b7280",
          lineWidth: 1,
          lineStyle: 2,
          axisLabelVisible: true,
        });
        setDrawings((current) => ({
          ...current,
          horizontalLines: [...current.horizontalLines, { id: Date.now(), price, priceLine }],
        }));
        return;
      }

      if (tool === "stopLoss" || tool === "takeProfit") {
        addPriceLine(tool, price);
        return;
      }

      if (tool === "trend") {
        const start = trendStartRef.current;
        if (!start) {
          if (param.time == null) {
            return;
          }
          setTrendStart({ time: param.time, price });
          return;
        }

        if (param.time == null || param.time === start.time) {
          setTrendStart(null);
          return;
        }

        const lineData = [
          { time: start.time, value: start.price },
          { time: param.time, value: price },
        ].sort((left, right) => left.time - right.time);

        const trendSeries = chart.addSeries(LineSeries, {
          color: "#8b5cf6",
          lineWidth: 1,
          priceLineVisible: false,
          lastValueVisible: false,
        });
        trendSeries.setData(lineData);

        setDrawings((current) => ({
          ...current,
          trendLines: [...current.trendLines, { id: Date.now(), series: trendSeries }],
        }));
        setTrendStart(null);
      }
    });

    const onResize = () => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: containerRef.current.clientWidth });
      }
    };

    window.addEventListener("resize", onResize);

    return () => {
      window.removeEventListener("resize", onResize);
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(handleVisibleRangeChange);
      chart.remove();
      chartRef.current = null;
      priceSeriesRef.current = null;
      primarySeriesModeRef.current = null;
      indicatorRefs.current = {};
    };
  }, [ensurePrimarySeries]);

  useEffect(() => {
    if (!chartRef.current) {
      return;
    }

    chartRef.current.applyOptions({
      layout: { textColor: chartTheme.axis },
      grid: {
        vertLines: { color: chartTheme.grid },
        horzLines: { color: chartTheme.grid },
      },
      rightPriceScale: { borderColor: chartTheme.grid, autoScale: true },
      timeScale: {
        borderColor: chartTheme.grid,
        rightOffset: DEFAULT_RIGHT_OFFSET,
        barSpacing: DEFAULT_BAR_SPACING,
        minBarSpacing: MIN_BAR_SPACING,
        fixLeftEdge: true,
        lockVisibleTimeRangeOnResize: true,
      },
    });
  }, [chartTheme]);

  useEffect(() => {
    if (!instrumentCode || !chartRef.current) {
      return undefined;
    }

    if (prevInstrumentRef.current && prevInstrumentRef.current !== instrumentCode) {
      clearAllDrawings();
    }
    prevInstrumentRef.current = instrumentCode;
    pendingAutoFitRef.current = true;

    let cancelled = false;

    async function fetchData() {
      setLoading(true);
      setError(null);

      try {
        const result = isCrypto
          ? await loadCryptoData(instrumentCode, range)
          : await loadLineData(instrumentCode, rangeDates);

        if (cancelled) {
          return;
        }

        const primarySeries = ensurePrimarySeries(result.mode);
        if (!primarySeries) {
          return;
        }

        primarySeries.setData(result.priceData);
        dataPointCountRef.current = result.priceData.length;
        indicatorDataRef.current = result.indicatorData;
        syncIndicatorSeries(result.indicatorData);
        if (pendingAutoFitRef.current) {
          chartRef.current?.timeScale().fitContent();
          pendingAutoFitRef.current = false;
        }
        applyPresetLine();
      } catch (fetchError) {
        if (!cancelled) {
          setError(extractErrorMessage(fetchError, "Veri yuklenirken hata olustu."));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    fetchData();
    return () => {
      cancelled = true;
    };
  }, [
    applyPresetLine,
    clearAllDrawings,
    ensurePrimarySeries,
    instrumentCode,
    isCrypto,
    range,
    rangeDates,
    reloadToken,
    syncIndicatorSeries,
  ]);

  useEffect(() => {
    syncIndicatorSeries(indicatorDataRef.current);
  }, [activeIndicators, syncIndicatorSeries]);

  useEffect(() => {
    const handleOutside = (event) => {
      if (activeTool === "cursor") {
        return;
      }

      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setActiveTool("cursor");
        setTrendStart(null);
      }
    };

    document.addEventListener("mousedown", handleOutside);
    return () => document.removeEventListener("mousedown", handleOutside);
  }, [activeTool]);

  function addPriceLine(tool, price) {
    const priceSeries = priceSeriesRef.current;
    if (!priceSeries) {
      return;
    }

    const current = drawingsRef.current;
    const previous = tool === "stopLoss" ? current.stopLoss : current.takeProfit;
    if (previous?.priceLine) {
      try { priceSeries.removePriceLine(previous.priceLine); } catch { /* noop */ }
    }

    const config = tool === "stopLoss"
      ? { color: "#ef4444", title: "Stop-Loss" }
      : { color: "#22c55e", title: "Take-Profit" };

    const priceLine = priceSeries.createPriceLine({
      price,
      color: config.color,
      lineWidth: 2,
      lineStyle: 0,
      axisLabelVisible: true,
      title: config.title,
    });

    setDrawings((currentState) => ({
      ...currentState,
      [tool]: { price, priceLine },
    }));
  }

  function removeStopLoss() {
    if (drawings.stopLoss?.priceLine) {
      try { priceSeriesRef.current?.removePriceLine(drawings.stopLoss.priceLine); } catch { /* noop */ }
    }
    setDrawings((current) => ({ ...current, stopLoss: null }));
  }

  function removeTakeProfit() {
    if (drawings.takeProfit?.priceLine) {
      try { priceSeriesRef.current?.removePriceLine(drawings.takeProfit.priceLine); } catch { /* noop */ }
    }
    setDrawings((current) => ({ ...current, takeProfit: null }));
  }

  function removeHorizontalLine(id) {
    const line = drawings.horizontalLines.find((item) => item.id === id);
    if (line?.priceLine) {
      try { priceSeriesRef.current?.removePriceLine(line.priceLine); } catch { /* noop */ }
    }
    setDrawings((current) => ({
      ...current,
      horizontalLines: current.horizontalLines.filter((item) => item.id !== id),
    }));
  }

  function removeTrendLine(id) {
    const trendLine = drawings.trendLines.find((item) => item.id === id);
    if (trendLine?.series) {
      try { chartRef.current?.removeSeries(trendLine.series); } catch { /* noop */ }
    }
    setDrawings((current) => ({
      ...current,
      trendLines: current.trendLines.filter((item) => item.id !== id),
    }));
  }

  async function handleCreateAlert(kind) {
    const drawing = kind === "stopLoss" ? drawings.stopLoss : drawings.takeProfit;
    if (!drawing?.price || !instrumentCode) {
      return;
    }

    if (!userId) {
      await login();
      return;
    }

    const conditionType = kind === "stopLoss" ? "BELOW" : "ABOVE";

    try {
      setCreatingAlertKey(kind);
      await createAlert(userId, {
        instrumentCode,
        conditionType,
        targetPrice: Number(drawing.price.toFixed(8)),
      });
      showToast("success", kind === "stopLoss" ? "Stop-loss alarmi olusturuldu." : "Take-profit alarmi olusturuldu.");
    } catch (createError) {
      showToast("error", extractErrorMessage(createError, "Alarm olusturulamadi."));
    } finally {
      setCreatingAlertKey(null);
    }
  }

  function toggleIndicator(key) {
    setActiveIndicators((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  return (
    <section className="panel-surface advanced-chart-card">
      {toast ? (
        <div className={`toast-notify ${toast.type}`}>
          {toast.type === "success"
            ? <Check size={15} strokeWidth={2.5} className="toast-notify-icon" />
            : <X size={15} strokeWidth={2.5} className="toast-notify-icon" />}
          <span>{toast.message}</span>
        </div>
      ) : null}

      <div className="advanced-chart-header">
        <div className="advanced-chart-header-left">
          <span className="advanced-chart-symbol">{instrumentCode?.toUpperCase() ?? "-"}</span>
          {quote?.displayName && quote.displayName !== instrumentCode?.toUpperCase() ? (
            <span className="advanced-chart-name">{quote.displayName}</span>
          ) : null}
          {quote?.price != null ? (
            <span className="advanced-chart-price">{formatNumber(quote.price, 2)}</span>
          ) : null}
          {changeRate != null ? (
            <span className={`advanced-chart-change ${isPositive ? "positive" : "negative"}`}>
              {isPositive ? "+" : ""}{Number(changeRate).toFixed(2)}%
            </span>
          ) : null}
        </div>

        <div className="chart-timeframes">
          {RANGE_OPTIONS.map((option) => (
            <button
              key={option.value}
              className={`chart-tf-btn${range === option.value ? " active" : ""}`}
              onClick={() => setRange(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="advanced-chart-indicators">
        {INDICATOR_BUTTONS.map((button) => (
          <button
            key={button.key}
            className={`chart-indicator-btn${activeIndicators.has(button.key) ? " active" : ""}`}
            style={{ "--indicator-color": button.color }}
            onClick={() => toggleIndicator(button.key)}
          >
            <span className="chart-indicator-dot" />
            {button.label}
          </button>
        ))}

        <span className="chart-toolbar-sep" aria-hidden="true" />

        {DRAW_TOOLS.map((tool) => {
          const Icon = tool.icon;
          return (
            <button
              key={tool.key}
              title={tool.label}
              className={`draw-tool-btn${activeTool === tool.key ? " active" : ""}`}
              onClick={() => {
                setActiveTool(tool.key);
                if (tool.key !== "trend") {
                  setTrendStart(null);
                }
              }}
            >
              <Icon size={16} strokeWidth={2} />
            </button>
          );
        })}
      </div>

      {trendStart ? (
        <div className="chart-trend-hint">
          Bitis noktasini secin. Baslangic: <strong>{formatNumber(trendStart.price, 2)}</strong>
        </div>
      ) : null}

      {hasDrawings ? (
        <div className="advanced-chart-drawings-bar">
          {drawings.stopLoss ? (
            <span className="drawing-chip drawing-chip--sl">
              Stop: {formatNumber(drawings.stopLoss.price, 2)}
              <button
                className="drawing-chip-action"
                onClick={() => handleCreateAlert("stopLoss")}
                disabled={creatingAlertKey === "stopLoss"}
              >
                {creatingAlertKey === "stopLoss" ? "Ekleniyor" : "Alarm olarak ekle"}
              </button>
              <button className="drawing-chip-del" onClick={removeStopLoss} title="Sil">x</button>
            </span>
          ) : null}

          {drawings.takeProfit ? (
            <span className="drawing-chip drawing-chip--tp">
              TP: {formatNumber(drawings.takeProfit.price, 2)}
              <button
                className="drawing-chip-action"
                onClick={() => handleCreateAlert("takeProfit")}
                disabled={creatingAlertKey === "takeProfit"}
              >
                {creatingAlertKey === "takeProfit" ? "Ekleniyor" : "Alarm olarak ekle"}
              </button>
              <button className="drawing-chip-del" onClick={removeTakeProfit} title="Sil">x</button>
            </span>
          ) : null}

          {drawings.horizontalLines.map((line) => (
            <span key={line.id} className="drawing-chip drawing-chip--hline">
              Yatay: {formatNumber(line.price, 2)}
              <button className="drawing-chip-del" onClick={() => removeHorizontalLine(line.id)} title="Sil">x</button>
            </span>
          ))}

          {drawings.trendLines.map((line) => (
            <span key={line.id} className="drawing-chip drawing-chip--trend">
              Trend
              <button className="drawing-chip-del" onClick={() => removeTrendLine(line.id)} title="Sil">x</button>
            </span>
          ))}
        </div>
      ) : null}

      <div className={`advanced-chart-body${activeTool !== "cursor" ? " drawing-mode" : ""}`}>
        {loading ? (
          <div className="advanced-chart-overlay">
            <span>Yukleniyor...</span>
          </div>
        ) : null}

        {!loading && error ? (
          <div className="advanced-chart-overlay advanced-chart-overlay--error">
            <span>{error}</span>
            <button className="chart-retry-btn" onClick={() => setReloadToken((value) => value + 1)}>
              Tekrar dene
            </button>
          </div>
        ) : null}

        <div ref={containerRef} className="advanced-chart-canvas" />
      </div>
    </section>
  );
}

async function loadCryptoData(symbol, range) {
  const candles = await getTechnicalCandles(symbol, { range, interval: "1d" });
  const normalizedCandles = normalizeCandles(candles);

  if (!normalizedCandles.length) {
    throw new Error(CRYPTO_CANDLE_ERROR_MESSAGE);
  }

  return {
    mode: "candlestick",
    priceData: normalizedCandles.map((candle) => ({
      time: candle.time,
      open: candle.open,
      high: candle.high,
      low: candle.low,
      close: candle.close,
    })),
    indicatorData: {
      sma7: mapIndicatorSeries(normalizedCandles, "sma7"),
      sma20: mapIndicatorSeries(normalizedCandles, "sma20"),
      sma50: mapIndicatorSeries(normalizedCandles, "sma50"),
    },
  };
}

async function loadLineData(symbol, rangeDates) {
  const analysis = await getAdvancedTechnical(symbol, {
    from: rangeDates.from,
    to: rangeDates.to,
    indicators: DEFAULT_INDICATORS,
  });

  const points = Array.isArray(analysis?.points) ? analysis.points : [];
  if (!points.length) {
    throw new Error("Bu enstruman icin veri bulunamadi.");
  }

  return {
    mode: "line",
    priceData: points
      .filter((point) => point?.date && point?.close != null)
      .map((point) => ({
        time: toEpochSeconds(point.date),
        value: point.close,
      })),
    indicatorData: {
      sma7: points
        .filter((point) => point?.date && point?.sma7 != null)
        .map((point) => ({ time: toEpochSeconds(point.date), value: point.sma7 })),
      sma20: points
        .filter((point) => point?.date && point?.sma20 != null)
        .map((point) => ({ time: toEpochSeconds(point.date), value: point.sma20 })),
      sma50: points
        .filter((point) => point?.date && point?.sma50 != null)
        .map((point) => ({ time: toEpochSeconds(point.date), value: point.sma50 })),
    },
  };
}

function mapIndicatorSeries(candles, key) {
  return candles
    .filter((candle) => candle?.time != null && candle?.[key] != null)
    .map((candle) => ({ time: candle.time, value: candle[key] }));
}

function normalizeCandles(candles) {
  if (!Array.isArray(candles)) {
    return [];
  }

  const deduped = new Map();

  candles.forEach((candle) => {
    const time = Number(candle?.timestamp);
    const open = toFiniteNumber(candle?.open);
    const high = toFiniteNumber(candle?.high);
    const low = toFiniteNumber(candle?.low);
    const close = toFiniteNumber(candle?.close);

    if (!Number.isInteger(time) || time <= 0) {
      return;
    }
    if ([open, high, low, close].some((value) => value == null)) {
      return;
    }

    deduped.set(time, {
      time,
      open,
      high,
      low,
      close,
      sma7: toFiniteNumber(candle?.sma7),
      sma20: toFiniteNumber(candle?.sma20),
      sma50: toFiniteNumber(candle?.sma50),
    });
  });

  return Array.from(deduped.values()).sort((left, right) => left.time - right.time);
}

function toFiniteNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function buildDateRange(range) {
  const today = new Date();
  const from = new Date(today);

  switch (range) {
    case "1m":
      from.setMonth(from.getMonth() - 1);
      break;
    case "3m":
      from.setMonth(from.getMonth() - 3);
      break;
    case "6m":
      from.setMonth(from.getMonth() - 6);
      break;
    case "1y":
      from.setFullYear(from.getFullYear() - 1);
      break;
    case "max":
      return {
        from: "2000-01-01",
        to: toIsoDate(today),
      };
      break;
    default:
      from.setMonth(from.getMonth() - 6);
      break;
  }

  return {
    from: toIsoDate(from),
    to: toIsoDate(today),
  };
}

function toIsoDate(value) {
  return value.toISOString().slice(0, 10);
}

function toEpochSeconds(date) {
  return Math.floor(new Date(`${date}T00:00:00Z`).getTime() / 1000);
}

function mapLegacyTimeframeToRange(value) {
  switch (String(value || "").toLowerCase()) {
    case "1h":
      return "1m";
    case "4h":
      return "3m";
    case "1w":
      return "1y";
    case "1d":
    default:
      return DEFAULT_RANGE;
  }
}

function mapInitialTool(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "STOPLOSS_LINE") {
    return "stopLoss";
  }
  if (normalized === "TAKEPROFIT_LINE") {
    return "takeProfit";
  }
  return null;
}
