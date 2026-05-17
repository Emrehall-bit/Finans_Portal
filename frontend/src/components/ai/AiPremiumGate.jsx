import AiLockedCard from "./AiLockedCard";

export default function AiPremiumGate({ featureName, description }) {
  return (
    <AiLockedCard
      featureName={featureName}
      description={description ?? "Premium üyelik ile gelişmiş AI analizlerine erişebilirsiniz."}
      requiresPremium
    />
  );
}
