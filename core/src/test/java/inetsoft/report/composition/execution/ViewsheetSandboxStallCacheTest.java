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

import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.report.script.viewsheet.TableDataVSAScriptable;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.stall.LockStallException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #77123: a query that failed with a lock stall (FAIL stall mode) must not leave a cached
 * NULL result for the assembly. Otherwise the next read, e.g. an output assembly's value read
 * by a script ({@code Gauge1.value}), got null from the data map instead of running the query
 * again, and the stall that was rethrown to the first reader became a silent null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxStallCacheTest {
   @Test
   void getDataDoesNotCacheAStalledQuery() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(new RuntimeException("query", stall))
         .thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(stall, LockStallException.find(
            assertThrows(RuntimeException.class, () -> box.getData("Text1"))));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL),
                    "getData() cached a NULL result for a stalled query");

         // the stall is gone: the query runs again
         assertEquals(42, box.getData("Text1"));
      }
   }

   /**
    * A failure that is not a stall is cached as before.
    */
   @Test
   void getDataStillCachesAFailedQueryAsBefore() throws Exception {
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(new RuntimeException("boom"));

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertThrows(RuntimeException.class, () -> box.getData("Text1"));
         assertNotNull(dmap(box).get("Text1", DataMap.NORMAL));
      }
   }

   /**
    * A failure that is not a stall is cached exactly as before: the cached value is the NULL
    * marker, and the next read returns null from the cache without running the query again.
    */
   @Test
   void nonStallFailureIsCachedAsNullAndNotRunAgain() throws Exception {
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(new RuntimeException("boom")).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertThrows(RuntimeException.class, () -> box.getData("Text1"));
         assertEquals("__null__", dmap(box).get("Text1", DataMap.NORMAL));
         assertNull(box.getData("Text1"));
         Mockito.verify(query, Mockito.times(1)).getData();
      }
   }

   /**
    * A stall thrown directly (not wrapped) is not cached either.
    */
   @Test
   void getDataDoesNotCacheADirectStall() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(stall).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(stall, assertThrows(LockStallException.class, () -> box.getData("Text1")));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL));
         assertEquals(42, box.getData("Text1"));
         // the good result is cached: no further runs
         assertEquals(42, box.getData("Text1"));
         Mockito.verify(query, Mockito.times(2)).getData();
      }
   }

   /**
    * A query that keeps stalling runs once per read, no more: each read fails with the stall
    * and nothing is cached, and the first read after the stall is gone caches the result.
    */
   @Test
   void persistentStallRunsTheQueryOncePerRead() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData())
         .thenThrow(stall, stall, stall, stall, stall)
         .thenReturn(7);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         for(int i = 1; i <= 5; i++) {
            assertSame(stall, assertThrows(LockStallException.class,
                                           () -> box.getData("Text1")));
            Mockito.verify(query, Mockito.times(i)).getData();
            assertNull(dmap(box).get("Text1", DataMap.NORMAL));
         }

         assertEquals(7, box.getData("Text1"));
         assertEquals(7, box.getData("Text1"));
         Mockito.verify(query, Mockito.times(6)).getData();
      }
   }

   /**
    * getVSTableLens0 cached a NULL view table (DataMap.VSTABLE) for a table whose query
    * stalled, so every later getVSTableLens (the next render, or a script reading Table1.row)
    * got no table, with no error, until the data map was reset.
    */
   @Test
   void getVSTableLensDoesNotCacheAStalledQuery() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = tableQuery();
      Mockito.when(query.getData()).thenThrow(new RuntimeException("query", stall))
         .thenReturn(lens(2));

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(stall, LockStallException.find(
            assertThrows(Exception.class, () -> box.getVSTableLens("Table1", false))));
         assertNull(dmap(box).get("Table1", DataMap.VSTABLE),
                    "getVSTableLens() cached a NULL view table for a stalled query");

         // the stall is gone: the next read builds the table from the new data
         VSTableLens lens = box.getVSTableLens("Table1", false);
         assertNotNull(lens);
         assertEquals(2, lens.getRowCount());
         assertNotNull(dmap(box).get("Table1", DataMap.VSTABLE));
      }
   }

   /**
    * A stall from building the view table (after the data was read) is not cached either.
    */
   @Test
   void getVSTableLensDoesNotCacheAStalledViewTable() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = tableQuery();
      Mockito.when(query.getData()).thenReturn(lens(3));
      Mockito.doThrow(stall)
         .doAnswer(inv -> new VSTableLens(inv.getArgument(0)))
         .when(query).getViewTableLens(Mockito.any());

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(stall, assertThrows(LockStallException.class,
                                        () -> box.getVSTableLens("Table1", false)));
         assertNull(dmap(box).get("Table1", DataMap.VSTABLE));

         VSTableLens lens = box.getVSTableLens("Table1", false);
         assertNotNull(lens);
         assertEquals(3, lens.getRowCount());
      }
   }

   /**
    * A failure that is not a stall is cached exactly as before: the view table is the NULL
    * marker and the next read returns null without building it again.
    */
   @Test
   void getVSTableLensStillCachesANonStallFailureAsNull() throws Exception {
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = tableQuery();
      Mockito.when(query.getData()).thenReturn(lens(3));
      Mockito.doThrow(new RuntimeException("boom"))
         .doAnswer(inv -> new VSTableLens(inv.getArgument(0)))
         .when(query).getViewTableLens(Mockito.any());

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertThrows(RuntimeException.class, () -> box.getVSTableLens("Table1", false));
         assertEquals("__null__", dmap(box).get("Table1", DataMap.VSTABLE));
         assertNull(box.getVSTableLens("Table1", false));
         Mockito.verify(query, Mockito.times(1)).getViewTableLens(Mockito.any());
      }
   }

   /**
    * Through the real sandbox: after a cleared table's re-fetch stalls, the next script read
    * of Table1.row / Table1.table reads the new table, not the old one.
    */
   @Test
   void scriptReadAfterAStalledRefetchReadsTheNewTable() throws Exception {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = tableQuery();
      Mockito.when(query.getData())
         .thenReturn(lens(3))
         .thenThrow(new RuntimeException("query", stall))
         .thenReturn(lens(2));

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         TableDataVSAScriptable table =
            (TableDataVSAScriptable) box.getScope().getVSAScriptable("Table1");
         assertNotNull(table);
         assertEquals(3, table.getMember("row"));

         table.clearCache();
         assertSame(stall, assertThrows(LockStallException.class,
                                        () -> table.getMember("row")));

         assertEquals(2, table.getMember("row"));
         assertEquals(2, ((TableArray) table.getMember("table")).getMember("length"));
         assertEquals(2, box.getVSTableLens("Table1", false).getRowCount());
      }
   }

   private static DataVSAQuery tableQuery() throws Exception {
      DataVSAQuery query = Mockito.mock(DataVSAQuery.class);
      Mockito.when(query.getViewTableLens(Mockito.any()))
         .thenAnswer(inv -> new VSTableLens(inv.getArgument(0)));
      return query;
   }

   private static TableLens lens(int rows) {
      Object[][] data = new Object[rows][];
      data[0] = new Object[] { "a", "b" };

      for(int i = 1; i < rows; i++) {
         data[i] = new Object[] { i, i * 10 };
      }

      return new DefaultTableLens(data);
   }

   private static ViewsheetSandbox sandbox() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "Stall77123", null);
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
