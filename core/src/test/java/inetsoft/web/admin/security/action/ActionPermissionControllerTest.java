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
package inetsoft.web.admin.security.action;

/*
 * Test strategy
 *
 * ActionPermissionController orchestrates four endpoints:
 *   getActionTree       — pure delegation to ActionPermissionService
 *   getPermissions      — validates org, refreshes actions cache, delegates to ResourcePermissionService
 *   setPermissions      — validates org, saves via ResourcePermissionService, then re-fetches permissions
 *   validateIdentities  — pure delegation to ResourcePermissionService
 *
 * Coverage scope:
 *   [getActionTree]                      delegates to actionService.getActionTree(principal)
 *   [getPermissions: invalid org]        org null → InvalidOrgException; service never called
 *   [getPermissions: valid]              valid org → service model returned
 *   [setPermissions: invalid org]        org null → InvalidOrgException; setResourcePermissions never called
 *   [setPermissions: valid]              saves via setResourcePermissions, then returns re-fetched model
 *   [validateIdentities]                 delegates to permissionService.findMissingIdentities()
 *
 * getPermissions() calls loadActions() internally, which calls actionService.getActionTree().
 * The root node's children are traversed to populate the actions cache; using an empty children
 * list keeps setup minimal while allowing the service delegation to proceed.
 *
 * Static singletons (OrganizationManager, ThreadContext, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 * principal.getName() returns a valid IdentityID key so getIdentityIDFromKey() can run for real.
 */

import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.security.ResourcePermissionTableModel;
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
class ActionPermissionControllerTest {

   @Mock private ActionPermissionService actionService;
   @Mock private ResourcePermissionService permissionService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private Organization organization;
   @Mock private ActionTreeNode rootNode;
   @Mock private ResourcePermissionModel permissionModel;
   @Mock private Catalog catalog;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;

   private ActionPermissionController controller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ThreadContext> threadContextStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new ActionPermissionController(actionService, permissionService, securityEngine);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      threadContextStatic = mockStatic(ThreadContext.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.getOrganization("host-org")).thenReturn(organization);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("translated-label");

      // loadActions() calls actionService.getActionTree(ThreadContext.getContextPrincipal())
      // and traverses root.children(); an empty children list keeps the actions cache empty
      // without causing NPE. actions.get(resource) then returns null, which getTableModel accepts.
      threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(null);
      lenient().when(actionService.getActionTree(any())).thenReturn(rootNode);
      lenient().when(rootNode.children()).thenReturn(List.of());

      // getPermissions() checks roles to determine org-admin status
      lenient().when(securityProvider.getRoles(any(IdentityID.class))).thenReturn(new IdentityID[0]);
      lenient().when(securityProvider.getAllRoles(any(IdentityID[].class))).thenReturn(new IdentityID[0]);

      // principal.getName() must return a valid IdentityID key for getIdentityIDFromKey()
      lenient().when(principal.getName())
         .thenReturn(new IdentityID("alice", "host-org").convertToKey());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      threadContextStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // getActionTree()
   // -------------------------------------------------------------------------

   // [delegation] delegates to actionService.getActionTree(principal) and returns result unchanged
   @Test
   void getActionTree_delegatesToActionService() {
      when(actionService.getActionTree(principal)).thenReturn(rootNode);

      ActionTreeNode result = controller.getActionTree(principal);

      assertSame(rootNode, result);
      verify(actionService).getActionTree(principal);
   }

   // -------------------------------------------------------------------------
   // getPermissions()
   // -------------------------------------------------------------------------

   // [invalid org] org lookup returns null → InvalidOrgException; service never called
   @Test
   void getPermissions_invalidOrg_throwsInvalidOrgException() {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.getPermissions(
            ResourceType.VIEWSHEET.name(), "*", true, principal));

      verify(permissionService, never()).getTableModel(
         anyString(), any(), any(), anyString(), any(Principal.class));
   }

   // [valid] valid org → cache refreshed, service model returned unchanged
   @Test
   void getPermissions_validResource_returnsPermissionModel() {
      when(permissionService.getTableModel(
         eq("*"), eq(ResourceType.VIEWSHEET), isNull(), anyString(), eq(principal)))
         .thenReturn(permissionModel);

      ResourcePermissionModel result =
         controller.getPermissions(ResourceType.VIEWSHEET.name(), "*", true, principal);

      assertSame(permissionModel, result);
      verify(permissionService).getTableModel(
         eq("*"), eq(ResourceType.VIEWSHEET), isNull(), anyString(), eq(principal));
   }

   // -------------------------------------------------------------------------
   // setPermissions()
   // -------------------------------------------------------------------------

   // [invalid org] org lookup returns null → InvalidOrgException; setResourcePermissions never called
   @Test
   void setPermissions_invalidOrg_throwsInvalidOrgException() throws Exception {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.setPermissions(
            ResourceType.VIEWSHEET.name(), "*", true, permissionModel, principal));

      verify(permissionService, never()).setResourcePermissions(
         anyString(), any(), anyString(), any(), any(Principal.class));
   }

   // [valid] saves via setResourcePermissions, then re-fetches and returns updated permissions
   @Test
   void setPermissions_valid_savesAndReturnsUpdatedModel() throws Exception {
      ResourcePermissionModel updatedModel = mock(ResourcePermissionModel.class);
      when(permissionService.getTableModel(
         eq("*"), eq(ResourceType.VIEWSHEET), isNull(), anyString(), eq(principal)))
         .thenReturn(updatedModel);

      ResourcePermissionModel result =
         controller.setPermissions(
            ResourceType.VIEWSHEET.name(), "*", true, permissionModel, principal);

      verify(permissionService).setResourcePermissions(
         eq("*"), eq(ResourceType.VIEWSHEET), eq("VIEWSHEET: *"),
         eq(permissionModel), eq(principal));
      assertSame(updatedModel, result);
   }

   // -------------------------------------------------------------------------
   // validateIdentities()
   // -------------------------------------------------------------------------

   // [delegation] delegates to permissionService.findMissingIdentities() and returns result
   @Test
   void validateIdentities_delegatesToService() {
      List<ResourcePermissionTableModel> identities = List.of(mock(ResourcePermissionTableModel.class));
      List<ResourcePermissionTableModel> missing = List.of();
      when(permissionService.findMissingIdentities(identities)).thenReturn(missing);

      List<ResourcePermissionTableModel> result = controller.validateIdentities(identities);

      assertSame(missing, result);
      verify(permissionService).findMissingIdentities(identities);
   }
}
