import { useCallback, useEffect, useRef, useState } from 'react'
import { api, onConnection, setAnalysisRecovery } from './api.js'
import { applyTheme, initialTheme } from './theme.js'
import ImportPanel from './components/ImportPanel.jsx'
import StartScreen from './components/StartScreen.jsx'
import Explorer from './components/Explorer.jsx'
import WeakLines from './components/WeakLines.jsx'
import OutOfBook from './components/OutOfBook.jsx'
import StudyPlan from './components/StudyPlan.jsx'
import Scout from './components/Scout.jsx'
import Greats from './components/Greats.jsx'
import UnderTheHood from './components/UnderTheHood.jsx'

const TABS = [
  { id: 'board', label: 'Board' },
  { id: 'weak', label: 'Weak lines' },
  { id: 'book', label: 'Leaving theory' },
  { id: 'plan', label: 'Study plan' },
  { id: 'opponent', label: 'Opponent' },
  { id: 'greats', label: 'The greats' },
  { id: 'how', label: 'How it works' },
]

const other = (side) => (side === 'white' ? 'black' : 'white')

export default function App() {
  const [theme, setTheme] = useState(initialTheme)
  // one import analyses both colours: pair = { white: analysis, black: analysis }
  const [pair, setPair] = useState(null)
  const [oppPair, setOppPair] = useState(null)
  const [side, setSide] = useState('white')
  const [tab, setTab] = useState('board')
  const [path, setPath] = useState([])
  const [timings, setTimings] = useState([])
  const [changing, setChanging] = useState(false)
  const [scoutNext, setScoutNext] = useState(false)
  const [startView, setStartView] = useState('home') // home | how | greats (on the start screen)
  const [home, setHome] = useState(false) // games are loaded but the start screen is showing
  const [online, setOnline] = useState(true)
  const [notice, setNotice] = useState('')

  // the requests behind the current analyses, so they can be replayed after a server restart
  const ownReq = useRef(null)
  const oppReq = useRef(null)
  const pairs = useRef({ own: null, opp: null })
  pairs.current = { own: pair, opp: oppPair }

  useEffect(() => applyTheme(theme), [theme])
  useEffect(() => onConnection(setOnline), [])

  useEffect(() => {
    // The server keeps analyses in memory. If it restarted, re-import the same games quietly
    // and hand back the new id for whichever colour was asked for.
    setAnalysisRecovery(async (oldId) => {
      for (const which of ['own', 'opp']) {
        const p = pairs.current[which]
        const req = which === 'own' ? ownReq.current : oppReq.current
        if (!p || !req) continue
        const colour = p.white.id === oldId ? 'white' : p.black.id === oldId ? 'black' : null
        if (!colour) continue
        try {
          const fresh = await api.createPair(req)
          if (which === 'own') setPair(fresh)
          else setOppPair(fresh)
          setNotice('The server restarted, so your games were reloaded.')
          setTimeout(() => setNotice(''), 5000)
          return fresh[colour].id
        } catch {
          return null
        }
      }
      return null
    })
    return () => setAnalysisRecovery(null)
  }, [])

  const onTiming = useCallback((label, res) => {
    setTimings((t) => [{ label, millis: res.millis, algorithm: res.algorithm }, ...t].slice(0, 12))
  }, [])

  async function onImport(req) {
    const created = await api.createPair(req)
    ownReq.current = req
    setPair(created)
    setOppPair(null)
    setSide(created.white.summary.games >= created.black.summary.games ? 'white' : 'black')
    setTimings((t) => [{ label: 'Import games (both colours)', millis: created.millis, algorithm: { name: 'One download, two opening trees' } }, ...t])
    setPath([])
    setTab(scoutNext ? 'opponent' : 'board')
    setScoutNext(false)
    setChanging(false)
    setStartView('home')
    setHome(false)
  }

  async function loadOpponent(req) {
    const created = await api.createPair(req)
    oppReq.current = req
    setOppPair(created)
    return created
  }

  function switchSide(next) {
    if (next === side) return
    setSide(next)
    setPath([])
  }

  function openOnBoard(moves) {
    setPath(moves)
    setTab('board')
  }

  const analysis = pair ? pair[side] : null
  const opponent = oppPair ? oppPair[other(side)] : null
  const dark = theme === 'dark'
  const player = pair?.white.summary.player
  const onStart = !pair || home

  return (
    <div className={`app ${onStart ? 'is-start' : ''}`}>
      <header className="top">
        <button className="logo" onClick={() => { setStartView('home'); setChanging(false); if (pair) setHome(true) }} aria-label="OpeningCoach home">
          <img src={dark ? '/pieces/wN.svg' : '/pieces/bN.svg'} alt="" />
          <span className="logo-text">Opening<span>Coach</span></span>
        </button>
        <span className="spacer" />
        {!onStart ? (
          <>
            <span className="player-chip">
              <span className="avatar">{player.slice(0, 1).toUpperCase()}</span>
              <strong>{player}</strong>
            </span>
            <div className="side-switch" role="radiogroup" aria-label="Show my games as">
              {['white', 'black'].map((c) => (
                <button key={c} role="radio" aria-checked={side === c} className={side === c ? 'on' : ''} onClick={() => switchSide(c)}>
                  <img src={`/pieces/${c === 'white' ? 'w' : 'b'}K.svg`} alt="" />
                  {c === 'white' ? 'White' : 'Black'}
                  <span className="count">{pair[c].summary.games}</span>
                </button>
              ))}
            </div>
            <button className="pill-btn" onClick={() => setChanging((c) => !c)}>{changing ? 'Cancel' : 'Change player'}</button>
          </>
        ) : (
          <>
            <button className="nav-link" onClick={() => setStartView(startView === 'greats' ? 'home' : 'greats')}>{startView === 'greats' ? 'Back' : 'The greats'}</button>
            <button className="nav-link" onClick={() => setStartView(startView === 'how' ? 'home' : 'how')}>{startView === 'how' ? 'Back' : 'How it works'}</button>
            {pair && <button className="pill-btn" onClick={() => { setHome(false); setStartView('home') }}>Back to my games</button>}
          </>
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

      {!online && <div className="banner warn" role="status">Reconnecting to the OpeningCoach server…</div>}
      {notice && online && <div className="banner" role="status">{notice}</div>}

      {onStart && startView === 'home' && <StartScreen onImport={onImport} onScoutAfter={() => setScoutNext(true)} />}
      {onStart && startView === 'how' && <main className="page"><UnderTheHood timings={timings} /></main>}
      {onStart && startView === 'greats' && <main className="page"><Greats onTiming={onTiming} /></main>}

      {!onStart && (
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

          <div className="screen" key={tab === 'opponent' || tab === 'greats' || tab === 'how' ? tab : `${tab}-${analysis.id}`}>
            {analysis.summary.games === 0 && !['greats', 'how'].includes(tab) ? (
              <div className="empty-side">
                <img src={`/pieces/${side === 'white' ? 'w' : 'b'}K.svg`} alt="" />
                <h2>No games as {side === 'white' ? 'White' : 'Black'}</h2>
                <p>This import has {pair[other(side)].summary.games} games as {other(side) === 'white' ? 'White' : 'Black'} and none as {side === 'white' ? 'White' : 'Black'}.</p>
                <button className="pill-btn" onClick={() => switchSide(other(side))}>Show my {other(side) === 'white' ? 'White' : 'Black'} games</button>
              </div>
            ) : <>
            {tab === 'board' && <Explorer analysis={analysis} path={path} setPath={setPath} onTiming={onTiming} />}
            {tab === 'weak' && <WeakLines analysis={analysis} openOnBoard={openOnBoard} onTiming={onTiming} />}
            {tab === 'book' && <OutOfBook analysis={analysis} openOnBoard={openOnBoard} onTiming={onTiming} />}
            {tab === 'plan' && <StudyPlan analysis={analysis} openOnBoard={openOnBoard} onTiming={onTiming} />}
            {tab === 'opponent' && <Scout analysis={analysis} opponent={opponent} oppPair={oppPair} loadOpponent={loadOpponent} onTiming={onTiming} />}
            {tab === 'greats' && <Greats onTiming={onTiming} />}
            {tab === 'how' && <UnderTheHood timings={timings} />}
            </>}
          </div>
        </main>
      )}

      <footer>
        Only finished games are analysed. Using an opening explorer during a game breaks Lichess and Chess.com fair-play
        rules. Reference games: Lichess open database (CC0). Pieces: Colin M.L. Burnett (CC BY-SA 3.0).
      </footer>
    </div>
  )
}
