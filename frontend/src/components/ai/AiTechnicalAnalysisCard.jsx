import AiTechnicalInsightCard from "./AiTechnicalInsightCard";

export default function AiTechnicalAnalysisCard({ symbol, quote, analysis, trendLabel, dataPointCount = 0 }) {
  return (
    <AiTechnicalInsightCard
      symbol={symbol}
      availableData={{
        analysis,
        chartData: Array.from({ length: dataPointCount }),
        quote,
        trendLabel,
      }}
    />
  );
}
