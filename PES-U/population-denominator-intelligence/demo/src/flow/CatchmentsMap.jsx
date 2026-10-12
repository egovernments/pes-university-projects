import { useMemo, useRef } from "react";
import { MapContainer, TileLayer, GeoJSON } from "react-leaflet";
import { LIGHT_BASEMAP } from "../lib/basemaps.js";
import L from "leaflet";
import { geoBounds, MapDownloadBar } from "./ResultsMap.jsx";
import { facilityColors } from "../lib/colors.js";
import { fmtInt } from "../lib/format.js";

const nameOf = (p) => p.name || p.boundaryCode;
// Guard: on a dense district the footprints can run to tens of thousands. Canvas
// handles a few thousand smoothly; beyond the cap we evenly sample for the view.
const MAX_BUILDINGS = 12000;

function sampleFeatures(features, cap) {
  if (features.length <= cap) return features;
  const step = features.length / cap;
  const out = [];
  for (let i = 0; i < features.length; i += step) out.push(features[Math.floor(i)]);
  return out;
}

function Legend({ colors, showBuildings, showCenters }) {
  const entries = Object.entries(colors).slice(0, 10);
  const more = Object.keys(colors).length - entries.length;
  return (
    <div className="overlay-card legend catch-legend">
      <h4>Health-centre catchments</h4>
      {entries.map(([code, color]) => (
        <div className="row" key={code}>
          <span className="sw" style={{ background: color }} />
          <span className="catch-legend-code mono">{code}</span>
        </div>
      ))}
      {more > 0 && <div className="catch-legend-more">+{more} more centres</div>}
      {showCenters && (
        <div className="catch-legend-foot">
          <span className="catch-ring" /> health centre (from the uploaded sheet)
        </div>
      )}
      {showBuildings && (
        <div className="catch-legend-foot">
          <span className="catch-dot" /> each dot is a building, coloured by its nearest centre
        </div>
      )}
    </div>
  );
}

// Whole-country base (faint) + Voronoi catchment cells + building footprints,
// every cell and building coloured by its health centre (Service Boundary Code).
export default function CatchmentsMap({
  base, catchments, buildings, onSelect, selectedCode, downloadUrl,
}) {
  const geoRef = useRef(null);

  const colors = useMemo(
    () => facilityColors(catchments.features.map((f) => f.properties.boundaryCode)),
    [catchments]);
  const bounds = useMemo(() => geoBounds(catchments), [catchments]);

  const buildingsFc = useMemo(() => {
    if (!buildings) return null;
    const features = sampleFeatures(
      buildings.features.filter((f) => f.geometry && f.geometry.type === "Point"),
      MAX_BUILDINGS);
    return { type: "FeatureCollection", features };
  }, [buildings]);
  const buildingCount = buildingsFc ? buildingsFc.features.length : 0;

  // Health-centre points, taken from the uploaded sheet's coordinates and carried
  // on each catchment cell, so the facility is marked at its exact location.
  const centersFc = useMemo(() => {
    const features = catchments.features
      .filter((f) => f.properties.center_lat != null && f.properties.center_lon != null)
      .map((f) => ({
        type: "Feature",
        geometry: { type: "Point", coordinates: [f.properties.center_lon, f.properties.center_lat] },
        properties: { boundaryCode: f.properties.boundaryCode, name: f.properties.name },
      }));
    return features.length ? { type: "FeatureCollection", features } : null;
  }, [catchments]);
  const centerCount = centersFc ? centersFc.features.length : 0;

  const buildingPoint = (feature, latlng) =>
    L.circleMarker(latlng, {
      radius: 1.6,
      weight: 0,
      fillOpacity: 0.7,
      fillColor: colors[feature.properties.boundaryCode] || "#64748b",
    });

  const centerMarker = (feature, latlng) =>
    L.circleMarker(latlng, {
      radius: 6,
      weight: 2.5,
      color: "#ffffff",
      opacity: 1,
      fillColor: colors[feature.properties.boundaryCode] || "#0f172a",
      fillOpacity: 1,
    }).bindTooltip(
      `<b>Health centre</b><br/>${nameOf(feature.properties)}`,
      { direction: "top", offset: [0, -4] });

  const cellStyle = (feature) => {
    const p = feature.properties;
    const color = colors[p.boundaryCode] || "#94a3b8";
    const selected = p.boundaryCode === selectedCode;
    return {
      fillColor: color,
      fillOpacity: selected ? 0.42 : 0.22,
      color,
      weight: selected ? 3 : 1.4,
    };
  };

  const onEachCell = (feature, layer) => {
    const p = feature.properties;
    layer.bindTooltip(
      `<b>${nameOf(p)}</b><br/>${fmtInt(p.household_target)} households · ` +
        `${fmtInt(p.population_estimate)} people`,
      { sticky: true });
    layer.on({
      click: () => onSelect(p),
      mouseover: (e) => e.target.setStyle({ weight: 2.5 }),
      mouseout: (e) => geoRef.current && geoRef.current.resetStyle(e.target),
    });
  };

  return (
    <div className="map-wrap">
      <MapContainer bounds={bounds} className="results-map" scrollWheelZoom preferCanvas>
        <TileLayer {...LIGHT_BASEMAP} />
        {base && (
          <GeoJSON
            data={base}
            interactive={false}
            style={{ color: "#94a3b8", weight: 0.6, fillColor: "#cbd5e1", fillOpacity: 0.08 }}
          />
        )}
        {buildingsFc && (
          <GeoJSON key={buildingCount} data={buildingsFc} pointToLayer={buildingPoint} />
        )}
        <GeoJSON
          ref={geoRef}
          data={catchments}
          style={cellStyle}
          onEachFeature={onEachCell}
        />
        {centersFc && (
          <GeoJSON key={`centers-${centerCount}`} data={centersFc} pointToLayer={centerMarker} />
        )}
      </MapContainer>
      <Legend colors={colors} showBuildings={buildingCount > 0} showCenters={centerCount > 0} />
      {downloadUrl && <MapDownloadBar downloadUrl={downloadUrl} />}
    </div>
  );
}
