// Thin client for the Java REST API. All algorithms run on the server; this file only moves JSON.

async function request(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!res.ok) {
    const msg = data?.detail || `Server returned ${res.status}`
    throw new Error(msg)
  }
  return data
}

export const api = {
  reference: () => request('GET', '/api/reference'),
  createAnalysis: (body) => request('POST', '/api/analyses', body),
  tree: (id, moves) => request('GET', `/api/analyses/${id}/tree?moves=${encodeURIComponent(moves.join(','))}`),
  weakLines: (id, k = 10, minGames = 3) => request('GET', `/api/analyses/${id}/weak-lines?k=${k}&minGames=${minGames}`),
  outOfBook: (id, k = 10) => request('GET', `/api/analyses/${id}/out-of-book?k=${k}`),
  studyPlan: (id, body) => request('POST', `/api/analyses/${id}/study-plan`, body),
}

export function pct(x) {
  return x == null ? '—' : `${Math.round(x * 100)}%`
}

export function lineText(path) {
  if (!path.length) return 'Start position'
  return path.map((m, i) => (i % 2 === 0 ? `${i / 2 + 1}.${m}` : m)).join(' ')
}
