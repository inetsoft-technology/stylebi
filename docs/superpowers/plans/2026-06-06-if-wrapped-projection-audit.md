# `@if`-wrapped Material Projection Audit & Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Find and fix every Angular Material named-slot projection target that the Angular 21 control-flow migration wrapped in an `@if`/`@for`/`@switch` block, in a single community PR.

**Architecture:** Discovery-driven. A deterministic prefilter narrows ~1000 templates to a candidate set; an agent fan-out applies a precise target→host ancestor rule to confirm true positives and emit exact rewrites; the rewrites are applied mechanically (`@if` wrapper → `*ngIf` on the projected element) and verified with build + lint + structural spot-check.

**Tech Stack:** Angular 21 / Angular Material 21, TypeScript, Python 3 (detection script), ESLint, Angular CLI (`ng build`).

**Spec:** `docs/superpowers/specs/2026-06-06-if-wrapped-projection-audit-design.md`

**Branch:** `fix-if-projection-audit` (community repo, off `main`) — already created; the spec is already committed here.

---

## Background the executor must know

- **The bug:** In Angular 21, when a control-flow block (`@if`/`@else`/`@else if`/`@for`/`@switch`/`@case`) sits *between* a Material projection target and its host component, the target is no longer projected into the host's named slot — it renders as ordinary content, causing layout bugs.
- **The fix pattern (from #75272):** move the condition off the `@if` wrapper and onto the projected element as `*ngIf="<condition>"`, preserving the condition verbatim and all bindings/template refs. Add `NgIf` to the component's `imports` array if missing.
- **Reference fixes already merged/open:** #75349 (`table-view.component.html`, `mat-expansion-panel-header`), #75335 (`dynamic-combo-box.component.html`, 5 `matSuffix` buttons). Use these as worked examples.

### The Detection Rule (true positive)

A target is broken iff, walking up from the target element, the **first** control-flow block is reached **before** its Material host element.

- ❌ broken: `<mat-form-field> … @if (c) { <button matSuffix>…</button> } </mat-form-field>` (control-flow between target and host)
- ✅ fine: `@if (c) { <mat-form-field> … <button matSuffix>…</button> </mat-form-field> }` (control-flow above host)

### Target → Host catalog (Material only)

| Host | Targets |
|---|---|
| `mat-form-field` | `matSuffix`, `matPrefix`, `matTextSuffix`, `matTextPrefix`, `mat-label`, `mat-error`, `mat-hint` |
| `mat-card` | `mat-card-title`, `mat-card-subtitle`, `mat-card-header`, `mat-card-footer`, `mat-card-actions` |
| `mat-expansion-panel` | `mat-expansion-panel-header`; inside header: `mat-panel-title`, `mat-panel-description` |
| `mat-step`/`mat-stepper` | `matStepLabel` |
| `mat-list-item` | `matListItemTitle`, `matListItemLine`, `matListItemMeta` |
| `mat-select`/autocomplete | `mat-select-trigger` |

`mat-option` inside `@for` is legitimate — excluded.

---

## Task 1: Commit the detection prefilter script

**Files:**
- Create: `community/web/tools/if-projection-audit.py` (run from `community/web`)

- [ ] **Step 1: Create the prefilter script**

```python
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
        hits = [f for f in files
                if has_target(open(f, encoding='utf-8', errors='ignore').read())
                and any(has_target(b) for b in
                        cf_block_bodies(open(f, encoding='utf-8', errors='ignore').read()))]
        print(f'\n=== {root}: {len(hits)} candidate files ===')
        for h in hits:
            print('  ' + h)

if __name__ == '__main__':
    roots = sys.argv[1:] or ['projects/portal', 'projects/em', 'projects/shared']
    main(roots)
```

- [ ] **Step 2: Run it and confirm the candidate set**

Run: `cd community/web && python3 tools/if-projection-audit.py`
Expected: `portal: 0`, `em: 78`, `shared: 0` candidate files (the 78 em files are candidates, not confirmed bugs).

- [ ] **Step 3: Commit**

```bash
cd community
git add web/tools/if-projection-audit.py
git commit -m "Add @if-wrapped Material projection prefilter script"
```

---

## Task 2: Classify candidates (agent fan-out) → findings list

**Files:**
- Create: `community/docs/superpowers/plans/if-projection-findings.md` (the audit record / fix worklist)

- [ ] **Step 1: Fan out classification over the 78 candidate files**

Dispatch agents in parallel (batches of ~10 files each) using `superpowers:dispatching-parallel-agents`. Each agent receives a slice of the candidate file list and this exact instruction:

> For each assigned template file, find every Angular Material projection target from this catalog: `matSuffix`, `matPrefix`, `matTextSuffix`, `matTextPrefix`, `mat-label`, `mat-error`, `mat-hint`, `mat-card-title`, `mat-card-subtitle`, `mat-card-header`, `mat-card-footer`, `mat-card-actions`, `mat-expansion-panel-header`, `mat-panel-title`, `mat-panel-description`, `matStepLabel`, `matListItemTitle`, `matListItemLine`, `matListItemMeta`, `mat-select-trigger`.
>
> Apply this rule: a target is a TRUE POSITIVE iff, walking up from the target element, the FIRST control-flow block (`@if`/`@else`/`@else if`/`@for`/`@switch`/`@case`) is reached BEFORE its Material host element (`mat-form-field`/`mat-card`/`mat-expansion-panel`/`mat-step`/`mat-list-item`/`mat-select`). If the control-flow block instead wraps the WHOLE host (host is inside the block, target is inside the host), it is a FALSE POSITIVE — do not flag it.
>
> Return JSON: a list of `{file, line, host, target, snippet, verdict: "true"|"false", reason, proposedFix}` where `proposedFix` is the exact `*ngIf` rewrite (move the `@if` condition verbatim onto the target element; drop the wrapping block braces). Read files only — do not edit.

- [ ] **Step 2: Consolidate into the findings doc**

Write `docs/superpowers/plans/if-projection-findings.md` containing only the TRUE positives: a table of `file | line | host | target | condition` plus the exact before/after snippet for each. This is the worklist for Task 3. Record the false-positive count per file for the audit trail.

- [ ] **Step 3: Commit the findings**

```bash
cd community
git add docs/superpowers/plans/if-projection-findings.md
git commit -m "Add @if-projection audit findings (confirmed true positives)"
```

---

## Task 3: Apply fixes (one sub-task per confirmed file)

Repeat the following for **each file** in the findings doc. Do them in small commits grouped by feature area (e.g. all `settings/schedule/*` in one commit) to keep the PR reviewable.

**The transform (apply verbatim to each confirmed target):**

Before:
```html
@if (<condition>) {
  <el matSuffix ...>...</el>
}
```
After:
```html
<el matSuffix *ngIf="<condition>" ...>...</el>
```

Rules:
- Copy `<condition>` verbatim (it may contain method calls, `&&`, etc.).
- Keep every attribute, binding, and template ref (e.g. `#menuTrigger="matMenuTrigger"`) on the element.
- For an `@if`/`@else if`/`@else` chain, convert each branch's projected element to its own `*ngIf` with the equivalent condition (negate/AND prior conditions as needed; if non-trivial, note it in the findings and verify by reasoning).
- Do NOT touch control-flow blocks that wrap a whole host (false positives).

- [ ] **Step 1: Edit the file** — apply the transform to each confirmed target in that file.

- [ ] **Step 2: Ensure `NgIf` is imported** — open the matching `*.component.ts`; if `*ngIf` is now used and `NgIf` (from `@angular/common`) is not in `imports`, add it. (Most already import it.)

Run to check: `grep -n "NgIf" <component>.ts` — expected: present in the `imports:` array.

- [ ] **Step 3: Lint the changed files**

Run: `cd community/web && npx eslint "<changed .html>" "<changed .ts if any>"`
Expected: exit 0, no errors.

- [ ] **Step 4: Commit the group**

```bash
cd community
git add web/projects/em/src/app/<area>/...
git commit -m "Bug: fix @if-wrapped Material projection in <area>"
```

---

## Task 4: Build verification

- [ ] **Step 1: Build em**

Run: `cd community/web && npx ng build em --configuration development`
Expected: `Application bundle generation complete.` (If any portal files were fixed, also run `npx ng build portal --configuration development`.)

- [ ] **Step 2: If the build fails**, read the error (usually a missing `NgIf` import or an unbalanced block from a mis-applied transform), fix, and re-run until it passes.

---

## Task 5: Spot-check + run touched component specs

- [ ] **Step 1: Structural spot-check** — pick one fixed file per host type (form-field, card, expansion-panel, etc.) and reason through that the target now sits as a direct child of its host with `*ngIf`, no control-flow between them. Confirm against the §rule.

- [ ] **Step 2: Run any specs that exist for touched components**

Run: `cd community/web && npx ng test em --include='**/<touched-component>.spec.ts' --watch=false` for each touched component that has a `*.spec.ts`.
Expected: all pass (these mostly assert `should create`; the goal is to confirm no template regression).

---

## Task 6: Open the PR

- [ ] **Step 1: Push**

```bash
cd community
git push -u origin fix-if-projection-audit
```

- [ ] **Step 2: Create the community PR**

```bash
gh pr create --base main \
  --title "Fix @if-wrapped Angular Material projection regressions (audit sweep)" \
  --body "<summary + the findings table from if-projection-findings.md + root cause (Angular 21 @if breaks named-slot projection; incomplete #75272 sweep) + test plan (build/lint/spot-check) + references #75272 #75349 #75335>"
```

- [ ] **Step 3: Add reviewer**

```bash
gh api -X POST repos/inetsoft-technology/stylebi/pulls/<num>/requested_reviewers -f "reviewers[]=jshobe-inetsoft"
```

---

## Notes for the executor

- All changed Angular code is in the **community** submodule; there is no enterprise change and no submodule-pointer commit in this work.
- If classification finds **zero** true positives in portal/shared (expected) and only a handful in em, that's the correct outcome — the prefilter is deliberately noisy.
- Keep the detection script (`tools/if-projection-audit.py`) in the PR — it documents the method and can be re-run.
