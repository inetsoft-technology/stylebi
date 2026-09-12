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
   public SchedulerHealthIndicator(ScheduleClient client, StatusDumpService statusDumpService) {
      this.client = client;
      this.statusDumpService = statusDumpService;
   }

   @Override
   public Health health() {
      if(!(client.isCloud() || client.isAutoStart())) {
         // The scheduler runs as an independent process this node cannot reach
         // (e.g. a clustered install, or a non-cloud install with auto-start
         // disabled), so there is no way to determine its health from here.
         return unknown("scheduler health cannot be determined from this node");
      }

      try {
         Optional<HealthStatus> status = client.getHealthStatus();

         if(status.isPresent()) {
            SchedulerStatus health = status.get().getSchedulerStatus();

            if(!health.isHealthy()) {
               LoggerFactory.getLogger(getClass()).error(
                  "SchedulerHealthIndicator DOWN: status={}", health);
               statusDumpService.dumpStatus();
               return Health.down()
                  .withDetail("started", health.isStarted())
                  .withDetail("shutdown", health.isShutdown())
                  .withDetail("standby", health.isStandby())
                  .withDetail("lastCheck", Instant.ofEpochMilli(health.getLastCheck()).toString())
                  .withDetail("executingCount", health.getExecutingCount())
                  .withDetail("threadCount", health.getThreadCount())
                  .build();
            }

            return Health.up().build();
         }

         return unknown("scheduler health status unavailable");
      }
      catch(Exception e) {
         // Don't include e.getMessage() in the response: the health endpoint may be
         // reachable without authentication, and the exception could carry internal
         // connection details. It's still fully captured in the server log below.
         LoggerFactory.getLogger(getClass()).error("Failed to get scheduler health", e);
         return unknown("failed to get scheduler health");
      }
   }

   private static Health unknown(String reason) {
      return Health.unknown().withDetail("reason", reason).build();
   }

   private final ScheduleClient client;
   private final StatusDumpService statusDumpService;
}
