import { useMemo, useState } from "react";
import CoverageMap from "./CoverageMap.jsx";
import { classCounts, gapMeta } from "../lib/gap.js";
import { fmtInt, fmtPct } from "../lib/format.js";
import { IconMap } from "../components/icons.jsx";

const nameOf = (p) => p.name || p.boundaryCode;

// What `coverage_ratio` is actually a ratio *of*. The engine classifies on one measure
// (under-5 by default, config.COVERAGE_PRIMARY_MEASURE) while the panel also shows the
// all-ages counts, so the headline percentage has to name its own measure - quoting it
// above a different measure's counts is what made the number unreadable.
const MEASURES = {
  under5: {
    label: "under-5 coverage",
    noun: "children",
    registered: (p) => Number(p.registered_under5) || 0,
    estimated: (p) => Math.round(Number(p.under5) || 0),
  },
  households: {
    label: "household coverage",
    noun: "households",
    registered: (p) => Number(p.registered_households) || 0,
    estimated: (p) => Number(p.estimated_households) || 0,
  },
  population: {
    label: "population coverage",
    noun: "people",
    registered: (p) => Number(p.registered_population) || 0,
    estimated: (p) => Number(p.population_estimate) || 0,
  },
};

const measureOf = (p) => MEASURES[p.coverage_measure] || MEASURES.population;

// Coverage tab for the live API run. Every field is read straight from the
// units.geojson the engine now enriches with the registered denominator:
// registered_population/under5/households, coverage_ratio and gap_classification.
export default function CoverageView({ geojson, downloadUrl }) {
  const [selected, setSelected] = useState(null);
  const [active, setActive] = useState(null);

  const { counts, kpis } = useMemo(() => {
    let estimated = 0;
    let registered = 0;
    let covered = 0;
    let blackPop = 0;
    for (const f of geojson.features) {
      const p = f.properties;
      estimated += Number(p.population_estimate) || 0;
      registered += Number(p.registered_population) || 0;
      if (Number(p.registered_population) > 0) covered += 1;
      if (p.gap_classification === "BLACK") blackPop += Number(p.population_estimate) || 0;
    }
    const c = classCounts(geojson.features);
    const gap = estimated - registered;
    return {
      counts: c,
      kpis: [
        { k: "Estimated population", v: fmtInt(estimated), s: `${geojson.features.length} boundaries`, bar: "var(--primary)" },
        // The sheet holds no headcount of people, so this side is households x average
        // household size. Saying so stops it being read as a measured figure.
        { k: "Registered", v: fmtInt(registered), s: `${covered} covered · derived from households`, bar: "var(--secondary)" },
        { k: "Coverage gap", v: fmtInt(gap), s: `${fmtPct(estimated ? gap / estimated : 0, 1)} unregistered`, bar: gapMeta("RED").color },
        { k: "Never reached", v: `${c.BLACK}`, s: `${fmtInt(blackPop)} people, built-up but unregistered`, bar: gapMeta("BLACK").color },
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
        <CoverageMap
          geojson={geojson}
          counts={counts}
          active={active}
          setActive={setActive}
          onSelect={setSelected}
          selectedCode={selected?.boundaryCode}
          downloadUrl={downloadUrl}
        />
        <CoveragePanel props={selected} />
      </div>
    </div>
  );
}

function CoverageBar({ label, registered, estimated, color }) {
  const pct = estimated > 0 ? Math.min(registered / estimated, 1) : 0;
  return (
    <div className="cov-bar">
      <div className="cov-bar-head">
        <span>{label}</span>
        <span className="num">{fmtInt(registered)} / {fmtInt(estimated)}</span>
      </div>
      <div className="cov-track">
        <span className="cov-fill" style={{ width: `${pct * 100}%`, background: color }} />
      </div>
    </div>
  );
}

function Metric({ k, v }) {
  return (
    <div className="metric">
      <div className="k">{k}</div>
      <div className="v num">{v}</div>
    </div>
  );
}

function CoveragePanel({ props }) {
  if (!props) {
    return (
      <div className="panel">
        <div className="empty">
          <IconMap />
          <div>Select a boundary on the map to see its coverage gap, or click a class in the legend to filter.</div>
        </div>
      </div>
    );
  }

  const meta = gapMeta(props.gap_classification);
  const registered = Number(props.registered_population) || 0;
  const estimated = Number(props.population_estimate) || 0;
  const registeredU5 = Number(props.registered_under5) || 0;
  const estimatedU5 = Math.round(Number(props.under5) || 0);
  const coverage = Number(props.coverage_ratio);
  const neverReached = props.gap_classification === "BLACK";

  // The headline percentage and the sentence under it must describe the same measure.
  const measure = measureOf(props);
  const headlineRegistered = measure.registered(props);
  const headlineEstimated = measure.estimated(props);

  return (
    <div className="panel">
      <h2>{nameOf(props)}</h2>
      <div className="sub mono">{props.boundaryCode}</div>
      {props.is_catchment ? <span className="badge">catchment cell</span> : null}

      <div className="cov-hero" style={{ "--cls": meta.color }}>
        <div className="cov-hero-top">
          <span className="pill" style={{ background: meta.color }}>
            <span className="dot" />{meta.label}
          </span>
          <span className="cov-hero-pct num">
            {headlineRegistered > 0 ? fmtPct(coverage, 1) : "0%"}
          </span>
        </div>
        <div className="cov-hero-sub">
          {neverReached
            ? `No one registered here — an estimated ${fmtInt(headlineEstimated)} ${measure.noun} the campaign never reached.`
            : `${fmtInt(headlineRegistered)} of an estimated ${fmtInt(headlineEstimated)} ${measure.noun} registered.`}
        </div>
        <div className="cov-hero-measure">measured on {measure.label}</div>
      </div>

      <div className="section-title">Coverage</div>
      <CoverageBar label="Under-5 (measured)" registered={registeredU5} estimated={estimatedU5} color={meta.color} />
      <CoverageBar label="All ages (derived)" registered={registered} estimated={estimated} color={meta.color} />

      <div className="section-title">Gap</div>
      <div className="metric-grid">
        <Metric k="Under-5 gap" v={fmtInt(estimatedU5 - registeredU5)} />
        <Metric k="Reg. children" v={fmtInt(registeredU5)} />
        <Metric k="Est. children" v={fmtInt(estimatedU5)} />
        <Metric k="Population gap" v={fmtInt(estimated - registered)} />
        <Metric k="Reg. households" v={fmtInt(props.registered_households)} />
        <Metric k="Buildings" v={fmtInt(props.building_count)} />
      </div>
    </div>
  );
}
