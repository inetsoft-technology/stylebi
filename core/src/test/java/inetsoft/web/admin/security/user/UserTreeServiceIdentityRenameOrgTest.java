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
 */

import inetsoft.mv.MVManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceIdentityRenameOrgTest {
   @BeforeEach
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

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine,
         mock(IdentityThemeService.class), null, mock(FavoritesService.class), cycleManager, null,
         mvManager, storage, null, null, null, dependencyStorageService, recycleBin);
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

   @Test
   void migrateRename_sameID_runsNoMigrations() throws Exception {
      IdentityID id = new IdentityID("sales", ORG_B);

      service.migrateGroupRename(id, id);
      service.migrateUserRename(id, id);

      verifyNoInteractions(storage, cycleManager, mvManager, dependencyStorageService, recycleBin);
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
   private final List<String> migrations = new ArrayList<>();
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
