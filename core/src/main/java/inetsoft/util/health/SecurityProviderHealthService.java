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

package inetsoft.util.health;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SecurityProviderHealthService {
   public static SecurityProviderHealthService getInstance() {
      return SingletonManager.getInstance(SecurityProviderHealthService.class);
   }

   public SecurityProviderStatus getStatus() {
      SecurityProviderStatus status = null;
      boolean enabled = "true".equals(SreeEnv.getProperty("health.securityProviders.enabled"));

      if(enabled) {
         SecurityEngine engine = SecurityEngine.getSecurity();
         Optional<AuthenticationChain> chain = engine.getAuthenticationChain();

         if(chain.isPresent()) {
            List<SecurityProviderState> states = new ArrayList<>();

            for(AuthenticationProvider provider : chain.get().getProviders()) {
               boolean available = true;

               if(provider instanceof DatabaseAuthenticationProvider dap) {
                  try {
                     dap.testConnection();
                  }
                  catch(Exception e) {
                     LOG.error("Security provider \"{}\" is down", provider.getProviderName(), e);
                     available = false;
                  }
               }
               else {
                  try {
                     provider.checkParameters();
                  }
                  catch(Exception e) {
                     LOG.error("Security provider \"{}\" is down", provider.getProviderName(), e);
                     available = false;
                  }
               }

               states.add(new SecurityProviderState(provider.getProviderName(), available));
            }

            status = new SecurityProviderStatus(
               states.size(), states.toArray(new SecurityProviderState[0]));
         }
      }

      if(status == null) {
         status = new SecurityProviderStatus(0, new SecurityProviderState[0]);
      }

      return status;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SecurityProviderHealthService.class);
}
