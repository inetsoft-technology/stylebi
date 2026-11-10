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
 * Bug #80956: the EM role rename moves the role's own permission key, and a failed move does not
 * skip the rename migration.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceRoleRenamePermissionTest {
   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      OrganizationManager orgManager =
         mock(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider())
         .thenReturn(mock(SecurityProvider.class, withSettings().lenient()));
      SystemAdminService systemAdminService =
         mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);
      identityService = mock(IdentityService.class);
      principal = mock(Principal.class, withSettings().lenient());

      service = spy(new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine,
         mock(IdentityThemeService.class), null, null, null, null, null, null, null, null, null,
         null, null));
      // the VPM migration is covered by UserTreeServiceRoleRenameVpmRegistryTest
      doNothing().when(service).migrateRoleRename(any(), any());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      sUtilStatic.close();
   }

   @Test
   void rename_movesPermissionThenMigrates() throws Exception {
      IdentityID oldID = new IdentityID("r", ORG);
      IdentityID newID = new IdentityID("r2", ORG);
      when(provider.getRole(oldID)).thenReturn(new FSRole(oldID));

      service.editRole(model("r", "r2", ORG), "Primary", principal);

      InOrder order = inOrder(identityService, service);
      order.verify(identityService).setIdentity(any(), any(), any(), any());
      order.verify(identityService)
         .moveIdentityPermission(ResourceType.SECURITY_ROLE, oldID, newID);
      order.verify(service).migrateRoleRename(oldID, newID);
   }

   @Test
   void globalRole_movesWithNullOrg() throws Exception {
      IdentityID oldID = new IdentityID("g", null);
      when(provider.getRole(oldID)).thenReturn(new FSRole(oldID));

      service.editRole(model("g", "g2", null), "Primary", principal);

      verify(identityService).moveIdentityPermission(
         ResourceType.SECURITY_ROLE, oldID, new IdentityID("g2", null));
   }

   @Test
   void moveFails_migrationStillRunsThenFailureReported() throws Exception {
      IdentityID oldID = new IdentityID("r", ORG);
      when(provider.getRole(oldID)).thenReturn(new FSRole(oldID));
      IllegalStateException failure = new IllegalStateException("write failed");
      doThrow(failure).when(identityService).moveIdentityPermission(any(), any(), any());

      IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
         service.editRole(model("r", "r2", ORG), "Primary", principal));

      assertSame(failure, thrown);
      verify(service).migrateRoleRename(oldID, new IdentityID("r2", ORG));
   }

   private static EditRolePaneModel model(String oldName, String name, String org) {
      return EditRolePaneModel.builder().oldName(oldName).name(name).isSysAdmin(false)
         .isOrgAdmin(false).organization(org).build();
   }

   private static final String ORG = "orgA";
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SUtil> sUtilStatic;
   private EditableAuthenticationProvider provider;
   private IdentityService identityService;
   private Principal principal;
   private UserTreeService service;
}
