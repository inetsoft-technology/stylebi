/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.ClusterCache;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Class that provides access to persistent schedule task status data.
 * @since 12.2
 */
public class ScheduleStatusDao implements AutoCloseable {
   public ScheduleStatusDao() {
      this.cache = new StatusCache();
      cache.initialize();
   }

   public static ScheduleStatusDao getInstance() {
      return SingletonManager.getInstance(ScheduleStatusDao.class);
   }

   @Override
   public void close() throws Exception {
      cache.close();
   }

   /**
    * Gets the status of the last execution of a task.
    *
    * @param taskName the name of the task.
    * @return the last execution status or <tt>null</tt> if none.
    */
   public Status getStatus(String taskName) {
      return cache.getStatus(taskName);
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

      cache.modify(() -> cache.putStatus(taskName, newStatus));
      return newStatus;
   }

   /**
    * Removes the status entry for the specified task.
    *
    * @param taskName the task name.
    */
   @SuppressWarnings("WeakerAccess")
   public void clearStatus(String taskName) {
      cache.modify(() -> cache.removeStatus(taskName));
   }

   private final StatusCache cache;
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
      private static final long serialVersionUID = 1L;
   }

   public static final class StatusList {
      public Map<String, Status> getStatuses() {
         if(statuses == null) {
            statuses = new HashMap<>();
         }

         return statuses;
      }

      public void setStatuses(Map<String, Status> statuses) {
         this.statuses = statuses;
      }

      Map<String, Status> statuses;
   }

   private static final class StatusCache extends ClusterCache<LoadEvent, LoadData, SaveData> {
      StatusCache() {
         super(null, STATUSES);
      }

      @Override
      protected Map<String, Map> doLoad(boolean initializing, LoadData loadData) {
         Map<String, Map> maps = new HashMap<>();
         Map<String, Status> statuses = new HashMap<>();

         DataSpace dataSpace = DataSpace.getDataSpace();

         if(dataSpace.exists(null, "schedule-status.json")) {
            ObjectMapper mapper = new ObjectMapper();

            try(InputStream input = dataSpace.getInputStream(null, "schedule-status.json")) {
               if(input != null) {
                  StatusList list = mapper.readValue(input, StatusList.class);
                  statuses.putAll(list.getStatuses());
               }
            }
            catch(IOException e) {
               LOG.warn("Failed to load schedule status from file", e);
            }
         }

         maps.put(STATUSES, statuses);
         return maps;
      }

      @Override
      protected void doSave(SaveData saveData) throws Exception {
         StatusList list = new StatusList();
         list.getStatuses().putAll(getStatuses());

         DataSpace dataSpace = DataSpace.getDataSpace();
         ObjectMapper mapper = new ObjectMapper();

         try {
            dataSpace.withOutputStream(
               null, "schedule-status.json", output -> mapper.writeValue(output, list));
         }
         catch(IOException e) {
            throw new Exception(Catalog.getCatalog().getString(
               "Failed to write schedule status to file"), e);
         }
      }

      @Override
      protected LoadData getLoadData(LoadEvent event) {
         return new LoadData(System.currentTimeMillis());
      }

      @Override
      protected SaveData getSaveData() {
         return new SaveData(System.currentTimeMillis());
      }

      Status getStatus(String key) {
         return getStatuses().get(key);
      }

      Status putStatus(String key, Status status) {
         return getStatuses().put(key, status);
      }

      Status removeStatus(String key) {
         return getStatuses().remove(key);
      }

      private Map<String, Status> getStatuses() {
         return getMap(STATUSES);
      }

      private static final String STATUSES = "statuses";
   }

   /*
   These cache data structures are basically empty, but provide a place to pass data or fire events
    if the future if needed.
    */

   public static final class LoadEvent extends EventObject {
      public LoadEvent(Object source) {
         super(source);
      }
   }

   public static final class LoadData implements Serializable {
      public LoadData(long timestamp) {
         this.timestamp = timestamp;
      }

      final long timestamp;
   }

   public static final class SaveData implements Serializable {
      public SaveData(long timestamp) {
         this.timestamp = timestamp;
      }

      final long timestamp;
   }
}
