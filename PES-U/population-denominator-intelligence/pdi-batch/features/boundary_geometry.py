"""The PDI geometry sheet: where users give every HCM boundary code a location.

HCM boundaries carry no shapes or coordinates, so before a WorldPop run each boundary code
needs either a latitude/longitude point (typed into the sheet) or a polygon (a GeoJSON or
zipped shapefile whose features carry a ``boundaryCode`` property).

``generate`` writes the sheet from the hierarchy rows the API resolved; ``parse`` checks an
uploaded sheet and shape file against the hierarchy and returns the accepted locations, the
rejected rows with reasons, and a copy of the sheet with an error column.
"""

import argparse
import json
from pathlib import Path

import geopandas as gpd
from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Protection
from shapely import make_valid
from shapely.geometry import MultiPolygon, Point, Polygon, mapping

import config
from sources.boundaries import load_boundaries

CODE = "boundaryCode"
SHEET_TITLE = "Boundary Locations"
CODE_HEADER = "Boundary Code"
LAT_HEADER = "Latitude"
LON_HEADER = "Longitude"
ERROR_HEADER = "Error"
HEADERS = [CODE_HEADER, "Boundary Type", "Level", "Name", "Parent Path", LAT_HEADER, LON_HEADER]
LOCKED_FILL = PatternFill("solid", fgColor="D9D9D9")
INPUT_FILL = PatternFill("solid", fgColor="FFF2CC")
SHAPE_CODE_KEYS = ("boundarycode", "boundaryco", "code")
PARENT_TOLERANCE_DEG = config.UPLOAD_COUNTRY_BUFFER_DEG


def generate(rows, out_path):
    """Write the fill-in sheet: locked boundary columns, editable latitude/longitude."""
    book = Workbook()
    sheet = book.active
    sheet.title = SHEET_TITLE
    sheet.append(HEADERS)
    for cell in sheet[1]:
        cell.font = Font(bold=True)
    for row in rows:
        sheet.append([row[CODE], row.get("boundaryType"), row.get("level"), row.get("name"),
                      row.get("parentPath"), row.get("latitude"), row.get("longitude")])

    editable = {HEADERS.index(LAT_HEADER) + 1, HEADERS.index(LON_HEADER) + 1}
    for row in sheet.iter_rows(min_row=2):
        for cell in row:
            if cell.column in editable:
                cell.protection = Protection(locked=False)
                cell.fill = INPUT_FILL
            else:
                cell.fill = LOCKED_FILL
    for column, width in zip("ABCDEFG", (28, 16, 8, 28, 48, 14, 14)):
        sheet.column_dimensions[column].width = width
    sheet.freeze_panes = "B2"
    sheet.protection.sheet = True
    sheet.protection.formatColumns = False
    book.save(out_path)


def _country_outline(iso3):
    districts = load_boundaries(iso3)
    return districts.geometry.union_all().buffer(config.UPLOAD_COUNTRY_BUFFER_DEG)


def _polygonal(geometry):
    """The polygon part of a geometry after repair, or None when nothing polygonal is left."""
    if geometry is None or geometry.is_empty:
        return None
    repaired = make_valid(geometry) if not geometry.is_valid else geometry
    if isinstance(repaired, (Polygon, MultiPolygon)):
        return None if repaired.is_empty else repaired
    parts = [part for part in getattr(repaired, "geoms", [])
             if isinstance(part, (Polygon, MultiPolygon)) and not part.is_empty]
    if not parts:
        return None
    polygons = [poly for part in parts
                for poly in (part.geoms if isinstance(part, MultiPolygon) else [part])]
    return MultiPolygon(polygons)


def _shape_code_column(frame):
    for column in frame.columns:
        if column.lower() in SHAPE_CODE_KEYS:
            return column
    raise ValueError("the shape file has no boundaryCode property on its features")


def _read_shapes(path):
    path = Path(path)
    source = f"zip://{path}" if path.suffix.lower() == ".zip" else str(path)
    frame = gpd.read_file(source)
    if frame.crs is None:
        frame = frame.set_crs(config.STORAGE_CRS)
    frame = frame.to_crs(config.STORAGE_CRS)
    column = _shape_code_column(frame)
    frame[CODE] = frame[column].astype(str).str.strip()
    return frame[[CODE, "geometry"]]


def _header_index(sheet):
    headers = {str(cell.value).strip().lower(): cell.column for cell in sheet[1] if cell.value}
    missing = [h for h in (CODE_HEADER, LAT_HEADER, LON_HEADER) if h.lower() not in headers]
    if missing:
        raise ValueError(f"the sheet is missing the column(s): {', '.join(missing)}")
    return headers


def _number(value):
    if value is None or (isinstance(value, str) and not value.strip()):
        return None
    return float(str(value).strip().replace(",", "."))


class _Checker:
    """Applies the upload rules and collects accepted locations and rejections."""

    def __init__(self, tree, stored_polygons, outline):
        self.parents = {node[CODE]: node.get("parentCode") for node in tree}
        self.polygons = dict(stored_polygons)
        self.outline = outline
        self.accepted = {}
        self.rejected = []
        self.warnings = []

    def reject(self, code, reason, row=None):
        self.rejected.append({CODE: code, "row": row, "reason": reason})

    def known(self, code, row=None):
        if code not in self.parents:
            self.reject(code, "boundary code is not in this hierarchy", row)
            return False
        return True

    def inside_parent(self, code, geometry):
        parent = self.parents.get(code)
        polygon = self.polygons.get(parent)
        if polygon is None:
            return True
        probe = geometry if isinstance(geometry, Point) else geometry.representative_point()
        return polygon.buffer(PARENT_TOLERANCE_DEG).contains(probe)

    def add_shape(self, code, geometry):
        if not self.known(code):
            return
        if code in self.accepted:
            self.reject(code, "more than one shape for this boundary code")
            return
        polygon = _polygonal(geometry)
        if polygon is None:
            self.reject(code, "shape is not a valid Polygon or MultiPolygon")
            return
        if not self.outline.intersects(polygon):
            self.reject(code, "shape lies outside the country")
            return
        self.polygons[code] = polygon
        self.accepted[code] = {"geometryType": "POLYGON", "source": "SHAPE_UPLOAD",
                               "geometry": polygon}

    def check_shape_parents(self):
        for code, entry in list(self.accepted.items()):
            if entry["geometryType"] == "POLYGON" and not self.inside_parent(code, entry["geometry"]):
                del self.accepted[code]
                self.polygons.pop(code, None)
                self.reject(code, "shape does not fall inside its parent boundary")

    def add_point(self, code, lat, lon, row):
        if not self.known(code, row):
            return
        if code in self.accepted:
            if self.accepted[code]["source"] == "SHEET_POINT":
                self.reject(code, "boundary code appears more than once in the sheet", row)
            else:
                self.reject(code, "this upload also has a shape for this boundary; "
                                  "the shape is used", row)
            return
        if code in self.polygons:
            self.reject(code, "a shape is already stored for this boundary; "
                              "upload a new shape to replace it", row)
            return
        if not (-90 <= lat <= 90) or not (-180 <= lon <= 180):
            self.reject(code, "latitude must be -90..90 and longitude -180..180", row)
            return
        point = Point(lon, lat)
        if not self.outline.contains(point):
            swapped = Point(lat, lon)
            hint = " (latitude and longitude look swapped)" if self.outline.contains(swapped) else ""
            self.reject(code, f"point is outside the country{hint}", row)
            return
        if not self.inside_parent(code, point):
            self.reject(code, "point does not fall inside its parent boundary", row)
            return
        self.accepted[code] = {"geometryType": "POINT", "source": "SHEET_POINT",
                               "geometry": point, "row": row}

    def flag_duplicate_points(self):
        seen = {}
        for code, entry in self.accepted.items():
            if entry["geometryType"] != "POINT":
                continue
            key = (round(entry["geometry"].x, 6), round(entry["geometry"].y, 6))
            seen.setdefault(key, []).append(code)
        for codes in seen.values():
            if len(codes) > 1:
                self.warnings.append({"boundaryCodes": codes,
                                      "reason": "these boundaries share the same point"})


def _sheet_points(sheet_path):
    """``(code, latitude, longitude, row number, error)`` for every filled sheet row."""
    book = load_workbook(sheet_path, data_only=True)
    sheet = book.worksheets[0]
    headers = _header_index(sheet)
    code_col = headers[CODE_HEADER.lower()]
    lat_col = headers[LAT_HEADER.lower()]
    lon_col = headers[LON_HEADER.lower()]
    for row in sheet.iter_rows(min_row=2):
        cells = {cell.column: cell.value for cell in row}
        code = cells.get(code_col)
        lat, lon = cells.get(lat_col), cells.get(lon_col)
        if code is None or str(code).strip() == "":
            continue
        if (lat is None or str(lat).strip() == "") and (lon is None or str(lon).strip() == ""):
            continue
        row_number = row[0].row
        try:
            yield str(code).strip(), _number(lat), _number(lon), row_number, None
        except ValueError:
            yield str(code).strip(), None, None, row_number, "latitude and longitude must be numbers"


def _annotate(sheet_path, rejected, out_path):
    """Copy of the uploaded sheet with an error column naming each rejected row's reason."""
    book = load_workbook(sheet_path)
    sheet = book.worksheets[0]
    sheet.protection.sheet = False
    error_col = sheet.max_column + 1
    for cell in sheet[1]:
        if str(cell.value).strip() == ERROR_HEADER:
            error_col = cell.column
    sheet.cell(row=1, column=error_col, value=ERROR_HEADER).font = Font(bold=True)
    by_row = {entry["row"]: entry["reason"] for entry in rejected if entry.get("row")}
    by_code = {entry[CODE]: entry["reason"] for entry in rejected if not entry.get("row")}
    code_col = _header_index(sheet)[CODE_HEADER.lower()]
    for row in sheet.iter_rows(min_row=2):
        code = row[code_col - 1].value
        reason = by_row.get(row[0].row) or by_code.get(str(code).strip() if code else None)
        sheet.cell(row=row[0].row, column=error_col, value=reason)
    book.save(out_path)


def parse(tree, iso3, sheet_path=None, shapes_path=None, stored_polygons=None, annotated_path=None):
    """Check an upload and return accepted locations, rejections and warnings."""
    if not sheet_path and not shapes_path:
        raise ValueError("upload a filled geometry sheet, a shape file, or both")
    checker = _Checker(tree, stored_polygons or {}, _country_outline(iso3))

    if shapes_path:
        for _, shape in _read_shapes(shapes_path).iterrows():
            checker.add_shape(shape[CODE], shape.geometry)
        checker.check_shape_parents()

    if sheet_path:
        for code, lat, lon, row, error in _sheet_points(sheet_path):
            if error:
                checker.reject(code, error, row)
            elif lat is None or lon is None:
                checker.reject(code, "both latitude and longitude are required", row)
            else:
                checker.add_point(code, lat, lon, row)
        if annotated_path:
            _annotate(sheet_path, checker.rejected, annotated_path)

    checker.flag_duplicate_points()
    accepted = [{CODE: code, "geometryType": entry["geometryType"], "source": entry["source"],
                 "geometry": mapping(entry["geometry"])}
                for code, entry in checker.accepted.items()]
    return {"accepted": accepted, "rejected": checker.rejected, "warnings": checker.warnings}


def _stored_polygons(path):
    if not path:
        return {}
    frame = gpd.read_file(path)
    if frame.empty:
        return {}
    frame = frame[frame.geometry.geom_type.isin(["Polygon", "MultiPolygon"])]
    return dict(zip(frame[CODE], frame.geometry))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    gen = commands.add_parser("generate", help="write the fill-in geometry sheet")
    gen.add_argument("--rows", required=True, help="JSON list of boundary rows")
    gen.add_argument("--out", required=True, help="xlsx path")

    chk = commands.add_parser("parse", help="check an uploaded sheet and shape file")
    chk.add_argument("--tree", required=True, help="JSON list of {boundaryCode, parentCode}")
    chk.add_argument("--iso3", required=True)
    chk.add_argument("--sheet", help="filled geometry sheet")
    chk.add_argument("--shapes", help="GeoJSON or zipped shapefile keyed by boundaryCode")
    chk.add_argument("--stored", help="GeoJSON of polygons already stored for the hierarchy")
    chk.add_argument("--annotated", help="where to write the sheet with an error column")
    chk.add_argument("--out", required=True, help="result JSON path")

    args = parser.parse_args()
    if args.command == "generate":
        rows = json.loads(Path(args.rows).read_text(encoding="utf-8"))
        generate(rows, args.out)
        print(f"geometry sheet: {len(rows)} boundaries", flush=True)
        return

    tree = json.loads(Path(args.tree).read_text(encoding="utf-8"))
    result = parse(tree, args.iso3.upper(), args.sheet, args.shapes,
                   _stored_polygons(args.stored), args.annotated)
    Path(args.out).write_text(json.dumps(result), encoding="utf-8")
    print(f"geometry upload: {len(result['accepted'])} accepted, "
          f"{len(result['rejected'])} rejected", flush=True)


if __name__ == "__main__":
    main()
