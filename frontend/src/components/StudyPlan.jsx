import { useEffect, useRef, useState } from 'react'
import { api, pct } from '../api.js'

export default function StudyPlan({ analysis, openLine, onTiming }) {
  const [mode, setMode] = useState('budget')
  const [budget, setBudget] = useState(60)
  const [minutesPerPly, setMinutesPerPly] = useState(2)
  const [extraPlies, setExtraPlies] = useState(6)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const timer = useRef(null)

  useEffect(() => {
    clearTimeout(timer.current)
    timer.current = setTimeout(() => {
      setError('')
      api
        .studyPlan(analysis.id, {
          budgetMinutes: mode === 'budget' ? Number(budget) : null,
          minutesPerPly: Number(minutesPerPly),
          extraPlies: Number(extraPlies),
          weakLines: 15,
          minGames: 3,
        })
        .then((res) => {
          setData(res)
          onTiming('Study plan', res)
        })
        .catch((e) => setError(e.message))
    }, 200)
    return () => clearTimeout(timer.current)
  }, [analysis.id, mode, budget, minutesPerPly, extraPlies])

  const r = data?.result

  return (
    <div className="plan">
      <div className="card plan-controls">
        <div className="seg">
          <button className={mode === 'budget' ? 'on' : ''} onClick={() => setMode('budget')}>
            Fit my time
          </button>
          <button className={mode === 'cover' ? 'on' : ''} onClick={() => setMode('cover')}>
            Fix everything
          </button>
        </div>
        {mode === 'budget' && (
          <label className="field slider">
            <span>
              Study time: <strong>{budget} min</strong>
            </span>
            <input type="range" min="10" max="300" step="5" value={budget} onChange={(e) => setBudget(e.target.value)} />
          </label>
        )}
        <label className="field narrow">
          <span>Minutes per move</span>
          <input type="number" min="0.5" max="10" step="0.5" value={minutesPerPly} onChange={(e) => setMinutesPerPly(e.target.value)} />
        </label>
        <label className="field narrow">
          <span>Extra theory moves</span>
          <input type="number" min="0" max="20" value={extraPlies} onChange={(e) => setExtraPlies(e.target.value)} />
        </label>
      </div>
      {error && <p className="error">{error}</p>}
      {r && (
        <>
          <div className="coverage card">
            <div className="coverage-head">
              <span>
                Fixes <strong>{pct(r.coverage)}</strong> of the points you lose in your 15 weakest lines
              </span>
              <span className="muted">
                {r.totalMinutes} min · {r.lines.length} lines
              </span>
            </div>
            <div className="track big">
              <span className="fill good" style={{ width: `${Math.round(r.coverage * 100)}%` }} />
            </div>
            <p className="muted small">
              {r.guarantee}
              {r.measuredRatio != null &&
                (mode === 'budget'
                  ? ` Checked by brute force over all ${2 ** r.candidateLines} combinations of ${r.candidateLines} lines: this plan gets ${pct(r.measuredRatio)} of the best possible.`
                  : ` Checked by brute force over ${r.candidateLines} lines: this plan takes ${r.measuredRatio.toFixed(2)}× the minimum time.`)}
            </p>
          </div>
          <ol className="plan-list">
            {r.lines.map((l, i) => (
              <li key={l.id} className="card">
                <div className="plan-line-head">
                  <span className="step">Line {i + 1}</span>
                  <span className="muted">
                    {l.minutes} min · fixes {l.pointsCovered.toFixed(1)} points
                  </span>
                </div>
                <div className="line">{l.line}</div>
                <div className="chips">
                  {l.fixes.map((f) => (
                    <span key={f} className="chip">
                      {f}
                    </span>
                  ))}
                </div>
                <button onClick={() => openLine(l.moves)}>Walk through it</button>
              </li>
            ))}
          </ol>
          {r.notCovered.length > 0 && (
            <div className="card skip">
              <h4>Skip for now ({r.notCovered.length})</h4>
              <ul>
                {r.notCovered.map((w) => (
                  <li key={w.line}>
                    {w.line} <span className="muted">−{w.pointsLost.toFixed(1)} points, {w.games} games</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </div>
  )
}
