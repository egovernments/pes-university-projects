"""Feature 2 - the gap report: what the field enumerated against what the engine estimates.

The comparison itself lives in :mod:`features.coverage`; this module is the batch entry
point that reads the estimation output from disk, joins the uploaded enumeration onto it,
and writes the report out.
"""

import argparse

import geopandas as gpd

import config
from features import coverage
from sources import enumeration as enumeration_source

CODE = config.BOUNDARY_CODE_FIELD

IDENTITY_COLUMNS = [CODE, "campaign_id", "msp_district", "msp_province", "microplan_district"]


def build(enumeration_path=None, boundaries_path=None, measure=None, tenant_id=None):
    """Return ``(table, gdf, resolution)``: the per-unit gap report and its name matching.

    ``enumeration_path`` is the uploaded workbook. Without one there is nothing to compare
    against, so every unit is classified ``NO_ENUMERATION`` rather than a misleading
    zero-coverage RED.
    """
    if not config.DISTRICT_POPULATION_GEOJSON.exists():
        raise FileNotFoundError(
            f"{config.DISTRICT_POPULATION_GEOJSON} not found - run features.estimation first")
    est = gpd.read_file(config.DISTRICT_POPULATION_GEOJSON)

    counts, resolution = load_enumeration_for(est, enumeration_path, boundaries_path)
    frame = coverage.compare(est, counts, measure=measure, tenant_id=tenant_id)
    frame["campaign_id"] = config.CAMPAIGN_ID

    columns = [c for c in IDENTITY_COLUMNS if c in frame.columns] + coverage.report_columns(frame)
    table = frame[columns].reset_index(drop=True)
    gdf = gpd.GeoDataFrame(
        table.merge(est[[CODE, "geometry"]], on=CODE), geometry="geometry",
        crs=config.STORAGE_CRS)
    return table, gdf, resolution


def load_enumeration_for(units, enumeration_path, boundaries_path=None):
    """``(counts, resolution)`` for ``units``, or ``(None, empty)`` when nothing was uploaded.

    The roster the sheet's names resolve onto is the set of unit codes, which for uploaded
    catchments are the facility names themselves.
    """
    from sources import facilities

    if not enumeration_source.has_enumeration(enumeration_path):
        return None, facilities.Resolution()
    roster = list(units[CODE].astype(str))
    return enumeration_source.load_enumeration(enumeration_path, roster)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--enumeration", help="uploaded enumeration workbook (.xlsx)")
    parser.add_argument("--boundaries", help="uploaded catchment geojson")
    parser.add_argument("--measure", choices=list(coverage.MEASURES),
                        help="comparison driving classification (default from config)")
    args = parser.parse_args()

    table, gdf, resolution = build(args.enumeration, args.boundaries, args.measure)
    config.GAP_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    table.to_csv(config.GAP_REPORT_CSV, index=False, encoding="utf-8-sig")
    gdf.to_file(config.GAP_REPORT_GEOJSON, driver="GeoJSON")

    print(resolution.summary())
    print(f"\nmeasure:                  {table['coverage_measure'].iloc[0]}")
    print(f"units in report:          {len(table):>12,}")
    counts = table["gap_classification"].value_counts()
    print("classification:")
    for label in ["GREEN", "YELLOW", "RED", "BLACK", "POOLED", "NOT_ENUMERATED", "NO_ENUMERATION"]:
        if counts.get(label):
            print(f"  {label:<16} {int(counts.get(label, 0)):>6}")

    summary = coverage.totals(table)
    if summary:
        print(f"\nenumerated vs estimated over {summary['units']} units:")
        for measure, values in summary.items():
            if not isinstance(values, dict):
                continue
            ratio = values["coverage"]
            print(f"  {measure:<12} {values['enumerated']:>9,} / {values['estimated']:>9,}"
                  f"  {'-' if ratio is None else f'{ratio:.1%}'}")

    print(f"\nWrote:\n  {config.GAP_REPORT_CSV}\n  {config.GAP_REPORT_GEOJSON}")


if __name__ == "__main__":
    main()
