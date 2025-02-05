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

package inetsoft.web.metrics;

import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.util.health.HealthStatus;
import inetsoft.util.health.SchedulerStatus;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class SchedulerScalingMetric extends ScalingMetric {
   public SchedulerScalingMetric(boolean movingAverage, int capacity) {
      super(movingAverage, capacity);
   }

   @Override
   protected double calculate() {
      ScheduleClient client = new ScheduleClient();

      if(client.isCloud()) {
         try {
            Optional<HealthStatus> health = client.getHealthStatus();

            if(health.isPresent()) {
               SchedulerStatus status = health.get().getSchedulerStatus();

               if(status != null) {
                  double tasks = status.getExecutingCount();
                  double threads = status.getThreadCount();
                  return tasks / threads;
               }
            }
         }
         catch(Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Failed to get scheduler health", e);
         }
      }

      return 0D;
   }
}
