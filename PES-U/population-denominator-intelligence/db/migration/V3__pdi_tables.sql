CREATE TABLE population_estimate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    boundary_code VARCHAR(256) NOT NULL UNIQUE REFERENCES settlement_boundary (boundary_code),
    estimated_population INTEGER NOT NULL,
    estimated_households INTEGER,
    building_count INTEGER,
    confidence DOUBLE PRECISION,
    method VARCHAR(32) NOT NULL,
    worldpop_version VARCHAR(64),
    open_buildings_version VARCHAR(64),
    population_density DOUBLE PRECISION,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_id VARCHAR(64) NOT NULL
);

CREATE TABLE gap_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    boundary_code VARCHAR(256) NOT NULL REFERENCES settlement_boundary (boundary_code),
    campaign_id VARCHAR(128) NOT NULL,
    estimated_population INTEGER,
    registered_population INTEGER,
    population_gap INTEGER,
    estimated_households INTEGER,
    registered_households INTEGER,
    household_gap INTEGER,
    coverage_ratio DOUBLE PRECISION,
    gap_classification VARCHAR(16) CHECK (gap_classification IN ('GREEN', 'YELLOW', 'RED', 'BLACK')),
    risk_score INTEGER,
    risk_priority VARCHAR(16) CHECK (risk_priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    risk_factors JSONB,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_id VARCHAR(64) NOT NULL,
    CONSTRAINT uq_gap_report_campaign_boundary UNIQUE (campaign_id, boundary_code)
);

CREATE INDEX idx_gap_report_campaign_class ON gap_report (campaign_id, gap_classification);
CREATE INDEX idx_gap_report_campaign_risk ON gap_report (campaign_id, risk_score DESC);

CREATE TABLE invisible_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id VARCHAR(64) NOT NULL UNIQUE,
    centroid geometry(Point, 4326) NOT NULL,
    convex_hull geometry(Polygon, 4326),
    building_count INTEGER,
    estimated_population INTEGER,
    nearest_boundary_code VARCHAR(256),
    distance_to_nearest_km DOUBLE PRECISION,
    parent_boundary_code VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'DETECTED',
    verified_by VARCHAR(128),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at TIMESTAMPTZ,
    tenant_id VARCHAR(64) NOT NULL
);

CREATE INDEX idx_invisible_settlement_centroid ON invisible_settlement USING GIST (centroid);

CREATE TABLE building_footprint (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    polygon geometry(Polygon, 4326) NOT NULL,
    centroid geometry(Point, 4326) NOT NULL,
    area_m2 DOUBLE PRECISION,
    confidence DOUBLE PRECISION,
    boundary_code VARCHAR(256) REFERENCES settlement_boundary (boundary_code),
    source_dataset VARCHAR(64),
    source_version VARCHAR(64),
    tenant_id VARCHAR(64) NOT NULL
);

CREATE INDEX idx_building_footprint_polygon ON building_footprint USING GIST (polygon);
CREATE INDEX idx_building_footprint_centroid ON building_footprint USING GIST (centroid);
CREATE INDEX idx_building_footprint_boundary ON building_footprint (boundary_code);

CREATE TABLE risk_score_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_type VARCHAR(64) NOT NULL,
    weight_population_gap DOUBLE PRECISION NOT NULL,
    weight_building_density DOUBLE PRECISION NOT NULL,
    weight_facility_distance DOUBLE PRECISION NOT NULL,
    weight_past_performance DOUBLE PRECISION NOT NULL,
    weight_missed_children DOUBLE PRECISION NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_risk_config_type_tenant UNIQUE (campaign_type, tenant_id)
);
