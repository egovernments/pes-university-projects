# Population Denominator Intelligence (PDI)

A geospatial intelligence layer for the DIGIT **Health Campaign Management (HCM)** platform. PDI
estimates the *expected* population and households for every campaign area from open satellite and
building-footprint data, compares that expectation against who was actually registered in the field,
and surfaces the coverage gaps, unmapped settlements, and risk hotspots that a manual microplan
cannot see.

PDI is **country-agnostic**: you give it an ISO3 country code, and it fetches every input it needs
on demand (boundaries, population rasters, building footprints). Nothing is pre-downloaded and
nothing is Chad-specific. Chad (`TCD`) is simply the reference deployment, because it is the country
we have a registered-beneficiary sample for.

---

## 1. The problem PDI solves

Campaign coverage today is measured as `served / registered`. The flaw is that **"registered" is not
"actual population"** — field enumeration misses households and whole settlements, so the denominator
is wrong and coverage looks better than it is.

```
If 800 people are registered out of 1,200 who actually live there:
  HCM today reports   800 / 800  = 100%   (on track)
  Reality is          800 / 1200 = 67%    (400 people invisible)
```

PDI supplies an **independent denominator** derived from WorldPop population rasters and Open
Buildings footprints, then does the gap arithmetic per boundary so supervisors can act on the
difference.

---

## 2. Architecture at a glance

Three tiers, in the DIGIT convention: **Python for offline geospatial analytics, Java Spring Boot for
the request-serving API, React for presentation.** Heavy raster and vector math never runs on a
request thread — the Java service shells out to the Python engine as an *asynchronous job*, persists
the result into PostGIS, and serves every repeat from cache.

```mermaid
graph TB
    subgraph Sources["Open data, fetched on demand by ISO3"]
        GB["geoBoundaries API<br/>ADM2 / ADM1 / ADM0 polygons"]
        WP["WorldPop REST<br/>100 m GeoTIFF<br/>total + age/sex bands"]
        VIDA["VIDA Open Buildings<br/>GeoParquet footprints<br/>Google v3 + Microsoft + OSM"]
    end

    subgraph Local["Optional local inputs"]
        SHEET["Microplan sheet (.xlsx)<br/>facility lat/lon + boundary codes"]
        REG["HCM register sample<br/>individuals + households"]
    end

    subgraph Engine["pdi-batch — intelligence engine (Python)"]
        F1["Feature 1 · estimation"]
        F2["Feature 2 · gap"]
        F4["Feature 4 · invisible settlements"]
        F5["Feature 5 · risk"]
        TGT["features.targets<br/>orchestrator + CLI"]
    end

    subgraph Service["pdi-service — population-denominator-service (Java 21 / Spring Boot 3.4)"]
        JOB["Async job runner<br/>PythonEngineRunner"]
        CACHE["Result cache<br/>engine_result_cache"]
        API["REST API<br/>/population/v1/**"]
    end

    PG[("PostgreSQL 15 + PostGIS 3.4<br/>settlement_boundary · population_estimate<br/>gap_report · invisible_settlement<br/>building_footprint · engine_result_cache")]

    subgraph UI["Presentation"]
        DEMO["demo/ — React + Leaflet<br/>Feature 3 dashboard"]
        DIGIT["react/ — DIGIT HCM<br/>micro-frontend module"]
    end

    GB --> F1
    WP --> F1
    VIDA --> F1 & F4
    SHEET --> TGT
    REG --> F2 & F4
    F1 --> F2 --> F5
    F1 & F2 & F4 & F5 --> TGT
    TGT -->|persistence/store.py| PG
    JOB --> TGT
    API --> JOB
    API --> CACHE --> PG
    API -->|JPA read| PG
    DEMO --> API
    DIGIT -. planned .-> API
```

**Read path vs. write path.** The engine writes; the API reads. `pdi-batch/persistence/store.py`
upserts every feature output into PostGIS at the end of a run, and `DashboardController` serves
dashboard aggregates straight out of those tables through JPA — no raster math at request time.

---

## 3. Repository layout

| Path | What it is |
|------|-----------|
| `pdi-batch/` | The Python intelligence engine. Source adapters, five features, PostGIS persistence, tests. |
| `pdi-batch/config.py` | Single config module: country, target cohorts, thresholds, CRS, risk weights, paths. |
| `pdi-batch/sources/` | On-demand adapters: `remote` (download + cache), `boundaries`, `worldpop`, `buildings`, `catchments`, `register`. |
| `pdi-batch/features/` | `estimation` (F1), `gap` (F2), `invisible` (F4), `risk` (F5), `targets` (orchestrator + CLI entry point). |
| `pdi-batch/persistence/store.py` | Upserts engine output into the PostGIS tables. |
| `pdi-batch/tests/` | `pytest` suite over sources and features. |
| `pdi-service/` | `population-denominator-service` — Spring Boot 3.4 / Java 21 API that drives the engine and serves results. |
| `db/migration/` | Flyway migrations `V1`–`V5` (PostGIS extensions, boundaries, PDI tables, risk-config seed, result cache). |
| `db/init/` | SQL applied automatically on first container start (extensions). |
| `demo/` | React + Leaflet dashboard (Feature 3) driven entirely by the service API. |
| `react/` | DIGIT HCM micro-frontend module scaffold (`digit-ui-module-health-pdi`). |
| `Data_Source/` | Local-only inputs: the synthetic Chad register and microplan workbooks. |
| `docker-compose.yml` | PostGIS 15/3.4, plus Redis and Kafka behind the `full` profile. |

---

## 4. Prerequisites

| Tool | Version | Needed for |
|------|---------|-----------|
| Python | 3.12 | the batch engine |
| Java JDK | 21 | the Spring Boot service |
| Maven | 3.9+ (or the bundled `mvnw`) | building the service |
| Docker + Compose | any recent | PostGIS, Flyway |
| Node.js | 18+ | the demo dashboard |

Disk and network: the first run for a country downloads WorldPop rasters (~37 files) and the VIDA
building parquet. For Chad that is a few GB and can take from minutes to hours on a slow link.
Everything lands in `.cache/<ISO3>/` and is reused forever after.

---

## 5. Getting started (full, from a clean clone)

The order matters: **database → Python engine → Java service → dashboard.** Each step below is
independently verifiable, so you can stop and check before moving on.

### 5.1 Configure environment

```bash
cd PES-U/population-denominator-intelligence
cp .env.example .env
```

Open `.env` and set the Postgres credentials. The defaults in `.env.example` (`pdi` / `pdi` / `pdi`)
line up with what `docker-compose.yml` creates and what `application.yml` expects, so you can leave
them alone for local work:

```ini
PDI_ISO3=TCD                 # default country for the batch CLI
PDI_YEAR=2026                # default projection year

POSTGRES_DB=pdi
POSTGRES_USER=pdi
POSTGRES_PASSWORD=pdi
POSTGRES_HOST=localhost
POSTGRES_PORT=5432

DATABASE_URL=postgresql://pdi:pdi@localhost:5432/pdi
```

`DATABASE_URL` is what the **Python** side reads (`pdi-batch/db.py`); the individual `POSTGRES_*`
variables are what **docker-compose** and the **Java** service read. Keep the two consistent.

### 5.2 Start the database

```bash
docker compose up -d postgis
```

This starts PostgreSQL 15 with PostGIS 3.4 on port 5432, with a named volume `pdi_pgdata` so your
data survives container restarts. On first start it runs `db/init/01_extensions.sql`, which enables
`postgis`, `postgis_raster` and `pg_trgm`.

Add Redis and Kafka only if you need them (they are provisioned for future caching/eventing and are
not used by the current code path):

```bash
docker compose --profile full up -d
```

Verify the container is healthy:

```bash
docker compose ps
docker exec -it pdi-postgis psql -U pdi -d pdi -c "SELECT postgis_full_version();"
```

### 5.3 Apply the database schema (Flyway)

The schema lives in `db/migration/` as five migrations:

| Migration | Creates |
|-----------|---------|
| `V1__extensions.sql` | `postgis`, `postgis_raster`, `pg_trgm` extensions |
| `V2__settlement_boundary.sql` | `settlement_boundary` + GiST index on the polygon |
| `V3__pdi_tables.sql` | `population_estimate`, `gap_report`, `invisible_settlement`, `building_footprint`, `risk_score_config` |
| `V4__risk_config_seed.sql` | default risk weights for the scoring model |
| `V5__engine_result_cache.sql` | `engine_result_cache` — the whole-run result cache |

**Option A — Flyway via Docker (no local install).** Run from the project root:

```bash
docker run --rm --network host \
  -v "$PWD/db/migration:/flyway/sql" \
  flyway/flyway:10 \
  -url=jdbc:postgresql://localhost:5432/pdi \
  -user=pdi -password=pdi \
  -baselineOnMigrate=true -baselineVersion=0 \
  migrate
```

On Windows PowerShell, substitute `${PWD}` for `$PWD`. If `--network host` is unavailable (Docker
Desktop on Windows/macOS), use `-url=jdbc:postgresql://host.docker.internal:5432/pdi` and drop
`--network host`.

**Option B — local Flyway CLI.** Copy the config template and fill in your password:

```bash
cp db/flyway.conf.example db/flyway.conf
# edit db/flyway.conf, then:
flyway -configFiles=db/flyway.conf migrate
```

`db/flyway.conf` is gitignored because it holds a password — only the `.example` is tracked.

Verify the schema landed:

```bash
docker exec -it pdi-postgis psql -U pdi -d pdi -c "\dt"
```

You should see `settlement_boundary`, `population_estimate`, `gap_report`, `invisible_settlement`,
`building_footprint`, `risk_score_config`, `engine_result_cache` and Flyway's own
`flyway_schema_history`.

### 5.4 Set up the Python engine

```bash
python -m venv .venv

source .venv/Scripts/activate      # Windows, Git Bash
# .venv\Scripts\Activate.ps1       # Windows, PowerShell
# source .venv/bin/activate        # Linux / macOS

pip install -r requirements.txt
```

Check the database connection from Python:

```bash
python pdi-batch/db.py            # prints: SELECT 1 -> 1
```

### 5.5 Run the Java API service

```bash
cd pdi-service
mvn spring-boot:run
```

### 5.6 Run the demo dashboard

```bash
cd demo
npm install
npm run dev
```

Opens **http://localhost:5180**. It calls the service at `http://localhost:8080` by default; point it
elsewhere with `VITE_API_BASE`.

The service allows CORS from `http://localhost:*` and `http://127.0.0.1:*` out of the box
(`pdi.cors.allowed-origin-patterns`).

### 5.7 End-to-end smoke test

With the database, the service and the dashboard all up:

1. Open the dashboard, pick **Chad (TCD)**, optionally upload a microplan workbook, and hit compute.
2. The job streams progress (`PROGRESS <pct>` lines from the engine are parsed into the progress bar).
   First run for a country downloads rasters; later runs are near-instant from the result cache.
3. When it completes you get the coverage map, catchments, invisible settlements and risk tabs.

---

## 6. API reference

All endpoints are under `/population/v1` and tenant-scoped (`tenantId`, default `default`).

### Compute (async job over the Python engine)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/targets/_compute` | Submit a compute job (multipart). Returns `jobId` + `statusUrl`. |
| `GET` | `/targets/{jobId}/_status` | `PENDING` / `RUNNING` / `DONE` / `FAILED`, with message + percent. |
| `GET` | `/targets/{jobId}/_download` | Filled target workbook (`.xlsx`, sheet runs only). |
| `GET` | `/targets/{jobId}/_geojson` | Whole-country boundary polygons + targets + gap + risk. |
| `GET` | `/targets/{jobId}/_catchments` | Voronoi catchment cells (sheet runs only). |
| `GET` | `/targets/{jobId}/_buildings` | Catchment building points tagged by facility. |
| `GET` | `/targets/{jobId}/_settlements` | Invisible-settlement clusters. |
| `GET` | `/targets/{jobId}/_stats` | Dashboard summary JSON. |

`_compute` parameters: `iso3` (required), `sheet` (file), `year`, `householdSize`, `groups`,
`withBuildings` (default `true`), `campaignId`, `tenantId`, `force` (default `false`).

### Dashboard (read-only, straight from PostGIS via JPA)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/dashboard/_stats?campaignId=&boundaryCode=&tenantId=` | Summary totals, gap distribution, risk distribution, top gaps. |
| `POST` | `/gap/_search?tenantId=` | Paged gap-report search; body is `{"gapSearchCriteria": {"campaignId": "..."}}`. |

---

## 7. Caching — two independent layers

PDI caches at two levels, and they solve different problems. Both are safe to delete.

**1. Download cache — `.cache/<ISO3>/` (disk).** Raw WorldPop GeoTIFFs, geoBoundaries GeoJSON and the
VIDA parquet, streamed once and reused. This is the expensive one: it is what turns a multi-hour
first run into a two-minute rerun. Deleting it forces a re-download.

**2. Result cache — `engine_result_cache` (Postgres).** A whole engine run is deterministic for a
given country and parameter set, so the service hashes the normalized request (ISO3, year, household
size, groups, buildings flag, uploaded-sheet SHA-256) and stores the complete response plus every
artifact against that key. A repeat request is served from Postgres without touching Python at all.

`force=true` on `_compute` skips the *lookup* but still writes its result back, so a forced recompute
refreshes the existing entry rather than duplicating it or leaving it stale. If Postgres is
unreachable the cache disables itself for the process and every request simply runs the engine.

---

## 8. How the engine works

### 8.1 Everything is fetched by ISO3

`sources/remote.py` is the single download choke point. Given a country code it resolves and streams:

- **Boundaries** — geoBoundaries `gbOpen` API, trying `ADM2` first and falling back to `ADM1`, then
  `ADM0`. Codes come from `shapeID`, names from `shapeName`.
- **WorldPop** — the REST catalogue, resolving the constrained 100 m total-population and age/sex
  structure releases for the requested year.
- **Buildings** — the VIDA combined Open Buildings GeoParquet for that country, downloaded once to
  `.cache/<ISO3>/<ISO3>_buildings.parquet` and then read locally with bounding-box pushdown. A single
  run reads it up to four times (country estimate, invisible settlements, and both passes when a
  microplan sheet is uploaded), so streaming it per read made every run pay the full ~600 MB repeatedly.

Downloads emit `PROGRESS <pct> …` lines, which the Java service parses into the UI progress bar.

### 8.2 WorldPop → population (`sources/worldpop.py`)

For each boundary polygon the engine computes a **coverage-weighted zonal sum**:

1. Read only the raster window covering the polygon's bounding box.
2. **Sub-pixel supersampling** (`COVERAGE_SUBSAMPLE = 10`): each 100 m pixel is diced 10×10, the
   polygon is rasterized onto that fine grid, and the fraction of each coarse pixel genuinely inside
   the polygon becomes its weight. `population = Σ (pixel_value × covered_fraction)`.
3. Coverage weighting — rather than naive pixel-centre-in-polygon — is what fixed the N'Djamena
   sub-pixel undercount, where small dense urban catchments were reading close to zero.

The same routine runs for every entry in `config.TARGET_GROUPS`, giving each boundary a selectable
denominator across **60+ cohorts**: `total`, `under5`, `under15`, `women_15_49`, every five-year band
`age_00` … `age_90_plus`, and each of those split by sex.

```mermaid
flowchart LR
    P["Boundary polygon"] --> W["Read raster window"]
    W --> S["Supersample pixels 10x10"]
    S --> C["Rasterize polygon -> coverage fraction"]
    C --> M["Sum value x fraction"]
    M --> D["Population per cohort<br/>total, under5, women_15_49, ..."]
```

### 8.3 Open Buildings → households (`sources/buildings.py`)

1. Spatial-filter the GeoParquet to the boundary bounding box (`bbox=` pushdown when a covering-bbox
   column exists, `.cx` fallback otherwise).
2. **Confidence filter:** keep Google footprints with `confidence ≥ 0.70`; keep all null-confidence
   footprints, since Microsoft and OSM carry no score.
3. Assign each building to a boundary by representative-point-within spatial join.

Buildings feed the household proxy in Feature 1 and are the raw material for Feature 4.

### 8.4 Feature 1 — the ensemble (`features/estimation.py`)

WorldPop and buildings are cross-validated rather than trusted blindly:

```
buildingEstimate = buildingCount * AVG_HOUSEHOLD_SIZE      # 5.4 by default
divergence       = |worldpop - buildingEstimate| / worldpop

if buildings present and divergence < 0.30:                # methods agree
    population = 0.6*worldpop + 0.4*buildingEstimate
    confidence = 0.85 + 0.15*(1 - divergence)              # method = "ensemble"
else:                                                       # methods diverge
    population = worldpop                                   # WorldPop primary
    confidence = 0.50 + 0.2*min(buildingCount/10, 1)       # method = "worldpop_primary"
```

Each boundary also gets an equal-area area (`EPSG:6933`) and the resulting population density.

### 8.5 Feature 2 — gap detection (`features/gap.py`)

Registered individuals and households are spatially joined into boundaries and compared against the
Feature 1 estimate:

```
coverageRatio = registeredPopulation / estimatedPopulation

GREEN   ratio >= 0.85
YELLOW  0.50 <= ratio < 0.85
RED     ratio < 0.50
BLACK   registered = 0 but buildings > 0   (a built-up area with nobody registered)
```

Gap detection only runs when the country has a register (`sources/register.has_register`) — today
that is Chad, from the synthetic sample in `Data_Source/`. For any other ISO3 the engine returns
estimates without gap or risk, rather than inventing coverage it cannot measure. Because the sample
covers only N'Djamena, a national Chad run correctly reports most districts as BLACK.

### 8.6 Feature 4 — invisible settlements (`features/invisible.py`)

Finds clusters of buildings that **no registered household reaches** — settlements the campaign never
enumerated:

1. Filter to buildings with no registered household within `INVISIBLE_BUFFER_METERS = 200`, via a
   single STRtree-indexed nearest query over the whole set (`O(n log m)`, not a per-building loop).
2. **DBSCAN** the uncovered centroids per boundary (`eps = 100 m`, `min_samples = 3`).
3. Each cluster's footprint is the **concave hull** (alpha shape, `ratio = 0.1`), so a sprawling
   settlement follows its real outline instead of a convex hull that bridges empty gaps.
4. Attach the nearest registered boundary, distance, centroid, and an `UNVERIFIED` status for field
   follow-up.

### 8.7 Feature 5 — explainable risk scoring (`features/risk.py`)

A transparent weighted linear model (weights configurable, seeded in `risk_score_config`) scoring each
boundary 0–100 and enriching the gap report in place:

```mermaid
pie title Risk score weights
    "Population gap" : 30
    "Past performance" : 25
    "Facility distance" : 20
    "Building density" : 15
    "Missed children" : 10
```

- **Population gap** — `populationGap / estimatedPopulation`, clamped 0–1.
- **Facility distance** — km from the boundary's interior point to the nearest facility from the
  uploaded sheet, saturating at `RISK_FACILITY_MAX_KM = 50`; farther = less access = higher risk.
- **Building density** — buildings per km², min-max normalized across boundaries in scope.
- **Past performance** and **missed children** — no data feed yet, so they are held at a neutral `0.5`
  and flagged `provisional` in the per-factor `risk_factors` JSON. The score stays honest about what
  it does and does not know.

Bands: `CRITICAL ≥ 75`, `HIGH ≥ 50`, `MEDIUM ≥ 25`, else `LOW`. The full per-factor breakdown (score,
weight, provisional flag) is written to `risk_factors` (jsonb) for explainability.

### 8.8 Catchment overlay (`sources/catchments.py`)

When a microplan sheet supplies facility coordinates, the engine builds **Voronoi catchment cells**
around those points, clipped to their district. Points falling outside every district are snapped to
the nearest within `CATCHMENT_SNAP_TOLERANCE_M = 2000` and dropped beyond that. Every feature then
re-runs over the catchment cells, so you get the same estimates at facility granularity alongside the
whole-country district layer.

---

## 9. Data model

Defined as Flyway migrations under `db/migration/`. Every table is tenant-scoped (`tenant_id`) to fit
DIGIT multi-tenancy, and all geometry is stored as `EPSG:4326`.

```mermaid
erDiagram
    SETTLEMENT_BOUNDARY ||--o| POPULATION_ESTIMATE : "boundary_code"
    SETTLEMENT_BOUNDARY ||--o{ GAP_REPORT : "boundary_code"
    SETTLEMENT_BOUNDARY ||--o{ BUILDING_FOOTPRINT : "boundary_code"

    SETTLEMENT_BOUNDARY {
        uuid id PK
        text boundary_code UK
        text boundary_type
        text name
        text parent_boundary_code
        geometry polygon
        float area_km2
        text tenant_id
    }
    POPULATION_ESTIMATE {
        uuid id PK
        text boundary_code FK
        int estimated_population
        int estimated_households
        int building_count
        float confidence
        text method
        text worldpop_version
        text open_buildings_version
        float population_density
        text tenant_id
    }
    GAP_REPORT {
        uuid id PK
        text boundary_code FK
        text campaign_id
        int estimated_population
        int registered_population
        int population_gap
        int estimated_households
        int registered_households
        int household_gap
        float coverage_ratio
        text gap_classification
        int risk_score
        text risk_priority
        jsonb risk_factors
        text tenant_id
    }
    INVISIBLE_SETTLEMENT {
        uuid id PK
        text cluster_id UK
        geometry centroid
        geometry convex_hull
        int building_count
        int estimated_population
        text nearest_boundary_code
        float distance_to_nearest_km
        text status
        text tenant_id
    }
    BUILDING_FOOTPRINT {
        bigint id PK
        geometry polygon
        geometry centroid
        float area_m2
        float confidence
        text boundary_code FK
        text source_dataset
        text tenant_id
    }
    RISK_SCORE_CONFIG {
        uuid id PK
        text campaign_type
        float weight_population_gap
        float weight_building_density
        float weight_facility_distance
        float weight_past_performance
        float weight_missed_children
        text tenant_id
    }
    ENGINE_RESULT_CACHE {
        text cache_key PK
        text iso3
        jsonb params
        jsonb response
        text geojson
        text stats_json
        bytea sheet_xlsx
        int hit_count
    }
```

`gap_classification` is constrained to `GREEN/YELLOW/RED/BLACK`, `risk_priority` to
`CRITICAL/HIGH/MEDIUM/LOW`. `gap_report` is unique per `(campaign_id, boundary_code)`, with indexes on
`(campaign_id, gap_classification)` and `(campaign_id, risk_score DESC)` for dashboard queries.
Geometry columns carry GiST indexes.

### Adding a migration

Create `db/migration/V6__<description>.sql` and re-run the Flyway command from §5.3. Never edit an
applied migration — Flyway validates checksums and will refuse to run.

---

## 10. Configuration reference

| Variable | Default | Read by | Purpose |
|----------|---------|---------|---------|
| `PDI_ISO3` | `TCD` | Python | Default country for the batch CLI |
| `PDI_YEAR` | `2026` | Python | Default projection year |
| `PDI_CACHE_DIR` | `.cache/` | Python | Where downloads are cached |
| `DATABASE_URL` | — | Python | SQLAlchemy URL for persistence |
| `POSTGRES_DB/USER/PASSWORD/HOST/PORT` | `pdi`/`pdi`/`pdi`/`localhost`/`5432` | Compose, Java | Database connection |
| `SPRING_DATASOURCE_URL` | derived from `POSTGRES_*` | Java | Override the full JDBC URL |
| `PDI_ENGINE_PYTHON` | `../.venv/Scripts/python.exe` | Java | Interpreter used to run the engine |
| `PDI_ENGINE_WORKDIR` | `../pdi-batch` | Java | Working directory for the engine process |
| `PDI_ENGINE_TIMEOUT` | `0` | Java | Hard kill after N seconds; `0` = no cap |
| `PDI_PERSIST_BUILDINGS` | `true` | Java | Persist individual footprints on first compute |
| `PDI_CACHE_ENABLED` | `true` | Java | Master switch for the result cache |
| `PDI_ARTIFACTS_DIR` | system temp | Java | Where job artifacts are written |
| `VITE_API_BASE` | `http://localhost:8080` | demo | API base URL |

Non-environment tuning — cohort definitions, thresholds, DBSCAN parameters, risk weights, CRS — lives
in `pdi-batch/config.py`.

---

## 11. Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `DATABASE_URL is not set; check the project root .env` | `.env` missing or not at the project root. `pdi-batch/db.py` loads it from one level above `pdi-batch/`. |
| Service starts but `_compute` fails immediately | Wrong `PDI_ENGINE_PYTHON`. It is relative to `pdi-service/`; on Linux/macOS use `../.venv/bin/python`. |
| First compute appears frozen for a long time | It is downloading WorldPop rasters. Watch the job log in `PDI_ARTIFACTS_DIR`; `PROGRESS` lines show the percentage. |
| `WARNING persistence skipped: …` in the engine output | Postgres unreachable or migrations not applied. Persistence is best-effort by design — the compute still returns. |
| Dashboard loads but stats are empty | The compute ran without `campaignId`, so nothing was persisted, or you are querying a different `campaignId`/`tenantId`. |
| Repeat computes are still slow | Result cache is off or the DB is down. Check `PDI_CACHE_ENABLED` and that `V5` has been applied. |
| Flyway `validate` failure | An applied migration was edited. Restore it, or `flyway repair` if you know what changed. |
| Everything is BLACK in the gap report | Expected for Chad: the register sample only covers N'Djamena. For other countries there is no register at all, so gap/risk are skipped. |
| CORS error in the browser | The dashboard origin is not in `pdi.cors.allowed-origin-patterns` (defaults cover `localhost:*`). |

---

## 12. Technology summary

| Concern | Choice | Notes |
|---------|--------|-------|
| Raster zonal stats | `rasterio`, `rasterstats`, `numpy` | coverage-weighted sub-pixel extraction |
| Vector / spatial | `geopandas`, `shapely`, `pyproj`, `pyogrio` | spatial joins, hulls, Voronoi, equal-area density |
| Building I/O | `pyarrow` GeoParquet from the local cache | VIDA combined Open Buildings, bbox pushdown |
| Clustering | `scikit-learn` DBSCAN | invisible settlement detection |
| Persistence | `sqlalchemy`, `geoalchemy2`, `psycopg` | upserts into PostGIS |
| Database | PostgreSQL 15 + PostGIS 3.4, Flyway | DIGIT-standard, tenant-scoped |
| API | Java 21, Spring Boot 3.4, Spring Data JPA | async job runner + read API |
| Frontend | React 18 + Leaflet (`demo/`), DIGIT module (`react/`) | Feature 3 visualization |
| Testing | `pytest`, JUnit via `spring-boot-starter-test` | |

### Coordinate reference systems

`EPSG:4326` (WGS84) is the storage CRS for all persisted geometry. Distance and clustering run in a
metric CRS auto-derived per dataset (the UTM zone from the data bounds); area and population density
use the equal-area `EPSG:6933`.

---

Developed by Kashyap K