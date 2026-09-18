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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.admin.security.PropertyModel;

import java.util.List;

/**
 * One flat carrier for every field any unit's {@code create} might populate (spec section 11).
 * Deliberately not four separate DTO subclasses -- there is no natural type discriminator inside
 * {@code spec} itself, so the "which fields are legal for this {@link IdentityUnitType}" decision
 * lives entirely in {@link IdentityChangePlanService#requireLegalFields}, one place, matching where
 * the hash/plan validation already has to happen. Every field not legal for the resolved unit type
 * must be {@code null}/absent -- checked explicitly there, not just ignored (spec section 11's
 * discriminator-confusion defense, the concrete answer to this repo's CLAUDE.md {@code fieldConfigs}
 * example).
 *
 * <p>{@code adminIdentities}-shaped grants are never part of this class -- refused categorically at
 * the tool layer and re-checked here only by virtue of this class having no such field to populate
 * (spec section 1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentitySpec {
   /** user, group, role: bare local name. organization: not used -- see {@link #getId()}. */
   public String getName() { return name; }
   public void setName(String v) { this.name = v; }

   /** user/group/role: defaults to the caller's current org when absent. Not used for
    * organization, which is identified by {@link #getId()} alone. */
   public String getOrgId() { return orgId; }
   public void setOrgId(String v) { this.orgId = v; }

   /** organization only: its id field, distinct from its display name (spec section 3). Required
    * for {@code unitType=organization}, refused for every other unit type. */
   public String getId() { return id; }
   public void setId(String v) { this.id = v; }

   /** organization only: its display name. */
   public String getOrgName() { return orgName; }
   public void setOrgName(String v) { this.orgName = v; }

   /** user only: required on create, never echoed back (spec section 9). */
   public String getPassword() { return password; }
   public void setPassword(String v) { this.password = v; }

   /** user only. */
   public String getAlias() { return alias; }
   public void setAlias(String v) { this.alias = v; }

   /** user/organization: locale name. */
   public String getLocale() { return locale; }
   public void setLocale(String v) { this.locale = v; }

   /** user only. {@code null} (not just {@code false}) means "not specified" -- unset vs. false
    * matters (spec section 11). */
   public Boolean getActive() { return active; }
   public void setActive(Boolean v) { this.active = v; }

   /** user only. */
   public List<String> getEmails() { return emails; }
   public void setEmails(List<String> v) { this.emails = v; }

   /** user only: existing groups to join. Silently filtered to ones the caller holds ADMIN over
    * (spec section 2/11 -- disclosed as a warning, not silently dropped without comment). */
   public List<String> getGroups() { return groups; }
   public void setGroups(List<String> v) { this.groups = v; }

   /** user/group/role: existing roles to assign/inherit. */
   public List<String> getRoles() { return roles; }
   public void setRoles(List<String> v) { this.roles = v; }

   /** group only: existing parent groups. */
   public List<String> getParentGroups() { return parentGroups; }
   public void setParentGroups(List<String> v) { this.parentGroups = v; }

   /** group only: existing users to add as members (spec section 2's correction -- allowed,
    * filtered to permitted+existing identities, never creates a new one). */
   public List<String> getMemberUsers() { return memberUsers; }
   public void setMemberUsers(List<String> v) { this.memberUsers = v; }

   /** group only: existing groups to add as members (same correction as memberUsers). */
   public List<String> getMemberGroups() { return memberGroups; }
   public void setMemberGroups(List<String> v) { this.memberGroups = v; }

   /** role only. */
   public String getDescription() { return description; }
   public void setDescription(String v) { this.description = v; }

   /** role only: existing roles this role inherits from. */
   public List<String> getInheritedRoles() { return inheritedRoles; }
   public void setInheritedRoles(List<String> v) { this.inheritedRoles = v; }

   /** role only: existing users to assign this role to (spec section 2's correction -- allowed;
    * also the mechanism delete role's rollback depends on, spec section 4/6). */
   public List<String> getAssignedUsers() { return assignedUsers; }
   public void setAssignedUsers(List<String> v) { this.assignedUsers = v; }

   /** role only: existing groups to assign this role to. */
   public List<String> getAssignedGroups() { return assignedGroups; }
   public void setAssignedGroups(List<String> v) { this.assignedGroups = v; }

   /** user/group/role/organization: theme name. */
   public String getTheme() { return theme; }
   public void setTheme(String v) { this.theme = v; }

   /** role only. {@code null} means "not specified" -- same unset-vs-false convention as
    * {@link #getActive()} (spec section 11). Marks the role as automatically assigned to every
    * newly-created user. */
   public Boolean getDefaultRole() { return defaultRole; }
   public void setDefaultRole(Boolean v) { this.defaultRole = v; }

   /** role only. {@code null} means "not specified", same convention as {@link #getDefaultRole()}.
    * Designates the role as the System Administrator role -- security-sensitive, same class as
    * Providers' {@code sysAdminRoles}. */
   public Boolean getSysAdmin() { return sysAdmin; }
   public void setSysAdmin(Boolean v) { this.sysAdmin = v; }

   /** organization only: org-scoped overrides into the shared global server-property namespace
    * (the same store this plugin's Properties area manages, namespaced per-organization) -- not
    * inert metadata. {@code null} means "not specified" (leave existing properties untouched);
    * an empty list clears every existing property. De-duplicated by last-write-wins per name if
    * two entries share a name. */
   public List<PropertyModel> getProperties() { return properties; }
   public void setProperties(List<PropertyModel> v) { this.properties = v; }

   private String name;
   private String orgId;
   private String id;
   private String orgName;
   private String password;
   private String alias;
   private String locale;
   private Boolean active;
   private List<String> emails;
   private List<String> groups;
   private List<String> roles;
   private List<String> parentGroups;
   private List<String> memberUsers;
   private List<String> memberGroups;
   private String description;
   private List<String> inheritedRoles;
   private List<String> assignedUsers;
   private List<String> assignedGroups;
   private String theme;
   private Boolean defaultRole;
   private Boolean sysAdmin;
   private List<PropertyModel> properties;
}
