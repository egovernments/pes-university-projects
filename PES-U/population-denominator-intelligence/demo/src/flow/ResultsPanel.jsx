import { fmtInt, fmtPct } from "../lib/format.js";
import { subPopulation } from "../lib/demographics.js";
import AgePyramid from "../components/AgePyramid.jsx";
import { IconMap } from "../components/icons.jsx";

// Mirrors the batch engine's blend (pdi-batch/features/estimation.py):
// ensemble = 0.6 * WorldPop + 0.4 * (buildings * household size).
const ENSEMBLE_W_WP = 0.6;
const ENSEMBLE_W_BLD = 0.4;

function Metric({ k, v }) {
  return (
    <div className="metric">
      <div className="k">{k}</div>
      <div className="v num">{v}</div>
    </div>
  );
}

function confColor(c) {
  if (c >= 0.85) return "var(--st-exact)";
  if (c >= 0.65) return "var(--accent)";
  return "var(--danger)";
}

// Headline estimate with a transparent derivation: which sources fed it and how.
function EstimateBreakdown({ props, householdSize }) {
  const wp = Number(props.total) || 0;
  const buildings = Number(props.building_count) || 0;
  const bldEst = buildings * householdSize;
  const estimate = Number(props.population_estimate ?? props.total) || 0;
  const method = props.method;

  const wWp = method === "ensemble" ? ENSEMBLE_W_WP : method === "buildings_only" ? 0 : 1;
  const wBld = method === "ensemble" ? ENSEMBLE_W_BLD : method === "buildings_only" ? 1 : 0;

  const heroLabel =
    method === "buildings_only" ? "Buildings-only estimate"
    : method === "worldpop_primary" ? "WorldPop estimate"
    : "Ensemble estimate";

  return (
    <div className="estimate">
      <div className="est-hero">
        <div className="est-label">{heroLabel}</div>
        <div className="est-value num">{fmtInt(estimate)}</div>
        {method === "ensemble" && (
          <div className="est-formula num">
            {ENSEMBLE_W_WP} × {fmtInt(wp)} + {ENSEMBLE_W_BLD} × {fmtInt(bldEst)}
          </div>
        )}
        {method === "worldpop_primary" && (
          <div className="est-note">
            WorldPop only — building estimate diverged {fmtPct(Number(props.divergence) || 0)} (&gt; 30%)
          </div>
        )}
        {method === "buildings_only" && (
          <div className="est-note">Buildings only — WorldPop returned 0 for this boundary</div>
        )}
      </div>

      <div className="est-sources">
        <div className={`src ${wWp === 0 ? "muted" : ""}`}>
          <span className="src-dot wp" />
          <span className="src-name">WorldPop</span>
          <span className="src-val num">{fmtInt(wp)}</span>
          <span className="src-w">{fmtPct(wWp)}</span>
        </div>
        <div className={`src ${wBld === 0 ? "muted" : ""}`}>
          <span className="src-dot bld" />
          <span className="src-name">
            Building estimate <em>{fmtInt(buildings)} × {householdSize}</em>
          </span>
          <span className="src-val num">{fmtInt(bldEst)}</span>
          <span className="src-w">{fmtPct(wBld)}</span>
        </div>
      </div>
    </div>
  );
}

/**
 * Detail panel for the selected boundary, driven by the API's units.geojson.
 * @param props selected feature properties (or null)
 * @param sex/from/to current age-sex filter (band indices); groupLabel its label
 */
export default function ResultsPanel({ props, sex, from, to, groupLabel, householdSize, emptyHint }) {
  if (!props) {
    return (
      <div className="panel">
        <div className="empty">
          <IconMap />
          <div>{emptyHint || "Select a boundary on the map to see its details."}</div>
        </div>
      </div>
    );
  }

  const name = props.name || props.boundaryCode;
  const conf = Number(props.confidence) || 0;
  const isEnsemble = props.method === "ensemble";
  const hasEstimate = props.population_estimate != null || props.method != null;

  const hasFilter = sex != null && from != null && to != null;
  const groupPop = hasFilter ? subPopulation(props, sex, from, to) : 0;
  const districtTotal = Number(props.total) || 0;
  const groupShare = districtTotal > 0 ? groupPop / districtTotal : 0;

  return (
    <div className="panel">
      <h2>{name}</h2>
      <div className="sub mono">{props.boundaryCode}</div>
      {props.is_catchment ? <span className="badge">catchment cell</span> : null}

      {hasFilter && groupShare < 0.999 && (
        <div className="group-callout">
          <div className="gc-label">{groupLabel}</div>
          <div className="gc-value num">{fmtInt(groupPop)}</div>
          <div className="gc-sub num">{fmtPct(groupShare, 1)} of boundary population</div>
        </div>
      )}

      {hasEstimate && (
        <>
          <div className="section-title">Population estimate</div>
          <EstimateBreakdown props={props} householdSize={householdSize} />
          <div className="metric-grid">
            <Metric k="Density (ppl/km²)" v={fmtInt(props.density_ppl_km2)} />
            <Metric k="Area (km²)" v={fmtInt(props.area_km2)} />
          </div>

          <div className="section-title">Method &amp; confidence</div>
          <div className="metric-grid">
            <Metric k="Method" v={isEnsemble ? "Ensemble" : "WorldPop primary"} />
            <Metric k="Est. households" v={fmtInt(props.estimated_households ?? props.household_target)} />
          </div>
          <div style={{ marginTop: 10 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--muted)" }}>
              <span>Confidence</span>
              <span className="num" style={{ fontWeight: 600, color: "var(--ink)" }}>{fmtPct(conf)}</span>
            </div>
            <div className="conf-bar">
              <span style={{ width: `${conf * 100}%`, background: confColor(conf) }} />
            </div>
          </div>
        </>
      )}

      <div className="section-title">Age / sex structure</div>
      <AgePyramid props={props} from={from} to={to} sex={sex} />
    </div>
  );
}
