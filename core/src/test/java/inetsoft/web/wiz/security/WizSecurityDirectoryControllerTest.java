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
package inetsoft.web.wiz.security;

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.SecurityProviderStatus;
import inetsoft.web.admin.security.user.GetIdentityNameResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Proves the new wiz-prefixed identity directory endpoints (bug 76118) return real data for a
 * wiz-flagged principal, and are gated by {@link inetsoft.web.admin.ai.AdminAiCallerGuard} the same
 * way the admin-chat endpoints are.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class WizSecurityDirectoryControllerTest {
   @Mock private AuthenticationProviderService service;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private Catalog catalog;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;

   private WizSecurityDirectoryController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new WizSecurityDirectoryController(service, securityEngine);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("invalid-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      // Bearer-authenticated request, matching how the wiz-services broker calls this controller.
      // AdminAiCallerGuardTest already covers the guard's own behaviour; the "without bearer
      // token" tests below just confirm this controller actually invokes it.
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      catalogStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   // -------------------------------------------------------------------------
   // bearer-token gate (CSRF backstop on the /api/wiz/** prefix)
   // -------------------------------------------------------------------------

   @Test
   void getCurrentProvider_throwsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      assertThrows(ResponseStatusException.class, () -> controller.getCurrentProvider(principal));
      verifyNoInteractions(service);
   }

   @Test
   void getIdentities_throwsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      assertThrows(ResponseStatusException.class,
         () -> controller.getIdentities("myProvider", Identity.USER, principal));
      verifyNoInteractions(service);
   }

   // -------------------------------------------------------------------------
   // getCurrentProvider() — delegates to the same service method the original EM endpoint uses
   // -------------------------------------------------------------------------

   @Test
   void getCurrentProvider_delegatesToService() {
      SecurityProviderStatus status = SecurityProviderStatus.builder()
         .name("myProvider").label("myProvider").cacheEnabled(false).cacheAge(0).loading(false)
         .build();
      when(service.getCurrentProvider(principal)).thenReturn(status);

      assertEquals(status, controller.getCurrentProvider(principal));
      verify(service).getCurrentProvider(principal);
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
   // getIdentities() — type routing, same as SecurityTreeController.getIdentities
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
}
