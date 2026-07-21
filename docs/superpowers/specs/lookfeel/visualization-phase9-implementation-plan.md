# StyleBI Visualization — Phase 9 (Themeability And Exposure Review) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Confirm the visualization token layer shipped in Phases 1–8 is genuinely
runtime-themeable and backward-compatible (legacy default, modern only inside the gate), then make
the modern visualization **state palette** customer-overridable by exposing it in the EM theme
editor through a two-tier `-modern` default seam.

**Architecture:** Phase 9 is mostly a **verification/exposure review**, not new runtime behavior.
The one code change is additive and cascade-driven: the modern state colors are currently hardcoded
hex inside the `.viz-modern` block in `_viz-tokens.scss` (set on `<body>`), which shadows any
customer `:root` override, so they are not themeable today. The fix introduces `--inet-viz-*-modern`
**default** variables on `:root` and repoints the five adopted modern state colors to consume them
via `var()`; the theme editor (a hardcoded `portalCssData` array — the server side is free-form
pass-through) then exposes the `-modern` defaults so a customer override on `:root` flows into the
`.viz-modern` scope. Gate-off stays byte-identical because the legacy `:root` values and the modern
density blocks are untouched.

**Tech Stack:** SCSS (portal token contract), Angular/TypeScript (EM theme editor array), Java
properties (i18n message bundle). No Java model, controller, or service change — the theme pipeline
(`ThemeCssVariableModel` / `ThemeService.writeTempCssFile`) persists any `--inet-*` name generically.

## Global Constraints

Copied from the roadmap, design spec, and the Phase 1–8 plans; every task's requirements implicitly
include this section.

- **Gate-off byte-identical.** With the modern gate off, every surface — portal, composer, viewer,
  and every export format — must be pixel-identical to today. No legacy `:root` viz value and no
  server render path may change.
- **Modern only inside the gated scope.** Every modern DOM value must live under a `.viz-modern` /
  `[data-viz-theme="v2"]` selector (browser half) or behind a server-side org-scoped gate
  (`VSDensityDefaults.isModern()` + sub-gate). No modern value may leak to `:root` as a *consumed*
  token.
- **Render-location rule (unchanged from Phases 6/8).** Chart marks and in-graph chrome are
  server-rendered and appear in export; browser `--inet-viz-*` CSS cannot color them. The reserved
  `--inet-viz-chart-*` / `--inet-viz-ramp-*` / `--inet-viz-threshold-*` / `--inet-viz-conditional-*`
  names stay **un-wired** in the browser. Phase 9 does **not** expose any chart-color token in the
  theme editor.
- **Additive, no destructive rename.** Introduce the `-modern` default vars alongside the existing
  tokens; never rename or repoint the legacy `:root` aliases.
- **Naming.** New tokens use `--inet-viz-<role>-modern` (density/state family already uses
  `--inet-viz-<family>-<role>`). The EM heading i18n key is
  `em.presentation.lookAndFeel.css.heading.visualization`.
- **Scope discipline.** Expose only the **five adopted** modern state colors (Decision D2). Do not
  expose vocabulary-only tokens, density (an EM control, not a color picker), or any server-rendered
  color. Sub-gate EM UI is out of scope (Decision D4).

---

## Render / themeability boundary (read this first)

Two independent theming systems, verified against branch `viz-updates` (2026-07-17):

- **Browser DOM half** — `--inet-viz-*` CSS variables in
  `community/web/projects/portal/src/scss/_viz-tokens.scss`, gated by the `.viz-modern` /
  `viz-density-<mode>` body classes. These are the **only** viz surfaces a CSS theme variable can
  reach: DOM data-surface density (worksheet detail, dialog lists, schedule list) and live-view DOM
  state overlays (table row hover, combo dropdown selection, sort glyph). **This is Phase 9's
  exposure target.**
- **Server-rendered half** — viewsheet-assembly density, table structure, chart marks + in-graph
  chrome, object title bar, KPI/output chrome. Themed server-side via `format.css` `CSSConstants`
  classes, descriptor defaults, and `VSFormat`, gated by org-scoped `SreeEnv` properties. Customers
  already override these through a theme JAR's `format.css` and per-object descriptors; **Phase 9
  adds nothing here** and must not route any exported color through a browser token.

Consequence: "add customer-themeable viz tokens" means the browser-DOM state palette only. The
server-rendered color surfaces are a separate, already-existing customer-override path (verified in
Task 3).

---

## Grounding (verified against current code, branch `viz-updates`, 2026-07-17)

### Token contract and gate wiring

| Anchor | Location | Note |
|---|---|---|
| Viz token contract | `web/projects/portal/src/scss/_viz-tokens.scss` (111 lines) | `:root` legacy defaults (aliases to shell tokens) + `.viz-modern`/`[data-viz-theme="v2"]` modern state block (`:71-83`) + density matrices (`:92-110`). Imported by `global.scss:19` right after `variables`. |
| Modern state colors (the 5 to expose) | `_viz-tokens.scss:71,72,73,74,79` | `--inet-viz-hover-bg #EEF4F6` (`:71`), `--inet-viz-selected-bg #DDF1F5` (`:72`), `--inet-viz-selected-border #BFDDE5` (`:73`), `--inet-viz-selected-text #123C44` (`:74`), `--inet-viz-sorted-color #8C5510` (`:79`). |
| Portal body-class writer | `portal/src/app/portal/app.component.ts:88-90,260-273` | toggles `viz-modern` + `viz-density-<mode>` from `PortalModel.modernVisualization`/`vizDensity`. |
| Composer body-class writer | `portal/src/app/composer/app.component.ts:43-45,133-146` | same, from the same `PortalModel`. |
| Viewer body-class writer | `portal/src/app/vsobjects/viewer-app.component.ts:429-430,2750-2751,2782-2792` | from a STOMP command `info["modernVisualization"]`/`info["vizDensity"]`, applied only when `!inPortal`. |
| Server master gate | `core/.../uql/viewsheet/internal/VSDensityDefaults.java:39-41` | `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)` (3rd arg = org-scoped). |
| Server sub-gates | `VSChartChromeDefaults`, `VSTitleChromeDefaults`, `VSOutputChromeDefaults`, `VSChartPaletteDefaults`, `VSTableStructureDefaults` (same dir) | each `isModern()` = master `&&` `!"false".equals(SreeEnv.getProperty("viewsheet.modern<X>", false, true))` — opt-out only, default-on, **not surfaced in EM** (Decision D4). |

### Theme editor exposure surface

| Anchor | Location | Note |
|---|---|---|
| **The overridable-variable list** | `web/projects/em/src/app/settings/presentation/presentation-themes-view/theme-css-view/theme-css-view.component.ts:115-304` | `readonly portalCssData: ThemeCssVariableModel[]` — a hardcoded array of `{name, heading?, color?, autoGenLightDark?}`. **The single source of truth for what the editor exposes.** No `--inet-viz-*` entry today. Rows + reactive form controls + color pickers are auto-generated from this array (`getFormControls():398-419`, `initTableDataSource():421-471`). |
| Insertion point | `theme-css-view.component.ts:300-303` | new "visualization" section goes immediately **before** the `// script editor` heading (`:301`). |
| Entry model | `.../theme-css-variable-model.ts:18-25` | `{name; value?; heading?; color?; colorPickerActive?; autoGenLightDark?}`. |
| Server persistence (free-form) | `enterprise/.../web/admin/theme/ThemeService.java:634-662` (`writeTempCssFile`), `:378-448` (`loadThemeCssVariables`) | emits `:root { name: value; }` for every non-empty variable the client sends; reads back every declaration generically. **No Java change needed to add a token.** |
| i18n heading keys | `core/src/main/resources/inetsoft/util/srinter.properties:4459-4478` | alphabetical `em.presentation.lookAndFeel.css.heading.*`; `toolbars` (`:4477`), `worksheet` (`:4478`). New `visualization` key inserts between them. |

### Token adoption audit (which tokens are real)

Of 21 `--inet-viz-*` tokens, **9 are consumed by a selector; 12 are vocabulary-only.** The five
**adopted state color** tokens (the exposure set) and their consumers:

| Token | Consumed at |
|---|---|
| `--inet-viz-hover-bg` | `_themeable.scss:830` (`.table-data__row:hover`), `vs-combo-box.component.scss:171`, `vs-table.component.scss:108` |
| `--inet-viz-selected-bg` | `vs-combo-box.component.scss:176` (`.dropdown-item.selected`) |
| `--inet-viz-selected-text` | `vs-combo-box.component.scss:177` |
| `--inet-viz-selected-border` | `_themeable.scss:113` (`.bd-selected-cell`) |
| `--inet-viz-sorted-color` | `_themeable.scss:1269` (`.viz-modern .vs-header-cell-button-sort...`) |

Vocabulary-only (NOT exposed in Phase 9): `--inet-viz-active-border`, `-context-bg`,
`-inline-edit-bg`, `-filtered-bg`, `-pinned-divider`, `-warning-bg`, `-anomaly-bg`,
`-dimmed-opacity`, `-row-height`, `-chrome-row-height`, `-font-size`, `-mode`.

### The cascade problem this phase fixes (verified reasoning)

Today `--inet-viz-selected-bg: #DDF1F5` is set inside `.viz-modern` on `<body>`. A customer theme
writes overrides to `theme-variables.css` `:root { … }` (html element). Under the gate, an element
inside `<body>` inherits the custom property from the **nearest** ancestor that declares it — `body`
(`.viz-modern`) is closer than `html` (`:root`) — so the hardcoded `.viz-modern` value **always
shadows** a `:root` customer override. Exposing the base token name would therefore appear to work
gate-off but be silently ignored gate-on.

The two-tier fix (Task 4): declare `--inet-viz-selected-bg-modern: #DDF1F5` on `:root`, and set
`.viz-modern { --inet-viz-selected-bg: var(--inet-viz-selected-bg-modern); }`. Now the `.viz-modern`
block reads a `:root`-scoped variable, which the customer overrides on `:root` (later source order in
`theme-variables.css` wins over the app default `:root` — the same mechanism every existing shell
token override already relies on). Task 6 verifies this empirically.

---

## Decisions (resolved with initiative owner)

### D1 — Phase 9 is verification-first → **CONFIRMED**

Roadmap Phase 9 has five tasks: (1) tokens are runtime vars, (2) legacy correct gate-off, (3) modern
only in gated scope, (4) expose customer-themeable tokens where needed, (5) naming clarity. Four are
verification; one (task 4) is the sole code change. This plan front-loads verification (Part A) and
scopes the code change tightly (Part B).

### D2 — Exposure set = the 5 adopted modern state colors → **CONFIRMED (owner)**

Expose `--inet-viz-hover-bg`, `-selected-bg`, `-selected-text`, `-selected-border`, `-sorted-color`
as customer-overridable **modern** defaults. Rationale: these are the only state tokens that are
both (a) consumed by a real selector and (b) genuine brand-identity colors (the visualization teal
selection family + sort accent) a customer would reasonably want to retint. Vocabulary-only tokens
are not exposed until a selector adopts them (avoids advertising inert controls).

### D3 — Two-tier `-modern` seam, not base-name exposure → **CONFIRMED**

Because the `.viz-modern` value shadows a `:root` override (see cascade grounding), expose
`-modern`-suffixed default variables consumed inside the gate. Gate-off theming already works via
the existing shell-token overrides (the legacy viz values are `var(--inet-shell-*)` aliases), so no
gate-off exposure is needed.

### D4 — Sub-gate EM UI out of scope → **CONFIRMED (owner: leave as raw SreeEnv)**

The four sub-gates (`modernChartChrome`/`modernObjectChrome`/`modernChartPalette`/
`modernTableStructure`) stay advanced opt-out `SreeEnv` properties behind the single EM "Modern
Visualization" master toggle. Phase 9 documents them (Task 5); it does not add EM controls.

### D5 — Density and server color not re-exposed → **CONFIRMED**

Density is already an EM control (Phase 3 Part C: `visualizationDensity` select). Server-rendered
color (chart palette/chrome/table structure/title) is already customer-overridable via a theme JAR's
`format.css` + descriptors. Phase 9 verifies these paths (Task 3) but exposes nothing new for them.

---

## Parts

- **Part A — Themeability & compatibility verification (Tasks 1–3).** No production code; produces a
  Phase 9 review record with grep/build/manual evidence.
- **Part B — Modern state palette exposure (Tasks 4–5).** The SCSS two-tier refactor + the EM theme
  editor rows + i18n. The only shipped code.
- **Part C — Validation + docs (Tasks 6–7).** Manual customer-theme override proof; phase doc,
  roadmap status, swatch note.

---

## Task 1: Token inventory + runtime-var + naming-clarity audit (roadmap tasks 1 & 5)

**Files:**
- Create: `docs/superpowers/specs/lookfeel/visualization-phase9-review.md` (the review record)

**Interfaces:**
- Produces: a checked inventory table (token → runtime var? → consumed? → gated?) that Tasks 2–3
  append verification results to, and Task 7 references.

- [ ] **Step 1: Inventory every `--inet-viz-*` declaration**

Run:
```bash
cd community && rg -n "^\s*--inet-viz-" web/projects/portal/src/scss/_viz-tokens.scss
```
Expected: every token from Section 2 of `visualization-phase1-contract.md` appears, each declared as
a runtime CSS custom property (`--inet-viz-*: …;`). Confirm there are **zero** SCSS `$`-variables or
compile-time constants standing in for a viz token (the contract requires runtime `--inet-*`).

- [ ] **Step 2: Confirm no viz value is a hardcoded literal outside the token file**

Run:
```bash
cd community && rg -n "inet-viz" web/projects --glob '!**/scss/_viz-tokens.scss' | rg -v "var\(--inet-viz" || echo "CLEAN: only var() consumers outside the token file"
```
Expected: `CLEAN` — every use of a viz token outside `_viz-tokens.scss` is a `var(--inet-viz-*)`
read, never a redefinition. (This proves the token file is the single source of truth.)

- [ ] **Step 3: Record the inventory + naming-clarity finding**

Write `visualization-phase9-review.md` with a table: token | runtime var (Y) | consumed by selector
(Y/N + file:line from the Phase 9 adoption audit) | modern value gated (Y/N). Add a "Naming clarity"
note: the family follows `--inet-viz-<family>-<role>`; the new customer-facing tokens follow
`--inet-viz-<role>-modern` and appear under a "Visualization" editor heading. Flag the 12
vocabulary-only tokens as reserved (defined, not yet adopted) so a customer/reviewer is not misled.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-phase9-review.md
git commit -m "Phase 9: token inventory + runtime-var + naming-clarity audit (roadmap tasks 1,5)"
```

---

## Task 2: Gate-off byte-identical + modern-only-in-gated-scope verification (roadmap tasks 2 & 3)

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase9-review.md` (append the gate section)

**Interfaces:**
- Consumes: the built portal CSS.
- Produces: a recorded proof that (a) `:root` legacy viz values are unchanged and (b) every modern
  value sits under a gate selector.

- [ ] **Step 1: Prove every modern DOM value is inside a gate selector**

Run:
```bash
cd community && rg -n "^\s*--inet-viz-.*:\s*#|^\s*--inet-viz-.*:\s*[0-9]" web/projects/portal/src/scss/_viz-tokens.scss
```
Inspect: every **literal** (hex/number) modern value must fall inside a `.viz-modern` /
`[data-viz-theme="v2"]` / `.viz-modern.viz-density-*` block (lines 57+), never inside the top `:root`
block (lines 22–55). The only literal in `:root` is `--inet-viz-dimmed-opacity: 0.5` (legacy default,
intentional). Record the line ranges.

- [ ] **Step 2: Prove the `:root` legacy tier is shell-alias-only (gate-off inherits customer themes)**

Run:
```bash
cd community && sed -n '22,55p' web/projects/portal/src/scss/_viz-tokens.scss | rg "var\(--inet-" | wc -l
```
Expected: every consumed legacy state/density token in `:root` resolves to a `var(--inet-shell-*)` /
`var(--inet-*)` shell alias (so a customer shell-token override already flows to viz gate-off). This
is the evidence that **no new gate-off exposure is required** (Decision D3).

- [ ] **Step 3: Build the portal and confirm no compile/emit change gate-off**

Run:
```bash
cd community/web && npm run build
```
Expected: clean build. (Baseline for Task 4 — after the refactor the gate-off computed styles must
still match; verified manually in Task 6.)

- [ ] **Step 4: Confirm the server sub-gates are default-on / opt-out (modern only when master on)**

Run:
```bash
cd community && rg -n "modernChartChrome|modernObjectChrome|modernChartPalette|modernTableStructure" core/src/main/java/inetsoft/uql/viewsheet/internal/
```
Expected: each sibling resolver's `isModern()` is `VSDensityDefaults.isModern() && !"false".equals(...)`
— i.e. modern activates only when the org master gate is on. Record this as the server half of
"modern only inside the gated scope."

- [ ] **Step 5: Append the gate verification to the review doc + commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-phase9-review.md
git commit -m "Phase 9: verify gate-off byte-identical + modern-only-in-gate (roadmap tasks 2,3)"
```

---

## Task 3: Document the existing server-color customer-override path (roadmap task 4, non-exposure half)

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase9-review.md` (append the server-color
  section)

**Interfaces:** none (documentation task establishing that server color needs no new exposure).

- [ ] **Step 1: Confirm chart/table/chrome color is customer-overridable via `format.css`**

Run:
```bash
cd community && rg -n "ChartPalette|ChartAxisLine|ChartPlotLine|ChartLegend" core/src/main/resources/inetsoft/util/css/defaults.css | head
```
Expected: the shipped `CSSConstants` chart classes exist in `defaults.css`; a customer theme JAR's
`format.css` overrides them (the System B path from Phase 8). Record that server color theming is an
existing, separate mechanism — the theme editor's CSS-variable list does **not** and must **not**
drive it.

- [ ] **Step 2: Confirm no chart-color browser token is wired (render-boundary guard)**

Run:
```bash
cd community && rg -n "inet-viz-chart-series|inet-viz-ramp|inet-viz-threshold|inet-viz-conditional" web/projects
```
Expected: matches only the two reserved-name comment lines in `_viz-tokens.scss:53-54`; zero `var()`
consumers. Record: chart-color names stay conceptual/un-wired (Phase 1 §2c preserved).

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-phase9-review.md
git commit -m "Phase 9: document server-color override path is separate from theme-editor tokens"
```

---

## Task 4: Two-tier `-modern` seam for the five adopted state colors (SCSS)

**Files:**
- Modify: `community/web/projects/portal/src/scss/_viz-tokens.scss` (`:root` block ~`:45`; modern
  state block `:71-79`)

**Interfaces:**
- Produces (used by Task 5): five overridable defaults on `:root` —
  `--inet-viz-hover-bg-modern`, `--inet-viz-selected-bg-modern`, `--inet-viz-selected-text-modern`,
  `--inet-viz-selected-border-modern`, `--inet-viz-sorted-color-modern` — each defaulting to the
  current modern hex, consumed inside `.viz-modern`.

- [ ] **Step 1: Add the overridable modern defaults to `:root`**

In `_viz-tokens.scss`, immediately after the `--inet-viz-dimmed-opacity: 0.5;` line (`:45`, still
inside the `:root` block), add:

```scss
  // customer-overridable modern state colors (Phase 9). Defined on :root so a theme-variables.css
  // :root override wins; consumed only inside the .viz-modern block below, so gate-off is inert.
  --inet-viz-hover-bg-modern: #EEF4F6;
  --inet-viz-selected-bg-modern: #DDF1F5;
  --inet-viz-selected-text-modern: #123C44;
  --inet-viz-selected-border-modern: #BFDDE5;
  --inet-viz-sorted-color-modern: #8C5510;
```

- [ ] **Step 2: Repoint the five modern state values to consume the `-modern` defaults**

In the modern state block (`.viz-modern, [data-viz-theme="v2"]`, `:71-79`), change only these five
lines (leave the other modern values hardcoded — they are vocabulary-only):

```scss
  --inet-viz-hover-bg: var(--inet-viz-hover-bg-modern);
  --inet-viz-selected-bg: var(--inet-viz-selected-bg-modern);
  --inet-viz-selected-border: var(--inet-viz-selected-border-modern);
  --inet-viz-selected-text: var(--inet-viz-selected-text-modern);
  --inet-viz-sorted-color: var(--inet-viz-sorted-color-modern);
```

(The hardcoded default now lives on `:root`; the gate block references it. `active-border`,
`context-bg`, `inline-edit-bg`, `filtered-bg`, `pinned-divider`, `warning-bg`, `anomaly-bg` stay as
literals — not exposed.)

- [ ] **Step 3: Build and confirm no error**

Run:
```bash
cd community/web && npm run build
```
Expected: clean build.

- [ ] **Step 4: Verify gate-off is unchanged and gate-on default is unchanged (grep proof)**

Run:
```bash
cd community && sed -n '22,90p' web/projects/portal/src/scss/_viz-tokens.scss | rg "inet-viz-(hover|selected|sorted)"
```
Expected: the `:root` legacy lines (`var(--inet-shell-*)` / `var(--inet-hover-primary-bg-color)` /
`var(--inet-text-muted-color)`) are **unchanged**; the five modern lines now read
`var(--inet-viz-*-modern)`; the five `-modern` defaults hold the original hex. So with no customer
override, the gate-on computed color is byte-identical to before (a `var()` to the same hex), and
gate-off is untouched.

- [ ] **Step 5: Commit**

```bash
git add community/web/projects/portal/src/scss/_viz-tokens.scss
git commit -m "Phase 9: two-tier modern state-color seam so customer themes can override under the gate"
```

---

## Task 5: Expose the five modern state colors in the EM theme editor

**Files:**
- Modify: `community/web/projects/em/src/app/settings/presentation/presentation-themes-view/theme-css-view/theme-css-view.component.ts:300` (add a "visualization" section to `portalCssData`, before the `// script editor` block)
- Modify: `community/core/src/main/resources/inetsoft/util/srinter.properties:4477` (add the heading label)

**Interfaces:**
- Consumes: the five `-modern` defaults from Task 4.
- Produces: five color-picker rows under a "Visualization" heading in the Portal theme editor; on
  save they persist to the theme JAR's `theme-variables.css` `:root` (via the existing free-form
  `ThemeService.writeTempCssFile`).

- [ ] **Step 1: Add the i18n heading label**

In `srinter.properties`, insert alphabetically between `…heading.toolbars` (`:4477`) and
`…heading.worksheet` (`:4478`):

```properties
em.presentation.lookAndFeel.css.heading.visualization=Visualization
```

- [ ] **Step 2: Add the "visualization" section to `portalCssData`**

In `theme-css-view.component.ts`, immediately **before** the `// script editor` comment (`:301`), add:

```typescript
      // visualization (modern gate only)
      {name: "_#(js:em.presentation.lookAndFeel.css.heading.visualization)", heading: true},
      {name: "--inet-viz-hover-bg-modern", color: true},
      {name: "--inet-viz-selected-bg-modern", color: true},
      {name: "--inet-viz-selected-text-modern", color: true},
      {name: "--inet-viz-selected-border-modern", color: true},
      {name: "--inet-viz-sorted-color-modern", color: true},
```

(Rows + reactive form controls + color pickers auto-generate from this array via `getFormControls`
and `initTableDataSource` — no other TS wiring is needed. Do **not** set `autoGenLightDark`; these
are single flat values, not primary-accent families.)

- [ ] **Step 3: Build EM and confirm the section renders**

Run:
```bash
cd community/web && npm run build
```
Expected: clean build (the `_#(js:…)` key resolves against the new `srinter.properties` entry at
i18n-transform time).

- [ ] **Step 4: Run the theme-view spec suite (guard against array/form regressions)**

Run:
```bash
cd community/web && npx vitest run projects/em/src/app/settings/presentation/presentation-themes-view
```
Expected: existing theme-editor specs (`presentation-themes-view.component.spec.ts`,
`theme-editor-view.component.spec.ts`) still pass — the new rows must not break form-control
generation or the save/merge path. If a spec asserts an exact `portalCssData` length, update it to
include the five new rows + heading.

- [ ] **Step 5: Commit**

```bash
git add community/web/projects/em/src/app/settings/presentation/presentation-themes-view/theme-css-view/theme-css-view.component.ts \
        community/core/src/main/resources/inetsoft/util/srinter.properties
git commit -m "Phase 9: expose modern visualization state palette in the EM theme editor"
```

---

## Task 6: Manual themeability validation (customer override actually wins under the gate)

**Files:** none (verification). Requires a built/running server (`docker compose up` from
`docker/target/docker-test`) and EM access.

- [ ] **Step 1: Gate-off baseline unchanged**

With `viewsheet.modernVisualization` off (org default), open a viewsheet with a table and a combo
box; confirm row hover, selection, and sort-glyph colors are identical to pre-Phase-9 (legacy shell
values). No theme applied.

- [ ] **Step 2: Gate-on default unchanged**

Enable Modern Visualization for the org (EM → Look and Feel). Confirm the modern state palette
renders exactly as before Phase 9 (teal selection `#DDF1F5`, hover `#EEF4F6`, sort `#8C5510`) — the
two-tier refactor must be visually inert with no theme override.

- [ ] **Step 3: Customer override wins under the gate (the core proof)**

In EM → Presentation → Themes, create/edit a custom theme; under the new **Visualization** section
set `--inet-viz-selected-bg-modern` to a distinct color (e.g. `#FFD54F`). Save and assign the theme.
With the gate **on**, reload the viewsheet: the combo-box selected item and table selection must show
the custom color, proving the `:root` override reaches the `.viz-modern` scope through the `-modern`
seam. Inspect the served `theme-variables.css` and confirm it contains
`--inet-viz-selected-bg-modern: #FFD54F;` in the `:root` block.

- [ ] **Step 4: Override is inert gate-off (no leakage)**

With the same theme assigned but the gate **off**, confirm the override has no effect (the `-modern`
default is only consumed inside `.viz-modern`), and legacy colors render — modern theming does not
leak into legacy mode.

- [ ] **Step 5: Record results in the review doc**

Append the four observations to `visualization-phase9-review.md`. Note any surface where the override
did **not** take effect (would indicate a missed consumer or a cascade assumption to fix).

---

## Task 7: Phase 9 docs — roadmap status + swatch note

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md` (Phase 9 status)
- Modify: `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html` (state-palette note)

- [ ] **Step 1: Add a Phase 9 status note to the roadmap**

Under `## Phase 9`, add a "Status (implemented …)" note mirroring the Phase 4/5/6/8 notes: tokens
verified as runtime `--inet-viz-*` vars; gate-off byte-identical and modern-only-in-gate confirmed;
the five adopted modern **state colors** exposed as customer-overridable via a two-tier `-modern`
seam + a "Visualization" theme-editor section; density (EM control) and server-rendered color
(`format.css`/descriptors) verified as already-themeable and not re-exposed; sub-gate EM UI and
vocabulary-only token exposure deferred with grounded reasons.

- [ ] **Step 2: Annotate the swatches**

In the state-palette section of `visualization-palette-swatches.html`, note that the modern
selection/hover/sort colors are now customer-overridable through the theme editor
(`--inet-viz-*-modern`), defaults unchanged.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md \
        docs/superpowers/specs/lookfeel/visualization-palette-swatches.html
git commit -m "Phase 9: mark themeability review complete; record state-palette exposure + deferrals"
```

---

## Deferred / follow-ups (grounded)

1. **Vocabulary-only token exposure** — `--inet-viz-warning-bg`, `-anomaly-bg`, `-context-bg`,
   `-inline-edit-bg`, `-filtered-bg`, `-pinned-divider`, `-active-border` are defined but not adopted
   by any selector. Expose each only when a selector consumes it (avoids inert controls). The same
   two-tier `-modern` seam applies when they land.
2. **Sub-gate EM toggles** (D4) — surfacing `modernChartChrome`/`modernObjectChrome`/
   `modernChartPalette`/`modernTableStructure` as EM checkboxes; today they are raw-SreeEnv opt-out.
   A product/admin-UX decision, not a theming default.
3. **Density-token theming** — density is an EM select (Phase 3 Part C), not a color; per-org density
   is already configurable. Exposing raw row-height/padding vars in the theme editor is out of scope.
4. **Server-rendered color in the CSS theme editor** — must stay in `format.css`/descriptors
   (export-consistent); never route through a browser token (render-boundary rule).
5. **Dark-mode viz palette** — `series-dark-*` / modern dark state values ride the initiative's dark
   pass, unchanged from Phases 5–8.

---

## Self-Review (against roadmap Phase 9 tasks)

- **Task 1 — tokens are runtime `--inet-*` vars:** covered by Task 1 (inventory + grep proof).
- **Task 2 — legacy correct gate-off:** covered by Task 2 (`:root` alias proof + build) and Task 6.1/6.4.
- **Task 3 — modern only in gated scope:** covered by Task 2 (every literal under a gate selector;
  server sub-gates opt-out) and Task 6.2.
- **Task 4 — add customer-themeable tokens where needed:** covered by Tasks 4–5 (five adopted state
  colors via the two-tier seam + editor rows); server color + density verified as already-themeable
  (Task 3, D5); vocabulary-only deferred (grounded).
- **Task 5 — naming clarity:** covered by Task 1 Step 3 (`--inet-viz-<role>-modern`; "Visualization"
  heading; reserved tokens flagged).
- **Type/name consistency:** the five `-modern` var names in Task 4 Step 1 match the `var()`
  consumers in Task 4 Step 2 and the `portalCssData` rows in Task 5 Step 2 exactly; the i18n key in
  Task 5 Step 1 matches the `_#(js:…)` reference in Step 2.
- **Placeholder scan:** no TBD/"handle errors"/"similar to" — every code step shows exact content.

---

## Validation summary

1. **Build:** `cd community/web && npm run build` clean (portal + em).
2. **Specs:** theme-view spec suite green (Task 5.4).
3. **Gate-off byte-identical:** legacy `:root` viz values untouched; modern refactor inert without a
   theme (Task 6.1/6.2).
4. **Customer override wins gate-on:** `--inet-viz-selected-bg-modern` set in a theme recolors
   selection under the gate; present in served `theme-variables.css` (Task 6.3).
5. **No gate-off leakage:** override inert with the gate off (Task 6.4).
6. **Boundary:** no chart-color browser token wired; no server render path touched;
   `git diff --stat` shows only `_viz-tokens.scss`, `theme-css-view.component.ts`,
   `srinter.properties`, and the doc files.

---

## Branching (per CLAUDE.md)

Phase 9 is community-only (portal SCSS + EM Angular + core i18n properties + docs), continuing on
`viz-updates` alongside Phases 1–8. Confirm whether Phase 9 stays on `viz-updates` or a child
`feature-{issue}`. Nothing on `main` or a `v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd
without explicit approval. An enterprise submodule-pointer bump only at PR time (Phase 9 touches no
enterprise file — `ThemeService`/`ThemesController` are name-agnostic and need no change).

---

## Related

- [visualization-implementation-roadmap.md](./visualization-implementation-roadmap.md) — Phase 9
  goal/tasks and the themeability-and-compatibility mechanism
- [visualization-phase1-contract.md](./visualization-phase1-contract.md) — the `--inet-viz-*` token
  contract, §2c chart-color server-side constraint, §5 open ownership question this phase resolves
- [visualization-phase8-implementation-plan.md](./visualization-phase8-implementation-plan.md) — the
  server-rendered color path (System B) Phase 9 verifies but does not re-expose
- [visualization-design-spec.md](./visualization-design-spec.md) — Adoption And Compatibility
  Strategy, Rendering And Theming Architecture, Token Groups To Define
- [theme-strategy-overview.md](./theme-strategy-overview.md) — cross-layer theming intent
