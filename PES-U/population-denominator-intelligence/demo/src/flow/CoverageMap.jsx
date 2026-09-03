import { useMemo, useRef } from "react";
import { MapContainer, TileLayer, GeoJSON } from "react-leaflet";
import { LIGHT_BASEMAP } from "../lib/basemaps.js";
import { geoBounds, MapDownloadBar } from "./ResultsMap.jsx";
import { GAP_CLASSES, CLASS_ORDER, gapMeta } from "../lib/gap.js";
import { fmtInt, fmtPct } from "../lib/format.js";

const nameOf = (p) => p.name || p.boundaryCode;
const NO_DATA = "#e2e8f0";

function ClassLegend({ counts, active, setActive }) {
  return (
    <div className="overlay-card legend legend-cov">
      <h4>Coverage classification</h4>
      {CLASS_ORDER.map((cls) => {
        const meta = GAP_CLASSES[cls];
        const isActive = active === cls;
        const dim = active && !isActive;
        return (
          <button
            key={cls}
            className={`row cov-legend-row ${isActive ? "active" : ""} ${dim ? "dim" : ""}`}
            onClick={() => setActive(isActive ? null : cls)}
            title={meta.desc}
          >
            <span className="sw" style={{ background: meta.color }} />
            <span className="cov-legend-label">{meta.label}</span>
            <span className="num cov-legend-count">{counts[cls] ?? 0}</span>
          </button>
        );
      })}
    </div>
  );
}

export default function CoverageMap({
  geojson, counts, active, setActive, onSelect, selectedCode, downloadUrl,
}) {
  const geoRef = useRef(null);
  const bounds = useMemo(() => geoBounds(geojson), [geojson]);

  const style = (feature) => {
    const p = feature.properties;
    const cls = p.gap_classification;
    const meta = cls ? gapMeta(cls) : null;
    const selected = p.boundaryCode === selectedCode;
    const dim = active && cls !== active;
    return {
      fillColor: meta ? meta.color : NO_DATA,
      fillOpacity: dim ? 0.15 : 0.85,
      color: selected ? "#0f172a" : "#ffffff",
      weight: selected ? 3 : 0.8,
    };
  };

  const onEachFeature = (feature, layer) => {
    const p = feature.properties;
    const cls = p.gap_classification;
    const registered = Number(p.registered_population) || 0;
    const estimated = Number(p.population_estimate) || 0;
    const coverage = estimated > 0 ? registered / estimated : 0;
    layer.bindTooltip(
      `<b>${nameOf(p)}</b><br/>${gapMeta(cls).label}` +
        `<br/>${fmtInt(registered)} / ${fmtInt(estimated)} (${fmtPct(coverage, 0)})`,
      { sticky: true });
    layer.on({
      click: () => onSelect(p),
      mouseover: (e) => e.target.setStyle({ weight: 2.5, color: "#0f172a" }),
      mouseout: (e) => geoRef.current && geoRef.current.resetStyle(e.target),
    });
  };

  return (
    <div className="map-wrap">
      <MapContainer bounds={bounds} className="results-map" scrollWheelZoom preferCanvas>
        <TileLayer {...LIGHT_BASEMAP} />
        <GeoJSON
          key={active || "all"}
          ref={geoRef}
          data={geojson}
          style={style}
          onEachFeature={onEachFeature}
        />
      </MapContainer>
      <ClassLegend counts={counts} active={active} setActive={setActive} />
      {downloadUrl && <MapDownloadBar downloadUrl={downloadUrl} />}
    </div>
  );
}
