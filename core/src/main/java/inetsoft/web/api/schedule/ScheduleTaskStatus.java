/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.api.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.schedule.TaskActivity;
import inetsoft.web.json.OffsetDateTimeDeserializer;
import inetsoft.web.json.OffsetDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.time.*;
import java.util.Objects;

/**
 * ScheduleTaskStatus describes the current status of a schedule task.
 */
@Validated
@Schema(description = "The current status of a scheduled task.")
public class ScheduleTaskStatus {
   /**
    * Creates a new instance of {@code ScheduleTaskStatus}.
    */
   public ScheduleTaskStatus() {
   }

   /**
    * Creates a new instance of {@code ScheduleTaskStatus}.
    *
    * @param activity the activity that contains the current status.
    */
   public ScheduleTaskStatus(TaskActivity activity) {
      if(activity != null) {
         setTask(activity.getTaskName());
         setLastRunStatus(activity.getLastRunStatus());
         setNextRunStatus(activity.getNextRunStatus());
         setLastRunStart(getTimestamp(activity.getLastRunStart()));
         setLastRunEnd(getTimestamp(activity.getLastRunEnd()));
         setNextRunStart(getTimestamp(activity.getNextRunStart()));
         setMessage(activity.getMessage());
         setError(activity.getError());
      }
   }

   /**
    * Gets the name of the schedule task.
    *
    * @return the task name.
    */
   @NotNull
   @Schema(description = "The name of the task.", example = "user0~;~organization0:Weekly Summary")
   public String getTask() {
      return task;
   }

   /**
    * Sets the name of the schedule task.
    *
    * @param task the task name.
    */
   public void setTask(String task) {
      this.task = task;
   }

   /**
    * Gets the status of the last run of the task.
    *
    * @return the status.
    */
   @Schema(description = "The status of the last run of the task.", example = "Finished")
   public String getLastRunStatus() {
      return lastRunStatus;
   }

   /**
    * Sets the status of the last run of the task.
    *
    * @param lastRunStatus the status.
    */
   public void setLastRunStatus(String lastRunStatus) {
      this.lastRunStatus = lastRunStatus;
   }

   /**
    * Gets the status of the next run of the task.
    *
    * @return the status.
    */
   @Schema(description = "The status of the next run of the task.", example = "Pending")
   public String getNextRunStatus() {
      return nextRunStatus;
   }

   /**
    * Sets the status of the next run of the task.
    *
    * @param nextRunStatus the status.
    */
   public void setNextRunStatus(String nextRunStatus) {
      this.nextRunStatus = nextRunStatus;
   }

   /**
    * Gets the date and time at which the last run started.
    *
    * @return the start time.
    */
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
   @JsonSerialize(using = OffsetDateTimeSerializer.class)
   @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
   @Schema(
      description = "The date and time that the last run of the task started.",
      format = "date-time",
      example = "2020-03-31T01:00:00Z")
   public OffsetDateTime getLastRunStart() {
      return lastRunStart;
   }

   /**
    * Sets the date and time at which the last run started.
    *
    * @param lastRunStart the start time.
    */
   public void setLastRunStart(OffsetDateTime lastRunStart) {
      this.lastRunStart = lastRunStart;
   }

   /**
    * Gets the date and time at which the last run ended.
    *
    * @return the end time.
    */
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
   @JsonSerialize(using = OffsetDateTimeSerializer.class)
   @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
   @Schema(
      description = "The date and time that the last run of the task ended.",
      format = "date-time",
      example = "2020-03-31T01:00:00Z")
   public OffsetDateTime getLastRunEnd() {
      return lastRunEnd;
   }

   /**
    * Sets the date and time at which the last run ended.
    *
    * @param lastRunEnd the end time.
    */
   public void setLastRunEnd(OffsetDateTime lastRunEnd) {
      this.lastRunEnd = lastRunEnd;
   }

   /**
    * Gets the date and time at which the next run is scheduled.
    *
    * @return the start time.
    */
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
   @JsonSerialize(using = OffsetDateTimeSerializer.class)
   @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
   @Schema(
      description = "The date and time for the next run of the task.",
      format = "date-time",
      example = "2020-04-06T01:00:00Z")
   public OffsetDateTime getNextRunStart() {
      return nextRunStart;
   }

   /**
    * Sets the date and time at which the next run is scheduled.
    *
    * @param nextRunStart the start time.
    */
   public void setNextRunStart(OffsetDateTime nextRunStart) {
      this.nextRunStart = nextRunStart;
   }

   /**
    * Gets the error message from the last run, if any.
    *
    * @return the error message.
    */
   @Schema(description = "The error message from the last run, if any.", example = "Task Completed")
   public String getMessage() {
      return message;
   }

   /**
    * Sets the error message from the last run, if any.
    *
    * @param message the error message.
    */
   public void setMessage(String message) {
      this.message = message;
   }

   /**
    * Gets the error that occurred during the last run, if any.
    *
    * @return the error stack trace.
    */
   @Schema(description = "The stack trace from the error that occurred during the last run, if any.")
   public String getError() {
      return error;
   }

   /**
    * Sets the error that occurred during the last run, if any.
    *
    * @param error the error stack trace.
    */
   public void setError(String error) {
      this.error = error;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleTaskStatus that = (ScheduleTaskStatus) o;
      return Objects.equals(task, that.task) &&
         Objects.equals(lastRunStatus, that.lastRunStatus) &&
         Objects.equals(nextRunStatus, that.nextRunStatus) &&
         Objects.equals(lastRunStart, that.lastRunStart) &&
         Objects.equals(lastRunEnd, that.lastRunEnd) &&
         Objects.equals(nextRunStart, that.nextRunStart) &&
         Objects.equals(message, that.message) &&
         Objects.equals(error, that.error);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         task, lastRunStatus, nextRunStatus, lastRunStart, lastRunEnd, nextRunStart, message,
         error);
   }

   @Override
   public String toString() {
      return "ScheduleTaskStatus{" +
         "task='" + task + '\'' +
         ", lastRunStatus='" + lastRunStatus + '\'' +
         ", nextRunStatus='" + nextRunStatus + '\'' +
         ", lastRunStart=" + lastRunStart +
         ", lastRunEnd=" + lastRunEnd +
         ", nextRunStart=" + nextRunStart +
         ", message='" + message + '\'' +
         ", error='" + error + '\'' +
         '}';
   }

   private static OffsetDateTime getTimestamp(long ts) {
      if(ts == 0L) {
         return null;
      }

      return OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("UTC"));
   }

   private String task;
   private String lastRunStatus;
   private String nextRunStatus;
   private OffsetDateTime lastRunStart;
   private OffsetDateTime lastRunEnd;
   private OffsetDateTime nextRunStart;
   private String message;
   private String error;
}
