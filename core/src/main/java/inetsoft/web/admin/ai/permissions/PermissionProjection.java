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
package inetsoft.web.admin.ai.permissions;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;

import java.util.*;

/**
 * Canonical string projections of a resource's raw {@link Permission}, used both as the
 * plan/hash input (the TOTAL projection, spec section 5) and as the audit before/after value (a
 * single-grant projection, spec section 8). Deliberately independent of
 * {@code SecurityService.convertPermission}, which is {@code private} and DTO-shaped -- this
 * class re-derives the projection only, not the authorization or mutation logic, so it is not the
 * kind of duplication Track C.0's finding warned against (that finding was about re-deriving
 * *authorization checks*, not about re-deriving a read-only string projection of already-fetched
 * data).
 *
 * <p>{@code Permission} has no {@code equals()} (confirmed by reading the class -- only
 * {@code hashCode()}/{@code toString()} are overridden), and its internal grant sets are
 * unordered ({@code HashSet}-backed, per {@code SecurityService.convertPermission}'s own
 * {@code HashMap}/{@code HashSet} usage). Both projections here sort deterministically so that two
 * calls resolving the same logical grant set always produce the same string, regardless of
 * internal iteration order.
 *
 * <p>Scoped to a single organization id, matching what {@code SecurityService}'s own DTO
 * methods can see (spec section 4a: none of its permission methods carry {@code @SwitchOrg}; org
 * scoping instead comes from {@code OrganizationManager.getCurrentOrgID()} read inside
 * {@code convertPermission}). A raw {@code Permission} object can in principle carry grants for
 * other orgs, but this area's own reads/writes never see or touch them, so including them in the
 * hash would perturb the plan on a change no verb in this area could have caused or observed --
 * scoping to the ambient org keeps the hash's "what could have changed this" story accurate.
 */
final class PermissionProjection {
   private PermissionProjection() {
   }

   /**
    * The total projection: every grant on the resource, scoped to {@code orgId}, sorted by
    * {@code (identityType, orgID, name)} then by action name within each identity.
    *
    * @return {@code null} when {@code permission} is {@code null} -- callers must not confuse this
    *         with an explicit empty-but-present {@code Permission} (spec section 4's null-vs-empty
    *         finding); the empty case projects to {@code ""}, not {@code null}.
    */
   static String projectTotal(Permission permission, String orgId) {
      if(permission == null) {
         return null;
      }

      return render(collectByIdentity(permission, orgId));
   }

   /**
    * The total projection (as {@link #projectTotal}) but with one identity's grant replaced or
    * removed -- used for a plan's {@code proposedValue} (spec section 5/6), where the hash must
    * still cover the whole resource but the rendered value must reflect the requested change
    * rather than repeating the pre-change snapshot. Unlike {@link #projectTotal}, degrades
    * gracefully when {@code permission} is {@code null} (a resource with no existing grants at
    * all) instead of returning {@code null} -- the projection then contains just the overridden
    * identity, if any.
    *
    * @param overrideActionsOrNull the actions this identity should have after the change;
    *                              {@code null} or empty removes the identity's grant entirely
    *                              (the delete case).
    */
   static String projectTotalWithOverride(Permission permission, String orgId, String identityType,
                                          IdentityID identityId, List<String> overrideActionsOrNull)
   {
      TreeMap<String, TreeSet<String>> byIdentity =
         permission == null ? new TreeMap<>() : collectByIdentity(permission, orgId);

      String key = identityKey(identityType, identityId);

      if(overrideActionsOrNull == null || overrideActionsOrNull.isEmpty()) {
         byIdentity.remove(key);
      }
      else {
         byIdentity.put(key, new TreeSet<>(overrideActionsOrNull));
      }

      return render(byIdentity);
   }

   private static TreeMap<String, TreeSet<String>> collectByIdentity(Permission permission,
                                                                     String orgId)
   {
      TreeMap<String, TreeSet<String>> byIdentity = new TreeMap<>();

      for(ResourceAction action : ResourceAction.values()) {
         collect(byIdentity, "USER", permission.getOrgScopedUserGrants(action, orgId), action);
         collect(byIdentity, "GROUP", permission.getOrgScopedGroupGrants(action, orgId), action);
         collect(byIdentity, "ROLE", permission.getOrgScopedRoleGrants(action, orgId), action);
         collect(byIdentity, "ORGANIZATION",
                permission.getOrgScopedOrganizationGrants(action, orgId), action);
      }

      return byIdentity;
   }

   private static String render(TreeMap<String, TreeSet<String>> byIdentity) {
      StringBuilder sb = new StringBuilder();

      for(Map.Entry<String, TreeSet<String>> entry : byIdentity.entrySet()) {
         sb.append(entry.getKey()).append('=')
            .append(String.join(",", entry.getValue())).append(';');
      }

      return sb.toString();
   }

   /** The projection of a single grant, used for the audit {@code beforeValue}/{@code afterValue}
    * (spec section 8) and for apply-time single-grant verification (spec section 6). {@code null}
    * {@code actions} (or an empty list) projects to just the identity key with no {@code =...}
    * suffix, representing "this identity currently has no grant" -- the audit record for a delete's
    * {@code afterValue} and a create's {@code beforeValue}. */
   static String projectGrant(String identityType, IdentityID identityId, List<String> actions) {
      String key = identityKey(identityType, identityId);

      if(actions == null || actions.isEmpty()) {
         return key;
      }

      TreeSet<String> sorted = new TreeSet<>(actions);
      return key + "=" + String.join(",", sorted);
   }

   private static void collect(TreeMap<String, TreeSet<String>> byIdentity, String type,
                               Set<IdentityID> ids, ResourceAction action)
   {
      for(IdentityID id : ids) {
         byIdentity.computeIfAbsent(identityKey(type, id), k -> new TreeSet<>()).add(action.name());
      }
   }

   /** The set of actions currently granted to one identity on a resource, read from the raw
    * {@link Permission} -- used for apply-time single-grant verification (spec section 6), which
    * intentionally checks only the touched grant, not the whole resource (unlike the hash, spec
    * section 5). Empty (never {@code null}) when the identity has no grant, or when
    * {@code permission} is {@code null}. */
   static Set<String> actionsFor(Permission permission, String identityType, IdentityID identityId,
                                 String orgId)
   {
      if(permission == null) {
         return Collections.emptySet();
      }

      TreeSet<String> result = new TreeSet<>();

      for(ResourceAction action : ResourceAction.values()) {
         Set<IdentityID> grants;

         switch(identityType) {
         case "USER":
            grants = permission.getOrgScopedUserGrants(action, orgId);
            break;
         case "GROUP":
            grants = permission.getOrgScopedGroupGrants(action, orgId);
            break;
         case "ROLE":
            grants = permission.getOrgScopedRoleGrants(action, orgId);
            break;
         case "ORGANIZATION":
            grants = permission.getOrgScopedOrganizationGrants(action, orgId);
            break;
         default:
            grants = Collections.emptySet();
         }

         if(grants.contains(identityId)) {
            result.add(action.name());
         }
      }

      return result;
   }

   private static String identityKey(String type, IdentityID id) {
      String orgId = id == null || id.getOrgID() == null ? "" : id.getOrgID();
      String name = id == null || id.getName() == null ? "" : id.getName();
      return type + "|" + orgId + "|" + name;
   }
}
