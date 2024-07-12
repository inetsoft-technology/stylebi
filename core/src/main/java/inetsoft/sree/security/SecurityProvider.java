/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import java.security.Principal;
import java.util.EnumSet;

/**
 * This interface defines the API for security provider. A security provider provides information on
 * users, manages resource permission settings, and check permissions.
 */
public interface SecurityProvider extends AuthenticationProvider, AuthorizationProvider {
   /**
    * Checks if a user has been granted permission to perform an action on the specified resource.
    *
    * @param user     a principal identifying the user.
    * @param type     the type of resource.
    * @param resource the name of the resource.
    * @param action   the name of the action.
    */
   boolean checkPermission(Principal user, ResourceType type, String resource,
                           ResourceAction action);

   /**
    * Checks if the user has been granted permission to perform any of the specified actions on a
    * resource.
    *
    * @param user     a principal identifying the user.
    * @param type     the type of resource.
    * @param resource the name of the resource.
    * @param actions  the names of the actions.
    *
    * @return {@code true} if the user has been granted permission for any of the actions.
    */
   default boolean checkAnyPermission(Principal user, ResourceType type, String resource,
                                      EnumSet<ResourceAction> actions)
   {
      for(ResourceAction action : actions) {
         if(checkPermission(user, type, resource, action)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Checks if the user has been granted permission to perform all of the specified actions on a
    * resource.
    *
    * @param user     a principal identifying the user.
    * @param type     the type of resource.
    * @param resource the name of the resource.
    * @param actions  the names of the actions.
    *
    * @return {@code true} if the user has been granted permission for all of the actions.
    */
   default boolean checkAllPermissions(Principal user, ResourceType type, String resource,
                                       EnumSet<ResourceAction> actions)
   {
      for(ResourceAction action : actions) {
         if(!checkPermission(user, type, resource, action)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Gets the module that provides authentication support to this security
    * provider.
    *
    * @return the authentication module.
    */
   default AuthenticationProvider getAuthenticationProvider() {
      return this;
   }

   /**
    * Gets the module that provides authorization support to this security
    * provider.
    *
    * @return the authorization module.
    */
   default AuthorizationProvider getAuthorizationProvider() {
      return this;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   default boolean isCacheEnabled() {
      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   default void clearCache() {
   }

   /**
    * {@inheritDoc}
    */
   @Override
   default boolean isLoading() {
      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   default long getCacheAge() {
      return 0L;
   }
}
