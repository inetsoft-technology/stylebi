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

import inetsoft.report.composition.WorksheetService;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.FileSystemService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78022, #78023: an outer copy of a snapshot table, made when a worksheet W1 is embedded in
 * a worksheet W2, names W1's data files. Only the table that wrote the files may delete or replace
 * them, and an outer copy that is not copied again when W2 is opened (Auto Update off) gets its
 * own files when W2 is saved. Runs through {@code WorksheetEngine.setWorksheet} and the real asset
 * repository.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotWorksheetSaveFailureTest.Beans.class,
                                  SnapshotOuterCopyOwnershipTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotOuterCopyOwnershipTest {
   @Autowired
   private WorksheetService worksheetService;

   // P1: removing W2 deleted W1's files
   @Test
   void removingEmbeddingWorksheetKeepsOwnerFiles() throws Exception {
      AssetEntry e1 = entry("own_p1_w1");
      AssetEntry e2 = entry("own_p1_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();

      Worksheet ws2 = embed(e1, true);
      SnapshotEmbeddedTableAssembly own = new SnapshotEmbeddedTableAssembly(ws2, "Own");
      ws2.addAssembly(own);
      own.setEmbeddedData(new XEmbeddedTable(createTable("own", 10)));
      save(ws2, e2);
      String[] ownPaths = own.getDataPaths();
      assertArrayEquals(paths1, storedCopy(e2).getDataPaths(), "W2 should share W1's files");

      repository().removeSheet(e2, null, true);

      assertFilesExist(paths1, true, "W1's files deleted by removing W2");
      assertFilesExist(ownPaths, false, "W2's own files not deleted with it");
      XSwappableTable table = coldLoad(e1, NAME, paths1);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
   }

   // P2: saving W2 after W1 was saved with new data deleted W1's current files
   @Test
   void savingEmbeddingWorksheetAfterOwnerSaveKeepsOwnerFiles() throws Exception {
      AssetEntry e1 = entry("own_p2_w1");
      AssetEntry e2 = entry("own_p2_w2");
      saveOwner(e1, "old", 50);
      Worksheet ws2 = embed(e1, true);
      save(ws2, e2);

      // W1 parsed again, e.g. in another session, has another file prefix than the copy
      repository().clearCache(e1);
      String[] paths2 = resaveOwner(e1, "new", 80);
      assertArrayEquals(paths2, outerCopy(ws2).getDataPaths(), "open copy not updated");
      save(ws2, e2);

      assertFilesExist(paths2, true, "W1's current files deleted by saving W2");
      assertArrayEquals(paths2, outerCopy(ws2).getDataPaths(), "W2 wrote files of its own");
      assertArrayEquals(paths2, storedCopy(e2).getDataPaths());
      XSwappableTable table = coldLoad(e1, NAME, paths2);
      assertEquals("new4", table.getObject(5, 1));
   }

   // P3: W1's save deleted the files a frozen W2 still read
   @Test
   void frozenCopyKeepsItsDataAfterOwnerSave() throws Exception {
      AssetEntry e1 = entry("own_p3_w1");
      AssetEntry e2 = entry("own_p3_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      save(embed(e1, false), e2);
      String[] copyPaths = storedCopy(e2).getDataPaths();
      assertFalse(Arrays.equals(paths1, copyPaths), "frozen copy should have its own files");

      String[] paths2 = resaveOwner(e1, "new", 80);
      assertFilesExist(paths1, false, "W1's replaced files not deleted");

      XSwappableTable table = coldLoad(e2, null, copyPaths, paths1);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));

      repository().removeSheet(e2, null, true);
      assertFilesExist(copyPaths, false, "frozen copy's files not deleted with W2");
      assertFilesExist(paths2, true, "W1's files deleted by removing W2");
   }

   // Auto Update turned off after the copy was made
   @Test
   void copyFrozenAfterItWasMadeGetsItsOwnFiles() throws Exception {
      AssetEntry e1 = entry("own_toggle_w1");
      AssetEntry e2 = entry("own_toggle_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      save(embed(e1, true), e2);
      assertArrayEquals(paths1, storedCopy(e2).getDataPaths());

      Worksheet ws2 = open(e2);
      ((MirrorTableAssembly) ws2.getAssembly(MIRROR)).setAutoUpdate(false);
      save(ws2, e2);
      String[] copyPaths = storedCopy(e2).getDataPaths();
      assertFalse(Arrays.equals(paths1, copyPaths), "frozen copy should have its own files");

      resaveOwner(e1, "new", 80);
      XSwappableTable table = coldLoad(e2, null, copyPaths, paths1);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
   }

   // a frozen W2 stored before the fix names W1's files without the ownership flag, it gets its
   // own files on its first save
   @Test
   void legacyFrozenCopyGetsItsOwnFilesOnSave() throws Exception {
      AssetEntry e1 = entry("own_legacy_w1");
      AssetEntry e2 = entry("own_legacy_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      save(embed(e1, true), e2);

      storeFrozen(e2);
      assertArrayEquals(paths1, storedCopy(e2).getDataPaths());
      assertFalse(storedCopy(e2).ownsDataFiles());

      clearCaches(e2, paths1);
      Worksheet ws2 = open(e2);
      assertArrayEquals(paths1, outerCopy(ws2).getDataPaths(), "frozen copy made again on load");
      save(ws2, e2);
      String[] copyPaths = storedCopy(e2).getDataPaths();
      assertFalse(Arrays.equals(paths1, copyPaths), "frozen copy should have its own files");
      assertFilesExist(paths1, true, "W1's files deleted by saving W2");

      String[] paths2 = resaveOwner(e1, "new", 80);
      XSwappableTable table = coldLoad(e2, null, copyPaths, paths1);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));

      save(open(e2), e2);
      assertArrayEquals(copyPaths, storedCopy(e2).getDataPaths(), "frozen copy wrote files again");

      repository().removeSheet(e2, null, true);
      assertFilesExist(copyPaths, false, "frozen copy's files not deleted with W2");
      assertFilesExist(paths2, true, "W1's files deleted by removing W2");
   }

   // W1 moved after W2 was stored: the copies keep the names made from W1's old path
   @Test
   void frozenCopyOfMovedWorksheetGetsItsOwnFiles() throws Exception {
      AssetEntry e1 = entry("own_move_w1");
      AssetEntry e2 = entry("own_move_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      save(embed(e1, true), e2);
      storeFrozen(e2);

      repository().addFolder(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.FOLDER, "own_move_folder", null), null);
      AssetEntry moved = entry("own_move_folder/own_move_w1");
      repository().changeSheet(e1, moved, null, true);
      // as the rename transform rewrites the mirror's source in the stored W2
      IndexedStorage storage = repository().getStorage(e2);
      Worksheet stored = (Worksheet) storage.getXMLSerializable(e2.toIdentifier(), null);
      stored.renameOuterDependent(e1, moved);
      storage.putXMLSerializable(e2.toIdentifier(), stored);

      // as stored, opening it drops copies not named after the mirror's current worksheet
      // (removeOrphanedOuterAssemblies), the mirror still reads the copy it found before that
      clearCaches(e2, paths1);
      Worksheet ws2 = (Worksheet) storage.getXMLSerializable(e2.toIdentifier(), null);
      assertEquals(moved, ((MirrorTableAssembly) ws2.getAssembly(MIRROR)).getEntry());
      assertTrue(outerCopy(ws2).getName().startsWith(AssetUtil.createPrefix(e1)));
      save(ws2, e2);
      String[] copyPaths = storedCopy(e2).getDataPaths();
      assertFalse(Arrays.equals(paths1, copyPaths), "frozen copy should have its own files");

      resaveOwner(moved, "new", 80);
      assertFilesExist(paths1, false, "W1's replaced files not deleted");
      assertFilesExist(copyPaths, true, "frozen copy's files deleted by W1's save");
      clearCaches(e2, copyPaths, paths1);
      XSwappableTable table = storedCopy(e2).getTable();
      table.moreRows(XTable.EOT);
      assertEquals(51, table.getRowCount());
      assertEquals("old4", table.getObject(5, 1));
   }

   // the copies of W1 "a_b" start with the name prefix of W1 "a", an auto-updated mirror of "a"
   // must not make them count as updated
   @Test
   void frozenCopyIsNotTakenForCopyOfWorksheetWithShorterName() throws Exception {
      AssetEntry ea = entry("own_pre");
      AssetEntry eab = entry("own_pre_b");
      AssetEntry e2 = entry("own_pre_w2");
      String[] pathsA = saveOwner(ea, "a", 20).getDataPaths();
      String[] pathsAB = saveOwner(eab, "old", 50).getDataPaths();
      assertTrue(AssetUtil.createPrefix(eab).startsWith(AssetUtil.createPrefix(ea)));

      Worksheet ws2 = embed(ea, true);
      WSAssembly[] created = AssetUtil.copyOuterAssemblies(repository(), eab, null, ws2, null);
      MirrorTableAssembly mirror =
         new MirrorTableAssembly(ws2, MIRROR2, eab, true, created[created.length - 1]);
      mirror.setAutoUpdate(false);
      ws2.addAssembly(mirror);
      save(ws2, e2);

      SnapshotEmbeddedTableAssembly copyA = (SnapshotEmbeddedTableAssembly)
         ws2.getAssembly(AssetUtil.createPrefix(ea) + "0");
      SnapshotEmbeddedTableAssembly copyAB = (SnapshotEmbeddedTableAssembly)
         ws2.getAssembly(AssetUtil.createPrefix(eab) + "0");
      assertArrayEquals(pathsA, copyA.getDataPaths(), "auto-updated copy wrote its own files");
      String[] copyPaths = copyAB.getDataPaths();
      assertFalse(Arrays.equals(pathsAB, copyPaths), "frozen copy should have its own files");
      assertTrue(copyAB.ownsDataFiles());

      resaveOwner(eab, "new", 80);
      assertFilesExist(pathsAB, false, "W1's replaced files not deleted");
      assertFilesExist(copyPaths, true, "frozen copy's files deleted by W1's save");
   }

   // an auto-updated copy shows W1's new data after W1 is saved
   @Test
   void autoUpdatedCopyShowsOwnerDataAfterOwnerSave() throws Exception {
      AssetEntry e1 = entry("own_autonew_w1");
      AssetEntry e2 = entry("own_autonew_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      save(embed(e1, true), e2);

      String[] paths2 = resaveOwner(e1, "new", 80);
      XSwappableTable table = coldLoad(e2, null, paths1, paths2);
      assertEquals(81, table.getRowCount());
      assertEquals("new4", table.getObject(5, 1));
   }

   // a stored frozen copy whose shared files are already gone can't get its own files, the save
   // must still succeed
   @Test
   void frozenCopyOfDeletedFilesStillSaves() throws Exception {
      AssetEntry e1 = entry("own_broken_w1");
      AssetEntry e2 = entry("own_broken_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      save(embed(e1, true), e2);
      resaveOwner(e1, "new", 80);
      assertFilesExist(paths1, false, "W1's replaced files not deleted");

      clearCaches(e2, paths1);
      // as stored, without the copy being made again on load
      Worksheet ws2 = (Worksheet) repository().getStorage(e2)
         .getXMLSerializable(e2.toIdentifier(), null);
      MirrorTableAssembly mirror = (MirrorTableAssembly) ws2.getAssembly(MIRROR);
      mirror.setAutoUpdate(false);
      assertNotNull(mirror.getAssembly());
      assertArrayEquals(paths1, outerCopy(ws2).getDataPaths());

      save(ws2, e2);
      assertArrayEquals(paths1, storedCopy(e2).getDataPaths());
   }

   // an auto-updated copy keeps sharing W1's files and never writes its own
   @Test
   void autoUpdatedCopyNeverWritesFiles() throws Exception {
      AssetEntry e1 = entry("own_auto_w1");
      AssetEntry e2 = entry("own_auto_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      save(embed(e1, true), e2);

      for(int i = 0; i < 3; i++) {
         Worksheet ws2 = open(e2);
         save(ws2, e2);
         assertArrayEquals(paths1, outerCopy(ws2).getDataPaths());
         assertArrayEquals(paths1, storedCopy(e2).getDataPaths());
      }
   }

   // a save of W2 must not rewrite W1's files to clear their temp flag, they are W1's to keep
   @Test
   void savingEmbeddingWorksheetLeavesOwnerTempFilesAlone() throws Exception {
      AssetEntry e1 = entry("own_temp_w1");
      AssetEntry e2 = entry("own_temp_w2");
      String[] paths1 = saveOwner(e1, "old", 50).getDataPaths();
      Worksheet ws2 = embed(e1, true);
      save(ws2, e2);

      // W1 stored naming temp files, as a save after undo did before bug #78012
      EmbeddedTableStorage storage = EmbeddedTableStorage.getInstance();

      for(String path : paths1) {
         byte[] data;

         try(InputStream input = storage.readTable(path + "_s.tdat")) {
            data = input.readAllBytes();
         }

         storage.writeTable(path + "_s.tdat", new ByteArrayInputStream(data), true);
      }

      save(ws2, e2);

      for(String path : paths1) {
         assertTrue(storage.isTempTable(path + "_s.tdat"), "W1's file rewritten by W2: " + path);
      }

      assertArrayEquals(paths1, storedCopy(e2).getDataPaths());
   }

   // W1's own frozen copy of W0, copied into W2, still belongs to W1
   @Test
   void removingWorksheetKeepsFilesOfNestedCopy() throws Exception {
      AssetEntry e0 = entry("own_nest_zero");
      AssetEntry e1 = entry("own_nest_one");
      AssetEntry e2 = entry("own_nest_two");
      saveOwner(e0, "old", 50);
      save(embed(e0, false), e1);
      String[] nestedPaths = storedCopy(e1).getDataPaths();

      Worksheet ws2 = embed(e1, true);
      save(ws2, e2);
      assertArrayEquals(nestedPaths, outerCopy(ws2).getDataPaths());

      repository().removeSheet(e2, null, true);
      assertFilesExist(nestedPaths, true, "W1's files deleted by removing W2");
   }

   // an export writes the files of a frozen copy, and not those of a copy sharing W1's files
   @Test
   void exportWritesOnlyFilesOwnedByCopy() throws Exception {
      AssetEntry e1 = entry("own_export_w1");
      AssetEntry frozen = entry("own_export_frozen");
      AssetEntry auto = entry("own_export_auto");
      saveOwner(e1, "old", 50);
      save(embed(e1, false), frozen);
      save(embed(e1, true), auto);

      assertTrue(countExportedFiles(frozen) > 0, "frozen copy's files not exported");
      assertEquals(0, countExportedFiles(auto), "shared files exported with W2");
   }

   // after a failed save of W1, which wrote new files, a save of W2 must not delete W1's stored ones
   @Test
   void savingEmbeddingWorksheetAfterFailedOwnerSaveKeepsOwnerFiles() throws Exception {
      AssetEntry e1 = entry("own_failed_w1");
      AssetEntry e2 = entry("own_failed_w2");
      String[] paths1 = saveOwnerWithSecondTable(e1);
      Worksheet ws2 = embed(e1, true);
      save(ws2, e2);

      failOwnerSave(e1);
      assertFilesExist(paths1, true, "failed save deleted W1's files");

      save(ws2, e2);
      assertFilesExist(paths1, true, "W1's files deleted by saving W2");
      assertEquals("old4", coldLoad(e1, NAME, paths1).getObject(5, 1));
   }

   // the same, with W1 saved without changes in another session
   @Test
   void savingOwnerInOtherSessionAfterFailedSaveKeepsFiles() throws Exception {
      AssetEntry e1 = entry("own_session_w1");
      String[] paths1 = saveOwnerWithSecondTable(e1);
      Worksheet other = open(e1);

      failOwnerSave(e1);
      save(other, e1);

      assertFilesExist(paths1, true, "W1's stored files deleted by the other session's save");
      assertEquals("old4", coldLoad(e1, NAME, paths1).getObject(5, 1));
   }

   private SnapshotEmbeddedTableAssembly saveOwner(AssetEntry entry, String tag, int rows)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
      return table;
   }

   private String[] saveOwnerWithSecondTable(AssetEntry entry) throws Exception {
      SnapshotEmbeddedTableAssembly table = saveOwner(entry, "old", 50);
      Worksheet ws = table.getWorksheet();
      SnapshotEmbeddedTableAssembly table2 = new SnapshotEmbeddedTableAssembly(ws, NAME2);
      ws.addAssembly(table2);
      table2.setEmbeddedData(new XEmbeddedTable(createTable("two", 5)));
      save(ws, entry);
      return table.getDataPaths();
   }

   /**
    * Give both tables of W1 new data and save it, the write of the second table fails after the
    * first one wrote its new files.
    */
   private void failOwnerSave(AssetEntry entry) throws Exception {
      Worksheet ws = open(entry);
      ((SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME))
         .setEmbeddedData(new XEmbeddedTable(createTable("new", 80)));
      XSwappableTable table2 = createTable("two", 8);
      ((SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME2))
         .setEmbeddedData(new XEmbeddedTable(table2));
      table2.getTables()[0].testBeforeWrite = () -> {
         throw new UncheckedIOException(new IOException("simulated write failure"));
      };

      assertThrows(MessageException.class, () -> save(ws, entry));
   }

   private String[] resaveOwner(AssetEntry entry, String tag, int rows) throws Exception {
      Worksheet ws = open(entry);
      SnapshotEmbeddedTableAssembly table = (SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      save(ws, entry);
      return table.getDataPaths();
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

   /**
    * Store a worksheet with Auto Update off, as an older server did, without the composer save.
    */
   private static void storeFrozen(AssetEntry entry) throws Exception {
      IndexedStorage storage = repository().getStorage(entry);
      Worksheet stored = (Worksheet) storage.getXMLSerializable(entry.toIdentifier(), null);
      ((MirrorTableAssembly) stored.getAssembly(MIRROR)).setAutoUpdate(false);
      storage.putXMLSerializable(entry.toIdentifier(), stored);
   }

   private void save(Worksheet ws, AssetEntry entry) throws Exception {
      worksheetService.setWorksheet(ws, entry, null, true, false);
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

   /**
    * Get the outer snapshot table of a worksheet as stored, as {@code removeSheet} parses it.
    */
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

   private static int countExportedFiles(AssetEntry entry) throws Exception {
      Worksheet ws = open(entry);

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof SnapshotEmbeddedTableAssembly) {
            ((SnapshotEmbeddedTableAssembly) assembly).getTable();
         }
      }

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(JarOutputStream out = new JarOutputStream(bytes)) {
         ws.writeData(out);
      }

      int count = 0;

      try(ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
         for(ZipEntry zipEntry; (zipEntry = in.getNextEntry()) != null; ) {
            if(zipEntry.getName().startsWith("__WS_EMBEDDED_TABLE_")) {
               count++;
            }
         }
      }

      return count;
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
      // moving a sheet queues the rename transform of the sheets depending on it, the tests
      // rewrite the stored mirror themselves
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   private static final String NAME = "T";
   private static final String NAME2 = "T2";
   private static final String MIRROR = "M";
   private static final String MIRROR2 = "M2";
}
