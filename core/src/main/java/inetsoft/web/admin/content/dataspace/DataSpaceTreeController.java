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

import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.MessageException;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.dataspace.model.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
public class DataSpaceTreeController {
   @Autowired
   public DataSpaceTreeController(DataSpaceContentSettingsService dataSpaceContentSettingsService,
                                   SecurityEngine securityEngine)
   {
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
      this.securityEngine = securityEngine;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/data-space",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/content/data-space/tree")
   public DataSpaceTreeModel getDataSpaceTree(
      @DecodeParam(value = "path", required = false) String parentPath,
      @RequestBody List<String> expandPaths) throws Exception {
      return dataSpaceContentSettingsService.getTree(parentPath, expandPaths);
   }

   /**
    * Get the data space tree nodes
    */
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/data-space",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/content/data-space/tree")
   public DataSpaceTreeModel getDataSpaceTree(
      @DecodeParam(value = "path", required = false) String parentPath)
      throws Exception
   {
      return dataSpaceContentSettingsService.getTree(parentPath);
   }

   /**
    * Get the data space tree node for the specified path
    */
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/data-space",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/content/data-space/tree/node")
   public DataSpaceTreeNodeModel getDataSpaceTreeNode(
      @DecodeParam("path") String path) throws Exception
   {
      return dataSpaceContentSettingsService.getTreeNode(path);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/content/data-space",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/content/data-space/repair-files")
   public void repairDataSpaceFiles() {
      dataSpaceContentSettingsService.repairFiles();
   }

   /**
    * Delete the selected repository entry
    */
   @PostMapping("api/em/content/data-space/tree/delete")
   public void deleteNodes(@RequestBody DeleteDataSpaceTreeNodesRequest deleteRequest,
                           Principal principal, HttpServletRequest request)
      throws Exception
   {
      DataSpaceTreeNodeInfo[] nodes = deleteRequest.nodes();

      if(nodes == null) {
         return;
      }

      for(DataSpaceTreeNodeInfo node : nodes) {
         if(!checkDeletePermission(principal, node.path())) {
            throw new inetsoft.sree.security.SecurityException(
               "Unauthorized access to resource \"" + request.getRequestURI() + "\" by user " + principal);
         }
      }

      for(DataSpaceTreeNodeInfo node : nodes) {
         this.dataSpaceContentSettingsService.deleteDataSpaceNode(node.path(), node.folder());

         if(!node.folder()) {
            this.dataSpaceContentSettingsService.updateFolder(node.path());
         }
      }
   }

   private boolean checkDeletePermission(Principal principal, String path)
      throws inetsoft.sree.security.SecurityException
   {
      if(!securityEngine.checkPermission(principal, ResourceType.EM, "*", ResourceAction.ACCESS)) {
         return false;
      }

      String globalShapesDir = ImageShapes.getGlobalShapesDirectory();
      String orgShapesDir = ImageShapes.getShapesDirectory();

      if(path.equals(globalShapesDir) || path.startsWith(globalShapesDir + "/")) {
         return securityEngine.checkPermission(principal, ResourceType.EM_COMPONENT,
            "settings/presentation/settings", ResourceAction.ACCESS);
      }

      if(path.equals(orgShapesDir) || path.startsWith(orgShapesDir + "/")) {
         return securityEngine.checkPermission(principal, ResourceType.EM_COMPONENT,
               "settings/presentation/settings", ResourceAction.ACCESS) ||
            securityEngine.checkPermission(principal, ResourceType.EM_COMPONENT,
               "settings/presentation/org-settings", ResourceAction.ACCESS);
      }

      return securityEngine.checkPermission(principal, ResourceType.EM_COMPONENT,
         "settings/content/data-space", ResourceAction.ACCESS);
   }

   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
   private final SecurityEngine securityEngine;
}
