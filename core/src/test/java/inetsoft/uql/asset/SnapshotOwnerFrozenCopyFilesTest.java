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
package inetsoft.uql.asset;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.table.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.FileSystemService;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78032: a frozen (Auto Update off) outer copy stored before bug #78023 was fixed names the
 * data files of the worksheet W1 it was copied from, without owning them. Saving or removing W1
 * must not delete those files, and must not move an open copy to W1's new data. The worksheets
 * embedding W1 are found in the dependency storage, so the saves update it, as the composer does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotOwnerFrozenCopyFilesTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotOwnerFrozenCopyFilesTest {
   @Autowired
   private WorksheetService worksheetService;
   @Autowired
   private ViewsheetService engine;

   @Test
   void legacyFrozenCopyKeepsItsDataAfterOwnerSave() throws Exception {
      AssetEntry e1 = entry("ofc_save_w1");
      AssetEntry e2 = entry("ofc_save_w2");
      String[] paths1 = saveOwner(e1, "old", 50);
      storeLegacyFrozen(e1, e2, paths1);

      String[] paths2 = resaveOwner(e1, "new", 80);

      assertFilesExist(paths1, true, "files of the frozen copy deleted by W1's save");
      XSwappableTable table = coldLoad(e2, null, paths1, paths2);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
      assertEquals("new4", coldLoad(e1, NAME, paths1, paths2).getObject(5, 1));
   }

   @Test
   void legacyFrozenCopyKeepsItsDataAfterOwnerRemoval() throws Exception {
      AssetEntry e1 = entry("ofc_remove_w1");
      AssetEntry e2 = entry("ofc_remove_w2");
      String[] paths1 = saveOwner(e1, "old", 50);
      storeLegacyFrozen(e1, e2, paths1);

      repository().removeSheet(e1, null, true);

      assertFilesExist(paths1, true, "files of the frozen copy deleted by removing W1");
      XSwappableTable table = coldLoad(e2, null, paths1);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
   }

   // a private worksheet embedding W1 is read from its user's scope
   @Test
   void privateLegacyFrozenCopyKeepsItsDataAfterOwnerSave() throws Exception {
      AssetEntry e1 = entry("ofc_user_w1");
      AssetEntry e2 = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "ofc_user_w2", new IdentityID("ofc_user", e1.getOrgID()));
      String[] paths1 = saveOwner(e1, "old", 50);
      storeLegacyFrozen(e1, e2, paths1);

      String[] paths2 = resaveOwner(e1, "new", 80);

      assertFilesExist(paths1, true, "files of the frozen copy deleted by W1's save");
      XSwappableTable table = coldLoad(e2, null, paths1, paths2);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
   }

   // W2 open in this server while W1 is saved: the copy is not moved to W1's new files
   @Test
   void openLegacyFrozenCopyKeepsItsDataDuringOwnerSave() throws Exception {
      AssetEntry e1 = entry("ofc_open_w1");
      AssetEntry e2 = entry("ofc_open_w2");
      String[] paths1 = saveOwner(e1, "old", 50);
      storeLegacyFrozen(e1, e2, paths1);
      clearCaches(e2, paths1);
      Worksheet ws2 = open(e2);
      assertArrayEquals(paths1, outerCopy(ws2).getDataPaths());

      resaveOwner(e1, "new", 80);

      assertArrayEquals(paths1, outerCopy(ws2).getDataPaths(), "open copy moved to W1's files");
      XSwappableTable table = outerCopy(ws2).getTable();
      table.moreRows(XTable.EOT);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));

      // its save gives it its own files with the old data
      save(ws2, e2);
      String[] copyPaths = storedCopy(e2).getDataPaths();
      assertFalse(Arrays.equals(paths1, copyPaths));
      assertEquals("old4", coldLoad(e2, null, copyPaths, paths1).getObject(5, 1));
   }

   // W2 open in the composer (a runtime worksheet) while W1 is saved in another one, then W2 saved
   @Test
   void composerOpenLegacyFrozenCopyKeepsItsDataDuringOwnerSave() throws Exception {
      AssetEntry e1 = entry("ofc_rws_w1");
      AssetEntry e2 = entry("ofc_rws_w2");
      String[] paths1 = saveOwner(e1, "old", 50);
      storeLegacyFrozen(e1, e2, paths1);
      clearCaches(e2, paths1);
      String id2 = engine.openWorksheet((AssetEntry) e2.clone(), null);

      try {
         Worksheet ws2 = engine.getWorksheet(id2, null).getWorksheet();
         String id1 = engine.openWorksheet((AssetEntry) e1.clone(), null);

         try {
            RuntimeWorksheet rws1 = engine.getWorksheet(id1, null);
            ((SnapshotEmbeddedTableAssembly) rws1.getWorksheet().getAssembly(NAME))
               .setEmbeddedData(new XEmbeddedTable(createTable("new", 80)));
            save(rws1.getWorksheet(), e1);
         }
         finally {
            engine.closeWorksheet(id1, null);
         }

         assertArrayEquals(paths1, outerCopy(ws2).getDataPaths(), "open copy moved to W1's files");
         XSwappableTable table = outerCopy(ws2).getTable();
         table.moreRows(XTable.EOT);
         assertEquals(51, table.getRowCount());
         assertEquals("old4", table.getObject(5, 1));

         save(ws2, e2);
      }
      finally {
         engine.closeWorksheet(id2, null);
      }

      SnapshotEmbeddedTableAssembly copy = storedCopy(e2);
      assertTrue(copy.ownsDataFiles());
      XSwappableTable table = coldLoad(e2, null, copy.getDataPaths(), paths1);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
   }

   // only the files the frozen copy names are kept, later replaced files of W1 are deleted, and an
   // auto-updated copy next to the frozen one gets W1's new data
   @Test
   void ownerSavesKeepOnlyFilesNamedByLegacyCopy() throws Exception {
      AssetEntry e1 = entry("ofc_two_w1");
      AssetEntry e2 = entry("ofc_two_w2");
      AssetEntry e3 = entry("ofc_two_w3");
      String[] paths1 = saveOwner(e1, "old", 50);
      storeLegacyFrozen(e1, e2, paths1);
      save(embed(e1, true), e3);

      String[] paths2 = resaveOwner(e1, "new", 80);
      assertFilesExist(paths1, true, "files of the frozen copy deleted by W1's save");
      assertEquals("new4", coldLoad(e3, null, paths1, paths2).getObject(5, 1));

      String[] paths3 = resaveOwner(e1, "newer", 90);
      assertFilesExist(paths2, false, "W1's replaced files not deleted");
      assertFilesExist(paths1, true, "files of the frozen copy deleted by W1's second save");
      XSwappableTable table = coldLoad(e2, null, paths1, paths2, paths3);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
      assertEquals("newer4", coldLoad(e3, null, paths1, paths2, paths3).getObject(5, 1));
   }

   // the embedding worksheets are read as stored, not opened
   @Test
   void frozenCopyPathsAreReadFromStoredWorksheets() throws Exception {
      AssetEntry e1 = entry("ofc_read_w1");
      AssetEntry frozen = entry("ofc_read_frozen");
      AssetEntry auto = entry("ofc_read_auto");
      AssetEntry detached = entry("ofc_read_detached");
      String[] paths1 = saveOwner(e1, "old", 50);
      storeLegacyFrozen(e1, frozen, paths1);
      save(embed(e1, true), auto);
      save(embed(e1, false), detached);

      Set<String> paths =
         SnapshotEmbeddedTableAssembly.getFrozenCopyDataPaths(repository(), e1);

      assertEquals(new HashSet<>(Arrays.asList(paths1)), paths);
   }

   // an auto-updated copy is copied again from W1 when it is opened, its files are not kept
   @Test
   void ownerSaveDeletesFilesOfAutoUpdatedCopy() throws Exception {
      AssetEntry e1 = entry("ofc_auto_w1");
      AssetEntry e2 = entry("ofc_auto_w2");
      String[] paths1 = saveOwner(e1, "old", 50);
      save(embed(e1, true), e2);

      String[] paths2 = resaveOwner(e1, "new", 80);

      assertFilesExist(paths1, false, "W1's replaced files not deleted");
      XSwappableTable table = coldLoad(e2, null, paths1, paths2);
      assertEquals(81, table.getRowCount());
      assertEquals("new4", table.getObject(5, 1));
   }

   // a frozen copy saved since bug #78023 was fixed has its own files
   @Test
   void ownerSaveDeletesFilesNotNamedByLegacyCopy() throws Exception {
      AssetEntry e1 = entry("ofc_own_w1");
      AssetEntry e2 = entry("ofc_own_w2");
      String[] paths1 = saveOwner(e1, "old", 50);
      save(embed(e1, false), e2);
      String[] copyPaths = storedCopy(e2).getDataPaths();
      assertTrue(storedCopy(e2).ownsDataFiles());

      String[] paths2 = resaveOwner(e1, "new", 80);

      assertFilesExist(paths1, false, "W1's replaced files not deleted");
      assertEquals("old4", coldLoad(e2, null, copyPaths, paths1, paths2).getObject(5, 1));

      repository().removeSheet(e1, null, true);
      assertFilesExist(paths2, false, "W1's files not deleted with it");
      assertFilesExist(copyPaths, true, "frozen copy's files deleted with W1");
   }

   @Test
   void ownerWithoutEmbeddingWorksheetsDeletesItsFiles() throws Exception {
      AssetEntry e1 = entry("ofc_alone_w1");
      String[] paths1 = saveOwner(e1, "old", 50);

      String[] paths2 = resaveOwner(e1, "new", 80);
      assertFilesExist(paths1, false, "replaced files not deleted");

      repository().removeSheet(e1, null, true);
      assertFilesExist(paths2, false, "files not deleted with the worksheet");
   }

   private String[] saveOwner(AssetEntry entry, String tag, int rows) throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
      return table.getDataPaths();
   }

   private String[] resaveOwner(AssetEntry entry, String tag, int rows) throws Exception {
      Worksheet ws = open(entry);
      SnapshotEmbeddedTableAssembly table = (SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
      return table.getDataPaths();
   }

   /**
    * Store W2 embedding W1 with Auto Update off, as a server before bug #78023 was fixed did: the
    * copy names W1's files and has no ownership flag. That is what a save with Auto Update on
    * stores, the stored mirror is then changed without the composer save.
    */
   private void storeLegacyFrozen(AssetEntry e1, AssetEntry e2, String[] paths1)
      throws Exception
   {
      save(embed(e1, true), e2);
      IndexedStorage storage = repository().getStorage(e2);
      Worksheet stored = (Worksheet) storage.getXMLSerializable(e2.toIdentifier(), null);
      ((MirrorTableAssembly) stored.getAssembly(MIRROR)).setAutoUpdate(false);
      storage.putXMLSerializable(e2.toIdentifier(), stored);

      SnapshotEmbeddedTableAssembly copy = storedCopy(e2);
      assertFalse(copy.ownsDataFiles());
      assertArrayEquals(paths1, copy.getDataPaths());
   }

   /**
    * Embed a worksheet the way dragging it into a worksheet in the composer does.
    */
   private static Worksheet embed(AssetEntry entry, boolean autoUpdate) throws Exception {
      Worksheet ws = new Worksheet();
      WSAssembly[] created = AssetUtil.copyOuterAssemblies(repository(), entry, null, ws, null);
      MirrorTableAssembly mirror =
         new MirrorTableAssembly(ws, MIRROR, entry, true, created[created.length - 1]);
      mirror.setAutoUpdate(autoUpdate);
      ws.addAssembly(mirror);
      ws.setPrimaryAssembly(MIRROR);
      return ws;
   }

   // as the composer saves, updating the dependency storage
   private void save(Worksheet ws, AssetEntry entry) throws Exception {
      worksheetService.setWorksheet(ws, entry, null, true, true);
   }

   private static Worksheet open(AssetEntry entry) throws Exception {
      return (Worksheet) repository().getSheet(entry, null, false, AssetContent.ALL);
   }

   private static SnapshotEmbeddedTableAssembly outerCopy(Worksheet ws) {
      SnapshotEmbeddedTableAssembly copy = null;

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof SnapshotEmbeddedTableAssembly &&
            ((SnapshotEmbeddedTableAssembly) assembly).isOuter())
         {
            assertNull(copy, "more than one outer snapshot table");
            copy = (SnapshotEmbeddedTableAssembly) assembly;
         }
      }

      assertNotNull(copy, "no outer snapshot table");
      return copy;
   }

   private static SnapshotEmbeddedTableAssembly storedCopy(AssetEntry entry) throws Exception {
      Worksheet ws = (Worksheet) repository().getStorage(entry)
         .getXMLSerializable(entry.toIdentifier(), null);
      return outerCopy(ws);
   }

   /**
    * Load a snapshot table as another node would, without this node's cached copy of its files.
    *
    * @param name the table name, or {@code null} for the outer snapshot table.
    */
   private static XSwappableTable coldLoad(AssetEntry entry, String name, String[]... paths)
      throws Exception
   {
      clearCaches(entry, paths);
      Worksheet ws = open(entry);
      SnapshotEmbeddedTableAssembly assembly = name == null ?
         outerCopy(ws) : (SnapshotEmbeddedTableAssembly) ws.getAssembly(name);
      XSwappableTable table = assembly.getTable();
      table.moreRows(XTable.EOT);
      return table;
   }

   private static void clearCaches(AssetEntry entry, String[]... paths) {
      repository().clearCache(entry);
      SnapshotEmbeddedTableDataCache.getInstance().clear();

      for(String[] list : paths) {
         for(String path : list) {
            FileSystemService.getInstance().getCacheFile(path + "_s.tdat").delete();
         }
      }
   }

   private static void assertFilesExist(String[] paths, boolean exist, String message) {
      assertNotNull(paths);
      assertTrue(paths.length > 0);

      for(String path : paths) {
         assertEquals(exist, EmbeddedTableStorage.getInstance().tableExists(path + "_s.tdat"),
                      message + ": " + path);
      }
   }

   private static AbstractAssetEngine repository() {
      return (AbstractAssetEngine) AssetUtil.getAssetRepository(false);
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
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

      // the real dependency handler (the base configuration mocks it), the worksheets
      // embedding a worksheet are found by it
      @Bean
      @Primary
      public DependencyHandler realDependencyHandler(XRepository xRepository) {
         return new LocalDependencyHandler(xRepository);
      }

      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   private static final String NAME = "T";
   private static final String MIRROR = "M";
}
