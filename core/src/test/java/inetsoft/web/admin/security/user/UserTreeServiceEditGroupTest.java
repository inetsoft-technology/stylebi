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
 * The edit-group endpoint authorizes the group in the path (@PermissionPath), but
 * UserTreeService.editGroup() builds the edited identity from the request body's organization
 * and oldName. A body naming another org (or another group) must be rejected before anything
 * is written, otherwise an org admin could rewrite another org's group members and permission.
 */

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.*;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceEditGroupTest {
   @BeforeEach
   void setUp() {
      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn("orgA");
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganization("orgA")).thenReturn(new FSOrganization("orgA"));
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      editProvider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(editProvider);

      systemAdminService = mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);

      identityService = mock(IdentityService.class);
      principal = mock(Principal.class);

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine,
         mock(IdentityThemeService.class), null, null, mock(DataCycleManager.class), null, null,
         mock(IndexedStorage.class), null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   @Test
   void bodyOrgDiffersFromPathOrg_rejectedWithoutWrites() throws Exception {
      EditGroupPaneModel model = model("g", "Admins", "orgB");

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.editGroup("Primary", new IdentityID("g", "orgA"), model, principal));

      assertEquals(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"),
                   thrown.getMessage());
      verifyNoInteractions(identityService);
   }

   @Test
   void bodyOldNameDiffersFromPathName_rejectedWithoutWrites() throws Exception {
      EditGroupPaneModel model = model("other", "other", "orgA");

      assertThrows(MessageException.class, () ->
         service.editGroup("Primary", new IdentityID("g", "orgA"), model, principal));

      verifyNoInteractions(identityService);
   }

   @Test
   void missingGroup_rejectedAsNotFound() throws Exception {
      IdentityID groupID = new IdentityID("g", "orgA");
      when(editProvider.getGroup(groupID)).thenReturn(null);

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.editGroup("Primary", groupID, model("g", "g", "orgA"), principal));

      assertEquals(Catalog.getCatalog().getString("em.security.groupNotFound", "g"),
                   thrown.getMessage());
      verifyNoInteractions(identityService);
   }

   @Test
   void bodyMatchesPath_renameWithinOrgProceeds() throws Exception {
      IdentityID groupID = new IdentityID("g", "orgA");
      FSGroup group = new FSGroup(groupID);
      group.setOrganization("orgA");
      when(editProvider.getGroup(groupID)).thenReturn(group);
      EditGroupPaneModel model = model("g", "g2", "orgA");

      service.editGroup("Primary", groupID, model, principal);

      verify(identityService).setIdentity(group, model, editProvider, principal);
      verify(identityService).setIdentityPermissions(
         eq(groupID), eq(new IdentityID("g2", "orgA")), eq(ResourceType.SECURITY_GROUP),
         eq(principal), eq(Collections.emptyList()), eq(""));
   }

   private static EditGroupPaneModel model(String oldName, String name, String org) {
      return EditGroupPaneModel.builder()
         .oldName(oldName)
         .name(name)
         .organization(org)
         .build();
   }

   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private EditableAuthenticationProvider editProvider;
   private SystemAdminService systemAdminService;
   private IdentityService identityService;
   private Principal principal;
   private UserTreeService service;
}
