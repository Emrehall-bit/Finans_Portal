import { Crown, Lock } from "lucide-react";

/**
 * Renders a locked premium card in place of a premium AI feature
 * when the current user does not have premium access.
 *
 * Usage:
 *   if (!isPremium) return <AiPremiumGate featureName="..." description="..." />;
 */
export default function AiPremiumGate({ featureName, description }) {
  return (
    <section className="ai-card ai-premium-gate-card" aria-label={`${featureName} — premium özellik`}>
      <div className="ai-premium-gate-glow" aria-hidden="true" />

      <div className="ai-premium-gate-body">
        <span className="ai-premium-gate-lock" aria-hidden="true">
          <Lock size={20} />
        </span>

        <div className="ai-premium-gate-content">
          <div className="ai-premium-gate-top">
            <h3 className="ai-premium-gate-title">{featureName}</h3>
            <span className="ai-premium-upgrade-badge">
              <Crown size={10} aria-hidden="true" />
              PREMIUM
            </span>
          </div>

          <p className="ai-premium-gate-desc">
            {description ?? "Bu gelişmiş AI analizi premium üyeler için sunulmaktadır."}
          </p>

          <p className="ai-premium-gate-hint">
            Premium üyelik ile birleşik analiz, haber etki analizi ve daha fazlasına erişebilirsiniz.
          </p>
        </div>
      </div>
    </section>
  );
}
