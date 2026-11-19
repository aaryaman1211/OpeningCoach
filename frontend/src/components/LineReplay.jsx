import { useEffect, useState } from 'react'
import { api } from '../api.js'
import ChessBoard from './ChessBoard.jsx'

/**
 * Plays a line move by move on a read-only board. The server replays the moves and returns each
 * position; this component only steps through them.
 */
export default function LineReplay({ moves, orientation = 'white', interval = 900, autoPlay = true, loop = false,
                                     onDone, renderStep, controls = true, arrowsFor }) {
  const [replay, setReplay] = useState(null)
  const [idx, setIdx] = useState(-1)
  const [playing, setPlaying] = useState(autoPlay)
  const [error, setError] = useState('')
  const key = moves.join(',')

  useEffect(() => {
    let live = true
    // keep showing the old line until the new one arrives, then pieces slide back to the start
    api.board(moves)
      .then((r) => {
        if (!live) return
        setReplay(r)
        setIdx(-1)
        setPlaying(autoPlay)
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
        if (loop) setIdx(-1)
        else setPlaying(false)
        onDone?.()
      }, interval * 2.2)
      return () => clearTimeout(t)
    }
    const t = setTimeout(() => setIdx((i) => i + 1), idx === -1 ? interval * 0.8 : interval)
    return () => clearTimeout(t)
  }, [replay, playing, idx])

  if (error) return <p className="error small">{error}</p>
  if (!replay) return <div className="skeleton board-skel" />
  const ply = idx >= 0 ? replay.plies[idx] : null
  const fen = ply ? ply.fen : replay.startFen

  return (
    <div className="replay">
      <ChessBoard
        fen={fen}
        orientation={orientation}
        interactive={false}
        lastMove={ply ? { from: ply.from, to: ply.to } : null}
        arrows={arrowsFor ? arrowsFor(idx, replay.plies) : []}
      />
      {controls && (
        <div className="toolbar compact">
          <button onClick={() => { setIdx(-1); setPlaying(true) }} aria-label="Restart">↺</button>
          <button onClick={() => setIdx((i) => Math.max(-1, i - 1))} aria-label="Back">◀</button>
          <button className={`play ${playing ? 'on' : ''}`} onClick={() => setPlaying((p) => !p)} aria-label={playing ? 'Pause' : 'Play'}>
            {playing ? '❚❚' : '▶'}
          </button>
          <button onClick={() => setIdx((i) => Math.min(replay.plies.length - 1, i + 1))} aria-label="Forward">▶</button>
        </div>
      )}
      {renderStep && renderStep(idx, replay.plies, (i) => { setIdx(i); setPlaying(false) })}
    </div>
  )
}
