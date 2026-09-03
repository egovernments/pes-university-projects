// Tile sources for every map in the demo, in one place.
//
// These were CARTO Positron (basemaps.cartocdn.com) until CARTO started requiring an
// API key for their basemaps: anonymous requests still return HTTP 200, but the tile
// PNG itself is stamped "API KEY REQUIRED - carto.com/basemaps/apikey", which showed
// through as text scrawled across the map background. Esri's public ArcGIS Online
// services are the like-for-like replacement - same pale grey canvas, no key, and the
// satellite layer was already coming from the same host.

const ESRI_ATTRIBUTION = "&copy; Esri, HERE, Garmin, &copy; OpenStreetMap contributors";

// Esri's Light Gray Canvas only publishes tiles to zoom 16. Leaflet upscales the last
// available level past maxNativeZoom rather than going blank, so the map stays usable
// when you zoom in to inspect individual settlements.
export const LIGHT_BASEMAP = {
  url: "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}",
  attribution: ESRI_ATTRIBUTION,
  maxNativeZoom: 16,
  maxZoom: 19,
};

// World Imagery goes deep enough to make out individual rooftops.
export const SATELLITE_BASEMAP = {
  url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
  attribution: "Imagery &copy; Esri, Maxar, Earthstar Geographics",
  maxNativeZoom: 19,
  maxZoom: 19,
};

// Transparent place/boundary labels drawn over imagery so the satellite view stays legible.
export const LABELS_LAYER = {
  url: "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}",
  attribution: "",
  maxNativeZoom: 16,
  maxZoom: 19,
};

// The two-way toggle shared by the choropleth and invisible-settlement maps.
// `layer` is kept separate from `label` so it can be spread straight onto <TileLayer>
// without leaking a stray prop into Leaflet's options.
export const BASEMAPS = {
  light: { label: "Map", layer: LIGHT_BASEMAP },
  satellite: { label: "Satellite", layer: SATELLITE_BASEMAP },
};
