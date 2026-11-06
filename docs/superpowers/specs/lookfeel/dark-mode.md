# Dark Mode

Current design, decisions and open work for dark mode in the portal and in designed dashboards.
Written 2026-11-06 from a code audit. Enterprise Manager (EM) has its own theme mechanism
(`emDark`, `theme-dark.css`) and is **out of scope** here.

This is the single place dark-mode decisions are recorded. See "Relationship to older docs" and
"Basis and limits" at the end before relying on the older Phase 9B plan or the roadmap.

## The model in three sentences

1. **Dashboards are dark because they were stamped dark.** Each assembly carries a mark
   (`MODERN_LIGHT` or `MODERN_DARK`), and rendering and export read that mark.
2. **The portal shell follows the viewer's preference** (user, then org, then site). It does not
   depend on any dashboard's stamp.
3. **A viewer's preference never recolors dashboard content.** A dashboard is always drawn in the
   mode it was designed for, and has to look right inside either shell.

## Two surfaces

| | Shell (portal, later composer) | Designed dashboards |
|---|---|---|
| What it is | Navigation, panels, dialogs, lists, forms | Charts, tables, KPIs, selection, calendar, titles |
| Selected by | A `shell-dark` body class (decided, not yet built) | The assembly's `VizMark` |
| Controlled at | User, org and site (decided, not yet built) | Author, per dashboard (switch-mark, Modernize, Revert) |
| Org/site setting | `portal.shellDark` (decided, not yet built) | `viewsheet.darkMode` decides only what **new** assemblies are stamped with |

## How it works today (dashboards)

Code to read first:

- `core/.../uql/viewsheet/internal/VizMark.java`: the persisted mark (XML attribute `vizMark`).
- `core/.../uql/viewsheet/internal/VizContext.java`: `VizContext.of(assemblyInfo)` turns a mark into
  `modern`, `dark` and `density`. Dark never applies without modern.
- `core/.../uql/viewsheet/internal/VS*Defaults.java`: the resolvers (table structure, chart chrome,
  title chrome, output chrome, palette, calendar).
- `core/.../web/composer/vs/controller/ModernizeViewsheetService.java`: `switchMark`, Modernize, Revert.

Key facts:

- **The org setting is a creation-time switch.** `viewsheet.darkMode` (org-scoped, falling back to
  the site value) only chooses the stamp for new assemblies. Turning it off or on does not change an
  existing dashboard. The only route back is the per-dashboard Revert.
- **Most dark values are seeded, not computed.** `seedChromeDefaults` writes DEFAULT-tier values
  into the assembly's own formats and chart descriptor: borders, card background, gridlines, title
  ink, padding, density sizes, palette. This is deliberate, so the values travel in an exported
  asset. It runs at creation, on Modernize, Revert and switch-mark, and on every state or bookmark
  restore (`reseedAfterRestore`).
- **Some values are resolved at render time:** the model flags (`VSObjectModel` `vizDark`),
  `GraphGenerator`, table density and structure.
- **Tiers protect author choices.** A USER or CSS-tier color outranks the seeded DEFAULT tier, so
  author-set formats survive a mark switch.
- **Browser styling follows the assembly**, not the org: `viz-modern` and `viz-dark` classes sit on
  each assembly wrapper, and tooltips carry their own `widget__tooltip--dark` class. Tokens are in
  `web/projects/portal/src/scss/_viz-tokens.scss`.
- **Three reads still follow the org gate,** each an accepted cost: tooltip style AUTO resolution,
  inline-SVG animation, and the shell body classes (`viz-shell`, `viz-shell-dark`).

## Exports

Decision: **exports follow the stamp.**

- PDF, PPT and PNG paint a page background, so a dark-marked dashboard exports dark and a
  light-marked one exports light.
- Excel cannot paint a background, so it substitutes the legacy near-black ink
  (`AbstractVSExporter.paintsPageBackground()` and `applyDarkOptOutInPlace`).
- Print layouts and legacy reports skip the dark substitution (`VsToReportConverter`).
- Scheduled and emailed output uses the same exporters (`ViewsheetAction` has no mark or gate reads
  of its own), so it follows the stamp too.
- This keeps the guarantee the resolvers are built on: live view and every export resolve the same
  default. The original Phase 9B recommendation (exports always light) was not adopted.

### Per-export "light" option (decided, not yet built)

Dark print output is uncomfortable, so exports get a choice of **as designed** (default, follows the
stamp) or **light**. First release: PDF and the scheduler and email action. Other formats later.

How it would work:

- `AbstractVSExporter.export()` already clones the viewsheet, swaps the clone into the sandbox and
  swaps the original back in a `finally` block. For a light export, apply the light mark to the
  clone right after cloning. The stored dashboard is untouched.
- Charts built for export (`getVGraphPair(..., export=true)`) are created fresh from the sandbox's
  current viewsheet, so they should pick up the clone's mark.
- Plumbing is the larger cost: the export dialog, the REST export call, and the persisted
  scheduler and email action settings.

Hazards to resolve before building:

1. `VizModernizeUtil.applyMark` selects every assembly whose mark differs from the target, which
   includes unmarked (legacy) ones, so a naive light export would modernize legacy content. Use a
   variant that touches only `MODERN_DARK` assemblies.
2. Five other `getVGraphPair(name)` call sites in the exporter use the non-export path, which reuses
   the sandbox's cached graph pair. Not checked: whether a dark-rendered graph can be returned from
   that cache during a light export.
3. The mark collector skips embedded viewsheets, so an embedded dashboard's dark content would stay
   dark. Decide whether that is acceptable or needs a recursive variant.
4. Author-set (USER or CSS tier) colors outrank the seeded values, so an author-chosen dark
   background stays dark. This is correct, but the option cannot promise an all-white page.
5. `export(..., cloneVs)` takes a flag. Not every caller was traced to confirm it clones.

Open question: the viewer's Print button. The portal styles contain no `@media print` rules, so
browser printing has no dark handling. Whether the Print button uses server export or browser
printing has not been checked.

## Shell dark (decided, not yet built)

**Implementation phases, not release phases:** phase 1 is the portal, phase 2 is the composer. Shell
dark is not released until **both** are complete. Until then only engineers and testers see it, behind
the hidden setting described under Rollout, and they should expect a half-finished product.

Today `viz-shell-dark` on `<body>` switches viz tokens only. There are no dark values for the
neutral shell tokens, so org Dark Mode produces dark dashboards inside a light portal.

Design:

- **New body class `shell-dark`,** independent of Modern Visualization and of `viz-shell-dark`.
- **Own org and site setting** (for example `portal.shellDark`) on the Look and Feel page, separate
  from `viewsheet.darkMode`. An admin lock, "Allow users to choose", defaults to allowed.
- **User control:** an Appearance setting in the portal Preferences dialog (organization default,
  light, dark), stored with `UserEnv` like `historyBarEnable`, and resolved server-side into the
  portal model in `PortalController`. The effective order is user, then org, then site.
- **Dark neutrals:** one block overriding the shell neutrals (surfaces, borders, text, hover,
  toolbar, panels). Base values already exist in `visualization-palette-swatches.html`: canvas
  `#1C1B1F`, surface `#252428`, subtle `#2D2B30`, border `#49454F`.
- **Apply before first paint.** Today body classes are set after the portal-model request returns,
  which would flash light for dark users. The resolved value must arrive with the initial page or
  be cached client side. How the portal HTML is served has not been checked yet.

Known work:

- `_variables.scss` defines 249 `--inet-*` custom properties fed by 38 `$shell-*` Sass colors. A dark
  block can override the properties.
- Sass-compiled values (for example `$navbar-bg`, `$link-color`, `$component-active-color`) become
  static CSS and will not flip. Bootstrap here is 5.2.3, which has no built-in color modes. These
  need converting to CSS properties or override rules.
- 241 hard-coded color literals across 65 portal component SCSS files need auditing. Each is either
  a deliberate color or a defect in dark. Not yet audited.
- Third-party widgets (date pickers, code editors, the jsPlumb canvas), icons and logos have not been
  reviewed.

Known shell symptoms to fix by token, not by per-component patches: table header sort/drill/menu
hover fill, calendar day-of-week surface, composer/viewer canvas background, image placeholder.

### Rollout

Hidden site-level setting, default off, until **both the portal and the composer** are complete.
Developers and QA enable it per org. Nothing is exposed in the Look and Feel page or Preferences
until then, and no release ships with it enabled for users. Reason: a partial conversion can leave
light text on a hard-coded white surface, which is unreadable and worse than a plain light screen.

Build order:

1. **Phase 1, portal:** token block and early apply, then portal chrome, then dialogs and remaining
   screens with the color audit.
2. **Phase 2, composer:** the composer app applies the same class (it sets its own body classes in
   `composer/app.component.ts`), then composer-only surfaces: editor canvas and its background,
   binding, format panels, the VS wizard, and the worksheet editor. Many dialogs and widgets are
   shared with the portal, so phase 1 converts part of the composer too.

While the phases are in progress, the composer may look wrong or light next to a dark portal. That
is expected for testers and is not a release state.

## Deferred

- **Per-viewer re-resolve of dashboard content.** Not viable cheaply: dark values are persisted in
  shared assembly formats, about 30 render-time reads would need a viewer context, and restore,
  export and the scheduler would have to stay on the stamp. If revisited, apply the viewer mode to a
  per-session render clone re-seeded under a viewer flag, never to the shared assembly. A narrower
  alternative, switching only the browser-side `vizDark` styling for a viewer, is untested and leaves
  charts and tables as stamped. Not checked: the chart tile endpoint.
- "Match system" (`prefers-color-scheme`). Nothing follows the OS today.
- Dark sequential and diverging ramps, and the `--vivid-series-dark-*` / `--family-series-dark-*`
  palette families (see the Phase 9B plan).

## Validation

Checks to run in the Phase 10 pass:

- legacy (unmarked) dashboards are unchanged with the gate on or off, in light and dark orgs
- light-stamped modern dashboards are unchanged when an org turns Dark Mode on
- dark-stamped dashboards are correct on tables, crosstabs, charts, title bars, KPI and output
  widgets, selection and input controls, calendar, slider, image placeholder and the palette
- switch-mark, Modernize and Revert round-trip a dashboard without mixed light and dark values;
  author-set (USER or CSS tier) formats are preserved
- PDF, PPT and PNG export follows the stamp; Excel stays readable; scheduled and emailed output
  matches interactive export
- once built, a light export of a dark dashboard is light, leaves legacy and light-stamped content
  unchanged, does not alter the stored dashboard, and matches between interactive and scheduled export
- bookmark and saved-state restore keep the stamp's values
- a dark dashboard is readable in a light shell and in a dark shell; tooltips and the mini-toolbar
  follow the assembly, not the org
- shell dark, checked at the end of each phase and again before release: every converted screen is
  readable, with no light text on hard-coded light surfaces, and no flash of light before first
  paint; at release, no portal or composer screen is left unconverted

Existing coverage: per-resolver unit tests set `viewsheet.darkMode`. The only manual dark script is
Group F of `docs/superpowers/plans/2026-08-24-palette-revert-manual-tests.md` (palette and revert),
with no recorded results found.

## Other gaps found

- `PortalProfileController` colors the profiling chart from the EM dark flag (`isEMDarkTheme()`), so
  that chart can be dark on a light portal page.
- No user-level control exists. `inetsoft.portal.themeId` is read by the enterprise theme resolver,
  but nothing in the repository sets it, and it selects custom CSS themes rather than dark.
- Login, signup and the Preferences dialog have no dark handling.

## Relationship to older docs

The Phase 9B plan and the roadmap are historical records. They were written before implementation
and have deliberately not been edited, except for a banner at the top of the 9B plan pointing here.
Where they disagree with this doc, this doc wins. Known differences:

- **Selection mechanism.** The 9B plan's Task 0 asks how dark is selected and floats
  `prefers-color-scheme`, a body class, or an org/theme flag. In the code, dark is selected per
  assembly by its `VizMark`, and nothing follows `prefers-color-scheme`.
- **Export scope.** The 9B plan recommends live-only dark with exports staying light. The code
  follows the stamp, and a per-export "light" option is decided. See Exports.
- **Shell-owned dark neutrals.** The 9B plan defers these to an unscheduled "shell dark-mode
  initiative". They are now covered in "Shell dark" above.
- **Status.** The 9B plan says "NOT STARTED". Its Tasks 1 to 4 have corresponding code and unit tests.
  Task 5 (validation) has no recorded results, and the roadmap's Phase 10 section lists no dark
  checks. The checks are in Validation above.

## Basis and limits

This doc comes from a targeted code audit on 2026-11-06, not a full reconciliation against the 9B
plan. Verified by reading code: the mark and `VizContext` flow, the org-scoped setting and its
fallback, the seeding mechanism, the export clone-and-swap path, the scheduler's use of the same
exporters, the existing shell and viz tokens, and what the Preferences dialog stores. Not verified:
the chart tile endpoint and cache behavior during export, how every caller of `export(..., cloneVs)`
behaves, how the portal HTML is served (relevant to applying dark before first paint), whether the
viewer's Print button uses server export, third-party widgets, and any other places the
implementation differs from the 9B plan. Treat the statements here as accurate for the areas listed
and as unchecked elsewhere.

## Related

- [visualization-phase9b-dark-mode-implementation-plan.md](visualization-phase9b-dark-mode-implementation-plan.md): original task list (historical)
- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md): Phase 9B and Phase 10
- [visualization-palette-swatches.html](visualization-palette-swatches.html): dark token source of truth
- [theme-strategy-overview.md](theme-strategy-overview.md): shell, composer and visualization layering
- [2026-09-18-per-dashboard-viz-override-design.md](2026-09-18-per-dashboard-viz-override-design.md): per-dashboard mark override
