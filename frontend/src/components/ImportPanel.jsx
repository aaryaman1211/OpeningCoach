import { useState } from 'react'

const SOURCES = [
  { id: 'lichess', label: 'Lichess' },
  { id: 'chesscom', label: 'Chess.com' },
  { id: 'pgn', label: 'PGN file' },
  { id: 'demo', label: 'Demo' },
]

/**
 * Game import form. Used for your own games and (with `opponent`) for an opponent's.
 */
export default function ImportPanel({ onImport, opponent = false, defaultColour = 'white', submitLabel = 'Analyse', onCancel }) {
  const [source, setSource] = useState('lichess')
  const [username, setUsername] = useState('')
  const [colour, setColour] = useState(defaultColour)
  const [maxGames, setMaxGames] = useState(500)
  const [includeBullet, setIncludeBullet] = useState(false)
  const [file, setFile] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setError('')
    const name = source === 'demo' ? 'demo_player' : username.trim()
    if (!/^[A-Za-z0-9_-]{2,30}$/.test(name)) {
      setError('Enter a username: 2–30 letters, digits, _ or -.')
      return
    }
    let pgnText
    if (source === 'pgn') {
      if (!file) return setError('Choose a .pgn file.')
      if (file.size > 20 * 1024 * 1024) return setError('The file is larger than 20 MB.')
      pgnText = await file.text()
    }
    setBusy(true)
    try {
      await onImport({ source, username: name, colour, maxGames: Number(maxGames), includeBullet, pgnText })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const fetching = source === 'lichess' || source === 'chesscom'

  return (
    <form className="import box" onSubmit={submit}>
      <div className="field">
        <span>Games from</span>
        <div className="choice">
          {SOURCES.map((s) => (
            <button type="button" key={s.id} className={source === s.id ? 'on' : ''} onClick={() => setSource(s.id)}>
              {s.label}
            </button>
          ))}
        </div>
      </div>

      {source !== 'demo' && (
        <label className="field">
          <span>{source === 'pgn' ? 'Player name as written in the file' : opponent ? "Opponent's username" : 'Your username'}</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off" spellCheck="false" />
        </label>
      )}
      {source === 'pgn' && (
        <label className="field">
          <span>PGN file</span>
          <input type="file" accept=".pgn,text/plain" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </label>
      )}

      <div className="import-row">
        <div className="field">
          <span>{opponent ? 'They play' : 'Analyse my games as'}</span>
          <div className="choice">
            {['white', 'black'].map((c) => (
              <button type="button" key={c} className={colour === c ? 'on' : ''} onClick={() => setColour(c)}>
                {c === 'white' ? 'White' : 'Black'}
              </button>
            ))}
          </div>
        </div>
        {fetching && (
          <label className="field narrow">
            <span>Up to</span>
            <input type="number" min="1" max="2000" value={maxGames} onChange={(e) => setMaxGames(e.target.value)} aria-label="Maximum games" />
          </label>
        )}
        {fetching && (
          <label className="check">
            <input type="checkbox" checked={includeBullet} onChange={(e) => setIncludeBullet(e.target.checked)} />
            Include bullet
          </label>
        )}
      </div>

      {source === 'demo' && (
        <p className="muted small">
          900 real club games from the Lichess database (January 2013), relabelled as one player so you can try the app
          offline.
        </p>
      )}
      {error && <p className="error small" role="alert">{error}</p>}

      <div className="row">
        <button className="primary" disabled={busy}>{busy ? 'Loading games…' : submitLabel}</button>
        {onCancel && <button type="button" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  )
}
