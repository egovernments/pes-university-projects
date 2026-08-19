// Feature 4 invisible-settlement sizing. A "settlement" is a DBSCAN building cluster
// (eps 100 m, min 3 buildings) with no registered household within 200 m — a place the
// microplan can't count because it never saw it. Buckets by building count so the map
// reads at a glance; violet ramp is deliberately distinct from the gap tab's traffic-light.
export const SIZE_BUCKETS = [
  { key: "sm", label: "3 – 9", min: 3, max: 9, color: "#c4b5fd" },
  { key: "md", label: "10 – 49", min: 10, max: 49, color: "#a78bfa" },
  { key: "lg", label: "50 – 199", min: 50, max: 199, color: "#7c3aed" },
  { key: "xl", label: "200+", min: 200, max: Infinity, color: "#4c1d95" },
];

export const bucketOf = (buildingCount) =>
  SIZE_BUCKETS.find((b) => buildingCount >= b.min && buildingCount <= b.max) || SIZE_BUCKETS[0];

// Count settlements per size bucket, preserving order.
export function bucketCounts(features) {
  const counts = Object.fromEntries(SIZE_BUCKETS.map((b) => [b.key, 0]));
  for (const f of features) counts[bucketOf(Number(f.properties.building_count)).key] += 1;
  return counts;
}
