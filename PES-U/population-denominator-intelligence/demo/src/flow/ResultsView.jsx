import { useMemo, useState } from "react";
import ResultsMap from "./ResultsMap.jsx";
import ResultsPanel from "./ResultsPanel.jsx";
import CoverageView from "./CoverageView.jsx";
import InvisibleView from "./InvisibleView.jsx";
import CatchmentsView from "./CatchmentsView.jsx";
import RiskView from "./RiskView.jsx";
import StatCards from "../components/StatCards.jsx";
import FilterControls, { filterLabel } from "../components/FilterControls.jsx";
import { subPopulation } from "../lib/demographics.js";
import { hasRisk } from "../lib/risk.js";
import { fmtInt } from "../lib/format.js";
import {
  recomputeFeatureCollection,
  DEFAULT_HOUSEHOLD_SIZE,
  HOUSEHOLD_SIZE_MIN,
  HOUSEHOLD_SIZE_MAX,
  HOUSEHOLD_SIZE_STEP,
} from "../lib/estimate.js";
import { countryName } from "./countries.js";
import { IconFilter, IconGauge, IconPin, IconLayers, IconShield } from "../components/icons.jsx";

const nameOf = (p) => p.name || p.boundaryCode;

// The registration/coverage overlay only exists for countries the engine has a
// register for (N'Djamena today); elsewhere the geojson carries no such property,
// so the Coverage tab is hidden and every other feature still renders.
const hasRegistration = (result, geojson) =>
  result.registeredAvailable ||
  geojson.features.some((f) => f.properties.registered_population != null);

export default function ResultsView({ result, geojson, initialHouseholdSize, onReset }) {
  const [tab, setTab] = useState("explorer");
  // Age/sex denominator filter (band indices). Default: whole population.
  const [filter, setFilter] = useState({ sex: "both", from: 0, to: 19 });
  const [selected, setSelected] = useState(null);
  // The household size actually used for the estimate, re-tunable live. Seeded
  // with whatever the run was computed at so the first render matches the engine.
  const [householdSize, setHouseholdSize] = useState(
    initialHouseholdSize || result.householdSize || result.avgHouseholdSize || DEFAULT_HOUSEHOLD_SIZE
  );
  const { sex, from, to } = filter;

  const set = (patch) => setFilter((f) => ({ ...f, ...patch }));

  // Re-blend every boundary's population estimate at the current household size.
  const data = useMemo(
    () => recomputeFeatureCollection(geojson, householdSize),
    [geojson, householdSize]
  );

  const value = useMemo(() => (p) => subPopulation(p, sex, from, to), [sex, from, to]);
  const label = filterLabel(sex, from, to);

  const { groupTotal, totalPop } = useMemo(() => {
    let gt = 0;
    let tp = 0;
    for (const f of data.features) {
      gt += value(f.properties);
      tp += Number(f.properties.total) || 0;
    }
    return { groupTotal: gt, totalPop: tp };
  }, [data, value]);

  const catchments = geojson.features.filter((f) => f.properties.is_catchment).length;
  const unitWord = catchments ? "catchments" : "boundaries";
  const downloadUrl = result.downloadUrl;
  const showCoverage = hasRegistration(result, geojson);
  const showInvisible = result.invisibleAvailable && !!result.settlementsUrl;
  const showCatchments = !!result.catchmentsUrl;
  const showRisk = result.riskAvailable && hasRisk(geojson);

  // Everything on screen is derived from this run's uploads, so the explorer names the
  // unit the upload actually supplied rather than always claiming "district".
  const tabs = [
    { id: "explorer", label: catchments ? "Catchment explorer" : "District explorer", Icon: IconFilter },
    ...(showCatchments ? [{ id: "catchments", label: "Catchments", Icon: IconLayers }] : []),
    ...(showCoverage ? [{ id: "coverage", label: "Coverage gap", Icon: IconGauge }] : []),
    ...(showInvisible ? [{ id: "invisible", label: "Invisible settlements", Icon: IconPin }] : []),
    ...(showRisk ? [{ id: "risk", label: "Risk priority", Icon: IconShield }] : []),
  ];
  const available = new Set(tabs.map((t) => t.id));
  const activeTab = available.has(tab) ? tab : "explorer";
  // Household size only drives building-based estimates (explorer + catchments);
  // coverage/risk/invisible read the engine's own numbers, so hide it there.
  const showHouseholdControl = activeTab === "explorer" || activeTab === "catchments";

  // Keep the open detail panel in sync as the household size is re-tuned.
  const selectedLive = useMemo(() => {
    if (!selected) return null;
    const match = data.features.find((f) => f.properties.boundaryCode === selected.boundaryCode);
    return match ? match.properties : selected;
  }, [selected, data]);

  return (
    <div className="results">
      <header className="results-bar">
        <div className="results-id">
          <span className="results-country">{countryName(result.iso3)}</span>
          <span className="results-meta mono">{result.iso3}</span>
        </div>
        <div className="results-stats">
          <Stat value={fmtInt(result.boundaryCount ?? geojson.features.length)} label={unitWord} />
          <Stat value={fmtInt(totalPop)} label="total population" />
        </div>
        <div className="results-actions">
          {showHouseholdControl && (
            <HouseholdControl value={householdSize} onChange={setHouseholdSize} />
          )}
          <button className="ghost" onClick={onReset}>New run</button>
        </div>
      </header>

      <nav className="results-tabs">
        {tabs.map(({ id, label: tabLabel, Icon }) => (
          <button
            key={id}
            className={`rtab ${activeTab === id ? "active" : ""}`}
            onClick={() => setTab(id)}
            aria-current={activeTab === id ? "page" : undefined}
          >
            <Icon />
            {tabLabel}
          </button>
        ))}
      </nav>

      {activeTab === "catchments" ? (
        <CatchmentsView
          base={data}
          catchmentsUrl={result.catchmentsUrl}
          buildingsUrl={result.buildingsUrl}
          householdSize={householdSize}
          downloadUrl={downloadUrl}
        />
      ) : activeTab === "coverage" ? (
        <CoverageView geojson={geojson} downloadUrl={downloadUrl} />
      ) : activeTab === "invisible" ? (
        <InvisibleView settlementsUrl={result.settlementsUrl} />
      ) : activeTab === "risk" ? (
        <RiskView geojson={geojson} />
      ) : (
        <div className="results-scroll">
          <StatCards
            features={data.features}
            value={value}
            label={label}
            nationalTotal={groupTotal}
            totalPop={totalPop}
            nameOf={nameOf}
            scope={`across ${unitWord}`}
          />
          <FilterControls
            sex={sex}
            from={from}
            to={to}
            set={set}
            nationalTotal={groupTotal}
            sharePct={totalPop ? groupTotal / totalPop : 0}
            scopeLabel={`across ${unitWord}`}
          />
          <div className="split results-split-map">
            <ResultsMap
              geojson={data}
              value={value}
              format={fmtInt}
              legendTitle={label}
              onSelect={setSelected}
              selectedCode={selected?.boundaryCode}
              downloadUrl={downloadUrl}
            />
            <ResultsPanel
              props={selectedLive}
              sex={sex}
              from={from}
              to={to}
              groupLabel={label}
              householdSize={householdSize}
              emptyHint="Set an age range and sex above, then click a boundary to see its estimate and demographic structure."
            />
          </div>
        </div>
      )}
    </div>
  );
}

function Stat({ value, label }) {
  return (
    <div className="results-stat">
      <span className="rs-value num">{value}</span>
      <span className="rs-label">{label}</span>
    </div>
  );
}

// Live average-household-size stepper. Drives the client-side re-blend of every
// building-based population estimate, so the whole dashboard updates as you tune it.
function HouseholdControl({ value, onChange }) {
  const clamp = (n) =>
    Math.min(HOUSEHOLD_SIZE_MAX, Math.max(HOUSEHOLD_SIZE_MIN, Math.round(n * 10) / 10));
  const nudge = (delta) => onChange(clamp(value + delta));

  return (
    <div className="hh-control" title="Average people per household — retunes every estimate live">
      <span className="hh-label">Avg household size</span>
      <div className="hh-stepper">
        <button
          type="button"
          className="hh-btn"
          onClick={() => nudge(-HOUSEHOLD_SIZE_STEP)}
          disabled={value <= HOUSEHOLD_SIZE_MIN}
          aria-label="Decrease household size"
        >
          −
        </button>
        <input
          className="hh-input num"
          type="number"
          step={HOUSEHOLD_SIZE_STEP}
          min={HOUSEHOLD_SIZE_MIN}
          max={HOUSEHOLD_SIZE_MAX}
          value={value}
          onChange={(e) => {
            const n = Number(e.target.value);
            if (Number.isFinite(n)) onChange(clamp(n));
          }}
        />
        <button
          type="button"
          className="hh-btn"
          onClick={() => nudge(HOUSEHOLD_SIZE_STEP)}
          disabled={value >= HOUSEHOLD_SIZE_MAX}
          aria-label="Increase household size"
        >
          +
        </button>
      </div>
    </div>
  );
}
