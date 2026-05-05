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
 * Class type: thin orchestration controller — UserController has real in-controller logic
 * in editUser(): checks that the current org is valid before delegating, and handles
 * session ticket warnings post-edit.
 *
 * Coverage scope:
 *   [createUser]                   pure delegation to userTreeService.createUser()
 *   [editUser: invalid org]        org null → InvalidOrgException thrown before service is called
 *   [editUser: valid org]          valid org → delegates to service; warning logged, not thrown
 *
 * Static singletons (OrganizationManager, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 */

import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

   @Mock private UserTreeService userTreeService;
   @Mock private inetsoft.web.admin.security.IdentityService identityService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private EditUserPaneModel userModel;
   @Mock private Catalog catalog;
   @Mock private Principal principal;
   @Mock private HttpServletRequest httpRequest;
   @Mock private HttpSession httpSession;

   private UserController controller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Catalog> catalogStatic;
   private OrganizationManager orgManager;

   @BeforeEach
   void setUp() {
      controller = new UserController(userTreeService, identityService, securityEngine);

      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("invalid-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(httpRequest.getSession(true)).thenReturn(httpSession);
      lenient().when(httpSession.getAttribute(anyString())).thenReturn(null);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // createUser()
   // -------------------------------------------------------------------------

   @Test
   void createUser_delegatesToService() {
      EditUserPaneModel expected = mock(EditUserPaneModel.class);
      CreateEntityRequest request = CreateEntityRequest.builder().parentGroup(null).build();
      when(userTreeService.createUser("myProvider", null, principal)).thenReturn(expected);

      EditUserPaneModel result = controller.createUser(principal, "myProvider", request);

      assertSame(expected, result);
      verify(userTreeService).createUser("myProvider", null, principal);
   }

   // -------------------------------------------------------------------------
   // editUser()
   // -------------------------------------------------------------------------

   // [invalid org] org lookup returns null → InvalidOrgException before service is called
   @Test
   void editUser_invalidOrg_throwsInvalidOrgException() throws Exception {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.editUser(httpRequest, userModel, "myProvider", principal));

      verify(userTreeService, never()).editUser(any(), anyString(), any(Principal.class));
   }

   // [valid org] org lookup succeeds → delegates to service; no exception
   @Test
   void editUser_validOrg_delegatesToService() throws Exception {
      when(securityProvider.getOrganization("host-org")).thenReturn(mock(Organization.class));
      lenient().when(userModel.oldName()).thenReturn("alice");
      lenient().when(identityService.getTimeOutWarning(any(), anyString())).thenReturn(null);

      controller.editUser(httpRequest, userModel, "myProvider", principal);

      verify(userTreeService).editUser(userModel, "myProvider", principal);
   }
}
