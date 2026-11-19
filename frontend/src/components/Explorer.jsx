import { useEffect, useState } from 'react'
import { api, pct } from '../api.js'
import Board from './Board.jsx'
import ScoreBar from './ScoreBar.jsx'

export default function Explorer({ analysis, path, setPath, onTiming }) {
  const [view, setView] = useState(null)
  const [error, setError] = useState('')
  const white = analysis.summary.colour === 'white'

  useEffect(() => {
    let live = true
    setError('')
    api
      .tree(analysis.id, path)
      .then((res) => {
        if (!live) return
        setView(res.result)
        onTiming('Explorer', res)
      })
      .catch((e) => live && setError(e.message))
    return () => {
      live = false
    }
  }, [analysis.id, path.join(',')])

  if (error) return <p className="error">{error}</p>
  if (!view) return <p className="muted">Loading…</p>

  return (
    <div className="explorer">
      <div className="board-col">
        <Board fen={view.fen} flipped={!white} />
        <div className="crumbs">
          <button className="link" onClick={() => setPath([])} disabled={!path.length}>
            Start
          </button>
          {path.map((m, i) => (
            <button key={i} className="link" onClick={() => setPath(path.slice(0, i + 1))}>
              {i % 2 === 0 ? `${i / 2 + 1}.` : ''}
              {m}
            </button>
          ))}
        </div>
        <div className="nav-row">
          <button onClick={() => setPath(path.slice(0, -1))} disabled={!path.length}>
            ← Back
          </button>
          <span className="muted small">{view.yourTurn ? 'Your move' : "Opponent's move"}</span>
        </div>
      </div>
      <div className="table-col">
        <div className="position-stats">
          <div>
            <span className="label">You reached this position</span>
            <strong>{view.yourGames} games</strong>
            <ScoreBar score={view.yourScore} />
          </div>
          <div>
            <span className="label">Players rated 1700+</span>
            <strong>{view.refGames ? `${view.refGames} games` : 'never reached it'}</strong>
            <ScoreBar score={view.refScore} muted />
          </div>
        </div>
        <table className="moves">
          <thead>
            <tr>
              <th>Move</th>
              <th>Your games</th>
              <th>Your score</th>
              <th>Strong players</th>
              <th>Their score</th>
            </tr>
          </thead>
          <tbody>
            {view.moves.map((m) => (
              <tr key={m.san} onClick={() => setPath([...path, m.san])} tabIndex={0}
                  onKeyDown={(e) => e.key === 'Enter' && setPath([...path, m.san])}>
                <td className="san">
                  {m.san}
                  {!m.inBook && <span className="badge warn">out of book</span>}
                </td>
                <td>{m.yourGames || '—'}</td>
                <td>{m.yourGames ? <ScoreBar score={m.yourScore} /> : '—'}</td>
                <td>{m.refGames ? `${pct(m.refShare)} · ${m.refGames}` : '—'}</td>
                <td>{m.refGames ? <ScoreBar score={m.refScore} muted /> : '—'}</td>
              </tr>
            ))}
            {!view.moves.length && (
              <tr>
                <td colSpan="5" className="muted">
                  No games continue from here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <p className="muted small">
          Scores are from your side: 100% = always won. “Out of book” = strong players never reached the position
          after this move. Every “maybe in book” answer from the Bloom filter is confirmed on disk.
        </p>
      </div>
    </div>
  )
}
