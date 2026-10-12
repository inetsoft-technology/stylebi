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
import inetsoft.web.composer.ws.WSEditTableDataService;
import inetsoft.web.composer.ws.event.WSEditTableDataEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78012, the data files that a stored worksheet names for a snapshot table were deleted
 * while the stored worksheet still named them, so it reloaded with null cells:
 * <ul>
 * <li>A: a cell edit deleted them at once, before any save.</li>
 * <li>B': a cache flush (a temp write after each change) queued them for deletion in a set that
 * every copy of the cached worksheet shared, and a later save of an unchanged copy (the same user
 * after closing without saving, or another session) deleted them.</li>
 * </ul>
 * Each case runs the real worksheet engine and asset repository, and checks the stored worksheet
 * with a cold reload (no data cache, no local cache copy, no cached sheet), as after a restart.
 * A reopen does not clear the repository cache, a user reopens the cached worksheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotStoredDataFilesTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotStoredDataFilesTest {
   @Autowired
   private ViewsheetService engine;

   @AfterEach
   void cleanUp() throws Exception {
      for(String id : opened) {
         try {
            engine.closeWorksheet(id, null);
         }
         catch(Exception ignore) {
         }
      }

      opened.clear();

      for(XSwappableTable table : tables) {
         table.dispose();
      }

      tables.clear();
   }

   // A: a cell edit request on a snapshot table, then close without saving
   @Test
   void cellEditDoesNotDeleteStoredFiles() throws Exception {
      AssetEntry entry = createStored("ws78012a");
      String[] paths0 = loadStoredTable(entry).getDataPaths();
      String id = open(entry);
      WSEditTableDataService service = new WSEditTableDataService(
         engine, mock(AssetDataCache.class), mock(DataSourceRegistry.class));
      WSEditTableDataEvent event = new WSEditTableDataEvent();
      event.setTableName(NAME);
      event.setX(1);
      event.setY(3);
      event.setEditData("edited");

      service.editTableData(id, event, null, mock(CommandDispatcher.class));
      assertStoredFiles(paths0, true);
      close(id);

      assertStored(entry, 51, 5, "old4");
   }

   // B' one user: change, cache flush, close without saving, reopen, save unchanged
   @Test
   void flushedChangeClosedWithoutSaveKeepsStoredFiles() throws Exception {
      AssetEntry entry = createStored("ws78012b");
      String[] paths0 = loadStoredTable(entry).getDataPaths();
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);
      changeAndFlush(id, rws);
      close(id);

      id = open(entry);
      rws = engine.getWorksheet(id, null);
      assertArrayEquals(paths0, getTable(rws.getWorksheet()).getDataPaths());
      engine.setWorksheet(rws.getWorksheet(), entry, null, true, false);
      close(id);

      assertStoredFiles(paths0, true);
      assertStored(entry, 51, 5, "old4");
   }

   // B' two sessions: one session changes its copy and flushes, the other saves its copy unchanged
   @Test
   void otherSessionSaveKeepsStoredFiles() throws Exception {
      AssetEntry entry = createStored("ws78012c");
      String[] paths0 = loadStoredTable(entry).getDataPaths();
      Worksheet other = getCachedCopy(entry);
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);
      changeAndFlush(id, rws);

      engine.setWorksheet(other, entry, null, true, false);
      close(id);

      assertStoredFiles(paths0, true);
      assertStored(entry, 51, 5, "old4");
   }

   // C1: one session saves new data, then another session saves its stale unchanged copy
   @Test
   void staleSessionSaveKeepsNewData() throws Exception {
      AssetEntry entry = createStored("ws78012d");
      String[] paths0 = loadStoredTable(entry).getDataPaths();
      Worksheet other = getCachedCopy(entry);
      Worksheet ws = getCachedCopy(entry);
      getTable(ws).setEmbeddedData(new XEmbeddedTable(createTable("new", 80)));

      engine.setWorksheet(ws, entry, null, true, false);
      engine.setWorksheet(other, entry, null, true, false);

      assertStoredFiles(paths0, false);
      assertStored(entry, 81, 80, "new79");
   }

   // an ordinary change, cache flush and save deletes the replaced files and stores the new ones
   // as permanent files
   @Test
   void saveAfterFlushDeletesReplacedFiles() throws Exception {
      AssetEntry entry = createStored("ws78012e");
      String[] paths0 = loadStoredTable(entry).getDataPaths();
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);
      changeAndFlush(id, rws);
      engine.setWorksheet(rws.getWorksheet(), entry, null, true, false);
      close(id);

      assertStoredFiles(paths0, false);
      assertStored(entry, 81, 80, "new79");
      assertNotTemp(entry);
   }

   // after undo and redo the table is not dirty, its files were written by a temp write. the save
   // must not store a worksheet that names temp files, which expire after 14 days
   @Test
   void saveAfterUndoRedoStoresPermanentFiles() throws Exception {
      AssetEntry entry = createStored("ws78012f");
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);
      changeAndFlush(id, rws);
      assertTrue(rws.undo(new ChangedAssemblyList()));
      assertTrue(rws.redo(new ChangedAssemblyList()));
      engine.putRuntimeSheet(id, rws);
      engine.setWorksheet(rws.getWorksheet(), entry, null, true, false);
      close(id);

      assertStored(entry, 81, 80, "new79");
      assertNotTemp(entry);
   }

   /**
    * Change the data as Import CSV into an existing snapshot table does, add the undo checkpoint
    * and flush the runtime sheet to the cache, as after any write event (temp write).
    */
   private void changeAndFlush(String id, RuntimeWorksheet rws) {
      Worksheet ws = rws.getWorksheet();
      getTable(ws).setEmbeddedData(new XEmbeddedTable(createTable("new", 80)));
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

   /**
    * A copy of the cached stored worksheet, as another session gets it.
    */
   private static Worksheet getCachedCopy(AssetEntry entry) throws Exception {
      return (Worksheet) AssetUtil.getAssetRepository(false)
         .getSheet(entry, null, false, AssetContent.ALL);
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
   private static void assertStored(AssetEntry entry, int rows, int row, String cell) throws Exception {
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
      assertEquals(rows, table.getRowCount());
      assertEquals(cell, table.getObject(row, 1), "stored worksheet lost its data");
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
   }

   private final List<String> opened = new ArrayList<>();
   private final List<XSwappableTable> tables = new ArrayList<>();
   private static final String NAME = "T78012";
}
