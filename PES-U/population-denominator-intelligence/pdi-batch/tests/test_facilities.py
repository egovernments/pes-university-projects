import pytest

import config

from sources import facilities

# The boundary geojson's spellings - the roster everything else resolves onto.
ROSTER = [
    "CS ABENA", "CS AL-MANSOUR", "CS CEPHAS", "CS EVENGELIQUE HOREB",
    "CS GUELMATE", "CS MANDJAFA (NDJAMENA-SUD)", "CS ORHAN TOPAL",
]


@pytest.mark.parametrize(
    "raw, expected",
    [
        ("CS ABENA", "ABENA"),                       # facility-type prefix dropped
        ("abena", "ABENA"),                          # case folded
        ("CS MANDJAFA (NDJAMENA-SUD)", "MANDJAFA"),  # parenthetical qualifier dropped
        ("CS GUELMATE**", "GUELMATE"),               # footnote marker dropped
        ("CS  ORDRE  DE   MALTE", "ORDRE DE MALTE"), # runs of spaces collapsed
        ("CENTRE DE SANTE ABENA", "ABENA"),          # longest prefix wins over "CS"
        ("Énumération", "ENUMERATION"),              # diacritics folded
        (None, ""),
    ],
)
def test_normalize(raw, expected):
    assert facilities.normalize(raw) == expected


def test_normalisation_alone_resolves_ordinary_spelling_differences():
    # No alias table needed: dropping the prefix and the parenthetical is enough.
    resolution = facilities.resolve(["MANDJAFA", "abena"], ROSTER)
    assert resolution.mapping == {
        "MANDJAFA": "CS MANDJAFA (NDJAMENA-SUD)",
        "abena": "CS ABENA",
    }


def test_no_aliases_are_configured_by_default():
    # Deployment-specific aliases must be opted into. A shipped default would silently
    # merge two real areas for every other country that used the same words.
    assert config.FACILITY_ALIASES == {}


def test_a_configured_alias_is_applied(monkeypatch):
    monkeypatch.setattr(config, "FACILITY_ALIASES", {"GUELMADE": "GUELMATE"})
    resolution = facilities.resolve(["GUELMADE"], ROSTER)
    assert resolution.mapping == {"GUELMADE": "CS GUELMATE"}


def test_without_the_alias_the_near_miss_is_only_suggested(monkeypatch):
    monkeypatch.setattr(config, "FACILITY_ALIASES", {})
    resolution = facilities.resolve(["GUELMADE"], ROSTER)
    assert resolution.mapping == {}
    assert resolution.suggestions == {"GUELMADE": "CS GUELMATE"}


def test_pooled_row_maps_to_every_member():
    # One sheet row reporting two facilities whose enumeration could not be separated.
    row = "**CS ORHaN TOPAL+CS GUELMATE"
    resolution = facilities.resolve([row], ROSTER)
    assert resolution.pooled == {row: ["CS ORHAN TOPAL", "CS GUELMATE"]}
    # A pooled row is not a match: neither facility gets the counts attributed to it.
    assert resolution.mapping == {}


def test_split_pooled_collapses_repeats():
    assert facilities.split_pooled("CS ABENA + CS ABENA") == ["ABENA"]


def test_unmatched_reported_in_both_directions():
    resolution = facilities.resolve(["CS ABENA", "CS NOWHERE"], ROSTER)
    assert resolution.mapping == {"CS ABENA": "CS ABENA"}
    assert resolution.unmatched_source == ["CS NOWHERE"]
    # Every roster facility with no sheet row is surfaced too, so a facility cannot
    # silently drop out of the denominator.
    assert "CS CEPHAS" in resolution.unmatched_roster


def test_near_miss_is_suggested_not_joined():
    # A close name is reported for a human to confirm; joining it automatically would
    # merge two facilities' numbers on a guess.
    resolution = facilities.resolve(["CS ABENAA"], ROSTER)
    assert resolution.mapping == {}
    assert resolution.suggestions == {"CS ABENAA": "CS ABENA"}


def test_summary_counts_every_outcome():
    resolution = facilities.resolve(["CS ABENA", "CS NOWHERE"], ROSTER)
    summary = resolution.summary()
    assert "1 matched" in summary
    assert "1 unmatched in sheet" in summary
