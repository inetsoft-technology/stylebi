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

/**
 * A skeletal implementation of an authorization provider.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public abstract class AbstractAuthorizationProvider
   implements AuthorizationProvider
{
   @Override
   public void setPermission(ResourceType type, String resource, Permission perm) {
   }

   @Override
   public void setPermission(ResourceType type, IdentityID identityID, Permission perm) {
   }

   @Override
   public void removePermission(ResourceType type, String resource) {
   }

   @Override
   public void removePermission(ResourceType type, String resource, String orgID) {
   }

   @Override
   public void removePermission(ResourceType type, IdentityID identityID) {
   }

   @Override
   public Permission getPermission(ResourceType type, String resource) {
      return null;
   }

   @Override
   public Permission getPermission(ResourceType type, IdentityID identityID) {
      return null;
   }

   @Override
   public boolean supportGroupPermission() {
      return true;
   }

   @Override
   public String getProviderName() {
      return providerName;
   }

   @Override
   public void setProviderName(String providerName) {
      this.providerName = providerName;
   }

   private String providerName;
}
