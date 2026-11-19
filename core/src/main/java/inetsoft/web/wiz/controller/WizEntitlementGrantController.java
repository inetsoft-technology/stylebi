/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.controller;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminIdentityRefs;
import inetsoft.web.wiz.WizPermissions;
import inetsoft.web.wiz.WizUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * wiz-services-facing read and write of the direct grants of the Wiz permissions
 * ({@code ResourceType.WIZ_PERMISSION}, action {@code ACCESS}) in the caller's organization. The
 * caller arrives as a wiz principal, which {@code WizDelegatingCheckPermissionStrategy} approves
 * for every check, so the {@code entitlement.read}/{@code entitlement.write} gates are evaluated on
 * {@link WizUtil#saveCheckPrincipal} (the same user without the wiz tag). The organization is
 * always the caller's, never a request value.
 */
@RestController
public class WizEntitlementGrantController {
   @Autowired
   public WizEntitlementGrantController(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @GetMapping("/api/wiz/v1/entitlements/grants")
   public List<GrantEntry> getGrants(Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      requirePermission(principal, "entitlement.read");
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
      SecurityProvider provider = securityEngine.getSecurityProvider();
      List<GrantEntry> result = new ArrayList<>();

      for(String permission : WizPermissions.ALL) {
         result.add(read(provider, permission, orgId));
      }

      return result;
   }

   @PostMapping("/api/wiz/v1/entitlements/grants")
   public GrantEntry setGrant(@RequestBody(required = false) GrantRequest request,
                              Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      requirePermission(principal, "entitlement.write");

      if(request == null) {
         throw badRequest("body: required");
      }

      String permission = request.permission();

      if(permission == null || !WizPermissions.ALL.contains(permission)) {
         throw badRequest("permission: must be one of " + WizPermissions.ALL);
      }

      int type = identityType(request.principalType());

      if(request.granted() == null) {
         throw badRequest("granted: required (true or false)");
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
      IdentityID id = resolveIdentity(request.principal(), type, orgId);
      SecurityProvider provider = securityEngine.getSecurityProvider();
      Permission existing = provider.getPermission(ResourceType.WIZ_PERMISSION, permission, orgId);
      Permission updated = existing == null ? new Permission() : (Permission) existing.clone();
      Set<String> names = ownGrantNames(updated, type, orgId);
      boolean changed = request.granted() ? names.add(id.getName()) : names.remove(id.getName());

      if(changed) {
         setGrantNames(updated, type, names, orgId);

         if(hasAnyGrant(updated, orgId)) {
            updated.updateGrantAllByOrg(orgId, true);
         }
         else {
            updated.removeGrantAllByOrg(orgId);
         }

         provider.setPermission(ResourceType.WIZ_PERMISSION, permission, updated, orgId);
         audit(principal, permission, request, id);
      }

      return read(provider, permission, orgId);
   }

   private void requirePermission(Principal principal, String permission)
      throws inetsoft.sree.security.SecurityException
   {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(principal == null) {
         throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
      }

      Principal real = principal instanceof XPrincipal ?
         WizUtil.saveCheckPrincipal((XPrincipal) principal) : principal;

      if(!securityEngine.checkPermission(
         real, ResourceType.WIZ_PERMISSION, permission, ResourceAction.ACCESS))
      {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, permission + " required");
      }
   }

   private static int identityType(String type) {
      if(type != null) {
         switch(type) {
         case "user":
            return Identity.USER;
         case "group":
            return Identity.GROUP;
         case "role":
            return Identity.ROLE;
         default:
            break;
         }
      }

      throw badRequest("principalType: must be one of user, group, role");
   }

   private IdentityID resolveIdentity(String raw, int type, String orgId) {
      if(raw == null || raw.isBlank()) {
         throw badRequest("principal: required");
      }

      AdminIdentityRefs.Kind kind = switch(type) {
         case Identity.USER -> AdminIdentityRefs.Kind.USER;
         case Identity.GROUP -> AdminIdentityRefs.Kind.GROUP;
         default -> AdminIdentityRefs.Kind.ROLE;
      };
      AdminIdentityRefs.Directory directory =
         AdminIdentityRefs.of(() -> securityEngine.getSecurityProvider());
      IdentityID id;

      try {
         id = AdminIdentityRefs.parse("principal", raw, orgId, kind, false, false, directory);
      }
      catch(IllegalArgumentException e) {
         throw badRequest(e.getMessage());
      }

      if(!directory.exists(kind, id)) {
         throw badRequest("principal: no " + kind.name().toLowerCase() + " named \"" +
                          raw.trim() + "\" in the caller's organization");
      }

      return id;
   }

   private static GrantEntry read(SecurityProvider provider, String permission, String orgId) {
      Permission perm = provider.getPermission(ResourceType.WIZ_PERMISSION, permission, orgId);

      if(perm == null) {
         return new GrantEntry(permission, List.of(), List.of(), List.of());
      }

      return new GrantEntry(permission,
                            names(perm.getOrgScopedUserGrants(ResourceAction.ACCESS, orgId)),
                            names(perm.getOrgScopedGroupGrants(ResourceAction.ACCESS, orgId)),
                            names(perm.getOrgScopedRoleGrants(ResourceAction.ACCESS, orgId)));
   }

   private static List<String> names(Set<IdentityID> ids) {
      return ids.stream().map(IdentityID::getName).sorted().collect(Collectors.toList());
   }

   /** The grantees stored in the caller's own bucket (global, org-less grants are left alone). */
   private static Set<String> ownGrantNames(Permission perm, int type, String orgId) {
      return perm.getGrants(ResourceAction.ACCESS, type, orgId).stream()
         .filter(g -> orgId.equals(g.getOrganizationID()))
         .map(Permission.PermissionIdentity::getName)
         .collect(Collectors.toCollection(TreeSet::new));
   }

   private static void setGrantNames(Permission perm, int type, Set<String> names, String orgId) {
      switch(type) {
      case Identity.USER -> perm.setUserGrantsForOrg(ResourceAction.ACCESS, names, orgId);
      case Identity.GROUP -> perm.setGroupGrantsForOrg(ResourceAction.ACCESS, names, orgId);
      default -> perm.setRoleGrantsForOrg(ResourceAction.ACCESS, names, orgId);
      }
   }

   private static boolean hasAnyGrant(Permission perm, String orgId) {
      return !perm.getOrgScopedUserGrants(ResourceAction.ACCESS, orgId).isEmpty() ||
         !perm.getOrgScopedGroupGrants(ResourceAction.ACCESS, orgId).isEmpty() ||
         !perm.getOrgScopedRoleGrants(ResourceAction.ACCESS, orgId).isEmpty();
   }

   private static void audit(Principal principal, String permission, GrantRequest request,
                             IdentityID id)
   {
      try {
         ActionRecord record = new ActionRecord(
            SUtil.getUserName(principal), ActionRecord.ACTION_NAME_EDIT,
            ResourceType.WIZ_PERMISSION + ":" + permission,
            ActionRecord.OBJECT_TYPE_OBJECTPERMISSION,
            new Timestamp(System.currentTimeMillis()), ActionRecord.ACTION_STATUS_SUCCESS,
            (request.granted() ? "grant " : "revoke ") + request.principalType() + " " +
               id.getName());
         Audit.getInstance().auditAction(record, principal);
      }
      catch(Exception e) {
         // an audit failure must not replace the outcome of the write
         LOG.error("Failed to audit Wiz permission grant change", e);
      }
   }

   private static ResponseStatusException badRequest(String message) {
      return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
   }

   public record GrantEntry(String permission, List<String> users, List<String> groups,
                            List<String> roles)
   {
   }

   public record GrantRequest(String permission, String principalType, String principal,
                              Boolean granted)
   {
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizEntitlementGrantController.class);
   private final SecurityEngine securityEngine;
}
