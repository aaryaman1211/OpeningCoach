import { useEffect, useRef, useState } from 'react'
import { api, pct } from '../api.js'
import LineReplay from './LineReplay.jsx'
import PageHeader from './PageHeader.jsx'

/** Which lines to study: within a time budget, or enough to cover every weak line. */
export default function StudyPlan({ analysis, openOnBoard, onTiming }) {
  const [mode, setMode] = useState('budget')
  const [budget, setBudget] = useState(60)
  const [minutesPerPly, setMinutesPerPly] = useState(2)
  const [data, setData] = useState(null)
  const [selected, setSelected] = useState(0)
  const [error, setError] = useState('')
  const timer = useRef(null)

  const mpp = Number(minutesPerPly)
  const mppValid = minutesPerPly !== '' && mpp >= 0.5 && mpp <= 10

  useEffect(() => {
    clearTimeout(timer.current)
    if (!mppValid) return undefined
    timer.current = setTimeout(() => {
      setError('')
      api.studyPlan(analysis.id, {
        budgetMinutes: mode === 'budget' ? Number(budget) : null,
        minutesPerPly: Number(minutesPerPly),
        extraPlies: 6,
        weakLines: 15,
        minGames: 3,
      })
        .then((res) => {
          setData(res)
          setSelected(0)
          onTiming('Study plan', res)
        })
        .catch((e) => setError(e.message))
    }, 250)
    return () => clearTimeout(timer.current)
  }, [analysis.id, mode, budget, minutesPerPly])

  const r = data?.result
  const sel = r?.lines[selected]

  return (
    <div>
      <PageHeader kicker="Study plan" title="Practice makes" accent="pawn-fect.">
        Each study line is one of your weak lines plus strong players’ usual continuation. One line can fix several weak
        spots, so the plan picks the lines that fix the most for your time.
      </PageHeader>

      <div className="plan-controls">
        <div className="field">
          <span>Goal</span>
          <div className="choice">
            <button className={mode === 'budget' ? 'on' : ''} onClick={() => setMode('budget')}>Fit my time</button>
            <button className={mode === 'cover' ? 'on' : ''} onClick={() => setMode('cover')}>Fix everything</button>
          </div>
        </div>
        {mode === 'budget' && (
          <label className="field slider">
            <span>Time available: <strong style={{ color: 'var(--ink)' }}>{budget} minutes</strong></span>
            <input type="range" min="10" max="300" step="5" value={budget} onChange={(e) => setBudget(e.target.value)} />
          </label>
        )}
        <label className="field narrow">
          <span>Minutes per move</span>
          <input type="number" min="0.5" max="10" step="0.5" value={minutesPerPly} onChange={(e) => setMinutesPerPly(e.target.value)}
                 aria-invalid={!mppValid} aria-describedby="mpp-hint" />
          {!mppValid && <span id="mpp-hint" className="field-hint">Use 0.5 to 10</span>}
        </label>
      </div>

      {error && <p className="error">{error}</p>}
      {!r && !error && <div className="skeleton" />}
      {r && (
        <>
          <div className="plan-summary">
            <p>
              {r.lines.length ? (
                <>
                  Study <strong>{r.lines.length} {r.lines.length === 1 ? 'line' : 'lines'}</strong> ({r.totalMinutes} minutes) to fix{' '}
                  <strong>{pct(r.coverage)}</strong> of the points you lose in your {r.weakLinesConsidered} weakest lines.
                </>
              ) : (
                <>No study line fits in {budget} minutes. Allow more time or fewer minutes per move.</>
              )}
            </p>
            <div className="meter" aria-hidden="true"><span style={{ width: `${Math.round(r.coverage * 100)}%` }} /></div>
          </div>

          {r.lines.length > 0 && (
            <div className="split">
              <div>
                <table className="list">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Line to study</th>
                      <th className="num">Minutes</th>
                      <th className="num hide-sm">Fixes</th>
                    </tr>
                  </thead>
                  <tbody>
                    {r.lines.map((l, i) => (
                      <tr key={l.id} tabIndex={0} className={i === selected ? 'selected' : ''}
                          onClick={() => setSelected(i)} onKeyDown={(e) => e.key === 'Enter' && setSelected(i)}>
                        <td className="num">{i + 1}</td>
                        <td className="line">{l.line}</td>
                        <td className="num">{l.minutes}</td>
                        <td className="num hide-sm">{l.fixes.length} {l.fixes.length === 1 ? 'line' : 'lines'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {r.notCovered.length > 0 && (
                  <div className="box" style={{ marginTop: 16 }}>
                    <h3 style={{ marginBottom: 8 }}>Skip for now</h3>
                    <table className="plain">
                      <tbody>
                        {r.notCovered.map((w) => (
                          <tr key={w.line}>
                            <td className="line">{w.line}</td>
                            <td className="num muted">{w.games} games</td>
                            <td className="num muted">−{w.pointsLost.toFixed(1)} pts</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                <details className="how">
                  <summary>How the plan is chosen</summary>
                  <p>{r.guarantee}</p>
                  {r.measuredRatio != null && (
                    <p>
                      With {r.candidateLines} candidate lines, every combination was also checked by brute force:
                      {mode === 'budget'
                        ? ` this plan reaches ${pct(r.measuredRatio)} of the best possible.`
                        : ` this plan takes ${r.measuredRatio.toFixed(2)}× the shortest possible time.`}
                    </p>
                  )}
                </details>
              </div>

              {sel && (
                <aside className="preview">
                  <LineReplay moves={sel.moves} orientation={analysis.summary.colour} interval={800} autoPlay={false} startAt="end" />
                  <div className="caption">
                    <p>
                      Line {selected + 1}: {sel.plies} moves, about {sel.minutes} minutes. Fixes{' '}
                      {sel.pointsCovered.toFixed(1)} lost points from:
                    </p>
                    <p className="fixes mono">{sel.fixes.join(' · ')}</p>
                  </div>
                  <div className="preview-actions">
                    <button onClick={() => openOnBoard(sel.moves)}>Open on board</button>
                  </div>
                </aside>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
