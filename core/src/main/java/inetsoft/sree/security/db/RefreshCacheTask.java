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

package inetsoft.sree.security.db;

import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;

import java.time.Duration;
import java.util.Objects;

public class RefreshCacheTask implements SingletonCallableTask<Long> {
   public RefreshCacheTask(String providerName, boolean force) {
      this.providerName = providerName;
      this.force = force;
   }

   @Override
   public Long call() throws Exception {
      DatabaseAuthenticationProvider provider = getProvider();
      DatabaseAuthenticationCache cache = provider.getCache(false);
      return cache.load(force);
   }

   private DatabaseAuthenticationProvider getProvider() {
      SecurityProvider root = SecurityEngine.getSecurity().getSecurityProvider();

      if(root == null) {
         throw new IllegalStateException("Security not configured");
      }

      AuthenticationProvider rootAuthentication = root.getAuthenticationProvider();
      DatabaseAuthenticationProvider provider = null;

      if(rootAuthentication instanceof DatabaseAuthenticationProvider db &&
         Objects.equals(providerName, db.getProviderName()))
      {
         provider = db;
      }
      else if(rootAuthentication instanceof AuthenticationChain chain) {
         for(AuthenticationProvider child : chain.getProviders()) {
            if(child instanceof DatabaseAuthenticationProvider db &&
               Objects.equals(providerName, db.getProviderName()))
            {
               provider = db;
               break;
            }
         }
      }

      if(provider == null) {
         throw new IllegalStateException(Tool.buildString("Security provider:", providerName, " not found"));
      }

      return provider;
   }

   private final String providerName;
   private final boolean force;
}
