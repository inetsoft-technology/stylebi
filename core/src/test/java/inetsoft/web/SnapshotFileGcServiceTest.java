/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78035: a permanent (non-temp) snapshot {@code _s.tdat} file that nothing stored, no
 * auto-save draft, and no open runtime session still references is never deleted by anything
 * else. {@link SnapshotFileGcService} deletes such files once they have been continuously
 * unreferenced for a grace period and a weekly full reconciliation sweep has confirmed them
 * unreferenced, subsuming leak scenario 1a (a table removed from a worksheet, then saved),
 * category 4 (an owner file kept forever by bug #78032's {@code isNamedByFrozenCopy} once its
 * keeping frozen copy is gone), and the backdated-import edge case (an incremental scan's
 * {@code lastModified} watermark can permanently miss an imported asset's reference, so deletion
 * is never authorized on incremental evidence alone).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SnapshotFileGcServiceTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class SnapshotFileGcServiceTest {
   @Autowired
   private WorksheetService worksheetService;
   @Autowired
   private SnapshotFileGcService gcService;

   @BeforeEach
   void resetGcState() throws Exception {
      gcService.resetStateForTesting(orgId());
   }

   // leak scenario 1a: a table removed from a worksheet, then saved, leaves its files unreferenced
   // by anything. They are kept past the grace period alone (an incremental scan's evidence is
   // never sufficient by itself), and only cleaned up once a full sweep also confirms them
   // unreferenced.
   @Test
   void removedTableFilesAreCleanedUpAfterGracePeriodAndFullSweep() throws Exception {
      AssetEntry e = entry("gc_removed_w1");
      String[] paths = saveOwner(e, "x", 10);

      Instant now0 = Instant.now();
      // first-ever run for this org: always a full sweep, seeds the state and sees the table
      // still referenced
      gcService.removeOrphanedPermanentSnapshotFiles(now0);
      assertFilesExist(paths, true, "referenced file deleted by the seeding GC cycle");

      Worksheet ws = open(e);
      assertTrue(ws.removeAssembly(NAME));
      save(ws, e);

      Instant now1 = now0.plus(Duration.ofHours(1));
      // incremental: well within 7 days of the seeding full sweep
      gcService.removeOrphanedPermanentSnapshotFiles(now1);
      assertFilesExist(paths, true, "file deleted before the grace period elapsed");

      Instant now2 = now0.plus(Duration.ofDays(9));
      // past both the 48h grace period and the 7-day full-sweep interval
      gcService.removeOrphanedPermanentSnapshotFiles(now2);
      assertFilesExist(paths, false, "orphaned file not cleaned up");
   }

   // category 4: a file kept forever by bug #78032's isNamedByFrozenCopy mechanism becomes an
   // ordinary orphan, and is cleaned up, once the frozen copy worksheet that justified keeping it
   // is deleted.
   @Test
   void frozenCopyKeptFileIsCleanedUpOnceTheFrozenCopyIsDeleted() throws Exception {
      AssetEntry e1 = entry("gc_frozen_w1");
      AssetEntry e2 = entry("gc_frozen_w2");
      String[] paths1 = saveOwner(e1, "old", 20);
      storeLegacyFrozen(e1, e2, paths1);

      // W1's replaced files are kept (not deleted) because the legacy frozen copy e2 still names
      // them -- confirmed by bug #78032's own test; re-confirmed here as the starting condition
      String[] paths2 = resaveOwner(e1, "new", 30);
      assertFilesExist(paths1, true, "files of the frozen copy deleted by W1's save");

      Instant now0 = Instant.now();
      // full sweep sees paths1 still named by e2's stored content, paths2 named by e1
      gcService.removeOrphanedPermanentSnapshotFiles(now0);
      assertFilesExist(paths1, true, "frozen-copy-kept file deleted while its copy still exists");
      assertFilesExist(paths2, true, "owner's current file deleted while still referenced");

      // the justification for keeping paths1 is gone once e2 itself is deleted
      repository().removeSheet(e2, null, true);

      Instant now1 = now0.plus(Duration.ofHours(1));
      gcService.removeOrphanedPermanentSnapshotFiles(now1);
      assertFilesExist(paths1, true, "file deleted before the grace period elapsed");

      Instant now2 = now0.plus(Duration.ofDays(9));
      gcService.removeOrphanedPermanentSnapshotFiles(now2);
      assertFilesExist(paths1, false, "file no longer named by anything was not cleaned up");
      assertFilesExist(paths2, true, "owner's current, still-referenced file was wrongly deleted");
   }

   // an imported worksheet's reference must not be lost: its stored lastModified is backdated to
   // its original authoring time, so an incremental scan's lastModified watermark can permanently
   // miss that it references a path. The path must survive past the grace period on incremental
   // evidence alone, and only a weekly full sweep -- which has no lastModified filter to evade --
   // may ever clear it from the candidate set.
   @Test
   void importedWorksheetReferenceIsNotLostToTheIncrementalWatermark() throws Exception {
      AssetEntry e = entry("gc_import_w1");

      Instant now0 = Instant.now();
      // seed the state with a full sweep before the "import" -- the backdated lastModified only
      // matters to an incremental scan's watermark filter, which this establishes
      gcService.removeOrphanedPermanentSnapshotFiles(now0);

      String[] paths = saveImportedOwner(e, "imported", 15, now0.minus(Duration.ofDays(60)));

      Instant now1 = now0.plus(Duration.ofHours(1));
      // incremental: the imported entry's stored lastModified predates the watermark (now0), so
      // this scan cannot see that it references anything
      gcService.removeOrphanedPermanentSnapshotFiles(now1);

      Instant now2 = now1.plus(Duration.ofHours(49));
      // still incremental (well within 7 days of now0's full sweep) and past the 48h grace period
      // -- but a full sweep has never confirmed it unreferenced, so it must still survive
      gcService.removeOrphanedPermanentSnapshotFiles(now2);
      assertFilesExist(paths, true,
                       "imported worksheet's file deleted on incremental evidence alone");

      Instant now3 = now0.plus(Duration.ofDays(8));
      // a full sweep: no lastModified filter, so it reads the imported entry's content regardless
      // and finds the reference the incremental scans above could not
      gcService.removeOrphanedPermanentSnapshotFiles(now3);
      assertFilesExist(paths, true,
                       "imported worksheet's referenced file was deleted after the full sweep");
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
    * Save a worksheet the way an import does: the worksheet's own {@code lastModified}/
    * {@code lastModifiedBy} are set before the save (as a deserialized import package would carry
    * them), and {@link DeployManagerService#IS_IMPORTING} is set around the save so the common
    * sheet-save path preserves that original timestamp into the stored blob's metadata instead of
    * stamping the moment of the save (see {@code AbstractAssetEngine.java} around lines
    * 2825-2859, and {@code BlobIndexedStorage.getLastModified(String, XMLSerializable)}).
    */
   private String[] saveImportedOwner(AssetEntry entry, String tag, int rows,
                                      Instant originalModified) throws Exception
   {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(NAME);
      table.setEmbeddedData(new XEmbeddedTable(createTable(tag, rows)));
      ws.setLastModified(originalModified.toEpochMilli());
      ws.setLastModifiedBy("importeduser");

      DeployManagerService.IS_IMPORTING.set(true);

      try {
         save(ws, entry);
      }
      finally {
         DeployManagerService.IS_IMPORTING.remove();
      }

      // confirm the simulated import actually backdated the stored metadata -- otherwise this
      // test would not be exercising the edge case it claims to
      IndexedStorage storage = repository().getStorage(entry);
      long storedModified = storage.lastModified(entry.toIdentifier());
      assertTrue(storedModified <= originalModified.toEpochMilli() + 1000,
                "stored lastModified was not backdated by the simulated import: " + storedModified);

      return table.getDataPaths();
   }

   /**
    * Store W2 embedding W1 with Auto Update off, as a server before bug #78023 was fixed did: the
    * copy names W1's files and has no ownership flag.
    */
   private void storeLegacyFrozen(AssetEntry e1, AssetEntry e2, String[] paths1) throws Exception {
      save(embed(e1, true), e2);
      IndexedStorage storage = repository().getStorage(e2);
      Worksheet stored = (Worksheet) storage.getXMLSerializable(e2.toIdentifier(), null);
      ((MirrorTableAssembly) stored.getAssembly(MIRROR)).setAutoUpdate(false);
      storage.putXMLSerializable(e2.toIdentifier(), stored);
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

   private static String orgId() {
      return OrganizationManager.getInstance().getCurrentOrgID();
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

      @Bean
      public SnapshotFileGcService snapshotFileGcService(
         ViewsheetService viewsheetService, EmbeddedTableStorage embeddedTableStorage,
         KeyValueStorageManager keyValueStorageManager)
      {
         return new SnapshotFileGcService(viewsheetService, embeddedTableStorage,
                                          keyValueStorageManager);
      }
   }

   private static final String NAME = "T";
   private static final String MIRROR = "M";
}
