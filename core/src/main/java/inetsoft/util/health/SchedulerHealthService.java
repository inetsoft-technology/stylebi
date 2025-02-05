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

import inetsoft.sree.schedule.Scheduler;
import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;

import java.util.concurrent.atomic.AtomicLong;

public class SchedulerHealthService {
   public static SchedulerHealthService getInstance() {
      return SingletonManager.getInstance(SchedulerHealthService.class);
   }

   public SchedulerStatus getStatus() {
      if(isLocal()) {
         return Scheduler.getScheduler().getSchedulerStatus();
      }

      return null;
   }

   public long getLastCheck() {
      return lastCheck.get();
   }

   public void setLastCheck(long value) {
      lastCheck.set(value);
   }

   private boolean isLocal() {
      return "true".equals(System.getProperty("ScheduleServer")) ||
         InetsoftConfig.getInstance().getCloudRunner() != null;
   }

   private final AtomicLong lastCheck = new AtomicLong(0L);
}
