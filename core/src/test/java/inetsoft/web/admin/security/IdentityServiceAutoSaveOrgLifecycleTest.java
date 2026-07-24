/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.security;

/*
 * Scenarios 6a/6b/6d/7a (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "3.4 Autosave 文件 / Task Save 文件".
 *
 * 6a/6b cover IdentityService.copyStorages() -> updateBlobStorageName("__autoSave", ..., copy=true)
 * (:1299-1322): both the copy (replace=false) and rename (replace=true) callers of copyStorages()
 * route through the exact same private method with the SAME hardcoded `copy=true` literal
 * (IdentityService.java:1145) -- the `rename` boolean passed into copyStorages() is used for a few
 * other steps (indexedStorage.copyStorageData(), mvManager.migrateStorageData()) but is NEVER
 * forwarded into any of the four updateBlobStorageName() calls (__mvws/__pdata/__autoSave/__mvBlock).
 * In other words: copyStorages() by itself NEVER deletes the source org's autosave bucket, no matter
 * which caller shape invokes it. The actual source-side cleanup for a rename happens later, as a
 * separate step: AbstractEditableAuthenticationProvider.copyOrganizationInternal() only calls
 * identityService.removeStorages(fromOrgId) inside its `if(replace)` cleanup block (:291-297), which
 * is what scenario 6d below (IdentityService.removeStorages() -> removeBlobStorage("__autoSave",...))
 * actually exercises. 6b is written to confirm this directly rather than assume it from reading the
 * source: it drives copyStorages(..., true) alone (no removeStorages() call) and asserts the source
 * blob survives.
 *
 * 7a covers IdentityService.updateTaskSaveFiles() -> externalStorageService.renameFolder() (:2764-2778).
 * This method is called from exactly one place: AbstractEditableAuthenticationProvider
 * .copyOrganizationInternal()'s `if(replace)` branch (:154) -- the copy (replace=false) branch has no
 * equivalent call anywhere in that method, confirmed by reading copyOrganizationInternal() in full.
 * This test only exercises updateTaskSaveFiles() itself in isolation (the org-equality guard and the
 * renameFolder() delegation); the copy-branch asymmetry is a code-reading fact recorded in the matrix
 * doc, not separately re-verified through the full copyOrganization() entry point here.
 */

import inetsoft.mv.MVManager;
import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.Organization;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import inetsoft.storage.ExternalStorageService;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.util.IndexedStorage;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.RecycleBin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceAutoSaveOrgLifecycleTest {

   @Autowired
   private BlobStorageManager blobStorageManager;

   private IdentityService service;
   private ExternalStorageService externalStorageService;

   @BeforeEach
   void setUp() {
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

      // dependencies actually touched by copyStorages()/removeStorages()/updateTaskSaveFiles();
      // every call these methods make on them is a void no-op (or, for LibManagerProvider, is only
      // dereferenced for .close()), so a bare mock is enough -- only blobStorageManager and
      // externalStorageService need to be observable/real for the assertions below.
      ReflectionTestUtils.setField(service, "blobStorageManager", blobStorageManager);
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "dependencyStorageService", mock(DependencyStorageService.class));
      ReflectionTestUtils.setField(service, "recycleBin", mock(RecycleBin.class));
      ReflectionTestUtils.setField(service, "indexedStorage", mock(IndexedStorage.class));
      ReflectionTestUtils.setField(service, "mvManager", mock(MVManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));

      LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      when(libManagerProvider.getManager(anyString())).thenReturn(mock(LibManager.class));
      ReflectionTestUtils.setField(service, "libManagerProvider", libManagerProvider);

      externalStorageService = mock(ExternalStorageService.class);
      ReflectionTestUtils.setField(service, "externalStorageService", externalStorageService);

      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
   }

   // ── scenario 6a: copy (replace=false) streams the __autoSave bucket to the new org and leaves
   //    the source bucket untouched ──

   @Test
   void copy_copyStorages_autoSave_copiesBlobAndLeavesSourceUntouched() throws Exception {
      String fromOrgId = "autosave_copy_from";
      String toOrgId = "autosave_copy_to";

      seedAutoSaveBlob(fromOrgId, "file1.vs", "hello".getBytes());

      service.copyStorages(new Organization(fromOrgId), new Organization(toOrgId), false);

      assertArrayEquals("hello".getBytes(), readAutoSaveBlob(toOrgId, "file1.vs"),
                        "copy must produce the same blob content under the target org's bucket");
      assertArrayEquals("hello".getBytes(), readAutoSaveBlob(fromOrgId, "file1.vs"),
                        "copy (replace=false) must never remove the source org's blob");
   }

   // ── scenario 6b: rename (replace=true) drives the SAME updateBlobStorageName(..., copy=true)
   //    call -- copyStorages() alone does not delete the source bucket even when its own `rename`
   //    flag is true; source cleanup is a separate step (removeStorages(), see 6d) invoked later by
   //    the caller, not by copyStorages() itself ──

   @Test
   void rename_copyStorages_autoSave_sourceSurvivesUntilSeparateRemoveStoragesCall() throws Exception {
      String fromOrgId = "autosave_rename_from";
      String toOrgId = "autosave_rename_to";

      seedAutoSaveBlob(fromOrgId, "file2.vs", "world".getBytes());

      service.copyStorages(new Organization(fromOrgId), new Organization(toOrgId), true);

      assertArrayEquals("world".getBytes(), readAutoSaveBlob(toOrgId, "file2.vs"),
                        "rename must also produce the blob under the target org's bucket");
      assertArrayEquals("world".getBytes(), readAutoSaveBlob(fromOrgId, "file2.vs"),
                        "confirmed: copyStorages()'s updateBlobStorageName(\"__autoSave\", ..., true) "
                        + "call hardcodes copy=true regardless of the rename argument -- the source "
                        + "blob is only removed later by a separate removeStorages() call made by the "
                        + "caller (AbstractEditableAuthenticationProvider's replace=true cleanup "
                        + "block), not by copyStorages() itself");
   }

   // ── scenario 6d: delete via removeStorages() removes the whole __autoSave bucket for the org ──

   @Test
   void delete_removeStorages_autoSave_wholeBucketGone() throws Exception {
      String orgId = "autosave_delete_org";

      seedAutoSaveBlob(orgId, "file3.vs", "bye".getBytes());
      assertNotNull(readAutoSaveBlob(orgId, "file3.vs"), "precondition: blob must exist before removal");

      service.removeStorages(orgId);

      BlobStorage<AutoSaveUtils.Metadata> fresh =
         blobStorageManager.getStorage(orgId.toLowerCase() + "__autoSave", false);
      assertEquals(0, fresh.paths().count(),
                  "removeStorages() must delete the whole __autoSave bucket -- no residual blob");
   }

   // ── scenario 7a: updateTaskSaveFiles() delegates to externalStorageService.renameFolder(), only
   //    when the two org ids actually differ ──

   @Test
   void updateTaskSaveFiles_orgsDiffer_renamesExternalStorageFolder() throws Exception {
      Organization from = new Organization("tasksave_from");
      Organization to = new Organization("tasksave_to");

      service.updateTaskSaveFiles(from, to);

      verify(externalStorageService).renameFolder("tasksave_from", "tasksave_to");
   }

   @Test
   void updateTaskSaveFiles_sameOrgId_noOp() {
      Organization same1 = new Organization("tasksave_same");
      Organization same2 = new Organization("tasksave_same");

      service.updateTaskSaveFiles(same1, same2);

      verifyNoInteractions(externalStorageService);
   }

   // ── fixture helpers ──

   private void seedAutoSaveBlob(String orgId, String path, byte[] content) throws Exception {
      BlobStorage<AutoSaveUtils.Metadata> storage =
         blobStorageManager.getStorage(orgId.toLowerCase() + "__autoSave", false);

      try(BlobTransaction<AutoSaveUtils.Metadata> tx = storage.beginTransaction();
          OutputStream out = tx.newStream(path, new AutoSaveUtils.Metadata()))
      {
         out.write(content);
         tx.commit();
      }
   }

   private byte[] readAutoSaveBlob(String orgId, String path) throws Exception {
      BlobStorage<AutoSaveUtils.Metadata> storage =
         blobStorageManager.getStorage(orgId.toLowerCase() + "__autoSave", false);

      if(!storage.exists(path)) {
         return null;
      }

      try(InputStream in = storage.getInputStream(path)) {
         return in.readAllBytes();
      }
   }
}
