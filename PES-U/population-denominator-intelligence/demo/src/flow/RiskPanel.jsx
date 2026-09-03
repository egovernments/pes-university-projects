import { fmtInt, fmtPct } from "../lib/format.js";
import { riskMeta, RISK_FACTORS, parseFactors } from "../lib/risk.js";
import { gapMeta } from "../lib/gap.js";
import { IconShield } from "../components/icons.jsx";

const nameOf = (p) => p.name || p.boundaryCode;

// Per-factor sub-line: live detail where we have it, otherwise the static blurb.
function factorDetail(key, f, fallback) {
  if (key === "facility_distance" && f.distance_to_nearest_km != null)
    return `${f.distance_to_nearest_km.toFixed(1)} km to nearest facility`;
  if (key === "building_density" && f.buildings_per_km2 != null)
    return `${fmtInt(f.buildings_per_km2)} buildings / km²`;
  return fallback;
}

// One row: factor, its renormalised weight, the 0–1 score as a bar, and the
// points it contributes to the 100-point total (score × weight × 100).
function FactorRow({ meta, f }) {
  const score = Number(f.score) || 0;
  const points = score * f.weight * 100;
  return (
    <div className="rk-row">
      <div className="rk-main">
        <span className="rk-name">
          <span className="rk-dot" style={{ background: meta.color }} />
          {meta.label}
          {f.provisional && <span className="rk-prov">prov</span>}
        </span>
        <span className="rk-weight num">{fmtPct(f.weight, 0)}</span>
        <span className="rk-scorecell">
          <span className="rk-track">
            <span className="rk-fill"
              style={{ width: `${Math.min(score * 100, 100)}%`, background: meta.color }} />
          </span>
        </span>
        <span className={`rk-pts num ${points < 0.05 ? "zero" : ""}`}>{points.toFixed(1)}</span>
      </div>
      <div className="rk-sub">{factorDetail(meta.key, f, meta.desc)}</div>
    </div>
  );
}

// Risk detail for the live API run — same explainable breakdown as the legacy
// dashboard, but reading the live property contract (name/boundaryCode).
export default function RiskPanel({ props }) {
  if (!props) {
    return (
      <div className="panel">
        <div className="empty">
          <IconShield />
          <div>Select a district to see its risk score broken down by factor, or click a
            priority band in the legend to filter.</div>
        </div>
      </div>
    );
  }

  const m = riskMeta(props.risk_priority);
  const score = Number(props.risk_score) || 0;
  const factors = parseFactors(props.risk_factors);
  const gm = gapMeta(props.gap_classification);
  const rows = RISK_FACTORS.map((meta) => ({ meta, f: factors[meta.key] })).filter((r) => r.f);

  return (
    <div className="panel">
      <h2>{nameOf(props)}</h2>
      <div className="sub mono">{props.boundaryCode}</div>

      <div className="risk-hero" style={{ "--cls": m.color }}>
        <div className="risk-hero-top">
          <span className="risk-pill"><span className="dot" />{m.label} priority</span>
          <div className="risk-score">
            <span className="rs-value num">{score}</span>
            <span className="rs-max">/100</span>
          </div>
        </div>
        <div className="risk-hero-sub">
          Weighted priority across five factors — higher districts are actioned first.
        </div>
      </div>

      <div className="section-title">Factor breakdown</div>
      <div className="rk-table">
        <div className="rk-head">
          <span>Factor</span>
          <span className="rk-weight">Weight</span>
          <span className="rk-scorecell">Score</span>
          <span className="rk-pts">Pts</span>
        </div>
        {rows.map(({ meta, f }) => <FactorRow key={meta.key} meta={meta} f={f} />)}
        <div className="rk-total">
          <span>Total risk score</span>
          <span className="rk-total-val num" style={{ color: m.color }}>{score} <span>/ 100</span></span>
        </div>
      </div>

      <div className="section-title">District context</div>
      <div className="metric-grid">
        <div className="metric">
          <div className="k">Coverage gap</div>
          <div className="v" style={{ color: gm.color }}>{gm.label}</div>
        </div>
        <div className="metric">
          <div className="k">Population gap</div>
          <div className="v num">{fmtInt(props.population_gap ?? (Number(props.population_estimate) - Number(props.registered_population)))}</div>
        </div>
        <div className="metric">
          <div className="k">Estimated</div>
          <div className="v num">{fmtInt(props.population_estimate)}</div>
        </div>
        <div className="metric">
          <div className="k">Registered</div>
          <div className="v num">{fmtInt(props.registered_population)}</div>
        </div>
      </div>
    </div>
  );
}
