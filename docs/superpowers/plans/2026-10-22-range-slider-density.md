# Range Slider Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A modern range slider takes the shared card padding (16 / 12 / 8), 6px label gaps and a height-only density size with a "follow default density" height checkbox; a contained slider insets itself like a selection list and stores its body only; legacy sliders and the table's ad hoc filter slider are unchanged.

**Architecture:** The values live in `VSDensityDefaults`; `TimeSliderVSAssemblyInfo` seeds the padding and runs the size rule in `seedChromeDefaults`, and answers three read-time questions (`getBodyPadding()`, `getSliderBodyHeight()`, `takesLabelSpacing()`) that every consumer reads: the five container sizing sites, the browser model and component, and the export painter `VSTimeSlider`. The height checkbox reuses #6798's `densitySizeIsHeightOnly()` wiring.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`); Angular 21 with Vitest.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-22-range-slider-density-design.md` (commit `03f5f5e235`). Its §12 lists the calls the builder follows.

## Global Constraints

- **Branch and worktree:** `bug-78994`, in the worktree at `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/bad66df6-90cc-47a5-9d4c-ac3cbe9fbad6/scratchpad/range-slider-density` (call it `$WT`). It is stacked on #6798 (`feature-form-control-density` @ `ae00b34ca6`). Never work in the shared checkout `E:/StyleBI/stylebi-enterprise/community`; other sessions use it.
- **Git:** run each git command as its own call with `git -C "$WT" ...`; never chain git commands with `&&` (a hook blocks it). Run `git -C "$WT" branch --show-current` before every commit and expect `bug-78994`. Never push; the user pushes.
- **Commit messages:** subject `Bug #78994, <what changed>`, plain prose body if needed, no blockquotes, ending with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- **Numbers (spec D1–D3, D5):** padding 16 / 12 / 8 at comfortable / compact / dense, legacy 0. Content height 50, legacy 40. Default size 232×82 / 224×74 / 216×66, legacy 200×40. Recognised heights {40, 66, 74, 82}; default widths {200, 216, 224, 232}. Label gap 6px either side of the track; today it is 0 (current value) and 1 (min/max) in the browser, 2 and 2 in export. Contained expanded drawn height 112 / 100 / 86 (lane 30 / 26 / 20 + body).
- **Gate:** the assembly's mark — `getVizMark() != null` (`VizContext.of(info).modern`) in Java, `model.vizModern` in the portal, never the `.viz-shell` body class. An unmarked slider keeps every value and position it has today.
- **Ad hoc filter** (`isAdhocFilter()`): no padding, no corner, today's label offsets.
- **Excel and CSV:** no padding (`insetsTableCard()` is false there).
- **Comments:** a short clause each. Never mention the spec, this plan, decisions (D1…), PR numbers or tickets in source code. No comments in `.html` templates.
- **New files** start with the AGPL header of `core/src/test/java/inetsoft/uql/viewsheet/internal/ControlDensitySizeTest.java` (`Copyright (C) 2026  InetSoft Technology`).
- **Spring context:** every new Java test class uses the header of `ControlDensitySizeTest` (`@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`, `@DirtiesContext(classMode = AFTER_CLASS)`, `@SreeHome`, `@Tag("core")`). Run each new or changed class alone and confirm it passes.
- **Unchanged:** the slider's colours and images (the CSS redraw), the single-value slider, the selection container's own size, the overflow estimate's sum of stored heights (`VSSelectionContainerService:165`), `VSTimeSlider`'s 3/4 track position when min/max are hidden.

## Review Focus

1. **A legacy dashboard opened in the viewer.** An unmarked slider at 200×40, and one an author made 400×55, keep their size, zero padding and today's label offsets in the viewer and in every export. Pinned in Task 3 (`aRestoreLeavesAnUnmarkedSliderAlone`), Task 7 (`keepsTodaysOffsetsWhenUnmarked`) and Task 8 (`anUnmarkedSliderPaintsWithTodaysGeometry`).
2. **A modern slider whose author zeroes the padding** and is then put in a container: its body is 50, not 82. Pinned in Task 2 (`anAuthorPaddingCountsInTheBody`).
3. **Pressing OK in the range slider dialog at comfortable without touching anything** flags nothing: `userSize` and `userPadding` stay false. Pinned in Task 9 (`anUntouchedApplyFlagsNothing`).
4. **A contained slider pulled out as the ad hoc filter and returned** draws square and unpadded while it is the filter, then returns with its padding, corner and an 82px body. Pinned in Task 4 (`aMarkedSliderDroppedIntoAContainerStoresItsBody`) and Task 5 (`anAdhocSliderIsSquareUntilItReturns`).
5. **A density change on a slider an author widened to 400** at comfortable moves it to 400×74 at compact, keeping the width. Pinned in Task 3 (`aDensityChangeMovesAWidenedSlidersHeightOnly`).

## How to run tests

Java, from `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts. `-am` builds the modules core needs without installing anything into the shared local repository:

```bash
cd "$WT"
./mvnw -q -o test -pl core -am -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
```

Several classes: `"-Dtest=A,B,C"`. Never add `install`. If `-o` fails on a missing artifact, drop `-o` once.

Frontend: Vitest cannot run from a worktree whose `node_modules` is a junction. Task 7 runs `npm ci` in `$WT/web` once, then:

```bash
cd "$WT/web"
npx ng test portal --include="**/vs-range-slider.component.spec.ts"
```

---

### Task 0: Baseline exports (the user, before any production code)

Spec §8, MC-1. No production code lands until this is done.

**Files:**
- Create (git-ignored): `$WT/.superpowers/baselines/range-slider-density/before/`

- [ ] **Step 1: Build a fixture on a pre-change server**

On a server built from code without this branch (the shared checkout's current build is fine), create four dashboards in the global folder: `RSD Legacy` (Revert applied), `RSD Modern Comfortable`, `RSD Modern Compact`, `RSD Modern Dense` (each pinning its density). Each holds:
- a range slider at the default size;
- a range slider an author widened to 400×40;
- a selection container (300 wide) holding a selection list and a range slider, the slider expanded;
- a table bound to a date column, for the ad hoc filter.

- [ ] **Step 2: Export and store**

Export each dashboard to PDF, PNG, HTML, PPTX and XLSX (match layout) into `before/`, and screenshot each in the viewer, plus the table's ad hoc filter on the date column open.

- [ ] **Step 3: Tell the implementer the folder is filled**

---

### Task 1: Density defaults for the slider's size

Spec D3.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` (beside `selectionSize`, `:259`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`

**Interfaces:**
- Produces:
  - `public static Dimension rangeSliderSize(VizContext ctx)` — the tier's default box, 200×40 when `!ctx.modern`.
  - `public static boolean isSeededRangeSliderHeight(int height)` — true for 40, 66, 74, 82.
  - `public static boolean isSeededRangeSliderWidth(int width)` — true for 200, 216, 224, 232.
  - `static final int RANGE_SLIDER_CONTENT_HEIGHT = 50;` (package-private).

- [ ] **Step 1: Write the failing tests**

```java
@Test
void rangeSliderSizeIsContentPlusPaddingAtEachTier() {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   assertEquals(new Dimension(232, 82), VSDensityDefaults.rangeSliderSize(VizContext.of(VizMark.MODERN_LIGHT)));
   SreeEnv.setProperty("viewsheet.density", "compact");
   assertEquals(new Dimension(224, 74), VSDensityDefaults.rangeSliderSize(VizContext.of(VizMark.MODERN_LIGHT)));
   SreeEnv.setProperty("viewsheet.density", "dense");
   assertEquals(new Dimension(216, 66), VSDensityDefaults.rangeSliderSize(VizContext.of(VizMark.MODERN_LIGHT)));
   assertEquals(new Dimension(200, 40), VSDensityDefaults.rangeSliderSize(VizContext.LEGACY));
}

@Test
void rangeSliderRecognisersMatchExactlyTheDefaults() {
   for(int h : new int[] { 40, 66, 74, 82 }) {
      assertTrue(VSDensityDefaults.isSeededRangeSliderHeight(h), "height " + h);
   }

   for(int w : new int[] { 200, 216, 224, 232 }) {
      assertTrue(VSDensityDefaults.isSeededRangeSliderWidth(w), "width " + w);
   }

   assertFalse(VSDensityDefaults.isSeededRangeSliderHeight(60));
   assertFalse(VSDensityDefaults.isSeededRangeSliderWidth(300));
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q -o test -pl core -am -Dtest=VSDensityDefaultsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `rangeSliderSize` not found.

- [ ] **Step 3: Implement the three methods and the constant**

Height is `inset.top + RANGE_SLIDER_CONTENT_HEIGHT + inset.bottom` and width `inset.left + 2 * AssetUtil.defw + inset.right`, from `chartPaddingForMode(mode)` (the same matrix `tablePadding` reads). The recognisers build their sets from the producer for the three tiers plus the legacy box, as `isSeededSelectionSize` does, so the two cannot drift. Javadoc each in one or two sentences.

- [ ] **Step 4: Run them to verify they pass**

Run: the Step 2 command. Expected: PASS, no other test in the class changed.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java
git -C "$WT" commit -m "Bug #78994, add the range slider's density size to VSDensityDefaults" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The slider's padding, corner and read-time body answers

Spec D1, D2 (the gate), D5 (body height), D7 (padding read-time).

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java` (`seedChromeDefaults`, `:779`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` (`isCornerSeedTarget`, `:1336`, and its javadoc above it)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/RangeSliderDensityTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.tablePadding(VizContext)`, `RANGE_SLIDER_CONTENT_HEIGHT` (Task 1).
- Produces, on `TimeSliderVSAssemblyInfo`:
  - `protected Insets defaultPadding(VizContext ctx)` → `VSDensityDefaults.tablePadding(ctx)`.
  - `public Insets getBodyPadding()` — a fresh zero `Insets` while `isAdhocFilter()`, else a clone of `getPadding()` (zero if null).
  - `public int getSliderBodyHeight()` — marked: `RANGE_SLIDER_CONTENT_HEIGHT + padding.top + padding.bottom` from `getPadding()` (null counts 0); unmarked: `getListHeight() * AssetUtil.defh`.
  - `public boolean takesLabelSpacing()` — `getVizMark() != null && !isAdhocFilter()`.

- [ ] **Step 1: Write the failing tests** in `RangeSliderDensityTest`

A helper `marked(String density)` sets `viewsheet.density`, constructs a `TimeSliderVSAssemblyInfo`, sets `VizMark.MODERN_LIGHT` and calls `initDefaultFormat()`. `@AfterEach` clears `viewsheet.density`.

```java
@ParameterizedTest
@CsvSource({ "comfortable, 16", "compact, 12", "dense, 8" })
void aMarkedSliderTakesTheCardPadding(String density, int inset) {
   assertEquals(new Insets(inset, inset, inset, inset), marked(density).getPadding());
}

@Test
void anUnmarkedSliderHasNoPadding() {
   TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
   info.setVizMark(null);
   info.initDefaultFormat();
   assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
}

@Test
void anAuthorPaddingSurvivesAReseed() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setPadding(new Insets(0, 0, 0, 0));
   info.setUserPadding(true);
   info.seedChromeDefaults(VizContext.of(info));
   assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
}

@Test
void revertWritesZeroPaddingAndNoCorner() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setVizMark(null);
   info.seedChromeDefaults(VizContext.ofTransition(null, null));
   assertEquals(new Insets(0, 0, 0, 0), info.getPadding());
   assertEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue());
}

@Test
void aMarkedSliderTakesTheCardCorner() {
   assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                marked("comfortable").getFormat().getDefaultFormat().getRoundCornerValue());
}

@ParameterizedTest
@CsvSource({ "comfortable, 82", "compact, 74", "dense, 66" })
void aMarkedSlidersBodyIsContentPlusPadding(String density, int body) {
   assertEquals(body, marked(density).getSliderBodyHeight());
}

@Test
void anAuthorPaddingCountsInTheBody() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setPadding(new Insets(0, 16, 0, 16));
   assertEquals(50, info.getSliderBodyHeight());
}

@Test
void anUnmarkedSlidersBodyIsItsListHeight() {
   TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
   info.setVizMark(null);
   info.setListHeight(2);
   assertEquals(2 * AssetUtil.defh, info.getSliderBodyHeight());
}

@Test
void anAdhocSliderReadsNoPaddingAndNoSpacing() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setAdhocFilter(true);
   assertEquals(new Insets(0, 0, 0, 0), info.getBodyPadding());
   assertFalse(info.takesLabelSpacing());
   info.setAdhocFilter(false);
   assertEquals(new Insets(16, 16, 16, 16), info.getBodyPadding());
   assertTrue(info.takesLabelSpacing());
}
```

Use the accessor `SeedChromeDefaultsTest` uses for the stored round-corner value if `getRoundCornerValue()` is named differently.

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q -o test -pl core -am -Dtest=RangeSliderDensityTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure on `getSliderBodyHeight` / `getBodyPadding` / `takesLabelSpacing`.

- [ ] **Step 3: Implement**

- `seedChromeDefaults`: after the existing foreground seed, `if(!isUserPadding() && !isCssPaddingDefined()) setPadding(VSDensityDefaults.tablePadding(ctx));` — the list's guard (`SelectionBaseVSAssemblyInfo:1011-1040`).
- `defaultPadding`, `getBodyPadding`, `getSliderBodyHeight`, `takesLabelSpacing` as in Interfaces.
- `isCornerSeedTarget()`: add `|| this instanceof TimeSliderVSAssemblyInfo`. Rewrite the javadoc's "TimeSliderVSAssemblyInfo extends SelectionVSAssemblyInfo and must stay out" to say it is listed by name because a base-class test would sweep in the other `SelectionVSAssemblyInfo` types.

- [ ] **Step 4: Run them to verify they pass, then the neighbours**

Run: `"-Dtest=RangeSliderDensityTest,SeedChromeDefaultsTest,RangeSliderTitleLaneTest,TitleLaneHeightRowTest,VSObjectChromeDefaultsTest"` with the Step 2 command.
Expected: all PASS. If a neighbour asserted the range slider takes no corner, update that one assertion to the card corner and say so in the commit body.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/RangeSliderDensityTest.java
git -C "$WT" commit -m "Bug #78994, give the modern range slider the card padding and corner" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: The height-only size rule

Spec D3.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/RangeSliderDensityTest.java`

**Interfaces:**
- Consumes: `rangeSliderSize`, `isSeededRangeSliderHeight`, `isSeededRangeSliderWidth` (Task 1); #6798's `densitySizeIsHeightOnly()` and `resetSize(VizContext)` (`VSAssemblyInfo:1476`, `:1499`).
- Produces, on `TimeSliderVSAssemblyInfo`: `takesDensitySize()` → true; `densitySizeIsHeightOnly()` → true; `followsDensitySize()` → `!isUserSize() && isSeededRangeSliderHeight(getPixelSize().height)`; `protected Dimension defaultSize(VizContext ctx)`; `private boolean isInSelectionContainer()`.

- [ ] **Step 1: Write the failing tests**

```java
@ParameterizedTest
@CsvSource({ "comfortable, 232, 82", "compact, 224, 74", "dense, 216, 66" })
void aNewMarkedSliderTakesTheTierBox(String density, int w, int h) {
   assertEquals(new Dimension(w, h), marked(density).getPixelSize());
}

@Test
void modernizingAWidenedLegacySliderTakesTheTierHeightAtItsWidth() {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
   info.setVizMark(null);
   info.setPixelSize(new Dimension(400, 40));
   info.setVizMark(VizMark.MODERN_LIGHT);
   info.seedChromeDefaults(VizContext.ofTransition(null, VizMark.MODERN_LIGHT));
   assertEquals(new Dimension(400, 82), info.getPixelSize());
}

@Test
void aDensityChangeMovesAWidenedSlidersHeightOnly() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setPixelSize(new Dimension(400, 82));
   SreeEnv.setProperty("viewsheet.density", "compact");
   info.seedChromeDefaults(VizContext.of(info));
   assertEquals(new Dimension(400, 74), info.getPixelSize());
}

@Test
void anAuthorHeightIsKept() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setPixelSize(new Dimension(232, 45));
   info.seedChromeDefaults(VizContext.of(info));
   assertEquals(45, info.getPixelSize().height);
}

@Test
void aFlaggedSizeIsKept() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setUserSize(true);
   SreeEnv.setProperty("viewsheet.density", "dense");
   info.seedChromeDefaults(VizContext.of(info));
   assertEquals(new Dimension(232, 82), info.getPixelSize());
}

@Test
void revertReturnsTheLegacyHeightAndKeepsAnAuthorWidth() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setPixelSize(new Dimension(400, 82));
   info.setVizMark(null);
   info.seedChromeDefaults(VizContext.ofTransition(null, null));
   assertEquals(new Dimension(400, 40), info.getPixelSize());
}

@Test
void aRestoreLeavesAnUnmarkedSliderAlone() {
   TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
   info.setVizMark(null);
   info.setPixelSize(new Dimension(400, 55));
   info.seedChromeDefaults(VizContext.of((VizMark) null));
   assertEquals(new Dimension(400, 55), info.getPixelSize());
}

@Test
void resetSizeWritesTheTierHeightAndKeepsAnAuthorWidth() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   info.setPixelSize(new Dimension(400, 45));
   info.setUserSize(true);
   info.resetSize(VizContext.of(info));
   assertEquals(new Dimension(400, 82), info.getPixelSize());
   assertFalse(info.isUserSize());
}

@Test
void onlyAHeightChangeIsTheAuthors() {
   TimeSliderVSAssemblyInfo info = marked("comfortable");
   assertFalse(info.isAuthorSizeChange(new Dimension(232, 82), new Dimension(400, 82)));
   assertTrue(info.isAuthorSizeChange(new Dimension(232, 82), new Dimension(232, 90)));
}

@Test
void aContainedSliderIsNotResized() {
   Viewsheet vs = new Viewsheet();
   vs.getViewsheetInfo().setVizDensity("comfortable");
   CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
   vs.addAssembly(container);
   container.setAssemblies(new String[] { "RangeSlider1" });
   TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "RangeSlider1");
   vs.addAssembly(slider);
   TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
   info.setVizMark(VizMark.MODERN_LIGHT);
   info.setPixelSize(new Dimension(232, 40));
   info.seedChromeDefaults(VizContext.of(info));
   assertEquals(new Dimension(232, 40), info.getPixelSize());
   assertEquals(new Insets(16, 16, 16, 16), info.getPadding(), "the padding still seeds");
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q -o test -pl core -am -Dtest=RangeSliderDensityTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: the new tests FAIL (sizes stay 200×40 / 400×40); Task 2's tests still PASS.

- [ ] **Step 3: Implement**

`defaultSize(ctx)`: the tier height, and the tier width only when the current width is a default width:

```java
Dimension size = getPixelSize();
Dimension tier = VSDensityDefaults.rangeSliderSize(ctx);
int width = VSDensityDefaults.isSeededRangeSliderWidth(size.width) ? tier.width : size.width;
return new Dimension(width, tier.height);
```

`isInSelectionContainer()`: true when `getViewsheet()` holds a `CurrentSelectionVSAssembly` whose `getAssemblies()` contains `getName()`. Last in `seedChromeDefaults`, after the padding: `if(!isInSelectionContainer() && (ctx.modern || ctx.transition) && followsDensitySize()) setPixelSize(defaultSize(ctx));`. Plus the four overrides in Interfaces. A short comment on the guard: the container owns a child's width and height.

- [ ] **Step 4: Run them to verify they pass, then the neighbours**

Run: `"-Dtest=RangeSliderDensityTest,SeedChromeDefaultsTest,RangeSliderTitleLaneTest,ControlDensitySizeTest"` with the Step 2 command.
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/RangeSliderDensityTest.java
git -C "$WT" commit -m "Bug #78994, size the modern range slider by density, height only" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: The container stores a marked slider's body

Spec D6. Five sites; unmarked sliders keep today's formula at each.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSSelectionContainerService.java` (overflow estimate `:189-195`, expand write `:229-237`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/vslayout/AbstractLayout.java` (`:482-487`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/GroupingService.java` (`:150-156`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerVSSelectionListService.java` (`createTimeSliderVSAssembly`, `:222-245`)
- Test: `core/src/test/java/inetsoft/web/viewsheet/service/VSSelectionContainerServiceTest.java`, `core/src/test/java/inetsoft/uql/viewsheet/vslayout/AbstractLayoutTest.java`, `core/src/test/java/inetsoft/web/composer/vs/objects/controller/GroupingServiceTest.java`, `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerVSSelectionListServiceVizMarkTest.java`

**Interfaces:**
- Consumes: `TimeSliderVSAssemblyInfo.getSliderBodyHeight()` (Task 2).

- [ ] **Step 1: Update and add the container service tests**

In `VSSelectionContainerServiceTest.slider(...)`, call `info.initDefaultFormat()` right after `info.setVizMark(mark)`, so a marked slider carries its seeded padding as in production. Then:
- `aMarkedSliderCollapsedAtTheOldLaneStillExpands`: expect 82 (was `2 * AssetUtil.defh + 30`).
- `anAuthorLaneStillExpands`: expect 82 and `assertEquals(25, info.getTitleHeight())`, message "the pinned lane is kept, and not stored".
- `aRevertedSliderCollapsedAtTheTierLaneStillExpands`: unchanged (60).
- New:

```java
@ParameterizedTest
@CsvSource({ "comfortable, 82", "compact, 74", "dense, 66" })
void expandingAMarkedSliderStoresItsBody(String density, int body) throws Exception {
   vs.getViewsheetInfo().setVizDensity(density);
   container(VizMark.MODERN_LIGHT, 800, "RangeSlider1");
   TimeSliderVSAssemblyInfo info = slider("RangeSlider1", VizMark.MODERN_LIGHT, true, 30);

   service.applySelection(rvs, "RangeSlider1", false, dispatcher, "");

   assertEquals(body, info.getPixelSize().height);
}

@Test
void expandingTwiceStoresTheSameBody() throws Exception {
   container(VizMark.MODERN_LIGHT, 800, "RangeSlider1");
   TimeSliderVSAssemblyInfo info = slider("RangeSlider1", VizMark.MODERN_LIGHT, true, 30);

   service.applySelection(rvs, "RangeSlider1", false, dispatcher, "");
   service.applySelection(rvs, "RangeSlider1", true, dispatcher, "");
   service.applySelection(rvs, "RangeSlider1", false, dispatcher, "");

   assertEquals(82, info.getPixelSize().height);
}

@Test
void expandingAMarkedSliderCollapsesASiblingItsBodyWouldOverflow() throws Exception { ... }
```

For the overflow test, size the container so the estimate (content height `size.height - 40`, `:145`) overflows with an 82px body and not with a 40px one: an open marked list sibling at a stored height chosen for that, the slider hidden at 30. Assert the list collapses to its lane (30).

- [ ] **Step 2: Add the layout, drop and conversion tests**

- `AbstractLayoutTest.aMarkedExpandedSliderTakesItsBodyInADeviceLayout`: a marked, seeded, expanded contained slider at comfortable gets a layout child height of 82; the existing `aMarkedCollapsedSliderKeepsItsHeightInADeviceLayout` stays.
- `GroupingServiceTest`:
  - `aMarkedSliderDroppedIntoAContainerStoresItsBody`: a marked comfortable slider at 400×60 dropped in → height 82.
  - `aHiddenMarkedSliderDroppedIntoAContainerKeepsItsLane`: hidden → height 30.
  - `anUnmarkedSliderDroppedIntoAContainerKeepsItsHeight`: 60 stays 60.
- `ComposerVSSelectionListServiceVizMarkTest`:
  - `convertingAMarkedListStoresTheSlidersBody`: a marked comfortable list 300 wide → slider 300×82.
  - `convertingAMarked200WideCollapsedListKeepsItsWidth`: a marked comfortable list at 200×20 → slider 200×82, not 232.
  - `convertingAnUnmarkedListKeepsTheLegacyHeight`: expanded → 60, collapsed (height 20) → 40.

Build each fixture the way its class already builds lists and sliders.

- [ ] **Step 3: Run them to verify the new expectations fail**

Run: `"-Dtest=VSSelectionContainerServiceTest,AbstractLayoutTest,GroupingServiceTest,ComposerVSSelectionListServiceVizMarkTest"` with the Task 1 command.
Expected: the changed and new slider tests FAIL with today's heights (70, 40, 60); the rest PASS.

- [ ] **Step 4: Implement the five sites**

Each takes `getSliderBodyHeight()` when `info.getVizMark() != null`, and keeps today's expression otherwise:
- Expand write: `size.height = marked ? info.getSliderBodyHeight() : info.getListHeight() * AssetUtil.defh + info.getTitleHeight();`
- Overflow estimate for the expanding slider: `totalHeight += marked ? info.getSliderBodyHeight() : info.getListHeight() * AssetUtil.defh;`
- Device layout: a marked slider that is not collapsed sets `childSize.height = info.getSliderBodyHeight()`; others as today.
- Drop: a marked slider that is not hidden stores `getSliderBodyHeight()`; hidden stores the lane as today; unmarked as today.
- Conversion: after `info.initDefaultFormat(true)`, a marked slider gets `setPixelSize(new Dimension(list.getPixelSize().width, info.getSliderBodyHeight()))`. It must come after that call, which seeds the padding the body reads.

- [ ] **Step 5: Run them to verify they pass, then the export readers**

Run: `"-Dtest=VSSelectionContainerServiceTest,AbstractLayoutTest,GroupingServiceTest,ComposerVSSelectionListServiceVizMarkTest,SelectionContainerChildExportTest,ContainerChildTopTest,RangeSliderTitleHeightDialogTest"`.
Expected: all PASS. The two export classes read stored heights through `getDrawnHeight()`; if one pinned a marked slider's old lane-included height, update it to the body and say so in the commit body.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/viewsheet/service/VSSelectionContainerService.java core/src/main/java/inetsoft/uql/viewsheet/vslayout/AbstractLayout.java core/src/main/java/inetsoft/web/composer/vs/objects/controller/GroupingService.java core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerVSSelectionListService.java core/src/test/java/inetsoft/web/viewsheet/service/VSSelectionContainerServiceTest.java core/src/test/java/inetsoft/uql/viewsheet/vslayout/AbstractLayoutTest.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/GroupingServiceTest.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerVSSelectionListServiceVizMarkTest.java
git -C "$WT" commit -m "Bug #78994, store a modern contained range slider's body without its title lane" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: The ad hoc filter slider stays square

Spec D7. Its padding and spacing are already read-time (Task 2); this task covers the corner, which is a stored format value.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerAdhocFilterService.java` (`saveFormats`, `:552-580`)
- Test: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerAdhocFilterServiceTest.java`

- [ ] **Step 1: Write the failing test**

`anAdhocSliderIsSquareUntilItReturns`: a marked, seeded slider (round corner 6) goes through the service's ad hoc entry point used by this class's other tests; while it is the filter its object format's DEFAULT round corner is 0; after `moveAdhocFilter` back into its container (which calls `restoreFormats`) it is 6 again. If driving the public path needs more mocks than the class already has, call the private `saveFormats` / `restoreFormats` through reflection, as a last resort.

- [ ] **Step 2: Run it to verify it fails**

Run: `-Dtest=ComposerAdhocFilterServiceTest` with the Task 1 command. Expected: FAIL, corner 6 while ad hoc.

- [ ] **Step 3: Implement**

In `saveFormats`, beside the white background, a `TimeSliderVSAssembly` also gets `objFormat.getDefaultFormat().setRoundCornerValue(0)`. `restoreFormats` already puts the saved object format back, so nothing else changes.

- [ ] **Step 4: Run it to verify it passes**

Run: the Step 2 command. Expected: PASS, the whole class.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerAdhocFilterService.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerAdhocFilterServiceTest.java
git -C "$WT" commit -m "Bug #78994, keep the table's ad hoc filter slider square" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: The browser model sends the padding

Spec §5 (model), D5 (right edge).

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSRangeSliderModel.java`
- Create: `core/src/test/java/inetsoft/web/viewsheet/model/VSRangeSliderModelDensityTest.java`

**Interfaces:**
- Consumes: `getBodyPadding()` (Task 2).
- Produces: `public Insets getPadding()` on `VSRangeSliderModel`, serialized as `padding: { top, left, bottom, right }`, which the portal's `VSObjectModel.padding?: TablePadding` already declares.

- [ ] **Step 1: Write the failing tests**

Build the model as `VSTabModelTest` does (a `Viewsheet`, the assembly added, a mocked `RuntimeViewsheet` returning it). Give the slider a selection list with a few values if the constructor needs labels.

```java
@Test
void aMarkedSliderSendsItsPaddingAndAnInsetTrack() {
   VSRangeSliderModel model = model(marked("comfortable", 300, 82), false);
   assertEquals(new Insets(16, 16, 16, 16), model.getPadding());
   assertEquals(300 - 32, model.getMaxRangeBarWidth(), 0.01);
}

@Test
void aMarkedContainedSliderGivesUpTheListsTwoPixels() {
   VSRangeSliderModel model = model(marked("comfortable", 300, 82), true);
   assertEquals(300 - 32 - 2, model.getMaxRangeBarWidth(), 0.01);
}

@Test
void anUnmarkedSliderSendsNoPaddingAndTodaysTrack() {
   VSRangeSliderModel model = model(unmarked(200, 40), false);
   assertEquals(new Insets(0, 0, 0, 0), model.getPadding());
   assertEquals(200, model.getMaxRangeBarWidth(), 0.01);
}

@Test
void anAdhocSliderSendsNoPadding() { ... getBodyPadding() is zero, track 200 ... }
```

`model(info, contained)` puts the slider in a `CurrentSelectionVSAssembly` when `contained`; the container's own side borders are subtracted as today, so pick a container with no object borders or subtract them in the expectation.

- [ ] **Step 2: Run them to verify they fail**

Run: `-Dtest=VSRangeSliderModelDensityTest`. Expected: compilation failure on `getPadding()`.

- [ ] **Step 3: Implement**

`padding = assemblyInfo.getBodyPadding()` in the constructor, with a getter, as `VSSelectionBaseModel:54` and `:174` do. After the border subtraction at `:142-148`, `maxRangeBarWidth -= padding.left + padding.right`, and for a marked slider in a `CurrentSelectionVSAssembly` a further 2.

- [ ] **Step 4: Run them to verify they pass**

Run: `"-Dtest=VSRangeSliderModelDensityTest,VSObjectModelTest,VSTabModelTest"`. Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/viewsheet/model/VSRangeSliderModel.java core/src/test/java/inetsoft/web/viewsheet/model/VSRangeSliderModelDensityTest.java
git -C "$WT" commit -m "Bug #78994, send the range slider's body padding to the browser" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: The browser draws the padding and the label gaps

Spec §5, D1 (title lane corner), D2.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.ts` (`rangeValueOffset` `:112`, `getLabelPosition` `:445`, `getBodyHeight` `:508`, `setTopPositions` `:1101`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.html` (header `:54`, body `:150-260`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.scss`
- Test: `web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.spec.ts`

**Interfaces:**
- Consumes: `model.padding` (Task 6), `model.vizModern`, `model.adhocFilter`.
- Produces, on `VSRangeSlider`:
  - `get bodyInset(): TablePadding` — `model.padding` or all zero.
  - `get rangeValueOffset(): number` — 15 when spaced, else 10 (replaces the `readonly` field).
  - `get currentValueGap(): number` — 6 when spaced, else 0.
  - spaced = `model.vizModern && !model.adhocFilter`.

- [ ] **Step 0: Install the worktree's packages**

Run: `cd "$WT/web" && npm ci`. Expected: completes; `node_modules` is a real folder, not a junction.

- [ ] **Step 1: Write the failing tests** in a new `describe("density")` block, built with `createVSRangeSlider` from the test helpers

```ts
it("keeps today's offsets when unmarked", () => {
   const { comp } = createVSRangeSlider({ vizModern: false });
   expect(comp.rangeValueOffset).toBe(10);
   expect(comp.currentValueGap).toBe(0);
});

it("spaces the labels 6px from the track when marked", () => {
   const { comp } = createVSRangeSlider({ vizModern: true });
   expect(comp.rangeValueOffset).toBe(15);
   expect(comp.currentValueGap).toBe(6);
});

it("keeps today's offsets on an ad hoc filter", () => {
   const { comp } = createVSRangeSlider({ vizModern: true, adhocFilter: true });
   expect(comp.rangeValueOffset).toBe(10);
});

it("centres the track inside the padding", () => {
   const { comp } = createVSRangeSlider({
      vizModern: true, padding: { top: 16, left: 16, bottom: 16, right: 16 },
      objectFormat: { ...TestUtils.createMockVSFormatModel(), width: 232, height: 82 } as any,
   });
   expect(comp.sliderTop).toBe(Math.ceil(50 / 2) - 4);
});

it("centres the current label against the inner width", () => { ... });
```

Match `createVSRangeSlider`'s return shape and how it triggers `setTopPositions` (existing tests read `sliderTop` after creation). For the label position, assert `getLabelPosition()` with padding equals the unpadded result for an inner width of `232 - 32`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd "$WT/web" && npx ng test portal --include="**/vs-range-slider.component.spec.ts"`
Expected: the new tests FAIL; the existing ones PASS.

- [ ] **Step 3: Implement**

- The getters in Interfaces. `setTopPositions()` subtracts `bodyInset.top + bodyInset.bottom` from `sliderHeight`. `getLabelPosition()` uses `objectFormat.width - bodyInset.left - bodyInset.right`.
- Template: wrap the body's contents (`.range-slider`, `.range-line`, both `.range-value`, `.current-value-wrapper`, the ticks) in one `<div class="vs-range-slider-inset">` bound to `[style.left.px]="bodyInset.left"`, `[style.top.px]="bodyInset.top"`, `[style.width.px]` and `[style.height.px]` as the body's minus the insets. The current value's top becomes `'calc(' + sliderTop + 'px - 1em - ' + currentValueGap + 'px)'`. The header's `[style.border-radius.px]` becomes `model.vizModern ? model.titleFormat.roundCorner : model.objectFormat.roundCorner`. No comments in the template.
- SCSS: `.vs-range-slider-inset { position: absolute; }`.

- [ ] **Step 4: Run them to verify they pass, then the slider's other specs**

Run: the Step 2 command, then `npx ng test portal --include="**/range-slider/*.spec.ts"`.
Expected: all PASS. Do not run the `*.tl.spec.ts` suite unscoped.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.ts web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.html web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.scss web/projects/portal/src/app/vsobjects/objects/range-slider/vs-range-slider.component.spec.ts
git -C "$WT" commit -m "Bug #78994, draw the range slider's padding and label gaps in the browser" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Export draws the padding and the label gaps

Spec §6, D2.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/gui/viewsheet/VSTimeSlider.java` (`getImageSize` `:41-65`, `paintComponent` `:90-106`, label lines `:172`, `:183`, `:194`, `getShrinkString` `:204-222`)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java` (`:757`)
- Create: `core/src/test/java/inetsoft/report/gui/viewsheet/VSTimeSliderDensityTest.java`

**Interfaces:**
- Consumes: `getBodyPadding()`, `takesLabelSpacing()` (Task 2); `insetsTableCard()` (`AbstractVSExporter:2011`).
- Produces, on `VSTimeSlider`: `public void setInsetsCard(boolean insetsCard)` (default true); package-private `Insets getBodyInset()` (zero when `!insetsCard`, else `info.getBodyPadding()`) and `int getLabelGap()` (6 when `info.takesLabelSpacing()`, else 2).

- [ ] **Step 1: Write the failing tests**

```java
@Test
void aMarkedSliderInsetsItsBodyAndSpacesItsLabels() {
   VSTimeSlider painter = painter(marked("comfortable"));
   assertEquals(new Insets(16, 16, 16, 16), painter.getBodyInset());
   assertEquals(6, painter.getLabelGap());
}

@Test
void excelDrawsNoInset() {
   VSTimeSlider painter = painter(marked("comfortable"));
   painter.setInsetsCard(false);
   assertEquals(new Insets(0, 0, 0, 0), painter.getBodyInset());
   assertEquals(6, painter.getLabelGap(), "the spacing still applies");
}

@Test
void anUnmarkedSliderPaintsWithTodaysGeometry() {
   VSTimeSlider painter = painter(unmarked());
   assertEquals(new Insets(0, 0, 0, 0), painter.getBodyInset());
   assertEquals(2, painter.getLabelGap());
}

@Test
void anAdhocSliderPaintsWithTodaysGeometry() { ... inset zero, gap 2 ... }

@Test
void aMarkedImageFitsItsBox() {
   // the lower labels end inside the bottom padding, so the image is not enlarged
   assertEquals(new Dimension(232, 82), painter(marked("comfortable")).getImageSize());
}

@Test
void paintingAMarkedSliderDoesNotThrow() {
   // paint into a BufferedImage at 232x82 and at 400x45, the second too short for its padding
}
```

`painter(info)` builds `new VSTimeSlider(vs)`, then `setViewsheet`, `setTheme`, `setAssemblyInfo` as `AbstractVSExporter.getImage` does (`:775-781`), with the slider added to `vs`.

- [ ] **Step 2: Run them to verify they fail**

Run: `-Dtest=VSTimeSliderDensityTest`. Expected: compilation failure.

- [ ] **Step 3: Implement**

- `paintComponent` and `getImageSize`: `w` and `h` lose `inset.left + inset.right` and `inset.top + inset.bottom`; the drawing translates by `getContentX() + inset.left`, `getContentY() + inset.top`. `getShrinkString` uses the same inner width.
- The three `2`s at `:172`, `:183`, `:194` become `getLabelGap()`; `getImageSize`'s bottom becomes `lineY + SLIDER_H + getLabelGap() + fontH`, plus the top inset.
- `AbstractVSExporter:757`: `obj = new VSTimeSlider(vs); ((VSTimeSlider) obj).setInsetsCard(insetsTableCard());`.

- [ ] **Step 4: Run them to verify they pass, then the export suites**

Run: `"-Dtest=VSTimeSliderDensityTest,SelectionContainerChildExportTest,ContainerChildTopTest,SelectionExportInsetTest,HTMLHiddenContainerTitleTest"`. Then the XLSX module: `./mvnw -q -o test -pl core,utils/inetsoft-xml-formats -am "-Dtest=*Excel*" -Dsurefire.failIfNoSpecifiedTests=false`.
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/report/gui/viewsheet/VSTimeSlider.java core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java core/src/test/java/inetsoft/report/gui/viewsheet/VSTimeSliderDensityTest.java
git -C "$WT" commit -m "Bug #78994, draw the range slider's padding and label gaps in export" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: The padding pane and the height checkbox

Spec §7.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/RangeSliderGeneralPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/RangeSliderPropertyDialogService.java` (read `:90-130`, write `:236-275`)
- Modify: `web/projects/portal/src/app/vsobjects/model/range-slider-general-pane-model.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/range-slider-general-pane.component.html`
- Create: `core/src/test/java/inetsoft/web/composer/vs/dialog/RangeSliderDensityDialogTest.java`

**Interfaces:**
- Consumes: `VSDialogService.readSizeFollowsDensity`, `applyDensitySize`, `recordAuthorSize` (`VSDialogService:166-221`); `resetPadding(VizContext)`; Task 3's overrides.
- Produces: `RangeSliderGeneralPaneModel.getPaddingPaneModel()` / `setPaddingPaneModel(PaddingPaneModel)`, field initialised to `new PaddingPaneModel()`; TS `paddingPaneModel: PaddingPaneModel` on `RangeSliderGeneralPaneModel`.

- [ ] **Step 1: Write the failing tests**

Build the service and fixtures as `RangeSliderTitleHeightDialogTest` does.

- `readOffersTheHeightCheckboxOnAStandaloneMarkedSlider`: `getSizeFollowsDensity()` is `TRUE` and `getDensityHeightOnly()` is `TRUE`.
- `readHidesTheHeightCheckboxInAContainer`: `getSizeFollowsDensity()` is `null`.
- `readShowsThePaddingAndItsFollowCheckbox`: padding 16 on each edge, `getFollowsDefault()` `TRUE`; null for an unmarked slider.
- `tickingTheHeightCheckboxRestoresTheTierHeightAndKeepsATypedWidth`: stored 400×45 with `userSize`, ticked, width typed 500 → 500×82, `userSize` false.
- `anUntouchedApplyFlagsNothing`: read then write unchanged at comfortable → `isUserSize()` and `isUserPadding()` both false, size 232×82, padding 16.
- `anAuthorPaddingIsStored`: unticked, padding 0 typed → padding 0, `isUserPadding()` true.

- [ ] **Step 2: Run them to verify they fail**

Run: `-Dtest=RangeSliderDensityDialogTest`. Expected: FAIL / compilation failure on `getPaddingPaneModel`.

- [ ] **Step 3: Implement**

- Read: the padding pane exactly as `SelectionListPropertyDialogService:150-159`; then `VSDialogService.readSizeFollowsDensity(info, sizePositionPaneModel, !inSelectionContainer)`.
- Write: the size as `SelectionListPropertyDialogService:248-254` (capture `shownSize`, `applyDensitySize`, `setAssemblySize`, `recordAuthorSize`) in place of today's plain `setAssemblySize`; then the padding as `:338-358`.
- Portal: the model field, and `<padding-pane [model]="model.paddingPaneModel"></padding-pane>` above the `size-position-pane` in `range-slider-general-pane.component.html`, as `selection-general-pane.component.html:217` places it. Import the padding pane's model type the way `selection-general-pane-model.ts` does.

- [ ] **Step 4: Run them to verify they pass, then the dialog neighbours**

Run: `"-Dtest=RangeSliderDensityDialogTest,RangeSliderTitleHeightDialogTest,RangeSliderPropertyDialogServiceTest"`, then `npx ng test portal --include="**/size-position-pane.spec.ts"`.
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/composer/model/vs/RangeSliderGeneralPaneModel.java core/src/main/java/inetsoft/web/composer/vs/dialog/RangeSliderPropertyDialogService.java web/projects/portal/src/app/vsobjects/model/range-slider-general-pane-model.ts web/projects/portal/src/app/vsobjects/dialog/range-slider-general-pane.component.html core/src/test/java/inetsoft/web/composer/vs/dialog/RangeSliderDensityDialogTest.java
git -C "$WT" commit -m "Bug #78994, add the range slider's padding pane and height density checkbox" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Notes, full runs and the manual checks

Spec D4, §8, §11.

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/chart-card-open-item-decisions.md` (§3, one line)
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` (one line beside the range slider painter half, `:230`)
- Create (git-ignored): `$WT/.superpowers/baselines/range-slider-density/manual-checks.md`

- [ ] **Step 1: Add the two notes**

§3 of the open-item decisions and the roadmap's range slider line each gain one sentence: the redraw keeps the density geometry from `2026-10-22-range-slider-density-design.md` (padding 16 / 12 / 8, 6px label gaps, 50px content).

- [ ] **Step 2: Run the full core suite**

Run: `./mvnw -q -o test -pl core -am`. Expected: 0 failures. A failure in a class this branch did not touch: rerun that class alone; if it passes alone, record it as a flake with its name in the manual-checks file and continue.

- [ ] **Step 3: Run the portal suite**

Run: `cd "$WT/web" && npm run test:portal 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -E "Test Files|^ *Tests +[0-9]"`. Expected: both blocks report no failures.

- [ ] **Step 4: Write the manual checks for the user**

`manual-checks.md` lists MC-1 to MC-7 from spec §8, each with the fixture from Task 0, the steps, the expected result and an empty Result column. MC-1 compares against `before/`.

- [ ] **Step 5: Commit the notes**

```bash
git -C "$WT" add docs/superpowers/specs/lookfeel/chart-card-open-item-decisions.md docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git -C "$WT" commit -m "Bug #78994, note the range slider's density geometry for the redraw" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```
