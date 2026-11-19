import { useEffect, useState } from 'react'
import { api, pct } from '../api.js'
import PositionPreview from './PositionPreview.jsx'
import ScoreBar from './ScoreBar.jsx'

/** Where games leave the positions strong players have reached. */
export default function OutOfBook({ analysis, openOnBoard, onTiming }) {
  const [data, setData] = useState(null)
  const [selected, setSelected] = useState(0)
  const [error, setError] = useState('')

  useEffect(() => {
    api.outOfBook(analysis.id, 12)
      .then((res) => {
        setData(res)
        onTiming('Leaving theory', res)
      })
      .catch((e) => setError(e.message))
  }, [analysis.id])

  if (error) return <p className="error">{error}</p>
  if (!data) return <div className="skeleton" />
  const r = data.result
  const a = data.algorithm
  const left = r.youLeft + r.opponentLeft
  const sel = r.exits[selected]

  return (
    <div>
      <div className="section-head">
        <div>
          <h2>Where your games leave theory</h2>
          <p className="muted">
            In {left} of {r.gamesAnalysed} games, someone played a move strong players never reached within the first
            10 moves: you first in {r.youLeft}, your opponent first in {r.opponentLeft}.
            {r.averageExitPly != null && ` On average this happens around move ${Math.ceil(r.averageExitPly / 2)}.`}
          </p>
        </div>
      </div>

      {!r.exits.length ? (
        <p className="empty">Your games stayed in theory for the first 10 moves.</p>
      ) : (
        <div className="split">
          <div>
            <table className="list">
              <thead>
                <tr>
                  <th>Line (the new move is last)</th>
                  <th>Played by</th>
                  <th className="num">Games</th>
                  <th className="hide-sm">Your score</th>
                </tr>
              </thead>
              <tbody>
                {r.exits.map((e, i) => (
                  <tr key={e.line} tabIndex={0} className={i === selected ? 'selected' : ''}
                      onClick={() => setSelected(i)} onKeyDown={(ev) => ev.key === 'Enter' && setSelected(i)}>
                    <td className="line">{e.line}</td>
                    <td>{e.byYou ? 'You' : 'Opponent'}</td>
                    <td className="num">{e.games}</td>
                    <td className="hide-sm"><ScoreBar score={e.yourScore} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {a.lookups != null && (
              <details className="how">
                <summary>How this is checked</summary>
                <p>
                  {a.lookups.toLocaleString()} positions were looked up. A Bloom filter in memory answered “never reached”
                  straight away for {a.bloomRejected.toLocaleString()} of them. The other {(a.confirmedInBook + a.falsePositives).toLocaleString()}{' '}
                  were confirmed against the full index on disk, which caught {a.falsePositives} Bloom false positives.
                </p>
              </details>
            )}
          </div>

          {sel && (
            <aside className="preview">
              <PositionPreview
                analysisId={analysis.id}
                path={sel.path.slice(0, -1)}
                orientation={analysis.summary.colour}
                marks={[
                  ...sel.strongPlayersPlay.map((alt) => ({ san: alt.san, color: 'ref' })),
                  { san: sel.leavingMove, color: sel.byYou ? 'you' : 'them' },
                ]}
              />
              <div className="legend">
                <span><i className={sel.byYou ? 'you' : 'them'} />{sel.byYou ? 'your move' : "opponent's move"}</span>
                <span><i className="ref" />strong players’ moves</span>
              </div>
              <div className="caption">
                <p>
                  Here {sel.byYou ? 'you' : 'your opponent'} played <strong className="mono">{sel.leavingMove}</strong> in{' '}
                  {sel.games} {sel.games === 1 ? 'game' : 'games'} (you scored {pct(sel.yourScore)}).
                </p>
                {sel.strongPlayersPlay.length > 0 && (
                  <p>
                    Strong players play{' '}
                    {sel.strongPlayersPlay.map((alt, i) => (
                      <span key={alt.san}>
                        {i > 0 && (i === sel.strongPlayersPlay.length - 1 ? ' or ' : ', ')}
                        <strong className="mono">{alt.san}</strong> <span className="muted">({alt.games} games)</span>
                      </span>
                    ))}
                    .
                  </p>
                )}
              </div>
              <div className="preview-actions">
                <button onClick={() => openOnBoard(sel.path.slice(0, -1))}>Explore this position</button>
              </div>
            </aside>
          )}
        </div>
      )}
    </div>
  )
}
