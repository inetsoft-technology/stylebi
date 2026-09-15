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
package inetsoft.report;

import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for #75747 / #75801: converting a crosstab to a freehand table
 * generates a {@code toList(data['Col'], ...)} expression per row/column GROUP cell,
 * where "Col" is the physical column name backing the regenerated crosstab's data.
 * {@code LayoutTool.getGroupColumnName()} decides that name from a cell binding's
 * value, which can hold either the physical column name or the dimension's display
 * full name (e.g. "None(Month(ndate))") depending on how the dimension was bound.
 * Guessing wrong collapses the group cell's expansion to zero rows (#75747), and
 * "fixing" it by always stripping the wrapper breaks dimensions whose physical
 * column genuinely is named with its display form, such as "Year(Date)" (#75801).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LayoutToolTest {
   @Test
   void usesRawValueWhenItIsAlreadyTheOuterDateDimensionsPhysicalColumn() throws Exception {
      // #75801 (CroTable7): "Year(Date)" is a sole/outer date GROUP dimension whose
      // crosstab column is genuinely named with its display form -- must be used as-is.
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "Year(Date)", "Sum(Total)" },
         { "2022", 100.0 }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "Year(Date)");
      assertEquals("Year(Date)", getGroupColumnName(base, cell));
   }

   @Test
   void unwrapsDisplayNameForNestedDateDimension() throws Exception {
      // #75747 (GroupAndTab): "None(Month(ndate))" is the display full name for a
      // date dimension nested under another group; the crosstab's actual column is
      // the unwrapped "Month(ndate))".
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "state", "Month(ndate)", "Sum(eid)" },
         { "Others", "2022-01", 5.0 }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("Month(ndate)", getGroupColumnName(base, cell));
   }

   @Test
   void unwrapsDisplayNameForMultipleNestedPassthroughDateDimensions() throws Exception {
      // #75801 (Special2): several nested date/passthrough dimensions, each whose
      // display full name must be unwrapped to its own physical column.
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "NDateTime", "NDate", "NTime", "Sum(x)" },
         { "2022-01-01", "2022-01-01", "10:00", 3.0 }
      });

      assertEquals("NDateTime", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NDateTime)")));
      assertEquals("NDate", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NDate)")));
      assertEquals("NTime", getGroupColumnName(base,
         new TableCellBinding(CellBinding.BIND_COLUMN, "None(NTime)")));
   }

   @Test
   void returnsRawValueUncheckedWhenBaseIsNull() throws Exception {
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("None(Month(ndate))", getGroupColumnName(null, cell));
   }

   @Test
   void returnsRawValueWhenNeitherFormMatchesAnyColumn() throws Exception {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "unrelated_column" },
         { "x" }
      });

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "None(Month(ndate))");
      assertEquals("None(Month(ndate))", getGroupColumnName(base, cell));
   }

   private static String getGroupColumnName(TableLens base, TableCellBinding cell) throws Exception {
      Method method = LayoutTool.class.getDeclaredMethod(
         "getGroupColumnName", TableLens.class, TableCellBinding.class);
      method.setAccessible(true);
      return (String) method.invoke(null, base, cell);
   }

   /**
    * Regression guard for the "[Ljava.lang.Object;@&lt;hash&gt;" defect: a plain
    * detail-grouping cell (no expand, no ancestor group), in the non-crosstab-supported
    * branch of createCalcBindingExpression, must be wrapped in none(...) the same way
    * its crosstab-supported sibling branch already wraps the identical DETAIL bType.
    * Without the wrapper, CalcTableLens/CellRange.getCollectionValue always returns a
    * raw Object[] for calc-table array lookups, and an unwrapped cell renders that
    * array's toString() instead of a scalar value.
    */
   @Test
   void wrapsPlainDetailCellExpressionInNoneToAvoidLeakingARawArray() throws Exception {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "EMPLOYEE_ID" },
         { 5 },
         { 7 }
      });

      TableLayout layout = new TableLayout();
      layout.setColCount(1);
      TableDataDescriptor desc = base.getDescriptor();
      TableDataPath rpath = desc.getRowDataPath(0);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(1);
      layout.addRegion(rpath, region);

      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "EMPLOYEE_ID");
      cell.setBType(TableCellBinding.DETAIL);
      layout.setCellBinding(0, 0, cell);

      Viewsheet viewsheet = new Viewsheet();
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(viewsheet, "CalcTable1");
      assembly.setTableLayout(layout);
      viewsheet.addAssembly(assembly);

      Method method = LayoutTool.class.getDeclaredMethod("fillCalcTableLens",
         FormulaTable.class, TableLens.class, VariableTable.class, boolean.class);
      method.setAccessible(true);
      method.invoke(null, assembly, base, new VariableTable(), false);

      CalcTableLens clens = (CalcTableLens) assembly.getBaseTable();
      String exp = clens.getFormula(0, 0);
      assertTrue(exp != null && exp.startsWith("none("),
         "expected expression to be wrapped in none(...), was: " + exp);
   }

   /**
    * Regression guard for #76676 (VCT-012): a SUMMARY calc cell's mergeRowGroup was
    * silently ignored by the aggregate-expression builder -- only the cell's own nearest
    * native rowGroup ancestor (here StateGrp) was ever consulted, so a cell placed inside
    * StateGrp's row could not escape its scope up to a named coarser ancestor (RegionGrp)
    * the way the bug report's RegionAmtForState cell needed to. The generated expression
    * must now qualify on RegionGrp only, not on StateGrp.
    */
   @Test
   void mergeRowGroupTruncatesAggregationScopeToNamedAncestor() throws Exception {
      TableLayout layout = new TableLayout();
      DefaultTableLens base = groupedBase();
      layout.setColCount(2);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(2);
      layout.addRegion(base.getDescriptor().getRowDataPath(0), region);

      TableCellBinding regionGroup = TableCellBinding.getGroupBinding("Region");
      regionGroup.setCellName("RegionGrp");
      layout.setCellBinding(0, 0, regionGroup);

      TableCellBinding stateGroup = TableCellBinding.getGroupBinding("State");
      stateGroup.setCellName("StateGrp");
      stateGroup.setRowGroup("RegionGrp");
      layout.setCellBinding(1, 0, stateGroup);

      TableCellBinding summary = TableCellBinding.getSummaryBinding("Total");
      summary.setFormula("Sum");
      summary.setRowGroup("StateGrp");
      summary.setMergeRowGroup("RegionGrp");
      layout.setCellBinding(1, 1, summary);

      String exp = fillCalcTableLens(layout, base, false).getFormula(1, 1);
      assertTrue(exp.contains("RegionGrp"), "expected expression to scope to RegionGrp, was: " + exp);
      assertFalse(exp.contains("StateGrp"), "expected StateGrp qualifier to be dropped, was: " + exp);
   }

   /**
    * Column-axis mirror of the above, covering sibling bug #76675 (VCT-013) in the same
    * fix: mergeColGroup must equally truncate the native colGroup ancestor chain.
    */
   @Test
   void mergeColGroupTruncatesAggregationScopeToNamedAncestor() throws Exception {
      TableLayout layout = new TableLayout();
      DefaultTableLens base = groupedBase();
      layout.setColCount(2);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(2);
      layout.addRegion(base.getDescriptor().getRowDataPath(0), region);

      TableCellBinding quarterGroup = TableCellBinding.getGroupBinding("Region");
      quarterGroup.setCellName("QuarterGrp");
      layout.setCellBinding(0, 0, quarterGroup);

      TableCellBinding monthGroup = TableCellBinding.getGroupBinding("State");
      monthGroup.setCellName("MonthGrp");
      monthGroup.setColGroup("QuarterGrp");
      layout.setCellBinding(0, 1, monthGroup);

      TableCellBinding summary = TableCellBinding.getSummaryBinding("Total");
      summary.setFormula("Sum");
      summary.setColGroup("MonthGrp");
      summary.setMergeColGroup("QuarterGrp");
      layout.setCellBinding(1, 1, summary);

      String exp = fillCalcTableLens(layout, base, false).getFormula(1, 1);
      assertTrue(exp.contains("QuarterGrp"), "expected expression to scope to QuarterGrp, was: " + exp);
      assertFalse(exp.contains("MonthGrp"), "expected MonthGrp qualifier to be dropped, was: " + exp);
   }

   /**
    * Regression guard for the col-axis range arithmetic bug found in review of #76676: when a
    * SUMMARY cell has *both* a row group and a col group (the realistic row+column
    * crosstab-style shape #76675 targets), the mergeColGroup truncation must search/truncate
    * only the just-appended col-axis portion of the shared holders/locs list -- not a
    * same-sized slice of the row-axis portion that happens to sit at the front of the list.
    * With the old `[0, holders.size() - rowGroupCount)` range, this cell's mergeColGroup would
    * be searched for among RegionGrp/StateGrp (the row axis) instead of QuarterGrp/MonthGrp
    * (the col axis), and -- since QuarterGrp is not one of the cell's row-axis ancestors --
    * incorrectly throw MessageException instead of scoping to it. The row-axis chain
    * (RegionGrp, StateGrp) must also come through untouched, since no mergeRowGroup was set.
    */
   @Test
   void mergeColGroupTruncationIsScopedToColAxisWhenCellAlsoHasARowGroup() throws Exception {
      TableLayout layout = new TableLayout();
      DefaultTableLens base = groupedBase();
      layout.setColCount(3);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(3);
      layout.addRegion(base.getDescriptor().getRowDataPath(0), region);

      TableCellBinding regionGroup = TableCellBinding.getGroupBinding("Region");
      regionGroup.setCellName("RegionGrp");
      layout.setCellBinding(1, 0, regionGroup);

      TableCellBinding stateGroup = TableCellBinding.getGroupBinding("State");
      stateGroup.setCellName("StateGrp");
      stateGroup.setRowGroup("RegionGrp");
      layout.setCellBinding(2, 0, stateGroup);

      TableCellBinding quarterGroup = TableCellBinding.getGroupBinding("Region");
      quarterGroup.setCellName("QuarterGrp");
      layout.setCellBinding(0, 1, quarterGroup);

      TableCellBinding monthGroup = TableCellBinding.getGroupBinding("State");
      monthGroup.setCellName("MonthGrp");
      monthGroup.setColGroup("QuarterGrp");
      layout.setCellBinding(0, 2, monthGroup);

      TableCellBinding summary = TableCellBinding.getSummaryBinding("Total");
      summary.setFormula("Sum");
      summary.setRowGroup("StateGrp");
      summary.setColGroup("MonthGrp");
      summary.setMergeColGroup("QuarterGrp");
      layout.setCellBinding(2, 2, summary);

      String exp = fillCalcTableLens(layout, base, false).getFormula(2, 2);
      assertTrue(exp.contains("QuarterGrp"), "expected expression to scope to QuarterGrp, was: " + exp);
      assertFalse(exp.contains("MonthGrp"), "expected MonthGrp qualifier to be dropped, was: " + exp);
      assertTrue(exp.contains("RegionGrp"), "expected row-axis RegionGrp to be untouched, was: " + exp);
      assertTrue(exp.contains("StateGrp"), "expected row-axis StateGrp to be untouched, was: " + exp);
   }

   /**
    * Same truncation must be visible to the crossTabSupported branch (createCrosstabCalcExpression),
    * since it consumes the same groups/gnames arrays built upstream of the crossTabSupported check --
    * confirms the fix isn't limited to the plain-SUMMARY expression path.
    */
   @Test
   void mergeRowGroupTruncationAppliesToCrosstabSupportedBranchToo() throws Exception {
      TableLayout layout = new TableLayout();
      DefaultTableLens base = groupedBase();
      layout.setColCount(2);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(2);
      layout.addRegion(base.getDescriptor().getRowDataPath(0), region);

      TableCellBinding regionGroup = TableCellBinding.getGroupBinding("Region");
      regionGroup.setCellName("RegionGrp");
      layout.setCellBinding(0, 0, regionGroup);

      TableCellBinding stateGroup = TableCellBinding.getGroupBinding("State");
      stateGroup.setCellName("StateGrp");
      stateGroup.setRowGroup("RegionGrp");
      layout.setCellBinding(1, 0, stateGroup);

      TableCellBinding summary = TableCellBinding.getSummaryBinding("Total");
      summary.setFormula("Sum");
      summary.setRowGroup("StateGrp");
      summary.setMergeRowGroup("RegionGrp");
      layout.setCellBinding(1, 1, summary);

      String exp = fillCalcTableLens(layout, base, true).getFormula(1, 1);
      assertTrue(exp.contains("RegionGrp"), "expected expression to scope to RegionGrp, was: " + exp);
      assertFalse(exp.contains("StateGrp"), "expected StateGrp qualifier to be dropped, was: " + exp);
   }

   /**
    * If mergeRowGroup names a cell that exists somewhere else in the layout (so
    * validateLayout's own nonexistent-name downgrade to DEFAULT_GROUP does not intercept
    * it first) but is not actually an ancestor of this cell's own native rowGroup chain,
    * the fix must fail loud instead of silently resolving to the cell's own nearest group.
    */
   @Test
   void mergeRowGroupNamingNonAncestorCellFailsLoud() throws Exception {
      TableLayout layout = new TableLayout();
      DefaultTableLens base = groupedBase();
      layout.setColCount(2);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(2);
      layout.addRegion(base.getDescriptor().getRowDataPath(0), region);

      TableCellBinding regionGroup = TableCellBinding.getGroupBinding("Region");
      regionGroup.setCellName("RegionGrp");
      layout.setCellBinding(0, 0, regionGroup);

      // a real cell name elsewhere in the layout, unrelated to the summary cell's own chain
      TableCellBinding siblingGroup = TableCellBinding.getGroupBinding("Total");
      siblingGroup.setCellName("SiblingGrp");
      layout.setCellBinding(0, 1, siblingGroup);

      TableCellBinding stateGroup = TableCellBinding.getGroupBinding("State");
      stateGroup.setCellName("StateGrp");
      stateGroup.setRowGroup("RegionGrp");
      layout.setCellBinding(1, 0, stateGroup);

      TableCellBinding summary = TableCellBinding.getSummaryBinding("Total");
      summary.setFormula("Sum");
      summary.setRowGroup("StateGrp");
      summary.setMergeRowGroup("SiblingGrp");
      layout.setCellBinding(1, 1, summary);

      InvocationTargetException ex = assertThrows(InvocationTargetException.class,
         () -> fillCalcTableLens(layout, base, false));
      assertTrue(ex.getCause() instanceof MessageException,
         "expected a MessageException, was: " + ex.getCause());
      assertTrue(ex.getCause().getMessage().contains("SiblingGrp"),
         "expected the error to name the invalid mergeRowGroup value, was: " +
         ex.getCause().getMessage());
   }

   /**
    * Critical regression guard for a bug in this PR's own first fix commit, flagged by
    * GitHub's automated review of #5257 (inline comment on LayoutTool.java:2404): every
    * drag-and-dropped composer/wizard cell is created by
    * TableLayoutHandler.createDefalutCellBinding() with mergeRowGroup/mergeColGroup set to
    * the TableCellBinding.DEFAULT_GROUP sentinel, never left null. applyDefaultGroups()
    * resolves that sentinel to the cell's own runtime name before truncateAtMergeGroup ever
    * runs, so the original null/DEFAULT_GROUP-only early-return in truncateAtMergeGroup
    * missed this case entirely: it would search the cell's own ancestor chain for the
    * cell's own name, never find it, and throw MessageException for essentially any
    * ordinary SUMMARY cell with an ancestor group. This must behave identically to a
    * SUMMARY cell that never set mergeRowGroup/mergeColGroup at all.
    */
   @Test
   void mergeGroupExplicitlySetToDefaultGroupSentinelBehavesAsUnsetWithAncestor() throws Exception {
      TableLayout layout = new TableLayout();
      DefaultTableLens base = groupedBase();
      layout.setColCount(2);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(2);
      layout.addRegion(base.getDescriptor().getRowDataPath(0), region);

      TableCellBinding regionGroup = TableCellBinding.getGroupBinding("Region");
      regionGroup.setCellName("RegionGrp");
      layout.setCellBinding(0, 0, regionGroup);

      TableCellBinding stateGroup = TableCellBinding.getGroupBinding("State");
      stateGroup.setCellName("StateGrp");
      stateGroup.setRowGroup("RegionGrp");
      layout.setCellBinding(1, 0, stateGroup);

      TableCellBinding summary = TableCellBinding.getSummaryBinding("Total");
      summary.setFormula("Sum");
      summary.setRowGroup("StateGrp");
      // as TableLayoutHandler.createDefalutCellBinding() actually sets it -- not left null,
      // and not set to an explicit ancestor/sibling name.
      summary.setMergeRowGroup(TableCellBinding.DEFAULT_GROUP);
      summary.setMergeColGroup(TableCellBinding.DEFAULT_GROUP);
      layout.setCellBinding(1, 1, summary);

      String exp = fillCalcTableLens(layout, base, false).getFormula(1, 1);
      assertTrue(exp.contains("StateGrp"),
         "expected expression to scope to the cell's own nearest group StateGrp, same as " +
         "if mergeRowGroup had never been set, was: " + exp);
   }

   /**
    * Same DEFAULT_GROUP-sentinel case as above, but on a flat summary cell with no
    * row/col group ancestor at all -- confirms the fix doesn't throw even when there is no
    * ancestor chain to fall back to.
    */
   @Test
   void mergeGroupExplicitlySetToDefaultGroupSentinelBehavesAsUnsetWithNoAncestor() throws Exception {
      TableLayout layout = new TableLayout();
      DefaultTableLens base = groupedBase();
      layout.setColCount(1);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(1);
      layout.addRegion(base.getDescriptor().getRowDataPath(0), region);

      TableCellBinding summary = TableCellBinding.getSummaryBinding("Total");
      summary.setFormula("Sum");
      // as TableLayoutHandler.createDefalutCellBinding() actually sets it, with no
      // rowGroup/colGroup ancestor present at all.
      summary.setMergeRowGroup(TableCellBinding.DEFAULT_GROUP);
      summary.setMergeColGroup(TableCellBinding.DEFAULT_GROUP);
      layout.setCellBinding(0, 0, summary);

      String exp = fillCalcTableLens(layout, base, false).getFormula(0, 0);
      assertTrue(exp != null && !exp.isEmpty(),
         "expected an aggregation expression to be generated without throwing, was: " + exp);
   }

   private static DefaultTableLens groupedBase() {
      return new DefaultTableLens(new Object[][] {
         { "Region", "State", "Total" },
         { "East", "NY", 10.0 }
      });
   }

   private static CalcTableLens fillCalcTableLens(TableLayout layout, TableLens base,
      boolean crossTabSupported) throws Exception
   {
      Viewsheet viewsheet = new Viewsheet();
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(viewsheet, "CalcTable1");
      assembly.setTableLayout(layout);
      viewsheet.addAssembly(assembly);

      Method method = LayoutTool.class.getDeclaredMethod("fillCalcTableLens",
         FormulaTable.class, TableLens.class, VariableTable.class, boolean.class);
      method.setAccessible(true);
      method.invoke(null, assembly, base, new VariableTable(), crossTabSupported);

      return (CalcTableLens) assembly.getBaseTable();
   }
}
