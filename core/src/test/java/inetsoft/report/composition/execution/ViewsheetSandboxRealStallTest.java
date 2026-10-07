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
import inetsoft.report.lens.CrossJoinTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.*;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #77123 with a real lock stall instead of a constructed one: the table's lens is a cross
 * join whose worker is blocked, and the table's own script reads the lens through the real
 * {@code getVSTableLens0} path (query, view table, {@code executeScript}, GraalJS,
 * {@code TableArray}). In FAIL mode the stall reaches the caller and no NULL view table is
 * cached, so the read after the worker is released gets the whole table; in ALERT mode the
 * read keeps waiting and then completes, as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxRealStallTest {
   @BeforeEach
   void setUp() {
      resetGlobalStallState();
      pool = readerPool();
   }

   @AfterEach
   void tearDown() {
      if(gated != null) {
         gated.open();
      }

      pool.shutdownNow();
      StallTestSupport.clearOverride();
   }

   @Test
   void realStallInTableScriptIsNotCachedAsNullTable() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, true));
      gated = new GatedTable(30);
      TableLens data = crossJoin();
      ViewsheetSandbox box = sandbox();

      LockStallException stall = stallIn(failureOf(read(box, data), 15));
      assertEquals("CrossJoinTableLens.moreRows", stall.getSite());
      assertTrue(stall.getStalledMillis() >= 1000);
      assertNull(dmap(box).get("Table1", DataMap.VSTABLE),
                 "a NULL view table was cached for a stalled table");

      // the worker is released: the next read runs the script again over the whole table
      gated.open();
      VSTableLens lens = read(box, data).get(15, TimeUnit.SECONDS);
      assertNotNull(lens, "the read after the stall got no table");
      assertEquals(ROWS, rowCount(lens));
      assertSame(lens, dmap(box).get("Table1", DataMap.VSTABLE));
   }

   @Test
   void alertModeTableScriptReadWaitsAndCompletes() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      gated = new GatedTable(30);
      TableLens data = crossJoin();
      ViewsheetSandbox box = sandbox();
      int dumps = WaitRegistry.global().getDumper().getDumpCount();
      Future<VSTableLens> reader = read(box, data);

      try {
         awaitTrue(() -> WaitRegistry.global().getDumper().getDumpCount() > dumps, 15,
                   "the cross join wait was never alerted");
         assertFalse(reader.isDone(), "the reader must still be waiting");
      }
      finally {
         gated.open();
      }

      VSTableLens lens = reader.get(15, TimeUnit.SECONDS);
      assertNotNull(lens);
      assertEquals(ROWS, rowCount(lens));
      assertSame(lens, dmap(box).get("Table1", DataMap.VSTABLE));
   }

   /**
    * The cross join's constructor reads its bases, so it is built on a reader thread, which
    * the gated base does not block. Its workers are started there too, before any script, as
    * for a lens the query already started (inside a script the cross join loads its bases on
    * the calling thread, #76935); they are not readers and block until the gate opens.
    */
   private TableLens crossJoin() throws Exception {
      return pool.submit(() -> {
         TableLens lens = new CrossJoinTableLens(gated, new DefaultTableLens(data(2)));
         assertTrue(lens.moreRows(0));
         return lens;
      }).get(15, TimeUnit.SECONDS);
   }

   /**
    * Read Table1's view table on a reader thread. Static mocks are per thread, so the query is
    * mocked on that thread.
    */
   private Future<VSTableLens> read(ViewsheetSandbox box, TableLens data) {
      return pool.submit(() -> {
         try(MockedStatic<VSAQuery> st = mockQuery(data)) {
            return box.getVSTableLens("Table1", false);
         }
      });
   }

   private static int rowCount(TableLens lens) {
      lens.moreRows(TableLens.EOT);
      return lens.getRowCount();
   }

   private static ViewsheetSandbox sandbox() {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      VSAssemblyInfo info = table.getVSAssemblyInfo();
      // the table's script reads its own rows while the view table is built
      info.setScript("var n = table.length; if(n != " + ROWS + ") throw 'rows: ' + n;");
      info.setScriptEnabled(true);
      vs.addAssembly(table);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "RealStall77123", null);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private static MockedStatic<VSAQuery> mockQuery(TableLens data) throws Exception {
      // only the worksheet part of the query is replaced: it returns the cross join
      DataVSAQuery query = Mockito.mock(DataVSAQuery.class);
      Mockito.when(query.getData()).thenReturn(data);
      Mockito.when(query.getViewTableLens(Mockito.any()))
         .thenAnswer(inv -> new VSTableLens(inv.getArgument(0)));
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

   // header + 30 x 2 cross joined rows
   private static final int ROWS = 61;

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private GatedTable gated;
}
