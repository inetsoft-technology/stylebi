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
package inetsoft.sree.internal;

/*
 * Scenarios 5a-5e (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.3 Schedule Task / Data Cycle" -- not duplicated here. Covers
 * DataCycleManager.migrateDataCycles()/clearDataCycles(), the completely independent mechanism
 * that migrates/removes Data Cycle *existence* (as opposed to MigrateScheduleTask, mechanism two,
 * which rewrites a Schedule Task's own Action content -- see section 二).
 *
 * A probe run while landing this file surfaced a defect NOT in the original scenario list:
 * DataCycleManager.getDataCycleIds(String orgId) (private, :741-756) calls
 * storage.getKeys(filter) -- the 1-arg IndexedStorage.getKeys() overload -- which internally
 * falls back to OrganizationManager.getCurrentOrgID() (BlobIndexedStorage.getMetadataStorage())
 * instead of ever using the orgId parameter to scope the query; the parameter is only used
 * afterwards to *label* the resulting DataCycleId. Both real call sites --
 * AbstractEditableAuthenticationProvider.copyOrganizationInternal() (:273) and
 * IdentityService.syncIdentity() (:622) -- invoke migrateDataCycles()/clearDataCycles() without
 * ever wrapping the call in OrganizationManager.runInOrgScope(oldOrgId, ...), unlike several
 * sibling steps in the same methods that do. The realistic production caller (a site admin
 * renaming/deleting a *different* org from their own Host Organization context) therefore has
 * getDataCycleIds(oldOrgId) silently scan the ACTING ADMIN'S org bucket, not the org being
 * migrated/deleted -- if that bucket has no Data Cycles (the common case), the whole
 * migrate/clear call becomes a silent no-op: nothing is copied, nothing is deleted, no
 * exception or log. This is a genuine, previously-undocumented defect, confirmed empirically
 * below (scenario 5f) rather than assumed from reading the code -- see "已确认缺陷" in the
 * matrix doc for the full write-up. Scenarios 5a/5b/5c below intentionally drive
 * migrateDataCycles()/clearDataCycles() from *within* the source org's context (matching the
 * one caller shape that does work) so they can independently verify the copy/rename/delete
 * *content* logic on its own, without being confounded by 5f's context bug.
 *
 * Scenario 5h goes one call-frame higher than 5f: instead of calling
 * DataCycleManager.migrateDataCycles() directly, it drives the same
 * AbstractEditableAuthenticationProvider.copyOrganization(...) entry point that
 * UserTreeService.createOrganization() (the real handler behind EM's "Add Organization" ->
 * "duplicate from" dialog) calls, with a real DataCycleManager wired in. This answers, with an
 * actual production call boundary rather than an internal method, whether "clone org0 into a
 * brand-new org" really does leave the new org's Data Cycles silently missing.
 */

import inetsoft.mv.MVManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.DataSpace;
import inetsoft.util.IndexedStorage;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class,
                                  DataCycleManagerOrgLifecycleTest.DataCycleManagerConfig.class,
                                  DataCycleManagerOrgLifecycleTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataCycleManagerOrgLifecycleTest {

   @Autowired
   private DataCycleManager dataCycleManager;

   @Autowired
   private IndexedStorage indexedStorage;

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── scenario 5a: copy (replace=false) copies the asset, leaves the source untouched ──

   @Test
   void copy_migrateDataCycles_copiesAssetAndLeavesSourceUntouched() throws Exception {
      String fromOrgId = "cycle_copy_from";
      String toOrgId = "cycle_copy_to";

      builder = SecurityTestDataBuilder.create()
         .addOrg("CycleCopyFrom", fromOrgId)
         .addOrg("CycleCopyTo", toOrgId)
         .setup();

      seedDataCycle(fromOrgId, "MyCycle", "alice");

      // driven from within the source org's own context -- the one call shape under which
      // getDataCycleIds(oorg.getId()) actually resolves the right storage bucket (see 5f)
      OrganizationManager.runInOrgScope(fromOrgId, () -> {
         dataCycleManager.migrateDataCycles(new Organization(fromOrgId), new Organization(toOrgId), false);
         return null;
      });

      DataCycleManager.DataCycleAsset copied = readAsset(toOrgId, "MyCycle");
      assertNotNull(copied, "copy must produce a same-named DataCycleAsset under the target org");
      assertEquals(toOrgId, copied.getOrgId(),
                  "the copied asset's own orgId field must be rewritten to the target org");

      DataCycleManager.DataCycleAsset source = readAsset(fromOrgId, "MyCycle");
      assertNotNull(source, "copy (replace=false) must never remove the source org's asset");
      assertEquals(fromOrgId, source.getOrgId(),
                  "the source asset must still point at the source org");
   }

   // ── scenario 5b: rename (replace=true) copies the asset AND removes the source entry ──

   @Test
   void rename_migrateDataCycles_replaceTrue_copiesAssetAndRemovesSource() throws Exception {
      String fromOrgId = "cycle_rename_from";
      String toOrgId = "cycle_rename_to";

      builder = SecurityTestDataBuilder.create()
         .addOrg("CycleRenameFrom", fromOrgId)
         .addOrg("CycleRenameTo", toOrgId)
         .setup();

      seedDataCycle(fromOrgId, "RenameCycle", "bob");

      OrganizationManager.runInOrgScope(fromOrgId, () -> {
         dataCycleManager.migrateDataCycles(new Organization(fromOrgId), new Organization(toOrgId), true);
         return null;
      });

      DataCycleManager.DataCycleAsset migrated = readAsset(toOrgId, "RenameCycle");
      assertNotNull(migrated, "rename must produce the asset under the target org");
      assertEquals(toOrgId, migrated.getOrgId());

      assertNull(readAsset(fromOrgId, "RenameCycle"),
                "rename (replace=true) must delete the source org's entry -- no orphan");
   }

   // ── scenario 5c: delete via clearDataCycles() removes every DataCycleId for that org ──

   @Test
   void delete_clearDataCycles_removesAllCyclesForOrg() throws Exception {
      String orgId = "cycle_delete_org";

      builder = SecurityTestDataBuilder.create()
         .addOrg("CycleDeleteOrg", orgId)
         .setup();

      seedDataCycle(orgId, "CycleOne", "carol");
      seedDataCycle(orgId, "CycleTwo", "carol");

      assertNotNull(readAsset(orgId, "CycleOne"), "precondition: first cycle must exist");
      assertNotNull(readAsset(orgId, "CycleTwo"), "precondition: second cycle must exist");

      // clearDataCycles(orgId) itself suffers from the same current-org-context coupling as
      // migrateDataCycles() (see 5f) -- drive it from within the target org's own context so
      // this scenario isolates clearDataCycles()'s own deletion logic.
      OrganizationManager.runInOrgScope(orgId, () -> {
         dataCycleManager.clearDataCycles(orgId);
         return null;
      });

      assertNull(readAsset(orgId, "CycleOne"), "every DataCycleId for the org must be gone");
      assertNull(readAsset(orgId, "CycleTwo"), "including the second one -- no partial cleanup");
   }

   // ── scenario 5d: plain (non-Data-Cycle) Schedule Task delete relies on the same
   //    indexedStorage.removeStorage(orgId) whole-bucket delete documented in the shared
   //    background's delete call chain (":1102") -- confirm a Data Cycle asset (itself just an
   //    IndexedStorage-backed AssetEntry.Type.DATA_CYCLE entry) does not survive that call, as a
   //    low-priority regression guard against IndexedStorage ever changing its per-org bucketing
   //    strategy. ──

   @Test
   void delete_indexedStorageRemoveStorage_wholeOrgBucketGone() throws Exception {
      String orgId = "cycle_bucket_delete_org";

      builder = SecurityTestDataBuilder.create()
         .addOrg("CycleBucketDeleteOrg", orgId)
         .setup();

      seedDataCycle(orgId, "BucketCycle", "dave");
      assertNotNull(readAsset(orgId, "BucketCycle"), "precondition: cycle must exist before removeStorage");

      indexedStorage.removeStorage(orgId);

      assertNull(readAsset(orgId, "BucketCycle"),
                "removeStorage(orgId) must leave no residual DataCycleAsset -- guards against a "
                + "future IndexedStorage re-bucketing change silently orphaning Data Cycle assets");
   }

   // ── scenario 5e: confirmed defect -- migrateCycleInfo() never persists the rewritten
   //    identity fields back onto the CycleInfo it mutates (DataCycleManager.java:937-957) ──

   @Test
   void migrateCycleInfo_createdByAndModifiedBy_remainStaleAfterMigration() throws Exception {
      String fromOrgId = "cycle_identity_from";
      String toOrgId = "cycle_identity_to";

      builder = SecurityTestDataBuilder.create()
         .addOrg("CycleIdentityFrom", fromOrgId)
         .addOrg("CycleIdentityTo", toOrgId)
         .setup();

      seedDataCycle(fromOrgId, "IdentityCycle", "erin");

      DataCycleManager.CycleInfo before = dataCycleManager.getCycleInfo("IdentityCycle", fromOrgId);
      String createdByBefore = before.getCreatedBy();
      String modifiedByBefore = before.getLastModifiedBy();
      assertTrue(createdByBefore != null && createdByBefore.endsWith(fromOrgId),
                "sanity check: seeded createdBy must be keyed to the source org before migration");

      OrganizationManager.runInOrgScope(fromOrgId, () -> {
         dataCycleManager.migrateDataCycles(new Organization(fromOrgId), new Organization(toOrgId), false);
         return null;
      });

      DataCycleManager.DataCycleAsset migrated = readAsset(toOrgId, "IdentityCycle");
      assertNotNull(migrated);
      DataCycleManager.CycleInfo migratedInfo = migrated.getInfo();

      // migrateCycleInfo() (:937-957) does `IdentityID identityID =
      // IdentityID.getIdentityIDFromKey(createdBy); identityID.setOrgID(norg.getId());` and then
      // throws the result away -- it never calls cycleInfo.setCreatedBy(identityID.convertToKey())
      // (or the lastModifiedBy equivalent). The asset's own orgId field IS rewritten (a different
      // field, set directly two lines above in migrateDataCycles() itself), but the identity
      // strings inside CycleInfo are untouched -- this is the current, confirmed (not
      // hypothetical) behavior, recorded here as the regression baseline for a known bug, not as
      // asserting it is correct.
      assertEquals(createdByBefore, migratedInfo.getCreatedBy(),
                  "confirmed defect: createdBy is left pointing at the OLD org after migration -- "
                  + "migrateCycleInfo() computes the rewritten IdentityID but never writes it back");
      assertEquals(modifiedByBefore, migratedInfo.getLastModifiedBy(),
                  "confirmed defect: lastModifiedBy has the same never-written-back bug");
      assertEquals(toOrgId, migrated.getOrgId(),
                  "meanwhile the asset's own orgId field (a separate field, set directly in "
                  + "migrateDataCycles()) is correctly rewritten -- the inconsistency IS the bug");
   }

   // ── scenario 5f (new, not in the original 5a-5e list): the current-org-context coupling
   //    defect described in this file's header comment, confirmed empirically. ──

   @Test
   void migrateDataCycles_calledOutsideSourceOrgContext_silentlyMigratesNothing() throws Exception {
      String fromOrgId = "cycle_ctxbug_from";
      String toOrgId = "cycle_ctxbug_to";

      builder = SecurityTestDataBuilder.create()
         .addOrg("CycleCtxBugFrom", fromOrgId)
         .addOrg("CycleCtxBugTo", toOrgId)
         .setup();

      seedDataCycle(fromOrgId, "CtxBugCycle", "frank");
      assertNotNull(readAsset(fromOrgId, "CtxBugCycle"), "precondition: cycle exists in source org");

      // deliberately NOT wrapped in OrganizationManager.runInOrgScope(fromOrgId, ...) -- this is
      // exactly how both real call sites invoke it (AbstractEditableAuthenticationProvider
      // .copyOrganizationInternal():273, IdentityService.syncIdentity():622): the acting
      // principal's own org context (here, the test JVM's default -- Organization
      // .getDefaultOrganizationID(), "host-org") is left in place, standing in for the realistic
      // production case of a site admin renaming a *different* org from their own Host
      // Organization session.
      dataCycleManager.migrateDataCycles(new Organization(fromOrgId), new Organization(toOrgId), false);

      assertNull(readAsset(toOrgId, "CtxBugCycle"),
                "confirmed defect: getDataCycleIds(oorg.getId()) (DataCycleManager.java:741-756) "
                + "resolves its storage.getKeys(filter) call via the CURRENT thread's org context "
                + "(BlobIndexedStorage.getMetadataStorage(null) -> OrganizationManager"
                + ".getCurrentOrgID()), not the oorg.getId() parameter it was handed -- since "
                + "neither real call site wraps the call in runInOrgScope(oldOrgId, ...), a site "
                + "admin acting from Host Organization silently migrates ZERO Data Cycles whenever "
                + "the org being renamed has none of its own under the admin's own org bucket");
      assertNotNull(readAsset(fromOrgId, "CtxBugCycle"),
                   "the source asset is untouched -- this is a silent no-op, not a crash or a "
                   + "partial/corrupting migration");
   }

   // ── scenario 5h (new, verifies 5f's finding through the real production entry point instead
   //    of calling DataCycleManager.migrateDataCycles() directly): EM's "Add Organization" ->
   //    "duplicate from" dialog (CreateOrganizationDialogComponent) posts to
   //    /api/em/security/users/create-organization/{provider}, handled by
   //    OrganizationController.createOrganization() -> UserTreeService.createOrganization(),
   //    which -- when cloning -- calls
   //    AbstractEditableAuthenticationProvider.copyOrganization(fromOrg, newOrgID,
   //    identityService, themeService, dashboardRegistryManager, dataCycleManager, principal,
   //    replace=false, defaultPassword). That "duplicate from" dropdown independently fetches
   //    ../api/em/security/users/get-all-organizations and has no reference anywhere to the
   //    page-header org switcher/PageHeaderService.currentOrgId, so there is no requirement (and
   //    no natural admin workflow) that forces the acting principal's own current org (its
   //    curr_org_id session property, see EmPageHeaderController) to equal the clone source --
   //    unlike renaming an org, which the EM UI does force you to switch into first (5b's
   //    precondition). This test drives that exact real entry point -- not the lower-level
   //    migrateDataCycles() call 5f exercises -- with a real DataCycleManager, to confirm the
   //    clone genuinely ends up missing the source org's Data Cycle. ──

   @Test
   void cloneOrganization_viaRealCopyOrganizationEntryPoint_newOrgSilentlyMissingSourceDataCycle()
      throws Exception
   {
      String fromOrgId = "cycle_clone_from";
      String toOrgId = "cycle_clone_to";

      builder = SecurityTestDataBuilder.create()
         .addOrg("CycleCloneFrom", fromOrgId)
         .setup();

      seedDataCycle(fromOrgId, "CloneCycle", "grace");
      assertNotNull(readAsset(fromOrgId, "CloneCycle"), "precondition: cycle exists in source org");

      StubProvider provider = new StubProvider();
      FSOrganization fromOrg = new FSOrganization(fromOrgId);
      fromOrg.setName("CycleCloneFrom");

      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      // deliberately NOT wrapped in OrganizationManager.runInOrgScope(fromOrgId, ...) -- this
      // mirrors UserTreeService.createOrganization(), which never switches the acting
      // principal's org context before calling copyOrganization(); the ambient context here is
      // the test JVM's default (Organization.getDefaultOrganizationID(), "host-org"), standing
      // in for the site admin's own session while they clone a *different* org.
      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);

         try {
            provider.copyOrganization(fromOrg, toOrgId, mock(IdentityService.class),
               mock(IdentityThemeService.class), mock(DashboardRegistryManager.class),
               dataCycleManager, mock(Principal.class), false, "Password123!");
         }
         catch(Exception e) {
            // Tolerated: copyOrganizationInternal()'s tail touches several static singletons
            // (ScheduleManager.getScheduleManager(), etc.) that may not behave fully in this
            // minimal context. dataCycleManager.migrateDataCycles(...) (the step this scenario
            // cares about) runs earlier in the same replace=false block and is itself wrapped in
            // its own try/catch inside copyOrganizationInternal() that only logs -- its effect on
            // storage has already landed by the time any later, unrelated step might throw.
         }
      }

      assertNull(readAsset(toOrgId, "CloneCycle"),
                "confirmed defect, reproduced through the real EM entry point: cloning org0 into "
                + "a brand-new org silently drops org0's Data Cycle -- the acting principal's own "
                + "current org (never switched by this call path) doesn't match the clone source, "
                + "so migrateDataCycles() inside copyOrganizationInternal() scans the wrong bucket");
      assertNotNull(readAsset(fromOrgId, "CloneCycle"),
                   "the source org's own cycle is untouched -- silent no-op, not data corruption");
   }

   // ── fixture helpers ──

   /**
    * Seeds one enabled DataCycleAsset with one TimeCondition and a CycleInfo whose
    * createdBy/lastModifiedBy are set to {@code userName}@{@code orgId}, via DataCycleManager's
    * own public API (addCondition()/setCycleInfo()) -- both of which correctly pass the explicit
    * orgId through to the 2-arg IndexedStorage overloads, so seeding itself is unaffected by the
    * current-org-context coupling this file documents.
    */
   private void seedDataCycle(String orgId, String cycleName, String userName) {
      // TimeCondition.at(hour, minute, second) is used instead of `new TimeCondition()` --
      // the no-arg constructor leaves `date` null, which TimeCondition.writeXML() dereferences
      // unconditionally and throws a NullPointerException that DataCycleManager.addCondition()
      // silently swallows (logs "Failed to add condition" and returns without persisting
      // anything) -- using a real daily condition here keeps the fixture's write path honest.
      dataCycleManager.addCondition(cycleName, orgId, TimeCondition.at(1, 0, 0));

      DataCycleManager.CycleInfo info = new DataCycleManager.CycleInfo(cycleName, orgId);
      String identity = new IdentityID(userName, orgId).convertToKey();
      info.setCreatedBy(identity);
      info.setLastModifiedBy(identity);
      dataCycleManager.setCycleInfo(cycleName, orgId, info);
   }

   private DataCycleManager.DataCycleAsset readAsset(String orgId, String cycleName) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DATA_CYCLE,
                                        "/__DATA_CYCLE__" + cycleName, null, orgId);
      String key = entry.toIdentifier();

      if(!indexedStorage.contains(key, orgId)) {
         return null;
      }

      return (DataCycleManager.DataCycleAsset) indexedStorage.getXMLSerializable(key, null, orgId);
   }

   // ── Spring wiring for DataCycleManager's dependency graph beyond BaseTestConfiguration and
   //    ScheduleTestConfiguration (which together already supply IndexedStorage, ScheduleManager,
   //    SecurityEngine, Cluster, DataSpace). DataCycleManager itself must be a real @Bean (not
   //    hand-constructed with `new`) so Spring drives its @PostConstruct
   //    (initAfterCreate()/loadOldConfig()) the same way it does in production. ──

   @Configuration
   public static class DataCycleManagerConfig {
      @Bean
      public MVManager mvManager(Cluster cluster) {
         return new MVManager(cluster);
      }

      @Bean
      public RepletRegistryManager repletRegistryManager(DataSpace dataSpace) {
         return new RepletRegistryManager(dataSpace);
      }

      @Bean
      public DataCycleManager dataCycleManager(
         ScheduleManager scheduleManager, IndexedStorage indexedStorage,
         inetsoft.sree.security.SecurityEngine securityEngine, Cluster cluster,
         MVManager mvManager, DataSpace dataSpace, RepletRegistryManager repletRegistryManager)
      {
         return new DataCycleManager(scheduleManager, indexedStorage, securityEngine, cluster,
                                     mvManager, dataSpace, repletRegistryManager);
      }
   }

   // ── PortalThemesManager override for scenario 5h only: BaseTestConfiguration's bean is a bare
   //    unstubbed mock whose getCssEntries() returns null, and copyOrganizationInternal() calls
   //    manager.getCssEntries().get(fromOrgId) unconditionally -- same rationale as
   //    DashboardRegistryOrgLifecycleTest.PortalThemesManagerConfig in inetsoft.sree.security. ──

   @Configuration
   public static class PortalThemesManagerConfig {
      @Bean
      @Primary
      public PortalThemesManager portalThemesManager() {
         PortalThemesManager mockPortalThemesManager = mock(PortalThemesManager.class);
         when(mockPortalThemesManager.getCssEntries()).thenReturn(new HashMap<>());
         return mockPortalThemesManager;
      }
   }

   /**
    * Minimal concrete AbstractEditableAuthenticationProvider whose identity lookups all return
    * null/empty, so copyOrganizationInternal()'s role/user/group copy loops are no-ops and only
    * the DataCycleManager call under test actually does anything -- same pattern as
    * DashboardRegistryOrgLifecycleTest.StubProvider in inetsoft.sree.security (package-private
    * there, so re-declared here rather than shared).
    */
   static class StubProvider extends AbstractEditableAuthenticationProvider {
      @Override public User  getUser(IdentityID id)  { return null; }
      @Override public Group getGroup(IdentityID id) { return null; }
      @Override public Role  getRole(IdentityID id)  { return null; }

      @Override public boolean authenticate(IdentityID userIdentity, Object credential) { return false; }
      @Override public Organization getOrganization(String id)  { return null; }
      @Override public String getOrgIdFromName(String name)     { return null; }
      @Override public String getOrgNameFromID(String id)       { return null; }
      @Override public String[] getOrganizationIDs()            { return new String[0]; }
      @Override public String[] getOrganizationNames()          { return new String[0]; }
      @Override public void tearDown() {}
   }
}
