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

import inetsoft.report.internal.Util;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.TableLoadException;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.SQLDataException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #78126: with replet.streaming the rows of a query result load on a background thread
 * after the XNodeTableLens is created, so a row fetch error may be stored before or after
 * XSessionManager.buildXTable checks the new table. A failure stored before the check failed
 * the whole query (a null lens, or the raw exception for a scheduled run) instead of giving the
 * rows read before it plus the warning (Bug #77901). Here the background tasks run on the
 * calling thread, so the failure is always stored before the check.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XSessionManagerLoadFailureTest {
   private static final String DB_MESSAGE = "Attempt to divide by zero.";
   private static final int ROWS = 3;

   @Autowired
   private XSessionService sessionService;

   private final AtomicInteger runs = new AtomicInteger();
   private Supplier<XNode> nodes;
   private XSessionManager manager;
   private MockedStatic<ThreadPool> threadPool;

   @BeforeEach
   void setUp() throws Exception {
      SreeEnv.setProperty("replet.streaming", "true");
      CoreTool.clearUserMessage();
      runs.set(0);

      // the data service stands in for the handler, which looks up the result cache first
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            DataCacheVisitor visitor = inv.getArgument(5);

            if(!visitor.visitCache("bug78126")) {
               runs.incrementAndGet();
            }

            return nodes.get();
         });
      manager = new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
      manager.setCacheData(true);

      // the rows still load in the background as far as the table knows, but have finished
      // (and failed) before buildXTable checks the table
      threadPool = mockStatic(ThreadPool.class, inv -> {
         if("addOnDemand".equals(inv.getMethod().getName())) {
            ((Runnable) inv.getArgument(0)).run();
            return null;
         }

         return inv.callRealMethod();
      });
   }

   @AfterEach
   void tearDown() {
      threadPool.close();
      SreeEnv.remove("replet.streaming");
      CoreTool.clearUserMessage();
   }

   @Test
   void interactiveReadGetsRowsAndWarning() throws Exception {
      nodes = () -> new FetchNode(true, false);
      TableLens lens = getTable(new VariableTable());

      assertNotNull(lens, "the failure stored before the check failed the whole query");
      assertTrue(nestedLens(lens).isLoadInBackground());
      assertFalse(lens.moreRows(Integer.MAX_VALUE));
      assertEquals(ROWS + 1, lens.getRowCount());

      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message);
      assertEquals(Catalog.getCatalog().getString("common.table.getDataFailed") + ": " +
                   DB_MESSAGE, message.getMessage());

      // the failed result is not cached, the query runs again
      assertNotNull(getTable(new VariableTable()));
      assertEquals(2, runs.get());
   }

   @Test
   void scheduledReadFailsWithTableLoadException() throws Exception {
      nodes = () -> new FetchNode(true, false);
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", Boolean.TRUE);
      TableLens lens = getTable(vars);

      assertNotNull(lens);
      RuntimeException ex =
         assertThrows(RuntimeException.class, () -> lens.moreRows(Integer.MAX_VALUE));
      assertNotNull(TableLoadException.find(ex), String.valueOf(ex));
      assertEquals(DB_MESSAGE, ex.getMessage());
   }

   @Test
   void cancelStillAbandonsTheQuery() throws Exception {
      nodes = () -> new FetchNode(false, true);
      TableLens lens = getTable(new VariableTable());

      assertNull(lens);
      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message);
      assertEquals(Catalog.getCatalog().getString("common.table.queryCancelled"),
                   message.getMessage());
   }

   @Test
   void successfulResultIsCached() throws Exception {
      nodes = () -> new FetchNode(false, false);
      TableLens lens = getTable(new VariableTable());

      assertFalse(lens.moreRows(Integer.MAX_VALUE));
      assertEquals(ROWS + 1, lens.getRowCount());
      assertNull(CoreTool.getUserMessage());
      assertNotNull(getTable(new VariableTable()));
      assertEquals(1, runs.get());
   }

   @Test
   void synchronousLoadStillFailsTheQuery() throws Exception {
      // the rows load before the table is created, unchanged by Bug #78126
      SreeEnv.setProperty("replet.streaming", "false");
      nodes = () -> new FetchNode(true, false);
      TableLens lens = getTable(new VariableTable());

      assertNull(lens);
      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains(DB_MESSAGE), message.getMessage());
   }

   private TableLens getTable(VariableTable vars) throws Exception {
      return manager.getXNodeTableLens(mock(XQuery.class), vars, null, null, null, -1);
   }

   private static XNodeTableLens nestedLens(TableLens lens) {
      return (XNodeTableLens) Util.getNestedTable(lens, XNodeTableLens.class);
   }

   /**
    * A result whose fetch fails after {@link #ROWS} rows, the way JDBCTableNode wraps a driver
    * error, or that is cancelled there.
    */
   private static final class FetchNode extends XTableNode {
      FetchNode(boolean fail, boolean cancel) {
         this.fail = fail;
         this.cancel = cancel;
      }

      @Override
      public boolean next() {
         if(++row <= ROWS) {
            return true;
         }

         if(fail) {
            SQLDataException ex = new SQLDataException(DB_MESSAGE);
            throw new RuntimeException(ex + "", ex);
         }

         return false;
      }

      @Override
      public boolean isCanceled() {
         return cancel;
      }

      @Override
      public int getColCount() {
         return 1;
      }

      @Override
      public String getName(int col) {
         return "ID";
      }

      @Override
      public Class getType(int col) {
         return Integer.class;
      }

      @Override
      public Object getObject(int col) {
         return row;
      }

      @Override
      public XMetaInfo getXMetaInfo(int col) {
         return null;
      }

      @Override
      public boolean rewind() {
         return false;
      }

      @Override
      public boolean isRewindable() {
         return false;
      }

      private final boolean fail;
      private final boolean cancel;
      private int row;
   }
}
