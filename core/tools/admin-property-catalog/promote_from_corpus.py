#!/usr/bin/env python3
"""Propose admin-property-catalog.json entries from the server-property doc corpus.

Track A #6 (docs/superpowers/plans/2026-08-25-admin-plugin-master-plan.md, stylebi-wiz repo):
the property corpus at docs/properties/**/*.md (stylebi-enterprise) already carries, per
property, everything AUTHORING.md requires - type, default, secret/credential flags, an
intro sentence written for an operator - while
core/src/main/resources/inetsoft/web/admin/ai/admin-property-catalog.json (this repo) only
covers 47 of the ~368 documented properties. Once a property has a corpus page, promoting
it into the catalog is "largely transcription, not re-research" (master plan, Track A #6).

This script does the transcription and stops. It NEVER writes admin-property-catalog.json.
It writes a review file of proposed CatalogEntry-shaped JSON objects for a human (or agent)
to check and merge by hand - see AdminPropertyCatalog.java's own javadoc: a catalogued name
that does not exist would be snapshotted as null, applied, read back as null, and reported as
success for a property nothing reads. That is exactly the failure mode a script that
"auto-applies" would risk, which is why this one only proposes.

Two fields cannot be transcribed and are never guessed: `risk` and `snapshotScope`. The
corpus does not carry either (it carries facts *relevant* to them - secret/credential,
audience - not the values themselves; see the corpus README/AUTHORING.md). Determining them
for real means reading PropertyChangeSideEffects.java / PropertiesEngine.applyProperty /
addPropertyChangeListener the way c14c73e98 ("catalogue the SSO area") did by hand, per
property. This script always proposes the conservative pair (risk: high, snapshotScope:
storage - the same defaults AdminRiskClassifier already applies to an uncatalogued
property), attaches whatever grep-based evidence it can find in
PropertyChangeSideEffects.java, and marks both fields needsVerification so nothing merges a
guess silently.

Usage:
    python3 promote_from_corpus.py
        [--corpus-dir PATH]   default: ../../../../docs/properties relative to this file
                              (i.e. <stylebi-enterprise checkout>/docs/properties - this file
                              only resolves by default when community/ is checked out as the
                              submodule of a full stylebi-enterprise checkout, same assumption
                              AUTHORING.md's own tooling makes)
        [--catalog PATH]      default: ../../src/main/resources/inetsoft/web/admin/ai/admin-property-catalog.json
        [--side-effects PATH] default: ../../src/main/java/inetsoft/web/admin/properties/PropertyChangeSideEffects.java
        [--out PATH]          default: catalog-promotion-proposals.json next to this script
"""
import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent

# CatalogEntry.java: string, int, boolean, enum. The corpus's closed type set (AUTHORING.md
# lib/frontmatter.py TYPES) is wider - float, csv, path, duration-ms have no catalog
# equivalent today. Coining one silently is exactly the "novel shape" AUTHORING.md treats as
# an escalation, not a value to invent; entries in that shape are reported separately.
TYPE_MAP = {
    "string": "string",
    "boolean": "boolean",
    "integer": "int",
    "enum": "enum",
}

# The conservative pair AdminRiskClassifier already assigns an uncatalogued property
# (plugin/admin/README.md Track A #6 / AREA-SPEC-GUIDE.md). Promoting a property should never
# make it *less* safe to change than staying uncatalogued would have been.
DEFAULT_RISK = "high"
DEFAULT_SNAPSHOT_SCOPE = "storage"

SENTENCE_END = re.compile(r"(?<=[.!?])\s+")


def parse_scalar(raw):
    s = raw.strip()

    if s == "" or s == "null" or s == "~":
        return None

    if len(s) >= 2 and s[0] == s[-1] and s[0] in ("'", '"'):
        return s[1:-1]

    if s == "true":
        return True

    if s == "false":
        return False

    if s.startswith("[") and s.endswith("]"):
        inner = s[1:-1].strip()

        if not inner:
            return []

        return [parse_scalar(part) for part in inner.split(",")]

    return s


def parse_frontmatter(text):
    """Minimal frontmatter reader for this corpus's actual shapes (AUTHORING.md Sec. 5).

    Not a general YAML parser - deliberately narrower, so it fails loud (KeyError / None)
    on a shape it does not recognise rather than silently misreading one. Handles scalars,
    inline lists ("[a, b]"), block lists ("- item" per line) and one level of nested mapping
    (orgScope:, assessment:), which is everything the corpus pages actually use.
    """
    if not text.startswith("---"):
        return {}, text

    _, _, rest = text.partition("---\n")
    raw, _, body = rest.partition("\n---")
    lines = raw.split("\n")
    meta = {}
    i, n = 0, len(lines)

    while i < n:
        line = lines[i]

        if not line.strip() or line.startswith("#") or line[0] in (" ", "\t"):
            i += 1
            continue

        if ":" not in line:
            i += 1
            continue

        key, _, val = line.partition(":")
        key = key.strip()
        val = val.strip()

        if val:
            meta[key] = parse_scalar(val)
            i += 1
            continue

        block = []
        j = i + 1

        while j < n and (lines[j].startswith("  ") or not lines[j].strip()):
            block.append(lines[j])
            j += 1

        nonblank = [b for b in block if b.strip()]

        if nonblank and nonblank[0].strip().startswith("- "):
            meta[key] = [parse_scalar(b.strip()[2:]) for b in nonblank
                         if b.strip().startswith("- ")]
        else:
            sub = {}

            for b in nonblank:
                if ":" in b:
                    k2, _, v2 = b.strip().partition(":")
                    sub[k2.strip()] = parse_scalar(v2)

            meta[key] = sub

        i = j

    return meta, body.lstrip("\n")


def intro_description(body):
    """First sentence of the page's H1 intro (AUTHORING.md Sec. 6), as plain prose.

    The intro is the one paragraph AUTHORING.md requires to be self-contained and written in
    the operator's own vocabulary, so it is the safest thing on the page to lift verbatim -
    unlike the three H2 sections, which routinely depend on the corpus's own vocabulary
    (org scope, the property store) a catalog description has no room to define. This is a
    draft, not a final: the existing 47 entries sometimes tighten or editorialise past what
    the corpus says (e.g. sso.protocol.type's entry names the catalog's own validation, which
    the corpus page cannot know about itself), which is exactly why this is a review field.
    """
    lines = body.splitlines()
    start = None

    for idx, line in enumerate(lines):
        if line.startswith("# "):
            start = idx + 1
            break

    if start is None:
        return ""

    end = len(lines)

    for idx in range(start, len(lines)):
        if lines[idx].startswith("## "):
            end = idx
            break

    intro = " ".join(l.strip() for l in lines[start:end] if l.strip())
    # Strip markdown emphasis (`code`, *italic*, **bold**) - the corpus uses all three for EM
    # labels and property names (AUTHORING.md Sec. 6/8); a catalog description is plain prose.
    intro = re.sub(r"[`*]", "", intro)

    sentences = SENTENCE_END.split(intro, maxsplit=1)
    return sentences[0].strip() if sentences else intro.strip()


def load_catalog(path):
    with path.open(encoding="utf-8") as f:
        entries = json.load(f)

    keys = set()

    for entry in entries:
        keys.add(entry["name"].lower())

        for alias in entry.get("aliases") or []:
            keys.add(alias.lower())

    return entries, keys


def side_effect_hits(name, side_effects_text):
    """Literal-name hits in PropertyChangeSideEffects.java, as a lead for the reviewer.

    Evidence, not a verdict: a hit here means the property has a KNOWN special-cased side
    effect worth reading (some are cache-only -> value scope, some reach storage/fire a
    repository-wide event -> storage scope - see the class's own javadoc for both examples).
    A miss proves nothing either way - PropertiesEngine.applyProperty and listeners
    registered through addPropertyChangeListener are separate channels this grep does not
    cover, per AdminPropertyCatalog.java's javadoc. That asymmetry is exactly why
    snapshotScope always defaults to the conservative "storage" rather than flipping to
    "value" on a miss.
    """
    if side_effects_text is None:
        return None

    return bool(re.search(r'"' + re.escape(name) + r'"', side_effects_text))


def collect_corpus_pages(corpus_dir):
    pages = []

    for md_path in sorted(corpus_dir.glob("*/*.md")):
        area = md_path.parent.name

        if area.startswith("_"):
            continue

        meta, body = parse_frontmatter(md_path.read_text(encoding="utf-8"))

        if not meta.get("name") or not meta.get("type"):
            print(f"WARNING: skipping {md_path} - could not read name/type from frontmatter",
                  file=sys.stderr)
            continue

        pages.append((md_path, meta, body))

    return pages


def build_proposal(md_path, meta, body, corpus_dir, side_effects_text):
    name = meta["name"]
    corpus_type = meta["type"]
    catalog_type = TYPE_MAP.get(corpus_type)
    # Forward slashes always - Track A #6's own corpus carries this exact cosmetic gap
    # (plan Sec. 4, Track A item 8: "Windows path separators"); no reason to add a second copy.
    rel_source = md_path.relative_to(corpus_dir.parent.parent).as_posix()

    if meta.get("dynamicSuffix"):
        return None, {
            "name": name,
            "category": "family-stem",
            "reason": f"dynamicSuffix ({meta['dynamicSuffix']!r}) - this is a family stem, "
                      "not a literal settable property (AUTHORING.md Sec. 10); it has no "
                      "single value to catalogue.",
            "sourcePage": rel_source,
        }

    if catalog_type is None:
        return None, {
            "name": name,
            "category": "unmapped-type",
            "reason": f"corpus type {corpus_type!r} has no CatalogEntry.java equivalent "
                      "(string/int/boolean/enum only) - a schema gap, not a value to coin.",
            "sourcePage": rel_source,
        }

    read_sites = [s for s in (meta.get("readSites") or []) if isinstance(s, str)]
    has_community_site = any(s.startswith("community/") for s in read_sites)

    if read_sites and not has_community_site:
        # c14c73e98 ("catalogue the SSO area") deliberately left out the whole
        # stylebi.google.* family etc. for exactly this reason: their accessors live in the
        # enterprise superproject, so on a Community build the property does not exist, and
        # AdminPropertyCatalog.java's own javadoc calls that the one thing that must never
        # happen - a catalogued name that does not exist gets snapshotted null, applied,
        # read back null, reported success for a property nothing reads.
        return None, {
            "name": name,
            "category": "enterprise-only",
            "reason": "every readSite is outside community/ (" + "; ".join(read_sites) + ") "
                      "- likely enterprise-only, the same reason c14c73e98 excluded the "
                      "stylebi.google.* family from this catalog. Do not add to this repo's "
                      "catalog; an enterprise-side catalog resource would be the place, if one "
                      "exists.",
            "sourcePage": rel_source,
        }

    entry = {
        "name": name,
        "aliases": [],
        "type": catalog_type,
    }

    if corpus_type == "enum":
        entry["allowedValues"] = meta.get("allowedValues") or []

    entry["description"] = intro_description(body)
    entry["risk"] = DEFAULT_RISK
    entry["snapshotScope"] = DEFAULT_SNAPSHOT_SCOPE

    notes = []

    if not read_sites:
        notes.append(
            "no readSites (defaultSource: " + str(meta.get("defaultSource")) + ") - this "
            "script cannot confirm the property exists in the Community build from the corpus "
            "alone; verify before cataloguing, per AdminPropertyCatalog.java's javadoc.")

    assessment = meta.get("assessment") or {}
    audience = assessment.get("audience")

    if meta.get("secret") or meta.get("credential"):
        notes.append(
            "corpus marks secret={}/credential={} - check whether this name belongs in "
            "AdminPropertyCatalog.ENCRYPTED_CREDENTIALS (separate from this JSON file) "
            "before treating risk/snapshotScope as done.".format(
                meta.get("secret"), meta.get("credential")))

    if audience:
        notes.append(f"corpus assessment.audience: {audience}")

    hit = side_effect_hits(name, side_effects_text)

    if hit:
        notes.append(
            "found as a literal name in PropertyChangeSideEffects.java - read that side "
            "effect before setting snapshotScope; it is evidence, not a verdict.")
    elif hit is False:
        notes.append(
            "not found in PropertyChangeSideEffects.java - does not by itself mean "
            "snapshotScope: value; PropertiesEngine.applyProperty and "
            "addPropertyChangeListener are separate channels this check does not cover.")

    proposal = {
        "entry": entry,
        "sourcePage": rel_source,
        "needsVerification": ["risk", "snapshotScope"],
        "notes": notes,
    }
    return proposal, None


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                      formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus-dir", type=Path,
                         # SCRIPT_DIR is community/core/tools/admin-property-catalog; docs/properties
                         # lives in the stylebi-enterprise superproject, one level above community/.
                         default=SCRIPT_DIR / ".." / ".." / ".." / ".." / "docs" / "properties")
    parser.add_argument("--catalog", type=Path,
                         default=SCRIPT_DIR / ".." / ".." / "src" / "main" / "resources" /
                                 "inetsoft" / "web" / "admin" / "ai" /
                                 "admin-property-catalog.json")
    parser.add_argument("--side-effects", type=Path,
                         default=SCRIPT_DIR / ".." / ".." / "src" / "main" / "java" /
                                 "inetsoft" / "web" / "admin" / "properties" /
                                 "PropertyChangeSideEffects.java")
    parser.add_argument("--out", type=Path, default=SCRIPT_DIR / "catalog-promotion-proposals.json")
    args = parser.parse_args()

    corpus_dir = args.corpus_dir.resolve()
    catalog_path = args.catalog.resolve()

    if not corpus_dir.is_dir():
        sys.exit(f"corpus dir not found: {corpus_dir}")

    if not catalog_path.is_file():
        sys.exit(f"catalog not found: {catalog_path}")

    _, catalogued_keys = load_catalog(catalog_path)

    side_effects_text = None

    if args.side_effects.resolve().is_file():
        side_effects_text = args.side_effects.resolve().read_text(encoding="utf-8")
    else:
        print(f"WARNING: {args.side_effects} not found - snapshotScope notes will be omitted",
              file=sys.stderr)

    pages = collect_corpus_pages(corpus_dir)

    proposals = []
    skipped = []
    already_catalogued = 0

    for md_path, meta, body in pages:
        if meta["name"].lower() in catalogued_keys:
            already_catalogued += 1
            continue

        proposal, skip = build_proposal(md_path, meta, body, corpus_dir, side_effects_text)

        if proposal is not None:
            proposals.append(proposal)
        else:
            skipped.append(skip)

    by_category = {}

    for s in skipped:
        by_category[s["category"]] = by_category.get(s["category"], 0) + 1

    output = {
        "corpusPageCount": len(pages),
        "alreadyCatalogued": already_catalogued,
        "proposalCount": len(proposals),
        "skippedCount": len(skipped),
        "skippedByCategory": by_category,
        "proposals": proposals,
        "skipped": skipped,
    }

    args.out.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")

    print(f"corpus pages read: {len(pages)}")
    print(f"already catalogued: {already_catalogued}")
    print(f"proposals written: {len(proposals)}")
    print(f"skipped: {len(skipped)} {by_category}")
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()
