// A friendly error with a retry button instead of a dead end.
export default function ErrorCard({ message, onRetry }) {
  return (
    <div className="error-card" role="alert">
      <strong>That move didn’t go through.</strong>
      <p>{message}</p>
      {onRetry && <button className="pill-btn" onClick={onRetry}>Try again</button>}
    </div>
  )
}
