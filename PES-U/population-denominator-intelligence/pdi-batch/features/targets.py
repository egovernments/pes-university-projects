"""Feature 3 - the engine entry point the API drives.

Takes the two uploads - a boundary geojson of catchment cells and an enumeration workbook -
computes population targets over both the catchments and the country's districts, compares
them against what the field enumerated, scores risk, and writes the artifacts the service
serves back.

Neither upload is required. Without a boundary geojson the units are the country's
districts; without an enumeration workbook there is nothing to compare against and the
coverage layer reports ``NO_ENUMERATION`` rather than inventing a zero.
"""

import argparse
import json
from pathlib import Path

import geopandas as gpd
import pandas as pd

import config
from features import coverage
from sources import enumeration as enumeration_source
from sources import facilities

CODE = config.BOUNDARY_CODE_FIELD


def _targets_frame(table, groups):
    """Per-boundary target columns (``household_target`` + one per age group)."""
    targets = pd.DataFrame({CODE: table[CODE].to_numpy()})
    targets["household_target"] = table["building_count"].astype(int).to_numpy()
    for group in groups:
        targets[f"{group}_target"] = table[group].round().astype(int).to_numpy()
    return targets.reset_index(drop=True)


def compute(iso3=None, avg_household_size=None, groups=None, with_buildings=True, year=None):
    """Whole-country base layer: targets over the country's own districts."""
    from features import estimation

    groups = groups or config.DEFAULT_TARGET_GROUPS
    table, units = estimation.estimate(
        iso3=iso3, sheet_path=None, with_buildings=with_buildings,
        avg_household_size=avg_household_size, year=year, label="country population")
    return _targets_frame(table, groups), units.reset_index(drop=True)


def compute_catchments(iso3=None, boundaries_path=None, sheet_path=None,
                       avg_household_size=None, groups=None, with_buildings=True, year=None):
    """Catchment overlay: targets over the uploaded cells, or Voronoi cells from a sheet."""
    if not (boundaries_path or sheet_path):
        return None, None
    from features import estimation
    from sources.catchments import build_analysis_units

    groups = groups or config.DEFAULT_TARGET_GROUPS
    cells = build_analysis_units(iso3, sheet_path=sheet_path, boundaries_path=boundaries_path)
    cells = cells[cells["is_catchment"]].reset_index(drop=True)
    if cells.empty:
        return None, None
    table, units = estimation.estimate(
        iso3=iso3, with_buildings=with_buildings, avg_household_size=avg_household_size,
        units=cells, year=year, label="catchment population")
    return _targets_frame(table, groups), units.reset_index(drop=True)


def catchment_buildings(catchment_units, iso3=None):
    if catchment_units is None or catchment_units.empty:
        return None
    from sources import buildings as buildings_source

    clipped = buildings_source.clip_to_boundaries(catchment_units[[CODE, "geometry"]], iso3)
    points = clipped.set_geometry("centroid")[["boundary_code", "centroid"]].rename(
        columns={"boundary_code": "boundaryCode", "centroid": "geometry"})
    return gpd.GeoDataFrame(
        points, geometry="geometry", crs=config.STORAGE_CRS).reset_index(drop=True)


# --- Coverage -----------------------------------------------------------------

def coverage_frame(units, enumeration_path, measure=None, tenant_id=None,
                   avg_household_size=None):
    """``(frame, resolution)``: enumerated counts compared against ``units``.

    Returns ``(None, empty)`` when no enumeration workbook was uploaded, which is what
    switches the coverage and risk layers off downstream. ``avg_household_size`` is the
    size the estimate was computed at, so both sides of the population ratio agree.
    """
    if units is None or not enumeration_source.has_enumeration(enumeration_path):
        return None, facilities.Resolution()
    counts, resolution = enumeration_source.load_enumeration(
        enumeration_path, list(units[CODE].astype(str)),
        avg_household_size=avg_household_size)
    frame = coverage.compare(units, counts, measure=measure, tenant_id=tenant_id)
    frame.attrs["withheld"] = counts.attrs.get("withheld", {})
    return frame, resolution


# --- Risk ---------------------------------------------------------------------

def detect_risk(units, covered, anchors=None):
    """Feature 5 scores, or ``None`` when there is no enumeration to score against."""
    if covered is None:
        return None
    from features import risk as risk_feature

    name = config.BOUNDARY_NAME_FIELD
    base = units[[CODE, name, "geometry"]].copy()
    gap_gdf = base.merge(
        covered[[CODE, "estimated_population", "registered_population", "population_gap",
                 "registered_households", "registered_under5", "estimated_under5",
                 "coverage_ratio", "gap_classification",
                 "active_user_pct", "absent_households"]],
        on=CODE, how="left")
    gap_gdf = gpd.GeoDataFrame(gap_gdf, geometry="geometry", crs=config.STORAGE_CRS)

    _, risk_gdf = risk_feature.score(gap_gdf, units[[CODE, "building_count", "area_km2"]], anchors)
    return risk_gdf


def detect_invisible(iso3=None):
    """Feature 4 clusters, or ``None`` while the feature is dormant.

    Detection needs household-level GPS to ask "is there a building no one visited within
    200 m?". The enumeration workbook is aggregate - counts per facility, no coordinates -
    so the question cannot be asked from it. The feature stays switched off (see
    ``config.INVISIBLE_ENABLED``) until a point-level household export is available,
    rather than shipping a weaker result under the same name.
    """
    if not config.INVISIBLE_ENABLED:
        return None
    from features import invisible as invisible_feature

    _, gdf = invisible_feature.detect(iso3=iso3)
    return gdf


# --- Output -------------------------------------------------------------------

# Computed columns appended to the uploaded workbook, in this order. Prefixed so they are
# obviously the engine's and can never collide with a column the sheet already had.
APPENDED_COLUMNS = {
    "pdi_estimated_population": "population_estimate",
    "pdi_estimated_under5": "estimated_under5",
    "pdi_estimated_households": "estimated_households",
    "pdi_building_count": "building_count",
    "pdi_area_km2": "area_km2",
    "pdi_density_ppl_km2": "density_ppl_km2",
    "pdi_confidence": "confidence",
    "pdi_method": "method",
    "pdi_coverage_ratio": "coverage_ratio",
    "pdi_coverage_measure": "coverage_measure",
    "pdi_gap_classification": "gap_classification",
    "pdi_risk_score": "risk_score",
    "pdi_risk_priority": "risk_priority",
    "pdi_review": "review_flags",
}


def _computed_frame(units, covered, risk, upload):
    """One row per area carrying every value that gets written back to the workbook."""
    frame = units.drop(columns="geometry") if "geometry" in units.columns else units.copy()
    frame = frame.copy()
    if "estimated_under5" not in frame.columns and "under5" in frame.columns:
        frame["estimated_under5"] = frame["under5"].round().astype(int)
    if covered is not None:
        extra = [c for c in coverage.report_columns(covered) if c not in frame.columns]
        frame = frame.merge(covered[[CODE, *extra]], on=CODE, how="left")
    if risk is not None:
        extra = [c for c in ("risk_score", "risk_priority") if c in risk.columns]
        frame = frame.merge(risk[[CODE, *extra]], on=CODE, how="left")
    if upload is not None:
        flags = {name: "; ".join(reasons) for name, reasons in upload.review_flags().items()}
        frame["review_flags"] = frame[CODE].map(flags)
    return frame


def append_to_workbook(enumeration_path, output_path, units, covered, risk=None, upload=None):
    """Write the uploaded workbook back out with the engine's columns appended.

    Returns ``(path, appended_row_count)``.
    """
    from sources import enumeration as enumeration_source
    from sources import facilities as facilities_source

    frame = _computed_frame(units, covered, risk, upload)
    available = {label: source for label, source in APPENDED_COLUMNS.items()
                 if source in frame.columns}

    values_by_area = {}
    for _, row in frame.iterrows():
        key = facilities_source.normalize(row[CODE])
        if key:
            values_by_area[key] = {label: row[source] for label, source in available.items()}

    return enumeration_source.annotate_workbook(
        enumeration_path, output_path, values_by_area, list(available))


RISK_PROPERTY_COLUMNS = ["risk_score", "risk_priority", "risk_factors"]


def write_geojson(units, targets, output_path, covered=None, risk=None):
    merged = units.merge(targets, on=CODE, how="left")
    if covered is not None:
        columns = [c for c in coverage.report_columns(covered) if c not in merged.columns]
        merged = merged.merge(covered[[CODE, *columns]], on=CODE, how="left")
    if risk is not None:
        columns = [c for c in RISK_PROPERTY_COLUMNS if c in risk.columns]
        merged = merged.merge(risk[[CODE, *columns]], on=CODE, how="left")
    frame = merged.rename(columns={CODE: "boundaryCode", config.BOUNDARY_NAME_FIELD: "name"})
    output_path = Path(output_path)
    frame.to_file(output_path, driver="GeoJSON")
    return output_path


def write_points_geojson(points, output_path):
    output_path = Path(output_path)
    if points is None or points.empty:
        output_path.write_text(
            '{"type": "FeatureCollection", "features": []}', encoding="utf-8")
    else:
        points.to_file(output_path, driver="GeoJSON")
    return output_path


def build_stats(units, covered, risk=None, summary_extra=None):
    """Dashboard summary. Headline coverage is the configured primary measure."""
    df = units.drop(columns="geometry") if "geometry" in units.columns else units.copy()
    est_pop = int(df["population_estimate"].sum())
    est_hh = int(df["estimated_households"].sum())

    summary = {
        "totalEstimatedPopulation": est_pop,
        "totalEstimatedHouseholds": est_hh,
        "totalRegisteredPopulation": 0,
        "totalRegisteredHouseholds": 0,
        "totalRegisteredUnder5": 0,
        "totalPopulationGap": est_pop,
        "householdGap": est_hh,
        "overallCoverageRatio": None,
        "coverageMeasure": config.COVERAGE_PRIMARY_MEASURE,
        "populationIsDerived": True,
        "invisibleSettlementCount": 0,
        "invisibleEstimatedPopulation": 0,
        "invisibleEnabled": config.INVISIBLE_ENABLED,
    }
    gap_distribution, top_gap, measures = {}, [], {}

    risk_lookup, risk_distribution = {}, {}
    if risk is not None and not risk.empty:
        risk_lookup = {row[CODE]: (int(row["risk_score"]), row["risk_priority"])
                       for _, row in risk.iterrows()}
        risk_distribution = {str(priority): int(count)
                             for priority, count in risk["risk_priority"].value_counts().items()}

    if covered is not None:
        totals = coverage.totals(covered)
        measures = {k: v for k, v in totals.items() if isinstance(v, dict)}
        primary = measures.get(config.COVERAGE_PRIMARY_MEASURE, {})
        summary["totalRegisteredPopulation"] = int(covered["registered_population"].sum())
        summary["totalRegisteredHouseholds"] = int(covered["registered_households"].sum())
        summary["totalRegisteredUnder5"] = int(covered["registered_under5"].sum())
        summary["totalPopulationGap"] = est_pop - summary["totalRegisteredPopulation"]
        summary["householdGap"] = est_hh - summary["totalRegisteredHouseholds"]
        summary["overallCoverageRatio"] = primary.get("coverage")
        if "official_target" in totals:
            summary["officialTarget"] = totals["official_target"]

        for classification, group in covered.groupby("gap_classification"):
            gap_distribution[classification] = {
                "count": int(len(group)),
                "population": int(group["estimated_population"].sum()),
            }

        ranked = covered.sort_values("under5_gap", ascending=False).head(10)
        for _, row in ranked.iterrows():
            score, priority = risk_lookup.get(row[CODE], (None, None))
            ratio = row["coverage_ratio"]
            top_gap.append({
                "name": row.get(config.BOUNDARY_NAME_FIELD) or row[CODE],
                "boundaryCode": row[CODE],
                "gap": int(row["under5_gap"]),
                "coverageRatio": None if pd.isna(ratio) else float(ratio),
                "riskScore": score,
                "riskPriority": priority,
            })

    summary.update(summary_extra or {})
    return {
        "summary": summary,
        "measures": measures,
        "gapDistribution": gap_distribution,
        "topGapSettlements": top_gap,
        "riskDistribution": risk_distribution,
    }


def build_provenance(resolution, upload, covered):
    """What the operator needs to trust - or distrust - this run's numbers."""
    block = {
        "coverageMeasure": config.COVERAGE_PRIMARY_MEASURE,
        "facilities": {
            "matched": resolution.matched_count,
            "pooled": {name: members for name, members in resolution.pooled.items()},
            "unmatchedInSheet": resolution.unmatched_source,
            "unmatchedInBoundaries": resolution.unmatched_roster,
            "suggestions": resolution.suggestions,
        },
    }
    if upload is not None:
        flags = upload.review_flags()
        block["cellsNeedingReview"] = flags
        block["cellsNeedingReviewCount"] = len(flags)
    if covered is not None:
        block["withheld"] = covered.attrs.get("withheld", {})
    return block


# --- CLI ----------------------------------------------------------------------

def _parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--iso3", help="country ISO3 (default from PDI_ISO3 / config)")
    parser.add_argument("--boundaries", help="uploaded catchment geojson (the analysis units)")
    parser.add_argument("--enumeration", help="uploaded enumeration workbook (.xlsx)")
    parser.add_argument("--sheet", help="facility coordinate sheet; cuts Voronoi cells instead")
    parser.add_argument("--with-country", action="store_true",
                        help="also compute the whole-country district layer. Off by default "
                             "when an upload defines the areas: it is the slow half of a run "
                             "and the dashboard shows the uploaded areas.")
    parser.add_argument("--measure", choices=list(coverage.MEASURES),
                        help="comparison driving classification (default from config)")
    parser.add_argument("--out", help="output workbook path")
    parser.add_argument("--json", help="write the result envelope (for the API)")
    parser.add_argument("--geojson", help="write the whole-country districts + targets")
    parser.add_argument("--catchments-geojson", help="write the catchment cells + coverage")
    parser.add_argument("--buildings-geojson", help="write catchment building points")
    parser.add_argument("--invisible-geojson", help="write invisible-settlement clusters")
    parser.add_argument("--stats-json", help="write the dashboard summary JSON")
    parser.add_argument("--groups", help="comma-separated target-group keys (default from config)")
    parser.add_argument("--household-size", type=float, help="average household size")
    parser.add_argument("--year", type=int, help="WorldPop population year")
    parser.add_argument("--no-buildings", action="store_true", help="skip Open Buildings")
    parser.add_argument("--no-risk", action="store_true", help="skip Feature 5 risk scoring")
    parser.add_argument("--persist", action="store_true",
                        help="upsert the results into the PostGIS tables the API reads from")
    parser.add_argument("--campaign-id", help="campaign identifier for the persisted gap report")
    parser.add_argument("--tenant-id", default=config.TENANT_ID, help="tenant identifier")
    parser.add_argument("--persist-buildings", action="store_true",
                        help="also persist individual building footprints (large; off by default)")
    return parser.parse_args()


def main():
    args = _parse_args()
    groups = [g.strip() for g in args.groups.split(",")] if args.groups else None
    with_buildings = not args.no_buildings

    # Catchment layer from the uploads - the areas the run is actually about.
    catch_targets, catch_units = compute_catchments(
        iso3=args.iso3, boundaries_path=args.boundaries, sheet_path=args.sheet,
        groups=groups, avg_household_size=args.household_size,
        with_buildings=with_buildings, year=args.year)

    # The whole-country pass is the expensive half of a run: WorldPop zonal statistics over
    # every district, plus a country-wide building clip. When an upload already defines the
    # areas of interest, that work produces nothing the dashboard shows, so it is skipped
    # unless explicitly asked for.
    if catch_units is not None and not args.with_country:
        targets, units = catch_targets, catch_units
    else:
        targets, units = compute(
            iso3=args.iso3, groups=groups, avg_household_size=args.household_size,
            with_buildings=with_buildings, year=args.year)

    upload = None
    anchors = None
    if args.boundaries:
        from sources.catchments import load_boundary_upload

        upload = load_boundary_upload(args.boundaries)
        anchors = upload.anchors

    # Enumeration is compared against whichever layer the upload actually describes.
    scope_units = catch_units if catch_units is not None else units
    covered, resolution = coverage_frame(
        scope_units, args.enumeration, args.measure, args.tenant_id,
        avg_household_size=args.household_size)
    risk = None if args.no_risk else detect_risk(scope_units, covered, anchors)
    invisible = detect_invisible(args.iso3)

    catchment_count = 0 if catch_units is None else int(len(catch_units))
    # When the country layer was skipped, `units` *is* the catchment layer, so the coverage
    # and risk overlays belong on it.
    base_is_scope = units is scope_units
    base_covered = covered if base_is_scope else None
    base_risk = risk if base_is_scope else None

    if args.persist:
        _persist(args, scope_units, covered, risk, invisible)

    out = None
    if args.out and args.enumeration:
        out, annotated = append_to_workbook(
            args.enumeration, args.out, scope_units, covered, risk, upload)
        out = str(Path(out).resolve())
        print(f"appended {len(APPENDED_COLUMNS)} columns to {annotated} rows of the workbook")

    geojson_path = None
    if args.geojson:
        geojson_path = str(write_geojson(
            units, targets, args.geojson, base_covered, base_risk).resolve())

    catchments_path = None
    if args.catchments_geojson and catch_units is not None:
        catchments_path = str(write_geojson(
            catch_units, catch_targets, args.catchments_geojson, covered, risk).resolve())

    buildings_path = None
    if args.buildings_geojson and catch_units is not None:
        footprints = catchment_buildings(catch_units, args.iso3) if with_buildings else None
        buildings_path = str(write_points_geojson(footprints, args.buildings_geojson).resolve())

    invisible_path = None
    if args.invisible_geojson and invisible is not None:
        invisible_path = str(write_points_geojson(invisible, args.invisible_geojson).resolve())

    provenance = build_provenance(resolution, upload, covered)

    stats_path = None
    if args.stats_json:
        stats = build_stats(scope_units, covered, risk)
        stats["provenance"] = provenance
        Path(args.stats_json).write_text(json.dumps(stats), encoding="utf-8")
        stats_path = str(Path(args.stats_json).resolve())

    if args.json:
        _write_envelope(args, targets, groups, covered, resolution, provenance, out,
                        geojson_path, catchments_path, buildings_path, invisible_path,
                        stats_path, catchment_count, risk, invisible)

    _report(targets, catchment_count, covered, resolution, risk, invisible, upload, out)


def _persist(args, units, covered, risk, invisible):
    campaign_id = args.campaign_id or config.CAMPAIGN_ID
    try:
        from persistence import store

        summary = store.persist(campaign_id, args.tenant_id, units, covered, risk, invisible)
        print(f"persisted {summary['boundaries']:,} boundaries "
              f"(campaign {campaign_id}, tenant {args.tenant_id})")
        if args.persist_buildings:
            written = store.persist_buildings(
                args.tenant_id, units.attrs.get("building_footprints"))
            print(f"persisted {written:,} building footprints")
    except Exception as exc:  # noqa: BLE001 - persistence is best-effort for the live tool
        print(f"WARNING persistence skipped: {exc}")


def _write_envelope(args, targets, groups, covered, resolution, provenance, out, geojson_path,
                    catchments_path, buildings_path, invisible_path, stats_path,
                    catchment_count, risk, invisible):
    records = targets.rename(columns={CODE: "boundaryCode"}).to_dict(orient="records")
    covered_records = []
    if covered is not None:
        columns = [CODE, "registered_population", "registered_under5", "registered_households"]
        covered_records = covered[columns].rename(
            columns={CODE: "boundaryCode"}).to_dict(orient="records")

    envelope = {
        "iso3": (args.iso3 or config.COUNTRY_ISO3).upper(),
        "count": int(len(targets)),
        "groups": groups or config.DEFAULT_TARGET_GROUPS,
        "sheet": out,
        "geojson": geojson_path,
        "targets": records,
        "registeredAvailable": covered is not None,
        "registered": covered_records,
        "invisibleAvailable": invisible is not None,
        "invisibleCount": 0 if invisible is None else int(len(invisible)),
        "invisibleGeojson": invisible_path,
        "catchmentsAvailable": catchment_count > 0,
        "catchmentCount": catchment_count,
        "catchmentsGeojson": catchments_path,
        "buildingsGeojson": buildings_path,
        "riskAvailable": risk is not None,
        "statsJson": stats_path,
        "provenance": provenance,
    }
    Path(args.json).write_text(json.dumps(envelope), encoding="utf-8")


def _report(targets, catchment_count, covered, resolution, risk, invisible, upload, out):
    if catchment_count:
        print(f"areas computed: {catchment_count:,} from the uploaded geojson")
    else:
        print(f"areas computed: {len(targets):,} districts (whole country)")
    if upload is not None:
        flags = upload.review_flags()
        print(f"cells needing review: {len(flags):,} of {catchment_count:,}")
    if covered is None:
        print("enumeration: none uploaded (coverage and risk layers omitted)")
    else:
        print(resolution.summary())
        summary = coverage.totals(covered)
        for measure, values in summary.items():
            if isinstance(values, dict):
                ratio = values["coverage"]
                print(f"  {measure:<12} {values['enumerated']:>9,} / {values['estimated']:>9,}"
                      f"  {'-' if ratio is None else f'{ratio:.1%}'}")
    if risk is not None:
        print(f"risk: scored {len(risk):,} units")
    print(f"invisible settlements: {'dormant (needs household GPS)' if invisible is None else len(invisible)}")
    if out:
        print(f"wrote workbook: {out}")


if __name__ == "__main__":
    main()
