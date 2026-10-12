import { useEffect, useMemo, useState } from "react";
import InvisibleMap from "../components/InvisibleMap.jsx";
import InvisibleDetailPanel from "../components/InvisibleDetailPanel.jsx";
import { bucketCounts } from "../lib/invisible.js";
import { fmtInt } from "../lib/format.js";
import { fetchGeojson } from "./api.js";

// Feature 4 tab for the live API run. The invisible-settlement clusters are
// fetched from the job's _settlements artifact (DBSCAN over VIDA footprints with
// no registered household within 200 m). The registered households ride along in
// the same artifact under `registerHouseholds`, so the map can show the teal dots
// that explain why each cluster is "invisible".
export default function InvisibleView({ settlementsUrl }) {
  const [geojson, setGeojson] = useState(null);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);
  const [active, setActive] = useState(null);

  useEffect(() => {
    let alive = true;
    setGeojson(null);
    setError(null);
    fetchGeojson(settlementsUrl)
      .then((data) => alive && setGeojson(data))
      .catch((e) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, [settlementsUrl]);

  const stats = useMemo(() => {
    if (!geojson) return null;
    let buildings = 0;
    let est = 0;
    let top = null;
    for (const f of geojson.features) {
      const p = f.properties;
      buildings += Number(p.building_count) || 0;
      est += Number(p.estimated_population) || 0;
      if (!top || Number(p.building_count) > Number(top.building_count)) top = p;
    }
    return {
      counts: bucketCounts(geojson.features),
      kpis: [
        { k: "Invisible settlements", v: fmtInt(geojson.features.length), s: "clusters with no register within 200 m", bar: "#7c3aed" },
        { k: "Buildings clustered", v: fmtInt(buildings), s: "VIDA footprints, confidence ≥ 0.70", bar: "var(--primary)" },
        { k: "Estimated people", v: fmtInt(est), s: "at 5.4 per household", bar: "var(--secondary)" },
        {
          k: "Largest settlement",
          v: top ? fmtInt(top.building_count) : "—",
          s: top ? `${fmtInt(top.estimated_population)} people · ${top.parent_boundary_code}` : "—",
          bar: "#4c1d95",
        },
      ],
    };
  }, [geojson]);

  if (error) {
    return <div className="cov-state">Could not load invisible settlements: {error}</div>;
  }
  if (!geojson) {
    return <div className="cov-state"><span className="loader-ring" /> Detecting settlements…</div>;
  }
  if (geojson.features.length === 0) {
    return (
      <div className="cov-state">
        No invisible settlements detected — every building cluster has a registered household within 200 m.
      </div>
    );
  }

  return (
    <div className="results-scroll cov-view">
      <div className="cov-kpis">
        {stats.kpis.map((c) => (
          <div className="cov-kpi" key={c.k} style={{ "--accent-bar": c.bar }}>
            <div className="cov-kpi-k">{c.k}</div>
            <div className="cov-kpi-v num">{c.v}</div>
            <div className="cov-kpi-s">{c.s}</div>
          </div>
        ))}
      </div>
      <div className="split results-split-map">
        <InvisibleMap
          geojson={geojson}
          register={geojson.registerHouseholds || null}
          counts={stats.counts}
          active={active}
          setActive={setActive}
          onSelect={setSelected}
          selectedId={selected?.cluster_id}
        />
        <InvisibleDetailPanel props={selected} />
      </div>
    </div>
  );
}
