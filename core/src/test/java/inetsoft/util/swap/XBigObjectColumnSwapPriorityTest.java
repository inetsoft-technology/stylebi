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

import inetsoft.test.*;
import inetsoft.uql.table.XBigObjectColumn;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #77653, a fully swapped XBigObjectColumn kept a non-zero swap priority and re-counted its
 * swapped rows on every swap. The swapper counts every swappable with a non-zero priority as
 * swapped, so the column kept the criticalNoSwap escape of waitForMemory() from firing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XBigObjectColumnSwapPriorityTest {
   @Test
   void fullySwappedColumnHasNothingToSwap() throws Exception {
      XBigObjectColumn column = createSwappedColumn();
      int scount = getInt(column, "scount");
      int swapCount = getInt(column, "swapCount");

      assertEquals(0, column.getSwapPriority(), "fully swapped column is still a swap candidate");
      assertFalse(column.swap(), "swap freed nothing but returned true");
      assertEquals(scount, getInt(column, "scount"), "swapped rows were counted again");
      assertEquals(swapCount, getInt(column, "swapCount"), "swap that freed nothing was counted");
      assertEquals(0, column.getSwapPriority());
      column.dispose();
   }

   @Test
   void readingRowsBackMakesColumnSwappableAgain() throws Exception {
      XBigObjectColumn column = createSwappedColumn();
      XSwapper swapper = spy(XSwapper.getSwapper());
      doNothing().when(swapper).waitForMemory();
      column.swapper = swapper;

      for(int i = 0; i < 10; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      assertNotEquals(0, column.getSwapPriority(), "rows read back can't be swapped");
      assertTrue(column.swap(), "rows read back were not swapped");
      assertEquals(countSwappedRows(column), getInt(column, "scount"));
      assertEquals(0, column.getSwapPriority());

      for(int i = 0; i < ROWS; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      column.dispose();
   }

   @Test
   void fullySwappedColumnDoesNotBlockCriticalNoSwap() throws Exception {
      XBigObjectColumn column = createSwappedColumn();
      XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      doReturn(false).when(swapper).doGC(anyBoolean());
      // shared with the swapper bean
      AtomicInteger criticalNoSwap = (AtomicInteger) getField(swapper, "criticalNoSwap");
      criticalNoSwap.set(0);

      // a sweep thread that only has the column registered
      GroupedThread thread = startSweepThread(swapper, column);
      Object swapLock = getField(swapper, "swapLock");

      try {
         long end = System.currentTimeMillis() + 5000;

         // notify so the first sweep doesn't wait 5s, later sweeps run every 500ms at critical
         while(criticalNoSwap.get() == 0 && System.currentTimeMillis() < end) {
            synchronized(swapLock) {
               swapLock.notifyAll();
            }

            Thread.sleep(100);
         }

         assertTrue(criticalNoSwap.get() > 0, "fully swapped column was counted as swapped");
      }
      finally {
         thread.cancel();
         criticalNoSwap.set(0);
         column.dispose();
      }
   }

   @Test
   void waiterDoesNotWaitForFullySwappedColumns() throws Exception {
      XBigObjectColumn[] columns = { createSwappedColumn(), createSwappedColumn() };
      XSwapper swapper = spy(XSwapper.getSwapper());
      doReturn(XSwapper.CRITICAL_MEM).when(swapper).getMemoryState();
      doReturn(false).when(swapper).doGC(anyBoolean());
      swapper.setMaxCriticalWait(10000L);
      AtomicInteger criticalNoSwap = (AtomicInteger) getField(swapper, "criticalNoSwap");
      criticalNoSwap.set(0);
      GroupedThread thread = startSweepThread(swapper, columns);

      try {
         // nothing can be swapped, so the waiter should be let through by the criticalNoSwap
         // escape instead of waiting until the max wait
         long start = System.currentTimeMillis();
         swapper.waitForMemory();
         long elapsed = System.currentTimeMillis() - start;

         assertTrue(elapsed < 5000, "waited " + elapsed + "ms for fully swapped columns");
      }
      finally {
         thread.cancel();
         criticalNoSwap.set(0);

         for(XBigObjectColumn column : columns) {
            column.dispose();
         }
      }
   }

   private static GroupedThread startSweepThread(XSwapper swapper, XSwappable... swappables)
      throws Exception
   {
      Class<?> threadClass = Class.forName(XSwapper.class.getName() + "$XSwapperThread");
      // don't depend on the parameters after the outer instance, pass null for them
      List<Constructor<?>> constructors = Arrays.stream(threadClass.getDeclaredConstructors())
         .filter(c -> c.getParameterCount() > 0 && c.getParameterTypes()[0] == XSwapper.class)
         .toList();
      assertEquals(1, constructors.size(),
                   "expected one XSwapperThread constructor taking the swapper, found " + constructors);
      Constructor<?> constructor = constructors.get(0);
      constructor.setAccessible(true);
      Object[] args = new Object[constructor.getParameterCount()];
      args[0] = swapper;
      GroupedThread thread = (GroupedThread) constructor.newInstance(args);
      Method register = threadClass.getDeclaredMethod("register", XSwappable.class);
      register.setAccessible(true);

      for(XSwappable swappable : swappables) {
         register.invoke(thread, swappable);
      }

      thread.start();
      return thread;
   }

   private static XBigObjectColumn createSwappedColumn() {
      XBigObjectColumn column = new XBigObjectColumn((char) 4, (char) 16, (char) ROWS);

      for(int i = 0; i < ROWS; i++) {
         column.addObject("value" + i);
      }

      column.complete();
      assertTrue(column.swap(), "column was not swapped");

      try {
         assertEquals(ROWS - 4, countSwappedRows(column));
         assertEquals(ROWS - 4, getInt(column, "scount"));
      }
      catch(Exception ex) {
         fail(ex);
      }

      return column;
   }

   private static int countSwappedRows(XBigObjectColumn column) throws Exception {
      Object[] arr = (Object[]) getField(column, "arr");
      int count = 0;

      for(int i = 0; i < ROWS; i++) {
         if(arr[i] == Tool.NULL) {
            count++;
         }
      }

      return count;
   }

   private static int getInt(Object obj, String name) throws Exception {
      return (Integer) getField(obj, name);
   }

   private static Object getField(Object obj, String name) throws Exception {
      Class<?> cls = obj.getClass();

      // the swapper is a mockito subclass
      while(cls != null) {
         try {
            Field field = cls.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(obj);
         }
         catch(NoSuchFieldException ex) {
            cls = cls.getSuperclass();
         }
      }

      throw new NoSuchFieldException(name);
   }

   private static final int ROWS = 300;
}
