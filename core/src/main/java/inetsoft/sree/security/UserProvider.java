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

import inetsoft.sree.SreeEnv;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * {@code UserProvider} is an interface for classes that obtain user information from an external
 * source when using SSO. The user provider is configured by setting the
 * {@code inetsoft.sree.security.UserProvider} property to the fully-qualified class name of the
 * implementing class.
 */
public interface UserProvider {
   /**
    * Gets information about the named user.
    *
    * @param userID the username.
    *
    * @return the user information or {@code null} if the user does not exist in the external
    * source.
    */
   User getUser(IdentityID userID);

   /**
    * Gets a list of all users.
    *
    * @return a list of all users or an empty list if no users are available.
    */
   default List<IdentityID> getUsers() {
      return Collections.emptyList();
   }

   /**
    * Performs any additional configuration of a principal that identifies a remote user.
    *
    * @param user      information about the user, typically obtained from @{link #getUser(String)}.
    * @param principal the principal to be configured.
    *
    * @return the configured principal. By default, the original principal is returned.
    */
   default SRPrincipal configurePrincipal(User user, SRPrincipal principal) {
      return principal;
   }

   /**
    * Gets an instance of the configured implementation of {@code UserProvider}.
    *
    * @return the provider instance or {@code null} if not configured.
    */
   static UserProvider getInstance() {
      String providerClass = SreeEnv.getProperty("inetsoft.sree.security.UserProvider");

      if(providerClass != null) {
         try {
            return (UserProvider) Class.forName(providerClass).getConstructor().newInstance();
         }
         catch(Exception e) {
            LoggerFactory.getLogger(UserProvider.class)
               .error("Failed to create instance of {}", providerClass, e);
         }
      }

      return null;
   }
}
