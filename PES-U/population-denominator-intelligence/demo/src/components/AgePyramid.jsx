import { fmtInt } from "../lib/format.js";
import { PYRAMID_BANDS as BANDS } from "../lib/demographics.js";

// The WorldPop output carries per-band totals + overall male/female totals, but
// not sex-by-band. We approximate each band's split from the district's overall
// male/female ratio. (Band totals and overall M/F are exact; the split is derived.)
export default function AgePyramid({ props, from, to, sex }) {
  const total = Number(props.total) || 0;
  const female = Number(props.female_all) || 0;
  const male = Number(props.male_all) || 0;
  const fShare = total > 0 ? female / total : 0.5;

  // Reflect the active filter: bands outside [from, to] and the unselected sex
  // are dimmed so the pyramid highlights exactly the group the numbers describe.
  const hasRange = from != null && to != null;
  const maleMuted = sex === "female";
  const femaleMuted = sex === "male";

  const rows = BANDS.map(([key, label], i) => {
    const bandTotal = Number(props[key]) || 0;
    const inRange = !hasRange || (i >= from && i <= to);
    return { label, female: bandTotal * fShare, male: bandTotal * (1 - fShare), inRange };
  });
  const max = Math.max(1, ...rows.map((r) => Math.max(r.female, r.male)));

  return (
    <div>
      <div className="pyramid" role="img" aria-label="Population age and sex pyramid">
        {rows.map((r) => (
          <div className={`pyr-row ${r.inRange ? "" : "out"}`} key={r.label}>
            <div className="pyr-left">
              <div
                className={`pyr-bar ${maleMuted ? "muted" : ""}`}
                style={{ width: `${(r.male / max) * 100}%` }}
                title={`Male ${r.label}: ${fmtInt(r.male)}`}
              />
            </div>
            <div className="pyr-label">{r.label}</div>
            <div className="pyr-right">
              <div
                className={`pyr-bar ${femaleMuted ? "muted" : ""}`}
                style={{ width: `${(r.female / max) * 100}%` }}
                title={`Female ${r.label}: ${fmtInt(r.female)}`}
              />
            </div>
          </div>
        ))}
      </div>
      <div className="pyr-legend">
        <span><span className="sw" style={{ background: "#0d9488" }} />Male {fmtInt(male)}</span>
        <span><span className="sw" style={{ background: "#84cc16" }} />Female {fmtInt(female)}</span>
      </div>
    </div>
  );
}
