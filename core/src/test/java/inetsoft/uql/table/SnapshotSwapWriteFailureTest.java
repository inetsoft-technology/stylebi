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
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77963, saving a snapshot embedded table while its swap write fails. The save cleared
 * the dirty flag and returned without the data, and the failed fragment was not swapped again
 * until it was read, so the table was saved without data, or with the previous data files
 * under the new row count, and later saves did not write it again. An XObjectColumn holding
 * a value Kryo can't write was saved without any data and reloaded as nulls.
 *
 * <p>The write failure is forced with the test-only {@code testBeforeWrite} hook. The save and
 * reload go through the real writeEmbeddedData() / parseEmbeddedData() / initTable() on an
 * EmbeddedTableStorage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SnapshotSwapWriteFailureTest.StorageConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotSwapWriteFailureTest {
   /**
    * A new table whose swap write fails is saved without data paths, and the next save, with
    * no read in between, writes the data.
    */
   @Test
   void failedFirstSaveIsWrittenByNextSave() throws Exception {
      XSwappableTable table = createTable("s", ROWS);
      SnapshotEmbeddedTableAssembly assembly = createAssembly(table);
      XTableFragment fragment = table.getTables()[0];

      try {
         fragment.testBeforeWrite = FAIL;
         String xml1 = writeEmbeddedData(assembly);
         fragment.testBeforeWrite = null;
         assertFalse(xml1.contains("<path>"), "data paths saved without data files");
         assertEquals(true, getField(assembly, "fileDirty"), "dirty flag cleared");

         String xml2 = writeEmbeddedData(assembly);
         assertTrue(xml2.contains("<path>"), "second save did not write the data");
         assertEquals(false, getField(assembly, "fileDirty"));

         XSwappableTable reloaded = reload(xml2);
         assertEquals(ROWS + 1, reloaded.getRowCount());
         assertEquals("s4", reloaded.getObject(5, 1));
         assertEquals(49, reloaded.getObject(ROWS, 0));
      }
      finally {
         table.dispose();
      }
   }

   /**
    * Edited data whose swap write fails must not be saved with the data files of the previous
    * save, and the next save writes the edited data.
    */
   @Test
   void failedSaveOfEditedDataDoesNotKeepOldFiles() throws Exception {
      XSwappableTable table1 = createTable("old", ROWS);
      SnapshotEmbeddedTableAssembly assembly = createAssembly(table1);
      XSwappableTable table2 = createTable("new", 80);

      try {
         writeEmbeddedData(assembly);
         String[] paths0 = assembly.getDataPaths();
         assertNotNull(paths0);

         assembly.setEmbeddedData(new XEmbeddedTable(table2));
         XTableFragment fragment = table2.getTables()[0];
         fragment.testBeforeWrite = FAIL;
         String xml1 = writeEmbeddedData(assembly);
         fragment.testBeforeWrite = null;

         for(String path : paths0) {
            assertFalse(xml1.contains(path), "previous data file saved with the edited data");
         }

         // a read in between does not matter, the data must still be written
         assertEquals("new4", table2.getObject(5, 1));
         String xml2 = writeEmbeddedData(assembly);
         assertTrue(xml2.contains("<path>"), "second save did not write the data");

         XSwappableTable reloaded = reload(xml2);
         assertEquals(81, reloaded.getRowCount());
         assertEquals("new4", reloaded.getObject(5, 1));
         assertEquals("new79", reloaded.getObject(80, 1));
      }
      finally {
         table1.dispose();
         table2.dispose();
      }
   }

   /**
    * Edited data whose swap write fails, with no read before the next save, is written by the
    * next save, and a save after that keeps the same files.
    */
   @Test
   void failedSaveOfEditedDataIsWrittenByNextSaveWithoutRead() throws Exception {
      XSwappableTable table1 = createTable("old", ROWS);
      SnapshotEmbeddedTableAssembly assembly = createAssembly(table1);
      XSwappableTable table2 = createTable("new", 80);

      try {
         writeEmbeddedData(assembly);
         String[] paths0 = assembly.getDataPaths();

         assembly.setEmbeddedData(new XEmbeddedTable(table2));
         XTableFragment fragment = table2.getTables()[0];
         fragment.testBeforeWrite = FAIL;
         writeEmbeddedData(assembly);
         fragment.testBeforeWrite = null;
         assertEquals(true, getField(assembly, "fileDirty"), "dirty flag cleared");

         String xml2 = writeEmbeddedData(assembly);
         String[] paths2 = assembly.getDataPaths();
         assertFalse(Arrays.equals(paths0, paths2), "edited data not written");
         assertEquals("new4", reload(xml2).getObject(5, 1));

         String xml3 = writeEmbeddedData(assembly);
         assertArrayEquals(paths2, assembly.getDataPaths());
         assertEquals("new79", reload(xml3).getObject(80, 1));
      }
      finally {
         table1.dispose();
         table2.dispose();
      }
   }

   /**
    * A write failure in one fragment of a table with several fragments does not save the
    * files of the other fragments, and the next save writes all of them.
    */
   @Test
   void failedFragmentOfManyIsWrittenByNextSave() throws Exception {
      int rows = 8192 * 2 + 10;
      XSwappableTable table = createTable("m", rows);
      SnapshotEmbeddedTableAssembly assembly = createAssembly(table);
      XTableFragment fragment = table.getTables()[1];

      try {
         fragment.testBeforeWrite = FAIL;
         String xml1 = writeEmbeddedData(assembly);
         fragment.testBeforeWrite = null;
         assertFalse(xml1.contains("<path>"), "data paths saved without all data files");

         String xml2 = writeEmbeddedData(assembly);
         assertNotNull(assembly.getDataPaths(), "second save did not write the data");
         assertEquals(table.getPrefixes().length, assembly.getDataPaths().length);

         XSwappableTable reloaded = reload(xml2);
         assertEquals(rows + 1, reloaded.getRowCount());
         assertEquals("m4", reloaded.getObject(5, 1));
         assertEquals("m8199", reloaded.getObject(8200, 1));
         assertEquals("m" + (rows - 1), reloaded.getObject(rows, 1));
      }
      finally {
         table.dispose();
      }
   }

   /**
    * Saves keep failing while the write fails, and the first save after it succeeds writes the
    * data.
    */
   @Test
   void repeatedFailedSavesAreWrittenByNextSave() throws Exception {
      XSwappableTable table = createTable("s", ROWS);
      SnapshotEmbeddedTableAssembly assembly = createAssembly(table);
      XTableFragment fragment = table.getTables()[0];

      try {
         fragment.testBeforeWrite = FAIL;
         assertFalse(writeEmbeddedData(assembly).contains("<path>"));
         assertFalse(writeEmbeddedData(assembly).contains("<path>"));
         fragment.testBeforeWrite = null;

         String xml = writeEmbeddedData(assembly);
         assertTrue(xml.contains("<path>"), "save after the failures did not write the data");
         assertEquals("s49", reload(xml).getObject(ROWS, 1));
      }
      finally {
         table.dispose();
      }
   }

   /**
    * One value Kryo can't write in an XObjectColumn is saved as null, the rest of the column
    * is saved, and the column stays in memory with the value.
    */
   @Test
   void unwritableValueKeepsRestOfColumn() throws Exception {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });
      Unwritable unwritable = new Unwritable();

      for(int i = 0; i < ROWS; i++) {
         // the first value is serializable, so the column is swapped
         table.addRow(new Object[] { i, i == 2 ? unwritable : "s" + i });
      }

      table.complete();
      XTableFragment fragment = table.getTables()[0];
      assertInstanceOf(XObjectColumn.class, fragment.getColumns()[1]);
      SnapshotEmbeddedTableAssembly assembly = createAssembly(table);

      try {
         String xml = writeEmbeddedData(assembly);
         assertTrue(xml.contains("<path>"));
         assertTrue(fragment.getColumns()[1].isValid(), "partly written column dropped");
         assertSame(unwritable, table.getObject(3, 1));

         XSwappableTable reloaded = reload(xml);
         assertEquals(4, reloaded.getObject(5, 0));
         assertEquals("s4", reloaded.getObject(5, 1), "column saved without its data");
         assertEquals("s0", reloaded.getObject(1, 1));
         assertNull(reloaded.getObject(3, 1));
         assertEquals("s49", reloaded.getObject(ROWS, 1));
      }
      finally {
         table.dispose();
      }
   }

   private static SnapshotEmbeddedTableAssembly createAssembly(XSwappableTable table) {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, "T77963");
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(table));
      return assembly;
   }

   private static String writeEmbeddedData(SnapshotEmbeddedTableAssembly assembly)
      throws Exception
   {
      Method method = SnapshotEmbeddedTableAssembly.class
         .getDeclaredMethod("writeEmbeddedData", PrintWriter.class);
      method.setAccessible(true);
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      method.invoke(assembly, writer);
      writer.flush();
      return buf.toString();
   }

   /**
    * Parse the saved XML into a new assembly and load its table from the saved data files.
    */
   private static XSwappableTable reload(String xml) throws Exception {
      String doc = "<root>" + xml + "</root>";
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(doc.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      SnapshotEmbeddedTableAssembly assembly =
         new SnapshotEmbeddedTableAssembly(new Worksheet(), "T77963r");
      Method method = SnapshotEmbeddedTableAssembly.class
         .getDeclaredMethod("parseEmbeddedData", Element.class);
      method.setAccessible(true);
      method.invoke(assembly, root);
      // the constructor sets an empty in-memory table, load the saved one instead
      Field stable = SnapshotEmbeddedTableAssembly.class.getDeclaredField("stable");
      stable.setAccessible(true);
      stable.set(assembly, null);

      XSwappableTable table = assembly.getTable();
      table.moreRows(XTable.EOT);
      return table;
   }

   private static Object getField(Object obj, String name) throws Exception {
      Field field = SnapshotEmbeddedTableAssembly.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(obj);
   }

   private static XSwappableTable createTable(String tag, int rows) {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < rows; i++) {
         table.addRow(new Object[] { i, tag + i });
      }

      table.complete();
      return table;
   }

   @Configuration
   static class StorageConfiguration {
      @Bean
      public EmbeddedTableStorage embeddedTableStorage(BlobStorageManager manager) {
         return new EmbeddedTableStorage(manager);
      }
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

   private static final Runnable FAIL = () -> {
      throw new UncheckedIOException(new IOException("simulated write failure"));
   };
   private static final int ROWS = 50;
}
