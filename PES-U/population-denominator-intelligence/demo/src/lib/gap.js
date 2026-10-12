// Feature 2 gap-classification metadata. Colours are the traffic-light scheme from
// the Gap Detection Engine diagram (GREEN/YELLOW/RED) plus a dark "never reached" BLACK.
export const GAP_CLASSES = {
  GREEN: { label: "On track", color: "#16a34a", desc: "Registered ≥ 85% of estimated" },
  YELLOW: { label: "Moderate gap", color: "#f59e0b", desc: "50–85% coverage" },
  RED: { label: "Critical gap", color: "#dc2626", desc: "Below 50% coverage" },
  BLACK: { label: "Never reached", color: "#334155", desc: "Built-up, but no registrations" },
};

export const CLASS_ORDER = ["GREEN", "YELLOW", "RED", "BLACK"];

export const gapMeta = (c) => GAP_CLASSES[c] || { label: c || "—", color: "#94a3b8", desc: "" };

// Count features per classification, preserving the canonical order.
export function classCounts(features) {
  const counts = Object.fromEntries(CLASS_ORDER.map((c) => [c, 0]));
  for (const f of features) {
    const c = f.properties.gap_classification;
    if (c in counts) counts[c] += 1;
  }
  return counts;
}
