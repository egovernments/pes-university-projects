"""District boundary reader: geoBoundaries ADM polygons for the selected country."""

import geopandas as gpd

import config
from sources import remote


def load_boundaries(iso3=None):
    """District polygons for ``iso3`` (default ``config.COUNTRY_ISO3``) in storage CRS."""
    path = remote.boundaries_geojson(iso3)
    boundaries = gpd.read_file(path)
    boundaries = boundaries[boundaries.geometry.geom_type.isin(["Polygon", "MultiPolygon"])]
    boundaries = boundaries.to_crs(config.STORAGE_CRS)
    return boundaries.reset_index(drop=True)


def main():
    boundaries = load_boundaries()
    code, name = config.BOUNDARY_CODE_FIELD, config.BOUNDARY_NAME_FIELD
    print(f"{config.COUNTRY_ISO3} districts: {len(boundaries):,}")
    for _, row in boundaries.head(10).iterrows():
        label = row[name] if name in boundaries.columns else ""
        print(f"  {row[code]:<24} {label}")


if __name__ == "__main__":
    main()
