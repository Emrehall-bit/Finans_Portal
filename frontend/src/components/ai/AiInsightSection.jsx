import AiFundInsightCard from "./AiFundInsightCard";
import AiFundamentalInsightCard from "./AiFundamentalInsightCard";
import AiMacroInsightCard from "./AiMacroInsightCard";
import AiNewsImpactCard from "./AiNewsImpactCard";
import AiRiskSummaryCard from "./AiRiskSummaryCard";
import AiTechnicalInsightCard from "./AiTechnicalInsightCard";
import AiYieldRiskInsightCard from "./AiYieldRiskInsightCard";

export default function AiInsightSection({ instrumentType, symbol, code, availableData = {} }) {
  const type = normalizeInstrumentType(instrumentType);
  const displaySymbol = symbol || code || availableData.quote?.code || availableData.quote?.symbol || "-";

  return (
    <section className="ai-insight-section">
      <div className="ai-insight-section-head">
        <div>
          <p className="eyebrow">AI İçgörüler</p>
          <h3>{formatInstrumentType(type)} analizi</h3>
        </div>
        <span>{displaySymbol}</span>
      </div>

      <div className="ai-insight-grid">
        {type === "STOCK" ? (
          <>
            <AiTechnicalInsightCard symbol={displaySymbol} availableData={availableData} />
            <AiFundamentalInsightCard symbol={displaySymbol} availableData={availableData} />
            <AiNewsImpactCard symbol={displaySymbol} availableData={availableData} />
            <AiRiskSummaryCard instrumentType={type} symbol={displaySymbol} availableData={availableData} />
          </>
        ) : null}

        {type === "CRYPTO" ? (
          <>
            <AiTechnicalInsightCard symbol={displaySymbol} availableData={availableData} />
            <AiNewsImpactCard symbol={displaySymbol} availableData={availableData} />
            <AiRiskSummaryCard instrumentType={type} symbol={displaySymbol} availableData={availableData} />
          </>
        ) : null}

        {type === "FUND" ? <AiFundInsightCard symbol={displaySymbol} availableData={availableData} /> : null}

        {type === "FX" ? <AiMacroInsightCard symbol={displaySymbol} availableData={availableData} /> : null}

        {type === "BOND" ? <AiYieldRiskInsightCard symbol={displaySymbol} availableData={availableData} /> : null}

        {type === "FUTURES" ? (
          <>
            <AiTechnicalInsightCard symbol={displaySymbol} availableData={availableData} highRisk />
            <AiRiskSummaryCard instrumentType={type} symbol={displaySymbol} availableData={availableData} highRisk />
          </>
        ) : null}
      </div>
    </section>
  );
}

function normalizeInstrumentType(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (["STOCK", "CRYPTO", "FUND", "FX", "BOND", "FUTURES"].includes(normalized)) {
    return normalized;
  }
  return "STOCK";
}

function formatInstrumentType(type) {
  return {
    STOCK: "Hisse",
    CRYPTO: "Kripto",
    FUND: "Fon",
    FX: "Döviz",
    BOND: "Tahvil/Bono",
    FUTURES: "Vadeli İşlem",
  }[type] ?? "Varlık";
}
