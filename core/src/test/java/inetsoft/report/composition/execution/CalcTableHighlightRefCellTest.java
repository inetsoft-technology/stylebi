/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #75509: a highlight condition on an expanded freehand table cell that references another
 * named cell must read that cell's value from the same row. The referenced cell sits in fewer
 * groups than the highlighted cell, so its context must match as a subset, not only by equality.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableHighlightRefCellTest {
   @Test
   void referencedCellValueComesFromTheSameRow() throws Exception {
      RuntimeCalcTableLens lens = runQuery();
      Map<Object, Object> totalByRegion = new LinkedHashMap<>();
      StringBuilder dump = new StringBuilder();

      for(int r = 0; r < lens.getRowCount(); r++) {
         dump.append(r).append(':');

         for(int c = 0; c < lens.getColCount(); c++) {
            dump.append(" [").append(lens.getObject(r, c)).append(']');
         }

         dump.append('\n');
      }

      // the highlighted cell is Sum(Sales) in the expanded Category columns (col 1 and 2)
      for(int r = 0; r < lens.getRowCount(); r++) {
         Object region = lens.getObject(r, 0);

         if("East".equals(region) || "West".equals(region)) {
            Map kv = lens.getKeyValuePairs(r, 1, Set.of("Total"));
            totalByRegion.put(region, kv.get("Total"));
         }
      }

      assertEquals(Map.of("East", 10.0, "West", 100.0), totalByRegion,
                   "table:\n" + dump + "referenced Total per region");
   }

   private static RuntimeCalcTableLens runQuery() throws Exception {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);

      TableLayout layout = cassembly.getTableLayout();
      layout.setColCount(3);
      TableCellBinding category = TableCellBinding.getGroupBinding("Category");
      category.setExpansion(GroupableCellBinding.EXPAND_H);
      layout.setCellBinding(0, 1, category);
      TableCellBinding region = TableCellBinding.getGroupBinding("Region");
      region.setExpansion(GroupableCellBinding.EXPAND_V);
      layout.setCellBinding(1, 0, region);
      TableCellBinding sales = new TableCellBinding(CellBinding.BIND_COLUMN, "Sales");
      sales.setBType(CellBinding.SUMMARY);
      sales.setFormula("Sum");
      sales.setCellName("SalesCell");
      sales.setRowGroup("Region");
      sales.setColGroup("Category");
      layout.setCellBinding(1, 1, sales);
      TableCellBinding total = new TableCellBinding(CellBinding.BIND_COLUMN, "Weight");
      total.setBType(CellBinding.SUMMARY);
      total.setFormula("Sum");
      total.setCellName("Total");
      total.setRowGroup("Region");
      layout.setCellBinding(1, 2, total);

      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "Region", "Category", "Sales", "Weight" },
         { "East", "A", 1.0, 4.0 }, { "East", "B", 2.0, 6.0 },
         { "West", "A", 3.0, 40.0 }, { "West", "B", 4.0, 60.0 } });

      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      when(box.getScope().getScriptEnv()).thenReturn(env);
      TableAssembly table = mock(TableAssembly.class);

      CalcTableVSAQuery query = new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         @Override
         protected TableLens getTableLens(TableAssembly table) {
            return data;
         }
      };

      TableLens lens = query.getTableLens();
      assertInstanceOf(RuntimeCalcTableLens.class, lens);
      return (RuntimeCalcTableLens) lens;
   }
}
