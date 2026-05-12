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
 * ContentRepositoryTreeController has real in-controller logic in three methods:
 *
 *   POST /api/em/content/repository/tree (getRepositoryTree with body)
 *     — validates current org via OrganizationManager + SecurityProvider; throws
 *       InvalidOrgException when null; delegates to treeService.getRootNodes() on success.
 *
 *   POST /api/em/content/repository/private/tree (getRepositoryPrivateTree)
 *     — iterates users array; branches on Tool.MY_DASHBOARD vs SUtil.MY_DASHBOARD;
 *       calls treeService.getUserReports() or getUserDashboardNode() accordingly.
 *
 *   GET /api/em/content/repository/tree (getRepositoryTree with path+owner)
 *     — parses owner key to IdentityID; fetches registry; branches on path value.
 *
 * Pure-delegation methods (no branch logic):
 *   getLicensedComponents  → treeService.getLicensedComponents()
 *   searchRepositoryTree   → treeService.searchNodes(principal, filter)
 *
 * Excluded: isSiteAdmin (calls OrganizationManager.isSiteAdmin which needs full security
 * context); @Secured/@RequiredPermission annotations (Spring Security; E2E coverage).
 *
 * Static singletons (OrganizationManager, Catalog) are intercepted with
 * mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 */

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.util.Tool;
import inetsoft.util.data.CommonKVModel;
import inetsoft.web.admin.content.repository.model.LicensedComponents;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ContentRepositoryTreeControllerTest {

   @Mock private ContentRepositoryTreeService treeService;
   @Mock private SecurityEngine securityEngine;
   @Mock private RepletRegistryManager repletRegistryManager;
   @Mock private SecurityProvider securityProvider;
   @Mock private Organization organization;
   @Mock private Catalog catalog;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;
   @Mock private RepletRegistry registry;
   @Mock private ContentRepositoryTreeNode treeNode;
   @Mock private LicensedComponents licensedComponents;

   private ContentRepositoryTreeController controller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() throws Exception {
      controller = new ContentRepositoryTreeController(
         treeService, securityEngine, repletRegistryManager);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.getOrganization("host-org")).thenReturn(organization);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("msg");

      lenient().when(repletRegistryManager.getRegistry(any(IdentityID.class))).thenReturn(registry);
      lenient().when(treeNode.children()).thenReturn(Collections.emptyList());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // POST /api/em/content/repository/tree — getRepositoryTree(List, Principal)
   // -------------------------------------------------------------------------

   // [invalid org] org lookup returns null → InvalidOrgException; treeService never called
   @Test
   void getRepositoryTree_invalidOrg_throwsInvalidOrgException() throws Exception {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.getRepositoryTree(Collections.emptyList(), principal));

      verify(treeService, never()).getRootNodes(any(), any());
   }

   // [valid org] valid org → treeService.getRootNodes() called and result wrapped in model
   @Test
   void getRepositoryTree_validOrg_delegatesToService() throws Exception {
      List<String> usersToLoad = List.of("user1");
      List<ContentRepositoryTreeNode> nodes = List.of(treeNode);
      when(treeService.getRootNodes(principal, usersToLoad)).thenReturn(nodes);

      ContentRepositoryTreeModel result = controller.getRepositoryTree(usersToLoad, principal);

      assertSame(nodes, result.getNodes());
      verify(treeService).getRootNodes(principal, usersToLoad);
   }

   // -------------------------------------------------------------------------
   // POST /api/em/content/repository/private/tree — getRepositoryPrivateTree
   // -------------------------------------------------------------------------

   // [Tool.MY_DASHBOARD path] "My Dashboards" → treeService.getUserReports() called
   @Test
   void getRepositoryPrivateTree_myDashboardPath_callsGetUserReports() throws Exception {
      String key = new IdentityID("alice", "org").convertToKey();
      String path = Tool.MY_DASHBOARD;
      CommonKVModel<String, String>[] users =
         new CommonKVModel[]{ new CommonKVModel<>(key, path) };

      when(treeService.getUserReports(any(IdentityID.class), eq(registry), eq(principal)))
         .thenReturn(treeNode);

      controller.getRepositoryPrivateTree(users, principal);

      verify(treeService).getUserReports(any(IdentityID.class), eq(registry), eq(principal));
      verify(treeService, never()).getUserDashboardNode(any(), any(), any());
   }

   // [SUtil.MY_DASHBOARD path] "My Portal Dashboards" → treeService.getUserDashboardNode() called
   @Test
   void getRepositoryPrivateTree_sutiMyDashboardPath_callsGetUserDashboardNode() throws Exception {
      String key = new IdentityID("alice", "org").convertToKey();
      String path = SUtil.MY_DASHBOARD;
      CommonKVModel<String, String>[] users =
         new CommonKVModel[]{ new CommonKVModel<>(key, path) };

      when(treeService.getUserDashboardNode(any(IdentityID.class), eq(registry), eq(principal)))
         .thenReturn(treeNode);

      controller.getRepositoryPrivateTree(users, principal);

      verify(treeService).getUserDashboardNode(any(IdentityID.class), eq(registry), eq(principal));
      verify(treeService, never()).getUserReports(any(), any(), any());
   }

   // -------------------------------------------------------------------------
   // GET /api/em/content/repository/tree — getRepositoryTree(path, owner, Principal)
   // -------------------------------------------------------------------------

   // [Tool.MY_DASHBOARD path] GET variant with "My Dashboards" → getUserReports() called
   @Test
   void getRepositoryTreeNode_myDashboardPath_callsGetUserReports() throws Exception {
      String owner = new IdentityID("alice", "org").convertToKey();
      String path = Tool.MY_DASHBOARD;

      when(treeService.getUserReports(any(IdentityID.class), eq(registry), eq(principal)))
         .thenReturn(treeNode);

      controller.getRepositoryTree(path, owner, principal);

      verify(treeService).getUserReports(any(IdentityID.class), eq(registry), eq(principal));
      verify(treeService, never()).getUserDashboardNode(any(), any(), any());
   }

   // -------------------------------------------------------------------------
   // GET /api/em/content/repository/tree/licensed — getLicensedComponents
   // -------------------------------------------------------------------------

   // [pure delegation] delegates entirely to treeService.getLicensedComponents()
   @Test
   void getLicensedComponents_delegatesToService() {
      when(treeService.getLicensedComponents()).thenReturn(licensedComponents);

      LicensedComponents result = controller.getLicensedComponents(principal);

      assertSame(licensedComponents, result);
      verify(treeService).getLicensedComponents();
   }

   // -------------------------------------------------------------------------
   // GET /api/em/content/repository/tree/search — searchRepositoryTree
   // -------------------------------------------------------------------------

   // [pure delegation] delegates to treeService.searchNodes(principal, filter)
   @Test
   void searchRepositoryTree_delegatesToService() throws Exception {
      String filter = "dash";
      List<ContentRepositoryTreeNode> nodes = List.of(treeNode);
      when(treeService.searchNodes(principal, filter)).thenReturn(nodes);

      ContentRepositoryTreeModel result = controller.searchRepositoryTree(filter, principal);

      assertSame(nodes, result.getNodes());
      verify(treeService).searchNodes(principal, filter);
   }
}
