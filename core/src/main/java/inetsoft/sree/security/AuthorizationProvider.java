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
package inetsoft.sree.security;

import inetsoft.util.Tuple3;

import java.util.List;

/**
 * Interface for classes that provide access control services to a security
 * provider.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public interface AuthorizationProvider
   extends AuthenticationChangeListener, JsonConfigurableProvider, CachableProvider
{
   /**
    * Sets the permissions for the specified resource.
    *
    * @param type     the type of resource.
    * @param resource the resource name, such as a replet register name or a saved report path.
    * @param perm     the granted permissions.
    */
   void setPermission(ResourceType type, String resource, Permission perm);

   /**
    * Sets the permissions for the specified identity.
    *
    * @param type       the type of resource.
    * @param identityID the identity id to set permissions for
    * @param perm       the granted permissions.
    */
   void setPermission(ResourceType type, IdentityID identityID, Permission perm);

   /**
    * Clears all permissions on the specified resource.
    *
    * @param type     the resource type.
    * @param resource the resource name, such as a replet path or a saved report path.
    */
   void removePermission(ResourceType type, String resource);

   /**
    * Clears all permissions on the specified identity.
    *
    * @param type     the resource type.
    * @param identityID the identity, such as a specified user or role.
    */
   void removePermission(ResourceType type, IdentityID identityID);

   /**
    * Gets the permissions for the specified resource.
    *
    * @param type     the resource type.
    * @param resource the resource name.
    *
    * @return the granted permissions or {@code null} of none have been set.
    */
   Permission getPermission(ResourceType type, String resource);

   /**
    * Gets the permissions for the specified identity.
    *
    * @param type       the resource type.
    * @param identityID the identity id.
    *
    * @return the granted permissions or {@code null} of none have been set.
    */
   Permission getPermission(ResourceType type, IdentityID identityID);

   /**
    * Gets the permissions for all resources
    *
    * @return the list of tuples corresponding to the resource name, their types, and their permissions
    */
   default List<Tuple3<ResourceType, String, Permission>> getPermissions() {
      throw new UnsupportedOperationException();
   }

   /**
    * Check if provider support to allocate permission to group or not.
    */
   boolean supportGroupPermission();

   /**
    * Tear down the security provider.
    */
   void tearDown();
}
