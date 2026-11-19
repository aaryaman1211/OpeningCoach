import { useEffect, useState } from 'react'
import { api, pct } from '../api.js'
import LineReplay from './LineReplay.jsx'
import ScoreBar from './ScoreBar.jsx'

/** Lines where the player loses the most points compared with strong players. */
export default function WeakLines({ analysis, openOnBoard, onTiming, who = 'you', name }) {
  const [data, setData] = useState(null)
  const [minGames, setMinGames] = useState(3)
  const [selected, setSelected] = useState(0)
  const [error, setError] = useState('')
  const them = who === 'them'

  useEffect(() => {
    setError('')
    api.weakLines(analysis.id, 10, minGames)
      .then((res) => {
        setData(res)
        setSelected(0)
        onTiming?.(them ? 'Opponent weak lines' : 'Weak lines', res)
      })
      .catch((e) => setError(e.message))
  }, [analysis.id, minGames])

  if (error) return <p className="error">{error}</p>
  if (!data) return <div className="skeleton" />
  const { lines, candidates, redundantSkipped } = data.result
  const sel = lines[selected]
  const subject = them ? name : 'You'

  return (
    <div>
      <div className="section-head">
        <div>
          <h2>{them ? `Where ${name} loses the most` : 'Where you lose the most'}</h2>
          <p className="muted">
            Lines where {them ? 'they score' : 'you score'} worse than strong players did from the same position,
            weighted by how often {them ? 'they' : 'you'} played them.
          </p>
        </div>
        <label className="field narrow">
          <span>Played at least</span>
          <input type="number" min="1" max="100" value={minGames} onChange={(e) => setMinGames(Number(e.target.value) || 1)} aria-label="Minimum games" />
        </label>
      </div>

      {!lines.length ? (
        <p className="empty">No line where {them ? 'they lose' : 'you lose'} points compared with strong players. Try lowering the minimum games.</p>
      ) : (
        <div className="split">
          <div>
            <table className="list">
              <thead>
                <tr>
                  <th>Line</th>
                  <th className="num">Games</th>
                  <th>{them ? 'Their' : 'Your'} score</th>
                  <th className="hide-sm">Strong players</th>
                  <th className="num">Points lost</th>
                </tr>
              </thead>
              <tbody>
                {lines.map((l, i) => (
                  <tr key={l.line} tabIndex={0} className={i === selected ? 'selected' : ''}
                      onClick={() => setSelected(i)} onKeyDown={(e) => e.key === 'Enter' && setSelected(i)}>
                    <td className="line">{l.line}</td>
                    <td className="num">{l.games}</td>
                    <td><ScoreBar score={l.yourScore} compare={l.expectedScore} /></td>
                    <td className="hide-sm">{pct(l.expectedScore)}</td>
                    <td className="num">{l.pointsLost.toFixed(1)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <details className="how">
              <summary>How this list is made</summary>
              <p>
                Points lost = games × (strong players’ score − {them ? 'their' : 'your'} score). A draw counts as half a
                point. The top 10 of {candidates} lines are picked with randomized Quickselect. {redundantSkipped}{' '}
                shorter lines are hidden because a longer line in the list explains the same losses.
              </p>
            </details>
          </div>

          {sel && (
            <aside className="preview">
              <LineReplay moves={sel.path} orientation={analysis.summary.colour} startAt="end" autoPlay={false} interval={700} />
              <div className="caption">
                <p>
                  {subject} reached this position <strong>{sel.games}</strong> times and scored <strong>{pct(sel.yourScore)}</strong>.
                  Strong players score <strong>{pct(sel.expectedScore)}</strong> from here, so {them ? 'they lose' : 'you lose'} about{' '}
                  <strong>{sel.pointsLost.toFixed(1)}</strong> points.
                </p>
                <p className="muted small">
                  {sel.yourMoveLast
                    ? `The last move was ${them ? 'theirs' : 'yours'}: check whether a different move works better.`
                    : them
                      ? 'The last move was yours: steer the game here.'
                      : "The last move was your opponent's: review your best reply."}
                </p>
              </div>
              <div className="preview-actions">
                <button onClick={() => openOnBoard(sel.path)}>Explore from here</button>
              </div>
            </aside>
          )}
        </div>
      )}
    </div>
  )
}
