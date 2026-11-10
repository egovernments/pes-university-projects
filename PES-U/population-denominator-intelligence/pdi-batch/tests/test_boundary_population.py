import json

import numpy as np
import pytest
import rasterio
from rasterio.transform import from_origin
from shapely.geometry import box, mapping

import config
from features import boundary_population
from sources import worldpop


def _write_raster(path, data):
    with rasterio.open(path, "w", driver="GTiff", height=data.shape[0], width=data.shape[1],
                       count=1, dtype="float32", crs=config.STORAGE_CRS,
                       transform=from_origin(0, data.shape[0], 1, 1), nodata=-1.0) as dataset:
        dataset.write(data.astype("float32"), 1)


def _collection(path, features):
    path.write_text(json.dumps({"type": "FeatureCollection", "features": [
        {"type": "Feature", "geometry": geometry, "properties": properties}
        for geometry, properties in features]}), encoding="utf-8")
    return path


def _point(x, y):
    return {"type": "Point", "coordinates": [x, y]}


@pytest.fixture
def uniform_raster(tmp_path, monkeypatch):
    raster = tmp_path / "total.tif"
    _write_raster(raster, np.full((4, 4), 10.0))
    monkeypatch.setattr(worldpop, "_resolve_rasters", lambda tokens, iso3, year: {"total": raster})
    return raster


def test_polygon_units_are_summed_as_they_are(tmp_path, uniform_raster):
    units = _collection(tmp_path / "units.geojson", [
        (mapping(box(0, 0, 2, 2)), {"boundaryCode": "D1", "boundaryType": "District"})])

    result = boundary_population.compute(units, "TST")

    assert result["results"] == [{"boundaryCode": "D1", "method": "ZONAL_UPLOAD",
                                  "areaKm2": pytest.approx(49_000, rel=0.02),
                                  "population": {"total": 40}}]


def test_points_tessellate_their_clip_polygon(tmp_path, uniform_raster):
    units = _collection(tmp_path / "units.geojson", [
        (_point(1, 2), {"boundaryCode": "V1", "boundaryType": "Village", "clipCode": "D1"}),
        (_point(3, 2), {"boundaryCode": "V2", "boundaryType": "Village", "clipCode": "D1"})])
    clips = _collection(tmp_path / "clips.geojson", [
        (mapping(box(0, 0, 4, 4)), {"boundaryCode": "D1"})])

    result = boundary_population.compute(units, "TST", clips_path=clips)

    totals = {row["boundaryCode"]: row["population"]["total"] for row in result["results"]}
    assert totals == {"V1": 80, "V2": 80}
    assert {row["method"] for row in result["results"]} == {"POINT_VORONOI"}


def test_points_sharing_a_location_split_their_cell(tmp_path, uniform_raster):
    units = _collection(tmp_path / "units.geojson", [
        (_point(1, 2), {"boundaryCode": "V1", "boundaryType": "Village", "clipCode": "D1"}),
        (_point(1, 2), {"boundaryCode": "V2", "boundaryType": "Village", "clipCode": "D1"}),
        (_point(3, 2), {"boundaryCode": "V3", "boundaryType": "Village", "clipCode": "D1"})])
    clips = _collection(tmp_path / "clips.geojson", [
        (mapping(box(0, 0, 4, 4)), {"boundaryCode": "D1"})])

    result = boundary_population.compute(units, "TST", clips_path=clips)

    totals = {row["boundaryCode"]: row["population"]["total"] for row in result["results"]}
    assert totals == {"V1": 40, "V2": 40, "V3": 80}


def test_point_outside_its_clip_is_skipped(tmp_path, uniform_raster):
    units = _collection(tmp_path / "units.geojson", [
        (_point(1, 1), {"boundaryCode": "V1", "boundaryType": "Village", "clipCode": "D1"}),
        (_point(3.5, 3.5), {"boundaryCode": "V2", "boundaryType": "Village", "clipCode": "D1"})])
    clips = _collection(tmp_path / "clips.geojson", [
        (mapping(box(0, 0, 2, 2)), {"boundaryCode": "D1"})])

    result = boundary_population.compute(units, "TST", clips_path=clips)

    assert [row["boundaryCode"] for row in result["results"]] == ["V1"]
    assert [entry["boundaryCode"] for entry in result["skipped"]] == ["V2"]


def test_unknown_group_is_rejected(tmp_path, uniform_raster):
    units = _collection(tmp_path / "units.geojson", [])

    with pytest.raises(ValueError, match="unknown population groups"):
        boundary_population.compute(units, "TST", groups=["nonsense"])


def test_compute_zonal_reads_the_given_code_field(tmp_path, uniform_raster):
    import geopandas as gpd

    frame = gpd.GeoDataFrame({"boundaryCode": ["A"]}, geometry=[box(0, 0, 1, 1)],
                             crs=config.STORAGE_CRS)

    rows = worldpop.compute_zonal(frame, iso3="TST", code_field="boundaryCode", groups=["total"])

    assert rows == [{"boundary_code": "A", "total": pytest.approx(10.0)}]
