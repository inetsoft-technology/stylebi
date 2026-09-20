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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
      // Requires current.getDefaultRole()/getSysAdmin()/getOrgAdmin() to already be populated by
      // SecurityService.getRoleModel (the read-path fix) -- landing this merge fix before that
      // read-path fix would make current.getDefaultRole()/getSysAdmin()/getOrgAdmin() always null
      // here, silently resetting an existing defaultRole:true/sysAdmin:true/orgAdmin:true role to
      // false on any unrelated update.
      merged.setDefaultRole(spec.getDefaultRole() != null ? spec.getDefaultRole() :
                            current.getDefaultRole());
      merged.setSysAdmin(spec.getSysAdmin() != null ? spec.getSysAdmin() : current.getSysAdmin());
      merged.setOrgAdmin(spec.getOrgAdmin() != null ? spec.getOrgAdmin() : current.getOrgAdmin());
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
      // Requires current.getProperties() to already be populated by
      // SecurityService.getOrganizationModel (the read-path fix) -- same ordering dependency as
      // mergeRole's defaultRole/sysAdmin above.
      merged.setProperties(mergeProperties(spec.getProperties(), current.getProperties()));
      return merged;
   }

   private static final List<String> QUOTA_PROPERTY_KEYS =
      List.of("max.row.count", "max.col.count", "max.cell.size", "max.user.count");

   /**
    * {@code spec.getProperties()}'s value if present (never {@code current}'s, so an explicit empty
    * list really does clear every property, matching every other list field's clear-with-empty-list
    * convention), de-duplicated by last-write-wins per name (design section 3, human decision 5) --
    * BUT with the 4 named quota keys' CURRENT value re-included whenever {@code spec} is present but
    * silent on them. This is where human decision 4 is implemented: {@code
    * SecurityService.applyOrganizationProperties}'s own EM-parity clear-on-omission behavior for
    * those 4 keys would otherwise silently clear them the moment a caller updates {@code properties}
    * without re-listing every quota key it never meant to touch -- deliberately NOT replicating that
    * asymmetry for this admin-chat identities path, done here (server-side) rather than in wiz's
    * normalizer, since {@code current} is already available at exactly this merge boundary.
    */
   private static List<PropertyModel> mergeProperties(List<PropertyModel> specProperties,
                                                       List<PropertyModel> currentProperties)
   {
      if(specProperties == null) {
         return currentProperties;
      }

      Map<String, PropertyModel> merged = new LinkedHashMap<>();

      for(PropertyModel property : specProperties) {
         merged.put(property.name(), property);
      }

      if(currentProperties != null) {
         for(PropertyModel property : currentProperties) {
            if(QUOTA_PROPERTY_KEYS.contains(property.name()) && !merged.containsKey(property.name())) {
               merged.put(property.name(), property);
            }
         }
      }

      return new ArrayList<>(merged.values());
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
