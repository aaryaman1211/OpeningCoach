import { useEffect, useState } from 'react'
import { api } from '../api.js'

function size(bytes) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`
}

/** Plain explanation of the algorithms behind each screen, with live numbers. */
export default function UnderTheHood({ timings }) {
  const [ref, setRef] = useState(null)
  useEffect(() => {
    api.reference().then(setRef).catch(() => setRef({ available: false }))
  }, [])

  return (
    <div className="stack" style={{ gap: 28, maxWidth: 900 }}>
      <section>
        <h2 style={{ marginBottom: 8 }}>How it works</h2>
        <p className="muted" style={{ marginBottom: 12 }}>
          All of the analysis runs in Java on the server. The browser only shows the results.
        </p>
        <table className="plain">
          <thead>
            <tr><th>Screen</th><th>Algorithm</th><th>What it does</th></tr>
          </thead>
          <tbody>
            <tr><td>Board</td><td>Trie</td><td>Your games are stored as a tree of moves. Looking up a line takes one step per move, however many games there are.</td></tr>
            <tr><td>Leaving theory</td><td>Bloom filter</td><td>A compact in-memory check of “have strong players ever reached this position?”. A “no” is certain. A “maybe” is confirmed against the full index on disk.</td></tr>
            <tr><td>Weak lines</td><td>Randomized Quickselect</td><td>Finds the 10 worst lines without sorting all of them (expected linear time).</td></tr>
            <tr><td>Study plan</td><td>Greedy set cover</td><td>Choosing the best set of lines is NP-hard, so a greedy method with a proven bound is used and checked against brute force on small cases.</td></tr>
            <tr><td>Opponent</td><td>Tree intersection</td><td>Walks your tree and the opponent’s together, visiting only lines you have both played.</td></tr>
          </tbody>
        </table>
      </section>

      {ref?.available && (
        <section>
          <h3 style={{ marginBottom: 4 }}>Reference games and Bloom filter</h3>
          <p className="muted small">
            {ref.build.gamesUsed} Lichess games from January 2013 where both players were rated {ref.build.minElo}+,
            first {ref.build.maxPly / 2} moves: {ref.positions.toLocaleString()} positions.
          </p>
          <div className="facts">
            <div><span className="muted small">Bloom filter size</span><strong>{size(ref.bloom.memoryBytes)}</strong></div>
            <div><span className="muted small">Same set as a Java HashSet (est.)</span><strong>{size(ref.bloom.hashSetEstimateBytes)}</strong></div>
            <div><span className="muted small">Bits per position · hash functions</span><strong>{ref.bloom.bitsPerKey} · {ref.bloom.hashFunctions}</strong></div>
            <div><span className="muted small">False positives: expected / seen</span><strong>{(ref.bloom.theoreticalFpp * 100).toFixed(2)}% / {(ref.lookups.observedFpp * 100).toFixed(2)}%</strong></div>
            <div><span className="muted small">Lookups / answered from memory</span><strong>{ref.lookups.total.toLocaleString()} / {ref.lookups.rejectedByBloom.toLocaleString()}</strong></div>
            <div><span className="muted small">Disk reads</span><strong>{ref.lookups.diskProbes.toLocaleString()}</strong></div>
          </div>
        </section>
      )}
      {ref && !ref.available && <p className="error">Reference games are not available: {ref.unavailableReason}</p>}

      <section>
        <h3 style={{ marginBottom: 8 }}>Server time for your recent actions</h3>
        {!timings.length ? (
          <p className="muted">Use the other screens and their timings appear here.</p>
        ) : (
          <table className="plain">
            <thead><tr><th>Action</th><th className="num">Time</th><th>Method</th></tr></thead>
            <tbody>
              {timings.map((t, i) => (
                <tr key={i}>
                  <td>{t.label}</td>
                  <td className="num">{t.millis} ms</td>
                  <td className="muted">{t.algorithm?.name}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
