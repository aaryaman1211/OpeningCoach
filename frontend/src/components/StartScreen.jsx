import { useEffect, useRef, useState } from 'react'
import IsoBoard from './IsoBoard.jsx'

// Headline intro: the two lines drop in 3D, one after the other, and slam into the page; the page shakes and the board's pieces jump.
// two waves: the top line drops, then the orange line slams down after it
const LINES = [['Don’t let your opening', false], ['checkmate you.', true]]
const IMPACT_MS = 930 // the moment the second line hits
const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

function useImpact() {
  const [impact, setImpact] = useState(false)
  useEffect(() => {
    if (reducedMotion()) return undefined
    const t = setTimeout(() => {
      setImpact(true)
      // remembered too, so a 3D board that finishes loading after the landing still reacts
      window.__ocImpact = { at: performance.now(), seen: false }
      window.dispatchEvent(new CustomEvent('oc-impact'))
    }, IMPACT_MS)
    return () => clearTimeout(t)
  }, [])
  return impact
}

function FallingHeadline() {
  return (
    <div className="headline-wrap">
      <h1 className="headline drop" aria-label="Don’t let your opening checkmate you.">
        {LINES.map(([text, accent], i) => (
          <span key={i} aria-hidden="true" className={`w ${accent ? 'accent' : ''}`}
                style={{ animationDelay: `${0.1 + i * 0.4}s`, '--rz': `${i ? 3 : -4}deg` }}>
            {text}
          </span>
        ))}
      </h1>
    </div>
  )
}

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
  const [username, setUsername] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const fileInput = useRef(null)
  const nameInput = useRef(null)
  const impact = useImpact()

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
      file.text().then((pgnText) => run({ source: 'pgn', username: name, pgnText }, 'go'))
      return
    }
    run({ source: site, username: name, maxGames: 1000 }, 'go')
  }

  function pickFile(f) {
    if (!f) return
    setFile(f)
    setError('')
    setNote('')
    nameInput.current?.focus()
  }

  function scout() {
    setError('')
    setNote('First load your own games, then open the Opponent tab to scout your rival.')
    onScoutAfter()
    nameInput.current?.focus()
  }

  return (
    <section className={`hero ${impact ? 'impact' : ''}`}>
      <div className="hero-disc" aria-hidden="true" />
      <div className="hero-board" aria-hidden="true"><IsoBoard /></div>

      <div className="hero-copy">
        <p className="kicker">{greeting()}</p>
        <FallingHeadline />
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
          <button type="button" disabled={!!busy} onClick={() => run({ source: 'demo', username: 'demo_player' }, 'demo')}>
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
        <p className="fine-print">We analyse your White and Black games together. Only finished public games are read.</p>
      </form>
    </section>
  )
}
