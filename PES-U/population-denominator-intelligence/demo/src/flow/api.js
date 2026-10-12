const BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

export const absoluteUrl = (path) => `${BASE}${path}`;

// `force` bypasses the stored result and re-runs the engine, overwriting the cache
// entry for these exact parameters. Everything else identifies the result itself.
export async function submitCompute({
  iso3, year, boundaries, enumeration, sheet, householdSize, groups, withBuildings, force,
}) {
  const form = new FormData();
  form.append("iso3", iso3);
  if (year) form.append("year", String(year));
  // `boundaries` supplies the catchment cells, `enumeration` what the field recorded in
  // them. `sheet` is the older single-upload path that derives cells from coordinates.
  if (boundaries) form.append("boundaries", boundaries);
  if (enumeration) form.append("enumeration", enumeration);
  if (sheet) form.append("sheet", sheet);
  if (householdSize) form.append("householdSize", String(householdSize));
  if (groups) form.append("groups", groups);
  form.append("withBuildings", String(withBuildings));
  if (force) form.append("force", "true");

  let response;
  try {
    response = await fetch(`${BASE}/population/v1/targets/_compute`, {
      method: "POST",
      body: form,
    });
  } catch (networkError) {
    throw new Error(`Could not reach the engine at ${BASE}. Is the API running?`);
  }
  if (!response.ok) {
    throw new Error(await problemDetail(response, "The engine rejected the request"));
  }
  return response.json();
}

export async function fetchStatus(statusUrl) {
  const response = await fetch(absoluteUrl(statusUrl));
  if (!response.ok) {
    throw new Error(await problemDetail(response, "Status check failed"));
  }
  return response.json();
}

export async function fetchGeojson(path) {
  const response = await fetch(absoluteUrl(path));
  if (!response.ok) throw new Error("Could not load the boundary geometry.");
  return response.json();
}

async function problemDetail(response, fallback) {
  try {
    const problem = await response.json();
    if (problem.detail) return problem.detail;
  } catch (ignored) {
    // no JSON body
  }
  return `${fallback} (${response.status}).`;
}
