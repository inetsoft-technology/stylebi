# `@if`-wrapped Material Projection Audit & Fix — Design

**Date:** 2026-06-06
**Status:** Approved (design)
**Repo:** community (`community/web`), Angular 21 / Angular Material 21

## Problem

The Angular 20→21 control-flow migration (`*ngFor`/`*ngIf` → `@for`/`@if`, commit `b667af680`) introduced a class of regression: when a **named content-projection target** is wrapped in an `@if` (or other control-flow) block that sits *between* the target and its host component, Angular 21 no longer projects the element into the host's named slot. The element instead renders as ordinary content, producing layout/visual bugs.

The #75272 follow-up (`0c96f3a5d`) converted many such cases back to `*ngIf`, but the sweep was incomplete. Confirmed missed instances found since:

- **#75349** — `mat-expansion-panel-header` wrapped in `@if` in the shared `table-view.component.html`; collapsing a monitoring table hid the whole pane. (Duplicate: **#75330**, the "Open Dashboards" pane.)
- **#75335** — 5 `<button matSuffix>` icons wrapped in `@if` in `dynamic-combo-box.component.html`; the value-type icon rendered at the top-left of an enlarged field instead of as a suffix.

This audit systematically finds and fixes the remaining instances across the Angular codebase.

## Goal

Find **all** remaining `@if`-wrapped Angular Material projection targets and fix them in a **single community PR**.

## The Detection Rule

A Material projection target is **broken** iff a control-flow block (`@if` / `@else` / `@else if` / `@for` / `@switch` / `@case`) sits **between** the target element and its Material host component in the template nesting.

- ❌ broken — control-flow between target and host:
  ```html
  <mat-form-field>
    <input matInput>
    @if (cond) { <button matSuffix>…</button> }   <!-- @if between matSuffix and mat-form-field -->
  </mat-form-field>
  ```
- ✅ fine — `@if` is *above* the host, nothing between target and host:
  ```html
  @if (cond) {
    <mat-form-field>
      <input matInput>
      <button matSuffix>…</button>
    </mat-form-field>
  }
  ```

Equivalent phrasing: walking up from the target element, if the first control-flow block is reached **before** the host element, it is a true positive.

### Target → Host Catalog (Angular Material only)

| Host | Projected targets |
|---|---|
| `mat-form-field` | `matSuffix`, `matPrefix`, `matTextSuffix`, `matTextPrefix`, `mat-label`, `mat-error`, `mat-hint` |
| `mat-card` | `mat-card-title`, `mat-card-subtitle`, `mat-card-header`, `mat-card-footer`, `mat-card-actions` |
| `mat-expansion-panel` | `mat-expansion-panel-header`; within the header: `mat-panel-title`, `mat-panel-description` |
| `mat-step` / `mat-stepper` | `matStepLabel` |
| `mat-list-item` | `matListItemTitle`, `matListItemLine`, `matListItemMeta` |
| `mat-select` / autocomplete | `mat-select-trigger` |

**Note:** `mat-option` inside `@for` is legitimate (the `@for` is the host's content, not between option and select) and is excluded.

## Detection Pipeline (Hybrid — Approach C)

1. **Prefilter (deterministic, robust).** Scan all `projects/portal`, `projects/em`, and `projects/shared` `*.html` templates for files where a catalog target co-occurs with any control-flow block (`@if`/`@for`/`@switch`). Use correct parenthesis handling so conditions containing method calls (e.g. `@if (isFoo())`) are not skipped — a flaw in an earlier ad-hoc scan made it wrongly report portal as having zero hits. Output: a candidate file list. portal and shared are re-checked properly here, not trusted from the earlier flawed scan.

2. **Classify (agent fan-out over the candidate files only).** Each agent applies the Detection Rule above and returns structured findings per occurrence:
   - `file:line`
   - host component and projected target
   - the offending `@if`/control-flow snippet
   - true-positive / false-positive verdict with reasoning
   - the proposed `*ngIf` rewrite

3. **Consolidate.** Merge findings into one human-readable candidate list (the review checkpoint). Discard false positives (e.g. `@if` legitimately wrapping a whole host).

## Fix Application

For each confirmed true positive, apply the #75272 pattern:

- Move the condition off the `@if` wrapper and onto the projected element as `*ngIf`, **preserving the condition verbatim** and all bindings/template refs (e.g. `#menuTrigger="matMenuTrigger"`).
- Ensure the affected component's `imports` array includes `NgIf`; add it if missing.

The transform is mechanical, minimal, and consistent across all sites. Control-flow blocks that wrap a *whole* host (the ✅ case) are left untouched.

## Verification & Delivery

- `ng build em` — and `ng build portal` if any portal hits — must succeed.
- `npx eslint` on every changed template/TS file must pass.
- Spot-check a representative sample (one per host type) by reasoning through the projection.
- Deliver as **one community PR** off `main` (branch `fix-if-projection-audit`), reviewer `jshobe-inetsoft`. The PR body carries the consolidated findings list as the audit record and references the related regressions (#75272 sweep, #75349, #75335).

## Out of Scope (YAGNI)

- Custom-component `ng-content select="…"` projection slots (Material only this pass).
- A lint rule / CI guardrail against reintroduction (verification is build + lint + spot-check).
- `@for` track-key bugs (e.g. #75333) and any other migration issues unrelated to projection.
- Enterprise-repo templates (the Angular code lives in the community submodule).

## Success Criteria

- Every `@if`-wrapped Material projection target in portal/em/shared is either fixed or explicitly recorded as a reviewed false positive.
- Builds and lint pass; no behavioral change other than restoring correct slot projection.
- A single reviewable PR with a findings list that documents what was scanned and what changed.
