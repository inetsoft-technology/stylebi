repo: inetsoft-technology/stylebi
branch: viz-updates
path: web/projects/portal/src

## Last sync
date: 2026-08-12T04:10:00Z
commit: (unchanged — `viz-updates` resolved to tree 52c127c128c3, identical to the previous four
syncs. Tree hash, not a commit sha.)

### Updated in this project
- **Searched for the annotation-rectangle radius the card seed's comment claims to match — could not
  corroborate it.** No radius constant under `vsobjects/objects/annotation/`, and none in the bounded
  server scan. Not proof of absence; recorded as an unverified comment rather than a pending check.
- **Found a third off-scale radius.** `CalendarVSAssemblyInfo:1420` seeds
  `setRoundCornerValue(10)`. So: card 12px, calendar 10px, DOM scale tops out at 6px
  (`_variables.scss:523`). The card decision to move to 6px leaves the calendar diverging — flagged in
  the new ticket, not audited.
- **Found a second, better reversibility mechanism.** `PlotDescriptor.getBarCornerRadius()` returns
  `modernCornerSeed && !VSObjectChromeDefaults.isModern() ? 0 : barCornerRadius`, with
  `modernCornerSeed` commented as "modern mode seeded the radius rather than a user setting it, so the
  gate may take it away again." Per-value provenance done with a flag rather than value-sniffing.
- **New file: `Seeded value reversibility - ticket.md`.** Decision to consolidate all three mechanisms
  (value-sniffing, `modernCornerSeed`, the seed mark) on the mark. Names the blocker: **the mark is
  version-blind** — it records which branch ran, not which defaults existed, so a second seeded
  default added later is wrongly assumed present on assemblies marked before it existed.
  Value-sniffing is immune to that. Also records the granularity cost (`VSCompositeFormat` has no
  back-pointer to its assembly), the sequencing, and two concrete upsides (the tab carve-out
  disappears; a `format.css`-set 12px radius stops being wrongly stripped).
- **Also confirmed:** `ChartVSAssemblyInfo:96` sets `plotDesc.setBarCornerRadius(0.3)`, so the gate
  already modernizes part of the chart *interior*, not only the card. §10.3's "chart is the hard half"
  still holds for greys and palette (server-side `GDefaults`), but the plot is not untouched by the
  gate — not yet reflected in the spec.
- Files read this pass: `VSCompositeFormat.java` (grep), `PlotDescriptor.java` (grep),
  `CalendarVSAssemblyInfo.java` (grep), `ChartVSAssemblyInfo.java` (grep),
  `annotation/vs-annotation.component.ts` (grep). Documents changed: new ticket, `README.md`.

### Updated in this project — 2026-08-12T04:00Z
- **Traced the modern gate to its real source: an org-scoped server property, not client code.**
  `VSDensityDefaults.isModern()` reads `SreeEnv.getBooleanProperty("viewsheet.modernVisualization",
  false, true)`. Three siblings: `viewsheet.darkMode` (requires modern), `viewsheet.density`
  (dense/compact/comfortable, default dense), `viewsheet.modernObjectChrome` (sub-toggle, on by
  default under modern, shared with `VSTitleChromeDefaults`). `GuiTool.isVizModern()` only *reads*
  `.viz-modern` off `document.body`; every `classList.add("viz-modern")` in the portal is in a
  `.spec.ts` test. `normalizeMode`'s comment names "the browser body-class whitelist" as the
  consumer, so the body class is emitted server-side from these properties. Files read this pass:
  `VSDensityDefaults.java`, `VSObjectChromeDefaults.java`, `scss/_viz-tokens.scss`,
  `common/util/gui-tool.ts`.
- **CONFLICT, now flagged in §01 and both token tables: the card corner radius.**
  `VSObjectChromeDefaults.CARD_CORNER_RADIUS` seeds **12px** into the saved asset at creation
  (commented as matching the annotation-rectangle radius); this spec binds the card to
  `--inet-radius-xl`, which is **6px** and the top of the DOM scale (`_variables.scss:523`) — 12px is
  not on the scale at all. The server value is what gets written into assets and shown in the format
  editor, so it wins unless changed. Either the scale gains a 12px step or the seed drops to 6px;
  recorded as a conflict rather than silently resolved.
- **§10.3 reframed from absence to reconciliation.** Dark is not undecided at the card level:
  `VSObjectChromeDefaults` already defines card background `#252428`, object border `#49454F`, page
  canvas `#1C1B1F`, text `#E6E0E9`, and an `applyDarkForeground` resolver substituting light text onto
  a clone at both model build and export. This spec's greys are light-mode literals never checked
  against those four. What remains genuinely undecided is the chart interior, which is server-rendered.
  §09's open item updated to match.
- **§10.2 confirmed dark-correct against a real value:** the shipped dark card `#252428` computes to
  L ≈ 0.02, well under the 0.45 threshold, so the glyph flips to the light tone with no dark branch.
- **Mechanism note:** `resolveSeededCorner` reverts the seeded radius to square when the gate is off
  by testing *exact equality with the seed value* — value-sniffing, which is what the sibling
  project's seed mark exists to replace. The mark would generalize this rather than sit beside it.
- **Consequence worth holding onto, stated carefully:** the `.viz-modern` body class gates browser CSS
  only, and nothing in the client sets it, so all *CSS-gated* work is currently unreachable in the
  browser. Seeding is a separate path — `VSObjectChromeDefaults.isModern()` reads
  `viewsheet.modernVisualization` / `viewsheet.modernObjectChrome` from `SreeEnv` server-side and
  writes through `setDefaultFormat` — so the unwired class says nothing about what has been seeded.
  Both properties default off and an EM control exists for them; whether any deployment has enabled
  them is **not established here** and must be checked before anything relies on the seeded cohort
  being empty.
- Documents changed this turn: `Chart Card Spec v3.dc.html` (§01, §09, §10.3, both token tables).
  No repo files copied.

### Updated in this project — 2026-08-12T03:27Z
- **No upstream change; read two toolbar files in full to answer a geometry question, and they
  contradicted the spec in three places.** Read at tree 52c127c128c3:
  `mini-toolbar.service.ts`, `mini-toolbar.component.scss`, `mini-toolbar.component.html`.
- **§04's lane-height rule is not what shipped.** The spec says the title lane is the greater of the
  title's line box or the strip's height. `.mini-toolbar` is `position: absolute` in every state,
  placed at a computed `top`/`left`/`width`, so it **overlays** the lane rather than growing it.
  Consequence both ways: anchoring cannot shrink the plot on any existing chart — no reflow, gate on
  or off, which answers the preservation question for the toolbar half — but where the title's lane is
  shorter than the strip, the strip overhangs onto the plot (~14px at a 13px title, 44px on touch).
  Flagged in §04 as spec-vs-code disagreement to reconcile; not silently rewritten.
- **The spec's "opacity only, never display" instruction was wrong, and the SCSS says so on purpose.**
  The action groups use `display: none` at rest, with the reasoning recorded in the file: the pill is
  `width: fit-content` right-aligned by `margin-left: auto`, so transparent-but-in-layout buttons
  would leave a wide empty pill at rest instead of one wrapped tight around the kebab, and
  `.mini-toolbar-button-group` sets `pointer-events: all` unconditionally so a transparent button
  would still be clickable. The file's comment ends "Do not 'fix' this back to opacity." **The kebab**
  does use opacity (0.55 → 1), which is correct and necessary — opacity is not escapable by a
  descendant, so a kebab inside an `opacity: 0` container multiplies to zero. §10.1, §02's mock
  caption, §02's fourth source edit and §04's caption all corrected to the two-rule version.
- **A second capability idiom already exists in that file.** `@media (pointer: coarse)` scopes the
  44px touch target. §10.1's query is `(hover: hover) and (pointer: fine)` — a different question
  (can we reveal on hover, vs how precise is the pointer). Both should stay, commented, or a later
  reader collapses them and changes hybrid behaviour. Noted in §10.1.
- **Also confirmed:** the shipped resting kebab is `opacity: 0.55` on every device, full on coarse
  pointers — so §10.1's empty-lane-on-desktop behaviour is genuinely not yet shipped, as recorded.
  `ANCHORED_ASSEMBLY_TYPES` holds the six types from slices 1–3 with the container deliberately
  absent, matching the previous sync.
- Documents changed this turn: `Chart Card Spec v3.dc.html` (§02, §04, §10). No repo files copied.

### Updated in this project — 2026-08-12T02:58Z
- **No upstream change. `viz-updates` has not advanced since the 2026-08-11T23:58Z sync** — same
  resolved tree at `web/projects/portal/src/` and at the repo root, so nothing outside the tracked
  path moved either. No files pulled, no screens rebuilt, no screen-map rows touched. Everything the
  previous entry recorded stands as read.
- **Two spec decisions were made in-project this turn, not pulled from the branch.** §02's resting
  kebab is now scoped by pointer capability (`@media (hover: hover) and (pointer: fine)` — drawn at
  rest on touch, revealed on hover on mouse/trackpad, hidden with `opacity` so the button keeps its
  tab order), and the toolbar glyph now takes one of two tones derived from the author-set card
  background's relative luminance (`L > 0.45` → light) instead of sitting in a white bordered box.
  Both are read-time CSS. **The first modifies behaviour already shipped on this branch** — see the
  kebab-only caveat below.
- **Open against the shipped code, needs a decision before the query is applied:** slices 1–3 shipped
  the anchored kebab for chart, the table family, and selection list/tree as kebab-only. For the
  kebab-only families — and for the §06 32–56px rung — the kebab *is* the whole strip, so the pointer
  query would leave those cards with no chrome at all at rest on a desktop. Not what `kebabOnly` was
  approved for. Chart and the table family can take the query first.
- **One implementation detail the tone rule exposes:** the kebab is an `<img>` in
  `mini-toolbar.component.html`, so `color` cannot tint it. Needs inline SVG with `currentColor`, or a
  `filter: invert()` pair.
- **Read a sibling project's patch file addressed to this one.** `Chart card handoff - mark impact
  patch.md` in the Visualization Widget Spec project (`ed3d2aff`) carries six edits to
  `Open items - handoff.md`; all six find-strings still match this project's copy. Applied so far: the
  seed-mark section of `Corrections since handoff.md` rewritten from that project's §03 — the chart is
  one of the eight *seeded* types, so §01 card chrome is marked and gated identically, while §02's
  toolbar, §06's ladder, tooltips, selection and the type scale are read-time and unmarked. The six
  handoff edits themselves are not applied yet.
- Documents changed this turn: `Chart Card Spec v3.dc.html` (§02, §03), `README.md`,
  `Corrections since handoff.md`. No repo files were copied.

### Updated in this project — 2026-08-11T23:58Z
- **Slice 3 of the anchored-toolbar rollout shipped: the selection family, kebab-only.** 20 files across the
  5 newest commits under `web/projects/portal/src/`. `ANCHORED_ASSEMBLY_TYPES` now includes
  `vsselectionlist` and `vsselectiontree`, commented "anchored but not capped," and the mechanism is a new
  `AbstractVSActions.kebabOnly` getter (overridden `true` in `selection-list-actions.ts` and
  `selection-tree-actions.ts`) that returns a budget of zero action buttons at any width. This is **Case 2 of
  `Anchoring beyond charts - discussion.md` implemented exactly as decided** — as a capability rather than a
  width threshold, which was that decision's own argument.
- **`kebabOnly` is permanent where the rest of the rollout scaffolding is not.** Its comment says the
  anchored-type set, `AbstractVSActions.resident` and `VSObjectContainer.isToolbarAnchored` are all deleted
  when the last slice lands, leaving `.viz-modern` as the only gate, while kebab-only survives as a property
  of the selection family. It sits *after* the `resident` guard in `allowedActionsNum()` deliberately: keyed on
  `kebabOnly` alone it would also fire gate-off and strip a floating toolbar users already have.
- **The container is deliberately excluded** — "it is its own slice" in the set's comment. Case 3 (container
  nesting) is still the open question it was; the code has not pre-empted it. Calendar is likewise unstarted.
- **Two findings written into the discussion doc.** (1) Case 2's "below ~56px of width, drop chrome entirely"
  **did not ship and nothing replaced it**: the only rung that removes chrome is the 32px *height* floor in
  `showingActions`, which a 150px-wide, 200px-tall filter never reaches, so a narrow selection list keeps its
  kebab. Flagged as either an intentional drop to record or a gap. (2) The resting kebab collided with the
  selection list's pending-alert icon, and `vs-selection.component.scss` now offsets it under the gate
  (`:host-context(.viz-modern) .pending-alert:not(.left) { right: 48px }`, with ~40px derived for the kebab
  pill) — concrete support for Case 2's "the header is already occupied" argument, and a collision to expect
  once per family.
- **Superseded a paragraph rather than adding to it.** Case 4's follow-on required §06's fit test to measure
  the title's `titleRatio` share for selection assemblies; with kebab-only shipped, only the ~40px pill has to
  fit, so that test now belongs to the container alone. Marked superseded in place, and the handoff's 3c
  rewritten to say so.
- **Also in the diff, already recorded at 01:23Z:** the resident-kebab template split (`kebabResident`,
  `kebabSplit`, `showToolbarContainer`), the `flattenedMoreActions()` dedupe for the no-action-button case,
  and the anchored SCSS (`display:none`-at-rest groups, `margin-left:auto` pill, coarse-pointer target).
  Re-read this pass and unchanged in substance; the touch route (`residentKebab` for max mode, where there is
  no lane to anchor into) is the one part worth re-reading before the container slice.
- **Spec §02 corrected.** Its "beyond charts" card told reviewers three was a *ceiling* the narrow family
  shared (`Math.min(3, allowedActionsNum())`). Now three slices in, that is a chart-and-table rule; the card
  names `kebabOnly` instead.

### Updated in this project — 2026-08-11T01:23Z
- **Switched tracked branch from `epic-74519` to `viz-updates` and synced.** 99 files changed across 48
  commits under `web/projects/portal/src/` since the last-synced commit. Headline: much of what this
  project treated as *decided* or *proposed* has since **shipped as real code** on `viz-updates`, closely
  matching the decisions on file — this sync is mostly confirmation, plus a few new findings to flag.
- **Anchored toolbar rollout: slices 1–2 shipped.** `mini-toolbar.service.ts` now has
  `ANCHORED_ASSEMBLY_TYPES` (`vschart`, `vstable`, `vscrosstab`, `vscalctable`) and
  `isAnchoredAssemblyType()`, explicitly commented as the TEMPORARY rollout boundary pointing at
  `Anchoring beyond charts - discussion.md`. `AbstractVSActions` carries the exact height ladder from the
  handoff (`ACTION_FLOOR=32`, `ACTIONS_MIN=56`, `MAX_TOOLBAR_ACTIONS=3`, `allowedActionsNum()`), the
  title-hidden overlay + 22px sort-control reserve (`VSObjectContainer.rightEdgeReserve`), and the resident
  kebab with `display:none`-at-rest action groups — all gated on `GuiTool.isVizModern()`. `chart-actions.ts`
  also has the menu-actions reachability fix (show-data / open-max-mode / close-max-mode appended to
  `createMenuActions`) and a `stableFirst` toolbar ordering under the gate. Updated `Anchoring beyond
  charts - discussion.md` and `Open items - handoff.md` with sync notes; the remaining rollout (selection
  family, calendar, container, range slider exclusion) is still undecided, as before.
- **Selection vocabulary (items 7, 8, 9, 11) shipped, matching the decision exactly.** `ChartTool.drawRegions()`
  now takes an `areaName` param, defines `CHROME_AREAS`/`isChromeArea()`, and gates stroke-only chrome
  selection on `GuiTool.isVizModern() && isChromeArea(areaName)`. `vs-annotation.component.scss` has the
  decided `2px solid var(--inet-primary-color)` border under `.viz-modern`, plus two bonus bindings (body
  text color, button border) not previously discussed. Updated `Chart overlay surfaces - ticket.md`.
- **New, unticketed: a data-mark-anchored tooltip tail.** `widget/tooltip/` gained `tooltip-tail-placement.ts`
  and SVG chrome (background/border/tail path with a drop-shadow) in `tooltip.component.html/scss`, wired
  from `chart-area.component.ts` via a new `chart-tail-config.ts` (`tailAxisForChartType`) for
  `tooltipStyle === "CARD"` charts with inline SVG. This changes tooltip *positioning*, not just palette —
  outside the "5 mechanical bindings + type ramp" scope the tooltip ticket items describe. Flagged in
  `Chart overlay surfaces - ticket.md`'s sync note; not yet reviewed against this project's decisions.
- **New, unticketed: a shell-owned `_viz-tokens.scss`.** A whole `--inet-viz-*` token contract (density
  matrices for dense/compact/comfortable, modern/dark color scoping, a `.viz-modern`/`.viz-dark` gate
  system) landed at `scss/_viz-tokens.scss`, explicitly scoped to "browser-DOM data surfaces only" and
  reserving chart-color tokens as server-rendered/conceptual-only. Adjacent to but distinct from this
  project's `--inet-font-size-chart-*` proposal in `Chart type sizes - ticket.md` — worth a reconciliation
  pass before that ticket is implemented, since `GuiTool.isVizModern()` (the gate this file and the
  toolbar/selection work both read) now exists as the shared read of `.viz-modern` on `document.body`.
- **Unaffected, confirmed still open:** `vs-chart.component.scss` (Resize Plot sliders, item 13) does not
  appear in the diff — ticket unchanged. `Dead menu icons - ticket.md` and `Outlined chart text -
  ticket.md` not re-verified this pass; nothing in the diff touches their files.
- Noted but out of this project's scope: `vs-slider.component.scss` (the div-based `VSSlider` widget, one
  of the "four forks" flagged as not-ours) was rewritten wholesale to an M3-style slider with its own
  `.viz-modern`/`.viz-dark` neutral palette — independent modernization, not the chart's Resize Plot control.

### Updated in this project — 2026-08-02
- **Rewrote `Open items - handoff.md` as an ordered implementation plan.** It was organised by who could act, which was right while decisions were pending and wrong once they were all made — the dependencies now span five documents and are not derivable without reading all of them. New structure: a dependency diagram, then five numbered phases (shell first, selection as one changeset across two tickets, the ordered toolbar chain, the type-scale pair, and the independent items), plus the four cross-cutting rules and a short "not ours, but real" section for the slider-fork consolidation. Names the two long poles explicitly: the eight-assembly rollout, and the manual pass needed on the selection CSS branch that has never executed in production.
- **Ran the consumer inventory from a local checkout of `epic-74519` @ `c75c3fabdf64`** and wrote it into `Shell surfaces - ticket.md`. Three findings. (1) `tooltip.directive.ts:43` defaults `tooltipCSS` to `"widget__default-tooltip"`, and `wTooltip` has **12 consumers** — table, crosstab, calc table, chart, chart plot/axis/legend, selection list, range slider, gauge, image, text, annotation, hidden annotation — so the default-tooltip items are genuinely product-wide. (2) **`widget__card-tooltip` is applied in exactly one place**, `chart-area.component.ts:369` gated on `tooltipStyle === "CARD"`, so the three-role ramp's only consumer is the chart data point and the risk flagged when it was decided is closed — build it as decided. (3) The shell `$stacking-order` registry is **two entries** (`.fixed-dropdown` 999900, `w-tooltip` 999901), which makes item 12 worse than recorded: the data tips at `POP_UP_BACKGROUND_ZINDEX = 9996` sit two orders of magnitude *below* it, so any dropdown or tooltip covers a data tip, and the `+99998`/`+99999` offsets exist as the workaround. Also inventoried `vs-annotation`'s 11 mount sites (chart, gauge, image, text, line, rectangle, oval, table, crosstab, calc table, vs-object-container) as the reach of item 11's selection-border change.
- **Split the overlay ticket by code ownership** into `Chart overlay surfaces - ticket.md` (chart-owned: `drawRegions` selection, chart `graph/objects/*.scss` literals, Resize Plot sliders, drill and date-comparison tips) and `Shell surfaces - ticket.md` (shared: the three tooltip classes in `scss/internal/_directives.scss` and `widget/tooltip/`, the annotation overlay which renders on tables and selection lists, and the data-tip `$stacking-order` bypass). Reason: one document was asking a chart reviewer to approve product-wide changes. Both carry an explicit cross-dependency — the selection vocabulary is a single decision implemented in `ChartTool.drawRegions()` **and** the annotation border, so those ship together or the product keeps two selection idioms. Shell ticket also carries four generalised rules, derived from the Hide MiniToolbar base-class precedent: inventory consumers first, never scope defensively to the chart, test one instance per consumer rather than per chart type, and land shared changes before chart ones. Flagged the outstanding risk: the card-tooltip ramp was judged on chart data-point content, so a consumer inventory is a precondition of implementing it (not of the decision). That inventory is **not done** — bounded code search could not complete it; it needs a grep in a checkout.
- **Consolidated the drawings into `Chart overlay surfaces - decided visuals.dc.html`** — three sections, all settled, no new proposals: selection (items 7, 8, 11), the card tooltip ramp (item 4), and the Resize Plot sliders (item 13). Rationale recorded in the document: these three decisions are visual and were held only as prose, and a layout described in a table gets implemented two ways. Deliberately excluded the annotation overlays, drill and date-comparison tips, data-tip z-index and resize handles — value swaps with no design content, where drawing adds review weight without information. Also flagged in the scope card that the card tooltip is styled from the shell's `_directives.scss` and used product-wide, so it sits in a chart ticket only because that is where it was found. Selection section draws the axis-selected flood against the stroke-only replacement, data selection keeping its fill on the palette colour, and the annotation border losing its dotted grey. Tooltip section renders both ramps at real sizes. Superseded: the standalone `Resize Plot slider - proposal.dc.html`, now §03 of the combined document.
- **Drew the Resize Plot slider proposal** (originally standalone) after reading `vs-chart.component.scss` in full. Current: a 150×22 box with `1px #ddd` border and 50% white fill, a 2px `#d3d3d3` track and a 13px `#ff8d41` thumb, with the vertical copy rotated −90° so its box intrudes 22px into the plot's left edge; hover floods the entire track orange and the thumb grows 13→18px on hover *and* focus, so pointing at it moves it. Proposed: delete the container, track to `--inet-default-border-color`, 12px thumb in `--inet-primary-color` with a 2px white ring for separation over bars and gridlines, hover darkens the thumb only, focus uses the shipped `--inet-focus-ring-*`, and coarse pointers grow the transparent hit area to `--inet-control-height-touch` rather than the drawn thumb — hit target and visual weight being different problems the current file conflates. Length `min(150px, 60% of plot edge)` with an 80px floor. No new tokens; net smaller than the file it replaces, since half is dead IE11 code and six more rules are the size-change repeats across three engine prefixes. Also noted the engines disagree today for no stated reason (11px Firefox thumb vs 13px WebKit).
- **Answered both remaining code-verification questions (ticket item 13 / handoff item 7).** IE11: `web/package.json` pins Angular `^21.2.15`, and Angular dropped IE11 support in v13, so the `::-ms-track`/`::-ms-thumb`/`::-ms-fill-*` pseudo-elements and four `-ms-high-contrast` blocks in `vs-chart.component.scss` are unreachable — roughly half the file, including the invalid unitless `padding: 20 0 20 0`. Delete; no product policy call needed. Range control — **first answer corrected**: the portal has no *shared* control but four private ones, all div-based, totalling ~40KB of SCSS — `vs-slider` (14.5KB), `binding/widget/slider` (10.5KB), `binding/widget/range-slider` (9.5KB), `vs-range-slider` (4.5KB) — with `vs-slider` and `binding/widget/slider` sharing the `.slider-track`/`.slider-tracked`/`.slider-handle`/`.slider-tick`/`.slider-value` vocabulary almost line for line (viewer and binding-pane forks of one design). `mat-slider` is `em`-only, so Material was never the alternative. The chart's Resize Plot is the portal's **only** native `input[type=range]`. Recommendation: keep it native and retokenize — the div sliders are not keyboard-reachable (`vs-slider` is `role="slider"` with `tabindex="-1"`), so rebuilding the chart control to match would trade a working control for a consistently broken one; fix the `:focus { outline: none }` regression and bind the colours instead. Flagged as new and unowned: four forks of one slider design is its own consolidation ticket.
- **Wrote the shared chart type scale into `Chart type sizes - ticket.md`** (retitled "Chart type scale"), closing the prerequisite that gated the outlined-text conversion and the tooltip ramp. One ramp in two tiers meeting at 11px: interior `--inet-font-size-chart-sm/-base/-title` at 9/10/11 from `GDefaults`' `DEFAULT_SMALL_FONT`/`DEFAULT_TEXT_FONT`/`DEFAULT_TITLE_FONT`, chrome at 11/13/16 (`--inet-feedback-font-size`, `--inet-font-size-base`, `--inet-font-size-lg` to add). Framed explicitly as a freeze — every value is what charts render today, so applying it moves nothing. Derived 9pt → 9px from `java.awt.Font` point units and Batik `SVGGraphics2D`'s 72dpi user space, flagged for one measurement before applying, and noted `viewsheetScale` scales the whole SVG so the tokens name the chart's own coordinate space. Ruled against aliasing `--inet-font-size-chart-title` to `--inet-feedback-font-size` despite both being 11px — they meet by coincidence of value, and the deferred revision must be able to move one without the other. Recorded as deliberately deferred: the chart tier's 9→10→11 has the same 1px-step flaw the tooltip ramp was rebuilt to fix, frozen because revising it re-lays out every chart and cannot ride inside a "nothing moves" conversion.
- **Kebab contents: §02 already settled it — §06 now restates rather than redefines.** A draft making the kebab `menuActions`-only was written and reverted after review caught it contradicting §02, whose cap card already prepends overflowed toolbar actions to the menu groups **for both entry points**, giving kebab and right-click one identical complete list. That already delivers the properties wanted (never empty, same answer either way, whole inventory on touch), so §06 restates it as the reason the lower rungs are safe. The reverted draft would also have unmotivated §02's fifth source edit, which exists only because concatenation double-lists Clear Brushing / View All Data. **Consequence for item 8: not a hard precondition** — under concatenation a suppressed toolbar's actions overflow to both entry points anyway. Still sequenced first, because that reachability is a side effect of overflow arithmetic rather than a structural guarantee, and the absence from `createMenuActions` is a standing right-click bug. Superseded draft note: `MiniMenu` permits either — it renders whatever `AssemblyActionGroup[]` is bound to its `actions` input via `ActionsContextmenuComponent` — so this is a binding choice, not new code. Chosen because an overflow kebab is empty on any assembly with three or fewer actions (breaking the resting kebab as a permanent signal, and breaking first on the 150px selection lists the pattern rolls out to), because on touch the buttons never render so the kebab is the whole interface, and because one card should not answer the same question two ways. Cost accepted: the three pinned actions appear in both places, and item 8 becomes a hard precondition for the entire §06 ladder rather than just its bottom rung. §06 card rewritten with the rule and the corrected precondition; handoff item 8 updated.
- **Earlier in this turn, before the decision: corrected the scope of item 8's block on §06.** Read `mini-menu.component.ts`: `MiniMenu` is generic, rendering whatever `AssemblyActionGroup[]` is bound to its `actions` input through the same `ActionsContextmenuComponent` as the right-click menu. So the anchored kebab can hold **toolbar** overflow, and since `show-data` and max-mode are toolbar actions they stay reachable at the comfortable, compact and kebab-only rungs with no change to `createMenuActions`. Item 8 gates only the bottom rung (below 32px, no chrome, right-click the only route) and the spec's general "right-click is a complete fallback" claim. **Spec gap surfaced:** §06 never says which list the kebab renders — toolbar overflow or `menuActions` — and the code permits either, so the spec owes that decision and it determines how much of the ladder item 8 gates. Handoff item 8 rewritten.
- **Confirmed the range-slider exception, with a correction.** `titleRatio` is **not** unique to `VSRangeSliderModel` — `VSSelectionBaseModel` (list, tree) and `VSSelectionContainerModel` declare it too. But both of those extend `VSCompositeModel` and so carry `titleVisible` alongside it, while `VSRangeSliderModel` extends `VSObjectModel` directly and redeclares `title`/`titleFormat` without picking up `titleVisible`. So the exception holds on `titleVisible`, not `titleRatio`; the ratio split is a family of three. Surfaces new rollout work: a selection assembly's anchored strip would sit in a lane that is only `titleRatio` of a ~150px width, so §06's fit test must measure the title's share rather than the assembly width — the tightest geometry in the rollout, and one the chart pilot never exercises. Discussion Case 4, its open-questions list, and handoff item 11 updated.
- **Decision: card-tooltip type ramp goes to three roles.** Value 16px (`--inet-font-size-lg`, committed as a new token) / label 13px / caption 11px, tier opacities replaced by the `--inet-text-*` ramp, subtitles collapsed into the caption role, and the 20px stack total dropped to 16px with a hairline rule above it. No fourth size — the total getting quieter is the accepted trade. Settles the tooltip half of the shared type ramp, so the outlined-text conversion extends 16/13/11 instead of minting a second scale. Ticket item 4 marked decided (it subsumes item 2), spec §07, handoff and README updated; the handoff's decision queue is now empty.
- **Verified item 9, canvas class coverage — the blocker does not exist.** Every selection-drawing canvas carries `chart-object-canvas` plus a per-area modifier: plot (`chart-plot-area.component.html:116`), reference line (`:108`), all four axes (`chart-axis-area.component.html:58`), legend content/title (`chart-legend-area.component.html:45`), all four axis titles (`chart-title-area.component.html:44`). All five resolve through the single `#objectCanvas` view child on `ChartObjectAreaBase`, which is also the only path `drawSelectedRegions` draws through, so there is no sixth selectable area. One rule on `.chart-object-canvas` reaches everything; item 8 cannot half-apply.
- **Second finding: the CSS override path in `drawRegions` has never executed.** All four per-area SCSS blocks declare only `position: absolute`; nothing anywhere sets `color` or `border-color` on those canvases, so the `getComputedStyle` branch never fires and every selection in the product is currently on the hardcoded `#dc581e`. Makes item 8 additive rather than a behaviour change, but the first rule written is the first execution of that branch — flagged for one manual pass. Also found `.chart-legend__canvas` in `chart-legend-container.component.scss` has no matching element in its template; dead rule.
- **Decision: one selection vocabulary, stroke-only for chrome.** Fill → `--inet-focus-ring-color`, stroke → `--inet-primary-color`; `#dc581e` and the annotation's `border: 2px dotted darkgray` both go. Chrome selections (axes, legend content/title, four axis titles) become stroke-only at 2px; data selections (`plot_area` VOs) keep the fill, because the fill is area-proportional while the signal need is constant. Accepted trade: chrome selection is quieter. Ticket items 7, 8 and 11 marked decided, sequencing note rewritten, spec §07 and the handoff Decided section updated.

Earlier bullets condensed into Sync history on 2026-08-02.

## Sync history

Condensed 2026-08-02. Earlier entries are summarised to one line each; the findings they describe are
all reflected in the current documents, which are the authority. Nothing below needs acting on.

| Date | What that sync established |
| --- | --- |
| 2026-07-30 18:17 | **Switched to `epic-74519` and found it ships a complete `--inet-*` token system**, invalidating most of the spec's proposed token work: spacing, radius, elevation, control heights and a neutral ramp all exist, and the shell palette matches the spec's warm greys exactly. Spec rewritten to bind rather than create; four declarations survive (`--inet-subtle-border-color`, `--inet-control-height-touch`, `--inet-icon-size-sm`, `--inet-icon-size-md`). Re-verified `GDefaults.java` and all four §09 open items on the new branch. Corrected the "text is not text" claim: fonts are author-settable and default to Roboto via a `SreeEnv` property — what remains true is that zero `<text>` elements exist, so CSS cannot reach chart type. |
| 2026-07-30 17:55 | Branch switch bookkeeping — everything read before this point came from `main` and needed re-verification. Done in the entry above. |
| 2026-07-30 17:08 | **Floating legends are a drag result, not a fifth dock position.** `LegendOption.IN_PLACE` holds a raw (x, y) offset with no anchor or gap; edge bands of chart dimension ÷ 10 dock, anything else floats. Spec §05 corrected and given a per-placement gap table. |
| 2026-07-30 15:36 | Bootstrap pinned at ^5.2.3 with @ng-bootstrap 20; `--bs-border-radius` already ships and is themeable, contradicting an earlier "no radius scale" finding. `$spacers` is not customised, so no 12px step. |
| 2026-07-30 03:12 | Spec §04 spacing model added — five nested boxes, only the plot elastic, twelve gap rules. First full read of `_variables.scss` (colour, font-size and family only at that point). |
| 2026-07-30 02:20 | Spec §03 reduced to the single visual change: floating overlay → anchored strip, cap at three, kebab overflow. Third pinned action changed from Edit to Properties. |
| 2026-07-30 01:38–01:56 | Both menu entry points traced; the kebab is not mounted on charts at all. Menu inventory rebuilt from `chart-actions.ts` (10 groups), six gating layers documented, click-target matrix added. |
| 2026-07-31 | **Decided Hide MiniToolbar moves to the menu via `AbstractVSActions.createToolbarActions`, not a chart-only override** — the `groups.splice(0,0,…)` prepend eats a slot under §02's cap of three, and the base class was chosen because the bug is in shared code. Wrote §02's nav-bar card (stays floating, lower right, inset from the plot area) and the §06 toolbar ladder after reading `mini-toolbar.service.ts`: `getActionsWidth` derives ~40px per action from the root font size and **never consults height**. Corrected two earlier claims — the Hide MiniToolbar dismissal is transient (one string, cleared on re-hover), and the mini-toolbar is not rendered at all on touch (`@if (!mobileDevice)` wraps the whole container). Scoped the eight-assembly rollout in `Anchoring beyond charts - discussion.md`: `hasMiniToolbar()` limits toolbars to eight types, `titleVisible` already exists on five models, and title-hidden assemblies overlay rather than reserve a lane so pixel-positioned dashboards do not shift. |
| 2026-07-31 | **Swept every chart overlay surface and split the findings into tickets.** Tooltip: three classes with the same off-scale `border-radius: 1px`, tier opacities instead of the text ramp, `.hidden__annotation-tooltip` duplicating `.widget__default-tooltip` property-for-property. Selection: `ChartTool.drawRegions()` is the single draw path, defaults `rgba(220,88,30,0.3)`/`#dc581e`, both CSS-overridable. Annotations: `border: 2px dotted darkgray` as a third selection idiom. Data tips: `POP_UP_BACKGROUND_ZINDEX = 9996` bypassing the app's `$stacking-order` registry, and two scrims 0.1 apart. Resize sliders: a fourth orange `#ff8d41`, `:focus { outline: none }` with no replacement, dead IE11 code worth half the file. Also moved two items out of the spec entirely — `Dead menu icons - ticket.md` (app-wide, ~24 files; `icon()` is live for toolbar actions only, so deleting it from menu-only actions is safe) and `Outlined chart text - ticket.md` (the switch is Batik's `textAsShapes` at `SVGUtil.java:63`). |
| 2026-07-31 | Added §04's title-truncation clause using the shipped `[tooltipIf]` directive (`scrollWidth > clientWidth` → native `title`), and recorded the resulting tension: data-point tooltips use the styled `wTooltip`, so a native `title` on the chart title puts two tooltip appearances in one card. |
| Earlier | Chart toolbar respecified from source as an overlay above the assembly rather than chrome inside the card; geometry, glyph names and icon SVGs taken from the repo. |

### Superseded screens — `Chart Card Spec v1.dc.html` (deleted 2026-08-02)
Folded into the current spec and removed. Kept for provenance; not part of the current mapping.

| Superseded screen | Repo files |
| --- | --- |
| v1 §01 Anatomy | vsobjects/objects/mini-toolbar/mini-toolbar.component.{html,scss,ts}; assets/ineticons/icon_svg/*.svg |
| v1 §02 Top lane | vsobjects/objects/mini-toolbar/mini-toolbar.component.scss |
| v1 §03 Toolbar | vsobjects/action/chart-actions.ts, action/abstract-vs-actions.ts, objects/mini-toolbar/mini-menu.component.{html,ts}, graph/model/chart-tool.ts, widget/fixed-dropdown/actions-contextmenu.component.{html,scss}, scss/_bootstrap-override.scss, scss/_icons.scss |
| v1 palette research | (server) CategoricalColorFrame — not read; handed to the palette project |

## Screen map
| Project screen | Repo files |
| --- | --- |
| Chart Card Spec v3.dc.html §01 card radius conflict, §09 + §10.3 dark mode, gate mechanism | (server) core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java (`viewsheet.modernVisualization`, `viewsheet.darkMode`, `viewsheet.density`, `normalizeMode`), VSObjectChromeDefaults.java (`CARD_CORNER_RADIUS` 12px, dark colours, `resolveSeededCorner`, `applyDarkForeground`), scss/_viz-tokens.scss (`.viz-modern` / `.viz-dark` scopes), scss/_variables.scss:520-523 (radius scale), app/common/util/gui-tool.ts (`isVizModern`) |
| Seeded value reversibility - ticket.md | (server) core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java (`resolveSeededCorner`), VSCompositeFormat.java (`resolveDefaultTierCorner`, tab exemption), graph/PlotDescriptor.java (`modernCornerSeed`, `getBarCornerRadius`), internal/CalendarVSAssemblyInfo.java:1420, internal/ChartVSAssemblyInfo.java:92-96 |
| Chart Card Spec v3.dc.html §08 GDefaults greys | (server) core/src/main/java/inetsoft/graph/internal/GDefaults.java |
| Chart Card Spec v3.dc.html §05 Legend placement | graph/model/legend-option.ts, graph/objects/chart-legend-container.component.ts, (server) VSChartMoveLegendService.java |
| Chart Card Spec v3.dc.html §04 token binding, §07 token map, §08 shipped tokens | scss/_variables.scss, scss/_bootstrap-override.scss, web/package.json @ epic-74519 |
| Chart Card Spec v3.dc.html §02 Beyond charts | vsobjects/objects/mini-toolbar/mini-toolbar.service.ts (`ANCHORED_ASSEMBLY_TYPES`), vsobjects/action/abstract-vs-actions.ts (`resident`, `kebabOnly`, `allowedActionsNum`), vsobjects/action/selection-{list,tree}-actions.ts, vsobjects/model/base-table-model.ts, vsobjects/model/vs-composite-model.ts, vsobjects/model/vs-compound-model.ts, vsobjects/model/vs-range-slider-model.ts, vsobjects/model/calendar/vs-calendar-model.ts |
| Anchoring beyond charts - discussion.md Cases 2-4 | vsobjects/objects/mini-toolbar/mini-toolbar.service.ts, vsobjects/action/abstract-vs-actions.ts, vsobjects/action/selection-{list,tree}-actions.ts, vsobjects/objects/selection/vs-selection.component.scss, vsobjects/objects/mini-toolbar/mini-toolbar.component.{html,scss,ts}, vsobjects/objects/vs-object-container.component.{html,ts} |
| Chart Card Spec v3.dc.html §07 Adjacent surfaces | graph/objects/chart-area.component.html, graph/objects/chart-area.component.ts, graph/objects/chart-object-area-base.ts, graph/model/chart-tool.ts, scss/internal/_directives.scss, widget/tooltip/tooltip.component.scss, scss/_variables.scss |
| Chart overlay surfaces - ticket.md items 7-9, 11 (selection) | graph/model/chart-tool.ts (`drawRegions`), graph/objects/chart-object-area-base.ts, graph/objects/chart-{plot,axis,legend,title}-area.component.html + .scss, graph/objects/chart-legend-container.component.{html,scss}, vsobjects/objects/annotation/vs-annotation.component.scss |
| Chart Card Spec v3.dc.html §04 Title truncation | widget/tooltip/tooltip-if.directive.ts, widget/tooltip/tooltip.directive.ts, vsobjects/objects/mini-toolbar/mini-toolbar.component.html |
| Chart Card Spec v3.dc.html §09 Text is outlined | (server) report/StyleFont.java, graph/internal/GDefaults.java |
| Chart overlay surfaces - decided visuals.dc.html §01-02 | graph/model/chart-tool.ts (`drawRegions`), vsobjects/objects/annotation/vs-annotation.component.scss, scss/internal/_directives.scss, scss/_variables.scss |
| Chart overlay surfaces - decided visuals.dc.html §03 | vsobjects/objects/chart/vs-chart.component.scss, .html |
| Chart type sizes - ticket.md (the named scale) | (server) graph/internal/GDefaults.java, report/StyleFont.java, utils/inetsoft-xml-formats/.../graphics/SVGUtil.java, scss/_variables.scss |
| Chart Card Spec v3.dc.html §09 Dead icons | vsobjects/objects/mini-toolbar/mini-toolbar.component.html, widget/fixed-dropdown/actions-contextmenu.component.html, vsobjects/action/*-actions.ts |
| Chart Card Spec v3.dc.html §02 resting kebab / touch, §04 lane overlay, §10.1 | vsobjects/objects/mini-toolbar/mini-toolbar.component.html (`@if (!mobileDevice)` wraps the action-button groups; kebab is inside the container), .scss (`.mini-toolbar` position:absolute, `.mini-toolbar--anchored` display-at-rest rules, `@media (pointer: coarse)` touch target), .ts (`mobileDevice = GuiTool.isMobileDevice()`), mini-toolbar.service.ts (`ANCHORED_ASSEMBLY_TYPES`, `getActionsWidth`) |
| Chart Card Spec v3.dc.html §04 Bootstrap scales | web/package.json, scss/_bootstrap-override.scss, scss/_variables.scss, scss/_imports.scss |
