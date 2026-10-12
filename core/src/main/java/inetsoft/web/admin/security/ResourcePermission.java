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

   /**
    * Gets whether the resource derives its permission from its parent for the organization (the
    * "Derive Permissions from Parent" option in the Enterprise Manager).
    *
    * @return {@code true} if the resource derives from its parent, {@code false} if only its own
    *         grants apply, or {@code null} if not set.
    */
   @Schema(
      description = "Whether the resource derives its permission from its parent resource (the " +
         "\"Derive Permissions from Parent\" option in the Enterprise Manager). When true, the " +
         "grants of the resource only add to those of its parent (or, for some action types, to " +
         "the default of allowing everyone), so they restrict nobody. When false, only the " +
         "grants of the resource apply, and false with no grants denies everyone without an " +
         "administrator permission. On a write, an omitted (null) value means false when any " +
         "grant applies to the resource after the write and true otherwise. Always set on a " +
         "read.",
      example = "false")
   public Boolean getInheritFromParent() {
      return inheritFromParent;
   }

   /**
    * Sets whether the resource derives its permission from its parent for the organization.
    *
    * @param inheritFromParent {@code true} to derive from the parent, {@code false} to apply only
    *                          the resource's own grants, or {@code null} to derive only when no
    *                          grant applies after the write.
    */
   public void setInheritFromParent(Boolean inheritFromParent) {
      this.inheritFromParent = inheritFromParent;
   }

   @Override
   public int hashCode() {
      return Objects.hash(resource, resourceType, grants, inheritFromParent);
   }

   @Override
   public String toString() {
      return "PermissionGrant{" +
         "resource='" + resource + '\'' +
         ", resourceType='" + resourceType + '\'' +
         ", grants=" + grants +
         ", inheritFromParent=" + inheritFromParent +
         '}';
   }

   String resource;
   String resourceType;
   List<PermissionGrant> grants;
   Boolean inheritFromParent;
}
