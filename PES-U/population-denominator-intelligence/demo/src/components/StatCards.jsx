import { fmtInt, fmtPct } from "../lib/format.js";
import { districtName } from "./ChoroplethMap.jsx";

// Filter-aware KPI row: every number reflects the current age/sex denominator.
// `nameOf` resolves a feature's display name (defaults to the Chad schema).
export default function StatCards({
  features, value, label, nationalTotal, totalPop, scope = "national", nameOf = districtName,
}) {
  let top = null;
  let covered = 0;
  for (const f of features) {
    const v = value(f.properties);
    if (v > 0) covered++;
    if (!top || v > top.v) top = { v, name: nameOf(f.properties) };
  }

  const cards = [
    { k: label, v: fmtInt(nationalTotal), s: `selected group (${scope})`, bar: "var(--accent)" },
    {
      k: "Share of population",
      v: fmtPct(totalPop ? nationalTotal / totalPop : 0, 1),
      s: `${fmtInt(totalPop)} total`,
      bar: "var(--primary)",
    },
    {
      k: "Highest district",
      v: top ? fmtInt(top.v) : "—",
      s: top ? top.name : "—",
      bar: "var(--secondary)",
    },
    { k: "Districts", v: `${covered} / ${features.length}`, s: "with this group present", bar: "var(--st-exact)" },
  ];

  return (
    <div className="kpis">
      {cards.map((c) => (
        <div className="kpi" key={c.k} style={{ "--accent-bar": c.bar }}>
          <div className="k">{c.k}</div>
          <div className="v num">{c.v}</div>
          <div className="s">{c.s}</div>
        </div>
      ))}
    </div>
  );
}
