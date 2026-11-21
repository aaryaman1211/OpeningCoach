import * as THREE from 'three'

// Staunton-style chess pieces modelled in code: each body is a lathe (a profile turned around
// the vertical axis); the knight's head is an extruded, bevelled silhouette. One square = 1 unit.

const V = (r, y) => new THREE.Vector2(r, y)

/** points on a circular arc (centre cy, radius rad) from angle a0 to a1, in degrees */
function arc(cy, rad, a0, a1, n = 10) {
  const pts = []
  for (let i = 0; i <= n; i++) {
    const a = ((a0 + ((a1 - a0) * i) / n) * Math.PI) / 180
    pts.push(V(Math.max(0, Math.cos(a) * rad), cy + Math.sin(a) * rad))
  }
  return pts
}

/** smooth curve through control points (r, y) */
function curve(points, n = 16) {
  return new THREE.SplineCurve(points.map(([r, y]) => V(r, y))).getPoints(n)
}

function base(w) {
  // a stepped, rounded foot shared by every piece; w scales the width
  return [
    V(0, 0), V(0.33 * w, 0), V(0.345 * w, 0.015), V(0.345 * w, 0.05), V(0.33 * w, 0.065),
    ...curve([[0.31 * w, 0.07], [0.29 * w, 0.1], [0.3 * w, 0.125], [0.26 * w, 0.15]], 8),
    V(0.24 * w, 0.16),
  ]
}

const lathe = (pts) => new THREE.LatheGeometry(pts, 48)

function profile(type) {
  switch (type) {
    case 'p':
      return [
        ...base(0.9),
        ...curve([[0.2, 0.17], [0.14, 0.24], [0.105, 0.32], [0.1, 0.37]], 10),
        V(0.17, 0.38), V(0.175, 0.405), V(0.16, 0.42), V(0.09, 0.43),
        ...arc(0.53, 0.13, -50, 90, 14),
      ]
    case 'r':
      return [
        ...base(1),
        ...curve([[0.235, 0.17], [0.2, 0.26], [0.185, 0.45], [0.19, 0.56]], 10),
        V(0.215, 0.58), V(0.225, 0.6), V(0.24, 0.63), V(0.24, 0.7), V(0.17, 0.7), V(0.17, 0.66), V(0, 0.66),
      ]
    case 'b':
      return [
        ...base(0.95),
        ...curve([[0.22, 0.17], [0.15, 0.27], [0.1, 0.42], [0.095, 0.52]], 12),
        V(0.17, 0.53), V(0.18, 0.555), V(0.17, 0.575), V(0.1, 0.585),
        ...curve([[0.1, 0.59], [0.15, 0.66], [0.15, 0.73], [0.1, 0.81], [0.03, 0.86], [0, 0.865]], 18),
      ]
    case 'q':
      return [
        ...base(1.05),
        ...curve([[0.25, 0.17], [0.16, 0.28], [0.105, 0.48], [0.1, 0.62]], 14),
        V(0.2, 0.635), V(0.205, 0.66), V(0.18, 0.68), V(0.11, 0.69),
        ...curve([[0.11, 0.7], [0.14, 0.78], [0.21, 0.88], [0.205, 0.9]], 10),
        V(0.15, 0.91),
        ...curve([[0.15, 0.91], [0.11, 0.95], [0.04, 0.98], [0, 0.985]], 8),
      ]
    case 'k':
      return [
        ...base(1.05),
        ...curve([[0.25, 0.17], [0.165, 0.28], [0.11, 0.5], [0.105, 0.66]], 14),
        V(0.2, 0.675), V(0.205, 0.7), V(0.18, 0.72), V(0.115, 0.73),
        ...curve([[0.115, 0.74], [0.15, 0.82], [0.2, 0.93], [0.195, 0.955]], 10),
        ...curve([[0.195, 0.96], [0.15, 0.99], [0.07, 1.0], [0, 1.0]], 8),
      ]
    case 'n':
      return [...base(1), V(0.22, 0.2), V(0.2, 0.22), V(0, 0.22)]
    default:
      return base(1)
  }
}

function knightHead() {
  // side view of the head, facing +x
  const s = new THREE.Shape()
  s.moveTo(-0.2, 0.2)
  s.bezierCurveTo(-0.24, 0.42, -0.2, 0.6, -0.08, 0.74) // back of the neck
  s.lineTo(-0.02, 0.8)
  s.lineTo(0.0, 0.9) // ear
  s.lineTo(0.06, 0.8)
  s.bezierCurveTo(0.14, 0.76, 0.22, 0.66, 0.29, 0.54) // forehead to nose
  s.bezierCurveTo(0.31, 0.5, 0.3, 0.45, 0.26, 0.44) // muzzle
  s.lineTo(0.16, 0.44)
  s.bezierCurveTo(0.1, 0.44, 0.06, 0.42, 0.05, 0.4) // jaw
  s.bezierCurveTo(0.1, 0.32, 0.2, 0.26, 0.21, 0.2) // chest
  s.lineTo(-0.2, 0.2)
  const g = new THREE.ExtrudeGeometry(s, {
    depth: 0.2, bevelEnabled: true, bevelThickness: 0.06, bevelSize: 0.04, bevelSegments: 5, curveSegments: 24,
  })
  g.translate(0, 0, -0.1)
  g.computeVertexNormals()
  return g
}

let cache = null

/** shared geometries: { p, r, b, q, k, n: [geometry, transform] lists } built once */
export function pieceGeometries() {
  if (cache) return cache
  cache = {}
  for (const t of 'prbqkn') cache[t] = [{ geo: lathe(profile(t)) }]
  // rook battlements
  const merlon = new THREE.BoxGeometry(0.075, 0.08, 0.07)
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2
    cache.r.push({ geo: merlon, pos: [Math.cos(a) * 0.205, 0.74, Math.sin(a) * 0.205], rotY: -a })
  }
  // bishop and queen finials, queen crown points, king cross
  const ball = new THREE.SphereGeometry(1, 20, 14)
  cache.b.push({ geo: ball, pos: [0, 0.89], scale: 0.04 })
  cache.q.push({ geo: ball, pos: [0, 1.02], scale: 0.05 })
  for (let i = 0; i < 9; i++) {
    const a = (i / 9) * Math.PI * 2
    cache.q.push({ geo: ball, pos: [Math.cos(a) * 0.2, 0.905, Math.sin(a) * 0.2], scale: 0.03 })
  }
  cache.k.push({ geo: new THREE.BoxGeometry(0.06, 0.2, 0.06), pos: [0, 1.09] })
  cache.k.push({ geo: new THREE.BoxGeometry(0.17, 0.055, 0.06), pos: [0, 1.11] })
  cache.n.push({ geo: knightHead() })
  return cache
}

export function pieceMaterials() {
  return {
    w: new THREE.MeshPhysicalMaterial({ color: 0xe6dac4, roughness: 0.68, clearcoat: 0.08, clearcoatRoughness: 0.6 }),
    b: new THREE.MeshPhysicalMaterial({ color: 0x2f2b28, roughness: 0.62, clearcoat: 0.1, clearcoatRoughness: 0.6 }),
  }
}

/** a piece as a THREE.Group standing at the origin; code is a FEN letter */
export function makePiece(code, materials) {
  const type = code.toLowerCase()
  const side = code === type ? 'b' : 'w'
  const group = new THREE.Group()
  for (const part of pieceGeometries()[type]) {
    const m = new THREE.Mesh(part.geo, materials[side])
    if (part.pos) m.position.set(part.pos[0], part.pos[1], part.pos[2] ?? 0)
    if (part.scale) m.scale.setScalar(part.scale)
    if (part.rotY) m.rotation.y = part.rotY
    m.castShadow = true
    m.receiveShadow = true
    group.add(m)
  }
  if (type === 'n') {
    // knights look across the board (side-on to the player), turned slightly toward the opponent
    const dx = side === 'w' ? -1 : 1
    const dz = side === 'w' ? -0.25 : 0.25
    group.rotation.y = Math.atan2(-dz, dx)
  }
  group.userData.code = code
  return group
}
