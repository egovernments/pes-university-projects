import { useEffect, useMemo, useState } from "react";
import CatchmentsMap from "./CatchmentsMap.jsx";
import { fmtInt, fmtPct } from "../lib/format.js";
import { gapMeta } from "../lib/gap.js";
import { fetchGeojson } from "./api.js";
import { recomputeFeatureCollection } from "../lib/estimate.js";
import { IconLayers } from "../components/icons.jsx";

const nameOf = (p) => p.name || p.boundaryCode;

// Catchment (Voronoi) tab. The whole-country base stays behind; on top sit the
// per-health-centre catchment cells and the building footprints, each coloured by
// its nearest centre. Cells + buildings are fetched from the job's artifacts.
export default function CatchmentsView({ base, catchmentsUrl, buildingsUrl, householdSize, downloadUrl }) {
  const [raw, setRaw] = useState(null);
  const [buildings, setBuildings] = useState(null);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    let alive = true;
    setRaw(null);
    setBuildings(null);
    setError(null);
    fetchGeojson(catchmentsUrl)
      .then((data) => alive && setRaw(data))
      .catch((e) => alive && setError(e.message));
    if (buildingsUrl) {
      fetchGeojson(buildingsUrl)
        .then((data) => alive && setBuildings(data))
        .catch(() => alive && setBuildings(null)); // buildings are optional
    }
    return () => { alive = false; };
  }, [catchmentsUrl, buildingsUrl]);

  // Re-blend the catchment cells' estimates at the current household size.
  const catchments = useMemo(
    () => (raw ? recomputeFeatureCollection(raw, householdSize) : null),
    [raw, householdSize]
  );

  // Keep the open cell's panel in sync as the household size is re-tuned.
  const selectedLive = useMemo(() => {
    if (!selected || !catchments) return selected;
    const match = catchments.features.find(
      (f) => f.properties.boundaryCode === selected.boundaryCode
    );
    return match ? match.properties : selected;
  }, [selected, catchments]);

  const kpis = useMemo(() => {
    if (!catchments) return null;
    let households = 0;
    let people = 0;
    let top = null;
    for (const f of catchments.features) {
      const p = f.properties;
      households += Number(p.household_target) || 0;
      people += Number(p.population_estimate) || 0;
      if (!top || Number(p.population_estimate) > Number(top.population_estimate)) top = p;
    }
    return [
      { k: "Health centres", v: fmtInt(catchments.features.length), s: "Voronoi catchment cells", bar: "var(--primary)" },
      { k: "Households in catchments", v: fmtInt(households), s: "assigned to a nearest centre", bar: "var(--secondary)" },
      { k: "People served", v: fmtInt(people), s: "estimated across the cells", bar: "#7c3aed" },
      {
        k: "Buildings mapped",
        v: buildings ? fmtInt(buildings.features.length) : "—",
        s: buildings ? "coloured by nearest centre" : "building layer off",
        bar: "#0891b2",
      },
    ];
  }, [catchments, buildings]);

  if (error) return <div className="cov-state">Could not load catchments: {error}</div>;
  if (!catchments) return <div className="cov-state"><span className="loader-ring" /> Building catchments…</div>;
  if (catchments.features.length === 0) {
    return (
      <div className="cov-state">
        No catchment cells — the uploaded sheet had no service points with coordinates.
      </div>
    );
  }

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
        <CatchmentsMap
          base={base}
          catchments={catchments}
          buildings={buildings}
          onSelect={setSelected}
          selectedCode={selected?.boundaryCode}
          downloadUrl={downloadUrl}
        />
        <CatchmentPanel props={selectedLive} />
      </div>
    </div>
  );
}

function Metric({ k, v, color }) {
  return (
    <div className="metric">
      <div className="k">{k}</div>
      <div className="v num" style={color ? { color } : undefined}>{v}</div>
    </div>
  );
}

function CatchmentPanel({ props }) {
  if (!props) {
    return (
      <div className="panel">
        <div className="empty">
          <IconLayers />
          <div>Click a catchment cell to see the health centre's target households,
            estimated population and coverage.</div>
        </div>
      </div>
    );
  }

  const coverage = Number(props.coverage_ratio);
  const hasCoverage = props.gap_classification != null;
  const meta = hasCoverage ? gapMeta(props.gap_classification) : null;

  return (
    <div className="panel">
      <h2>{nameOf(props)}</h2>
      <div className="sub mono">Service Boundary Code</div>
      <span className="badge">catchment cell</span>

      <div className="section-title">Campaign targets</div>
      <div className="metric-grid">
        <Metric k="Target households" v={fmtInt(props.household_target)} />
        <Metric k="Estimated people" v={fmtInt(props.population_estimate)} />
        <Metric k="Under-5 target" v={fmtInt(props.under5_target)} />
        <Metric k="Buildings" v={fmtInt(props.building_count)} />
      </div>

      {hasCoverage && (
        <>
          <div className="section-title">Coverage</div>
          <div className="metric-grid">
            <Metric k="Status" v={meta.label} color={meta.color} />
            <Metric k="Coverage" v={props.registered_population > 0 ? fmtPct(coverage, 1) : "0%"} />
            <Metric k="Registered" v={fmtInt(props.registered_population)} />
            <Metric k="Reg. households" v={fmtInt(props.registered_households)} />
          </div>
        </>
      )}
    </div>
  );
}
