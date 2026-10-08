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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.util.swap.SwapLostTestSupport.LostTable;
import static inetsoft.util.swap.SwapLostTestSupport.swapLost;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77909: {@link CalcTableVSAQuery#getTableLens()} over a base whose swap file is lost
 * fails with the swap file read failure instead of returning no table. The query is driven
 * through the real {@code getTableLens()}; only the base-table fetch is replaced.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAQuerySwapLostTest {
   /**
    * The expansion of a lazy {@code rowList} value list reads the lost rows outside any script
    * catch, so the failure reaches the query's own catch.
    */
   @Test
   void lostBaseInRowListExpansionFailsTheQuery() {
      SwapFileReadException lost = swapLost();
      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      CalcTableVSAQuery query = createQuery(box, new LostTable(VALUES, 2, lost));

      assertSame(lost, assertThrows(SwapFileReadException.class, query::getTableLens));
      // the sandbox write lock is still released
      verify(box).lockWrite();
      verify(box).unlockWrite();
   }

   @Test
   void readableBaseExpandsAsBefore() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      TableLens lens = createQuery(box, new DefaultTableLens(VALUES)).getTableLens();

      assertNotNull(lens);
      // one row per base value, then the second row of the default 2x2 layout
      assertEquals(4, lens.getRowCount());
      assertEquals("a", lens.getObject(0, 0));
      assertEquals("b", lens.getObject(1, 0));
      assertEquals("c", lens.getObject(2, 0));
   }

   private static CalcTableVSAQuery createQuery(ViewsheetSandbox box, TableLens data) {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);

      TableLayout layout = cassembly.getTableLayout();
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_FORMULA, "rowList(data, 'col')");
      cell.setExpansion(GroupableCellBinding.EXPAND_V);
      layout.setCellBinding(0, 0, cell);

      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      when(box.getScope().getScriptEnv()).thenReturn(env);
      TableAssembly table = mock(TableAssembly.class);

      return new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         @Override
         protected TableLens getTableLens(TableAssembly table) {
            return data;
         }
      };
   }

   private static final Object[][] VALUES = { { "col" }, { "a" }, { "b" }, { "c" } };
}
