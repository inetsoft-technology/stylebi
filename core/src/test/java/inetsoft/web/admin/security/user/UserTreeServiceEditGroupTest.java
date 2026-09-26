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
 * Bug #77080: GroupController.editGroup authorizes the {group} path key, but
 * UserTreeService.editGroup built the identity it edits (old/new IDs, grants, membership)
 * from the request body's oldName/organization. A caller with ADMIN on one group could
 * therefore edit another group (in another org, or in the same org). editGroup must now reject
 * a body that does not name the path group, and build every written identity from the stored
 * path group.
 */

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceEditGroupTest {
   @BeforeEach
   void setUp() {
      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_A);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganization(anyString())).thenReturn(mock(Organization.class));
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      systemAdminService = mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);

      identityService = mock(IdentityService.class);
      themeService = mock(IdentityThemeService.class);
      indexedStorage = mock(IndexedStorage.class);
      dataCycleManager = mock(DataCycleManager.class);
      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("admin", ORG_A).convertToKey());

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine, themeService,
         null, null, dataCycleManager, null, null, indexedStorage, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   @Test
   void bodyNamesGroupInOtherOrg_rejectedAndNothingWritten() {
      IdentityID path = new IdentityID("g1", ORG_A);
      when(provider.getGroup(path)).thenReturn(new FSGroup(path));
      when(provider.getGroup(new IdentityID("g2", ORG_B))).thenReturn(new FSGroup(new IdentityID("g2", ORG_B)));

      assertThrows(java.lang.SecurityException.class, () ->
         service.editGroup("Primary", path, model("g2", "g2", ORG_B), principal));

      assertNothingWritten();
   }

   @Test
   void bodyNamesOtherGroupInSameOrg_rejectedAndNothingWritten() {
      IdentityID path = new IdentityID("g1", ORG_A);
      when(provider.getGroup(path)).thenReturn(new FSGroup(path));
      when(provider.getGroup(new IdentityID("g2", ORG_A))).thenReturn(new FSGroup(new IdentityID("g2", ORG_A)));

      assertThrows(java.lang.SecurityException.class, () ->
         service.editGroup("Primary", path, model("g2", "g2", ORG_A), principal));

      assertNothingWritten();
   }

   @Test
   void caseVariantOrgInBody_acceptedAndWrittenWithStoredOrg() throws Exception {
      IdentityID path = new IdentityID("g1", ORG_A);
      when(provider.getGroup(path)).thenReturn(new FSGroup(path));

      service.editGroup("Primary", path, model("g1", "g1", "ORGA"), principal);

      ArgumentCaptor<EditGroupPaneModel> written = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(any(Group.class), written.capture(), eq(provider), eq(principal));
      assertEquals(ORG_A, written.getValue().organization());
      verify(identityService).setIdentityPermissions(
         eq(path), eq(path), eq(ResourceType.SECURITY_GROUP), eq(principal), anyList(), eq(ORG_A));
   }

   @Test
   void normalRename_writesRenameWithinPathGroupOrg() throws Exception {
      IdentityID path = new IdentityID("g1", ORG_A);
      FSGroup stored = new FSGroup(path);
      when(provider.getGroup(path)).thenReturn(stored);

      service.editGroup("Primary", path, model("g1", "g1new", ORG_A), principal);

      verify(identityService).setIdentity(same(stored), any(EditGroupPaneModel.class), eq(provider), eq(principal));
      verify(identityService).setIdentityPermissions(
         eq(path), eq(new IdentityID("g1new", ORG_A)), eq(ResourceType.SECURITY_GROUP), eq(principal),
         anyList(), eq(ORG_A));
      verify(indexedStorage).migrateStorageData("g1", "g1new");
   }

   @Test
   void groupsRoot_multiTenantOrg_setsRootPermissions() throws Exception {
      IdentityID root = new IdentityID("Groups", ORG_A);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP, root.convertToKey(),
                                            ResourceAction.ADMIN)).thenReturn(true);

      service.editGroup("Primary", root, model("Groups", "Groups", ORG_A), principal);

      verify(identityService).setIdentityPermissions(
         eq(root), eq(root), eq(ResourceType.SECURITY_GROUP), eq(principal), anyList(), eq(ORG_A));
      verify(provider, never()).getGroup(any());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void groupsRoot_singleTenantDefaultOrg_setsRootPermissions() throws Exception {
      String hostOrg = Organization.getDefaultOrganizationID();
      when(orgManager.getCurrentOrgID()).thenReturn(hostOrg);
      IdentityID root = new IdentityID("Groups", hostOrg);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP, root.convertToKey(),
                                            ResourceAction.ADMIN)).thenReturn(true);
      // single-tenant root model carries no organization, so the Immutables default is sent
      EditGroupPaneModel model = EditGroupPaneModel.builder().name("Groups").oldName("Groups").root(true).build();

      service.editGroup("Primary", root, model, principal);

      verify(identityService).setIdentityPermissions(
         eq(root), eq(root), eq(ResourceType.SECURITY_GROUP), eq(principal), anyList(), eq(hostOrg));
   }

   @Test
   void missingPathGroup_clearGroupNotFoundError() {
      IdentityID path = new IdentityID("ghost", ORG_A);
      when(provider.getGroup(path)).thenReturn(null);

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.editGroup("Primary", path, model("ghost", "ghost", ORG_A), principal));

      assertEquals(Catalog.getCatalog().getString("em.security.groupNotFound", "ghost"), thrown.getMessage());
      assertNothingWritten();
   }

   private static EditGroupPaneModel model(String oldName, String name, String org) {
      return EditGroupPaneModel.builder().oldName(oldName).name(name).organization(org).build();
   }

   private void assertNothingWritten() {
      verifyNoInteractions(identityService, themeService, indexedStorage, dataCycleManager);
   }

   private static final String ORG_A = "orgA";
   private static final String ORG_B = "orgB";

   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private EditableAuthenticationProvider provider;
   private SecurityProvider securityProvider;
   private SystemAdminService systemAdminService;
   private IdentityService identityService;
   private IdentityThemeService themeService;
   private IndexedStorage indexedStorage;
   private DataCycleManager dataCycleManager;
   private Principal principal;
   private UserTreeService service;
}
