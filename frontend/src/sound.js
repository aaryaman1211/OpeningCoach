// Tiny synthesised move sounds (no audio files). Muted state is remembered per browser.
let ctx = null
let muted = true // off unless the user turns it on
try {
  muted = localStorage.getItem('oc-muted') !== '0'
} catch {
  muted = true
}

export function isMuted() {
  return muted
}

export function setMuted(m) {
  muted = m
  try {
    localStorage.setItem('oc-muted', m ? '1' : '0')
  } catch {
    /* storage unavailable: keep in memory only */
  }
}

export function playMove(capture = false) {
  if (muted) return
  try {
    ctx = ctx || new (window.AudioContext || window.webkitAudioContext)()
    const t = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    const filter = ctx.createBiquadFilter()
    filter.type = 'lowpass'
    filter.frequency.value = capture ? 1400 : 900
    osc.type = 'triangle'
    osc.frequency.setValueAtTime(capture ? 220 : 320, t)
    osc.frequency.exponentialRampToValueAtTime(capture ? 90 : 160, t + 0.08)
    gain.gain.setValueAtTime(0.0001, t)
    gain.gain.exponentialRampToValueAtTime(capture ? 0.35 : 0.22, t + 0.005)
    gain.gain.exponentialRampToValueAtTime(0.0001, t + (capture ? 0.16 : 0.1))
    osc.connect(filter).connect(gain).connect(ctx.destination)
    osc.start(t)
    osc.stop(t + 0.2)
  } catch {
    /* audio not available */
  }
}
