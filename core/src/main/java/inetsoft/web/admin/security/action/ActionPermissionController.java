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
package inetsoft.web.admin.security.action;

import inetsoft.sree.security.*;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.security.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class ActionPermissionController {
   @Autowired
   public ActionPermissionController(ActionPermissionService actionService,
                                     ResourcePermissionService permissionService)
   {
      this.actionService = actionService;
      this.permissionService = permissionService;
   }

   @PostConstruct
   public void loadActions() {
      ActionTreeNode root = this.actionService.getActionTree(ThreadContext.getContextPrincipal());
      Deque<ActionTreeNode> queue = new ArrayDeque<>(root.children());

      while(!queue.isEmpty()) {
         ActionTreeNode node = queue.removeFirst();
         Resource resource = new Resource(node.type(), node.resource());
         actions.put(resource, node.actions());

         for(ActionTreeNode child : node.children()) {
            queue.addLast(child);
         }
      }
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/security/actions",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/security/actions")
   public ActionTreeNode getActionTree(@PermissionUser Principal principal) {
      return actionService.getActionTree(principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/security/actions",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/security/actions/{type}/**")
   public synchronized ResourcePermissionModel getPermissions(@PathVariable("type") String typeName,
                                                 @RemainingPath String path,
                                                 @RequestParam("isGrant") boolean isGrant,
                                                 @PermissionUser Principal principal)
   {
      ResourceType type = ResourceType.valueOf(typeName);
      Resource resource = new Resource(type, path);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      String label = isGrant ? "Grant access to all users" : "Deny access to all users";

      if(typeName.equals(ResourceType.EM_COMPONENT.name()) ||
         typeName.equals(ResourceType.CHART_TYPE.name()))
      {
         label = "Use Parent Permissions";
      }

      boolean isOrgAdmin = false;

      if(principal != null) {
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         IdentityID[] roles = provider.getRoles(pId);
         isOrgAdmin = Arrays
            .stream(provider.getAllRoles(roles))
            .noneMatch(provider::isSystemAdministratorRole);
      }

      label = Catalog.getCatalog().getString(label);
      loadActions();
      return permissionService.getTableModel(path, type, actions.get(resource), label, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/security/actions",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/security/actions/{type}/**")
   public ResourcePermissionModel setPermissions(
      @PathVariable("type") String typeName, @RemainingPath String path,
      @RequestParam("isGrant") boolean isGrant,
      @RequestBody ResourcePermissionModel permissions, @PermissionUser Principal principal)
      throws Exception
   {
      ResourceType type = ResourceType.valueOf(typeName);
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      permissionService
         .setResourcePermissions(path, type, getActionObjectName(typeName, path), permissions, principal);
      return getPermissions(typeName, path, isGrant, principal);
   }

   private String getActionObjectName(String typeName, String path) {
      String type = typeName != null ? typeName.replace("_", " ") : null;
      return path == null || path.isEmpty() ? type : type + ": " + path;
   }

   private final ActionPermissionService actionService;
   private final ResourcePermissionService permissionService;
   private final Map<Resource, EnumSet<ResourceAction>> actions = new HashMap<>();
}
