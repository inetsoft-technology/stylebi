# Chart Spacing Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the axis label distance and the axis title distance in the chart's Axis Properties and Axis Title dialogs, relabel "Legend Gap" to "Distance from Chart", and make the modern defaults of all three distances follow the dashboard density, without moving legacy charts.

**Architecture:** The three distances stay tiered `CompositeValue<Integer>` fields on `AxisDescriptor`, `TitleDescriptor` and `LegendsDescriptor`; nothing is seeded. `VSChartChromeDefaults` resolves a value with no opinion on a marked chart to a per-density default, both at render (`GraphGenerator`) and in the dialog models, so a dialog shows what the chart draws. An author's value lives in the USER tier and an explicit 0 counts; a save marker separates an author's 0 from the stored zeros older builds left in assets, which load as no value.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`); Angular 21 with Vitest (`portal` and `portal:test-tl` targets).

**Spec:** `docs/superpowers/specs/2026-10-20-chart-spacing-controls-design.md` (PR #7114). Read it together with the next section; where the two differ, this plan wins.

## Decisions and corrections (2026-10-22)

Settled with the user after grounding the spec against `epic-74519` `1f2a5bc5d8`. Every line reference in the spec was accurate there.

| # | Topic | This plan | Spec |
|---|---|---|---|
| D1 | Explicit 0 | An author's 0 is an opinion for the axis label and title gaps: `hasUserValue() \|\| value != 0`. | §5.1, kept |
| D2 | Checkbox when only format.css sets a value | "Follow the default density" is ticked whenever the USER tier is empty, on all three rows. The field shows the resolved value, which is the format.css value when there is one. OK without edits writes nothing. The legend dialog changes to match. | §5.3's code (`!hasLabelGapValue()`) contradicted §8 check 4; §5.6 said no legend backend change |
| D3 | Leftover stored zeros | Customer assets carry `labelGap="0"` / `gap="0"` from older builds, which `CompositeValue.parse` (`:134-135`) reads as a USER 0. One customer's assets: 18 of 632 axis, 21 of 543 title, 4 of 66 legend descriptors. No 13.7 class writes these fields, so they are residue, not choices. Saving an author's 0 now writes a marker attribute (`labelGapZero="true"`, legend `gapZero="true"`); loading a 0 without the marker drops it to no value. Every load path runs the descriptors' `parseXML`: open, bookmark restore (`ChartVSAssembly:569-596`), import, autosave. Accepted cost: a legend 0 saved by current, unreleased epic builds loads as the default. | not covered |
| D4 | Legacy writes | No checkbox on legacy charts. Store the typed value only when it differs from the value shown. | §5.3, kept |
| C1 | Legacy custom title | Takes the USER tier only. A format.css title `label_gap` still never moves a legacy custom title, the rule `GraphGenerator:6315` and `AxisTitleGapTest` guard. | §5.2 used `hasLabelGapValue()`, which counts CSS |
| C2 | Legacy axis label display | Shows 2 for a stored 0, which is what `DefaultAxis.getLabelGap()` (`:2111-2115`) draws. Showing 0 under `min=1` would let `number-stepper.onInputBlur` clamp to 1 and commit it, so tabbing through the field would store a 1. | silent |
| C3 | `AxisDescriptor.clone()` | Deep-copies `labelGap`. Today (`:843-888`) a clone shares it, and undo checkpoints are clones (`Viewsheet.prepareCheckpoint`), so undo could not revert a dialog edit. | silent |
| C4 | Tooltips | `[title]="'_#(key)'"` on the row, the dialog convention (`selection-general-pane.component.html:155`). `.field-help-icon` is styled only inside the two components that load `formats-pane.component.scss`. | §6 help-icon pattern |

Kept on purpose, not defects to fix here:

- The Axis Title dialog shows the default for labels drawn (4 at compact) even when the abutting labels are hidden and the chart draws 8. Computing it needs `GraphGenerator.getTitleAxisDescriptor`'s field lists. The tooltip says the default varies.
- With its labels hidden, a title inherits the density's label-gap default, not an author's label distance.
- The Format pane's Reset (`CSSChartStyles.resetUserValues`, `:610-685`) clears all three distances, as it already does for the legend gap.
- `core/src/main/java/inetsoft/web/graph/handler/Chart` (no extension) is a stray, uncompiled copy of `ChartRegionHandler` from the initial import. It shows up in greps. Do not edit it.

## Global Constraints

- **Branch and worktree:** `bug-79136` (Redmine Bug #79136), in the worktree `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/85aa6067-9748-4320-a98c-82269346fe9c/scratchpad/chart-spacing` (call it `$WT`). It was cut from `origin/chart-spacing-spec` `bc2cd99b14`, which is `epic-74519` `1f2a5bc5d8` plus the spec commit, and has no upstream. Never work in the shared checkout `E:/StyleBI/stylebi-enterprise/community`; other sessions use it.
- **Git:** run each git command as its own call with `git -C "$WT" ...`; never chain git commands with `&&`. Run `git -C "$WT" branch --show-current` before every commit and expect `bug-79136`. Never push; the user pushes.
- **Commit messages:** subject `Bug #79136, <what changed>`, plain prose, no blockquotes, ending with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- **Density defaults (dense / compact / comfortable), unrecognized mode = dense:** axis label distance 6 / 8 / 12; axis title distance 2 / 4 / 6; legend distance 10 / 14 / 18. Compact equals today's values.
- **Minimums:** axis label distance 1 on every chart; title and legend distance 0.
- **Gates:** server code keys on the assembly's mark through `VizContext` (`ctx.modern`). Browser code keys on the model value the server sends (`labelGapFollowsDefault != null` means modern). Never the org gate.
- **Names that do not change:** fields `labelGap` / `gap`, XML attributes `labelGap` / `gap`, CSS properties `label_gap` / `legend_gap`, script methods. The only new XML attributes are `labelGapZero` and `gapZero`. UI copy never says "gap".
- **i18n:** reuse `composer.vs.followDefaultDensity`. New keys are listed in Tasks 6-8. Remove `Legend\ Gap` (`srinter.properties:1694`); its only consumer is `legend-format-general-pane.component.html:78`, and the localize bundles in `community-examples/localize` do not carry it.
- **Comments:** a short clause each. Never mention the spec, this plan, decision labels, PR numbers or tickets in source. No comments in `.html` templates.
- **New files** start with the AGPL header of `ChartGapResolverTest.java`, with `Copyright (C) 2026  InetSoft Technology`.
- **Unchanged:** legacy chart rendering (except an author's own title gap on a legacy custom title, C1), legend layout code, `VSChartResizeLegendService` (it already resolves through `VizContext.of(info)`), Format Painter.

## Review Focus

1. **An existing asset or bookmark whose descriptors carry `labelGap="0"` / `gap="0"` from an older build, then Modernized**, follows the density defaults (8 / 4 / 14 at compact), and its dialogs open ticked. Pinned in Task 1 (`aStoredZeroWithoutTheMarkerLoadsAsNoValue`, `aChartDescriptorRestoreDropsLeftoverZeros`).
2. **A legacy chart's Axis Properties or Axis Title dialog closed with OK and no edit, including tabbing through the new field,** stores nothing. Pinned in Task 4 (`aLegacyAxisShowsTheTwoPixelsItDraws`, `aLegacyOkWithoutEditsStoresNothing`) and Task 5 (`aLegacyOkWithoutEditsStoresNothing`).
3. **A legacy chart with a custom axis title and a format.css title `label_gap`**: the title does not move, and the dialog shows 0. Pinned in Task 3 (`unmarkedChartWithACustomTitleIgnoresAStylesheetGap`) and Task 5 (`aLegacyCustomTitleShowsWhatItDraws`).
4. **Undo right after changing the axis label distance** brings the old value back. Pinned in Task 1 (`aCloneDoesNotShareTheAxisLabelGap`).
5. **A dashboard whose density differs from the org's**: the chart and the dialog both use the dashboard's. Pinned in Task 2 (`aDashboardDensityReachesEveryResolver`, `aComfortableDashboardWidensTheTitleGap`) and Task 4 (`aModernAxisShowsTheDashboardDensityDefault`).

## How to run tests

Java, from `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts:

```bash
cd "$WT"
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

Do not add `install`; the shared local repository must not receive this branch's jars. Run every new or changed test class on its own at least once (CLAUDE.md, order-dependent context leaks).

Frontend: Vitest cannot run from a worktree whose `node_modules` is a junction. Task 6 runs `npm ci` in `$WT/web` once. Then, one spec per run:

```bash
cd "$WT/web"
npx ng test portal --include="**/<file>.spec.ts"
npx ng run portal:test-tl --include="**/<file>.tl.spec.ts"
```

`ng test portal` silently matches nothing for a `*.tl.spec.ts` and exits 0. TL specs go through `portal:test-tl` only, and never unscoped.

Shared Java test helper, used in Tasks 2, 4 and 5 (copy it as a private static method into each test class that needs it):

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

### Task 1: Descriptors — opinion tests, tier reset, leftover-zero marker, clone

D1, D3, C3; spec §5.1 items 1-2. No caller changes yet.

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/graph/GapZeroMarker.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/AxisDescriptor.java` (accessors after `setLabelGap` `:455-457`; `parseAttributes` `:671-675`; `writeAttributes` `:782`; `clone` `:843-888`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/TitleDescriptor.java` (accessors after `setLabelGap` `:177-179`; `writeAttributes` `:276-280`; `parseAttributes` `:331-335`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/LegendsDescriptor.java` (accessor after `hasGapValue` `:347-349`; `writeAttributes` `:472`; `parseAttributes` `:544-546`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/ChartGapPersistenceTest.java` (create)

**Interfaces:**
- Produces, on `AxisDescriptor` and `TitleDescriptor`:
  - `public boolean hasLabelGapValue()`: `labelGap.hasUserValue() || getLabelGap() != 0`. Mirror the Javadoc of `LegendsDescriptor.hasGapValue()` (`:337-346`).
  - `public boolean hasLabelGapUserValue()`: `labelGap.hasUserValue()`.
  - `public void resetLabelGap(CompositeValue.Type type)`: `labelGap.resetValue(type)` only. Not `resetCompositeValues`, which also clears `visible` (title) and the label rotation (axis).
- Produces, on `LegendsDescriptor`: `public boolean hasGapUserValue()`: `gap.hasUserValue()`.
- Produces, as a package-private `final class GapZeroMarker` with a private constructor:
  - `static void write(PrintWriter writer, String attr, CompositeValue<Integer> gap)`: prints ` <attr>="true"` iff `gap.hasUserValue()` and `gap.get(CompositeValue.Type.USER)` equals 0.
  - `static void dropUnmarked(CompositeValue<Integer> gap, String marker)`: calls `gap.resetUserValue()` iff the USER tier holds 0 and `marker` is not `"true"`.
- Wiring: each `writeAttributes` calls `write` right after it writes `labelGap` / `gap`, with attribute `labelGapZero` (axis, title) or `gapZero` (legend). Each `parseAttributes` calls `dropUnmarked` right after it parses `labelGap` / `gap`, passing `Tool.getAttribute(<element>, "<marker attr>")`.

- [ ] **Step 1: Write the failing tests**

`ChartGapPersistenceTest`, in package `inetsoft.uql.viewsheet.graph`, with the standard context: `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`.

```java
@Test
void anAuthorZeroSurvivesSaveAndLoad() throws Exception {
   AxisDescriptor axis = new AxisDescriptor();
   axis.setLabelGap(0);
   TitleDescriptor title = new TitleDescriptor();
   title.setLabelGap(0);
   LegendsDescriptor legends = new LegendsDescriptor();
   legends.setGap(0);

   assertTrue(parse(new AxisDescriptor(), xml(axis)).hasLabelGapUserValue());
   assertTrue(parse(new TitleDescriptor(), xml(title)).hasLabelGapUserValue());
   assertTrue(parse(new LegendsDescriptor(), xml(legends)).hasGapUserValue());
}

@Test
void aStoredZeroWithoutTheMarkerLoadsAsNoValue() throws Exception {
   // the shape older builds wrote: a bare labelGap="0"
   AxisDescriptor axis = new AxisDescriptor();
   axis.setLabelGap(0);
   TitleDescriptor title = new TitleDescriptor();
   title.setLabelGap(0);
   LegendsDescriptor legends = new LegendsDescriptor();
   legends.setGap(0);

   AxisDescriptor axis2 = parse(new AxisDescriptor(), withoutMarker(xml(axis), "labelGapZero"));
   TitleDescriptor title2 = parse(new TitleDescriptor(), withoutMarker(xml(title), "labelGapZero"));
   LegendsDescriptor legends2 = parse(new LegendsDescriptor(), withoutMarker(xml(legends), "gapZero"));

   assertFalse(axis2.hasLabelGapValue());
   assertFalse(title2.hasLabelGapValue());
   assertFalse(legends2.hasGapValue());
}

@Test
void aChartDescriptorRestoreDropsLeftoverZeros() throws Exception {
   // bookmark restore parses a whole ChartDescriptor (ChartVSAssembly.parseStateContent)
   ChartDescriptor desc = new ChartDescriptor();
   desc.getTitlesDescriptor().getXTitleDescriptor().setLabelGap(0);
   desc.getLegendsDescriptor().setGap(0);
   String old = withoutMarker(withoutMarker(xml(desc), "labelGapZero"), "gapZero");

   ChartDescriptor restored = parse(new ChartDescriptor(), old);

   assertFalse(restored.getTitlesDescriptor().getXTitleDescriptor().hasLabelGapValue());
   assertFalse(restored.getLegendsDescriptor().hasGapValue());
}

@Test
void aNonZeroAuthorValueNeedsNoMarker() throws Exception {
   AxisDescriptor axis = new AxisDescriptor();
   axis.setLabelGap(5);
   String written = xml(axis);

   assertFalse(written.contains("labelGapZero"));
   assertEquals(5, parse(new AxisDescriptor(), written).getLabelGap());
}

@Test
void anOpinionIsAnAuthorValueOrANonZeroStylesheetValue() {
   TitleDescriptor title = new TitleDescriptor();
   assertFalse(title.hasLabelGapValue());

   title.setLabelGap(0, CompositeValue.Type.CSS);
   assertFalse(title.hasLabelGapValue(), "a stylesheet zero cannot be told from unset");

   title.setLabelGap(20, CompositeValue.Type.CSS);
   assertTrue(title.hasLabelGapValue());
   assertFalse(title.hasLabelGapUserValue());

   title.setLabelGap(0);
   assertTrue(title.hasLabelGapValue());
   assertTrue(title.hasLabelGapUserValue());
}

@Test
void resetLabelGapClearsOnlyTheGap() {
   TitleDescriptor title = new TitleDescriptor();
   title.setVisible(false);
   title.setLabelGap(20, CompositeValue.Type.CSS);
   title.setLabelGap(6);

   title.resetLabelGap(CompositeValue.Type.USER);

   assertEquals(20, title.getLabelGap(), "the stylesheet value shows through");
   assertFalse(title.isVisible(), "other author values survive");

   AxisDescriptor axis = new AxisDescriptor();
   axis.getAxisLabelTextFormat().getUserDefinedFormat().setRotation(45.0);
   axis.setLabelGap(6);
   axis.resetLabelGap(CompositeValue.Type.USER);

   assertFalse(axis.hasLabelGapUserValue());
   assertEquals(45.0, axis.getAxisLabelTextFormat().getUserDefinedFormat().getRotation());
}

@Test
void aCloneDoesNotShareTheAxisLabelGap() {
   AxisDescriptor axis = new AxisDescriptor();
   axis.setLabelGap(5);
   AxisDescriptor copy = axis.clone();

   copy.setLabelGap(9);

   assertEquals(5, axis.getLabelGap());
}
```

Helpers in the same class:

```java
private static String xml(XMLSerializable obj) {
   StringWriter out = new StringWriter();
   PrintWriter writer = new PrintWriter(out);
   obj.writeXML(writer);
   writer.flush();
   return out.toString();
}

private static <T extends XMLSerializable> T parse(T target, String xml) throws Exception {
   target.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
   return target;
}

private static String withoutMarker(String xml, String marker) {
   String stripped = xml.replaceAll("\\s+" + marker + "=\"true\"", "");
   assertNotEquals(xml, stripped, "the author zero was saved with its marker");
   return stripped;
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=ChartGapPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`hasLabelGapUserValue`, `hasLabelGapValue`, `resetLabelGap`, `hasGapUserValue` not found).

- [ ] **Step 3: Implement**

Add the accessors and `GapZeroMarker`, then wire them as listed under Interfaces. In `AxisDescriptor.clone()`, add `desc.labelGap = labelGap.clone();` beside the other deep copies.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=ChartGapPersistenceTest,ChartGapResolverTest,LegendFormatDialogModelTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. `ChartGapResolverTest.aDeliberateUserZeroLegendGapIsHonoured` still passes, because it never round-trips XML.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/graph/GapZeroMarker.java core/src/main/java/inetsoft/uql/viewsheet/graph/AxisDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/graph/TitleDescriptor.java core/src/main/java/inetsoft/uql/viewsheet/graph/LegendsDescriptor.java core/src/test/java/inetsoft/uql/viewsheet/graph/ChartGapPersistenceTest.java
git -C "$WT" commit -m "Bug #79136, record chart gap opinions and drop leftover stored zeros on load"
```

---

### Task 2: Density-scaled resolvers with an opinion flag

D1; spec §5.1 item 3, §5.2 lines 2671 and 6345, §5.5.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java` (`:99-137` resolvers, `:154-158` constants and comment)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` (`:525-527`: drop `private` from `COMFORTABLE`, `COMPACT`, `DENSE`)
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` (`:2671`, `:6318-6319`, `:6345-6346`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java`
- Test: `core/src/test/java/inetsoft/report/composition/graph/AxisTitleGapTest.java`

**Interfaces:**
- Consumes: `AxisDescriptor.hasLabelGapValue()`, `TitleDescriptor.hasLabelGapValue()` (Task 1).
- Produces, in `VSChartChromeDefaults`:
  - `public static int resolveAxisLabelGap(int current, boolean hasOpinion, VizContext ctx)`: `ctx.modern && !hasOpinion ? axisLabelGapForMode(ctx.density) : current`.
  - `public static int resolveAxisTitleGap(int current, boolean hasOpinion, boolean abuttingLabelsDrawn, VizContext ctx)`: returns `current` when `!ctx.modern || hasOpinion`, else `axisTitleGapForMode` when the labels are drawn and `axisLabelGapForMode` when they are not.
  - `public static int resolveLegendGap(int current, boolean hasOpinion, VizContext ctx)`: same signature; now returns `legendGapForMode(ctx.density)`.
  - `static int axisLabelGapForMode(String mode)`, `static int axisTitleGapForMode(String mode)`, `static int legendGapForMode(String mode)`: package-private, switching on `VSDensityDefaults.COMFORTABLE` / `COMPACT` with the dense value as `default`, shaped like `VSDensityDefaults.chartPaddingForMode`. Values from Global Constraints.
  - Delete `AXIS_TITLE_GAP`, `AXIS_LABEL_GAP`, `LEGEND_GAP`. Rewrite the `:154-158` comment as the per-density scale: compact is the shipped value, and each legend value is 2px under its total because of `VGraph`'s fixed 2. Drop the sentence that calls zero the unset marker from both axis resolvers' Javadoc.
- `GraphGenerator`: `:2671` passes `axisD.hasLabelGapValue()`; `:6318` and `:6345` pass `titleDesc.hasLabelGapValue()`. No behaviour change beyond D1 and density; the legacy custom-title rule is Task 3.

- [ ] **Step 1: Update and add the tests**

In `ChartGapResolverTest`, replace `private static final VizContext MODERN` with a field `modern` set in `@BeforeEach` to `modernAt("compact")` (the shared helper). It must not depend on `viewsheet.density` left in `SreeEnv` by another class. Rewrite every existing call to the new signatures: an unset 0 passes `hasOpinion = false`, and the author values 3 and 5 pass `true`. Expected numbers are unchanged (8, 0, 3, 4, 8, 0, 5, 14). Then add:

```java
@Test
void anAuthorZeroIsHonouredWhenModern() {
   assertEquals(0, VSChartChromeDefaults.resolveAxisLabelGap(0, true, modern));
   assertEquals(0, VSChartChromeDefaults.resolveAxisTitleGap(0, true, true, modern));
   assertEquals(0, VSChartChromeDefaults.resolveAxisTitleGap(0, true, false, modern));
}

@Test
void theDefaultsScaleWithDensity() {
   assertEquals(6, VSChartChromeDefaults.axisLabelGapForMode("dense"));
   assertEquals(8, VSChartChromeDefaults.axisLabelGapForMode("compact"));
   assertEquals(12, VSChartChromeDefaults.axisLabelGapForMode("comfortable"));
   assertEquals(6, VSChartChromeDefaults.axisLabelGapForMode("roomy"));
   assertEquals(2, VSChartChromeDefaults.axisTitleGapForMode("dense"));
   assertEquals(4, VSChartChromeDefaults.axisTitleGapForMode("compact"));
   assertEquals(6, VSChartChromeDefaults.axisTitleGapForMode("comfortable"));
   assertEquals(2, VSChartChromeDefaults.axisTitleGapForMode("roomy"));
   assertEquals(10, VSChartChromeDefaults.legendGapForMode("dense"));
   assertEquals(14, VSChartChromeDefaults.legendGapForMode("compact"));
   assertEquals(18, VSChartChromeDefaults.legendGapForMode("comfortable"));
   assertEquals(10, VSChartChromeDefaults.legendGapForMode("roomy"));
}

@Test
void aDashboardDensityReachesEveryResolver() {
   SreeEnv.setProperty("viewsheet.density", "compact");
   VizContext comfortable = modernAt("comfortable");

   assertEquals(12, VSChartChromeDefaults.resolveAxisLabelGap(0, false, comfortable));
   assertEquals(6, VSChartChromeDefaults.resolveAxisTitleGap(0, false, true, comfortable));
   assertEquals(12, VSChartChromeDefaults.resolveAxisTitleGap(0, false, false, comfortable));
   assertEquals(18, VSChartChromeDefaults.resolveLegendGap(0, false, comfortable));
}
```

Clear `viewsheet.density` in an `@AfterEach` (`SreeEnv.setProperty("viewsheet.density", null)`), as `ViewsheetInfoVizDensityTest` does.

In `AxisTitleGapTest`, widen the private builder to `xTitleGap(boolean labelVisible, VizMark mark, String customTitle, Integer userGap, Integer cssGap, String density)`:
- set the custom title only when `customTitle != null`;
- set `userGap` on the USER tier and `cssGap` on the CSS tier, each only when non-null, independent of the title;
- when `density != null`, attach a `new Viewsheet()` with that `setVizDensity` via `assemblyInfo.setViewsheet(vs)`.

Keep the two existing overloads, delegating with nulls. `markedChartWithACustomTitleResolvesTheCardGap` must now pass `userGap = null`: under D1 its old `setLabelGap(0)` is an author's 0 and draws 0. Add:

```java
@Test
void markedChartHonoursAnAuthorZeroTitleGap() {
   assertEquals(0, xTitleGap(true, VizMark.MODERN_LIGHT, "Custom X Title", 0, null, null));
}

@Test
void aComfortableDashboardWidensTheTitleGap() {
   assertEquals(6, xTitleGap(true, VizMark.MODERN_LIGHT, null, null, null, "comfortable"));
   assertEquals(12, xTitleGap(false, VizMark.MODERN_LIGHT, null, null, null, "comfortable"));
}
```

`GraphGenerator` does not run `CSSChartStyles.apply` itself (`VGraphPair:1406` does), so a CSS tier set in the builder reaches the render.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=ChartGapResolverTest,AxisTitleGapTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure on the new resolver signatures and `*ForMode` methods.

- [ ] **Step 3: Implement** the resolver changes, the per-mode functions, the `VSDensityDefaults` visibility change and the three `GraphGenerator` call sites.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=ChartGapResolverTest,AxisTitleGapTest,ChartGapPersistenceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass, including the unchanged `unmarkedChartWithACustomTitleAndADescriptorGapStaysAtZero`. Then run `ChartGapResolverTest` alone once more.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java core/src/test/java/inetsoft/report/composition/graph/AxisTitleGapTest.java
git -C "$WT" commit -m "Bug #79136, scale chart gap defaults with density and honour an author's zero"
```

---

### Task 3: A legacy custom title takes an author's gap, never a stylesheet's

C1; spec §5.2 line 6317.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java`
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` (`:6315-6317`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java`
- Test: `core/src/test/java/inetsoft/report/composition/graph/AxisTitleGapTest.java`

**Interfaces:**
- Consumes: `TitleDescriptor.hasLabelGapUserValue()` (Task 1); the widened `xTitleGap` builder (Task 2).
- Produces: `public static int axisTitleGapSource(int labelGap, boolean authorSet, boolean customTitle, VizContext ctx)` in `VSChartChromeDefaults`. It returns `labelGap` when `ctx.modern || !customTitle || authorSet`, else 0. It is the title gap before resolution; Task 5 reuses it so the dialog shows what the chart draws.
- `GraphGenerator:6317` becomes `int customTitleGap = VSChartChromeDefaults.axisTitleGapSource(titleDesc.getLabelGap(), titleDesc.hasLabelGapUserValue(), true, vizContext);`. Reword the `:6315-6316` comment to say a legacy custom title takes only an author's gap. `:6345` is untouched.

- [ ] **Step 1: Write the failing tests**

`ChartGapResolverTest`:

```java
@Test
void aLegacyCustomTitleTakesOnlyAnAuthorGap() {
   assertEquals(0, VSChartChromeDefaults.axisTitleGapSource(20, false, true, LEGACY));
   assertEquals(6, VSChartChromeDefaults.axisTitleGapSource(6, true, true, LEGACY));
   assertEquals(20, VSChartChromeDefaults.axisTitleGapSource(20, false, false, LEGACY));
   assertEquals(20, VSChartChromeDefaults.axisTitleGapSource(20, false, true, modern));
}
```

`AxisTitleGapTest`: delete `unmarkedChartWithACustomTitleAndADescriptorGapStaysAtZero`, and then the four-argument `xTitleGap` overload, which has no caller left. Add:

```java
@Test
void unmarkedChartWithACustomTitleTakesAnAuthorGap() {
   assertEquals(6, xTitleGap(true, null, "Custom X Title", 6, null, null));
}

@Test
void unmarkedChartWithACustomTitleIgnoresAStylesheetGap() {
   // a legacy custom title has never moved for a format.css label_gap
   assertEquals(0, xTitleGap(true, null, "Custom X Title", null, 99, null));
}

@Test
void unmarkedChartWithADerivedTitleStillTakesAStylesheetGap() {
   assertEquals(20, xTitleGap(true, null, null, null, 20, null));
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=ChartGapResolverTest,AxisTitleGapTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`axisTitleGapSource` not found).

- [ ] **Step 3: Implement** `axisTitleGapSource` and the `GraphGenerator:6317` change.

- [ ] **Step 4: Run the tests to verify they pass**

Run: the Step 2 command. Expected: all pass. `unmarkedChartWithADerivedTitleStillTakesAStylesheetGap` passed before this task too; it guards the derived-title path.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java core/src/test/java/inetsoft/uql/viewsheet/internal/ChartGapResolverTest.java core/src/test/java/inetsoft/report/composition/graph/AxisTitleGapTest.java
git -C "$WT" commit -m "Bug #79136, let a legacy custom axis title take an author's gap"
```

---

### Task 4: Axis Properties dialog model

D2, D4, C2; spec §5.3.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/AxisLabelPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java` (constructor after the label pane is filled, `:107-120`; `updateAxisPropertyDialogModel` after `:294-299`)
- Test: `core/src/test/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModelTest.java`

**Interfaces:**
- Consumes: `AxisDescriptor.hasLabelGapValue()`, `hasLabelGapUserValue()`, `resetLabelGap(Type)` (Task 1); `resolveAxisLabelGap(int, boolean, VizContext)` (Task 2).
- Produces, on `AxisLabelPaneModel` (fields `private int labelGap; private Boolean labelGapFollowsDefault;`): `int getLabelGap()`, `void setLabelGap(int)`, `Boolean getLabelGapFollowsDefault()`, `void setLabelGapFollowsDefault(Boolean)`.
- Produces, on `AxisPropertyDialogModel`, package-private and static so tests can call them without a `ChartArea` (the constructor dereferences one in `GraphUtil.getAxisItems`):
  - `static int shownLabelGap(AxisDescriptor axisDesc, VizContext ctx)`: the resolved gap, with 0 mapped to 2, which is what `DefaultAxis` draws.
  - `static Boolean labelGapFollowsDefault(AxisDescriptor axisDesc, VizContext ctx)`: `ctx.modern ? !axisDesc.hasLabelGapUserValue() : null`.
- The constructor sets both pane fields from these helpers. The update follows `LegendFormatDialogModel:140-160`:
  - `null`: write `setLabelGap(value)` only if `value != shownLabelGap(axisDesc, ctx)`.
  - `TRUE`: `resetLabelGap(CompositeValue.Type.USER)`.
  - `FALSE`: `setLabelGap(value)`.

- [ ] **Step 1: Write the failing tests** (add the shared `modernAt` helper)

```java
@Test
void aLegacyAxisShowsTheTwoPixelsItDraws() {
   assertEquals(2, AxisPropertyDialogModel.shownLabelGap(new AxisDescriptor(), VizContext.LEGACY));
   assertNull(AxisPropertyDialogModel.labelGapFollowsDefault(new AxisDescriptor(), VizContext.LEGACY));
}

@Test
void aModernAxisShowsTheDashboardDensityDefault() {
   VizContext ctx = modernAt("comfortable");

   assertEquals(12, AxisPropertyDialogModel.shownLabelGap(new AxisDescriptor(), ctx));
   assertEquals(Boolean.TRUE, AxisPropertyDialogModel.labelGapFollowsDefault(new AxisDescriptor(), ctx));
}

@Test
void aStylesheetGapIsShownWithTheBoxTicked() {
   AxisDescriptor axis = new AxisDescriptor();
   axis.setLabelGap(20, CompositeValue.Type.CSS);
   VizContext ctx = modernAt("compact");

   assertEquals(20, AxisPropertyDialogModel.shownLabelGap(axis, ctx));
   assertEquals(Boolean.TRUE, AxisPropertyDialogModel.labelGapFollowsDefault(axis, ctx));
}

@Test
void anAuthorValueUnticksTheBox() {
   AxisDescriptor axis = new AxisDescriptor();
   axis.setLabelGap(5);
   VizContext ctx = modernAt("compact");

   assertEquals(5, AxisPropertyDialogModel.shownLabelGap(axis, ctx));
   assertEquals(Boolean.FALSE, AxisPropertyDialogModel.labelGapFollowsDefault(axis, ctx));
}

@Test
void aLegacyOkWithoutEditsStoresNothing() throws Exception {
   AxisDescriptor axis = new AxisDescriptor();
   AxisLabelPaneModel pane = axisPropertyDialogModel.getAxisLabelPaneModel();
   pane.setLabelGap(AxisPropertyDialogModel.shownLabelGap(axis, VizContext.LEGACY));
   pane.setLabelGapFollowsDefault(null);

   axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, VizContext.LEGACY);

   assertFalse(axis.hasLabelGapUserValue());
}

@Test
void aLegacyEditIsStored() throws Exception {
   AxisDescriptor axis = new AxisDescriptor();
   axisPropertyDialogModel.getAxisLabelPaneModel().setLabelGap(1);

   axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, VizContext.LEGACY);

   assertEquals(1, axis.getLabelGap());
}

@Test
void tickingTheBoxHandsTheGapBackToTheStylesheet() throws Exception {
   AxisDescriptor axis = new AxisDescriptor();
   axis.setLabelGap(20, CompositeValue.Type.CSS);
   axis.setLabelGap(6);
   axisPropertyDialogModel.getAxisLabelPaneModel().setLabelGapFollowsDefault(true);

   axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, modernAt("compact"));

   assertFalse(axis.hasLabelGapUserValue());
   assertEquals(20, axis.getLabelGap());
}

@Test
void untickingStoresTheShownValue() throws Exception {
   AxisDescriptor axis = new AxisDescriptor();
   AxisLabelPaneModel pane = axisPropertyDialogModel.getAxisLabelPaneModel();
   pane.setLabelGap(8);
   pane.setLabelGapFollowsDefault(false);

   axisPropertyDialogModel.updateAxisPropertyDialogModel(axis, "", "", false, modernAt("compact"));

   assertTrue(axis.hasLabelGapUserValue());
   assertEquals(8, axis.getLabelGap());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=AxisPropertyDialogModelTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`shownLabelGap`, `setLabelGap` on the pane not found).

- [ ] **Step 3: Implement** the pane fields, the two helpers, the constructor lines and the three-way update.

- [ ] **Step 4: Run the tests to verify they pass**

Run: the Step 2 command. Expected: all pass, including the three existing tests.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/graph/model/dialog/AxisLabelPaneModel.java core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java core/src/test/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModelTest.java
git -C "$WT" commit -m "Bug #79136, carry the axis label distance through the axis properties dialog"
```

---

### Task 5: Axis Title dialog model

D2, D4, C1; spec §5.4.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/TitleFormatPaneModel.java` (fields, accessors, `toString`)
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/TitleFormatDialogModel.java` (constructor `:31`, `updateTitleProperties` `:63`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/RegionPropertyDialogService.java` (`:266`, `:293`: pass `VizContext.of(chartAssemblyInfo)`, as the axis and legend paths in the same class do)
- Test: `core/src/test/java/inetsoft/web/graph/model/dialog/TitleFormatDialogModelTest.java` (create; same context annotations as `LegendFormatDialogModelTest`)

**Interfaces:**
- Consumes: Task 1's `TitleDescriptor` accessors; `resolveAxisTitleGap(int, boolean, boolean, VizContext)` (Task 2); `axisTitleGapSource(int, boolean, boolean, VizContext)` (Task 3).
- Produces, on `TitleFormatPaneModel`: `int getLabelGap()`, `void setLabelGap(int)`, `Boolean getLabelGapFollowsDefault()`, `void setLabelGapFollowsDefault(Boolean)`.
- Produces, on `TitleFormatDialogModel`:
  - `public TitleFormatDialogModel(TitleDescriptor titleDesc, String oldTitle, VizContext ctx)`
  - `public void updateTitleProperties(TitleDescriptor titleDesc, VizContext ctx)`
  - `static int shownLabelGap(TitleDescriptor titleDesc, VizContext ctx)`: `resolveAxisTitleGap(axisTitleGapSource(getLabelGap(), hasLabelGapUserValue(), customTitle, ctx), hasLabelGapValue(), true, ctx)`. Here `customTitle` is `titleDesc.getTitle()` being non-empty, the test `GraphGenerator:6301` applies.
- The constructor sets `labelGapFollowsDefault` to `ctx.modern ? !hasLabelGapUserValue() : null`. The update uses Task 4's three-way logic against `shownLabelGap`.
- These are the only two callers (`git grep` for `new TitleFormatDialogModel(` and `updateTitleProperties(`; ignore the stray `handler/Chart`).

- [ ] **Step 1: Write the failing tests** (add the shared `modernAt` helper)

```java
@Test
void aModernTitleShowsTheDashboardDensityDefault() {
   TitleFormatPaneModel pane =
      new TitleFormatDialogModel(new TitleDescriptor(), "X", modernAt("dense")).getTitleFormatPaneModel();

   assertEquals(2, pane.getLabelGap());
   assertEquals(Boolean.TRUE, pane.getLabelGapFollowsDefault());
}

@Test
void anAuthorZeroIsShownUntickedAndKept() {
   TitleDescriptor title = new TitleDescriptor();
   title.setLabelGap(0);
   VizContext ctx = modernAt("compact");
   TitleFormatDialogModel dialog = new TitleFormatDialogModel(title, "X", ctx);

   assertEquals(0, dialog.getTitleFormatPaneModel().getLabelGap());
   assertEquals(Boolean.FALSE, dialog.getTitleFormatPaneModel().getLabelGapFollowsDefault());

   dialog.updateTitleProperties(title, ctx);

   assertTrue(title.hasLabelGapUserValue());
   assertEquals(0, title.getLabelGap());
}

@Test
void aLegacyCustomTitleShowsWhatItDraws() {
   TitleDescriptor title = new TitleDescriptor();
   title.setLabelGap(20, CompositeValue.Type.CSS);

   assertEquals(20, TitleFormatDialogModel.shownLabelGap(title, VizContext.LEGACY));

   title.setTitle("Custom X Title");

   assertEquals(0, TitleFormatDialogModel.shownLabelGap(title, VizContext.LEGACY));
   assertNull(new TitleFormatDialogModel(title, "X", VizContext.LEGACY)
                 .getTitleFormatPaneModel().getLabelGapFollowsDefault());
}

@Test
void aLegacyOkWithoutEditsStoresNothing() {
   TitleDescriptor title = new TitleDescriptor();
   TitleFormatDialogModel dialog = new TitleFormatDialogModel(title, "X", VizContext.LEGACY);

   dialog.updateTitleProperties(title, VizContext.LEGACY);

   assertFalse(title.hasLabelGapUserValue());
}

@Test
void tickingTheBoxClearsOnlyTheGap() {
   TitleDescriptor title = new TitleDescriptor();
   title.setLabelGap(6);
   VizContext ctx = modernAt("compact");
   TitleFormatDialogModel dialog = new TitleFormatDialogModel(title, "X", ctx);
   dialog.getTitleFormatPaneModel().setLabelGapFollowsDefault(true);

   dialog.updateTitleProperties(title, ctx);

   assertFalse(title.hasLabelGapUserValue());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=TitleFormatDialogModelTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (three-argument constructor not found).

- [ ] **Step 3: Implement** the pane fields, the constructor and update signatures, `shownLabelGap`, and the two `RegionPropertyDialogService` call sites.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=TitleFormatDialogModelTest,AxisPropertyDialogModelTest,LegendFormatDialogModelTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass, and `core` compiles. Any missed caller of the old signatures fails compilation here.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/graph/model/dialog/TitleFormatPaneModel.java core/src/main/java/inetsoft/web/graph/model/dialog/TitleFormatDialogModel.java core/src/main/java/inetsoft/web/composer/vs/dialog/RegionPropertyDialogService.java core/src/test/java/inetsoft/web/graph/model/dialog/TitleFormatDialogModelTest.java
git -C "$WT" commit -m "Bug #79136, carry the axis title distance through the title dialog"
```

---

### Task 6: Axis Properties → Label: the distance row

Spec §4, §6.

**Files:**
- Modify: `web/projects/portal/src/app/graph/model/dialog/axis-label-panel-model.ts` (add `labelGap: number;` and `labelGapFollowsDefault?: boolean;`)
- Modify: `web/projects/portal/src/app/graph/dialog/axis-label-pane.component.ts`
- Modify: `web/projects/portal/src/app/graph/dialog/axis-label-pane.component.html` (new row at the end of the Options fieldset)
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties`
- Test: `web/projects/portal/src/app/graph/dialog/axis-label-pane.tl.spec.ts` (`createModel` gains `labelGap: 8`; new tests)
- Test: `web/projects/portal/src/app/graph/dialog/axis-property-dialog.spec.ts` (`createLabelModel` gains `labelGap: 0`, or the spec no longer type-checks)

**Interfaces:**
- Consumes: JSON fields `labelGap` / `labelGapFollowsDefault` on `axisLabelPaneModel` (Task 4).
- The component mirrors `legend-format-general-pane.component.ts:155-170`:
  - implements `OnInit`;
  - sets `showLabelGapFollowDefault = this.model.labelGapFollowsDefault != null` in `ngOnInit`;
  - has `labelGapFollowDefaultChanged(follows: boolean): void`, which sets the flag and leaves `labelGap` alone;
  - adds `NumberStepperComponent` to `imports`.
- The template copies the legend's stepper row (`legend-format-general-pane.component.html:72-93`):
  - the wrapper gets `[title]="'_#(composer.vs.chart.labelDistanceTip)'"`;
  - the stepper is `[(ngModel)]="model.labelGap" [min]="1" ariaLabel="_#(Label Distance from Axis)" [disabled]="model.labelGapFollowsDefault === true"`;
  - the label is `_#(Label Distance from Axis)`;
  - the checkbox has `id="axisLabelGapFollowsDefault"` and label `_#(composer.vs.followDefaultDensity)`.
- i18n, in their alphabetical blocks:

```
Label\ Distance\ from\ Axis=Label Distance from Axis
composer.vs.chart.labelDistanceTip=Space between the axis line and its labels. Applies perpendicular to the axis only.
```

- [ ] **Step 1: Install frontend dependencies once**

Run: `cd "$WT/web" && npm ci`
Expected: completes. It needs the GitHub PAT in `~/.npmrc`; a junctioned `node_modules` does not work with Vitest.

- [ ] **Step 2: Add the two model fields, then write the failing tests**

Add the fields to `axis-label-panel-model.ts` first. The spec literals that gain `labelGap` would not type-check without them, and the run below would fail for the wrong reason. Then, in `axis-label-pane.tl.spec.ts`:

```ts
const DISTANCE = "_#(Label Distance from Axis)";
const FOLLOW = "_#(composer.vs.followDefaultDensity)";

describe("AxisLabelPane — label distance", () => {
   it("disables the stepper while the default is followed", async () => {
      await renderPane({ labelGap: 8, labelGapFollowsDefault: true });

      expect(screen.getByRole("spinbutton", { name: DISTANCE })).toBeDisabled();
      expect(screen.getByLabelText(FOLLOW)).toBeChecked();
   });

   it("hides the follow-default box on a legacy chart", async () => {
      await renderPane({ labelGap: 2 });

      expect(screen.queryByLabelText(FOLLOW)).not.toBeInTheDocument();
      expect(screen.getByRole("spinbutton", { name: DISTANCE })).toBeEnabled();
   });

   it("never goes below 1", async () => {
      await renderPane({ labelGap: 2 });

      expect(screen.getByRole("spinbutton", { name: DISTANCE })).toHaveAttribute("aria-valuemin", "1");
   });

   it("keeps the shown value when the box is unticked", async () => {
      const user = userEvent.setup();
      const { fixture } = await renderPane({ labelGap: 8, labelGapFollowsDefault: true });

      await user.click(screen.getByLabelText(FOLLOW));

      expect(fixture.componentInstance.model.labelGapFollowsDefault).toBe(false);
      expect(fixture.componentInstance.model.labelGap).toBe(8);
      expect(screen.getByRole("spinbutton", { name: DISTANCE })).toBeEnabled();
   });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd "$WT/web" && npx ng run portal:test-tl --include="**/axis-label-pane.tl.spec.ts"`
Expected: the four new tests fail (no spinbutton named "Label Distance from Axis"); the existing groups pass.

- [ ] **Step 4: Implement** the component, template and i18n keys.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npx ng run portal:test-tl --include="**/axis-label-pane.tl.spec.ts"`, then `npx ng test portal --include="**/axis-property-dialog.spec.ts"`.
Expected: both pass.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" add web/projects/portal/src/app/graph/model/dialog/axis-label-panel-model.ts web/projects/portal/src/app/graph/dialog/axis-label-pane.component.ts web/projects/portal/src/app/graph/dialog/axis-label-pane.component.html web/projects/portal/src/app/graph/dialog/axis-label-pane.tl.spec.ts web/projects/portal/src/app/graph/dialog/axis-property-dialog.spec.ts core/src/main/resources/inetsoft/util/srinter.properties
git -C "$WT" commit -m "Bug #79136, add the label distance row to axis properties"
```

---

### Task 7: Axis Title dialog: the distance row

Spec §4, §6.

**Files:**
- Modify: `web/projects/portal/src/app/graph/model/dialog/title-format-pane-model.ts` (add `labelGap: number;` and `labelGapFollowsDefault?: boolean;`)
- Modify: `web/projects/portal/src/app/graph/dialog/title-format-pane.component.ts` (add `FormsModule` and `NumberStepperComponent` to `imports`; `OnInit` and the follow-default handler as in Task 6)
- Modify: `web/projects/portal/src/app/graph/dialog/title-format-pane.component.html` (new row in the Title fieldset, after the title combo row)
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties`
- Test: `web/projects/portal/src/app/graph/dialog/title-format-dialog.spec.ts` (the mock model gains `labelGap: 0`; new tests)

**Interfaces:**
- Consumes: JSON fields `labelGap` / `labelGapFollowsDefault` on `titleFormatPaneModel` (Task 5).
- The row is Task 6's row with these differences:
  - min is `[min]="0"`;
  - the stepper's `ariaLabel` and the label are `_#(Title Distance from Axis)`;
  - the tooltip key is `composer.vs.chart.titleDistanceTip`;
  - the checkbox id is `titleLabelGapFollowsDefault`.
  The pane's form is `ngNoForm`, so `[(ngModel)]` needs no `name`; this matches the axis label pane.
- i18n:

```
Title\ Distance\ from\ Axis=Title Distance from Axis
composer.vs.chart.titleDistanceTip=Space between the axis title and the axis labels (or the plot, when labels are hidden). The default varies with label visibility.
```

- [ ] **Step 1: Add the two model fields to `title-format-pane-model.ts`, then write the failing tests** in `title-format-dialog.spec.ts`. Let `createMockTitleFormatDialogModel` take optional `labelGap` / `labelGapFollowsDefault`.

```ts
it("shows the title distance disabled while the default is followed", () => {
   fixture = TestBed.createComponent(TitleFormatDialog);
   fixture.componentInstance.model = createMockTitleFormatDialogModel("state", 4, true);
   fixture.detectChanges();

   const stepper: HTMLInputElement = fixture.nativeElement.querySelector(
      "input[role=spinbutton][aria-label='_#(Title Distance from Axis)']");
   expect(stepper.disabled).toBe(true);
   expect(stepper.getAttribute("aria-valuemin")).toBe("0");
   expect(fixture.nativeElement.querySelector("#titleLabelGapFollowsDefault")).not.toBeNull();
});

it("hides the follow-default box on a legacy chart", () => {
   fixture = TestBed.createComponent(TitleFormatDialog);
   fixture.componentInstance.model = createMockTitleFormatDialogModel("state", 0, undefined);
   fixture.detectChanges();

   expect(fixture.nativeElement.querySelector("#titleLabelGapFollowsDefault")).toBeNull();
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd "$WT/web" && npx ng test portal --include="**/title-format-dialog.spec.ts"`
Expected: the two new tests fail; the existing "should revert to default title" passes.

- [ ] **Step 3: Implement** the component, template and i18n keys.

- [ ] **Step 4: Run the tests to verify they pass**

Run: the Step 2 command. Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add web/projects/portal/src/app/graph/model/dialog/title-format-pane-model.ts web/projects/portal/src/app/graph/dialog/title-format-pane.component.ts web/projects/portal/src/app/graph/dialog/title-format-pane.component.html web/projects/portal/src/app/graph/dialog/title-format-dialog.spec.ts core/src/main/resources/inetsoft/util/srinter.properties
git -C "$WT" commit -m "Bug #79136, add the title distance row to the axis title dialog"
```

---

### Task 8: Legend: "Distance from Chart", and the box ticked under a stylesheet

D2; spec §4, §5.6, §6.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java` (`:58`)
- Modify: `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.html` (`:73-78`)
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties` (remove `Legend\ Gap` at `:1694`; add the keys below)
- Test: `core/src/test/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModelTest.java`
- Test: `web/projects/portal/src/app/graph/dialog/legend-format-general-pane.spec.ts`

**Interfaces:**
- Consumes: `LegendsDescriptor.hasGapUserValue()` (Task 1).
- Produces: `static Boolean gapFollowsDefault(LegendsDescriptor legendsDesc, VizContext ctx)` on `LegendFormatDialogModel`, `ctx.modern ? !legendsDesc.hasGapUserValue() : null`. Line `:58` calls it. The shown value (`:56-57`) and the write logic (`:140-160`) are unchanged.
- Template:
  - `:78` becomes `<label>_#(Distance from Chart)</label>`;
  - the stepper gains `ariaLabel="_#(Distance from Chart)"`;
  - its wrapper gains `[title]="'_#(composer.vs.chart.legendDistanceTip)'"`.
  Nothing else in the row changes.
- i18n:

```
Distance\ from\ Chart=Distance from Chart
composer.vs.chart.legendDistanceTip=Space between the legends and the plot area. Applies to all legends.
```

- [ ] **Step 1: Write the failing tests**

`LegendFormatDialogModelTest`:

```java
@Test
public void aStylesheetGapLeavesTheBoxTicked() {
   LegendsDescriptor legends = new LegendsDescriptor();
   legends.setGap(20, CompositeValue.Type.CSS);

   assertEquals(Boolean.TRUE,
                LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.of(VizMark.MODERN_LIGHT)));
}

@Test
public void anAuthorZeroUnticksTheBox() {
   LegendsDescriptor legends = new LegendsDescriptor();
   legends.setGap(0);

   assertEquals(Boolean.FALSE,
                LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.of(VizMark.MODERN_LIGHT)));
   assertNull(LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.LEGACY));
}
```

`legend-format-general-pane.spec.ts`:

```ts
it("labels the legend distance 'Distance from Chart'", () => {
   const fixture = TestBed.createComponent(LegendFormatGeneralPane);
   fixture.componentInstance.model = createModel();
   fixture.componentInstance.form = new FormGroup({});
   fixture.detectChanges();

   const labels: string[] = Array.from(fixture.nativeElement.querySelectorAll("label"))
      .map((e: HTMLElement) => e.textContent.trim());
   expect(labels).toContain("_#(Distance from Chart)");
   expect(labels).not.toContain("_#(Legend Gap)");
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=LegendFormatDialogModelTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: test-compile failure (`gapFollowsDefault` not found).
Run: `cd "$WT/web" && npx ng test portal --include="**/legend-format-general-pane.spec.ts"`
Expected: the new test fails.

- [ ] **Step 3: Implement** the helper, the `:58` change, the template change and the i18n edit.

- [ ] **Step 4: Run the tests to verify they pass**

Run: both Step 2 commands. Expected: all pass. Then run `git -C "$WT" grep -n "Legend Gap" -- core/src/main web/projects` and expect no hits.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModel.java web/projects/portal/src/app/graph/dialog/legend-format-general-pane.component.html core/src/main/resources/inetsoft/util/srinter.properties core/src/test/java/inetsoft/web/graph/model/dialog/LegendFormatDialogModelTest.java web/projects/portal/src/app/graph/dialog/legend-format-general-pane.spec.ts
git -C "$WT" commit -m "Bug #79136, relabel the legend gap as distance from chart"
```

---

### Task 9: Whole-branch verification and the manual checklist

**Files:** none changed, unless a check fails.

- [ ] **Step 1: Run every touched Java class together, then each alone**

Run: `./mvnw -q test -pl core "-Dtest=ChartGapPersistenceTest,ChartGapResolverTest,AxisTitleGapTest,AxisPropertyDialogModelTest,TitleFormatDialogModelTest,LegendFormatDialogModelTest,VizContextTest,ViewsheetInfoVizDensityTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all pass. Then run each of the first six classes with `-Dtest=<one class>`.

- [ ] **Step 2: Run the four frontend specs**, one per run, as in Tasks 6-8. Expected: all pass.

- [ ] **Step 3: Manual checks, for the user**

Run these against a dev server built from `$WT`. They are the spec's §8 checks 1-3 and 5-12, plus these changes:

- **Check 4 (CSS):** with `format.css` `label_gap: 20` on the axis, the box is **ticked** and the field shows 20, disabled. Untick and set 25: it wins over CSS. Tick again: CSS applies again. OK without edits stores nothing.
- **Leftover zeros:** import a chart asset whose XML carries `labelGap="0"` on `axisDescriptor` / `titleDescriptor` and `gap="0"` on `legendsDescriptor`; any file under `E:/InetSoft Work/Worthington Assets/asset.dat.d` that `grep -l 'labelGap="0"'` finds will do. Modernize it. The spacing is 8 / 4 / 14 at compact and every box is ticked. Restore a bookmark saved before Modernize: the same.
- **Legacy tab-through:** on a legacy chart, open Axis Properties → Label. The field shows 2. Tab into it and out, then OK. Nothing moves, and reopening shows 2 again.
- **Legacy custom title under CSS:** a legacy chart with custom axis title text and a format.css title `label_gap: 20` does not move, and its Axis Title dialog shows 0. Setting 10 in the dialog moves it (spec check 6).
- **Undo:** change the axis label distance, OK, Undo. The old spacing returns.

- [ ] **Step 4: Report** the results. Do not push. The PR (base `epic-74519`, community only) waits for the user. If #7114 has merged by then, rebase `bug-79136` onto `origin/epic-74519` first so the PR carries only this work.
