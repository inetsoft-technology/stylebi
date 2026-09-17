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
 * {@code CreateScheduleTaskRequest} contains the properties of a security group.
 */
@Validated
@Schema(description = "The properties of a security group.")
public class SecurityGroup {
   /**
    * Gets the theme used by the group.
    *
    * @return the theme used by the group.
    */
   @Schema(description = "The theme used by the group.")
   public String getTheme() {
      return theme;
   }

   /**
    * Sets the theme used by the group.
    *
    * @param theme the theme used by the group.
    */
   public void setTheme(String theme) {
      this.theme = theme;
   }

   /**
    * Gets the organization ID of the group.
    *
    * @return the group's orgID.
    */
   @Schema(description = "The organization ID of the group.", example = "organization0")
   public String getOrgID() {
      if(identityID == null) {
         return null;
      }

      return identityID.orgID;
   }

   /**
    * Sets the organization ID of the group.
    *
    * @param orgID the group's orgID.
    */
   public void setOrgID(String orgID) {
      if(identityID == null) {
         identityID = new IdentityID("", orgID);
      }
      else {
         this.identityID.orgID = orgID;
      }
   }

   /**
    * Gets the groups that this group will be a member of.
    *
    * @return the groups.
    */
   @Schema(description = "The list of groups that this group will be a member of.",
      example = "[ \"group0\", \"group1\" ]")
   public List<String> getParentGroups() {
      return parentGroups;
   }

   /**
    * Sets the groups that this group will be a member of.
    *
    * @param parentGroups the group members of this group.
    */
   public void setParentGroups(List<String> parentGroups) {
      this.parentGroups = parentGroups;
   }

   /**
    * Gets the users that will be members of this group.
    *
    * @return the user members of this group.
    */
   @Schema(description = "The list of users that will be members of the group.",
      example = "[ \"user0\", \"user1\" ]")
   public List<String> getMemberUsers() {
      return memberUsers;
   }

   /**
    * Gets the users that will be members of this group.
    *
    * @param memberUsers the user members of this group.
    */
   public void setMemberUsers(List<String> memberUsers) {
      this.memberUsers = memberUsers;
   }

   /**
    * Gets the groups that will be members of this group.
    *
    * @return the groups.
    */
   @Schema(description = "The list of groups that will be members of the group.",
      example = "[ \"group2\", \"group3\" ]")
   public List<String> getMemberGroups() {
      return memberGroups;
   }

   /**
    * Gets the group that will be members of this group.
    *
    * @param memberGroups the group members of this group.
    */
   public void setMemberGroups(List<String> memberGroups) {
      this.memberGroups = memberGroups;
   }

   /**
    * Gets the roles to be assigned to the group.
    *
    * @return the roles.
    */
   @ArraySchema(arraySchema = @Schema(description = "A list of roles to assign to this group.",
      example = "[{\"name\":\"role0\",\"orgID\":\"organization0\"}]"))
   public List<IdentityID> getRoles() {
      return roles;
   }

   /**
    * Sets the roles to be assigned to the user.
    *
    * @param roles the roles.
    */
   public void setRoles(List<IdentityID> roles) {
      this.roles = roles;
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
    * get the identityID for the group
    * @return identityID of the group
    */
   @Schema(
      description = "The group's identity id.",
      implementation = GroupIdentityID.class,
      example = "{\"name\":\"group0\",\"orgID\":\"organization0\"}"
   )
   public IdentityID getIdentityID() {
      return this.identityID;
   }

   /**
    * set the identityID for the group
    * @param id the id to set this user
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

      SecurityGroup that = (SecurityGroup) o;

      return identityID.name.equals(that.identityID.name) &&
         (theme == null || theme.equals(that.theme)) &&
         (parentGroups == null || parentGroups.equals(that.parentGroups)) &&
         (memberUsers == null || memberUsers.equals(that.memberUsers)) &&
         (memberGroups == null || memberGroups.equals(that.memberGroups)) &&
         (roles == null || roles.equals(that.roles)) &&
         (adminIdentities == null || adminIdentities.equals(that.adminIdentities));
   }

   @Override
   public int hashCode() {
      return Objects.hash(identityID.name, theme, parentGroups, memberUsers,
                          memberGroups, roles, adminIdentities);
   }

   @Override
   public String toString() {
      return "SecurityGroup{" +
         "name='" + identityID.name + '\'' +
         ", orgID='" + identityID.orgID + '\'' +
         ", theme='" + theme + '\'' +
         ", parentGroups=" + parentGroups +
         ", memberUsers=" + memberUsers +
         ", memberGroups=" + memberGroups +
         ", roles=" + roles +
         ", adminIdentities=" + adminIdentities +
         '}';
   }

   private IdentityID identityID;
   private String theme;
   private List<String> parentGroups;
   private List<String> memberUsers;
   private List<String> memberGroups;
   private List<IdentityID> roles;
   private AdminIdentities adminIdentities;
}
