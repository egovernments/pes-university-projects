import json

import pytest
from openpyxl import load_workbook
from shapely.geometry import box, mapping

from features import boundary_geometry

TREE = [
    {"boundaryCode": "COUNTRY", "parentCode": None},
    {"boundaryCode": "D1", "parentCode": "COUNTRY"},
    {"boundaryCode": "V1", "parentCode": "D1"},
    {"boundaryCode": "V2", "parentCode": "D1"},
    {"boundaryCode": "V3", "parentCode": "D1"},
]


@pytest.fixture(autouse=True)
def country(monkeypatch):
    monkeypatch.setattr(boundary_geometry, "_country_outline", lambda iso3: box(10, 0, 20, 30))


def _sheet(tmp_path, points):
    rows = [{"boundaryCode": code, "boundaryType": "Village", "level": 3, "name": code,
             "parentPath": "COUNTRY / D1"} for code in ("V1", "V2", "V3")]
    path = tmp_path / "geometry.xlsx"
    boundary_geometry.generate(rows, path)
    book = load_workbook(path)
    sheet = book.worksheets[0]
    for row in sheet.iter_rows(min_row=2):
        if row[0].value in points:
            row[5].value, row[6].value = points[row[0].value]
    book.save(path)
    return path


def _shapes(tmp_path, features):
    path = tmp_path / "shapes.geojson"
    path.write_text(json.dumps({"type": "FeatureCollection", "features": [
        {"type": "Feature", "geometry": mapping(geometry), "properties": {"boundaryCode": code}}
        for code, geometry in features]}), encoding="utf-8")
    return path


def test_generated_sheet_locks_everything_but_the_coordinates(tmp_path):
    path = _sheet(tmp_path, {})
    sheet = load_workbook(path).worksheets[0]

    assert [cell.value for cell in sheet[1]] == boundary_geometry.HEADERS
    assert sheet.protection.sheet
    assert sheet["A2"].protection.locked
    assert not sheet["F2"].protection.locked and not sheet["G2"].protection.locked


def test_valid_points_are_accepted_and_blank_rows_ignored(tmp_path):
    sheet = _sheet(tmp_path, {"V1": (12, 15), "V2": (13, 16)})

    result = boundary_geometry.parse(TREE, "TST", sheet_path=sheet)

    assert sorted(entry["boundaryCode"] for entry in result["accepted"]) == ["V1", "V2"]
    assert result["rejected"] == []


def test_swapped_coordinates_are_rejected_with_a_hint(tmp_path):
    sheet = _sheet(tmp_path, {"V1": (15, 25)})

    result = boundary_geometry.parse(TREE, "TST", sheet_path=sheet)

    assert result["accepted"] == []
    assert "swapped" in result["rejected"][0]["reason"]


def test_point_outside_parent_polygon_is_rejected(tmp_path):
    sheet = _sheet(tmp_path, {"V1": (12, 15), "V2": (25, 18)})
    shapes = _shapes(tmp_path, [("D1", box(14, 10, 16, 14))])

    result = boundary_geometry.parse(TREE, "TST", sheet_path=sheet, shapes_path=shapes)

    accepted = {entry["boundaryCode"]: entry["geometryType"] for entry in result["accepted"]}
    assert accepted == {"D1": "POLYGON", "V1": "POINT"}
    assert result["rejected"] == [{"boundaryCode": "V2", "row": 3,
                                   "reason": "point does not fall inside its parent boundary"}]


def test_unknown_code_and_duplicate_points(tmp_path):
    sheet = _sheet(tmp_path, {"V1": (12, 15), "V2": (12, 15)})
    shapes = _shapes(tmp_path, [("NOPE", box(14, 10, 16, 14))])

    result = boundary_geometry.parse(TREE, "TST", sheet_path=sheet, shapes_path=shapes)

    assert result["rejected"][0] == {"boundaryCode": "NOPE", "row": None,
                                     "reason": "boundary code is not in this hierarchy"}
    assert result["warnings"] == [{"boundaryCodes": ["V1", "V2"],
                                   "reason": "these boundaries share the same point"}]


def test_annotated_sheet_carries_the_error_column(tmp_path):
    sheet = _sheet(tmp_path, {"V1": ("abc", 15)})
    annotated = tmp_path / "errors.xlsx"

    boundary_geometry.parse(TREE, "TST", sheet_path=sheet, annotated_path=annotated)

    rows = list(load_workbook(annotated).worksheets[0].values)
    assert rows[0][-1] == "Error"
    assert rows[1][-1] == "latitude and longitude must be numbers"
    assert rows[2][-1] is None


def test_point_does_not_replace_a_stored_shape(tmp_path):
    sheet = _sheet(tmp_path, {"V1": (12, 15)})

    result = boundary_geometry.parse(TREE, "TST", sheet_path=sheet,
                                     stored_polygons={"V1": box(14, 11, 16, 13)})

    assert result["accepted"] == []
    assert "a shape is already stored" in result["rejected"][0]["reason"]


def test_point_and_shape_for_one_code_in_one_upload_keeps_the_shape(tmp_path):
    sheet = _sheet(tmp_path, {"V1": (12, 15)})
    shapes = _shapes(tmp_path, [("V1", box(14, 11, 16, 13))])

    result = boundary_geometry.parse(TREE, "TST", sheet_path=sheet, shapes_path=shapes)

    assert [(entry["boundaryCode"], entry["geometryType"]) for entry in result["accepted"]] == [
        ("V1", "POLYGON")]
    assert result["rejected"] == [{"boundaryCode": "V1", "row": 2, "reason":
                                   "this upload also has a shape for this boundary; the shape is used"}]


def test_upload_needs_a_sheet_or_shapes():
    with pytest.raises(ValueError, match="upload a filled geometry sheet"):
        boundary_geometry.parse(TREE, "TST")
