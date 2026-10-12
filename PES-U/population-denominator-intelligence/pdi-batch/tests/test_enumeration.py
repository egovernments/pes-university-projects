import pandas as pd
import pytest

import config
from sources import enumeration

ROSTER = ["CS ABENA", "CS GUELMATE", "CS ORHAN TOPAL"]

FACILITY_HEADER = [
    "S. No.", "Health Center", "Population totale", "Cible",
    "Nombre d'utilisateurs par installation", "Utilisateurs actifs par établissement",
    "Nombre total d'enregistrements de recensement", "Enfants admissibles énumérés",
    "Nombre de membres invités", "Nombre de ménages absents",
]


def _facility_sheet(rows, title="Until June 4 (today's) Data at 18:40"):
    """A sheet shaped like the real workbook: a title row, then the header, then data."""
    width = len(FACILITY_HEADER)
    padded = [[title] + [None] * (width - 1), FACILITY_HEADER]
    padded.extend(row + [None] * (width - len(row)) for row in rows)
    return pd.DataFrame(padded)


def _write(tmp_path, sheets):
    path = tmp_path / "enumeration.xlsx"
    with pd.ExcelWriter(path) as writer:
        for name, frame in sheets.items():
            frame.to_excel(writer, sheet_name=name, index=False, header=False)
    return path


@pytest.fixture
def workbook(tmp_path):
    rows = [
        [None, "TOTAL", 116291, 25720, 72, 31, 3000, 1100, 55, 47],
        [1, "CS ABENA", 95558, 19040, 51, 27, 2219, 878, 55, 46],
        [2, "CS GUELMATE**", 20733, 6680, 21, 0, 0, 0, 0, 0],
        [3, "CS ORHAN TOPAL**", 68236, 13596, 21, 0, 0, 0, 0, 0],
        [None, "**CS ORHaN TOPAL+CS GUELMATE", None, None, None, None, 781, 222, 24, 1],
    ]
    return _write(tmp_path, {"Enumeration by Health Facility": _facility_sheet(rows)})


def test_loads_counts_past_the_title_row(workbook):
    frame, resolution = enumeration.load_enumeration(workbook, ROSTER)
    assert resolution.matched_count == 3
    abena = frame.set_index("facility_name").loc["CS ABENA"]
    assert abena["registered_households"] == 2219      # census records
    assert abena["registered_under5"] == 878           # eligible children
    assert abena["official_target"] == 19040           # microplan cible


def test_total_row_is_not_a_facility(workbook):
    frame, _ = enumeration.load_enumeration(workbook, ROSTER)
    assert "TOTAL" not in set(frame["facility_name"])
    assert len(frame) == 3


def test_population_is_derived_from_households(workbook):
    # The sheet has no headcount of people, so this is households x average size and
    # must say so - it is the one figure that is not directly measured.
    frame, _ = enumeration.load_enumeration(workbook, ROSTER)
    abena = frame.set_index("facility_name").loc["CS ABENA"]
    assert abena["registered_population"] == round(2219 * config.AVG_HOUSEHOLD_SIZE)
    assert bool(abena["population_is_derived"]) is True


def test_derived_population_follows_the_run_household_size(workbook):
    # The estimate honours the household size the run was submitted with, so the
    # registered side has to as well. Scaling the two at different sizes silently puts
    # numerator and denominator of every population coverage ratio in different units.
    frame, _ = enumeration.load_enumeration(workbook, ROSTER, avg_household_size=6.0)
    abena = frame.set_index("facility_name").loc["CS ABENA"]
    assert abena["registered_population"] == round(2219 * 6.0)
    assert abena["registered_population"] != round(2219 * config.AVG_HOUSEHOLD_SIZE)


def test_active_user_percentage(workbook):
    frame, _ = enumeration.load_enumeration(workbook, ROSTER)
    abena = frame.set_index("facility_name").loc["CS ABENA"]
    assert abena["active_user_pct"] == pytest.approx(27 / 51, abs=1e-4)


def test_pooled_row_withheld_not_split(workbook):
    """The default policy attributes a pooled row to neither member."""
    frame, resolution = enumeration.load_enumeration(workbook, ROSTER, pooled_policy="flag")
    pooled = frame[frame["is_pooled"]]
    assert set(pooled["facility_name"]) == {"CS GUELMATE", "CS ORHAN TOPAL"}
    assert (pooled["registered_households"] == 0).all()
    # The withheld counts are reported so the shortfall against the sheet's own TOTAL
    # is explained rather than silently missing.
    assert frame.attrs["withheld"]["registered_households"] == 781
    assert frame.attrs["withheld"]["registered_under5"] == 222
    assert resolution.pooled


def test_pooled_row_apportioned_by_official_target(workbook):
    frame, _ = enumeration.load_enumeration(workbook, ROSTER, pooled_policy="apportion")
    by_name = frame.set_index("facility_name")
    share = 13596 / (13596 + 6680)
    assert by_name.loc["CS ORHAN TOPAL", "registered_households"] == round(781 * share)
    # Apportioning consumes the pooled row, so nothing is left withheld.
    assert frame.attrs["withheld"] == {}


def test_per_enumerator_sheet_is_skipped(tmp_path):
    """A per-user sheet is ignored: its headers are shifted in the reference file, and
    it holds nothing the facility sheets lack."""
    per_user = pd.DataFrame([
        ["Until June 4 (today's) Data at 18:40", None, None, None],
        ["S. No.", "Health Center", "ID de l'utilisateur", "Enfants admissibles énumérés"],
        [1, "CS ABENA", "AB-01", 134],
    ])
    facility = _facility_sheet([[1, "CS ABENA", 95558, 19040, 51, 27, 2219, 878, 55, 46]])
    path = _write(tmp_path, {"By Facility": facility, "By User": per_user})

    frame, _ = enumeration.load_enumeration(path, ROSTER)
    # 2219 is the facility sheet's census count; 134 would mean the user sheet leaked in.
    assert frame.set_index("facility_name").loc["CS ABENA", "registered_households"] == 2219


def test_later_snapshot_supersedes_earlier(tmp_path):
    """Two snapshots of one campaign: counts take the larger, denominators the first."""
    early = _facility_sheet([[1, "CS ABENA", 95558, 19040, 51, 26, 2215, 876, 54, 46]],
                            title="Data at 15:00")
    late = _facility_sheet([[1, "CS ABENA", None, None, 51, 27, 2219, 878, 55, 46]],
                           title="Data at 18:40")
    path = _write(tmp_path, {"15h": early, "18h": late})

    frame, _ = enumeration.load_enumeration(path, ROSTER)
    abena = frame.set_index("facility_name").loc["CS ABENA"]
    assert abena["registered_households"] == 2219   # the later, larger count
    assert abena["official_target"] == 19040        # carried from the sheet that has it


def test_name_column_found_by_matching_the_roster_not_the_header(tmp_path):
    """A sheet with unrecognisable headers still joins, because the area names give it away."""
    frame = pd.DataFrame([
        ["Colonne 1", "Colonne 2", "Colonne 3"],
        ["nord", "CS ABENA", 42],
        ["sud", "CS GUELMATE", 17],
    ])
    path = _write(tmp_path, {"Feuille": frame})

    loaded, resolution = enumeration.load_enumeration(path, ROSTER)
    assert set(loaded["facility_name"]) == {"CS ABENA", "CS GUELMATE"}
    assert resolution.matched_count == 2


def test_unrecognised_columns_survive_for_write_back(tmp_path):
    frame = pd.DataFrame([
        ["Health Center", "Vaccinateurs prevus"],
        ["CS ABENA", 76],
    ])
    path = _write(tmp_path, {"Feuille": frame})

    loaded, _ = enumeration.load_enumeration(path, ROSTER)
    carried = [c for c in loaded.columns if c.startswith(enumeration.PASSTHROUGH_PREFIX)]
    assert any("Vaccinateurs" in column for column in carried)


def test_rejects_a_workbook_with_no_recognisable_area_column(tmp_path):
    path = _write(tmp_path, {"Sheet1": pd.DataFrame([["a", "b"], [1, 2]])})
    with pytest.raises(ValueError, match="no sheet in this workbook has a column of area names"):
        enumeration.load_enumeration(path, ROSTER)


def test_has_enumeration():
    assert enumeration.has_enumeration("x.xlsx") is True
    assert enumeration.has_enumeration(None) is False
    assert enumeration.has_enumeration("  ") is False
