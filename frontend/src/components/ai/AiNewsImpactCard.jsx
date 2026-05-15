import AiCard from "./AiCard";
import { formatDateTime, formatNumber } from "../../utils/formatters";

export default function AiNewsImpactCard({ symbol, availableData = {} }) {
  const newsItems = Array.isArray(availableData.newsItems) ? availableData.newsItems : [];
  const recent = newsItems.slice(0, 3);

  return (
    <AiCard title="AI Haber Etkisi" subtitle={`${symbol || "Varlık"} için haber akışı yorumu`}>
      <p>
        Haber akışında <strong>{formatNumber(newsItems.length, 0)} başlık</strong> görünüyor. Başlıklar fiyat hareketiyle
        birlikte okunmalı; tek haber kalıcı yön için yeterli olmayabilir.
      </p>

      {recent.length ? (
        <ul className="ai-insight-list">
          {recent.map((item) => (
            <li key={item.id || item.externalId || item.title}>
              <strong>{item.provider || item.source || "Haber"}:</strong> {item.title || "Başlıksız haber"}{" "}
              <span className="ai-muted-inline">({formatDateTime(item.publishedAt)})</span>
            </li>
          ))}
        </ul>
      ) : (
        <ul className="ai-insight-list">
          <li>
            <strong>Akış:</strong> Bu ekranda henüz haber verisi yüklenmedi veya ilgili başlık bulunamadı.
          </li>
          <li>
            <strong>Etki:</strong> KAP, merkez bankası ve makro veri akışları oynaklığı artırabilir.
          </li>
        </ul>
      )}
    </AiCard>
  );
}
