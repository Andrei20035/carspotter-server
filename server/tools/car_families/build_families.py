#!/usr/bin/env python3
"""
Builds a deterministic (brand, model) -> family mapping for the car_models catalog
seeded by V5__seed_car_models.sql.

A "family" is the base commercial model within a brand (e.g. all Golf variants share
the family "Golf"), not the body style, trim, or performance variant. The mapping this
script produces is the source of truth consumed to generate the V23 backfill migration
(see the plan for this task) — mapping.csv is meant to be reviewed by a human, not
trusted blindly.

Algorithm, applied independently per brand:

  1. Token-prefix match — if a whole-word-boundary prefix of a model's name is itself
     a catalog model of the same brand (after normalization), that catalog model
     becomes the family. E.g. "Arteon Shooting Brake" -> "Arteon" (a real VW row).
     This is a token comparison, not a substring one: "Golf" is not a token-prefix of
     "Gol", so the well-known Gol/Golf collision is avoided for free.

  2. Variant-suffix reduction — repeatedly strip one trailing "variant" token (body
     style / trim / powertrain suffix drawn from a fixed vocabulary below, or a
     trailing parenthetical like "(US)") from models step 1 didn't already place.
     The resulting reduced base becomes a shared family ONLY when at least two models
     in the brand reduce to the same base — this is what forms families like "Golf",
     "Clio", "Megane", "Polo" even though the bare model name never appears alone in
     the catalog. A base reached by only one model is not promoted; that model falls
     through to step 4 as its own singleton.

  3. Overrides — explicit (brand, model, family) rows loaded from overrides.csv (if
     present) take absolute priority over steps 1-2. This is where every case the
     algorithm cannot resolve safely (aliases joined by "/", electric variants that
     should still share the combustion model's family, brand-specific casing quirks,
     etc.) is decided explicitly and visibly, rather than folded into the code.

  4. Fallback — any model still without a family after steps 1-3 becomes a singleton
     family named after itself.

Output: mapping.csv with columns brand,model,family — one row per catalog (brand, model)
pair, sorted by (brand, model) for a stable, reviewable diff.

Usage:
    python3 build_families.py            # parse V5, run the algorithm, write mapping.csv
    python3 build_families.py --check    # parse V5 only, report pair/brand counts, verify
                                          # against the expected catalog size (no mapping.csv write)
"""
from __future__ import annotations

import csv
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
V5_PATH = SCRIPT_DIR / ".." / ".." / "src" / "main" / "resources" / "db" / "migrations" / "V5__seed_car_models.sql"
OVERRIDES_PATH = SCRIPT_DIR / "overrides.csv"
MAPPING_PATH = SCRIPT_DIR / "mapping.csv"

EXPECTED_PAIR_COUNT = 2928
EXPECTED_BRAND_COUNT = 119

# One VALUES row: ('Brand', 'Model'), with '' as the SQL escape for a literal quote.
_PAIR_RE = re.compile(r"\(\s*'((?:[^']|'')*)'\s*,\s*'((?:[^']|'')*)'\s*\)")

# Trailing multi-word phrases stripped as a single suffix (checked before single tokens,
# longest first, so e.g. "3 Doors" is removed as a whole rather than just "Doors").
SUFFIX_PHRASES = [
    "3 doors",
    "5 doors",
    "4 doors",
    "2 doors",
]

# Trailing single tokens (already casefolded) treated as a variant/trim/powertrain suffix.
SUFFIX_TOKENS = {
    "estate", "variant", "coupe", "coupé", "cabrio", "cabriolet", "sedan", "saloon",
    "hatchback", "touring", "tourer", "avant", "sportback", "combi", "alltrack",
    "allspace", "plus", "sportsvan", "roadster", "t-modell",
    "r", "rs", "gt", "gti", "gtd", "gte", "gtx", "gli", "amg",
}


def normalize_for_compare(text: str) -> str:
    """Casefold + collapse whitespace for matching purposes. Not used for display."""
    collapsed = re.sub(r"\s+", " ", text.strip())
    return collapsed.casefold()


def parse_catalog(sql_text: str) -> list[tuple[str, str]]:
    pairs = []
    for brand, model in _PAIR_RE.findall(sql_text):
        pairs.append((brand.replace("''", "'"), model.replace("''", "'")))
    return pairs


def load_overrides(path: Path) -> dict[tuple[str, str], str]:
    """Loads explicit (brand, model) -> family overrides. Missing file -> no overrides."""
    if not path.exists():
        return {}
    overrides: dict[tuple[str, str], str] = {}
    with path.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            brand = row["brand"].strip()
            model = row["model"].strip()
            family = row["family"].strip()
            overrides[(brand, model)] = family
    return overrides


def validate_overrides(
    overrides: dict[tuple[str, str], str],
    catalog_pairs: set[tuple[str, str]],
) -> list[str]:
    """An override is rejected if it references a (brand, model) pair that isn't in the
    catalog. Overrides are always keyed by the exact (brand, model) tuple — never by model
    name alone — so this same check also rules out a family ending up with members from
    different brands: there is no way for an override to attach a model to a family under
    the wrong brand without that (brand, model) pair failing this existence check first
    (and the composite FK in V22, car_models(brand, family_id) -> car_families(brand, id),
    enforces the same invariant again at the database level)."""
    errors = []
    for brand, model in overrides:
        if (brand, model) not in catalog_pairs:
            errors.append(f"override references unknown (brand, model): ({brand!r}, {model!r})")
    return errors


def _strip_one_suffix(tokens: list[str]) -> list[str] | None:
    """Removes one trailing variant suffix (phrase, single token, or parenthetical)."""
    if len(tokens) <= 1:
        return None

    lowered = [t.casefold() for t in tokens]
    for phrase in SUFFIX_PHRASES:
        phrase_tokens = phrase.split(" ")
        n = len(phrase_tokens)
        if len(tokens) > n and lowered[-n:] == phrase_tokens:
            return tokens[:-n]

    last = tokens[-1]
    if last.casefold() in SUFFIX_TOKENS:
        return tokens[:-1]
    if re.fullmatch(r"\([^)]*\)", last):
        return tokens[:-1]

    return None


def reduce_to_base(model: str) -> str:
    """Repeatedly strips trailing variant suffixes, returning the reduced base name."""
    tokens = model.split(" ")
    while True:
        stripped = _strip_one_suffix(tokens)
        if stripped is None:
            return " ".join(tokens)
        tokens = stripped


def _token_prefix_match(model: str, exact_by_norm: dict[str, str]) -> str | None:
    """Step 1: does a whole-word prefix of model match another real catalog model?"""
    tokens = model.split(" ")
    for k in range(len(tokens) - 1, 0, -1):
        candidate = " ".join(tokens[:k])
        hit = exact_by_norm.get(normalize_for_compare(candidate))
        if hit is not None and hit != model:
            return hit
    return None


def _pick_canonical_name(items: list[tuple[str, str]], exact_by_norm: dict[str, str]) -> str:
    """Picks the display name for a reduced-base group: a real catalog row if one
    matches exactly, else the most common raw reduced spelling among the group."""
    for _model, reduced in items:
        hit = exact_by_norm.get(normalize_for_compare(reduced))
        if hit is not None:
            return hit
    counts = Counter(reduced for _model, reduced in items)
    return counts.most_common(1)[0][0]


def build_family_map(
    pairs: list[tuple[str, str]],
    overrides: dict[tuple[str, str], str],
) -> dict[tuple[str, str], str]:
    by_brand: dict[str, list[str]] = defaultdict(list)
    for brand, model in pairs:
        by_brand[brand].append(model)

    family_of: dict[tuple[str, str], str] = {}

    for brand, models in by_brand.items():
        exact_by_norm = {normalize_for_compare(m): m for m in models}
        brand_family: dict[str, str] = {}

        # Step 1 — token-prefix match against a real catalog row.
        for model in models:
            hit = _token_prefix_match(model, exact_by_norm)
            if hit is not None:
                brand_family[model] = hit

        # Step 2 — suffix reduction, promoted only when shared by >= 2 models.
        reduced_groups: dict[str, list[tuple[str, str]]] = defaultdict(list)
        for model in models:
            if model in brand_family:
                continue
            reduced = reduce_to_base(model)
            reduced_groups[normalize_for_compare(reduced)].append((model, reduced))

        for _norm_reduced, items in reduced_groups.items():
            if len(items) >= 2:
                canonical = _pick_canonical_name(items, exact_by_norm)
                for model, _reduced in items:
                    brand_family[model] = canonical

        # Step 3 — explicit overrides win over steps 1-2.
        for model in models:
            override = overrides.get((brand, model))
            if override is not None:
                brand_family[model] = override

        # Step 4 — fallback: singleton family named after the model itself.
        for model in models:
            brand_family.setdefault(model, model)

        for model in models:
            family_of[(brand, model)] = brand_family[model]

    return family_of


def write_mapping(pairs: list[tuple[str, str]], family_of: dict[tuple[str, str], str], path: Path) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["brand", "model", "family"])
        for brand, model in sorted(set(pairs)):
            writer.writerow([brand, model, family_of[(brand, model)]])


def print_summary(pairs: list[tuple[str, str]], family_of: dict[tuple[str, str], str]) -> None:
    brands = {b for b, _m in pairs}
    families_by_key: dict[tuple[str, str], set[str]] = defaultdict(set)
    for (brand, model), family in family_of.items():
        families_by_key[(brand, family)].add(model)

    multi = {k: v for k, v in families_by_key.items() if len(v) > 1}
    singleton = {k: v for k, v in families_by_key.items() if len(v) == 1}

    print(f"pairs: {len(pairs)} (distinct: {len(set(pairs))})")
    print(f"brands: {len(brands)}")
    print(f"families: {len(families_by_key)}")
    print(f"  multi-model: {len(multi)}")
    print(f"  singleton:   {len(singleton)}")


def main() -> int:
    check_only = "--check" in sys.argv[1:]

    if not V5_PATH.exists():
        print(f"error: catalog file not found: {V5_PATH}", file=sys.stderr)
        return 1

    sql_text = V5_PATH.read_text(encoding="utf-8")
    pairs = parse_catalog(sql_text)
    distinct_pairs = set(pairs)
    brands = {b for b, _m in pairs}

    if len(pairs) != len(distinct_pairs):
        print(f"error: parsed {len(pairs)} rows but only {len(distinct_pairs)} are distinct "
              f"— duplicate (brand, model) pair in the catalog", file=sys.stderr)
        return 1

    if check_only:
        print(f"pairs: {len(pairs)} (expected {EXPECTED_PAIR_COUNT})")
        print(f"brands: {len(brands)} (expected {EXPECTED_BRAND_COUNT})")
        ok = len(pairs) == EXPECTED_PAIR_COUNT and len(brands) == EXPECTED_BRAND_COUNT
        if not ok:
            print("check FAILED", file=sys.stderr)
            return 1
        print("check OK")
        return 0

    overrides = load_overrides(OVERRIDES_PATH)
    override_errors = validate_overrides(overrides, distinct_pairs)
    if override_errors:
        for message in override_errors:
            print(f"error: {message}", file=sys.stderr)
        return 1

    family_of = build_family_map(pairs, overrides)
    write_mapping(pairs, family_of, MAPPING_PATH)
    print_summary(pairs, family_of)
    print(f"wrote {MAPPING_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
