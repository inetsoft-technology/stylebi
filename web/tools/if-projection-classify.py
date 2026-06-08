#!/usr/bin/env python3
"""Structural classifier for @if-wrapped Angular Material projection targets.

Builds a nesting tree of each template (HTML elements + control-flow blocks)
and, for every Material projection target, finds the nearest enclosing ancestor
that is either (a) the target's Material host element or (b) a control-flow
block (@if/@else/@for/@switch/@case). If a control-flow block is reached before
the host, the projection is broken -> TRUE POSITIVE.

Usage: python3 tools/if-projection-classify.py [projects/em ...]
"""
import re, glob, sys

# target attr -> set of acceptable host tags
ATTR_HOST = {
    'matSuffix': {'mat-form-field'}, 'matPrefix': {'mat-form-field'},
    'matTextSuffix': {'mat-form-field'}, 'matTextPrefix': {'mat-form-field'},
    'matStepLabel': {'mat-step'},
    'matListItemTitle': {'mat-list-item'}, 'matListItemLine': {'mat-list-item'},
    'matListItemMeta': {'mat-list-item'},
}
# target tag -> set of acceptable host tags
# NOTE: mat-card's own template is a single <ng-content> (no named slots), so
# mat-card-title/subtitle/actions/footer as DIRECT children of mat-card are NOT
# named-projected and an @if around them is harmless. mat-card-title/subtitle are
# only named-projected by mat-card-header / mat-card-title-group.
EL_HOST = {
    'mat-label': {'mat-form-field'}, 'mat-error': {'mat-form-field'},
    'mat-hint': {'mat-form-field'},
    'mat-card-title': {'mat-card-header', 'mat-card-title-group'},
    'mat-card-subtitle': {'mat-card-header', 'mat-card-title-group'},
    'mat-expansion-panel-header': {'mat-expansion-panel'},
    'mat-panel-title': {'mat-expansion-panel-header'},
    'mat-panel-description': {'mat-expansion-panel-header'},
    'mat-select-trigger': {'mat-select'},
}
CF = ('if', 'else if', 'else', 'for', 'switch', 'case', 'default')
# block opener: @kw ( ... ) {  /  @else {  /  @default { — built from CF so the
# keyword vocabulary lives in one place. 'else if' precedes 'else' for longest-match.
CF_OPEN_RE = r'@(' + '|'.join(CF) + r')\b[^\{<]*\{'

VOID = {'input', 'img', 'br', 'hr', 'mat-icon'}  # mat-icon: projection targets never nest inside it

def tokenize(txt):
    """Yield ('open', tag, attrs, line), ('close', tag, line),
    ('block', kw, line), ('blockclose', line)."""
    # strip comments and {{ }} interpolations so their braces don't get mistaken
    # for control-flow block delimiters (newline-preserving for accurate line nums)
    blank = lambda m: '\n' * m.group(0).count('\n')
    txt = re.sub(r'<!--.*?-->', blank, txt, flags=re.S)
    txt = re.sub(r'\{\{.*?\}\}', blank, txt, flags=re.S)
    i = 0; n = len(txt); line = 1
    # we walk char by char tracking line numbers
    while i < n:
        c = txt[i]
        if c == '\n':
            line += 1; i += 1; continue
        if c == '<':
            m = re.match(r'</\s*([a-zA-Z][\w-]*)\s*>', txt[i:])
            if m:
                yield ('close', m.group(1), line); i += m.end(); continue
            m = re.match(r'<([a-zA-Z][\w-]*)((?:[^<>"\']|"[^"]*"|\'[^\']*\')*?)(/?)>', txt[i:], re.S)
            if m:
                tag, attrs, selfclose = m.group(1), m.group(2), m.group(3)
                line_inc = txt[i:i+m.end()].count('\n')
                yield ('open', tag, attrs, line)
                if selfclose or tag in VOID:
                    yield ('close', tag, line)
                line += line_inc; i += m.end(); continue
            i += 1; continue
        # control-flow block opening:  @kw ( ... ) {   or  @else {   or @default {
        m = re.match(CF_OPEN_RE, txt[i:])
        if m:
            yield ('block', m.group(1), line)
            line += txt[i:i+m.end()].count('\n'); i += m.end(); continue
        if c == '}':
            yield ('blockclose', line); i += 1; continue
        if c == '{':  # stray { (e.g. object literal in binding that slipped through) - skip
            i += 1; continue
        i += 1

def classify(path):
    txt = open(path, encoding='utf-8', errors='ignore').read()
    stack = []  # each entry: ('el', tag, line) or ('block', kw, line)
    findings = []
    for tok in tokenize(txt):
        kind = tok[0]
        if kind == 'open':
            _, tag, attrs, line = tok
            # check this element as a projection target (by attr or by tag)
            targets = []
            for a in ATTR_HOST:
                if re.search(r'(^|\s)' + re.escape(a) + r'(\s|=|$)', attrs):
                    targets.append((a, ATTR_HOST[a]))
            if tag in EL_HOST:
                targets.append((tag, EL_HOST[tag]))
            for tname, hosts in targets:
                # Walk up. A target is broken ONLY IF a control-flow block sits
                # between it and an actual host that exists above it. If the host
                # is never found, the element isn't named-projected here (e.g.
                # mat-card-title as a direct child of mat-card, which single-slots)
                # -> not a bug.
                block_between = None
                for entry in reversed(stack):
                    if entry[0] == 'el' and entry[1] in hosts:
                        if block_between is not None:
                            findings.append((line, tag, tname,
                                             block_between[2], block_between[1]))
                        break  # host found (with or without a block between)
                    if entry[0] == 'block' and block_between is None:
                        block_between = entry  # remember the nearest block
                # host never found -> skip (not a named-projection context)
            stack.append(('el', tag, line))
        elif kind == 'close':
            _, tag, line = tok
            # pop to matching element tag
            for idx in range(len(stack) - 1, -1, -1):
                if stack[idx][0] == 'el' and stack[idx][1] == tag:
                    del stack[idx:]
                    break
        elif kind == 'block':
            stack.append(('block', tok[1], tok[2]))
        elif kind == 'blockclose':
            for idx in range(len(stack) - 1, -1, -1):
                if stack[idx][0] == 'block':
                    del stack[idx:]
                    break
    return findings

def main(roots):
    total = 0
    for root in roots:
        for f in sorted(glob.glob(f'{root}/**/*.html', recursive=True)):
            for (tline, tag, tname, bline, bkw) in classify(f):
                total += 1
                print(f'{f}:{tline}  target=<{tag}> ({tname})  '
                      f'broken by @{bkw} at line {bline}')
    print(f'\nTOTAL TRUE POSITIVES: {total}')

if __name__ == '__main__':
    main(sys.argv[1:] or ['projects/em', 'projects/portal', 'projects/shared'])
