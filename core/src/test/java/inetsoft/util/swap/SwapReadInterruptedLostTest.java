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
package inetsoft.util.swap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.table.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77916: an interrupted swap read is not logged as a lost file at any of the three read
 * sites and does not mark a table fragment's swap file lost, while a missing swap file is
 * still a plain lost file at every site, also on an interrupted thread. A sort over a base
 * whose read is interrupted fails loudly and sorts every row once the read succeeds.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SwapReadInterruptedLostTest {
   @BeforeEach
   void attachLog() {
      log = new ListAppender<>();
      log.start();

      for(Class<?> cls : LOGGED) {
         ((Logger) LoggerFactory.getLogger(cls)).addAppender(log);
      }
   }

   @AfterEach
   void detachLog() {
      Thread.interrupted();

      for(Class<?> cls : LOGGED) {
         ((Logger) LoggerFactory.getLogger(cls)).detachAppender(log);
      }
   }

   @Test
   void interruptedReadIsNotLoggedAsALostFile() throws Exception {
      XIntFragment ints = new XIntFragment(new int[] { 1, 2, 3, 4, 5, 6 });
      assertTrue(ints.swap(), "int fragment was not swapped");
      assertInterruptedRead(() -> ints.getSafely(5));
      assertEquals(6, ints.getSafely(5));
      ints.dispose();

      XObjectFragment<String> objects = objectFragment();
      assertTrue(objects.swap(), "object fragment was not swapped");
      assertInterruptedRead(() -> objects.getSafely(5));
      assertEquals("v5", objects.getSafely(5));
      objects.dispose();

      for(boolean typed : new boolean[] { true, false }) {
         XSwappableTable table = createTable(typed);
         XTableFragment fragment = table.getTables()[0];
         assertTrue(fragment.swap(false), "table fragment was not swapped");
         Tool.clearUserMessage();

         for(int c = 0; c < table.getColCount(); c++) {
            final int col = c;
            assertInterruptedRead(() -> table.getObject(5, col));
         }

         assertNull(Tool.getUserMessage(), "an interrupted read reported a missing swap file");
         assertFalse(isLost(fragment), "an interrupted read marked the swap file lost");
         assertEquals("s4", table.getObject(5, 1));
         assertTrue(fragment.swap(false), "the fragment of an interrupted read is not swapped again");
         assertEquals("s6", table.getObject(7, 1));
         table.dispose();
      }
   }

   @Test
   void missingSwapFileIsStillLostAtEverySite() throws Exception {
      for(boolean interrupted : new boolean[] { false, true }) {
         XObjectFragment<String> objects = objectFragment();
         assertTrue(objects.swap(), "object fragment was not swapped");
         assertTrue(objects.getFile(objects.prefix + "_0.tdat").delete(), "swap file not deleted");
         assertLostRead(() -> objects.getSafely(5), interrupted);
         objects.dispose();

         for(boolean typed : new boolean[] { true, false }) {
            XSwappableTable table = createTable(typed);
            XTableFragment fragment = table.getTables()[0];
            assertTrue(fragment.swap(false), "table fragment was not swapped");
            assertTrue(fragment.getSwapFile().delete(), "swap file not deleted");

            for(int c = 0; c < table.getColCount(); c++) {
               final int col = c;
               assertLostRead(() -> table.getObject(5, col), interrupted);
            }

            assertFalse(fragment.swap(false), "a fragment whose swap file is lost was swapped");
            assertTrue(isLost(fragment), "a missing swap file was not marked lost");
            table.dispose();
         }
      }
   }

   @Test
   void sortOverInterruptedBaseReadFailsLoudlyThenSortsEveryRow() {
      XSwappableTable table = createTable(false);
      InterruptingBase base = new InterruptingBase(table);
      SortFilter sort = new SortFilter(base, new int[] { 0 }, false);

      assertThrows(SwapReadInterruptedException.class, () -> sort.moreRows(XTable.EOT),
                   "the sort completed with the rows read before the interrupt");
      Thread.interrupted();

      base.armed = false;
      sort.moreRows(XTable.EOT);
      assertEquals(51, sort.getRowCount());
      assertEquals(49, sort.getObject(1, 0));
      table.dispose();
   }

   private void assertInterruptedRead(Executable read) {
      log.list.clear();
      Thread.currentThread().interrupt();
      SwapReadInterruptedException ex = assertThrows(SwapReadInterruptedException.class, read::run);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag was cleared");
      Thread.interrupted();
      assertTrue(ex.getFile().exists(), "swap file of an interrupted read was removed");
      assertTrue(log.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR),
                 "an interrupted read was logged as an error: " + log.list);
   }

   private void assertLostRead(Executable read, boolean interrupted) {
      log.list.clear();

      if(interrupted) {
         Thread.currentThread().interrupt();
      }

      SwapFileReadException ex = assertThrows(SwapFileReadException.class, read::run);
      Thread.interrupted();
      assertEquals(SwapFileReadException.class, ex.getClass(), "a missing file is a lost file");
      assertTrue(log.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR),
                 "a lost swap file was not logged as an error");
   }

   private static boolean isLost(XTableFragment fragment) throws Exception {
      Field field = XTableFragment.class.getDeclaredField("lost");
      field.setAccessible(true);
      return (boolean) field.get(fragment);
   }

   private static XObjectFragment<String> objectFragment() {
      XObjectFragment<String> fragment =
         new XObjectFragment<>((char) 100, (char) 100, String.class);

      for(int i = 0; i < 100; i++) {
         fragment.add("v" + i);
      }

      fragment.complete();
      return fragment;
   }

   private static XSwappableTable createTable(boolean typed) {
      XSwappableTable table = typed ?
         new XSwappableTable(new Class[] { Integer.class, String.class, Double.class, Timestamp.class }) :
         new XSwappableTable(2, false);
      table.addRow(typed ? new Object[] { "a", "b", "c", "d" } : new Object[] { "a", "b" });

      for(int i = 0; i < 50; i++) {
         table.addRow(typed ? new Object[] { i, "s" + i, i + 0.5, new Timestamp(i * 1000L) } :
                         new Object[] { i, "s" + i });
      }

      table.complete();
      return table;
   }

   @FunctionalInterface
   private interface Executable {
      void run();
   }

   /**
    * A base over a swapped table whose reads past row 20 are interrupted, as by a cancel of the
    * reading thread, while armed.
    */
   private static final class InterruptingBase extends AbstractTableLens {
      InterruptingBase(XSwappableTable table) {
         this.table = table;
      }

      @Override
      public boolean moreRows(int row) {
         return table.moreRows(row);
      }

      @Override
      public int getRowCount() {
         return table.getRowCount();
      }

      @Override
      public int getColCount() {
         return table.getColCount();
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public Object getObject(int r, int c) {
         if(!armed || r < 20) {
            return table.getObject(r, c);
         }

         table.getTables()[0].swap(false);
         Thread.currentThread().interrupt();

         // the read may run on a pool thread, which must not keep the flag
         try {
            return table.getObject(r, c);
         }
         finally {
            Thread.interrupted();
         }
      }

      private final XSwappableTable table;
      private volatile boolean armed = true;
   }

   private static final Class<?>[] LOGGED =
      { XIntFragment.class, XObjectFragment.class, AbstractTableColumn.class };
   private ListAppender<ILoggingEvent> log;
}
