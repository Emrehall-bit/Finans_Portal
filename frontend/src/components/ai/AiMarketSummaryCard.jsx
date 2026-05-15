import AiCard from "./AiCard";
import { formatCurrency, formatNumber } from "../../utils/formatters";

export default function AiMarketSummaryCard({ marketQuotes = [], newsItems = [] }) {
  const rows = Array.isArray(marketQuotes) ? marketQuotes : [];
  const changedRows = rows.filter((item) => Number.isFinite(Number(item?.changeRate)));
  const gainers = [...changedRows].sort((a, b) => Number(b.changeRate) - Number(a.changeRate)).slice(0, 2);
  const losers = [...changedRows].sort((a, b) => Number(a.changeRate) - Number(b.changeRate)).slice(0, 2);
  const averageChange = changedRows.length
    ? changedRows.reduce((sum, item) => sum + Number(item.changeRate), 0) / changedRows.length
    : null;
  const marketMood = averageChange === null
    ? "Veri bekleniyor"
    : averageChange > 0.4
      ? "pozitif"
      : averageChange < -0.4
        ? "zayıf"
        : "dengeli";

  return (
    <AiCard title="AI Piyasa Özeti" subtitle="Mevcut ekran verilerinden üretilen kısa piyasa yorumu">
      <p>
        Piyasa görünümü şu anda <strong>{marketMood}</strong>. İzlenen enstrümanlarda ortalama günlük değişim{" "}
        <strong>{averageChange === null ? "-" : `${formatNumber(averageChange, 2)}%`}</strong> seviyesinde.
        Haber akışında {newsItems?.length || 0} başlık öne çıkıyor.
      </p>

      <div className="ai-metric-strip">
        <AiMiniMetric label="İzlenen" value={formatNumber(rows.length, 0)} />
        <AiMiniMetric label="Yükselen" value={formatNumber(changedRows.filter((item) => Number(item.changeRate) > 0).length, 0)} />
        <AiMiniMetric label="Düşen" value={formatNumber(changedRows.filter((item) => Number(item.changeRate) < 0).length, 0)} />
      </div>

      <ul className="ai-insight-list">
        <li>
          <strong>Güçlüler:</strong> {formatMoverList(gainers)}
        </li>
        <li>
          <strong>Zayıflar:</strong> {formatMoverList(losers)}
        </li>
        <li>
          <strong>Odak:</strong> Kısa vadede hacim ve haber akışı oynaklığı belirleyebilir.
        </li>
      </ul>
    </AiCard>
  );
}

function AiMiniMetric({ label, value }) {
  return (
    <span className="ai-mini-metric">
      <small>{label}</small>
      <strong>{value}</strong>
    </span>
  );
}

function formatMoverList(rows) {
  if (!rows.length) {
    return "Yeterli veri yok";
  }

  return rows
    .map((item) => `${item.code || item.symbol || "-"} (${formatSignedPercent(item.changeRate)}, ${formatCurrency(item.price, item.currency || "TRY")})`)
    .join(", ");
}

function formatSignedPercent(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return "-";
  return `${numeric >= 0 ? "+" : ""}${formatNumber(numeric, 2)}%`;
}
