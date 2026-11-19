import { useCallback, useState } from 'react'
import { api } from './api.js'
import ImportPanel from './components/ImportPanel.jsx'
import Explorer from './components/Explorer.jsx'
import WeakLines from './components/WeakLines.jsx'
import OutOfBook from './components/OutOfBook.jsx'
import StudyPlan from './components/StudyPlan.jsx'
import UnderTheHood from './components/UnderTheHood.jsx'
import ScoreBar from './components/ScoreBar.jsx'

const TABS = [
  { id: 'weak', label: 'Weak lines' },
  { id: 'book', label: 'Leaving theory' },
  { id: 'plan', label: 'Study plan' },
  { id: 'explore', label: 'Explorer' },
  { id: 'hood', label: 'Under the hood' },
]

export default function App() {
  const [analysis, setAnalysis] = useState(null)
  const [busy, setBusy] = useState(false)
  const [tab, setTab] = useState('weak')
  const [path, setPath] = useState([])
  const [timings, setTimings] = useState([])

  const onTiming = useCallback((label, res) => {
    setTimings((t) => [{ label, millis: res.millis, algorithm: res.algorithm }, ...t].slice(0, 12))
  }, [])

  async function onImport(req) {
    setBusy(true)
    try {
      const created = await api.createAnalysis(req)
      setAnalysis(created)
      setTimings((t) => [{ label: 'Import + build trie', millis: created.millis, algorithm: { name: 'PGN replay + trie insert' } }, ...t])
      setPath([])
      setTab('weak')
    } finally {
      setBusy(false)
    }
  }

  function openLine(moves) {
    setPath(moves)
    setTab('explore')
  }

  const s = analysis?.summary
  const r = s?.importReport
  const skipped = r ? r.nonStandard + r.unfinished + r.illegal + r.noMoves : 0

  return (
    <div className="app">
      <header className="top">
        <div className="brand">
          <span className="logo">♞</span>
          <div>
            <h1>OpeningCoach</h1>
            <p>Find where your openings go wrong, then study only what matters.</p>
          </div>
        </div>
      </header>

      <main>
        <ImportPanel onImport={onImport} busy={busy} compact={!!analysis} />

        {analysis && (
          <>
            <section className="summary">
              <div className="stat">
                <span className="label">Player</span>
                <strong>
                  {s.player} · {s.colour === 'white' ? '♔ White' : '♚ Black'}
                </strong>
              </div>
              <div className="stat">
                <span className="label">Games analysed</span>
                <strong>{s.games}</strong>
                {skipped > 0 && <span className="muted small">{skipped} skipped (variants, unfinished or illegal)</span>}
              </div>
              <div className="stat">
                <span className="label">
                  Score · {s.wins}W {s.draws}D {s.losses}L
                </span>
                <ScoreBar score={s.score} />
              </div>
              <div className="stat">
                <span className="label">Opening tree</span>
                <strong>{s.trieNodes.toLocaleString()} lines</strong>
                <span className="muted small">first {s.maxPly / 2} moves</span>
              </div>
            </section>

            <nav className="tabs" role="tablist">
              {TABS.map((t) => (
                <button key={t.id} role="tab" aria-selected={tab === t.id} className={tab === t.id ? 'on' : ''}
                        onClick={() => setTab(t.id)}>
                  {t.label}
                </button>
              ))}
            </nav>

            <section className="panel">
              {tab === 'weak' && <WeakLines analysis={analysis} openLine={openLine} onTiming={onTiming} />}
              {tab === 'book' && <OutOfBook analysis={analysis} openLine={openLine} onTiming={onTiming} />}
              {tab === 'plan' && <StudyPlan analysis={analysis} openLine={openLine} onTiming={onTiming} />}
              {tab === 'explore' && <Explorer analysis={analysis} path={path} setPath={setPath} onTiming={onTiming} />}
              {tab === 'hood' && <UnderTheHood timings={timings} />}
            </section>
          </>
        )}
      </main>

      <footer>
        Analyses finished games only. Using an opening explorer during a game breaks Lichess and Chess.com fair-play
        rules. CIA 3 Advanced Algorithm project.
      </footer>
    </div>
  )
}
