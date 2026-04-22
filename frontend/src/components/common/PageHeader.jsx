export default function PageHeader({ title, description, actions, eyebrow = "Overview" }) {
  return (
    <div className="page-header panel-surface">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description ? <p className="page-description">{description}</p> : null}
      </div>
      {actions ? <div className="page-header-actions">{actions}</div> : null}
    </div>
  );
}
