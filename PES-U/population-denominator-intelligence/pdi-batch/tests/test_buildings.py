import geopandas as gpd
import numpy as np
import pandas as pd
from shapely.geometry import box

import config
from sources import buildings


def _square(x, y, s=0.0005):
    """A small square footprint centred near (x, y)."""
    return box(x - s, y - s, x + s, y + s)


def test_confidence_ok_keeps_high_and_null():
    df = pd.DataFrame({"confidence": [0.90, 0.50, np.nan, 0.71]})
    keep = buildings._confidence_ok(df)
    # high google, low google (drop), null microsoft/osm (keep), high google
    assert list(keep) == [True, False, True, True]


def test_clip_assigns_boundary_and_drops_outside(tmp_path, monkeypatch):
    boundaries = gpd.GeoDataFrame(
        {config.BOUNDARY_CODE_FIELD: ["B_A", "B_B"]},
        geometry=[box(0, 0, 1, 1), box(2, 0, 3, 1)],
        crs=config.STORAGE_CRS,
    )
    footprints = gpd.GeoDataFrame(
        {
            "area_in_meters": [10, 10, 10, 10],
            "confidence": [0.9, np.nan, 0.9, 0.4],   # google, osm(null), google, low-google
            "bf_source": ["google", "osm", "microsoft", "google"],
        },
        geometry=[_square(0.5, 0.5), _square(0.6, 0.5), _square(2.5, 0.5), _square(0.7, 0.5)],
        crs=config.STORAGE_CRS,
    )
    monkeypatch.setattr(buildings, "_read_in_bbox", lambda bounds, iso3=None: footprints)

    clipped = buildings.clip_to_boundaries(boundaries)

    assert clipped.groupby("boundary_code").size().to_dict() == {"B_A": 2, "B_B": 1}
    assert {"area_m2", "centroid", "bf_source", "boundary_code"} <= set(clipped.columns)
    assert clipped.geometry.geom_type.eq("Polygon").all()
    assert set(clipped["bf_source"]) == {"google", "osm", "microsoft"}
