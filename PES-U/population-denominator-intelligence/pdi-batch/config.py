# Central configuration for the PDI batch pre-computation layer.

import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# --- Country selection  --------
COUNTRY_ISO3 = os.getenv("PDI_ISO3", "TCD").upper()
TARGET_YEAR = int(os.getenv("PDI_YEAR", "2026"))

CACHE_DIR = Path(os.getenv("PDI_CACHE_DIR", str(REPO_ROOT / ".cache")))

GEOBOUNDARIES_API = "https://www.geoboundaries.org/api/current/gbOpen/{iso3}/{adm}/"
GEOBOUNDARIES_ADM_LEVELS = ("ADM2", "ADM1", "ADM0")

WORLDPOP_REST = "https://hub.worldpop.org/rest/data"
WORLDPOP_AGE_ALIAS = "age_structures/G2_CN_Age_R25A_100m"
WORLDPOP_TOTAL_ALIAS = "pop/G2_CN_POP_R25A_100m"
WORLDPOP_ISO = COUNTRY_ISO3.lower() 

VIDA_PARQUET_URL = (
    "https://data.source.coop/vida/google-microsoft-osm-open-buildings"
    "/geoparquet/by_country/country_iso={iso3}/{iso3}.parquet"
)

BOUNDARY_CODE_FIELD = "shapeID"
BOUNDARY_NAME_FIELD = "shapeName"

AVG_HOUSEHOLD_SIZE = 5.4

SHEET_SHEET_NAME = 0                       # first sheet, or a sheet name
SHEET_LAT_COLUMN = "Latitude"
SHEET_LON_COLUMN = "Longitude"
SHEET_CODE_COLUMN = "Service Boundary Code"
# A point up to this far outside every district is snapped to the nearest; dropped beyond.
CATCHMENT_SNAP_TOLERANCE_M = 2000

# --- Area name resolution -----------------------------------------------------
# The uploaded geojson and spreadsheet join on a hand-typed area name. Leading
# facility-type words are dropped before matching (longest first, so "CENTRE DE SANTE"
# is tried before "CS"). Extend for other languages as needed; an unlisted prefix only
# means the name is matched in full, never a wrong match.
FACILITY_NAME_PREFIXES = (
    "CENTRE DE SANTE", "CENTRE SANTE", "HEALTH CENTRE", "HEALTH CENTER", "HEALTH POST",
    "HOPITAL", "HOSPITAL", "CLINIC", "CSI", "CHP", "CS", "HP", "HC",
)

# Optional deployment-specific overrides, mapping an alternate spelling onto the one the
# geojson uses. Empty by default: normalisation plus the reported near-miss suggestions
# handle the ordinary cases, and a wrong alias silently merges two real areas into one.
# Only add an entry with evidence that the two records are the same place.
FACILITY_ALIASES = {}

# --- Enumeration sheet ---------------------------------------------------------
# Column aliases, matched against accent-folded uppercase headers. The uploaded sheet
# is filled in by hand in French, so a header is matched when it *contains* an alias.
ENUMERATION_COLUMNS = {
    "facility_name": ("HEALTH CENTER", "HEALTH CENTRE", "CENTRE DE SANTE",
                      "FORMATION SANITAIRE", "FACILITY"),
    "official_population": ("POPULATION TOTALE", "TOTAL POPULATION"),
    "official_target": ("CIBLE", "TARGET"),
    "users_total": ("NOMBRE D UTILISATEURS", "NUMBER OF USERS"),
    "users_active": ("UTILISATEURS ACTIFS", "ACTIVE USERS"),
    "census_records": ("ENREGISTREMENTS DE RECENSEMENT", "CENSUS RECORDS"),
    "eligible_children": ("ENFANTS ADMISSIBLES", "ELIGIBLE CHILDREN"),
    "invited_members": ("MEMBRES INVITES", "INVITED MEMBERS"),
    "absent_households": ("MENAGES ABSENTS", "ABSENT HOUSEHOLDS"),
}
ENUMERATION_HEADER_SCAN_ROWS = 10
# A column is taken to hold the area names when at least this share of its values resolve
# to an area in the uploaded geojson. Set high enough that an unrelated text column cannot
# win by coincidence, low enough to tolerate a few unmatched or misspelt rows.
ENUMERATION_MIN_NAME_MATCH = 0.5
# Rows whose facility cell matches these are totals, not facilities.
ENUMERATION_TOTAL_LABELS = ("TOTAL", "TOTAUX", "GRAND TOTAL")
# A sheet carrying one row per enumerator rather than per facility is skipped: it adds
# nothing the facility sheets lack, and its headers are shifted one column in the
# reference file (the column labelled "Enfants admissibles" in fact holds census records).
ENUMERATION_PER_USER_HEADERS = ("ID DE L UTILISATEUR", "USER ID")
# How a row that pools several facilities is handled:
#   "flag"      - members carry no counts and are reported as pooled (no invented split)
#   "apportion" - the pooled counts are split by each member's official target share
ENUMERATION_POOLED_POLICY = "flag"

# --- Uploaded boundary geojson -------------------------------------------------
# Polygons are the analysis areas; Points, when present, are their anchors, joined to
# their polygon on the area name.
#
# The property holding that name differs between exports, so it is detected rather than
# assumed: these candidates are tried in order, and if none is present the engine falls
# back to whichever string property is unique across the polygons. Set
# BOUNDARY_UPLOAD_NAME_FIELD to pin it explicitly for a known feed.
BOUNDARY_UPLOAD_NAME_FIELD = os.getenv("PDI_BOUNDARY_NAME_FIELD") or None

# An upload is checked against the country it was submitted for before anything is
# computed. A file from the wrong country still produces a full, confident-looking
# dashboard - WorldPop is sampled wherever the polygons happen to fall - so the
# mismatch has to be caught here rather than discovered in the output.
#
# Share of the upload that must fall inside the country's districts. A correct pairing
# sits near 1.0 and a mismatched one near 0.0, so the threshold is deliberately loose:
# it separates the two cases without rejecting legitimate border areas.
UPLOAD_MIN_COUNTRY_OVERLAP = float(os.getenv("PDI_UPLOAD_MIN_COUNTRY_OVERLAP", "0.5"))
# Slack (degrees, roughly 2 km) for cells that genuinely straddle a national border and
# for the generalisation in the geoBoundaries outline itself.
UPLOAD_COUNTRY_BUFFER_DEG = float(os.getenv("PDI_UPLOAD_COUNTRY_BUFFER_DEG", "0.02"))
BOUNDARY_UPLOAD_NAME_CANDIDATES = (
    "facility_name", "facility", "health_facility", "name", "area_name", "boundary_name",
    "admin_name", "label", "title", "nom", "libelle", "denominacion", "nombre",
    "shapeName", "NAME", "Name",
)
# Properties copied through when present. Anything else on the feature is ignored.
BOUNDARY_UPLOAD_PROVENANCE = (
    "gps_source", "anchor_source", "boundary_method", "point_count", "area_km2", "warnings")
# An anchor from any other source is a fallback position, not a surveyed facility location.
BOUNDARY_TRUSTED_ANCHOR_SOURCES = ("field_gps",)
# point_count is the sample the cell was drawn from, not an enumeration total (in the
# reference file it sums to exactly 10,000 - an export cap). Cells built from fewer than
# this many points have unreliable shapes and are flagged for review.
CATCHMENT_LOW_SAMPLE_POINTS = 50

DEFAULT_TARGET_GROUPS = ["total", "under5", "age_00", "age_01_04"]

COUNTRY = COUNTRY_ISO3
# Fallback campaign id, matching the one the API mints. The engine is country-agnostic,
# so this is derived rather than naming any one country's campaign.
CAMPAIGN_ID = f"PDI-{COUNTRY_ISO3}-{TARGET_YEAR}"
TENANT_ID = "default"
WORLDPOP_VERSION = "2026-CN-100m-R2025A"
OPEN_BUILDINGS_SOURCE = "vida-google-microsoft-osm"

SANITY_BBOX = None
WORLDPOP_REFERENCE = None

TARGET_GROUPS = {
    "total": ["total"],
    "age_00": ["t_00"],
    "age_01_04": ["t_01"],
    "age_05_09": ["t_05"],
    "age_10_14": ["t_10"],
    "age_15_19": ["t_15"],
    "age_20_24": ["t_20"],
    "age_25_29": ["t_25"],
    "age_30_34": ["t_30"],
    "age_35_39": ["t_35"],
    "age_40_44": ["t_40"],
    "age_45_49": ["t_45"],
    "age_50_54": ["t_50"],
    "age_55_59": ["t_55"],
    "age_60_64": ["t_60"],
    "age_65_69": ["t_65"],
    "age_70_74": ["t_70"],
    "age_75_79": ["t_75"],
    "age_80_84": ["t_80"],
    "age_85_89": ["t_85"],
    "age_90_plus": ["t_90"],
    "under5": ["t_00", "t_01"],
    "under15": ["t_00", "t_01", "t_05", "t_10"],
    "school_age_5_14": ["t_05", "t_10"],
    "working_age_15_64": [
        "t_15", "t_20", "t_25", "t_30", "t_35",
        "t_40", "t_45", "t_50", "t_55", "t_60",
    ],
    "elderly_65_plus": ["t_65", "t_70", "t_75", "t_80", "t_85", "t_90"],
    "female_all": ["T_F"],
    "male_all": ["T_M"],
    "women_15_49": ["f_15", "f_20", "f_25", "f_30", "f_35", "f_40", "f_45"],
    "men_15_49": ["m_15", "m_20", "m_25", "m_30", "m_35", "m_40", "m_45"],
    "female_under5": ["f_00", "f_01"],
    "male_under5": ["m_00", "m_01"],
    "female_under15": ["f_00", "f_01", "f_05", "f_10"],
    "male_under15": ["m_00", "m_01", "m_05", "m_10"],
    "female_under30": ["f_00", "f_01", "f_05", "f_10", "f_15", "f_20", "f_25"],
    "male_under30": ["m_00", "m_01", "m_05", "m_10", "m_15", "m_20", "m_25"],
}

AGE_BANDS = [
    ("00", "00"), ("01", "01_04"), ("05", "05_09"), ("10", "10_14"), ("15", "15_19"),
    ("20", "20_24"), ("25", "25_29"), ("30", "30_34"), ("35", "35_39"), ("40", "40_44"),
    ("45", "45_49"), ("50", "50_54"), ("55", "55_59"), ("60", "60_64"), ("65", "65_69"),
    ("70", "70_74"), ("75", "75_79"), ("80", "80_84"), ("85", "85_89"), ("90", "90_plus"),
]
for _token, _label in AGE_BANDS:
    TARGET_GROUPS[f"female_age_{_label}"] = [f"f_{_token}"]
    TARGET_GROUPS[f"male_age_{_label}"] = [f"m_{_token}"]

STORAGE_CRS = "EPSG:4326"
# Equal-area CRS used for district area / population density (EPSG:6933).
AREA_CRS = "EPSG:6933"
METRIC_CRS = None  # None auto-derives the UTM zone from the data

BUILDING_CONFIDENCE_THRESHOLD = 0.70

DBSCAN_EPS_METERS = 100
DBSCAN_MIN_SAMPLES = 3
INVISIBLE_BUFFER_METERS = 200
INVISIBLE_STATUS_INITIAL = "UNVERIFIED"
# Feature 4 is dormant. Detection asks "is there a building no enumerator came within 200 m
# of?", which needs household-level GPS. The enumeration workbook is aggregate - counts per
# facility, no coordinates - so the question cannot be asked from it. The code and its table
# are intact; set this True once a point-level household export is available.
INVISIBLE_ENABLED = os.getenv("PDI_INVISIBLE", "").lower() in ("1", "true", "yes")

CONCAVE_HULL_RATIO = 0.1

GAP_GREEN_THRESHOLD = 0.85
GAP_YELLOW_THRESHOLD = 0.50

# Which like-for-like comparison drives gap_classification and the headline coverage_ratio.
# "under5" is the default: both sides are directly measured counts of children 0-59 months,
# and it is the cohort the campaign plans against. "households" is also directly measured;
# "population" relies on the derived headcount and is the weakest of the three.
COVERAGE_PRIMARY_MEASURE = "under5"

RISK_WEIGHTS = {
    "population_gap": 0.30,
    "past_performance": 0.25,
    "facility_distance": 0.20,
    "building_density": 0.15,
    "missed_children": 0.10,
}

RISK_FACILITY_MAX_KM = 50

RISK_MISSING_FACTOR_DEFAULT = 0.5
RISK_PROVISIONAL_FACTORS = ("past_performance", "missed_children")
# Priority bands on the 0-100 score.
RISK_CRITICAL_THRESHOLD = 75
RISK_HIGH_THRESHOLD = 50
RISK_MEDIUM_THRESHOLD = 25

SANITY_TOLERANCE = 0.02

OUTPUT_DIR = REPO_ROOT / "pdi-batch" / "output"
ESTIMATION_OUTPUT_DIR = OUTPUT_DIR / "estimation"
DISTRICT_POPULATION_CSV = ESTIMATION_OUTPUT_DIR / "district_population.csv"
DISTRICT_POPULATION_GEOJSON = ESTIMATION_OUTPUT_DIR / "district_population.geojson"

GAP_OUTPUT_DIR = OUTPUT_DIR / "gap"
GAP_REPORT_CSV = GAP_OUTPUT_DIR / "gap_report.csv"
GAP_REPORT_GEOJSON = GAP_OUTPUT_DIR / "gap_report.geojson"

INVISIBLE_OUTPUT_DIR = OUTPUT_DIR / "invisible"
INVISIBLE_SETTLEMENTS_CSV = INVISIBLE_OUTPUT_DIR / "invisible_settlements.csv"
INVISIBLE_SETTLEMENTS_GEOJSON = INVISIBLE_OUTPUT_DIR / "invisible_settlements.geojson"
