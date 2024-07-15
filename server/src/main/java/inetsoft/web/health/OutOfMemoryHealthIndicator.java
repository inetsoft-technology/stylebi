/*
 * inetsoft-server - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.health;

import inetsoft.util.health.OutOfMemoryHealthService;
import inetsoft.util.health.OutOfMemoryStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OutOfMemoryHealthIndicator implements HealthIndicator {
   public OutOfMemoryHealthIndicator() {
      service = OutOfMemoryHealthService.getInstance();
   }

   @Override
   public Health health() {
      OutOfMemoryStatus status = service.getStatus();

      if(status.isOutOfMemory()) {
         return Health.down().withDetail("time", status.getTime()).build();
      }

      return Health.up().build();
   }

   private final OutOfMemoryHealthService service;
}
