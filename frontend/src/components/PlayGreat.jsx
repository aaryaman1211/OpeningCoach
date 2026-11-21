import { useEffect, useRef, useState } from 'react'
import { api, pct } from '../api.js'
import { playMove } from '../sound.js'
import ChessBoard from './ChessBoard.jsx'

// Play a game against one of the greats. The server picks their replies from strong players'
// games by win % (and calculates once those run out); after each of your moves a coach grades it.
// A weak move can be taken back, and the coach gives hints about the idea, never the move itself.

const LEVELS = [
  { id: 'easy', label: 'Easy', text: 'Plays moves strong players tried, often not the best ones.' },
  { id: 'medium', label: 'Medium', text: 'Prefers moves with a high win %, with some variety.' },
  { id: 'hard', label: 'Hard', text: 'Always the best-scoring move, then calculates as deep as it can.' },
]
const VERDICT = {
  best: 'Best', good: 'Good', ok: 'Playable', dubious: 'Dubious', mistake: 'Mistake', blunder: 'Blunder',
}

export default function PlayGreat({ great, onExit, onTiming }) {
  const first = great.name.split(' ')[0]
  const [setup, setSetup] = useState({ side: 'white', level: 'medium' })
  const [game, setGame] = useState(null) // { side, level }
  const [pos, setPos] = useState(null) // position from the server
  const [history, setHistory] = useState([]) // positions before each of your moves, for take-backs
  const [theirNote, setTheirNote] = useState(null)
  const [review, setReview] = useState(null) // { verdict, ... } for your last move
  const [deciding, setDeciding] = useState(false) // waiting for: take back / keep
  const [retry, setRetry] = useState(null) // hints carried over after a take-back
  const [hintsShown, setHintsShown] = useState(0)
  const [thinking, setThinking] = useState(false)
  const [error, setError] = useState('')
  const [tally, setTally] = useState({})
  const [resigned, setResigned] = useState(false)
  const [boardKey, setBoardKey] = useState(0) // bumped to undo the board's optimistic move after an error
  const alive = useRef(true)
  useEffect(() => () => { alive.current = false }, [])

  const signature = great.game ? great.game.moves.split(' ') : null
  const sigNote = great.game ? `${first} played this in ${great.game.title} (${great.game.event}).` : null

  async function opponentMove(moves, side, level) {
    setThinking(true)
    try {
      const t0 = performance.now()
      const r = await api.playOpponent({
        moves, level,
        signature: signature && great.game.side !== side ? signature : null,
        signatureNote: sigNote,
      })
      if (!alive.current) return
      onTiming?.(`${first} replies (${level})`, { millis: performance.now() - t0, algorithm: { name: r.source === 'engine' ? 'Alpha-beta search' : 'Reference games by win %' } })
      // a short pause so their move does not land at the same instant as yours
      await new Promise((res) => setTimeout(res, 250))
      setPos(r.position)
      setTheirNote({ san: r.move.san, source: r.source, note: r.note })
      playMove(r.move.san.includes('x'))
    } catch (e) {
      setError(e.message)
    } finally {
      if (alive.current) setThinking(false)
    }
  }

  async function start() {
    const side = setup.side === 'random' ? (Math.random() < 0.5 ? 'white' : 'black') : setup.side
    const g = { side, level: setup.level }
    setGame(g)
    setHistory([])
    setReview(null)
    setRetry(null)
    setTheirNote(null)
    setTally({})
    setResigned(false)
    setError('')
    const p = await api.playPosition([])
    setPos(p)
    if (side === 'black') opponentMove([], side, g.level)
  }

  async function onMove(san) {
    if (!pos || thinking || deciding) return
    setError('')
    const before = pos
    try {
      setThinking(true)
      const r = await api.playReview(pos.moves, san)
      if (!alive.current) return
      playMove(san.includes('x'))
      setHistory((h) => [...h, before])
      setPos(r.position)
      setReview(r.review)
      setHintsShown(0)
      setTally((t) => ({ ...t, [r.review.verdict]: (t[r.review.verdict] || 0) + 1 }))
      setThinking(false)
      if (r.review.hints.length && r.position.status === 'playing') {
        setDeciding(true) // let the player take it back or keep it
      } else {
        setRetry(null)
        if (r.position.status === 'playing') opponentMove(r.position.moves, game.side, game.level)
      }
    } catch (e) {
      setThinking(false)
      setError(e.message)
      setBoardKey((k) => k + 1) // put the piece back
    }
  }

  function takeBack() {
    const prev = history[history.length - 1]
    setHistory((h) => h.slice(0, -1))
    setPos(prev)
    setRetry({ hints: review.hints, facts: review.facts, shown: Math.max(1, hintsShown), verdict: review.verdict })
    setReview(null)
    setDeciding(false)
  }

  function keep() {
    setDeciding(false)
    setRetry(null)
    opponentMove(pos.moves, game.side, game.level)
  }

  // ------------------------------------------------------------------ setup
  if (!game) {
    return (
      <div className="play-setup">
        <div className="play-head">
          <span className="great-avatar big">{great.initials}</span>
          <div>
            <span className="kicker">Play a game</span>
            <h2 className="great-name">You vs {great.name}</h2>
            <p className="muted">
              {first}’s moves come from 21,925 games by strong players: from each position they pick a reply by its win %.
              When those games run out, they calculate. After each of your moves a coach tells you how it compares with
              what strong players do, and helps you find a better move without giving it away.
            </p>
          </div>
        </div>
        <div className="play-options">
          <div>
            <span className="opt-label">You play</span>
            <div className="choice">
              {['white', 'black', 'random'].map((s) => (
                <button key={s} className={setup.side === s ? 'on' : ''} onClick={() => setSetup({ ...setup, side: s })}>
                  {s === 'random' ? 'Random' : s === 'white' ? 'White' : 'Black'}
                </button>
              ))}
            </div>
          </div>
          <div>
            <span className="opt-label">Level</span>
            <div className="choice">
              {LEVELS.map((l) => (
                <button key={l.id} className={setup.level === l.id ? 'on' : ''} onClick={() => setSetup({ ...setup, level: l.id })}>{l.label}</button>
              ))}
            </div>
            <p className="muted small">{LEVELS.find((l) => l.id === setup.level).text}</p>
          </div>
        </div>
        <div className="row">
          <button className="primary" onClick={start}>Start the game</button>
          <button className="pill-btn" onClick={onExit}>Back to their famous game</button>
        </div>
      </div>
    )
  }

  // ------------------------------------------------------------------ game
  const over = pos && (pos.status !== 'playing' || resigned)
  const yourTurn = pos && !over && (pos.fen.split(' ')[1] === 'w') === (game.side === 'white')
  const result = !over ? null
    : resigned ? `You resigned. ${first} wins.`
    : pos.status === 'checkmate' ? (pos.winner === game.side ? `Checkmate. You beat ${great.name}!` : `Checkmate. ${first} wins.`)
    : `Draw by ${pos.status}.`
  const coachHints = deciding ? review.hints : retry?.hints ?? []
  const shownCount = deciding ? hintsShown : retry?.shown ?? 0

  return (
    <div className="play-game">
      <div className="great-board">
        {pos ? (
          <ChessBoard
            key={boardKey}
            fen={pos.fen}
            orientation={game.side}
            lastMove={pos.lastFrom ? { from: pos.lastFrom, to: pos.lastTo } : null}
            check={pos.check}
            legalMoves={yourTurn && !thinking && !deciding ? pos.legalMoves : []}
            onMove={onMove}
            tilt={34}
          />
        ) : <div className="skeleton board-skel" />}
        <div className="controls">
          <button className="pill-btn" onClick={() => setGame(null)}>New game</button>
          {!over && <button className="pill-btn" onClick={() => setResigned(true)}>Resign</button>}
          <button className="pill-btn" onClick={onExit}>Back to their famous game</button>
        </div>
        {pos && <MoveList moves={pos.moves} />}
      </div>

      <div className="great-info">
        <div className="opp-head">
          <span className="great-avatar">{great.initials}</span>
          <div>
            <strong>{great.name}</strong>
            <span className="muted small">{LEVELS.find((l) => l.id === game.level).label} · you play {game.side === 'white' ? 'White' : 'Black'}</span>
          </div>
          <span className={`turn ${thinking ? 'busy' : ''}`}>
            {over ? 'Game over' : thinking ? (yourTurn ? 'Checking your move…' : `${first} is thinking…`) : deciding ? 'Your decision' : yourTurn ? 'Your move' : ''}
          </span>
        </div>

        {result && (
          <div className="result-card">
            <strong>{result}</strong>
            <Summary tally={tally} />
            <button className="primary" onClick={() => setGame(null)}>Play again</button>
          </div>
        )}

        {theirNote && (
          <div className="their-move">
            <span className="key-label">{first} played {theirNote.san}</span>
            <p>{theirNote.source === 'signature' ? sigNote : theirNote.note}</p>
          </div>
        )}

        {review && (
          <div className={`coach-card v-${review.verdict}`}>
            <div className="coach-top">
              <span className={`verdict v-${review.verdict}`}>{VERDICT[review.verdict]}</span>
              <strong>{review.headline}</strong>
            </div>
            {review.facts.length > 0 && <ul className="coach-facts">{review.facts.map((f, i) => <li key={i}>{f}</li>)}</ul>}
            {review.yourScore != null && review.bestScore != null && (
              <div className="bars">
                <Bar label="Your move" value={review.yourScore} />
                <Bar label="Best well-tested choice" value={review.bestScore} />
              </div>
            )}
            {deciding && (
              <div className="row">
                <button className="primary" onClick={takeBack}>Take it back and try again</button>
                <button className="pill-btn" onClick={keep}>Keep my move</button>
              </div>
            )}
          </div>
        )}

        {retry && !review && (
          <div className="coach-card retry">
            <div className="coach-top">
              <span className="verdict v-retry">Try again</span>
              <strong>Find a better move. You can use the hints below.</strong>
            </div>
            {retry.facts.length > 0 && <ul className="coach-facts">{retry.facts.map((f, i) => <li key={i}>{f}</li>)}</ul>}
          </div>
        )}

        {coachHints.length > 0 && (
          <div className="hints">
            {coachHints.slice(0, shownCount).map((h, i) => <p key={i} className="hint">{h}</p>)}
            {shownCount < coachHints.length && (
              <button className="pill-btn" onClick={() => (deciding ? setHintsShown((n) => n + 1) : setRetry((r) => ({ ...r, shown: r.shown + 1 })))}>
                {shownCount === 0 ? 'Give me a hint' : 'Another hint'}
              </button>
            )}
            <p className="muted small">Hints describe the idea, the piece and the area. The coach never tells you the move.</p>
          </div>
        )}

        {error && <p className="error">{error}</p>}
        {!over && !review && !retry && !theirNote && yourTurn && (
          <p className="muted">Make your first move. Drag a piece or click it, then click where it should go.</p>
        )}
      </div>
    </div>
  )
}

function Bar({ label, value }) {
  return (
    <div className="bar-row">
      <span>{label}</span>
      <span className="track"><span style={{ width: `${Math.round(value * 100)}%` }} /></span>
      <span className="num">{pct(value)}</span>
    </div>
  )
}

function MoveList({ moves }) {
  const ref = useRef(null)
  useEffect(() => { if (ref.current) ref.current.scrollTop = ref.current.scrollHeight }, [moves.length])
  if (!moves.length) return null
  return (
    <ol className="game-moves" ref={ref} aria-label="Moves">
      {moves.map((m, i) => (
        <li key={i}>
          {i % 2 === 0 && <span className="n">{i / 2 + 1}.</span>}
          <span className={`mv ${i === moves.length - 1 ? 'cur' : ''}`}>{m}</span>
        </li>
      ))}
    </ol>
  )
}

function Summary({ tally }) {
  const keys = Object.keys(VERDICT).filter((k) => tally[k])
  if (!keys.length) return null
  return (
    <p className="muted small">Your moves: {keys.map((k) => `${tally[k]} ${VERDICT[k].toLowerCase()}`).join(' · ')}</p>
  )
}
