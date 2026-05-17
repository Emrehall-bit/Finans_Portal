import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, GitCompareArrows, LockKeyhole, ShieldAlert, Sparkles } from "lucide-react";
import { getAiComparisonAnalysis } from "../../api/aiApi";
import { getMarkets } from "../../api/marketApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useAuth } from "../../auth/AuthContext";
import AiLockedCard from "./AiLockedCard";
import AiResponseMeta from "./AiResponseMeta";

export default function AiCompanyComparisonCard({ leftSymbol, displaySymbol, instrumentType = "STOCK" }) {
  const { isAuthenticated, isPremium } = useAuth();
  const [rightSymbol, setRightSymbol] = useState("");
  const [quotes, setQuotes] = useState([]);
  const [loadingQuotes, setLoadingQuotes] = useState(false);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [validationMessage, setValidationMessage] = useState("");

  const canUse = isPremium;
  const normalizedLeft = normalizeCode(leftSymbol);
  const datalistId = useMemo(() => `ai-compare-options-${normalizedLeft || "instrument"}`, [normalizedLeft]);

  useEffect(() => {
    if (!canUse) {
      setQuotes([]);
      setLoadingQuotes(false);
      return;
    }

    let active = true;

    async function loadQuotes() {
      try {
        setLoadingQuotes(true);
        const rows = await getMarkets();
        if (!active) {
          return;
        }
        setQuotes(Array.isArray(rows) ? rows : []);
      } catch {
        if (active) {
          setQuotes([]);
        }
      } finally {
        if (active) {
          setLoadingQuotes(false);
        }
      }
    }

    loadQuotes();
    return () => {
      active = false;
    };
  }, [canUse]);

  const compareOptions = useMemo(() => {
    const normalizedType = String(instrumentType || "").trim().toUpperCase();
    return quotes
      .filter((item) => normalizeCode(item.symbol) !== normalizedLeft)
      .filter((item) => {
        if (!normalizedType) {
          return true;
        }
        const itemType = String(item.instrumentType || "").trim().toUpperCase();
        return itemType === normalizedType;
      })
      .slice(0, 150);
  }, [instrumentType, normalizedLeft, quotes]);

  const normalizedRight = resolveRightSymbol(rightSymbol, compareOptions);

  async function handleCompare() {
    if (!canUse) {
      return;
    }

    if (!normalizedRight) {
      setValidationMessage("Karsilastirmak icin ikinci enstrumani secmelisiniz.");
      setError("");
      setData(null);
      return;
    }

    if (normalizeCode(normalizedRight) === normalizedLeft) {
      setValidationMessage("Ayni enstruman karsilastirilamaz.");
      setError("");
      setData(null);
      return;
    }

    try {
      setLoading(true);
      setValidationMessage("");
      setError("");
      const response = await getAiComparisonAnalysis(leftSymbol, normalizedRight);
      setData(response ?? null);
    } catch (requestError) {
      setData(null);
      setError(extractErrorMessage(requestError, "AI karsilastirma su anda uretilemedi."));
    } finally {
      setLoading(false);
    }
  }

  if (!isAuthenticated) {
    return (
      <AiLockedCard
        featureName="AI ile Karsilastir"
        description="Mevcut enstrumani ikinci bir enstrumanla AI yorumu uzerinden karsilastirabilirsiniz."
      />
    );
  }

  if (!canUse) {
    return (
      <AiLockedCard
        featureName="AI ile Karsilastir"
        description="Iki enstrumanin teknik, temel ve risk farklarini premium AI yorumu ile gorebilirsiniz."
        requiresPremium
      />
    );
  }

  return (
    <section className="ai-card ai-comparison-card">
      <div className="ai-card-glow" aria-hidden="true" />

      <header className="ai-card-head">
        <div className="ai-card-title-row">
          <span className="ai-card-icon" aria-hidden="true">
            <GitCompareArrows size={17} />
          </span>
          <div>
            <h3>AI ile Karsilastir</h3>
            <p>{displaySymbol || leftSymbol} icin ikinci enstrumanla premium yorum karsilastirmasi</p>
          </div>
        </div>
        <span className="ai-card-badge ai-unified-badge">PRO</span>
      </header>

      <div className="ai-card-body">
        <div className="ai-comparison-toolbar">
          <div className="market-filter-field">
            <span>Mevcut enstruman</span>
            <input value={displaySymbol || leftSymbol || ""} disabled />
          </div>

          <div className="market-filter-field">
            <span>Karsilastirilacak enstruman</span>
            <input
              list={datalistId}
              value={rightSymbol}
              onChange={(event) => setRightSymbol(event.target.value)}
              placeholder={loadingQuotes ? "Liste yukleniyor..." : "PGSUS, SDTTR, ETHUSDT..."}
            />
            <datalist id={datalistId}>
              {compareOptions.map((item) => (
                <option key={`${item.symbol}-${item.source || item.instrumentType}`} value={item.symbol}>
                  {formatOptionLabel(item)}
                </option>
              ))}
            </datalist>
          </div>

          <button type="button" className="ai-comparison-action" onClick={handleCompare} disabled={loading}>
            {loading ? "Karsilastiriliyor..." : "AI ile Karsilastir"}
          </button>
        </div>

        {validationMessage ? (
          <p className="ai-state-message error">
            <LockKeyhole size={14} aria-hidden="true" />
            <span>{validationMessage}</span>
          </p>
        ) : null}

        {error ? <p className="ai-state-message error">{error}</p> : null}

        {!loading && !error && data ? (
          <div className="ai-comparison-content">
            {data.dataQuality !== "COMPLETE" ? (
              <div className="ai-comparison-warning">
                <AlertTriangle size={14} aria-hidden="true" />
                <span>
                  Veri kalitesi {formatDataQuality(data.dataQuality)}. Yorum bazi alanlarda sinirli veriyle uretildi.
                </span>
              </div>
            ) : null}

            <ComparisonSection icon={<Sparkles size={14} aria-hidden="true" />} title="Ozet" content={data.summary} />
            <ComparisonSection icon={<GitCompareArrows size={14} aria-hidden="true" />} title="Teknik gorunum" content={data.technicalComparison} />
            <ComparisonSection icon={<Sparkles size={14} aria-hidden="true" />} title="Temel gorunum" content={data.fundamentalComparison} />
            <ComparisonSection icon={<ShieldAlert size={14} aria-hidden="true" />} title="Risk karsilastirmasi" content={data.riskComparison} />

            <div className="ai-comparison-grid">
              <ListBlock title={`${displaySymbol || leftSymbol} guclu yonler`} items={data.strengthsLeft} tone="positive" />
              <ListBlock title={`${formatComparedSymbol(data.rightSymbol)} guclu yonler`} items={data.strengthsRight} tone="positive" />
              <ListBlock title={`${displaySymbol || leftSymbol} zayif yonler`} items={data.weaknessesLeft} tone="risk" />
              <ListBlock title={`${formatComparedSymbol(data.rightSymbol)} zayif yonler`} items={data.weaknessesRight} tone="risk" />
            </div>

            <ComparisonSection icon={<Sparkles size={14} aria-hidden="true" />} title="Final yorum" content={data.finalComment} />
          </div>
        ) : null}
      </div>

      {data ? <div className="ai-card-footer"><AiResponseMeta metadata={data.metadata} /></div> : null}
    </section>
  );
}

function ComparisonSection({ icon, title, content }) {
  if (!content) {
    return null;
  }

  return (
    <div className="ai-unified-section">
      <div className="ai-unified-section-head">
        {icon}
        <strong>{title}</strong>
      </div>
      <p>{content}</p>
    </div>
  );
}

function ListBlock({ title, items, tone = "neutral" }) {
  const rows = Array.isArray(items) ? items.filter(Boolean) : [];
  if (rows.length === 0) {
    return null;
  }

  return (
    <div className={`ai-fundamental-list ai-comparison-list ai-comparison-list--${tone}`}>
      <strong>{title}</strong>
      <ul>
        {rows.map((item, index) => (
          <li key={`${title}-${index}`}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function normalizeCode(value) {
  if (value == null) {
    return "";
  }

  const rawValue = String(value).trim();
  if (rawValue.toUpperCase().startsWith("TCMB:")) {
    return rawValue.toUpperCase();
  }

  return rawValue.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function resolveRightSymbol(input, options) {
  const rawInput = String(input || "").trim();
  if (!rawInput) {
    return "";
  }

  const matched = options.find((item) => normalizeCode(item.symbol) === normalizeCode(rawInput) || normalizeCode(item.code) === normalizeCode(rawInput));
  return matched?.symbol || rawInput;
}

function formatOptionLabel(item) {
  const code = item?.code || item?.symbol || "-";
  const title = item?.displayName ? ` - ${item.displayName}` : "";
  return `${code}${title}`;
}

function formatComparedSymbol(symbol) {
  return String(symbol || "").trim() || "-";
}

function formatDataQuality(value) {
  switch (String(value || "").toUpperCase()) {
    case "COMPLETE":
      return "yuksek";
    case "PARTIAL":
      return "orta";
    case "LIMITED":
    case "LOW":
    case "INSUFFICIENT":
      return "dusuk";
    default:
      return "bilinmiyor";
  }
}
