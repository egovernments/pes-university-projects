"""WorldPop population per HCM boundary code - the engine side of the boundary-population run.

The API resolves each boundary's location and writes it to ``--units``: a polygon is used
as it is; a point is turned into an area here by cutting Voronoi cells among the points of
the same boundary level that share a clip region. The clip region is the nearest ancestor
that has a polygon (``clipCode``, looked up in ``--clips``), or, when no ancestor has one,
the geoBoundaries district containing the point.

The command is stateless: it writes one entry per computable code to ``--out`` and leaves
parent roll-ups, persistence and activation to the API.
"""

import argparse
import json
from pathlib import Path

import geopandas as gpd

import config
from sources import worldpop
from sources.boundaries import load_boundaries
from sources.catchments import _assign_to_districts, _voronoi_cells

CODE = "boundaryCode"
ZONAL_UPLOAD = "ZONAL_UPLOAD"
POINT_VORONOI = "POINT_VORONOI"


def _read_features(path):
    frame = gpd.read_file(path) if path else None
    if frame is None or frame.empty:
        return gpd.GeoDataFrame({CODE: []}, geometry=[], crs=config.STORAGE_CRS)
    if frame.crs is None:
        frame = frame.set_crs(config.STORAGE_CRS)
    return frame.to_crs(config.STORAGE_CRS)


def _point_groups(points, clips, iso3):
    """Yield ``(points, clip_geometry)`` for every set of points tessellated together."""
    clipped = points[points["clipCode"].notna() & points["clipCode"].isin(clips.index)]
    for _, group in clipped.groupby(["clipCode", "boundaryType"], dropna=False):
        yield group, clips.loc[group["clipCode"].iloc[0]]

    unclipped = points.drop(clipped.index)
    if unclipped.empty:
        return
    districts = load_boundaries(iso3)
    assigned, _ = _assign_to_districts(unclipped, districts)
    by_code = districts.set_index(config.BOUNDARY_CODE_FIELD)
    for (district_code, _), group in assigned.groupby(["district_code", "boundaryType"], dropna=False):
        yield group.drop(columns="district_code"), by_code.loc[district_code, "geometry"]


def _voronoi_units(points, clips, iso3):
    """One area per point code; points sharing a location share their cell equally."""
    units, shares, skipped = [], {}, []
    for group, clip in _point_groups(points, clips, iso3):
        distinct = group.drop_duplicates(subset="geometry")
        cells = _voronoi_cells(distinct.reset_index(drop=True), clip)
        cell_by_location = {point.wkb: cell for point, cell in zip(distinct.geometry, cells)}
        for location, members in group.groupby(group.geometry.apply(lambda geom: geom.wkb)):
            cell = cell_by_location.get(location)
            for code in members[CODE]:
                if cell is None or cell.is_empty:
                    skipped.append({CODE: code, "reason": "point lies outside its clip region"})
                    continue
                units.append({CODE: code, "method": POINT_VORONOI, "geometry": cell})
                shares[code] = len(members)
    placed = {unit[CODE] for unit in units} | {entry[CODE] for entry in skipped}
    for code in points.loc[~points[CODE].isin(placed), CODE]:
        skipped.append({CODE: code, "reason": "point is outside every district of the country"})
    return units, shares, skipped


def build_units(units_path, clips_path, iso3):
    """The areas to sum WorldPop over, one per boundary code, plus codes that got none."""
    features = _read_features(units_path)
    clips = _read_features(clips_path).set_index(CODE).geometry if clips_path else gpd.GeoSeries()

    polygons = features[features.geometry.geom_type.isin(["Polygon", "MultiPolygon"])]
    rows = [{CODE: row[CODE], "method": ZONAL_UPLOAD, "geometry": row.geometry}
            for _, row in polygons.iterrows()]

    points = features[features.geometry.geom_type == "Point"].copy()
    if "clipCode" not in points.columns:
        points["clipCode"] = None
    if "boundaryType" not in points.columns:
        points["boundaryType"] = None
    shares, skipped = {}, []
    if not points.empty:
        voronoi, shares, skipped = _voronoi_units(points, clips, iso3)
        rows.extend(voronoi)

    units = gpd.GeoDataFrame(rows, columns=[CODE, "method", "geometry"],
                             geometry="geometry", crs=config.STORAGE_CRS)
    return units, shares, skipped


def compute(units_path, iso3, year=None, groups=None, clips_path=None):
    """Population per boundary code for ``groups`` (default: total) as a result document."""
    groups = groups or ["total"]
    unknown = sorted(set(groups) - config.TARGET_GROUPS.keys())
    if unknown:
        raise ValueError(f"unknown population groups: {', '.join(unknown)}")

    units, shares, skipped = build_units(units_path, clips_path, iso3)
    results = []
    if not units.empty:
        areas = units.to_crs(config.AREA_CRS).geometry.area / 1_000_000
        sums = worldpop.compute_zonal(units, iso3=iso3, year=year, label="boundary population",
                                      code_field=CODE, groups=groups)
        for (_, unit), area, row in zip(units.iterrows(), areas, sums):
            share = shares.get(unit[CODE], 1)
            results.append({
                CODE: unit[CODE],
                "method": unit["method"],
                "areaKm2": round(float(area) / share, 4),
                "population": {group: int(round(row[group] / share)) for group in groups},
            })

    return {
        "iso3": iso3,
        "year": year or config.TARGET_YEAR,
        "worldpopRelease": config.WORLDPOP_VERSION,
        "groups": groups,
        "results": results,
        "skipped": skipped,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--units", required=True, help="GeoJSON of boundary locations")
    parser.add_argument("--clips", help="GeoJSON of ancestor polygons that points are clipped to")
    parser.add_argument("--iso3", required=True)
    parser.add_argument("--year", type=int)
    parser.add_argument("--groups", default="total", help="comma-separated target groups")
    parser.add_argument("--out", required=True, help="result JSON path")
    args = parser.parse_args()

    groups = [group.strip() for group in args.groups.split(",") if group.strip()]
    document = compute(args.units, args.iso3.upper(), args.year, groups, args.clips)
    Path(args.out).write_text(json.dumps(document), encoding="utf-8")
    print(f"boundary population: {len(document['results'])} computed, "
          f"{len(document['skipped'])} skipped", flush=True)


if __name__ == "__main__":
    main()
