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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * SecurityRoleList contains a list of security roles.
 */
@Validated
@Schema(description = "A list of security roles.")
public class SecurityRoleList {
   /**
    * Gets the list of roles.
    *
    * @return the role list.
    */
   @NotNull
   @Schema(description = "The security roles.")
   public List<SecurityRole> getRoles() {
      if(roles == null) {
         roles = new ArrayList<>();
      }

      return roles;
   }

   /**
    * Sets the list of roles.
    *
    * @param roles the role list.
    */
   public void setRoles(List<SecurityRole> roles) {
      this.roles = roles;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SecurityRoleList that = (SecurityRoleList) o;
      return Objects.equals(roles, that.roles);
   }

   @Override
   public int hashCode() {
      return Objects.hash(roles);
   }

   @Override
   public String toString() {
      return "SecurityRoleList{" +
         "roles=" + roles +
         '}';
   }

   private List<SecurityRole> roles;
}
