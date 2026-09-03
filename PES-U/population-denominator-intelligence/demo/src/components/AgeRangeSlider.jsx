import { BANDS } from "../lib/demographics.js";

// Ticks to label along the age axis (band indices → short labels).
const TICKS = [
  [0, "0"],
  [4, "15"],
  [8, "35"],
  [12, "55"],
  [16, "75"],
  [19, "90+"],
];

// Dual-handle slider over the real WorldPop 5-year band edges (custom age ranges).
export default function AgeRangeSlider({ from, to, onChange }) {
  const N = BANDS.length - 1; // 19
  const pct = (i) => (i / N) * 100;

  return (
    <div className="agerange">
      <div className="ar-slider">
        <div className="ar-track" />
        <div className="ar-fill" style={{ left: `${pct(from)}%`, right: `${100 - pct(to)}%` }} />
        <input
          type="range" className="ar-input" min={0} max={N} step={1} value={from}
          onChange={(e) => onChange({ from: Math.min(+e.target.value, to) })}
          aria-label="Minimum age"
        />
        <input
          type="range" className="ar-input" min={0} max={N} step={1} value={to}
          onChange={(e) => onChange({ to: Math.max(+e.target.value, from) })}
          aria-label="Maximum age"
        />
      </div>
      <div className="ar-ticks">
        {TICKS.map(([i, label]) => (
          <span key={i} className="ar-tick" style={{ left: `${pct(i)}%` }}>{label}</span>
        ))}
      </div>
    </div>
  );
}
