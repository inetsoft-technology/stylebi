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

import inetsoft.mv.MVExecutionException;
import inetsoft.report.FormulaTable;
import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptStopTestSupport.Stops;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #77949: a query that failed because a script was stopped by its timeout, e.g. a freehand
 * table formula of the query's process(), must not leave a cached NULL result for the
 * assembly, or every later read would get no table instead of running it again. Other
 * script errors are still cached as NULL, see {@link ViewsheetSandboxSwapCacheTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxStopCacheTest {
   @Test
   void getDataDoesNotCacheAStop() throws Exception {
      assertStopNotCached(ScriptStopTestSupport.stopped());
   }

   @Test
   void getDataDoesNotCacheAWrappedStop() throws Exception {
      assertStopNotCached(new RuntimeException("query", ScriptStopTestSupport.stopped()));
   }

   /**
    * An MV query wraps any failure in MVExecutionException, which getData() handles in its own
    * catch clause (MV on demand is off here, so the failure is rethrown).
    */
   @Test
   void getDataDoesNotCacheAStopWrappedByAnMVQuery() throws Exception {
      assertStopNotCached(new MVExecutionException(ScriptStopTestSupport.stopped()));
   }

   /**
    * Unchanged: an ordinary script error is cached as NULL.
    */
   @Test
   void getDataStillCachesAScriptError() throws Exception {
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(new ScriptException("boom")).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertThrows(ScriptException.class, () -> box.getData("Text1"));
         assertEquals("__null__", dmap(box).get("Text1", DataMap.NORMAL));
         assertNull(box.getData("Text1"));
         Mockito.verify(query, Mockito.times(1)).getData();
      }
   }

   /**
    * A table whose query was stopped: neither the view table (VSTABLE) nor the data (NORMAL)
    * is cached as NULL.
    */
   @Test
   void getVSTableLensDoesNotCacheAStopOfTheQuery() throws Exception {
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = Mockito.mock(DataVSAQuery.class);
      Mockito.when(query.getViewTableLens(Mockito.any()))
         .thenAnswer(inv -> new VSTableLens(inv.getArgument(0)));
      Mockito.when(query.getData())
         .thenThrow(new RuntimeException("query", ScriptStopTestSupport.stopped()))
         .thenReturn(lens());

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertTrue(ScriptTimeoutGuard.isStop(
            assertThrows(Exception.class, () -> box.getVSTableLens("Table1", false))));
         assertNull(dmap(box).get("Table1", DataMap.VSTABLE),
                    "getVSTableLens() cached a NULL view table for a stopped script");
         assertNull(dmap(box).get("Table1", DataMap.NORMAL));

         VSTableLens lens = box.getVSTableLens("Table1", false);
         assertNotNull(lens);
         assertEquals(2, lens.getRowCount());
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   /**
    * A freehand table whose formula times out (real 1 s timeout) in the query's process():
    * the next read processes it again instead of reading a cached NULL as no table.
    */
   @Test
   void timedOutFreehandTableIsProcessedAgain() throws Exception {
      String previousTimeout = ScriptStopTestSupport.setTimeout("1");
      Stops stops = new Stops();
      ScriptStopTestSupport.StoppingEnv env = new ScriptStopTestSupport.StoppingEnv(stops);
      env.init();

      try {
         String marker = "/*vs77949*/";
         FormulaTable elem = Mockito.mock(FormulaTable.class);
         Mockito.when(elem.getScriptEnv()).thenReturn(env);
         Mockito.when(elem.getID()).thenReturn("CalcTable1");
         Mockito.when(elem.getScriptTable()).thenReturn(lens());
         CalcTableLens calc = new CalcTableLens(1, 1);
         calc.setElement(elem);
         calc.setObject(0, 0, new CalcTableLens.Formula("[1, 2, 3]" + marker));
         calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
         stops.reset(marker, n -> n == 1, false);

         ViewsheetSandbox box = sandbox();
         VSAQuery query = Mockito.mock(VSAQuery.class);
         Mockito.when(query.getData()).thenAnswer(inv -> calc.process());

         try(MockedStatic<VSAQuery> st = mockQuery(query)) {
            assertTrue(ScriptTimeoutGuard.isStop(
               assertThrows(Exception.class, () -> box.getData("Text1"))));
            assertNull(dmap(box).get("Text1", DataMap.NORMAL),
                       "getData() cached a NULL result for a timed-out formula");

            // the processed table, as the sandbox wraps it
            TableLens data = assertInstanceOf(TableLens.class, box.getData("Text1"));
            assertEquals(3, data.getRowCount());

            for(int r = 0; r < 3; r++) {
               assertEquals(r + 1, ((Number) data.getObject(r, 0)).intValue());
            }

            assertEquals(1, stops.stops());
         }
      }
      finally {
         Thread.interrupted();
         ScriptStopTestSupport.setTimeout(previousTimeout);
      }
   }

   /**
    * A formula table is computed lazily, after getData() cached it: a stop on a read of it
    * then fails that read, and the next getData() runs the query again instead of handing out
    * the stopped table.
    */
   @Test
   void getDataComputesALazilyStoppedFormulaTableAgain() throws Exception {
      Stops stops = new Stops();
      ScriptStopTestSupport.StoppingEnv env = new ScriptStopTestSupport.StoppingEnv(stops);
      env.init();
      String marker = "/*lazy77949*/";
      stops.reset(marker, n -> n == 3, true);
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenAnswer(inv -> formulaTable(env, marker));

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         TableLens first = (TableLens) box.getData("Text1");
         assertTrue(ScriptTimeoutGuard.isStop(
            assertThrows(Exception.class, () -> drain(first))));
         assertSame(first, dmap(box).get("Text1", DataMap.NORMAL), "cached before the stop");

         TableLens next = (TableLens) box.getData("Text1");
         assertNotSame(first, next, "the stopped table is not handed out again");
         assertEquals(List.of(10, 20, 30, 40, 50), drain(next));
         Mockito.verify(query, Mockito.times(2)).getData();
         // the table handed out before keeps failing at the stopped row
         assertTrue(ScriptTimeoutGuard.isStop(
            assertThrows(Exception.class, () -> first.getObject(3, 2))));
      }
   }

   /**
    * The same for the view table of a table assembly, built lazily over the formula table.
    */
   @Test
   void getVSTableLensBuildsALazilyStoppedTableAgain() throws Exception {
      Stops stops = new Stops();
      ScriptStopTestSupport.StoppingEnv env = new ScriptStopTestSupport.StoppingEnv(stops);
      env.init();
      String marker = "/*lazyvs77949*/";
      stops.reset(marker, n -> n == 3, true);
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = Mockito.mock(DataVSAQuery.class);
      Mockito.when(query.getViewTableLens(Mockito.any()))
         .thenAnswer(inv -> new VSTableLens(inv.getArgument(0)));
      Mockito.when(query.getData()).thenAnswer(inv -> formulaTable(env, marker));

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         VSTableLens first = box.getVSTableLens("Table1", false);
         assertTrue(ScriptTimeoutGuard.isStop(
            assertThrows(Exception.class, () -> drain(first))));

         VSTableLens next = box.getVSTableLens("Table1", false);
         assertNotSame(first, next, "the stopped view table is not handed out again");
         assertEquals(List.of(10, 20, 30, 40, 50), drain(next));
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   /**
    * A freehand table cell evaluated lazily, after getData() cached the processed table: the
    * next getData() processes it again.
    */
   @Test
   void getDataProcessesALazilyStoppedFreehandTableAgain() throws Exception {
      Stops stops = new Stops();
      ScriptStopTestSupport.StoppingEnv env = new ScriptStopTestSupport.StoppingEnv(stops);
      env.init();
      String marker = "/*lazycalc77949*/";
      FormulaTable elem = Mockito.mock(FormulaTable.class);
      Mockito.when(elem.getScriptEnv()).thenReturn(env);
      Mockito.when(elem.getID()).thenReturn("CalcTable1");
      Mockito.when(elem.getScriptTable()).thenReturn(lens());
      CalcTableLens calc = new CalcTableLens(1, 1);
      calc.setElement(elem);
      calc.setObject(0, 0, new CalcTableLens.Formula("41 + 1" + marker));
      stops.reset(marker, n -> n == 1, true);
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenAnswer(inv -> calc.process());

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         TableLens first = (TableLens) box.getData("Text1");
         assertTrue(ScriptTimeoutGuard.isStop(
            assertThrows(Exception.class, () -> first.getObject(0, 0))));

         TableLens next = (TableLens) box.getData("Text1");
         assertNotSame(first, next);
         assertEquals(42, ((Number) next.getObject(0, 0)).intValue());
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   private static FormulaTableLens formulaTable(ScriptStopTestSupport.StoppingEnv env,
                                                String marker)
   {
      Object[][] data = new Object[6][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= 5; i++) {
         data[i] = new Object[] { "k" + i, i };
      }

      return new FormulaTableLens(new DefaultTableLens(data), new String[] { "f" },
                                  new String[] { "field['value'] * 10" + marker }, env, null);
   }

   // the formula column of every data row
   private static List<Integer> drain(TableLens table) {
      List<Integer> values = new ArrayList<>();
      int col = table.getColCount() - 1;

      for(int r = 1; table.moreRows(r); r++) {
         values.add(((Number) table.getObject(r, col)).intValue());
      }

      return values;
   }

   private static void assertStopNotCached(Exception failure) throws Exception {
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(failure).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(failure, assertThrows(Exception.class, () -> box.getData("Text1")));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL),
                    "getData() cached a NULL result for a stopped script");

         // the next read runs the query again
         assertEquals(42, box.getData("Text1"));
         assertEquals(42, box.getData("Text1"));
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   private static TableLens lens() {
      return new DefaultTableLens(new Object[][] { { "a", "b" }, { 1, 10 } });
   }

   private static ViewsheetSandbox sandbox() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "Stop77949", null);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private static MockedStatic<VSAQuery> mockQuery(VSAQuery query) {
      MockedStatic<VSAQuery> st = Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS);
      st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(), Mockito.anyInt()))
         .thenReturn(query);
      return st;
   }

   private static DataMap dmap(ViewsheetSandbox box) throws Exception {
      Field f = ViewsheetSandbox.class.getDeclaredField("dmap");
      f.setAccessible(true);
      return (DataMap) f.get(box);
   }
}
