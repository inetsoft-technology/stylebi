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

import java.io.Serial;
import java.io.Serializable;
import java.time.*;
import java.util.concurrent.Future;

public class SchedulerStatus implements Serializable {
   public SchedulerStatus(boolean started, boolean shutdown, boolean standby, long lastCheck,
                          long nextCheck, Future.State checkState, int executingCount,
                          int threadCount)
   {
      this.started = started;
      this.shutdown = shutdown;
      this.standby = standby;
      this.lastCheck = lastCheck;
      this.nextCheck = nextCheck;
      this.checkState = checkState;
      this.executingCount = executingCount;
      this.threadCount = threadCount;
   }

   public boolean isStarted() {
      return started;
   }

   public boolean isShutdown() {
      return shutdown;
   }

   public boolean isStandby() {
      return standby;
   }

   public long getLastCheck() {
      return lastCheck;
   }

   public long getNextCheck() {
      return nextCheck;
   }

   public Future.State getCheckState() {
      return checkState;
   }

   public int getExecutingCount() {
      return executingCount;
   }

   public int getThreadCount() {
      return threadCount;
   }

   public boolean isHealthy() {
      boolean healthy = started && !shutdown && !standby;

      if(healthy) {
         if(lastCheck >= 0 &&
            Instant.now().isAfter(Instant.ofEpochMilli(lastCheck).plusSeconds(120)))
         {
            // if there are free threads and the health check job has not run, there's a problem
            healthy = executingCount >= threadCount;
         }
      }

      return healthy;
   }

   private String getDisplayLastCheck() {
      return getDisplayCheck(lastCheck, "%s (%s ago)");
   }

   private String getDisplayNextCheck() {
      return getDisplayCheck(nextCheck, "%s (in %s)");
   }

   private String getDisplayCheck(long ts, String format) {
      if(ts <= 0) {
         return "none";
      }

      Instant tsInstant = Instant.ofEpochMilli(ts);
      String tsString = ZonedDateTime.ofInstant(tsInstant, ZoneId.systemDefault()).toString();
      Duration duration = Duration.between(tsInstant, Instant.now());

      if(duration.isNegative()) {
         return tsString;
      }

      return String.format(format, tsString, duration);
   }

   @Override
   public String toString() {
      return "SchedulerStatus{" +
         "started=" + started +
         ", shutdown=" + shutdown +
         ", standby=" + standby +
         ", lastCheck=" + getDisplayLastCheck() +
         ", nextCheck=" + getDisplayNextCheck() +
         ", checkState=" + checkState +
         ", executingCount=" + executingCount +
         ", threadCount=" + threadCount +
         '}';
   }

   private final boolean started;
   private final boolean shutdown;
   private final boolean standby;
   private final long lastCheck;
   private final long nextCheck;
   private final Future.State checkState;
   private final int executingCount;
   private final int threadCount;
   @Serial
   private static final long serialVersionUID = 1L;
}
