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
 *   [getPermissions: valid]              node in caller's tree → model built from node.actions()
 *   [getPermissions: missing node]       (type, path) not in caller's tree → SecurityException
 *   [setPermissions: invalid org]        org null → InvalidOrgException; setResourcePermissions never called
 *   [setPermissions: valid]              saves via setResourcePermissions, then returns re-fetched model
 *   [setPermissions: Bug #77251/#77252]  target must be a node (leaf or folder) of the request
 *                                        principal's action tree, and displayActions must be a subset
 *                                        of node.actions(); per-row stored actions are not rejected
 *   [validateIdentities]                 delegates to permissionService.findMissingIdentities()
 *
 * The action tree is built from real ActionTreeNode values (only ActionPermissionService is
 * mocked): an org admin's tree (no org admin exclusions, no SECURITY_* nodes, as
 * ActionPermissionService.getActionTree() produces for any non-sysadmin in multi-tenant mode)
 * and a site admin's tree that additionally contains the excluded settings/general node.
 *
 * Static singletons (OrganizationManager, ThreadContext, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 * principal.getName() returns a valid IdentityID key so getIdentityIDFromKey() can run for real.
 */

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
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
import java.util.EnumSet;
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

      threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(null);
      lenient().when(actionService.getActionTree(principal)).thenReturn(orgAdminTree());

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
      ActionTreeNode rootNode = orgAdminTree();
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

   // [valid] node of the caller's tree → model built from that node's actions
   @Test
   void getPermissions_validResource_returnsPermissionModel() {
      when(permissionService.getTableModel(
         eq("*"), eq(ResourceType.VIEWSHEET), eq(EnumSet.of(ResourceAction.READ)), anyString(),
         eq(principal)))
         .thenReturn(permissionModel);

      ResourcePermissionModel result =
         controller.getPermissions(ResourceType.VIEWSHEET.name(), "*", true, principal);

      assertSame(permissionModel, result);
      verify(actionService).getActionTree(principal);
   }

   // [Bug #77251] a node missing from the caller's tree is refused before anything is read;
   // this used to reach getTableModel() with null actions (NPE)
   @Test
   void getPermissions_nodeNotInCallerTree_throwsSecurityException() {
      assertThrows(java.lang.SecurityException.class,
         () -> controller.getPermissions(
            ResourceType.EM_COMPONENT.name(), SETTINGS_GENERAL, true, principal));

      verify(permissionService, never()).getTableModel(
         anyString(), any(), any(), anyString(), any(Principal.class));
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
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.READ));
      ResourcePermissionModel updatedModel = mock(ResourcePermissionModel.class);
      when(permissionService.getTableModel(
         eq("*"), eq(ResourceType.VIEWSHEET), eq(EnumSet.of(ResourceAction.READ)), anyString(),
         eq(principal)))
         .thenReturn(updatedModel);

      ResourcePermissionModel result =
         controller.setPermissions(
            ResourceType.VIEWSHEET.name(), "*", true, body, principal);

      verify(permissionService).setResourcePermissions(
         eq("*"), eq(ResourceType.VIEWSHEET), eq("VIEWSHEET: *"),
         eq(body), eq(principal));
      assertSame(updatedModel, result);
   }

   // [Bug #77251] positive control: an org admin granting ACCESS on a node visible in their
   // tree still saves, and the response is built from the node's own actions
   @Test
   void setPermissions_orgAdminAccessOnVisibleLeaf_saves() throws Exception {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ACCESS),
         row("bob", EnumSet.of(ResourceAction.ACCESS)));

      controller.setPermissions(
         ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, body, principal);

      verify(permissionService).setResourcePermissions(
         eq(SETTINGS_USERS), eq(ResourceType.EM_COMPONENT), anyString(), eq(body), eq(principal));
      verify(permissionService).getTableModel(
         eq(SETTINGS_USERS), eq(ResourceType.EM_COMPONENT), eq(EnumSet.of(ResourceAction.ACCESS)),
         anyString(), eq(principal));
   }

   // [Bug #77251] folder nodes are editable on the actions page, so they must match too
   @Test
   void setPermissions_folderNode_saves() throws Exception {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ACCESS),
         row("bob", EnumSet.of(ResourceAction.ACCESS)));

      controller.setPermissions(
         ResourceType.EM_COMPONENT.name(), "settings", true, body, principal);

      verify(permissionService).setResourcePermissions(
         eq("settings"), eq(ResourceType.EM_COMPONENT), anyString(), eq(body), eq(principal));
   }

   // [Bug #77251] GET rows carry every stored action (e.g. an ADMIN planted before the fix) and
   // the UI echoes them back; only displayActions is written, so such rows must not block a save
   @Test
   void setPermissions_rowWithStoredActionOutsideNodeActions_stillSaves() throws Exception {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ACCESS),
         row("bob", EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN)));

      controller.setPermissions(
         ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, body, principal);

      verify(permissionService).setResourcePermissions(
         eq(SETTINGS_USERS), eq(ResourceType.EM_COMPONENT), anyString(), eq(body), eq(principal));
   }

   // [Bug #77251] reporter's case: an org admin writing an ADMIN self grant on the excluded
   // settings/general component is refused, because that node is not in their tree
   @Test
   void setPermissions_orgAdminAdminOnExcludedSettingsGeneral_refused() {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ADMIN),
         row("alice", EnumSet.of(ResourceAction.ADMIN)));

      assertThrows(java.lang.SecurityException.class,
         () -> controller.setPermissions(
            ResourceType.EM_COMPONENT.name(), SETTINGS_GENERAL, true, body, principal));

      verifyNoWrite();
   }

   // [Bug #77252] a delegate (ACCESS on settings/security/actions only) writing a
   // SECURITY_ORGANIZATION ADMIN self grant is refused: SECURITY_* is never an action tree node
   @Test
   void setPermissions_delegateAdminOnSecurityOrganization_refused() {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ADMIN),
         row("alice", EnumSet.of(ResourceAction.ADMIN)));
      String orgKey = new IdentityID("host-org", "host-org").convertToKey();

      assertThrows(java.lang.SecurityException.class,
         () -> controller.setPermissions(
            ResourceType.SECURITY_ORGANIZATION.name(), orgKey, true, body, principal));

      verifyNoWrite();
   }

   // [Bug #77252] ADMIN is never among a node's actions, so it cannot be written on a visible
   // EM component either
   @Test
   void setPermissions_adminOnVisibleLeaf_refused() {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN),
         row("alice", EnumSet.of(ResourceAction.ADMIN)));

      assertThrows(java.lang.SecurityException.class,
         () -> controller.setPermissions(
            ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, body, principal));

      verifyNoWrite();
   }

   // [Bug #77251] the type is matched as well as the path: CHART_TYPE_FOLDER:Bar is in the tree,
   // EM_COMPONENT:Bar is not
   @Test
   void setPermissions_samePathOtherType_refused() {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.READ));

      assertThrows(java.lang.SecurityException.class,
         () -> controller.setPermissions(
            ResourceType.EM_COMPONENT.name(), "Bar", true, body, principal));

      verifyNoWrite();
   }

   // [Bug #77251] a model without displayActions (not producible by the immutable builder, but
   // possible from a hand-crafted body) is refused rather than written
   @Test
   void setPermissions_nullDisplayActions_refused() {
      ResourcePermissionModel body = mock(ResourcePermissionModel.class);
      when(body.displayActions()).thenReturn(null);

      assertThrows(java.lang.SecurityException.class,
         () -> controller.setPermissions(
            ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, body, principal));

      verifyNoWrite();
   }

   // [Bug #77251] the path is matched exactly: a trailing-slash variant of a visible node is not
   // a node of the tree
   @Test
   void setPermissions_trailingSlashPath_refused() {
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ACCESS),
         row("alice", EnumSet.of(ResourceAction.ACCESS)));

      assertThrows(java.lang.SecurityException.class,
         () -> controller.setPermissions(
            ResourceType.EM_COMPONENT.name(), SETTINGS_USERS + "/", true, body, principal));

      verifyNoWrite();
   }

   // [Bug #77251] positive control: a site admin, whose tree contains settings/general, still
   // edits it
   @Test
   void setPermissions_siteAdminAccessOnSettingsGeneral_saves() throws Exception {
      when(actionService.getActionTree(principal)).thenReturn(siteAdminTree());
      ResourcePermissionModel body = model(EnumSet.of(ResourceAction.ACCESS),
         row("bob", EnumSet.of(ResourceAction.ACCESS)));

      controller.setPermissions(
         ResourceType.EM_COMPONENT.name(), SETTINGS_GENERAL, true, body, principal);

      verify(permissionService).setResourcePermissions(
         eq(SETTINGS_GENERAL), eq(ResourceType.EM_COMPONENT), anyString(), eq(body), eq(principal));
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

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private static final String SETTINGS_GENERAL = "settings/general";
   private static final String SETTINGS_USERS = "settings/security/users";

   private void verifyNoWrite() {
      try {
         verify(permissionService, never()).setResourcePermissions(
            anyString(), any(), anyString(), any(), any(Principal.class));
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static ActionTreeNode orgAdminTree() {
      return tree(false);
   }

   private static ActionTreeNode siteAdminTree() {
      return tree(true);
   }

   private static ActionTreeNode tree(boolean siteAdmin) {
      ActionTreeNode.Builder settings = ActionTreeNode.builder()
         .resource("settings").label("Settings").folder(true)
         .type(ResourceType.EM_COMPONENT).actions(EnumSet.of(ResourceAction.ACCESS))
         .addChildren(leaf(ResourceType.EM_COMPONENT, SETTINGS_USERS, ResourceAction.ACCESS));

      if(siteAdmin) {
         settings.addChildren(leaf(ResourceType.EM_COMPONENT, SETTINGS_GENERAL, ResourceAction.ACCESS));
      }

      ActionTreeNode em = ActionTreeNode.builder()
         .resource("*").label("EM").folder(true)
         .type(ResourceType.EM).actions(EnumSet.of(ResourceAction.ACCESS))
         .addChildren(settings.build())
         .build();
      ActionTreeNode chart = ActionTreeNode.builder()
         .resource("Bar").label("Bar").folder(true)
         .type(ResourceType.CHART_TYPE_FOLDER).actions(EnumSet.of(ResourceAction.READ))
         .build();
      return ActionTreeNode.builder()
         .label("").folder(true).actions(EnumSet.noneOf(ResourceAction.class))
         .addChildren(em, chart, leaf(ResourceType.VIEWSHEET, "*", ResourceAction.READ))
         .build();
   }

   private static ActionTreeNode leaf(ResourceType type, String resource, ResourceAction action) {
      return ActionTreeNode.builder()
         .resource(resource).label(resource).folder(false)
         .type(type).actions(EnumSet.of(action))
         .build();
   }

   private static ResourcePermissionModel model(EnumSet<ResourceAction> displayActions,
                                                ResourcePermissionTableModel... rows)
   {
      return ResourcePermissionModel.builder()
         .permissions(List.of(rows))
         .displayActions(displayActions)
         .hasOrgEdited(true)
         .securityEnabled(true)
         .requiresBoth(false)
         .derivePermissionLabel("Use Parent Permissions")
         .grantReadToAllVisible(false)
         .build();
   }

   private static ResourcePermissionTableModel row(String user, EnumSet<ResourceAction> actions) {
      return ResourcePermissionTableModel.builder()
         .identityID(new IdentityID(user, "host-org"))
         .type(Identity.Type.USER)
         .actions(actions)
         .build();
   }
}
