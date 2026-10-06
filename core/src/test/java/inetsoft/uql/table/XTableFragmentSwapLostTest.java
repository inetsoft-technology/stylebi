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
package inetsoft.uql.table;

import inetsoft.test.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77895, a lost swap file of an XTableFragment was read back as a plain
 * NullPointerException for typed columns and as silent nulls for an XObjectColumn. A later
 * swap of the fragment also recreated the lost file from the columns that were not in memory.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XTableFragmentSwapLostTest {
   @Test
   void readOfLostTypedColumnsThrows() throws Exception {
      XSwappableTable table = createTypedTable();
      XTableFragment fragment = swapFragment(table);
      File file = fragment.getSwapFile();
      File moved = new File(file.getPath() + ".moved");
      Files.move(file.toPath(), moved.toPath(), StandardCopyOption.REPLACE_EXISTING);
      Tool.clearUserMessage();

      for(int c = 0; c < table.getColCount(); c++) {
         int col = c;
         SwapFileReadException ex =
            assertThrows(SwapFileReadException.class, () -> table.getObject(5, col));
         assertEquals(file, ex.getFile());
      }

      assertThrows(SwapFileReadException.class, () -> table.getInt(5, 0));
      assertThrows(SwapFileReadException.class, () -> table.getDouble(5, 2));
      assertThrows(SwapFileReadException.class, () -> table.isNull(5, 1));

      UserMessage message = Tool.getUserMessage();
      assertNotNull(message, "missing swap file message");
      assertEquals(Catalog.getCatalog().getString("common.worksheet.swap.missing"),
                   message.getMessage());

      // the columns stay swapped out, so they are read once the file is back
      Files.move(moved.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      assertEquals(4, table.getObject(5, 0));
      assertEquals("s4", table.getObject(5, 1));
      assertEquals(4.5, table.getDouble(5, 2));
      assertEquals(new Timestamp(4000L), table.getObject(5, 3));
      table.dispose();
   }

   @Test
   void readOfLostObjectColumnsThrows() throws Exception {
      XSwappableTable table = createObjectTable();
      XTableFragment fragment = swapFragment(table);
      Files.delete(fragment.getSwapFile().toPath());

      assertThrows(SwapFileReadException.class, () -> table.getObject(5, 0));
      assertThrows(SwapFileReadException.class, () -> table.getObject(5, 1));
      assertThrows(SwapFileReadException.class, () -> table.isNull(5, 1));
      table.dispose();
   }

   @Test
   void swapAfterLostObjectColumnsDoesNotRecreateFile() throws Exception {
      XSwappableTable table = createObjectTable();
      XTableFragment fragment = swapFragment(table);
      File file = fragment.getSwapFile();
      Files.delete(file.toPath());

      assertThrows(SwapFileReadException.class, () -> table.getObject(5, 0));

      assertFalse(fragment.swap(false), "fragment was swapped without its swap file");
      assertFalse(file.exists(), "swap file was recreated");
      assertEquals(0, fragment.getSwapPriority(), "fragment is still offered to the swapper");

      // the columns must not read back as nulls written to a new file
      assertThrows(SwapFileReadException.class, () -> table.getObject(5, 0));
      assertThrows(SwapFileReadException.class, () -> table.getObject(5, 1));
      assertFalse(fragment.swap(true));
      assertFalse(file.exists(), "swap file was recreated");
      table.dispose();
   }

   @Test
   void swapAfterLostTypedColumnsDoesNotRecreateFile() throws Exception {
      XSwappableTable table = createTypedTable();
      XTableFragment fragment = swapFragment(table);
      File file = fragment.getSwapFile();
      Files.delete(file.toPath());

      assertThrows(SwapFileReadException.class, () -> table.getObject(5, 0));

      assertFalse(fragment.swap(false), "fragment was swapped without its swap file");
      assertFalse(file.exists(), "swap file was recreated");
      assertEquals(0, fragment.getSwapPriority(), "fragment is still offered to the swapper");

      for(int c = 0; c < table.getColCount(); c++) {
         int col = c;
         assertThrows(SwapFileReadException.class, () -> table.getObject(5, col));
      }

      table.dispose();
   }

   @Test
   void swapAfterLostFileWithResidentColumnsRewritesFile() throws Exception {
      XSwappableTable table = createObjectTable();
      XTableFragment fragment = swapFragment(table);
      File file = fragment.getSwapFile();

      // read every column back before the file is lost, the data is all in memory
      assertEquals(4, table.getObject(5, 0));
      assertEquals("s4", table.getObject(5, 1));
      Files.delete(file.toPath());

      assertTrue(fragment.swap(false), "resident fragment was not swapped");
      assertTrue(file.exists(), "swap file was not written from memory");
      assertEquals(4, table.getObject(5, 0));
      assertEquals("s4", table.getObject(5, 1));
      table.dispose();
   }

   @Test
   void corruptObjectColumnFileStillReadsNulls() throws Exception {
      // bug #74483, an object column whose data can't be deserialized (e.g. old Kryo data)
      // reads as nulls instead of failing
      XSwappableTable table = createObjectTable();
      XTableFragment fragment = swapFragment(table);
      File file = fragment.getSwapFile();
      byte[] junk = new byte[(int) file.length()];
      new Random(77895).nextBytes(junk);
      Files.write(file.toPath(), junk);

      assertNull(assertDoesNotThrow(() -> table.getObject(5, 0)));
      assertNull(assertDoesNotThrow(() -> table.getObject(5, 1)));
      table.dispose();
   }

   @Test
   void rowIndexDiagnosticDoesNotReplaceOriginalException() throws Exception {
      XSwappableTable table = createObjectTable();
      XTableFragment fragment = table.getTables()[0];
      XTableColumn column = mock(XTableColumn.class);
      when(column.getObject(anyInt())).thenThrow(new IndexOutOfBoundsException("row"));
      when(column.getInMemoryLength())
         .thenThrow(new SwapFileReadException(new File("lost"), new Exception()));
      fragment.getColumns()[0] = column;

      IndexOutOfBoundsException ex =
         assertThrows(IndexOutOfBoundsException.class, () -> table.getObject(5, 0));
      assertEquals("row", ex.getMessage());
   }

   private static XSwappableTable createTypedTable() {
      XSwappableTable table = new XSwappableTable(
         new Class[] { Integer.class, String.class, Double.class, Timestamp.class });
      table.addRow(new Object[] { "int", "str", "dbl", "ts" });

      for(int i = 0; i < ROWS; i++) {
         table.addRow(new Object[] { i, "s" + i, i + 0.5, new Timestamp(i * 1000L) });
      }

      table.complete();
      return table;
   }

   private static XSwappableTable createObjectTable() {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < ROWS; i++) {
         table.addRow(new Object[] { i, "s" + i });
      }

      table.complete();
      return table;
   }

   private static XTableFragment swapFragment(XSwappableTable table) {
      XTableFragment fragment = table.getTables()[0];
      assertTrue(fragment.swap(false), "fragment not swapped");
      assertTrue(fragment.getSwapFile().exists(), "swap file not written");

      for(XTableColumn column : fragment.getColumns()) {
         assertFalse(column.isValid(), "column still in memory: " + column.getClass());
      }

      return fragment;
   }

   private static final int ROWS = 50;
}
