export default function PageHeader({ title, description, actions, eyebrow = "Overview", className = "" }) {
  const rootClassName = `page-header panel-surface${className ? ` ${className}` : ""}`;

  return (
    <div className={rootClassName}>
      <div>
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h1>{title}</h1>
        {description ? <p className="page-description">{description}</p> : null}
      </div>
      {actions ? <div className="page-header-actions">{actions}</div> : null}
    </div>
  );
}
