/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77864, a lost swap file of an XBigObjectColumn or a SelectionList was read back as null
 * values with only a log message, so a query or selection silently showed wrong data. A later
 * swap of the column also recreated the lost file, so the rows swapped out before read back
 * other rows' values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class SwapFileLostTest {
   @Test
   void readOfLostColumnFileThrowsAndLeavesRowSwapped() throws Exception {
      XBigObjectColumn column = createSwappedColumn();
      int scount = getInt(column, "scount");
      deleteColumnFile(column);

      SwapFileReadException ex =
         assertThrows(SwapFileReadException.class, () -> column.getObject(200));
      assertEquals(getColumnFile(column), ex.getFile());
      assertSame(Tool.NULL, getRow(column, 200), "null cached as the value of the row");
      assertEquals(scount, getInt(column, "scount"), "row counted as read back");

      // the row is read again rather than returning a cached null
      assertThrows(SwapFileReadException.class, () -> column.getObject(200));
      assertThrows(SwapFileReadException.class, () -> column.isNull(200));
      column.dispose();
   }

   @Test
   void readOfTruncatedColumnFileThrows() throws Exception {
      XBigObjectColumn column = createSwappedColumn();
      File file = getColumnFile(column);

      try(RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
         raf.setLength(file.length() / 2);
      }

      assertThrows(SwapFileReadException.class, () -> column.getObject(ROWS - 10));
      column.dispose();
   }

   @Test
   void swapAfterLostColumnFileKeepsResidentRows() throws Exception {
      XBigObjectColumn column = createSwappedColumn();

      // read rows back successfully, which moves the never swapped tail rows out of the
      // in-memory list so the next swap would write them
      for(int i = 0; i < 10; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      deleteColumnFile(column);
      File file = getColumnFile(column);

      assertFalse(column.swap(), "column was swapped without its swap file");
      assertFalse(file.exists(), "swap file was recreated");
      assertEquals(0, column.getSwapPriority(), "column is still offered to the swapper");

      // the rows read back before the loss are still in memory with their own values
      for(int i = 0; i < 10; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      // the tail rows never swapped out
      for(int i = ROWS - 4; i < ROWS; i++) {
         assertEquals("value" + i, column.getObject(i));
      }

      assertThrows(SwapFileReadException.class, () -> column.getObject(150));
      column.dispose();
   }

   @Test
   void swapAfterFailedReadDoesNotMisaddressRows() throws Exception {
      XBigObjectColumn column = createSwappedColumn();
      deleteColumnFile(column);

      for(int i = 200; i < 204; i++) {
         int r = i;
         assertThrows(SwapFileReadException.class, () -> column.getObject(r));
      }

      assertFalse(column.swap(), "column was swapped without its swap file");
      assertFalse(getColumnFile(column).exists(), "swap file was recreated");

      // rows swapped out before the loss must not read another row's value
      for(int i = 0; i < 4; i++) {
         int r = i;
         assertThrows(SwapFileReadException.class, () -> column.getObject(r));
      }

      // a file recreated by someone else doesn't make the old offsets readable either
      Files.write(getColumnFile(column).toPath(), new byte[16]);
      assertThrows(SwapFileReadException.class, () -> column.getObject(0));
      column.dispose();
   }

   @Test
   void readOfLostListFileThrowsAndRetries() throws Exception {
      SelectionList list = createSwappedList("v");
      File file = getListFile(list);
      File moved = new File(file.getPath() + ".moved");
      Files.move(file.toPath(), moved.toPath(), StandardCopyOption.REPLACE_EXISTING);

      SwapFileReadException ex =
         assertThrows(SwapFileReadException.class, list::getSelectionValues);
      assertEquals(file, ex.getFile());
      assertFalse(list.isValid());
      assertThrows(SwapFileReadException.class, () -> list.getSelectionValue(5));
      assertThrows(SwapFileReadException.class, list::clone);
      assertDoesNotThrow(list::toString);

      // the list stays swapped out, so it is read once the file is back
      Files.move(moved.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      SelectionValue[] values = list.getSelectionValues();
      assertEquals(VALUES, values.length);

      for(int i = 0; i < VALUES; i++) {
         assertEquals("v" + i, values[i].getValue());
      }

      assertTrue(list.isValid());
      list.dispose();
   }

   @Test
   void cloneOfParentWithLostChildListThrows() throws Exception {
      SelectionList parent = new SelectionList();
      parent.addSelectionValue(new SelectionValue("x", "x"));
      CompositeSelectionValue value = new CompositeSelectionValue("p", "p");
      SelectionList child = createSwappedList("c");
      child.swapper = mockSwapper();
      value.setSelectionList(child);
      parent.addSelectionValue(value);
      parent.complete();
      Files.delete(getListFile(child).toPath());

      assertThrows(SwapFileReadException.class, value::clone);
      // a null child must not be added silently to the cloned parent
      assertThrows(SwapFileReadException.class, parent::clone);
      parent.dispose();
   }

   @Test
   void cloneFailureOtherThanSwapFileStillReturnsNull() {
      SelectionList parent = new SelectionList();
      parent.addSelectionValue(new SelectionValue("x", "x") {
         @Override
         public Object clone() {
            throw new IllegalStateException("clone failed");
         }
      });
      parent.complete();

      assertNull(parent.clone());
   }

   private static XBigObjectColumn createSwappedColumn() throws Exception {
      XBigObjectColumn column = new XBigObjectColumn((char) 4, (char) 16, (char) ROWS);

      for(int i = 0; i < ROWS; i++) {
         column.addObject("value" + i);
      }

      column.complete();
      column.swapper = mockSwapper();
      assertTrue(column.swap(), "column was not swapped");
      assertSame(Tool.NULL, getRow(column, 5), "row is still in memory");
      assertTrue(getColumnFile(column).exists(), "swap file was not written");
      return column;
   }

   private static SelectionList createSwappedList(String prefix) {
      SelectionList list = new SelectionList();

      for(int i = 0; i < VALUES; i++) {
         list.addSelectionValue(new SelectionValue(prefix + i, prefix + i));
      }

      list.complete();
      list.swapper = mockSwapper();
      assertTrue(list.swap(), "list was not swapped");
      assertFalse(list.isValid(), "list is still in memory");
      return list;
   }

   private static XSwapper mockSwapper() {
      XSwapper swapper = spy(XSwapper.getSwapper());
      doNothing().when(swapper).waitForMemory();
      return swapper;
   }

   private static File getColumnFile(XBigObjectColumn column) {
      return column.getFile(column.prefix + ".tdat");
   }

   private static File getListFile(SelectionList list) {
      return list.getFile(list.prefix + "_slist.swap");
   }

   private static void deleteColumnFile(XBigObjectColumn column) throws Exception {
      Files.delete(getColumnFile(column).toPath());
   }

   private static Object getRow(XBigObjectColumn column, int r) throws Exception {
      Field field = XBigObjectColumn.class.getDeclaredField("arr");
      field.setAccessible(true);
      return ((Object[]) field.get(column))[r];
   }

   private static int getInt(XBigObjectColumn column, String name) throws Exception {
      Field field = XBigObjectColumn.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.getInt(column);
   }

   private static final int ROWS = 300;
   private static final int VALUES = 60;
}
