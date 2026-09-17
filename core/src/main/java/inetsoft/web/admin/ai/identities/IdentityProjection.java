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
package inetsoft.web.admin.ai.identities;

import inetsoft.web.admin.security.*;
import inetsoft.sree.security.IdentityID;

import java.util.*;

/**
 * Canonical string projections of the four identity DTOs, used as the plan/hash input (spec
 * section 5) and as the audit before/after value (spec section 8). Every list-valued field is
 * sorted before concatenation, since none of the four DTOs canonicalize list order and none but
 * {@code SecurityUser} override {@code equals()} (confirmed by reading each) -- the same reasoning
 * {@code PermissionProjection} (Track C.2) applied to {@code Permission}'s unordered grant sets.
 *
 * <p>{@code password}/{@code defaultPassword} are never included as literal content (spec section
 * 9) -- only a fixed presence/absence token, so a plan whose only change is a different password
 * still perturbs the hash without the hash (or a hash-mismatch error message echoing it back) ever
 * exposing the value or even its length.
 */
final class IdentityProjection {
   private IdentityProjection() {
   }

   static String projectUser(SecurityUser u) {
      if(u == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();
      append(sb, "id", idKey(u.getIdentityID()));
      append(sb, "alias", u.getAlias());
      append(sb, "locale", u.getLocale());
      append(sb, "theme", u.getTheme());
      append(sb, "active", u.isActive() ? "true" : "false");
      append(sb, "password", "pw:set"); // presence-only token; SecurityUser never carries the
                                        // value on a read (getUserModel never sets it), and this
                                        // projection is never built from a create request's raw
                                        // password either -- see requireLegalFields/resolveCreate.
      appendSorted(sb, "emails", u.getEmails());
      appendSorted(sb, "groups", u.getGroups());
      appendSortedIds(sb, "roles", u.getRoles());
      return sb.toString();
   }

   /** Projects a create request's spec before the identity exists (no {@code get} call possible
    * yet) -- used for the {@code proposedValue} half of a create's {@link
    * inetsoft.web.admin.ai.PlanChange}, mirroring {@link #projectUser} field-for-field so the hash
    * is comparable across preview and apply. */
   static String projectUserSpec(IdentityID id, IdentitySpec spec) {
      StringBuilder sb = new StringBuilder();
      append(sb, "id", idKey(id));
      append(sb, "alias", spec.getAlias());
      append(sb, "locale", spec.getLocale());
      append(sb, "theme", spec.getTheme());
      append(sb, "active", (spec.getActive() == null || spec.getActive()) ? "true" : "false");
      append(sb, "password", isBlank(spec.getPassword()) ? "pw:unset" : "pw:set");
      appendSorted(sb, "emails", spec.getEmails());
      appendSorted(sb, "groups", spec.getGroups());
      appendSorted(sb, "roles", spec.getRoles());
      return sb.toString();
   }

   static String projectGroup(SecurityGroup g, IdentityID id) {
      if(g == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();
      append(sb, "id", idKey(id));
      append(sb, "theme", g.getTheme());
      appendSorted(sb, "parentGroups", g.getParentGroups());
      appendSorted(sb, "memberUsers", g.getMemberUsers());
      appendSorted(sb, "memberGroups", g.getMemberGroups());
      appendSortedIds(sb, "roles", g.getRoles());
      return sb.toString();
   }

   static String projectGroupSpec(IdentityID id, IdentitySpec spec) {
      StringBuilder sb = new StringBuilder();
      append(sb, "id", idKey(id));
      append(sb, "theme", spec.getTheme());
      appendSorted(sb, "parentGroups", spec.getParentGroups());
      appendSorted(sb, "memberUsers", spec.getMemberUsers());
      appendSorted(sb, "memberGroups", spec.getMemberGroups());
      appendSorted(sb, "roles", spec.getRoles());
      return sb.toString();
   }

   static String projectRole(SecurityRole r, IdentityID id) {
      if(r == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();
      append(sb, "id", idKey(id));
      append(sb, "description", r.getDescription());
      append(sb, "theme", r.getTheme());
      appendSorted(sb, "assignedUsers", r.getAssignedUsers());
      appendSorted(sb, "assignedGroups", r.getAssignedGroups());
      appendSortedIds(sb, "inheritedRoles", r.getInheritedRoles());
      return sb.toString();
   }

   static String projectRoleSpec(IdentityID id, IdentitySpec spec) {
      StringBuilder sb = new StringBuilder();
      append(sb, "id", idKey(id));
      append(sb, "description", spec.getDescription());
      append(sb, "theme", spec.getTheme());
      appendSorted(sb, "assignedUsers", spec.getAssignedUsers());
      appendSorted(sb, "assignedGroups", spec.getAssignedGroups());
      appendSorted(sb, "inheritedRoles", spec.getInheritedRoles());
      return sb.toString();
   }

   static String projectOrganization(SecurityOrganization o, String organizationId) {
      if(o == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();
      append(sb, "id", organizationId);
      append(sb, "name", o.getName());
      append(sb, "locale", o.getLocale());
      append(sb, "theme", o.getTheme());
      return sb.toString();
   }

   static String projectOrganizationSpec(String organizationId, IdentitySpec spec) {
      StringBuilder sb = new StringBuilder();
      append(sb, "id", organizationId);
      append(sb, "name", spec.getOrgName());
      append(sb, "locale", spec.getLocale());
      append(sb, "theme", spec.getTheme());
      return sb.toString();
   }

   private static void append(StringBuilder sb, String field, String value) {
      sb.append(field).append('=').append(value == null ? "" : value).append(';');
   }

   private static void appendSorted(StringBuilder sb, String field, List<String> values) {
      TreeSet<String> sorted = values == null ? new TreeSet<>() : new TreeSet<>(values);
      sb.append(field).append('=').append(String.join(",", sorted)).append(';');
   }

   private static void appendSortedIds(StringBuilder sb, String field, List<IdentityID> ids) {
      TreeSet<String> sorted = new TreeSet<>();

      if(ids != null) {
         for(IdentityID id : ids) {
            sorted.add(idKey(id));
         }
      }

      sb.append(field).append('=').append(String.join(",", sorted)).append(';');
   }

   private static String idKey(IdentityID id) {
      return id == null ? "" : id.convertToKey();
   }

   private static boolean isBlank(String s) {
      return s == null || s.trim().isEmpty();
   }
}
