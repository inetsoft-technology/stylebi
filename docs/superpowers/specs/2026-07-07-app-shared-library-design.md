# Design: Extract `app-shared` and `common` Angular Libraries

**Date:** 2026-07-07
**Branch:** `feature-app-shared-library` (community submodule)
**Scope:** `community/web` frontend workspace only

## Problem

In `community/web`, the `portal`, `elements`, and `viewer-element` Angular
projects all share the **same** source root (`projects/portal`) with the same
`tsconfig.app.json`, differing only by their `main` entry file:

| Project        | Root (today)     | Entry                        | Bootstraps                                   |
|----------------|------------------|------------------------------|----------------------------------------------|
| portal         | `projects/portal`| `main.ts`                    | `AppComponent` + `app.routes` (full app)     |
| elements       | `projects/portal`| `main-elements.ts`           | `<inetsoft-chart>` (embed/chart) web comp.   |
| viewer-element | `projects/portal`| `main-viewer-element.ts`     | `<inetsoft-viewer>` (embed/viewer) web comp. |

Consequences:

1. **The shared feature tree is compiled three times** — once per project build.
2. **Non-standard Angular structure** — three "applications" pointing at one
   source root is not how Angular workspaces are meant to be organized.

A fourth project, `em` (Enterprise Manager), is a separate application that also
consumes the plain-source `projects/shared` folder via relative imports.

## Goal

Refactor the workspace so shared code lives in a proper **buildable Angular
component library**, compiled **once**, and each of the three projects gets its
own directory — while `portal` keeps its `projects/portal` directory.

The buildable-library approach is required because it is the only option that
actually eliminates the 3× compilation: the apps consume a pre-built `dist`
artifact rather than recompiling shared source. (A source-only "shared folder"
with a path alias would still be compiled per app and was rejected.)

## Target Structure

```
projects/
  common/          ← LIBRARY (renamed from projects/shared)
                     util, data, schedule, download, stomp, ai-assistant,
                     sso, resize-event, ckeditor-wrapper, codemirror, testing
  app-shared/      ← LIBRARY (factored from projects/portal/src/app)
                     composer, portal, viewer, vsobjects, binding, graph,
                     widget, common, format, vsview, vs-wizard, status-bar,
                     embed, + AppComponent / app.config / app.routes
  portal/          ← APP shell: main.ts, index.html, styles.scss, polyfills,
                     assets, environments
  elements/        ← APP shell: main-elements.ts, elements.scss, index.html, …
  viewer-element/  ← APP shell: main-viewer-element.ts, viewer-element.scss, …
  em/              ← APP (existing): rewired to consume `common`
```

**Dependencies (must remain acyclic):** `app-shared` depends on `common`; the
three shells depend on `app-shared`; `em` depends on `common`. Equivalently, the
**build order** is `common` first, then `app-shared`, then
`{portal, elements, viewer-element}` (and `em` after `common`). `common` is the
leaf that everything builds on.

**Naming:** library import roots are `common` and `app-shared`. Note the
`app-shared` library internally contains an `app/common/` folder — this is
purely internal (relative imports) and never imported as a package, so there is
no collision with the `common` library.

## Library Boundary: Maximal

The entire feature tree moves into `app-shared`; the three projects become thin
bootstrap shells. Rationale:

- The feature modules are **bidirectionally coupled** — `vsobjects` imports
  `composer` 72×, `widget` 52×, `binding` 35×, etc. A "minimal" library (only
  cross-app-shared modules, portal-only features left behind) would require
  untangling that web of circular dependencies first: very high effort/risk.
- With a maximal library, **every cross-boundary import is one-directional
  (app → lib)** — no cycles between the shells and `app-shared` to break.
- Tree-shaking keeps each app's bundle lean, so `elements`/`viewer-element`
  still ship only what they use even though the library contains portal-only
  features (`composer`, `portal`, `viewer`).

## `common` as a UI-Neutral Leaf

`common` is consumed by both `app-shared` (portal → Bootstrap/`@ng-bootstrap`)
and `em` (Angular Material). Two hard invariants:

### Invariant 1 — No back-edges

`common` must not import from `portal/` or `em/`. Today it violates this in
**23 files / 58 import lines** hitting **~44 distinct symbols**. Each target is
triaged into one of two moves:

- **Pull *down* into `common`** — only if the target is (a) foundational/
  low-level, (b) UI-toolkit-free, and (c) drags no transitive UI/app deps.
  Safe set from the inventory: pure model/type files (`identity-id`,
  `idenity-id-with-label`, `data-ref`, `chart-ref`, `tree-node-model`,
  `time-instant`, `xschema`, `common-kv-model`, `local-storage.util`) and clean
  services (`base-href.service`, `heartbeat-worker.service`).
- **Push *up* / invert** — whenever the target touches a UI toolkit or is
  app-specific. Examples: `component-tool` (uses `@ng-bootstrap`),
  `parameter-dialog.component`, the binding-model usages inside
  `util/tool.ts` (extract into an `app-shared` binding helper — `tool.ts` itself
  is imported 930× and stays in `common`), and all `em/*` UI targets.

**Gate:** `grep -rE 'from "(\.\./)+(portal|em)/' projects/common` returns empty.

### Invariant 2 — UI-toolkit-free

`common` depends only on Angular core/common/forms/router/rxjs — never
`@ng-bootstrap/ng-bootstrap`, `bootstrap`, or `@angular/material`.

Two existing leaks to relocate up front:
- `util/localized-mat-paginator.ts` (`@angular/material/paginator`) → move to `em`.
- `util/guard/global-parameter-guard.service.ts` (`@ng-bootstrap`) → move to `app-shared`.

**Gate:** `grep -rE '@ng-bootstrap|@angular/material|from "bootstrap' projects/common`
returns empty.

Every "pull down" move must be followed by re-running both greps to confirm it
did not re-introduce a back-edge or a UI dependency.

## Public-API / Entry-Point Strategy

A buildable ng-packagr library only exposes what its **entry points** export;
consumers cannot deep-import arbitrary internal files.

### `app-shared` — single flat public API

The only imports crossing *into* `app-shared` come from the three thin shells —
a small enumerable set (~50 symbols):

- `AppComponent`, `appConfig` (portal shell)
- `EmbedChartComponent`, `embedElementConfig`, `embedChartRoutes` (elements shell)
- `EmbedViewerComponent` + the ~40 services/tokens listed in
  `main-viewer-element.ts`'s provider array (viewer-element shell)
- the `main-base-element` init (moves into `app-shared`, exported; both element
  shells call it)

Everything else stays internal and keeps its existing relative imports
unchanged. One `src/public-api.ts` re-exports the ~50 symbols.

### `common` — secondary entry points per folder

Every `app-shared → common` and `em → common` import crosses the library
boundary (~1900 import lines). Use one **secondary entry point per top-level
folder**, each a barrel (`public-api.ts` + `ng-package.json`):

`common/util`, `common/data`, `common/schedule`, `common/download`,
`common/stomp`, `common/ai-assistant`, `common/sso`, `common/resize-event`,
`common/ckeditor-wrapper`, `common/codemirror`, `common/testing`.

The folder layout does not move; barrels are added. The ~1900 deep imports
(`.../shared/util/tool` → `common/util`) are rewritten by a codemod.
Within-folder imports stay relative (internal to the entry point). esbuild
tree-shakes the barrels so app bundles stay lean.

**Risk:** symbol-name collisions within a barrel surface as TS duplicate-export
errors at build time; resolved by rename or by splitting the entry point.

## Build & Consumption Model

**Build order (acyclic):** `common` → `app-shared` → `{portal, elements,
viewer-element}`; `em` needs only `common`. `package.json` scripts prepend the
library builds.

**Production / CI:** apps consume the pre-built `dist` of `common` and
`app-shared` → **shared code compiled once** (the goal).

**Local `--watch` (this workspace does not use `ng serve`):** dual resolution
via two tsconfig `paths` sets using the *same* import statements:
- **prod config** → `common/*` and `app-shared` resolve to built `dist`.
- **dev/watch config** → the same aliases resolve to library **source**
  (`projects/common/*`, `projects/app-shared/src/public-api.ts`), so a watched
  app rebuilds incrementally without a full ng-packagr pass. Since only one app
  is watched at a time, per-app recompile of source does not bite.

Caveat: dev builds against source while prod builds against dist — a minor
partial-compilation divergence. A CI gate always builds the true dist path.

**Output paths unchanged:** the four app `outputPath` bases stay byte-identical
(`.../resources/app`, `/em`, `/elements`, `/viewer-element`), so Spring Boot
static serving and the gulp post-processing are unaffected.

## Gulp Post-Processing: Kept (elimination out of scope)

The `elements:*` / `viewer-element:*` gulp tasks perform genuine web-component
packaging that standard `ng build` does not, independent of project layout:

1. **Single-file concat** — the `@angular/build:application` builder emits
   multiple hashed chunks (`runtime.*`, `polyfills.*`, `scripts.*`, `main.*`); a
   custom element must ship as one predictably-named file (`elements.js` /
   `viewer-element.js`). Gulp concatenates them.
2. **CSS selector rewriting** — rewrites `inetsoft-chart :root` /
   `inetsoft-chart body` → `inetsoft-chart` (and `#inetsoft-viewer-overlay`
   variants), because inside a custom element there is no `:root`/`body`; global
   styles must re-anchor to the host. Also `@font-face` `@at-root` hoisting +
   `cssnano` minify.

**Decision:** keep gulp as-is. The only change: two `gulp.src(...)` scss source
paths move with their shells:
- `projects/portal/src/elements.scss` → `projects/elements/src/elements.scss`
- `projects/portal/src/viewer-element.scss` → `projects/viewer-element/src/viewer-element.scss`

`global.scss` (consumed by both the portal app and the gulp CSS-concat via its
`global.css` intermediate) gets a deliberate home in `app-shared`; both
consumers point at it.

Replacing gulp (single-bundle plugin + host-scoped style authoring) is a
separate investigation with its own correctness risk and is **out of scope**.

## Migration Mechanics

**Library scaffolding** (both `projectType: "library"` + `@angular/build:ng-packagr`):
- `common`: rename `projects/shared` → `projects/common`; add root
  `package.json` (name `common`), `ng-package.json`, `tsconfig.lib(.prod).json`,
  and a per-folder `ng-package.json` + `public-api.ts` for each ~11 secondary
  entry point.
- `app-shared`: `projects/app-shared/src/` holds the moved `app/` tree + shared
  top-level files (`main-base-element.ts`, `typings.d.ts`, `global.scss`); add
  `package.json` (name `app-shared`), `ng-package.json`,
  `tsconfig.lib(.prod).json`, flat `src/public-api.ts` (~50 symbols).

**App shells** (each its own `projectType: "application"` with its **own** build
options — `scripts` / `polyfills` / `allowedCommonJsDependencies` arrays are
replicated per shell, no longer shared through portal's root):
- `portal` keeps its dir; `app/` removed; retains `main.ts`, `index.html`,
  `styles.scss`, `polyfills.ts`, `assets`, `environments`; `main.ts` imports
  `AppComponent`/`appConfig` from `app-shared`.
- **new** `projects/elements` and `projects/viewer-element` — real application
  projects, each importing its entry component from `app-shared`, `outputPath`
  bases preserved exactly.
- `em` rewired: `../../../shared/*` → `common/*`.

**Codemods (scripted, mechanical):**
1. `shared/*` → `common/<entrypoint>` across `app-shared` + `em` (~1900 lines).
2. shell → `app-shared` entry imports.
3. back-edge removals (Invariant 1).

**Shared assets** are pointed to a single source dir from all shells to avoid
duplication; **`typings.d.ts`** lives in `app-shared` and is referenced by each
shell's tsconfig.

**tsconfig `paths`:** prod → dist, dev/watch → source (dual resolution above).

**Tests/lint:** specs move with their code; `common` and `app-shared` get their
own `test`/`test-tl` targets (vitest + MSW `vitest-setup*` files move along);
`package.json` gains `test:common`/`test:app-shared` and per-project lint
targets.

**gulp:** only the two scss `src` paths change.

## Sequencing (each phase independently buildable, gated by green `build:prod` + artifact diff vs. baseline)

0. **Baseline & prep** — capture a reference `build:prod` output tree + bundle
   sizes; land codemod scripts. No behavior change.
1. **Make `common` a clean leaf *in place*** — break all 58 back-edges,
   relocate the 2 UI leaks, still as plain source. *Gate:* both greps empty;
   portal + em build green. De-risks the hardest part before any library
   machinery.
2. **Promote `common` to a buildable library** — entry points + codemod ① +
   build order + angular.json. *Gate:* portal + em build consuming `common`
   dist; em tests pass.
3. **Factor `app-shared` library** — move `app/` tree, `public-api.ts` (~50),
   portal becomes a shell. *Gate:* portal builds on both libs.
4. **Split `elements` + `viewer-element` into own shells** — new app projects,
   fix gulp scss paths. *Gate:* all four outputs + gulp
   `elements.js`/`viewer-element.js`/CSS **diff-identical** to baseline.
5. **Cleanup** — dev/prod dual-resolution tsconfig, `package.json` scripts
   (`build`, `build:prod`, `build:watch`, `test*`, `lint*`), docs.

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Barrel symbol collisions in `common` entry points | Surface as TS duplicate-export errors in Phase 2; rename/split entry points. |
| A "pull down" drags transitive/UI deps back into `common` | Re-run both greps after each move (Phase 1 gate). |
| ng-packagr partial-compilation strictness on the huge `app-shared` (non-exported public-API types, decorator patterns) | Expect a batch in Phase 3; fix per error. |
| Web-component output divergence (chunk names, concat, CSS scoping) | Phase 4 artifact diff against baseline is the guardrail. |
| dev-vs-prod resolution divergence (source vs dist) | CI gate always builds the true dist path. |
| `em` regressions from the shared→common rewrite | em test suite is a Phase 2 gate. |

## Out of Scope

- Eliminating gulp post-processing.
- Restructuring `em` beyond the mechanical `shared → common` import rewrite.
- Any change to the four apps' runtime behavior or output artifact contents.
