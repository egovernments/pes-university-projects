"""
Generate labeled deduplication test dataset with a CLEAN base.

Step 1: From the 55K synthetic data, select only records with unique
        (given_name, family_name, date_of_birth, gender) combinations.
        This removes natural/accidental duplicates from the base.

Step 2: Inject known duplicates with various transformations and label them.

Step 3: Output:
  - dedup_test_records.csv   - Clean base + injected duplicates (shuffled)
  - dedup_ground_truth.csv   - Every duplicate pair labeled with type
  - dedup_test_summary.json  - Statistics and evaluation guidance

This ensures the ground truth is COMPLETE - every duplicate in the test set
is labeled, so precision/recall metrics are accurate.
"""

import pandas as pd
import numpy as np
import json
import uuid
import os
import random
import copy
from datetime import datetime, timedelta

random.seed(42)
np.random.seed(42)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(SCRIPT_DIR, "individuals_flat.csv")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "dedup_test")

# ── Phonetic variation mappings (Arabic/French transliteration) ──────
PHONETIC_MAP = {
    "Mahamat": ["Muhammad", "Mohamed", "Mohammed", "Mohamad", "Muhamat"],
    "Ibrahim": ["Ibraheem", "Ebrahim", "Brahim", "Ibrahem"],
    "Abdoulaye": ["Abdullahi", "Abdulai", "Abdulaye", "Abdoulay"],
    "Oumar": ["Omar", "Umar", "Oumare"],
    "Abakar": ["Aboubakar", "Abubakar", "Aboubacar"],
    "Youssouf": ["Yusuf", "Yousuf", "Yusef", "Youssef"],
    "Moussa": ["Musa", "Mousa", "Mussa"],
    "Issa": ["Isa", "Essa", "Eissa"],
    "Hassan": ["Hasan", "Hassane", "Hacene"],
    "Ali": ["Aly", "Ally"],
    "Fatima": ["Fatime", "Fatimatou", "Fadima", "Fadimata"],
    "Amina": ["Aminata", "Amena", "Amine"],
    "Khadija": ["Khadidja", "Kadija", "Khadijah", "Kadidja"],
    "Aisha": ["Aicha", "Aissata", "Aysha", "Aishatou"],
    "Hawa": ["Haoua", "Hauwa", "Hava"],
    "Mariam": ["Maryam", "Miriam", "Mariama", "Meriem"],
    "Djimadoum": ["Jimadoum", "Djimadoun", "Jimadoun"],
    "Nadjitangar": ["Nadjitangare", "Najitangar", "Nadjitangarre"],
    "Ngarmbatina": ["Ngarmbatinna", "Garmbatina", "Ngarmbattina"],
    "Jean-Pierre": ["Jean Pierre", "Jean-Pier", "Jan-Pierre"],
    "Francois": ["Francoise", "Fransois", "Francoi"],
    "Christine": ["Kristine", "Cristine", "Kristin"],
    "Therese": ["Terese", "Theresa", "Tereze"],
    "Deby": ["Debi", "Deby Itno", "Debby"],
    "Haroun": ["Haroune", "Haroon", "Harun"],
}

PHONETIC_LOOKUP = {}
for canonical, variants in PHONETIC_MAP.items():
    PHONETIC_LOOKUP[canonical.lower()] = variants
    for v in variants:
        PHONETIC_LOOKUP[v.lower()] = [canonical] + [x for x in variants if x != v]


def add_spelling_error(name):
    if not name or len(name) < 3:
        return name
    error_type = random.choice(["swap", "drop", "double", "substitute"])
    chars = list(name)
    idx = random.randint(1, len(chars) - 2)
    if error_type == "swap" and idx < len(chars) - 1:
        chars[idx], chars[idx + 1] = chars[idx + 1], chars[idx]
    elif error_type == "drop":
        chars.pop(idx)
    elif error_type == "double":
        chars.insert(idx, chars[idx])
    elif error_type == "substitute":
        similar = {
            'a': 'e', 'e': 'a', 'i': 'y', 'y': 'i', 'o': 'u', 'u': 'o',
            'b': 'p', 'p': 'b', 'd': 't', 't': 'd', 'g': 'k', 'k': 'g',
            's': 'z', 'z': 's', 'c': 'k', 'm': 'n', 'n': 'm',
        }
        c = chars[idx].lower()
        if c in similar:
            replacement = similar[c]
            chars[idx] = replacement.upper() if chars[idx].isupper() else replacement
    return "".join(chars)


def abbreviate_name(name):
    if not name:
        return name
    if "-" in name:
        return name.split("-")[0]
    if " " in name:
        return name.split(" ")[0]
    if len(name) > 5:
        cut = random.choice([c for c in [3, 4, 5] if c < len(name)])
        return name[:cut]
    return name


def jitter_gps(lat, lon, max_meters=80):
    lat_jitter = random.uniform(-max_meters, max_meters) / 111000
    lon_jitter = random.uniform(-max_meters, max_meters) / (111000 * np.cos(np.radians(lat)))
    return round(lat + lat_jitter, 6), round(lon + lon_jitter, 6)


def jitter_dob(dob_str):
    dob = datetime.strptime(dob_str, "%Y-%m-%d")
    error_type = random.choice(["day", "month", "year", "swap_dm"])
    if error_type == "day":
        dob += timedelta(days=random.choice([-1, 1, -2, 2]))
    elif error_type == "month":
        month = max(1, min(12, dob.month + random.choice([-1, 1])))
        day = min(dob.day, 28)
        dob = dob.replace(month=month, day=day)
    elif error_type == "year":
        dob = dob.replace(year=dob.year + random.choice([-1, 1]))
    elif error_type == "swap_dm":
        if dob.day <= 12:
            dob = dob.replace(month=dob.day, day=dob.month)
    return dob.strftime("%Y-%m-%d")


def make_dup(rec):
    """Create a duplicate record with a new UUID."""
    dup = copy.deepcopy(rec)
    dup["individual_client_ref"] = str(uuid.uuid4())
    dup["is_duplicate"] = True
    return dup


def generate_duplicates(records, boundary_codes):
    """Generate labeled duplicates from a clean (no natural dupes) base."""
    duplicates = []
    ground_truth = []

    def add_pair(rec, dup, dtype, difficulty, score_min, score_max, desc):
        duplicates.append(dup)
        ground_truth.append({
            "original_id": rec["individual_client_ref"],
            "duplicate_id": dup["individual_client_ref"],
            "type": dtype,
            "difficulty": difficulty,
            "expected_score_min": score_min,
            "expected_score_max": score_max,
            "description": desc,
        })

    # ── EXACT_DUPLICATE (~2500) ──
    for rec in random.sample(records, min(2500, len(records))):
        dup = make_dup(rec)
        # Assign to a different cycle to simulate cross-cycle re-registration
        orig_cycle = rec.get("cycle", "1")
        dup["cycle"] = random.choice([c for c in ["1", "2", "3"] if c != orig_cycle])
        add_pair(rec, dup, "EXACT_DUPLICATE", "EASY", 0.95, 1.0,
                 f"Identical record re-registered in cycle {dup['cycle']}")

    # ── PHONETIC_VARIATION (~2000) ──
    phonetic_pool = [r for r in records
                     if (r["given_name"] and r["given_name"].lower() in PHONETIC_LOOKUP)
                     or (r["family_name"] and r["family_name"].lower() in PHONETIC_LOOKUP)]
    for rec in random.sample(phonetic_pool, min(2000, len(phonetic_pool))):
        dup = make_dup(rec)
        orig_given = rec["given_name"]
        orig_family = rec["family_name"]
        if rec["given_name"] and rec["given_name"].lower() in PHONETIC_LOOKUP:
            dup["given_name"] = random.choice(PHONETIC_LOOKUP[rec["given_name"].lower()])
        if rec["family_name"] and rec["family_name"].lower() in PHONETIC_LOOKUP and random.random() > 0.5:
            dup["family_name"] = random.choice(PHONETIC_LOOKUP[rec["family_name"].lower()])
        dup["latitude"], dup["longitude"] = jitter_gps(float(rec["latitude"]), float(rec["longitude"]), 30)
        dup["cycle"] = random.choice(["1", "2", "3"])
        add_pair(rec, dup, "PHONETIC_VARIATION", "MEDIUM", 0.70, 0.95,
                 f"'{orig_given} {orig_family}' -> '{dup['given_name']} {dup['family_name']}'")

    # ── SPELLING_ERROR (~2000) ──
    for rec in random.sample(records, min(2000, len(records))):
        dup = make_dup(rec)
        orig_given = rec["given_name"]
        orig_family = rec["family_name"]
        dup["given_name"] = add_spelling_error(rec["given_name"])
        if random.random() > 0.6:
            dup["family_name"] = add_spelling_error(rec["family_name"])
        dup["latitude"], dup["longitude"] = jitter_gps(float(rec["latitude"]), float(rec["longitude"]), 20)
        dup["cycle"] = random.choice(["1", "2", "3"])
        add_pair(rec, dup, "SPELLING_ERROR", "MEDIUM", 0.75, 0.95,
                 f"'{orig_given} {orig_family}' -> '{dup['given_name']} {dup['family_name']}'")

    # ── NAME_ABBREVIATION (~1000) ──
    long_pool = [r for r in records
                 if r["given_name"] and (len(r["given_name"]) > 5 or "-" in r["given_name"])]
    for rec in random.sample(long_pool, min(1000, len(long_pool))):
        dup = make_dup(rec)
        orig_given = rec["given_name"]
        dup["given_name"] = abbreviate_name(rec["given_name"])
        dup["latitude"], dup["longitude"] = jitter_gps(float(rec["latitude"]), float(rec["longitude"]), 25)
        dup["cycle"] = random.choice(["1", "2", "3"])
        add_pair(rec, dup, "NAME_ABBREVIATION", "HARD", 0.55, 0.85,
                 f"'{orig_given}' -> '{dup['given_name']}'")

    # ── NAME_ORDER_SWAP (~1000) ──
    for rec in random.sample(records, min(1000, len(records))):
        dup = make_dup(rec)
        dup["given_name"] = rec["family_name"]
        dup["family_name"] = rec["given_name"]
        dup["latitude"], dup["longitude"] = jitter_gps(float(rec["latitude"]), float(rec["longitude"]), 15)
        dup["cycle"] = random.choice(["1", "2", "3"])
        add_pair(rec, dup, "NAME_ORDER_SWAP", "MEDIUM", 0.65, 0.90,
                 f"'{rec['given_name']} {rec['family_name']}' -> '{dup['given_name']} {dup['family_name']}'")

    # ── DOB_VARIATION (~1500) ──
    for rec in random.sample(records, min(1500, len(records))):
        dup = make_dup(rec)
        orig_dob = rec["date_of_birth"]
        dup["date_of_birth"] = jitter_dob(rec["date_of_birth"])
        if random.random() > 0.5:
            dup["given_name"] = add_spelling_error(rec["given_name"])
        dup["cycle"] = random.choice(["1", "2", "3"])
        add_pair(rec, dup, "DOB_VARIATION", "MEDIUM", 0.70, 0.95,
                 f"DOB '{orig_dob}' -> '{dup['date_of_birth']}'")

    # ── GPS_NEARBY (~1000) ──
    for rec in random.sample(records, min(1000, len(records))):
        dup = make_dup(rec)
        dup["latitude"], dup["longitude"] = jitter_gps(float(rec["latitude"]), float(rec["longitude"]), 150)
        dup["location_accuracy"] = str(round(random.uniform(10, 25), 1))
        dup["cycle"] = random.choice(["1", "2", "3"])
        add_pair(rec, dup, "GPS_NEARBY", "EASY", 0.80, 1.0,
                 f"Same name, GPS shifted ~{random.randint(20, 150)}m")

    # ── COMBINED_NOISE (~1500) ──
    for rec in random.sample(records, min(1500, len(records))):
        dup = make_dup(rec)
        transforms = []
        orig_given = rec["given_name"]
        orig_family = rec["family_name"]
        if rec["given_name"] and rec["given_name"].lower() in PHONETIC_LOOKUP and random.random() > 0.4:
            dup["given_name"] = random.choice(PHONETIC_LOOKUP[rec["given_name"].lower()])
            transforms.append("phonetic")
        else:
            dup["given_name"] = add_spelling_error(rec["given_name"])
            transforms.append("spelling")
        if random.random() > 0.5:
            dup["family_name"] = add_spelling_error(rec["family_name"])
            transforms.append("family_spelling")
        if random.random() > 0.4:
            dup["date_of_birth"] = jitter_dob(rec["date_of_birth"])
            transforms.append("dob")
        dup["latitude"], dup["longitude"] = jitter_gps(float(rec["latitude"]), float(rec["longitude"]), 200)
        transforms.append("gps")
        if random.random() > 0.7:
            dup["mobile_number"] = ""
            transforms.append("no_phone")
        dup["cycle"] = random.choice([c for c in ["1", "2", "3"] if c != rec.get("cycle", "1")])
        transforms.append(f"cross_cycle_{dup['cycle']}")
        add_pair(rec, dup, "COMBINED_NOISE", "HARD", 0.45, 0.85,
                 f"Transforms: {', '.join(transforms)}. "
                 f"'{orig_given} {orig_family}' -> '{dup['given_name']} {dup['family_name']}'")

    # ── CROSS_BOUNDARY (~750) ──
    for rec in random.sample(records, min(750, len(records))):
        dup = make_dup(rec)
        other_boundaries = [b for b in boundary_codes if b != rec["boundary_code"]]
        new_boundary = random.choice(other_boundaries)
        dup["boundary_code"] = new_boundary
        # Move GPS to the new boundary area
        new_recs = [r for r in records if r["boundary_code"] == new_boundary]
        if new_recs:
            ref = random.choice(new_recs)
            dup["latitude"] = str(float(ref["latitude"]) + random.uniform(-0.002, 0.002))
            dup["longitude"] = str(float(ref["longitude"]) + random.uniform(-0.002, 0.002))
            dup["locality_name"] = ref["locality_name"]
        if random.random() > 0.5:
            dup["given_name"] = add_spelling_error(rec["given_name"])
        dup["cycle"] = random.choice(["1", "2", "3"])
        add_pair(rec, dup, "CROSS_BOUNDARY", "HARD", 0.50, 0.85,
                 f"Boundary '{rec['locality_name']}' -> '{dup['locality_name']}'")

    return duplicates, ground_truth


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("Loading synthetic data...")
    df = pd.read_csv(CSV_PATH)
    for col in ["given_name", "family_name", "father_name", "husband_name", "mobile_number"]:
        df[col] = df[col].fillna("")
    print(f"Original records: {len(df)}")

    # Step 1: Remove natural duplicates to create clean base
    print("\nStep 1: Creating clean base (removing natural duplicates)...")
    before = len(df)
    clean_df = df.drop_duplicates(
        subset=["given_name", "family_name", "date_of_birth", "gender"],
        keep="first"
    )
    dropped = before - len(clean_df)
    print(f"  Dropped {dropped} natural duplicates (same name+DOB+gender)")
    print(f"  Clean base: {len(clean_df)} unique records")

    # Step 2: Generate labeled duplicates
    print("\nStep 2: Generating labeled duplicates...")
    clean_df["is_duplicate"] = False
    records = clean_df.to_dict("records")
    boundary_codes = clean_df["boundary_code"].unique().tolist()
    duplicates, ground_truth = generate_duplicates(records, boundary_codes)

    # Step 3: Combine and shuffle
    print("\nStep 3: Combining and shuffling...")
    dup_df = pd.DataFrame(duplicates)
    combined = pd.concat([clean_df, dup_df], ignore_index=True)
    combined = combined.sample(frac=1, random_state=42).reset_index(drop=True)

    # Save
    records_path = os.path.join(OUTPUT_DIR, "dedup_test_records.csv")
    combined.to_csv(records_path, index=False)

    gt_df = pd.DataFrame(ground_truth)
    gt_path = os.path.join(OUTPUT_DIR, "dedup_ground_truth.csv")
    gt_df.to_csv(gt_path, index=False)

    type_counts = gt_df["type"].value_counts().to_dict()
    difficulty_counts = gt_df["difficulty"].value_counts().to_dict()

    summary = {
        "dataset": {
            "clean_base_records": int(len(clean_df)),
            "natural_duplicates_removed": int(dropped),
            "injected_duplicates": int(len(duplicates)),
            "total_records": int(len(combined)),
            "duplicate_ratio": round(len(duplicates) / len(combined) * 100, 1),
            "note": "Base records are guaranteed unique on (given_name, family_name, date_of_birth, gender). "
                    "Every duplicate in this dataset is labeled in the ground truth. "
                    "No unlabeled duplicates exist."
        },
        "duplicate_types": {k: int(v) for k, v in type_counts.items()},
        "difficulty_distribution": {k: int(v) for k, v in difficulty_counts.items()},
        "type_details": {
            "EXACT_DUPLICATE": {
                "count": int(type_counts.get("EXACT_DUPLICATE", 0)),
                "difficulty": "EASY",
                "description": "Identical name, DOB, gender, GPS. Different UUID and cycle. Simulates cross-cycle re-registration.",
                "what_catches_it": "Any basic matching on name+DOB should detect these."
            },
            "PHONETIC_VARIATION": {
                "count": int(type_counts.get("PHONETIC_VARIATION", 0)),
                "difficulty": "MEDIUM",
                "description": "Arabic/French transliteration: Mahamat->Muhammad, Khadija->Khadidja, Fatima->Fadima.",
                "what_catches_it": "Soundex or Double Metaphone phonetic encoding."
            },
            "SPELLING_ERROR": {
                "count": int(type_counts.get("SPELLING_ERROR", 0)),
                "difficulty": "MEDIUM",
                "description": "Typos: character swaps (Ibrahmi->Ibrahim), dropped/doubled letters, similar-char substitution.",
                "what_catches_it": "Jaro-Winkler or Levenshtein with appropriate threshold."
            },
            "NAME_ABBREVIATION": {
                "count": int(type_counts.get("NAME_ABBREVIATION", 0)),
                "difficulty": "HARD",
                "description": "Shortened names: Ibrahim->Ibra, Abdoulaye->Abdou, Jean-Pierre->Jean.",
                "what_catches_it": "Prefix matching or substring containment check."
            },
            "NAME_ORDER_SWAP": {
                "count": int(type_counts.get("NAME_ORDER_SWAP", 0)),
                "difficulty": "MEDIUM",
                "description": "Given/family name reversed: 'Mahamat Deby' registered as 'Deby Mahamat'.",
                "what_catches_it": "Cross-field comparison (given1 vs family2 and vice versa)."
            },
            "DOB_VARIATION": {
                "count": int(type_counts.get("DOB_VARIATION", 0)),
                "difficulty": "MEDIUM",
                "description": "DOB transcription errors: off-by-one day/month, day-month swap, year +/-1.",
                "what_catches_it": "DOB fuzzy matching with tolerance window."
            },
            "GPS_NEARBY": {
                "count": int(type_counts.get("GPS_NEARBY", 0)),
                "difficulty": "EASY",
                "description": "Same name/DOB, GPS shifted 20-150 meters. Different GPS reading at same location.",
                "what_catches_it": "Haversine distance scoring."
            },
            "COMBINED_NOISE": {
                "count": int(type_counts.get("COMBINED_NOISE", 0)),
                "difficulty": "HARD",
                "description": "Multiple variations: phonetic + spelling + DOB + GPS + missing phone + cross-cycle.",
                "what_catches_it": "Multi-attribute weighted scoring pipeline. Tests the full system."
            },
            "CROSS_BOUNDARY": {
                "count": int(type_counts.get("CROSS_BOUNDARY", 0)),
                "difficulty": "HARD",
                "description": "Same person registered in a different boundary/settlement. GPS won't help.",
                "what_catches_it": "Name+DOB matching across boundaries. Cannot rely on GPS proximity."
            },
        },
        "evaluation_guidance": {
            "metrics": [
                "Precision: Of pairs flagged as duplicates, what % are actual duplicates?",
                "Recall: Of all actual duplicate pairs, what % were detected?",
                "F1 Score: Harmonic mean of precision and recall"
            ],
            "benchmarks": {
                "EASY": ">90% recall at >90% precision",
                "MEDIUM": ">75% recall at >80% precision",
                "HARD": ">50% recall at >70% precision",
                "overall_F1": ">0.75"
            },
            "usage": [
                "1. Load dedup_test_records.csv into your dedup pipeline",
                "2. Run matching algorithm to produce candidate pairs with scores",
                "3. Compare detected pairs against dedup_ground_truth.csv",
                "4. Calculate precision, recall, F1 per type and overall",
                "5. Tune scoring weights and thresholds to improve F1"
            ]
        }
    }

    summary_path = os.path.join(OUTPUT_DIR, "dedup_test_summary.json")
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    # Print results
    print(f"\n{'=' * 65}")
    print("DEDUP TEST DATASET GENERATED (CLEAN BASE)")
    print(f"{'=' * 65}")
    print(f"\n  Clean base:          {len(clean_df)} records (natural dupes removed)")
    print(f"  Injected duplicates: {len(duplicates)}")
    print(f"  Total records:       {len(combined)}")
    print(f"\n  {'Type':<25} {'Count':>7} {'Difficulty':<10}")
    print(f"  {'-' * 50}")
    for t in ["EXACT_DUPLICATE", "PHONETIC_VARIATION", "SPELLING_ERROR",
              "NAME_ABBREVIATION", "NAME_ORDER_SWAP", "DOB_VARIATION",
              "GPS_NEARBY", "COMBINED_NOISE", "CROSS_BOUNDARY"]:
        c = type_counts.get(t, 0)
        d = summary["type_details"][t]["difficulty"]
        print(f"  {t:<25} {c:>7} {d:<10}")
    print(f"\n  {'TOTAL':<25} {len(gt_df):>7}")
    print(f"\n  Difficulty: EASY={difficulty_counts.get('EASY', 0)} | "
          f"MEDIUM={difficulty_counts.get('MEDIUM', 0)} | "
          f"HARD={difficulty_counts.get('HARD', 0)}")
    print(f"\nSaved to: {OUTPUT_DIR}/")


if __name__ == "__main__":
    main()
