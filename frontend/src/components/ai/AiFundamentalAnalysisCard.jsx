import AiFundamentalInsightCard from "./AiFundamentalInsightCard";

export default function AiFundamentalAnalysisCard({ symbol, data }) {
  return (
    <AiFundamentalInsightCard
      symbol={symbol}
      availableData={{
        fundamentals: data,
      }}
    />
  );
}
