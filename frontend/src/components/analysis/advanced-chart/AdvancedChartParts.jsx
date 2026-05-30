import { memo } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Check, CircleAlert, Lock, Minus, Sparkles, X } from "lucide-react";
import { formatNumber } from "../../../utils/formatters";

export const MetricGroup = memo(function MetricGroup({ title, children }) {
  return (
    <div className="adv-metric-group">
      <span className="adv-metric-group-title">{title}</span>
      <div className="adv-metric-group-body">{children}</div>
    </div>
  );
});

export const MetricRow = memo(function MetricRow({ label, value, tone = "neutral", detail = null }) {
  return (
    <div className="adv-metric-row">
      <span className="adv-metric-row-label">
        {tone && tone !== "neutral" ? <i className={`adv-metric-dot adv-metric-dot--${tone}`} aria-hidden="true" /> : null}
        {label}
      </span>
      <span className="adv-metric-row-value">{value}</span>
      {detail ? <span className="adv-metric-row-detail">{detail}</span> : null}
    </div>
  );
});

const TECH_CHECK_ICONS = {
  positive: Check,
  warning: CircleAlert,
  negative: X,
};

export const TechnicalViewCard = memo(function TechnicalViewCard({ view }) {
  if (!view) {
    return null;
  }

  return (
    <section className={`advanced-tech-view advanced-tech-view--${view.tone}`}>
      <div className="advanced-tech-view-head">
        <span>{view.title}</span>
        <strong className="advanced-tech-view-verdict">{view.label}</strong>
      </div>
      <ul className="advanced-tech-view-checklist">
        {view.reasons.map((reason) => {
          const Icon = TECH_CHECK_ICONS[reason.tone] ?? Minus;
          return (
            <li key={reason.text} className={`advanced-tech-check advanced-tech-check--${reason.tone}`}>
              <Icon size={13} strokeWidth={2.6} className="advanced-tech-check-icon" />
              <span>{reason.text}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
});

export const CrosshairTooltip = memo(function CrosshairTooltip({ model }) {
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
            <strong>{`RSI: ${formatNumber(model.rsiValue, 2)} â€” ${model.zoneLabel}`}</strong>
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

export const TooltipMetric = memo(function TooltipMetric({ label, value, digits = 2, suffix = "" }) {
  return (
    <div className="advanced-crosshair-row">
      <span>{label}</span>
      <strong>{value == null ? "-" : `${formatNumber(value, digits)}${suffix}`}</strong>
    </div>
  );
});

export const LegendTooltip = memo(function LegendTooltip({ model }) {
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

export function AiTechPanel({ isAuthenticated, isPremium, aiData, aiLoading, aiError, onRetry, onLogin, t }) {
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
          {aiData.metadata.cacheHit ? "âš¡ " : "ğŸ¤– "}{aiData.metadata.provider}
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
