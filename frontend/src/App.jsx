import { useCallback, useEffect, useState } from 'react'
import { api } from './api.js'
import ImportPanel from './components/ImportPanel.jsx'
import Explorer from './components/Explorer.jsx'
import WeakLines from './components/WeakLines.jsx'
import OutOfBook from './components/OutOfBook.jsx'
import StudyPlan from './components/StudyPlan.jsx'
import Scout from './components/Scout.jsx'
import UnderTheHood from './components/UnderTheHood.jsx'
import LineReplay from './components/LineReplay.jsx'
import ScoreBar from './components/ScoreBar.jsx'

const TABS = [
  { id: 'explore', label: 'Board', icon: '♞' },
  { id: 'weak', label: 'Weak lines', icon: '⚠' },
  { id: 'book', label: 'Leaving theory', icon: '⤳' },
  { id: 'plan', label: 'Study plan', icon: '✎' },
  { id: 'scout', label: 'Scout opponent', icon: '⚔' },
  { id: 'hood', label: 'Under the hood', icon: '⚙' },
]

// Famous openings for the landing board (replayed by the server, shown only as decoration).
const SHOWCASE = [
  { name: 'Italian Game', moves: ['e4', 'e5', 'Nf3', 'Nc6', 'Bc4', 'Bc5', 'c3', 'Nf6', 'd4', 'exd4', 'cxd4', 'Bb4+'] },
  { name: 'Sicilian Najdorf', moves: ['e4', 'c5', 'Nf3', 'd6', 'd4', 'cxd4', 'Nxd4', 'Nf6', 'Nc3', 'a6'] },
  { name: "Queen's Gambit Declined", moves: ['d4', 'd5', 'c4', 'e6', 'Nc3', 'Nf6', 'Bg5', 'Be7', 'e3', 'O-O', 'Nf3', 'Nbd7'] },
  { name: 'Caro-Kann Advance', moves: ['e4', 'c6', 'd4', 'd5', 'e5', 'Bf5', 'Nf3', 'e6', 'Be2', 'c5'] },
  { name: 'Ruy Lopez, Berlin', moves: ['e4', 'e5', 'Nf3', 'Nc6', 'Bb5', 'Nf6', 'O-O', 'Nxe4', 'd4', 'Nd6', 'Bxc6', 'dxc6', 'dxe5', 'Nf5', 'Qxd8+', 'Kxd8'] },
]

export default function App() {
  const [analysis, setAnalysis] = useState(null)
  const [opponent, setOpponent] = useState(null)
  const [busy, setBusy] = useState(false)
  const [tab, setTab] = useState('explore')
  const [path, setPath] = useState([])
  const [timings, setTimings] = useState([])
  const [showImport, setShowImport] = useState(false)
  const [showcase, setShowcase] = useState(0)

  const onTiming = useCallback((label, res) => {
    setTimings((t) => [{ label, millis: res.millis, algorithm: res.algorithm }, ...t].slice(0, 14))
  }, [])

  async function onImport(req) {
    setBusy(true)
    try {
      const created = await api.createAnalysis(req)
      setAnalysis(created)
      setOpponent(null)
      setTimings((t) => [{ label: 'Import + build trie', millis: created.millis, algorithm: { name: 'PGN replay + trie insert' } }, ...t])
      setPath([])
      setTab('explore')
      setShowImport(false)
    } finally {
      setBusy(false)
    }
  }

  function openLine(moves) {
    setPath(moves)
    setTab('explore')
  }

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [tab])

  const s = analysis?.summary
  const r = s?.importReport
  const skipped = r ? r.nonStandard + r.unfinished + r.illegal + r.noMoves : 0

  return (
    <div className="app">
      <header className="top">
        <div className="brand">
          <span className="logo" aria-hidden="true">♞</span>
          <span className="brand-name">Opening<span>Coach</span></span>
        </div>
        {analysis && (
          <div className="player-chip">
            <span className="dot" />
            <span>
              <strong>{s.player}</strong> · {s.colour === 'white' ? '♔' : '♚'} {s.games} games
            </span>
            <button onClick={() => setShowImport((v) => !v)}>{showImport ? 'Close' : 'Change'}</button>
          </div>
        )}
      </header>

      {!analysis && (
        <section className="hero fade-in">
          <div className="hero-copy">
            <h1>
              Know your openings.<br />
              <span className="grad">Fix the leaks. Scout your rival.</span>
            </h1>
            <p className="lede">
              OpeningCoach reads your games and builds a tree of every opening you play. It compares each position
              with 21,925 games between players rated 1700+, shows you where you lose points, and plans exactly what to
              study.
            </p>
            <ImportPanel onImport={onImport} busy={busy} compact />
            <div className="hero-feats">
              <span>♞ Interactive board</span>
              <span>⚠ Weak-line finder</span>
              <span>✎ Time-boxed study plan</span>
              <span>⚔ Opponent prep</span>
            </div>
          </div>
          <div className="hero-board">
            <div className="hero-glow" />
            <LineReplay
              moves={SHOWCASE[showcase].moves}
              interval={700}
              controls={false}
              onDone={() => setShowcase((i) => (i + 1) % SHOWCASE.length)}
            />
            <div className="hero-caption">
              <span className="label">Now playing</span>
              <strong>{SHOWCASE[showcase].name}</strong>
            </div>
          </div>
        </section>
      )}

      {analysis && (
        <main className="fade-in">
          {showImport && (
            <div className="drawer fade-in">
              <ImportPanel onImport={onImport} busy={busy} compact />
            </div>
          )}

          <section className="summary">
            <Stat label="Games analysed" value={s.games} sub={skipped ? `${skipped} skipped` : `as ${s.colour}`} />
            <div className="stat card">
              <span className="label">Score · {s.wins}W {s.draws}D {s.losses}L</span>
              <ScoreBar score={s.score} />
            </div>
            <Stat label="Opening tree" value={s.trieNodes} sub={`lines, first ${s.maxPly / 2} moves`} />
            <Stat label="Opponent" value={opponent ? opponent.summary.player : '—'} sub={opponent ? `${opponent.summary.games} games scouted` : 'not scouted yet'} raw />
          </section>

          <nav className="tabs" role="tablist">
            {TABS.map((t) => (
              <button key={t.id} role="tab" aria-selected={tab === t.id} className={tab === t.id ? 'on' : ''} onClick={() => setTab(t.id)}>
                <span className="ti" aria-hidden="true">{t.icon}</span>
                {t.label}
              </button>
            ))}
          </nav>

          <section className="panel fade-in" key={tab}>
            {tab === 'explore' && <Explorer analysis={analysis} path={path} setPath={setPath} onTiming={onTiming} />}
            {tab === 'weak' && <WeakLines analysis={analysis} openLine={openLine} onTiming={onTiming} />}
            {tab === 'book' && <OutOfBook analysis={analysis} openLine={openLine} onTiming={onTiming} />}
            {tab === 'plan' && <StudyPlan analysis={analysis} openLine={openLine} onTiming={onTiming} />}
            {tab === 'scout' && <Scout analysis={analysis} opponent={opponent} setOpponent={setOpponent} onTiming={onTiming} />}
            {tab === 'hood' && <UnderTheHood timings={timings} />}
          </section>
        </main>
      )}

      <footer>
        Analyses finished games only. Using an opening explorer during a game breaks Lichess and Chess.com fair-play
        rules. Pieces: Colin M.L. Burnett (CC BY-SA 3.0). Reference games: Lichess open database (CC0). CIA 3 Advanced
        Algorithm project.
      </footer>
    </div>
  )
}

function Stat({ label, value, sub, raw }) {
  const [shown, setShown] = useState(raw || typeof value !== 'number' ? value : 0)
  useEffect(() => {
    if (raw || typeof value !== 'number') {
      setShown(value)
      return
    }
    let frame
    const start = performance.now()
    const tick = (t) => {
      const k = Math.min(1, (t - start) / 700)
      setShown(Math.round(value * (1 - Math.pow(1 - k, 3))))
      if (k < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])
  return (
    <div className="stat card">
      <span className="label">{label}</span>
      <strong className="big">{typeof shown === 'number' ? shown.toLocaleString() : shown}</strong>
      {sub && <span className="muted small">{sub}</span>}
    </div>
  )
}
