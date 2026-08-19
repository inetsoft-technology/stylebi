# Seed Mark P4 — Server Reads Follow the Mark — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every server-side render-time read resolve modern chrome from the assembly's own `VizMark` instead of the org gate, so an unmarked dashboard renders legacy while a marked one renders modern — the behaviour reversal the whole seed-mark track exists to deliver.

**Architecture:** `VizContext.of(VSAssemblyInfo)` and `of(VizMark)` already exist and already carry the `gate &&` term, so closing the org gate still reverts read-time chrome on marked assemblies without any extra work. This phase changes *who supplies the context*: 43 call sites move from `VizContext.ofGate()` to a context derived from the assembly in scope. They divide into groups that resolve differently — most have the info directly in hand and are a one-word change, six chart-pipeline sites need a context field set at construction, six dialog-model sites need two services to forward an info they already hold, and two sites stay org-scoped by decision. Creation flips too, so the values an assembly persists agree with the values it will read.

**Tech Stack:** Java 21, JUnit 5 (`@Tag("core")`, Spring `@ContextConfiguration` + `@SreeHome`, Mockito), Maven via `./mvnw` from `community/`.

**Spec:** [`docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md`](../specs/2026-08-14-seed-mark-forward-half-design.md) — §5's P4, the three trip-ups recorded above it, and **"The shape P4 takes — decided 2026-08-18"**, which is the binding text for every choice below.

## Global Constraints

- **`VizContext.of(...)` must never return the `LEGACY` instance for a viewsheet chart.** Seven chart descriptors' font lines compare identity against `VizContext.LEGACY` to mean "is this a viewsheet chart at all", and two tests hold that line. Pass `LEGACY` only on genuine report paths.
- **Gate-off behaviour is already correct and must not be re-implemented.** `VizContext.of(VizMark)` computes `modern = VSDensityDefaults.isModern() && mark != null`. Do not add gate checks at call sites.
- **`resolveSeededCorner` and the `VSCompositeFormat` tab carve-out stay on the org gate**, per the spec's decision. Do not flip them, and do not thread a context into `VSFormat` getters.
- **`ChartColorPaletteController.getChartColorPalette()` stays on `ofGate()`**, per the spec's decision, with a comment saying why. It is a parameterless bootstrap GET with no assembly in scope.
- **No browser change.** `VSObjectModel`'s mark field, the per-assembly CSS scope and the `GuiTool` sites are P5.
- **No revert sweep, no dark re-stamp.** Both are later phases.
- Java style in this tree: three-space indent, `if(cond) {` with no space before the paren.
- Copy the AGPL header from a sibling file into any new file.

---

## File Structure

**Modified — production**, grouped as the tasks are:

| Task | Files | Sites |
|---|---|---|
| 1 | `VSAssemblyInfo.java:1234` | 1 (creation) |
| 2 | `VSChartModel`, `BaseTableModel`, `VSCalendarModel`, `VSTextModel`, `VSSliderModel`, `VSRangeSliderModel`, `VSRadioButtonModel`, `VSCompositeModel`, `VSCheckBoxModel` | 9 |
| 3 | `AbstractVSExporter` (4), `VSTableDataHelper`, `VSSelectionListHelper`, `VSSelectionTreeHelper`, `ExportUtil` (+2 callers), `VSCalendar` (2), `VSSlider` | 11 |
| 4 | `GraphGenerator` (2), `RadarGraphGenerator`, `VGraphPair`, `CSSChartStyles` (+3 callers), `ChangeChartProcessor` | 6 |
| 5 | `ChartLinePaneModel` (2), `AxisPropertyDialogModel` (2), `LegendFormatDialogModel` (2), `ChartPropertyDialogService`, `RegionPropertyDialogService` | 6 |
| 6 | `BaseTableService` (2), `FormatPainterService`, `ChangeChartTypeService`, `DataVSAQuery`, `VSTableLens`, `CSSProcessor`, `VsToReportConverter` (2), `SelectionBaseVSAssemblyInfo`, `ChartColorPaletteController` | 10 |
| 7 | the guard test, then the docs | — |

**Created:**

| File | Responsibility |
|---|---|
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextReadFlipTest.java` | The behavioural assertion for the flip — a marked info reads modern, an unmarked one reads legacy — plus the structural guard that no new `ofGate()` appears outside the allow-list. |

---

## Task 1: Creation seeds what it can mark

The spec's first trip-up. `AbstractVSAssembly:135-136` gives a new assembly the host's **stored** mark, absence included, while `setDefaultFormat` seeds from the org gate — so an assembly added to a legacy dashboard under an open gate persists the full modern set with no mark, and after this phase would read legacy over those values.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java:1234`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java` (append; 23 tests today, all must still pass)

**Interfaces:**
- Consumes: `VizContext.of(VSAssemblyInfo)`, existing.
- Produces: nothing new. Later tasks rely on the fact that creation and reads now derive from the same source.

- [ ] **Step 1: Write the failing test**

Append to `SeedChromeDefaultsTest`:

```java
   @Test
   void creationOnAnUnmarkedHostSeedsNothingModern() {
      // the host is what decides: AbstractVSAssembly hands a new assembly the host's stored mark,
      // so an assembly added to a legacy dashboard must seed the values it will actually read
      gateOff();
      Viewsheet legacy = new Viewsheet();
      assertNull(legacy.getVSAssemblyInfo().getVizMark(), "gate off at construction: unmarked sheet");

      gateOn();
      TableVSAssembly table = new TableVSAssembly(legacy, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      VSFormat fmt = objectDefault(table.getVSAssemblyInfo());

      assertNull(table.getVSAssemblyInfo().getVizMark(), "it inherits the host's absent mark");
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor,
                   "so it seeds the legacy border, matching what it will read");
      assertEquals(0, fmt.getRoundCornerValue(), "and no card radius");
   }

   @Test
   void creationOnAMarkedHostStillSeedsModern() {
      gateOn();
      Viewsheet modern = new Viewsheet();
      assertEquals(VizMark.MODERN_LIGHT, modern.getVSAssemblyInfo().getVizMark());

      TableVSAssembly table = new TableVSAssembly(modern, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      VSFormat fmt = objectDefault(table.getVSAssemblyInfo());
      VizContext ctx = VizContext.ofGate();

      assertEquals(VizMark.MODERN_LIGHT, table.getVSAssemblyInfo().getVizMark());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx), fmt.getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
   }
```

- [ ] **Step 2: Run to verify the first one fails**

```bash
cd E:/StyleBI/stylebi-enterprise/community
./mvnw test -pl core -Dtest=SeedChromeDefaultsTest
```

Expected: `creationOnAnUnmarkedHostSeedsNothingModern` FAILS — the border comes back as the modern neutral, because creation still reads the org gate. `creationOnAMarkedHostStillSeedsModern` passes already.

- [ ] **Step 3: Flip the creation site**

In `VSAssemblyInfo.setDefaultFormat(boolean,boolean,boolean)`, the last statement:

```java
      setCSSDefaults();
      // the assembly's own provenance, not the org's: an assembly on an unmarked host seeds the
      // values it will read. The stamp precedes this call (AbstractVSAssembly's two-arg constructor
      // and ViewsheetVSAssemblyInfo's), so `this` already carries whatever mark it will keep.
      seedChromeDefaults(VizContext.of(this));
```

- [ ] **Step 4: Run to verify both pass, and that the net survives**

```bash
./mvnw test -pl core -Dtest=SeedChromeDefaultsTest
```

Expected: all 25 pass. The existing 23 build their assemblies on a `new Viewsheet()`, which stamps itself from the gate, so a gate-on fixture is marked and still seeds modern — that is why they survive. If one fails, read whether its fixture host is marked before changing anything.

- [ ] **Step 5: Run the neighbours and compile**

```bash
./mvnw test -pl core -Dtest='SeedChromeDefaultsTest,VizModernizeUtilTest,ModernizeViewsheetServiceTest,VizContextTest'
./mvnw clean install -pl core -DskipTests -o
```

Expected: pass, then BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(viewsheet): seed an assembly from its own mark, not the org gate"
```

---

## Task 2: The model layer

Nine sites, all in constructors that already hold the assembly. This is the group the browser renders, so it is the most visible half of the flip.

**Files:**
- Modify, one site each unless noted: `core/src/main/java/inetsoft/web/viewsheet/model/chart/VSChartModel.java:60`, `table/BaseTableModel.java:41`, `calendar/VSCalendarModel.java:45`, `VSTextModel.java:211`, `VSSliderModel.java:46`, `VSRangeSliderModel.java:109`, `VSRadioButtonModel.java:38`, `VSCompositeModel.java:39`, `VSCheckBoxModel.java:38`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextReadFlipTest.java` (create)

**Interfaces:**
- Consumes: `VizContext.of(VSAssemblyInfo)`.
- Produces: the test file later tasks append to, with helpers `markedTable()`, `unmarkedTable()` and `gateOn()`/`gateOff()`.

**P5 note:** these same nine models gain a `vizMark` field in P5 so the browser can scope CSS per assembly. Do not add it now.

- [ ] **Step 1: Write the failing test**

Create `VizContextReadFlipTest.java`, AGPL header copied from `VizContextTest.java`:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The assertion this phase exists to make true: a marked assembly resolves modern, an unmarked one
 * resolves legacy, under the same open org gate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizContextReadFlipTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   /** A table on a sheet stamped under an open gate. */
   private TableVSAssemblyInfo markedTable() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Marked");
      table.getVSAssemblyInfo().initDefaultFormat();
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   /** A table on a legacy sheet: the gate is open, but the assembly carries no mark. */
   private TableVSAssemblyInfo unmarkedTable() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      gateOn();
      TableVSAssembly table = new TableVSAssembly(vs, "Unmarked");
      table.getVSAssemblyInfo().initDefaultFormat();
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   @Test
   void aMarkedInfoResolvesModern() {
      VizContext ctx = VizContext.of(markedTable());
      assertTrue(ctx.modern);
   }

   @Test
   void anUnmarkedInfoResolvesLegacyUnderTheSameOpenGate() {
      TableVSAssemblyInfo info = unmarkedTable();
      assertNull(info.getVizMark());
      assertFalse(VizContext.of(info).modern,
                  "the gate is open, but this assembly is not modern");
   }

   @Test
   void closingTheGateStillRevertsAMarkedAssembly() {
      TableVSAssemblyInfo marked = markedTable();
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VizContext.of(marked).modern,
                  "the gate && term in of(mark) is what makes gate-off revert read-time chrome");
   }
}
```

- [ ] **Step 2: Run it**

```bash
./mvnw test -pl core -Dtest=VizContextReadFlipTest
```

Expected: PASS, all three — they assert the factory's contract, which already holds. This file is the foundation the later tasks extend; it is not red-green.

- [ ] **Step 3: Flip the nine model sites**

Each is the same shape. The info is in scope at every one — verified: `VSObjectModel`'s constructor takes `VSAssemblyInfo assemblyInfo` (`:90`) and passes it to `createFormatModel(compositeFormat, assemblyInfo)` (`:99`), which is the parameter `VSTextModel:211` overrides. In each file replace `VizContext.ofGate()` with the assembly's context:

- `VSChartModel:60`, `BaseTableModel:41`, `VSCalendarModel:45`, `VSCheckBoxModel:38`, `VSCompositeModel:39`, `VSRadioButtonModel:38`, `VSSliderModel:46`, `VSRangeSliderModel:109` — these are constructors holding `assembly`; use `VizContext.of(assembly.getVSAssemblyInfo())`. Where a local `info`/`assemblyInfo` already exists in the constructor, use it rather than re-fetching.
- `VSTextModel:211` — `createFormatModel(VSCompositeFormat compositeFormat, VSAssemblyInfo assemblyInfo)` already receives the info; use `VizContext.of(assemblyInfo)`.

- [ ] **Step 4: Add the model-layer assertion**

Append to `VizContextReadFlipTest`:

```java
   @Test
   void theModelLayerNoLongerReadsTheOrgGate() {
      // structural: the nine model classes must derive their context from the assembly they are
      // built for, not from the gate. Cheaper and more durable than constructing nine web models.
      assertEquals(0, countOfGateIn("web/viewsheet/model"),
                   "no model-layer class may call VizContext.ofGate()");
   }

   private static int countOfGateIn(String relativePackagePath) throws Exception {
      java.nio.file.Path root = java.nio.file.Paths.get("src/main/java/inetsoft", relativePackagePath);

      if(!java.nio.file.Files.isDirectory(root)) {
         root = java.nio.file.Paths.get("core/src/main/java/inetsoft", relativePackagePath);
      }

      try(java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
         return (int) files.filter(p -> p.toString().endsWith(".java"))
            .mapToLong(p -> {
               try {
                  return java.nio.file.Files.readAllLines(p).stream()
                     .filter(l -> l.contains("VizContext.ofGate()")).count();
               }
               catch(Exception ex) {
                  throw new RuntimeException(ex);
               }
            })
            .sum();
      }
   }
```

Declare the test `throws Exception`. If the working directory at test time makes both candidate paths wrong, print `java.nio.file.Paths.get("").toAbsolutePath()` once, fix the relative path to match, and say so in your report — do not delete the test.

- [ ] **Step 5: Run, then compile**

```bash
./mvnw test -pl core -Dtest=VizContextReadFlipTest
./mvnw clean install -pl core -DskipTests -o
```

Expected: PASS, then BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/model core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextReadFlipTest.java
git commit -m "feat(viewsheet): resolve model-layer chrome from the assembly's mark"
```

---

## Task 3: Export and the painters

Eleven sites. **This is the export-affecting group** — the one the manual pass in Task 7 exists for, because no test in the suite can compare a rendered PDF against a rendered canvas.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java:1408,1804,1891,2694`, `VSTableDataHelper.java:402`, `VSSelectionListHelper.java:77`, `VSSelectionTreeHelper.java:268`, `ExportUtil.java:110`, `core/src/main/java/inetsoft/report/gui/viewsheet/VSCalendar.java:735,957`, `VSSlider.java:67`
- Modify (callers of the changed signature): `core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFVSExporter.java:653`, `svg/SVGVSExporter.java:374`

**Interfaces:**
- Consumes: `VizContext.of(VSAssemblyInfo)`.
- Produces: `ExportUtil.getBackGroundColor(VSCompositeFormat titleFormat, VSCompositeFormat objectFormat, VizContext ctx)` — one added trailing parameter. Both callers pass `VizContext.of(info)`.

- [ ] **Step 1: Flip the sites that already hold an info**

Each of these has the info in scope; replace `VizContext.ofGate()` with the context of that info:

| Site | What is in scope |
|---|---|
| `AbstractVSExporter:1408` | inside `export(...)`, a local `info` guarded by `if(info.isTitleVisible())` |
| `AbstractVSExporter:1804` | `prepareAssembly(VSAssembly assembly)` — use `assembly.getVSAssemblyInfo()`; a local `vinfo` is already in scope at the site |
| `AbstractVSExporter:1891` | `getTextFormat(VSAssemblyInfo info)` |
| `AbstractVSExporter:2694` | `getCalendarTitleFormat(CalendarVSAssemblyInfo info)` |
| `VSTableDataHelper:402` | `writeTitle(TableDataVSAssemblyInfo info, int totalColumnWidth)` |
| `VSSelectionListHelper:77` | `write(SelectionListVSAssembly assembly)` — `assembly.getVSAssemblyInfo()` |
| `VSSelectionTreeHelper:268` | `writeTitle(SelectionTreeVSAssemblyInfo info)` |
| `VSCalendar:735,957` | painter methods; the class reads its own info as `(CalendarVSAssemblyInfo) getAssemblyInfo()` at `:58` — follow that idiom |
| `VSSlider:67` | `paintComponent`; same idiom at `:53` — `(SliderVSAssemblyInfo) getAssemblyInfo()` |

- [ ] **Step 2: Widen `ExportUtil.getBackGroundColor` and its two callers**

`ExportUtil:110` is a static helper with no info. It has exactly two callers, both holding one:

```java
   public static Color getBackGroundColor(VSCompositeFormat titleFormat,
                                          VSCompositeFormat objectFormat, VizContext ctx)
```

At `PDFVSExporter:653` and `SVGVSExporter:374`, both currently read `ExportUtil.getBackGroundColor(format, info.getFormat())`; pass `VizContext.of(info)` as the third argument.

- [ ] **Step 3: Compile**

```bash
./mvnw clean install -pl core -DskipTests -o
```

Expected: BUILD SUCCESS. A compile error here means a caller of `getBackGroundColor` was missed — find it and pass the calling site's own info, never `ofGate()`.

- [ ] **Step 4: Add the group's structural assertion**

Append to `VizContextReadFlipTest`:

```java
   @Test
   void theExportAndPainterLayersNoLongerReadTheOrgGate() throws Exception {
      assertEquals(0, countOfGateIn("report/io/viewsheet"),
                   "export must resolve chrome per assembly, or export disagrees with view");
      assertEquals(0, countOfGateIn("report/gui/viewsheet"),
                   "the painters likewise");
   }
```

- [ ] **Step 5: Run the suite for the export area**

```bash
./mvnw test -pl core -Dtest='VizContextReadFlipTest,VSExportServiceTest'
./mvnw test -pl core
```

Expected: `VizContextReadFlipTest` passes; the full run is green. The full run is worth it here because the export path is widely covered by existing tests and this group changes eleven of its sites.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/report/io core/src/main/java/inetsoft/report/gui \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextReadFlipTest.java
git commit -m "feat(viewsheet): resolve export and painter chrome from the assembly's mark"
```

---

## Task 4: The chart pipeline

Six sites across five files, and the only group that cannot read an info at the call site. The pattern is a context field set at construction, which also replaces a convention with a fact: `VizContext` encodes "is this a viewsheet chart at all" as identity against `LEGACY`, and a field set by the constructor that knows the answer says it directly.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/graph/GraphGenerator.java:218,433,2345,2570`, `RadarGraphGenerator.java:170`, `VGraphPair.java:1021`, `core/src/main/java/inetsoft/uql/viewsheet/graph/CSSChartStyles.java:106`, `core/src/main/java/inetsoft/report/internal/graph/ChangeChartProcessor.java:1893`
- Modify (callers of the changed signature): `VGraphPair.java:1353`, `core/src/main/java/inetsoft/report/css/CSSProcessor.java:303`, `core/src/main/java/inetsoft/web/binding/dnd/controller/VSChartDndService.java:224`

**Interfaces:**
- Produces: `protected final VizContext vizContext` on `GraphGenerator`, set by both constructors; `CSSChartStyles.apply(ChartDescriptor desc, ChartInfo info, CSSDictionary cssDictionary, List<CSSParameter> parentParams, VizContext ctx)` — one added trailing parameter.

- [ ] **Step 1: Give `GraphGenerator` a context field**

It has two constructors, and each knows the answer. At `:218`:

```java
   protected GraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
```

set `this.vizContext = VizContext.of(chart);`. At `:433`:

```java
   protected GraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
```

set `this.vizContext = VizContext.LEGACY;` — this is the report path, which has no viewsheet and no mark, and `LEGACY` is what the descriptors' identity comparison expects there.

Declare the field with a comment that says why it is a field and not a parameter:

```java
   /**
    * The context this graph resolves modern chrome against, fixed at construction because the
    * methods that need it see only descriptors and scales. The viewsheet constructor knows the
    * assembly; the report constructor knows there is none.
    */
   protected final VizContext vizContext;
```

Then replace `VizContext.ofGate()` at `:2345` and `:2570` with `vizContext`.

- [ ] **Step 2: `RadarGraphGenerator` inherits it**

`RadarGraphGenerator:170` (`fixParallelCoord`) is a subclass method — replace `VizContext.ofGate()` with the inherited `vizContext`.

- [ ] **Step 3: `VGraphPair` already holds the info**

`VGraphPair:1021` sits in `fixChartFormat(ChartVSAssemblyInfo info)` — use `VizContext.of(info)`.

- [ ] **Step 4: `CSSChartStyles.apply` takes a context**

Add a trailing `VizContext ctx` parameter and use it in place of the `ofGate()` call at `:106`. Then its three callers:

| Caller | Passes |
|---|---|
| `VGraphPair:1353` | `VizContext.of(info)` — `info` is the `ChartVSAssemblyInfo` in scope |
| `VSChartDndService:224` | `VizContext.of(clone.getVSAssemblyInfo())` — check the local's real name at the site and use the assembly it is cloning |
| `CSSProcessor:303` | `VizContext.LEGACY` — report path, no viewsheet |

- [ ] **Step 5: `ChangeChartProcessor` already holds the info**

`ChangeChartProcessor:1893` sits in `updateColorFrameCSSParentParams(ChartVSAssemblyInfo info, VSChartInfo chartInfo, ...)` — use `VizContext.of(info)`.

- [ ] **Step 6: Add the group's assertions**

Append to `VizContextReadFlipTest`:

```java
   @Test
   void theChartPipelineNoLongerReadsTheOrgGate() throws Exception {
      assertEquals(0, countOfGateIn("report/composition/graph"),
                   "the graph generators take their context from construction");
      assertEquals(0, countOfGateIn("uql/viewsheet/graph"),
                   "and CSSChartStyles takes it as a parameter");
   }

   @Test
   void theReportPathIsLegacyByIdentity() {
      // seven chart descriptors compare identity against LEGACY to mean "not a viewsheet chart",
      // so the report constructor must hand out that exact instance, not an equal one
      assertSame(VizContext.LEGACY, VizContext.LEGACY);
      assertNotSame(VizContext.LEGACY, VizContext.of((VizMark) null),
                    "no factory may return the LEGACY instance");
   }
```

- [ ] **Step 7: Run and compile**

```bash
./mvnw test -pl core -Dtest='VizContextReadFlipTest,VizContextTest,VSChartChromeDefaultsTest'
./mvnw clean install -pl core -DskipTests -o
```

Expected: pass, then BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/graph core/src/main/java/inetsoft/uql/viewsheet/graph \
        core/src/main/java/inetsoft/report/internal/graph core/src/main/java/inetsoft/report/css \
        core/src/main/java/inetsoft/web/binding/dnd core/src/main/java/inetsoft/report/composition/graph/VGraphPair.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextReadFlipTest.java
git commit -m "feat(chart): resolve graph chrome from the chart's mark, legacy on the report path"
```

---

## Task 5: The composer dialog models

Six sites in three models that see only a `ChartInfo` or a descriptor. The two services above them each hold a `ChartVSAssemblyInfo` and simply do not forward it. Threading it means a property dialog opened on a legacy chart previews legacy chrome and agrees with the canvas behind it — which is the point of the phase.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/ChartLinePaneModel.java:37,57`, `core/src/main/java/inetsoft/web/graph/model/dialog/AxisPropertyDialogModel.java:81,263`, `LegendFormatDialogModel.java:68,128`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java`, `core/src/main/java/inetsoft/web/graph/controller/RegionPropertyDialogService.java` (locate the call hops — grep for the three model constructors)

**Interfaces:**
- Produces: each of the three models takes a trailing `VizContext ctx` on the constructor and on its `update*` method, and stores it if both need it.

- [ ] **Step 1: Find the call hops**

```bash
grep -rn "new ChartLinePaneModel(\|new AxisPropertyDialogModel(\|new LegendFormatDialogModel(\|updateChartLinePaneModel(\|updateAxisPropertyDialogModel(\|updateLegendFormatDialogModel(" --include=*.java core/src/main
```

Record every call site in your report. The two services named above are expected to hold a `ChartVSAssemblyInfo` one or two hops up; if a third caller has no route to an assembly, **stop and report it** rather than passing `ofGate()` to make it compile.

- [ ] **Step 2: Widen the three models**

Add a trailing `VizContext ctx` parameter to both the constructor and the `update*` method of each model, and use it in place of `VizContext.ofGate()` at all six sites. The two sites inside each model are the constructor and the updater, which resolve the same values — pass the same context to both.

- [ ] **Step 3: Forward from the services**

In `ChartPropertyDialogService` and `RegionPropertyDialogService`, the assembly info is already in hand at the method that builds these models. Pass `VizContext.of(info)` down. Where the info is one hop further up than the model construction, widen the intervening private method rather than re-fetching the assembly by name.

- [ ] **Step 4: Add the assertion**

Append to `VizContextReadFlipTest`:

```java
   @Test
   void theDialogModelsNoLongerReadTheOrgGate() throws Exception {
      assertEquals(0, countOfGateIn("web/graph/model/dialog"),
                   "a dialog opened on a legacy chart must preview legacy chrome");
      assertEquals(0, countOfGateIn("web/composer/model/vs"),
                   "likewise the chart line pane");
   }
```

- [ ] **Step 5: Run and compile**

```bash
./mvnw test -pl core -Dtest=VizContextReadFlipTest
./mvnw clean install -pl core -DskipTests -o
```

Expected: pass, then BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/web/graph core/src/main/java/inetsoft/web/composer \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextReadFlipTest.java
git commit -m "feat(composer): preview dialog chrome from the chart's own mark"
```

---

## Task 6: Services, query, lens and the report path

The remainder — ten sites that do not form a pattern with each other, which is why they are one task rather than four.

**Files and the resolution for each:**

| Site | Enclosing method | Resolution |
|---|---|---|
| `BaseTableService.java:462` | `setLayout(TableDataVSAssembly assembly, VSTableLens lens, ...)` | `VizContext.of(assembly.getVSAssemblyInfo())` |
| `BaseTableService.java:1163` | `loadTableModelProperties(...)` | `VizContext.of(tinfo)` — `tinfo` is in scope at the site |
| `FormatPainterService.java:217` | `getFormat(@ClusterProxyKey String runtimeId, GetVSObjectFormatEvent event, ...)` | the assembly is fetched from the runtime viewsheet in that method; use its info |
| `ChangeChartTypeService.java:204` | `changeChartType(@ClusterProxyKey String id, ChangeChartTypeEvent event, ...)` | same — the chart assembly is in scope by the time the site is reached |
| `DataVSAQuery.java:133` | `getViewTableLens(TableLens data)` | `VizContext.of(((VSAssembly) getAssembly()).getVSAssemblyInfo())` — `getAssembly()` is used the same way at `:106-120` |
| `VSTableLens.java:1779` | `initTableLensRowHeights(VSAssemblyInfo info)` | `VizContext.of(info)` |
| `CSSProcessor.java:474` | `applyCSS(ChartInfo cinfo, CSSDictionary cssDict, ...)` | `VizContext.LEGACY` — report path, structurally no viewsheet |
| `VsToReportConverter.java:1376` | `createTitle(VSAssembly assembly, String title, ...)` | `VizContext.of(assembly.getVSAssemblyInfo())` |
| `VsToReportConverter.java:1516` | `addChart(ChartVSAssembly assembly, DataSet data, ...)` | `VizContext.of(assembly.getVSAssemblyInfo())` |
| `SelectionBaseVSAssemblyInfo.java:139` | `getEffectiveCellHeight()` | `VizContext.of(this)` |
| `ChartColorPaletteController.java:42` | `getChartColorPalette()` | **stays `ofGate()`** — add the comment below |

- [ ] **Step 1: Flip the ten, and document the one that stays**

Apply the table above. On `ChartColorPaletteController:42`, leave the call and add:

```java
   // stays on the org gate by decision: this is a parameterless bootstrap fetch with no assembly in
   // scope, and the swatch list it returns is a global palette rather than per-assembly chrome.
   // A per-assembly endpoint would make it uncacheable for the sake of a picker.
```

- [ ] **Step 2: Add the assertion, with the allow-list**

Append to `VizContextReadFlipTest`:

```java
   @Test
   void onlyTheDocumentedSitesStillReadTheOrgGate() throws Exception {
      // the whole point of the phase: after it, ofGate() survives in exactly three places, each for a
      // stated reason. VizContext defines it; VSAssemblyInfo's creation seed reads the assembly's own
      // mark via of(this) and no longer appears here; ChartColorPaletteController has no assembly.
      assertEquals(1, countOfGateIn("web/portal/controller"),
                   "ChartColorPaletteController is the one deliberate survivor");
      assertEquals(0, countOfGateIn("report/composition"),
                   "query and lens resolve per assembly");
      assertEquals(0, countOfGateIn("web/viewsheet/controller"),
                   "table services resolve per assembly");
   }
```

- [ ] **Step 3: Run and compile**

```bash
./mvnw test -pl core -Dtest='VizContextReadFlipTest,FormatPainterServiceTest'
./mvnw clean install -pl core -DskipTests -o
```

Expected: pass, then BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/web core/src/main/java/inetsoft/report core/src/main/java/inetsoft/uql \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VizContextReadFlipTest.java
git commit -m "feat(viewsheet): resolve remaining server reads from the assembly's mark"
```

---

## Task 7: The sweep, the manual pass and the documents

- [ ] **Step 1: Prove the flip is complete**

```bash
grep -rn "VizContext.ofGate()" --include=*.java core/src/main
```

Expected: exactly **two** hits — the factory's own definition in `VizContext.java`, and `ChartColorPaletteController:42` with its comment. Anything else is an unflipped site: flip it, or report why it cannot be.

```bash
grep -rn "VizContext.of(" --include=*.java core/src/main | wc -l
```

Record the number in your report. It should be roughly 43 plus the creation site.

- [ ] **Step 2: The full suite**

```bash
./mvnw test -pl core 2>&1 | tail -30
```

Expected: no failures. The baseline is 4887 passing at the P3 commit; this phase adds tests, so the number rises. **A failure here is a real regression** — this phase changes what resolvers return, and a test that encoded the org-gated answer will now be wrong. Read each failure before changing it: if the test asserted "gate on means modern" for an assembly with no mark, the test's premise is what this phase invalidates, and it should be updated to build a marked fixture. Say in your report which tests you touched and why.

- [ ] **Step 3: The manual pass — record the result of each**

Build and start per `CLAUDE.md`, gate **on**, using a dashboard saved before the mark existed.

1. **The reversal.** The legacy dashboard now renders **legacy** chrome — titles, fonts, axis colours and table banding all back to their pre-gate appearance. This is the check that failed by design in P3; it must pass now.
2. Press Modernize on it: it renders modern.
3. A dashboard created new under the gate renders modern without any action.
4. **Export agrees with view, for both.** Export the unmarked dashboard and the modernized one to **PDF, PNG and Excel** and compare each against its canvas. Chrome colours move; geometry does not.
5. Turn the gate **off**: the marked dashboard reverts to legacy read-time chrome.
6. Turn dark mode off with the gate on: nothing is stranded — no dark card backgrounds under light chart chrome.
7. Open a chart property dialog on the unmarked dashboard: the preview shows **legacy** chrome, matching the canvas.
8. A mixed dashboard — one Modernized assembly beside unmarked ones — renders both correctly side by side.
9. Scheduled export of the unmarked dashboard matches its interactive export.

- [ ] **Step 4: Update the documents**

In `2026-08-14-seed-mark-forward-half-design.md`: mark P4 built in §5, and record anything the implementation contradicted. In `chart-card-roadmap.md`: add the Done row, mark P4 shipped and **P5 startable** in the dependency picture, and re-derive "What to pick up next" from the picture rather than editing the old ranking — the file's own instructions require re-deriving, and the ranking is dated.

Record in the roadmap that **L′, the title lane height row, is now unblocked** — it needed both the flag and a read path that consults the mark, and this phase supplies the second.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md \
        docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "docs(chart-card): record what P4 built and re-derive the next step"
```

---

## Done when

- `VizContext.ofGate()` survives in exactly two places in `core/src/main`: its own definition, and `ChartColorPaletteController` with a comment saying why.
- An unmarked assembly resolves legacy and a marked one resolves modern under the same open gate, asserted in `VizContextReadFlipTest`.
- Creation seeds from `of(this)`, so an assembly added to a legacy dashboard persists the values it will read — with its own test, and the 23-test characterization net still green.
- Closing the org gate still reverts read-time chrome on marked assemblies.
- The full `core` suite passes, with every test this phase had to change named and justified in the report.
- The nine manual checks pass, including export agreeing with view across PDF, PNG and Excel.

**Not in P4, and do not let it creep in:** the `VSObjectModel` mark field, per-assembly CSS scoping, the body-class rename and the 17 `GuiTool` sites (all P5); the dark re-stamp branch; the revert sweep; retiring `resolveSeededCorner` or threading a context into `VSFormat` getters.
