import { useCallback, useEffect, useState } from 'react'
import { api, pct } from './api.js'
import { applyTheme, initialTheme } from './theme.js'
import ImportPanel from './components/ImportPanel.jsx'
import StartScreen from './components/StartScreen.jsx'
import Explorer from './components/Explorer.jsx'
import WeakLines from './components/WeakLines.jsx'
import OutOfBook from './components/OutOfBook.jsx'
import StudyPlan from './components/StudyPlan.jsx'
import Scout from './components/Scout.jsx'
import UnderTheHood from './components/UnderTheHood.jsx'

const TABS = [
  { id: 'board', label: 'Board' },
  { id: 'weak', label: 'Weak lines' },
  { id: 'book', label: 'Leaving theory' },
  { id: 'plan', label: 'Study plan' },
  { id: 'opponent', label: 'Opponent' },
  { id: 'how', label: 'How it works' },
]

export default function App() {
  const [theme, setTheme] = useState(initialTheme)
  const [analysis, setAnalysis] = useState(null)
  const [opponent, setOpponent] = useState(null)
  const [tab, setTab] = useState('board')
  const [path, setPath] = useState([])
  const [timings, setTimings] = useState([])
  const [changing, setChanging] = useState(false)
  const [scoutNext, setScoutNext] = useState(false)
  const [showHow, setShowHow] = useState(false)

  useEffect(() => applyTheme(theme), [theme])

  const onTiming = useCallback((label, res) => {
    setTimings((t) => [{ label, millis: res.millis, algorithm: res.algorithm }, ...t].slice(0, 12))
  }, [])

  async function onImport(req) {
    const created = await api.createAnalysis(req)
    setAnalysis(created)
    setOpponent(null)
    setTimings((t) => [{ label: 'Import games', millis: created.millis, algorithm: { name: 'Replay games and build the opening tree' } }, ...t])
    setPath([])
    setTab(scoutNext ? 'opponent' : 'board')
    setScoutNext(false)
    setChanging(false)
    setShowHow(false)
  }

  function openOnBoard(moves) {
    setPath(moves)
    setTab('board')
  }

  const s = analysis?.summary
  const r = s?.importReport
  const skipped = r ? r.nonStandard + r.unfinished + r.illegal + r.noMoves : 0
  const dark = theme === 'dark'

  return (
    <div className={`app ${analysis ? '' : 'is-start'}`}>
      <header className="top">
        <button className="logo" onClick={() => { setShowHow(false); if (analysis) setTab('board') }} aria-label="OpeningCoach home">
          <img src={dark ? '/pieces/wN.svg' : '/pieces/bN.svg'} alt="" />
          <span className="logo-text">Opening<span>Coach</span></span>
        </button>
        <span className="spacer" />
        {analysis ? (
          <>
            <span className="who">
              <strong>{s.player}</strong> as {s.colour} · {s.games} games · scored {pct(s.score)}
              {skipped > 0 && ` · ${skipped} skipped`}
            </span>
            <button onClick={() => setChanging((c) => !c)}>{changing ? 'Cancel' : 'Change player'}</button>
          </>
        ) : (
          <button className="nav-link" onClick={() => setShowHow((v) => !v)}>{showHow ? 'Back' : 'How it works'}</button>
        )}
        <button className="theme-toggle" onClick={() => setTheme(dark ? 'light' : 'dark')}
                aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'} title={dark ? 'Light mode' : 'Dark mode'}>
          {dark ? (
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
          ) : (
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 14.5A8 8 0 019.5 4a8 8 0 1010.5 10.5z" /></svg>
          )}
        </button>
      </header>

      {!analysis && !showHow && <StartScreen onImport={onImport} onScoutAfter={() => setScoutNext(true)} />}
      {!analysis && showHow && (
        <main className="page">
          <UnderTheHood timings={timings} />
        </main>
      )}

      {analysis && (
        <main className="page">
          {changing && (
            <div style={{ maxWidth: 560, marginBottom: 20 }}>
              <ImportPanel onImport={onImport} onCancel={() => setChanging(false)} />
            </div>
          )}

          <nav className="tabs" role="tablist">
            {TABS.map((t) => (
              <button key={t.id} role="tab" aria-selected={tab === t.id} className={tab === t.id ? 'on' : ''} onClick={() => setTab(t.id)}>
                {t.label}
              </button>
            ))}
          </nav>

          {tab === 'board' && <Explorer analysis={analysis} path={path} setPath={setPath} onTiming={onTiming} />}
          {tab === 'weak' && <WeakLines analysis={analysis} openOnBoard={openOnBoard} onTiming={onTiming} />}
          {tab === 'book' && <OutOfBook analysis={analysis} openOnBoard={openOnBoard} onTiming={onTiming} />}
          {tab === 'plan' && <StudyPlan analysis={analysis} openOnBoard={openOnBoard} onTiming={onTiming} />}
          {tab === 'opponent' && <Scout analysis={analysis} opponent={opponent} setOpponent={setOpponent} onTiming={onTiming} />}
          {tab === 'how' && <UnderTheHood timings={timings} />}
        </main>
      )}

      <footer>
        Only finished games are analysed. Using an opening explorer during a game breaks Lichess and Chess.com fair-play
        rules. Reference games: Lichess open database (CC0). Pieces: Colin M.L. Burnett (CC BY-SA 3.0).
      </footer>
    </div>
  )
}
