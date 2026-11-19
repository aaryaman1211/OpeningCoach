import { useState } from 'react'

const SOURCES = [
  { id: 'lichess', label: 'Lichess' },
  { id: 'chesscom', label: 'Chess.com' },
  { id: 'pgn', label: 'Upload PGN' },
  { id: 'demo', label: 'Demo games' },
]

export default function ImportPanel({ onImport, busy, compact }) {
  const [source, setSource] = useState('lichess')
  const [username, setUsername] = useState('')
  const [colour, setColour] = useState('white')
  const [maxGames, setMaxGames] = useState(500)
  const [includeBullet, setIncludeBullet] = useState(false)
  const [file, setFile] = useState(null)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    setError('')
    const name = source === 'demo' ? 'demo_player' : username.trim()
    if (!/^[A-Za-z0-9_-]{2,30}$/.test(name)) {
      setError('Enter a username: 2–30 letters, digits, _ or -')
      return
    }
    let pgnText
    if (source === 'pgn') {
      if (!file) {
        setError('Choose a .pgn file')
        return
      }
      if (file.size > 20 * 1024 * 1024) {
        setError('PGN file is larger than 20 MB')
        return
      }
      pgnText = await file.text()
    }
    try {
      await onImport({ source, username: name, colour, maxGames: Number(maxGames), includeBullet, pgnText })
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <form className={`card import ${compact ? 'compact' : ''}`} onSubmit={submit}>
      {!compact && (
        <div className="import-intro">
          <h2>Analyse your openings</h2>
          <p>
            Enter your username. OpeningCoach downloads your finished public games and compares your first 10 moves
            with 21,925 games between players rated 1700+.
          </p>
        </div>
      )}
      <div className="seg" role="tablist" aria-label="Game source">
        {SOURCES.map((s) => (
          <button
            type="button"
            key={s.id}
            role="tab"
            aria-selected={source === s.id}
            className={source === s.id ? 'on' : ''}
            onClick={() => setSource(s.id)}
          >
            {s.label}
          </button>
        ))}
      </div>
      <div className="import-row">
        {source !== 'demo' && (
          <label className="field grow">
            <span>{source === 'pgn' ? 'Your name in the PGN' : 'Username'}</span>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder={source === 'chesscom' ? 'e.g. hikaru' : 'e.g. your_lichess_name'}
              autoComplete="off"
              spellCheck="false"
            />
          </label>
        )}
        {source === 'pgn' && (
          <label className="field grow">
            <span>PGN file</span>
            <input type="file" accept=".pgn,text/plain" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </label>
        )}
        <div className="field">
          <span>Your colour</span>
          <div className="seg small">
            {['white', 'black'].map((c) => (
              <button type="button" key={c} className={colour === c ? 'on' : ''} onClick={() => setColour(c)}>
                {c === 'white' ? '♔ White' : '♚ Black'}
              </button>
            ))}
          </div>
        </div>
        {(source === 'lichess' || source === 'chesscom') && (
          <>
            <label className="field narrow">
              <span>Max games</span>
              <input type="number" min="1" max="2000" value={maxGames} onChange={(e) => setMaxGames(e.target.value)} />
            </label>
            <label className="check">
              <input type="checkbox" checked={includeBullet} onChange={(e) => setIncludeBullet(e.target.checked)} />
              Include bullet
            </label>
          </>
        )}
        <button className="primary" disabled={busy}>
          {busy ? 'Analysing…' : 'Analyse'}
        </button>
      </div>
      {source === 'demo' && (
        <p className="hint">
          900 real club-level games from the Lichess January 2013 database, relabelled as one “demo_player” so the app
          works offline.
        </p>
      )}
      {error && <p className="error" role="alert">{error}</p>}
    </form>
  )
}
