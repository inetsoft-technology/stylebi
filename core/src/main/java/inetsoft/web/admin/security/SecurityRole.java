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
package inetsoft.web.admin.security;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

/**
 * {@code CreateScheduleTaskRequest} contains the properties of a security role.
 */
@Validated
@Schema(description = "The properties of a security role.")
public class SecurityRole {
   /**
    * Gets the description of the role.
    *
    * @return the role name.
    */
   @Schema(description = "The description of the role.", example = "Text to describe the role")
   public String getDescription() {
      return description;
   }

   /**
    * Sets the description of the role.
    *
    * @param description the role description.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets the theme used by the role.
    *
    * @return the theme used by the role.
    */
   @Schema(description = "The theme used by the role.")
   public String getTheme() {
      return theme;
   }

   /**
    * Sets the theme used by the role.
    *
    * @param theme the theme used by the role.
    */
   public void setTheme(String theme) {
      this.theme = theme;
   }

   /**
    * Gets the users that will be assigned this role.
    *
    * @return the users assigned this role.
    */
   @Schema(description = "The list of users that will be assigned this role. For a global " +
      "role, a user of another organization than the current one is given as " +
      "name~;~organizationID, as getting the role reports it; one that the caller may not " +
      "administer is not reported and is kept on update. An update replaces the role's users " +
      "in the current organization, in each organization named by key and in each " +
      "organization of assigneeOrganizations, and keeps them in every other organization. " +
      "So the last user of another organization is removed by leaving it out only when " +
      "assigneeOrganizations names its organization, as it does in a role that was got, and " +
      "an empty list without assigneeOrganizations clears only the current organization.",
      example = "[ \"user0\", \"user1\" ]")
   public List<String> getAssignedUsers() {
      return assignedUsers;
   }

   /**
    * Sets the users that will be assigned this role.
    *
    * @param assignedUsers the users assigned this role.
    */
   public void setAssignedUsers(List<String> assignedUsers) {
      this.assignedUsers = assignedUsers;
   }

   /**
    * Gets the groups that will be assigned this role.
    *
    * @return the groups assigned this role.
    */
   @Schema(description = "The list of groups that will be assigned this role. For a global " +
      "role, a group of another organization than the current one is given as " +
      "name~;~organizationID, as getting the role reports it; one that the caller may not " +
      "administer is not reported and is kept on update. An update replaces the role's groups " +
      "in the current organization, in each organization named by key and in each " +
      "organization of assigneeOrganizations, and keeps them in every other organization. " +
      "So the last group of another organization is removed by leaving it out only when " +
      "assigneeOrganizations names its organization, as it does in a role that was got, and " +
      "an empty list without assigneeOrganizations clears only the current organization.",
      example = "[ \"group0\", \"group1\" ]")
   public List<String> getAssignedGroups() {
      return assignedGroups;
   }

   /**
    * Sets the groups that will be assigned this role.
    *
    * @param assignedGroups the groups assigned this role.
    */
   public void setAssignedGroups(List<String> assignedGroups) {
      this.assignedGroups = assignedGroups;
   }

   /**
    * Gets the organizations whose users and groups a global role's assigned user and group
    * lists replace on update.
    *
    * @return the organization ids, or <tt>null</tt> to replace only those of the current
    *         organization and of the organizations named by key.
    */
   @ArraySchema(arraySchema = @Schema(description = "For a global role, the organizations " +
      "whose users and groups assignedUsers and assignedGroups replace on update, in addition " +
      "to the current organization and the organizations named by key. Getting or listing a " +
      "global role fills it with the organizations of the users and groups it reports, so " +
      "that leaving out the last user or group of an organization removes the role from it. " +
      "A user or group that the caller may not administer is still kept, and an " +
      "organization without users or groups that hold the role changes nothing. When a role " +
      "that was got is reused with a replaced list, the role is removed from the users and " +
      "groups of these organizations that the new list leaves out, including an organization " +
      "named only for the other list; set this to null to keep them. It is ignored for a " +
      "role of an organization, for which getting the role gives null.",
      example = "[ \"organization0\", \"organization1\" ]"))
   public List<String> getAssigneeOrganizations() {
      return assigneeOrganizations;
   }

   /**
    * Sets the organizations whose users and groups a global role's assigned user and group
    * lists replace on update.
    *
    * @param assigneeOrganizations the organization ids.
    */
   public void setAssigneeOrganizations(List<String> assigneeOrganizations) {
      this.assigneeOrganizations = assigneeOrganizations;
   }

   /**
    * Gets the roles to inherit from.
    *
    * @return the roles to inherit from.
    */
   @ArraySchema(arraySchema = @Schema(description = "A list of roles that this role will inherit permissions from.",
      example = "[{\"name\":\"role0\",\"orgID\":\"organization0\"}]"))
   public List<IdentityID> getInheritedRoles() {
      return inheritedRoles;
   }

   /**
    * Sets the roles to inherit from.
    *
    * @param inheritedRoles the roles to inherit from.
    */
   public void setInheritedRoles(List<IdentityID> inheritedRoles) {
      this.inheritedRoles = inheritedRoles;
   }

   /**
    * Gets whether this role is automatically assigned to every newly-created user.
    *
    * @return {@code true} if this is a default role, {@code false} if not, or {@code null} if
    * unspecified.
    */
   @Schema(description = "Whether this role is automatically assigned to every newly-created user.")
   public Boolean getDefaultRole() {
      return defaultRole;
   }

   /**
    * Sets whether this role is automatically assigned to every newly-created user.
    *
    * @param defaultRole {@code true}/{@code false} to set, or {@code null} to leave unspecified.
    */
   public void setDefaultRole(Boolean defaultRole) {
      this.defaultRole = defaultRole;
   }

   /**
    * Gets whether this role designates the System Administrator role.
    *
    * @return {@code true} if this is the System Administrator role, {@code false} if not, or
    * {@code null} if unspecified.
    */
   @Schema(description = "Whether this role designates the System Administrator role.")
   public Boolean getSysAdmin() {
      return sysAdmin;
   }

   /**
    * Sets whether this role designates the System Administrator role.
    *
    * @param sysAdmin {@code true}/{@code false} to set, or {@code null} to leave unspecified.
    */
   public void setSysAdmin(Boolean sysAdmin) {
      this.sysAdmin = sysAdmin;
   }

   /**
    * Gets whether this role designates an Organization Administrator role.
    *
    * @return {@code true} if this is an Organization Administrator role, {@code false} if not, or
    * {@code null} if unspecified.
    */
   @Schema(description = "Whether this role designates an Organization Administrator role.")
   public Boolean getOrgAdmin() {
      return orgAdmin;
   }

   /**
    * Sets whether this role designates an Organization Administrator role.
    *
    * @param orgAdmin {@code true}/{@code false} to set, or {@code null} to leave unspecified.
    */
   public void setOrgAdmin(Boolean orgAdmin) {
      this.orgAdmin = orgAdmin;
   }

   /**
    * Sets the identities with admin permission over the user
    *
    * @return the identities.
    */
   @Schema(description = "The identities that may assign this role to users and groups (the " +
      "ASSIGN permission on the role). An update replaces them; a role created with this list " +
      "gives the listed identities ASSIGN and the creator admin permission. Identities given " +
      "admin permission on the role by a create before this field was stored as ASSIGN are " +
      "not listed and are not changed by an update. List and remove them with the permission " +
      "API on the resource name~;~organizationID of type SECURITY_ROLE, with orgId set to the " +
      "role's organization: GET /api/public/security/permissions/SECURITY_ROLE?resource=..." +
      "&orgId=..., then PUT /api/public/security/permissions?orgId=... or DELETE " +
      "/api/public/security/permissions/SECURITY_ROLE/{idType}/{idName}?resource=...&orgId=.... " +
      "For a global role, including the global roles in the role list, these are the " +
      "identities of the caller's current organization.")
   public AdminIdentities getAdminIdentities() {
      return adminIdentities;
   }

   /**
    * Sets the identities with admin permission over the user
    *
    * @param adminIdentities the identities.
    */
   public void setAdminIdentities(AdminIdentities adminIdentities) {
      this.adminIdentities = adminIdentities;
   }


   /**
    * get the identityID for the role
    * @return identityID of the role
    */
   @Schema(
      description = "The role's identity id.",
      implementation = RoleIdentityID.class,
      example = "{\"name\":\"role0\",\"orgID\":\"organization0\"}"
   )
   public IdentityID getIdentityID() {
      return this.identityID;
   }

   /**
    * set the identityID for the role
    * @param id the id to set this role
    */
   public void setIdentityID(IdentityID id) {
      this.identityID = id;
   }


   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SecurityRole that = (SecurityRole) o;

      return identityID.name.equals(that.identityID.name) &&
         (description == null || description.equals(that.description)) &&
         (theme == null || theme.equals(that.theme)) &&
         (assignedUsers == null || assignedUsers.equals(that.assignedUsers)) &&
         (assignedGroups == null || assignedGroups.equals(that.assignedGroups)) &&
         (assigneeOrganizations == null ||
            assigneeOrganizations.equals(that.assigneeOrganizations)) &&
         (inheritedRoles == null || inheritedRoles.equals(that.inheritedRoles)) &&
         (adminIdentities == null || adminIdentities.equals(that.adminIdentities)) &&
         (defaultRole == null || defaultRole.equals(that.defaultRole)) &&
         (sysAdmin == null || sysAdmin.equals(that.sysAdmin)) &&
         (orgAdmin == null || orgAdmin.equals(that.orgAdmin));
   }

   @Override
   public int hashCode() {
      return Objects.hash(identityID.name, description, theme, assignedUsers, assignedGroups,
                          assigneeOrganizations, inheritedRoles, adminIdentities, defaultRole, sysAdmin, orgAdmin);
   }

   @Override
   public String toString() {
      return "SecurityRole{" +
         "name='" + identityID.name + '\'' +
         ", orgID='" + identityID.orgID + '\'' +
         ", description='" + description + '\'' +
         ", theme='" + theme + '\'' +
         ", assignedUsers=" + assignedUsers +
         ", assignedGroups=" + assignedGroups +
         ", assigneeOrganizations=" + assigneeOrganizations +
         ", inheritedRoles=" + inheritedRoles +
         ", adminIdentities=" + adminIdentities +
         ", defaultRole=" + defaultRole +
         ", sysAdmin=" + sysAdmin +
         ", orgAdmin=" + orgAdmin +
         '}';
   }

   private IdentityID identityID;
   private String description;
   private String theme;
   private List<String> assignedUsers;
   private List<String> assignedGroups;
   private List<String> assigneeOrganizations;
   private List<IdentityID> inheritedRoles;
   private AdminIdentities adminIdentities;
   private Boolean defaultRole;
   private Boolean sysAdmin;
   private Boolean orgAdmin;
}
