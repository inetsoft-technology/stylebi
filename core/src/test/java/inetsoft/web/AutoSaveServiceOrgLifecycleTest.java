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
 * Scenarios 6e/6g/6h (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "3.4 Autosave 文件" / "Autosave — 管理/恢复层".
 *
 * IMPORTANT CORRECTION (2026-07-27, discovered while writing this file): the matrix doc's original
 * 6g wording ("runInOrgScope() is likely a no-op because OrganizationManager.getCurrentOrgID(Principal)
 * checks xPrincipal.getCurrentOrgId() before OrganizationContextHolder") is WRONG. Reading
 * XPrincipal.getCurrentOrgId() itself (XPrincipal.java:628-634) shows it checks
 * OrganizationContextHolder FIRST, before its own instance properties:
 *
 *    public String getCurrentOrgId() {
 *       String currentOrgId = OrganizationContextHolder.getCurrentOrgId();
 *       currentOrgId = currentOrgId == null ? getProperty("curr_org_id") : currentOrgId;
 *       currentOrgId = currentOrgId == null ? getOrgId() : currentOrgId;
 *       return currentOrgId;
 *    }
 *
 * OrganizationContextHolder is a plain ThreadLocal (OrganizationContextHolder.java:45), so within the
 * same thread runInOrgScope()'s write IS visible to xPrincipal.getCurrentOrgId(), regardless of which
 * XPrincipal instance is asked. Digging further: migrateAutoSaveFiles() doesn't do cross-bucket
 * copying at all -- it operates entirely within whatever org getStorage(principal) resolves to. That
 * only makes sense because the __autoSave bucket is already physically copied bucket-to-bucket
 * *before* this runs (see scenarios 6a/6b: IdentityService.copyStorages() -> updateBlobStorageName
 * ("__autoSave", ..., copy=true)), so by the time migrateAutoSaveFiles(oorg, norg, principal) runs
 * wrapped in runInOrgScope(newOrgID, ...), norg's bucket already contains raw copies of every file
 * (still filename-tagged with the OLD org's identity string) and this method's only job is to rename
 * those filenames (and rewrite embedded XML content) in place, within norg's bucket. runInOrgScope
 * (newOrgID, ...) is therefore correct BY DESIGN, not a bug -- 6g is rewritten below as a passing
 * (non-@Disabled) confirmation test, not a reproduction of a suspected defect.
 *
 * 6e remains a real, but much narrower, concern than originally worded: AutoSaveService
 * .restoreAutoSaveAssets() resolves the restored asset's org via the 4-arg AssetEntry constructor
 * (AssetEntry.java:573-575) -> 5-arg constructor's null-orgID fallback (:583-585) ->
 * OrganizationManager.getInstance().getCurrentOrgID() (no-arg overload, OrganizationManager.java:64-68)
 * -> ThreadContext.getContextPrincipal() -- NOT the `principal` method parameter that
 * restoreAutoSaveAssets(id, assetName, override, principal) already has in scope. Reading the
 * generated AutoSaveServiceProxy (target/generated-sources/.../AutoSaveServiceProxy.java) and
 * ServiceProxyContext confirms local (same-node) calls never touch ThreadContext at all, so in the
 * ordinary single-node call shape `principal` and ThreadContext.getContextPrincipal() are normally the
 * SAME identity (both come from the same authenticated request) -- no divergence. The gap only shows
 * up in narrower shapes: an admin restoring "as"/"for" a different identity than their own ambient
 * session context, or a cluster-proxy dispatch where the proxy's captured contextPrincipal (captured
 * at construction time on the *calling* node, ServiceProxyContext.java:58-59) diverges from the
 * explicit `principal` argument. The test below reproduces that narrower divergence directly (by
 * setting a different ThreadContext principal than the method's own `principal` parameter) rather than
 * claiming every call is affected. Not yet run/confirmed -- left @Disabled pending verification.
 *
 * 6h (AutoSaveService.removeExpiredAutoSaveFiles(), :45-51) is a real, simpler gap: it calls
 * AutoSaveUtils.getStorage(null) once, which (BlobStorage has no public API to backdate an entry's
 * last-modified timestamp) means this test can only verify *which* org bucket the scheduled job
 * touches, not the full "and then deletes it because it's 7 days old" behavior end-to-end -- it
 * asserts the routing/scope gap (only the default org's bucket is ever inspected; a non-default org's
 * bucket is never even listed), which is the actual reported defect, not the age-based deletion logic
 * itself (that part of the method is not in question). Not yet run/confirmed -- left @Disabled.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SRPrincipal;
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

   // ── scenario 6e: restoreAutoSaveAssets() resolves the restored asset's org from
   //    ThreadContext.getContextPrincipal(), not from the `principal` method parameter --
   //    reproduced here as the narrow "acting principal diverges from ambient thread context" shape ──

   @Test
   @Disabled("6e: not yet run/confirmed -- see class-level comment for the narrowed scope")
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

   // ── scenario 6g: migrateAutoSaveFiles(), wrapped in OrganizationManager.runInOrgScope(newOrgID,
   //    ...) exactly as AbstractEditableAuthenticationProvider's copy branch does it, correctly
   //    operates on the NEW org's bucket even when the acting principal's own org differs from both
   //    the source and target orgs -- CONFIRMED CORRECT, not a bug (see class-level comment) ──

   @Test
   void migrateAutoSaveFiles_runInOrgScope_correctlyScopesToNewOrgBucket() throws Exception {
      String sourceOrgId = "sixg_source_org";
      String targetOrgId = "sixg_target_org";
      String actorOrgId = "sixg_unrelated_actor_org";

      // acting principal's OWN org is unrelated to both source/target -- proves resolution comes
      // from OrganizationContextHolder (set by runInOrgScope), not from the principal's own identity
      Principal principal = new SRPrincipal(new IdentityID("sixg_actor", actorOrgId),
                                            new IdentityID[0], new String[0], actorOrgId, 1L);

      // simulates the state right after 6a/6b's bucket-level copyStorages("__autoSave", copy=true):
      // the raw file already lives in the TARGET org's bucket, still filename-tagged with the
      // SOURCE org's identity string
      String oldUserKey = new IdentityID("sixg_user", sourceOrgId).convertToKey();
      String rawFileName = "8^VIEWSHEET^" + oldUserKey + "^Untitled-1^0_0_0_0_0_0_0_1~";
      seedAutoSaveBlob(targetOrgId, rawFileName, "not-real-xml-content".getBytes());

      Organization oorg = new Organization(sourceOrgId);
      Organization norg = new Organization(targetOrgId);

      OrganizationManager.runInOrgScope(targetOrgId, () -> {
         AutoSaveUtils.migrateAutoSaveFiles(oorg, norg, principal);
         return null;
      });

      String newUserKey = new IdentityID("sixg_user", targetOrgId).convertToKey();
      String expectedNewFileName = "8^VIEWSHEET^" + newUserKey + "^Untitled-1^0_0_0_0_0_0_0_1~";

      BlobStorage<AutoSaveUtils.Metadata> targetBucket =
         blobStorageManager.getStorage(targetOrgId.toLowerCase() + "__autoSave", false);

      assertFalse(targetBucket.exists(rawFileName),
                 "the old, source-org-tagged filename must be renamed away within the target bucket");
      assertTrue(targetBucket.exists(expectedNewFileName),
                "migrateAutoSaveFiles() must rename the file (within the target org's own bucket) "
                + "to carry the target org's identity string, proving runInOrgScope(newOrgID, ...) "
                + "correctly scoped getStorage(principal) to the target org despite the acting "
                + "principal's own org being unrelated to source/target");
   }

   // ── scenario 6h: removeExpiredAutoSaveFiles() only ever inspects the default org's __autoSave
   //    bucket (AutoSaveUtils.getStorage(null) -> ThreadContext.getContextPrincipal() is null on the
   //    scheduler thread -> Organization.getDefaultOrganizationID()) -- a non-default org's bucket is
   //    never even listed, regardless of what it contains ──

   @Test
   @Disabled("6h: not yet run/confirmed; also only verifies bucket routing/scope, not the 7-day-old "
      + "deletion criteria itself -- BlobStorage exposes no public API to backdate a blob's "
      + "last-modified timestamp, so an end-to-end \"and it actually gets deleted\" assertion isn't "
      + "practical here")
   void removeExpiredAutoSaveFiles_onlyScansDefaultOrgBucket_nonDefaultOrgNeverListed() throws Exception {
      String nonDefaultOrgId = "sixh_non_default_org";
      seedAutoSaveBlob(nonDefaultOrgId, "8^VIEWSHEET^_NULL_^Untitled-1^0_0_0_0_0_0_0_1~",
                       "dummy".getBytes());

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AutoSaveService service = new AutoSaveService(viewsheetService);

      // no ThreadContext principal set (mirrors the @Scheduled executor thread in production, where
      // ThreadContext.getContextPrincipal() is normally null)
      service.removeExpiredAutoSaveFiles();

      BlobStorage<AutoSaveUtils.Metadata> nonDefaultBucket =
         blobStorageManager.getStorage(nonDefaultOrgId.toLowerCase() + "__autoSave", false);

      assertEquals(1, nonDefaultBucket.paths().count(),
                  "removeExpiredAutoSaveFiles() currently never inspects any bucket other than the "
                  + "default org's -- a non-default org's file survives untouched regardless of age, "
                  + "which is the reported gap (not whether age-based deletion itself works)");
   }

   // ── fixture helper ──

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
}
