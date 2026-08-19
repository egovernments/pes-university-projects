// Feature 5 risk-scoring metadata. The engine writes a 0–100 weighted score plus a
// priority band per district (see pdi-batch/features/risk.py). Bands mirror the
// thresholds in config.py: CRITICAL ≥75, HIGH ≥50, MEDIUM ≥25, LOW <25.
export const RISK_PRIORITIES = {
  CRITICAL: { label: "Critical", color: "#b91c1c", desc: "Score ≥ 75" },
  HIGH: { label: "High", color: "#ea580c", desc: "Score 50–74" },
  MEDIUM: { label: "Medium", color: "#eab308", desc: "Score 25–49" },
  LOW: { label: "Low", color: "#16a34a", desc: "Score < 25" },
};

export const PRIORITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

export const riskMeta = (p) =>
  RISK_PRIORITIES[p] || { label: p || "—", color: "#94a3b8", desc: "" };

// The five weighted factors, in display order, each with a human label, a short
// explanation, and a distinct colour for the score-composition bar. `provisional`
// factors have no data feed yet (held at a neutral 0.5 — see config.RISK_PROVISIONAL_FACTORS).
export const RISK_FACTORS = [
  { key: "population_gap", label: "Population gap", color: "#dc2626", desc: "Share of the estimated population not yet registered" },
  { key: "past_performance", label: "Past performance", color: "#0d9488", desc: "Historical campaign coverage in this district" },
  { key: "facility_distance", label: "Facility access", color: "#2563eb", desc: "Distance to the nearest health facility" },
  { key: "building_density", label: "Building density", color: "#7c3aed", desc: "Built-up intensity — VIDA footprints per km²" },
  { key: "missed_children", label: "Missed children", color: "#f59e0b", desc: "Under-5s missed in earlier rounds" },
];

// Count districts per priority band, preserving the canonical order.
export function priorityCounts(features) {
  const counts = Object.fromEntries(PRIORITY_ORDER.map((p) => [p, 0]));
  for (const f of features) {
    const p = f.properties.risk_priority;
    if (p in counts) counts[p] += 1;
  }
  return counts;
}

// risk_factors arrives as a nested object from the geojson, or a JSON string from
// the CSV path. Normalise to an object; return {} when absent.
export const parseFactors = (raw) => {
  if (raw == null) return {};
  if (typeof raw === "string") {
    try {
      return JSON.parse(raw);
    } catch {
      return {};
    }
  }
  return raw;
};

// True once the gap report has been enriched with the risk score.
export const hasRisk = (geojson) =>
  !!geojson?.features?.some((f) => f.properties.risk_priority != null);
