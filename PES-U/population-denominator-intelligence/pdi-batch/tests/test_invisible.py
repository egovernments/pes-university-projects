import geopandas as gpd
import pandas as pd
from shapely.geometry import Point

import config
from features import invisible


def _metric():
    return "EPSG:32633"


def _cluster_gdf(records, hulls, centroids, crs):
    gdf = gpd.GeoDataFrame(records, geometry=hulls, crs=crs)
    gdf["centroid_m"] = gpd.GeoSeries(centroids, crs=crs)
    return gdf


def test_clusters_in_groups_dense_buildings():
    # Two tight triplets 5 km apart -> two clusters; one lone building -> noise, dropped.
    crs = _metric()
    xs = [0, 30, 60, 5000, 5030, 5060, 20000]
    ys = [0, 30, 0, 0, 30, 0, 0]
    group = gpd.GeoDataFrame(
        {"mx": xs, "my": ys},
        geometry=gpd.points_from_xy(xs, ys), crs=crs,
    )
    clusters = list(invisible._clusters_in(group, crs))
    assert len(clusters) == 2
    assert sorted(count for _, _, _, count in clusters) == [3, 3]


def test_uncovered_buildings_drops_those_within_the_enumeration_buffer():
    crs = _metric()
    # Three buildings near a household (<=200 m), three ~5 km away (uncovered).
    xs = [0, 30, 60, 5000, 5030, 5060]
    ys = [0, 0, 0, 0, 0, 0]
    buildings = gpd.GeoDataFrame(
        {"mx": xs, "my": ys, "boundary_code": ["P"] * 6},
        geometry=gpd.points_from_xy(xs, ys), crs=crs,
    )
    homes = gpd.GeoDataFrame(
        {"boundary_code": ["X"]}, geometry=[Point(100, 0)], crs=crs)

    uncovered = invisible._uncovered_buildings(buildings, homes)
    assert sorted(uncovered["mx"]) == [5000, 5030, 5060]


def test_uncovered_buildings_keeps_all_when_no_households():
    crs = _metric()
    buildings = gpd.GeoDataFrame(
        {"mx": [0, 500], "my": [0, 0], "boundary_code": ["P", "P"]},
        geometry=gpd.points_from_xy([0, 500], [0, 0]), crs=crs,
    )
    empty = gpd.GeoDataFrame(geometry=[], crs=crs)
    assert len(invisible._uncovered_buildings(buildings, empty)) == 2


def test_finalize_reports_nearest_household_distance():
    crs = _metric()
    hull = Point(0, 0).buffer(50)
    clusters = _cluster_gdf(
        [{"cluster_id": "B", "parent_boundary_code": "P",
          "building_count": 4, "estimated_population": 22}],
        [hull], [Point(0, 0)], crs,
    )
    homes = gpd.GeoDataFrame(
        {"boundary_code": ["NEAR", "FAR"]},
        geometry=[Point(1000, 0), Point(9000, 0)], crs=crs,
    )

    table, gdf = invisible._finalize(clusters, homes, crs)
    row = table.iloc[0]
    assert row["nearest_boundary_code"] == "NEAR"
    assert row["distance_to_nearest_km"] == 1.0
    assert row["status"] == config.INVISIBLE_STATUS_INITIAL
    assert gdf.crs.to_string() == config.STORAGE_CRS
    assert list(table.columns) == invisible.TABLE_COLUMNS


def test_finalize_handles_no_invisible_settlements():
    crs = _metric()
    empty = gpd.GeoDataFrame(geometry=[], crs=crs)
    table, gdf = invisible._finalize(empty, gpd.GeoDataFrame(geometry=[], crs=crs), crs)
    assert table.empty
    assert list(table.columns) == invisible.TABLE_COLUMNS
    assert gdf.empty
