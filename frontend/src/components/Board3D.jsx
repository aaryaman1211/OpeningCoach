import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { makePiece, pieceMaterials } from '../three/pieces.js'
import { diffPieces, parseFen } from './ChessBoard.jsx'

// A real 3D board (WebGL, three.js). Same props as ChessBoard. Like the flat board it has no chess
// logic: legal moves come from the Java server in `legalMoves`.
//   mode="board": camera behind the player's side, looking down at the board
//   mode="hero":  decorative corner view for the start screen, slowly drifting

const FILES = 'abcdefgh'
const BORDER = 0.62
const SIZE = 8 + BORDER * 2

const sqPos = (sq) => [FILES.indexOf(sq[0]) - 3.5, 3.5 - (Number(sq[1]) - 1)]
const posSq = (x, z) => {
  const f = Math.floor(x + 4)
  const r = Math.floor(4 - z)
  return f < 0 || f > 7 || r < 0 || r > 7 ? null : FILES[f] + (r + 1)
}
const ease = (k) => 1 - Math.pow(1 - k, 3)

function cssVar(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}
/** CSS colour string -> { color, alpha } (handles #hex and rgba()) */
function cssColor(str) {
  const m = str.match(/rgba?\(([^)]+)\)/)
  if (m) {
    const [r, g, b, a = '1'] = m[1].split(',').map((s) => s.trim())
    return { color: new THREE.Color(`rgb(${r},${g},${b})`), alpha: Number(a) }
  }
  return { color: new THREE.Color(str), alpha: 1 }
}

function boardTexture(orientation, showCoords) {
  const px = 160
  const c = document.createElement('canvas')
  c.width = c.height = Math.round(SIZE * px)
  const g = c.getContext('2d')
  const wood = g.createLinearGradient(0, 0, c.width, c.height)
  wood.addColorStop(0, '#9a7552')
  wood.addColorStop(1, '#84613f')
  g.fillStyle = wood
  g.fillRect(0, 0, c.width, c.height)
  const o = BORDER * px
  const light = cssVar('--sq-light', '#f2e8d5')
  const dark = cssVar('--sq-dark', '#c29a72')
  g.fillStyle = 'rgba(0,0,0,0.25)'
  g.fillRect(o - 6, o - 6, 8 * px + 12, 8 * px + 12)
  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      // canvas row 0 is rank 8 (far side of the scene), column 0 is the a-file
      g.fillStyle = (row + col) % 2 === 0 ? light : dark
      g.fillRect(o + col * px, o + row * px, px, px)
    }
  }
  if (showCoords) {
    g.fillStyle = '#f2e8d5'
    g.font = `600 ${Math.round(px * 0.3)}px system-ui, sans-serif`
    g.textAlign = 'center'
    g.textBaseline = 'middle'
    const flip = orientation === 'black'
    const label = (text, x, y) => {
      g.save()
      g.translate(x, y)
      if (flip) g.rotate(Math.PI)
      g.fillText(text, 0, 0)
      g.restore()
    }
    for (let i = 0; i < 8; i++) {
      const mid = o + i * px + px / 2
      label(FILES[i], mid, flip ? o / 2 : c.height - o / 2)
      label(String(8 - i), flip ? c.width - o / 2 : o / 2, mid)
    }
  }
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.anisotropy = 8
  return tex
}

let checkTex = null
function checkTexture() {
  if (checkTex) return checkTex
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const g = c.getContext('2d')
  const r = g.createRadialGradient(64, 64, 0, 64, 64, 64)
  r.addColorStop(0, 'rgba(200,40,30,0.85)')
  r.addColorStop(0.5, 'rgba(200,40,30,0.4)')
  r.addColorStop(1, 'rgba(200,40,30,0)')
  g.fillStyle = r
  g.fillRect(0, 0, 128, 128)
  checkTex = new THREE.CanvasTexture(c)
  return checkTex
}

function flat(geo, x, z, y, color, alpha, extra = {}) {
  const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color, transparent: true, opacity: alpha, depthWrite: false, toneMapped: false, ...extra }))
  m.rotation.x = -Math.PI / 2
  m.position.set(x, y, z)
  return m
}

function arrowMesh(a, colors) {
  const [x1, z1] = sqPos(a.from)
  const [x2, z2] = sqPos(a.to)
  // work in the board plane: u = x, v = -z
  const dx = x2 - x1, dv = -(z2 - z1)
  const len = Math.hypot(dx, dv)
  const ux = dx / len, uv = dv / len, nx = -uv, nv = ux
  const w = (a.width ?? 0.15) * 0.95
  const head = w * 2.6, hw = w * 1.7
  const end = len - 0.12
  const P = (t, s) => new THREE.Vector2(x1 + ux * t + nx * s, -z1 + uv * t + nv * s)
  const shape = new THREE.Shape([P(0.05, -w / 2), P(end - head, -w / 2), P(end - head, -hw), P(end, 0), P(end - head, hw), P(end - head, w / 2), P(0.05, w / 2)])
  const { color } = colors[a.color] || colors.ref
  const m = new THREE.Mesh(new THREE.ShapeGeometry(shape),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity: a.opacity ?? 0.8, depthWrite: false, depthTest: false, toneMapped: false }))
  m.rotation.x = -Math.PI / 2
  m.position.y = 0.02
  m.renderOrder = 10
  return m
}

function createEngine(container, getProps, onPromotion) {
  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 0.95
  container.appendChild(renderer.domElement)
  const canvas = renderer.domElement

  const scene = new THREE.Scene()
  const pmrem = new THREE.PMREMGenerator(renderer)
  const env = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  scene.environment = env
  scene.environmentIntensity = 0.55

  scene.add(new THREE.HemisphereLight(0xfff4e6, 0x5a4532, 0.9))
  const sun = new THREE.DirectionalLight(0xffffff, 2.2)
  sun.position.set(-4, 11, 6)
  sun.castShadow = true
  sun.shadow.mapSize.set(2048, 2048)
  Object.assign(sun.shadow.camera, { left: -7, right: 7, top: 7, bottom: -7, near: 1, far: 30 })
  sun.shadow.bias = -0.0004
  sun.shadow.normalBias = 0.02
  scene.add(sun)

  const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 100)
  const mode = getProps().mode

  // board: wooden block with the squares painted on top
  const woodSide = new THREE.MeshStandardMaterial({ color: 0x6e4f35, roughness: 0.6 })
  const top = new THREE.MeshStandardMaterial({ roughness: 0.55, metalness: 0 })
  const block = new THREE.Mesh(new THREE.BoxGeometry(SIZE, 0.4, SIZE), [woodSide, woodSide, top, woodSide, woodSide, woodSide])
  block.position.y = -0.2
  block.receiveShadow = true
  block.castShadow = true
  scene.add(block)
  // the top face of a BoxGeometry is mapped with v pointing to -z, which matches the canvas layout

  if (mode === 'hero') {
    const ground = new THREE.Mesh(new THREE.PlaneGeometry(40, 40), new THREE.ShadowMaterial({ opacity: 0.16 }))
    ground.rotation.x = -Math.PI / 2
    ground.position.y = -0.4
    ground.receiveShadow = true
    scene.add(ground)
  }

  const materials = pieceMaterials()
  const marks = new THREE.Group()
  const arrowsG = new THREE.Group()
  scene.add(marks, arrowsG)

  const geo = {
    sq: new THREE.PlaneGeometry(1, 1),
    dot: new THREE.CircleGeometry(0.15, 32),
    ring: new THREE.RingGeometry(0.4, 0.49, 48),
    circle: new THREE.RingGeometry(0.38, 0.45, 48),
  }

  const st = {
    pieces: [], // { id, p, sq, fresh? } - the identities ChessBoard's diff works with
    objs: new Map(), // id -> { group, x, z, from, t0, dur, hop, dying }
    selected: null,
    drag: null,
    hoverSq: null,
    committedFen: null,
    userArrows: [],
    userCircles: [],
    rightStart: null,
    dirty: true,
    visible: true,
    texKey: '',
    colors: {},
    w: 1, h: 1,
    raf: 0,
    t0: performance.now(),
  }

  function readColors() {
    st.colors = {
      last: cssColor(cssVar('--sq-last', 'rgba(223,163,122,0.55)')),
      sel: cssColor(cssVar('--sq-sel', 'rgba(196,83,43,0.35)')),
      ref: cssColor(cssVar('--arrow-ref', '#3b7a4f')),
      you: cssColor(cssVar('--arrow-you', '#2f5f9e')),
      them: cssColor(cssVar('--arrow-them', '#c4532b')),
      user: cssColor(cssVar('--arrow-user', '#b54a26')),
      pred: cssColor(cssVar('--arrow-pred', '#6d6c67')),
    }
  }

  function refreshBoard() {
    const { orientation, showCoords } = getProps()
    const key = `${orientation}|${showCoords}|${document.documentElement.dataset.theme}`
    if (key === st.texKey) return
    st.texKey = key
    top.map?.dispose()
    top.map = boardTexture(orientation, showCoords)
    top.needsUpdate = true
    readColors()
    fitCamera()
    st.dirty = true
  }

  // ---------------------------------------------------------------- camera
  const target = new THREE.Vector3()
  function placeCamera(az, el, r) {
    camera.position.set(target.x + Math.sin(az) * Math.cos(el) * r, target.y + Math.sin(el) * r, target.z + Math.cos(az) * Math.cos(el) * r)
    camera.lookAt(target)
  }
  function view() {
    const { orientation, tilt } = getProps()
    const base = orientation === 'black' ? Math.PI : 0
    if (mode === 'hero') {
      const drift = matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : Math.sin((performance.now() - st.t0) / 9000) * 0.12
      return { az: base + 0.72 + drift, el: (33 * Math.PI) / 180 }
    }
    return { az: base, el: ((90 - (tilt || 36) * 0.95) * Math.PI) / 180 }
  }
  function fitCamera() {
    camera.aspect = st.w / st.h
    camera.updateProjectionMatrix()
    const { az, el } = view()
    const h = SIZE / 2
    const pts = []
    for (const x of [-h, h]) for (const z of [-h, h]) for (const y of [-0.4, mode === 'hero' ? 1.1 : 0.9]) pts.push(new THREE.Vector3(x, y, z))
    target.set(0, 0, 0)
    let r = 22
    const margin = mode === 'hero' ? 0.84 : 0.97
    for (let i = 0; i < 6; i++) {
      placeCamera(az, el, r)
      camera.updateMatrixWorld()
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
      for (const p of pts) {
        const q = p.clone().project(camera)
        minX = Math.min(minX, q.x); maxX = Math.max(maxX, q.x)
        minY = Math.min(minY, q.y); maxY = Math.max(maxY, q.y)
      }
      const span = Math.max((maxX - minX) / 2, (maxY - minY) / 2)
      r *= span / margin
      // re-centre: shift the target along the ground so the board sits in the middle of the frame
      const cy = (maxY + minY) / 2
      const cx = (maxX + minX) / 2
      const right = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 0)
      const fwd = new THREE.Vector3(-Math.sin(az), 0, -Math.cos(az))
      target.addScaledVector(fwd, cy * r * 0.35).addScaledVector(right, cx * r * 0.25)
    }
    placeCamera(az, el, r)
    st.radius = r
    st.dirty = true
  }

  function resize() {
    const rect = container.getBoundingClientRect()
    st.w = Math.max(1, rect.width)
    st.h = Math.max(1, rect.height)
    renderer.setSize(st.w, st.h, false)
    canvas.style.width = '100%'
    canvas.style.height = '100%'
    fitCamera()
  }

  // ---------------------------------------------------------------- pieces
  function addObj(o, fresh) {
    const group = makePiece(o.p, materials)
    group.userData.id = o.id
    const [x, z] = sqPos(o.sq)
    group.position.set(x, 0, z)
    scene.add(group)
    const obj = { group, x, z, from: null, t0: 0, dur: 0, hop: 0, grow: fresh ? performance.now() : 0 }
    if (fresh) group.scale.setScalar(0.01)
    st.objs.set(o.id, obj)
  }

  function moveObj(obj, sq, instant) {
    const [x, z] = sqPos(sq)
    if (obj.x === x && obj.z === z && !obj.from) return
    if (instant) {
      obj.from = null
      obj.x = x; obj.z = z
      obj.group.position.set(x, 0, z)
      return
    }
    const cur = obj.group.position
    const dist = Math.hypot(x - cur.x, z - cur.z)
    obj.from = [cur.x, cur.y, cur.z]
    obj.x = x; obj.z = z
    obj.t0 = performance.now()
    obj.dur = 220 + Math.min(dist, 6) * 45
    obj.hop = obj.group.userData.code.toLowerCase() === 'n' ? 0.55 : Math.min(0.35, 0.08 + dist * 0.04)
  }

  function setPosition(placement, firstTime) {
    const { pieces, gone } = diffPieces(st.pieces, placement)
    st.pieces = pieces
    for (const o of gone) {
      const obj = st.objs.get(o.id)
      if (obj) obj.dying = performance.now()
    }
    for (const o of pieces) {
      const obj = st.objs.get(o.id)
      if (obj?.dying) scene.remove(obj.group)
      if (!obj || obj.dying) addObj(o, !firstTime && o.fresh)
      else moveObj(obj, o.sq, false)
    }
    st.selected = null
    st.committedFen = null
    onPromotion(null)
    updateMarks()
  }

  // ---------------------------------------------------------------- marks and arrows
  function movesFrom() {
    const m = {}
    for (const lm of getProps().legalMoves || []) (m[lm.from] ||= []).push(lm)
    return m
  }
  function placementNow() {
    const pl = {}
    for (const o of st.pieces) pl[o.sq] = o.p
    return pl
  }

  function clear(g) {
    for (const c of [...g.children]) {
      g.remove(c)
      if (c.geometry && !Object.values(geo).includes(c.geometry)) c.geometry.dispose()
      c.material.dispose()
    }
  }

  function updateMarks() {
    const { lastMove, check, arrows = [], fen } = getProps()
    const C = st.colors
    clear(marks)
    clear(arrowsG)
    const at = (sq) => sqPos(sq)
    if (lastMove) {
      for (const sq of [lastMove.from, lastMove.to]) {
        const [x, z] = at(sq)
        marks.add(flat(geo.sq, x, z, 0.003, C.last.color, Math.min(0.8, C.last.alpha + 0.15)))
      }
    }
    if (st.selected) {
      const [x, z] = at(st.selected)
      marks.add(flat(geo.sq, x, z, 0.004, C.sel.color, Math.max(0.35, C.sel.alpha)))
    }
    if (check) {
      const side = (fen.split(' ')[1] || 'w') === 'w' ? 'K' : 'k'
      const k = st.pieces.find((o) => o.p === side)
      if (k) {
        const [x, z] = at(k.sq)
        marks.add(flat(geo.sq, x, z, 0.005, 0xffffff, 1, { map: checkTexture() }))
      }
    }
    if (st.selected) {
      const pl = placementNow()
      const targets = [...new Set((movesFrom()[st.selected] || []).map((m) => m.to))]
      for (const sq of targets) {
        const [x, z] = at(sq)
        marks.add(flat(pl[sq] ? geo.ring : geo.dot, x, z, 0.006, 0x1d1a16, pl[sq] ? 0.3 : 0.28))
        if (sq === st.hoverSq) marks.add(flat(geo.sq, x, z, 0.0055, 0xffffff, 0.35))
      }
    }
    for (const sq of st.userCircles) {
      const [x, z] = at(sq)
      marks.add(flat(geo.circle, x, z, 0.007, C.user.color, 0.9))
    }
    for (const a of [...arrows, ...st.userArrows.map((u) => ({ ...u, color: 'user' }))]) {
      if (a.from && a.to && a.from !== a.to) arrowsG.add(arrowMesh(a, C))
    }
    st.dirty = true
  }

  // ---------------------------------------------------------------- picking
  const ray = new THREE.Raycaster()
  const ground = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0)
  function setRay(e) {
    const rect = canvas.getBoundingClientRect()
    ray.setFromCamera(new THREE.Vector2(((e.clientX - rect.left) / rect.width) * 2 - 1, -((e.clientY - rect.top) / rect.height) * 2 + 1), camera)
  }
  function groundPoint(e, y = 0) {
    setRay(e)
    ground.constant = -y
    return ray.ray.intersectPlane(ground, new THREE.Vector3())
  }
  function squareAt(e) {
    setRay(e)
    const live = []
    for (const [id, obj] of st.objs) if (!obj.dying && id !== st.drag?.id) live.push(obj.group)
    const hit = ray.intersectObjects(live, true)[0]
    if (hit) {
      let g = hit.object
      while (g && g.userData.id == null) g = g.parent
      const o = st.pieces.find((p) => p.id === g?.userData.id)
      if (o) return o.sq
    }
    const p = groundPoint(e)
    return p ? posSq(p.x, p.z) : null
  }

  function ownPiece(sq) {
    const { fen } = getProps()
    const p = placementNow()[sq]
    if (!p) return false
    return (p === p.toUpperCase()) === ((fen.split(' ')[1] || 'w') === 'w') && (movesFrom()[sq] || []).length > 0
  }

  function commit(from, to, instant) {
    const { fen, onMove } = getProps()
    const options = (movesFrom()[from] || []).filter((m) => m.to === to)
    if (!options.length || st.committedFen === fen) return false
    if (options.length > 1) {
      st.selected = null
      onPromotion({ from, to, options })
      returnDragged()
      updateMarks()
      return true
    }
    st.committedFen = fen
    // optimistic: move the piece now; the server's position confirms it a moment later
    const mover = st.pieces.find((o) => o.sq === from)
    if (mover) {
      const victim = st.pieces.find((o) => o.sq === to && o.id !== mover.id)
      if (victim) st.objs.get(victim.id).dying = performance.now()
      st.pieces = st.pieces.filter((o) => o !== victim).map((o) => (o.id === mover.id ? { ...o, sq: to } : o))
      moveObj(st.objs.get(mover.id), to, instant)
    }
    st.selected = null
    updateMarks()
    onMove?.(options[0].san)
    return true
  }

  function returnDragged() {
    const d = st.drag
    if (!d) return
    const o = st.pieces.find((p) => p.id === d.id)
    const obj = st.objs.get(d.id)
    if (o && obj) {
      obj.x = NaN
      moveObj(obj, o.sq, false)
    }
  }

  function onDown(e) {
    const { interactive } = getProps()
    if (!interactive) return
    const sq = squareAt(e)
    if (!sq) return
    if (e.button === 2) {
      st.rightStart = sq
      return
    }
    if (e.button !== 0) return
    st.userArrows = []
    st.userCircles = []
    onPromotion(null)
    if (st.selected && (movesFrom()[st.selected] || []).some((m) => m.to === sq)) {
      commit(st.selected, sq, false)
      return
    }
    if (ownPiece(sq)) {
      st.selected = sq
      const o = st.pieces.find((p) => p.sq === sq)
      st.drag = { id: o.id, sq, sx: e.clientX, sy: e.clientY, moved: false }
      canvas.setPointerCapture?.(e.pointerId)
    } else st.selected = null
    updateMarks()
  }

  function onMoveEvt(e) {
    const d = st.drag
    if (!d) {
      if (getProps().interactive && e.buttons === 0) {
        const sq = squareAt(e)
        canvas.style.cursor = sq && (ownPiece(sq) || (st.selected && (movesFrom()[st.selected] || []).some((m) => m.to === sq))) ? 'pointer' : 'default'
      }
      return
    }
    if (!d.moved && Math.hypot(e.clientX - d.sx, e.clientY - d.sy) > 4) d.moved = true
    if (!d.moved) return
    canvas.style.cursor = 'grabbing'
    const p = groundPoint(e, 0.3)
    const obj = st.objs.get(d.id)
    if (p && obj) {
      obj.from = null
      obj.group.position.set(Math.max(-4.4, Math.min(4.4, p.x)), 0.3, Math.max(-4.4, Math.min(4.4, p.z)))
    }
    const g = groundPoint(e)
    const hover = g ? posSq(g.x, g.z) : null
    if (hover !== st.hoverSq) {
      st.hoverSq = hover
      updateMarks()
    }
    st.dirty = true
  }

  function onUp(e) {
    if (e.button === 2 && st.rightStart) {
      const end = squareAt(e)
      const start = st.rightStart
      st.rightStart = null
      if (!end) return
      if (end === start) st.userCircles = st.userCircles.includes(end) ? st.userCircles.filter((x) => x !== end) : [...st.userCircles, end]
      else {
        const key = start + end
        st.userArrows = st.userArrows.some((x) => x.from + x.to === key) ? st.userArrows.filter((x) => x.from + x.to !== key) : [...st.userArrows, { from: start, to: end }]
      }
      updateMarks()
      return
    }
    const d = st.drag
    if (!d) return
    if (d.moved) {
      const g = groundPoint(e)
      const sq = g ? posSq(g.x, g.z) : null
      const ok = sq && sq !== d.sq && (movesFrom()[d.sq] || []).some((m) => m.to === sq)
      if (!(ok && commit(d.sq, sq, true))) returnDragged()
    }
    st.drag = null
    st.hoverSq = null
    canvas.style.cursor = 'default'
    updateMarks()
  }

  const onCancel = () => {
    returnDragged()
    st.drag = null
    st.hoverSq = null
  }
  const noMenu = (e) => e.preventDefault()
  canvas.addEventListener('pointerdown', onDown)
  window.addEventListener('pointermove', onMoveEvt)
  window.addEventListener('pointerup', onUp)
  window.addEventListener('pointercancel', onCancel)
  canvas.addEventListener('contextmenu', noMenu)

  // ---------------------------------------------------------------- loop
  function tick(now) {
    st.raf = requestAnimationFrame(tick)
    if (!st.visible) return
    let busy = mode === 'hero'
    if (busy) {
      const { az, el } = view()
      placeCamera(az, el, st.radius)
    }
    for (const [id, obj] of st.objs) {
      const g = obj.group
      if (obj.dying) {
        const k = Math.min(1, (now - obj.dying) / 240)
        g.scale.setScalar(Math.max(0.01, 1 - ease(k)))
        if (k >= 1) {
          scene.remove(g)
          st.objs.delete(id)
        }
        busy = true
        continue
      }
      if (obj.grow) {
        const k = Math.min(1, (now - obj.grow) / 260)
        g.scale.setScalar(Math.max(0.01, ease(k)))
        if (k >= 1) obj.grow = 0
        busy = true
      }
      if (obj.from) {
        const k = Math.min(1, (now - obj.t0) / obj.dur)
        const e = ease(k)
        const [fx, fy, fz] = obj.from
        g.position.set(fx + (obj.x - fx) * e, fy * (1 - e) + Math.sin(Math.PI * k) * obj.hop, fz + (obj.z - fz) * e)
        if (k >= 1) {
          obj.from = null
          g.position.set(obj.x, 0, obj.z)
        }
        busy = true
      }
    }
    if (busy || st.dirty) {
      renderer.render(scene, camera)
      st.dirty = false
    }
  }

  const ro = new ResizeObserver(resize)
  ro.observe(container)
  const io = new IntersectionObserver(([en]) => {
    st.visible = en.isIntersecting
    st.dirty = true
  })
  io.observe(container)
  const mo = new MutationObserver(() => {
    refreshBoard()
    updateMarks()
  })
  mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

  resize()
  refreshBoard()
  st.raf = requestAnimationFrame(tick)

  return {
    setPosition,
    refresh() {
      refreshBoard()
      fitCamera()
      updateMarks()
    },
    updateMarks,
    promote(san) {
      st.committedFen = getProps().fen
      getProps().onMove?.(san)
    },
    dispose() {
      cancelAnimationFrame(st.raf)
      ro.disconnect()
      io.disconnect()
      mo.disconnect()
      canvas.removeEventListener('pointerdown', onDown)
      window.removeEventListener('pointermove', onMoveEvt)
      window.removeEventListener('pointerup', onUp)
      window.removeEventListener('pointercancel', onCancel)
      canvas.removeEventListener('contextmenu', noMenu)
      clear(marks)
      clear(arrowsG)
      top.map?.dispose()
      env.dispose()
      pmrem.dispose()
      renderer.dispose()
      renderer.forceContextLoss()
      canvas.remove()
    },
  }
}

const pieceImg = (p) => `/pieces/${p === p.toUpperCase() ? 'w' : 'b'}${p.toUpperCase()}.svg`

export default function Board3D(props) {
  const {
    fen, orientation = 'white', lastMove = null, check = false, legalMoves = [], arrows = [],
    interactive = true, showCoords = true, tilt = 36, mode = 'board', onFail,
  } = props
  const holder = useRef(null)
  const engine = useRef(null)
  const propsRef = useRef(null)
  propsRef.current = { ...props, orientation, interactive, showCoords, tilt, mode, legalMoves, arrows }
  const [promotion, setPromotion] = useState(null)
  const placement = useMemo(() => parseFen(fen), [fen])

  useEffect(() => {
    try {
      engine.current = createEngine(holder.current, () => propsRef.current, setPromotion)
      engine.current.setPosition(parseFen(propsRef.current.fen), true)
    } catch (err) {
      console.warn('3D board unavailable, using the flat board', err)
      onFail?.()
    }
    return () => {
      engine.current?.dispose()
      engine.current = null
    }
  }, [])

  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    engine.current?.setPosition(placement, false)
  }, [placement])

  useEffect(() => engine.current?.refresh(), [orientation, showCoords, tilt])
  useEffect(() => engine.current?.updateMarks(), [lastMove?.from, lastMove?.to, check, JSON.stringify(arrows), legalMoves])

  const sideToMove = fen.split(' ')[1] || 'w'
  return (
    <div className={`board3d ${mode}`} ref={holder} role={mode === 'hero' ? undefined : 'application'}
         aria-label={mode === 'hero' ? undefined : 'Chess board (3D)'}>
      {promotion && (
        <div className="promo3d" onPointerDown={(e) => e.stopPropagation()}>
          {['q', 'r', 'b', 'n'].map((t) => {
            const opt = promotion.options.find((m) => m.promotion === t)
            const p = sideToMove === 'w' ? t.toUpperCase() : t
            return (
              <button key={t} onClick={() => { setPromotion(null); engine.current?.promote(opt.san) }}
                      style={{ backgroundImage: `url(${pieceImg(p)})` }} aria-label={`Promote to ${t}`} />
            )
          })}
        </div>
      )}
    </div>
  )
}
