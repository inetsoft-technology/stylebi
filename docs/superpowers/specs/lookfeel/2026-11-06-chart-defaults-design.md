# Chart defaults under the modern mark — design

**Date:** 2026-11-06
**Status:** design. Decisions approved by the owner on 2026-11-06. Nothing built.
**Base:** `epic-74519` at `5e4772a6cf`. Every file and line citation was read there (static read, not run). Paths are under `core/src/main/java/inetsoft/` unless stated otherwise.
**Depends on:** feature-80398, the Modernize dialog (PR #7817, not merged when this was written). Its reset, residue rules and per-type `clearAuthorChrome` hook are assumed here.
**Source design:** the chart-defaults design bundle (`chart-defaults-design-bundle.zip`).
- Its text is mirrored in [chart-defaults-design/](./chart-defaults-design/). The images, the drawn cards and the repro data are only in the zip.
- The bundle decides how each chart should look.
- This spec decides how each look is built, and records every ruling that overrides the bundle or an earlier decision in the repo.
- The bundle was written without being reconciled against the repo. Section 6 lists where it is wrong about the code.

**Types in scope:** every chart type on an assembly that carries a modern `VizMark`, in 11 families:
- financial: candle, stock, box plot
- bar: bar, horizontal bar, interval
- line: line, step, jump, area, step area
- point: point, scatter contour, contour map
- pie and donut
- radar and filled radar
- hierarchy: treemap, sunburst, circle packing, icicle
- relation: tree, network, circular network
- Gantt
- geo: map
- funnel, waterfall, pareto, marimekko

**Not in scope:**
- unmarked (classic) charts, which must render pixel-identical
- every other assembly type
- the five defects that affect every chart, logged as separate bugs (§8)

## 1. Why

A modern chart with no author formatting still draws much of the classic look:
- the legacy blue `#518DB9` for a single series
- axis titles such as "Sum(Sales)" on every chart, including pie and radar
- a rule under the title
- smooth curves that invent values between points
- pill-shaped bar ends
- a right-hand legend on every chart
- grey labels that cannot be read on treemap cells

The bundle captured 11 chart families on live dashboards and decided 24 shared rules (C1 to C24) plus per-family looks. This spec does four things:
- turns that design into requirements
- traces every item to the code
- corrects the bundle's cost classes, several of which were wrong
- fixes one way of building, so that each item keeps four guarantees:
  - classic charts never change
  - the author's value wins
  - Revert restores the classic look
  - a seeded value travels in the asset

## 2. Goals, non-goals, anti-goals

**Goals**

- A new chart on a modern dashboard, with no author formatting, looks as the bundle draws it.
- Modernize ("Keep my formatting") brings an existing chart as close to that look as the author's values allow.
- Modernize's reset copy brings it all the way there, except for the date level (D5).
- Every default that can be expressed as a stored value is seeded into the asset. It then travels to a build without this work, and the format dialogs show what the chart draws.

**Non-goals**

- The five defects in §8, which are wrong for every chart and are fixed separately.
- The per-panel value range on candle and stock. It is blocked on the product: a panelled chart's value axis cannot be selected today.
- The bundle's "known limits": no top N or scroll default, many series, month names stored as text, and so on.
- Changing how density gaps are resolved. They stay resolved at render, as shipped (SC §5.5), so they do not travel. This is recorded as a limit in §3, M9.

**Anti-goals** (failures even if every item works)

- A classic chart changes in any pixel. This includes changes through shared graph code: the corner floor, the pareto ticks, the legend order and the label rules.
- A re-seed, Modernize or Revert overwrites a value the author set.
- Modernize ("Keep my formatting") or Revert changes a binding, a sort, a chart type or a date level.
- A format dialog shows a different value from the one the chart draws (the design-time WYSIWYG rule, title-lane-unfilled §1).

## 3. Mechanism

These rules apply to every item in §5.

### M1. The scope is the mark

Only an assembly with a modern `VizMark` changes. The org gate does not decide this, and neither does the `.viz-shell` body class (SV D4).

- The graph package (`inetsoft.graph.*`) has no mark context.
- `GraphGenerator` holds a `VizContext` (`GraphGenerator:224`, with `.modern`, `.dark` and `.density`).
- So every graph-package change reads a property that the generator sets from that context. The property goes on `EGraph`, an element, `AxisSpec`, `LegendSpec`, `TitleSpec` or a coordinate.
- With the property unset, the graph code behaves as it does today.
- This holds even for changes that look harmless for every chart, such as the bar-corner floor and the pareto second-axis ticks.

### M2. Four kinds of item; prefer a stored value

Every item in §5 is one of these kinds:

| Kind | Meaning | Travels to an older build? | How the author wins |
|---|---|---|---|
| **V**: value of an existing property | The old renderer already reads this property. The modern default is seeded into it. | Yes, once slice 0 makes it save (M3) | Tiered field: the author's USER tier. Plain field: a user flag (M4) |
| **N**: new stored property | A new field, saved with the asset | Saved, but an older build ignores it and draws as before | Same as V |
| **D**: drawing rule | Code that runs when the chart draws: layout, fit, or anything that depends on the data or the card size | No. An older build doesn't have the code | Applies only while the author has not set the setting it replaces |
| **C**: creation-time default | A binding, sort, chart type or date level chosen when a chart is created or changes type | Yes. It is ordinary saved binding | It is the author's binding afterwards (M6) |

**Principle.** Where a rule can be stated as a value of a property that older builds already read, it is built as kind V, even if a drawing rule would be simpler. Example: pie size is seeded into the existing `pieRatio` field at 0.8, instead of computing 80% at draw time. A rule becomes D only when it depends on:
- the data, such as the diverging ramp
- the card size, such as the label rule
- new layout code, such as legend caps, pixel floors and zero lines

The legend's entry count (S3-1) depends on the data, but it is not a drawing rule: the owner ruled on 2026-11-11 that a legend stays where the chart was designed. The count is taken once, when the composer draws the chart after a seed, and the resulting position is saved (kind V).

**Sizes are drawing rules (owner, 2026-11-12, D44).** Every draw rewrites a chart's default size values (`GraphGenerator.fixSizeFrameValues`) unless the author changed the frame, so a seeded size would be overwritten at the next draw. The modern sizes in S4 are therefore chosen there, under the mark (kind D).
- The value still lands in the stored frame, so the Size dialog shows what draws.
- An older build draws its own sizes.
- This is an exception to D2 and D3 (§4, R11).

### M3. Seeded values are saved (slice 0)

Chart descriptor values do not travel today.
- The `CompositeValue` fields on `PlotDescriptor`, `LegendsDescriptor`, `LegendDescriptor` and `TitleDescriptor` are built with `saveDefault=false` (`uql/CompositeValue.java:261`).
- `toString` writes only the USER tier (`:165-179`).
- The seed does not run when a file is loaded. It runs at creation, on Modernize, on Revert, on a type change, on a density change and on state or bookmark restore (`reseedAfterRestore`).
- So a value seeded into the DEFAULT tier is lost on save and reopen.
- The shipped modern gridline survives only because `CSSChartStyles.apply` writes it into the CSS tier on every render (`VGraphPair:1406`).
- The dark-chrome design accepted this as a limit and named the fix as the next piece of work (DK §4, "Accepted as a limitation" and "the natural successor"). `SeedChromeDefaultsTest.theSeededLineColoursDoNotTravelInTheAssetYet` pins today's behaviour.

Slice 0 makes every chart descriptor `CompositeValue` that a modern seed writes, today or under this design, save its DEFAULT tier, **on marked charts only**:
- Saving is a per-instance switch, turned on by the modern branch of the seed and off by the legacy branch.
- Write the value in `seedChromeDefaults` (both branches) where today only render-time CSS writes carry it.

**Why marked charts only.** On a classic chart, the DEFAULT tier of the x and y grid, diagonal and quadrant colours comes from a per-org `format.css` lookup (`ChartPlotLine`):
- it runs when the object is built (`PlotDescriptor:1898-1911`) and in the legacy seed (`:196-207`)
- nothing re-resolves it when the chart draws

Saving it would freeze the org's current value into every classic chart. A later `format.css` edit, or an import into another org, would no longer reach those charts. That is a classic chart changing. A marked chart's DEFAULT tier is the modern constant, so saving it freezes nothing that should move. Classic XML is also left as it is.

Facts that make this safe:

- **Older builds read it.** `CompositeValue.parse` reads the `^^DEF^^` prefix into the DEFAULT tier, whatever the reading build's `saveDefault` is (`:114-127`). This has been true since this repository's first commit (2024-07-12). The reviewer also confirmed it in the InetSoft 2023 `inetsoft-core.jar`.
- **Older builds don't keep it (known limitation, ruled 2026-11-09).**
  - An older build's `CompositeValue.toString` writes the DEFAULT tier only when `saveDefault` is set, and parsing never sets it. So the tier is gone the first time that build writes the asset.
  - Any state round trip by that build loses them, not only a save:
    - An EM import writes on the way in (`AbstractSheetAsset.parseContent` → `setSheet`). An imported asset therefore arrives without its seeded values. On a marked chart, that build's render-time CSS still colours the x, y and facet lines, but the diagonal and quadrant lines draw classic in the composer.
    - Its viewer writes and re-reads the dashboard state on open. In the S2a checks, `13c54d60b6`'s viewer drew a modern map's no-data regions in the classic `#F2EFE9`, while its composer drew the saved `#D6D2C8` (that build's `PlotDescriptor.savedDefaults()` has no `emptyColor`).
  - Opening the stored file in that build's composer without a write, for example after a downgrade on the same storage, draws the saved values.
  - Only the USER tier survives an older writer. Saving a seed there would make it the author's and break Revert, so this is not changed.
- **Bug #55730.** The `readAsDefault` branch (`:129-136`) turns a bare string into DEFAULT only when the field saves defaults *and* the caller passes `readAsDefault=true`. No chart descriptor parse passes `true`. The only caller that does is `StaticSizeFrameWrapper:99`. So an author's bare value from an old file stays USER.
- **Data-label ink already travels.** `CompositeTextFormat` writes its `<defaultFormat>` without any `saveDefault` gate (`CompositeTextFormat.writeContents`, DK §4).

### M4. The author wins

- **Tiered fields:** the seed writes only the DEFAULT tier.
- **Plain fields:** the seed writes the field only while its user flag is false.
  - The flag is stamped only on a real author change: a dialog edit, a drag, a script assignment or a binding-handler write. It is never stamped on every Apply (SV D13 exemption, GEO §1.3 B).
  - The existing pattern is `userBarCornerRadius` and `userSmoothLines`.
- **New flags this design needs:**
  - the value-axis line (`AxisDescriptor.lineVisible`, per axis)
  - the legend layout (`LegendsDescriptor.option`)
  - the legend title's visibility (`LegendDescriptor.titleVisible`, per legend; S3-6)
  - values shown (`PlotDescriptor.valuesVisible`)
  - the size legend's visibility (`LegendDescriptor` visible, size legend)
  - the tree node corner (`PlotDescriptor.nodeCornerRadius`)
  - `pieRatio`
- **Already have provenance:**
  - static colour frames: the default, CSS and user tiers are saved by `StaticColorFrameWrapper:99-146`
  - size frames: `isSizeChanged`
  - shape frames: `VisualFrameWrapper.changed`, `:156`
- **Files saved before a flag existed.**
  - When the flag attribute is missing, the flag is derived on parse. A value that differs from every classic creation default of that field counts as the author's. This follows the `userTitleHeight` precedent (SV, "What changed" §1).
  - `userBarCornerRadius` was built on the opposite precedent, where a missing attribute means no opinion. It moves to this rule in S4-1 (owner, D47). Released builds have the radius but neither the flag nor a seed, so a non-zero radius in a file they saved is the author's.
  - Without this, an author's old value-axis line "off" would survive Modernize but be lost on Revert. "Keep my formatting" would also move an author's bottom legend to the top and overwrite an author's `pieRatio`. Both break P6 and MD §2.
  - Where a field had more than one creation default, every one of them counts as no opinion.
  - The tree corner is the exception (owner, D48).
    - Every tree created on this branch gets 0.3, modern or classic.
    - A tree saved by a released build has no corner attribute and draws square.
    - That square corner counts as the author's, so "Keep my formatting" and Revert both leave it square (S4-11).
  - A flag attribute that is present is read as it is.
- **Max-mode copies.** `maxModeVisible` (`TitleDescriptor:399`) and `maxModeLineVisible` (`AxisDescriptor:930`) are plain fields with no flag. Under modern they follow `visible` and `lineVisible`. A stored max-mode value that differs from its classic default (true) counts as the author's.
- **Typed axis titles count as the author's.** See S1-2.

### M5. One hook, both branches

- **One hook.** Each value is written in `seedChromeDefaults(VizContext)` of the type, on both branches. Revert clears the mark and re-runs the same hook, so every value Modernize writes is undone by the same edit to the same method (SV D11–D13).
- **The legacy branch** writes what a gate-off creation would write, by re-running the same lookup rather than writing a literal, so `format.css` survives (SV D12).
- **Chart descriptor values are written on every legacy pass, not only on a transition.** This applies to tiered values and to flag-guarded plain fields alike. TC R3's transition-only rule stays for the card border widths it governs.
  - **Why:** after S0, a bookmark taken while modern carries modern DEFAULT tiers and modern plain values (`ChartVSAssembly:470-481`). Restored after a Revert, a transition-only legacy pass would write nothing, and the chart would come back modern: the un-revert SV Decision 10 rules out.
  - **Why:** feature-80398's residue simulation (`classic()` in `VizModernizeUtil`) seeds with a non-transition legacy context. It must produce the classic value.
  - **Why it is safe:**
    - Unmarked charts save no DEFAULT tier (M3), so the legacy lookup reproduces exactly what loading produced.
    - Plain fields are written only while their flag is false, and an old file's own non-default value is flagged on parse (M4).
    - This is how gridlines already behave.
- **Per-type values key on the resolved type.** These are axis titles, x grid, axis lines, legend layout, corners, smoothing and sizes.
  - New charts are `CHART_AUTO` (`AbstractChartInfo:59`). Their drawn type (bar, line or point) comes from the binding (`:1006`, `:1055`).
  - The resolved type is the runtime type. On a multi-style chart it is the first measure's type, as `getTitleSpec` uses.
  - The per-type seeds re-run whenever the resolved type changes. That covers an explicit type change through the transition matrix, and also every binding change: a drop, a convert, a swap and the wizard.
  - A re-run writes only DEFAULT tiers and plain fields whose flag is false, so it never touches an author value.
  - Per-field axis descriptors created at drop time are seeded then.
  - There is one predicate per value. `GraphTypes.isSmoothLinesDefault` is the precedent (SV D13 qualifier, 6C).
- **Copies and type conversions** keep their own mark (SV D3).

### M6. Creation-time defaults (kind C)

Some defaults choose the binding or the data, not the look. These are:
- month as the date level
- stacked area
- the waterfall in data order
- pie slices sorted largest first
- the treemap coloured by its top level
- the marimekko coloured by Break By
- candle refusing a colour dimension

**When they apply:**
- They apply when a chart is created, or changes type, on a modern dashboard.
- Modernize ("Keep my formatting") and Revert never touch them. A chart created modern and then reverted keeps its binding; Revert restores the look, not the binding.
- **The reset copy** (feature-80398, option 2) applies every creation-time default except the date level.
  - Resetting the date level would change the numbers on the copy, for example a "Sales by year" chart becoming monthly.
  - The saved level cannot tell an author's choice from the old default.
  - This amends 80398's anti-goal "data bindings … change" (MD §2) for this list only.

### M7. Modernize dialog (feature-80398)

- **The allowlist grows.** Every new kind V or N property joins the chrome slot list that feature-80398 uses for residue and reset (`ChartChromeSlots.of`, built from `ChromeSlot`). One edit adds a property to the seed, the residue rule and the reset. MD §10 names this hook `clearAuthorChrome(residueOnly)`; the branch shipped it as slot lists.
- **The reset also clears axis-title visibility and value-axis-line visibility** (owner, 2026-11-06). The MD allowlist kept "visibility". Legend visibility and title text are still kept.
- **The reset sets the legend layout to the type's modern default** (C7), not `RIGHT` (amends MD D28 and §11.3).
- **Residue.** An author value is residue only if it equals what the classic render would draw with that value removed, compared strictly (MD §4).
  - This includes a system write that landed in the USER tier.
  - Example: `ChangeChartTypeProcessor.changeToMap` and `changeFromMap` write title visibility as USER (`:588-605`). Under modern they should write DEFAULT (§5, S1-2).
- **P6 invariant.** It is "Modernize then Revert renders identically", as amended by MD §4.

### M8. Charts already marked

There is no migration on load: the epic is unreleased.
- Drawing rules (D) apply to marked charts at once.
- Seeded values (V, N) reach a chart that was marked before this ships at its next re-seed: Revert then Modernize, a dark/light switch, a density change, a type change or a restore. Modernize on its own is not one: it skips charts that are already marked (`VizModernizeUtil.modernize` takes the unmarked ones only). One example is the corner (0.3 → 0.08), whose plain value is already stored. Seeds that were not saved before slice 0 come back as the field's own default until then.

### M9. Exports and older builds

- Every export (PDF, PNG, SVG, Excel, PowerPoint) draws through `GraphGenerator`, so drawing rules apply there too. Excel keeps its opaque card (TC D16).
- **In an older build:**
  - V items arrive and draw. Those saved as a DEFAULT tier (chart descriptor values) last only until that build writes the asset, and an import writes it (M3).
  - User flags saved only where the parse would derive them wrongly (`userLineVisible`, `userTitle`, `userSmoothLines`, `userBarCornerRadius`; M4) are dropped the same way. The next open derives them again, so the seeded value counts as the author's and no later re-seed or Revert replaces it: a seeded-off value line, a seeded straight area, and a title the wizard typed, which then stays visible on a modern chart. This is accepted, like the DEFAULT tier. For `userBarCornerRadius` a released build does not know it, so a marked chart it saves derives the seeded 0.08 as the author's on the next open, and Revert then keeps 0.08. Accepted like the others.
  - N items are ignored.
  - D items fall back to the old rule.
  - The S4 sizes (D44) draw at that build's own values. Its first draw also rewrites the modern values held in the stored frame.
  - C items arrive as ordinary bindings.
- Density gaps and legend gaps stay resolved at render (SC §5.5), so they fall back to the old values there. This is accepted and not changed here.

## 4. Rulings that override recorded decisions

Ruled by the owner on 2026-11-06. Each overrides the decision named. The older docs are not edited; this table is the record.

| # | Ruling | Overrides |
|---|---|---|
| R1 | **C2.** No rule under a **chart's** title. Tables, selections and calendars keep theirs. | OI §1 and the title-lane-unfilled design ("unfilled, with a hairline bottom rule", matched to the table header; colour retuned in TC D13) |
| R2 | **C8.** No legend border and no legend fill on modern charts. A legend placed in-place over the plot keeps both. | Roadmap dependency picture, "§05 legend — CLOSED BY DECISION 2026-08-27, do not re-open"; TC D20 (dark legend fill `#3E3C43` at 50%) |
| R3 | **Straight segments** by default for modern line, step, jump, area and step area. Smooth stays an author choice. | 6C goal (modern lines seeded smooth, `ChartVSAssemblyInfo:163-165`, `ChangeChartTypeService.applySmoothLinesTransition:356-364`); SV D13 qualifier. Classic area and circular network stay smooth by type. |
| R4 | **C22.** Bar corner fraction 0.08 with a 2px floor and a quarter-thickness cap. | 6B global constraint and VDS "Corner Radius Defaults" (0.3) |
| R5 | **C10.** A top or bottom legend's gap is one density step larger: 16, 20 and 24 px on screen (descriptor 14, 18, 22). Side legends keep 12, 16, 20. | GEO D3 and SC §5.5 (one legend gap). The bundle's own "12, 16, 20 on screen" is what ships today; the owner ruled the step. |
| R6 | **C7.** The reset copy puts the legend at the type's modern default. | MD D28 and §11.3 (`RIGHT`) |
| R7 | The reset copy applies creation-time defaults, except the date level (M6). | MD §2 anti-goal "data bindings change" |
| R8 | The reset copy clears author axis-title visibility and value-axis-line visibility. | MD §5.1 (visibility kept) |
| R11 | The S4 mark sizes (Gantt bar, bubble, edge, milestone, radar line) are drawing rules chosen under the mark, not seeded values. Ruled 2026-11-12 (D44). | D2 and D3 for these items. Every draw rewrites the default sizes, so a seed could not survive (M2) |

**Where the bundle is overruled instead:**

| # | Ruling | Why |
|---|---|---|
| R9 | **C16.** Direction colours follow the mark (light or dark tokens), not the page behind the card. | TC D12 stands. If direction colours alone followed the page, a dark-marked chart on a light page would mix light-mode direction colours with dark-mode everything else, and "dark" would mean two things. The composer already offers to switch the mark when the dashboard background crosses the threshold (TC D12, §3.3). The author's Color field covers mid-tone and image pages. |
| R10 | **C21.** Labels off a mark keep the title-tier ink (DK §2.1). White or near-black is chosen per fill only for text drawn on a filled mark. | The bundle's build item 6 asked to "pick one". DK §2.1 is kept as recorded. |

## 5. Items by slice

**How to read the tables:**
- **Kind** is from M2.
- **Where** gives the verified decision point.
- **Bundle IDs:** C*n* is a common rule. A family name plus a number is that family's build item, for example "bar 5".
- **The bundle's own rows (decisions, drawn cards, captures)** are the requirement wherever this spec is silent.

**Slice order:**

| Slice | Name | Plan | Needs |
|---|---|---|---|
| S0 | Chart defaults travel | Written with this spec | — |
| S1 | Chart chrome | Written with this spec | S0. 80398's slot lists for S1-2, S1-4, S1-5 and S1-8 |
| S2 | Colour | Later | S0, 80398 |
| S3 | Legend layout | Later | S0, 80398 |
| S4a | Corners and widths: S4-1 to S4-6, and the Gantt bar size | Later | S0, 80398. Stacked on S3 |
| S4b | The other marks: S4-7 to S4-12 | Later | S4a |
| S5 | Axis labels and titles | Later | S1 |
| S6 | Padding and sizing | Later | S4 for the pie ratio flag pattern |
| S7 | Creation-time defaults | Later | 80398, for the reset path |
| S8 | Family items | Later | S1 to S4 |

### S0. Chart defaults travel

| # | Item | Where | Kind | Notes |
|---|---|---|---|---|
| S0-1 | Save the DEFAULT tier, on marked charts only, of every chart descriptor `CompositeValue` that a modern seed writes, today or in S1 to S8 | `PlotDescriptor` (gridline colours and styles, `emptyColor`, `paretoLineColor`, `alpha`), `LegendsDescriptor.border` and its border colour, `TitleDescriptor.visible` (`:398`), and any other field the builder finds `seedChromeDefaults` or `CSSChartStyles.apply` writing | infra | A per-instance switch set by the seed's modern branch and cleared by its legacy branch (M3). `saveDefault` is a constructor argument today, so it needs a setter or an equivalent. The builder enumerates the exact list from the seeds and the render-time CSS writes. A field no seed writes is unchanged. |
| S0-2 | Write the modern values into the DEFAULT tier in `ChartVSAssemblyInfo.seedChromeDefaults` where today only `CSSChartStyles.apply` carries them | `CSSChartStyles:113` and `:181` (inverted charts swap x and y), `VSChartChromeDefaults:225-238` | infra | Whether the render-time CSS writes stay as well is the builder's call, provided the dialog and the canvas read the same value. |
| S0-3 | Bug #55730 compatibility | `CompositeValue.parse:109-138` and every caller that passes `readAsDefault` | infra | An author's bare value in an old file must stay USER (M3). |
| S0-4 | Flip the pinning tests | `SeedChromeDefaultsTest.theSeededLineColoursDoNotTravelInTheAssetYet` becomes a "travels" test. Add a write-then-read round trip through a fresh descriptor (standing in for an older build). Add a test that a classic chart's XML is unchanged and that its `format.css` gridline still follows the org stylesheet. | test | DK §4 named this as the test to change. |
| S0-5 | Legacy chart-descriptor writes on every legacy pass, and the flag derivation (M4, M5) | `ChartVSAssemblyInfo.seedChromeDefaults` legacy branch; `VizModernizeUtil.reseedAfterRestore`; the parse of each flag | infra | Test: a bookmark taken while modern, restored after Revert, draws classic. Test: an old file's non-default plain value is flagged on parse and survives Modernize and Revert. |
| S0-6 | Re-run the per-type seeds when the resolved type changes (M5) | The binding paths that change the runtime type: drop, convert, swap, the wizard, `ChangeChartTypeService` | infra | One entry point that compares the resolved type before and after, so each slice adds its per-type values to one place. S1-2, S1-3, S1-4 and S1-8 are its first users. |

### S1. Chart chrome

| # | Item | Bundle | Where | Kind | Notes |
|---|---|---|---|---|---|
| S1-1 | No rule under a chart's title | C2, common 0 | `ChartVSAssemblyInfo.seedChromeDefaults:145` writes `VSTitleChromeDefaults.titleRuleBorders()` on the modern branch | V | Change the chart's call only. The helper is shared by table, calendar and selection. Tests that flip: TL §7 assertions, `SeedChromeDefaultsTest`, the `border.bottom 1px solid #d9d5cc` assertion in `VSFormatModel` tests. The title format's DEFAULT tier is already saved. |
| S1-2 | Axis titles off by type | C6, common 1, pie 1, radar 1, Gantt 6, candle 3, stock 3 | `TitleDescriptor.visible` (tiered), read in `GraphGenerator.getTitleSpec` (about `:6306` and `:6328`) | V | See "S1-2 title rules" under this table. |
| S1-3 | Vertical grid for continuous X | C3, common 2, point 1 | `PlotDescriptor.xGridStyle` (tiered, default NONE, `:1902`), read in `GraphGenerator.createAxisSpec:2808` | V | Seed THIN when the resolved type is point or scatter contour *and* a measure is on X; otherwise NONE. It is re-run on binding changes (M5), so a point chart with a dimension on X gets no vertical grid. It is seeded rather than suppressed at draw time, so the dialog shows what draws. Marimekko and radar ignore the plot grid styles (radar's web is S8). |
| S1-4 | Value axis line off | C4, common 7 | `AxisDescriptor.lineVisible` (plain, `:926`) and `maxModeLineVisible` (`:930`); read at `GraphGenerator:2668` | V + flag | See "S1-4 value axis line" under this table. |
| S1-5 | Legend border none and legend fill none | C8, common 4, geo 3 | Border: `LegendsDescriptor.border` (tiered, `:643`), spec at `GraphUtil:582`. Fill: `LegendsDescriptor:104` and `LegendDescriptor:78` `initDefaultFormat(ctx)`, run from `VGraphPair:1075-1169` | V | Seed border NONE. The modern `initDefaultFormat` writes no fill unless the legend is in-place. Revert restores the thin line and the 50% fill (white light, `#3E3C43` dark, TC D20). The gradient legend shares the same path (`Legend.paint:906-942`). Check that the client legend wrapper draws nothing when the model has no border or fill (`chart-legend-container.component.ts:87`). |
| S1-6 | Automatic white or near-black label colour works under modern | C21, common 6, hierarchy 4, relation 5 | `GraphGenerator:1160` (treemap) and `:1546` (relation) compare the label colour with `GDefaults.DEFAULT_TEXT_COLOR` `#4B4B4B`. The modern per-field ink `#6A685F` (`ChartRefImpl:60`) fails the test, so the automatic colour is off on modern charts. | F (modern only) | Test "the author has not set a colour", as `:1235` does with the user tier, instead of comparing values. A colour the author set still disables the automatic colour (MD §4). Funnel and waterfall are S8, because they never had the automatic colour (`:4484`). |
| S1-7 | Pareto second axis without tick marks | common 12, funnel 9 | `GraphDefault:78-97` builds the right axis with a new `AxisSpec` (ticks on by default, `AxisSpec:671`) and copies only colours and grid | D | Copy the tick (and line and label) visibility from the descriptor. Gate it on modern: classic pareto charts keep their ticks. |
| S1-8 | Straight segments by default (R3) | line 1 | Modern seed `smoothLines = true` at `ChartVSAssemblyInfo:163-165`, guarded by `userSmoothLines`. The modern branch of `ChangeChartTypeService.applySmoothLinesTransition:356-364` | V | Seed `false` under modern for line, step, jump, area and step area. Classic keeps smoothing by type (`GraphTypes.isSmoothLinesDefault`). On a type change, keep an author's smoothing when `userSmoothLines` is set: today the transition overwrites it, and the bundle keeps smooth as an author choice. `userSmoothLines` is already on the 80398 allowlist. |

**S1-2 title rules.**
- Seed `false` by chart type in `seedChromeDefaults` and on type change.
- **Titles stay on:**
  - point and scatter contour, both axes
  - pareto and marimekko, both axes
  - box plot, Y only
- **Titles are off on every other type,** including:
  - candle and stock
  - the measure title on pie, donut, radar and funnel
  - the Gantt "Task" title
- An author toggle through `VSChartTitlesVisibilityService` writes USER and wins.
- **A typed title still shows** (bundle financial page: "A title the author sets still shows").
  - The seed writes `false` only while the axis title has no custom text.
  - Typing a title, through the dialog, inline edit or script, stamps USER visibility `true`. This matters because `getTitleSpec` draws custom text only when the title is visible (about `GraphGenerator:6290-6303`).
  - The wizard's own title text ("Frequency", `VSWizardBindingHandler:916-917`) is a system write. It does not count as custom text.
  - On the reset copy, title text is kept (MD §5.1), so an axis title with custom text stays visible there too. Text is content, not look.
- `ChangeChartTypeProcessor.changeToMap` and `changeFromMap` (`:588-605`) write USER today. Under modern they write DEFAULT.
- `maxModeVisible` (`:399`) is plain. It follows `visible` unless the author set it (builder to confirm).
- The reset clears USER visibility (R8).

**S1-4 value axis line.**
- The line stays on for the category or time baseline and off on the value axis.
- Charts with two continuous axes keep both lines.
- A new per-axis user flag is stamped by `AxisPropertyDialogModel`, `VSChartAxesVisibilityService`, the script `lineVisible` and the binding paths that write it.
- Check that the facet outer-border logic still works, because it depends on this field (`GraphGenerator:4245-4361`).
- The radar spoke path (`RadarGraphGenerator:205`) is unaffected.
- Add the field to the reset allowlist (R8).
- The chart-level `AxisDescriptor` serves the Y measures of both bar and point. Per-field axis descriptors are created at drop time with `lineVisible=true` and are seeded then (M5).
- An old file's line "off" is flagged as the author's on parse (M4), so Modernize and Revert both keep it.

C1 (card), C13 (text sizes and colours) and C15 (density) are already shipped. So are common item 3's grid colour `#D6D2C8` / `#4A484F` and axis-line colour `#85827A` / `#7E7B86` (#7750, `VSChartChromeDefaults:225-238`). They need no work, apart from the C13 check in §7.

### S2. Colour

| # | Item | Bundle | Where | Kind | Notes |
|---|---|---|---|---|---|
| S2-1 | A chart with no colour field takes modern slot 1, and further measures follow the modern palette order | C11, common 13, bar, line, point, box plot 1, Gantt 1, funnel 1, relation 2, hierarchy 1 (single colour only) | Colour picked by `GraphUtil.fixStaticColorFrame` and `fixDuplicateColor:1049-1125` from the legacy `CategoricalColorFrame.COLOR_PALETTE` | V | See "S2-1 palette readers" under this table. |
| S2-2 | Pareto line in slot 2, 1.5px; the right axis in the line colour | funnel 1 | `GraphGenerator.getParetoColorFrame:3363-3392` copies the bar colour on purpose; `PlotDescriptor.paretoLineColor` is tiered (`:1948`) | V | Seed slot 2 under modern and use it when no author colour is set. |
| S2-3 | Relation edges in a chrome neutral (the axis-line colour) | relation 2 | The edge element's static frame | V | Edge colour by a field still works as shipped. |
| S2-4 | Map: highlighted region in slot 1 at about 30%; unmatched regions in the grid colour | geo 1 | Highlight: `GraphUtil.MAP_DEFAULT_COLOR` `#FBD29A` (`:86`, `map.default.color` property), written by `ChangeChartProcessor.fixMapColorFrame:906-929` and `MapGenerator:494-503`. Unmatched: `PlotDescriptor.emptyColor` (tiered, `:1934`) | V | Alpha is lost on save (`StaticColorFrameWrapper:126`, `new Color(int)`), so seed a colour pre-blended over the modern card composite (`#ECEBE9` light, `#313034` dark). Make the palette check at `ChangeChartProcessor:917` aware of the mark. |
| S2-5 | Scatter contour gets the teal ramp on Modernize | point 6 | New modern charts are already teal (`ChangeChartTypeProcessor.fixColorFrame:191-195`, `ContourScatterChartFilter:48`). `seedColorPalette:227` walks the aesthetic refs only, never `info.getColorFrame()` | V | Add the chart-level frame to the seed. |
| S2-6 | Direction colours: candle up and down, stock with no colour dimension, waterfall increase and decrease | C12, candle 1, stock 1 and 2, funnel 4 | Candle: `CandleGraphGenerator:217-224` (static `Color.gray`), `CandlePainter.paint:116-150` draws one colour (there is no darkening). Stock: `StockPainter` draws one colour. Waterfall: see notes. | N | See "S2-6 direction tokens" under this table. |
| S2-7 | Waterfall Total in the neutral token | C12, funnel 4 | `VSChartAggregateRef:59-60` summary colour `#7030A0`, applied at `GraphGenerator:4509-4515` (tiered, saved) | V | Skipped when the author set a colour. |
| S2-8 | Size-legend symbols in the chrome label grey, bordered in the grid colour | C20, point 9, relation 6, geo 5 | `Legend.createItems:702-707` falls back to `LegendItem.SYMBOL_COLOR` `#759595` and `SYMBOL_BORDER` (`:306-307`) when `getElementColor` (`:725`) returns null | D | Add a symbol-colour property to `LegendSpec`, set in `GraphGenerator.processSizeLegend` (about `:1830`) from the modern chrome. |
| S2-9 | A measure on colour that crosses zero uses the diverging ramp, centred on zero | C23, point 4 | Frame class chosen at bind (`GraphUtil:972` `defaultLinearFrame(ctx)`). `ChangeChartProcessor.fixColorField:234` always picks Blues, even on modern charts. `LinearColorFrame:52-70` runs min to max with no centre | D + flag | See "S2-9 diverging ramp" under this table. |
| S2-10 | Map ramp starts at about 25% of its range | C23, geo 2 | `TealColorFrame` has fixed spline stops and no offset | N | The builder chooses between a stored low-stop property and a modern-only frame. Mark detection keys on the frame class (`ChartVSAssemblyInfo:264-268`). |
| S2-11 | Each deeper level of a sunburst or icicle is a lighter step of its group colour, up to three levels | hierarchy 3 | `TreemapVO.paint:212-242` fills every level with `gobj.getColor(0)` | D | A level-step flag on `TreemapElement`, set by the generator. Use the palette's shade function, not `getCompanionColor` (COMP §8 says they differ). Labels must keep 4.5:1 on the lightest step. A per-level author colour wins (the builder defines how one is set). |
| S2-12 | Funnel colours in funnel order, top stage = slot 1 | funnel 3 | The colours follow the colour field's own comparator (alphabetical). `ChartVSAQuery.applyFunnelSort:2080-2102` sorts the stage by value and leaves colour out on purpose (comment 55854) | D | It recolours saved modern colour-by-stage funnels, which is accepted (a look change; Revert undoes it). The funnel legend lists the stages in the same order, top stage first: moved here from S3-4 by the owner's S2b ruling Q9 (2026-11-11). |
| S2-13 | Contrast of the direction tokens on the real translucent card | C16, financial open item | The card shipped in #7750, so this is no longer blocked | check | Sample the composited card in light and dark at 15%. Record the figures in the plan for S2-6. |

**S2-1 palette readers.**
- Static frames already save default, CSS and user colours separately (`StaticColorFrameWrapper:99-146`). So this is a DEFAULT-tier seed.
- Add a static-frame branch to `ChartVSAssemblyInfo.seedColorFrame` (`:251`). The pick at bind time uses the context.
- Readers that must become aware of the mark, or the legacy pick overwrites the seed:
  - `VSFrameVisitor:198`, which re-picks at render
  - the `OLD_COLOR_PALETTE_DEFAULT_COLOR` early return
  - `ChartInfoModelBuilder:290`
  - `GraphGenerator:5467`
- Do not change `StaticColorFrame.DEFAULT_COLOR`.
- Candle (`Color.gray`) and the map have their own defaults (S2-6, S2-4).
- The relation node frame is `nodeColorFrameWrapper` (`initRelationElement:1486`).
- The Gantt milestone takes slot 2 by measure order, which settles the bundle's "palette colour 2 or the ink".
- `userColor` is already on the 80398 allowlist (MD §5.1).

**S2-6 direction tokens.**
- Tokens live in `VSChartPaletteDefaults`:
  - light: up `#1C857B`, down `#AD6119`, neutral `#767676`
  - dark: up `#3FBFB2`, down `#E8954A`, neutral `#A8A5AD`
- The token set follows the mark (R9).
- **New stored up and down colours** (tiered, saved), with these rules:
  - The Color field on a candle edits the two colours. It takes no dimension; that is S7-7.
  - Candle keeps hollow for up and filled for down.
  - Stock uses direction colours only when no colour dimension is bound. With one bound, it uses the series palette, and dropping the dimension returns to direction colours.
- **Up and down test:** close against open.
- **Waterfall:** reuse the sparkline's negative-colour pattern (`GraphGenerator:4696-4700`) if it has a stored property (then kind V). Otherwise a new property.
- **In an older build:** these charts draw as before.

**S2-9 diverging ramp.**
- Make `fixColorField` use the context.
- At draw time:
  - when the bound frame is the seeded default teal, and
  - the data range crosses zero,
  - draw with Variance, using a scale symmetric about zero.
- A seeded Teal and an author's Teal are the same class (comment at `ChartVSAssemblyInfo:271`). So a new flag records an author's pick.

### S3. Legend layout

Amended 2026-11-11, after the owner reviewed S3 against the slice 2 head (`e600339878`, PR #8038). The owner's rulings are D28 to D31 and D43; the design calls made with them are D32 to D42 (§10).

**The rule that shapes this slice (D28).** A legend stays where the chart was designed. Nothing in the viewer moves it, and neither do later data changes, filters or selections. So the saved `LegendsDescriptor.option` is always the position that draws. That has three consequences:
- The Legend Format dialog, the browser's `legendOption` (`GraphBuilder.getLegendOption`, which drives the legend resize handle in `vs-chart.service.ts`) and the drawn chart always agree.
- The gap (S3-5) and the title (S3-6) can follow the saved position. They become a resolver input and a seed, not drawing rules.
- The "more than 5 entries" rule is a count taken once and saved, not a fallback at draw time.

| # | Item | Bundle | Where | Kind | Notes |
|---|---|---|---|---|---|
| S3-1 | Top legend for cartesian charts; right for part-to-whole, ramps, relation charts, polar charts, maps and more than 5 entries | C7, common 8 | `LegendsDescriptor.option` (plain, `RIGHT`, `:689`), through `GraphUtil.setLegendOptions:460` to `EGraph.setLegendLayout` | V + flag | See "S3-1 legend placement" under this table. |
| S3-2 | Legend size caps: 30% of the width for a side legend, 25% of the height for a top or bottom one | C9, common 10 | `VGraph.layout0:677-705` (0.5 caps, then `Math.max` with the minimum size), the layout size at `:626-641`; `LegendGroup.refixRatios` | D | A new `EGraph` property set from the context. See "S3-2 caps" under this table. |
| S3-3 | Legend alignment: start, centre or end. The modern default for top and bottom legends is start, at the plot's left edge | C10, common 9 | `LegendGroup.layoutTB:165-205` centres a single legend and fills the width with several. `VGraph.layout0` lays out legends before `coord.fit` | N | See "S3-3 alignment" under this table. |
| S3-4 | Stacked legends list entries in mark order | C24, bar 6 | `Legend.isReversed` (vertical or single-column layouts only) and the reversed layout in `Legend.layoutItems` (`:546-575`, Bug #10019). Comparator from `VSFrameVisitor.setLegendComparator:264` | D | See "S3-4 legend order" under this table. The funnel half of C24 moved to S2-12 (S2b ruling Q9). |
| S3-5 | Top and bottom legend gap one step larger (R5) | C10, common 5 | `VSChartChromeDefaults.resolveLegendGap:176` and its callers: `GraphGenerator:1760`, `:1841`, `:1878`; `LegendFormatDialogModel:86`, `:177`; `VSChartResizeLegendService:78` | D (resolver) | Add the layout as a parameter: the saved position, which is the drawn one (D28). Top and bottom give descriptor 14, 18, 22 (16, 20, 24 on screen); left, right and in-place keep 10, 14, 18. In the dialog, Apply compares against the gap resolved for the position being applied. |
| S3-6 | A top or bottom legend has no title; a side legend keeps its title | Text specs, Gantt 5 | `LegendDescriptor.titleVisible` (plain, default true, no flag, `:957`), read at `GraphGenerator:6756` | V + flag | See "S3-6 legend title" under this table. |

**S3-1 legend placement.**
- **The type default** is seeded on marked charts, keyed on the resolved type (M5):
  - `TOP`: bar, horizontal bar, interval, line, step, jump, area, step area, point, box plot, candle, stock, Gantt, waterfall and pareto.
  - `RIGHT`: pie, donut, the treemap family, funnel, marimekko, radar, filled radar, tree, network, circular network, map and contour map. Scatter contour is `RIGHT` too, because its legend is always the density ramp.
  - Stacked variants follow their base type. A multi-style chart follows its resolved type (the first measure's type).
- **A colour ramp** (a measure on Color) seeds `RIGHT`. This is known from the binding, so it is part of the seed, not the count.
- **The count.** When the composer draws a marked chart whose placement seed is waiting for a count, it counts the entries in the legends it draws.
  - It counts the total across every visible legend (colour, shape and size), after hidden values and `graph.legend.maxcount`.
  - More than 5 entries: the position is saved as `RIGHT`. Otherwise the type default stays.
  - Only the composer writes the count. The viewer, exports, the scheduler and any other draw read the saved position and never write it.
- **The count's verdict is saved** (more than 5 entries or not). This is a new saved value; the builder chooses where it lives (D41).
  - The modern placement seed depends only on three things: the resolved type, the Color binding and the saved verdict.
  - So every re-seed reproduces the designed position without counting, including in the viewer. That covers bookmark and state restores (`VizModernizeUtil.reseedAfterRestore`), density changes, `SyncChartHandler` and the resolved-type re-seed in `ChartVSAssemblyInfo.update`.
  - A restore re-seeds from the designed chart's verdict. A bookmark state saved without a verdict does not clear it.
  - A binding change made in the viewer's chart editor re-seeds from the saved verdict and never counts.
- **When the count is taken again:**
  - creation
  - a type change
  - any binding change in the composer: every shelf, including Color, Shape and Size, and the measures that make a "Measures" legend
  - Modernize, the reset copy, and "show all legends" from no legend (below)
  - The per-type re-seed today keys only on `ResolvedChartType` (type, measure placement, X and Y fields). The placement seed must also re-run when the Color, Shape or Size binding changes.
  - These are the moments when the composer draw that follows retakes the count. Every other re-seed reuses the saved verdict (above).
  - Data changes, filters, selections and parameters never trigger it.
  - If the chart is saved before the composer has drawn it, the seeded default stands until the next re-seed.
- **The user flag** (`userLegendLayout`, M4):
  - **Stamped by** `VSChartMoveLegendService` (a drag to an edge), `LegendFormatDialogModel` (only when the position changes; `:167` writes on every Apply today) and the two script properties, `legendPosition` (`ChartProcessor:272`) and `legend.position` (`:351`). The script properties go through a separate author setter, as `TitleDescriptor.setAuthorTitle` does, because both bind straight to `setLayout`.
  - **A resized legend stays where it is** (owner, D43). A legend size the author set (`LegendsDescriptor.preferredSize`, written by `VSChartResizeLegendService` and kept current by `VSChartDataHandler.updatePreferredSize`) counts as an opinion on the position:
    - A resize stamps the flag.
    - On parse, an old file with a stored size derives the flag as `true`. The builder confirms that nothing but an author resize sets the size; its creation default is null.
    - So neither the seed nor the count moves a sized legend, and Modernize "Keep" leaves it where it was.
    - The reset copy clears both the size (80398's `chart:legends:size` slot) and the flag, then seeds the type default.
    - Why: the size is stored as width and height proportions, and a side legend's height proportion is about 1.0. Moved to the top, that legend would take half the chart.
  - **Not stamped by `SyncChartHandler`** (`:243`). It copies the layout together with its flag, and a marked target re-seeds an unflagged layout. (This corrects the earlier list, which made the wizard a stamping path. S1's rule for a marked wizard target copies only author values.)
  - **"Show all legends" from no legend** (`VSChartLegendsVisibilityService:214-215`) restores `RIGHT` today. On a marked chart it clears the flag and seeds the type default instead, so the count runs again. Classic keeps `RIGHT`.
  - **Old files** derive the flag on parse:
    - `RIGHT` with no stored size is no opinion, because it is the classic default.
    - Any other stored value (`TOP`, `BOTTOM`, `LEFT`, `IN_PLACE`, `NO_LEGEND`) is the author's, and so is any value with a stored size (D43).
    - Write it with `DerivedUserFlag`, so classic XML is unchanged and a seeded `TOP` writes its `false` explicitly.
- **Legacy branch:** writes `RIGHT` on every legacy pass while the flag is false (D24).
- **Precedence:**
  - A flagged layout always wins.
  - A single series still has no legend.
  - Modernize "Keep" keeps a flagged layout.
  - The reset clears the flag and seeds the type default (R6).
- **Frame:** a top legend is not in-place, so it stays unframed (S1-5's `legendFramed`).
- **In an older build:** the saved position draws there (V). An older writer drops the flag (M9), so the next open derives a seeded `TOP` as the author's. This is accepted under M9.

**S3-2 caps.**
- **Which legends:** the caps apply on marked charts to a legend with no size from the author (`LegendsDescriptor.preferredSize` unset):
  - a side legend: at most 30% of the chart width
  - a top or bottom legend: at most 25% of the chart height
- **Classic:** today's 50% cap stays, and so does `Math.max(…, getMinHeight/Width)`, which lets a legend's minimum size beat that cap.
- **Marked:** the new cap wins over the minimum size.
- **A size the author set** (a legend drag, `VSChartResizeLegendService`) keeps today's behaviour, including the 50% cap.
- **Entries that do not fit** behave as they do today at the 50% cap. Nothing new is built for overflow. `GraphUtil.setLegendSpec` marks every legend partial, and the browser's legend area scrolls on hover (`chart-legend-area.component.scss`). The plan checks both on a live chart.
- **No plot floor.**

**S3-3 alignment.**
- **The new setting:** start, centre or end.
  - It is stored as a `LegendsDescriptor` field and XML attribute, carried in a `LegendSpec` field, and exposed in the dialog (`LegendFormatDialogModel`, `legend-format-dialog.component.ts`), in `property-type.properties` and as a script property.
  - It is offered on every chart, because it is an author setting.
- **Unset draws today's layout** on any chart: a single legend centred across the chart width, several legends filling it. Classic charts are pixel-identical because they never have it set.
- **Start, centre and end are measured against the plot's horizontal extent:**
  - Start puts the row at the plot's left edge, after the Y axis title and labels. End puts it at the plot's right edge. Centre centres it on the plot.
  - With several legends, each takes its content width and they sit side by side from that edge.
- **Modern default:** start, seeded for top and bottom legends (tiered DEFAULT tier saved on marked charts as in S0, or plain and flagged; builder). The legacy pass writes unset.
- **Layout:** the plot's left edge is known only after `coord.fit`, so the legend row moves in a second pass. The move only shifts the row along x, so it must not change the legend's height or re-run the fit.
- **Side and in-place legends** ignore the setting.
- **In an older build:** the attribute is ignored, and the legend draws today's layout (N).
- **It joins 80398's slot list** (M7). The reset seeds the modern default.
- **Land it with S3-1.**

**S3-4 legend order.**
- **Run the §7 check first.** The bundle saw a stacked bar's side legend run opposite to its stack, but the code already reverses vertical stacked legends (Bug #10019). The fix is defined by the order wanted below, not by today's code.
- **The rule, on marked charts only,** through a property the generator sets on the legend (M1). It applies to stacked elements (stacked bar, and stacked area following bar):
  - **The legend and the stack run along the same axis** (a side legend on a vertical stack, or a top or bottom legend on a horizontal stack): each entry sits at the same end as its segment. On a vertical stack, the top entry is the top segment. On a horizontal stack, the row reads left to right in the same order as the segments (owner, D31).
  - **They run across each other** (a top or bottom legend on a vertical stack, or a side legend on a horizontal stack): the outer segment comes first. On a vertical stack, the leftmost entry is the top segment. On a horizontal stack, the top entry is the outermost segment (design call, D36).
- **Clustered bars** keep their natural order.
- **Classic charts** keep the shipped order, including Bug #10019's reversal.
- **The funnel legend** is S2-12.

**S3-6 legend title.**
- **The flag:** `LegendDescriptor.titleVisible` gets a user flag (`userTitleVisible`, M4) on every legend descriptor the chart can show. The builder enumerates them: colour, shape, size, and the per-field descriptors that `GraphUtil.getLegendDescriptor` returns.
- **Seed:**
  - Modern: `false` while the saved position is `TOP` or `BOTTOM`; `true` for left, right and in-place.
  - Legacy: `true` on every legacy pass while the flag is false.
- **The title follows the position** while its flag is false. It is re-seeded whenever the position changes: by the seed, by the count, and by an author's move (a drag, the dialog or script). So dragging a top legend to the right brings its title back.
- **Stamped** by `LegendFormatDialogModel` only when the title visibility changes (`:160` writes on every Apply today), and by the `colorLegend.titleVisible`, `shapeLegend.titleVisible` and `sizeLegend.titleVisible` script properties (`ChartProcessor:360`, `:376`, `:392`).
  - `SyncChartHandler` (`:268-269`) copies the text and the value together with the flag.
- **A typed legend title counts as the author's** (D42, following S1-2 and D26). Setting custom title text stamps the flag with the title visible: through the dialog's title field (`LegendFormatDialogModel:150-158`) or the `colorLegend.title`, `shapeLegend.title` and `sizeLegend.title` script properties. So a top legend with a typed title still shows it.
- **Old files:**
  - A stored `false` is the author's.
  - A stored `true` with custom title text is the author's.
  - `true` with no text is no opinion.
- **Gantt:** its top legend loses the "Measures" title. End stays out of the Gantt legend by design (`VSFrameVisitor.getAggregates:278-280`).
- **It joins 80398's slot list** (M7). "Keep" keeps a flagged title. The reset clears the flag (design call, D37, following R8). The exception is a legend with custom title text: title text is kept on the reset copy (MD §5.1), so that title stays visible, as for axis titles in S1-2.

### S4. Mark sizes and corners

Amended 2026-11-12, after the owner reviewed S4 against the S3 head (`958852a11a`, PR #8081). The owner's rulings are D44 to D50; the design calls made with them are D51 to D62 (§10). Lines in this section are at `958852a11a`.

**Two facts that shape this slice.**
- **Every draw rewrites the default sizes (D44).**
  - `GraphGenerator.getFixedInfo` (`:6715`) returns the stored info itself.
  - So `fixSizeFrameValues` (`:5311-5421`) writes the stored size frames on every draw, unless the author changed the frame (`isSizeChanged`, `:5235`). It writes a static frame's DEFAULT tier (`:5402-5411`) and a linear frame's smallest and largest (`:5419-5420`).
  - `ChangeChartProcessor.fixSizeFrameValues` (`:407-508`) does the same on a type change. Its switch has no Gantt case, and it puts `CHART_MAP` in the point case.
  - So every modern size in this slice is a branch in those two switches, chosen under the mark: kind D for travel (M2, M9).
  - It needs no seed, no reset slot and no residue rule: the first draw after Modernize, Revert or the reset copy writes the right value.
  - The author wins through `isSizeChanged`. An edit to any value in the Size dialog makes the whole frame the author's, so an untouched modern largest stays after Revert (D44).
- **The corner is computed in three places**, and all three take the modern rule (D52):
  - the bar (`BarVO.buildRoundedBarShape:1705-1721`)
  - the region arc zones (`BarVO.computeArcZones:1793`, used by `GraphBuilder:1394`)
  - the browser, which redraws hover and selection outlines from the fraction that `ChartRegion` sends (`web/projects/portal/src/app/graph/model/chart-tool.ts` `drawRoundedBar:1725-1747`, `GraphBuilder:1364-1419`)

**Split (D51).** S4a covers S4-1 to S4-6 and the Gantt bar size, stacked on S3. S4b covers S4-7 to S4-12, stacked on S4a.

| # | Item | Bundle | Where | Kind | Notes |
|---|---|---|---|---|---|
| S4-1 | Bar corner fraction 0.08 | C22, bar 5, funnel 8 | `ChartVSAssemblyInfo.seedChromeDefaults:162-167` writes 0.3 for every chart type (legacy 0 at `:194-196`). Read at `GraphGenerator:4036-4037` (bar), `:3840-3841` (pareto), `:3885-3889` (waterfall), `:3987-3989` (Gantt) and `:4020-4023` (interval), into `IntervalElement.cornerRadius` (`:717-747`) | V | `userBarCornerRadius` protects an author's radius, and is now derived on parse (D47, "S4-1 corner flag"). A marked chart keeps 0.3 until its next re-seed (M8). The box plot reads the same value (S4-3). |
| S4-2 | Corner floor and cap | C22, bar 5b | The three places above. Bounds are in screen space when rounded (transformed at `BarVO:217`) | D | See "S4-2 corner rule". |
| S4-3 | Box plot corner from the bar setting, on all four corners; box width 30% of the slot, between 6 and 28px; a box at least 2px tall | financial box 2 and 2b | `BoxPainter:174-182` passes s/3 as the `RoundRectangle2D` arc width, which is a diameter, so today's radius is s/6. Width `SchemaPainter.getSize:113-134` = maxw/3 + (2/3)·maxw·size/30 at size 5 (`GraphGenerator:5373-5374`), about 44% of the slot. `SchemaElement` has no corner property | D | See "S4-3 box plot". |
| S4-4 | Bar width: 50% of its slot, clamped to 3–32px on screen; in a slot too narrow for 3px a bar, the floor is the bar's share of the slot (D63) | C17, bar 1, funnel 8, Gantt 3b | `BarVO.getBarSize:1227-1248`: size 15 of 30, 51.7% (`GraphGenerator:5319-5329`, pareto `:5344-5350`). Slot `RectCoord.getMaxWidth:1049`. Clusters are placed by `BarVO.dodge:780-892` at `VGraph:156`, before `coord.transform` (`:162`) | D | See "S4-4 bar width". |
| S4-5 | Interval bars follow the width rule when Size is bound | bar 7 | The Size measure becomes the interval length (`fixIntervalData:5897-5905`). `ChangeChartProcessor.fixSizeFrameValues` sets min = max = 15 (`:425-427`, `:505-507`), then `GraphGenerator.fixSizeFrame:5230-5233` sets smallest = largest − 1 with the frame maximum at 30: 72–73% of the slot | D | Under the mark the width comes from the S4-4 rule, not from the size frame. The Size measure still sets the bar length. |
| S4-6 | Waterfall bar at least 2px tall; marimekko segment at least a 1px line | C17 | Waterfall: `BarVO`, where `zeroHeight` (`:68-70`) is in logical units and set only by interval. Marimekko: `MekkoVO.paint:80-140` (not `BarVO`); its segments tile the column with 1px white separators | D | The waterfall floor applies after stack rounding, which returns early on a 0 interval (`applyStackRounding:1578-1625`) and derives px per unit from the segment (`computeFullBarBounds:1660`). A step of exactly zero still draws no bar. A shorter step grows from its start in the direction of its change, so it still meets its incoming bridge line (D59). A marimekko segment under about 2px draws as a 1px line in its colour over the separator, and its neighbours keep their size (D50). |
| S4-7 | Candle body at least 3px with a 1px wick; stock ticks at least 3px each side | C17, candle 4, stock 4 | Body `5 + (maxw − 5)·size/max` in logical units (`SchemaPainter.getSize:113-133`, `MIN_WIDTH` `:191`); wick `CandlePainter:123`; `StockPainter` `LENGTH` 10 (`:159`), stroke `:93` | D | The floors apply in screen space. |
| S4-8 | Largest bubble 20px across, on point charts and on map points | C17, point 5, geo 4 | radius = size + `GShape.getMinSize()`/2 = size + 2 (`PointVO:88`, `GShape:400`), drawn 2r across, so largest 15 draws 34px. Point case `GraphGenerator:5362-5371`; maps take the default branch (`:5385-5388`), also 1..15 | D (D44) | Largest 8 under the mark (radius 10), in both `fixSizeFrameValues`. Skipped when `isSizeChanged`. Only where Size draws a point shape: a word cloud's Size sets its font size (`PointVO:100-104`) and is outside the 11 families, and the contour charts' static 15 is not a bubble. Both keep their sizes (D58). |
| S4-9 | Relation edge at most 6px | relation 3 | stroke = size in px (`RelationEdgeVO:93-96`); edges take the default branch, 1..15 | D (D44) | Largest 6. The §7 check is answered: the bundle's capture shows edges of about 15px, which largest 15 explains; "about 35px" was an overestimate. Node size is not capped (D55). |
| S4-10 | Gantt milestone diamond 10px | Gantt 5 | Size 9 (`GraphGenerator:5377`) gives radius 11, a 22px diamond tip to tip. Element at `:3992-4004` | D (D44) | Size 3 under the mark (radius 5). |
| S4-11 | Tree node corner 0.08 | relation 5b | `PlotDescriptor.nodeCornerRadius`: plain, 0.3 for every new tree (`:2019-2020`), written by the pane on every Apply (`ChartPlotOptionsPaneModel:250`) and by script (`ChartProcessor:254-255`). Applied only to `CHART_TREE` (`GraphGenerator:1417-1418`); radius = r × short side (`RelationVO.resolveNodeShape:380-397`) | V + flag | See "S4-11 tree corner". Trees do not take the bar seed. |
| S4-12 | Radar series line 2px; filled radar fill at 30% with a solid outline | radar 5 | Width: default branch, size 1 = 1px (`LineVO.getLineSize:1014-1016`, px = 1 + (size − 1) × 0.5). Alpha: `PlotDescriptor.alpha` (tiered; −1 = auto, which keeps `AreaElement`'s 0.8 from `initBases:75`), `applyAlpha:1666-1677`, not in `savedDefaults()` (`:1942-1945`). Outline: `AreaVO:250-259`, at alpha × 1.2 | D (size) + V (alpha) + D (outline) | Size 3 under the mark: 2px, as line charts draw (D49; the bundle's 1.8px would be the fraction 2.6). The filled-radar outline uses the same size. Alpha 0.3 is seeded per resolved type, and `alpha` joins `savedDefaults()` under S0-1 (D54). Its reset slot covers filled radar only, as a type-conditional slot like `chart:radar` (`ChartChromeSlots:192`). The reset copy keeps an author's alpha on every other type, as MD §5.1 keeps the plot settings. A solid outline needs an `AreaElement` hint. |

**S4-1 corner flag (D47).**
- Released builds (`v1.1.x`, `main`) have `barCornerRadius` with a default of 0 and no seed. The `userBarCornerRadius` attribute is newer, and today a missing attribute reads as no opinion (`ChartVSAssemblyInfo:1791-1795`).
- So Modernize "Keep my formatting" overwrites a released author's radius, and Revert sets it to 0.
- From S4-1, a missing attribute derives the flag from the value: a non-zero radius is the author's.
- This branch writes the attribute on every save (`:1739`), so only files saved by a released build lack it.

**S4-2 corner rule (D52).**
- **The new rule.** On marked charts, where `f > 0`, the arc is `max(f × s, min(2px, s / 4))` in place of `f × s`.
  - s is the bar's thickness in screen pixels.
  - Each branch keeps its own caps. The round-all branch caps the arc at `s / 2`. The free-end branch caps it at `w / 2` and at the bar's length (`h` on a vertical bar, and the transpose for horizontal), so a bar shorter than it is thick gets a smaller corner.
- **How it reaches the code:**
  - It is gated by a modern flag that `GraphGenerator` sets on `IntervalElement` next to `cornerRadius`, added to `equalsContent` (M1).
  - The same rule goes into `computeArcZones` and, through a new `ChartRegion` field, into `chart-tool.ts`, so hover and selection outlines match the drawn bar.
- **Which charts:**
  - Classic charts are unchanged, including when the author set a radius.
  - On a marked chart the floor applies to an author's fraction too. It only raises a corner below 2px. An author's 0 still turns rounding off.
  - It reaches bar, horizontal bar, interval, pareto, waterfall and Gantt. Funnel, 3D and polar bars are skipped before it (`BarVO:225`).
- **Which ends round** (shipped, unchanged):
  - Plain bars round the value end, or all four corners when the author ticks Round All Corners.
  - Pareto rounds the outer end of the column, and waterfall both ends of it (`applyStackRounding`).
  - Interval and Gantt round all four corners.
  - A stack rounds only its outer end. Positive and negative stacks each round their own.

**S4-3 box plot (D46).**
- **Carrier.** A modern hint on the box plot's `SchemaElement` carries the chart's `barCornerRadius` to `BoxPainter`. `GraphGenerator` sets it where the element is created (`:3860`). Hints already reach the painter (`SchemaPainter.applyAlpha:158`).
- **Corner:**
  - Radius `f × s`, with the S4-2 floor and cap, on all four corners, in pixels. At 0.08 it is about half of today's s/6. Classic box plots keep s/6.
  - The Advanced pane's corner field is shown for marked box plots. Today `ChartPlotOptionsPaneModel:126-130` hides it, so the author can set it only while the chart is another type. An author's 0 gives square boxes.
- **Width:**
  - 30% of the slot, clamped to 6–28px on screen. It replaces today's `maxw/3 + (2/3)·maxw·size/30`.
  - 30% is below that formula's 33% base, so this is a painter rule, not a size value.
  - An author's size (`isSizeChanged`) keeps today's formula.
  - The box, median, whiskers and whisker caps (half the box width, `:62`) all derive from one width in logical units (`init()`), so they rescale together.
  - The painter is also run for regions and bounds (`SchemaVO:221-223`, `:293-294`) and with non-screen transforms (`:63`, dodge `:378-398`). These must agree with the drawn box.
- **With a Color dimension**, each box takes the width rule on its own, and the boxes sit side by side as today (`SchemaVO.dodge:360-398`, which does not fit them into the slot). Four or more series overflow the slot, where today three do (D57).
- **Height:** at least 2px, so a region of identical values draws a thin box rather than a hairline.
- The white median, whiskers and outliers are unchanged. The filled box is already shipped ungated.

**S4-4 bar width (D45, D53).**
- The 50% proportion already holds for bar, waterfall and pareto (51.7%). Only the pixel clamp is new: 3–32px in screen space, on marked charts, through the S4-2 element flag.
- **Clustered bars:**
  - Dodge places the bars before pixel sizes exist, fitting the cluster into the slot with its bars touching.
  - Each bar is clamped, then the cluster is re-packed around its centre so its bars still touch. This is a layout-time pass.
- **Crowded slots (D63).** The 3px floor never makes bars overlap. A bar floors at 3px or at its share of the slot (the slot width divided by the bars dodged into it), whichever is smaller.
  - A crowded cluster shrinks to touching bars, and a single bar in a slot under 3px fills its slot.
  - Without this, a two-series horizontal bar chart with 30 categories in a short card hid the second series down to half a pixel behind the next category's bar.
  - The facet rescale and a brushed bar over its base use the same floor. Waterfall bridges need no change: a bar that fills its slot leaves no gap to bridge.
- **No clamp** when a Size field is bound or the author set a size (`isSizeChanged`), as for S3's legend caps.
- **Stacks** clamp with their segments: they share x and width, and `computeFullBarBounds` reads the segment bounds.
- **Waterfall bridge lines** move with the clamped bars. Today they are placed in slot units when the geometry is built (`IntervalElement:386-391`, `:892-926`).
- **Faceted charts** rescale cached paths after layout (`VGraph:938-940`, `BarVO:1188-1222`). The clamp must survive that.
- **The Gantt bar** is 20 of 30 today (68% of its row, `GraphGenerator:5376-5377`). Under the mark it is 15 (D44), and then the clamp applies.

**S4-5 and S4-6 legend note.** A size legend whose smallest equals its largest now draws full symbols on every chart, classic included. It drew invalid (NaN) geometry before. This is the one classic-visible change of S4, an exception to M1.

**S4-11 tree corner (D48, D56).**
- **The flag.** `userNodeCornerRadius` (M4) is stamped only when the pane changes the value, since the pane writes the field on every Apply. The `nodeCornerRadius` script property goes through an author setter, as S3-1's legend position does.
- **The seed.**
  - Written in `seedChromeDefaults` on every chart, like the bar corner, while the flag is false: 0.08 modern, 0.3 legacy (this branch's creation default). The field draws only on trees.
  - The legacy write never changes a classic chart. It already holds 0.3, or (from a released build) it is flagged below.
- **Old files.**
  - A missing `nodeCornerRadius` attribute means a released build saved the chart. Rounded tree nodes came with Feature #74787 (`565e05c6e7`), which is not in `main` or `v1.1.x`. Such a tree draws square, and its flag is derived true.
  - So "Keep my formatting" and Revert both leave it square, and only the reset copy rounds it.
  - The flag is derived on every chart type, not trees only, because the parse does not know the type (D60). So a released chart later changed into a tree keeps square nodes too, and only the reset copy rounds them. Without the flag, the legacy pass would write 0.3 into that chart, and on a classic dashboard it would turn into a rounded tree where it is square today.
  - With the attribute present and no flag attribute, 0.3 is no opinion, and any other value is the author's.
  - The flag is written with `DerivedUserFlag`, so classic XML is unchanged.
- **Reset.** It joins 80398's slot list (M7). The reset copy clears the flag and seeds 0.08.
- **No pixel floor in S4.** 0.08 of the default 40×30 box is 2.4px, inside the bundle's "2 to 3px". S6-6 shrinks tree boxes and decides the floor then (D61).

### S5. Axis labels and titles

| # | Item | Bundle | Where | Kind | Notes |
|---|---|---|---|---|---|
| S5-1 | Automatic label rule when the author has not set the rotation | C5, bar 3, Gantt 4 | Rotation: `GraphGenerator.setDefaultRotation:6501-6553` (0° or 90°; a facet's inner axis always rotates), called at `:2382` and `:2764` only when the rotation is null. Thinning: `DefaultAxis.removeOverlappedLabels:1469`. Band and truncation: `DefaultAxis.getMaxLabelSize:954-966` (half the coordinate size, or two thirds without truncation, at least 50px; pixel truncation at `:90`) | D | See "S5-1 label rule" under this table. |
| S5-2 | Compact date labels | C5, Gantt 2 | Gantt: `GanttGraphGenerator:128-133` and `:148`, `getDefaultFormat:196-206`. The live abbreviation comes from the shared `GraphGenerator.setAbbreviate:2886-2892`; `GanttGraphGenerator:130`'s own flag is overwritten | V + D | Seed a compact date format (month names) on date axes (V). Show the year only once and drop labels that do not fit (D). The Gantt time axis keeps the density padding beyond the last date. |
| S5-3 | An axis title longer than its axis is hidden, never cut | C6 | `VGraph.createTitle:326`, bounds in `layout0` (about `:850` and `:876`), cut in `VLabel.getDisplayLabel:360` and `truncate:441` | D | A new `TitleSpec` flag set in `getTitleSpec`. |
| S5-4 | Point axis titles name the field, not the aggregate | point 2 | `getTitleSpec:6175` → `getRefLabel:6375` → `GraphUtil.getLabel:205` (`toView()`) | D | Only for the generated title, never for an author's text. Scatter contour already shows "Price" because its measures are not aggregated; that is not a title rule. |
| S5-5 | Radar spoke labels name the field without the aggregate, truncated | radar 2 | Spoke values are the full measure names (`ParallelCoord.ParallelScale.initValues:126-132`), shown through the alias `TextFrame` and `setTruncate` in `RadarGraphGenerator.fixParallelCoord:204-210` | D | Change the displayed label only. The `_Parallel_Label_` values key the aliases, formats and selection (`ChartRegionHandler`, `PlotArea`, `FormatPainterService`). An author alias still shows. |
| S5-6 | Radar scale labels on one spoke only | radar 3 | One axis per spoke (`AbstractParallelCoord.createAxis:83-92`); per-measure `labelVisible` is plain (`fixCoordProperties:2447-2463`) | D | See "S5-6 radar scale spoke" under this table. |
| S5-7 | Horizontal bar lists the first category at the top | bar 2 | Nothing reverses it today. Precedents: `CategoricalScale.setReversed` (`GraphDefault:117-126` for Gantt, `GraphGenerator:4117`) | D | A look rule: it follows the mark, Modernize applies it and Revert undoes it. The author's sort still decides the order. Check facets and the date-comparison `setReverseLines` (`:2632`). |

**S5-1 label rule.**
- **What the modern rule does:**
  - Thin the labels first.
  - Rotate only if overlaps remain, and never past 45°.
  - Keep the label band within 20% of the plot height, or within 30% of the card width for a horizontal category axis.
  - Truncate with an ellipsis within the band.
- **Truncation is by pixels.** The bundle's "about 12 characters" and "about 20 characters" are the expected results at 10px. There is no character-count setting, and none is added.
- **The author's settings still win:** a rotation, `AxisDescriptor.truncate`, a fixed axis size (`fixedWidth`, `fixedHeight`) and label visibility.
- **New `AxisSpec` properties** are set by the generator: maximum rotation, band ratio and thin-before-rotate.
- **Facet inner axes:** whether they keep "always rotate" is the builder's call.

**S5-6 radar scale spoke.**
- Labels go on the spoke nearest 12 o'clock. Spoke *i* sits at (*i* + 0.5)/*n* × 360°, so only 6, 10, … spokes have one exactly at the top.
- They are hidden on the other spokes only where the author has not set label visibility.

### S6. Padding and sizing

| # | Item | Bundle | Where | Kind | Notes |
|---|---|---|---|---|---|
| S6-1 | Plot keeps the density padding on all four sides | C19 | The card inset is already seeded on all four sides and travels: `ChartVSAssemblyInfo.seedChromeDefaults:131-135` (`!isUserPadding() && !isCssPaddingDefined()`), `VSDensityDefaults.chartPadding` 8, 12, 16. The chart draws `padding.top` below the title lane (`VGraphPair:3019`) | verify | The bundle measured about 5px at the top, while GEO §1.1.1 found no gap at render. Measure on a live chart first (§7). Fix only what takes the space. |
| S6-2 | Pie and donut diameter at most 80% of the plot height | pie 3 | `PolarCoord.getRadius:986` uses `pieRatio` (plain, 0 = auto), set in `createPolarCoord:928` | V + flag | Seed 0.8 under modern (the principle in M2). The donut hole stays about 45–50%, because the ring is size 15 of 30 (`:5222`); `holeRatio` is for another type. |
| S6-3 | Radar keeps the density padding from the title, the card edges and the legend | radar 6, C19 | `AbstractParallelCoord.fit:136`, `PolarCoord.fit:532` (no padding input) | D | Check S6-1 first: the card inset may already bound the radar. Long labels must still fit. |
| S6-4 | Circular network inside the plot, at most 80% of its height | relation 4 | `RelationElement.mxLayout:327-334`: radius 90% of the smaller layout side, minus the node size | D | The overflow itself is the separate bug 02. This item adds only the 80% cap and the padding. |
| S6-5 | Network graph keeps the density padding | relation 5c | `mxLayout:360-366`, gap `min(20px, 5%)` | D | Not spreading on a large card is the separate bug 03. |
| S6-6 | Tree opens at its root and scales to the card width, down to a minimum box size, then scrolls | relation 1 | `mxLayout:341-348` (`COMPACT_TREE`, gap 12, `scalePositions` false). The client resets the scroll to 0,0 (`web/projects/portal/src/app/graph/objects/chart-area.component.ts:444-464`) | D | Server fit plus client initial scroll (root at the top, centred). A box under 25px takes the 0.08 corner below 2px, so this item decides whether the tree corner gets S4-2's floor (D61). |
| S6-7 | Contour map fits its extent to the data | point 7 | `MapGenerator:405` `setFullMap(!isZooming())`; web maps already pad 0.05 (`:415`) | D | Only when the author has no zoom, pan or `lonlat` (`PlotDescriptor:1937-1940`). |
| S6-8 | Pie and donut start at 12 o'clock and run clockwise | pie 2 | `PolarCoord.transformPoint0:402-407` (θ = 0 at 3 o'clock, counter-clockwise); `rotate` (`:168`) is reachable only from script | N | A new start-angle and direction property on the coordinate. The modern default is seeded. This is look, so it follows the mark. The largest-first sort is S7-3. |

### S7. Creation-time defaults

All of these apply when a chart is created or changes type on a modern dashboard (M6). Modernize ("Keep my formatting") and Revert never apply them. The reset copy applies all of them except S7-1.

| # | Item | Bundle | Where | Notes |
|---|---|---|---|---|
| S7-1 | A date dropped on a shelf groups by month | line 2 | `VSChartBindingHandler.setDefaultDateLevel:470-480`, `BDimensionRefModel:97-105`, `ChartDndHandler.fixChartRef:796-801`, `WizardRecommenderUtil:342` | Gate with `VizContext.of(chart)` at drop. Charts only: the crosstab path (`VSCrosstabBindingHandler:389`) is out. It changes the query. Not applied by the reset (D5). |
| S7-2 | Area with a colour dimension is stacked | line 3 | Stacking is the chart type (`GraphTypes.isStack`, `GraphGenerator:3967-3984`). AUTO never resolves to area (`AbstractChartInfo:1029`, `:1051`) | Choose the stacked area type in `ChangeChartTypeService` and in the client's stack box default. It changes `StackRange`, tooltips and show-value totals. |
| S7-3 | Pie and donut slices sorted largest first | pie 2 | The pie dimension moves to colour in `fixPieDimensions:431-461`; its order is the default `SORT_ASC` (`VSDimensionRef:1964`) | Set a by-value descending sort. Slice colours follow the sort (`CategoricalScale:152-160`), so colours go by rank. |
| S7-4 | Waterfall keeps the data order | funnel 5 | `VSDimensionRef.order` defaults to ascending for every dimension (`:1964`, read at `:1028`) | Set no sort (original order). For an aggregated database query, the original order is whatever order the query returns. |
| S7-5 | Treemap family coloured by its top level when no colour field is bound | hierarchy 1 | `copyToTreemap` (`ChangeChartTypeProcessor:1132`); the wizard already does this | Clearing the colour stays cleared. |
| S7-6 | Marimekko coloured by its Break By field when no colour field is bound | funnel 7 | `copyToMekko` (`:1219`); today `GraphGenerator:5013-5014` only sets the inner dimension | — |
| S7-7 | Candle's Color field takes no dimension; switching to candle clears a colour dimension | candle 2 | Server: `ChartDndHandler.getFixedXYRef:1597-1602`. The client drop check is not traced (`vs-chart-editor.service.ts` `isFieldAcceptable`) | A modern-dashboard rule only. The Color field edits the up and down colours (S2-6). |

### S8. Family items

| # | Item | Bundle | Where | Kind | Notes |
|---|---|---|---|---|---|
| S8-1 | Zero line in the axis-line colour when the value axis has both positive and negative values; vertical too when X crosses zero | C18, bar 4, line 4, point 8 | No zero line exists. `DefaultAxis.createGridLines:665-675` darkens the 0 gridline only where gridlines exist | D | A new `AxisSpec` zero-line colour, set from the generator. It is drawn even where the value axis line is off (C4's exception). Covers bar, line, point and waterfall. |
| S8-2 | Radar web: faint polygon rings at the scale values, in the grid colour | common 11, radar 4 | The spoke specs keep grid NONE (`AxisSpec:689`), although `AbstractParallelCoord.createAxis:100-124` already draws spoke-to-spoke lines when a grid style is set. `PolarCoord.setWebGrid:1104` exists but only scripts reach it | D | Wire the grid style and colour onto the spoke specs, and call `setWebGrid` under modern. Without the gate every old radar would gain rings, because the Y grid defaults to thin. Spokes keep the grid colour (TC D15). |
| S8-3 | Values on funnel stages and waterfall bars at least about 18px tall; no vertical grid behind the funnel | C14, funnel 2, waterfall | `PlotDescriptor.valuesVisible` (plain, `:1921`), read at `GraphGenerator:1007-1009` | V + flag + D | See "S8-3 funnel and waterfall values" under this table. |
| S8-4 | Shape encodings use filled markers | point 3 | `CategoricalShapeFrame()` (`:47`: outlined circle, triangle, square first). The frame's `changed` flag is saved (`VisualFrameWrapper:156`) | V | Seed a filled shape list while the frame is unchanged. The same frame serves line markers; check them. |
| S8-5 | Hide the size legend for the treemap family | hierarchy 2 | `processSizeLegend:1854` reads `sizeDesc.isVisible()`, which is plain (`LegendDescriptor:85`, written at `:512`) | V + flag | Sunburst, circle packing and icicle share it. |
| S8-6 | Tree boxes: a palette tint at about 15%, a 1px outline in the full colour, dark chrome ink | relation 5b | `RelationVO:153-158` already fills with `getFillColor()` and outlines in the node colour when colour is unbound; the generator line is commented out (`GraphGenerator:1489-1494`) | D (unbound) + N (bound) | Pre-blend the tint, as in S2-4. Network and circular nodes stay solid. |
| S8-7 | Gantt grid lines at labelled ticks only | Gantt 3 | Time axis grid from `PlotDescriptor.yGridStyle` (`createAxisSpec:2815`); task-row lines from `GanttGraphGenerator.fixGanttCoord:151-155` | D | No such mode exists. Check the "dozens of ticks" observation first (§7): `TimeScale.getTicks:480-488` aims for about 5. |
| S8-8 | Gantt bars at least about 3px tall, task labels thinned | Gantt 4 | `BarVO.getBarSize` has no pixel floor; label thinning as in S5-1 | D | Shares the clamp with S4-4. |

**S8-3 funnel and waterfall values.**
- Seed `valuesVisible` true for funnel and waterfall under modern, protected by a user flag.
- Hide a label on a mark shorter than about 18px. This is new graph code, not traced.
- The automatic white or near-black colour is turned on for funnel and waterfall. Today it is enabled only for treemap and relation (`GraphGenerator:4484`).
- The funnel's x grid is seeded NONE, because the fill is the data (C3).

## 6. Corrections to the bundle

The bundle was written without reading the repo's docs, and some of its code premises are wrong. Where it conflicts with the code, the code facts below win. Its look decisions stand.

**Already shipped:**
- The grid colour `#D6D2C8` / `#4A484F` and the axis-line colour `#85827A` / `#7E7B86` (common item 3, #7750).
- The translucent card (C1).
- Bar, waterfall and pareto widths of about 50% of the slot.
- Rounding on both ends for waterfall, interval and Gantt; free end only for pareto.
- Teal for new modern scatter contours.
- Stacked-legend reversal for vertical layouts.

**Cost classes that were wrong:**
- "Seed only" items on chart descriptors could not travel without S0. The legend gap is a resolver, not a seed.
- C11 is a seed, because static colours already keep their tiers. "Needs provenance" was wrong for it and for the box plot colour.
- These items need provenance, which the bundle missed:
  - hiding the size legend (plain field)
  - turning on values (plain field)
  - the waterfall sort (plain field)
  - the tree corner (plain, with no flag)
- These are drawing rules, not seeds:
  - the 2px corner floor
  - the Gantt grid at labelled ticks
  - the size-legend symbol colour
  - the ramp floor
- The pareto ticks are a one-line change, not new behaviour.

**Wrong facts:**
- Candle does not darken its colour.
- The Gantt bar is about 68% of its row, not 60%. Its pill ends come from the modern 0.3 seed.
- The point bubble's largest size is 15, close to a radius, not 30. It draws 34px across (radius = size + 2).
- Relation edges reach about 15px, not 35px.
- The box plot box is about 44% of its slot, and its corner radius is a sixth of its short side, not a third.
- The interval bar's extra thickness comes from a size of 15 with smallest = largest − 1, not from a largest of 30.
- The waterfall's "alphabetical" order is the default for every dimension.
- The funnel is sorted by value, not by data order.
- The tree corner comes from `nodeCornerRadius`, not the bar seed.
- The radar has a spoke at 12 o'clock only for 6, 10, … spokes.
- The contour title drops "Sum(" because its data is not aggregated.
- A legend fill of `#252428` is stale; it is `#3E3C43` at 50% (TC D20).

## 7. Check on a live chart before building

Each of these is a claim in the bundle that the code reading did not confirm. The plan for each slice runs its checks first, on the bundle's repro dashboards (`chart-defaults-design/repro/charts.md`).

| Check | Slice | What to find out |
|---|---|---|
| The top gap below the title | S6-1 | The bundle measured about 5px at the top against 12 at the bottom. GEO §1.1.1 found no gap at render. Did the measurement count the top value label, which sits centred on the top gridline? |
| 9px or 10px labels | — | The chart-type sizes ticket maps tick and data labels to 9px (`DEFAULT_SMALL_FONT`). The bundle observed 10px. C13 stays as shipped either way; record which is true. |
| Stacked-legend order | S3-4 | The code already reverses vertical stacked legends. Is the observed order really reversed? Record the order of a side legend and of a top legend on a vertical and on a horizontal stacked bar. |
| Legend overflow | S3-2 | What a legend that hits today's 50% cap does with entries that do not fit, on screen and in a PDF export. The capped modern legend keeps that behaviour. |
| Gantt tick count | S8-7 | "Dozens" of grid lines at 25 tasks against about 5 ticks from `TimeScale`. |
| Relation edge width | S4-9 | Answered 2026-11-12: the capture shows edges of about 15px, which largest 15 explains. |
| Direction-token contrast on the real card | S2-13 | Sample now that #7750 has shipped. |

## 8. Out of scope

**Bugs logged separately**, fixed for every chart and not gated on the mark. The drafts are in `E:/Temp/chart-defaults-bugs/`:
- 01: the pie clipped in Preview. Probably a duplicate of Bug #80312; re-check first.
- 02: the circular network cut off.
- 03: the network not spreading on a large card.
- 04: the gradient legend labelled "2K" at both ends.
- 05: waterfall ticks skipping zero.

**Blocked:**
- The per-panel value range and minimum panel height for candle and stock (candle 5, "several series become panels"). Blocked on the product.

**Settled with no change,** as the bundle's known limits:
- no top N or scroll default
- many series, slices, spokes or levels
- month names stored as text
- negative values on a pie or radar
- small regions on a world map
- the contour map's Break By shelf

**Script-only and DSL behaviour** is untouched, except that every script setter of a property flagged in M4 stamps the user flag.

## 9. Left to the builder

- The exact list of fields for S0-1, and whether the render-time CSS writes stay (S0-2).
- How each axis is identified as the value axis or the category axis for S1-4, including facets and dual axes.
- How the composer's legend count reaches the saved descriptor: which draw takes it, how the composer is told apart from the viewer and exports, whether the "waiting for a count" marker is saved, and keeping the write inside the undo step of the action that seeded it (S3-1).
- How the placement seed re-runs on a Color, Shape or Size binding change: a wider `ResolvedChartType` key or a second key (S3-1).
- Whether the legend alignment is a tiered value or a plain field with a flag, and where several start-aligned legends go when they are wider than the plot (S3-3).
- Which legend descriptors carry the title flag (S3-6).
- The shape of the up and down colour property, and the editor for it (S2-6).
- The pre-blend base for the map highlight and the tree tint (S2-4, S8-6).
- How an author sets a per-level colour on a sunburst or icicle (S2-11).
- The facet inner-axis exception in S5-1.
- How the clustered-bar re-pack runs at layout time, and how the width clamp survives a faceted chart's rescale (S4-4).
- The `ChartRegion` field that carries the corner rule to the browser outline (S4-2).
- How the box plot's pixel width reaches the painter's logical-unit layout so regions, bounds and dodge agree (S4-3).

## 10. Decisions

"Owner" means ruled by the owner on 2026-11-06, on 2026-11-11 from D28 on, or on 2026-11-12 from D44 on. "Design" means settled while writing this spec, while amending S3 for D32 to D42, or while amending S4 for D51 to D62.

| # | Decision | Who |
|---|---|---|
| D1 | The bundle's look decisions are requirements, as confirmed by the design owner | Owner |
| D2 | Seeded defaults are saved in the asset, so they travel and the dialogs show what draws (M2, M3). S0 comes first. This revises an earlier proposal to resolve chart settings at draw time, which would break design-time WYSIWYG and portability | Owner |
| D3 | Prefer a value of an existing property over a drawing rule wherever the rule can be stated that way (M2) | Owner |
| D4 | Binding and data defaults apply at creation or type change on modern dashboards only. Modernize "Keep" and Revert leave them alone (M6) | Owner |
| D5 | The reset copy applies the creation-time defaults except the date level (M6, R7) | Owner |
| D6 | The reset clears axis-title visibility and value-axis-line visibility (R8) | Owner |
| D7 | Rulings R1 to R8 override the earlier decisions named in §4 | Owner |
| D8 | Direction colours follow the mark (R9) | Owner |
| D9 | The five defects in §8 are separate Redmine bugs, fixed for every chart | Owner |
| D10 | Top and bottom legend gap of 16, 20 and 24 px on screen (R5) | Owner |
| D11 | Deliverable: this overall spec now, then plans for S0 and S1. Later plans are written when their slice starts | Owner |
| D12 | Every graph-package change is gated through a property the generator sets, including changes that look harmless for every chart (M1) | Design |
| D13 | No load-time migration for already-marked charts (M8) | Design |
| D14 | Data-label ink off a mark stays the title tier (R10) | Design, following the owner's acceptance of the recommendations |
| D15 | The bubble cap is expressed as about 20px across, because the code's size is close to a radius (S4-8) | Design |
| D16 | Radar scale labels go on the spoke nearest 12 o'clock (S5-6) | Design |
| D17 | Hierarchy level steps use the palette's shade function (S2-11) | Design |
| D18 | The Gantt milestone takes slot 2 by measure order, and its legend loses the "Measures" title through S3-6 | Design |
| D19 | A waterfall's "data order" means no sort (S7-4) | Design |
| D20 | Pie start angle and direction are look, a new property that follows the mark. The largest-first sort is creation-time (S6-8, S7-3) | Design |
| D21 | The bundle's text is mirrored into the repo without images; the zip remains the full source | Design |
| D22 | DEFAULT tiers are saved on marked charts only. Classic charts keep their XML and their `format.css` lookup (M3) | Design, from the reviewer's check |
| D23 | A plain value in a file saved before its flag existed counts as the author's when it differs from every classic creation default (M4) | Design, from the reviewer's check |
| D24 | Chart descriptor legacy values are written on every legacy pass; TC R3 stays for card border widths (M5) | Design, from the reviewer's check |
| D25 | Per-type seeds key on the resolved type and re-run on every change of it, including binding changes (M5, S0-6) | Design, from the reviewer's check |
| D26 | A typed axis title keeps its title visible on new charts, on Keep and on the reset copy (S1-2) | Design, from the bundle |
| D27 | Straight lines are S1-8. A type change keeps an author's smoothing | Design, from the reviewer's check |
| D28 | A legend stays where the chart was designed. It never moves in the viewer or with later data. The "more than 5 entries" rule is a count taken once, when the composer draws the chart after a seed, and saved (S3-1) | Owner |
| D29 | A measure on Color seeds a right legend from the binding. A stored right legend with no flag is no opinion. The gap and the legend title follow the saved position. The legend title gets a user flag, and while it is unset the title follows every position change, an author's move included (S3-1, S3-5, S3-6) | Owner, approving the S3 review's proposal |
| D30 | The 30% and 25% caps apply only to a legend the author has not sized; an author's size keeps today's 50% cap; overflow behaves as today. Alignment is start, centre or end against the plot's horizontal extent, in the dialog and in script (S3-2, S3-3) | Owner, approving the S3 review's proposal |
| D31 | A top or bottom legend on a horizontal stacked bar reads left to right in the same order as the segments (S3-4) | Owner |
| D32 | The types the earlier S3-1 lists left out are placed by family: step area, waterfall and pareto at the top; filled radar, tree, contour map and scatter contour on the right | Design |
| D33 | The legend count is the total across every visible legend | Design |
| D34 | `SyncChartHandler` copies the legend layout and title flags and never stamps them; an earlier S3-1 list made it a stamping path (S1's wizard rule) | Design |
| D35 | "Show all legends" from no legend restores the type default on a marked chart and clears the layout flag | Design |
| D36 | A side legend on a horizontal stack puts the outermost segment at the top (S3-4) | Design |
| D37 | The reset clears the legend-title flag, following R8 | Design |
| D38 | An unset alignment draws today's layout on every chart, so classic charts stay pixel-identical; the setting is offered on every chart | Design |
| D39 | Only the composer writes the legend count. A chart saved before the composer draws it keeps the seeded default until the next re-seed | Design |
| D40 | The placement seed also re-runs on a Color, Shape or Size binding change, which the resolved-type key does not see | Design |
| D41 | The count's verdict is saved, and the modern placement seed depends only on the resolved type, the Color binding and that verdict. Every re-seed, the viewer's included, reproduces the designed position without counting (D28 with M5) | Design, from the builder check |
| D42 | A typed legend title counts as the author's and stays visible on a top or bottom legend, on "Keep" and on the reset copy (S1-2 and D26 applied to legends) | Design, from the builder check |
| D43 | A legend the author resized stays where it is: a resize stamps the layout flag, and an old file with a stored legend size derives it. The reset copy clears both (S3-1) | Owner |
| D44 | The S4 sizes (Gantt bar 15, largest bubble 8, largest edge 6, milestone 3, radar line 3) are chosen under the mark in both `fixSizeFrameValues`, because every draw rewrites the default sizes. They do not travel to an older build (R11). An edit to any value in the Size dialog makes the whole frame the author's (M2, M9, S4) | Owner, 2026-11-12 |
| D45 | The 3–32px bar clamp applies per bar, and a cluster is then re-packed around its centre so its bars touch. No clamp with a Size binding or an author's size (S4-4) | Owner, 2026-11-12 |
| D46 | Box plot: the 0.08 corner (about half of today's), the corner field shown on marked box plots, and a width of 30% of the slot (6–28px) as a painter rule (S4-3) | Owner, 2026-11-12 |
| D47 | `userBarCornerRadius` is derived on parse: a missing attribute with a non-zero radius is the author's (M4, S4-1) | Owner, 2026-11-12 |
| D48 | A tree saved by a released build keeps its square corner on "Keep my formatting" and on Revert; the reset copy gives 0.08 (M4, S4-11) | Owner, 2026-11-12 |
| D49 | Radar series lines are size 3 (2px), as line charts draw, and the filled-radar outline follows (S4-12) | Owner, 2026-11-12 |
| D50 | A marimekko segment under about 2px draws as a 1px line in its colour over the white separator; its neighbours keep their size (S4-6) | Owner, 2026-11-12 |
| D51 | S4 is split: S4a (S4-1 to S4-6 and the Gantt bar) stacked on S3, then S4b (S4-7 to S4-12) | Design |
| D52 | The corner floor is carried by a modern element flag to the bar, the region arc zones and the browser outline. It applies to an author's fraction on marked charts, and the free-end branch keeps its length cap (S4-2) | Design |
| D53 | Waterfall bridge lines move with the clamped bars; the waterfall's 2px minimum height applies after stack rounding (S4-4, S4-6) | Design |
| D54 | Filled-radar alpha joins `savedDefaults()` and is seeded 0.3 per resolved type; the solid outline is a new `AreaElement` hint (S4-12) | Design |
| D55 | Relation node size is not capped; the bundle decided nothing about it (S4-9) | Design |
| D56 | `userNodeCornerRadius` is written with `DerivedUserFlag`, and the tree corner is seeded on every chart like the bar corner (S4-11) | Design |
| D57 | With a Color dimension, each box plot box takes the width rule on its own and the boxes sit side by side as today (S4-3) | Design, from the builder check |
| D58 | The bubble cap applies only where Size draws a point shape: word clouds and contour charts keep their sizes (S4-8) | Design, from the builder check |
| D59 | A waterfall step of exactly zero draws no bar; a shorter step grows from its start in the direction of its change (S4-6) | Design, from the builder check |
| D60 | The tree-corner flag is derived on every chart type a released build saved, so a released chart changed into a tree keeps square nodes until the reset copy (S4-11) | Design, from the builder check |
| D61 | The tree corner takes no pixel floor in S4; S6-6 decides it when it shrinks tree boxes (S4-11, S6-6) | Design, from the builder check |
| D62 | The filled-radar alpha reset slot is type-conditional: the reset copy keeps an author's alpha on every other type (S4-12, D54) | Design, from the builder check |
| D63 | A bar floors at 3px or at its share of its slot, whichever is smaller, so the floor never makes bars overlap (S4-4, D45) | Owner, 2026-11-13 |

## 11. Testing

- **Seeds:**
  - Extend `ChromeSeedClassificationTest`, `ModernVisualizationDefaultTest` and `SeedChromeDefaultsTest`.
  - Every V and N item asserts the modern and legacy branches, and that an author value survives a re-seed.
- **Round trip:** `VizModernizeUtilTest` must show that Modernize then Revert renders identically to a dashboard never modernized (P6, as amended by MD §4).
- **Reset and residue:**
  - Each new property joins feature-80398's `ChartChromeSlotsTest`, `VizModernizeResetTest` and `VizModernizeResidueTest`.
  - The reset applies S7 except S7-1.
- **Travel (S0):** write a seeded chart, read it with a fresh descriptor, and check that the DEFAULT tier is there. An author's bare value from an old file still reads as USER. A classic chart's XML is unchanged.
- **Bookmarks:** a bookmark taken while modern and restored after Revert draws classic, for every chart descriptor value (M5).
- **Old files:** an old file's non-default plain value is flagged on parse and survives Modernize, Revert and residue cleanup (M4).
- **Resolved type:** an AUTO chart that becomes a point chart through its binding gains its point titles and x grid. A type change keeps the author's values (M5).
- **Classic identity:** every D item renders a classic chart before and after the change and compares the images. Gated graph code must leave classic output unchanged.
- **Sizes (D44):** after a draw, a classic chart's size frames hold the classic values, a marked chart's hold the modern ones, and a frame the author changed is untouched. Modernize, Revert and the reset copy each draw the right size with no seed.
- **Corners (S4-2):** the drawn bar, the region arc zones and the browser outline agree on a marked chart (Java and portal tests).
- **Spring context:** Java tests that reach the seed or the generator need the Spring context set up as `CLAUDE.md` describes.
- **Manual:**
  - Rebuild the bundle's dashboards from `repro/charts.md` on the built server.
  - Compare against the drawn cards in light and dark, at the three densities, in the composer, Preview, the portal viewer and the PDF and PNG exports.
