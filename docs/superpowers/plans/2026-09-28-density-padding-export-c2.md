# Density Padding Slice C2: Table Card Inset in Print Layout — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Print a padded table, crosstab or calc table in a print-layout PDF with the same card inset as the browser and the C1 formats. The border and background sit at the card edge. Every zero-inset table and every classic report prints exactly as today.

**Architecture:**
- **The converter.** `VsToReportConverter` reads each table's inset through the PDF exporter's resolver.
  - It draws the part of the card that never grows as a fixed card-top box: the top inset, plus the title lane framed on three sides.
  - It gives the `TableElementDef` the other three edges as a card inset, (0, L, B, R).
- **The report engine.** It lays the columns out in the grid.
  - `TablePaintable` keeps its box as the grid, so no cell calculation moves.
  - Its public geometry (`getBounds`, `getBounds2`, `getHeight`, `getLocation`, `setLocation`) speaks for the card: x − L, width + L + R, and + B on the last region only.
  - It paints the card background under the inset bands.

**Tech Stack:** Java 21, JUnit 5, Spring test context with `@SreeHome` (core), Maven, PyMuPDF for the manual comparisons.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-25-density-padding-export-design.md`. Read §8 and §9.3–§9.4 before starting; §2 holds the rulings. This plan is C2 only. C1 is PR #5808.

## Global Constraints

- **Legacy output:** a table with padding `0,0,0,0` prints exactly as today: same page count, word positions and vector drawings. Every new path is inert at a zero or null inset.
- **Classic reports:** they share `TableElementDef` and `TablePaintable` and never set the card inset. A null `cardInset` must leave every engine path as it is.
- **One resolver:** the converter reads the inset only through `getCardInset(info)`, which calls the lookup `PDFVSExporter` hands it. No converter code reads `getPadding()` for the card inset.
- **No scaling:** the inset is not multiplied by the print layout's `scalefont`, like the chart's padding and the cell padding.
- **Page breaks:** a continuation region gets no top inset and no top border. The inset is (0, L, B, R) on every region, and B is drawn on the last region only.
- **Baselines first:** Task 0 must be finished before any production code in Task 1 lands.
- **Core test tag:** core's surefire runs only JUnit tag `core` (`core/pom.xml:996`). Every new test class carries `@Tag("core")`, or it silently never runs.
- **Shared checkout:** other Claude sessions use this same checkout. Run `git branch --show-current` immediately before every `git add` and `git commit`, and expect `feature-density-padding-print-layout`. Run each git command as its own call; never chain them with `&&`.
- **Comments:** keep code comments to a short clause. Never mention the spec, this plan, slices or PR numbers in source.
- **Commits:** the message body is plain prose, ending with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Never push; the user pushes.

## Review Focus

1. **An asymmetric inset**, for example `8, 24, 4, 0`, typed by an author: only those edges move, and a zero edge keeps today's layout on that side. Pinned in Task 5 (`theTabsHeightAddsTheTopAndBottomInsets`) and Task 6 (`anAsymmetricInsetMovesOnlyItsOwnEdges`).
2. **A card narrower or shorter than its inset:** nothing throws or hangs, and every width and height clamps at zero. Pinned in Task 1 (`aCardNarrowerThanItsInsetStillPrints`) and Task 6 (`aCardSmallerThanItsInsetClampsAtZero`).
3. **A Fit Contents table wrapped into segments across several pages:**
   - every segment, the later pages included, is cut at the grid width;
   - B comes once, after the last segment.

   Pinned in Task 1 (`aLaterPageCutsAtTheGridWidthToo`) and Task 4 (`onlyTheLastFitContentsSegmentCarriesTheBottomInset`).
4. **A long report whose pages swap to disk:** the paintable is serialized and comes back with a bare `BaseElement`, and the inset survives. Pinned in Task 4 (`theInsetSurvivesAPageSwap`).
5. **A region moved after printing,** by section vertical alignment or header/footer alignment (`StyleCore:1434`, `:766-818`): the card and its cells move together. Pinned in Task 4 (`theLocationIsTheCardOrigin`).

## How to run tests

`-q` hides the summary on success, so read the surefire report after each run:

```bash
cd /e/StyleBI/stylebi-enterprise/community
./mvnw -q test -pl core -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"' core/target/surefire-reports/TEST-<fully.qualified.ClassName>.xml
```

`-Dtest` takes a comma-separated list or a pattern, for example `-Dtest='PrintTableCard*Test'`.

Two failure signatures:
- **Stale `target/`:** a `NoSuchMethodError` from a class you did not touch means `target/` is stale from another branch. Add `clean` and rerun.
- **Missing tag:** a test class with no surefire report means it is missing `@Tag("core")`.

## File Structure

| File | Responsibility |
|---|---|
| `core/.../report/internal/TableElementDef.java` | the `cardInset` field; the grid area; the B reservation in `layout()` and `fitNext` |
| `core/.../report/internal/TablePaintable.java` | its own `cardInset`; the grid box; card geometry and location; the card frame and background; the three outer-cell border suppressions; `isContentMatchTableWidth` |
| `core/.../uql/viewsheet/internal/VsToReportConverter.java` | the inset lookup; column widths, the fit-page decision and the tabs height against the grid; the card-top box and title |
| `core/.../report/io/viewsheet/pdf/PDFVSExporter.java` | `createReportConverter`, which hands the converter its resolver |
| `core/src/test/.../report/internal/PrintTableFixture.java` | test-only: prints one table in a section band, as the converter builds it |
| `core/src/test/.../uql/viewsheet/internal/PrintLayoutConverterFixture.java` | test-only: one table assembly fed to the converter's private table methods |

`core/...` is `core/src/main/java/inetsoft`, and `core/src/test/...` is `core/src/test/java/inetsoft`.

The zero-inset numbers the tests assert were measured in a throwaway harness spike on 2026-09-28, at `a466aa43b`, with no C2 code. Each inset number is derived from them in its test's comment. What the spike measured:
- US Letter with 0.5in margins puts the band at (36, 36). A table at band (20, 10) has its box at (57, 46), and its first cell at x = 58.
- Fit Page Width scales three columns to 398 in a 400 card: the right cell border and its 1pt gap come off.
- A header plus 34 rows of 20pt is one 700pt region in the 710pt left below the band's top, while 40 rows split 34 / 6.
- Four 150pt columns under Fit Contents stack as two segments, at y = 46 and y = 266.
- Borderless columns under Fit Page Width come out 133.33, 133.33 and 132.33, the last reduced by `refreshLastCol`.
- The zero-inset bounds are (57, 46, 398, 219), and `getBounds2` is (57, 46, 398, 220).
- A `TablePaintable` survives Java serialization with its bounds intact.
- `addTable`, called by reflection on a converter built with nulls, gives the title (20, 10, 400, titleH) and the table element (20, 10 + titleH − 1, 400, 250 − titleH). The cached lens widths are 100, 100, 200.

---

### Task 0: Extend the print fixture and recapture the print baselines (manual, before any code)

The user does this in the running app. It is the legacy reference for print layout.
- C1 left print output unchanged (its MC-1), so a capture from this branch head is still pre-slice-C output.
- It cannot be recreated once Task 1 lands.

**Files:**
- Create in `community/.superpowers/baselines/density-padding-export/` (git-excluded through `.git/modules/community/info/exclude`, which lists `.superpowers/`):
  - `c2-legacy-print.pdf`
  - `c2-modern-print.pdf`
  - `dpx-fixture-c2.zip`
- Modify: `community/.superpowers/baselines/density-padding-export/README.txt`

- [ ] **Step 1: Build and start the server from this branch as it stands**

```bash
cd /e/StyleBI/stylebi-enterprise/community
git branch --show-current     # feature-density-padding-print-layout
git log --oneline -4          # this plan's commit, the two spec commits, then 4f742abe8
```

Build and start the server the usual way.
- If `community/server/target/server/config/fonts/` did not exist before this start, export any PDF once, then restart the server. Java2D only registers Roboto at JVM start.
- Keep `viewsheet.density` = `comfortable` and `viewsheet.modernVisualization` = `true`, as for the C1 baselines.

- [ ] **Step 2: Give the bottom-tabs table a fixed four rows**

The print checks must not rely on a table condition, because print layout can drop a conditioned table's rows (spec §10, F5).
1. Open the `Examples/Order Details` worksheet.
2. Add an embedded table named `Four Rows` with the columns `Cal QTR`, `Cal QTR_1_1` and `Order ID`, and four rows: `Q2-LY, Q2-LY, 2102995`, `Q1-LY, Q1-LY, 2103029`, `Q3-TY, Q3-TY, 2103008` and `Q3-LY, Q3-LY, 2103034`.
3. Save.

- [ ] **Step 3: Edit both print copies, `DPX Modern Print` and `DPX Legacy Print`**

In each:
1. **Take the conditions off.** Remove TableView4's conditions (Order ID). Rebind TableView9 to `Four Rows` and remove its condition.
2. **Add three tables to the print layout.** Open the print layout (Layouts), then drag onto the page, below Tab1, on new space:
   - `Chart1`, at 400 × 240;
   - `TableView5`, the title-hidden table, at 400 × 250;
   - `TableView3`, the eight-column table, at **300** × 250, which is narrower than its eight columns.
3. **Set TableView3's flow.** Right-click TableView3 → Table Flow Control → **Fit Contents**. Every other table keeps Fit Page Width.
4. Save.

- [ ] **Step 4: Export**

- From the viewer, export PDF for `DPX Legacy Print` into the baselines folder as `c2-legacy-print.pdf`, and for `DPX Modern Print` as `c2-modern-print.pdf`. A PDF export uses the print layout automatically.
- Export the same assets `dpx-fixture.zip` holds (the four DPX viewsheets, the Order Details worksheet and the default table style), the same way it was made, as `dpx-fixture-c2.zip` in the same folder. The compact and dense checks in Task 7 re-import it.

- [ ] **Step 5: Check the captures**

Open both PDFs and confirm:
- TableView4 ("TableShrink") prints its rows, since it no longer has a condition;
- the bottom-tabs table prints its four rows flush with the tab strip;
- TableView3 wraps into stacked column segments under one card;
- Chart1 and TableView5 are present.

A table with no rows, or a missing assembly, means Steps 2–3 did not take. Redo them before continuing.

- [ ] **Step 6: Record the capture in `README.txt`**

Append:

```text
C2 print baselines (Task 0 of the C2 plan)
------------------------------------------
Date:    <date>, from feature-density-padding-print-layout @ <git log -1 --format=%h>, no C2 code
Files:   c2-legacy-print.pdf, c2-modern-print.pdf, dpx-fixture-c2.zip
Changes to both print copies against the C1 fixture:
  - TableView4 has no conditions; TableView9 is bound to the embedded worksheet table
    "Four Rows" and has no condition (F5: print layout can drop a conditioned table's rows)
  - the print layout adds Chart1 (400x240), TableView5 (title hidden, 400x250) and
    TableView3 (8 columns in a 300x250 box, Table Flow Control = Fit Contents)
Pages:   legacy <n>, modern <n>
```

---

### Task 1: Lay the columns out in the grid

**Files:**
- Modify: `core/src/main/java/inetsoft/report/internal/TableElementDef.java`:
  - the field beside `borders` (`:2675`);
  - the accessors after `getBorders()` (`:2660`);
  - `calcRemainingArea` (`:1021`);
  - the next-page area (`:1569-1571`).
- Modify: `core/src/main/java/inetsoft/report/internal/TablePaintable.java`:
  - the field beside `padding` (`:4607`);
  - the constructor (`:105`);
  - `init` (`:656`).
- Create: `core/src/test/java/inetsoft/report/internal/PrintTableFixture.java`
- Test: `core/src/test/java/inetsoft/report/internal/PrintTableCardGridTest.java`

**Interfaces:**
- Produces:
  - `void TableElementDef.setCardInset(Insets inset)`: stores a copy, or null when the left, bottom and right edges are all 0; the top edge is ignored.
  - `Insets TableElementDef.getCardInset()`: returns a copy, or null.
  - `private Insets TablePaintable.cardInset` (non-transient) and `private int TablePaintable.getCardLeft()`.
  - `PrintTableFixture` with `rows(int)`, `widths(int...)`, `layout(int)`, `noCellBorders()`, `background(Color)`, `inset(int left, int bottom, int right)`, `textBelow()`, `element()`, `print()`, `regions()`, `static paintables(StylePage, Class)` and `static render(StylePage)`.

- [ ] **Step 1: Write the fixture**

```java
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.uql.viewsheet.BorderColors;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;

/**
 * Prints one table the way print layout's converter lays it out: a TableElementDef in a section
 * band, on a US Letter page with 0.5in margins. The band starts at (36, 36), so a table at band
 * (20, 10) has its card at x = 56, y = 46, and its box, where the cells start, at x = 57 + L.
 */
final class PrintTableFixture {
   PrintTableFixture rows(int rows) {
      this.rows = rows;
      return this;
   }

   PrintTableFixture widths(int... widths) {
      this.widths = widths;
      return this;
   }

   PrintTableFixture layout(int layout) {
      this.layout = layout;
      return this;
   }

   PrintTableFixture noCellBorders() {
      this.cellBorders = false;
      return this;
   }

   PrintTableFixture background(Color background) {
      this.background = background;
      return this;
   }

   PrintTableFixture inset(int left, int bottom, int right) {
      this.inset = new Insets(0, left, bottom, right);
      return this;
   }

   /** Put a one-line text element in the band at y = 120, below the table's 100pt design box. */
   PrintTableFixture textBelow() {
      this.textBelow = true;
      return this;
   }

   /** The table element in its band, before anything prints. */
   TableElementDef element() {
      if(element == null) {
         build();
      }

      return element;
   }

   /** Every page the report engine produces. */
   List<StylePage> print() {
      element();
      List<StylePage> pages = new ArrayList<>();
      Enumeration<?> generated = ReportGenerator.generate(report);

      while(generated.hasMoreElements()) {
         pages.add((StylePage) generated.nextElement());
      }

      return pages;
   }

   /** Every table region, in print order. */
   List<TablePaintable> regions() {
      List<TablePaintable> regions = new ArrayList<>();

      for(StylePage page : print()) {
         regions.addAll(paintables(page, TablePaintable.class));
      }

      return regions;
   }

   static <T extends Paintable> List<T> paintables(StylePage page, Class<T> type) {
      List<T> found = new ArrayList<>();

      for(int i = 0; i < page.getPaintableCount(); i++) {
         if(type.isInstance(page.getPaintable(i))) {
            found.add(type.cast(page.getPaintable(i)));
         }
      }

      return found;
   }

   /** The page painted on white at one pixel per point. */
   static BufferedImage render(StylePage page) {
      Dimension size = page.getPageDimension();
      BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, size.width, size.height);
      page.print(g);
      g.dispose();
      return image;
   }

   private void build() {
      report = new TabularSheet(null, null);
      report.setPageSize(new Size(8.5, 11));
      report.setMargin(new Margin(0.5, 0.5, 0.5, 0.5));

      // the section shape VsToReportConverter.createReportSection builds
      SectionElementDef section = new SectionElementDef(report);
      section.setSpacing(-1);
      section.getSection().getSectionHeader()[0].setVisible(false);
      section.getSection().getSectionFooter()[0].setVisible(false);
      SectionBand band = section.getSection().getSectionContent()[0];
      band.setHeight(300 / 72f);

      Object[][] data = new Object[rows + 1][widths.length];

      for(int c = 0; c < widths.length; c++) {
         data[0][c] = "H" + c;

         for(int r = 1; r <= rows; r++) {
            data[r][c] = "r" + r + "c" + c;
         }
      }

      DefaultTableLens lens = new DefaultTableLens(data);
      lens.setHeaderRowCount(1);

      if(!cellBorders) {
         lens.setRowBorder(StyleConstants.NO_BORDER);
         lens.setColBorder(StyleConstants.NO_BORDER);
      }

      element = new TableElementDef(report, lens);
      element.setEmbedWidth(true);
      element.setLayout(layout);
      element.setFixedWidths(widths);
      int[] heights = new int[rows + 1];
      Arrays.fill(heights, ROW_H);
      element.setFixedHeights(heights);
      element.setBorders(new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                                    StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
      element.setBorderColors(new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED));

      if(background != null) {
         element.setBackground(background);
      }

      element.setCardInset(inset);
      band.addElement(element, new Rectangle(20, 10, 400, 100));

      if(textBelow) {
         band.addElement(new TextElementDef(report, new DefaultTextLens("below")),
                         new Rectangle(20, 120, 400, 20));
      }

      report.addElement(0, 0, section);
   }

   static final int ROW_H = 20;
   private int rows = 10;
   private int[] widths = { 100, 100, 100 };
   private int layout = ReportSheet.TABLE_FIT_PAGE;
   private boolean cellBorders = true;
   private Color background;
   private Insets inset;
   private boolean textBelow;
   private TabularSheet report;
   private TableElementDef element;
}
```

- [ ] **Step 2: Write the failing tests**

```java
package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A print-layout table with a card inset lays its columns out in the grid, the card less its
 * side insets, on every page.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardGridTest {
   @Test
   void theCellsStartInsideTheLeftInset() {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);

      // the box at 57 + 16, plus the first column's 1pt left cell border
      assertEquals(74, region.getPrintBounds(1, 0, false).x, 0.01);
   }

   @Test
   void withoutAnInsetTheCellsStartAtTheCardEdge() {
      TablePaintable region = new PrintTableFixture().regions().get(0);

      assertEquals(58, region.getPrintBounds(1, 0, false).x, 0.01);
   }

   @Test
   void fitPageWidthScalesTheColumnsToTheGrid() {
      // the 368 grid, less the right cell border and its 1pt gap
      assertEquals(366, totalWidth(new PrintTableFixture().inset(16, 16, 16).regions().get(0)),
                   0.01);
   }

   @Test
   void withoutAnInsetFitPageWidthScalesTheColumnsToTheCard() {
      assertEquals(398, totalWidth(new PrintTableFixture().regions().get(0)), 0.01);
   }

   @Test
   void fitContentsCutsTheColumnsAtTheGridWidth() {
      // 390 fits the 400 card but not the 368 grid
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertEquals(List.of(2, 1), regions.stream().map(r -> r.getTableRegion().width).toList());
   }

   @Test
   void withoutAnInsetFitContentsKeepsOneSegment() {
      List<TablePaintable> regions = new PrintTableFixture()
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertEquals(List.of(3), regions.stream().map(r -> r.getTableRegion().width).toList());
   }

   @Test
   void aLaterPageCutsAtTheGridWidthToo() {
      // 60 rows reach a third page, whose area comes from the next-page frame, not
      // calcRemainingArea
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16).rows(60)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertTrue(regions.stream().anyMatch(r -> r.getTableRegion().y > 34),
                 "the table reaches rows past the first page");
      assertTrue(regions.stream().allMatch(r -> r.getTableRegion().width <= 2),
                 "no segment is wider than the grid");
   }

   @Test
   @Timeout(60)
   void aCardNarrowerThanItsInsetStillPrints() {
      // 250 + 250 of side inset leaves the 400 card no grid at all
      assertFalse(new PrintTableFixture().inset(250, 16, 250).regions().isEmpty());
   }

   private static float totalWidth(TablePaintable region) {
      float total = 0;

      for(int c = 0; c < 3; c++) {
         total += region.getColWidth(c);
      }

      return total;
   }
}
```

- [ ] **Step 3: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardGridTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, "cannot find symbol: method setCardInset(java.awt.Insets)".

- [ ] **Step 4: Add the field and accessors to `TableElementDef`**

After `getBorders()`:

```java
   /**
    * Set the inset between a print-layout table's card edge and its grid. Only the left, bottom
    * and right edges are used; the card's top band lives outside the table element.
    * @param inset the inset, or null for none.
    */
   public void setCardInset(Insets inset) {
      boolean none = inset == null ||
         inset.left == 0 && inset.bottom == 0 && inset.right == 0;
      this.cardInset = none ? null : (Insets) inset.clone();
   }

   /**
    * Get the print-layout card inset.
    * @return a copy of the inset, or null for none.
    */
   public Insets getCardInset() {
      return cardInset == null ? null : (Insets) cardInset.clone();
   }
```

After `private Insets borders = null;`:

```java
   // print layout only: the card inset (0, left, bottom, right) around the grid
   private Insets cardInset = null;
```

- [ ] **Step 5: Inset both layout areas**

In `calcRemainingArea`, replace the tail:

```java
      area.x += indw;
      area.width -= indw;

      return area;
   }
```

with:

```java
      area.x += indw;
      area.width -= indw;
      insetToGrid(area);

      return area;
   }

   // a print-layout card keeps its columns inside the side insets
   private void insetToGrid(Rectangle area) {
      if(cardInset != null) {
         area.x += cardInset.left;
         area.width = Math.max(0, area.width - cardInset.left - cardInset.right);
      }
   }
```

In `layout()`, the next-page area at `:1569-1571`:

```java
         // indent
         nextarea.x += indw;
         nextarea.width -= indw;
```

becomes:

```java
         // indent
         nextarea.x += indw;
         nextarea.width -= indw;
         insetToGrid(nextarea);
```

- [ ] **Step 6: Move `TablePaintable`'s box to the grid**

After `private Insets padding;`:

```java
   // print layout's card inset (0, left, bottom, right); the box stays the grid
   private Insets cardInset;
```

In the constructor, directly after `TableElementDef telem = (TableElementDef) elem;` (it must run before `refreshLastCol()` and `init()` at the constructor's end):

```java
      this.cardInset = telem.getCardInset();
```

In `init()`, `box.x = printBox.x + (float) elem.getIndent() * 72 + 1;` becomes:

```java
      box.x = printBox.x + (float) elem.getIndent() * 72 + 1 + getCardLeft();
```

Add, after `getHeight()`:

```java
   private int getCardLeft() {
      return cardInset == null ? 0 : cardInset.left;
   }
```

- [ ] **Step 7: Run the tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardGridTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `tests="8"`, `failures="0"`, `errors="0"`.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
```
Expect `feature-density-padding-print-layout`.

```bash
git add core/src/main/java/inetsoft/report/internal/TableElementDef.java core/src/main/java/inetsoft/report/internal/TablePaintable.java core/src/test/java/inetsoft/report/internal/PrintTableFixture.java core/src/test/java/inetsoft/report/internal/PrintTableCardGridTest.java
```

```bash
git commit -F - <<'EOF'
Lay a print-layout table's columns out inside its card inset

A table element can now carry a card inset, and its columns are laid out in the grid, the card less its side insets, on the first page and on every later one. The painter's box starts at the grid, so no cell arithmetic moves. Without an inset every path is unchanged, which keeps classic reports as they are.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 2: Keep the bottom inset with the last row

**Files:**
- Modify: `core/src/main/java/inetsoft/report/internal/TableElementDef.java`:
  - the two row-fit loops in `layout()` (`:1633` and `:1652`, each `h += rowHeight[i];`);
  - `fitNext` (`:854`).
- Test: `core/src/test/java/inetsoft/report/internal/PrintTableCardBottomTest.java`

**Interfaces:**
- Consumes: `TableElementDef.setCardInset` and `PrintTableFixture` (Task 1).
- Produces: `private int TableElementDef.getLastRowInset(int row)`.

- [ ] **Step 1: Write the failing tests**

```java
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A card's bottom inset travels with the table's last row: a last row that fits without it,
 * but not with it, moves to the next page with its band.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardBottomTest {
   @Test
   void aLastRowThatFitsOnlyWithoutTheInsetMovesToTheNextPage() {
      // a header and 34 rows fill 700 of the 710 below the band's top; 716 does not fit
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16).rows(34).regions();

      assertEquals(List.of(33, 1), heights(regions));
   }

   @Test
   void withoutAnInsetTheLastRowStaysOnThePage() {
      assertEquals(List.of(34), heights(new PrintTableFixture().rows(34).regions()));
   }

   @Test
   void fitNextCountsTheBottomInsetOnTheLastRegion() {
      TableElementDef table = laidOut(new PrintTableFixture().inset(16, 16, 16).rows(34));

      assertEquals(-1, table.fitNext(700), "700 holds the rows but not the 16pt bottom inset");
      assertEquals(1, table.fitNext(716));
   }

   @Test
   void withoutAnInsetFitNextCountsTheRowsOnly() {
      assertEquals(1, laidOut(new PrintTableFixture().rows(34)).fitNext(700));
   }

   // an 800pt area holds every row and the inset in one region, which is then the last
   private static TableElementDef laidOut(PrintTableFixture fixture) {
      TableElementDef table = fixture.element();
      ReportSheet report = table.getReport();
      report.printBox = new Rectangle(56, 46, 400, 800);
      report.printHead = new Position(0, 0);
      report.frames = new Rectangle[] { report.printBox };
      report.npframes = null;
      report.currFrame = 0;
      table.validate(new StylePage(new Dimension(612, 900)), report, null);
      return table;
   }

   private static List<Integer> heights(List<TablePaintable> regions) {
      return regions.stream().map(r -> r.getTableRegion().height).toList();
   }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardBottomTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `failures="2"`.
- `aLastRowThatFitsOnlyWithoutTheInsetMovesToTheNextPage` fails with "expected: <[33, 1]> but was: <[34]>".
- `fitNextCountsTheBottomInsetOnTheLastRegion` fails with "expected: <-1> but was: <1>".

- [ ] **Step 3: Count B in both row-fit loops**

Both loops in `layout()` start with `h += rowHeight[i];`:
- `for(int i = lu.y; i < reg.y + reg.height; i++)`, under `if(lu.x != headerC)`;
- `for(int i = lu.y; i < rowHeight.length; i++)`, under `else`.

In each, directly after that line, add:

```java
               h += getLastRowInset(i);
```

Add the helper after `insetToGrid`:

```java
   // the card's bottom inset travels with the table's last row
   private int getLastRowInset(int row) {
      return cardInset != null && row == rowHeight.length - 1 ? cardInset.bottom : 0;
   }
```

- [ ] **Step 4: Count B in `fitNext` for the last region**

In `fitNext`, before `return avail + 1 >= h ? 1 : -1;`:

```java
         // the last region carries the card's bottom inset
         if(cardInset != null && currentRegion == regions.size() - 1) {
            h += cardInset.bottom;
         }
```

- [ ] **Step 5: Run the tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest='PrintTableCard*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `PrintTableCardBottomTest` 4 tests and `PrintTableCardGridTest` 8 tests, all passing.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
```
Expect `feature-density-padding-print-layout`.

```bash
git add core/src/main/java/inetsoft/report/internal/TableElementDef.java core/src/test/java/inetsoft/report/internal/PrintTableCardBottomTest.java
```

```bash
git commit -F - <<'EOF'
Keep a print-layout table's bottom inset with its last row

A region that reaches the table's last row now needs room for the card's bottom inset too, both when layout breaks the table into regions and when printing checks the next region's fit. So a last row that fits only without the inset moves to the next page with its band, and the card is never cut off at the page bottom.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 3: Keep an inset edge's outer cell border

In print layout the table border replaces the outer cells' own left, right and bottom borders, so the two lines never double. With an inset on an edge the two lines sit apart, so that edge keeps its cell border. The right edge's case also depends on `isContentMatchTableWidth`, which must compare the columns with the grid.

**Files:**
- Modify: `core/src/main/java/inetsoft/report/internal/TablePaintable.java`:
  - `init` (`:747`, `:760`, `:799`, `:817`);
  - `isContentMatchTableWidth` (`:4507`).
- Test: `core/src/test/java/inetsoft/report/internal/PrintTableCardBorderTest.java`

**Interfaces:**
- Consumes: `TablePaintable.cardInset` and `getCardLeft()` (Task 1), and `PrintTableFixture` (Task 1).
- Produces: `private int TablePaintable.getCardRight()` and `private int TablePaintable.getCardBottom()`. The latter is the bottom inset on the last region, 0 elsewhere.

- [ ] **Step 1: Write the failing left, bottom and content-match tests**

```java
package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.report.StyleConstants;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Print layout hides an outer cell's own border where the table border would double it. An edge
 * with a card inset puts the two apart, so it keeps the cell border.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardBorderTest {
   @Test
   void theLeftAndBottomInsetsKeepTheOuterCellBorders() throws Exception {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);
      int[][] ver = matrix(region, "ver");
      int[][] hor = matrix(region, "hor");

      assertEquals(StyleConstants.THIN_LINE, ver[1][0], "the first column's left border");
      assertEquals(StyleConstants.THIN_LINE, hor[hor.length - 2][1], "the last row's bottom border");
   }

   @Test
   void withoutAnInsetTheTableBorderReplacesTheOuterCellBorders() throws Exception {
      TablePaintable region = new PrintTableFixture().regions().get(0);
      int[][] ver = matrix(region, "ver");
      int[][] hor = matrix(region, "hor");

      assertEquals(StyleConstants.NO_BORDER, ver[1][0]);
      assertEquals(StyleConstants.NO_BORDER, hor[hor.length - 2][1]);
   }

   @Test
   void theColumnsAreMatchedWithTheGridNotTheCard() {
      // borderless columns fill the 368 grid; a match takes 1pt off the last to end with the title
      TablePaintable region =
         new PrintTableFixture().inset(16, 16, 16).noCellBorders().regions().get(0);

      assertEquals(368 / 3f - 1, region.getColWidth(2), 0.01);
   }

   @Test
   void withoutAnInsetTheColumnsAreMatchedWithTheCard() {
      TablePaintable region = new PrintTableFixture().noCellBorders().regions().get(0);

      assertEquals(400 / 3f - 1, region.getColWidth(2), 0.01);
   }

   static int[][] matrix(TablePaintable region, String name) throws Exception {
      Field field = TablePaintable.class.getDeclaredField(name);
      field.setAccessible(true);
      return (int[][]) field.get(region);
   }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardBorderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `failures="2"`.
- `theLeftAndBottomInsetsKeepTheOuterCellBorders` fails with "expected: <4097> but was: <0>" (`THIN_LINE` is 4097).
- `theColumnsAreMatchedWithTheGridNotTheCard` fails with "expected: <121.666…> but was: <122.666…>".

- [ ] **Step 3: Add the edge helpers**

After `getCardLeft()`:

```java
   private int getCardRight() {
      return cardInset == null ? 0 : cardInset.right;
   }

   // the bottom inset belongs to the last region only
   private int getCardBottom() {
      return cardInset == null || !lastregion ? 0 : cardInset.bottom;
   }
```

- [ ] **Step 4: Skip the left and bottom suppressions on an inset edge**

In `init()`, both left-edge lines read `(j == 0 && tableBorders.left != 0) ?`:
- `ver[i + n][j + m] = …`, in the span branch;
- `ver[i][j] = …`, in the plain branch.

In each, change the condition to:

```java
(j == 0 && tableBorders.left != 0 && getCardLeft() == 0) ?
```

The bottom edge, `if(lastregion && tableBorders.bottom != 0) {`, becomes:

```java
            if(lastregion && tableBorders.bottom != 0 && getCardBottom() == 0) {
```

- [ ] **Step 5: Match the columns against the grid**

In `isContentMatchTableWidth`, `float pw = printb.width;` becomes:

```java
      float pw = printb.width - getCardLeft() - getCardRight();
```

- [ ] **Step 6: Run the tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardBorderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `tests="4"`, `failures="0"`, `errors="0"`.

- [ ] **Step 7: Write the failing right-edge tests**

Add to `PrintTableCardBorderTest`:

```java
   @Test
   void theRightInsetKeepsTheLastColumnsBorderWhenTheColumnsFillTheGrid() throws Exception {
      // 368 of columns fills the 368 grid; refreshLastCol takes 1pt off the last, and init's
      // recheck still matches, which is the case that hides the right cell border
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(122, 123, 123).regions().get(0);
      int[][] ver = matrix(region, "ver");

      assertEquals(StyleConstants.THIN_LINE, ver[1][ver[1].length - 2]);
   }

   @Test
   void withoutAnInsetColumnsFillingTheCardHideTheRightCellBorder() throws Exception {
      // 400 of columns fills the 400 card, and still matches after refreshLastCol's 1pt
      TablePaintable region = new PrintTableFixture()
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(133, 133, 134).regions().get(0);
      int[][] ver = matrix(region, "ver");

      assertEquals(StyleConstants.NO_BORDER, ver[1][ver[1].length - 2]);
   }
```

- [ ] **Step 8: Run them and watch the inset one fail**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardBorderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `failures="1"`. `theRightInsetKeepsTheLastColumnsBorderWhenTheColumnsFillTheGrid` fails with "expected: <4097> but was: <0>": since Step 5 the columns match the grid, so the suppression fires.

The match is checked twice, which is why the widths add up to exactly the width they fill:
1. `refreshLastCol()`, in the constructor, takes 1pt off the last column when the columns match.
2. `init()` then checks again with the trimmed column. `borderw` is at least 1, which absorbs the trim only when the columns filled the width before it.

- [ ] **Step 9: Skip the right suppression on an inset edge**

In `init()`, `if(hideLeftBorder && tableBorders.right != 0) {` becomes:

```java
         if(hideLeftBorder && tableBorders.right != 0 && getCardRight() == 0) {
```

- [ ] **Step 10: Run the tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest='PrintTableCard*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `PrintTableCardBorderTest` 6, `PrintTableCardBottomTest` 4 and `PrintTableCardGridTest` 8, all passing.

- [ ] **Step 11: Commit**

```bash
git branch --show-current
```
Expect `feature-density-padding-print-layout`.

```bash
git add core/src/main/java/inetsoft/report/internal/TablePaintable.java core/src/test/java/inetsoft/report/internal/PrintTableCardBorderTest.java
```

```bash
git commit -F - <<'EOF'
Keep an outer cell's border where a print-layout card inset parts it from the table border

Print layout hides the outer cells' own left, right and bottom borders, so they do not double the table border. With an inset on an edge the two lines sit apart, so that edge now keeps the cell border. Whether the columns fill the table is now judged against the grid, which also keeps the last column ending with the title.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 4: Report and paint the card

**Files:**
- Modify: `core/src/main/java/inetsoft/report/internal/TablePaintable.java`:
  - `getHeight` (`:846`), `getBounds` (`:1880`), `getBounds2` (`:1902`), `setLocation` (`:1913`) and `getLocation` (`:1931`);
  - `paintBorder` (`:1412`);
  - `paint`, before `// print contents` (`:957`).
- Test: `core/src/test/java/inetsoft/report/internal/PrintTableCardGeometryTest.java`

**Interfaces:**
- Consumes: `cardInset`, `getCardLeft()`, `getCardRight()` and `getCardBottom()` (Tasks 1 and 3), and `PrintTableFixture` (Task 1).
- Produces:
  - card-based `getBounds()`, `getBounds2()`, `getHeight()`, `getLocation()` and `setLocation(Point)`;
  - `private float[] TablePaintable.getCardFrame(Insets borders)`, which returns `{x0, y0, x1, y1}` along the border lines, or null.

- [ ] **Step 1: Write the failing geometry tests**

```java
package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.report.StylePage;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A print-layout table region reports and paints its card: the grid box grown by the side
 * insets, and by the bottom inset on the last region only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardGeometryTest {
   @Test
   void theBoundsDescribeTheCard() {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);

      // the box at (73, 46), 366 x 220, grown by 16 left and right and by 16 below
      assertEquals(new Rectangle(57, 46, 398, 236), region.getBounds());
      assertEquals(new Rectangle(57, 46, 398, 236), region.getBounds2());
      assertEquals(236, region.getHeight(), 0.01);
   }

   @Test
   void withoutAnInsetTheBoundsAreTodays() {
      TablePaintable region = new PrintTableFixture().regions().get(0);

      assertEquals(new Rectangle(57, 46, 398, 219), region.getBounds());
      assertEquals(new Rectangle(57, 46, 398, 220), region.getBounds2());
      assertEquals(220, region.getHeight(), 0.01);
   }

   @Test
   void onlyTheLastRegionCarriesTheBottomInset() {
      // 40 rows print 34 on the first page and 6 on the second
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16).rows(40).regions();

      assertEquals(700, regions.get(0).getHeight(), 0.01);
      assertEquals(156, regions.get(1).getHeight(), 0.01);
   }

   @Test
   void withoutAnInsetEveryRegionIsItsRows() {
      List<TablePaintable> regions = new PrintTableFixture().rows(40).regions();

      assertEquals(700, regions.get(0).getHeight(), 0.01);
      assertEquals(140, regions.get(1).getHeight(), 0.01);
   }

   @Test
   void onlyTheLastFitContentsSegmentCarriesTheBottomInset() {
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16)
         .layout(ReportSheet.TABLE_FIT_CONTENT).widths(130, 130, 130).regions();

      assertEquals(220, regions.get(0).getHeight(), 0.01);
      assertEquals(236, regions.get(1).getHeight(), 0.01);
   }

   @Test
   void theLocationIsTheCardOrigin() {
      TablePaintable region = new PrintTableFixture().inset(16, 16, 16).regions().get(0);
      Rectangle bounds = region.getBounds();

      assertEquals(new Point(57, 46), region.getLocation());

      region.setLocation(region.getLocation());
      assertEquals(bounds, region.getBounds(), "moving a region to where it is moves nothing");

      region.setLocation(new Point(57, 146));
      assertEquals(new Rectangle(57, 146, 398, 236), region.getBounds());
      assertEquals(74, region.getPrintBounds(1, 0, false).x, 0.01, "the cells moved with it");
   }

   @Test
   void theElementBelowIsPushedDownByTheCardsGrowth() {
      StylePage inset = new PrintTableFixture().inset(16, 16, 16).textBelow().print().get(0);
      StylePage plain = new PrintTableFixture().textBelow().print().get(0);
      int growth = table(inset).getBounds().height - table(plain).getBounds().height;

      assertEquals(17, growth, "B, plus the last row's own bottom border, which the inset keeps");
      assertEquals(text(plain).getBounds().y + growth, text(inset).getBounds().y);
   }

   private static TablePaintable table(StylePage page) {
      return PrintTableFixture.paintables(page, TablePaintable.class).get(0);
   }

   private static TextPaintable text(StylePage page) {
      return PrintTableFixture.paintables(page, TextPaintable.class).get(0);
   }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardGeometryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `failures="5"`:
- `theBoundsDescribeTheCard`, which gets `x=73,y=46,width=366,height=220`;
- `onlyTheLastRegionCarriesTheBottomInset`;
- `onlyTheLastFitContentsSegmentCarriesTheBottomInset`;
- `theLocationIsTheCardOrigin`;
- `theElementBelowIsPushedDownByTheCardsGrowth`.

- [ ] **Step 3: Report the card**

`getHeight()`'s body, `return height;`, becomes:

```java
      return height + getCardBottom();
```

In `getBounds()`, wrap the returned rectangle:

```java
      return toCard(new Rectangle(Math.round(box.x), Math.round(box.y),
         (int) Math.ceil(box.width + rb - 1),
         (int) Math.ceil(box.height + bb - 1)));
```

In `getBounds2()`:

```java
      return toCard(new Rectangle((int) box.x, (int) box.y, (int) box.width,
                                  (int) box.height));
```

In `setLocation(Point loc)`, `box.setLocation(loc);` becomes:

```java
      box.setLocation(new Point(loc.x + getCardLeft(), loc.y));
```

`getLocation()`'s body, `return box.getLocation();`, becomes:

```java
      Point loc = box.getLocation();
      loc.x -= getCardLeft();
      return loc;
```

Add after `getCardBottom()`:

```java
   // the grid bounds grown to the card
   private Rectangle toCard(Rectangle grid) {
      if(cardInset != null) {
         grid.x -= cardInset.left;
         grid.width += cardInset.left + cardInset.right;
         grid.height += getCardBottom();
      }

      return grid;
   }
```

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardGeometryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `tests="7"`, `failures="0"`, `errors="0"`.

- [ ] **Step 5: Write the failing background tests**

Add to `PrintTableCardGeometryTest`:

```java
   @Test
   void theBackgroundCoversTheInsetBands() {
      BufferedImage page = PrintTableFixture.render(
         new PrintTableFixture().inset(16, 16, 16).background(Color.YELLOW).print().get(0));

      // the card spans 56..456 across and 46..282 down; the grid runs 73..439 and 46..266
      assertEquals(Color.YELLOW.getRGB(), page.getRGB(64, 100), "left band");
      assertEquals(Color.YELLOW.getRGB(), page.getRGB(448, 100), "right band");
      assertEquals(Color.YELLOW.getRGB(), page.getRGB(200, 274), "bottom band");
   }

   @Test
   void withoutAnInsetNothingIsPaintedBelowTheRows() {
      BufferedImage page = PrintTableFixture.render(
         new PrintTableFixture().background(Color.YELLOW).print().get(0));

      assertEquals(Color.WHITE.getRGB(), page.getRGB(200, 274));
   }
```

- [ ] **Step 6: Run them and watch the inset one fail**

Run: `./mvnw -q test -pl core -Dtest=PrintTableCardGeometryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `failures="1"`. `theBackgroundCoversTheInsetBands` fails on "left band" with "expected: <-256> but was: <-1>".

- [ ] **Step 7: Share `paintBorder`'s frame, and paint the card background**

Replace `paintBorder` with a frame helper and a border painter that draw exactly what it draws today:

```java
   /**
    * Paint table's outer borders
    * Because report table have no outer border for the whole table,
    * so this is only used in printlayout mode.
    */
   private void paintBorder(Graphics g) {
      if(!(elem instanceof TableElementDef table) || table.getBorders() == null) {
         return;
      }

      Insets borders = table.getBorders();
      float[] frame = getCardFrame(borders);

      if(frame == null) {
         return;
      }

      BorderColors bcolors = table.getBorderColors();
      float x0 = frame[0];
      float y0 = frame[1];
      float x1 = frame[2];
      float y1 = frame[3];

      // left
      if(borders.left != 0) {
         g.setColor(bcolors.leftColor);
         Common.drawLine(g, x0, y0, x0, y1, borders.left);
      }

      // right
      if(borders.right != 0) {
         g.setColor(bcolors.rightColor);
         Common.drawLine(g, x1, y0, x1, y1, borders.right);
      }

      // bottom
      if(lastregion) {
         if(borders.bottom != 0) {
            g.setColor(bcolors.bottomColor);
            Common.drawLine(g, x0, y1, x1, y1, borders.bottom);
         }
      }
   }

   /**
    * The table's frame on this region in print layout, {x0, y0, x1, y1} along the border lines,
    * or null when the region is not in a print-layout band or lies past the page.
    */
   private float[] getCardFrame(Insets borders) {
      Rectangle printband = getPrintBandBounds();

      if(printband == null) {
         return null;
      }

      float linew_l = Common.getLineWidth(borders.left);
      float linew_r = Common.getLineWidth(borders.right);
      float thinlW = Common.getLineWidth(StyleConstants.THIN_LINE);

      Rectangle bounds = getBounds();
      float reg_x = bounds.x; // runtime x of table region.
      float reg_y = bounds.y; // runtime y of table region.
      float reg_w = printband.width; // pixel width setted in vs printlayout.
      float reg_h = bounds.height;   // height of the expanded table region.
      float offsetX = 1; // when alignment is left, table content start at 1.

      Size pageSize = ((BaseElement) elem).getReport().getPageSize();
      Margin margin = ((BaseElement) elem).getReport().getMargin();
      int pwidth = (int) ((pageSize.width - margin.right) * 72.0);

      if(reg_x >= pwidth) {
         return null;
      }

      if(reg_x + reg_w > pwidth) {
         reg_w = pwidth - reg_x + offsetX;
      }

      int tableadv = 0;
      // 1. considering the border width, to make sure table border is align
      // with the title border.
      // 2. double line is drawed differently with others lines.
      float lOffset = borders.left == StyleConstants.DOUBLE_LINE ?
          (offsetX - ((linew_l - 1) + thinlW / 2)) : (offsetX - linew_l / 2);
      float rOffset = borders.right == StyleConstants.DOUBLE_LINE ?
         ((linew_l - 1) + thinlW / 2) + thinlW : (linew_r + linew_l) / 2;
      float x0 = reg_x - lOffset;
      float y0 = reg_y;
      float x1 = x0 + reg_w - rOffset;
      float y1 = reg_y + reg_h;
      // if not last region, should add table advance to make sure the outer
      // borders of each region have no gap.
      y1 = lastregion ? y1 : y1 + tableadv;

      return new float[] { x0, y0, x1, y1 };
   }

   // a print-layout card's background also fills its inset bands
   private void paintCardBackground(Graphics g) {
      Color bg = elem.getBackground();
      Insets borders = elem instanceof TableElementDef table ? table.getBorders() : null;
      float[] frame = getCardFrame(borders == null ? new Insets(0, 0, 0, 0) : borders);

      if(bg == null || frame == null) {
         return;
      }

      Color oc = g.getColor();
      g.setColor(bg);
      Common.fillRect(g, frame[0], frame[1], frame[2] - frame[0], frame[3] - frame[1]);
      g.setColor(oc);
   }
```

In `paint`, directly before the `// print contents` comment:

```java
      if(cardInset != null) {
         paintCardBackground(g);
      }
```

`getCardFrame` is the old `paintBorder` arithmetic moved verbatim, so a zero-inset table's border does not move.

- [ ] **Step 8: Write the page-swap test**

Add to `PrintTableCardGeometryTest`:

```java
   @Test
   void theInsetSurvivesAPageSwap() throws Exception {
      // a swapped page restores its element as a bare BaseElement, so the inset must be the
      // paintable's own
      TablePaintable region =
         new PrintTableFixture().inset(16, 16, 16).noCellBorders().regions().get(0);
      TablePaintable back = roundTrip(region);

      assertEquals(new Rectangle(57, 46, 398, 235), region.getBounds());
      assertEquals(region.getBounds(), back.getBounds());
      assertEquals(new Point(57, 46), back.getLocation());
   }

   private static TablePaintable roundTrip(TablePaintable region) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(region);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (TablePaintable) in.readObject();
      }
   }
```

It passes on its first run, because Task 1 made the field non-transient. To see that it guards the field:
1. mark `private Insets cardInset;` as `transient`;
2. run `PrintTableCardGeometryTest`; `theInsetSurvivesAPageSwap` fails with "expected: <…x=57…> but was: <…x=73…>";
3. remove `transient` again.

- [ ] **Step 9: Run every engine test and see them pass**

Run: `./mvnw -q test -pl core -Dtest='PrintTableCard*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `PrintTableCardGeometryTest` 10, `PrintTableCardBorderTest` 6, `PrintTableCardBottomTest` 4 and `PrintTableCardGridTest` 8, all passing.

- [ ] **Step 10: Commit**

```bash
git branch --show-current
```
Expect `feature-density-padding-print-layout`.

```bash
git add core/src/main/java/inetsoft/report/internal/TablePaintable.java core/src/test/java/inetsoft/report/internal/PrintTableCardGeometryTest.java
```

```bash
git commit -F - <<'EOF'
Report and paint a print-layout table region as its card

A region's bounds, height and location now describe the card: the grid grown by the side insets, and by the bottom inset on the last region. So the elements below are pushed down past the card, and a region moved by alignment keeps its cells with it. The card background also fills the inset bands, through the same frame the border is drawn on, and the inset survives a page swap. Without an inset every value is today's.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 5: Read the inset into the converter, and size the columns to the grid

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java`:
  - the imports;
  - a field and setter after the constructor (`:80-87`);
  - `addTable` (`:1104`, fit-page `:1144-1149`);
  - `calculateColumnWidths` (`:1246`);
  - `computePrintLayoutTableHeight` (`:1036`).
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFVSExporter.java` (`:112`)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/PrintLayoutConverterFixture.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/PrintLayoutCardArithmeticTest.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/pdf/PDFPrintLayoutCardInsetTest.java`

**Interfaces:**
- Consumes: `VSExporter.getTableCardInset(TableDataVSAssemblyInfo)` (C1), and `VSTableLens.getColumnWidthInGrid(int col, TableDataVSAssemblyInfo info, int insetW)` (C1, `VSTableLens:346`).
- Produces:
  - `public void VsToReportConverter.setTableCardInsets(Function<TableDataVSAssemblyInfo, Insets> resolver)`;
  - `private Insets VsToReportConverter.getCardInset(TableDataVSAssemblyInfo info)`, which is never null;
  - `protected VsToReportConverter PDFVSExporter.createReportConverter(ViewsheetSandbox box)`;
  - `PrintLayoutConverterFixture`.

- [ ] **Step 1: Write the converter fixture**

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.*;
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.SectionElementDef;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.XTableUtil;
import inetsoft.uql.viewsheet.*;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * One 400 x 250 table at (20, 10) in print layout, with a THIN object border and a yellow
 * background, fed to VsToReportConverter's private table methods. Its lens caches the viewer's
 * widths: 100, 100, and the last filled to the card, 200.
 */
final class PrintLayoutConverterFixture {
   PrintLayoutConverterFixture() throws Exception {
      vs.addAssembly(table);
      info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(20, 10));
      info.setPixelSize(new Dimension(400, 250));
      info.setLayoutPosition(new Point(20, 10));
      info.setLayoutSize(new Dimension(400, 250));
      info.setTitleVisibleValue(true);
      VSFormat object = info.getFormat().getUserDefinedFormat();
      object.setBorders(new Insets(THIN, THIN, THIN, THIN));
      object.setBackground(Color.YELLOW);

      Field field = VsToReportConverter.class.getDeclaredField("report");
      field.setAccessible(true);
      TabularSheet report = (TabularSheet) field.get(converter);
      section = new SectionElementDef(report);
      report.addElement(0, 0, section);
   }

   PrintLayoutConverterFixture inset(int top, int left, int bottom, int right) {
      Insets inset = new Insets(top, left, bottom, right);
      converter.setTableCardInsets(ignored -> (Insets) inset.clone());
      return this;
   }

   /** The table's own title format, created if it has none yet. */
   VSFormat titleFormat() {
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format = finfo.getFormat(VSAssemblyInfo.TITLEPATH);

      if(format == null) {
         format = new VSCompositeFormat();
         finfo.setFormat(VSAssemblyInfo.TITLEPATH, format);
      }

      return format.getUserDefinedFormat();
   }

   void setColumnWidths(int width) {
      for(int c = 0; c < lens().getColCount(); c++) {
         info.setColumnWidthValue2(c, width, lens());
      }
   }

   int[] columnWidths() throws Exception {
      return (int[]) invoke("calculateColumnWidths",
         new Class<?>[] { TableDataVSAssemblyInfo.class, VSTableLens.class }, info, lens());
   }

   int printHeight() throws Exception {
      return (int) invoke("computePrintLayoutTableHeight",
         new Class<?>[] { TableDataVSAssemblyInfo.class, VSTableLens.class }, info, lens());
   }

   /** Run addTable, and return what it added to the section, in order. */
   List<ReportElement> addTable() throws Exception {
      invoke("addTable",
         new Class<?>[] { TableDataVSAssembly.class, VSTableLens.class, String.class },
         table, lens(), section.getID());
      SectionBand band = band();
      List<ReportElement> elements = new ArrayList<>();

      for(int i = 0; i < band.getElementCount(); i++) {
         elements.add(band.getElement(i));
      }

      return elements;
   }

   TableElementDef tableElement() throws Exception {
      return (TableElementDef) addTable().stream()
         .filter(e -> e instanceof TableElementDef).findFirst().orElseThrow();
   }

   Rectangle bounds(ReportElement element) {
      SectionBand band = band();
      return band.getBounds(band.getElementIndex(element));
   }

   private SectionBand band() {
      return section.getSection().getSectionContent()[0];
   }

   private VSTableLens lens() {
      if(lens == null) {
         VSTableLens base = new VSTableLens(new DefaultTableLens(XTableUtil.getDefaultData()));
         base.initTableGrid(info);
         lens = new RegionTableLens(base, base.getRowCount(), base.getColCount());
      }

      return lens;
   }

   private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
      Method method = VsToReportConverter.class.getDeclaredMethod(name, types);
      method.setAccessible(true);
      return method.invoke(converter, args);
   }

   private static final int THIN = StyleConstants.THIN_LINE;
   final VsToReportConverter converter = new VsToReportConverter(null, null, null, null, null);
   final Viewsheet vs = new Viewsheet();
   final TableVSAssembly table = new TableVSAssembly(vs, "Table1");
   final TableVSAssemblyInfo info;
   private final SectionElementDef section;
   private VSTableLens lens;
}
```

- [ ] **Step 2: Write the failing column-width and height tests**

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.ReportSheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The converter sizes a padded table's columns to the grid, the card less its side insets, and
 * counts the top and bottom insets in the height it predicts.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintLayoutCardArithmeticTest {
   @Test
   void theLastColumnGivesBackTheLensFillThenFillsTheGrid() throws Exception {
      // the lens filled its last column to the 400 card; 32 of that fill is inset
      assertArrayEquals(new int[] { 100, 100, 168 },
                        new PrintLayoutConverterFixture().inset(16, 16, 16, 16).columnWidths());
   }

   @Test
   void withoutAnInsetTheLastColumnKeepsTheLensFill() throws Exception {
      assertArrayEquals(new int[] { 100, 100, 200 }, new PrintLayoutConverterFixture().columnWidths());
   }

   @Test
   void setColumnsFillTheGrid() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.setColumnWidths(80);

      // 240 of columns in the 368 grid: the last takes the 128 left over
      assertArrayEquals(new int[] { 80, 80, 208 }, fixture.columnWidths());
   }

   @Test
   void withoutAnInsetSetColumnsFillTheCard() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture();
      fixture.setColumnWidths(80);

      assertArrayEquals(new int[] { 80, 80, 240 }, fixture.columnWidths());
   }

   @Test
   void theTabsHeightAddsTheTopAndBottomInsets() throws Exception {
      int plain = new PrintLayoutConverterFixture().printHeight();
      int inset = new PrintLayoutConverterFixture().inset(16, 16, 12, 16).printHeight();

      assertEquals(plain + 16 + 12, inset);
   }
}
```

- [ ] **Step 3: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=PrintLayoutCardArithmeticTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, "cannot find symbol: method setTableCardInsets".

- [ ] **Step 4: Add the lookup to the converter**

Add `import java.util.function.Function;` beside `import java.util.stream.Collectors;`.

After the constructor:

```java
   /**
    * Resolve each table's card inset through the exporter. Without a resolver, tables print
    * with no inset.
    */
   public void setTableCardInsets(Function<TableDataVSAssemblyInfo, Insets> resolver) {
      this.tableCardInsets = resolver;
   }

   // the table's card inset in this export, never null
   private Insets getCardInset(TableDataVSAssemblyInfo info) {
      Insets inset = tableCardInsets == null ? null : tableCardInsets.apply(info);
      return inset == null ? new Insets(0, 0, 0, 0) : inset;
   }
```

After `private float scalefont = 1;`:

```java
   private Function<TableDataVSAssemblyInfo, Insets> tableCardInsets = null;
```

- [ ] **Step 5: Size the columns to the grid**

In `calculateColumnWidths`, replace:

```java
      int totalWidth = 0;
      int totalPixelW = 0;
      int layoutPixelW = info.getLayoutSize().width;
      int[] ws = new int[lens.getColCount()];
      int[] widths = lens.getColumnWidths();

      // get user set column widths
      for(int i = 0; i < ws.length; i++) {
         double w = info.getColumnWidth2(i, lens);

         if((Double.isNaN(w) || w <= 0) && widths != null && i < widths.length) {
            w = widths[i];
         }
```

with:

```java
      int totalWidth = 0;
      int totalPixelW = 0;
      // the columns fill the grid, the card less its side insets
      Insets inset = getCardInset(info);
      int insetW = inset.left + inset.right;
      int layoutPixelW = Math.max(0, info.getLayoutSize().width - insetW);
      int[] ws = new int[lens.getColCount()];
      int[] widths = lens.getColumnWidths();

      // get user set column widths
      for(int i = 0; i < ws.length; i++) {
         double w = info.getColumnWidth2(i, lens);

         if((Double.isNaN(w) || w <= 0) && widths != null && i < widths.length) {
            w = lens.getColumnWidthInGrid(i, info, insetW);
         }
```

and, a few lines down:

```java
      Dimension infoSize = info.getPixelSize();
      totalPixelW += infoSize.width;
```

with:

```java
      Dimension infoSize = info.getPixelSize();
      totalPixelW += Math.max(0, infoSize.width - insetW);
```

At a zero inset `getColumnWidthInGrid` returns `widths[i]` unchanged (`VSTableLens:349`), so the path is today's.

- [ ] **Step 6: Count the top and bottom insets in the predicted height**

In `computePrintLayoutTableHeight`, replace the final `return height;` with:

```java
      // the card's top and bottom insets
      Insets inset = getCardInset(info);
      return height + inset.top + inset.bottom;
```

- [ ] **Step 7: Run the tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest=PrintLayoutCardArithmeticTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `tests="5"`, `failures="0"`, `errors="0"`.

- [ ] **Step 8: Write the failing fit-page test**

Add to `PrintLayoutCardArithmeticTest`:

```java
   @Test
   void theFitPageChoiceComparesTheColumnsWithTheGrid() throws Exception {
      // 100 + 100 + 168 fill the 368 grid, so the table is not switched to fit page width
      TableElementDef table =
         new PrintLayoutConverterFixture().inset(16, 16, 16, 16).tableElement();

      assertEquals(ReportSheet.TABLE_FIT_CONTENT_PAGE, table.getLayout());
   }

   @Test
   void withoutAnInsetTheFitPageChoiceComparesTheColumnsWithTheCard() throws Exception {
      assertEquals(ReportSheet.TABLE_FIT_CONTENT_PAGE,
                   new PrintLayoutConverterFixture().tableElement().getLayout());
   }
```

Add `import inetsoft.report.internal.TableElementDef;` to the imports.

- [ ] **Step 9: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=PrintLayoutCardArithmeticTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `failures="1"`. `theFitPageChoiceComparesTheColumnsWithTheGrid` fails with "expected: <4> but was: <1>": the 368 of columns is under the 400 card.

- [ ] **Step 10: Compare the columns with the grid in `addTable`**

After `TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();` at the top of `addTable`:

```java
      Insets inset = getCardInset(info);
```

Replace:

```java
      if(totalw < info.getLayoutSize().width) {
         tableelem.setLayout(ReportSheet.TABLE_FIT_PAGE);
      }
      else if(totalw > info.getLayoutSize().width * 5 && tableLayout == ReportSheet.TABLE_FIT_PAGE) {
```

with:

```java
      // compared with the grid, since the columns were filled to it
      int gridW = Math.max(0, info.getLayoutSize().width - inset.left - inset.right);

      if(totalw < gridW) {
         tableelem.setLayout(ReportSheet.TABLE_FIT_PAGE);
      }
      else if(totalw > gridW * 5 && tableLayout == ReportSheet.TABLE_FIT_PAGE) {
```

- [ ] **Step 11: Write the failing resolver test**

```java
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VsToReportConverter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Print layout reads a table's card inset through the PDF exporter's resolver, the one C1's
 * PDF export reads.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PDFPrintLayoutCardInsetTest {
   @Test
   void thePrintLayoutConverterReadsThePdfExportersInset() throws Exception {
      VsToReportConverter converter =
         new PDFVSExporter(null, null, null, null, new ByteArrayOutputStream())
            .createReportConverter(null);

      assertEquals(new Insets(16, 12, 8, 4), cardInset(converter, padded(new Insets(16, 12, 8, 4))));
   }

   @Test
   void aConverterWithoutAResolverPrintsTablesUninset() throws Exception {
      VsToReportConverter converter = new VsToReportConverter(null, null, null, null, null);

      assertEquals(new Insets(0, 0, 0, 0), cardInset(converter, padded(new Insets(16, 12, 8, 4))));
   }

   private static Insets cardInset(VsToReportConverter converter, TableDataVSAssemblyInfo info)
      throws Exception
   {
      Method method =
         VsToReportConverter.class.getDeclaredMethod("getCardInset", TableDataVSAssemblyInfo.class);
      method.setAccessible(true);
      return (Insets) method.invoke(converter, info);
   }

   private static TableVSAssemblyInfo padded(Insets padding) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setPadding(padding);
      return info;
   }
}
```

- [ ] **Step 12: Run it and watch it fail**

Run: `./mvnw -q test -pl core -Dtest=PDFPrintLayoutCardInsetTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR, "cannot find symbol: method createReportConverter".

- [ ] **Step 13: Hand the converter the PDF exporter's resolver**

In `PDFVSExporter.export`, replace:

```java
            VsToReportConverter converter = new VsToReportConverter(box, libManagerProvider, cluster, fileSystemService, dataSpace);
            ReportSheet report = converter.generateReport();
```

with:

```java
            ReportSheet report = createReportConverter(box).generateReport();
```

and add after `export`:

```java
   /**
    * The print-layout converter, reading each table's card inset through this exporter.
    */
   protected VsToReportConverter createReportConverter(ViewsheetSandbox box) {
      VsToReportConverter converter =
         new VsToReportConverter(box, libManagerProvider, cluster, fileSystemService, dataSpace);
      converter.setTableCardInsets(this::getTableCardInset);
      return converter;
   }
```

- [ ] **Step 14: Run the converter tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest='PrintLayoutCardArithmeticTest,PDFPrintLayoutCardInsetTest,PrintLayoutCellPaddingTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected:
- `PrintLayoutCardArithmeticTest`: 7 tests;
- `PDFPrintLayoutCardInsetTest`: 2 tests;
- the existing `PrintLayoutCellPaddingTest`: unchanged and still passing.

All have `failures="0"` and `errors="0"`.

- [ ] **Step 15: Commit**

```bash
git branch --show-current
```
Expect `feature-density-padding-print-layout`.

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFVSExporter.java core/src/test/java/inetsoft/uql/viewsheet/internal/PrintLayoutConverterFixture.java core/src/test/java/inetsoft/uql/viewsheet/internal/PrintLayoutCardArithmeticTest.java core/src/test/java/inetsoft/report/io/viewsheet/pdf/PDFPrintLayoutCardInsetTest.java
```

```bash
git commit -F - <<'EOF'
Size a print-layout table's columns to its grid

Print layout now reads each table's card inset through the PDF exporter's resolver, the one the PDF export already reads.
- The columns fill the grid, the card less its side insets. The lens's fill into the inset is given back first.
- The fit-page decision compares against the same width.
- The height predicted for a shrunk table in bottom tabs counts the top and bottom insets.

A converter without a resolver prints every table as today.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 6: Draw the card-top box and the title inside it

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java`:
  - `addTable` (`:1104`);
  - new `createCardTop`, `addCardTitle`, `setBoxBorders` and `isZero`, placed after `subtractHiddenTitleFromBounds` (`:1439`).
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/PrintLayoutCardTopTest.java`

**Interfaces:**
- Consumes:
  - from Task 5: `getCardInset`, `setTableCardInsets` and `PrintLayoutConverterFixture`;
  - from Task 1: `TableElementDef.setCardInset` and `getCardInset`.
- Produces:
  - `private Rectangle createCardTop(TableDataVSAssembly assembly, Insets inset, String sectionName)`, which returns the table element's bounds;
  - `private static boolean isZero(Insets inset)`.

- [ ] **Step 1: Write the failing tests**

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.ReportElement;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.TextBoxElementDef;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A padded print-layout table gets a fixed card-top box, T + title tall and framed on three
 * sides, with the title inside the side insets; the table element below carries (0, L, B, R).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintLayoutCardTopTest {
   @Test
   void aPaddedTableGetsACardTopBoxAboveItsTitle() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();
      TextBoxElementDef top = (TextBoxElementDef) elements.get(0);
      TextBoxElementDef title = (TextBoxElementDef) elements.get(1);
      TableElementDef table = (TableElementDef) elements.get(2);

      assertEquals(new Rectangle(20, 10, 400, 16 + titleH), fixture.bounds(top));
      assertEquals(new Insets(THIN, THIN, NONE, THIN), top.getBorders());
      assertEquals(Color.YELLOW, top.getBackground());
      assertEquals(new Rectangle(36, 26, 368, titleH), fixture.bounds(title));
      assertEquals(new Rectangle(20, 10 + 16 + titleH - 1, 400, 250 - 16 - titleH),
                   fixture.bounds(table));
      assertEquals(new Insets(0, 16, 16, 16), table.getCardInset());
   }

   @Test
   void theTitleInsideTheCardKeepsOnlyItsOwnBorders() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.titleFormat().setBorders(new Insets(NONE, NONE, THIN, NONE));
      TextBoxElementDef title = (TextBoxElementDef) fixture.addTable().get(1);

      // the object's THIN top, left and right stay on the card-top box
      assertEquals(new Insets(NONE, NONE, THIN, NONE), title.getBorders());
   }

   @Test
   void aHiddenTitleLeavesACardTopBoxAsTallAsTheTopInset() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.setTitleVisibleValue(false);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();
      TextBoxElementDef top = (TextBoxElementDef) elements.get(0);

      assertEquals(2, elements.size(), "no title box");
      assertEquals(new Rectangle(20, 10, 400, 16), fixture.bounds(top));
      assertEquals(new Insets(THIN, THIN, NONE, THIN), top.getBorders(),
                   "a padded card keeps its top border with the title hidden");
      // the hidden-title height still comes off, as it did before the inset
      assertEquals(new Rectangle(20, 10 + 16 - 1, 400, 250 - 16 - titleH),
                   fixture.bounds(elements.get(1)));
   }

   @Test
   void anAsymmetricInsetMovesOnlyItsOwnEdges() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(8, 24, 4, 0);
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();

      assertEquals(new Rectangle(20, 10, 400, 8 + titleH), fixture.bounds(elements.get(0)));
      assertEquals(new Rectangle(44, 18, 376, titleH), fixture.bounds(elements.get(1)));
      assertEquals(new Insets(0, 24, 4, 0), ((TableElementDef) elements.get(2)).getCardInset());
   }

   @Test
   void aCardSmallerThanItsInsetClampsAtZero() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture().inset(16, 16, 16, 16);
      fixture.info.setPixelSize(new Dimension(20, 20));
      fixture.info.setLayoutSize(new Dimension(20, 20));
      List<ReportElement> elements = fixture.addTable();

      assertEquals(0, fixture.bounds(elements.get(1)).width, "the title has no width left");
      assertEquals(0, fixture.bounds(elements.get(2)).height, "the table has no height left");
   }

   @Test
   void withoutAnInsetTheTitleCarriesTheFrameAsToday() throws Exception {
      PrintLayoutConverterFixture fixture = new PrintLayoutConverterFixture();
      List<ReportElement> elements = fixture.addTable();
      int titleH = fixture.info.getTitleHeight();
      TextBoxElementDef title = (TextBoxElementDef) elements.get(0);
      TableElementDef table = (TableElementDef) elements.get(1);

      assertEquals(2, elements.size(), "no card-top box");
      assertEquals(new Rectangle(20, 10, 400, titleH), fixture.bounds(title));
      assertEquals(new Insets(THIN, THIN, THIN, THIN), title.getBorders());
      assertEquals(new Rectangle(20, 10 + titleH - 1, 400, 250 - titleH), fixture.bounds(table));
      assertNull(table.getCardInset());
   }

   private static final int THIN = StyleConstants.THIN_LINE;
   private static final int NONE = StyleConstants.NO_BORDER;
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./mvnw -q test -pl core -Dtest=PrintLayoutCardTopTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: the five inset tests do not pass. With no card-top box the element list is one short, so surefire counts each of the five as a failure (a wrong bound or size) or an error (a `ClassCastException` or `IndexOutOfBoundsException` on the missing element); `failures` + `errors` = 5. `withoutAnInsetTheTitleCarriesTheFrameAsToday` passes.

- [ ] **Step 3: Draw the card-top box**

At the top of `addTable`, replace:

```java
      final Rectangle bounds;

      if(info.isTitleVisible()) {
```

with:

```java
      final Rectangle bounds;

      if(!isZero(inset)) {
         bounds = createCardTop(assembly, inset, sectionName);
      }
      else if(info.isTitleVisible()) {
```

Directly before the final `addElement0(bounds, tableelem, sectionName);`:

```java
      if(!isZero(inset)) {
         // the card-top box holds the top inset; the element carries the other three edges
         tableelem.setCardInset(new Insets(0, inset.left, inset.bottom, inset.right));
      }
```

After `subtractHiddenTitleFromBounds`:

```java
   /**
    * Draw the part of a padded table's card that never grows: the top inset and the title lane,
    * framed on the top, left and right, with the title inside the side insets.
    * @return the bounds left for the table element.
    */
   private Rectangle createCardTop(TableDataVSAssembly assembly, Insets inset,
                                   String sectionName)
   {
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();
      Rectangle bounds = getPixelBounds(assembly);
      int titleH = getTitleHeight(assembly, true);
      int laneH = info.isTitleVisible() ? titleH : 0;
      Rectangle topBounds = new Rectangle(bounds.x, bounds.y, bounds.width, inset.top + laneH);
      TextBoxElementDef top = addTextBoxElement0(
         info, new TableDataPath(-1, TableDataPath.OBJECT), "", topBounds, sectionName);
      VSCompositeFormat objfmt = info.getFormat();
      Insets borders = objfmt == null ? null : objfmt.getBorders();
      // the table element below continues the sides and closes the bottom
      setBoxBorders(top, borders == null ? new Insets(0, 0, 0, 0) :
         new Insets(borders.top, borders.left, StyleConstants.NO_BORDER, borders.right));

      if(info.isTitleVisible()) {
         addCardTitle(assembly, new Rectangle(bounds.x + inset.left, bounds.y + inset.top,
            Math.max(0, bounds.width - inset.left - inset.right), titleH), sectionName);
      }

      // 1px up so the sides join, as the table joins its title without an inset
      return new Rectangle(bounds.x, bounds.y + inset.top + laneH - 1, bounds.width,
                           Math.max(0, bounds.height - inset.top - titleH));
   }

   // the title inside a padded table's card keeps its own format and borders, not the card's
   private void addCardTitle(TableDataVSAssembly assembly, Rectangle titleBounds,
                             String sectionName)
   {
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();
      TextBoxElementDef textbox =
         new TextBoxElementDef(report, new DefaultTextLens(info.getTitle()));
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat detailfmt = finfo == null ? null :
         finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
      applyFormat(textbox, info.getFormat(), detailfmt, info, true);
      Insets own = detailfmt == null ? null : detailfmt.getBorders();
      setBoxBorders(textbox, own == null ? new Insets(0, 0, 0, 0) : (Insets) own.clone());
      textbox.setZIndex(assembly.getZIndex());
      addElement0(titleBounds, textbox, sectionName);
   }

   // an empty frame also clears the box's overall border, as applyFormat does
   private static void setBoxBorders(TextBoxElementDef box, Insets borders) {
      box.setBorders(borders);

      if(isZero(borders)) {
         box.setBorder(StyleConstants.NO_BORDER);
      }
   }

   private static boolean isZero(Insets inset) {
      return inset.top == 0 && inset.left == 0 && inset.bottom == 0 && inset.right == 0;
   }
```

Three facts this code relies on:
- **Why `addTextBoxElement0` is safe here.** Its `applyFormat` would give every empty edge of the box a `THIN_LINE`, because `TableDataVSAssemblyInfo` is not on its default-border exception list (`:2118`). `setBoxBorders` then overwrites that, the way the chart sets `borderTextBox`'s borders at `:1495`.
- **The paint order.** The title is added after the box. `printFixedContainer` orders the band by y, and `sortPaintableByZIndex`'s `Arrays.sort` is stable, so the title paints over the box.
- **The title's borders.** `getFormat(TITLEPATH, false)` copies the object's border colours into the title's default tier, but not its borders (`FormatInfo:317-323`). So `own` holds the title's own borders.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./mvnw -q test -pl core -Dtest='PrintLayoutCard*Test,PDFPrintLayoutCardInsetTest,PrintLayoutCellPaddingTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `PrintLayoutCardTopTest` 6, `PrintLayoutCardArithmeticTest` 7, `PDFPrintLayoutCardInsetTest` 2 and `PrintLayoutCellPaddingTest` unchanged, all passing.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
```
Expect `feature-density-padding-print-layout`.

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java core/src/test/java/inetsoft/uql/viewsheet/internal/PrintLayoutCardTopTest.java
```

```bash
git commit -F - <<'EOF'
Draw a padded print-layout table's card top and the title inside it

A padded table now opens with a fixed box as tall as its top inset and title lane. The box carries the card's top, left and right borders and its background. The title sits inside the side insets with only its own borders. The table element below carries the other three edges of the inset. A hidden title leaves a box as tall as the top inset, which still carries the top border. A table without an inset keeps today's title.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 7: Verify C2 against the baselines

**Files:**
- Create: `community/.superpowers/baselines/density-padding-export/after-c2/` (git-excluded)
- Create: `community/.superpowers/baselines/density-padding-export/print_inset_lines.py` (git-excluded)

- [ ] **Step 1: Run the whole core suite**

```bash
cd /e/StyleBI/stylebi-enterprise/community
./mvnw -q test -pl core
cat core/target/surefire-reports/TEST-*.xml | grep -o '<testsuite [^>]*' | grep -o 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"' | awk -F'"' '{s[$1]+=$2} END {for(k in s) print k s[k]}'
```

Expected: `failures=0` and `errors=0`. The test count is C1's 6046 plus about 38 new ones. Run with `clean` if a class you did not touch fails with `NoSuchMethodError`.

- [ ] **Step 2: Build, restart, and export the after-C2 print PDFs**

- Build and start the server from this branch.
- Export `DPX Legacy Print` and `DPX Modern Print` as PDF into `after-c2/`, as `c2-legacy-print.pdf` and `c2-modern-print.pdf`.
- Re-import `dpx-fixture-c2.zip` into a fresh server at `viewsheet.density` = `compact`, and again at `dense`. Export `DPX Modern Print` each time, as `after-c2/compact/c2-modern-print.pdf` and `after-c2/dense/c2-modern-print.pdf`.
- A re-import keeps each table's seeded inset (`4f742abe8`). For the compact and dense runs, re-seed the tables with the dashboard's Revert and then Modernize before exporting, so they carry 12 and 8.

- [ ] **Step 3: Legacy print must not change**

```bash
cd /e/StyleBI/stylebi-enterprise/community/.superpowers/baselines/density-padding-export
python -c "import compare_after_c1 as c; print(c.compare_pdf('c2-legacy-print.pdf', 'after-c2/c2-legacy-print.pdf'))"
```

Expected: `(True, '')`, which means the same page count, text positions and vector drawings.
- If it differs only in chart or tab images, suspect the font registration first, not the code: restart once after the fonts folder exists, then export again.
- Any difference in a table's words or lines is a C2 regression in a zero-inset path. Stop and debug it.

- [ ] **Step 4: Measure the modern inset**

Write `print_inset_lines.py` in the baselines folder:

```python
"""
List each page's vertical border lines and table titles in a print-layout PDF.

    python print_inset_lines.py PDF

A padded card shows its left border line and, L to the right, the first column's own left
border, which the inset keeps. So the gap between the first two x values of a card is its inset.
"""
import sys

import fitz

for number, page in enumerate(fitz.open(sys.argv[1]), 1):
    xs = sorted({round(it[1].x, 1) for d in page.get_drawings() for it in d["items"]
                 if it[0] == "l" and abs(it[1].x - it[2].x) < 0.01
                 and abs(it[1].y - it[2].y) >= 20})
    titles = [(round(w[0], 1), round(w[1], 1), w[4]) for w in page.get_text("words")
              if w[4].startswith(("Table", "Crosstab", "Calc", "Chart"))]
    print(f"page {number}: vertical lines {xs}")

    for title in titles:
        print(f"    title {title[2]} at ({title[0]}, {title[1]})")
```

Run it on `after-c2/c2-modern-print.pdf`, `after-c2/compact/c2-modern-print.pdf` and `after-c2/dense/c2-modern-print.pdf`. For each padded table, the gap between its card's left line and the next line to the right is 16, 12 and 8 respectively. Before C2 there was no such gap. In the C1 baseline `modern-print.pdf`, page 1's card border sits at 72.5 and the next line is a column border, at 166.7.

- [ ] **Step 5: Check the modern print by eye, against `c2-modern-print.pdf`**

At each density:
- **The inset matches Chart1's.** Every padded table's gap from border to grid equals Chart1's on all three sides.
- **Edges.** The border and background sit at the card edge. The title sits inside the side insets, a top inset band above it.
- **Page breaks.** Where a padded table breaks across pages:
  - the continuation page starts with the repeated header, with no top band and no top border;
  - the last page ends with the last row, then the B band, then the bottom border, all on that page.
- **TableView5** (title hidden) has a top border over a T-tall band.
- **TableView3**, Fit Contents, reads as one card: its segments stack with side borders running through, no band between segments, and B once after the last segment.
- **Bottom tabs.** The shrunk bottom-tabs table still ends flush with the tab strip.
- **Clipping.** No row or column is clipped.

- [ ] **Step 6: Record the results**

Append a "C2 results" block to `README.txt`:
- the core test totals;
- the legacy comparison output;
- the measured gaps at each density;
- one line per Step 5 item.

A failed item goes back to the task that owns it. The PR is the user's call, as in C1: the branch is `feature-density-padding-print-layout`, its base is `feature-density-padding-export`, and it opens after #5808.
