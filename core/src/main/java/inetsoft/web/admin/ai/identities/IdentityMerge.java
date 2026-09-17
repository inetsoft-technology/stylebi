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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds a fully-populated update request DTO from an already-fetched current identity and the
 * caller's partial {@link IdentitySpec}, per field: {@code spec}'s value when present, the
 * current value otherwise (design section 3.1). Every field is supplied explicitly -- none of
 * {@code SecurityService.updateUser}/{@code updateGroup}/{@code updateRole}/
 * {@code updateOrganization} can be trusted to preserve an omitted field on its own (only
 * {@code emails}/{@code roles} on user, {@code roles} on group, and {@code inheritedRoles} on role
 * are genuinely null-guarded there; every other field is a blind overwrite -- confirmed by reading
 * all four methods in full).
 */
final class IdentityMerge {
   private IdentityMerge() {
   }

   static SecurityUser mergeUser(SecurityUser current, IdentitySpec spec, IdentityID currentId) {
      SecurityUser merged = new SecurityUser();
      merged.setIdentityID(new IdentityID(renamedOrCurrent(spec.getName(), currentId.name),
                                          currentId.orgID));
      merged.setAlias(spec.getAlias() != null ? spec.getAlias() : current.getAlias());
      merged.setLocale(spec.getLocale() != null ? spec.getLocale() : current.getLocale());
      merged.setTheme(spec.getTheme() != null ? spec.getTheme() : current.getTheme());
      merged.setActive(spec.getActive() != null ? spec.getActive() : current.isActive());
      merged.setEmails(spec.getEmails() != null ? spec.getEmails() : current.getEmails());
      merged.setGroups(spec.getGroups() != null ? spec.getGroups() : current.getGroups());
      merged.setRoles(spec.getRoles() != null ? toIdentityIds(spec.getRoles(), currentId.orgID) :
                      current.getRoles());
      // password: never set (left null) -- updateUser never reads request.getPassword() at all,
      // and spec.password is refused at validation time before the merge ever runs; belt and
      // suspenders, not load-bearing.
      return merged;
   }

   static SecurityGroup mergeGroup(SecurityGroup current, IdentitySpec spec, IdentityID currentId) {
      SecurityGroup merged = new SecurityGroup();
      merged.setIdentityID(new IdentityID(renamedOrCurrent(spec.getName(), currentId.name),
                                          currentId.orgID));
      merged.setTheme(spec.getTheme() != null ? spec.getTheme() : current.getTheme());
      merged.setParentGroups(spec.getParentGroups() != null ? spec.getParentGroups() :
                             current.getParentGroups());
      merged.setMemberUsers(spec.getMemberUsers() != null ? spec.getMemberUsers() :
                            current.getMemberUsers());
      merged.setMemberGroups(spec.getMemberGroups() != null ? spec.getMemberGroups() :
                             current.getMemberGroups());
      merged.setRoles(spec.getRoles() != null ? toIdentityIds(spec.getRoles(), currentId.orgID) :
                      current.getRoles());
      return merged;
   }

   static SecurityRole mergeRole(SecurityRole current, IdentitySpec spec, IdentityID currentId) {
      SecurityRole merged = new SecurityRole();
      merged.setIdentityID(new IdentityID(renamedOrCurrent(spec.getName(), currentId.name),
                                          currentId.orgID));
      merged.setDescription(spec.getDescription() != null ? spec.getDescription() :
                            current.getDescription());
      merged.setTheme(spec.getTheme() != null ? spec.getTheme() : current.getTheme());
      merged.setAssignedUsers(spec.getAssignedUsers() != null ? spec.getAssignedUsers() :
                              current.getAssignedUsers());
      merged.setAssignedGroups(spec.getAssignedGroups() != null ? spec.getAssignedGroups() :
                               current.getAssignedGroups());
      merged.setInheritedRoles(spec.getInheritedRoles() != null ?
                               toIdentityIds(spec.getInheritedRoles(), currentId.orgID) :
                               current.getInheritedRoles());
      return merged;
   }

   /**
    * {@code currentOrgId} is always the id this method sets on the merged DTO -- {@code
    * spec.getId()} is never read here, even defensively: it is refused at validation time
    * (section 2.3 item 3) before the merge ever runs, so it is guaranteed absent by the time this
    * runs; the merge still never reads it, as defense in depth.
    */
   static SecurityOrganization mergeOrganization(SecurityOrganization current, IdentitySpec spec,
                                                 String currentOrgId)
   {
      SecurityOrganization merged = new SecurityOrganization();
      merged.setId(currentOrgId);
      merged.setName(spec.getOrgName() != null ? spec.getOrgName() : current.getName());
      merged.setLocale(spec.getLocale() != null ? spec.getLocale() : current.getLocale());
      merged.setTheme(spec.getTheme() != null ? spec.getTheme() : current.getTheme());
      // Organization's IdentitySpec has never carried membership fields (section 1/3.1) -- always
      // copied from current verbatim, never read from spec.
      merged.setMemberUsers(current.getMemberUsers());
      merged.setMemberGroups(current.getMemberGroups());
      merged.setRoles(current.getRoles());
      return merged;
   }

   /** Moved from {@code IdentityChangesetApplyService} (design section 3.1's explicit
    * recommendation) so there is exactly one implementation, now shared by {@code create}'s and
    * {@code update}'s merge paths. */
   static List<IdentityID> toIdentityIds(List<String> names, String orgId) {
      if(names == null) {
         return null;
      }

      return names.stream().map(n -> new IdentityID(n, orgId)).collect(Collectors.toList());
   }

   private static String renamedOrCurrent(String specName, String currentName) {
      return isBlank(specName) ? currentName : specName.trim();
   }

   private static boolean isBlank(String s) {
      return s == null || s.trim().isEmpty();
   }
}
