import { useMemo, useRef } from "react";
import { MapContainer, TileLayer, GeoJSON } from "react-leaflet";
import { makeScale } from "../lib/colors.js";
import { absoluteUrl } from "./api.js";

export function geoBounds(geojson) {
  let minLat = 90, minLng = 180, maxLat = -90, maxLng = -180;
  const visit = (coords) => {
    if (typeof coords[0] === "number") {
      const [lng, lat] = coords;
      minLat = Math.min(minLat, lat);
      maxLat = Math.max(maxLat, lat);
      minLng = Math.min(minLng, lng);
      maxLng = Math.max(maxLng, lng);
    } else {
      coords.forEach(visit);
    }
  };
  for (const feature of geojson.features) {
    if (feature.geometry) visit(feature.geometry.coordinates);
  }
  return [[minLat, minLng], [maxLat, maxLng]];
}

const nameOf = (p) => p.name || p.boundaryCode;

function Legend({ scale, title, format }) {
  return (
    <div className="overlay-card legend">
      <h4>{title}</h4>
      {scale.legend().map((s, i) => (
        <div className="row" key={i}>
          <span className="sw" style={{ background: s.color }} />
          <span className="num">
            {format(s.from)}{s.to == null ? "+" : ` – ${format(s.to)}`}
          </span>
        </div>
      ))}
    </div>
  );
}

export default function ResultsMap({
  geojson, value, format, legendTitle, onSelect, selectedCode, downloadUrl,
}) {
  const geoRef = useRef(null);
  const bounds = useMemo(() => geoBounds(geojson), [geojson]);
  const scale = useMemo(
    () => makeScale(geojson.features.map((f) => value(f.properties))),
    [geojson, value]);

  const style = (feature) => {
    const selected = feature.properties.boundaryCode === selectedCode;
    return {
      fillColor: scale.color(value(feature.properties)),
      fillOpacity: 0.85,
      color: selected ? "#0f172a" : "#ffffff",
      weight: selected ? 3 : 0.8,
    };
  };

  const onEachFeature = (feature, layer) => {
    const p = feature.properties;
    layer.bindTooltip(`<b>${nameOf(p)}</b><br/>${legendTitle}: ${format(value(p))}`, { sticky: true });
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
          key={legendTitle}
          ref={geoRef}
          data={geojson}
          style={style}
          onEachFeature={onEachFeature}
        />
      </MapContainer>
      <Legend scale={scale} title={legendTitle} format={format} />
      {downloadUrl && <MapDownloadBar downloadUrl={downloadUrl} />}
    </div>
  );
}

// Sits along the bottom edge of the map: the uploaded sheet, copied and filled
// with the computed target and registration columns, ready to download.
export function MapDownloadBar({ downloadUrl }) {
  return (
    <div className="map-downloadbar">
      <span className="mdl-text">
        Uploaded sheet filled with target &amp; registration columns
      </span>
      <a className="cta cta-sm" href={absoluteUrl(downloadUrl)}>Download filled sheet</a>
    </div>
  );
}
