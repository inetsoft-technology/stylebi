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
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.security.ResourcePermissionTableModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class ActionPermissionController {
   @Autowired
   public ActionPermissionController(ActionPermissionService actionService,
                                     ResourcePermissionService permissionService,
                                     SecurityEngine securityEngine)
   {
      this.actionService = actionService;
      this.permissionService = permissionService;
      this.securityEngine = securityEngine;
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
   public ResourcePermissionModel getPermissions(@PathVariable("type") String typeName,
                                                 @RemainingPath String path,
                                                 @RequestParam("isGrant") boolean isGrant,
                                                 @PermissionUser Principal principal)
   {
      ResourceType type = ResourceType.valueOf(typeName);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(securityEngine.getSecurityProvider().getOrganization(currOrgID) == null) {
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
         SecurityProvider provider = securityEngine.getSecurityProvider();
         IdentityID[] roles = provider.getRoles(pId);
         isOrgAdmin = Arrays
            .stream(provider.getAllRoles(roles))
            .noneMatch(provider::isSystemAdministratorRole);
      }

      // Bug #77251, only a node of the caller's own action tree may be read, and its actions come
      // from that node, never from state shared with other principals
      ActionTreeNode node = getActionNode(type, path, principal);
      label = Catalog.getCatalog().getString(label);
      return permissionService.getTableModel(path, type, node.actions(), label, principal);
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

      if(securityEngine.getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      // Bug #77251, Bug #77252, the target must be a node of the caller's own action tree (which
      // never contains the org admin exclusions or any SECURITY_* resource), and only the actions
      // that node offers may be written. setResourcePermissions() writes every action in
      // displayActions, so an unchecked client model could store e.g. an ADMIN grant.
      ActionTreeNode node = getActionNode(type, path, principal);
      EnumSet<ResourceAction> displayActions = permissions.displayActions();

      if(displayActions == null || !node.actions().containsAll(displayActions)) {
         throw new java.lang.SecurityException(
            "Unauthorized actions " + displayActions + " for action permission " + typeName +
            ":" + path + " by user " + principal);
      }

      OrganizationManager orgManager = OrganizationManager.getInstance();

      // Bug #77362, a caller who is neither a site admin nor an org admin (e.g. a user who only
      // holds ACCESS on settings/security/actions) may only hand out what they already hold
      if(!orgManager.isSiteAdmin(principal) && !orgManager.isOrgAdmin(principal)) {
         // the same stored permission setResourcePermissions() starts from
         Permission stored = securityEngine.getSecurityProvider().getPermission(type, path);
         permissions = keepUnadministeredGrants(permissions, node, stored);

         if(isGrantOrUseParentChange(permissions, stored, currOrgID, principal) &&
            !holdsNodeActions(node, principal))
         {
            throw new java.lang.SecurityException(
               "Unauthorized grant of action permission " + typeName + ":" + path +
               " not held by user " + principal);
         }
      }

      permissionService
         .setResourcePermissions(path, type, getActionObjectName(typeName, path), permissions, principal);
      return getPermissions(typeName, path, isGrant, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/security/actions",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/security/actions/validate-identities")
   public List<ResourcePermissionTableModel> validateIdentities(
      @RequestBody List<ResourcePermissionTableModel> identities)
   {
      return permissionService.findMissingIdentities(identities);
   }

   /**
    * Finds the node of the principal's action tree that matches the requested type and path,
    * searching folders as well as leaves.
    *
    * @throws java.lang.SecurityException if the tree has no such node.
    */
   private ActionTreeNode getActionNode(ResourceType type, String path, Principal principal) {
      ActionTreeNode root = actionService.getActionTree(principal);
      Deque<ActionTreeNode> queue = new ArrayDeque<>(root.children());

      while(!queue.isEmpty()) {
         ActionTreeNode node = queue.removeFirst();

         if(node.type() == type && node.resource() != null && node.resource().equals(path)) {
            return node;
         }

         queue.addAll(node.children());
      }

      throw new java.lang.SecurityException(
         "Unauthorized access to action permission " + type + ":" + path + " by user " + principal);
   }

   /**
    * A model without permissions makes setResourcePermissions() clear the grants of every
    * identity in the org. Replaces it with an empty permission list over the node's actions, so
    * that only the grants of identities the caller administers are cleared, the same as a save of
    * an emptied table.
    */
   private ResourcePermissionModel keepUnadministeredGrants(ResourcePermissionModel permissions,
                                                            ActionTreeNode node, Permission stored)
   {
      if(permissions.permissions() != null || stored == null) {
         return permissions;
      }

      return ResourcePermissionModel.builder()
         .from(permissions)
         .permissions(Collections.emptyList())
         .displayActions(node.actions())
         .build();
   }

   /**
    * Checks if the save would grant an action to an identity that does not hold it in the stored
    * permission, or would switch "use parent permissions" on or off. Removing grants and saving
    * the stored state unchanged are not changes. Only the stored grants of identities the caller
    * administers count, so that the outcome does not reveal the grants of hidden identities.
    */
   private boolean isGrantOrUseParentChange(ResourcePermissionModel permissions, Permission stored,
                                            String orgID, Principal principal)
   {
      if(permissions.permissions() == null) {
         // nothing is stored, so setResourcePermissions() writes nothing
         return false;
      }

      boolean storedEdited = stored != null && stored.hasOrgEditedGrantAll(orgID);

      if(storedEdited != permissions.hasOrgEdited()) {
         return true;
      }

      for(ResourcePermissionTableModel row : permissions.permissions()) {
         IdentityID identity = row.identityID();

         // global role rows are only written by a site admin
         if(identity == null || (row.type() == Identity.Type.ROLE && identity.orgID == null)) {
            continue;
         }

         // Bug #77461, whether the caller administers the row's identity, judged by the same
         // (name, current org) key the GET view and setResourcePermissions() use, not the client
         // supplied org ID
         boolean administered = permissionService.isIdentityAuthorized(
            new IdentityID(identity.name, orgID), row.type(), principal);

         for(ResourceAction action : row.actions()) {
            if(!permissions.displayActions().contains(action)) {
               continue;
            }

            boolean granted = administered && stored != null && stored
               .getOrgScopedGrants(action, row.type().code(), orgID).stream()
               .anyMatch(id -> identity.name.equals(id.name) &&
                  (row.type() != Identity.Type.ROLE || id.orgID != null));

            if(!granted) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean holdsNodeActions(ActionTreeNode node, Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      for(ResourceAction action : node.actions()) {
         if(!securityEngine.checkPermission(principal, node.type(), node.resource(), action)) {
            return false;
         }
      }

      return true;
   }

   private String getActionObjectName(String typeName, String path) {
      String type = typeName != null ? typeName.replace("_", " ") : null;
      String actionObjectName = path == null || path.isEmpty() ? type : type + ": " + path;

      //hardcode end user visible description of permission change for some audit records instead of internal path
      if("DASHBOARD:*".equals(actionObjectName) || "DASHBOARD: *".equals(actionObjectName)) {
         return "PORTAL TAB: Dashboard";
      }

      if("SCHEDULER:*".equals(actionObjectName) || "SCHEDULER: *".equals(actionObjectName)) {
         return "PORTAL TAB: Scheduler";
      }

      return actionObjectName;
   }

   private final ActionPermissionService actionService;
   private final ResourcePermissionService permissionService;
   private final SecurityEngine securityEngine;
}
