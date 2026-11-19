// Simple line icons (24px grid, 1.75 stroke) so the controls look consistent.
const base = { width: 18, height: 18, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.75, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }

export const First = () => <svg {...base}><path d="M6 5v14M18 6l-8 6 8 6V6z" /></svg>
export const Prev = () => <svg {...base}><path d="M15 6l-6 6 6 6" /></svg>
export const Next = () => <svg {...base}><path d="M9 6l6 6-6 6" /></svg>
export const Last = () => <svg {...base}><path d="M18 5v14M6 6l8 6-8 6V6z" /></svg>
export const Play = () => <svg {...base}><path d="M8 5l11 7-11 7V5z" /></svg>
export const Pause = () => <svg {...base}><path d="M8 5v14M16 5v14" /></svg>
export const Flip = () => <svg {...base}><path d="M7 4v16M7 4L4 7M7 4l3 3M17 20V4M17 20l-3-3M17 20l3-3" /></svg>
export const Arrows = () => <svg {...base}><path d="M5 19L19 5M19 5h-7M19 5v7" /></svg>
export const SoundOn = () => <svg {...base}><path d="M4 10v4h4l5 4V6L8 10H4zM16 9a4 4 0 010 6M18.5 6.5a7.5 7.5 0 010 11" /></svg>
export const SoundOff = () => <svg {...base}><path d="M4 10v4h4l5 4V6L8 10H4zM17 10l4 4M21 10l-4 4" /></svg>
export const Restart = () => <svg {...base}><path d="M4 12a8 8 0 108-8M4 4v5h5" /></svg>
