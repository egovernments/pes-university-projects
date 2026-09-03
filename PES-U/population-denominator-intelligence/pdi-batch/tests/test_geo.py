import geopandas as gpd
import pytest
from shapely.geometry import box

import config
import geo


@pytest.mark.parametrize(
    "longitude, latitude, expected",
    [
        (15.05, 12.13, 32633),   # N'Djamena, Chad
        (3.40, 6.50, 32631),     # Lagos, Nigeria
        (-74.0, 40.7, 32618),    # New York, northern
        (151.2, -33.9, 32756),   # Sydney, southern hemisphere
        (-180.0, 0.0, 32601),    # western edge
        (179.0, 0.0, 32660),     # eastern edge
    ],
)
def test_utm_epsg_for(longitude, latitude, expected):
    assert geo.utm_epsg_for(longitude, latitude) == expected


def test_resolve_metric_crs_auto_derives_utm(monkeypatch):
    monkeypatch.setattr(config, "METRIC_CRS", None)
    boundaries = gpd.GeoDataFrame(
        geometry=[box(15.0, 12.0, 15.2, 12.2)], crs=config.STORAGE_CRS
    )
    assert geo.resolve_metric_crs(boundaries) == "EPSG:32633"


def test_resolve_metric_crs_respects_override(monkeypatch):
    monkeypatch.setattr(config, "METRIC_CRS", "EPSG:3857")
    boundaries = gpd.GeoDataFrame(
        geometry=[box(15.0, 12.0, 15.2, 12.2)], crs=config.STORAGE_CRS
    )
    assert geo.resolve_metric_crs(boundaries) == "EPSG:3857"
