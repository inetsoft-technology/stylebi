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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrganizationContextHolder;
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
import inetsoft.util.DataSpace;
import inetsoft.util.FileSystemService;
import inetsoft.util.MessageException;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.PasteAssembliesService;
import inetsoft.web.composer.ws.dialog.SaveWorksheetDialogService;
import inetsoft.web.composer.ws.event.WSPasteAssembliesEvent;
import inetsoft.web.viewsheet.controller.UndoRedoService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78029, a snapshot table read from a stored worksheet has its rows only in the data files
 * it names. Save-as, paste and the save after a redo give the table new files, and must load it
 * first, or fail when its files are missing. The unloaded state is reached the way a cluster
 * reaches it: the runtime sheet is rebuilt from the distributed cache on a node that did not hold
 * it. Each stored worksheet is checked with a cold reload.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotPastedLoadTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotPastedLoadTest {
   @Autowired
   private ViewsheetService engine;

   @AfterEach
   void cleanUp() {
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

   // save-as of a sheet rebuilt from the cache stores the rows, and the sheet keeps them
   @Test
   void saveAsOfRestoredSheetKeepsData() throws Exception {
      AssetEntry entry = createStored("ws78029a");
      String id = openAndRestore(entry);

      saveAs(id, "ws78029acopy");

      assertRows(getTable(engine.getWorksheet(id, null).getWorksheet()).getTable(), 51, "old4");
      assertStored(copyEntry("ws78029acopy"), 51, "old4");
      assertStored(entry, 51, "old4");
   }

   // undo, undo, redo on a sheet rebuilt from the cache leaves an unloaded table flagged by the
   // undo. the save before the next cache flush stores the rows
   @Test
   void saveAfterRedoOfRestoredSheetKeepsData() throws Exception {
      AssetEntry entry = createStored("ws78029b");
      String id = open(entry);
      RuntimeWorksheet rws = engine.getWorksheet(id, null);

      for(String name : new String[] { "E1", "E2" }) {
         Worksheet ws = rws.getWorksheet();
         ws.addAssembly(new EmbeddedTableAssembly(ws, name));
         rws.addCheckpoint(ws.prepareCheckpoint());
      }

      engine.putRuntimeSheet(id, rws);
      evictLocal(id);
      UndoRedoService undoRedo = new UndoRedoService(null, null, null, engine, null);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      undoRedo.undo(id, null, null, dispatcher);
      undoRedo.undo(id, null, null, dispatcher);
      undoRedo.redo(id, null, null, dispatcher);

      rws = engine.getWorksheet(id, null);
      engine.setWorksheet(rws.getWorksheet(), entry, null, true, false);

      assertStored(entry, 51, "old4");
   }

   // a table pasted from a sheet rebuilt from the cache into another worksheet has the rows
   @Test
   void pasteFromRestoredSheetKeepsData() throws Exception {
      AssetEntry source = createStored("ws78029c");
      AssetEntry target = copyEntry("ws78029ctarget");
      engine.setWorksheet(new Worksheet(), target, null, true, false);
      String sourceId = openAndRestore(source);
      String targetId = open(target);

      WSPasteAssembliesEvent event = new WSPasteAssembliesEvent();
      event.setAssemblies(new String[] { NAME });
      event.setSourceRuntimeId(sourceId);
      new PasteAssembliesService(engine, DataSourceRegistry.getRegistry())
         .pasteAssemblies(targetId, event, null, mock(CommandDispatcher.class));

      RuntimeWorksheet rws = engine.getWorksheet(targetId, null);
      assertRows(getTable(rws.getWorksheet()).getTable(), 51, "old4");
      engine.setWorksheet(rws.getWorksheet(), target, null, true, false);
      assertStored(target, 51, "old4");
   }

   // the files of the table are gone: save-as fails naming the table, nothing is stored, and the
   // sheet still names the files, so a later save tries again
   @Test
   void saveAsFailsWhenDataFilesAreMissing() throws Exception {
      AssetEntry entry = createStored("ws78029d");
      String[] paths = loadStoredTable(entry).getDataPaths();
      String id = openAndRestore(entry);
      removeDataFiles(paths);

      MessageException ex = assertThrows(MessageException.class, () ->
         saveAs(id, "ws78029dcopy"));

      assertTrue(ex.getMessage().contains(NAME), ex.getMessage());
      assertFalse(AssetUtil.getAssetRepository(false).containsEntry(copyEntry("ws78029dcopy")));
      SnapshotEmbeddedTableAssembly table =
         getTable(engine.getWorksheet(id, null).getWorksheet());
      assertArrayEquals(paths, table.getDataPaths());
   }

   // a paste that fails on a table whose files are gone leaves the target unchanged, also when
   // the table comes after other assemblies of the selection
   @Test
   void failedPasteLeavesTargetUnchanged() throws Exception {
      AssetEntry source = createStored("ws78029g");
      AssetEntry target = copyEntry("ws78029gtarget");
      Worksheet tws = new Worksheet();
      tws.addAssembly(new EmbeddedTableAssembly(tws, "E1"));
      engine.setWorksheet(tws, target, null, true, false);

      String sourceId = open(source);
      Worksheet sws = engine.getWorksheet(sourceId, null).getWorksheet();
      EmbeddedTableAssembly plain = new EmbeddedTableAssembly(sws, "E1");
      plain.setPixelOffset(new java.awt.Point(0, 0));
      sws.addAssembly(plain);
      String[] paths = getTable(sws).getDataPaths();
      engine.putRuntimeSheet(sourceId, engine.getWorksheet(sourceId, null));
      evictLocal(sourceId);
      removeDataFiles(paths);
      String targetId = open(target);
      RuntimeWorksheet rws = engine.getWorksheet(targetId, null);

      for(String[] order : new String[][] { { "E1", NAME }, { NAME, "E1" } }) {
         WSPasteAssembliesEvent event = new WSPasteAssembliesEvent();
         event.setAssemblies(order);
         event.setSourceRuntimeId(sourceId);

         MessageException ex = assertThrows(MessageException.class, () ->
            new PasteAssembliesService(engine, DataSourceRegistry.getRegistry())
               .pasteAssemblies(targetId, event, null, mock(CommandDispatcher.class)));

         assertTrue(ex.getMessage().contains(NAME), ex.getMessage());
         assertEquals(List.of("E1"), getNames(rws.getWorksheet()), Arrays.toString(order));
      }

      engine.setWorksheet(rws.getWorksheet(), target, null, true, false);
      AssetUtil.getAssetRepository(false).clearCache(target);
      assertEquals(List.of("E1"), getNames((Worksheet) AssetUtil.getAssetRepository(false)
         .getSheet(target, null, false, AssetContent.ALL)));
   }

   // a table already loaded with null rows for its missing files isn't copied as if it had data
   @Test
   void pastedFailsForTableLoadedWithMissingFiles() throws Exception {
      AssetEntry entry = createStored("ws78029e");
      String[] paths = loadStoredTable(entry).getDataPaths();
      removeDataFiles(paths);
      SnapshotEmbeddedTableAssembly table = loadStoredTable(entry);
      assertNotNull(table.getTable());

      MessageException ex = assertThrows(MessageException.class, table::pasted);

      assertTrue(ex.getMessage().contains(NAME), ex.getMessage());
      assertArrayEquals(paths, table.getDataPaths());
   }

   // in another org that sees the default org, the files of a default-org worksheet are read
   // from the default org. pasted() loads them from there, as the table's own load does, and
   // doesn't report them missing
   @Test
   void pastedLoadsDataFilesFromDefaultOrg() throws Exception {
      AssetEntry entry = createStored("ws78029g");
      SnapshotEmbeddedTableAssembly hidden = loadStoredTable(entry);
      SnapshotEmbeddedTableAssembly visible = loadStoredTable(entry);
      clearLocalCopies(hidden.getDataPaths());

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         OrganizationContextHolder.setCurrentOrgId("org78029");

         sutil.when(SUtil::isDefaultVSGloballyVisible).thenReturn(false);
         assertThrows(MessageException.class, hidden::pasted);

         sutil.when(SUtil::isDefaultVSGloballyVisible).thenReturn(true);
         visible.pasted();
      }
      finally {
         OrganizationContextHolder.clear();
      }

      assertNull(visible.getDataPaths());
      assertRows(visible.getTable(), 51, "old4");
   }

   // tables whose data is not in files they own still save-as and paste: a new table, a table
   // with unsaved changes, and an outer copy, which keeps naming the files of its worksheet
   @Test
   void tablesWithoutOwnStoredDataStillPaste() throws Exception {
      AssetEntry entry = createStored("ws78029f");
      String id = open(entry);
      Worksheet ws = engine.getWorksheet(id, null).getWorksheet();
      SnapshotEmbeddedTableAssembly added = new SnapshotEmbeddedTableAssembly(ws, "T2");
      ws.addAssembly(added);
      added.setEmbeddedData(new XEmbeddedTable(createTable("new", 10)));
      getTable(ws).setEmbeddedData(new XEmbeddedTable(createTable("chg", 20)));

      saveAs(id, "ws78029fcopy");

      AssetRepository repository = AssetUtil.getAssetRepository(false);
      repository.clearCache(copyEntry("ws78029fcopy"));
      Worksheet copy = (Worksheet) repository.getSheet(
         copyEntry("ws78029fcopy"), null, false, AssetContent.ALL);
      assertRows(((SnapshotEmbeddedTableAssembly) copy.getAssembly("T2")).getTable(), 11, "new4");
      assertStored(copyEntry("ws78029fcopy"), 21, "chg4");

      String[] paths = loadStoredTable(entry).getDataPaths();
      removeDataFiles(paths);
      SnapshotEmbeddedTableAssembly outer = loadStoredTable(entry);
      outer.setOuter(true);
      outer.pasted();
      assertArrayEquals(paths, outer.getDataPaths());
   }

   private static List<String> getNames(Worksheet ws) {
      return Arrays.stream(ws.getAssemblies()).map(Assembly::getName).sorted().toList();
   }

   private void saveAs(String id, String name) throws Exception {
      AssetRepositoryPaneModel pane = new AssetRepositoryPaneModel();
      pane.setName(name);
      pane.setParentEntry(
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/", null));
      SaveWorksheetDialogModel model = SaveWorksheetDialogModel.builder()
         .worksheetOptionPaneModel(new WorksheetOptionPaneModel())
         .assetRepositoryPaneModel(pane)
         .build();
      new SaveWorksheetDialogService(engine, DataSourceRegistry.getRegistry(),
                                     DataSpace.getDataSpace())
         .saveWorksheet(id, model, null, mock(CommandDispatcher.class));
   }

   /**
    * Open the worksheet, flush it to the cache as after the open event, and drop the local copy,
    * so the next access rebuilds it from the cache as a node that did not hold it does.
    */
   private String openAndRestore(AssetEntry entry) throws Exception {
      String id = open(entry);
      engine.putRuntimeSheet(id, engine.getWorksheet(id, null));
      evictLocal(id);
      return id;
   }

   private void evictLocal(String id) throws Exception {
      Field amap = WorksheetEngine.class.getDeclaredField("amap");
      amap.setAccessible(true);
      Field local = RuntimeSheetCache.class.getDeclaredField("local");
      local.setAccessible(true);
      ((Map<?, ?>) local.get(amap.get(engine))).remove(id);
   }

   private AssetEntry createStored(String name) throws Exception {
      AssetEntry entry = copyEntry(name);
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(createTable("old", 50)));
      engine.setWorksheet(ws, entry, null, true, false);
      assertStored(entry, 51, "old4");
      return entry;
   }

   private static AssetEntry copyEntry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
   }

   private String open(AssetEntry entry) throws Exception {
      String id = engine.openWorksheet((AssetEntry) entry.clone(), null);
      opened.add(id);
      return id;
   }

   private static SnapshotEmbeddedTableAssembly loadStoredTable(AssetEntry entry)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      repository.clearCache(entry);
      return getTable((Worksheet) repository.getSheet(entry, null, false, AssetContent.ALL));
   }

   private static void removeDataFiles(String[] paths) throws Exception {
      for(String path : paths) {
         EmbeddedTableStorage.getInstance().removeTable(path + "_s.tdat");
      }

      clearLocalCopies(paths);
   }

   /**
    * Drop the loaded tables and the local copies of the data files, so the next load reads the
    * stored files.
    */
   private static void clearLocalCopies(String[] paths) {
      SnapshotEmbeddedTableDataCache.getInstance().clear();

      for(String path : paths) {
         File file = FileSystemService.getInstance().getCacheFile(path + "_s.tdat");

         if(file.exists()) {
            assertTrue(file.delete(), "local cache copy not deleted: " + file);
         }
      }
   }

   /**
    * Reload the stored worksheet cold, as after a restart or on another node, and check its data.
    */
   private static void assertStored(AssetEntry entry, int rows, String cell) throws Exception {
      String[] paths = loadStoredTable(entry).getDataPaths();
      assertNotNull(paths, "stored worksheet names no data files");
      clearLocalCopies(paths);

      for(String path : paths) {
         assertTrue(EmbeddedTableStorage.getInstance().tableExists(path + "_s.tdat"), path);
      }

      assertRows(loadStoredTable(entry).getTable(), rows, cell);
   }

   private static void assertRows(XSwappableTable table, int rows, String cell) {
      assertNotNull(table);
      table.moreRows(XTable.EOT);
      assertEquals(rows, table.getRowCount());
      assertEquals(cell, table.getObject(5, 1), "the table lost its data");
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
   private static final String NAME = "T78029";
}
