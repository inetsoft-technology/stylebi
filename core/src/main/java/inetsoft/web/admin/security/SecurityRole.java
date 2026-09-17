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
   @Schema(description = "The list of users that will be assigned this role.",
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
   @Schema(description = "The list of groups that will be assigned this role.",
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
    * Sets the identities with admin permission over the user
    *
    * @return the identities.
    */
   @Schema(description = "A list of identities with admin permission over the user.")
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
         (inheritedRoles == null || inheritedRoles.equals(that.inheritedRoles)) &&
         (adminIdentities == null || adminIdentities.equals(that.adminIdentities));
   }

   @Override
   public int hashCode() {
      return Objects.hash(identityID.name, description, theme, assignedUsers, assignedGroups, inheritedRoles, adminIdentities);
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
         ", inheritedRoles=" + inheritedRoles +
         ", adminIdentities=" + adminIdentities +
         '}';
   }

   private IdentityID identityID;
   private String description;
   private String theme;
   private List<String> assignedUsers;
   private List<String> assignedGroups;
   private List<IdentityID> inheritedRoles;
   private AdminIdentities adminIdentities;
}
