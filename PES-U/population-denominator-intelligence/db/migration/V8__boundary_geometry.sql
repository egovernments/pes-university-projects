CREATE TABLE IF NOT EXISTS eg_pdi_boundary_geometry (
    id                  VARCHAR(64)  PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    hierarchy_type      VARCHAR(128) NOT NULL,
    boundary_code       VARCHAR(256) NOT NULL,
    geometry_type       VARCHAR(16)  NOT NULL CHECK (geometry_type IN ('POLYGON', 'POINT')),
    geom                geometry(Geometry, 4326) NOT NULL,
    source              VARCHAR(16)  NOT NULL CHECK (source IN ('SHAPE_UPLOAD', 'SHEET_POINT')),
    file_store_id       VARCHAR(128),            -- the upload it came from
    created_by          VARCHAR(64),
    created_time        BIGINT       NOT NULL,
    last_modified_by    VARCHAR(64),
    last_modified_time  BIGINT       NOT NULL,   -- drives change detection in DELTA runs
    CONSTRAINT uq_pdi_geom_code UNIQUE (tenant_id, hierarchy_type, boundary_code)
);

CREATE INDEX IF NOT EXISTS idx_pdi_geom_gist ON eg_pdi_boundary_geometry USING GIST (geom);
