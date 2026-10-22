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
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    * #79030: an explicit "_TABLE_ROOT_" row/column group (the only way to attach an
    * expanding group cell to the table root as an independent list) must survive the
    * default-group normalization done when the lens is built; an unknown name is still
    * reset, and merge groups are unchanged.
    */
   @Test
   void keepsRootGroupForRowAndColGroupButStillResetsUnknownNames() throws Exception {
      TableLayout layout = rootGroupLayout(2, TableCellBinding.EXPAND_V);
      TableCellBinding cell = (TableCellBinding) layout.getCellBinding(0, 1);
      cell.setRowGroup(CalcTableLens.ROOT_GROUP);
      cell.setColGroup(CalcTableLens.ROOT_GROUP);
      cell.setMergeRowGroup(CalcTableLens.ROOT_GROUP);
      cell.setMergeColGroup("NoSuchCell");
      TableCellBinding other = (TableCellBinding) layout.getCellBinding(0, 0);
      other.setRowGroup("NoSuchCell");
      other.setColGroup("NoSuchCell");

      CalcTableLens clens = fillLens(layout);

      assertEquals(CalcTableLens.ROOT_GROUP, clens.getRowGroup(0, 1));
      assertEquals(CalcTableLens.ROOT_GROUP, clens.getColGroup(0, 1));
      // the default group resolves to a concrete parent cell once the lens is built, so
      // assert the invalid names are gone rather than the literal default
      assertNotEquals(CalcTableLens.ROOT_GROUP, clens.getMergeRowGroup(0, 1));
      assertNotEquals("NoSuchCell", clens.getMergeColGroup(0, 1));
      assertNotEquals("NoSuchCell", clens.getRowGroup(0, 0));
      assertNotEquals("NoSuchCell", clens.getColGroup(0, 0));
   }

   @Test
   void buildTreeDoesNotRewriteRootGroupOnTheStoredLayout() {
      TableLayout layout = rootGroupLayout(2, TableCellBinding.EXPAND_V);
      TableCellBinding cell = (TableCellBinding) layout.getCellBinding(0, 1);
      cell.setRowGroup(CalcTableLens.ROOT_GROUP);

      LayoutTool.buildTree(layout);

      assertEquals(CalcTableLens.ROOT_GROUP, cell.getRowGroup());
   }

   /**
    * #79030: two expanding row-group cells side by side render as independent lists (row
    * count is the larger list) when the second is attached to the root, and as nested
    * groups (the product) when it keeps the default group.
    */
   @Test
   void rootRowGroupRendersIndependentListsInsteadOfTheProduct() {
      TableLayout layout = rootGroupLayout(2, TableCellBinding.EXPAND_V);
      ((TableCellBinding) layout.getCellBinding(0, 1)).setRowGroup(CalcTableLens.ROOT_GROUP);
      assertEquals(3, renderedRows(layout));

      layout = rootGroupLayout(2, TableCellBinding.EXPAND_V);
      assertEquals(6, renderedRows(layout));
   }

   @Test
   void rootColGroupRendersIndependentListsInsteadOfTheProduct() {
      TableLayout layout = rootGroupLayout(2, TableCellBinding.EXPAND_H);
      ((TableCellBinding) layout.getCellBinding(1, 0)).setColGroup(CalcTableLens.ROOT_GROUP);
      assertEquals(3, renderedCols(layout));

      layout = rootGroupLayout(2, TableCellBinding.EXPAND_H);
      assertEquals(6, renderedCols(layout));
   }

   private static int renderedRows(TableLayout layout) {
      return render(layout).getRowCount();
   }

   private static int renderedCols(TableLayout layout) {
      return render(layout).getColCount();
   }

   /** Fills the lens for the layout and evaluates it over the full A x B cross product. */
   private static RuntimeCalcTableLens render(TableLayout layout) {
      DefaultTableLens base = crossProductBase();
      CalcTableLens clens = fillLens(layout, base);
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      CalcTableVSAssembly assembly = lastAssembly;
      assembly.setScriptEnv(env);
      assembly.setScriptTable(base);
      clens.setScriptTable(base);
      return clens.process();
   }

   private static CalcTableLens fillLens(TableLayout layout) {
      return fillLens(layout, crossProductBase());
   }

   private static CalcTableVSAssembly lastAssembly;

   private static CalcTableLens fillLens(TableLayout layout, DefaultTableLens base) {
      Viewsheet viewsheet = new Viewsheet();
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(viewsheet, "CalcTable1");
      assembly.setTableLayout(layout);
      viewsheet.addAssembly(assembly);
      lastAssembly = assembly;
      LayoutTool.fillCalcTableLens(assembly, base, new VariableTable(), false);
      return (CalcTableLens) assembly.getBaseTable();
   }

   /** Column A has 3 distinct values, column B has 2, over all 6 combinations. */
   private static DefaultTableLens crossProductBase() {
      return new DefaultTableLens(new Object[][] {
         { "A", "B" },
         { "a1", "b1" }, { "a1", "b2" },
         { "a2", "b1" }, { "a2", "b2" },
         { "a3", "b1" }, { "a3", "b2" }
      });
   }

   /** Two group cells: side by side for EXPAND_V, stacked in one column for EXPAND_H. */
   private static TableLayout rootGroupLayout(int count, int expansion) {
      DefaultTableLens base = crossProductBase();
      TableLayout layout = new TableLayout();
      boolean vertical = expansion == TableCellBinding.EXPAND_V;
      layout.setColCount(vertical ? count : 1);
      TableDataPath rpath = base.getDescriptor().getRowDataPath(0);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(vertical ? 1 : count);
      layout.addRegion(rpath, region);

      String[] columns = { "A", "B" };
      String[] names = { "RG", "CG" };

      for(int i = 0; i < count; i++) {
         TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, columns[i]);
         cell.setBType(TableCellBinding.GROUP);
         cell.setExpansion(expansion);
         cell.setCellName(names[i]);
         layout.setCellBinding(vertical ? 0 : i, vertical ? i : 0, cell);
      }

      return layout;
   }
}
