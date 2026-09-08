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

import inetsoft.web.api.identity.TaskExecuteAsIdentityID;
import inetsoft.web.api.identity.TaskOwnerIdentityID;
import inetsoft.sree.security.IdentityID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * ScheduleTask contains the properties of a scheduled task.
 */
@Schema(description = "The properties of a scheduled task.")
public class ScheduleTask {
   /**
    * Creates a new instance of {@code ScheduleTask}.
    */
   public ScheduleTask() {
   }

   public ScheduleTask(String name, IdentityID owner, boolean enabled, boolean deleteIfNotScheduledToRun, long startDate, long endDate, String description, String locale, ExecuteAsID executeAsID) {
      this.name = name;
      this.owner = owner;
      this.description = description;
      this.locale = locale;
      this.startDate = startDate;
      this.endDate = endDate;
      this.enabled = enabled;
      this.deleteIfNotScheduledToRun = deleteIfNotScheduledToRun;
      this.executeAsID = executeAsID;
   }

   /**
    * Creates a new instance of {@code ScheduleTask}.
    *
    * @param taskName    the task name.
    * @param owner   the task owner.
    * @param enabled {@code true} if enabled; {@code false} if disabled.
    */
   public ScheduleTask(String taskName, IdentityID owner, boolean enabled) {
      this.name = taskName;
      this.owner = owner;
      this.enabled = enabled;
   }

   /**
    * Creates a new instance of {@code ScheduleTask}.
    *
    * @param task the task object being represented.
    */
   public ScheduleTask(inetsoft.sree.schedule.ScheduleTask task) {
      this(task.getName(), task.getOwner(), task.isEnabled());
   }

   /**
    * Gets the name of the task.
    *
    * @return the task name.
    */
   @NotNull
   @Schema(description = "The name of the task.", example = "Weekly Task")
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the task.
    *
    * @param name the task name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the name of the user that owns the task.
    *
    * @return the task owner.
    */
   @NotNull
   @Schema(
      description = "The name of the user that owns the task.",
      implementation = TaskOwnerIdentityID.class,
      example = "{\"name\":\"user0\",\"orgID\":\"organization0\"}"
   )
   public IdentityID getOwner() {
      return owner;
   }

   /**
    * Sets the name of the user that owns the task.
    *
    * @param owner the task owner.
    */
   public void setOwner(IdentityID owner) {
      this.owner = owner;
   }

   /**
    * Gets the description of the task.
    *
    * @return the task description.
    *
    * @since 2023
    */
   @Schema(description = "The description of the task.")
   public String getDescription() {
      return description;
   }

   /**
    * Sets the description of the task.
    *
    * @param description the task description.
    *
    * @since 2023
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets the locale of the task.
    *
    * @return the task locale.
    *
    * @since 2023
    */
   @Schema(description = "The locale of the task.")
   public String getLocale() {
      return locale;
   }

   /**
    * Sets the locale of the task.
    *
    * @param locale the task locale.
    *
    * @since 2023
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   /**
    * Gets the start date of the task in milliseconds.
    *
    * @return the task start date.
    *
    * @since 2023
    */
   @Schema(description = "The start date of the task in milliseconds.")
   public long getStartDate() {
      return startDate;
   }

   /**
    * Sets the start date of the task in milliseconds.
    *
    * @param startDate the task start date in milliseconds.
    *
    * @since 2023
    */
   public void setStartDate(long startDate) {
      this.startDate = startDate;
   }

   /**
    * Gets the end date of the task in milliseconds.
    *
    * @return the task end date.
    *
    * @since 2023
    */
   @Schema(description = "The end date of the task in milliseconds.")
   public long getEndDate() {
      return endDate;
   }

   /**
    * Sets the end date of the task in milliseconds.
    *
    * @param endDate the task end date in milliseconds.
    *
    * @since 2023
    */
   public void setEndDate(long endDate) {
      this.endDate = endDate;
   }

   /**
    * Gets a flag indicating if the task is enabled or disabled.
    *
    * @return {@code true} if enabled; {@code false} if disabled.
    */
   @NotNull
   @Schema(description = "A flag indicating if the task is enabled or disabled.", example = "true")
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Sets a flag indicating if the task is enabled or disabled.
    *
    * @param enabled {@code true} if enabled; {@code false} if disabled.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /**
    * Gets the flag indicating if the task should be deleted if it is not scheduled to run again.
    *
    * @return the flag indicating if the task should be deleted.
    *
    * @since 2023
    */
   @Schema(description = "The flag indicating if the task should be deleted if it is not scheduled to run again.")
   public boolean isDeleteIfNotScheduledToRun() {
      return deleteIfNotScheduledToRun;
   }

   /**
    * Sets the flag indicating if the task should be deleted if it is not scheduled to run again.
    *
    * @param deleteIfNotScheduledToRun the flag indicating if the task should be deleted.
    *
    * @since 2023
    */
   public void setDeleteIfNotScheduledToRun(boolean deleteIfNotScheduledToRun) {
      this.deleteIfNotScheduledToRun = deleteIfNotScheduledToRun;
   }

   /**
    * Gets the identity used to execute the task.
    *
    * @return the identity used to execute the task.
    *
    * @since 2023
    */
   @Schema(description = "The identity used to execute the task.")
   public ExecuteAsID getExecuteAsID() {
      return executeAsID;
   }

   /**
    * Sets the identity used to execute the task.
    *
    * @param executeAsID the identity used to execute the task.
    *
    * @since 2023
    */
   public void setExecuteAsID(ExecuteAsID executeAsID) {
      this.executeAsID = executeAsID;
   }

   /**
    * Gets the current status of the task.
    *
    * @return the task status.
    */
   @Schema(description = "The current status of the task.")
   public ScheduleTaskStatus getStatus() {
      return status;
   }

   /**
    * Sets the current status of the task.
    *
    * @param status the task status.
    */
   public void setStatus(ScheduleTaskStatus status) {
      this.status = status;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleTask that = (ScheduleTask) o;
      return enabled == that.enabled &&
         deleteIfNotScheduledToRun == that.deleteIfNotScheduledToRun &&
         startDate == that.startDate &&
         endDate == that.endDate &&
         Objects.equals(name, that.name) &&
         Objects.equals(owner, that.owner) &&
         Objects.equals(description, that.description) &&
         Objects.equals(locale, that.locale) &&
         Objects.equals(executeAsID, that.executeAsID) &&
         Objects.equals(status, that.status);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, owner, description, locale, startDate, endDate,
                          enabled, deleteIfNotScheduledToRun, executeAsID, status);
   }

   @Override
   public String toString() {
      return "ScheduleTask{" +
         "name='" + name + '\'' +
         ", owner='" + owner + '\'' +
         ", description='" + description + '\'' +
         ", locale='" + locale + '\'' +
         ", startDate=" + startDate +
         ", endDate=" + endDate +
         ", enabled=" + enabled +
         ", deleteIfNotScheduledToRun=" + deleteIfNotScheduledToRun +
         ", executeAsID=" + executeAsID +
         ", status=" + status +
         '}';
   }

   /**
    * ExecuteAsID describes identity type and name for the identity used to execute the task.
    */
   @Validated
   @Schema(description = "Describes identity type and name for the identity used to execute the task.")
   public static class ExecuteAsID {
      /**
       * Creates a new instance of {@code ExecuteAsID}.
       */
      public ExecuteAsID() {
      }

      /**
       * Creates a new instance of {@code ExecuteAsID}.
       *
       * @param type   the type of the executing identity.
       * @param identityID   the name of the executing identity.
       */
      public ExecuteAsID(Type type, IdentityID identityID) {
         this.type = type;
         this.identityID = identityID;
      }

      /**
       * Gets the type of the executing identity.
       *
       * @return the type of the executing identity.
       */
      @NotNull
      @Schema(description = "The type of the executing identity.", example = "USER")
      public Type getType() {
         return type;
      }

      /**
       * Sets the type of the executing identity.
       *
       * @param type the type of the executing identity.
       */
      public void setType(Type type) {
         this.type = type;
      }

      /**
       * Gets the name of the executing identity.
       *
       * @return the name of the executing identity.
       */
      @NotNull
      @Schema(
         description = "The name of the executing identity.",
         implementation = TaskExecuteAsIdentityID.class,
         example = "{\"name\":\"user0\",\"orgID\":\"organization0\"}"
      )
      public IdentityID getIdentityID() {
         return identityID;
      }

      /**
       * Sets the name of the executing identity.
       *
       * @param identityID the name of the executing identity.
       */
      public void setIdentityID(IdentityID identityID) {
         this.identityID = identityID;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         ExecuteAsID that = (ExecuteAsID) o;
         return Objects.equals(type, that.type) && Objects.equals(identityID, that.identityID);
      }

      @Override
      public int hashCode() {
         return Objects.hash(type, identityID);
      }

      @Override
      public String toString() {
         return "ExecuteAsID{" +
            "type='" + type + '\'' +
            ", name='" + identityID + '\'' +
            '}';
      }

      private Type type;
      private IdentityID identityID;

      /**
       * Enumeration of the types of identity types available for executing the task
       */
      public enum Type {
         USER, GROUP
      }
   }

   private String name;
   private IdentityID owner;
   private String description;
   private String locale;
   private long startDate = -1;
   private long endDate = -1;
   private boolean enabled;
   private boolean deleteIfNotScheduledToRun;
   private ExecuteAsID executeAsID;
   private ScheduleTaskStatus status;
}
