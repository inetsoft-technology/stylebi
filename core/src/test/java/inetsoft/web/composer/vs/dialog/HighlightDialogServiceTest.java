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
package inetsoft.web.composer.vs.dialog;

import inetsoft.report.TableDataPath;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug 76307: a crosstab with 2+ stacked aggregate measures had no way to resolve a highlight
 * call's {@code colName} to that measure's own cell -- {@code lens.getTableDataPath(row, col)} is
 * a pure function of {@code (row, col)} alone, so two calls naming different measures but both
 * omitting row/col always collapsed onto the SAME {@code TableDataPath}, and every measure but the
 * first lost its highlight.
 *
 * <p>Bug 76558 / VSH-001: a plain (non-crosstab) table's {@code colName}-only call had no
 * resolution at all -- {@code row}/{@code col} stayed at 0/0, landing on column 0's HEADER cell,
 * which a rendered DATA row never matches, so the highlight was inert on every column.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HighlightDialogServiceTest {
   private static VSAggregateRef aggregate(String column, AggregateFormula formula) {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setDataRef(new AttributeRef(column));
      ref.setFormula(formula);
      return ref;
   }

   // ── resolveStackedMeasureCell: pure unit coverage ──────────────────────────

   @Test
   void resolvesTheSecondStackedMeasureToOneRowPastTheFirstDataCell() {
      DataRef[] aggregates = {
         aggregate("PAID", AggregateFormula.SUM), aggregate("DISCOUNT", AggregateFormula.SUM),
         aggregate("QTY", AggregateFormula.SUM),
      };

      int[] cell = HighlightDialogService.resolveStackedMeasureCell(
         aggregates, false, 2, 1, "DISCOUNT");

      assertArrayEquals(new int[]{ 3, 1 }, cell, "stacked: row advances by the measure's index");
   }

   @Test
   void resolvesBySideBySideMeasureToAColumnOffsetInstead() {
      DataRef[] aggregates = {
         aggregate("PAID", AggregateFormula.SUM), aggregate("DISCOUNT", AggregateFormula.SUM),
         aggregate("QTY", AggregateFormula.SUM),
      };

      int[] cell = HighlightDialogService.resolveStackedMeasureCell(
         aggregates, true, 2, 1, "QTY");

      assertArrayEquals(new int[]{ 2, 3 }, cell, "side by side: col advances by the measure's index");
   }

   @Test
   void matchesByFullNameWhenTheBareColumnNameDoesNotMatch() {
      VSAggregateRef paid = aggregate("PAID", AggregateFormula.SUM);
      DataRef[] aggregates = { paid, aggregate("PAID", AggregateFormula.AVG) };

      int[] cell = HighlightDialogService.resolveStackedMeasureCell(
         aggregates, false, 1, 1, paid.getFullName());

      assertArrayEquals(new int[]{ 1, 1 }, cell);
   }

   @Test
   void leavesTheCellUnresolvedWhenColNameIsAbsent() {
      DataRef[] aggregates = {
         aggregate("PAID", AggregateFormula.SUM), aggregate("DISCOUNT", AggregateFormula.SUM),
      };

      assertNull(HighlightDialogService.resolveStackedMeasureCell(aggregates, false, 1, 1, null),
                 "no colName means nothing to resolve -- the existing fallback is left alone");
   }

   @Test
   void leavesTheCellUnresolvedWhenColNameNamesNoRuntimeAggregate() {
      DataRef[] aggregates = {
         aggregate("PAID", AggregateFormula.SUM), aggregate("DISCOUNT", AggregateFormula.SUM),
      };

      assertNull(HighlightDialogService.resolveStackedMeasureCell(
         aggregates, false, 1, 1, "NOT_A_MEASURE"));
   }

   @Test
   void leavesTheCellUnresolvedWithOnlyOneStackedMeasure() {
      DataRef[] aggregates = { aggregate("PAID", AggregateFormula.SUM) };

      assertNull(HighlightDialogService.resolveStackedMeasureCell(aggregates, false, 1, 1, "PAID"),
                 "a single measure has nothing to disambiguate -- leave the existing " +
                 "first-data-cell fallback alone");
   }

   // ── end-to-end against a real crosstab lens ────────────────────────────────

   /**
    * The direct regression test for the collapse: two measures, resolved via {@code colName}
    * through the real {@code CrossTabFilter}/{@code CrossFilterDataDescriptor} row/col-to-path
    * machinery (not a mock), must land on two DISTINCT {@code TableDataPath}s -- and each must be
    * the actual cell holding that measure's own summed value, not merely "a" different cell.
    */
   @Test
   void resolvedCellsForTwoStackedMeasuresAreDistinctRealDataCellsNotJustDifferentNumbers() {
      Object[][] data = {
         { "YEAR", "PAID", "DISCOUNT", "QTY" },
         { 2021, 100, 10, 5 },
         { 2022, 200, 20, 6 },
      };
      int[] rowh = { 0 };
      int[] colh = {};
      int[] dcol = { 1, 2, 3 };
      DataRef[] runtimeAggregates = {
         aggregate("PAID", AggregateFormula.SUM), aggregate("DISCOUNT", AggregateFormula.SUM),
         aggregate("QTY", AggregateFormula.SUM),
      };

      CrossTabFilter table = new CrossTabFilter(new DefaultTableLens(data), rowh, colh, dcol,
         new SumFormula[]{ new SumFormula(), new SumFormula(), new SumFormula() });

      int headerRowCount = table.getHeaderRowCount();
      int headerColCount = table.getHeaderColCount();

      int[] paidCell = HighlightDialogService.resolveStackedMeasureCell(
         runtimeAggregates, table.isSummarySideBySide(), headerRowCount, headerColCount, "PAID");
      int[] discountCell = HighlightDialogService.resolveStackedMeasureCell(
         runtimeAggregates, table.isSummarySideBySide(), headerRowCount, headerColCount,
         "DISCOUNT");

      assertNotNull(paidCell);
      assertNotNull(discountCell);
      assertFalse(java.util.Arrays.equals(paidCell, discountCell),
                  "PAID and DISCOUNT must resolve to different physical cells");

      TableDataPath paidPath = table.getDescriptor().getCellDataPath(paidCell[0], paidCell[1]);
      TableDataPath discountPath =
         table.getDescriptor().getCellDataPath(discountCell[0], discountCell[1]);

      assertNotNull(paidPath);
      assertNotNull(discountPath);
      assertNotEquals(paidPath, discountPath,
                       "the whole bug: before this fix both measures' calls resolved to the " +
                       "SAME TableDataPath, so their highlights collapsed into one HighlightGroup");

      // Not just structurally distinct -- each path's cell actually holds that measure's own
      // value for the first row group (2021), confirming the offset lands on the real measure,
      // not merely a different one.
      assertEquals(100.0, ((Number) table.getObject(paidCell[0], paidCell[1])).doubleValue(),
                   0.0001);
      assertEquals(10.0, ((Number) table.getObject(discountCell[0], discountCell[1])).doubleValue(),
                   0.0001);
   }

   // ── resolveNamedColumnCell: bug 76558 / VSH-001 ────────────────────────────

   /**
    * Bug 76558 / VSH-001: a plain (non-crosstab) table's {@code colName}-only highlight call was
    * never resolved to a real cell -- {@code row}/{@code col} stayed at their defaulted-to-0 input,
    * so {@code lens.getTableDataPath(row, col)} always landed on column 0's HEADER cell, and the
    * stored highlight then matched no rendered DATA row in any column, ever.
    */
   @Test
   void resolvesANamedColumnToTheFirstDataRowOnAPlainTable() {
      Object[][] data = {
         { "ORDER_ID", "STATE", "AMOUNT" },
         { 1, "NY", 100 },
         { 2, "CA", 200 },
      };
      DefaultTableLens table = new DefaultTableLens(data);

      int[] cell = HighlightDialogService.resolveNamedColumnCell(table, "STATE");

      assertArrayEquals(new int[]{ table.getHeaderRowCount(), 1 }, cell,
                         "STATE is column 1, and the resolved row is the first DATA row, not the " +
                         "header row");
   }

   @Test
   void resolvedNamedColumnCellIsARealDetailPathNotTheHeaderOfColumnZero() {
      Object[][] data = {
         { "ORDER_ID", "STATE", "AMOUNT" },
         { 1, "NY", 100 },
      };
      DefaultTableLens table = new DefaultTableLens(data);

      int[] cell = HighlightDialogService.resolveNamedColumnCell(table, "STATE");
      TableDataPath path = table.getDescriptor().getCellDataPath(cell[0], cell[1]);

      assertEquals(TableDataPath.DETAIL, path.getType(),
                   "the whole bug: unresolved colName landed on the HEADER path of column 0, " +
                   "which a rendered DETAIL row never matches");
      assertArrayEquals(new String[]{ "STATE" }, path.getPath());
   }

   @Test
   void leavesTheCellUnresolvedWhenTheNamedColumnDoesNotExist() {
      Object[][] data = { { "ORDER_ID", "STATE" }, { 1, "NY" } };
      DefaultTableLens table = new DefaultTableLens(data);

      assertNull(HighlightDialogService.resolveNamedColumnCell(table, "NOT_A_COLUMN"));
   }

   @Test
   void leavesTheCellUnresolvedWhenColNameIsAbsentForAPlainTable() {
      Object[][] data = { { "ORDER_ID", "STATE" }, { 1, "NY" } };
      DefaultTableLens table = new DefaultTableLens(data);

      assertNull(HighlightDialogService.resolveNamedColumnCell(table, null));
   }

   // ── Redmine 78261: highlights written during a date comparison hit the runtime clone ──

   private static VSChartAggregateRef chartAggregate(String column) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new AttributeRef(column));
      ref.setFormula(AggregateFormula.SUM);
      return ref;
   }

   private static VSChartInfo chartWith(VSChartAggregateRef design, boolean withRuntimeClone) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(design);

      if(withRuntimeClone) {
         info.setRuntimeDateComparisonRefs(new ChartRef[]{ (ChartRef) design.clone() });
      }

      return info;
   }

   private static HighlightDialogModel dialogModel(String measure, boolean rejectDerived,
                                                   String... names)
   {
      HighlightDialogModel model = new HighlightDialogModel();
      model.setMeasure(measure);
      model.setRejectDerivedMeasure(rejectDerived);
      HighlightModel[] highlights = new HighlightModel[names.length];

      for(int i = 0; i < names.length; i++) {
         highlights[i] = new HighlightModel();
         highlights[i].setName(names[i]);
      }

      model.setHighlights(highlights);
      return model;
   }

   private static final HighlightDialogService.HighlightConverter CONVERT = m -> {
      TextHighlight highlight = new TextHighlight();
      highlight.setName(m.getName());
      return highlight;
   };

   private static void assertHas(HighlightGroup group, String... names) {
      assertNotNull(group);
      assertEquals(names.length, group.getHighlightCount(HighlightGroup.DEFAULT_LEVEL));

      for(String name : names) {
         assertNotNull(group.getHighlight(name), name);
      }
   }

   @Test
   void highlightIsWrittenThroughToTheDesignRefAndSurvivesDroppingTheRuntimeRefs() throws Exception {
      VSChartAggregateRef design = chartAggregate("TOTAL");
      VSChartInfo info = chartWith(design, true);
      ChartRef runtime = info.getRuntimeDateComparisonRefs()[0];

      HighlightDialogService.applyChartHighlights(
         info, dialogModel(design.getFullName(), true, "Hot"), CONVERT);

      assertHas(design.getHighlightGroup(), "Hot");

      // the clone is what the dialog resolved first; it is dropped on clear / option switch
      info.setRuntimeDateComparisonRefs(null);
      assertHas(design.getHighlightGroup(), "Hot");
      assertNotNull(runtime);
   }

   @Test
   void replaceSemanticsAreMirroredToTheDesignRef() throws Exception {
      VSChartAggregateRef design = chartAggregate("TOTAL");
      VSChartInfo info = chartWith(design, true);
      String measure = design.getFullName();

      HighlightDialogService.applyChartHighlights(info, dialogModel(measure, true, "A"), CONVERT);
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ (ChartRef) design.clone() });
      HighlightDialogService.applyChartHighlights(info, dialogModel(measure, true, "B"), CONVERT);

      assertHas(design.getHighlightGroup(), "B");
   }

   @Test
   void deletingDuringTheComparisonAlsoClearsTheDesignRef() throws Exception {
      VSChartAggregateRef design = chartAggregate("TOTAL");
      String measure = design.getFullName();
      HighlightGroup existing = new HighlightGroup();
      TextHighlight pre = new TextHighlight();
      pre.setName("Pre");
      existing.addHighlight("Pre", pre);
      design.setHighlightGroup(existing);
      VSChartInfo info = chartWith(design, true);

      // the delete path sends the kept list, which is empty
      HighlightDialogService.applyChartHighlights(info, dialogModel(measure, true), CONVERT);
      info.setRuntimeDateComparisonRefs(null);

      assertTrue(design.getHighlightGroup() == null || design.getHighlightGroup().isEmpty(),
                 "the persisted highlight must not resurrect after the comparison is cleared");
   }

   @Test
   void noRuntimeRefsMeansASingleWriteAndNoRefusal() throws Exception {
      VSChartAggregateRef design = chartAggregate("TOTAL");
      VSChartInfo info = chartWith(design, false);

      HighlightDialogService.applyChartHighlights(
         info, dialogModel(design.getFullName(), true, "Hot"), CONVERT);

      assertHas(design.getHighlightGroup(), "Hot");
   }

   @Test
   void derivedOnlyMeasureIsRefusedOnTheWizPathButAcceptedOnTheGuiPath() throws Exception {
      VSChartAggregateRef design = chartAggregate("TOTAL");
      VSChartInfo info = chartWith(design, false);
      VSChartAggregateRef derived = chartAggregate("TOTAL_DERIVED");
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ derived });

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> HighlightDialogService.applyChartHighlights(
            info, dialogModel(derived.getFullName(), true, "Hot"), CONVERT));
      assertTrue(e.getMessage().contains(derived.getFullName()));

      assertDoesNotThrow(() -> HighlightDialogService.applyChartHighlights(
         info, dialogModel(derived.getFullName(), false, "Hot"), CONVERT));
   }
}
