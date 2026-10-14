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
package inetsoft.uql.viewsheet;

import inetsoft.report.BaseLayout;
import inetsoft.report.CellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLayout;
import inetsoft.report.lens.DefaultTableLens;
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
 * Bug #78282: renaming a calc field must rewrite the calc table's bound cells through the
 * regular dependents sweep ({@code renameBindingCol}), not only when the caller names the calc
 * table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAssemblyTest {
   private static TableCellBinding cell(int type, int btype, String value) {
      TableCellBinding cell = new TableCellBinding(type, value);
      cell.setBType(btype);
      return cell;
   }

   private static CalcTableVSAssembly calcTable(TableCellBinding... cells) {
      TableLayout layout = new TableLayout();
      layout.setColCount(cells.length);
      TableDataPath rpath = new DefaultTableLens(new Object[][] { { "a" } })
         .getDescriptor().getRowDataPath(0);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(1);
      layout.addRegion(rpath, region);

      for(int i = 0; i < cells.length; i++) {
         layout.setCellBinding(0, i, cells[i]);
      }

      CalcTableVSAssembly assembly = new CalcTableVSAssembly(new Viewsheet(), "CalcTable1");
      assembly.setTableLayout(layout);
      return assembly;
   }

   @Test
   void renameBindingColRewritesGroupAndSummaryCells() {
      CalcTableVSAssembly assembly = calcTable(
         cell(CellBinding.BIND_COLUMN, CellBinding.GROUP, "NetRevenue"),
         cell(CellBinding.BIND_COLUMN, CellBinding.SUMMARY, "NetRevenue"),
         cell(CellBinding.BIND_COLUMN, CellBinding.DETAIL, "Other"));

      assembly.renameBindingCol("NetRevenue", "NetRevenue2");

      TableLayout layout = assembly.getTableLayout();
      assertEquals("NetRevenue2", layout.getCellBinding(0, 0).getValue());
      assertEquals("NetRevenue2", layout.getCellBinding(0, 1).getValue());
      assertEquals("Other", layout.getCellBinding(0, 2).getValue());
   }

   @Test
   void renameBindingColLeavesTextCellsAlone() {
      CalcTableVSAssembly assembly = calcTable(
         cell(CellBinding.BIND_TEXT, CellBinding.DETAIL, "NetRevenue"),
         cell(CellBinding.BIND_COLUMN, CellBinding.DETAIL, "NetRevenue"));

      assembly.renameBindingCol("NetRevenue", "NetRevenue2");

      TableLayout layout = assembly.getTableLayout();
      assertEquals("NetRevenue", layout.getCellBinding(0, 0).getValue());
      assertEquals("NetRevenue2", layout.getCellBinding(0, 1).getValue());
   }

   @Test
   void removeBindingColKeepsTextAndFormulaCellsClearsBoundCells() {
      CalcTableVSAssembly assembly = calcTable(
         cell(CellBinding.BIND_TEXT, CellBinding.DETAIL, "NetRevenue"),
         cell(CellBinding.BIND_FORMULA, CellBinding.DETAIL, "NetRevenue"),
         cell(CellBinding.BIND_COLUMN, CellBinding.GROUP, "NetRevenue"),
         cell(CellBinding.BIND_COLUMN, CellBinding.SUMMARY, "NetRevenue"),
         cell(CellBinding.BIND_COLUMN, CellBinding.DETAIL, "Other"));

      assembly.removeBindingCol("NetRevenue");

      TableLayout layout = assembly.getTableLayout();
      assertEquals(CellBinding.BIND_TEXT, layout.getCellBinding(0, 0).getType());
      assertEquals("NetRevenue", layout.getCellBinding(0, 0).getValue());
      assertEquals(CellBinding.BIND_FORMULA, layout.getCellBinding(0, 1).getType());
      assertEquals("NetRevenue", layout.getCellBinding(0, 1).getValue());
      assertNull(layout.getCellBinding(0, 2).getValue());
      assertNull(layout.getCellBinding(0, 3).getValue());
      assertEquals("Other", layout.getCellBinding(0, 4).getValue());
   }

   @Test
   void changeCalcTypeKeepsTextCellClearsBoundCell() {
      CalcTableVSAssembly assembly = calcTable(
         cell(CellBinding.BIND_TEXT, CellBinding.DETAIL, "NetRevenue"),
         cell(CellBinding.BIND_COLUMN, CellBinding.GROUP, "NetRevenue"));

      assembly.changeCalcType("NetRevenue", null);

      TableLayout layout = assembly.getTableLayout();
      assertEquals("NetRevenue", layout.getCellBinding(0, 0).getValue());
      assertNull(layout.getCellBinding(0, 1).getValue());
   }
}
