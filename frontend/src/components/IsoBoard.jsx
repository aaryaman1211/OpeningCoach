import { Suspense, lazy, useEffect, useRef, useState } from 'react'

const Board3D = lazy(() => import('./Board3D.jsx'))

// Decorative board for the start screen, looping through the first moves of the Italian Game.
// It uses the WebGL 3D board; the flat-shaded SVG drawing below is the fallback without WebGL.
// No chess rules here: it is a fixed animation.

const W = 66, H = 33, CX = 330, TOP = 150, FR = 14, FT = 16, S = 1.08
const P = (i, j) => [CX + ((i - j) * W) / 2, TOP + ((i + j) * H) / 2]
const pt = (a) => a.map((p) => p.map((v) => Math.round(v * 10) / 10).join(',')).join(' ')

const COL = {
  w: { l: '#FBF9F4', r: '#D9D3C5', t: '#FFFFFF', s: '#8A8475' },
  b: { l: '#4E4C47', r: '#2F2E2B', t: '#6A6863', s: '#141413' },
}
const PROF = {
  p: [[15, 0], [15, 4], [10, 7], [7, 20], [10, 23], [6, 25]],
  r: [[16, 0], [16, 5], [11, 8], [10, 32], [14, 35], [14, 44]],
  b: [[16, 0], [16, 5], [10, 8], [7, 30], [11, 32], [6, 34]],
  q: [[17, 0], [17, 5], [11, 8], [7, 36], [13, 41], [13, 45]],
  k: [[17, 0], [17, 5], [11, 8], [7, 38], [13, 42], [11, 48]],
  n: [[16, 0], [16, 5], [11, 8], [11, 14]],
}
// file, row (0 = black's back rank) -> file, row
const MOVES = [[4, 6, 4, 4], [4, 1, 4, 3], [6, 7, 5, 5], [1, 0, 2, 2], [5, 7, 2, 4], [5, 0, 2, 3]]

function body(x, y, prof, c) {
  prof = prof.map(([r, h]) => [r * S, h * S])
  const L = prof.map(([r, h]) => [x - r, y - h])
  const R = prof.map(([r, h]) => [x + r, y - h])
  const top = prof[prof.length - 1]
  const st = `stroke="${c.s}" stroke-width="0.8"`
  let s = `<ellipse cx="${x}" cy="${y}" rx="${prof[0][0]}" ry="${prof[0][0] * 0.45}" fill="${c.r}" ${st}/>`
  s += `<polygon points="${pt([...L, [x, y - top[1]], [x, y]])}" fill="${c.l}" ${st}/>`
  s += `<polygon points="${pt([[x, y], [x, y - top[1]], ...R.slice().reverse()].reverse())}" fill="${c.r}" ${st}/>`
  s += `<ellipse cx="${x}" cy="${y - top[1]}" rx="${top[0]}" ry="${top[0] * 0.45}" fill="${c.t}" ${st}/>`
  return { s, ty: y - top[1] }
}

function piece(t, side, x, y) {
  const c = COL[side]
  const k = S
  const st = `stroke="${c.s}" stroke-width="0.8"`
  const { s: base, ty } = body(x, y, PROF[t], c)
  let s = base
  if (t === 'p') {
    s += `<circle cx="${x}" cy="${ty - 7 * k}" r="${8.5 * k}" fill="${c.l}" ${st}/>`
    s += `<path d="M${x},${ty - 15.5 * k} a${8.5 * k},${8.5 * k} 0 0 1 0,${17 * k}" fill="${c.r}"/>`
  } else if (t === 'r') {
    for (const o of [-9, 0, 9]) s += `<rect x="${x + o * k - 3 * k}" y="${ty - 7 * k}" width="${6 * k}" height="${7 * k}" fill="${o > 0 ? c.r : c.l}" ${st}/>`
  } else if (t === 'b') {
    s += `<ellipse cx="${x}" cy="${ty - 10 * k}" rx="${8 * k}" ry="${12 * k}" fill="${c.l}" ${st}/>`
    s += `<path d="M${x},${ty - 22 * k} a${8 * k},${12 * k} 0 0 1 0,${24 * k}" fill="${c.r}"/>`
    s += `<line x1="${x - 3 * k}" y1="${ty - 16 * k}" x2="${x + 4 * k}" y2="${ty - 9 * k}" stroke="${c.s}" stroke-width="1.2"/>`
    s += `<circle cx="${x}" cy="${ty - 24 * k}" r="${3 * k}" fill="${c.t}" ${st}/>`
  } else if (t === 'q') {
    s += `<polygon points="${pt([[x - 13 * k, ty], [x - 15 * k, ty - 14 * k], [x - 7 * k, ty - 6 * k], [x, ty - 17 * k], [x + 7 * k, ty - 6 * k], [x + 15 * k, ty - 14 * k], [x + 13 * k, ty]])}" fill="${c.l}" ${st}/>`
    s += `<polygon points="${pt([[x, ty], [x, ty - 17 * k], [x + 7 * k, ty - 6 * k], [x + 15 * k, ty - 14 * k], [x + 13 * k, ty]])}" fill="${c.r}"/>`
    for (const o of [-15, 0, 15]) s += `<circle cx="${x + o * k}" cy="${ty - (o ? 16 : 20) * k}" r="${2.6 * k}" fill="${c.t}" stroke="${c.s}" stroke-width="0.7"/>`
  } else if (t === 'k') {
    s += `<rect x="${x - 2.5 * k}" y="${ty - 20 * k}" width="${5 * k}" height="${20 * k}" fill="${c.l}" ${st}/>`
    s += `<rect x="${x - 8 * k}" y="${ty - 15 * k}" width="${16 * k}" height="${5 * k}" fill="${c.l}" ${st}/>`
    s += `<rect x="${x}" y="${ty - 15 * k}" width="${8 * k}" height="${5 * k}" fill="${c.r}"/>`
  } else if (t === 'n') {
    const d = side === 'w' ? -1 : 1
    const head = [[x - 11 * k, ty], [x - 12 * k, ty - 14 * k], [x + d * 12 * k, ty - 30 * k], [x + d * 4 * k, ty - 42 * k], [x - d * 2 * k, ty - 40 * k], [x - d * 14 * k, ty - 30 * k], [x - d * 12 * k, ty - 24 * k], [x - d * 2 * k, ty - 26 * k], [x - d * 9 * k, ty - 12 * k], [x + 11 * k, ty]]
    s += `<polygon points="${pt(head)}" fill="${c.l}" ${st}/>`
    s += `<polygon points="${pt([[x, ty], [x + d * 12 * k, ty - 30 * k], [x + d * 4 * k, ty - 42 * k], [x + d * k, ty - 30 * k], [x + 11 * d * k, ty]])}" fill="${c.r}" stroke="${c.s}" stroke-width="0.6"/>`
    s += `<circle cx="${x + d * 3 * k}" cy="${ty - 33 * k}" r="1.8" fill="${c.s}"/>`
  }
  return s
}

function startPieces() {
  const back = 'rnbqkbnr'
  const a = []
  for (let i = 0; i < 8; i++) {
    a.push({ t: back[i], c: 'b', i, j: 0 }, { t: 'p', c: 'b', i, j: 1 }, { t: 'p', c: 'w', i, j: 6 }, { t: back[i], c: 'w', i, j: 7 })
  }
  return a
}

function draw(svg, pieces, anim, last) {
  let s = ''
  const g = P(4, 4)
  s += `<ellipse cx="${g[0]}" cy="${g[1] + 40}" rx="300" ry="130" style="fill: var(--iso-ground)"/>`
  const off = FR / (W / 2)
  const A = P(-off, -off), B = P(8 + off, -off), C = P(8 + off, 8 + off), D = P(-off, 8 + off)
  s += `<polygon points="${pt([D, C, [C[0], C[1] + FT], [D[0], D[1] + FT]])}" fill="#7A5A3E"/>`
  s += `<polygon points="${pt([B, C, [C[0], C[1] + FT], [B[0], B[1] + FT]])}" fill="#5E4530"/>`
  s += `<polygon points="${pt([A, B, C, D])}" fill="#93704F" stroke="#A9855F" stroke-width="1.2"/>`
  const on = (o, i, j) => o && ((i === o.fi && j === o.fj) || (i === o.ti && j === o.tj))
  for (let j = 0; j < 8; j++) {
    for (let i = 0; i < 8; i++) {
      const light = (i + j) % 2 === 0
      const hl = on(anim, i, j) || (!anim && on(last, i, j))
      const fill = hl ? (light ? '#F3D2B4' : '#DFA37A') : light ? '#F2E8D5' : '#C29A72'
      s += `<polygon points="${pt([P(i, j), P(i + 1, j), P(i + 1, j + 1), P(i, j + 1)])}" fill="${fill}"/>`
    }
  }
  const list = pieces
    .map((p) => {
      let i = p.i, j = p.j, lift = 0
      if (anim && p === anim.p) {
        i = p.i + (anim.ti - p.i) * anim.k
        j = p.j + (anim.tj - p.j) * anim.k
        lift = Math.sin(anim.k * Math.PI) * 26
      }
      return { p, i, j, lift }
    })
    .sort((a, b) => a.i + a.j - (b.i + b.j))
  for (const o of list) {
    const [x, y] = P(o.i + 0.5, o.j + 0.5)
    s += `<ellipse cx="${x}" cy="${y + 1}" rx="15" ry="6" fill="#000" opacity="${o.lift ? 0.12 : 0.08}"/>`
    s += piece(o.p.t, o.p.c, x, y - o.lift)
  }
  svg.innerHTML = s
}

function IsoSvg() {
  const ref = useRef(null)

  useEffect(() => {
    const svg = ref.current
    let pieces = startPieces()
    let m = 0
    let last = null
    let raf = 0
    let timer = 0
    let alive = true
    draw(svg, pieces, null, null)
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined

    function step() {
      if (!alive) return
      if (m >= MOVES.length) {
        pieces = startPieces()
        m = 0
        last = null
        draw(svg, pieces, null, null)
        timer = setTimeout(step, 1500)
        return
      }
      const [fi, fj, ti, tj] = MOVES[m]
      const p = pieces.find((q) => q.i === fi && q.j === fj)
      const anim = { p, fi, fj, ti, tj, k: 0 }
      const t0 = performance.now()
      const frame = (t) => {
        if (!alive) return
        anim.k = Math.min(1, (t - t0) / 650)
        draw(svg, pieces, anim, last)
        if (anim.k < 1) {
          raf = requestAnimationFrame(frame)
        } else {
          pieces = pieces.filter((q) => !(q.i === ti && q.j === tj))
          p.i = ti
          p.j = tj
          last = { fi, fj, ti, tj }
          draw(svg, pieces, null, last)
          m++
          timer = setTimeout(step, 1000)
        }
      }
      raf = requestAnimationFrame(frame)
    }
    timer = setTimeout(step, 900)
    return () => {
      alive = false
      cancelAnimationFrame(raf)
      clearTimeout(timer)
    }
  }, [])

  return (
    <svg ref={ref} className="iso" viewBox="0 0 640 500" role="img" aria-label="A 3D chess board playing the first moves of the Italian Game" />
  )
}

// the same opening as FEN positions for the 3D board: 1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5
const ITALIAN = [
  ['rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w', null],
  ['rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b', ['e2', 'e4']],
  ['rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w', ['e7', 'e5']],
  ['rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b', ['g1', 'f3']],
  ['r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w', ['b8', 'c6']],
  ['r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b', ['f1', 'c4']],
  ['r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w', ['f8', 'c5']],
]

function hasWebgl() {
  try {
    const c = document.createElement('canvas')
    return !!(c.getContext('webgl2') || c.getContext('webgl'))
  } catch {
    return false
  }
}

export default function IsoBoard() {
  const [ok, setOk] = useState(hasWebgl)
  const [i, setI] = useState(0)
  useEffect(() => {
    if (!ok || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined
    const t = setTimeout(() => setI((n) => (n + 1) % ITALIAN.length), i === 0 ? 1600 : i === ITALIAN.length - 1 ? 2600 : 1250)
    return () => clearTimeout(t)
  }, [i, ok])
  if (!ok) return <IsoSvg />
  const [fen, mv] = ITALIAN[i]
  return (
    <Suspense fallback={<div className="board3d iso3d" />}>
      <Board3D mode="hero" fen={fen} lastMove={mv ? { from: mv[0], to: mv[1] } : null}
               interactive={false} showCoords={false} onFail={() => setOk(false)} />
    </Suspense>
  )
}
