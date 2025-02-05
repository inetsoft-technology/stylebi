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

import inetsoft.util.StatusDumpService;
import inetsoft.util.health.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DeadlockHealthIndicator implements HealthIndicator {
   public DeadlockHealthIndicator() {
      service = DeadlockHealthService.getInstance();
   }

   @Override
   public Health health() {
      DeadlockStatus status = service.getStatus();

      if(status.getDeadlockedThreadCount() > 0) {
         Map<String, Map<String, String>> details = new HashMap<>();

         for(DeadlockedThread thread : status.getDeadlockedThreads()) {
            Map<String, String> threadDetails = new HashMap<>();
            details.put(thread.getThreadName(), threadDetails);
            threadDetails.put("lockName", thread.getLockName());
            threadDetails.put("lockOwnerName", thread.getLockOwnerName());
         }

         LoggerFactory.getLogger(getClass()).error(
            "DeadlockHealthIndicator DOWN: details={}", details);
         StatusDumpService.getInstance().dumpStatus();
         return Health.down().withDetails(details).build();
      }

      return Health.up().build();
   }

   private final DeadlockHealthService service;
}
