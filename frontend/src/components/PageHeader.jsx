// Screen header in the same voice as the start screen: a small chip, a big heavy title
// with an orange ending, and one plain sentence.
export default function PageHeader({ kicker, title, accent, children, aside, small = false }) {
  return (
    <header className={`page-head ${small ? 'small' : ''}`}>
      <div>
        <span className="kicker">{kicker}</span>
        <h1 className="page-title">
          {title} {accent && <span>{accent}</span>}
        </h1>
        {children && <p className="page-sub">{children}</p>}
      </div>
      {aside && <div className="page-aside">{aside}</div>}
    </header>
  )
}
