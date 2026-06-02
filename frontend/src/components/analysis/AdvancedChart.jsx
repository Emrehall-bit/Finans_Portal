import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CandlestickSeries, createChart, HistogramSeries, LineSeries } from "lightweight-charts";
import {
  Activity,
  BookmarkPlus,
  Check,
  ChevronDown,
  ChevronsUpDown,
  CircleAlert,
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
import {
  deleteDrawing,
  getAdvancedTechnical,
  getDrawings,
  getTechnicalCandles,
  saveDrawing,
  updateDrawing,
} from "../../api/analysisApi";
import { getAiTechnicalAnalysis } from "../../api/aiApi";
import { getMarketHistory } from "../../api/marketApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useAuth } from "../../auth/AuthContext";
import useToast from "../../hooks/useToast";
import { useTheme } from "../../theme/ThemeContext";
import { formatNumber } from "../../utils/formatters";
import {
  AiTechPanel,
  CrosshairTooltip,
  LegendTooltip,
  MetricGroup,
  MetricRow,
  TechnicalViewCard,
} from "./advanced-chart/AdvancedChartParts";
import {
  computeBollingerSeries,
  computeEmaSeries,
  computeSimpleMovingAverageSeries,
  closeToCloseVolatility,
  formatCompactPrice,
  formatTooltipDate,
  normalizeCandles,
  normalizeChartTime,
  normalizeHistoryPoints,
  normalizeLinePoints,
  normalizeSignalDescriptor,
  normalizeTrendDirection,
  resolveMomentumThreshold,
  resolveQuoteLatestPrice,
  resolveRsiThresholds,
  resolveSelectedRangePerformance,
  toFiniteNumber,
  toEpochSeconds,
  toneFromRsi,
  toneFromSignal,
  trendTone,
} from "./advancedChartUtils";

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
  { key: "sma20", label: "MA 20", pane: "price", color: "#f59e0b", children: ["sma20"] },
  { key: "sma50", label: "MA 50", pane: "price", color: "#7c3aed", children: ["sma50"] },
  { key: "ema20", label: "EMA 20", pane: "price", color: "#e11d48", children: ["ema20"] },
  {
    key: "bollinger",
    label: "Bollinger",
    pane: "price",
    color: "#64748b",
    children: ["bollingerUpper", "bollingerMiddle", "bollingerLower"],
  },
  { key: "volumeMa20", label: "Vol MA", pane: "volume", color: "#6b7280", children: ["volumeMa20"] },
];

const DEFAULT_RANGE = "6m";
const DEFAULT_INDICATORS = "SMA7,SMA20,SMA50,RSI14";
const DEFAULT_BAR_SPACING = 11;
const MIN_BAR_SPACING = 1;
const DEFAULT_RIGHT_OFFSET = 2;
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
  dateRange = null,
  activeRange = null,
  rangePresets = null,
  onRangeChange = null,
  onAddToNotes = null,
  noteAdding = false,
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
  const drawingSyncKeyRef = useRef(null);
  const pendingAutoFitRef = useRef(true);
  const dataPointCountRef = useRef(0);
  const syncLockRef = useRef(false);
  const selectedDrawingKeyRef = useRef(null);
  const hoveredDrawingKeyRef = useRef(null);
  const draggingRef = useRef(null);
  const latestDatasetRef = useRef(null);
  const technicalSnapshotRef = useRef(null);
  const warmupBgSeriesRef = useRef(null);
  const toolsDropdownRef = useRef(null);
  const indicatorsRef = useRef(null);
  const activeIndicatorsRef = useRef(new Set());

  const [range, setRange] = useState(() => mapLegacyTimeframeToRange(initialTimeframe));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [drawingSyncVersion, setDrawingSyncVersion] = useState(0);
  const [activeIndicators, setActiveIndicators] = useState(() => new Set());
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
  const [noteDate, setNoteDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [aiData, setAiData] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(null);
  const activeIndicatorItems = useMemo(
    () => INDICATOR_REGISTRY.filter((indicator) => activeIndicators.has(indicator.key)),
    [activeIndicators],
  );
  const resolvedTechnicalSnapshot = useMemo(
    () => mergeTechnicalSnapshot(technicalSnapshot, technicalAnalysis, quote?.instrumentType),
    [technicalSnapshot, technicalAnalysis, quote?.instrumentType],
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
  const technicalView = useMemo(() => buildTechnicalView(effectiveTechnicalSnapshot, t, quote?.instrumentType), [effectiveTechnicalSnapshot, t, quote?.instrumentType]);
  const insufficientOverlays = useMemo(() => {
    if (!effectiveTechnicalSnapshot) return [];
    const names = [];
    if (activeIndicators.has("sma20") && effectiveTechnicalSnapshot.sma20 == null) names.push("MA 20");
    if (activeIndicators.has("sma50") && effectiveTechnicalSnapshot.sma50 == null) names.push("MA 50");
    return names;
  }, [effectiveTechnicalSnapshot, activeIndicators]);

  activeToolRef.current = activeTool;
  drawingsRef.current = drawings;
  trendStartRef.current = trendStart;
  selectedDrawingKeyRef.current = selectedDrawingKey;
  hoveredDrawingKeyRef.current = hoveredDrawingKey;
  technicalSnapshotRef.current = technicalSnapshot;
  activeIndicatorsRef.current = activeIndicators;

  const isCrypto = String(quote?.instrumentType || "").toUpperCase() === "CRYPTO";
  const hasDrawings = Boolean(
    drawings.stopLoss ||
    drawings.takeProfit ||
    drawings.horizontalLines.length ||
    drawings.trendLines.length,
  );
  const rangeDates = useMemo(
    () => dateRange ?? buildDateRange(range),
    [dateRange, range],
  );
  const drawingTimeframe = activeRange ?? range;
  const volumeVisible = Boolean(technicalSnapshot?.volumeVisible);
  const hasVolumeMaData = (technicalSnapshot?.volumeDataCount ?? 0) >= 20;

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

  const persistDrawing = useCallback((drawing) => {
    if (!isAuthenticated || !instrumentCode || !drawing) {
      return;
    }

    const payload = serializeDrawingForApi(drawing, drawingTimeframe);
    if (!payload) {
      return;
    }

    const backendId = getDrawingBackendId(drawing);
    const request = backendId
      ? updateDrawing(backendId, payload)
      : saveDrawing(instrumentCode, payload);

    request
      .then((saved) => {
        const savedId = getDrawingBackendId(saved);
        if (!savedId) {
          return;
        }
        setDrawings((current) => attachBackendIdToDrawing(current, drawing, savedId));
      })
      .catch((err) => {
        console.warn("Çizim kaydedilemedi:", err?.response?.status ?? err?.message);
        showToast("error", "Çizim kaydedilemedi");
      });
  }, [drawingTimeframe, instrumentCode, isAuthenticated, showToast]);

  const deletePersistedDrawing = useCallback((drawing) => {
    const backendId = getDrawingBackendId(drawing);
    if (!backendId || !isAuthenticated) {
      return;
    }

    deleteDrawing(backendId).catch((err) => {
      console.warn("Çizim silinemedi:", err?.response?.status ?? err?.message);
      showToast("error", "Çizim silinemedi");
    });
  }, [isAuthenticated, showToast]);

  const renderPersistedDrawings = useCallback((items) => {
    const priceSeries = priceSeriesRef.current;
    const priceChart = priceChartRef.current;
    if (!priceSeries || !priceChart) {
      return;
    }

    const nextDrawings = {
      stopLoss: null,
      takeProfit: null,
      horizontalLines: [],
      trendLines: [],
    };

    (Array.isArray(items) ? items : []).forEach((item) => {
      const drawing = buildChartDrawingFromApi(item, priceSeries, priceChart, t);
      if (!drawing) {
        return;
      }

      if (drawing.kind === "stopLoss") {
        nextDrawings.stopLoss = drawing;
        return;
      }
      if (drawing.kind === "takeProfit") {
        nextDrawings.takeProfit = drawing;
        return;
      }
      if (drawing.kind === "trend") {
        nextDrawings.trendLines.push(drawing);
        return;
      }
      nextDrawings.horizontalLines.push(drawing);
    });

    setDrawings(nextDrawings);
    setSelectedDrawingKey(null);
    setHoveredDrawingKey(null);
  }, [t]);

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
        title: "",
      });
    }

    if (summary.resistanceLevel != null) {
      structureLineRefs.current.resistance = priceSeries.createPriceLine({
        price: summary.resistanceLevel,
        color: "rgba(239, 68, 68, 0.5)",
        lineWidth: 1,
        lineStyle: 0,
        axisLabelVisible: false,
        title: "",
      });
    }
  }, [clearStructurePriceLines, showStructureLines]);


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
      indicators: buildTooltipIndicators(dataset, time, activeIndicatorsRef.current),
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
        zoneLabel: t(`analysis.chart.rsiZone.${resolveRsiZoneKey(lineRsi, quote?.instrumentType)}`),
      });
      return;
    }

    // Warm-up bölgesi: fiyat verisi var ama RSI henüz hesaplanamamış
    if (time != null && row != null && lineRsi == null) {
      const { left, top } = resolveTooltipPosition(rect, pointX, pointY, "rsi-zone");
      setTooltipModel({ kind: "rsi-zone", left, top, title: t("analysis.chart.rsiNoData") });
      return;
    }

    if (hoveredRsi == null) {
      setTooltipModel(null);
      return;
    }

    setTooltipModel(null);
  }, [quote?.instrumentType, t]);

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

      const nonNegativeScale = (original) => {
        const info = original();
        if (!info) return null;
        return { ...info, priceRange: { minValue: Math.max(0, info.priceRange.minValue), maxValue: info.priceRange.maxValue } };
      };
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
            autoscaleInfoProvider: nonNegativeScale,
          })
        : priceChart.addSeries(LineSeries, {
            color: "#2f6bff",
            lineWidth: 3,
            priceLineVisible: false,
            lastValueVisible: true,
            autoscaleInfoProvider: nonNegativeScale,
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

    // Warm-up arka plan — RSI line'dan önce eklenmeli (z-index sırası)
    if (!warmupBgSeriesRef.current) {
      warmupBgSeriesRef.current = rsiChart.addSeries(HistogramSeries, {
        color: "rgba(148, 163, 184, 0.10)",
        priceLineVisible: false,
        lastValueVisible: false,
        autoscaleInfoProvider: () => ({ priceRange: { minValue: 0, maxValue: 100 } }),
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
    const horzGrid = withAlpha(chartTheme.grid, 0.24);
    const vertGrid = withAlpha(chartTheme.grid, 0.12);
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
          vertLines: { color: vertGrid },
          horzLines: { color: horzGrid },
        },
        crosshair: {
          mode: 1,
          vertLine: { color: withAlpha(chartTheme.axis, 0.28), labelBackgroundColor: withAlpha(chartTheme.axis, 0.12) },
          horzLine: { color: withAlpha(chartTheme.axis, 0.2), labelBackgroundColor: withAlpha(chartTheme.axis, 0.12) },
        },
        timeScale: commonTimeScale,
        handleScroll: { mouseWheel: false, pressedMouseMove: true },
        handleScale: { mouseWheel: false, axisPressedMouseMove: { time: false, price: true } },
        ...extraOptions,
      });
    });

    if (rsiChartRef.current) {
      rsiChartRef.current.priceScale("right").applyOptions({
        visible: false,
        autoScale: true,
      });
    }

    if (warmupBgSeriesRef.current) {
      warmupBgSeriesRef.current.applyOptions({ color: withAlpha(chartTheme.grid, 0.18) });
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
              lineStyle: seriesLineStyle(seriesKey),
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

  const syncStructurePriceLinesRef = useRef(null);
  syncStructurePriceLinesRef.current = syncStructurePriceLines;

  useEffect(() => {
    if (hasVolumeMaData || !activeIndicators.has("volumeMa20")) {
      return;
    }
    setActiveIndicators((current) => {
      if (!current.has("volumeMa20")) {
        return current;
      }
      const next = new Set(current);
      next.delete("volumeMa20");
      return next;
    });
  }, [activeIndicators, hasVolumeMaData]);

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
    const backendId = getDrawingBackendId(existing);
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
      lineStyle: 0,
      priceLine,
      backendId,
      },
    }));
    setSelectedDrawingKey(id);
    persistDrawing({
      id,
      kind: id,
      label: title,
      color,
      price,
      lineStyle: 0,
      backendId,
    });
  }, [persistDrawing]);

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
    const backendId = getDrawingBackendId(existing);
    if (existing?.priceLine) {
      try { priceSeries.removePriceLine(existing.priceLine); } catch { /* noop */ }
    }

    const id = existing?.id ?? `${tool}-${Date.now()}`;
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
          lineStyle: config.lineStyle,
          priceLine,
          backendId,
        },
      ],
    }));
    setSelectedDrawingKey(id);
    persistDrawing({
      id,
      kind: tool,
      label: config.label,
      color: config.color,
      price,
      lineStyle: config.lineStyle,
      backendId,
    });
  }, [persistDrawing, t]);

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
  }, []);

  const removeDrawingByKey = useCallback((drawingKey) => {
    if (!drawingKey) {
      return;
    }

    if (drawingKey === "stopLoss") {
      deletePersistedDrawing(drawingsRef.current.stopLoss);
      if (drawingsRef.current.stopLoss?.priceLine) {
        try { priceSeriesRef.current?.removePriceLine(drawingsRef.current.stopLoss.priceLine); } catch { /* noop */ }
      }
      setDrawings((current) => ({ ...current, stopLoss: null }));
      setSelectedDrawingKey(null);
      return;
    }

    if (drawingKey === "takeProfit") {
      deletePersistedDrawing(drawingsRef.current.takeProfit);
      if (drawingsRef.current.takeProfit?.priceLine) {
        try { priceSeriesRef.current?.removePriceLine(drawingsRef.current.takeProfit.priceLine); } catch { /* noop */ }
      }
      setDrawings((current) => ({ ...current, takeProfit: null }));
      setSelectedDrawingKey(null);
      return;
    }

    const horizontalLine = drawingsRef.current.horizontalLines.find((item) => item.id === drawingKey);
    if (horizontalLine) {
      deletePersistedDrawing(horizontalLine);
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
      deletePersistedDrawing(trendLine);
      try { priceChartRef.current?.removeSeries(trendLine.series); } catch { /* noop */ }
      setDrawings((current) => ({
        ...current,
        trendLines: current.trendLines.filter((item) => item.id !== drawingKey),
      }));
      setSelectedDrawingKey(null);
    }
  }, [deletePersistedDrawing]);

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

    const priceContainer = priceContainerRef.current;
    const volumeContainer = volumeContainerRef.current;
    const rsiContainer = rsiContainerRef.current;

    const priceChart = createChart(priceContainer, {
      width: Math.max(priceContainer.clientWidth, 1),
      height: PRICE_CHART_HEIGHT,
    });
    const volumeChart = createChart(volumeContainer, {
      width: Math.max(volumeContainer.clientWidth, 1),
      height: VOLUME_CHART_HEIGHT,
    });
    const rsiChart = createChart(rsiContainer, {
      width: Math.max(rsiContainer.clientWidth, 1),
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
        const drawing = {
          id,
          kind: "trend",
          label: t("analysis.chart.drawing.trend"),
          color: "#8b5cf6",
          series: trendSeries,
          data: lineData,
        };
        setDrawings((current) => ({
          ...current,
          trendLines: [...current.trendLines, drawing],
        }));
        setSelectedDrawingKey(id);
        setTrendStart(null);
        persistDrawing(drawing);
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
        lastPrice: price,
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
      draggingRef.current.lastPrice = nextPrice;
      updateHorizontalDrawingPrice(draggingRef.current.drawingKey, nextPrice);
    };

    const handleMouseUp = () => {
      if (draggingRef.current?.drawingKey) {
        const updatedDrawing = findDrawingByKey(drawingsRef.current, draggingRef.current.drawingKey);
        if (updatedDrawing) {
          persistDrawing({
            ...updatedDrawing,
            price: draggingRef.current.lastPrice ?? updatedDrawing.price,
          });
        }
      }
      draggingRef.current = null;
    };

    const handleResize = () => {
      const priceWidth = Math.max(priceContainer.clientWidth, 1);
      const volumeWidth = Math.max(volumeContainer.clientWidth, 1);
      const rsiWidth = Math.max(rsiContainer.clientWidth, 1);
      priceChart.applyOptions({ width: priceWidth });
      volumeChart.applyOptions({ width: volumeWidth });
      rsiChart.applyOptions({ width: rsiWidth });
    };

    const resizeObserver = typeof ResizeObserver !== "undefined"
      ? new ResizeObserver(() => {
          handleResize();
        })
      : null;

    resizeObserver?.observe(priceContainer);
    resizeObserver?.observe(volumeContainer);
    resizeObserver?.observe(rsiContainer);

    requestAnimationFrame(() => {
      handleResize();
    });

    priceContainer.addEventListener("mousedown", handleMouseDown);
    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);
    window.addEventListener("resize", handleResize);

    return () => {
      priceContainer.removeEventListener("mousedown", handleMouseDown);
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
      warmupBgSeriesRef.current = null;
      overlaySeriesRefs.current = {};
    };
  }, [
    addPriceLine,
    addHorizontalDrawing,
    applyThemeOptions,
    ensureCoreSeries,
    persistDrawing,
    syncVisibleRangeAcrossCharts,
    t,
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
          ? await loadCryptoData(instrumentCode, range, t, quote)
          : await loadLineData(instrumentCode, rangeDates, t, quote, activeRange ?? range);

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
        if (warmupBgSeriesRef.current) {
          warmupBgSeriesRef.current.setData(dataset.warmupRsiData ?? []);
        }
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
        syncStructurePriceLinesRef.current(dataset.summary);
        syncIndicatorSeriesRef.current(dataset);
        const syncKey = `${instrumentCode}:::${activeRange ?? range}`;
        if (drawingSyncKeyRef.current !== syncKey) {
          drawingSyncKeyRef.current = syncKey;
          setDrawingSyncVersion((value) => value + 1);
        }

        if (pendingAutoFitRef.current) {
          priceChartRef.current?.applyOptions({ timeScale: { barSpacing: rangeToBarSpacing(activeRange ?? range) } });
          priceChartRef.current?.timeScale().fitContent();
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
    activeRange,
    clearAllDrawings,
    ensureCoreSeries,
    initialHighlightTool,
    instrumentCode,
    isCrypto,
    presetPrice,
    quote?.currentPrice,
    quote?.instrumentType,
    quote?.last,
    quote?.latestPrice,
    quote?.price,
    quote?.sellRate,
    quote?.source,
    range,
    rangeDates,
    reloadToken,
    clearStructurePriceLines,
    t,
  ]);

  useEffect(() => {
    if (!drawingSyncVersion || !instrumentCode || !priceSeriesRef.current) {
      return undefined;
    }

    let cancelled = false;
    clearAllDrawings();

    if (!isAuthenticated) {
      return undefined;
    }

    async function loadPersistedDrawings() {
      try {
        const savedDrawings = await getDrawings(instrumentCode, drawingTimeframe);
        if (!cancelled) {
          renderPersistedDrawings(savedDrawings);
        }
      } catch (err) {
        if (!cancelled) {
          console.warn("Çizimler yüklenemedi:", err?.response?.status ?? err?.message);
          showToast("error", "Çizimler yüklenemedi");
        }
      }
    }

    loadPersistedDrawings();
    return () => {
      cancelled = true;
    };
  }, [
    clearAllDrawings,
    drawingSyncVersion,
    drawingTimeframe,
    instrumentCode,
    isAuthenticated,
    renderPersistedDrawings,
    showToast,
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
    if (indicatorKey === "volumeMa20" && !hasVolumeMaData) {
      return;
    }
    setActiveIndicators((current) => {
      const next = new Set(current);
      if (next.has(indicatorKey)) {
        next.delete(indicatorKey);
      } else {
        next.add(indicatorKey);
      }
      return next;
    });
  }, [hasVolumeMaData]);

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
          {dateRange && rangePresets && onRangeChange ? (
            <div className="chart-timeframes" role="group" aria-label="Range">
              {rangePresets.map((preset) => (
                <button
                  type="button"
                  key={preset.key}
                  className={`chart-tf-btn${activeRange === preset.key ? " active" : ""}`}
                  onClick={() => onRangeChange(preset)}
                >
                  {preset.key}
                </button>
              ))}
            </div>
          ) : !dateRange ? (
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
          ) : null}
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
                  {INDICATOR_REGISTRY.map((indicator) => {
                    const disabled = indicator.key === "volumeMa20" && !hasVolumeMaData;
                    return (
                    <label
                      key={indicator.key}
                      className={`indicators-item${disabled ? " is-disabled" : ""}`}
                      title={disabled ? "Hacim verisi yok" : undefined}
                    >
                      <input
                        type="checkbox"
                        checked={activeIndicators.has(indicator.key)}
                        disabled={disabled}
                        onChange={() => toggleIndicator(indicator.key)}
                      />
                      <span className="indicators-item-dot" style={{ "--indicator-color": indicator.color }} />
                      <span className="indicators-item-label">{indicator.label}</span>
                    </label>
                    );
                  })}
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
              <div className="price-scale-drag-hint" aria-hidden="true">
                <ChevronsUpDown size={14} strokeWidth={1.8} />
              </div>
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
                  <button
                    type="button"
                    className="rsi-info-btn"
                    onMouseEnter={(event) => showLegendTooltip(event, t("analysis.chart.rsiDisclaimer"))}
                    onMouseLeave={hideLegendTooltip}
                    aria-label={t("analysis.chart.rsiDisclaimer")}
                  >
                    ⓘ
                  </button>
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
          <div className="advanced-tech-metrics">
            <MetricGroup title={t("analysis.chart.techPanel.groupMomentum")}>
              <MetricRow
                label="RSI 14"
                value={effectiveTechnicalSnapshot?.rsiValue != null ? effectiveTechnicalSnapshot.rsiValue.toFixed(2) : "-"}
                tone={toneFromRsi(effectiveTechnicalSnapshot?.rsiValue, quote?.instrumentType)}
              />
              <MetricRow
                label={t("analysis.chart.techPanel.momentum")}
                value={effectiveTechnicalSnapshot?.momentumKey ? t(`analysis.chart.techPanel.momentumState.${effectiveTechnicalSnapshot.momentumKey}`) : "-"}
                tone={effectiveTechnicalSnapshot?.momentumTone ?? "neutral"}
              />
              <MetricRow
                label={t("analysis.chart.techPanel.latestSignal")}
                value={effectiveTechnicalSnapshot?.signalKey
                  ? t(`analysis.chart.signal.${effectiveTechnicalSnapshot.signalKey}.short`)
                  : (effectiveTechnicalSnapshot?.rawSignalLabel ?? "-")}
                tone={toneFromSignal(effectiveTechnicalSnapshot?.latestSignalTone)}
              />
            </MetricGroup>

            <MetricGroup title={t("analysis.chart.techPanel.groupLevels")}>
              <MetricRow
                label={effectiveTechnicalSnapshot?.levelMode === "closeBand" ? t("analysis.chart.techPanel.rangeLow") : t("analysis.chart.techPanel.support")}
                value={effectiveTechnicalSnapshot?.supportLevel != null ? formatNumber(effectiveTechnicalSnapshot.supportLevel, 2) : "-"}
                detail={effectiveTechnicalSnapshot?.supportDistancePct != null ? `${effectiveTechnicalSnapshot.supportDistancePct.toFixed(2)}% ${t("analysis.chart.techPanel.fromPrice")}` : null}
              />
              <MetricRow
                label={effectiveTechnicalSnapshot?.levelMode === "closeBand" ? t("analysis.chart.techPanel.rangeHigh") : t("analysis.chart.techPanel.resistance")}
                value={effectiveTechnicalSnapshot?.resistanceLevel != null ? formatNumber(effectiveTechnicalSnapshot.resistanceLevel, 2) : "-"}
                detail={effectiveTechnicalSnapshot?.resistanceDistancePct != null ? `${effectiveTechnicalSnapshot.resistanceDistancePct.toFixed(2)}% ${t("analysis.chart.techPanel.fromPrice")}` : null}
              />
            </MetricGroup>

            <MetricGroup title={t("analysis.chart.techPanel.groupTrend")}>
              <MetricRow
                label={t("analysis.chart.techPanel.trend")}
                value={effectiveTechnicalSnapshot?.trendKey ? t(`analysis.chart.trend.${effectiveTechnicalSnapshot.trendKey}`) : "-"}
                tone={toneFromSignal(effectiveTechnicalSnapshot?.trendTone)}
              />
              <MetricRow
                label={t("analysis.chart.techPanel.volatility")}
                value={effectiveTechnicalSnapshot?.volatilityKey ? t(`analysis.chart.volatilityLevel.${effectiveTechnicalSnapshot.volatilityKey}`) : "-"}
                tone={effectiveTechnicalSnapshot?.volatilityTone ?? "neutral"}
              />
              <MetricRow
                label={t("analysis.chart.techPanel.maAlignment")}
                value={effectiveTechnicalSnapshot?.maAlignmentKey ? t(`analysis.chart.maAlign.${effectiveTechnicalSnapshot.maAlignmentKey}`) : "-"}
                tone={effectiveTechnicalSnapshot?.maAlignmentTone ?? "neutral"}
              />
            </MetricGroup>
            {insufficientOverlays.length > 0 ? (
              <MetricRow
                label={t("analysis.chart.techPanel.insufficientData")}
                value={insufficientOverlays.join(", ")}
                tone="neutral"
              />
            ) : null}
          </div>
          {onAddToNotes ? (
            <div className="advanced-note-add-wrap">
              <div className="advanced-note-date-row">
                <span className="advanced-note-date-label">Analiz tarihi:</span>
                <input
                  type="date"
                  className="advanced-note-date-input"
                  value={noteDate}
                  max={new Date().toISOString().slice(0, 10)}
                  onChange={(e) => setNoteDate(e.target.value)}
                />
              </div>
              <button
                type="button"
                className="simple-tech-add-note-btn"
                disabled={noteAdding}
                onClick={() => {
                  const dataset = latestDatasetRef.current;
                  const hist = resolveHistoricalValues(noteDate, dataset);
                  onAddToNotes(buildAdvancedNoteContent({
                    instrumentCode,
                    quote,
                    activeRange,
                    toDate: noteDate,
                    snapshot: effectiveTechnicalSnapshot,
                    viewLabel: technicalView?.label,
                    drawings,
                    activeIndicators,
                    historicalValues: hist,
                  }));
                }}
              >
                <BookmarkPlus size={15} strokeWidth={2.2} />
                <span>{noteAdding ? "Ekleniyor..." : "Notlara Ekle"}</span>
              </button>
            </div>
          ) : null}
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

async function loadCryptoData(symbol, range, t, quote) {
  const candles = alignAdvancedRowsWithQuote(
    normalizeCandles(await getTechnicalCandles(symbol, { range, interval: "1d" })),
    quote,
    "candlestick",
  );
  if (!candles.length) {
    throw new Error(t("analysis.chart.errors.noCandles"));
  }

  const closes = candles.map((candle) => candle.close);
  const volumes = candles.map((candle) => candle.volume ?? null);
  const validVolumeCandles = candles.filter((candle) => isValidVolume(candle.volume));
  const overlayData = buildOverlayData(candles, closes, volumes);
  const infoByTime = buildInfoByTime(candles);
  const rsiData = candles
    .filter((candle) => candle.rsi14 != null)
    .map((candle) => ({ time: candle.time, value: candle.rsi14 }));
  const warmupRsiData = candles
    .filter((candle) => candle.rsi14 == null)
    .map((candle) => ({ time: candle.time, value: 100 }));
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
      .filter((candle) => isValidVolume(candle.volume))
      .map((candle) => ({
        time: candle.time,
        value: candle.volume,
        color: candle.close >= candle.open ? "rgba(34, 197, 94, 0.72)" : "rgba(239, 68, 68, 0.72)",
      })),
    rsiData,
    warmupRsiData,
    overlayData,
    infoByTime,
    summary: buildTechnicalSummary({
      rows: candles,
      latestRow: candles.at(-1),
      trendDirection: deriveTrendDirection(candles),
      overlayData,
      mode: "candlestick",
      volumeVisible: true,
      volumeDataCount: validVolumeCandles.length,
      rsiStats,
      rangeKey: range,
      instrumentType: "CRYPTO",
    }),
  };
}

async function loadLineData(symbol, rangeDates, t, quote, rangeKey) {
  let analysis = null;
  let analysisError = null;

  try {
    analysis = await getAdvancedTechnical(symbol, {
      from: rangeDates.from,
      to: rangeDates.to,
      indicators: DEFAULT_INDICATORS,
      ...(quote?.instrumentType != null && { instrumentType: quote.instrumentType }),
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
  points = alignAdvancedRowsWithQuote(points, quote, "line");

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
  const warmupRsiData = points
    .filter((point) => point.rsi14 == null)
    .map((point) => ({ time: point.time, value: 100 }));
  const rsiStats = buildRsiStats(rsiData);

  return {
    mode: "line",
    priceData: points.map((point) => ({
      time: point.time,
      value: point.close,
    })),
    volumeData: [],
    rsiData,
    warmupRsiData,
    overlayData,
    infoByTime,
    summary: buildTechnicalSummary({
      rows: points,
      latestRow: points.at(-1),
      trendDirection: normalizeTrendDirection(analysis?.trendDirection) ?? deriveTrendDirection(points),
      overlayData,
      latestSignal: analysis?.signals?.[0]?.label ?? analysis?.signals?.[0]?.signalType ?? null,
      mode: "line",
      volumeVisible: false,
      volumeDataCount: 0,
      rsiStats,
      rangeKey,
      instrumentType: quote?.instrumentType,
    }),
  };
}

function alignAdvancedRowsWithQuote(rows, quote, mode) {
  const quotePrice = resolveQuoteLatestPrice(quote);
  if (quotePrice == null || !Array.isArray(rows) || rows.length === 0) {
    return rows;
  }

  const today = toEpochSeconds(new Date().toISOString().slice(0, 10));
  const lastRow = rows.at(-1);
  if (!lastRow?.time) {
    return rows;
  }

  if (lastRow.time >= today) {
    const updatedRow = mode === "candlestick"
      ? {
          ...lastRow,
          close: quotePrice,
          high: Math.max(toFiniteNumber(lastRow.high) ?? quotePrice, quotePrice),
          low: Math.min(toFiniteNumber(lastRow.low) ?? quotePrice, quotePrice),
        }
      : {
          ...lastRow,
          close: quotePrice,
        };
    return [...rows.slice(0, -1), updatedRow];
  }

  const appendedRow = mode === "candlestick"
    ? {
        time: today,
        dateLabel: formatTooltipDate(today),
        open: quotePrice,
        high: quotePrice,
        low: quotePrice,
        close: quotePrice,
        volume: null,
        sma7: null,
        sma20: null,
        sma50: null,
        rsi14: null,
        changePct: derivePercentChange(lastRow.close, quotePrice),
      }
    : {
        time: today,
        dateLabel: formatTooltipDate(today),
        open: null,
        high: null,
        low: null,
        close: quotePrice,
        volume: null,
        sma7: null,
        sma20: null,
        sma50: null,
        rsi14: null,
        changePct: derivePercentChange(lastRow.close, quotePrice),
      };

  return [...rows, appendedRow];
}

function derivePercentChange(from, to) {
  const start = toFiniteNumber(from);
  const end = toFiniteNumber(to);
  if (start == null || end == null || start === 0) {
    return null;
  }
  return ((end - start) / Math.abs(start)) * 100;
}

function buildOverlayData(rows, closes, volumes) {
  const timeRows = rows.map((row) => row.time);
  const sma7 = computeSimpleMovingAverageSeries(closes, 7);
  const sma20 = computeSimpleMovingAverageSeries(closes, 20);
  const sma50 = computeSimpleMovingAverageSeries(closes, 50);
  const ema20 = computeEmaSeries(closes, 20);
  const volumeMa20 = computeSimpleMovingAverageSeries(volumes, 20);
  const bollinger = computeBollingerSeries(closes, 20, 2);

  return {
    sma7: mapSeriesWithFallback(rows, "sma7", timeRows, sma7),
    sma20: mapSeriesWithFallback(rows, "sma20", timeRows, sma20),
    sma50: mapSeriesWithFallback(rows, "sma50", timeRows, sma50),
    ema20: mapSeriesFromValues(timeRows, ema20),
    bollingerUpper: mapSeriesFromValues(timeRows, bollinger.upper),
    bollingerMiddle: mapSeriesFromValues(timeRows, bollinger.middle),
    bollingerLower: mapSeriesFromValues(timeRows, bollinger.lower),
    volumeMa20: mapSeriesFromValues(timeRows, volumeMa20),
  };
}

function mapSeriesWithFallback(rows, key, times, fallbackValues) {
  return rows
    .map((row, index) => ({
      time: times[index],
      value: row[key] ?? fallbackValues[index],
    }))
    .filter((entry) => entry.time != null && entry.value != null);
}

function mapSeriesFromValues(times, values) {
  return times
    .map((time, index) => ({ time, value: values[index] }))
    .filter((entry) => entry.value != null);
}

function buildInfoByTime(rows) {
  return new Map(rows.map((row) => [row.time, row]));
}

function buildTechnicalSummary({
  rows,
  latestRow,
  trendDirection,
  latestSignal,
  mode,
  volumeVisible,
  volumeDataCount,
  rsiStats,
  rangeKey,
  instrumentType,
}) {
  const latestRsi = latestRow?.rsi14 ?? null;
  const maAlignment = deriveMaAlignment(latestRow);
  const volatility = deriveVolatility(rows, rangeKey);
  const selectedRangePerformance = resolveSelectedRangePerformance(rows, instrumentType, rangeKey);
  const momentum = deriveMomentum(selectedRangePerformance.totalChangePct, instrumentType);
  const supportResistance = deriveSupportResistance(rows, latestRow);
  const rawSignal = normalizeSignalDescriptor(latestSignal);
  const derivedSignal = deriveLatestSignal({ latestRow, trendDirection, maAlignment, volatility, instrumentType });
  const signalTone = rawSignal?.tone ?? derivedSignal.tone;

  return {
    rsiValue: latestRsi,
    rsiRegimeKey: latestRsi == null ? null : resolveRsiZoneKey(latestRsi, instrumentType),
    trendKey: (trendDirection ?? "SIDEWAYS").toLowerCase(),
    trendTone: trendTone(trendDirection),
    selectedRangeStateKey: selectedRangePerformance.stateKey,
    selectedRangeTrendTone: selectedRangePerformance.tone,
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
    levelMode: supportResistance.levelMode,
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

function deriveMomentum(changePct, instrumentType) {
  const value = toFiniteNumber(changePct);
  if (value == null) {
    return { key: "awaiting", tone: "neutral", value: null };
  }
  const threshold = resolveMomentumThreshold(instrumentType);
  if (value >= threshold) {
    return { key: "positive", tone: "positive", value };
  }
  if (value <= -threshold) {
    return { key: "negative", tone: "negative", value };
  }
  return { key: "neutral", tone: "neutral", value };
}

function deriveSupportResistance(rows, latestRow) {
  const normalized = Array.isArray(rows) ? rows : [];
  const latestClose = toPositivePrice(latestRow?.close);
  if (!normalized.length || latestClose == null) {
    return { support: null, supportDistancePct: null, resistance: null, resistanceDistancePct: null, levelMode: "none" };
  }

  const hasOhlc = normalized.some((row) => toPositivePrice(row?.high) != null && toPositivePrice(row?.low) != null);
  const closes = normalized.map((row) => toPositivePrice(row?.close)).filter((value) => value != null);
  const support = hasOhlc
    ? nearestSwingLevel(normalized, latestClose, "low")
    : Math.min(...closes);
  const resistance = hasOhlc
    ? nearestSwingLevel(normalized, latestClose, "high")
    : Math.max(...closes);

  return {
    support: toPositivePrice(support),
    supportDistancePct: toPositivePrice(support) != null ? Math.abs(((latestClose - support) / latestClose) * 100) : null,
    resistance: toPositivePrice(resistance),
    resistanceDistancePct: toPositivePrice(resistance) != null ? Math.abs(((resistance - latestClose) / latestClose) * 100) : null,
    levelMode: hasOhlc ? "swing" : "closeBand",
  };
}

function nearestSwingLevel(rows, latestClose, key) {
  const values = rows.map((row) => toPositivePrice(row?.[key])).filter((value) => value != null);
  const swings = [];
  for (let i = 2; i < rows.length - 2; i++) {
    const value = toPositivePrice(rows[i]?.[key]);
    if (value == null) continue;
    const neighbors = [rows[i - 2], rows[i - 1], rows[i + 1], rows[i + 2]].map((row) => toPositivePrice(row?.[key]));
    const isSwing = key === "low"
      ? neighbors.every((neighbor) => neighbor != null && value <= neighbor)
      : neighbors.every((neighbor) => neighbor != null && value >= neighbor);
    if (isSwing) swings.push(value);
  }
  const candidates = key === "low"
    ? swings.filter((value) => value <= latestClose)
    : swings.filter((value) => value >= latestClose);
  if (candidates.length) {
    return candidates.reduce((best, value) => Math.abs(value - latestClose) < Math.abs(best - latestClose) ? value : best);
  }
  return key === "low" ? Math.min(...values) : Math.max(...values);
}

function toPositivePrice(value) {
  const numeric = toFiniteNumber(value);
  return numeric != null && numeric > 0 ? numeric : null;
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

function deriveMaAlignment(latestRow) {
  const sma20 = latestRow?.sma20;
  const sma50 = latestRow?.sma50;
  if (sma20 == null || sma50 == null) {
    return { key: "limited", tone: "neutral" };
  }
  const spreadPct = ((sma20 - sma50) / sma50) * 100;
  if (spreadPct > 0.20) return { key: "bullish", tone: "positive" };
  if (spreadPct < -0.20) return { key: "bearish", tone: "negative" };
  return { key: "mixed", tone: "neutral" };
}

function deriveVolatility(rows, rangeKey) {
  const closes = (Array.isArray(rows) ? rows : [])
    .map((row) => toFiniteNumber(row?.close))
    .filter((value) => value != null);
  const volatilityPct = closeToCloseVolatility(closes, rangeKey);
  if (volatilityPct == null) {
    return { key: "awaiting", tone: "neutral", summaryKey: "awaiting" };
  }

  if (volatilityPct >= 2) {
    return { key: "high", tone: "warning", summaryKey: "high" };
  }
  if (volatilityPct >= 0.75) {
    return { key: "medium", tone: "neutral", summaryKey: "medium" };
  }
  return { key: "low", tone: "positive", summaryKey: "low" };
}

function mergeTechnicalSnapshot(snapshot, technicalAnalysis, instrumentType) {
  const baseSnapshot = snapshot ?? null;
  const backendRsi = extractIndicatorValue(technicalAnalysis, "RSI14");
  const backendTrendDirection = normalizeTrendDirection(technicalAnalysis?.trendDirection);
  const backendSignal = normalizeSignalDescriptor(Array.isArray(technicalAnalysis?.signals) ? technicalAnalysis.signals[0] : null);
  const resolvedRsi = baseSnapshot?.rsiValue ?? backendRsi ?? null;

  return {
    ...(baseSnapshot ?? {}),
    rsiValue: resolvedRsi,
    rsiRegimeKey: resolvedRsi == null ? (baseSnapshot?.rsiRegimeKey ?? null) : resolveRsiZoneKey(resolvedRsi, instrumentType),
    trendKey: backendTrendDirection ? backendTrendDirection.toLowerCase() : (baseSnapshot?.trendKey ?? null),
    trendTone: backendTrendDirection ? trendTone(backendTrendDirection) : (baseSnapshot?.trendTone ?? "neutral"),
    selectedRangeStateKey: baseSnapshot?.selectedRangeStateKey ?? "neutral",
    selectedRangeTrendTone: baseSnapshot?.selectedRangeTrendTone ?? "neutral",
    signalKey: backendSignal ? null : (baseSnapshot?.signalKey ?? null),
    rawSignalLabel: backendSignal?.shortLabel ?? baseSnapshot?.rawSignalLabel ?? null,
    rawSignalText: backendSignal?.text ?? baseSnapshot?.rawSignalText ?? null,
    latestSignalTone: backendSignal?.tone ?? baseSnapshot?.latestSignalTone ?? "neutral",
  };
}

function deriveLatestSignal({ latestRow, trendDirection, maAlignment, volatility, instrumentType }) {
  const rsiThresholds = resolveRsiThresholds(instrumentType);
  if (latestRow?.rsi14 != null && latestRow.rsi14 <= rsiThresholds.oversold) {
    return { key: "oversoldRisk", tone: "warning" };
  }
  if (latestRow?.rsi14 != null && latestRow.rsi14 >= rsiThresholds.overbought) {
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
    levelMode: manualSupportLine || manualResistanceLine ? "swing" : (baseSnapshot.levelMode ?? "swing"),
  };
}

function seriesColor(key) {
  return INDICATOR_REGISTRY.find((indicator) => indicator.children.includes(key))?.color
    ?? INDICATOR_REGISTRY.find((indicator) => indicator.key === "bollinger")?.color
    ?? "#64748b";
}

function seriesLineStyle(key) {
  if (key === "bollingerUpper" || key === "bollingerLower") {
    return 2;
  }
  return 0;
}

function isValidVolume(value) {
  const numeric = toFiniteNumber(value);
  return numeric != null && numeric > 0;
}

function buildTooltipIndicators(dataset, time, activeIndicators) {
  if (!dataset?.overlayData || !activeIndicators?.size) {
    return [];
  }

  return INDICATOR_REGISTRY
    .filter((indicator) => activeIndicators.has(indicator.key))
    .flatMap((indicator) => indicator.children.map((seriesKey) => ({
      key: seriesKey,
      label: tooltipIndicatorLabel(indicator, seriesKey),
      color: indicator.color,
      value: findOverlayValueAtTime(dataset.overlayData[seriesKey], time),
      digits: indicator.pane === "volume" ? 0 : 2,
    })));
}

function findOverlayValueAtTime(series, time) {
  if (!Array.isArray(series) || time == null) {
    return null;
  }
  const match = series.find((point) => normalizeChartTime(point?.time) === time);
  return toFiniteNumber(match?.value);
}

function tooltipIndicatorLabel(indicator, seriesKey) {
  if (indicator.key !== "bollinger") {
    return indicator.label;
  }
  switch (seriesKey) {
    case "bollingerUpper":
      return "Bollinger Üst";
    case "bollingerMiddle":
      return "Bollinger Orta";
    case "bollingerLower":
      return "Bollinger Alt";
    default:
      return indicator.label;
  }
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

  // Tooltip'i tetikleyen panelin (fiyat/RSI) dikey sınırları içinde tut; böylece
  // fiyat tooltip'i alttaki RSI bölgesiyle çakışmaz.
  const paneTopLimit = rect.top + TOOLTIP_VIEWPORT_MARGIN;
  const paneBottomLimit = rect.bottom - height - TOOLTIP_VIEWPORT_MARGIN;
  if (paneBottomLimit >= paneTopLimit) {
    top = Math.min(Math.max(top, paneTopLimit), paneBottomLimit);
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

function resolveRsiZoneKey(rsiValue, instrumentType) {
  const thresholds = resolveRsiThresholds(instrumentType);
  if (rsiValue >= thresholds.overbought) {
    return "overbought";
  }
  if (rsiValue <= thresholds.oversold) {
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

function buildTechnicalView(snapshot, t, instrumentType) {
  if (!snapshot) {
    return null;
  }

  const stateKey = snapshot.selectedRangeStateKey ?? "neutral";
  const tone = snapshot.selectedRangeTrendTone ?? "neutral";

  const reasons = [{
    text: t(`analysis.chart.techView.rangeReason.${stateKey}`),
    tone,
  }];
  const shortTermReasons = [];

  if (snapshot.rsiValue != null) {
    const rsiZone = resolveRsiZoneKey(snapshot.rsiValue, instrumentType);
    shortTermReasons.push({
      text: t(`analysis.chart.techView.reason.rsi.${rsiZone}`),
      tone: rsiZone === "neutral" ? "neutral" : "warning",
    });
  }

  if (snapshot.lastClose != null && snapshot.sma20 != null) {
    const distancePct = Math.abs(((snapshot.lastClose - snapshot.sma20) / snapshot.lastClose) * 100);
    if (distancePct <= 0.35) {
      shortTermReasons.push({ text: t("analysis.chart.techView.reason.priceNearMa20"), tone: "warning" });
    } else if (snapshot.lastClose > snapshot.sma20) {
      shortTermReasons.push({ text: t("analysis.chart.techView.reason.priceAboveMa20"), tone: "positive" });
    } else {
      shortTermReasons.push({ text: t("analysis.chart.techView.reason.priceBelowMa20"), tone: "negative" });
    }
  }

  if (snapshot.maAlignmentKey === "bullish") {
    shortTermReasons.push({ text: t("analysis.chart.techView.reason.maBullish"), tone: "positive" });
  } else if (snapshot.maAlignmentKey === "bearish") {
    shortTermReasons.push({ text: t("analysis.chart.techView.reason.maBearish"), tone: "negative" });
  }

  if (snapshot.momentumKey === "positive") {
    reasons.push({ text: t("analysis.chart.techView.reason.momentumPositive"), tone: "positive" });
  } else if (snapshot.momentumKey === "negative") {
    reasons.push({ text: t("analysis.chart.techView.reason.momentumNegative"), tone: "negative" });
  } else if (snapshot.momentumKey === "neutral") {
    reasons.push({ text: t("analysis.chart.techView.reason.momentumNeutral"), tone: "warning" });
  }

  if (snapshot.resistanceDistancePct != null && snapshot.resistanceDistancePct <= 1.5) {
    const resistanceKey = snapshot.levelMode === "closeBand" ? "nearRangeHigh" : "nearResistance";
    shortTermReasons.push({ text: t(`analysis.chart.techView.reason.${resistanceKey}`, { value: snapshot.resistanceDistancePct.toFixed(2) }), tone: "warning" });
  } else if (snapshot.supportDistancePct != null && snapshot.supportDistancePct <= 1.5) {
    const supportKey = snapshot.levelMode === "closeBand" ? "nearRangeLow" : "nearSupport";
    shortTermReasons.push({ text: t(`analysis.chart.techView.reason.${supportKey}`, { value: snapshot.supportDistancePct.toFixed(2) }), tone: "warning" });
  }

  if (snapshot.latestSignalTone === "positive") {
    shortTermReasons.push({ text: t("analysis.chart.techView.reason.signalPositive"), tone: "positive" });
  } else if (snapshot.latestSignalTone === "negative") {
    shortTermReasons.push({ text: t("analysis.chart.techView.reason.signalNegative"), tone: "negative" });
  }

  return {
    title: t("analysis.chart.techView.title"),
    label: t(`analysis.chart.techView.state.${stateKey}`),
    tone,
    reasons: reasons.filter((reason) => reason.text).slice(0, 2),
    shortTermTitle: t("analysis.chart.techView.shortTermTitle"),
    shortTermReasons: shortTermReasons.filter((reason) => reason.text).slice(0, 3),
  };
}

function resolveAdvancedChartErrorMessage(error, t, isCrypto) {
  const status = Number(error?.response?.status);
  if ([400, 404, 422, 500].includes(status)) {
    return isCrypto ? t("analysis.chart.errors.noCandles") : t("analysis.rangeUnavailable");
  }
  return extractErrorMessage(error, t("analysis.chart.errors.loadFailed"));
}

function extractIndicatorValue(technicalAnalysis, indicatorName) {
  const normalizedName = String(indicatorName || "").trim().toUpperCase();
  const match = technicalAnalysis?.indicatorValues
    ?.find?.((item) => String(item?.indicator || "").trim().toUpperCase() === normalizedName);
  const numeric = Number(match?.value);
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

function getDrawingBackendId(drawing) {
  const id = Number(drawing?.backendId ?? drawing?.id);
  return Number.isFinite(id) && id > 0 ? id : null;
}

function getDrawingPrice(drawing) {
  const price = Number(drawing?.price ?? drawing?.points?.[0]?.price);
  return Number.isFinite(price) && price > 0 ? price : null;
}

function toApiDrawingType(kind) {
  switch (kind) {
    case "stopLoss":
      return "STOPLOSS_LINE";
    case "takeProfit":
      return "TAKEPROFIT_LINE";
    case "support":
      return "SUPPORT_LINE";
    case "resistance":
      return "RESISTANCE_LINE";
    case "trend":
      return "TREND_LINE";
    case "horizontal":
    default:
      return "HORIZONTAL_LINE";
  }
}

function fromApiDrawingType(type) {
  switch (String(type || "").toUpperCase()) {
    case "STOPLOSS_LINE":
      return "stopLoss";
    case "TAKEPROFIT_LINE":
      return "takeProfit";
    case "SUPPORT_LINE":
      return "support";
    case "RESISTANCE_LINE":
      return "resistance";
    case "TREND_LINE":
      return "trend";
    case "HORIZONTAL_LINE":
    default:
      return "horizontal";
  }
}

function serializeDrawingForApi(drawing, timeframe) {
  if (!drawing || !timeframe) {
    return null;
  }

  const kind = drawing.kind;
  const style = {
    color: drawing.color,
    lineStyle: drawing.lineStyle,
  };

  if (kind === "trend") {
    const points = (drawing.data ?? [])
      .map((point) => ({
        time: normalizeChartTime(point.time),
        price: Number(point.value ?? point.price),
      }))
      .filter((point) => point.time != null && Number.isFinite(point.price));

    if (points.length < 2) {
      return null;
    }

    return {
      drawingType: toApiDrawingType(kind),
      timeframe,
      points,
      style,
      label: drawing.label,
    };
  }

  const price = getDrawingPrice(drawing);
  if (price == null) {
    return null;
  }

  return {
    drawingType: toApiDrawingType(kind),
    timeframe,
    points: [{ price }],
    style,
    label: drawing.label,
  };
}

function attachBackendIdToDrawing(current, drawing, backendId) {
  if (!drawing || !backendId) {
    return current;
  }

  if (drawing.kind === "stopLoss" || drawing.kind === "takeProfit") {
    const target = current[drawing.kind];
    if (!target || target.id !== drawing.id) {
      return current;
    }
    return {
      ...current,
      [drawing.kind]: { ...target, backendId },
    };
  }

  if (drawing.kind === "trend") {
    return {
      ...current,
      trendLines: current.trendLines.map((line) =>
        line.id === drawing.id ? { ...line, backendId } : line,
      ),
    };
  }

  return {
    ...current,
    horizontalLines: current.horizontalLines.map((line) =>
      line.id === drawing.id ? { ...line, backendId } : line,
    ),
  };
}

function findDrawingByKey(drawings, drawingKey) {
  if (!drawings || !drawingKey) {
    return null;
  }

  if (drawingKey === "stopLoss" || drawingKey === "takeProfit") {
    return drawings[drawingKey] ?? null;
  }

  return (
    drawings.horizontalLines?.find((line) => line.id === drawingKey) ??
    drawings.trendLines?.find((line) => line.id === drawingKey) ??
    null
  );
}

function buildChartDrawingFromApi(item, priceSeries, priceChart, t) {
  const kind = fromApiDrawingType(item?.drawingType);
  const backendId = getDrawingBackendId(item);
  const style = item?.style ?? {};
  const color = style.color;
  const lineStyle = Number.isFinite(Number(style.lineStyle)) ? Number(style.lineStyle) : undefined;

  if (kind === "trend") {
    const lineData = (item?.points ?? [])
      .map((point) => ({
        time: normalizeChartTime(point.time),
        value: Number(point.price),
      }))
      .filter((point) => point.time != null && Number.isFinite(point.value))
      .sort((left, right) => left.time - right.time);

    if (lineData.length < 2) {
      return null;
    }

    const trendSeries = priceChart.addSeries(LineSeries, {
      color: color || "#8b5cf6",
      lineWidth: 1.5,
      priceLineVisible: false,
      lastValueVisible: false,
    });
    trendSeries.setData(lineData);

    return {
      id: `trend-${backendId ?? Date.now()}`,
      backendId,
      kind,
      label: item?.label || t("analysis.chart.drawing.trend"),
      color: color || "#8b5cf6",
      series: trendSeries,
      data: lineData,
    };
  }

  const price = getDrawingPrice(item);
  if (price == null) {
    return null;
  }

  if (kind === "stopLoss" || kind === "takeProfit") {
    const isStopLoss = kind === "stopLoss";
    const label = item?.label || (isStopLoss ? "Stop-Loss" : "Take-Profit");
    const resolvedColor = color || (isStopLoss ? "#ef4444" : "#22c55e");
    const priceLine = priceSeries.createPriceLine({
      price,
      color: resolvedColor,
      lineWidth: 2,
      lineStyle: lineStyle ?? 0,
      axisLabelVisible: true,
      title: `${label} ${formatCompactPrice(price)}`,
    });

    return {
      id: kind,
      backendId,
      kind,
      label,
      color: resolvedColor,
      price,
      lineStyle: lineStyle ?? 0,
      priceLine,
    };
  }

  const config = resolveHorizontalToolConfig(kind, t) ?? resolveHorizontalToolConfig("horizontal", t);
  const label = item?.label || config.label;
  const resolvedColor = color || config.color;
  const resolvedLineStyle = lineStyle ?? config.lineStyle;
  const priceLine = priceSeries.createPriceLine({
    price,
    color: resolvedColor,
    lineWidth: 1,
    lineStyle: resolvedLineStyle,
    axisLabelVisible: true,
    title: `${label}: ${formatCompactPrice(price)}`,
  });

  return {
    id: `${kind}-${backendId ?? Date.now()}`,
    backendId,
    kind,
    label,
    color: resolvedColor,
    price,
    lineStyle: resolvedLineStyle,
    priceLine,
  };
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

function rangeToBarSpacing(rangeKey) {
  const key = String(rangeKey || "").toLowerCase();
  if (key === "1m") return 8;
  if (key === "3m") return 5;
  if (key === "6m") return 3;
  if (key === "1y") return 2;
  return 1;
}

function resolveDisplayCode(instrumentCode, quote) {
  if (quote?.code) return String(quote.code).toUpperCase();
  const raw = String(instrumentCode || "");
  // TCMB:AUD:SELL → AUD
  const parts = raw.split(":");
  return parts.length === 3 ? parts[1].toUpperCase() : raw.toUpperCase();
}

function buildDrawingLines(drawings) {
  if (!drawings) return [];
  const lines = [];
  const v = (n) => (n != null && Number.isFinite(Number(n)) ? Number(n).toFixed(2) : "-");

  if (drawings.stopLoss?.price != null) {
    lines.push(`Stop-Loss: ${v(drawings.stopLoss.price)}`);
  }
  if (drawings.takeProfit?.price != null) {
    lines.push(`Take-Profit: ${v(drawings.takeProfit.price)}`);
  }
  drawings.horizontalLines?.forEach((line) => {
    if (line.price != null) {
      lines.push(`${line.label || line.kind}: ${v(line.price)}`);
    }
  });
  drawings.trendLines?.forEach((line) => {
    const d = line.data;
    if (Array.isArray(d) && d.length >= 2) {
      const p1 = d[0], p2 = d[d.length - 1];
      const t1 = formatTooltipDate(p1.time);
      const t2 = formatTooltipDate(p2.time);
      lines.push(`Trend: ${v(p1.value)} (${t1}) → ${v(p2.value)} (${t2})`);
    }
  });
  return lines;
}

function resolveHistoricalValues(noteDate, dataset) {
  if (!noteDate || !dataset) return null;
  const today = new Date().toISOString().slice(0, 10);
  if (noteDate === today) return null;

  const targetTime = toEpochSeconds(noteDate);
  if (!targetTime) return null;

  const atOrBefore = (series) => {
    if (!Array.isArray(series) || !series.length) return null;
    let result = null;
    for (const point of series) {
      if (point.time <= targetTime) result = point.value;
      else break;
    }
    return result;
  };

  const closeAtDate = (() => {
    const series = dataset.priceData;
    if (!Array.isArray(series)) return null;
    let result = null;
    for (const point of series) {
      if (point.time <= targetTime) {
        result = dataset.mode === "candlestick" ? point.close : point.value;
      } else break;
    }
    return result;
  })();

  const od = dataset.overlayData ?? {};
  const sma20 = atOrBefore(od.sma20);
  const sma50 = atOrBefore(od.sma50);
  const rsiValue = atOrBefore(dataset.rsiData);

  const maAlignmentKey = (() => {
    if (sma20 == null || sma50 == null || sma50 === 0) return null;
    const spread = (sma20 - sma50) / sma50;
    if (spread > 0.002) return "bullish";
    if (spread < -0.002) return "bearish";
    return "mixed";
  })();

  const rsiRegimeKey = (() => {
    if (rsiValue == null) return null;
    if (rsiValue >= 70) return "overbought";
    if (rsiValue <= 30) return "oversold";
    return "neutral";
  })();

  return {
    close: closeAtDate,
    rsiValue,
    rsiRegimeKey,
    sma7: atOrBefore(od.sma7),
    sma20,
    sma50,
    ema20: atOrBefore(od.ema20),
    bollingerUpper: atOrBefore(od.bollingerUpper),
    bollingerMiddle: atOrBefore(od.bollingerMiddle),
    bollingerLower: atOrBefore(od.bollingerLower),
    maAlignmentKey,
  };
}

function buildAdvancedNoteContent({ instrumentCode, quote, activeRange, toDate, snapshot, viewLabel, drawings, activeIndicators, historicalValues }) {
  const val = (v, digits = 2) =>
    v == null || !Number.isFinite(Number(v)) ? "-" : Number(v).toFixed(digits);
  const pct = (v) =>
    v == null || !Number.isFinite(Number(v)) ? "-" : `${Number(v) >= 0 ? "+" : ""}${Number(v).toFixed(2)}%`;
  const KEY_LABELS = {
    trend:    { uptrend: "Yükseliş", downtrend: "Düşüş", sideways: "Yatay" },
    rsi:      { overbought: "Aşırı alım", oversold: "Aşırı satım", neutral: "Nötr" },
    momentum: { positive: "Pozitif", negative: "Negatif", neutral: "Nötr", awaiting: "-" },
    vol:      { high: "Yüksek", medium: "Orta", low: "Düşük", awaiting: "-" },
    ma:       { bullish: "Yukarı dizilim", bearish: "Aşağı dizilim", mixed: "Yatay", limited: "-" },
  };
  const label = (map, key) => map[key] ?? key ?? "-";
  const isCloseBand = snapshot?.levelMode === "closeBand";
  const dateObj = toDate ? new Date(toDate) : new Date();
  const date = dateObj.toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" });
  const symbol = resolveDisplayCode(instrumentCode, quote);
  const drawingLines = buildDrawingLines(drawings);
  const h = historicalValues;

  const lastClose   = h?.close       ?? snapshot?.lastClose;
  const rsiValue    = h?.rsiValue    ?? snapshot?.rsiValue;
  const rsiKey      = h?.rsiRegimeKey ?? snapshot?.rsiRegimeKey;
  const maAlignKey  = h?.maAlignmentKey ?? snapshot?.maAlignmentKey;

  const has = (key) => activeIndicators?.has(key);
  const indicatorLines = [];
  if (has("sma7")  && (h?.sma7   ?? null) != null) indicatorLines.push(`MA 7:  ${val(h.sma7)}`);
  if (has("sma20") && (h?.sma20  ?? snapshot?.sma20) != null) indicatorLines.push(`MA 20: ${val(h?.sma20 ?? snapshot?.sma20)}`);
  if (has("sma50") && (h?.sma50  ?? snapshot?.sma50) != null) indicatorLines.push(`MA 50: ${val(h?.sma50 ?? snapshot?.sma50)}`);
  if (has("ema20") && (h?.ema20  ?? null) != null) indicatorLines.push(`EMA 20: ${val(h.ema20)}`);
  if (has("bollinger")) {
    if ((h?.bollingerUpper  ?? null) != null) indicatorLines.push(`Bollinger Üst:  ${val(h.bollingerUpper)}`);
    if ((h?.bollingerMiddle ?? null) != null) indicatorLines.push(`Bollinger Orta: ${val(h.bollingerMiddle)}`);
    if ((h?.bollingerLower  ?? null) != null) indicatorLines.push(`Bollinger Alt:  ${val(h.bollingerLower)}`);
  }

  return [
    `${symbol} - ${String(activeRange || "-").toUpperCase()} Teknik Analiz`,
    "",
    `Son fiyat: ${val(lastClose)}`,
    `Günlük değişim: ${pct(snapshot?.latestChangePct)}`,
    `Teknik görünüm: ${viewLabel ?? "-"}`,
    `RSI: ${val(rsiValue)} (${label(KEY_LABELS.rsi, rsiKey)})`,
    `Momentum: ${label(KEY_LABELS.momentum, snapshot?.momentumKey)}`,
    `Volatilite: ${label(KEY_LABELS.vol, snapshot?.volatilityKey)}`,
    `MA dizilimi: ${label(KEY_LABELS.ma, maAlignKey)}`,
    snapshot?.supportLevel != null
      ? `${isCloseBand ? "Aralık en düşük" : "Destek"}: ${val(snapshot.supportLevel)}`
      : null,
    snapshot?.resistanceLevel != null
      ? `${isCloseBand ? "Aralık en yüksek" : "Direnç"}: ${val(snapshot.resistanceLevel)}`
      : null,
    indicatorLines.length > 0 ? "" : null,
    indicatorLines.length > 0 ? "── Göstergeler ──" : null,
    ...indicatorLines,
    drawingLines.length > 0 ? "" : null,
    drawingLines.length > 0 ? "── Çizimler ──" : null,
    ...drawingLines,
    "",
    `Tarih: ${date}`,
  ].filter((line) => line !== null).join("\n");
}

