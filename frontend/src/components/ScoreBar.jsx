// A small horizontal bar for a score in [0, 1]; red below 45%, green above 55%.
export default function ScoreBar({ score, muted }) {
  if (score == null) return <span className="muted">—</span>
  const p = Math.round(score * 100)
  const tone = muted ? 'neutral' : p < 45 ? 'bad' : p > 55 ? 'good' : 'mid'
  return (
    <span className="scorebar" title={`${p}%`}>
      <span className="track">
        <span className={`fill ${tone}`} style={{ width: `${p}%` }} />
      </span>
      <span className="num">{p}%</span>
    </span>
  )
}
