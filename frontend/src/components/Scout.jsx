import { useEffect, useState } from 'react'
import { api, pct } from '../api.js'
import Explorer from './Explorer.jsx'
import ImportPanel from './ImportPanel.jsx'
import LineReplay from './LineReplay.jsx'
import ScoreBar from './ScoreBar.jsx'
import WeakLines from './WeakLines.jsx'
import PageHeader from './PageHeader.jsx'

const VIEWS = [
  { id: 'plan', label: 'Game plan' },
  { id: 'openings', label: 'Their openings' },
  { id: 'weak', label: 'Their weak lines' },
  { id: 'board', label: 'Their games on the board' },
]

/** Opponent preparation. */
export default function Scout({ analysis, opponent, oppPair, loadOpponent, onTiming }) {
  const [view, setView] = useState('plan')
  const [path, setPath] = useState([])
  const [changing, setChanging] = useState(false)
  useEffect(() => setPath([]), [opponent?.id])
  const yourColour = analysis.summary.colour
  const theirColour = yourColour === 'white' ? 'black' : 'white'

  async function load(req) {
    await loadOpponent(req)
    setChanging(false)
    setPath([])
    setView('plan')
  }

  function openOnBoard(p) {
    setPath(p)
    setView('board')
  }

  if (!oppPair || changing) {
    return (
      <div style={{ maxWidth: 560 }}>
        <PageHeader kicker="Opponent prep" title="Know your" accent="rival.">
          Load your next opponent’s games to see what they play, where they struggle, which of your lines hit their weak
          spots, and how your game will probably start. Both of their colours are analysed: while you look at your{' '}
          {yourColour} games you see their {theirColour} games, and the White/Black switch at the top flips both.
        </PageHeader>
        <ImportPanel onImport={load} opponent submitLabel="Load opponent"
                     onCancel={oppPair ? () => setChanging(false) : null} />
      </div>
    )
  }

  const s = opponent.summary
  return (
    <div>
      <PageHeader kicker="Opponent prep" title={`${s.player} plays`} accent={`${s.colour}.`}
                  aside={<button className="pill-btn" onClick={() => setChanging(true)}>Change opponent</button>}>
        {s.games} games scouted, scoring {pct(s.score)}. Here is how to meet them.
      </PageHeader>

      <div className="subtabs" role="tablist">
        {VIEWS.map((v) => (
          <button key={v.id} role="tab" aria-selected={view === v.id} className={view === v.id ? 'on' : ''} onClick={() => setView(v.id)}>
            {v.label}
          </button>
        ))}
      </div>

      {view === 'plan' && <GamePlan analysis={analysis} opponent={opponent} openOnBoard={openOnBoard} onTiming={onTiming} />}
      {view === 'openings' && <TheirOpenings opponent={opponent} openOnBoard={openOnBoard} />}
      {view === 'weak' && <WeakLines analysis={opponent} openOnBoard={openOnBoard} onTiming={onTiming} who="them" name={s.player} />}
      {view === 'board' && <Explorer key={opponent.id} analysis={opponent} path={path} setPath={setPath} onTiming={onTiming} who="them" name={s.player} />}
    </div>
  )
}

function GamePlan({ analysis, opponent, openOnBoard, onTiming }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const opposite = analysis.summary.colour !== opponent.summary.colour

  useEffect(() => {
    if (!opposite) return
    api.matchup(analysis.id, opponent.id, 2, 6)
      .then((res) => { setData(res); onTiming('Opponent matchup', res) })
      .catch((e) => setError(e.message))
  }, [analysis.id, opponent.id])

  if (!opposite) {
    return (
      <p className="empty">
        You loaded your {analysis.summary.colour} games and their {opponent.summary.colour} games. Load the opposite
        colours to compare your lines with theirs.
      </p>
    )
  }
  if (error) return <p className="error">{error}</p>
  if (!data) return <div className="skeleton" />
  const r = data.result

  return (
    <div className="split" style={{ gridTemplateColumns: 'minmax(0, 1fr) 380px' }}>
      <div className="stack">
        <MatchupTable title="Lines to aim for" note="You score well here and they score badly." items={r.weapons} openOnBoard={openOnBoard} />
        <MatchupTable title="Lines to avoid" note="They score well here and you struggle." items={r.dangers} openOnBoard={openOnBoard} />
        <details className="how">
          <summary>How these are found</summary>
          <p>
            Both players’ games are stored as opening trees. Walking the two trees together visits only the{' '}
            {r.sharedLines} lines you have both played. Each line gets an edge = (your score − their score) ÷ 2, and lines
            are ranked by edge × √games so one lucky game does not dominate.
          </p>
        </details>
      </div>

      <aside className="preview">
        <h3 style={{ marginBottom: 4 }}>Likely start of your game</h3>
        <p className="muted small" style={{ marginBottom: 10 }}>Each side plays the move they chose most often.</p>
        {r.predicted.length ? (
          <LineReplay
            moves={r.predicted.map((p) => p.san)}
            orientation={analysis.summary.colour}
            interval={1000}
            autoPlay={false}
            renderSteps={(idx, jump) => (
              <ol className="predicted-moves">
                {r.predicted.map((p, i) => (
                  <li key={i} className={i === idx ? 'cur' : ''} onClick={() => jump(i)}>
                    <span className="muted">{Math.floor(i / 2) + 1}{i % 2 ? '…' : '.'}</span>
                    <span className="san">{p.san}</span>
                    <span className="muted">{p.yourMove ? 'you' : 'them'}</span>
                    <span className="muted small">{pct(p.share)} of {p.games} games</span>
                  </li>
                ))}
              </ol>
            )}
          />
        ) : (
          <p className="empty">Not enough shared games to predict a line.</p>
        )}
      </aside>
    </div>
  )
}

function MatchupTable({ title, note, items, openOnBoard }) {
  return (
    <div>
      <h3>{title}</h3>
      <p className="muted small" style={{ marginBottom: 8 }}>{note}</p>
      {!items.length ? (
        <p className="empty">None found with at least 2 games each.</p>
      ) : (
        <table className="list">
          <thead>
            <tr>
              <th>Line</th>
              <th>You</th>
              <th>Them</th>
              <th className="num hide-sm">They reach it</th>
            </tr>
          </thead>
          <tbody>
            {items.map((m) => (
              <tr key={m.line} tabIndex={0} onClick={() => openOnBoard(m.path)} onKeyDown={(e) => e.key === 'Enter' && openOnBoard(m.path)} title="Open on the board">
                <td className="line">{m.line}</td>
                <td><ScoreBar score={m.yourScore} /> <span className="muted small">({m.yourGames})</span></td>
                <td><ScoreBar score={m.theirScore} /> <span className="muted small">({m.theirGames})</span></td>
                <td className="num hide-sm">{pct(m.theyReachShare)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function TheirOpenings({ opponent, openOnBoard }) {
  const [first, setFirst] = useState(null)
  const [deep, setDeep] = useState(null)
  const theyWhite = opponent.summary.colour === 'white'

  useEffect(() => {
    api.topLines(opponent.id, 6, theyWhite ? 1 : 2).then((r) => setFirst(r.result))
    api.topLines(opponent.id, 10, 6).then((r) => setDeep(r.result))
  }, [opponent.id])

  return (
    <div className="two-col">
      <div>
        <h3 style={{ marginBottom: 8 }}>{theyWhite ? 'First move' : 'Reply to your first move'}</h3>
        {first ? <ShareTable lines={first.lines} openOnBoard={openOnBoard} /> : <div className="skeleton" />}
      </div>
      <div>
        <h3 style={{ marginBottom: 8 }}>Most played lines (first 3 moves)</h3>
        {deep ? <ShareTable lines={deep.lines} openOnBoard={openOnBoard} /> : <div className="skeleton" />}
      </div>
    </div>
  )
}

function ShareTable({ lines, openOnBoard }) {
  if (!lines.length) return <p className="empty">Not enough games.</p>
  return (
    <table className="list">
      <thead>
        <tr>
          <th>Line</th>
          <th className="num">Share</th>
          <th className="num hide-sm">Games</th>
          <th>Score</th>
        </tr>
      </thead>
      <tbody>
        {lines.map((l) => (
          <tr key={l.line} tabIndex={0} onClick={() => openOnBoard(l.path)} onKeyDown={(e) => e.key === 'Enter' && openOnBoard(l.path)} title="Open on the board">
            <td className="line">{l.line}</td>
            <td className="num">{pct(l.share)}</td>
            <td className="num hide-sm">{l.games}</td>
            <td><ScoreBar score={l.score} /></td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
