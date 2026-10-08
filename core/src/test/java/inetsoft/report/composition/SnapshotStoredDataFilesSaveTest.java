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
package inetsoft.report.composition;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.table.SnapshotEmbeddedTableDataCache;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.FileSystemService;
import inetsoft.util.MessageException;
import inetsoft.web.composer.ws.TableModeService;
import inetsoft.web.composer.ws.WSEditTableDataService;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.composer.ws.event.WSEditTableDataEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78012, saves around the stored data files of a snapshot table that
 * {@link SnapshotStoredDataFilesTest} does not cover: a save that fails while it marks the flushed
 * (temp) files as permanent, a save-as of a stored worksheet, and the edit-mode refusal for
 * snapshot tables next to a normal embedded table. Each stored worksheet is checked with a cold
 * reload (no data cache, no local cache copy, no cached sheet).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotStoredDataFilesSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotStoredDataFilesSaveTest {
   @Autowired
   private ViewsheetService engine;

   @AfterEach
   void cleanUp() {
      FailingStorage.failPermanentWrite = false;

      for(String id : opened) {
         try {
            engine.closeWorksheet(id, null);
         }
         catch(Exception ignore) {
         }
      }

      opened.clear();
      tables.forEach(XSwappableTable::dispose);
      tables.clear();
   }

   // after a cache flush the save only marks the flushed files as permanent. if that fails, the
   // save fails and the stored worksheet keeps its files and data (bug #77986). the retry stores
   // the new data and deletes the replaced files
   @Test
   void failedSaveAfterFlushKeepsStoredData() throws Exception {
      AssetEntry entry = createStored("ws78012s1");
      String[] paths0 = loadStoredTable(entry).getDataPaths();
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);
      changeAndFlush(id, rws, "new", 80);

      FailingStorage.failPermanentWrite = true;
      MessageException ex = assertThrows(MessageException.class, () ->
         engine.setWorksheet(rws.getWorksheet(), entry, null, true, false));
      FailingStorage.failPermanentWrite = false;
      assertTrue(ex.getMessage().contains(NAME), ex.getMessage());
      assertStoredFiles(paths0, true);
      assertStored(entry, 51, 5, "old4");

      engine.setWorksheet(rws.getWorksheet(), entry, null, true, false);
      close(id);

      assertStoredFiles(paths0, false);
      assertStored(entry, 81, 80, "new79");
      assertNotTemp(entry);
   }

   // save-as gives the copy its own files. saving changes to the copy, then to the original,
   // never deletes the files the other one names
   @Test
   void saveAsKeepsOriginalFiles() throws Exception {
      AssetEntry entry = createStored("ws78012s2");
      AssetEntry copyEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.WORKSHEET, "ws78012s2copy", null);
      String[] paths0 = loadStoredTable(entry).getDataPaths();
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);
      Worksheet ws = rws.getWorksheet();

      // as SaveWorksheetDialogService does for a save-as
      for(Assembly assembly : ws.getAssemblies()) {
         ((AbstractWSAssembly) assembly).pasted();
      }

      SnapshotEmbeddedTableAssembly.writeDataFilesForSave(ws);
      engine.setWorksheet(ws, copyEntry, null, true, false);
      String[] copyPaths0 = getTable(ws).getDataPaths();
      assertFalse(Arrays.equals(paths0, copyPaths0), "the copy names the files of the original");
      assertStoredFiles(paths0, true);
      assertStored(entry, 51, 5, "old4");
      assertStored(copyEntry, 51, 5, "old4");

      changeAndFlush(id, rws, "copy", 70);
      engine.setWorksheet(rws.getWorksheet(), copyEntry, null, true, false);
      String[] copyPaths1 = getTable(rws.getWorksheet()).getDataPaths();
      close(id);

      assertStoredFiles(paths0, true);
      assertStoredFiles(copyPaths0, false);
      assertStored(entry, 51, 5, "old4");
      assertStored(copyEntry, 71, 70, "copy69");

      id = open(entry);
      RuntimeWorksheet rws2 = engine.getWorksheet(id, null);
      changeAndFlush(id, rws2, "orig", 20);
      engine.setWorksheet(rws2.getWorksheet(), entry, null, true, false);
      close(id);

      assertStoredFiles(paths0, false);
      assertStoredFiles(copyPaths1, true);
      assertStored(entry, 21, 20, "orig19");
      assertStored(copyEntry, 71, 70, "copy69");
   }

   // edit mode and cell edits are refused for a snapshot table and still work for a normal
   // embedded table in the same worksheet
   @Test
   void editModeRefusedOnlyForSnapshotTable() throws Exception {
      AssetEntry entry = createStored("ws78012s3");
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);
      Worksheet ws = rws.getWorksheet();
      EmbeddedTableAssembly plain = new EmbeddedTableAssembly(ws, PLAIN);
      ws.addAssembly(plain);
      plain.setEmbeddedData(new XEmbeddedTable(createTable("plain", 5)));
      TableModeService modes = new TableModeService(
         engine, mock(AssetDataCache.class), mock(DataSourceRegistry.class));
      WSEditTableDataService edits = new WSEditTableDataService(
         engine, mock(AssetDataCache.class), mock(DataSourceRegistry.class));
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      modes.setEditMode(id, createModeEvent(NAME), null, dispatcher);
      edits.editTableData(id, createEditEvent(NAME, "edited"), null, dispatcher);
      SnapshotEmbeddedTableAssembly snapshot = getTable(ws);
      assertFalse(snapshot.isEditMode());
      assertEquals("old2", snapshot.getEmbeddedData().getObject(3, 1));

      modes.setEditMode(id, createModeEvent(PLAIN), null, dispatcher);
      edits.editTableData(id, createEditEvent(PLAIN, "edited"), null, dispatcher);
      plain = (EmbeddedTableAssembly) ws.getAssembly(PLAIN);
      assertTrue(plain.isEditMode());
      assertEquals("edited", plain.getEmbeddedData().getObject(3, 1));
   }

   private static WSAssemblyEvent createModeEvent(String table) throws Exception {
      WSAssemblyEvent event = new WSAssemblyEvent();
      Field field = WSAssemblyEvent.class.getDeclaredField("assemblyName");
      field.setAccessible(true);
      field.set(event, table);
      return event;
   }

   private static WSEditTableDataEvent createEditEvent(String table, String data) {
      WSEditTableDataEvent event = new WSEditTableDataEvent();
      event.setTableName(table);
      event.setX(1);
      event.setY(3);
      event.setEditData(data);
      return event;
   }

   /**
    * Change the data, add the undo checkpoint and flush the runtime sheet to the cache, as after
    * any write event (temp write).
    */
   private void changeAndFlush(String id, RuntimeWorksheet rws, String tag, int rows) {
      Worksheet ws = rws.getWorksheet();
      getTable(ws).setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      rws.addCheckpoint(ws.prepareCheckpoint());
      engine.putRuntimeSheet(id, rws);
   }

   private AssetEntry createStored(String name) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        name, null);
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(createTable("old", 50)));
      engine.setWorksheet(ws, entry, null, true, false);
      assertStored(entry, 51, 5, "old4");
      return entry;
   }

   private String open(AssetEntry entry) throws Exception {
      String id = engine.openWorksheet((AssetEntry) entry.clone(), null);
      opened.add(id);
      return id;
   }

   private void close(String id) throws Exception {
      opened.remove(id);
      engine.closeWorksheet(id, null);
   }

   private static SnapshotEmbeddedTableAssembly loadStoredTable(AssetEntry entry)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      repository.clearCache(entry);
      return getTable((Worksheet) repository.getSheet(entry, null, false, AssetContent.ALL));
   }

   /**
    * Reload the stored worksheet cold, as after a restart or on another node, and check its data.
    */
   private static void assertStored(AssetEntry entry, int rows, int row, String cell)
      throws Exception
   {
      String[] paths = loadStoredTable(entry).getDataPaths();
      assertNotNull(paths, "stored worksheet names no data files");
      SnapshotEmbeddedTableDataCache.getInstance().clear();

      for(String path : paths) {
         File file = FileSystemService.getInstance().getCacheFile(path + "_s.tdat");

         if(file.exists()) {
            assertTrue(file.delete(), "local cache copy not deleted: " + file);
         }
      }

      assertStoredFiles(paths, true);
      XSwappableTable table = loadStoredTable(entry).getTable();
      table.moreRows(XTable.EOT);
      assertEquals(rows, table.getRowCount(), entry.getName());
      assertEquals(cell, table.getObject(row, 1), entry.getName() + " lost its data");
   }

   private static void assertNotTemp(AssetEntry entry) throws Exception {
      for(String path : loadStoredTable(entry).getDataPaths()) {
         assertFalse(EmbeddedTableStorage.getInstance().isTempTable(path + "_s.tdat"),
                     "stored worksheet names a temp file: " + path);
      }
   }

   private static void assertStoredFiles(String[] paths, boolean exist) {
      assertNotNull(paths);

      for(String path : paths) {
         assertEquals(exist, EmbeddedTableStorage.getInstance().tableExists(path + "_s.tdat"),
                      "data file " + path + (exist ? " deleted" : " not deleted"));
      }
   }

   private static SnapshotEmbeddedTableAssembly getTable(Worksheet ws) {
      return (SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME);
   }

   private XSwappableTable createTable(String tag, int rows) {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < rows; i++) {
         table.addRow(new Object[] { i, tag + i });
      }

      table.complete();
      tables.add(table);
      return table;
   }

   @Configuration
   static class Beans {
      // the constructor is package private; saving a sheet updates the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      // IntegrationTestConfiguration defines the storage bean, replace it with one that can fail
      @Bean
      public static BeanPostProcessor failingEmbeddedTableStorage(
         ObjectProvider<BlobStorageManager> manager)
      {
         return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
               return bean instanceof EmbeddedTableStorage && !(bean instanceof FailingStorage) ?
                  new FailingStorage(manager.getObject()) : bean;
            }
         };
      }
   }

   /**
    * Storage whose non-temp put fails while {@link #failPermanentWrite} is set.
    */
   static class FailingStorage extends EmbeddedTableStorage {
      FailingStorage(BlobStorageManager manager) {
         super(manager);
      }

      @Override
      public void writeTable(String path, InputStream input, boolean temp) throws IOException {
         if(failPermanentWrite && !temp) {
            throw new IOException("simulated storage put failure");
         }

         super.writeTable(path, input, temp);
      }

      static volatile boolean failPermanentWrite;
   }

   private final List<String> opened = new ArrayList<>();
   private final List<XSwappableTable> tables = new ArrayList<>();
   private static final String NAME = "T78012";
   private static final String PLAIN = "E78012";
}
