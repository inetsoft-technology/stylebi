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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * {@code PermissionGrant} contains a permission granted to an identity on a resource.
 */
@Schema(description = "A permission granted to an identity on a resource.")
public class PermissionGrant {
   /**
    * Gets the name of the identity to which the permission is granted.
    *
    * @return the name of the identity.
    */
   @NotNull
   @Schema(
      description = "The IdentityID of the identity to which the permission is granted.",
      implementation = PermissionIdentityID.class,
      example = "{\"name\":\"role0\",\"orgID\":\"organization0\"}"
   )
   public IdentityID getIdentityID() {
      return identityID;
   }

   /**
    * Sets the name of the identity to which the permission is granted.
    *
    * @param identityID the identityID of the identity.
    */
   public void setIdentityID(IdentityID identityID) {
      this.identityID = identityID;
   }

   /**
    * Gets the type of identity to which the permission is granted.
    *
    * @return the identity type.
    */
   @NotNull
   @Schema(
      description = "The type of identity to which the permission is granted.",
      allowableValues = { "ROLE", "GROUP", "USER" },
      example = "ROLE")
   public String getType() {
      return type;
   }

   /**
    * Sets the type of identity to which the permission is granted.
    *
    * @param type the identity type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Gets the list of actions to which the identity is granted. The allowable actions is dependent
    * on the type of resource.
    *
    * @return the granted actions.
    */
   @NotNull
   @Schema(
      description = "The list of actions to which the identity is granted. The allowable actions are dependent on the type of resource.",
      allowableValues = { "READ", "WRITE", "DELETE", "ACCESS", "SHARE", "ASSIGN", "ADMIN" },
      example = "[ \"READ\" ]")
   public List<String> getActions() {
      return actions;
   }

   /**
    * Sets the list of actions to which the identity is granted. The allowable actions is dependent
    *     * on the type of resource.
    *
    * @param actions the granted actions.
    */
   public void setActions(List<String> actions) {
      this.actions = actions;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      PermissionGrant that = (PermissionGrant) o;
      return Objects.equals(identityID, that.identityID) &&
         Objects.equals(type, that.type) &&
         Objects.equals(actions, that.actions);
   }

   @Override
   public int hashCode() {
      return Objects.hash(identityID, type, actions);
   }

   @Override
   public String toString() {
      return "PermissionGrant{" +
         "name='" + identityID + '\'' +
         ", type='" + type + '\'' +
         ", actions=" + actions +
         '}';
   }

   private IdentityID identityID;
   private String type;
   private List<String> actions;
}
