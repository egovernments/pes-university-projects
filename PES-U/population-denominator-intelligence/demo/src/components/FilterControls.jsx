import { BANDS, PRESETS } from "../lib/demographics.js";
import { fmtInt, fmtPct } from "../lib/format.js";
import AgeRangeSlider from "./AgeRangeSlider.jsx";

const SEXES = [
  { id: "both", label: "All" },
  { id: "female", label: "Female" },
  { id: "male", label: "Male" },
];

export function rangeLabel(from, to) {
  const lo = BANDS[from].lo;
  const hiBand = BANDS[to];
  if (hiBand.key === "90_plus") return `${lo}+`;
  return `${lo}–${hiBand.hi}`;
}

export function filterLabel(sex, from, to) {
  const s = SEXES.find((x) => x.id === sex)?.label ?? "All";
  return `${s} · age ${rangeLabel(from, to)}`;
}

export default function FilterControls({ sex, from, to, set, nationalTotal, sharePct, scopeLabel = "nationally" }) {
  const activePreset = PRESETS.findIndex((p) => p.sex === sex && p.from === from && p.to === to);

  return (
    <div className="filterbar">
      <div className="fb-group fb-presets">
        <span className="fb-label">Quick denominators</span>
        <div className="filters">
          {PRESETS.map((p, i) => (
            <button
              key={p.label}
              className={`chip ${activePreset === i ? "active" : ""}`}
              onClick={() => set({ sex: p.sex, from: p.from, to: p.to })}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <div className="fb-group">
        <span className="fb-label">Sex</span>
        <div className="seg" role="group" aria-label="Sex">
          {SEXES.map((s) => (
            <button
              key={s.id}
              className={`seg-btn ${sex === s.id ? "active" : ""}`}
              onClick={() => set({ sex: s.id })}
              aria-pressed={sex === s.id}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="fb-group fb-age">
        <div className="fb-agehead">
          <span className="fb-label">Age range</span>
          <span className="fb-agebadge num">{rangeLabel(from, to)}</span>
        </div>
        <AgeRangeSlider from={from} to={to} onChange={set} />
      </div>

      <div className="fb-group fb-total">
        <span className="fb-label">This group, {scopeLabel}</span>
        <span className="fb-total-val num">{fmtInt(nationalTotal)}</span>
        <span className="fb-total-sub num">{fmtPct(sharePct, 1)} of population</span>
      </div>
    </div>
  );
}
