"""Feature 4 - settlements the enumeration never reached. Dormant; see ``config.INVISIBLE_ENABLED``.

Detection asks a question only point-level data can answer: is there a cluster of buildings
with no enumerated household within 200 m? The current enumeration workbook is aggregate -
counts per facility, no coordinates - so nothing here can run against it. The module now
takes household points as an argument rather than reading a fixed register, so it works
unchanged the day a point-level household export arrives.
"""

import argparse
from datetime import datetime, timezone

import geopandas as gpd
import pandas as pd
from shapely import concave_hull
from sklearn.cluster import DBSCAN

import config
from geo import resolve_metric_crs
from sources import buildings as buildings_source
from sources.boundaries import load_boundaries

CODE = config.BOUNDARY_CODE_FIELD

TABLE_COLUMNS = [
    "cluster_id", "parent_boundary_code", "building_count", "estimated_population",
    "nearest_boundary_code", "distance_to_nearest_km",
    "centroid_lon", "centroid_lat", "status", "detected_at", "campaign_id", "tenant_id",
]


def _assign_households(households, boundaries):
    """Enumerated household points tagged with the boundary polygon that contains them."""
    joined = gpd.sjoin(
        households, boundaries[[CODE, "geometry"]], how="inner", predicate="within")
    return joined.rename(columns={CODE: "boundary_code"}).drop(columns="index_right")


def _clusters_in(group, metric_crs):
    """DBSCAN one district's building centroids; yield (label, footprint, centroid, count).

    Footprint is the concave hull (alpha shape) of the cluster's buildings so a sprawling
    settlement follows its real outline instead of a convex hull that bridges gaps and swallows
    neighbouring clusters.
    """
    labels = DBSCAN(
        eps=config.DBSCAN_EPS_METERS, min_samples=config.DBSCAN_MIN_SAMPLES
    ).fit_predict(group[["mx", "my"]].to_numpy())
    group = group.assign(cluster=labels)
    for label, members in group[group["cluster"] >= 0].groupby("cluster"):
        points = gpd.GeoSeries(
            gpd.points_from_xy(members["mx"], members["my"]), crs=metric_crs).union_all()
        hull = concave_hull(points, ratio=config.CONCAVE_HULL_RATIO)
        yield label, hull, points.centroid, len(members)


def _uncovered_buildings(buildings, homes):
    """Buildings with no registered household within INVISIBLE_BUFFER_METERS.

    One STRtree-indexed nearest query over the whole building set (not a per-building loop),
    so this scales as O(n log m) and clustering afterwards runs on the smaller uncovered subset.
    """
    if homes.empty:
        return buildings
    pts = gpd.GeoDataFrame(
        buildings, geometry=gpd.points_from_xy(buildings["mx"], buildings["my"]), crs=homes.crs)
    near = gpd.sjoin_nearest(pts[["geometry"]], homes[["geometry"]], distance_col="dist_m", how="left")
    near = near[~near.index.duplicated(keep="first")].reindex(buildings.index)
    return buildings[near["dist_m"].to_numpy() > config.INVISIBLE_BUFFER_METERS]


def _finalize(invisible, homes, metric_crs):
    """Attach nearest-household distance, centroid coordinates, and metadata; project to storage CRS."""
    if invisible.empty:
        empty = gpd.GeoDataFrame(
            pd.DataFrame(columns=TABLE_COLUMNS), geometry=[], crs=config.STORAGE_CRS)
        return empty.drop(columns="geometry"), empty

    df = invisible.copy()
    if homes.empty:
        df["nearest_boundary_code"] = None
        df["distance_to_nearest_km"] = pd.NA
    else:
        centroids = gpd.GeoDataFrame(
            df.drop(columns=["geometry", "centroid_m"]),
            geometry=df["centroid_m"].values, crs=metric_crs)
        nearest = gpd.sjoin_nearest(
            centroids, homes[["boundary_code", "geometry"]], how="left", distance_col="dist_m")
        nearest = nearest[~nearest.index.duplicated(keep="first")].reindex(df.index)
        df["nearest_boundary_code"] = nearest["boundary_code"]
        df["distance_to_nearest_km"] = (nearest["dist_m"] / 1000).round(3)

    centroid_ll = gpd.GeoSeries(df["centroid_m"].values, crs=metric_crs).to_crs(config.STORAGE_CRS)
    df["centroid_lon"] = centroid_ll.x.round(6).values
    df["centroid_lat"] = centroid_ll.y.round(6).values

    df["status"] = config.INVISIBLE_STATUS_INITIAL
    df["detected_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    df["campaign_id"] = config.CAMPAIGN_ID
    df["tenant_id"] = config.TENANT_ID

    df = df.sort_values("building_count", ascending=False).reset_index(drop=True)
    gdf = gpd.GeoDataFrame(
        df[TABLE_COLUMNS], geometry=df["geometry"].to_crs(config.STORAGE_CRS),
        crs=config.STORAGE_CRS)
    return df[TABLE_COLUMNS], gdf


def detect(households, iso3=None, units=None):
    """Building clusters with no enumerated household nearby.

    ``households`` is a GeoDataFrame of enumerated household points - the input the current
    aggregate workbook cannot supply. ``units`` defaults to the country's districts. Only
    units the households actually reach are examined: elsewhere every cluster would read as
    invisible simply because nobody enumerated there.
    """
    boundaries = units if units is not None else load_boundaries(iso3)
    metric_crs = resolve_metric_crs(boundaries)

    homes = _assign_households(households, boundaries).to_crs(metric_crs)
    reached = set(homes["boundary_code"])
    boundaries = boundaries[boundaries[CODE].isin(reached)]

    buildings = buildings_source.clip_to_boundaries(boundaries, iso3)
    centroids = gpd.GeoSeries(
        buildings["centroid"].values, crs=config.STORAGE_CRS).to_crs(metric_crs)
    buildings = buildings.assign(mx=centroids.x.values, my=centroids.y.values)
    buildings = buildings[buildings["boundary_code"].isin(reached)]

    # Coverage filter first: keep only buildings the register never reached, then cluster those.
    buildings = _uncovered_buildings(buildings.reset_index(drop=True), homes)

    records, hulls, centroids_m = [], [], []
    for code, group in buildings.groupby("boundary_code"):
        if len(group) < config.DBSCAN_MIN_SAMPLES:
            continue
        for label, hull, centroid, count in _clusters_in(group, metric_crs):
            records.append({
                "cluster_id": f"{code}-C{int(label):04d}",
                "parent_boundary_code": code,
                "building_count": int(count),
                "estimated_population": round(count * config.AVG_HOUSEHOLD_SIZE),
            })
            hulls.append(hull)
            centroids_m.append(centroid)

    if not records:
        return _finalize(
            gpd.GeoDataFrame(geometry=[], crs=metric_crs), homes, metric_crs)

    clusters = gpd.GeoDataFrame(records, geometry=hulls, crs=metric_crs)
    clusters["centroid_m"] = gpd.GeoSeries(centroids_m, crs=metric_crs)

    return _finalize(clusters, homes, metric_crs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("households",
                        help="point file of enumerated household locations (any format "
                             "geopandas reads). Not available from the aggregate workbook - "
                             "this feature is dormant until the country exports one.")
    parser.add_argument("--iso3", help="country ISO3 (default from PDI_ISO3 / config)")
    args = parser.parse_args()

    households = gpd.read_file(args.households).to_crs(config.STORAGE_CRS)
    table, gdf = detect(households, iso3=args.iso3)
    config.INVISIBLE_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    table.to_csv(config.INVISIBLE_SETTLEMENTS_CSV, index=False, encoding="utf-8-sig")
    gdf.to_file(config.INVISIBLE_SETTLEMENTS_GEOJSON, driver="GeoJSON")

    print(f"invisible settlements:    {len(table):>12,}")
    print(f"buildings in clusters:    {table['building_count'].sum():>12,}")
    print(f"estimated population:     {table['estimated_population'].sum():>12,}")
    if not table.empty:
        print("\ntop invisible settlements by building count:")
        for _, row in table.head(10).iterrows():
            print(f"  {row['cluster_id']:<22} {row['building_count']:>6,} bldg  "
                  f"~{row['estimated_population']:>6,} ppl  "
                  f"{row['distance_to_nearest_km']:>6} km to {row['nearest_boundary_code']}")
    print(f"\nWrote:\n  {config.INVISIBLE_SETTLEMENTS_CSV}\n  {config.INVISIBLE_SETTLEMENTS_GEOJSON}")


if __name__ == "__main__":
    main()
