// Draws a position from its FEN string. Pure display: no chess logic here.
const GLYPH = { k: '♚', q: '♛', r: '♜', b: '♝', n: '♞', p: '♟' }

export default function Board({ fen, flipped = false, lastMoveSquares = [] }) {
  const rows = fen.split(' ')[0].split('/')
  const grid = rows.map((row) => {
    const cells = []
    for (const ch of row) {
      if (/\d/.test(ch)) for (let i = 0; i < Number(ch); i++) cells.push(null)
      else cells.push(ch)
    }
    return cells
  })
  const ranks = flipped ? [0, 1, 2, 3, 4, 5, 6, 7] : [7, 6, 5, 4, 3, 2, 1, 0]
  const files = flipped ? [7, 6, 5, 4, 3, 2, 1, 0] : [0, 1, 2, 3, 4, 5, 6, 7]
  const squares = []
  for (const r of ranks) {
    for (const f of files) {
      const piece = grid[7 - r][f]
      const name = 'abcdefgh'[f] + (r + 1)
      const dark = (r + f) % 2 === 0
      squares.push(
        <div key={name} className={`sq ${dark ? 'dark' : 'light'} ${lastMoveSquares.includes(name) ? 'hl' : ''}`}>
          {piece && (
            <span className={`pc ${piece === piece.toUpperCase() ? 'white' : 'black'}`}>
              {GLYPH[piece.toLowerCase()]}{'︎'}
            </span>
          )}
          {f === files[0] && <span className="coord rank">{r + 1}</span>}
          {r === ranks[7] && <span className="coord file">{'abcdefgh'[f]}</span>}
        </div>,
      )
    }
  }
  return (
    <div className="board" role="img" aria-label={`Chess position ${fen}`}>
      {squares}
    </div>
  )
}
