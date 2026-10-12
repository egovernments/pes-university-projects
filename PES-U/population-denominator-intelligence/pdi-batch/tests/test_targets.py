import geopandas as gpd
import pandas as pd
from shapely.geometry import box

import config
from features import targets

CODE = config.BOUNDARY_CODE_FIELD


def _units():
    return gpd.GeoDataFrame(
        {
            CODE: ["A", "B"],
            config.BOUNDARY_NAME_FIELD: ["Alpha", "Beta"],
            "population_estimate": [1000, 500],
            "estimated_households": [200, 100],
            "building_count": [200, 0],
            "under5": [200, 100],
            "total": [1000, 500],
            "geometry": [box(0, 0, 1, 1), box(2, 2, 3, 3)],
        },
        geometry="geometry",
        crs=config.STORAGE_CRS,
    )


def _covered(**overrides):
    """A coverage frame as features.coverage.compare would produce it."""
    frame = pd.DataFrame({
        CODE: ["A", "B"],
        "estimated_population": [1000, 500],
        "estimated_households": [200, 100],
        "estimated_under5": [200, 100],
        "registered_population": [850, 0],
        "registered_under5": [170, 0],
        "registered_households": [160, 0],
        "under5_gap": [30, 100],
        "coverage_ratio": [0.85, pd.NA],
        "coverage_measure": ["under5", "under5"],
        "gap_classification": ["GREEN", "RED"],
        "official_target": [pd.NA, pd.NA],
    })
    return frame.assign(**overrides)


def test_coverage_frame_is_none_without_an_enumeration_upload():
    # No workbook uploaded: the coverage and risk layers are simply absent, and the
    # estimation layers still render.
    frame, resolution = targets.coverage_frame(_units(), enumeration_path=None)
    assert frame is None
    assert resolution.matched_count == 0


def test_build_stats_without_enumeration_is_estimation_only():
    stats = targets.build_stats(_units(), None, None)
    summary = stats["summary"]
    assert summary["totalEstimatedPopulation"] == 1500
    assert summary["totalRegisteredPopulation"] == 0
    assert summary["totalPopulationGap"] == 1500
    assert summary["overallCoverageRatio"] is None
    assert stats["gapDistribution"] == {}
    assert stats["riskDistribution"] == {}


def test_build_stats_headline_coverage_uses_the_primary_measure():
    stats = targets.build_stats(_units(), _covered(), None)
    summary = stats["summary"]
    assert summary["coverageMeasure"] == "under5"
    # Under-5, not population: 170 children found against 300 estimated.
    assert summary["overallCoverageRatio"] == round(170 / 300, 4)
    assert summary["totalRegisteredUnder5"] == 170
    assert summary["populationIsDerived"] is True


def test_build_stats_reports_every_measure_side_by_side():
    stats = targets.build_stats(_units(), _covered(), None)
    measures = stats["measures"]
    assert measures["households"]["enumerated"] == 160
    assert measures["under5"]["enumerated"] == 170
    assert measures["population"]["enumerated"] == 850


def test_build_stats_ranks_by_under5_gap():
    stats = targets.build_stats(_units(), _covered(), None)
    assert stats["topGapSettlements"][0]["boundaryCode"] == "B"
    assert stats["gapDistribution"]["GREEN"] == {"count": 1, "population": 1000}


def test_build_stats_marks_feature_four_dormant():
    stats = targets.build_stats(_units(), _covered(), None)
    assert stats["summary"]["invisibleEnabled"] is config.INVISIBLE_ENABLED
    assert stats["summary"]["invisibleSettlementCount"] == 0


def test_detect_invisible_is_dormant_without_household_points():
    # Feature 4 needs household GPS the aggregate workbook cannot supply.
    assert config.INVISIBLE_ENABLED is False
    assert targets.detect_invisible(iso3="TCD") is None


def test_detect_risk_is_none_without_coverage():
    assert targets.detect_risk(_units(), None) is None
