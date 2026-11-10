CREATE TABLE IF NOT EXISTS eg_pdi_boundary_population (
    id                    VARCHAR(64)  PRIMARY KEY,
    run_id                VARCHAR(64)  NOT NULL
        REFERENCES eg_pdi_population_run (id) ON DELETE CASCADE,
    tenant_id             VARCHAR(64)  NOT NULL,
    hierarchy_type        VARCHAR(128) NOT NULL,
    boundary_code         VARCHAR(256) NOT NULL,
    boundary_type         VARCHAR(64),
    parent_boundary_code  VARCHAR(256),
    total_population      INTEGER,                -- null when NO_GEOMETRY
    population            JSONB,                  -- {"total":1234,"under5":210}
    method                VARCHAR(16)  NOT NULL
        CHECK (method IN ('ZONAL_UPLOAD', 'POINT_VORONOI', 'ROLLUP', 'NO_GEOMETRY')),
    area_km2              DOUBLE PRECISION,
    created_time          BIGINT       NOT NULL,
    CONSTRAINT uq_pdi_bp_run_code UNIQUE (run_id, boundary_code)
);

CREATE INDEX IF NOT EXISTS idx_pdi_bp_lookup
    ON eg_pdi_boundary_population (tenant_id, hierarchy_type, boundary_code);
