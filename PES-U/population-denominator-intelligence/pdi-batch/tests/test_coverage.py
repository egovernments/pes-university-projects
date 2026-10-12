import geopandas as gpd
import pandas as pd
import pytest
from shapely.geometry import box

import config
from features import coverage

CODE = config.BOUNDARY_CODE_FIELD


@pytest.mark.parametrize(
    "coverage_ratio, enumerated, building_count, expected",
    [
        (0.90, 900, 200, "GREEN"),                          # above the green threshold
        (config.GAP_GREEN_THRESHOLD, 850, 200, "GREEN"),    # exactly at the green cut
        (0.70, 700, 200, "YELLOW"),                         # between yellow and green
        (config.GAP_YELLOW_THRESHOLD, 500, 200, "YELLOW"),  # exactly at the yellow cut
        (0.30, 300, 200, "RED"),                            # below yellow, still enumerated
        (0.0, 0, 200, "BLACK"),                             # nothing found, but built up
        (0.0, 0, 0, "RED"),                                 # nothing found, nothing built
        (None, 0, 5, "BLACK"),                              # undefined coverage, buildings present
    ],
)
def test_classify(coverage_ratio, enumerated, building_count, expected):
    assert coverage.classify(coverage_ratio, enumerated, building_count) == expected


def test_classify_distinguishes_not_enumerated_from_zero_coverage():
    """A unit the upload never covered is out of scope, not a failure."""
    assert coverage.classify(None, 0, 500, enumerated_present=False) == "NOT_ENUMERATED"
    # The same unit, had the enumeration reached it and found nothing, is BLACK.
    assert coverage.classify(None, 0, 500, enumerated_present=True) == "BLACK"


def test_classify_pooled_is_not_assessable():
    assert coverage.classify(0.9, 900, 200, pooled=True) == "POOLED"


def _units():
    return gpd.GeoDataFrame(
        {
            CODE: ["A", "B"],
            "population_estimate": [1000, 500],
            "estimated_households": [200, 100],
            "building_count": [200, 100],
            "under5": [200, 100],
            "geometry": [box(0, 0, 1, 1), box(2, 2, 3, 3)],
        },
        geometry="geometry", crs=config.STORAGE_CRS,
    )


def _enumeration(**overrides):
    frame = pd.DataFrame({
        "facility_name": ["A"],
        "registered_households": [160],
        "registered_under5": [170],
        "registered_population": [864],
        "population_is_derived": [True],
        "official_target": [250],
        "is_pooled": [False],
    })
    return frame.assign(**overrides)


def test_compare_without_enumeration_reports_no_enumeration():
    frame = coverage.compare(_units(), None)
    assert set(frame["gap_classification"]) == {"NO_ENUMERATION"}
    assert frame["coverage_ratio"].isna().all()


def test_compare_pairs_like_with_like():
    frame = coverage.compare(_units(), _enumeration()).set_index(CODE)
    row = frame.loc["A"]
    assert row["coverage_ratio_under5"] == pytest.approx(170 / 200)
    assert row["coverage_ratio_households"] == pytest.approx(160 / 200)
    assert row["coverage_ratio_population"] == pytest.approx(864 / 1000)


def test_compare_headline_ratio_follows_the_configured_measure():
    frame = coverage.compare(_units(), _enumeration(), measure="households").set_index(CODE)
    assert frame.loc["A", "coverage_measure"] == "households"
    assert frame.loc["A", "coverage_ratio"] == frame.loc["A", "coverage_ratio_households"]


def test_unit_with_no_enumeration_row_is_out_of_scope_not_red():
    # B has buildings but the upload never described it - it must not read as a failure.
    frame = coverage.compare(_units(), _enumeration()).set_index(CODE)
    assert frame.loc["B", "gap_classification"] == "NOT_ENUMERATED"


def test_official_target_compared_against_the_estimate():
    frame = coverage.compare(_units(), _enumeration()).set_index(CODE)
    assert frame.loc["A", "official_vs_estimated"] == pytest.approx(250 / 200)


def test_totals_exclude_units_the_enumeration_never_reached():
    frame = coverage.compare(_units(), _enumeration())
    totals = coverage.totals(frame)
    assert totals["units"] == 1
    assert totals["under5"]["enumerated"] == 170
    assert totals["under5"]["estimated"] == 200
    assert totals["official_target"] == 250
