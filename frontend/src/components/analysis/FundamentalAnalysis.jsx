import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../auth/AuthContext";
import PremiumGate from "../common/PremiumGate";
import {
  getFundamentalRatios,
  getFundamentalHistory,
  getFinancialData,
} from "../../api/analysisApi";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";

function RatioCard({ label, value, unit = "", suffix = "" }) {
  if (value == null) return null;
  const formatted =
    typeof value === "number" ? value.toFixed(2) : parseFloat(value).toFixed(2);
  return (
    <div className="ratio-card">
      <span className="ratio-label">{label}</span>
      <span className="ratio-value">
        {unit}{formatted}{suffix}
      </span>
    </div>
  );
}

function SignalBadge({ signal }) {
  const colors = { BULLISH: "badge-bullish", NEUTRAL: "badge-neutral", BEARISH: "badge-bearish" };
  const labels = { BULLISH: "Alınabilir", NEUTRAL: "Nötr", BEARISH: "Riskli" };
  if (!signal) return null;
  return <span className={`signal-badge ${colors[signal] ?? "badge-neutral"}`}>{labels[signal] ?? signal}</span>;
}

function GrowthArrow({ value }) {
  if (value == null) return null;
  const num = parseFloat(value);
  return (
    <span className={num >= 0 ? "growth-up" : "growth-down"}>
      {num >= 0 ? "↑" : "↓"} {Math.abs(num).toFixed(1)}%
    </span>
  );
}

function PiotroskiCard({ score }) {
  if (score == null) return null;
  const color = score >= 7 ? "#22c55e" : score >= 4 ? "#f59e0b" : "#ef4444";
  return (
    <div className="piotroski-card">
      <h4>Piotroski F-Skoru</h4>
      <div className="piotroski-bar-bg">
        <div
          className="piotroski-bar-fill"
          style={{ width: `${(score / 9) * 100}%`, background: color }}
        />
      </div>
      <p style={{ color }}>{score} / 9</p>
    </div>
  );
}

function AltmanCard({ score }) {
  if (score == null) return null;
  const num = parseFloat(score);
  const zone =
    num < 1.81 ? { label: "Tehlike Bölgesi", color: "#ef4444" }
    : num < 2.99 ? { label: "Gri Bölge", color: "#f59e0b" }
    : { label: "Güvenli Bölge", color: "#22c55e" };
  return (
    <div className="altman-card">
      <h4>Altman Z-Skoru</h4>
      <p className="altman-score" style={{ color: zone.color }}>{num.toFixed(2)}</p>
      <span className="altman-zone" style={{ color: zone.color }}>{zone.label}</span>
    </div>
  );
}

export default function FundamentalAnalysis({ instrumentCode }) {
  const { isPremium } = useAuth();
  const { t } = useTranslation();
  const [ratios, setRatios] = useState(null);
  const [history, setHistory] = useState([]);
  const [financials, setFinancials] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!instrumentCode) return;
    setLoading(true);
    setError("");

    const promises = [
      getFundamentalRatios(instrumentCode).then(setRatios).catch(() => {}),
      getFinancialData(instrumentCode).then(setFinancials).catch(() => {}),
    ];

    if (isPremium) {
      promises.push(getFundamentalHistory(instrumentCode).then(setHistory).catch(() => {}));
    }

    Promise.all(promises)
      .catch((e) => setError(e?.message ?? "Veriler yüklenemedi"))
      .finally(() => setLoading(false));
  }, [instrumentCode, isPremium]);

  if (loading) return <div className="fundamental-loading">Temel analiz yükleniyor…</div>;
  if (error) return <div className="fundamental-error">{error}</div>;
  if (!ratios) return <div className="fundamental-empty">Bu enstrüman için temel analiz verisi yok.</div>;

  return (
    <div className="fundamental-analysis">
      {/* Başlık ve genel sinyal */}
      <div className="fundamental-header">
        <h3>{instrumentCode.toUpperCase()} — Temel Analiz</h3>
        <SignalBadge signal={ratios.overallSignal} />
      </div>

      {/* Finansal özet kartları */}
      {financials.length > 0 && (
        <div className="financial-summary-grid">
          <div className="fin-card">
            <span className="fin-label">Hasılat</span>
            <span className="fin-value">
              {(financials[0].revenue / 1_000_000).toFixed(0)} M
            </span>
          </div>
          <div className="fin-card">
            <span className="fin-label">Net Kar</span>
            <span className="fin-value" style={{ color: financials[0].netIncome >= 0 ? "#22c55e" : "#ef4444" }}>
              {(financials[0].netIncome / 1_000_000).toFixed(0)} M
            </span>
          </div>
          <div className="fin-card">
            <span className="fin-label">Özkaynaklar</span>
            <span className="fin-value">{(financials[0].totalEquity / 1_000_000).toFixed(0)} M</span>
          </div>
          <div className="fin-card">
            <span className="fin-label">Toplam Varlıklar</span>
            <span className="fin-value">{(financials[0].totalAssets / 1_000_000).toFixed(0)} M</span>
          </div>
        </div>
      )}

      {/* Oran grid */}
      <div className="ratios-grid">
        <RatioCard label="F/K" value={ratios.peRatio} />
        <RatioCard label="PD/DD" value={ratios.pbRatio} />
        <RatioCard label="Brüt Marj" value={ratios.grossMargin} suffix="%" />
        <RatioCard label="Net Marj" value={ratios.netMargin} suffix="%" />
        <RatioCard label="ROE" value={ratios.roe} suffix="%" />
        <RatioCard label="ROA" value={ratios.roa} suffix="%" />
        <RatioCard label="Borç/Özkaynak" value={ratios.debtToEquity} />
        <RatioCard label="Cari Oran" value={ratios.currentRatio} />
      </div>

      {/* YoY Büyüme */}
      {(ratios.revenueGrowthYoy != null || ratios.netIncomeGrowthYoy != null) && (
        <div className="growth-row">
          <span>Hasılat Büyümesi (YoY): <GrowthArrow value={ratios.revenueGrowthYoy} /></span>
          <span>Net Kar Büyümesi (YoY): <GrowthArrow value={ratios.netIncomeGrowthYoy} /></span>
          <span>Varlık Büyümesi (YoY): <GrowthArrow value={ratios.assetGrowthYoy} /></span>
        </div>
      )}

      {/* Premium bölüm */}
      <PremiumGate featureName="Gelişmiş Skorlar" previewMode="blur">
        <div className="premium-scores-grid">
          {/* Graham Sayısı */}
          {ratios.grahamNumber != null && (
            <div className="graham-card">
              <h4>Graham Sayısı</h4>
              <p className="graham-number">₺{parseFloat(ratios.grahamNumber).toFixed(2)}</p>
              <p className="graham-current">Mevcut: ₺{parseFloat(ratios.calculationPrice).toFixed(2)}</p>
              <p
                className="graham-discount"
                style={{
                  color:
                    ratios.calculationPrice < ratios.grahamNumber ? "#22c55e" : "#ef4444",
                }}
              >
                {ratios.calculationPrice < ratios.grahamNumber
                  ? `%${(((ratios.grahamNumber - ratios.calculationPrice) / ratios.grahamNumber) * 100).toFixed(0)} İskontolu`
                  : "Primli İşlem Görüyor"}
              </p>
            </div>
          )}

          <PiotroskiCard score={ratios.piotroskiScore} />
          <AltmanCard score={ratios.altmanZScore} />
        </div>

        {/* Çok dönem trend grafiği */}
        {history.length > 0 && (
          <div className="fundamental-trend-chart">
            <h4>Çok Dönem Trend (Son 8 Dönem)</h4>
            <ResponsiveContainer width="100%" height={250}>
              <LineChart data={[...history].reverse()} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
                <XAxis dataKey="period" tick={{ fill: "#9ca3af", fontSize: 11 }} />
                <YAxis yAxisId="left" tick={{ fill: "#9ca3af", fontSize: 11 }} />
                <YAxis yAxisId="right" orientation="right" tick={{ fill: "#9ca3af", fontSize: 11 }} />
                <Tooltip
                  contentStyle={{ background: "#1e293b", border: "1px solid #334155", color: "#f1f5f9" }}
                />
                <Legend />
                <Line yAxisId="right" type="monotone" dataKey="netMargin" name="Net Marj %" stroke="#22c55e" dot={false} />
                <Line yAxisId="right" type="monotone" dataKey="roe" name="ROE %" stroke="#3b82f6" dot={false} />
                <Line yAxisId="left" type="monotone" dataKey="peRatio" name="F/K" stroke="#f59e0b" dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </PremiumGate>
    </div>
  );
}
