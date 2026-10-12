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

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.KryoSerializable;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77948, a failed XTableFragment swap write left a 0-byte or partial swap file. The next
 * swap reused the file and invalidated every column, including the ones whose data was never
 * written, so they read back as nulls (XObjectColumn) or threw NullPointerException (typed
 * columns). A column whose data could not be copied to a buffer (copyToBuffer() returned
 * null) was dropped the same way, without any I/O error.
 *
 * <p>The write failure is forced with the test-only {@code testBeforeWrite} hook, so the tests
 * do not depend on platform file locking.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XTableFragmentSwapWriteFailureTest {
   /**
    * A write failing at each column (0-byte file at the first one, partial file at a later
    * one) or at the footer keeps every column in memory, and the next swap rewrites the file.
    */
   @Test
   void failedWriteKeepsDataAndNextSwapRewritesFile() throws Exception {
      // 4 columns, so failAt 4 is the footer
      for(int failAt = 0; failAt <= 4; failAt++) {
         XSwappableTable table = createTypedTable();
         XTableFragment fragment = table.getTables()[0];
         File file = fragment.getSwapFile();

         try {
            fragment.testBeforeWrite = failingWrite(failAt);
            assertTrue(fragment.swap(false));
            fragment.testBeforeWrite = null;
            assertFailedSwapCleanedUp(fragment, file, "failAt " + failAt);
            assertTypedValues(table, "failAt " + failAt);

            assertTrue(fragment.swap(false), "fragment not swapped again, failAt " + failAt);
            assertTrue(file.exists(), "swap file not rewritten, failAt " + failAt);
            assertEquals(List.of(file), fragment.getFiles());

            for(XTableColumn column : fragment.getColumns()) {
               assertFalse(column.isValid(), "column still in memory, failAt " + failAt);
            }

            assertTypedValues(table, "failAt " + failAt);
         }
         finally {
            table.dispose();
         }
      }
   }

   /**
    * The snapshot save/export path (getFilesList, swap(true)) must not list the incomplete file,
    * and the next file list rewrites it.
    */
   @Test
   void failedWriteThroughFileListIsNotListed() throws Exception {
      for(XSwappableTable table : new XSwappableTable[] { createTypedTable(), createObjectTable() }) {
         XTableFragment fragment = table.getTables()[0];
         File file = fragment.getSwapFile();

         try {
            fragment.testBeforeWrite = failingWrite(0);
            assertEquals(List.of(), table.getFilesList(), "incomplete swap file listed");
            fragment.testBeforeWrite = null;
            assertFailedSwapCleanedUp(fragment, file, "file list");

            assertEquals(4, table.getObject(5, 0));
            assertEquals(List.of(file), table.getFilesList());
            assertTrue(file.exists(), "swap file not rewritten");

            for(XTableColumn column : fragment.getColumns()) {
               assertFalse(column.isValid(), "column still in memory: " + column.getClass());
            }

            assertEquals(4, table.getObject(5, 0));
            assertEquals("s4", table.getObject(5, 1));
         }
         finally {
            table.dispose();
         }
      }
   }

   /**
    * An XObjectColumn holding a value Kryo can't write gets no swap data. It must stay in
    * memory when the next swap reuses the file instead of reading back as nulls.
    */
   @Test
   void objectColumnThatCannotBeWrittenStaysInMemory() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      Unwritable unwritable = new Unwritable();

      for(int i = 0; i < ROWS; i++) {
         // the first value is serializable, so the column is swapped
         table.addRow(new Object[] { i, i == 2 ? unwritable : "s" + i });
      }

      table.complete();
      XTableFragment fragment = table.getTables()[0];

      try {
         assertSwapKeepsUnwrittenColumn(table, fragment, 1);
         assertEquals(4, table.getObject(5, 0));
         assertEquals("s4", table.getObject(5, 1));
         assertSame(unwritable, table.getObject(3, 1));
      }
      finally {
         table.dispose();
      }
   }

   /**
    * An XStringColumn whose copyToBuffer() fails returns null the same way and must also stay
    * in memory.
    */
   @Test
   void stringColumnThatCannotBeCopiedStaysInMemory() throws Exception {
      XSwappableTable table = createTypedTable();
      XTableFragment fragment = table.getTables()[0];
      XTableColumn column = fragment.getColumns()[1];
      assertInstanceOf(XStringColumn.class, column);
      // a byte length that is too short makes copyToBuffer() overflow and return null
      Field bytelen = XStringColumn.class.getDeclaredField("bytelen");
      bytelen.setAccessible(true);
      bytelen.setLong(column, 0L);

      try {
         assertSwapKeepsUnwrittenColumn(table, fragment, 1);
         assertTypedValues(table, "string column");
      }
      finally {
         table.dispose();
      }
   }

   /**
    * Swap, read, swap again (the read makes the fragment swappable again). The column at
    * {@code col} has no swap data and must stay in memory through both swaps.
    */
   private static void assertSwapKeepsUnwrittenColumn(XSwappableTable table,
                                                      XTableFragment fragment, int col)
   {
      XTableColumn[] columns = fragment.getColumns();
      assertTrue(fragment.swap(false), "fragment not swapped");
      assertTrue(fragment.getSwapFile().exists(), "swap file not written");
      assertTrue(columns[col].isValid(), "test setup: the column was written");
      assertFalse(columns[0].isValid(), "column 0 not swapped");

      assertEquals(4, table.getObject(5, 0));
      assertTrue(fragment.swap(false), "fragment not swapped again");
      assertTrue(columns[col].isValid(), "column without swap data was dropped");

      for(int c = 0; c < columns.length; c++) {
         if(c != col) {
            assertFalse(columns[c].isValid(), "column " + c + " not swapped");
         }
      }
   }

   private static void assertFailedSwapCleanedUp(XTableFragment fragment, File file, String msg) {
      assertFalse(file.exists(), "incomplete swap file kept, " + msg);
      assertEquals(List.of(), fragment.getFiles(), "incomplete swap file listed, " + msg);

      for(XTableColumn column : fragment.getColumns()) {
         assertTrue(column.isValid(), "column dropped after a failed write, " + msg + ": " +
            column.getClass());
      }
   }

   private static void assertTypedValues(XSwappableTable table, String msg) {
      assertEquals(4, table.getObject(5, 0), msg);
      assertEquals("s4", table.getObject(5, 1), msg);
      assertEquals(4.5, table.getObject(5, 2), msg);
      assertEquals(new Timestamp(4000L), table.getObject(5, 3), msg);
      assertEquals(49, table.getObject(ROWS, 0), msg);
   }

   /**
    * A hook that fails the write with the given index: the serializable columns in order,
    * then the footer.
    */
   private static Runnable failingWrite(int failAt) {
      int[] count = { 0 };

      return () -> {
         if(count[0]++ == failAt) {
            throw new UncheckedIOException(new IOException("simulated write failure"));
         }
      };
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

   /**
    * A value Kryo can't write.
    */
   public static class Unwritable implements KryoSerializable {
      @Override
      public void write(Kryo kryo, Output output) {
         throw new IllegalStateException("not writable");
      }

      @Override
      public void read(Kryo kryo, Input input) {
         throw new IllegalStateException("not readable");
      }
   }

   private static final int ROWS = 50;
}
