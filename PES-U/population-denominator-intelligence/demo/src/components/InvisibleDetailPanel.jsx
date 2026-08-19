import { fmtInt } from "../lib/format.js";
import { bucketOf } from "../lib/invisible.js";
import { IconPin } from "./icons.jsx";

function Metric({ k, v }) {
  return (
    <div className="metric">
      <div className="k">{k}</div>
      <div className="v num">{v}</div>
    </div>
  );
}

export default function InvisibleDetailPanel({ props, districtName }) {
  if (!props) {
    return (
      <div className="panel">
        <div className="empty">
          <IconPin />
          <div>
            <b>Violet</b> shapes are building clusters detected from satellite imagery; <b>teal</b>{" "}
            dots are registered households. A cluster is “invisible” when no teal dot falls within
            200 m of it. Click any settlement to inspect it, or filter by size in the legend.
          </div>
        </div>
      </div>
    );
  }

  const b = bucketOf(Number(props.building_count));
  const buildings = Number(props.building_count) || 0;
  const est = Number(props.estimated_population) || 0;
  const parent = districtName?.(props.parent_boundary_code) || props.parent_boundary_code;

  return (
    <div className="panel">
      <h2>Invisible settlement</h2>
      <div className="sub mono">{props.cluster_id}</div>

      <div className="inv-hero" style={{ "--cls": b.color }}>
        <div className="inv-hero-head">
          <span className="inv-tag">{b.label} buildings</span>
          <span className="inv-badge">{props.status || "—"}</span>
        </div>
        <div className="inv-hero-label">Estimated unregistered population</div>
        <div className="inv-hero-value num">{fmtInt(est)}</div>
      </div>

      <div className="section-title">Detection</div>
      <div className="metric-grid">
        <Metric k="Buildings" v={fmtInt(buildings)} />
        <Metric k="Est. population" v={fmtInt(est)} />
        <Metric k="Dist. to register" v={`${props.distance_to_nearest_km} km`} />
        <Metric k="Parent district" v={parent} />
        <Metric k="Nearest district" v={districtName?.(props.nearest_boundary_code) || "—"} />
        <Metric k="Campaign" v={props.campaign_id || "—"} />
      </div>
    </div>
  );
}
