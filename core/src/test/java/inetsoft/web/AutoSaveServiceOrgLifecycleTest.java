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
package inetsoft.web;

/*
 * Scenarios 6e/6g/6h: community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "3.4 Autosave 文件" / "Autosave — 管理/恢复层" -- see that doc for full rationale
 * (including the 6g correction: runInOrgScope() is NOT a no-op, contrary to the doc's original
 * wording). Keep this comment short; don't duplicate the doc's narrative here.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AutoSaveServiceOrgLifecycleTest {

   @Autowired
   private BlobStorageManager blobStorageManager;

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
   }

   // ── scenario 6e: restoreAutoSaveAssets() resolves the org from ThreadContext.getContextPrincipal(),
   //    not the `principal` parameter -- see matrix doc for the narrowed scope ──

   @Test
   @Disabled("6e: not yet run/confirmed -- see matrix doc section 3.4")
   void restore_targetOrgResolvedFromThreadContext_notFromMethodPrincipal() throws Exception {
      String sourceOrgId = "sixe_source_org";
      String threadOrgId = "sixe_thread_org";

      // the acting principal explicitly passed to the method -- represents "who is doing the
      // restore", scoped to the source org
      Principal methodPrincipal =
         new SRPrincipal(new IdentityID("sixe_user", sourceOrgId), new IdentityID[0],
                        new String[0], sourceOrgId, 1L);

      // a DIFFERENT ambient thread-context principal -- represents a divergent session context
      // (e.g. an admin operating outside their own org) that restoreAutoSaveAssets() ends up
      // consulting instead of `principal`
      ThreadContext.setContextPrincipal(
         new SRPrincipal(new IdentityID("sixe_admin", threadOrgId), new IdentityID[0],
                        new String[0], threadOrgId, 1L));
      OrganizationContextHolder.setCurrentOrgId(null);

      String id = "8^VIEWSHEET^" + new IdentityID("sixe_user", sourceOrgId).convertToKey() +
         "^Untitled-1^0_0_0_0_0_0_0_1~";

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AutoSaveService service = new AutoSaveService(viewsheetService);

      AssetRepository repository = mock(AssetRepository.class);
      AbstractSheet sheet = mock(AbstractSheet.class);
      when(repository.getSheet(any(), eq(methodPrincipal), eq(false), eq(AssetContent.ALL)))
         .thenReturn(sheet);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          // ActionRecord's constructor (unconditionally built by restoreAutoSaveAssets() for the
          // audit log) calls OrganizationManager.getCurrentOrgName(), which looks up a real
          // Organization from the security provider -- none is registered in this lightweight
          // fixture, so it NPEs unless stubbed; this is unrelated to the org-resolution behavior
          // under test, so only this one static method is overridden, everything else stays real
          MockedStatic<OrganizationManager> orgManager =
             mockStatic(OrganizationManager.class, CALLS_REAL_METHODS))
      {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);
         orgManager.when(OrganizationManager::getCurrentOrgName).thenReturn("dummy-org-name");

         // override=true skips the isDuplicatedEntry() check entirely
         service.restoreAutoSaveAssets(id, "Restored Report", true, methodPrincipal);
      }

      ArgumentCaptor<AssetEntry> entryCaptor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(repository).setSheet(entryCaptor.capture(), eq(sheet), eq(methodPrincipal), eq(false));

      // documents the CURRENT (suspected buggy) behavior: the restored entry's org tracks the
      // thread-context principal's org, not the source file's org or the explicit method
      // principal's org
      assertEquals(threadOrgId, entryCaptor.getValue().getOrgID(),
                  "restoreAutoSaveAssets() currently resolves the target org via "
                  + "ThreadContext.getContextPrincipal() (through AssetEntry's null-orgID default), "
                  + "not via the explicit `principal` parameter -- this assertion pins the current "
                  + "(suspected incorrect) behavior for confirmation, not the desired one");
   }

   // ── scenario 6g: migrateAutoSaveFiles() scopes strictly to the explicit storageOrgId bucket --
   //    it takes no Principal at all, so there's no ambient principal/org context to fall back to ──

   @Test
   void migrateAutoSaveFiles_explicitStorageOrgId_scopesToTargetBucket() throws Exception {
      String sourceOrgId = "sixg_source_org";
      String targetOrgId = "sixg_target_org";

      // simulates the state right after 6a/6b's bucket-level copyStorages("__autoSave", copy=true):
      // the raw file already lives in the TARGET org's bucket, still filename-tagged with the
      // SOURCE org's identity string
      String oldUserKey = new IdentityID("sixg_user", sourceOrgId).convertToKey();
      String rawFileName = "8^VIEWSHEET^" + oldUserKey + "^Untitled-1^0_0_0_0_0_0_0_1~";
      seedAutoSaveBlob(targetOrgId, rawFileName, "not-real-xml-content".getBytes());

      Organization oorg = new Organization(sourceOrgId);
      Organization norg = new Organization(targetOrgId);

      AutoSaveUtils.migrateAutoSaveFiles(oorg, norg, targetOrgId);

      String newUserKey = new IdentityID("sixg_user", targetOrgId).convertToKey();
      String expectedNewFileName = "8^VIEWSHEET^" + newUserKey + "^Untitled-1^0_0_0_0_0_0_0_1~";

      BlobStorage<AutoSaveUtils.Metadata> targetBucket =
         blobStorageManager.getStorage(targetOrgId.toLowerCase() + "__autoSave", false);

      assertFalse(targetBucket.exists(rawFileName),
                 "the old, source-org-tagged filename must be renamed away within the target bucket");
      assertTrue(targetBucket.exists(expectedNewFileName),
                "migrateAutoSaveFiles() must rename the file (within the target org's own bucket) "
                + "to carry the target org's identity string, using the explicit storageOrgId "
                + "argument");
   }

   // ── Issue #75827 (confirmed root cause of the reported "autosave file missing after org
   //    clone" bug): migrateAutoSaveFiles() strips the "recycle/" prefix via getName() to split
   //    out the name fields, then rebuilds the migrated filename from those fields WITHOUT
   //    re-applying the prefix -- so a recycled (discarded) draft silently turns into an ACTIVE
   //    autosave file after migration. addRecycleAutoSaved() only lists paths that still start
   //    with RECYCLE_PREFIX, so the migrated file drops off the EM "Auto Saved Files" tree ──

   @Test
   void migrateAutoSaveFiles_recycledDraft_keepsRecyclePrefixAfterMigration() throws Exception {
      String sourceOrgId = "recyclepfx_source_org";
      String targetOrgId = "recyclepfx_target_org";

      String oldUserKey = new IdentityID("recyclepfx_user", sourceOrgId).convertToKey();
      String recycledFileName =
         AutoSaveUtils.RECYCLE_PREFIX + "8^WORKSHEET^" + oldUserKey + "^Untitled-1^0_0_0_0_0_0_0_1~";
      seedAutoSaveBlob(targetOrgId, recycledFileName, "not-real-xml-content".getBytes());

      Organization oorg = new Organization(sourceOrgId);
      Organization norg = new Organization(targetOrgId);

      AutoSaveUtils.migrateAutoSaveFiles(oorg, norg, targetOrgId);

      String newUserKey = new IdentityID("recyclepfx_user", targetOrgId).convertToKey();
      String expectedNewFileName = AutoSaveUtils.RECYCLE_PREFIX +
         "8^WORKSHEET^" + newUserKey + "^Untitled-1^0_0_0_0_0_0_0_1~";

      BlobStorage<AutoSaveUtils.Metadata> targetBucket =
         blobStorageManager.getStorage(targetOrgId.toLowerCase() + "__autoSave", false);

      assertFalse(targetBucket.exists(recycledFileName),
                 "the old, source-org-tagged filename must be renamed away");
      assertTrue(targetBucket.exists(expectedNewFileName),
                "migrateAutoSaveFiles() must preserve the RECYCLE_PREFIX on the migrated filename "
                + "-- otherwise the recycled draft becomes an active autosave file and silently "
                + "drops off the EM \"Auto Saved Files\" recycle-bin tree (Issue #75827)");
   }

   // ── Bug #75887 (scenario 6h): removeExpiredAutoSaveFiles() only ever inspected the default
   //    org's bucket -- a non-default org's bucket was never even listed, regardless of age.
   //    Fixed by looping over SecurityProvider.getOrganizationIDs() and scoping each pass via
   //    OrganizationContextHolder, mirroring CleanupTableCacheTask's cross-org pattern ──

   @Test
   void removeExpiredAutoSaveFiles_expiredNonDefaultOrgFile_isDeleted() throws Exception {
      String nonDefaultOrgId = "sixh_non_default_org";
      String path = "8^VIEWSHEET^_NULL_^Untitled-1^0_0_0_0_0_0_0_1~";
      long eightDaysAgo = System.currentTimeMillis() - java.time.Duration.ofDays(8).toMillis();
      seedAutoSaveBlob(nonDefaultOrgId, path, "dummy".getBytes(), eightDaysAgo);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AutoSaveService service = new AutoSaveService(viewsheetService);

      // no ThreadContext principal set (mirrors the @Scheduled executor thread in production, where
      // ThreadContext.getContextPrincipal() is normally null)
      try(MockedStatic<SecurityEngine> securityEngine = mockOrganizationIDs(nonDefaultOrgId)) {
         service.removeExpiredAutoSaveFiles();
      }

      BlobStorage<AutoSaveUtils.Metadata> nonDefaultBucket =
         blobStorageManager.getStorage(nonDefaultOrgId.toLowerCase() + "__autoSave", false);

      assertEquals(0, nonDefaultBucket.paths().count(),
                  "removeExpiredAutoSaveFiles() must scan every organization's __autoSave bucket, "
                  + "not just the default org's, and delete entries older than 7 days from each");
   }

   @Test
   void removeExpiredAutoSaveFiles_freshNonDefaultOrgFile_survives() throws Exception {
      String nonDefaultOrgId = "sixh_non_default_org_fresh";
      String path = "8^VIEWSHEET^_NULL_^Untitled-1^0_0_0_0_0_0_0_1~";
      seedAutoSaveBlob(nonDefaultOrgId, path, "dummy".getBytes(), 0L);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AutoSaveService service = new AutoSaveService(viewsheetService);

      try(MockedStatic<SecurityEngine> securityEngine = mockOrganizationIDs(nonDefaultOrgId)) {
         service.removeExpiredAutoSaveFiles();
      }

      BlobStorage<AutoSaveUtils.Metadata> nonDefaultBucket =
         blobStorageManager.getStorage(nonDefaultOrgId.toLowerCase() + "__autoSave", false);

      assertEquals(1, nonDefaultBucket.paths().count(),
                  "a non-default org's auto save file that is not yet 7 days old must not be deleted");
   }

   /**
    * The test fixture's default SecurityProvider (VirtualAuthenticationProvider) only ever
    * reports the default org, so removeExpiredAutoSaveFiles()'s org loop needs
    * SecurityEngine.getSecurity().getSecurityProvider().getOrganizationIDs() stubbed to include
    * the non-default org under test -- scoped to a try-with-resources so it can't leak into
    * other tests sharing this class's cached Spring context.
    */
   private MockedStatic<SecurityEngine> mockOrganizationIDs(String... extraOrgIds) {
      MockedStatic<SecurityEngine> securityEngineMock = mockStatic(SecurityEngine.class);
      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      String[] orgIds = new String[extraOrgIds.length + 1];
      orgIds[0] = Organization.getDefaultOrganizationID();
      System.arraycopy(extraOrgIds, 0, orgIds, 1, extraOrgIds.length);
      when(provider.getOrganizationIDs()).thenReturn(orgIds);
      when(engine.getSecurityProvider()).thenReturn(provider);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(engine);
      return securityEngineMock;
   }

   // ── fixture helper ──

   private void seedAutoSaveBlob(String orgId, String path, byte[] content) throws Exception {
      seedAutoSaveBlob(orgId, path, content, 0L);
   }

   private void seedAutoSaveBlob(String orgId, String path, byte[] content, long lastModified)
      throws Exception
   {
      BlobStorage<AutoSaveUtils.Metadata> storage =
         blobStorageManager.getStorage(orgId.toLowerCase() + "__autoSave", false);

      try(BlobTransaction<AutoSaveUtils.Metadata> tx = storage.beginTransaction();
          OutputStream out = tx.newStream(path, new AutoSaveUtils.Metadata(), null, lastModified))
      {
         out.write(content);
         tx.commit();
      }
   }
}
