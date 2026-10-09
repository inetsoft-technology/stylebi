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
package inetsoft.web.admin.security.user;

/*
 * Bug #77088: the EM group and user renames ran the storage, MV and data cycle migrations, which
 * resolve the current org, without switching to the renamed identity's org. A site admin whose
 * session was on org A renaming an identity of org B therefore rewrote org A's data and left org
 * B's stale. The permission grants of the renamed identity were stamped with the current org too.
 *
 * Bug #77098: the EM role rename rewrote the VPM hidden-column roles of the current org, and the
 * Users root permission grants were stamped with the current org.
 */

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.mv.MVManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceIdentityRenameOrgTest {
   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));

      // the session's current org is ORG_A unless an org scope is active, which mirrors the
      // precedence of XPrincipal.getCurrentOrgId()
      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenAnswer(inv -> {
         String scoped = OrganizationContextHolder.getCurrentOrgId();
         return scoped != null ? scoped : ORG_A;
      });
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      // real runInOrgScope()
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().defaultAnswer(CALLS_REAL_METHODS)
                                       .strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganization(ORG_A)).thenReturn(new FSOrganization(ORG_A));
      when(securityProvider.getGroupMembers(any())).thenReturn(new Identity[0]);
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      SystemAdminService systemAdminService = mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);

      identityService = mock(IdentityService.class);
      storage = mock(IndexedStorage.class);
      cycleManager = mock(DataCycleManager.class);
      mvManager = mock(MVManager.class);
      dependencyStorageService = mock(DependencyStorageService.class);
      recycleBin = mock(RecycleBin.class);
      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("admin", ORG_A).convertToKey());

      doAnswer(inv -> record("storage")).when(storage)
         .migrateStorageData(any(IdentityID.class), any(IdentityID.class), anyInt());
      doAnswer(inv -> record("cycle")).when(cycleManager)
         .updateCycleInfoNotify(anyString(), anyString(), anyBoolean());
      doAnswer(inv -> record("mvAssets")).when(mvManager).migrateUserAssetsMV(any(), any());
      doAnswer(inv -> record("mvUsers")).when(mvManager).updateMVUser(any(), any());

      // each org has one data source "ds" whose data model holds that org's VPMs; the data model
      // resolves the org from the current org, like DataSourceRegistry
      xRepository = mock(XRepository.class, withSettings().lenient());
      when(xRepository.getDataSourceFullNames(any(IdentityID.class))).thenAnswer(inv -> {
         IdentityID orgID = inv.getArgument(0);
         vpmEvents.add("list(" + orgID.getOrgID() + ")@" + orgManager.getCurrentOrgID());
         return vpms.containsKey(orgID.getOrgID()) ? new String[] { "ds" } : new String[0];
      });
      when(xRepository.getDataModel("ds")).thenAnswer(inv -> {
         if(orgManager.getCurrentOrgID().equals(failingOrg)) {
            throw new IllegalStateException("unreadable data model");
         }

         return dataModel(orgManager.getCurrentOrgID());
      });

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine,
         mock(IdentityThemeService.class), null, mock(FavoritesService.class), cycleManager, null,
         mvManager, storage, null, null, xRepository, dependencyStorageService, recycleBin);
      // no dashboard manager unless a test gives one, so that a rename doesn't look up the bean
      ObjectProvider<DashboardManager> noDashboardManager = mock(ObjectProvider.class);
      service.setDashboardManager(noDashboardManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      sUtilStatic.close();
      OrganizationContextHolder.clear();
   }

   @Test
   void siteAdmin_renamesGroupOfOtherOrg_migratesInGroupOrg() throws Exception {
      IdentityID pathGroup = new IdentityID("sales", ORG_B);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));

      service.editGroup("Primary", pathGroup, groupModel("sales", "sales2", ORG_B), principal);

      assertEquals(List.of("storage@" + ORG_B, "cycle@" + ORG_B), migrations);
      verify(storage).migrateStorageData(
         new IdentityID("sales", ORG_B), new IdentityID("sales2", ORG_B), Identity.GROUP);
      verify(cycleManager).updateCycleInfoNotify("sales", "sales2", false);
      verify(identityService).setIdentityPermissions(
         eq(pathGroup), eq(new IdentityID("sales2", ORG_B)), eq(ResourceType.SECURITY_GROUP),
         eq(principal), any(), eq(ORG_B));
      assertEquals(ORG_A, orgManager.getCurrentOrgID(), "the org scope must be restored");
   }

   @Test
   void siteAdmin_renamesGroupOfCurrentOrg_migratesInCurrentOrg() throws Exception {
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));

      service.editGroup("Primary", pathGroup, groupModel("sales", "sales2", ORG_A), principal);

      assertEquals(List.of("storage@" + ORG_A, "cycle@" + ORG_A), migrations);
   }

   @Test
   void orgAdmin_renamesGroupOfOwnOrg_migratesInOwnOrg() throws Exception {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));

      service.editGroup("Primary", pathGroup, groupModel("sales", "sales2", ORG_A), principal);

      assertEquals(List.of("storage@" + ORG_A, "cycle@" + ORG_A), migrations);
      verify(identityService).setIdentityPermissions(
         eq(pathGroup), eq(new IdentityID("sales2", ORG_A)), eq(ResourceType.SECURITY_GROUP),
         eq(principal), any(), eq(ORG_A));
   }

   @Test
   void groupMembershipOnlyEdit_runsNoMigrations() throws Exception {
      IdentityID pathGroup = new IdentityID("sales", ORG_B);
      FSGroup oldGroup = new FSGroup(pathGroup);
      when(provider.getGroup(pathGroup)).thenReturn(oldGroup);
      EditGroupPaneModel model = groupModel("sales", "sales", ORG_B);

      service.editGroup("Primary", pathGroup, model, principal);

      verify(identityService).setIdentity(oldGroup, model, provider, principal);
      verifyNoInteractions(storage, cycleManager);
   }

   @Test
   void siteAdmin_renamesUserOfOtherOrg_migratesInUserOrg() throws Exception {
      IdentityID oldUser = new IdentityID("bob", ORG_B);
      IdentityID newUser = new IdentityID("bob2", ORG_B);
      stubUser(oldUser);

      service.editUser(userModel("bob", "bob2", ORG_B), "Primary", principal);

      assertEquals(List.of("storage@" + ORG_B, "mvAssets@" + ORG_B, "mvUsers@" + ORG_B,
                           "cycle@" + ORG_B), migrations);
      verify(storage).migrateStorageData(oldUser, newUser, Identity.USER);
      verify(mvManager).migrateUserAssetsMV(oldUser, newUser);
      verify(mvManager).updateMVUser(oldUser, newUser);
      verify(cycleManager).updateCycleInfoNotify("bob", "bob2", true);
      verify(dependencyStorageService).migrateStorageData(oldUser, newUser);
      verify(recycleBin).renameUser(oldUser, newUser);
      verify(identityService).setIdentityPermissions(
         eq(oldUser), eq(newUser), eq(ResourceType.SECURITY_USER), eq(principal), any(),
         eq(ORG_B));
      assertEquals(ORG_A, orgManager.getCurrentOrgID(), "the org scope must be restored");
   }

   @Test
   void renamesUserOfCurrentOrg_migratesInCurrentOrg() throws Exception {
      stubUser(new IdentityID("bob", ORG_A));

      service.editUser(userModel("bob", "bob2", ORG_A), "Primary", principal);

      assertEquals(List.of("storage@" + ORG_A, "mvAssets@" + ORG_A, "mvUsers@" + ORG_A,
                           "cycle@" + ORG_A), migrations);
   }

   // Bug #77798: the rename is already saved when the permission write fails, so every rename
   // migration still runs and the failure is reported after them
   @Test
   void renameUser_permissionWriteFails_migrationsStillRunThenFailureReported() throws Exception {
      IdentityID oldUser = new IdentityID("bob", ORG_A);
      IdentityID newUser = new IdentityID("bob2", ORG_A);
      stubUser(oldUser);
      inetsoft.util.MessageException failure =
         new inetsoft.util.MessageException("may not have been saved");
      doThrow(failure).when(identityService).setIdentityPermissions(
         any(IdentityID.class), any(IdentityID.class), any(ResourceType.class), any(), any(),
         anyString());

      Exception thrown = assertThrows(Exception.class, () ->
         service.editUser(userModel("bob", "bob2", ORG_A), "Primary", principal));

      assertSame(failure, thrown);
      assertEquals(List.of("storage@" + ORG_A, "mvAssets@" + ORG_A, "mvUsers@" + ORG_A,
                           "cycle@" + ORG_A), migrations);
      verify(dependencyStorageService).migrateStorageData(oldUser, newUser);
      verify(recycleBin).renameUser(oldUser, newUser);
   }

   @Test
   void userEditWithoutRename_runsNoMigrations() throws Exception {
      stubUser(new IdentityID("bob", ORG_B));

      service.editUser(userModel("bob", "bob", ORG_B), "Primary", principal);

      verifyNoInteractions(storage, cycleManager, mvManager, dependencyStorageService, recycleBin);
   }

   // Bug #77097: the REST API renames through the same helpers as the EM panes
   @Test
   void migrateGroupRename_fromOtherOrgSession_migratesGroupInItsOrg() throws Exception {
      IdentityID oldGroup = new IdentityID("sales", ORG_B);
      IdentityID newGroup = new IdentityID("sales2", ORG_B);

      service.migrateGroupRename(oldGroup, newGroup);

      assertEquals(List.of("storage@" + ORG_B, "cycle@" + ORG_B), migrations);
      verify(storage).migrateStorageData(oldGroup, newGroup, Identity.GROUP);
      verify(storage, never()).migrateStorageData(anyString(), anyString());
      verify(cycleManager).updateCycleInfoNotify("sales", "sales2", false);
      verifyNoInteractions(mvManager, dependencyStorageService, recycleBin);
      assertEquals(ORG_A, orgManager.getCurrentOrgID(), "the org scope must be restored");
   }

   @Test
   void migrateUserRename_fromOtherOrgSession_migratesUserInItsOrg() throws Exception {
      IdentityID oldUser = new IdentityID("bob", ORG_B);
      IdentityID newUser = new IdentityID("bob2", ORG_B);

      service.migrateUserRename(oldUser, newUser);

      assertEquals(List.of("storage@" + ORG_B, "mvAssets@" + ORG_B, "mvUsers@" + ORG_B,
                           "cycle@" + ORG_B), migrations);
      verify(storage).migrateStorageData(oldUser, newUser, Identity.USER);
      verify(cycleManager).updateCycleInfoNotify("bob", "bob2", true);
      verify(dependencyStorageService).migrateStorageData(oldUser, newUser);
      verify(recycleBin).renameUser(oldUser, newUser);
      assertEquals(ORG_A, orgManager.getCurrentOrgID(), "the org scope must be restored");
   }

   // Bug #78101: the viewsheets of the dashboard creates refused while the user was renamed are
   // removed once the user's assets are moved, in the user's org, not while they are moved
   @Test
   @SuppressWarnings("unchecked")
   void migrateUserRename_removesRefusedDashboardViewsheetsAfterTheMove() throws Exception {
      IdentityID oldUser = new IdentityID("bob", ORG_B);
      IdentityID newUser = new IdentityID("bob2", ORG_B);
      String refused = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                      "d1", oldUser).toIdentifier();
      DashboardManager dashboardManager = mock(DashboardManager.class);
      doAnswer(inv -> {
         record("lock");
         ((Runnable) inv.getArgument(0)).run();
         return null;
      }).when(dashboardManager).runLocked(any());
      when(dashboardManager.takeRefusedViewsheets(oldUser)).thenReturn(List.of(refused));
      doAnswer(inv -> record("removeRefused"))
         .when(dashboardManager).removeRefusedViewsheet(any(), any(), any());
      ObjectProvider<DashboardManager> managerProvider = mock(ObjectProvider.class);
      when(managerProvider.getIfAvailable()).thenReturn(dashboardManager);
      service.setDashboardManager(managerProvider);

      service.migrateUserRename(oldUser, newUser);

      assertEquals(List.of("storage@" + ORG_B, "mvAssets@" + ORG_B, "mvUsers@" + ORG_B,
                           "cycle@" + ORG_B, "lock@" + ORG_B, "removeRefused@" + ORG_B),
                   migrations);
      verify(dashboardManager).removeRefusedViewsheet(
         argThat(e -> oldUser.equals(e.getUser()) && "d1".equals(e.getPath())), eq(newUser),
         any());
   }

   // Bug #78101: a stale Spring AOT bean definition of UserTreeService (another jar's copy first
   // on the classpath) didn't apply setDashboardManager(), so the refused creates' viewsheets were
   // never removed. Without the injected manager the Spring bean is used.
   @Test
   void migrateUserRename_withoutInjectedDashboardManager_usesTheBean() throws Exception {
      IdentityID oldUser = new IdentityID("bob", ORG_B);
      IdentityID newUser = new IdentityID("bob2", ORG_B);
      String refused = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                      "d1", oldUser).toIdentifier();
      DashboardManager dashboardManager = mock(DashboardManager.class);
      doAnswer(inv -> {
         ((Runnable) inv.getArgument(0)).run();
         return null;
      }).when(dashboardManager).runLocked(any());
      when(dashboardManager.takeRefusedViewsheets(oldUser)).thenReturn(List.of(refused));
      service.setDashboardManager(null);

      try(MockedStatic<DashboardManager> managerStatic = mockStatic(DashboardManager.class)) {
         managerStatic.when(DashboardManager::getManager).thenReturn(dashboardManager);
         service.migrateUserRename(oldUser, newUser);
      }

      verify(dashboardManager).removeRefusedViewsheet(
         argThat(e -> oldUser.equals(e.getUser()) && "d1".equals(e.getPath())), eq(newUser),
         any());
   }

   @Test
   void migrateRename_sameID_runsNoMigrations() throws Exception {
      IdentityID id = new IdentityID("sales", ORG_B);

      service.migrateGroupRename(id, id);
      service.migrateUserRename(id, id);

      verifyNoInteractions(storage, cycleManager, mvManager, dependencyStorageService, recycleBin);
   }

   @Test
   void siteAdmin_renamesRoleOfOtherOrg_rewritesVpmsOfRoleOrg() throws Exception {
      addVpm(ORG_A, "v", "analyst");
      addVpm(ORG_B, "v", "analyst");
      stubRole(new IdentityID("analyst", ORG_B));

      service.editRole(roleModel("analyst", "analyst2", ORG_B), "Primary", principal);

      assertEquals(List.of("analyst2"), vpmRoles(ORG_B, "v"));
      assertEquals(List.of("analyst"), vpmRoles(ORG_A, "v"));
      assertEquals(List.of("list(" + ORG_B + ")@" + ORG_B, "save:v@" + ORG_B), vpmEvents);
      assertEquals(ORG_A, orgManager.getCurrentOrgID(), "the org scope must be restored");
   }

   @Test
   void renamesRoleOfCurrentOrg_savesOnlyVpmsListingTheRole() throws Exception {
      addVpm(ORG_A, "v1", "analyst", "sales");
      addVpm(ORG_A, "v2", "sales");
      addVpm(ORG_A, "v3");
      stubRole(new IdentityID("analyst", ORG_A));

      service.editRole(roleModel("analyst", "analyst2", ORG_A), "Primary", principal);

      assertEquals(List.of("sales", "analyst2"), vpmRoles(ORG_A, "v1"));
      assertEquals(List.of("sales"), vpmRoles(ORG_A, "v2"));
      assertEquals(List.of("list(" + ORG_A + ")@" + ORG_A, "save:v1@" + ORG_A), vpmEvents);
   }

   @Test
   void renamesGlobalRole_rewritesVpmsOfEveryOrgListingIt() throws Exception {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { ORG_A, ORG_B, ORG_C });
      addVpm(ORG_A, "v", "gRole");
      addVpm(ORG_B, "v", "gRole");
      addVpm(ORG_C, "v", "sales");
      stubRole(new IdentityID("gRole", null));

      service.editRole(roleModel("gRole", "gRole2", null), "Primary", principal);

      assertEquals(List.of("gRole2"), vpmRoles(ORG_A, "v"));
      assertEquals(List.of("gRole2"), vpmRoles(ORG_B, "v"));
      assertEquals(List.of("sales"), vpmRoles(ORG_C, "v"));
      assertTrue(vpmEvents.contains("save:v@" + ORG_A), vpmEvents::toString);
      assertTrue(vpmEvents.contains("save:v@" + ORG_B), vpmEvents::toString);
      assertFalse(vpmEvents.contains("save:v@" + ORG_C), "unchanged VPMs must not be saved");
      assertEquals(ORG_A, orgManager.getCurrentOrgID(), "the org scope must be restored");
   }

   @Test
   void renamesGlobalRoleOntoNameOfOrgRole_doesNotExemptTheOrgRole() throws Exception {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { ORG_A, ORG_B });
      addVpm(ORG_A, "v", "gRole");
      addVpm(ORG_B, "v", "gRole");
      stubRole(new IdentityID("gRole", null));
      // org B has its own role with the new name, which the VPM does not exempt
      when(securityProvider.getRole(new IdentityID("gRole2", ORG_B)))
         .thenReturn(new FSRole(new IdentityID("gRole2", ORG_B)));
      ListAppender<ILoggingEvent> appender = attachAppender();

      try {
         service.editRole(roleModel("gRole", "gRole2", null), "Primary", principal);
      }
      finally {
         detachAppender(appender);
      }

      assertEquals(List.of("gRole2"), vpmRoles(ORG_A, "v"));
      assertFalse(vpmRoles(ORG_B, "v").contains("gRole2"),
                  "the VPM must not start exempting org B's own gRole2 role");
      assertTrue(vpmEvents.contains("save:v@" + ORG_B), "the removal must be saved");
      assertTrue(appender.list.stream().anyMatch(
                    e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("gRole2") &&
                       e.getFormattedMessage().contains(ORG_B)),
                 "the skipped VPM role rename must be logged");
   }

   @Test
   void renamesGlobalRole_keepsOldNameWhereSameNamedOrgRoleRemains() throws Exception {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { ORG_A, ORG_B });
      addVpm(ORG_A, "v", "gRole");
      addVpm(ORG_B, "v", "gRole");
      stubRole(new IdentityID("gRole", null));
      // org B has its own role gRole, which the VPM keeps exempting
      when(securityProvider.getRole(new IdentityID("gRole", ORG_B)))
         .thenReturn(new FSRole(new IdentityID("gRole", ORG_B)));

      service.editRole(roleModel("gRole", "gRole2", null), "Primary", principal);

      assertEquals(List.of("gRole2"), vpmRoles(ORG_A, "v"));
      assertEquals(List.of("gRole", "gRole2"), vpmRoles(ORG_B, "v"));
      assertTrue(vpmEvents.contains("save:v@" + ORG_B), "the addition must be saved");
   }

   @Test
   void renamesOrgRole_keepsOldNameWhereSameNamedGlobalRoleRemains() throws Exception {
      addVpm(ORG_B, "v", "analyst");
      stubRole(new IdentityID("analyst", ORG_B));
      when(securityProvider.getRole(new IdentityID("analyst", null)))
         .thenReturn(new FSRole(new IdentityID("analyst", null)));

      service.editRole(roleModel("analyst", "analyst2", ORG_B), "Primary", principal);

      assertEquals(List.of("analyst", "analyst2"), vpmRoles(ORG_B, "v"));
      assertEquals(List.of("list(" + ORG_B + ")@" + ORG_B, "save:v@" + ORG_B), vpmEvents);
   }

   // Bug #77098 review: a caller may pass the global organization key instead of a null org
   @Test
   void migrateRoleRename_globalOrgKey_rewritesVpmsOfEveryOrg() throws Exception {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { ORG_A, ORG_B });
      addVpm(ORG_A, "v", "gRole");
      addVpm(ORG_B, "v", "gRole");
      // the renamed global role itself must not count as a colliding role
      when(securityProvider.getRole(new IdentityID("gRole2", null)))
         .thenReturn(new FSRole(new IdentityID("gRole2", null)));

      service.migrateRoleRename(new IdentityID("gRole", "__GLOBAL__"),
                                new IdentityID("gRole2", "__GLOBAL__"));

      assertEquals(List.of("gRole2"), vpmRoles(ORG_A, "v"));
      assertEquals(List.of("gRole2"), vpmRoles(ORG_B, "v"));
   }

   @Test
   void renamesGlobalRole_failureInOneOrg_stillMigratesOtherOrgs() throws Exception {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { ORG_A, ORG_B, ORG_C });
      addVpm(ORG_A, "v", "gRole");
      addVpm(ORG_B, "v", "gRole");
      addVpm(ORG_C, "v", "gRole");
      stubRole(new IdentityID("gRole", null));
      failingOrg = ORG_B;

      IllegalStateException ex = assertThrows(
         IllegalStateException.class,
         () -> service.editRole(roleModel("gRole", "gRole2", null), "Primary", principal));

      assertEquals("unreadable data model", ex.getMessage());
      assertEquals(List.of("gRole2"), vpmRoles(ORG_A, "v"));
      assertEquals(List.of("gRole"), vpmRoles(ORG_B, "v"));
      assertEquals(List.of("gRole2"), vpmRoles(ORG_C, "v"),
                   "the orgs after the failing org must still be migrated");
      assertEquals(ORG_A, orgManager.getCurrentOrgID(), "the org scope must be restored");
   }

   @Test
   void renamesOrgRoleOntoNameOfGlobalRole_doesNotExemptTheGlobalRole() throws Exception {
      addVpm(ORG_B, "v", "analyst");
      stubRole(new IdentityID("analyst", ORG_B));
      when(securityProvider.getRole(new IdentityID("everyone", null)))
         .thenReturn(new FSRole(new IdentityID("everyone", null)));

      service.editRole(roleModel("analyst", "everyone", ORG_B), "Primary", principal);

      assertEquals(List.of(), vpmRoles(ORG_B, "v"));
   }

   @Test
   void roleEditWithoutRename_savesNoVpms() throws Exception {
      addVpm(ORG_B, "v", "analyst");
      stubRole(new IdentityID("analyst", ORG_B));

      service.editRole(roleModel("analyst", "analyst", ORG_B), "Primary", principal);

      verify(identityService).setIdentity(any(Role.class), any(EditRolePaneModel.class),
                                          eq(provider), eq(principal));
      verifyNoInteractions(xRepository);
      assertEquals(List.of("analyst"), vpmRoles(ORG_B, "v"));
   }

   @Test
   void siteAdmin_editsUsersRootOfOtherOrg_stampsGrantsWithThatOrg() throws Exception {
      when(securityProvider.checkPermission(any(), any(ResourceType.class), anyString(),
                                            any(ResourceAction.class))).thenReturn(true);
      IdentityID rootID = new IdentityID("Users", ORG_B);

      service.editUser(userModel("Users", "Users", ORG_B), "Primary", principal);

      verify(identityService).setIdentityPermissions(
         eq(rootID), eq(rootID), eq(ResourceType.SECURITY_USER), eq(principal), any(), eq(ORG_B));
   }

   private Object record(String step) {
      migrations.add(step + "@" + orgManager.getCurrentOrgID());
      return null;
   }

   private void stubUser(IdentityID userID) {
      User user = mock(User.class, withSettings().lenient());
      when(user.getIdentityID()).thenReturn(userID);
      when(user.getPassword()).thenReturn("secret");
      when(provider.getUser(userID)).thenReturn(user);
   }

   private void stubRole(IdentityID roleID) {
      when(provider.getRole(roleID)).thenReturn(new FSRole(roleID));
   }

   private void addVpm(String orgID, String name, String... roles) {
      VirtualPrivateModel vpm = new VirtualPrivateModel(name);
      HiddenColumns hiddenColumns = new HiddenColumns();

      for(String role : roles) {
         hiddenColumns.addRole(role);
      }

      vpm.setHiddenColumns(hiddenColumns);
      vpms.computeIfAbsent(orgID, k -> new LinkedHashMap<>()).put(name, vpm);
   }

   private List<String> vpmRoles(String orgID, String name) {
      return Collections.list(vpms.get(orgID).get(name).getHiddenColumns().getRoles());
   }

   private XDataModel dataModel(String orgID) {
      Map<String, VirtualPrivateModel> orgVpms = vpms.getOrDefault(orgID, Map.of());
      XDataModel dataModel = mock(XDataModel.class, withSettings().lenient());
      when(dataModel.getVirtualPrivateModelNames())
         .thenReturn(orgVpms.keySet().toArray(new String[0]));
      when(dataModel.getVirtualPrivateModel(anyString()))
         .thenAnswer(inv -> orgVpms.get(inv.<String>getArgument(0)));
      doAnswer(inv -> {
         vpmEvents.add("save:" + inv.<VirtualPrivateModel>getArgument(0).getName() + "@" +
                       orgManager.getCurrentOrgID());
         return null;
      }).when(dataModel).addVirtualPrivateModel(any(VirtualPrivateModel.class), anyBoolean());
      return dataModel;
   }

   private static ListAppender<ILoggingEvent> attachAppender() {
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      ((Logger) LoggerFactory.getLogger(UserTreeService.class)).addAppender(appender);
      return appender;
   }

   private static void detachAppender(ListAppender<ILoggingEvent> appender) {
      ((Logger) LoggerFactory.getLogger(UserTreeService.class)).detachAppender(appender);
   }

   private static EditRolePaneModel roleModel(String oldName, String name, String orgID) {
      return EditRolePaneModel.builder()
         .name(name)
         .oldName(oldName)
         .organization(orgID)
         .isSysAdmin(false)
         .isOrgAdmin(false)
         .build();
   }

   private static EditGroupPaneModel groupModel(String oldName, String name, String orgID) {
      return EditGroupPaneModel.builder()
         .name(name)
         .oldName(oldName)
         .organization(orgID)
         .members(List.of())
         .build();
   }

   private static EditUserPaneModel userModel(String oldName, String name, String orgID) {
      return EditUserPaneModel.builder()
         .name(name)
         .oldName(oldName)
         .organization(orgID)
         .build();
   }

   private static final String ORG_A = "organizationA";
   private static final String ORG_B = "organizationB";
   private static final String ORG_C = "organizationC";
   private final List<String> migrations = new ArrayList<>();
   private final List<String> vpmEvents = new ArrayList<>();
   private final Map<String, Map<String, VirtualPrivateModel>> vpms = new HashMap<>();
   private XRepository xRepository;
   private String failingOrg;
   private EditableAuthenticationProvider provider;
   private SecurityProvider securityProvider;
   private IdentityService identityService;
   private IndexedStorage storage;
   private DataCycleManager cycleManager;
   private MVManager mvManager;
   private DependencyStorageService dependencyStorageService;
   private RecycleBin recycleBin;
   private Principal principal;
   private UserTreeService service;
   private OrganizationManager orgManager;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
