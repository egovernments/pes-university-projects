
import re
from pathlib import Path

import requests

import config

_TIMEOUT = 120
_HEADERS = {"User-Agent": "PDI/1.0 (population-denominator-intelligence)"}
_YEAR_TOKEN = re.compile(r"(.+?)_(\d{4})_")


def _iso3(iso3):
    return (iso3 or config.COUNTRY_ISO3).upper()


def _cache_dir(iso3):
    path = config.CACHE_DIR / iso3
    path.mkdir(parents=True, exist_ok=True)
    return path


def _download(url, dest, *, name=None, base=0.0, span=100.0):
    """Stream ``url`` to ``dest`` once; a present non-empty file is reused.

    While streaming, emit ``PROGRESS <pct> …`` lines that the service parses into
    a download progress bar. ``base``/``span`` let a caller map this single file
    onto a slice of an overall bar (e.g. file 3 of 37), so the percentage climbs
    smoothly across a multi-file WorldPop fetch instead of resetting each file.
    """
    dest = Path(dest)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    label = name or dest.name
    print(f"downloading {label}", flush=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    with requests.get(url, stream=True, timeout=300, headers=_HEADERS) as resp:
        resp.raise_for_status()
        total = int(resp.headers.get("Content-Length") or 0)
        done = 0
        last_pct = -1
        with open(tmp, "wb") as handle:
            for chunk in resp.iter_content(1 << 20):
                handle.write(chunk)
                done += len(chunk)
                if not total:
                    continue
                pct = min(100, int(base + span * done / total))
                if pct >= last_pct + 1:
                    last_pct = pct
                    print(f"PROGRESS {pct} {label} "
                          f"({done / 1e6:.0f}/{total / 1e6:.0f} MB)", flush=True)
    tmp.replace(dest)
    return dest


# --- Boundaries: geoBoundaries ADM2, falling back to coarser levels -----------

def boundaries_geojson(iso3=None):
    """Local path to the district-boundary GeoJSON for ``iso3`` (downloaded once).

    Small countries have no ADM2 layer, so we fall back to ADM1 then ADM0.
    """
    iso3 = _iso3(iso3)
    dest = _cache_dir(iso3) / f"{iso3}_boundaries.geojson"
    if dest.exists() and dest.stat().st_size > 0:
        return dest

    tried = []
    for adm in config.GEOBOUNDARIES_ADM_LEVELS:
        url = config.GEOBOUNDARIES_API.format(iso3=iso3, adm=adm)
        resp = requests.get(url, timeout=_TIMEOUT, headers=_HEADERS)
        if resp.status_code == 404:
            tried.append(adm)
            continue
        resp.raise_for_status()
        return _download(resp.json()["gjDownloadURL"], dest, name="boundaries")
    raise RuntimeError(
        f"geoBoundaries has no ADM boundary for {iso3} (tried {', '.join(tried)})")


# --- WorldPop: constrained 100m age/sex + total rasters -----------------------

def _worldpop_record(alias, iso3, year):
    url = f"{config.WORLDPOP_REST}/{alias}?iso3={iso3}"
    data = requests.get(url, timeout=_TIMEOUT, headers=_HEADERS).json().get("data", [])
    if not data:
        raise RuntimeError(f"WorldPop '{alias}' has no data for {iso3}")
    exact = [rec for rec in data if str(rec.get("popyear")) == str(year)]
    if exact:
        return exact[0]
    # Fall back to the nearest available year within the release.
    return min(data, key=lambda rec: abs(int(rec.get("popyear", 0)) - year))


def _token_from_filename(name, iso3):
    """``tcd_t_00_2026_CN_100m_R2025A_v1.tif`` -> ``t_00`` (None if it doesn't fit)."""
    prefix = f"{iso3.lower()}_"
    if not name.lower().startswith(prefix):
        return None
    match = _YEAR_TOKEN.match(name[len(prefix):])
    return match.group(1) if match else None


def worldpop_age_rasters(iso3=None, year=None):
    """``{token: local raster path}`` for every age/sex band, downloaded on demand."""
    iso3 = _iso3(iso3)
    year = year or config.TARGET_YEAR
    record = _worldpop_record(config.WORLDPOP_AGE_ALIAS, iso3, year)
    entries = []
    for url in record["files"]:
        if not url.lower().endswith(".tif"):
            continue
        name = url.rsplit("/", 1)[-1]
        token = _token_from_filename(name, iso3)
        if token:
            entries.append((token, url, name))

    out = {}
    count = len(entries)
    for index, (token, url, name) in enumerate(entries):
        out[token] = _download(
            url, _cache_dir(iso3) / "worldpop" / name,
            name=f"WorldPop age raster {index + 1}/{count}",
            base=index * 100 / count, span=100 / count)
    return out


def worldpop_total_raster(iso3=None, year=None):
    """Local path to the single national total-population raster for ``iso3``."""
    iso3 = _iso3(iso3)
    year = year or config.TARGET_YEAR
    record = _worldpop_record(config.WORLDPOP_TOTAL_ALIAS, iso3, year)
    tifs = [url for url in record["files"] if url.lower().endswith(".tif")]
    if not tifs:
        raise RuntimeError(f"WorldPop total raster missing for {iso3} {year}")
    url = tifs[0]
    return _download(url, _cache_dir(iso3) / "worldpop" / url.rsplit("/", 1)[-1],
                     name="WorldPop total raster")


# --- Buildings: VIDA per-country GeoParquet, cached on disk -------------------

def vida_parquet_url(iso3=None):
    return config.VIDA_PARQUET_URL.format(iso3=_iso3(iso3))


def vida_parquet(iso3=None):
    """Local path to the national building footprints (downloaded once).

    Cached like the WorldPop rasters rather than streamed per read: the file is
    ~600 MB and a single run reads it up to four times (country estimate,
    invisible settlements, and both passes when a microplan sheet is uploaded),
    which dominated the runtime on a slow link.
    """
    iso3 = _iso3(iso3)
    return _download(vida_parquet_url(iso3),
                     _cache_dir(iso3) / f"{iso3}_buildings.parquet",
                     name="Open Buildings footprints")
