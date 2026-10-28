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
import inetsoft.web.admin.ai.PlanHashInputs;

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
 *
 * <p>Every rendered value is separator-escaped ({@code \ , ; =} get a backslash prefix,
 * Bug #78854), so a value can't pass for a list boundary or another field: {@code ["GA,GB"]}
 * and {@code ["GA","GB"]} project differently, and so do their plan hashes. An {@link
 * IdentityID} key escapes its name and organization separately and keeps the literal
 * {@code ~;~} between them, so ordinary names (no separator characters) render, and hash,
 * exactly as before.
 */
final class IdentityProjection {
   private IdentityProjection() {
   }

   static String projectUser(SecurityUser u) {
      if(u == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();
      appendId(sb, u.getIdentityID());
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
   static String projectUserSpec(IdentityID id, IdentitySpec spec, List<IdentityID> roles) {
      StringBuilder sb = new StringBuilder();
      appendId(sb, id);
      append(sb, "alias", spec.getAlias());
      append(sb, "locale", spec.getLocale());
      append(sb, "theme", spec.getTheme());
      append(sb, "active", (spec.getActive() == null || spec.getActive()) ? "true" : "false");
      append(sb, "password", isBlank(spec.getPassword()) ? "pw:unset" : "pw:set");
      appendSorted(sb, "emails", spec.getEmails());
      appendSorted(sb, "groups", spec.getGroups());
      appendSortedIds(sb, "roles", roles);
      return sb.toString();
   }

   static String projectGroup(SecurityGroup g, IdentityID id) {
      if(g == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();
      appendId(sb, id);
      append(sb, "theme", g.getTheme());
      appendSorted(sb, "parentGroups", g.getParentGroups());
      appendSorted(sb, "memberUsers", g.getMemberUsers());
      appendSorted(sb, "memberGroups", g.getMemberGroups());
      appendSortedIds(sb, "roles", g.getRoles());
      return sb.toString();
   }

   static String projectGroupSpec(IdentityID id, IdentitySpec spec) {
      StringBuilder sb = new StringBuilder();
      appendId(sb, id);
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
      appendId(sb, id);
      append(sb, "description", r.getDescription());
      append(sb, "theme", r.getTheme());
      appendSorted(sb, "assignedUsers", r.getAssignedUsers());
      appendSorted(sb, "assignedGroups", r.getAssignedGroups());
      appendSortedIds(sb, "inheritedRoles", r.getInheritedRoles());
      append(sb, "defaultRole", String.valueOf(Boolean.TRUE.equals(r.getDefaultRole())));
      append(sb, "sysAdmin", String.valueOf(Boolean.TRUE.equals(r.getSysAdmin())));
      append(sb, "orgAdmin", String.valueOf(Boolean.TRUE.equals(r.getOrgAdmin())));
      return sb.toString();
   }

   static String projectRoleSpec(IdentityID id, IdentitySpec spec) {
      StringBuilder sb = new StringBuilder();
      appendId(sb, id);
      append(sb, "description", spec.getDescription());
      append(sb, "theme", spec.getTheme());
      appendSorted(sb, "assignedUsers", spec.getAssignedUsers());
      appendSorted(sb, "assignedGroups", spec.getAssignedGroups());
      appendSorted(sb, "inheritedRoles", spec.getInheritedRoles());
      append(sb, "defaultRole", String.valueOf(Boolean.TRUE.equals(spec.getDefaultRole())));
      append(sb, "sysAdmin", String.valueOf(Boolean.TRUE.equals(spec.getSysAdmin())));
      append(sb, "orgAdmin", String.valueOf(Boolean.TRUE.equals(spec.getOrgAdmin())));
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
      appendProperties(sb, "properties", o.getProperties());
      return sb.toString();
   }

   static String projectOrganizationSpec(String organizationId, IdentitySpec spec) {
      StringBuilder sb = new StringBuilder();
      append(sb, "id", organizationId);
      append(sb, "name", spec.getOrgName());
      append(sb, "locale", spec.getLocale());
      append(sb, "theme", spec.getTheme());
      appendProperties(sb, "properties", spec.getProperties());
      return sb.toString();
   }

   private static void append(StringBuilder sb, String field, String value) {
      sb.append(field).append('=').append(escape(value)).append(';');
   }

   /** The {@code id} field of a user/group/role: the key's name and org are escaped separately
    * and the literal {@code ~;~} between them is kept (see {@link #escapedIdKey}). */
   private static void appendId(StringBuilder sb, IdentityID id) {
      sb.append("id=").append(escapedIdKey(id)).append(';');
   }

   private static void appendSorted(StringBuilder sb, String field, List<String> values) {
      TreeSet<String> sorted = values == null ? new TreeSet<>() : new TreeSet<>(values);
      StringJoiner joined = new StringJoiner(",");

      for(String value : sorted) {
         joined.add(escape(value));
      }

      sb.append(field).append('=').append(joined).append(';');
   }

   /** {@code properties} isn't a flat {@code List<String>}, so it gets its own sorted-by-name
    * {@code name=value} rendering rather than reusing {@link #appendSorted}. */
   private static void appendProperties(StringBuilder sb, String field, List<PropertyModel> properties) {
      TreeMap<String, String> sorted = new TreeMap<>();

      if(properties != null) {
         for(PropertyModel property : properties) {
            sorted.put(property.name(), property.value());
         }
      }

      StringBuilder rendered = new StringBuilder();

      for(Map.Entry<String, String> entry : sorted.entrySet()) {
         if(rendered.length() > 0) {
            rendered.append(',');
         }

         rendered.append(escape(entry.getKey())).append('=')
            .append(escape(String.valueOf(entry.getValue())));
      }

      sb.append(field).append('=').append(rendered).append(';');
   }

   private static void appendSortedIds(StringBuilder sb, String field, List<IdentityID> ids) {
      // Sorted and de-duplicated by the escaped key. It equals the raw key for ordinary names, so
      // their order and bytes are unchanged, but two distinct ids whose raw keys collide (a
      // "~;~" inside a name or org) stay distinct.
      TreeSet<String> sorted = new TreeSet<>();

      if(ids != null) {
         for(IdentityID id : ids) {
            sorted.add(escapedIdKey(id));
         }
      }

      sb.append(field).append('=').append(String.join(",", sorted)).append(';');
   }

   /** {@link IdentityID#convertToKey()} with the name and organization escaped separately
    * around the literal {@link IdentityID#KEY_DELIMITER}: the delimiter is the only unescaped
    * {@code ;} in the result, so a {@code ~;~} inside the name or org can't move the boundary. */
   private static String escapedIdKey(IdentityID id) {
      if(id == null) {
         return "";
      }

      String key = id.convertToKey();
      String name = id.getName();
      // convertToKey() is name + KEY_DELIMITER + org (org falls back to the global key), so the
      // org part is everything after the name and the delimiter.
      String org = key.substring(String.valueOf(name).length() + IdentityID.KEY_DELIMITER.length());
      return escape(String.valueOf(name)) + IdentityID.KEY_DELIMITER + escape(org);
   }

   /** Backslash-escapes the projection's separator characters. Identity for ordinary values. The
    * implementation moved to {@link PlanHashInputs#escape} (Bug #79016) so every admin-ai
    * projection shares one escaper. */
   private static String escape(String value) {
      return PlanHashInputs.escape(value);
   }

   private static boolean isBlank(String s) {
      return s == null || s.trim().isEmpty();
   }
}
