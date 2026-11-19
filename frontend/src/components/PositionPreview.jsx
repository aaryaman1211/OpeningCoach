import { useEffect, useState } from 'react'
import { api } from '../api.js'
import ChessBoard from './ChessBoard.jsx'

/**
 * A read-only board showing the position after `path`, with arrows for the given moves
 * (e.g. the move the player chose vs the moves strong players chose).
 * marks: [{ san, color: 'you' | 'them' | 'ref' }]
 */
export default function PositionPreview({ analysisId, path, marks = [], orientation }) {
  const [view, setView] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let live = true
    setError('')
    api.tree(analysisId, path)
      .then((res) => live && setView(res.result))
      .catch((e) => live && setError(e.message))
    return () => {
      live = false
    }
  }, [analysisId, path.join(',')])

  if (error) return <p className="error small">{error}</p>
  if (!view) return <div className="skeleton board-skel" />
  const bySan = Object.fromEntries(view.moves.map((m) => [m.san, m]))
  const legalBySan = Object.fromEntries(view.legalMoves.map((m) => [m.san, m]))
  const arrows = marks
    .map((k) => ({ ...k, m: bySan[k.san]?.from ? bySan[k.san] : legalBySan[k.san] }))
    .filter((k) => k.m)
    .map((k) => ({ from: k.m.from, to: k.m.to, color: k.color, opacity: 0.8, width: 0.14 }))

  return (
    <ChessBoard
      fen={view.fen}
      orientation={orientation}
      interactive={false}
      lastMove={view.lastFrom ? { from: view.lastFrom, to: view.lastTo } : null}
      check={view.check}
      arrows={arrows}
    />
  )
}
