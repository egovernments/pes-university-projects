# Feature 5 - explainable weighted risk scoring; enriches the gap report with a 0-100 score.

import argparse
import json
from datetime import datetime, timezone

import geopandas as gpd
import pandas as pd

import config
from geo import resolve_metric_crs

CODE = config.BOUNDARY_CODE_FIELD

RISK_COLUMNS = ["risk_score", "risk_priority", "risk_factors", "risk_computed_at"]


def _normalize(series):
    """Min-max scale to 0-1 over the units in scope (the regional_stats baseline)."""
    low, high = series.min(), series.max()
    if pd.isna(low) or pd.isna(high) or high <= low:
        return pd.Series(0.0, index=series.index)
    return ((series - low) / (high - low)).clip(0, 1)


def _facility_distance_km(units, centers):
    """Km from each unit's interior point to the nearest catchment centre, or None if none given."""
    if centers is None or len(centers) == 0:
        return None
    metric_crs = resolve_metric_crs(units)
    centers = centers.to_crs(metric_crs)
    interiors = gpd.GeoDataFrame(
        geometry=units.to_crs(metric_crs).geometry.representative_point(), crs=metric_crs)
    nearest = gpd.sjoin_nearest(interiors, centers[["geometry"]], distance_col="dist_m")
    nearest = nearest[~nearest.index.duplicated(keep="first")].reindex(units.index)
    return (nearest["dist_m"] / 1000).round(3)


def priority_for(score):
    if score >= config.RISK_CRITICAL_THRESHOLD:
        return "CRITICAL"
    if score >= config.RISK_HIGH_THRESHOLD:
        return "HIGH"
    if score >= config.RISK_MEDIUM_THRESHOLD:
        return "MEDIUM"
    return "LOW"


def _renormalized_weights(active_names):
    """RISK_WEIGHTS restricted to the available factors, renormalised to sum 1.0."""
    total = sum(config.RISK_WEIGHTS[name] for name in active_names)
    return {name: config.RISK_WEIGHTS[name] / total for name in active_names}


def _column(df, name):
    """Numeric view of an optional column, or ``None`` when it is absent or entirely empty."""
    if name not in df.columns:
        return None
    series = pd.to_numeric(df[name], errors="coerce")
    return None if series.isna().all() else series


def _past_performance(df):
    """Factor 2 - enumerator engagement: risk rises as the active share of users falls.

    Read from the enumeration workbook's active-vs-provisioned user counts. A facility that
    was given 21 accounts and activated none of them is the clearest predictor in the sheet
    that its area will be under-covered.
    """
    active = _column(df, "active_user_pct")
    if active is None:
        return None
    return (1 - active.clip(0, 1)).fillna(config.RISK_MISSING_FACTOR_DEFAULT)


def _missed_children(df):
    """Factor 5 - households the team reached but could not enumerate.

    The absent share of visited households. Distinct from the population gap: that measures
    how much of the area was never reached, this measures failure at the door.
    """
    absent = _column(df, "absent_households")
    if absent is None:
        return None
    visited = df["registered_households"].fillna(0) + absent.fillna(0)
    return (absent / visited).where(visited > 0).fillna(
        config.RISK_MISSING_FACTOR_DEFAULT).clip(0, 1)


def _factors_json(scores, weights, provisional, building_density, distance_km, context):
    """Per-factor breakdown (score, renormalised weight, provenance) for the risk_factors jsonb."""
    factors = {}
    for name, score in scores.items():
        entry = {
            "score": round(float(score), 4),
            "weight": round(float(weights[name]), 4),
            "provisional": name in provisional,
        }
        if name == "building_density":
            entry["buildings_per_km2"] = round(float(building_density), 2)
        elif name == "facility_distance":
            entry["distance_to_nearest_km"] = None if pd.isna(distance_km) else round(
                float(distance_km), 3)
        elif name == "past_performance":
            entry["active_user_pct"] = _maybe(context.get("active_user_pct"))
        elif name == "missed_children":
            entry["absent_households"] = _maybe(context.get("absent_households"), integer=True)
        factors[name] = entry
    return factors


def _maybe(value, integer=False):
    """JSON-safe scalar: ``None`` for missing, otherwise a rounded number."""
    if value is None or pd.isna(value):
        return None
    return int(value) if integer else round(float(value), 4)


def score(gap_gdf, est, centers=None):
    gap = gap_gdf.drop(columns=[c for c in RISK_COLUMNS if c in gap_gdf.columns])
    df = gap.merge(est[[CODE, "building_count", "area_km2"]], on=CODE, how="left")

    # Factor 1 - population gap relative to the estimate, clamped to 0-1 (registered overshoot -> 0).
    gap_score = (df["population_gap"] / df["estimated_population"]).where(
        df["estimated_population"] > 0, 0.0).clip(0, 1)

    # Factor 4 - building density normalised against the units in scope.
    building_density = (df["building_count"] / df["area_km2"]).replace(
        [float("inf"), float("-inf")], pd.NA).fillna(0)
    density_score = _normalize(building_density)

    # Factor 3 - access falls with distance to the nearest catchment centre (dropped if none given).
    distance_km = _facility_distance_km(df, centers)
    if distance_km is not None:
        access_score = (1 - (distance_km / config.RISK_FACILITY_MAX_KM).clip(0, 1)).fillna(
            config.RISK_MISSING_FACTOR_DEFAULT)
    else:
        access_score = None

    # Factors 2 and 5 now read from the enumeration workbook. Either falls back to a
    # neutral, provisional value only when the sheet does not carry its column.
    performance_score = _past_performance(df)
    missed_score = _missed_children(df)
    neutral = pd.Series(config.RISK_MISSING_FACTOR_DEFAULT, index=df.index)

    provisional = set()
    for name, series in (("past_performance", performance_score),
                         ("missed_children", missed_score)):
        if series is None:
            provisional.add(name)

    components = {
        "population_gap": gap_score,
        "past_performance": performance_score if performance_score is not None else neutral,
        "facility_distance": access_score,   # None -> factor dropped, weights renormalise
        "building_density": density_score,
        "missed_children": missed_score if missed_score is not None else neutral,
    }
    active = {name: series for name, series in components.items() if series is not None}
    weights = _renormalized_weights(active)
    raw = sum(weights[name] * series for name, series in active.items())
    df["risk_score"] = (raw * 100).round().astype(int)
    df["risk_priority"] = df["risk_score"].map(priority_for)

    per_row = pd.DataFrame(active)
    df["risk_factors"] = [
        json.dumps(_factors_json(
            per_row.loc[i], weights, provisional, building_density.loc[i],
            distance_km.loc[i] if distance_km is not None else float("nan"),
            {"active_user_pct": df["active_user_pct"].get(i) if "active_user_pct" in df else None,
             "absent_households": df["absent_households"].get(i) if "absent_households" in df else None}))
        for i in df.index
    ]
    df["risk_computed_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")

    ordered = [c for c in gap.columns if c != "geometry"] + RISK_COLUMNS
    table = df[ordered].reset_index(drop=True)
    gdf = gpd.GeoDataFrame(
        df[ordered + ["geometry"]], geometry="geometry", crs=config.STORAGE_CRS)
    return table, gdf


def build(centers=None):
    """Batch entry point: score the on-disk gap report (see :func:`score`)."""
    if not config.GAP_REPORT_GEOJSON.exists():
        raise FileNotFoundError(
            f"{config.GAP_REPORT_GEOJSON} not found - run features.gap first")
    if not config.DISTRICT_POPULATION_CSV.exists():
        raise FileNotFoundError(
            f"{config.DISTRICT_POPULATION_CSV} not found - run features.estimation first")

    gap = gpd.read_file(config.GAP_REPORT_GEOJSON)
    est = pd.read_csv(
        config.DISTRICT_POPULATION_CSV, usecols=[CODE, "building_count", "area_km2"])
    return score(gap, est, centers)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--boundaries",
                        help="uploaded catchment geojson; its facility anchors enable "
                             "the facility-distance factor")
    args = parser.parse_args()

    centers = None
    if args.boundaries:
        from sources.catchments import load_boundary_upload
        centers = load_boundary_upload(args.boundaries).anchors

    table, gdf = build(centers)
    config.GAP_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    table.to_csv(config.GAP_REPORT_CSV, index=False, encoding="utf-8-sig")
    gdf.to_file(config.GAP_REPORT_GEOJSON, driver="GeoJSON")

    counts = table["risk_priority"].value_counts()
    print(f"units scored:             {len(table):>12,}")
    print(f"mean risk score:          {table['risk_score'].mean():>12.1f}")
    print("priority:")
    for label in ["CRITICAL", "HIGH", "MEDIUM", "LOW"]:
        print(f"  {label:<9} {int(counts.get(label, 0)):>6}")
    print(f"\nWrote:\n  {config.GAP_REPORT_CSV}\n  {config.GAP_REPORT_GEOJSON}")


if __name__ == "__main__":
    main()
