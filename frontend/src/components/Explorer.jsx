import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, pct } from '../api.js'
import ChessBoard, { parseFen } from './ChessBoard.jsx'
import ScoreBar from './ScoreBar.jsx'
import { isMuted, playMove, setMuted } from '../sound.js'

/**
 * Opening explorer around the interactive board. `who` switches the wording between your own
 * games ("you") and a scouted opponent ("them").
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
  const orientation = (white !== flipped) ? 'white' : 'black'
  const You = who === 'you' ? 'You' : name || 'They'
  const your = who === 'you' ? 'Your' : `${name || 'Their'}’s`

  // keep the longest line we have walked, so → can replay it after ←
  useEffect(() => {
    setLine((l) => (path.every((m, i) => l[i] === m) && l.length >= path.length ? l : path))
  }, [path.join(',')])

  useEffect(() => {
    let live = true
    const key = path.join(',')
    const hit = cache.current.get(key)
    if (hit) {
      setView(hit)
      return
    }
    setError('')
    api
      .tree(analysis.id, path)
      .then((res) => {
        if (!live) return
        cache.current.set(key, res.result)
        setView(res.result)
        onTiming?.('Explorer', res)
      })
      .catch((e) => live && setError(e.message))
    return () => {
      live = false
    }
  }, [analysis.id, path.join(',')])

  // move sound: a capture is a drop in the number of pieces
  useEffect(() => {
    if (!view) return
    if (prevFen.current && prevFen.current !== view.fen) {
      const before = Object.keys(parseFen(prevFen.current)).length
      const after = Object.keys(parseFen(view.fen)).length
      playMove(after < before)
    }
    prevFen.current = view.fen
  }, [view?.fen])

  const nextMove = useMemo(() => {
    if (!view) return null
    if (line.length > path.length && path.every((m, i) => line[i] === m)) return line[path.length]
    const mine = [...view.moves].sort((a, b) => b.yourGames - a.yourGames)[0]
    if (mine && mine.yourGames > 0) return mine.san
    const ref = [...view.moves].sort((a, b) => b.refGames - a.refGames)[0]
    return ref?.san ?? null
  }, [view, line, path])

  const go = useCallback((p) => setPath(p), [setPath])
  const back = () => path.length && go(path.slice(0, -1))
  const forward = () => nextMove && go([...path, nextMove])

  useEffect(() => {
    function onKey(e) {
      if (e.target.closest('input, textarea, select')) return
      if (e.key === 'ArrowLeft') { e.preventDefault(); back() }
      else if (e.key === 'ArrowRight') { e.preventDefault(); forward() }
      else if (e.key === 'ArrowUp') { e.preventDefault(); go([]) }
      else if (e.key === 'ArrowDown') { e.preventDefault(); go(line) }
      else if (e.key === 'f') setFlipped((f) => !f)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  // autoplay along the main line
  useEffect(() => {
    if (!playing) return
    if (!nextMove) {
      setPlaying(false)
      return
    }
    const t = setTimeout(forward, 850)
    return () => clearTimeout(t)
  }, [playing, nextMove, path.join(',')])

  if (error) return <p className="error">{error}</p>
  if (!view) return <div className="skeleton board-skel" />

  const refMoves = view.moves.filter((m) => m.refGames > 0 && m.from).sort((a, b) => b.refGames - a.refGames).slice(0, 3)
  const yourTop = view.moves.filter((m) => m.yourGames > 0 && m.from).sort((a, b) => b.yourGames - a.yourGames)[0]
  const arrows = []
  if (showArrows) {
    refMoves.forEach((m) => arrows.push({ from: m.from, to: m.to, color: 'ref', opacity: 0.35 + 0.55 * (m.refShare ?? 0), width: 0.1 + 0.12 * (m.refShare ?? 0) }))
    if (yourTop) arrows.push({ from: yourTop.from, to: yourTop.to, color: who === 'you' ? 'you' : 'them', opacity: 0.85, width: 0.17 })
  }
  const hovered = view.moves.find((m) => m.san === hoverSan && m.from)
  if (hovered) arrows.push({ from: hovered.from, to: hovered.to, color: 'pred', opacity: 0.95, width: 0.2 })

  const pairs = []
  for (let i = 0; i < line.length; i += 2) pairs.push([i, line[i], line[i + 1]])

  return (
    <div className="explorer2">
      <div className="board-area">
        <div className="board-row">
          <ScoreGauge yours={view.yourScore} ref_={view.refScore} label={your} />
          <div className="board-wrap">
            <ChessBoard
              fen={view.fen}
              orientation={orientation}
              lastMove={view.lastFrom ? { from: view.lastFrom, to: view.lastTo } : null}
              check={view.check}
              legalMoves={view.legalMoves}
              onMove={(san) => go([...path, san])}
              arrows={arrows}
            />
          </div>
        </div>
        <div>
          <div className="toolbar">
            <button onClick={() => go([])} disabled={!path.length} title="Start (↑)" aria-label="Start">⏮</button>
            <button onClick={back} disabled={!path.length} title="Back (←)" aria-label="Back">◀</button>
            <button className={`play ${playing ? 'on' : ''}`} onClick={() => setPlaying((p) => !p)} disabled={!nextMove && !playing}
                    title="Auto-play the main line" aria-label={playing ? 'Pause' : 'Play'}>
              {playing ? '❚❚' : '▶'}
            </button>
            <button onClick={forward} disabled={!nextMove} title="Forward (→)" aria-label="Forward">▶</button>
            <button onClick={() => go(line)} disabled={line.length <= path.length} title="End of line (↓)" aria-label="End">⏭</button>
            <span className="sep" />
            <button onClick={() => setFlipped((f) => !f)} title="Flip board (F)" aria-label="Flip board">⇅</button>
            <button className={showArrows ? 'on' : ''} onClick={() => setShowArrows((s) => !s)} title="Show arrows" aria-label="Toggle arrows">➚</button>
            <button onClick={() => { setMuted(!muted); setMutedState(!muted) }} title="Sound" aria-label="Toggle sound">
              {muted ? '🔇' : '🔊'}
            </button>
          </div>
          <p className="board-hint">
            Drag or click pieces to explore any move · right-drag to draw arrows · ← → to step · <span className="key ref" /> strong players{' '}
            <span className={`key ${who === 'you' ? 'you' : 'them'}`} /> {who === 'you' ? 'your' : 'their'} favourite
          </p>
        </div>
      </div>

      <div className="side">
        <div className="card line-card">
          <div className="line-head">
            <span className="label">Line</span>
            {path.length > 0 && (view.inBook ? <span className="badge">in theory</span> : <span className="badge warn">out of theory</span>)}
            {view.check && <span className="badge warn">check</span>}
          </div>
          <div className="movelist">
            {!line.length && <span className="muted small">Start position. Make a move on the board or pick one below.</span>}
            {pairs.map(([i, w, b]) => (
              <span key={i} className="pair">
                <span className="mn">{i / 2 + 1}.</span>
                <button className={`mv ${path.length === i + 1 ? 'cur' : ''} ${i + 1 > path.length ? 'future' : ''}`} onClick={() => go(line.slice(0, i + 1))}>{w}</button>
                {b && <button className={`mv ${path.length === i + 2 ? 'cur' : ''} ${i + 2 > path.length ? 'future' : ''}`} onClick={() => go(line.slice(0, i + 2))}>{b}</button>}
              </span>
            ))}
          </div>
        </div>

        <div className="pos-stats">
          <div className="card mini">
            <span className="label">{You} reached this</span>
            <strong>{view.yourGames} games</strong>
            <ScoreBar score={view.yourScore} />
          </div>
          <div className="card mini">
            <span className="label">Players rated 1700+</span>
            <strong>{view.refGames ? `${view.refGames.toLocaleString()} games` : 'never here'}</strong>
            <ScoreBar score={view.refScore} muted />
          </div>
        </div>

        <div className="card table-card">
          <table className="moves">
            <thead>
              <tr>
                <th>Move</th>
                <th>{who === 'you' ? 'You' : 'Them'}</th>
                <th>Score</th>
                <th>Strong players</th>
              </tr>
            </thead>
            <tbody>
              {view.moves.slice(0, 14).map((m) => (
                <tr key={m.san} onClick={() => go([...path, m.san])} tabIndex={0}
                    onMouseEnter={() => setHoverSan(m.san)} onMouseLeave={() => setHoverSan(null)}
                    onFocus={() => setHoverSan(m.san)} onBlur={() => setHoverSan(null)}
                    onKeyDown={(e) => e.key === 'Enter' && go([...path, m.san])}>
                  <td className="san">
                    {m.san}
                    {!m.inBook && <span className="badge warn">new</span>}
                  </td>
                  <td>{m.yourGames || '—'}</td>
                  <td>{m.yourGames ? <ScoreBar score={m.yourScore} /> : <span className="muted">—</span>}</td>
                  <td>
                    {m.refGames ? (
                      <span className="refcell">
                        <span className="share"><span style={{ width: pct(m.refShare) }} /></span>
                        <span className="small">{pct(m.refShare)}</span>
                        <span className="muted small">{pct(m.refScore)}</span>
                      </span>
                    ) : <span className="muted">—</span>}
                  </td>
                </tr>
              ))}
              {!view.moves.length && (
                <tr><td colSpan="4" className="muted">No games continue from here. You can still play any legal move on the board.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

/** Vertical gauge like an engine bar: your score here, with a tick for strong players' score. */
function ScoreGauge({ yours, ref_, label }) {
  const y = yours == null ? null : Math.round(yours * 100)
  const r = ref_ == null ? null : Math.round(ref_ * 100)
  return (
    <div className="gauge" title={`${label} score ${y ?? '—'}% · strong players ${r ?? '—'}%`}>
      <div className="gauge-fill" style={{ height: `${y ?? 50}%`, opacity: y == null ? 0.25 : 1 }} />
      {r != null && <div className="gauge-tick" style={{ bottom: `${r}%` }} />}
      <span className="gauge-num">{y == null ? '—' : y}</span>
    </div>
  )
}
