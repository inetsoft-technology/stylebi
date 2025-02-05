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

import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.util.StatusDumpService;
import inetsoft.util.health.HealthStatus;
import inetsoft.util.health.SchedulerStatus;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class SchedulerHealthIndicator implements HealthIndicator {
   @Override
   public Health health() {
      ScheduleClient client = ScheduleClient.getScheduleClient();

      if(client.isCloud() || client.isAutoStart()) {
         try {
            Optional<HealthStatus> status = client.getHealthStatus();

            if(status.isPresent()) {
               SchedulerStatus health = status.get().getSchedulerStatus();

               if(!health.isHealthy()) {
                  LoggerFactory.getLogger(getClass()).error(
                     "SchedulerHealthIndicator DOWN: status={}", health);
                  StatusDumpService.getInstance().dumpStatus();
                  return Health.down()
                     .withDetail("started", health.isStarted())
                     .withDetail("shutdown", health.isShutdown())
                     .withDetail("standby", health.isStandby())
                     .withDetail("lastCheck", Instant.ofEpochMilli(health.getLastCheck()).toString())
                     .withDetail("executingCount", health.getExecutingCount())
                     .withDetail("threadCount", health.getThreadCount())
                     .build();
               }
            }
         }
         catch(Exception e) {
            LoggerFactory.getLogger(getClass()).error("Failed to get scheduler health", e);
         }
      }

      return Health.up().build();
   }
}
