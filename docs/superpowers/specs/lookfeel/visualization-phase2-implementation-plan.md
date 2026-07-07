# StyleBI Visualization — Phase 2 (Compatibility Gate) — As Built

**Status: implemented (option 2b). Community core + frontend build clean.**

## Scope

Phase 2 introduces the **compatibility gate**: an explicit, opt-in switch that keeps legacy
visualization as the default while allowing a `modern` path to be activated. Its whole job is
*safety* — establish the switch and the additive scope, not to change any look yet.

Roadmap Phase 2 tasks, as delivered: (1) legacy stays the default, (2) a small explicit activation
mechanism (a single admin toggle), (3) additive-only rule — the modern scope adds `--inet-viz-mode`
and will hold modern token values later; no legacy selector is repointed.

Not in this phase: modern token values (Phase 3/4), adoption in real widget selectors, any server
render-path change for charts/tables (Phase 3/8).

**Definition of done (met):** with the gate **off** (default) computed styles are byte-identical to
today; with it **on**, `<body>` carries `viz-modern` and `--inet-viz-mode` computes to `modern`. No
visible change yet — nothing consumes the token — which is the correct Phase 2 end state.

## Grounding — two findings that shaped the design

**1. Theming is server-decided CSS, not client class toggling.** The static roots carry no theme
class/attribute; a theme is delivered by the server serving a generated `theme-variables.css` that
overrides `:root` `--inet-*` properties (`GlobalStyleController`, resolving the current org's theme).
That file is `<link>`-ed after `global.css`, so its `:root` overrides win. The one precedent for a
*client* root-class flag is `accessible` (a `PortalModel` boolean applied as a body class in
`PortalAppComponent`).

**2. Per-org theming is effectively enterprise-only.** Verified in code:
`CustomThemesManager` reflectively loads an enterprise impl; the **community stub returns an empty
theme set** (`CustomThemesImpl`), so the org Theme dropdown only ever shows "Default". More
decisively, the organization edit screen itself requires `SUtil.isMultiTenant()`, which requires
`LicenseManager.isEnterprise()`. So an `Organization`-field property and the org edit screen are
**not reachable in a plain community build**. This ruled out the first-pass placement (an org
property surfaced on the org edit screen) and moved the toggle to **Presentation › Look and Feel**,
which is the only Presentation tab visible in community.

## What was built

### The switch — a SreeEnv property on Look and Feel

The gate is driven by a single boolean property **`viewsheet.modernVisualization`**, surfaced as a
**"Modern Visualization"** checkbox on **EM › Settings › Presentation › Look and Feel**. This page
already owns appearance/branding, is community-visible, and needs no new controller (it rides the
existing `/api/em/settings/presentation/model` GET/POST).

Read/write uses the page's standard org-scope pattern (mirroring `portal.customLogo.enabled`):

- **Write** (`LookAndFeelService.setModel`): `SreeEnv.setProperty("viewsheet.modernVisualization",
  Boolean.toString(model.modernVisualization()), !globalSettings)` — writes the **global** key in
  community (`globalSettings` is always true there, since org scoping needs enterprise) and the
  **org-scoped** key (`inetsoft.org.<id>.…`) in enterprise org-settings mode.
- **Settings read** (`LookAndFeelService.getModel`):
  `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, !globalProperty)`.
- **Consumption read** (render time): `SreeEnv.getBooleanProperty("viewsheet.modernVisualization",
  false, true)` — `orgScope=true` resolves the org-scoped value **if set**, otherwise **falls back
  to the global** value (`PropertiesEngine.useAvailableOrgProperty`). So one call is correct in both
  editions: per-org in enterprise, global toggle in community.

This satisfies the "per-org where orgs exist" intent while actually functioning in community — which
a raw `Organization` field could not.

### The DOM hook — `body.viz-modern` from the flag

The property is carried to the browser and applied as a body class (the `accessible` pattern):

- **Portal** (covers the portal shell and embedded viewsheets): `PortalController` reads the property
  → `PortalModel.modernVisualization()` → `portal-model.ts` → `PortalAppComponent.updateVisualizationMode()`
  toggles `body.viz-modern` (`VIZ_MODERN_CLASS`).
- **Standalone viewer** (direct viewsheet links outside the portal): `CoreLifecycleService` puts
  `modernVisualization` into the `SetViewsheetInfoCommand` `info` map → `viewer-app.component.ts`
  applies `body.viz-modern`, guarded by `!inPortal` (embedded viewers inherit the portal's class).

### The CSS scope — `_viz-tokens.scss`

```scss
:root {
  // ... Phase 1 legacy defaults ...
  --inet-viz-mode: legacy;
}

// Modern gate scope (DOM surfaces only), toggled per org via body.viz-modern.
.viz-modern,
[data-viz-theme="v2"] {
  --inet-viz-mode: modern;
}
```

Legacy stays the default on `:root`; the scope only flips the mode flag in Phase 2. Modern token
values (and the `--inet-viz-legacy-*` / `--inet-viz-modern-*` alias palette) are the Phase 3/4 fill.
No canary was left in the code — the branch has no stray visual change.

**Boundary (carried from Phase 0/1):** this gate is the **DOM half only**. It cannot switch
server-rendered surfaces — chart marks/in-graph chrome and viewsheet-assembly density/format. Phase
3/8 will read the **same `viewsheet.modernVisualization` property on the server render path**, so the
DOM half and the server-rendered half share one switch and cannot diverge.

## Files changed

**Backend (4):**
- `web/admin/presentation/model/LookAndFeelSettingsModel.java` — `modernVisualization()` field
- `web/admin/presentation/LookAndFeelService.java` — read in `getModel`, write in `setModel`
- `web/portal/controller/PortalController.java` — read property → `PortalModel` builder
- `web/portal/model/PortalModel.java` — `modernVisualization()` (`@Value.Default false`)
- `web/viewsheet/service/CoreLifecycleService.java` — read property → viewsheet info map

**Frontend (6):**
- EM Look and Feel: `look-and-feel-settings-model.ts` (field),
  `look-and-feel-settings-view.component.ts` (form control + model setter + emit),
  `look-and-feel-settings-view.component.html` ("Modern Visualization" checkbox)
- Portal: `portal-model.ts` (field), `portal/app.component.ts` (`viz-modern` applier)
- Viewer: `vsobjects/viewer-app.component.ts` (standalone `viz-modern` applier)

**CSS (1):** `portal/src/scss/_viz-tokens.scss` — `.viz-modern` gate scope.

(First-pass org-property machinery — `Organization`, `FSOrganization`, `EditOrganizationPaneModel`,
`UserTreeService`, `IdentityService`, and the three EM edit-identity files — was reverted when the
enterprise-theming finding moved the toggle to Look and Feel.)

## Verification

- **Builds:** community core `mvn compile` and `npm run build` both exit 0, no warnings referencing
  the changed files. Immutables regenerated for `PortalModel` and `LookAndFeelSettingsModel`.
- **Inertness / compatibility:** the property defaults `false` everywhere; the `.viz-modern` scope
  matches nothing when the class is absent, so gate-off is byte-identical to today.
- **Runtime (needs a running server):** EM › Settings › Presentation › Look and Feel → tick
  **Modern Visualization** → Apply → reload portal → `<body>` carries `viz-modern` and
  `--inet-viz-mode` computes to `modern`. Charts and viewsheet tables are unchanged (DOM-half
  boundary). Untick → class and modern value clear.

## Deferred / follow-ups

- **Composer canvas:** not gated. It has no `get-portal-model` / info-map flow, so it would be
  net-new plumbing, and it overlaps Composer's own ownership. Add later if the modern look must apply
  while authoring.
- **Server `:root` value-swap emission (path A):** deferred — there are no modern token values to
  emit until Phase 3. When added, the same property can drive a server-emitted `:root` override for
  value-level swaps in addition to the body class (which covers structural rules).
- **i18n:** the `_#(Modern Visualization)` label renders literally until a catalog entry is added.
- **Server-rendered half (Phase 3/8):** must read `viewsheet.modernVisualization` on the render path
  so charts/table density switch with the same toggle.

## Open items

- **Class vs attribute:** `.viz-modern` is primary (mirrors `accessible`); `[data-viz-theme="v2"]` is
  kept as an accepted alias in the SCSS scope for embed/external activation.
- Carries the still-open contract §5 token-home decision (unaffected by Phase 2).
