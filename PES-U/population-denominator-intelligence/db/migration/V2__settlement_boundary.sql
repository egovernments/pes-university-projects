CREATE TABLE settlement_boundary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    boundary_code VARCHAR(256) NOT NULL UNIQUE,
    boundary_type VARCHAR(64),
    name VARCHAR(256),
    parent_boundary_code VARCHAR(256),
    polygon geometry(MultiPolygon, 4326) NOT NULL,
    area_km2 DOUBLE PRECISION,
    tenant_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_settlement_boundary_polygon ON settlement_boundary USING GIST (polygon);
CREATE INDEX idx_settlement_boundary_parent ON settlement_boundary (parent_boundary_code);
