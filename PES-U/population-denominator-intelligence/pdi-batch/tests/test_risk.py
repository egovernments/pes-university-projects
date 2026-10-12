import pandas as pd
import pytest

import config
from features import risk


@pytest.mark.parametrize(
    "score, expected",
    [
        (100, "CRITICAL"),
        (config.RISK_CRITICAL_THRESHOLD, "CRITICAL"),
        (config.RISK_CRITICAL_THRESHOLD - 1, "HIGH"),
        (config.RISK_HIGH_THRESHOLD, "HIGH"),
        (config.RISK_MEDIUM_THRESHOLD, "MEDIUM"),
        (config.RISK_MEDIUM_THRESHOLD - 1, "LOW"),
        (0, "LOW"),
    ],
)
def test_priority_for(score, expected):
    assert risk.priority_for(score) == expected


def test_normalize_scales_to_unit_range():
    scaled = risk._normalize(pd.Series([10.0, 20.0, 30.0]))
    assert list(scaled) == [0.0, 0.5, 1.0]


def test_normalize_constant_series_is_zero():
    scaled = risk._normalize(pd.Series([5.0, 5.0, 5.0]))
    assert list(scaled) == [0.0, 0.0, 0.0]


def test_weights_sum_to_one():
    assert sum(config.RISK_WEIGHTS.values()) == pytest.approx(1.0)


def _factors(provisional=(), context=None):
    scores = pd.Series({name: 0.5 for name in config.RISK_WEIGHTS})
    return risk._factors_json(
        scores, config.RISK_WEIGHTS, set(provisional),
        building_density=12.0, distance_km=3.0,
        context=context or {"active_user_pct": 0.53, "absent_households": 46})


def test_factors_json_reports_each_factor_input():
    factors = _factors()
    assert factors["building_density"]["buildings_per_km2"] == 12.0
    assert factors["facility_distance"]["distance_to_nearest_km"] == 3.0
    # The two factors that used to be hardcoded now carry the value they were read from.
    assert factors["past_performance"]["active_user_pct"] == 0.53
    assert factors["missed_children"]["absent_households"] == 46


def test_factors_are_not_provisional_when_the_sheet_supplies_them():
    factors = _factors()
    assert factors["past_performance"]["provisional"] is False
    assert factors["missed_children"]["provisional"] is False
    assert factors["population_gap"]["provisional"] is False


def test_factors_marked_provisional_when_the_column_is_absent():
    factors = _factors(provisional=("past_performance", "missed_children"),
                       context={"active_user_pct": None, "absent_households": None})
    assert factors["past_performance"]["provisional"] is True
    assert factors["missed_children"]["provisional"] is True
    assert factors["past_performance"]["active_user_pct"] is None


def _frame(**overrides):
    frame = pd.DataFrame({
        "registered_households": [100.0, 100.0],
        "active_user_pct": [0.25, 1.0],
        "absent_households": [0.0, 100.0],
    })
    return frame.assign(**overrides)


def test_past_performance_rises_as_engagement_falls():
    scores = risk._past_performance(_frame())
    # A quarter of users active is the riskier facility.
    assert scores.iloc[0] == pytest.approx(0.75)
    assert scores.iloc[1] == pytest.approx(0.0)


def test_missed_children_is_the_absent_share_of_visited_households():
    scores = risk._missed_children(_frame())
    assert scores.iloc[0] == pytest.approx(0.0)
    assert scores.iloc[1] == pytest.approx(100 / 200)


def test_factors_fall_back_when_the_column_is_missing_entirely():
    empty = pd.DataFrame({"registered_households": [100.0]})
    assert risk._past_performance(empty) is None
    assert risk._missed_children(empty) is None
