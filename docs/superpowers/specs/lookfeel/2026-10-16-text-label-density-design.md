# Text label density height — design

**Date:** 2026-10-16. **Branch:** `feature-text-label-density`, cut from #6798 (`feature-form-control-density`, `b954c83400`). It is rebased onto `epic-74519` once #6798 merges.

A marked Text component takes the form controls' density height, so a label sits level with the input beside it. Today a text is 20px at every tier while #6798 makes text input, combo box, spinner and submit 24 / 28 / 30.

## 1. Rulings (user, 2026-10-16)

| # | Decision | Ruling |
|---|---|---|
| D1 | Height per tier | The form controls' height: **24 / 28 / 30** at dense / compact / comfortable, from `VSDensityDefaults.controlHeight(ctx)`. Unmarked stays 20. |
| D2 | Which texts are sized, and when | A **new** marked text, and a **dashboard density change** for a text that follows. A restore (viewer open, bookmark, export), Modernize and Revert never resize a text. This is the calendar's reach (#6484 D4). |
| D3 | Auto Size | An Auto Size text never follows and is never offered the checkbox. Its height comes from its content in the viewer (`vs-text.component.html`) and in export (`CoordinateHelper.fixTextSize`, `fixTextHeight`). |
| D4 | Authorship | Height only, as for the form controls (#6798 `densitySizeIsHeightOnly()`). A width-only change keeps the text following; Width stays editable while Follow default density is ticked. |

Implied, not separately asked: marked texts only; the font stays bold 11px; the default width stays `AssetUtil.defw` (100).

## 2. Why these values hold

- **Text is vertically centred by default.** `TextVSAssemblyInfo.setDefaultFormat` sets `H_LEFT | V_CENTER`, so a 28px label beside a 28px input puts both texts on the same line.
- **24 / 28 / 30 never equals the legacy 20.** The recognizer is `VSDensityDefaults.isControlHeight` (24/28/30 only), not #6798's `isSeededControlHeight`, which also reads 20 as density-owned for controls. A text saved at 20 is therefore never read as following, so existing dashboards do not move on a density change.
- **A new text cannot use the recognizer.** It starts at the base default 100×20 (`AssemblyInfo`), which is deliberately not a tier height. The creation seed is unconditional for a marked text that is not Auto Size, and the density-change seed (`seedDensitySize`) goes through `followsDensitySize()`.

## 3. Design

All of it is in `TextVSAssemblyInfo`, through the author-size API #6390 added and #6798 extended.

| Member | Text's implementation |
|---|---|
| `takesDensitySize()` | `!isAutoSize()`. The runtime value counts: `Viewsheet.getWarningTextAssembly` sets Auto Size with `setAutoSize(true)` (runtime) before `initDefaultFormat`. |
| `followsDensitySize()` | `takesDensitySize() && !isUserSize() && VSDensityDefaults.isControlHeight(height)` |
| `defaultSize(ctx)` | `(current width, VSDensityDefaults.controlHeight(ctx))` |
| `densitySizeIsHeightOnly()` | `true` |
| `seedDensitySize(ctx)` | When `ctx.modern && followsDensitySize()`, set the height to `controlHeight(ctx)`. Called only by `VizModernizeUtil.reseed` (a dashboard density change). |
| `initDefaultFormat()` | After `setDefaultFormat(true)`: when `ctx.modern && takesDensitySize()`, set the height to `controlHeight(ctx)` (the creation seed). |

What follows from the existing generic code, with no change:

- **Composer drag.** `ComposerObjectService.resizeObject` flags `userSize` when `takesDensitySize() && isAuthorSizeChange(before, after)`, so a height drag is the author's and a width drag is not.
- **Dialogs.** `VSDialogService.readSizeFollowsDensity` offers the checkbox for a marked text that takes a density size. `setAssemblySizeFollowingDensity` resets a ticked box (keeping a typed width, since it is height-only) and records an untick or a typed height as the author's. `TextPropertyDialogService` wires both, as `SubmitPropertyDialogService` does. The Size & Position pane already shows the checkbox when `sizeFollowsDensity` is not null and keeps Width editable for a height-only type, so there is **no frontend change**.
- **Bottom tabs.** `VizModernizeUtil.reseed` re-flushes every bottom-tabs child after the seed, so a text the density resizes stays on its strip.
- **Export.** It uses the stored size, so it follows with no change.

### 3.1 Creation paths

| Path | Effect |
|---|---|
| Composer drop (`VSEventUtil.createVSAssembly` → `initDefaultFormat`) | Takes the tier height. |
| Visualization wizard description text (`VSWizardObjectHandler`) | Sets its own height after `initDefaultFormat`, unchanged. |
| Dashboard warning text (`Viewsheet.getWarningTextAssembly`) | Auto Size at runtime, so it is not sized. |
| **Print and device layout text** (`VSLayoutService.createVSAssembly`) | **Keeps the legacy 20.** It is a layout-only object stored in a `VSAssemblyLayout` (`VSLayoutControllerService` ~324), so no dashboard density change ever reaches it. A tier height there would be frozen at creation. `createVSAssembly` puts its height back to `AssetUtil.defh` after `initDefaultFormat`. *(Claude's call, not a user ruling.)* |
| Exports, presenters, format painter, report conversion | Transient objects that never call `initDefaultFormat` on a dashboard text. Unaffected. |

## 4. Accepted costs

- **An unflagged legacy text sized to exactly 24, 28 or 30 reads as following once its dashboard is Modernized,** and moves on the next density change. Modernize itself does not resize it. #6798 accepted the same for controls. There is no flag on content saved before this, so it cannot be told apart from a seeded height.
- **Revert leaves a seeded 24 / 28 / 30 text at that height,** and **Modernize leaves a legacy text at 20,** until the author ticks Follow default density. This is the calendar's accepted cost.
- **Ticking Auto Size in the same save as a ticked Follow:** Auto Size wins, and nothing is resized. The checkbox is not hidden live while the dialog is open.
- **An org-level density change resizes no existing text.** Only the dashboard's own density change does, as for the calendar.

## 5. Out of scope

Font size per tier; the default width; per-tier padding; Auto Size behaviour; the combo box's open list (dropped, since it is the native `<select>` popup for 500 options or fewer); the table row-height follow checkbox (roadmap decision 4, after #6832 and #6798 merge).
