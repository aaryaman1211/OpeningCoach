// A score in [0, 1] as a short bar and a percentage. Coloured only when compared with
// another score (e.g. yours vs strong players') and the gap is meaningful (5+ points).
export default function ScoreBar({ score, compare }) {
  if (score == null) return <span className="muted">–</span>
  const p = Math.round(score * 100)
  let tone = ''
  if (compare != null) {
    const gap = score - compare
    if (gap >= 0.05) tone = 'good'
    else if (gap <= -0.05) tone = 'bad'
  }
  return (
    <span className={`score ${tone}`}>
      <span className="track"><span className="fill" style={{ width: `${p}%` }} /></span>
      <span className="val">{p}%</span>
    </span>
  )
}
