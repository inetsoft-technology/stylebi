# Visualization Phase 6C — Smooth Lines Default Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under modern-visualization mode, line charts render smooth — both when created and when an existing chart is switched to Line — reversibly, without altering any saved viewsheet.

**Architecture:** Reuses Phase 6B's mechanism unchanged: a design-time seed written under `VSObjectChromeDefaults.isModern()`, plus a marker (`modernSmoothSeed`) so a gate-aware `isSmoothLines()` returns `false` when the gate is off. The seed is written at chart-assembly creation (type-agnostic) and by the chart-type transition matrix. An explicit user toggle clears the marker and owns the value thereafter.

**Tech Stack:** Java 21, Maven (`./mvnw`), JUnit 5 + Spring Test (`@SreeHome`, `@Tag("core")`), all changes in `community/core`.

## Global Constraints

- **DO NOT COMMIT. DO NOT CREATE BRANCHES.** The repository owner commits manually. Every "Commit" step is **overridden**: stage nothing, commit nothing, leave work in the working tree. Never run `git commit`, `git add`, `git checkout`, `git switch`, `git stash`, `git reset`, or `git clean`.
- Work on the current branch, `viz-updates`, which carries Phase 6B (this phase depends on it). Do not switch branches.
- Community-only. No enterprise-side change.
- `PlotDescriptor.smoothLines` field default stays **`false`**. The `true` value arrives only via the gated seed.
- Marker field name is exactly `modernSmoothSeed`. Do **not** rename or reuse 6B's `modernCornerSeed` — the two must stay independent (spec D2).
- Do **not** touch the area or circular branches of either existing hook. Both already default smooth, ungated, and gating them would regress non-modern orgs (spec D5).
- Do **not** change `isSmoothLinesVisible()`. Step, jump and step-area variants stay hidden and unaffected (spec D7).
- The gate is the existing `VSObjectChromeDefaults.isModern()`. Introduce no new SreeEnv property.
- `core/pom.xml:996` sets surefire `<groups>core</groups>`. A test class without `@Tag("core")` is silently excluded and appears to pass by not existing. Every test class touched or added must carry `@Tag("core")` **and** whatever harness it needs. After every run, read the executed count and compare it to what you expect.
- `new PlotDescriptor()` requires a Spring-backed `SreeEnv` (`PlotDescriptor:1969` initializes a field via `SreeEnv.getProperty("webmap.default")`), so any test constructing one needs the full harness matching `PlotDescriptorTextLayoutTest:21-26`.
- Style: 3-space indent, `if(cond) {` no space after `if`, javadoc on new public methods, short-clause `//` comments, no ticket/PR/phase/design-doc references in source.
- Line numbers verified 2026-07-29; re-confirm before editing.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `core/src/test/.../web/binding/controller/ChangeChartTypeServiceSmoothLinesTransitionTest.java` | **Repair first.** 14 tests covering the matrix this phase rewrites; has never executed. | 1 |
| `core/src/main/.../uql/viewsheet/graph/PlotDescriptor.java` | `modernSmoothSeed` marker, gate-aware `isSmoothLines()`, raw accessor, marker cleared in setter, XML + `equalsContent`. | 2 |
| `core/src/test/.../uql/viewsheet/graph/PlotDescriptorXmlTest.java` | Marker round-trip, gate behavior, setter-clears-marker. Already repaired in 6B. | 2 |
| `core/src/main/.../uql/viewsheet/internal/ChartVSAssemblyInfo.java` | Type-agnostic creation seed under the gate. | 3 |
| `core/src/test/.../uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java` | Creation-seed + gate-revert tests (existing 6C-adjacent class). | 3 |
| `core/src/main/.../web/binding/controller/ChangeChartTypeService.java` | Gate-aware transition: under the gate, `→ line` sets `true`. | 4 |
| `core/src/main/.../web/graph/model/dialog/ChartPlotOptionsPaneModel.java` | No-op dialog-save guard, mirroring 6B's. | 5 |
| `core/src/test/.../web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java` | No-op-save preservation tests. Already repaired in 6B. | 5 |

**Order:** 1 → 2 → 3 → 4 → 5. Task 1 is a hard prerequisite for Task 4.

---

### Task 1: Repair the transition test class

Its 14 tests cover exactly the matrix Task 4 rewrites, and they have never run. Fix that before changing the code they guard.

**Files:**
- Modify: `core/src/test/java/inetsoft/web/binding/controller/ChangeChartTypeServiceSmoothLinesTransitionTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: a test class that actually executes. Task 4 depends on it.

- [ ] **Step 1: Add the tag and harness**

The class currently declares only `class ChangeChartTypeServiceSmoothLinesTransitionTest {` with no annotations. Add these imports:

```java
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
```

and change the declaration to match `PlotDescriptorTextLayoutTest:21-26`:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChangeChartTypeServiceSmoothLinesTransitionTest {
```

Change nothing else — no test bodies, no assertions, no helper.

- [ ] **Step 2: Run it and confirm 14 tests actually execute**

```bash
./mvnw test -pl core -Dtest=ChangeChartTypeServiceSmoothLinesTransitionTest
```
Expected: `Tests run: 14, Failures: 0, Errors: 0`. Report the executed count verbatim. (The class has 14 `@Test` methods and 16 `applySmoothLinesTransition` call sites — some tests invoke it twice. Count methods, not call sites.)

If any test **fails on an assertion**, that is a latent defect this repair exposed, not something to fix here. Stop, report the test name, its assertion, and the actual value. Do not edit the assertion and do not remove the tag.

- [ ] **Step 3: Commit** — OVERRIDDEN, do not commit. Leave in the working tree.

---

### Task 2: Marker, gate-aware read, and setter clearing

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java` — field near `:1969-1971`, accessors near `:630-640`, `parseAttributes` `:1512`, `writeAttributes` `:1698`, `equalsContent` `:1852`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/graph/PlotDescriptorXmlTest.java`

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.isModern()`.
- Produces on `PlotDescriptor`:
  - `public boolean isSmoothLines()` — effective; returns `false` when `modernSmoothSeed && !isModern()`.
  - `public boolean isSmoothLinesValue()` — raw stored value, ignores the gate.
  - `public boolean isModernSmoothSeed()` / `public void setModernSmoothSeed(boolean)`.
  - `setSmoothLines(boolean)` now clears `modernSmoothSeed`.
  - XML attribute `modernSmoothSeed`; absent ⇒ `false`.

- [ ] **Step 1: Write the failing tests**

Append to `PlotDescriptorXmlTest` (repaired in 6B, already carries the full harness and a `withGate` helper — reuse them, do not redefine):

```java
   @Test
   void smoothLines_newInstanceStillDefaultsToFalse() {
      assertFalse(new PlotDescriptor().isSmoothLinesValue());
      assertFalse(new PlotDescriptor().isModernSmoothSeed());
   }

   @Test
   void modernSmoothSeed_roundTripsTrue() throws Exception {
      PlotDescriptor written = new PlotDescriptor();
      written.setSmoothLines(true);
      written.setModernSmoothSeed(true);

      PlotDescriptor parsed = roundTrip(written);

      assertTrue(parsed.isModernSmoothSeed());
      assertTrue(parsed.isSmoothLinesValue());
   }

   @Test
   void modernSmoothSeed_legacyXmlWithoutAttributeIsFalse() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<plotDescriptor/>"));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertFalse(parsed.isModernSmoothSeed(),
                  "charts saved before this phase must not look gate-seeded");
      assertFalse(parsed.isSmoothLinesValue());
   }

   @Test
   void smoothLines_seededValueStrippedGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setSmoothLines(true);
      pd.setModernSmoothSeed(true);

      withGate("true", () -> assertTrue(pd.isSmoothLines()));
      withGate("false", () -> assertFalse(pd.isSmoothLines(),
                                         "a gate-seeded smooth reverts to straight"));
      // the raw value is never gate-dependent
      withGate("false", () -> assertTrue(pd.isSmoothLinesValue()));
   }

   @Test
   void smoothLines_userValueSurvivesGateOff() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setSmoothLines(true);   // clears the marker — this is a user-authored value

      withGate("false", () -> assertTrue(pd.isSmoothLines(),
                                        "a user-set smooth is not gate-stripped"));
   }

   @Test
   void setSmoothLines_clearsModernSmoothSeed() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setSmoothLines(true);
      pd.setModernSmoothSeed(true);

      pd.setSmoothLines(false);

      assertFalse(pd.isModernSmoothSeed(), "an explicit write makes the value user-authored");
      assertFalse(pd.isSmoothLinesValue());
   }

   @Test
   void modernSmoothSeed_participatesInEqualsContent() {
      PlotDescriptor a = new PlotDescriptor();
      a.setSmoothLines(true);
      a.setModernSmoothSeed(true);

      PlotDescriptor b = new PlotDescriptor();
      b.setSmoothLines(true);

      assertFalse(a.equalsContent(b), "a seeded descriptor differs from a user-authored one");
   }
```

Note `smoothLines_userValueSurvivesGateOff` relies on the setter clearing the marker — that is intentional and pins the D6 behavior from the consumer's side.

- [ ] **Step 2: Run to verify they fail**

```bash
./mvnw test -pl core -Dtest=PlotDescriptorXmlTest
```
Expected: FAIL to compile — `cannot find symbol: isSmoothLinesValue()`, `isModernSmoothSeed()`, `setModernSmoothSeed(boolean)`.

- [ ] **Step 3: Write the implementation**

**Field** — beside `smoothLines` at `:1969-1971`, keeping that field's existing comment intact:

```java
   // Gate marker for smoothLines: true means modern mode seeded it rather than a user setting it, so
   // the gate may take it away again. Independent of modernCornerSeed by design — the two mark disjoint
   // chart families and must not un-gate each other.
   private boolean modernSmoothSeed = false;
```

**Accessors** — replace the existing `isSmoothLines()`/`setSmoothLines()` pair at `:630-640` with:

```java
   /** Effective smooth-lines flag; a gate-seeded value collapses to false when the gate is off. */
   public boolean isSmoothLines() {
      return modernSmoothSeed && !VSObjectChromeDefaults.isModern() ? false : smoothLines;
   }

   /** Raw stored flag, for serialization and content comparison. */
   public boolean isSmoothLinesValue() {
      return smoothLines;
   }

   public void setSmoothLines(boolean smoothLines) {
      this.smoothLines = smoothLines;
      // an explicit write makes the value user-authored, so it stops tracking the gate
      this.modernSmoothSeed = false;
   }

   public boolean isModernSmoothSeed() {
      return modernSmoothSeed;
   }

   public void setModernSmoothSeed(boolean modernSmoothSeed) {
      this.modernSmoothSeed = modernSmoothSeed;
   }
```

`VSObjectChromeDefaults` is already imported (6B added it).

**`parseAttributes()`** — add after the existing `smoothLines` parse at `:1512`. Leave that line unchanged:

```java
      modernSmoothSeed = "true".equals(Tool.getAttribute(node, "modernSmoothSeed"));
```

**`writeAttributes()`** — the existing line at `:1698` already writes the raw field; leave it and add:

```java
      writer.print(" modernSmoothSeed=\"" + modernSmoothSeed + "\" ");
```

**`equalsContent()`** — beside `smoothLines == desc.smoothLines` at `:1852`:

```java
         modernSmoothSeed == desc.modernSmoothSeed &&
```

`clone()` needs no change (primitive, copied by `super.clone()`).

- [ ] **Step 4: Verify the setter change is safe at every call site**

`setSmoothLines()` now clears the marker, so any caller that sets the marker **before** calling the setter would have its seed destroyed. Enumerate every call site and confirm each is marker-after-setter or marker-irrelevant:

```bash
grep -rn "setSmoothLines" --include=*.java core/src/main
```

Report what you found at each. Known sites to check: `VSWizardBindingHandler:872`, `ChangeChartTypeService:328` and `:331`, `ChartPlotOptionsPaneModel:199`, `ChartProcessor:243` (script binding). If any sets the marker first, STOP and report rather than proceeding.

- [ ] **Step 5: Run to verify they pass**

```bash
./mvnw test -pl core -Dtest=PlotDescriptorXmlTest
```
Expected: 22 tests run (15 from 6B + 7 new), 0 failures. Report the actual count.

- [ ] **Step 6: Run the regression suites**

```bash
./mvnw test -pl core -Dtest='PlotDescriptor*Test,ChartVSAScriptableTest,ChangeChartTypeService*Test,CSSChartStylesModernChromeTest'
```
Expected: all green, including Task 1's now-executing 14 transition tests.

- [ ] **Step 7: Commit** — OVERRIDDEN, do not commit.

---

### Task 3: Type-agnostic creation seed

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` (`setDefaultFormat`, beside 6B's bar-radius seed)
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfoBarRoundingTest.java`

**Interfaces:**
- Consumes: `PlotDescriptor.setSmoothLines(boolean)`, `setModernSmoothSeed(boolean)`, `isSmoothLines()` (Task 2).
- Produces: after `new ChartVSAssemblyInfo().initDefaultFormat()` under the gate, `getChartDescriptor().getPlotDescriptor().isSmoothLines()` is `true` and `isModernSmoothSeed()` is `true`.

- [ ] **Step 1: Write the failing tests**

Append to `ChartVSAssemblyInfoBarRoundingTest` (already carries the full harness and a `withGate` helper and a `newChartPlot()` helper — reuse them):

```java
   @Test
   void smoothLinesSeededUnderGate() {
      withGate("true", () -> {
         PlotDescriptor plot = newChartPlot();
         assertTrue(plot.isSmoothLines());
         assertTrue(plot.isModernSmoothSeed(), "seeded value is marked as gate-owned");
      });
   }

   @Test
   void smoothLinesNotSeededGateOff() {
      withGate("false", () -> {
         PlotDescriptor plot = newChartPlot();
         assertFalse(plot.isSmoothLines());
         assertFalse(plot.isModernSmoothSeed());
      });
   }

   @Test
   void seededSmoothLinesRevertsWhenGateTurnedOff() {
      PlotDescriptor[] holder = new PlotDescriptor[1];
      withGate("true", () -> holder[0] = newChartPlot());

      withGate("false", () -> assertFalse(holder[0].isSmoothLines(),
                                          "a chart made under the gate goes straight once it is off"));
      withGate("true", () -> assertTrue(holder[0].isSmoothLines(),
                                        "and smooths again when the gate returns"));
   }
```

- [ ] **Step 2: Run to verify they fail**

```bash
./mvnw test -pl core -Dtest=ChartVSAssemblyInfoBarRoundingTest
```
Expected: `smoothLinesSeededUnderGate` and `seededSmoothLinesRevertsWhenGateTurnedOff` FAIL. The gate-off test passes already.

- [ ] **Step 3: Write the implementation**

In `ChartVSAssemblyInfo.setDefaultFormat()`, extend the existing 6B gate block rather than adding a second `if`:

```java
      if(VSObjectChromeDefaults.isModern()) {
         PlotDescriptor plotDesc = getChartDescriptor().getPlotDescriptor();
         plotDesc.setBarCornerRadius(0.3);
         plotDesc.setModernCornerSeed(true);
         plotDesc.setSmoothLines(true);
         plotDesc.setModernSmoothSeed(true);
      }
```

Order matters: `setSmoothLines(true)` clears `modernSmoothSeed`, so `setModernSmoothSeed(true)` must follow it. Likewise `setBarCornerRadius` precedes `setModernCornerSeed`. Do not reorder.

The chart type is not known at this point, so the seed is type-agnostic and inert for non-line/area/circular families — that is intended (spec D3).

- [ ] **Step 4: Run to verify they pass**

```bash
./mvnw test -pl core -Dtest=ChartVSAssemblyInfoBarRoundingTest
```
Expected: 8 tests run (5 from 6B + 3 new), 0 failures.

- [ ] **Step 5: Confirm 6B's bar-radius seed still works**

The shared gate block was edited, so re-confirm 6B's assertions specifically. `barRadiusSeededUnderGate` and `seededBarRadiusRevertsWhenGateTurnedOff` must still pass — they are in the same class and covered by Step 4, but call it out in the report.

- [ ] **Step 6: Commit** — OVERRIDDEN, do not commit.

---

### Task 4: Gate-aware type transition

**Files:**
- Modify: `core/src/main/java/inetsoft/web/binding/controller/ChangeChartTypeService.java` — `applySmoothLinesTransition:320-333` and its call site `:203`
- Modify: `core/src/test/java/inetsoft/web/binding/controller/ChangeChartTypeServiceSmoothLinesTransitionTest.java`

**Interfaces:**
- Consumes: `PlotDescriptor.setSmoothLines`/`setModernSmoothSeed` (Task 2); `VSObjectChromeDefaults.isModern()`.
- Produces: `static void applySmoothLinesTransition(int oldType, int newType, PlotDescriptor plotDesc, boolean modern)` — a fourth parameter. Keeping the gate as a parameter rather than reading it inside preserves the method's purity, so the 14 existing matrix tests need no Spring context for the matrix logic itself.

- [ ] **Step 1: Write the failing tests**

Append to the repaired transition test class. These are **new gate-on counterparts** — do not edit the existing 14, whose assertions describe gate-off behavior and must stay exactly as they are:

```java
   @Test
   void areaToLine_underGate_setsSmoothLinesTrue() {
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeService.applySmoothLinesTransition(
         GraphTypes.CHART_AREA, GraphTypes.CHART_LINE, pd, true);
      assertTrue(pd.isSmoothLinesValue(), "under the gate a line chart stays smooth");
      assertTrue(pd.isModernSmoothSeed(), "and the value is gate-owned");
   }

   @Test
   void barToLine_underGate_setsSmoothLinesTrue() {
      // a saved bar chart switched to Line has no marker, so the transition must SET, not merely skip
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeService.applySmoothLinesTransition(
         GraphTypes.CHART_BAR, GraphTypes.CHART_LINE, pd, true);
      assertTrue(pd.isSmoothLinesValue());
      assertTrue(pd.isModernSmoothSeed());
   }

   @Test
   void circularToLine_underGate_setsSmoothLinesTrue() {
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeService.applySmoothLinesTransition(
         GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_LINE_STACK, pd, true);
      assertTrue(pd.isSmoothLinesValue());
      assertTrue(pd.isModernSmoothSeed());
   }

   @Test
   void areaToLine_gateOff_stillResetsFalse() {
      // the gate-off path must be byte-identical to pre-phase behavior
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeService.applySmoothLinesTransition(
         GraphTypes.CHART_AREA, GraphTypes.CHART_LINE, pd, false);
      assertFalse(pd.isSmoothLinesValue());
   }

   @Test
   void barToArea_underGate_stillSetsSmoothLinesTrue() {
      // area/circular were already smooth by default, ungated; the gate must not change that branch
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeService.applySmoothLinesTransition(
         GraphTypes.CHART_BAR, GraphTypes.CHART_AREA, pd, true);
      assertTrue(pd.isSmoothLinesValue());
   }
```

Then update the 16 existing calls to pass `false` as the new fourth argument, so they continue asserting gate-off behavior. That is a mechanical signature change to the call, not a change to any assertion.

- [ ] **Step 2: Run to verify they fail**

```bash
./mvnw test -pl core -Dtest=ChangeChartTypeServiceSmoothLinesTransitionTest
```
Expected: FAIL to compile — the 4-argument overload does not exist yet.

- [ ] **Step 3: Write the implementation**

Replace `applySmoothLinesTransition:320-333` with:

```java
   static void applySmoothLinesTransition(int oldType, int newType, PlotDescriptor plotDesc,
                                          boolean modern)
   {
      boolean newIsArea = newType == GraphTypes.CHART_AREA || newType == GraphTypes.CHART_AREA_STACK;
      boolean oldIsArea = oldType == GraphTypes.CHART_AREA || oldType == GraphTypes.CHART_AREA_STACK;
      boolean newIsLine = newType == GraphTypes.CHART_LINE || newType == GraphTypes.CHART_LINE_STACK;
      boolean newIsCircular = newType == GraphTypes.CHART_CIRCULAR;
      boolean oldIsCircular = oldType == GraphTypes.CHART_CIRCULAR;

      if((newIsArea && !oldIsArea) || (newIsCircular && !oldIsCircular)) {
         plotDesc.setSmoothLines(true);
      }
      else if(newIsLine) {
         if(modern) {
            // under the gate a line chart is smooth; set rather than preserve, since a saved chart
            // switched to Line carries no marker
            plotDesc.setSmoothLines(true);
            plotDesc.setModernSmoothSeed(true);
         }
         else if(oldIsArea || oldIsCircular) {
            plotDesc.setSmoothLines(false);
         }
      }
   }
```

Note the restructure: the `newIsLine` branch is now entered for *any* origin type, with the old `oldIsArea || oldIsCircular` condition moved inside the gate-off path. That preserves gate-off behavior exactly — including `barToLine_doesNotTouchSmoothLines`, since with `modern == false` and a bar origin neither inner branch fires.

Update the call site at `:203`:

```java
      applySmoothLinesTransition(oldType, newType, plotDesc, VSObjectChromeDefaults.isModern());
```

Add `import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;` if absent.

- [ ] **Step 4: Run to verify they pass**

```bash
./mvnw test -pl core -Dtest=ChangeChartTypeServiceSmoothLinesTransitionTest
```
Expected: 22 tests run (14 existing + 5 new), 0 failures. Report the actual count; all 14 originals must still pass.

- [ ] **Step 5: Run the binding regression suite**

```bash
./mvnw test -pl core -Dtest='ChangeChartTypeService*Test,ChartVSAScriptableTest'
```
Expected: green.

- [ ] **Step 6: Commit** — OVERRIDDEN, do not commit.

---

### Task 5: No-op dialog-save guard

`ChartPlotOptionsPaneModel:199` writes `plotDesc.setSmoothLines(smoothLines)` unconditionally on every dialog apply, and `:314` reads the now-gate-aware getter. Since the setter clears the marker, a user who opens Plot Options and changes something unrelated would silently un-gate smooth lines — the same defect 6B fixed for the bar radius.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModel.java:199`
- Modify: `core/src/test/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModelTest.java`

**Interfaces:**
- Consumes: `PlotDescriptor.isSmoothLines()`, `setSmoothLines(boolean)`, `isModernSmoothSeed()` (Task 2).
- Produces: no new API.

- [ ] **Step 1: Write the failing tests**

Append to `ChartPlotOptionsPaneModelTest` (repaired in 6B; carries the full harness and a `withGate` helper — reuse them). Build a seeded descriptor the same way the class's existing helpers do:

```java
   @Test
   void noOpSavePreservesModernSmoothSeed() {
      withGate("true", () -> {
         VSChartInfo info = new VSChartInfo();
         info.setChartType(GraphTypes.CHART_LINE);
         PlotDescriptor plotDesc = new PlotDescriptor();
         plotDesc.setSmoothLines(true);
         plotDesc.setModernSmoothSeed(true);

         ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
         model.updateChartPlotOptionsPaneModel(info, plotDesc);

         assertTrue(plotDesc.isModernSmoothSeed(),
                    "a no-op OK must not un-gate smooth lines");
         assertTrue(plotDesc.isSmoothLinesValue());
      });
   }

   @Test
   void uncheckingSmoothLinesClearsModernSmoothSeed() {
      withGate("true", () -> {
         VSChartInfo info = new VSChartInfo();
         info.setChartType(GraphTypes.CHART_LINE);
         PlotDescriptor plotDesc = new PlotDescriptor();
         plotDesc.setSmoothLines(true);
         plotDesc.setModernSmoothSeed(true);

         ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
         model.setSmoothLines(false);
         model.updateChartPlotOptionsPaneModel(info, plotDesc);

         assertFalse(plotDesc.isModernSmoothSeed(), "a real edit makes the value user-authored");
         assertFalse(plotDesc.isSmoothLinesValue());
      });
   }
```

- [ ] **Step 2: Run to verify the first one fails**

```bash
./mvnw test -pl core -Dtest=ChartPlotOptionsPaneModelTest
```
Expected: `noOpSavePreservesModernSmoothSeed` FAILS (marker cleared by the unconditional write). The second test passes already.

- [ ] **Step 3: Write the implementation**

Replace the unconditional write at `:199`:

```java
      plotDesc.setSmoothLines(smoothLines);
```

with a change-guarded write, mirroring 6B's bar-radius guard:

```java
      // a no-op OK must not turn a gate-seeded value into a user-authored one
      if(smoothLines != plotDesc.isSmoothLines()) {
         plotDesc.setSmoothLines(smoothLines);
      }
```

The comparison is against the **gate-aware** getter — the same value the constructor sent to the client at `:314`. Comparing against the raw accessor would make a no-op save look like an edit whenever the gate is off. `setSmoothLines` already clears the marker, so no explicit marker write is needed here.

- [ ] **Step 4: Run to verify they pass**

```bash
./mvnw test -pl core -Dtest=ChartPlotOptionsPaneModelTest
```
Expected: 51 tests run (49 from 6B + 2 new), 0 failures.

- [ ] **Step 5: Commit** — OVERRIDDEN, do not commit.

---

## Final Verification

- [ ] **Full targeted suite**

```bash
./mvnw test -pl core -Dtest='PlotDescriptor*Test,ChartVSAssemblyInfoBarRoundingTest,ChartPlotOptionsPaneModelTest,ChangeChartTypeService*Test,VSObjectChromeDefaultsTest,VSCompositeFormatRoundCornerGateTest,ChartVSAScriptableTest'
```
Expected: all green. Confirm executed counts against the per-task expectations above.

- [ ] **Gate-off parity**

With `viewsheet.modernVisualization` off, confirm: new charts straight; area/circular still smooth (ungated hooks); all 14 original transition assertions hold; a chart saved before this phase loads straight.

- [ ] **Gate-on behavior**

New chart → Line: smooth. Saved bar chart switched to Line: smooth. Gate then off: both revert. User unchecks Smooth Lines: stays unchecked in both gate states.

- [ ] **Hand off (do not commit)**

Leave everything in the working tree on `viz-updates`. Report files touched, test counts, and anything outstanding.

## Deferred

- Gating area and circular (spec D5) — deliberately not done; would regress non-modern orgs.
- The remaining untagged test classes repo-wide — separate cleanup.
- Frontend: no Angular change needed. The checkbox binds to `model.smoothLines`, which the server populates from the gate-aware getter.
