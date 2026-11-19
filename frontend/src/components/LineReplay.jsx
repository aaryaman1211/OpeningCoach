import { useEffect, useState } from 'react'
import { api } from '../api.js'
import ChessBoard from './ChessBoard.jsx'
import { Next, Pause, Play, Prev, Restart } from './Icons.jsx'

/**
 * Steps through a line on a read-only board. The server replays the moves and returns every
 * position; this component only moves between them.
 *
 * startAt: 'start' plays from move 1; 'end' opens on the final position (press play to watch).
 */
export default function LineReplay({ moves, orientation = 'white', interval = 900, autoPlay = true, onDone,
                                     controls = true, renderSteps, arrowsAt, startAt = 'start' }) {
  const [replay, setReplay] = useState(null)
  const [idx, setIdx] = useState(-1)
  const [playing, setPlaying] = useState(autoPlay && startAt === 'start')
  const [error, setError] = useState('')
  const key = moves.join(',')

  useEffect(() => {
    let live = true
    // keep the previous line on screen until the new one arrives, so pieces slide instead of flashing
    api.board(moves)
      .then((r) => {
        if (!live) return
        setReplay(r)
        setIdx(startAt === 'end' ? r.plies.length - 1 : -1)
        setPlaying(autoPlay && startAt === 'start')
      })
      .catch((e) => live && setError(e.message))
    return () => {
      live = false
    }
  }, [key])

  useEffect(() => {
    if (!replay || !playing) return
    if (idx >= replay.plies.length - 1) {
      const t = setTimeout(() => {
        setPlaying(false)
        onDone?.()
      }, interval * 2)
      return () => clearTimeout(t)
    }
    const t = setTimeout(() => setIdx((i) => i + 1), interval)
    return () => clearTimeout(t)
  }, [replay, playing, idx])

  if (error) return <p className="error small">{error}</p>
  if (!replay) return <div className="skeleton board-skel" />
  const ply = idx >= 0 ? replay.plies[idx] : null
  const atEnd = idx >= replay.plies.length - 1

  return (
    <div>
      <ChessBoard
        fen={ply ? ply.fen : replay.startFen}
        orientation={orientation}
        interactive={false}
        lastMove={ply ? { from: ply.from, to: ply.to } : null}
        arrows={arrowsAt ? arrowsAt(idx, replay.plies) : []}
      />
      {controls && (
        <div className="controls">
          <button className="icon" onClick={() => { setIdx(-1); setPlaying(false) }} aria-label="Back to start" title="Back to start"><Restart /></button>
          <button className="icon" onClick={() => { setPlaying(false); setIdx((i) => Math.max(-1, i - 1)) }} disabled={idx < 0} aria-label="Previous move" title="Previous move"><Prev /></button>
          <button className="icon" onClick={() => { if (atEnd) setIdx(-1); setPlaying((p) => !p) }} aria-label={playing ? 'Pause' : 'Play'} title={playing ? 'Pause' : 'Play the line'}>
            {playing ? <Pause /> : <Play />}
          </button>
          <button className="icon" onClick={() => { setPlaying(false); setIdx((i) => Math.min(replay.plies.length - 1, i + 1)) }} disabled={atEnd} aria-label="Next move" title="Next move"><Next /></button>
          <span className="muted small">{idx < 0 ? 'Start' : `Move ${Math.floor(idx / 2) + 1}${idx % 2 ? '…' : '.'} ${replay.plies[idx].san}`}</span>
        </div>
      )}
      {renderSteps && renderSteps(idx, (i) => { setIdx(i); setPlaying(false) })}
    </div>
  )
}
