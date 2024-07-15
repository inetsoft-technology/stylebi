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
package inetsoft.web.health;

import inetsoft.util.health.CacheSwapHealthService;
import inetsoft.util.health.CacheSwapStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CacheSwapHealthIndicator implements HealthIndicator {
   public CacheSwapHealthIndicator() {
      this.service = CacheSwapHealthService.getInstance();
   }

   @Override
   public Health health() {
      CacheSwapStatus status = service.getStatus();

      if(status.isCriticalMemory() || status.isExcessiveWaiting()) {
         return Health.down()
            .withDetail("criticalMemory", status.isCriticalMemory())
            .withDetail("criticalMemoryStart", status.getCriticalMemoryStart())
            .withDetail("excessiveWaiting", status.isExcessiveWaiting())
            .withDetail("excessiveWaitingStart", status.getExcessiveWaitingStart())
            .build();
      }

      return Health.up().build();
   }

   private final CacheSwapHealthService service;
}
