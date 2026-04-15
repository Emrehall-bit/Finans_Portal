export default function EmptyState({ title = "No data", description = "Nothing to show yet." }) {
  return (
    <div className="status-box empty">
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  );
}
