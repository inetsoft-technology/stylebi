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
package inetsoft.web.admin.content.repository;

/*
 * Test strategy
 *
 * Class type: pure-delegation controller — ResourcePermissionTreeController contains no
 * in-controller logic; the single method delegates entirely to SecurityTreeServer.
 *
 * Coverage scope:
 *   [named provider]    delegates with isPermissions=true, providerChanged=false, correct flags
 *   [null provider]     null provider is passed through unchanged
 *   [org-admin flags]   hideOrgAdminRole=true and isTimeRange=true are forwarded correctly
 */

import inetsoft.web.admin.security.user.SecurityTreeRootModel;
import inetsoft.web.admin.security.user.SecurityTreeServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ResourcePermissionTreeControllerTest {

   @Mock private SecurityTreeServer securityTreeServer;
   @Mock private SecurityTreeRootModel treeRootModel;
   @Mock private Principal principal;

   private ResourcePermissionTreeController controller;

   @BeforeEach
   void setUp() {
      controller = new ResourcePermissionTreeController(securityTreeServer);
   }

   // -------------------------------------------------------------------------
   // getResourceTree()
   // -------------------------------------------------------------------------

   // [named provider] provider specified → delegates with isPermissions=true, providerChanged=false
   @Test
   void getResourceTree_namedProvider_delegatesToSecurityTreeServer() {
      when(securityTreeServer.getSecurityTree("myProvider", principal, true, false, false, false))
         .thenReturn(treeRootModel);

      SecurityTreeRootModel result =
         controller.getResourceTree("myProvider", false, false, principal);

      assertSame(treeRootModel, result);
      verify(securityTreeServer).getSecurityTree("myProvider", principal, true, false, false, false);
   }

   // [null provider] null provider is passed through to SecurityTreeServer unchanged
   @Test
   void getResourceTree_nullProvider_passesNullToServer() {
      when(securityTreeServer.getSecurityTree(null, principal, true, false, false, false))
         .thenReturn(treeRootModel);

      SecurityTreeRootModel result =
         controller.getResourceTree(null, false, false, principal);

      assertSame(treeRootModel, result);
      verify(securityTreeServer).getSecurityTree(null, principal, true, false, false, false);
   }

   // [org-admin flags] hideOrgAdminRole=true and isTimeRange=true are forwarded to the server
   @Test
   void getResourceTree_withOrgAdminFlags_forwardsToServer() {
      when(securityTreeServer.getSecurityTree("myProvider", principal, true, false, true, true))
         .thenReturn(treeRootModel);

      SecurityTreeRootModel result =
         controller.getResourceTree("myProvider", true, true, principal);

      assertSame(treeRootModel, result);
      verify(securityTreeServer).getSecurityTree("myProvider", principal, true, false, true, true);
   }
}
