export default function PageHeader({ title, description, actions }) {
  return (
    <div className="page-header">
      <div>
        <span className="page-eyebrow">ENTERPRISE WORKSPACE</span>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  )
}
