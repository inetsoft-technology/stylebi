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
import inetsoft.web.admin.security.action.ActionPermissionService;
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

      if(!(request.granted() instanceof Boolean)) {
         throw badRequest("granted: must be the JSON boolean true or false");
      }

      boolean granted = (Boolean) request.granted();

      if(granted && SUtil.isMultiTenant() &&
         !ActionPermissionService.isOrgAdminAction(ResourceType.WIZ_PERMISSION, permission))
      {
         throw badRequest("permission '" + permission +
                          "' is not grantable in multi-tenant mode");
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
      IdentityID id = resolveIdentity(request.principal(), type, orgId, granted);

      // One node only: this serializes the read-modify-write below inside this JVM. A cluster
      // would need storage-level help (compare-and-set), which this does not provide.
      GrantEntry result;
      boolean changed;
      SecurityProvider provider = securityEngine.getSecurityProvider();

      synchronized(WRITE_LOCK) {
         provider = securityEngine.getSecurityProvider();
         Permission existing =
            provider.getPermission(ResourceType.WIZ_PERMISSION, permission, orgId);
         // The stored object is live and Permission.clone() shares its edited-flag map, so the
         // update works on a copy whose flag map is also its own: nothing reachable from the stored
         // permission may change before setPermission succeeds.
         Permission updated = existing == null ? new Permission() : (Permission) existing.clone();
         updated.setOrgEditedGrantAll(existing == null || existing.getOrgEditedGrantAll() == null ?
            new HashMap<>() : new HashMap<>(existing.getOrgEditedGrantAll()));
         Set<String> names = ownGrantNames(updated, type, orgId);

         // GET lists organization-less (global) grantees too, but only the caller's own bucket can
         // be edited here, so a revoke of one must say so instead of silently doing nothing
         if(!granted && !names.contains(id.getName()) && isGlobalGrant(updated, type, id.getName())) {
            throw badRequest("principal: this grant is global (organization-less) and can only " +
                             "be changed in the Enterprise Manager");
         }

         changed = granted ? names.add(id.getName()) : names.remove(id.getName());

         if(changed) {
            setGrantNames(updated, type, names, orgId);

            // A revoke can only tighten: the edited flag is set by a grant and never cleared by a
            // revoke, so revoking the last grantee leaves the permission restricted to nobody.
            // Only reset restores the default.
            if(granted) {
               updated.updateGrantAllByOrg(orgId, true);
            }

            try {
               provider.setPermission(ResourceType.WIZ_PERMISSION, permission, updated, orgId);
            }
            catch(RuntimeException e) {
               LOG.error("Failed to store Wiz permission {}", permission, e);
               throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                 "the grant could not be stored");
            }

         }

         result = read(provider, permission, orgId);
      }

      // outside the lock: the audit backend may do database I/O
      if(changed) {
         audit(principal, permission,
               (granted ? "grant " : "revoke ") + request.principalType() + " " + id.getName(),
               orgId, provider);
      }

      return result;
   }

   /**
    * Removes every grantee of the permission's ACCESS action in the caller's organization and
    * clears the organization's edited flag, restoring the default behaviour. It only removes, so
    * unlike a grant it is also allowed for the global config.* names in multi-tenant mode (it is
    * the clean-up path). Only the caller's organization bucket is removed: grants with no
    * organization (global role grants) are kept, and the returned {@code roles} still lists them.
    * The default-allow outcome of an end-user permission is not affected by them: it depends on
    * the organization's edited flag, which the reset clears, so after a reset the permission is
    * held by everybody again even while such a global grant remains.
    */
   @PostMapping("/api/wiz/v1/entitlements/grants/reset")
   public GrantEntry resetGrants(@RequestBody(required = false) ResetRequest request,
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

      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
      GrantEntry result;
      boolean changed = false;
      SecurityProvider provider = securityEngine.getSecurityProvider();

      synchronized(WRITE_LOCK) {
         Permission existing =
            provider.getPermission(ResourceType.WIZ_PERMISSION, permission, orgId);

         if(existing != null && (existing.hasOrgEditedGrantAll(orgId) || hasOwnGrants(existing, orgId))) {
            Permission updated = (Permission) existing.clone();
            updated.setOrgEditedGrantAll(existing.getOrgEditedGrantAll() == null ?
               new HashMap<>() : new HashMap<>(existing.getOrgEditedGrantAll()));
            Set<String> none = Set.of();
            updated.setUserGrantsForOrg(ResourceAction.ACCESS, none, orgId);
            updated.setGroupGrantsForOrg(ResourceAction.ACCESS, none, orgId);
            updated.setRoleGrantsForOrg(ResourceAction.ACCESS, none, orgId);
            updated.setOrganizationGrantsForOrg(ResourceAction.ACCESS, none, orgId);
            updated.removeGrantAllByOrg(orgId);

            try {
               provider.setPermission(ResourceType.WIZ_PERMISSION, permission, updated, orgId);
            }
            catch(RuntimeException e) {
               LOG.error("Failed to reset Wiz permission {}", permission, e);
               throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                 "the reset could not be stored");
            }

            changed = true;
         }

         result = read(provider, permission, orgId);
      }

      if(changed) {
         audit(principal, permission, "reset all grants", orgId, provider);
      }

      return result;
   }

   private static boolean hasOwnGrants(Permission perm, String orgId) {
      for(int type : new int[] { Identity.USER, Identity.GROUP, Identity.ROLE,
                                 Identity.ORGANIZATION })
      {
         if(perm.getGrants(ResourceAction.ACCESS, type, orgId).stream()
            .anyMatch(g -> orgId.equals(g.getOrganizationID())))
         {
            return true;
         }
      }

      return false;
   }

   private void requirePermission(Principal principal, String permission)
      throws inetsoft.sree.security.SecurityException
   {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!(principal instanceof XPrincipal)) {
         throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
      }

      Principal real = WizUtil.saveCheckPrincipal((XPrincipal) principal);

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

   private IdentityID resolveIdentity(String raw, int type, String orgId, boolean mustExist) {
      if(raw == null || raw.isBlank()) {
         throw badRequest("principal: required");
      }

      if(raw.length() > MAX_PRINCIPAL_LENGTH) {
         throw badRequest("principal: must be at most " + MAX_PRINCIPAL_LENGTH + " characters");
      }

      AdminIdentityRefs.Kind kind = switch(type) {
         case Identity.USER -> AdminIdentityRefs.Kind.USER;
         case Identity.GROUP -> AdminIdentityRefs.Kind.GROUP;
         default -> AdminIdentityRefs.Kind.ROLE;
      };
      AdminIdentityRefs.Directory directory =
         AdminIdentityRefs.of(() -> securityEngine.getSecurityProvider());
      // every failure gets the same message: the parser's own messages would tell a caller which
      // organization ids exist
      String notFound = "principal: no such " + kind.name().toLowerCase() +
         " in the caller's organization";
      IdentityID id;

      try {
         id = AdminIdentityRefs.parse("principal", raw, orgId, kind, false, false, directory);
      }
      catch(IllegalArgumentException e) {
         throw badRequest(notFound);
      }

      if(id == null) {
         throw badRequest(notFound);
      }

      // A revoke only has to name an org-scoped identity: the grantee may have been deleted since,
      // and a grant that is not there is an idempotent no-op.
      if(!mustExist) {
         return id;
      }

      if(!directory.exists(kind, id)) {
         throw badRequest(notFound);
      }

      // directory.exists() also accepts a global (organization-less) role under the caller's org
      // (it falls back to (name, null)); a grant stored for it never matches its members, whose
      // role identity has no organization. Same test as SecurityService.isGlobalRole.
      if(kind == AdminIdentityRefs.Kind.ROLE &&
         securityEngine.getSecurityProvider().getRole(id) == null)
      {
         throw badRequest("principal: global roles cannot be granted Wiz permissions; " +
                          "use an organization role");
      }

      return id;
   }

   private static GrantEntry read(SecurityProvider provider, String permission, String orgId) {
      Permission perm = provider.getPermission(ResourceType.WIZ_PERMISSION, permission, orgId);

      if(perm == null) {
         return new GrantEntry(permission, List.of(), List.of(), List.of(), false,
                               WizPermissions.isEndUser(permission));
      }

      return new GrantEntry(permission,
                            names(perm.getOrgScopedUserGrants(ResourceAction.ACCESS, orgId)),
                            names(perm.getOrgScopedGroupGrants(ResourceAction.ACCESS, orgId)),
                            names(perm.getOrgScopedRoleGrants(ResourceAction.ACCESS, orgId)),
                            perm.hasOrgEditedGrantAll(orgId) && WizPermissions.isEndUser(permission),
                            WizPermissions.isEndUser(permission));
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

   private static boolean isGlobalGrant(Permission perm, int type, String name) {
      return perm.getGrants(ResourceAction.ACCESS, type, null).stream()
         .anyMatch(g -> g.getOrganizationID() == null && name.equals(g.getName()));
   }

   private static void setGrantNames(Permission perm, int type, Set<String> names, String orgId) {
      switch(type) {
      case Identity.USER -> perm.setUserGrantsForOrg(ResourceAction.ACCESS, names, orgId);
      case Identity.GROUP -> perm.setGroupGrantsForOrg(ResourceAction.ACCESS, names, orgId);
      default -> perm.setRoleGrantsForOrg(ResourceAction.ACCESS, names, orgId);
      }
   }

   private static void audit(Principal principal, String permission, String description,
                             String orgId, SecurityProvider provider)
   {
      try {
         ActionRecord record = SUtil.getActionRecord(
            principal, ActionRecord.ACTION_NAME_EDIT,
            ResourceType.WIZ_PERMISSION + ":" + permission + " [" + description + "]",
            ActionRecord.OBJECT_TYPE_OBJECTPERMISSION);
         String orgName = provider.getOrgNameFromID(orgId);
         record.setResourceOrganization(orgId);
         record.setResourceOrganizationName(orgName == null ? orgId : orgName);
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

   /**
    * A revoke can only tighten: it never clears the restriction, so revoking the last grantee of
    * an end-user permission leaves it restricted to nobody until the explicit reset.
    *
    * @param restricted   true only for end-user permissions whose default allow is switched off for
    *                     the organization; the grantee lists then name who still holds it,
    *                     possibly nobody. Always false for an admin permission, which is denied
    *                     unless granted whatever the organization's edited flag says.
    * @param defaultAllow true for an end-user permission, which everybody holds while it is not
    *                     restricted; false for an admin permission, denied unless granted.
    */
   public record GrantEntry(String permission, List<String> users, List<String> groups,
                            List<String> roles, boolean restricted, boolean defaultAllow)
   {
   }

   public record ResetRequest(String permission) {
   }

   /** {@code granted} is read as an Object so that only a real JSON boolean is accepted. */
   public record GrantRequest(String permission, String principalType, String principal,
                              Object granted)
   {
   }

   private static final int MAX_PRINCIPAL_LENGTH = 256;
   private static final Object WRITE_LOCK = new Object();
   private static final Logger LOG = LoggerFactory.getLogger(WizEntitlementGrantController.class);
   private final SecurityEngine securityEngine;
}
