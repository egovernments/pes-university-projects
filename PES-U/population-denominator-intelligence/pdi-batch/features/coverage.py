"""Enumerated counts against modelled estimates, compared measure by measure.

The uploaded sheet and the engine express population in different units, and only some
of them line up. This module keeps each comparison honest by pairing like with like:

``under5``
    children 0-59 months enumerated against WorldPop's under-5 band. Both are counts of
    children, both are directly measured, and it is the cohort a polio campaign plans
    against - so it is the default measure for classification.
``households``
    enumeration records against building footprints. Both are counts of dwellings.
``population``
    a derived comparison. The sheet holds no headcount of people, so the registered side
    is households scaled by the average household size; it is reported, and flagged
    derived, but is the weakest of the three.

A unit with no enumeration row is *not* zero-coverage. It is outside the campaign the
sheet describes, and says so (``NOT_ENUMERATED``) rather than colouring the map red.
"""

from datetime import datetime, timezone

import pandas as pd

import config

CODE = config.BOUNDARY_CODE_FIELD

# measure -> (enumerated column, estimated column, ratio column)
MEASURES = {
    "under5": ("registered_under5", "estimated_under5", "coverage_ratio_under5"),
    "households": ("registered_households", "estimated_households", "coverage_ratio_households"),
    "population": ("registered_population", "estimated_population", "coverage_ratio_population"),
}

ENUMERATION_COLUMNS = [
    "registered_population", "registered_under5", "registered_households",
    "population_is_derived", "official_population", "official_target",
    "active_user_pct", "absent_households", "users_total", "users_active",
    "is_pooled", "pooled_with",
]

REPORT_COLUMNS = [
    "estimated_population", "registered_population", "population_gap",
    "estimated_households", "registered_households", "household_gap",
    "estimated_under5", "registered_under5", "under5_gap",
    "coverage_ratio", "coverage_measure", "gap_classification",
    "coverage_ratio_under5", "coverage_ratio_households", "coverage_ratio_population",
    "official_population", "official_target", "official_vs_estimated",
    "population_is_derived", "is_pooled", "pooled_with",
    # Carried through because Feature 5 reads them back off the written gap report.
    "active_user_pct", "absent_households", "users_total", "users_active",
    "computed_at", "tenant_id",
]


def _ratio(enumerated, estimated):
    """Enumerated / estimated, left undefined where nothing is estimated."""
    return (enumerated / estimated).where(estimated > 0).round(4)


def classify(coverage_ratio, enumerated, building_count, enumerated_present=True, pooled=False):
    """Coverage band for one unit.

    ``NOT_ENUMERATED`` for units the upload never covered, ``POOLED`` where the sheet
    reported several facilities on one line and the split is unknowable, ``BLACK`` for a
    built-up unit the enumeration reached but recorded nothing in.
    """
    if not enumerated_present:
        return "NOT_ENUMERATED"
    if pooled:
        return "POOLED"
    if enumerated == 0:
        return "BLACK" if building_count > 0 else "RED"
    if coverage_ratio is None or pd.isna(coverage_ratio):
        return "RED"
    if coverage_ratio >= config.GAP_GREEN_THRESHOLD:
        return "GREEN"
    if coverage_ratio >= config.GAP_YELLOW_THRESHOLD:
        return "YELLOW"
    return "RED"


def compare(units, enumeration, measure=None, tenant_id=None):
    """Per-unit coverage table joining ``enumeration`` counts onto estimated ``units``.

    ``units`` is the estimation output (``population_estimate``, ``under5``,
    ``building_count``, ``estimated_households``) keyed by boundary code. ``enumeration``
    is the loader's frame keyed by ``facility_name``, which for an uploaded catchment is
    the same value as the boundary code. Pass ``enumeration=None`` when no sheet was
    uploaded: every unit then reports ``NO_ENUMERATION`` instead of a misleading zero.
    """
    measure = measure or config.COVERAGE_PRIMARY_MEASURE
    frame = units.drop(columns="geometry") if "geometry" in units.columns else units.copy()
    frame = frame.copy()

    frame["estimated_population"] = frame["population_estimate"].round().astype(int)
    frame["estimated_under5"] = frame["under5"].round().astype(int)
    frame["estimated_households"] = frame["estimated_households"].astype(int)

    if enumeration is None:
        for column in ENUMERATION_COLUMNS:
            frame[column] = pd.NA
        frame["gap_classification"] = "NO_ENUMERATION"
        enumerated_present = pd.Series(False, index=frame.index)
    else:
        counts = enumeration.rename(columns={"facility_name": CODE})
        keep = [CODE] + [c for c in ENUMERATION_COLUMNS if c in counts.columns]
        frame = frame.merge(counts[keep], on=CODE, how="left")
        enumerated_present = frame["registered_households"].notna()
        for column in ["registered_population", "registered_under5", "registered_households"]:
            frame[column] = frame[column].fillna(0).astype(int)
        frame["is_pooled"] = frame["is_pooled"].fillna(False).astype(bool)

    for _, (enumerated, estimated, ratio) in MEASURES.items():
        frame[ratio] = _ratio(frame[enumerated], frame[estimated]) if enumeration is not None \
            else pd.NA

    frame["population_gap"] = frame["estimated_population"] - frame["registered_population"]
    frame["household_gap"] = frame["estimated_households"] - frame["registered_households"]
    frame["under5_gap"] = frame["estimated_under5"] - frame["registered_under5"]

    # How far the microplan's own denominator sits from the modelled one - the number the
    # campaign was budgeted against versus the number the engine derives for the same area.
    frame["official_vs_estimated"] = _ratio(
        pd.to_numeric(frame.get("official_target"), errors="coerce"), frame["estimated_under5"])

    frame["coverage_measure"] = measure
    frame["coverage_ratio"] = frame[MEASURES[measure][2]]
    if enumeration is not None:
        enumerated_column = MEASURES[measure][0]
        frame["gap_classification"] = [
            classify(row["coverage_ratio"], row[enumerated_column], row["building_count"],
                     enumerated_present.iloc[position], bool(row["is_pooled"]))
            for position, (_, row) in enumerate(frame.iterrows())
        ]

    frame["computed_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    frame["tenant_id"] = tenant_id or config.TENANT_ID
    return frame


def report_columns(frame):
    """``REPORT_COLUMNS`` restricted to what ``frame`` actually carries."""
    return [column for column in REPORT_COLUMNS if column in frame.columns]


def totals(frame):
    """Campaign-wide roll-up over the units an enumeration actually reached."""
    covered = frame[~frame["gap_classification"].isin(["NO_ENUMERATION", "NOT_ENUMERATED"])]
    if covered.empty:
        return {}
    summary = {"units": int(len(covered))}
    for measure, (enumerated, estimated, _) in MEASURES.items():
        enumerated_total = int(covered[enumerated].sum())
        estimated_total = int(covered[estimated].sum())
        summary[measure] = {
            "enumerated": enumerated_total,
            "estimated": estimated_total,
            "coverage": round(enumerated_total / estimated_total, 4) if estimated_total else None,
        }
    if covered["official_target"].notna().any():
        summary["official_target"] = int(
            pd.to_numeric(covered["official_target"], errors="coerce").fillna(0).sum())
    return summary
