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
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77635, XBigObjectColumn.getObject() read swapped rows back into memory without
 * waitForMemory(). The column stays swappable after a swap, so the wait must happen outside of
 * its lock, and at most once between swaps rather than once for each row read back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XBigObjectColumnSwapInTest {
   @Test
   void swapInWaitsForMemoryOutsideOfLockBeforeReadingBack() throws Exception {
      XBigObjectColumn column = createSwappedColumn();
      XSwapper swapper = spy(XSwapper.getSwapper());
      List<Boolean> swappedAtWait = new ArrayList<>();
      List<Boolean> lockedAtWait = new ArrayList<>();
      doAnswer(invocation -> {
         swappedAtWait.add(getRow(column, 5) == Tool.NULL);
         lockedAtWait.add(Thread.holdsLock(column));
         return null;
      }).when(swapper).waitForMemory();
      column.swapper = swapper;

      assertEquals("value5", column.getObject(5));
      assertEquals(List.of(true), swappedAtWait,
                   "waitForMemory() must be called once, before the row is read back");
      assertEquals(List.of(false), lockedAtWait,
                   "waitForMemory() must not be called while holding the column lock");
      column.dispose();
   }

   @Test
   void readingManyRowsAfterOneSwapWaitsOnce() {
      XBigObjectColumn column = createSwappedColumn();
      XSwapper swapper = spy(XSwapper.getSwapper());
      doNothing().when(swapper).waitForMemory();
      column.swapper = swapper;

      for(int i = 0; i < ROWS; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      verify(swapper, times(1)).waitForMemory();

      // the next swap of the column allows another wait
      assertTrue(column.swap(), "column was not swapped again");

      for(int i = 0; i < ROWS; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      verify(swapper, times(2)).waitForMemory();
      column.dispose();
   }

   @Test
   void swapDuringTheWaitDoesNotMakeEveryRowWait() {
      XBigObjectColumn column = createSwappedColumn();
      XSwapper swapper = spy(XSwapper.getSwapper());
      // the swapper sweeps the column again while the reader waits for memory
      doAnswer(invocation -> {
         assertTrue(column.swap(), "column was not swapped during the wait");
         return null;
      }).when(swapper).waitForMemory();
      column.swapper = swapper;

      for(int i = 0; i < ROWS; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      verify(swapper, times(1)).waitForMemory();
      column.dispose();
   }

   @Test
   void readOfResidentRowDoesNotWait() {
      XBigObjectColumn column = createColumn();
      XSwapper swapper = spy(XSwapper.getSwapper());
      column.swapper = swapper;

      assertEquals("value5", column.getObject(5));
      verify(swapper, never()).waitForMemory();
      column.dispose();
   }

   private static XBigObjectColumn createSwappedColumn() {
      XBigObjectColumn column = createColumn();
      assertTrue(column.swap(), "column was not swapped");

      try {
         assertSame(Tool.NULL, getRow(column, 5), "row is still in memory");
      }
      catch(Exception ex) {
         fail(ex);
      }

      return column;
   }

   private static XBigObjectColumn createColumn() {
      XBigObjectColumn column = new XBigObjectColumn((char) 4, (char) 16, (char) ROWS);

      for(int i = 0; i < ROWS; i++) {
         column.addObject("value" + i);
      }

      column.complete();
      return column;
   }

   private static Object getRow(XBigObjectColumn column, int r) throws Exception {
      Field field = XBigObjectColumn.class.getDeclaredField("arr");
      field.setAccessible(true);
      return ((Object[]) field.get(column))[r];
   }

   private static final int ROWS = 300;
}
