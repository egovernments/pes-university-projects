#WorldPop zonal statistics computed from on-demand, per-country GeoTIFFs

import math

import numpy as np
import rasterio
from rasterio.features import rasterize
from rasterio.transform import from_bounds as transform_from_bounds
from rasterio.windows import Window, bounds as window_bounds, from_bounds

import config
from sources import remote
from sources.boundaries import load_boundaries

# Sub-pixel supersampling factor for coverage-weighted extraction.
COVERAGE_SUBSAMPLE = 10


def _coverage_weighted_sum(geom, src, k):
    """Sum of raster values under a geometry, weighted by each pixel's covered fraction."""
    w0 = from_bounds(*geom.bounds, src.transform)
    col0 = max(0, math.floor(w0.col_off))
    row0 = max(0, math.floor(w0.row_off))
    col1 = min(src.width, max(math.ceil(w0.col_off + w0.width), col0 + 1))
    row1 = min(src.height, max(math.ceil(w0.row_off + w0.height), row0 + 1))
    if col1 <= col0 or row1 <= row0:
        return 0.0

    win = Window(col0, row0, col1 - col0, row1 - row0)
    data = src.read(1, window=win).astype("float64")
    if src.nodata is not None:
        data = np.where(data == src.nodata, 0.0, data)
    data = np.where(np.isnan(data), 0.0, data)

    height, width = data.shape
    kk = max(1, min(k, 1500 // max(height, width)))
    fine = transform_from_bounds(*window_bounds(win, src.transform), width * kk, height * kk)
    cover = rasterize([(geom, 1)], out_shape=(height * kk, width * kk),
                      transform=fine, fill=0, dtype="uint8")
    fraction = np.reshape(cover, (height, kk, width, kk)).mean(axis=(1, 3))
    return float((data * fraction).sum())


def _zonal_sums(geometries, raster_path, k=COVERAGE_SUBSAMPLE):
    with rasterio.open(raster_path) as src:
        return [_coverage_weighted_sum(geom, src, k) for geom in geometries]


def _group_rows(codes, band_sums, groups):
    rows = []
    for index, code in enumerate(codes):
        row = {"boundary_code": code}
        for name, tokens in groups.items():
            row[name] = sum(band_sums[token][index] for token in tokens)
        rows.append(row)
    return rows


def _resolve_rasters(tokens, iso3, year):
    """Map every required band token to a local raster path (fetched on demand)."""
    rasters = remote.worldpop_age_rasters(iso3, year)
    if "total" in tokens:
        rasters["total"] = remote.worldpop_total_raster(iso3, year)
    missing = tokens - rasters.keys()
    if missing:
        raise FileNotFoundError(f"WorldPop bands unavailable: {sorted(missing)}")
    return rasters


def compute_zonal(boundaries, iso3=None, year=None, label="population"):
    """Per-boundary population for every configured target group, keyed by boundary_code.

    ``label`` names the pass in the progress log; a run with a microplan sheet does
    this twice (whole country, then the catchment cells).
    """
    codes = boundaries[config.BOUNDARY_CODE_FIELD].tolist()
    tokens = {token for tokens in config.TARGET_GROUPS.values() for token in tokens}
    print(f"fetching WorldPop rasters ({len(tokens)} bands)", flush=True)
    rasters = _resolve_rasters(tokens, iso3, year)
    print(f"computing {label} over {len(codes)} boundaries", flush=True)

    # One band at a time, reporting as we go: this pass is minutes long on a national
    # run, and without progress the caller cannot tell it apart from a hung job.
    band_sums = {}
    ordered = sorted(tokens)
    for index, token in enumerate(ordered):
        band_sums[token] = _zonal_sums(boundaries.geometry, rasters[token])
        print(f"PROGRESS {(index + 1) * 100 // len(ordered)} {label}: "
              f"band {index + 1}/{len(ordered)} over {len(codes)} boundaries", flush=True)
    return _group_rows(codes, band_sums, config.TARGET_GROUPS)


def main():
    boundaries = load_boundaries()
    print(f"Per-boundary population ({len(config.TARGET_GROUPS)} target groups) "
          f"for {config.COUNTRY_ISO3} {config.TARGET_YEAR}")
    rows = sorted(compute_zonal(boundaries), key=lambda row: row["boundary_code"])
    for row in rows:
        print(f"{row['boundary_code']:<24} total={row['total']:>10,.0f} "
              f"under5={row['under5']:>9,.0f}")

    footprint = sum(row["total"] for row in rows)
    print(f"\n{'footprint':<24} total={footprint:>10,.0f} (sum over loaded boundaries)")


if __name__ == "__main__":
    main()
