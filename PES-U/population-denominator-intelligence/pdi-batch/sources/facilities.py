"""Facility name resolution between the uploaded boundary geojson and enumeration sheet.

The two uploads share no identifier column. The only join key is a facility name typed
by hand in French, and the same facility is spelled differently in each file
(``GUELMADE`` / ``GUELMATE``, ``HOREB`` / ``EVENGELIQUE HOREB``). Every downstream number
is joined on this key, so a name that fails to resolve is a facility silently dropped
from the denominator - this module reports those instead, in both directions.

The boundary geojson is the roster: its spelling wins, and enumeration rows resolve onto it.
"""

import difflib
import re
import unicodedata
from dataclasses import dataclass, field

import config

_FOOTNOTE = re.compile(r"[*†‡]+")
_PARENTHETICAL = re.compile(r"\([^)]*\)")
_NON_ALPHANUMERIC = re.compile(r"[^A-Z0-9]+")


def normalize(name):
    """Canonical key for a facility name: accent-free, prefix-free, uppercase.

    Strips footnote markers, diacritics, a leading facility-type prefix (``CS``,
    ``CSI``, ...) and any parenthetical qualifier, then folds punctuation to single
    spaces. ``"CS MANDJAFA (NDJAMENA-SUD)"`` and ``"MANDJAFA"`` both land on ``MANDJAFA``.
    """
    if name is None:
        return ""
    text = _FOOTNOTE.sub("", str(name))
    text = unicodedata.normalize("NFKD", text)
    text = "".join(char for char in text if not unicodedata.combining(char))
    text = _PARENTHETICAL.sub(" ", text.upper())
    text = _NON_ALPHANUMERIC.sub(" ", text).strip()
    for prefix in config.FACILITY_NAME_PREFIXES:
        if text.startswith(prefix + " "):
            text = text[len(prefix) + 1:].strip()
            break
    return config.FACILITY_ALIASES.get(text, text)


def split_pooled(name):
    """Facility keys carried by one row, splitting rows that pool several facilities.

    A sheet may report two facilities on a single line (``CS ORHAN TOPAL+CS GUELMATE``)
    when their enumeration could not be separated. Such a row resolves to every member,
    and callers must decide how to apportion it rather than assign it to one facility.
    """
    parts = [part for part in re.split(r"\s*[+/&]\s*", str(name or "")) if part.strip()]
    keys = [key for key in (normalize(part) for part in parts) if key]
    # Preserve order while dropping repeats, so "X + X" collapses to a single member.
    return list(dict.fromkeys(keys))


@dataclass
class Resolution:
    """Outcome of matching source names onto the roster.

    ``mapping`` holds the clean one-to-one matches. ``pooled`` holds source rows that
    cover several roster facilities at once. ``unmatched_source`` and ``unmatched_roster``
    are the two failure directions, each carrying a suggestion for the operator - never
    applied automatically, because a wrong auto-match corrupts a denominator silently.
    """

    mapping: dict = field(default_factory=dict)
    pooled: dict = field(default_factory=dict)
    unmatched_source: list = field(default_factory=list)
    unmatched_roster: list = field(default_factory=list)
    suggestions: dict = field(default_factory=dict)

    @property
    def matched_count(self):
        return len(self.mapping)

    def summary(self):
        """One-line status for the engine log and the API's provenance block."""
        return (f"facilities: {self.matched_count} matched, {len(self.pooled)} pooled, "
                f"{len(self.unmatched_source)} unmatched in sheet, "
                f"{len(self.unmatched_roster)} roster facilities with no enumeration")


def resolve(source_names, roster_names):
    """Match ``source_names`` (enumeration rows) onto ``roster_names`` (geojson facilities).

    Returns a :class:`Resolution`. Matching is exact on the normalized key; close names
    that do not match are reported as suggestions rather than joined, so an operator
    decides whether ``GUELMADE`` and ``GUELMATE`` are the same place.
    """
    roster = {}
    for name in roster_names:
        key = normalize(name)
        if key:
            roster.setdefault(key, name)

    result = Resolution(unmatched_roster=list(roster.values()))
    seen = set()

    for name in source_names:
        members = split_pooled(name)
        if not members:
            continue
        known = [key for key in members if key in roster]
        if len(members) > 1 and known:
            result.pooled[name] = [roster[key] for key in known]
            seen.update(known)
        elif len(members) == 1 and known:
            result.mapping[name] = roster[known[0]]
            seen.update(known)
        else:
            result.unmatched_source.append(name)
            close = difflib.get_close_matches(members[0], roster.keys(), n=1, cutoff=0.75)
            if close:
                result.suggestions[name] = roster[close[0]]

    result.unmatched_roster = [roster[key] for key in roster if key not in seen]
    return result
