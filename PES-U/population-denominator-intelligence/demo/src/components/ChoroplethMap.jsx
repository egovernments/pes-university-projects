import { useMemo, useRef, useState } from "react";
import { MapContainer, TileLayer, GeoJSON } from "react-leaflet";
import { makeScale } from "../lib/colors.js";

export const districtName = (p) =>
  p.microplan_district || p.msp_district || p.Boundary_code;

const MSP_ONLY_FILL = "#475569";   // slate-600 — dark but lighter than the outline
const MSP_ONLY_LINE = "#1e293b";   // slate-800

// Base layers for the verification toggle: the usual light choropleth basemap,
// and Esri World Imagery so you can eyeball real rooftops against the estimate.
const BASEMAPS = {
  light: {
    label: "Map",
    url: "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
    attribution: "&copy; OpenStreetMap &copy; CARTO",
  },
  satellite: {
    label: "Satellite",
    url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
    attribution: "Imagery &copy; Esri, Maxar, Earthstar Geographics",
  },
};
// Street / place labels drawn on top of imagery so the satellite view stays legible.
const LABELS_URL =
  "https://{s}.basemaps.cartocdn.com/rastertiles/voyager_only_labels/{z}/{x}/{y}{r}.png";

function BasemapControl({ base, setBase, showFill, setShowFill }) {
  return (
    <div className="overlay-card basemap-control">
      <label>Base layer</label>
      <div className="seg">
        {Object.entries(BASEMAPS).map(([key, b]) => (
          <button
            key={key}
            className={base === key ? "on" : ""}
            onClick={() => setBase(key)}
          >
            {b.label}
          </button>
        ))}
      </div>
      <label className="chk">
        <input
          type="checkbox"
          checked={showFill}
          onChange={(e) => setShowFill(e.target.checked)}
        />
        Fill catchments
      </label>
    </div>
  );
}

function Legend({ scale, title, format, showMspOnly }) {
  return (
    <div className="overlay-card legend">
      <h4>{title}</h4>
      {scale.legend().map((s, i) => (
        <div className="row" key={i}>
          <span className="sw" style={{ background: s.color }} />
          <span className="num">
            {format(s.from)}
            {s.to == null ? "+" : ` – ${format(s.to)}`}
          </span>
        </div>
      ))}
      {showMspOnly && (
        <div className="row legend-msp">
          <span className="sw" style={{ background: MSP_ONLY_FILL, opacity: 0.6 }} />
          <span>MSP district · not in microplan</span>
        </div>
      )}
    </div>
  );
}

/**
 * Interactive district choropleth. `value(props)` produces the number a district
 * is colored by; `rekey` forces a restyle when the metric changes.
 */
export default function ChoroplethMap({
  geojson,
  value,
  format,
  legendTitle,
  onSelect,
  selectedCode,
  rekey,
  outline,
  mspOnly,
  center = [15.4, 18.7],
  zoom = 5,
  basemaps = false,
  children,
}) {
  const geoRef = useRef(null);
  const [base, setBase] = useState("light");
  const [showFill, setShowFill] = useState(true);
  const satellite = base === "satellite";

  const scale = useMemo(
    () => makeScale(geojson.features.map((f) => value(f.properties))),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [geojson, rekey]
  );

  // On imagery, drop the fill so rooftops show through and brighten the outline.
  const fillOpacity = showFill ? (satellite ? 0.45 : 0.85) : 0;
  const baseStyle = (feature) => {
    const selected = selectedCode && feature.properties.Boundary_code === selectedCode;
    return {
      fillColor: scale.color(value(feature.properties)),
      weight: selected ? 3 : satellite ? 1.5 : 1,
      color: selected ? "#0f172a" : satellite ? "#fde047" : "#ffffff",
      fillOpacity,
    };
  };

  const onEach = (feature, layer) => {
    const p = feature.properties;
    layer.bindTooltip(`<b>${districtName(p)}</b><br/>${legendTitle}: ${format(value(p))}`, {
      sticky: true,
    });
    layer.on({
      click: () => onSelect(p),
      mouseover: (e) => e.target.setStyle({ weight: 2.5, color: "#0f172a" }),
      mouseout: (e) => geoRef.current && geoRef.current.resetStyle(e.target),
    });
  };

  return (
    <div className="map-wrap">
      <MapContainer center={center} zoom={zoom} scrollWheelZoom preferCanvas>
        <TileLayer key={base} attribution={BASEMAPS[base].attribution} url={BASEMAPS[base].url} />
        {satellite && <TileLayer url={LABELS_URL} />}
        {mspOnly && mspOnly.features?.length > 0 && (
          <GeoJSON
            data={mspOnly}
            style={{
              fillColor: MSP_ONLY_FILL,
              color: MSP_ONLY_LINE,
              weight: 1,
              fillOpacity: 0.55,
              dashArray: "3 3",
            }}
            onEachFeature={(feature, layer) => {
              const p = feature.properties;
              layer.bindTooltip(
                `<b>${p.msp_district || p.Boundary_code}</b><br/>MSP district · not in microplan`,
                { sticky: true }
              );
              layer.on({
                mouseover: (e) => e.target.setStyle({ fillOpacity: 0.72, weight: 1.5 }),
                mouseout: (e) => e.target.setStyle({ fillOpacity: 0.55, weight: 1 }),
              });
            }}
          />
        )}
        <GeoJSON
          key={`${rekey}-${base}-${showFill}`}
          ref={geoRef}
          data={geojson}
          style={baseStyle}
          onEachFeature={onEach}
        />
        {outline && (
          <GeoJSON
            data={outline}
            interactive={false}
            style={{ color: "#0f172a", weight: 3.5, fill: false, opacity: 0.9 }}
          />
        )}
      </MapContainer>
      {basemaps && (
        <BasemapControl base={base} setBase={setBase} showFill={showFill} setShowFill={setShowFill} />
      )}
      {children}
      <Legend
        scale={scale}
        title={legendTitle}
        format={format}
        showMspOnly={Boolean(mspOnly && mspOnly.features?.length > 0)}
      />
    </div>
  );
}
