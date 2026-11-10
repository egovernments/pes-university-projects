CREATE TABLE IF NOT EXISTS eg_pdi_population_run (
    id                  VARCHAR(64)  PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    hierarchy_type      VARCHAR(128) NOT NULL,
    iso3                VARCHAR(3)   NOT NULL,
    year                SMALLINT     NOT NULL,
    worldpop_release    VARCHAR(64),
    groups              JSONB        NOT NULL,   -- ["total","under5",...]
    mode                VARCHAR(8)   NOT NULL CHECK (mode IN ('DELTA', 'FULL')),
    status              VARCHAR(16)  NOT NULL
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    is_active           BOOLEAN      NOT NULL DEFAULT false,
    progress            SMALLINT,
    boundary_count      INTEGER,
    computed_count      INTEGER,
    no_geometry_count   INTEGER,
    error_message       TEXT,
    created_by          VARCHAR(64),
    created_time        BIGINT       NOT NULL,
    last_modified_by    VARCHAR(64),
    last_modified_time  BIGINT
);

-- At most one active run, and at most one in-flight run, per tenant and hierarchy type.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pdi_run_active
    ON eg_pdi_population_run (tenant_id, hierarchy_type) WHERE is_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_pdi_run_inflight
    ON eg_pdi_population_run (tenant_id, hierarchy_type)
    WHERE status IN ('PENDING', 'RUNNING');
