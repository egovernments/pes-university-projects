import json

import pytest

import config
from sources import catchments

# The property the file happens to use for the area name...
SOURCE_FIELD = "facility_name"
# ...and the canonical column the loader normalises it into.
FACILITY = catchments.FACILITY


def _feature(geometry, properties):
    return {"type": "Feature", "geometry": geometry, "properties": properties}


def _polygon(x, y, size=0.01):
    return {"type": "Polygon", "coordinates": [[
        [x, y], [x + size, y], [x + size, y + size], [x, y + size], [x, y]]]}


def _upload(tmp_path, features):
    path = tmp_path / "boundaries.geojson"
    path.write_text(json.dumps({"type": "FeatureCollection", "features": features}),
                    encoding="utf-8")
    return path


@pytest.fixture
def boundaries(tmp_path):
    """Two catchments: one anchored on surveyed GPS, one on a guessed centroid."""
    return _upload(tmp_path, [
        _feature(_polygon(15.0, 12.0), {
            SOURCE_FIELD: "CS ABENA", "gps_source": "whatsapp", "anchor_source": "field_gps",
            "point_count": 413, "area_km2": 9.05, "warnings": ""}),
        _feature({"type": "Point", "coordinates": [15.005, 12.005]}, {
            SOURCE_FIELD: "CS ABENA", "gps_source": "whatsapp", "anchor_source": "field_gps"}),
        _feature(_polygon(15.1, 12.1), {
            SOURCE_FIELD: "CS CEPHAS", "gps_source": "centroid_fallback",
            "anchor_source": "household_centroid", "point_count": 3, "area_km2": 3.27,
            "warnings": "no whatsapp GPS collected"}),
        _feature({"type": "Point", "coordinates": [15.105, 12.105]}, {
            SOURCE_FIELD: "CS CEPHAS", "gps_source": "centroid_fallback",
            "anchor_source": "household_centroid"}),
    ])


def test_polygons_and_points_are_separated(boundaries):
    upload = catchments.load_boundary_upload(boundaries)
    assert len(upload.units) == 2
    assert len(upload.anchors) == 2
    assert upload.facility_names == ["CS ABENA", "CS CEPHAS"]


def test_facility_name_becomes_the_boundary_code(boundaries):
    # The rest of the pipeline joins on the boundary code, so it has to be the name the
    # enumeration sheet also uses.
    upload = catchments.load_boundary_upload(boundaries)
    assert list(upload.units[config.BOUNDARY_CODE_FIELD]) == ["CS ABENA", "CS CEPHAS"]
    assert upload.units["is_catchment"].all()


def test_anchor_coordinates_are_carried_onto_the_cell(boundaries):
    upload = catchments.load_boundary_upload(boundaries).units.set_index(FACILITY)
    assert upload.loc["CS ABENA", "center_lon"] == pytest.approx(15.005)
    assert upload.loc["CS ABENA", "center_lat"] == pytest.approx(12.005)


def test_inferred_anchors_are_flagged(boundaries):
    units = catchments.load_boundary_upload(boundaries).units.set_index(FACILITY)
    assert bool(units.loc["CS ABENA", "anchor_is_estimated"]) is False
    assert bool(units.loc["CS CEPHAS", "anchor_is_estimated"]) is True


def test_cells_cut_from_too_few_points_are_flagged(boundaries):
    units = catchments.load_boundary_upload(boundaries).units.set_index(FACILITY)
    # point_count is the sample the cell was drawn from; 3 points cannot describe a
    # real catchment, so the shape is not to be trusted.
    assert bool(units.loc["CS CEPHAS", "low_sample"]) is True
    assert bool(units.loc["CS ABENA", "low_sample"]) is False


def test_review_flags_explain_every_reason(boundaries):
    flags = catchments.load_boundary_upload(boundaries).review_flags()
    assert "CS ABENA" not in flags
    reasons = " ".join(flags["CS CEPHAS"])
    assert "anchor inferred" in reasons
    assert "cut from 3 points" in reasons
    assert "no whatsapp GPS collected" in reasons


@pytest.mark.parametrize("property_name", ["facility_name", "name", "nom", "shapeName"])
def test_any_known_name_property_is_detected(tmp_path, property_name):
    # Uploads from different countries and tools name this property differently.
    path = _upload(tmp_path, [
        _feature(_polygon(15.0, 12.0), {property_name: "Area One"}),
        _feature(_polygon(15.1, 12.1), {property_name: "Area Two"}),
    ])
    units = catchments.load_boundary_upload(path).units
    assert list(units[FACILITY]) == ["Area One", "Area Two"]


def test_unknown_property_name_falls_back_to_the_unique_column(tmp_path):
    # Nothing recognisable, but one property takes a distinct value per polygon - which is
    # what identifying the areas means.
    path = _upload(tmp_path, [
        _feature(_polygon(15.0, 12.0), {"zone_libelle_2026": "Zone A", "region": "North"}),
        _feature(_polygon(15.1, 12.1), {"zone_libelle_2026": "Zone B", "region": "North"}),
    ])
    units = catchments.load_boundary_upload(path).units
    assert list(units[FACILITY]) == ["Zone A", "Zone B"]


def test_explicit_configuration_overrides_detection(tmp_path, monkeypatch):
    monkeypatch.setattr(config, "BOUNDARY_UPLOAD_NAME_FIELD", "region")
    path = _upload(tmp_path, [
        _feature(_polygon(15.0, 12.0), {"name": "Area One", "region": "North"}),
        _feature(_polygon(15.1, 12.1), {"name": "Area Two", "region": "South"}),
    ])
    units = catchments.load_boundary_upload(path).units
    assert list(units[FACILITY]) == ["North", "South"]


def test_rejects_a_file_where_no_property_identifies_the_areas(tmp_path):
    path = _upload(tmp_path, [
        _feature(_polygon(15.0, 12.0), {"region": "North"}),
        _feature(_polygon(15.1, 12.1), {"region": "North"}),
    ])
    with pytest.raises(ValueError, match="could not tell which property names each area"):
        catchments.load_boundary_upload(path)


def test_a_file_with_no_anchor_points_still_works(tmp_path):
    # Many exports carry only the areas.
    path = _upload(tmp_path, [_feature(_polygon(15.0, 12.0), {"name": "Area One"})])
    upload = catchments.load_boundary_upload(path)
    assert len(upload.units) == 1
    assert upload.anchors.empty


def test_rejects_a_file_with_no_polygons(tmp_path):
    path = _upload(tmp_path, [
        _feature({"type": "Point", "coordinates": [15.0, 12.0]}, {SOURCE_FIELD: "CS ABENA"})])
    with pytest.raises(ValueError, match="no polygon features"):
        catchments.load_boundary_upload(path)


# --- Country match -------------------------------------------------------------
#
# An upload from the wrong country cannot be caught downstream: every raster and
# building source is sampled by coordinate, so the run would return a complete
# dashboard describing somewhere else entirely.

def _districts(x, y, size=1.0):
    """A one-district stand-in country covering a degree square at (x, y)."""
    import geopandas as gpd
    from shapely.geometry import box

    return gpd.GeoDataFrame(
        {config.BOUNDARY_CODE_FIELD: ["D1"]},
        geometry=[box(x, y, x + size, y + size)], crs=config.STORAGE_CRS)


def test_upload_inside_the_country_is_accepted(boundaries):
    upload = catchments.load_boundary_upload(boundaries)
    catchments.assert_in_country(upload.units, _districts(15.0, 12.0), "TCD")


def test_upload_from_another_country_is_rejected(boundaries):
    upload = catchments.load_boundary_upload(boundaries)
    # Same cells, a country square that sits nowhere near them.
    with pytest.raises(ValueError, match="not in KEN"):
        catchments.assert_in_country(upload.units, _districts(36.0, -2.0), "KEN")


def test_rejection_names_where_the_file_actually_is(boundaries):
    upload = catchments.load_boundary_upload(boundaries)
    with pytest.raises(ValueError, match=r"centre is at 12\.\d+, 15\.\d+"):
        catchments.assert_in_country(upload.units, _districts(36.0, -2.0), "KEN")


def test_a_cell_straddling_the_border_is_still_accepted(boundaries):
    # Legitimate border areas overhang the national outline; the threshold has to
    # tolerate that without tolerating a whole file from elsewhere.
    upload = catchments.load_boundary_upload(boundaries)
    catchments.assert_in_country(upload.units, _districts(15.05, 12.05, size=1.0), "TCD")


def test_service_points_are_checked_too(tmp_path):
    import geopandas as gpd

    points = gpd.GeoDataFrame(
        geometry=gpd.points_from_xy([36.8, 36.9], [-1.3, -1.2]), crs=config.STORAGE_CRS)
    with pytest.raises(ValueError, match="not in TCD"):
        catchments.assert_in_country(points, _districts(15.0, 12.0), "TCD",
                                     label="sheet's service points")
