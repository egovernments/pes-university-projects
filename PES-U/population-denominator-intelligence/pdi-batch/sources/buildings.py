import geopandas as gpd

import config
from sources import remote
from sources.boundaries import load_boundaries

_OUTPUT_COLUMNS = ["geometry", "centroid", "area_m2", "confidence", "bf_source", "boundary_code"]
_LAST_READ = {}


def _confidence_ok(buildings):
    """Keep Google footprints above the threshold, and all null-confidence (Microsoft/OSM) ones."""
    confidence = buildings["confidence"]
    return confidence.isna() | (confidence >= config.BUILDING_CONFIDENCE_THRESHOLD)


def _read_in_bbox(bounds, iso3=None):
    """Footprints whose bbox intersects ``bounds`` (minx, miny, maxx, maxy)."""
    bbox = tuple(round(value, 6) for value in bounds)
    key = ((iso3 or config.COUNTRY_ISO3).upper(), bbox)
    if key in _LAST_READ:
        print("reusing Open Buildings footprints", flush=True)
        return _LAST_READ[key]

    path = remote.vida_parquet(iso3)
    _LAST_READ.clear()
    print("reading Open Buildings footprints", flush=True)
    buildings = gpd.read_parquet(path, bbox=bbox).to_crs(config.STORAGE_CRS)
    _LAST_READ[key] = buildings
    return buildings


def clip_to_boundaries(boundaries, iso3=None):
    """Buildings whose representative point lies within a boundary, tagged with boundary_code."""
    boundaries = boundaries.to_crs(config.STORAGE_CRS)

    buildings = _read_in_bbox(boundaries.total_bounds, iso3)
    buildings = buildings[_confidence_ok(buildings)]
    buildings = buildings.rename(columns={"area_in_meters": "area_m2"})
    buildings["centroid"] = buildings.geometry.representative_point()

    code = config.BOUNDARY_CODE_FIELD
    clipped = gpd.sjoin(
        buildings.set_geometry("centroid"),
        boundaries[[code, "geometry"]],
        how="inner",
        predicate="within",
    )
    clipped = clipped.rename(columns={code: "boundary_code"})
    return clipped.set_geometry("geometry")[_OUTPUT_COLUMNS]


def main():
    boundaries = load_boundaries()
    buildings = clip_to_boundaries(boundaries)
    print(f"buildings within {config.COUNTRY_ISO3} boundaries (confidence >= "
          f"{config.BUILDING_CONFIDENCE_THRESHOLD} or null): {len(buildings):,}")

    print("by source:")
    for source, count in buildings["bf_source"].value_counts().items():
        print(f"  {source:<12} {count:>8,}")

    counts = buildings.groupby("boundary_code").size().sort_index()
    missing = set(boundaries[config.BOUNDARY_CODE_FIELD]) - set(counts.index)
    if missing:
        print("no buildings assigned:", ", ".join(sorted(missing)))


if __name__ == "__main__":
    main()
