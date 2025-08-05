/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.ldap;

import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.sree.security.*;

import java.util.Objects;

public class LoadCacheTask implements SingletonCallableTask<Long> {
   public LoadCacheTask(String providerName, boolean force) {
      this.providerName = providerName;
      this.force = force;
   }

   @Override
   public Long call() throws Exception {
      return getProvider().getCache().load(force).get();
   }

   private LdapAuthenticationProvider getProvider() {
      SecurityProvider root = SecurityEngine.getSecurity().getSecurityProvider();

      if(root == null) {
         throw new IllegalStateException("Security not configured");
      }

      AuthenticationProvider rootAuthentication = root.getAuthenticationProvider();
      LdapAuthenticationProvider provider = null;

      if(rootAuthentication instanceof LdapAuthenticationProvider ldap &&
         Objects.equals(providerName, ldap.getProviderName()))
      {
         provider = ldap;
      }
      else if(rootAuthentication instanceof AuthenticationChain chain) {
         for(AuthenticationProvider child : chain.getProviders()) {
            if(child instanceof LdapAuthenticationProvider ldap &&
               Objects.equals(providerName, ldap.getProviderName()))
            {
               provider = ldap;
               break;
            }
         }
      }

      if(provider == null) {
         throw new IllegalStateException("Security provider not found");
      }

      return provider;
   }

   private final String providerName;
   private final boolean force;
}
