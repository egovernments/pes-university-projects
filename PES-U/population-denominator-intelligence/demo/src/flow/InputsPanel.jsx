import { useState } from "react";
import { COUNTRIES, countryLabel } from "./countries.js";

const DEFAULT_YEAR = 2026;

const BOUNDARY_EXTENSIONS = [".geojson", ".json"];
const WORKBOOK_EXTENSIONS = [".xlsx", ".xls"];

const hasExtension = (file, allowed) =>
  allowed.some((extension) => file.name.toLowerCase().endsWith(extension));

export default function InputsPanel({ onCompute, error }) {
  const [iso3, setIso3] = useState("TCD");
  const [year, setYear] = useState(DEFAULT_YEAR);
  const [boundaries, setBoundaries] = useState(null);
  const [enumeration, setEnumeration] = useState(null);
  const [fileErrors, setFileErrors] = useState({});
  const [householdSize, setHouseholdSize] = useState("");

  // Rejecting the wrong file type here saves a round trip, but the API validates it
  // again - the browser is not the place to enforce a contract.
  const pickFile = (key, allowed, setter) => (event) => {
    const file = event.target.files?.[0] ?? null;
    if (file && !hasExtension(file, allowed)) {
      setter(null);
      setFileErrors((current) => ({
        ...current,
        [key]: `${file.name} is not a ${allowed.join(" or ")} file.`,
      }));
      return;
    }
    setter(file);
    setFileErrors((current) => ({ ...current, [key]: null }));
  };

  const run = (force) => onCompute({
    iso3,
    year,
    boundaries,
    enumeration,
    householdSize: householdSize ? Number(householdSize) : undefined,
    withBuildings: true,
    force,
  });

  const submit = (event) => {
    event.preventDefault();
    run(false);
  };

  // Enumeration without boundaries has nothing to attach its counts to.
  const orphanEnumeration = Boolean(enumeration && !boundaries);

  return (
    <section className="compute-panel">
      <div className="compute-panel-head">
        <h2>Compute campaign targets</h2>
        <p>
          Pick a country, then add the campaign&rsquo;s two files: the catchment boundaries and
          what the field enumerated inside them. The engine estimates population from WorldPop
          and VIDA building footprints, and compares the enumeration against it.
        </p>
      </div>

      <form className="compute-form" onSubmit={submit}>
        <div className="field-row">
          <label className="field field-grow">
            <span className="field-label">Country</span>
            <select
              className="field-input"
              value={iso3}
              onChange={(event) => setIso3(event.target.value)}
            >
              {COUNTRIES.map((country) => (
                <option key={country.iso3} value={country.iso3}>{countryLabel(country)}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">WorldPop year</span>
            <input
              type="number"
              step="1"
              min="2000"
              max="2030"
              className="field-input year-input"
              value={year}
              onChange={(event) => setYear(Number(event.target.value))}
            />
          </label>
        </div>

        <label className="field">
          <span className="field-label">Catchment boundaries <em>optional</em></span>
          <input
            type="file"
            accept=".geojson,.json"
            className="field-file"
            onChange={pickFile("boundaries", BOUNDARY_EXTENSIONS, setBoundaries)}
          />
          <span className="field-hint">
            {boundaries
              ? `Selected: ${boundaries.name} — its polygons become the analysis units.`
              : "GeoJSON of catchment polygons plus a facility point each. Without it the whole country is computed by district."}
          </span>
          {fileErrors.boundaries && <span className="field-error">{fileErrors.boundaries}</span>}
        </label>

        <label className="field">
          <span className="field-label">Enumeration workbook <em>optional</em></span>
          <input
            type="file"
            accept=".xlsx,.xls"
            className="field-file"
            onChange={pickFile("enumeration", WORKBOOK_EXTENSIONS, setEnumeration)}
          />
          <span className="field-hint">
            {enumeration
              ? `Selected: ${enumeration.name} — households and children enumerated per facility.`
              : "Per-facility field counts. Adding it turns on the coverage and risk layers; without it the engine only estimates."}
          </span>
          {fileErrors.enumeration && <span className="field-error">{fileErrors.enumeration}</span>}
          {orphanEnumeration && (
            <span className="field-error">
              An enumeration workbook needs catchment boundaries to attach its counts to. Add the
              boundary geojson, or the enumeration will be ignored.
            </span>
          )}
        </label>

        <label className="field">
          <span className="field-label">Avg household size <em>optional</em></span>
          <input
            type="number"
            step="0.1"
            min="1"
            className="field-input"
            value={householdSize}
            onChange={(event) => setHouseholdSize(event.target.value)}
            placeholder="country default"
          />
          <span className="field-hint">
            You can re-tune this on the results screen and every estimate updates instantly — no
            re-run needed.
          </span>
        </label>

        {error && <div className="input-error">{error}</div>}

        <div className="compute-actions">
          <button className="cta" type="submit" disabled={!iso3}>Compute targets</button>
          <button
            className="cta cta-secondary"
            type="button"
            disabled={!iso3}
            onClick={() => run(true)}
          >
            Recompute
          </button>
        </div>

        <p className="compute-note">
          The first run for a country downloads WorldPop rasters (~1&ndash;2&nbsp;GB) and can take a
          few minutes; later runs are served from the stored result.{" "}
          <strong>Recompute</strong> ignores the stored result, re-runs the engine for these exact
          settings and overwrites what is saved &mdash; use it when the underlying data has changed.
        </p>
      </form>
    </section>
  );
}
