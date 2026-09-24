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
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for bug #76987: on the no-crosstab path,
 * {@link CalcTableVSAQuery#getTableLens()} must not write the layout snapshot it took at the
 * start of the query back onto the live calc assembly when a property edit replaced the
 * assembly's layout while the query was running.
 *
 * <p>Property edits ({@code VSTableLayoutService} -> {@code VSAssemblyInfoHandler.apply()} ->
 * {@code setVSAssemblyInfo()}) copy the edited info into the live assembly in place and take no
 * lock that the query also holds, so such an edit can land between the query's
 * {@code cassembly.clone()} and its write-back. The query below is driven through the real
 * {@code getTableLens()}; only the base-table fetch is replaced, and that fetch applies the edit
 * the way {@code VSTableLayoutService.setCellBinding()} does. The outcome must not depend on
 * whether the query runs on a script thread.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAQueryLayoutWriteBackTest {
   @ParameterizedTest(name = "scriptThread={0}")
   @ValueSource(booleans = { false, true })
   void editLandingDuringQueryIsNotReverted(boolean scriptThread) throws Exception {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);

      CalcTableVSAQuery query = createQuery(vs, () -> {
         // what VSTableLayoutService.setCellBinding() does: edit a clone, then apply it
         CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) cassembly.getInfo().clone();
         ninfo.getTableLayout().setCellBinding(0, 0, new TableCellBinding(CellBinding.BIND_TEXT, "EDITED"));
         cassembly.setVSAssemblyInfo(ninfo);
         assertEquals("EDITED", getCellValue(cassembly, 0, 0), "edit must be applied");
      });

      TableLens lens = runQuery(query, scriptThread);

      assertNotNull(lens);
      assertEquals("EDITED", getCellValue(cassembly, 0, 0),
                   "the query must not revert an edit applied while it was running");
   }

   /**
    * Without a concurrent edit, the query keeps putting its snapshot back, so normalizations
    * that building the calc lens applies to the live layout ({@code LayoutTool.syncCalcTopN})
    * are not persisted on the assembly, the same as before #76987.
    */
   @ParameterizedTest(name = "scriptThread={0}")
   @ValueSource(booleans = { false, true })
   void queryWithoutEditDoesNotPersistLayoutNormalization(boolean scriptThread) throws Exception {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);

      // top-N on a summary column that does not exist; syncCalcTopN() resets it
      TableCellBinding group = TableCellBinding.getGroupBinding("col");
      TopNInfo topN = group.getTopN(true);
      topN.setTopN(3);
      topN.setTopNSummaryCol(0);
      cassembly.getTableLayout().setCellBinding(0, 0, group);
      TableLayout before = cassembly.getTableLayout();

      TableLens lens = runQuery(createQuery(vs, () -> { }), scriptThread);

      assertNotNull(lens);
      assertNotSame(before, cassembly.getTableLayout(),
                    "without an edit the query puts its snapshot back onto the assembly");
      TableCellBinding binding =
         (TableCellBinding) cassembly.getTableLayout().getCellBinding(0, 0);
      assertEquals(3, binding.getTopN(false).getTopN(),
                   "the top-N normalization must not become persistent on the assembly");
      assertEquals(0, binding.getTopN(false).getTopNSummaryCol());
   }

   private static CalcTableVSAQuery createQuery(Viewsheet vs, Runnable duringFetch) {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      TableAssembly table = mock(TableAssembly.class);
      TableLens data = new DefaultTableLens(new Object[][] { { "col" }, { "a" }, { "b" } });

      return new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         // the base-table fetch runs between the query's snapshot and its write-back
         @Override
         protected TableLens getTableLens(TableAssembly table) {
            duringFetch.run();
            return data;
         }
      };
   }

   private static TableLens runQuery(CalcTableVSAQuery query, boolean scriptThread)
      throws Exception
   {
      Callable<TableLens> call = query::getTableLens;

      if(!scriptThread) {
         return call.call();
      }

      JavaScriptEngine.pushExecScriptable(mock(ScriptScope.class));

      try {
         assertTrue(JavaScriptEngine.isScriptThread());
         return call.call();
      }
      finally {
         JavaScriptEngine.popExecScriptable();
      }
   }

   private static String getCellValue(CalcTableVSAssembly cassembly, int r, int c) {
      CellBinding binding = cassembly.getTableLayout().getCellBinding(r, c);
      return binding == null ? null : binding.getValue();
   }
}
