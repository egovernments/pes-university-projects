import { countryName } from "./countries.js";

export default function LoadingView({ iso3, message, percent }) {
  const hasBar = typeof percent === "number";
  return (
    <div className="flow-stage">
      <div className="loading-card">
        <div className="loader-ring" aria-hidden="true" />
        <h2>Computing targets for {countryName(iso3)}</h2>
        <p>
          Fetching boundaries, WorldPop population and VIDA building footprints, then running the
          estimation engine. The first run for a country downloads rasters and can take a few minutes.
        </p>
        {hasBar && (
          <div
            className="dl-progress"
            role="progressbar"
            aria-valuenow={percent}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div className="dl-bar">
              <span style={{ width: `${percent}%` }} />
            </div>
            <div className="dl-pct">{percent}%</div>
          </div>
        )}
        {message && <div className="loading-progress mono">{message}</div>}
      </div>
    </div>
  );
}
