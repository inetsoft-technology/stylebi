# Chart Defaults S0 — Chart Defaults Travel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the chart descriptor values a modern seed writes save in the asset on marked charts only, leave every classic chart's XML and rendering exactly as today, and give later slices one place where per-type defaults re-run when a chart's resolved type changes.

**Architecture:** `CompositeValue` gains a per-instance save switch. Each chart descriptor lists the tiered fields a seed writes, and `ChartDescriptor.setSaveDefaults` flips them together. `ChartVSAssemblyInfo` sets the switch from its mark in `seedChromeDefaults` and again right before each write (asset and bookmark), so a descriptor swapped in without a re-seed still saves what its chart's mark says. A per-type seed hook runs at the end of the full seed and from `update()` whenever a recorded key (resolved type plus axis binding) changes. In S0 its body is empty.

**Tech Stack:** Java 21, JUnit 5, Mockito (`mockStatic`), Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`). No frontend change.

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md`: §3 M3–M5 and M8, §5 S0 (S0-1 … S0-6), §10 D22–D25, §11 "Travel", "Bookmarks", "Resolved type". Read it with the next section. Where they differ, this plan wins.

**Verified against:** `chart-defaults-design` = `epic-74519` `5e4772a6cf` plus the spec commit. Every line cited below was read there. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/`.

## Corrections to the spec

| # | Spec | Code at `5e4772a6cf` | This plan |
|---|---|---|---|
| C1 | S0-1 lists gridline styles, `emptyColor`, `paretoLineColor`, `alpha`, `LegendsDescriptor.border`, `TitleDescriptor.visible` | No seed writes them. The modern seed writes the five line colours (`ChartVSAssemblyInfo:169-174`). Render-time CSS writes the legend border colour and three line colours (`CSSChartStyles:113-115`, `:150-155`). | S0 registers the five line colours and the legend border colour. Each other field is registered by the slice that first seeds it, per the spec's own "a field no seed writes is unchanged". |
| C2 | M3: the modern gridline survives a reload because CSS writes it at render | Only x, y and facet are CSS-written (`CSSChartStyles:150-155`). A reloaded marked chart's diagonal and quadrant lines fall back to the classic lookup today. | S0 fixes this as a side effect. Task 2 asserts all five lines. |
| C3 | M3: the seed's branches turn the switch on and off | Descriptors are replaced without a re-seed: load (`ChartVSAssemblyInfo:1761-1766`), `copyViewInfo` (`desc = ninfo.desc`), the wizard sharing one object (`VSWizardBindingHandler:1744`), `SyncChartHandler:183`, `ChartPropertyDialogService:533`. | The chart also applies the switch from its mark right before each write (Task 2). |
| C4 | M5, S0-5: legacy descriptor values are transition-only today | They are written on every legacy pass already (`ChartVSAssemblyInfo:181-211`). Only the card frame (`VSAssemblyInfo.seedCardSurface:1358`) and the linear ramp (`ChartVSAssemblyInfo:264`) are transition-only. | S0-5 is the legend border's legacy write (Task 3) plus pinning tests (Tasks 2, 3). |
| C5 | S0-6: compare the resolved type before and after | A type change can install a new `VSChartInfo` whose runtime type is already the new one (`AbstractChartInfo.setChartType:611-615`, `ChangeChartTypeProcessor.process`). An explicit point chart stays POINT whatever is on X (`AbstractChartInfo:970-972`), yet S1-3 keys on "a measure on X". | The last key seeded is recorded on the assembly info, and the key includes the axis binding (Task 4). |
| C6 | M3: "the legacy seed (`:196-207`)" under `PlotDescriptor` | That range is `ChartVSAssemblyInfo:196-208`. | — |
| C7 | C3: applying the switch before each write is enough after a descriptor swap | **Found by manual check S1-38, after execution.** The switch decides whether a swapped-in plot saves its DEFAULT tier, not which values it holds. The wizard's sync replaces the kept chart's plot with a clone of the source's (`SyncChartHandler.syncPlotProperties`), and the preview sync also copies its formats. So a classic chart finished through the wizard on a modern dashboard saved the classic line colours (`^^DEF^^-1118482`), corner radius 0.0 and label ink `#4B4B4B` as a marked chart's DEFAULT tier. It drew modern only through render-time CSS. | **Owner rulings, 2026-11-09:** the finished chart becomes modern, and it must save the modern values. **Fix:** at the end of `SyncChartHandler.syncChart`, a marked target is re-seeded for its own mark (`VizModernizeUtil.reseedAfterRestore`). The padding, smoothing and corner-radius flags travel with their values, so the author's survive. Test: `WizardSyncReseedTest`. |
| C8 | M3, M9: older builds read `^^DEF^^`, so the seeded values travel | They read it but don't keep it. On an older build, an EM import re-writes the asset and drops every DEFAULT tier (manual check S0-13; spec M3). | **Owner ruling, 2026-11-09:** a known limitation, not changed. |

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on a branch cut from `chart-defaults-design`. Never work in the shared checkout `E:/StyleBI/stylebi-enterprise/community`. All changes are in the community repo under `core/`.
- **Git:** run each git command as its own call with `git -C "$WT" ...`. Never chain with `&&`. Run `git -C "$WT" branch --show-current` before every commit. One commit per task, subject `Chart defaults S0, <what changed>`, plain prose, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never push.
- **Scope is the mark (M1, D22):** only an assembly with a modern `VizMark` saves a DEFAULT tier. A classic chart's descriptor XML stays byte-identical and its x, y, diagonal and quadrant DEFAULT tiers keep coming from the `ChartPlotLine` lookup when the object is built (`PlotDescriptor:1898-1911`).
- **The author wins:** seeds write only DEFAULT tiers, and plain fields only while their user flag is false. Modernize then Revert renders identically (P6 as amended by feature-80398 MD §4).
- **No behaviour change for drawing:** the render-time CSS writes stay (`CSSChartStyles:113-115`, `:150-155`). Smoothing stays where it is (`ChartVSAssemblyInfo:163-165`, `:190-192`, `ChangeChartTypeService.applySmoothLinesTransition:342`). The per-type seed body is empty in S0.
- **Modern values:** gridline and legend border `#D6D2C8` light, `#4A484F` dark (`VSChartChromeDefaults.gridlineColor`, `legendBorderColor`). **Classic values:** `ChartLineColor.getPlotLineColor(GDefaults.DEFAULT_GRIDLINE_COLOR, "x" | "y" | "diagonal" | "quadrant")`; facet and legend border `GDefaults.DEFAULT_LINE_COLOR`.
- **Transition-only stays only for the card border widths** (TC R3, `VSAssemblyInfo.seedCardSurface:1358`) and the linear ramp. Chart descriptor values are written on every legacy pass (D24).
- **Not in S0:** the parse-time flag derivation of M4. It has no user until the first new flag (S1-4), which builds it.
- **feature-80398 is not merged.** It adds `chromeSlots()` after `ChartVSAssemblyInfo.defaultPadding` (`:217-220`). Keep S0's additions inside `seedChromeDefaults`, beside `getChartDescriptor` and in `update`, so the merge stays clean. Its chrome slots already cover the six registered fields.
- **Comments:** a short clause each. No ticket, PR, issue, slice or design-doc names in source. No `.html` file is touched.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Java tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach` as `SeedChromeDefaultsTest.reset()` (`:65-70`) does. Close every `mockStatic` in `@AfterEach` or try-with-resources.

## Review Focus

1. **A marked chart opened and saved again with no re-seed in between** (open, edit something else, save) keeps its seeded tiers. Pinned in Task 2 by `aReloadedMarkedChartKeepsSavingItsDefaults`.
2. **A descriptor object swapped in without a re-seed** (the wizard shares the temp chart's descriptor, `VSWizardBindingHandler:1744`; Chart Properties clones one in, `ChartPropertyDialogService:533`) saves what the owning chart's mark says, both ways. Pinned in Task 2 by `theSaveFollowsTheOwningChartsMark`.
3. **A bookmark taken while classic, restored on a chart Modernized since,** draws modern, and the next save carries the tiers. Pinned in Task 2 by `aClassicBookmarkRestoredAfterModernizeDrawsModernAndSavesItsDefaults`.
4. **Opening a dashboard and executing it again and again,** including a date-comparison chart whose chart info is replaced by a clone on every update (`ChartVSAssemblyInfo:2221-2223`), never re-seeds; only a change of the key does. Pinned in Task 4 by `theFirstUpdateAfterLoadRecordsWithoutSeeding` and `aDateComparisonChartDoesNotReseedOnEveryUpdate`.
5. **A copy of a marked chart** (duplicate, paste and undo checkpoints are clones) saves its tiers, and each copy owns its switch. Pinned in Task 1 by `aCopyKeepsTheSwitchAndOwnsIt` and in Task 2 by `aCopyOfAMarkedChartSavesItsDefaults`.

## File Structure

- `uql/CompositeValue.java`: the per-instance switch.
- `uql/viewsheet/graph/PlotDescriptor.java`, `LegendsDescriptor.java`: each lists its own registered fields.
- `uql/viewsheet/graph/ChartDescriptor.java`: aggregates the lists and flips the switch.
- `uql/viewsheet/internal/ChartVSAssemblyInfo.java`: applies the switch from the mark, seeds the legend border, records the key, hosts the per-type seed and the entry point.
- `uql/viewsheet/ChartVSAssembly.java`: applies the switch before a bookmark write.
- `uql/viewsheet/internal/ResolvedChartType.java` (new): the key the per-type seed runs for.
- Tests: `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartDescriptorSavedDefaultsTest.java` (new, generic over the registered list), `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` (concrete values), `core/src/test/java/inetsoft/uql/viewsheet/internal/ResolvedTypeReseedTest.java` (new, the entry point).

## How to run tests

From `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts.

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

Do not add `install`. Run every new or changed test class on its own at least once.

---

### Task 1: The save switch and the registered list

S0-1 mechanism, S0-3, S0-4's fresh-descriptor round trip.

**Files:**
- Modify: `uql/CompositeValue.java` (new setter after `hasUserValue` `:82-84`; field `:261`)
- Modify: `uql/viewsheet/graph/PlotDescriptor.java` (beside `resetCompositeValues` `:1871`)
- Modify: `uql/viewsheet/graph/ChartDescriptor.java` (after `clone` `:261-288`)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartDescriptorSavedDefaultsTest.java`

**Interfaces:**
- Produces:
  - `CompositeValue.setSaveDefault(boolean saveDefault)`: public. Decides whether `toString` writes the `^^DEF^^` tier. `parse` is unchanged, and `clone` keeps the flag (`super.clone`).
  - `PlotDescriptor.savedDefaults()`: package-private, `List<CompositeValue<?>>`, built per call from the live fields (clone replaces them): `xGridColor, yGridColor, facetColor, diagonalColor, quadrantColor`.
  - `ChartDescriptor.savedDefaults()`: package-private, `List<CompositeValue<?>>`, the plot descriptor's list. Null-safe on `plotDesc`.
  - `ChartDescriptor.setSaveDefaults(boolean save)`: public, calls `setSaveDefault(save)` on every element of `savedDefaults()`.

- [ ] **Step 1: Confirm no chart-descriptor parse reads a bare value as DEFAULT (S0-3)**

Run: `grep -rnE "\.parse\([A-Za-z_]+, " core/src/main/java/inetsoft/uql/viewsheet/graph core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/ChartVSAssembly.java`
Expected: exactly one line, `aesthetic/StaticSizeFrameWrapper.java:99`, a size frame and not a descriptor. Every descriptor field parses through `parse(String)`, which passes `readAsDefault = false` (`CompositeValue:105-107`).

- [ ] **Step 2: Write the failing tests**

Standard context (Global Constraints). Helpers: `xml(ChartDescriptor)` and `parse(ChartDescriptor target, String xml)` as `ChartGapPersistenceTest` does, plus:

```java
private static <T> void promoteDefaultToUser(CompositeValue<T> value) {
   value.setValue(value.get(CompositeValue.Type.DEFAULT), CompositeValue.Type.USER);
}
```

```java
@Test
void aSwitchedOnDescriptorWritesItsRegisteredDefaultTiers() throws Exception {
   ChartDescriptor desc = new ChartDescriptor();
   desc.getPlotDescriptor().setYGridColor(new Color(0x4A484F), CompositeValue.Type.DEFAULT);
   desc.setSaveDefaults(true);

   // a fresh descriptor reads as an older build does: its own switch is off
   ChartDescriptor read = parse(new ChartDescriptor(), xml(desc));

   assertEquals(new Color(0x4A484F), read.getPlotDescriptor().getYGridColor());

   for(int i = 0; i < desc.savedDefaults().size(); i++) {
      assertEquals(desc.savedDefaults().get(i).get(CompositeValue.Type.DEFAULT),
                   read.savedDefaults().get(i).get(CompositeValue.Type.DEFAULT), "field " + i);
      assertFalse(read.savedDefaults().get(i).hasUserValue(), "field " + i);
   }
}

@Test
void aSwitchedOffDescriptorWritesWhatItAlwaysWrote() {
   ChartDescriptor desc = new ChartDescriptor();
   String before = xml(desc);
   desc.setSaveDefaults(true);
   desc.setSaveDefaults(false);

   assertEquals(before, xml(desc));
   assertFalse(before.contains("^^DEF^^"));
}

@Test
void aBareValueFromAnOldFileStaysTheAuthors() throws Exception {
   ChartDescriptor old = new ChartDescriptor();
   old.savedDefaults().forEach(ChartDescriptorSavedDefaultsTest::promoteDefaultToUser);
   String oldXml = xml(old);
   assertFalse(oldXml.contains("^^DEF^^"), "precondition: bare values, as every older file has");

   ChartDescriptor read = parse(new ChartDescriptor(), oldXml);

   read.savedDefaults().forEach(v -> assertTrue(v.hasUserValue()));
}

@Test
void theSwitchDoesNotChangeHowABareValueParses() {
   CompositeValue<Color> value = new CompositeValue<>(Color.class, Color.BLACK);
   value.setSaveDefault(true);

   value.parse(String.valueOf(Color.RED.getRGB()));

   assertTrue(value.hasUserValue());
   assertEquals(Color.BLACK, value.get(CompositeValue.Type.DEFAULT));
}
```

`ChartDescriptor.parseContents` builds new sub-descriptors (`:387`, `:394`), so a descriptor's switch is always off while it parses. The second test covers a switched-on value anyway.

`aCopyKeepsTheSwitchAndOwnsIt`: switch a descriptor on, `clone()` it, and assert `xml(copy)` contains `^^DEF^^`. Then switch the copy off and assert `xml(original)` still contains `^^DEF^^`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=ChartDescriptorSavedDefaultsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`setSaveDefault`, `setSaveDefaults`, `savedDefaults` not found).

- [ ] **Step 4: Implement** the three members listed under Interfaces.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=ChartDescriptorSavedDefaultsTest,PlotDescriptorXmlTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/CompositeValue.java core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/graph/ChartDescriptor.java core/src/test/java/inetsoft/uql/viewsheet/graph/ChartDescriptorSavedDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S0, let chart descriptor values save their default tier" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: A marked chart saves its seeded tiers

S0-1 application, S0-4, the bookmark half of S0-5.

**Files:**
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedChromeDefaults` end `:214`; new method after `getChartDescriptor` `:1264-1266`; `writeContents` `:1663-1664`)
- Modify: `uql/viewsheet/ChartVSAssembly.java` (`writeStateContent`, first line inside `if(runtime)` `:457`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` (replace `:1190-1202`; reword the comment at `:1167-1170`; new tests)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartDescriptorSavedDefaultsTest.java`

**Interfaces:**
- Consumes: `ChartDescriptor.setSaveDefaults(boolean)`, `ChartDescriptor.savedDefaults()` (Task 1).
- Produces: `ChartVSAssemblyInfo.syncSavedDefaults()`, public, `desc.setSaveDefaults(getVizMark() != null)` when `desc != null`. Called:
  - in `writeContents` right before `desc.writeXML(writer)`;
  - in `ChartVSAssembly.writeStateContent` as the first statement inside `if(runtime)`, before `state_info` and `state_descriptor`.
- Also: right after `seedColorPalette(ctx)` (`:214`), `seedChromeDefaults` calls `getChartDescriptor().setSaveDefaults(ctx.modern)`. That turns the switch on in the modern branch and off in the legacy one. `ctx.modern` equals "marked" for every caller: `VizContext.of(info)`, and `ofTransition(vs, info.getVizMark())` after stamping.
- No parse hook. A loaded chart's descriptor always parses switched off (`ChartDescriptor.parseContents:387`, `:394`), and its next write turns the switch on.

- [ ] **Step 1: Write the failing tests**

In `SeedChromeDefaultsTest`, add helpers:
- `chartDescriptorXml(String fullXml)`: the substring from `<chartDescriptor` to `</chartDescriptor>`. The VSChartInfo's size frame writes its own `^^DEF^^`, so whole-info XML cannot be searched.
- `parseInfo(String xml)`: `roundTrip`'s parse half (`:1717-1721`).
- `writeState(VSAssembly)` and `restoreState(VSAssembly, String)`, as `BottomTabsReseedTest:189-190`.
- `chartOn(Viewsheet)`: `new ChartVSAssembly(vs, "Chart1")`, `vs.addAssembly`, `initDefaultFormat()`.

In `theSeededDataLabelInkTravelsInTheAsset`, drop the comment's contrast with the line colours and the old test's name (`:1167-1170`). Replace `theSeededLineColoursDoNotTravelInTheAssetYet` (and its Javadoc) with:

```java
@Test
void theSeededLineColoursTravelInTheAsset() throws Exception {
   SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   SreeEnv.setProperty("viewsheet.darkMode", "true");
   PlotDescriptor restored = plot(roundTrip(newChart()));
   Color dark = new Color(0x4A484F);

   assertEquals(dark, restored.getXGridColor(), "x gridline");
   assertEquals(dark, restored.getYGridColor(), "y gridline");
   assertEquals(dark, restored.getFacetGridColor(), "facet lines");
   assertEquals(dark, restored.getDiagonalColor(), "diagonal, which no render-time CSS write rescues");
   assertEquals(dark, restored.getQuadrantColor(), "quadrant, likewise");
}
```

Add these tests:
- `aReloadedMarkedChartKeepsSavingItsDefaults`: gate on; `plot(roundTrip(roundTrip(newChart()))).getDiagonalColor()` equals `#D6D2C8`. No seed runs on load, so only the save can keep the tier.
- `aClassicChartsDescriptorWritesNoDefaultTier`: gate off; `chartDescriptorXml(writeXml(newChart()))` does not contain `^^DEF^^`.
- `aRevertedChartsDescriptorWritesNoDefaultTier`: gate on, `newChart()`, `setVizMark(null)`, `initDefaultFormat()`; no `^^DEF^^`.
- `theSaveFollowsTheOwningChartsMark`:
  - gate on, marked chart, `setChartDescriptor(new ChartDescriptor())`: its XML contains `^^DEF^^`;
  - gate off, classic chart, `setChartDescriptor(d)` where `d.setSaveDefaults(true)`: its XML has none.
- `aCopyOfAMarkedChartSavesItsDefaults`: gate on; `(ChartVSAssemblyInfo) newChart().clone()` writes `^^DEF^^`.
- `aClassicChartsGridlineFollowsTheStylesheetInForceWhenItLoads` (S0-4). Write a classic and a marked chart. Then, under `mockStatic(ChartLineColor.class, CALLS_REAL_METHODS)` with `getPlotLineColor(any(), eq("x"))` returning `Color.RED`, `parseInfo` both:
  - classic: x grid is `RED`, because nothing was frozen and the lookup at load decides;
  - marked: x grid is `#D6D2C8`, because its saved tier is the modern constant.
- `aBookmarkTakenWhileModernAndRestoredAfterRevertDrawsClassic`:
  - gate on, `chartOn(vs)`, `state = writeState(chart)`;
  - assert `chartDescriptorXml(state)` contains `^^DEF^^` (precondition);
  - `VizModernizeUtil.revert(vs)`, then `restoreState(chart, state)`;
  - x, y, diagonal and quadrant equal their `ChartLineColor` lookups, facet is `GDefaults.DEFAULT_LINE_COLOR`, `labelInk` is `GDefaults.DEFAULT_TEXT_COLOR`;
  - the chart's descriptor XML has no `^^DEF^^`.
- `aClassicBookmarkRestoredAfterModernizeDrawsModernAndSavesItsDefaults`: gate off, `chartOn(vs)`, `state = writeState(chart)`, gate on, `VizModernizeUtil.modernize(vs)`, `restoreState`. The y grid is `#D6D2C8` and the descriptor XML contains `^^DEF^^`.

In `ChartDescriptorSavedDefaultsTest` add `newChart()` and `gateOn()` / `gateOff()` (as `SeedChromeDefaultsTest:72-78, :98-103`), and:

```java
@Test
void aLegacyPassRestoresEveryRegisteredDefault() {
   gateOn();
   ChartVSAssemblyInfo info = newChart();
   info.setVizMark(null);
   // not a transition, as a restore and the residue simulation run it
   VizModernizeUtil.reseedAfterRestore(info);
   gateOff();
   List<CompositeValue<?>> classic = newChart().getChartDescriptor().savedDefaults();
   List<CompositeValue<?>> after = info.getChartDescriptor().savedDefaults();

   for(int i = 0; i < classic.size(); i++) {
      assertEquals(classic.get(i).get(CompositeValue.Type.DEFAULT),
                   after.get(i).get(CompositeValue.Type.DEFAULT), "field " + i);
   }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=SeedChromeDefaultsTest,ChartDescriptorSavedDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in `theSeededLineColoursTravelInTheAsset`, `aReloadedMarkedChartKeepsSavingItsDefaults`, `theSaveFollowsTheOwningChartsMark`, `aCopyOfAMarkedChartSavesItsDefaults`, the stylesheet test's marked assertion, the modern-bookmark precondition and the classic-bookmark save assertion. These already pass and are pins: `aClassicChartsDescriptorWritesNoDefaultTier`, `aRevertedChartsDescriptorWritesNoDefaultTier` and `aLegacyPassRestoresEveryRegisteredDefault` (the legacy branch writes all five lines on every pass, `:196-208`).

- [ ] **Step 3: Implement** `syncSavedDefaults()` and its three call sites, as listed under Interfaces.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=SeedChromeDefaultsTest,ChartDescriptorSavedDefaultsTest,VizModernizeUtilTest,ChartVSAssemblyInfoSeedTest,BottomTabsReseedTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. Then run `SeedChromeDefaultsTest` alone once more.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/ChartVSAssembly.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java core/src/test/java/inetsoft/uql/viewsheet/graph/ChartDescriptorSavedDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S0, save a marked chart's seeded descriptor defaults" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The legend border colour is seeded and travels

S0-2, the legacy-write half of S0-5.

**Files:**
- Modify: `uql/viewsheet/graph/LegendsDescriptor.java` (beside `resetCompositeValues` `:635`)
- Modify: `uql/viewsheet/graph/ChartDescriptor.java` (`savedDefaults`)
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (modern branch after `:174`, legacy branch after `:208`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `ChartDescriptor.savedDefaults()` (Task 1), `syncSavedDefaults()` (Task 2).
- Produces:
  - `LegendsDescriptor.savedDefaults()`: package-private, `List.of(borderColor)`. `ChartDescriptor.savedDefaults()` appends it after the plot's list, null-safe on `legendsDesc`.
  - Seed: the modern branch writes `getChartDescriptor().getLegendsDescriptor().setBorderColor(VSChartChromeDefaults.legendBorderColor(ctx), CompositeValue.Type.DEFAULT)`. The legacy branch writes `GDefaults.DEFAULT_LINE_COLOR` at DEFAULT, the constructed default (`LegendsDescriptor:641-642`, no stylesheet lookup), on every pass.

- [ ] **Step 1: Write the failing tests**

In `SeedChromeDefaultsTest`, with a helper `legends(ChartVSAssemblyInfo)`:
- `aModernChartSeedsTheLegendBorderColour`: gate on, `#D6D2C8`. Dark gate, `#4A484F`.
- `anUnmarkedChartKeepsTheLegacyLegendBorderColour`: gate off, `GDefaults.DEFAULT_LINE_COLOR`.
- `revertRestoresTheLegacyLegendBorderColour`: gate on, `setVizMark(null)`, `initDefaultFormat()`, `DEFAULT_LINE_COLOR`.
- `anAuthorLegendBorderColourOutranksTheSeed`: gate on, USER `Color.RED`, `initDefaultFormat()` again, still `RED`.
- `theSeededLegendBorderColourTravelsInTheAsset`: dark gate, `legends(roundTrip(newChart())).getBorderColor()` is `#4A484F`.
- Add `assertEquals(GDefaults.DEFAULT_LINE_COLOR, legends(chart.getChartInfo()).getBorderColor())` to `aBookmarkTakenWhileModernAndRestoredAfterRevertDrawsClassic`.

`aLegacyPassRestoresEveryRegisteredDefault` (Task 2) now covers the new field with no edit.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=SeedChromeDefaultsTest,ChartDescriptorSavedDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL in `aModernChartSeedsTheLegendBorderColour` and `theSeededLegendBorderColourTravelsInTheAsset` (the DEFAULT tier is still `DEFAULT_LINE_COLOR`). The others pass.

- [ ] **Step 3: Implement** the registration and both seed writes in this one change. Registering without the legacy write fails `aLegacyPassRestoresEveryRegisteredDefault`, because a legacy pass would leave the modern colour in place.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=SeedChromeDefaultsTest,ChartDescriptorSavedDefaultsTest,LegendFormatDialogModelTest,CSSChartStylesModernChromeTest,VizModernizeUtilTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. `LegendFormatDialogModelTest` shows the dialog's border colour is unchanged: `resolveLegendBorderColor` (`LegendFormatDialogModel:100-103`) already showed the modern value.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/LegendsDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/graph/ChartDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S0, seed and save the modern legend border colour" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Per-type seeds re-run when the resolved type changes

S0-6.

**Where the paths converge.** Every path that can change the runtime type ends in `ChartVSAssemblyInfo.update(Viewsheet, ColumnSelection)`. The runtime type itself is recomputed there (`clone.update(...)` `:2222` → `VSChartInfo.updateChartType` `:1021`), reached from `ViewsheetSandbox.updateAssembly` → `refreshMetaData` → `AbstractVSAssembly.update` `:599`:
- drop: `VSChartDndService:220` → `VSChartDataHandler.changeChartData` `:414`
- convert: `ConvertChartRefService:206`; `VSChartDataHandler.convertChartRef` `:357`
- swap: `SwapXYBindingService:97`
- type change: `ChangeChartTypeService:211`
- wizard: `VSWizardDataService:117` (the temp chart, which `VSCloseObjectWizardService:177` puts in place)

The entry point therefore sits in `update`, right after `cinfo = clone;` (`:2223`). That is before `chartTree.updateHierarcy`, which needs a real runtime in tests.

**Files:**
- Create: `uql/viewsheet/internal/ResolvedChartType.java`
- Modify: `uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`seedChromeDefaults` end; `update` `:2223`; field beside `userSmoothLines` `:3202`; accessors beside `isUserSmoothLines` `:2957-2966`)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/ResolvedTypeReseedTest.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartDescriptorSavedDefaultsTest.java`

**Interfaces:**
- Consumes: `ChartDescriptor.savedDefaults()` (Tasks 1, 3).
- Produces:
  - `public record ResolvedChartType(int type, boolean measureOnX, boolean measureOnY, List<String> xFields, List<String> yFields) implements Serializable`, with `public static ResolvedChartType of(ChartInfo info)`. A null info gives `(GraphTypes.CHART_AUTO, false, false, List.of(), List.of())`.
    - `measureOnX` / `measureOnY` come from `GraphUtil.hasMeasureOnX` / `hasMeasureOnY` (design fields).
    - `xFields` / `yFields` are `List.copyOf` of each design field's `getFullName()`, in order.
    - `type` comes from a private `static int resolvedType(ChartInfo info)`. It follows `GraphGenerator.getFirstChartType` (`:6435-6444`) via `GraphTypeUtil.getChartType` (`:598-622`):

      ```java
      if(info.isMultiStyles()) {
         // first measure, Y before X; design refs carry the runtime type before execution
         for(ChartRef[] refs : new ChartRef[][] { info.getRTYFields(), info.getRTXFields(),
                                                  info.getYFields(), info.getXFields() }) {
            for(ChartRef ref : refs == null ? new ChartRef[0] : refs) {
               if(ref instanceof ChartAggregateRef agg) {
                  return agg.getRTChartType();
               }
            }
         }
      }

      return info.getRTChartType();
      ```

  - `ChartVSAssemblyInfo`:
    - `private ResolvedChartType seededType`. Not written to XML and not copied by `copyViewInfo`; `clone` carries it.
    - `public ResolvedChartType getSeededType()`: the key the per-type seed last ran for, or null before the first observation.
    - `protected void seedTypeDefaults(VizContext ctx, ResolvedChartType type)`: empty body, comment "values that depend on the resolved type; written on both branches".
    - `public void reseedIfResolvedTypeChanged()`: `now = ResolvedChartType.of(cinfo)`. If `getVizMark() != null && seededType != null && !seededType.equals(now)`, call `seedTypeDefaults(VizContext.of(this), now)`. Then set `seededType = now`. An unmarked chart only records. Its per-type values are the same for every type, and classic smoothing stays in `ChangeChartTypeService`, so a classic chart is never written to.
    - `seedChromeDefaults` ends, after Task 2's `setSaveDefaults` line, with `now = ResolvedChartType.of(getVSChartInfo()); seedTypeDefaults(ctx, now); seededType = now;`.

- [ ] **Step 1: Write the failing tests**

`ResolvedTypeReseedTest`, in package `inetsoft.uql.viewsheet.internal`, standard context. The harness copies `ChartAdvancedPaneModelDcShareTest`:
- the `GraphTypeUtil.checkChartStylePermission` stub (`:63-73`);
- `columns()` (`:205-214`), extended with `state` (STRING) and `w` (DOUBLE);
- the ref builders (`:216-240`);
- `render` (`:244-257`), which tolerates the date-comparison NPE;
- `writeXml(ChartVSAssemblyInfo)`, as `SeedChromeDefaultsTest:1709-1715`.

```java
/** Records every per-type seed it is asked to run. */
static class ProbeInfo extends ChartVSAssemblyInfo {
   final List<ResolvedChartType> seeded = new ArrayList<>();
   final List<Boolean> modern = new ArrayList<>();

   @Override
   protected void seedTypeDefaults(VizContext ctx, ResolvedChartType type) {
      seeded.add(type);
      modern.add(ctx.modern);
      super.seedTypeDefaults(ctx, type);
   }
}
```

`probeChart(Viewsheet vs, boolean created)` builds the chart:
- `new ChartVSAssembly(vs, "Chart1") { @Override protected VSAssemblyInfo createInfo() { return new ProbeInfo(); } }`;
- `vs.addAssembly`, `setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"))`;
- `setVizMark(VizMark.MODERN_LIGHT)`;
- `initDefaultFormat()` only when `created` (a load never calls it), then clears the probe.

`bind(ChartVSAssembly chart)` puts dimension `state` on X and measure `Sum(v)` on Y of the chart's own `VSChartInfo`, then calls `render`.

```java
@Test
void aDropThatResolvesTheTypeReseeds() throws Exception {   // VSChartDataHandler:414
   ChartVSAssembly chart = probeChart(new Viewsheet(), true);
   bind(chart);

   ProbeInfo probe = (ProbeInfo) chart.getChartInfo();
   assertEquals(1, probe.seeded.size());
   assertEquals(GraphTypes.CHART_BAR, probe.seeded.get(0).type());
   assertTrue(probe.seeded.get(0).measureOnY());
   assertTrue(probe.modern.get(0), "the re-run resolves against the chart's own mark");
}
```

Each test below starts from a bound chart with the probe cleared, makes one change, renders, and asserts:
- `theFullSeedRecordsTheTypeItSeeded`: right after creation, `getSeededType()` equals `ResolvedChartType.of(getVSChartInfo())`.
- `theFirstUpdateAfterLoadRecordsWithoutSeeding` (M8): write a bound chart, `parseXML` it into `probeChart(vs2, false)`'s info (`getSeededType()` is null), render. The probe is empty and `getSeededType()` is non-null.
- `anUnchangedBindingDoesNotReseed`: render again; the probe is empty.
- `aClassicChartRecordsButNeverReseeds`: as `aDropThatResolvesTheTypeReseeds`, but `setVizMark(null)` before binding. Bind, then convert X to `Sum(w)`. The probe is empty, and `getSeededType().type()` is `CHART_POINT`.
- `aSecondMeasureOnTheSameTypeReseeds`: add `Sum(w)` to Y. One call, type BAR. Per-field axis descriptors are new.
- `convertingTheXDimensionToAMeasureReseeds` (`ConvertChartRefService:206`): replace X with `Sum(w)`. One call, `CHART_POINT`, `measureOnX`.
- `swappingXAndYReseeds` (`SwapXYBindingService:97`): `new SwapXYBindingProcessor(cinfo, info.getChartDescriptor()).process()`. One call, `measureOnX` true and `measureOnY` false. The type is still BAR, so only the binding moved the key.
- `aChartTypeChangeReseeds` (`ChangeChartTypeService:211`): `info.setVSChartInfo((VSChartInfo) new ChangeChartTypeProcessor(GraphTypes.CHART_AUTO, GraphTypes.CHART_LINE, false, false, null, cinfo, false, info.getChartDescriptor(), VizContext.of(info)).process())`. One call, `CHART_LINE`.
- `aTypeChangeOnAReplacedChartInfoStillReseeds`: `cinfo.setChartType(CHART_LINE)` (the runtime type changes at once), then `info.setVSChartInfo((VSChartInfo) cinfo.clone())`. One call, `CHART_LINE`.
- `aBindingCopiedInWholesaleReseeds` (wizard): `info.setVSChartInfo(other)`, where `other` has `Sum(w)` on X and `Sum(v)` on Y. One call, `CHART_POINT`.
- `aMultiStyleChartKeysOnItsFirstMeasure`: `cinfo.setMultiStyles(true)`, X `state`, Y `Sum(v)` set to LINE then `Sum(w)` set to BAR (`setChartType`), rendered and cleared.
  - Set the second measure to `CHART_POINT`: no call.
  - Set the first to `CHART_AREA`: one call, `CHART_AREA`.
- `theTypeReseedRunsOnlyThePerTypeSeed`: set the plot's x grid DEFAULT to `Color.MAGENTA`, then convert as above. `MAGENTA` survives, where a full seed would write `#D6D2C8`.
- `aDateComparisonChartDoesNotReseedOnEveryUpdate` (Review Focus 4): a probe chart set up as `ChartAdvancedPaneModelDcShareTest.chart` sets up its chart (`:216-240`), with `dc(DateComparisonInfo.CHANGE_VALUE)` (`:187-203`). Render, clear, render. The probe is empty. If this fails, stop and report which key component changed between the two updates. Do not loosen the assertion.

In `ChartDescriptorSavedDefaultsTest`:

```java
@Test
void aTypeReseedLeavesEveryAuthorValueAlone() {
   gateOn();
   ChartVSAssemblyInfo info = newChart();
   List<CompositeValue<?>> fields = info.getChartDescriptor().savedDefaults();
   List<CompositeValue<?>> classic = new ChartDescriptor().savedDefaults();
   for(int i = 0; i < fields.size(); i++) { setUser(fields.get(i), classic.get(i)); }
   PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
   info.setUserBarCornerRadius(true);
   plot.setBarCornerRadius(0.17);
   info.setUserSmoothLines(true);
   plot.setSmoothLines(false);

   info.getVSChartInfo().setChartType(GraphTypes.CHART_LINE);
   info.reseedIfResolvedTypeChanged();

   assertEquals(GraphTypes.CHART_LINE, info.getSeededType().type(), "precondition: the re-seed ran");
   for(int i = 0; i < fields.size(); i++) {
      assertTrue(fields.get(i).hasUserValue(), "field " + i);
      assertEquals(classic.get(i).get(CompositeValue.Type.DEFAULT),
                   fields.get(i).get(CompositeValue.Type.USER), "field " + i);
   }
   assertEquals(0.17, plot.getBarCornerRadius(), 1e-9);
   assertFalse(plot.isSmoothLines());
}
```

`setUser(CompositeValue<T> to, CompositeValue<?> from)` copies `from`'s DEFAULT into `to`'s USER tier. It is an unchecked cast helper beside `promoteDefaultToUser`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=ResolvedTypeReseedTest,ChartDescriptorSavedDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`ResolvedChartType`, `seedTypeDefaults`, `getSeededType`, `reseedIfResolvedTypeChanged` not found).

- [ ] **Step 3: Implement** the record and the `ChartVSAssemblyInfo` members, and add the call `reseedIfResolvedTypeChanged();` right after `cinfo = clone;` in `update`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=ResolvedTypeReseedTest,ChartDescriptorSavedDefaultsTest,SeedChromeDefaultsTest,ChartAdvancedPaneModelDcShareTest,ChangeChartTypeServiceTest,ChangeChartTypeServiceSmoothLinesTransitionTest,VSWizardBindingHandlerSmoothLinesTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. Then run `ResolvedTypeReseedTest` alone.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/ResolvedChartType.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/ResolvedTypeReseedTest.java core/src/test/java/inetsoft/uql/viewsheet/graph/ChartDescriptorSavedDefaultsTest.java
```
```bash
git -C "$WT" commit -m "Chart defaults S0, re-run per-type seeds when the resolved chart type changes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Interfaces for later slices

- **Saved-defaults list.**
  - `PlotDescriptor.savedDefaults()` and `LegendsDescriptor.savedDefaults()` (package-private, `List<CompositeValue<?>>`, built per call) list the tiered fields a marked chart saves with their DEFAULT tier. `ChartDescriptor.savedDefaults()` concatenates them.
  - Register a field by adding it to its descriptor's list, in the commit that first seeds it on both branches.
  - For `TitleDescriptor.visible`: add `TitleDescriptor.savedDefaults()`, a `TitlesDescriptor.savedDefaults()` over its four titles, and append it in `ChartDescriptor.savedDefaults()`.
  - An `AxisDescriptor` field is written by `VSChartInfo`. Registering one also extends `ChartVSAssemblyInfo.syncSavedDefaults()` to the chart-level and per-ref design axis descriptors.
  - `ChartDescriptorSavedDefaultsTest` then covers a registered field with no new test: round trip, bare value stays USER, legacy pass restores, type re-seed spares USER.
- **Per-type seed.** `protected void seedTypeDefaults(VizContext ctx, ResolvedChartType type)` in `ChartVSAssemblyInfo`, empty in S0.
  - `seedChromeDefaults` calls it last, and so does the entry point.
  - Write each value there on both branches: a tiered value through `set…(value, CompositeValue.Type.DEFAULT)`, a plain one under `if(!isUser…())`.
  - The legacy branch re-runs the classic creation lookup.
- **Entry point.** `public void reseedIfResolvedTypeChanged()` in `ChartVSAssemblyInfo`. `update(Viewsheet, ColumnSelection)` calls it right after `cinfo = clone;`.
  - It compares `ResolvedChartType.of(getVSChartInfo())` with `getSeededType()`. On a difference, and only on a marked chart, it runs the per-type seed under `VizContext.of(this)`.
  - When nothing is recorded yet (the first update after a load), or the chart is unmarked, it only records.
  - A per-type value whose classic value differs by type (today only smoothing) keeps its classic path outside the per-type seed. The legacy branch of `seedTypeDefaults` still runs from the full seed, on Revert.
  - `ChangeChartTypeService` writes `valuesVisible` and the smoothing matrix after `updateAssembly` (`:211-223`). A value the per-type seed takes over must leave that code.
- **Type and binding.** The `ResolvedChartType` record (`inetsoft.uql.viewsheet.internal`):
  - `type()` is `getRTChartType()`. On a multi-style chart it is the first measure's type, Y before X, as `GraphGenerator.getFirstChartType` does.
  - `measureOnX()` and `measureOnY()` come from `GraphUtil.hasMeasureOnX/Y` (design fields).
  - `xFields()` and `yFields()` are the design field full names.
  - A value that depends on another binding fact adds a component. Equality of the record is what re-runs the seed.
- **Plain flagged field.** Write it on every pass of both branches, with no `ctx.transition` guard, only while its flag is false.
  - Store, parse and copy the flag as `userBarCornerRadius` is (`ChartVSAssemblyInfo:1532`, `:1581-1582`, `:1983-1986`).
  - Stamp it only on an author path.
  - The parse-time derivation for files without the attribute (M4) is not in S0. The first new flag (S1-4) builds it.
