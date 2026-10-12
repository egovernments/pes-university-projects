// Choropleth ramp: pale -> deep green (low -> high). Used app-wide for district maps.
export const CHORO_RAMP = ["#edf8e9", "#c7e9c0", "#a1d99b", "#74c476", "#31a354", "#006d2c"];

function rampColor(value, breaks, colors, missing = "#e5e7eb") {
  if (value == null || Number.isNaN(Number(value))) return missing;
  const v = Number(value);
  for (let i = 0; i < breaks.length; i++) if (v < breaks[i]) return colors[i];
  return colors[colors.length - 1];
}

function stops(breaks, colors) {
  const out = [];
  let prev = 0;
  for (let i = 0; i < breaks.length; i++) {
    out.push({ color: colors[i], from: prev, to: breaks[i] });
    prev = breaks[i];
  }
  out.push({ color: colors[colors.length - 1], from: prev, to: null });
  return out;
}

// Distinct categorical palette for per-facility colouring (Voronoi catchments and
// the buildings that fall inside them). Chosen for separation on a light basemap.
export const FACILITY_PALETTE = [
  "#2563eb", "#16a34a", "#db2777", "#f59e0b", "#0d9488", "#7c3aed",
  "#dc2626", "#0891b2", "#65a30d", "#c026d3", "#ea580c", "#4f46e5",
];

// Stable code -> colour map: the first facility seen gets the first swatch, and
// so on, cycling the palette. Catchment cells and their buildings share it.
export function facilityColors(codes) {
  const map = {};
  let i = 0;
  for (const code of codes) {
    if (!(code in map)) {
      map[code] = FACILITY_PALETTE[i % FACILITY_PALETTE.length];
      i += 1;
    }
  }
  return map;
}

// Data-driven scale from a set of values (quantile breaks over the amber->red ramp).
export function makeScale(values) {
  const sorted = values.filter((v) => Number.isFinite(v) && v > 0).sort((a, b) => a - b);
  const n = CHORO_RAMP.length; // 6 colors -> 5 breaks
  const round = (x) => {
    if (x >= 100000) return Math.round(x / 1000) * 1000;
    if (x >= 1000) return Math.round(x / 100) * 100;
    if (x >= 10) return Math.round(x);
    return Math.round(x * 10) / 10;
  };
  const breaks = [];
  if (sorted.length) {
    for (let i = 1; i < n; i++) {
      breaks.push(round(sorted[Math.floor((sorted.length - 1) * (i / n))]));
    }
  }
  return {
    color: (v) => rampColor(v, breaks, CHORO_RAMP),
    legend: () => stops(breaks, CHORO_RAMP),
  };
}
