/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.content.dataspace;

/*
 * Test strategy
 *
 * DataSpaceShapeTreeController.getDataSpaceTree() must only list the shapes subtree for a
 * caller holding just the Presentation permission; any other path (including null/""/"/")
 * requires settings/content/data-space. Path scope is evaluated after normalisation, and a
 * path with "." / ".." segments is never treated as in-scope.
 *
 *   [in scope]       own-org dir, subfolder, global dir, single-tenant "portal/host-org/shapes",
 *                    "/portal/shapes" (normalised) -> allowed, service called
 *   [out of scope]   null, "", "/", "conf", other org dir, "portal/shapes/../conf"
 *                    -> SecurityException and no service call for a presentation-only principal;
 *                    allowed for a principal with data-space permission
 *   [init]           the "shapes" wrapper node is built only for allowed paths
 */

import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.web.admin.content.dataspace.model.DataSpaceTreeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class DataSpaceShapeTreeControllerTest {
   @Mock private DataSpaceContentSettingsService service;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal principal;

   private DataSpaceShapeTreeController controller;

   @BeforeEach
   void setUp() throws Exception {
      controller = new DataSpaceShapeTreeController(service, securityEngine);
      lenient().when(service.getTree(any()))
         .thenReturn(DataSpaceTreeModel.builder().nodes(List.of()).build());
   }

   private void setDataSpacePermission(boolean granted) throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.EM_COMPONENT,
                                          "settings/content/data-space", ResourceAction.ACCESS))
         .thenReturn(granted);
   }

   /** Call the endpoint as an org "orgA" caller; shapesDir is what ImageShapes reports. */
   private DataSpaceTreeModel call(String path, boolean init, String shapesDir) throws Exception {
      try(MockedStatic<ImageShapes> shapes =
             mockStatic(ImageShapes.class, CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> orgs = mockStatic(OrganizationManager.class))
      {
         shapes.when(ImageShapes::getShapesDirectory).thenReturn(shapesDir);
         OrganizationManager mgr = mock(OrganizationManager.class);
         orgs.when(OrganizationManager::getInstance).thenReturn(mgr);
         lenient().when(mgr.getCurrentOrgID()).thenReturn("orgA");

         return controller.getDataSpaceTree(path, init, principal);
      }
   }

   private void assertAllowedWithoutDataSpace(String path, String shapesDir) throws Exception {
      call(path, false, shapesDir);
      verify(service).getTree(path);
      verify(securityEngine, never()).checkPermission(any(Principal.class),
         eq(ResourceType.EM_COMPONENT), eq("settings/content/data-space"), any());
   }

   private void assertRejected(String path) throws Exception {
      setDataSpacePermission(false);
      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> call(path, false, "portal/orgA/shapes"));
      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> call(path, true, "portal/orgA/shapes"));
      verifyNoInteractions(service);
   }

   @Test
   void ownOrgShapesDirAllowed() throws Exception {
      assertAllowedWithoutDataSpace("portal/orgA/shapes", "portal/orgA/shapes");
   }

   @Test
   void ownOrgShapesSubfolderAllowed() throws Exception {
      assertAllowedWithoutDataSpace("portal/orgA/shapes/custom", "portal/orgA/shapes");
   }

   @Test
   void globalShapesAllowed() throws Exception {
      assertAllowedWithoutDataSpace("portal/shapes", "portal/orgA/shapes");
   }

   @Test
   void singleTenantHostOrgShapesAllowed() throws Exception {
      // single tenant: ImageShapes reports the global dir, the client sends the org dir
      assertAllowedWithoutDataSpace("portal/orgA/shapes", "portal/shapes");
   }

   @Test
   void leadingAndDoubleSlashNormalised() throws Exception {
      call("/portal/shapes", false, "portal/orgA/shapes");
      call("portal//shapes/sub", false, "portal/orgA/shapes");
      verify(service).getTree("/portal/shapes");
      verify(service).getTree("portal//shapes/sub");
   }

   @Test
   void nullPathRejected() throws Exception {
      assertRejected(null);
   }

   @Test
   void emptyPathRejected() throws Exception {
      assertRejected("");
   }

   @Test
   void rootPathRejected() throws Exception {
      assertRejected("/");
   }

   @Test
   void otherDataSpacePathRejected() throws Exception {
      assertRejected("conf");
   }

   @Test
   void otherOrgShapesRejected() throws Exception {
      assertRejected("portal/orgB/shapes");
   }

   @Test
   void siblingPrefixRejected() throws Exception {
      assertRejected("portal/shapes-extra");
   }

   @Test
   void dotDotTraversalRejected() throws Exception {
      assertRejected("portal/shapes/../conf");
   }

   @Test
   void dataSpaceHolderMayListOutOfScopePaths() throws Exception {
      setDataSpacePermission(true);

      for(String path : new String[] { null, "", "/", "conf", "portal/orgB/shapes",
                                       "portal/shapes/../conf" })
      {
         call(path, false, "portal/orgA/shapes");
         verify(service).getTree(path);
      }
   }

   @Test
   void initNodeBuiltOnlyForAllowedPath() throws Exception {
      DataSpaceTreeModel tree = call("portal/orgA/shapes", true, "portal/orgA/shapes");

      assertEquals(1, tree.nodes().size());
      assertEquals("shapes", tree.nodes().get(0).label());
      assertEquals("portal/orgA/shapes", tree.nodes().get(0).path());
   }
}
