/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.util;

import inetsoft.report.TableLens;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.util.Catalog;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class XNodeTableTest {
   @Test
   public void testSerializeXTableTableNode() throws Exception {
      XTableTableNode xNode = new XTableTableNode(XTableUtil.getDefaultTableLens());
      XNodeTable originalTable = new XNodeTable(xNode);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XNodeTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeCompositeTableNode() throws Exception {
      CompositeTableNode xNode = new CompositeTableNode(
         new XTableTableNode(XTableUtil.getDefaultTableLens()), 3);
      XNodeTable originalTable = new XNodeTable(xNode);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XNodeTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeXTableNode() throws Exception {
      XNodeTable originalTable = new XNodeTable(XTableUtil.getDefaultXTableNode());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XNodeTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeXSwappableTable2() throws Exception {
      XNodeTable xNodeTable = new XNodeTable(XTableUtil.getDefaultXTableNode());
      Assertions.assertEquals(XNodeTable.XSwappableTable2.class, xNodeTable.getTable().getClass());

      // any table that creates a descriptor with a table reference would do
      FormulaTableLens originalTable = new FormulaTableLens(new XTableLens(xNodeTable),
                                                            new String[0], new String[0],
                                                            new GraalJavaScriptEnv(), null);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(FormulaTableLens.class, deserializedTable.getClass());
   }

   // Bug #77870: a failure while reading the rows is kept for the caller, without changing what
   // the table reports to other callers (cancelled, the rows read before the failure)
   @Test
   public void testLoadExceptionStreaming() throws Exception {
      checkLoadException("true");
   }

   @Test
   public void testLoadExceptionNotStreaming() throws Exception {
      checkLoadException("false");
   }

   @Test
   public void testNoLoadExceptionWhenAllRowsLoad() throws Exception {
      XNodeTable table = new XNodeTable(XTableUtil.getDefaultXTableNode());
      table.moreRows(Integer.MAX_VALUE);
      Assertions.assertEquals(5, table.getRowCount());
      Assertions.assertNull(table.getLoadException());
      Assertions.assertFalse(table.isCancelled());
   }

   private void checkLoadException(String streaming) throws Exception {
      String old = SreeEnv.getProperty("replet.streaming");
      RuntimeException failure = new RuntimeException("bug77870 read failed");

      try {
         SreeEnv.setProperty("replet.streaming", streaming);
         XNodeTable table = new XNodeTable(new FailingTableNode(3, failure));
         // does not throw, the rows read before the failure stay as before
         Assertions.assertFalse(table.moreRows(Integer.MAX_VALUE));
         Assertions.assertEquals(4, table.getRowCount());
         // kept before the waiting reader is woken, so no wait is needed
         Assertions.assertSame(failure, table.getLoadException());

         if("false".equals(streaming)) {
            // the background loader marks the table cancelled after waking the reader
            Assertions.assertTrue(table.isCancelled());
         }
      }
      finally {
         SreeEnv.setProperty("replet.streaming", old);
      }
   }

   // Bug #78250: a failure of the streaming (background) loader reaches every reader through
   // XNodeTableLens, also once the loader has finished its outer catch: a scheduled reader gets a
   // TableLoadException, an interactive one the rows read before the failure and the
   // getDataFailed warning. moreRows() used to throw a raw RuntimeException for the legacy
   // loadDataException instead, always for the next reader after a ClassCastException, and for a
   // reader that raced the loader's catch otherwise, which also lost the failure in worker lenses
   @Test
   public void testLoadFailureReachesReadersAfterTheLoaderFinished() throws Exception {
      RuntimeException[] failures = {
         new RuntimeException("bug78250 read failed"),
         new ClassCastException("bug78250 wrong value type")
      };

      List<Executable> cases = new ArrayList<>();

      for(RuntimeException failure : failures) {
         for(boolean distinct : new boolean[] { false, true }) {
            for(boolean scheduler : new boolean[] { true, false }) {
               String name = failure.getClass().getSimpleName() + " distinct=" + distinct +
                  " scheduler=" + scheduler;

               cases.add(() -> {
                  LoaderEvents events = new LoaderEvents(false);

                  try {
                     XNodeTableLens base = streamingLens(failure, events.queryId);
                     base.setFailOnLoadException(scheduler);
                     // the second complete() runs after the loader's outer catch
                     Assertions.assertTrue(events.loaderDone.await(30, TimeUnit.SECONDS),
                                           name + ": the loader did not finish");
                     assertReaderGetsFailure(reader(base, distinct), scheduler, failure, name);
                  }
                  finally {
                     events.close();
                  }
               });
            }
         }
      }

      Assertions.assertAll(cases);
   }

   // Bug #78250: a reader that runs while the loader is held between complete() and its outer
   // catch, and another one after the catch, both get the failure
   @Test
   public void testLoadFailureReachesReadersWhileTheLoaderIsInItsCatch() throws Exception {
      RuntimeException failure = new RuntimeException("bug78250 read failed");

      for(boolean distinct : new boolean[] { false, true }) {
         for(boolean scheduler : new boolean[] { true, false }) {
            String name = "distinct=" + distinct + " scheduler=" + scheduler;
            LoaderEvents events = new LoaderEvents(true);

            try {
               XNodeTableLens base = streamingLens(failure, events.queryId);
               base.setFailOnLoadException(scheduler);
               Assertions.assertTrue(events.loaderHeld.await(30, TimeUnit.SECONDS),
                                     name + ": the loader did not complete the table");

               try {
                  assertReaderGetsFailure(reader(base, distinct), scheduler, failure,
                                          name + " held");
               }
               finally {
                  events.releaseLoader.countDown();
               }

               Assertions.assertTrue(events.loaderDone.await(30, TimeUnit.SECONDS),
                                     name + ": the loader did not finish");
               assertReaderGetsFailure(reader(base, distinct), scheduler, failure,
                                       name + " after the catch");
            }
            finally {
               events.close();
            }
         }
      }
   }

   private static XNodeTableLens streamingLens(RuntimeException failure, String queryId) {
      String old = SreeEnv.getProperty("replet.streaming");

      try {
         SreeEnv.setProperty("replet.streaming", "true");
         FailingTableNode node = new FailingTableNode(3, failure);
         // the loader fires a query finished event for this id on each complete()
         node.setAttribute("queryId", queryId);
         XNodeTableLens lens = new XNodeTableLens(node);
         Assertions.assertTrue(lens.isLoadInBackground());
         return lens;
      }
      finally {
         SreeEnv.setProperty("replet.streaming", old);
      }
   }

   private static Supplier<TableLens> reader(XNodeTableLens base, boolean distinct) {
      // the distinct lens reads the base on a worker thread
      return distinct ? () -> new DistinctTableLens(base, new int[] { 0 }, true) : () -> base;
   }

   private static void assertReaderGetsFailure(Supplier<TableLens> reader, boolean scheduler,
                                               RuntimeException failure, String name)
   {
      CoreTool.clearUserMessage();

      if(scheduler) {
         RuntimeException ex = Assertions.assertThrows(
            RuntimeException.class, () -> dataRows(reader.get()), name);
         Assertions.assertNotNull(TableLoadException.find(ex), name + ": " + ex);
         Assertions.assertTrue(String.valueOf(ex.getMessage()).contains(failure.getMessage()),
                               name + ": " + ex);
      }
      else {
         int rows = Assertions.assertDoesNotThrow(() -> dataRows(reader.get()), name);
         Assertions.assertEquals(3, rows, name);
         UserMessage message = CoreTool.getUserMessage();
         Assertions.assertNotNull(message, name + ": no warning for the failed load");
         Assertions.assertEquals(Catalog.getCatalog().getString("common.table.getDataFailed") +
                                 ": " + failure.getMessage(), message.getMessage(), name);
      }
   }

   private static int dataRows(TableLens lens) {
      lens.moreRows(XTable.EOT);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   /**
    * Follows the background loader of one table through the query finished events that its
    * complete() fires: loadTable0() fires the first one before the loader's outer catch, and
    * the finally after that catch fires the second one.
    */
   private static final class LoaderEvents implements QueryExecutionListener {
      LoaderEvents(boolean holdLoader) {
         this.holdLoader = holdLoader;
         XNodeTable.addQueryExecutionListener(this);
      }

      @Override
      public void queryExecutionStarted(QueryExecutionEvent event) {
         // not used
      }

      @Override
      public void queryExecutionFinished(QueryExecutionEvent event) {
         if(!queryId.equals(event.getQueryId())) {
            return;
         }

         int count = finished.incrementAndGet();

         if(count == 1 && holdLoader) {
            loaderHeld.countDown();

            try {
               releaseLoader.await(30, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
         else if(count == 2) {
            loaderDone.countDown();
         }
      }

      void close() {
         releaseLoader.countDown();
         XNodeTable.removeQueryExecutionListener(this);
      }

      private final String queryId = "bug78250-" + UUID.randomUUID();
      private final boolean holdLoader;
      private final AtomicInteger finished = new AtomicInteger();
      private final CountDownLatch loaderHeld = new CountDownLatch(1);
      private final CountDownLatch releaseLoader = new CountDownLatch(1);
      private final CountDownLatch loaderDone = new CountDownLatch(1);
   }

   private static final class FailingTableNode extends XTableNode {
      FailingTableNode(int rows, RuntimeException failure) {
         this.rows = rows;
         this.failure = failure;
      }

      @Override
      public boolean next() {
         if(++row > rows) {
            throw failure;
         }

         return true;
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

      private final int rows;
      private final RuntimeException failure;
      private int row;
   }
}
