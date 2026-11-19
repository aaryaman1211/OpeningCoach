import { useEffect, useState } from 'react'
import { api, pct } from '../api.js'
import Explorer from './Explorer.jsx'
import LineReplay from './LineReplay.jsx'
import ScoreBar from './ScoreBar.jsx'
import WeakLines from './WeakLines.jsx'

const SOURCES = [
  { id: 'lichess', label: 'Lichess' },
  { id: 'chesscom', label: 'Chess.com' },
  { id: 'pgn', label: 'Upload PGN' },
  { id: 'demo', label: 'Demo opponent' },
]

const VIEWS = [
  { id: 'plan', label: 'Game plan' },
  { id: 'rep', label: 'Their repertoire' },
  { id: 'weak', label: 'Where they struggle' },
  { id: 'explore', label: 'Explore their games' },
]

export default function Scout({ analysis, opponent, setOpponent, onTiming }) {
  const [view, setView] = useState('plan')
  const [path, setPath] = useState([])
  const [editing, setEditing] = useState(!opponent)

  function openLine(p) {
    setPath(p)
    setView('explore')
  }

  if (!opponent || editing) {
    return (
      <OpponentForm
        yourColour={analysis.summary.colour}
        onLoaded={(o) => { setOpponent(o); setEditing(false); setPath([]); setView('plan') }}
        onCancel={opponent ? () => setEditing(false) : null}
      />
    )
  }

  const s = opponent.summary
  return (
    <div className="scout fade-in">
      <div className="card opp-head">
        <div className="avatar">{s.player.slice(0, 1).toUpperCase()}</div>
        <div className="opp-meta">
          <span className="label">Opponent</span>
          <strong className="opp-name">{s.player}</strong>
          <span className="muted small">
            as {s.colour === 'white' ? '♔ White' : '♚ Black'} · {s.games} games · {s.wins}W {s.draws}D {s.losses}L
          </span>
        </div>
        <div className="opp-score">
          <span className="label">Their score</span>
          <ScoreBar score={s.score} />
        </div>
        <button onClick={() => setEditing(true)}>Change opponent</button>
      </div>

      <div className="pills" role="tablist">
        {VIEWS.map((v) => (
          <button key={v.id} role="tab" aria-selected={view === v.id} className={view === v.id ? 'on' : ''} onClick={() => setView(v.id)}>
            {v.label}
          </button>
        ))}
      </div>

      <div className="fade-in" key={view}>
        {view === 'plan' && <GamePlan analysis={analysis} opponent={opponent} openLine={openLine} onTiming={onTiming} />}
        {view === 'rep' && <Repertoire opponent={opponent} openLine={openLine} onTiming={onTiming} />}
        {view === 'weak' && <WeakLines analysis={opponent} openLine={openLine} onTiming={onTiming} who="them" name={s.player} />}
        {view === 'explore' && (
          <Explorer analysis={opponent} path={path} setPath={setPath} onTiming={onTiming} who="them" name={s.player} />
        )}
      </div>
    </div>
  )
}

function OpponentForm({ yourColour, onLoaded, onCancel }) {
  const [source, setSource] = useState('lichess')
  const [username, setUsername] = useState('')
  const [colour, setColour] = useState(yourColour === 'white' ? 'black' : 'white')
  const [maxGames, setMaxGames] = useState(500)
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    setError('')
    const name = source === 'demo' ? 'demo_player' : username.trim()
    if (!/^[A-Za-z0-9_-]{2,30}$/.test(name)) {
      setError("Enter the opponent's username (2–30 letters, digits, _ or -)")
      return
    }
    let pgnText
    if (source === 'pgn') {
      if (!file) return setError('Choose a .pgn file')
      pgnText = await file.text()
    }
    setBusy(true)
    try {
      onLoaded(await api.createAnalysis({ source, username: name, colour, maxGames: Number(maxGames), pgnText }))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card scout-form fade-in" onSubmit={submit}>
      <div>
        <h2>Scout an opponent</h2>
        <p className="muted">
          Load your next opponent’s games. OpeningCoach shows what they play, where they struggle, and which of your
          lines hit their weak spots. It also predicts how your game is likely to start.
        </p>
      </div>
      <div className="seg">
        {SOURCES.map((s) => (
          <button type="button" key={s.id} className={source === s.id ? 'on' : ''} onClick={() => setSource(s.id)}>
            {s.label}
          </button>
        ))}
      </div>
      <div className="import-row">
        {source !== 'demo' && (
          <label className="field grow">
            <span>{source === 'pgn' ? "Opponent's name in the PGN" : "Opponent's username"}</span>
            <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="e.g. their_username" spellCheck="false" />
          </label>
        )}
        {source === 'pgn' && (
          <label className="field grow">
            <span>PGN file</span>
            <input type="file" accept=".pgn,text/plain" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </label>
        )}
        <div className="field">
          <span>They will play</span>
          <div className="seg small">
            {['white', 'black'].map((c) => (
              <button type="button" key={c} className={colour === c ? 'on' : ''} onClick={() => setColour(c)}>
                {c === 'white' ? '♔ White' : '♚ Black'}
              </button>
            ))}
          </div>
        </div>
        {(source === 'lichess' || source === 'chesscom') && (
          <label className="field narrow">
            <span>Max games</span>
            <input type="number" min="1" max="2000" value={maxGames} onChange={(e) => setMaxGames(e.target.value)} />
          </label>
        )}
        <button className="primary" disabled={busy}>{busy ? <span className="spinner" /> : 'Scout'}</button>
        {onCancel && <button type="button" onClick={onCancel}>Cancel</button>}
      </div>
      {colour === yourColour && (
        <p className="hint">Tip: pick the opposite of your own colour ({yourColour}) to get matchup prep and a predicted game.</p>
      )}
      {source === 'demo' && <p className="hint">Loads the demo games with the other colour, so you can try scouting offline.</p>}
      {error && <p className="error" role="alert">{error}</p>}
    </form>
  )
}

function GamePlan({ analysis, opponent, openLine, onTiming }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const opposite = analysis.summary.colour !== opponent.summary.colour

  useEffect(() => {
    if (!opposite) return
    api.matchup(analysis.id, opponent.id, 2, 6)
      .then((res) => { setData(res); onTiming('Matchup', res) })
      .catch((e) => setError(e.message))
  }, [analysis.id, opponent.id])

  if (!opposite) {
    return (
      <div className="card callout">
        You loaded your <strong>{analysis.summary.colour}</strong> games and their <strong>{opponent.summary.colour}</strong>{' '}
        games. Pick opposite colours to compare your lines with theirs.
      </div>
    )
  }
  if (error) return <p className="error">{error}</p>
  if (!data) return <div className="skeleton tall" />
  const r = data.result
  const youWhite = analysis.summary.colour === 'white'

  return (
    <div className="plan-grid">
      <div className="card predicted">
        <h3>How your game will probably start</h3>
        <p className="muted small">
          Each side plays the move they chose most often in their own games. Built by walking both opening trees
          together ({data.algorithm.visitedNodes} shared positions checked).
        </p>
        {r.predicted.length ? (
          <LineReplay
            moves={r.predicted.map((p) => p.san)}
            orientation={youWhite ? 'white' : 'black'}
            interval={1100}
            renderStep={(idx, plies, jump) => (
              <ol className="pred-steps">
                {r.predicted.map((p, i) => (
                  <li key={i} className={`${i === idx ? 'cur' : ''} ${p.yourMove ? 'you' : 'them'}`} onClick={() => jump(i)}>
                    <span className="mn">{Math.floor(i / 2) + 1}{i % 2 ? '…' : '.'}</span>
                    <strong>{p.san}</strong>
                    <span className="who">{p.yourMove ? 'you' : 'them'}</span>
                    <span className="share"><span style={{ width: pct(p.share) }} /></span>
                    <span className="muted small">{pct(p.share)} of {p.games}</span>
                  </li>
                ))}
              </ol>
            )}
          />
        ) : (
          <p className="muted">Not enough shared games to predict a line.</p>
        )}
      </div>

      <div className="matchups">
        <MatchupList title="Your weapons" subtitle="Lines where you score well and they score badly" items={r.weapons} kind="good" openLine={openLine} />
        <MatchupList title="Danger zones" subtitle="Lines where they do well and you struggle: avoid or prepare" items={r.dangers} kind="bad" openLine={openLine} />
        <p className="muted small">
          {r.sharedLines} lines appear in both your games and theirs. Edge = (your score − their score) ÷ 2, ranked by
          edge × √games so one lucky game does not dominate. Top picks chosen with randomized Quickselect.
        </p>
      </div>
    </div>
  )
}

function MatchupList({ title, subtitle, items, kind, openLine }) {
  return (
    <div className="card">
      <h3>{title}</h3>
      <p className="muted small">{subtitle}</p>
      {!items.length && <p className="muted">None found with at least 2 games each.</p>}
      <div className="mu-list">
        {items.map((m) => (
          <button key={m.line} className={`mu ${kind}`} onClick={() => openLine(m.path)}>
            <div className="line">{m.line}</div>
            <div className="mu-stats">
              <span>You <strong>{pct(m.yourScore)}</strong> <span className="muted">({m.yourGames})</span></span>
              <span>Them <strong>{pct(m.theirScore)}</strong> <span className="muted">({m.theirGames})</span></span>
              <span className="muted">they reach it in {pct(m.theyReachShare)} of games</span>
            </div>
            <div className="edge">
              <span className="edge-bar"><span className={kind} style={{ width: `${Math.min(100, Math.abs(m.edge) * 200)}%` }} /></span>
              <span className="small">{m.edge > 0 ? '+' : ''}{Math.round(m.edge * 100)}</span>
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}

function Repertoire({ opponent, openLine, onTiming }) {
  const [first, setFirst] = useState(null)
  const [deep, setDeep] = useState(null)
  const theyWhite = opponent.summary.colour === 'white'

  useEffect(() => {
    api.topLines(opponent.id, 6, theyWhite ? 1 : 2).then((r) => { setFirst(r.result); onTiming('Their first moves', r) })
    api.topLines(opponent.id, 8, 6).then((r) => setDeep(r.result))
  }, [opponent.id])

  return (
    <div className="rep-grid">
      <div className="card">
        <h3>{theyWhite ? 'First move' : 'Replies to your first move'}</h3>
        {!first ? <div className="skeleton" /> : <ShareList lines={first.lines} openLine={openLine} />}
      </div>
      <div className="card">
        <h3>Favourite lines (3 moves deep)</h3>
        {!deep ? <div className="skeleton" /> : <ShareList lines={deep.lines} openLine={openLine} />}
      </div>
    </div>
  )
}

function ShareList({ lines, openLine }) {
  if (!lines.length) return <p className="muted">Not enough games.</p>
  const max = Math.max(...lines.map((l) => l.share))
  return (
    <div className="share-list">
      {lines.map((l) => (
        <button key={l.line} className="share-row" onClick={() => openLine(l.path)}>
          <span className="line">{l.line}</span>
          <span className="share-bar"><span style={{ width: `${(l.share / max) * 100}%` }} /></span>
          <span className="small">{pct(l.share)}</span>
          <span className="small muted">{l.games} games · {pct(l.score)}</span>
        </button>
      ))}
    </div>
  )
}
