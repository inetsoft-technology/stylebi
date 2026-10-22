# Range slider — density padding, label spacing, and the contained slider

**Date:** 2026-10-22
**Issue:** Bug #78994
**Types in scope:** the range slider (`TimeSliderVSAssembly`), standalone and as a child of a selection
container.
**Not in scope:** the table's ad hoc filter slider, which stays as it is today (D7); the range slider's
CSS redraw (track and band colours, handle shape, the derived selection fill); the single-value slider
(`SliderVSAssemblyInfo`); the selection container's own size.
**Decided by:** the designer's review of the options write-up chose B1 on 2026-10-22, with the label
spacing and the container treatment. The user ruled the same day that density ships on its own, that the
ad hoc filter slider is left alone, that a converted slider stores its correct body, that density owns the
height whatever the width, and that the height gets a "follow default density" checkbox. Calls this spec
makes on its own are marked *(my call)* and collected in §12.
**Read at:** #6798's head `ae00b34ca6`, which this branch stacks on (§9), over `epic-74519` `1f2a5bc5d8`.
Every line number below is at `ae00b34ca6`. Of the files cited, #6798 changes only `VSAssemblyInfo`,
`VSDensityDefaults`, `VSDialogService` and the Size & Position pane.

## 1. Why

A request asks for dashboard density to apply to a standalone range slider. Today a new standalone slider
is identical at dense, compact and comfortable. Every other modern object's content starts 8 / 12 / 16 px
in from its box, while the slider's track runs edge to edge and its end handles sit on the box edge. Its
labels also touch the track: the current-value label ends exactly at the track's top in the browser.

## 2. The slider today

- **Box.** 200×40 (2 × `defw` by 2 × `defh`), set in the `TimeSliderVSAssemblyInfo` constructor (`:58`).
- **No frame.** Toolbox creation calls `initDefaultFormat()`, which runs `setDefaultFormat(false)`: no
  border and no background. The type is not in `isCornerSeedTarget()` (`VSAssemblyInfo:1336`), so it takes
  no corner either. A new modern selection list is the same: no border, no background. Charts and tables
  are the only types with a card surface.
- **No padding.** `TimeSliderVSAssemblyInfo` does not override `defaultPadding(VizContext)`
  (`VSAssemblyInfo:1537`). The model sends none and the painter reads none.
- **Track position.** Centered vertically: `ceil(h / 2) − 4` in the browser (`setTopPositions`,
  `vs-range-slider.component.ts:1101`); `h / 2 − 4` in export with min/max visible and `3h / 4 − 4`
  without (`VSTimeSlider:105`). Full width: `maxRangeBarWidth` is the width minus the borders
  (`VSRangeSliderModel:142-148`).
- **Label offsets.** Fixed distances from the track, independent of the box:

  | Distance | Browser | Export |
  | --- | --- | --- |
  | Current-value label to track top | 0 — `top: calc(sliderTop - 1em)` with `line-height: 1` (`vs-range-slider.component.html:255`) | 2 — `lineY - descent - 2` (`VSTimeSlider:172`) |
  | Min/max labels to track bottom | 1 — `rangeValueOffset = 10` against a 9px track (`.ts:112`) | 2 — `+ SLIDER_H + 2` (`:183`, `:194`) |

  At 40px the current-value label starts about 5px from the top and the min/max labels end at the bottom
  edge.
- **Title lane.** Drawn only inside a selection container (`.html:45`). For a marked slider its height
  already follows density, 30 / 26 / 20 (`getTitleHeight()`, `:430`, container density D4).
- **The lane counted twice.** In a container, the expand write stores `listHeight × defh + titleHeight`
  (`VSSelectionContainerService:231-234`), and the browser model adds the lane again when it builds the
  drawn height (`VSRangeSliderModel:127`), as export does (`CurrentSelectionVSAssembly.getDrawnHeight`,
  `:158`). The drop (`GroupingService:150-156`), the device layout (`AbstractLayout:483-487`) and the
  overflow estimate (`VSSelectionContainerService:192-194`) all treat the stored height as the body only.
  So a slider dropped in expanded has a 40px body, and after one collapse and expand it has 40 + lane.
- **Every format exports the body as one image.** `AbstractVSExporter:757` constructs `VSTimeSlider`; HTML
  embeds its image (`HTMLVSExporter.writeTimeSlider`), and so does XLSX, which lives in
  `utils/inetsoft-xml-formats` (`PoiExcelVSExporter.writeTimeSlider`, `:1411`, `writePicture` at `:1469`
  and `:1472`).

### 2.1 The precedent this spec copies — the selection list

- **Padding.** `SelectionBaseVSAssemblyInfo.seedChromeDefaults` (`:1011`) seeds
  `VSDensityDefaults.tablePadding(ctx)` (16 / 12 / 8) unless `isUserPadding()` or `isCssPaddingDefined()`;
  `defaultPadding` (`:1089`); `resetPadding(VizContext)` (`VSAssemblyInfo:1551`) backs the padding pane's
  "follow default" checkbox.
- **Size rule.** The same hook, last: `(ctx.modern || ctx.transition) && followsDensitySize()` →
  `setPixelSize(VSDensityDefaults.selectionSize(ctx))`. `followsDensitySize()` is
  `!isUserSize() && isSeededSelectionSize(getPixelSize())` (`:1063`); `isAuthorSizeChange` counts a height
  change only (`:1071`); `takesDensitySize()` is true.
- **Dialog.** `VSDialogService.readSizeFollowsDensity`, `applyDensitySize` and `recordAuthorSize`
  (`:166-221`) drive the Size & Position "follow default density" checkbox for any type whose
  `takesDensitySize()` is true. `governed` is false for a selection container's child, which hides the
  checkbox there.
- **Container.** The container takes no card inset of its own, and a child insets its own body exactly
  as it would standing alone (`vs-selection.component.ts:949-957`). A marked list is sized through
  `getContainedListHeight()` (`:160`), which is its lane plus `getListBodyHeight()` (`:150`), the rows plus
  `padding.top + padding.bottom`.

## 3. Decisions

### D1 — B1: card padding, no surface

- **Padding.** The slider takes the shared card padding, 16 / 12 / 8 px on all four edges at comfortable /
  compact / dense, from `VSDensityDefaults.tablePadding(ctx)`. It is seeded in
  `TimeSliderVSAssemblyInfo.seedChromeDefaults` under the list's guard
  (`!isUserPadding() && !isCssPaddingDefined()`), with a `defaultPadding(VizContext)` override, so
  `resetPadding` and Revert work as they do for lists. Revert writes the legacy value, zero on all edges.
- **Where it goes.** Around the whole control: current-value label, track, handles, ticks and min/max
  labels move together, so the track's ends stay lined up with the min/max labels. Inside a container the
  title lane stays flush and full width above the padded body, as a list's does.
- **Corner.** The type joins `isCornerSeedTarget()` by name. Its javadoc, which says the range slider
  "must stay out", is rewritten to say why a base-class test still must not be used (it would sweep in
  other `SelectionVSAssemblyInfo` types). The 6px corner shows wherever the slider has a border or a
  background:
  - **Standalone:** only once an author adds one, as for lists.
  - **Inside a selection container:** a dropped child gets top and bottom borders
    (`GroupingService:96-99`) and a converted slider gets all four (`initDefaultFormat(true)`), so a
    contained slider draws as a rounded box. Contained lists already do *(my call: accepted, as for lists)*.
  - **Title lane:** for a marked slider, the lane binds `titleFormat.roundCorner`, as the list header does
    (`vs-selection.component.html:61`), instead of `objectFormat.roundCorner`
    (`vs-range-slider.component.html:54`), so the lane stays square under a rounded body. An unmarked
    slider keeps today's binding *(my call)*.
  - **Ad hoc filter:** see D7.
- **No surface.** The background stays unset. Choice B2 (a white card like charts and tables) was
  declined: whether filters sit on surfaces is a look decision of its own.
- **This reverses a recorded exclusion.** The widget spec (§03) and the `isCornerSeedTarget()` javadoc
  keep the range slider out of the card set "deliberately", with no reason given. The title-lane exclusion
  had no reason either, and container density D4 reversed it on 2026-10-05.

### D2 — Label spacing: 6px either side of the track, in both renderers

- **The gaps.** The current-value label's bottom sits 6px above the track's top, and the min/max labels'
  top sits 6px below the track's bottom, in the browser and in export.
- **Browser.** `rangeValueOffset` 10 → 15 (`.ts:112`); the current-value label's top becomes
  `calc(sliderTop - 1em - 6px)` (`.html:255`).
- **Export.** `- 2` → `- 6` at `VSTimeSlider:172`; `+ 2` → `+ 6` at `:183` and `:194`; `getImageSize()`
  (`:60`) becomes `lineY + SLIDER_H + 6 + fontH`, so the enlarged image still holds the labels.
- **The same at every tier.** Track and handle sizes are outside density
  (`chart-card-open-item-decisions.md` §3), and these offsets are part of the same geometry. Only the
  padding varies by tier.
- **Marked sliders only** *(my call)*. The gate is the assembly's mark: `model.vizModern` in the browser
  (the mark, never the `.viz-shell` body class) and `VizContext.of(info).modern` in export. An unmarked
  slider keeps today's offsets, because its box stays 200×40 and 40px cannot hold two 6px gaps, and because
  legacy output must not move.
- **Not on an ad hoc filter slider** (D7).
- **Ticks.** Export draws ticks from `lineY + 9` to `lineY + 12`; the min/max text now starts at
  `lineY + 15` instead of `lineY + 11`, so they no longer overlap. The browser's ticks
  (`sliderTop + tickOffset`, `tickOffset = 5`) are unchanged.

### D3 — Content 50; the default size is content plus padding

- **Content height 50** for a marked slider. Each side of the track's middle needs its own label: above,
  4 (half the track) + 6 + about 13 for the label at the default 11pt font = 23; below, 5 + 6 + 13 = 24.
  Twice the larger is 48, and 50 leaves a pixel on each side. Today's 40 fits only because the gaps are
  0–2px.
- **Default size**, using the list's rule (box = content + padding); width is 200 + left + right:

  | | comfortable | compact | dense | legacy |
  | --- | --- | --- | --- | --- |
  | Content height (px) | 50 | 50 | 50 | 40 |
  | Default size (px) | 232×82 | 224×74 | 216×66 | 200×40 |

- **Density owns the height; the width follows only while it is a default** (user ruling, 2026-10-22,
  option 1). This is the form controls' rule from #6798 (`FormControlDensity.seedControlHeight`,
  `densitySizeIsHeightOnly()`), plus the default-width step D1's padding needs:
  - **Height** is recognized whatever the width: a slider whose height is 40, 66, 74 or 82 follows
    density and takes the tier height. A legacy slider widened to 400×40 becomes 400×82 at comfortable on
    Modernize.
  - **Width** changes only while it is one of the four default widths (200, 216, 224, 232), so a new
    slider gets the table's box. Any other width is the author's and is kept.
- **Why height-only.** Matching the whole size, as lists do, leaves a widened slider at its old height with
  the new padding inside it: 8 / 16 / 24 px for a 50px control at comfortable / compact / dense. A list
  that is too short shows fewer rows; a slider that is too short is unusable.
- **New defaults** *(names my call)*. `VSDensityDefaults.rangeSliderSize(VizContext)` returns the column
  for the context's tier, or 200×40 when not modern. `isSeededRangeSliderHeight(int)` recognizes 40, 66,
  74 and 82; `isSeededRangeSliderWidth(int)` recognizes 200, 216, 224 and 232. The 50 is the one new
  constant this work adds.
- **The size rule** runs last in `TimeSliderVSAssemblyInfo.seedChromeDefaults`, after the padding, when
  `(ctx.modern || ctx.transition) && followsDensitySize()`. It sets the height to the tier's, and the width
  to the tier's when the width is a default. `takesDensitySize()` and `densitySizeIsHeightOnly()` return
  true; `followsDensitySize()` is `!isUserSize() && isSeededRangeSliderHeight(getPixelSize().height)`;
  `defaultSize(VizContext)` is the same width rule over the tier height.
- **The cost of height-only.** Every legacy slider that is 40 tall grows on Modernize, including one an
  author set to 40 on purpose: the stored value cannot tell them apart, the same limit lists and form
  controls have.
- **Not inside a selection container** *(my call)*. The size rule skips a slider whose assembly is a
  selection container's child (found through `getViewsheet()`): the container owns its width, and D6's
  sites own its height. Without the guard, a contained slider whose stored size happens to equal a seeded
  size is rewritten to a standalone width on the next open, Modernize, Revert or density change.
- **Authorship counts height only.** It follows from `densitySizeIsHeightOnly()`: #6798's base
  `isAuthorSizeChange` then compares heights only. A slider an author only widened is not flagged and its
  height keeps following density.
- **Revert** restores a tier height to 40 and a tier width to 200, with zero padding and zero corner,
  through the transition branch. An author's width is kept.
- **Existing marked sliders** take the padding and the size on their next open, through
  `reseedAfterRestore`, as lists did (selection family design D6). An author-sized slider keeps its size.

### D4 — Density ships on its own, not inside the redraw ticket

- **User ruling, 2026-10-22.** The range slider's CSS redraw sits behind the §07 derived selection fill,
  which the roadmap still lists as needing an owner. Density changes geometry only, not colour, and it
  changes both renderers in the same change, which is all `chart-card-open-item-decisions.md` §3 requires.
- **The redraw keeps this geometry** when it comes: the padding, the 6px gaps and the 50px content. A
  one-line note goes into that decision's §3 and into the roadmap so the redraw ticket inherits it
  *(my call)*.

### D5 — Inside a selection container, the slider insets itself like a list

- **No padding from the container** (container density D1). The slider carries its own D1 padding, the
  same inside a container as outside.
- **Left ends line up by construction.** The list and the slider take their padding from the same
  `tablePadding` and are both the container's width, so the track's left end sits on the list rows' left
  edge.
- **Right ends line up too** *(my call)*. A contained list's body gives up 2px on the right, and 2 more
  while selected in the composer (`vs-selection.component.ts:1003`). A marked contained slider's track
  gives up the same constant 2px, in the browser model's `maxRangeBarWidth`. The composer-only 2px while
  selected is not mirrored: the track width comes from the server, which does not know the selection.
  Export is unchanged.
- **Body height.** A new `TimeSliderVSAssemblyInfo.getSliderBodyHeight()`: for a marked slider,
  `50 + padding.top + padding.bottom` from `getPadding()`, so an author's padding counts, as
  `getListBodyHeight()` does; for an unmarked slider, today's `listHeight × defh`.
- **Drawn height.** An expanded marked slider draws at `titleHeight + getSliderBodyHeight()`: 30 + 82 = 112
  at comfortable, 26 + 74 = 100 at compact, 20 + 66 = 86 at dense. The model and `getDrawnHeight()` already
  add the lane to the stored height; D6 makes the stored height the body.

### D6 — Store the body, not the body plus the lane

For a marked slider, every site that sets an expanded contained slider's height stores
`getSliderBodyHeight()`, with no lane:

| Site | Today | Marked slider after this change |
| --- | --- | --- |
| Expand write, `VSSelectionContainerService:231-234` | `listHeight × defh + titleHeight` | `getSliderBodyHeight()` |
| Overflow estimate for the expanding slider, `:192-194` | `listHeight × defh` | `getSliderBodyHeight()` |
| Device layout, `AbstractLayout:483-487` | `listHeight × defh` | `getSliderBodyHeight()` |
| Convert a list to a range slider, `ComposerVSSelectionListService:222-245` | 60 if the list's stored height is over 20, else 40 | `getSliderBodyHeight()` |
| Drop into a container, `GroupingService:150-156` | the standalone height (lane if hidden) | `getSliderBodyHeight()` if not hidden; lane if hidden |

- **Unmarked sliders keep today's formulas at every site**, so legacy output does not move.
- **The drop** follows the list, whose drop stores `getContainedListHeight()` for a marked list
  (`GroupingService:144`), not its standalone size *(my call)*. This also covers the return from an ad hoc
  filter: `moveAdhocFilter` (`ComposerAdhocFilterService:288`) re-groups through the drop, and today stores
  the ad hoc box's 60px as the body. Without this, one ad hoc use leaves an expanded slider with
  28 / 36 / 44 px for its 50px control until the next collapse and expand.
- **The conversion** is the user's ruling (option 1). Today it sets the size, then calls
  `initDefaultFormat(true)`. For a marked slider the height is written after that call, because the call
  seeds the padding `getSliderBodyHeight()` reads, and runs D3's size rule, which would otherwise rewrite a
  converted slider that happens to be exactly 200×40. The width stays the list's. The converted slider
  still opens expanded, as today. Converting back to a list needs nothing: it tests
  `slider height > defh`, which 82 / 74 / 66 still pass.
- **Collapsed or expanded** is already answered by `isHidden()` for a marked slider
  (`TimeSliderVSAssemblyInfo.isCollapsedInContainer`, `:529`), so a stored body never reads as collapsed.
  Collapse still stores the lane.
- **This supersedes a deferral.** `2026-10-12-density-deferred-fixes-design.md` §9 lists the contained
  slider's double count as dropped. For a marked slider it is now fixed, because B1's padding would
  otherwise stack on top of the extra lane.

### D7 — The table's ad hoc filter slider stays as it is today

- **User ruling, 2026-10-22.** The ad hoc filter slider is not covered, and a new ad hoc filter gets no
  padding.
- **Why it needs an exclusion.** An ad hoc slider is an ordinary `TimeSliderVSAssembly`. The table creates
  it through `createSelectionVSAssembly` and `initDefaultFormat()` (`ComposerAdhocFilterService:915-934`),
  which runs the D1 seed, and then sizes it 200×60, or the container's layout width × 60
(`changeAssemblyToAdhocFilter`, `:514-519`). A slider already in a
  selection container can also be pulled out as the ad hoc filter, carrying its padding. Without an
  exclusion it would draw B1's padding inside a 60px box with a title lane over it, leaving −2 / 10 / 24 px
  for the control.
- **The mechanism** *(my call)*. While `isAdhocFilter()` is set, the padding both renderers use is zero
  and the label offsets are today's: read-time, through accessors on the info, so a container slider
  pulled out as the ad hoc filter draws as today and gets its padding back when it returns. The corner is
  a stored format value, so it goes the way the ad hoc white background already goes:
  `saveFormats` saves the object format and writes a zero corner beside the white background, and
  `restoreFormats` puts the saved format back on the return.
- **Why the corner is included.** Every ad hoc slider gets a white background in its object format's
  DEFAULT tier (`ComposerAdhocFilterService.saveFormats`, `:573-574`), so a seeded 6px corner would draw it
  as a rounded white card. Excluding the corner is what "stays as it is today" requires.
- **What is still stored.** A new ad hoc slider still stores the seeded padding, read as zero while it is
  an ad hoc filter. Its corner is zero in the format `saveFormats` writes, and 6 again in the format
  `restoreFormats` puts back. Its size path (`changeAssemblyToAdhocFilter`) runs after
  the seed and overwrites the D3 size, as today.
- **The return trip is D6's.** Returning to the container goes through the drop
  (`moveAdhocFilter`, `:288` → `groupComponents`), which D6 makes store the correct body.

## 4. Model

`TimeSliderVSAssemblyInfo`:
- `defaultPadding(VizContext)` → `VSDensityDefaults.tablePadding(ctx)`.
- `seedChromeDefaults`: after the existing foreground seed, the padding seed (D1), then the size rule (D3).
- `takesDensitySize()`, `followsDensitySize()`, `isAuthorSizeChange()` (height only), and
  `defaultSize(VizContext)` → `VSDensityDefaults.rangeSliderSize(ctx)`, which is what the base
  `resetSize(VizContext)` (`VSAssemblyInfo:1499`) and the Size & Position follow checkbox use, as
  `SelectionBaseVSAssemblyInfo:1084` does for lists.
- `getSliderBodyHeight()` (D5).
- The ad hoc accessor (D7), for example `getBodyPadding()`: zero insets while `isAdhocFilter()`, otherwise
  `getPadding()`, null-safe.

`VSAssemblyInfo.isCornerSeedTarget()` gains `TimeSliderVSAssemblyInfo`, with the javadoc rewritten (D1).

`VSDensityDefaults`: `rangeSliderSize(VizContext)` and `isSeededRangeSliderSize(Dimension)` (D3).

The four container sites and the conversion (D6).

## 5. Browser

- **Model.** `VSRangeSliderModel` sends the body padding (zero for an ad hoc slider), in the
  `padding` field `VSObjectModel` already declares on the TypeScript side, populated as
  `VSSelectionBaseModel:54` does for lists. It subtracts left + right from `maxRangeBarWidth`
  (`:142-148`). The range slider is not an anchored type, so `VSObjectContainer.getLaneInset` never reads
  this padding and the mini toolbar does not move.
- **Component.** `vs-range-slider.component` wraps the body's contents in one inner box offset by the
  padding. The labels, ticks and current value are absolutely positioned, so padding on
  `.vs-range-slider-body` alone would not move them. `getBodyHeight()` (`:508`) and `setTopPositions()`
  (`:1101`) subtract top + bottom; `getLabelPosition()` (`:445`) centres against the inner width instead of
  `objectFormat.width`.
- **Label spacing** (D2), when `model.vizModern && !model.adhocFilter`.
- **Contained right edge** (D5).

## 6. Export

`VSTimeSlider`, which draws the body image for every format:
- Width and height at `:90-91` lose the padding, and the drawing translates by `padding.left`/`padding.top`.
  `getImageSize()` (`:41-65`) and `getShrinkString()` (`:204-222`) use the same inner width and height.
- **No padding in Excel** *(my call)*. Excel and CSV take no card inset for tables or selections, because
  they are cell grids (`AbstractVSExporter.insetsTableCard`, `:2011`; overridden false in
  `ExcelVSExporter:55` and `CSVVSExporter:64`). The slider follows the same predicate: the exporter tells
  `VSTimeSlider` whether to inset, so in XLSX the track stays flush, as a contained list's rows do there.
  The label spacing still applies.
- XLSX needs nothing else: `PoiExcelVSExporter.resetTimeSliderSize` (`:2882`) sizes a contained slider's
  image from its stored height, which D6 makes the body.
- The label spacing (D2), under the same gate as the browser, read through `VizContext.of(info)` and the
  ad hoc accessor.
- `VSFloatable.drawBackground` already paints any author background with its corner over the whole box,
  so the padding needs nothing there.

## 7. Dialogs

- **Padding pane.** `RangeSliderGeneralPaneModel` gains a `PaddingPaneModel` with a "follow default"
  checkbox, read and written as `SelectionGeneralPaneModel` and its dialog service do. On the portal side,
  `vsobjects/dialog/range-slider-general-pane.component.html` adds `padding-pane` as
  `composer/dialog/vs/selection-general-pane.component.html` does, and
  `vsobjects/model/range-slider-general-pane-model.ts` gains the field. Shown for every range
  slider, including an unmarked one, as for lists *(my call)*.
- **Size & Position: a "follow default density" checkbox for the height** (user request, 2026-10-22).
  `RangeSliderPropertyDialogService` (`:96-127` read, `:244-261` write) calls
  `VSDialogService.readSizeFollowsDensity` with `governed` false inside a selection container, then
  `applyDensitySize` and `recordAuthorSize`, as the list's dialog service does. Because
  `densitySizeIsHeightOnly()` is true, #6798's wiring makes it a height checkbox: the pane's
  `densityHeightOnly` keeps Width editable while it is ticked, and `applyDensitySize` keeps the width the
  author typed. Ticking it puts a slider back on its tier height, which is the fix for an author height
  that is too short for the padding (§9). Hidden for a contained slider, whose height the container owns
  (D6). The existing title-height follow checkbox for a contained slider (container density §6) is
  unchanged.
- **No script property** is added for the padding *(my call)*.

## 8. Verification

Automated (Java):
- Seed: each tier's padding and size; legacy creation unchanged; an author padding and an author size kept
  through a density change; `format.css` padding kept; Revert to 200×40 and zero padding.
- Height-only rule: a legacy 400×40 Modernized to 400×82 / 74 / 66; a 400-wide slider follows a density
  change in height only; an author height of 45 kept; Revert of a 400×82 slider to 400×40; a contained
  slider untouched by the rule.
- Size & Position: the checkbox reads ticked for a default height and null inside a container; ticking it
  restores the tier height and keeps a typed width; unticking with a changed height sets `userSize`.
- `getSliderBodyHeight()`: each tier, an author padding, unmarked.
- Each D6 site, marked and unmarked; the expand–collapse–expand round trip leaves no lane in the stored
  height; a marked slider collapsed and expanded twice is the same height both times.
- The conversion: marked list expanded and collapsed, at each tier, including one exactly 200×40;
  unmarked list unchanged (60 / 40).
- The ad hoc accessor: zero while the flag is set, the seeded padding once it clears.
- `VSTimeSlider` geometry: a marked slider's track and label positions at each tier; an unmarked slider's
  image identical to today's.

Automated (portal `*.spec.ts`):
- Inner box offset and `maxRangeBarWidth` with and without padding; label offsets 15 / `- 6px` only when
  `vizModern` and not `adhocFilter`; `getLabelPosition()` against the inner width.

Manual, on a fixture with a standalone slider, a contained slider under a list, and an ad hoc filter, at
each tier and in a legacy dashboard:
- **MC-1 legacy identical.** A legacy dashboard's exports (PDF, PNG, HTML, PPTX, XLSX) match the pre-change
  build byte for byte or pixel for pixel.
- **MC-2 standalone by eye.** The track inset by 16 / 12 / 8; labels 6px clear of the track in the viewer
  and in PDF and PNG; nothing clipped at the default size (D3's open check).
- **MC-3 container alignment.** The track's left and right ends on the list rows' edges; expand and
  collapse twice, the height stays 112 / 100 / 86.
- **MC-4 conversion.** Convert a contained list to a range slider at each tier: correct body at once.
- **MC-5 ad hoc unchanged.** A table's ad hoc filter slider looks as it does on the pre-change build, both a
  new one and one pulled out of a container; returned to the container, the slider has its padding again.
- **MC-6 Modernize, Revert, density change.** A legacy slider Modernized takes the tier size, and a
  widened one takes the tier height at its own width; Revert gives back 200×40 or the author's width × 40;
  an org density change moves a default-height slider to the new tier and leaves an author height alone.
- **MC-7 height checkbox.** Resize a slider to 45px tall: the control is cramped. Tick "follow default
  density": the height returns to the tier's and the width stays as typed. Inside a container the checkbox
  does not show.

## 9. Risks and accepted costs

- **Existing marked sliders change on open.** A standalone one at 200×40 grows to its tier size and may
  overlap what sits below it, as lists did (selection family D6).
- **Existing marked contained sliders are cramped until their next expand.** The padding arrives on open,
  but the stored body (40, or 40 + lane) changes only at the next expand. Until then the control has
  8–38 px instead of 50 at comfortable. Accepted for the same reason as D6 of the selection family: the
  branch has not shipped, so the marked population is internal.
- **An author height below content + padding** (for example 45px at comfortable, which needs 82) keeps the
  author's height with the padding inside it, so the control is cramped. Ticking the height checkbox (§7)
  or resizing fixes it. Nothing shrinks the padding to fit *(my call)*.
- **A larger author label font** no longer fits the 50px content; the author resizes, which sets
  `userSize`. Nothing fits the content to the font *(my call)*.
- **An author's resize of a contained slider** is lost on its next expand, as today: `getSliderBodyHeight()`
  has no author term, while a list keeps its size through `listHeight`.
- **XLSX** keeps shifting a standalone slider's anchor down by its title-lane height
  (`PoiExcelVSExporter.getAnchorPosition`, `utils/inetsoft-xml-formats`, `:2096-2105`; recorded in container
  density §9).
- **Depends on #6798.** `densitySizeIsHeightOnly()`, its height-only `isAuthorSizeChange`, the
  `densityHeightOnly` pane field and the Width binding all come from the form-control density PR, which is
  open on `epic-74519`. This branch stacks on it until it merges, then retargets to `epic-74519` and
  rebases, as #6979 does *(my call)*.

## 10. Out of scope and left alone

- The table's ad hoc filter slider (D7).
- The CSS redraw: colours, handle shape, the derived selection fill, hover and press (D4).
- The overflow estimate's sum of stored heights (`VSSelectionContainerService:165`) leaves out a
  slider's lane, today and after; not changed *(my call)*.
- With min/max hidden, export puts the track at 3/4 of the height (`VSTimeSlider:51`, `:105`) while the
  browser keeps it centred; not changed. The 50px content assumes min/max are visible, the default.
- The single-value slider and the selection container's own default size.

## 11. Relationship to other documents

- Reverses the range slider's exclusion from the card set in the widget spec §03 and the
  `isCornerSeedTarget()` javadoc (D1).
- Supersedes the deferral of the contained slider's double count in
  `2026-10-12-density-deferred-fixes-design.md` §9, for marked sliders (D6).
- Adds a constraint to the redraw in `chart-card-open-item-decisions.md` §3: keep this geometry (D4).
- Extends `2026-10-05-selection-container-density-design.md`: D5 and D6 here are the slider's half of its
  "each child insets itself" rule.

## 12. Calls this spec made

These were not ruled by the designer or the user; each is the default the builder follows unless changed.

1. Label spacing is gated on the mark; unmarked sliders keep today's offsets (D2).
2. The names `rangeSliderSize`, `isSeededRangeSliderHeight`, `isSeededRangeSliderWidth`,
   `getSliderBodyHeight` and the ad hoc accessor (D3, D5, D7).
3. The width follows density only while it is one of the four default widths (D3).
4. A note in the redraw decision and the roadmap carries this geometry forward (D4).
5. The contained track gives up the list's constant 2px on the right, in the browser only (D5).
6. The ad hoc exclusion is read-time for padding and spacing, and rides `saveFormats` for the corner (D7).
7. The padding pane shows for unmarked sliders too, as for lists; no script property (§7).
8. A larger author font is handled by an author resize (§9).
9. The overflow estimate is left as is (§10).
10. A contained or converted slider draws as a rounded box, as contained lists do; a marked slider's title
    lane binds the title format's radius (D1).
11. The ad hoc exclusion zeroes the corner too, because ad hoc sliders get a white background (D7).
    Superseded in part by item 6: it is done in `saveFormats`.
12. The drop stores the correct body for a marked slider, which also fixes the ad hoc return trip (D6).
13. The size rule skips a slider inside a selection container (D3).
14. Excel draws no padding, as for tables and selections (§6).
15. An author height too short for the padding stays cramped until the author ticks the height checkbox or
    resizes (§9).
16. The branch stacks on #6798 until it merges (§9).
