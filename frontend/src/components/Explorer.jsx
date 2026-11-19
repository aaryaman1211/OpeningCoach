import { useEffect, useMemo, useRef, useState } from 'react'
import { api, pct } from '../api.js'
import ChessBoard, { parseFen } from './ChessBoard.jsx'
import ScoreBar from './ScoreBar.jsx'
import { Arrows, First, Flip, Last, Next, Pause, Play, Prev, SoundOff, SoundOn } from './Icons.jsx'
import { isMuted, playMove, setMuted } from '../sound.js'

/**
 * The board view: an interactive board plus the moves played from the current position.
 * who = 'you' for your own games, 'them' for a scouted opponent.
 */
export default function Explorer({ analysis, path, setPath, onTiming, who = 'you', name }) {
  const [view, setView] = useState(null)
  const [error, setError] = useState('')
  const [line, setLine] = useState(path)
  const [flipped, setFlipped] = useState(false)
  const [showArrows, setShowArrows] = useState(true)
  const [hoverSan, setHoverSan] = useState(null)
  const [playing, setPlaying] = useState(false)
  const [muted, setMutedState] = useState(isMuted())
  const cache = useRef(new Map())
  const prevFen = useRef(null)
  const white = analysis.summary.colour === 'white'
  const orientation = white !== flipped ? 'white' : 'black'
  const subject = who === 'you' ? 'You' : name
  const theirs = who === 'you' ? 'your' : 'their'

  // remember the longest line walked, so "next" can replay it after going back
  useEffect(() => {
    setLine((l) => (path.every((m, i) => l[i] === m) && l.length >= path.length ? l : path))
  }, [path.join(',')])

  useEffect(() => {
    let live = true
    const key = path.join(',')
    if (cache.current.has(key)) {
      setView(cache.current.get(key))
      return
    }
    setError('')
    api.tree(analysis.id, path)
      .then((res) => {
        if (!live) return
        cache.current.set(key, res.result)
        setView(res.result)
        onTiming?.('Board position', res)
      })
      .catch((e) => live && setError(e.message))
    return () => {
      live = false
    }
  }, [analysis.id, path.join(',')])

  useEffect(() => {
    if (!view) return
    if (prevFen.current && prevFen.current !== view.fen) {
      const capture = Object.keys(parseFen(view.fen)).length < Object.keys(parseFen(prevFen.current)).length
      playMove(capture)
    }
    prevFen.current = view.fen
  }, [view?.fen])

  // "next": continue the walked line, else the most played move, else strong players' main move
  const nextMove = useMemo(() => {
    if (!view) return null
    if (line.length > path.length && path.every((m, i) => line[i] === m)) return line[path.length]
    const mine = view.moves.filter((m) => m.yourGames > 0).sort((a, b) => b.yourGames - a.yourGames)[0]
    if (mine) return mine.san
    return view.moves.filter((m) => m.refGames > 0).sort((a, b) => b.refGames - a.refGames)[0]?.san ?? null
  }, [view, line, path])

  const back = () => path.length && setPath(path.slice(0, -1))
  const forward = () => nextMove && setPath([...path, nextMove])

  useEffect(() => {
    function onKey(e) {
      if (e.target.closest('input, textarea, select')) return
      if (e.key === 'ArrowLeft') { e.preventDefault(); back() }
      else if (e.key === 'ArrowRight') { e.preventDefault(); forward() }
      else if (e.key === 'Home') { e.preventDefault(); setPath([]) }
      else if (e.key === 'End') { e.preventDefault(); setPath(line) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  useEffect(() => {
    if (!playing) return
    if (!nextMove) {
      setPlaying(false)
      return
    }
    const t = setTimeout(forward, 900)
    return () => clearTimeout(t)
  }, [playing, nextMove, path.join(',')])

  if (error) return <p className="error">{error} <button className="link" onClick={() => setPath([])}>Back to start</button></p>
  if (!view) return <div className="boardview"><div className="skeleton board-skel" /><div /></div>

  const arrows = []
  if (showArrows) {
    const top = view.moves.filter((m) => m.yourGames > 0 && m.from).sort((a, b) => b.yourGames - a.yourGames)[0]
    view.moves.filter((m) => m.refGames > 0 && m.from && m.san !== top?.san).sort((a, b) => b.refGames - a.refGames).slice(0, 2)
      .forEach((m) => arrows.push({ from: m.from, to: m.to, color: 'ref', opacity: 0.75, width: 0.13 }))
    if (top) arrows.push({ from: top.from, to: top.to, color: who === 'you' ? 'you' : 'them', opacity: 0.8, width: 0.13 })
  }
  const hovered = view.moves.find((m) => m.san === hoverSan && m.from)
  if (hovered) arrows.push({ from: hovered.from, to: hovered.to, color: 'pred', opacity: 0.9, width: 0.15 })

  return (
    <div className="boardview">
      <div>
        <ChessBoard
          fen={view.fen}
          orientation={orientation}
          lastMove={view.lastFrom ? { from: view.lastFrom, to: view.lastTo } : null}
          check={view.check}
          legalMoves={view.legalMoves}
          onMove={(san) => setPath([...path, san])}
          arrows={arrows}
        />
        <div className="controls">
          <button className="icon" onClick={() => setPath([])} disabled={!path.length} title="Start (Home)" aria-label="Go to start"><First /></button>
          <button className="icon" onClick={back} disabled={!path.length} title="Previous move (←)" aria-label="Previous move"><Prev /></button>
          <button className="icon" onClick={() => setPlaying((p) => !p)} disabled={!nextMove && !playing}
                  title={playing ? 'Pause' : `Play ${theirs} main line`} aria-label={playing ? 'Pause' : 'Play main line'}>
            {playing ? <Pause /> : <Play />}
          </button>
          <button className="icon" onClick={forward} disabled={!nextMove} title="Next move (→)" aria-label="Next move"><Next /></button>
          <button className="icon" onClick={() => setPath(line)} disabled={line.length <= path.length} title="End of line (End)" aria-label="End of line"><Last /></button>
          <span className="gap" />
          <button className="icon" onClick={() => setFlipped((f) => !f)} title="Flip board" aria-label="Flip board"><Flip /></button>
          <button className={`icon ${showArrows ? 'on' : ''}`} onClick={() => setShowArrows((v) => !v)} title="Show arrows" aria-label="Show arrows" aria-pressed={showArrows}><Arrows /></button>
          <button className={`icon ${muted ? '' : 'on'}`} onClick={() => { setMuted(!muted); setMutedState(!muted) }} title="Move sounds" aria-label="Move sounds" aria-pressed={!muted}>
            {muted ? <SoundOff /> : <SoundOn />}
          </button>
        </div>
        {showArrows && (
          <div className="legend">
            <span><i className="ref" />most played by strong players</span>
            <span><i className={who === 'you' ? 'you' : 'them'} />{theirs} most played</span>
          </div>
        )}
      </div>

      <div>
        <div className="moves-line" aria-label="Moves">
          {!line.length && <span className="muted">Start position. Drag or click a piece, or pick a move below.</span>}
          {line.map((m, i) => (
            <span key={i}>
              {i % 2 === 0 && <span className="n">{i / 2 + 1}.</span>}
              <button className={`${path.length === i + 1 ? 'cur' : ''} ${i + 1 > path.length ? 'after' : ''}`} onClick={() => setPath(line.slice(0, i + 1))}>{m}</button>
            </span>
          ))}
        </div>

        <div className="position-summary">
          <div>
            <div className="muted small">{subject} reached this position</div>
            <div className="big">{view.yourGames} {view.yourGames === 1 ? 'game' : 'games'}</div>
            {view.yourGames > 0 && <div className="small">scored <ScoreBar score={view.yourScore} compare={view.refScore} /></div>}
          </div>
          <div>
            <div className="muted small">Players rated 1700+</div>
            {view.refGames ? (
              <>
                <div className="big">{view.refGames.toLocaleString()} games</div>
                <div className="small">{who === 'you' ? 'your' : 'their'} side scored <ScoreBar score={view.refScore} /></div>
              </>
            ) : (
              <div className="small" style={{ marginTop: 6 }}>Never reached this position. You are out of theory.</div>
            )}
          </div>
        </div>

        <table className="list">
          <thead>
            <tr>
              <th>Next move</th>
              <th className="num">{who === 'you' ? 'Your' : 'Their'} games</th>
              <th>{who === 'you' ? 'Your' : 'Their'} score</th>
              <th className="hide-sm">Strong players</th>
              <th className="hide-sm">Their score</th>
            </tr>
          </thead>
          <tbody>
            {view.moves.slice(0, 15).map((m) => (
              <tr key={m.san} tabIndex={0}
                  onClick={() => setPath([...path, m.san])}
                  onKeyDown={(e) => e.key === 'Enter' && setPath([...path, m.san])}
                  onMouseEnter={() => setHoverSan(m.san)} onMouseLeave={() => setHoverSan(null)}
                  onFocus={() => setHoverSan(m.san)} onBlur={() => setHoverSan(null)}>
                <td className="mono">
                  {m.san}
                  {!m.inBook && <span className="tag warn" title="Strong players never played this">new</span>}
                </td>
                <td className="num">{m.yourGames || '–'}</td>
                <td>{m.yourGames ? <ScoreBar score={m.yourScore} compare={m.refScore} /> : <span className="muted">–</span>}</td>
                <td className="hide-sm">
                  {m.refGames ? (
                    <span className="share-cell" title={`${m.refGames} games`}>
                      <span className="track"><span style={{ width: pct(m.refShare) }} /></span>
                      {pct(m.refShare)}
                    </span>
                  ) : <span className="muted">–</span>}
                </td>
                <td className="hide-sm">{m.refGames ? pct(m.refScore) : <span className="muted">–</span>}</td>
              </tr>
            ))}
            {!view.moves.length && (
              <tr><td colSpan="5" className="muted">No games continue from here. You can still try any legal move on the board.</td></tr>
            )}
          </tbody>
        </table>
        <p className="muted small" style={{ marginTop: 8 }}>
          Scores are from {theirs} side (100% = always won). Green or red means at least 5 points better or worse than
          strong players. Keys: ← → step, Home / End.
        </p>
      </div>
    </div>
  )
}
