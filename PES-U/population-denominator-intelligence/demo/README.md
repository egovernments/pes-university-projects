# PDI Demo Dashboard

The Feature 3 visualization layer: a React + Leaflet single-page app driven entirely by the
`population-denominator-service` API. It has **no local data files and no build-time data prep** —
every map, table and statistic comes from the service over HTTP.

For the full project setup (database, engine, service), see the [root README](../README.md).

## Run

```bash
npm install
npm run dev          # http://localhost:5180
```

The service must already be running on `http://localhost:8080`. Point elsewhere with:

```bash
VITE_API_BASE=http://localhost:9090 npm run dev
```

Production build:

```bash
npm run build && npm run preview
```

## The flow

1. **Inputs** (`flow/InputsPanel.jsx`) — pick a country by ISO3, optionally upload a microplan
   workbook to add the facility-catchment overlay, and set household size / campaign / tenant.
2. **Loading** (`flow/LoadingView.jsx`) — submits `POST /population/v1/targets/_compute` and polls
   `_status` every 3s. The engine's `PROGRESS` lines drive the bar. A country's first run downloads
   WorldPop rasters and can take hours, so the poll runs against a 12-hour ceiling; repeats come back
   from the service's result cache in seconds.
3. **Results** (`flow/ResultsView.jsx`) — tabs over the returned artifacts:

| Tab | Shows | Source endpoint |
|-----|-------|-----------------|
| District explorer | Per-boundary population + cohort targets, choropleth | `_geojson` |
| Catchments | Voronoi cells per health facility with their buildings | `_catchments`, `_buildings` |
| Coverage gap | Registered vs. estimated, GREEN/YELLOW/RED/BLACK gap map | `_geojson` |
| Invisible settlements | DBSCAN clusters no registered household reaches | `_settlements` |
| Risk priority | 5-factor score per boundary with the per-factor breakdown | `_geojson` |

Only "District explorer" is always present. The other tabs appear when the run produced them — the
catchment tabs need an uploaded sheet, and coverage/invisible/risk need a country with a register.
Above the tabs, a summary strip reads the persisted totals back from PostGIS via
`GET /population/v1/dashboard/_stats`.

Household size can be re-applied client-side (`lib/estimate.js`) so you can see the effect of a
different average without re-running the engine; anything else requires a recompute.

## Layout 

```
src/
  App.jsx              compute → poll → results state machine
  flow/                the API-driven flow: api.js, views, maps, panels
  components/          reusable map/chart pieces (choropleth, age pyramid, stat cards)
  lib/                 pure helpers: colors, formatting, demographics, gap, risk, invisible
```

Design system: "Data-Dense Dashboard" — blue `#1E40AF` with an amber accent, Fira Sans / Fira Code,
WCAG AA.
