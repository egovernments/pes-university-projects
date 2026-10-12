import json

from shapely.geometry import MultiPolygon
from sqlalchemy import text

import config
import db

CODE = config.BOUNDARY_CODE_FIELD
NAME = config.BOUNDARY_NAME_FIELD

_PARENT_FIELDS = ("microplan_province", "msp_province", "parent_boundary_code")


def _as_multipolygon_wkt(geometry):
    if geometry is None or geometry.is_empty:
        return None
    if geometry.geom_type == "Polygon":
        geometry = MultiPolygon([geometry])
    return geometry.wkt


def _value(row, *names, default=None):
    for name in names:
        if name in row and row[name] is not None and row[name] == row[name]:
            return row[name]
    return default


def _parent_of(row):
    return _value(row, *_PARENT_FIELDS)


def persist(campaign_id, tenant_id, units, registered=None, risk=None, invisible=None):
    """Upsert one country's engine output for ``campaign_id`` / ``tenant_id``."""
    engine = db.get_engine()
    with engine.begin() as conn:
        _write_boundaries(conn, tenant_id, units)
        _write_population(conn, tenant_id, units)
        _write_gap(conn, campaign_id, tenant_id, units, registered, risk)
        _write_invisible(conn, campaign_id, tenant_id, invisible)
    return {
        "boundaries": len(units),
        "campaign_id": campaign_id,
        "tenant_id": tenant_id,
    }


def persist_buildings(tenant_id, footprints):
    #Upsert building footprints for `tenant_id`. Returns the number of rows written
    if footprints is None or footprints.empty:
        return 0

    frame = footprints[footprints.geometry.geom_type == "Polygon"].copy()
    if frame.empty:
        return 0

    codes = [str(code) for code in frame["boundary_code"].dropna().unique().tolist()]
    frame = frame[["geometry", "area_m2", "confidence", "bf_source", "boundary_code"]].rename(
        columns={"bf_source": "source_dataset"})
    frame["source_version"] = config.OPEN_BUILDINGS_SOURCE
    frame["tenant_id"] = tenant_id
    frame = frame.set_geometry("geometry")

    engine = db.get_engine()
    with engine.begin() as conn:
        conn.execute(text(
            "DELETE FROM building_footprint WHERE tenant_id = :tenant_id "
            "AND boundary_code = ANY(:codes)"),
            {"tenant_id": tenant_id, "codes": codes})
    frame.to_postgis("building_footprint_stage", engine, if_exists="replace",
                     index=False, chunksize=10000)
    with engine.begin() as conn:
        conn.execute(text("""
            INSERT INTO building_footprint
                (polygon, centroid, area_m2, confidence, boundary_code,
                 source_dataset, source_version, tenant_id)
            SELECT geometry, ST_PointOnSurface(geometry), area_m2, confidence,
                   boundary_code, source_dataset, source_version, tenant_id
            FROM building_footprint_stage
        """))
        conn.execute(text("DROP TABLE IF EXISTS building_footprint_stage"))
    return len(frame)


def _write_boundaries(conn, tenant_id, units):
    rows = []
    for _, row in units.iterrows():
        polygon = _as_multipolygon_wkt(row.geometry)
        if polygon is None:
            continue
        rows.append({
            "boundary_code": row[CODE],
            "boundary_type": "CATCHMENT" if _value(row, "is_catchment", default=False) else "DISTRICT",
            "name": _value(row, NAME, "name"),
            "parent_boundary_code": _parent_of(row),
            "polygon": polygon,
            "area_km2": _value(row, "area_km2"),
            "tenant_id": tenant_id,
        })
    conn.execute(text("""
        INSERT INTO settlement_boundary
            (boundary_code, boundary_type, name, parent_boundary_code, polygon, area_km2, tenant_id)
        VALUES
            (:boundary_code, :boundary_type, :name, :parent_boundary_code,
             ST_Multi(ST_GeomFromText(:polygon, 4326)), :area_km2, :tenant_id)
        ON CONFLICT (boundary_code) DO UPDATE SET
            boundary_type = EXCLUDED.boundary_type,
            name = EXCLUDED.name,
            parent_boundary_code = EXCLUDED.parent_boundary_code,
            polygon = EXCLUDED.polygon,
            area_km2 = EXCLUDED.area_km2,
            tenant_id = EXCLUDED.tenant_id,
            updated_at = now()
    """), rows)


def _write_population(conn, tenant_id, units):
    rows = [{
        "boundary_code": row[CODE],
        "estimated_population": int(_value(row, "population_estimate", default=0)),
        "estimated_households": int(_value(row, "estimated_households", default=0)),
        "building_count": int(_value(row, "building_count", default=0)),
        "confidence": _value(row, "confidence"),
        "method": _value(row, "method", default="worldpop_primary"),
        "worldpop_version": config.WORLDPOP_VERSION,
        "open_buildings_version": config.OPEN_BUILDINGS_SOURCE,
        "population_density": _value(row, "density_ppl_km2"),
        "tenant_id": tenant_id,
    } for _, row in units.iterrows()]
    conn.execute(text("""
        INSERT INTO population_estimate
            (boundary_code, estimated_population, estimated_households, building_count,
             confidence, method, worldpop_version, open_buildings_version,
             population_density, tenant_id)
        VALUES
            (:boundary_code, :estimated_population, :estimated_households, :building_count,
             :confidence, :method, :worldpop_version, :open_buildings_version,
             :population_density, :tenant_id)
        ON CONFLICT (boundary_code) DO UPDATE SET
            estimated_population = EXCLUDED.estimated_population,
            estimated_households = EXCLUDED.estimated_households,
            building_count = EXCLUDED.building_count,
            confidence = EXCLUDED.confidence,
            method = EXCLUDED.method,
            worldpop_version = EXCLUDED.worldpop_version,
            open_buildings_version = EXCLUDED.open_buildings_version,
            population_density = EXCLUDED.population_density,
            tenant_id = EXCLUDED.tenant_id,
            computed_at = now()
    """), rows)


def _write_gap(conn, campaign_id, tenant_id, units, registered, risk):
    reg = registered.set_index(CODE) if registered is not None else None
    rsk = risk.set_index(CODE) if risk is not None else None

    rows = []
    for _, row in units.iterrows():
        code = row[CODE]
        estimated_pop = int(_value(row, "population_estimate", default=0))
        estimated_hh = int(_value(row, "estimated_households", default=0))

        registered_pop = registered_hh = 0
        coverage = classification = None
        if reg is not None and code in reg.index:
            r = reg.loc[code]
            registered_pop = int(_value(r, "registered_population", default=0))
            registered_hh = int(_value(r, "registered_households", default=0))
            coverage = _value(r, "coverage_ratio")
            classification = _value(r, "gap_classification")

        risk_score = risk_priority = None
        risk_factors = None
        if rsk is not None and code in rsk.index:
            rr = rsk.loc[code]
            risk_score = _value(rr, "risk_score")
            risk_priority = _value(rr, "risk_priority")
            factors = _value(rr, "risk_factors")
            risk_factors = factors if isinstance(factors, str) else json.dumps(factors) if factors else None

        rows.append({
            "boundary_code": code,
            "campaign_id": campaign_id,
            "estimated_population": estimated_pop,
            "registered_population": registered_pop,
            "population_gap": estimated_pop - registered_pop,
            "estimated_households": estimated_hh,
            "registered_households": registered_hh,
            "household_gap": estimated_hh - registered_hh,
            "coverage_ratio": coverage,
            "gap_classification": classification,
            "risk_score": None if risk_score is None else int(risk_score),
            "risk_priority": risk_priority,
            "risk_factors": risk_factors,
            "tenant_id": tenant_id,
        })
    conn.execute(text("""
        INSERT INTO gap_report
            (boundary_code, campaign_id, estimated_population, registered_population,
             population_gap, estimated_households, registered_households, household_gap,
             coverage_ratio, gap_classification, risk_score, risk_priority, risk_factors, tenant_id)
        VALUES
            (:boundary_code, :campaign_id, :estimated_population, :registered_population,
             :population_gap, :estimated_households, :registered_households, :household_gap,
             :coverage_ratio, :gap_classification, :risk_score, :risk_priority,
             CAST(:risk_factors AS JSONB), :tenant_id)
        ON CONFLICT (campaign_id, boundary_code) DO UPDATE SET
            estimated_population = EXCLUDED.estimated_population,
            registered_population = EXCLUDED.registered_population,
            population_gap = EXCLUDED.population_gap,
            estimated_households = EXCLUDED.estimated_households,
            registered_households = EXCLUDED.registered_households,
            household_gap = EXCLUDED.household_gap,
            coverage_ratio = EXCLUDED.coverage_ratio,
            gap_classification = EXCLUDED.gap_classification,
            risk_score = EXCLUDED.risk_score,
            risk_priority = EXCLUDED.risk_priority,
            risk_factors = EXCLUDED.risk_factors,
            tenant_id = EXCLUDED.tenant_id,
            computed_at = now()
    """), rows)


def _write_invisible(conn, campaign_id, tenant_id, invisible):
    conn.execute(text(
        "DELETE FROM invisible_settlement WHERE tenant_id = :tenant_id"),
        {"tenant_id": tenant_id})
    if invisible is None or invisible.empty:
        return

    rows = []
    for _, row in invisible.iterrows():
        lon = _value(row, "centroid_lon")
        lat = _value(row, "centroid_lat")
        if lon is None or lat is None:
            continue
        hull = row.geometry.wkt if row.geometry is not None and not row.geometry.is_empty else None
        distance = _value(row, "distance_to_nearest_km")
        rows.append({
            "cluster_id": row["cluster_id"],
            "centroid": f"POINT({lon} {lat})",
            "convex_hull": hull,
            "building_count": int(_value(row, "building_count", default=0)),
            "estimated_population": int(_value(row, "estimated_population", default=0)),
            "nearest_boundary_code": _value(row, "nearest_boundary_code"),
            "distance_to_nearest_km": None if distance is None else float(distance),
            "parent_boundary_code": _value(row, "parent_boundary_code"),
            "status": _value(row, "status", default=config.INVISIBLE_STATUS_INITIAL),
            "tenant_id": tenant_id,
        })
    if not rows:
        return
    conn.execute(text("""
        INSERT INTO invisible_settlement
            (cluster_id, centroid, convex_hull, building_count, estimated_population,
             nearest_boundary_code, distance_to_nearest_km, parent_boundary_code, status, tenant_id)
        VALUES
            (:cluster_id, ST_GeomFromText(:centroid, 4326),
             ST_GeomFromText(:convex_hull, 4326), :building_count, :estimated_population,
             :nearest_boundary_code, :distance_to_nearest_km, :parent_boundary_code,
             :status, :tenant_id)
        ON CONFLICT (cluster_id) DO UPDATE SET
            centroid = EXCLUDED.centroid,
            convex_hull = EXCLUDED.convex_hull,
            building_count = EXCLUDED.building_count,
            estimated_population = EXCLUDED.estimated_population,
            nearest_boundary_code = EXCLUDED.nearest_boundary_code,
            distance_to_nearest_km = EXCLUDED.distance_to_nearest_km,
            parent_boundary_code = EXCLUDED.parent_boundary_code,
            status = EXCLUDED.status,
            tenant_id = EXCLUDED.tenant_id
    """), rows)
