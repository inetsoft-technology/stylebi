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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.function.IntPredicate;

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
 *
 * <p><b>Bug #77237</b>: rounds 1 and 2 only filtered out specific blank-cell
 * densities (merge continuations, always-blank columns). Any other blank cell
 * in a freehand table (a formula column returning {@code ''} on most rows, a
 * single blank cell, a blank spacer row) still exempted its row, so the clamp
 * failed again. The bug #53192 exemption is now applied to crosstabs only
 * (bug #53192 was a date-comparison crosstab); every other table counts every
 * row toward the design-height budget, because the exporters still write
 * those rows. The calc-table tests below cover the blank-density axis,
 * {@link #blankRowInPlainTableCountsTowardTheHeightClamp} covers plain tables,
 * and {@link #genuinelyBlankCrosstabRowIsStillExemptFromTheHeightClamp} guards
 * the crosstab exemption.</p>
 *
 * <p>So the round-1 and round-2 rules described above ({@code checkDisplayRow()},
 * {@code isMergedContinuationCell()}, {@code findSignificantColumns()}) now run
 * for crosstabs only. The three bug #76829 fixtures are parameterized over
 * {@link Kind}: the {@code CROSSTAB} run keeps those helpers covered, and the
 * {@code CALC} run checks that calc tables count every row.</p>
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
    *
    * <p>Runs for both table kinds. For a calc table every row counts (bug
    * #77237). For a crosstab, {@code checkDisplayRow()} is still consulted, so
    * this guards round 1's {@code isMergedContinuationCell()} rule: without it
    * the 8 continuation rows would be exempted and the result would be 14.</p>
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   void blankMergeContinuationRowsAreCountedTowardTheHeightClamp(Kind kind) {
      TableLens data = tableWithGroupLabelColumn(/* blankOnContinuation */ true);
      TestExporter exporter = exporter(kind);

      int regionRowCount = exporter.regionRowCount(data);

      assertEquals(12, regionRowCount,
         kind + ": the blank Worker_Type cells on rows 2-3 of each group are "
         + "merge-continuation cells (covered by a span anchored at the group's "
         + "first row), not hidden/filtered rows, so they count toward the height "
         + "budget and the 248px design-height clamp trips normally: "
         + "2 header + 10 data rows");
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
   @ParameterizedTest
   @EnumSource(Kind.class)
   void populatedGroupLabelColumnLetsTheHeightClampWork(Kind kind) {
      TableLens data = tableWithGroupLabelColumn(/* blankOnContinuation */ false);
      TestExporter exporter = exporter(kind);

      int regionRowCount = exporter.regionRowCount(data);

      assertTrue(regionRowCount < TOTAL_ROWS,
         "without blank continuation rows, the height clamp should actually clamp");
      assertEquals(12, regionRowCount,
         "2 header rows + floor(208px budget / 20px per row) = 2 + 10 = 12");
   }

   /**
    * Guards bug #53192's original intent, which must not regress: in a
    * <em>crosstab</em> (bug #53192 was a date-comparison crosstab), a row
    * that is blank &mdash; not because a merged/spanning cell from an earlier
    * row covers it &mdash; is still exempted from the height budget, so it
    * never consumes budget that a later, real row needs.
    *
    * <p>1 header row + 6 data rows (index 1 = "V1", index 2 = blank with no
    * span anywhere, indices 3-6 = "V2".."V5"), design height sized so exactly
    * the 5 non-blank data rows fit in the 100px budget (5 x 20px). If the
    * blank row were counted toward the budget, "V5" would be clamped off and
    * this would return 6 instead of 7.</p>
    *
    * <p>Before bug #77237 this test mocked a {@code CalcTableVSAssembly}; its
    * premise (a blank row takes no space) is false for freehand tables, whose
    * exporters write every row, so it now runs against a crosstab, the type
    * the exemption was written for. The calc-table expectation for the same
    * shape is in {@link #blankSpacerRowInCalcTableCountsTowardTheHeightClamp}.</p>
    */
   @Test
   void genuinelyBlankCrosstabRowIsStillExemptFromTheHeightClamp() {
      TableLens data = oneColumnTableWithBlankRow();

      CrosstabVSAssemblyInfo info = Mockito.mock(CrosstabVSAssemblyInfo.class);
      when(info.isTitleVisible()).thenReturn(false);

      VSCrosstabInfo crosstabInfo = Mockito.mock(VSCrosstabInfo.class);
      // one column header -> hrow = 1
      when(crosstabInfo.getRuntimeColHeaders()).thenReturn(new DataRef[1]);

      CrosstabVSAssembly table = Mockito.mock(CrosstabVSAssembly.class);
      when(table.getInfo()).thenReturn(info);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getVSCrosstabInfo()).thenReturn(crosstabInfo);
      // headerHeight = 20 (1 header row); budget = 120 - 20 = 100 = exactly
      // 5 rows worth of the 5 non-blank data rows.
      when(table.getPixelSize()).thenReturn(new Dimension(PIXEL_WIDTH, 120));
      when(table.getName()).thenReturn("HiddenRowCrosstab");
      Viewsheet vs = viewsheet();
      when(table.getViewsheet()).thenReturn(vs);

      int regionRowCount = new TestExporter(table).regionRowCount(data);

      assertEquals(7, regionRowCount,
         "the blank, unspanned crosstab row must be exempted from the height "
         + "budget (bug #53192's original behavior), so all 5 non-blank data rows "
         + "still fit and are counted");
   }

   /**
    * Bug #77237, wholly blank row: the same 1-header + 6-data-row shape as the
    * crosstab test above, but in a freehand table. A blank calc-table row (a
    * design spacer row, or a row whose formulas all return {@code ''}) is still
    * written as a real row by the exporters, so it counts toward the 100px
    * budget and only 5 data rows fit: 1 header + 5 = 6.
    */
   @Test
   void blankSpacerRowInCalcTableCountsTowardTheHeightClamp() {
      TableLens data = oneColumnTableWithBlankRow();

      CalcTableVSAssemblyInfo info = Mockito.mock(CalcTableVSAssemblyInfo.class);
      when(info.getHeaderRowCount()).thenReturn(1);
      when(info.isTitleVisible()).thenReturn(false);

      CalcTableVSAssembly table = Mockito.mock(CalcTableVSAssembly.class);
      when(table.getInfo()).thenReturn(info);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getPixelSize()).thenReturn(new Dimension(PIXEL_WIDTH, 120));
      when(table.getName()).thenReturn("SpacerRowTable");
      Viewsheet vs = viewsheet();
      when(table.getViewsheet()).thenReturn(vs);

      assertEquals(6, new TestExporter(table).regionRowCount(data),
         "a blank calc-table row takes real vertical space, so it must count "
         + "toward the height budget: 1 header + 5 data rows");
   }

   /**
    * Bug #77237, plain table: the fix exempts only crosstabs, so a plain
    * {@code TableVSAssembly} (and {@code EmbeddedTableVSAssembly}, its
    * subclass) with a blank row must also count every row. This fails against
    * a narrower discriminator such as {@code !(table instanceof
    * CalcTableVSAssembly)}. Plain tables have 1 header row (hrow = 1), so the
    * shape and expectation match the calc-table spacer test: 1 header + 5 = 6.
    */
   @Test
   void blankRowInPlainTableCountsTowardTheHeightClamp() {
      TableLens data = oneColumnTableWithBlankRow();

      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.isTitleVisible()).thenReturn(false);

      TableVSAssembly table = Mockito.mock(TableVSAssembly.class);
      when(table.getInfo()).thenReturn(info);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getPixelSize()).thenReturn(new Dimension(PIXEL_WIDTH, 120));
      when(table.getName()).thenReturn("PlainTable");
      Viewsheet vs = viewsheet();
      when(table.getViewsheet()).thenReturn(vs);

      assertEquals(6, new TestExporter(table).regionRowCount(data),
         "a blank plain-table row is still written, so it must count toward the "
         + "height budget: 1 header + 5 data rows");
   }

   /**
    * Bug #77237, the reported shape: FreehandTable2 after appending a sixth
    * column whose formula ({@code ... ? 'X' : ''}) is non-blank on exactly one
    * data row. Before the fix the column was "significant" (non-blank somewhere),
    * so the 11 rows where it is blank were all exempted and the method returned
    * 14, overflowing into the selection lists below
    * ({@code The range P18:U19 intersects with another merged region N18:P18}).
    */
   @Test
   void sparseFormulaColumnDoesNotDefeatTheHeightClamp() {
      int onlyValueRow = HEADER_ROWS + ROWS_PER_GROUP; // Foreman / Correctly Done
      TableLens data = sixColumnFreehandTable(row -> row == onlyValueRow);

      assertEquals(12, exporter().regionRowCount(data),
         "a column that is blank on 11 of 12 data rows is ordinary calc-table "
         + "data; every row is still written, so the clamp must trip at "
         + "2 header + 10 data rows");
   }

   /**
    * Bug #77237, the other end of the blank-density axis: the sixth column is
    * filled on every data row except one. Before the fix that single blank cell
    * exempted its row and the method returned 13 (one row of overflow).
    */
   @Test
   void singleBlankCellDoesNotDefeatTheHeightClamp() {
      int blankRow = HEADER_ROWS + 5;
      TableLens data = sixColumnFreehandTable(row -> row != blankRow);

      assertEquals(12, exporter().regionRowCount(data),
         "one blank cell in an otherwise full calc-table column must not exempt "
         + "its row: 2 header + 10 data rows");
   }

   /**
    * Bug #77237, blank spacer row in the reported 6-column shape: an extra
    * wholly blank row (15 rows in all) is still a written row, so the clamp
    * still trips at 12. Before the fix the spacer row was exempted and the
    * method returned 13.
    */
   @Test
   void blankSpacerRowDoesNotDefeatTheHeightClampInTheReportedShape() {
      TableLens full = sixColumnFreehandTable(row -> true);
      // between the first and second Worker_Type groups
      int spacerAt = HEADER_ROWS + ROWS_PER_GROUP;
      Object[][] rows = new Object[full.getRowCount() + 1][];

      for(int r = 0, src = 0; r < rows.length; r++) {
         if(r == spacerAt) {
            rows[r] = new Object[]{ "", "", "", "", "", "" };
            continue;
         }

         rows[r] = new Object[full.getColCount()];

         for(int c = 0; c < full.getColCount(); c++) {
            rows[r][c] = full.getObject(src, c);
         }

         src++;
      }

      DefaultTableLens raw = new DefaultTableLens(rows);
      raw.setHeaderRowCount(HEADER_ROWS);

      // keep the Worker_Type merges, shifted past the spacer row
      for(int g = 0; g < GROUPS; g++) {
         int origin = HEADER_ROWS + g * ROWS_PER_GROUP + (g > 0 ? 1 : 0);
         raw.setSpan(origin, 0, new Dimension(1, ROWS_PER_GROUP));
      }

      TableLens data = new VSTableLens(raw);

      assertEquals(12, exporter().regionRowCount(data),
         "a wholly blank spacer row in a freehand table is still written, so it "
         + "must count toward the height budget: 2 header + 10 data rows");
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
    *
    * <p>Runs for both table kinds. For a crosstab it guards round 2's
    * {@code findSignificantColumns()} rule (and round 1's merge rule for col 0):
    * without either, every data row would be exempted and the result would be
    * 14. For a calc table every row counts regardless (bug #77237).</p>
    */
   @ParameterizedTest
   @EnumSource(Kind.class)
   void alwaysBlankGapColumnDoesNotDefeatTheHeightClamp(Kind kind) {
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
      TestExporter exporter = exporter(kind);

      int regionRowCount = exporter.regionRowCount(data);

      assertEquals(12, regionRowCount,
         kind + ": the always-undefined 'gap' column (index 3, no cellBinding "
         + "anywhere in the real asset) carries no per-row signal (blank in every "
         + "row), so it must not exempt any row from the height budget; the clamp "
         + "trips normally at 2 header + 10 data rows, same as the 2-column fixture");
   }

   /**
    * 1 header row + 6 data rows; data row index 2 is blank and not covered by
    * any span.
    */
   private static TableLens oneColumnTableWithBlankRow() {
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
      return new VSTableLens(raw);
   }

   /**
    * FreehandTable2's shape after bug #77237's appended column: the 5 columns
    * of {@link #alwaysBlankGapColumnDoesNotDefeatTheHeightClamp} (merged
    * Worker_Type, status, value, always-blank gap column, value) plus col 5,
    * which holds "X" on the data rows where {@code hasValue} is true and ""
    * elsewhere. 14 rows (2 header + 4 groups x 3).
    */
   private static TableLens sixColumnFreehandTable(IntPredicate hasValue) {
      Object[][] rows = new Object[TOTAL_ROWS][6];
      rows[0] = new Object[]{ "", "", "Location", "", "Year", "Flag" };
      rows[1] = new Object[]{ "", "Status", "", "", "", "" };

      String[] groupNames = { "Drywall Installers", "Foreman", "General Workers", "Lawyers" };
      String[] statusNames = { "Correctly Done", "Incorrectly Done", "Requires Replacement" };

      for(int g = 0; g < GROUPS; g++) {
         for(int r = 0; r < ROWS_PER_GROUP; r++) {
            int row = HEADER_ROWS + g * ROWS_PER_GROUP + r;
            String workerType = r == 0 ? groupNames[g] : "";
            rows[row] = new Object[]{ workerType, statusNames[r], "2", "", "1",
                                      hasValue.test(row) ? "X" : "" };
         }
      }

      DefaultTableLens raw = new DefaultTableLens(rows);
      raw.setHeaderRowCount(HEADER_ROWS);

      for(int g = 0; g < GROUPS; g++) {
         raw.setSpan(HEADER_ROWS + g * ROWS_PER_GROUP, 0, new Dimension(1, ROWS_PER_GROUP));
      }

      return new VSTableLens(raw);
   }

   private static Viewsheet viewsheet() {
      Viewsheet vs = Mockito.mock(Viewsheet.class);
      when(vs.getDisplayRowHeight(Mockito.eq(true), anyString(), anyInt())).thenReturn(ROW_HEIGHT);
      when(vs.getDisplayRowHeight(Mockito.eq(false), anyString())).thenReturn(ROW_HEIGHT);
      return vs;
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

   /** The table kinds the bug #76829 fixtures run against. */
   enum Kind { CALC, CROSSTAB }

   private static TestExporter exporter() {
      return exporter(Kind.CALC);
   }

   /**
    * A 248px-high table with 2 header rows of the given kind. The crosstab
    * mock has 2 runtime column headers, so {@code hrow == 2}, matching the calc
    * table's {@code getHeaderRowCount() == 2}.
    */
   private static TestExporter exporter(Kind kind) {
      if(kind == Kind.CROSSTAB) {
         CrosstabVSAssemblyInfo info = Mockito.mock(CrosstabVSAssemblyInfo.class);
         when(info.isTitleVisible()).thenReturn(false);

         VSCrosstabInfo crosstabInfo = Mockito.mock(VSCrosstabInfo.class);
         when(crosstabInfo.getRuntimeColHeaders()).thenReturn(new DataRef[HEADER_ROWS]);

         CrosstabVSAssembly table = Mockito.mock(CrosstabVSAssembly.class);
         when(table.getInfo()).thenReturn(info);
         when(table.getVSAssemblyInfo()).thenReturn(info);
         when(table.getVSCrosstabInfo()).thenReturn(crosstabInfo);
         when(table.getPixelSize()).thenReturn(new Dimension(PIXEL_WIDTH, PIXEL_HEIGHT));
         when(table.getName()).thenReturn("Crosstab1");
         Viewsheet vs = viewsheet();
         when(table.getViewsheet()).thenReturn(vs);

         return new TestExporter(table);
      }

      CalcTableVSAssemblyInfo info = Mockito.mock(CalcTableVSAssemblyInfo.class);
      when(info.getHeaderRowCount()).thenReturn(HEADER_ROWS);
      when(info.isTitleVisible()).thenReturn(false);

      CalcTableVSAssembly table = Mockito.mock(CalcTableVSAssembly.class);
      when(table.getInfo()).thenReturn(info);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getPixelSize()).thenReturn(new Dimension(PIXEL_WIDTH, PIXEL_HEIGHT));
      when(table.getName()).thenReturn("FreehandTable2");
      Viewsheet vs = viewsheet();
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
      private final TableDataVSAssembly table;

      TestExporter(TableDataVSAssembly table) {
         this.table = table;
         setMatchLayout(true);
      }

      int regionRowCount(TableLens data) {
         return getRegionRowCount(table, data);
      }
   }
}
