import { useRef, useState } from 'react'
import IsoBoard from './IsoBoard.jsx'

function greeting() {
  const h = new Date().getHours()
  if (h < 12) return 'Rise and castle'
  if (h < 18) return 'Your move, champ'
  return 'Good knight'
}

/**
 * The start screen: greeting, headline, one username bar and three secondary actions,
 * in front of a large isometric board.
 */
export default function StartScreen({ onImport, onScoutAfter }) {
  const [site, setSite] = useState('lichess')
  const [colour, setColour] = useState('white')
  const [username, setUsername] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const fileInput = useRef(null)
  const nameInput = useRef(null)

  async function run(req, which) {
    setError('')
    setBusy(which)
    try {
      await onImport(req)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(null)
    }
  }

  function submit(e) {
    e.preventDefault()
    const name = username.trim()
    if (!/^[A-Za-z0-9_-]{2,30}$/.test(name)) {
      setError(file ? 'Enter your name exactly as it appears in the PGN file.' : 'Enter your username: 2–30 letters, digits, _ or -.')
      nameInput.current?.focus()
      return
    }
    if (file) {
      if (file.size > 20 * 1024 * 1024) return setError('That file is larger than 20 MB.')
      file.text().then((pgnText) => run({ source: 'pgn', username: name, colour, pgnText }, 'go'))
      return
    }
    run({ source: site, username: name, colour, maxGames: 500 }, 'go')
  }

  function pickFile(f) {
    if (!f) return
    setFile(f)
    setError('')
    setNote('')
    nameInput.current?.focus()
  }

  function scout() {
    setNote('First load your own games, then open the Opponent tab to scout your rival.')
    onScoutAfter()
    nameInput.current?.focus()
  }

  return (
    <section className="hero">
      <div className="hero-disc" aria-hidden="true" />
      <div className="hero-board" aria-hidden="true"><IsoBoard /></div>

      <div className="hero-copy">
        <p className="kicker">{greeting()}</p>
        <h1 className="headline">
          Don’t let your opening <span>checkmate you.</span>
        </h1>
        <p className="lede">We check every move you make, so you never get rook’d before move ten.</p>
      </div>

      <form className="hero-cta" onSubmit={submit}>
        <div className="toggles">
          {!file ? (
            <div className="pill-switch" role="radiogroup" aria-label="Site">
              {[['lichess', 'Lichess'], ['chesscom', 'Chess.com']].map(([id, label]) => (
                <button type="button" key={id} role="radio" aria-checked={site === id} className={site === id ? 'on' : ''} onClick={() => setSite(id)}>
                  {label}
                </button>
              ))}
            </div>
          ) : (
            <span className="file-chip">
              {file.name}
              <button type="button" aria-label="Remove file" onClick={() => { setFile(null); fileInput.current.value = '' }}>×</button>
            </span>
          )}
          <div className="pill-switch" role="radiogroup" aria-label="Your colour">
            {[['white', 'as White'], ['black', 'as Black']].map(([id, label]) => (
              <button type="button" key={id} role="radio" aria-checked={colour === id} className={colour === id ? 'on' : ''} onClick={() => setColour(id)}>
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="name-bar">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden="true">
            <circle cx="12" cy="8" r="4" /><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
          </svg>
          <input
            ref={nameInput}
            value={username}
            onChange={(e) => { setUsername(e.target.value); setError('') }}
            placeholder={file ? 'Your name in the file' : 'Your username'}
            aria-label={file ? 'Your name in the PGN file' : 'Your username'}
            autoComplete="off"
            spellCheck="false"
          />
          <button className="make-move" disabled={!!busy}>
            {busy === 'go' ? 'Loading games…' : 'Make your move'}
            {busy !== 'go' && (
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
            )}
          </button>
        </div>
        {error && <p className="hero-error" role="alert">{error}</p>}
        {note && !error && <p className="hero-note">{note}</p>}

        <div className="action-cards">
          <button type="button" disabled={!!busy} onClick={() => run({ source: 'demo', username: 'demo_player', colour }, 'demo')}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinejoin="round" aria-hidden="true"><path d="M8 5l11 7-11 7V5z" /></svg>
            <strong>{busy === 'demo' ? 'Loading…' : 'Demo game'}</strong>
            <small>Try it, no login</small>
          </button>
          <button type="button" onClick={() => fileInput.current.click()}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 16V4M7 9l5-5 5 5M5 20h14" /></svg>
            <strong>Upload PGN</strong>
            <small>Your own file</small>
          </button>
          <button type="button" onClick={scout}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M4 20l6-6M14 10l6-6M4 4l16 16M15 20l5-5M9 4L4 9" /></svg>
            <strong>Scout a rival</strong>
            <small>Prep for a game</small>
          </button>
        </div>
        <input ref={fileInput} type="file" accept=".pgn,text/plain" hidden onChange={(e) => pickFile(e.target.files?.[0])} />
        <p className="fine-print">Only finished public games are read.</p>
      </form>
    </section>
  )
}
