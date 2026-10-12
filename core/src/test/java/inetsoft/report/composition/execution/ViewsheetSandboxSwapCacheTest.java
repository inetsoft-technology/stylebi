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
import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #77908: a query that failed because a swap file was lost must not leave a cached NULL result
 * for the assembly. Otherwise only the first read failed, and every later read in the sandbox
 * (a table scroll, a view-only rebuild, a script read, an output executed again after onLoad)
 * got null from the data map, i.e. no table, a 0x0 table, "" or 0, until the assembly was
 * reset. Other failures are still cached as NULL, see {@link ViewsheetSandboxStallCacheTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxSwapCacheTest {
   @Test
   void getDataDoesNotCacheADirectSwapFailure() throws Exception {
      SwapFileReadException swap = swapFailure();
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(swap, swap).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(swap, assertThrows(SwapFileReadException.class, () -> box.getData("Text1")));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL),
                    "getData() cached a NULL result for a lost swap file");

         // still lost: the next read runs the query again and fails again, it is not null
         assertSame(swap, assertThrows(SwapFileReadException.class, () -> box.getData("Text1")));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL));

         // rebuilt: the next read returns the data and caches it
         assertEquals(42, box.getData("Text1"));
         assertEquals(42, box.getData("Text1"));
         Mockito.verify(query, Mockito.times(3)).getData();
      }
   }

   @Test
   void getDataDoesNotCacheAWrappedSwapFailure() throws Exception {
      SwapFileReadException swap = swapFailure();
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData())
         .thenThrow(new RuntimeException("query", swap), new RuntimeException("query", swap))
         .thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(swap, SwapFileReadException.find(
            assertThrows(RuntimeException.class, () -> box.getData("Text1"))));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL));

         assertSame(swap, SwapFileReadException.find(
            assertThrows(RuntimeException.class, () -> box.getData("Text1"))));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL));

         assertEquals(42, box.getData("Text1"));
         Mockito.verify(query, Mockito.times(3)).getData();
      }
   }

   /**
    * An MV query wraps any failure in MVExecutionException, which getData() handles in its own
    * catch clause (MV on demand is off here, so the failure is rethrown).
    */
   @Test
   void getDataDoesNotCacheASwapFailureWrappedByAnMVQuery() throws Exception {
      SwapFileReadException swap = swapFailure();
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData())
         .thenThrow(new MVExecutionException(swap), new MVExecutionException(swap))
         .thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertSame(swap, SwapFileReadException.find(
            assertThrows(MVExecutionException.class, () -> box.getData("Text1"))));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL),
                    "getData() cached a NULL result for a lost swap file in an MV query");

         assertSame(swap, SwapFileReadException.find(
            assertThrows(MVExecutionException.class, () -> box.getData("Text1"))));
         assertNull(dmap(box).get("Text1", DataMap.NORMAL));

         assertEquals(42, box.getData("Text1"));
         Mockito.verify(query, Mockito.times(3)).getData();
      }
   }

   /**
    * An MV query failure that is not a lost swap file is cached as before.
    */
   @Test
   void getDataStillCachesAnMVFailureThatIsNotASwapFailure() throws Exception {
      ViewsheetSandbox box = sandbox();
      VSAQuery query = Mockito.mock(VSAQuery.class);
      Mockito.when(query.getData()).thenThrow(new MVExecutionException("boom")).thenReturn(42);

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         assertThrows(MVExecutionException.class, () -> box.getData("Text1"));
         assertEquals("__null__", dmap(box).get("Text1", DataMap.NORMAL));
         assertNull(box.getData("Text1"));
         Mockito.verify(query, Mockito.times(1)).getData();
      }
   }

   /**
    * A table whose data query lost a swap file: neither the view table (VSTABLE) nor the data
    * (NORMAL) is cached as NULL, so the next read neither returns no table nor builds a 0x0
    * table from a cached NULL.
    */
   @Test
   void getVSTableLensDoesNotCacheASwapFailureOfTheQuery() throws Exception {
      SwapFileReadException swap = swapFailure();
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = tableQuery();
      Mockito.when(query.getData())
         .thenThrow(new RuntimeException("query", swap), new RuntimeException("query", swap))
         .thenReturn(lens(2));

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         for(int i = 0; i < 2; i++) {
            assertSame(swap, SwapFileReadException.find(
               assertThrows(Exception.class, () -> box.getVSTableLens("Table1", false))));
            assertNull(dmap(box).get("Table1", DataMap.VSTABLE),
                       "getVSTableLens() cached a NULL view table for a lost swap file");
            assertNull(dmap(box).get("Table1", DataMap.NORMAL),
                       "getData() cached a NULL table for a lost swap file");
         }

         VSTableLens lens = box.getVSTableLens("Table1", false);
         assertNotNull(lens);
         assertEquals(2, lens.getRowCount());
         assertNotNull(dmap(box).get("Table1", DataMap.VSTABLE));
         Mockito.verify(query, Mockito.times(3)).getData();
      }
   }

   /**
    * A swap failure from building the view table (after the data was read) is not cached as a
    * NULL view table either.
    */
   @Test
   void getVSTableLensDoesNotCacheASwapFailureOfTheViewTable() throws Exception {
      SwapFileReadException swap = swapFailure();
      ViewsheetSandbox box = sandbox();
      DataVSAQuery query = tableQuery();
      Mockito.when(query.getData()).thenReturn(lens(3));
      Mockito.doThrow(swap).doThrow(swap)
         .doAnswer(inv -> new VSTableLens(inv.getArgument(0)))
         .when(query).getViewTableLens(Mockito.any());

      try(MockedStatic<VSAQuery> st = mockQuery(query)) {
         for(int i = 0; i < 2; i++) {
            assertSame(swap, assertThrows(SwapFileReadException.class,
                                          () -> box.getVSTableLens("Table1", false)));
            assertNull(dmap(box).get("Table1", DataMap.VSTABLE));
         }

         VSTableLens lens = box.getVSTableLens("Table1", false);
         assertNotNull(lens);
         assertEquals(3, lens.getRowCount());
         Mockito.verify(query, Mockito.times(3)).getViewTableLens(Mockito.any());
      }
   }

   private static SwapFileReadException swapFailure() {
      File file = new File("s1234_1.tdat");
      return new SwapFileReadException(file, new IOException("file not found: " + file));
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
                                        AssetEntry.Type.VIEWSHEET, "Swap77908", null);
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
