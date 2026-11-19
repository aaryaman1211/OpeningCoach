// Thin client for the Java REST API. All algorithms (and all chess rules) run on the server;
// this file only moves JSON.

const OFFLINE = "Can't reach the OpeningCoach server. Start it with: java -jar api/target/openingcoach-api-1.0.0.jar"

async function request(method, path, body) {
  let res
  try {
    res = await fetch(path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new Error(OFFLINE)
  }
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!res.ok) {
    // the dev proxy answers 500/502/504 with no JSON body when the Java server is down
    if (!data && [500, 502, 503, 504].includes(res.status)) throw new Error(OFFLINE)
    const msg = data?.detail || `Something went wrong on the server (${res.status}).`
    throw new Error(msg)
  }
  return data
}

const moveParam = (moves) => encodeURIComponent(moves.join(','))

export const api = {
  reference: () => request('GET', '/api/reference'),
  createAnalysis: (body) => request('POST', '/api/analyses', body),
  tree: (id, moves) => request('GET', `/api/analyses/${id}/tree?moves=${moveParam(moves)}`),
  weakLines: (id, k = 10, minGames = 3) => request('GET', `/api/analyses/${id}/weak-lines?k=${k}&minGames=${minGames}`),
  outOfBook: (id, k = 10) => request('GET', `/api/analyses/${id}/out-of-book?k=${k}`),
  studyPlan: (id, body) => request('POST', `/api/analyses/${id}/study-plan`, body),
  topLines: (id, k = 8, depth = 6) => request('GET', `/api/analyses/${id}/top-lines?k=${k}&depth=${depth}`),
  matchup: (id, opponentId, minGames = 2, k = 6) =>
    request('GET', `/api/analyses/${id}/matchup/${opponentId}?minGames=${minGames}&k=${k}`),
  board: (moves) => request('GET', `/api/board?moves=${moveParam(moves)}`),
}

export function pct(x) {
  return x == null ? '—' : `${Math.round(x * 100)}%`
}

export function lineText(path) {
  if (!path.length) return 'Start position'
  return path.map((m, i) => (i % 2 === 0 ? `${i / 2 + 1}.${m}` : m)).join(' ')
}
