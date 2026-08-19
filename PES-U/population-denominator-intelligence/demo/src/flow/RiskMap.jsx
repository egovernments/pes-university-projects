import { useMemo, useRef } from "react";
import { MapContainer, TileLayer, GeoJSON } from "react-leaflet";
import { geoBounds } from "./ResultsMap.jsx";
import { PRIORITY_ORDER, riskMeta } from "../lib/risk.js";

const nameOf = (p) => p.name || p.boundaryCode;
const NO_DATA = "#e2e8f0";

// Clickable priority legend — picking a band dims every other district.
function RiskLegend({ counts, active, setActive }) {
  return (
    <div className="overlay-card legend gap-legend">
      <h4>Microplan risk</h4>
      {PRIORITY_ORDER.map((p) => {
        const m = riskMeta(p);
        const isActive = active === p;
        const dim = active && !isActive;
        return (
          <button
            key={p}
            className={`gl-row ${dim ? "dim" : ""}`}
            onClick={() => setActive(isActive ? null : p)}
            title={m.desc}
          >
            <span className="sw" style={{ background: m.color }} />
            <span className="gl-label">{m.label}</span>
            <span className="gl-count num">{counts[p] ?? 0}</span>
          </button>
        );
      })}
      <div className="gl-row legend-foot" style={{ cursor: "default" }}>
        <span className="gl-label" style={{ fontSize: 11 }}>Weighted 0–100 priority score</span>
      </div>
    </div>
  );
}

// Categorical district choropleth coloured by risk_priority, over the live
// units.geojson the engine now enriches with risk_score/risk_priority.
export default function RiskMap({ geojson, counts, active, setActive, onSelect, selectedCode }) {
  const geoRef = useRef(null);
  const bounds = useMemo(() => geoBounds(geojson), [geojson]);

  const style = (feature) => {
    const p = feature.properties;
    const m = p.risk_priority ? riskMeta(p.risk_priority) : null;
    const selected = p.boundaryCode === selectedCode;
    const dim = active && p.risk_priority !== active;
    return {
      fillColor: m ? m.color : NO_DATA,
      fillOpacity: dim ? 0.12 : selected ? 0.9 : 0.82,
      color: selected ? "#0f172a" : "#ffffff",
      weight: selected ? 3 : 0.8,
      opacity: dim ? 0.35 : 1,
    };
  };

  const onEachFeature = (feature, layer) => {
    const p = feature.properties;
    const m = riskMeta(p.risk_priority);
    layer.bindTooltip(
      `<b>${nameOf(p)}</b><br/>${m.label} · score ${p.risk_score ?? "—"}`,
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
        <TileLayer
          attribution="&copy; OpenStreetMap &copy; CARTO"
          url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
        />
        <GeoJSON
          key={active || "all"}
          ref={geoRef}
          data={geojson}
          style={style}
          onEachFeature={onEachFeature}
        />
      </MapContainer>
      <RiskLegend counts={counts} active={active} setActive={setActive} />
    </div>
  );
}
