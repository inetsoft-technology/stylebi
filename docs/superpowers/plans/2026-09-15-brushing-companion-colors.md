# Brushing Companion Colours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a chart is brushed, unselected marks fade to each series' own companion colour and selected marks render each series' own base colour, instead of every series collapsing to one flat grey and one flat red.

**Architecture:** A decorator `ColorFrame` wraps the chart's real colour frame and returns the companion of whatever that frame would have answered, keyed on **colour** rather than series index. The same frame carries an `isSelected` marker that replaces two identity-by-colour comparisons elsewhere in the codebase. Everything is gated on the chart's own `VizMark` via `VizContext`, and falls back to today's behaviour when a customer has themed the brush colours in CSS.

**Tech Stack:** Java 21, Maven, JUnit 5 (Jupiter), `@Tag("core")`. Modules `core` and `utils/inetsoft-xml-formats`.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-15-brushing-companion-colors-design.md`

## Global Constraints

- **Branch:** `feature-chart-brushing-companion`, stacked on `feature-chart-companion-colors`. Do not rebase onto `epic-74519` until PR #5275 merges.
- **All new tests must carry `@Tag("core")`.** `core/pom.xml:996` sets surefire `<groups>core</groups>`; an untagged test silently does not run.
- **Test classes are package-private** (`class FooTest`, not `public class FooTest`), matching `CategoricalColorFrameCompanionTest`.
- **`VizMark`'s constants are `MODERN_LIGHT` and `MODERN_DARK`.** There is no `VizMark.MODERN`.
- **Every behaviour change is gated.** An unmarked chart, a report chart (`VizContext.LEGACY`), and a deployment that declares `.brush-dim-color` or `.brush-highlight-color` must be pixel-identical to before.
- **`BrushingColor.getHighlightColor()` and `getDimColor()` keep their existing signatures.** They still serve the legacy and CSS-override paths.
- **No comments referencing PR numbers, ticket numbers, or design docs in source files.** Comments explain the code.
- **Maven commands run from `E:\StyleBI\stylebi-enterprise\community`.** Use `./mvnw` in Git Bash or `.\mvnw.cmd` in PowerShell.
- **Do not chain git commands with `&&`.** Run `git add` and `git commit` as separate invocations.
- Commit messages end with: `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`

### Testability constraint, and why the code is shaped around it

`ElementGeometry` is **abstract**, and its constructors take a `GGraph` (`ElementGeometry.java:38,46,59`). Building one in a unit test would mean standing up an entire graph. So every class here splits into a **thin adapter** that pulls three fields off a geometry and a **pure decision function** that takes those three values. Tests target the decision function. The adapters are three field reads each — `getElement()`, `getVisualModel()`, `getTupleIndex()` are all plain field returns — and are covered by compilation plus the manual pass in Task 5.

Do not try to instantiate `ElementGeometry`, `PointGeometry`, `PointVO` or `GGraph` in a test.

---

## File Structure

| File | Responsibility |
|---|---|
| `core/src/main/java/inetsoft/report/composition/graph/BrushedMarks.java` | **new.** Is this mark a brushed one? A pure decision function plus a geometry adapter, so no caller compares colours. |
| `core/src/main/java/inetsoft/report/composition/graph/CompanionBrushColorFrame.java` | **new.** The decorator frame: companion for unselected, base colour for selected, and the public `isSelected` marker. |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | gains `companionOf(Color, VizContext)` — the shared colour-to-companion resolver, lifted from `ChartPropertyService`. |
| `core/src/main/java/inetsoft/web/viewsheet/service/ChartPropertyService.java` | loses its private `resolveCompanion`; becomes a caller. |
| `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` | `applyBrushing` source and target paths, the summary element, and the gate predicate. |
| `core/src/main/java/inetsoft/report/composition/graph/BrushingComparator.java` | colour comparison replaced by `BrushedMarks`. |
| `utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/excel/chart/ExcelChartHelper.java` | same replacement; drops its `brushHLColor` field. |

---

## Task 1: The brush predicate, and `BrushingComparator`

Lands the extracted predicate and converts the one trap whose answer never depends on the new frame. This task touches nothing from PR #5275 and is shippable on its own.

**Files:**
- Create: `core/src/main/java/inetsoft/report/composition/graph/BrushedMarks.java`
- Modify: `core/src/main/java/inetsoft/report/composition/graph/BrushingComparator.java` (whole class)
- Test: `core/src/test/java/inetsoft/report/composition/graph/BrushedMarksTest.java`

**Interfaces:**
- Consumes: `HLColorFrame.getHighlight(DataSet, int)` (public, overridable, returns `Highlight` or null); `CompositeColorFrame.getFrames(Class) -> Stream<VisualFrame>`; `ElementGeometry.getElement()/getVisualModel()/getTupleIndex()`; `VisualModel.getDataSet()`; `GraphElement.getColorFrame()`.
- Produces:
  - `public static boolean BrushedMarks.isBrushed(ElementGeometry gobj)` — the adapter
  - `static boolean BrushedMarks.isBrushed(ColorFrame frame, DataSet data, int tidx)` — the decision
  - `static int BrushedMarks.order(boolean b1, boolean b2)` — the sort ordering

  Task 4 extends the decision function to also read `CompanionBrushColorFrame.isSelected`.

**Background the implementer needs.** `BrushingComparator` sorts brushed marks in front of unbrushed ones so the brushed ones draw on top. It decides which is which by comparing the mark's colour against the one global highlight red. Once colours vary per series that comparison silently matches nothing. The replacement asks the colour frame's brush condition directly. The idiom already exists at `GraphGenerator.java:1630` (the density contour's `PointSelector`) — this task extracts it.

`BrushingComparator` is installed at `GraphGenerator.java:379` and `MapGenerator.java:193`, both inside the brushing-**source** branch only. A source chart always has an `HLColorFrame` in its composite, which is why this conversion needs nothing from later tasks.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/report/composition/graph/BrushedMarksTest.java`:

```java
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CompositeColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.TextHighlight;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class BrushedMarksTest {
   @Test
   void aRowMatchingTheBrushConditionIsBrushed() {
      assertTrue(BrushedMarks.isBrushed(sourceComposite(0), data(), 0));
   }

   @Test
   void aRowNotMatchingTheBrushConditionIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(sourceComposite(0), data(), 1));
   }

   @Test
   void aCompositeWithNoHighlightFrameIsNotBrushed() {
      CompositeColorFrame frame = new CompositeColorFrame();
      frame.addFrame(new StaticColorFrame(Color.BLUE));
      assertFalse(BrushedMarks.isBrushed(frame, data(), 0));
   }

   @Test
   void aNonCompositeFrameIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(new StaticColorFrame(Color.BLUE), data(), 0));
   }

   @Test
   void aNullFrameOrDataIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(null, data(), 0));
      assertFalse(BrushedMarks.isBrushed(sourceComposite(0), null, 0));
   }

   @Test
   void aNullGeometryIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(null));
   }

   @Test
   void brushedMarksSortAfterUnbrushedOnes() {
      assertEquals(1, BrushedMarks.order(true, false));
      assertEquals(-1, BrushedMarks.order(false, true));
      assertEquals(0, BrushedMarks.order(true, true));
      assertEquals(0, BrushedMarks.order(false, false));
   }

   private DataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Region", "Sales" },
         { "East", 100.0 },
         { "West", 5.0 }
      });
   }

   // the composite a brushing-source chart carries: HLColorFrame over the real palette.
   // getHighlight is stubbed so the test does not depend on the condition-building API.
   private CompositeColorFrame sourceComposite(int brushedRow) {
      HLColorFrame hframe = new HLColorFrame(null, null, null) {
         @Override
         public Highlight getHighlight(DataSet data, int row) {
            return row == brushedRow ? new TextHighlight() : null;
         }
      };

      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(hframe);
      composite.addFrame(new StaticColorFrame(Color.BLUE));
      return composite;
   }
}
```

`new HLColorFrame(null, null, null)` is safe: its `init` is skipped when either the group or the data is null, so the frame is left with an empty condition array.

- [ ] **Step 2: Run the test and confirm it fails**

```
./mvnw test -pl core -Dtest=BrushedMarksTest
```

Expected: compilation failure, `cannot find symbol: class BrushedMarks`.

- [ ] **Step 3: Write the implementation**

Create `core/src/main/java/inetsoft/report/composition/graph/BrushedMarks.java`. Copy the AGPL header from `BrushingComparator.java:1-17` verbatim, then:

```java
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.CompositeColorFrame;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.ElementGeometry;

/**
 * Whether a rendered mark is one of the brushed ones. Asks the colour frame's brush condition
 * rather than comparing the mark's colour against a known highlight, which only holds while every
 * brushed mark shares one colour.
 */
public final class BrushedMarks {
   private BrushedMarks() {
   }

   public static boolean isBrushed(ElementGeometry gobj) {
      if(gobj == null || gobj.getElement() == null) {
         return false;
      }

      VisualModel model = gobj.getVisualModel();

      return isBrushed(gobj.getElement().getColorFrame(),
                       model == null ? null : model.getDataSet(),
                       gobj.getTupleIndex());
   }

   static boolean isBrushed(ColorFrame frame, DataSet data, int tidx) {
      if(!(frame instanceof CompositeColorFrame) || data == null) {
         return false;
      }

      return ((CompositeColorFrame) frame).getFrames(HLColorFrame.class)
         .anyMatch(f -> ((HLColorFrame) f).getHighlight(data, tidx) != null);
   }

   /**
    * Ordering that puts a brushed mark after an unbrushed one, so it draws on top.
    */
   static int order(boolean b1, boolean b2) {
      return b1 == b2 ? 0 : (b1 ? 1 : -1);
   }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

```
./mvnw test -pl core -Dtest=BrushedMarksTest
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 5: Convert `BrushingComparator`**

Replace everything after the AGPL header with:

```java
package inetsoft.report.composition.graph;

import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.visual.PointVO;

import java.util.Comparator;

/**
 * A comparator to make sure brushed vo is on top of the base vo.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class BrushingComparator implements Comparator {
   @Override
   public int compare(Object v1, Object v2) {
      if(!(v1 instanceof PointVO) || !(v2 instanceof PointVO)) {
         return 0;
      }

      ElementGeometry gobj1 = (ElementGeometry) ((PointVO) v1).getGeometry();
      ElementGeometry gobj2 = (ElementGeometry) ((PointVO) v2).getGeometry();

      if(gobj1.getElement() != gobj2.getElement()) {
         return 0;
      }

      return BrushedMarks.order(BrushedMarks.isBrushed(gobj1), BrushedMarks.isBrushed(gobj2));
   }
}
```

The old `c1.equals(c2) -> 0` short-circuit is subsumed: two marks in the same brush state now compare equal regardless of colour, which is the intended meaning. The `java.awt.*` and `BrushingColor` imports are no longer needed — remove them.

- [ ] **Step 6: Confirm the module still compiles and the test still passes**

```
./mvnw test -pl core -Dtest=BrushedMarksTest
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`, and no compilation error from `BrushingComparator`.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/BrushedMarks.java
git add core/src/main/java/inetsoft/report/composition/graph/BrushingComparator.java
git add core/src/test/java/inetsoft/report/composition/graph/BrushedMarksTest.java
```

```bash
git commit -m "Ask the brush condition which marks are brushed, not their colour" -m "BrushingComparator decided which marks to sort in front by comparing each mark's colour against the single global highlight red. That holds only while every brushed mark shares one colour, and it fails silently rather than loudly when they do not." -m "BrushedMarks asks the colour frame's brush condition directly, extracting the idiom the density contour's PointSelector already uses. The decision is split from the geometry lookup so it can be tested without standing up a graph. No behaviour change: the comparator is installed only on the brushing-source path, where the highlight frame is always present." -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Lift the companion resolver to a shared home

`ChartPropertyService.resolveCompanion` is private and has one caller. Two more consumers arrive in Tasks 3 and 4, so it moves to the class that already owns companion resolution.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` (add a public static method)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/ChartPropertyService.java:407-421` (delete the private method, call the new one)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsCompanionOfTest.java`

**Interfaces:**
- Consumes: `VSChartPaletteDefaults.activePalette(VizContext)`, `VSChartPaletteDefaults.companionColor(CategoricalColorFrame, String, int, boolean)`, `CategoricalColorFrame.setDefaultColors/setDefaultColor/getDefaultColor/getColorCount`.
- Produces: `public static Color VSChartPaletteDefaults.companionOf(Color base, VizContext ctx)`. Tasks 3 and 4 call this.

**Background.** The private method at `ChartPropertyService.java:407-421` walks the active palette looking for a slot whose colour equals the base, and resolves the authored `-soft` companion by palette name and index when it finds one. When it does not, it wraps the colour in a one-slot frame and derives by rule. Keying on colour is what lets the brushing decorator work without a series index. `MODERN_NAME` (`"Modern"`) and `DARK_NAME` (`"Modern Dark"`) already exist in `VSChartPaletteDefaults` at `:263-264`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsCompanionOfTest.java`:

```java
package inetsoft.uql.viewsheet.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class VSChartPaletteDefaultsCompanionOfTest {
   @Test
   void aPaletteColourTakesItsAuthoredCompanion() {
      // Modern slot 0 is #0490FF; Modern-soft slot 0 is #97BEEB
      assertEquals(new Color(0x97BEEB),
                   VSChartPaletteDefaults.companionOf(new Color(0x0490FF), light()));
   }

   @Test
   void theDarkAnchorTakesItsAuthoredCompanion() {
      // Modern Dark slot 2 is #49447D; Modern Dark-soft slot 2 is #8988AB
      assertEquals(new Color(0x8988AB),
                   VSChartPaletteDefaults.companionOf(new Color(0x49447D), dark()));
   }

   @Test
   void aColourOutsideThePaletteIsDerivedByRule() {
      Color off = new Color(0x3366CC);
      Color companion = VSChartPaletteDefaults.companionOf(off, light());

      assertNotNull(companion);
      assertNotEquals(off, companion);
   }

   @Test
   void aNullBaseHasNoCompanion() {
      assertNull(VSChartPaletteDefaults.companionOf(null, light()));
   }

   @Test
   void everyHeadSlotResolvesInBothModes() {
      Color[] modern = VSChartPaletteDefaults.modernPalette();
      Color[] modernDark = VSChartPaletteDefaults.darkPalette();

      for(int i = 0; i < 8; i++) {
         assertNotNull(VSChartPaletteDefaults.companionOf(modern[i], light()), "light slot " + i);
         assertNotNull(VSChartPaletteDefaults.companionOf(modernDark[i], dark()), "dark slot " + i);
      }
   }

   private VizContext light() {
      return VizContext.of(VizMark.MODERN_LIGHT);
   }

   private VizContext dark() {
      return VizContext.of(VizMark.MODERN_DARK);
   }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```
./mvnw test -pl core -Dtest=VSChartPaletteDefaultsCompanionOfTest
```

Expected: compilation failure, `cannot find symbol: method companionOf`.

- [ ] **Step 3: Add `companionOf` to `VSChartPaletteDefaults`**

Insert immediately after the existing `companionColor` method:

```java
   /**
    * The companion of a colour rather than of a slot: the authored companion when the colour is a
    * member of the active palette, otherwise the rule applied to the colour itself. Keying on
    * colour rather than index lets a caller that holds only a resolved colour - a brushed mark, a
    * target band - reach the same answer a slot lookup would give.
    */
   public static Color companionOf(Color base, VizContext ctx) {
      if(base == null) {
         return null;
      }

      CategoricalColorFrame active = new CategoricalColorFrame();
      active.setDefaultColors(activePalette(ctx));

      for(int i = 0; i < active.getColorCount(); i++) {
         if(base.equals(active.getDefaultColor(i))) {
            return companionColor(active, ctx.dark ? DARK_NAME : MODERN_NAME, i, ctx.dark);
         }
      }

      CategoricalColorFrame holder = new CategoricalColorFrame();
      holder.setDefaultColor(0, base);
      return companionColor(holder, null, 0, ctx.dark);
   }
```

Add `import inetsoft.graph.aesthetic.CategoricalColorFrame;` and `import java.awt.Color;` if not already present.

- [ ] **Step 4: Run the test and confirm it passes**

```
./mvnw test -pl core -Dtest=VSChartPaletteDefaultsCompanionOfTest
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 5: Make `ChartPropertyService` a caller**

Delete the private `resolveCompanion` method at `ChartPropertyService.java:407-421` entirely. At its former call site (`:375`), replace `resolveCompanion(base, ctx)` with `VSChartPaletteDefaults.companionOf(base, ctx)`.

Then check whether the palette-name constants are still used before deleting them:

```
grep -n "MODERN_PALETTE\|DARK_PALETTE" core/src/main/java/inetsoft/web/viewsheet/service/ChartPropertyService.java
```

Delete `MODERN_PALETTE` and `DARK_PALETTE` at `:1106-1107` only if that grep returns nothing else.

- [ ] **Step 6: Run the companion suites and confirm nothing regressed**

```
./mvnw test -pl core -Dtest='*Companion*,VSChartPaletteDefaultsTest'
```

Expected: all pass. The existing drift guard in `VSChartPaletteDefaultsTest` near `:395` must still be green — it is what proves the derivation and the authored palette agree.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java
git add core/src/main/java/inetsoft/web/viewsheet/service/ChartPropertyService.java
git add core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsCompanionOfTest.java
```

```bash
git commit -m "Lift companion-by-colour resolution to VSChartPaletteDefaults" -m "resolveCompanion was private to ChartPropertyService with a single caller. Brushing needs the same answer from two more places, and a colour-keyed lookup belongs beside the slot-keyed one it delegates to." -m "No behaviour change: the method moves verbatim apart from a null guard, and the target band path now calls it instead of owning it." -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: The decorator frame

Builds `CompanionBrushColorFrame` and proves it in isolation. Nothing constructs it yet, so this task changes no rendered output.

**Files:**
- Create: `core/src/main/java/inetsoft/report/composition/graph/CompanionBrushColorFrame.java`
- Test: `core/src/test/java/inetsoft/report/composition/graph/CompanionBrushColorFrameTest.java`

**Interfaces:**
- Consumes: `VSChartPaletteDefaults.companionOf(Color, VizContext)` from Task 2; `HLColorFrame.getHighlight(DataSet, int)`.
- Produces:
  - `public CompanionBrushColorFrame(HLColorFrame predicate, boolean selected, ColorFrame base, boolean dark)`
  - `public boolean isSelected(DataSet data, int row)`
  - `public Color getColor(DataSet data, String col, int row)`
  - `public Color getColor(Object val)`
  - `public ColorFrame getBase()`

  Task 4 constructs this and calls `isSelected`.

**Background.** `ColorFrame` is abstract with exactly two abstract methods, `getColor(DataSet, String, int)` and `getColor(Object)` (`ColorFrame.java:44,49`). `VisualFrame` implements `Serializable`, so **the frame must not hold a lambda or method reference** — hold the `HLColorFrame` itself, which is serializable.

`predicate` is nullable. Null means the layer is uniform and `selected` decides it — the brushing-target case, where a chart is split into an all-data layer and a brushed-data layer, each wholly one state. `getColor(Object val)` has no row to evaluate, so a uniform layer answers from `selected` and a per-row layer answers the base colour unchanged; this path serves legend lookups.

`VisualFrame.getVisualField()` is concrete and returns `getField()` by default (`VisualFrame.java:174`). This frame overrides it to delegate to its base, so the decorator reports the same field it wraps.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/report/composition/graph/CompanionBrushColorFrameTest.java`:

```java
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.TextHighlight;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class CompanionBrushColorFrameTest {
   private static final Color AZURE = new Color(0x0490FF);
   private static final Color AZURE_SOFT = new Color(0x97BEEB);
   private static final Color CORAL = new Color(0xFF5A35);
   private static final Color CORAL_SOFT = new Color(0xF6B2A2);

   @Test
   void aUniformSelectedLayerKeepsTheBaseColour() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, true, new StaticColorFrame(AZURE), false);

      assertEquals(AZURE, frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aUniformUnselectedLayerTakesTheCompanion() {
      CompanionBrushColorFrame frame =
         new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false);

      assertEquals(AZURE_SOFT, frame.getColor(data(), "Sales", 0));
   }

   @Test
   void eachSeriesTakesItsOwnCompanion() {
      assertEquals(AZURE_SOFT,
                   new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false)
                      .getColor(data(), "Sales", 0));
      assertEquals(CORAL_SOFT,
                   new CompanionBrushColorFrame(null, false, new StaticColorFrame(CORAL), false)
                      .getColor(data(), "Sales", 0));
   }

   @Test
   void darkResolvesAgainstTheDarkCompanionPalette() {
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         null, false, new StaticColorFrame(new Color(0x4FA5FF)), true);

      assertEquals(new Color(0x00569C), frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aNullBaseColourFallsThrough() {
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         null, false, new StaticColorFrame((Color) null), false);

      assertNull(frame.getColor(data(), "Sales", 0));
   }

   @Test
   void aUniformLayerReportsItsOwnSelectionState() {
      DataSet data = data();

      assertTrue(new CompanionBrushColorFrame(null, true, new StaticColorFrame(AZURE), false)
                    .isSelected(data, 0));
      assertFalse(new CompanionBrushColorFrame(null, false, new StaticColorFrame(AZURE), false)
                     .isSelected(data, 0));
   }

   @Test
   void aPerRowLayerSplitsSelectedFromUnselected() {
      DataSet data = data();
      CompanionBrushColorFrame frame = new CompanionBrushColorFrame(
         brushedAtRow(0), false, new StaticColorFrame(AZURE), false);

      assertTrue(frame.isSelected(data, 0));
      assertEquals(AZURE, frame.getColor(data, "Sales", 0));

      assertFalse(frame.isSelected(data, 1));
      assertEquals(AZURE_SOFT, frame.getColor(data, "Sales", 1));
   }

   private DataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Region", "Sales" },
         { "East", 100.0 },
         { "West", 5.0 }
      });
   }

   private HLColorFrame brushedAtRow(int brushedRow) {
      return new HLColorFrame(null, null, null) {
         @Override
         public Highlight getHighlight(DataSet data, int row) {
            return row == brushedRow ? new TextHighlight() : null;
         }
      };
   }
}
```

Note the `(Color) null` cast — `StaticColorFrame` has both a `Color` and a `String` constructor, so a bare `null` is ambiguous and will not compile.

`eachSeriesTakesItsOwnCompanion` deliberately uses two `StaticColorFrame` bases and the per-row overload rather than one `CategoricalColorFrame`. Do not "simplify" it back: `CategoricalColorFrame.getColor(Object val)` treats `val` as a **categorical data value** looked up in `cmap` by `GTool.toString(val)` (`CategoricalColorFrame.java:181-201`), not as a colour, so passing a `Color` makes the result depend on map state and the index fallback rather than on the behaviour under test.

- [ ] **Step 2: Run the test and confirm it fails**

```
./mvnw test -pl core -Dtest=CompanionBrushColorFrameTest
```

Expected: compilation failure, `cannot find symbol: class CompanionBrushColorFrame`.

- [ ] **Step 3: Write the implementation**

Create `core/src/main/java/inetsoft/report/composition/graph/CompanionBrushColorFrame.java`. Copy the AGPL header from `BrushingComparator.java:1-17`, then:

```java
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;

import java.awt.Color;

/**
 * Brushing colours resolved per mark. An unselected mark recedes to the companion of the colour it
 * would otherwise have carried, so a brushed chart keeps its series distinguishable; a selected one
 * keeps that colour unchanged.
 *
 * Also the marker for which marks are brushed. A predicate answers per row, which is the case where
 * one layer holds both states. A null predicate means the layer is uniform and selected decides it,
 * which is the case where the chart is split into an all-data layer and a brushed-data layer.
 */
public class CompanionBrushColorFrame extends ColorFrame {
   public CompanionBrushColorFrame(HLColorFrame predicate, boolean selected, ColorFrame base,
                                   boolean dark)
   {
      this.predicate = predicate;
      this.selected = selected;
      this.base = base;
      this.dark = dark;
   }

   /**
    * Whether the mark at this row is one of the brushed ones.
    */
   public boolean isSelected(DataSet data, int row) {
      return predicate == null ? selected : predicate.getHighlight(data, row) != null;
   }

   public ColorFrame getBase() {
      return base;
   }

   @Override
   public Color getColor(DataSet data, String col, int row) {
      Color c = base == null ? null : base.getColor(data, col, row);

      if(c == null) {
         return null;
      }

      return isSelected(data, row) ? c : companion(c);
   }

   @Override
   public Color getColor(Object val) {
      Color c = base == null ? null : base.getColor(val);

      if(c == null) {
         return null;
      }

      // no row to evaluate, so only a uniform unselected layer recedes
      return predicate != null || selected ? c : companion(c);
   }

   @Override
   public String getVisualField() {
      return base == null ? super.getVisualField() : base.getVisualField();
   }

   private Color companion(Color c) {
      VizContext ctx = VizContext.of(dark ? VizMark.MODERN_DARK : VizMark.MODERN_LIGHT);
      Color companion = VSChartPaletteDefaults.companionOf(c, ctx);

      return companion == null ? c : companion;
   }

   private final HLColorFrame predicate;
   private final boolean selected;
   private final ColorFrame base;
   private final boolean dark;
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

```
./mvnw test -pl core -Dtest=CompanionBrushColorFrameTest
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`.

### Corrections found while executing Task 3

Three defects in the test code above, all found during execution and fixed in the commits. Recorded here so this plan is not reused as written.

1. **The class needs Spring scaffolding, not just `@Tag("core")`.** `companion()` calls `VizContext.of(...)`, which reaches `SreeEnv`/`ConfigurationContext` — shared static state that must be initialised and that corrupts sibling tests in the same fork otherwise. Add the annotation block used by every other test that calls `VizContext.of(VizMark…)`, copied from `VSChartPaletteDefaultsTest`: `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(...)`, `@DirtiesContext(classMode = AFTER_CLASS)`, `@SreeHome`. The same correction applies to Task 2's test.

2. **`new StaticColorFrame((Color) null)` does not yield a null colour.** That constructor sets only `userColor`; `getColor()` falls back through `cssColor` to `defaultColor`, which is initialised to the non-null `DEFAULT_COLOR` (`StaticColorFrame.java:70,287`). `aNullBaseColourFallsThrough` must also call `setDefaultColor(null)` on the base, or it asserts the opposite of its name.

3. **`getColor(Object val)` had no coverage.** It is one of `ColorFrame`'s two abstract methods and its rule (`predicate != null || selected`) differs from the per-row overload, so it needs its own three tests: uniform+selected → base unchanged; uniform+unselected → companion; `predicate != null` → base unchanged **asserted with `selected=false`**, which is the case that proves the `||` is an OR rather than an AND. Use a `StaticColorFrame` base, never a `CategoricalColorFrame`, for the reason given above.

Final count for this test class is 10, not 7.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/CompanionBrushColorFrame.java
git add core/src/test/java/inetsoft/report/composition/graph/CompanionBrushColorFrameTest.java
```

```bash
git commit -m "Add the companion brushing colour frame" -m "Wraps a chart's real colour frame and returns the companion of whatever that frame would have answered for an unselected mark, leaving a selected one unchanged. Keying on colour rather than series index means a colour-unbound or measure-coloured chart resolves an answer too, and no call site needs an index threaded through it." -m "Also carries isSelected, which later replaces the identity-by-colour comparisons that ask whether a rendered mark is a brushed one. Nothing constructs the frame yet, so no chart renders differently." -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Wire it in, convert the Excel guard, correct the companion design

The behaviour change. Everything before this was inert.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java` — constructors near `:223` and `:439`, `applyBrushing()` at `:373`, `createBrushingTargetColorFrame` at `:408`, `applyBrushing(ColorFrame, DataSet)` at `:656`, summary element at `:4451`, new field near `:7601`
- Modify: `core/src/main/java/inetsoft/report/composition/graph/BrushedMarks.java`
- Modify: `utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/excel/chart/ExcelChartHelper.java:694` and `:3103`
- Modify: `docs/superpowers/specs/lookfeel/2026-09-14-chart-companion-colors-design.md` — §8
- Test: `core/src/test/java/inetsoft/report/composition/graph/BrushedMarksTest.java` (extend)

**Interfaces:** consumes everything from Tasks 1-3; produces no new public API.

**Background.** Two brushing paths, and they differ:

- **Source** (`adata == null && isBrushingSource()`): one dataset, one layer, `HLColorFrame` deciding per row. `applyBrushing(ColorFrame, DataSet)` at `:656` builds `[hframe, color]` where `hframe` answers red or grey and never falls through.
- **Target** (`adata != null`): two layers, each wholly one state. `acolor` is the all-data layer; `createBrushingTargetColorFrame` builds the brushed layer.

`getHLColorFrame(brushHLColor)` is still called to build the predicate. Its `highlight.setForeground(color)` simply goes unused when the decorator wraps it, which is why no signature changes.

- [ ] **Step 1: Add the gate field**

`vizContext` is assigned in the constructor, so this **must not** be a field initialiser — those run first and would read null. Declare the field beside `brushHLColor`/`brushDimColor` near `:7601`:

```java
   protected boolean companionBrushing;
```

Then in **both** constructors, immediately after the line assigning `this.vizContext`:

```java
      this.companionBrushing = vizContext.modern
         && !CSSDictionary.getDictionary().checkPresent(".brush-dim-color")
         && !CSSDictionary.getDictionary().checkPresent(".brush-highlight-color");
```

Add `import inetsoft.util.css.CSSDictionary;` if absent. Verify both sites:

```
grep -n "this.vizContext = " core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java
```

- [ ] **Step 2: Wire the source path**

Replace `applyBrushing(ColorFrame color, DataSet data)` at `:656`:

```java
   protected ColorFrame applyBrushing(ColorFrame color, DataSet data) {
      HLColorFrame hframe = getHLColorFrame(brushHLColor);
      hframe.setDefaultColor(brushDimColor);

      if(color == null) {
         return hframe;
      }

      CompositeColorFrame cframe = new CompositeColorFrame();

      if(companionBrushing) {
         cframe.addFrame(new CompanionBrushColorFrame(hframe, false, color, vizContext.dark));
      }
      else {
         cframe.addFrame(hframe);
      }

      cframe.addFrame(color);
      return cframe;
   }
```

- [ ] **Step 3: Wire the brushed target layer**

Replace `createBrushingTargetColorFrame` at `:408`. Its return type widens from `StaticColorFrame` to `ColorFrame`:

```java
   private ColorFrame createBrushingTargetColorFrame(boolean isAll) {
      // if showing all data, dim all. (53441)
      boolean dimAll = data instanceof BrushDataSet &&
         ((BrushDataSet) data).isBrushedDataEmpty() &&
         ((BrushDataSet) data).isBrushedDataOnly() || isAll;

      ColorFrame base = getColorFrame(null);

      if(companionBrushing && base != null) {
         return new CompanionBrushColorFrame(null, !dimAll, base, vizContext.dark);
      }

      StaticColorFrame frame2 = new StaticColorFrame();
      frame2.setUserColor(dimAll ? brushDimColor : brushHLColor);
      return frame2;
   }
```

Confirm every caller still compiles:

```
grep -n "createBrushingTargetColorFrame" core/src/main/java/inetsoft/report/composition/graph/*.java
```

- [ ] **Step 4: Wire the all-data target layer**

In `applyBrushing()`, the `else` branch at `:391-405`, replace the `acolor` construction:

```java
      else {
         ColorFrame base = getColorFrame(null);
         ColorFrame dimFrame;

         if(companionBrushing && base != null) {
            dimFrame = new CompanionBrushColorFrame(null, false, base, vizContext.dark);
         }
         else {
            StaticColorFrame flat = new StaticColorFrame();
            flat.setUserColor(brushDimColor);
            dimFrame = flat;
         }

         acolor = applyBrushing(getColorFrame(null), dimFrame);

         ColorFrame brushed = createBrushingTargetColorFrame(false);
         setColorFrame(applyBrushing(getColorFrame(null), brushed));

         for(String measure : cvisitor.getMeasures()) {
            VisualFrame frame = cvisitor.getMeasureFrame(measure);
            frame = applyBrushing((ColorFrame) frame, brushed);
            cvisitor.setMeasureFrame(measure, frame);
         }
      }
```

- [ ] **Step 5: Wire the summary element**

At `:4451`, the `else if(isBrushingSource())` branch:

```java
            else if(isBrushingSource()) {
               if(companionBrushing && color != null) {
                  elem.setColorFrame(
                     new CompanionBrushColorFrame(null, false, color, vizContext.dark));
               }
               else {
                  StaticColorFrame frame2 = new StaticColorFrame();
                  frame2.setUserColor(brushDimColor);
                  elem.setColorFrame(frame2);
               }
            }
```

- [ ] **Step 6: Teach `BrushedMarks` about the marker frame**

A target chart has no `HLColorFrame`, so the marker must be asked first. Replace the decision function:

```java
   static boolean isBrushed(ColorFrame frame, DataSet data, int tidx) {
      if(!(frame instanceof CompositeColorFrame) || data == null) {
         return false;
      }

      CompositeColorFrame composite = (CompositeColorFrame) frame;

      if(composite.getFrames(CompanionBrushColorFrame.class).findAny().isPresent()) {
         return composite.getFrames(CompanionBrushColorFrame.class)
            .anyMatch(f -> ((CompanionBrushColorFrame) f).isSelected(data, tidx));
      }

      return composite.getFrames(HLColorFrame.class)
         .anyMatch(f -> ((HLColorFrame) f).getHighlight(data, tidx) != null);
   }
```

A marked chart's answer comes from the marker alone; the legacy path is reached only when no marker frame is present, so its behaviour is unchanged.

- [ ] **Step 7: Add the marker tests**

Append to `BrushedMarksTest.java`:

```java
   @Test
   void aTargetChartLayerAnswersFromItsMarker() {
      CompositeColorFrame brushedLayer = new CompositeColorFrame();
      brushedLayer.addFrame(new CompanionBrushColorFrame(
         null, true, new StaticColorFrame(Color.BLUE), false));
      assertTrue(BrushedMarks.isBrushed(brushedLayer, data(), 0));

      CompositeColorFrame allDataLayer = new CompositeColorFrame();
      allDataLayer.addFrame(new CompanionBrushColorFrame(
         null, false, new StaticColorFrame(Color.BLUE), false));
      assertFalse(BrushedMarks.isBrushed(allDataLayer, data(), 0));
   }

   @Test
   void aMarkedSourceChartAnswersPerRow() {
      HLColorFrame predicate = new HLColorFrame(null, null, null) {
         @Override
         public Highlight getHighlight(DataSet data, int row) {
            return row == 0 ? new TextHighlight() : null;
         }
      };

      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(new CompanionBrushColorFrame(
         predicate, false, new StaticColorFrame(Color.BLUE), false));
      composite.addFrame(new StaticColorFrame(Color.BLUE));

      assertTrue(BrushedMarks.isBrushed(composite, data(), 0));
      assertFalse(BrushedMarks.isBrushed(composite, data(), 1));
   }
```

- [ ] **Step 8: Run the graph tests and confirm they pass**

```
./mvnw test -pl core -Dtest='BrushedMarksTest,CompanionBrushColorFrameTest'
```

Expected: `Tests run: 9` for `BrushedMarksTest` and `Tests run: 7` for `CompanionBrushColorFrameTest`, no failures.

- [ ] **Step 9: Convert the Excel guard**

In `ExcelChartHelper.java`, replace the condition at `:693-696`:

```java
         // set default datapointinfo, to avoid bugs when export stack chart.
         if(sinfo.getDefaultDataPointInfo() == null && (!hasBrush || !isBrushedVO(vo))) {
            sinfo.setDefaultDataPointInfo(dinfo);
         }
```

Add a private helper beside the other private methods:

```java
   private boolean isBrushedVO(ElementVO vo) {
      return vo != null && vo.getGeometry() instanceof ElementGeometry &&
         BrushedMarks.isBrushed((ElementGeometry) vo.getGeometry());
   }
```

`ElementVO.getGeometry()` returns `Geometry`, hence the instanceof. Delete the `brushHLColor` field at `:3103` and add `import inetsoft.report.composition.graph.BrushedMarks;`. Then confirm the old import is dead:

```
grep -n "brushHLColor\|BrushingColor" utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/excel/chart/ExcelChartHelper.java
```

Remove the `BrushingColor` import if that grep returns nothing.

- [ ] **Step 10: Correct the companion design's §8**

In `docs/superpowers/specs/lookfeel/2026-09-14-chart-companion-colors-design.md`:

In the **ENGINE §2 brushing** row of the §8 table, change the call-site sentence so it names five files — adding `ExcelChartHelper` to `GraphGenerator`, `MapGenerator`, `VGraphPair` and `BrushingComparator` — and record that the item has shipped for cartesian charts with maps deferred, linking `./2026-09-15-brushing-companion-colors-design.md`.

In the paragraph beginning "**The mechanism-with-one-consumer risk is real on this branch.**", record that the mechanism now has three consumers — the target band, the brushing dim colour, and the brushing marker — rather than one.

- [ ] **Step 11: Build both modules and run the full core suite**

```
./mvnw clean install -DskipTests
```

Expected: BUILD SUCCESS across all 46 modules. This is what compiles the `inetsoft-xml-formats` change.

```
./mvnw test -pl core
```

Expected: the full `core` suite green. A full run is required here, not a scoped one — a scoped sweep missed a regression on a sibling branch that only the full run caught.

- [ ] **Step 12: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java
git add core/src/main/java/inetsoft/report/composition/graph/BrushedMarks.java
git add core/src/test/java/inetsoft/report/composition/graph/BrushedMarksTest.java
git add utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/excel/chart/ExcelChartHelper.java
git add docs/superpowers/specs/lookfeel/2026-09-14-chart-companion-colors-design.md
```

```bash
git commit -m "Resolve brushing colours per mark on a modern chart" -m "An unselected mark now recedes to the companion of its own series colour and a selected one keeps that colour, so a brushed chart stays readable as a set of series instead of collapsing to one grey and one red." -m "Gated on the chart's own VizMark, so an unmarked chart and a report chart are unchanged. A deployment that declares .brush-dim-color or .brush-highlight-color keeps today's flat behaviour entirely: the two are a tuned pair, and honouring one alone produces a combination nobody designed." -m "The Excel export's default-data-point guard asked whether a mark was brushed by comparing its fill against the global highlight, which stops matching once colours vary. It now asks the marker frame. Maps are unchanged and keep flat brushing, which is also why VGraphPair's polygon z-order comparison is still correct and is left alone." -m "Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Manual verification

No code. This is the gate before the PR; the design's §6 list is the checklist.

- [ ] **Step 1: Build and start**

```
./mvnw clean install -DskipTests -PdockerImage
```

Then from `docker/target/docker-test`: `docker compose up -d`, and open `http://localhost:8080`.

- [ ] **Step 2: Walk the list, light mode then dark**

- [ ] Bar chart, several series, brushed — every series stays distinguishable in both states, no red anywhere
- [ ] Line chart, several series, brushed — same
- [ ] Colour-unbound chart, brushed — dims to a soft version of its own colour
- [ ] Map, brushed — still flat grey and red, highlighted regions still draw on top of unhighlighted ones
- [ ] Brushed chart on an **unmarked** dashboard — flat grey and red, unchanged
- [ ] A second chart on the same dashboard as a brushed one (the target path) — all-data layer companioned, brushed layer at full colour
- [ ] Export a brushed chart to Excel — a column with no data takes an unselected series colour, not a brushed one

- [ ] **Step 3: Confirm the CSS fallback**

Add `.brush-dim-color { color: #808080; }` to the org's `format.css` via Enterprise Manager, reload, and re-brush a modern chart. Expected: flat grey and red, exactly as before this branch. Remove it afterwards.

- [ ] **Step 4: Record the results**

Append a short "Verification" section to `2026-09-15-brushing-companion-colors-design.md` listing what was checked and anything found, then commit that edit alone.

---

## Self-Review

**Spec coverage.** §1 mechanism → Task 3. §2 attachment points → Task 4 steps 2-5. §3 gate and CSS fallback → Task 4 step 1. §4 traps → Task 1 (trap 2), Task 4 steps 6 and 9 (trap 3); trap 1 explicitly out of scope. §5 unchanged surfaces → no task by design, confirmed in Task 5. §6 testing → the unit steps plus Task 5. §7 deferred → no task. §8 files touched → the File Structure table. §9 commit ordering → the task boundaries.

**Naming consistency.** `BrushedMarks.isBrushed`, `BrushedMarks.order`, `CompanionBrushColorFrame.isSelected`, `VSChartPaletteDefaults.companionOf` and the `companionBrushing` field are used identically wherever they appear.

**Signatures verified against source**, not assumed: `ColorFrame`'s two abstract methods (`:44,49`); `VisualFrame.getVisualField` concrete, returning `getField()` (`:174`); `CompositeColorFrame.getColor` first-non-null (`:173-184`) and `getFrames(Class)` (`:348`); `HLColorFrame.getColor`/`getHighlight` (`:259,270`) and its three-arg constructor skipping `init` on null input; `StaticColorFrame(Color)` versus `StaticColorFrame(String)` — hence the `(Color) null` cast; `VizMark.MODERN_LIGHT`/`MODERN_DARK`; `VSChartPaletteDefaults.MODERN_NAME`/`DARK_NAME` (`:263-264`); `ElementVO.getGeometry()` returning `Geometry` (`:64`); `ElementGeometry` abstract with `GGraph`-taking constructors (`:38,46,59`), which is why no test builds one; `core/pom.xml:996` surefire `<groups>core</groups>`.
