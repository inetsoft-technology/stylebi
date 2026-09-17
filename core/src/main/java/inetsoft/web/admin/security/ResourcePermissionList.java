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

import java.util.*;

public class ResourcePermissionList {
   /**
    * Gets the list of permissions.
    *
    * @return the permission list.
    */
   @NotNull
   @Schema(description = "The permissions.")
   public List<ResourcePermission> getPermissions() {
      if(permissions == null) {
         permissions = new ArrayList<>();
      }

      return permissions;
   }

   /**
    * Sets the list of permissions.
    *
    * @param permissions the permission list.
    */
   public void setPermissions(List<ResourcePermission> permissions) {
      this.permissions = permissions;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ResourcePermissionList that = (ResourcePermissionList) o;
      return Objects.equals(permissions, that.permissions);
   }

   @Override
   public int hashCode() {
      return Objects.hash(permissions);
   }

   @Override
   public String toString() {
      return "ResourcePermissionList{" +
         "resourcePermissions=" + permissions +
         '}';
   }

   private List<ResourcePermission> permissions;
}
