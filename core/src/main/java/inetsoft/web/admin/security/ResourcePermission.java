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

import java.util.List;
import java.util.Objects;

/**
 * {@code ResourcePermission} contains a permission resource and grants on the list of grants on that resource
 */
@Schema(description = "A permission granted to an identity on a resource.")
public class ResourcePermission {
   /**
    * Gets the path of the resource managed by this permission
    *
    * @return the path of the resource.
    */
   @NotNull
   @Schema(
      description = "The path of the resource managed by this permission.",
      example = "Examples/Census")
   public String getResource() {
      return resource;
   }

   /**
    * Sets the path of the resource managed by this permission
    *
    * @param resource the path of the resource
    */
   public void setResource(String resource) {
      this.resource = resource;
   }


   /**
    * Gets the type of resource managed by this permission
    *
    * @return the type of resource.
    */
   @NotNull
   @Schema(
      description = "The type of resource managed by this permission.",
      example = "REPORT")
   public String getResourceType() {
      return resourceType;
   }

   /**
    * Sets the type of resource managed by this permission
    *
    * @param resourceType the type of resource
    */
   public void setResourceType(String resourceType) {
      this.resourceType = resourceType;
   }


   /**
    * Gets the permission grants of this resource
    *
    * @return the permission grants.
    */
   @NotNull
   @Schema(
      description = "The type of resource managed by this permission.")
   public List<PermissionGrant> getPermissionGrants() {
      return grants;
   }

   /**
    * Sets the permission grants of this resource
    *
    * @param grants the permission grants
    */
   public void setPermissionGrants(List<PermissionGrant> grants) {
      this.grants = grants;
   }

   @Override
   public int hashCode() {
      return Objects.hash(resource, resourceType, grants);
   }

   @Override
   public String toString() {
      return "PermissionGrant{" +
         "resource='" + resource + '\'' +
         ", resourceType='" + resourceType + '\'' +
         ", grants=" + grants +
         '}';
   }

   String resource;
   String resourceType;
   List<PermissionGrant> grants;
}
