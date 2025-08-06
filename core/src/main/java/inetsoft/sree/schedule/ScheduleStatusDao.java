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

import inetsoft.sree.schedule.quartz.JobCompletionListener;
import inetsoft.storage.KeyValueStorage;
import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;

/**
 * Class that provides access to persistent schedule task status data.
 * @since 12.2
 */
public class ScheduleStatusDao implements AutoCloseable {
   public ScheduleStatusDao() {
      storage = KeyValueStorage.newInstance("scheduleStatus");
   }

   public static ScheduleStatusDao getInstance() {
      return SingletonManager.getInstance(ScheduleStatusDao.class);
   }

   @Override
   public void close() throws Exception {
      storage.close();
   }

   /**
    * Gets the status of the last execution of a task.
    *
    * @param taskName the name of the task.
    * @return the last execution status or <tt>null</tt> if none.
    */
   public Status getStatus(String taskName) {
      return storage.get(taskName);
   }

   /**
    * Sets the status of the last execution of a task.
    *
    * @param taskName  the name of the task.
    * @param status    the status of the last execution.
    * @param startTime the time at which the execution started.
    * @param endTime   the time at which the execution finished.
    * @param error     the error message or <tt>null</tt> if none.
    *
    * @return the new status.
    */
   public Status setStatus(String taskName, Scheduler.Status status, long startTime,
                           long endTime, String error, boolean runNow)
   {
      Status oldStatus = getStatus(taskName);
      Status newStatus = new Status();
      newStatus.setStatus(status);
      newStatus.setStartTime(startTime);
      newStatus.setEndTime(endTime);
      newStatus.setError(error);

      if(runNow && oldStatus != null) {
         newStatus.setLastScheduledStartTime(oldStatus.getLastScheduledStartTime());
      }
      else {
         newStatus.setLastScheduledStartTime(startTime);
      }

      try {
         storage.put(taskName, newStatus).get();
      }
      catch(Exception ex) {
         LOG.error("Failed to put schedule status {} for {}", status, taskName);
      }

      return newStatus;
   }

   /**
    * Removes the status entry for the specified task.
    *
    * @param taskName the task name.
    */
   @SuppressWarnings("WeakerAccess")
   public void clearStatus(String taskName) {
      storage.remove(taskName);
   }

   private final KeyValueStorage<Status> storage;
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleStatusDao.class);

   public static final class Status implements Serializable {
      /**
       * Gets the status of the last execution of the task.
       *
       * @return the status.
       */
      public Scheduler.Status getStatus() {
         return status;
      }

      /**
       * Sets the status of the last execution of the task.
       *
       * @param status the status.
       */
      public void setStatus(Scheduler.Status status) {
         this.status = status;
      }

      /**
       * Gets the time at which the last execution of the task started.
       *
       * @return the start time.
       */
      public long getStartTime() {
         return startTime;
      }

      /**
       * Sets the time at which the last execution of the task started.
       *
       * @param startTime the start time.
       */
      public void setStartTime(long startTime) {
         this.startTime = startTime;
      }

      /**
       * Gets the time at which the last execution of the task finished.
       *
       * @return the end time.
       */
      public long getEndTime() {
         return endTime;
      }

      /**
       * Sets the time at which the last execution of the task finished.
       *
       * @param endTime the end time.
       */
      public void setEndTime(long endTime) {
         this.endTime = endTime;
      }

      /**
       * Gets the error message from the last execution of the task.
       *
       * @return the error message or <tt>null</tt> if none.
       */
      public String getError() {
         return error;
      }

      /**
       * Sets the error message from the last execution of the task.
       *
       * @param error the error message or <tt>null</tt> if none.
       */
      public void setError(String error) {
         this.error = error;
      }

      // get last time task was run, excluding run now.
      public long getLastScheduledStartTime() {
         return scheduledStartTime;
      }

      public void setLastScheduledStartTime(long time) {
         this.scheduledStartTime = time;
      }

      @Override
      public String toString() {
         return "Status{" +
            ", status=" + status +
            ", startTime=" + startTime +
            ", endTime=" + endTime +
            ", error='" + error + '\'' +
            '}';
      }

      private Scheduler.Status status;
      private long startTime;
      private long endTime;
      private String error;
      private long scheduledStartTime;
      @Serial
      private static final long serialVersionUID = 1L;
   }
}
