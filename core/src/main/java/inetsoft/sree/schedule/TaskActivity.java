/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.sree.schedule;

import inetsoft.util.Tool;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class holds the schedule task status.
 *
 * @version 5.1, 9/23/2003
 * @author InetSoft Technology Corp
 */
public class TaskActivity implements Serializable {
   /**
    * Format a time stamp to string.
    */
   private String formatDateTime(long timeVal) {
      if(timeVal <= 0) {
         return "";
      }

      // @by jasonshobe, don't cache date formats, they are not thread safe
      SimpleDateFormat format = Tool.getDateTimeFormat();
      return format.format(new Date(timeVal));
   }

   /**
    * Create a task status object.
    */
   public TaskActivity(String taskname) {
      this.name = taskname;
   }

   /**
    * Get task name.
    */
   public String getTaskName() {
      return name;
   }

   /**
    * Get the last execution status.
    */
   public String getLastRunStatus() {
      return lastRunStatus;
   }

   /**
    * Get the last execution starting time.
    */
   public long getLastRunStart() {
      return lastRunStart;
   }

   /**
    * Get a string representation of last execution start time.
    */
   public String getFormattedLastRunStart() {
      return formatDateTime(lastRunStart);
   }

   /**
    * Get the last execution end time.
    */
   public long getLastRunEnd() {
      return lastRunEnd;
   }

   /**
    * Get a string representation of last execution ending time.
    */
   public String getFormattedLastRunEnd() {
      return formatDateTime(lastRunEnd);
   }

   /**
    * Get the next execution status.
    */
   public String getNextRunStatus() {
      return nextRunStatus;
   }

   /**
    * Get the next execution start time.
    */
   public long getNextRunStart() {
      return nextRunStart;
   }

   /**
    * Get a string representation of next execution start time.
    */
   public String getFormattedNextRunStart() {
      return formatDateTime(nextRunStart);
   }

   /**
    * Get the status message.
    */
   public String getMessage() {
      return msg;
   }

   /**
    * Get a non-null message string.
    */
   public String getCleanMessage() {
      if(msg == null || msg.equals("null")) {
         return "";
      }
      else {
         return msg;
      }
   }

   /**
    * Set the task name.
    */
   public void setTaskName(String taskname) {
      this.name = taskname;
   }

   /**
    * Set the last execution status.
    */
   public void setLastRunStatus(String status) {
      this.lastRunStatus = status;
   }

   /**
    * Set the last execution start time.
    */
   public void setLastRunStart(long start) {
      this.lastRunStart = start;
   }

   /**
    * Set the last execution end time.
    */
   public void setLastRunEnd(long end) {
      this.lastRunEnd = end;
   }

   /**
    * Set the next execution status.
    */
   public void setNextRunStatus(String status) {
      this.nextRunStatus = status;
   }

   /**
    * Set the next execution start time.
    */
   public void setNextRunStart(long start) {
      this.nextRunStart = start;
   }

   /**
    * Set the task status message.
    */
   public void setMessage(String msg) {
      this.msg = msg;
   }

   /**
    * Gets the error message, if any.
    *
    * @return the error message.
    */
   public String getError() {
      return error;
   }

   /**
    * Sets the error message, if any.
    *
    * @param error the error message.
    */
   public void setError(String error) {
      this.error = error;
   }

   @Override
   public String toString() {
      return "TaskActivity{" +
         "name='" + name + '\'' +
         ", lastRunStatus='" + lastRunStatus + '\'' +
         ", nextRunStatus='" + nextRunStatus + '\'' +
         ", lastRunStart=" + lastRunStart +
         ", lastRunEnd=" + lastRunEnd +
         ", nextRunStart=" + nextRunStart +
         ", msg='" + msg + '\'' +
         ", error='" + error + '\'' +
         '}';
   }

   private String name = null;
   private String lastRunStatus = null;
   private String nextRunStatus = null;
   private long lastRunStart;
   private long lastRunEnd;
   private long nextRunStart;
   private String msg;
   private String error;
}
