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
 * Class type: thin orchestration controller — SecurityTreeController has real in-controller
 * logic in getIdentities(): validates the current org, then routes to one of four
 * filtered getter methods on AuthenticationProviderService based on the identity type.
 *
 * Coverage scope:
 *   [invalid org]          org null → InvalidOrgException thrown before routing
 *   [USER type]            type == Identity.USER → getFilteredUsers() called
 *   [GROUP type]           type == Identity.GROUP → getFilteredGroups() called
 *   [ROLE type]            type == Identity.ROLE → getFilteredRoles() called
 *   [ORGANIZATION type]    type == Identity.ORGANIZATION → getFilteredOrganizations() called
 *
 * Static singletons (OrganizationManager, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient().
 */

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.web.admin.security.AuthenticationProviderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class SecurityTreeControllerTest {

   @Mock private AuthenticationProviderService service;
   @Mock private IdentityThemeService themeService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private Catalog catalog;
   @Mock private Principal principal;

   private SecurityTreeController controller;
   private OrganizationManager orgManager;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new SecurityTreeController(service, themeService, securityEngine);

      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("invalid-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // getIdentities() — org guard
   // -------------------------------------------------------------------------

   @Test
   void getIdentities_invalidOrg_throwsInvalidOrgException() {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.getIdentities("myProvider", Identity.USER, principal));
   }

   // -------------------------------------------------------------------------
   // getIdentities() — type routing
   // -------------------------------------------------------------------------

   @Test
   void getIdentities_userType_delegatesToFilteredUsers() {
      when(securityProvider.getOrganization("host-org")).thenReturn(mock(Organization.class));
      IdentityID uid = new IdentityID("alice", "host-org");
      when(service.getFilteredUsers("myProvider", principal)).thenReturn(List.of(uid));

      GetIdentityNameResponse result = controller.getIdentities("myProvider", Identity.USER, principal);

      assertEquals(1, result.identityNames().length);
      assertEquals(uid, result.identityNames()[0]);
      verify(service).getFilteredUsers("myProvider", principal);
      verify(service, never()).getFilteredGroups(anyString(), any(Principal.class));
   }

   @Test
   void getIdentities_groupType_delegatesToFilteredGroups() {
      when(securityProvider.getOrganization("host-org")).thenReturn(mock(Organization.class));
      IdentityID gid = new IdentityID("developers", "host-org");
      when(service.getFilteredGroups("myProvider", principal)).thenReturn(List.of(gid));

      GetIdentityNameResponse result = controller.getIdentities("myProvider", Identity.GROUP, principal);

      assertEquals(1, result.identityNames().length);
      assertEquals(gid, result.identityNames()[0]);
      verify(service).getFilteredGroups("myProvider", principal);
   }

   @Test
   void getIdentities_roleType_delegatesToFilteredRoles() {
      when(securityProvider.getOrganization("host-org")).thenReturn(mock(Organization.class));
      IdentityID rid = new IdentityID("analyst", "host-org");
      when(service.getFilteredRoles("myProvider", principal)).thenReturn(List.of(rid));

      GetIdentityNameResponse result = controller.getIdentities("myProvider", Identity.ROLE, principal);

      assertEquals(1, result.identityNames().length);
      assertEquals(rid, result.identityNames()[0]);
      verify(service).getFilteredRoles("myProvider", principal);
   }

   @Test
   void getIdentities_organizationType_delegatesToFilteredOrganizations() {
      when(securityProvider.getOrganization("host-org")).thenReturn(mock(Organization.class));
      IdentityID oid = new IdentityID("acme", "host-org");
      when(service.getFilteredOrganizations("myProvider", principal)).thenReturn(List.of(oid));

      GetIdentityNameResponse result =
         controller.getIdentities("myProvider", Identity.ORGANIZATION, principal);

      assertEquals(1, result.identityNames().length);
      assertEquals(oid, result.identityNames()[0]);
      verify(service).getFilteredOrganizations("myProvider", principal);
   }
}
