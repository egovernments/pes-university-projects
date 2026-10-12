import { useMemo, useState } from "react";
import RiskMap from "./RiskMap.jsx";
import RiskPanel from "./RiskPanel.jsx";
import { priorityCounts, riskMeta } from "../lib/risk.js";
import { fmtInt } from "../lib/format.js";

const nameOf = (p) => p.name || p.boundaryCode;

// Feature 5 tab for the live API run. Risk rides on the same units.geojson the
// engine now enriches (risk_score/risk_priority/risk_factors), so no extra fetch.
export default function RiskView({ geojson }) {
  const [selected, setSelected] = useState(null);
  const [active, setActive] = useState(null);

  const { counts, kpis } = useMemo(() => {
    const features = geojson.features;
    let sum = 0;
    let top = null;
    for (const f of features) {
      const p = f.properties;
      sum += Number(p.risk_score) || 0;
      if (!top || Number(p.risk_score) > Number(top.risk_score)) top = p;
    }
    const c = priorityCounts(features);
    const mean = features.length ? sum / features.length : 0;
    return {
      counts: c,
      kpis: [
        { k: "Districts scored", v: fmtInt(features.length), s: "explainable 5-factor model", bar: "var(--primary)" },
        { k: "Critical priority", v: `${c.CRITICAL}`, s: `${c.HIGH} high · ${c.MEDIUM} medium`, bar: riskMeta("CRITICAL").color },
        { k: "Mean risk score", v: mean.toFixed(1), s: "weighted 0–100 scale", bar: riskMeta("HIGH").color },
        { k: "Highest risk", v: top ? `${top.risk_score}` : "—", s: top ? nameOf(top) : "—", bar: riskMeta("CRITICAL").color },
      ],
    };
  }, [geojson]);

  return (
    <div className="results-scroll cov-view">
      <div className="cov-kpis">
        {kpis.map((c) => (
          <div className="cov-kpi" key={c.k} style={{ "--accent-bar": c.bar }}>
            <div className="cov-kpi-k">{c.k}</div>
            <div className="cov-kpi-v num">{c.v}</div>
            <div className="cov-kpi-s">{c.s}</div>
          </div>
        ))}
      </div>
      <div className="split results-split-map">
        <RiskMap
          geojson={geojson}
          counts={counts}
          active={active}
          setActive={setActive}
          onSelect={setSelected}
          selectedCode={selected?.boundaryCode}
        />
        <RiskPanel props={selected} />
      </div>
    </div>
  );
}
