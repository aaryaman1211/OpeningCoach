// Client for the Java REST API. All algorithms and chess rules run on the server; this file
// moves JSON and makes the app survive server restarts:
//  - if the server is unreachable, it waits (up to ~45 s) for it to come back, then retries;
//  - if the server restarted and forgot an analysis (404), it re-imports the same games once
//    and retries with the new id.

const listeners = new Set()
let online = true

/** Subscribe to connection changes; returns an unsubscribe function. */
export function onConnection(fn) {
  listeners.add(fn)
  fn(online)
  return () => listeners.delete(fn)
}

function setOnline(v) {
  if (online === v) return
  online = v
  listeners.forEach((fn) => fn(v))
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function waitForServer(maxMs = 45000) {
  const start = Date.now()
  while (Date.now() - start < maxMs) {
    try {
      const r = await fetch('/api/reference', { cache: 'no-store' })
      if (r.ok) {
        setOnline(true)
        return true
      }
    } catch {
      /* still down */
    }
    await sleep(1500)
  }
  return false
}

// Re-import hook set by the app: (oldId) => Promise<newId | null>
let recoverAnalysis = null
const recovering = new Map()
export function setAnalysisRecovery(fn) {
  recoverAnalysis = fn
}

async function rawFetch(method, path, body) {
  return fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
}

async function request(method, path, body, attempt = 0) {
  let res
  try {
    res = await rawFetch(method, path, body)
  } catch {
    res = null
  }
  // the dev proxy answers 500/502/503/504 without a JSON body when the Java server is down
  const proxyDown = res && !res.headers.get('content-type')?.includes('json') && [500, 502, 503, 504].includes(res.status)
  if (!res || proxyDown) {
    setOnline(false)
    if (attempt < 1 && (await waitForServer())) return request(method, path, body, attempt + 1)
    throw new Error('The OpeningCoach server is not running. Start it with ./run.sh in the project folder, then try again.')
  }
  setOnline(true)

  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }

  if (res.status === 404 && recoverAnalysis && attempt < 2) {
    const m = path.match(/^\/api\/analyses\/([0-9a-f-]{36})/)
    if (m) {
      const oldId = m[1]
      if (!recovering.has(oldId)) recovering.set(oldId, recoverAnalysis(oldId))
      const newId = await recovering.get(oldId)
      if (newId) return request(method, path.split(oldId).join(newId), body, attempt + 1)
    }
  }

  if (!res.ok) throw new Error(data?.detail || `Something went wrong on the server (${res.status}).`)
  return data
}

const moveParam = (moves) => encodeURIComponent(moves.join(','))

export const api = {
  reference: () => request('GET', '/api/reference'),
  createAnalysis: (body) => request('POST', '/api/analyses', body),
  createPair: (body) => request('POST', '/api/analyses/pair', body),
  highlights: (id, k = 6, minOpponentElo = 0) => request('GET', `/api/analyses/${id}/highlights?k=${k}&minOpponentElo=${minOpponentElo}`),
  tree: (id, moves) => request('GET', `/api/analyses/${id}/tree?moves=${moveParam(moves)}`),
  weakLines: (id, k = 10, minGames = 3) => request('GET', `/api/analyses/${id}/weak-lines?k=${k}&minGames=${minGames}`),
  outOfBook: (id, k = 10) => request('GET', `/api/analyses/${id}/out-of-book?k=${k}`),
  studyPlan: (id, body) => request('POST', `/api/analyses/${id}/study-plan`, body),
  topLines: (id, k = 8, depth = 6) => request('GET', `/api/analyses/${id}/top-lines?k=${k}&depth=${depth}`),
  matchup: (id, opponentId, minGames = 2, k = 6) =>
    request('GET', `/api/analyses/${id}/matchup/${opponentId}?minGames=${minGames}&k=${k}`),
  board: (moves) => request('GET', `/api/board?moves=${moveParam(moves)}`),
  playPosition: (moves) => request('GET', `/api/play/position?moves=${moveParam(moves)}`),
  playOpponent: (body) => request('POST', '/api/play/opponent', body),
  playReview: (moves, move) => request('POST', '/api/play/review', { moves, move }),
}

export function pct(x) {
  return x == null ? '—' : `${Math.round(x * 100)}%`
}

export function lineText(path) {
  if (!path.length) return 'Start position'
  return path.map((m, i) => (i % 2 === 0 ? `${i / 2 + 1}.${m}` : m)).join(' ')
}
