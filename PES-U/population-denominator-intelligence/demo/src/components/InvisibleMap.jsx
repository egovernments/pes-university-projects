import { useMemo, useRef, useState } from "react";
import L from "leaflet";
import { MapContainer, TileLayer, GeoJSON, CircleMarker } from "react-leaflet";
import { BASEMAPS, LABELS_LAYER } from "../lib/basemaps.js";
import { SIZE_BUCKETS, bucketOf } from "../lib/invisible.js";
import { fmtInt } from "../lib/format.js";

// Registered households are drawn in teal (the design-system --secondary) so the story
// reads: violet settlements sit where NO teal dot falls within 200 m.
const REGISTER_COLOR = "#0d9488";

function Controls({ base, setBase, showRegister, setShowRegister }) {
  return (
    <div className="overlay-card basemap-control">
      <label>Base layer</label>
      <div className="seg">
        {Object.entries(BASEMAPS).map(([key, b]) => (
          <button key={key} className={base === key ? "on" : ""} onClick={() => setBase(key)}>
            {b.label}
          </button>
        ))}
      </div>
      <label className="chk">
        <input
          type="checkbox"
          checked={showRegister}
          onChange={(e) => setShowRegister(e.target.checked)}
        />
        Registered households
      </label>
    </div>
  );
}

function Legend({ counts, registerCount, active, onPick }) {
  return (
    <div className="overlay-card legend gap-legend">
      <h4>Buildings per settlement</h4>
      {SIZE_BUCKETS.map((b) => {
        const dim = active && active !== b.key;
        return (
          <button
            key={b.key}
            className={`gl-row ${dim ? "dim" : ""}`}
            onClick={() => onPick(active === b.key ? null : b.key)}
          >
            <span className="sw" style={{ background: b.color }} />
            <span className="gl-label">{b.label}</span>
            <span className="gl-count num">{counts[b.key] ?? 0}</span>
          </button>
        );
      })}
      {registerCount > 0 && (
        <div className="gl-row legend-foot">
          <span className="sw sw-dot" style={{ background: REGISTER_COLOR }} />
          <span className="gl-label">Registered household</span>
          <span className="gl-count num">{fmtInt(registerCount)}</span>
        </div>
      )}
    </div>
  );
}

/**
 * Invisible-settlement map: registered households (teal), DBSCAN convex hulls + centroid
 * markers (violet, sized by building count), and parent-district outlines — on satellite
 * imagery so the missed rooftops are visible. `active` dims all but one size class; the
 * register layer visualises why a cluster is "invisible" (no teal dot within 200 m).
 */
export default function InvisibleMap({
  geojson,
  districts,
  register,
  counts,
  active,
  setActive,
  onSelect,
  selectedId,
  center = [12.11, 15.05],
  zoom = 11,
}) {
  const geoRef = useRef(null);
  const [base, setBase] = useState("light");
  const [showRegister, setShowRegister] = useState(true);
  const satellite = base === "satellite";
  const registerCount = register?.coordinates?.length || 0;

  // MultiPoint -> one small non-interactive canvas dot per household.
  const registerToLayer = useMemo(
    () => (_feature, latlng) =>
      L.circleMarker(latlng, {
        radius: 2,
        weight: 0,
        fillColor: REGISTER_COLOR,
        fillOpacity: satellite ? 0.85 : 0.6,
        interactive: false,
      }),
    [satellite]
  );

  const style = (feature) => {
    const p = feature.properties;
    const b = bucketOf(Number(p.building_count));
    const selected = selectedId && p.cluster_id === selectedId;
    const dim = active && active !== b.key;
    return {
      fillColor: b.color,
      color: selected ? "#fde047" : b.color,
      weight: selected ? 2.5 : 1,
      fillOpacity: dim ? 0.1 : selected ? 0.75 : 0.55,
      opacity: dim ? 0.25 : 1,
    };
  };

  const onEach = (feature, layer) => {
    const p = feature.properties;
    layer.bindTooltip(
      `<b>${fmtInt(p.building_count)} buildings</b><br/>~${fmtInt(p.estimated_population)} people · ` +
        `${p.distance_to_nearest_km} km to nearest register`,
      { sticky: true }
    );
    layer.on({
      click: () => onSelect(p),
      mouseover: (e) => e.target.setStyle({ weight: 2.5, color: "#fde047" }),
      mouseout: (e) => geoRef.current && geoRef.current.resetStyle(e.target),
    });
  };

  return (
    <div className="map-wrap">
      <MapContainer center={center} zoom={zoom} scrollWheelZoom preferCanvas>
        <TileLayer key={base} {...BASEMAPS[base].layer} />
        {satellite && <TileLayer {...LABELS_LAYER} />}
        {showRegister && register && (
          <GeoJSON key={`reg-${base}`} data={register} pointToLayer={registerToLayer} />
        )}
        {districts && (
          <GeoJSON
            data={districts}
            interactive={false}
            style={{
              color: satellite ? "#fde047" : "#0f172a",
              weight: 1.5,
              fill: false,
              opacity: 0.7,
              dashArray: "4 4",
            }}
          />
        )}
        <GeoJSON
          key={`${active || "all"}-${base}`}
          ref={geoRef}
          data={geojson}
          style={style}
          onEachFeature={onEach}
        />
        {geojson.features.map((f) => {
          const p = f.properties;
          const b = bucketOf(Number(p.building_count));
          if (active && active !== b.key) return null;
          const selected = selectedId && p.cluster_id === selectedId;
          return (
            <CircleMarker
              key={p.cluster_id}
              center={[p.centroid_lat, p.centroid_lon]}
              radius={selected ? 6 : 3}
              pathOptions={{
                color: selected ? "#fde047" : "#ffffff",
                weight: selected ? 2 : 1,
                fillColor: b.color,
                fillOpacity: 0.95,
              }}
              eventHandlers={{ click: () => onSelect(p) }}
            />
          );
        })}
      </MapContainer>
      <Controls
        base={base}
        setBase={setBase}
        showRegister={showRegister}
        setShowRegister={setShowRegister}
      />
      <Legend counts={counts} registerCount={registerCount} active={active} onPick={setActive} />
    </div>
  );
}
