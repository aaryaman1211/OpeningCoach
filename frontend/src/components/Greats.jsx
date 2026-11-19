import { useRef, useState } from 'react'
import { api } from '../api.js'
import Explorer from './Explorer.jsx'
import LineReplay from './LineReplay.jsx'
import PageHeader from './PageHeader.jsx'

// Famous games are stored as move lists only; the server replays and checks every move.
// Each one was verified to replay legally to its known final move.
const GREATS = [
  {
    id: 'carlsen', name: 'Magnus Carlsen', initials: 'MC', era: 'Norway · born 1990',
    fact: 'World Champion 2013–2023. Peak rating 2882, the highest ever recorded.',
    game: {
      title: 'Carlsen vs Ernst', event: 'Wijk aan Zee, 2004', side: 'white', result: '1-0',
      blurb: 'Carlsen was 13. He opens up the black king with sacrifices and finishes with mate.',
      moves: 'e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6 Nf3 Nd7 h5 Bh7 Bd3 Bxd3 Qxd3 e6 Bf4 Ngf6 O-O-O Be7 Ne4 Qa5 Kb1 O-O Nxf6+ Nxf6 Ne5 Rad8 Qe2 c5 Ng6 fxg6 Qxe6+ Kh8 hxg6 Ng8 Bxh6 gxh6 Rxh6+ Nxh6 Qxe7 Nf7 gxf7 Kg7 Rd3 Rd6 Rg3+ Rg6 Qe5+ Kxf7 Qf5+ Rf6 Qd7#',
      key: { ply: 34, text: '18.Ng6! The knight is given up to rip open the f-file and the black king. Three moves later Bxh6 and Rxh6+ finish the attack.' },
    },
  },
  {
    id: 'kasparov', name: 'Garry Kasparov', initials: 'GK', era: 'Russia · born 1963',
    fact: 'World Champion 1985–2000 and world number one for about twenty years.',
    game: {
      title: 'Kasparov vs Topalov', event: 'Wijk aan Zee, 1999', side: 'white', result: '1-0',
      blurb: 'Often called Kasparov’s Immortal: a rook sacrifice that chases the black king across the board.',
      moves: 'e4 d6 d4 Nf6 Nc3 g6 Be3 Bg7 Qd2 c6 f3 b5 Nge2 Nbd7 Bh6 Bxh6 Qxh6 Bb7 a3 e5 O-O-O Qe7 Kb1 a6 Nc1 O-O-O Nb3 exd4 Rxd4 c5 Rd1 Nb6 g3 Kb8 Na5 Ba8 Bh3 d5 Qf4+ Ka7 Rhe1 d4 Nd5 Nbxd5 exd5 Qd6 Rxd4 cxd4 Re7+ Kb6 Qxd4+ Kxa5 b4+ Ka4 Qc3 Qxd5 Ra7 Bb7 Rxb7 Qc4 Qxf6 Kxa3 Qxa6+ Kxb4 c3+ Kxc3 Qa1+ Kd2 Qb2+ Kd1 Bf1 Rd2 Rd7 Rxd7 Bxc4 bxc4 Qxh8 Rd3 Qa8 c3 Qa4+ Ke1 f4 f5 Kc1 Rd2 Qa7',
      key: { ply: 46, text: '24.Rxd4!! Kasparov gives up a whole rook. After cxd4 Re7+ the black king is dragged up the board to a4 and a3.' },
    },
  },
  {
    id: 'fischer', name: 'Bobby Fischer', initials: 'BF', era: 'USA · 1943–2008',
    fact: 'World Champion 1972–1975. Won two Candidates matches 6–0 on his way to the title.',
    game: {
      title: 'Byrne vs Fischer', event: 'New York, 1956', side: 'black', result: '0-1',
      blurb: '“The Game of the Century”. Fischer, aged 13, gives up his queen and wins with his minor pieces.',
      moves: 'Nf3 Nf6 c4 g6 Nc3 Bg7 d4 O-O Bf4 d5 Qb3 dxc4 Qxc4 c6 e4 Nbd7 Rd1 Nb6 Qc5 Bg4 Bg5 Na4 Qa3 Nxc3 bxc3 Nxe4 Bxe7 Qb6 Bc4 Nxc3 Bc5 Rfe8+ Kf1 Be6 Bxb6 Bxc4+ Kg1 Ne2+ Kf1 Nxd4+ Kg1 Ne2+ Kf1 Nc3+ Kg1 axb6 Qb4 Ra4 Qxb6 Nxd1 h3 Rxa2 Kh2 Nxf2 Re1 Rxe1 Qd8+ Bf8 Nxe1 Bd5 Nf3 Ne4 Qb8 b5 h4 h5 Ne5 Kg7 Kg1 Bc5+ Kf1 Ng3+ Ke1 Bb4+ Kd1 Bb3+ Kc1 Ne2+ Kb1 Nc3+ Kc1 Rc2#',
      key: { ply: 33, text: '17…Be6!! Fischer leaves his queen to be taken. In return his bishops and knight win a rook, two bishops and a pawn, and he mates on move 41.' },
    },
  },
  {
    id: 'morphy', name: 'Paul Morphy', initials: 'PM', era: 'USA · 1837–1884',
    fact: 'An American prodigy who beat Europe’s best players in 1858.',
    game: {
      title: 'Morphy vs Duke of Brunswick and Count Isouard', event: 'Paris Opera, 1858', side: 'white', result: '1-0',
      blurb: '“The Opera Game”: fast development, open lines and a finishing queen sacrifice.',
      moves: 'e4 e5 Nf3 d6 d4 Bg4 dxe5 Bxf3 Qxf3 dxe5 Bc4 Nf6 Qb3 Qe7 Nc3 c6 Bg5 b5 Nxb5 cxb5 Bxb5+ Nbd7 O-O-O Rd8 Rxd7 Rxd7 Rd1 Qe6 Bxd7+ Nxd7 Qb8+ Nxb8 Rd8#',
      key: { ply: 30, text: '16.Qb8+!! The queen is offered on b8. After Nxb8 the rook goes to d8 and it is mate.' },
    },
  },
  {
    id: 'anderssen', name: 'Adolf Anderssen', initials: 'AA', era: 'Germany · 1818–1879',
    fact: 'Won London 1851, the first international chess tournament.',
    game: {
      title: 'Anderssen vs Kieseritzky', event: 'London, 1851', side: 'white', result: '1-0',
      blurb: '“The Immortal Game”. White gives up a bishop, both rooks and the queen, then mates with minor pieces.',
      moves: 'e4 e5 f4 exf4 Bc4 Qh4+ Kf1 b5 Bxb5 Nf6 Nf3 Qh6 d3 Nh5 Nh4 Qg5 Nf5 c6 g4 Nf6 Rg1 cxb5 h4 Qg6 h5 Qg5 Qf3 Ng8 Bxf4 Qf6 Nc3 Bc5 Nd5 Qxb2 Bd6 Bxg1 e5 Qxa1+ Ke2 Na6 Nxg7+ Kd8 Qf6+ Nxf6 Be7#',
      key: { ply: 42, text: '22.Qf6+!! The last heavy piece is given away. After Nxf6, Be7 is mate with two bishops and a knight.' },
    },
  },
  {
    id: 'reti', name: 'Richard Réti', initials: 'RR', era: 'Czechoslovakia · 1889–1929',
    fact: 'A pioneer of hypermodern chess. The Réti Opening (1.Nf3) is named after him.',
    game: {
      title: 'Réti vs Tartakower', event: 'Vienna, 1910', side: 'white', result: '1-0',
      blurb: 'An 11-move miniature in the Caro-Kann that ends in a queen sacrifice and a double check.',
      moves: 'e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nf6 Qd3 e5 dxe5 Qa5+ Bd2 Qxe5 O-O-O Nxe4 Qd8+ Kxd8 Bg5+ Kc7 Bd8#',
      key: { ply: 16, text: '9.Qd8+!! Kxd8 is forced, then 10.Bg5+ is a double check from bishop and rook, and 11.Bd8 is mate.' },
    },
  },
  {
    id: 'lasker', name: 'Edward Lasker', initials: 'EL', era: 'Germany / USA · 1885–1981',
    fact: 'Engineer and chess author, best remembered for this king hunt.',
    game: {
      title: 'Ed. Lasker vs Thomas', event: 'London, 1912', side: 'white', result: '1-0',
      blurb: 'A queen sacrifice drives the black king from g8 all the way to g1.',
      moves: 'd4 e6 Nf3 f5 Nc3 Nf6 Bg5 Be7 Bxf6 Bxf6 e4 fxe4 Nxe4 b6 Ne5 O-O Bd3 Bb7 Qh5 Qe7 Qxh7+ Kxh7 Nxf6+ Kh6 Neg4+ Kg5 h4+ Kf4 g3+ Kf3 Be2+ Kg2 Rh2+ Kg1 Kd2#',
      key: { ply: 20, text: '11.Qxh7+!! After Kxh7 every white move is a check, and the king is hunted down to g1, where Kd2 is mate.' },
    },
  },
  {
    id: 'nakamura', name: 'Hikaru Nakamura', initials: 'HN', era: 'USA · born 1987',
    fact: 'Five-time US Champion and one of the most active players online.',
    live: { username: 'Hikaru' },
  },
  {
    id: 'gukesh', name: 'Gukesh D', initials: 'GD', era: 'India · born 2006',
    fact: 'Became the youngest undisputed World Champion in 2024, aged 18.',
    live: { username: 'GukeshDommaraju' },
  },
  {
    id: 'caruana', name: 'Fabiano Caruana', initials: 'FC', era: 'USA · born 1992',
    fact: 'World Championship challenger in 2018, with a peak rating of 2844.',
    live: { username: 'FabianoCaruana' },
  },
]

export default function Greats({ onTiming }) {
  const [selected, setSelected] = useState('carlsen')
  const [live, setLive] = useState({}) // id -> { loading, error, pair, wins }
  const [liveGame, setLiveGame] = useState(null) // a chosen online win to replay
  const [exploring, setExploring] = useState(null) // { id, colour }
  const [explorePath, setExplorePath] = useState([])
  const jump = useRef(null)
  const g = GREATS.find((x) => x.id === selected)
  const L = live[selected]

  function pick(id) {
    setSelected(id)
    setLiveGame(null)
    setExploring(null)
    setExplorePath([])
  }

  async function loadLive(great) {
    setLive((m) => ({ ...m, [great.id]: { loading: true } }))
    try {
      const pair = await api.createPair({ source: 'chesscom', username: great.live.username, maxGames: 300 })
      const [w, b] = await Promise.all([
        pair.white.summary.games ? api.highlights(pair.white.id, 4, 2400) : null,
        pair.black.summary.games ? api.highlights(pair.black.id, 4, 2400) : null,
      ])
      if (w) onTiming?.('Best wins (White)', w)
      const wins = [
        ...(w?.result.games ?? []).map((x) => ({ ...x, side: 'white' })),
        ...(b?.result.games ?? []).map((x) => ({ ...x, side: 'black' })),
      ]
      setLive((m) => ({ ...m, [great.id]: { pair, wins } }))
      if (wins.length) setLiveGame(wins[0])
    } catch (e) {
      setLive((m) => ({ ...m, [great.id]: { error: e.message } }))
    }
  }

  // what the board shows: the famous game, or a chosen online win
  const shown = liveGame
    ? { moves: liveGame.sanMoves, side: liveGame.side, title: `${liveGame.white} vs ${liveGame.black}`,
        event: [liveGame.event, liveGame.date].filter(Boolean).join(', '), key: null }
    : g.game
      ? { moves: g.game.moves.split(' '), side: g.game.side, title: g.game.title, event: g.game.event, key: g.game.key }
      : null

  return (
    <div>
      <PageHeader kicker="The greats" title="Learn from the" accent="legends.">
        Ten of the strongest players in history. Replay their most famous games, jump to the move that decided them, or
        load a current star’s recent online wins and openings.
      </PageHeader>

      <div className="greats-grid">
        {GREATS.map((x) => (
          <button key={x.id} className={`great-card ${selected === x.id ? 'on' : ''}`} onClick={() => pick(x.id)} aria-pressed={selected === x.id}>
            <span className="great-avatar">{x.initials}</span>
            <strong>{x.name}</strong>
            <span className="great-era">{x.era}</span>
            <span className={`great-tag ${x.live ? 'live' : ''}`}>{x.live ? 'Live from Chess.com' : x.game.event.split(', ')[1]}</span>
          </button>
        ))}
      </div>

      <section className="great-detail" key={selected}>
        <div className="great-board">
          {shown ? (
            <LineReplay
              moves={shown.moves}
              orientation={shown.side}
              interval={850}
              autoPlay={false}
              tilt={34}
              jumpRef={jump}
              renderSteps={(idx, go) => (
                <ol className="game-moves" aria-label="Moves">
                  {shown.moves.map((m, i) => (
                    <li key={i}>
                      {i % 2 === 0 && <span className="n">{i / 2 + 1}.</span>}
                      <button className={`${i === idx ? 'cur' : ''} ${shown.key?.ply === i ? 'key' : ''}`} onClick={() => go(i)}>{m}</button>
                    </li>
                  ))}
                </ol>
              )}
            />
          ) : (
            <div className="great-empty">
              <span className="great-avatar big">{g.initials}</span>
              <p>Load {g.name.split(' ')[0]}’s recent games to replay their best online wins here.</p>
            </div>
          )}
        </div>

        <div className="great-info">
          <span className="kicker">{g.era}</span>
          <h2 className="great-name">{g.name}</h2>
          <p className="great-fact">{g.fact}</p>

          {shown && (
            <div className="game-card">
              <strong>{shown.title}</strong>
              <span className="muted small">{shown.event}{g.game && !liveGame ? ` · ${g.game.result}` : ''}</span>
              {g.game && !liveGame && <p>{g.game.blurb}</p>}
            </div>
          )}

          {shown?.key && (
            <div className="key-moment">
              <span className="key-label">Key moment</span>
              <p>{shown.key.text}</p>
              <button className="pill-btn" onClick={() => jump.current?.(shown.key.ply)}>
                Show move {Math.floor(shown.key.ply / 2) + 1}
              </button>
            </div>
          )}

          {g.live && (
            <div className="live-box">
              {!L && (
                <>
                  <p className="muted">Fetches up to 300 of {g.name.split(' ')[0]}’s recent blitz, rapid and classical games from Chess.com (account {g.live.username}).</p>
                  <button className="primary" onClick={() => loadLive(g)}>Load their games</button>
                </>
              )}
              {L?.loading && <p className="muted">Downloading and analysing games… this can take a little while.</p>}
              {L?.error && <p className="error">{L.error} <button className="link" onClick={() => loadLive(g)}>Try again</button></p>}
              {L?.wins && (
                <>
                  <h3>Best recent wins</h3>
                  <p className="muted small">Wins against players rated 2400+ that lasted at least 10 moves: checkmates first, then the strongest opponents.</p>
                  {!L.wins.length && <p className="muted">No qualifying wins in these games.</p>}
                  <div className="win-list">
                    {L.wins.map((w, i) => (
                      <button key={i} className={`win ${liveGame === w ? 'on' : ''}`} onClick={() => { setLiveGame(w); setExploring(null) }}>
                        <span><strong>vs {w.opponent}</strong>{w.opponentElo ? ` (${w.opponentElo})` : ''}</span>
                        <span className="muted small">{w.side === 'white' ? 'as White' : 'as Black'} · {w.moves} moves{w.mate ? ' · checkmate' : ''}</span>
                      </button>
                    ))}
                  </div>
                  <div className="row" style={{ marginTop: 12 }}>
                    <button className="pill-btn" onClick={() => { setExploring({ id: g.id, colour: 'white' }); setExplorePath([]) }}>Their openings as White</button>
                    <button className="pill-btn" onClick={() => { setExploring({ id: g.id, colour: 'black' }); setExplorePath([]) }}>Their openings as Black</button>
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      </section>

      {exploring && L?.pair && (
        <section className="great-explore">
          <Explorer key={exploring.colour} analysis={L.pair[exploring.colour]} path={explorePath} setPath={setExplorePath}
                    onTiming={onTiming} who="them" name={g.name.split(' ')[0]} />
        </section>
      )}
    </div>
  )
}
