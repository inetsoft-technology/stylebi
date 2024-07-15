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
package inetsoft.sree.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Internal action that balances the tasks in time ranges that are about to start.
 */
public class TaskBalancerAction implements ScheduleAction {
   @Override
   public void run(Principal principal) throws Throwable {
      LocalTime now = LocalTime.now();
      TaskBalancer balancer = new TaskBalancer();

      for(TimeRange range : TimeRange.getTimeRanges()) {
         if(Math.abs(Duration.between(now, range.getStartTime()).toMinutes()) <= 10) {
            try {
               balancer.balanceTasks(range);
            }
            catch(Exception e) {
               LOG.error("Failed to re-balance time range: {}", range.getName(), e);
            }
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TaskBalancerAction.class);
}
