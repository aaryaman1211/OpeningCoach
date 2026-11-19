import { useEffect, useState } from 'react'
import { api } from '../api.js'

function kb(bytes) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`
}

export default function UnderTheHood({ timings }) {
  const [ref, setRef] = useState(null)
  useEffect(() => {
    api.reference().then(setRef).catch(() => setRef({ available: false }))
  }, [])

  return (
    <div className="hood">
      <div className="card">
        <h3>Algorithms in this app</h3>
        <table className="algo">
          <tbody>
            <tr><td>Opening trie</td><td>Unit 4</td><td>Your games as a tree of moves; lookup is O(moves), not O(games).</td></tr>
            <tr><td>Bloom filter</td><td>Unit 6 · CO5</td><td>Answers “did strong players ever reach this position?” from memory; a “maybe” is always confirmed by binary search on disk.</td></tr>
            <tr><td>Randomized Quickselect</td><td>Unit 6 · CO5</td><td>Picks your k weakest lines in expected O(N) without sorting all N.</td></tr>
            <tr><td>Randomized QuickSort</td><td>Unit 6 · CO5</td><td>Orders the short lists you see (expected O(k log k)).</td></tr>
            <tr><td>Set cover / budgeted coverage</td><td>Unit 5</td><td>Chooses study lines. NP-hard, so greedy with a proven bound, compared against brute force on small cases.</td></tr>
          </tbody>
        </table>
      </div>
      {ref?.available && (
        <div className="card">
          <h3>Bloom filter over {ref.positions.toLocaleString()} reference positions</h3>
          <div className="stat-row">
            <div className="stat"><span className="label">Bits (m)</span><strong>{ref.bloom.bits.toLocaleString()}</strong></div>
            <div className="stat"><span className="label">Hash functions (k)</span><strong>{ref.bloom.hashFunctions}</strong></div>
            <div className="stat"><span className="label">Bits per position</span><strong>{ref.bloom.bitsPerKey}</strong></div>
            <div className="stat"><span className="label">Bits set</span><strong>{Math.round(ref.bloom.fillRatio * 100)}%</strong></div>
          </div>
          <div className="mem">
            <div><span className="label">Bloom filter</span><span className="memtrack"><span className="fill good" style={{ width: `${Math.max(1, (100 * ref.bloom.memoryBytes) / ref.bloom.hashSetEstimateBytes)}%` }} /></span>{kb(ref.bloom.memoryBytes)}</div>
            <div><span className="label">HashSet&lt;Long&gt; (est.)</span><span className="memtrack"><span className="fill neutral" style={{ width: '100%' }} /></span>{kb(ref.bloom.hashSetEstimateBytes)}</div>
          </div>
          <div className="stat-row">
            <div className="stat"><span className="label">Theoretical false positives</span><strong>{(ref.bloom.theoreticalFpp * 100).toFixed(2)}%</strong></div>
            <div className="stat"><span className="label">Observed so far</span><strong>{(ref.lookups.observedFpp * 100).toFixed(2)}%</strong></div>
            <div className="stat"><span className="label">Lookups / rejected instantly</span><strong>{ref.lookups.total.toLocaleString()} / {ref.lookups.rejectedByBloom.toLocaleString()}</strong></div>
            <div className="stat"><span className="label">Disk probes</span><strong>{ref.lookups.diskProbes.toLocaleString()}</strong></div>
          </div>
          <p className="muted small">
            Reference: {ref.build.gamesUsed} Lichess games (January 2013, both players rated {ref.build.minElo}+), first{' '}
            {ref.build.maxPly / 2} moves. Source: database.lichess.org, CC0.
          </p>
        </div>
      )}
      {ref && !ref.available && <p className="error">Reference book unavailable: {ref.unavailableReason}</p>}
      <div className="card">
        <h3>Server timings this session</h3>
        {!timings.length && <p className="muted">Open the other tabs to see timings.</p>}
        <table className="algo">
          <tbody>
            {timings.map((t, i) => (
              <tr key={i}>
                <td>{t.label}</td>
                <td>{t.millis} ms</td>
                <td className="muted small">{t.algorithm?.name}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
