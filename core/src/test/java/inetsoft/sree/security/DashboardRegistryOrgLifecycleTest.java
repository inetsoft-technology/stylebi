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
package inetsoft.sree.security;

/*
 * Scenarios 4c-4f (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.2 Dashboard", "注册表" subsections -- not duplicated here. Covers
 * DashboardRegistryManager/DashboardRegistry, the file-based (DataSpace-backed) VSDashboard
 * *definition* storage, which is completely independent from DashboardManager's KeyValueStorage
 * *preference* bucket (covered separately by inetsoft.sree.web.dashboard
 * .DashboardManagerOrgLifecycleTest -- scenarios 4a/4b).
 *
 * Follows the same Spring-integration pattern as OrgLifecycleThemeOrchestrationTest/
 * PermissionMatrixOrgLifecycleTest in this package: BaseTestConfiguration + @SreeHome give a real
 * DataSpace/SecurityEngine; SecurityTestDataBuilder registers real orgs/users behind a real
 * FileAuthenticationProvider so DashboardRegistry's org-id resolution (which goes through the
 * *static* SecurityEngine.getSecurity().getSecurityProvider() lookup, not any constructor-injected
 * reference) resolves our test org ids to themselves instead of collapsing to null.
 *
 * 4e drives IdentityService.setOrganizationInfo() -- private -- via reflection, same precedent as
 * AbstractEditableAuthenticationProviderStaticDepTest's reflection helpers. The full chain is driven
 * for real (IdentityService + real SecurityEngine/DashboardRegistryManager/DataSpace), with a Mockito
 * spy stubbing unrelated storage helpers. OrganizationContextHolder is set to fromOrgId first to
 * mirror EM UI (operator must switch to the target org before editing its id); eprovider is a real
 * FileAuthenticationProvider because updateOrganizationMembers() reads/writes users via it.
 */

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DashboardRegistryOrgLifecycleTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardRegistryOrgLifecycleTest {

   @Autowired
   private DashboardRegistryManager dashboardRegistryManager;

   @Autowired
   private DataSpace dataSpace;

   // No-op mock: these tests assert on dashboard-registry behavior, not EM favorites; the
   // org-delete path only hands favorites cleanup off to this collaborator.
   private final FavoritesService favoritesService = mock(FavoritesService.class);

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── scenario 4c: copyDashboardRegistry() clones admin + per-user registries, source untouched ──

   // Flaky: intermittently fails (observed independently at roughly 1-in-5 to 1-in-8 runs) with
   // "new org's per-user registry must also contain the cloned dashboard" -- securityEngine
   // .getOrgUsers(fromOrgId) sometimes returns empty right after SecurityTestDataBuilder.setup()
   // writes "alice" via authcProvider.addUser(), suggesting a timing-sensitive gap between that
   // write and SecurityEngine's own internally-cached provider reflecting it. Root cause not yet
   // isolated -- disabled rather than left flaky in the suite until someone tracks down the
   // provider-refresh timing. The mechanism itself (IdentityService.copyDashboardRegistry()
   // looping securityEngine.getOrgUsers()) is still correctly described in the matrix doc row 4c;
   // only this test's reliability is in question, not the finding.
   // Before re-enabling: every assertion pair below also calls getDashboard() twice on a live
   // registry that holds an un-detached change listener, which is the same exposure as 4d had
   // (Bug #77102). Assert on the raw registry XML (orgIdFromRegistryXml) instead.
   @Disabled("Flaky -- intermittent SecurityEngine.getOrgUsers() race right after "
      + "SecurityTestDataBuilder.setup(); root cause not yet isolated, see comment above")
   @Test
   void copy_copyDashboardRegistry_adminAndUserRegistryCloned_sourceUnaffected() throws Exception {
      String fromOrgId = "dashreg_copy_from";
      String toOrgId = "dashreg_copy_to";
      IdentityID user = new IdentityID("alice", fromOrgId);

      builder = SecurityTestDataBuilder.create()
         .addOrg("DashRegCopyFrom", fromOrgId)
         .addOrg("DashRegCopyTo", toOrgId)
         .addUser("alice", fromOrgId, "password")
         .setup();

      seedAdminDashboard(fromOrgId, "AdminDash", fromOrgId);
      seedUserDashboard(user, "AliceDash", fromOrgId, "alice");

      Organization fromOrg = new Organization(fromOrgId);
      Organization toOrg = new Organization(toOrgId);

      IdentityService identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, null, null, null, null, null, null, null, null, null, // positions 3-14
         Optional.empty(),                                                       // position 15
         null, null, null,                                                       // 16-18
         dashboardRegistryManager,                                               // position 19
         null, null, null, null, null, null, null, null,                        // 20-27
         null,                                                                   // 28
         Optional.empty());                                                     // position 29

      identityService.copyDashboardRegistry(fromOrg, toOrg);

      DashboardRegistry newAdminRegistry = dashboardRegistryManager.getRegistry(toOrgId);
      assertNotNull(newAdminRegistry.getDashboard("AdminDash"),
                    "new org's admin registry must contain the cloned dashboard");
      assertEquals(toOrgId, orgIdOf((VSDashboard) newAdminRegistry.getDashboard("AdminDash")),
                  "the clone's embedded viewsheet reference must be rewritten to the new org");

      DashboardRegistry newUserRegistry =
         dashboardRegistryManager.getRegistry(new IdentityID("alice", toOrgId));
      assertNotNull(newUserRegistry.getDashboard("AliceDash"),
                    "new org's per-user registry must also contain the cloned dashboard -- "
                    + "IdentityService.copyDashboardRegistry() loops securityEngine.getOrgUsers()");
      assertEquals(toOrgId, orgIdOf((VSDashboard) newUserRegistry.getDashboard("AliceDash")),
                  "the per-user clone's embedded viewsheet reference must also be rewritten");

      DashboardRegistry sourceAdminRegistry = dashboardRegistryManager.getRegistry(fromOrgId);
      assertNotNull(sourceAdminRegistry.getDashboard("AdminDash"),
                    "copy (replace=false) must never touch the source org's admin registry");
      assertEquals(fromOrgId, orgIdOf((VSDashboard) sourceAdminRegistry.getDashboard("AdminDash")));

      DashboardRegistry sourceUserRegistry = dashboardRegistryManager.getRegistry(user);
      assertNotNull(sourceUserRegistry.getDashboard("AliceDash"),
                    "copy (replace=false) must never touch the source org's per-user registry");
      assertEquals(fromOrgId, orgIdOf((VSDashboard) sourceUserRegistry.getDashboard("AliceDash")));
   }

   // ── scenario 4d: plain rename via copyOrganizationInternal(replace=true) alone ──

   @Test
   void rename_copyOrganizationInternal_dashboardFilesRelocatedByDataSpaceRename_contentNotRewritten()
      throws Exception
   {
      String fromOrgId = "dashreg_rename_from";
      String toOrgId = "dashreg_rename_to";
      IdentityID user = new IdentityID("bob", fromOrgId);

      // toOrgId is registered so the target org is a real org, as it would be in production.
      // Note that the assertions below deliberately do NOT load the relocated files through
      // dashboardRegistryManager.getRegistry(toOrgId) / getRegistry(bob@toOrgId), and read the
      // raw DataSpace XML instead (Bug #77102). A freshly loaded DashboardRegistry registers a
      // DataSpace change listener on its file, and a late BlobStorageEvent delivery (its own
      // port-on-load save(), the copyDataSpace() rename's events, or an ancestor "portal"
      // directory event from an earlier test class) makes that listener reset() the live
      // registry in place and re-parse it asynchronously; a lookup in that window sees an empty
      // registry.
      builder = SecurityTestDataBuilder.create()
         .addOrg("DashRegRenameFrom", fromOrgId)
         .addOrg("DashRegRenameTo", toOrgId)
         .setup();

      seedAdminDashboard(fromOrgId, "AdminDash", fromOrgId);
      seedUserDashboard(user, "BobDash", fromOrgId, "bob");

      FSOrganization fromOrg = new FSOrganization(fromOrgId);
      fromOrg.setName("DashRegRenameFrom");

      StubProvider provider = new StubProvider();

      CustomThemesManager themesManager = noopThemesManager();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);

         try {
            provider.copyOrganization(fromOrg, toOrgId, mock(IdentityService.class),
               mock(IdentityThemeService.class), dashboardRegistryManager, mock(DataCycleManager.class),
               mock(Principal.class), true, null);
         }
         catch(Exception e) {
            // Tolerated: copyOrganizationInternal()'s replace=true tail (:289) calls the static
            // RepletRegistryManager.getInstance(), which has no bean/registration in this minimal
            // context. removeOrgScopedDataSpaceElements(fromOrganization) (:283) -- the step this
            // scenario cares about -- runs strictly earlier in the same replace=true block, so its
            // effect on disk has already landed by the time this (unrelated) step throws.
         }
      }

      // Matrix row 4d's documented mechanism says dashboardRegistryManager.clear() is the *only*
      // dashboard-specific step on the replace=true path, and that the source file is later
      // deleted by removeOrgScopedDataSpaceElements() -- i.e. pure data loss. What actually
      // happens is more specific: copyDataSpace() (AbstractEditableAuthenticationProvider:146),
      // called *before* any dashboard-specific code runs, does a blanket
      // dataSpace.rename(oldPath, newPath) over every path returned by getOrgScopedPaths(fromOrg)
      // -- which matches both the admin (portal/{orgId}/dashboard-registry.xml) and per-user
      // (portal/{orgId}/{user}/dashboard-registry.xml) registry files, since both start with
      // "portal/{fromOrgId}/". By the time dashboardRegistryManager.clear() (:151) and, later,
      // removeOrgScopedDataSpaceElements(fromOrganization) (:283) run, both files have *already*
      // been physically relocated to the new org's path -- there is nothing left under the old
      // prefix for the delete step to remove. Net effect: the files are NOT lost, they are moved
      // -- but their *internal* content (the embedded VSDashboard/viewsheet identifier's org
      // segment) is never rewritten, because that rewriting only happens inside
      // DashboardRegistryManager.migrateRegistry()/migrateVSDashboard(), which this call path
      // never invokes. This is asserted here as current behavior (not @Disabled -- it is an
      // accurate description of what the code does, same convention as this file's sibling
      // OrgLifecycleThemeOrchestrationTest's documented-gap test).
      // Assert on the raw relocated XML, as 4e does, not through getRegistry(toOrgId) /
      // getRegistry(bob@toOrgId): loading a live registry here registers a change listener, and
      // a late BlobStorageEvent reset()s that registry in place and re-loads it asynchronously,
      // so back-to-back getDashboard() calls could see it empty (Bug #77102). The raw XML still
      // proves what this scenario is about: the files were moved and their content was not
      // rewritten.
      String newAdminPath = "portal/" + toOrgId + "/dashboard-registry.xml";
      String newUserPath = "portal/" + toOrgId + "/bob/dashboard-registry.xml";

      assertTrue(dataSpace.exists(null, newAdminPath),
                 "the admin registry FILE was relocated to the new org's path by "
                 + "copyDataSpace()'s blanket DataSpace rename, so it is readable there");
      assertEquals(fromOrgId, orgIdFromRegistryXml(newAdminPath),
                  "but its internal viewsheet reference still points at the OLD org -- "
                  + "copyOrganizationInternal(replace=true) never rewrites dashboard content, "
                  + "only DashboardRegistryManager.migrateRegistry() does that, and this call "
                  + "path never invokes it");

      assertTrue(dataSpace.exists(null, newUserPath),
                 "the per-user registry FILE was relocated the same way -- copyDataSpace() "
                 + "does not distinguish admin vs. per-user paths, both start with "
                 + "\"portal/{fromOrgId}/\"");
      assertEquals(fromOrgId, orgIdFromRegistryXml(newUserPath),
                  "same stale-content gap as the admin registry");

      // Nothing is left behind under the old org's prefix -- confirms "moved", not "duplicated".
      assertFalse(dataSpace.exists(null, "portal/" + fromOrgId + "/dashboard-registry.xml"),
                 "old admin registry path must no longer exist -- it was renamed away, not copied");
      assertFalse(dataSpace.exists(null, "portal/" + fromOrgId + "/bob/dashboard-registry.xml"),
                 "old per-user registry path must no longer exist either");
   }

   // ── scenario 4e: setOrganizationInfo() under EM-like current-org context ──

   @Test
   void rename_setOrganizationInfo_adminAndPerUserRegistryContentRewritten()
      throws Exception
   {
      String fromOrgId = "dashreg_full_from";
      String fromOrgName = "DashRegFullFrom";
      String toOrgId = "dashreg_full_to";
      String toOrgName = "DashRegFullTo";
      IdentityID user = new IdentityID("carol", fromOrgId);

      // Do not pre-register toOrgId: setOrganizationInfo → checkDuplicateOrgIDs would reject an
      // already-existing target id. Admin DashboardRegistry resolution for toOrg is fine after
      // migrateRegistry has written portal/{toOrgId}/dashboard-registry.xml (and we clear caches
      // below so getRegistry reloads from that file).
      builder = SecurityTestDataBuilder.create()
         .addOrg(fromOrgName, fromOrgId)
         .addUser("carol", fromOrgId, "password")
         .setup();

      seedAdminDashboard(fromOrgId, "AdminDash", fromOrgId);
      seedUserDashboard(user, "CarolDash", fromOrgId, "carol");

      AuthenticationProvider authc = SecurityEngine.getSecurity().getSecurityProvider()
         .getAuthenticationProvider();
      FileAuthenticationProvider fileProvider =
         (FileAuthenticationProvider) ((AuthenticationChain) authc).getProviders().get(0);
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(fromOrgId);

      RepletRegistryManager repletRegistryManager = mock(RepletRegistryManager.class);
      when(repletRegistryManager.getRegistry(anyString())).thenReturn(mock(RepletRegistry.class));

      IdentityService realService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         mock(IdentityThemeService.class), null, null, favoritesService, null, null,
         mock(DataCycleManager.class), null, mock(LogManager.class), null, null, null,
         Optional.empty(),
         null, mock(CustomThemesManager.class), null,
         dashboardRegistryManager,
         null, null, mock(PortalThemesManager.class), null, dataSpace,
         null, null, null,
         repletRegistryManager,
         Optional.empty());

      IdentityService spyService = spy(realService);
      // Stub storage helpers unrelated to dashboards so migrateRegistry / updateOrgScopedDataSpace
      // / removeOrgScopedDataSpaceElements can run without Blob/MV/schedule infrastructure.
      doNothing().when(spyService).updateOrgProperties(any(), any());
      doNothing().when(spyService).updateAutoSaveFiles(any(), any(), any());
      doNothing().when(spyService).updateTaskSaveFiles(any(), any());
      doNothing().when(spyService).updateIdentityPermissions(
         anyInt(), any(), any(), any(), any(), anyBoolean());
      doNothing().when(spyService).clearDataSourceMetadata();
      doNothing().when(spyService).copyStorages(any(), any(), anyBoolean());
      doNothing().when(spyService).copyRepletRegistry(any(), any());
      doNothing().when(spyService).removeOrgProperties(any());
      doNothing().when(spyService).updateRepletRegistry(any(), any());
      doNothing().when(spyService).removeStorages(any());
      doNothing().when(spyService).addCopiedIdentityPermission(any(), any(), any(), anyInt(), anyBoolean());

      IdentityModel carolMember = IdentityModel.builder()
         .identityID(user)
         .type(Identity.USER)
         .build();

      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(toOrgId)
         .name(toOrgName)
         .oldName(fromOrgName)
         .members(List.of(carolMember))
         .status(true)
         .build();

      Method setOrganizationInfo = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      setOrganizationInfo.setAccessible(true);

      CustomThemesManager themesManager = noopThemesManager();

      // Mirror EM UI: operator switches to the org being renamed before editing its id, so
      // updateOrganizationMembers()'s :869 migrateRegistry(currentOrg) sees current == fromOrg.
      OrganizationContextHolder.setCurrentOrgId(fromOrgId);

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);

         try {
            setOrganizationInfo.invoke(spyService, oldOrg, model, fileProvider, mock(Principal.class));
         }
         catch(Exception e) {
            // Tolerated: copyOrganizationInternal()'s replace=true tail may hit statics
            // (FSService/XJobPool/…) absent in this minimal context; dashboard migration +
            // DataSpace steps that this scenario asserts run earlier.
         }
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }

      String adminPath = "portal/" + toOrgId + "/dashboard-registry.xml";
      String userPath = "portal/" + toOrgId + "/carol/dashboard-registry.xml";

      assertTrue(dataSpace.exists(null, adminPath),
                 "admin registry file must exist under the new org after setOrganizationInfo");
      assertTrue(dataSpace.exists(null, userPath),
                 "per-user registry file must exist under the new org after setOrganizationInfo");

      // Assert org segment from raw DataSpace XML -- do not use getRegistry(toOrgId) here.
      // After a partial syncIdentity failure the provider may not yet list toOrgId, and
      // DashboardRegistry(String) then collapses organizationId to null and loads the wrong path.
      assertEquals(toOrgId, orgIdFromRegistryXml(adminPath),
                  "admin-level embedded viewsheet org segment must be rewritten "
                  + "(setOrganizationInfo :2127 migrateRegistry)");
      assertEquals(toOrgId, orgIdFromRegistryXml(userPath),
                  "per-user embedded viewsheet org segment must be rewritten when current org "
                  + "context matches fromOrg (EM UI path via updateOrganizationMembers :869)");
   }

   // ── scenario 4f: delete via removeOrgScopedDataSpaceElements() -- no orphan of either kind ──

   @Test
   void delete_removeOrgScopedDataSpaceElements_bothAdminAndPerUserRegistryFilesRemoved()
      throws Exception
   {
      String orgId = "dashreg_delete_org";
      IdentityID user = new IdentityID("dave", orgId);

      builder = SecurityTestDataBuilder.create()
         .addOrg("DashRegDeleteOrg", orgId)
         .setup();

      seedAdminDashboard(orgId, "AdminDash", orgId);
      seedUserDashboard(user, "DaveDash", orgId, "dave");

      String adminPath = "portal/" + orgId + "/dashboard-registry.xml";
      String userPath = "portal/" + orgId + "/dave/dashboard-registry.xml";

      assertTrue(dataSpace.exists(null, adminPath), "precondition: admin registry file must exist");
      assertTrue(dataSpace.exists(null, userPath), "precondition: per-user registry file must exist");

      IdentityService identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, null, null, null, null, null, null, null, null, null,
         Optional.empty(),
         null, null, null, null, null, null, null, null, dataSpace, null, null, null, null,
         Optional.empty());

      identityService.removeOrgScopedDataSpaceElements(new Organization(orgId));

      assertFalse(dataSpace.exists(null, adminPath),
                 "admin registry file must be gone -- no orphan (matrix row 4f)");
      assertFalse(dataSpace.exists(null, userPath),
                 "per-user registry file must also be gone -- getOrgScopedPaths() matches any "
                 + "path starting with \"portal/{orgId}/\", which covers the nested per-user path "
                 + "too, so no orphan survives a delete for either registry shape");
   }

   // ── Bug #77231: migrateRegistry() with no new org must not write the file back ──

   @Test
   void migrateRegistry_noNewOrg_registryFileDeletedAndNotWrittenBack() throws Exception {
      String orgId = "dashreg_migrate_null";

      builder = SecurityTestDataBuilder.create()
         .addOrg("DashRegMigrateNull", orgId)
         .setup();

      seedAdminDashboard(orgId, "AdminDash", orgId);
      String adminPath = "portal/" + orgId + "/dashboard-registry.xml";
      assertTrue(dataSpace.exists(null, adminPath), "precondition: admin registry file must exist");

      dashboardRegistryManager.migrateRegistry(null, new Organization(orgId), null);

      assertFalse(dataSpace.exists(null, adminPath),
                  "migrateRegistry(null, org, null) deletes the registry file, it must not "
                  + "save() it back at the same path");
      assertFalse(registryCache().containsKey(orgId + "__ADMIN__"),
                  "the removed registry must not stay cached");
   }

   // ── Bug #77231: org delete evicts the org's global and user registries ──

   @Test
   void delete_syncIdentity_evictsGlobalAndUserRegistries_recreatedOrgGetsFreshEmptyRegistries()
      throws Exception
   {
      String orgId = "dashreg_evict_org";
      String orgName = "DashRegEvictOrg";
      IdentityID user = new IdentityID("erin", orgId);

      builder = SecurityTestDataBuilder.create()
         .addOrg(orgName, orgId)
         .addUser("erin", orgId, "password")
         .setup();

      FileAuthenticationProvider fileProvider = fileProvider();
      assertTrue(Arrays.asList(fileProvider.getUsers()).contains(user),
                 "precondition: the provider must list the org's user");

      seedAdminDashboard(orgId, "AdminDash", orgId);
      seedUserDashboard(user, "ErinDash", orgId, "erin");
      DashboardRegistry oldGlobal = dashboardRegistryManager.getRegistry(orgId);
      DashboardRegistry oldUser = dashboardRegistryManager.getRegistry(user);
      assertSame(oldGlobal, registryCache().get(orgId + "__ADMIN__"), "precondition: global cached");
      assertSame(oldUser, registryCache().get(orgId + "__erin"), "precondition: user cached");

      String adminPath = "portal/" + orgId + "/dashboard-registry.xml";
      String userPath = "portal/" + orgId + "/erin/dashboard-registry.xml";
      dataSpace.delete(null, NULL_ORG_ADMIN_PATH);

      deleteOrganization(fileProvider, new IdentityID(orgName, orgId));

      assertFalse(registryCache().containsKey(orgId + "__ADMIN__"),
                  "org delete must evict the org's global registry (" + orgId + "__ADMIN__)");
      assertFalse(registryCache().containsKey(orgId + "__erin"),
                  "org delete must evict the registries of the org's users");
      assertFalse(registryCache().containsValue(oldGlobal));
      assertFalse(registryCache().containsValue(oldUser));
      assertTrue(isDetached(oldGlobal), "the evicted global registry must no longer watch its file");
      assertTrue(isDetached(oldUser), "the evicted user registry must no longer watch its file");
      assertFalse(dataSpace.exists(null, adminPath), "admin registry file must be gone");
      assertFalse(dataSpace.exists(null, userPath), "per-user registry file must be gone");

      // re-create an org with the same id
      FSOrganization org = new FSOrganization(orgId);
      org.setName(orgName);
      org.setMembers(new String[0]);
      fileProvider.addOrganization(org);

      DashboardRegistry newGlobal = dashboardRegistryManager.getRegistry(orgId);
      DashboardRegistry newUser = dashboardRegistryManager.getRegistry(user);

      try {
         assertNotSame(oldGlobal, newGlobal, "the re-created org must get a new global registry");
         assertNotSame(oldUser, newUser, "the re-created org's user must get a new registry");
         assertEquals(0, newGlobal.getDashboardNames().length, "the new global registry is empty");
         assertEquals(0, newUser.getDashboardNames().length, "the new user registry is empty");
         assertEquals(orgId, field(DashboardRegistry.class, "organizationId", newGlobal),
                      "the new global registry must use the re-created org's path, not portal/null");

         newGlobal.addDashboard("NewDash", newVsDashboard(orgId, null));
         newGlobal.save();
      }
      finally {
         detachFileWatch(newGlobal);
         detachFileWatch(newUser);
      }

      assertTrue(dataSpace.exists(null, adminPath), "the new org's registry is saved at its path");
      assertFalse(dataSpace.exists(null, NULL_ORG_ADMIN_PATH),
                  "no registry may be written under portal/null");
   }

   @Test
   void delete_syncIdentity_doesNotEvictAnotherOrgWhoseIdStartsWithTheDeletedId() throws Exception {
      String orgId = "dashreg_ab";
      String otherOrgId = "dashreg_ab__b";

      builder = SecurityTestDataBuilder.create()
         .addOrg("DashRegAb", orgId)
         .addOrg("DashRegAbB", otherOrgId)
         .setup();

      seedAdminDashboard(orgId, "AdminDash", orgId);
      seedAdminDashboard(otherOrgId, "OtherDash", otherOrgId);
      DashboardRegistry other = dashboardRegistryManager.getRegistry(otherOrgId);

      deleteOrganization(fileProvider(), new IdentityID("DashRegAb", orgId));

      assertFalse(registryCache().containsKey(orgId + "__ADMIN__"));
      assertSame(other, registryCache().get(otherOrgId + "__ADMIN__"),
                 "deleting org " + orgId + " must not evict org " + otherOrgId);
      assertTrue(dataSpace.exists(null, "portal/" + otherOrgId + "/dashboard-registry.xml"));
   }

   @Test
   void delete_syncIdentity_userRegistriesOfAnotherOrgWhoseIdStartsWithTheDeletedIdAreKept()
      throws Exception
   {
      String orgId = "dashreg_uab";
      String otherOrgId = "dashreg_uab__b";

      builder = SecurityTestDataBuilder.create()
         .addOrg("DashRegUab", orgId)
         .addOrg("DashRegUabB", otherOrgId)
         .setup();

      // Keys are org__user and both parts may contain "__": the deleted org's user "b__y" is
      // cached as dashreg_uab__b__y, which looks like org dashreg_uab__b + user y. The other
      // org's user "x" is cached as dashreg_uab__b__x.
      DashboardRegistry ownUser = dashboardRegistryManager.getRegistry(new IdentityID("b__y", orgId));
      DashboardRegistry otherUser =
         dashboardRegistryManager.getRegistry(new IdentityID("x", otherOrgId));
      DashboardRegistry otherGlobal = dashboardRegistryManager.getRegistry(otherOrgId);
      detachFileWatch(otherUser);
      detachFileWatch(otherGlobal);
      assertSame(ownUser, registryCache().get(orgId + "__b__y"), "precondition: own user cached");
      assertSame(otherUser, registryCache().get(otherOrgId + "__x"),
                 "precondition: other org's user cached");

      deleteOrganization(fileProvider(), new IdentityID("DashRegUab", orgId));

      assertFalse(registryCache().containsKey(orgId + "__b__y"),
                  "the deleted org's user registry must be evicted, whatever its user name");
      assertTrue(isDetached(ownUser));
      assertSame(otherUser, registryCache().get(otherOrgId + "__x"),
                 "deleting org " + orgId + " must not evict the user registries of org " + otherOrgId);
      assertSame(otherGlobal, registryCache().get(otherOrgId + "__ADMIN__"),
                 "deleting org " + orgId + " must not evict the global registry of org " + otherOrgId);
   }

   @Test
   void delete_syncIdentity_evictsUserRegistryNotListedByTheProvider() throws Exception {
      String orgId = "dashreg_sso_org";
      String orgName = "DashRegSsoOrg";
      // An SSO (SAML ORGID_CLAIM) / virtual user: it has a session and a cached registry for
      // the org, but the editable provider does not list it.
      IdentityID ssoUser = new IdentityID("sso_user", orgId);

      builder = SecurityTestDataBuilder.create()
         .addOrg(orgName, orgId)
         .setup();

      FileAuthenticationProvider fileProvider = fileProvider();
      assertFalse(Arrays.asList(fileProvider.getUsers()).contains(ssoUser),
                  "precondition: the provider must not list the SSO user");

      DashboardRegistry ssoRegistry = dashboardRegistryManager.getRegistry(ssoUser);
      assertSame(ssoRegistry, registryCache().get(orgId + "__sso_user"),
                 "precondition: SSO user registry cached");

      deleteOrganization(fileProvider, new IdentityID(orgName, orgId));

      assertFalse(registryCache().containsKey(orgId + "__sso_user"),
                  "org delete must evict the registry of a user the provider does not list");
      assertTrue(isDetached(ssoRegistry), "the evicted registry must no longer watch its file");
   }

   @Test
   void delete_syncIdentity_mixedCaseOrg_evictsGlobalCachedUnderLowercasedCurrentOrgId()
      throws Exception
   {
      String orgId = "DashRegMixedCo";
      String orgName = "DashRegMixedCoName";

      builder = SecurityTestDataBuilder.create()
         .addOrg(orgName, orgId)
         .setup();

      // No-arg / current-org path, as DashboardManager does: getCurrentOrgID() lowercases the id.
      DashboardRegistry currentOrgGlobal;
      OrganizationContextHolder.setCurrentOrgId(orgId);

      try {
         currentOrgGlobal = dashboardRegistryManager.getRegistry((IdentityID) null);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }

      detachFileWatch(currentOrgGlobal);
      String lowercasedKey = orgId.toLowerCase() + "__ADMIN__";
      assertSame(currentOrgGlobal, registryCache().get(lowercasedKey),
                 "precondition: the current-org global is cached under the lowercased id");
      assertFalse(registryCache().containsKey(orgId + "__ADMIN__"),
                  "precondition: it is not cached under the org id as given");

      DashboardRegistry exactGlobal = dashboardRegistryManager.getRegistry(orgId);
      detachFileWatch(exactGlobal);

      deleteOrganization(fileProvider(), new IdentityID(orgName, orgId));

      assertFalse(registryCache().containsKey(lowercasedKey),
                  "org delete must evict the global cached under the lowercased current-org id");
      assertFalse(registryCache().containsKey(orgId + "__ADMIN__"),
                  "org delete must evict the global cached under the org id as given");
      assertFalse(registryCache().containsValue(currentOrgGlobal));
      assertFalse(registryCache().containsValue(exactGlobal));
   }

   /**
    * Runs the org branch of IdentityService.syncIdentity() (private) for a delete, with the
    * storage helpers unrelated to dashboards stubbed.
    */
   private void deleteOrganization(FileAuthenticationProvider fileProvider, IdentityID orgIdentity)
      throws Exception
   {
      RepletRegistryManager repletRegistryManager = mock(RepletRegistryManager.class);
      when(repletRegistryManager.getRegistry(anyString())).thenReturn(mock(RepletRegistry.class));

      IdentityService realService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         mock(IdentityThemeService.class), null, null, favoritesService, null, null,
         mock(DataCycleManager.class), mock(inetsoft.uql.service.DataSourceRegistry.class),
         mock(LogManager.class), null, mock(inetsoft.sree.schedule.ScheduleManager.class), null,
         Optional.empty(),
         null, mock(CustomThemesManager.class), null,
         dashboardRegistryManager,
         null, mock(inetsoft.sree.web.dashboard.DashboardManager.class),
         mock(PortalThemesManager.class), null, dataSpace,
         null, null, null,
         repletRegistryManager,
         Optional.empty());

      IdentityService spyService = spy(realService);
      doNothing().when(spyService).clearDataSourceMetadata();
      doNothing().when(spyService).removeOrgProperties(any());
      doNothing().when(spyService).updateRepletRegistry(any(), any());
      doNothing().when(spyService).removeStorages(any());

      Method syncIdentity = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      syncIdentity.setAccessible(true);

      try {
         syncIdentity.invoke(spyService, fileProvider,
                             new inetsoft.uql.util.DefaultIdentity(orgIdentity, Identity.ORGANIZATION),
                             null);
      }
      catch(InvocationTargetException e) {
         // Tolerated: the tail of the org delete may hit statics (FSService/XJobPool/...) absent
         // in this minimal context; the registry eviction and the file removal run earlier.
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private static FileAuthenticationProvider fileProvider() {
      AuthenticationProvider authc = SecurityEngine.getSecurity().getSecurityProvider()
         .getAuthenticationProvider();
      return (FileAuthenticationProvider) ((AuthenticationChain) authc).getProviders().get(0);
   }

   @SuppressWarnings("unchecked")
   private Map<String, DashboardRegistry> registryCache() {
      return (Map<String, DashboardRegistry>)
         field(DashboardRegistryManager.class, "registries", dashboardRegistryManager);
   }

   private static boolean isDetached(DashboardRegistry registry) {
      return (Boolean) field(DashboardRegistry.class, "detached", registry);
   }

   private static Object field(Class<?> type, String name, Object target) {
      try {
         java.lang.reflect.Field field = type.getDeclaredField(name);
         field.setAccessible(true);
         return field.get(target);
      }
      catch(ReflectiveOperationException e) {
         throw new AssertionError(e);
      }
   }

   private static final String NULL_ORG_ADMIN_PATH = "portal/null/dashboard-registry.xml";

   // ── fixture helpers ──

   private void seedAdminDashboard(String orgId, String dashboardName, String vsOrgId)
      throws Exception
   {
      DashboardRegistry registry = dashboardRegistryManager.getRegistry(orgId);
      registry.addDashboard(dashboardName, newVsDashboard(vsOrgId, null));
      registry.save();
      detachFileWatch(registry);
   }

   private void seedUserDashboard(IdentityID user, String dashboardName, String vsOrgId,
                                  String vsUserName) throws Exception
   {
      DashboardRegistry registry = dashboardRegistryManager.getRegistry(user);
      registry.addDashboard(dashboardName, newVsDashboard(vsOrgId, vsUserName));
      registry.save();
      detachFileWatch(registry);
   }

   /**
    * Remove the seeded registry's data space change listener (Bug #75793).
    *
    * <p>DashboardRegistry watches its backing dashboard-registry.xml and, on change, reset()s
    * dashboardsMap and re-loads it from the file at its *current* path. Those change events are
    * delivered asynchronously (LocalKeyValueStorage.ListenerDelegate hands off to
    * ThreadPool.addOnDemand, which submits to BlobStorage's single-thread eventExecutor), so the
    * event for the seeding save() above can land at an arbitrary later point -- including in the
    * middle of DashboardRegistryManager.migrateRegistry(), which mutates the registry in memory
    * (migrateVSDashboard rewrites the embedded viewsheet's org segment), re-points the listener at
    * the new org's path (modifyOrgId) and only then save()s. A late callback landing before
    * modifyOrgId reverts the in-memory rewrite from the old file, so the new file is written with
    * the *old* org id; one landing between modifyOrgId and save() re-loads from the new path, which
    * does not exist yet, leaving an empty map that is then saved as an empty registry. Both were
    * observed intermittently, and only when the JVM is busy enough to delay delivery, i.e. when the
    * whole security package runs in one surefire JVM.
    *
    * <p>clear() unregisters the listener, so any pending or later event is a no-op for this
    * instance. The instance stays in the manager's cache, so the code under test still operates on
    * this same object -- evicting it (DashboardRegistryManager.clear()) would instead have
    * migrateRegistry() re-load a fresh instance that registers a new listener on the same path,
    * which would leave the race open.
    */
   private static void detachFileWatch(DashboardRegistry registry) {
      registry.clear();
   }

   private VSDashboard newVsDashboard(String orgId, String userName) {
      VSDashboard dashboard = new VSDashboard();
      IdentityID owner = userName == null ? null : new IdentityID(userName, orgId);
      int scope = owner == null ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
      String path = (userName == null ? "" : userName + "/") + "myvs";
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, owner, orgId);

      ViewsheetEntry viewsheetEntry = new ViewsheetEntry(path, owner);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
      return dashboard;
   }

   private String orgIdOf(VSDashboard dashboard) {
      String identifier = dashboard.getViewsheet().getIdentifier();
      return AssetEntry.createAssetEntry(identifier).getOrgID();
   }

   /**
    * Read the first viewsheet {@code identifier} org segment from a dashboard-registry.xml in
    * DataSpace. Avoids DashboardRegistry(String) org-id resolution, which collapses to null when
    * the target org is not yet registered on the authentication provider.
    */
   private String orgIdFromRegistryXml(String path) throws Exception {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         assertNotNull(in, "registry XML must be readable at " + path);
         Document doc = Tool.parseXML(in);
         NodeList entries = doc.getElementsByTagName("entry");
         assertTrue(entries.getLength() > 0, "registry XML must contain a viewsheet entry: " + path);
         Element entry = (Element) entries.item(0);
         String identifier = Tool.byteDecode(Tool.getAttribute(entry, "identifier"));
         assertNotNull(identifier, "viewsheet entry must have identifier: " + path);
         return AssetEntry.createAssetEntry(identifier).getOrgID();
      }
   }

   private static CustomThemesManager noopThemesManager() {
      CustomThemesManager mockManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(mockManager);
      when(mockManager.getCustomThemes()).thenReturn(new HashSet<>());
      return mockManager;
   }

   // ── PortalThemesManager override -- same rationale as
   //    OrgLifecycleThemeOrchestrationTest.PortalThemesManagerConfig: BaseTestConfiguration's bean
   //    is a bare unstubbed mock whose getCssEntries() returns null, and
   //    copyOrganizationInternal() calls manager.getCssEntries().get(fromOrgId) unconditionally.
   //    Also supplies DashboardRegistryManager as a real bean -- BaseTestConfiguration does not
   //    declare it (it's a @Service normally picked up by component scanning in the real app,
   //    which this minimal @Configuration-only test context does not do). ──

   @Configuration
   public static class PortalThemesManagerConfig {
      @Bean
      @Primary
      public PortalThemesManager portalThemesManager() {
         PortalThemesManager mockPortalThemesManager = mock(PortalThemesManager.class);
         when(mockPortalThemesManager.getCssEntries()).thenReturn(new HashMap<>());
         return mockPortalThemesManager;
      }

      @Bean
      public DashboardRegistryManager dashboardRegistryManager(
         org.springframework.context.ApplicationEventPublisher eventPublisher,
         SecurityEngine securityEngine, inetsoft.uql.asset.DependencyHandler dependencyHandler,
         DataSpace dataSpace)
      {
         return new DashboardRegistryManager(eventPublisher, securityEngine, dependencyHandler, dataSpace);
      }
   }

   /**
    * Mirrors OrgLifecycleThemeOrchestrationTest.StubProvider -- a minimal concrete
    * AbstractEditableAuthenticationProvider whose identity lookups all return null/empty, so
    * copyOrganizationInternal()'s role/user/group copy loops are no-ops and only the
    * dashboard/DataSpace-relevant machinery under test actually does anything.
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
