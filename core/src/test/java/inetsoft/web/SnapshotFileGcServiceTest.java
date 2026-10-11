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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

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
   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

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

   // refuter's recheck-1 addendum: a snapshot table present in a worksheet at the moment a user
   // enters the "join tables" editor, then removed from the top-level worksheet while that
   // join-edit clone is still open and uncommitted, is referenced only by
   // RuntimeWorksheetState.getJoinWS() -- a VCDIFF-encoded delta against the parent ws, never the
   // top-level ws itself. RuntimeSheetCache.getOpenWorksheetDataPaths() must recurse into it, not
   // just read the top-level ws, or this path would be invisible to the live-session signal and
   // only protected by the grace period plus the next full sweep, same as before this fix.
   @Test
   void joinEditCloneKeepsItsTableVisibleToTheLiveSessionScan() throws Exception {
      AssetEntry e = entry("gc_joinws_w1");
      String[] paths = saveOwner(e, "x", 10);

      String id = worksheetService.openWorksheet((AssetEntry) e.clone(), null);

      try {
         RuntimeWorksheet rws = worksheetService.getWorksheet(id, null);
         rws.cloneWS();
         assertTrue(rws.getWorksheet().removeAssembly(NAME),
                    "table missing from the top-level worksheet before the test scenario even starts");
         worksheetService.putRuntimeSheet(id, rws);

         WorksheetEngine engine = (WorksheetEngine) worksheetService;
         Set<String> openPaths = engine.getOpenWorksheetDataPaths();

         for(String path : paths) {
            assertTrue(openPaths.contains(path),
                       "join-edit clone's own data path invisible to the live-session scan: " + path);
         }
      }
      finally {
         worksheetService.closeWorksheet(id, null);
      }
   }

   // bug #78328 (A): a private worksheet whose owner the security provider does not list (security
   // off, SSO-only, ...) must still be seen by the full sweep, which used to enumerate USER_SCOPE
   // only for provider-listed users and so deleted the file the worksheet still names.
   @Test
   void unlistedOwnersPrivateWorksheetFileIsKeptByEveryCycle() throws Exception {
      AssetEntry e = userEntry("gc_user_w1");
      String[] paths = saveOwner(e, "u", 10);

      try {
         Instant now0 = Instant.now();

         for(Duration offset : new Duration[] { Duration.ZERO, Duration.ofHours(24),
                                                Duration.ofHours(49), Duration.ofDays(8),
                                                Duration.ofDays(11), Duration.ofDays(15) })
         {
            gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(offset));
            assertFilesExist(paths, true,
                             "private worksheet's file deleted at t0+" + offset.toHours() + "h");
         }
      }
      finally {
         cleanup(e, paths);
      }
   }

   // bug #78328 (A): protection by the incremental scan alone (a private worksheet edited after the
   // watermark) used to be lost again at the next weekly full sweep.
   @Test
   void editedPrivateWorksheetFileSurvivesTheNextFullSweep() throws Exception {
      AssetEntry e = userEntry("gc_user_w2");
      String[] paths = saveOwner(e, "u", 10);

      try {
         Instant now0 = Instant.now();
         gcService.removeOrphanedPermanentSnapshotFiles(now0);

         Worksheet ws = open(e);
         ws.setLastModified(System.currentTimeMillis());
         save(ws, e);

         for(Duration offset : new Duration[] { Duration.ofHours(24), Duration.ofHours(49),
                                                Duration.ofDays(8), Duration.ofDays(11) })
         {
            gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(offset));
            assertFilesExist(paths, true,
                             "edited private worksheet's file deleted at t0+" + offset.toHours() + "h");
         }
      }
      finally {
         cleanup(e, paths);
      }
   }

   // bug #78328 (A) control: enumerating from the storage keys must not over-reference -- a private
   // worksheet's file that the worksheet no longer names is still collected.
   @Test
   void privateWorksheetsOrphanedFileIsStillCleanedUp() throws Exception {
      AssetEntry e = userEntry("gc_user_w3");
      String[] paths = saveOwner(e, "u", 10);

      try {
         Instant now0 = Instant.now();
         gcService.removeOrphanedPermanentSnapshotFiles(now0);
         assertFilesExist(paths, true, "referenced file deleted by the seeding GC cycle");

         Worksheet ws = open(e);
         assertTrue(ws.removeAssembly(NAME));
         save(ws, e);

         gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(Duration.ofHours(1)));
         assertFilesExist(paths, true, "file deleted before the grace period elapsed");

         gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(Duration.ofDays(9)));
         assertFilesExist(paths, false, "private worksheet's orphaned file not cleaned up");
      }
      finally {
         cleanup(e, paths);
      }
   }

   // bug #78328 (B): a stored worksheet that no longer parses still names its file. Its raw text is
   // salvaged, so the file is kept from the very first full sweep on, the readable control is kept,
   // and an unrelated true orphan is still collected while the corrupt entry exists.
   @Test
   void unreadableWorksheetsFileIsKeptByRawSalvageWhileTrueOrphansAreStillCollected()
      throws Exception
   {
      AssetEntry broken = entry("gc_corrupt_w1");
      AssetEntry control = entry("gc_corrupt_control1");
      AssetEntry orphaned = entry("gc_corrupt_orphan1");
      String[] brokenPaths = saveOwner(broken, "b", 10);
      String[] controlPaths = saveOwner(control, "c", 10);
      String[] orphanPaths = saveOwner(orphaned, "o", 10);
      Worksheet ws = open(orphaned);
      assertTrue(ws.removeAssembly(NAME));
      save(ws, orphaned);
      corrupt(broken);

      try {
         assertThrows(Exception.class, () -> repository().getStorage(broken)
            .getXMLSerializable(broken.toIdentifier(), null, orgId()));

         Instant now0 = Instant.now();

         for(Duration offset : new Duration[] { Duration.ZERO, Duration.ofHours(49),
                                                Duration.ofDays(8), Duration.ofDays(11) })
         {
            gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(offset));
            assertFilesExist(brokenPaths, true,
                             "unreadable worksheet's file deleted at t0+" + offset.toHours() + "h");
            assertFilesExist(controlPaths, true,
                             "control's file deleted at t0+" + offset.toHours() + "h");
         }

         assertFilesExist(orphanPaths, false, "true orphan not collected next to a corrupt entry");
      }
      finally {
         cleanup(broken, brokenPaths);
         cleanup(control, controlPaths);
         cleanup(orphaned, orphanPaths);
      }
   }

   // bug #78328 (B), incremental arm: an entry first seen by an incremental scan (it was saved
   // after the seeding full sweep) that no longer parses must not lose its file to the next full
   // sweep.
   @Test
   void worksheetCorruptedAfterTheSeedingSweepKeepsItsFile() throws Exception {
      AssetEntry broken = entry("gc_corrupt_w2");
      Instant now0 = Instant.now();
      gcService.removeOrphanedPermanentSnapshotFiles(now0);

      String[] brokenPaths = saveOwner(broken, "b", 10);
      corrupt(broken);

      try {
         gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(Duration.ofHours(1)));
         gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(Duration.ofHours(50)));
         assertFilesExist(brokenPaths, true, "file deleted by an incremental scan");

         gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(Duration.ofDays(8)));
         assertFilesExist(brokenPaths, true, "file deleted by the full sweep");

         gcService.removeOrphanedPermanentSnapshotFiles(now0.plus(Duration.ofDays(11)));
         assertFilesExist(brokenPaths, true, "file deleted by the following cycle");
      }
      finally {
         cleanup(broken, brokenPaths);
      }
   }

   // bug #78328 (B) fallback: when a corrupt entry's raw text cannot be obtained either, nothing in
   // the organization is confirmed or deleted while it exists; once it is gone the next full sweep
   // collects the orphan again.
   @Test
   void unsalvageableWorksheetSuspendsDeletionUntilItIsRemoved() throws Exception {
      SnapshotFileGcService noRaw = new SnapshotFileGcService(
         (ViewsheetService) worksheetService, EmbeddedTableStorage.getInstance(),
         keyValueStorageManager)
      {
         @Override
         byte[] readRawWorksheet(IndexedStorage storage, String key, String orgId) {
            return null;
         }
      };

      AssetEntry broken = entry("gc_corrupt_w3");
      AssetEntry orphaned = entry("gc_corrupt_orphan3");
      String[] brokenPaths = saveOwner(broken, "b", 10);
      String[] orphanPaths = saveOwner(orphaned, "o", 10);
      Worksheet ws = open(orphaned);
      assertTrue(ws.removeAssembly(NAME));
      save(ws, orphaned);
      corrupt(broken);

      try {
         Instant now0 = Instant.now();

         for(Duration offset : new Duration[] { Duration.ZERO, Duration.ofHours(49),
                                                Duration.ofDays(8), Duration.ofDays(11) })
         {
            noRaw.removeOrphanedPermanentSnapshotFiles(now0.plus(offset));
            assertFilesExist(brokenPaths, true, "unreadable worksheet's file deleted");
            assertFilesExist(orphanPaths, true,
                             "deletion not suspended at t0+" + offset.toHours() + "h");
         }

         repository().removeSheet(broken, null, true);
         noRaw.removeOrphanedPermanentSnapshotFiles(now0.plus(Duration.ofDays(20)));
         assertFilesExist(orphanPaths, false, "orphan not collected once the corrupt entry is gone");
      }
      finally {
         cleanup(broken, brokenPaths);
         cleanup(orphaned, orphanPaths);
      }
   }

   private void cleanup(AssetEntry entry, String[] paths) {
      try {
         repository().removeSheet(entry, null, true);
      }
      catch(Exception ignore) {
         // already removed
      }

      for(String path : paths) {
         try {
            EmbeddedTableStorage.getInstance().removeTable(path + "_s.tdat", orgId());
         }
         catch(Exception ignore) {
            // already removed
         }
      }
   }

   /**
    * Rewrites the stored blob with a raw U+001F after the worksheet start tag, keeping its
    * metadata, as a save before bugs #78128/#78110 could leave it.
    */
   @SuppressWarnings({ "rawtypes", "unchecked" })
   private static void corrupt(AssetEntry entry) throws Exception {
      IndexedStorage storage = repository().getStorage(entry);
      Method m = null;

      for(Class<?> c = storage.getClass(); c != null && m == null; c = c.getSuperclass()) {
         try {
            m = c.getDeclaredMethod("getMetadataStorage", String.class);
         }
         catch(NoSuchMethodException ignore) {
            // look in the superclass
         }
      }

      assertNotNull(m);
      m.setAccessible(true);
      BlobStorage blobs = (BlobStorage) m.invoke(storage, orgId());
      String key = entry.toIdentifier();
      Serializable metadata = (Serializable) blobs.getMetadata(key);
      String xml;

      try(InputStream in = blobs.getInputStream(key)) {
         xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      int start = xml.indexOf("<worksheet");
      int end = xml.indexOf('>', start);
      assertTrue(start >= 0 && end > 0);
      String bad = xml.substring(0, end + 1) + "\u001F" + xml.substring(end + 1);

      try(BlobTransaction tx = blobs.beginTransaction(); OutputStream out = tx.newStream(key, metadata)) {
         out.write(bad.getBytes(StandardCharsets.UTF_8));
         out.flush();
         tx.commit();
      }
   }

   private static AssetEntry userEntry(String name) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET, name,
                            new IdentityID("anonymous", orgId()), orgId());
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
