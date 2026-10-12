import numpy as np
import pytest
import rasterio
from rasterio.transform import from_origin
from shapely.geometry import box

import config
from sources import worldpop


def _write_raster(path, data, nodata=-1.0):
    transform = from_origin(0, data.shape[0], 1, 1)
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        height=data.shape[0],
        width=data.shape[1],
        count=1,
        dtype="float32",
        crs=config.STORAGE_CRS,
        transform=transform,
        nodata=nodata,
    ) as dataset:
        dataset.write(data.astype("float32"), 1)


def test_zonal_sums_covers_all_pixels(tmp_path):
    raster = tmp_path / "pop.tif"
    _write_raster(raster, np.array([[1, 2], [3, 4]]))
    covering = box(-0.5, -0.5, 2.5, 2.5)

    assert worldpop._zonal_sums([covering], raster) == [10.0]


def test_zonal_sums_excludes_nodata(tmp_path):
    raster = tmp_path / "pop.tif"
    _write_raster(raster, np.array([[1, 2], [3, -1]]), nodata=-1.0)
    covering = box(-0.5, -0.5, 2.5, 2.5)

    assert worldpop._zonal_sums([covering], raster) == [6.0]


def test_zonal_sums_partial_overlap(tmp_path):
    raster = tmp_path / "pop.tif"
    _write_raster(raster, np.array([[1, 2], [3, 4]]))
    top_row = box(-0.5, 1.1, 2.5, 2.5)

    assert worldpop._zonal_sums([top_row], raster) == pytest.approx([2.7], abs=0.05)


def test_zonal_sums_subpixel_polygon_is_proportional(tmp_path):
    """A polygon smaller than one pixel gets a proportional share, never 0.

    Regression for the N'Djamena dense Voronoi cells: plain centre-based zonal
    stats returned 0 (no pixel centre inside) or a whole pixel; coverage
    weighting must return roughly value * covered_fraction.
    """
    raster = tmp_path / "pop.tif"
    _write_raster(raster, np.array([[100.0]]))  # single pixel, x 0..1, y 0..1
    quarter = box(0.0, 0.0, 0.5, 0.5)  # covers 25% of the pixel

    (result,) = worldpop._zonal_sums([quarter], raster)
    assert result == pytest.approx(25.0, abs=1.0)


def test_group_rows_sums_band_tokens():
    band_sums = {"total": [5.0, 50.0], "t_00": [1.0, 10.0], "t_01": [2.0, 20.0]}
    groups = {"total": ["total"], "under5": ["t_00", "t_01"]}

    rows = worldpop._group_rows(["A", "B"], band_sums, groups)

    assert rows == [
        {"boundary_code": "A", "total": 5.0, "under5": 3.0},
        {"boundary_code": "B", "total": 50.0, "under5": 30.0},
    ]


def test_token_from_filename_parses_bands():
    from sources import remote

    assert remote._token_from_filename("tcd_t_00_2026_CN_100m_R2025A_v1.tif", "TCD") == "t_00"
    assert remote._token_from_filename("tcd_T_F_2026_CN_100m_R2025A_v1.tif", "TCD") == "T_F"
    assert remote._token_from_filename("ken_m_45_2026_CN_100m_R2025A_v1.tif", "KEN") == "m_45"
    assert remote._token_from_filename("other_file.tif", "TCD") is None
