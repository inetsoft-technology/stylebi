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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

public class AdminIdentities {
   /**
    * Gets the users that have admin permission over the identity.
    * Does not need to include current the api user, as the creator user will always be appended on.
    *
    * @return the users with admin permission over the identity.
    */
   @ArraySchema(arraySchema = @Schema(description = "The list of the users with admin permission over the identity." +
      "Does not need to include the current api user, as the creator user will always be appended on.",
      example = "[{\"name\":\"user0\",\"orgID\":\"organization0\"}]"))
   public List<IdentityID> getUsers() {
      return users;
   }

   /**
    * Sets the users that have admin permission over the identity.
    *
    * @param users the users with admin permission over the identity.
    */
   public void setUsers(List<IdentityID> users) {
      this.users = users;
   }

   /**
    * Gets the groups that have admin permission over the identity.
    *
    * @return the groups with admin permission over the identity.
    */
   @ArraySchema(arraySchema = @Schema(description = "The list of the groups with admin permission over the identity.",
      example = "[{\"name\":\"group0\",\"orgID\":\"organization0\"}]"))
   public List<IdentityID> getGroups() {
      return groups;
   }

   /**
    * Sets the groups that have admin permission over the identity.
    *
    * @param groups the groups with admin permission over the identity.
    */
   public void setGroups(List<IdentityID> groups) {
      this.groups = groups;
   }

   /**
    * Gets the roles that have admin permission over the identity.
    *
    * @return the roles with admin permission over the identity.
    */
   @ArraySchema(arraySchema = @Schema(description = "The list of the roles with admin permission over the identity.",
      example = "[{\"name\":\"role0\",\"orgID\":\"organization0\"}]"))
   public List<IdentityID> getRoles() {
      return roles;
   }

   /**
    * Sets the roles that have admin permission over the identity.
    *
    * @param roles the roles with admin permission over the identity.
    */
   public void setRoles(List<IdentityID> roles) {
      this.roles = roles;
   }

   /**
    * Gets the organizations that have admin permission over the identity.
    *
    * @return the organizations with admin permission over the identity.
    */
   @Schema(description = "The list of the organizations with admin permission over the identity.",
      example = "[ \"organization0\"]")
   public List<String> getOrganizations() {
      return organizations;
   }

   /**
    * Sets the organizations that have admin permission over the identity.
    *
    * @param organizations the organizations with admin permission over the identity.
    */
   public void setOrganizations(List<String> organizations) {
      this.organizations = organizations;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      AdminIdentities that = (AdminIdentities) o;

      return (users == null || users.equals(that.users)) &&
         (groups == null || groups.equals(that.groups)) &&
         (organizations == null || organizations.equals(that.organizations)) &&
         (roles == null || roles.equals(that.roles));
   }

   @Override
   public int hashCode() {
      return Objects.hash(users, groups, roles, organizations);
   }

   @Override
   public String toString() {
      return "AdminIdentities{" +
         "users=" + users +
         ", groups=" + groups +
         ", roles=" + roles +
         ", organizations=" + organizations +
         '}';
   }

   private List<IdentityID> users;
   private List<IdentityID> groups;
   private List<IdentityID> roles;
   private List<String> organizations;
}
