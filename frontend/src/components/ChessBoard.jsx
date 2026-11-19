import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'

// Interactive, animated board. It draws positions and lets the user pick moves, but every
// legal move comes from the Java server (legalMoves prop) - there is no chess logic here.

const FILES = 'abcdefgh'
let nextId = 1

export function parseFen(fen) {
  const placement = {}
  const rows = fen.split(' ')[0].split('/')
  rows.forEach((row, i) => {
    let f = 0
    for (const ch of row) {
      if (/\d/.test(ch)) f += Number(ch)
      else {
        placement[FILES[f] + (8 - i)] = ch
        f++
      }
    }
  })
  return placement
}

function toXY(sq, orientation) {
  const f = FILES.indexOf(sq[0])
  const r = Number(sq[1]) - 1
  return orientation === 'white' ? [f, 7 - r] : [7 - f, r]
}

function fromXY(x, y, orientation) {
  if (x < 0 || x > 7 || y < 0 || y > 7) return null
  return orientation === 'white' ? FILES[x] + (8 - y) : FILES[7 - x] + (y + 1)
}

/** Keep piece identities across positions so moved pieces slide instead of blinking. */
function diffPieces(prev, placement) {
  const used = new Set()
  const result = []
  const pending = []
  for (const [sq, p] of Object.entries(placement)) {
    const same = prev.find((o) => !used.has(o.id) && o.sq === sq && o.p === p)
    if (same) {
      used.add(same.id)
      result.push({ id: same.id, p, sq })
    } else pending.push([sq, p])
  }
  for (const [sq, p] of pending) {
    let best = null
    let bestD = Infinity
    for (const o of prev) {
      if (used.has(o.id) || o.p !== p) continue
      const d = Math.abs(FILES.indexOf(o.sq[0]) - FILES.indexOf(sq[0])) + Math.abs(o.sq[1] - sq[1])
      if (d < bestD) {
        bestD = d
        best = o
      }
    }
    if (best) {
      used.add(best.id)
      result.push({ id: best.id, p, sq })
    } else result.push({ id: nextId++, p, sq, fresh: true })
  }
  const gone = prev.filter((o) => !used.has(o.id))
  return { pieces: result, gone }
}

function pieceImg(p) {
  return `/pieces/${p === p.toUpperCase() ? 'w' : 'b'}${p.toUpperCase()}.svg`
}

export default function ChessBoard({
  fen,
  orientation = 'white',
  lastMove = null,
  check = false,
  legalMoves = [],
  onMove,
  arrows = [],
  interactive = true,
  showCoords = true,
  tilt = 0,
}) {
  const placement = useMemo(() => parseFen(fen), [fen])
  const sideToMove = fen.split(' ')[1] || 'w'
  const [pieces, setPieces] = useState(() => diffPieces([], placement).pieces)
  const [ghosts, setGhosts] = useState([])
  const [selected, setSelected] = useState(null)
  const [drag, setDrag] = useState(null) // {sq, id, x, y, moved}
  const [instantId, setInstantId] = useState(null)
  const [promotion, setPromotion] = useState(null) // {from, to, options}
  const [userArrows, setUserArrows] = useState([])
  const [userCircles, setUserCircles] = useState([])
  const rightStart = useRef(null)
  const committedFen = useRef(null)
  const boardRef = useRef(null)
  const stageRef = useRef(null)
  const is3d = tilt > 0
  const piecesRef = useRef(pieces)
  piecesRef.current = pieces

  // new position from the server -> diff against what is on screen
  useLayoutEffect(() => {
    const { pieces: next, gone } = diffPieces(piecesRef.current, placement)
    setPieces(next)
    if (gone.length) {
      setGhosts((g) => [...g, ...gone.map((o) => ({ ...o, ghost: true }))])
      const ids = new Set(gone.map((o) => o.id))
      setTimeout(() => setGhosts((g) => g.filter((o) => !ids.has(o.id))), 260)
    }
    setSelected(null)
    setPromotion(null)
    committedFen.current = null
  }, [placement])

  useEffect(() => {
    if (instantId == null) return
    const t = requestAnimationFrame(() => requestAnimationFrame(() => setInstantId(null)))
    return () => cancelAnimationFrame(t)
  }, [instantId])

  const movesFrom = useMemo(() => {
    const m = {}
    for (const lm of legalMoves) (m[lm.from] ||= []).push(lm)
    return m
  }, [legalMoves])

  const targets = selected ? [...new Set((movesFrom[selected] || []).map((m) => m.to))] : []
  const kingSquare = check
    ? Object.entries(placement).find(([, p]) => p === (sideToMove === 'w' ? 'K' : 'k'))?.[0]
    : null

  function squareAt(clientX, clientY) {
    if (is3d) {
      // the board is tilted in 3D: ask the browser which square (or piece) is under the pointer
      const el = document.elementFromPoint(clientX, clientY)
      const hit = el?.closest?.('[data-square]')
      return hit && boardRef.current?.contains(hit) ? hit.dataset.square : null
    }
    const rect = boardRef.current.getBoundingClientRect()
    const size = rect.width / 8
    return fromXY(Math.floor((clientX - rect.left) / size), Math.floor((clientY - rect.top) / size), orientation)
  }

  function ownPiece(sq) {
    const p = placement[sq]
    if (!p) return false
    return (p === p.toUpperCase()) === (sideToMove === 'w') && (movesFrom[sq] || []).length > 0
  }

  function commit(from, to, instant) {
    const options = (movesFrom[from] || []).filter((m) => m.to === to)
    if (!options.length) return false
    // one move per position: ignore a duplicate commit (e.g. click + drop for the same gesture)
    if (committedFen.current === fen) return false
    if (options.length > 1) {
      setPromotion({ from, to, options })
      setSelected(null)
      return true
    }
    const move = options[0]
    committedFen.current = fen
    // optimistic: slide (or drop) the piece now; the server's position confirms it a moment later
    const mover = piecesRef.current.find((o) => o.sq === from)
    if (mover) {
      if (instant) setInstantId(mover.id)
      setPieces((ps) => ps.filter((o) => o.sq !== to || o.id === mover.id).map((o) => (o.id === mover.id ? { ...o, sq: to } : o)))
    }
    setSelected(null)
    onMove?.(move.san)
    return true
  }

  function onPointerDown(e) {
    if (!interactive) return
    const sq = squareAt(e.clientX, e.clientY)
    if (!sq) return
    if (e.button === 2) {
      rightStart.current = sq
      return
    }
    if (e.button !== 0) return
    dragRef.current = null // drop any drag whose release we never saw
    setDrag(null)
    setUserArrows([])
    setUserCircles([])
    setPromotion(null)
    if (selected && targets.includes(sq)) {
      commit(selected, sq, false)
      return
    }
    if (ownPiece(sq)) {
      setSelected(sq)
      const mover = piecesRef.current.find((o) => o.sq === sq)
      const rect = (is3d ? stageRef.current : boardRef.current).getBoundingClientRect()
      const d = { sq, id: mover?.id, p: mover?.p, x: e.clientX - rect.left, y: e.clientY - rect.top, cx: e.clientX, cy: e.clientY, moved: false, sx: e.clientX, sy: e.clientY }
      dragRef.current = d
      setDrag(d)
    } else setSelected(null)
  }

  // While dragging, listen on the window so a drop outside the board (or a lost pointer capture)
  // still ends the drag. dragRef guards against pointerup + mouseup both firing.
  const dragRef = useRef(null)
  dragRef.current = drag
  useEffect(() => {
    if (!drag) return
    const move = (e) => {
      const d = dragRef.current
      if (!d || !boardRef.current) return
      const rect = (is3d ? stageRef.current : boardRef.current).getBoundingClientRect()
      const moved = d.moved || Math.hypot(e.clientX - d.sx, e.clientY - d.sy) > 4
      const next = { ...d, x: e.clientX - rect.left, y: e.clientY - rect.top, cx: e.clientX, cy: e.clientY, moved }
      dragRef.current = next
      setDrag(next)
    }
    const up = (e) => {
      const d = dragRef.current
      if (!d) return
      dragRef.current = null
      setDrag(null)
      if (d.moved || Math.hypot(e.clientX - d.sx, e.clientY - d.sy) > 4) {
        const sq = squareAt(e.clientX, e.clientY)
        if (sq && sq !== d.sq && (movesFrom[d.sq] || []).some((m) => m.to === sq)) commit(d.sq, sq, true)
      }
    }
    const cancel = () => {
      dragRef.current = null
      setDrag(null)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
    window.addEventListener('mouseup', up)
    window.addEventListener('pointercancel', cancel)
    return () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
      window.removeEventListener('mouseup', up)
      window.removeEventListener('pointercancel', cancel)
    }
  }, [drag != null])

  function onPointerUp(e) {
    if (e.button === 2 && rightStart.current) {
      const end = squareAt(e.clientX, e.clientY)
      const start = rightStart.current
      rightStart.current = null
      if (!end) return
      if (end === start) {
        setUserCircles((c) => (c.includes(end) ? c.filter((x) => x !== end) : [...c, end]))
      } else {
        const key = start + end
        setUserArrows((a) => (a.some((x) => x.from + x.to === key) ? a.filter((x) => x.from + x.to !== key) : [...a, { from: start, to: end }]))
      }
    }
  }

  const hoverSq = drag?.moved && boardRef.current
    ? is3d ? squareAt(drag.cx, drag.cy) : (() => {
        const size = boardRef.current.getBoundingClientRect().width / 8
        return fromXY(Math.floor(drag.x / size), Math.floor(drag.y / size), orientation)
      })()
    : null

  const allArrows = [...arrows, ...userArrows.map((a) => ({ ...a, color: 'user' }))]
  const promoColor = sideToMove

  const board = (
    <div
      className={`cb ${interactive ? 'interactive' : ''} ${is3d ? 'is3d' : ''}`}
      ref={boardRef}
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
      onContextMenu={(e) => e.preventDefault()}
      role="application"
      aria-label="Chess board"
    >
      {/* squares */}
      {Array.from({ length: 64 }, (_, i) => {
        const x = i % 8
        const y = Math.floor(i / 8)
        const sq = fromXY(x, y, orientation)
        const dark = (FILES.indexOf(sq[0]) + Number(sq[1])) % 2 === 1 // a1 is dark
        const cls = [
          'cb-sq',
          dark ? 'dark' : 'light',
          lastMove && (sq === lastMove.from || sq === lastMove.to) ? 'last' : '',
          sq === selected ? 'sel' : '',
          sq === hoverSq && targets.includes(sq) ? 'hover' : '',
          sq === kingSquare ? 'check' : '',
        ].join(' ')
        return (
          <div key={sq} className={cls} data-square={sq} style={{ left: `${x * 12.5}%`, top: `${y * 12.5}%` }}>
            {showCoords && x === 0 && <span className="cb-rank">{sq[1]}</span>}
            {showCoords && y === 7 && <span className="cb-file">{sq[0]}</span>}
            {targets.includes(sq) && <span className={placement[sq] ? 'cb-ring' : 'cb-dot'} />}
            {userCircles.includes(sq) && <span className="cb-circle" />}
          </div>
        )
      })}

      {/* pieces (and fading captured pieces) */}
      {[...ghosts, ...pieces].map((o) => {
        const [x, y] = toXY(o.sq, orientation)
        const dragging = drag?.moved && drag.id === o.id
        if (is3d) {
          // a standing piece: positioned on its square, the image turned back up to face the viewer
          return (
            <div
              key={`${o.ghost ? 'g' : 'p'}${o.id}`}
              className={`cb-pc3 ${o.ghost ? 'ghost' : ''} ${o.fresh ? 'fresh' : ''} ${dragging ? 'lifted' : ''}`}
              style={{ transform: `translate(${x * 100}%, ${y * 100}%)`, ...(o.id === instantId ? { transition: 'none' } : {}) }}
            >
              <span className="pc-shadow" />
              <img src={pieceImg(o.p)} alt="" data-square={o.ghost ? undefined : o.sq} draggable="false"
                   style={{ transform: `rotateX(${-tilt}deg)` }} />
            </div>
          )
        }
        const style = dragging
          ? { transform: `translate(calc(${drag.x}px - 50%), calc(${drag.y}px - 50%))`, transition: 'none', zIndex: 30 }
          : { transform: `translate(${x * 100}%, ${y * 100}%)`, ...(o.id === instantId ? { transition: 'none' } : {}) }
        return (
          <div
            key={`${o.ghost ? 'g' : 'p'}${o.id}`}
            className={`cb-pc ${o.ghost ? 'ghost' : ''} ${o.fresh ? 'fresh' : ''} ${dragging ? 'dragging' : ''}`}
            style={{ ...style, backgroundImage: `url(${pieceImg(o.p)})` }}
          />
        )
      })}

      {/* arrows */}
      <svg className="cb-arrows" viewBox="0 0 8 8" aria-hidden="true">
        <defs>
          {['ref', 'you', 'them', 'user', 'pred'].map((c) => (
            <marker key={c} id={`ah-${c}`} markerWidth="3.2" markerHeight="3.2" refX="1.6" refY="1.6" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L3.2,1.6 L0,3.2 z" className={`ah ${c}`} />
            </marker>
          ))}
        </defs>
        {allArrows.map((a, i) => {
          const [x1, y1] = toXY(a.from, orientation)
          const [x2, y2] = toXY(a.to, orientation)
          const cx1 = x1 + 0.5, cy1 = y1 + 0.5, cx2 = x2 + 0.5, cy2 = y2 + 0.5
          const len = Math.hypot(cx2 - cx1, cy2 - cy1)
          const shorten = 0.42
          const ex = cx2 - ((cx2 - cx1) / len) * shorten
          const ey = cy2 - ((cy2 - cy1) / len) * shorten
          const w = a.width ?? 0.15
          return (
            <line
              key={i + a.from + a.to + a.color}
              x1={cx1} y1={cy1} x2={ex} y2={ey}
              className={`arrow ${a.color}`}
              strokeWidth={w}
              style={{ opacity: a.opacity ?? 0.8 }}
              markerEnd={`url(#ah-${a.color})`}
            />
          )
        })}
      </svg>

      {/* promotion picker */}
      {promotion && (() => {
        const [x, y] = toXY(promotion.to, orientation)
        const down = y === 0
        return (
          <div className="cb-promo" style={{ left: `${x * 12.5}%`, top: down ? 0 : 'auto', bottom: down ? 'auto' : 0 }}>
            {['q', 'r', 'b', 'n'].map((t) => {
              const opt = promotion.options.find((m) => m.promotion === t)
              const p = promoColor === 'w' ? t.toUpperCase() : t
              return (
                <button key={t} onPointerDown={(e) => e.stopPropagation()}
                        onClick={() => { setPromotion(null); committedFen.current = fen; onMove?.(opt.san) }}
                        style={{ backgroundImage: `url(${pieceImg(p)})` }} aria-label={`Promote to ${t}`} />
              )
            })}
          </div>
        )
      })()}
    </div>
  )

  if (!is3d) return <div className="frame">{board}</div>

  // perspective view: the board lies on a table tilted away from the viewer
  const lift = -(1 - Math.cos((tilt * Math.PI) / 180)) * 100 + 12
  return (
    <div className="stage3d" ref={stageRef} style={{ marginTop: `${lift}%` }}>
      <div className="frame tilted" style={{ transform: `rotateX(${tilt}deg)` }}>
        <span className="frame-edge" aria-hidden="true" />
        {board}
      </div>
      {drag?.moved && drag.p && (
        <img className="drag-ghost" src={pieceImg(drag.p)} alt="" style={{ left: drag.x, top: drag.y }} />
      )}
    </div>
  )
}
