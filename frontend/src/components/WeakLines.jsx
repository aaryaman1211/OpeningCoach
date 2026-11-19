import { useEffect, useState } from 'react'
import { api } from '../api.js'
import ScoreBar from './ScoreBar.jsx'

export default function WeakLines({ analysis, openLine, onTiming }) {
  const [data, setData] = useState(null)
  const [minGames, setMinGames] = useState(3)
  const [error, setError] = useState('')

  useEffect(() => {
    setError('')
    api
      .weakLines(analysis.id, 10, minGames)
      .then((res) => {
        setData(res)
        onTiming('Weak lines', res)
      })
      .catch((e) => setError(e.message))
  }, [analysis.id, minGames])

  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="muted">Loading…</p>
  const { lines, candidates, redundantSkipped } = data.result

  return (
    <div>
      <div className="section-head">
        <div>
          <h3>Where you lose the most points</h3>
          <p className="muted small">
            Points lost = games × (strong players’ score − your score) from the same position. Picked from {candidates}{' '}
            candidate lines with randomized Quickselect; {redundantSkipped} broader lines were hidden because a deeper
            line explains them.
          </p>
        </div>
        <label className="field narrow">
          <span>Min games</span>
          <input type="number" min="1" max="100" value={minGames} onChange={(e) => setMinGames(Number(e.target.value) || 1)} />
        </label>
      </div>
      {!lines.length && <p className="muted">No line loses points compared with strong players. Nice.</p>}
      <ol className="weak-list">
        {lines.map((l, i) => (
          <li key={l.line} className="card weak">
            <div className="rank">{i + 1}</div>
            <div className="weak-body">
              <div className="line">{l.line}</div>
              <div className="weak-stats">
                <span>
                  <span className="label">You · {l.games} games</span>
                  <ScoreBar score={l.yourScore} />
                </span>
                <span>
                  <span className="label">Expected (strong players)</span>
                  <ScoreBar score={l.expectedScore} muted />
                </span>
                <span className="lost">
                  −{l.pointsLost.toFixed(1)} <span className="label">points</span>
                </span>
              </div>
              <p className="muted small">
                {l.yourMoveLast ? 'Your move ends this line.' : "Your opponent's move ends this line: review your reply."}
              </p>
            </div>
            <button onClick={() => openLine(l.path)}>Open in explorer</button>
          </li>
        ))}
      </ol>
    </div>
  )
}
