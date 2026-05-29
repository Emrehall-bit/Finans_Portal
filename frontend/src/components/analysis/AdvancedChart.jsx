import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { CandlestickSeries, createChart, HistogramSeries, LineSeries } from "lightweight-charts";
import {
  Activity,
  Check,
  ChevronDown,
  CircleAlert,
  Lock,
  Minus,
  MousePointer2,
  Pen,
  Signal,
  Sparkles,
  Target,
  TrendingUp,
  Waves,
  X,
} from "lucide-react";
import { createAlert } from "../../api/alertApi";
import { getAdvancedTechnical, getTechnicalCandles } from "../../api/analysisApi";
import { getAiTechnicalAnalysis } from "../../api/aiApi";
import { getMarketHistory } from "../../api/marketApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useAuth } from "../../auth/AuthContext";
import useToast from "../../hooks/useToast";
import { useTheme } from "../../theme/ThemeContext";
import { formatNumber } from "../../utils/formatters";
import { formatSignalLabel } from "./analysisUtils";

const RANGE_VALUES = ["1m", "3m", "6m", "1y", "max"];

const DRAW_TOOL_DEFS = [
  { key: "cursor", icon: MousePointer2 },
  { key: "horizontal", icon: Minus },
  { key: "trend", icon: TrendingUp },
  { key: "support", icon: Minus },
  { key: "resistance", icon: Minus },
  { key: "stopLoss", icon: CircleAlert },
  { key: "takeProfit", icon: Target },
];

const INDICATOR_REGISTRY = [
  { key: "sma7", label: "MA 7", pane: "price", color: "#0f766e", children: ["sma7"] },
  { key: "sma20", label: "MA 20", pane: "price", color: "#2563eb", children: ["sma20"] },
  { key: "sma50", label: "MA 50", pane: "price", color: "#f59e0b", children: ["sma50"] },
  { key: "ema20", label: "EMA 20", pane: "price", color: "#8b5cf6", children: ["ema20"] },
  {
    key: "bollinger",
    label: "Bollinger",
    pane: "price",
    color: "#94a3b8",
    children: ["bollingerUpper", "bollingerMiddle", "bollingerLower"],
  },
  { key: "volumeMa20", label: "Vol MA", pane: "volume", color: "#64748b", children: ["volumeMa20"] },
];

const DEFAULT_RANGE = "6m";
const DEFAULT_INDICATORS = "SMA7,SMA20,SMA50,RSI14";
const DEFAULT_BAR_SPACING = 11;
const MIN_BAR_SPACING = 8;
const DEFAULT_RIGHT_OFFSET = 2;
const MAX_VISIBLE_CANDLE_BARS = 180;
const MAX_VISIBLE_LINE_BARS = 260;
const PRICE_CHART_HEIGHT = 680;
const VOLUME_CHART_HEIGHT = 48;
const RSI_CHART_HEIGHT = 170;
const TOOLTIP_WIDTH = 230;
const TOOLTIP_HEIGHT = 196;
const TOOLTIP_OFFSET = 18;
const TOOLTIP_VIEWPORT_MARGIN = 12;
const RSI_TOOLTIP_LINE_THRESHOLD = 10;
const STRUCTURE_LINE_HOVER_THRESHOLD = 6;
const DEFAULT_DRAWINGS = {
  stopLoss: null,
  takeProfit: null,
  horizontalLines: [],
  trendLines: [],
};

export default function AdvancedChart({
  instrumentCode,
  initialTimeframe = "1d",
  initialHighlightTool = null,
  presetPrice = null,
  quote = null,
  technicalAnalysis = null,
}) {
  const { t } = useTranslation();
  const { chartTheme } = useTheme();
  const { userId, login, isAuthenticated, isPremium } = useAuth();
  const { toast, showToast } = useToast();

  const rangeOptions = useMemo(
    () => RANGE_VALUES.map((value) => ({ value, label: t(`analysis.chart.range.${value}`) })),
    [t],
  );
  const drawTools = useMemo(
    () => DRAW_TOOL_DEFS.map((def) => ({ ...def, label: resolveDrawToolLabel(def.key, t) })),
    [t],
  );

  const priceContainerRef = useRef(null);
  const volumeContainerRef = useRef(null);
  const rsiContainerRef = useRef(null);
  const priceChartRef = useRef(null);
  const volumeChartRef = useRef(null);
  const rsiChartRef = useRef(null);
  const priceSeriesRef = useRef(null);
  const volumeSeriesRef = useRef(null);
  const rsiSeriesRef = useRef(null);
  const overlaySeriesRefs = useRef({});
  const structureLineRefs = useRef({ support: null, resistance: null });
  const activeToolRef = useRef("cursor");
  const drawingsRef = useRef(DEFAULT_DRAWINGS);
  const trendStartRef = useRef(null);
  const prevInstrumentRef = useRef(null);
  const pendingAutoFitRef = useRef(true);
  const dataPointCountRef = useRef(0);
  const syncLockRef = useRef(false);
  const rangeAdjustLockRef = useRef(false);
  const selectedDrawingKeyRef = useRef(null);
  const hoveredDrawingKeyRef = useRef(null);
  const draggingRef = useRef(null);
  const latestDatasetRef = useRef(null);
  const technicalSnapshotRef = useRef(null);
  const toolsDropdownRef = useRef(null);
  const indicatorsRef = useRef(null);

  const [range, setRange] = useState(() => mapLegacyTimeframeToRange(initialTimeframe));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [activeIndicators, setActiveIndicators] = useState(() => new Set(["sma20", "sma50", "volumeMa20"]));
  const [activeTool, setActiveTool] = useState(() => mapInitialTool(initialHighlightTool));
  const [drawings, setDrawings] = useState(DEFAULT_DRAWINGS);
  const [trendStart, setTrendStart] = useState(null);
  const [creatingAlertKey, setCreatingAlertKey] = useState(null);
  const [tooltipModel, setTooltipModel] = useState(null);
  const [legendTooltipModel, setLegendTooltipModel] = useState(null);
  const [technicalSnapshot, setTechnicalSnapshot] = useState(null);
  const [selectedDrawingKey, setSelectedDrawingKey] = useState(null);
  const [hoveredDrawingKey, setHoveredDrawingKey] = useState(null);
  const [toolsOpen, setToolsOpen] = useState(false);
  const [indicatorsOpen, setIndicatorsOpen] = useState(false);
  const [techTab, setTechTab] = useState("rules");
  const [showStructureLines, setShowStructureLines] = useState(false);
  const [aiData, setAiData] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(null);
  const activeIndicatorItems = useMemo(
    () => INDICATOR_REGISTRY.filter((indicator) => activeIndicators.has(indicator.key)),
    [activeIndicators],
  );
  const resolvedTechnicalSnapshot = useMemo(
    () => mergeTechnicalSnapshot(technicalSnapshot, technicalAnalysis),
    [technicalSnapshot, technicalAnalysis],
  );
  const manualSupportLine = useMemo(
    () => drawings.horizontalLines.find((line) => line.kind === "support") ?? null,
    [drawings.horizontalLines],
  );
  const manualResistanceLine = useMemo(
    () => drawings.horizontalLines.find((line) => line.kind === "resistance") ?? null,
    [drawings.horizontalLines],
  );
  const effectiveTechnicalSnapshot = useMemo(
    () => mergeManualStructureLevels(resolvedTechnicalSnapshot, manualSupportLine, manualResistanceLine),
    [resolvedTechnicalSnapshot, manualSupportLine, manualResistanceLine],
  );
  const technicalView = useMemo(() => buildTechnicalView(effectiveTechnicalSnapshot, t), [effectiveTechnicalSnapshot, t]);

  activeToolRef.current = activeTool;
  drawingsRef.current = drawings;
  trendStartRef.current = trendStart;
  selectedDrawingKeyRef.current = selectedDrawingKey;
  hoveredDrawingKeyRef.current = hoveredDrawingKey;
  technicalSnapshotRef.current = technicalSnapshot;

  const isCrypto = String(quote?.instrumentType || "").toUpperCase() === "CRYPTO";
  const hasDrawings = Boolean(
    drawings.stopLoss ||
    drawings.takeProfit ||
    drawings.horizontalLines.length ||
    drawings.trendLines.length,
  );
  const rangeDates = useMemo(() => buildDateRange(range), [range]);
  const volumeVisible = Boolean(technicalSnapshot?.volumeVisible);

  const clearTrendSelection = useCallback(() => {
    setTrendStart(null);
  }, []);

  const clearAllDrawings = useCallback(() => {
    const current = drawingsRef.current;
    const priceSeries = priceSeriesRef.current;
    const priceChart = priceChartRef.current;

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

    if (priceChart) {
      current.trendLines.forEach((line) => {
        try { priceChart.removeSeries(line.series); } catch { /* noop */ }
      });
    }

    setDrawings({
      stopLoss: null,
      takeProfit: null,
      horizontalLines: [],
      trendLines: [],
    });
    setSelectedDrawingKey(null);
    setHoveredDrawingKey(null);
    clearTrendSelection();
  }, [clearTrendSelection]);

  const clearOverlaySeries = useCallback(() => {
    Object.entries(overlaySeriesRefs.current).forEach(([key, entry]) => {
      try {
        entry.chart.removeSeries(entry.series);
      } catch {
        /* noop */
      }
      delete overlaySeriesRefs.current[key];
    });
  }, []);

  const clearStructurePriceLines = useCallback(() => {
    const priceSeries = priceSeriesRef.current;
    const currentLines = structureLineRefs.current;

    if (priceSeries) {
      Object.values(currentLines).forEach((line) => {
        if (!line) {
          return;
        }
        try {
          priceSeries.removePriceLine(line);
        } catch {
          /* noop */
        }
      });
    }

    structureLineRefs.current = { support: null, resistance: null };
  }, []);

  const syncStructurePriceLines = useCallback((summary) => {
    clearStructurePriceLines();

    const priceSeries = priceSeriesRef.current;
    if (!priceSeries || !summary || !showStructureLines) {
      return;
    }

    if (summary.supportLevel != null) {
      structureLineRefs.current.support = priceSeries.createPriceLine({
        price: summary.supportLevel,
        color: "rgba(34, 197, 94, 0.5)",
        lineWidth: 1,
        lineStyle: 0,
        axisLabelVisible: false,
        title: "Auto Support",
      });
    }

    if (summary.resistanceLevel != null) {
      structureLineRefs.current.resistance = priceSeries.createPriceLine({
        price: summary.resistanceLevel,
        color: "rgba(239, 68, 68, 0.5)",
        lineWidth: 1,
        lineStyle: 0,
        axisLabelVisible: false,
        title: "Auto Resistance",
      });
    }
  }, [clearStructurePriceLines, showStructureLines]);

  const getMaxVisibleBars = useCallback(() => {
    const dataset = latestDatasetRef.current;
    if (!dataset) {
      return MAX_VISIBLE_CANDLE_BARS;
    }
    return dataset.mode === "candlestick"
      ? Math.min(dataset.priceData.length || MAX_VISIBLE_CANDLE_BARS, MAX_VISIBLE_CANDLE_BARS)
      : Math.min(dataset.priceData.length || MAX_VISIBLE_LINE_BARS, MAX_VISIBLE_LINE_BARS);
  }, []);

  const syncVisibleRangeAcrossCharts = useCallback((sourceChart, logicalRange) => {
    if (!logicalRange || syncLockRef.current) {
      return;
    }
    syncLockRef.current = true;
    [priceChartRef.current, volumeChartRef.current, rsiChartRef.current]
      .filter(Boolean)
      .filter((chart) => chart !== sourceChart)
      .forEach((chart) => {
        chart.timeScale().setVisibleLogicalRange(logicalRange);
      });
    requestAnimationFrame(() => {
      syncLockRef.current = false;
    });
  }, []);

  const clampVisibleRange = useCallback((chart, logicalRange) => {
    if (!logicalRange || rangeAdjustLockRef.current) {
      return;
    }
    const maxVisibleBars = getMaxVisibleBars();
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
  }, [getMaxVisibleBars]);

  const updateTooltip = useCallback((param) => {
    const dataset = latestDatasetRef.current;
    const currentSnapshot = technicalSnapshotRef.current;
    if (!dataset || !param?.point || !priceContainerRef.current) {
      setTooltipModel(null);
      return;
    }

    const pointX = param.point.x;
    const pointY = param.point.y;
    if (
      pointX < 0 ||
      pointY < 0 ||
      pointX > priceContainerRef.current.clientWidth ||
      pointY > PRICE_CHART_HEIGHT
    ) {
      setTooltipModel(null);
      return;
    }

    const rect = priceContainerRef.current.getBoundingClientRect();
    const hoveredStructureLine = showStructureLines
      ? resolveHoveredStructureLine({
          priceSeries: priceSeriesRef.current,
          pointY,
          snapshot: currentSnapshot,
        })
      : null;

    if (hoveredStructureLine) {
      const { left, top } = resolveTooltipPosition(rect, pointX, pointY, "price-line");
      setTooltipModel({
        kind: "price-line",
        left,
        top,
        label: resolveStructureHoverLabel(hoveredStructureLine.key, t),
        value: hoveredStructureLine.value,
      });
      return;
    }

    if (!param?.time) {
      setTooltipModel(null);
      return;
    }

    const time = normalizeChartTime(param.time);
    const row = dataset.infoByTime.get(time);
    if (!row) {
      setTooltipModel(null);
      return;
    }

    const { left, top } = resolveTooltipPosition(rect, pointX, pointY, "price");

    setTooltipModel({
      kind: "price",
      left,
      top,
      dateLabel: formatTooltipDate(time),
      row,
    });
  }, [showStructureLines, t]);

  const updateRsiTooltip = useCallback((param) => {
    const dataset = latestDatasetRef.current;
    const rsiContainer = rsiContainerRef.current;
    const rsiSeries = rsiSeriesRef.current;
    if (!dataset || !param?.point || !rsiContainer || !rsiSeries) {
      setTooltipModel(null);
      return;
    }

    const pointX = param.point.x;
    const pointY = param.point.y;
    if (
      pointX < 0 ||
      pointY < 0 ||
      pointX > rsiContainer.clientWidth ||
      pointY > RSI_CHART_HEIGHT
    ) {
      setTooltipModel(null);
      return;
    }

    const rect = rsiContainer.getBoundingClientRect();
    const hoveredRsi = rsiSeries.coordinateToPrice(pointY);
    const time = param.time != null ? normalizeChartTime(param.time) : null;
    const row = time != null ? dataset.infoByTime.get(time) : null;
    const lineRsi = row?.rsi14 ?? null;
    const lineY = lineRsi != null ? rsiSeries.priceToCoordinate(lineRsi) : null;

    if (
      time != null &&
      lineRsi != null &&
      lineY != null &&
      Number.isFinite(lineY) &&
      Math.abs(lineY - pointY) <= RSI_TOOLTIP_LINE_THRESHOLD
    ) {
      const { left, top } = resolveTooltipPosition(rect, pointX, pointY, "rsi-point");
      setTooltipModel({
        kind: "rsi-point",
        left,
        top,
        dateLabel: formatTooltipDate(time),
        rsiValue: lineRsi,
        zoneLabel: t(`analysis.chart.rsiZone.${resolveRsiZoneKey(lineRsi)}`),
      });
      return;
    }

    if (hoveredRsi == null) {
      setTooltipModel(null);
      return;
    }

    setTooltipModel(null);
  }, [t]);

  const showLegendTooltip = useCallback((event, text) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const tooltipWidth = 180;
    const tooltipHeight = 44;
    let left = rect.left + rect.width + 10;
    let top = rect.bottom + 8;

    if (left + tooltipWidth > window.innerWidth - TOOLTIP_VIEWPORT_MARGIN) {
      left = rect.right - tooltipWidth;
    }
    if (left < TOOLTIP_VIEWPORT_MARGIN) {
      left = TOOLTIP_VIEWPORT_MARGIN;
    }
    if (top + tooltipHeight > window.innerHeight - TOOLTIP_VIEWPORT_MARGIN) {
      top = rect.top - tooltipHeight - 10;
    }
    if (top < TOOLTIP_VIEWPORT_MARGIN) {
      top = TOOLTIP_VIEWPORT_MARGIN;
    }

    setLegendTooltipModel({
      left,
      top,
      text,
    });
  }, []);

  const hideLegendTooltip = useCallback(() => {
    setLegendTooltipModel(null);
  }, []);

  const ensureCoreSeries = useCallback((mode) => {
    const priceChart = priceChartRef.current;
    const volumeChart = volumeChartRef.current;
    const rsiChart = rsiChartRef.current;

    if (!priceChart || !volumeChart || !rsiChart) {
      return null;
    }

    if (!priceSeriesRef.current || priceSeriesRef.current.__mode !== mode) {
      clearAllDrawings();
      clearStructurePriceLines();
      clearOverlaySeries();
      if (priceSeriesRef.current) {
        try { priceChart.removeSeries(priceSeriesRef.current); } catch { /* noop */ }
      }

      priceSeriesRef.current = mode === "candlestick"
        ? priceChart.addSeries(CandlestickSeries, {
            upColor: "#22c55e",
            borderUpColor: "#22c55e",
            wickUpColor: "#22c55e",
            downColor: "#ef4444",
            borderDownColor: "#ef4444",
            wickDownColor: "#ef4444",
            priceLineVisible: false,
            lastValueVisible: true,
          })
        : priceChart.addSeries(LineSeries, {
            color: "#4c7fff",
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
          });
      priceSeriesRef.current.__mode = mode;
    }

    if (!volumeSeriesRef.current) {
      volumeSeriesRef.current = volumeChart.addSeries(HistogramSeries, {
        priceLineVisible: false,
        lastValueVisible: false,
        priceFormat: { type: "volume" },
      });
    }

    if (!rsiSeriesRef.current) {
      rsiSeriesRef.current = rsiChart.addSeries(LineSeries, {
        color: "#a855f7",
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false,
        autoscaleInfoProvider: () => ({
          priceRange: {
            minValue: 0,
            maxValue: 100,
          },
        }),
      });

    }

    return {
      priceSeries: priceSeriesRef.current,
      volumeSeries: volumeSeriesRef.current,
      rsiSeries: rsiSeriesRef.current,
    };
  }, [clearAllDrawings, clearOverlaySeries, clearStructurePriceLines]);

  const applyThemeOptions = useCallback(() => {
    const softenedGrid = withAlpha(chartTheme.grid, 0.52);
    const commonTimeScale = {
      borderColor: withAlpha(chartTheme.grid, 0.72),
      rightOffset: DEFAULT_RIGHT_OFFSET,
      barSpacing: DEFAULT_BAR_SPACING,
      minBarSpacing: MIN_BAR_SPACING,
      fixLeftEdge: true,
      lockVisibleTimeRangeOnResize: true,
    };

    [
      [priceChartRef.current, { height: PRICE_CHART_HEIGHT, rightPriceScale: { borderColor: withAlpha(chartTheme.grid, 0.72), autoScale: true } }],
      [volumeChartRef.current, { height: VOLUME_CHART_HEIGHT, rightPriceScale: { visible: false }, leftPriceScale: { visible: false } }],
      [rsiChartRef.current, {
        height: RSI_CHART_HEIGHT,
        rightPriceScale: {
          borderColor: withAlpha(chartTheme.grid, 0.72),
          autoScale: true,
          scaleMargins: { top: 0.08, bottom: 0.08 },
        },
      }],
    ].forEach(([chart, extraOptions]) => {
      if (!chart) {
        return;
      }
      chart.applyOptions({
        layout: {
          background: { color: "transparent" },
          textColor: chartTheme.axis,
        },
        grid: {
          vertLines: { color: softenedGrid },
          horzLines: { color: softenedGrid },
        },
        crosshair: {
          mode: 1,
          vertLine: { color: withAlpha(chartTheme.axis, 0.28), labelBackgroundColor: withAlpha(chartTheme.axis, 0.12) },
          horzLine: { color: withAlpha(chartTheme.axis, 0.2), labelBackgroundColor: withAlpha(chartTheme.axis, 0.12) },
        },
        timeScale: commonTimeScale,
        ...extraOptions,
      });
    });

    if (rsiChartRef.current) {
      rsiChartRef.current.priceScale("right").applyOptions({
        visible: false,
        autoScale: true,
      });
    }
  }, [chartTheme]);

  const syncIndicatorSeries = useCallback((dataset) => {
    if (!dataset) {
      return;
    }

    INDICATOR_REGISTRY.forEach((indicator) => {
      const enabled = activeIndicators.has(indicator.key);
      indicator.children.forEach((seriesKey) => {
        const data = dataset.overlayData[seriesKey] ?? [];
        const existing = overlaySeriesRefs.current[seriesKey];
        if (!enabled || !data.length) {
          if (existing) {
            try { existing.chart.removeSeries(existing.series); } catch { /* noop */ }
            delete overlaySeriesRefs.current[seriesKey];
          }
          return;
        }

        const targetChart = indicator.pane === "volume" ? volumeChartRef.current : priceChartRef.current;
        if (!targetChart) {
          return;
        }

        if (!existing) {
          overlaySeriesRefs.current[seriesKey] = {
            chart: targetChart,
            series: targetChart.addSeries(LineSeries, {
              color: seriesColor(seriesKey),
              lineWidth: indicator.pane === "volume" ? 1.5 : 2,
              priceLineVisible: false,
              lastValueVisible: false,
            }),
          };
        }

        overlaySeriesRefs.current[seriesKey].series.setData(data);
      });
    });
  }, [activeIndicators]);

  const syncIndicatorSeriesRef = useRef(null);
  syncIndicatorSeriesRef.current = syncIndicatorSeries;

  const updateDrawingSelection = useCallback((nextKey, nextHoverKey = null) => {
    setSelectedDrawingKey(nextKey);
    setHoveredDrawingKey(nextHoverKey);
  }, []);

  const addPriceLine = useCallback((tool, price) => {
    const priceSeries = priceSeriesRef.current;
    if (!priceSeries) {
      return;
    }

    const isStopLoss = tool === "stopLoss";
    const existing = isStopLoss ? drawingsRef.current.stopLoss : drawingsRef.current.takeProfit;
    if (existing?.priceLine) {
      try { priceSeries.removePriceLine(existing.priceLine); } catch { /* noop */ }
    }

    const id = isStopLoss ? "stopLoss" : "takeProfit";
    const title = isStopLoss ? "Stop-Loss" : "Take-Profit";
    const color = isStopLoss ? "#ef4444" : "#22c55e";
    const priceLine = priceSeries.createPriceLine({
      price,
      color,
      lineWidth: 2,
      lineStyle: 0,
      axisLabelVisible: true,
      title: `${title} ${formatCompactPrice(price)}`,
    });

    setDrawings((current) => ({
      ...current,
      [id]: {
        id,
        kind: id,
        label: title,
        color,
        price,
        priceLine,
      },
    }));
    setSelectedDrawingKey(id);
  }, []);

  const addHorizontalDrawing = useCallback((tool, price) => {
    const priceSeries = priceSeriesRef.current;
    if (!priceSeries) {
      return;
    }

    const config = resolveHorizontalToolConfig(tool, t);
    if (!config) {
      return;
    }

    const existing = drawingsRef.current.horizontalLines.find((line) => line.kind === tool);
    if (existing?.priceLine) {
      try { priceSeries.removePriceLine(existing.priceLine); } catch { /* noop */ }
    }

    const id = `${tool}-${Date.now()}`;
    const priceLine = priceSeries.createPriceLine({
      price,
      color: config.color,
      lineWidth: 1,
      lineStyle: config.lineStyle,
      axisLabelVisible: true,
      title: `${config.label}: ${formatCompactPrice(price)}`,
    });

    setDrawings((current) => ({
      ...current,
      horizontalLines: [
        ...current.horizontalLines.filter((line) => line.kind !== tool),
        {
          id,
          kind: tool,
          label: config.label,
          color: config.color,
          price,
          priceLine,
        },
      ],
    }));
    setSelectedDrawingKey(id);
  }, [t]);

  const updateHorizontalDrawingPrice = useCallback((drawingKey, nextPrice) => {
    const normalizedPrice = Number(nextPrice);
    if (!Number.isFinite(normalizedPrice) || normalizedPrice <= 0) {
      return;
    }

    setDrawings((current) => {
      if (drawingKey === "stopLoss" || drawingKey === "takeProfit") {
        const target = current[drawingKey];
        if (!target?.priceLine) {
          return current;
        }
        target.priceLine.applyOptions({
          price: normalizedPrice,
          title: `${target.label} ${formatCompactPrice(normalizedPrice)}`,
        });
        return {
          ...current,
          [drawingKey]: { ...target, price: normalizedPrice },
        };
      }

      const nextHorizontalLines = current.horizontalLines.map((line) => {
        if (line.id !== drawingKey) {
          return line;
        }
        line.priceLine.applyOptions({
          price: normalizedPrice,
          title: `${line.label}: ${formatCompactPrice(normalizedPrice)}`,
        });
        return { ...line, price: normalizedPrice };
      });

      return {
        ...current,
        horizontalLines: nextHorizontalLines,
      };
    });
  }, [t]);

  const removeDrawingByKey = useCallback((drawingKey) => {
    if (!drawingKey) {
      return;
    }

    if (drawingKey === "stopLoss") {
      if (drawingsRef.current.stopLoss?.priceLine) {
        try { priceSeriesRef.current?.removePriceLine(drawingsRef.current.stopLoss.priceLine); } catch { /* noop */ }
      }
      setDrawings((current) => ({ ...current, stopLoss: null }));
      setSelectedDrawingKey(null);
      return;
    }

    if (drawingKey === "takeProfit") {
      if (drawingsRef.current.takeProfit?.priceLine) {
        try { priceSeriesRef.current?.removePriceLine(drawingsRef.current.takeProfit.priceLine); } catch { /* noop */ }
      }
      setDrawings((current) => ({ ...current, takeProfit: null }));
      setSelectedDrawingKey(null);
      return;
    }

    const horizontalLine = drawingsRef.current.horizontalLines.find((item) => item.id === drawingKey);
    if (horizontalLine) {
      try { priceSeriesRef.current?.removePriceLine(horizontalLine.priceLine); } catch { /* noop */ }
      setDrawings((current) => ({
        ...current,
        horizontalLines: current.horizontalLines.filter((item) => item.id !== drawingKey),
      }));
      setSelectedDrawingKey(null);
      return;
    }

    const trendLine = drawingsRef.current.trendLines.find((item) => item.id === drawingKey);
    if (trendLine) {
      try { priceChartRef.current?.removeSeries(trendLine.series); } catch { /* noop */ }
      setDrawings((current) => ({
        ...current,
        trendLines: current.trendLines.filter((item) => item.id !== drawingKey),
      }));
      setSelectedDrawingKey(null);
    }
  }, []);

  const handleCreateAlert = useCallback(async (kind) => {
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
      showToast("success", kind === "stopLoss" ? t("analysis.chart.alerts.stopLossCreated") : t("analysis.chart.alerts.takeProfitCreated"));
    } catch (createError) {
      showToast("error", extractErrorMessage(createError, t("analysis.chart.alerts.createFailed")));
    } finally {
      setCreatingAlertKey(null);
    }
  }, [drawings.stopLoss, drawings.takeProfit, instrumentCode, login, showToast, userId, t]);

  useEffect(() => {
    if (!priceContainerRef.current || !volumeContainerRef.current || !rsiContainerRef.current) {
      return undefined;
    }

    const priceChart = createChart(priceContainerRef.current, {
      width: Math.max(priceContainerRef.current.clientWidth, 1),
      height: PRICE_CHART_HEIGHT,
    });
    const volumeChart = createChart(volumeContainerRef.current, {
      width: Math.max(volumeContainerRef.current.clientWidth, 1),
      height: VOLUME_CHART_HEIGHT,
    });
    const rsiChart = createChart(rsiContainerRef.current, {
      width: Math.max(rsiContainerRef.current.clientWidth, 1),
      height: RSI_CHART_HEIGHT,
      localization: {
        priceFormatter: (value) => value.toFixed(0),
      },
    });

    priceChartRef.current = priceChart;
    volumeChartRef.current = volumeChart;
    rsiChartRef.current = rsiChart;
      ensureCoreSeries("line");
      applyThemeOptions();

      rsiChart.priceScale("right").applyOptions({
        visible: false,
        autoScale: true,
        scaleMargins: { top: 0.08, bottom: 0.08 },
      });

    const bindTimeScale = (chart) => {
      const handler = (logicalRange) => {
        clampVisibleRange(chart, logicalRange);
        syncVisibleRangeAcrossCharts(chart, logicalRange);
      };
      chart.timeScale().subscribeVisibleLogicalRangeChange(handler);
      return handler;
    };

    const priceRangeHandler = bindTimeScale(priceChart);
    const volumeRangeHandler = bindTimeScale(volumeChart);
    const rsiRangeHandler = bindTimeScale(rsiChart);

    const handleCrosshairMove = (param) => {
      updateTooltip(param);

      const hoverKey = resolveHoveredDrawingKey(param);
      setHoveredDrawingKey(hoverKey);
      if (priceContainerRef.current) {
        priceContainerRef.current.style.cursor = hoverKey && activeToolRef.current === "cursor" ? "row-resize" : (activeToolRef.current === "cursor" ? "default" : "crosshair");
      }
    };

    priceChart.subscribeCrosshairMove(handleCrosshairMove);

    const handleRsiCrosshairMove = (param) => {
      updateRsiTooltip(param);
    };

    rsiChart.subscribeCrosshairMove(handleRsiCrosshairMove);

    const handleChartClick = (param) => {
      const tool = activeToolRef.current;
      const priceSeries = priceSeriesRef.current;
      if (!priceSeries || !param?.point) {
        return;
      }

      const hoveredKey = resolveHoveredDrawingKey(param);
      if (tool === "cursor" && hoveredKey) {
        setSelectedDrawingKey(hoveredKey);
        return;
      }

      const price = priceSeries.coordinateToPrice(param.point.y);
      if (price == null) {
        return;
      }

      if (tool === "horizontal" || tool === "support" || tool === "resistance") {
        addHorizontalDrawing(tool, price);
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
          setTrendStart({ time: normalizeChartTime(param.time), price });
          return;
        }

        const nextTime = normalizeChartTime(param.time);
        if (nextTime == null || nextTime === start.time) {
          setTrendStart(null);
          return;
        }

        const lineData = [
          { time: start.time, value: start.price },
          { time: nextTime, value: price },
        ].sort((left, right) => left.time - right.time);

        const trendSeries = priceChart.addSeries(LineSeries, {
          color: "#8b5cf6",
          lineWidth: 1.5,
          priceLineVisible: false,
          lastValueVisible: false,
        });
        trendSeries.setData(lineData);

        const id = `trend-${Date.now()}`;
        setDrawings((current) => ({
          ...current,
          trendLines: [...current.trendLines, {
            id,
            kind: "trend",
            label: t("analysis.chart.drawing.trend"),
            color: "#8b5cf6",
            series: trendSeries,
            data: lineData,
          }],
        }));
        setSelectedDrawingKey(id);
        setTrendStart(null);
      }
    };

    priceChart.subscribeClick(handleChartClick);

    const handleMouseDown = (event) => {
      if (activeToolRef.current !== "cursor") {
        return;
      }
      const hoveredKey = hoveredDrawingKeyRef.current;
      if (!hoveredKey) {
        return;
      }
      const price = priceSeriesRef.current?.coordinateToPrice(event.offsetY);
      if (price == null) {
        return;
      }
      draggingRef.current = {
        drawingKey: hoveredKey,
      };
      setSelectedDrawingKey(hoveredKey);
    };

    const handleMouseMove = (event) => {
      if (!draggingRef.current) {
        return;
      }
      const nextPrice = priceSeriesRef.current?.coordinateToPrice(event.offsetY);
      if (nextPrice == null) {
        return;
      }
      updateHorizontalDrawingPrice(draggingRef.current.drawingKey, nextPrice);
    };

    const handleMouseUp = () => {
      draggingRef.current = null;
    };

    const handleResize = () => {
      if (!priceContainerRef.current || !volumeContainerRef.current || !rsiContainerRef.current) {
        return;
      }
      const priceWidth = Math.max(priceContainerRef.current.clientWidth, 1);
      const volumeWidth = Math.max(volumeContainerRef.current.clientWidth, 1);
      const rsiWidth = Math.max(rsiContainerRef.current.clientWidth, 1);
      priceChart.applyOptions({ width: priceWidth });
      volumeChart.applyOptions({ width: volumeWidth });
      rsiChart.applyOptions({ width: rsiWidth });
    };

    const resizeObserver = typeof ResizeObserver !== "undefined"
      ? new ResizeObserver(() => {
          handleResize();
        })
      : null;

    resizeObserver?.observe(priceContainerRef.current);
    resizeObserver?.observe(volumeContainerRef.current);
    resizeObserver?.observe(rsiContainerRef.current);

    requestAnimationFrame(() => {
      handleResize();
    });

    priceContainerRef.current.addEventListener("mousedown", handleMouseDown);
    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);
    window.addEventListener("resize", handleResize);

    return () => {
      priceContainerRef.current?.removeEventListener("mousedown", handleMouseDown);
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
      window.removeEventListener("resize", handleResize);
      resizeObserver?.disconnect();

      priceChart.unsubscribeCrosshairMove(handleCrosshairMove);
      rsiChart.unsubscribeCrosshairMove(handleRsiCrosshairMove);
      priceChart.unsubscribeClick(handleChartClick);
      priceChart.timeScale().unsubscribeVisibleLogicalRangeChange(priceRangeHandler);
      volumeChart.timeScale().unsubscribeVisibleLogicalRangeChange(volumeRangeHandler);
      rsiChart.timeScale().unsubscribeVisibleLogicalRangeChange(rsiRangeHandler);
      priceChart.remove();
      volumeChart.remove();
      rsiChart.remove();

      priceChartRef.current = null;
      volumeChartRef.current = null;
      rsiChartRef.current = null;
      priceSeriesRef.current = null;
      volumeSeriesRef.current = null;
      rsiSeriesRef.current = null;
      overlaySeriesRefs.current = {};
    };
  }, [
    addPriceLine,
    applyThemeOptions,
    clampVisibleRange,
    ensureCoreSeries,
    syncVisibleRangeAcrossCharts,
    updateHorizontalDrawingPrice,
    updateTooltip,
    updateRsiTooltip,
  ]);

  useEffect(() => {
    applyThemeOptions();
  }, [applyThemeOptions]);

  useEffect(() => {
    if (!instrumentCode || !priceChartRef.current) {
      return undefined;
    }

    if (prevInstrumentRef.current && prevInstrumentRef.current !== instrumentCode) {
      clearAllDrawings();
    }
    prevInstrumentRef.current = instrumentCode;
    pendingAutoFitRef.current = true;
    setTooltipModel(null);

    let cancelled = false;

    async function fetchData() {
      setLoading(true);
      setError(null);

      try {
        const dataset = isCrypto
          ? await loadCryptoData(instrumentCode, range, t)
          : await loadLineData(instrumentCode, rangeDates, t, quote);

        if (cancelled) {
          return;
        }

        const coreSeries = ensureCoreSeries(dataset.mode);
        if (!coreSeries) {
          return;
        }

        coreSeries.priceSeries.setData(dataset.priceData);
        coreSeries.volumeSeries.setData(dataset.volumeData);
        coreSeries.rsiSeries.setData(dataset.rsiData);
        latestDatasetRef.current = dataset;
        dataPointCountRef.current = dataset.priceData.length;
        const rsiDebug = {
          ...dataset.summary.rsiDebug,
          renderedSeriesCount: [coreSeries.rsiSeries].filter(Boolean).length,
        };
        setTechnicalSnapshot({
          ...dataset.summary,
          rsiDebug,
        });
        syncStructurePriceLines(dataset.summary);
        syncIndicatorSeriesRef.current(dataset);

        if (pendingAutoFitRef.current) {
          priceChartRef.current?.timeScale().fitContent();
          volumeChartRef.current?.timeScale().fitContent();
          rsiChartRef.current?.timeScale().fitContent();
          pendingAutoFitRef.current = false;
        }

        if (presetPrice && initialHighlightTool) {
          const tool = mapInitialTool(initialHighlightTool);
          if (tool && Number.isFinite(presetPrice) && presetPrice > 0) {
            addPriceLine(tool, presetPrice);
          }
        }
      } catch (fetchError) {
        if (!cancelled) {
          setTechnicalSnapshot(null);
          clearStructurePriceLines();
          setError(resolveAdvancedChartErrorMessage(fetchError, t, isCrypto));
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
    addPriceLine,
    addHorizontalDrawing,
    clearAllDrawings,
    ensureCoreSeries,
    initialHighlightTool,
    instrumentCode,
    isCrypto,
    presetPrice,
    quote?.instrumentType,
    quote?.source,
    range,
    rangeDates,
    reloadToken,
    clearStructurePriceLines,
    syncStructurePriceLines,
  ]);

  useEffect(() => {
    if (!latestDatasetRef.current) {
      return;
    }
    syncIndicatorSeries(latestDatasetRef.current);
  }, [syncIndicatorSeries]);

  useEffect(() => {
    syncStructurePriceLines(technicalSnapshotRef.current);
  }, [showStructureLines, syncStructurePriceLines]);

  useEffect(() => {
    const handleOutside = (event) => {
      if (priceContainerRef.current && !priceContainerRef.current.contains(event.target)) {
        if (activeToolRef.current !== "cursor") {
          setActiveTool("cursor");
        }
        setHoveredDrawingKey(null);
      }
      if (toolsDropdownRef.current && !toolsDropdownRef.current.contains(event.target)) {
        setToolsOpen(false);
      }
      if (indicatorsRef.current && !indicatorsRef.current.contains(event.target)) {
        setIndicatorsOpen(false);
      }
    };

    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        setActiveTool("cursor");
        setTrendStart(null);
        draggingRef.current = null;
        return;
      }

      if ((event.key === "Delete" || event.key === "Backspace") && selectedDrawingKeyRef.current) {
        event.preventDefault();
        removeDrawingByKey(selectedDrawingKeyRef.current);
      }
    };

    document.addEventListener("mousedown", handleOutside);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleOutside);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [removeDrawingByKey]);

  useEffect(() => {
    setAiData(null);
    setAiError(null);
    setTechTab("rules");
  }, [instrumentCode]);

  useEffect(() => {
    if (techTab !== "ai" || !isAuthenticated || !isPremium || !instrumentCode) {
      return undefined;
    }
    if (aiData || aiLoading) {
      return undefined;
    }

    let cancelled = false;

    async function fetchAiData() {
      setAiLoading(true);
      setAiError(null);
      try {
        const data = await getAiTechnicalAnalysis(instrumentCode);
        if (!cancelled) setAiData(data ?? null);
      } catch (err) {
          if (!cancelled) setAiError(extractErrorMessage(err, t("analysis.chart.aiPanel.loadError")));
      } finally {
        if (!cancelled) setAiLoading(false);
      }
    }

    fetchAiData();
    return () => { cancelled = true; };
  }, [techTab, isAuthenticated, isPremium, instrumentCode, aiData, aiLoading, t]);

  const toggleIndicator = useCallback((indicatorKey) => {
    setActiveIndicators((current) => {
      const next = new Set(current);
      if (next.has(indicatorKey)) {
        next.delete(indicatorKey);
      } else {
        next.add(indicatorKey);
      }
      return next;
    });
  }, []);

  function resolveHoveredDrawingKey(param) {
    if (!param?.point || !priceSeriesRef.current) {
      return null;
    }
    const priceAtPointer = priceSeriesRef.current.coordinateToPrice(param.point.y);
    if (priceAtPointer == null) {
      return null;
    }
    const threshold = Math.max(Math.abs(priceAtPointer) * 0.0025, 0.75);

    const horizontalCandidates = [
      drawingsRef.current.stopLoss,
      drawingsRef.current.takeProfit,
      ...drawingsRef.current.horizontalLines,
    ].filter(Boolean);

    const hit = horizontalCandidates.find((line) => Math.abs(line.price - priceAtPointer) <= threshold);
    if (hit) {
      return hit.id ?? hit.kind;
    }
    return null;
  }

  return (
    <section className="panel-surface advanced-chart-card advanced-chart-card--terminal">
      {toast ? (
        <div className={`toast-notify ${toast.type}`}>
          {toast.type === "success"
            ? <Check size={15} strokeWidth={2.5} className="toast-notify-icon" />
            : <X size={15} strokeWidth={2.5} className="toast-notify-icon" />}
          <span>{toast.message}</span>
        </div>
      ) : null}

      <div className="advanced-chart-toolbar">
        <div className="advanced-chart-toolbar-row">
          <div className="chart-timeframes" role="group" aria-label="Range">
            {rangeOptions.map((option) => (
              <button
                type="button"
                key={option.value}
                className={`chart-tf-btn${range === option.value ? " active" : ""}`}
                onClick={() => setRange(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>

          <div className="advanced-chart-toolbar-actions">
            <div className="indicators-dropdown" ref={indicatorsRef}>
              <button
                type="button"
                className={`indicators-trigger${activeIndicators.size > 0 ? " has-active" : ""}`}
                onClick={() => setIndicatorsOpen((o) => !o)}
              >
                <Activity size={14} strokeWidth={2} />
                <span>{t("analysis.chart.indicators.label")}</span>
                {activeIndicators.size > 0 ? (
                  <span className="indicators-count">{activeIndicators.size}</span>
                ) : null}
                <ChevronDown size={11} strokeWidth={2.4} />
              </button>
              {indicatorsOpen ? (
                <div className="indicators-menu">
                  {INDICATOR_REGISTRY.map((indicator) => (
                    <label key={indicator.key} className="indicators-item">
                      <input
                        type="checkbox"
                        checked={activeIndicators.has(indicator.key)}
                        onChange={() => toggleIndicator(indicator.key)}
                      />
                      <span className="indicators-item-dot" style={{ "--indicator-color": indicator.color }} />
                      <span className="indicators-item-label">{indicator.label}</span>
                    </label>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="draw-tools-dropdown" ref={toolsDropdownRef}>
              <button
                type="button"
                className={`draw-tools-toggle${activeTool !== "cursor" ? " is-active" : ""}`}
                onClick={() => setToolsOpen((o) => !o)}
              >
                <Pen size={14} strokeWidth={2} />
                <span>{t("analysis.chart.tools.label")}</span>
                <ChevronDown size={11} strokeWidth={2.4} />
              </button>
              {toolsOpen ? (
                <div className="draw-tools-menu">
                  {drawTools.map((tool) => {
                    const Icon = tool.icon;
                    return (
                      <button
                        type="button"
                        key={tool.key}
                        className={`draw-tool-btn${activeTool === tool.key ? " active" : ""}`}
                        onClick={() => {
                          setActiveTool(tool.key);
                          if (tool.key !== "trend") setTrendStart(null);
                          setToolsOpen(false);
                        }}
                      >
                        <Icon size={14} strokeWidth={2} />
                        <span>{tool.label}</span>
                      </button>
                    );
                  })}
                  <button
                    type="button"
                    className={`draw-tool-btn${showStructureLines ? " active" : ""}`}
                    onClick={() => {
                      setShowStructureLines((current) => !current);
                      setToolsOpen(false);
                    }}
                  >
                    <Signal size={14} strokeWidth={2} />
                    <span>{resolveDrawToolLabel("autoStructure", t)}</span>
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        </div>

        {activeIndicatorItems.length > 0 ? (
          <div className="advanced-chart-active-indicators">
            {activeIndicatorItems.map((indicator) => (
              <button
                key={indicator.key}
                type="button"
                className="advanced-chart-indicator-chip"
                onClick={() => toggleIndicator(indicator.key)}
              >
                <span className="advanced-chart-indicator-chip-dot" style={{ "--indicator-color": indicator.color }} />
                <span>{indicator.label}</span>
                <X size={11} strokeWidth={2.4} />
              </button>
            ))}
          </div>
        ) : null}
      </div>

      {trendStart ? (
        <div className="chart-trend-hint">
          {t("analysis.chart.trendHint")} <strong>{formatNumber(trendStart.price, 2)}</strong>
        </div>
      ) : null}

      <div className="advanced-chart-layout">
        <div className="advanced-chart-workspace">
          {hasDrawings ? (
            <div className="advanced-chart-drawings-bar">
              {drawings.stopLoss ? (
                <DrawingChip
                  drawing={drawings.stopLoss}
                  selected={selectedDrawingKey === "stopLoss"}
                  hovered={hoveredDrawingKey === "stopLoss"}
                  actionLabel={creatingAlertKey === "stopLoss" ? t("analysis.chart.drawing.saving") : t("analysis.chart.drawing.addAlert")}
                  actionDisabled={creatingAlertKey === "stopLoss"}
                  deleteLabel={t("analysis.chart.drawing.delete")}
                  onAction={() => handleCreateAlert("stopLoss")}
                  onDelete={() => removeDrawingByKey("stopLoss")}
                  onSelect={() => updateDrawingSelection("stopLoss")}
                />
              ) : null}

              {drawings.takeProfit ? (
                <DrawingChip
                  drawing={drawings.takeProfit}
                  selected={selectedDrawingKey === "takeProfit"}
                  hovered={hoveredDrawingKey === "takeProfit"}
                  actionLabel={creatingAlertKey === "takeProfit" ? t("analysis.chart.drawing.saving") : t("analysis.chart.drawing.addAlert")}
                  actionDisabled={creatingAlertKey === "takeProfit"}
                  deleteLabel={t("analysis.chart.drawing.delete")}
                  onAction={() => handleCreateAlert("takeProfit")}
                  onDelete={() => removeDrawingByKey("takeProfit")}
                  onSelect={() => updateDrawingSelection("takeProfit")}
                />
              ) : null}

              {drawings.horizontalLines.map((line) => (
                <DrawingChip
                  key={line.id}
                  drawing={line}
                  selected={selectedDrawingKey === line.id}
                  hovered={hoveredDrawingKey === line.id}
                  deleteLabel={t("analysis.chart.drawing.delete")}
                  onDelete={() => removeDrawingByKey(line.id)}
                  onSelect={() => updateDrawingSelection(line.id)}
                />
              ))}

              {drawings.trendLines.map((line) => (
                <DrawingChip
                  key={line.id}
                  drawing={line}
                  selected={selectedDrawingKey === line.id}
                  hovered={hoveredDrawingKey === line.id}
                  deleteLabel={t("analysis.chart.drawing.delete")}
                  onDelete={() => removeDrawingByKey(line.id)}
                  onSelect={() => updateDrawingSelection(line.id)}
                />
              ))}
            </div>
          ) : null}

          <div className={`advanced-chart-stack${activeTool !== "cursor" ? " drawing-mode" : ""}`}>
            <div className="advanced-chart-canvas-shell advanced-chart-canvas-shell--price">
              {loading ? (
                <div className="advanced-chart-overlay">
                  <span>{t("analysis.chart.loading")}</span>
                </div>
              ) : null}

              {!loading && error ? (
                <div className="advanced-chart-overlay advanced-chart-overlay--error">
                  <span>{error}</span>
                  <button className="chart-retry-btn" onClick={() => setReloadToken((value) => value + 1)}>
                    {t("analysis.chart.retry")}
                  </button>
                </div>
              ) : null}

              <div ref={priceContainerRef} className="advanced-chart-canvas advanced-chart-canvas--price" />
            </div>

            {volumeVisible ? (
              <div className="advanced-chart-canvas-shell advanced-chart-canvas-shell--volume">
                <div className="advanced-chart-subpanel-head">
                  <span>Volume</span>
                  <span>{technicalSnapshot?.lastVolume != null ? formatNumber(technicalSnapshot.lastVolume, 0) : "-"}</span>
                </div>
                <div ref={volumeContainerRef} className="advanced-chart-canvas advanced-chart-canvas--volume" />
              </div>
            ) : (
              <div ref={volumeContainerRef} className="advanced-chart-canvas advanced-chart-canvas--volume is-hidden" />
            )}

            <div className="advanced-chart-canvas-shell advanced-chart-canvas-shell--rsi">
              <div className="advanced-chart-subpanel-head advanced-chart-subpanel-head--rsi">
                <div className="advanced-chart-rsi-head-left">
                  <span>RSI (14)</span>
                  <div className="advanced-chart-rsi-legend" aria-label="RSI legend">
                    <span
                      className="advanced-chart-rsi-legend-dot advanced-chart-rsi-legend-dot--overbought"
                      onMouseEnter={(event) => showLegendTooltip(event, "Aşırı alım bölgesi — RSI 70 üzeri")}
                      onMouseLeave={hideLegendTooltip}
                    />
                    <span
                      className="advanced-chart-rsi-legend-dot advanced-chart-rsi-legend-dot--neutral"
                      onMouseEnter={(event) => showLegendTooltip(event, "Nötr bölge — RSI 30-70 arası")}
                      onMouseLeave={hideLegendTooltip}
                    />
                    <span
                      className="advanced-chart-rsi-legend-dot advanced-chart-rsi-legend-dot--oversold"
                      onMouseEnter={(event) => showLegendTooltip(event, "Aşırı satım bölgesi — RSI 30 altı")}
                      onMouseLeave={hideLegendTooltip}
                    />
                  </div>
                </div>
                <span className="advanced-chart-rsi-badge">
                  {effectiveTechnicalSnapshot?.rsiValue != null ? `RSI ${effectiveTechnicalSnapshot.rsiValue.toFixed(2)}` : "RSI -"}
                </span>
              </div>
              <div className="advanced-chart-rsi-guides" aria-hidden="true">
                <div className="advanced-chart-rsi-guide advanced-chart-rsi-guide--overbought" />
                <div className="advanced-chart-rsi-guide advanced-chart-rsi-guide--neutral" />
                <div className="advanced-chart-rsi-guide advanced-chart-rsi-guide--oversold" />
              </div>
              <div className="advanced-chart-rsi-axis" aria-hidden="true">
                <span className="advanced-chart-rsi-axis-label" style={{ top: "8%" }}>100</span>
                <span className="advanced-chart-rsi-axis-label" style={{ top: "33%" }}>70</span>
                <span className="advanced-chart-rsi-axis-label" style={{ top: "50%" }}>50</span>
                <span className="advanced-chart-rsi-axis-label" style={{ top: "67%" }}>30</span>
                <span className="advanced-chart-rsi-axis-label" style={{ top: "92%" }}>0</span>
              </div>
              <div ref={rsiContainerRef} className="advanced-chart-canvas advanced-chart-canvas--rsi" />
            </div>
          </div>
        </div>

        <aside className="advanced-tech-panel">
          <div className="advanced-tech-tabs">
            <button
              className={`tech-tab-btn${techTab === "rules" ? " active" : ""}`}
              onClick={() => setTechTab("rules")}
            >
              {t("analysis.chart.aiPanel.tabRules")}
            </button>
            <button
              className={`tech-tab-btn${techTab === "ai" ? " active" : ""}`}
              onClick={() => setTechTab("ai")}
            >
              <Sparkles size={11} strokeWidth={2} />
              {t("analysis.chart.aiPanel.tabAi")}
            </button>
          </div>

          {techTab === "rules" ? (
            <>
          <TechnicalViewCard view={technicalView} />
          <div className="advanced-tech-panel-head">
            <span>{t("analysis.chart.techPanel.title")}</span>
            <span className={`advanced-tech-badge advanced-tech-badge--${toneFromSignal(resolvedTechnicalSnapshot?.latestSignalTone)}`}>
              {resolvedTechnicalSnapshot?.signalKey
                ? t(`analysis.chart.signal.${resolvedTechnicalSnapshot.signalKey}.short`)
                : (resolvedTechnicalSnapshot?.rawSignalLabel ?? t("analysis.chart.techPanel.waiting"))}
            </span>
          </div>

          <div className="advanced-tech-kpi-stack">
            <KpiCard
              label="RSI 14"
              value={effectiveTechnicalSnapshot?.rsiValue != null ? effectiveTechnicalSnapshot.rsiValue.toFixed(2) : "-"}
              tone={toneFromRsi(effectiveTechnicalSnapshot?.rsiValue)}
              detail={effectiveTechnicalSnapshot?.rsiRegimeKey ? t(`analysis.chart.rsiRegime.${effectiveTechnicalSnapshot.rsiRegimeKey}`) : t("analysis.chart.techPanel.awaitingData")}
            />
            <KpiCard
              label={t("analysis.chart.techPanel.trend")}
              value={effectiveTechnicalSnapshot?.trendKey ? t(`analysis.chart.trend.${effectiveTechnicalSnapshot.trendKey}`) : "-"}
              tone={toneFromSignal(effectiveTechnicalSnapshot?.trendTone)}
              detail={effectiveTechnicalSnapshot?.momentumKey ? t(`analysis.chart.techPanel.momentumState.${effectiveTechnicalSnapshot.momentumKey}`) : t("analysis.chart.techPanel.awaitingData")}
            />
            <KpiCard
              label={t("analysis.chart.techPanel.latestSignal")}
              value={effectiveTechnicalSnapshot?.signalKey
                ? t(`analysis.chart.signal.${effectiveTechnicalSnapshot.signalKey}.short`)
                : (effectiveTechnicalSnapshot?.rawSignalLabel ?? "-")}
              tone={toneFromSignal(effectiveTechnicalSnapshot?.latestSignalTone)}
              detail={effectiveTechnicalSnapshot?.rawSignalText ?? t("analysis.chart.signal.neutral.text")}
              wrap
            />
          </div>

          <div className="advanced-tech-details-stack">
            <StackedStatCard
              label={t("analysis.chart.techPanel.momentum")}
              value={effectiveTechnicalSnapshot?.momentumKey ? t(`analysis.chart.techPanel.momentumState.${effectiveTechnicalSnapshot.momentumKey}`) : "-"}
              tone={effectiveTechnicalSnapshot?.momentumTone ?? "neutral"}
              detail={effectiveTechnicalSnapshot?.momentumValue != null ? `${effectiveTechnicalSnapshot.momentumValue >= 0 ? "+" : ""}${effectiveTechnicalSnapshot.momentumValue.toFixed(2)}%` : t("analysis.chart.techPanel.awaitingData")}
            />
            <StackedStatCard
              label={t("analysis.chart.techPanel.support")}
              value={effectiveTechnicalSnapshot?.supportLevel != null ? formatNumber(effectiveTechnicalSnapshot.supportLevel, 2) : "-"}
              tone="neutral"
              detail={effectiveTechnicalSnapshot?.supportDistancePct != null ? `${effectiveTechnicalSnapshot.supportDistancePct.toFixed(2)}% ${t("analysis.chart.techPanel.fromPrice")}` : t("analysis.chart.techPanel.awaitingData")}
            />
            <StackedStatCard
              label={t("analysis.chart.techPanel.resistance")}
              value={effectiveTechnicalSnapshot?.resistanceLevel != null ? formatNumber(effectiveTechnicalSnapshot.resistanceLevel, 2) : "-"}
              tone="neutral"
              detail={effectiveTechnicalSnapshot?.resistanceDistancePct != null ? `${effectiveTechnicalSnapshot.resistanceDistancePct.toFixed(2)}% ${t("analysis.chart.techPanel.fromPrice")}` : t("analysis.chart.techPanel.awaitingData")}
            />
            <StackedStatCard
              label={t("analysis.chart.techPanel.volatility")}
              value={effectiveTechnicalSnapshot?.volatilityKey ? t(`analysis.chart.volatilityLevel.${effectiveTechnicalSnapshot.volatilityKey}`) : "-"}
              tone={effectiveTechnicalSnapshot?.volatilityTone ?? "neutral"}
              detail={effectiveTechnicalSnapshot?.volatilitySummaryKey ? t(`analysis.chart.volatilitySummary.${effectiveTechnicalSnapshot.volatilitySummaryKey}`) : t("analysis.chart.techPanel.awaitingData")}
            />
            <StackedStatCard
              label={t("analysis.chart.techPanel.maAlignment")}
              value={effectiveTechnicalSnapshot?.maAlignmentKey ? t(`analysis.chart.maAlign.${effectiveTechnicalSnapshot.maAlignmentKey}`) : "-"}
              tone={effectiveTechnicalSnapshot?.maAlignmentTone ?? "neutral"}
              detail={effectiveTechnicalSnapshot?.lastClose != null ? `${t("analysis.chart.techPanel.lastClose")}: ${formatNumber(effectiveTechnicalSnapshot.lastClose, 2)}` : t("analysis.chart.techPanel.awaitingData")}
            />
          </div>
            </>
          ) : (
            <AiTechPanel
              instrumentCode={instrumentCode}
              isAuthenticated={isAuthenticated}
              isPremium={isPremium}
              aiData={aiData}
              aiLoading={aiLoading}
              aiError={aiError}
              onRetry={() => { setAiData(null); setAiError(null); }}
              onLogin={login}
              t={t}
            />
          )}
        </aside>
      </div>

      {tooltipModel ? <CrosshairTooltip model={tooltipModel} /> : null}
      {legendTooltipModel ? <LegendTooltip model={legendTooltipModel} /> : null}
    </section>
  );
}

const DrawingChip = memo(function DrawingChip({
  drawing,
  selected,
  hovered,
  actionLabel,
  actionDisabled,
  deleteLabel,
  onAction,
  onDelete,
  onSelect,
}) {
  return (
    <span
      className={`drawing-chip drawing-chip--${chipTone(drawing.kind)}${selected ? " is-selected" : ""}${hovered ? " is-hovered" : ""}`}
      onMouseEnter={onSelect}
      onClick={onSelect}
      role="button"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect();
        }
      }}
    >
      {drawing.kind === "trend" ? drawing.label : `${drawing.label}: ${formatNumber(drawing.price, 2)}`}
      {onAction ? (
        <button
          className="drawing-chip-action"
          onClick={(event) => {
            event.stopPropagation();
            onAction();
          }}
          disabled={actionDisabled}
        >
          {actionLabel}
        </button>
      ) : null}
      <button
        className="drawing-chip-del"
        onClick={(event) => {
          event.stopPropagation();
          onDelete();
        }}
        title={deleteLabel}
      >
        x
      </button>
    </span>
  );
});

const KpiCard = memo(function KpiCard({ label, value, tone, detail, wrap = false }) {
  return (
    <div className={`advanced-tech-kpi advanced-tech-kpi--${tone}`}>
      <div className="advanced-tech-kpi-head">
        <span className="advanced-tech-kpi-label">{label}</span>
        <strong className={`advanced-tech-kpi-value${wrap ? " is-wrap" : ""}`}>{value}</strong>
      </div>
      <span className="advanced-tech-kpi-detail">{detail}</span>
    </div>
  );
});

const TechnicalViewCard = memo(function TechnicalViewCard({ view }) {
  if (!view) {
    return null;
  }

  return (
    <section className={`advanced-tech-view advanced-tech-view--${view.tone}`}>
      <div className="advanced-tech-view-head">
        <span>{view.title}</span>
        <strong>{view.label}</strong>
      </div>
      <div className="advanced-tech-view-reasons">
        {view.reasons.map((reason) => (
          <span key={reason} className="advanced-tech-view-reason">{reason}</span>
        ))}
      </div>
    </section>
  );
});

const StackedStatCard = memo(function StackedStatCard({ label, value, tone, detail }) {
  return (
    <div className={`advanced-tech-stack-card advanced-tech-stack-card--${tone}`}>
      <div className="advanced-tech-stack-head">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <p>{detail}</p>
    </div>
  );
});

const CrosshairTooltip = memo(function CrosshairTooltip({ model }) {
  const { t } = useTranslation();
  if (typeof document === "undefined") {
    return null;
  }

  let content = null;
  if (model.kind === "rsi-point") {
    content = (
      <>
        <strong>{model.dateLabel}</strong>
        <div className="advanced-crosshair-grid advanced-crosshair-grid--compact">
          <div className="advanced-crosshair-row advanced-crosshair-row--single">
            <strong>{`RSI: ${formatNumber(model.rsiValue, 2)} — ${model.zoneLabel}`}</strong>
          </div>
        </div>
      </>
    );
  } else if (model.kind === "price-line") {
    content = (
      <div className="advanced-crosshair-zone">
        <strong>{`${model.label}: ${formatNumber(model.value, 2)}`}</strong>
      </div>
    );
  } else if (model.kind === "rsi-zone") {
    content = (
      <div className="advanced-crosshair-zone">
        <strong>{model.title}</strong>
        {model.subtitle ? <span>{model.subtitle}</span> : null}
      </div>
    );
  } else {
    const row = model.row;
    content = (
      <>
        <strong>{model.dateLabel}</strong>
        <div className="advanced-crosshair-grid">
          <TooltipMetric label={t("analysis.chart.tooltip.open")} value={row.open} />
          <TooltipMetric label={t("analysis.chart.tooltip.high")} value={row.high} />
          <TooltipMetric label={t("analysis.chart.tooltip.low")} value={row.low} />
          <TooltipMetric label={t("analysis.chart.tooltip.close")} value={row.close} />
          <TooltipMetric label={t("analysis.chart.tooltip.volume")} value={row.volume} digits={0} />
          <TooltipMetric label={t("analysis.chart.tooltip.change")} value={row.changePct} suffix="%" digits={2} />
        </div>
      </>
    );
  }

  return createPortal(
    <div className="advanced-crosshair-tooltip" style={{ left: model.left, top: model.top }}>
      {content}
    </div>,
    document.body,
  );
});

const TooltipMetric = memo(function TooltipMetric({ label, value, digits = 2, suffix = "" }) {
  return (
    <div className="advanced-crosshair-row">
      <span>{label}</span>
      <strong>{value == null ? "-" : `${formatNumber(value, digits)}${suffix}`}</strong>
    </div>
  );
});

const LegendTooltip = memo(function LegendTooltip({ model }) {
  if (typeof document === "undefined") {
    return null;
  }

  return createPortal(
    <div className="advanced-legend-tooltip" style={{ left: model.left, top: model.top }}>
      <span>{model.text}</span>
      <i className="advanced-legend-tooltip-arrow" aria-hidden="true" />
    </div>,
    document.body,
  );
});

async function loadCryptoData(symbol, range, t) {
  const candles = normalizeCandles(await getTechnicalCandles(symbol, { range, interval: "1d" }));
  if (!candles.length) {
    throw new Error(t("analysis.chart.errors.noCandles"));
  }

  const closes = candles.map((candle) => candle.close);
  const volumes = candles.map((candle) => candle.volume ?? null);
  const overlayData = buildOverlayData(candles, closes, volumes);
  const infoByTime = buildInfoByTime(candles);
  const rsiData = candles
    .filter((candle) => candle.rsi14 != null)
    .map((candle) => ({ time: candle.time, value: candle.rsi14 }));
  const rsiStats = buildRsiStats(rsiData);

  return {
    mode: "candlestick",
    priceData: candles.map((candle) => ({
      time: candle.time,
      open: candle.open,
      high: candle.high,
      low: candle.low,
      close: candle.close,
    })),
    volumeData: candles
      .filter((candle) => candle.volume != null)
      .map((candle) => ({
        time: candle.time,
        value: candle.volume,
        color: candle.close >= candle.open ? "rgba(34, 197, 94, 0.72)" : "rgba(239, 68, 68, 0.72)",
      })),
    rsiData,
    overlayData,
    infoByTime,
    summary: buildTechnicalSummary({
      rows: candles,
      latestRow: candles.at(-1),
      previousRow: candles.at(-2),
      trendDirection: deriveTrendDirection(candles),
      overlayData,
      mode: "candlestick",
      volumeVisible: true,
      volumeDataCount: candles.filter((candle) => candle.volume != null).length,
      rsiStats,
    }),
  };
}

async function loadLineData(symbol, rangeDates, t, quote) {
  let analysis = null;
  let analysisError = null;

  try {
    analysis = await getAdvancedTechnical(symbol, {
      from: rangeDates.from,
      to: rangeDates.to,
      indicators: DEFAULT_INDICATORS,
    });
  } catch (error) {
    analysisError = error;
  }

  let points = normalizeLinePoints(Array.isArray(analysis?.points) ? analysis.points : []);
  if (!points.length) {
    const history = await getMarketHistory(symbol, {
      from: rangeDates.from,
      to: rangeDates.to,
      source: quote?.source,
      type: quote?.instrumentType,
    });
    points = normalizeHistoryPoints(history);
  }

  if (!points.length) {
    if (analysisError) {
      throw analysisError;
    }
    throw new Error(t("analysis.chart.errors.noData"));
  }

  const closes = points.map((point) => point.close);
  const overlayData = buildOverlayData(points, closes, []);
  const infoByTime = buildInfoByTime(points);
  const rsiData = points
    .filter((point) => point.rsi14 != null)
    .map((point) => ({ time: point.time, value: point.rsi14 }));
  const rsiStats = buildRsiStats(rsiData);

  return {
    mode: "line",
    priceData: points.map((point) => ({
      time: point.time,
      value: point.close,
    })),
    volumeData: [],
    rsiData,
    overlayData,
    infoByTime,
    summary: buildTechnicalSummary({
      rows: points,
      latestRow: points.at(-1),
      previousRow: points.at(-2),
      trendDirection: normalizeTrendDirection(analysis?.trendDirection) ?? deriveTrendDirection(points),
      overlayData,
      latestSignal: analysis?.signals?.[0]?.label ?? analysis?.signals?.[0]?.signalType ?? null,
      mode: "line",
      volumeVisible: false,
      volumeDataCount: 0,
      rsiStats,
    }),
  };
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
    const volume = toFiniteNumber(candle?.volume);
    const rsi14 = toFiniteNumber(candle?.rsi14);
    const prev = deduped.get(time);
    const previousClose = prev?.close ?? null;

    if (!Number.isInteger(time) || time <= 0) {
      return;
    }
    if ([open, high, low, close].some((value) => value == null)) {
      return;
    }

    deduped.set(time, {
      time,
      dateLabel: formatTooltipDate(time),
      open,
      high,
      low,
      close,
      volume,
      sma7: toPositiveOverlayNumber(candle?.sma7),
      sma20: toPositiveOverlayNumber(candle?.sma20),
      sma50: toPositiveOverlayNumber(candle?.sma50),
      rsi14,
      changePct: previousClose ? ((close - previousClose) / previousClose) * 100 : null,
    });
  });

  const sorted = Array.from(deduped.values()).sort((left, right) => left.time - right.time);
  return sorted.map((row, index) => ({
    ...row,
    changePct: index > 0 ? ((row.close - sorted[index - 1].close) / sorted[index - 1].close) * 100 : row.changePct,
  }));
}

function normalizeLinePoints(points) {
  return points
    .filter((point) => point?.date && point?.close != null)
    .map((point, index, source) => {
      const time = toEpochSeconds(point.date);
      const previous = index > 0 ? source[index - 1] : null;
      const previousClose = previous?.close != null ? Number(previous.close) : null;
      return {
        time,
        dateLabel: point.date,
        open: null,
        high: null,
        low: null,
        close: Number(point.close),
        volume: null,
        sma7: toPositiveOverlayNumber(point.sma7),
        sma20: toPositiveOverlayNumber(point.sma20),
        sma50: toPositiveOverlayNumber(point.sma50),
        rsi14: toFiniteNumber(point.rsi14),
        changePct: previousClose ? ((Number(point.close) - previousClose) / previousClose) * 100 : null,
      };
    });
}

function normalizeHistoryPoints(history) {
  return (Array.isArray(history) ? history : [])
    .map((point, index, source) => {
      const rawDate = point?.priceTimestamp ? String(point.priceTimestamp) : null;
      const close = toFiniteNumber(point?.closePrice);
      if (!rawDate || close == null) {
        return null;
      }

      const formattedDate = rawDate.slice(0, 10);
      const time = toEpochSeconds(formattedDate);
      const previousClose = index > 0 ? toFiniteNumber(source[index - 1]?.closePrice) : null;

      return {
        time,
        dateLabel: formattedDate,
        open: null,
        high: null,
        low: null,
        close,
        volume: null,
        sma7: null,
        sma20: null,
        sma50: null,
        rsi14: null,
        changePct: previousClose ? ((close - previousClose) / previousClose) * 100 : null,
      };
    })
    .filter(Boolean);
}

function buildOverlayData(rows, closes, volumes) {
  const timeRows = rows.map((row) => row.time);
  const ema20 = computeEmaSeries(closes, 20);
  const volumeMa20 = computeSimpleMovingAverageSeries(volumes, 20);
  const bollinger = computeBollingerSeries(closes, 20, 2);

  return {
    sma7: mapSeries(rows, "sma7"),
    sma20: mapSeries(rows, "sma20"),
    sma50: mapSeries(rows, "sma50"),
    ema20: mapSeriesFromValues(timeRows, ema20),
    bollingerUpper: mapSeriesFromValues(timeRows, bollinger.upper),
    bollingerMiddle: mapSeriesFromValues(timeRows, bollinger.middle),
    bollingerLower: mapSeriesFromValues(timeRows, bollinger.lower),
    volumeMa20: mapSeriesFromValues(timeRows, volumeMa20),
  };
}

function mapSeries(rows, key) {
  return rows
    .filter((row) => row[key] != null)
    .map((row) => ({ time: row.time, value: row[key] }));
}

function mapSeriesFromValues(times, values) {
  return times
    .map((time, index) => ({ time, value: values[index] }))
    .filter((entry) => entry.value != null);
}

function buildInfoByTime(rows) {
  return new Map(rows.map((row) => [row.time, row]));
}

function computeEmaSeries(values, period) {
  const normalized = values.map((value) => toFiniteNumber(value));
  if (!normalized.length) {
    return [];
  }

  const multiplier = 2 / (period + 1);
  let previousEma = null;

  return normalized.map((value, index) => {
    if (value == null) {
      return null;
    }
    if (index < period - 1) {
      return null;
    }
    if (index === period - 1) {
      const seed = normalized.slice(0, period);
      if (seed.some((item) => item == null)) {
        return null;
      }
      previousEma = seed.reduce((sum, item) => sum + item, 0) / period;
      return previousEma;
    }

    previousEma = ((value - previousEma) * multiplier) + previousEma;
    return previousEma;
  });
}

function computeSimpleMovingAverageSeries(values, period) {
  const normalized = values.map((v) => toFiniteNumber(v));
  const result = new Array(normalized.length).fill(null);
  let windowSum = 0;
  let validCount = 0;

  for (let i = 0; i < normalized.length; i++) {
    const val = normalized[i];
    if (val != null) { windowSum += val; validCount++; }
    if (i >= period) {
      const dropping = normalized[i - period];
      if (dropping != null) { windowSum -= dropping; validCount--; }
    }
    if (i >= period - 1 && validCount === period) {
      result[i] = windowSum / period;
    }
  }
  return result;
}

function computeBollingerSeries(values, period, multiplier) {
  const normalized = values.map((v) => toFiniteNumber(v));
  const middle = computeSimpleMovingAverageSeries(normalized, period);
  const upper = new Array(normalized.length).fill(null);
  const lower = new Array(normalized.length).fill(null);
  let sumSq = 0;
  let validCount = 0;

  for (let i = 0; i < normalized.length; i++) {
    const val = normalized[i];
    if (val != null) { sumSq += val * val; validCount++; }
    if (i >= period) {
      const dropping = normalized[i - period];
      if (dropping != null) { sumSq -= dropping * dropping; validCount--; }
    }
    if (i >= period - 1 && validCount === period && middle[i] != null) {
      const sd = Math.sqrt(Math.max(0, sumSq / period - middle[i] * middle[i])) * multiplier;
      upper[i] = middle[i] + sd;
      lower[i] = middle[i] - sd;
    }
  }
  return { upper, middle, lower };
}

function buildTechnicalSummary({
  rows,
  latestRow,
  previousRow,
  trendDirection,
  latestSignal,
  mode,
  volumeVisible,
  volumeDataCount,
  rsiStats,
}) {
  const latestRsi = latestRow?.rsi14 ?? null;
  const maAlignment = deriveMaAlignment(latestRow);
  const volatility = deriveVolatility(latestRow, previousRow);
  const momentum = deriveMomentum(rows);
  const supportResistance = deriveSupportResistance(rows, latestRow);
  const rawSignal = normalizeSignalDescriptor(latestSignal);
  const derivedSignal = deriveLatestSignal({ latestRow, trendDirection, maAlignment, volatility });
  const signalTone = rawSignal?.tone ?? derivedSignal.tone;

  return {
    rsiValue: latestRsi,
    rsiRegimeKey: latestRsi == null ? null : latestRsi >= 70 ? "overbought" : latestRsi <= 30 ? "oversold" : "neutral",
    trendKey: (trendDirection ?? "SIDEWAYS").toLowerCase(),
    trendTone: trendTone(trendDirection),
    maAlignmentKey: maAlignment.key,
    maAlignmentTone: maAlignment.tone,
    volatilityKey: volatility.key,
    volatilityTone: volatility.tone,
    volatilitySummaryKey: volatility.summaryKey,
    signalKey: rawSignal ? null : derivedSignal.key,
    rawSignalLabel: rawSignal?.shortLabel ?? null,
    rawSignalText: rawSignal?.text ?? null,
    latestSignalTone: signalTone,
    lastClose: latestRow?.close ?? null,
    lastVolume: latestRow?.volume ?? null,
    latestChangePct: latestRow?.changePct ?? null,
    momentumKey: momentum.key,
    momentumTone: momentum.tone,
    momentumValue: momentum.value,
    sma20: latestRow?.sma20 ?? null,
    sma50: latestRow?.sma50 ?? null,
    supportLevel: supportResistance.support,
    supportDistancePct: supportResistance.supportDistancePct,
    resistanceLevel: supportResistance.resistance,
    resistanceDistancePct: supportResistance.resistanceDistancePct,
    volumeVisible,
    volumeDataCount: volumeDataCount ?? 0,
    rsiDebug: {
      dataCount: rsiStats?.count ?? 0,
      min: rsiStats?.min ?? null,
      max: rsiStats?.max ?? null,
      renderedSeriesCount: (rsiStats?.count ?? 0) > 0 ? 1 : 0,
    },
    mode,
  };
}

function deriveMomentum(rows) {
  const normalized = Array.isArray(rows) ? rows : [];
  if (normalized.length < 6) {
    return { key: "awaiting", tone: "neutral", value: null };
  }
  const latest = toFiniteNumber(normalized.at(-1)?.close);
  const anchor = toFiniteNumber(normalized.at(-6)?.close);
  if (latest == null || anchor == null || anchor === 0) {
    return { key: "awaiting", tone: "neutral", value: null };
  }
  const value = ((latest - anchor) / Math.abs(anchor)) * 100;
  if (value >= 3) {
    return { key: "positive", tone: "positive", value };
  }
  if (value <= -3) {
    return { key: "negative", tone: "negative", value };
  }
  return { key: "neutral", tone: "neutral", value };
}

function deriveSupportResistance(rows, latestRow) {
  const normalized = Array.isArray(rows) ? rows : [];
  const recent = normalized.slice(-20);
  const latestClose = toFiniteNumber(latestRow?.close);
  if (!recent.length || latestClose == null || latestClose === 0) {
    return { support: null, supportDistancePct: null, resistance: null, resistanceDistancePct: null };
  }

  const lows = recent
    .map((row) => toFiniteNumber(row?.low ?? row?.close))
    .filter((value) => value != null);
  const highs = recent
    .map((row) => toFiniteNumber(row?.high ?? row?.close))
    .filter((value) => value != null);

  const support = lows.length ? Math.min(...lows) : null;
  const resistance = highs.length ? Math.max(...highs) : null;

  return {
    support,
    supportDistancePct: support != null ? Math.abs(((latestClose - support) / latestClose) * 100) : null,
    resistance,
    resistanceDistancePct: resistance != null ? Math.abs(((resistance - latestClose) / latestClose) * 100) : null,
  };
}

function buildRsiStats(rsiData) {
  const values = (Array.isArray(rsiData) ? rsiData : [])
    .map((point) => toFiniteNumber(point?.value))
    .filter((value) => value != null);

  if (!values.length) {
    return { count: 0, min: null, max: null };
  }

  return {
    count: values.length,
    min: Math.min(...values),
    max: Math.max(...values),
  };
}

function deriveTrendDirection(rows) {
  if (!Array.isArray(rows) || rows.length < 2) {
    return "SIDEWAYS";
  }
  const latest = rows.at(-1)?.close;
  const reference = rows[Math.max(rows.length - 6, 0)]?.close;
  if (latest == null || reference == null) {
    return "SIDEWAYS";
  }
  if (latest > reference * 1.02) {
    return "UPTREND";
  }
  if (latest < reference * 0.98) {
    return "DOWNTREND";
  }
  return "SIDEWAYS";
}

function normalizeTrendDirection(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized || null;
}

function deriveMaAlignment(latestRow) {
  const sma7 = latestRow?.sma7;
  const sma20 = latestRow?.sma20;
  const sma50 = latestRow?.sma50;

  if ([sma7, sma20, sma50].some((value) => value == null)) {
    return { key: "limited", tone: "neutral" };
  }
  if (sma7 > sma20 && sma20 > sma50) {
    return { key: "bullish", tone: "positive" };
  }
  if (sma7 < sma20 && sma20 < sma50) {
    return { key: "bearish", tone: "negative" };
  }
  return { key: "mixed", tone: "neutral" };
}

function deriveVolatility(latestRow, previousRow) {
  if (!latestRow?.close || !previousRow?.close) {
    return { key: "awaiting", tone: "neutral", summaryKey: "awaiting" };
  }

  const dailyMove = Math.abs(((latestRow.close - previousRow.close) / previousRow.close) * 100);
  if (dailyMove >= 4) {
    return { key: "high", tone: "negative", summaryKey: "high" };
  }
  if (dailyMove >= 2) {
    return { key: "medium", tone: "warning", summaryKey: "medium" };
  }
  return { key: "low", tone: "positive", summaryKey: "low" };
}

function mergeTechnicalSnapshot(snapshot, technicalAnalysis) {
  const baseSnapshot = snapshot ?? null;
  const backendRsi = extractIndicatorValue(technicalAnalysis, "RSI14");
  const backendTrendDirection = normalizeTrendDirection(technicalAnalysis?.trendDirection);
  const backendSignal = normalizeSignalDescriptor(Array.isArray(technicalAnalysis?.signals) ? technicalAnalysis.signals[0] : null);
  const resolvedRsi = backendRsi ?? baseSnapshot?.rsiValue ?? null;

  return {
    ...(baseSnapshot ?? {}),
    rsiValue: resolvedRsi,
    rsiRegimeKey: resolvedRsi == null ? (baseSnapshot?.rsiRegimeKey ?? null) : resolveRsiZoneKey(resolvedRsi),
    trendKey: backendTrendDirection ? backendTrendDirection.toLowerCase() : (baseSnapshot?.trendKey ?? null),
    trendTone: backendTrendDirection ? trendTone(backendTrendDirection) : (baseSnapshot?.trendTone ?? "neutral"),
    signalKey: backendSignal ? null : (baseSnapshot?.signalKey ?? null),
    rawSignalLabel: backendSignal?.shortLabel ?? baseSnapshot?.rawSignalLabel ?? null,
    rawSignalText: backendSignal?.text ?? baseSnapshot?.rawSignalText ?? null,
    latestSignalTone: backendSignal?.tone ?? baseSnapshot?.latestSignalTone ?? "neutral",
  };
}

function deriveLatestSignal({ latestRow, trendDirection, maAlignment, volatility }) {
  if (latestRow?.rsi14 != null && latestRow.rsi14 <= 30) {
    return { key: "oversoldRisk", tone: "positive" };
  }
  if (latestRow?.rsi14 != null && latestRow.rsi14 >= 70) {
    return { key: "overboughtRisk", tone: "warning" };
  }
  if (trendDirection === "UPTREND" && maAlignment.tone === "positive") {
    return { key: "weakBullish", tone: "positive" };
  }
  if (trendDirection === "DOWNTREND" && maAlignment.tone === "negative") {
    return { key: "weakBearish", tone: "negative" };
  }
  if (volatility.tone === "negative") {
    return { key: "highVolatility", tone: "warning" };
  }
  return { key: "neutral", tone: "neutral" };
}

function chipTone(kind) {
  switch (kind) {
    case "stopLoss":
      return "sl";
    case "takeProfit":
      return "tp";
    case "trend":
      return "trend";
    case "support":
      return "tp";
    case "resistance":
      return "sl";
    default:
      return "hline";
  }
}

function resolveDrawToolLabel(key, t) {
  switch (key) {
    case "support":
      return "Destek Çiz";
    case "resistance":
      return "Direnç Çiz";
    case "autoStructure":
      return "Otomatik Destek/Direnç";
    default:
      return t(`analysis.chart.tools.${key}`);
  }
}

function resolveStructureHoverLabel(key, t) {
  switch (key) {
    case "autoSupport":
      return "Otomatik Destek";
    case "autoResistance":
      return "Otomatik Direnç";
    case "support":
      return "Destek";
    case "resistance":
      return "Direnç";
    default:
      return t("analysis.chart.techPanel.awaitingData");
  }
}

function resolveHorizontalToolConfig(tool, t) {
  switch (tool) {
    case "horizontal":
      return {
        label: t("analysis.chart.drawing.horizontal"),
        color: "#64748b",
        lineStyle: 2,
      };
    case "support":
      return {
        label: "Destek",
        color: "rgba(34, 197, 94, 0.66)",
        lineStyle: 2,
      };
    case "resistance":
      return {
        label: "Direnç",
        color: "rgba(239, 68, 68, 0.66)",
        lineStyle: 2,
      };
    default:
      return null;
  }
}

function mergeManualStructureLevels(snapshot, manualSupportLine, manualResistanceLine) {
  const baseSnapshot = snapshot ?? null;
  if (!baseSnapshot) {
    return null;
  }

  const lastClose = toFiniteNumber(baseSnapshot.lastClose);
  const support = toFiniteNumber(manualSupportLine?.price) ?? toFiniteNumber(baseSnapshot.supportLevel);
  const resistance = toFiniteNumber(manualResistanceLine?.price) ?? toFiniteNumber(baseSnapshot.resistanceLevel);

  return {
    ...baseSnapshot,
    supportLevel: support,
    supportDistancePct: support != null && lastClose != null && lastClose !== 0
      ? Math.abs(((lastClose - support) / lastClose) * 100)
      : null,
    resistanceLevel: resistance,
    resistanceDistancePct: resistance != null && lastClose != null && lastClose !== 0
      ? Math.abs(((resistance - lastClose) / lastClose) * 100)
      : null,
  };
}

function seriesColor(key) {
  switch (key) {
    case "sma7":
      return "#0f766e";
    case "sma20":
      return "#2563eb";
    case "sma50":
      return "#f59e0b";
    case "ema20":
      return "#8b5cf6";
    case "bollingerUpper":
    case "bollingerMiddle":
    case "bollingerLower":
      return "#94a3b8";
    case "volumeMa20":
      return "#64748b";
    default:
      return "#94a3b8";
  }
}

function toneFromRsi(rsi) {
  if (rsi == null) {
    return "neutral";
  }
  if (rsi >= 70) {
    return "warning";
  }
  if (rsi <= 30) {
    return "positive";
  }
  return "neutral";
}

function resolveTooltipPosition(rect, pointX, pointY, kind) {
  const { width, height } = tooltipDimensions(kind);
  let left = rect.left + pointX + TOOLTIP_OFFSET;
  let top = rect.top + pointY - TOOLTIP_OFFSET;

  if (left + width > window.innerWidth - TOOLTIP_VIEWPORT_MARGIN) {
    left = rect.left + pointX - width - TOOLTIP_OFFSET;
  }
  if (left < TOOLTIP_VIEWPORT_MARGIN) {
    left = TOOLTIP_VIEWPORT_MARGIN;
  }

  if (top + height > window.innerHeight - TOOLTIP_VIEWPORT_MARGIN) {
    top = window.innerHeight - height - TOOLTIP_VIEWPORT_MARGIN;
  }
  if (top < TOOLTIP_VIEWPORT_MARGIN) {
    top = TOOLTIP_VIEWPORT_MARGIN;
  }

  return { left, top };
}

function tooltipDimensions(kind) {
  switch (kind) {
    case "rsi-point":
      return { width: 220, height: 76 };
    case "price-line":
      return { width: 180, height: 54 };
    case "rsi-zone":
      return { width: 180, height: 64 };
    default:
      return { width: TOOLTIP_WIDTH, height: TOOLTIP_HEIGHT };
  }
}

function resolveRsiZoneKey(rsiValue) {
  if (rsiValue >= 70) {
    return "overbought";
  }
  if (rsiValue <= 30) {
    return "oversold";
  }
  return "neutral";
}

function resolveHoveredStructureLine({ priceSeries, pointY, snapshot }) {
  if (!priceSeries || !snapshot) {
    return null;
  }

  const candidates = [
    { key: "autoSupport", value: snapshot.supportLevel },
    { key: "autoResistance", value: snapshot.resistanceLevel },
  ].filter((item) => item.value != null);

  for (const candidate of candidates) {
    const coordinate = priceSeries.priceToCoordinate(candidate.value);
    if (coordinate != null && Number.isFinite(coordinate) && Math.abs(coordinate - pointY) <= STRUCTURE_LINE_HOVER_THRESHOLD) {
      return candidate;
    }
  }

  return null;
}

function buildTechnicalView(snapshot, t) {
  if (!snapshot) {
    return null;
  }

  let score = 0;
  if (snapshot.trendKey === "uptrend") score += 2;
  if (snapshot.trendKey === "downtrend") score -= 2;
  if (snapshot.maAlignmentKey === "bullish") score += 2;
  if (snapshot.maAlignmentKey === "bearish") score -= 2;
  if (snapshot.momentumKey === "positive") score += 1;
  if (snapshot.momentumKey === "negative") score -= 1;
  if (snapshot.latestSignalTone === "positive") score += 1;
  if (snapshot.latestSignalTone === "negative") score -= 1;
  if (snapshot.rsiValue != null) {
    if (snapshot.rsiValue >= 70) score -= 1;
    else if (snapshot.rsiValue <= 30) score += 1;
    else if (snapshot.rsiValue >= 55) score += 1;
    else if (snapshot.rsiValue <= 45) score -= 1;
  }

  let stateKey = "neutral";
  let tone = "neutral";
  if (score >= 5) {
    stateKey = "strongBullish";
    tone = "positive";
  } else if (score >= 2) {
    stateKey = "weakBullish";
    tone = "positive";
  } else if (score <= -5) {
    stateKey = "strongBearish";
    tone = "negative";
  } else if (score <= -2) {
    stateKey = "weakBearish";
    tone = "negative";
  }

  const reasons = [];

  if (snapshot.rsiValue != null) {
    reasons.push(t(`analysis.chart.techView.reason.rsi.${resolveRsiZoneKey(snapshot.rsiValue)}`));
  }

  if (snapshot.lastClose != null && snapshot.sma20 != null) {
    const distancePct = Math.abs(((snapshot.lastClose - snapshot.sma20) / snapshot.lastClose) * 100);
    if (distancePct <= 0.35) {
      reasons.push(t("analysis.chart.techView.reason.priceNearMa20"));
    } else if (snapshot.lastClose > snapshot.sma20) {
      reasons.push(t("analysis.chart.techView.reason.priceAboveMa20"));
    } else {
      reasons.push(t("analysis.chart.techView.reason.priceBelowMa20"));
    }
  }

  if (snapshot.maAlignmentKey === "bullish") {
    reasons.push(t("analysis.chart.techView.reason.maBullish"));
  } else if (snapshot.maAlignmentKey === "bearish") {
    reasons.push(t("analysis.chart.techView.reason.maBearish"));
  }

  if (snapshot.momentumKey === "positive") {
    reasons.push(t("analysis.chart.techView.reason.momentumPositive"));
  } else if (snapshot.momentumKey === "negative") {
    reasons.push(t("analysis.chart.techView.reason.momentumNegative"));
  } else if (snapshot.momentumKey === "neutral") {
    reasons.push(t("analysis.chart.techView.reason.momentumNeutral"));
  }

  if (snapshot.resistanceDistancePct != null && snapshot.resistanceDistancePct <= 1.5) {
    reasons.push(t("analysis.chart.techView.reason.nearResistance", { value: snapshot.resistanceDistancePct.toFixed(2) }));
  } else if (snapshot.supportDistancePct != null && snapshot.supportDistancePct <= 1.5) {
    reasons.push(t("analysis.chart.techView.reason.nearSupport", { value: snapshot.supportDistancePct.toFixed(2) }));
  }

  if (snapshot.latestSignalTone === "positive") {
    reasons.push(t("analysis.chart.techView.reason.signalPositive"));
  } else if (snapshot.latestSignalTone === "negative") {
    reasons.push(t("analysis.chart.techView.reason.signalNegative"));
  }

  return {
    title: t("analysis.chart.techView.title"),
    label: t(`analysis.chart.techView.state.${stateKey}`),
    tone,
    reasons: reasons.filter(Boolean).slice(0, 4),
  };
}

function resolveAdvancedChartErrorMessage(error, t, isCrypto) {
  const status = Number(error?.response?.status);
  if ([400, 404, 422, 500].includes(status)) {
    return isCrypto ? t("analysis.chart.errors.noCandles") : t("analysis.rangeUnavailable");
  }
  return extractErrorMessage(error, t("analysis.chart.errors.loadFailed"));
}

function toneFromSignal(tone) {
  return tone ?? "neutral";
}

function normalizeSignalDescriptor(value) {
  if (!value) {
    return null;
  }
  if (typeof value === "object" && value.shortLabel && value.text) {
    return value;
  }
  const label = String(value).trim();
  if (!label) {
    return null;
  }
  const formattedLabel = formatSignalLabel(label);
  return {
    shortLabel: formattedLabel,
    text: formattedLabel,
    tone: /buy|bull|up|long/i.test(label)
      ? "positive"
      : /sell|bear|down|short/i.test(label)
        ? "negative"
        : "neutral",
  };
}

function trendTone(direction) {
  switch (direction) {
    case "UPTREND":
      return "positive";
    case "DOWNTREND":
      return "negative";
    default:
      return "neutral";
  }
}

function formatCompactPrice(value) {
  return Number(value).toFixed(Math.abs(value) >= 100 ? 2 : 4);
}

function toFiniteNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function extractIndicatorValue(technicalAnalysis, indicatorName) {
  const normalizedName = String(indicatorName || "").trim().toUpperCase();
  const match = technicalAnalysis?.indicatorValues
    ?.find?.((item) => String(item?.indicator || "").trim().toUpperCase() === normalizedName);
  const numeric = Number(match?.value);
  return Number.isFinite(numeric) ? numeric : null;
}

function toPositiveOverlayNumber(value) {
  const numeric = toFiniteNumber(value);
  return numeric != null && numeric > 0 ? numeric : null;
}

function normalizeChartTime(time) {
  if (typeof time === "number") {
    return time;
  }
  if (time && typeof time === "object" && "year" in time) {
    return Math.floor(Date.UTC(time.year, time.month - 1, time.day) / 1000);
  }
  return null;
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

function formatTooltipDate(epochSeconds) {
  const date = new Date(epochSeconds * 1000);
  return date.toLocaleDateString("tr-TR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

function withAlpha(color, alpha) {
  if (!color) {
    return `rgba(148, 163, 184, ${alpha})`;
  }
  if (color.startsWith("rgba")) {
    return color.replace(/rgba\((.+),\s*[\d.]+\)/, `rgba($1, ${alpha})`);
  }
  if (color.startsWith("rgb")) {
    return color.replace("rgb(", "rgba(").replace(")", `, ${alpha})`);
  }
  return color;
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
  return "cursor";
}

function AiTechPanel({ isAuthenticated, isPremium, aiData, aiLoading, aiError, onRetry, onLogin, t }) {
  if (!isAuthenticated) {
    return (
      <div className="tech-ai-gate">
        <div className="tech-ai-gate-icon"><Lock size={22} strokeWidth={1.5} /></div>
          <p className="tech-ai-gate-title">{t("analysis.chart.aiPanel.premiumTitle")}</p>
          <p className="tech-ai-gate-desc">{t("analysis.chart.aiPanel.authDesc")}</p>
        <button className="tech-ai-gate-btn" onClick={onLogin}>
            {t("analysis.chart.aiPanel.loginCta")}
        </button>
      </div>
    );
  }

  if (!isPremium) {
    return (
      <div className="tech-ai-gate tech-ai-gate--premium">
        <div className="tech-ai-gate-icon tech-ai-gate-icon--premium"><Sparkles size={20} strokeWidth={1.5} /></div>
          <p className="tech-ai-gate-title">{t("analysis.chart.aiPanel.premiumTitle")}</p>
          <p className="tech-ai-gate-desc">{t("analysis.chart.aiPanel.premiumDesc")}</p>
        <a href="/profile" className="tech-ai-gate-btn tech-ai-gate-btn--premium">
            {t("analysis.chart.aiPanel.premiumCta")}
        </a>
        <div className="tech-ai-blur-preview" aria-hidden="true">
          <div className="tech-ai-blur-line" />
          <div className="tech-ai-blur-line tech-ai-blur-line--short" />
          <div className="tech-ai-blur-line" />
          <div className="tech-ai-blur-line tech-ai-blur-line--short" />
        </div>
      </div>
    );
  }

  if (aiLoading) {
    return (
      <div className="tech-ai-skeleton">
        <div className="tech-ai-skel-badge" />
        <div className="tech-ai-skel-line" />
        <div className="tech-ai-skel-line tech-ai-skel-line--short" />
        <div className="tech-ai-skel-sep" />
        <div className="tech-ai-skel-line" />
        <div className="tech-ai-skel-line tech-ai-skel-line--short" />
        <div className="tech-ai-skel-line" />
      </div>
    );
  }

  if (aiError) {
    return (
      <div className="tech-ai-error">
        <p>{aiError}</p>
        <button className="tech-ai-retry-btn" onClick={onRetry}>
              {t("analysis.chart.aiPanel.retry")}
        </button>
      </div>
    );
  }

  if (!aiData) {
    return null;
  }

  const signalTone = aiSignalTone(aiData.signal);
  const riskTone = aiRiskTone(aiData.riskLevel);

  return (
    <div className="tech-ai-content">
      <div className="tech-ai-badges">
        {aiData.signal ? (
          <span className={`tech-ai-badge tech-ai-badge--${signalTone}`}>
            {aiData.signal}
          </span>
        ) : null}
        {aiData.riskLevel ? (
          <span className={`tech-ai-badge tech-ai-badge--${riskTone}`}>
            {aiData.riskLevel} Risk
          </span>
        ) : null}
      </div>

      {aiData.summary ? (
        <div className="tech-ai-section">
            <div className="tech-ai-section-label">{t("analysis.chart.aiPanel.summary")}</div>
          <p className="tech-ai-section-text">{aiData.summary}</p>
        </div>
      ) : null}

      {aiData.trendComment ? (
        <div className="tech-ai-section">
            <div className="tech-ai-section-label">{t("analysis.chart.aiPanel.trendComment")}</div>
          <p className="tech-ai-section-text">{aiData.trendComment}</p>
        </div>
      ) : null}

      {aiData.momentumComment ? (
        <div className="tech-ai-section">
            <div className="tech-ai-section-label">{t("analysis.chart.aiPanel.momentumComment")}</div>
          <p className="tech-ai-section-text">{aiData.momentumComment}</p>
        </div>
      ) : null}

      {aiData.disclaimer ? (
        <p className="tech-ai-disclaimer">{aiData.disclaimer}</p>
      ) : (
          <p className="tech-ai-disclaimer">{t("analysis.chart.aiPanel.defaultDisclaimer")}</p>
      )}

      {aiData.metadata?.provider ? (
        <div className="tech-ai-meta">
          {aiData.metadata.cacheHit ? "⚡ " : "🤖 "}{aiData.metadata.provider}
        </div>
      ) : null}
    </div>
  );
}

function aiSignalTone(signal) {
  switch (String(signal || "").toUpperCase()) {
    case "POSITIVE": return "positive";
    case "NEGATIVE": return "negative";
    case "RISKY": return "warning";
    default: return "neutral";
  }
}

function aiRiskTone(level) {
  switch (String(level || "").toUpperCase()) {
    case "LOW": return "positive";
    case "HIGH": return "negative";
    case "MEDIUM": return "warning";
    default: return "neutral";
  }
}
