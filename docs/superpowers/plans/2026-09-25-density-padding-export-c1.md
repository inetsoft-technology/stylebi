# Density Padding Slice C1: Table Card Inset in Export — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export a table, crosstab or calc table with its card inset in PDF, PNG (via SVG), PowerPoint and HTML, matching the browser, while Excel, CSV and every zero-inset table export exactly as they do today.

**Architecture:** One resolver on the exporter, `VSExporter.getTableCardInset(info)`, returns the table's padding, or zero where `insetsTableCard()` is false (Excel, CSV). Every geometry site reads the inset only through it: the helpers through `VSTableDataHelper.getCardInset`, the exporter-level sites directly, and HTML through a value `HTMLVSExporter` hands its helpers. Each site then applies the inset explicitly. Nothing copies the assembly.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context with `@SreeHome` (core), Maven.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-25-density-padding-export-design.md`. Read §5–§7 and §9 before starting. This plan is C1 only; C2 (print layout, spec §8) gets its own plan after C1 runs locally.

## Global Constraints

- **Legacy output:** a table with padding `0,0,0,0` exports byte-identically to today. Every new path is inert at a zero inset.
- **Excel and CSV are out:** `insetsTableCard()` is false in `ExcelVSExporter` and `CSVVSExporter`, so every site adds 0 for them.
- **One resolver:** no site reads `getPadding()` for the export inset except `AbstractVSExporter.getTableCardInset`.
- **The lens cache is shared:** `VSTableLens`'s cached widths are never written by the take-back. `getColumnWidthInGrid` is pure.
- **Baselines first:** Task 0 must be finished before any production code in Task 1 lands.
- **Shared checkout:** other Claude sessions use this same checkout. Run `git branch --show-current` immediately before every `git add` and `git commit`, and expect `feature-density-padding-export`. Run each git command as its own call; never chain them with `&&`.
- **Comments:** keep code comments to a short clause. Never mention the spec, this plan, slices or PR numbers in source.
- **Commits:** message body in plain prose, ending with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Never push; the user pushes.

## Review Focus

1. **An asymmetric inset**, for example only `left = 24`, typed by an author: only that edge moves. Pinned in Task 3 (`anAsymmetricInsetMovesOnlyItsOwnEdges`) and Task 6 (`anEdgeWithoutAnInsetStillCopiesTheBorder`).
2. **A hidden, zero-width last column** on a padded table stays at 0 after the take-back. Pinned in Task 2 (`aHiddenLastColumnStaysHidden`).
3. **A card smaller than its inset**, such as a 20 × 20 table with 16px insets: nothing throws and the grid clamps at zero. Pinned in Task 3 (`aCardSmallerThanItsInsetClampsTheGrid`).
4. **A crosstab in the same export as a table** gets the same inset arithmetic through `VSCrosstabHelper`. Pinned in Task 7 (`theCrosstabDataBudgetIsTheGridHeight`).
5. **Exporting PDF, then Excel, in one session:** the take-back must not write into the lens's shared cached widths, or the later Excel export loses width. Pinned in Task 2 (`theTakeBackLeavesTheSharedCacheAlone`).

## How to run tests

`-q` hides the summary on success, so read the surefire report after each run:

```bash
cd /e/StyleBI/stylebi-enterprise/community
./mvnw -q test -pl core -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"' core/target/surefire-reports/TEST-<fully.qualified.ClassName>.xml
```

For a test in `utils/inetsoft-xml-formats`, build core in the same reactor (`-am`) rather than installing it, because other sessions share `~/.m2`:

```bash
./mvnw -q test -pl utils/inetsoft-xml-formats -am -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"' utils/inetsoft-xml-formats/target/surefire-reports/TEST-<fully.qualified.ClassName>.xml
```

A `NoSuchMethodError` from a class you did not touch means a stale `target/` from another branch: add `clean` and rerun.

## File Structure

| File | Responsibility |
|---|---|
| `core/.../report/io/viewsheet/VSExporter.java` | declares the resolver |
| `core/.../report/io/viewsheet/AbstractVSExporter.java` | implements it with `insetsTableCard()`; the expand, region-lens and bottom-tabs sites |
| `core/.../web/viewsheet/service/ExcelVSExporter.java`, `core/.../report/io/viewsheet/excel/CSVVSExporter.java` | `insetsTableCard()` false |
| `core/.../report/composition/VSTableLens.java` | `getColumnWidthInGrid`, the one take-back rule |
| `core/.../web/viewsheet/controller/table/BaseTableService.java` | `getColWidths` switches to the shared take-back |
| `core/.../report/io/viewsheet/VSTableDataHelper.java` | `getCardInset`/`getCardBounds`/`getGridBounds`; cell origin, title, chrome, column widths, border copy, bottom tabs |
| `core/.../report/io/viewsheet/VSTableHelper.java` | horizontal cull, visible rows |
| `core/.../report/io/viewsheet/VSCrosstabHelper.java` | height budget |
| `core/.../report/io/viewsheet/pdf/PDFTableHelper.java` | page cap write-back |
| `core/.../report/io/viewsheet/excel/ExcelVSUtil.java` | the fill width parameter |
| `core/.../report/io/viewsheet/html/HTMLTableDataHelper.java`, `HTMLTableHelper.java`, `HTMLCrosstabHelper.java`, `HTMLVSExporter.java` | the HTML wrapper and sizes |
| `core/src/test/.../report/io/viewsheet/TableExportFixtures.java`, `RecordingTableHelper.java`, `RecordingCrosstabHelper.java` | test-only fixtures shared by Tasks 3–8 |

`core/...` is `core/src/main/java/inetsoft`.

---

### Task 0: Capture the baselines (manual, before any code)

The user does this in the running app. It is the reference for the legacy guarantee, and it cannot be recreated once Task 1 lands.

**Files:**
- Create: `community/.superpowers/baselines/density-padding-export/` (git-excluded through `.git/modules/community/info/exclude`, which lists `.superpowers/`)

- [ ] **Step 1: Build and start the server from this branch as it stands**

The branch head must be the spec commit, with no slice C code.

```bash
cd /e/StyleBI/stylebi-enterprise/community
git branch --show-current     # feature-density-padding-export
git log --oneline -1          # the plan commit, above b7015d876
```

Build and start the server the usual way.

- [ ] **Step 2: Set the server properties**

In EM → Settings → Presentation → Properties, set:
- `viewsheet.density` = `comfortable`
- `viewsheet.modernVisualization` = `true`

- [ ] **Step 3: Build viewsheet `DPX Modern`**

Use any worksheet with at least 60 rows, 8 columns, and at least one dimension and one measure. Create a new viewsheet, so every assembly is marked and seeded with the 16px comfortable inset, and add:

| Assembly | Type | Setup | Exercises |
|---|---|---|---|
| `Chart1` | bar chart | any dimension × measure | the reference inset |
| `TableFit` | table | 3 columns at default widths, 500 × 250 px | last-column fill |
| `TableTall` | table | 3 columns, all 60+ rows, 400 × 250 px | expand mode, match-layout row cut |
| `TableWide` | table | 8 columns, 400 × 250 px | horizontal cull, expand width |
| `TableShrink` | table | 3 columns, 5 rows, Shrink to Fit on, 500 × 250 px | the shrunk card |
| `TableNoTitle` | table | 3 columns, title hidden | the title-hidden paths |
| `TableRound` | table | 3 columns, Format → corner radius 8 | the round clip |
| `Crosstab1` | crosstab | one row header, one column header, one measure, enough rows to scroll | the crosstab paths |
| `Calc1` | freehand table | convert a 3-column table to freehand | the calc path |
| `Tabs1` | tab container, **bottom** tabs | holds `TableInTab`: 3 columns, 4 rows, Shrink to Fit on | the bottom-tabs shift |

Then:
- Add one annotation on a data cell of `TableFit`. It checks HTML annotation placement.
- Open each table's Padding pane and confirm it shows 16 on all four edges.
- Save.

- [ ] **Step 4: Build viewsheet `DPX Legacy`**

Save `DPX Modern` as `DPX Legacy`, then use the dashboard-level Revert (the Modernize/Revert control). Confirm every table's Padding pane shows 0 on all four edges. If Revert is unavailable, set `viewsheet.modernVisualization` to `false`, build the same assemblies in a new viewsheet, and set the property back.

- [ ] **Step 5: Build the two print copies**

- Save `DPX Modern` as `DPX Modern Print`, and `DPX Legacy` as `DPX Legacy Print`.
- In each, create a print layout (Layouts → New Print Layout) and drag onto the page `TableFit`, `TableTall`, `TableShrink`, `Crosstab1`, `Calc1` and `Tabs1`.
- Save.
- These feed C2 later. A PDF export uses the print layout automatically, so they must be separate viewsheets.

- [ ] **Step 6: Export 22 files into the baseline folder**

Export from the viewer's Export dialog. For `DPX Modern` and `DPX Legacy`, export each format twice: once with Data Size = **Match Layout** and once with **Expand Components**. For the print copies, export PDF once.

| File | Viewsheet | Format | Data Size |
|---|---|---|---|
| `modern-pdf-match.pdf`, `modern-pdf-expand.pdf` | DPX Modern | PDF | Match / Expand |
| `modern-png-match.png`, `modern-png-expand.png` | DPX Modern | PNG | Match / Expand |
| `modern-html-match.html`, `modern-html-expand.html` | DPX Modern | HTML | Match / Expand |
| `modern-pptx-match.pptx`, `modern-pptx-expand.pptx` | DPX Modern | PowerPoint | Match / Expand |
| `modern-xlsx-match.xlsx`, `modern-xlsx-expand.xlsx` | DPX Modern | Excel | Match / Expand |
| `legacy-pdf-match.pdf`, `legacy-pdf-expand.pdf` | DPX Legacy | PDF | Match / Expand |
| `legacy-png-match.png`, `legacy-png-expand.png` | DPX Legacy | PNG | Match / Expand |
| `legacy-html-match.html`, `legacy-html-expand.html` | DPX Legacy | HTML | Match / Expand |
| `legacy-pptx-match.pptx`, `legacy-pptx-expand.pptx` | DPX Legacy | PowerPoint | Match / Expand |
| `legacy-xlsx-match.xlsx`, `legacy-xlsx-expand.xlsx` | DPX Legacy | Excel | Match / Expand |
| `modern-print.pdf` | DPX Modern Print | PDF | either |
| `legacy-print.pdf` | DPX Legacy Print | PDF | either |

- [ ] **Step 7: Save the fixture itself**

In EM → Content → Repository, export the four viewsheets and their worksheet as one asset JAR, `dpx-fixture.zip`, into the same folder. Task 11 re-imports it at the other densities.

- [ ] **Step 8: Write `README.txt` in the folder**

Record:
- the commit from Step 1;
- the date;
- the two property values;
- the worksheet used.

---

### Task 1: The resolver

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSExporter.java`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java` (beside `paintsPageBackground()`, `:1834`)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/ExcelVSExporter.java`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/excel/CSVVSExporter.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/TableCardInsetResolverTest.java`
- Test: `utils/inetsoft-xml-formats/src/test/java/inetsoft/report/io/viewsheet/excel/TableCardInsetOfficeResolverTest.java`

**Interfaces:**
- Produces: `Insets VSExporter.getTableCardInset(TableDataVSAssemblyInfo info)`, which never returns null and returns a copy. Also `protected boolean AbstractVSExporter.insetsTableCard()`.

- [ ] **Step 1: Write the failing core test**

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.report.io.viewsheet.svg.PNGVSExporter;
import inetsoft.report.io.viewsheet.svg.SVGVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table's export inset is its padding in every format that paints a card, and zero in the
 * formats that cannot represent one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCardInsetResolverTest {
   @Test
   void cardFormatsResolveTheTablePadding() {
      for(AbstractVSExporter exporter : List.of(
         new SVGVSExporter(new ByteArrayOutputStream()),
         new PNGVSExporter(new ByteArrayOutputStream()),
         new HTMLVSExporter(new ByteArrayOutputStream())))
      {
         assertEquals(new Insets(16, 12, 8, 4),
                      exporter.getTableCardInset(padded(new Insets(16, 12, 8, 4))),
                      exporter.getClass().getSimpleName());
      }
   }

   @Test
   void csvResolvesZero() {
      assertEquals(new Insets(0, 0, 0, 0),
                   new CSVVSExporter(new ByteArrayOutputStream(), null)
                      .getTableCardInset(padded(new Insets(16, 16, 16, 16))));
   }

   @Test
   void aNullPaddingResolvesZero() {
      assertEquals(new Insets(0, 0, 0, 0),
                   new SVGVSExporter(new ByteArrayOutputStream()).getTableCardInset(padded(null)));
   }

   @Test
   void theResolvedInsetIsACopy() {
      TableVSAssemblyInfo info = padded(new Insets(16, 16, 16, 16));
      new SVGVSExporter(new ByteArrayOutputStream()).getTableCardInset(info).left = 0;

      assertEquals(16, info.getPadding().left, "a caller must not be able to change the padding");
   }

   private static TableVSAssemblyInfo padded(Insets padding) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setPadding(padding);
      return info;
   }
}
```

- [ ] **Step 2: Write the failing utils test**

```java
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.io.viewsheet.ppt.PPTContext;
import inetsoft.report.io.viewsheet.ppt.PPTVSExporter;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Excel's fixed row grid cannot represent a card inset, so both Excel exporters resolve zero;
 * PowerPoint paints a card and resolves the padding.
 */
class TableCardInsetOfficeResolverTest {
   @Test
   void excelExportersResolveZero() {
      TableVSAssemblyInfo info = padded();

      assertEquals(new Insets(0, 0, 0, 0),
                   new PoiExcelVSExporter(Mockito.mock(ExcelContext.class),
                                          new ByteArrayOutputStream()).getTableCardInset(info));
      assertEquals(new Insets(0, 0, 0, 0),
                   new OfflineExcelVSExporter(Mockito.mock(ExcelContext.class),
                                              new ByteArrayOutputStream()).getTableCardInset(info));
   }

   @Test
   void powerPointResolvesThePadding() {
      assertEquals(new Insets(16, 16, 16, 16),
                   new PPTVSExporter(Mockito.mock(PPTContext.class), new ByteArrayOutputStream())
                      .getTableCardInset(padded()));
   }

   private static TableVSAssemblyInfo padded() {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.getPadding()).thenReturn(new Insets(16, 16, 16, 16));
      return info;
   }
}
```

- [ ] **Step 3: Run both and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=TableCardInsetResolverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, "cannot find symbol: method getTableCardInset".

- [ ] **Step 4: Declare the resolver on `VSExporter`**

Add `import java.awt.Insets;` and `import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;`. After `boolean isMatchLayout();`, add a default that keeps today's output for any implementer outside `AbstractVSExporter`:

```java
   /**
    * The inset between a table's card edge and its grid in this format: the table's padding,
    * or zero where the format cannot represent one. Never null; the caller owns the copy.
    */
   default Insets getTableCardInset(TableDataVSAssemblyInfo info) {
      return new Insets(0, 0, 0, 0);
   }
```

- [ ] **Step 5: Implement it in `AbstractVSExporter`**

Place it directly after `paintsPageBackground()`. `java.awt.*` is already imported; add `TableDataVSAssemblyInfo` if `inetsoft.uql.viewsheet.internal.*` is not imported.

```java
   /**
    * Whether this format insets a table's grid from its card edge. A spreadsheet's fixed row
    * grid cannot represent an 8-16px inset, and CSV has no geometry.
    */
   protected boolean insetsTableCard() {
      return true;
   }

   @Override
   public Insets getTableCardInset(TableDataVSAssemblyInfo info) {
      Insets padding = info == null || !insetsTableCard() ? null : info.getPadding();
      return padding == null ? new Insets(0, 0, 0, 0) : (Insets) padding.clone();
   }
```

- [ ] **Step 6: Turn it off for Excel and CSV**

In `ExcelVSExporter`, after `paintsPageBackground()`:

```java
   /**
    * A spreadsheet's rows are a fixed grid, so both Excel exporters keep the grid at the card.
    */
   @Override
   protected boolean insetsTableCard() {
      return false;
   }
```

In `CSVVSExporter`, after the constructors:

```java
   @Override
   protected boolean insetsTableCard() {
      return false;
   }
```

- [ ] **Step 7: Run both tests and watch them pass**

Run the core test (4 tests), then the utils test with `-am` (2 tests).
Expected: 0 failures, 0 errors in both reports.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/VSExporter.java core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java core/src/main/java/inetsoft/web/viewsheet/service/ExcelVSExporter.java core/src/main/java/inetsoft/report/io/viewsheet/excel/CSVVSExporter.java core/src/test/java/inetsoft/report/io/viewsheet/TableCardInsetResolverTest.java utils/inetsoft-xml-formats/src/test/java/inetsoft/report/io/viewsheet/excel/TableCardInsetOfficeResolverTest.java
git commit -m "Resolve a table's export inset on the exporter" -m "Every table export site will read the card inset through one resolver, so the sites that run before any helper get the same value as the helpers. Excel and CSV resolve zero: a spreadsheet's fixed rows cannot represent the inset, and CSV has no geometry." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The lens take-back

`VSTableLens.initTableLensColumnWidths` fills its cached last column to the card, once, and Excel, CSV, HTML and print layout all read that cache. The inset paths take the fill back without writing to it.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/VSTableLens.java` (after `getColumnWidths()`, `:337`)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/controller/table/BaseTableService.java` (the width-less last-column fallback in `getColWidths`)
- Test: `core/src/test/java/inetsoft/report/composition/VSTableLensColumnWidthInGridTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `public int VSTableLens.getColumnWidthInGrid(int col, TableDataVSAssemblyInfo info, int insetW)`. `col` must index the cached widths. The method never writes the cache.

- [ ] **Step 1: Write the failing test**

Every table is 400px wide with 3 columns. The lens fills the last column to 400; the grid is 368 (`insetW` 32).

```java
package inetsoft.report.composition;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lens's cached last column is filled to the card; inside a grid 32px narrower the fill
 * gives back up to 32px, never below the column's own width.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTableLensColumnWidthInGridTest {
   // 100 + 100 + the 100px default = 300, so the lens fills the last column by 100 to 200
   @Test
   void aWidthlessLastColumnGivesBackTheInset() {
      Fixture f = fixture(100, 100, Double.NaN);

      assertEquals(168, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   // a set 50px last column is filled by 150 to 200; 32 of that is inside the inset
   @Test
   void aSetLastColumnGivesBackTheInset() {
      Fixture f = fixture(100, 100, 50);

      assertEquals(168, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   // 150 + 130 + 100 = 380, filled by only 20: all of it comes back, down to the 100px default
   @Test
   void aFillSmallerThanTheInsetComesBackWhole() {
      Fixture f = fixture(150, 130, Double.NaN);

      assertEquals(100, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   @Test
   void aHiddenLastColumnStaysHidden() {
      Fixture f = fixture(100, 100, 0);

      assertEquals(0, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   @Test
   void anEarlierColumnIsUnchanged() {
      Fixture f = fixture(100, 100, Double.NaN);

      assertEquals(100, f.lens.getColumnWidthInGrid(0, f.info, 32));
   }

   @Test
   void noInsetKeepsTheCardFill() {
      Fixture f = fixture(100, 100, Double.NaN);

      assertEquals(200, f.lens.getColumnWidthInGrid(2, f.info, 0));
   }

   @Test
   void theTakeBackLeavesTheSharedCacheAlone() {
      Fixture f = fixture(100, 100, Double.NaN);
      f.lens.getColumnWidthInGrid(2, f.info, 32);
      f.lens.getColumnWidthInGrid(2, f.info, 32);

      assertArrayEquals(new int[] { 100, 100, 200 }, f.lens.getColumnWidths(),
                        "Excel and HTML read this same cache later in the export");
   }

   private record Fixture(VSTableLens lens, TableVSAssemblyInfo info) {}

   private static Fixture fixture(double... widths) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(400, 200));

      for(int i = 0; i < widths.length; i++) {
         info.setColumnWidthValue(i, widths[i]);
      }

      VSTableLens lens = new VSTableLens(new DefaultTableLens(new Object[][] {
         { "A", "B", "C" },
         { 1, 2, 3 }
      }));
      lens.initTableGrid(info);
      return new Fixture(lens, info);
   }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=VSTableLensColumnWidthInGridTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, "cannot find symbol: method getColumnWidthInGrid".

- [ ] **Step 3: Implement `getColumnWidthInGrid`**

In `VSTableLens`, directly after `getColumnWidths()`:

```java
   /**
    * A column's cached width inside a grid insetW narrower than the card. The cache fills the
    * last column to the card; this takes back what that fill put into the inset, and never
    * writes the cache.
    */
   public int getColumnWidthInGrid(int col, TableDataVSAssemblyInfo info, int insetW) {
      int width = widths[col];

      if(insetW <= 0 || col != widths.length - 1) {
         return width;
      }

      // the width initTableLensColumnWidths starts from before it fills
      double setW = info.getColumnWidth2(col, this);
      int base = Double.isNaN(setW) || (int) setW < 0 ? AssetUtil.defw : (int) setW;
      int fill = Math.max(0, width - base);

      return width - Math.min(fill, insetW);
   }
```

- [ ] **Step 4: Run it and watch it pass**

Run the same command. Expected: `tests="7"`, 0 failures, 0 errors.

- [ ] **Step 5: Switch `getColWidths` to the shared rule**

In `BaseTableService.getColWidths(assembly, lens, inset)`, replace:

```java
               double lensW = lens.getColumnWidths()[i];

               // the lens fills its last column to the card; take back what it put in the inset
               if(insetW > 0) {
                  lensW = Math.max(AssetUtil.defw, lensW - insetW);
               }

               width = lens.getColumnWidthWithPadding(lensW, i);
```

with:

```java
               // the lens fills its last column to the card; take back what it put in the inset
               double lensW = lens.getColumnWidthInGrid(i, tinfo, (int) insetW);
               width = lens.getColumnWidthWithPadding(lensW, i);
```

This branch runs only for a width-less column, whose base is `AssetUtil.defw`, so the two rules agree.

- [ ] **Step 6: Run the existing column-width tests**

Run: `./mvnw -q test -pl core "-Dtest=BaseTableColWidthsInsetTest,ComposerVSTableServiceColumnWidthInsetTest,VSTableLensColumnWidthInGridTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 5 + 2 + 7 tests, 0 failures, 0 errors.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/composition/VSTableLens.java core/src/main/java/inetsoft/web/viewsheet/controller/table/BaseTableService.java core/src/test/java/inetsoft/report/composition/VSTableLensColumnWidthInGridTest.java
git commit -m "Take a lens's card fill back out of a table's inset" -m "The lens fills its cached last column to the card, once, and several exporters read that cache. A padded table's grid is narrower than the card, so each inset path reading the cache now takes back whatever that fill put into the inset, through one rule that never writes the cache. The browser's column widths use the same rule." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: The grid origin and the title

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java`: new accessors; `write` (`:346`); `writeTitle` (`:394-420`); `getPixelBounds` (`:514-571`)
- Create: `core/src/test/java/inetsoft/report/io/viewsheet/TableExportFixtures.java`
- Create: `core/src/test/java/inetsoft/report/io/viewsheet/RecordingTableHelper.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/TableExportGridOriginTest.java`

**Interfaces:**
- Consumes: `VSExporter.getTableCardInset` (Task 1).
- Produces, on `VSTableDataHelper`:
  - `protected Insets getCardInset(TableDataVSAssemblyInfo info)`: never null; zero when there is no exporter.
  - `protected Rectangle getCardBounds(TableDataVSAssemblyInfo info)`: `getPixelPosition(pixelOffset)` and `getPixelSize`.
  - `protected Rectangle getGridBounds(TableDataVSAssemblyInfo info)`: the card inset on all four edges, with width and height clamped at 0.
- Produces, as test fixtures:
  - `TableExportFixtures.table(Insets, Dimension, double... widths)`
  - `TableExportFixtures.lens(TableDataVSAssembly, int dataRows)`
  - `TableExportFixtures.exporter(Insets, boolean match)`
  - `RecordingTableHelper` with `cells` and `title`

- [ ] **Step 1: Create the shared fixtures**

`TableExportFixtures.java`:

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;

import java.awt.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A three-column table at (40, 60) with a visible 20px title, and an exporter that resolves a
 * given card inset.
 */
final class TableExportFixtures {
   static TableVSAssembly table(Insets padding, Dimension size, double... widths) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      configure(table.getTableDataVSAssemblyInfo(), padding, size, widths);
      return table;
   }

   static void configure(TableDataVSAssemblyInfo info, Insets padding, Dimension size,
                         double... widths)
   {
      info.setPixelOffset(new Point(40, 60));
      info.setPixelSize(size);
      info.setPadding(padding);
      info.setTitleVisibleValue(true);
      info.setTitleHeightValue(20);

      for(int i = 0; i < widths.length; i++) {
         info.setColumnWidthValue(i, widths[i]);
      }
   }

   static VSTableLens lens(TableDataVSAssembly table, int dataRows) {
      Object[][] data = new Object[dataRows + 1][];
      data[0] = new Object[] { "A", "B", "C" };

      for(int r = 1; r <= dataRows; r++) {
         data[r] = new Object[] { r, r * 2, r * 3 };
      }

      VSTableLens lens = new VSTableLens(new DefaultTableLens(data));
      lens.initTableGrid(table.getVSAssemblyInfo());
      return lens;
   }

   static VSExporter exporter(Insets inset, boolean match) {
      VSExporter exporter = mock(VSExporter.class);
      when(exporter.getTableCardInset(any())).thenAnswer(i -> (Insets) inset.clone());
      when(exporter.isMatchLayout()).thenReturn(match);
      return exporter;
   }

   static final Insets NONE = new Insets(0, 0, 0, 0);
   static final Insets COMFORTABLE = new Insets(16, 16, 16, 16);

   private TableExportFixtures() {
   }
}
```

`RecordingTableHelper.java`:

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws nothing and records every cell and the title it would have drawn.
 */
class RecordingTableHelper extends VSTableHelper {
   RecordingTableHelper(TableDataVSAssembly table, VSExporter exporter) {
      setViewsheet(table.getViewsheet());
      setExporter(exporter);
      this.assembly = table;
   }

   @Override
   protected void writeTableCell(int startX, int startY, Dimension span,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText, Object dispObj,
                                 Hyperlink.Ref hyperlink, VSCompositeFormat parentformat,
                                 Rectangle rec, Insets padding)
   {
      cells.add(new Cell(row, col, pixelbounds, format));
   }

   @Override
   protected void writeTitleCell(int startX, int startY, Dimension span,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText, Object dispObj,
                                 Hyperlink.Ref hyperlink, VSCompositeFormat parentformat,
                                 Rectangle rec)
   {
      title = new Rectangle(startX, startY, span.width, span.height);
   }

   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info, VSTableLens lens,
                                   boolean borderOnly)
   {
   }

   Cell cell(int row, int col) {
      return cells.stream().filter(c -> c.row == row && c.col == col).findFirst().orElse(null);
   }

   record Cell(int row, int col, Rectangle2D bounds, VSCompositeFormat format) {}

   final List<Cell> cells = new ArrayList<>();
   Rectangle title;
}
```

- [ ] **Step 2: Write the failing test**

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cells and the title start at the grid's origin, inside the card inset, and the cells stop at
 * the grid's bottom. The table sits at (40, 60) with a 20px title.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportGridOriginTest {
   @Test
   void cellsStartAtTheGridOrigin() {
      Rectangle2D first = write(COMFORTABLE, new Dimension(400, 250), 3).cell(0, 0).bounds();

      assertEquals(56, first.getX(), 0.001, "40 + 16");
      assertEquals(96, first.getY(), 0.001, "60 + 16 + the 20px title");
   }

   @Test
   void withoutAnInsetCellsStartAtTheCard() {
      Rectangle2D first = write(NONE, new Dimension(400, 250), 3).cell(0, 0).bounds();

      assertEquals(40, first.getX(), 0.001);
      assertEquals(80, first.getY(), 0.001);
   }

   @Test
   void theTitleStartsAtTheGridOriginAndSpansTheGrid() {
      Rectangle title = write(COMFORTABLE, new Dimension(400, 250), 3).title;

      assertEquals(new Point(56, 76), title.getLocation());
      assertEquals(368, title.width, "400 - 16 - 16");
   }

   @Test
   void withoutAnInsetTheTitleSpansTheCard() {
      Rectangle title = write(NONE, new Dimension(400, 250), 3).title;

      assertEquals(new Point(40, 60), title.getLocation());
      assertEquals(400, title.width);
   }

   // 20 rows overflow a 100px card; no cell reaches past 60 + 100 - 16
   @Test
   void cellsStopAtTheGridBottom() {
      double bottom = write(COMFORTABLE, new Dimension(400, 100), 20).cells.stream()
         .mapToDouble(c -> c.bounds().getMaxY()).max().orElseThrow();

      assertEquals(144, bottom, 0.001);
   }

   @Test
   void withoutAnInsetCellsStopAtTheCardBottom() {
      double bottom = write(NONE, new Dimension(400, 100), 20).cells.stream()
         .mapToDouble(c -> c.bounds().getMaxY()).max().orElseThrow();

      assertEquals(160, bottom, 0.001);
   }

   @Test
   void anAsymmetricInsetMovesOnlyItsOwnEdges() {
      RecordingTableHelper helper = write(new Insets(0, 24, 0, 0), new Dimension(400, 250), 3);

      assertEquals(64, helper.cell(0, 0).bounds().getX(), 0.001, "40 + 24");
      assertEquals(80, helper.cell(0, 0).bounds().getY(), 0.001, "no top inset");
      assertEquals(376, helper.title.width, "only the left edge comes off");
   }

   @Test
   void aCardSmallerThanItsInsetClampsTheGrid() {
      TableVSAssembly table = table(COMFORTABLE, new Dimension(20, 20), 100, 100, 100);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(COMFORTABLE, false));

      assertDoesNotThrow(() -> helper.write(table, lens(table, 3)));
      Rectangle grid = helper.getGridBounds(table.getTableDataVSAssemblyInfo());
      assertEquals(0, grid.width);
      assertEquals(0, grid.height);
   }

   private static RecordingTableHelper write(Insets inset, Dimension size, int rows) {
      TableVSAssembly table = table(inset, size, 100, 100, 100);
      VSTableLens lens = lens(table, rows);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens);
      return helper;
   }
}
```

- [ ] **Step 3: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=TableExportGridOriginTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, "cannot find symbol: method getGridBounds".

- [ ] **Step 4: Add the accessors to `VSTableDataHelper`**

Put these after `getViewsheetBounds()`:

```java
   /**
    * The table card's inset as this helper's exporter resolves it.
    */
   protected Insets getCardInset(TableDataVSAssemblyInfo info) {
      VSExporter exporter = getExporter();
      Insets inset = exporter == null ? null : exporter.getTableCardInset(info);
      return inset == null ? new Insets(0, 0, 0, 0) : inset;
   }

   /**
    * The card: the assembly's own rect, in viewsheet pixels.
    */
   protected Rectangle getCardBounds(TableDataVSAssemblyInfo info) {
      Point pos = getViewsheet().getPixelPosition(info.getPixelOffset());
      Dimension size = getViewsheet().getPixelSize(info);
      return new Rectangle(pos.x, pos.y, size.width, size.height);
   }

   /**
    * The grid: the card less its inset on all four edges.
    */
   protected Rectangle getGridBounds(TableDataVSAssemblyInfo info) {
      Rectangle card = getCardBounds(info);
      Insets inset = getCardInset(info);

      return new Rectangle(card.x + inset.left, card.y + inset.top,
                           Math.max(0, card.width - inset.left - inset.right),
                           Math.max(0, card.height - inset.top - inset.bottom));
   }
```

- [ ] **Step 5: Start the cells at the grid**

In `getPixelBounds`, replace:

```java
      Point pos = getViewsheet().getPixelPosition(info.getPixelOffset());
      int titleHeight = info.isTitleVisible() ? info.getTitleHeight() : 0;
      int x = pos.x;
      int y = pos.y + titleHeight;
```

with:

```java
      Rectangle grid = getGridBounds(info);
      int titleHeight = info.isTitleVisible() ? info.getTitleHeight() : 0;
      int x = grid.x;
      int y = grid.y + titleHeight;
```

Then replace `int totalInfoY = pos.y + info.getPixelSize().height;` with:

```java
      int totalInfoY = grid.y + grid.height;
```

At a zero inset the grid is the card, and `getViewsheet().getPixelSize(info)` returns `info.getPixelSize()` whenever that is set, so the output is unchanged.

- [ ] **Step 6: Start the title at the grid**

The title is positioned from the raw pixel offset, not `getPixelPosition`, so keep that origin and add the inset. In `writeTitle`, after `Point position = info.getPixelOffset();`, add:

```java
      Insets inset = getCardInset(info);
```

and change the `writeTitleCell` call's first two arguments to:

```java
      writeTitleCell(position.x + inset.left - viewsheetX, position.y + inset.top - viewsheetY,
                     titleBounds,
```

keeping the remaining arguments as they are.

In `write`, replace:

```java
      int infoWidth = CoordinateHelper.getAssemblySize(
         assembly, CoordinateHelper.getLensSize(lens, true)).width;
```

with:

```java
      Insets inset = getCardInset(info);
      // the title lane spans the grid, inside the card inset
      int infoWidth = Math.max(0, CoordinateHelper.getAssemblySize(
         assembly, CoordinateHelper.getLensSize(lens, true)).width - inset.left - inset.right);
```

- [ ] **Step 7: Run it and watch it pass**

Run the same command. Expected: `tests="8"`, 0 failures, 0 errors.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java core/src/test/java/inetsoft/report/io/viewsheet/TableExportFixtures.java core/src/test/java/inetsoft/report/io/viewsheet/RecordingTableHelper.java core/src/test/java/inetsoft/report/io/viewsheet/TableExportGridOriginTest.java
git commit -m "Start an exported table's grid inside its card inset" -m "Cells and the title now start at the grid's origin, the card moved in by its inset, and cells stop at the grid's bottom. The title keeps its own origin, the raw pixel offset, so a title in an embedded viewsheet does not move." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: The chrome bounds

`getObjectPixelBounds` feeds the round-corner clip and `drawObjectFormat`, so it must describe the card. Its first rect is built from `getPixelBounds`, which now starts at the grid.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java` (`getObjectPixelBounds`, `:157-216`)
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/TableExportChromeBoundsTest.java`

**Interfaces:**
- Consumes: `getCardInset` and `getPixelBounds` (Task 3), and the fixtures.
- Produces: `getObjectPixelBounds` always returns the card.

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.svg.SVGCoordinateHelper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chrome, meaning the border, the background and the round clip, stays on the card whatever
 * the inset. A shrunk card wraps its columns plus the inset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportChromeBoundsTest {
   @Test
   void theChromeIsTheCard() {
      assertEquals(new Rectangle2D.Double(40, 60, 400, 250),
                   chrome(COMFORTABLE, new Dimension(400, 250), false, 3).getBounds2D());
   }

   // 3 x 100 columns, unfilled when shrunk
   @Test
   void aShrunkCardWrapsTheColumnsAndTheInset() {
      assertEquals(332, chrome(COMFORTABLE, new Dimension(400, 250), true, 3).getWidth(), 0.001);
      assertEquals(300, chrome(NONE, new Dimension(400, 250), true, 3).getWidth(), 0.001);
   }

   @Test
   void aShrunkCardIsTallerByTheVerticalInset() {
      double withInset = chrome(COMFORTABLE, new Dimension(400, 250), true, 3).getHeight();
      double without = chrome(NONE, new Dimension(400, 250), true, 3).getHeight();

      assertEquals(32, withInset - without, 0.001);
   }

   private static Rectangle2D chrome(Insets inset, Dimension size, boolean shrink, int rows) {
      TableVSAssembly table = table(inset, size, 100, 100, 100);
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      info.setShrinkValue(shrink);
      VSTableLens lens = lens(table, rows);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens);
      SVGCoordinateHelper vHelper = new SVGCoordinateHelper();
      vHelper.setViewsheet(table.getViewsheet());
      return helper.getObjectPixelBounds(info, lens, vHelper);
   }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=TableExportChromeBoundsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `theChromeIsTheCard` passes; the card branch already reads `vHelper.getBounds`. `aShrunkCardWrapsTheColumnsAndTheInset` fails with "expected 332.0 but was 300.0", and the height test fails with "expected 32.0 but was 0.0".

- [ ] **Step 3: Grow the first rect back to the card**

In `getObjectPixelBounds`, replace:

```java
      bounds.height = (int) (height * getPixelToPointRatio());
```

with:

```java
      Insets inset = getCardInset(info);
      double ratio = getPixelToPointRatio();
      // getPixelBounds starts at the grid; the chrome is the card around it
      bounds.x -= (int) Math.round(inset.left * ratio);
      bounds.y -= (int) Math.round(inset.top * ratio);
      bounds.width += (int) Math.round((inset.left + inset.right) * ratio);
      bounds.height = (int) ((height + inset.top + inset.bottom) * ratio);
```

Then in the shrink branch replace:

```java
            Dimension d = new Dimension(getShrinkTableWidth(lens), 0);
```

with:

```java
            Dimension d = new Dimension(getShrinkTableWidth(lens) + inset.left + inset.right, 0);
```

PowerPoint's `getPixelBounds` returns points, and its ratio is 0.75, which is why the grow-back is scaled by the ratio. `vHelper.getOutputSize` converts the shrink width itself.

- [ ] **Step 4: Run it and watch it pass**

Run the same command, then rerun `TableExportGridOriginTest`. Expected: 3 tests and 8 tests, 0 failures, 0 errors.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java core/src/test/java/inetsoft/report/io/viewsheet/TableExportChromeBoundsTest.java
git commit -m "Keep an exported table's chrome on its card" -m "The border, background and round clip read the object bounds, which are built from the cell geometry that now starts inside the inset. Those bounds grow back out to the card, and a shrunk card wraps its columns plus the inset, as it does in the browser." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: The column widths

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/excel/ExcelVSUtil.java` (`calculateColumnWidths`, `:146-162`)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java` (`calculateColumnWidths` `:488-494`; `write` `:330-331`)
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/TableExportColumnWidthsTest.java`

**Interfaces:**
- Consumes: `VSTableLens.getColumnWidthInGrid` (Task 2) and `getCardInset` (Task 3).
- Produces: `ExcelVSUtil.calculateColumnWidths(Viewsheet, TableDataVSAssemblyInfo, VSTableLens, boolean isFillColumns, boolean matchLayout, boolean needDistributeWidth, int insetW)`. The old six-argument form is removed; `VSTableDataHelper` is its only caller.

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The last column fills the grid, 400 - 32 = 368 wide, instead of the 400px card. A width-less
 * last column does not keep the fill the lens gave it to reach the card.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportColumnWidthsTest {
   @Test
   void theLastColumnFillsTheGrid() {
      assertArrayEquals(new int[] { 100, 100, 168 }, pixelWidths(COMFORTABLE, 100, 100, 100));
   }

   @Test
   void withoutAnInsetTheLastColumnFillsTheCard() {
      assertArrayEquals(new int[] { 100, 100, 200 }, pixelWidths(NONE, 100, 100, 100));
   }

   // 150 + 130 + the 100px default = 380, past the 368 grid: the lens's 20px fill comes back
   @Test
   void aWidthlessLastColumnDropsTheLensCardFill() {
      assertArrayEquals(new int[] { 150, 130, 100 },
                        pixelWidths(COMFORTABLE, 150, 130, Double.NaN));
   }

   @Test
   void theWrappedLineCountsUseGridWidths() {
      TableVSAssembly table = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      VSTableLens lens = lens(table, 3);
      new RecordingTableHelper(table, exporter(COMFORTABLE, false)).write(table, lens);

      assertArrayEquals(new double[] { 100, 100, 168 }, lens.getColWidths(), 0.001);
   }

   private static int[] pixelWidths(Insets inset, double... widths) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), widths);
      VSTableLens lens = lens(table, 3);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens);
      return helper.columnPixelW;
   }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=TableExportColumnWidthsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `withoutAnInset…` passes. The two inset tests fail with `[100, 100, 200]` and `[150, 130, 120]`. `theWrappedLineCounts…` fails with 200 at index 2.

- [ ] **Step 3: Give `ExcelVSUtil.calculateColumnWidths` a fill width**

Add `int insetW` as the last parameter. Replace `int totalPixelW = info.getPixelSize().width;` with:

```java
      // the grid inside the card inset, which the last column fills
      int totalPixelW = Math.max(0, info.getPixelSize().width - insetW);
```

Inside the first loop, replace `w = widths[i];` with:

```java
            w = lens.getColumnWidthInGrid(i, info, insetW);
```

- [ ] **Step 4: Pass the inset from `VSTableDataHelper`**

Replace `calculateColumnWidths`:

```java
   protected int[] calculateColumnWidths(TableDataVSAssemblyInfo info,
                                         VSTableLens lens)
   {
      Insets inset = getCardInset(info);

      return ExcelVSUtil.calculateColumnWidths(
         getViewsheet(), info, lens, isFillColumns(info), getExporter().isMatchLayout(),
         needDistributeWidth(), inset.left + inset.right);
   }
```

In `write`, replace:

```java
         // export still lays the grid across the whole card
         double[] colWidths = BaseTableService.getColWidths(assembly, lens, false);
```

with:

```java
         Insets gridInset = getCardInset(info);
         // the wrapped-line widths fill the grid inside the card inset
         double[] colWidths = BaseTableService.getColWidths(
            assembly, lens, gridInset.left + gridInset.right > 0);
```

Task 3 declared `Insets inset` further down in `write`; the name `gridInset` keeps the two apart.

- [ ] **Step 5: Run it and watch it pass**

Run: `./mvnw -q test -pl core "-Dtest=TableExportColumnWidthsTest,TableExportGridOriginTest,TableExportChromeBoundsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 4 + 8 + 3 tests, 0 failures, 0 errors.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/excel/ExcelVSUtil.java core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java core/src/test/java/inetsoft/report/io/viewsheet/TableExportColumnWidthsTest.java
git commit -m "Fill an exported table's last column to its grid" -m "The pixel column widths and the widths behind the wrapped-line counts both filled the last column to the card. They now fill it to the grid inside the inset, and take back the fill the lens's cached widths carry, so a padded table's columns fit its grid as they do in the browser." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: The border copy

`applyTableBorders` copies the assembly border onto the outer cells. With an inset the border belongs to the card alone.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java` (`applyTableBorders`, `:423`)
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/TableExportBorderCopyTest.java`

**Interfaces:**
- Consumes: `getCardInset` (Task 3) and the fixtures.
- Produces: `applyTableBorders` ignores every edge whose inset is non-zero. Its signature is unchanged, so its three callers, including `OfflineExcelTableHelper`, need no edit.

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A card with a thin border and an inset keeps that border at the card edge; the outer cells no
 * longer copy it, or a second frame would appear at the grid edge.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportBorderCopyTest {
   @Test
   void anInsetEdgeKeepsTheBorderOffTheOuterCells() {
      RecordingTableHelper helper = write(COMFORTABLE);

      assertEquals(0, left(helper, 0, 0));
      assertEquals(0, right(helper, 0, 2));
   }

   @Test
   void withoutAnInsetTheOuterCellsCopyTheBorder() {
      RecordingTableHelper helper = write(NONE);

      assertEquals(StyleConstants.THIN_LINE, left(helper, 0, 0));
      assertEquals(StyleConstants.THIN_LINE, right(helper, 0, 2));
   }

   @Test
   void anEdgeWithoutAnInsetStillCopiesTheBorder() {
      RecordingTableHelper helper = write(new Insets(0, 24, 0, 0));

      assertEquals(0, left(helper, 0, 0), "the left edge has an inset");
      assertEquals(StyleConstants.THIN_LINE, right(helper, 0, 2), "the right edge has none");
   }

   private static RecordingTableHelper write(Insets inset) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), 100, 100, 100);
      int thin = StyleConstants.THIN_LINE;
      table.getTableDataVSAssemblyInfo().getFormat().getUserDefinedFormat()
         .setBorders(new Insets(thin, thin, thin, thin));
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens(table, 3));
      return helper;
   }

   private static int left(RecordingTableHelper helper, int row, int col) {
      return helper.cell(row, col).format().getUserDefinedFormat().getBorders().left;
   }

   private static int right(RecordingTableHelper helper, int row, int col) {
      return helper.cell(row, col).format().getUserDefinedFormat().getBorders().right;
   }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=TableExportBorderCopyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `withoutAnInset…` passes; the other two fail with "expected 0 but was 4097", or whatever `THIN_LINE`'s value is.

- [ ] **Step 3: Drop the inset edges**

At the top of `applyTableBorders`, before `if(!left && !bottom && !right && !top)`, add:

```java
      if(info instanceof TableDataVSAssemblyInfo) {
         // an inset edge leaves the assembly border at the card edge
         Insets inset = getCardInset((TableDataVSAssemblyInfo) info);
         left = left && inset.left == 0;
         bottom = bottom && inset.bottom == 0;
         right = right && inset.right == 0;
         top = top && inset.top == 0;
      }
```

- [ ] **Step 4: Run it and watch it pass**

Run the same command. Expected: `tests="3"`, 0 failures, 0 errors.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java core/src/test/java/inetsoft/report/io/viewsheet/TableExportBorderCopyTest.java
git commit -m "Leave an exported table's border on its card" -m "The outer cells copied the assembly border so the edge looked continuous. With an inset the grid no longer reaches the card edge, so the copy drew a second frame inside it. An edge with an inset now leaves the border to the card; an edge without one still copies it." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: The row and column budgets

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableHelper.java`: horizontal cull `:144-161`; `getVisibleRowCount` `:200-246`; its call `:259`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSCrosstabHelper.java`: `getTableRectangle` `:317`; `getTablePixelHeight` `:331-337`; `writeData` `:367-372`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFTableHelper.java` (page cap `:197`)
- Create: `core/src/test/java/inetsoft/report/io/viewsheet/RecordingCrosstabHelper.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/TableExportBudgetTest.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/pdf/PDFTableCardInsetTest.java`

**Interfaces:**
- Consumes: `getCardInset` and `getGridBounds` (Task 3) and the fixtures.
- Produces:
  - `protected static int VSTableHelper.getVisibleRowCount(TableDataVSAssemblyInfo info, VSTableLens lens, Insets inset)`, replacing the two-argument form.
  - `protected double VSCrosstabHelper.getDataHeightBudget(TableDataVSAssemblyInfo info)`.

- [ ] **Step 1: Create the crosstab recorder**

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * A crosstab helper that draws nothing, for the crosstab's own geometry.
 */
class RecordingCrosstabHelper extends VSCrosstabHelper {
   RecordingCrosstabHelper(TableDataVSAssembly table, VSExporter exporter) {
      setViewsheet(table.getViewsheet());
      setExporter(exporter);
      this.assembly = table;
   }

   @Override
   protected void writeTableCell(int startX, int startY, Dimension span,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText, Object dispObj,
                                 Hyperlink.Ref hyperlink, VSCompositeFormat parentformat,
                                 Rectangle rec, Insets padding)
   {
   }

   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info, VSTableLens lens,
                                   boolean borderOnly)
   {
   }
}
```

- [ ] **Step 2: Write the failing budget test**

An inset of T + B must behave exactly like a card T + B shorter, and an inset of L + R like a card L + R narrower. That makes each expectation derivable without knowing font metrics.

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The rows and columns an export fits into a padded table are those of the grid, not the card.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportBudgetTest {
   // 200 + 200 already passes the 368 grid, so the third column is culled
   @Test
   void theHorizontalCullStopsAtTheGrid() {
      assertTrue(written(COMFORTABLE, 2).isEmpty());
   }

   // 200 + 200 = 400 does not pass the 400 card, so the third column is drawn
   @Test
   void withoutAnInsetTheCullStopsAtTheCard() {
      assertFalse(written(NONE, 2).isEmpty());
   }

   @Test
   void theVisibleRowsAreThoseOfTheGrid() {
      TableVSAssembly padded = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      TableVSAssembly shorter = table(NONE, new Dimension(400, 218), 100, 100, 100);

      assertEquals(
         VSTableHelper.getVisibleRowCount(shorter.getTableDataVSAssemblyInfo(), lens(shorter, 30), NONE),
         VSTableHelper.getVisibleRowCount(padded.getTableDataVSAssemblyInfo(), lens(padded, 30),
                                          COMFORTABLE));
   }

   @Test
   void theCrosstabDataBudgetIsTheGridHeight() {
      assertEquals(budget(NONE, new Dimension(400, 218)),
                   budget(COMFORTABLE, new Dimension(400, 250)), 0.001);
   }

   private static double budget(Insets inset, Dimension size) {
      CrosstabVSAssembly crosstab = crosstab(inset, size);
      RecordingCrosstabHelper helper = new RecordingCrosstabHelper(crosstab, exporter(inset, false));
      // getTablePixelHeight measures the first cell, which reads the pixel column widths
      helper.columnPixelW = new int[] { 100 };
      return helper.getDataHeightBudget(crosstab.getTableDataVSAssemblyInfo());
   }

   private static java.util.List<RecordingTableHelper.Cell> written(Insets inset, int col) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), 200, 200, 100);
      VSTableLens lens = lens(table, 3);
      RecordingTableHelper helper = new RecordingTableHelper(table, exporter(inset, false));
      helper.write(table, lens);
      return helper.cells.stream().filter(c -> c.col() == col).toList();
   }

   private static CrosstabVSAssembly crosstab(Insets inset, Dimension size) {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      vs.addAssembly(crosstab);
      configure(crosstab.getTableDataVSAssemblyInfo(), inset, size);
      return crosstab;
   }
}
```

- [ ] **Step 3: Write the failing PDF page-cap test**

```java
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A table too tall for one PDF page is capped to the rows that fit; the height written back is
 * the card's, so it carries the vertical inset on top of those rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PDFTableCardInsetTest {
   // 2000 rows pass the 15000px page cap
   @Test
   void theCappedCardCarriesTheVerticalInset() {
      assertEquals(cappedHeight(new Insets(0, 0, 0, 0)) + 32,
                   cappedHeight(new Insets(16, 16, 16, 16)));
   }

   private static int cappedHeight(Insets inset) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(400, 250));
      info.setPadding(inset);

      Object[][] data = new Object[2001][];
      data[0] = new Object[] { "A", "B", "C" };

      for(int r = 1; r < data.length; r++) {
         data[r] = new Object[] { r, r, r };
      }

      VSTableLens lens = new VSTableLens(new DefaultTableLens(data));
      lens.initTableGrid(info);

      VSExporter exporter = mock(VSExporter.class);
      when(exporter.getTableCardInset(any())).thenAnswer(i -> (Insets) inset.clone());
      PDFTableHelper helper = new PDFTableHelper(
         new PDFCoordinateHelper(new ByteArrayOutputStream()), vs, table);
      helper.setExporter(exporter);
      helper.getTableRectangle(info, lens);
      return info.getPixelSize().height;
   }
}
```

- [ ] **Step 4: Run both and watch them fail**

Run: `./mvnw -q test -pl core "-Dtest=TableExportBudgetTest,PDFTableCardInsetTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR on the three-argument `getVisibleRowCount` and on `getDataHeightBudget`.

- [ ] **Step 5: Cull at the grid**

In `VSTableHelper.writeTableDataCell`, replace:

```java
      Viewsheet vs = getViewsheet();
      Dimension psize = vs.getPixelSize(info);
```

with:

```java
      // columns past the grid's right edge are culled
      int gridW = getGridBounds(info).width;
```

and in the loop that follows, replace both `psize.width` with `gridW`.

- [ ] **Step 6: Fit the visible rows to the grid**

Change the signature to:

```java
   protected static int getVisibleRowCount(TableDataVSAssemblyInfo info,
                                           VSTableLens lens, Insets inset)
```

Replace:

```java
         int tableRowsHeight = size.height - (info.isTitleVisible() ? info.getTitleHeight() : 0)
            - info.getViewsheet().getDisplayRowHeight(true, info.getName());
```

with:

```java
         int tableRowsHeight = size.height - inset.top - inset.bottom
            - (info.isTitleVisible() ? info.getTitleHeight() : 0)
            - info.getViewsheet().getDisplayRowHeight(true, info.getName());
```

In `VSTableHelper.getTableRectangle`, change the call to `getVisibleRowCount(info, lens, getCardInset(info))`. In `VSCrosstabHelper.getTableRectangle` (`:317`), change it to `VSTableHelper.getVisibleRowCount(info, lens, getCardInset(info))`.

- [ ] **Step 7: Budget the crosstab to the grid**

In `VSCrosstabHelper`, replace the body of `getTablePixelHeight` with:

```java
      double ratio = getPixelToPointRatio();
      Rectangle2D pbounds = getPixelBounds(info, 0, 0, new Dimension(1, 1), null);
      double pheight = pbounds == null ? 0 : pbounds.getHeight();
      Insets inset = getCardInset(info);

      return (getViewsheet().getPixelSize(info).height - inset.top - inset.bottom) * ratio -
         pheight * ratio;
```

Add after it:

```java
   /**
    * The height the data rows may fill: the grid's, less the title.
    */
   protected double getDataHeightBudget(TableDataVSAssemblyInfo info) {
      Insets inset = getCardInset(info);
      Dimension psize = getViewsheet().getPixelSize(info);
      double height = Math.max(getTablePixelHeight(info),
                               psize.getHeight() - inset.top - inset.bottom);

      if(info.isTitleVisible()) {
         height -= info.getTitleHeight();
      }

      return height;
   }
```

In `writeData`, replace:

```java
      Viewsheet vs = getViewsheet();
      Dimension psize = vs.getPixelSize(info);
      double height = Math.max(getTablePixelHeight(info), psize.getHeight());

      if(info.isTitleVisible()) {
         height -= info.getTitleHeight();
      }
```

with:

```java
      double height = getDataHeightBudget(info);
```

- [ ] **Step 8: Write the card height back at the PDF page cap**

In `PDFTableHelper.getTableRectangle`, replace `info.getPixelSize().height = height;` with:

```java
            Insets inset = getCardInset(info);
            // the capped rows fill the grid; the card carries the inset around them
            info.getPixelSize().height = height + inset.top + inset.bottom;
```

- [ ] **Step 9: Run both and watch them pass**

Run the Step 4 command. Expected: 4 + 1 tests, 0 failures, 0 errors. Then rerun Tasks 3–6: `-Dtest=TableExport*Test`.

- [ ] **Step 10: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/VSTableHelper.java core/src/main/java/inetsoft/report/io/viewsheet/VSCrosstabHelper.java core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFTableHelper.java core/src/test/java/inetsoft/report/io/viewsheet/RecordingCrosstabHelper.java core/src/test/java/inetsoft/report/io/viewsheet/TableExportBudgetTest.java core/src/test/java/inetsoft/report/io/viewsheet/pdf/PDFTableCardInsetTest.java
git commit -m "Fit an exported table's rows and columns to its grid" -m "The column cull, the rows that fit in match layout and the crosstab's height budget measured the card. They now measure the grid inside the inset, so a padded table draws the rows and columns that fit its grid. A table capped at the PDF page limit writes back the card's height, the capped rows plus the vertical inset." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: The exporter-level sites

These run in `AbstractVSExporter` before any helper, so they call `getTableCardInset` directly.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java`:
  - bottom-tabs loop `:333`
  - `getRegionColCount` `:938`
  - `getRegionRowCount` `:1016`
  - `getExpandTableHeight` `:2542-2582`
  - `getExpandTableWidth` `:2683-2728`
  - `getExpandWidth` `:2860-2894`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java` (`applyShrunkBottomTabsShift` and `computeShrunkRenderedHeight`, `:869-960`)
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/TableExportExporterSitesTest.java`

**Interfaces:**
- Consumes: `getTableCardInset` (Task 1) and `getColumnWidthInGrid` (Task 2).
- Produces: `public static void VSTableDataHelper.applyShrunkBottomTabsShift(TableDataVSAssembly assembly, VSTableLens lens, Insets inset)`, replacing the two-argument form. The three-argument `(TableDataVSAssembly, int, int)` form used by print layout is unchanged.

- [ ] **Step 1: Write the failing test**

These tests use a real `SVGVSExporter`, so the resolver reads the table's padding.

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.svg.SVGVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static inetsoft.report.io.viewsheet.TableExportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Expansion, the match-layout region and the bottom-tabs shift size a padded table's card as
 * its grid plus the inset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableExportExporterSitesTest {
   @Test
   void anExpandedCardIsTheRowsPlusTheVerticalInset() {
      TableVSAssembly padded = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      TableVSAssembly plain = table(NONE, new Dimension(400, 250), 100, 100, 100);

      assertEquals(exporter().getExpandTableHeight(plain, lens(plain, 40)) + 32,
                   exporter().getExpandTableHeight(padded, lens(padded, 40)));
   }

   // 600 of columns in a 400 card: the card widens to the columns plus 32
   @Test
   void anExpandedCardIsTheColumnsPlusTheHorizontalInset() {
      assertEquals(632, expandedWidth(COMFORTABLE, 200, 200, 200));
      assertEquals(600, expandedWidth(NONE, 200, 200, 200));
   }

   // 390 of columns fit the 400 card but not the 368 grid
   @Test
   void columnsThatFitTheCardButNotTheGridStillExpand() {
      assertEquals(422, expandedWidth(COMFORTABLE, 100, 100, 190));
      assertEquals(400, expandedWidth(NONE, 100, 100, 190));
   }

   // three 100px columns: 300 reaches the 278 grid at the 3rd; the 310 card is never reached,
   // so the count runs one past the last column
   @Test
   void theMatchLayoutRegionIsTheGridsColumns() {
      assertEquals(3, regionCols(COMFORTABLE));
      assertEquals(4, regionCols(NONE));
   }

   @Test
   void theMatchLayoutRegionIsTheGridsRows() {
      TableVSAssembly padded = table(COMFORTABLE, new Dimension(400, 250), 100, 100, 100);
      TableVSAssembly shorter = table(NONE, new Dimension(400, 218), 100, 100, 100);
      SVGVSExporter exporter = exporter();
      exporter.setMatchLayout(true);

      assertEquals(exporter.getRegionRowCount(shorter, lens(shorter, 30)),
                   exporter.getRegionRowCount(padded, lens(padded, 30)));
   }

   @Test
   void aShrunkBottomTabsCardMovesDownLessByTheVerticalInset() {
      assertEquals(shift(NONE) - 32, shift(COMFORTABLE));
   }

   private static SVGVSExporter exporter() {
      return new SVGVSExporter(new ByteArrayOutputStream());
   }

   private static int expandedWidth(Insets inset, double... widths) {
      TableVSAssembly table = table(inset, new Dimension(400, 250), widths);
      exporter().expandTable(table, lens(table, 3), false);
      return table.getPixelSize().width;
   }

   private static int regionCols(Insets inset) {
      TableVSAssembly table = table(inset, new Dimension(310, 250), 100, 100, 100);
      SVGVSExporter exporter = exporter();
      exporter.setMatchLayout(true);
      return exporter.getRegionColCount(table, lens(table, 3));
   }

   // a shrunk 3-row table in a 400px-tall slot of a bottom-tabs container
   private static int shift(Insets inset) {
      TableVSAssembly real = table(inset, new Dimension(400, 400), 100, 100, 100);
      TableDataVSAssemblyInfo info = real.getTableDataVSAssemblyInfo();
      info.setShrinkValue(true);
      VSTableLens lens = lens(real, 3);

      TabVSAssemblyInfo tabInfo = mock(TabVSAssemblyInfo.class);
      when(tabInfo.isBottomTabs()).thenReturn(true);
      TabVSAssembly tabs = mock(TabVSAssembly.class);
      when(tabs.getVSAssemblyInfo()).thenReturn(tabInfo);
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getContainer()).thenReturn(tabs);

      VSTableDataHelper.applyShrunkBottomTabsShift(table, lens, inset);
      return info.getPixelOffset().y - 60;
   }
}
```

Note: the exporter `expandTable` in `AbstractVSExporter` is protected, and so are `getExpandTableHeight` and the two region methods. The test sits in the same package, so it can call them. `SVGVSExporter.expandTable` handles only rows itself and hands the width path to `super`.

- [ ] **Step 2: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=TableExportExporterSitesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR on the three-argument `applyShrunkBottomTabsShift`. Once it compiles, every inset assertion fails and the `NONE` ones pass.

- [ ] **Step 3: Add the inset to the expanded card**

At the end of `getExpandTableHeight`, replace `return expandedHeight;` with:

```java
      Insets inset = getTableCardInset(info);
      // the card holds the rows plus its inset
      return expandedHeight + inset.top + inset.bottom;
```

At the end of `getExpandTableWidth`, replace `return expandedWidth;` with:

```java
      Insets inset = getTableCardInset(tinfo);
      return expandedWidth + inset.left + inset.right;
```

In `getExpandWidth`, after `TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) obj.getInfo();`, add:

```java
      Insets inset = getTableCardInset(info);
      int insetW = inset.left + inset.right;
```

Then replace `w = widths[i];` with `w = lens.getColumnWidthInGrid(i, info, insetW);`. Finally replace:

```java
      if(ow >= totalWidth) {
```

with:

```java
      // the columns fill the grid inside the card inset
      if(ow - insetW >= totalWidth) {
```

- [ ] **Step 4: Size the match-layout region to the grid**

In `getRegionColCount`, replace `int totalWidth = table.getPixelSize().width;` with:

```java
         Insets inset = getTableCardInset(info);
         int totalWidth = table.getPixelSize().width - inset.left - inset.right;
```

In `getRegionRowCount`, replace `int tableRowsHeight = size.height - headerHeight;` with:

```java
         Insets inset = getTableCardInset(info);
         int tableRowsHeight = size.height - headerHeight - inset.top - inset.bottom;
```

- [ ] **Step 5: Keep a shrunk bottom-tabs card flush with the strip**

In `VSTableDataHelper`, change the two-argument `applyShrunkBottomTabsShift` to:

```java
   public static void applyShrunkBottomTabsShift(TableDataVSAssembly assembly,
                                                 VSTableLens lens, Insets inset)
```

and its last statement to:

```java
      applyShrunkBottomTabsShift(
         assembly, info.getPixelSize().height, computeShrunkRenderedHeight(info, lens, inset));
```

Give `computeShrunkRenderedHeight` an `Insets inset` parameter, and replace its final `return Math.min(height, info.getPixelSize().height);` with:

```java
      // the rendered card is its rows plus its vertical inset
      return Math.min(height + inset.top + inset.bottom, info.getPixelSize().height);
```

In `AbstractVSExporter`'s loop (`:333`), replace the call with:

```java
            VSTableDataHelper.applyShrunkBottomTabsShift(
               tableAssembly, lens, getTableCardInset(tableAssembly.getTableDataVSAssemblyInfo()));
```

- [ ] **Step 6: Run it and watch it pass**

Run the same command. Expected: `tests="6"`, 0 failures, 0 errors.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java core/src/main/java/inetsoft/report/io/viewsheet/VSTableDataHelper.java core/src/test/java/inetsoft/report/io/viewsheet/TableExportExporterSitesTest.java
git commit -m "Size an exported table's card as its grid plus its inset" -m "Expand mode, the match-layout region and the bottom-tabs shift size a table before any helper draws it. They now add the inset to the rows and columns they measure, so an expanded padded table keeps every row and column, and a shrunk one in a bottom-tabs container keeps its card flush with the strip." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: The Excel proof

**Files:**
- Test: `utils/inetsoft-xml-formats/src/test/java/inetsoft/report/io/viewsheet/ExcelTableCardInsetTest.java`. It uses package `inetsoft.report.io.viewsheet` so it can call the protected accessors.

**Interfaces:**
- Consumes: `getGridBounds` and `getCardBounds` (Task 3), and the Excel resolver (Task 1).

- [ ] **Step 1: Write the test**

```java
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.excel.ExcelContext;
import inetsoft.report.io.viewsheet.excel.ExcelCrosstabHelper;
import inetsoft.report.io.viewsheet.excel.ExcelTableHelper;
import inetsoft.report.io.viewsheet.excel.PoiExcelVSExporter;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Through either Excel helper a padded table's grid is its card, so no Excel geometry can move.
 */
class ExcelTableCardInsetTest {
   @Test
   void theExcelTableGridIsTheCard() {
      XSSFWorkbook book = new XSSFWorkbook();
      ExcelTableHelper helper = new ExcelTableHelper(book, book.createSheet(), null);
      helper.setExporter(new PoiExcelVSExporter(Mockito.mock(ExcelContext.class),
                                                new ByteArrayOutputStream()));
      TableVSAssemblyInfo info = padded(helper);

      assertEquals(helper.getCardBounds(info), helper.getGridBounds(info));
   }

   @Test
   void theExcelCrosstabGridIsTheCard() {
      XSSFWorkbook book = new XSSFWorkbook();
      ExcelCrosstabHelper helper = new ExcelCrosstabHelper(book, book.createSheet(), null);
      helper.setExporter(new PoiExcelVSExporter(Mockito.mock(ExcelContext.class),
                                                new ByteArrayOutputStream()));
      TableVSAssemblyInfo info = padded(helper);

      assertEquals(helper.getCardBounds(info), helper.getGridBounds(info));
   }

   private static TableVSAssemblyInfo padded(VSTableDataHelper helper) {
      Viewsheet vs = Mockito.mock(Viewsheet.class);
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.getPadding()).thenReturn(new Insets(16, 16, 16, 16));
      when(info.getPixelOffset()).thenReturn(new Point(40, 60));
      when(vs.getPixelPosition(any(Point.class))).thenReturn(new Point(40, 60));
      when(vs.getPixelSize(info)).thenReturn(new Dimension(400, 250));
      helper.setViewsheet(vs);
      return info;
   }
}
```

- [ ] **Step 2: Run it and confirm it passes**

Run: `./mvnw -q test -pl utils/inetsoft-xml-formats -am -Dtest=ExcelTableCardInsetTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `tests="2"`, 0 failures, 0 errors. This test passes on first run by design: it pins that Tasks 1 and 3 compose so Excel cannot move.

- [ ] **Step 3: Confirm it would catch a regression**

Temporarily delete the `insetsTableCard()` override in `ExcelVSExporter` and rerun. Expected: both tests fail with the grid 32px smaller than the card. Restore the override and rerun: pass.

- [ ] **Step 4: Commit**

```bash
git branch --show-current
git add utils/inetsoft-xml-formats/src/test/java/inetsoft/report/io/viewsheet/ExcelTableCardInsetTest.java
git commit -m "Pin that a table's inset cannot reach Excel" -m "Through either Excel helper a padded table's grid is its card, because both Excel exporters resolve a zero inset. Excel's fixed row grid cannot represent the inset, so this is the mechanical guarantee its output does not move." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: HTML

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableDataHelper.java`: new field and helpers; `fixShrinkTableBounds` `:65-76`; the annotation offset in `updateAnnotations`, `:152-155`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableHelper.java` (`write` `:70-99`)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLCrosstabHelper.java`: `write`; `initRowColumns` `:137-158`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLVSExporter.java` (`writeTable`, `writeCrosstab`, `writeCalcTable`, `:549-573`)
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/html/HTMLTableCardInsetTest.java`

**Interfaces:**
- Consumes: `getTableCardInset` (Task 1) and `getColumnWidthInGrid` (Task 2).
- Produces, on `HTMLTableDataHelper`:
  - `public void setCardInset(Insets)`, where null means zero;
  - `protected boolean hasCardInset()`;
  - `protected int getGridWidth(Rectangle2D)` and `protected int getGridHeight(Rectangle2D)`;
  - `protected String getGridBoxStart(Rectangle2D)`.

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.report.io.viewsheet.html;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An HTML table with a card inset nests its title and data in a box inside the card's div; at a
 * zero inset no box is written.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HTMLTableCardInsetTest {
   private static final Insets INSET = new Insets(16, 16, 16, 16);
   private static final Insets NONE = new Insets(0, 0, 0, 0);

   @Test
   void theGridBoxSitsInsideTheCard() {
      assertTrue(writeTable(INSET, false).contains(
         "<div style='position:absolute;left:16px;top:16px;width:368px;height:218px'>"));
   }

   @Test
   void withoutAnInsetThereIsNoGridBox() {
      assertFalse(writeTable(NONE, false).contains("left:0px;top:0px;width:"));
   }

   // 250 - 20 title - 32 inset
   @Test
   void theDataHeightIsTheGrids() {
      assertTrue(writeTable(INSET, false).contains(";height:198'>"));
      assertTrue(writeTable(NONE, false).contains(";height:230'>"));
   }

   // 3 x 100 columns plus 32
   @Test
   void aShrunkCardWrapsTheColumnsAndTheInset() {
      assertTrue(writeTable(INSET, true).contains("width:332.0px"));
      assertTrue(writeTable(NONE, true).contains("width:300.0px"));
   }

   @Test
   void theCrosstabLastColumnFillsTheGrid() {
      assertEquals(168, crosstabLastColumn(INSET));
      assertEquals(200, crosstabLastColumn(NONE));
   }

   private static String writeTable(Insets inset, boolean shrink) {
      TableVSAssembly table = new TableVSAssembly(new Viewsheet(), "Table1");
      table.getViewsheet().addAssembly(table);
      VSTableLens lens = configure(table, inset, shrink);
      HTMLCoordinateHelper vHelper = new HTMLCoordinateHelper();
      vHelper.setViewsheet(table.getViewsheet());
      HTMLTableHelper helper = new HTMLTableHelper(vHelper, table.getViewsheet(), table);
      helper.setCardInset(inset);
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      helper.write(writer, table, lens);
      writer.flush();
      return out.toString();
   }

   private static int crosstabLastColumn(Insets inset) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(new Viewsheet(), "Crosstab1");
      crosstab.getViewsheet().addAssembly(crosstab);
      VSTableLens lens = configure(crosstab, inset, false);
      HTMLCoordinateHelper vHelper = new HTMLCoordinateHelper();
      vHelper.setViewsheet(crosstab.getViewsheet());
      HTMLCrosstabHelper helper = new HTMLCrosstabHelper(vHelper, crosstab.getViewsheet(),
                                                         crosstab);
      helper.setCardInset(inset);
      helper.write(new PrintWriter(new StringWriter()), crosstab, lens);
      return helper.columnWidths[helper.columnWidths.length - 1];
   }

   private static VSTableLens configure(TableDataVSAssembly table, Insets inset, boolean shrink) {
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 60));
      info.setPixelSize(new Dimension(400, 250));
      info.setPadding(inset);
      info.setTitleVisibleValue(true);
      info.setTitleHeightValue(20);
      info.setShrinkValue(shrink);

      for(int i = 0; i < 3; i++) {
         info.setColumnWidthValue(i, 100);
      }

      VSTableLens lens = new VSTableLens(new DefaultTableLens(new Object[][] {
         { "A", "B", "C" },
         { 1, 2, 3 }
      }));
      lens.initTableGrid(info);
      return lens;
   }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=HTMLTableCardInsetTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR on `setCardInset`.

- [ ] **Step 3: Give `HTMLTableDataHelper` the inset**

Add `import java.awt.geom.Rectangle2D;` if absent. Add these members beside `columnWidths`:

```java
   /**
    * The table card's inset, as the exporter resolves it.
    */
   public void setCardInset(Insets inset) {
      this.cardInset = inset == null ? new Insets(0, 0, 0, 0) : inset;
   }

   protected boolean hasCardInset() {
      return cardInset.top != 0 || cardInset.left != 0 || cardInset.bottom != 0 ||
         cardInset.right != 0;
   }

   protected int getGridWidth(Rectangle2D bounds) {
      return Math.max(0, (int) bounds.getWidth() - cardInset.left - cardInset.right);
   }

   protected int getGridHeight(Rectangle2D bounds) {
      return Math.max(0, (int) bounds.getHeight() - cardInset.top - cardInset.bottom);
   }

   /**
    * The box inside the card that holds the title and the data.
    */
   protected String getGridBoxStart(Rectangle2D bounds) {
      return "<div style='position:absolute;left:" + cardInset.left + "px;top:" +
         cardInset.top + "px;width:" + getGridWidth(bounds) + "px;height:" +
         getGridHeight(bounds) + "px'>";
   }

   protected Insets cardInset = new Insets(0, 0, 0, 0);
```

In `fixShrinkTableBounds`, replace:

```java
         int height = totalHeight + titleH;
         Dimension d = new Dimension(totalWidth, height);
```

with:

```java
         // the card wraps the content plus its inset
         int height = totalHeight + titleH + cardInset.top + cardInset.bottom;
         Dimension d = new Dimension(totalWidth + cardInset.left + cardInset.right, height);
```

In `updateAnnotations`, replace the two position lines with:

```java
         int x = (int) (pos.x + cardInset.left + (hBorderWidth * (c + 1)));
         int y = (int) (titleH + pos.y + cardInset.top + (vBorderWidth * (r + 1)));
```

- [ ] **Step 4: Nest the plain table**

In `HTMLTableHelper.write`, replace:

```java
      table.append("'>");
      table.append(vHelper.getTitle(info));
      appendTableData(table, info, (int) bounds.getHeight() - titleH, (int) bounds.getWidth(), lens);
      table.append("</div>");
```

with:

```java
      table.append("'>");

      if(hasCardInset()) {
         table.append(getGridBoxStart(bounds));
      }

      table.append(vHelper.getTitle(info));
      appendTableData(table, info, getGridHeight(bounds) - titleH, getGridWidth(bounds), lens);
      table.append(hasCardInset() ? "</div></div>" : "</div>");
```

- [ ] **Step 5: Nest the crosstab and fill it to the grid**

In `HTMLCrosstabHelper.write`, replace:

```java
      table.append("'>");
      table.append(vHelper.getTitle(info));
```

with:

```java
      table.append("'>");

      if(hasCardInset()) {
         table.append(getGridBoxStart(bounds));
      }

      table.append(vHelper.getTitle(info));
```

Then replace `appendTableData(writer, (int) bounds.getHeight() - titleH, info, lens);` with `appendTableData(writer, getGridHeight(bounds) - titleH, info, lens);`, and replace `writer.append("</div>");` with `writer.append(hasCardInset() ? "</div></div>" : "</div>");`.

In `initRowColumns`, replace:

```java
         double w =  widths != null && i < widths.length ? widths[i] : info.getColumnWidth(i);
```

with:

```java
         double w = widths != null && i < widths.length ?
            lens.getColumnWidthInGrid(i, info, cardInset.left + cardInset.right) :
            info.getColumnWidth(i);
```

and replace the fill block:

```java
      if(totalWidth < bounds.getWidth()) {
         for(int i = columnWidths.length - 1; i >= 0; i--) {
            if(columnWidths[i] > 0) {
               double diff = bounds.getWidth() - totalWidth;
               columnWidths[columnWidths.length - 1] += diff;
               totalWidth = (int) bounds.getWidth();
```

with:

```java
      double gridW = bounds.getWidth() - cardInset.left - cardInset.right;

      if(totalWidth < gridW) {
         for(int i = columnWidths.length - 1; i >= 0; i--) {
            if(columnWidths[i] > 0) {
               double diff = gridW - totalWidth;
               columnWidths[columnWidths.length - 1] += diff;
               totalWidth = (int) gridW;
```

- [ ] **Step 6: Hand the helpers the resolved inset**

In `HTMLVSExporter.writeTable`, `writeCrosstab` and `writeCalcTable`, add after each helper is constructed:

```java
      thelper.setCardInset(getTableCardInset(assembly.getTableDataVSAssemblyInfo()));
```

- [ ] **Step 7: Run it and watch it pass**

Run the Step 2 command. Expected: `tests="5"`, 0 failures, 0 errors.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableDataHelper.java core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLTableHelper.java core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLCrosstabHelper.java core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLVSExporter.java core/src/test/java/inetsoft/report/io/viewsheet/html/HTMLTableCardInsetTest.java
git commit -m "Inset an HTML-exported table's grid inside its card" -m "A padded table's title and data now sit in a box inside the card's div, which keeps the border, background and rounded clip. The data height, the shrunk card and the crosstab's last-column fill measure the grid, and annotations move with it. At a zero inset no box is written, so the markup is unchanged." -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Verify C1 against the baselines

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/2026-09-25-density-padding-export-design.md`, but only if a manual check changes a ruling (for example the HTML gutter).

- [ ] **Step 1: Run the full core suite and the utils module**

```bash
./mvnw test -pl core > "$SCRATCH/core-tests.log" 2>&1
grep -E "Tests run: [0-9]+, Failures" "$SCRATCH/core-tests.log" | tail -1
./mvnw test -pl utils/inetsoft-xml-formats -am -Dsurefire.failIfNoSpecifiedTests=false > "$SCRATCH/utils-tests.log" 2>&1
grep -E "Tests run: [0-9]+, Failures" "$SCRATCH/utils-tests.log" | tail -1
```

`$SCRATCH` is the session's scratchpad directory. Expected: 0 failures and 0 errors in both. Report any failure by name, including ones this work did not cause.

- [ ] **Step 2: Rebuild the server from this branch and export again**

Export the same 22 files as Task 0 Step 6, with the same names, into `community/.superpowers/baselines/density-padding-export/after-c1/`.

- [ ] **Step 3: Compare the legacy exports**

```bash
cd /e/StyleBI/stylebi-enterprise/community/.superpowers/baselines/density-padding-export
diff legacy-html-match.html after-c1/legacy-html-match.html && echo "html match identical"
diff legacy-html-expand.html after-c1/legacy-html-expand.html && echo "html expand identical"
cmp legacy-png-match.png after-c1/legacy-png-match.png && echo "png match identical"
cmp legacy-png-expand.png after-c1/legacy-png-expand.png && echo "png expand identical"
```

Expected: all four identical. If a PNG differs by bytes only, compare pixels (`magick compare -metric AE a.png b.png null:` prints 0 when they match). Open the legacy PDF, PPTX and XLSX files beside their baselines and confirm they look identical. Their containers carry timestamps, so bytes will differ.

- [ ] **Step 4: Compare the modern Excel exports**

Open `modern-xlsx-match.xlsx` and `modern-xlsx-expand.xlsx` beside their baselines. Confirm every table occupies the same cells, with the same column widths and row heights.

- [ ] **Step 5: Check the modern exports**

For PDF, PNG, HTML and PPTX, in both match and expand, confirm for `TableFit`, `TableTall`, `TableWide`, `TableShrink`, `TableNoTitle`, `TableRound`, `Crosstab1`, `Calc1` and `TableInTab`:
- the inset matches `Chart1`'s 16px;
- the border and background sit at the card edge;
- no row or column is clipped;
- the rounded corner clips the content;
- the shrunk card wraps its columns plus the inset;
- `TableInTab`'s card bottom is flush with the tab strip;
- the crosstab shows no tip icons.

In the HTML exports, check where the vertical scrollbar gutter lands. If a scrollbar that is hidden in the baseline is now visible in the right inset band, report it; the spec's fallback is to widen the scroll div by R.

- [ ] **Step 6: Check the other densities**

Re-import `dpx-fixture.zip` with `viewsheet.density` set to `compact`, and again with `dense`. Export `DPX Modern` to PDF and HTML in match layout, and confirm each table's inset matches the chart's: 12px and 8px.

- [ ] **Step 7: Report**

Report to the user:
- the suite results;
- each comparison, with its result;
- anything that failed, by file and assembly.

Do not push and do not open a PR; the user decides. C2 then gets its own plan, and the render-harness checkpoint (spec §9 step 5) comes up.
