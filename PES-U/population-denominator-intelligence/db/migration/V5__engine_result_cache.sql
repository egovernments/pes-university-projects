CREATE TABLE IF NOT EXISTS engine_result_cache (
    cache_key    VARCHAR(64) PRIMARY KEY,   -- sha256 of the normalized request
    iso3         VARCHAR(8)  NOT NULL,
    params       JSONB       NOT NULL,       -- {householdSize, withBuildings, groups, sheetHash}
    response     JSONB       NOT NULL,       -- the TargetComputeResponse (URLs re-minted per hit)
    geojson      TEXT,                       -- whole-country units + demographics
    stats_json   TEXT,
    settlements  TEXT,                       -- invisible-settlement clusters
    catchments   TEXT,                       -- Voronoi catchment cells (sheet runs)
    buildings    TEXT,                       -- catchment building points (sheet runs)
    sheet_xlsx   BYTEA,                      -- filled downloadable sheet (sheet runs)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_hit_at  TIMESTAMPTZ,
    hit_count    INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_engine_result_cache_iso3 ON engine_result_cache (iso3);
