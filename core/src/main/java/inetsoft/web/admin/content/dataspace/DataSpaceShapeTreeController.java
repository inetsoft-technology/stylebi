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
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.dataspace.model.DataSpaceTreeModel;
import inetsoft.web.admin.content.dataspace.model.DataSpaceTreeNodeModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DataSpaceShapeTreeController {
   @Autowired
   public DataSpaceShapeTreeController(DataSpaceContentSettingsService dataSpaceContentSettingsService,
                                       SecurityEngine securityEngine)
   {
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
      this.securityEngine = securityEngine;
   }

   @Secured(
      value = {
         @RequiredPermission(
            resourceType = ResourceType.EM_COMPONENT,
            resource = "settings/presentation/settings",
            actions = ResourceAction.ACCESS
         ),
         @RequiredPermission(
            resourceType = ResourceType.EM_COMPONENT,
            resource = "settings/presentation/org-settings",
            actions = ResourceAction.ACCESS
         )
      },
      operator = "OR"
   )
   @GetMapping("/api/em/content/data-space/shapes/tree")
   public DataSpaceTreeModel getDataSpaceTree(
      @DecodeParam(value = "path", required = false) String parentPath,
      @RequestParam(value = "init", required = false) boolean init,
      Principal principal)
      throws Exception
   {
      if(!checkPathPermission(principal, parentPath)) {
         throw new inetsoft.sree.security.SecurityException(
            "Unauthorized access to data space path \"" + parentPath + "\" by user " + principal);
      }

      if(init) {
         DataSpaceTreeModel tree = dataSpaceContentSettingsService.getTree(parentPath);
         DataSpaceTreeNodeModel shapes = DataSpaceTreeNodeModel.builder().label("shapes")
            .path(parentPath)
            .folder(true)
            .children(tree.nodes())
            .build();
         List<DataSpaceTreeNodeModel> models = new ArrayList<>();
         models.add(shapes);

         return DataSpaceTreeModel.builder().nodes(models).build();
      }

      return dataSpaceContentSettingsService.getTree(parentPath);
   }

   /**
    * Only the shapes folders may be listed with the presentation permission; any other
    * data space path requires the data space permission.
    */
   private boolean checkPathPermission(Principal principal, String path)
      throws inetsoft.sree.security.SecurityException
   {
      String normalized = normalize(path);

      if(normalized != null && !normalized.isEmpty()) {
         String orgID = OrganizationManager.getInstance().getCurrentOrgID();
         String[] shapesDirs = {
            ImageShapes.getGlobalShapesDirectory(),
            ImageShapes.getShapesDirectory(),
            "portal/" + orgID + "/shapes"
         };

         for(String dir : shapesDirs) {
            if(normalized.equals(dir) || normalized.startsWith(dir + "/")) {
               return true;
            }
         }
      }

      return securityEngine.checkPermission(principal, ResourceType.EM_COMPONENT,
         "settings/content/data-space", ResourceAction.ACCESS);
   }

   /**
    * Normalize a data space path. Returns null if the path contains a "." or ".." segment.
    */
   private static String normalize(String path) {
      if(path == null) {
         return "";
      }

      StringBuilder buf = new StringBuilder();

      for(String segment : path.trim().replace('\\', '/').split("/")) {
         if(segment.isEmpty()) {
            continue;
         }

         if(".".equals(segment) || "..".equals(segment)) {
            return null;
         }

         if(buf.length() > 0) {
            buf.append('/');
         }

         buf.append(segment);
      }

      return buf.toString();
   }

   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
   private final SecurityEngine securityEngine;
}
