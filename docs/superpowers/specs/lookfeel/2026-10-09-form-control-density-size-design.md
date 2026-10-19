# Form controls — an author's size survives density, and checkbox and radio rows join the lane

**Date:** 2026-10-09
**Verified against:** community `feature-form-control-density`, cut from
`bug-bottom-tabs-modernize-revert` @ `a41d4cff1d` (#6561, stacked on #6484 and #6390). Every code
claim below was checked against that commit's files.
**Types in scope:** combo box, text input, spinner, submit, checkbox, radio button.
**Not in scope:** the slider (§11).

## 1. Why

Six form controls already take a density height. Two things about how they take it are wrong.

**They guess whether a height is the author's from its value.** The size rule moves any control
whose height is 20, 24, 28 or 30, and Revert puts any 24, 28 or 30 back to 20. A text input an
author sized to exactly 28px follows the next density change, and Revert undoes it. The
`isControlHeight` javadoc (`VSDensityDefaults:170-178`) records this as an accepted false positive.
#6390 removed the same guess for selection lists, trees and the container with a stored `userSize`
flag. These six types never took it.

**Checkbox and radio rows are out of step with the dashboard around them.** The seed doubles the
control height for the box and holds the title at 20, so the option row absorbs the rest: 28 / 36 /
40px at dense / compact / comfortable. At comfortable, a checkbox's option rows are 40px under a
20px title, beside a selection list whose title lane is 30 and whose rows are 28, and a text input
that is 30. The title's exclusion from the density lane is pinned by
`TitleLaneHeightRowTest.excludedTypesNeverTakeTheDensityRow` (`:72-77`). The reason given in code
(`CheckBoxVSAssemblyInfo:473-480`) cites `updateDataRowCol()`, which does not exist. The real row
arithmetic, in `VSCheckBoxModel:50-67`, `VSCompound.getColumnCount` (`:265-276`) and
`HTMLCoordinateHelper.getRowCount` (`:741-755`), only requires the lane and the row to move together.
The seed avoided that by never moving the lane.

## 2. What exists today

### 2.1 The single-height controls

Combo box, text input, spinner and submit each carry an identical block in `seedChromeDefaults`
(`ComboBoxVSAssemblyInfo:426-434`, `TextInputVSAssemblyInfo:92-100`, `SpinnerVSAssemblyInfo:75-83`,
`SubmitVSAssemblyInfo:92-100`):

- marked, and height 20 or a tier height → height = `VSDensityDefaults.controlHeight(ctx)`, 24 / 28 / 30;
- unmarked, and height a tier height → height = 20.

Width is never touched. The four types share no base class below `VSAssemblyInfo`: combo box
extends `ListInputVSAssemblyInfo`, text input `ClickableInputVSAssemblyInfo`, spinner
`NumericRangeVSAssemblyInfo`, submit `ClickableOutputVSAssemblyInfo`.

The block runs wherever `seedChromeDefaults` runs: creation, every restore
(`VizModernizeUtil.reseedAfterRestore`, `:214`), a density change (`reseed`, `:122`), Modernize,
Revert and the mode switch (`moveMark`, `:107`).

**The unmarked branch fires on every restore, which is a live defect.** `AbstractVSAssembly.parseState`
(`:656`) and `Viewsheet`'s state restore (`:2433`, `:2474`) re-seed every assembly, unmarked ones
included, through `VizContext.of(info)`. So a legacy text input, combo box, spinner or submit an
author sized to 24, 28 or 30px is drawn at 20 in every viewer open and export, and a legacy checkbox
or radio button at 48, 56 or 60 at 40. Revert is the only route that needs the unmarked branch, and
it is the only one that runs with `ctx.transition` (`VizContext.ofTransition`, `:112`). #6157 fixed
the same defect for selections with exactly that guard (`SelectionBaseVSAssemblyInfo:1011`,
`CurrentSelectionVSAssemblyInfo:114`).

### 2.2 Checkbox and radio button

Both default to `2 × defw` by `2 × defh`, 200 × 40 (`CheckBoxVSAssemblyInfo:53`,
`RadioButtonVSAssemblyInfo:50`). Their seed (`CheckBoxVSAssemblyInfo:482-505`,
`RadioButtonVSAssemblyInfo:432-455`) works in pairs:

- marked, and the box is 40 or twice a tier height → box = `2 × controlHeight(ctx)` (48 / 56 / 60),
  and the row, if it still equals the old box minus the title, becomes the new box minus the title
  (28 / 36 / 40);
- unmarked, and the box is twice a tier height → box = 40, and a paired row → 20.

The title stays at its stored 20: `getTitleHeight()` returns `titleInfo.getTitleHeight()` directly
(`CheckBoxVSAssemblyInfo:134`, `RadioButtonVSAssemblyInfo:156`), not through the density resolver
the ten included types use.

The row is `ListInputVSAssemblyInfo.cellHeight` (field `:869`, getter `:166`, written `:367`,
parsed `:392`, copied `:668`). It has no authorship flag. The dialog writes it unconditionally
(`VSInputService:573`, `:1325`), and so does the composer's option-row drag
(`VSInputService.setDetailHeight`, `:239-252`).

### 2.3 #6390's author-size contract

`VSAssemblyInfo` carries `userSize` (field `:1948`, written only when true `:890`, parsed `:943`,
copied `:707`), three hooks a type overrides — `takesDensitySize()` (`:1455`),
`followsDensitySize()` (`:1462`) and `defaultSize(ctx)` (`:1469`) — and `resetSize(ctx)` (`:1476`),
which clears the flag and writes `defaultSize`.

Three places set or clear the flag:

- `ComposerObjectService:148` — a composer resize, when `takesDensitySize()` and the size changed;
- `VSDialogService.recordAuthorSize` (`:193`) — a Size & Position apply that unticked, or changed
  width or height from a client with no answer;
- `VSDialogService.applyDensitySize` (`:177`) — a ticked apply calls `resetSize` and copies the
  box's new width and height into the model.

`VSDialogService.readSizeFollowsDensity` (`:165`) fills `SizePositionPaneModel.sizeFollowsDensity`:
null (no checkbox) unless the box is governed, takes a density size and is marked.

### 2.4 The Size & Position pane

`size-position-pane.component.ts` shows each follow-density checkbox when its model field is
non-null (`ngOnInit`, `:147-151`). Ticking the size checkbox disables **both** Width and Height
(`sizeFollowChanged`, `:124-129`; `initForm`, `:73-85`; the template's `[disabled]` bindings,
`.html:48` and `:60`).

## 3. Decisions

### D1 — The six controls take #6390's author-size flag

Each overrides `takesDensitySize()` to true, `followsDensitySize()` and `defaultSize(ctx)`. The
size rule skips a flagged box in both directions, so Revert leaves an author's height alone. The
`isControlHeight` false positive (§1) is gone for any height set after this ships.

### D2 — Density owns only these controls' height, so only a height change is the author's

Widening a text input or combo box is the common edit and says nothing about its height. One new
hook, `VSAssemblyInfo.densitySizeIsHeightOnly()`, false by default and true on the six types,
drives four sites:

1. **`VSAssemblyInfo.isAuthorSizeChange(before, after)`**: `before.height != after.height` for a
   height-only type, `!before.equals(after)` otherwise. It replaces the equality test at
   `ComposerObjectService:148` and the width-or-height test in `recordAuthorSize`.
   *(Amended 2026-10-19: #6832 added the same method first, overridable, and narrowed it to the
   height in `SelectionBaseVSAssemblyInfo`. This design's body is the default, and the method is
   not final, so that override stands.)*
2. **`applyDensitySize`** copies only the height back into the model for a height-only type, so a
   width typed into the dialog in the same apply survives.
3. **`readSizeFollowsDensity`** also writes `SizePositionPaneModel.densityHeightOnly`.
4. **The pane** keeps Width enabled while the size checkbox is ticked when `densityHeightOnly` is
   true.

Lists, trees, the container and the calendar return false. The container and the calendar keep
#6390's behaviour exactly; lists and trees keep #6832's height-only authorship from their own
override.

### D3 — Checkbox and radio button join the title lane

`getTitleHeight()` resolves through `VSDensityDefaults.titleHeight(this, stored)` (`:344`), the
resolver the ten included types use: 20 / 26 / 30 when marked, not author-set, and stored at the
legacy 20; the stored height otherwise. Both types already expose `isUserTitleHeight()` and
`getLegacyTitleHeight()` (20). The composer's title drag already sets the flag: it goes through
`resizeTitle`, which flags every `TitledVSAssembly` (`ComposerObjectService:448-451`), and both
types are one through `CompositeVSAssembly`.

Neither type is in `ANCHORED_ASSEMBLY_TYPES` (`mini-toolbar.service.ts:42`), so a taller lane draws
no strip. Anchoring them stays out of scope.

### D4 — The option row takes the selection list's row; the box is lane plus one row

| | dense | compact | comfortable | legacy |
|---|---|---|---|---|
| lane | 20 | 26 | 30 | 20 |
| option row | 20 | 24 | 28 | 20 |
| default box | **40** | **50** | **58** | 40 |
| *today: box / row* | *48 / 28* | *56 / 36* | *60 / 40* | *40 / 20* |

The row is `VSDensityDefaults.cellHeight(ctx)` (`:145`), the selection list's matrix. An option is
a list item, so it matches the list beside it. Dense returns to exactly the legacy box. A default
box therefore shows exactly one row at every tier, the same as today's.

Rejected: rows at the control height (24 / 28 / 30, box 44 / 54 / 60), which match a text input but
not the selection list; and keeping today's boxes (rows 28 / 30 / 30), which match no matrix.

### D5 — The option row records authorship with a flag and resolves when drawn

This mirrors the selection list's cell height (`SelectionBaseVSAssemblyInfo.getEffectiveCellHeight`,
`:141-145`).

- `ListInputVSAssemblyInfo` gains `userCellHeight`, beside the `cellHeight` it guards, written only
  when true so an unmarked asset's XML stays byte-identical. An absent attribute means false.
- `getCellHeight()` returns `VSDensityDefaults.cellHeight(VizContext.of(this))` when the type takes
  a density row, the assembly is marked, the flag is clear and the stored row is 20; the stored row
  otherwise. The cheap tests run before the context is built, as in `titleHeight(info, stored)`.
- `getCellHeightValue()`, new, returns the stored row for the writer, the seed and the dialog's
  comparisons.
- `takesDensityRow()`, a new protected hook on `ListInputVSAssemblyInfo`, is false by default and
  true on checkbox and radio button. Combo box inherits the field and never resolves a row.

Because `getCellHeight()` resolves, the seven render readers follow unedited: `VSCheckBoxModel:53`,
`VSRadioButtonModel:53`, `VSCompound:109` and `:270`, `HTMLCoordinateHelper:730`, `:751` and `:763`.

The flag is set by the dialog's Cell Height (D8) and by the composer's option-row drag
(`setDetailHeight`).

Rejected: a seed-time row recognised by value, which needs no flag but brings back the guess D1
removes.

### D6 — Existing marked checkboxes and radio buttons are rewritten on their next open

Forced by D3. The lane resolves when drawn, so a box today's seed left at 60 / 40 would read a 30px
lane over a 40px row at comfortable and overflow its box by 10px. The rewrite runs in
`seedChromeDefaults`, which already runs on open. It is the same reach the selection family
accepted (`2026-10-01-selection-family-padding-design.md` §9, "feature not live").

### D7 — Revert

An unmarked control resolves its lane and row to the stored values, so Revert needs no row work for
anything seeded under this design. The size rule puts an unflagged recognised box back to legacy.
A flagged box keeps its size.

### D8 — Dialogs mirror the selection list's

The checkbox and radio dialogs read and apply the title and row exactly as
`SelectionListPropertyDialogService` does (`:121-132` read, `:255-287` apply): the resolved value in
the field, a non-null `titleHeightFollowsDensity` / `cellHeightFollowsDensity` when marked, tick →
clear the flag and store the legacy 20, untick → flag and store the field, null (unmarked or a stale
client) → flag and store only if the field differs from the resolved value. All six dialogs gain
the size checkbox with #6390's order: `shownSize`, `applyDensitySize`, `setAssemblySize`,
`recordAuthorSize` (`SelectionListPropertyDialogService:248-252`).

A follow checkbox reads ticked only for a value the density rule owns: unflagged at the legacy 20,
or for the row one of the former seed's 28 / 36 / 40. An unflagged author value reads unticked, so
an OK keeps it.

### D9 — The legacy branch runs only on Revert and the mode switch

Every unmarked branch in §5 and §6.2 runs only when `ctx.transition` is true, the guard #6157 put on
the selection size rule. A plain restore, creation or density change never resizes an unmarked
control. This fixes the defect in §2.1 for the six types. Revert still reaches the branch, because
`revert` stamps through `moveMark` with `VizContext.ofTransition(vs, null)`.

The existing Revert tests in `ControlHeightFollowDensityTest` simulate Revert with
`VizContext.of((VizMark) null)`, which is a restore, not a Revert. They move to
`VizContext.ofTransition(null, null)`.

## 4. Values and rules

Values stay in `VSDensityDefaults`, beside the matrices they derive from:

```java
static int optionBoxHeightForMode(String mode)        // titleHeightForMode + rowHeightForMode: 40 / 50 / 58
public static int optionBoxHeight(VizContext ctx)     // 2 * defh when !ctx.modern
public static boolean isSeededControlHeight(int h)    // 20, 24, 28, 30
public static boolean isSeededOptionBoxHeight(int h)  // 40, 50, 58, and today's 48, 56, 60
public static boolean isFormerOptionBoxHeight(int h)  // today's 48, 56, 60: twice a control height
public static boolean isFormerOptionRow(int h)        // today's 28, 36, 40: a former box less 20
```

`optionBoxHeightForMode` adds the two matrices rather than holding three literals, so the box cannot
drift from its parts. `isControlHeight` keeps its one remaining reader, the unmarked row pairing in
§6.2, and its javadoc drops the false-positive paragraph.

The rules go in one new package-private class, `FormControlDensity`, so each type's
`seedChromeDefaults` makes one call and the rule is tested once:

```java
static void seedControlHeight(VSAssemblyInfo info, VizContext ctx)
static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> int lane(T info, VizContext ctx)
static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> int row(T info, VizContext ctx)
static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> void seedOptionBox(T info, VizContext ctx)
```

The generic bound gives the option-list rule both the row and the title without a cast, and keeps
combo box, a `ListInputVSAssemblyInfo` without a title, out of it at compile time.

## 5. The single-height controls

`FormControlDensity.seedControlHeight(info, ctx)`, called from each of the four `seedChromeDefaults`
in place of the copied block:

| state | stored height | result |
|---|---|---|
| `userSize` | any | unchanged |
| marked | 20, 24, 28, 30 | `controlHeight(ctx)` |
| unmarked, `ctx.transition` | 24, 28, 30 | 20 |
| otherwise | — | unchanged |

Per-type overrides, on each of the four:

- `takesDensitySize()` → true
- `densitySizeIsHeightOnly()` → true
- `followsDensitySize()` → `!isUserSize() && isSeededControlHeight(getPixelSize().height)`
- `defaultSize(ctx)` → `new Dimension(getPixelSize().width, controlHeight(ctx))`

`followsDensitySize` recognises exactly the heights the rule moves, so the checkbox never shows
ticked for a size the rule would leave.

## 6. Checkbox and radio button

### 6.1 Accessors

On both types, `getTitleHeight()` → `VSDensityDefaults.titleHeight(this, titleInfo.getTitleHeight())`.
`getTitleHeightValue()` stays the stored value.

On `ListInputVSAssemblyInfo`, per D5: `userCellHeight` with `isUserCellHeight` / `setUserCellHeight`,
`takesDensityRow()`, `getCellHeight()` resolving, `getCellHeightValue()` raw. `writeAttributes`
writes `userCellHeight="true"` only when set; `parseAttributes` reads it; `copyViewInfo` copies it.

### 6.2 The seed

`FormControlDensity.seedOptionBox(info, ctx)`, called from both `seedChromeDefaults` in place of the
paired blocks. It runs two steps in order.

**Step 1, the row.** Today's seed wrote 28, 36 or 40 into the stored row; this design never stores a
tier row.

| state | stored row | result |
|---|---|---|
| `userCellHeight` | any | unchanged |
| marked | 28, 36, 40 | 20 |
| unmarked, `ctx.transition` | 28, 36, 40, equal to box − stored title, box twice a tier height | 20 |
| otherwise | — | unchanged |

The marked case ignores the box, so a box an author resized after today's seed ran is covered too.
The unmarked case keeps today's pairing, so an unmarked author row is never touched.

**Step 2, the box.**

| state | stored box | result |
|---|---|---|
| `userSize` | any | unchanged |
| marked | 40, 50, 58, 48, 56, 60 | `lane(ctx) + row(ctx)` |
| unmarked, `ctx.transition` | 48, 56, 60, 50, 58 | 40 |
| otherwise | — | unchanged |

`lane(ctx)` and `row(ctx)` apply the accessors' tests against the context the seed is handed rather
than `VizContext.of(info)`: the stored value when author-set or not at its legacy 20, the tier value
otherwise. So an author title or row is kept and the box grows or shrinks around it. The seed
always runs after the mark is stamped (`VizModernizeUtil.seedAll`, `:173-178`), but it is handed its
context and must not assume the two agree.

Per-type overrides, on both: `takesDensitySize()`, `densitySizeIsHeightOnly()` and
`takesDensityRow()` → true, `followsDensitySize()` → `!isUserSize() &&
isSeededOptionBoxHeight(height)`, `defaultSize(ctx)` → current width ×
`FormControlDensity.lane(this, ctx) + FormControlDensity.row(this, ctx)`.

The comment blocks at `CheckBoxVSAssemblyInfo:473-480` and `RadioButtonVSAssemblyInfo:408-415`,
which cite the non-existent `updateDataRowCol()`, are replaced.

## 7. Dialogs and the composer

**Server.**

- `VSInputService`: the checkbox (`:386`, `:526`), radio (`:1134`, `:1277`), text input (`:711`,
  `:835`), combo box (`:1768`, `:1972`) and spinner (`:2187`, `:2261`) get/set pairs gain
  `readSizeFollowsDensity(info, model, true)` on read and #6390's three-call order on apply. The
  checkbox and radio pairs also take D8's title and row handling, replacing `:454-458` / `:569-573`
  and `:1200-1202` / `:1321-1325`.
- `SubmitPropertyDialogService` (`:84`, `:136`) takes the size checkbox the same way.
- The repeated logic is four `VSDialogService` helpers, so each call site stays one line and the
  logic is tested once: `setAssemblySizeFollowingDensity(info, model, viewsheet)` runs #6390's
  three-call order, the static `readOptionHeights(info, model)` / `applyOptionHeights(info, model)`
  carry D8's title and row handling for checkbox and radio button, and `applyOptionListSize(info,
  model, viewsheet)` applies the lane and row before the size so a following box is sized from
  them. The checkbox and radio apply paths call `applyOptionListSize`.
- `VSInputService.setDetailHeight` (`:252`) sets `userCellHeight`.
- `ComposerObjectService:148` uses `isAuthorSizeChange`.
- `VSDialogService.applyDensitySize`, `recordAuthorSize` and `readSizeFollowsDensity` change per D2.

**Browser.**

- `SizePositionPaneModel` (Java and `size-position-pane-model.ts`) gains `densityHeightOnly`.
- `size-position-pane.component.ts`: `initForm` and `sizeFollowChanged` disable Width only when
  `!model.densityHeightOnly`. The template's `[disabled]` binding on Width (`.html:48`) changes the
  same way.

No other template or model changes. The pane already renders the three follow-density checkboxes
from non-null fields.

**Not flagged**, as in #6390 §10: the dashboard wizard's resize, sizes a script sets at runtime,
and device and print layout sizes stored in `VSAssemblyLayout`.

## 8. Surfaces

**Browser.** `VSCheckBoxModel` and `VSRadioButtonModel` compute rows and columns from
`getTitleHeight()` and `getCellHeight()`, so they follow D3 and D5. The four single-height controls
draw at their stored box.

**Export.** PDF, SVG/PNG, PowerPoint and Excel paint checkbox and radio buttons as pictures through
`VSCompound` (`PDFVSExporter.writeInputWithLabel`, `PoiExcelVSExporter` and `PPTVSExporter`
`writePictureWithLabel`), which reads both accessors (`:100-109`, `:265-276`, `:288`). HTML reads
them in `HTMLCoordinateHelper.getCellSize` / `getRowCount` / `getColCount` (`:727-768`). Print layout
draws the title and the selected labels as text boxes, and its lane comes from
`tinfo.getTitleHeight()` (`VsToReportConverter:1482`); it draws no option rows. Excel anchors
pictures from pixel bounds, so a changed box moves its anchor, as in every earlier slice. CSV writes
values only.

**Bottom tabs.** A changed box height is kept on the strip by #6484's re-flush on a density change
(`VizModernizeUtil.reseed`, `:122-135`) and #6561's on Modernize, Revert and the mode switch
(`moveMark`, `:107-115`). On open, `reseedRestored` (`:226-255`) measures any type's height before
and after the seed and shifts the child by the difference. That restore-time shift is why this
branch stacks on #6561: D6's rewrite on open would otherwise move a checkbox off its strip.

**Rows and columns.** Every renderer lays out every value: rows = ⌊(box − lane) / row⌋, at least
one; columns = ⌈values / rows⌉; column width = box width / columns. No option is dropped. Taller
rows in an author-sized box mean fewer rows and more, narrower columns (§10.2).

## 9. Verification

### 9.1 Automated

`core` JUnit, beside `ControlHeightFollowDensityTest`, `TitleLaneHeightRowTest`,
`AuthorSizeFlagTest`, `BottomTabsReseedTest`, `SizeFollowsDensityDialogTest` and
`CalendarSizeFollowsDensityTest`:

- **Values.** `optionBoxHeight` is 40 / 50 / 58 and legacy 40, and equals lane + row at every tier.
  `controlHeight` is unchanged.
- **The four single-height controls**, one parameterised test over the types:
  - creation at each tier; a later density change moves an unflagged box and leaves a flagged one;
  - Revert (`VizContext.ofTransition(null, null)`) leaves a flagged 28 and puts an unflagged 28
    back to 20;
  - D9: an unmarked 28 is untouched by a plain restore (`VizContext.of((VizMark) null)`);
  - `followsDensitySize`'s truth table; `resetSize` writes the tier height and keeps the width.
  - `ControlHeightFollowDensityTest`'s Revert tests move to the transition context, and
    `aControlHandSizedToATierValueIsAlsoMoved` (`:412`) keeps its meaning only for an unflagged
    box; a flagged sibling is added.
- **`isAuthorSizeChange`**: a width-only change is not the author's on the six types and is on a
  selection list.
- **Checkbox and radio button:**
  - `TitleLaneHeightRowTest` moves both into `includedTypesTakeTheDensityRow`; the excluded test
    goes.
  - the row resolves when marked, unflagged and stored at 20; a flagged row and an unmarked row do
    not; combo box never resolves.
  - `userCellHeight` round-trips, and an unmarked checkbox's XML is byte-identical before and after
    this change.
  - the seed: 60 / 40 → 58 with a resolving row at comfortable; 140 / 40 (author box) → 140 with a
    resolving row; 48 / 28 at dense → 40; Revert of a 58 box → 40.
  - a default box draws exactly one row at every tier, by the `VSCheckBoxModel:62-63` formula.
  - the checkbox and radio tests at `ControlHeightFollowDensityTest:139-295` and `:425`, which pin
    today's doubled box and fixed title, are replaced by tests of D4's values.
- **Composer:** in `ComposerObjectServiceTest`, a height change flags a text input, a width-only
  change does not, and an unchanged resend does not. `setDetailHeight` flags the row.
- **Dialogs**, at the `VSDialogService` helper level (§7), because `VSInputService` has no test
  harness and its apply paths dereference a dozen sub-models: the follow fields are null when
  unmarked, ticking resets and clears, unticking flags, an untouched apply at comfortable flags
  nothing, and a ticked apply keeps a typed width on a height-only type.
- **Bottom tabs:** a checkbox on the strip saved with today's 60px box keeps its bottom edge on open.

Front end: `size-position-pane.spec.ts` gains Width staying enabled when ticked with
`densityHeightOnly`, and disabled without it.

### 9.2 Fixture and manual checks

**Task 0, before any production code lands:** export baselines from today's build, as slice C did.
One dashboard per tier, plus a legacy copy, each holding: every control at its default size; a
widened text input; a combo box an author made taller; a checkbox and a radio button at default;
a checkbox sized by an author to 100px tall with eight values; and a bottom-tabs strip holding a
checkbox and a text input. The marked copies are what prove D6, because only today's seed can
produce their 48 / 56 / 60 boxes. Export PDF, PNG and HTML of each.

**After:**

- every marked copy opens with D4's boxes, the bottom-tabs children still on their strip;
- a density change dense → comfortable → compact moves default controls and leaves the widened
  text input following and the taller combo box fixed;
- the size checkbox: ticking returns a box to its tier, and Width stays editable on the six types;
- Revert keeps the author-sized controls and returns the rest to legacy;
- viewer against PDF, PNG and HTML: lane and rows agree;
- the legacy copy's exports are identical to its baselines.

## 10. Risks and accepted costs

1. **Existing marked checkboxes and radio buttons shrink on their next open** (D6): boxes 60 → 58,
   56 → 50, 48 → 40, rows 40 → 28, 36 → 24, 28 → 20. A shrink leaves a gap below rather than an
   overlap, and bottom tabs are handled (§8).
2. **Rows in an author-sized box can rearrange.** A marked checkbox or radio button with an
   author-sized box shows fewer, taller rows and more columns. Every option stays visible, but each
   column is narrower, so long labels are shortened in export (`VSCompound.shrinkLabel`) and can run
   into the next column in the browser (`white-space: nowrap; overflow: visible`).
3. **Values set before this ships are read as seeded, once:** a control height of 20, 24, 28 or 30;
   a marked option box of 40, 48, 50, 56, 58 or 60; and a marked option row of 28, 36 or 40. Any
   author edit after this ships records its flag.
4. **Combo box carries an inert `userCellHeight`.** It inherits the field and never resolves a row.
5. **A box whose author title or row takes it off the recognised set stops following**, without a
   flag: the checkbox shows unticked because the rule would not move it. Ticking it resets the box.
6. **An unmarked control an author edits gains a flag attribute in its XML** (`userSize`, or
   `userCellHeight` on a checkbox or radio row). It is inert until the dashboard is Modernized, the
   same as #6390's `userSize` on unmarked boxes.
7. **A composer open does not run the rewrite.** Design mode never restores state, so a checkbox or
   radio button saved by the former seed shows a 30 lane over its 40 row at comfortable in the
   composer until it is opened in the viewer, its dialog is applied (which lands on the tier box),
   or the dashboard's density or mark changes. #6390 accepted the same gap for selection lists.
8. **A composer row drag leaves the box until the next open.** The drag flags the row; the next open
   sizes a following box to lane + row, so a row dragged to 33 in a 58 box opens at 63.

## 11. Out of scope

- **The slider.** No rows and no lane; its geometry is a track, a handle and tick labels at fixed
  sizes in `vs-slider.component.scss` and the `VSSlider` painter. The same reasoning kept the range
  slider's body out of #6390 (`2026-10-05-selection-container-density-design.md` §10).
- **Inner text padding and font size by tier.** The browser pads the controls by fixed amounts
  (`vs-text-input`, `vs-spinner`: `0 4px`), and the export painters draw text across the whole box
  (`VSComboBox:103`, `VSSpinner:84`). Both would have to change together.
- **Anchoring checkbox and radio button.**
- **The combo box's open list rows.**
- **The wizard, script and layout sizes** (§7).

## 12. Branching

`feature-form-control-density` is cut from `bug-bottom-tabs-modernize-revert` (#6561) @
`a41d4cff1d`, because it needs #6390's author-size API, #6484's density-change re-flush and #6561's
restore-time shift, none of which is on `epic-74519`. It rebases down the stack as each PR merges,
and onto `epic-74519` once #6561 does.
