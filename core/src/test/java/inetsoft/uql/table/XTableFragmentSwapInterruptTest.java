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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77948, real write failures of an XTableFragment swap, without the test hook: an
 * interrupted snapshot file list, and (Windows only) a locked swap file whose stub can't be
 * deleted. The data must stay in memory, the incomplete file must not be listed, and the next
 * swap must rewrite the file so that a snapshot reload of it reads the original values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XTableFragmentSwapInterruptTest {
   /**
    * An interrupt on the caller thread of getFilesList() (snapshot save/export) closes the
    * channel in the middle of the write.
    */
   @Test
   void interruptedFileListKeepsDataAndIsRewritten(@TempDir Path dir) throws Exception {
      XSwappableTable table = createTable(4);
      XTableFragment fragment = table.getTables()[0];
      File file = fragment.getSwapFile();

      try {
         Thread.currentThread().interrupt();
         List<File> list;

         try {
            list = table.getFilesList();
         }
         finally {
            Thread.interrupted();
         }

         assertEquals(List.of(), list, "interrupted swap file listed");
         assertFalse(file.exists(), "interrupted swap file kept");
         assertColumnsValid(fragment, true);
         assertValues(table, 4);

         assertEquals(List.of(file), table.getFilesList());
         assertColumnsValid(fragment, false);
         assertValues(table, 4);
         assertSnapshotReload(table, dir);
      }
      finally {
         table.dispose();
      }
   }

   /**
    * A real write failure at the last column leaves a partial file. When the stub can't be
    * deleted (the locking handle is still open, which only blocks the write on Windows) it
    * stays on disk, but it is not listed, and the next swap truncates and rewrites it instead
    * of reusing it.
    */
   @Test
   @EnabledOnOs(OS.WINDOWS)
   void undeletableStubIsNotReusedAndIsRewritten(@TempDir Path dir) throws Exception {
      XSwappableTable table = createTable(5);
      XTableFragment fragment = table.getTables()[0];
      File file = fragment.getSwapFile();

      try {
         LockingValue.lock = null;
         LockingValue.target = file;
         assertTrue(fragment.swap(false));
         assertNotNull(LockingValue.lock, "test setup: the swap file was not locked");
         LockingValue.release();

         assertTrue(file.exists() && file.length() > 0, "test setup: no partial stub kept");
         long stubLength = file.length();
         assertEquals(List.of(), fragment.getFiles(), "partial swap file listed");
         assertColumnsValid(fragment, true);
         assertValues(table, 5);

         assertEquals(List.of(file), table.getFilesList());
         assertNotEquals(stubLength, file.length(), "partial swap file reused");
         assertColumnsValid(fragment, false);
         assertValues(table, 5);
         assertSnapshotReload(table, dir);
      }
      finally {
         LockingValue.release();
         table.dispose();
      }
   }

   /**
    * Copy the listed swap file as a snapshot and load it the way
    * SnapshotEmbeddedTableAssembly.createFragment() does.
    */
   private static void assertSnapshotReload(XSwappableTable table, Path dir) throws Exception {
      List<File> files = table.getFilesList();
      assertEquals(1, files.size());
      Files.copy(files.get(0).toPath(), dir.resolve("snap_s.tdat"));

      XTableColumnCreator[] creators = table.getCreators();
      XTableColumn[] columns = new XTableColumn[creators.length];

      for(int i = 0; i < columns.length; i++) {
         columns[i] = creators[i].createColumn((char) 128, (char) 0x2000);
      }

      XTableFragment original = table.getTables()[0];
      XTableFragment snapshot = new XTableFragment(columns, false);
      snapshot.setSnapshotPath(dir.resolve("snap").toString());

      try {
         for(int r = 0; r < original.getColumns()[0].length(); r++) {
            for(int c = 0; c < columns.length; c++) {
               assertEquals(original.getObject(r, c), snapshot.getObject(r, c),
                            "snapshot row " + r + " col " + c);
            }
         }
      }
      finally {
         snapshot.dispose();
      }
   }

   private static void assertColumnsValid(XTableFragment fragment, boolean valid) {
      for(XTableColumn column : fragment.getColumns()) {
         assertEquals(valid, column.isValid(), column.getClass().getSimpleName());
      }
   }

   private static void assertValues(XSwappableTable table, int ncol) {
      for(int i = 0; i < ROWS; i++) {
         assertEquals(i, table.getObject(i + 1, 0));
         assertEquals("s" + i, table.getObject(i + 1, 1));
         assertEquals(i + 0.5, table.getObject(i + 1, 2));
         assertEquals(new Timestamp(i * 1000L), table.getObject(i + 1, 3));

         if(ncol > 4) {
            assertEquals(new LockingValue(i), table.getObject(i + 1, 4));
         }
      }
   }

   /**
    * Integer, String, Double, Timestamp and, with 5 columns, an object column last.
    */
   private static XSwappableTable createTable(int ncol) {
      Class<?>[] types = { Integer.class, String.class, Double.class, Timestamp.class, Object.class };
      Class<?>[] ttypes = new Class<?>[ncol];
      System.arraycopy(types, 0, ttypes, 0, ncol);
      XSwappableTable table = new XSwappableTable(ttypes);
      table.addRow(new Object[ncol]);

      for(int i = 0; i < ROWS; i++) {
         Object[] row = { i, "s" + i, i + 0.5, new Timestamp(i * 1000L), new LockingValue(i) };
         Object[] trow = new Object[ncol];
         System.arraycopy(row, 0, trow, 0, ncol);
         table.addRow(trow);
      }

      table.complete();
      return table;
   }

   /**
    * A value that, when written, locks the target swap file through a second handle, so the
    * real FileChannel.write() of its column fails (Windows enforces the lock).
    */
   public static class LockingValue implements KryoSerializable, Serializable {
      public LockingValue() {
      }

      LockingValue(int value) {
         this.value = value;
      }

      @Override
      public void write(Kryo kryo, Output output) {
         if(target != null && lock == null && target.exists()) {
            try {
               raf = new RandomAccessFile(target, "rw");
               lock = raf.getChannel().lock();
            }
            catch(Exception ex) {
               throw new IllegalStateException(ex);
            }
         }

         output.writeInt(value);
      }

      @Override
      public void read(Kryo kryo, Input input) {
         value = input.readInt();
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof LockingValue && ((LockingValue) obj).value == value;
      }

      @Override
      public int hashCode() {
         return value;
      }

      static void release() throws Exception {
         target = null;

         if(raf != null) {
            raf.close(); // also releases the lock
         }

         raf = null;
      }

      private int value;
      static volatile File target;
      static RandomAccessFile raf;
      static FileLock lock;
   }

   private static final int ROWS = 50;
}
