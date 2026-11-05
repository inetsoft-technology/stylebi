# Translucent Modern Card Default Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the modern card's 1px border and opaque background with a `#AAAAAA` tint at 15% alpha and no border. Every surface that sits on the card, or exports it, is adjusted so it stays readable.

**Architecture:**
- **Writing the tint.** The tint is a DEFAULT-tier seed, written by one new method, `VSAssemblyInfo.seedCardSurface(VizContext)`. The base `seedChromeDefaults` hook calls it, so creation, Modernize, Revert and restore all write it the same way (R1–R3).
- **Keeping it one layer.** Three read-side rules:
  - `VSCompositeFormat.getAlpha()` drops the seeded alpha when an author colour is in effect (D4).
  - The object-to-path and object-to-cell format copies skip the tint.
  - Exporters that can't show translucency substitute an opaque colour, at one place: `AbstractVSExporter.prepareAssembly`.
- **Client.** The client work is SCSS tokens and bindings, plus one composer offer that reuses the Modernize bar.

**Tech Stack:**
- Java 21 (`core`, `utils/inetsoft-xml-formats`)
- JUnit 5 with the Spring test context (`@SreeHome`)
- Angular 21 with Vitest (the portal `test` and `test-tl` targets)
- SCSS

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-23-translucent-card-default-design.md`, after Task 1 corrects it.

## Global Constraints

- **Gate on the mark.**
  - Gate every change on the per-assembly mark:
    - server: `ctx.modern` / `ctx.dark` from `VizContext.of(info)`
    - client: the `.viz-modern` / `.viz-dark` wrapper classes, or `model.vizModern`
  - Never gate on the org setting or on `body.viz-shell`.
  - Unmarked (legacy) assemblies must render pixel-identical.
- **Seeds write the DEFAULT tier only.** Never write the USER or CSS tier from a seed.
- **Card tint:** `#AAAAAA` with alpha value 15. As an AWT `Color` that is alpha 38 (`Math.round(15 * 255f / 100)`).
- **Opaque card colours** for Excel, data tips and PowerPoint's backdrop: `#FFFFFF` light, `#252428` dark.
- **Neutral values (D13).**

  | Neutral | Light | Dark |
  |---|---|---|
  | Gridline (table, chart, slider inactive track) | `#D6D2C8` | `#4A484F` |
  | Axis line | `#85827A` | `#7E7B86` |
  | Header separator and title rule | `#C7C4BB` | `#544F5A` |
  | Header band | `#DEDCD7` | `#38363C` |
  | Total band | `#D6D2C9` | `#403E46` |
  | Subtotal band | `#DBD7CF` | `#3B3940` |
  | Slider active track | `#B7B2A8` | `#544F5A` |

- **Other fixed values.**
  - Zebra stripe: `#AAAAAA` at 15% (`Color` alpha 38) in light, 5% (`Color` alpha 13) in dark.
  - Dark legend fill: `#3E3C43` at alpha 50.
  - Selection outline: `#2F7F93` in light modern, `#2DD4BF` in dark.
  - Page threshold: WCAG relative luminance below `0.179` counts as dark.
- **Code comments.** One short clause. No ticket numbers, PR numbers or design-doc names in source comments. No comments in Angular `.html` templates.
- **Java tests.**
  - Any test that touches formats, `SreeEnv` or `CSSDictionary` uses `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome` and `@Tag("core")`.
  - Copy the annotations of the existing class you extend.
  - Run every new or changed class on its own.
- **Commands,** from the worktree root:
  - Core: `./mvnw -q -pl core test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`
  - Utils: run `./mvnw -q -pl core install -DskipTests` once after changing core, then `./mvnw -q -pl utils/inetsoft-xml-formats test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`
  - Portal, from `web/`:
    - plain specs: `npx ng test portal --include='**/<name>.spec.ts'`
    - testing-library specs: `npx ng run portal:test-tl --include='**/<name>.tl.spec.ts'`
    - Never run an unscoped `test-tl`.
- **Git.**
  - One commit per task, with the message `Bug #79276, <what>` and the `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` line.
  - Run `git add` and `git commit` as separate commands, without `&&`.
  - Don't push.

## Review Focus

1. **An author's colour on a modern card renders opaque, and an author's own alpha still applies.** This covers colours from the format pane, from `format.css` and from a script. Task 3 adds `anAuthorColourDropsTheSeededAlpha` and `anAuthorAlphaStillWins`. The CSS tier shares the same predicate, so it gets checked by hand in Task 12.
2. **Nothing inside a card paints a second layer of tint.** That covers an unstyled table's cells, the chart title lane, selection list rows and a selection container's children. Tests: Task 3 `pathFormatsDoNotInheritTheTint` and `unstyledTableCellsDoNotInheritTheTint`; Task 4 `aSelectionContainerChildTakesNoTint`; Task 9 the children-layer binding tests.
3. **On a page whose `<body>` has `viz-shell`, a legacy assembly keeps its exact outline colour.** `viz-shell` means the org setting is on. Test: Task 10 `vizShellKeepsTheOldOutlineForUnmarkedAssemblies`.
4. **A bookmark saved while modern and opened after Revert shows the legacy frame and no tint.** This applies to table, crosstab, calc table and chart. Test: Task 6 `aModernBookmarkOpenedAfterRevertKeepsTheLegacyFrame`.
5. **Excel output of a modern dashboard matches today's.** Chart and table cards fill with `#FFFFFF` / `#252428`, and selection, slider and calendar cards have no fill. On a dark dashboard, no near-black text lands on a dark cell. Exporting must never change the live dashboard: `AbstractVSExporter.export` clones the sheet, because `cloneVs` defaults to true and no caller passes false. Tests: Task 8 `excelCardFillIsTodaysOutput` and `ExcelCardFillTest`.

---

### Task 1: Correct the spec against the code

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/2026-10-23-translucent-card-default-design.md`

- [ ] **Step 1: Apply these corrections.** Each one is a finding from reading the code.
  1. **Title rule (§3.1, D13).** The title rule colour is `VSTitleChromeDefaults.TITLE_BORDER` / `TITLE_BORDER_DARK`, read through `titleRuleColors(ctx)`. Retune those two constants. The base hook's title colouring is unchanged.
  2. **Slider active track (D13).** Add a row: `#C8C2B7` → `#B7B2A8` and `#49454F` → `#544F5A` (1.77 / 1.65 on the card).
     - Without this row, the dark inactive track (`#4A484F`) would sit at 1.04:1 against the shipped active track.
     - Note that the dark active-to-inactive step is 1.13, against 1.24 shipped.
  3. **D4 scope.** The seeded alpha is dropped only when the default tier holds the card tint. A legacy path that inherits an object alpha keeps it.
  4. **New §3.1 bullet.** The tint never propagates into sub-formats. These skip a default-tier tint:
     - `FormatInfo.copyDefaultFormat`
     - `VSFormatTableLens.copyDefaultFormat`
     - `VSFormatTableLens.createCellFormat`

     Without this, unstyled table cells and the chart title would double the tint.
  5. **D5 and §3.2.**
     - Membership is looked up through `VSAssemblyInfo.isInSelectionContainer()`, which moves here from `TimeSliderVSAssemblyInfo`.
     - Children skip the border-width write as well, so the 1px separators that `GroupingService` writes survive.
     - Ad hoc filters skip the card surface.
     - The drop-in path (`GroupingService.groupComponents`) calls `seedCardSurface`.
     - The move-out path already reseeds, through `initDefaultFormat`.
  6. **D8.** The calendar is seeded in `applyCalendarSeed`, for the calendar show type only. Its bypass stays, because the base hook's legacy radius is 0, not 10. `seededPrototype` therefore carries the card surface.
  7. **D17 and R4.**
     - Crosstab and calc table do carry formats in bookmark state (`<state_crosstabformat>`, `<state_calctable>`).
     - R4 is implemented once, in `AbstractVSAssembly.parseState`, and applies to every type.
  8. **D16 and §3.4.**
     - The Excel picture backing for chart, slider, range slider and calendar comes from `VSFloatable.drawBackground`. `getImageWithPadding` is never called.
     - Substitution happens once, in `AbstractVSExporter.prepareAssembly`, on the cloned sheet:
       - Excel: chart and table get the opaque card colour, and other card types get no fill. That is today's output.
       - PowerPoint: the tint is blended over the assembly's page background. This replaces the planned `PPTValueHelper` overload.
  9. **§3.3.**
     - There is a fourth backdrop: `vs-calendar.component.scss` `.pending-alert.left`.
     - The header-button backdrop is an inline style binding, and it moves to SCSS.
     - The container background is bound a third time, in `composer-selection-container-children.component.html`.
  10. **§3.3 outline.** `.viz-shell` needs its own mapping to `--inet-viz-selected-border-modern`. Unmarked assemblies on org-gated pages inherit from `body.viz-shell`.
  11. **§3.3 mark offer.** The only author path is the format pane with nothing selected (`FormatPainterService.setFormat`). The offer is a server command, `OfferVizMarkCommand`, shown in the Modernize bar.
  12. **§4.** Replace "the chart's legacy seed writes no background" with "the chart's legacy seed keeps writing `#ffffff` with alpha 100" (R2).
  13. **Line drift.** The quick-switch rule is at 281–301. `PPTValueHelper` is at 282–285 and 400–403.
- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/lookfeel/2026-10-23-translucent-card-default-design.md
git commit -m "Bug #79276, correct the translucent card spec against the code"
```

### Task 2: Table, title and slider neutrals (D13, D14)

**Files:**
- Modify:
  - `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaults.java` (getters 35–102, constants 104–121)
  - `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java` (`TITLE_BORDER` 93, `TITLE_BORDER_DARK` 98)
  - `core/src/main/java/inetsoft/uql/viewsheet/internal/VSOutputChromeDefaults.java` (103–104, 117–118)
  - `core/src/main/java/inetsoft/report/composition/execution/DataVSAQuery.java` (`applyDarkZebra` 288, its call at ≈278)
- Test:
  - `VSTableStructureDefaultsTest`, `VSTitleChromeDefaultsTest`, `VSOutputChromeDefaultsTest` (all in `core/src/test/java/inetsoft/uql/viewsheet/internal/`)
  - `core/src/test/java/inetsoft/report/composition/execution/DataVSAQueryDarkStructureTest.java`
  - `core/src/test/java/inetsoft/web/viewsheet/model/VSFormatModelTest.java` (81, 106, 129)
  - the calendar header and body cases in `SeedChromeDefaultsTest` (≈1896–1937)

**Interfaces:**
- Produces:
  - `VSTableStructureDefaults.zebraBackground(VizContext ctx)` returns `#AAAAAA` with alpha 38 in light modern and alpha 13 in dark. It is still only called for modern tables (`DataVSAQuery` ≈137).
  - `bodyBackground(VizContext)` returns `null` in both modes.
  - `DataVSAQuery.applyZebra(XTableStyle, Color)` is the renamed `applyDarkZebra`, with the same body.

- [ ] **Step 1: Update the value tests to the D13 values, and add the new cases**

Change every pinned value in the test files above to the Global Constraints values. For example, `gridlineColorValue` expects `0xD6D2C8`, and `headerSeparatorMatchesShellBorder` becomes `headerSeparatorValue` expecting `0xC7C4BB`. The `VSFormatModelTest` title rule string becomes `"1px solid #c7c4bb"`. Then add these to `VSTableStructureDefaultsTest`:

```java
@Test
void zebraIsTheTranslucentTintInBothModes() {
   SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   Color light = VSTableStructureDefaults.zebraBackground(VizContext.ofGate());
   assertEquals(0xAAAAAA, light.getRGB() & 0xFFFFFF);
   assertEquals(38, light.getAlpha());
   SreeEnv.setProperty("viewsheet.darkMode", "true");
   assertEquals(13, VSTableStructureDefaults.zebraBackground(VizContext.ofGate()).getAlpha());
}

@Test
void darkBodyIsTransparent() {
   SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   SreeEnv.setProperty("viewsheet.darkMode", "true");
   assertNull(VSTableStructureDefaults.bodyBackground(VizContext.ofGate()));
}
```

Add one more test to `DataVSAQueryDarkStructureTest`. Name it `lightModernRecoloursTheZebraSpec`: with a light modern context, `applyZebra` sets the REGULAR spec's `background` to the light stripe.

- [ ] **Step 2: Run the six classes and confirm they fail on the new values**
- [ ] **Step 3: Change the constants and getters**
  - `zebraBackground`: `ctx.dark ? ZEBRA_DARK : ZEBRA`, with alphas 13 and 38.
  - `bodyBackground`: always `null`.
  - `TITLE_BORDER(_DARK)` and the two slider constants take their new values.
  - Rename `applyDarkZebra` to `applyZebra`.
- [ ] **Step 4: Run the six classes; expect them to PASS**
- [ ] **Step 5: Commit** — `Bug #79276, retune the table, title and slider neutrals for the tinted card`

### Task 2b: Chart lines, radar spokes and the dark legend (D13, D15, D20)

**Files:**
- Modify:
  - `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java` (`resolveAxisLineColor` 75–77, `GRIDLINE` 215, `GRIDLINE_DARK` 222, `LEGEND_BG_DARK` 226)
  - `core/src/main/java/inetsoft/report/composition/graph/RadarGraphGenerator.java` (204)
- Test:
  - `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaultsTest.java`
  - `core/src/test/java/inetsoft/web/composer/model/vs/ChartLinePaneModelTest.java` (71, 75)

**Interfaces:**
- Produces:
  - `public static Color axisLineColor(VizContext ctx)` returns `#85827A` / `#7E7B86`.
  - `resolveAxisLineColor(Color current, VizContext ctx)` returns `axisLineColor(ctx)` when `ctx.modern && GDefaults.DEFAULT_LINE_COLOR.equals(current)`. Its callers don't change: `GraphGenerator` 2432 and 2667, `AxisPropertyDialogModel` 93 and 278.
  - `public static Color resolveSpokeColor(Color current, VizContext ctx)` returns `gridlineColor(ctx)` under the same condition, and `current` otherwise. `RadarGraphGenerator` 204 calls it in place of `resolveAxisLineColor`.
- Unchanged:
  - The legend border (`legendBorderColor`, `resolveLegendBorderColor`) and the facet grid keep following `GRIDLINE`.
  - The legend fill alpha stays 50.

- [ ] **Step 1: Update the pinned values and add the new cases**
  - In `VSChartChromeDefaultsTest`, the light gridline becomes `0xD6D2C8`. Every dark gridline and legend border expectation becomes `0x4A484F`. The dark axis-line resolve becomes `0x7E7B86`, and `legendBackgroundDark` becomes `0x3E3C43`.
  - `ChartLinePaneModelTest`'s `"#3a383d"` becomes `"#4a484f"`.
  - Add:

```java
@Test
void axisLineLightModern() {
   SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   assertEquals(0x85827A, rgb(VSChartChromeDefaults.resolveAxisLineColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.ofGate())));
}

@Test
void spokesKeepTheGridColour() {
   SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   assertEquals(0xD6D2C8, rgb(VSChartChromeDefaults.resolveSpokeColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.ofGate())));
   assertEquals(0x123456, rgb(VSChartChromeDefaults.resolveSpokeColor(new Color(0x123456), VizContext.ofGate())));
   assertEquals(GDefaults.DEFAULT_LINE_COLOR, VSChartChromeDefaults.resolveSpokeColor(GDefaults.DEFAULT_LINE_COLOR, VizContext.LEGACY));
}
```

- [ ] **Step 2: Run both classes; expect FAIL**
- [ ] **Step 3: Implement** the constants, `axisLineColor`, `resolveSpokeColor` and the radar call
- [ ] **Step 4: Run both classes plus `ChartVSAssemblyInfoSeedTest`; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, retune the chart lines and dark legend; radar spokes keep the grid colour`

### Task 3: Card tint API, the D4 alpha rule, and no propagation

**Files:**
- Modify:
  - `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java`
  - `core/src/main/java/inetsoft/uql/viewsheet/VSCompositeFormat.java` (`getBackground` 77–92, `getAlpha` 292–297, `getAlphaValue` 303–307)
  - `core/src/main/java/inetsoft/uql/viewsheet/FormatInfo.java` (`copyDefaultFormat`, background 309–315, alpha 326–331)
  - `core/src/main/java/inetsoft/report/composition/execution/VSFormatTableLens.java` (`createCellFormat` ≈300–367, `copyDefaultFormat` ≈699–742)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/VSCompositeFormatCardTintTest.java`, with the annotations of `VSCompositeFormatRoundCornerGateTest`
- Test:
  - `VSObjectChromeDefaultsTest`
  - `core/src/test/java/inetsoft/report/composition/execution/VSFormatTableLensTest.java`

**Interfaces:**
- Produces, in `VSObjectChromeDefaults`:
  - `public static final int CARD_TINT_RGB = 0xAAAAAA;`
  - `public static final int CARD_TINT_ALPHA = 15;`
  - `public static String cardTintValue()` returns `"#aaaaaa"`.
  - `public static Color cardBackground(VizContext ctx)` returns the opaque `CARD_BG` or `CARD_BG_DARK`.
  - `public static Color cardTintOver(Color backdrop)` returns the opaque 15% blend. A `null` backdrop counts as white.
  - `public static Color excelCardFill(VSAssemblyInfo info)` returns `cardBackground(VizContext.of(info))` for `ChartVSAssemblyInfo` and `TableDataVSAssemblyInfo`, and `null` otherwise.
- Produces, in `VSCompositeFormat`:
  - `public boolean isDefaultCardTint()` is true when the background resolves from the DEFAULT tier, that tier's background RGB is `CARD_TINT_RGB`, and its alpha value is `CARD_TINT_ALPHA`.

- [ ] **Step 1: Write the failing tests**

```java
// VSCompositeFormatCardTintTest
private static VSCompositeFormat tinted() {
   VSCompositeFormat f = new VSCompositeFormat();
   f.getDefaultFormat().setBackgroundValue(VSObjectChromeDefaults.cardTintValue());
   f.getDefaultFormat().setAlphaValue(VSObjectChromeDefaults.CARD_TINT_ALPHA);
   return f;
}
@Test void anUntouchedCardKeepsTheSeededAlpha() { assertEquals(15, tinted().getAlpha()); assertTrue(tinted().isDefaultCardTint()); }
@Test void anAuthorColourDropsTheSeededAlpha() {
   VSCompositeFormat f = tinted(); f.getUserDefinedFormat().setBackgroundValue("#336699");
   assertEquals(100, f.getAlpha()); assertEquals(100, f.getAlphaValue());
   assertEquals(255, f.getBackground().getAlpha()); assertFalse(f.isDefaultCardTint());
}
@Test void anAuthorAlphaStillWins() { VSCompositeFormat f = tinted(); f.getUserDefinedFormat().setAlphaValue(40); assertEquals(40, f.getAlpha()); }
@Test void anInheritedAlphaWithoutTheTintIsUnchanged() {
   VSCompositeFormat f = new VSCompositeFormat();
   f.getDefaultFormat().setBackgroundValue("#ffffff"); f.getDefaultFormat().setAlphaValue(50);
   f.getUserDefinedFormat().setBackgroundValue("#336699");
   assertEquals(50, f.getAlpha());
}
@Test void pathFormatsDoNotInheritTheTint() {
   FormatInfo fi = new FormatInfo(); fi.setFormat(VSAssemblyInfo.OBJECTPATH, tinted());
   VSCompositeFormat title = fi.getFormat(VSAssemblyInfo.TITLEPATH, false);
   assertNull(title.getBackground()); assertEquals(100, title.getAlpha());
}
@Test void pathFormatsStillInheritAnOpaqueObjectBackground() {
   FormatInfo fi = new FormatInfo(); VSCompositeFormat obj = new VSCompositeFormat();
   obj.getDefaultFormat().setBackgroundValue("#ffffff"); fi.setFormat(VSAssemblyInfo.OBJECTPATH, obj);
   assertEquals(0xFFFFFF, fi.getFormat(VSAssemblyInfo.TITLEPATH, false).getBackground().getRGB() & 0xFFFFFF);
}
```

- In `VSObjectChromeDefaultsTest`, add `cardTintOverTheModernPages`: `cardTintOver(#F8F7F4)` is `#ECEBE9`, `cardTintOver(#1C1B1F)` is `#313034`, and `cardTintOver(null)` is `#F2F2F2`.
- In `VSFormatTableLensTest`, add `unstyledTableCellsDoNotInheritTheTint`, built on that class's existing fixture. A table with no style and a tinted object format gives a cell format whose background is null.

- [ ] **Step 2: Run the three classes; expect FAIL** (the methods don't exist yet)
- [ ] **Step 3: Implement**
  - Add `private boolean backgroundFromDefaultTier()` to `VSCompositeFormat`: neither the user tier (`isBackgroundDefined` or `isBackgroundValueDefined`) nor `cssfmt.isBackgroundValueDefined()` holds a background. Use it in `getBackground`, `isDefaultCardTint`, and the new fourth branch of `getAlpha` and `getAlphaValue`.
  - That branch: when neither the user tier nor the CSS tier defines an alpha, and the default tier holds the card tint but the background doesn't resolve from it, return 100.
  - In both `copyDefaultFormat` methods, skip the background and alpha copies when `sfmt.isDefaultCardTint()`.
- [ ] **Step 4: Run the three classes; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, add the card tint, drop its alpha under an author colour, never copy it`

### Task 4: Seed the card surface (R1–R3, D5, D11) and the chart and table backgrounds

**Files:**
- Modify:
  - `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` (`seedChromeDefaults` 1284–1324)
  - `TimeSliderVSAssemblyInfo.java`: delete `isInSelectionContainer()` at 835 and move its body to `VSAssemblyInfo`
  - `SelectionBaseVSAssemblyInfo.java`: delete the private `isInSelectionContainer()` at 445; `isTitleOverRows` uses the inherited one
  - `TableVSAssemblyInfo`, `CalcTableVSAssemblyInfo`, `CrosstabVSAssemblyInfo`, `ChartVSAssemblyInfo`, `CurrentSelectionVSAssemblyInfo`: add the `legacyObjectBorders()` override
  - `ChartVSAssemblyInfo.seedChromeDefaults` (115–116)
  - `TableDataVSAssemblyInfo.seedChromeDefaults` (1683–1684)
  - `core/src/main/java/inetsoft/web/composer/vs/objects/controller/GroupingService.java` (after the membership change at 117–120)
- Test:
  - `SeedChromeDefaultsTest`, `VizModernizeUtilTest`, `ChromeSeedClassificationTest`, `VSObjectChromeDefaultsTest`

**Interfaces:**
- Consumes (Task 3): `cardTintValue()`, `CARD_TINT_ALPHA`, `isDefaultCardTint()`.
- Produces:
  - `public void seedCardSurface(VizContext ctx)`. The base `seedChromeDefaults` calls it last.
  - `protected Insets legacyObjectBorders()`. The base returns `null`. The five types return `THIN_LINE` on all four sides.
  - `public boolean isInSelectionContainer()`. It scans `getViewsheet()` for a `CurrentSelectionVSAssembly` that contains `getName()`, and returns false when there is no sheet.

`seedCardSurface` writes the object's DEFAULT tier as follows. It applies only to `isCornerSeedTarget()` types:

| Case | Background | Alpha | Border widths |
|---|---|---|---|
| ad hoc filter (`isAdhocFilter()` on `SelectionVSAssemblyInfo` / `TimeSliderVSAssemblyInfo`) | untouched | untouched | untouched |
| selection container child, `ctx.modern \|\| ctx.transition` | `null` | 100 | untouched |
| `ctx.modern` | `cardTintValue()` | 15 | `null` |
| legacy, `ctx.transition` | `null` | 100 | `legacyObjectBorders()` |
| legacy, not a transition | untouched | untouched | untouched |

After calling `super`, the chart and table seeds no longer write `cardBackgroundCss(ctx)` in modern. On every legacy pass they write their legacy background and alpha 100: `"#ffffff"` for the chart and `null` for the table data family. That way a stale bookmark loses its tint (R2).

- [ ] **Step 1: Write the failing tests, in `SeedChromeDefaultsTest` unless named otherwise**
  - `aModernCardTakesTheTintAndNoBorder`: for `newTable()`, `newChart()`, `newSelectionList()`, `newTimeSlider()`, `newCurrentSelection()` and a `SliderVSAssembly`, with the gate on, `objectDefault(info)` has background value `cardTintValue()`, alpha value 15 and null borders value.
  - `textImageAndGaugeTakeNoTint`: with the gate on, `TextVSAssembly`, `ImageVSAssembly` and `GaugeVSAssembly` all have `getFormat().isDefaultCardTint() == false`.
  - `aSelectionContainerChildTakesNoTint`:
    1. Create a sheet holding `CurrentSelectionVSAssembly "C"` and `SelectionListVSAssembly "L"`. Add both, run `C.setAssemblies(new String[] {"L"})`, and set L's default borders to `new Insets(1,0,1,0)`.
    2. Call `L.getVSAssemblyInfo().seedCardSurface(VizContext.ofGate())`.
    3. Expect background null, alpha 100 and borders `(1,0,1,0)`.
  - `anAdhocFilterKeepsItsWhite`: set a list's default background to `Color.WHITE.getRGB()+""`, mark it as an ad hoc filter, and call `seedCardSurface`. The background is unchanged.
  - `revertWritesTheLegacyFrameOnlyOnATransition`:
    1. Take a table created in modern.
    2. Call `seedChromeDefaults(VizContext.of((VizMark) null))`. Expect the borders to stay null.
    3. Call `seedChromeDefaults(VizContext.ofTransition(vs, null))`. Expect four `THIN_LINE` borders, a null background and alpha 100.
  - `aLegacyRestoreLeavesAStoredWidthAlone`: take a legacy table with default borders `(2,2,2,2)` and call `VizModernizeUtil.reseedAfterRestore`. The borders are unchanged.
  - `aLegacyChartKeepsItsWhiteCardAndOpaqueAlpha`.
  - `VizModernizeUtilTest`:
    - In `aModernizedThenRevertedSheetMatchesOneNeverModernized`, also compare `getBordersValue()`, `getAlphaValue()` and the assembly's `getPixelSize()`.
    - Rename `applyMarkReseedsOnALightToDarkFlip` to `aLightToDarkFlipKeepsTheTint`, and assert the same tint value and alpha before and after the flip (D2).
  - `ChromeSeedClassificationTest`:
    - Add the object default borders, background value and alpha value to `chrome()`.
    - The revert case uses `VizContext.ofTransition(vs, null)`.
    - Add `legacyObjectBordersMatchCreationForEveryType`: for every corner-target type created with the gate closed, `legacyObjectBorders()` equals the creation borders.
  - Update the pinned card-background cases listed in the research to expect the tint: `SeedChromeDefaultsTest` 729, 736, 1072 and 1092, and `VSObjectChromeDefaultsTest.chartCardSeedDark` and `chartCardSeedWhiteLightModern`.
- [ ] **Step 2: Run the four classes; expect FAIL**
- [ ] **Step 3: Implement** the three methods, the five overrides, and the chart and table background branches shown in the table. Then add one line to `GroupingService`: `object.getVSAssemblyInfo().seedCardSurface(VizContext.of(object.getVSAssemblyInfo()));`.
- [ ] **Step 4: Run the four classes, plus `BottomTabsReseedTest`, `SelectionAuthorSizeReseedTest`, `TableCardInsetSeedTest` and `SelectionCardInsetSeedTest`; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, seed the translucent card surface on Modernize, Revert and creation`

### Task 5: The calendar joins the card (D8)

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java` (`applyCalendarSeed` 154–191)
- Test: `SeedChromeDefaultsTest`, `VSObjectChromeDefaultsTest` (`calendarKeepsItsOwnRadiusInBothGateStates`), `CalendarPrototypeSwapTest` (must stay green, unchanged)

**Interfaces:**
- Consumes (Task 3): `cardTintValue()`, `CARD_TINT_ALPHA`.
- Behaviour. When `legacySource` is `normalDefault`, `applyCalendarSeed` writes the object's DEFAULT tier:
  - modern: background `cardTintValue()`, alpha 15, borders null, radius `cardCornerRadius()`
  - legacy on a transition: background null, alpha 100, four `THIN_LINE` borders, all colours `DEFAULT_BORDER_COLOR`, radius 10
  - legacy otherwise: nothing

  The dropdown prototype is never touched.

- [ ] **Step 1: Write the failing tests**
  - `aModernCalendarIsATintedCard`: tint, 15, null borders, radius 6.
  - `revertingACalendarRestoresItsLegacyFrame`: modernize a sheet with a calendar, then run `VizModernizeUtil.revert`. Expect `THIN_LINE` ×4, `#DADADA`, radius 10 and alpha 100.
  - `aDropdownCalendarStaysWhite`: the object background is `"0xffffff"` with the gate on.
  - Update `calendarKeepsItsOwnRadiusInBothGateStates` to expect 6 under the gate and 10 with it closed.
- [ ] **Step 2: Run them; expect FAIL**
- [ ] **Step 3: Implement it in `applyCalendarSeed`** (`seededPrototype` picks it up)
- [ ] **Step 4: Run `SeedChromeDefaultsTest`, `VSObjectChromeDefaultsTest` and `CalendarPrototypeSwapTest`; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, make the modern calendar a tinted card`

### Task 6: A state restore keeps the live border widths (R4)

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/AbstractVSAssembly.java` (`parseState` 641–657)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/BookmarkChromeResolutionTest.java`

**Interfaces:**
- Behaviour, in `parseState`:
  1. Before `parseStateContent(elem)`, take a copy of the live `getVSAssemblyInfo().getFormat().getDefaultFormat().getBordersValue()`. It may be null.
  2. After it, and before `reseedRestored`, write that copy into the new info's object DEFAULT tier.

- [ ] **Step 1: Write the failing tests** using the class's `writeState`, `parseXml` and `modernizeViaGate` helpers.
  - `aModernBookmarkOpenedAfterRevertKeepsTheLegacyFrame`, run once each for `TableVSAssembly`, `CrosstabVSAssembly`, `CalcTableVSAssembly` and `ChartVSAssembly`:
    1. Create the assembly and add it to the sheet.
    2. Run `initDefaultFormat`, `modernizeViaGate(vs)`, `String s = writeState(a)`, `VizModernizeUtil.revert(vs)` and `a.parseState(parseXml(s))`.
    3. Expect four `THIN_LINE` borders, `isDefaultCardTint()` false and alpha value 100.
  - `aLegacyBookmarkKeepsAStoredNonStandardWidth`: a legacy table has default borders `(2,2,2,2)`. Restoring its own state keeps `(2,2,2,2)`.
- [ ] **Step 2: Run `BookmarkChromeResolutionTest`; expect FAIL**
- [ ] **Step 3: Implement it in `parseState`**
- [ ] **Step 4: Run `BookmarkChromeResolutionTest`, `BottomTabsReseedTest` and `SeedChromeDefaultsTest`; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, keep the live border widths when a bookmark is restored`

### Task 7: Data tips and pop components get an opaque surface (D18)

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/controller/VSDataTipService.java` (≈155–165)
- Create: `core/src/test/java/inetsoft/web/viewsheet/controller/VSDataTipCardSurfaceTest.java`

**Interfaces:**
- Produces: package-private `static void opaqueCardSurface(VSAssemblyInfo info)`.
  - If `info.getFormat().isDefaultCardTint()`, it sets the user-tier background to `VSObjectChromeDefaults.cardBackground(VizContext.of(info))`.
  - `applyDataTip` calls it after `setAlpha` and before the `(245,245,249)` null check.

- [ ] **Step 1: Write the failing tests**
  - `aModernTipViewIsOpaqueWhite`: a light modern chart info, after `opaqueCardSurface`, has background RGB `0xFFFFFF`.
  - `aDarkTipViewIsOpaqueCard`: gives `0x252428`.
  - `aLegacyTipViewIsUnchanged`.
- [ ] **Step 2: Run them; expect FAIL**
- [ ] **Step 3: Implement it and call it**
- [ ] **Step 4: Run them; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, give data tips over a tinted card an opaque surface`

### Task 8: Exports substitute the tint where translucency can't show (D16)

**Files:**
- Modify:
  - `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java` (`prepareAssembly` 2008)
  - `core/src/main/java/inetsoft/web/viewsheet/service/ExcelVSExporter.java`
  - `utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/ppt/PPTVSExporter.java`
- Create:
  - `core/src/test/java/inetsoft/report/io/viewsheet/CardTintExportTest.java`
  - `utils/inetsoft-xml-formats/src/test/java/inetsoft/report/io/viewsheet/excel/ExcelCardFillTest.java`, modelled on `ExcelTableCardInsetTest`
  - `utils/inetsoft-xml-formats/src/test/java/inetsoft/report/io/viewsheet/ppt/PPTCardFillTest.java`, modelled on `PPTTextPaddingTest`

**Interfaces:**
- Consumes (Task 3): `isDefaultCardTint()`, `excelCardFill(info)`, `cardTintOver(backdrop)`.
- Produces on `AbstractVSExporter`:
  - `protected boolean substitutesCardTint()` returns false.
  - `protected Color cardTintSubstitute(VSAssemblyInfo info)` returns null.
  - When `substitutesCardTint()` is true and the object format `isDefaultCardTint()`, `prepareAssembly` writes the object DEFAULT tier: background `null` (substitute is null) or `"#rrggbb"`, and alpha 100.
- Overrides:
  - `ExcelVSExporter`: true, and `excelCardFill(info)`.
  - `PPTVSExporter`: true, and `cardTintOver(page)`. Here `page` is `info.getViewsheet().getFormatInfo().getFormat(new TableDataPath(-1, TableDataPath.OBJECT)).getBackground()`.

- [ ] **Step 1: Write the failing tests**
  - `CardTintExportTest`, which uses a same-package exporter subclass that returns `Color.RED`:
    - `aSubstitutingExporterReplacesTheDefaultTint`
    - `anAuthorColourIsNeverSubstituted`
    - `aPaintingExporterLeavesTheTint` (the base exporter)
  - In `VSObjectChromeDefaultsTest`, `excelCardFillIsTodaysOutput`:
    - a modern chart and table give `#FFFFFF` in light and `#252428` in dark
    - a selection list, time slider, calendar and selection container give `null`
  - `ExcelCardFillTest`:
    1. A probe subclass of `PoiExcelVSExporter` calls `prepareAssembly` on a modern table, in light and in dark.
    2. One cell is written through `ExcelTableHelper` with `info.getFormat()` as the parent format.
    3. `cell.getCellStyle()` fill RGB is `FFFFFF` / `252428`.
  - `PPTCardFillTest`: after a probe calls `prepareAssembly`, a modern table on a default light sheet has background `#ECEBE9` with alpha 255.
- [ ] **Step 2: Run `CardTintExportTest` and `VSObjectChromeDefaultsTest`, install core, then run the two utils tests; expect FAIL**
- [ ] **Step 3: Implement the two hooks and the two overrides**
- [ ] **Step 4: Rerun the same tests, plus `ExporterDarkOptOutTest`, `ExcelSelectionDarkOptOutTest` and `TableExportExporterSitesTest`; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, substitute the card tint in Excel and PowerPoint exports`

### Task 9: Client card surfaces: backdrops, container bindings, group border, calendar width

**Files:**
- Modify:
  - `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.html` (64–69): remove `[style.background-color]="'rgba(255,255,255,0.75)'"`
  - `vs-selection.component.scss`: header buttons 181–187, `.pending-alert.left` 205–221, `.quick-switch-overlay-btn` 281–301
  - `web/projects/portal/src/app/vsobjects/objects/calendar/vs-calendar.component.scss`: `.pending-alert.left` 265–275 and `.day-table` 165–169
  - `vs-selection-container-children.component.html` (31) and `composer/gui/vs/objects/selection/composer-selection-container-children.component.html` (28): remove the background binding
  - `vs-group-container.component.scss` (22–31), `.html` (26) and `.ts` (63–74): remove the modern border rule, the class binding and `hasAnyBorder()`
- Test: `vs-selection.component.display.tl.spec.ts`, `vs-selection-container-children.component.tl.spec.ts`, `composer-selection-container-children.component.interaction.tl.spec.ts`, `vs-group-container.spec.ts`, `month-calendar.component.display.tl.spec.ts`, `strip-glyph-tone.spec.ts`

**Interfaces:** These rules go under `:host-context(.viz-modern)`, followed by `:host-context(.viz-dark)`. Dark comes last because a dark wrapper also has `.viz-modern`.
- All four backdrops:
  - legacy keeps today's colour
  - modern uses `var(--inet-shell-surface-default)`
  - dark uses `var(--inet-viz-surface-dark)`
- Bordered elements (the quick-switch button): modern `border-color: var(--inet-default-border-color)`, dark `var(--inet-viz-hairline-dark)`.
- `.day-table` in modern: `width: 100%`, except under `.dropdown-calendar`. Legacy keeps `calc(100% - 2px)`.

- [ ] **Step 1: Write the failing tests** using the compiled-CSS pattern from `vs-selection.component.display.tl.spec.ts` 901–911.
  - `headerButtonBackdropFollowsTheMark`, `pendingAlertBackdropFollowsTheMark` and `quickSwitchBackdropFollowsTheMark`. Each matches the `.viz-modern` and `.viz-dark` selectors with the token values above, plus the legacy colour.
  - `theTemplateHasNoInlineBackdrop`: `readFileSync` the html; it does not contain `rgba(255,255,255,0.75)`.
  - Children layers (viewer and composer) render no inline `background-color`, even with `objectFormat.background` set.
  - Group container: the compiled styles contain no `.viz-modern` border rule.
  - The calendar `.day-table` modern width rule exists.
  - `strip-glyph-tone.spec.ts`: add `"rgba(170,170,170,0.15)"` to the translucent-lane loop for both `vizDark` values, and expect `achieved >= 3`.
- [ ] **Step 2: Run each file with `--include`; expect FAIL**
- [ ] **Step 3: Implement**
- [ ] **Step 4: Rerun the same files, plus `vs-selection-container.component.tl.spec.ts` and `vs-calendar.component.display.tl.spec.ts`; expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, give overlay controls the toolbar surface and paint the container card once`

### Task 10: One outline token for every selection (D19)

**Files:**
- Modify:
  - `web/projects/portal/src/scss/_viz-tokens.scss`: `:root` 22–92, then new rules after the modern block (110–126) and in the dark block (175–192)
  - Consumers that switch `var(--inet-viz-selected-border)` to `var(--inet-viz-assembly-outline)`:
    - `_themeable.scss` 113 and 133
    - `editable-object-container.component.scss` 149
    - `layout-object.component.scss` 53
    - `vs-annotation.component.scss` 101
    - `chart-tool.ts` 944 (the property name)
  - `web/projects/em/src/app/settings/presentation/presentation-themes-view/theme-css-view/theme-css-view.component.ts` (≈329)
- Create: `web/projects/portal/src/scss/assembly-outline-token.spec.ts`. It reads the SCSS sources with `readFileSync`, as `editable-object-container.component.snap.spec.ts` does.
- Test: `chart-tool-selection.spec.ts` (113, 126)

**Interfaces:**
- In `:root`:
  - `--inet-viz-assembly-outline: var(--inet-viz-selected-border);`
  - `--inet-viz-assembly-outline-modern: #2F7F93;`
  - `--inet-viz-assembly-outline-dark: #2DD4BF;`
- New rule, `.viz-shell { --inet-viz-assembly-outline: var(--inet-viz-selected-border-modern); }`. Then `.viz-modern, [data-viz-theme="v2"] { --inet-viz-assembly-outline: var(--inet-viz-assembly-outline-modern); }` after it.
- In the dark block (`.viz-dark, .viz-shell-dark`): `--inet-viz-assembly-outline: var(--inet-viz-assembly-outline-dark);`.
- EM list: add `{name: "--inet-viz-assembly-outline-modern", color: true}` after the selected-border entry.

- [ ] **Step 1: Write the failing tests**
  - `assembly-outline-token.spec.ts`:
    - `modernMapsToTheNewOutline`
    - `vizShellKeepsTheOldOutlineForUnmarkedAssemblies`
    - `darkMapsToTheDarkOutline`
    - `noOutlineConsumerReadsTheSelectedBorderToken`, a regex over the six consumer files
  - `chart-tool-selection.spec.ts`: set `--inet-viz-assembly-outline` on the host, and the stroke is that value.
- [ ] **Step 2: Run both with `--include`; expect FAIL**
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run both; expect PASS.** Also run `editable-object-container.component.snap.spec.ts` and `vs-annotation` specs, if any.
- [ ] **Step 5: Commit** — `Bug #79276, use one darker outline token for every modern selection`

### Task 11: Offer a mark switch when the page crosses the threshold (D12)

**Files:**
- Create:
  - `core/src/main/java/inetsoft/uql/viewsheet/internal/PageMarkCheck.java`
  - `core/src/main/java/inetsoft/web/composer/vs/command/OfferVizMarkCommand.java`
  - `core/src/main/java/inetsoft/web/composer/vs/event/SwitchVizMarkEvent.java`
  - `web/projects/portal/src/app/composer/gui/vs/command/offer-viz-mark-command.ts`
  - test `core/src/test/java/inetsoft/uql/viewsheet/internal/PageMarkCheckTest.java` (plain JUnit, `@Tag("core")`)
- Modify:
  - `core/src/main/java/inetsoft/web/composer/vs/controller/FormatPainterService.java` (`setFormat` 478–783: read before 622, offer after `setViewsheetInfo` 669–681)
  - `ModernizeViewsheetController.java` (44–54) and `ModernizeViewsheetService.java` (51–85)
  - `core/src/main/resources/inetsoft/util/srinter.properties` (after 3691)
  - `web/projects/portal/src/app/composer/gui/vs/editor/modernize-bar.component.ts` and `.html`, and `viewsheet-pane.component.ts` (≈1732–1766) and `.html` (37–41)
- Test: `modernize-bar.component.spec.ts`, `viewsheet-pane.component.interaction.tl.spec.ts`

**Interfaces:**
- `PageMarkCheck`:
  - `public static final double DARK_PAGE_LUMINANCE = 0.179;`
  - `public static boolean isDark(Color c)`, using WCAG relative luminance
  - `public static VizMark offer(Color before, Color after, VizMark mark)`:
    - returns `MODERN_DARK` when `mark == MODERN_LIGHT && !isDark(before) && isDark(after)`
    - returns `MODERN_LIGHT` for the mirror case
    - otherwise, or when any argument is null, returns `null`
- `OfferVizMarkCommand implements ViewsheetCommand { boolean dark; }`, with a getter and setter, like `ChangeCurrentLayoutCommand`.
- In `FormatPainterService`, when `vs` is true:
  1. Read the sheet background before and after `changeFormat`, with `formatInfo.getFormat(new TableDataPath(-1, TableDataPath.OBJECT)).getBackground()`.
  2. Compute `target = PageMarkCheck.offer(before, after, viewsheet.getVSAssemblyInfo().getVizMark())`.
  3. If `target != null`, call `commandDispatcher.sendCommand(new OfferVizMarkCommand(target == VizMark.MODERN_DARK))`.
- Controller and service:
  - `@Undoable @LoadingMask @HandleAssetExceptions @MessageMapping("composer/viewsheet/switch-mark") public void switchMark(@Payload SwitchVizMarkEvent event, Principal principal, CommandDispatcher commandDispatcher, @LinkUri String linkUri)`
  - `ModernizeViewsheetService.switchMark(String runtimeId, boolean dark, Principal principal, CommandDispatcher dispatcher, String linkUri)` follows `modernize`'s shape: the permission check, then `VizModernizeUtil.applyMark(vs, dark ? VizMark.MODERN_DARK : VizMark.MODERN_LIGHT)`, then the same refresh.
- New i18n keys, with this exact text:

  | Key | Text |
  |---|---|
  | `composer.vs.markOffer.toDark.message` | This background is dark. Switch the dashboard to dark mode so its default text stays readable? |
  | `composer.vs.markOffer.toDark.action` | Switch to dark |
  | `composer.vs.markOffer.toLight.message` | This background is light. Switch the dashboard to light mode so its default text stays readable? |
  | `composer.vs.markOffer.toLight.action` | Switch to light |

  Dismiss reuses `composer.vs.modernize.dismiss`.
- Client:
  - `ModernizeBarComponent` gets `@Input() actionLabel = "_#(js:composer.vs.modernize.action)"`, and the template renders `{{actionLabel}}`.
  - `ViewsheetPane` gets:
    - `markOffer: "dark" | "light" | null = null`
    - `processOfferVizMarkCommand(command: OfferVizMarkCommand)`
    - `switchMark()`, which sends `/events/composer/viewsheet/switch-mark` with `{dark}` and clears `markOffer`
    - `dismissMarkOffer()`
  - Template: `@if (markOffer) {…} @else if (modernizeOffered) {…}`.

- [ ] **Step 1: Write the failing tests**
  - `PageMarkCheckTest`:
    - `aLightMarkIsOfferedDarkWhenThePageGoesDark`: `#F8F7F4` → `#000000` gives `MODERN_DARK`
    - `aSecondDarkColourOffersNothing`: `#000000` → `#1A2B3C` gives null
    - `returningToTheMarksSideOffersNothing`: `#000000` → `#FFFFFF` with the light mark gives null
    - `aDarkMarkIsOfferedLightWhenThePageGoesLight`: `#1C1B1F` → `#FFFFFF` gives `MODERN_LIGHT`
    - `aLegacySheetIsNeverOffered`
    - `nullColoursOfferNothing`
    - `theThresholdIsEqualContrast`: white → `#757575` (L 0.1779) gives `MODERN_DARK`; white → `#767676` (L 0.1812) gives null
  - `modernize-bar.component.spec.ts`: a custom `actionLabel` renders in `.modernize-bar_action`.
  - `viewsheet-pane.component.interaction.tl.spec.ts`, using `mocks.dispatchCommand`:
    - `OfferVizMarkCommand {dark:true}` shows the bar with the dark action text and hides the Modernize bar
    - the action sends `/events/composer/viewsheet/switch-mark` with `{dark:true}`
    - dismiss hides the bar
- [ ] **Step 2: Run the three; expect FAIL**
- [ ] **Step 3: Implement the server side, then the client side**
- [ ] **Step 4: Run the three, plus `ModernizeViewsheetController`/`FormatPainterService` tests if any exist (search `core/src/test` for those class names); expect PASS**
- [ ] **Step 5: Commit** — `Bug #79276, offer to switch the dashboard mark when its background crosses dark or light`

### Task 12: Verify the whole change

**Files:** none, unless a check fails.

- [ ] **Step 1: Run every Java test class named in Tasks 2–8 and 11 in one Maven call:** `-Dtest=ClassA,ClassB,…`. Then run `ChromeSeedClassificationTest` and `BookmarkChromeResolutionTest` alone. Expect all to PASS.
- [ ] **Step 2: Run every portal spec named in Tasks 9–11, one `--include` per target; expect PASS**
- [ ] **Step 3: Check by hand on Census 2 and one light dashboard,** in the composer and the viewer.
  - Light page, dark mark, black page and image page.
  - A table's header, totals, zebra and gridlines, and a chart's gridlines and axis line, in light and dark. The zebra must show as a stripe: a table-style colour carries its alpha into the cell through `VSFormatTableLens.getAlpha` ≈759.
  - A `format.css` background on a card renders opaque.
  - There is no darker band behind a chart title, and no darker cells in a table whose style is set to none.
  - A list dragged into and out of a selection container: no tint inside, tint outside.
  - A radar's spokes, a calendar's width (before and after), and a data tip in light and dark.
  - Outlines on a cell, an axis, an annotation and in layout mode. Repeat on an unmarked dashboard with the org setting on; its outlines must be unchanged.
  - The offer:
    - Black on a light mark shows it once.
    - Another dark colour shows nothing.
    - Light again shows nothing.
    - Black again shows it again.
  - Excel and PowerPoint export of light and dark dashboards. HTML export of a black dashboard: check the `TYPE_INT_RGB` presenter cells (`HTMLCoordinateHelper` 64 and 1256).
- [ ] **Step 4: Record the results in the PR description.** If a check fails, add a fix task before opening the PR.
