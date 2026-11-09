# Modernize dialog — keep my formatting, or reset a modern copy

**Date:** 2026-11-04
**Feature:** #80398
**Status:** implemented
**Base:** `epic-74519` (cited lines are at `c81751a3cb`)
**Scope:** the composer, dashboards only. The viewer, worksheets and the deploy/shell paths are out.

## 1. Why

Modernize stamps a mark on every unmarked assembly and re-seeds its chrome through
`seedChromeDefaults`, which writes the DEFAULT tier only. Anything in the USER tier wins. On a
legacy dashboard that carries a lot of formatting, Modernize therefore changes little, and people
conclude the modern look is close to the classic one.

A census of real legacy dashboards (appendix A) found two different causes:

1. **Real author choices.** On component formats (text, titles, table cells, object borders and
   backgrounds), 24% of customer format slots and 52% of InetSoft gallery slots carry an author
   colour, font or border, and at most 10% of those equal the default stored beside them. Only a
   reset that overwrites author formatting can change these.
2. **Residue.** Many author-tier values are copies of the classic default and express no choice.
   70% of the author-set chart text fonts in customer files equal the stored default, 55% of the
   chart text colours do, and 35 of 43 customer charts carry a gridline colour in the USER tier
   (34 of the 35 are `#eeeeee`, `#d2d2d2` or `#ebebeb`). A bare `CompositeValue` string parses as
   USER (`uql/CompositeValue.java` `parse`, the non-`^^DEF^^` branch), and Modernize seeds the
   gridline into DEFAULT only (`ChartVSAssemblyInfo.java:158`), so those charts keep classic
   gridlines after Modernize.

This feature addresses both: Modernize becomes a dialog with a non-destructive option that also
drops residue, and a destructive option that resets look and feel on a saved copy.

## 2. Goals, non-goals, anti-goals

**Goals**

- An author can see what their dashboard looks like fully modern without risking the original.
- Today's Modernize keeps its guarantee (author choices survive, Revert brings the classic look
  back) while changing more, because residue no longer blocks it.
- The author is told when a script on the dashboard can override what the reset did.

**Non-goals**

- Changing the org stylesheet (`format.css`) or an assembly's CSS class/ID. Both outrank DEFAULT,
  so in orgs that use them the copy changes less. Not addressed, not mentioned in the UI.
- Rewriting scripts, or detecting every script that sets formatting.
- Bulk or scheduled modernization, and anything in the viewer.
- Moving schedules, bookmarks or links to the copy.

**Anti-goals** (failures even if the feature works)

- The original dashboard changes in any way, on any path including Cancel and failure.
- An existing asset is overwritten by the copy.
- Data bindings, scripts, conditions, highlights, number and date formats, or component positions
  change.
- "Keep my formatting" produces a visible change to the classic look after Revert.
- The script warning fires on most dashboards with scripts (the census found 20 customer
  components with scripts, 2 of which look like they set formatting).

## 3. What the author sees

### 3.1 Entry points

- The viewsheet pane's menu entry is relabelled **"Modernize…"** and opens the dialog. It is
  shown when the org gate is on (`VSDensityDefaults.isModern()`) and the dashboard is not
  deployed, **including when every assembly is already marked**. Today it also requires
  unmarked content (`modernizable = isModern() && hasUnmarked(vs)`,
  `CoreLifecycleService.java:314`); that term moves to the "Keep my formatting" option.
- The Modernize bar keeps today's visibility rule (`modernizeOffered` in
  `viewsheet-pane.component.ts`: modernizable, not deployed, not dismissed, not in layout mode).
  Its button opens the same dialog instead of modernizing in one click.
- With the gate off, neither entry is shown: the org has chosen the classic look, and
  `VizModernizeUtil.modernize` is already a no-op there (`VizMark.fromGate()` gives no mark).
- Revert stays its own menu entry with today's visibility (`revertable`).
- The **Options pane switch** ("Modern Visualization" in the dashboard properties dialog,
  `viewsheet-options-pane.component.html`) stays a plain switch and offers no reset. Turning it on
  behaves like "Keep my formatting", including residue cleanup (§4). It is a setting saved by the
  same OK as description, alias, max rows and the rest (`ViewsheetPropertyDialogService`), so
  folding a copy-and-switch-tabs action into it would leave unclear which dashboard those other
  settings land on.
  - When the author turns the switch on, the dashboard has unmarked content and the org gate is
    on, one line appears under the switch: "This keeps your formatting. To reset it, use
    Modernize Dashboard… to create a modern copy."
  - With the org gate off the switch still works, as today, but the line is not shown, because
    Modernize… is not offered there. A gate-off org therefore has no reset copy; it has chosen
    the classic look.

### 3.2 The dialog

Two options, as radio buttons:

1. **Keep my formatting** — selected by default. Text: today's Modernize, reversible with Revert,
   touches only components still on the classic look, keeps every author value.
   - Disabled, with the line "Everything already uses the modern look", when the dashboard has no
     unmarked content. The reset is then preselected.
2. **Reset all look and feel to modern defaults** — overwrites author formatting, always on a
   saved copy.

When option 2 is selected the dialog shows, under it:

- A **Save as** field prefilled with the first free name in the original's folder:
  `<name> (modern)`, then `<name> (modern 2)`, `<name> (modern 3)` …
- An inline error when the typed name already exists ("A dashboard with this name already
  exists"). There is no overwrite choice. The button stays disabled until the name is free.
- An inline error when the author cannot create a dashboard in that folder.
- One line: "Bookmarks, schedules and links stay with the original."
- When the original has unsaved changes: "Your unsaved changes go into the copy. The original
  keeps its last saved version."
- When the dashboard embeds other dashboards: "Embedded dashboards are not changed."
- The script line from §7, when it applies.

The button reads **"Modernize"** for option 1 and **"Create modern copy"** for option 2.

When the dashboard has never been saved (temporary scope), option 2 is disabled with the line
"Save the dashboard first."

### 3.3 After "Keep my formatting"

Exactly today's result (one undo step, the bar and menu recompute), plus residue cleanup (§4).

### 3.4 After "Create modern copy"

- The composer tab is now the copy, as after any Save As (`rvs.setEntry(entry)`,
  `SaveViewsheetDialogService.java:307`). The original is not reopened.
- A notification says the copy was created and names it. When §7 applies it repeats the script
  warning and names the components.
- On the copy, Revert's confirm text names the original as the way back (§8).

## 4. "Keep my formatting" — residue cleanup

**Rule.** When an assembly moves from unmarked to a modern mark, before it is seeded, every
author-tier value of a property on the reset allowlist (§5.1) that **equals what that property
would render as under the classic look with the author's value removed** is cleared. The modern
seed then writes DEFAULT as today.

The guarantee decides the comparison, not the stored DEFAULT tier. The two differ in three ways,
and the comparison must follow the render in each:

- **CSS tier.** `VSCompositeFormat` resolves USER → CSS → DEFAULT. Where `format.css` or an
  assembly class/ID defines the property, the classic value is the CSS value, so a USER value that
  equals DEFAULT but overrides a different CSS value is *not* residue.
- **Seeded properties.** Revert re-seeds the legacy branch with today's legacy values (e.g. a
  table title's foreground and background in `TableDataVSAssemblyInfo.seedChromeDefaults`, the
  gridline through `ChartLineColor.getPlotLineColor`). The classic value is that freshly seeded
  value, not the DEFAULT an older release stored in the file.
- **Chart text.** Axis, legend and title DEFAULT tiers are re-initialised at render
  (`VGraphPair` → `*Descriptor.initDefaultFormat(ctx)`), and the font depends on the context. The
  classic value is the one the render's context produces.

Recommended mechanism: simulate Revert on a clone — clear the property's USER value, seed with the
legacy context, initialise chart text defaults as the render does, resolve — and compare the
result with the author's value.

- For a categorical palette entry, the classic value is the classic palette colour at that index.
  For a static colour frame, its default colour. For padding, the classic default padding, or the
  `format.css` padding where one applies (then `userPadding` is cleared too).
- Strict equality only. A value that equals an *older* release's default but not today's (the
  census's `#d2d2d2` and `#ebebeb` gridlines, about half of the customer charts) is not residue,
  and those charts keep classic gridlines after "Keep my formatting". The reset covers them. The
  owner chose this over an exception for known older defaults, which would have broken the Revert
  guarantee and rests on an untraced guess that those greys were defaults (§11).
- The census (appendix A) compared against the stored DEFAULT, so its residue shares are an upper
  bound; in orgs that use `format.css` this rule clears less.
- Expression- or variable-bound values (`=…`, `$(…)`) are never residue.
- Table, crosstab and calc cell paths (types HEADER, DETAIL, TRAILER, GROUP_HEADER, SUMMARY,
  SUMMARY_HEADER and crosstab HIDDEN_SUMMARY) are never residue: their classic value is built at
  render from the table style and data (`DataVSAQuery`, `VSFormatTableLens.createCellFormat`), so it
  cannot be computed here. The reset still clears them.
- Titles, cells and chart text inherit an author's object-level value, so that value is residue
  only if clearing it also leaves every other property resolving as before: an object font equal
  to the classic one is kept while it holds a table title off its bold default.
- Render code that branches on whether an author value is set, not on its value, is outside the
  classic simulation, so those properties are never residue: a table, crosstab or calc table's
  object font, transparency and border colours (cells defer to the table style without them); the
  font and colour of chart data labels, target labels and text-field labels (a set colour turns off
  the automatic contrast colour, and only set values build text-layout specs); and a range
  slider's padding (an unmarked slider draws only an author's).
- Properties off the allowlist (alignment, wrapping, number and date formats, presenters, spans,
  shape and line settings) are never cleared.

**Where it runs.** Every unmarked→marked transition: Modernize, and the Options pane's
per-dashboard switch when it moves an unmarked assembly to a modern mark
(`ViewsheetPropertyDialogService.java:354` → `VizModernizeUtil.applyMark`). Not on Revert, not on
a light↔dark flip of a marked assembly, not on a density re-seed, not on state or bookmark
restore (`reseedAfterRestore`).

**Why it is safe.** A cleared value equalled the classic default, so after Revert the assembly
renders exactly as before. It is the same undo step as the rest of Modernize.

**Invariant change.** The P6 property "Modernize then Revert matches a dashboard never
modernized" (`chart-card-roadmap.md`, P6; `VizModernizeUtilTest`
`aModernizedThenRevertedSheetMatchesOneNeverModernized`) becomes **renders identically**, not
byte-identical: the reverted asset no longer carries the residue in its USER tier. The roadmap's
P6 text is amended when this ships.

**Known limit.** A bookmark taken before Modernize still carries the residue in its state, and
restoring it puts the classic value back in the USER tier of a marked assembly. That is today's
behaviour for author values; it is not fixed here.

## 5. "Reset" — what is cleared and what is kept

### 5.1 The allowlist

"Modern defaults" means **what a new assembly of the same type gets today under the modern mark**.
For each property below the reset therefore does three things on every assembly of the copy and
on the copy's sheet:

1. clears the **author tier**;
2. refreshes the **DEFAULT tier** from today's creation defaults for the type;
3. stamps and seeds (§5.2).

Step 2 is needed because clearing USER alone exposes the DEFAULT tier stored in the file, and an
old file holds the creation defaults of the release that saved it (`VSCompositeFormat` reads
`<defaultFormat>` back). Fonts and most cell colours are written by no seed: fonts do not depend
on the mark at all (`AxisDescriptor.initDefaultFormat`: "font follows 'is a viewsheet chart', not
the modern gate"). Today's creation defaults are what `initDefaultFormat` / `setDefaultFormat` give
a new assembly of the type (26 overrides under `uql/viewsheet/internal`). Note that
`setDefaultFormat` builds new `VSCompositeFormat` objects (`VSAssemblyInfo.java:1209`), so step 2
must copy DEFAULT tiers only — for example from a freshly initialised instance of the type — and
never replace a format wholesale, which would take the kept properties with it. Step 2 runs on the
reset only, never on "Keep my formatting" or Revert.

Anything not listed below is kept. A property missed from the list therefore errs toward keeping
the author's value.

Principle for the builder: the allowlist is the set of look properties `seedChromeDefaults` (or
the render-time CSS-tier writes that stand in for it, such as `CSSChartStyles.apply`'s gridline)
writes for that type, plus the extras named below. A number format or a size is never on it, even
if some seed writes one.

| Family | Cleared | Kept |
|---|---|---|
| Component formats (`VSCompositeFormat`: object, title, every table/crosstab/calc cell data path, selection cells, calendar, input labels, the sheet) | foreground, background (incl. gradient), font (see the font rule below), borders, border colours, transparency, round corner | alignment, wrapping, number/date format and extent, presenter, span, any expression-bound value |
| Chart text (`CompositeTextFormat`: axis labels and titles, legend title and items, chart title, data labels, target labels) | font (see the font rule below), colour, background, transparency | alignment, rotation |
| Chart plot (`PlotDescriptor`) | gridline, facet, diagonal, quadrant, trend-line, border and background colours; gridline styles | everything else (trend line type, stacking, values visible, …) |
| Chart legends (`LegendsDescriptor`, legend descriptors) | border, border colour, round corners, gap/padding, position (back to the default a new chart gets, `RIGHT`), preferred size and each legend's size and position (none, as on a new chart; §11.3) | visibility, title text |
| Chart axes | line colour and any axis spacing the modern seed writes | visibility, scale, label text, format |
| Chart shape defaults the mark seeds | bar corner radius (`userBarCornerRadius`), smooth lines (`userSmoothLines`) | — |
| Colour frames | categorical author palette (`userColors`) and per-value colours (`dimensionColors`); static author colour (`userColor`); a linear ramp the author picked is replaced by the mark's seeded ramp | which field is bound to colour; shape, texture, line and size frames |
| Assembly | padding (`userPadding`), table style | title text, title visibility, title height the author set |
| Highlights / conditional formatting | — | all kept |

**Font rule.** The request names "fonts and sizes of titles, axes and legends", so fonts split in
two:

- **Titles, axes and legends** — object titles, chart titles, axis labels and titles, legend
  titles and items: the whole font resets (family, size and style).
- **Everything else** — text components, table/crosstab/calc cells, selection items, calendar
  cells, form controls, chart data and target labels: the family resets to today's default for
  the type, and the author's **size and style** (bold, italic and the other `StyleFont` style
  flags) are kept. Text components are 45% of the census's components and are often headings and
  KPI values; resetting their size would flatten the dashboard's type hierarchy.

A font the author never set takes today's default whole in both cases.

### 5.2 Marks

Every assembly and the sheet itself is stamped and seeded, including assemblies already marked.
The mark is the dashboard's own when it has one (a dashboard switched to dark in Options stays
dark); otherwise `VizMark.fromGate()`, the mark new content gets.

### 5.3 Sizes and positions

Positions and author-set sizes (including an author title height, `userTitleHeight`) are kept.
What Modernize already re-seeds — title lanes and sizes the author never set, contained sliders,
bottom-tabs children (`moveMark`) — happens on the reset too, because it goes through the same
path.

### 5.4 Left alone

- Embedded dashboards: the copy references the same embedded asset, and changing it would change
  something other than the copy. Any host-side overrides of embedded assemblies are also left
  (builder to confirm what the host stores, §10).
- Shapes (rectangle, oval, line) and images: decorative content, not component chrome, and the
  modern seed does not style them. Legacy dashboards use rectangles as section panels; resetting
  their fill would flatten layouts.
- Device and print layouts.
- `format.css` and assembly CSS class/ID (§2).

## 6. The copy

**Sequence** (one server request, after the dialog is confirmed):

1. Re-validate the name and folder permission (the dialog's check can be stale).
2. Save the current runtime under the new name through the existing Save As path
   (`SaveViewsheetDialogService.saveViewsheet`), with overwrite refused and dependency rename off
   (`isUpdateDepend` false). The runtime now points at the copy; the original asset is unchanged
   on disk at its last saved version.
3. Run the reset on the runtime (§5).
4. Record the provenance on the copy (below) and save again.
5. Refresh the composer as Modernize does, then send the notification.

**Failure.** If step 3 or 4 fails, delete the copy, point the runtime back at the original entry,
restore the pre-reset state, and report the error. If step 2 fails nothing exists to clean up.

**Cancel** before confirming changes nothing. There is no cancel once the request is sent.

**Unsaved changes** on the original go into the copy (step 2 saves the runtime as it is). The
original keeps its last saved version. The original's own autosave is not left behind as a newer
version of the original (builder to verify, §10).

**Folder** is the original's. No folder picker in this version.

**Provenance.** The copy stores the original's asset path (a new `ViewsheetInfo` property). It is
informational: if the original is later renamed or deleted, Revert's text falls back to the
generic wording.

**Undo.** The reset is one undo step on the copy, like Modernize. The copy's save point is after
step 4, so undoing marks the copy dirty and leaves the original untouched.

## 7. Script warning

**What counts:** a component counts when formatting on it can be set by something the reset does
not change:

- an assignment to a look property in any script on the dashboard — the component's own script,
  another component's script, or the dashboard's `onInit`/`onLoad` — attributed to the component
  it targets; or
- an expression- or variable-bound value on an allowlisted property (§5.1), which the reset keeps.

Detection (`ScriptFormattingScanner`) uses a small lexer of its own, not `ScriptIterator`. The
lexer drops comments and strings, so text inside them never counts, and it reads `['name']` as
`.name`. On the resulting tokens, the scanner finds plain assignments (`=`, not `==` or `+=`) and
setter calls whose property is on a fixed list of look-property names: foreground, background,
font, borders, borderColors, alpha, roundCorner, titleFont, titleColor, any name ending in
`Color`, `Colors` or `Font`, and the setters `setForeground`, `setBackground`, `setFont`,
`setColor` and `setColors`. A script that only reads properties does not count. A full parse is
not required; a missed case is acceptable, a warning on every script is not.

Attribution:

- **In a component's own script**, a write counts for the component itself when it names no
  target, names `this`, or starts with a name that is not an assembly. The last case covers the
  script's own scope, such as a chart's `xAxis.labelColor` or `axis['State'].font`. A write that
  starts with an assembly's name counts for that assembly when it is the dashboard's own.
- **In `onInit` or `onLoad`**, a write that starts with an own assembly's name counts for that
  assembly. Any other write sets the dashboard-script flag.

**Wording:**

- Dialog, under option 2: "Scripts set formatting on N components. The reset does not change
  scripts, so a script that sets formatting can still override the new defaults." When a
  dashboard script sets formatting but its target cannot be resolved, append "…, and the
  dashboard script sets formatting."
- Notification after the copy is created: the same sentence, followed by the component names.
- Nothing is shown when nothing counts. The warning never blocks and adds no confirmation.

**Cost:** computed when the dialog opens, from the runtime sheet. Scripts are short; the largest
dashboard in the census has 175 components.

## 8. Revert

- Unchanged in behaviour and visibility.
- On a copy with provenance: the confirm text adds that the reset copy's earlier formatting is
  gone, so Revert gives the classic defaults, and that the original dashboard (named) is the way
  back. Without provenance, today's text (`composer.vs.revert.confirm`).

## 9. Decisions

"Owner" = decided by the feature owner in the 2026-11-04 brainstorm (they accepted the proposed
recommendations wholesale and added residue cleanup). "Design" = settled in writing this document.

| # | Decision | Who |
|---|---|---|
| 1 | Reset = allowlist of author-tier properties, then the normal modern seed | Owner |
| 2 | Keep alignment and wrapping; reset transparency with background; reset table cell formats | Owner |
| 3 | Reset author chart colours, palettes and per-value colours | Owner |
| 4 | Keep highlights; "conditions" means data conditions | Owner |
| 5 | Reset table style | Owner |
| 6 | Keep expression-bound formatting; count it in the warning | Owner |
| 7 | Keep positions and author sizes; allow what Modernize already re-seeds | Owner |
| 8 | Keep a per-dashboard dark/light mark; else the gate's mark | Owner |
| 9 | `format.css` and CSS class/ID out of scope | Owner |
| 10 | Embedded dashboards untouched; dialog line only when present | Owner |
| 11 | Reuse Save As; composer ends on the copy | Owner |
| 12 | Same folder, name field only, inline permission error | Owner |
| 13 | Prefill first free `(modern N)` name; no overwrite | Owner |
| 14 | Unsaved changes go into the copy, with a dialog line | Owner |
| 15 | Never-saved dashboard: option 2 disabled | Owner |
| 16 | Failure after the copy is saved deletes it | Owner |
| 17 | Copy stores the original's path for Revert's text | Owner |
| 18 | Bookmarks/schedules/links line in the dialog | Owner |
| 19 | "Modernize…" menu entry whenever the gate is on; "Keep" disabled on a fully marked dashboard | Owner |
| 20 | Bar keeps its visibility rule and opens the dialog | Owner |
| 21 | Script warning from a tokenizer scan of look-property assignments | Owner |
| 22 | Dashboard scripts attributed to the components they target | Owner |
| 23 | "Keep my formatting" drops author values equal to the classic default | Owner |
| 24 | Residue rule is strict; older-release gridline greys are not residue | Owner (§11.1) |
| 25 | Residue cleanup also runs on the Options per-dashboard switch, on unmarked→marked only | Design |
| 25a | The Options switch offers no reset; it shows a hint pointing to Modernize… when the gate is on and the dashboard has unmarked content, and no hint with the gate off | Owner |
| 26 | Residue cleanup uses the reset's allowlist | Design |
| 27 | Shapes, images and layouts are left alone by the reset | Design |
| 28 | Legend position resets to `RIGHT` and legend sizes to a new chart's (§11.3); bar corner radius and smooth lines are on the allowlist | Design, Owner |
| 29 | Provenance falls back to generic Revert text when the original no longer resolves | Design |
| 30 | The reset is one undo step on the copy | Design |
| 31 | Text components and table cells keep the author's font size and style; family resets | Owner (§11.2) |
| 31a | The same keep-size-and-style rule extends to all other non-title text (selection items, calendar, form controls, chart data and target labels); titles, axes and legends reset the whole font | Design |
| 32 | "Modern defaults" = today's creation defaults for the type plus the modern seed, so the reset refreshes DEFAULT tiers | Request's intent ("people do not see the modern look") |
| 33 | Residue compares against what renders under classic with the author value removed (CSS tier, legacy seed, render-context chart text), not the stored DEFAULT | Owner's premise for residue cleanup (Revert returns the same look) |
| 34 | No residue cleanup path for an already fully marked dashboard ("Keep" stays disabled); Revert then Keep, or the reset, reaches it. Few exist: the epic is unreleased | Design |

## 10. Left to the builder

- The exact enumeration of each type's allowlist from its `seedChromeDefaults` override and the
  render-time CSS-tier writers, and where the clearing lives. Recommended: a per-type hook beside
  `seedChromeDefaults` (e.g. `clearAuthorChrome(boolean residueOnly)`), so a property added to the
  seed is added to the reset in the same type — the single-hook principle `VizModernizeUtil`'s
  class comment states for Modernize and Revert.
- How to obtain today's creation DEFAULT tiers per type for §5.1 step 2 (a fresh instance's
  `initDefaultFormat`, or factoring the default-tier writes out of `setDefaultFormat`), including
  table/crosstab/calc cell data paths and chart descriptors.
- How to compute the classic value for §4's comparison (the clone simulation, or an equivalent that
  reaches the same CSS, legacy-seed and render-context results).
- What a host dashboard stores for its embedded assemblies, and confirming the reset leaves it.
- Whether Save As copies bookmarks or leaves an autosave of the original behind, and closing either
  if it does.
- The dialog component, i18n keys (`srinter.properties`, next to `composer.vs.modernize.*`), the
  STOMP event and service shape. The reset request should go through a new endpoint, not by
  chaining the existing Save As and Modernize events from the client, so step order and failure
  cleanup are server-side.
- Whether the dialog's script count is computed by a separate request or carried on the
  viewsheet info.
- How to keep the menu entry reachable in layout mode (today's bar is hidden there).

## 11. Owner rulings after review (2026-11-04)

1. **Older-release gridline defaults — keep the strict rule.** `#d2d2d2` and `#ebebeb` gridlines
   are not residue; "Keep my formatting" leaves them, the reset replaces them.
2. **Font size on text components and table cells — keep the author's size and style**, reset
   the family (§5.1, font rule).

Rulings after the scripted manual checks (2026-11-05):

3. **Legend size resets with its position.** The reset moves the legend to `RIGHT` and gives it
   the size a new chart gets: no legends size, and no size or position on any single legend. A
   size kept from a bottom legend took half the chart's width once the legend moved right.
4. **The copy carries no author chart fonts on titles, axes or legends.** The reset also covers
   the runtime copies of the chart's fields that a loaded sheet keeps (its runtime group fields
   are stored in the file) and each measure's own title, not only the design fields.
5. **A group container used as a panel resets like a component.** The copy clears its author
   fill and border; its members keep their own look. Accepted: the author can restyle the panel.
6. **"Keep my formatting" adopts the current modern card.** Classic components without an author
   background end with the card a new assembly gets (after PR #7750, the translucent tint).
7. **Modernize Dashboard... is reachable in device and print layouts.** The layout pane's menu
   offers the same entry, with the same visibility; the bar stays hidden there. Revert Dashboard
   to Classic Look is offered there too, for the same reason. The canvas hands both of its
   entries to the layout pane, so the two menus cannot drift apart.

## 12. Verification

**Automated (core):**

- Residue: a legacy sheet with author values equal to and different from the classic default;
  after Modernize only the equal ones are gone; after Revert every resolved value equals a
  never-modernized sheet's. Covers `VSCompositeFormat`, `CompositeTextFormat`, a plot gridline
  `CompositeValue`, a categorical palette entry and a static colour.
- Residue never clears an expression-bound value or an off-allowlist property.
- Residue with a CSS-tier fixture: a USER value equal to DEFAULT that overrides a different CSS
  value is kept. With an old-DEFAULT fixture: a USER value equal to the file's stored DEFAULT but
  not to today's legacy seed is kept.
- Reset: every allowlisted property resolves to what a new assembly of the type gets under the
  modern mark, on a marked and an unmarked assembly, including on a fixture whose stored DEFAULT
  tier comes from an older release; number formats, alignment, highlights, bindings, scripts,
  positions and author sizes are unchanged.
- Font rule: an axis label and a component title with an author font take today's default font
  whole; a text component and a table cell with an author font keep its size and style and take
  today's default family.
- Copy: the original asset's stored XML is byte-identical before and after, on success, on a
  forced failure in each step, and with unsaved changes in the runtime.
- Name: an existing name is refused, never overwritten; the prefill skips taken names.
- Script scan: assignments and setter calls on look properties count; reads do not; dashboard
  scripts are attributed by target name.

**Manual (composer):**

- Both entry points open the dialog; deployed, gate-off and fully marked behaviour.
- Options pane: turning "Modern Visualization" on shows the hint with the gate on and not with
  it off; OK modernizes with residue cleanup and creates no copy.
- Census (`Examples/Census`) and a customer-like dashboard: Keep shows modern gridlines and chart
  text where residue was; Reset produces a fully modern copy and the original is unchanged.
- Revert on the copy shows the provenance text; Revert after Keep looks identical to the original.
- Export of the copy (PDF, PNG, Excel) matches the composer.

## Appendix A. Census

**Method.** A script read the stored viewsheet XML and, for every format tier the file holds,
counted where the USER tier defines a property (`VSFormat` `hint` bits, `TextFormat`
`*Defined` flags, a non-empty bare `CompositeValue` attribute, colour-frame `userColors` /
`userColor` / `dimensionColors`) and whether that value equals the DEFAULT stored beside it. It
counts what files store, not what renders, and ignores `format.css`. Its "equal to default" shares
are therefore an upper bound on what §4's rule clears. "Looks like it sets formatting" is a regex
over script text.

**Corpora.** Customers: 47 dashboards, 354 components (Escbhach 23, Worthington 21, UTS 3 unique).
InetSoft: 191 dashboards, 3,051 components (the 13.x gallery repository and `Examples/Census`). No
assembly was already marked.

| | Customers | InetSoft |
|---|---|---|
| Component format slots with an author colour/font/border/alpha/corner | 427 of 1,797 (24%) | 10,418 of 20,200 (52%) |
| …foreground / background / font / border equal to stored default | 3% / 11% / 7% / 2% | 17% / 17% / 8% / 1% |
| Transparency / wrapping equal to stored default | 100% / 85% | 85% / 57% |
| Chart text font / colour author-set, share equal to default | 718 (70%) / 768 (55%) | 5,754 (57%) / 3,705 (41%) |
| Charts with a USER-tier gridline colour | 35 of 43 | 423 of 480 |
| …of those `#eeeeee` / `#d2d2d2` / `#ebebeb` | 15 / 15 / 4 | 282 / 134 / 3 |
| Categorical palette entries author-set, share equal to default | 414 (9%) | 3,487 (4%) |
| Static colours author-set, equal to default | 45, 0 | 497, 33 |
| Tables/crosstabs with a table style | 25 | 176 |
| Components with a script / looking like they set formatting | 20 / 2 | 196 / 31 |
| Expression-bound formatting values | 0 | 15 |
| `userPadding` set | 0 | 0 |
