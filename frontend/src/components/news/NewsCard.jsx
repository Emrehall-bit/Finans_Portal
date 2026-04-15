import { formatDateTime } from "../../utils/formatters";

export default function NewsCard({ item, onClick }) {
  return (
    <button className="card news-card" onClick={() => onClick(item)}>
      <h3>{item.title || "Untitled"}</h3>
      <p className="muted">
        {(item.provider || item.source || "-")} | {formatDateTime(item.publishedAt)}
      </p>
      <p>{item.summary || "No summary provided."}</p>
    </button>
  );
}
