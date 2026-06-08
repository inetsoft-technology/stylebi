#!/usr/bin/env python3
"""Prefilter for @if-wrapped Angular Material projection targets (Bug audit).

Lists template files where a Material named-slot projection target appears
inside a control-flow block body. Over-inclusive by design: the agent
classification pass applies the precise target->host ancestor rule to
separate true positives from the common false positive (an @if legitimately
wrapping a whole host such as <mat-form-field>).

Usage:  python3 tools/if-projection-audit.py [projects/em projects/portal ...]
"""
import re, glob, sys

TARGET_ATTR = re.compile(
    r'\b(matSuffix|matPrefix|matTextSuffix|matTextPrefix|matStepLabel'
    r'|matListItemTitle|matListItemLine|matListItemMeta)\b')
TARGET_EL = re.compile(
    r'<(mat-error|mat-hint|mat-label|mat-card-title|mat-card-subtitle'
    # mat-card-header/footer/actions: mat-card uses a single ng-content slot, so an
    # @if around these is never a named-projection bug. Kept here so the prefilter
    # stays over-inclusive, but intentionally absent from the classifier's EL_HOST.
    r'|mat-card-header|mat-card-footer|mat-card-actions|mat-expansion-panel-header'
    r'|mat-panel-title|mat-panel-description|mat-select-trigger)\b')

def has_target(s):
    return bool(TARGET_ATTR.search(s) or TARGET_EL.search(s))

def cf_block_bodies(txt):
    """Yield the brace-matched body of each @if/@else/@for/@switch/@case block.

    The opening is matched up to its first '{', which correctly skips over
    method-call parens in conditions (e.g. @if (isFoo())).
    """
    for m in re.finditer(r'@(if|else if|else|for|switch|case|default)\b[^\{]*\{', txt):
        start = m.end(); depth = 1; j = start
        while j < len(txt) and depth > 0:
            if txt[j] == '{': depth += 1
            elif txt[j] == '}': depth -= 1
            j += 1
        yield txt[start:j-1]

def main(roots):
    for root in roots:
        files = sorted(glob.glob(f'{root}/**/*.html', recursive=True))
        hits = []
        for f in files:
            txt = open(f, encoding='utf-8', errors='ignore').read()
            if has_target(txt) and any(has_target(b) for b in cf_block_bodies(txt)):
                hits.append(f)
        print(f'\n=== {root}: {len(hits)} candidate files ===')
        for h in hits:
            print('  ' + h)

if __name__ == '__main__':
    roots = sys.argv[1:] or ['projects/portal', 'projects/em', 'projects/shared']
    main(roots)
