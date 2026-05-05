/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
 * Test strategy
 *
 * Class type: behavioral-orchestration controller — RoleController has real in-controller
 * logic in createRole(), editRole(), and deleteIdentities().
 *
 * Coverage scope (6 cases in 3 groups):
 *
 * --- createRole() ---
 *
 *  [no permission]               checkPermission() returns false → null returned immediately
 *
 * --- deleteIdentities() ---
 *
 *  [includes default org]        model contains defaultOrg name → warning; service never called
 *  [no system admin after delete] hasSystemAdminAfterDelete() false → warning; service never called
 *  [clean state]                  all guards pass → delegates to identityService.deleteIdentities()
 *
 * --- editRole() ---
 *
 *  [invalid org]                 org null → InvalidOrgException before permission check
 *  [no permission]               permission check false → SecurityException
 *
 * Static singletons (OrganizationManager, Organization, Catalog, SUtil, Audit) are intercepted
 * with Mockito.mockStatic() using lenient() where possible.
 */

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.util.audit.*;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

   @Mock private SecurityProvider securityProvider;
   @Mock private IdentityService identityService;
   @Mock private UserTreeService userTreeService;
   @Mock private SecurityTreeServer securityTreeServer;
   @Mock private SystemAdminService systemAdminService;
   @Mock private IdentityThemeService themeService;
   @Mock private Catalog catalog;
   @Mock private Audit audit;
   @Mock private Principal principal;
   @Mock private HttpServletRequest httpRequest;
   @Mock private EditRolePaneModel roleModel;

   private RoleController controller;
   private OrganizationManager orgManager;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Organization> organizationStatic;
   private MockedStatic<Catalog> catalogStatic;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() {
      controller = new RoleController(securityProvider, identityService, userTreeService,
                                      securityTreeServer, systemAdminService, themeService);

      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      organizationStatic = mockStatic(Organization.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());
      auditStatic = mockStatic(Audit.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      catalogStatic.when(() -> Catalog.getCatalog(any(Principal.class))).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("warning-message");
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      organizationStatic.when(Organization::getDefaultOrganizationID).thenReturn("host-org");
      organizationStatic.when(Organization::getDefaultOrganizationName).thenReturn("host-org");
      organizationStatic.when(Organization::getSelfOrganizationName).thenReturn("self-org");
      lenient().when(securityProvider.getOrganization("host-org")).thenReturn(mock(Organization.class));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      organizationStatic.close();
      catalogStatic.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // createRole()
   // -------------------------------------------------------------------------

   // [no permission] checkPermission returns false → returns null immediately
   @Test
   void createRole_noPermission_returnsNull() {
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_ROLE),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(false);

      EditRolePaneModel result = controller.createRole(httpRequest, principal, "myProvider");

      assertNull(result);
      verify(securityProvider, never()).getAuthenticationProvider();
   }

   // -------------------------------------------------------------------------
   // deleteIdentities()
   // -------------------------------------------------------------------------

   // [includes default org] org name == default org name → warning returned; service not called
   @Test
   void deleteIdentities_includesDefaultOrg_returnsWarning() {
      IdentityID defaultOrgId = new IdentityID("host-org", "host-org");
      IdentityModel model = IdentityModel.builder()
         .identityID(defaultOrgId)
         .type(Identity.ORGANIZATION)
         .build();
      when(systemAdminService.createIdentity(any(IdentityID.class), anyInt()))
         .thenReturn(mock(inetsoft.uql.util.Identity.class));

      DeleteIdentitiesResponse result =
         controller.deleteIdentities(new IdentityModel[]{ model }, "myProvider", principal);

      assertFalse(result.warnings().isEmpty());
      verify(identityService, never()).deleteIdentities(any(), anyString(), any(Principal.class));
   }

   // [no system admin after delete] hasSystemAdminAfterDelete returns false → warning; service not called
   @Test
   void deleteIdentities_noSystemAdminAfterDelete_returnsWarning() {
      IdentityID roleId = new IdentityID("analyst", "host-org");
      IdentityModel model = IdentityModel.builder()
         .identityID(roleId)
         .type(Identity.ROLE)
         .build();
      when(systemAdminService.createIdentity(any(IdentityID.class), anyInt()))
         .thenReturn(mock(inetsoft.uql.util.Identity.class));
      when(systemAdminService.hasSystemAdminAfterDelete(any(Set.class))).thenReturn(false);

      DeleteIdentitiesResponse result =
         controller.deleteIdentities(new IdentityModel[]{ model }, "myProvider", principal);

      assertFalse(result.warnings().isEmpty());
      verify(identityService, never()).deleteIdentities(any(), anyString(), any(Principal.class));
   }

   // [clean state] all guards pass → delegates to identityService.deleteIdentities()
   @Test
   void deleteIdentities_cleanState_delegatesToService() {
      IdentityID roleId = new IdentityID("analyst", "host-org");
      IdentityModel model = IdentityModel.builder()
         .identityID(roleId)
         .type(Identity.ROLE)
         .build();
      IdentityModel[] models = { model };
      when(systemAdminService.createIdentity(any(IdentityID.class), anyInt()))
         .thenReturn(mock(inetsoft.uql.util.Identity.class));
      when(systemAdminService.hasSystemAdminAfterDelete(any(Set.class))).thenReturn(true);
      when(systemAdminService.hasOrgAdminAfterDelete(any(Set.class))).thenReturn(true);
      when(identityService.deleteIdentities(models, "myProvider", principal))
         .thenReturn(List.of());

      DeleteIdentitiesResponse result =
         controller.deleteIdentities(models, "myProvider", principal);

      assertTrue(result.warnings().isEmpty());
      verify(identityService).deleteIdentities(models, "myProvider", principal);
   }

   // -------------------------------------------------------------------------
   // editRole()
   // -------------------------------------------------------------------------

   // [invalid org] org null → InvalidOrgException thrown
   @Test
   void editRole_invalidOrg_throwsInvalidOrgException() {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.editRole(httpRequest, principal, roleModel, "myProvider"));
   }

   // [no permission] permission check false → SecurityException thrown
   @Test
   void editRole_noPermission_throwsSecurityException() throws Exception {
      // org is valid (set in setUp)
      when(roleModel.oldName()).thenReturn("analyst");
      when(roleModel.organization()).thenReturn("host-org");
      when(roleModel.name()).thenReturn("analyst"); // not "Roles" or "Organization Roles"
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_ROLE),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.editRole(httpRequest, principal, roleModel, "myProvider"));

      verify(userTreeService, never()).editRole(any(), anyString(), any(Principal.class));
   }
}
