import EmptyState from "../common/EmptyState";
import ErrorMessage from "../common/ErrorMessage";
import LoadingSpinner from "../common/LoadingSpinner";
import { formatDateTime } from "../../utils/formatters";

export default function InstrumentNewsList({ loading, error, items }) {
  return (
    <section className="panel-surface instrument-news-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Haberler</p>
          <h3>Secili enstrumanla ilgili akis</h3>
        </div>
      </div>

      {loading ? <LoadingSpinner label="Haberler yukleniyor..." /> : null}
      {error ? <ErrorMessage message={error} /> : null}

      {!loading && !error && items.length === 0 ? (
        <EmptyState title="Haber bulunamadi" description="Bu enstruman icin eslesen haber kaydi gelmedi." />
      ) : null}

      {!loading && !error && items.length > 0 ? (
        <div className="instrument-news-list">
          {items.map((item) => (
            <article key={item.id ?? item.externalId ?? item.url} className="instrument-news-card">
              <div className="instrument-news-meta">
                <span>{item.provider || item.source || "Kaynak yok"}</span>
                <span>{formatDateTime(item.publishedAt)}</span>
              </div>
              <h4>{item.title || "Baslik yok"}</h4>
              <p>{item.summary || "Ozet bilgisi gelmedi."}</p>
              {item.url ? (
                <a href={item.url} target="_blank" rel="noreferrer">
                  Habere git
                </a>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}
