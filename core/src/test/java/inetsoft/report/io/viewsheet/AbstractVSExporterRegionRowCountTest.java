/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.report.io.viewsheet;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression test for Bug #76829 (Excel match-layout export of
 * {@code Examples/Construction Dashboard}'s {@code FreehandTable2} throws
 * {@code IllegalStateException: ... intersects with another merged region}
 * while writing {@code ExcelCrosstabHelper.writeData()}).
 *
 * <p>Root cause, confirmed by direct code reading and reproduced here with
 * the real asset's exact shape: {@link AbstractVSExporter#getRegionRowCount}'s
 * height-budget clamp is supposed to bound a match-layout table export to
 * what fits in its designed {@code pixelHeight}, by walking rows and calling
 * a private {@code checkDisplayRow(data, i)} helper first. That helper used to
 * treat <em>any</em> row with a blank column as a hidden/filtered row and
 * exempt it from the height budget entirely (bug #53192's original intent)
 * &mdash; but it fired identically for the blank <em>continuation</em> rows of
 * a vertically merged group-label column, which is exactly
 * {@code FreehandTable2}'s own structure (a {@code Worker_Type} column with
 * {@code expansion="2"}+{@code mergeCells="true"}, blank on 2 of every 3 rows
 * in each of its 4 groups). The clamp became a complete no-op: it returned
 * the full, unclamped runtime row count (14 for this asset's 2 header + 4x3
 * data rows) instead of one bounded by the design {@code pixelHeight=248},
 * and that wrong (too generous) count then flowed straight into
 * {@link RegionTableLens}, defeating its own row-count/span clamp too since
 * {@code RegionTableLens}'s constructor can only clamp down to
 * {@code data.getRowCount()}, which was already exactly what
 * {@code getRegionRowCount()} wrongly returned.</p>
 *
 * <p><b>The fix</b>: {@code checkDisplayRow()} now only exempts a blank cell
 * from the height budget when it is <em>not</em> explained by a merged/spanning
 * cell anchored at an earlier row (checked via {@link TableLens#getSpan}). A
 * merge-continuation row is counted toward the height budget like any other
 * row; a genuinely blank/hidden row (bug #53192's original case &mdash; no
 * span covers it) is still exempted exactly as before.</p>
 *
 * <p><b>Round 2</b>: P5 verification found that the round-1 fix above, while
 * correct as far as it went, did not close the reported bug. The real asset's
 * actual {@code tableLayout} is 5 columns wide, not 2 &mdash; and design
 * column index 3 has <em>no {@code cellBinding} defined at all</em>, in
 * either the header or detail regions. That column is blank on <em>every</em>
 * row, header rows included, and is therefore never a merge continuation of
 * anything (there's nothing non-blank above it to anchor a span). The round-1
 * fix's {@code isMergedContinuationCell()} correctly returns {@code false}
 * for it (there genuinely is no anchor), so {@code checkDisplayRow()} still
 * returned {@code false} for every row on account of column 3 alone &mdash;
 * completely independent of whether column 0's merge fix was correct. Live
 * re-export against the real asset still threw the identical
 * {@code IllegalStateException} after round 1.
 *
 * <p>Round 2's fix: {@code getRegionRowCount()} now precomputes, once per
 * call, which columns ever hold a non-blank value anywhere across the row
 * range {@code checkDisplayRow()} is evaluated over ({@link #findSignificantColumns}).
 * A column that is blank in <em>every</em> one of those rows carries no
 * per-row signal at all &mdash; it can never distinguish a genuinely hidden/
 * filtered row from a normal one &mdash; so {@code checkDisplayRow()} now
 * skips such columns entirely instead of letting them exempt every row.</p>
 *
 * <p>The tests below use the same 14-row shape and the same design
 * {@code pixelHeight=248}/{@code headerRowHeights="20,20"}: one with the real
 * asset's blank-on-continuation merge column ({@link #tableWithGroupLabelColumn}
 * with {@code blankOnContinuation=true}) and one with the label column
 * populated on every row instead of merged ({@code blankOnContinuation=false})
 * &mdash; after the fix, both now correctly clamp to 12, isolating that the
 * merge column's blank continuation cells no longer defeat the clamp. A third
 * test confirms bug #53192's original genuinely-blank-row exemption still
 * works. A fourth, {@link #alwaysBlankGapColumnDoesNotDefeatTheHeightClamp},
 * reproduces the real asset's full 5-column shape (including the undefined
 * gap column at index 3) and is the test that caught round 1's gap.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractVSExporterRegionRowCountTest {
   private static final int PIXEL_HEIGHT = 248;
   private static final int PIXEL_WIDTH = 546;
   private static final int HEADER_ROWS = 2;
   private static final int ROW_HEIGHT = 20;
   private static final int GROUPS = 4;
   private static final int ROWS_PER_GROUP = 3;
   private static final int DATA_ROWS = GROUPS * ROWS_PER_GROUP;
   private static final int TOTAL_ROWS = HEADER_ROWS + DATA_ROWS;

   /**
    * FreehandTable2's real shape: {@code Worker_Type} (col 0) is blank on the
    * 2nd/3rd row of every 3-row group &mdash; a vertically merged group-label
    * column, exactly as bug #76829's asset defines it (`expansion="2"` +
    * `mergeCells="true"`, `mergeRowGrp="Worker_Type"`).
    */
   @Test
   void blankMergeContinuationRowsAreCountedTowardTheHeightClamp() {
      TableLens data = tableWithGroupLabelColumn(/* blankOnContinuation */ true);
      TestExporter exporter = exporter();

      int regionRowCount = exporter.regionRowCount(data);

      assertEquals(12, regionRowCount,
         "checkDisplayRow() must recognize the blank Worker_Type cells on rows "
         + "2-3 of each group as merge-continuation rows (covered by a span "
         + "anchored at the group's first row), not hidden/filtered rows, so it "
         + "counts them toward the height budget and the 248px design-height "
         + "clamp trips normally: 2 header + 10 data rows");
      assertTrue(regionRowCount < TOTAL_ROWS,
         "the clamp must actually clamp, not return the full unclamped row count");

      // Downstream effect: RegionTableLens now genuinely clamps 14 real rows
      // down to 12, instead of the pre-fix no-op (where getRegionRowCount()
      // already returned 14, so RegionTableLens had nothing left to clamp).
      RegionTableLens region = new RegionTableLens(data, regionRowCount, data.getColCount());
      assertEquals(12, region.getRowCount(),
         "RegionTableLens should clamp the 14-row lens down to the corrected count");
   }

   /**
    * Same 14-row shape, same 248px design height, but the group-label column
    * is populated on every row (no vertical merge) instead of blank on
    * continuation rows. This isolates the merge/blank-continuation pattern,
    * specifically, as the defect trigger: with it removed, the exact same
    * {@code getRegionRowCount()} correctly clamps to what fits in 248px
    * (12: 2 header + 10 data rows).
    */
   @Test
   void populatedGroupLabelColumnLetsTheHeightClampWork() {
      TableLens data = tableWithGroupLabelColumn(/* blankOnContinuation */ false);
      TestExporter exporter = exporter();

      int regionRowCount = exporter.regionRowCount(data);

      assertTrue(regionRowCount < TOTAL_ROWS,
         "without blank continuation rows, the height clamp should actually clamp");
      assertEquals(12, regionRowCount,
         "2 header rows + floor(208px budget / 20px per row) = 2 + 10 = 12");
   }

   /**
    * Guards bug #53192's original intent, which the fix must not regress: a
    * row that is blank because it is genuinely hidden/filtered &mdash; not
    * because a merged/spanning cell from an earlier row covers it &mdash;
    * must still be exempted from the height budget entirely, so it never
    * consumes budget that a later, real row needs.
    *
    * <p>1 header row + 6 data rows (index 1 = "V1", index 2 = genuinely
    * blank with no span anywhere, indices 3-6 = "V2".."V5"), design height
    * sized so exactly the 5 real data rows fit in the 100px budget (5 x
    * 20px). If the blank row were wrongly counted toward the budget instead
    * of exempted, it would consume one row's worth of height and "V5" would
    * be clamped off, returning 6 instead of 7.</p>
    */
   @Test
   void genuinelyBlankRowIsStillExemptFromTheHeightClamp() {
      Object[][] rows = {
         { "Header" },
         { "V1" },
         { "" },
         { "V2" },
         { "V3" },
         { "V4" },
         { "V5" },
      };
      DefaultTableLens raw = new DefaultTableLens(rows);
      raw.setHeaderRowCount(1);
      // No span set anywhere -- the blank row at index 2 is not a merge
      // continuation of any cell.
      TableLens data = new VSTableLens(raw);

      CalcTableVSAssemblyInfo info = Mockito.mock(CalcTableVSAssemblyInfo.class);
      when(info.getHeaderRowCount()).thenReturn(1);
      when(info.isTitleVisible()).thenReturn(false);

      CalcTableVSAssembly table = Mockito.mock(CalcTableVSAssembly.class);
      when(table.getInfo()).thenReturn(info);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      // headerHeight = 20 (1 header row); budget = 120 - 20 = 100 = exactly
      // 5 rows worth of the 5 real data rows.
      when(table.getPixelSize()).thenReturn(new Dimension(PIXEL_WIDTH, 120));
      when(table.getName()).thenReturn("HiddenRowTable");

      Viewsheet vs = Mockito.mock(Viewsheet.class);
      when(vs.getDisplayRowHeight(Mockito.eq(true), anyString(), anyInt())).thenReturn(ROW_HEIGHT);
      when(vs.getDisplayRowHeight(Mockito.eq(false), anyString())).thenReturn(ROW_HEIGHT);
      when(table.getViewsheet()).thenReturn(vs);

      int regionRowCount = new TestExporter(table).regionRowCount(data);

      assertEquals(7, regionRowCount,
         "the genuinely blank, unspanned row must be exempted from the height "
         + "budget (bug #53192's original behavior), so all 5 real data rows "
         + "still fit and are counted -- not 6, which is what would happen if "
         + "the blank row wrongly consumed height budget");
   }

   /**
    * Reproduces FreehandTable2's real, full 5-column {@code tableLayout} shape
    * (extracted from {@code community-examples/examples.zip}'s viewsheet XML):
    * col 0 = {@code Worker_Type} (merged group label, blank on continuation
    * rows), col 1 = status label (never blank), col 2 = a Lost Days count
    * (never blank), col 3 = a design column with <em>no cellBinding defined
    * anywhere</em> (blank on every single row, header rows included, never
    * covered by any span), col 4 = a second Lost Days count (never blank).
    *
    * <p>This is the P5-found gap round 1 missed: the committed round-1 test
    * used a simplified 2-column fixture that never included a structurally
    * always-blank column, so it could not catch that {@code checkDisplayRow()}
    * still returned {@code false} for every row on account of column 3 alone,
    * regardless of whether column 0's merge-continuation logic was fixed.</p>
    */
   @Test
   void alwaysBlankGapColumnDoesNotDefeatTheHeightClamp() {
      Object[][] rows = new Object[TOTAL_ROWS][5];
      rows[0] = new Object[]{ "", "", "Location", "", "Year" };
      rows[1] = new Object[]{ "", "Status", "", "", "" };

      String[] groupNames = { "Drywall Installers", "Foreman", "General Workers", "Lawyers" };
      String[] statusNames = { "Correctly Done", "Incorrectly Done", "Requires Replacement" };

      for(int g = 0; g < GROUPS; g++) {
         for(int r = 0; r < ROWS_PER_GROUP; r++) {
            int row = HEADER_ROWS + g * ROWS_PER_GROUP + r;
            String workerType = r == 0 ? groupNames[g] : "";
            // col 3 ("") has no cellBinding in the real asset -- always blank,
            // never covered by any span, exactly like the header rows above.
            rows[row] = new Object[]{ workerType, statusNames[r], "2", "", "1" };
         }
      }

      DefaultTableLens raw = new DefaultTableLens(rows);
      raw.setHeaderRowCount(HEADER_ROWS);

      for(int g = 0; g < GROUPS; g++) {
         int origin = HEADER_ROWS + g * ROWS_PER_GROUP;
         raw.setSpan(origin, 0, new Dimension(1, ROWS_PER_GROUP));
      }

      TableLens data = new VSTableLens(raw);
      TestExporter exporter = exporter();

      int regionRowCount = exporter.regionRowCount(data);

      assertEquals(12, regionRowCount,
         "the always-undefined 'gap' column (index 3, no cellBinding anywhere in "
         + "the real asset) must not, on its own, keep defeating checkDisplayRow()'s "
         + "clamp via the any-blank-column-exempts-the-row rule -- with it correctly "
         + "excluded (it carries no per-row signal, blank in every row) the clamp "
         + "trips normally at 2 header + 10 data rows, same as the 2-column fixture");
   }

   /**
    * Builds a 14-row, 2-column table lens matching FreehandTable2's real
    * detail-region cell binding: col 0 = the {@code Worker_Type} group label
    * (merged across each 3-row group when {@code blankOnContinuation}), col 1
    * = an aggregate value that is never blank.
    */
   private static TableLens tableWithGroupLabelColumn(boolean blankOnContinuation) {
      Object[][] rows = new Object[TOTAL_ROWS][2];
      rows[0] = new Object[]{ "Worker Type", "Status" };
      rows[1] = new Object[]{ "", "Status" };

      String[] groupNames = { "Drywall Installers", "Foreman", "General Workers", "Lawyers" };

      for(int g = 0; g < GROUPS; g++) {
         for(int r = 0; r < ROWS_PER_GROUP; r++) {
            int row = HEADER_ROWS + g * ROWS_PER_GROUP + r;
            boolean isOrigin = r == 0;
            String label = isOrigin || !blankOnContinuation ? groupNames[g] : "";
            rows[row] = new Object[]{ label, "Status" + r };
         }
      }

      DefaultTableLens raw = new DefaultTableLens(rows);
      raw.setHeaderRowCount(HEADER_ROWS);

      if(blankOnContinuation) {
         for(int g = 0; g < GROUPS; g++) {
            int origin = HEADER_ROWS + g * ROWS_PER_GROUP;
            raw.setSpan(origin, 0, new Dimension(1, ROWS_PER_GROUP));
         }
      }

      return new VSTableLens(raw);
   }

   private static TestExporter exporter() {
      CalcTableVSAssemblyInfo info = Mockito.mock(CalcTableVSAssemblyInfo.class);
      when(info.getHeaderRowCount()).thenReturn(HEADER_ROWS);
      when(info.isTitleVisible()).thenReturn(false);

      CalcTableVSAssembly table = Mockito.mock(CalcTableVSAssembly.class);
      when(table.getInfo()).thenReturn(info);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getPixelSize()).thenReturn(new Dimension(PIXEL_WIDTH, PIXEL_HEIGHT));
      when(table.getName()).thenReturn("FreehandTable2");

      Viewsheet vs = Mockito.mock(Viewsheet.class);
      when(vs.getDisplayRowHeight(Mockito.eq(true), anyString(), anyInt())).thenReturn(ROW_HEIGHT);
      when(vs.getDisplayRowHeight(Mockito.eq(false), anyString())).thenReturn(ROW_HEIGHT);
      when(table.getViewsheet()).thenReturn(vs);

      return new TestExporter(table);
   }

   /**
    * Exposes the protected {@code getRegionRowCount()} under test, with
    * match-layout on (bug #76829's export is a match-layout Excel export).
    * Subclasses {@code CSVVSExporter} purely to get a concrete
    * {@code AbstractVSExporter} for free (it's the simplest exporter in
    * {@code core}) &mdash; none of its writeXxx() overrides are exercised.
    */
   private static final class TestExporter extends CSVVSExporter {
      private final CalcTableVSAssembly table;

      TestExporter(CalcTableVSAssembly table) {
         this.table = table;
         setMatchLayout(true);
      }

      int regionRowCount(TableLens data) {
         return getRegionRowCount(table, data);
      }
   }
}
