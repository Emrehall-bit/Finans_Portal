import { Info, Sparkles } from "lucide-react";

export default function AiCard({ title, subtitle, badge = "AI", children, footer, className = "" }) {
  return (
    <section className={`ai-card ${className}`}>
      <div className="ai-card-glow" aria-hidden="true" />
      <header className="ai-card-head">
        <div className="ai-card-title-row">
          <span className="ai-card-icon" aria-hidden="true">
            <Sparkles size={17} />
          </span>
          <div>
            <h3>{title}</h3>
            {subtitle ? <p>{subtitle}</p> : null}
          </div>
        </div>
        <span className="ai-card-badge">{badge}</span>
      </header>

      <div className="ai-card-body">{children}</div>
      {footer ? <div className="ai-card-footer">{footer}</div> : null}
      <p className="ai-disclaimer">
        <Info size={13} aria-hidden="true" />
        <span>Bu yorum yatırım tavsiyesi değildir; yalnızca mevcut verilerin otomatik analizidir.</span>
      </p>
    </section>
  );
}
