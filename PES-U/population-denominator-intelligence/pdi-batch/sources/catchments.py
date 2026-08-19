"""Analysis units: the polygons every population figure is computed over.

Three sources, in order of precedence:

* an uploaded boundary geojson, whose polygons are the catchment cells directly (mode C);
* an uploaded sheet of facility coordinates, from which Voronoi cells are cut inside each
  district (the earlier path, kept for callers that only have points);
* the country's geoBoundaries districts, when nothing is uploaded.

The country districts remain the base layer in every case - uploaded catchments are an
overlay on them, not a replacement.
"""

from dataclasses import dataclass

import geopandas as gpd
import pandas as pd
from shapely import voronoi_polygons
from shapely.geometry import MultiPoint

import config
from geo import resolve_metric_crs
from sources.boundaries import load_boundaries

CODE = config.BOUNDARY_CODE_FIELD
NAME = config.BOUNDARY_NAME_FIELD
# Canonical column the upload's area name is normalised into, whatever the file called it.
FACILITY = "area_name"


def load_catchment_points(sheet_path):
    frame = pd.read_excel(sheet_path, sheet_name=config.SHEET_SHEET_NAME)
    lat, lon, code = config.SHEET_LAT_COLUMN, config.SHEET_LON_COLUMN, config.SHEET_CODE_COLUMN
    missing = [c for c in (lat, lon, code) if c not in frame.columns]
    if missing:
        print(f"sheet has no service points (missing columns {missing}); "
              "skipping the catchment overlay", flush=True)
        return gpd.GeoDataFrame({CODE: []}, geometry=[], crs=config.STORAGE_CRS)
    frame = frame.dropna(subset=[lat, lon, code])
    points = gpd.GeoDataFrame(
        {CODE: frame[code].astype(str)},
        geometry=gpd.points_from_xy(frame[lon], frame[lat]),
        crs=config.STORAGE_CRS,
    )
    return points.reset_index(drop=True)


@dataclass
class BoundaryUpload:
    """An uploaded boundary geojson, split into catchment cells and facility anchors.

    ``units`` carries one row per catchment polygon, keyed by facility name, with the
    file's own provenance fields preserved plus two review flags:

    ``anchor_is_estimated``
        the facility position was inferred (a household centroid) rather than surveyed.
    ``low_sample``
        the cell was cut from very few points, so its shape is not dependable.

    Both are reported, never silently corrected - a catchment drawn around a guessed
    anchor still produces a population figure, and the operator has to know which ones are.
    """

    units: gpd.GeoDataFrame
    anchors: gpd.GeoDataFrame

    @property
    def facility_names(self):
        return list(self.units[FACILITY])

    def review_flags(self):
        """``{facility: [reasons]}`` for every cell needing a human look before it is trusted."""
        flags = {}
        for _, row in self.units.iterrows():
            reasons = []
            if row.get("anchor_is_estimated"):
                reasons.append(f"anchor inferred ({row.get('anchor_source') or 'unknown'})")
            if row.get("low_sample"):
                reasons.append(f"cell cut from {int(row['point_count'])} points")
            warning = row.get("warnings")
            if warning and not pd.isna(warning) and str(warning).strip():
                reasons.append(str(warning).strip())
            if reasons:
                flags[row[FACILITY]] = reasons
        return flags


def _upload_provenance(frame):
    """Copy through the file's provenance properties, filling absent ones with NA."""
    return {field: (frame[field] if field in frame.columns else pd.NA)
            for field in config.BOUNDARY_UPLOAD_PROVENANCE}


def resolve_name_field(frame, polygons):
    """Which property names each area, for an upload from any source.

    Tried in order: an explicitly configured field, then the known candidate names, then
    whichever string property takes a distinct value on every polygon. That last fallback
    is what lets an unfamiliar export work without configuration - a column that is unique
    across areas is, by definition, the one identifying them.
    """
    configured = config.BOUNDARY_UPLOAD_NAME_FIELD
    if configured:
        if configured not in frame.columns:
            raise ValueError(
                f"configured boundary name field '{configured}' is not a property of this "
                f"file; available: {', '.join(map(str, frame.columns))}")
        return configured

    for candidate in config.BOUNDARY_UPLOAD_NAME_CANDIDATES:
        if candidate in frame.columns and polygons[candidate].notna().all():
            return candidate

    ignored = {"geometry", *config.BOUNDARY_UPLOAD_PROVENANCE}
    for column in polygons.columns:
        if column in ignored or polygons[column].isna().any():
            continue
        values = polygons[column].astype(str)
        if values.nunique() == len(values) and not pd.api.types.is_numeric_dtype(polygons[column]):
            return column

    raise ValueError(
        "could not tell which property names each area - no known name property and no "
        "column unique across the polygons. Set PDI_BOUNDARY_NAME_FIELD to name it. "
        f"Available properties: {', '.join(map(str, frame.columns))}")


def load_boundary_upload(path):
    """Read an uploaded boundary geojson into catchment cells plus facility anchors.

    The file mixes both geometry types in one FeatureCollection: ``Polygon`` features are
    the catchments, ``Point`` features are the facility locations. They are matched on
    ``facility_name``, which also becomes the boundary code the rest of the pipeline
    joins on - the same role the sheet's Service Boundary Code plays in the Voronoi path.
    """
    frame = gpd.read_file(path)
    if frame.empty:
        raise ValueError(f"{path}: the file contains no features")
    frame = frame.to_crs(config.STORAGE_CRS)

    polygons = frame[frame.geometry.geom_type.isin(["Polygon", "MultiPolygon"])]
    points = frame[frame.geometry.geom_type == "Point"]
    if polygons.empty:
        raise ValueError(f"{path}: no polygon features found - nothing to compute over")

    name_field = resolve_name_field(frame, polygons)

    units = gpd.GeoDataFrame(
        {FACILITY: polygons[name_field].astype(str), **_upload_provenance(polygons)},
        geometry=polygons.geometry.values, crs=config.STORAGE_CRS).reset_index(drop=True)
    units[CODE] = units[FACILITY]
    units[NAME] = units[FACILITY]
    units["is_catchment"] = True
    units.attrs["name_field"] = name_field

    # Points are optional: many exports carry only the areas.
    anchor_names = points[name_field].astype(str) if name_field in points.columns else []
    anchors = gpd.GeoDataFrame(
        {FACILITY: anchor_names, **_upload_provenance(points)},
        geometry=points.geometry.values, crs=config.STORAGE_CRS).reset_index(drop=True)
    anchors[CODE] = anchors[FACILITY]

    if not anchors.empty:
        position = anchors.set_index(FACILITY).geometry
        units["center_lon"] = units[FACILITY].map(position.x)
        units["center_lat"] = units[FACILITY].map(position.y)
        # Anchor provenance lives on the point feature; carry it onto the cell so a single
        # row tells the whole story of how that catchment was arrived at.
        for field in ("gps_source", "anchor_source"):
            if field in anchors.columns:
                units[field] = units[FACILITY].map(anchors.set_index(FACILITY)[field])

    trusted = config.BOUNDARY_TRUSTED_ANCHOR_SOURCES
    units["anchor_is_estimated"] = ~units.get(
        "anchor_source", pd.Series(pd.NA, index=units.index)).isin(trusted)
    units["low_sample"] = pd.to_numeric(
        units.get("point_count"), errors="coerce") < config.CATCHMENT_LOW_SAMPLE_POINTS

    return BoundaryUpload(units=units, anchors=anchors)


def _assign_to_districts(points, districts):
    """Tag each point with the district that contains it, snapping strays to the nearest."""
    inside = gpd.sjoin(points, districts[[CODE, "geometry"]].rename(columns={CODE: "district_code"}),
                       how="left", predicate="within").drop(columns="index_right")
    stray = inside["district_code"].isna()
    if stray.any():
        metric = resolve_metric_crs(districts)
        near = gpd.sjoin_nearest(
            points[stray].to_crs(metric),
            districts[[CODE, "geometry"]].rename(columns={CODE: "district_code"}).to_crs(metric),
            distance_col="dist_m", max_distance=config.CATCHMENT_SNAP_TOLERANCE_M)
        near = near[~near.index.duplicated(keep="first")]
        inside.loc[near.index, "district_code"] = near["district_code"]
    dropped = int(inside["district_code"].isna().sum())
    return inside.dropna(subset=["district_code"]), dropped


def _voronoi_cells(points, district_geom):
    """Voronoi polygons of ``points`` clipped to ``district_geom``, aligned back to each point."""
    if len(points) == 1:
        return [district_geom]
    regions = voronoi_polygons(MultiPoint(list(points.geometry)), extend_to=district_geom)
    cells = gpd.GeoDataFrame(
        geometry=[cell.intersection(district_geom) for cell in regions.geoms],
        crs=config.STORAGE_CRS)
    cells = cells[~cells.geometry.is_empty].reset_index(drop=True)
    matched = gpd.sjoin(points, cells, how="left", predicate="within")
    matched = matched[~matched.index.duplicated(keep="first")]
    return [None if pd.isna(i) else cells.geometry.iloc[int(i)] for i in matched["index_right"]]


UNIT_COLUMNS = [CODE, NAME, "is_catchment", "geometry"]


def _districts_outside(units, districts):
    """Districts no uploaded catchment reaches into, kept whole as the country base layer.

    An upload covers one campaign area, not the country. The rest of the country still has
    to appear in the report - as districts, flagged ``is_catchment=False`` - or its
    population silently leaves the denominator.
    """
    touched = gpd.sjoin(
        districts[[CODE, "geometry"]], units[["geometry"]], how="inner", predicate="intersects")
    rest = districts[~districts[CODE].isin(set(touched[CODE]))].copy()
    rest[NAME] = rest[NAME] if NAME in rest.columns else rest[CODE]
    rest["is_catchment"] = False
    return rest[UNIT_COLUMNS]


def _inside_share(frame, country):
    """Share of ``frame`` lying inside ``country``: by area for polygons, by count for points.

    The area maths runs on the merged shapely geometry rather than the GeoSeries: it is a
    ratio of two areas in the same CRS, so the lon/lat distortion cancels, and going
    through shapely keeps geopandas' "geographic CRS" warning out of the engine log, which
    is parsed for progress and error reporting.
    """
    shape = frame.geometry.union_all()
    if shape.area > 0:
        return shape.intersection(country).area / shape.area
    within = frame.geometry.within(country)
    return float(within.mean()) if len(within) else 0.0


def assert_in_country(frame, districts, iso3, label="upload"):
    """Reject geometry that does not lie in the country it was submitted against.

    Nothing downstream can detect this on its own. WorldPop, buildings and settlements are
    all sampled by coordinate, so a Kenyan file computed against Chad returns a complete
    dashboard of real numbers describing the wrong place. The only honest moment to catch
    it is before any of that runs.
    """
    if frame.empty or districts.empty:
        return
    country = districts.geometry.union_all().buffer(config.UPLOAD_COUNTRY_BUFFER_DEG)
    share = _inside_share(frame, country)
    if share >= config.UPLOAD_MIN_COUNTRY_OVERLAP:
        return

    centre = frame.geometry.union_all().centroid
    raise ValueError(
        f"This {label} is not in {iso3}: {share:.0%} of it falls inside the country, and its "
        f"centre is at {centre.y:.4f}, {centre.x:.4f}. Either the wrong country is selected "
        f"or the wrong file was uploaded - check the pair and run again.")


def build_analysis_units(iso3=None, sheet_path=None, boundaries_path=None):
    """The polygons to compute over: uploaded catchments, Voronoi cells, or districts.

    Returns a GeoDataFrame of ``(boundary_code, name, is_catchment, geometry)`` plus, for an
    uploaded geojson, its provenance columns. An uploaded ``boundaries_path`` wins over
    ``sheet_path``: its cells are the ones the country drew, so there is nothing to infer.
    Catchment cells are keyed by facility name; whole districts by the geoBoundaries shapeID.
    """
    if boundaries_path:
        upload = load_boundary_upload(boundaries_path)
        districts = load_boundaries(iso3)
        assert_in_country(upload.units, districts, iso3 or config.COUNTRY_ISO3,
                          label="boundary file")
        extra = [column for column in upload.units.columns if column not in UNIT_COLUMNS]
        rest = _districts_outside(upload.units, districts)
        return gpd.GeoDataFrame(
            pd.concat([upload.units[UNIT_COLUMNS + extra], rest], ignore_index=True),
            geometry="geometry", crs=config.STORAGE_CRS)

    districts = load_boundaries(iso3)
    if not sheet_path:
        units = districts[[CODE, "geometry"]].copy()
        units[NAME] = districts[NAME] if NAME in districts.columns else units[CODE]
        units["is_catchment"] = False
        return units[[CODE, NAME, "is_catchment", "geometry"]].reset_index(drop=True)

    points = load_catchment_points(sheet_path)
    assert_in_country(points, districts, iso3 or config.COUNTRY_ISO3,
                      label="sheet's service points")
    points, _ = _assign_to_districts(points, districts)

    rows = []
    covered = set()
    districts_by_code = districts.set_index(CODE)
    for district_code, group in points.groupby("district_code"):
        covered.add(district_code)
        district_geom = districts_by_code.loc[district_code, "geometry"]
        cells = _voronoi_cells(group, district_geom)
        for (_, point), cell in zip(group.iterrows(), cells):
            rows.append({CODE: point[CODE], NAME: point[CODE],
                         "is_catchment": True, "geometry": cell or district_geom})

    for _, district in districts[~districts[CODE].isin(covered)].iterrows():
        rows.append({CODE: district[CODE], NAME: district.get(NAME, district[CODE]),
                     "is_catchment": False, "geometry": district.geometry})

    return gpd.GeoDataFrame(rows, geometry="geometry", crs=config.STORAGE_CRS).reset_index(drop=True)


def main():
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--boundaries", help="uploaded catchment geojson (takes precedence)")
    parser.add_argument("--sheet", help="facility coordinate sheet; cuts Voronoi cells instead")
    parser.add_argument("--iso3", help="country ISO3 (default from PDI_ISO3 / config)")
    args = parser.parse_args()

    units = build_analysis_units(
        iso3=args.iso3, sheet_path=args.sheet, boundaries_path=args.boundaries)
    catchments = int(units["is_catchment"].sum())
    print(f"{args.iso3 or config.COUNTRY_ISO3} analysis units: {len(units):,} "
          f"({catchments} catchment cells, {len(units) - catchments} whole districts)")

    if args.boundaries:
        flags = load_boundary_upload(args.boundaries).review_flags()
        print(f"\ncells needing review: {len(flags)} of {catchments}")
        for name, reasons in flags.items():
            print(f"  {name:<28} {'; '.join(reasons)}")


if __name__ == "__main__":
    main()
