// Client-side mirror of the batch engine's population blend so the average
// household size can be re-tuned live in the browser without re-running the
// engine. Kept byte-for-byte faithful to pdi-batch/features/estimation.py::_ensemble
// so the numbers shown here match what a fresh engine run at the same size would
// produce.

export const ENSEMBLE_W_WP = 0.6;
export const ENSEMBLE_W_BLD = 0.4;

export const DEFAULT_HOUSEHOLD_SIZE = 5.4;
export const HOUSEHOLD_SIZE_MIN = 1;
export const HOUSEHOLD_SIZE_MAX = 12;
export const HOUSEHOLD_SIZE_STEP = 0.1;

const round3 = (x) => Math.round(x * 1000) / 1000;

/**
 * The Feature-1 ensemble for a single boundary.
 * @returns {{population:number, confidence:number, method:string, divergence:number|null}}
 */
export function ensemble(total, count, householdSize) {
  const buildingEstimate = count * householdSize;
  if (total <= 0) {
    return { population: buildingEstimate, confidence: 0.4, method: "buildings_only", divergence: null };
  }
  const divergence = Math.abs(total - buildingEstimate) / total;
  if (count && divergence < 0.3) {
    return {
      population: 0.6 * total + 0.4 * buildingEstimate,
      confidence: round3(0.85 + 0.15 * (1 - divergence)),
      method: "ensemble",
      divergence: round3(divergence),
    };
  }
  return {
    population: total,
    confidence: count ? round3(0.5 + 0.2 * Math.min(count / 10, 1)) : 0.5,
    method: "worldpop_primary",
    divergence: round3(divergence),
  };
}

// Re-derive only the fields that depend on household size, leaving every
// WorldPop-derived column (age bands, totals, targets) untouched. Boundaries
// with no building_count property were computed without a building cross-check,
// so there is nothing to re-blend — those pass through unchanged.
function recomputeProps(props, householdSize) {
  if (props.building_count == null && props.population_estimate == null) return props;
  const total = Number(props.total) || 0;
  const count = Number(props.building_count) || 0;
  const { population, confidence, method, divergence } = ensemble(total, count, householdSize);
  const estimate = Math.round(population);
  const area = Number(props.area_km2) || 0;
  return {
    ...props,
    population_estimate: estimate,
    confidence,
    method,
    divergence,
    density_ppl_km2: area ? Math.round((estimate / area) * 10) / 10 : props.density_ppl_km2,
  };
}

/**
 * Return a GeoJSON FeatureCollection with every boundary's population estimate
 * re-blended at `householdSize`. Memo-friendly: returns the input untouched when
 * there is nothing to recompute.
 */
export function recomputeFeatureCollection(geojson, householdSize) {
  if (!geojson?.features?.length) return geojson;
  return {
    ...geojson,
    features: geojson.features.map((f) => {
      const next = recomputeProps(f.properties, householdSize);
      return next === f.properties ? f : { ...f, properties: next };
    }),
  };
}
