import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { formatInstrumentCode, getFxCodeLabel } from "../../utils/instrumentUtils";

// ─── Constants (exported so consumers can use them) ──────────────────────────

const SUGGESTION_TYPE_ORDER = ["FX", "CRYPTO", "STOCK", "FUND", "COMMODITY", "FUTURES", "BOND", "INDEX"];

export const INSTRUMENT_CATEGORIES = [
  { key: "ALL", labelKey: "analysis.symbolPicker.categories.all" },
  { key: "FX", labelKey: "analysis.symbolPicker.categories.fx" },
  { key: "CRYPTO", labelKey: "analysis.symbolPicker.categories.crypto" },
  { key: "STOCK", labelKey: "analysis.symbolPicker.categories.stock" },
  { key: "FUND", labelKey: "analysis.symbolPicker.categories.fund" },
  { key: "COMMODITY", labelKey: "analysis.symbolPicker.categories.commodity" },
  { key: "FUTURES", labelKey: "analysis.symbolPicker.categories.futures" },
  { key: "BOND", labelKey: "analysis.symbolPicker.categories.bond" },
  { key: "INDEX", labelKey: "markets.categories.INDEX" },
];

export const INSTRUMENT_CATEGORIES_NO_INDEX = INSTRUMENT_CATEGORIES.filter((c) => c.key !== "INDEX");

const CATEGORY_LABEL_KEYS = {
  FX: "analysis.symbolPicker.categories.fx",
  CRYPTO: "analysis.symbolPicker.categories.crypto",
  STOCK: "analysis.symbolPicker.categories.stock",
  BIST: "analysis.symbolPicker.categories.stock",
  FUND: "analysis.symbolPicker.categories.fund",
  COMMODITY: "analysis.symbolPicker.categories.commodity",
  FUTURES: "analysis.symbolPicker.categories.futures",
  BOND: "analysis.symbolPicker.categories.bond",
  INDEX: "markets.categories.INDEX",
};

// ─── Exported helpers ─────────────────────────────────────────────────────────

export function formatInstrumentLabel(item) {
  return item?.code || formatInstrumentCode(item?.symbol) || "-";
}

export function resolveInstrumentTitle(item, locale) {
  const type = String(item?.instrumentType || "").trim().toUpperCase();
  const displayCode = item?.code || formatInstrumentCode(item?.symbol) || "";
  if (type === "FX") return getFxCodeLabel(displayCode, locale);
  const displayName = String(item?.displayName || "").trim();
  if (displayName && displayName !== displayCode) return displayName;
  return displayCode || "-";
}

export function normalizeInstrumentCategory(item) {
  const type = String(item?.instrumentType || "").trim().toUpperCase();
  switch (type) {
    case "FUTURE": case "FUTURES": return "FUTURES";
    case "BOND": case "BONDS": case "BILL": case "BILLS": case "BONO": return "BOND";
    case "COMMODITY": case "COMMODITIES": return "COMMODITY";
    default: return type;
  }
}

export function resolveTypeLabel(item, t) {
  const type = normalizeInstrumentCategory(item);
  const key = CATEGORY_LABEL_KEYS[type];
  return key ? t(key) : (type || "-");
}

export function buildBalancedSuggestions(pool, limit) {
  const byType = new Map();
  for (const item of pool) {
    const type = String(item?.instrumentType || "").trim().toUpperCase() || "OTHER";
    if (!byType.has(type)) byType.set(type, []);
    byType.get(type).push(item);
  }
  const orderedTypes = [
    ...SUGGESTION_TYPE_ORDER.filter((t) => byType.has(t)),
    ...[...byType.keys()].filter((t) => !SUGGESTION_TYPE_ORDER.includes(t)),
  ];
  const result = [];
  let round = 0;
  let added = true;
  while (result.length < limit && added) {
    added = false;
    for (const type of orderedTypes) {
      const bucket = byType.get(type);
      if (bucket && round < bucket.length) {
        result.push(bucket[round]);
        added = true;
        if (result.length >= limit) break;
      }
    }
    round += 1;
  }
  return result;
}

export function filterAndSearchInstruments(items, search, category) {
  const query = search.trim().toLowerCase();
  const pool = category === "ALL"
    ? items
    : items.filter((item) => normalizeInstrumentCategory(item) === category);

  if (query) {
    return pool
      .filter((item) =>
        item.symbol?.toLowerCase().includes(query)
        || item.code?.toLowerCase().includes(query)
        || item.displayName?.toLowerCase().includes(query))
      .slice(0, 20);
  }

  if (category === "ALL") return buildBalancedSuggestions(pool, 12);
  return pool.slice(0, 12);
}

// ─── Main component ───────────────────────────────────────────────────────────

/**
 * Reusable instrument-selector popover — used for both primary and reference
 * instrument selection in the analysis page.
 *
 * @param {object[]} items           — instruments to show in the popover
 * @param {string}   selectedSymbol  — item.symbol that is currently selected
 * @param {function} onSelect        — (symbol) => void
 * @param {string}   triggerLabel    — text on the closed trigger button
 * @param {string}   placeholder     — search input placeholder
 * @param {object[]} categories      — category tabs; defaults to all categories
 * @param {boolean}  showCategories  — whether to show category filter tabs
 * @param {boolean}  disabled        — greys out trigger; popover won't open
 * @param {string}   disabledTitle   — tooltip when disabled
 */
export default function InstrumentSelectorPopover({
  items,
  selectedSymbol,
  onSelect,
  triggerLabel,
  placeholder,
  categories = INSTRUMENT_CATEGORIES,
  showCategories = true,
  disabled = false,
  disabledTitle = "",
}) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage;

  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("ALL");
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    function onOutside(e) {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false);
    }
    function onKey(e) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onOutside);
    window.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onOutside);
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const results = useMemo(
    () => filterAndSearchInstruments(items, search, category),
    [items, search, category],
  );

  function handleSelect(symbol) {
    onSelect(symbol);
    setOpen(false);
    setSearch("");
    setCategory("ALL");
  }

  return (
    <div className="analysis-primary-picker" ref={rootRef}>
      <button
        type="button"
        disabled={disabled}
        className={`analysis-primary-trigger${open ? " active" : ""}${disabled ? " comparison-chip-disabled" : ""}`}
        onClick={() => setOpen((p) => !p)}
        title={disabled ? disabledTitle : undefined}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className="analysis-primary-trigger-label">{triggerLabel}</span>
        <ChevronDown size={15} strokeWidth={2.2} />
      </button>

      {open && !disabled ? (
        <div className="analysis-instrument-popover" role="dialog" aria-label={placeholder}>
          <label className="analysis-compare-search">
            <Search size={14} strokeWidth={2} />
            <input
              autoFocus
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={placeholder}
            />
          </label>

          {showCategories ? (
            <div className="analysis-instrument-categories" role="group">
              {categories.map((cat) => (
                <button
                  key={cat.key}
                  type="button"
                  className={`analysis-instrument-cat${category === cat.key ? " active" : ""}`}
                  onClick={() => setCategory(cat.key)}
                >
                  {t(cat.labelKey)}
                </button>
              ))}
            </div>
          ) : null}

          <div className="analysis-compare-results">
            {results.length === 0 ? (
              <div className="analysis-compare-empty">{t("analysis.symbolPicker.noResults")}</div>
            ) : results.map((item) => {
              const isSelected = item.symbol === selectedSymbol;
              return (
                <button
                  key={`${item.symbol}-${item.source ?? ""}`}
                  type="button"
                  className={`analysis-compare-option analysis-instrument-option${isSelected ? " active" : ""}`}
                  onClick={() => handleSelect(item.symbol)}
                >
                  <span className="analysis-instrument-option-top">
                    <strong>{formatInstrumentLabel(item)}</strong>
                    <span className="analysis-instrument-option-type">{resolveTypeLabel(item, t)}</span>
                  </span>
                  <span className="analysis-instrument-option-name">{resolveInstrumentTitle(item, locale)}</span>
                </button>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}
