# Calendar — a density default size

**Date:** 2026-10-06
**Branch:** community `feature-calendar-density-size`, cut from `epic-74519` @ `aee985d164`
**Verified against:** `aee985d164` for everything on `epic-74519`, and `feature-selection-container-density`
@ `ae84d2821e` (PR #6390) for the size machinery that has not merged yet. Each citation names the
commit it was checked against when that is not `aee985d164`.
**Depends on:** PR #6390. D5 and D6 use its `userSize` flag, the `takesDensitySize` /
`followsDensitySize` / `defaultSize` / `resetSize` methods and the three `VSDialogService` size helpers.
Implementation starts after #6390 merges into `epic-74519` and this branch is rebased onto it.
**Follows:** [the density padding design](./2026-09-23-density-padding-design.md), [the selection family
padding design](./2026-10-01-selection-family-padding-design.md) and [the selection container density
design](./2026-10-05-selection-container-density-design.md) D7, which this design reuses for the author's size.
**Type in scope:** the calendar (`CalendarVSAssembly`), both show types and both view modes.

## 1. Why

Density changes the calendar only through its title lane. Before choosing what else density should
change, the review found that the two obvious candidates do not apply:

- **There is no row height to pad.** Day cells stretch to fill the box (§2.1). The space between two
  numerals is the box size divided by seven, in both the browser and the export. Cell padding would
  only move a numeral around inside a cell that is already about 43×34px at the default size.
- **A card inset costs a grid that cannot give anything up.** A month always shows 7×7 cells, so dense
  cannot fit more of them. It can only shrink them. The 36px month band already gives the grid a top
  margin.

The review also found that density runs **backwards** on the calendar today. At the 300×300 default,
the shorter title lane hands its height to the grid, so the densest tier has the tallest rows:

| | legacy | comfortable | compact | dense |
|---|---|---|---|---|
| title lane | 36 | 30 | 26 | 20 |
| row height `(300 − lane − 36) / 7` | 32.6 | 33.4 | 34.0 | 34.9 |

So density acts on the calendar through its default box size, and the box is sized so that the rows
step with the tier.

## 2. What exists today

### 2.1 Rows fill the box, in the browser and the export

- **Browser.** A full calendar's month or year view is `objectFormat.height − titleFormat.height` tall
  (`month-calendar.component.ts:267`, `year-calendar.component.ts:208`). Inside it, the 36px month band
  (`.child-calendar-title`, `vs-calendar.component.scss:241`) sits above `.day-table`, which is
  `calc(100% - 36px)` tall (`:168`). The weekday header and six week rows are `flex-grow: 1` cells
  with `padding: 0`, so each takes one seventh of what remains. The year view fills the same space
  with three rows of month cells, so it follows the box the same way.
- **Export.** `VSCalendar.paintMonthCalendar` divides the height into eight equal rows, the month title
  being one of them (`VSCalendar.java:657`, `int rH = h / 8`), and fills each cell.
- The two layouts already differ (a 36px band against an eighth of the height). That predates this
  work and is unchanged by it. Both fill the same stored box, so changing the box changes both.

### 2.2 The default size

- A new calendar is 300×300 (`CalendarVSAssemblyInfo.java:88`, raised from 300×200 in #3030).
- Besides an author's resize, two things change the stored width:
  - The calendar's **Toggle Double Calendar** action, in the composer and the viewer, doubles or
    halves it (`VSCalendarService.java:136-158`). It is not an author resize: it does not go through
    `ComposerObjectService.resizeObject`, so it never sets #6390's `userSize`.
  - `fixCalendarSize()` (`:1515`) does the same, and pins a dropdown to 18px, but only after a script
    sets the show type or view mode at runtime (the `typeSValid` / `modeSValid` flags, set only by
    `CalendarVSAScriptable.java:108`, `:125`).
- **The property dialog's frontend resizes the box when the show type or view mode is switched**
  (`calendar-property-dialog.component.ts:110-120`). It doubles the width for double and halves it
  for single, and sets the height to 20 for a dropdown and 180 for a full calendar. The server stores
  that size as sent, except in the bottom-tabs branch (§5). *Corrected 2026-10-06: this bullet first
  said the dialog left the size alone, which is true of the server only. The Task 4 review found it.*
- `fitCalendarHeightToTitle()` (`:1569`) grows the box only when the title lane is at least the box
  height. No tier size comes near that.
- A **dropdown** ignores its stored height. Closed, it is title-lane tall. Open, its body is a fixed
  `CALENDAR_BODY_HEIGHT` of 144px (`CalendarVSAssemblyInfo.java:78`, `vs-util.ts:591`;
  `vs-calendar.component.ts:198`). Only its stored width is drawn.

### 2.3 Where seeds run

| Path | Calls | Context |
|---|---|---|
| Creation | `initDefaultFormat` → `seedChromeDefaults` (`CalendarVSAssemblyInfo.java:95-100`), after the constructor stamps the host's mark (`AbstractVSAssembly.java:136`; `VSEventUtil.java:2134`) | the new mark |
| Dashboard density change | `VizModernizeUtil.reseed` (`:106`), whose only caller is `ViewsheetPropertyDialogService.java:360` | `VizContext.of(info)` |
| Modernize, Revert, the dashboard's modern/dark switch | `VizModernizeUtil.seedAll` (`:116`) | a transition context |
| Every state and bookmark restore (viewer open, export) | `VizModernizeUtil.reseedAfterRestore` (`:161`), from `AbstractVSAssembly.java:656` | `VizContext.of(info)` |

The density change and the restore build the **same** context, so a rule inside `seedChromeDefaults`
cannot tell them apart. This is why D4 needs its own hook.

`initDefaultFormat` reaches only new calendars. Its one caller that runs on an existing assembly,
`ComposerObjectService.java:771`, handles a child moved out of a selection container, and only
selection lists and range sliders can be dropped into one (`editable-object-container.component.ts:1683-1686`).

The context's density is the top-level dashboard's (`VizContext.densityOf`, `VizContext.java:92`),
which falls back to the org's.

## 3. Decisions

### D1 — No card inset (declined 2026-10-06)

Insetting the card by 16/12/8 would take 32/24/16px out of each axis of a fixed 7×7 grid. The month
band already separates the grid from the title lane, and the weekday header cells are bordered
surfaces of their own. The chart, the table family and the selection family take the inset because
their content can scroll or show fewer rows. The calendar's cannot.

### D2 — No cell padding; density acts through the box

Because rows fill the box (§2.1), padding has nothing to act on. The rows step with the tier only if
the box does. Gaps between cells are out of scope (§8), because they would decide the selection
shape that the widget spec's §07 range treatment has not settled.

### D3 — Row heights 38 / 34 / 30, and compact keeps 300×300

```
calendarSize(ctx) = 300 × (titleHeight + 36 + 7 × rowHeight)
```

| | lane | band | rows | box |
|---|---|---|---|---|
| comfortable | 30 | 36 | 7 × 38 | **300×332** |
| compact | 26 | 36 | 7 × 34 | **300×300** |
| dense | 20 | 36 | 7 × 30 | **300×266** |
| legacy | 36 | 36 | 7 × 32.6 | **300×300** |

- **Compact is anchored on today's default.** The org default tier is compact
  (`defaults.properties:233`, `viewsheet.density=compact`), so a new calendar at the default tier is the
  same 300×300 it is today. This follows the density design's D2 rule that the compact column holds
  what already shipped.
- **The band stays 36 at every tier.** The widget spec keeps the two 32×32 navigation buttons at
  their compact size everywhere, and the band holds them.
- **The width stays 300.** Density has an opinion about vertical rhythm (the title lane and the
  rows), and none about how wide a calendar should be. This matches the selection family, whose
  content width stays at legacy.
- **Dense keeps 30px rows**, above the 24×24 minimum target size for a clickable day.
- Because compact equals legacy, the recognized set has three sizes, not four: 300×300, 300×332 and
  300×266.

### D4 — Reach: creation and a dashboard density change only (decided 2026-10-06)

The size rule runs in exactly two places:

1. **Creation**, from `CalendarVSAssemblyInfo.initDefaultFormat`, after `seedChromeDefaults`.
2. **A dashboard density change**, from `VizModernizeUtil.reseed`.

It does **not** run on a state or bookmark restore, on Modernize, on Revert, or on the dashboard's
modern/dark switch.

**Why.** Calendars that are already modern are not resized on purpose. Only calendars saved after the
modern mark shipped and before this work can be affected, and the feature is not live yet. So
nothing is built to bring them onto the tier size, and the rule stays off the restore path that would
otherwise move them on their next viewer open. This differs from the selection family on purpose:
its size rule runs on every restore (the selection family design's D6 and the container design's D6).

**Mechanism.** `VSAssemblyInfo` gains `protected void seedDensitySize(VizContext ctx)`, empty by
default. It is protected for the same reason `seedChromeDefaults` is: `VizModernizeUtil` is in the
same package. `CalendarVSAssemblyInfo` overrides it:

```java
if(ctx.modern && followsDensitySize()) {
   setPixelSize(VSDensityDefaults.calendarSize(ctx));
}
```

`initDefaultFormat` calls it, and `VizModernizeUtil.reseed` calls it on each target after
`seedAll`. Neither `seedAll` nor `reseedAfterRestore` calls it. The guard has no `ctx.transition`
clause because no transition reaches it. An unmarked calendar has `ctx.modern == false`, so it never
moves.

**Consequences, accepted.**
- **Modernize** leaves a legacy calendar at 300×300. That is already the compact size, and at the
  other tiers the box takes the tier size on the next dashboard density change, or when the author
  ticks the checkbox (D6).
- **Revert** leaves a seeded 300×332 or 300×266 box in place. A legacy calendar draws any box, so
  nothing breaks. Its rows are just a little taller or shorter than a fresh legacy calendar's.
- **An already-modern calendar** keeps its size until its dashboard's density changes.
- **An org-level density change** resizes nothing that already exists. Only `reseed` reaches existing
  calendars, and only the dashboard property dialog calls it. New calendars take the new tier.
- **The composer and the viewer agree.** Both paths that write the size are composer actions, so the
  composer-versus-viewer mismatch recorded for the container (#6390, `ae84d2821e`) does not arise for
  the calendar.

### D5 — An author's size is kept, using #6390's flag

The calendar opts in to the machinery #6390 adds to `VSAssemblyInfo` (at `ae84d2821e`: `isUserSize`
`:1440`, `takesDensitySize` `:1455`, `followsDensitySize` `:1462`, `defaultSize` `:1469`, `resetSize`
`:1476`):

- `takesDensitySize()` returns true.
- `followsDensitySize()` returns `!isUserSize() && VSDensityDefaults.isSeededCalendarSize(getPixelSize())`.
- `defaultSize(ctx)` returns `VSDensityDefaults.calendarSize(ctx)`, so `resetSize` writes the tier size.

**Composer drag-resize needs nothing more.** `ComposerObjectService.resizeObject` already sets the
flag whenever `takesDensitySize()` is true and the size actually changed (`:148` at `ae84d2821e`).
Align and Distribute resend sizes unchanged, so they do not set it.

An author size off the recognized set is protected by the recognizer alone. An author size on it,
such as a calendar resized to exactly 300×300, is protected by the flag. Content resized before
#6390 has no flag, so a calendar an author left at exactly a tier size follows the next density
change.

### D6 — The follow-the-default-density checkbox in the calendar dialog

`CalendarPropertyDialogService` makes the same three calls as `SelectionListPropertyDialogService`
(`:133`, `:250`, `:252` at `ae84d2821e`):

- **Open:** `VSDialogService.readSizeFollowsDensity(info, sizePositionPaneModel, governed)`, with
  `governed` true only for a single full calendar (`getShowTypeValue() == CALENDAR_SHOW_TYPE` and
  `getViewModeValue() == SINGLE_CALENDAR_MODE`). The helper returns null for an unmarked calendar,
  so no checkbox is shown there either.
  - **Not a dropdown**, because a dropdown does not draw its stored height (§2.2), so a checkbox
    there would control nothing visible.
  - **Not a double calendar**, because ticking the box writes `calendarSize(ctx)`, which is 300 wide.
    A double calendar the toggle widened to 600 would have its two months squeezed into 300px.
- **Save:** `VSDialogService.applyDensitySize` before the existing `dialogService.setAssemblySize`
  (`CalendarPropertyDialogService.java:245`), then `VSDialogService.recordAuthorSize` after it, with
  the size as it was before the save.
- **A save that switches the show type or view mode applies neither** (added 2026-10-06). The size
  the dialog wrote for the switch (§2.2) is stored as sent. It is not reset to the density size, so a
  ticked single calendar switched to double keeps its doubled width. It is not flagged as the
  author's either, so a double calendar switched back to a single 300-wide box follows density
  again. This matches #6390's D7, where a dialog's show-type switch is a derived write that never
  sets the flag.
- **A save of a dropdown or double calendar ignores the checkbox answer.** Apply keeps the dialog
  open with the model it was opened with, so after an Apply that switched to double or dropdown, OK
  can resend the answer read while the calendar was single. The save clears it, so OK cannot reset
  that calendar to the 300-wide density size.

**No frontend change.** The calendar's general pane already embeds `<size-position-pane>`
(`calendar-general-pane.component.html:27`). The pane shows the checkbox whenever
`model.sizeFollowsDensity != null` (`size-position-pane.component.ts:151` at `ae84d2821e`) and locks
width and height while it is ticked.

## 4. `VSDensityDefaults` additions

Beside `selectionSize` / `isSeededSelectionSize` (`VSDensityDefaults.java:192`, `:208`):

```java
public static Dimension calendarSize(VizContext ctx)
// legacy 300×300 when !ctx.modern, else calendarSizeForMode(ctx.density)

public static boolean isSeededCalendarSize(Dimension size)
// null-safe; equals legacy or any of the three tier sizes

private static Dimension legacyCalendarSize()
// 300×300, read by both the producer and the recognizer so they cannot drift

private static Dimension calendarSizeForMode(String mode)
// 300 × (titleHeightForMode(mode) + CALENDAR_NAV_BAND + CALENDAR_ROWS × calendarRowHeightForMode(mode))

static int calendarRowHeightForMode(String mode)
// 38 / 34 / 30
```

Constants: `CALENDAR_NAV_BAND = 36`, documented as the browser's `.child-calendar-title`, and
`CALENDAR_ROWS = 7`, the weekday header plus six weeks. The constructor's 300×300 in
`CalendarVSAssemblyInfo` and `legacyCalendarSize()` must stay equal. A test pins this.

## 5. Unchanged on purpose

- The export painter (`VSCalendar`), every calendar template and stylesheet, and the 36px band.
- `fixCalendarSize()`, `fitCalendarHeightToTitle()` and the Toggle Double Calendar action. A double
  calendar the toggle widened to 600 is outside the recognized set, so density leaves it alone.
  Toggling it back to 300 wide puts it back in the set if its height is still a tier height.
- The dropdown's fixed 144px body (`CALENDAR_BODY_HEIGHT`) and its closed height.
- The constructor's 300×300.
- The bottom-tabs branch of `CalendarPropertyDialogService` (`:312-340`), which writes
  `DEFAULT_CALENDAR_HEIGHT` (162) when a calendar in a bottom-tabs container switches from dropdown to
  full calendar. 162 is not a recognized size, so such a calendar simply stops following density.
- Selection lists, trees and the container keep their own size rules and their restore-path reach.

## 6. Verification

### 6.1 Automated

- **`VSDensityDefaultsTest`:**
  - `calendarSize` is 300×332 / 300×300 / 300×266 at the three tiers, and 300×300 unmarked.
  - `isSeededCalendarSize` accepts those three sizes and rejects null, 300×162, 600×300 and 300×301.
- **`CalendarVSAssemblyInfo` tests:**
  - A calendar created under a modern mark has its tier's size at each tier. Created unmarked, it is 300×300.
  - The constructor's size equals `calendarSize` for an unmarked context, which is what
    `legacyCalendarSize()` returns.
  - `seedDensitySize` leaves a `userSize` calendar alone, leaves an off-set author size (400×300)
    alone, and leaves an unmarked calendar alone.
  - **Restore does not resize:** `reseedAfterRestore` on a marked 300×300 calendar at comfortable
    leaves 300×300.
  - **Modernize and Revert do not resize:** `VizModernizeUtil.modernize` and `revert` leave the size
    as it was.
  - **A density change does:** `VizModernizeUtil.reseed` takes a marked 300×300 calendar to 300×332
    when the dashboard is comfortable, and back to 300×300 at compact.
  - The dropdown show type is resized too (its stored height is not drawn).
  - `followsDensitySize()` is false at 600×332 (a toggled double calendar) and true again at 300×332.
- **`CalendarPropertyDialogServiceTest`** (tagged `core`):
  - No checkbox for an unmarked calendar, a dropdown, or a double calendar.
  - Ticking it writes the tier size and clears `userSize`.
  - Unticking it, or typing a different width or height, sets `userSize`.
- **`ComposerObjectService`:** one calendar case showing that a changed drag-resize sets `userSize`
  and an unchanged one does not.

### 6.2 Manual

Run with the dashboard set to each tier in turn:

1. Create a calendar: its box matches D3's table, and the rows look tighter at dense than at comfortable.
2. Drag-resize it, then change the dashboard density: the size stays. Tick the checkbox: it returns to the tier size.
3. Leave a fresh calendar alone and change the dashboard density: it resizes.
4. Create a calendar at compact on a dashboard that follows the org density, then change the org
   density to comfortable in EM and open the dashboard in the viewer: it stays 300×300.
5. Modernize, then Revert, a legacy 300×300 calendar: the size does not change.
6. Export to PDF and PNG: the calendar's box matches the browser's.
7. A full calendar inside a bottom-tabs container, at comfortable, then change the dashboard density:
   see §7.

## 7. Risks and accepted costs

- **Bottom-tabs alignment after a density change.** In the viewer, a full calendar inside a
  bottom-tabs container is drawn at its stored top (`vs-calendar.component.ts:186`), and that top is
  `tab top − height`. Only the property dialog and the bottom-tabs toggle recompute it
  (`TabVSAssemblyInfo.java:460`, `:551`). A density change that resizes such a calendar therefore
  leaves it 32px into the tab strip (compact to comfortable) or 34px short of it (compact to dense).
  This has been read in the code but not yet seen in a browser. The selection lists' size rule has
  the same exposure. **Not fixed in this slice.** Manual check 7 confirms or refutes it. If it is
  confirmed, the fix belongs in the shared re-seed path so it covers both types, as a follow-up.
- **Double calendars.**
  - One switched to double in the property dialog keeps its 300px width and shows two months in it.
    That is today's behaviour at 300×300, and density keeps following its height.
  - One widened to 600 by the toggle no longer follows density, and gets no checkbox (D6). Toggling
    it back restores both.
- **Pre-#6390 author sizes on a tier value** follow the next density change (D5).

## 8. Out of scope

- **Gaps between cells.** A density gap that makes the weekday headers and selected days separate
  tiles. Both the browser and the export painter would need it, and it would decide the shape of the
  widget spec's §07 range selection (a continuous band with bordered ends). That decision comes first.
- **The dropdown's open body.** 144px leaves 15.4px per row at every tier. A density-driven body would
  need a model field, because the constant is also read on the server and by the composer layout.
- **The weekday-header path split**, a stored-format change tracked on the roadmap as its own
  initiative.
- **The bottom-tabs re-alignment fix** (§7).
