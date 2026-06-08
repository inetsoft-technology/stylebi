# `@if`-wrapped Material Projection — Audit Findings

**Date:** 2026-06-06
**Detector:** `web/tools/if-projection-classify.py` (structural; validated against the three known cases below)
**Scanned:** `projects/em`, `projects/portal`, `projects/shared` (all `*.html`)

## Method

The prefilter (`tools/if-projection-audit.py`) narrowed ~1000 templates to **78 candidate files** in `em` (portal/shared: 0 co-occurrences). The structural classifier (`tools/if-projection-classify.py`) then applied the precise rule — a target is broken only if a control-flow block sits **between** the target and an actual Material host that exists above it — handling `{{ }}` interpolations, comments, void/self-closing tags, and method-call conditions.

Key correctness facts established during the audit:
- `mat-card`'s own template is a single `<ng-content>` (no named slots). So `mat-card-title`/`subtitle`/`actions`/`footer` as **direct children of `mat-card`** are NOT named-projected — an `@if`/`@for` around them is harmless. `mat-card-title`/`subtitle` are only named-projected by `mat-card-header` / `mat-card-title-group`.
- Stray `mat-label`/`mat-error`/`mat-hint` outside a `mat-form-field` have no host and are unaffected.

## True positives (7 total)

| File | Line | Target | Host | Status |
|---|---|---|---|---|
| `common/util/table/table-view.component.html` | 66 | `mat-expansion-panel-header` | `mat-expansion-panel` | **Already fixed** — Bug #75349, PR #3912 |
| `widget/dynamic-combo-box.component.html` | 26 | `matSuffix` button | `mat-form-field` | **Already fixed** — Bug #75335, PR #3926 |
| `widget/dynamic-combo-box.component.html` | 32 | `matSuffix` button | `mat-form-field` | **Already fixed** — Bug #75335, PR #3926 |
| `widget/dynamic-combo-box.component.html` | 52 | `matSuffix` button | `mat-form-field` | **Already fixed** — Bug #75335, PR #3926 |
| `widget/dynamic-combo-box.component.html` | 67 | `matSuffix` button | `mat-form-field` | **Already fixed** — Bug #75335, PR #3926 |
| `widget/dynamic-combo-box.component.html` | 73 | `matSuffix` button | `mat-form-field` | **Already fixed** — Bug #75335, PR #3926 |
| `settings/schedule/task-options-pane/task-options-pane.component.html` | 100 | `matSuffix` (`mat-spinner`) | `mat-form-field` | **TO FIX in this PR** |

### The fix in this PR — `task-options-pane.component.html:99-101`

Before:
```html
@if (isAdminNameLoading) {
  <mat-spinner matSuffix mode="indeterminate" diameter="17"></mat-spinner>
}
```
After:
```html
<mat-spinner matSuffix *ngIf="isAdminNameLoading" mode="indeterminate" diameter="17"></mat-spinner>
```

The `mat-spinner` carries `matSuffix` and is meant to project into the `mat-form-field`'s (the "Owner" field) suffix slot as a loading indicator; the wrapping `@if` broke that projection so the spinner rendered as ordinary content.

## False positives reviewed (representative)

All other candidates from the 78-file prefilter were confirmed harmless:
- `mat-card-title`/`subtitle` as direct children of `mat-card` (single-slot): many settings views (localization, presentation-*, schedule-status, etc.).
- Stray `mat-label`/`mat-error`/`mat-hint` outside any `mat-form-field` (start-time-editor:26 hint in a radio-group, security-settings-page:42 hint in a slide-toggle, delivery-emails:152 label in an `@else`, repository-sheet-settings:22 decorative error in a card-header, email-settings stray labels).
- `@if` wrapping a *whole* `mat-form-field` (webmap, look-and-feel, import-asset, etc.) — projection happens within the form-field, unaffected.

## Outcome

The Angular-21 `@if`-projection regression is essentially cleaned up: of 7 total instances, 6 are already addressed by open PRs #3912 and #3926, leaving exactly one (`task-options-pane`) fixed here. `portal` and `shared` are clean.
