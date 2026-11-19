import { useEffect, useState } from 'react'
import { api } from '../api.js'
import ScoreBar from './ScoreBar.jsx'

export default function WeakLines({ analysis, openLine, onTiming, who = 'you', name }) {
  const [data, setData] = useState(null)
  const [minGames, setMinGames] = useState(3)
  const [error, setError] = useState('')
  const them = who === 'them'

  useEffect(() => {
    setError('')
    api
      .weakLines(analysis.id, 10, minGames)
      .then((res) => {
        setData(res)
        onTiming?.(them ? 'Their weak lines' : 'Weak lines', res)
      })
      .catch((e) => setError(e.message))
  }, [analysis.id, minGames])

  if (error) return <p className="error">{error}</p>
  if (!data) return <div className="skeleton tall" />
  const { lines, candidates, redundantSkipped } = data.result
  const maxLost = Math.max(1, ...lines.map((l) => l.pointsLost))

  return (
    <div className="fade-in">
      <div className="section-head">
        <div>
          <h3>{them ? `Where ${name || 'they'} lose the most points` : 'Where you lose the most points'}</h3>
          <p className="muted small">
            Points lost = games × (strong players’ score − {them ? 'their' : 'your'} score) from the same position.
            Picked from {candidates} candidate lines with randomized Quickselect; {redundantSkipped} broader lines hidden
            because a deeper line explains them.
          </p>
        </div>
        <label className="field narrow">
          <span>Min games</span>
          <input type="number" min="1" max="100" value={minGames} onChange={(e) => setMinGames(Number(e.target.value) || 1)} />
        </label>
      </div>
      {!lines.length && <p className="muted">No line loses points compared with strong players.</p>}
      <ol className="weak-list">
        {lines.map((l, i) => (
          <li key={l.line} className="card weak" style={{ animationDelay: `${i * 40}ms` }}>
            <div className="rank">{i + 1}</div>
            <div className="weak-body">
              <div className="line">{l.line}</div>
              <div className="weak-stats">
                <span>
                  <span className="label">{them ? 'They' : 'You'} · {l.games} games</span>
                  <ScoreBar score={l.yourScore} />
                </span>
                <span>
                  <span className="label">Expected (strong players)</span>
                  <ScoreBar score={l.expectedScore} muted />
                </span>
              </div>
              <span className="lost-bar"><span style={{ width: `${(l.pointsLost / maxLost) * 100}%` }} /></span>
              <p className="muted small">
                {them
                  ? (l.yourMoveLast ? 'Their move ends this line.' : 'Your move ends this line: steer them here.')
                  : (l.yourMoveLast ? 'Your move ends this line.' : "Your opponent's move ends this line: review your reply.")}
              </p>
            </div>
            <div className="weak-side">
              <span className="lost">−{l.pointsLost.toFixed(1)}</span>
              <span className="label">points</span>
              <button onClick={() => openLine(l.path)}>Show on board</button>
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}
