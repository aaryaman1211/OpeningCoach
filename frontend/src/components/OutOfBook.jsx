import { useEffect, useState } from 'react'
import { api, pct } from '../api.js'
import ScoreBar from './ScoreBar.jsx'

export default function OutOfBook({ analysis, openLine, onTiming }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .outOfBook(analysis.id, 12)
      .then((res) => {
        setData(res)
        onTiming('Leaving theory', res)
      })
      .catch((e) => setError(e.message))
  }, [analysis.id])

  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="muted">Loading…</p>
  const r = data.result
  const algo = data.algorithm

  return (
    <div>
      <div className="stat-row">
        <Stat label="Games analysed" value={r.gamesAnalysed} />
        <Stat label="You left theory first" value={r.youLeft} />
        <Stat label="Opponent left first" value={r.opponentLeft} />
        <Stat label="Average exit" value={r.averageExitPly == null ? '—' : `move ${Math.ceil(r.averageExitPly / 2)}`} />
      </div>
      {algo.lookups != null && (
        <p className="muted small">
          {algo.lookups.toLocaleString()} position lookups: {algo.bloomRejected.toLocaleString()} answered “not in book”
          instantly by the Bloom filter, {algo.confirmedInBook.toLocaleString()} confirmed on disk,{' '}
          {algo.falsePositives} Bloom false positives caught by the exact check.
        </p>
      )}
      <h3>Most common places you leave theory</h3>
      <div className="exit-list">
        {r.exits.map((e) => (
          <div key={e.line} className="card exit">
            <div className="exit-main">
              <div className="line">
                {e.line.slice(0, e.line.lastIndexOf(e.leavingMove))}
                <mark>{e.leavingMove}</mark>
              </div>
              <div className="exit-meta">
                <span className={`badge ${e.byYou ? 'warn' : ''}`}>{e.byYou ? 'your move' : "opponent's move"}</span>
                <span>{e.games} games</span>
                <ScoreBar score={e.yourScore} />
              </div>
              {e.strongPlayersPlay.length > 0 && (
                <p className="small">
                  Strong players play{' '}
                  {e.strongPlayersPlay.map((a, i) => (
                    <span key={a.san}>
                      {i > 0 && ', '}
                      <strong>{a.san}</strong> ({a.games}, {pct(a.score)} for you)
                    </span>
                  ))}
                </p>
              )}
            </div>
            <button onClick={() => openLine(e.path.slice(0, -1))}>Open position</button>
          </div>
        ))}
      </div>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div className="stat">
      <span className="label">{label}</span>
      <strong>{value}</strong>
    </div>
  )
}
