# Visualization Phase 9 — Themeability & Exposure Review

This document records the read-only verification checks run for Phase 9 ("Themeability And Exposure
Review") of the StyleBI visualization theming initiative. It verifies:

1. Every `--inet-viz-*` token is a runtime CSS custom property, consumed only via `var()`, and the
   naming convention is clear about what's adopted vs. reserved.
2. Gate-off (legacy) is byte-identical to pre-Phase-1 behavior, and all modern literal values live
   inside the `.viz-modern` / `[data-viz-theme="v2"]` gate — both on the CSS side and the server
   `isModern()` side.
3. Server-rendered chart color continues to flow through the existing `format.css` / `defaults.css`
   mechanism, entirely separate from and unwired to the CSS-variable theme editor.

All commands were run from `E:/StyleBI/stylebi-enterprise/community`.

## 1. Token inventory & runtime-var & naming clarity

### 1a. Every viz token is a runtime custom property

Command:
```
rg -n "^\s*--inet-viz-" web/projects/portal/src/scss/_viz-tokens.scss
```

Output (21 declaration sites across the `:root` block, the `.viz-modern` mode/state blocks, and the
three density blocks):

```
24:  --inet-viz-row-height: var(--inet-row-height-md);
25:  --inet-viz-chrome-row-height: var(--inet-row-height-md);
26:  --inet-viz-cell-padding-x: var(--inet-space-4);
27:  --inet-viz-cell-padding-y: var(--inet-space-3);
28:  --inet-viz-toolbar-height: var(--inet-control-height-md);
29:  --inet-viz-control-height: var(--inet-control-height-sm);
30:  --inet-viz-font-size: var(--inet-font-size-base);
33:  --inet-viz-hover-bg: var(--inet-hover-primary-bg-color); // legacy = data-table row-hover hook (gate-off parity)
34:  --inet-viz-selected-bg: var(--inet-shell-selected-bg-color);
35:  --inet-viz-selected-text: var(--inet-selected-item-text-color);
36:  --inet-viz-selected-border: var(--inet-primary-color);
37:  --inet-viz-active-border: var(--inet-primary-color);
38:  --inet-viz-context-bg: var(--inet-shell-surface-subtle);
39:  --inet-viz-inline-edit-bg: var(--inet-shell-surface-default);
40:  --inet-viz-filtered-bg: var(--inet-shell-surface-subtle);
41:  --inet-viz-sorted-color: var(--inet-text-muted-color);
42:  --inet-viz-pinned-divider: var(--inet-border-strong-color);
43:  --inet-viz-warning-bg: var(--inet-warning-color-light);
44:  --inet-viz-anomaly-bg: var(--inet-danger-color-light);
45:  --inet-viz-dimmed-opacity: 0.5;
49:  --inet-viz-mode: legacy;
60:  --inet-viz-mode: modern;
71:  --inet-viz-hover-bg: #EEF4F6;
72:  --inet-viz-selected-bg: #DDF1F5;
73:  --inet-viz-selected-border: #BFDDE5;
74:  --inet-viz-selected-text: #123C44;
75:  --inet-viz-active-border: var(--inet-primary-color); // outline, no fill
76:  --inet-viz-context-bg: #F7F3EA;
77:  --inet-viz-inline-edit-bg: #FFFFFF;
78:  --inet-viz-filtered-bg: #F6E2C8;
79:  --inet-viz-sorted-color: #8C5510;
80:  --inet-viz-pinned-divider: #C8C2B7;
81:  --inet-viz-warning-bg: #F8E8CC;
82:  --inet-viz-anomaly-bg: #F7DEDE;
93:  --inet-viz-row-height: 20px;
94:  --inet-viz-chrome-row-height: 22px;
95:  --inet-viz-cell-padding-x: var(--inet-space-2); // 4px
96:  --inet-viz-cell-padding-y: 3px;
97:  --inet-viz-toolbar-height: 24px;
98:  --inet-viz-control-height: 24px;
99:  --inet-viz-font-size: 12px; // only tier that reduces type
103:  --inet-viz-row-height: 24px;
104:  --inet-viz-chrome-row-height: 26px;
105:  --inet-viz-cell-padding-x: var(--inet-space-3); // 6px
106:  --inet-viz-cell-padding-y: var(--inet-space-2); // 4px
107:  --inet-viz-toolbar-height: 28px;
108:  --inet-viz-control-height: 28px;
109:  --inet-viz-font-size: var(--inet-font-size-base); // 13px
113:  --inet-viz-row-height: 28px;
114:  --inet-viz-chrome-row-height: 30px;
115:  --inet-viz-cell-padding-x: var(--inet-space-4); // 8px
116:  --inet-viz-cell-padding-y: var(--inet-space-3); // 6px
117:  --inet-viz-toolbar-height: 30px;
118:  --inet-viz-control-height: 30px;
119:  --inet-viz-font-size: var(--inet-font-size-base); // 13px
```

Every match is a `--inet-viz-*: …;` custom-property declaration. A follow-up check for SCSS
`$`-variables standing in for a viz token —

```
rg -n '\$inet-viz|\$viz-' web/projects/portal/src/scss/_viz-tokens.scss
```

— returned no matches (exit code 1 / empty).

**Verdict: PASS.** Every viz token is a runtime CSS custom property; zero SCSS `$`-variables or
compile-time constants stand in for a viz token.

### 1b. Every consumer outside the token file reads via `var()`

Command:
```
rg -n "inet-viz" web/projects --glob '!**/scss/_viz-tokens.scss' | rg -v "var\(--inet-viz"
```

Output: empty (no lines printed).

**Verdict: PASS — CLEAN: only var() consumers (and comments) outside the token file.**

### 1c. Adoption inventory

| Token | Runtime var | Consumed by a selector | Modern value gated |
|---|---|---|---|
| `--inet-viz-cell-padding-x` | Y | Y | Y |
| `--inet-viz-cell-padding-y` | Y | Y | Y |
| `--inet-viz-toolbar-height` | Y | Y | Y |
| `--inet-viz-control-height` | Y | Y | Y |
| `--inet-viz-hover-bg` | Y | Y | Y |
| `--inet-viz-selected-bg` | Y | Y | Y |
| `--inet-viz-selected-text` | Y | Y | Y |
| `--inet-viz-selected-border` | Y | Y | Y |
| `--inet-viz-sorted-color` | Y | Y | Y |
| `--inet-viz-row-height` | Y | N (vocabulary-only) | Y |
| `--inet-viz-chrome-row-height` | Y | N (vocabulary-only) | Y |
| `--inet-viz-font-size` | Y | N (vocabulary-only) | Y |
| `--inet-viz-active-border` | Y | N (vocabulary-only) | Y (no-op: same value as legacy) |
| `--inet-viz-context-bg` | Y | N (vocabulary-only) | Y |
| `--inet-viz-inline-edit-bg` | Y | N (vocabulary-only) | Y |
| `--inet-viz-filtered-bg` | Y | N (vocabulary-only) | Y |
| `--inet-viz-pinned-divider` | Y | N (vocabulary-only) | Y |
| `--inet-viz-warning-bg` | Y | N (vocabulary-only) | Y |
| `--inet-viz-anomaly-bg` | Y | N (vocabulary-only) | Y |
| `--inet-viz-dimmed-opacity` | Y | N (vocabulary-only) | N (stays 0.5 in both legacy and modern) |
| `--inet-viz-mode` | Y | N (vocabulary-only — a mode flag, not a style value) | Y (`legacy` → `modern`) |

9 adopted (consumed by a selector) + 12 vocabulary-only (defined, not consumed) = 21 tokens total,
matching the brief's restated adoption data.

Spot-checks:

```
rg -n "var\(--inet-viz-selected-bg\)" web/projects
```
→ `web/projects/portal/src/app/vsobjects/objects/combo-box/vs-combo-box.component.scss:176: background-color: var(--inet-viz-selected-bg);`
(a real consumer, as expected).

```
rg -n "var\(--inet-viz-filtered-bg\)" web/projects
```
→ empty (no consumer, as expected — vocabulary-only).

Additional spot-checks:
```
rg -n "var\(--inet-viz-sorted-color\)|var\(--inet-viz-toolbar-height\)|var\(--inet-viz-row-height\)|var\(--inet-viz-mode\)" web/projects
```
→
```
web/projects/portal/src/scss/_themeable.scss:1269:  color: var(--inet-viz-sorted-color) !important;
web/projects/portal/src/app/portal/schedule/schedule-task-list/schedule-task-list.component.scss:97:      min-height: var(--inet-viz-toolbar-height);
web/projects/portal/src/app/portal/schedule/schedule-task-list/schedule-task-list.component.scss:112:        min-height: var(--inet-viz-toolbar-height);
```
`--inet-viz-sorted-color` and `--inet-viz-toolbar-height` (both in the "adopted" list) show real
consumers; `--inet-viz-row-height` and `--inet-viz-mode` (both "vocabulary-only") show none, confirming
the classification.

**Naming clarity note:** the family follows `--inet-viz-<family>-<role>` (e.g.
`--inet-viz-cell-padding-x`, `--inet-viz-selected-bg`). Customer-facing tokens added in a later task
follow `--inet-viz-<role>-modern` and are surfaced under a "Visualization" heading in the theme editor.
The 12 vocabulary-only tokens listed above are reserved: they are defined and legacy/modern-gated but
not yet consumed by any selector, so nobody should be misled into thinking theming them has a visible
effect today.

**Verdict: PASS** — inventory matches the restated adoption data; spot-checks confirm the
adopted/vocabulary-only split.

## 2. Gate-off + modern-only-in-gate

### 2a. Every literal modern value is inside a gate block

Command:
```
rg -n "^\s*--inet-viz-.*:\s*#|^\s*--inet-viz-.*:\s*[0-9]" web/projects/portal/src/scss/_viz-tokens.scss
```

Output:
```
45:  --inet-viz-dimmed-opacity: 0.5;
71:  --inet-viz-hover-bg: #EEF4F6;
72:  --inet-viz-selected-bg: #DDF1F5;
73:  --inet-viz-selected-border: #BFDDE5;
74:  --inet-viz-selected-text: #123C44;
76:  --inet-viz-context-bg: #F7F3EA;
77:  --inet-viz-inline-edit-bg: #FFFFFF;
78:  --inet-viz-filtered-bg: #F6E2C8;
79:  --inet-viz-sorted-color: #8C5510;
80:  --inet-viz-pinned-divider: #C8C2B7;
81:  --inet-viz-warning-bg: #F8E8CC;
82:  --inet-viz-anomaly-bg: #F7DEDE;
93:  --inet-viz-row-height: 20px;
94:  --inet-viz-chrome-row-height: 22px;
96:  --inet-viz-cell-padding-y: 3px;
97:  --inet-viz-toolbar-height: 24px;
98:  --inet-viz-control-height: 24px;
99:  --inet-viz-font-size: 12px;
103:  --inet-viz-row-height: 24px;
104:  --inet-viz-chrome-row-height: 26px;
107:  --inet-viz-toolbar-height: 28px;
108:  --inet-viz-control-height: 28px;
113:  --inet-viz-row-height: 28px;
114:  --inet-viz-chrome-row-height: 30px;
117:  --inet-viz-toolbar-height: 30px;
118:  --inet-viz-control-height: 30px;
```

Full-file read of `_viz-tokens.scss` (120 lines) establishes the block boundaries:

- `:root { … }` — lines 22–55. Only literal inside: line 45, `--inet-viz-dimmed-opacity: 0.5`.
- `.viz-modern, [data-viz-theme="v2"] { --inet-viz-mode: modern; }` — lines 58–61 (no literal
  hex/number values; `modern` is a keyword, not matched by the regex).
- `.viz-modern, [data-viz-theme="v2"] { … }` (state palette) — lines 69–84. Literals at 71, 72, 73,
  74, 76, 77, 78, 79, 80, 81, 82 — all hex colors, all inside this block.
- `.viz-modern, .viz-modern.viz-density-dense { … }` — lines 91–100. Literals at 93, 94, 96, 97, 98,
  99 — all inside this block.
- `.viz-density-compact { … }` (under `.viz-modern.viz-density-compact`) — lines 102–110. Literals at
  103, 104, 107, 108 — all inside this block.
- `.viz-density-comfortable { … }` (under `.viz-modern.viz-density-comfortable`) — lines 112–120.
  Literals at 113, 114, 117, 118 — all inside this block.

Every literal outside line 45 falls inside a `.viz-modern` / `[data-viz-theme="v2"]` /
`.viz-modern.viz-density-*` block. The only literal in `:root` is `--inet-viz-dimmed-opacity: 0.5`, as
expected (and it is intentionally *not* overridden in the modern state block — line 83 comments that it
keeps the `:root` value in modern too).

**Verdict: PASS.** No modern literal leaks into `:root`.

### 2b. Legacy `:root` tokens resolve to shell aliases

Command:
```
sed -n '22,55p' web/projects/portal/src/scss/_viz-tokens.scss
```

Output (see full text above in 2a's context / Section 1a). Every consumed legacy token in `:root`
resolves to a `var(--inet-*)` / `var(--inet-shell-*)` alias:

- `--inet-viz-row-height` → `var(--inet-row-height-md)`
- `--inet-viz-chrome-row-height` → `var(--inet-row-height-md)`
- `--inet-viz-cell-padding-x` → `var(--inet-space-4)`
- `--inet-viz-cell-padding-y` → `var(--inet-space-3)`
- `--inet-viz-toolbar-height` → `var(--inet-control-height-md)`
- `--inet-viz-control-height` → `var(--inet-control-height-sm)`
- `--inet-viz-hover-bg` → `var(--inet-hover-primary-bg-color)`
- `--inet-viz-selected-bg` → `var(--inet-shell-selected-bg-color)`
- `--inet-viz-selected-text` → `var(--inet-selected-item-text-color)`
- `--inet-viz-selected-border` → `var(--inet-primary-color)`
- `--inet-viz-sorted-color` → `var(--inet-text-muted-color)`
- (etc. — every non-literal entry in the block is a `var(--inet-*)` reference)

The only exception is the literal `--inet-viz-dimmed-opacity: 0.5` (not a themeable shell color/size —
an opacity constant with no shell equivalent).

**Verdict: PASS.** No new gate-off exposure is required: a customer shell-token override (e.g.
overriding `--inet-shell-selected-bg-color`) already flows through to the legacy viz tokens without any
change to `_viz-tokens.scss`.

### 2c. Server sub-gates require the org master gate

Command:
```
rg -n "modernChartChrome|modernObjectChrome|modernChartPalette|modernTableStructure" core/src/main/java/inetsoft/uql/viewsheet/internal/
```

Output:
```
VSChartChromeDefaults.java:48:   !"false".equals(SreeEnv.getProperty("viewsheet.modernChartChrome", false, true));
VSOutputChromeDefaults.java:42:  * Shares the viewsheet.modernObjectChrome gate with VSTitleChromeDefaults so one admin toggle covers all
VSOutputChromeDefaults.java:56:  !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
VSChartPaletteDefaults.java:40:  !"false".equals(SreeEnv.getProperty("viewsheet.modernChartPalette", false, true));
VSTableStructureDefaults.java:44: !"false".equals(SreeEnv.getProperty("viewsheet.modernTableStructure", false, true));
VSTitleChromeDefaults.java:53:   !"false".equals(SreeEnv.getProperty("viewsheet.modernObjectChrome", false, true));
```

Follow-up with 3 lines of leading context on each `isModern()` confirms the shared pattern in all four
classes (`VSChartChromeDefaults`, `VSChartPaletteDefaults`, `VSTableStructureDefaults`,
`VSTitleChromeDefaults`; `VSOutputChromeDefaults` shares `VSTitleChromeDefaults`'s gate per its own
doc comment):

```java
return VSDensityDefaults.isModern() &&
   !"false".equals(SreeEnv.getProperty("viewsheet.<sibling>", false, true));
```

Each sibling resolver's `isModern()` is `VSDensityDefaults.isModern() && !"false".equals(...)` — i.e.
each per-feature sub-gate can only turn ON when the org master density gate
(`VSDensityDefaults.isModern()`) is already on; a sibling property can only opt a feature back OUT
(via an explicit `"false"`), never opt it in independently of the master gate.

**Verdict: PASS.** This is the server-side half of "modern only inside the gated scope," symmetric
with the CSS-side `.viz-modern` gate confirmed in 2a/2b. No Java was modified.

## 3. Server-color override path

### 3a. Server chart color classes exist in `defaults.css`

Command:
```
rg -n "ChartPalette|ChartAxisLine|ChartPlotLine|ChartLegend" core/src/main/resources/inetsoft/util/css/defaults.css | head
```

Output:
```
18:ChartPalette[name='Default'][index='1'] {
22:ChartPalette[name='Default'][index='2'] {
26:ChartPalette[name='Default'][index='3'] {
30:ChartPalette[name='Default'][index='4'] {
34:ChartPalette[name='Default'][index='5'] {
38:ChartPalette[name='Default'][index='6'] {
42:ChartPalette[name='Default'][index='7'] {
46:ChartPalette[name='Default'][index='8'] {
50:ChartPalette[name='Default'][index='9'] {
54:ChartPalette[name='Default'][index='10'] {
```

Shipped `CSSConstants` chart classes (`ChartPalette[...]`) exist in `defaults.css`, confirming
server-side chart color resolves through the `CSSDictionary` / `format.css` mechanism (a customer
theme JAR's `format.css` can override these class rules).

**Verdict: PASS.** Server color theming is an existing, separate mechanism (System B) that the
CSS-variable theme editor does not, and must not, drive.

### 3b. Chart-color token names are conceptual-only, unwired

Command:
```
rg -n "inet-viz-chart-series|inet-viz-ramp|inet-viz-threshold|inet-viz-conditional" web/projects
```

Output:
```
web/projects/portal/src/scss/_viz-tokens.scss:53:  // --inet-viz-chart-series-*, --inet-viz-ramp-sequential-*, --inet-viz-ramp-diverging-*,
web/projects/portal/src/scss/_viz-tokens.scss:54:  // --inet-viz-threshold-*, --inet-viz-conditional-*
```

Both matches are the reserved-name comment lines in `_viz-tokens.scss` (lines 53–54, inside the
"chart color: RESERVED / CONCEPTUAL ONLY" comment block). There are zero `var()` consumers and zero
actual `--inet-viz-*` custom-property declarations for these names anywhere in `web/projects`.

**Verdict: PASS.** Chart-color names stay conceptual/un-wired — they exist only as a documented,
reserved vocabulary for a future task, not as live CSS custom properties.

## Summary

All three verification sections PASS with no findings:

1. Every `--inet-viz-*` entry is a runtime custom property; no SCSS `$`-variables stand in for a
   token; every external use is a `var()` read; the 9 adopted / 12 vocabulary-only split matches the
   restated adoption data and spot-checks.
2. Gate-off is byte-identical — the only literal in `:root` is the opacity constant; every other
   literal modern value lives inside a `.viz-modern` / `[data-viz-theme="v2"]` / density block; every
   consumed legacy token resolves to a `var(--inet-shell-*)`/`var(--inet-*)` alias so shell overrides
   already propagate. The server-side sub-gates (`VSChartChromeDefaults`, `VSChartPaletteDefaults`,
   `VSTableStructureDefaults`, `VSTitleChromeDefaults`/`VSOutputChromeDefaults`) all require
   `VSDensityDefaults.isModern()` before their own property can activate modern behavior.
3. Server-rendered chart color continues to resolve via the existing `defaults.css` /
   customer-`format.css` `CSSConstants` mechanism, entirely separate from and unwired to the
   CSS-variable theme editor; the reserved chart-color token names appear only as comments, never as
   live properties or `var()` consumers.

No production code was changed and no commits were made as part of this verification task.

## Deferred / open follow-ups (not lost)

These surfaced during the Phase 9 exposure review and are recorded so they are not lost. None blocks
Phase 9; each is a scoped future item.

1. **Server-side selection-state (`selected` fill + text) bridge for viewsheet tables and selection
   lists.** The exposed `--inet-viz-selected-bg` / `--inet-viz-selected-text` tokens are consumed only
   by transient live-only DOM (the combo-box dropdown pick list). On the real BI data surfaces —
   viewsheet tables and selection lists — the selected cell/item **fill and text color are
   server-rendered from `VSFormat`** (baked into the live model *and* every export format:
   `vs-table.component.html:271` ← `BaseTableCellModel` ← `VSFormatModel`), so a browser `--inet-viz-*`
   CSS token cannot drive them without desyncing live view from PDF/PNG/Excel/PPT export. Modernizing
   them requires a **gated server-side `VSFormat` resolver** (mirroring `VSDensityDefaults` /
   `VSTableStructureDefaults` / the chrome/palette resolvers), defaults-only and org-gated, because it
   changes export output and reflows saved sheets. Phase 4 pointed this at Phase 5; Phase 5 Decision D1
   shipped the zero-export-impact CSS half (row hover, selection **border/outline**, sort glyph) and
   **deferred** the export-affecting selected-fill/text bridge. It remains unscheduled — a candidate
   for a future phase, not something Phase 9 should wire into the browser. Until it lands, a customer
   override of `--inet-viz-selected-bg` / `-selected-text` in the theme editor visibly affects the
   combo-box dropdown but not viewsheet table / selection-list selection.
2. **Vocabulary-only token exposure.** The 12 unadopted tokens (e.g. `--inet-viz-filtered-bg`,
   `-context-bg`, `-inline-edit-bg`, `-pinned-divider`, `-warning-bg`, `-anomaly-bg`) are not exposed
   in the theme editor; expose each only when a selector adopts it, via the same two-tier `-modern`
   seam, so no inert control is advertised.
3. **Shell/Composer selection primitives stay shell-owned.** `.bg-selected`, `%selected-item-colors`,
   `.bg-node-selected` were deliberately not repointed to viz tokens (Phase 5): they live on
   navigation/Composer surfaces, and repointing would violate layer ownership and the data-vs-Composer
   selection-distinctness rule.
4. **Sub-gate EM UI, density-token theming, server-rendered color, dark-mode viz palette** — deferred
   with grounded reasons in the Phase 9 plan's Deferred section (sub-gates stay raw-`SreeEnv` opt-out;
   density is an EM control; server color is themeable via `format.css`/descriptors; dark mode rides
   the initiative's dark pass).
