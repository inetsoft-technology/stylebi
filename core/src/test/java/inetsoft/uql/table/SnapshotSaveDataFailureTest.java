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

import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77986, a worksheet save whose snapshot data could not be written was stored without the
 * data and reported success. The table reloaded as rows of nulls, and the data files of the
 * previous save were left in storage with nothing naming them.
 *
 * <p>A save here runs the steps of {@code WorksheetEngine.setSheet}: the data files are written
 * by {@link SnapshotEmbeddedTableAssembly#writeDataFilesForSave}, the worksheet XML is written as
 * the stored version, and {@link SnapshotEmbeddedTableAssembly#finishSave} runs after it. The
 * failures are a failed swap write (the {@code testBeforeWrite} hook), a failed storage put and a
 * swap file deleted after it was written. Each one must fail the save, keep the previously
 * stored version loadable, and let a later save write the data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SnapshotSaveDataFailureTest.StorageConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotSaveDataFailureTest {
   @AfterEach
   void clearFailure() {
      FailingStorage.fail = false;
   }

   // the first save of a new table fails while the swap file can't be written, the next works
   @Test
   void failedSwapWriteOfNewTableFailsSave() throws Exception {
      XSwappableTable table = createTable("s", ROWS);
      Worksheet ws = createWorksheet(table);
      XTableFragment fragment = table.getTables()[0];

      try {
         fragment.testBeforeWrite = FAIL;
         MessageException ex = assertThrows(MessageException.class, () -> save(ws));
         fragment.testBeforeWrite = null;
         assertTrue(ex.getMessage().contains(NAME), ex.getMessage());

         String xml = save(ws);
         assertEquals("s4", reload(xml).getObject(5, 1));
      }
      finally {
         table.dispose();
      }
   }

   // a failed save of edited data keeps the stored version and its files
   @Test
   void failedSwapWriteOfEditedDataKeepsStoredVersion() throws Exception {
      XSwappableTable table1 = createTable("old", ROWS);
      Worksheet ws = createWorksheet(table1);
      XSwappableTable table2 = createTable("new", 80);

      try {
         String xml0 = save(ws);
         String[] paths0 = getTable(ws).getDataPaths();

         getTable(ws).setEmbeddedData(new XEmbeddedTable(table2));
         XTableFragment fragment = table2.getTables()[0];
         fragment.testBeforeWrite = FAIL;
         assertThrows(MessageException.class, () -> save(ws));
         fragment.testBeforeWrite = null;

         assertFilesExist(paths0, true);
         assertEquals("old4", reload(xml0).getObject(5, 1), "stored version lost its data");

         String xml2 = save(ws);
         XSwappableTable reloaded = reload(xml2);
         assertEquals(81, reloaded.getRowCount());
         assertEquals("new79", reloaded.getObject(80, 1));
         assertFilesExist(paths0, false);
      }
      finally {
         table1.dispose();
         table2.dispose();
      }
   }

   // the swap file was written, but the put into storage fails
   @Test
   void failedStoragePutFailsSave() throws Exception {
      XSwappableTable table1 = createTable("old", ROWS);
      Worksheet ws = createWorksheet(table1);
      XSwappableTable table2 = createTable("new", 80);

      try {
         String xml0 = save(ws);
         String[] paths0 = getTable(ws).getDataPaths();

         getTable(ws).setEmbeddedData(new XEmbeddedTable(table2));
         FailingStorage.fail = true;
         assertThrows(MessageException.class, () -> save(ws));
         FailingStorage.fail = false;

         assertFilesExist(paths0, true);
         assertEquals("old4", reload(xml0).getObject(5, 1), "stored version lost its data");

         String xml2 = save(ws);
         assertEquals("new79", reload(xml2).getObject(80, 1));
         assertFilesExist(paths0, false);
      }
      finally {
         table1.dispose();
         table2.dispose();
      }
   }

   // the swap file is listed but was deleted, e.g. by a temp file sweep
   @Test
   void deletedSwapFileFailsSave() throws Exception {
      XSwappableTable table1 = createTable("old", ROWS);
      Worksheet ws = createWorksheet(table1);
      XSwappableTable table2 = createTable("new", 80);

      try {
         String xml0 = save(ws);
         String[] paths0 = getTable(ws).getDataPaths();

         getTable(ws).setEmbeddedData(new XEmbeddedTable(table2));
         List<File> files = table2.getFilesList();
         assertFalse(files.isEmpty());

         for(File file : files) {
            assertTrue(file.delete());
         }

         assertThrows(MessageException.class, () -> save(ws));
         assertFilesExist(paths0, true);
         assertEquals("old4", reload(xml0).getObject(5, 1), "stored version lost its data");
      }
      finally {
         table1.dispose();
         table2.dispose();
      }
   }

   // the replaced files are deleted only after the worksheet is stored
   @Test
   void replacedFilesAreDeletedAfterWorksheetIsStored() throws Exception {
      XSwappableTable table1 = createTable("old", ROWS);
      Worksheet ws = createWorksheet(table1);
      XSwappableTable table2 = createTable("new", 80);

      try {
         save(ws);
         String[] paths0 = getTable(ws).getDataPaths();
         getTable(ws).setEmbeddedData(new XEmbeddedTable(table2));

         // the store fails after the data files were written
         SnapshotEmbeddedTableAssembly.writeDataFilesForSave(ws);
         String xml1 = writeXML(ws);
         SnapshotEmbeddedTableAssembly.finishSave(ws, false);
         assertFilesExist(paths0, true);
         assertEquals("new79", reload(xml1).getObject(80, 1));

         // a later save stores it and deletes the replaced files
         String xml2 = save(ws);
         assertFilesExist(paths0, false);
         assertEquals("new79", reload(xml2).getObject(80, 1));
      }
      finally {
         table1.dispose();
         table2.dispose();
      }
   }

   // a write that fails while the worksheet is stored is reported after it is stored
   @Test
   void failureWhileStoringIsReported() throws Exception {
      XSwappableTable table = createTable("s", ROWS);
      Worksheet ws = createWorksheet(table);
      XTableFragment fragment = table.getTables()[0];

      try {
         fragment.testBeforeWrite = FAIL;
         writeXML(ws);
         fragment.testBeforeWrite = null;
         MessageException ex = assertThrows(
            MessageException.class, () -> SnapshotEmbeddedTableAssembly.finishSave(ws, true));
         assertTrue(ex.getMessage().contains(NAME), ex.getMessage());

         // a write that works clears it
         writeXML(ws);
         assertDoesNotThrow(() -> SnapshotEmbeddedTableAssembly.finishSave(ws, true));
      }
      finally {
         table.dispose();
      }
   }

   /**
    * Save the worksheet as WorksheetEngine.setSheet does and return the stored XML.
    */
   private static String save(Worksheet ws) {
      boolean saved = false;

      try {
         SnapshotEmbeddedTableAssembly.writeDataFilesForSave(ws);
         String xml = writeXML(ws);
         saved = true;
         return xml;
      }
      finally {
         SnapshotEmbeddedTableAssembly.finishSave(ws, saved);
      }
   }

   private static String writeXML(Worksheet ws) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      ws.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   /**
    * Parse the stored XML into a new worksheet and load the table from its data files.
    */
   private static XSwappableTable reload(String xml) throws Exception {
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      Worksheet ws = new Worksheet();
      ws.parseXML(root);
      XSwappableTable table = getTable(ws).getTable();
      table.moreRows(XTable.EOT);
      return table;
   }

   private static void assertFilesExist(String[] paths, boolean exist) {
      assertNotNull(paths);

      for(String path : paths) {
         assertEquals(exist, EmbeddedTableStorage.getInstance().tableExists(path + "_s.tdat"),
                      "data file " + path + (exist ? " deleted" : " not deleted"));
      }
   }

   private static SnapshotEmbeddedTableAssembly getTable(Worksheet ws) {
      return (SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME);
   }

   private static Worksheet createWorksheet(XSwappableTable table) {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(table));
      return ws;
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
         return new FailingStorage(manager);
      }
   }

   /**
    * Storage whose put fails while {@link #fail} is set.
    */
   static class FailingStorage extends EmbeddedTableStorage {
      FailingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public void writeTable(String path, InputStream input, boolean temp) throws IOException {
         if(fail) {
            throw new IOException("simulated storage put failure");
         }

         super.writeTable(path, input, temp);
      }

      static volatile boolean fail;
   }

   private static final Runnable FAIL = () -> {
      throw new UncheckedIOException(new IOException("simulated write failure"));
   };
   private static final String NAME = "T77986";
   private static final int ROWS = 50;
}
