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

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.security.Principal;

/**
 * Security provider implementation that wraps a arbitrary authentication and
 * authorization modules.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class CompositeSecurityProvider extends AbstractSecurityProvider {
   /**
    * Creates a new instance of CompositeSecurityProvider.
    *
    * @param authentication the authentication module.
    * @param authorization the authorization module.
    */
   private CompositeSecurityProvider(AuthenticationProvider authentication,
                                     AuthorizationProvider authorization)
   {
      super(authentication, authorization);
   }

   public static CompositeSecurityProvider create(AuthenticationProvider authentication,
                                                  AuthorizationProvider authorization)
   {
      final CompositeSecurityProvider provider =
         new CompositeSecurityProvider(authentication, authorization);
      final CheckPermissionStrategy strategy = createCheckPermissionStrategy(provider);
      provider.setCheckPermissionStrategy(strategy);
      return provider;
   }

   private static CheckPermissionStrategy createCheckPermissionStrategy(SecurityProvider provider) {
      final String className =
         SreeEnv.getProperty(CheckPermissionStrategy.class.getName());

      if(className != null) {
         try {
            return (CheckPermissionStrategy) Class.forName(className)
               .getConstructor(SecurityProvider.class)
               .newInstance(provider);
         }
         catch(Exception e) {
            LOG.error("Failed to instantiate {}. Using default check permission strategy", className, e);
         }
      }

      return new DefaultCheckPermissionStrategy(provider);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
