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

import inetsoft.web.api.identity.VSOwnerIdentityID;
import inetsoft.sree.security.IdentityID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * {@code CreateScheduleTaskRequest} contains the parameters used to create a schedule task.
 */
@Validated
@Schema(description = "The parameters used to create a schedule task.")
public class CreateScheduleTaskRequest {
   /**
    * Gets the name of the task.
    *
    * @return the task name.
    */
   @NotNull
   @Schema(description = "The name of the task.", example = "Weekly Summary")
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
    * @return the owner.
    */
   @NotNull
   @Schema(
      description = "The owner of the task.",
      implementation = VSOwnerIdentityID.class,
      example = "{\"name\":\"user0\",\"orgID\":\"organization0\"}"
   )
   public IdentityID getOwner() {
      return owner;
   }

   /**
    * Sets the name of the user that owns the task.
    *
    * @param owner the owner.
    */
   public void setOwner(IdentityID owner) {
      this.owner = owner;
   }

   /**
    * Gets the flag indicating the if the task is enabled or disabled.
    *
    * @return {@code true} if enabled; {@code false} otherwise.
    */
   @NotNull
   @Schema(
      description = "A flag that indicates if the task is enabled or disabled.",
      example = "true")
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Sets the flag indicating the if the task is enabled or disabled.
    *
    * @param enabled {@code true} if enabled; {@code false} otherwise.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isDeleteIfNotScheduledToRun() {
      return deleteIfNotScheduledToRun;
   }

   public void setDeleteIfNotScheduledToRun(boolean deleteIfNotScheduledToRun) {
      this.deleteIfNotScheduledToRun = deleteIfNotScheduledToRun;
   }

   public long getStartDate() {
      return startDate;
   }

   public void setStartDate(long startDate) {
      this.startDate = startDate;
   }

   public long getEndDate() {
      return endDate;
   }

   public void setEndDate(long endDate) {
      this.endDate = endDate;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getLocale() {
      return locale;
   }

   public void setLocale(String locale) {
      this.locale = locale;
   }

   public ScheduleTask.ExecuteAsID getExecuteAsID() {
      return executeAsID;
   }

   public void setExecuteAsID(ScheduleTask.ExecuteAsID executeAsID) {
      this.executeAsID = executeAsID;
   }

   /**
    * Gets the list of conditions for the task.
    *
    * @return the condition list.
    */
   @NotNull
   @NotEmpty
   @Schema(description = "The list of conditions for the task. At least one condition is required.")
   public List<ScheduleCondition> getConditions() {
      if(conditions == null) {
         conditions = new ArrayList<>();
      }

      return conditions;
   }

   /**
    * Sets the list of conditions for the task.
    *
    * @param conditions the condition list.
    */
   public void setConditions(List<ScheduleCondition> conditions) {
      this.conditions = conditions;
   }

   /**
    * Gets the list of actions for the class.
    *
    * @return the action list.
    */
   @NotNull
   @NotEmpty
   @Schema(description = "The list of actions for the task. At least one action is required.")
   public List<ScheduleAction> getActions() {
      if(actions == null) {
         actions = new ArrayList<>();
      }

      return actions;
   }

   /**
    * Sets the list of actions for the class.
    *
    * @param actions the action list.
    */
   public void setActions(List<ScheduleAction> actions) {
      this.actions = actions;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      CreateScheduleTaskRequest that = (CreateScheduleTaskRequest) o;
      return enabled == that.enabled &&
         Objects.equals(name, that.name) &&
         Objects.equals(owner, that.owner) &&
         Objects.equals(deleteIfNotScheduledToRun, that.deleteIfNotScheduledToRun) &&
         Objects.equals(startDate, that.startDate) &&
         Objects.equals(endDate, that.endDate) &&
         Objects.equals(description, that.description) &&
         Objects.equals(locale, that.locale) &&
         Objects.equals(executeAsID, that.executeAsID) &&
         Objects.equals(conditions, that.conditions) &&
         Objects.equals(actions, that.actions);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, owner, enabled, deleteIfNotScheduledToRun,
                          startDate, endDate, description, locale, executeAsID, conditions, actions);
   }

   @Override
   public String toString() {
      return "CreateScheduleTaskRequest{" +
         "name='" + name + '\'' +
         ", owner='" + owner + '\'' +
         ", enabled=" + enabled +
         ", deleteIfNotScheduledToRun=" + deleteIfNotScheduledToRun +
         ", startDate=" + startDate +
         ", endDate=" + endDate +
         ", description=" + description +
         ", locale=" + locale +
         ", executeAsID=" + executeAsID +
         ", conditions=" + conditions +
         ", actions=" + actions +
         '}';
   }

   private String name;
   private IdentityID owner;
   private boolean enabled;
   private boolean deleteIfNotScheduledToRun;
   private long startDate;
   private long endDate;
   private String description;
   private String locale;
   private ScheduleTask.ExecuteAsID executeAsID;
   private List<ScheduleCondition> conditions;
   private List<ScheduleAction> actions;
}
