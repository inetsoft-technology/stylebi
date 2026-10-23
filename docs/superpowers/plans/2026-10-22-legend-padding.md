# Legend Padding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the chart legend's padding in the Legend Format dialog as "Row Spacing" (top / bottom) and "Side Indent" (left / right), and give marked charts a per-density default, without moving legacy charts.

**Architecture:** Legend padding stays the tiered `CompositeValue<Insets>` on `LegendsDescriptor`; nothing is seeded. `VSChartChromeDefaults.resolveLegendPadding` turns "no opinion on a marked chart" into the density value at the three places `GraphGenerator` hands padding to a legend, and the dialog model shows the same resolved value. Compact resolves to `null`, the engine's built-in insets, so compact charts are unchanged. An author's value lives in the USER tier; ticking "Follow the default density" clears that tier.

**Tech Stack:** Java 21, JUnit 5, Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`); Angular 21 with Vitest (`portal` target).

**Spec:** `docs/superpowers/specs/2026-10-20-legend-padding-review-design.md`. Decisions 6-8 there were settled with the user on 2026-10-22.

## Global Constraints

- **Branch and worktree:** `bug-79139` (Redmine Bug #79139) in the worktree `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/a0ab3dcf-1565-4122-8940-7ddc31666db8/scratchpad/legend-padding` (call it `$WT`). It was cut from `origin/bug-79136` `3065d0a694` (the chart spacing implementation, PR #7259, itself on the spec PR #7114) and has no upstream. Never work in the shared checkout `E:/StyleBI/stylebi-enterprise/community`; other sessions use it.
- **Git:** run each git command as its own call with `git -C "$WT" ...`; never chain git commands with `&&`; never `git stash` (the stash is shared by every worktree). Run `git -C "$WT" branch --show-current` before every commit and expect `bug-79139`. Never push; the user pushes.
- **Commit messages:** subject `Bug #79139, <what changed>`, plain prose, no blockquotes, ending with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- **Density values (top, left, bottom, right):** dense `Insets(0, 2, 0, 2)`; compact `null`; comfortable `Insets(1, 6, 2, 6)`; unrecognized mode = dense. The dialog shows `null` as the built-in item values `Insets(0, 4, 1, 4)`.
- **Minimums:** all four values 0; no maximum.
- **Gates:** server code keys on the assembly's mark through `VizContext` (`ctx.modern`). Browser code keys on the model value the server sends (`padding.followsDefault != null` means modern). Never the org gate.
- **Names that do not change:** field and XML attribute `padding`, CSS property `padding`, `LegendSpec.setPadding`. No new XML attribute. UI copy never says "padding".
- **i18n:** reuse `composer.vs.followDefaultDensity`, `Top`, `Bottom`, `Left`, `Right`. New keys, exactly:

```
Row\ Spacing=Row Spacing
Side\ Indent=Side Indent
composer.vs.chart.legendRowSpacingTip=Space added above and below each legend item and the legend title. Applies to all legends.
composer.vs.chart.legendSideIndentTip=Indent of the legend title and items from the legend edge. Applies to all legends.
```

- **Comments:** a short clause each. Never mention the spec, this plan, decision numbers, PR numbers or tickets in source. No comments in `.html` templates.
- **New files** start with the AGPL header of `ChartGapResolverTest.java` (`Copyright (C) 2026  InetSoft Technology`).
- **Unchanged:** everything in `inetsoft.graph` (legend layout), `CSSChartStyles`, the script API, `padding-pane.component.*`.

## Review Focus

1. **A legacy chart's Legend Format dialog closed with OK and no edit** stores nothing, so the legend keeps the built-in look. Pinned in Task 3 (`aLegacyOkWithoutEditsStoresNothing`).
2. **A marked chart whose padding comes only from format.css** opens with the box ticked and the stylesheet's values shown; OK leaves the USER tier empty and the stylesheet value drawing. Pinned in Task 3 (`aStylesheetPaddingShowsTickedWithItsValue`, `okWhileTickedLeavesAStylesheetPaddingStanding`) and Task 2 (`aStylesheetPaddingWinsOnAMarkedChart`).
3. **A request from a browser bundle that predates the field** (no `padding` in the general pane) saves without error and writes no padding. Pinned in Task 3 (`aRequestWithoutPaddingWritesNothing`).
4. **A dashboard whose density differs from the org's**: the chart and the dialog both use the dashboard's. Pinned in Task 1 (`aDashboardDensityReachesEveryResolver`, extended) and Task 2 (`aDashboardDensityBeatsTheOrgDensity`).
5. **A script that mutates the padding it reads** (`getPadding().top = 9`) on one chart must not change the density default another chart gets. Pinned in Task 1 (`eachLegendPaddingIsAFreshInsets`).

## How to run tests

Java, from `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts:

```bash
cd "$WT"
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

Do not add `install`; the shared local repository must not receive this branch's jars. Run every new or changed test class on its own at least once (CLAUDE.md, order-dependent context leaks).

Frontend: Vitest cannot run from a worktree whose `node_modules` is a junction. Task 4 runs `npm ci` in `$WT/web` once (it needs the GitHub PAT in `~/.npmrc`). Then:

```bash
cd "$WT/web"
npx ng test portal --include="**/legend-format-general-pane.spec.ts"
```

Shared Java test helper, used in Tasks 1 and 3 (Task 1's class already has it; copy it as a private static method into `LegendFormatDialogModelTest`):

```java
/** A marked chart's context on a dashboard of the given density. */
private static VizContext modernAt(String density) {
   Viewsheet vs = new Viewsheet();
   vs.getViewsheetInfo().setVizDensity(density);
   ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
   vs.addAssembly(chart);
   chart.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
   return VizContext.of(chart.getVSAssemblyInfo());
}
```

---

### Task 1: Descriptor opinion accessors and the density resolver

Spec §4, §6.1, §6.2.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/LegendsDescriptor.java` (accessors after `hasGapUserValue` `:351-356`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java` (after `legendGapForMode` `:178-188`; add `import java.awt.Insets;`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartGapPersistenceTest.java`

**Interfaces:**
- Produces, on `LegendsDescriptor`:
  - `public boolean hasPaddingValue()`: `padding.hasUserValue() || getPadding() != null`. Javadoc: an author's value or a stylesheet's counts as an opinion.
  - `public boolean hasPaddingUserValue()`: `padding.hasUserValue()`.
  - `public void resetPadding(CompositeValue.Type type)`: `padding.resetValue(type)` only, not `resetCompositeValues`.
- Produces, on `VSChartChromeDefaults`:
  - `public static Insets resolveLegendPadding(Insets current, boolean hasOpinion, VizContext ctx)`: `ctx.modern && !hasOpinion ? legendPaddingForMode(ctx.density) : current`.
  - `static Insets legendPaddingForMode(String mode)`: switch on `VSDensityDefaults.COMFORTABLE` / `VSDensityDefaults.COMPACT` / default, returning a new `Insets` (or `null` for compact) on every call. Javadoc: compact is null, the engine's built-in insets.

- [ ] **Step 1: Write the failing tests**

`ChartGapResolverTest` (add `import java.awt.Insets;`):

```java
@Test
void legendPaddingScalesWithDensity() {
   assertEquals(new Insets(0, 2, 0, 2), VSChartChromeDefaults.legendPaddingForMode("dense"));
   assertNull(VSChartChromeDefaults.legendPaddingForMode("compact"));
   assertEquals(new Insets(1, 6, 2, 6), VSChartChromeDefaults.legendPaddingForMode("comfortable"));
   assertEquals(new Insets(0, 2, 0, 2), VSChartChromeDefaults.legendPaddingForMode("roomy"));
}

@Test
void eachLegendPaddingIsAFreshInsets() {
   assertNotSame(VSChartChromeDefaults.legendPaddingForMode("dense"),
                 VSChartChromeDefaults.legendPaddingForMode("dense"));
   assertNotSame(VSChartChromeDefaults.legendPaddingForMode("comfortable"),
                 VSChartChromeDefaults.legendPaddingForMode("comfortable"));
}

@Test
void legendPaddingTakesTheDensityValueWhenModernAndUnset() {
   assertNull(VSChartChromeDefaults.resolveLegendPadding(null, false, modern));
   assertEquals(new Insets(0, 2, 0, 2),
                VSChartChromeDefaults.resolveLegendPadding(null, false, modernAt("dense")));
}

@Test
void legendPaddingKeepsAnOpinion() {
   Insets stylesheet = new Insets(5, 5, 5, 5);
   assertSame(stylesheet,
              VSChartChromeDefaults.resolveLegendPadding(stylesheet, true, modernAt("comfortable")));
}

@Test
void legendPaddingIsUnchangedWhenLegacy() {
   assertNull(VSChartChromeDefaults.resolveLegendPadding(null, false, LEGACY));
   Insets stored = new Insets(3, 3, 3, 3);
   assertSame(stored, VSChartChromeDefaults.resolveLegendPadding(stored, true, LEGACY));
}
```

In the existing `aDashboardDensityReachesEveryResolver`, add:

```java
assertEquals(new Insets(1, 6, 2, 6),
             VSChartChromeDefaults.resolveLegendPadding(null, false, comfortable));
```

`ChartGapPersistenceTest` (add `import java.awt.Insets;`):

```java
@Test
void aLegendPaddingOpinionIsAnAuthorOrStylesheetValue() {
   LegendsDescriptor legends = new LegendsDescriptor();
   assertFalse(legends.hasPaddingValue());
   assertFalse(legends.hasPaddingUserValue());

   legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);
   assertTrue(legends.hasPaddingValue());
   assertFalse(legends.hasPaddingUserValue());

   legends.setPadding(new Insets(0, 4, 1, 4));
   assertTrue(legends.hasPaddingUserValue());
}

@Test
void resetPaddingHandsBackTheStylesheetValue() {
   LegendsDescriptor legends = new LegendsDescriptor();
   legends.setGap(20);
   legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);
   legends.setPadding(new Insets(3, 3, 3, 3));

   legends.resetPadding(CompositeValue.Type.USER);

   assertEquals(new Insets(5, 5, 5, 5), legends.getPadding());
   assertFalse(legends.hasPaddingUserValue());
   assertEquals(20, legends.getGap(), "other author values survive");
}

@Test
void anAuthorLegendPaddingSurvivesSaveLoadAndClone() throws Exception {
   LegendsDescriptor legends = new LegendsDescriptor();
   legends.setPadding(new Insets(1, 6, 2, 6));

   LegendsDescriptor loaded = parse(new LegendsDescriptor(), xml(legends));
   assertTrue(loaded.hasPaddingUserValue());
   assertEquals(new Insets(1, 6, 2, 6), loaded.getPadding());

   LegendsDescriptor copy = (LegendsDescriptor) legends.clone();
   copy.setPadding(new Insets(9, 9, 9, 9));
   assertEquals(new Insets(1, 6, 2, 6), legends.getPadding());
}

@Test
void aLegendWithoutPaddingLoadsWithoutOne() throws Exception {
   assertFalse(parse(new LegendsDescriptor(), xml(new LegendsDescriptor())).hasPaddingValue());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=ChartGapResolverTest,ChartGapPersistenceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`legendPaddingForMode`, `hasPaddingValue` not found).

- [ ] **Step 3: Implement** the three accessors and the two resolver methods as in Interfaces.

- [ ] **Step 4: Run the tests to verify they pass**

Run: the Step 2 command, then each class alone. Expected: all pass, including every existing test in both classes.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/LegendsDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java core/src/test/java/inetsoft/uql/viewsheet/graph/ChartGapPersistenceTest.java
git -C "$WT" commit -m "Bug #79139, resolve a marked chart's legend padding from the dashboard density"
```

---

### Task 2: Every legend renders the resolved padding

Spec §6.3, §7.1 "Render".

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` (`:1744`, `:1824`, `:1860`)
- Test: `core/src/test/java/inetsoft/report/composition/graph/LegendPaddingRenderTest.java` (create)

**Interfaces:**
- Consumes: `LegendsDescriptor.hasPaddingValue()`, `VSChartChromeDefaults.resolveLegendPadding(...)` (Task 1).
- Each of the three `legend.setPadding(legends.getPadding())` lines becomes `legend.setPadding(VSChartChromeDefaults.resolveLegendPadding(legends.getPadding(), legends.hasPaddingValue(), vizContext))`. `:1744` is the color legend, `:1824` the shape/texture legend, `:1860` the size legend.

- [ ] **Step 1: Write the failing test**

Class annotations and context as `AxisTitleGapTest` (`@ExtendWith(SpringExtension.class)`, `BaseTestConfiguration`, `ConfigurationContextInitializer`, `@DirtiesContext(AFTER_CLASS)`, `@SreeHome`, `@Tag("core")`), plus an `@AfterEach` that runs `SreeEnv.setProperty("viewsheet.density", null)`.

The harness was run on 2026-10-22: a plain bar chart passes padding to all three sites, through the element's color frame, texture frame and size frame. No aesthetic binding is needed.

```java
/** The padding each legend site hands its frame, on a bar chart of one dimension by one measure. */
private static List<Insets> legendPaddings(VizMark mark, String density, Insets author,
                                           Insets stylesheet)
{
   Object[][] rows = new Object[7][];
   rows[0] = new Object[]{ "State", "Total" };

   for(int i = 1; i < rows.length; i++) {
      rows[i] = new Object[]{ i % 3 == 0 ? "CA" : (i % 3 == 1 ? "NY" : "TX"), (double) (i * 100) };
   }

   VSChartAggregateRef total = new VSChartAggregateRef();
   total.setDataRef(new BaseField("Total"));
   VSChartInfo cinfo = new DefaultVSChartInfo();
   cinfo.setMultiStyles(false);
   cinfo.setChartType(GraphTypes.CHART_BAR);
   cinfo.setRTChartType(GraphTypes.CHART_BAR);
   cinfo.addXField(new VSChartDimensionRef(new BaseField("State")));
   cinfo.addYField(total);
   cinfo.updateChartType(false);

   ChartDescriptor chartDesc = new ChartDescriptor();
   LegendsDescriptor legends = chartDesc.getLegendsDescriptor();

   if(stylesheet != null) {
      legends.setPadding(stylesheet, CompositeValue.Type.CSS);
   }

   if(author != null) {
      legends.setPadding(author);
   }

   ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
   assemblyInfo.setVSChartInfo(cinfo);
   assemblyInfo.setChartDescriptor(chartDesc);
   assemblyInfo.setVizMark(mark);
   Viewsheet vs = new Viewsheet();
   vs.getViewsheetInfo().setVizDensity(density);
   assemblyInfo.setViewsheet(vs);

   GraphElement elem = GraphGenerator.getGenerator(
      assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
      XSourceInfo.NONE, null).createEGraph().getElement(0);

   return Arrays.asList(elem.getColorFrame().getLegendSpec().getPadding(),
                        elem.getTextureFrame().getLegendSpec().getPadding(),
                        elem.getSizeFrame().getLegendSpec().getPadding());
}
```

Tests, each asserting all three entries (`assertEquals(Collections.nCopies(3, expected), legendPaddings(...))`):

| Test | Arguments (mark, density, author, stylesheet) | Expected each |
|---|---|---|
| `aMarkedChartTakesTheDashboardDensityOnEveryLegend` | `MODERN_LIGHT, "comfortable", null, null`; and `MODERN_LIGHT, "dense", null, null` | `Insets(1, 6, 2, 6)`; `Insets(0, 2, 0, 2)` |
| `aMarkedCompactChartKeepsTheBuiltInLegend` | `MODERN_LIGHT, "compact", null, null` | `null` |
| `anAuthorPaddingWinsOnAMarkedChart` | `MODERN_LIGHT, "comfortable", Insets(3, 3, 3, 3), null` | `Insets(3, 3, 3, 3)` |
| `aStylesheetPaddingWinsOnAMarkedChart` | `MODERN_LIGHT, "dense", null, Insets(5, 5, 5, 5)` | `Insets(5, 5, 5, 5)` |
| `anUnmarkedChartIsUnchanged` | `null, "comfortable", null, null`; and `null, "comfortable", Insets(3, 3, 3, 3), null` | `null`; `Insets(3, 3, 3, 3)` |
| `aDashboardDensityBeatsTheOrgDensity` | first `SreeEnv.setProperty("viewsheet.density", "compact")`, then `MODERN_LIGHT, "comfortable", null, null` | `Insets(1, 6, 2, 6)` |

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q test -pl core -Dtest=LegendPaddingRenderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `aMarkedChartTakesTheDashboardDensityOnEveryLegend` and `aDashboardDensityBeatsTheOrgDensity` fail (each site gets `null`). The other four pass already; they pin behaviour this task must keep.

- [ ] **Step 3: Implement** the three one-line changes.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=LegendPaddingRenderTest,AxisTitleGapTest,ChartGapResolverTest" -Dsurefire.failIfNoSpecifiedTests=false`, then `LegendPaddingRenderTest` alone.
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/test/java/inetsoft/report/composition/graph/LegendPaddingRenderTest.java
git -C "$WT" commit -m "Bug #79139, give every legend of a marked chart its density padding"
```

---

### Task 3: The Legend Format dialog model reads and writes the padding

Spec §5, §6.4, decisions 6 and 7.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatGeneralPaneModel.java` (field, getter and setter next to `gapFollowsDefault`)
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java` (read after `:62`; write after the gap block `:144-164`)
- Test: `core/src/test/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModelTest.java`

**Interfaces:**
- Consumes: Task 1's accessors and `resolveLegendPadding`.
- Produces, on `LegendFormatGeneralPaneModel`: `PaddingPaneModel getPadding()` / `setPadding(PaddingPaneModel)` (`inetsoft.web.composer.model.vs.PaddingPaneModel`). Javadoc: legend row spacing (top / bottom) and side indent (left / right); `followsDefault` is null on an unmarked chart. JSON: `padding: {top, left, bottom, right, followsDefault}`.
- Produces, on `LegendFormatDialogModel` (package-private static, for the tests):
  - `static Insets shownPadding(LegendsDescriptor legendsDesc, VizContext ctx)`: `resolveLegendPadding(getPadding(), hasPaddingValue(), ctx)`, or a new `Insets(0, 4, 1, 4)` when that is null. Keep the built-in value in one private static method with a short comment naming `Legend` and `LegendItem` as its source.
  - `static PaddingPaneModel paddingPane(LegendsDescriptor legendsDesc, VizContext ctx)`: the four values of `shownPadding`, and `followsDefault = ctx.modern ? !legendsDesc.hasPaddingUserValue() : null`.
- Constructor: `generalPaneModel.setPadding(paddingPane(legendsDesc, ctx))` after the gap lines.
- `updateLegendFormatDialogModel`, after the gap block, with `pane = generalPaneModel.getPadding()` and `typed = new Insets(top, left, bottom, right)`:
  - `pane == null`: write nothing.
  - `followsDefault == null`: `legendsDesc.setPadding(typed)` only if `!typed.equals(shownPadding(legendsDesc, ctx))`.
  - `followsDefault == true`: `legendsDesc.resetPadding(CompositeValue.Type.USER)`.
  - `followsDefault == false`: `legendsDesc.setPadding(typed)`.

- [ ] **Step 1: Write the failing tests**

Add the `modernAt` helper (How to run tests) and this one, which runs OK on a bare legend with only the padding under test:

```java
/** Runs the dialog's OK with the given padding pane; every other field is left as shown. */
private static void ok(LegendsDescriptor legends, PaddingPaneModel padding, VizContext ctx) {
   LegendFormatGeneralPaneModel pane = new LegendFormatGeneralPaneModel();
   pane.setPosition("");
   pane.setFillColor("#ffffff");
   pane.setGap(VSChartChromeDefaults.resolveLegendGap(legends.getGap(), legends.hasGapValue(), ctx));
   pane.setPadding(padding);
   LegendFormatDialogModel dialog = new LegendFormatDialogModel();
   dialog.setLegendFormatGeneralPaneModel(pane);
   dialog.setLegendScalePaneModel(new LegendScalePaneModel());
   AliasPaneModel alias = new AliasPaneModel();
   alias.setAliasList(new ModelAlias[0]);
   dialog.setAliasPaneModel(alias);
   dialog.updateLegendFormatDialogModel(new VSChartInfo(), legends, new LegendDescriptor(), null, ctx);
}
```

Tests (`assertPane(pane, top, left, bottom, right, followsDefault)` is a small private assertion helper over the five fields):

| Test | Setup and action | Expected |
|---|---|---|
| `aCompactChartShowsTheBuiltInValuesTicked` | new legends; `paddingPane(legends, modernAt("compact"))` | 0, 4, 1, 4, `TRUE` |
| `aComfortableChartShowsItsDensityValues` | new legends; `paddingPane(legends, modernAt("comfortable"))` | 1, 6, 2, 6, `TRUE` |
| `aStylesheetPaddingShowsTickedWithItsValue` | CSS `Insets(5, 5, 5, 5)`; `paddingPane(..., modernAt("compact"))` | 5, 5, 5, 5, `TRUE` |
| `anAuthorPaddingUnticksTheBox` | USER `Insets(3, 3, 3, 3)`; `paddingPane` at `modernAt("compact")`, then at `VizContext.LEGACY` | 3, 3, 3, 3, `FALSE`; then `followsDefault` `null` |
| `okWhileTickedClearsTheAuthorTier` | CSS `Insets(5, 5, 5, 5)` and USER `Insets(3, 3, 3, 3)`; pane = `paddingPane`, `setFollowsDefault(true)`; `ok` | `hasPaddingUserValue()` false; `getPadding()` = `Insets(5, 5, 5, 5)` |
| `okWhileTickedLeavesAStylesheetPaddingStanding` | CSS `Insets(5, 5, 5, 5)` only; `ok(legends, paddingPane(legends, ctx), ctx)` at compact | `hasPaddingUserValue()` false; `getPadding()` = `Insets(5, 5, 5, 5)` |
| `untickingOnCompactStoresTheShownValues` | new legends; pane = `paddingPane` at compact, `setFollowsDefault(false)`; `ok` | `hasPaddingUserValue()` true; `getPadding()` = `Insets(0, 4, 1, 4)` |
| `anUntickedEditIsStored` | new legends; pane at comfortable with top 3, bottom 3, `followsDefault` false; `ok` | `getPadding()` = `Insets(3, 6, 3, 6)` |
| `aLegacyOkWithoutEditsStoresNothing` | new legends; `ok(legends, paddingPane(legends, LEGACY), LEGACY)` | `hasPaddingValue()` false |
| `aLegacyEditIsStored` | new legends; pane = `paddingPane(legends, LEGACY)` with top 2; `ok` | `getPadding()` = `Insets(2, 4, 1, 4)` |
| `aRequestWithoutPaddingWritesNothing` | new legends; `ok(legends, null, modernAt("compact"))` | no exception; `hasPaddingValue()` false |

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=LegendFormatDialogModelTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`paddingPane`, `setPadding(PaddingPaneModel)` not found).

- [ ] **Step 3: Implement** the pane model field, the two static helpers, the constructor line and the write block.

- [ ] **Step 4: Run the tests to verify they pass**

Run: the Step 2 command, then `./mvnw -q test -pl core "-Dtest=LegendFormatDialogModelTest,ChartGapResolverTest" -Dsurefire.failIfNoSpecifiedTests=false`.
Expected: all pass, including the existing tests in `LegendFormatDialogModelTest`.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatGeneralPaneModel.java core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java core/src/test/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModelTest.java
git -C "$WT" commit -m "Bug #79139, read and write the legend padding in the legend format dialog"
```

---

### Task 4: Row Spacing and Side Indent in the Legend Format dialog

Spec §5, §6.5, decision 8.

**Files:**
- Modify: `web/projects/portal/src/app/graph/model/dialog/legend-format-general-pane-model.ts`
- Modify: `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.html` (after the Distance from Chart row, which ends at `:94`)
- Modify: `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.ts` (`:51`, `:153-156`, `:168-170`)
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties` (`Row\ Spacing` after `Row\ Range` `:2401`; `Side\ Indent` before `Sign\ In\ With\ Google` `:2705`; the two tips after `composer.vs.chart.legendDistanceTip` `:3675`)
- Test: `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.spec.ts`

**Interfaces:**
- Consumes: the JSON `padding` object (Task 3).
- TS model: `padding: PaddingPaneModel;` imported from `../../vsobjects/model/padding-pane-model`.
- Component: `showPaddingFollowDefault: boolean`, set in `ngOnInit` to `this.model.padding?.followsDefault != null`; `paddingFollowDefaultChanged(follows: boolean): void` sets `this.model.padding.followsDefault`.
- Template: one `shell-form-row--field row` with two `col-6` groups, then the checkbox row inside `@if (showPaddingFollowDefault)`. Each group is `role="group"` with `aria-labelledby` pointing at its caption, carries `[title]` with its tooltip key, and holds a `<label class="form-label">` caption (`_#(Row Spacing)` with id `legendRowSpacingLabel`, `_#(Side Indent)` with id `legendSideIndentLabel`) over an inner `row` of two `col d-flex flex-column` cells. Each cell holds a `number-stepper` and, under it, its `<label>` (`_#(Top)`, `_#(Bottom)`, `_#(Left)`, `_#(Right)`), as `padding-pane.component.html` does. Each stepper: `[(ngModel)]="model.padding.<side>"`, `[min]="0"`, `ariaLabel` = its side's key, `[disabled]="model.padding.followsDefault === true"`, `[ngModelOptions]="{standalone: true}"`, and `data-test="legend-padding-<side>"`. The checkbox row copies the Distance from Chart checkbox (`:81-93`) with id `legendPaddingFollowsDefault`, `[ngModel]="model.padding.followsDefault"` and `(ngModelChange)="paddingFollowDefaultChanged($event)"`.

- [ ] **Step 1: Install the worktree's frontend dependencies**

Run: `cd "$WT/web" && npm ci`
Expected: completes.

- [ ] **Step 2: Write the failing tests**

In the spec, `createModel` gains `padding: { top: 0, left: 4, bottom: 1, right: 4, followsDefault: true }`. Each test sets the model, runs `fixture.detectChanges()`, `await fixture.whenStable()`, `fixture.detectChanges()`, and finds the steppers by `[data-test="legend-padding-top|bottom|left|right"]`:

```ts
it("captions the legend padding 'Row Spacing' and 'Side Indent'", async () => {
   // labels include "_#(Row Spacing)", "_#(Side Indent)", "_#(Top)", "_#(Bottom)", "_#(Left)", "_#(Right)"
});

it("disables the four spacing steppers while following the default", async () => {
   // followsDefault true: each stepper's host has class "disabled";
   // the checkbox #legendPaddingFollowsDefault exists and is checked
});

it("enables the four spacing steppers once the box is unticked", async () => {
   // followsDefault false: no stepper host has class "disabled"
});

it("hides the padding follow-default box on an unmarked chart", async () => {
   // followsDefault undefined: no #legendPaddingFollowsDefault; no stepper is disabled
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd "$WT/web" && npx ng test portal --include="**/legend-format-general-pane.spec.ts"`
Expected: the four new tests fail; the two existing tests pass.

- [ ] **Step 4: Implement** the TS model field, the component members, the template rows and the i18n keys.

- [ ] **Step 5: Run the tests to verify they pass**

Run: the Step 3 command. Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" add web/projects/portal/src/app/graph/model/dialog/legend-format-general-pane-model.ts web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.html web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.ts web/projects/portal/src/app/graph/dialog/legend-format-general-pane.spec.ts core/src/main/resources/inetsoft/util/srinter.properties
git -C "$WT" commit -m "Bug #79139, add row spacing and side indent to the legend format dialog"
```

---

### Task 5: Whole-branch verification and the manual checklist

**Files:** none changed, unless a check fails.

- [ ] **Step 1: Run every touched Java class together, then each alone**

Run: `./mvnw -q test -pl core "-Dtest=ChartGapResolverTest,ChartGapPersistenceTest,LegendPaddingRenderTest,LegendFormatDialogModelTest,AxisTitleGapTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. Then run each of the first four classes with `-Dtest=<one class>`.

- [ ] **Step 2: Run the frontend spec** (Task 4, Step 3 command). Expected: all pass.

- [ ] **Step 3: Manual checks, for the user**

The spec's §7.2 checks 1-9, against a dev server built from `$WT`. Expectations that differ from a first reading:

- **Check 3:** 3 / 3 makes each row 5px taller than compact's built-in (6 against 1).
- **Check 5:** with only a format.css `padding`, the box is **ticked**, showing the stylesheet's values disabled.
- **Check 7:** unticking on compact and pressing OK unchanged adds 1px below the title and moves a gradient legend's min/max labels 4px in from each side; nothing else moves.

- [ ] **Step 4: Report** the results. Do not push. The PR (community only) waits for the user: base `bug-79136` while #7259 is open, retargeted to `epic-74519` after #7114 and #7259 merge.
