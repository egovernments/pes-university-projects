// Age bands present in the WorldPop-derived population properties, in order.
// `key` is the both-sex column suffix; `lo`/`hi` are the age range the band covers.
export const BANDS = [
  { key: "00", label: "0", lo: 0, hi: 0 },
  { key: "01_04", label: "1–4", lo: 1, hi: 4 },
  { key: "05_09", label: "5–9", lo: 5, hi: 9 },
  { key: "10_14", label: "10–14", lo: 10, hi: 14 },
  { key: "15_19", label: "15–19", lo: 15, hi: 19 },
  { key: "20_24", label: "20–24", lo: 20, hi: 24 },
  { key: "25_29", label: "25–29", lo: 25, hi: 29 },
  { key: "30_34", label: "30–34", lo: 30, hi: 34 },
  { key: "35_39", label: "35–39", lo: 35, hi: 39 },
  { key: "40_44", label: "40–44", lo: 40, hi: 44 },
  { key: "45_49", label: "45–49", lo: 45, hi: 49 },
  { key: "50_54", label: "50–54", lo: 50, hi: 54 },
  { key: "55_59", label: "55–59", lo: 55, hi: 59 },
  { key: "60_64", label: "60–64", lo: 60, hi: 64 },
  { key: "65_69", label: "65–69", lo: 65, hi: 69 },
  { key: "70_74", label: "70–74", lo: 70, hi: 74 },
  { key: "75_79", label: "75–79", lo: 75, hi: 79 },
  { key: "80_84", label: "80–84", lo: 80, hi: 84 },
  { key: "85_89", label: "85–89", lo: 85, hi: 89 },
  { key: "90_plus", label: "90+", lo: 90, hi: 120 },
];

// For the AgePyramid, which expects age_XX property keys.
export const PYRAMID_BANDS = BANDS.map((b) => [`age_${b.key}`, b.label]);

// Column name for a band + sex. sex: "both" | "female" | "male".
export function bandColumn(bandKey, sex) {
  if (sex === "female") return `female_age_${bandKey}`;
  if (sex === "male") return `male_age_${bandKey}`;
  return `age_${bandKey}`;
}

// Sum a district's population for a sex + inclusive band index range.
export function subPopulation(props, sex, fromIdx, toIdx) {
  let sum = 0;
  for (let i = fromIdx; i <= toIdx; i++) {
    sum += Number(props[bandColumn(BANDS[i].key, sex)]) || 0;
  }
  return sum;
}

// Campaign denominators worth surfacing.
export const DENOMINATORS = [
  ["under5", "Under 5"],
  ["under15", "Under 15"],
  ["school_age_5_14", "School age 5–14"],
  ["women_15_49", "Women 15–49"],
  ["working_age_15_64", "Working age 15–64"],
  ["elderly_65_plus", "Elderly 65+"],
];

// Quick-filter presets for the Explorer: {label, sex, from, to} band indices.
export const PRESETS = [
  { label: "Total", sex: "both", from: 0, to: 19 },
  { label: "Under 5", sex: "both", from: 0, to: 1 },
  { label: "Under 15", sex: "both", from: 0, to: 3 },
  { label: "Women 15–49", sex: "female", from: 4, to: 10 },
  { label: "Men 15–49", sex: "male", from: 4, to: 10 },
  { label: "Working age 15–64", sex: "both", from: 4, to: 13 },
  { label: "Elderly 65+", sex: "both", from: 14, to: 19 },
];

const NUM_KEYS = [
  "total", "female_all", "male_all",
  ...PYRAMID_BANDS.map(([k]) => k),
  ...DENOMINATORS.map(([k]) => k),
];

/** Sum the numeric demographic fields across an array of geojson features. */
export function aggregate(features) {
  const out = Object.fromEntries(NUM_KEYS.map((k) => [k, 0]));
  for (const f of features) {
    const p = f.properties;
    for (const k of NUM_KEYS) out[k] += Number(p[k]) || 0;
  }
  return out;
}
