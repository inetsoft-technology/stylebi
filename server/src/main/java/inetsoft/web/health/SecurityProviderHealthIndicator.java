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

package inetsoft.web.health;

import inetsoft.util.health.*;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SecurityProviderHealthIndicator implements HealthIndicator {
   public SecurityProviderHealthIndicator() {
      service = SecurityProviderHealthService.getInstance();
   }

   @Override
   public Health health() {
      SecurityProviderStatus status = service.getStatus();

      if(status.isProviderDown()) {
         Map<String, Boolean> details = new HashMap<>();

         for(SecurityProviderState state : status.getProviders()) {
            details.put(state.getName(), state.isAvailable());
         }

         return Health.down().withDetails(details).build();
      }

      return Health.up().build();
   }

   private final SecurityProviderHealthService service;
}
