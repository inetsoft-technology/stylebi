/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.sree.security.*;
import inetsoft.uql.util.IdentityNode;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class IdentityTreeController {
   @Autowired
   public IdentityTreeController(IdentityTreeService identityTreeService) {
      this.identityTreeService = identityTreeService;

   }
   /**
    * Get sub identity ndoes from folder
    *
    * @param name          the parent node name
    * @param type          the parent node type
    * @param searchMode    if is in search mode
    * @param principal     the principal user
    *
    * @return  the sub identity nodes
    */
   @GetMapping("/api/vs/expand-identity-node")
   public TreeNodeModel[] getFolder(
      @RequestParam(value = "name", defaultValue = "") String name,
      @RequestParam(value = "type", defaultValue = "0") int type,
      @RequestParam(value = "searchMode", defaultValue = "false") boolean searchMode,
      @RequestParam(value = "searchString", defaultValue = "") String searchString,
      @RequestParam(value = "hideOrgName", defaultValue = "true") boolean hideOrgName,
      Principal principal)
   {
      SecurityEngine engine = SecurityEngine.getSecurity();
      SecurityProvider provider = engine.getSecurityProvider();
      List<TreeNodeModel> treeNodeModels = new ArrayList<>();
      IdentityID id = IdentityID.getIdentityIDFromKey(name);

      if(!Tool.isEmptyString(searchString) && IdentityNode.USERS == type) {
         treeNodeModels = identityTreeService.getUserTeeNode(engine, provider, searchMode, id,
            searchString, principal);
      }
      else if(!Tool.isEmptyString(searchString) && IdentityNode.GROUPS == type) {
         treeNodeModels = identityTreeService.getGroupTreeNode(
            provider, searchMode, searchString, principal, hideOrgName);
      }
      else if(!Tool.isEmptyString(searchString)) {
         treeNodeModels = identityTreeService.getSearchTree(engine, provider, id,
            searchMode, searchString, principal, hideOrgName);
      }
      else if(IdentityNode.USERS == type) {
         treeNodeModels = identityTreeService.getUserTeeNode(engine, provider, searchMode, id,
            searchString, principal);
      }
      else if(IdentityNode.GROUPS == type) {
         treeNodeModels = identityTreeService.getGroupTreeNode(
            provider, searchMode, searchString, principal, hideOrgName);
      }

      return treeNodeModels.toArray(new TreeNodeModel[0]);
   }


   private final IdentityTreeService identityTreeService;
   private static final Logger LOG =
      LoggerFactory.getLogger(IdentityTreeController.class);
}
